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
import com.erp.inventory.domain.InvStockCheck;
import com.erp.inventory.domain.InvStockCheckApprovalStartOutbox;
import com.erp.inventory.domain.vo.InvStockCheckApprovalStartOutboxQuery;
import com.erp.inventory.mapper.InvStockCheckApprovalStartOutboxMapper;
import com.erp.inventory.mapper.InvStockCheckMapper;

@DisplayName("盘点统一审批发起发件箱服务")
class InvStockCheckApprovalStartOutboxServiceTest
{
    @Test
    @DisplayName("入队加入业务事务而状态转换使用独立短事务")
    void shouldDeclareRequiredTransactionBoundaries() throws Exception
    {
        Method enqueue = InvStockCheckApprovalStartOutboxService.class
                .getMethod("enqueue", InvStockCheck.class,
                        ApprovalStartRequest.class, String.class);
        Transactional transactional = enqueue.getAnnotation(
                Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation())
                .isEqualTo(Propagation.MANDATORY);
        assertRequiresNew("claim", InvStockCheckApprovalStartOutbox.class);
        assertRequiresNew("recordRemoteSucceeded",
                InvStockCheckApprovalStartOutbox.class, Long.class,
                String.class, Integer.class, Integer.class);
        assertRequiresNew("finalizeRemoteSuccess",
                InvStockCheckApprovalStartOutbox.class, String.class);
        assertRequiresNew("markRetry",
                InvStockCheckApprovalStartOutbox.class, int.class,
                java.util.Date.class, Integer.class, String.class,
                String.class);
        assertRequiresNew("markFailed",
                InvStockCheckApprovalStartOutbox.class, Integer.class,
                String.class, String.class);
        assertRequiresNew("replayFailed",
                InvStockCheckApprovalStartOutboxQuery.class, Long.class,
                String.class);
        assertDataScoped("selectOps",
                InvStockCheckApprovalStartOutboxQuery.class);
        assertDataScoped("selectSummary",
                InvStockCheckApprovalStartOutboxQuery.class);
        assertDataScoped("replayFailed",
                InvStockCheckApprovalStartOutboxQuery.class, Long.class,
                String.class);
    }

    @Test
    @DisplayName("并发 claim 只有一个派发器获得远端调用权")
    void shouldAllowOnlyOneConcurrentClaim() throws Exception
    {
        InvStockCheckApprovalStartOutboxMapper mapper = mock(
                InvStockCheckApprovalStartOutboxMapper.class);
        AtomicBoolean winner = new AtomicBoolean();
        when(mapper.claimForSubmitting(10L,
                InvStockCheckApprovalStartOutboxService.PENDING, 0L))
                .thenAnswer(invocation -> winner.compareAndSet(false, true)
                        ? 1 : 0);
        InvStockCheckApprovalStartOutboxService service = service(mapper,
                mock(InvStockCheckMapper.class));
        InvStockCheckApprovalStartOutbox first = pendingRow();
        InvStockCheckApprovalStartOutbox second = pendingRow();
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
    @DisplayName("远端成功关联使用盘点状态轮次与 rowVersion CAS")
    void shouldFinalizeRemoteSuccessWithBusinessCas()
    {
        InvStockCheckApprovalStartOutboxMapper mapper = mock(
                InvStockCheckApprovalStartOutboxMapper.class);
        InvStockCheckMapper checkMapper = mock(InvStockCheckMapper.class);
        when(mapper.selectByIdForUpdate(10L))
                .thenReturn(remoteSucceededRow());
        when(checkMapper.selectInvStockCheckByIdForUpdate(88L))
                .thenReturn(pendingCheck(null));
        when(checkMapper.finalizeNativeApprovalStart(88L, 1, 7L,
                9001L, "worker")).thenReturn(1);
        when(mapper.markSucceeded(10L,
                InvStockCheckApprovalStartOutboxService.REMOTE_SUCCEEDED,
                4L)).thenReturn(1);
        InvStockCheckApprovalStartOutbox candidate = remoteSucceededRow();

        service(mapper, checkMapper).finalizeRemoteSuccess(candidate,
                "worker");

        verify(checkMapper).finalizeNativeApprovalStart(88L, 1, 7L,
                9001L, "worker");
        verify(mapper).markSucceeded(10L,
                InvStockCheckApprovalStartOutboxService.REMOTE_SUCCEEDED,
                4L);
        assertThat(candidate.getStatus())
                .isEqualTo(InvStockCheckApprovalStartOutboxService.SUCCEEDED);
    }

    @Test
    @DisplayName("rowVersion 过期时保留远端成功供后续恢复")
    void shouldRetryWhenBusinessVersionBecameStale()
    {
        InvStockCheckApprovalStartOutboxMapper mapper = mock(
                InvStockCheckApprovalStartOutboxMapper.class);
        InvStockCheckMapper checkMapper = mock(InvStockCheckMapper.class);
        when(mapper.selectByIdForUpdate(10L))
                .thenReturn(remoteSucceededRow());
        when(checkMapper.selectInvStockCheckByIdForUpdate(88L))
                .thenReturn(pendingCheck(null));
        when(checkMapper.finalizeNativeApprovalStart(88L, 1, 7L,
                9001L, "worker")).thenReturn(0);

        assertThatThrownBy(() -> service(mapper, checkMapper)
                .finalizeRemoteSuccess(remoteSucceededRow(), "worker"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("并发冲突");
        verify(mapper, never()).markSucceeded(
                org.mockito.ArgumentMatchers.any(), anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("远端实例轮次不一致时在读取盘点单前拒绝关联")
    void shouldRejectRemoteRoundMismatchBeforeBindingBusiness()
    {
        InvStockCheckApprovalStartOutboxMapper mapper = mock(
                InvStockCheckApprovalStartOutboxMapper.class);
        InvStockCheckMapper checkMapper = mock(InvStockCheckMapper.class);
        InvStockCheckApprovalStartOutbox mismatch = remoteSucceededRow();
        mismatch.setRemoteBusinessRound(2);
        when(mapper.selectByIdForUpdate(10L)).thenReturn(mismatch);

        assertThatThrownBy(() -> service(mapper, checkMapper)
                .finalizeRemoteSuccess(mismatch, "worker"))
                .isInstanceOf(
                        InvStockCheckApprovalStartOutboxService.PermanentFailure.class)
                .hasMessageContaining("业务轮次");
        verify(checkMapper, never()).selectInvStockCheckByIdForUpdate(88L);
        verify(checkMapper, never()).finalizeNativeApprovalStart(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), anyString());
    }

    @Test
    @DisplayName("盘点版本变化但状态轮次未变时拒绝绑定过期审批实例")
    void shouldRejectStaleCheckSnapshotBeforeBindingInstance()
    {
        InvStockCheckApprovalStartOutboxMapper mapper = mock(
                InvStockCheckApprovalStartOutboxMapper.class);
        InvStockCheckMapper checkMapper = mock(InvStockCheckMapper.class);
        InvStockCheckApprovalStartOutbox locked = remoteSucceededRow();
        InvStockCheck changed = pendingCheck(null);
        changed.setRowVersion(8L);
        when(mapper.selectByIdForUpdate(10L)).thenReturn(locked);
        when(checkMapper.selectInvStockCheckByIdForUpdate(88L))
                .thenReturn(changed);

        assertThatThrownBy(() -> service(mapper, checkMapper)
                .finalizeRemoteSuccess(locked, "worker"))
                .isInstanceOf(
                        InvStockCheckApprovalStartOutboxService.PermanentFailure.class)
                .hasMessageContaining("快照版本")
                .satisfies(error -> assertThat(
                        ((InvStockCheckApprovalStartOutboxService.PermanentFailure)
                                error).getErrorCode())
                        .isEqualTo("STOCK_CHECK_VERSION_CONFLICT"));
        verify(checkMapper, never()).finalizeNativeApprovalStart(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), anyString());
    }

    @Test
    @DisplayName("存量异常行的非正远端实例ID在读取盘点单前被隔离")
    void shouldRejectLegacyNonPositiveInstanceBeforeBindingBusiness()
    {
        InvStockCheckApprovalStartOutboxMapper mapper = mock(
                InvStockCheckApprovalStartOutboxMapper.class);
        InvStockCheckMapper checkMapper = mock(InvStockCheckMapper.class);
        InvStockCheckApprovalStartOutbox invalid = remoteSucceededRow();
        invalid.setRemoteInstanceId(0L);
        when(mapper.selectByIdForUpdate(10L)).thenReturn(invalid);

        assertThatThrownBy(() -> service(mapper, checkMapper)
                .finalizeRemoteSuccess(invalid, "worker"))
                .isInstanceOf(
                        InvStockCheckApprovalStartOutboxService.PermanentFailure.class)
                .hasMessageContaining("实例关联缺失")
                .satisfies(error -> assertThat(
                        ((InvStockCheckApprovalStartOutboxService.PermanentFailure)
                                error).getErrorCode())
                        .isEqualTo("REMOTE_INSTANCE_MISSING"));
        verify(checkMapper, never()).selectInvStockCheckByIdForUpdate(88L);
    }

    @Test
    @DisplayName("已关联相同实例的恢复是幂等的")
    void shouldCompleteWhenSameInstanceWasAlreadyAttached()
    {
        InvStockCheckApprovalStartOutboxMapper mapper = mock(
                InvStockCheckApprovalStartOutboxMapper.class);
        InvStockCheckMapper checkMapper = mock(InvStockCheckMapper.class);
        when(mapper.selectByIdForUpdate(10L))
                .thenReturn(remoteSucceededRow());
        when(checkMapper.selectInvStockCheckByIdForUpdate(88L))
                .thenReturn(pendingCheck(9001L));
        when(mapper.markSucceeded(10L,
                InvStockCheckApprovalStartOutboxService.REMOTE_SUCCEEDED,
                4L)).thenReturn(1);

        service(mapper, checkMapper).finalizeRemoteSuccess(
                remoteSucceededRow(), "worker");

        verify(checkMapper, never()).finalizeNativeApprovalStart(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), anyString());
    }

    @Test
    @DisplayName("重复入队复用同一业务轮次的幂等事实")
    void shouldReturnExistingOutboxForDuplicateEnqueue()
    {
        InvStockCheckApprovalStartOutboxMapper mapper = mock(
                InvStockCheckApprovalStartOutboxMapper.class);
        when(mapper.insertOutbox(
                org.mockito.ArgumentMatchers.any())).thenReturn(0);
        InvStockCheckApprovalStartOutbox existing = pendingRow();
        existing.setIdempotencyKey("INV_STOCK_CHECK:88:1");
        when(mapper.selectByCheckRound(88L, 1)).thenReturn(existing);

        InvStockCheckApprovalStartOutbox result = service(mapper,
                mock(InvStockCheckMapper.class)).enqueue(
                        pendingCheck(null), request(), "tester");

        assertThat(result).isSameAs(existing);
    }

    @Test
    @DisplayName("人工重放使用版本 CAS")
    void shouldReplayFailedWithVersionCas()
    {
        InvStockCheckApprovalStartOutboxMapper mapper = mock(
                InvStockCheckApprovalStartOutboxMapper.class);
        InvStockCheckApprovalStartOutboxQuery query = query(10L);
        InvStockCheckApprovalStartOutbox failed = pendingRow();
        failed.setStatus(InvStockCheckApprovalStartOutboxService.FAILED);
        failed.setVersion(8L);
        when(mapper.selectScopedByIdForUpdate(query)).thenReturn(failed);
        when(mapper.replayFailedScoped(query, 8L, "operator"))
                .thenReturn(0);

        assertThatThrownBy(() -> service(mapper,
                mock(InvStockCheckMapper.class)).replayFailed(query, 8L,
                        "operator"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("状态已变化");
    }

    @Test
    @DisplayName("组织范围外记录按不存在返回且不执行重放 CAS")
    void shouldHideOutOfScopeReplayTarget()
    {
        InvStockCheckApprovalStartOutboxMapper mapper = mock(
                InvStockCheckApprovalStartOutboxMapper.class);
        InvStockCheckApprovalStartOutboxQuery query = query(10L);

        assertThatThrownBy(() -> service(mapper,
                mock(InvStockCheckMapper.class)).replayFailed(query, 8L,
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
            "STOCK_CHECK_NOT_FOUND", "STOCK_CHECK_STATE_CHANGED",
            "STOCK_CHECK_VERSION_CONFLICT" })
    @DisplayName("永久失败态禁止普通人工重放")
    void shouldBlockReplayForPermanentFailure(String errorCode)
    {
        InvStockCheckApprovalStartOutboxMapper mapper = mock(
                InvStockCheckApprovalStartOutboxMapper.class);
        InvStockCheckApprovalStartOutboxQuery query = query(10L);
        InvStockCheckApprovalStartOutbox failed = pendingRow();
        failed.setStatus(InvStockCheckApprovalStartOutboxService.FAILED);
        failed.setVersion(8L);
        failed.setLastErrorCode(errorCode);
        when(mapper.selectScopedByIdForUpdate(query)).thenReturn(failed);

        assertThatThrownBy(() -> service(mapper,
                mock(InvStockCheckMapper.class)).replayFailed(query, 8L,
                        "operator"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("禁止普通重放");
        verify(mapper, never()).replayFailedScoped(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private static InvStockCheckApprovalStartOutboxService service(
            InvStockCheckApprovalStartOutboxMapper mapper,
            InvStockCheckMapper checkMapper)
    {
        return new InvStockCheckApprovalStartOutboxService(mapper,
                checkMapper);
    }

    private static void assertRequiresNew(String methodName,
            Class<?>... parameterTypes) throws Exception
    {
        Transactional transactional =
                InvStockCheckApprovalStartOutboxService.class
                        .getMethod(methodName, parameterTypes)
                        .getAnnotation(Transactional.class);
        assertThat(transactional).as(methodName).isNotNull();
        assertThat(transactional.propagation()).as(methodName)
                .isEqualTo(Propagation.REQUIRES_NEW);
    }

    private static void assertDataScoped(String methodName,
            Class<?>... parameterTypes) throws Exception
    {
        DataScope annotation = InvStockCheckApprovalStartOutboxService.class
                .getMethod(methodName, parameterTypes)
                .getAnnotation(DataScope.class);
        assertThat(annotation).as(methodName).isNotNull();
        assertThat(annotation.deptAlias()).isEqualTo("d");
    }

    private static InvStockCheckApprovalStartOutboxQuery query(Long outboxId)
    {
        InvStockCheckApprovalStartOutboxQuery query =
                new InvStockCheckApprovalStartOutboxQuery();
        query.setOutboxId(outboxId);
        return query;
    }

    private static InvStockCheckApprovalStartOutbox pendingRow()
    {
        InvStockCheckApprovalStartOutbox row =
                new InvStockCheckApprovalStartOutbox();
        row.setOutboxId(10L);
        row.setCheckId(88L);
        row.setBusinessRound(1);
        row.setStatus(InvStockCheckApprovalStartOutboxService.PENDING);
        row.setVersion(0L);
        row.setRetryCount(0);
        row.setCheckRowVersion(7L);
        return row;
    }

    private static InvStockCheckApprovalStartOutbox remoteSucceededRow()
    {
        InvStockCheckApprovalStartOutbox row = pendingRow();
        row.setStatus(
                InvStockCheckApprovalStartOutboxService.REMOTE_SUCCEEDED);
        row.setVersion(4L);
        row.setRemoteInstanceId(9001L);
        row.setRemoteStatus("RUNNING");
        row.setRemoteBusinessRound(1);
        return row;
    }

    private static InvStockCheck pendingCheck(Long instanceId)
    {
        InvStockCheck check = new InvStockCheck();
        check.setCheckId(88L);
        check.setStatus(InvStatusConstants.PENDING_APPROVAL);
        check.setApprovalEngine(
                InventoryUnifiedApprovalService.ENGINE_NATIVE);
        check.setApprovalRound(1);
        check.setApprovalInstanceId(instanceId);
        check.setRowVersion(7L);
        return check;
    }

    private static ApprovalStartRequest request()
    {
        ApprovalStartRequest request = new ApprovalStartRequest();
        request.setBusinessCode(
                InventoryUnifiedApprovalService.STOCK_CHECK);
        request.setBusinessId("88");
        request.setBusinessRound(1);
        request.setApplicantId(7L);
        request.setIdempotencyKey("INV_STOCK_CHECK:88:1");
        return request;
    }
}
