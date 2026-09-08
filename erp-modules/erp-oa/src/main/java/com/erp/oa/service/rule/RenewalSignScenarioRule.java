package com.erp.oa.service.rule;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.api.domain.HrEmployeeSigningSnapshot;
import com.erp.oa.api.domain.HrSignBusinessEvent;
import com.erp.oa.constant.OaSignPackageStatus;
import com.erp.oa.constant.OaSignPlanScope;
import com.erp.oa.constant.OaSignScenarioCodes;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPlanVersion;
import com.erp.oa.domain.vo.OaSignDraftDecision;
import com.erp.oa.mapper.OaSignPlanVersionMapper;

/** 续签场景只读规则：校验typed事件并选择不可变发布方案版本。 */
@Component
public class RenewalSignScenarioRule implements OaSignScenarioRule
{
    private static final String SCENARIO = "RENEWAL";
    private static final String RENEW = "RENEW";
    private static final String DECLINE = "DECLINE";
    private static final List<String> CONTRACT_TYPES = List.of(
            "LABOR_CONTRACT", "SERVICE_CONTRACT", "INTERNSHIP_AGREEMENT",
            "OUTSOURCING_CONTRACT");
    private static final List<String> CONTRACT_TERMS = List.of("FIXED_TERM", "OPEN_ENDED");

    private final OaSignPlanVersionMapper versionMapper;
    private final ObjectMapper objectMapper;

    public RenewalSignScenarioRule(OaSignPlanVersionMapper versionMapper,
            ObjectMapper objectMapper)
    {
        this.versionMapper = versionMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String scenario)
    {
        return OaSignScenarioCodes.isRenewal(scenario);
    }

    @Override
    public String dedupeKey(HrSignBusinessEvent event)
    {
        List<String> reasons = new ArrayList<>();
        EventDecision decision = validateEvent(event, reasons);
        if (!reasons.isEmpty() || decision == null)
        {
            throw new ServiceException("续签事件不一致: " + String.join(",", reasons));
        }
        if (!RENEW.equals(decision.decision) && !DECLINE.equals(decision.decision))
        {
            throw new ServiceException("续签事件的处理决定无效");
        }
        return SCENARIO + ":" + event.getEmployeeId() + ":"
                + decision.oldContractEndDate + ":" + decision.newRenewalCount;
    }

    @Override
    public OaSignDraftDecision decide(HrSignBusinessEvent event)
    {
        List<String> reasons = new ArrayList<>();
        EventDecision decision = validateEvent(event, reasons);
        if (!reasons.isEmpty() || decision == null)
        {
            return needsData(reasons.isEmpty() ? List.of("INVALID_RENEWAL_EVENT") : reasons);
        }
        if (DECLINE.equals(decision.decision))
        {
            if (!sameTypedSnapshot(event.getBeforeSnapshot(), event.getAfterSnapshot()))
            {
                return needsData("DECLINE_SNAPSHOT_CHANGED");
            }
            return noAction();
        }
        if (!RENEW.equals(decision.decision))
        {
            return needsData("INVALID_RENEWAL_DECISION");
        }

        HrEmployeeSigningSnapshot before = event.getBeforeSnapshot();
        HrEmployeeSigningSnapshot after = event.getAfterSnapshot();
        validateRenewalSnapshots(before, after, reasons);
        if (!reasons.isEmpty())
        {
            return needsData(reasons);
        }

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
                RuleSnapshot rule = parseRule(candidate.getRuleJson());
                if (rule == null)
                {
                    invalidRule = true;
                    continue;
                }
                if (code(after.getContractTypeCode()).equals(rule.contractTypeCode)
                        && code(after.getContractTermCode()).equals(rule.contractTermCode))
                {
                    matched.add(candidate);
                }
            }
        }
        if (matched.isEmpty())
        {
            return needsData(invalidRule ? "PLAN_RULE_INVALID" : "PLAN_NOT_FOUND");
        }
        if (matched.size() > 1)
        {
            return needsData("PLAN_CONFLICT");
        }

        OaSignPlanVersion version = matched.get(0);
        OaSignDraftDecision result = new OaSignDraftDecision();
        result.setAction(OaSignDraftDecision.Action.CREATE_DRAFT);
        result.setPlanVersionId(version.getVersionId());
        result.setRiskLevel("LOW");
        result.setReasonCodes(List.of());
        result.setDraftPackage(draft(before, after, version));
        return result;
    }

    private EventDecision validateEvent(HrSignBusinessEvent event, List<String> reasons)
    {
        if (event == null || !supports(event.getScenario()))
        {
            add(reasons, "INVALID_SCENARIO");
            return null;
        }
        if (event.getEmployeeId() == null || event.getEmployeeId() <= 0)
            add(reasons, "INVALID_EMPLOYEE_ID");
        if (!"HR_LIFECYCLE_ACTION".equals(code(event.getSourceType())))
            add(reasons, "INVALID_SOURCE_TYPE");
        if (blank(event.getSourceBusinessId())
                || !trim(event.getSourceBusinessId()).matches("[1-9][0-9]*"))
            add(reasons, "INVALID_SOURCE_ACTION_ID");
        if (event.getSourceEventVersion() == null || event.getSourceEventVersion() <= 0)
            add(reasons, "INVALID_ACTION_VERSION");

        HrEmployeeSigningSnapshot before = event.getBeforeSnapshot();
        HrEmployeeSigningSnapshot after = event.getAfterSnapshot();
        if (before == null) add(reasons, "MISSING_BEFORE_SNAPSHOT");
        if (after == null) add(reasons, "MISSING_AFTER_SNAPSHOT");
        if (before == null || after == null)
        {
            return null;
        }
        if (!Objects.equals(event.getEmployeeId(), before.getEmployeeId())
                || !Objects.equals(event.getEmployeeId(), after.getEmployeeId()))
            add(reasons, "EMPLOYEE_ID_MISMATCH");

        Map<String, Object> attributes = event.getAttributes();
        String decision = code(attribute(attributes, "decision"));
        if (!RENEW.equals(decision) && !DECLINE.equals(decision))
            add(reasons, "INVALID_RENEWAL_DECISION");
        String actionType = code(attribute(attributes, "actionType"));
        if (actionType == null)
        {
            add(reasons, "MISSING_ACTION_TYPE");
        }
        else if ((RENEW.equals(decision) && !"RENEWAL_CONFIRMED".equals(actionType))
                || (DECLINE.equals(decision) && !"RENEWAL_DECLINED".equals(actionType)))
        {
            add(reasons, "ACTION_TYPE_MISMATCH");
        }
        String oldEndText = trim(attribute(attributes, "oldContractEndDate"));
        LocalDate oldEnd = parseDate(oldEndText);
        if (before.getContractEndDate() == null || oldEnd == null
                || !Objects.equals(before.getContractEndDate(), oldEnd))
            add(reasons, "OLD_CONTRACT_END_MISMATCH");

        Integer oldCount = integerAttribute(attributes, "oldRenewalCount");
        Integer newCount = integerAttribute(attributes, "newRenewalCount");
        if (oldCount == null || !Objects.equals(before.getRenewalCount(), oldCount))
            add(reasons, "OLD_RENEWAL_COUNT_MISMATCH");
        if (newCount == null || !Objects.equals(after.getRenewalCount(), newCount))
            add(reasons, "NEW_RENEWAL_COUNT_MISMATCH");
        return new EventDecision(decision, oldEnd, oldCount, newCount);
    }

    private void validateRenewalSnapshots(HrEmployeeSigningSnapshot before,
            HrEmployeeSigningSnapshot after,
            List<String> reasons)
    {
        if (!sameImmutableRenewalSnapshot(before, after))
            add(reasons, "RENEW_SNAPSHOT_CHANGED");
        if (blank(after.getEmployeeName())) add(reasons, "MISSING_EMPLOYEE_NAME");
        if (blank(after.getPhone())) add(reasons, "MISSING_PHONE");
        else if (!trim(after.getPhone()).matches("1[0-9]{10}")) add(reasons, "INVALID_PHONE");
        if (blank(after.getIdType()) || blank(after.getIdNumber()))
            add(reasons, "MISSING_IDENTITY");
        if (after.getShopDeptId() == null || after.getDeptId() == null
                || blank(after.getShopDeptName()) || blank(after.getDeptName()))
            add(reasons, "MISSING_ORGANIZATION");
        if (after.getPostId() == null || blank(after.getPostCode())
                || blank(after.getPostName()))
            add(reasons, "MISSING_POST");
        if (blank(after.getJobGradeCode())) add(reasons, "MISSING_JOB_GRADE");
        // 原合同公司是续签历史快照，仍需保留；新合同公司待首次签名后确定。
        if (!completeLegalEntity(before))
            add(reasons, "MISSING_PREVIOUS_LEGAL_ENTITY");

        if (!completeContract(before)) add(reasons, "MISSING_OLD_CONTRACT");
        if (!completeContract(after)) add(reasons, "MISSING_NEW_CONTRACT");
        validateContractCodes(before, "OLD", reasons);
        validateContractCodes(after, "NEW", reasons);

        if (before.getContractStartDate() != null && before.getContractEndDate() != null
                && !before.getContractEndDate().isAfter(before.getContractStartDate()))
            add(reasons, "INVALID_OLD_CONTRACT_DATES");
        if (after.getContractStartDate() != null && after.getContractEndDate() != null)
        {
            if (!after.getContractEndDate().isAfter(after.getContractStartDate()))
                add(reasons, "INVALID_NEW_CONTRACT_DATES");
            if (before.getContractEndDate() != null
                    && !after.getContractStartDate().isAfter(before.getContractEndDate()))
                add(reasons, "CONTRACT_OVERLAP");
        }
        if (before.getRenewalCount() == null || before.getRenewalCount() < 0
                || after.getRenewalCount() == null
                || after.getRenewalCount() != before.getRenewalCount() + 1)
            add(reasons, "INVALID_RENEWAL_COUNT");
    }

    private void validateContractCodes(HrEmployeeSigningSnapshot snapshot,
            String prefix, List<String> reasons)
    {
        if (!blank(snapshot.getContractTypeCode())
                && !CONTRACT_TYPES.contains(code(snapshot.getContractTypeCode())))
            add(reasons, "INVALID_" + prefix + "_CONTRACT_TYPE");
        if (!blank(snapshot.getContractTermCode())
                && !CONTRACT_TERMS.contains(code(snapshot.getContractTermCode())))
            add(reasons, "INVALID_" + prefix + "_CONTRACT_TERM");
    }

    private boolean eligible(OaSignPlanVersion candidate,
            HrEmployeeSigningSnapshot snapshot)
    {
        return candidate != null && candidate.getVersionId() != null
                && "PUBLISHED".equals(code(candidate.getPublishStatus()))
                && "ENABLED".equals(code(candidate.getMatchingStatus()))
                && OaSignScenarioCodes.isRenewal(candidate.getScenario())
                && OaSignPlanScope.appliesTo(candidate.getShopDeptId(), snapshot.getShopDeptId());
    }

    private RuleSnapshot parseRule(String ruleJson)
    {
        try
        {
            if (blank(ruleJson)) return null;
            JsonNode root = objectMapper.readTree(ruleJson);
            if (root == null || !root.isObject()) return null;
            String[] fields = {
                    "renewalContractTypeCode", "contractTypeCode", "employmentType",
                    "renewalContractTermCode", "contractTermCode", "contractTerm"
            };
            for (String field : fields)
            {
                JsonNode node = root.get(field);
                if (node != null && !node.isNull() && !node.isTextual()) return null;
            }

            String renewalTypeRaw = text(root, "renewalContractTypeCode");
            String standardTypeRaw = text(root, "contractTypeCode");
            String legacyTypeRaw = text(root, "employmentType");
            String renewalTermRaw = text(root, "renewalContractTermCode");
            String standardTermRaw = text(root, "contractTermCode");
            String legacyTermRaw = text(root, "contractTerm");
            String renewalType = standardContractType(renewalTypeRaw);
            String standardType = standardContractType(standardTypeRaw);
            String legacyType = legacyContractType(legacyTypeRaw);
            String renewalTerm = standardContractTerm(renewalTermRaw);
            String standardTerm = standardContractTerm(standardTermRaw);
            String legacyTerm = legacyContractTerm(legacyTermRaw);
            if (invalid(renewalTypeRaw, renewalType) || invalid(standardTypeRaw, standardType)
                    || invalid(legacyTypeRaw, legacyType) || invalid(renewalTermRaw, renewalTerm)
                    || invalid(standardTermRaw, standardTerm) || invalid(legacyTermRaw, legacyTerm))
                return null;

            String type = consistent(renewalType, standardType, legacyType);
            String term = consistent(renewalTerm, standardTerm, legacyTerm);
            if (type == null || term == null) return null;
            return new RuleSnapshot(type, term);
        }
        catch (Exception ignored)
        {
            return null;
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
        draft.setLegalEntityIdSnapshot(null);
        draft.setLegalEntityCodeSnapshot(null);
        draft.setLegalEntityNameSnapshot(null);
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
        draft.setPostLevelSnapshot(trim(after.getJobGradeCode()));
        draft.setSalaryVersion(trim(after.getSalaryVersion()));
        draft.setEntryDate(dateText(after.getEntryDate()));
        draft.setContractStartDate(dateText(after.getContractStartDate()));
        draft.setContractEndDate(dateText(after.getContractEndDate()));
        draft.setPreviousContractEndDate(dateText(before.getContractEndDate()));
        draft.setPreviousEmploymentType(code(before.getContractTypeCode()));
        draft.setPreviousLegalEntityIdSnapshot(before.getLegalEntityId());
        draft.setPreviousRenewalCount(before.getRenewalCount());
        draft.setRenewalCount(after.getRenewalCount());
        draft.setBaseSalary(after.getBaseSalary());
        draft.setPostSalary(after.getPostSalary());
        draft.setFieldAllowance(after.getFieldAllowance());
        draft.setPerformanceSalary(after.getPerformanceSalary());
        draft.setSalaryTotal(after.getSalaryTotal());
        draft.setRemark(renewalDifference(before, after));
        draft.setStatus(OaSignPackageStatus.DRAFT);
        draft.setVersion(0L);
        draft.setPlanVersionId(version.getVersionId());
        return draft;
    }

    private String renewalDifference(HrEmployeeSigningSnapshot before,
            HrEmployeeSigningSnapshot after)
    {
        String value = "oldContractStartDate=" + before.getContractStartDate()
                + ";oldContractEndDate=" + before.getContractEndDate()
                + ";oldContractTypeCode=" + code(before.getContractTypeCode())
                + ";oldContractTermCode=" + code(before.getContractTermCode())
                + ";newContractStartDate=" + after.getContractStartDate()
                + ";newContractEndDate=" + after.getContractEndDate()
                + ";newContractTypeCode=" + code(after.getContractTypeCode())
                + ";newContractTermCode=" + code(after.getContractTermCode())
                + ";oldLegalEntityId=" + before.getLegalEntityId()
                + ";oldLegalEntityCode=" + code(before.getLegalEntityCode())
                + ";newLegalEntityId=" + after.getLegalEntityId()
                + ";newLegalEntityCode=" + code(after.getLegalEntityCode())
                + ";oldRenewalCount=" + before.getRenewalCount()
                + ";newRenewalCount=" + after.getRenewalCount();
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private boolean sameTypedSnapshot(HrEmployeeSigningSnapshot before,
            HrEmployeeSigningSnapshot after)
    {
        return sameImmutableRenewalSnapshot(before, after)
                && Objects.equals(before.getLegalEntityId(), after.getLegalEntityId())
                && Objects.equals(before.getLegalEntityCode(), after.getLegalEntityCode())
                && Objects.equals(before.getLegalEntityName(), after.getLegalEntityName())
                && Objects.equals(before.getContractStartDate(), after.getContractStartDate())
                && Objects.equals(before.getContractEndDate(), after.getContractEndDate())
                && Objects.equals(before.getContractTypeCode(), after.getContractTypeCode())
                && Objects.equals(before.getContractTermCode(), after.getContractTermCode())
                && Objects.equals(before.getRenewalCount(), after.getRenewalCount());
    }

    private boolean sameImmutableRenewalSnapshot(HrEmployeeSigningSnapshot before,
            HrEmployeeSigningSnapshot after)
    {
        return Objects.equals(before.getEmployeeId(), after.getEmployeeId())
                && Objects.equals(before.getEmployeeNo(), after.getEmployeeNo())
                && Objects.equals(before.getEmployeeName(), after.getEmployeeName())
                && Objects.equals(before.getPhone(), after.getPhone())
                && Objects.equals(before.getIdType(), after.getIdType())
                && Objects.equals(before.getIdNumber(), after.getIdNumber())
                && Objects.equals(before.getCurrentAddress(), after.getCurrentAddress())
                && Objects.equals(before.getEmployeeStatus(), after.getEmployeeStatus())
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
                && Objects.equals(before.getDepartmentSupervisorName(),
                        after.getDepartmentSupervisorName())
                && Objects.equals(before.getWorkLocation(), after.getWorkLocation())
                && Objects.equals(before.getWorkCityLevel(), after.getWorkCityLevel())
                && Objects.equals(before.getSocialTypeCode(), after.getSocialTypeCode())
                && Objects.equals(before.getEntryDate(), after.getEntryDate())
                && Objects.equals(before.getProbationStartDate(), after.getProbationStartDate())
                && Objects.equals(before.getProbationEndDate(), after.getProbationEndDate())
                && Objects.equals(before.getActualRegularizationDate(),
                        after.getActualRegularizationDate())
                && Objects.equals(before.getLeaveDate(), after.getLeaveDate())
                && sameAmount(before.getBaseSalary(), after.getBaseSalary())
                && sameAmount(before.getPostSalary(), after.getPostSalary())
                && sameAmount(before.getFieldAllowance(), after.getFieldAllowance())
                && sameAmount(before.getPerformanceSalary(), after.getPerformanceSalary())
                && sameAmount(before.getSalaryTotal(), after.getSalaryTotal())
                && Objects.equals(before.getSalaryVersion(), after.getSalaryVersion());
    }

    private boolean sameAmount(BigDecimal before, BigDecimal after)
    {
        return before == null ? after == null
                : after != null && before.compareTo(after) == 0;
    }

    private boolean completeContract(HrEmployeeSigningSnapshot snapshot)
    {
        return snapshot.getContractStartDate() != null && snapshot.getContractEndDate() != null
                && !blank(snapshot.getContractTypeCode()) && !blank(snapshot.getContractTermCode());
    }

    private boolean completeLegalEntity(HrEmployeeSigningSnapshot snapshot)
    {
        return snapshot.getLegalEntityId() != null && snapshot.getLegalEntityId() > 0
                && !blank(snapshot.getLegalEntityCode()) && !blank(snapshot.getLegalEntityName());
    }

    private OaSignDraftDecision noAction()
    {
        OaSignDraftDecision decision = new OaSignDraftDecision();
        decision.setAction(OaSignDraftDecision.Action.NO_ACTION);
        decision.setRiskLevel("LOW");
        decision.setReasonCodes(List.of("RENEWAL_DECLINED"));
        return decision;
    }

    private OaSignDraftDecision needsData(String reason)
    {
        return needsData(List.of(reason));
    }

    private OaSignDraftDecision needsData(List<String> reasons)
    {
        OaSignDraftDecision decision = new OaSignDraftDecision();
        decision.setAction(OaSignDraftDecision.Action.NEEDS_DATA);
        decision.setRiskLevel("HIGH");
        decision.setReasonCodes(reasons);
        return decision;
    }

    private String standardContractType(String value)
    {
        String normalized = code(value);
        return normalized != null && CONTRACT_TYPES.contains(normalized) ? normalized : null;
    }

    private String legacyContractType(String value)
    {
        String normalized = trim(value);
        if ("劳动合同".equals(normalized)) return "LABOR_CONTRACT";
        if ("劳务合同".equals(normalized)) return "SERVICE_CONTRACT";
        return standardContractType(normalized);
    }

    private String standardContractTerm(String value)
    {
        String normalized = code(value);
        return normalized != null && CONTRACT_TERMS.contains(normalized) ? normalized : null;
    }

    private String legacyContractTerm(String value)
    {
        String normalized = trim(value);
        if ("固定期限".equals(normalized)) return "FIXED_TERM";
        if ("无固定期限".equals(normalized)) return "OPEN_ENDED";
        return standardContractTerm(normalized);
    }

    private String consistent(String... values)
    {
        String result = null;
        for (String value : values)
        {
            if (value == null) continue;
            if (result != null && !result.equals(value)) return null;
            result = value;
        }
        return result;
    }

    private boolean invalid(String raw, String normalized)
    {
        return raw != null && normalized == null;
    }

    private String text(JsonNode root, String field)
    {
        JsonNode node = root.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }

    private String attribute(Map<String, Object> attributes, String key)
    {
        Object value = attributes == null ? null : attributes.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Integer integerAttribute(Map<String, Object> attributes, String key)
    {
        Object value = attributes == null ? null : attributes.get(key);
        if (value instanceof Number number)
        {
            long longValue = number.longValue();
            if (longValue < Integer.MIN_VALUE || longValue > Integer.MAX_VALUE
                    || BigDecimal.valueOf(longValue).compareTo(new BigDecimal(number.toString())) != 0)
                return null;
            return (int) longValue;
        }
        try
        {
            return value == null ? null : Integer.valueOf(String.valueOf(value).trim());
        }
        catch (NumberFormatException ignored)
        {
            return null;
        }
    }

    private LocalDate parseDate(String value)
    {
        try
        {
            return value == null ? null : LocalDate.parse(value);
        }
        catch (RuntimeException ignored)
        {
            return null;
        }
    }

    private String dateText(LocalDate value)
    {
        return value == null ? null : value.toString();
    }

    private void add(List<String> reasons, String reason)
    {
        if (!reasons.contains(reason)) reasons.add(reason);
    }

    private boolean blank(String value)
    {
        return trim(value) == null;
    }

    private String code(String value)
    {
        String normalized = trim(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String trim(String value)
    {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record EventDecision(String decision, LocalDate oldContractEndDate,
            Integer oldRenewalCount, Integer newRenewalCount) {}

    private record RuleSnapshot(String contractTypeCode, String contractTermCode) {}
}
