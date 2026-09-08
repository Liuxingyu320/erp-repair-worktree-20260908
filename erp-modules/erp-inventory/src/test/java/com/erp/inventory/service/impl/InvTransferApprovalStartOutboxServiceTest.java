package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.erp.approval.api.domain.ApprovalStartRequest;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.datascope.annotation.DataScope;
import com.erp.inventory.constant.InvStatusConstants;
import com.erp.inventory.domain.InvTransferApprovalStartOutbox;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.vo.InvTransferApprovalStartOutboxQuery;
import com.erp.inventory.mapper.InvTransferApprovalStartOutboxMapper;
import com.erp.inventory.mapper.InvTransferOrderMapper;

@DisplayName("调拨统一审批发起发件箱服务")
class InvTransferApprovalStartOutboxServiceTest
{
    @Test
    @DisplayName("业务事实入队必须加入调用方事务")
    void shouldRequireCallerTransactionForEnqueue() throws Exception
    {
        Method enqueue = InvTransferApprovalStartOutboxService.class
                .getMethod("enqueue", InvTransferOrder.class,
                        ApprovalStartRequest.class, String.class);
        Transactional transactional = enqueue.getAnnotation(
                Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation())
                .isEqualTo(Propagation.MANDATORY);
        assertRequiresNew("claim", InvTransferApprovalStartOutbox.class);
        assertRequiresNew("recordRemoteSucceeded",
                InvTransferApprovalStartOutbox.class, Long.class,
                String.class, Integer.class, Integer.class);
        assertRequiresNew("finalizeRemoteSuccess",
                InvTransferApprovalStartOutbox.class, String.class);
        assertRequiresNew("markRetry",
                InvTransferApprovalStartOutbox.class, int.class,
                java.util.Date.class, Integer.class, String.class,
                String.class);
        assertRequiresNew("markFailed",
                InvTransferApprovalStartOutbox.class, Integer.class,
                String.class, String.class);
        assertRequiresNew("replayFailed",
                InvTransferApprovalStartOutboxQuery.class, Long.class,
                String.class);
        assertDataScoped("selectOps",
                InvTransferApprovalStartOutboxQuery.class);
        assertDataScoped("selectSummary",
                InvTransferApprovalStartOutboxQuery.class);
        assertDataScoped("replayFailed",
                InvTransferApprovalStartOutboxQuery.class, Long.class,
                String.class);
    }

    @Test
    @DisplayName("并发 claim 只有一个调度器获得远端调用权")
    void shouldAllowOnlyOneConcurrentClaim() throws Exception
    {
        InvTransferApprovalStartOutboxMapper mapper = mock(
                InvTransferApprovalStartOutboxMapper.class);
        AtomicBoolean winner = new AtomicBoolean();
        when(mapper.claimForSubmitting(10L,
                InvTransferApprovalStartOutboxService.PENDING, 0L))
                .thenAnswer(invocation -> winner.compareAndSet(false, true)
                        ? 1 : 0);
        InvTransferApprovalStartOutboxService service = service(mapper,
                mock(InvTransferOrderMapper.class));
        InvTransferApprovalStartOutbox first = pendingRow();
        InvTransferApprovalStartOutbox second = pendingRow();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try
        {
            Future<Boolean> a = workers.submit(() ->
            {
                start.await(5, TimeUnit.SECONDS);
                return service.claim(first);
            });
            Future<Boolean> b = workers.submit(() ->
            {
                start.await(5, TimeUnit.SECONDS);
                return service.claim(second);
            });
            start.countDown();

            int winners = (a.get(5, TimeUnit.SECONDS) ? 1 : 0)
                    + (b.get(5, TimeUnit.SECONDS) ? 1 : 0);
            assertThat(winners).isEqualTo(1);
        }
        finally
        {
            workers.shutdownNow();
        }
    }

    @Test
    @DisplayName("远端成功关联使用调拨单状态轮次版本 CAS")
    void shouldFinalizeRemoteSuccessWithBusinessCas()
    {
        InvTransferApprovalStartOutboxMapper mapper = mock(
                InvTransferApprovalStartOutboxMapper.class);
        InvTransferOrderMapper orderMapper = mock(InvTransferOrderMapper.class);
        InvTransferApprovalStartOutbox locked = remoteSucceededRow();
        InvTransferOrder transfer = submittedTransfer(null);
        when(mapper.selectByIdForUpdate(10L)).thenReturn(locked);
        when(orderMapper.selectInvTransferOrderByIdForUpdate(88L))
                .thenReturn(transfer);
        when(orderMapper.finalizeNativeApprovalStart(88L, 1, 7L,
                9001L, "worker")).thenReturn(1);
        when(mapper.markSucceeded(10L,
                InvTransferApprovalStartOutboxService.REMOTE_SUCCEEDED, 4L))
                .thenReturn(1);
        InvTransferApprovalStartOutbox candidate = remoteSucceededRow();

        service(mapper, orderMapper).finalizeRemoteSuccess(candidate,
                "worker");

        verify(orderMapper).finalizeNativeApprovalStart(88L, 1, 7L,
                9001L, "worker");
        verify(mapper).markSucceeded(10L,
                InvTransferApprovalStartOutboxService.REMOTE_SUCCEEDED, 4L);
        assertThat(candidate.getStatus())
                .isEqualTo(InvTransferApprovalStartOutboxService.SUCCEEDED);
        assertThat(candidate.getVersion()).isEqualTo(5L);
    }

    @Test
    @DisplayName("已关联同一实例的恢复派发是幂等的")
    void shouldCompleteWhenSameInstanceWasAlreadyAttached()
    {
        InvTransferApprovalStartOutboxMapper mapper = mock(
                InvTransferApprovalStartOutboxMapper.class);
        InvTransferOrderMapper orderMapper = mock(InvTransferOrderMapper.class);
        InvTransferApprovalStartOutbox locked = remoteSucceededRow();
        when(mapper.selectByIdForUpdate(10L)).thenReturn(locked);
        when(orderMapper.selectInvTransferOrderByIdForUpdate(88L))
                .thenReturn(submittedTransfer(9001L));
        when(mapper.markSucceeded(10L,
                InvTransferApprovalStartOutboxService.REMOTE_SUCCEEDED, 4L))
                .thenReturn(1);

        service(mapper, orderMapper).finalizeRemoteSuccess(
                remoteSucceededRow(), "worker");

        verify(orderMapper, never()).finalizeNativeApprovalStart(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), anyString());
        verify(mapper).markSucceeded(10L,
                InvTransferApprovalStartOutboxService.REMOTE_SUCCEEDED, 4L);
    }

    @Test
    @DisplayName("调拨业务状态已变化时不隐藏孤儿实例风险")
    void shouldExposePermanentBusinessStateConflict()
    {
        InvTransferApprovalStartOutboxMapper mapper = mock(
                InvTransferApprovalStartOutboxMapper.class);
        InvTransferOrderMapper orderMapper = mock(InvTransferOrderMapper.class);
        when(mapper.selectByIdForUpdate(10L))
                .thenReturn(remoteSucceededRow());
        InvTransferOrder changed = submittedTransfer(null);
        changed.setStatus(InvStatusConstants.CLOSED);
        when(orderMapper.selectInvTransferOrderByIdForUpdate(88L))
                .thenReturn(changed);

        assertThatThrownBy(() -> service(mapper, orderMapper)
                .finalizeRemoteSuccess(remoteSucceededRow(), "worker"))
                .isInstanceOf(
                        InvTransferApprovalStartOutboxService.PermanentFailure.class)
                .hasMessageContaining("待关联审批轮次");
        verify(mapper, never()).markSucceeded(
                org.mockito.ArgumentMatchers.any(), anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("远端实例轮次不一致时在读取业务单前拒绝关联")
    void shouldRejectRemoteRoundMismatchBeforeBindingBusiness()
    {
        InvTransferApprovalStartOutboxMapper mapper = mock(
                InvTransferApprovalStartOutboxMapper.class);
        InvTransferOrderMapper orderMapper = mock(InvTransferOrderMapper.class);
        InvTransferApprovalStartOutbox mismatch = remoteSucceededRow();
        mismatch.setRemoteBusinessRound(2);
        when(mapper.selectByIdForUpdate(10L)).thenReturn(mismatch);

        assertThatThrownBy(() -> service(mapper, orderMapper)
                .finalizeRemoteSuccess(mismatch, "worker"))
                .isInstanceOf(
                        InvTransferApprovalStartOutboxService.PermanentFailure.class)
                .hasMessageContaining("业务轮次");
        verify(orderMapper, never()).selectInvTransferOrderByIdForUpdate(88L);
        verify(orderMapper, never()).finalizeNativeApprovalStart(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), anyString());
    }

    @Test
    @DisplayName("调拨版本变化但状态轮次未变时拒绝绑定过期审批实例")
    void shouldRejectStaleTransferSnapshotBeforeBindingInstance()
    {
        InvTransferApprovalStartOutboxMapper mapper = mock(
                InvTransferApprovalStartOutboxMapper.class);
        InvTransferOrderMapper orderMapper = mock(InvTransferOrderMapper.class);
        InvTransferApprovalStartOutbox locked = remoteSucceededRow();
        InvTransferOrder changed = submittedTransfer(null);
        changed.setVersion(8L);
        when(mapper.selectByIdForUpdate(10L)).thenReturn(locked);
        when(orderMapper.selectInvTransferOrderByIdForUpdate(88L))
                .thenReturn(changed);

        assertThatThrownBy(() -> service(mapper, orderMapper)
                .finalizeRemoteSuccess(locked, "worker"))
                .isInstanceOf(
                        InvTransferApprovalStartOutboxService.PermanentFailure.class)
                .hasMessageContaining("快照版本")
                .satisfies(error -> assertThat(
                        ((InvTransferApprovalStartOutboxService.PermanentFailure)
                                error).getErrorCode())
                        .isEqualTo("TRANSFER_VERSION_CONFLICT"));
        verify(orderMapper, never()).finalizeNativeApprovalStart(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), anyString());
    }

    @Test
    @DisplayName("存量异常行的非正远端实例ID在读取调拨单前被隔离")
    void shouldRejectLegacyNonPositiveInstanceBeforeBindingBusiness()
    {
        InvTransferApprovalStartOutboxMapper mapper = mock(
                InvTransferApprovalStartOutboxMapper.class);
        InvTransferOrderMapper orderMapper = mock(InvTransferOrderMapper.class);
        InvTransferApprovalStartOutbox invalid = remoteSucceededRow();
        invalid.setRemoteInstanceId(0L);
        when(mapper.selectByIdForUpdate(10L)).thenReturn(invalid);

        assertThatThrownBy(() -> service(mapper, orderMapper)
                .finalizeRemoteSuccess(invalid, "worker"))
                .isInstanceOf(
                        InvTransferApprovalStartOutboxService.PermanentFailure.class)
                .hasMessageContaining("实例关联缺失")
                .satisfies(error -> assertThat(
                        ((InvTransferApprovalStartOutboxService.PermanentFailure)
                                error).getErrorCode())
                        .isEqualTo("REMOTE_INSTANCE_MISSING"));
        verify(orderMapper, never()).selectInvTransferOrderByIdForUpdate(88L);
    }

    @Test
    @DisplayName("重复入队返回同一幂等事实而不新建第二条")
    void shouldReturnExistingOutboxForDuplicateEnqueue()
    {
        InvTransferApprovalStartOutboxMapper mapper = mock(
                InvTransferApprovalStartOutboxMapper.class);
        when(mapper.insertOutbox(
                org.mockito.ArgumentMatchers.any())).thenReturn(0);
        InvTransferApprovalStartOutbox existing = pendingRow();
        existing.setIdempotencyKey("INV_TRANSFER:88:1");
        when(mapper.selectByTransferRound(88L, 1)).thenReturn(existing);

        InvTransferApprovalStartOutbox result = service(mapper,
                mock(InvTransferOrderMapper.class)).enqueue(
                        submittedTransfer(null), request(), "tester");

        assertThat(result).isSameAs(existing);
    }

    @Test
    @DisplayName("人工重放使用版本 CAS")
    void shouldReplayFailedWithVersionCas()
    {
        InvTransferApprovalStartOutboxMapper mapper = mock(
                InvTransferApprovalStartOutboxMapper.class);
        InvTransferApprovalStartOutboxQuery query = query(10L);
        InvTransferApprovalStartOutbox failed = pendingRow();
        failed.setStatus(InvTransferApprovalStartOutboxService.FAILED);
        failed.setVersion(8L);
        when(mapper.selectScopedByIdForUpdate(query)).thenReturn(failed);
        when(mapper.replayFailedScoped(query, 8L, "operator"))
                .thenReturn(0);

        assertThatThrownBy(() -> service(mapper,
                mock(InvTransferOrderMapper.class)).replayFailed(query, 8L,
                        "operator"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("状态已变化");
    }

    @Test
    @DisplayName("组织范围外记录按不存在返回且不执行重放 CAS")
    void shouldHideOutOfScopeReplayTarget()
    {
        InvTransferApprovalStartOutboxMapper mapper = mock(
                InvTransferApprovalStartOutboxMapper.class);
        InvTransferApprovalStartOutboxQuery query = query(10L);

        assertThatThrownBy(() -> service(mapper,
                mock(InvTransferOrderMapper.class)).replayFailed(query, 8L,
                        "operator"))
                .isInstanceOf(ServiceException.class)
                .extracting("code").isEqualTo(404);
        verify(mapper, never()).replayFailedScoped(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "REMOTE_ROUND_MISMATCH", "INSTANCE_CONFLICT",
            "TRANSFER_NOT_FOUND", "TRANSFER_STATE_CHANGED",
            "TRANSFER_VERSION_CONFLICT" })
    @DisplayName("永久失败态禁止普通人工重放")
    void shouldBlockReplayForPermanentFailure(String errorCode)
    {
        InvTransferApprovalStartOutboxMapper mapper = mock(
                InvTransferApprovalStartOutboxMapper.class);
        InvTransferApprovalStartOutboxQuery query = query(10L);
        InvTransferApprovalStartOutbox failed = pendingRow();
        failed.setStatus(InvTransferApprovalStartOutboxService.FAILED);
        failed.setVersion(8L);
        failed.setLastErrorCode(errorCode);
        when(mapper.selectScopedByIdForUpdate(query)).thenReturn(failed);

        assertThatThrownBy(() -> service(mapper,
                mock(InvTransferOrderMapper.class)).replayFailed(query, 8L,
                        "operator"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("禁止普通重放");
        verify(mapper, never()).replayFailedScoped(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private static InvTransferApprovalStartOutboxService service(
            InvTransferApprovalStartOutboxMapper mapper,
            InvTransferOrderMapper orderMapper)
    {
        return new InvTransferApprovalStartOutboxService(mapper, orderMapper);
    }

    private static void assertRequiresNew(String methodName,
            Class<?>... parameterTypes) throws Exception
    {
        Transactional transactional =
                InvTransferApprovalStartOutboxService.class
                        .getMethod(methodName, parameterTypes)
                        .getAnnotation(Transactional.class);
        assertThat(transactional).as(methodName).isNotNull();
        assertThat(transactional.propagation()).as(methodName)
                .isEqualTo(Propagation.REQUIRES_NEW);
    }

    private static void assertDataScoped(String methodName,
            Class<?>... parameterTypes) throws Exception
    {
        DataScope annotation = InvTransferApprovalStartOutboxService.class
                .getMethod(methodName, parameterTypes)
                .getAnnotation(DataScope.class);
        assertThat(annotation).as(methodName).isNotNull();
        assertThat(annotation.deptAlias()).isEqualTo("d");
    }

    private static InvTransferApprovalStartOutboxQuery query(Long outboxId)
    {
        InvTransferApprovalStartOutboxQuery query =
                new InvTransferApprovalStartOutboxQuery();
        query.setOutboxId(outboxId);
        return query;
    }

    private static InvTransferApprovalStartOutbox pendingRow()
    {
        InvTransferApprovalStartOutbox row =
                new InvTransferApprovalStartOutbox();
        row.setOutboxId(10L);
        row.setTransferId(88L);
        row.setBusinessRound(1);
        row.setStatus(InvTransferApprovalStartOutboxService.PENDING);
        row.setVersion(0L);
        row.setRetryCount(0);
        row.setTransferVersion(7L);
        return row;
    }

    private static InvTransferApprovalStartOutbox remoteSucceededRow()
    {
        InvTransferApprovalStartOutbox row = pendingRow();
        row.setStatus(
                InvTransferApprovalStartOutboxService.REMOTE_SUCCEEDED);
        row.setVersion(4L);
        row.setRemoteInstanceId(9001L);
        row.setRemoteStatus("RUNNING");
        row.setRemoteBusinessRound(1);
        return row;
    }

    private static InvTransferOrder submittedTransfer(Long instanceId)
    {
        InvTransferOrder transfer = new InvTransferOrder();
        transfer.setTransferId(88L);
        transfer.setStatus(InvStatusConstants.SUBMITTED);
        transfer.setApprovalEngine(
                InventoryUnifiedApprovalService.ENGINE_NATIVE);
        transfer.setApprovalRound(1);
        transfer.setApprovalInstanceId(instanceId);
        transfer.setVersion(7L);
        return transfer;
    }

    private static ApprovalStartRequest request()
    {
        ApprovalStartRequest request = new ApprovalStartRequest();
        request.setBusinessCode(InventoryUnifiedApprovalService.TRANSFER);
        request.setBusinessId("88");
        request.setBusinessRound(1);
        request.setApplicantId(7L);
        request.setIdempotencyKey("INV_TRANSFER:88:1");
        return request;
    }
}
