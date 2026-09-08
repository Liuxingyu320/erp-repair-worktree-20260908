package com.erp.oa.service.impl;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.erp.approval.api.RemoteApprovalService;
import com.erp.approval.api.domain.ApprovalStartRequest;
import com.erp.approval.api.domain.ApprovalStartResponse;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.domain.R;
import com.erp.common.core.utils.StringUtils;
import com.erp.oa.domain.OaPurchaseApprovalStartOutbox;
import feign.FeignException;

/** 仅在本地采购提交事务成功后发起统一审批。 */
@Service
public class OaPurchaseApprovalStartDispatcher
{
    private static final Logger log = LoggerFactory.getLogger(
            OaPurchaseApprovalStartDispatcher.class);
    private static final int[] RETRY_MINUTES = { 1, 5, 30, 120, 360 };

    private final OaPurchaseApprovalStartOutboxService outboxService;
    private final RemoteApprovalService remoteApprovalService;
    private final Clock clock;

    @Autowired
    public OaPurchaseApprovalStartDispatcher(
            OaPurchaseApprovalStartOutboxService outboxService,
            RemoteApprovalService remoteApprovalService)
    {
        this(outboxService, remoteApprovalService, Clock.systemUTC());
    }

    OaPurchaseApprovalStartDispatcher(
            OaPurchaseApprovalStartOutboxService outboxService,
            RemoteApprovalService remoteApprovalService, Clock clock)
    {
        this.outboxService = outboxService;
        this.remoteApprovalService = remoteApprovalService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${oa.purchase.approval-start.dispatch-fixed-delay-ms:30000}")
    public void dispatchDue()
    {
        Instant now = clock.instant();
        List<OaPurchaseApprovalStartOutbox> rows = outboxService.selectDue(
                Date.from(now), Date.from(now.minus(5, ChronoUnit.MINUTES)),
                100);
        if (rows == null) return;
        for (OaPurchaseApprovalStartOutbox row : rows)
        {
            dispatchSafely(row, now);
        }
    }

    /** 用户提交后尝试一次；任何失败仍由定时调度恢复。 */
    public void dispatchOneNow(Long outboxId)
    {
        try
        {
            OaPurchaseApprovalStartOutbox row = outboxService.selectById(
                    outboxId);
            if (row != null)
            {
                dispatchOne(row, clock.instant());
            }
        }
        catch (RuntimeException failure)
        {
            log.error("采购审批发起立即派发失败，outboxId={}, type={}",
                    outboxId, failure.getClass().getSimpleName());
        }
    }

    private void dispatchSafely(OaPurchaseApprovalStartOutbox row,
            Instant now)
    {
        try
        {
            dispatchOne(row, now);
        }
        catch (RuntimeException stateFailure)
        {
            log.error("采购审批发起发件箱状态转换失败，outboxId={}, purchaseId={}, type={}",
                    row == null ? null : row.getOutboxId(),
                    row == null ? null : row.getPurchaseId(),
                    stateFailure.getClass().getSimpleName());
        }
    }

    private void dispatchOne(OaPurchaseApprovalStartOutbox row, Instant now)
    {
        if (!outboxService.claim(row)) return;
        if (row.getRemoteInstanceId() != null)
        {
            finalizeKnownRemote(row, now);
            return;
        }

        ApprovalStartRequest request;
        try
        {
            request = JSON.parseObject(row.getRequestJson(),
                    ApprovalStartRequest.class);
            validateRequest(row, request);
        }
        catch (JSONException | IllegalArgumentException invalid)
        {
            outboxService.markFailed(row, null, "INVALID_PAYLOAD",
                    "审批发起快照无效");
            return;
        }

        R<ApprovalStartResponse> response;
        try
        {
            response = remoteApprovalService.start(request,
                    SecurityConstants.INNER);
        }
        catch (RuntimeException remoteFailure)
        {
            Integer status = findHttpStatus(remoteFailure);
            if (status != null)
            {
                classifyHttp(row, now, status);
            }
            else
            {
                boolean timeout = containsTimeout(remoteFailure);
                scheduleRetry(row, now, null,
                        timeout ? "REMOTE_TIMEOUT" : "REMOTE_UNAVAILABLE",
                        timeout ? "审批中心调用超时"
                                : "审批中心暂时不可用");
            }
            return;
        }
        if (response == null)
        {
            scheduleRetry(row, now, null, "REMOTE_UNAVAILABLE",
                    "审批中心未返回结果");
            return;
        }
        if (!R.isSuccess(response))
        {
            classifyHttp(row, now, response.getCode());
            return;
        }
        ApprovalStartResponse result = response.getData();
        if (result == null || result.getInstanceId() == null
                || result.getInstanceId() <= 0)
        {
            outboxService.markFailed(row, response.getCode(),
                    "INVALID_REMOTE_RESPONSE", "审批中心成功结果缺少实例ID");
            return;
        }
        if (!Objects.equals(request.getBusinessRound(),
                result.getBusinessRound()))
        {
            row.setRemoteInstanceId(result.getInstanceId());
            row.setRemoteStatus(result.getStatus());
            row.setRemoteBusinessRound(result.getBusinessRound());
            outboxService.markFailed(row, response.getCode(),
                    "REMOTE_ROUND_MISMATCH", "审批中心返回轮次不一致");
            return;
        }

        /* 先单独固化远端成功，再做本地关联。 */
        outboxService.recordRemoteSucceeded(row, result.getInstanceId(),
                result.getStatus(), result.getBusinessRound(),
                response.getCode());
        finalizeKnownRemote(row, now);
    }

    private void finalizeKnownRemote(OaPurchaseApprovalStartOutbox row,
            Instant now)
    {
        try
        {
            outboxService.finalizeRemoteSuccess(row,
                    StringUtils.isBlank(row.getCreateBy())
                            ? "approval-outbox" : row.getCreateBy());
        }
        catch (OaPurchaseApprovalStartOutboxService.PermanentFailure failure)
        {
            outboxService.markFailed(row, row.getLastHttpStatus(),
                    failure.getErrorCode(), failure.getMessage());
        }
        catch (RuntimeException transientFailure)
        {
            scheduleRetry(row, now, row.getLastHttpStatus(),
                    "LOCAL_FINALIZE_RETRY",
                    "远端已成功，本地采购审批关联待重试");
        }
    }

    private void validateRequest(OaPurchaseApprovalStartOutbox row,
            ApprovalStartRequest request)
    {
        String expectedKey = OaPurchaseApprovalStartOutboxService.BUSINESS_CODE
                + ":" + row.getPurchaseId() + ":" + row.getBusinessRound();
        if (request == null
                || !OaPurchaseApprovalStartOutboxService.BUSINESS_CODE.equals(
                        request.getBusinessCode())
                || !Objects.equals(String.valueOf(row.getPurchaseId()),
                        request.getBusinessId())
                || !Objects.equals(row.getBusinessRound(),
                        request.getBusinessRound())
                || !Objects.equals(row.getIdempotencyKey(),
                        request.getIdempotencyKey())
                || !Objects.equals(expectedKey, request.getIdempotencyKey())
                || request.getApplicantId() == null)
        {
            throw new IllegalArgumentException("request snapshot mismatch");
        }
    }

    private void classifyHttp(OaPurchaseApprovalStartOutbox row,
            Instant now, int status)
    {
        String code = "REMOTE_HTTP_" + status;
        if (status >= 400 && status < 500 && status != 408
                && status != 425 && status != 429)
        {
            outboxService.markFailed(row, status, code,
                    "审批中心拒绝发起请求");
        }
        else
        {
            scheduleRetry(row, now, status, code,
                    "审批中心暂时无法完成请求");
        }
    }

    private void scheduleRetry(OaPurchaseApprovalStartOutbox row,
            Instant now, Integer status, String code, String message)
    {
        int previous = row.getRetryCount() == null ? 0
                : row.getRetryCount();
        int delay = RETRY_MINUTES[Math.min(previous,
                RETRY_MINUTES.length - 1)];
        outboxService.markRetry(row, previous + 1,
                Date.from(now.plus(delay, ChronoUnit.MINUTES)), status, code,
                message);
    }

    private Integer findHttpStatus(Throwable failure)
    {
        Throwable current = failure;
        while (current != null)
        {
            if (current instanceof FeignException feign
                    && feign.status() > 0)
            {
                return feign.status();
            }
            current = current.getCause();
        }
        return null;
    }

    private boolean containsTimeout(Throwable failure)
    {
        Throwable current = failure;
        while (current != null)
        {
            if (current instanceof SocketTimeoutException
                    || current instanceof HttpTimeoutException
                    || current instanceof TimeoutException)
            {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
