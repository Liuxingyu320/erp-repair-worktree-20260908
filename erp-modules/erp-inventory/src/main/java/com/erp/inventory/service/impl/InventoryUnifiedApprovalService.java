package com.erp.inventory.service.impl;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.erp.approval.api.RemoteApprovalService;
import com.erp.approval.api.domain.ApprovalBusinessCallbackRequest;
import com.erp.approval.api.domain.ApprovalBusinessCallbackResponse;
import com.erp.approval.api.domain.ApprovalInstanceSnapshot;
import com.erp.approval.api.domain.ApprovalStartRequest;
import com.erp.approval.api.domain.ApprovalWithdrawRequest;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.domain.R;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.constant.InvStatusConstants;
import com.erp.inventory.constant.InvTransferSourceConfirmStatus;
import com.erp.inventory.constant.InvTransferRevisionStatuses;
import com.erp.inventory.constant.InvTransferTypes;
import com.erp.inventory.domain.InvStockCheck;
import com.erp.inventory.domain.InvStockCheckDetail;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.InvTransferStatusLog;
import com.erp.inventory.domain.vo.InvStockCheckAdjustmentResult;
import com.erp.inventory.mapper.InvStockCheckDetailMapper;
import com.erp.inventory.mapper.InvStockCheckMapper;
import com.erp.inventory.mapper.InvTransferOrderMapper;
import com.erp.inventory.mapper.InvTransferStatusLogMapper;
import com.erp.inventory.service.BusinessFeatureGate;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.model.LoginUser;

/**
 * Inventory adapter for the native approval runtime.
 *
 * <p>The explicit engine marker is required because both legacy inventory
 * engines already use {@code approval_instance_id}; IDs from two engines are
 * therefore never inferred by shape or value.</p>
 */
@Service
public class InventoryUnifiedApprovalService extends InvBaseService
{
    public static final String ENGINE_NATIVE = "NATIVE";
    public static final String ENGINE_LEGACY = "LEGACY";
    public static final String STOCK_CHECK = "INV_STOCK_CHECK";
    public static final String TRANSFER = "INV_TRANSFER";

    private final RemoteApprovalService approvalService;
    private final BusinessFeatureGate featureGate;
    private final InvStockCheckMapper stockCheckMapper;
    private final InvStockCheckDetailMapper stockCheckDetailMapper;
    private final InvStockCheckAdjustmentService stockCheckAdjustmentService;
    private final InvTransferOrderMapper transferOrderMapper;
    private final InvTransferStatusLogMapper transferStatusLogMapper;
    private final InvTransferReservationService transferReservationService;
    private final InvTransferRevisionService transferRevisionService;

    public InventoryUnifiedApprovalService(RemoteApprovalService approvalService,
            BusinessFeatureGate featureGate,
            InvStockCheckMapper stockCheckMapper,
            InvStockCheckDetailMapper stockCheckDetailMapper,
            InvStockCheckAdjustmentService stockCheckAdjustmentService,
            InvTransferOrderMapper transferOrderMapper,
            InvTransferStatusLogMapper transferStatusLogMapper,
            InvTransferReservationService transferReservationService,
            InvTransferRevisionService transferRevisionService)
    {
        this.approvalService = approvalService;
        this.featureGate = featureGate;
        this.stockCheckMapper = stockCheckMapper;
        this.stockCheckDetailMapper = stockCheckDetailMapper;
        this.stockCheckAdjustmentService = stockCheckAdjustmentService;
        this.transferOrderMapper = transferOrderMapper;
        this.transferStatusLogMapper = transferStatusLogMapper;
        this.transferReservationService = transferReservationService;
        this.transferRevisionService = transferRevisionService;
    }

    public boolean useNativeStockCheck(InvStockCheck check)
    {
        return isNative(check == null ? null : check.getApprovalEngine())
                || featureGate.isEnabled(
                        BusinessFeatureGate.STOCK_CHECK_NATIVE_APPROVAL);
    }

    public boolean useNativeTransfer(InvTransferOrder transfer)
    {
        return isNative(transfer == null ? null : transfer.getApprovalEngine())
                || featureGate.isEnabled(
                        BusinessFeatureGate.TRANSFER_NATIVE_APPROVAL);
    }

    public ApprovalStartRequest buildStockCheckStartRequest(
            InvStockCheck check, List<InvStockCheckDetail> details,
            int round)
    {
        ApprovalStartRequest request = baseRequest(STOCK_CHECK,
                String.valueOf(check.getCheckId()), round,
                isNative(check.getApprovalEngine())
                        ? check.getApprovalInstanceId() : null,
                check.getShopDeptId(), "DIFFERENCE");
        request.setAnchorDeptName(firstNonBlank(check.getShopDeptName(),
                deptScopeMapper.selectDeptNameById(check.getShopDeptId())));

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("checkId", check.getCheckId());
        variables.put("checkNo", check.getCheckNo());
        variables.put("shopDeptId", check.getShopDeptId());
        variables.put("warehouseId", check.getWarehouseId());
        variables.put("detailCount", details == null ? 0 : details.size());
        variables.put("profitItemCount", check.getProfitItemCount());
        variables.put("lossItemCount", check.getLossItemCount());
        variables.put("totalDiffQuantity", check.getTotalDiffQuantity());
        request.setVariables(variables);

        Map<String, String> route = new LinkedHashMap<>();
        route.put("businessId", String.valueOf(check.getCheckId()));
        route.put("checkId", String.valueOf(check.getCheckId()));
        route.put("todoType", "INV_STOCK_CHECK_APPROVAL");
        String detailPath = "/inventory/stock-check/" + check.getCheckId();
        route.put("desktopPath", detailPath);
        route.put("mobilePath", detailPath);
        request.setRouteSnapshot(route);
        return request;
    }

    public ApprovalStartRequest buildTransferStartRequest(
            InvTransferOrder transfer, int round)
    {
        Long anchorDeptId = InvTransferTypes.STORE_RETURN.equals(
                transfer.getTransferType())
                        ? transfer.getFromDeptId() : transfer.getToDeptId();
        ApprovalStartRequest request = baseRequest(TRANSFER,
                String.valueOf(transfer.getTransferId()), round,
                isNative(transfer.getApprovalEngine())
                        ? transfer.getApprovalInstanceId() : null,
                anchorDeptId, transfer.getTransferType());
        request.setAnchorDeptName(firstNonBlank(
                InvTransferTypes.STORE_RETURN.equals(transfer.getTransferType())
                        ? transfer.getFromDeptName() : transfer.getToDeptName(),
                deptScopeMapper.selectDeptNameById(anchorDeptId)));

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("transferId", transfer.getTransferId());
        variables.put("orderNo", transfer.getOrderNo());
        variables.put("transferType", transfer.getTransferType());
        variables.put("fromDeptId", transfer.getFromDeptId());
        variables.put("toDeptId", transfer.getToDeptId());
        variables.put("sourceDeptId", transfer.getFromDeptId());
        variables.put("targetDeptId", transfer.getToDeptId());
        variables.put("totalQuantity", transfer.getTotalQuantity());
        variables.put("amount", transfer.getTotalAmount());
        request.setVariables(variables);

        Map<String, String> route = new LinkedHashMap<>();
        route.put("businessId", String.valueOf(transfer.getTransferId()));
        route.put("transferId", String.valueOf(transfer.getTransferId()));
        route.put("todoType", "INV_TRANSFER_APPROVAL");
        route.put("desktopPath", "/inventory/transfer");
        route.put("mobilePath", "/mobile/inventory-transfer");
        request.setRouteSnapshot(route);
        return request;
    }

    /**
     * Submit an applicant withdrawal to the approval runtime. The business row
     * deliberately remains in its approval state until the WITHDRAWN callback
     * is consumed, so a remote success can never leave a local-only terminal
     * state beside a RUNNING approval instance.
     */
    public void withdraw(String businessCode, Long businessId,
            Integer businessRound, Long instanceId, String reason)
    {
        if (businessId == null || businessRound == null || instanceId == null)
        {
            throw new ServiceException("统一审批业务关联不完整，无法撤回");
        }
        Long applicantId = SecurityUtils.getUserId();
        if (applicantId == null)
        {
            throw new ServiceException("无法识别审批申请人");
        }
        R<ApprovalInstanceSnapshot> snapshotResult = approvalService.getInstance(
                instanceId, SecurityConstants.INNER);
        ApprovalInstanceSnapshot snapshot = snapshotResult == null
                ? null : snapshotResult.getData();
        if (snapshotResult == null || !R.isSuccess(snapshotResult)
                || snapshot == null)
        {
            throw new ServiceException(snapshotResult == null
                    ? "审批中心暂时不可用，无法核对撤回申请"
                    : firstNonBlank(snapshotResult.getMsg(),
                            "无法核对统一审批实例"));
        }
        if (!Objects.equals(instanceId, snapshot.getInstanceId())
                || !Objects.equals(businessCode, snapshot.getBusinessCode())
                || !Objects.equals(String.valueOf(businessId),
                        snapshot.getBusinessId())
                || !Objects.equals(businessRound,
                        snapshot.getBusinessRound()))
        {
            throw new ServiceException("统一审批实例与当前业务单据不匹配");
        }
        if (!Objects.equals(applicantId, snapshot.getApplicantUserId()))
        {
            throw new ServiceException("只有当前审批申请人可以撤回");
        }
        String snapshotStatus = upper(snapshot.getStatus());
        if (!"RUNNING".equals(snapshotStatus)
                && !"WITHDRAWING".equals(snapshotStatus))
        {
            throw new ServiceException("当前审批状态不允许撤回");
        }
        ApprovalWithdrawRequest request = new ApprovalWithdrawRequest();
        request.setInstanceId(instanceId);
        request.setApplicantId(applicantId);
        request.setReason(firstNonBlank(reason, "申请人撤回"));
        R<Boolean> result = approvalService.withdraw(request,
                SecurityConstants.INNER);
        if (result == null || !R.isSuccess(result)
                || !Boolean.TRUE.equals(result.getData()))
        {
            throw new ServiceException(result == null
                    ? "审批中心暂时不可用，撤回请求未提交"
                    : firstNonBlank(result.getMsg(), "撤回审批失败"));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public ApprovalBusinessCallbackResponse applyCallback(
            ApprovalBusinessCallbackRequest request)
    {
        ApprovalBusinessCallbackResponse invalid = validateEnvelope(request);
        if (invalid != null)
        {
            return invalid;
        }
        return switch (request.getBusinessCode())
        {
            case STOCK_CHECK -> applyStockCheckCallback(request);
            case TRANSFER -> applyTransferCallback(request);
            default -> invalidated("BUSINESS_CODE_MISMATCH",
                    "库存回调入口不支持该业务类型");
        };
    }

    private ApprovalBusinessCallbackResponse applyStockCheckCallback(
            ApprovalBusinessCallbackRequest request)
    {
        Long checkId = parseBusinessId(request, "盘点");
        if (checkId == null)
        {
            return retry("INVALID_BUSINESS_ID", "盘点业务ID不是有效数字");
        }
        InvStockCheck check = stockCheckMapper
                .selectInvStockCheckByIdForUpdate(checkId);
        if (check == null)
        {
            return stale(request, "BUSINESS_NOT_FOUND", "盘点单不存在");
        }
        if (request.getEventKey().equals(check.getLastApprovalEventKey()))
        {
            return accepted("IDEMPOTENT", "回调事件已处理");
        }
        /*
         * 无人工节点的审批可能立刻回调。当前原生轮次还未关联返回的
         * instanceId 时不是过期事件，应请求审批中心稍后重投。
         */
        if (isNative(check.getApprovalEngine())
                && check.getApprovalInstanceId() == null
                && Objects.equals(request.getBusinessRound(),
                        check.getApprovalRound())
                && InvStatusConstants.PENDING_APPROVAL.equals(
                        check.getStatus()))
        {
            return retry("APPROVAL_START_LINK_PENDING",
                    "盘点审批实例正在本地关联，请重试回调");
        }
        if (!isNative(check.getApprovalEngine())
                || !Objects.equals(request.getInstanceId(),
                        check.getApprovalInstanceId())
                || !Objects.equals(request.getBusinessRound(),
                        check.getApprovalRound()))
        {
            return stale(request, "STALE_APPROVAL_EVENT",
                    "审批实例或盘点轮次已不是当前原生审批轮次");
        }
        CallbackPayload payload = callbackPayload(request);
        if (payload == null)
        {
            return retry("INVALID_PAYLOAD", "审批回调载荷无效");
        }
        if (!InvStatusConstants.PENDING_APPROVAL.equals(check.getStatus()))
        {
            return stale(request, "BUSINESS_STATUS_CHANGED",
                    "盘点单已不处于当前审批状态");
        }

        Date now = new Date();
        InvStockCheck update = new InvStockCheck();
        update.setCheckId(checkId);
        update.setLastApprovalEventKey(request.getEventKey());
        update.setUpdateBy(payload.operatorName());
        switch (payload.targetStatus())
        {
            case "APPROVED" ->
            {
                List<InvStockCheckDetail> details = stockCheckDetailMapper
                        .selectInvStockCheckDetailByCheckIdForUpdate(checkId);
                InvStockCheckAdjustmentResult result =
                        stockCheckAdjustmentService.evaluate(check, details,
                                true, payload.operatorName());
                if (result.hasSnapshotChanges())
                {
                    update.setStatus(InvStatusConstants.INVALIDATED);
                    update.setLastInvalidReason("库存快照已变化，需要重新盘点");
                    update.setLastInvalidDetailSnapshot(
                            JSON.toJSONString(result.getSnapshotChanges()));
                    update.setLastInvalidatedTime(now);
                    updateStockCheck(update);
                    return invalidated("STOCK_SNAPSHOT_CHANGED",
                            "库存快照已变化，盘点审批已失效");
                }
                update.setStatus(InvStatusConstants.COMPLETED);
                update.setApprovedUserId(payload.operatorId());
                update.setApprovedBy(payload.operatorName());
                update.setApprovedTime(now);
            }
            case "RETURNED" ->
            {
                update.setStatus(InvStatusConstants.RETURNED);
                update.setLastRejectReason(firstNonBlank(payload.reason(),
                        "审批退回，请修改后重新提交"));
                update.setLastRejectedUserId(payload.operatorId());
                update.setLastRejectedBy(payload.operatorName());
                update.setLastRejectedTime(now);
            }
            case "REJECTED" ->
            {
                update.setStatus(InvStatusConstants.REJECTED);
                update.setLastRejectReason(firstNonBlank(payload.reason(),
                        "审批已拒绝"));
                update.setLastRejectedUserId(payload.operatorId());
                update.setLastRejectedBy(payload.operatorName());
                update.setLastRejectedTime(now);
            }
            case "WITHDRAWN" -> update.setStatus(InvStatusConstants.DRAFT);
            case "TERMINATED" -> update.setStatus(InvStatusConstants.CANCELLED);
            default ->
            {
                return retry("UNSUPPORTED_TARGET_STATUS",
                        "不支持的盘点审批目标状态");
            }
        }
        updateStockCheck(update);
        return accepted("ACCEPTED", "盘点审批结果已应用");
    }

    private ApprovalBusinessCallbackResponse applyTransferCallback(
            ApprovalBusinessCallbackRequest request)
    {
        Long transferId = parseBusinessId(request, "调拨");
        if (transferId == null)
        {
            return retry("INVALID_BUSINESS_ID", "调拨业务ID不是有效数字");
        }
        InvTransferOrder transfer = transferOrderMapper
                .selectInvTransferOrderByIdForUpdate(transferId);
        if (transfer == null)
        {
            return stale(request, "BUSINESS_NOT_FOUND", "调拨单不存在");
        }
        if (request.getEventKey().equals(transfer.getLastApprovalEventKey()))
        {
            return accepted("IDEMPOTENT", "回调事件已处理");
        }
        /*
         * A no-human-node route can enqueue its final callback immediately.
         * While the start saga is still attaching the returned instance, this
         * exact native round is not stale: ask the approval outbox to retry so
         * it cannot invalidate a legitimate instance during local recovery.
         */
        if (isNative(transfer.getApprovalEngine())
                && transfer.getApprovalInstanceId() == null
                && Objects.equals(request.getBusinessRound(),
                        transfer.getApprovalRound())
                && InvStatusConstants.SUBMITTED.equals(transfer.getStatus()))
        {
            return retry("APPROVAL_START_LINK_PENDING",
                    "调拨审批实例正在本地关联，请重试回调");
        }
        if (!isNative(transfer.getApprovalEngine())
                || !Objects.equals(request.getInstanceId(),
                        transfer.getApprovalInstanceId())
                || !Objects.equals(request.getBusinessRound(),
                        transfer.getApprovalRound()))
        {
            return stale(request, "STALE_APPROVAL_EVENT",
                    "审批实例或调拨轮次已不是当前原生审批轮次");
        }
        CallbackPayload payload = callbackPayload(request);
        if (payload == null)
        {
            return retry("INVALID_PAYLOAD", "审批回调载荷无效");
        }
        if (!InvStatusConstants.SUBMITTED.equals(transfer.getStatus()))
        {
            return stale(request, "BUSINESS_STATUS_CHANGED",
                    "调拨单已不处于当前审批状态");
        }

        String targetBusinessStatus = switch (payload.targetStatus())
        {
            case "APPROVED" -> InvStatusConstants.APPROVED;
            case "RETURNED", "WITHDRAWN" -> InvStatusConstants.DRAFT;
            case "REJECTED" -> InvStatusConstants.REJECTED;
            case "TERMINATED" -> InvStatusConstants.CLOSED;
            default -> null;
        };
        if (targetBusinessStatus == null)
        {
            return retry("UNSUPPORTED_TARGET_STATUS",
                    "不支持的调拨审批目标状态");
        }

        if (!InvStatusConstants.APPROVED.equals(targetBusinessStatus))
        {
            transferReservationService.releaseAllRemaining(transfer,
                    payload.operatorName());
        }

        String revisionStatus = switch (payload.targetStatus())
        {
            case "APPROVED" -> InvTransferRevisionStatuses.APPROVED;
            case "RETURNED" -> InvTransferRevisionStatuses.RETURNED;
            case "WITHDRAWN" -> InvTransferRevisionStatuses.WITHDRAWN;
            case "REJECTED" -> InvTransferRevisionStatuses.REJECTED;
            case "TERMINATED" -> InvTransferRevisionStatuses.CLOSED;
            default -> throw new ServiceException(
                    "调拨审批版本结果无法映射");
        };
        transferRevisionService.recordApprovalOutcome(transfer,
                revisionStatus, targetBusinessStatus,
                request.getAction(), payload.reason(),
                payload.operatorId(), payload.operatorName(),
                request.getInstanceId());

        InvTransferOrder update = new InvTransferOrder();
        update.setTransferId(transferId);
        update.setStatus(targetBusinessStatus);
        update.setLastApprovalEventKey(request.getEventKey());
        update.setUpdateBy(payload.operatorName());
        if (InvStatusConstants.APPROVED.equals(targetBusinessStatus))
        {
            update.setApprovedTime(new Date());
            if (InvTransferTypes.requiresSourceConfirmation(
                    transfer.getTransferType(),
                    transfer.getSourceBusinessType()))
            {
                update.setSourceConfirmStatus(
                        InvTransferSourceConfirmStatus.PENDING);
            }
        }
        else if (InvStatusConstants.DRAFT.equals(targetBusinessStatus)
                && InvTransferTypes.requiresSourceConfirmation(
                        transfer.getTransferType(),
                        transfer.getSourceBusinessType()))
        {
            update.setSourceConfirmStatus(
                    InvTransferSourceConfirmStatus.NOT_STARTED);
        }
        else if (InvStatusConstants.CLOSED.equals(targetBusinessStatus))
        {
            update.setArchivedTime(new Date());
            update.setCloseReason(firstNonBlank(payload.reason(),
                    "审批被管理员终止"));
        }
        if (transferOrderMapper.updateInvTransferOrder(update) != 1)
        {
            throw new ServiceException("调拨审批回调更新失败");
        }
        writeTransferStatusLog(transfer, targetBusinessStatus, request,
                payload);
        return accepted("ACCEPTED", "调拨审批结果已应用");
    }

    private ApprovalStartRequest baseRequest(String businessCode,
            String businessId, int round, Long previousInstanceId,
            Long anchorDeptId, String subtype)
    {
        LoginUser login = SecurityUtils.getLoginUser();
        SysUser user = login == null ? null : login.getSysUser();
        Long applicantId = SecurityUtils.getUserId();
        if (applicantId == null)
        {
            throw new ServiceException("无法识别审批申请人");
        }
        ApprovalStartRequest request = new ApprovalStartRequest();
        request.setBusinessCode(businessCode);
        request.setBusinessId(businessId);
        request.setBusinessRound(round);
        request.setApplicantId(applicantId);
        request.setApplicantName(user == null
                ? SecurityUtils.getUsername()
                : firstNonBlank(user.getNickName(), user.getUserName()));
        request.setApplicantDeptId(user == null ? null : user.getDeptId());
        request.setApplicantDeptName(user == null || user.getDept() == null
                ? null : user.getDept().getDeptName());
        request.setAnchorDeptId(anchorDeptId);
        request.setPreviousInstanceId(previousInstanceId);
        request.setBusinessSubtype(firstNonBlank(subtype, "ALL"));
        request.setIdempotencyKey(businessCode + ":" + businessId + ":"
                + round);
        return request;
    }

    private ApprovalBusinessCallbackResponse validateEnvelope(
            ApprovalBusinessCallbackRequest request)
    {
        if (request == null || StringUtils.isBlank(request.getEventKey())
                || request.getInstanceId() == null
                || StringUtils.isBlank(request.getBusinessCode())
                || StringUtils.isBlank(request.getBusinessId())
                || request.getBusinessRound() == null
                || StringUtils.isBlank(request.getAction()))
        {
            return retry("INVALID_CALLBACK", "审批回调字段不完整");
        }
        if (request.getEventKey().length() > 128)
        {
            return retry("EVENT_KEY_TOO_LONG", "审批事件键超过128个字符");
        }
        return null;
    }

    private CallbackPayload callbackPayload(
            ApprovalBusinessCallbackRequest request)
    {
        try
        {
            JSONObject value = JSON.parseObject(request.getPayload());
            String target = value == null ? null
                    : upper(value.getString("targetStatus"));
            String expected = expectedTarget(request.getAction());
            if (target == null || expected == null || !target.equals(expected))
            {
                return null;
            }
            return new CallbackPayload(target,
                    value.getLong("operatorId"),
                    firstNonBlank(value.getString("operatorName"), "approval"),
                    value.getString("reason"));
        }
        catch (RuntimeException exception)
        {
            return null;
        }
    }

    private String expectedTarget(String action)
    {
        if (action == null)
        {
            return null;
        }
        return switch (action.trim().toUpperCase(Locale.ROOT))
        {
            case "APPROVE" -> "APPROVED";
            case "RETURN" -> "RETURNED";
            case "REJECT" -> "REJECTED";
            case "WITHDRAW" -> "WITHDRAWN";
            case "TERMINATE" -> "TERMINATED";
            default -> null;
        };
    }

    private void updateStockCheck(InvStockCheck update)
    {
        if (stockCheckMapper.updateInvStockCheck(update) != 1)
        {
            throw new ServiceException("盘点审批回调更新失败");
        }
    }

    private void writeTransferStatusLog(InvTransferOrder transfer,
            String targetStatus, ApprovalBusinessCallbackRequest request,
            CallbackPayload payload)
    {
        InvTransferStatusLog log = new InvTransferStatusLog();
        log.setTransferId(transfer.getTransferId());
        log.setFromStatus(transfer.getStatus());
        log.setToStatus(targetStatus);
        log.setAction(request.getAction().toLowerCase(Locale.ROOT));
        log.setOperatorId(payload.operatorId());
        log.setOperatorName(payload.operatorName());
        log.setReason(firstNonBlank(payload.reason(), "统一审批结果回调"));
        transferStatusLogMapper.insertLog(log);
    }

    private ApprovalBusinessCallbackResponse stale(
            ApprovalBusinessCallbackRequest request, String code,
            String message)
    {
        return "APPROVE".equals(upper(request.getAction()))
                ? invalidated(code, message)
                : accepted(code + "_IGNORED", message);
    }

    private Long parseBusinessId(ApprovalBusinessCallbackRequest request,
            String businessName)
    {
        try
        {
            return Long.valueOf(request.getBusinessId());
        }
        catch (NumberFormatException exception)
        {
            return null;
        }
    }

    private static boolean isNative(String engine)
    {
        return ENGINE_NATIVE.equalsIgnoreCase(engine == null ? "" : engine);
    }

    private static String upper(String value)
    {
        return StringUtils.isBlank(value) ? null
                : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String firstNonBlank(String value, String fallback)
    {
        return StringUtils.isBlank(value) ? fallback : value.trim();
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

    private ApprovalBusinessCallbackResponse invalidated(String code,
            String message)
    {
        return response(false, true, code, message);
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

    private record CallbackPayload(String targetStatus, Long operatorId,
            String operatorName, String reason) {}
}
