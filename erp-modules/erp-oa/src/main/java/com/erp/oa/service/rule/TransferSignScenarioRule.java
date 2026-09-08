package com.erp.oa.service.rule;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.api.domain.HrEmployeeSigningSnapshot;
import com.erp.oa.api.domain.HrSignBusinessEvent;
import com.erp.oa.constant.OaSignPackageStatus;
import com.erp.oa.constant.OaSignPlanScope;
import com.erp.oa.constant.OaSignTemplateType;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPlanVersion;
import com.erp.oa.domain.OaSignPlanVersionTemplate;
import com.erp.oa.domain.vo.OaSignDraftDecision;
import com.erp.oa.mapper.OaSignPlanVersionMapper;

/** 调岗场景只读规则：验证冻结前后快照并按业务生效日选择签约方案。 */
@Component
public class TransferSignScenarioRule implements OaSignScenarioRule
{
    private static final String SCENARIO = "TRANSFER";
    private static final String ACTION_TYPE = "TRANSFER_CONFIRMED";
    private static final String SOURCE_TYPE = "HR_LIFECYCLE_ACTION";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_MONEY_INTEGER_DIGITS = 14;
    private static final int MAX_MONEY_SCALE = 2;
    private static final Set<String> PREDICATE_FIELDS = Set.of(
            "organizationChanged", "postChanged", "gradeChanged", "locationChanged",
            "salaryChanged", "legalEntityChanged", "reportingChanged",
            "historicalSupplement");
    private static final Set<String> COMPATIBILITY_FIELDS = Set.of(
            "postName", "postLevel", "salaryVersion");

    private final OaSignPlanVersionMapper versionMapper;
    private final ObjectMapper objectMapper;

    public TransferSignScenarioRule(OaSignPlanVersionMapper versionMapper,
            ObjectMapper objectMapper)
    {
        this.versionMapper = versionMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String scenario)
    {
        return scenario != null && SCENARIO.equalsIgnoreCase(scenario.trim());
    }

    @Override
    public String dedupeKey(HrSignBusinessEvent event)
    {
        List<String> reasons = new ArrayList<>();
        validateEnvelope(event, reasons);
        if (!reasons.isEmpty())
        {
            throw new ServiceException("调岗事件不一致: " + String.join(",", reasons));
        }
        return SCENARIO + ":" + event.getEmployeeId() + ":"
                + event.getSourceBusinessId() + ":" + event.getSourceEventVersion();
    }

    @Override
    public OaSignDraftDecision decide(HrSignBusinessEvent event)
    {
        List<String> reasons = new ArrayList<>();
        validateEnvelope(event, reasons);
        if (!reasons.isEmpty())
        {
            return needsData(reasons, List.of());
        }

        LocalDate operationDate = LocalDate.ofInstant(
                event.getOccurredTime().toInstant(), BUSINESS_ZONE);
        LocalDate effectiveDate = effectiveDate(event);
        boolean historical = Boolean.TRUE.equals(historicalMarker(event));
        if (effectiveDate.isAfter(operationDate))
        {
            return needsData(List.of("FUTURE_TRANSFER_EVENT"), List.of());
        }
        if (historical != effectiveDate.isBefore(operationDate))
        {
            return needsData(List.of("HISTORICAL_MARKER_MISMATCH"), List.of());
        }
        Object historicalReason = attribute(event.getAttributes(), "historicalReason");
        if (historical && (historicalReason == null
                || blank(String.valueOf(historicalReason))))
        {
            return needsData(List.of("MISSING_HISTORICAL_REASON"),
                    List.of("HISTORICAL_BACKFILL"));
        }

        HrEmployeeSigningSnapshot before = event.getBeforeSnapshot();
        HrEmployeeSigningSnapshot after = event.getAfterSnapshot();
        validateSnapshots(before, after, effectiveDate, reasons);
        if (!reasons.isEmpty())
        {
            return needsData(reasons, historical
                    ? List.of("HISTORICAL_BACKFILL") : List.of());
        }

        Changes changes = changes(before, after, historical);
        if (!changes.hasBusinessChange())
        {
            return needsData(List.of("NO_TRANSFER_CHANGE"), changes.riskCodes());
        }
        if (changes.reportingOnly())
        {
            OaSignDraftDecision noAction = new OaSignDraftDecision();
            noAction.setAction(OaSignDraftDecision.Action.NO_ACTION);
            noAction.setRiskLevel("LOW");
            noAction.setReasonCodes(List.of("REPORTING_LINE_ONLY"));
            return noAction;
        }

        String risk = changes.riskCodes().isEmpty() ? "LOW" : "HIGH";
        List<OaSignPlanVersion> candidates = versionMapper.selectPublishedMatchingCandidates(
                SCENARIO, after.getShopDeptId(), after.getLegalEntityId());
        List<OaSignPlanVersion> matched = new ArrayList<>();
        boolean invalidRule = false;
        if (candidates != null)
        {
            for (OaSignPlanVersion candidate : candidates)
            {
                if (!eligible(candidate, after)) continue;
                ChangePredicate predicate = parsePredicate(candidate.getRuleJson());
                if (predicate == null)
                {
                    invalidRule = true;
                    continue;
                }
                if (predicate.matches(changes, after)) matched.add(candidate);
            }
        }
        if (invalidRule) return needsData(List.of("PLAN_RULE_INVALID"), changes.riskCodes());
        if (matched.isEmpty()) return needsData(List.of("PLAN_NOT_FOUND"), changes.riskCodes());
        if (matched.size() > 1) return needsData(List.of("PLAN_CONFLICT"), changes.riskCodes());

        OaSignPlanVersion version = matched.get(0);
        List<OaSignPlanVersionTemplate> templates =
                versionMapper.selectTemplatesByVersionId(version.getVersionId());
        if (templates == null || templates.isEmpty())
        {
            return needsData(List.of("PLAN_TEMPLATE_SNAPSHOT_MISSING"), changes.riskCodes());
        }
        TemplateDecision templateDecision = validateTemplates(version, templates);
        if (!templateDecision.valid())
        {
            return needsData(List.of("PLAN_TEMPLATE_INVALID"), changes.riskCodes());
        }
        if (!templateDecision.employeeConfirmationRequired())
        {
            OaSignDraftDecision noAction = new OaSignDraftDecision();
            noAction.setAction(OaSignDraftDecision.Action.NO_ACTION);
            noAction.setPlanVersionId(version.getVersionId());
            noAction.setRiskLevel(risk);
            List<String> noActionReasons = new ArrayList<>(changes.riskCodes());
            add(noActionReasons, "NO_EMPLOYEE_CONFIRMATION_DOCUMENT");
            add(noActionReasons, "方案未包含需要员工确认的文件");
            noAction.setReasonCodes(noActionReasons);
            return noAction;
        }

        OaSignDraftDecision decision = new OaSignDraftDecision();
        decision.setAction(OaSignDraftDecision.Action.CREATE_DRAFT);
        decision.setPlanVersionId(version.getVersionId());
        decision.setRiskLevel(risk);
        decision.setReasonCodes(changes.riskCodes());
        decision.setDraftPackage(draft(before, after, effectiveDate, historical, version));
        return decision;
    }

    private void validateEnvelope(HrSignBusinessEvent event, List<String> reasons)
    {
        if (event == null)
        {
            add(reasons, "MISSING_EVENT");
            return;
        }
        if (!supports(event.getScenario())) add(reasons, "INVALID_SCENARIO");
        if (blank(event.getEventId())) add(reasons, "MISSING_EVENT_ID");
        if (event.getEmployeeId() == null || event.getEmployeeId() <= 0)
            add(reasons, "INVALID_EMPLOYEE_ID");
        if (!SOURCE_TYPE.equals(code(event.getSourceType())))
            add(reasons, "INVALID_SOURCE_TYPE");
        Long sourceActionId = positiveLong(event.getSourceBusinessId());
        if (sourceActionId == null) add(reasons, "INVALID_SOURCE_ACTION_ID");
        if (event.getSourceEventVersion() == null || event.getSourceEventVersion() <= 0)
            add(reasons, "INVALID_SOURCE_ACTION_VERSION");
        if (event.getOccurredTime() == null) add(reasons, "MISSING_OCCURRED_TIME");
        if (event.getOperatorUserId() == null || event.getOperatorUserId() <= 0)
            add(reasons, "INVALID_OPERATOR_USER_ID");

        HrEmployeeSigningSnapshot before = event.getBeforeSnapshot();
        HrEmployeeSigningSnapshot after = event.getAfterSnapshot();
        if (before == null) add(reasons, "MISSING_BEFORE_SNAPSHOT");
        if (after == null) add(reasons, "MISSING_AFTER_SNAPSHOT");
        if (before != null && !Objects.equals(event.getEmployeeId(), before.getEmployeeId()))
            add(reasons, "BEFORE_EMPLOYEE_ID_MISMATCH");
        if (after != null && !Objects.equals(event.getEmployeeId(), after.getEmployeeId()))
            add(reasons, "AFTER_EMPLOYEE_ID_MISMATCH");

        Map<String, Object> attributes = event.getAttributes();
        if (!ACTION_TYPE.equals(code(attribute(attributes, "actionType"))))
            add(reasons, "ACTION_TYPE_MISMATCH");
        Long attributeActionId = positiveLong(attribute(attributes, "sourceActionId"));
        if (attributeActionId == null || !Objects.equals(sourceActionId, attributeActionId))
            add(reasons, "SOURCE_ACTION_ID_MISMATCH");
        Long attributeVersion = positiveLong(attribute(attributes, "sourceActionVersion"));
        if (attributeVersion == null
                || !Objects.equals(event.getSourceEventVersion(), attributeVersion))
            add(reasons, "SOURCE_ACTION_VERSION_MISMATCH");
        if (effectiveDate(event) == null) add(reasons, "INVALID_EFFECTIVE_DATE");
        if (historicalMarker(event) == null) add(reasons, "INVALID_HISTORICAL_MARKER");
    }

    private void validateSnapshots(HrEmployeeSigningSnapshot before,
            HrEmployeeSigningSnapshot after, LocalDate effectiveDate, List<String> reasons)
    {
        if (before == null || after == null) return;
        validateRequired(before, "BEFORE", reasons);
        validateRequired(after, "AFTER", reasons);
        if (!Objects.equals(after.getTransferEffectiveDate(), effectiveDate))
            add(reasons, "TRANSFER_EFFECTIVE_DATE_MISMATCH");
        if (!Objects.equals(trim(before.getEmployeeStatus()), trim(after.getEmployeeStatus()))
                || "离职".equals(trim(after.getEmployeeStatus()))
                || "待入职".equals(trim(after.getEmployeeStatus())))
            add(reasons, "INVALID_STATUS_TRANSITION");
        if (!sameUnrelated(before, after)) add(reasons, "UNRELATED_SNAPSHOT_CHANGED");
    }

    private void validateRequired(HrEmployeeSigningSnapshot snapshot,
            String prefix, List<String> reasons)
    {
        if (snapshot.getEmployeeId() == null || snapshot.getEmployeeId() <= 0)
            add(reasons, "INVALID_" + prefix + "_EMPLOYEE_ID");
        if (blank(snapshot.getEmployeeName())) add(reasons, "MISSING_" + prefix + "_EMPLOYEE_NAME");
        if (blank(snapshot.getPhone()) || !trim(snapshot.getPhone()).matches("1[0-9]{10}"))
            add(reasons, "INVALID_" + prefix + "_PHONE");
        if (blank(snapshot.getIdType()) || blank(snapshot.getIdNumber()))
            add(reasons, "MISSING_" + prefix + "_IDENTITY");
        if (blank(snapshot.getCurrentAddress())) add(reasons, "MISSING_" + prefix + "_ADDRESS");
        if (snapshot.getShopDeptId() == null || blank(snapshot.getShopDeptName())
                || snapshot.getDeptId() == null || blank(snapshot.getDeptName()))
            add(reasons, "MISSING_" + prefix + "_ORGANIZATION");
        if (snapshot.getPostId() == null || snapshot.getPostId() <= 0
                || blank(snapshot.getPostCode()) || blank(snapshot.getPostName()))
            add(reasons, "MISSING_" + prefix + "_POST");
        if (blank(snapshot.getJobGradeCode()) || blank(snapshot.getJobGradeName())
                || !Objects.equals(code(snapshot.getJobGradeCode()), code(snapshot.getJobGradeName())))
            add(reasons, "INVALID_" + prefix + "_JOB_GRADE");
        if (blank(snapshot.getWorkLocation()) || blank(snapshot.getWorkCityLevel()))
            add(reasons, "MISSING_" + prefix + "_WORK_LOCATION");
        if (snapshot.getEntryDate() == null || snapshot.getContractStartDate() == null
                || snapshot.getContractEndDate() == null
                || snapshot.getContractStartDate().isBefore(snapshot.getEntryDate())
                || !snapshot.getContractEndDate().isAfter(snapshot.getContractStartDate()))
            add(reasons, "INVALID_" + prefix + "_DATES");
        if (blank(snapshot.getContractTypeCode()) || blank(snapshot.getContractTermCode())
                || blank(snapshot.getSocialTypeCode()))
            add(reasons, "MISSING_" + prefix + "_CONTRACT_CODES");
        validateSalary(snapshot, prefix, reasons);
    }

    private void validateSalary(HrEmployeeSigningSnapshot snapshot,
            String prefix, List<String> reasons)
    {
        BigDecimal[] values = { snapshot.getBaseSalary(), snapshot.getPostSalary(),
                snapshot.getFieldAllowance(), snapshot.getPerformanceSalary(),
                snapshot.getSalaryTotal() };
        for (BigDecimal value : values)
        {
            if (!validMoney(value))
            {
                add(reasons, "INVALID_" + prefix + "_SALARY");
                return;
            }
        }
        if (snapshot.getSalaryTotal().signum() <= 0
                || snapshot.getBaseSalary().add(snapshot.getPostSalary())
                    .add(snapshot.getFieldAllowance()).add(snapshot.getPerformanceSalary())
                    .compareTo(snapshot.getSalaryTotal()) != 0
                || blank(snapshot.getSalaryVersion()))
            add(reasons, "INVALID_" + prefix + "_SALARY");
    }

    private boolean validMoney(BigDecimal value)
    {
        if (value == null || value.signum() < 0 || value.scale() > MAX_MONEY_SCALE) return false;
        long integerDigits = value.signum() == 0 ? 0L
                : Math.max((long) value.precision() - value.scale(), 0L);
        return integerDigits <= MAX_MONEY_INTEGER_DIGITS;
    }

    private boolean sameUnrelated(HrEmployeeSigningSnapshot before,
            HrEmployeeSigningSnapshot after)
    {
        return Objects.equals(before.getEmployeeNo(), after.getEmployeeNo())
                && Objects.equals(before.getEmployeeName(), after.getEmployeeName())
                && Objects.equals(before.getPhone(), after.getPhone())
                && Objects.equals(before.getIdType(), after.getIdType())
                && Objects.equals(before.getIdNumber(), after.getIdNumber())
                && Objects.equals(before.getCurrentAddress(), after.getCurrentAddress())
                && Objects.equals(before.getEmployeeStatus(), after.getEmployeeStatus())
                && Objects.equals(before.getEmployeeCategory(), after.getEmployeeCategory())
                && Objects.equals(before.getContractTypeCode(), after.getContractTypeCode())
                && Objects.equals(before.getContractTermCode(), after.getContractTermCode())
                && Objects.equals(before.getSocialTypeCode(), after.getSocialTypeCode())
                && Objects.equals(before.getRenewalCount(), after.getRenewalCount())
                && Objects.equals(before.getEntryDate(), after.getEntryDate())
                && Objects.equals(before.getContractStartDate(), after.getContractStartDate())
                && Objects.equals(before.getContractEndDate(), after.getContractEndDate())
                && Objects.equals(before.getProbationStartDate(), after.getProbationStartDate())
                && Objects.equals(before.getProbationEndDate(), after.getProbationEndDate())
                && Objects.equals(before.getActualRegularizationDate(), after.getActualRegularizationDate())
                && Objects.equals(before.getLeaveDate(), after.getLeaveDate());
    }

    private Changes changes(HrEmployeeSigningSnapshot before,
            HrEmployeeSigningSnapshot after, boolean historical)
    {
        boolean organization = !Objects.equals(before.getShopDeptId(), after.getShopDeptId())
                || !Objects.equals(before.getDeptId(), after.getDeptId())
                || !Objects.equals(trim(before.getShopDeptName()), trim(after.getShopDeptName()))
                || !Objects.equals(trim(before.getDeptName()), trim(after.getDeptName()));
        boolean post = !Objects.equals(before.getPostId(), after.getPostId())
                || !Objects.equals(trim(before.getPostCode()), trim(after.getPostCode()))
                || !Objects.equals(trim(before.getPostName()), trim(after.getPostName()));
        boolean grade = !Objects.equals(code(before.getJobGradeCode()), code(after.getJobGradeCode()));
        boolean location = !Objects.equals(trim(before.getWorkLocation()), trim(after.getWorkLocation()))
                || !Objects.equals(trim(before.getWorkCityLevel()), trim(after.getWorkCityLevel()));
        boolean salary = salaryChanged(before, after);
        boolean legal = !Objects.equals(before.getLegalEntityId(), after.getLegalEntityId())
                || !Objects.equals(code(before.getLegalEntityCode()), code(after.getLegalEntityCode()))
                || !Objects.equals(trim(before.getLegalEntityName()), trim(after.getLegalEntityName()));
        boolean reporting = !Objects.equals(before.getDirectSupervisorId(), after.getDirectSupervisorId())
                || !Objects.equals(trim(before.getDirectSupervisorName()), trim(after.getDirectSupervisorName()))
                || !Objects.equals(before.getDepartmentSupervisorId(), after.getDepartmentSupervisorId())
                || !Objects.equals(trim(before.getDepartmentSupervisorName()),
                        trim(after.getDepartmentSupervisorName()));
        List<String> risks = new ArrayList<>();
        if (historical) add(risks, "HISTORICAL_BACKFILL");
        if (legal) add(risks, "LEGAL_ENTITY_CHANGED");
        if (!sameCity(before.getWorkLocation(), after.getWorkLocation())) add(risks, "CROSS_CITY");
        if (gradeRank(after.getJobGradeCode()) < gradeRank(before.getJobGradeCode()))
            add(risks, "JOB_GRADE_DECREASED");
        if (moneyLess(after.getSalaryTotal(), before.getSalaryTotal()))
            add(risks, "SALARY_DECREASED");
        return new Changes(organization, post, grade, location, salary, legal,
                reporting, historical, List.copyOf(risks));
    }

    private boolean salaryChanged(HrEmployeeSigningSnapshot before,
            HrEmployeeSigningSnapshot after)
    {
        return !moneyEquals(before.getBaseSalary(), after.getBaseSalary())
                || !moneyEquals(before.getPostSalary(), after.getPostSalary())
                || !moneyEquals(before.getFieldAllowance(), after.getFieldAllowance())
                || !moneyEquals(before.getPerformanceSalary(), after.getPerformanceSalary())
                || !moneyEquals(before.getSalaryTotal(), after.getSalaryTotal())
                || !Objects.equals(trim(before.getSalaryVersion()), trim(after.getSalaryVersion()));
    }

    private boolean eligible(OaSignPlanVersion candidate,
            HrEmployeeSigningSnapshot after)
    {
        return candidate != null && candidate.getVersionId() != null
                && "PUBLISHED".equals(code(candidate.getPublishStatus()))
                && "ENABLED".equals(code(candidate.getMatchingStatus()))
                && SCENARIO.equals(code(candidate.getScenario()))
                && OaSignPlanScope.appliesTo(candidate.getShopDeptId(), after.getShopDeptId());
    }

    private ChangePredicate parsePredicate(String ruleJson)
    {
        try
        {
            if (blank(ruleJson)) return null;
            JsonNode root = objectMapper.readTree(ruleJson);
            if (root == null || !root.isObject()) return null;
            for (Map.Entry<String, JsonNode> field : root.properties())
            {
                JsonNode value = field.getValue();
                if (PREDICATE_FIELDS.contains(field.getKey()))
                {
                    if (value != null && !value.isNull() && !value.isBoolean()) return null;
                }
                else if (COMPATIBILITY_FIELDS.contains(field.getKey()))
                {
                    if (value != null && !value.isNull() && !value.isTextual()) return null;
                }
                else return null;
            }
            return new ChangePredicate(
                    booleanValue(root, "organizationChanged"),
                    booleanValue(root, "postChanged"),
                    booleanValue(root, "gradeChanged"),
                    booleanValue(root, "locationChanged"),
                    booleanValue(root, "salaryChanged"),
                    booleanValue(root, "legalEntityChanged"),
                    booleanValue(root, "reportingChanged"),
                    booleanValue(root, "historicalSupplement"),
                    optionalText(root, "postName"), optionalCode(root, "postLevel"),
                    optionalCode(root, "salaryVersion"));
        }
        catch (Exception ignored)
        {
            return null;
        }
    }

    private Boolean booleanValue(JsonNode root, String name)
    {
        JsonNode value = root.get(name);
        return value == null || value.isNull() ? null : value.booleanValue();
    }

    private String optionalText(JsonNode root, String name)
    {
        JsonNode value = root.get(name);
        if (value == null || value.isNull()) return null;
        return trim(value.textValue());
    }

    private String optionalCode(JsonNode root, String name)
    {
        String value = optionalText(root, name);
        return value == null ? null : code(value);
    }

    private TemplateDecision validateTemplates(OaSignPlanVersion version,
            List<OaSignPlanVersionTemplate> templates)
    {
        Set<String> types = new HashSet<>();
        boolean employeeConfirmation = false;
        boolean signingStrategy = false;
        for (OaSignPlanVersionTemplate template : templates)
        {
            if (template == null || template.getId() == null || template.getId() <= 0
                    || !Objects.equals(version.getVersionId(), template.getPlanVersionId())
                    || template.getTemplateId() == null || template.getTemplateId() <= 0
                    || blank(template.getTemplateVersion()) || blank(template.getTemplateType())
                    || blank(template.getTemplateName()) || blank(template.getSourceFileUrl())
                    || blank(template.getSourceFileHash()) || template.getSortOrder() == null
                    || template.getSortOrder() < 0
                    || !trim(template.getSourceFileHash()).matches("[0-9a-fA-F]{64}")
                    || !transferTemplateType(template.getTemplateType())
                    || !types.add(code(template.getTemplateType())))
                return new TemplateDecision(false, false);
            String signRequired = code(template.getEmployeeSignRequired());
            if (!"Y".equals(signRequired) && !"N".equals(signRequired))
                return new TemplateDecision(false, false);
            if (!blank(template.getSignaturePositionJson())
                    && !validPlacement(template.getSignaturePositionJson(), true))
                return new TemplateDecision(false, false);
            if (!blank(template.getCompanySealPositionJson())
                    && !validPlacement(template.getCompanySealPositionJson(), false))
                return new TemplateDecision(false, false);
            if ("Y".equals(signRequired))
            {
                if (blank(template.getSignaturePositionJson()))
                    return new TemplateDecision(false, false);
                employeeConfirmation = true;
                signingStrategy = true;
            }
            if (!blank(template.getCompanySealPositionJson())) signingStrategy = true;
        }
        return new TemplateDecision(signingStrategy, employeeConfirmation);
    }

    private boolean transferTemplateType(String type)
    {
        try
        {
            return "transfer".equals(OaSignTemplateType.require(code(type)).getScenario());
        }
        catch (ServiceException ignored)
        {
            return false;
        }
    }

    private boolean validPlacement(String json, boolean appendedAllowed)
    {
        try
        {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isObject()) return false;
            JsonNode mode = root.get("mode");
            if (mode != null)
            {
                if (!mode.isTextual()) return false;
                if ("APPENDED_CONFIRMATION_PAGE".equals(mode.textValue())) return appendedAllowed;
                if (!"PLACED".equals(mode.textValue())) return false;
            }
            JsonNode page = root.get("pageNumber");
            JsonNode x = root.get("x");
            JsonNode y = root.get("y");
            JsonNode width = root.get("width");
            JsonNode height = root.get("height");
            return page != null && page.isIntegralNumber() && page.intValue() >= 1
                    && finiteNonNegative(x) && finiteNonNegative(y)
                    && finitePositive(width) && finitePositive(height);
        }
        catch (Exception ignored)
        {
            return false;
        }
    }

    private boolean finiteNonNegative(JsonNode value)
    {
        return value != null && value.isNumber() && Double.isFinite(value.doubleValue())
                && value.doubleValue() >= 0;
    }

    private boolean finitePositive(JsonNode value)
    {
        return value != null && value.isNumber() && Double.isFinite(value.doubleValue())
                && value.doubleValue() > 0;
    }

    private OaSignPackage draft(HrEmployeeSigningSnapshot before,
            HrEmployeeSigningSnapshot after, LocalDate effectiveDate, boolean historical,
            OaSignPlanVersion version)
    {
        OaSignPackage draft = new OaSignPackage();
        draft.setEmployeeId(after.getEmployeeId());
        draft.setEmployeeNameSnapshot(after.getEmployeeName());
        draft.setEmployeePhoneSnapshot(after.getPhone());
        draft.setEmployeeIdCardSnapshot(after.getIdNumber());
        draft.setEmployeeAddressSnapshot(after.getCurrentAddress());
        draft.setDeptIdSnapshot(after.getDeptId());
        draft.setDeptNameSnapshot(after.getDeptName());
        draft.setShopDeptId(after.getShopDeptId());
        draft.setShopDeptName(after.getShopDeptName());
        draft.setSourcePlanId(version.getPlanId());
        draft.setSourcePlanName(version.getPlanName());
        draft.setScenario(SCENARIO);
        draft.setEmploymentType(code(after.getContractTypeCode()));
        draft.setSocialType(code(after.getSocialTypeCode()));
        draft.setPostNameSnapshot(after.getPostName());
        draft.setPostLevelSnapshot(code(after.getJobGradeCode()));
        draft.setSalaryVersion(trim(after.getSalaryVersion()));
        draft.setEntryDate(dateText(after.getEntryDate()));
        draft.setContractStartDate(dateText(after.getContractStartDate()));
        draft.setContractEndDate(dateText(after.getContractEndDate()));
        draft.setTransferEffectiveDate(effectiveDate.toString());
        draft.setHistoricalSupplement(historical);
        draft.setBeforeDeptNameSnapshot(before.getDeptName());
        draft.setBeforePostNameSnapshot(before.getPostName());
        draft.setBaseSalary(after.getBaseSalary());
        draft.setPostSalary(after.getPostSalary());
        draft.setFieldAllowance(after.getFieldAllowance());
        draft.setPerformanceSalary(after.getPerformanceSalary());
        draft.setSalaryTotal(after.getSalaryTotal());
        draft.setRemark(readableDifference(before, after, effectiveDate, historical));
        draft.setStatus(OaSignPackageStatus.DRAFT);
        draft.setVersion(0L);
        draft.setPlanVersionId(version.getVersionId());
        return draft;
    }

    private String readableDifference(HrEmployeeSigningSnapshot before,
            HrEmployeeSigningSnapshot after, LocalDate effectiveDate, boolean historical)
    {
        String value = (historical ? "历史调岗补录;" : "")
                + "生效日:" + effectiveDate
                + ";组织:" + text(before.getDeptName()) + "→" + text(after.getDeptName())
                + ";岗位:" + text(before.getPostName()) + "→" + text(after.getPostName())
                + ";职级:" + text(before.getJobGradeCode()) + "→" + text(after.getJobGradeCode())
                + ";地点:" + text(before.getWorkLocation()) + "→" + text(after.getWorkLocation())
                + ";法律主体:" + text(before.getLegalEntityName()) + "→" + text(after.getLegalEntityName())
                + ";薪资合计:" + moneyText(before.getSalaryTotal()) + "→" + moneyText(after.getSalaryTotal());
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private OaSignDraftDecision needsData(List<String> reasons, List<String> risks)
    {
        List<String> all = new ArrayList<>();
        if (reasons != null) reasons.forEach(reason -> add(all, reason));
        if (risks != null) risks.forEach(risk -> add(all, risk));
        OaSignDraftDecision decision = new OaSignDraftDecision();
        decision.setAction(OaSignDraftDecision.Action.NEEDS_DATA);
        decision.setReasonCodes(all);
        if (risks != null && !risks.isEmpty()) decision.setRiskLevel("HIGH");
        return decision;
    }

    private LocalDate effectiveDate(HrSignBusinessEvent event)
    {
        Object value = event == null ? null : attribute(event.getAttributes(), "effectiveDate");
        try
        {
            String normalized = value == null ? null : trim(String.valueOf(value));
            return normalized == null ? null : LocalDate.parse(normalized);
        }
        catch (RuntimeException ignored)
        {
            return null;
        }
    }

    private Boolean historicalMarker(HrSignBusinessEvent event)
    {
        Object value = event == null ? null
                : attribute(event.getAttributes(), "historicalSupplement");
        return value instanceof Boolean bool ? bool : null;
    }

    private Object attribute(Map<String, Object> attributes, String name)
    {
        return attributes == null ? null : attributes.get(name);
    }

    private Long positiveLong(Object value)
    {
        if (value == null) return null;
        try
        {
            long parsed;
            if (value instanceof BigDecimal decimal)
                parsed = decimal.toBigIntegerExact().longValueExact();
            else if (value instanceof BigInteger integer)
                parsed = integer.longValueExact();
            else if (value instanceof Byte || value instanceof Short
                    || value instanceof Integer || value instanceof Long)
                parsed = ((Number) value).longValue();
            else if (value instanceof Number) return null;
            else
            {
                String text = trim(String.valueOf(value));
                if (text == null || !text.matches("[1-9][0-9]*")) return null;
                parsed = Long.parseLong(text);
            }
            return parsed > 0 ? parsed : null;
        }
        catch (RuntimeException ignored)
        {
            return null;
        }
    }

    private boolean sameCity(String left, String right)
    {
        return Objects.equals(cityPrefix(left), cityPrefix(right));
    }

    private String cityPrefix(String value)
    {
        String normalized = trim(value);
        if (normalized == null) return null;
        int index = normalized.indexOf('市');
        return index < 0 ? normalized : normalized.substring(0, index + 1);
    }

    private int gradeRank(String grade)
    {
        String normalized = code(grade);
        if (normalized == null) return Integer.MAX_VALUE;
        String digits = normalized.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return Integer.MAX_VALUE;
        try
        {
            return Integer.parseInt(digits);
        }
        catch (NumberFormatException ignored)
        {
            return Integer.MAX_VALUE;
        }
    }

    private boolean moneyLess(BigDecimal left, BigDecimal right)
    {
        return left != null && right != null && left.compareTo(right) < 0;
    }

    private boolean moneyEquals(BigDecimal left, BigDecimal right)
    {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }

    private String moneyText(BigDecimal value)
    {
        return value == null ? "未填写" : value.toPlainString();
    }

    private String dateText(LocalDate value)
    {
        return value == null ? null : value.toString();
    }

    private String text(Object value)
    {
        String normalized = value == null ? null : trim(String.valueOf(value));
        return normalized == null ? "未填写" : normalized;
    }

    private String code(Object value)
    {
        String normalized = value == null ? null : trim(String.valueOf(value));
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String trim(String value)
    {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean blank(String value)
    {
        return trim(value) == null;
    }

    private void add(List<String> values, String value)
    {
        if (value != null && !values.contains(value)) values.add(value);
    }

    private record Changes(boolean organizationChanged, boolean postChanged,
            boolean gradeChanged, boolean locationChanged, boolean salaryChanged,
            boolean legalEntityChanged, boolean reportingChanged,
            boolean historicalSupplement, List<String> riskCodes)
    {
        private boolean hasBusinessChange()
        {
            return organizationChanged || postChanged || gradeChanged || locationChanged
                    || salaryChanged || legalEntityChanged || reportingChanged;
        }

        private boolean reportingOnly()
        {
            return reportingChanged && !organizationChanged && !postChanged && !gradeChanged
                    && !locationChanged && !salaryChanged && !legalEntityChanged;
        }
    }

    private record ChangePredicate(Boolean organizationChanged, Boolean postChanged,
            Boolean gradeChanged, Boolean locationChanged, Boolean salaryChanged,
            Boolean legalEntityChanged, Boolean reportingChanged,
            Boolean historicalSupplement, String postName, String postLevel,
            String salaryVersion)
    {
        private boolean matches(Changes changes, HrEmployeeSigningSnapshot after)
        {
            return matches(organizationChanged, changes.organizationChanged())
                    && matches(postChanged, changes.postChanged())
                    && matches(gradeChanged, changes.gradeChanged())
                    && matches(locationChanged, changes.locationChanged())
                    && matches(salaryChanged, changes.salaryChanged())
                    && matches(legalEntityChanged, changes.legalEntityChanged())
                    && matches(reportingChanged, changes.reportingChanged())
                    && matches(historicalSupplement, changes.historicalSupplement())
                    && (postName == null || postName.equals(trimmed(after.getPostName())))
                    && (postLevel == null || postLevel.equals(coded(after.getJobGradeCode())))
                    && (salaryVersion == null || salaryVersion.equals(coded(after.getSalaryVersion())));
        }

        private boolean matches(Boolean expected, boolean actual)
        {
            return expected == null || expected == actual;
        }

        private static String trimmed(String value)
        {
            if (value == null) return null;
            String normalized = value.trim();
            return normalized.isEmpty() ? null : normalized;
        }

        private static String coded(String value)
        {
            String normalized = trimmed(value);
            return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
        }
    }

    private record TemplateDecision(boolean valid,
            boolean employeeConfirmationRequired)
    {
    }
}
