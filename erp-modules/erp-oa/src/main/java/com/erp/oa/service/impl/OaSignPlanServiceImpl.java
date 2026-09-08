package com.erp.oa.service.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.oa.constant.OaSignPlanScope;
import com.erp.oa.constant.OaOnboardSalaryVersionPolicy;
import com.erp.oa.domain.OaSignPlan;
import com.erp.oa.domain.OaSignPlanTemplate;
import com.erp.oa.domain.OaSignTemplate;
import com.erp.oa.constant.OaSignScenarioCodes;
import com.erp.oa.constant.OaSignTemplateType;
import com.erp.oa.mapper.OaSignPlanMapper;
import com.erp.oa.mapper.OaSignTemplateMapper;
import com.erp.oa.service.IOaSignPlanService;

@Service
public class OaSignPlanServiceImpl implements IOaSignPlanService
{
    private static final String DEFAULT_SCENARIO = "onboard";
    private static final String ENABLED_STATUS = "0";
    private static final String DISABLED_STATUS = "1";
    private static final int DEFAULT_SORT_ORDER = 100;

    @Autowired
    private OaSignPlanMapper planMapper;

    @Autowired
    private OaSignTemplateMapper templateMapper;

    @Override
    public List<OaSignPlan> selectPlanList(OaSignPlan plan, Long selectedShopDeptId)
    {
        OaSignPlan query = plan == null ? new OaSignPlan() : plan;
        if (StringUtils.isNotBlank(query.getScenario()))
        {
            query.setScenario(OaSignScenarioCodes.normalizePackageScenario(query.getScenario()));
        }
        // Plans are maintained once by the configured HR user and shared by all
        // signing organizations. The selected organization is deliberately ignored.
        query.setShopDeptId(OaSignPlanScope.GLOBAL_SHOP_DEPT_ID);
        List<OaSignPlan> list = planMapper.selectOaSignPlanList(query);
        if (list != null)
        {
            for (OaSignPlan row : list)
            {
                attachTemplates(row);
            }
        }
        return list;
    }

    @Override
    public OaSignPlan getPlanDetail(Long planId, Long selectedShopDeptId)
    {
        OaSignPlan plan = assertAndGetGlobalPlan(planId);
        attachTemplates(plan);
        return plan;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaSignPlan savePlan(OaSignPlan plan, Long selectedShopDeptId)
    {
        if (plan == null || StringUtils.isBlank(plan.getPlanName()))
        {
            throw new ServiceException("方案名称不能为空");
        }
        // Never trust or preserve an organization sent by an old/stale client.
        plan.setShopDeptId(OaSignPlanScope.GLOBAL_SHOP_DEPT_ID);
        if (StringUtils.isBlank(plan.getScenario()))
        {
            plan.setScenario(DEFAULT_SCENARIO);
        }
        else
        {
            plan.setScenario(OaSignScenarioCodes.normalizePackageScenario(plan.getScenario()));
        }
        if (!OaSignScenarioCodes.isSupported(plan.getScenario()))
        {
            throw new ServiceException("不支持的签约场景：" + plan.getScenario());
        }
        normalizeOnboardSalaryVersion(plan);
        plan.setStatus(normalizeStatus(plan.getStatus(), true));
        if (plan.getSortOrder() == null)
        {
            plan.setSortOrder(DEFAULT_SORT_ORDER);
        }
        List<OaSignPlanTemplate> normalizedTemplates = normalizeTemplates(plan);
        if (plan.getPlanId() == null)
        {
            plan.setCreateBy(SecurityUtils.getUsername());
            assertAffectedOne(planMapper.insertOaSignPlan(plan));
        }
        else
        {
            assertAndGetGlobalPlan(plan.getPlanId());
            plan.setUpdateBy(SecurityUtils.getUsername());
            assertAffectedOne(planMapper.updateOaSignPlan(plan));
        }
        for (OaSignPlanTemplate binding : normalizedTemplates)
        {
            binding.setPlanId(plan.getPlanId());
        }
        planMapper.deletePlanTemplatesByPlanId(plan.getPlanId());
        if (planMapper.batchInsertPlanTemplates(normalizedTemplates) != normalizedTemplates.size())
        {
            throw new ServiceException("签约方案保存失败");
        }
        return getPlanDetail(plan.getPlanId(), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaSignPlan updatePlanStatus(Long planId, String status, Long selectedShopDeptId)
    {
        String normalizedStatus = normalizeStatus(status, false);
        assertAndGetGlobalPlan(planId);
        OaSignPlan update = new OaSignPlan();
        update.setPlanId(planId);
        update.setStatus(normalizedStatus);
        update.setUpdateBy(SecurityUtils.getUsername());
        if (planMapper.updateOaSignPlan(update) != 1)
        {
            throw new ServiceException("签约方案状态更新失败");
        }
        return getPlanDetail(planId, null);
    }

    private List<OaSignPlanTemplate> normalizeTemplates(OaSignPlan plan)
    {
        if (plan.getTemplates() == null || plan.getTemplates().isEmpty())
        {
            throw new ServiceException("签约方案至少需要绑定一个模板");
        }
        List<OaSignPlanTemplate> normalizedTemplates = new ArrayList<>();
        Set<Long> templateIds = new HashSet<>();
        int salaryConfirmationCount = 0;
        for (OaSignPlanTemplate binding : plan.getTemplates())
        {
            Long templateId = binding == null ? null : binding.getTemplateId();
            if (templateId != null && !templateIds.add(templateId))
            {
                throw new ServiceException("签约方案模板不能重复");
            }
            OaSignTemplate template = templateId == null ? null : templateMapper.selectOaSignTemplateById(templateId);
            if (template == null || !ENABLED_STATUS.equals(template.getStatus()))
            {
                throw new ServiceException("签约方案模板不存在或已停用");
            }
            OaSignTemplateType.Option option;
            try
            {
                option = OaSignTemplateType.require(
                        template.getTemplateType().trim().toUpperCase(Locale.ROOT));
            }
            catch (RuntimeException ex)
            {
                throw new ServiceException("签约方案包含未登记模板类型");
            }
            String planScenario = OaSignScenarioCodes.normalizePackageScenario(plan.getScenario());
            String catalogScenario = OaSignScenarioCodes.normalizePackageScenario(option.getScenario());
            if (!planScenario.equals(catalogScenario))
            {
                throw new ServiceException(scenarioLabel(planScenario) + "方案只能绑定"
                        + scenarioLabel(planScenario) + "场景模板");
            }
            if (!catalogScenario.equals(
                    OaSignScenarioCodes.normalizePackageScenario(template.getScenario())))
            {
                throw new ServiceException("模板登记场景与模板类型不一致：" + option.getCode());
            }
            if (OaSignScenarioCodes.ONBOARD.equals(
                    OaSignScenarioCodes.normalizeTaskScenario(planScenario))
                    && OaSignTemplateType.ONBOARD_SALARY_CONFIRM.equals(option.getCode()))
            {
                if (!isOnboardLaborPlan(plan))
                {
                    throw new ServiceException("入职劳务合同方案不能绑定薪酬结构确认书");
                }
                salaryConfirmationCount++;
                String expected = OaOnboardSalaryVersionPolicy.normalizeSalaryVersion(
                        plan.getSalaryVersion());
                String actual = OaOnboardSalaryVersionPolicy.normalizeSalaryVersion(
                        template.getSalaryVersion());
                if (expected == null || !expected.equals(actual))
                {
                    throw new ServiceException("薪酬版本" + expected
                            + "的方案只能绑定薪酬结构确认书（" + expected + "版）");
                }
            }
            OaSignPlanTemplate normalized = new OaSignPlanTemplate();
            normalized.setTemplateId(template.getTemplateId());
            normalized.setTemplateType(option.getCode());
            normalized.setSortOrder(resolveSortOrder(binding.getSortOrder(), template.getSortOrder()));
            normalizedTemplates.add(normalized);
        }
        if (isOnboardLaborPlan(plan) && salaryConfirmationCount != 1)
        {
            throw new ServiceException("入职劳动合同方案必须且只能绑定一份匹配薪酬版本的薪酬结构确认书");
        }
        return normalizedTemplates;
    }

    private void normalizeOnboardSalaryVersion(OaSignPlan plan)
    {
        if (!isOnboardLaborPlan(plan))
        {
            return;
        }
        String socialType = OaOnboardSalaryVersionPolicy.normalizeSocialType(
                plan.getSocialType());
        String required = OaOnboardSalaryVersionPolicy.requiredSalaryVersion(socialType);
        if (required == null)
        {
            throw new ServiceException("入职劳动合同方案必须明确社保口径");
        }
        // 薪酬版本是社保事实的确定性派生值，不能接受客户端单独选择 A/B。
        plan.setSocialType(socialType);
        plan.setSalaryVersion(required);
    }

    private boolean isOnboardLaborPlan(OaSignPlan plan)
    {
        if (plan == null || !OaSignScenarioCodes.ONBOARD.equals(
                OaSignScenarioCodes.normalizeTaskScenario(plan.getScenario())))
        {
            return false;
        }
        String employmentType = StringUtils.trim(plan.getEmploymentType());
        return "劳动合同".equals(employmentType)
                || "LABOR_CONTRACT".equalsIgnoreCase(employmentType);
    }

    private String scenarioLabel(String scenario)
    {
        return switch (OaSignScenarioCodes.normalizeTaskScenario(scenario))
        {
            case OaSignScenarioCodes.ONBOARD -> "入职";
            case OaSignScenarioCodes.RENEWAL -> "续签";
            case OaSignScenarioCodes.TRANSFER -> "调岗";
            case OaSignScenarioCodes.REGULARIZE -> "转正";
            case OaSignScenarioCodes.OFFBOARD -> "离职";
            default -> "签约";
        };
    }

    private Integer resolveSortOrder(Integer bindingSortOrder, Integer templateSortOrder)
    {
        if (bindingSortOrder != null)
        {
            return bindingSortOrder;
        }
        return templateSortOrder != null ? templateSortOrder : DEFAULT_SORT_ORDER;
    }

    private String normalizeStatus(String status, boolean defaultBlank)
    {
        if (StringUtils.isBlank(status))
        {
            if (defaultBlank)
            {
                return ENABLED_STATUS;
            }
            throw new ServiceException("签约方案状态不正确");
        }
        if (!ENABLED_STATUS.equals(status) && !DISABLED_STATUS.equals(status))
        {
            throw new ServiceException("签约方案状态不正确");
        }
        return status;
    }

    private void assertAffectedOne(int affectedRows)
    {
        if (affectedRows != 1)
        {
            throw new ServiceException("签约方案保存失败");
        }
    }

    private OaSignPlan assertAndGetGlobalPlan(Long planId)
    {
        OaSignPlan plan = planMapper.selectOaSignPlanById(planId);
        if (plan == null || !OaSignPlanScope.isGlobal(plan.getShopDeptId()))
        {
            throw new ServiceException("签约方案不存在或无权限访问");
        }
        return plan;
    }

    private void attachTemplates(OaSignPlan plan)
    {
        if (plan != null)
        {
            plan.setTemplates(planMapper.selectPlanTemplatesByPlanId(plan.getPlanId()));
        }
    }
}
