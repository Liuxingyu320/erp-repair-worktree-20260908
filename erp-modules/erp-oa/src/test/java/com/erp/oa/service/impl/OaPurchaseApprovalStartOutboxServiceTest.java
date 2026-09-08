package com.erp.oa.service.impl;

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
import com.alibaba.fastjson2.JSON;
import com.erp.approval.api.domain.ApprovalStartRequest;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.datascope.annotation.DataScope;
import com.erp.oa.domain.OaPurchase;
import com.erp.oa.domain.OaPurchaseApprovalStartOutbox;
import com.erp.oa.domain.vo.OaPurchaseApprovalStartOutboxQuery;
import com.erp.oa.mapper.OaPurchaseApprovalStartOutboxMapper;
import com.erp.oa.mapper.OaPurchaseMapper;

@DisplayName("OA采购统一审批发起发件箱服务")
class OaPurchaseApprovalStartOutboxServiceTest
{
    @Test
    @DisplayName("业务事实入队必须加入调用方事务，其余状态转换使用新事务")
    void shouldDeclareRequiredTransactionBoundaries() throws Exception
    {
        Method enqueue = OaPurchaseApprovalStartOutboxService.class
                .getMethod("enqueue", OaPurchase.class,
                        ApprovalStartRequest.class, String.class);
        Transactional transactional = enqueue.getAnnotation(
                Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation())
                .isEqualTo(Propagation.MANDATORY);
        assertRequiresNew("claim", OaPurchaseApprovalStartOutbox.class);
        assertRequiresNew("recordRemoteSucceeded",
                OaPurchaseApprovalStartOutbox.class, Long.class,
                String.class, Integer.class, Integer.class);
        assertRequiresNew("finalizeRemoteSuccess",
                OaPurchaseApprovalStartOutbox.class, String.class);
        assertRequiresNew("markRetry",
                OaPurchaseApprovalStartOutbox.class, int.class,
                java.util.Date.class, Integer.class, String.class,
                String.class);
        assertRequiresNew("markFailed",
                OaPurchaseApprovalStartOutbox.class, Integer.class,
                String.class, String.class);
        assertRequiresNew("replayFailed",
                OaPurchaseApprovalStartOutboxQuery.class, Long.class,
                String.class);
        assertDataScoped("selectOps",
                OaPurchaseApprovalStartOutboxQuery.class);
        assertDataScoped("selectSummary",
                OaPurchaseApprovalStartOutboxQuery.class);
        assertDataScoped("replayFailed",
                OaPurchaseApprovalStartOutboxQuery.class, Long.class,
                String.class);
    }

    @Test
    @DisplayName("并发 claim 只有一个调度器获得远端调用权")
    void shouldAllowOnlyOneConcurrentClaim() throws Exception
    {
        OaPurchaseApprovalStartOutboxMapper mapper = mock(
                OaPurchaseApprovalStartOutboxMapper.class);
        AtomicBoolean winner = new AtomicBoolean();
        when(mapper.claimForSubmitting(10L,
                OaPurchaseApprovalStartOutboxService.PENDING, 0L))
                .thenAnswer(invocation -> winner.compareAndSet(false, true)
                        ? 1 : 0);
        OaPurchaseApprovalStartOutboxService service = service(mapper,
                mock(OaPurchaseMapper.class));
        OaPurchaseApprovalStartOutbox first = pendingRow();
        OaPurchaseApprovalStartOutbox second = pendingRow();
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
    @DisplayName("远端成功关联使用采购状态、轮次、快照版本 CAS")
    void shouldFinalizeRemoteSuccessWithBusinessCas()
    {
        OaPurchaseApprovalStartOutboxMapper mapper = mock(
                OaPurchaseApprovalStartOutboxMapper.class);
        OaPurchaseMapper purchaseMapper = mock(OaPurchaseMapper.class);
        OaPurchaseApprovalStartOutbox locked = remoteSucceededRow();
        when(mapper.selectByIdForUpdate(10L)).thenReturn(locked);
        when(purchaseMapper.selectOaPurchaseByIdForUpdate(88L))
                .thenReturn(submittingPurchase(5L));
        when(purchaseMapper.finalizeApprovalStart(88L, 1, 5L, 9001L,
                "worker")).thenReturn(1);
        when(mapper.markSucceeded(10L,
                OaPurchaseApprovalStartOutboxService.REMOTE_SUCCEEDED, 4L))
                .thenReturn(1);
        OaPurchaseApprovalStartOutbox candidate = remoteSucceededRow();

        service(mapper, purchaseMapper).finalizeRemoteSuccess(candidate,
                "worker");

        verify(purchaseMapper).finalizeApprovalStart(88L, 1, 5L, 9001L,
                "worker");
        verify(mapper).markSucceeded(10L,
                OaPurchaseApprovalStartOutboxService.REMOTE_SUCCEEDED, 4L);
        assertThat(candidate.getStatus())
                .isEqualTo(OaPurchaseApprovalStartOutboxService.SUCCEEDED);
    }

    @Test
    @DisplayName("采购快照版本已变化时禁止关联过期实例")
    void shouldRejectStalePurchaseVersion()
    {
        OaPurchaseApprovalStartOutboxMapper mapper = mock(
                OaPurchaseApprovalStartOutboxMapper.class);
        OaPurchaseMapper purchaseMapper = mock(OaPurchaseMapper.class);
        when(mapper.selectByIdForUpdate(10L))
                .thenReturn(remoteSucceededRow());
        when(purchaseMapper.selectOaPurchaseByIdForUpdate(88L))
                .thenReturn(submittingPurchase(6L));

        assertThatThrownBy(() -> service(mapper, purchaseMapper)
                .finalizeRemoteSuccess(remoteSucceededRow(), "worker"))
                .isInstanceOf(
                        OaPurchaseApprovalStartOutboxService.PermanentFailure.class)
                .hasMessageContaining("版本已变化");
        verify(purchaseMapper, never()).finalizeApprovalStart(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), anyString());
    }

    @Test
    @DisplayName("远端实例轮次不一致时在读取采购单前拒绝关联")
    void shouldRejectRemoteRoundMismatchBeforeBindingBusiness()
    {
        OaPurchaseApprovalStartOutboxMapper mapper = mock(
                OaPurchaseApprovalStartOutboxMapper.class);
        OaPurchaseMapper purchaseMapper = mock(OaPurchaseMapper.class);
        OaPurchaseApprovalStartOutbox mismatch = remoteSucceededRow();
        mismatch.setRemoteBusinessRound(2);
        when(mapper.selectByIdForUpdate(10L)).thenReturn(mismatch);

        assertThatThrownBy(() -> service(mapper, purchaseMapper)
                .finalizeRemoteSuccess(mismatch, "worker"))
                .isInstanceOf(
                        OaPurchaseApprovalStartOutboxService.PermanentFailure.class)
                .hasMessageContaining("业务轮次");
        verify(purchaseMapper, never()).selectOaPurchaseByIdForUpdate(88L);
        verify(purchaseMapper, never()).finalizeApprovalStart(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), anyString());
    }

    @Test
    @DisplayName("存量异常行的非正远端实例ID在读取采购单前被隔离")
    void shouldRejectLegacyNonPositiveInstanceBeforeBindingBusiness()
    {
        OaPurchaseApprovalStartOutboxMapper mapper = mock(
                OaPurchaseApprovalStartOutboxMapper.class);
        OaPurchaseMapper purchaseMapper = mock(OaPurchaseMapper.class);
        OaPurchaseApprovalStartOutbox invalid = remoteSucceededRow();
        invalid.setRemoteInstanceId(0L);
        when(mapper.selectByIdForUpdate(10L)).thenReturn(invalid);

        assertThatThrownBy(() -> service(mapper, purchaseMapper)
                .finalizeRemoteSuccess(invalid, "worker"))
                .isInstanceOf(
                        OaPurchaseApprovalStartOutboxService.PermanentFailure.class)
                .hasMessageContaining("实例关联缺失")
                .satisfies(error -> assertThat(
                        ((OaPurchaseApprovalStartOutboxService.PermanentFailure)
                                error).getErrorCode())
                        .isEqualTo("REMOTE_INSTANCE_MISSING"));
        verify(purchaseMapper, never()).selectOaPurchaseByIdForUpdate(88L);
    }

    @Test
    @DisplayName("已关联其他实例时失败可观测且不覆盖")
    void shouldRejectDifferentExistingInstance()
    {
        OaPurchaseApprovalStartOutboxMapper mapper = mock(
                OaPurchaseApprovalStartOutboxMapper.class);
        OaPurchaseMapper purchaseMapper = mock(OaPurchaseMapper.class);
        when(mapper.selectByIdForUpdate(10L))
                .thenReturn(remoteSucceededRow());
        OaPurchase purchase = submittingPurchase(5L);
        purchase.setStatus("pending");
        purchase.setApprovalInstanceId(9999L);
        when(purchaseMapper.selectOaPurchaseByIdForUpdate(88L))
                .thenReturn(purchase);

        assertThatThrownBy(() -> service(mapper, purchaseMapper)
                .finalizeRemoteSuccess(remoteSucceededRow(), "worker"))
                .isInstanceOf(
                        OaPurchaseApprovalStartOutboxService.PermanentFailure.class)
                .hasMessageContaining("其他审批实例");
        verify(purchaseMapper, never()).finalizeApprovalStart(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), anyString());
    }

    @Test
    @DisplayName("已关联同一实例的恢复派发是幂等的")
    void shouldCompleteWhenSameInstanceWasAlreadyAttached()
    {
        OaPurchaseApprovalStartOutboxMapper mapper = mock(
                OaPurchaseApprovalStartOutboxMapper.class);
        OaPurchaseMapper purchaseMapper = mock(OaPurchaseMapper.class);
        when(mapper.selectByIdForUpdate(10L))
                .thenReturn(remoteSucceededRow());
        OaPurchase purchase = submittingPurchase(6L);
        purchase.setStatus("pending");
        purchase.setApprovalInstanceId(9001L);
        when(purchaseMapper.selectOaPurchaseByIdForUpdate(88L))
                .thenReturn(purchase);
        when(mapper.markSucceeded(10L,
                OaPurchaseApprovalStartOutboxService.REMOTE_SUCCEEDED, 4L))
                .thenReturn(1);

        service(mapper, purchaseMapper).finalizeRemoteSuccess(
                remoteSucceededRow(), "worker");

        verify(purchaseMapper, never()).finalizeApprovalStart(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), anyString());
        verify(mapper).markSucceeded(10L,
                OaPurchaseApprovalStartOutboxService.REMOTE_SUCCEEDED, 4L);
    }

    @Test
    @DisplayName("重复入队仅在幂等键与请求快照完全相同时返回既有事实")
    void shouldReturnOnlyExactExistingOutboxForDuplicateEnqueue()
    {
        OaPurchaseApprovalStartOutboxMapper mapper = mock(
                OaPurchaseApprovalStartOutboxMapper.class);
        when(mapper.insertOutbox(
                org.mockito.ArgumentMatchers.any())).thenReturn(0);
        ApprovalStartRequest request = request();
        OaPurchaseApprovalStartOutbox existing = pendingRow();
        existing.setIdempotencyKey("OA_PURCHASE:88:1");
        existing.setRequestJson(JSON.toJSONString(request));
        when(mapper.selectByPurchaseRound(88L, 1)).thenReturn(existing);

        OaPurchaseApprovalStartOutbox result = service(mapper,
                mock(OaPurchaseMapper.class)).enqueue(
                        submittingPurchase(5L), request, "tester");

        assertThat(result).isSameAs(existing);
    }

    @Test
    @DisplayName("人工重放使用发件箱版本 CAS")
    void shouldReplayFailedWithVersionCas()
    {
        OaPurchaseApprovalStartOutboxMapper mapper = mock(
                OaPurchaseApprovalStartOutboxMapper.class);
        OaPurchaseApprovalStartOutboxQuery query = query(10L);
        OaPurchaseApprovalStartOutbox failed = pendingRow();
        failed.setStatus(OaPurchaseApprovalStartOutboxService.FAILED);
        failed.setVersion(8L);
        when(mapper.selectScopedByIdForUpdate(query)).thenReturn(failed);
        when(mapper.replayFailedScoped(query, 8L, "operator"))
                .thenReturn(0);

        assertThatThrownBy(() -> service(mapper,
                mock(OaPurchaseMapper.class)).replayFailed(query, 8L,
                        "operator"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("状态已变化");
    }

    @Test
    @DisplayName("组织范围外记录按不存在返回且不执行重放 CAS")
    void shouldHideOutOfScopeReplayTarget()
    {
        OaPurchaseApprovalStartOutboxMapper mapper = mock(
                OaPurchaseApprovalStartOutboxMapper.class);
        OaPurchaseApprovalStartOutboxQuery query = query(10L);

        assertThatThrownBy(() -> service(mapper,
                mock(OaPurchaseMapper.class)).replayFailed(query, 8L,
                        "operator"))
                .isInstanceOf(ServiceException.class)
                .satisfies(error -> assertThat(
                        ((ServiceException) error).getCode()).isEqualTo(404));
        verify(mapper, never()).replayFailedScoped(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "REMOTE_ROUND_MISMATCH", "INSTANCE_CONFLICT",
            "PURCHASE_NOT_FOUND", "PURCHASE_STATE_CHANGED",
            "PURCHASE_VERSION_CONFLICT" })
    @DisplayName("永久失败态禁止普通人工重放")
    void shouldBlockReplayForPermanentFailure(String errorCode)
    {
        OaPurchaseApprovalStartOutboxMapper mapper = mock(
                OaPurchaseApprovalStartOutboxMapper.class);
        OaPurchaseApprovalStartOutboxQuery query = query(10L);
        OaPurchaseApprovalStartOutbox failed = pendingRow();
        failed.setStatus(OaPurchaseApprovalStartOutboxService.FAILED);
        failed.setVersion(8L);
        failed.setLastErrorCode(errorCode);
        when(mapper.selectScopedByIdForUpdate(query)).thenReturn(failed);

        assertThatThrownBy(() -> service(mapper,
                mock(OaPurchaseMapper.class)).replayFailed(query, 8L,
                        "operator"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("禁止普通重放");
        verify(mapper, never()).replayFailedScoped(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private static OaPurchaseApprovalStartOutboxService service(
            OaPurchaseApprovalStartOutboxMapper mapper,
            OaPurchaseMapper purchaseMapper)
    {
        return new OaPurchaseApprovalStartOutboxService(mapper,
                purchaseMapper);
    }

    private static void assertRequiresNew(String methodName,
            Class<?>... parameterTypes) throws Exception
    {
        Transactional transactional =
                OaPurchaseApprovalStartOutboxService.class
                        .getMethod(methodName, parameterTypes)
                        .getAnnotation(Transactional.class);
        assertThat(transactional).as(methodName).isNotNull();
        assertThat(transactional.propagation()).as(methodName)
                .isEqualTo(Propagation.REQUIRES_NEW);
    }

    private static void assertDataScoped(String methodName,
            Class<?>... parameterTypes) throws Exception
    {
        DataScope annotation = OaPurchaseApprovalStartOutboxService.class
                .getMethod(methodName, parameterTypes)
                .getAnnotation(DataScope.class);
        assertThat(annotation).as(methodName).isNotNull();
        assertThat(annotation.deptAlias()).isEqualTo("d");
    }

    private static OaPurchaseApprovalStartOutboxQuery query(Long outboxId)
    {
        OaPurchaseApprovalStartOutboxQuery query =
                new OaPurchaseApprovalStartOutboxQuery();
        query.setOutboxId(outboxId);
        return query;
    }

    private static OaPurchaseApprovalStartOutbox pendingRow()
    {
        OaPurchaseApprovalStartOutbox row =
                new OaPurchaseApprovalStartOutbox();
        row.setOutboxId(10L);
        row.setPurchaseId(88L);
        row.setBusinessRound(1);
        row.setStatus(OaPurchaseApprovalStartOutboxService.PENDING);
        row.setVersion(0L);
        row.setRetryCount(0);
        row.setPurchaseVersion(5L);
        return row;
    }

    private static OaPurchaseApprovalStartOutbox remoteSucceededRow()
    {
        OaPurchaseApprovalStartOutbox row = pendingRow();
        row.setStatus(
                OaPurchaseApprovalStartOutboxService.REMOTE_SUCCEEDED);
        row.setVersion(4L);
        row.setRemoteInstanceId(9001L);
        row.setRemoteStatus("RUNNING");
        row.setRemoteBusinessRound(1);
        return row;
    }

    private static OaPurchase submittingPurchase(Long version)
    {
        OaPurchase purchase = new OaPurchase();
        purchase.setPurchaseId(88L);
        purchase.setStatus("submitting");
        purchase.setApprovalRound(1);
        purchase.setApprovalInstanceId(null);
        purchase.setRowVersion(version);
        return purchase;
    }

    private static ApprovalStartRequest request()
    {
        ApprovalStartRequest request = new ApprovalStartRequest();
        request.setBusinessCode(
                OaPurchaseApprovalStartOutboxService.BUSINESS_CODE);
        request.setBusinessId("88");
        request.setBusinessRound(1);
        request.setApplicantId(7L);
        request.setIdempotencyKey("OA_PURCHASE:88:1");
        return request;
    }
}
