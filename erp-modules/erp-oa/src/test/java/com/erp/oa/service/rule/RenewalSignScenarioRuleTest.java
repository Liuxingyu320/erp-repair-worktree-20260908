package com.erp.oa.service.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Stream;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.api.domain.HrEmployeeSigningSnapshot;
import com.erp.oa.api.domain.HrSignBusinessEvent;
import com.erp.oa.constant.OaSignPackageStatus;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPlanVersion;
import com.erp.oa.domain.vo.OaSignDraftDecision;
import com.erp.oa.mapper.OaSignPlanVersionMapper;

@DisplayName("续签签约场景规则")
class RenewalSignScenarioRuleTest
{
    private OaSignPlanVersionMapper versionMapper;
    private RenewalSignScenarioRule rule;

    @BeforeEach
    void setUp()
    {
        versionMapper = mock(OaSignPlanVersionMapper.class);
        rule = new RenewalSignScenarioRule(
                versionMapper, JsonMapper.builder().findAndAddModules().build());
    }

    @Test
    @DisplayName("唯一支持RENEWAL并从typed快照和一致attributes生成固定去重键")
    void shouldSupportOnlyRenewalAndBuildStableDedupeKeys()
    {
        assertThat(rule.supports(" renewal ")).isTrue();
        assertThat(rule.supports("ONBOARD")).isFalse();
        assertThat(rule.dedupeKey(renewEvent())).isEqualTo(
                "RENEWAL:201:2026-08-01:3");
        assertThat(rule.dedupeKey(declineEvent())).isEqualTo(
                "RENEWAL:201:2026-08-01:2");
    }

    @Test
    @DisplayName("缺失actionType时决定进入NEEDS_DATA且去重键拒绝")
    void shouldRejectMissingActionType()
    {
        HrSignBusinessEvent event = renewEvent();
        event.getAttributes().remove("actionType");

        assertThatThrownBy(() -> rule.dedupeKey(event))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("MISSING_ACTION_TYPE");
        assertThat(rule.decide(event).getReasonCodes())
                .containsExactly("MISSING_ACTION_TYPE");
        verify(versionMapper, never()).selectPublishedMatchingCandidates(
                org.mockito.ArgumentMatchers.anyString(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("RENEW和DECLINE的actionType与decision不一致时必须拒绝")
    void shouldRejectActionTypeDecisionMismatch()
    {
        HrSignBusinessEvent renew = renewEvent();
        renew.getAttributes().put("actionType", "RENEWAL_DECLINED");
        HrSignBusinessEvent decline = declineEvent();
        decline.getAttributes().put("actionType", "RENEWAL_CONFIRMED");

        assertThatThrownBy(() -> rule.dedupeKey(renew))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("ACTION_TYPE_MISMATCH");
        assertThat(rule.decide(renew).getReasonCodes())
                .containsExactly("ACTION_TYPE_MISMATCH");
        assertThatThrownBy(() -> rule.dedupeKey(decline))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("ACTION_TYPE_MISMATCH");
        assertThat(rule.decide(decline).getReasonCodes())
                .containsExactly("ACTION_TYPE_MISMATCH");
    }

    @Test
    @DisplayName("冲突attributes不能覆盖typed快照构造去重键或规则决定")
    void shouldRejectConflictingAttributes()
    {
        HrSignBusinessEvent event = renewEvent();
        event.getAttributes().put("oldContractEndDate", "2099-01-01");
        event.getAttributes().put("newRenewalCount", 99);

        assertThatThrownBy(() -> rule.dedupeKey(event))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("续签事件");
        OaSignDraftDecision decision = rule.decide(event);
        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.NEEDS_DATA);
        assertThat(decision.getReasonCodes()).contains(
                "OLD_CONTRACT_END_MISMATCH", "NEW_RENEWAL_COUNT_MISMATCH");
        verify(versionMapper, never()).selectPublishedMatchingCandidates(
                org.mockito.ArgumentMatchers.anyString(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("非服务端生命周期action来源不能进入续签规则")
    void shouldRejectNonLifecycleActionSource()
    {
        HrSignBusinessEvent event = renewEvent();
        event.setSourceType("CLIENT_JSON");

        assertThatThrownBy(() -> rule.dedupeKey(event))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("续签事件");
        assertThat(rule.decide(event).getReasonCodes())
                .containsExactly("INVALID_SOURCE_TYPE");
        verify(versionMapper, never()).selectPublishedMatchingCandidates(
                org.mockito.ArgumentMatchers.anyString(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("DECLINE直接NO_ACTION且不查方案不建包")
    void shouldReturnNoActionForDeclineWithoutPlanLookup()
    {
        OaSignDraftDecision decision = rule.decide(declineEvent());

        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.NO_ACTION);
        assertThat(decision.getReasonCodes()).containsExactly("RENEWAL_DECLINED");
        assertThat(decision.getPlanVersionId()).isNull();
        assertThat(decision.getDraftPackage()).isNull();
        verify(versionMapper, never()).selectPublishedMatchingCandidates(
                org.mockito.ArgumentMatchers.anyString(), anyLong(), anyLong());
    }

    @ParameterizedTest(name = "RENEW禁止篡改{0}")
    @MethodSource("immutableRenewalMutations")
    @DisplayName("RENEW除合同法律主体和次数外所有typed字段必须保持不变")
    void shouldRejectEveryUnexpectedRenewalSnapshotChange(String field,
            String setter, Class<?> parameterType, Object value) throws Exception
    {
        HrSignBusinessEvent event = renewEvent();
        HrEmployeeSigningSnapshot.class.getMethod(setter, parameterType)
                .invoke(event.getAfterSnapshot(), value);

        OaSignDraftDecision decision = rule.decide(event);

        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.NEEDS_DATA);
        assertThat(decision.getReasonCodes()).contains("RENEW_SNAPSHOT_CHANGED");
        assertThat(decision.getDraftPackage()).isNull();
        verify(versionMapper, never()).selectPublishedMatchingCandidates(
                org.mockito.ArgumentMatchers.anyString(), anyLong(), anyLong());
    }

    @ParameterizedTest(name = "DECLINE禁止篡改{0}")
    @MethodSource("declineSnapshotMutations")
    @DisplayName("DECLINE要求包含法律组织岗位薪资在内的全快照一致")
    void shouldRejectAnyDeclineSnapshotChange(String field,
            String setter, Class<?> parameterType, Object value) throws Exception
    {
        HrSignBusinessEvent event = declineEvent();
        HrEmployeeSigningSnapshot.class.getMethod(setter, parameterType)
                .invoke(event.getAfterSnapshot(), value);

        OaSignDraftDecision decision = rule.decide(event);

        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.NEEDS_DATA);
        assertThat(decision.getReasonCodes()).containsExactly("DECLINE_SNAPSHOT_CHANGED");
        assertThat(decision.getDraftPackage()).isNull();
        verify(versionMapper, never()).selectPublishedMatchingCandidates(
                org.mockito.ArgumentMatchers.anyString(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("RENEW允许合同法律主体和次数变化且金额scale差异不算篡改")
    void shouldAllowServerOwnedRenewalFieldsAndEquivalentMoneyScale()
    {
        HrSignBusinessEvent event = renewEvent();
        HrEmployeeSigningSnapshot after = event.getAfterSnapshot();
        after.setLegalEntityId(302L);
        after.setLegalEntityCode("LE-BJ");
        after.setLegalEntityName("北京示例有限公司");
        after.setBaseSalary(new BigDecimal("5000.0000"));
        OaSignPlanVersion version = version(explicitRule(), "PUBLISHED", "ENABLED");
        version.setLegalEntityId(302L);
        version.setLegalEntityName("北京示例有限公司");
        when(versionMapper.selectPublishedMatchingCandidates("RENEWAL", 1171L, 302L))
                .thenReturn(List.of(version));

        OaSignDraftDecision decision = rule.decide(event);

        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.CREATE_DRAFT);
        assertThat(decision.getDraftPackage()).isNotNull();
    }

    @Test
    @DisplayName("DECLINE金额仅scale不同仍视为全快照一致")
    void shouldTreatEquivalentMoneyScaleAsSameDeclineSnapshot()
    {
        HrSignBusinessEvent event = declineEvent();
        event.getAfterSnapshot().setSalaryTotal(new BigDecimal("8000.0000"));

        OaSignDraftDecision decision = rule.decide(event);

        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.NO_ACTION);
        assertThat(decision.getDraftPackage()).isNull();
    }

    @Test
    @DisplayName("续签缺手机号证件组织岗位职级原合同公司及旧新合同字段返回全部原因")
    void shouldReturnReadableReasonsForMissingRenewalData()
    {
        HrSignBusinessEvent event = renewEvent();
        HrEmployeeSigningSnapshot before = event.getBeforeSnapshot();
        HrEmployeeSigningSnapshot after = event.getAfterSnapshot();
        after.setPhone(null);
        after.setIdType(null);
        after.setIdNumber(null);
        after.setShopDeptId(null);
        after.setDeptId(null);
        after.setPostId(null);
        after.setPostCode(null);
        after.setPostName(null);
        after.setJobGradeCode(null);
        after.setLegalEntityId(null);
        after.setLegalEntityCode(null);
        after.setLegalEntityName(null);
        before.setLegalEntityId(null);
        before.setLegalEntityCode(null);
        before.setLegalEntityName(null);
        after.setContractStartDate(null);
        after.setContractEndDate(null);
        after.setContractTypeCode(null);
        after.setContractTermCode(null);
        before.setContractStartDate(null);
        before.setContractTypeCode(null);
        before.setContractTermCode(null);

        OaSignDraftDecision decision = rule.decide(event);

        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.NEEDS_DATA);
        assertThat(decision.getReasonCodes()).contains(
                "MISSING_PHONE", "MISSING_IDENTITY", "MISSING_ORGANIZATION",
                "MISSING_POST", "MISSING_JOB_GRADE", "MISSING_PREVIOUS_LEGAL_ENTITY",
                "MISSING_OLD_CONTRACT", "MISSING_NEW_CONTRACT");
        verify(versionMapper, never()).selectPublishedMatchingCandidates(
                org.mockito.ArgumentMatchers.anyString(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("重叠倒置和new count不是old+1均进入NEEDS_DATA")
    void shouldRejectOverlapReversedDatesAndCountMismatch()
    {
        HrSignBusinessEvent overlap = renewEvent();
        overlap.getAfterSnapshot().setContractStartDate(LocalDate.of(2026, 8, 1));
        assertThat(rule.decide(overlap).getReasonCodes()).contains("CONTRACT_OVERLAP");

        HrSignBusinessEvent reversed = renewEvent();
        reversed.getAfterSnapshot().setContractEndDate(
                reversed.getAfterSnapshot().getContractStartDate());
        assertThat(rule.decide(reversed).getReasonCodes()).contains("INVALID_NEW_CONTRACT_DATES");

        HrSignBusinessEvent count = renewEvent();
        count.getAfterSnapshot().setRenewalCount(4);
        count.getAttributes().put("newRenewalCount", 4);
        assertThat(rule.decide(count).getReasonCodes()).contains("INVALID_RENEWAL_COUNT");
    }

    @Test
    @DisplayName("唯一PUBLISHED ENABLED精确ruleJson匹配创建冻结新合同和旧新差异的草稿")
    void shouldCreateDraftFromOneImmutableMatchingVersion()
    {
        HrSignBusinessEvent event = renewEvent();
        OaSignPlanVersion version = version(
                "{\"renewalContractTypeCode\":\"LABOR_CONTRACT\","
                        + "\"renewalContractTermCode\":\"FIXED_TERM\"}",
                "PUBLISHED", "ENABLED");
        when(versionMapper.selectPublishedMatchingCandidates("RENEWAL", 1171L, 301L))
                .thenReturn(List.of(version));

        OaSignDraftDecision decision = rule.decide(event);

        assertThat(decision.getReasonCodes()).isEmpty();
        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.CREATE_DRAFT);
        assertThat(decision.getPlanVersionId()).isEqualTo(901L);
        assertThat(decision.getRiskLevel()).isEqualTo("LOW");
        OaSignPackage draft = decision.getDraftPackage();
        assertThat(draft.getScenario()).isEqualTo("RENEWAL");
        assertThat(draft.getEmploymentType()).isEqualTo("LABOR_CONTRACT");
        assertThat(draft.getContractStartDate()).isEqualTo("2026-08-02");
        assertThat(draft.getContractEndDate()).isEqualTo("2027-08-01");
        assertThat(draft.getPreviousContractEndDate()).isEqualTo("2026-08-01");
        assertThat(draft.getPreviousEmploymentType()).isEqualTo("LABOR_CONTRACT");
        assertThat(draft.getLegalEntityIdSnapshot()).isNull();
        assertThat(draft.getLegalEntityCodeSnapshot()).isNull();
        assertThat(draft.getLegalEntityNameSnapshot()).isNull();
        assertThat(draft.getPreviousLegalEntityIdSnapshot()).isEqualTo(301L);
        assertThat(draft.getPreviousRenewalCount()).isEqualTo(2);
        assertThat(draft.getRenewalCount()).isEqualTo(3);
        assertThat(draft.getSalaryTotal()).isEqualByComparingTo("8000.00");
        assertThat(draft.getRemark()).contains(
                "oldContractStartDate=2025-08-02",
                "oldContractEndDate=2026-08-01",
                "oldContractTypeCode=LABOR_CONTRACT",
                "oldContractTermCode=FIXED_TERM",
                "newContractTermCode=FIXED_TERM",
                "oldLegalEntityId=301", "oldLegalEntityCode=LE-SH",
                "newLegalEntityId=301", "newLegalEntityCode=LE-SH",
                "oldRenewalCount=2", "newRenewalCount=3");
        assertThat(draft.getStatus()).isEqualTo(OaSignPackageStatus.DRAFT);
    }

    @Test
    @DisplayName("候选0和N分别PLAN_NOT_FOUND与PLAN_CONFLICT且草稿状态不猜")
    void shouldHandleZeroAndMultipleMatchingVersions()
    {
        HrSignBusinessEvent event = renewEvent();
        when(versionMapper.selectPublishedMatchingCandidates("RENEWAL", 1171L, 301L))
                .thenReturn(List.of());
        assertThat(rule.decide(event).getReasonCodes()).containsExactly("PLAN_NOT_FOUND");

        when(versionMapper.selectPublishedMatchingCandidates("RENEWAL", 1171L, 301L))
                .thenReturn(List.of(version(explicitRule(), "PUBLISHED", "ENABLED"),
                        version(explicitRule(), "PUBLISHED", "ENABLED")));
        OaSignDraftDecision conflict = rule.decide(event);
        assertThat(conflict.getAction()).isEqualTo(OaSignDraftDecision.Action.NEEDS_DATA);
        assertThat(conflict.getReasonCodes()).containsExactly("PLAN_CONFLICT");
    }

    @Test
    @DisplayName("只接受PUBLISHED ENABLED且支持HR全局方案范围")
    void shouldFilterCandidateVersionStateAndScope()
    {
        OaSignPlanVersion draft = version(explicitRule(), "DRAFT", "ENABLED");
        OaSignPlanVersion disabled = version(explicitRule(), "PUBLISHED", "DISABLED");
        OaSignPlanVersion wrongScenario = version(explicitRule(), "PUBLISHED", "ENABLED");
        wrongScenario.setScenario("ONBOARD");
        OaSignPlanVersion wrongShop = version(explicitRule(), "PUBLISHED", "ENABLED");
        wrongShop.setShopDeptId(999L);
        OaSignPlanVersion valid = version(explicitRule(), "PUBLISHED", "ENABLED");
        valid.setVersionId(999L);
        when(versionMapper.selectPublishedMatchingCandidates("RENEWAL", 1171L, 301L))
                .thenReturn(List.of(draft, disabled, wrongScenario, wrongShop, valid));

        OaSignDraftDecision decision = rule.decide(renewEvent());

        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.CREATE_DRAFT);
        assertThat(decision.getPlanVersionId()).isEqualTo(999L);
    }

    @Test
    @DisplayName("明确legacy合同类型期限键兼容但冲突字段使规则无效")
    void shouldSupportExplicitLegacyKeysAndRejectConflicts()
    {
        OaSignPlanVersion legacy = version(
                "{\"employmentType\":\"劳动合同\",\"contractTerm\":\"固定期限\"}",
                "PUBLISHED", "ENABLED");
        when(versionMapper.selectPublishedMatchingCandidates("RENEWAL", 1171L, 301L))
                .thenReturn(List.of(legacy));
        assertThat(rule.decide(renewEvent()).getAction())
                .isEqualTo(OaSignDraftDecision.Action.CREATE_DRAFT);

        OaSignPlanVersion conflict = version(
                "{\"renewalContractTypeCode\":\"LABOR_CONTRACT\","
                        + "\"renewalContractTermCode\":\"FIXED_TERM\","
                        + "\"employmentType\":\"劳务合同\"}",
                "PUBLISHED", "ENABLED");
        when(versionMapper.selectPublishedMatchingCandidates("RENEWAL", 1171L, 301L))
                .thenReturn(List.of(conflict));
        assertThat(rule.decide(renewEvent()).getReasonCodes())
                .containsExactly("PLAN_RULE_INVALID");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"renewalContractTypeCode\":\"SERVICE_CONTRACT\",\"renewalContractTermCode\":\"FIXED_TERM\"}",
            "{\"renewalContractTypeCode\":\"LABOR_CONTRACT\",\"renewalContractTermCode\":\"OPEN_ENDED\"}",
            "{}", "not-json"
    })
    @DisplayName("ruleJson不精确匹配续签合同代码时不按planName猜")
    void shouldNotGuessPlanFromName(String ruleJson)
    {
        OaSignPlanVersion version = version(ruleJson, "PUBLISHED", "ENABLED");
        version.setPlanName("劳动合同固定期限续签");
        when(versionMapper.selectPublishedMatchingCandidates("RENEWAL", 1171L, 301L))
                .thenReturn(List.of(version));

        OaSignDraftDecision decision = rule.decide(renewEvent());

        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.NEEDS_DATA);
        assertThat(decision.getReasonCodes()).containsAnyOf(
                "PLAN_NOT_FOUND", "PLAN_RULE_INVALID");
    }

    @Test
    @DisplayName("续签草稿校验完成后无需审核即可进入待发送")
    void shouldKeepAllCreatedDraftsReadyToSendWithoutReview() throws Exception
    {
        String source = Files.readString(repoFile(
                "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/"
                        + "OaSignTaskOrchestrator.java"), StandardCharsets.UTF_8);
        assertThat(source).contains("OaSignTaskStatus.READY_TO_SEND")
                .doesNotContain("transition(taskId, OaSignTaskStatus.WAITING_HR_CONFIRM");
    }

    private HrSignBusinessEvent renewEvent()
    {
        HrEmployeeSigningSnapshot before = snapshot();
        before.setContractStartDate(LocalDate.of(2025, 8, 2));
        before.setContractEndDate(LocalDate.of(2026, 8, 1));
        before.setRenewalCount(2);
        HrEmployeeSigningSnapshot after = snapshot();
        after.setContractStartDate(LocalDate.of(2026, 8, 2));
        after.setContractEndDate(LocalDate.of(2027, 8, 1));
        after.setRenewalCount(3);

        HrSignBusinessEvent event = event(before, after);
        event.getAttributes().put("actionType", "RENEWAL_CONFIRMED");
        event.getAttributes().put("decision", "RENEW");
        event.getAttributes().put("oldContractEndDate", "2026-08-01");
        event.getAttributes().put("oldRenewalCount", 2);
        event.getAttributes().put("newRenewalCount", 3);
        return event;
    }

    private HrSignBusinessEvent declineEvent()
    {
        HrEmployeeSigningSnapshot snapshot = snapshot();
        snapshot.setContractStartDate(LocalDate.of(2025, 8, 2));
        snapshot.setContractEndDate(LocalDate.of(2026, 8, 1));
        snapshot.setRenewalCount(2);
        HrSignBusinessEvent event = event(snapshot, snapshot());
        event.getAfterSnapshot().setContractStartDate(LocalDate.of(2025, 8, 2));
        event.getAfterSnapshot().setContractEndDate(LocalDate.of(2026, 8, 1));
        event.getAfterSnapshot().setRenewalCount(2);
        event.getAttributes().put("actionType", "RENEWAL_DECLINED");
        event.getAttributes().put("decision", "DECLINE");
        event.getAttributes().put("oldContractEndDate", "2026-08-01");
        event.getAttributes().put("oldRenewalCount", 2);
        event.getAttributes().put("newRenewalCount", 2);
        return event;
    }

    private HrSignBusinessEvent event(HrEmployeeSigningSnapshot before,
            HrEmployeeSigningSnapshot after)
    {
        HrSignBusinessEvent event = new HrSignBusinessEvent();
        event.setEventId("event-701");
        event.setScenario("RENEWAL");
        event.setEmployeeId(201L);
        event.setSourceType("HR_LIFECYCLE_ACTION");
        event.setSourceBusinessId("701");
        event.setSourceEventVersion(1L);
        event.setBeforeSnapshot(before);
        event.setAfterSnapshot(after);
        event.setAttributes(new LinkedHashMap<>());
        return event;
    }

    private HrEmployeeSigningSnapshot snapshot()
    {
        HrEmployeeSigningSnapshot snapshot = new HrEmployeeSigningSnapshot();
        snapshot.setEmployeeId(201L);
        snapshot.setEmployeeNo("E0201");
        snapshot.setEmployeeName("张三");
        snapshot.setPhone("13800000000");
        snapshot.setIdType("CN_ID_CARD");
        snapshot.setIdNumber("310101199001011234");
        snapshot.setCurrentAddress("上海市徐汇区");
        snapshot.setEmployeeStatus("在职");
        snapshot.setEmployeeCategory("FULL_TIME");
        snapshot.setShopDeptId(1171L);
        snapshot.setShopDeptName("徐汇门店");
        snapshot.setDeptId(1171L);
        snapshot.setDeptName("徐汇门店");
        snapshot.setLegalEntityId(301L);
        snapshot.setLegalEntityCode("LE-SH");
        snapshot.setLegalEntityName("上海示例有限公司");
        snapshot.setPostId(401L);
        snapshot.setPostCode("SALES");
        snapshot.setPostName("销售顾问");
        snapshot.setJobGradeCode("P3");
        snapshot.setJobGradeName("P3");
        snapshot.setWorkLocation("上海");
        snapshot.setContractTypeCode("LABOR_CONTRACT");
        snapshot.setContractTermCode("FIXED_TERM");
        snapshot.setSocialTypeCode("SOCIAL_INSURED");
        snapshot.setBaseSalary(new BigDecimal("5000.00"));
        snapshot.setPostSalary(new BigDecimal("2000.00"));
        snapshot.setFieldAllowance(new BigDecimal("300.00"));
        snapshot.setPerformanceSalary(new BigDecimal("700.00"));
        snapshot.setSalaryTotal(new BigDecimal("8000.00"));
        snapshot.setSalaryVersion("2026-V1");
        return snapshot;
    }

    private OaSignPlanVersion version(String ruleJson, String publishStatus,
            String matchingStatus)
    {
        OaSignPlanVersion version = new OaSignPlanVersion();
        version.setVersionId(901L);
        version.setPlanId(77L);
        version.setPlanName("名称不得参与匹配");
        version.setScenario("RENEWAL");
        version.setShopDeptId(0L);
        version.setLegalEntityId(301L);
        version.setLegalEntityName("上海示例有限公司");
        version.setRuleJson(ruleJson);
        version.setPublishStatus(publishStatus);
        version.setMatchingStatus(matchingStatus);
        return version;
    }

    private String explicitRule()
    {
        return "{\"renewalContractTypeCode\":\"LABOR_CONTRACT\","
                + "\"renewalContractTermCode\":\"FIXED_TERM\"}";
    }

    private static Stream<Arguments> immutableRenewalMutations()
    {
        return Stream.of(
                mutation("employeeNo", "setEmployeeNo", String.class, "E-X"),
                mutation("employeeName", "setEmployeeName", String.class, "李四"),
                mutation("phone", "setPhone", String.class, "13900000000"),
                mutation("idType", "setIdType", String.class, "PASSPORT"),
                mutation("idNumber", "setIdNumber", String.class, "P-X"),
                mutation("currentAddress", "setCurrentAddress", String.class, "北京市"),
                mutation("employeeStatus", "setEmployeeStatus", String.class, "停职"),
                mutation("employeeCategory", "setEmployeeCategory", String.class, "PART_TIME"),
                mutation("shopDeptId", "setShopDeptId", Long.class, 999L),
                mutation("shopDeptName", "setShopDeptName", String.class, "其他门店"),
                mutation("deptId", "setDeptId", Long.class, 999L),
                mutation("deptName", "setDeptName", String.class, "其他部门"),
                mutation("postId", "setPostId", Long.class, 999L),
                mutation("postCode", "setPostCode", String.class, "OTHER"),
                mutation("postName", "setPostName", String.class, "其他岗位"),
                mutation("jobGradeCode", "setJobGradeCode", String.class, "P9"),
                mutation("jobGradeName", "setJobGradeName", String.class, "P9"),
                mutation("directSupervisorId", "setDirectSupervisorId", Long.class, 901L),
                mutation("directSupervisorName", "setDirectSupervisorName", String.class, "主管甲"),
                mutation("departmentSupervisorId", "setDepartmentSupervisorId", Long.class, 902L),
                mutation("departmentSupervisorName", "setDepartmentSupervisorName", String.class, "主管乙"),
                mutation("workLocation", "setWorkLocation", String.class, "北京"),
                mutation("workCityLevel", "setWorkCityLevel", String.class, "一线"),
                mutation("socialTypeCode", "setSocialTypeCode", String.class, "NO_SOCIAL"),
                mutation("entryDate", "setEntryDate", LocalDate.class, LocalDate.of(2020, 1, 1)),
                mutation("probationStartDate", "setProbationStartDate", LocalDate.class,
                        LocalDate.of(2020, 1, 2)),
                mutation("probationEndDate", "setProbationEndDate", LocalDate.class,
                        LocalDate.of(2020, 4, 1)),
                mutation("actualRegularizationDate", "setActualRegularizationDate", LocalDate.class,
                        LocalDate.of(2020, 4, 2)),
                mutation("leaveDate", "setLeaveDate", LocalDate.class, LocalDate.of(2026, 9, 1)),
                mutation("baseSalary", "setBaseSalary", BigDecimal.class, new BigDecimal("5000.01")),
                mutation("postSalary", "setPostSalary", BigDecimal.class, new BigDecimal("2000.01")),
                mutation("fieldAllowance", "setFieldAllowance", BigDecimal.class, new BigDecimal("300.01")),
                mutation("performanceSalary", "setPerformanceSalary", BigDecimal.class,
                        new BigDecimal("700.01")),
                mutation("salaryTotal", "setSalaryTotal", BigDecimal.class, new BigDecimal("8000.01")),
                mutation("salaryVersion", "setSalaryVersion", String.class, "2026-V2"));
    }

    private static Stream<Arguments> declineSnapshotMutations()
    {
        return Stream.of(
                mutation("identity", "setIdNumber", String.class, "P-X"),
                mutation("organization", "setDeptId", Long.class, 999L),
                mutation("legal", "setLegalEntityId", Long.class, 302L),
                mutation("post", "setPostId", Long.class, 999L),
                mutation("social", "setSocialTypeCode", String.class, "NO_SOCIAL"),
                mutation("date", "setEntryDate", LocalDate.class, LocalDate.of(2020, 1, 1)),
                mutation("salary", "setSalaryTotal", BigDecimal.class, new BigDecimal("8000.01")),
                mutation("contract", "setContractTypeCode", String.class, "SERVICE_CONTRACT"));
    }

    private static Arguments mutation(String field, String setter,
            Class<?> parameterType, Object value)
    {
        return Arguments.of(field, setter, parameterType, value);
    }

    private static Path repoFile(String relativePath)
    {
        Path base = Paths.get(System.getProperty("user.dir"));
        Path path = base.resolve(relativePath);
        if (!Files.exists(path))
        {
            path = base.resolve("../..").resolve(relativePath).normalize();
        }
        return path;
    }
}
