package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.api.domain.HrEmployeeSigningSnapshot;
import com.erp.oa.api.domain.HrSignBusinessEvent;
import com.erp.system.domain.SysHrLifecycleAction;
import com.erp.system.domain.SysHrSignEventOutbox;
import com.erp.system.domain.dto.HrOffboardingCompletionStatus;
import com.erp.system.domain.dto.HrOffboardingConfirmRequest;
import com.erp.system.domain.dto.HrOffboardingNonCompeteDecision;
import com.erp.system.domain.dto.HrOffboardingRiskConfirmation;
import com.erp.system.domain.dto.HrOffboardingType;
import com.erp.system.mapper.SysConfigMapper;
import com.erp.system.mapper.SysDeptMapper;
import com.erp.system.mapper.SysHrLifecycleActionMapper;
import com.erp.system.mapper.SysHrRenewalGuardMapper;
import com.erp.system.mapper.SysHrSignEventOutboxMapper;
import com.erp.system.mapper.SysPostMapper;
import com.erp.system.mapper.SysUserMapper;
import com.erp.system.mapper.SysUserPostMapper;
import com.erp.system.mapper.SysUserProfileMapper;
import com.erp.system.service.ISysUserShopService;

@DisplayName("HR离职生命周期")
class HrOffboardingLifecycleTest
{
    private static final Instant NOW = Instant.parse("2026-07-13T02:30:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 13);
    private static final String RISK_STATEMENT =
            "我已核对离职类型、最后工作日、工资结算、资产交接、竞业决定及补偿信息，并确认立即执行离职及账号停用";

    private SysConfigMapper configMapper;
    private SysUserProfileMapper profileMapper;
    private SysHrLifecycleActionMapper actionMapper;
    private SysHrSignEventOutboxMapper outboxMapper;
    private SysUserMapper userMapper;
    private SysUserPostMapper userPostMapper;
    private ISysUserShopService userShopService;
    private ObjectMapper objectMapper;
    private HrLifecycleServiceImpl service;

    @BeforeEach
    void setUp()
    {
        configMapper = mock(SysConfigMapper.class);
        profileMapper = mock(SysUserProfileMapper.class);
        actionMapper = mock(SysHrLifecycleActionMapper.class);
        outboxMapper = mock(SysHrSignEventOutboxMapper.class);
        userMapper = mock(SysUserMapper.class);
        userPostMapper = mock(SysUserPostMapper.class);
        userShopService = mock(ISysUserShopService.class);
        objectMapper = JsonMapper.builder().findAndAddModules().build();
        service = new HrLifecycleServiceImpl(configMapper, profileMapper, actionMapper,
                outboxMapper, mock(SysHrRenewalGuardMapper.class), mock(SysPostMapper.class),
                mock(SysDeptMapper.class), userMapper, userPostMapper, userShopService,
                objectMapper, org.mockito.Mockito.mock(com.erp.system.service.impl.HrSalarySourceService.class));
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(NOW, ZoneId.of("UTC")));
    }

    @Test
    @DisplayName("离职业务日固定使用上海自然日")
    void resolvesShanghaiBusinessDate()
    {
        assertThat(service.offboardingBusinessDate()).isEqualTo(TODAY);
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(
                Instant.parse("2026-07-12T15:59:59Z"), ZoneId.of("America/Los_Angeles")));
        assertThat(service.offboardingBusinessDate()).isEqualTo(LocalDate.of(2026, 7, 12));
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(
                Instant.parse("2026-07-12T16:00:00Z"), ZoneId.of("America/Los_Angeles")));
        assertThat(service.offboardingBusinessDate()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("未来最后工作日固定文案拒绝且零业务副作用")
    void rejectsFutureDateWithoutSideEffects()
    {
        when(configMapper.selectConfiguredSignHrUserId()).thenReturn(88L);
        HrOffboardingConfirmRequest request = validRequest();
        request.setLastWorkingDate(TODAY.plusDays(1));

        assertThatThrownBy(() -> confirm(request, 88L, false))
                .isInstanceOf(ServiceException.class)
                .hasMessage("未来最后工作日的离职暂不能确认，请在最后工作日操作");

        verify(profileMapper, never()).lockSigningProfileByUserId(any());
        verify(profileMapper, never()).selectSigningSnapshotByUserIdForUpdate(any());
        verify(actionMapper, never()).insertAction(any());
        verify(outboxMapper, never()).insertOutbox(any());
    }

    @Test
    @DisplayName("管理员不是配置 HR 时也不能确认离职")
    void rejectsAdminWhoIsNotConfiguredHr()
    {
        when(configMapper.selectConfiguredSignHrUserId()).thenReturn(88L);
        assertThatThrownBy(() -> confirm(validRequest(), 77L, true))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("配置HR");
        verify(profileMapper, never()).lockSigningProfileByUserId(any());
    }

    @Test
    @DisplayName("当天标准主动离职原子冻结快照并只写一个 action 和 PENDING outbox")
    void confirmsStandardOffboarding()
            throws Exception
    {
        stubFreshOffboarding(currentSnapshot());

        assertThat(confirm(validRequest(), 88L, false)).isEqualTo(901L);

        ArgumentCaptor<HrEmployeeSigningSnapshot> profile =
                ArgumentCaptor.forClass(HrEmployeeSigningSnapshot.class);
        ArgumentCaptor<SysHrLifecycleAction> action =
                ArgumentCaptor.forClass(SysHrLifecycleAction.class);
        ArgumentCaptor<SysHrSignEventOutbox> outbox =
                ArgumentCaptor.forClass(SysHrSignEventOutbox.class);
        verify(profileMapper).updateOffboardingProfile(profile.capture(), eq("在职"),
                eq(null), eq("配置HR"));
        verify(userMapper).disableUserForOffboarding(9L, "0", "配置HR");
        verify(actionMapper).insertAction(action.capture());
        verify(outboxMapper).insertOutbox(outbox.capture());
        verify(userPostMapper, never()).deleteUserPostByUserId(any());
        org.mockito.InOrder writeOrder = org.mockito.Mockito.inOrder(
                actionMapper, profileMapper, userMapper, outboxMapper);
        writeOrder.verify(actionMapper).insertAction(any());
        writeOrder.verify(profileMapper).updateOffboardingProfile(
                any(), any(), any(), any());
        writeOrder.verify(userMapper).disableUserForOffboarding(any(), any(), any());
        writeOrder.verify(outboxMapper).insertOutbox(any());

        HrEmployeeSigningSnapshot after = profile.getValue();
        assertThat(after.getEmployeeStatus()).isEqualTo("离职");
        assertThat(after.getAccountStatus()).isEqualTo("1");
        assertThat(after.getLeaveDate()).isEqualTo(TODAY);
        assertThat(after.getOffboardingType()).isEqualTo("VOLUNTARY_EXPECTED");
        assertThat(after.getLeaveReason()).isEqualTo("按计划主动离职");

        SysHrLifecycleAction recorded = action.getValue();
        assertThat(recorded.getActionType()).isEqualTo("OFFBOARD_CONFIRMED");
        assertThat(recorded.getSourceType()).isEqualTo("HR_OFFBOARDING");
        assertThat(recorded.getSourceBusinessId()).isEqualTo("offboard-request-1");
        assertThat(recorded.getEffectiveDate()).isEqualTo(TODAY);
        assertThat(recorded.getActualConfirmTime()).isEqualTo(Date.from(NOW));
        assertThat(recorded.getRiskLevel()).isEqualTo("LOW");
        assertThat(recorded.getBeforeSnapshotJson()).contains(
                "\"employeeStatus\":\"在职\"", "\"accountStatus\":\"0\"");
        assertThat(recorded.getAfterSnapshotJson()).contains(
                "\"employeeStatus\":\"离职\"", "\"accountStatus\":\"1\"",
                "\"leaveDate\":\"2026-07-13\"");

        assertThat(outbox.getValue().getActionId()).isEqualTo(901L);
        assertThat(outbox.getValue().getEventVersion()).isEqualTo(1L);
        assertThat(outbox.getValue().getStatus()).isEqualTo("PENDING");
        HrSignBusinessEvent event = objectMapper.readValue(
                outbox.getValue().getPayloadJson(), HrSignBusinessEvent.class);
        assertThat(event.getScenario()).isEqualTo("OFFBOARD");
        assertThat(event.getSourceType()).isEqualTo("HR_LIFECYCLE_ACTION");
        assertThat(event.getSourceBusinessId()).isEqualTo("901");
        assertThat(event.getAttributes())
                .containsEntry("actionType", "OFFBOARD_CONFIRMED")
                .containsEntry("sourceActionId", 901)
                .containsEntry("sourceActionVersion", 1)
                .containsEntry("effectiveDate", "2026-07-13")
                .containsEntry("historicalSupplement", false)
                .containsEntry("riskLevel", "LOW");
    }

    @Test
    @DisplayName("所有服务端离职风险条件都派生 HIGH 并保存结构化确认")
    void derivesAllHighRiskCodes()
    {
        stubFreshOffboarding(currentSnapshot());
        HrOffboardingConfirmRequest request = validRequest();
        request.setLastWorkingDate(TODAY.minusDays(1));
        request.setOffboardingType(HrOffboardingType.TERMINATION);
        request.setSalarySettlementStatus(HrOffboardingCompletionStatus.PENDING);
        request.setAssetHandoverStatus(HrOffboardingCompletionStatus.PENDING);
        request.setNonCompeteDecision(HrOffboardingNonCompeteDecision.REQUIRED);
        request.setCompensationAmount(new BigDecimal("1000.00"));
        request.setCompensationNote("协商补偿");
        request.setRiskConfirmation(validConfirmation(request));

        assertThat(confirm(request, 88L, false)).isEqualTo(901L);

        ArgumentCaptor<SysHrLifecycleAction> action =
                ArgumentCaptor.forClass(SysHrLifecycleAction.class);
        verify(actionMapper).insertAction(action.capture());
        assertThat(action.getValue().getRiskLevel()).isEqualTo("HIGH");
        assertThat(action.getValue().getRiskCodesJson()).contains(
                "HISTORICAL_OFFBOARDING", "NON_STANDARD_OFFBOARDING_TYPE",
                "SALARY_SETTLEMENT_PENDING", "ASSET_HANDOVER_PENDING",
                "NON_COMPETE_REVIEW_REQUIRED", "COMPENSATION_REVIEW_REQUIRED");
        assertThat(action.getValue().getRiskConfirmationJson()).contains(
                "\"confirmed\":true", RISK_STATEMENT, "高风险信息已核对");
        assertThat(action.getValue().getHistoricalReason()).isEqualTo("高风险信息已核对");
    }

    @Test
    @DisplayName("每一种非标准离职类型都由服务端判定高风险")
    void derivesHighRiskForEveryNonStandardType()
    {
        for (HrOffboardingType type : HrOffboardingType.values())
        {
            if (type == HrOffboardingType.VOLUNTARY_EXPECTED) continue;
            setUp();
            stubFreshOffboarding(currentSnapshot());
            HrOffboardingConfirmRequest request = validRequest();
            request.setOffboardingType(type);
            request.setRiskConfirmation(validConfirmation(request));
            confirm(request, 88L, false);
            ArgumentCaptor<SysHrLifecycleAction> action =
                    ArgumentCaptor.forClass(SysHrLifecycleAction.class);
            verify(actionMapper).insertAction(action.capture());
            assertThat(action.getValue().getRiskCodesJson())
                    .as(type.name()).contains("NON_STANDARD_OFFBOARDING_TYPE");
        }
    }

    @Test
    @DisplayName("高风险缺少确认或结构化确认任一字段不匹配都拒绝")
    void rejectsMissingOrMismatchedHighRiskConfirmation()
    {
        when(configMapper.selectConfiguredSignHrUserId()).thenReturn(88L);
        when(profileMapper.lockSigningProfileByUserId(9L)).thenReturn(9L);
        when(profileMapper.selectSigningSnapshotByUserIdForUpdate(9L)).thenReturn(currentSnapshot());
        HrOffboardingConfirmRequest request = validRequest();
        request.setSalarySettlementStatus(HrOffboardingCompletionStatus.PENDING);

        assertThatThrownBy(() -> confirm(request, 88L, false))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("二次确认");

        request.setRiskConfirmation(validConfirmation(request));
        request.getRiskConfirmation().setRiskStatement("前端自定义文案");
        assertThatThrownBy(() -> confirm(request, 88L, false))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("二次确认内容");
        verify(actionMapper, never()).insertAction(any());
    }

    @Test
    @DisplayName("待入职、已离职、已停用或冲突 leaveDate 不得创建新离职动作")
    void rejectsInvalidCurrentState()
    {
        for (HrEmployeeSigningSnapshot snapshot : List.of(
                snapshotWithState("待入职", "0", null),
                snapshotWithState("离职", "1", TODAY),
                snapshotWithState("在职", "1", null),
                snapshotWithState("在职", "0", TODAY.minusDays(1))))
        {
            setUp();
            when(configMapper.selectConfiguredSignHrUserId()).thenReturn(88L);
            when(profileMapper.lockSigningProfileByUserId(9L)).thenReturn(9L);
            when(profileMapper.selectSigningSnapshotByUserIdForUpdate(9L)).thenReturn(snapshot);
            assertThatThrownBy(() -> confirm(validRequest(), 88L, false))
                    .isInstanceOf(ServiceException.class);
            verify(actionMapper, never()).insertAction(any());
            verify(outboxMapper, never()).insertOutbox(any());
        }
    }

    @Test
    @DisplayName("相同 requestId 与相同 payload 严格重放只返回原 actionId")
    void replaysIdenticalRequestWithoutWrites() throws Exception
    {
        HrOffboardingConfirmRequest request = validRequest();
        HrEmployeeSigningSnapshot current = currentSnapshot();
        applyRequest(current, request);
        when(configMapper.selectConfiguredSignHrUserId()).thenReturn(88L);
        when(profileMapper.lockSigningProfileByUserId(9L)).thenReturn(9L);
        when(profileMapper.selectSigningSnapshotByUserIdForUpdate(9L)).thenReturn(current);
        when(actionMapper.selectByRequestIdForUpdate(request.getRequestId()))
                .thenReturn(completedAction(request, current));

        assertThat(confirm(request, 88L, false)).isEqualTo(901L);
        verify(actionMapper, never()).insertAction(any());
        verify(profileMapper, never()).updateOffboardingProfile(any(), any(), any(), any());
        verify(userMapper, never()).disableUserForOffboarding(any(), any(), any());
        verify(outboxMapper, never()).insertOutbox(any());
    }

    @Test
    @DisplayName("相同 requestId 的 payload 或当前状态不一致必须拒绝")
    void rejectsReplayMismatch() throws Exception
    {
        HrOffboardingConfirmRequest request = validRequest();
        HrEmployeeSigningSnapshot frozenAfter = currentSnapshot();
        applyRequest(frozenAfter, request);
        HrEmployeeSigningSnapshot current = objectMapper.convertValue(
                frozenAfter, HrEmployeeSigningSnapshot.class);
        current.setLeaveDate(TODAY.minusDays(1));
        when(configMapper.selectConfiguredSignHrUserId()).thenReturn(88L);
        when(profileMapper.lockSigningProfileByUserId(9L)).thenReturn(9L);
        when(profileMapper.selectSigningSnapshotByUserIdForUpdate(9L)).thenReturn(current);
        when(actionMapper.selectByRequestIdForUpdate(request.getRequestId()))
                .thenReturn(completedAction(request, frozenAfter));

        assertThatThrownBy(() -> confirm(request, 88L, false))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("payload不一致");
        verify(outboxMapper, never()).insertOutbox(any());
    }

    private Long confirm(HrOffboardingConfirmRequest request, Long operatorId, boolean admin)
    {
        return service.confirmOffboarding(9L, request, operatorId, "配置HR", admin,
                "10.0.0.8", "test-agent");
    }

    private void stubFreshOffboarding(HrEmployeeSigningSnapshot snapshot)
    {
        when(configMapper.selectConfiguredSignHrUserId()).thenReturn(88L);
        when(profileMapper.lockSigningProfileByUserId(9L)).thenReturn(9L);
        when(profileMapper.selectSigningSnapshotByUserIdForUpdate(9L)).thenReturn(snapshot);
        when(actionMapper.selectOffboardingActionsForUpdate(9L,
                snapshot.getLeaveDate() == null ? TODAY : snapshot.getLeaveDate()))
                .thenReturn(List.of());
        doAnswer(invocation -> {
            SysHrLifecycleAction action = invocation.getArgument(0);
            action.setActionId(901L);
            return 1;
        }).when(actionMapper).insertAction(any());
        when(profileMapper.updateOffboardingProfile(any(), any(), any(), any())).thenReturn(1);
        when(userMapper.disableUserForOffboarding(any(), any(), any())).thenReturn(1);
        when(outboxMapper.insertOutbox(any())).thenReturn(1);
    }

    private HrEmployeeSigningSnapshot currentSnapshot()
    {
        return snapshotWithState("在职", "0", null);
    }

    private HrEmployeeSigningSnapshot snapshotWithState(
            String employeeStatus, String accountStatus, LocalDate leaveDate)
    {
        HrEmployeeSigningSnapshot snapshot = new HrEmployeeSigningSnapshot();
        snapshot.setEmployeeId(9L);
        snapshot.setEmployeeNo("E0009");
        snapshot.setEmployeeName("张三");
        snapshot.setEmployeeStatus(employeeStatus);
        snapshot.setAccountStatus(accountStatus);
        snapshot.setLeaveDate(leaveDate);
        snapshot.setShopDeptId(20L);
        snapshot.setShopDeptName("上海一店");
        snapshot.setDeptId(20L);
        snapshot.setDeptName("上海一店");
        snapshot.setPostId(401L);
        snapshot.setPostCode("SALES");
        snapshot.setPostName("销售顾问");
        snapshot.setEntryDate(LocalDate.of(2024, 1, 1));
        return snapshot;
    }

    private HrOffboardingConfirmRequest validRequest()
    {
        HrOffboardingConfirmRequest request = new HrOffboardingConfirmRequest();
        request.setRequestId("offboard-request-1");
        request.setLastWorkingDate(TODAY);
        request.setOffboardingType(HrOffboardingType.VOLUNTARY_EXPECTED);
        request.setReason("按计划主动离职");
        request.setSalarySettlementStatus(HrOffboardingCompletionStatus.COMPLETED);
        request.setAssetHandoverStatus(HrOffboardingCompletionStatus.COMPLETED);
        request.setNonCompeteDecision(HrOffboardingNonCompeteDecision.NOT_APPLICABLE);
        request.setCompensationAmount(new BigDecimal("0.00"));
        return request;
    }

    private HrOffboardingRiskConfirmation validConfirmation(HrOffboardingConfirmRequest request)
    {
        HrOffboardingRiskConfirmation confirmation = new HrOffboardingRiskConfirmation();
        confirmation.setConfirmed(true);
        confirmation.setEmployeeId(9L);
        confirmation.setEmployeeName("张三");
        confirmation.setOffboardingType(request.getOffboardingType());
        confirmation.setLastWorkingDate(request.getLastWorkingDate());
        confirmation.setOperationDate(TODAY);
        confirmation.setSalarySettlementStatus(request.getSalarySettlementStatus());
        confirmation.setAssetHandoverStatus(request.getAssetHandoverStatus());
        confirmation.setNonCompeteDecision(request.getNonCompeteDecision());
        confirmation.setCompensationAmount(request.getCompensationAmount());
        confirmation.setRiskStatement(RISK_STATEMENT);
        confirmation.setReason("高风险信息已核对");
        return confirmation;
    }

    private void applyRequest(HrEmployeeSigningSnapshot snapshot,
            HrOffboardingConfirmRequest request)
    {
        snapshot.setEmployeeStatus("离职");
        snapshot.setAccountStatus("1");
        snapshot.setLeaveDate(request.getLastWorkingDate());
        snapshot.setOffboardingType(request.getOffboardingType().name());
        snapshot.setLeaveReason(request.getReason());
        snapshot.setSalarySettlementStatus(request.getSalarySettlementStatus().name());
        snapshot.setAssetHandoverStatus(request.getAssetHandoverStatus().name());
        snapshot.setNonCompeteDecision(request.getNonCompeteDecision().name());
        snapshot.setCompensationAmount(request.getCompensationAmount());
        snapshot.setCompensationNote(request.getCompensationNote());
    }

    private SysHrLifecycleAction completedAction(HrOffboardingConfirmRequest request,
            HrEmployeeSigningSnapshot after) throws Exception
    {
        HrEmployeeSigningSnapshot before = currentSnapshot();
        SysHrLifecycleAction action = new SysHrLifecycleAction();
        action.setActionId(901L);
        action.setActionType("OFFBOARD_CONFIRMED");
        action.setEmployeeId(9L);
        action.setSourceType("HR_OFFBOARDING");
        action.setSourceBusinessId(request.getRequestId());
        action.setRequestId(request.getRequestId());
        action.setEffectiveDate(request.getLastWorkingDate());
        action.setActualConfirmTime(Date.from(NOW));
        action.setBeforeSnapshotJson(objectMapper.writeValueAsString(before));
        action.setAfterSnapshotJson(objectMapper.writeValueAsString(after));
        action.setVersion(1L);
        return action;
    }
}
