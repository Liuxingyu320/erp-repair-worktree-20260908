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

/** 转正场景只读规则：验证不可变人事动作并选择精确的已发布方案版本。 */
@Component
public class RegularizeSignScenarioRule implements OaSignScenarioRule
{
    private static final String SCENARIO = "REGULARIZE";
    private static final String ACTION_TYPE = "REGULARIZATION_CONFIRMED";
    private static final String SOURCE_TYPE = "HR_LIFECYCLE_ACTION";
    private static final int MAX_MONEY_INTEGER_DIGITS = 14;
    private static final int MAX_MONEY_SCALE = 2;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> CHANGE_PREDICATE_FIELDS = Set.of(
            "postChanged", "gradeChanged", "salaryChanged", "organizationChanged");
    private static final Set<String> COMPATIBILITY_RULE_FIELDS = Set.of(
            "postName", "employmentType", "socialType", "servicePersonType",
            "insuranceType", "postLevel", "salaryVersion");

    private final OaSignPlanVersionMapper versionMapper;
    private final ObjectMapper objectMapper;

    public RegularizeSignScenarioRule(OaSignPlanVersionMapper versionMapper,
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
            throw new ServiceException("转正事件不一致: " + String.join(",", reasons));
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
            return observableSalaryNeedsData(event == null ? null : event.getBeforeSnapshot(),
                    event == null ? null : event.getAfterSnapshot(), reasons);
        }

        HrEmployeeSigningSnapshot before = event.getBeforeSnapshot();
        HrEmployeeSigningSnapshot after = event.getAfterSnapshot();
        LocalDate eventBusinessDate = LocalDate.ofInstant(
                event.getOccurredTime().toInstant(), BUSINESS_ZONE);
        validateSnapshots(before, after, eventBusinessDate, reasons);
        if (!reasons.isEmpty())
        {
            return observableSalaryNeedsData(before, after, reasons);
        }
        Changes changes = changes(before, after);
        String risk = changes.salaryChanged ? "REVIEW_REQUIRED" : "LOW";

        List<OaSignPlanVersion> candidates = versionMapper.selectPublishedMatchingCandidates(
                SCENARIO, after.getShopDeptId(), after.getLegalEntityId());
        List<OaSignPlanVersion> matched = new ArrayList<>();
        boolean invalidRule = false;
        if (candidates != null)
        {
            for (OaSignPlanVersion candidate : candidates)
            {
                if (!eligible(candidate, after))
                {
                    continue;
                }
                ChangePredicate predicate = parsePredicate(candidate.getRuleJson());
                if (predicate == null)
                {
                    invalidRule = true;
                    continue;
                }
                if (predicate.matches(changes, after))
                {
                    matched.add(candidate);
                }
            }
        }
        if (invalidRule)
        {
            return trustedNeedsData("PLAN_RULE_INVALID", risk, changes.salaryChanged);
        }
        if (matched.isEmpty())
        {
            return trustedNeedsData("PLAN_NOT_FOUND", risk, changes.salaryChanged);
        }
        if (matched.size() > 1)
        {
            return trustedNeedsData("PLAN_CONFLICT", risk, changes.salaryChanged);
        }

        OaSignPlanVersion version = matched.get(0);
        List<OaSignPlanVersionTemplate> templates =
                versionMapper.selectTemplatesByVersionId(version.getVersionId());
        if (templates == null || templates.isEmpty())
        {
            return trustedNeedsData(
                    "PLAN_TEMPLATE_SNAPSHOT_MISSING", risk, changes.salaryChanged);
        }
        TemplateDecision templateDecision = validateTemplates(version, templates);
        if (!templateDecision.valid)
        {
            return trustedNeedsData("PLAN_TEMPLATE_INVALID", risk, changes.salaryChanged);
        }

        if (!templateDecision.employeeConfirmationRequired)
        {
            OaSignDraftDecision noAction = new OaSignDraftDecision();
            noAction.setAction(OaSignDraftDecision.Action.NO_ACTION);
            noAction.setPlanVersionId(version.getVersionId());
            noAction.setRiskLevel(risk);
            List<String> noActionReasons = new ArrayList<>();
            noActionReasons.add("NO_EMPLOYEE_CONFIRMATION_DOCUMENT");
            noActionReasons.add("方案未包含需要员工确认的文件");
            if (changes.salaryChanged)
            {
                noActionReasons.add("SALARY_CHANGED");
            }
            noAction.setReasonCodes(noActionReasons);
            return noAction;
        }

        OaSignDraftDecision result = new OaSignDraftDecision();
        result.setAction(OaSignDraftDecision.Action.CREATE_DRAFT);
        result.setPlanVersionId(version.getVersionId());
        result.setRiskLevel(risk);
        result.setReasonCodes(changes.salaryChanged
                ? List.of("SALARY_CHANGED") : List.of());
        result.setDraftPackage(draft(before, after, version));
        return result;
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
        Long attributeActionVersion = positiveLong(
                attribute(attributes, "sourceActionVersion"));
        if (attributeActionVersion == null
                || !Objects.equals(event.getSourceEventVersion(), attributeActionVersion))
            add(reasons, "SOURCE_ACTION_VERSION_MISMATCH");
    }

    private void validateSnapshots(HrEmployeeSigningSnapshot before,
            HrEmployeeSigningSnapshot after, LocalDate eventBusinessDate,
            List<String> reasons)
    {
        if (before == null || after == null)
        {
            return;
        }
        if (!"试用".equals(trim(before.getEmployeeStatus()))
                || !"正式".equals(trim(after.getEmployeeStatus())))
            add(reasons, "INVALID_STATUS_TRANSITION");
        if (before.getActualRegularizationDate() != null)
            add(reasons, "INVALID_BEFORE_REGULARIZATION_DATE");
        LocalDate actualDate = after.getActualRegularizationDate();
        if (actualDate == null
                || (after.getEntryDate() != null && actualDate.isBefore(after.getEntryDate()))
                || (after.getProbationStartDate() != null
                    && actualDate.isBefore(after.getProbationStartDate()))
                || (after.getContractEndDate() != null
                    && actualDate.isAfter(after.getContractEndDate()))
                || actualDate.isAfter(eventBusinessDate))
            add(reasons, "INVALID_REGULARIZATION_DATE");

        if (!sameOrganization(before, after))
            add(reasons, "ORGANIZATION_CHANGED");
        if (!sameUnrelatedSnapshot(before, after))
            add(reasons, "UNRELATED_SNAPSHOT_CHANGED");

        validateRequiredSnapshot(before, "BEFORE", reasons);
        validateRequiredSnapshot(after, "AFTER", reasons);
    }

    private void validateRequiredSnapshot(HrEmployeeSigningSnapshot snapshot,
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
                || !Objects.equals(code(snapshot.getJobGradeCode()),
                        code(snapshot.getJobGradeName())))
            add(reasons, "INVALID_" + prefix + "_JOB_GRADE");
        if (snapshot.getEntryDate() == null || snapshot.getContractStartDate() == null
                || snapshot.getContractEndDate() == null
                || snapshot.getProbationStartDate() == null
                || snapshot.getProbationEndDate() == null)
            add(reasons, "MISSING_" + prefix + "_DATES");
        else if (snapshot.getContractStartDate().isBefore(snapshot.getEntryDate())
                || !snapshot.getContractEndDate().isAfter(snapshot.getContractStartDate())
                || snapshot.getProbationStartDate().isBefore(snapshot.getEntryDate())
                || snapshot.getProbationStartDate().isBefore(snapshot.getContractStartDate())
                || snapshot.getProbationEndDate().isBefore(snapshot.getProbationStartDate())
                || snapshot.getProbationEndDate().isAfter(snapshot.getContractEndDate()))
            add(reasons, "INVALID_" + prefix + "_DATES");
        if (blank(snapshot.getContractTypeCode()) || blank(snapshot.getContractTermCode())
                || blank(snapshot.getSocialTypeCode()))
            add(reasons, "MISSING_" + prefix + "_CONTRACT_CODES");
        validateSalarySnapshot(snapshot, prefix, reasons);
    }

    private void validateSalarySnapshot(HrEmployeeSigningSnapshot snapshot,
            String prefix, List<String> reasons)
    {
        BigDecimal[] salary = {
                snapshot.getBaseSalary(), snapshot.getPostSalary(),
                snapshot.getFieldAllowance(), snapshot.getPerformanceSalary(),
                snapshot.getSalaryTotal()
        };
        for (BigDecimal value : salary)
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
        if (value == null || value.signum() < 0 || value.scale() > MAX_MONEY_SCALE)
        {
            return false;
        }
        long integerDigits = value.signum() == 0
                ? 0L : Math.max((long) value.precision() - value.scale(), 0L);
        return integerDigits <= MAX_MONEY_INTEGER_DIGITS;
    }

    private boolean sameOrganization(HrEmployeeSigningSnapshot before,
            HrEmployeeSigningSnapshot after)
    {
        return Objects.equals(before.getShopDeptId(), after.getShopDeptId())
                && Objects.equals(before.getShopDeptName(), after.getShopDeptName())
                && Objects.equals(before.getDeptId(), after.getDeptId())
                && Objects.equals(before.getDeptName(), after.getDeptName());
    }

    private boolean sameUnrelatedSnapshot(HrEmployeeSigningSnapshot before,
            HrEmployeeSigningSnapshot after)
    {
        return Objects.equals(before.getEmployeeNo(), after.getEmployeeNo())
                && Objects.equals(before.getEmployeeName(), after.getEmployeeName())
                && Objects.equals(before.getPhone(), after.getPhone())
                && Objects.equals(before.getIdType(), after.getIdType())
                && Objects.equals(before.getIdNumber(), after.getIdNumber())
                && Objects.equals(before.getCurrentAddress(), after.getCurrentAddress())
                && Objects.equals(before.getEmployeeCategory(), after.getEmployeeCategory())
                && Objects.equals(before.getDirectSupervisorId(), after.getDirectSupervisorId())
                && Objects.equals(before.getDirectSupervisorName(), after.getDirectSupervisorName())
                && Objects.equals(before.getDepartmentSupervisorId(), after.getDepartmentSupervisorId())
                && Objects.equals(before.getDepartmentSupervisorName(), after.getDepartmentSupervisorName())
                && Objects.equals(before.getWorkLocation(), after.getWorkLocation())
                && Objects.equals(before.getWorkCityLevel(), after.getWorkCityLevel())
                && Objects.equals(before.getContractTypeCode(), after.getContractTypeCode())
                && Objects.equals(before.getContractTermCode(), after.getContractTermCode())
                && Objects.equals(before.getSocialTypeCode(), after.getSocialTypeCode())
                && Objects.equals(before.getRenewalCount(), after.getRenewalCount())
                && Objects.equals(before.getEntryDate(), after.getEntryDate())
                && Objects.equals(before.getContractStartDate(), after.getContractStartDate())
                && Objects.equals(before.getContractEndDate(), after.getContractEndDate())
                && Objects.equals(before.getProbationStartDate(), after.getProbationStartDate())
                && Objects.equals(before.getProbationEndDate(), after.getProbationEndDate())
                && Objects.equals(before.getLeaveDate(), after.getLeaveDate());
    }

    private Changes changes(HrEmployeeSigningSnapshot before,
            HrEmployeeSigningSnapshot after)
    {
        boolean postChanged = !Objects.equals(before.getPostId(), after.getPostId())
                || !Objects.equals(before.getPostCode(), after.getPostCode())
                || !Objects.equals(before.getPostName(), after.getPostName());
        boolean gradeChanged = !Objects.equals(code(before.getJobGradeCode()),
                    code(after.getJobGradeCode()))
                || !Objects.equals(code(before.getJobGradeName()),
                    code(after.getJobGradeName()));
        boolean salaryChanged = observableSalaryChanged(before, after);
        return new Changes(postChanged, gradeChanged, salaryChanged,
                !sameOrganization(before, after));
    }

    private boolean observableSalaryChanged(HrEmployeeSigningSnapshot before,
            HrEmployeeSigningSnapshot after)
    {
        return before != null && after != null
                && (!moneyEquals(before.getBaseSalary(), after.getBaseSalary())
                || !moneyEquals(before.getPostSalary(), after.getPostSalary())
                || !moneyEquals(before.getFieldAllowance(), after.getFieldAllowance())
                || !moneyEquals(before.getPerformanceSalary(), after.getPerformanceSalary())
                || !moneyEquals(before.getSalaryTotal(), after.getSalaryTotal())
                || !Objects.equals(trim(before.getSalaryVersion()),
                    trim(after.getSalaryVersion())));
    }

    private boolean eligible(OaSignPlanVersion candidate,
            HrEmployeeSigningSnapshot snapshot)
    {
        return candidate != null && candidate.getVersionId() != null
                && "PUBLISHED".equals(code(candidate.getPublishStatus()))
                && "ENABLED".equals(code(candidate.getMatchingStatus()))
                && SCENARIO.equals(code(candidate.getScenario()))
                && OaSignPlanScope.appliesTo(candidate.getShopDeptId(), snapshot.getShopDeptId());
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
                if (CHANGE_PREDICATE_FIELDS.contains(field.getKey()))
                {
                    if (value != null && !value.isNull() && !value.isBoolean()) return null;
                }
                else if (COMPATIBILITY_RULE_FIELDS.contains(field.getKey()))
                {
                    if (value != null && !value.isNull() && !value.isTextual()) return null;
                }
                else
                {
                    return null;
                }
            }
            if (hasNonNullValue(root, "servicePersonType")
                    || hasNonNullValue(root, "insuranceType"))
            {
                return null;
            }
            String postName = optionalText(root, "postName");
            String employmentTypeRaw = optionalText(root, "employmentType");
            String socialTypeRaw = optionalText(root, "socialType");
            String postLevel = optionalCode(root, "postLevel");
            String salaryVersion = optionalCode(root, "salaryVersion");
            String contractTypeCode = contractTypeCode(employmentTypeRaw);
            String socialTypeCode = socialTypeCode(socialTypeRaw);
            if (invalidOptionalText(root, "postName", postName)
                    || invalidOptionalText(root, "employmentType", employmentTypeRaw)
                    || invalidOptionalText(root, "socialType", socialTypeRaw)
                    || invalidOptionalText(root, "postLevel", postLevel)
                    || invalidOptionalText(root, "salaryVersion", salaryVersion)
                    || (employmentTypeRaw != null && contractTypeCode == null)
                    || (socialTypeRaw != null && socialTypeCode == null))
            {
                return null;
            }
            return new ChangePredicate(booleanValue(root, "postChanged"),
                    booleanValue(root, "gradeChanged"),
                    booleanValue(root, "salaryChanged"),
                    booleanValue(root, "organizationChanged"), postName,
                    contractTypeCode, socialTypeCode, postLevel, salaryVersion);
        }
        catch (Exception ignored)
        {
            return null;
        }
    }

    private Boolean booleanValue(JsonNode root, String field)
    {
        JsonNode node = root.get(field);
        return node == null || node.isNull() ? null : node.booleanValue();
    }

    private boolean hasNonNullValue(JsonNode root, String field)
    {
        JsonNode node = root.get(field);
        return node != null && !node.isNull();
    }

    private String optionalText(JsonNode root, String field)
    {
        JsonNode node = root.get(field);
        return node == null || node.isNull() ? null : trim(node.textValue());
    }

    private String optionalCode(JsonNode root, String field)
    {
        String value = optionalText(root, field);
        return value == null ? null : code(value);
    }

    private boolean invalidOptionalText(JsonNode root, String field, String normalized)
    {
        return hasNonNullValue(root, field) && normalized == null;
    }

    private String contractTypeCode(String value)
    {
        if (value == null) return null;
        if ("劳动合同".equals(value)) return "LABOR_CONTRACT";
        if ("劳务合同".equals(value)) return "SERVICE_CONTRACT";
        String normalized = code(value);
        return "LABOR_CONTRACT".equals(normalized)
                || "SERVICE_CONTRACT".equals(normalized) ? normalized : null;
    }

    private String socialTypeCode(String value)
    {
        if (value == null) return null;
        if ("有社保".equals(value)) return "SOCIAL_INSURED";
        if ("无社保".equals(value)) return "SOCIAL_UNINSURED";
        String normalized = code(value);
        return "SOCIAL_INSURED".equals(normalized)
                || "SOCIAL_UNINSURED".equals(normalized) ? normalized : null;
    }

    private TemplateDecision validateTemplates(OaSignPlanVersion version,
            List<OaSignPlanVersionTemplate> templates)
    {
        Set<String> templateTypes = new HashSet<>();
        boolean employeeConfirmationRequired = false;
        boolean hasSigningStrategy = false;
        for (OaSignPlanVersionTemplate template : templates)
        {
            if (template == null || template.getId() == null || template.getId() <= 0
                    || !Objects.equals(version.getVersionId(), template.getPlanVersionId())
                    || template.getTemplateId() == null || template.getTemplateId() <= 0
                    || blank(template.getTemplateVersion()) || blank(template.getTemplateType())
                    || blank(template.getTemplateName()) || blank(template.getSourceFileUrl())
                    || blank(template.getSourceFileHash()) || template.getSortOrder() == null
                    || template.getSortOrder() < 0)
            {
                return new TemplateDecision(false, false);
            }
            String signRequired = code(template.getEmployeeSignRequired());
            if (!"Y".equals(signRequired) && !"N".equals(signRequired))
            {
                return new TemplateDecision(false, false);
            }
            if (!trim(template.getSourceFileHash()).matches("[0-9a-fA-F]{64}")
                    || !regularizeTemplateType(template.getTemplateType())
                    || !templateTypes.add(code(template.getTemplateType())))
            {
                return new TemplateDecision(false, false);
            }
            if (!blank(template.getSignaturePositionJson())
                    && !validPlacement(template.getSignaturePositionJson(), true))
            {
                return new TemplateDecision(false, false);
            }
            if (!blank(template.getCompanySealPositionJson())
                    && !validPlacement(template.getCompanySealPositionJson(), false))
            {
                return new TemplateDecision(false, false);
            }
            if ("Y".equals(signRequired))
            {
                if (blank(template.getSignaturePositionJson()))
                {
                    return new TemplateDecision(false, false);
                }
                employeeConfirmationRequired = true;
                hasSigningStrategy = true;
            }
            if (!blank(template.getCompanySealPositionJson()))
            {
                hasSigningStrategy = true;
            }
        }
        return new TemplateDecision(hasSigningStrategy, employeeConfirmationRequired);
    }

    private boolean regularizeTemplateType(String templateType)
    {
        try
        {
            return "regularize".equals(
                    OaSignTemplateType.require(code(templateType)).getScenario());
        }
        catch (ServiceException ignored)
        {
            return false;
        }
    }

    private boolean validPlacement(String json, boolean appendedPageAllowed)
    {
        try
        {
            JsonNode placement = objectMapper.readTree(json);
            if (placement == null || !placement.isObject()) return false;
            JsonNode mode = placement.get("mode");
            if (mode != null)
            {
                if (!mode.isTextual()) return false;
                if ("APPENDED_CONFIRMATION_PAGE".equals(mode.textValue()))
                {
                    return appendedPageAllowed;
                }
                if (!"PLACED".equals(mode.textValue())) return false;
            }
            JsonNode pageNumber = placement.get("pageNumber");
            JsonNode x = placement.get("x");
            JsonNode y = placement.get("y");
            JsonNode width = placement.get("width");
            JsonNode height = placement.get("height");
            if (pageNumber == null || !pageNumber.isIntegralNumber()
                    || x == null || !x.isNumber() || y == null || !y.isNumber()
                    || width == null || !width.isNumber()
                    || height == null || !height.isNumber())
            {
                return false;
            }
            double xValue = x.doubleValue();
            double yValue = y.doubleValue();
            double widthValue = width.doubleValue();
            double heightValue = height.doubleValue();
            return pageNumber.intValue() >= 1
                    && Double.isFinite(xValue) && Double.isFinite(yValue)
                    && xValue >= 0 && yValue >= 0
                    && Double.isFinite(widthValue) && Double.isFinite(heightValue)
                    && widthValue > 0 && heightValue > 0;
        }
        catch (Exception ignored)
        {
            return false;
        }
    }

    private OaSignPackage draft(HrEmployeeSigningSnapshot before,
            HrEmployeeSigningSnapshot after, OaSignPlanVersion version)
    {
        OaSignPackage draft = new OaSignPackage();
        draft.setEmployeeId(after.getEmployeeId());
        draft.setDeptIdSnapshot(after.getDeptId());
        draft.setDeptNameSnapshot(after.getDeptName());
        draft.setShopDeptId(after.getShopDeptId());
        draft.setShopDeptName(after.getShopDeptName());
        draft.setSourcePlanId(version.getPlanId());
        draft.setSourcePlanName(version.getPlanName());
        draft.setEmployeeNameSnapshot(after.getEmployeeName());
        draft.setEmployeePhoneSnapshot(after.getPhone());
        draft.setEmployeeIdCardSnapshot(after.getIdNumber());
        draft.setEmployeeAddressSnapshot(after.getCurrentAddress());
        draft.setScenario(SCENARIO);
        draft.setEmploymentType(code(after.getContractTypeCode()));
        draft.setSocialType(code(after.getSocialTypeCode()));
        draft.setPostNameSnapshot(after.getPostName());
        draft.setPostLevelSnapshot(code(after.getJobGradeCode()));
        draft.setSalaryVersion(trim(after.getSalaryVersion()));
        draft.setEntryDate(dateText(after.getEntryDate()));
        draft.setContractStartDate(dateText(after.getContractStartDate()));
        draft.setContractEndDate(dateText(after.getContractEndDate()));
        draft.setProbationStartDate(dateText(after.getProbationStartDate()));
        draft.setProbationEndDate(dateText(after.getProbationEndDate()));
        draft.setActualRegularizationDate(dateText(after.getActualRegularizationDate()));
        draft.setBaseSalary(after.getBaseSalary());
        draft.setPostSalary(after.getPostSalary());
        draft.setFieldAllowance(after.getFieldAllowance());
        draft.setPerformanceSalary(after.getPerformanceSalary());
        draft.setSalaryTotal(after.getSalaryTotal());
        draft.setRemark(readableDifference(before, after));
        draft.setStatus(OaSignPackageStatus.DRAFT);
        draft.setVersion(0L);
        draft.setPlanVersionId(version.getVersionId());
        return draft;
    }

    private String readableDifference(HrEmployeeSigningSnapshot before,
            HrEmployeeSigningSnapshot after)
    {
        String value = "状态:" + text(before.getEmployeeStatus()) + "→" + text(after.getEmployeeStatus())
                + ";实际转正日:" + dateTextReadable(before.getActualRegularizationDate())
                + "→" + dateTextReadable(after.getActualRegularizationDate())
                + ";组织:" + organizationText(before) + "→" + organizationText(after)
                + ";岗位:" + postText(before) + "→" + postText(after)
                + ";职级:" + text(before.getJobGradeCode()) + "→" + text(after.getJobGradeCode())
                + ";基本工资:" + moneyText(before.getBaseSalary()) + "→" + moneyText(after.getBaseSalary())
                + ";岗位工资:" + moneyText(before.getPostSalary()) + "→" + moneyText(after.getPostSalary())
                + ";外勤补贴:" + moneyText(before.getFieldAllowance()) + "→" + moneyText(after.getFieldAllowance())
                + ";绩效工资:" + moneyText(before.getPerformanceSalary()) + "→" + moneyText(after.getPerformanceSalary())
                + ";薪资合计:" + moneyText(before.getSalaryTotal()) + "→" + moneyText(after.getSalaryTotal())
                + ";薪资版本:" + text(before.getSalaryVersion()) + "→" + text(after.getSalaryVersion());
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private String organizationText(HrEmployeeSigningSnapshot snapshot)
    {
        return text(snapshot.getShopDeptName()) + "/" + text(snapshot.getDeptName());
    }

    private String postText(HrEmployeeSigningSnapshot snapshot)
    {
        return text(snapshot.getPostName()) + "(" + text(snapshot.getPostCode())
                + "/" + snapshot.getPostId() + ")";
    }

    private OaSignDraftDecision needsData(String reason)
    {
        return needsData(List.of(reason));
    }

    private OaSignDraftDecision needsData(List<String> reasons)
    {
        OaSignDraftDecision decision = new OaSignDraftDecision();
        decision.setAction(OaSignDraftDecision.Action.NEEDS_DATA);
        decision.setReasonCodes(reasons == null ? List.of() : List.copyOf(reasons));
        return decision;
    }

    private OaSignDraftDecision observableSalaryNeedsData(
            HrEmployeeSigningSnapshot before, HrEmployeeSigningSnapshot after,
            List<String> reasons)
    {
        if (!observableSalaryChanged(before, after))
        {
            return needsData(reasons);
        }
        List<String> enrichedReasons = new ArrayList<>(
                reasons == null ? List.of() : reasons);
        add(enrichedReasons, "SALARY_CHANGED");
        OaSignDraftDecision decision = needsData(enrichedReasons);
        decision.setRiskLevel("REVIEW_REQUIRED");
        return decision;
    }

    private OaSignDraftDecision trustedNeedsData(String reason, String risk,
            boolean salaryChanged)
    {
        List<String> reasons = new ArrayList<>();
        add(reasons, reason);
        if (salaryChanged)
        {
            add(reasons, "SALARY_CHANGED");
        }
        OaSignDraftDecision decision = needsData(reasons);
        decision.setRiskLevel(risk);
        return decision;
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
            {
                parsed = decimal.toBigIntegerExact().longValueExact();
            }
            else if (value instanceof BigInteger integer)
            {
                parsed = integer.longValueExact();
            }
            else if (value instanceof Byte || value instanceof Short
                    || value instanceof Integer || value instanceof Long)
            {
                parsed = ((Number) value).longValue();
            }
            else if (value instanceof Number)
            {
                return null;
            }
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

    private String dateTextReadable(LocalDate value)
    {
        return value == null ? "未填写" : value.toString();
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
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean blank(String value)
    {
        return trim(value) == null;
    }

    private void add(List<String> reasons, String reason)
    {
        if (reason != null && !reasons.contains(reason)) reasons.add(reason);
    }

    private record Changes(boolean postChanged, boolean gradeChanged,
            boolean salaryChanged, boolean organizationChanged)
    {
    }

    private record ChangePredicate(Boolean postChanged, Boolean gradeChanged,
            Boolean salaryChanged, Boolean organizationChanged, String postName,
            String contractTypeCode, String socialTypeCode, String postLevel,
            String salaryVersion)
    {
        private boolean matches(Changes changes, HrEmployeeSigningSnapshot snapshot)
        {
            return matches(postChanged, changes.postChanged)
                    && matches(gradeChanged, changes.gradeChanged)
                    && matches(salaryChanged, changes.salaryChanged)
                    && matches(organizationChanged, changes.organizationChanged)
                    && (postName == null || postName.equals(trimmed(snapshot.getPostName())))
                    && (contractTypeCode == null
                        || contractTypeCode.equals(coded(snapshot.getContractTypeCode())))
                    && (socialTypeCode == null
                        || socialTypeCode.equals(coded(snapshot.getSocialTypeCode())))
                    && (postLevel == null
                        || postLevel.equals(coded(snapshot.getJobGradeCode())))
                    && (salaryVersion == null
                        || salaryVersion.equals(coded(snapshot.getSalaryVersion())));
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
