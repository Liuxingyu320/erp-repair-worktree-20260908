package com.erp.oa.attendance.correction;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.erp.approval.api.RemoteApprovalService;
import com.erp.approval.api.domain.ApprovalStartRequest;
import com.erp.approval.api.domain.ApprovalStartResponse;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.domain.R;
import com.erp.oa.attendance.correction.AttendanceCorrectionModels.ApprovalOutbox;
import com.erp.oa.service.BusinessFeatureGate;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;

/** Durable correction approval dispatcher with retry and remote-success recovery. */
@Service
public class AttendanceCorrectionApprovalDispatcher
{
    private static final Logger log = LoggerFactory.getLogger(
            AttendanceCorrectionApprovalDispatcher.class);
    private static final int[] RETRY_MINUTES = { 1, 5, 30, 120, 360 };

    private final AttendanceCorrectionApprovalOutboxService outboxService;
    private final RemoteApprovalService remoteApprovalService;
    private final BusinessFeatureGate featureGate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public AttendanceCorrectionApprovalDispatcher(
            AttendanceCorrectionApprovalOutboxService outboxService,
            RemoteApprovalService remoteApprovalService,
            BusinessFeatureGate featureGate, ObjectMapper objectMapper)
    {
        this(outboxService, remoteApprovalService, featureGate, objectMapper,
                Clock.systemDefaultZone());
    }

    AttendanceCorrectionApprovalDispatcher(
            AttendanceCorrectionApprovalOutboxService outboxService,
            RemoteApprovalService remoteApprovalService,
            BusinessFeatureGate featureGate, ObjectMapper objectMapper,
            Clock clock)
    {
        this.outboxService = outboxService;
        this.remoteApprovalService = remoteApprovalService;
        this.featureGate = featureGate;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString =
            "${oa.attendance-v2.correction.approval-start-delay-ms:30000}")
    public void dispatchDue()
    {
        if (!featureGate.isEnabled(BusinessFeatureGate.ATTENDANCE_V2)) return;
        LocalDateTime now = now();
        List<ApprovalOutbox> rows = outboxService.selectDue(now,
                now.minusMinutes(5), 100);
        if (rows == null) return;
        for (ApprovalOutbox row : rows) dispatchSafely(row, now);
    }

    public void dispatchOneNow(Long outboxId)
    {
        if (!featureGate.isEnabled(BusinessFeatureGate.ATTENDANCE_V2)) return;
        try
        {
            ApprovalOutbox row = outboxService.selectById(outboxId);
            if (row != null) dispatchOne(row, now());
        }
        catch (RuntimeException failure)
        {
            log.error("correction approval dispatch failed, outboxId={}, type={}",
                    outboxId, failure.getClass().getSimpleName());
        }
    }

    private void dispatchSafely(ApprovalOutbox row, LocalDateTime now)
    {
        try { dispatchOne(row, now); }
        catch (RuntimeException failure)
        {
            log.error("correction approval state transition failed, outboxId={}, type={}",
                    row == null ? null : row.outboxId,
                    failure.getClass().getSimpleName());
        }
    }

    private void dispatchOne(ApprovalOutbox row, LocalDateTime now)
    {
        if (!outboxService.claim(row)) return;
        if (row.remoteInstanceId != null)
        {
            finalizeKnownRemote(row, now);
            return;
        }
        ApprovalStartRequest command;
        try
        {
            command = objectMapper.readValue(row.requestJson,
                    ApprovalStartRequest.class);
            validateSnapshot(row, command);
        }
        catch (Exception invalid)
        {
            outboxService.markFailed(row, null, "INVALID_PAYLOAD",
                    "补卡审批发起快照无效");
            return;
        }
        R<ApprovalStartResponse> response;
        try
        {
            response = remoteApprovalService.start(command,
                    SecurityConstants.INNER);
        }
        catch (RuntimeException failure)
        {
            Integer status = httpStatus(failure);
            if (status != null) classifyHttp(row, now, status);
            else scheduleRetry(row, now, null,
                    containsTimeout(failure) ? "REMOTE_TIMEOUT"
                            : "REMOTE_UNAVAILABLE",
                    containsTimeout(failure) ? "审批中心调用超时"
                            : "审批中心暂时不可用");
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
                    "INVALID_REMOTE_RESPONSE", "审批中心结果缺少实例ID");
            return;
        }
        if (!Objects.equals(row.businessRound, result.getBusinessRound()))
        {
            outboxService.markFailed(row, response.getCode(),
                    "REMOTE_ROUND_MISMATCH", "审批中心返回业务轮次不一致");
            return;
        }
        outboxService.recordRemoteSucceeded(row, result.getInstanceId(),
                result.getStatus(), result.getBusinessRound(),
                response.getCode());
        finalizeKnownRemote(row, now);
    }

    private void finalizeKnownRemote(ApprovalOutbox row, LocalDateTime now)
    {
        try
        {
            outboxService.finalizeRemoteSuccess(row,
                    row.createBy == null ? "approval-outbox" : row.createBy);
        }
        catch (AttendanceCorrectionApprovalOutboxService.PermanentFailure failure)
        {
            outboxService.markFailed(row, row.lastHttpStatus,
                    failure.getErrorCode(), failure.getMessage());
        }
        catch (RuntimeException transientFailure)
        {
            scheduleRetry(row, now, row.lastHttpStatus,
                    "LOCAL_FINALIZE_RETRY", "远端已成功，本地补卡关联待重试");
        }
    }

    private void validateSnapshot(ApprovalOutbox row,
            ApprovalStartRequest request)
    {
        String expectedKey =
                AttendanceCorrectionApprovalOutboxService.BUSINESS_CODE
                + ":" + row.correctionRequestId + ":" + row.businessRound;
        if (request == null
                || !AttendanceCorrectionApprovalOutboxService.BUSINESS_CODE
                        .equals(request.getBusinessCode())
                || !Objects.equals(String.valueOf(row.correctionRequestId),
                        request.getBusinessId())
                || !Objects.equals(row.businessRound,
                        request.getBusinessRound())
                || !Objects.equals(row.idempotencyKey,
                        request.getIdempotencyKey())
                || !expectedKey.equals(request.getIdempotencyKey())
                || request.getApplicantId() == null
                || request.getAnchorDeptId() == null)
            throw new IllegalArgumentException("snapshot mismatch");
    }

    private void classifyHttp(ApprovalOutbox row, LocalDateTime now,
            int status)
    {
        String code = "REMOTE_HTTP_" + status;
        if (status >= 400 && status < 500 && status != 408
                && status != 425 && status != 429)
            outboxService.markFailed(row, status, code, "审批中心拒绝发起请求");
        else scheduleRetry(row, now, status, code, "审批中心暂时无法完成请求");
    }

    private void scheduleRetry(ApprovalOutbox row, LocalDateTime now,
            Integer status, String code, String message)
    {
        int attempts = row.attemptCount == null ? 1 : row.attemptCount;
        int delay = RETRY_MINUTES[Math.min(Math.max(0, attempts - 1),
                RETRY_MINUTES.length - 1)];
        outboxService.markRetry(row, now.plusMinutes(delay), status, code,
                message);
    }

    private Integer httpStatus(Throwable failure)
    {
        for (Throwable current = failure; current != null;
                current = current.getCause())
            if (current instanceof FeignException feign
                    && feign.status() > 0) return feign.status();
        return null;
    }

    private boolean containsTimeout(Throwable failure)
    {
        for (Throwable current = failure; current != null;
                current = current.getCause())
            if (current instanceof SocketTimeoutException
                    || current instanceof HttpTimeoutException
                    || current instanceof TimeoutException) return true;
        return false;
    }

    private LocalDateTime now()
    { return LocalDateTime.now(clock).withNano(0); }
}
