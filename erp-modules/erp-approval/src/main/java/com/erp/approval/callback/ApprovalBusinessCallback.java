package com.erp.approval.callback;

import com.erp.approval.api.domain.ApprovalBusinessCallbackRequest;

/** Extension point used by native OA, inventory and system migrations. */
public interface ApprovalBusinessCallback
{
    String businessCode();

    ApprovalBusinessCallbackResult deliver(ApprovalBusinessCallbackRequest request);
}
