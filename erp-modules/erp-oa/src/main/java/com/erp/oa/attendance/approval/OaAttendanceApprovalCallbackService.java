package com.erp.oa.attendance.approval;

import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.approval.api.domain.ApprovalBusinessCallbackRequest;
import com.erp.approval.api.domain.ApprovalBusinessCallbackResponse;
import com.erp.approval.api.domain.ApprovalBusinessActionValidationRequest;
import com.erp.approval.api.domain.ApprovalBusinessActionValidationResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Idempotent, fail-closed callback owner for leave and correction requests. */
@Service
public class OaAttendanceApprovalCallbackService
{
    public static final String LEAVE = "OA_ATTENDANCE_LEAVE";
    public static final String CORRECTION = "OA_ATTENDANCE_CORRECTION";
    private static final String PENDING = "PENDING";
    private static final String SUBMITTING = "SUBMITTING";

    private final OaAttendanceApprovalCallbackMapper mapper;
    private final ObjectMapper objectMapper;

    public OaAttendanceApprovalCallbackService(
            OaAttendanceApprovalCallbackMapper mapper,
            ObjectMapper objectMapper)
    {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public boolean supports(String businessCode)
    {
        return LEAVE.equals(businessCode) || CORRECTION.equals(businessCode);
    }

    /**
     * Revalidates immutable leave evidence against the live business owner
     * immediately before the approval center persists an APPROVE action.
     */
    public ApprovalBusinessActionValidationResponse validateAction(
            ApprovalBusinessActionValidationRequest request)
    {
        if (request == null || request.getInstanceId() == null
                || request.getBusinessRound() == null
                || blank(request.getBusinessCode())
                || blank(request.getBusinessId())
                || blank(request.getAction())
                || request.getApplicantUserId() == null
                || request.getAnchorDeptId() == null
                || blank(request.getBusinessSnapshot()))
        {
            return actionResponse(false, "INVALID_VALIDATION_REQUEST",
                    "请假审批校验字段不完整");
        }
        if (!LEAVE.equals(request.getBusinessCode())
                || !"APPROVE".equals(request.getAction().trim()
                        .toUpperCase(Locale.ROOT)))
        {
            return actionResponse(false, "UNSUPPORTED_APPROVAL_ACTION",
                    "OA 不支持该审批动作校验");
        }
        long businessId;
        try
        {
            businessId = Long.parseLong(request.getBusinessId());
        }
        catch (NumberFormatException exception)
        {
            return actionResponse(false, "INVALID_BUSINESS_ID",
                    "请假业务ID不是有效数字");
        }
        OaAttendanceApprovalState current = mapper.selectLeave(businessId);
        if (current == null)
        {
            return actionResponse(false, "BUSINESS_NOT_FOUND",
                    "请假申请不存在");
        }
        if (!PENDING.equals(current.getStatus())
                || !Objects.equals(request.getInstanceId(),
                        current.getApprovalInstanceId())
                || !Objects.equals(request.getBusinessRound(),
                        current.getBusinessRound()))
        {
            return actionResponse(false, "BUSINESS_LINK_MISMATCH",
                    "请假申请已不属于当前审批实例或轮次");
        }
        if (!Objects.equals(request.getApplicantUserId(), current.getUserId())
                || !Objects.equals(request.getAnchorDeptId(),
                        current.getShopId()))
        {
            return actionResponse(false, "BUSINESS_SCOPE_MISMATCH",
                    "请假申请人与门店锚点不匹配");
        }
        JsonNode snapshot;
        try
        {
            snapshot = objectMapper.readTree(request.getBusinessSnapshot());
        }
        catch (Exception exception)
        {
            return actionResponse(false, "INVALID_BUSINESS_SNAPSHOT",
                    "请假审批快照不是有效JSON");
        }
        JsonNode snapshotBusinessId = snapshot == null ? null
                : snapshot.get("leaveRequestId");
        JsonNode snapshotShopId = snapshot == null ? null
                : snapshot.get("shopId");
        JsonNode snapshotCount = snapshot == null ? null
                : snapshot.get("attachmentCount");
        JsonNode snapshotRequired = snapshot == null ? null
                : snapshot.get("attachmentRequired");
        if (!integral(snapshotBusinessId) || !integral(snapshotShopId)
                || !integral(snapshotCount)
                || snapshotRequired == null
                || !snapshotRequired.isBoolean())
        {
            return actionResponse(false, "INCOMPLETE_EVIDENCE_SNAPSHOT",
                    "请假审批快照缺少附件策略，请退回后重新提交");
        }
        if (snapshotBusinessId.longValue() != businessId
                || snapshotShopId.longValue() != current.getShopId())
        {
            return actionResponse(false, "SNAPSHOT_SCOPE_MISMATCH",
                    "请假审批快照的业务或门店范围不匹配");
        }
        int expectedCount = snapshotCount.intValue();
        if (expectedCount < 0)
        {
            return actionResponse(false, "INVALID_ATTACHMENT_COUNT",
                    "请假审批快照附件数量无效");
        }
        int liveCount = mapper.countLeaveAttachments(businessId);
        if (liveCount != expectedCount)
        {
            return actionResponse(false, "ATTACHMENT_COUNT_CHANGED",
                    "请假附件与提交时快照不一致，请退回核对");
        }
        if (snapshotRequired.booleanValue() && liveCount <= 0)
        {
            return actionResponse(false, "REQUIRED_ATTACHMENT_MISSING",
                    "该请假申请必须包含证明附件");
        }
        return actionResponse(true, "ACCEPTED", "请假审批证据校验通过");
    }

    @Transactional(rollbackFor = Exception.class)
    public ApprovalBusinessCallbackResponse apply(
            ApprovalBusinessCallbackRequest request)
    {
        ApprovalBusinessCallbackResponse invalid = validate(request);
        if (invalid != null) return invalid;

        long businessId;
        try
        {
            businessId = Long.parseLong(request.getBusinessId());
        }
        catch (NumberFormatException exception)
        {
            return retry("INVALID_BUSINESS_ID", "考勤业务ID不是有效数字");
        }

        OaAttendanceApprovalState current = LEAVE.equals(
                request.getBusinessCode())
                        ? mapper.selectLeaveForUpdate(businessId)
                        : mapper.selectCorrectionForUpdate(businessId);
        if (current == null)
        {
            return stale(request, "BUSINESS_NOT_FOUND", "考勤申请不存在");
        }
        if (Objects.equals(request.getEventKey(),
                current.getLastApprovalEventKey()))
        {
            return accepted("IDEMPOTENT", "审批事件已处理");
        }
        if (SUBMITTING.equals(current.getStatus())
                && current.getApprovalInstanceId() == null
                && Objects.equals(request.getBusinessRound(),
                        current.getBusinessRound()))
        {
            return retry("APPROVAL_LINK_PENDING", "审批实例正在完成本地关联");
        }
        if (!Objects.equals(request.getInstanceId(),
                current.getApprovalInstanceId())
                || !Objects.equals(request.getBusinessRound(),
                        current.getBusinessRound()))
        {
            return stale(request, "STALE_APPROVAL_EVENT",
                    "审批实例或业务轮次已变更");
        }
        Decision decision = decision(request.getAction());
        String payloadTarget = payloadTarget(request.getPayload());
        if (decision == null || payloadTarget == null
                || !decision.approvalStatus().equals(payloadTarget))
        {
            return retry("INVALID_APPROVAL_TARGET", "审批动作与目标状态不一致");
        }
        boolean terminatingApprovedLeave = LEAVE.equals(
                request.getBusinessCode())
                && "APPROVED".equals(current.getStatus())
                && "TERMINATE".equals(request.getAction().trim()
                        .toUpperCase(Locale.ROOT))
                && "CANCELLED".equals(decision.businessStatus());
        if (!PENDING.equals(current.getStatus())
                && !terminatingApprovedLeave)
        {
            return stale(request, "BUSINESS_STATUS_CHANGED",
                    "考勤申请已不处于审批中");
        }
        String expectedStatus = current.getStatus();
        Date now = new Date();
        int updated = LEAVE.equals(request.getBusinessCode())
                ? mapper.updateLeaveDecision(businessId, expectedStatus,
                        current.getRowVersion(), decision.businessStatus(),
                        request.getEventKey(), now)
                : mapper.updateCorrectionDecision(businessId, expectedStatus,
                        current.getRowVersion(), decision.businessStatus(),
                        request.getEventKey(), now);
        if (updated != 1)
        {
            return retry("CONCURRENT_UPDATE", "考勤审批回调并发冲突");
        }
        // Every terminal decision changes the evidence set. Reopen the daily
        // result even for rejection/return/cancellation so a previously
        // pending or settled payroll source cannot remain stale.
        if (LEAVE.equals(request.getBusinessCode()))
            mapper.invalidateLeaveDayResults(businessId, now);
        else mapper.invalidateCorrectionDayResult(businessId, now);
        return accepted("ACCEPTED", "考勤审批状态已更新");
    }

    private ApprovalBusinessCallbackResponse validate(
            ApprovalBusinessCallbackRequest request)
    {
        if (request == null || blank(request.getEventKey())
                || request.getInstanceId() == null
                || blank(request.getBusinessCode())
                || blank(request.getBusinessId())
                || request.getBusinessRound() == null
                || blank(request.getAction()))
        {
            return retry("INVALID_CALLBACK", "审批回调字段不完整");
        }
        if (!supports(request.getBusinessCode()))
        {
            return retry("BUSINESS_CODE_MISMATCH", "OA 回调不支持该业务类型");
        }
        if (request.getEventKey().length() > 128)
        {
            return retry("EVENT_KEY_TOO_LONG", "审批事件键超过128个字符");
        }
        return null;
    }

    private Decision decision(String action)
    {
        return switch (action.trim().toUpperCase(Locale.ROOT))
        {
            case "APPROVE" -> new Decision("APPROVED", "APPROVED");
            case "RETURN" -> new Decision("RETURNED", "RETURNED");
            case "REJECT" -> new Decision("REJECTED", "REJECTED");
            case "WITHDRAW" -> new Decision("WITHDRAWN", "CANCELLED");
            case "TERMINATE" -> new Decision("TERMINATED", "CANCELLED");
            default -> null;
        };
    }

    private String payloadTarget(String payload)
    {
        if (blank(payload)) return null;
        try
        {
            JsonNode value = objectMapper.readTree(payload).get("targetStatus");
            return value == null || !value.isTextual() ? null
                    : value.asText().trim().toUpperCase(Locale.ROOT);
        }
        catch (Exception exception)
        {
            return null;
        }
    }

    private ApprovalBusinessCallbackResponse stale(
            ApprovalBusinessCallbackRequest request, String code,
            String message)
    {
        return "APPROVE".equals(request.getAction().trim()
                .toUpperCase(Locale.ROOT))
                        ? response(false, true, code, message)
                        : accepted(code + "_IGNORED", message);
    }

    private ApprovalBusinessCallbackResponse accepted(String code,
            String message)
    {
        return response(true, false, code, message);
    }

    private ApprovalBusinessCallbackResponse retry(String code,
            String message)
    {
        return response(false, false, code, message);
    }

    private ApprovalBusinessCallbackResponse response(boolean accepted,
            boolean invalidated, String code, String message)
    {
        ApprovalBusinessCallbackResponse response =
                new ApprovalBusinessCallbackResponse();
        response.setAccepted(accepted);
        response.setInvalidated(invalidated);
        response.setCode(code);
        response.setMessage(message);
        return response;
    }

    private ApprovalBusinessActionValidationResponse actionResponse(
            boolean accepted, String code, String message)
    {
        ApprovalBusinessActionValidationResponse response =
                new ApprovalBusinessActionValidationResponse();
        response.setAccepted(accepted);
        response.setCode(code);
        response.setMessage(message);
        return response;
    }

    private boolean integral(JsonNode value)
    {
        return value != null && value.isIntegralNumber()
                && value.canConvertToLong();
    }

    private boolean blank(String value)
    {
        return value == null || value.isBlank();
    }

    private record Decision(String approvalStatus, String businessStatus) { }
}
