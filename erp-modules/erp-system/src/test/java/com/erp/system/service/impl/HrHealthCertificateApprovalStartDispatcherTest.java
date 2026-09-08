package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.alibaba.fastjson2.JSON;
import com.erp.approval.api.RemoteApprovalService;
import com.erp.approval.api.domain.ApprovalStartRequest;
import com.erp.approval.api.domain.ApprovalStartResponse;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.domain.R;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.domain.HrHealthCertificateApprovalStartOutbox;

@DisplayName("健康证统一审批发起派发器")
class HrHealthCertificateApprovalStartDispatcherTest
{
    private static final Instant NOW = Instant.parse("2026-07-16T02:00:00Z");
    private HrHealthCertificateApprovalStartOutboxService outboxService;
    private RemoteApprovalService remoteService;
    private HrHealthCertificateApprovalStartDispatcher dispatcher;
    private HrHealthCertificateApprovalStartOutbox row;

    @BeforeEach
    void setUp()
    {
        outboxService = mock(
                HrHealthCertificateApprovalStartOutboxService.class);
        remoteService = mock(RemoteApprovalService.class);
        dispatcher = new HrHealthCertificateApprovalStartDispatcher(
                outboxService, remoteService,
                Clock.fixed(NOW, ZoneOffset.UTC));
        row = row(validRequest());
        when(outboxService.selectById(10L)).thenReturn(row);
        when(outboxService.claim(any())).thenAnswer(invocation ->
        {
            HrHealthCertificateApprovalStartOutbox claimed =
                    invocation.getArgument(0);
            claimed.setStatus(
                    HrHealthCertificateApprovalStartOutboxService.SUBMITTING);
            claimed.setVersion(claimed.getVersion() + 1);
            return true;
        });
        doAnswer(invocation ->
        {
            HrHealthCertificateApprovalStartOutbox succeeded =
                    invocation.getArgument(0);
            succeeded.setStatus(
                    HrHealthCertificateApprovalStartOutboxService
                            .REMOTE_SUCCEEDED);
            succeeded.setRemoteInstanceId(invocation.getArgument(1));
            succeeded.setRemoteStatus(invocation.getArgument(2));
            succeeded.setVersion(succeeded.getVersion() + 1);
            return null;
        }).when(outboxService).recordRemoteSucceeded(any(), any(),
                anyString(), anyInt(), anyInt());
        doAnswer(invocation ->
        {
            HrHealthCertificateApprovalStartOutbox retry =
                    invocation.getArgument(0);
            retry.setStatus(
                    HrHealthCertificateApprovalStartOutboxService.RETRY);
            retry.setRetryCount(invocation.getArgument(1));
            retry.setNextRetryTime(invocation.getArgument(2));
            retry.setVersion(retry.getVersion() + 1);
            return null;
        }).when(outboxService).markRetry(any(), anyInt(), any(Date.class),
                any(), anyString(), anyString());
    }

    @Test
    @DisplayName("远端成功而本地关联首次失败时恢复只重做本地 CAS")
    void shouldRecoverFinalizeWithoutStartingRemoteTwice()
    {
        when(remoteService.start(any(), eq(SecurityConstants.INNER)))
                .thenReturn(R.ok(response(9001L, 1)));
        doThrow(new ServiceException("injected finalize failure"))
                .doNothing().when(outboxService)
                .finalizeRemoteSuccess(any(), anyString());

        dispatcher.dispatchOneNow(10L);
        assertThat(row.getRemoteInstanceId()).isEqualTo(9001L);
        assertThat(row.getStatus()).isEqualTo(
                HrHealthCertificateApprovalStartOutboxService.RETRY);

        dispatcher.dispatchOneNow(10L);

        verify(remoteService, times(1)).start(any(),
                eq(SecurityConstants.INNER));
        verify(outboxService, times(2)).finalizeRemoteSuccess(any(),
                anyString());
    }

    @Test
    @DisplayName("超时重试始终重放同一请求幂等键")
    void shouldRetryTimeoutWithStableIdempotencyKey()
    {
        when(remoteService.start(any(), eq(SecurityConstants.INNER)))
                .thenThrow(new RuntimeException(new TimeoutException()))
                .thenReturn(R.ok(response(9002L, 1)));
        doNothing().when(outboxService).finalizeRemoteSuccess(any(),
                anyString());

        dispatcher.dispatchOneNow(10L);
        dispatcher.dispatchOneNow(10L);

        ArgumentCaptor<ApprovalStartRequest> requests = ArgumentCaptor
                .forClass(ApprovalStartRequest.class);
        verify(remoteService, times(2)).start(requests.capture(),
                eq(SecurityConstants.INNER));
        assertThat(requests.getAllValues())
                .extracting(ApprovalStartRequest::getIdempotencyKey)
                .containsOnly("HR_HEALTH_CERTIFICATE:88:1");
        verify(outboxService).recordRemoteSucceeded(any(), eq(9002L),
                eq("RUNNING"), eq(1), eq(R.SUCCESS));
    }

    @Test
    @DisplayName("确定性四百错误进入可人工重放的失败态")
    void shouldMakePermanentClientFailureObservable()
    {
        when(remoteService.start(any(), eq(SecurityConstants.INNER)))
                .thenReturn(R.fail(400, "invalid rule"));

        dispatcher.dispatchOneNow(10L);

        verify(outboxService).markFailed(row, 400, "REMOTE_HTTP_400",
                "审批中心拒绝发起请求");
        verify(outboxService, never()).markRetry(any(), anyInt(), any(),
                any(), anyString(), anyString());
    }

    @Test
    @DisplayName("五百错误保留为自动重试而不误判永久失败")
    void shouldRetryServerFailure()
    {
        when(remoteService.start(any(), eq(SecurityConstants.INNER)))
                .thenReturn(R.fail(503, "unavailable"));

        dispatcher.dispatchOneNow(10L);

        verify(outboxService).markRetry(eq(row), eq(1), any(Date.class),
                eq(503), eq("REMOTE_HTTP_503"),
                eq("审批中心暂时无法完成请求"));
        verify(outboxService, never()).markFailed(any(), any(), anyString(),
                anyString());
    }

    @Test
    @DisplayName("立即派发查询失败不向已提交的用户操作泄漏异常")
    void shouldContainImmediateLookupFailure()
    {
        when(outboxService.selectById(10L))
                .thenThrow(new ServiceException("database unavailable"));

        assertThatCode(() -> dispatcher.dispatchOneNow(10L))
                .doesNotThrowAnyException();

        verify(remoteService, never()).start(any(), anyString());
    }

    private static HrHealthCertificateApprovalStartOutbox row(
            ApprovalStartRequest request)
    {
        HrHealthCertificateApprovalStartOutbox value =
                new HrHealthCertificateApprovalStartOutbox();
        value.setOutboxId(10L);
        value.setCertificateId(88L);
        value.setBusinessRound(1);
        value.setIdempotencyKey(request.getIdempotencyKey());
        value.setRequestJson(JSON.toJSONString(request));
        value.setStatus(
                HrHealthCertificateApprovalStartOutboxService.PENDING);
        value.setRetryCount(0);
        value.setVersion(0L);
        value.setCreateBy("tester");
        return value;
    }

    private static ApprovalStartRequest validRequest()
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

    private static ApprovalStartResponse response(Long instanceId, int round)
    {
        ApprovalStartResponse response = new ApprovalStartResponse();
        response.setInstanceId(instanceId);
        response.setBusinessRound(round);
        response.setStatus("RUNNING");
        response.setCreated(true);
        return response;
    }
}
