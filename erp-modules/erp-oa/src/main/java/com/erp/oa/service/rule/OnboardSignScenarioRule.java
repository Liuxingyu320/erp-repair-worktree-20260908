package com.erp.oa.service.rule;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.api.domain.HrEmployeeSigningSnapshot;
import com.erp.oa.api.domain.HrSignBusinessEvent;
import com.erp.oa.constant.OaOnboardSalaryVersionPolicy;
import com.erp.oa.constant.OaSignPackageStatus;
import com.erp.oa.constant.OaSignPlanScope;
import com.erp.oa.constant.OaSignSigningSequence;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPlanVersion;
import com.erp.oa.domain.vo.OaSignDraftDecision;
import com.erp.oa.mapper.OaSignPlanVersionMapper;

/** 入职场景签约方案规则。 */
@Component
public class OnboardSignScenarioRule implements OaSignScenarioRule
{
    private static final String SCENARIO = "ONBOARD";

    private final OaSignPlanVersionMapper versionMapper;
    private final ObjectMapper objectMapper;

    public OnboardSignScenarioRule(OaSignPlanVersionMapper versionMapper, ObjectMapper objectMapper)
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
        if (event == null || !supports(event.getScenario()))
        {
            throw new ServiceException("入职事件的签约场景必须为入职");
        }
        if (event.getEmployeeId() == null || event.getEmployeeId() <= 0)
        {
            throw new ServiceException("入职事件的员工编号无效");
        }
        String actionId = event.getSourceBusinessId() == null
                ? null : event.getSourceBusinessId().trim();
        if (actionId == null || !actionId.matches("[1-9][0-9]*"))
        {
            throw new ServiceException("入职事件的来源业务编号必须有效");
        }
        if (event.getSourceEventVersion() == null || event.getSourceEventVersion() <= 0)
        {
            throw new ServiceException("入职事件的业务版本无效");
        }
        return SCENARIO + ":" + event.getEmployeeId() + ":" + actionId + ":"
                + event.getSourceEventVersion();
    }

    @Override
    public OaSignDraftDecision decide(HrSignBusinessEvent event)
    {
        List<String> reasons = new ArrayList<>();
        if (event == null || !supports(event.getScenario()))
        {
            return needsData("INVALID_SCENARIO");
        }
        HrEmployeeSigningSnapshot snapshot = event.getAfterSnapshot();
        if (snapshot == null)
        {
            return needsData("MISSING_AFTER_SNAPSHOT");
        }
        validateSnapshot(event, snapshot, reasons);
        if (!reasons.isEmpty())
        {
            return needsData(reasons);
        }

        String band = gradeBand(snapshot.getJobGradeCode());
        if (band == null)
        {
            return needsData("UNKNOWN_JOB_GRADE");
        }
        String routeCode = routeCode(snapshot.getContractTypeCode(), snapshot.getSocialTypeCode(), band);
        if (routeCode == null)
        {
            return needsData("UNSUPPORTED_ONBOARD_COMBINATION");
        }

        List<OaSignPlanVersion> candidates = versionMapper.selectPublishedMatchingCandidates(
                SCENARIO, snapshot.getShopDeptId(), snapshot.getLegalEntityId());
        List<MatchedPlan> matched = new ArrayList<>();
        boolean invalidRule = false;
        if (candidates != null)
        {
            for (OaSignPlanVersion candidate : candidates)
            {
                if (!eligible(candidate, snapshot))
                {
                    continue;
                }
                RuleSnapshot ruleSnapshot = parseRule(candidate.getRuleJson());
                if (ruleSnapshot == null)
                {
                    invalidRule = true;
                    continue;
                }
                if (routeCode.equals(ruleSnapshot.routeCode)
                        && code(snapshot.getContractTypeCode()).equals(ruleSnapshot.contractTypeCode)
                        && code(snapshot.getSocialTypeCode()).equals(ruleSnapshot.socialTypeCode)
                        && band.equals(ruleSnapshot.jobGradeBand)
                        && serviceFactsMatch(event, snapshot, ruleSnapshot))
                {
                    matched.add(new MatchedPlan(candidate, ruleSnapshot));
                }
            }
        }
        matched = preferMostSpecificServicePlan(event, snapshot, matched);
        if (matched.isEmpty())
        {
            return needsData(invalidRule ? "PLAN_RULE_INVALID" : "PLAN_NOT_FOUND");
        }
        if (matched.size() > 1)
        {
            return needsData("PLAN_CONFLICT");
        }

        MatchedPlan matchedPlan = matched.get(0);
        OaSignPlanVersion version = matchedPlan.version();
        OaSignDraftDecision decision = new OaSignDraftDecision();
        decision.setAction(OaSignDraftDecision.Action.CREATE_DRAFT);
        decision.setPlanVersionId(version.getVersionId());
        decision.setRiskLevel("LOW");
        decision.setReasonCodes(List.of());
        decision.setDraftPackage(draft(snapshot, version, matchedPlan.rule(), event));
        return decision;
    }

    private void validateSnapshot(HrSignBusinessEvent event, HrEmployeeSigningSnapshot snapshot,
            List<String> reasons)
    {
        if (event.getEmployeeId() == null || snapshot.getEmployeeId() == null
                || !Objects.equals(event.getEmployeeId(), snapshot.getEmployeeId()))
            add(reasons, "EMPLOYEE_ID_MISMATCH");
        if (blank(snapshot.getEmployeeName())) add(reasons, "MISSING_EMPLOYEE_NAME");
        if (blank(snapshot.getPhone())) add(reasons, "MISSING_PHONE");
        else if (!trim(snapshot.getPhone()).matches("1[0-9]{10}")) add(reasons, "INVALID_PHONE");
        if (blank(snapshot.getIdType()) || blank(snapshot.getIdNumber())) add(reasons, "MISSING_IDENTITY");
        if (blank(snapshot.getCurrentAddress())) add(reasons, "MISSING_CURRENT_ADDRESS");
        if (snapshot.getShopDeptId() == null || snapshot.getDeptId() == null
                || blank(snapshot.getShopDeptName()) || blank(snapshot.getDeptName()))
            add(reasons, "MISSING_ORGANIZATION");
        if (snapshot.getPostId() == null || blank(snapshot.getPostCode()) || blank(snapshot.getPostName()))
            add(reasons, "MISSING_POST");
        if (blank(snapshot.getJobGradeCode())) add(reasons, "MISSING_JOB_GRADE");
        if (blank(snapshot.getContractTypeCode())) add(reasons, "MISSING_CONTRACT_TYPE");
        else if (!List.of("LABOR_CONTRACT", "SERVICE_CONTRACT", "INTERNSHIP_AGREEMENT",
                "OUTSOURCING_CONTRACT").contains(code(snapshot.getContractTypeCode())))
            add(reasons, "INVALID_CONTRACT_TYPE");
        if (blank(snapshot.getContractTermCode())) add(reasons, "MISSING_CONTRACT_TERM");
        else if (!List.of("FIXED_TERM", "OPEN_ENDED").contains(code(snapshot.getContractTermCode())))
            add(reasons, "INVALID_CONTRACT_TERM");
        if (blank(snapshot.getSocialTypeCode())) add(reasons, "MISSING_SOCIAL_TYPE");
        else if (!List.of("SOCIAL_INSURED", "SOCIAL_UNINSURED", "DISPATCHED",
                "PENDING_CONFIRMATION").contains(code(snapshot.getSocialTypeCode())))
            add(reasons, "INVALID_SOCIAL_TYPE");

        if (snapshot.getEntryDate() == null) add(reasons, "MISSING_ENTRY_DATE");
        if (snapshot.getContractStartDate() == null || snapshot.getContractEndDate() == null)
        {
            add(reasons, "MISSING_CONTRACT_DATES");
        }
        else if (!snapshot.getContractEndDate().isAfter(snapshot.getContractStartDate())
                || (snapshot.getEntryDate() != null
                        && snapshot.getContractStartDate().isBefore(snapshot.getEntryDate())))
        {
            add(reasons, "INVALID_CONTRACT_DATES");
        }
        validateProbation(snapshot, reasons);
        validateSalary(snapshot, reasons);
    }

    private void validateProbation(HrEmployeeSigningSnapshot snapshot, List<String> reasons)
    {
        LocalDate start = snapshot.getProbationStartDate();
        LocalDate end = snapshot.getProbationEndDate();
        if ((start == null) != (end == null))
        {
            add(reasons, "INVALID_PROBATION_DATES");
            return;
        }
        if (start != null && (end.isBefore(start)
                || (snapshot.getEntryDate() != null && start.isBefore(snapshot.getEntryDate()))
                || (snapshot.getContractEndDate() != null && end.isAfter(snapshot.getContractEndDate()))))
        {
            add(reasons, "INVALID_PROBATION_DATES");
        }
    }

    private void validateSalary(HrEmployeeSigningSnapshot snapshot, List<String> reasons)
    {
        if (snapshot.getBaseSalary() == null || snapshot.getPostSalary() == null
                || snapshot.getFieldAllowance() == null
                || snapshot.getPerformanceSalary() == null || snapshot.getSalaryTotal() == null)
        {
            add(reasons, "MISSING_SALARY");
            return;
        }
        if (negative(snapshot.getBaseSalary()) || negative(snapshot.getPostSalary())
                || negative(snapshot.getFieldAllowance()) || negative(snapshot.getPerformanceSalary())
                || snapshot.getSalaryTotal().signum() <= 0)
        {
            add(reasons, "INVALID_SALARY");
            return;
        }
        BigDecimal calculated = snapshot.getBaseSalary().add(snapshot.getPostSalary())
                .add(snapshot.getFieldAllowance()).add(snapshot.getPerformanceSalary());
        if (calculated.compareTo(snapshot.getSalaryTotal()) != 0)
        {
            add(reasons, "INVALID_SALARY_TOTAL");
        }
    }

    private boolean eligible(OaSignPlanVersion candidate, HrEmployeeSigningSnapshot snapshot)
    {
        return candidate != null
                && candidate.getVersionId() != null
                && "PUBLISHED".equals(code(candidate.getPublishStatus()))
                && "ENABLED".equals(code(candidate.getMatchingStatus()))
                && SCENARIO.equals(code(candidate.getScenario()))
                && OaSignPlanScope.appliesTo(candidate.getShopDeptId(), snapshot.getShopDeptId());
    }

    private RuleSnapshot parseRule(String ruleJson)
    {
        try
        {
            if (blank(ruleJson)) return null;
            JsonNode root = objectMapper.readTree(ruleJson);
            if (root == null || !root.isObject() || !textualOrMissing(root,
                    "routeCode", "contractTypeCode", "socialTypeCode", "jobGradeBand",
                    "employmentType", "socialType", "postLevel",
                    "servicePersonType", "insuranceType", "salaryVersion"))
            {
                return null;
            }

            String explicitRouteRaw = text(root, "routeCode");
            String explicitContractRaw = text(root, "contractTypeCode");
            String explicitSocialRaw = text(root, "socialTypeCode");
            String explicitBandRaw = text(root, "jobGradeBand");
            String legacyContractRaw = text(root, "employmentType");
            String legacySocialRaw = text(root, "socialType");
            String legacyBandRaw = text(root, "postLevel");
            String servicePersonType = trim(text(root, "servicePersonType"));
            String insuranceType = trim(text(root, "insuranceType"));
            String salaryVersionRaw = text(root, "salaryVersion");

            String explicitRoute = code(explicitRouteRaw);
            String explicitContract = code(explicitContractRaw);
            String explicitSocial = code(explicitSocialRaw);
            String explicitBand = band(explicitBandRaw);
            String legacyContract = legacyContractType(legacyContractRaw);
            String legacySocial = legacySocialType(legacySocialRaw);
            String legacyBand = gradeBand(legacyBandRaw);
            String salaryVersion = code(salaryVersionRaw);
            if (invalidValue(explicitRouteRaw, explicitRoute)
                    || invalidValue(explicitContractRaw, explicitContract)
                    || invalidValue(explicitSocialRaw, explicitSocial)
                    || invalidValue(explicitBandRaw, explicitBand)
                    || invalidValue(legacyContractRaw, legacyContract)
                    || invalidValue(legacySocialRaw, legacySocial)
                    || invalidValue(legacyBandRaw, legacyBand)
                    || invalidValue(salaryVersionRaw, salaryVersion)
                    || conflicts(explicitContract, legacyContract)
                    || conflicts(explicitSocial, legacySocial)
                    || conflicts(explicitBand, legacyBand))
            {
                return null;
            }

            String contractTypeCode = preferExplicit(explicitContract, legacyContract);
            String socialTypeCode = preferExplicit(explicitSocial, legacySocial);
            String jobGradeBand = preferExplicit(explicitBand, legacyBand);
            String requiredSalaryVersion = OaOnboardSalaryVersionPolicy.requiredSalaryVersion(
                    socialTypeCode);
            if ("LABOR_CONTRACT".equals(contractTypeCode)
                    && (requiredSalaryVersion == null
                    || !requiredSalaryVersion.equals(salaryVersion)))
            {
                return null;
            }
            String derivedRoute = routeCode(contractTypeCode, socialTypeCode, jobGradeBand);
            if (derivedRoute == null || (explicitRoute != null && !explicitRoute.equals(derivedRoute)))
            {
                return null;
            }
            String routeCode = preferExplicit(explicitRoute, derivedRoute);
            return new RuleSnapshot(routeCode, contractTypeCode, socialTypeCode, jobGradeBand,
                    servicePersonType, insuranceType, salaryVersion);
        }
        catch (Exception ignored)
        {
            return null;
        }
    }

    private OaSignPackage draft(HrEmployeeSigningSnapshot snapshot, OaSignPlanVersion version,
            RuleSnapshot planRule, HrSignBusinessEvent event)
    {
        OaSignPackage draft = new OaSignPackage();
        draft.setEmployeeId(snapshot.getEmployeeId());
        draft.setDeptIdSnapshot(snapshot.getDeptId());
        draft.setDeptNameSnapshot(snapshot.getDeptName());
        draft.setShopDeptId(snapshot.getShopDeptId());
        draft.setShopDeptName(snapshot.getShopDeptName());
        draft.setLegalEntityIdSnapshot(snapshot.getLegalEntityId());
        draft.setLegalEntityCodeSnapshot(snapshot.getLegalEntityCode());
        draft.setLegalEntityNameSnapshot(snapshot.getLegalEntityName());
        draft.setLegalEntitySourceDeptId(snapshot.getDeptId());
        draft.setLegalEntityResolveMode(attribute(event, "legalEntityResolveMode"));
        draft.setLegalEntityCreditCodeSnapshot(attribute(event, "legalEntityCreditCode"));
        draft.setLegalEntityAddressSnapshot(attribute(event, "legalEntityAddress"));
        draft.setLegalRepresentativeSnapshot(attribute(event, "legalRepresentative"));
        draft.setLegalEntityPhoneSnapshot(attribute(event, "legalEntityPhone"));
        draft.setSealIdSnapshot(attributeLong(event, "sealId"));
        draft.setSealNameSnapshot(attribute(event, "sealName"));
        draft.setSealImageUrlSnapshot(attribute(event, "sealImageUrl"));
        draft.setSealImageHashSnapshot(attribute(event, "sealImageHash"));
        draft.setSigningSequence(OaSignSigningSequence.normalize(
                attribute(event, "signingSequence")));
        draft.setSourcePlanId(version.getPlanId());
        draft.setSourcePlanName(version.getPlanName());
        draft.setEmployeeNameSnapshot(snapshot.getEmployeeName());
        draft.setEmployeePhoneSnapshot(snapshot.getPhone());
        draft.setEmployeeIdCardSnapshot(snapshot.getIdNumber());
        draft.setEmployeeAddressSnapshot(snapshot.getCurrentAddress());
        draft.setScenario(SCENARIO);
        draft.setEmploymentType(code(snapshot.getContractTypeCode()));
        draft.setContractTermCodeSnapshot(code(snapshot.getContractTermCode()));
        draft.setSocialType(code(snapshot.getSocialTypeCode()));
        draft.setServicePersonType(preferPlanFact(planRule.servicePersonType(),
                attribute(event, "servicePersonType")));
        draft.setInsuranceType(preferPlanFact(planRule.insuranceType(),
                attribute(event, "insuranceType")));
        draft.setPostNameSnapshot(snapshot.getPostName());
        draft.setPostLevelSnapshot(trim(snapshot.getJobGradeCode()));
        draft.setSalaryVersion(OaOnboardSalaryVersionPolicy.requiredSalaryVersion(
                snapshot.getSocialTypeCode()));
        draft.setEntryDate(text(snapshot.getEntryDate()));
        draft.setContractStartDate(text(snapshot.getContractStartDate()));
        draft.setContractEndDate(text(snapshot.getContractEndDate()));
        draft.setProbationStartDate(text(snapshot.getProbationStartDate()));
        draft.setProbationEndDate(text(snapshot.getProbationEndDate()));
        draft.setBaseSalary(snapshot.getBaseSalary());
        draft.setPostSalary(snapshot.getPostSalary());
        draft.setFieldAllowance(snapshot.getFieldAllowance());
        draft.setPerformanceSalary(snapshot.getPerformanceSalary());
        draft.setSalaryTotal(snapshot.getSalaryTotal());
        draft.setStudentStatusSnapshot(attribute(event, "studentStatus"));
        draft.setRetirementStatusSnapshot(attribute(event, "retirementStatus"));
        draft.setIncomeStartYearMonth(attribute(event, "incomeStartYearMonth"));
        draft.setRecommendedCompanySnapshot(attribute(event, "recommendedCompany"));
        draft.setRecommendedLegalRepresentativeSnapshot(attribute(event,
                "recommendedLegalRepresentative"));
        draft.setRecommendedRegisteredAddressSnapshot(attribute(event,
                "recommendedRegisteredAddress"));
        draft.setHistoricalSupplement(Boolean.TRUE.equals(attributeBoolean(event,
                "historicalSupplement")));
        draft.setStatus(OaSignPackageStatus.DRAFT);
        draft.setVersion(0L);
        draft.setPlanVersionId(version.getVersionId());
        return draft;
    }

    private String attribute(HrSignBusinessEvent event, String name)
    {
        if (event == null || event.getAttributes() == null) return null;
        Object value = event.getAttributes().get(name);
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private Long attributeLong(HrSignBusinessEvent event, String name)
    {
        if (event == null || event.getAttributes() == null) return null;
        Object value = event.getAttributes().get(name);
        if (value instanceof Number number) return number.longValue();
        try { return value == null ? null : Long.valueOf(String.valueOf(value).trim()); }
        catch (NumberFormatException ignored) { return null; }
    }

    private Boolean attributeBoolean(HrSignBusinessEvent event, String name)
    {
        if (event == null || event.getAttributes() == null) return null;
        Object value = event.getAttributes().get(name);
        if (value instanceof Boolean bool) return bool;
        return value == null ? null : Boolean.valueOf(String.valueOf(value));
    }

    private boolean serviceFactsMatch(HrSignBusinessEvent event,
            HrEmployeeSigningSnapshot snapshot, RuleSnapshot rule)
    {
        if (!"SERVICE_CONTRACT".equals(code(snapshot.getContractTypeCode()))) return true;
        String requestedPersonType = attribute(event, "servicePersonType");
        String requestedInsuranceType = attribute(event, "insuranceType");
        return (blank(requestedPersonType) || blank(rule.servicePersonType())
                    || equalsCode(requestedPersonType, rule.servicePersonType()))
                && (blank(requestedInsuranceType) || blank(rule.insuranceType())
                    || equalsCode(requestedInsuranceType, rule.insuranceType()));
    }

    private List<MatchedPlan> preferMostSpecificServicePlan(HrSignBusinessEvent event,
            HrEmployeeSigningSnapshot snapshot, List<MatchedPlan> matched)
    {
        if (!"SERVICE_CONTRACT".equals(code(snapshot.getContractTypeCode()))
                || blank(attribute(event, "servicePersonType"))
                || blank(attribute(event, "insuranceType")) || matched.size() < 2)
            return matched;
        int max = matched.stream().mapToInt(value -> serviceSpecificity(value.rule())).max().orElse(0);
        return matched.stream().filter(value -> serviceSpecificity(value.rule()) == max).toList();
    }

    private int serviceSpecificity(RuleSnapshot rule)
    {
        return (blank(rule.servicePersonType()) ? 0 : 1)
                + (blank(rule.insuranceType()) ? 0 : 1);
    }

    private String preferPlanFact(String planValue, String requestedValue)
    {
        return blank(planValue) ? trim(requestedValue) : trim(planValue);
    }

    private boolean equalsCode(String left, String right)
    {
        return Objects.equals(code(left), code(right));
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

    private String routeCode(String contractType, String socialType, String band)
    {
        int index = switch (band)
        {
            case "2-4" -> 1;
            case "5-6" -> 2;
            case "7-9" -> 3;
            default -> 0;
        };
        if (index == 0) return null;
        String contract = code(contractType);
        String social = code(socialType);
        if ("LABOR_CONTRACT".equals(contract) && "SOCIAL_INSURED".equals(social))
            return "A" + index;
        if ("LABOR_CONTRACT".equals(contract) && "SOCIAL_UNINSURED".equals(social))
            return "A" + (index + 3);
        if ("SERVICE_CONTRACT".equals(contract) && "SOCIAL_UNINSURED".equals(social))
            return "B" + index;
        return null;
    }

    private String gradeBand(String value)
    {
        String normalized = code(value);
        if (normalized == null) return null;
        String explicit = band(normalized);
        if (explicit != null) return explicit;
        String number = normalized.startsWith("P") ? normalized.substring(1) : normalized;
        if (!number.matches("[2-9]")) return null;
        int grade = Integer.parseInt(number);
        if (grade <= 4) return "2-4";
        if (grade <= 6) return "5-6";
        return "7-9";
    }

    private String band(String value)
    {
        String normalized = code(value);
        if ("2-4".equals(normalized) || "5-6".equals(normalized)) return normalized;
        // Published versions created before grade 9 was enabled use 7-8. Normalize both
        // spellings to the current 7-9 business band so no in-place plan rewrite is required.
        if ("7-8".equals(normalized) || "7-9".equals(normalized)) return "7-9";
        return null;
    }

    private String legacyContractType(String value)
    {
        String normalized = trim(value);
        if ("劳动合同".equals(normalized)) return "LABOR_CONTRACT";
        if ("劳务合同".equals(normalized)) return "SERVICE_CONTRACT";
        return null;
    }

    private String legacySocialType(String value)
    {
        String normalized = trim(value);
        if ("有社保".equals(normalized) || "SOCIAL_INSURED".equals(normalized))
            return "SOCIAL_INSURED";
        if ("无社保".equals(normalized) || "SOCIAL_UNINSURED".equals(normalized))
            return "SOCIAL_UNINSURED";
        return null;
    }

    private boolean textualOrMissing(JsonNode root, String... fields)
    {
        for (String field : fields)
        {
            JsonNode node = root.get(field);
            if (node != null && !node.isNull() && !node.isTextual()) return false;
        }
        return true;
    }

    private boolean invalidValue(String raw, String normalized)
    {
        return raw != null && normalized == null;
    }

    private boolean conflicts(String explicit, String legacy)
    {
        return explicit != null && legacy != null && !explicit.equals(legacy);
    }

    private String preferExplicit(String explicit, String legacy)
    {
        return explicit != null ? explicit : legacy;
    }

    private String text(JsonNode root, String field)
    {
        JsonNode node = root == null ? null : root.get(field);
        return node == null || node.isNull() || !node.isTextual() ? null : node.asText();
    }

    private String text(LocalDate value)
    {
        return value == null ? null : value.toString();
    }

    private void add(List<String> reasons, String reason)
    {
        if (!reasons.contains(reason)) reasons.add(reason);
    }

    private boolean negative(BigDecimal value)
    {
        return value.signum() < 0;
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

    private record RuleSnapshot(String routeCode, String contractTypeCode,
            String socialTypeCode, String jobGradeBand, String servicePersonType,
            String insuranceType, String salaryVersion) {}

    private record MatchedPlan(OaSignPlanVersion version, RuleSnapshot rule) {}
}
