package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import com.erp.system.domain.HrHealthCertificateApprovalStartOutbox;
import com.erp.system.domain.vo.HrHealthCertificateApprovalStartOutboxQuery;
import com.erp.system.domain.vo.HrHealthCertificateVo;
import com.erp.system.mapper.HrHealthCertificateApprovalStartOutboxMapper;
import com.erp.system.mapper.HrHealthCertificateMapper;

@DisplayName("健康证统一审批发起发件箱服务")
class HrHealthCertificateApprovalStartOutboxServiceTest
{
    @Test
    @DisplayName("业务事实入队加入调用方事务，其余状态转换使用独立短事务")
    void shouldDeclareRequiredTransactionBoundaries() throws Exception
    {
        Method enqueue = HrHealthCertificateApprovalStartOutboxService.class
                .getMethod("enqueue", HrHealthCertificateVo.class,
                        ApprovalStartRequest.class, String.class);
        Transactional transactional = enqueue.getAnnotation(
                Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation())
                .isEqualTo(Propagation.MANDATORY);

        assertRequiresNew("claim",
                HrHealthCertificateApprovalStartOutbox.class);
        assertRequiresNew("recordRemoteSucceeded",
                HrHealthCertificateApprovalStartOutbox.class, Long.class,
                String.class, Integer.class, Integer.class);
        assertRequiresNew("finalizeRemoteSuccess",
                HrHealthCertificateApprovalStartOutbox.class, String.class);
        assertRequiresNew("markRetry",
                HrHealthCertificateApprovalStartOutbox.class, int.class,
                java.util.Date.class, Integer.class, String.class,
                String.class);
        assertRequiresNew("markFailed",
                HrHealthCertificateApprovalStartOutbox.class, Integer.class,
                String.class, String.class);
        assertRequiresNew("replayFailed",
                HrHealthCertificateApprovalStartOutboxQuery.class,
                Long.class, String.class);
    }

    @Test
    @DisplayName("并发调度只有一个 claim 获得远端调用权")
    void shouldAllowOnlyOneConcurrentClaim() throws Exception
    {
        HrHealthCertificateApprovalStartOutboxMapper mapper = mock(
                HrHealthCertificateApprovalStartOutboxMapper.class);
        AtomicBoolean winner = new AtomicBoolean();
        when(mapper.claimForSubmitting(10L,
                HrHealthCertificateApprovalStartOutboxService.PENDING, 0L))
                .thenAnswer(invocation -> winner.compareAndSet(false, true)
                        ? 1 : 0);
        HrHealthCertificateApprovalStartOutboxService service = service(
                mapper, mock(HrHealthCertificateMapper.class));
        HrHealthCertificateApprovalStartOutbox first = pendingRow();
        HrHealthCertificateApprovalStartOutbox second = pendingRow();
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
    @DisplayName("远端成功后使用健康证待发起轮次版本做 CAS 关联")
    void shouldFinalizeRemoteSuccessWithCertificateCas()
    {
        HrHealthCertificateApprovalStartOutboxMapper outboxMapper = mock(
                HrHealthCertificateApprovalStartOutboxMapper.class);
        HrHealthCertificateMapper certificateMapper = mock(
                HrHealthCertificateMapper.class);
        HrHealthCertificateApprovalStartOutbox locked = remoteSucceededRow();
        HrHealthCertificateVo certificate = submittingCertificate();
        when(outboxMapper.selectByIdForUpdate(10L)).thenReturn(locked);
        when(certificateMapper.selectByIdForUpdate(88L))
                .thenReturn(certificate);
        when(certificateMapper.markApprovalPending(88L, 1L, 9001L, 1,
                "worker")).thenReturn(1);
        when(outboxMapper.markSucceeded(10L,
                HrHealthCertificateApprovalStartOutboxService
                        .REMOTE_SUCCEEDED,
                4L)).thenReturn(1);
        HrHealthCertificateApprovalStartOutbox candidate =
                remoteSucceededRow();

        service(outboxMapper, certificateMapper).finalizeRemoteSuccess(
                candidate, "worker");

        verify(certificateMapper).markApprovalPending(88L, 1L, 9001L, 1,
                "worker");
        verify(outboxMapper).markSucceeded(10L,
                HrHealthCertificateApprovalStartOutboxService
                        .REMOTE_SUCCEEDED,
                4L);
        assertThat(candidate.getStatus()).isEqualTo(
                HrHealthCertificateApprovalStartOutboxService.SUCCEEDED);
    }

    @Test
    @DisplayName("健康证版本陈旧时拒绝关联且保留可观测失败原因")
    void shouldRejectStaleCertificateVersion()
    {
        HrHealthCertificateApprovalStartOutboxMapper outboxMapper = mock(
                HrHealthCertificateApprovalStartOutboxMapper.class);
        HrHealthCertificateMapper certificateMapper = mock(
                HrHealthCertificateMapper.class);
        when(outboxMapper.selectByIdForUpdate(10L))
                .thenReturn(remoteSucceededRow());
        HrHealthCertificateVo stale = submittingCertificate();
        stale.setVersion(2L);
        when(certificateMapper.selectByIdForUpdate(88L)).thenReturn(stale);

        assertThatThrownBy(() -> service(outboxMapper, certificateMapper)
                .finalizeRemoteSuccess(remoteSucceededRow(), "worker"))
                .isInstanceOf(
                        HrHealthCertificateApprovalStartOutboxService
                                .PermanentFailure.class)
                .hasMessageContaining("快照版本");
        verify(certificateMapper, never()).markApprovalPending(any(), any(),
                any(), any(), any());
        verify(outboxMapper, never()).markSucceeded(any(), any(), any());
    }

    @Test
    @DisplayName("健康证已关联其他实例时拒绝覆盖")
    void shouldRejectConflictingInstance()
    {
        HrHealthCertificateApprovalStartOutboxMapper outboxMapper = mock(
                HrHealthCertificateApprovalStartOutboxMapper.class);
        HrHealthCertificateMapper certificateMapper = mock(
                HrHealthCertificateMapper.class);
        when(outboxMapper.selectByIdForUpdate(10L))
                .thenReturn(remoteSucceededRow());
        HrHealthCertificateVo pending = submittingCertificate();
        pending.setReviewStatus("APPROVAL_PENDING");
        pending.setApprovalInstanceId(9999L);
        pending.setVersion(2L);
        when(certificateMapper.selectByIdForUpdate(88L)).thenReturn(pending);

        assertThatThrownBy(() -> service(outboxMapper, certificateMapper)
                .finalizeRemoteSuccess(remoteSucceededRow(), "worker"))
                .isInstanceOf(
                        HrHealthCertificateApprovalStartOutboxService
                                .PermanentFailure.class)
                .hasMessageContaining("其他审批实例");
        verify(certificateMapper, never()).markApprovalPending(any(), any(),
                any(), any(), any());
    }

    @Test
    @DisplayName("审批中心实际返回轮次不一致时绝不关联该实例")
    void shouldRejectRemoteRoundMismatchEvenAfterManualReplay()
    {
        HrHealthCertificateApprovalStartOutboxMapper outboxMapper = mock(
                HrHealthCertificateApprovalStartOutboxMapper.class);
        HrHealthCertificateMapper certificateMapper = mock(
                HrHealthCertificateMapper.class);
        HrHealthCertificateApprovalStartOutbox mismatch =
                remoteSucceededRow();
        mismatch.setRemoteBusinessRound(2);
        when(outboxMapper.selectByIdForUpdate(10L)).thenReturn(mismatch);

        assertThatThrownBy(() -> service(outboxMapper, certificateMapper)
                .finalizeRemoteSuccess(mismatch, "worker"))
                .isInstanceOf(
                        HrHealthCertificateApprovalStartOutboxService
                                .PermanentFailure.class)
                .hasMessageContaining("业务轮次");
        verify(certificateMapper, never()).selectByIdForUpdate(any());
        verify(certificateMapper, never()).markApprovalPending(any(), any(),
                any(), any(), any());
    }

    @Test
    @DisplayName("重复入队只接受完全一致的同轮请求快照")
    void shouldReturnSameOutboxForExactDuplicateEnqueue()
    {
        HrHealthCertificateApprovalStartOutboxMapper outboxMapper = mock(
                HrHealthCertificateApprovalStartOutboxMapper.class);
        when(outboxMapper.insertOutbox(any())).thenReturn(0);
        HrHealthCertificateApprovalStartOutbox existing = pendingRow();
        ApprovalStartRequest request = request();
        existing.setIdempotencyKey(request.getIdempotencyKey());
        existing.setCertificateVersion(1L);
        existing.setRequestJson(com.alibaba.fastjson2.JSON
                .toJSONString(request));
        when(outboxMapper.selectByCertificateRound(88L, 1))
                .thenReturn(existing);

        HrHealthCertificateApprovalStartOutbox result = service(outboxMapper,
                mock(HrHealthCertificateMapper.class)).enqueue(
                        submittingCertificate(), request, "tester");

        assertThat(result).isSameAs(existing);
    }

    @Test
    @DisplayName("人工重放同时使用数据范围与版本 CAS")
    void shouldReplayFailedWithScopedVersionCas()
    {
        HrHealthCertificateApprovalStartOutboxMapper mapper = mock(
                HrHealthCertificateApprovalStartOutboxMapper.class);
        HrHealthCertificateApprovalStartOutboxQuery query =
                new HrHealthCertificateApprovalStartOutboxQuery();
        query.setOutboxId(10L);
        HrHealthCertificateApprovalStartOutbox failed = pendingRow();
        failed.setStatus(HrHealthCertificateApprovalStartOutboxService.FAILED);
        failed.setVersion(8L);
        when(mapper.selectScopedByIdForUpdate(query)).thenReturn(failed);
        when(mapper.replayFailedScoped(query, 8L, "operator"))
                .thenReturn(0);

        assertThatThrownBy(() -> service(mapper,
                mock(HrHealthCertificateMapper.class)).replayFailed(query,
                        8L, "operator"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("状态已变化");
    }

    @Test
    @DisplayName("组织范围外健康证发件箱按不存在返回")
    void shouldHideOutOfScopeReplayTarget()
    {
        HrHealthCertificateApprovalStartOutboxMapper mapper = mock(
                HrHealthCertificateApprovalStartOutboxMapper.class);
        HrHealthCertificateApprovalStartOutboxQuery query =
                new HrHealthCertificateApprovalStartOutboxQuery();
        query.setOutboxId(10L);

        assertThatThrownBy(() -> service(mapper,
                mock(HrHealthCertificateMapper.class)).replayFailed(query,
                        8L, "operator"))
                .isInstanceOf(ServiceException.class)
                .satisfies(error -> assertThat(
                        ((ServiceException) error).getCode()).isEqualTo(404));
        verify(mapper, never()).replayFailedScoped(any(), any(), any());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "REMOTE_ROUND_MISMATCH", "INSTANCE_CONFLICT",
            "CERTIFICATE_NOT_FOUND", "CERTIFICATE_STATE_CHANGED",
            "CERTIFICATE_VERSION_CONFLICT", "CERTIFICATE_ROUND_CHANGED" })
    @DisplayName("永久失败态禁止普通人工重放")
    void shouldBlockReplayForPermanentFailure(String errorCode)
    {
        HrHealthCertificateApprovalStartOutboxMapper mapper = mock(
                HrHealthCertificateApprovalStartOutboxMapper.class);
        HrHealthCertificateApprovalStartOutboxQuery query =
                new HrHealthCertificateApprovalStartOutboxQuery();
        query.setOutboxId(10L);
        HrHealthCertificateApprovalStartOutbox failed = pendingRow();
        failed.setStatus(HrHealthCertificateApprovalStartOutboxService.FAILED);
        failed.setVersion(8L);
        failed.setLastErrorCode(errorCode);
        when(mapper.selectScopedByIdForUpdate(query)).thenReturn(failed);

        assertThatThrownBy(() -> service(mapper,
                mock(HrHealthCertificateMapper.class)).replayFailed(query,
                        8L, "operator"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("禁止普通重放");
        verify(mapper, never()).replayFailedScoped(any(), any(), any());
    }

    private static HrHealthCertificateApprovalStartOutboxService service(
            HrHealthCertificateApprovalStartOutboxMapper outboxMapper,
            HrHealthCertificateMapper certificateMapper)
    {
        return new HrHealthCertificateApprovalStartOutboxService(
                outboxMapper, certificateMapper);
    }

    private static void assertRequiresNew(String methodName,
            Class<?>... parameterTypes) throws Exception
    {
        Transactional transactional =
                HrHealthCertificateApprovalStartOutboxService.class
                        .getMethod(methodName, parameterTypes)
                        .getAnnotation(Transactional.class);
        assertThat(transactional).as(methodName).isNotNull();
        assertThat(transactional.propagation()).as(methodName)
                .isEqualTo(Propagation.REQUIRES_NEW);
    }

    private static HrHealthCertificateApprovalStartOutbox pendingRow()
    {
        HrHealthCertificateApprovalStartOutbox row =
                new HrHealthCertificateApprovalStartOutbox();
        row.setOutboxId(10L);
        row.setCertificateId(88L);
        row.setBusinessRound(1);
        row.setStatus(
                HrHealthCertificateApprovalStartOutboxService.PENDING);
        row.setRetryCount(0);
        row.setVersion(0L);
        return row;
    }

    private static HrHealthCertificateApprovalStartOutbox remoteSucceededRow()
    {
        HrHealthCertificateApprovalStartOutbox row = pendingRow();
        row.setStatus(HrHealthCertificateApprovalStartOutboxService
                .REMOTE_SUCCEEDED);
        row.setVersion(4L);
        row.setRemoteInstanceId(9001L);
        row.setRemoteStatus("RUNNING");
        row.setRemoteBusinessRound(1);
        row.setCertificateVersion(1L);
        return row;
    }

    private static HrHealthCertificateVo submittingCertificate()
    {
        HrHealthCertificateVo certificate = new HrHealthCertificateVo();
        certificate.setCertificateId(88L);
        certificate.setUserId(7L);
        certificate.setReviewStatus("APPROVAL_SUBMITTING");
        certificate.setApprovalRound(1);
        certificate.setVersion(1L);
        return certificate;
    }

    private static ApprovalStartRequest request()
    {
        ApprovalStartRequest request = new ApprovalStartRequest();
        request.setBusinessCode(
                HrHealthCertificateApprovalStartOutboxService.BUSINESS_CODE);
        request.setBusinessId("88");
        request.setBusinessRound(1);
        request.setApplicantId(7L);
        request.setIdempotencyKey("HR_HEALTH_CERTIFICATE:88:1");
        return request;
    }
}
