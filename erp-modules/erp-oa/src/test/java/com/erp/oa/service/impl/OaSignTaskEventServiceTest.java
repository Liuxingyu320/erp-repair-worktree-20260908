package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.constant.OaSignOperatorType;
import com.erp.oa.constant.OaSignTaskStatus;
import com.erp.oa.api.domain.HrRenewalGuard;
import com.erp.oa.domain.OaSignTask;
import com.erp.oa.domain.OaSignTaskEvent;
import com.erp.oa.mapper.OaSignTaskEventMapper;
import com.erp.oa.mapper.OaHrRenewalGuardMapper;
import com.erp.oa.mapper.OaSignTaskMapper;
import com.erp.oa.service.OaSignTaskStateMachine;

@DisplayName("签约任务事件链服务")
class OaSignTaskEventServiceTest
{
    @Test
    @DisplayName("状态更新和事件插入在同一事务并生成稳定hash")
    void shouldUpdateAndAppendEventAtomically() throws Exception
    {
        OaSignTaskMapper taskMapper = mock(OaSignTaskMapper.class);
        OaSignTaskEventMapper eventMapper = mock(OaSignTaskEventMapper.class);
        OaHrRenewalGuardMapper renewalGuardMapper = mock(OaHrRenewalGuardMapper.class);
        OaSignTaskEventService service = new OaSignTaskEventService(
                taskMapper, eventMapper, renewalGuardMapper, new OaSignTaskStateMachine());
        OaSignTask current = task(9L, OaSignTaskStatus.WAITING_HR_CONFIRM, 3L);
        OaSignTaskEvent previous = new OaSignTaskEvent();
        previous.setEventHash("a".repeat(64));
        when(eventMapper.selectLastTaskEvent(9L)).thenReturn(previous);
        when(taskMapper.updateStatusWithVersion(9L, "WAITING_HR_CONFIRM", "READY_TO_SEND", 3L,
                null, null, null, 101L)).thenReturn(1);
        when(eventMapper.insertOaSignTaskEvent(any())).thenReturn(1);

        OaSignTask result = service.transition(current, OaSignTaskStatus.READY_TO_SEND,
                OaSignOperatorType.HR, 101L, "HR_CONFIRMED", "资料和文件无误",
                "confirm-9", "127.0.0.1", "JUnit");

        assertThat(result.getStatus()).isEqualTo("READY_TO_SEND");
        assertThat(result.getVersion()).isEqualTo(4L);
        ArgumentCaptor<OaSignTaskEvent> captor = ArgumentCaptor.forClass(OaSignTaskEvent.class);
        verify(eventMapper).insertOaSignTaskEvent(captor.capture());
        OaSignTaskEvent event = captor.getValue();
        assertThat(event.getPrevEventHash()).isEqualTo("a".repeat(64));
        assertThat(event.getEventHash()).hasSize(64)
                .isEqualTo(OaSignTaskEventService.calculateEventHash(event));
        assertThat(event.getCreatedTime().getTime() % 1000).isZero();
        assertThat(event.getOperatorType()).isEqualTo("HR");
        assertThat(event.getRequestId()).isEqualTo("confirm-9");

        Method transition = OaSignTaskEventService.class.getMethod("transition",
                OaSignTask.class, OaSignTaskStatus.class, OaSignOperatorType.class, Long.class,
                String.class, String.class, String.class, String.class, String.class);
        assertThat(transition.getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    @DisplayName("并发操作已到达相同终态时幂等返回")
    void shouldReturnIdempotentlyWhenTerminalStateAlreadyReached()
    {
        OaSignTaskMapper taskMapper = mock(OaSignTaskMapper.class);
        OaSignTaskEventMapper eventMapper = mock(OaSignTaskEventMapper.class);
        OaHrRenewalGuardMapper renewalGuardMapper = mock(OaHrRenewalGuardMapper.class);
        OaSignTaskEventService service = new OaSignTaskEventService(
                taskMapper, eventMapper, renewalGuardMapper, new OaSignTaskStateMachine());
        OaSignTask current = task(9L, OaSignTaskStatus.PENDING_FINAL_CONFIRM, 5L);
        OaSignTask persisted = task(9L, OaSignTaskStatus.SIGNED, 6L);
        when(taskMapper.updateStatusWithVersion(9L, "PENDING_FINAL_CONFIRM", "SIGNED", 5L,
                null, null, null, 101L)).thenReturn(0);
        when(taskMapper.selectOaSignTaskById(9L)).thenReturn(persisted);

        OaSignTask result = service.transition(current, OaSignTaskStatus.SIGNED,
                OaSignOperatorType.EMPLOYEE, 201L, "SIGNED", null,
                "sign-9", null, null);

        assertThat(result).isSameAs(persisted);
        verify(eventMapper, never()).insertOaSignTaskEvent(any());
    }

    @Test
    @DisplayName("并发操作不是相同终态时拒绝覆盖")
    void shouldRejectConflictingConcurrentUpdate()
    {
        OaSignTaskMapper taskMapper = mock(OaSignTaskMapper.class);
        OaSignTaskEventMapper eventMapper = mock(OaSignTaskEventMapper.class);
        OaHrRenewalGuardMapper renewalGuardMapper = mock(OaHrRenewalGuardMapper.class);
        OaSignTaskEventService service = new OaSignTaskEventService(
                taskMapper, eventMapper, renewalGuardMapper, new OaSignTaskStateMachine());
        OaSignTask current = task(9L, OaSignTaskStatus.READY_TO_SEND, 2L);
        when(taskMapper.updateStatusWithVersion(9L, "READY_TO_SEND", "SENDING", 2L,
                null, null, null, 101L)).thenReturn(0);
        when(taskMapper.selectOaSignTaskById(9L))
                .thenReturn(task(9L, OaSignTaskStatus.WAITING_HR_CONFIRM, 3L));

        assertThatThrownBy(() -> service.transition(current, OaSignTaskStatus.SENDING,
                OaSignOperatorType.HR, 101L, "SEND", null, "send-9", null, null))
                .isInstanceOf(ServiceException.class)
                .hasMessage("任务已被其他操作更新");
        verify(eventMapper, never()).insertOaSignTaskEvent(any());
    }

    @Test
    @DisplayName("相同requestId只返回第一次相同操作")
    void shouldReuseSameRequestIdWithoutAppendingAnotherEvent()
    {
        OaSignTaskMapper taskMapper = mock(OaSignTaskMapper.class);
        OaSignTaskEventMapper eventMapper = mock(OaSignTaskEventMapper.class);
        OaHrRenewalGuardMapper renewalGuardMapper = mock(OaHrRenewalGuardMapper.class);
        OaSignTaskEventService service = new OaSignTaskEventService(
                taskMapper, eventMapper, renewalGuardMapper, new OaSignTaskStateMachine());
        OaSignTask current = task(9L, OaSignTaskStatus.VIEWED, 5L);
        OaSignTask persisted = task(9L, OaSignTaskStatus.SIGNED, 6L);
        OaSignTaskEvent existing = new OaSignTaskEvent();
        existing.setTaskId(9L);
        existing.setToStatus("SIGNED");
        when(eventMapper.selectEventByRequestId("sign-9")).thenReturn(existing);
        when(taskMapper.selectOaSignTaskById(9L)).thenReturn(persisted);

        assertThat(service.transition(current, OaSignTaskStatus.SIGNED,
                OaSignOperatorType.EMPLOYEE, 201L, "SIGNED", null,
                "sign-9", null, null)).isSameAs(persisted);
        verify(taskMapper, never()).updateStatusWithVersion(any(), any(), any(), any(), any(), any(), any(), any());
        verify(eventMapper, never()).insertOaSignTaskEvent(any());
    }

    @ParameterizedTest(name = "RENEWAL进入{0}时释放门闩")
    @ValueSource(strings = { "SIGNED", "REFUSED", "EXPIRED", "CANCELLED", "NO_ACTION" })
    @DisplayName("中央状态流在所有续签终态与事件同事务释放权威门闩")
    void shouldReleaseRenewalGuardForEveryTerminalStatus(String targetName)
    {
        OaSignTaskMapper taskMapper = mock(OaSignTaskMapper.class);
        OaSignTaskEventMapper eventMapper = mock(OaSignTaskEventMapper.class);
        OaHrRenewalGuardMapper renewalGuardMapper = mock(OaHrRenewalGuardMapper.class);
        OaSignTaskEventService service = new OaSignTaskEventService(
                taskMapper, eventMapper, renewalGuardMapper, new OaSignTaskStateMachine());
        OaSignTaskStatus target = OaSignTaskStatus.valueOf(targetName);
        OaSignTaskStatus from = target == OaSignTaskStatus.NO_ACTION
                ? OaSignTaskStatus.VALIDATING
                : target == OaSignTaskStatus.SIGNED
                        ? OaSignTaskStatus.PENDING_FINAL_CONFIRM
                        : OaSignTaskStatus.PENDING_SIGN;
        OaSignTask current = renewalTask(9L, from, 3L);
        when(taskMapper.updateStatusWithVersion(9L, from.name(), target.name(), 3L,
                null, null, null, 101L)).thenReturn(1);
        when(eventMapper.insertOaSignTaskEvent(any())).thenReturn(1);
        when(renewalGuardMapper.selectForUpdate(201L, "RENEWAL"))
                .thenReturn(guard(701L, 9L));
        when(renewalGuardMapper.release(201L, "RENEWAL", 701L, 9L)).thenReturn(1);

        assertThat(service.transition(current, target, OaSignOperatorType.SYSTEM,
                null, target.name(), null, null, null, null).getStatus())
                .isEqualTo(target.name());

        verify(renewalGuardMapper).release(201L, "RENEWAL", 701L, 9L);
    }

    @Test
    @DisplayName("续签非终态和非续签终态都不释放门闩")
    void shouldNotReleaseGuardOutsideRenewalTerminalTransitions()
    {
        OaSignTaskMapper taskMapper = mock(OaSignTaskMapper.class);
        OaSignTaskEventMapper eventMapper = mock(OaSignTaskEventMapper.class);
        OaHrRenewalGuardMapper renewalGuardMapper = mock(OaHrRenewalGuardMapper.class);
        OaSignTaskEventService service = new OaSignTaskEventService(
                taskMapper, eventMapper, renewalGuardMapper, new OaSignTaskStateMachine());
        OaSignTask renewal = renewalTask(9L, OaSignTaskStatus.NEW, 0L);
        when(taskMapper.updateStatusWithVersion(9L, "NEW", "VALIDATING", 0L,
                null, null, null, 101L)).thenReturn(1);
        when(eventMapper.insertOaSignTaskEvent(any())).thenReturn(1);
        when(renewalGuardMapper.selectForUpdate(201L, "RENEWAL"))
                .thenReturn(guard(701L, 9L));
        service.transition(renewal, OaSignTaskStatus.VALIDATING, OaSignOperatorType.SYSTEM,
                null, "VALIDATING", null, null, null, null);

        OaSignTask onboard = task(10L, OaSignTaskStatus.PENDING_FINAL_CONFIRM, 2L);
        onboard.setScenario("ONBOARD");
        when(taskMapper.updateStatusWithVersion(10L, "PENDING_FINAL_CONFIRM", "SIGNED", 2L,
                null, null, null, 101L)).thenReturn(1);
        service.transition(onboard, OaSignTaskStatus.SIGNED, OaSignOperatorType.EMPLOYEE,
                201L, "SIGNED", null, null, null, null);

        verify(renewalGuardMapper, never()).release(any(), any(), any(), any());
    }

    @Test
    @DisplayName("续签终态门闩释放失败时拒绝提交终态")
    void shouldFailTerminalTransitionWhenRenewalGuardCannotBeReleased()
    {
        OaSignTaskMapper taskMapper = mock(OaSignTaskMapper.class);
        OaSignTaskEventMapper eventMapper = mock(OaSignTaskEventMapper.class);
        OaHrRenewalGuardMapper renewalGuardMapper = mock(OaHrRenewalGuardMapper.class);
        OaSignTaskEventService service = new OaSignTaskEventService(
                taskMapper, eventMapper, renewalGuardMapper, new OaSignTaskStateMachine());
        OaSignTask current = renewalTask(9L, OaSignTaskStatus.PENDING_FINAL_CONFIRM, 3L);
        when(taskMapper.updateStatusWithVersion(9L, "PENDING_FINAL_CONFIRM", "SIGNED", 3L,
                null, null, null, 101L)).thenReturn(1);
        when(eventMapper.insertOaSignTaskEvent(any())).thenReturn(1);
        when(renewalGuardMapper.selectForUpdate(201L, "RENEWAL"))
                .thenReturn(guard(701L, 9L));

        assertThatThrownBy(() -> service.transition(current, OaSignTaskStatus.SIGNED,
                OaSignOperatorType.EMPLOYEE, 201L, "SIGNED", null,
                "signed-9", null, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("门闩");
    }

    @Test
    @DisplayName("历史重复非终态续签在一个终态后把门闩移交给下一最早任务")
    void shouldHandOffGuardToNextLegacyOpenRenewalTask()
    {
        OaSignTaskMapper taskMapper = mock(OaSignTaskMapper.class);
        OaSignTaskEventMapper eventMapper = mock(OaSignTaskEventMapper.class);
        OaHrRenewalGuardMapper renewalGuardMapper = mock(OaHrRenewalGuardMapper.class);
        OaSignTaskEventService service = new OaSignTaskEventService(
                taskMapper, eventMapper, renewalGuardMapper, new OaSignTaskStateMachine());
        OaSignTask current = renewalTask(9L, OaSignTaskStatus.PENDING_FINAL_CONFIRM, 3L);
        OaSignTask remaining = renewalTask(10L, OaSignTaskStatus.WAITING_HR_CONFIRM, 2L);
        remaining.setSourceBusinessId("702");
        when(taskMapper.updateStatusWithVersion(9L, "PENDING_FINAL_CONFIRM", "SIGNED", 3L,
                null, null, null, 101L)).thenReturn(1);
        when(eventMapper.insertOaSignTaskEvent(any())).thenReturn(1);
        when(taskMapper.selectOpenRenewalTaskForUpdate(201L)).thenReturn(remaining);
        when(renewalGuardMapper.selectForUpdate(201L, "RENEWAL"))
                .thenReturn(guard(701L, 9L));
        when(renewalGuardMapper.rebindActive(
                201L, "RENEWAL", 701L, 9L, 702L, 10L))
                .thenReturn(1);

        assertThat(service.transition(current, OaSignTaskStatus.SIGNED,
                OaSignOperatorType.EMPLOYEE, 201L, "SIGNED", null,
                "signed-9", null, null).getStatus()).isEqualTo("SIGNED");

        verify(renewalGuardMapper).rebindActive(
                201L, "RENEWAL", 701L, 9L, 702L, 10L);
        verify(renewalGuardMapper, never()).release(any(), any(), any(), any());
    }

    @Test
    @DisplayName("历史非canonical任务先终态时保持仍存活canonical门闩")
    void shouldKeepGuardWhenNonCanonicalLegacyTaskFinishesFirst()
    {
        OaSignTaskMapper taskMapper = mock(OaSignTaskMapper.class);
        OaSignTaskEventMapper eventMapper = mock(OaSignTaskEventMapper.class);
        OaHrRenewalGuardMapper renewalGuardMapper = mock(OaHrRenewalGuardMapper.class);
        OaSignTaskEventService service = new OaSignTaskEventService(
                taskMapper, eventMapper, renewalGuardMapper, new OaSignTaskStateMachine());
        OaSignTask current = renewalTask(10L, OaSignTaskStatus.PENDING_FINAL_CONFIRM, 3L);
        current.setSourceBusinessId("702");
        OaSignTask canonical = renewalTask(9L, OaSignTaskStatus.WAITING_HR_CONFIRM, 2L);
        when(renewalGuardMapper.selectForUpdate(201L, "RENEWAL"))
                .thenReturn(guard(701L, 9L));
        when(taskMapper.updateStatusWithVersion(10L, "PENDING_FINAL_CONFIRM", "SIGNED", 3L,
                null, null, null, 101L)).thenReturn(1);
        when(eventMapper.insertOaSignTaskEvent(any())).thenReturn(1);
        when(taskMapper.selectOpenRenewalTaskForUpdate(201L)).thenReturn(canonical);

        assertThat(service.transition(current, OaSignTaskStatus.SIGNED,
                OaSignOperatorType.EMPLOYEE, 201L, "SIGNED", null,
                "signed-10", null, null).getStatus()).isEqualTo("SIGNED");

        verify(renewalGuardMapper, never()).rebindActive(
                any(), any(), any(), any(), any(), any());
        verify(renewalGuardMapper, never()).release(any(), any(), any(), any());
    }

    @Test
    @DisplayName("迁移留下同action RESERVED门闩时终态重投精确释放")
    void shouldReleaseMatchingReservedGuardOnTerminalReplay()
    {
        OaSignTaskMapper taskMapper = mock(OaSignTaskMapper.class);
        OaSignTaskEventMapper eventMapper = mock(OaSignTaskEventMapper.class);
        OaHrRenewalGuardMapper renewalGuardMapper = mock(OaHrRenewalGuardMapper.class);
        OaSignTaskEventService service = new OaSignTaskEventService(
                taskMapper, eventMapper, renewalGuardMapper, new OaSignTaskStateMachine());
        OaSignTask terminal = renewalTask(9L, OaSignTaskStatus.SIGNED, 4L);
        HrRenewalGuard reserved = guard(701L, null);
        reserved.setStatus("RESERVED");
        when(renewalGuardMapper.selectForUpdate(201L, "RENEWAL")).thenReturn(reserved);
        when(renewalGuardMapper.releaseReserved(
                201L, "RENEWAL", 701L, 4L)).thenReturn(1);

        service.reconcileTerminalRenewalGuard(terminal);

        verify(renewalGuardMapper).releaseReserved(201L, "RENEWAL", 701L, 4L);
        verify(renewalGuardMapper, never()).release(any(), any(), any(), any());
    }

    @Test
    @DisplayName("终态重投精确释放仍绑定该终态任务的 ACTIVE 门闩")
    void shouldReleaseMatchingActiveGuardOnTerminalReplay()
    {
        OaSignTaskMapper taskMapper = mock(OaSignTaskMapper.class);
        OaSignTaskEventMapper eventMapper = mock(OaSignTaskEventMapper.class);
        OaHrRenewalGuardMapper renewalGuardMapper = mock(OaHrRenewalGuardMapper.class);
        OaSignTaskEventService service = new OaSignTaskEventService(
                taskMapper, eventMapper, renewalGuardMapper, new OaSignTaskStateMachine());
        OaSignTask terminal = renewalTask(9L, OaSignTaskStatus.SIGNED, 4L);
        when(renewalGuardMapper.selectForUpdate(201L, "RENEWAL"))
                .thenReturn(guard(701L, 9L));
        when(renewalGuardMapper.release(201L, "RENEWAL", 701L, 9L)).thenReturn(1);

        service.reconcileTerminalRenewalGuard(terminal);

        verify(renewalGuardMapper).release(201L, "RENEWAL", 701L, 9L);
        verify(renewalGuardMapper, never()).releaseReserved(any(), any(), any(), any());
    }

    @Test
    @DisplayName("终态重投看到 IDLE 门闩时幂等且不写门闩")
    void shouldLeaveIdleGuardUntouchedOnTerminalReplay()
    {
        OaSignTaskMapper taskMapper = mock(OaSignTaskMapper.class);
        OaSignTaskEventMapper eventMapper = mock(OaSignTaskEventMapper.class);
        OaHrRenewalGuardMapper renewalGuardMapper = mock(OaHrRenewalGuardMapper.class);
        OaSignTaskEventService service = new OaSignTaskEventService(
                taskMapper, eventMapper, renewalGuardMapper, new OaSignTaskStateMachine());
        HrRenewalGuard idle = guard(null, null);
        idle.setStatus("IDLE");
        when(renewalGuardMapper.selectForUpdate(201L, "RENEWAL")).thenReturn(idle);

        service.reconcileTerminalRenewalGuard(
                renewalTask(9L, OaSignTaskStatus.SIGNED, 4L));

        verify(renewalGuardMapper, never()).releaseReserved(any(), any(), any(), any());
        verify(renewalGuardMapper, never()).release(any(), any(), any(), any());
        verify(renewalGuardMapper, never()).rebindActive(
                any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("终态重投看到残留绑定的脏 IDLE 门闩时拒绝掩盖异常")
    void shouldRejectDirtyIdleGuardOnTerminalReplay()
    {
        OaSignTaskMapper taskMapper = mock(OaSignTaskMapper.class);
        OaSignTaskEventMapper eventMapper = mock(OaSignTaskEventMapper.class);
        OaHrRenewalGuardMapper renewalGuardMapper = mock(OaHrRenewalGuardMapper.class);
        OaSignTaskEventService service = new OaSignTaskEventService(
                taskMapper, eventMapper, renewalGuardMapper, new OaSignTaskStateMachine());
        HrRenewalGuard dirtyIdle = guard(701L, null);
        dirtyIdle.setStatus("IDLE");
        when(renewalGuardMapper.selectForUpdate(201L, "RENEWAL")).thenReturn(dirtyIdle);

        assertThatThrownBy(() -> service.reconcileTerminalRenewalGuard(
                renewalTask(9L, OaSignTaskStatus.SIGNED, 4L)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("绑定不一致");

        verify(renewalGuardMapper, never()).releaseReserved(any(), any(), any(), any());
        verify(renewalGuardMapper, never()).release(any(), any(), any(), any());
    }

    @Test
    @DisplayName("历史终态已SENT且迁移未建门闩时重投幂等返回")
    void shouldTreatMissingGuardAsNoOpForHistoricalTerminalReplay()
    {
        OaSignTaskMapper taskMapper = mock(OaSignTaskMapper.class);
        OaSignTaskEventMapper eventMapper = mock(OaSignTaskEventMapper.class);
        OaHrRenewalGuardMapper renewalGuardMapper = mock(OaHrRenewalGuardMapper.class);
        OaSignTaskEventService service = new OaSignTaskEventService(
                taskMapper, eventMapper, renewalGuardMapper, new OaSignTaskStateMachine());
        when(renewalGuardMapper.selectForUpdate(201L, "RENEWAL")).thenReturn(null);

        service.reconcileTerminalRenewalGuard(
                renewalTask(9L, OaSignTaskStatus.SIGNED, 4L));

        verify(renewalGuardMapper, never()).releaseReserved(any(), any(), any(), any());
        verify(renewalGuardMapper, never()).release(any(), any(), any(), any());
        verify(renewalGuardMapper, never()).rebindActive(
                any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("旧终态重投不得释放已预留给下一action的门闩")
    void shouldKeepGuardReservedForNextActionOnOlderTerminalReplay()
    {
        OaSignTaskMapper taskMapper = mock(OaSignTaskMapper.class);
        OaSignTaskEventMapper eventMapper = mock(OaSignTaskEventMapper.class);
        OaHrRenewalGuardMapper renewalGuardMapper = mock(OaHrRenewalGuardMapper.class);
        OaSignTaskEventService service = new OaSignTaskEventService(
                taskMapper, eventMapper, renewalGuardMapper, new OaSignTaskStateMachine());
        HrRenewalGuard next = guard(702L, null);
        next.setStatus("RESERVED");
        when(renewalGuardMapper.selectForUpdate(201L, "RENEWAL")).thenReturn(next);

        service.reconcileTerminalRenewalGuard(
                renewalTask(9L, OaSignTaskStatus.SIGNED, 4L));

        verify(renewalGuardMapper, never()).releaseReserved(any(), any(), any(), any());
        verify(renewalGuardMapper, never()).release(any(), any(), any(), any());
    }

    @Test
    @DisplayName("非canonical终态重投不得释放另一个存活canonical任务门闩")
    void shouldKeepActiveCanonicalGuardOnNonCanonicalTerminalReplay()
    {
        OaSignTaskMapper taskMapper = mock(OaSignTaskMapper.class);
        OaSignTaskEventMapper eventMapper = mock(OaSignTaskEventMapper.class);
        OaHrRenewalGuardMapper renewalGuardMapper = mock(OaHrRenewalGuardMapper.class);
        OaSignTaskEventService service = new OaSignTaskEventService(
                taskMapper, eventMapper, renewalGuardMapper, new OaSignTaskStateMachine());
        OaSignTask terminal = renewalTask(10L, OaSignTaskStatus.SIGNED, 4L);
        terminal.setSourceBusinessId("702");
        OaSignTask canonical = renewalTask(9L, OaSignTaskStatus.WAITING_HR_CONFIRM, 2L);
        when(renewalGuardMapper.selectForUpdate(201L, "RENEWAL"))
                .thenReturn(guard(701L, 9L));
        when(taskMapper.selectOpenRenewalTaskForUpdate(201L)).thenReturn(canonical);

        service.reconcileTerminalRenewalGuard(terminal);

        verify(renewalGuardMapper, never()).releaseReserved(any(), any(), any(), any());
        verify(renewalGuardMapper, never()).release(any(), any(), any(), any());
        verify(renewalGuardMapper, never()).rebindActive(
                any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("发送失败在同一状态CAS中原子累加重试代数")
    void shouldIncrementRetryGenerationAtomicallyForSendFailure()
    {
        OaSignTaskMapper taskMapper = mock(OaSignTaskMapper.class);
        OaSignTaskEventMapper eventMapper = mock(OaSignTaskEventMapper.class);
        OaHrRenewalGuardMapper renewalGuardMapper = mock(OaHrRenewalGuardMapper.class);
        OaSignTaskEventService service = new OaSignTaskEventService(
                taskMapper, eventMapper, renewalGuardMapper, new OaSignTaskStateMachine());
        OaSignTask current = task(9L, OaSignTaskStatus.SENDING, 5L);
        current.setRetryCount(0);
        current.setFailureCode("SEND_FAILED");
        current.setFailureDetail("网关超时");
        when(taskMapper.updateStatusWithRetryIncrement(9L, "SENDING", "FAILED", 5L,
                "SEND_FAILED", "网关超时", null, 101L)).thenReturn(1);
        when(eventMapper.insertOaSignTaskEvent(any())).thenReturn(1);

        OaSignTask result = service.transition(current, OaSignTaskStatus.FAILED,
                OaSignOperatorType.SYSTEM, null, "SEND_FAILED", "网关超时",
                null, "127.0.0.1", "JUnit");

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getVersion()).isEqualTo(6L);
        assertThat(result.getRetryCount()).isEqualTo(1);
        verify(taskMapper, never()).updateStatusWithVersion(any(), any(), any(), any(),
                any(), any(), any(), any());
        verify(eventMapper).insertOaSignTaskEvent(any());
    }

    @Test
    @DisplayName("事件hash按固定字段顺序计算")
    void shouldHashStableOrderedFields()
    {
        OaSignTaskEvent event = new OaSignTaskEvent();
        event.setTaskId(9L);
        event.setFromStatus("VIEWED");
        event.setToStatus("SIGNED");
        event.setOperatorType("EMPLOYEE");
        event.setOperatorUserId(201L);
        event.setReasonCode("SIGNED");
        event.setReasonDetail("确认");
        event.setRequestId("sign-9");
        event.setIpAddress("127.0.0.1");
        event.setUserAgent("JUnit");
        event.setPrevEventHash("a".repeat(64));
        event.setCreatedTime(new Date(1_752_220_800_000L));

        String first = OaSignTaskEventService.calculateEventHash(event);
        String second = OaSignTaskEventService.calculateEventHash(event);
        event.setReasonDetail("确认2");

        assertThat(first).isEqualTo(second).hasSize(64);
        assertThat(OaSignTaskEventService.calculateEventHash(event)).isNotEqualTo(first);
    }

    private OaSignTask task(Long taskId, OaSignTaskStatus status, Long version)
    {
        OaSignTask task = new OaSignTask();
        task.setTaskId(taskId);
        task.setAssignedHrUserId(101L);
        task.setStatus(status.name());
        task.setVersion(version);
        return task;
    }

    private OaSignTask renewalTask(Long taskId, OaSignTaskStatus status, Long version)
    {
        OaSignTask task = task(taskId, status, version);
        task.setScenario("RENEWAL");
        task.setEmployeeId(201L);
        task.setSourceType("HR_LIFECYCLE_ACTION");
        task.setSourceBusinessId("701");
        return task;
    }

    private HrRenewalGuard guard(Long actionId, Long taskId)
    {
        HrRenewalGuard guard = new HrRenewalGuard();
        guard.setEmployeeId(201L);
        guard.setScenario("RENEWAL");
        guard.setStatus("ACTIVE");
        guard.setActionId(actionId);
        guard.setTaskId(taskId);
        guard.setVersion(4L);
        return guard;
    }
}
