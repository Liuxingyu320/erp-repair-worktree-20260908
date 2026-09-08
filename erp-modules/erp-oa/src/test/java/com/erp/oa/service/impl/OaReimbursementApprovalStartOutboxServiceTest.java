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
import com.erp.oa.domain.OaReimbursement;
import com.erp.oa.domain.OaReimbursementApprovalStartOutbox;
import com.erp.oa.domain.vo.OaReimbursementApprovalStartOutboxQuery;
import com.erp.oa.mapper.OaReimbursementApprovalStartOutboxMapper;
import com.erp.oa.mapper.OaReimbursementMapper;

@DisplayName("OA报销统一审批发起发件箱服务")
class OaReimbursementApprovalStartOutboxServiceTest
{
    @Test
    @DisplayName("业务事实入队必须加入调用方事务，其余状态转换使用新事务")
    void shouldDeclareRequiredTransactionBoundaries() throws Exception
    {
        Method enqueue = OaReimbursementApprovalStartOutboxService.class
                .getMethod("enqueue", OaReimbursement.class,
                        ApprovalStartRequest.class, String.class);
        Transactional transactional = enqueue.getAnnotation(
                Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation())
                .isEqualTo(Propagation.MANDATORY);
        assertRequiresNew("claim", OaReimbursementApprovalStartOutbox.class);
        assertRequiresNew("recordRemoteSucceeded",
                OaReimbursementApprovalStartOutbox.class, Long.class,
                String.class, Integer.class, Integer.class);
        assertRequiresNew("finalizeRemoteSuccess",
                OaReimbursementApprovalStartOutbox.class, String.class);
        assertRequiresNew("markRetry",
                OaReimbursementApprovalStartOutbox.class, int.class,
                java.util.Date.class, Integer.class, String.class,
                String.class);
        assertRequiresNew("markFailed",
                OaReimbursementApprovalStartOutbox.class, Integer.class,
                String.class, String.class);
        assertRequiresNew("replayFailed",
                OaReimbursementApprovalStartOutboxQuery.class, Long.class,
                String.class);
        assertDataScoped("selectOps",
                OaReimbursementApprovalStartOutboxQuery.class);
        assertDataScoped("selectSummary",
                OaReimbursementApprovalStartOutboxQuery.class);
        assertDataScoped("replayFailed",
                OaReimbursementApprovalStartOutboxQuery.class, Long.class,
                String.class);
    }

    @Test
    @DisplayName("并发 claim 只有一个调度器获得远端调用权")
    void shouldAllowOnlyOneConcurrentClaim() throws Exception
    {
        OaReimbursementApprovalStartOutboxMapper mapper = mock(
                OaReimbursementApprovalStartOutboxMapper.class);
        AtomicBoolean winner = new AtomicBoolean();
        when(mapper.claimForSubmitting(10L,
                OaReimbursementApprovalStartOutboxService.PENDING, 0L))
                .thenAnswer(invocation -> winner.compareAndSet(false, true)
                        ? 1 : 0);
        OaReimbursementApprovalStartOutboxService service = service(mapper,
                mock(OaReimbursementMapper.class));
        OaReimbursementApprovalStartOutbox first = pendingRow();
        OaReimbursementApprovalStartOutbox second = pendingRow();
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
    @DisplayName("远端成功关联使用报销状态、轮次、快照版本 CAS")
    void shouldFinalizeRemoteSuccessWithBusinessCas()
    {
        OaReimbursementApprovalStartOutboxMapper mapper = mock(
                OaReimbursementApprovalStartOutboxMapper.class);
        OaReimbursementMapper reimbursementMapper = mock(OaReimbursementMapper.class);
        OaReimbursementApprovalStartOutbox locked = remoteSucceededRow();
        when(mapper.selectByIdForUpdate(10L)).thenReturn(locked);
        when(reimbursementMapper.selectByIdForUpdate(88L))
                .thenReturn(submittingReimbursement(5L));
        when(reimbursementMapper.finalizeApprovalStart(88L, 1, 5L, 9001L,
                "worker")).thenReturn(1);
        when(mapper.markSucceeded(10L,
                OaReimbursementApprovalStartOutboxService.REMOTE_SUCCEEDED, 4L))
                .thenReturn(1);
        OaReimbursementApprovalStartOutbox candidate = remoteSucceededRow();

        service(mapper, reimbursementMapper).finalizeRemoteSuccess(candidate,
                "worker");

        verify(reimbursementMapper).finalizeApprovalStart(88L, 1, 5L, 9001L,
                "worker");
        verify(mapper).markSucceeded(10L,
                OaReimbursementApprovalStartOutboxService.REMOTE_SUCCEEDED, 4L);
        assertThat(candidate.getStatus())
                .isEqualTo(OaReimbursementApprovalStartOutboxService.SUCCEEDED);
    }

    @Test
    @DisplayName("报销快照版本已变化时禁止关联过期实例")
    void shouldRejectStaleReimbursementVersion()
    {
        OaReimbursementApprovalStartOutboxMapper mapper = mock(
                OaReimbursementApprovalStartOutboxMapper.class);
        OaReimbursementMapper reimbursementMapper = mock(OaReimbursementMapper.class);
        when(mapper.selectByIdForUpdate(10L))
                .thenReturn(remoteSucceededRow());
        when(reimbursementMapper.selectByIdForUpdate(88L))
                .thenReturn(submittingReimbursement(6L));

        assertThatThrownBy(() -> service(mapper, reimbursementMapper)
                .finalizeRemoteSuccess(remoteSucceededRow(), "worker"))
                .isInstanceOf(
                        OaReimbursementApprovalStartOutboxService.PermanentFailure.class)
                .hasMessageContaining("版本已变化");
        verify(reimbursementMapper, never()).finalizeApprovalStart(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), anyString());
    }

    @Test
    @DisplayName("远端实例轮次不一致时在读取报销单前拒绝关联")
    void shouldRejectRemoteRoundMismatchBeforeBindingBusiness()
    {
        OaReimbursementApprovalStartOutboxMapper mapper = mock(
                OaReimbursementApprovalStartOutboxMapper.class);
        OaReimbursementMapper reimbursementMapper = mock(OaReimbursementMapper.class);
        OaReimbursementApprovalStartOutbox mismatch = remoteSucceededRow();
        mismatch.setRemoteBusinessRound(2);
        when(mapper.selectByIdForUpdate(10L)).thenReturn(mismatch);

        assertThatThrownBy(() -> service(mapper, reimbursementMapper)
                .finalizeRemoteSuccess(mismatch, "worker"))
                .isInstanceOf(
                        OaReimbursementApprovalStartOutboxService.PermanentFailure.class)
                .hasMessageContaining("业务轮次");
        verify(reimbursementMapper, never()).selectByIdForUpdate(88L);
        verify(reimbursementMapper, never()).finalizeApprovalStart(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), anyString());
    }

    @Test
    @DisplayName("存量异常行的非正远端实例ID在读取报销单前被隔离")
    void shouldRejectLegacyNonPositiveInstanceBeforeBindingBusiness()
    {
        OaReimbursementApprovalStartOutboxMapper mapper = mock(
                OaReimbursementApprovalStartOutboxMapper.class);
        OaReimbursementMapper reimbursementMapper = mock(OaReimbursementMapper.class);
        OaReimbursementApprovalStartOutbox invalid = remoteSucceededRow();
        invalid.setRemoteInstanceId(0L);
        when(mapper.selectByIdForUpdate(10L)).thenReturn(invalid);

        assertThatThrownBy(() -> service(mapper, reimbursementMapper)
                .finalizeRemoteSuccess(invalid, "worker"))
                .isInstanceOf(
                        OaReimbursementApprovalStartOutboxService.PermanentFailure.class)
                .hasMessageContaining("实例关联缺失")
                .satisfies(error -> assertThat(
                        ((OaReimbursementApprovalStartOutboxService.PermanentFailure)
                                error).getErrorCode())
                        .isEqualTo("REMOTE_INSTANCE_MISSING"));
        verify(reimbursementMapper, never()).selectByIdForUpdate(88L);
    }

    @Test
    @DisplayName("已关联其他实例时失败可观测且不覆盖")
    void shouldRejectDifferentExistingInstance()
    {
        OaReimbursementApprovalStartOutboxMapper mapper = mock(
                OaReimbursementApprovalStartOutboxMapper.class);
        OaReimbursementMapper reimbursementMapper = mock(OaReimbursementMapper.class);
        when(mapper.selectByIdForUpdate(10L))
                .thenReturn(remoteSucceededRow());
        OaReimbursement reimbursement = submittingReimbursement(5L);
        reimbursement.setStatus("pending");
        reimbursement.setApprovalInstanceId(9999L);
        when(reimbursementMapper.selectByIdForUpdate(88L))
                .thenReturn(reimbursement);

        assertThatThrownBy(() -> service(mapper, reimbursementMapper)
                .finalizeRemoteSuccess(remoteSucceededRow(), "worker"))
                .isInstanceOf(
                        OaReimbursementApprovalStartOutboxService.PermanentFailure.class)
                .hasMessageContaining("其他审批实例");
        verify(reimbursementMapper, never()).finalizeApprovalStart(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), anyString());
    }

    @Test
    @DisplayName("已关联同一实例的恢复派发是幂等的")
    void shouldCompleteWhenSameInstanceWasAlreadyAttached()
    {
        OaReimbursementApprovalStartOutboxMapper mapper = mock(
                OaReimbursementApprovalStartOutboxMapper.class);
        OaReimbursementMapper reimbursementMapper = mock(OaReimbursementMapper.class);
        when(mapper.selectByIdForUpdate(10L))
                .thenReturn(remoteSucceededRow());
        OaReimbursement reimbursement = submittingReimbursement(6L);
        reimbursement.setStatus("pending");
        reimbursement.setApprovalInstanceId(9001L);
        when(reimbursementMapper.selectByIdForUpdate(88L))
                .thenReturn(reimbursement);
        when(mapper.markSucceeded(10L,
                OaReimbursementApprovalStartOutboxService.REMOTE_SUCCEEDED, 4L))
                .thenReturn(1);

        service(mapper, reimbursementMapper).finalizeRemoteSuccess(
                remoteSucceededRow(), "worker");

        verify(reimbursementMapper, never()).finalizeApprovalStart(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), anyString());
        verify(mapper).markSucceeded(10L,
                OaReimbursementApprovalStartOutboxService.REMOTE_SUCCEEDED, 4L);
    }

    @Test
    @DisplayName("重复入队仅在幂等键与请求快照完全相同时返回既有事实")
    void shouldReturnOnlyExactExistingOutboxForDuplicateEnqueue()
    {
        OaReimbursementApprovalStartOutboxMapper mapper = mock(
                OaReimbursementApprovalStartOutboxMapper.class);
        when(mapper.insertOutbox(
                org.mockito.ArgumentMatchers.any())).thenReturn(0);
        ApprovalStartRequest request = request();
        OaReimbursementApprovalStartOutbox existing = pendingRow();
        existing.setIdempotencyKey("OA_REIMBURSEMENT:88:1");
        existing.setRequestJson(JSON.toJSONString(request));
        when(mapper.selectByReimbursementRound(88L, 1)).thenReturn(existing);

        OaReimbursementApprovalStartOutbox result = service(mapper,
                mock(OaReimbursementMapper.class)).enqueue(
                        submittingReimbursement(5L), request, "tester");

        assertThat(result).isSameAs(existing);
    }

    @Test
    @DisplayName("人工重放使用发件箱版本 CAS")
    void shouldReplayFailedWithVersionCas()
    {
        OaReimbursementApprovalStartOutboxMapper mapper = mock(
                OaReimbursementApprovalStartOutboxMapper.class);
        OaReimbursementApprovalStartOutboxQuery query = query(10L);
        OaReimbursementApprovalStartOutbox failed = pendingRow();
        failed.setStatus(OaReimbursementApprovalStartOutboxService.FAILED);
        failed.setVersion(8L);
        when(mapper.selectScopedByIdForUpdate(query)).thenReturn(failed);
        when(mapper.replayFailedScoped(query, 8L, "operator"))
                .thenReturn(0);

        assertThatThrownBy(() -> service(mapper,
                mock(OaReimbursementMapper.class)).replayFailed(query, 8L,
                        "operator"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("状态已变化");
    }

    @Test
    @DisplayName("组织范围外记录按不存在返回且不执行重放 CAS")
    void shouldHideOutOfScopeReplayTarget()
    {
        OaReimbursementApprovalStartOutboxMapper mapper = mock(
                OaReimbursementApprovalStartOutboxMapper.class);
        OaReimbursementApprovalStartOutboxQuery query = query(10L);

        assertThatThrownBy(() -> service(mapper,
                mock(OaReimbursementMapper.class)).replayFailed(query, 8L,
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
            "REIMBURSEMENT_NOT_FOUND", "REIMBURSEMENT_STATE_CHANGED",
            "REIMBURSEMENT_VERSION_CONFLICT" })
    @DisplayName("永久失败态禁止普通人工重放")
    void shouldBlockReplayForPermanentFailure(String errorCode)
    {
        OaReimbursementApprovalStartOutboxMapper mapper = mock(
                OaReimbursementApprovalStartOutboxMapper.class);
        OaReimbursementApprovalStartOutboxQuery query = query(10L);
        OaReimbursementApprovalStartOutbox failed = pendingRow();
        failed.setStatus(OaReimbursementApprovalStartOutboxService.FAILED);
        failed.setVersion(8L);
        failed.setLastErrorCode(errorCode);
        when(mapper.selectScopedByIdForUpdate(query)).thenReturn(failed);

        assertThatThrownBy(() -> service(mapper,
                mock(OaReimbursementMapper.class)).replayFailed(query, 8L,
                        "operator"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("禁止普通重放");
        verify(mapper, never()).replayFailedScoped(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private static OaReimbursementApprovalStartOutboxService service(
            OaReimbursementApprovalStartOutboxMapper mapper,
            OaReimbursementMapper reimbursementMapper)
    {
        return new OaReimbursementApprovalStartOutboxService(mapper,
                reimbursementMapper);
    }

    private static void assertRequiresNew(String methodName,
            Class<?>... parameterTypes) throws Exception
    {
        Transactional transactional =
                OaReimbursementApprovalStartOutboxService.class
                        .getMethod(methodName, parameterTypes)
                        .getAnnotation(Transactional.class);
        assertThat(transactional).as(methodName).isNotNull();
        assertThat(transactional.propagation()).as(methodName)
                .isEqualTo(Propagation.REQUIRES_NEW);
    }

    private static void assertDataScoped(String methodName,
            Class<?>... parameterTypes) throws Exception
    {
        DataScope annotation = OaReimbursementApprovalStartOutboxService.class
                .getMethod(methodName, parameterTypes)
                .getAnnotation(DataScope.class);
        assertThat(annotation).as(methodName).isNotNull();
        assertThat(annotation.deptAlias()).isEqualTo("d");
    }

    private static OaReimbursementApprovalStartOutboxQuery query(Long outboxId)
    {
        OaReimbursementApprovalStartOutboxQuery query =
                new OaReimbursementApprovalStartOutboxQuery();
        query.setOutboxId(outboxId);
        return query;
    }

    private static OaReimbursementApprovalStartOutbox pendingRow()
    {
        OaReimbursementApprovalStartOutbox row =
                new OaReimbursementApprovalStartOutbox();
        row.setOutboxId(10L);
        row.setReimbursementId(88L);
        row.setBusinessRound(1);
        row.setStatus(OaReimbursementApprovalStartOutboxService.PENDING);
        row.setVersion(0L);
        row.setRetryCount(0);
        row.setReimbursementVersion(5L);
        return row;
    }

    private static OaReimbursementApprovalStartOutbox remoteSucceededRow()
    {
        OaReimbursementApprovalStartOutbox row = pendingRow();
        row.setStatus(
                OaReimbursementApprovalStartOutboxService.REMOTE_SUCCEEDED);
        row.setVersion(4L);
        row.setRemoteInstanceId(9001L);
        row.setRemoteStatus("RUNNING");
        row.setRemoteBusinessRound(1);
        return row;
    }

    private static OaReimbursement submittingReimbursement(Long version)
    {
        OaReimbursement reimbursement = new OaReimbursement();
        reimbursement.setReimbursementId(88L);
        reimbursement.setStatus("submitting");
        reimbursement.setApprovalRound(1);
        reimbursement.setApprovalInstanceId(null);
        reimbursement.setRowVersion(version);
        return reimbursement;
    }

    private static ApprovalStartRequest request()
    {
        ApprovalStartRequest request = new ApprovalStartRequest();
        request.setBusinessCode(
                OaReimbursementApprovalStartOutboxService.BUSINESS_CODE);
        request.setBusinessId("88");
        request.setBusinessRound(1);
        request.setApplicantId(7L);
        request.setIdempotencyKey("OA_REIMBURSEMENT:88:1");
        return request;
    }
}
