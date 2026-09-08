package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.constant.OaSignTemplateType;
import com.erp.oa.domain.OaSignPlan;
import com.erp.oa.domain.OaSignPlanTemplate;
import com.erp.oa.domain.OaSignTemplate;
import com.erp.oa.mapper.OaSignPlanMapper;
import com.erp.oa.mapper.OaSignTemplateMapper;

@DisplayName("员工签约方案服务")
class OaSignPlanServiceImplTest
{
    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("保存签约方案时拒绝非法状态")
    void shouldRejectInvalidSaveStatus()
    {
        OaSignPlanServiceImpl service = planService();
        OaSignTemplateMapper templateMapper = (OaSignTemplateMapper) ReflectionTestUtils.getField(service, "templateMapper");
        when(templateMapper.selectOaSignTemplateById(10L)).thenReturn(template(10L, "renewal_labor_contract", 20));

        OaSignPlan plan = newPlan(1171L, binding(10L, null));
        plan.setStatus("2");

        assertThatThrownBy(() -> service.savePlan(plan, 1171L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("签约方案状态不正确");
    }

    @Test
    @DisplayName("保存续签方案时把历史renew别名规范为renewal")
    void shouldCanonicalizeRenewalScenarioWhenSaving()
    {
        OaSignPlanServiceImpl service = planService();
        OaSignPlanMapper planMapper = (OaSignPlanMapper) ReflectionTestUtils.getField(service, "planMapper");
        OaSignTemplateMapper templateMapper = (OaSignTemplateMapper) ReflectionTestUtils.getField(service, "templateMapper");
        ArgumentCaptor<OaSignPlan> planCaptor = ArgumentCaptor.forClass(OaSignPlan.class);
        when(templateMapper.selectOaSignTemplateById(10L)).thenReturn(template(10L, "renewal_labor_contract", 20));
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.getArgument(0, OaSignPlan.class).setPlanId(100L);
            return 1;
        }).when(planMapper).insertOaSignPlan(planCaptor.capture());
        when(planMapper.batchInsertPlanTemplates(org.mockito.Mockito.any())).thenReturn(1);
        when(planMapper.selectOaSignPlanById(100L)).thenAnswer(invocation -> planCaptor.getValue());
        when(planMapper.selectPlanTemplatesByPlanId(100L)).thenReturn(Collections.emptyList());

        OaSignPlan plan = newPlan(1171L, binding(10L, null));
        plan.setScenario("renew");

        service.savePlan(plan, 1171L);

        assertThat(planCaptor.getValue().getScenario()).isEqualTo("renewal");
    }

    @Test
    @DisplayName("保存签约方案时拒绝不存在或停用模板")
    void shouldRejectMissingOrDisabledTemplates()
    {
        OaSignPlanServiceImpl service = planService();
        OaSignTemplateMapper templateMapper = (OaSignTemplateMapper) ReflectionTestUtils.getField(service, "templateMapper");
        when(templateMapper.selectOaSignTemplateById(10L)).thenReturn(null);
        OaSignTemplate disabled = template(11L, "onboard_archive_catalog", 20);
        disabled.setStatus("1");
        when(templateMapper.selectOaSignTemplateById(11L)).thenReturn(disabled);

        assertThatThrownBy(() -> service.savePlan(newPlan(1171L, binding(10L, null)), 1171L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("签约方案模板不存在或已停用");
        assertThatThrownBy(() -> service.savePlan(newPlan(1171L, binding(11L, null)), 1171L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("签约方案模板不存在或已停用");
    }

    @Test
    @DisplayName("保存签约方案时拒绝重复模板")
    void shouldRejectDuplicateTemplates()
    {
        OaSignPlanServiceImpl service = planService();
        OaSignTemplateMapper templateMapper = (OaSignTemplateMapper) ReflectionTestUtils.getField(service, "templateMapper");
        when(templateMapper.selectOaSignTemplateById(10L)).thenReturn(template(10L, "onboard_labor_contract", 20));

        assertThatThrownBy(() -> service.savePlan(newPlan(1171L, binding(10L, null), binding(10L, 2)), 1171L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("签约方案模板不能重复");
    }

    @Test
    @DisplayName("保存方案时立即拒绝跨场景或登记场景损坏的模板")
    void shouldRejectCrossScenarioTemplateWhenSaving()
    {
        OaSignPlanServiceImpl service = planService();
        OaSignTemplateMapper templateMapper = (OaSignTemplateMapper) ReflectionTestUtils.getField(service, "templateMapper");
        when(templateMapper.selectOaSignTemplateById(10L))
                .thenReturn(template(10L, "renewal_labor_contract", 20));

        assertThatThrownBy(() -> service.savePlan(
                newPlan(1171L, binding(10L, null)), 1171L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("入职方案只能绑定入职场景模板");

        OaSignTemplate corrupted = template(11L, "onboard_commitment", 20);
        corrupted.setScenario("transfer");
        when(templateMapper.selectOaSignTemplateById(11L)).thenReturn(corrupted);
        assertThatThrownBy(() -> service.savePlan(
                newPlan(1171L, binding(11L, null)), 1171L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("模板登记场景与模板类型不一致");
    }

    @Test
    @DisplayName("更新签约方案状态时拒绝非法状态")
    void shouldRejectInvalidStatusUpdate()
    {
        OaSignPlanServiceImpl service = planService();

        assertThatThrownBy(() -> service.updatePlanStatus(100L, "2", 1171L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("签约方案状态不正确");
    }

    @Test
    @DisplayName("更新签约方案状态时写入状态和更新人")
    void shouldUpdatePlanStatus()
    {
        OaSignPlanServiceImpl service = planService();
        OaSignPlanMapper planMapper = (OaSignPlanMapper) ReflectionTestUtils.getField(service, "planMapper");
        ArgumentCaptor<OaSignPlan> updateCaptor = ArgumentCaptor.forClass(OaSignPlan.class);
        SecurityContextHolder.setUserName("admin");
        OaSignPlan existing = existingPlan(100L, 0L);
        when(planMapper.selectOaSignPlanById(100L)).thenReturn(existing);
        when(planMapper.updateOaSignPlan(updateCaptor.capture())).thenReturn(1);
        when(planMapper.selectPlanTemplatesByPlanId(100L)).thenReturn(Collections.emptyList());

        OaSignPlan saved = service.updatePlanStatus(100L, "1", 1171L);

        assertThat(saved.getPlanId()).isEqualTo(100L);
        assertThat(updateCaptor.getValue().getStatus()).isEqualTo("1");
        assertThat(updateCaptor.getValue().getUpdateBy()).isEqualTo("admin");
    }

    @Test
    @DisplayName("查询签约方案详情时拒绝非全局历史方案")
    void shouldRejectOutOfScopePlanDetail()
    {
        OaSignPlanServiceImpl service = planService();
        OaSignPlanMapper planMapper = (OaSignPlanMapper) ReflectionTestUtils.getField(service, "planMapper");
        OaSignPlan existing = existingPlan(100L, null);
        when(planMapper.selectOaSignPlanById(100L)).thenReturn(existing);

        assertThatThrownBy(() -> service.getPlanDetail(100L, 1171L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("签约方案不存在或无权限访问");
    }

    @Test
    @DisplayName("任意签约组织都只查询HR统一方案")
    void shouldListGlobalPlansRegardlessOfSelectedOrganization()
    {
        OaSignPlanServiceImpl service = planService();
        OaSignPlanMapper planMapper = (OaSignPlanMapper) ReflectionTestUtils.getField(service, "planMapper");
        ArgumentCaptor<OaSignPlan> queryCaptor = ArgumentCaptor.forClass(OaSignPlan.class);
        when(planMapper.selectOaSignPlanList(queryCaptor.capture())).thenReturn(Collections.emptyList());

        service.selectPlanList(new OaSignPlan(), 1157L);

        assertThat(queryCaptor.getValue().getShopDeptId()).isEqualTo(0L);
    }

    @Test
    @DisplayName("保存方案时忽略客户端组织并强制写入HR统一作用域")
    void shouldForceGlobalScopeWhenClientSendsOrganization()
    {
        OaSignPlanServiceImpl service = planService();
        OaSignPlanMapper planMapper = (OaSignPlanMapper) ReflectionTestUtils.getField(service, "planMapper");
        OaSignTemplateMapper templateMapper = (OaSignTemplateMapper) ReflectionTestUtils.getField(service, "templateMapper");
        ArgumentCaptor<OaSignPlan> planCaptor = ArgumentCaptor.forClass(OaSignPlan.class);
        when(templateMapper.selectOaSignTemplateById(10L))
                .thenReturn(template(10L, "onboard_labor_contract", 20));
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.getArgument(0, OaSignPlan.class).setPlanId(100L);
            return 1;
        }).when(planMapper).insertOaSignPlan(planCaptor.capture());
        when(planMapper.batchInsertPlanTemplates(org.mockito.Mockito.any())).thenReturn(1);
        when(planMapper.selectOaSignPlanById(100L)).thenAnswer(invocation -> planCaptor.getValue());
        when(planMapper.selectPlanTemplatesByPlanId(100L)).thenReturn(Collections.emptyList());

        OaSignPlan staleClientPlan = newPlan(1185L, binding(10L, null));

        service.savePlan(staleClientPlan, 1157L);

        assertThat(planCaptor.getValue().getShopDeptId()).isEqualTo(0L);
    }

    @Test
    @DisplayName("更新签约方案时替换模板绑定并检查写入行数")
    @SuppressWarnings("unchecked")
    void shouldUpdatePlanAndReplaceTemplateBindings()
    {
        OaSignPlanServiceImpl service = planService();
        OaSignPlanMapper planMapper = (OaSignPlanMapper) ReflectionTestUtils.getField(service, "planMapper");
        OaSignTemplateMapper templateMapper = (OaSignTemplateMapper) ReflectionTestUtils.getField(service, "templateMapper");
        ArgumentCaptor<OaSignPlan> updateCaptor = ArgumentCaptor.forClass(OaSignPlan.class);
        ArgumentCaptor<List<OaSignPlanTemplate>> templateCaptor = ArgumentCaptor.forClass(List.class);
        SecurityContextHolder.setUserName("admin");
        when(planMapper.selectOaSignPlanById(100L)).thenReturn(existingPlan(100L, 0L));
        when(templateMapper.selectOaSignTemplateById(10L)).thenReturn(template(10L, "onboard_labor_contract", 20));
        when(templateMapper.selectOaSignTemplateById(11L)).thenReturn(template(11L, "onboard_archive_catalog", 30));
        when(planMapper.updateOaSignPlan(updateCaptor.capture())).thenReturn(1);
        when(planMapper.batchInsertPlanTemplates(templateCaptor.capture())).thenReturn(2);
        when(planMapper.selectPlanTemplatesByPlanId(100L)).thenAnswer(invocation -> templateCaptor.getValue());

        OaSignPlan plan = newPlan(1171L, binding(10L, null), binding(11L, 5));
        plan.setPlanId(100L);
        plan.setPlanName("更新后的签约方案");

        OaSignPlan saved = service.savePlan(plan, 1171L);

        assertThat(saved.getTemplates()).hasSize(2);
        assertThat(updateCaptor.getValue().getUpdateBy()).isEqualTo("admin");
        verify(planMapper).deletePlanTemplatesByPlanId(100L);
        assertThat(templateCaptor.getValue()).extracting(OaSignPlanTemplate::getPlanId)
                .containsExactly(100L, 100L);
        assertThat(templateCaptor.getValue()).extracting(OaSignPlanTemplate::getSortOrder)
                .containsExactly(20, 5);
    }

    @Test
    @DisplayName("更新签约方案写入失败时抛出异常")
    void shouldRejectUpdatePlanWhenAffectedRowsUnexpected()
    {
        OaSignPlanServiceImpl service = planService();
        OaSignPlanMapper planMapper = (OaSignPlanMapper) ReflectionTestUtils.getField(service, "planMapper");
        OaSignTemplateMapper templateMapper = (OaSignTemplateMapper) ReflectionTestUtils.getField(service, "templateMapper");
        when(planMapper.selectOaSignPlanById(100L)).thenReturn(existingPlan(100L, 0L));
        when(templateMapper.selectOaSignTemplateById(10L)).thenReturn(template(10L, "onboard_labor_contract", 20));
        when(planMapper.updateOaSignPlan(org.mockito.Mockito.any())).thenReturn(0);

        OaSignPlan plan = newPlan(1171L, binding(10L, null));
        plan.setPlanId(100L);

        assertThatThrownBy(() -> service.savePlan(plan, 1171L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("签约方案保存失败");
    }

    @Test
    @DisplayName("保存签约方案模板绑定行数不一致时抛出异常")
    void shouldRejectTemplateBindingInsertMismatch()
    {
        OaSignPlanServiceImpl service = planService();
        OaSignPlanMapper planMapper = (OaSignPlanMapper) ReflectionTestUtils.getField(service, "planMapper");
        OaSignTemplateMapper templateMapper = (OaSignTemplateMapper) ReflectionTestUtils.getField(service, "templateMapper");
        when(templateMapper.selectOaSignTemplateById(10L)).thenReturn(template(10L, "onboard_labor_contract", 20));
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.getArgument(0, OaSignPlan.class).setPlanId(100L);
            return 1;
        }).when(planMapper).insertOaSignPlan(org.mockito.Mockito.any());
        when(planMapper.batchInsertPlanTemplates(org.mockito.Mockito.any())).thenReturn(0);

        assertThatThrownBy(() -> service.savePlan(newPlan(1171L, binding(10L, null)), 1171L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("签约方案保存失败");
    }

    @Test
    @DisplayName("保存签约方案时必须绑定启用模板")
    void shouldRejectPlanWithoutTemplates()
    {
        OaSignPlanServiceImpl service = planService();
        OaSignPlan plan = new OaSignPlan();
        plan.setPlanName("入职签约方案");
        plan.setShopDeptId(1171L);
        SecurityContextHolder.setUserName("admin");

        assertThatThrownBy(() -> service.savePlan(plan, 1171L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("签约方案至少需要绑定一个模板");
    }

    @Test
    @DisplayName("保存签约方案时写入方案和模板绑定")
    @SuppressWarnings("unchecked")
    void shouldSavePlanAndTemplateBindings()
    {
        OaSignPlanServiceImpl service = planService();
        OaSignPlanMapper planMapper = (OaSignPlanMapper) ReflectionTestUtils.getField(service, "planMapper");
        OaSignTemplateMapper templateMapper = (OaSignTemplateMapper) ReflectionTestUtils.getField(service, "templateMapper");
        ArgumentCaptor<OaSignPlan> planCaptor = ArgumentCaptor.forClass(OaSignPlan.class);
        ArgumentCaptor<List<OaSignPlanTemplate>> templateCaptor = ArgumentCaptor.forClass(List.class);
        SecurityContextHolder.setUserName("admin");

        OaSignTemplate laborTemplate = template(10L, "onboard_labor_contract", 20);
        OaSignTemplate archiveTemplate = template(11L, "onboard_archive_catalog", null);
        OaSignTemplate commitmentTemplate = template(12L, "onboard_commitment", null);
        when(templateMapper.selectOaSignTemplateById(10L)).thenReturn(laborTemplate);
        when(templateMapper.selectOaSignTemplateById(11L)).thenReturn(archiveTemplate);
        when(templateMapper.selectOaSignTemplateById(12L)).thenReturn(commitmentTemplate);
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.getArgument(0, OaSignPlan.class).setPlanId(100L);
            return 1;
        }).when(planMapper).insertOaSignPlan(planCaptor.capture());
        when(planMapper.batchInsertPlanTemplates(templateCaptor.capture())).thenReturn(3);
        when(planMapper.selectOaSignPlanById(100L)).thenAnswer(invocation -> planCaptor.getValue());
        when(planMapper.selectPlanTemplatesByPlanId(100L)).thenAnswer(invocation -> templateCaptor.getValue());

        OaSignPlan plan = new OaSignPlan();
        plan.setPlanName("入职签约方案");
        plan.setShopDeptId(1171L);
        OaSignPlanTemplate firstBinding = binding(10L, null);
        OaSignPlanTemplate secondBinding = binding(11L, 3);
        OaSignPlanTemplate thirdBinding = binding(12L, null);
        plan.setTemplates(Arrays.asList(firstBinding, secondBinding, thirdBinding));

        OaSignPlan saved = service.savePlan(plan, 1171L);

        assertThat(saved.getTemplates()).hasSize(3);
        assertThat(planCaptor.getValue().getPlanName()).isEqualTo("入职签约方案");
        assertThat(planCaptor.getValue().getScenario()).isEqualTo("onboard");
        assertThat(planCaptor.getValue().getShopDeptId()).isEqualTo(0L);
        assertThat(planCaptor.getValue().getCreateBy()).isEqualTo("admin");
        assertThat(templateCaptor.getValue()).extracting(OaSignPlanTemplate::getTemplateId)
                .containsExactly(10L, 11L, 12L);
        assertThat(templateCaptor.getValue()).extracting(OaSignPlanTemplate::getTemplateType)
                .containsExactly("ONBOARD_LABOR_CONTRACT", "ONBOARD_ARCHIVE_CATALOG", "ONBOARD_COMMITMENT");
        assertThat(templateCaptor.getValue()).extracting(OaSignPlanTemplate::getSortOrder)
                .containsExactly(20, 3, 100);
    }

    @Test
    @DisplayName("有社保方案忽略客户端A版选择并确定性派生B版")
    void shouldDeriveInsuredSalaryVersionInsteadOfTrustingClientSelection()
    {
        OaSignPlanServiceImpl service = planService();
        OaSignPlanMapper planMapper = (OaSignPlanMapper) ReflectionTestUtils.getField(service, "planMapper");
        OaSignTemplateMapper templateMapper = (OaSignTemplateMapper) ReflectionTestUtils.getField(service, "templateMapper");
        OaSignTemplate salaryTemplate = template(20L, OaSignTemplateType.ONBOARD_SALARY_CONFIRM, 20);
        salaryTemplate.setSalaryVersion("B");
        when(templateMapper.selectOaSignTemplateById(20L)).thenReturn(salaryTemplate);
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.getArgument(0, OaSignPlan.class).setPlanId(101L);
            return 1;
        }).when(planMapper).insertOaSignPlan(org.mockito.Mockito.any());
        when(planMapper.batchInsertPlanTemplates(org.mockito.Mockito.any())).thenReturn(1);
        when(planMapper.selectOaSignPlanById(101L)).thenAnswer(invocation -> {
            OaSignPlan saved = new OaSignPlan();
            saved.setPlanId(101L);
            saved.setPlanName("有社保方案");
            saved.setShopDeptId(0L);
            saved.setSocialType("SOCIAL_INSURED");
            saved.setSalaryVersion("B");
            return saved;
        });
        when(planMapper.selectPlanTemplatesByPlanId(101L)).thenReturn(List.of(binding(20L, 10)));

        OaSignPlan plan = newPlan(1171L, binding(20L, 10));
        plan.setPlanName("有社保方案");
        plan.setEmploymentType("劳动合同");
        plan.setSocialType("有社保");
        plan.setSalaryVersion("a");

        OaSignPlan saved = service.savePlan(plan, 1171L);

        assertThat(plan.getSocialType()).isEqualTo("SOCIAL_INSURED");
        assertThat(plan.getSalaryVersion()).isEqualTo("B");
        assertThat(saved.getSalaryVersion()).isEqualTo("B");
    }

    @Test
    @DisplayName("无社保方案无需输入薪酬版本即可派生A版")
    void shouldDeriveUninsuredSalaryVersionWithoutDirectVersionInput()
    {
        OaSignPlanServiceImpl service = planService();
        OaSignPlan plan = newPlan(1171L, binding(20L, 10));
        plan.setScenario("onboard");
        plan.setEmploymentType("劳动合同");
        plan.setSocialType("无社保");
        plan.setSalaryVersion(null);

        ReflectionTestUtils.invokeMethod(service, "normalizeOnboardSalaryVersion", plan);

        assertThat(plan.getSocialType()).isEqualTo("SOCIAL_UNINSURED");
        assertThat(plan.getSalaryVersion()).isEqualTo("A");
    }

    @Test
    @DisplayName("入职劳动合同方案社保口径缺失时拒绝保存")
    void shouldRejectUnknownSocialTypeForOnboardLaborPlan()
    {
        OaSignPlanServiceImpl service = planService();
        OaSignPlan plan = newPlan(1171L, binding(20L, 10));
        plan.setEmploymentType("劳动合同");
        plan.setSocialType("待确认");

        assertThatThrownBy(() -> service.savePlan(plan, 1171L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("必须明确社保口径");
    }

    private OaSignPlanServiceImpl planService()
    {
        OaSignPlanMapper planMapper = mock(OaSignPlanMapper.class);
        OaSignTemplateMapper templateMapper = mock(OaSignTemplateMapper.class);
        OaSignPlanServiceImpl service = new OaSignPlanServiceImpl();
        ReflectionTestUtils.setField(service, "planMapper", planMapper);
        ReflectionTestUtils.setField(service, "templateMapper", templateMapper);
        return service;
    }

    private OaSignTemplate template(Long templateId, String templateType, Integer sortOrder)
    {
        OaSignTemplateType.Option option = OaSignTemplateType.require(
                templateType.toUpperCase(Locale.ROOT));
        OaSignTemplate template = new OaSignTemplate();
        template.setTemplateId(templateId);
        template.setTemplateType(option.getCode());
        template.setScenario(option.getScenario());
        template.setStatus("0");
        template.setSortOrder(sortOrder);
        return template;
    }

    private OaSignPlan newPlan(Long shopDeptId, OaSignPlanTemplate... bindings)
    {
        OaSignPlan plan = new OaSignPlan();
        plan.setPlanName("入职签约方案");
        plan.setShopDeptId(shopDeptId);
        plan.setTemplates(Arrays.asList(bindings));
        return plan;
    }

    private OaSignPlan existingPlan(Long planId, Long shopDeptId)
    {
        OaSignPlan plan = new OaSignPlan();
        plan.setPlanId(planId);
        plan.setPlanName("入职签约方案");
        plan.setShopDeptId(shopDeptId);
        plan.setStatus("0");
        return plan;
    }

    private OaSignPlanTemplate binding(Long templateId, Integer sortOrder)
    {
        OaSignPlanTemplate binding = new OaSignPlanTemplate();
        binding.setTemplateId(templateId);
        binding.setSortOrder(sortOrder);
        return binding;
    }
}
