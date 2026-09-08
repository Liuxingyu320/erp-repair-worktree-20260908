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
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPlanVersion;
import com.erp.oa.domain.OaSignPlanVersionTemplate;
import com.erp.oa.domain.vo.OaSignDraftDecision;
import com.erp.oa.mapper.OaSignPlanVersionMapper;

/** 离职场景只读规则：仅消费 System 冻结快照并把材料停在单 HR 确认。 */
@Component
public class OffboardSignScenarioRule implements OaSignScenarioRule
{
    private static final String SCENARIO = "OFFBOARD";
    private static final String ACTION_TYPE = "OFFBOARD_CONFIRMED";
    private static final String SOURCE_TYPE = "HR_LIFECYCLE_ACTION";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> RULE_FIELDS = Set.of(
            "offboardingTypes", "salarySettlementStatuses",
            "assetHandoverStatuses", "nonCompeteDecisions", "riskLevels",
            "historicalSupplement");
    private static final Set<String> OFFBOARDING_TYPES = Set.of(
            "VOLUNTARY_EXPECTED", "VOLUNTARY_UNEXPECTED", "TERMINATION",
            "DISCIPLINARY_TERMINATION", "DISPUTED_TERMINATION");
    private static final Set<String> COMPLETION_STATUSES = Set.of("COMPLETED", "PENDING");
    private static final Set<String> NON_COMPETE_DECISIONS = Set.of(
            "NOT_APPLICABLE", "REQUIRED", "PENDING");
    private static final Set<String> RISK_LEVELS = Set.of("LOW", "HIGH");
    private static final Set<String> OFFBOARD_TEMPLATE_TYPES = Set.of(
            "OFFBOARD_CONFIRMATION", "OFFBOARD_HANDOVER", "OFFBOARD_SETTLEMENT",
            "OFFBOARD_CONFIDENTIALITY_NONCOMPETE", "OFFBOARD_TERMINATION_NOTICE",
            "OFFBOARD_LEAVE_CERTIFICATE");

    private final OaSignPlanVersionMapper versionMapper;
    private final ObjectMapper objectMapper;

    public OffboardSignScenarioRule(OaSignPlanVersionMapper versionMapper,
            ObjectMapper objectMapper)
    {
        this.versionMapper = versionMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String scenario)
    {
        return SCENARIO.equals(code(scenario));
    }

    @Override
    public String dedupeKey(HrSignBusinessEvent event)
    {
        List<String> reasons = new ArrayList<>();
        validateEnvelope(event, reasons);
        if (!reasons.isEmpty())
            throw new ServiceException("离职事件不一致: " + String.join(",", reasons));
        return SCENARIO + ":" + event.getEmployeeId() + ":"
                + event.getSourceBusinessId() + ":" + event.getSourceEventVersion();
    }

    @Override
    public OaSignDraftDecision decide(HrSignBusinessEvent event)
    {
        List<String> reasons = new ArrayList<>();
        validateEnvelope(event, reasons);
        if (!reasons.isEmpty()) return needsData(reasons, List.of());

        LocalDate effectiveDate = effectiveDate(event);
        LocalDate operationDate = LocalDate.ofInstant(
                event.getOccurredTime().toInstant(), BUSINESS_ZONE);
        if (effectiveDate.isAfter(operationDate))
            return needsData(List.of("FUTURE_OFFBOARD_EVENT"), List.of());
        boolean historical = effectiveDate.isBefore(operationDate);
        if (!Objects.equals(historicalMarker(event), historical))
            return needsData(List.of("HISTORICAL_MARKER_MISMATCH"),
                    historical ? List.of("HISTORICAL_OFFBOARDING") : List.of());

        validateSnapshots(event.getBeforeSnapshot(), event.getAfterSnapshot(),
                effectiveDate, reasons);
        if (!reasons.isEmpty()) return needsData(reasons, List.of());

        List<String> risks = riskCodes(event.getAfterSnapshot(), historical);
        String riskLevel = risks.isEmpty() ? "LOW" : "HIGH";
        if (!riskLevel.equals(code(attribute(event, "riskLevel"))))
            return needsData(List.of("RISK_LEVEL_MISMATCH"), risks);

        HrEmployeeSigningSnapshot after = event.getAfterSnapshot();
        List<OaSignPlanVersion> candidates = versionMapper.selectPublishedMatchingCandidates(
                SCENARIO, after.getShopDeptId(), after.getLegalEntityId());
        List<OaSignPlanVersion> matched = new ArrayList<>();
        boolean invalidRule = false;
        if (candidates != null)
        {
            for (OaSignPlanVersion candidate : candidates)
            {
                if (!eligible(candidate, after)) continue;
                OffboardPredicate predicate = parsePredicate(candidate.getRuleJson());
                if (predicate == null)
                {
                    invalidRule = true;
                    continue;
                }
                if (predicate.matches(after, riskLevel, historical)) matched.add(candidate);
            }
        }
        if (invalidRule) return needsData(List.of("PLAN_RULE_INVALID"), risks);
        if (matched.isEmpty()) return needsData(List.of("PLAN_NOT_FOUND"), risks);
        if (matched.size() > 1) return needsData(List.of("PLAN_CONFLICT"), risks);

        OaSignPlanVersion version = matched.get(0);
        List<OaSignPlanVersionTemplate> templates =
                versionMapper.selectTemplatesByVersionId(version.getVersionId());
        if (templates == null || templates.isEmpty())
            return needsData(List.of("PLAN_TEMPLATE_SNAPSHOT_MISSING"), risks);
        TemplateDecision templateDecision = validateTemplates(version, templates);
        if (!templateDecision.valid())
            return needsData(List.of("PLAN_TEMPLATE_INVALID"), risks);
        if (!templateDecision.employeeConfirmationRequired())
        {
            OaSignDraftDecision decision = new OaSignDraftDecision();
            decision.setAction(OaSignDraftDecision.Action.NO_ACTION);
            decision.setPlanVersionId(version.getVersionId());
            decision.setRiskLevel(riskLevel);
            List<String> noActionReasons = new ArrayList<>(risks);
            add(noActionReasons, "NO_EMPLOYEE_CONFIRMATION_DOCUMENT");
            add(noActionReasons, "方案未包含需要员工确认的文件");
            decision.setReasonCodes(noActionReasons);
            return decision;
        }

        OaSignDraftDecision decision = new OaSignDraftDecision();
        decision.setAction(OaSignDraftDecision.Action.CREATE_DRAFT);
        decision.setPlanVersionId(version.getVersionId());
        decision.setRiskLevel(riskLevel);
        decision.setReasonCodes(risks);
        decision.setDraftPackage(draft(event.getBeforeSnapshot(), after,
                effectiveDate, historical, version));
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
        if (event.getBeforeSnapshot() == null) add(reasons, "MISSING_BEFORE_SNAPSHOT");
        if (event.getAfterSnapshot() == null) add(reasons, "MISSING_AFTER_SNAPSHOT");
        if (event.getBeforeSnapshot() != null
                && !Objects.equals(event.getEmployeeId(),
                        event.getBeforeSnapshot().getEmployeeId()))
            add(reasons, "BEFORE_EMPLOYEE_ID_MISMATCH");
        if (event.getAfterSnapshot() != null
                && !Objects.equals(event.getEmployeeId(),
                        event.getAfterSnapshot().getEmployeeId()))
            add(reasons, "AFTER_EMPLOYEE_ID_MISMATCH");

        if (!ACTION_TYPE.equals(code(attribute(event, "actionType"))))
            add(reasons, "ACTION_TYPE_MISMATCH");
        Long attributeActionId = positiveLong(attribute(event, "sourceActionId"));
        if (attributeActionId == null || !Objects.equals(sourceActionId, attributeActionId))
            add(reasons, "SOURCE_ACTION_ID_MISMATCH");
        Long attributeVersion = positiveLong(attribute(event, "sourceActionVersion"));
        if (attributeVersion == null
                || !Objects.equals(event.getSourceEventVersion(), attributeVersion))
            add(reasons, "SOURCE_ACTION_VERSION_MISMATCH");
        if (effectiveDate(event) == null) add(reasons, "INVALID_EFFECTIVE_DATE");
        if (historicalMarker(event) == null) add(reasons, "INVALID_HISTORICAL_MARKER");
        if (!RISK_LEVELS.contains(code(attribute(event, "riskLevel"))))
            add(reasons, "INVALID_RISK_LEVEL");
    }

    private void validateSnapshots(HrEmployeeSigningSnapshot before,
            HrEmployeeSigningSnapshot after, LocalDate effectiveDate, List<String> reasons)
    {
        if (before == null || after == null) return;
        validateRequired(before, "BEFORE", reasons);
        validateRequired(after, "AFTER", reasons);
        if ("离职".equals(trim(before.getEmployeeStatus()))
                || !"0".equals(trim(before.getAccountStatus()))
                || !"离职".equals(trim(after.getEmployeeStatus()))
                || !"1".equals(trim(after.getAccountStatus()))
                || !Objects.equals(effectiveDate, after.getLeaveDate()))
            add(reasons, "INVALID_STATUS_TRANSITION");
        if (!sameUnrelated(before, after)) add(reasons, "UNRELATED_SNAPSHOT_CHANGED");
        if (!OFFBOARDING_TYPES.contains(code(after.getOffboardingType())))
            add(reasons, "INVALID_OFFBOARDING_TYPE");
        if (blank(after.getLeaveReason())) add(reasons, "MISSING_LEAVE_REASON");
        if (!COMPLETION_STATUSES.contains(code(after.getSalarySettlementStatus())))
            add(reasons, "INVALID_SALARY_SETTLEMENT_STATUS");
        if (!COMPLETION_STATUSES.contains(code(after.getAssetHandoverStatus())))
            add(reasons, "INVALID_ASSET_HANDOVER_STATUS");
        if (!NON_COMPETE_DECISIONS.contains(code(after.getNonCompeteDecision())))
            add(reasons, "INVALID_NON_COMPETE_DECISION");
        if (after.getCompensationAmount() == null
                || after.getCompensationAmount().signum() < 0
                || after.getCompensationAmount().scale() > 2)
            add(reasons, "INVALID_COMPENSATION_AMOUNT");
    }

    private void validateRequired(HrEmployeeSigningSnapshot snapshot,
            String prefix, List<String> reasons)
    {
        if (snapshot.getEmployeeId() == null || snapshot.getEmployeeId() <= 0
                || blank(snapshot.getEmployeeName()) || blank(snapshot.getPhone())
                || blank(snapshot.getIdType()) || blank(snapshot.getIdNumber())
                || blank(snapshot.getCurrentAddress()))
            add(reasons, "MISSING_" + prefix + "_EMPLOYEE");
        if (snapshot.getShopDeptId() == null || blank(snapshot.getShopDeptName())
                || snapshot.getDeptId() == null || blank(snapshot.getDeptName()))
            add(reasons, "MISSING_" + prefix + "_ORGANIZATION");
        if (snapshot.getPostId() == null || snapshot.getPostId() <= 0
                || blank(snapshot.getPostCode()) || blank(snapshot.getPostName()))
            add(reasons, "MISSING_" + prefix + "_POST");
        if (snapshot.getEntryDate() == null || snapshot.getContractStartDate() == null
                || snapshot.getContractEndDate() == null)
            add(reasons, "MISSING_" + prefix + "_CONTRACT_DATES");
        if (snapshot.getBaseSalary() == null || snapshot.getPostSalary() == null
                || snapshot.getFieldAllowance() == null
                || snapshot.getPerformanceSalary() == null
                || snapshot.getSalaryTotal() == null || blank(snapshot.getSalaryVersion()))
            add(reasons, "MISSING_" + prefix + "_SALARY");
    }

    private boolean sameUnrelated(HrEmployeeSigningSnapshot before,
            HrEmployeeSigningSnapshot after)
    {
        return Objects.equals(before.getEmployeeId(), after.getEmployeeId())
                && Objects.equals(before.getEmployeeNo(), after.getEmployeeNo())
                && Objects.equals(before.getEmployeeName(), after.getEmployeeName())
                && Objects.equals(before.getPhone(), after.getPhone())
                && Objects.equals(before.getIdType(), after.getIdType())
                && Objects.equals(before.getIdNumber(), after.getIdNumber())
                && Objects.equals(before.getCurrentAddress(), after.getCurrentAddress())
                && Objects.equals(before.getEmployeeCategory(), after.getEmployeeCategory())
                && Objects.equals(before.getShopDeptId(), after.getShopDeptId())
                && Objects.equals(before.getShopDeptName(), after.getShopDeptName())
                && Objects.equals(before.getDeptId(), after.getDeptId())
                && Objects.equals(before.getDeptName(), after.getDeptName())
                && Objects.equals(before.getPostId(), after.getPostId())
                && Objects.equals(before.getPostCode(), after.getPostCode())
                && Objects.equals(before.getPostName(), after.getPostName())
                && Objects.equals(before.getJobGradeCode(), after.getJobGradeCode())
                && Objects.equals(before.getJobGradeName(), after.getJobGradeName())
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
                && Objects.equals(before.getActualRegularizationDate(), after.getActualRegularizationDate())
                && Objects.equals(before.getTransferEffectiveDate(), after.getTransferEffectiveDate())
                && moneyEquals(before.getBaseSalary(), after.getBaseSalary())
                && moneyEquals(before.getPostSalary(), after.getPostSalary())
                && moneyEquals(before.getFieldAllowance(), after.getFieldAllowance())
                && moneyEquals(before.getPerformanceSalary(), after.getPerformanceSalary())
                && moneyEquals(before.getSalaryTotal(), after.getSalaryTotal())
                && Objects.equals(before.getSalaryVersion(), after.getSalaryVersion());
    }

    private List<String> riskCodes(HrEmployeeSigningSnapshot after, boolean historical)
    {
        List<String> risks = new ArrayList<>();
        if (historical) add(risks, "HISTORICAL_OFFBOARDING");
        if (!"VOLUNTARY_EXPECTED".equals(code(after.getOffboardingType())))
            add(risks, "NON_STANDARD_OFFBOARDING_TYPE");
        if (!"COMPLETED".equals(code(after.getSalarySettlementStatus())))
            add(risks, "SALARY_SETTLEMENT_PENDING");
        if (!"COMPLETED".equals(code(after.getAssetHandoverStatus())))
            add(risks, "ASSET_HANDOVER_PENDING");
        if (!"NOT_APPLICABLE".equals(code(after.getNonCompeteDecision())))
            add(risks, "NON_COMPETE_REVIEW_REQUIRED");
        if (after.getCompensationAmount().signum() > 0
                || trim(after.getCompensationNote()) != null)
            add(risks, "COMPENSATION_REVIEW_REQUIRED");
        return List.copyOf(risks);
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

    private OffboardPredicate parsePredicate(String ruleJson)
    {
        try
        {
            if (blank(ruleJson)) return null;
            JsonNode root = objectMapper.readTree(ruleJson);
            if (root == null || !root.isObject()) return null;
            for (Map.Entry<String, JsonNode> field : root.properties())
            {
                if (!RULE_FIELDS.contains(field.getKey())) return null;
                if ("historicalSupplement".equals(field.getKey()))
                {
                    if (field.getValue() == null || !field.getValue().isBoolean()) return null;
                }
                else if (!validCodeArray(field.getValue(), allowed(field.getKey()))) return null;
            }
            return new OffboardPredicate(
                    codes(root.get("offboardingTypes")),
                    codes(root.get("salarySettlementStatuses")),
                    codes(root.get("assetHandoverStatuses")),
                    codes(root.get("nonCompeteDecisions")),
                    codes(root.get("riskLevels")),
                    root.has("historicalSupplement")
                            ? root.get("historicalSupplement").booleanValue() : null);
        }
        catch (Exception ignored)
        {
            return null;
        }
    }

    private Set<String> allowed(String field)
    {
        return switch (field)
        {
            case "offboardingTypes" -> OFFBOARDING_TYPES;
            case "salarySettlementStatuses", "assetHandoverStatuses" -> COMPLETION_STATUSES;
            case "nonCompeteDecisions" -> NON_COMPETE_DECISIONS;
            case "riskLevels" -> RISK_LEVELS;
            default -> Set.of();
        };
    }

    private boolean validCodeArray(JsonNode value, Set<String> allowed)
    {
        if (value == null || !value.isArray()) return false;
        for (JsonNode item : value)
        {
            if (!item.isTextual() || !allowed.contains(code(item.textValue()))) return false;
        }
        return true;
    }

    private Set<String> codes(JsonNode array)
    {
        Set<String> values = new HashSet<>();
        if (array != null) array.forEach(item -> values.add(code(item.textValue())));
        return Set.copyOf(values);
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
                    || !OFFBOARD_TEMPLATE_TYPES.contains(code(template.getTemplateType()))
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
                    && finite(x, false) && finite(y, false)
                    && finite(width, true) && finite(height, true);
        }
        catch (Exception ignored)
        {
            return false;
        }
    }

    private boolean finite(JsonNode value, boolean positive)
    {
        return value != null && value.isNumber() && Double.isFinite(value.doubleValue())
                && (positive ? value.doubleValue() > 0 : value.doubleValue() >= 0);
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
        draft.setLeaveDate(effectiveDate.toString());
        draft.setLeaveReason(after.getLeaveReason());
        draft.setOffboardingType(code(after.getOffboardingType()));
        draft.setSalarySettlementStatus(code(after.getSalarySettlementStatus()));
        draft.setAssetHandoverStatus(code(after.getAssetHandoverStatus()));
        draft.setNonCompeteDecision(code(after.getNonCompeteDecision()));
        draft.setCompensationAmount(after.getCompensationAmount());
        draft.setCompensationNote(trim(after.getCompensationNote()));
        draft.setHistoricalSupplement(historical);
        draft.setBaseSalary(after.getBaseSalary());
        draft.setPostSalary(after.getPostSalary());
        draft.setFieldAllowance(after.getFieldAllowance());
        draft.setPerformanceSalary(after.getPerformanceSalary());
        draft.setSalaryTotal(after.getSalaryTotal());
        draft.setRemark(readableSummary(before, after, effectiveDate, historical));
        draft.setStatus(OaSignPackageStatus.DRAFT);
        draft.setConfirmStatus("WAITING_HR");
        draft.setVersion(0L);
        draft.setPlanVersionId(version.getVersionId());
        return draft;
    }

    private String readableSummary(HrEmployeeSigningSnapshot before,
            HrEmployeeSigningSnapshot after, LocalDate effectiveDate, boolean historical)
    {
        String value = (historical ? "历史离职补录;" : "")
                + "最后工作日:" + effectiveDate
                + ";离职类型:" + text(after.getOffboardingType())
                + ";离职原因:" + text(after.getLeaveReason())
                + ";工资结算:" + text(after.getSalarySettlementStatus())
                + ";资产交接:" + text(after.getAssetHandoverStatus())
                + ";竞业决定:" + text(after.getNonCompeteDecision())
                + ";补偿金额:" + after.getCompensationAmount().toPlainString()
                + ";补偿说明:" + text(after.getCompensationNote())
                + ";原状态:" + text(before.getEmployeeStatus()) + "→离职";
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
        if (risks != null) decision.setRiskLevel(risks.isEmpty() ? "LOW" : "HIGH");
        return decision;
    }

    private LocalDate effectiveDate(HrSignBusinessEvent event)
    {
        Object value = event == null ? null : attribute(event, "effectiveDate");
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
        Object value = event == null ? null : attribute(event, "historicalSupplement");
        return value instanceof Boolean bool ? bool : null;
    }

    private Object attribute(HrSignBusinessEvent event, String name)
    {
        Map<String, Object> attributes = event == null ? null : event.getAttributes();
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

    private boolean moneyEquals(BigDecimal left, BigDecimal right)
    {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
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

    private record OffboardPredicate(Set<String> offboardingTypes,
            Set<String> salarySettlementStatuses, Set<String> assetHandoverStatuses,
            Set<String> nonCompeteDecisions, Set<String> riskLevels,
            Boolean historicalSupplement)
    {
        private boolean matches(HrEmployeeSigningSnapshot after,
                String riskLevel, boolean historical)
        {
            return matches(offboardingTypes, after.getOffboardingType())
                    && matches(salarySettlementStatuses, after.getSalarySettlementStatus())
                    && matches(assetHandoverStatuses, after.getAssetHandoverStatus())
                    && matches(nonCompeteDecisions, after.getNonCompeteDecision())
                    && matches(riskLevels, riskLevel)
                    && (historicalSupplement == null
                            || historicalSupplement == historical);
        }

        private boolean matches(Set<String> expected, String actual)
        {
            return expected.isEmpty() || expected.contains(codeValue(actual));
        }

        private static String codeValue(String value)
        {
            if (value == null) return null;
            String normalized = value.trim();
            return normalized.isEmpty() ? null : normalized.toUpperCase(Locale.ROOT);
        }
    }

    private record TemplateDecision(boolean valid,
            boolean employeeConfirmationRequired)
    {
    }
}
