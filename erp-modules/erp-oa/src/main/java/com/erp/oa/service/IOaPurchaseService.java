package com.erp.oa.service;

import java.util.List;
import com.erp.approval.api.domain.ApprovalBusinessCallbackRequest;
import com.erp.approval.api.domain.ApprovalBusinessCallbackResponse;
import com.erp.oa.domain.OaPurchase;

public interface IOaPurchaseService
{
    OaPurchase saveDraft(OaPurchase purchase, Long selectedShopDeptId);

    OaPurchase submitPurchase(OaPurchase purchase, Long selectedShopDeptId);

    boolean isSubmissionEnabled();

    OaPurchase closeRejectedPurchase(Long purchaseId, Long selectedShopDeptId);

    OaPurchase withdrawPurchase(Long purchaseId, String reason,
            Long selectedShopDeptId);

    OaPurchase getPurchaseDetail(Long purchaseId, Long selectedShopDeptId);

    List<OaPurchase> selectMyPurchases(OaPurchase purchase, Long selectedShopDeptId);

    ApprovalBusinessCallbackResponse applyApprovalCallback(
            ApprovalBusinessCallbackRequest request);
}
