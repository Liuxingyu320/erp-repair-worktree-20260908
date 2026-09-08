package com.erp.oa.service.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.approval.api.RemoteApprovalService;
import com.erp.approval.api.domain.ApprovalBusinessCallbackRequest;
import com.erp.approval.api.domain.ApprovalBusinessCallbackResponse;
import com.erp.approval.api.domain.ApprovalStartRequest;
import com.erp.approval.api.domain.ApprovalWithdrawRequest;
import com.erp.common.core.constant.CacheConstants;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.domain.R;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.redis.service.RedisService;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.oa.domain.OaPurchase;
import com.erp.oa.domain.OaPurchaseApprovalStartOutbox;
import com.erp.oa.mapper.OaDeptScopeMapper;
import com.erp.oa.mapper.OaPurchaseMapper;
import com.erp.oa.service.BusinessFeatureGate;
import com.erp.oa.service.IOaPurchaseService;
import com.erp.system.api.domain.SysUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class OaPurchaseServiceImpl implements IOaPurchaseService
{
    private static final String BUSINESS_CODE =
            OaPurchaseApprovalStartOutboxService.BUSINESS_CODE;
    private static final String STATUS_DRAFT = "draft";
    private static final String STATUS_SUBMITTING = "submitting";
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_APPROVED = "approved";
    private static final String STATUS_RETURNED = "returned";
    private static final String STATUS_REJECTED = "rejected";
    private static final String STATUS_WITHDRAWN = "withdrawn";
    private static final String STATUS_TERMINATED = "terminated";
    private static final String STATUS_CANCELLED = "cancelled";

    private final OaPurchaseMapper purchaseMapper;
    private final OaDeptScopeMapper deptScopeMapper;
    private final ShopScopeService shopScopeService;
    private final RedisService redisService;
    private final RemoteApprovalService approvalService;
    private final ObjectMapper objectMapper;
    private final BusinessFeatureGate businessFeatureGate;
    private final OaPurchaseApprovalStartOutboxService approvalStartOutboxService;
    private final OaPurchaseApprovalStartAfterCommitTrigger approvalStartTrigger;

    public OaPurchaseServiceImpl(OaPurchaseMapper purchaseMapper,
            OaDeptScopeMapper deptScopeMapper,
            ShopScopeService shopScopeService,
            RedisService redisService,
            RemoteApprovalService approvalService,
            ObjectMapper objectMapper,
            BusinessFeatureGate businessFeatureGate,
            OaPurchaseApprovalStartOutboxService approvalStartOutboxService,
            OaPurchaseApprovalStartAfterCommitTrigger approvalStartTrigger)
    {
        this.purchaseMapper = purchaseMapper;
        this.deptScopeMapper = deptScopeMapper;
        this.shopScopeService = shopScopeService;
        this.redisService = redisService;
        this.approvalService = approvalService;
        this.objectMapper = objectMapper;
        this.businessFeatureGate = businessFeatureGate;
        this.approvalStartOutboxService = approvalStartOutboxService;
        this.approvalStartTrigger = approvalStartTrigger;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaPurchase saveDraft(OaPurchase purchase, Long selectedShopDeptId)
    {
        if (purchase == null)
        {
            throw new ServiceException("采购申请不能为空");
        }
        if (purchase.getPurchaseId() == null)
        {
            Long shopDeptId = shopScopeService.resolveRequiredShopDept(
                    selectedShopDeptId);
            fillBaseFields(purchase, shopDeptId);
            purchase.setStatus(STATUS_DRAFT);
            purchase.setApprovalInstanceId(null);
            purchase.setApprovalRound(0);
            purchase.setRowVersion(0L);
            purchase.setLastApprovalEventKey(null);
            if (purchaseMapper.insertOaPurchase(purchase) != 1)
            {
                throw new ServiceException("采购申请保存失败");
            }
        }
        else
        {
            OaPurchase current = assertAndLockScopedPurchase(
                    purchase.getPurchaseId(), selectedShopDeptId);
            assertPurchaseOwner(current);
            if (!isEditable(current.getStatus()))
            {
                throw new ServiceException("仅草稿、退回或撤回状态可以修改");
            }
            requireRowVersion(purchase.getRowVersion(), current.getRowVersion());
            purchase.setShopDeptId(current.getShopDeptId());
            purchase.setStatus(current.getStatus());
            purchase.setApprovalInstanceId(current.getApprovalInstanceId());
            purchase.setApprovalRound(current.getApprovalRound());
            purchase.setLastApprovalEventKey(current.getLastApprovalEventKey());
            purchase.setRowVersion(current.getRowVersion());
            purchase.setUpdateBy(SecurityUtils.getUsername());
            updateOrThrow(purchase, "采购申请已变化，请刷新后重试");
            evict(purchase.getPurchaseId());
        }
        return purchaseMapper.selectOaPurchaseById(purchase.getPurchaseId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaPurchase submitPurchase(OaPurchase purchase,
            Long selectedShopDeptId)
    {
        if (purchase == null)
        {
            throw new ServiceException("采购申请不能为空");
        }
        if (purchase.getPurchaseId() != null)
        {
            OaPurchase current = assertAndLockScopedPurchase(
                    purchase.getPurchaseId(), selectedShopDeptId);
            assertPurchaseOwner(current);
            if (STATUS_PENDING.equals(current.getStatus())
                    && current.getApprovalInstanceId() != null)
            {
                return current;
            }
            if (STATUS_SUBMITTING.equals(current.getStatus()))
            {
                OaPurchaseApprovalStartOutbox existing =
                        approvalStartOutboxService.selectByPurchaseRound(
                                current.getPurchaseId(),
                                current.getApprovalRound());
                if (existing == null)
                {
                    throw new ServiceException(
                            "采购申请处于提交中，但审批发起记录缺失");
                }
                approvalStartTrigger.trigger(existing.getOutboxId());
                return current;
            }
        }

        businessFeatureGate.requireEnabled(BusinessFeatureGate.PURCHASE);
        OaPurchase saved = saveDraft(purchase, selectedShopDeptId);
        int nextRound = (saved.getApprovalRound() == null
                ? 0 : saved.getApprovalRound()) + 1;
        Long previousInstanceId = saved.getApprovalInstanceId();
        ApprovalStartRequest startRequest = buildApprovalStartRequest(saved,
                nextRound, previousInstanceId);
        Long currentVersion = saved.getRowVersion() == null
                ? 0L : saved.getRowVersion();
        if (purchaseMapper.markApprovalSubmitting(saved.getPurchaseId(),
                saved.getStatus(), currentVersion, nextRound,
                SecurityUtils.getUsername()) != 1)
        {
            throw new ServiceException("采购申请已变化，无法提交");
        }
        saved.setStatus(STATUS_SUBMITTING);
        saved.setApprovalInstanceId(null);
        saved.setApprovalRound(nextRound);
        saved.setLastApprovalEventKey(null);
        saved.setRowVersion(currentVersion + 1);
        OaPurchaseApprovalStartOutbox outbox = approvalStartOutboxService
                .enqueue(saved, startRequest, SecurityUtils.getUsername());
        approvalStartTrigger.trigger(outbox.getOutboxId());
        evict(saved.getPurchaseId());
        return purchaseMapper.selectOaPurchaseById(saved.getPurchaseId());
    }

    @Override
    public boolean isSubmissionEnabled()
    {
        return businessFeatureGate.isEnabled(BusinessFeatureGate.PURCHASE);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaPurchase closeRejectedPurchase(Long purchaseId,
            Long selectedShopDeptId)
    {
        OaPurchase purchase = assertAndLockScopedPurchase(purchaseId,
                selectedShopDeptId);
        assertPurchaseOwner(purchase);
        if (STATUS_CANCELLED.equals(purchase.getStatus()))
        {
            return purchase;
        }
        if (!STATUS_REJECTED.equals(purchase.getStatus()))
        {
            throw new ServiceException("只有已拒绝的采购申请可以关闭");
        }
        OaPurchase update = stateUpdate(purchase, STATUS_CANCELLED);
        updateOrThrow(update, "采购申请已变化，请刷新后重试");
        evict(purchaseId);
        return purchaseMapper.selectOaPurchaseById(purchaseId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaPurchase withdrawPurchase(Long purchaseId, String reason,
            Long selectedShopDeptId)
    {
        OaPurchase purchase = assertAndLockScopedPurchase(purchaseId,
                selectedShopDeptId);
        assertPurchaseOwner(purchase);
        if (STATUS_WITHDRAWN.equals(purchase.getStatus()))
        {
            return purchase;
        }
        if (!STATUS_PENDING.equals(purchase.getStatus())
                || purchase.getApprovalInstanceId() == null)
        {
            throw new ServiceException("只有审批中的采购申请可以撤回");
        }
        String normalizedReason = StringUtils.isBlank(reason)
                ? "申请人撤回" : reason.trim();
        if (normalizedReason.length() > 500)
        {
            throw new ServiceException("撤回原因不能超过500个字符");
        }
        ApprovalWithdrawRequest command = new ApprovalWithdrawRequest();
        command.setInstanceId(purchase.getApprovalInstanceId());
        command.setApplicantId(SecurityUtils.getUserId());
        command.setReason(normalizedReason);
        R<Boolean> result = approvalService.withdraw(command,
                SecurityConstants.INNER);
        if (result == null || !R.isSuccess(result)
                || !Boolean.TRUE.equals(result.getData()))
        {
            throw new ServiceException(result == null
                    ? "审批中心暂时不可用，撤回失败"
                    : StringUtils.defaultIfEmpty(result.getMsg(), "撤回失败"));
        }
        return purchaseMapper.selectOaPurchaseById(purchaseId);
    }

    @Override
    public OaPurchase getPurchaseDetail(Long purchaseId,
            Long selectedShopDeptId)
    {
        OaPurchase purchase = assertAndGetScopedPurchase(purchaseId,
                selectedShopDeptId);
        redisService.setCacheObject(CacheConstants.OA_PURCHASE_KEY + purchaseId,
                purchase, CacheConstants.EXPIRATION, TimeUnit.MINUTES);
        return purchase;
    }

    @Override
    public List<OaPurchase> selectMyPurchases(OaPurchase purchase,
            Long selectedShopDeptId)
    {
        purchase.setApplicantId(SecurityUtils.getUserId());
        shopScopeService.appendShopScope(purchase, selectedShopDeptId);
        return purchaseMapper.selectMyOaPurchaseList(purchase);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApprovalBusinessCallbackResponse applyApprovalCallback(
            ApprovalBusinessCallbackRequest request)
    {
        ApprovalBusinessCallbackResponse envelopeError = validateCallback(request);
        if (envelopeError != null)
        {
            return envelopeError;
        }
        Long purchaseId;
        try
        {
            purchaseId = Long.valueOf(request.getBusinessId());
        }
        catch (NumberFormatException exception)
        {
            return retry("INVALID_BUSINESS_ID", "采购业务ID不是有效数字");
        }

        OaPurchase purchase = purchaseMapper.selectOaPurchaseByIdForUpdate(
                purchaseId);
        if (purchase == null)
        {
            return stale(request, "BUSINESS_NOT_FOUND", "采购申请不存在");
        }
        if (request.getEventKey().equals(purchase.getLastApprovalEventKey()))
        {
            return accepted("IDEMPOTENT", "回调事件已处理");
        }
        if (STATUS_SUBMITTING.equals(purchase.getStatus())
                && purchase.getApprovalInstanceId() == null
                && Objects.equals(request.getBusinessRound(),
                        purchase.getApprovalRound()))
        {
            return retry("APPROVAL_LINK_PENDING",
                    "采购审批实例正在关联，请稍后重试回调");
        }
        if (!Objects.equals(request.getInstanceId(),
                purchase.getApprovalInstanceId())
                || !Objects.equals(request.getBusinessRound(),
                        purchase.getApprovalRound()))
        {
            return stale(request, "STALE_APPROVAL_EVENT",
                    "审批实例或业务轮次已不是当前轮次");
        }

        CallbackTarget target = callbackTarget(request.getAction());
        if (target == null)
        {
            return retry("UNSUPPORTED_ACTION", "不支持的采购审批动作");
        }
        String payloadTarget = payloadTargetStatus(request.getPayload());
        if (payloadTarget == null)
        {
            return retry("INVALID_PAYLOAD", "审批回调缺少有效目标状态");
        }
        if (!target.approvalStatus().equals(payloadTarget))
        {
            return retry("TARGET_STATUS_MISMATCH", "审批动作与目标状态不一致");
        }
        if (!STATUS_PENDING.equals(purchase.getStatus()))
        {
            return stale(request, "BUSINESS_STATUS_CHANGED",
                    "采购申请已不处于当前审批状态");
        }

        OaPurchase update = stateUpdate(purchase, target.businessStatus());
        update.setLastApprovalEventKey(request.getEventKey());
        update.setUpdateBy("approval");
        updateOrThrow(update, "采购审批回调并发冲突");
        evict(purchaseId);
        return accepted("ACCEPTED", "采购审批状态已更新");
    }

    private ApprovalStartRequest buildApprovalStartRequest(OaPurchase purchase,
            int round, Long previousInstanceId)
    {
        ApprovalStartRequest request = new ApprovalStartRequest();
        request.setBusinessCode(BUSINESS_CODE);
        request.setBusinessId(String.valueOf(purchase.getPurchaseId()));
        request.setBusinessRound(round);
        request.setApplicantId(purchase.getApplicantId());
        request.setApplicantName(purchase.getApplicantName());
        request.setApplicantDeptId(purchase.getApplicantDeptId());
        request.setApplicantDeptName(purchase.getApplicantDeptName());
        request.setAnchorDeptId(purchase.getShopDeptId());
        request.setAnchorDeptName(purchase.getShopDeptName());
        request.setPreviousInstanceId(previousInstanceId);
        request.setBusinessSubtype("PURCHASE");
        request.setIdempotencyKey(BUSINESS_CODE + ":"
                + purchase.getPurchaseId() + ":" + round);

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("purchaseId", purchase.getPurchaseId());
        variables.put("title", purchase.getTitle());
        variables.put("amount", purchase.getAmount());
        variables.put("reason", purchase.getReason());
        variables.put("shopDeptId", purchase.getShopDeptId());
        variables.put("shopDeptName", purchase.getShopDeptName());
        request.setVariables(variables);

        Map<String, String> route = new LinkedHashMap<>();
        route.put("businessId", String.valueOf(purchase.getPurchaseId()));
        route.put("purchaseId", String.valueOf(purchase.getPurchaseId()));
        route.put("todoType", "OA_PURCHASE_APPROVAL");
        route.put("desktopPath", "/oa/purchase");
        route.put("mobilePath", "/mobile/oa-purchase-approval");
        request.setRouteSnapshot(route);
        return request;
    }

    private ApprovalBusinessCallbackResponse validateCallback(
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
        if (!BUSINESS_CODE.equals(request.getBusinessCode()))
        {
            return retry("BUSINESS_CODE_MISMATCH",
                    "OA 回调入口不支持该业务类型");
        }
        if (request.getEventKey().length() > 128)
        {
            return retry("EVENT_KEY_TOO_LONG", "审批事件键超过128个字符");
        }
        return null;
    }

    private CallbackTarget callbackTarget(String action)
    {
        return switch (action.trim().toUpperCase(Locale.ROOT))
        {
            case "APPROVE" -> new CallbackTarget("APPROVED", STATUS_APPROVED);
            case "RETURN" -> new CallbackTarget("RETURNED", STATUS_RETURNED);
            case "REJECT" -> new CallbackTarget("REJECTED", STATUS_REJECTED);
            case "WITHDRAW" -> new CallbackTarget("WITHDRAWN", STATUS_WITHDRAWN);
            case "TERMINATE" -> new CallbackTarget("TERMINATED", STATUS_TERMINATED);
            default -> null;
        };
    }

    private String payloadTargetStatus(String payload)
    {
        if (StringUtils.isBlank(payload))
        {
            return null;
        }
        try
        {
            JsonNode value = objectMapper.readTree(payload).get("targetStatus");
            return value == null || !value.isTextual()
                    ? null : value.asText().trim().toUpperCase(Locale.ROOT);
        }
        catch (JsonProcessingException exception)
        {
            return null;
        }
    }

    private OaPurchase stateUpdate(OaPurchase current, String status)
    {
        OaPurchase update = new OaPurchase();
        update.setPurchaseId(current.getPurchaseId());
        update.setStatus(status);
        update.setRowVersion(current.getRowVersion());
        update.setUpdateBy(SecurityUtils.getUsername());
        return update;
    }

    private void updateOrThrow(OaPurchase purchase, String message)
    {
        if (purchase.getRowVersion() == null
                || purchaseMapper.updateOaPurchase(purchase) != 1)
        {
            throw new ServiceException(message);
        }
    }

    private void requireRowVersion(Long requested, Long current)
    {
        if (requested == null || !Objects.equals(requested, current))
        {
            throw new ServiceException("采购申请已变化，请刷新后重试");
        }
    }

    private boolean isEditable(String status)
    {
        return STATUS_DRAFT.equals(status) || STATUS_RETURNED.equals(status)
                || STATUS_WITHDRAWN.equals(status);
    }

    private void fillBaseFields(OaPurchase purchase, Long shopDeptId)
    {
        SysUser user = SecurityUtils.getLoginUser().getSysUser();
        purchase.setShopDeptId(shopDeptId);
        purchase.setApplicantId(user.getUserId());
        purchase.setApplicantName(user.getUserName());
        purchase.setApplicantDeptId(user.getDeptId());
        purchase.setCreateBy(SecurityUtils.getUsername());
    }

    private OaPurchase assertAndGetScopedPurchase(Long purchaseId,
            Long selectedShopDeptId)
    {
        return assertScopedPurchase(purchaseMapper.selectOaPurchaseById(
                purchaseId), selectedShopDeptId);
    }

    private OaPurchase assertAndLockScopedPurchase(Long purchaseId,
            Long selectedShopDeptId)
    {
        return assertScopedPurchase(purchaseMapper
                .selectOaPurchaseByIdForUpdate(purchaseId), selectedShopDeptId);
    }

    private OaPurchase assertScopedPurchase(OaPurchase purchase,
            Long selectedShopDeptId)
    {
        if (purchase == null)
        {
            throw new ServiceException("单据不存在");
        }
        if (SecurityUtils.isAdmin())
        {
            return purchase;
        }
        Long rootDeptId = shopScopeService.resolveRequiredShopDept(
                selectedShopDeptId);
        if (deptScopeMapper.countDeptInScope(rootDeptId,
                purchase.getShopDeptId()) <= 0)
        {
            throw new ServiceException("无权访问该店铺单据");
        }
        return purchase;
    }

    private void assertPurchaseOwner(OaPurchase purchase)
    {
        if (!Objects.equals(purchase.getApplicantId(), SecurityUtils.getUserId()))
        {
            throw new ServiceException("仅申请人可以操作采购申请");
        }
    }

    private void evict(Long purchaseId)
    {
        redisService.deleteObject(CacheConstants.OA_PURCHASE_KEY + purchaseId);
    }

    private ApprovalBusinessCallbackResponse accepted(String code,
            String message)
    {
        return response(true, false, code, message);
    }

    private ApprovalBusinessCallbackResponse retry(String code, String message)
    {
        return response(false, false, code, message);
    }

    private ApprovalBusinessCallbackResponse stale(
            ApprovalBusinessCallbackRequest request, String code,
            String message)
    {
        return "APPROVE".equals(request.getAction().trim()
                .toUpperCase(Locale.ROOT))
                        ? invalidated(code, message)
                        : accepted(code + "_IGNORED", message);
    }

    private ApprovalBusinessCallbackResponse invalidated(String code,
            String message)
    {
        return response(false, true, code, message);
    }

    private ApprovalBusinessCallbackResponse response(boolean accepted,
            boolean invalidated, String code, String message)
    {
        ApprovalBusinessCallbackResponse result =
                new ApprovalBusinessCallbackResponse();
        result.setAccepted(accepted);
        result.setInvalidated(invalidated);
        result.setCode(code);
        result.setMessage(message);
        return result;
    }

    private record CallbackTarget(String approvalStatus,
            String businessStatus) { }
}
