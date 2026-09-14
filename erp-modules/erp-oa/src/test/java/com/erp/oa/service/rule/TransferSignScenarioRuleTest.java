package com.erp.oa.service.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.api.domain.HrEmployeeSigningSnapshot;
import com.erp.oa.api.domain.HrSignBusinessEvent;
import com.erp.oa.constant.OaSignTemplateType;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPlanVersion;
import com.erp.oa.domain.OaSignPlanVersionTemplate;
import com.erp.oa.domain.vo.OaSignDraftDecision;
import com.erp.oa.mapper.OaSignPlanVersionMapper;

@DisplayName("调岗签约场景规则")
class TransferSignScenarioRuleTest
{
    private OaSignPlanVersionMapper versionMapper;
    private TransferSignScenarioRule rule;

    @BeforeEach
    void setUp()
    {
        versionMapper = mock(OaSignPlanVersionMapper.class);
        rule = new TransferSignScenarioRule(versionMapper,
                JsonMapper.builder().findAndAddModules().build());
    }

    @Test
    @DisplayName("调岗事件使用固定action版本去重键")
    void shouldUseStableTransferDedupeKey()
    {
        assertThat(rule.supports(" transfer ")).isTrue();
        assertThat(rule.dedupeKey(event(today(), false)))
                .isEqualTo("TRANSFER:9:801:1");

        HrSignBusinessEvent forged = event(today(), false);
        forged.getAttributes().put("sourceActionVersion", 2L);
        assertThatThrownBy(() -> rule.dedupeKey(forged))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("调岗事件不一致")
                .hasMessageContaining("SOURCE_ACTION_VERSION_MISMATCH");
    }

    @Test
    @DisplayName("当天生效且有员工确认材料时创建冻结调岗差异和业务日期的草稿")
    void shouldCreateTodayTransferDraftWithFrozenDifferences()
    {
        stubPublishedPlan("{\"organizationChanged\":true}", 301L, true);

        OaSignDraftDecision decision = rule.decide(event(today(), false));

        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.CREATE_DRAFT);
        assertThat(decision.getPlanVersionId()).isEqualTo(55L);
        assertThat(decision.getRiskLevel()).isEqualTo("LOW");
        OaSignPackage draft = decision.getDraftPackage();
        assertThat(draft.getTransferEffectiveDate()).isEqualTo("2027-01-01");
        assertThat(draft.getHistoricalSupplement()).isFalse();
        assertThat(draft.getBeforeDeptNameSnapshot()).isEqualTo("上海一店");
        assertThat(draft.getBeforePostNameSnapshot()).isEqualTo("销售顾问");
        assertThat(draft.getDeptNameSnapshot()).isEqualTo("上海二店");
        assertThat(draft.getPostNameSnapshot()).isEqualTo("店长");
        assertThat(draft.getRemark()).contains(
                "生效日:2027-01-01", "组织:上海一店→上海二店", "岗位:销售顾问→店长");
    }

    @Test
    @DisplayName("历史补录必须使用业务生效日并在草稿与决定中标记HIGH")
    void shouldUseHistoricalBusinessDateAndHighRiskMarker()
    {
        stubPublishedPlan("{\"historicalSupplement\":true}", 301L, true);

        OaSignDraftDecision decision = rule.decide(
                event(LocalDate.of(2026, 12, 31), true));

        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.CREATE_DRAFT);
        assertThat(decision.getRiskLevel()).isEqualTo("HIGH");
        assertThat(decision.getReasonCodes()).contains("HISTORICAL_BACKFILL");
        assertThat(decision.getDraftPackage().getTransferEffectiveDate())
                .isEqualTo("2026-12-31");
        assertThat(decision.getDraftPackage().getHistoricalSupplement()).isTrue();
        assertThat(decision.getDraftPackage().getRemark()).contains("历史调岗补录");
    }

    @Test
    @DisplayName("历史补录事件缺少补录原因时必须进入单HR补资料队列")
    void shouldRequireHistoricalReason()
    {
        HrSignBusinessEvent event = event(LocalDate.of(2026, 12, 31), true);
        event.getAttributes().remove("historicalReason");

        OaSignDraftDecision decision = rule.decide(event);

        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.NEEDS_DATA);
        assertThat(decision.getReasonCodes())
                .contains("MISSING_HISTORICAL_REASON", "HISTORICAL_BACKFILL");
        verifyNoInteractions(versionMapper);
    }

    @Test
    @DisplayName("已匹配方案没有员工确认材料时应明确跳过签约")
    void shouldSkipWhenPlanHasNoEmployeeConfirmationDocument()
    {
        stubPublishedPlan("{\"organizationChanged\":true}", 301L, false);

        OaSignDraftDecision decision = rule.decide(event(today(), false));

        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.NO_ACTION);
        assertThat(decision.getPlanVersionId()).isEqualTo(55L);
        assertThat(decision.getReasonCodes())
                .contains("NO_EMPLOYEE_CONFIRMATION_DOCUMENT");
        assertThat(decision.getDraftPackage()).isNull();
    }

    @Test
    @DisplayName("未来生效的防御性事件不能匹配方案或创建草稿")
    void shouldRejectFutureTransferEventWithoutPlanLookup()
    {
        OaSignDraftDecision decision = rule.decide(
                event(LocalDate.of(2027, 1, 2), false));

        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.NEEDS_DATA);
        assertThat(decision.getReasonCodes()).contains("FUTURE_TRANSFER_EVENT");
        assertThat(decision.getDraftPackage()).isNull();
        verifyNoInteractions(versionMapper);
    }

    @Test
    @DisplayName("仅内部汇报关系变化应明确NO_ACTION且不读取签约方案")
    void shouldSkipReportingLineOnlyTransfer()
    {
        HrSignBusinessEvent event = event(today(), false);
        HrEmployeeSigningSnapshot before = event.getBeforeSnapshot();
        HrEmployeeSigningSnapshot after = copy(before);
        after.setDirectSupervisorId(66L);
        after.setDirectSupervisorName("区域经理");
        after.setTransferEffectiveDate(today());
        event.setAfterSnapshot(after);

        OaSignDraftDecision decision = rule.decide(event);

        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.NO_ACTION);
        assertThat(decision.getReasonCodes()).contains("REPORTING_LINE_ONLY");
        assertThat(decision.getPlanVersionId()).isNull();
        verifyNoInteractions(versionMapper);
    }

    @Test
    @DisplayName("签约资料缺失时进入单HR任务中心而不是静默跳过")
    void shouldReturnNeedsDataForIncompleteTransferSnapshot()
    {
        HrSignBusinessEvent event = event(today(), false);
        event.getAfterSnapshot().setPhone(null);

        OaSignDraftDecision decision = rule.decide(event);

        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.NEEDS_DATA);
        assertThat(decision.getReasonCodes()).contains("INVALID_AFTER_PHONE");
        verifyNoInteractions(versionMapper);
    }

    @Test
    @DisplayName("跨主体跨城市降职降薪由服务端推导HIGH和具体风险代码")
    void shouldDeriveHighRiskReasonsFromSnapshots()
    {
        HrSignBusinessEvent event = event(today(), false);
        HrEmployeeSigningSnapshot after = event.getAfterSnapshot();
        after.setLegalEntityId(302L);
        after.setLegalEntityCode("BJ-COMPANY");
        after.setLegalEntityName("北京公司");
        after.setWorkLocation("北京市朝阳区");
        after.setJobGradeCode("P2");
        after.setJobGradeName("P2");
        after.setBaseSalary(new BigDecimal("5000.00"));
        after.setPostSalary(new BigDecimal("1500.00"));
        after.setFieldAllowance(new BigDecimal("300.00"));
        after.setPerformanceSalary(new BigDecimal("1200.00"));
        after.setSalaryTotal(new BigDecimal("8000.00"));
        stubPublishedPlan("{\"legalEntityChanged\":true}", 302L, true);

        OaSignDraftDecision decision = rule.decide(event);

        assertThat(decision.getRiskLevel()).isEqualTo("HIGH");
        assertThat(decision.getReasonCodes()).contains(
                "LEGAL_ENTITY_CHANGED", "CROSS_CITY", "JOB_GRADE_DECREASED",
                "SALARY_DECREASED");
    }

    @Test
    @DisplayName("没有匹配方案时应进入NEEDS_DATA并保留可理解原因")
    void shouldNeedDataWhenPublishedPlanIsMissing()
    {
        when(versionMapper.selectPublishedMatchingCandidates("TRANSFER", 30L, 301L))
                .thenReturn(List.of());

        OaSignDraftDecision decision = rule.decide(event(today(), false));

        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.NEEDS_DATA);
        assertThat(decision.getReasonCodes()).contains("PLAN_NOT_FOUND");
        verify(versionMapper, never()).selectTemplatesByVersionId(55L);
    }

    @Test
    void unchangedSalaryRetainsOtherTransferDocumentsWithoutSalaryNotice()
    {
        stubPublishedPlan("{\"organizationChanged\":true}", 301L, true);
        HrSignBusinessEvent event = event(today(), false);
        preserveSalary(event);
        OaSignDraftDecision decision = rule.decide(event);
        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.CREATE_DRAFT);
        assertThat(decision.getDraftPackage().getRemark()).doesNotContain("薪资");
    }

    @Test
    void unchangedSalaryWithOnlySalaryTemplateIsNoAction()
    {
        stubPublishedPlan("{\"organizationChanged\":true}", 301L, true);
        when(versionMapper.selectTemplatesByVersionId(55L)).thenReturn(List.of(salaryTemplate(true)));
        HrSignBusinessEvent event = event(today(), false);
        preserveSalary(event);
        assertThat(rule.decide(event).getAction()).isEqualTo(OaSignDraftDecision.Action.NO_ACTION);
    }

    @Test
    void actualAdjustmentWithoutSalaryTemplateRequiresPlanCorrection()
    {
        stubPublishedPlan("{\"organizationChanged\":true}", 301L, true);
        when(versionMapper.selectTemplatesByVersionId(55L)).thenReturn(List.of(template(true)));
        OaSignDraftDecision decision = rule.decide(event(today(), false));
        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.NEEDS_DATA);
        assertThat(decision.getReasonCodes()).contains("SALARY_CONFIRMATION_TEMPLATE_MISSING");
    }

    private void preserveSalary(HrSignBusinessEvent event)
    {
        HrEmployeeSigningSnapshot before = event.getBeforeSnapshot(), after = event.getAfterSnapshot();
        after.setBaseSalary(before.getBaseSalary()); after.setPostSalary(before.getPostSalary());
        after.setFieldAllowance(before.getFieldAllowance()); after.setPerformanceSalary(before.getPerformanceSalary());
        after.setSalaryTotal(before.getSalaryTotal());
        after.setSalaryVersion("VERSION-ONLY-CHANGE");
    }

    private OaSignPlanVersionTemplate salaryTemplate(boolean employeeSignRequired)
    {
        OaSignPlanVersionTemplate value = template(employeeSignRequired);
        value.setId(72L); value.setTemplateId(7002L); value.setSortOrder(2);
        value.setTemplateType(OaSignTemplateType.TRANSFER_SALARY_CONFIRM);
        value.setTemplateName("调岗薪资确认书");
        return value;
    }

    private void stubPublishedPlan(String ruleJson, Long legalEntityId,
            boolean employeeSignRequired)
    {
        OaSignPlanVersion version = new OaSignPlanVersion();
        version.setVersionId(55L);
        version.setPlanId(5L);
        version.setPlanName("调岗补充协议方案");
        version.setScenario("TRANSFER");
        version.setShopDeptId(0L);
        version.setLegalEntityId(legalEntityId);
        version.setRuleJson(ruleJson);
        version.setPublishStatus("PUBLISHED");
        version.setMatchingStatus("ENABLED");
        when(versionMapper.selectPublishedMatchingCandidates(
                "TRANSFER", 30L, legalEntityId)).thenReturn(List.of(version));
        when(versionMapper.selectTemplatesByVersionId(55L))
                .thenReturn(List.of(template(employeeSignRequired), salaryTemplate(employeeSignRequired)));
    }

    private OaSignPlanVersionTemplate template(boolean employeeSignRequired)
    {
        OaSignPlanVersionTemplate template = new OaSignPlanVersionTemplate();
        template.setId(71L);
        template.setPlanVersionId(55L);
        template.setTemplateId(7001L);
        template.setTemplateVersion("V1");
        template.setTemplateType(OaSignTemplateType.TRANSFER_CONFIRMATION);
        template.setTemplateName("调岗确认书");
        template.setSourceFileUrl("/templates/transfer-confirmation-v1.docx");
        template.setSourceFileHash("a".repeat(64));
        template.setSortOrder(1);
        template.setEmployeeSignRequired(employeeSignRequired ? "Y" : "N");
        template.setSignaturePositionJson(employeeSignRequired
                ? "{\"mode\":\"APPENDED_CONFIRMATION_PAGE\"}" : null);
        template.setCompanySealPositionJson(employeeSignRequired ? null
                : "{\"pageNumber\":1,\"x\":10,\"y\":10,\"width\":80,\"height\":30}");
        return template;
    }

    private HrSignBusinessEvent event(LocalDate effectiveDate, boolean historical)
    {
        HrSignBusinessEvent event = new HrSignBusinessEvent();
        event.setEventId("event-transfer-801");
        event.setScenario("TRANSFER");
        event.setEmployeeId(9L);
        event.setSourceType("HR_LIFECYCLE_ACTION");
        event.setSourceBusinessId("801");
        event.setSourceEventVersion(1L);
        event.setOccurredTime(Date.from(Instant.parse("2026-12-31T16:30:00Z")));
        event.setOperatorUserId(88L);
        event.setBeforeSnapshot(before());
        HrEmployeeSigningSnapshot after = after();
        after.setTransferEffectiveDate(effectiveDate);
        event.setAfterSnapshot(after);
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("actionType", "TRANSFER_CONFIRMED");
        attributes.put("sourceActionId", 801L);
        attributes.put("sourceActionVersion", 1L);
        attributes.put("effectiveDate", effectiveDate.toString());
        attributes.put("historicalSupplement", historical);
        attributes.put("riskLevel", historical ? "HIGH" : "LOW");
        if (historical) attributes.put("historicalReason", "补录纸质调岗单");
        event.setAttributes(attributes);
        return event;
    }

    private HrEmployeeSigningSnapshot before()
    {
        HrEmployeeSigningSnapshot snapshot = common();
        snapshot.setShopDeptId(20L);
        snapshot.setShopDeptName("上海一店");
        snapshot.setDeptId(20L);
        snapshot.setDeptName("上海一店");
        snapshot.setPostId(401L);
        snapshot.setPostCode("SALES");
        snapshot.setPostName("销售顾问");
        snapshot.setJobGradeCode("P3");
        snapshot.setJobGradeName("P3");
        snapshot.setDirectSupervisorId(55L);
        snapshot.setDirectSupervisorName("原店长");
        snapshot.setDepartmentSupervisorName("原部门主管");
        snapshot.setWorkLocation("上海市黄浦区");
        snapshot.setBaseSalary(new BigDecimal("5500.00"));
        snapshot.setPostSalary(new BigDecimal("1800.00"));
        snapshot.setFieldAllowance(new BigDecimal("400.00"));
        snapshot.setPerformanceSalary(new BigDecimal("1300.00"));
        snapshot.setSalaryTotal(new BigDecimal("9000.00"));
        snapshot.setSalaryVersion("CURRENT-2026");
        return snapshot;
    }

    private HrEmployeeSigningSnapshot after()
    {
        HrEmployeeSigningSnapshot snapshot = common();
        snapshot.setShopDeptId(30L);
        snapshot.setShopDeptName("上海二店");
        snapshot.setDeptId(30L);
        snapshot.setDeptName("上海二店");
        snapshot.setPostId(402L);
        snapshot.setPostCode("STORE_MANAGER");
        snapshot.setPostName("店长");
        snapshot.setJobGradeCode("P4");
        snapshot.setJobGradeName("P4");
        snapshot.setDirectSupervisorId(66L);
        snapshot.setDirectSupervisorName("区域经理");
        snapshot.setDepartmentSupervisorName("新部门主管");
        snapshot.setWorkLocation("上海市浦东新区");
        snapshot.setBaseSalary(new BigDecimal("6000.00"));
        snapshot.setPostSalary(new BigDecimal("2000.00"));
        snapshot.setFieldAllowance(new BigDecimal("500.00"));
        snapshot.setPerformanceSalary(new BigDecimal("1500.00"));
        snapshot.setSalaryTotal(new BigDecimal("10000.00"));
        snapshot.setSalaryVersion("TRANSFER-2027-01");
        return snapshot;
    }

    private HrEmployeeSigningSnapshot common()
    {
        HrEmployeeSigningSnapshot snapshot = new HrEmployeeSigningSnapshot();
        snapshot.setEmployeeId(9L);
        snapshot.setEmployeeNo("E0009");
        snapshot.setEmployeeName("张三");
        snapshot.setPhone("13800000009");
        snapshot.setIdType("身份证");
        snapshot.setIdNumber("310101199001010019");
        snapshot.setCurrentAddress("上海市黄浦区");
        snapshot.setEmployeeStatus("正式");
        snapshot.setEmployeeCategory("全职");
        snapshot.setWorkCityLevel("一线");
        snapshot.setLegalEntityId(301L);
        snapshot.setLegalEntityCode("SH-COMPANY");
        snapshot.setLegalEntityName("上海公司");
        snapshot.setContractTypeCode("LABOR_CONTRACT");
        snapshot.setContractTermCode("THREE_YEAR");
        snapshot.setSocialTypeCode("SOCIAL_INSURED");
        snapshot.setRenewalCount(0);
        snapshot.setEntryDate(LocalDate.of(2024, 1, 1));
        snapshot.setContractStartDate(LocalDate.of(2024, 1, 1));
        snapshot.setContractEndDate(LocalDate.of(2027, 12, 31));
        return snapshot;
    }

    private HrEmployeeSigningSnapshot copy(HrEmployeeSigningSnapshot source)
    {
        return JsonMapper.builder().findAndAddModules().build()
                .convertValue(source, HrEmployeeSigningSnapshot.class);
    }

    private LocalDate today()
    {
        return LocalDate.of(2027, 1, 1);
    }
}
