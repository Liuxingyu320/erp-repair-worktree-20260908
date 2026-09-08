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
import com.erp.oa.constant.OaSignPackageStatus;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPlanVersion;
import com.erp.oa.domain.OaSignPlanVersionTemplate;
import com.erp.oa.domain.vo.OaSignDraftDecision;
import com.erp.oa.mapper.OaSignPlanVersionMapper;

@DisplayName("离职签约场景规则")
class OffboardSignScenarioRuleTest
{
    private OaSignPlanVersionMapper versionMapper;
    private OffboardSignScenarioRule rule;

    @BeforeEach
    void setUp()
    {
        versionMapper = mock(OaSignPlanVersionMapper.class);
        rule = new OffboardSignScenarioRule(versionMapper,
                JsonMapper.builder().findAndAddModules().build());
    }

    @Test
    @DisplayName("离职事件强校验信封并使用action版本去重")
    void shouldValidateEnvelopeAndUseStableDedupeKey()
    {
        assertThat(rule.supports(" offboard ")).isTrue();
        assertThat(rule.dedupeKey(event(today(), false)))
                .isEqualTo("OFFBOARD:9:901:1");

        HrSignBusinessEvent forged = event(today(), false);
        forged.getAttributes().put("sourceActionId", 902L);
        assertThatThrownBy(() -> rule.dedupeKey(forged))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("离职事件不一致")
                .hasMessageContaining("SOURCE_ACTION_ID_MISMATCH");
    }

    @Test
    @DisplayName("标准当天离职冻结员工组织岗位合同与离职日期并停在HR确认")
    void shouldCreateLowRiskDraftFromFrozenSnapshots()
    {
        stubPlan(standardRule(), true);

        OaSignDraftDecision decision = rule.decide(event(today(), false));

        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.CREATE_DRAFT);
        assertThat(decision.getRiskLevel()).isEqualTo("LOW");
        assertThat(decision.getReasonCodes()).isEmpty();
        assertThat(decision.getPlanVersionId()).isEqualTo(65L);
        OaSignPackage draft = decision.getDraftPackage();
        assertThat(draft.getScenario()).isEqualTo("OFFBOARD");
        assertThat(draft.getEmployeeId()).isEqualTo(9L);
        assertThat(draft.getEmployeeNameSnapshot()).isEqualTo("张三");
        assertThat(draft.getDeptNameSnapshot()).isEqualTo("上海一店");
        assertThat(draft.getShopDeptName()).isEqualTo("上海一店");
        assertThat(draft.getPostNameSnapshot()).isEqualTo("销售顾问");
        assertThat(draft.getEntryDate()).isEqualTo("2024-01-01");
        assertThat(draft.getContractStartDate()).isEqualTo("2024-01-01");
        assertThat(draft.getContractEndDate()).isEqualTo("2027-12-31");
        assertThat(draft.getLeaveDate()).isEqualTo("2027-01-01");
        assertThat(draft.getLeaveReason()).isEqualTo("个人职业规划");
        assertThat(draft.getOffboardingType()).isEqualTo("VOLUNTARY_EXPECTED");
        assertThat(draft.getSalarySettlementStatus()).isEqualTo("COMPLETED");
        assertThat(draft.getAssetHandoverStatus()).isEqualTo("COMPLETED");
        assertThat(draft.getNonCompeteDecision()).isEqualTo("NOT_APPLICABLE");
        assertThat(draft.getCompensationAmount()).isEqualByComparingTo("0");
        assertThat(draft.getCompensationNote()).isNull();
        assertThat(draft.getStatus()).isEqualTo(OaSignPackageStatus.DRAFT);
        assertThat(draft.getConfirmStatus()).isEqualTo("WAITING_HR");
        assertThat(draft.getPlanVersionId()).isEqualTo(65L);
        assertThat(draft.getSentTime()).isNull();
    }

    @Test
    @DisplayName("五类离职由冻结after快照派生风险而非信任前端")
    void shouldDeriveRiskForEveryOffboardingType()
    {
        for (String type : List.of("VOLUNTARY_EXPECTED", "VOLUNTARY_UNEXPECTED",
                "TERMINATION", "DISCIPLINARY_TERMINATION", "DISPUTED_TERMINATION"))
        {
            HrSignBusinessEvent event = event(today(), false);
            event.getAfterSnapshot().setOffboardingType(type);
            boolean high = !"VOLUNTARY_EXPECTED".equals(type);
            event.getAttributes().put("riskLevel", high ? "HIGH" : "LOW");
            stubPlan("{\"offboardingTypes\":[\"" + type + "\"]}", true);

            OaSignDraftDecision decision = rule.decide(event);

            assertThat(decision.getRiskLevel()).isEqualTo(high ? "HIGH" : "LOW");
            if (high)
                assertThat(decision.getReasonCodes()).contains("NON_STANDARD_OFFBOARDING_TYPE");
        }
    }

    @Test
    @DisplayName("工资资产竞业和自定义补偿分别派生精确HIGH风险")
    void shouldDeriveEveryDataRiskFromAfterSnapshot()
    {
        assertRisk(eventWithAfter("salarySettlementStatus", "PENDING"),
                "SALARY_SETTLEMENT_PENDING");
        assertRisk(eventWithAfter("assetHandoverStatus", "PENDING"),
                "ASSET_HANDOVER_PENDING");
        assertRisk(eventWithAfter("nonCompeteDecision", "REQUIRED"),
                "NON_COMPETE_REVIEW_REQUIRED");
        assertRisk(eventWithAfter("nonCompeteDecision", "PENDING"),
                "NON_COMPETE_REVIEW_REQUIRED");

        HrSignBusinessEvent amount = event(today(), false);
        amount.getAfterSnapshot().setCompensationAmount(new BigDecimal("20000.00"));
        assertRisk(amount, "COMPENSATION_REVIEW_REQUIRED");
        HrSignBusinessEvent note = event(today(), false);
        note.getAfterSnapshot().setCompensationNote("双方协商补偿");
        assertRisk(note, "COMPENSATION_REVIEW_REQUIRED");
    }

    @Test
    @DisplayName("历史离职按上海自然日派生HIGH且校验历史标记")
    void shouldDeriveHistoricalRiskUsingShanghaiBusinessDate()
    {
        HrSignBusinessEvent historical = event(LocalDate.of(2026, 12, 31), true);
        historical.getAttributes().put("riskLevel", "HIGH");
        stubPlan("{\"historicalSupplement\":true,\"riskLevels\":[\"HIGH\"]}", true);

        OaSignDraftDecision decision = rule.decide(historical);

        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.CREATE_DRAFT);
        assertThat(decision.getRiskLevel()).isEqualTo("HIGH");
        assertThat(decision.getReasonCodes()).contains("HISTORICAL_OFFBOARDING");
        assertThat(decision.getDraftPackage().getHistoricalSupplement()).isTrue();

        historical.getAttributes().put("historicalSupplement", false);
        assertThat(rule.decide(historical).getReasonCodes())
                .contains("HISTORICAL_MARKER_MISMATCH");
    }

    @Test
    @DisplayName("attribute风险等级与OA重算不一致进入补资料中心")
    void shouldRejectForgedLowRiskAttribute()
    {
        HrSignBusinessEvent event = event(today(), false);
        event.getAfterSnapshot().setOffboardingType("TERMINATION");

        OaSignDraftDecision decision = rule.decide(event);

        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.NEEDS_DATA);
        assertThat(decision.getRiskLevel()).isEqualTo("HIGH");
        assertThat(decision.getReasonCodes())
                .contains("RISK_LEVEL_MISMATCH", "NON_STANDARD_OFFBOARDING_TYPE");
        verifyNoInteractions(versionMapper);
    }

    @Test
    @DisplayName("离职前后状态和非离职字段必须来自不可变快照")
    void shouldRejectInvalidStateTransitionAndUnrelatedMutation()
    {
        HrSignBusinessEvent invalid = event(today(), false);
        invalid.getBeforeSnapshot().setAccountStatus("1");
        assertThat(rule.decide(invalid).getReasonCodes()).contains("INVALID_STATUS_TRANSITION");

        HrSignBusinessEvent changed = event(today(), false);
        changed.getAfterSnapshot().setPostName("伪造岗位");
        assertThat(rule.decide(changed).getReasonCodes()).contains("UNRELATED_SNAPSHOT_CHANGED");
        verifyNoInteractions(versionMapper);
    }

    @Test
    @DisplayName("规则缺失非法及多方案冲突进入单HR任务中心")
    void shouldRouteMissingInvalidAndConflictingPlansToNeedsData()
    {
        when(versionMapper.selectPublishedMatchingCandidates("OFFBOARD", 20L, 301L))
                .thenReturn(List.of());
        assertThat(rule.decide(event(today(), false)).getReasonCodes()).contains("PLAN_NOT_FOUND");

        OaSignPlanVersion invalid = version(65L, "{\"unknown\":[]}");
        when(versionMapper.selectPublishedMatchingCandidates("OFFBOARD", 20L, 301L))
                .thenReturn(List.of(invalid));
        assertThat(rule.decide(event(today(), false)).getReasonCodes()).contains("PLAN_RULE_INVALID");

        OaSignPlanVersion first = version(65L, "{}");
        OaSignPlanVersion second = version(66L, "{}");
        when(versionMapper.selectPublishedMatchingCandidates("OFFBOARD", 20L, 301L))
                .thenReturn(List.of(first, second));
        assertThat(rule.decide(event(today(), false)).getReasonCodes()).contains("PLAN_CONFLICT");
        verify(versionMapper, never()).selectTemplatesByVersionId(65L);
    }

    @Test
    @DisplayName("规则只接受计划列出的数组枚举和布尔字段")
    void shouldRejectMalformedRulePredicates()
    {
        for (String json : List.of("not-json", "[]", "{\"riskLevels\":\"LOW\"}",
                "{\"offboardingTypes\":[\"UNKNOWN\"]}",
                "{\"salarySettlementStatuses\":[\"UNKNOWN\"]}",
                "{\"assetHandoverStatuses\":[\"UNKNOWN\"]}",
                "{\"nonCompeteDecisions\":[\"UNKNOWN\"]}",
                "{\"riskLevels\":[\"MEDIUM\"]}",
                "{\"historicalSupplement\":\"false\"}"))
        {
            when(versionMapper.selectPublishedMatchingCandidates("OFFBOARD", 20L, 301L))
                    .thenReturn(List.of(version(65L, json)));
            assertThat(rule.decide(event(today(), false)).getReasonCodes())
                    .as(json).contains("PLAN_RULE_INVALID");
        }
    }

    @Test
    @DisplayName("模板缺失场景错误进入补资料，只有非员工签材料明确NO_ACTION")
    void shouldValidateFrozenTemplateSnapshotsAndEmployeeConfirmation()
    {
        OaSignPlanVersion version = version(65L, "{}");
        when(versionMapper.selectPublishedMatchingCandidates("OFFBOARD", 20L, 301L))
                .thenReturn(List.of(version));
        when(versionMapper.selectTemplatesByVersionId(65L)).thenReturn(List.of());
        assertThat(rule.decide(event(today(), false)).getReasonCodes())
                .contains("PLAN_TEMPLATE_SNAPSHOT_MISSING");

        when(versionMapper.selectTemplatesByVersionId(65L))
                .thenReturn(List.of(template("TRANSFER_CONFIRMATION", true)));
        assertThat(rule.decide(event(today(), false)).getReasonCodes())
                .contains("PLAN_TEMPLATE_INVALID");

        when(versionMapper.selectTemplatesByVersionId(65L))
                .thenReturn(List.of(template("OFFBOARD_LEAVE_CERTIFICATE", false)));
        OaSignDraftDecision decision = rule.decide(event(today(), false));
        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.NO_ACTION);
        assertThat(decision.getReasonCodes()).contains("NO_EMPLOYEE_CONFIRMATION_DOCUMENT");
        assertThat(decision.getDraftPackage()).isNull();
    }

    @Test
    @DisplayName("重复事件产生完全一致的业务决定")
    void shouldMakeDeterministicDecisionForRepeatedEvent()
    {
        stubPlan(standardRule(), true);
        OaSignDraftDecision first = rule.decide(event(today(), false));
        OaSignDraftDecision second = rule.decide(event(today(), false));

        assertThat(second.getAction()).isEqualTo(first.getAction());
        assertThat(second.getPlanVersionId()).isEqualTo(first.getPlanVersionId());
        assertThat(second.getRiskLevel()).isEqualTo(first.getRiskLevel());
        assertThat(second.getReasonCodes()).isEqualTo(first.getReasonCodes());
        assertThat(second.getDraftPackage().getLeaveDate())
                .isEqualTo(first.getDraftPackage().getLeaveDate());
        assertThat(second.getDraftPackage().getRemark())
                .isEqualTo(first.getDraftPackage().getRemark());
    }

    private void assertRisk(HrSignBusinessEvent event, String reason)
    {
        event.getAttributes().put("riskLevel", "HIGH");
        stubPlan("{\"riskLevels\":[\"HIGH\"]}", true);
        OaSignDraftDecision decision = rule.decide(event);
        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.CREATE_DRAFT);
        assertThat(decision.getRiskLevel()).isEqualTo("HIGH");
        assertThat(decision.getReasonCodes()).contains(reason);
    }

    private HrSignBusinessEvent eventWithAfter(String field, String value)
    {
        HrSignBusinessEvent event = event(today(), false);
        if ("salarySettlementStatus".equals(field))
            event.getAfterSnapshot().setSalarySettlementStatus(value);
        else if ("assetHandoverStatus".equals(field))
            event.getAfterSnapshot().setAssetHandoverStatus(value);
        else if ("nonCompeteDecision".equals(field))
            event.getAfterSnapshot().setNonCompeteDecision(value);
        return event;
    }

    private void stubPlan(String ruleJson, boolean employeeSignRequired)
    {
        OaSignPlanVersion version = version(65L, ruleJson);
        when(versionMapper.selectPublishedMatchingCandidates("OFFBOARD", 20L, 301L))
                .thenReturn(List.of(version));
        when(versionMapper.selectTemplatesByVersionId(65L))
                .thenReturn(List.of(template(employeeSignRequired
                        ? "OFFBOARD_CONFIRMATION" : "OFFBOARD_LEAVE_CERTIFICATE",
                        employeeSignRequired)));
    }

    private OaSignPlanVersion version(Long id, String ruleJson)
    {
        OaSignPlanVersion version = new OaSignPlanVersion();
        version.setVersionId(id);
        version.setPlanId(6L);
        version.setPlanName("离职材料方案");
        version.setScenario("OFFBOARD");
        version.setShopDeptId(0L);
        version.setLegalEntityId(301L);
        version.setRuleJson(ruleJson);
        version.setPublishStatus("PUBLISHED");
        version.setMatchingStatus("ENABLED");
        return version;
    }

    private OaSignPlanVersionTemplate template(String type, boolean employeeSignRequired)
    {
        OaSignPlanVersionTemplate template = new OaSignPlanVersionTemplate();
        template.setId(81L);
        template.setPlanVersionId(65L);
        template.setTemplateId(8001L);
        template.setTemplateVersion("V1");
        template.setTemplateType(type);
        template.setTemplateName("离职材料");
        template.setSourceFileUrl("/templates/offboard-v1.docx");
        template.setSourceFileHash("b".repeat(64));
        template.setSortOrder(1);
        template.setEmployeeSignRequired(employeeSignRequired ? "Y" : "N");
        template.setSignaturePositionJson(employeeSignRequired
                ? "{\"mode\":\"APPENDED_CONFIRMATION_PAGE\"}" : null);
        template.setCompanySealPositionJson(employeeSignRequired ? null
                : "{\"pageNumber\":1,\"x\":10,\"y\":10,\"width\":80,\"height\":30}");
        return template;
    }

    private String standardRule()
    {
        return "{\"offboardingTypes\":[\"VOLUNTARY_EXPECTED\"],"
                + "\"salarySettlementStatuses\":[\"COMPLETED\"],"
                + "\"assetHandoverStatuses\":[\"COMPLETED\"],"
                + "\"nonCompeteDecisions\":[\"NOT_APPLICABLE\"],"
                + "\"riskLevels\":[\"LOW\"],\"historicalSupplement\":false}";
    }

    private HrSignBusinessEvent event(LocalDate effectiveDate, boolean historical)
    {
        HrSignBusinessEvent event = new HrSignBusinessEvent();
        event.setEventId("event-offboard-901");
        event.setScenario("OFFBOARD");
        event.setEmployeeId(9L);
        event.setSourceType("HR_LIFECYCLE_ACTION");
        event.setSourceBusinessId("901");
        event.setSourceEventVersion(1L);
        event.setOccurredTime(Date.from(Instant.parse("2026-12-31T16:30:00Z")));
        event.setOperatorUserId(88L);
        event.setBeforeSnapshot(before());
        event.setAfterSnapshot(after(effectiveDate));
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("actionType", "OFFBOARD_CONFIRMED");
        attributes.put("sourceActionId", 901L);
        attributes.put("sourceActionVersion", 1L);
        attributes.put("effectiveDate", effectiveDate.toString());
        attributes.put("historicalSupplement", historical);
        attributes.put("riskLevel", historical ? "HIGH" : "LOW");
        if (historical) attributes.put("historicalReason", "补录纸质离职单");
        event.setAttributes(attributes);
        return event;
    }

    private HrEmployeeSigningSnapshot before()
    {
        HrEmployeeSigningSnapshot snapshot = common();
        snapshot.setEmployeeStatus("正式");
        snapshot.setAccountStatus("0");
        return snapshot;
    }

    private HrEmployeeSigningSnapshot after(LocalDate leaveDate)
    {
        HrEmployeeSigningSnapshot snapshot = common();
        snapshot.setEmployeeStatus("离职");
        snapshot.setAccountStatus("1");
        snapshot.setLeaveDate(leaveDate);
        snapshot.setOffboardingType("VOLUNTARY_EXPECTED");
        snapshot.setLeaveReason("个人职业规划");
        snapshot.setSalarySettlementStatus("COMPLETED");
        snapshot.setAssetHandoverStatus("COMPLETED");
        snapshot.setNonCompeteDecision("NOT_APPLICABLE");
        snapshot.setCompensationAmount(BigDecimal.ZERO);
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
        snapshot.setEmployeeCategory("全职");
        snapshot.setShopDeptId(20L);
        snapshot.setShopDeptName("上海一店");
        snapshot.setDeptId(20L);
        snapshot.setDeptName("上海一店");
        snapshot.setLegalEntityId(301L);
        snapshot.setLegalEntityCode("SH-COMPANY");
        snapshot.setLegalEntityName("上海公司");
        snapshot.setPostId(401L);
        snapshot.setPostCode("SALES");
        snapshot.setPostName("销售顾问");
        snapshot.setJobGradeCode("P3");
        snapshot.setJobGradeName("P3");
        snapshot.setDirectSupervisorId(55L);
        snapshot.setDirectSupervisorName("店长");
        snapshot.setDepartmentSupervisorId(66L);
        snapshot.setDepartmentSupervisorName("区域经理");
        snapshot.setWorkLocation("上海市黄浦区");
        snapshot.setWorkCityLevel("一线");
        snapshot.setContractTypeCode("LABOR_CONTRACT");
        snapshot.setContractTermCode("THREE_YEAR");
        snapshot.setSocialTypeCode("SOCIAL_INSURED");
        snapshot.setRenewalCount(0);
        snapshot.setEntryDate(LocalDate.of(2024, 1, 1));
        snapshot.setContractStartDate(LocalDate.of(2024, 1, 1));
        snapshot.setContractEndDate(LocalDate.of(2027, 12, 31));
        snapshot.setBaseSalary(new BigDecimal("5500.00"));
        snapshot.setPostSalary(new BigDecimal("1800.00"));
        snapshot.setFieldAllowance(new BigDecimal("400.00"));
        snapshot.setPerformanceSalary(new BigDecimal("1300.00"));
        snapshot.setSalaryTotal(new BigDecimal("9000.00"));
        snapshot.setSalaryVersion("CURRENT-2026");
        return snapshot;
    }

    private LocalDate today()
    {
        return LocalDate.of(2027, 1, 1);
    }
}
