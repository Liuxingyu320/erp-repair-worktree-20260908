package com.erp.oa.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.domain.R;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.oa.constant.OaSignPackageStatus;
import com.erp.oa.constant.OaSignScenarioCodes;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPlan;
import com.erp.oa.domain.OaSignTemplate;
import com.erp.oa.domain.dto.OaSignBatchCreateDraftsRequest;
import com.erp.oa.domain.dto.OaSignBatchCreateDraftsResult;
import com.erp.oa.domain.dto.OaSignBatchPreviewRequest;
import com.erp.oa.domain.dto.OaSignBatchPreviewRow;
import com.erp.oa.domain.dto.OaSignBatchTemplateOption;
import com.erp.oa.mapper.OaSignPackageMapper;
import com.erp.oa.mapper.OaSignPlanMapper;
import com.erp.oa.service.IOaSignBatchService;
import com.erp.oa.service.IOaSignPackageService;
import com.erp.oa.service.IOaSignPlanService;
import com.erp.system.api.RemoteUserService;
import com.erp.system.api.constant.SigningProfileCodes;
import com.erp.system.api.domain.SignCandidateUser;
import com.erp.system.api.domain.SignCandidateUserQuery;

@Service
public class OaSignBatchServiceImpl implements IOaSignBatchService
{
    private static final String PLAN_ENABLED_STATUS = "0";
    private static final int CANDIDATE_LIMIT = 500;
    private static final String SKIP_INCOMPLETE = "资料不完整";
    private static final String SKIP_INVALID = "资料格式不正确";
    private static final String SKIP_OUT_OF_SCOPE = "员工不在方案岗位范围";
    private static final String SKIP_OPEN_PACKAGE = "同员工同方案已有未完成签约包";
    private static final String SKIP_DUPLICATE_EMPLOYEE = "同一批次重复员工";
    private static final String SKIP_AUTO_MATCH = "不可自动匹配";
    private static final String ROUTE_PLAN_MISMATCH = "员工签约路由与所选方案不一致";
    private static final String SCENARIO_ONBOARD = "ONBOARD";
    private static final String CONTRACT_LABOR = "LABOR_CONTRACT";
    private static final String CONTRACT_SERVICE = "SERVICE_CONTRACT";
    private static final String SOCIAL_YES = "SOCIAL_INSURED";
    private static final String SOCIAL_NO = "SOCIAL_UNINSURED";
    private static final Pattern JOB_GRADE_NUMBER = Pattern.compile("(\\d+)");

    @Autowired
    private IOaSignPlanService planService;

    @Autowired
    private OaSignPlanMapper planMapper;

    @Autowired
    private OaSignPackageMapper packageMapper;

    @Autowired
    private IOaSignPackageService packageService;

    @Autowired
    private RemoteUserService remoteUserService;

    @Autowired
    @Qualifier("oaSignScopeService")
    private ShopScopeService shopScopeService;

    @Autowired(required = false)
    private Validator validator;

    @Override
    public List<OaSignBatchPreviewRow> preview(OaSignBatchPreviewRequest request, Long selectedShopDeptId)
    {
        PlanContext context = loadEnabledPlan(request == null ? null : request.getPlanId(), selectedShopDeptId);
        R<List<SignCandidateUser>> response = remoteUserService.listSignCandidates(buildCandidateQuery(request, context),
                SecurityConstants.INNER);
        if (response == null || R.isError(response))
        {
            throw new ServiceException("获取签约候选员工失败" + (response == null ? "" : blankToEmpty(response.getMsg())));
        }
        List<OaSignBatchPreviewRow> rows = new ArrayList<>();
        List<SignCandidateUser> candidates = response.getData() == null ? Collections.emptyList() : response.getData();
        for (SignCandidateUser candidate : candidates)
        {
            OaSignBatchPreviewRow row = buildPreviewRow(candidate, request, context);
            applyCreatableState(row, context);
            rows.add(row);
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.REPEATABLE_READ)
    public OaSignBatchCreateDraftsResult createDrafts(OaSignBatchCreateDraftsRequest request, Long selectedShopDeptId)
    {
        PlanContext context = loadEnabledPlan(request == null ? null : request.getPlanId(), selectedShopDeptId);
        List<Long> packageIds = new ArrayList<>();
        List<OaSignBatchPreviewRow> skippedRows = new ArrayList<>();

        List<OaSignBatchPreviewRow> rows = request == null || request.getRows() == null
                ? Collections.emptyList() : request.getRows();
        Map<Long, SignCandidateUser> candidateMap = reloadCreateCandidates(context, rows);
        Set<Long> processedEmployeeIds = new LinkedHashSet<>();
        for (OaSignBatchPreviewRow row : rows)
        {
            if (row == null || !Boolean.TRUE.equals(row.getSelected()))
            {
                continue;
            }
            if (row.getEmployeeId() != null && !processedEmployeeIds.add(row.getEmployeeId()))
            {
                markSkipped(row, Collections.emptyList(), SKIP_DUPLICATE_EMPLOYEE);
                skippedRows.add(row);
                continue;
            }
            SignCandidateUser candidate = row.getEmployeeId() == null ? null : candidateMap.get(row.getEmployeeId());
            if (row.getEmployeeId() == null)
            {
                applyServerDefaults(row, context.plan, context.activeTemplates);
            }
            else if (candidate == null)
            {
                markSkipped(row, Collections.emptyList(), SKIP_OUT_OF_SCOPE);
                skippedRows.add(row);
                continue;
            }
            else
            {
                applyServerCandidate(row, candidate, context.plan);
                applyServerDefaults(row, context.plan, context.activeTemplates);
            }
            List<String> missingFields = resolveMissingFields(
                    row, context.activeTemplates, context.plan);
            if (!missingFields.isEmpty())
            {
                markSkipped(row, missingFields, skipReasonFor(missingFields));
                skippedRows.add(row);
                continue;
            }
            if (packageMapper.selectOpenPackageByEmployeeAndPlanForUpdate(
                    row.getEmployeeId(), context.plan.getPlanId(), context.shopDeptId) != null)
            {
                markSkipped(row, missingFields, SKIP_OPEN_PACKAGE);
                skippedRows.add(row);
                continue;
            }

            OaSignPackage draftPackage = buildDraftPackage(row, context.plan, context.shopDeptId);
            if (request != null && StringUtils.isNotBlank(request.getEmergencyReason()))
            {
                draftPackage.setRemark("非生命周期任务来源：" + request.getEmergencyReason().trim());
            }
            List<String> validationMessages = validateDraftPackage(draftPackage);
            if (!validationMessages.isEmpty())
            {
                markSkipped(row, validationMessages, SKIP_INVALID);
                skippedRows.add(row);
                continue;
            }
            OaSignPackage created = packageService.createPackage(draftPackage, context.shopDeptId);
            if (created != null && created.getPackageId() != null)
            {
                packageIds.add(created.getPackageId());
            }
        }

        OaSignBatchCreateDraftsResult result = new OaSignBatchCreateDraftsResult();
        result.setCreatedCount(packageIds.size());
        result.setSkippedCount(skippedRows.size());
        result.setPackageIds(packageIds);
        result.setSkippedRows(skippedRows);
        return result;
    }

    private PlanContext loadEnabledPlan(Long planId, Long selectedShopDeptId)
    {
        if (planId == null)
        {
            throw new ServiceException("请选择签约方案");
        }
        Long shopDeptId = shopScopeService.resolveRequiredShopDept(selectedShopDeptId);
        OaSignPlan plan = planService.getPlanDetail(planId, selectedShopDeptId);
        if (!PLAN_ENABLED_STATUS.equals(plan.getStatus()))
        {
            throw new ServiceException("签约方案未启用");
        }
        if (!OaSignScenarioCodes.ONBOARD.equals(
                OaSignScenarioCodes.normalizeTaskScenario(plan.getScenario())))
        {
            throw new ServiceException("批量按岗位创建目前仅支持入职场景");
        }
        List<OaSignTemplate> activeTemplates = planMapper.selectActiveTemplatesByPlanId(planId);
        PlanContext context = new PlanContext();
        context.plan = plan;
        context.shopDeptId = shopDeptId;
        context.activeTemplates = activeTemplates == null ? Collections.emptyList() : activeTemplates;
        return context;
    }

    private SignCandidateUserQuery buildCandidateQuery(OaSignBatchPreviewRequest request, PlanContext context)
    {
        SignCandidateUserQuery query = new SignCandidateUserQuery();
        query.setPostName(firstText(context.plan.getPostName(), request == null ? null : request.getPostName()));
        query.setDeptId(context.shopDeptId);
        query.setKeyword(request == null ? null : request.getKeyword());
        query.setUserIds(request == null ? null : request.getEmployeeIds());
        query.setLimit(CANDIDATE_LIMIT);
        return query;
    }

    private Map<Long, SignCandidateUser> reloadCreateCandidates(PlanContext context,
            List<OaSignBatchPreviewRow> rows)
    {
        Long[] employeeIds = rows.stream()
                .filter(row -> row != null && Boolean.TRUE.equals(row.getSelected()) && row.getEmployeeId() != null)
                .map(OaSignBatchPreviewRow::getEmployeeId)
                .distinct()
                .toArray(Long[]::new);
        if (employeeIds.length == 0)
        {
            return Collections.emptyMap();
        }
        SignCandidateUserQuery query = new SignCandidateUserQuery();
        query.setDeptId(context.shopDeptId);
        query.setPostName(context.plan.getPostName());
        query.setUserIds(employeeIds);
        query.setLimit(CANDIDATE_LIMIT);
        R<List<SignCandidateUser>> response = remoteUserService.listSignCandidates(query, SecurityConstants.INNER);
        if (response == null || R.isError(response))
        {
            throw new ServiceException("获取签约候选员工失败" + (response == null ? "" : blankToEmpty(response.getMsg())));
        }
        List<SignCandidateUser> candidates = response.getData() == null ? Collections.emptyList() : response.getData();
        Map<Long, SignCandidateUser> candidateMap = new LinkedHashMap<>();
        for (SignCandidateUser candidate : candidates)
        {
            if (candidate != null && candidate.getUserId() != null)
            {
                candidateMap.put(candidate.getUserId(), candidate);
            }
        }
        return candidateMap;
    }

    private OaSignBatchPreviewRow buildPreviewRow(SignCandidateUser candidate, OaSignBatchPreviewRequest request,
            PlanContext context)
    {
        OaSignBatchPreviewRow row = new OaSignBatchPreviewRow();
        row.setSelected(true);
        if (candidate != null)
        {
            row.setEmployeeId(candidate.getUserId());
            row.setEmployeeNameSnapshot(firstText(candidate.getNickName(), candidate.getUserName()));
            row.setEmployeePhoneSnapshot(candidate.getPhonenumber());
            row.setDeptIdSnapshot(candidate.getDeptId());
            row.setDeptNameSnapshot(candidate.getDeptName());
            row.setLegalEntityIdSnapshot(candidate.getLegalEntityId());
            row.setLegalEntityNameSnapshot(candidate.getLegalEntity());
            row.setPostNameSnapshot(firstText(context.plan.getPostName(), candidate.getPostNames(),
                    request == null ? null : request.getPostName()));
            applyCandidateProfile(row, candidate);
        }
        applyServerDefaults(row, context.plan, context.activeTemplates);
        return row;
    }

    private void applyServerCandidate(OaSignBatchPreviewRow row, SignCandidateUser candidate, OaSignPlan plan)
    {
        row.setEmployeeId(candidate.getUserId());
        row.setEmployeeNameSnapshot(firstText(candidate.getNickName(), candidate.getUserName()));
        row.setEmployeePhoneSnapshot(candidate.getPhonenumber());
        row.setDeptIdSnapshot(candidate.getDeptId());
        row.setDeptNameSnapshot(candidate.getDeptName());
        row.setLegalEntityIdSnapshot(candidate.getLegalEntityId());
        row.setLegalEntityNameSnapshot(candidate.getLegalEntity());
        row.setPostNameSnapshot(firstText(candidate.getPostNames(), plan.getPostName()));
        applyCandidateProfile(row, candidate);
    }

    private void applyCreatableState(OaSignBatchPreviewRow row, PlanContext context)
    {
        List<String> missingFields = resolveMissingFields(
                row, context.activeTemplates, context.plan);
        row.setMissingFields(missingFields);
        if (row.getEmployeeId() != null
                && packageMapper.selectOpenPackageByEmployeeAndPlan(
                        row.getEmployeeId(), context.plan.getPlanId(), context.shopDeptId) != null)
        {
            row.setCreatable(false);
            row.setSkipReason(SKIP_OPEN_PACKAGE);
            return;
        }
        if (!missingFields.isEmpty())
        {
            row.setCreatable(false);
            row.setSkipReason(skipReasonFor(missingFields));
            return;
        }
        row.setCreatable(true);
        row.setSkipReason(null);
    }

    private void applyServerDefaults(OaSignBatchPreviewRow row, OaSignPlan plan, List<OaSignTemplate> activeTemplates)
    {
        row.setScenario(firstText(row.getScenario(), plan.getScenario()));
        row.setEmploymentType(firstText(row.getEmploymentType(), plan.getEmploymentType()));
        row.setSocialType(firstText(row.getSocialType(), plan.getSocialType()));
        row.setServicePersonType(firstText(row.getServicePersonType(), plan.getServicePersonType()));
        row.setInsuranceType(firstText(row.getInsuranceType(), plan.getInsuranceType()));
        row.setPostNameSnapshot(firstText(row.getPostNameSnapshot(), plan.getPostName()));
        row.setPostLevelSnapshot(firstText(row.getPostLevelSnapshot(), plan.getPostLevelSnapshot()));
        row.setSalaryVersion(firstText(row.getSalaryVersion(), plan.getSalaryVersion()));
        row.setEntryDate(firstText(row.getEntryDate(), plan.getEntryDate()));
        row.setContractStartDate(firstText(row.getContractStartDate(), plan.getContractStartDate()));
        row.setContractEndDate(firstText(row.getContractEndDate(), plan.getContractEndDate()));
        row.setProbationStartDate(firstText(row.getProbationStartDate(), plan.getProbationStartDate()));
        row.setProbationEndDate(firstText(row.getProbationEndDate(), plan.getProbationEndDate()));
        row.setBaseSalary(firstAmount(row.getBaseSalary(), plan.getBaseSalary()));
        row.setPostSalary(firstAmount(row.getPostSalary(), plan.getPostSalary()));
        row.setFieldAllowance(firstAmount(row.getFieldAllowance(), plan.getFieldAllowance()));
        row.setSalaryTotal(firstAmount(row.getSalaryTotal(), plan.getSalaryTotal()));
        row.setTemplates(toTemplateOptions(activeTemplates));
    }

    private void applyCandidateProfile(OaSignBatchPreviewRow row, SignCandidateUser candidate)
    {
        // All employee-specific values are reloaded from the protected system API.
        // A missing server value must clear an untrusted browser value; plan defaults are
        // applied afterwards and are the only permitted fallback.
        row.setEmployeeIdCardSnapshot(trimToNull(candidate.getIdNumber()));
        row.setEmployeeAddressSnapshot(trimToNull(candidate.getCurrentAddress()));
        row.setPostLevelSnapshot(trimToNull(candidate.getJobGrade()));
        row.setEmploymentType(trimToNull(candidate.getContractType()));
        row.setContractTermCodeSnapshot(trimToNull(candidate.getContractTerm()));
        row.setSocialType(trimToNull(candidate.getSocialType()));
        row.setEntryDate(trimToNull(candidate.getEntryDate()));
        row.setContractStartDate(trimToNull(candidate.getContractStartDate()));
        row.setContractEndDate(trimToNull(candidate.getContractEndDate()));
        row.setProbationStartDate(trimToNull(candidate.getProbationStartDate()));
        row.setProbationEndDate(trimToNull(candidate.getProbationEndDate()));
        row.setBaseSalary(candidate.getBaseSalary());
        row.setPostSalary(candidate.getPostSalary());
        row.setFieldAllowance(candidate.getFieldAllowance());
        row.setPerformanceSalary(candidate.getPerformanceSalary());
        row.setSalaryTotal(candidate.getSalaryTotal());
        row.setSalaryVersion(trimToNull(candidate.getSalaryVersion()));
    }

    private List<OaSignBatchTemplateOption> toTemplateOptions(List<OaSignTemplate> templates)
    {
        if (templates == null || templates.isEmpty())
        {
            return Collections.emptyList();
        }
        List<OaSignBatchTemplateOption> options = new ArrayList<>();
        for (OaSignTemplate template : templates)
        {
            OaSignBatchTemplateOption option = new OaSignBatchTemplateOption();
            option.setTemplateId(template.getTemplateId());
            option.setTemplateType(template.getTemplateType());
            option.setTemplateName(template.getTemplateName());
            option.setSortOrder(template.getSortOrder());
            options.add(option);
        }
        return options;
    }

    private List<String> resolveMissingFields(OaSignBatchPreviewRow row,
            List<OaSignTemplate> activeTemplates, OaSignPlan plan)
    {
        List<String> missingFields = new ArrayList<>();
        String packageMatchIssue = resolveOnboardingPackageMatch(row);
        if (row.getEmployeeId() == null)
        {
            missingFields.add("员工账号");
        }
        if (StringUtils.isBlank(row.getEmployeeNameSnapshot()))
        {
            missingFields.add("员工姓名");
        }
        if (StringUtils.isBlank(row.getEmployeePhoneSnapshot()))
        {
            missingFields.add("手机号");
        }
        if (StringUtils.isBlank(row.getEmployeeIdCardSnapshot()))
        {
            missingFields.add("身份证号");
        }
        if (StringUtils.isBlank(row.getScenario()))
        {
            missingFields.add("签约场景");
        }
        if (isOnboardScenario(row.getScenario()))
        {
            if (row.getDeptIdSnapshot() == null && StringUtils.isBlank(row.getDeptNameSnapshot()))
            {
                missingFields.add("归属部门/门店");
            }
            if (StringUtils.isBlank(row.getPostNameSnapshot()))
            {
                missingFields.add("岗位");
            }
            if (StringUtils.isBlank(row.getPostLevelSnapshot()))
            {
                missingFields.add("职级");
            }
            if (StringUtils.isBlank(row.getEntryDate()))
            {
                missingFields.add("入职时间");
            }
            if (StringUtils.isBlank(row.getContractStartDate()))
            {
                missingFields.add("合同开始日期");
            }
            if (StringUtils.isBlank(row.getContractEndDate()))
            {
                missingFields.add("合同到期日期");
            }
            if (StringUtils.isBlank(row.getEmploymentType()))
            {
                missingFields.add("合同类型");
            }
            if (StringUtils.isBlank(row.getSocialType()))
            {
                missingFields.add("社保类型");
            }
            if ((isPlaceholderRequired(activeTemplates, "contractTermSelection")
                    || isPlaceholderRequired(activeTemplates, "contractTermFixedMark")
                    || isPlaceholderRequired(activeTemplates, "contractTermOpenEndedMark"))
                    && !SigningProfileCodes.isKnownContractTerm(
                            trimToNull(row.getContractTermCodeSnapshot())))
            {
                missingFields.add("合同期限类型");
            }
            addTemplateRequiredField(missingFields, activeTemplates, "employeeAddress",
                    row.getEmployeeAddressSnapshot(), "联系住址");
            addTemplateRequiredField(missingFields, activeTemplates, "probationStartDate",
                    row.getProbationStartDate(), "试用期开始日期");
            addTemplateRequiredField(missingFields, activeTemplates, "probationEndDate",
                    row.getProbationEndDate(), "试用期结束日期");
            addTemplateRequiredField(missingFields, activeTemplates, "baseSalary",
                    row.getBaseSalary(), "基本工资");
            addTemplateRequiredField(missingFields, activeTemplates, "postSalary",
                    row.getPostSalary(), "岗位工资");
            addTemplateRequiredField(missingFields, activeTemplates, "fieldAllowance",
                    row.getFieldAllowance(), "外勤补贴");
            addTemplateRequiredField(missingFields, activeTemplates, "performanceSalary",
                    row.getPerformanceSalary(), "绩效工资");
            addTemplateRequiredField(missingFields, activeTemplates, "salaryTotal",
                    row.getSalaryTotal(), "薪资合计");
            addTemplateRequiredField(missingFields, activeTemplates, "salaryVersion",
                    row.getSalaryVersion(), "薪资版本");
            addTemplateRequiredField(missingFields, activeTemplates, "servicePersonType",
                    row.getServicePersonType(), "劳务人员类型");
            addTemplateRequiredField(missingFields, activeTemplates, "insuranceType",
                    row.getInsuranceType(), "保险类型");
            if (StringUtils.isNotBlank(packageMatchIssue))
            {
                missingFields.add(packageMatchIssue);
            }
            else if (StringUtils.isNotBlank(row.getPackageMatchCode()))
            {
                String planRouteCode = resolvePlanRouteCode(plan);
                if (!row.getPackageMatchCode().equals(planRouteCode))
                {
                    missingFields.add(ROUTE_PLAN_MISMATCH);
                }
            }
        }
        if (activeTemplates == null || activeTemplates.isEmpty())
        {
            missingFields.add("模板清单");
        }
        return missingFields;
    }

    private String resolveOnboardingPackageMatch(OaSignBatchPreviewRow row)
    {
        row.setPackageMatchCode(null);
        row.setPackageMatchName(null);
        if (!isOnboardScenario(row.getScenario()))
        {
            return null;
        }
        String rawEmploymentType = trimToNull(row.getEmploymentType());
        String rawSocialType = trimToNull(row.getSocialType());
        String employmentType = normalizeContractType(rawEmploymentType);
        String socialType = normalizeSocialType(rawSocialType);
        String postLevel = trimToNull(row.getPostLevelSnapshot());
        if (rawEmploymentType == null || rawSocialType == null || postLevel == null)
        {
            return null;
        }
        if (employmentType == null)
        {
            row.setPackageMatchName(SKIP_AUTO_MATCH);
            return "合同类型只能是劳动合同或劳务合同";
        }
        if (socialType == null)
        {
            row.setPackageMatchName(SKIP_AUTO_MATCH);
            return "社保类型只能是有社保或无社保";
        }
        Integer jobGrade = parseJobGrade(postLevel);
        if (jobGrade == null || jobGrade < 2 || jobGrade > 8)
        {
            row.setPackageMatchName(SKIP_AUTO_MATCH);
            return "职级不在 2-8 范围内";
        }
        if (CONTRACT_SERVICE.equals(employmentType) && SOCIAL_YES.equals(socialType))
        {
            row.setPackageMatchName(SKIP_AUTO_MATCH);
            return "劳务合同+有社保暂不支持自动匹配";
        }

        int band = jobGrade <= 4 ? 1 : jobGrade <= 6 ? 2 : 3;
        String gradeScope = band == 1 ? "2-4级" : band == 2 ? "5-6级" : "7-8级";
        String code;
        if (CONTRACT_LABOR.equals(employmentType) && SOCIAL_YES.equals(socialType))
        {
            code = "A" + band;
        }
        else if (CONTRACT_LABOR.equals(employmentType) && SOCIAL_NO.equals(socialType))
        {
            code = "A" + (band + 3);
        }
        else
        {
            code = "B" + band;
        }
        row.setPackageMatchCode(code);
        row.setPackageMatchName(code + " " + contractTypeLabel(employmentType)
                + socialTypeLabel(socialType) + " " + gradeScope);
        return null;
    }

    private String resolvePlanRouteCode(OaSignPlan plan)
    {
        if (plan == null)
        {
            return null;
        }
        OaSignBatchPreviewRow planRow = new OaSignBatchPreviewRow();
        planRow.setScenario(plan.getScenario());
        planRow.setEmploymentType(plan.getEmploymentType());
        planRow.setSocialType(plan.getSocialType());
        planRow.setPostLevelSnapshot(plan.getPostLevelSnapshot());
        String issue = resolveOnboardingPackageMatch(planRow);
        return StringUtils.isBlank(issue) ? planRow.getPackageMatchCode() : null;
    }

    private Integer parseJobGrade(String postLevel)
    {
        Matcher matcher = JOB_GRADE_NUMBER.matcher(postLevel);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private String skipReasonFor(List<String> missingFields)
    {
        if (missingFields != null)
        {
            for (String field : missingFields)
            {
                if (field != null && (ROUTE_PLAN_MISMATCH.equals(field)
                        || field.contains("自动匹配") || field.startsWith("职级不在")
                        || field.startsWith("合同类型只能") || field.startsWith("社保类型只能")))
                {
                    return SKIP_AUTO_MATCH;
                }
            }
        }
        return SKIP_INCOMPLETE;
    }

    private void markSkipped(OaSignBatchPreviewRow row, List<String> missingFields, String skipReason)
    {
        row.setCreatable(false);
        row.setSkipReason(skipReason);
        row.setMissingFields(missingFields);
    }

    private OaSignPackage buildDraftPackage(OaSignBatchPreviewRow row, OaSignPlan plan,
            Long shopDeptId)
    {
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setStatus(OaSignPackageStatus.DRAFT);
        signPackage.setSourcePlanId(plan.getPlanId());
        signPackage.setSourcePlanName(plan.getPlanName());
        signPackage.setShopDeptId(shopDeptId);
        signPackage.setLegalEntityIdSnapshot(null);
        signPackage.setLegalEntityNameSnapshot(null);
        signPackage.setEmployeeId(row.getEmployeeId());
        signPackage.setEmployeeNameSnapshot(row.getEmployeeNameSnapshot());
        signPackage.setEmployeePhoneSnapshot(row.getEmployeePhoneSnapshot());
        signPackage.setEmployeeIdCardSnapshot(row.getEmployeeIdCardSnapshot());
        signPackage.setEmployeeAddressSnapshot(row.getEmployeeAddressSnapshot());
        signPackage.setDeptIdSnapshot(row.getDeptIdSnapshot());
        signPackage.setDeptNameSnapshot(row.getDeptNameSnapshot());
        signPackage.setPostNameSnapshot(row.getPostNameSnapshot());
        signPackage.setPostLevelSnapshot(firstText(row.getPostLevelSnapshot(), plan.getPostLevelSnapshot()));
        signPackage.setScenario(firstText(row.getScenario(), plan.getScenario()));
        signPackage.setEmploymentType(firstText(row.getEmploymentType(), plan.getEmploymentType()));
        signPackage.setContractTermCodeSnapshot(row.getContractTermCodeSnapshot());
        signPackage.setSocialType(firstText(row.getSocialType(), plan.getSocialType()));
        signPackage.setServicePersonType(firstText(row.getServicePersonType(), plan.getServicePersonType()));
        signPackage.setInsuranceType(firstText(row.getInsuranceType(), plan.getInsuranceType()));
        signPackage.setSalaryVersion(firstText(row.getSalaryVersion(), plan.getSalaryVersion()));
        signPackage.setEntryDate(firstText(row.getEntryDate(), plan.getEntryDate()));
        signPackage.setContractStartDate(firstText(row.getContractStartDate(), plan.getContractStartDate()));
        signPackage.setContractEndDate(firstText(row.getContractEndDate(), plan.getContractEndDate()));
        signPackage.setProbationStartDate(firstText(row.getProbationStartDate(), plan.getProbationStartDate()));
        signPackage.setProbationEndDate(firstText(row.getProbationEndDate(), plan.getProbationEndDate()));
        signPackage.setBaseSalary(firstAmount(row.getBaseSalary(), plan.getBaseSalary()));
        signPackage.setPostSalary(firstAmount(row.getPostSalary(), plan.getPostSalary()));
        signPackage.setFieldAllowance(firstAmount(row.getFieldAllowance(), plan.getFieldAllowance()));
        signPackage.setPerformanceSalary(row.getPerformanceSalary());
        signPackage.setSalaryTotal(firstAmount(row.getSalaryTotal(), plan.getSalaryTotal()));
        return signPackage;
    }

    private void addTemplateRequiredField(List<String> missingFields,
            List<OaSignTemplate> templates, String placeholder, Object value, String label)
    {
        if (isPlaceholderRequired(templates, placeholder)
                && (value == null || value instanceof String text && StringUtils.isBlank(text))
                && !missingFields.contains(label))
        {
            missingFields.add(label);
        }
    }

    private boolean isPlaceholderRequired(List<OaSignTemplate> templates, String placeholder)
    {
        if (templates == null || StringUtils.isBlank(placeholder))
        {
            return false;
        }
        for (OaSignTemplate template : templates)
        {
            if (template == null || StringUtils.isBlank(template.getRequiredPlaceholders()))
            {
                continue;
            }
            for (String configured : template.getRequiredPlaceholders().split(","))
            {
                if (placeholder.equals(configured == null ? null : configured.trim()))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isOnboardScenario(String scenario)
    {
        return OaSignScenarioCodes.ONBOARD.equals(
                OaSignScenarioCodes.normalizeTaskScenario(scenario));
    }

    private String normalizeContractType(String value)
    {
        String normalized = trimToNull(value);
        if (normalized == null) return null;
        if ("劳动合同".equals(normalized) || CONTRACT_LABOR.equalsIgnoreCase(normalized))
            return CONTRACT_LABOR;
        if ("劳务合同".equals(normalized) || CONTRACT_SERVICE.equalsIgnoreCase(normalized))
            return CONTRACT_SERVICE;
        return null;
    }

    private String normalizeSocialType(String value)
    {
        String normalized = trimToNull(value);
        if (normalized == null) return null;
        if ("有社保".equals(normalized) || SOCIAL_YES.equalsIgnoreCase(normalized))
            return SOCIAL_YES;
        if ("无社保".equals(normalized) || SOCIAL_NO.equalsIgnoreCase(normalized))
            return SOCIAL_NO;
        return null;
    }

    private String contractTypeLabel(String value)
    {
        return CONTRACT_LABOR.equals(value) ? "劳动合同"
                : CONTRACT_SERVICE.equals(value) ? "劳务合同" : value;
    }

    private String socialTypeLabel(String value)
    {
        return SOCIAL_YES.equals(value) ? "有社保"
                : SOCIAL_NO.equals(value) ? "无社保" : value;
    }

    private List<String> validateDraftPackage(OaSignPackage signPackage)
    {
        Set<ConstraintViolation<OaSignPackage>> violations = resolveValidator().validate(signPackage);
        if (violations == null || violations.isEmpty())
        {
            return Collections.emptyList();
        }
        return violations.stream()
                .map(ConstraintViolation::getMessage)
                .distinct()
                .collect(Collectors.toList());
    }

    private Validator resolveValidator()
    {
        if (validator == null)
        {
            validator = Validation.buildDefaultValidatorFactory().getValidator();
        }
        return validator;
    }

    private String firstText(String... values)
    {
        if (values == null)
        {
            return null;
        }
        for (String value : values)
        {
            if (StringUtils.isNotBlank(value))
            {
                return value;
            }
        }
        return null;
    }

    private String trimToNull(String value)
    {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    private BigDecimal firstAmount(BigDecimal primary, BigDecimal fallback)
    {
        return primary != null ? primary : fallback;
    }

    private String blankToEmpty(String value)
    {
        return value == null ? "" : value;
    }

    private static class PlanContext
    {
        private OaSignPlan plan;
        private Long shopDeptId;
        private List<OaSignTemplate> activeTemplates;
    }
}
