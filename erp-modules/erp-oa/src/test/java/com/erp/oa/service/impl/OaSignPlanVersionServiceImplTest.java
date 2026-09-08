package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.oa.constant.OaSignTemplateType;
import com.erp.oa.domain.OaSignPlan;
import com.erp.oa.domain.OaSignPlanTemplate;
import com.erp.oa.domain.OaSignPlanVersion;
import com.erp.oa.domain.OaSignPlanVersionTemplate;
import com.erp.oa.domain.OaSignTemplate;
import com.erp.oa.mapper.OaSignPlanVersionMapper;

@DisplayName("不可变签约方案版本发布服务")
class OaSignPlanVersionServiceImplTest
{
    private static final Long PLAN_ID = 100L;
    private static final Long SHOP_ID = 1171L;

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "REGULARIZE_CONFIRMATION",
            "REGULARIZE_POST_DUTY",
            "REGULARIZE_SALARY_CONFIRM"
    })
    @DisplayName("发布链冻结三类转正模板类型并执行占位符校验")
    void shouldPublishRegularizationTemplateTypes(String templateType)
    {
        OaSignPlan plan = validPlan();
        plan.setScenario("regularize");
        plan.setRuleJson("{\"servicePersonType\":null,\"insuranceType\":null}");
        OaSignTemplate template = validTemplate(10L, templateType);
        template.setScenario("regularize");
        Fixture fixture = fixture(plan, List.of(binding(template, 20)));
        when(fixture.mapper.selectNextVersionNo(PLAN_ID)).thenReturn(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.getArgument(0, OaSignPlanVersion.class).setVersionId(501L);
            return 1;
        }).when(fixture.mapper).insertPlanVersion(any());
        when(fixture.mapper.batchInsertPlanVersionTemplates(any())).thenReturn(1);

        OaSignPlanVersion published = fixture.service.publish(PLAN_ID, SHOP_ID);

        assertThat(published.getScenario()).isEqualTo("regularize");
        assertThat(published.getTemplates()).singleElement()
                .extracting(OaSignPlanVersionTemplate::getTemplateType)
                .isEqualTo(templateType);
        verify(fixture.documentService).assertTemplateContainsRequiredPlaceholders(
                templateType, "/profile/labor.docx");
        verify(fixture.documentService).assertTemplateLegalEntityCompatible(
                templateType, "/profile/labor.docx", null);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "ONBOARD_POST_DUTY", "CUSTOM_REGULARIZE_DOCUMENT" })
    @DisplayName("转正方案在发布阶段拒绝跨场景或未知模板类型")
    void shouldRejectNonRegularizationCatalogTypesWhenPublishingRegularizationPlan(
            String templateType)
    {
        OaSignPlan plan = validPlan();
        plan.setScenario("regularize");
        OaSignTemplate template = validTemplate(10L, templateType);
        template.setScenario("regularize");
        Fixture fixture = fixture(plan, List.of(binding(template, 20)));

        assertThatThrownBy(() -> fixture.service.publish(PLAN_ID, SHOP_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("转正方案")
                .hasMessageContaining("模板类型");
        verify(fixture.mapper, never()).insertPlanVersion(any());
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("scenarioTemplateTypes")
    @DisplayName("五场景发布只接受本场景注册模板并规范化场景")
    void shouldPublishRegisteredTemplateTypeForEveryScenario(String rawScenario,
            String templateType, String expectedScenario)
    {
        OaSignPlan plan = validPlan();
        plan.setScenario(rawScenario);
        OaSignTemplate template = validTemplate(10L, templateType);
        template.setScenario(OaSignTemplateType.require(templateType).getScenario());
        Fixture fixture = fixture(plan, List.of(binding(template, 20)));
        when(fixture.mapper.selectNextVersionNo(PLAN_ID)).thenReturn(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.getArgument(0, OaSignPlanVersion.class).setVersionId(501L);
            return 1;
        }).when(fixture.mapper).insertPlanVersion(any());
        when(fixture.mapper.batchInsertPlanVersionTemplates(any())).thenReturn(1);

        OaSignPlanVersion published = fixture.service.publish(PLAN_ID, SHOP_ID);

        assertThat(published.getScenario()).isEqualTo(expectedScenario);
        assertThat(published.getTemplates()).singleElement()
                .extracting(OaSignPlanVersionTemplate::getTemplateType)
                .isEqualTo(templateType);
    }

    @ParameterizedTest(name = "{0} x {1}")
    @MethodSource("crossScenarioTemplateTypes")
    @DisplayName("所有场景都在发布阶段拒绝跨场景模板")
    void shouldRejectCrossScenarioTemplateTypes(String planScenario, String templateType)
    {
        OaSignPlan plan = validPlan();
        plan.setScenario(planScenario);
        OaSignTemplate template = validTemplate(10L, templateType);
        template.setScenario(OaSignTemplateType.require(templateType).getScenario());
        Fixture fixture = fixture(plan, List.of(binding(template, 20)));

        assertThatThrownBy(() -> fixture.service.publish(PLAN_ID, SHOP_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("方案只能绑定")
                .hasMessageContaining("场景模板类型");
        verify(fixture.mapper, never()).insertPlanVersion(any());
    }

    @Test
    @DisplayName("模板登记场景与类型目录不一致时拒绝发布")
    void shouldRejectCorruptedTemplateScenarioWhenPublishing()
    {
        OaSignTemplate template = validTemplate(10L,
                OaSignTemplateType.ONBOARD_LABOR_CONTRACT);
        template.setScenario("transfer");
        Fixture fixture = fixture(validPlan(), List.of(binding(template, 20)));

        assertThatThrownBy(() -> fixture.service.publish(PLAN_ID, SHOP_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("模板登记场景")
                .hasMessageContaining("模板类型不一致");
        verify(fixture.mapper, never()).insertPlanVersion(any());
    }

    @Test
    @DisplayName("未知方案场景拒绝发布")
    void shouldRejectUnknownPlanScenario()
    {
        OaSignPlan plan = validPlan();
        plan.setScenario("free-form");
        Fixture fixture = fixture(plan, List.of(binding(
                validTemplate(10L, OaSignTemplateType.ONBOARD_LABOR_CONTRACT), 20)));

        assertThatThrownBy(() -> fixture.service.publish(PLAN_ID, SHOP_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不支持的签约场景");
        verify(fixture.mapper, never()).insertPlanVersion(any());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "servicePersonType", "insuranceType" })
    @DisplayName("转正方案拒绝typed快照无法核验的兼容条件")
    void shouldRejectUnsupportedRegularizationCompatibilityFields(String field)
    {
        OaSignPlan plan = validPlan();
        plan.setScenario("regularize");
        if ("servicePersonType".equals(field))
        {
            plan.setServicePersonType("劳务人员");
        }
        else
        {
            plan.setInsuranceType("社保");
        }
        OaSignTemplate template = validTemplate(10L, "REGULARIZE_CONFIRMATION");
        template.setScenario("regularize");
        Fixture fixture = fixture(plan, List.of(binding(template, 20)));

        assertThatThrownBy(() -> fixture.service.publish(PLAN_ID, SHOP_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("转正方案")
                .hasMessageContaining("servicePersonType".equals(field) ? "劳务人员类型" : "保险类型");
        verify(fixture.mapper, never()).insertPlanVersion(any());
    }

    @ParameterizedTest(name = "raw {0}")
    @ValueSource(strings = { "servicePersonType", "insuranceType" })
    @DisplayName("转正方案也拒绝ruleJson中无法核验的非空兼容条件")
    void shouldRejectUnsupportedRawRegularizationCompatibilityFields(String field)
    {
        OaSignPlan plan = validPlan();
        plan.setScenario("regularize");
        plan.setRuleJson("{\"" + field + "\":\"任意值\"}");
        OaSignTemplate template = validTemplate(10L, "REGULARIZE_CONFIRMATION");
        template.setScenario("regularize");
        Fixture fixture = fixture(plan, List.of(binding(template, 20)));

        assertThatThrownBy(() -> fixture.service.publish(PLAN_ID, SHOP_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("转正方案")
                .hasMessageContaining("servicePersonType".equals(field) ? "劳务人员类型" : "保险类型");
        verify(fixture.mapper, never()).insertPlanVersion(any());
    }

    @AfterEach
    void clearSecurityContext()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("发布时冻结完整方案和模板快照并记录发布审计")
    void shouldPublishCompleteImmutableSnapshot()
    {
        OaSignPlan source = validPlan();
        source.setInsuranceType("社保");
        Fixture fixture = fixture(source,
                List.of(binding(validTemplate(10L, "ONBOARD_LABOR_CONTRACT"), 20)));
        SecurityContextHolder.setUserId("7");
        SecurityContextHolder.setUserName("唯一HR");
        when(fixture.mapper.selectNextVersionNo(PLAN_ID)).thenReturn(3);
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.getArgument(0, OaSignPlanVersion.class).setVersionId(501L);
            return 1;
        }).when(fixture.mapper).insertPlanVersion(any());
        when(fixture.mapper.batchInsertPlanVersionTemplates(any())).thenReturn(1);

        OaSignPlanVersion published = fixture.service.publish(PLAN_ID, SHOP_ID);

        assertThat(published.getVersionId()).isEqualTo(501L);
        assertThat(published.getVersionNo()).isEqualTo(3);
        assertThat(published.getScenario()).isEqualTo("onboard");
        assertThat(published.getShopDeptId()).isZero();
        assertThat(published.getLegalEntityId()).isNull();
        assertThat(published.getLegalEntityName()).isNull();
        assertThat(published.getRuleJson()).contains(
                "employmentType", "劳动合同", "insuranceType", "社保", "configuredRule");
        assertThat(published.getDefaultValuesJson()).contains("baseSalary", "5000", "configuredDefault");
        assertThat(published.getSignDeadlineDays()).isEqualTo(7);
        assertThat(published.getReminderPolicyJson()).isEqualTo("{\"daysBefore\":[1,3]}");
        assertThat(published.getAutoSendConditionJson()).isEqualTo("{\"enabled\":false}");
        assertThat(published.getPublishStatus()).isEqualTo("PUBLISHED");
        assertThat(published.getMatchingStatus()).isEqualTo("ENABLED");
        assertThat(published.getPublishedByUserId()).isEqualTo(7L);
        assertThat(published.getPublishedBy()).isEqualTo("唯一HR");
        assertThat(published.getPublishedTime()).isNotNull();
        assertThat(published.getVersionHash()).matches("[0-9a-f]{64}");
        assertThat(published.getTemplates()).singleElement().satisfies(snapshot -> {
            assertThat(snapshot.getTemplateId()).isEqualTo(10L);
            assertThat(snapshot.getTemplateVersion()).isEqualTo("v1");
            assertThat(snapshot.getTemplateType()).isEqualTo("ONBOARD_LABOR_CONTRACT");
            assertThat(snapshot.getSourceFileUrl()).isEqualTo("/profile/labor.docx");
            assertThat(snapshot.getSourceFileHash()).isEqualTo("source-hash");
            assertThat(snapshot.getSortOrder()).isEqualTo(20);
            assertThat(snapshot.getEmployeeSignRequired()).isEqualTo("Y");
            assertThat(snapshot.getCompanySealRequired()).isEqualTo("Y");
            assertThat(snapshot.getSignaturePositionJson()).isEqualTo(
                    "{\"height\":40,\"mode\":\"PLACED\",\"pageNumber\":1,"
                            + "\"width\":120,\"x\":10,\"y\":20}");
            assertThat(snapshot.getCompanySealPositionJson()).isEqualTo(
                    "{\"height\":80,\"mode\":\"PLACED\",\"pageNumber\":1,"
                            + "\"width\":80,\"x\":80,\"y\":20}");
            assertThat(snapshot.getMatchConditionJson()).contains("configuredMatch", "scenario");
        });
    }

    @Test
    @DisplayName("发布拒绝有社保方案绑定A版薪酬口径")
    void shouldRejectPublishingSalaryVersionThatConflictsWithSocialType()
    {
        OaSignPlan plan = validPlan();
        plan.setSocialType("有社保");
        plan.setSalaryVersion("A");
        Fixture fixture = fixture(plan,
                List.of(binding(validTemplate(10L, "ONBOARD_LABOR_CONTRACT"), 20)));

        assertThatThrownBy(() -> fixture.service.publish(PLAN_ID, SHOP_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("社保口径与薪酬版本冲突");
        verify(fixture.mapper, never()).insertPlanVersion(any());
    }

    @Test
    @DisplayName("发布校验将无社保口径规范化并保留唯一A版")
    void shouldNormalizeUninsuredSocialTypeWithDerivedSalaryVersion()
    {
        OaSignPlan plan = validPlan();
        plan.setSocialType("无社保");
        plan.setSalaryVersion("a");
        Fixture fixture = fixture(plan,
                List.of(binding(validTemplate(10L, "ONBOARD_LABOR_CONTRACT"), 20)));

        ReflectionTestUtils.invokeMethod(fixture.service, "validateSourcePlan", plan);

        assertThat(plan.getSocialType()).isEqualTo("SOCIAL_UNINSURED");
        assertThat(plan.getSalaryVersion()).isEqualTo("A");
    }

    @Test
    @DisplayName("提醒策略按天数排序并冻结为唯一规范形式")
    void shouldCanonicalizeExplicitReminderOffsets()
    {
        OaSignPlan plan = validPlan();
        plan.setReminderPolicyJson("{\"daysBefore\":[3,1]}");
        Fixture fixture = fixture(plan,
                List.of(binding(validTemplate(10L, "ONBOARD_LABOR_CONTRACT"), 20)));
        when(fixture.mapper.selectNextVersionNo(PLAN_ID)).thenReturn(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.getArgument(0, OaSignPlanVersion.class).setVersionId(501L);
            return 1;
        }).when(fixture.mapper).insertPlanVersion(any());
        when(fixture.mapper.batchInsertPlanVersionTemplates(any())).thenReturn(1);

        OaSignPlanVersion published = fixture.service.publish(PLAN_ID, SHOP_ID);

        assertThat(published.getReminderPolicyJson()).isEqualTo("{\"daysBefore\":[1,3]}");
    }

    @ParameterizedTest(name = "invalid reminder policy {0}")
    @ValueSource(strings = {
            "{\"unknown\":true}",
            "{\"daysBefore\":[]}",
            "{\"daysBefore\":[0]}",
            "{\"daysBefore\":[31]}",
            "{\"daysBefore\":[1,1]}",
            "{\"daysBefore\":[\"1\"]}",
            "{\"daysBefore\":[1,2,3,4,5,6,7,8,9]}"
    })
    @DisplayName("发布拒绝含糊或越界的提醒策略")
    void shouldRejectAmbiguousReminderPolicy(String reminderPolicy)
    {
        OaSignPlan plan = validPlan();
        plan.setReminderPolicyJson(reminderPolicy);
        Fixture fixture = fixture(plan,
                List.of(binding(validTemplate(10L, "ONBOARD_LABOR_CONTRACT"), 20)));

        assertThatThrownBy(() -> fixture.service.publish(PLAN_ID, SHOP_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("提醒策略");
        verify(fixture.mapper, never()).insertPlanVersion(any());
    }

    @Test
    @DisplayName("发布拒绝超过签署期限的提前提醒")
    void shouldRejectReminderOffsetBeyondFrozenDeadline()
    {
        OaSignPlan plan = validPlan();
        plan.setSignDeadlineDays(7);
        plan.setReminderPolicyJson("{\"daysBefore\":[8]}");
        Fixture fixture = fixture(plan,
                List.of(binding(validTemplate(10L, "ONBOARD_LABOR_CONTRACT"), 20)));

        assertThatThrownBy(() -> fixture.service.publish(PLAN_ID, SHOP_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不能超过签署期限");
        verify(fixture.mapper, never()).insertPlanVersion(any());
    }

    @Test
    @DisplayName("相同规范化内容重复发布返回已有版本且不插入新行")
    void shouldReturnExistingVersionForSameNormalizedContent()
    {
        Fixture fixture = fixture(validPlan(), List.of(binding(validTemplate(10L, "ONBOARD_LABOR_CONTRACT"), 20)));
        OaSignPlanVersion existing = new OaSignPlanVersion();
        existing.setVersionId(501L);
        existing.setPlanId(PLAN_ID);
        existing.setVersionNo(2);
        when(fixture.mapper.selectByPlanIdAndVersionHash(anyLong(), anyString())).thenReturn(existing);
        when(fixture.mapper.selectTemplatesByVersionId(501L)).thenReturn(List.of());

        OaSignPlanVersion published = fixture.service.publish(PLAN_ID, SHOP_ID);

        assertThat(published).isSameAs(existing);
        verify(fixture.mapper, never()).selectNextVersionNo(anyLong());
        verify(fixture.mapper, never()).insertPlanVersion(any());
        verify(fixture.mapper, never()).batchInsertPlanVersionTemplates(any());
        verify(fixture.mapper, never()).disableOtherPublishedMatchingVersions(anyLong(), anyLong());
    }

    @Test
    @DisplayName("新hash版本发布完成后在同一发布链停用同方案旧版本新匹配")
    void shouldDisableOtherPublishedMatchingVersionsAfterPublishingNewHash()
    {
        Fixture fixture = fixture(validPlan(),
                List.of(binding(validTemplate(10L, OaSignTemplateType.ONBOARD_LABOR_CONTRACT), 20)));
        when(fixture.mapper.selectNextVersionNo(PLAN_ID)).thenReturn(3);
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.getArgument(0, OaSignPlanVersion.class).setVersionId(503L);
            return 1;
        }).when(fixture.mapper).insertPlanVersion(any());
        when(fixture.mapper.batchInsertPlanVersionTemplates(any())).thenReturn(1);

        OaSignPlanVersion published = fixture.service.publish(PLAN_ID, SHOP_ID);

        assertThat(published.getVersionId()).isEqualTo(503L);
        InOrder ordered = inOrder(fixture.mapper);
        ordered.verify(fixture.mapper).insertPlanVersion(any());
        ordered.verify(fixture.mapper).batchInsertPlanVersionTemplates(any());
        ordered.verify(fixture.mapper).disableOtherPublishedMatchingVersions(PLAN_ID, 503L);
    }

    @ParameterizedTest(name = "{0} {1} 缺少 {3}")
    @MethodSource("incompleteOnboardPackages")
    @DisplayName("入职方案发布前按ruleJson合同类型和等级段阻断不完整套餐")
    void shouldRejectIncompleteOnboardPackageBeforePublishing(String contractTypeCode,
            String jobGradeBand, List<String> templateTypes, String missingTemplateLabel)
    {
        OaSignPlan plan = validPlan();
        plan.setRuleJson("{\"routeCode\":\"TEST\",\"contractTypeCode\":\""
                + contractTypeCode + "\",\"jobGradeBand\":\"" + jobGradeBand + "\"}");
        Fixture fixture = fixture(plan, bindingsForTypes(templateTypes));

        assertThatThrownBy(() -> fixture.service.publish(PLAN_ID, SHOP_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("入职签约方案缺少必需模板")
                .hasMessageContaining(missingTemplateLabel);
        verify(fixture.mapper, never()).insertPlanVersion(any());
    }

    @Test
    @DisplayName("入职承诺书限制为劳动合同时阻断劳务方案发布")
    void shouldRejectEmploymentRestrictedOnboardCommitment()
    {
        OaSignPlan plan = validPlan();
        plan.setEmploymentType("劳务合同");
        plan.setSocialType("无社保");
        plan.setSalaryVersion(null);
        plan.setRuleJson("{\"routeCode\":\"B3\",\"contractTypeCode\":\"SERVICE_CONTRACT\","
                + "\"jobGradeBand\":\"7-9\",\"servicePersonType\":\"退休返聘\"}");
        OaSignTemplate commitment =
                validTemplate(10L, OaSignTemplateType.ONBOARD_COMMITMENT);
        commitment.setEmploymentType("劳动合同");
        List<OaSignPlanTemplate> bindings = List.of(
                binding(commitment, 10),
                binding(validTemplate(11L, OaSignTemplateType.ONBOARD_SERVICE_CONTRACT), 20),
                binding(validTemplate(12L, OaSignTemplateType.ONBOARD_SERVICE_RECEIPT), 30),
                binding(validTemplate(13L,
                        OaSignTemplateType.ONBOARD_CONFIDENTIAL_NONCOMPETE), 40));
        Fixture fixture = fixture(plan, bindings);

        assertThatThrownBy(() -> fixture.service.publish(PLAN_ID, SHOP_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("入职承诺书必须同时适用于劳动合同和劳务合同")
                .hasMessageContaining("清空用工类型限制");
        verify(fixture.mapper, never()).insertPlanVersion(any());
    }

    @Test
    @DisplayName("入职承诺书不限制用工类型时允许发布完整劳务方案")
    void shouldPublishServiceOnboardPackageWithUniversalCommitment()
    {
        OaSignPlan plan = validPlan();
        plan.setEmploymentType("劳务合同");
        plan.setSocialType("无社保");
        plan.setSalaryVersion(null);
        plan.setRuleJson("{\"routeCode\":\"B3\",\"contractTypeCode\":\"SERVICE_CONTRACT\","
                + "\"jobGradeBand\":\"7-9\",\"servicePersonType\":\"退休返聘\"}");
        List<OaSignPlanTemplate> bindings = bindingsForTypes(List.of(
                OaSignTemplateType.ONBOARD_COMMITMENT,
                OaSignTemplateType.ONBOARD_SERVICE_CONTRACT,
                OaSignTemplateType.ONBOARD_SERVICE_RECEIPT,
                OaSignTemplateType.ONBOARD_CONFIDENTIAL_NONCOMPETE));
        Fixture fixture = fixture(plan, bindings);
        when(fixture.mapper.selectNextVersionNo(PLAN_ID)).thenReturn(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.getArgument(0, OaSignPlanVersion.class).setVersionId(501L);
            return 1;
        }).when(fixture.mapper).insertPlanVersion(any());
        when(fixture.mapper.batchInsertPlanVersionTemplates(any()))
                .thenReturn(bindings.size());

        OaSignPlanVersion published = fixture.service.publish(PLAN_ID, SHOP_ID);

        assertThat(published.getTemplates())
                .extracting(OaSignPlanVersionTemplate::getTemplateType)
                .containsExactlyInAnyOrder(
                        OaSignTemplateType.ONBOARD_COMMITMENT,
                        OaSignTemplateType.ONBOARD_SERVICE_CONTRACT,
                        OaSignTemplateType.ONBOARD_SERVICE_RECEIPT,
                        OaSignTemplateType.ONBOARD_CONFIDENTIAL_NONCOMPETE);
    }

    @Test
    @DisplayName("入职路由规则只配置合同类型时不允许绕过套餐校验")
    void shouldRejectPartialOnboardRoutingRule()
    {
        OaSignPlan plan = validPlan();
        plan.setRuleJson("{\"contractTypeCode\":\"LABOR_CONTRACT\"}");
        Fixture fixture = fixture(plan, bindingsForTypes(List.of(
                OaSignTemplateType.ONBOARD_COMMITMENT,
                OaSignTemplateType.ONBOARD_LABOR_CONTRACT,
                OaSignTemplateType.ONBOARD_HANDBOOK_RECEIPT,
                OaSignTemplateType.ONBOARD_SALARY_CONFIRM)));

        assertThatThrownBy(() -> fixture.service.publish(PLAN_ID, SHOP_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("必须明确合同类型和等级段");
        verify(fixture.mapper, never()).insertPlanVersion(any());
    }

    @Test
    @DisplayName("页面旧字段配置的七级及以上入职套餐也必须包含保密竞业")
    void shouldValidateLegacyUiRoutingFieldsBeforePublishing()
    {
        OaSignPlan plan = validPlan();
        plan.setPostLevelSnapshot("7-8");
        Fixture fixture = fixture(plan, bindingsForTypes(List.of(
                OaSignTemplateType.ONBOARD_COMMITMENT,
                OaSignTemplateType.ONBOARD_LABOR_CONTRACT,
                OaSignTemplateType.ONBOARD_HANDBOOK_RECEIPT,
                OaSignTemplateType.ONBOARD_SALARY_CONFIRM)));

        assertThatThrownBy(() -> fixture.service.publish(PLAN_ID, SHOP_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("入职签约方案缺少必需模板")
                .hasMessageContaining("保密与竞业限制协议");
        verify(fixture.mapper, never()).insertPlanVersion(any());
    }

    @Test
    @DisplayName("七级及以上劳动入职套餐完整时允许发布")
    void shouldPublishCompleteHighGradeLaborOnboardPackage()
    {
        OaSignPlan plan = validPlan();
        plan.setRuleJson("{\"routeCode\":\"A3\",\"contractTypeCode\":\"LABOR_CONTRACT\","
                + "\"jobGradeBand\":\"7-8\"}");
        List<OaSignPlanTemplate> bindings = bindingsForTypes(List.of(
                OaSignTemplateType.ONBOARD_COMMITMENT,
                OaSignTemplateType.ONBOARD_LABOR_CONTRACT,
                OaSignTemplateType.ONBOARD_HANDBOOK_RECEIPT,
                OaSignTemplateType.ONBOARD_SALARY_CONFIRM,
                OaSignTemplateType.ONBOARD_CONFIDENTIAL_NONCOMPETE));
        Fixture fixture = fixture(plan, bindings);
        when(fixture.mapper.selectNextVersionNo(PLAN_ID)).thenReturn(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.getArgument(0, OaSignPlanVersion.class).setVersionId(501L);
            return 1;
        }).when(fixture.mapper).insertPlanVersion(any());
        when(fixture.mapper.batchInsertPlanVersionTemplates(any())).thenReturn(bindings.size());

        OaSignPlanVersion published = fixture.service.publish(PLAN_ID, SHOP_ID);

        assertThat(published.getTemplates()).extracting(OaSignPlanVersionTemplate::getTemplateType)
                .containsExactlyInAnyOrder(
                        OaSignTemplateType.ONBOARD_COMMITMENT,
                        OaSignTemplateType.ONBOARD_LABOR_CONTRACT,
                        OaSignTemplateType.ONBOARD_HANDBOOK_RECEIPT,
                        OaSignTemplateType.ONBOARD_SALARY_CONFIRM,
                        OaSignTemplateType.ONBOARD_CONFIDENTIAL_NONCOMPETE);
    }

    @Test
    @DisplayName("方案字段和模板顺序规范化后生成稳定versionHash")
    void shouldHashStableFieldsInDeterministicOrder()
    {
        OaSignPlan plan = validPlan();
        OaSignTemplate labor = validTemplate(10L, "ONBOARD_LABOR_CONTRACT");
        OaSignTemplate handbook = validTemplate(11L, "ONBOARD_HANDBOOK");
        handbook.setFileUrl("/profile/handbook.docx");
        handbook.setFileHash("handbook-hash");
        Fixture fixture = fixture(plan, List.of(binding(handbook, 30), binding(labor, 20)));
        when(fixture.documentService.calculateFileUrlSha256("/profile/handbook.docx"))
                .thenReturn("handbook-hash");
        when(fixture.mapper.selectNextVersionNo(PLAN_ID)).thenReturn(1, 2);
        org.mockito.Mockito.doAnswer(invocation -> {
            OaSignPlanVersion value = invocation.getArgument(0);
            value.setVersionId((long) value.getVersionNo());
            return 1;
        }).when(fixture.mapper).insertPlanVersion(any());
        when(fixture.mapper.batchInsertPlanVersionTemplates(any())).thenReturn(2);

        fixture.service.publish(PLAN_ID, SHOP_ID);
        plan.setRuleJson("{\"configuredRule\":true,\"a\":1}");
        plan.setDefaultValuesJson("{\"configuredDefault\":true,\"a\":1}");
        labor.setSignaturePositionJson(
                "{\"width\":120,\"mode\":\"PLACED\",\"x\":10,\"height\":40,"
                        + "\"pageNumber\":1,\"y\":20}");
        fixture.bindings = List.of(binding(labor, 20), binding(handbook, 30));
        fixture.service.publish(PLAN_ID, SHOP_ID);

        ArgumentCaptor<OaSignPlanVersion> captor = ArgumentCaptor.forClass(OaSignPlanVersion.class);
        verify(fixture.mapper, org.mockito.Mockito.times(2)).insertPlanVersion(captor.capture());
        assertThat(captor.getAllValues()).extracting(OaSignPlanVersion::getVersionHash)
                .containsExactly(captor.getAllValues().get(0).getVersionHash(),
                        captor.getAllValues().get(0).getVersionHash());
    }

    @Test
    @DisplayName("发布先锁方案再锁模板并在锁内查重分配版本号")
    void shouldUsePlanAndTemplateLocksBeforeDeduplicationAndVersionAllocation()
    {
        Fixture fixture = fixture(validPlan(), List.of(binding(validTemplate(10L, "ONBOARD_LABOR_CONTRACT"), 20)));
        when(fixture.mapper.selectNextVersionNo(PLAN_ID)).thenReturn(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.getArgument(0, OaSignPlanVersion.class).setVersionId(501L);
            return 1;
        }).when(fixture.mapper).insertPlanVersion(any());
        when(fixture.mapper.batchInsertPlanVersionTemplates(any())).thenReturn(1);

        fixture.service.publish(PLAN_ID, SHOP_ID);

        InOrder ordered = inOrder(fixture.mapper);
        ordered.verify(fixture.mapper).lockPlanById(PLAN_ID);
        ordered.verify(fixture.mapper).lockPlanTemplateBindings(PLAN_ID);
        ordered.verify(fixture.mapper).lockTemplatesByIds(List.of(10L));
        ordered.verify(fixture.mapper).selectByPlanIdAndVersionHash(anyLong(), anyString());
        ordered.verify(fixture.mapper).selectNextVersionNo(PLAN_ID);
        ordered.verify(fixture.mapper).insertPlanVersion(any());
        ordered.verify(fixture.mapper).batchInsertPlanVersionTemplates(any());
    }

    @Test
    @DisplayName("共享模板始终按全局模板ID升序加锁而不受方案排序影响")
    void shouldLockSharedTemplatesInGlobalTemplateIdOrder()
    {
        OaSignTemplate labor = validTemplate(10L, "ONBOARD_LABOR_CONTRACT");
        OaSignTemplate handbook = validTemplate(11L, "ONBOARD_HANDBOOK");
        handbook.setFileUrl("/profile/handbook.docx");
        handbook.setFileHash("handbook-hash");
        Fixture fixture = fixture(validPlan(), List.of(binding(handbook, 10), binding(labor, 20)));
        when(fixture.documentService.calculateFileUrlSha256("/profile/handbook.docx"))
                .thenReturn("handbook-hash");
        when(fixture.mapper.selectNextVersionNo(PLAN_ID)).thenReturn(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.getArgument(0, OaSignPlanVersion.class).setVersionId(501L);
            return 1;
        }).when(fixture.mapper).insertPlanVersion(any());
        when(fixture.mapper.batchInsertPlanVersionTemplates(any())).thenReturn(2);

        fixture.service.publish(PLAN_ID, SHOP_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> ids = ArgumentCaptor.forClass(List.class);
        verify(fixture.mapper).lockTemplatesByIds(ids.capture());
        assertThat(ids.getValue()).containsExactly(10L, 11L);
    }

    @Test
    @DisplayName("模板文件不存在时拒绝发布")
    void shouldRejectMissingTemplateFile()
    {
        Fixture fixture = fixture(validPlan(), List.of(binding(validTemplate(10L, "ONBOARD_LABOR_CONTRACT"), 20)));
        when(fixture.documentService.calculateFileUrlSha256("/profile/labor.docx")).thenReturn(null);

        assertThatThrownBy(() -> fixture.service.publish(PLAN_ID, SHOP_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("模板文件不存在");
    }

    @Test
    @DisplayName("模板当前文件校验值与已保存校验值不一致时拒绝发布")
    void shouldRejectTemplateHashMismatch()
    {
        Fixture fixture = fixture(validPlan(), List.of(binding(validTemplate(10L, "ONBOARD_LABOR_CONTRACT"), 20)));
        when(fixture.documentService.calculateFileUrlSha256("/profile/labor.docx")).thenReturn("changed-hash");

        assertThatThrownBy(() -> fixture.service.publish(PLAN_ID, SHOP_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("校验值不一致");
    }

    @Test
    @DisplayName("模板缺少类型要求的必需占位符时拒绝发布")
    void shouldRejectMissingRequiredPlaceholders()
    {
        Fixture fixture = fixture(validPlan(), List.of(binding(validTemplate(10L, "ONBOARD_LABOR_CONTRACT"), 20)));
        org.mockito.Mockito.doThrow(new ServiceException("模板缺少占位符：${employeeName}"))
                .when(fixture.documentService)
                .assertTemplateContainsRequiredPlaceholders("ONBOARD_LABOR_CONTRACT", "/profile/labor.docx");

        assertThatThrownBy(() -> fixture.service.publish(PLAN_ID, SHOP_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("缺少占位符");
    }

    @Test
    @DisplayName("方案发布时公司尚未确定，固定公司模板在最终选公司时再校验")
    void shouldDeferTemplateLegalEntityCheckUntilCompanyFinalization()
    {
        Fixture fixture = fixture(validPlan(),
                List.of(binding(validTemplate(10L, "ONBOARD_LABOR_CONTRACT"), 20)));
        org.mockito.Mockito.doThrow(new ServiceException("模板写死了其他法律主体"))
                .when(fixture.documentService)
                .assertTemplateLegalEntityCompatible("ONBOARD_LABOR_CONTRACT",
                        "/profile/labor.docx", "星河餐饮有限公司");
        when(fixture.mapper.selectNextVersionNo(PLAN_ID)).thenReturn(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.getArgument(0, OaSignPlanVersion.class).setVersionId(501L);
            return 1;
        }).when(fixture.mapper).insertPlanVersion(any());
        when(fixture.mapper.batchInsertPlanVersionTemplates(any())).thenReturn(1);

        OaSignPlanVersion published = fixture.service.publish(PLAN_ID, SHOP_ID);

        assertThat(published.getVersionId()).isEqualTo(501L);
        verify(fixture.documentService).assertTemplateLegalEntityCompatible(
                "ONBOARD_LABOR_CONTRACT", "/profile/labor.docx", null);
    }

    @Test
    @DisplayName("方案没有模板时拒绝发布")
    void shouldRejectPlanWithoutTemplates()
    {
        Fixture fixture = fixture(validPlan(), List.of());

        assertThatThrownBy(() -> fixture.service.publish(PLAN_ID, SHOP_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("至少需要一个模板");
    }

    @Test
    @DisplayName("方案绑定重复模板类型时拒绝发布")
    void shouldRejectDuplicateTemplateTypes()
    {
        OaSignTemplate first = validTemplate(10L, "ONBOARD_LABOR_CONTRACT");
        OaSignTemplate second = validTemplate(11L, "ONBOARD_LABOR_CONTRACT");
        second.setFileUrl("/profile/labor-v2.docx");
        second.setFileHash("source-hash-v2");
        Fixture fixture = fixture(validPlan(), List.of(binding(first, 20), binding(second, 30)));

        assertThatThrownBy(() -> fixture.service.publish(PLAN_ID, SHOP_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("模板类型不能重复");
    }

    @Test
    @DisplayName("模板未配置员工签名或企业章策略时拒绝发布")
    void shouldRejectTemplateWithoutSigningStrategy()
    {
        OaSignTemplate template = validTemplate(10L, "ONBOARD_LABOR_CONTRACT");
        template.setEmployeeSignRequired("N");
        template.setSignaturePositionJson(null);
        template.setCompanySealPositionJson(null);
        template.setCompanySealRequired("N");
        Fixture fixture = fixture(validPlan(), List.of(binding(template, 20)));

        assertThatThrownBy(() -> fixture.service.publish(PLAN_ID, SHOP_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("签名策略");
    }

    @Test
    @DisplayName("员工需要签署但未配置坐标时冻结为追加确认页策略")
    void shouldFreezeExistingAppendedConfirmationPageStrategy()
    {
        OaSignTemplate template = validTemplate(10L, "ONBOARD_LABOR_CONTRACT");
        template.setSignaturePositionJson(null);
        Fixture fixture = fixture(validPlan(), List.of(binding(template, 20)));
        when(fixture.mapper.selectNextVersionNo(PLAN_ID)).thenReturn(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.getArgument(0, OaSignPlanVersion.class).setVersionId(501L);
            return 1;
        }).when(fixture.mapper).insertPlanVersion(any());
        when(fixture.mapper.batchInsertPlanVersionTemplates(any())).thenReturn(1);

        OaSignPlanVersion published = fixture.service.publish(PLAN_ID, SHOP_ID);

        assertThat(published.getTemplates()).singleElement()
                .extracting(OaSignPlanVersionTemplate::getSignaturePositionJson)
                .isEqualTo("{\"mode\":\"APPENDED_CONFIRMATION_PAGE\"}");
    }

    @Test
    @DisplayName("发布时把显式企业章追加确认页冻结到方案版本")
    void shouldFreezeAppendedCompanySealInPublishedVersion()
    {
        OaSignTemplate template = validTemplate(10L, "ONBOARD_LABOR_CONTRACT");
        template.setSignaturePositionJson(null);
        template.setCompanySealPositionJson("{ \"mode\" : \"APPENDED_CONFIRMATION_PAGE\" }");
        Fixture fixture = fixture(validPlan(), List.of(binding(template, 20)));
        when(fixture.mapper.selectNextVersionNo(PLAN_ID)).thenReturn(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.getArgument(0, OaSignPlanVersion.class).setVersionId(501L);
            return 1;
        }).when(fixture.mapper).insertPlanVersion(any());
        when(fixture.mapper.batchInsertPlanVersionTemplates(any())).thenReturn(1);

        OaSignPlanVersion published = fixture.service.publish(PLAN_ID, SHOP_ID);

        assertThat(published.getTemplates()).singleElement().satisfies(snapshot -> {
            assertThat(snapshot.getSignaturePositionJson())
                    .isEqualTo("{\"mode\":\"APPENDED_CONFIRMATION_PAGE\"}");
            assertThat(snapshot.getCompanySealRequired()).isEqualTo("Y");
            assertThat(snapshot.getCompanySealPositionJson())
                    .isEqualTo("{\"mode\":\"APPENDED_CONFIRMATION_PAGE\"}");
        });
    }

    @Test
    @DisplayName("需要企业章时禁止用空值默认为追加策略")
    void shouldRejectBlankCompanySealPolicyWhenPublishing()
    {
        OaSignTemplate template = validTemplate(10L, "ONBOARD_LABOR_CONTRACT");
        template.setCompanySealPositionJson("  ");
        Fixture fixture = fixture(validPlan(), List.of(binding(template, 20)));

        assertThatThrownBy(() -> fixture.service.publish(PLAN_ID, SHOP_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("需要盖章的模板缺少企业章定位");
        verify(fixture.mapper, never()).insertPlanVersion(any());
    }

    @Test
    @DisplayName("显式员工签名坐标缺字段或数值非法时拒绝发布")
    void shouldRejectInvalidSignaturePlacement()
    {
        OaSignTemplate template = validTemplate(10L, "ONBOARD_LABOR_CONTRACT");
        template.setSignaturePositionJson(
                "{\"mode\":\"PLACED\",\"pageNumber\":1,\"x\":10,\"y\":20,\"width\":120}");
        Fixture fixture = fixture(validPlan(), List.of(binding(template, 20)));

        assertThatThrownBy(() -> fixture.service.publish(PLAN_ID, SHOP_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("员工签名定位")
                .hasMessageContaining("height");
    }

    @Test
    @DisplayName("显式企业章坐标页码和宽高非法时拒绝发布")
    void shouldRejectInvalidCompanySealPlacement()
    {
        OaSignTemplate template = validTemplate(10L, "ONBOARD_LABOR_CONTRACT");
        template.setCompanySealPositionJson(
                "{\"mode\":\"PLACED\",\"pageNumber\":0,\"x\":80,\"y\":20,"
                        + "\"width\":-1,\"height\":80}");
        Fixture fixture = fixture(validPlan(), List.of(binding(template, 20)));

        assertThatThrownBy(() -> fixture.service.publish(PLAN_ID, SHOP_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("企业章定位");
    }

    @Test
    @DisplayName("发布时把最后一页签名和企业章定位冻结到方案版本")
    void shouldFreezeLastPagePlacementInPublishedVersion()
    {
        OaSignTemplate template = validTemplate(10L, "ONBOARD_LABOR_CONTRACT");
        template.setSignaturePositionJson(
                "{\"mode\":\"LAST_PAGE\",\"x\":10,\"y\":20,\"width\":120,\"height\":40}");
        template.setCompanySealPositionJson(
                "{\"width\":80,\"height\":80,\"mode\":\"LAST_PAGE\",\"y\":20,\"x\":80}");
        Fixture fixture = fixture(validPlan(), List.of(binding(template, 20)));
        when(fixture.mapper.selectNextVersionNo(PLAN_ID)).thenReturn(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.getArgument(0, OaSignPlanVersion.class).setVersionId(501L);
            return 1;
        }).when(fixture.mapper).insertPlanVersion(any());
        when(fixture.mapper.batchInsertPlanVersionTemplates(any())).thenReturn(1);

        OaSignPlanVersion published = fixture.service.publish(PLAN_ID, SHOP_ID);

        assertThat(published.getTemplates()).singleElement().satisfies(snapshot -> {
            assertThat(snapshot.getSignaturePositionJson()).isEqualTo(
                    "{\"height\":40,\"mode\":\"LAST_PAGE\",\"width\":120,\"x\":10,\"y\":20}");
            assertThat(snapshot.getCompanySealPositionJson()).isEqualTo(
                    "{\"height\":80,\"mode\":\"LAST_PAGE\",\"width\":80,\"x\":80,\"y\":20}");
        });
    }

    @Test
    @DisplayName("最后一页快照拒绝隐式页码和其他未知字段")
    void shouldRejectUnknownLastPagePlacementFields()
    {
        OaSignTemplate template = validTemplate(10L, "ONBOARD_LABOR_CONTRACT");
        template.setCompanySealPositionJson(
                "{\"mode\":\"LAST_PAGE\",\"pageNumber\":1,\"x\":80,\"y\":20,"
                        + "\"width\":80,\"height\":80}");
        Fixture fixture = fixture(validPlan(), List.of(binding(template, 20)));

        assertThatThrownBy(() -> fixture.service.publish(PLAN_ID, SHOP_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("企业章定位")
                .hasMessageContaining("未知字段：pageNumber");
    }

    @Test
    @DisplayName("发布时拒绝float下溢为零或上溢为无穷的最后一页坐标")
    void shouldRejectLastPageCoordinatesOutsideFloatExecutionDomain()
    {
        for (String field : new String[] { "x", "y", "width", "height" })
        {
            for (String value : new String[] { "1e-50", "1e39" })
            {
                OaSignTemplate template = validTemplate(10L, "ONBOARD_LABOR_CONTRACT");
                template.setCompanySealPositionJson(lastPagePlacement(field, value));
                Fixture fixture = fixture(validPlan(), List.of(binding(template, 20)));

                assertThatThrownBy(() -> fixture.service.publish(PLAN_ID, SHOP_ID))
                        .as("%s=%s应在发布时被拒绝", field, value)
                        .isInstanceOf(ServiceException.class)
                        .hasMessageContaining("企业章定位")
                        .hasMessageContaining(field);
            }
        }
    }

    @Test
    @DisplayName("方案无需预绑公司且不能用门店编号代替公司")
    void shouldPublishWithoutLegalEntityAndNeverFallBackToShop()
    {
        OaSignPlan plan = validPlan();
        plan.setLegalEntityId(null);
        plan.setLegalEntityName(null);
        Fixture fixture = fixture(plan, List.of(binding(validTemplate(10L, "ONBOARD_LABOR_CONTRACT"), 20)));
        when(fixture.mapper.selectNextVersionNo(PLAN_ID)).thenReturn(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.getArgument(0, OaSignPlanVersion.class).setVersionId(501L);
            return 1;
        }).when(fixture.mapper).insertPlanVersion(any());
        when(fixture.mapper.batchInsertPlanVersionTemplates(any())).thenReturn(1);

        OaSignPlanVersion published = fixture.service.publish(PLAN_ID, SHOP_ID);

        assertThat(published.getLegalEntityId()).isNull();
        assertThat(published.getLegalEntityName()).isNull();
        assertThat(published.getShopDeptId()).isZero();
    }

    @Test
    @DisplayName("发布不依赖所选门店但拒绝历史组织方案")
    void shouldIgnoreSelectedShopAndRejectNonGlobalSourcePlan()
    {
        Fixture fixture = fixture(validPlan(), List.of(binding(validTemplate(10L, "ONBOARD_LABOR_CONTRACT"), 20)));
        fixture.plan.setShopDeptId(1172L);
        assertThatThrownBy(() -> fixture.service.publish(PLAN_ID, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不存在或无权限");
    }

    @Test
    @DisplayName("已发布版本不能删除但仍可停用新匹配")
    void shouldProtectTaskReferencedVersionAndAllowDisablingNewMatching()
    {
        Fixture fixture = fixture(validPlan(), List.of(binding(validTemplate(10L, "ONBOARD_LABOR_CONTRACT"), 20)));
        OaSignPlanVersion version = new OaSignPlanVersion();
        version.setVersionId(501L);
        version.setShopDeptId(0L);
        version.setMatchingStatus("ENABLED");
        when(fixture.mapper.lockPlanVersionById(501L)).thenReturn(version);
        when(fixture.mapper.selectPlanVersionById(501L)).thenReturn(version);
        when(fixture.mapper.disableForNewMatching(501L)).thenReturn(1);

        assertThatThrownBy(() -> fixture.service.deleteUnreferencedVersion(501L, SHOP_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不可物理删除");
        OaSignPlanVersion disabled = fixture.service.disableForNewMatching(501L, SHOP_ID);

        assertThat(disabled.getMatchingStatus()).isEqualTo("DISABLED");
    }

    @Test
    @DisplayName("发布版本不因当前引用类型不同而开放物理删除")
    void shouldProtectPackageReferencedVersion()
    {
        Fixture fixture = fixture(validPlan(), List.of(binding(validTemplate(10L, "ONBOARD_LABOR_CONTRACT"), 20)));
        OaSignPlanVersion version = new OaSignPlanVersion();
        version.setVersionId(501L);
        version.setShopDeptId(0L);
        when(fixture.mapper.lockPlanVersionById(501L)).thenReturn(version);
        assertThatThrownBy(() -> fixture.service.deleteUnreferencedVersion(501L, SHOP_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不可物理删除");
    }

    @Test
    @DisplayName("已发布版本即使尚未被引用也禁止物理删除")
    void shouldRejectPhysicalDeletionEvenWhenVersionIsUnreferenced()
    {
        Fixture fixture = fixture(validPlan(), List.of(binding(validTemplate(10L, "ONBOARD_LABOR_CONTRACT"), 20)));
        OaSignPlanVersion version = new OaSignPlanVersion();
        version.setVersionId(501L);
        version.setShopDeptId(0L);
        when(fixture.mapper.lockPlanVersionById(501L)).thenReturn(version);
        assertThatThrownBy(() -> fixture.service.deleteUnreferencedVersion(501L, SHOP_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不可物理删除");

        verify(fixture.mapper).lockPlanVersionById(501L);
    }

    private Fixture fixture(OaSignPlan plan, List<OaSignPlanTemplate> bindings)
    {
        SecurityContextHolder.setUserId("7");
        SecurityContextHolder.setUserName("唯一HR");
        OaSignPlanVersionMapper mapper = mock(OaSignPlanVersionMapper.class);
        OaSignDocumentService documentService = mock(OaSignDocumentService.class);
        OaSignPlanVersionServiceImpl service = new OaSignPlanVersionServiceImpl();
        ReflectionTestUtils.setField(service, "versionMapper", mapper);
        ReflectionTestUtils.setField(service, "documentService", documentService);
        ObjectMapper objectMapper = new ObjectMapper();
        ReflectionTestUtils.setField(service, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(service, "versionFingerprint",
                new OaSignPlanVersionFingerprint());
        Fixture fixture = new Fixture(service, mapper, documentService, plan,
                new ArrayList<>(bindings));
        when(mapper.lockPlanById(PLAN_ID)).thenAnswer(invocation -> fixture.plan);
        when(mapper.lockPlanTemplateBindings(PLAN_ID)).thenAnswer(invocation -> fixture.bindings);
        when(mapper.lockTemplatesByIds(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<Long> templateIds = invocation.getArgument(0, List.class);
            return fixture.bindings.stream()
                    .map(OaSignPlanTemplate::getTemplate)
                    .filter(java.util.Objects::nonNull)
                    .filter(template -> templateIds.contains(template.getTemplateId()))
                    .sorted(java.util.Comparator.comparing(OaSignTemplate::getTemplateId))
                    .toList();
        });
        when(documentService.calculateFileUrlSha256(anyString())).thenAnswer(invocation -> {
            String url = invocation.getArgument(0);
            return fixture.bindings.stream()
                    .map(OaSignPlanTemplate::getTemplate)
                    .filter(template -> url.equals(template.getFileUrl()))
                    .map(OaSignTemplate::getFileHash)
                    .findFirst()
                    .orElse(null);
        });
        return fixture;
    }

    private OaSignPlan validPlan()
    {
        OaSignPlan plan = new OaSignPlan();
        plan.setPlanId(PLAN_ID);
        plan.setPlanName("入职劳动合同方案");
        plan.setScenario("onboard");
        plan.setShopDeptId(0L);
        plan.setLegalEntityId(9001L);
        plan.setLegalEntityName("星河餐饮有限公司");
        plan.setEmploymentType("劳动合同");
        plan.setSocialType("有社保");
        plan.setSalaryVersion("B");
        plan.setBaseSalary(new java.math.BigDecimal("5000.00"));
        plan.setRuleJson("{\"a\":1,\"configuredRule\":true}");
        plan.setDefaultValuesJson("{\"a\":1,\"configuredDefault\":true}");
        plan.setSignDeadlineDays(7);
        plan.setReminderPolicyJson("{\"daysBefore\":[1,3]}");
        plan.setAutoSendConditionJson("{\"enabled\":false}");
        plan.setStatus("0");
        return plan;
    }

    private String lastPagePlacement(String overriddenField, String value)
    {
        return "{\"mode\":\"LAST_PAGE\",\"x\":"
                + ("x".equals(overriddenField) ? value : "10")
                + ",\"y\":" + ("y".equals(overriddenField) ? value : "20")
                + ",\"width\":" + ("width".equals(overriddenField) ? value : "120")
                + ",\"height\":" + ("height".equals(overriddenField) ? value : "40")
                + "}";
    }

    private OaSignTemplate validTemplate(Long templateId, String templateType)
    {
        OaSignTemplate template = new OaSignTemplate();
        template.setTemplateId(templateId);
        template.setTemplateName(templateType);
        template.setTemplateVersion("v1");
        template.setTemplateType(templateType);
        if (OaSignTemplateType.ONBOARD_SALARY_CONFIRM.equals(templateType))
        {
            template.setSocialType("有社保");
            template.setSalaryVersion("B");
        }
        template.setScenario("onboard");
        template.setFileUrl("/profile/labor.docx");
        template.setFileHash("source-hash");
        template.setRequiredPlaceholders("employeeName");
        template.setEmployeeSignRequired("Y");
        template.setCompanySealRequired("Y");
        template.setSignaturePositionJson(
                "{\"mode\":\"PLACED\",\"x\":10,\"pageNumber\":1,\"y\":20,"
                        + "\"width\":120,\"height\":40}");
        template.setCompanySealPositionJson(
                "{\"mode\":\"PLACED\",\"x\":80,\"pageNumber\":1,\"y\":20,"
                        + "\"width\":80,\"height\":80}");
        template.setMatchConditionJson("{\"configuredMatch\":true}");
        template.setStatus("0");
        return template;
    }

    private OaSignPlanTemplate binding(OaSignTemplate template, int sortOrder)
    {
        OaSignPlanTemplate binding = new OaSignPlanTemplate();
        binding.setPlanId(PLAN_ID);
        binding.setTemplateId(template.getTemplateId());
        binding.setTemplateType(template.getTemplateType());
        binding.setSortOrder(sortOrder);
        binding.setTemplate(template);
        return binding;
    }

    private List<OaSignPlanTemplate> bindingsForTypes(List<String> templateTypes)
    {
        List<OaSignPlanTemplate> bindings = new ArrayList<>();
        long templateId = 10L;
        int sortOrder = 10;
        for (String templateType : templateTypes)
        {
            bindings.add(binding(validTemplate(templateId++, templateType), sortOrder));
            sortOrder += 10;
        }
        return bindings;
    }

    private static Stream<Arguments> incompleteOnboardPackages()
    {
        return Stream.of(
                Arguments.of("LABOR_CONTRACT", "2-4",
                        List.of(OaSignTemplateType.ONBOARD_LABOR_CONTRACT), "入职承诺书"),
                Arguments.of("SERVICE_CONTRACT", "5-6",
                        List.of(OaSignTemplateType.ONBOARD_COMMITMENT,
                                OaSignTemplateType.ONBOARD_SERVICE_CONTRACT),
                        "劳务合同签收单"),
                Arguments.of("LABOR_CONTRACT", "7-8",
                        List.of(OaSignTemplateType.ONBOARD_COMMITMENT,
                                OaSignTemplateType.ONBOARD_LABOR_CONTRACT,
                                OaSignTemplateType.ONBOARD_HANDBOOK_RECEIPT,
                                OaSignTemplateType.ONBOARD_SALARY_CONFIRM),
                        "保密与竞业限制协议"),
                Arguments.of("SERVICE_CONTRACT", "7-9",
                        List.of(OaSignTemplateType.ONBOARD_COMMITMENT,
                                OaSignTemplateType.ONBOARD_SERVICE_CONTRACT,
                                OaSignTemplateType.ONBOARD_SERVICE_RECEIPT),
                        "保密与竞业限制协议"));
    }

    private static Stream<Arguments> scenarioTemplateTypes()
    {
        return Stream.of(
                Arguments.of("ONBOARD", OaSignTemplateType.ONBOARD_LABOR_CONTRACT, "onboard"),
                Arguments.of("regularize", OaSignTemplateType.REGULARIZE_CONFIRMATION, "regularize"),
                Arguments.of("TRANSFER", OaSignTemplateType.TRANSFER_CONFIRMATION, "transfer"),
                Arguments.of("offboard", OaSignTemplateType.OFFBOARD_CONFIRMATION, "offboard"),
                Arguments.of("renew", OaSignTemplateType.RENEWAL_LABOR_CONTRACT, "renewal"));
    }

    private static Stream<Arguments> crossScenarioTemplateTypes()
    {
        return Stream.of(
                Arguments.of("onboard", OaSignTemplateType.TRANSFER_CONFIRMATION),
                Arguments.of("regularize", OaSignTemplateType.ONBOARD_LABOR_CONTRACT),
                Arguments.of("transfer", OaSignTemplateType.OFFBOARD_CONFIRMATION),
                Arguments.of("offboard", OaSignTemplateType.REGULARIZE_CONFIRMATION),
                Arguments.of("renewal", OaSignTemplateType.ONBOARD_LABOR_CONTRACT));
    }

    private static final class Fixture
    {
        private final OaSignPlanVersionServiceImpl service;
        private final OaSignPlanVersionMapper mapper;
        private final OaSignDocumentService documentService;
        private OaSignPlan plan;
        private List<OaSignPlanTemplate> bindings;

        private Fixture(OaSignPlanVersionServiceImpl service, OaSignPlanVersionMapper mapper,
                OaSignDocumentService documentService, OaSignPlan plan,
                List<OaSignPlanTemplate> bindings)
        {
            this.service = service;
            this.mapper = mapper;
            this.documentService = documentService;
            this.plan = plan;
            this.bindings = bindings;
        }
    }
}
