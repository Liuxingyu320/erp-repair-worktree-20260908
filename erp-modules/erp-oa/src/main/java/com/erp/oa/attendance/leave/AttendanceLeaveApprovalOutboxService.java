package com.erp.oa.attendance.leave;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.erp.approval.api.domain.ApprovalStartRequest;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.attendance.leave.AttendanceLeaveModels.ApprovalOutbox;
import com.erp.oa.attendance.leave.AttendanceLeaveModels.LeaveRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Short-transaction state owner for reliable leave approval start. */
@Service
public class AttendanceLeaveApprovalOutboxService
{
    public static final String BUSINESS_CODE = "OA_ATTENDANCE_LEAVE";
    public static final String PENDING = "PENDING";
    public static final String PROCESSING = "PROCESSING";
    public static final String RETRY = "RETRY";
    public static final String REMOTE_SUCCEEDED = "REMOTE_SUCCEEDED";
    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";
    private static final Set<String> CLAIMABLE = Set.of(PENDING, PROCESSING,
            RETRY, REMOTE_SUCCEEDED);

    private final AttendanceLeaveMapper mapper;
    private final ObjectMapper objectMapper;

    public AttendanceLeaveApprovalOutboxService(AttendanceLeaveMapper mapper,
            ObjectMapper objectMapper)
    {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class)
    public ApprovalOutbox enqueue(LeaveRequest request,
            ApprovalStartRequest command, String operator)
    {
        validateEnqueue(request, command, operator);
        String json;
        try { json = objectMapper.writeValueAsString(command); }
        catch (JsonProcessingException exception)
        { throw new ServiceException("LEAVE_APPROVAL_SNAPSHOT_FAILED"); }
        ApprovalOutbox row = new ApprovalOutbox();
        row.leaveRequestId = request.leaveRequestId;
        row.businessRound = command.getBusinessRound();
        row.idempotencyKey = command.getIdempotencyKey();
        row.requestJson = json;
        row.status = PENDING;
        row.attemptCount = 0;
        row.requestRowVersion = request.rowVersion;
        row.rowVersion = 0L;
        row.createBy = operator.trim();
        if (mapper.insertApprovalOutbox(row) == 1) return row;
        ApprovalOutbox existing = mapper.selectApprovalOutboxByRound(
                request.leaveRequestId, command.getBusinessRound());
        if (existing == null
                || !Objects.equals(row.idempotencyKey,
                        existing.idempotencyKey)
                || !Objects.equals(row.requestJson, existing.requestJson))
            throw new ServiceException("LEAVE_APPROVAL_IDEMPOTENCY_CONFLICT");
        return existing;
    }

    public ApprovalOutbox selectById(Long outboxId)
    { return outboxId == null ? null : mapper.selectApprovalOutboxById(outboxId); }

    public ApprovalOutbox selectByRound(Long requestId, Integer round)
    {
        return requestId == null || round == null ? null
                : mapper.selectApprovalOutboxByRound(requestId, round);
    }

    public List<ApprovalOutbox> selectDue(LocalDateTime dueAt,
            LocalDateTime staleClaimedBefore, int limit)
    {
        return mapper.selectDueApprovalOutboxes(dueAt, staleClaimedBefore,
                Math.max(1, Math.min(limit, 100)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public boolean claim(ApprovalOutbox row)
    {
        if (row == null || row.outboxId == null || row.rowVersion == null
                || !CLAIMABLE.contains(row.status)) return false;
        String token = UUID.randomUUID().toString().replace("-", "");
        if (mapper.claimApprovalOutbox(row.outboxId, row.status,
                row.rowVersion, token) != 1) return false;
        row.status = PROCESSING;
        row.claimToken = token;
        row.claimedAt = LocalDateTime.now();
        row.attemptCount = (row.attemptCount == null ? 0 : row.attemptCount) + 1;
        row.rowVersion++;
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public void recordRemoteSucceeded(ApprovalOutbox row, Long instanceId,
            String remoteStatus, Integer remoteRound, Integer httpStatus)
    {
        if (row == null || instanceId == null || instanceId <= 0
                || !Objects.equals(row.businessRound, remoteRound))
            throw new ServiceException("LEAVE_APPROVAL_REMOTE_RESULT_INVALID");
        assertUpdated(mapper.markApprovalRemoteSucceeded(row.outboxId,
                row.status, row.rowVersion, instanceId,
                truncate(remoteStatus, 32), remoteRound, httpStatus));
        row.status = REMOTE_SUCCEEDED;
        row.remoteInstanceId = instanceId;
        row.remoteStatus = truncate(remoteStatus, 32);
        row.remoteBusinessRound = remoteRound;
        row.lastHttpStatus = httpStatus;
        row.rowVersion++;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public void finalizeRemoteSuccess(ApprovalOutbox candidate, String operator)
    {
        if (candidate == null || candidate.outboxId == null)
            throw new PermanentFailure("OUTBOX_NOT_FOUND",
                    "请假审批发件箱不存在");
        ApprovalOutbox row = mapper.selectApprovalOutboxByIdForUpdate(
                candidate.outboxId);
        if (row == null)
            throw new PermanentFailure("OUTBOX_NOT_FOUND",
                    "请假审批发件箱不存在");
        if (COMPLETED.equals(row.status))
        {
            sync(candidate, row);
            return;
        }
        if (!PROCESSING.equals(row.status)
                && !REMOTE_SUCCEEDED.equals(row.status))
            throw new ServiceException("LEAVE_APPROVAL_OUTBOX_STATE_CHANGED");
        if (row.remoteInstanceId == null || row.remoteInstanceId <= 0)
            throw new PermanentFailure("REMOTE_INSTANCE_MISSING",
                    "请假审批实例缺失");
        if (!Objects.equals(row.businessRound, row.remoteBusinessRound))
            throw new PermanentFailure("REMOTE_ROUND_MISMATCH",
                    "请假审批实例轮次不一致");
        LeaveRequest request = mapper.selectLeaveRequestByIdForUpdate(
                row.leaveRequestId);
        if (request == null)
            throw new PermanentFailure("LEAVE_REQUEST_NOT_FOUND",
                    "请假申请不存在");
        if (request.approvalInstanceId != null)
        {
            if (!Objects.equals(request.approvalInstanceId,
                    row.remoteInstanceId))
                throw new PermanentFailure("INSTANCE_CONFLICT",
                        "请假申请已关联其他审批实例");
            if (!"PENDING".equals(request.status)
                    || !Objects.equals(request.businessRound,
                            row.businessRound))
                throw new PermanentFailure("LEAVE_STATE_CHANGED",
                        "请假申请状态已变化");
        }
        else
        {
            if (!"SUBMITTING".equals(request.status)
                    || !Objects.equals(request.businessRound,
                            row.businessRound)
                    || !Objects.equals(request.rowVersion,
                            row.requestRowVersion))
                throw new PermanentFailure("LEAVE_VERSION_CONFLICT",
                        "请假申请不再属于当前提交轮次");
            if (mapper.finalizeLeaveApprovalStart(row.leaveRequestId,
                    row.businessRound, row.requestRowVersion,
                    row.remoteInstanceId, safeOperator(operator)) != 1)
                throw new ServiceException("LEAVE_APPROVAL_FINALIZE_RETRY");
        }
        assertUpdated(mapper.markApprovalCompleted(row.outboxId, row.status,
                row.rowVersion));
        row.status = COMPLETED;
        row.rowVersion++;
        sync(candidate, row);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public void markRetry(ApprovalOutbox row, LocalDateTime nextRetryAt,
            Integer httpStatus, String errorCode, String error)
    {
        assertUpdated(mapper.markApprovalRetry(row.outboxId, row.status,
                row.rowVersion, nextRetryAt, httpStatus,
                truncate(errorCode, 64), truncate(error, 500)));
        row.status = RETRY;
        row.nextRetryAt = nextRetryAt;
        row.lastHttpStatus = httpStatus;
        row.lastErrorCode = truncate(errorCode, 64);
        row.lastError = truncate(error, 500);
        row.rowVersion++;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public void markFailed(ApprovalOutbox row, Integer httpStatus,
            String errorCode, String error)
    {
        assertUpdated(mapper.markApprovalFailed(row.outboxId, row.status,
                row.rowVersion, httpStatus, truncate(errorCode, 64),
                truncate(error, 500)));
        row.status = FAILED;
        row.lastHttpStatus = httpStatus;
        row.lastErrorCode = truncate(errorCode, 64);
        row.lastError = truncate(error, 500);
        row.rowVersion++;
    }

    private void validateEnqueue(LeaveRequest request,
            ApprovalStartRequest command, String operator)
    {
        if (request == null || request.leaveRequestId == null
                || request.rowVersion == null || command == null
                || !BUSINESS_CODE.equals(command.getBusinessCode())
                || !Objects.equals(String.valueOf(request.leaveRequestId),
                        command.getBusinessId())
                || command.getBusinessRound() == null
                || !Objects.equals(request.businessRound,
                        command.getBusinessRound())
                || !Objects.equals(BUSINESS_CODE + ":"
                        + request.leaveRequestId + ":"
                        + command.getBusinessRound(),
                        command.getIdempotencyKey())
                || operator == null || operator.isBlank())
            throw new ServiceException("LEAVE_APPROVAL_ENQUEUE_INVALID");
    }

    private void assertUpdated(int rows)
    {
        if (rows != 1)
            throw new ServiceException("LEAVE_APPROVAL_OUTBOX_CONFLICT");
    }

    private String safeOperator(String value)
    { return value == null || value.isBlank() ? "approval-outbox" : value.trim(); }

    private String truncate(String value, int max)
    { return value == null ? null : value.length() <= max ? value : value.substring(0, max); }

    private void sync(ApprovalOutbox target, ApprovalOutbox source)
    {
        target.status = source.status;
        target.rowVersion = source.rowVersion;
        target.remoteInstanceId = source.remoteInstanceId;
        target.remoteBusinessRound = source.remoteBusinessRound;
    }

    public static final class PermanentFailure extends RuntimeException
    {
        private static final long serialVersionUID = 1L;
        private final String errorCode;
        public PermanentFailure(String code, String message)
        { super(message); errorCode = code; }
        public String getErrorCode() { return errorCode; }
    }
}
