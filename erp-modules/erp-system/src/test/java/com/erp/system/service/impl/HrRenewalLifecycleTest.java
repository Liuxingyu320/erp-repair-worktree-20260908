package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.api.domain.HrEmployeeSigningSnapshot;
import com.erp.oa.api.domain.HrRenewalGuard;
import com.erp.oa.api.domain.HrSignBusinessEvent;
import com.erp.system.domain.SysHrLifecycleAction;
import com.erp.system.domain.SysHrSignEventOutbox;
import com.erp.system.domain.dto.HrRenewalDecisionRequest;
import com.erp.system.mapper.SysConfigMapper;
import com.erp.system.mapper.SysHrLifecycleActionMapper;
import com.erp.system.mapper.SysHrRenewalGuardMapper;
import com.erp.system.mapper.SysHrSignEventOutboxMapper;
import com.erp.system.mapper.SysPostMapper;
import com.erp.system.mapper.SysUserProfileMapper;
import com.erp.system.mapper.SysUserPostMapper;
import com.erp.system.service.ISysUserShopService;

@DisplayName("HR续签生命周期")
class HrRenewalLifecycleTest
{
    private SysConfigMapper configMapper;
    private SysUserProfileMapper profileMapper;
    private SysHrLifecycleActionMapper actionMapper;
    private SysHrRenewalGuardMapper renewalGuardMapper;
    private SysHrSignEventOutboxMapper outboxMapper;
    private ISysUserShopService userShopService;
    private ObjectMapper objectMapper;
    private HrLifecycleServiceImpl service;

    @BeforeEach
    void setUp()
    {
        configMapper = mock(SysConfigMapper.class);
        profileMapper = mock(SysUserProfileMapper.class);
        actionMapper = mock(SysHrLifecycleActionMapper.class);
        renewalGuardMapper = mock(SysHrRenewalGuardMapper.class);
        outboxMapper = mock(SysHrSignEventOutboxMapper.class);
        userShopService = mock(ISysUserShopService.class);
        objectMapper = JsonMapper.builder().findAndAddModules().build();
        service = new HrLifecycleServiceImpl(configMapper, profileMapper, actionMapper,
                outboxMapper, renewalGuardMapper, mock(SysPostMapper.class),
                mock(com.erp.system.mapper.SysDeptMapper.class),
                mock(com.erp.system.mapper.SysUserMapper.class),
                mock(SysUserPostMapper.class), userShopService, objectMapper, org.mockito.Mockito.mock(com.erp.system.service.impl.HrSalarySourceService.class));
    }

    @Test
    @DisplayName("扫描只追加幂等SYSTEM决策动作且不写档案或outbox")
    void shouldCreateOnlyImmutableSystemDecision() throws Exception
    {
        HrEmployeeSigningSnapshot snapshot = activeSnapshot();
        when(profileMapper.selectSigningSnapshotByUserIdForUpdate(9L)).thenReturn(snapshot);
        when(actionMapper.selectRenewalCycleActionsForUpdate(
                9L, "9:2026-08-01:2")).thenReturn(List.of());
        doAnswer(invocation -> {
            SysHrLifecycleAction action = invocation.getArgument(0);
            action.setActionId(501L);
            return 1;
        }).when(actionMapper).insertAction(any());

        Long actionId = service.createRenewalDecision(9L,
                LocalDate.of(2026, 7, 12), LocalDate.of(2026, 8, 11));

        assertThat(actionId).isEqualTo(501L);
        ArgumentCaptor<SysHrLifecycleAction> captor =
                ArgumentCaptor.forClass(SysHrLifecycleAction.class);
        verify(actionMapper).insertAction(captor.capture());
        SysHrLifecycleAction action = captor.getValue();
        assertThat(action.getActionType()).isEqualTo("RENEWAL_DECISION");
        assertThat(action.getSourceBusinessId()).isEqualTo("9:2026-08-01:2");
        assertThat(action.getRequestId())
                .isEqualTo("RENEWAL_DECISION:9:2026-08-01:2");
        assertThat(action.getBusinessStatus()).isEqualTo("PENDING");
        assertThat(action.getOperatorType()).isEqualTo("SYSTEM");
        assertThat(action.getOperatorUserId()).isNull();
        assertThat(action.getBeforeSnapshotJson()).isEqualTo(action.getAfterSnapshotJson());
        assertThat(objectMapper.readValue(action.getAfterSnapshotJson(),
                HrEmployeeSigningSnapshot.class).getRenewalCount()).isEqualTo(2);
        verify(profileMapper, never()).updateRenewalProfile(any(), any(), any(), any());
        verify(outboxMapper, never()).insertOutbox(any());
    }

    @Test
    @DisplayName("同合同周期已有决策或已解决历史时重复和多实例扫描均不追加")
    void shouldKeepOneDecisionPerContractCycle()
    {
        when(profileMapper.selectSigningSnapshotByUserIdForUpdate(9L))
                .thenReturn(activeSnapshot());
        SysHrLifecycleAction decision = cycleAction(501L, "RENEWAL_DECISION");
        when(actionMapper.selectRenewalCycleActionsForUpdate(
                9L, "9:2026-08-01:2")).thenReturn(List.of(decision));

        assertThat(service.createRenewalDecision(9L,
                LocalDate.of(2026, 7, 12), LocalDate.of(2026, 8, 11)))
                .isEqualTo(501L);
        verify(actionMapper, never()).insertAction(any());

        setUp();
        when(profileMapper.selectSigningSnapshotByUserIdForUpdate(9L))
                .thenReturn(activeSnapshot());
        SysHrLifecycleAction declined = cycleAction(502L, "RENEWAL_DECLINED");
        when(actionMapper.selectRenewalCycleActionsForUpdate(
                9L, "9:2026-08-01:2")).thenReturn(List.of(decision, declined));
        assertThat(service.createRenewalDecision(9L,
                LocalDate.of(2026, 7, 12), LocalDate.of(2026, 8, 11)))
                .isEqualTo(501L);
        verify(actionMapper, never()).insertAction(any());
    }

    @Test
    @DisplayName("服务端复核today和N日边界并排除过期超窗及离职员工")
    void shouldRecheckInclusiveWindowAndEmploymentStatus()
    {
        HrEmployeeSigningSnapshot today = activeSnapshot();
        today.setContractEndDate(LocalDate.of(2026, 7, 12));
        HrEmployeeSigningSnapshot lastDay = activeSnapshot();
        lastDay.setContractEndDate(LocalDate.of(2026, 8, 11));
        HrEmployeeSigningSnapshot expired = activeSnapshot();
        expired.setContractEndDate(LocalDate.of(2026, 7, 11));
        HrEmployeeSigningSnapshot tooFar = activeSnapshot();
        tooFar.setContractEndDate(LocalDate.of(2026, 8, 12));
        HrEmployeeSigningSnapshot departed = activeSnapshot();
        departed.setEmployeeStatus("离职");
        when(profileMapper.selectSigningSnapshotByUserIdForUpdate(9L))
                .thenReturn(today, lastDay, expired, tooFar, departed);
        when(actionMapper.selectRenewalCycleActionsForUpdate(anyLong(), any()))
                .thenReturn(List.of());
        final long[] id = {800L};
        doAnswer(invocation -> {
            SysHrLifecycleAction action = invocation.getArgument(0);
            action.setActionId(++id[0]);
            return 1;
        }).when(actionMapper).insertAction(any());

        LocalDate start = LocalDate.of(2026, 7, 12);
        LocalDate end = LocalDate.of(2026, 8, 11);
        assertThat(service.createRenewalDecision(9L, start, end)).isEqualTo(801L);
        assertThat(service.createRenewalDecision(9L, start, end)).isEqualTo(802L);
        assertThat(service.createRenewalDecision(9L, start, end)).isNull();
        assertThat(service.createRenewalDecision(9L, start, end)).isNull();
        assertThat(service.createRenewalDecision(9L, start, end)).isNull();
        verify(actionMapper, times(2)).insertAction(any());
        verify(outboxMapper, never()).insertOutbox(any());
    }

    @Test
    @DisplayName("多实例同周期撞requestId唯一键后返回并发决策")
    void shouldConvergeConcurrentDecisionInsert()
    {
        when(profileMapper.selectSigningSnapshotByUserIdForUpdate(9L))
                .thenReturn(activeSnapshot());
        when(actionMapper.selectRenewalCycleActionsForUpdate(
                9L, "9:2026-08-01:2")).thenReturn(List.of());
        doThrow(new org.springframework.dao.DuplicateKeyException("concurrent decision"))
                .when(actionMapper).insertAction(any());
        SysHrLifecycleAction concurrent = cycleAction(503L, "RENEWAL_DECISION");
        when(actionMapper.selectByRequestIdForUpdate(
                "RENEWAL_DECISION:9:2026-08-01:2")).thenReturn(concurrent);

        assertThat(service.createRenewalDecision(9L,
                LocalDate.of(2026, 7, 12), LocalDate.of(2026, 8, 11)))
                .isEqualTo(503L);
        verify(outboxMapper, never()).insertOutbox(any());
    }

    @Test
    @DisplayName("RENEW由服务端旧值生成新快照并原子写profile action outbox")
    void shouldRenewWithServerSnapshotAndThreeTransactionalWrites() throws Exception
    {
        stubPendingCycle();
        HrRenewalDecisionRequest request = renewRequest();

        Long actionId = confirm(request, 88L, false);

        assertThat(actionId).isEqualTo(701L);
        ArgumentCaptor<HrEmployeeSigningSnapshot> update =
                ArgumentCaptor.forClass(HrEmployeeSigningSnapshot.class);
        ArgumentCaptor<SysHrLifecycleAction> action =
                ArgumentCaptor.forClass(SysHrLifecycleAction.class);
        ArgumentCaptor<SysHrSignEventOutbox> outbox =
                ArgumentCaptor.forClass(SysHrSignEventOutbox.class);
        verify(profileMapper).updateRenewalProfile(update.capture(),
                org.mockito.ArgumentMatchers.eq(LocalDate.of(2026, 8, 1)),
                org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.eq("配置HR"));
        verify(actionMapper).insertAction(action.capture());
        verify(outboxMapper).insertOutbox(outbox.capture());
        verify(renewalGuardMapper).reserve(
                9L, "RENEWAL", 701L, 0L);

        HrEmployeeSigningSnapshot after = update.getValue();
        assertThat(after.getContractStartDate()).isEqualTo(LocalDate.of(2026, 8, 2));
        assertThat(after.getContractEndDate()).isEqualTo(LocalDate.of(2027, 8, 1));
        assertThat(after.getRenewalCount()).isEqualTo(3);
        assertThat(after.getBaseSalary()).isEqualByComparingTo("5000.00");
        assertThat(after.getSocialTypeCode()).isEqualTo("SOCIAL_INSURED");
        assertThat(action.getValue().getActionType()).isEqualTo("RENEWAL_CONFIRMED");
        assertThat(action.getValue().getBusinessStatus()).isEqualTo("CONFIRMED");
        assertThat(action.getValue().getBeforeSnapshotJson()).contains(
                "\"idNumber\":\"SERVER-ID\"", "\"renewalCount\":2");

        HrSignBusinessEvent event = objectMapper.readValue(
                outbox.getValue().getPayloadJson(), HrSignBusinessEvent.class);
        assertThat(event.getScenario()).isEqualTo("RENEWAL");
        assertThat(event.getSourceBusinessId()).isEqualTo("701");
        assertThat(event.getSourceEventVersion()).isEqualTo(1L);
        assertThat(event.getBeforeSnapshot().getContractEndDate())
                .isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(event.getAfterSnapshot().getRenewalCount()).isEqualTo(3);
        assertThat(event.getAttributes()).containsEntry("decision", "RENEW")
                .containsEntry("oldContractEndDate", "2026-08-01")
                .containsEntry("oldRenewalCount", 2)
                .containsEntry("newRenewalCount", 3);
    }

    @Test
    @DisplayName("DECLINE保持合同和次数不变但追加动作及typed NO_ACTION事件")
    void shouldDeclineWithoutUpdatingProfileAndStillWriteEvent() throws Exception
    {
        stubPendingCycle();
        HrRenewalDecisionRequest request = new HrRenewalDecisionRequest();
        request.setRequestId("req-renew-1");
        request.setDecision(HrRenewalDecisionRequest.Decision.DECLINE);

        assertThat(confirm(request, 88L, false)).isEqualTo(701L);

        verify(profileMapper, never()).updateRenewalProfile(any(), any(), any(), any());
        ArgumentCaptor<SysHrLifecycleAction> action =
                ArgumentCaptor.forClass(SysHrLifecycleAction.class);
        ArgumentCaptor<SysHrSignEventOutbox> outbox =
                ArgumentCaptor.forClass(SysHrSignEventOutbox.class);
        verify(actionMapper).insertAction(action.capture());
        verify(outboxMapper).insertOutbox(outbox.capture());
        assertThat(action.getValue().getActionType()).isEqualTo("RENEWAL_DECLINED");
        assertThat(action.getValue().getBusinessStatus()).isEqualTo("DECLINED");
        assertThat(action.getValue().getBeforeSnapshotJson())
                .isEqualTo(action.getValue().getAfterSnapshotJson());
        HrSignBusinessEvent event = objectMapper.readValue(
                outbox.getValue().getPayloadJson(), HrSignBusinessEvent.class);
        assertThat(event.getAttributes()).containsEntry("decision", "DECLINE")
                .containsEntry("oldRenewalCount", 2)
                .containsEntry("newRenewalCount", 2);
    }

    @Test
    @DisplayName("同requestId重放只返回原action且不同request不能重复解决同一旧合同")
    void shouldReplaySameRequestAndRejectDifferentRequestForResolvedCycle()
            throws Exception
    {
        when(configMapper.selectConfiguredSignHrUserId()).thenReturn(88L);
        HrEmployeeSigningSnapshot before = activeSnapshot();
        HrEmployeeSigningSnapshot after = renewedSnapshot(before, renewRequest());
        when(profileMapper.selectSigningSnapshotByUserIdForUpdate(9L)).thenReturn(after);
        SysHrLifecycleAction existing = resolvedAction(
                701L, "RENEWAL_CONFIRMED", before, after, "req-renew-1");
        when(actionMapper.selectByRequestIdForUpdate("req-renew-1")).thenReturn(existing);

        assertThat(confirm(renewRequest(), 88L, false)).isEqualTo(701L);
        verify(actionMapper, never()).insertAction(any());
        verify(outboxMapper, never()).insertOutbox(any());
        verify(renewalGuardMapper, never()).selectForUpdate(anyLong(), any());

        setUp();
        when(configMapper.selectConfiguredSignHrUserId()).thenReturn(88L);
        when(profileMapper.selectSigningSnapshotByUserIdForUpdate(9L))
                .thenReturn(activeSnapshot());
        when(actionMapper.selectRenewalCycleActionsForUpdate(
                9L, "9:2026-08-01:2")).thenReturn(List.of(
                        cycleAction(501L, "RENEWAL_DECISION"), existing));
        HrRenewalDecisionRequest different = renewRequest();
        different.setRequestId("req-renew-2");
        assertThatThrownBy(() -> confirm(different, 88L, false))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已处理");
        verify(actionMapper, never()).insertAction(any());
    }

    @Test
    @DisplayName("旧requestId不能把不同续签决定误判为成功重放")
    void shouldRejectReplayWithDifferentDecision() throws Exception
    {
        when(configMapper.selectConfiguredSignHrUserId()).thenReturn(88L);
        HrEmployeeSigningSnapshot before = activeSnapshot();
        HrRenewalDecisionRequest original = renewRequest();
        HrEmployeeSigningSnapshot after = renewedSnapshot(before, original);
        when(profileMapper.selectSigningSnapshotByUserIdForUpdate(9L)).thenReturn(after);
        when(actionMapper.selectByRequestIdForUpdate("req-renew-1")).thenReturn(
                resolvedAction(701L, "RENEWAL_CONFIRMED", before, after, "req-renew-1"));
        HrRenewalDecisionRequest replay = new HrRenewalDecisionRequest();
        replay.setRequestId("req-renew-1");
        replay.setDecision(HrRenewalDecisionRequest.Decision.DECLINE);

        assertThatThrownBy(() -> confirm(replay, 88L, false))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("payload");
        verify(actionMapper, never()).insertAction(any());
        verify(outboxMapper, never()).insertOutbox(any());
    }

    @Test
    @DisplayName("旧requestId不能把不同新合同payload误判为成功重放")
    void shouldRejectReplayWithDifferentRenewalPayload() throws Exception
    {
        when(configMapper.selectConfiguredSignHrUserId()).thenReturn(88L);
        HrEmployeeSigningSnapshot before = activeSnapshot();
        HrRenewalDecisionRequest original = renewRequest();
        HrEmployeeSigningSnapshot after = renewedSnapshot(before, original);
        when(profileMapper.selectSigningSnapshotByUserIdForUpdate(9L)).thenReturn(after);
        when(actionMapper.selectByRequestIdForUpdate("req-renew-1")).thenReturn(
                resolvedAction(701L, "RENEWAL_CONFIRMED", before, after, "req-renew-1"));
        HrRenewalDecisionRequest replay = renewRequest();
        replay.setContractEndDate(LocalDate.of(2028, 8, 1));

        assertThatThrownBy(() -> confirm(replay, 88L, false))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("payload");
        verify(actionMapper, never()).insertAction(any());
        verify(outboxMapper, never()).insertOutbox(any());
    }

    @Test
    @DisplayName("DECLINE requestId不能跨旧合同周期重放")
    void shouldRejectDeclineReplayFromAnotherContractCycle() throws Exception
    {
        when(configMapper.selectConfiguredSignHrUserId()).thenReturn(88L);
        HrEmployeeSigningSnapshot oldCycle = activeSnapshot();
        HrEmployeeSigningSnapshot currentCycle = activeSnapshot();
        currentCycle.setContractStartDate(LocalDate.of(2026, 8, 2));
        currentCycle.setContractEndDate(LocalDate.of(2027, 8, 1));
        currentCycle.setRenewalCount(3);
        when(profileMapper.selectSigningSnapshotByUserIdForUpdate(9L)).thenReturn(currentCycle);
        when(actionMapper.selectByRequestIdForUpdate("req-decline-old")).thenReturn(
                resolvedAction(702L, "RENEWAL_DECLINED", oldCycle, oldCycle,
                        "req-decline-old"));
        HrRenewalDecisionRequest replay = new HrRenewalDecisionRequest();
        replay.setRequestId("req-decline-old");
        replay.setDecision(HrRenewalDecisionRequest.Decision.DECLINE);

        assertThatThrownBy(() -> confirm(replay, 88L, false))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("合同周期");
        verify(actionMapper, never()).insertAction(any());
        verify(outboxMapper, never()).insertOutbox(any());
    }

    @Test
    @DisplayName("requestId历史快照JSON为null时业务拒绝而不是NPE")
    void shouldRejectJsonNullReplaySnapshot() throws Exception
    {
        when(configMapper.selectConfiguredSignHrUserId()).thenReturn(88L);
        HrEmployeeSigningSnapshot current = renewedSnapshot(activeSnapshot(), renewRequest());
        when(profileMapper.selectSigningSnapshotByUserIdForUpdate(9L)).thenReturn(current);
        SysHrLifecycleAction existing = cycleAction(701L, "RENEWAL_CONFIRMED");
        existing.setRequestId("req-renew-1");
        existing.setBeforeSnapshotJson("null");
        existing.setAfterSnapshotJson(objectMapper.writeValueAsString(current));
        when(actionMapper.selectByRequestIdForUpdate("req-renew-1")).thenReturn(existing);

        assertThatThrownBy(() -> confirm(renewRequest(), 88L, false))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("快照");
        verify(actionMapper, never()).insertAction(any());
        verify(outboxMapper, never()).insertOutbox(any());
    }

    @ParameterizedTest(name = "旧快照缺失{0}时拒绝")
    @ValueSource(strings = {
            "contractStartDate", "contractTypeCode", "contractTermCode",
            "legalEntityId", "legalEntityCode", "legalEntityName"
    })
    @DisplayName("旧合同和旧法律主体不完整时不消耗待处理决策")
    void shouldRejectIncompleteOldContractAndLegalEntityWithoutConsumingDecision(
            String field)
    {
        stubPendingCycle();
        HrEmployeeSigningSnapshot incomplete = activeSnapshot();
        switch (field)
        {
            case "contractStartDate" -> incomplete.setContractStartDate(null);
            case "contractTypeCode" -> incomplete.setContractTypeCode(null);
            case "contractTermCode" -> incomplete.setContractTermCode(null);
            case "legalEntityId" -> incomplete.setLegalEntityId(null);
            case "legalEntityCode" -> incomplete.setLegalEntityCode(" ");
            case "legalEntityName" -> incomplete.setLegalEntityName(null);
            default -> throw new IllegalArgumentException(field);
        }
        when(profileMapper.selectSigningSnapshotByUserIdForUpdate(9L)).thenReturn(incomplete);

        assertThatThrownBy(() -> confirm(renewRequest(), 88L, false))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(field.startsWith("legal") ? "旧法律主体" : "旧合同");
        verify(actionMapper, never()).insertAction(any());
        verify(profileMapper, never()).updateRenewalProfile(any(), any(), any(), any());
        verify(outboxMapper, never()).insertOutbox(any());
    }

    @ParameterizedTest(name = "旧合同{0}无效时拒绝")
    @ValueSource(strings = { "reversedDates", "contractTypeCode", "contractTermCode" })
    @DisplayName("旧合同日期倒置或代码非法时不消耗待处理决策")
    void shouldRejectInvalidOldContractWithoutConsumingDecision(String invalidField)
    {
        stubPendingCycle();
        HrEmployeeSigningSnapshot invalid = activeSnapshot();
        switch (invalidField)
        {
            case "reversedDates" -> invalid.setContractStartDate(invalid.getContractEndDate());
            case "contractTypeCode" -> invalid.setContractTypeCode("UNKNOWN_TYPE");
            case "contractTermCode" -> invalid.setContractTermCode("UNKNOWN_TERM");
            default -> throw new IllegalArgumentException(invalidField);
        }
        when(profileMapper.selectSigningSnapshotByUserIdForUpdate(9L)).thenReturn(invalid);

        assertThatThrownBy(() -> confirm(renewRequest(), 88L, false))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("旧合同");
        verify(actionMapper, never()).insertAction(any());
        verify(profileMapper, never()).updateRenewalProfile(any(), any(), any(), any());
        verify(outboxMapper, never()).insertOutbox(any());
    }

    @ParameterizedTest(name = "{0}门闩阻止第二个续签决定")
    @ValueSource(strings = { "RESERVED", "ACTIVE" })
    @DisplayName("已有未完成续签门闩时禁止创建第二个final action")
    void shouldBlockAnotherRenewalWhileGuardIsNonIdle(String status)
    {
        stubPendingCycle();
        when(renewalGuardMapper.selectForUpdate(9L, "RENEWAL"))
                .thenReturn(guard(status, 600L, status.equals("ACTIVE") ? 900L : null, 4L));

        assertThatThrownBy(() -> confirm(renewRequest(), 88L, false))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("未完成续签任务");
        verify(actionMapper, never()).insertAction(any());
        verify(profileMapper, never()).updateRenewalProfile(any(), any(), any(), any());
        verify(outboxMapper, never()).insertOutbox(any());
    }

    @Test
    @DisplayName("门闩预留失败时在profile和outbox写入前中止")
    void shouldStopBeforeProfileAndOutboxWhenGuardReservationLosesRace()
    {
        stubPendingCycle();
        when(renewalGuardMapper.reserve(9L, "RENEWAL", 701L, 0L)).thenReturn(0);

        assertThatThrownBy(() -> confirm(renewRequest(), 88L, false))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("门闩");
        verify(profileMapper, never()).updateRenewalProfile(any(), any(), any(), any());
        verify(outboxMapper, never()).insertOutbox(any());
    }

    @Test
    @DisplayName("没有未完成决策时不能由新requestId凭当前profile直接续签")
    void shouldRejectConfirmationWithoutPendingDecision()
    {
        when(configMapper.selectConfiguredSignHrUserId()).thenReturn(88L);
        when(profileMapper.selectSigningSnapshotByUserIdForUpdate(9L))
                .thenReturn(activeSnapshot());
        when(actionMapper.selectRenewalCycleActionsForUpdate(
                9L, "9:2026-08-01:2")).thenReturn(List.of());

        assertThatThrownBy(() -> confirm(renewRequest(), 88L, false))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("没有待处理");
        verify(actionMapper, never()).insertAction(any());
        verify(profileMapper, never()).updateRenewalProfile(any(), any(), any(), any());
        verify(outboxMapper, never()).insertOutbox(any());
    }

    @Test
    @DisplayName("非配置HR和越权门店在任何写入前拒绝")
    void shouldRequireCurrentConfiguredHrAndShopScope()
    {
        when(configMapper.selectConfiguredSignHrUserId()).thenReturn(88L);
        assertThatThrownBy(() -> confirm(renewRequest(), 77L, true))
                .isInstanceOf(ServiceException.class).hasMessageContaining("配置HR");
        verify(profileMapper, never()).selectSigningSnapshotByUserIdForUpdate(anyLong());

        when(profileMapper.selectSigningSnapshotByUserIdForUpdate(9L))
                .thenReturn(activeSnapshot());
        doThrow(new ServiceException("无权选择该店铺"))
                .when(userShopService).checkUserShopScope(88L, 20L, false);
        assertThatThrownBy(() -> confirm(renewRequest(), 88L, true))
                .isInstanceOf(ServiceException.class).hasMessageContaining("无权");
        verify(userShopService).checkUserShopScope(88L, 20L, false);
        verify(actionMapper, never()).insertAction(any());
    }

    @Test
    @DisplayName("续签日期重叠倒置法律主体缺失和代码非法均拒绝")
    void shouldRejectInvalidRenewalFields()
    {
        when(configMapper.selectConfiguredSignHrUserId()).thenReturn(88L);
        when(profileMapper.selectSigningSnapshotByUserIdForUpdate(9L))
                .thenReturn(activeSnapshot());
        HrRenewalDecisionRequest overlap = renewRequest();
        overlap.setContractStartDate(LocalDate.of(2026, 8, 1));
        assertThatThrownBy(() -> confirm(overlap, 88L, false))
                .isInstanceOf(ServiceException.class).hasMessageContaining("晚于旧合同");

        HrRenewalDecisionRequest reversed = renewRequest();
        reversed.setContractEndDate(reversed.getContractStartDate());
        assertThatThrownBy(() -> confirm(reversed, 88L, false))
                .isInstanceOf(ServiceException.class).hasMessageContaining("结束日期");

        HrRenewalDecisionRequest missingLegal = renewRequest();
        missingLegal.setLegalEntityCode(" ");
        assertThatThrownBy(() -> confirm(missingLegal, 88L, false))
                .isInstanceOf(ServiceException.class).hasMessageContaining("法律主体");

        HrRenewalDecisionRequest invalidCode = renewRequest();
        invalidCode.setContractTypeCode("maybe");
        assertThatThrownBy(() -> confirm(invalidCode, 88L, false))
                .isInstanceOf(ServiceException.class).hasMessageContaining("合同类型");
        verify(actionMapper, never()).insertAction(any());
    }

    @Test
    @DisplayName("profile action outbox任一写失败都向事务边界抛出")
    void shouldPropagateEveryWriteFailureForRollback()
    {
        stubPendingCycle();
        doThrow(new IllegalStateException("action failed"))
                .when(actionMapper).insertAction(any());
        assertThatThrownBy(() -> confirm(renewRequest(), 88L, false))
                .isInstanceOf(IllegalStateException.class).hasMessage("action failed");
        verify(profileMapper, never()).updateRenewalProfile(any(), any(), any(), any());

        setUp();
        stubPendingCycle();
        when(profileMapper.updateRenewalProfile(any(), any(), any(), any())).thenReturn(0);
        assertThatThrownBy(() -> confirm(renewRequest(), 88L, false))
                .isInstanceOf(ServiceException.class).hasMessageContaining("档案");
        verify(outboxMapper, never()).insertOutbox(any());

        setUp();
        stubPendingCycle();
        doThrow(new IllegalStateException("outbox failed"))
                .when(outboxMapper).insertOutbox(any());
        assertThatThrownBy(() -> confirm(renewRequest(), 88L, false))
                .isInstanceOf(IllegalStateException.class).hasMessage("outbox failed");
    }

    @Test
    @DisplayName("员工档案入口使用明确权限且只返回actionId并记录IP和UA")
    void shouldExposeMinimalAuditedRenewalEndpoint() throws Exception
    {
        String source = Files.readString(repoFile(
                "erp-modules/erp-system/src/main/java/com/erp/system/controller/"
                        + "HrEmployeeProfileController.java"), StandardCharsets.UTF_8);

        assertThat(source).contains(
                "@RequiresPermissions(\"hr:employee:renewal\")",
                "@PostMapping(\"/{userId}/renewal/confirm\")",
                "hrLifecycleService.confirmRenewal",
                "IpUtils.getIpAddr(servletRequest)",
                "servletRequest.getHeader(\"User-Agent\")",
                "Map.of(\"actionId\", actionId)")
                .doesNotContain("beforeSnapshot", "afterSnapshot", "salaryTotal");
    }

    @Test
    @DisplayName("续签SQL只改合同与次数且生命周期历史没有原地update")
    void shouldKeepMoneyAndHistoryImmutableInPersistence() throws Exception
    {
        String profileXml = Files.readString(repoFile(
                "erp-modules/erp-system/src/main/resources/mapper/system/"
                        + "SysUserProfileMapper.xml"), StandardCharsets.UTF_8);
        int updateStart = profileXml.indexOf("<update id=\"updateRenewalProfile\">");
        int updateEnd = profileXml.indexOf("</update>", updateStart);
        String renewalUpdate = profileXml.substring(updateStart, updateEnd);
        assertThat(renewalUpdate).contains(
                "contract_start_date", "contract_end_date", "contract_type",
                "contract_term", "legal_entity_id", "renewal_count",
                "coalesce(renewal_count, 0) = #{oldRenewalCount}")
                .doesNotContain("base_salary", "post_salary", "field_allowance",
                        "performance_salary", "salary_total", "social_type");

        String actionXml = Files.readString(repoFile(
                "erp-modules/erp-system/src/main/resources/mapper/system/"
                        + "SysHrLifecycleActionMapper.xml"), StandardCharsets.UTF_8);
        assertThat(actionXml).contains("insertAction", "selectRenewalCycleActionsForUpdate")
                .doesNotContain("<update id=", "update sys_hr_lifecycle_action");
    }

    private Long confirm(HrRenewalDecisionRequest request, Long operatorId, boolean admin)
    {
        return service.confirmRenewal(9L, request, operatorId, "配置HR", admin,
                "10.0.0.8", "JUnit-UA");
    }

    private void stubPendingCycle()
    {
        when(configMapper.selectConfiguredSignHrUserId()).thenReturn(88L);
        when(profileMapper.selectSigningSnapshotByUserIdForUpdate(9L))
                .thenReturn(activeSnapshot());
        when(actionMapper.selectRenewalCycleActionsForUpdate(
                9L, "9:2026-08-01:2")).thenReturn(
                        List.of(cycleAction(501L, "RENEWAL_DECISION")));
        when(profileMapper.updateRenewalProfile(any(), any(), any(), any())).thenReturn(1);
        when(renewalGuardMapper.selectForUpdate(9L, "RENEWAL"))
                .thenReturn(guard("IDLE", null, null, 0L));
        when(renewalGuardMapper.reserve(9L, "RENEWAL", 701L, 0L)).thenReturn(1);
        doAnswer(invocation -> {
            SysHrLifecycleAction action = invocation.getArgument(0);
            action.setActionId(701L);
            return 1;
        }).when(actionMapper).insertAction(any());
        when(outboxMapper.insertOutbox(any())).thenReturn(1);
    }

    private HrRenewalGuard guard(String status, Long actionId, Long taskId, Long version)
    {
        HrRenewalGuard guard = new HrRenewalGuard();
        guard.setEmployeeId(9L);
        guard.setScenario("RENEWAL");
        guard.setStatus(status);
        guard.setActionId(actionId);
        guard.setTaskId(taskId);
        guard.setVersion(version);
        return guard;
    }

    private SysHrLifecycleAction cycleAction(Long id, String type)
    {
        SysHrLifecycleAction action = new SysHrLifecycleAction();
        action.setActionId(id);
        action.setActionType(type);
        action.setEmployeeId(9L);
        action.setSourceType("HR_RENEWAL");
        action.setSourceBusinessId("9:2026-08-01:2");
        return action;
    }

    private SysHrLifecycleAction resolvedAction(Long id, String type,
            HrEmployeeSigningSnapshot before, HrEmployeeSigningSnapshot after,
            String requestId) throws Exception
    {
        SysHrLifecycleAction action = cycleAction(id, type);
        action.setRequestId(requestId);
        action.setBeforeSnapshotJson(objectMapper.writeValueAsString(before));
        action.setAfterSnapshotJson(objectMapper.writeValueAsString(after));
        action.setVersion(1L);
        return action;
    }

    private HrEmployeeSigningSnapshot renewedSnapshot(HrEmployeeSigningSnapshot before,
            HrRenewalDecisionRequest request)
    {
        HrEmployeeSigningSnapshot after = objectMapper.convertValue(
                before, HrEmployeeSigningSnapshot.class);
        after.setContractStartDate(request.getContractStartDate());
        after.setContractEndDate(request.getContractEndDate());
        after.setContractTypeCode(request.getContractTypeCode());
        after.setContractTermCode(request.getContractTermCode());
        after.setLegalEntityId(request.getLegalEntityId());
        after.setLegalEntityCode(request.getLegalEntityCode());
        after.setLegalEntityName(request.getLegalEntityName());
        after.setRenewalCount(before.getRenewalCount() + 1);
        return after;
    }

    private HrEmployeeSigningSnapshot activeSnapshot()
    {
        HrEmployeeSigningSnapshot snapshot = new HrEmployeeSigningSnapshot();
        snapshot.setEmployeeId(9L);
        snapshot.setEmployeeNo("E0009");
        snapshot.setEmployeeName("张三");
        snapshot.setPhone("13800000000");
        snapshot.setIdType("CN_ID_CARD");
        snapshot.setIdNumber("SERVER-ID");
        snapshot.setCurrentAddress("上海市徐汇区");
        snapshot.setEmployeeStatus("在职");
        snapshot.setEmployeeCategory("FULL_TIME");
        snapshot.setShopDeptId(20L);
        snapshot.setShopDeptName("徐汇门店");
        snapshot.setDeptId(20L);
        snapshot.setDeptName("徐汇门店");
        snapshot.setPostId(30L);
        snapshot.setPostCode("SALES");
        snapshot.setPostName("销售顾问");
        snapshot.setJobGradeCode("P3");
        snapshot.setJobGradeName("P3");
        snapshot.setWorkLocation("上海");
        snapshot.setContractTypeCode("LABOR_CONTRACT");
        snapshot.setContractTermCode("FIXED_TERM");
        snapshot.setSocialTypeCode("SOCIAL_INSURED");
        snapshot.setContractStartDate(LocalDate.of(2025, 8, 2));
        snapshot.setContractEndDate(LocalDate.of(2026, 8, 1));
        snapshot.setRenewalCount(2);
        snapshot.setLegalEntityId(300L);
        snapshot.setLegalEntityCode("LE-SH");
        snapshot.setLegalEntityName("上海示例有限公司");
        snapshot.setBaseSalary(new BigDecimal("5000.00"));
        snapshot.setPostSalary(new BigDecimal("2000.00"));
        snapshot.setFieldAllowance(new BigDecimal("300.00"));
        snapshot.setPerformanceSalary(new BigDecimal("700.00"));
        snapshot.setSalaryTotal(new BigDecimal("8000.00"));
        snapshot.setSalaryVersion("2026-V1");
        return snapshot;
    }

    private HrRenewalDecisionRequest renewRequest()
    {
        HrRenewalDecisionRequest request = new HrRenewalDecisionRequest();
        request.setRequestId("req-renew-1");
        request.setDecision(HrRenewalDecisionRequest.Decision.RENEW);
        request.setContractStartDate(LocalDate.of(2026, 8, 2));
        request.setContractEndDate(LocalDate.of(2027, 8, 1));
        request.setContractTypeCode("LABOR_CONTRACT");
        request.setContractTermCode("FIXED_TERM");
        request.setLegalEntityId(300L);
        request.setLegalEntityCode("LE-SH");
        request.setLegalEntityName("上海示例有限公司");
        return request;
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
