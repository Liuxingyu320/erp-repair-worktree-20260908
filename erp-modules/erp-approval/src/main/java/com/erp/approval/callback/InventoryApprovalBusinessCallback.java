package com.erp.approval.callback;

import org.springframework.stereotype.Component;
import com.erp.approval.api.domain.ApprovalBusinessCallbackRequest;
import com.erp.approval.callback.client.InventoryApprovalCallbackClient;
import com.erp.approval.constant.ApprovalBusinessCodes;
import com.erp.common.core.constant.SecurityConstants;

/** One adapter for each inventory business code keeps callback routing explicit. */
@Component
public class InventoryApprovalBusinessCallback
        extends AbstractRemoteApprovalBusinessCallback
{
    private final InventoryApprovalCallbackClient client;
    public InventoryApprovalBusinessCallback(InventoryApprovalCallbackClient client)
    {
        this.client = client;
    }
    @Override public String businessCode() { return ApprovalBusinessCodes.INV_TRANSFER; }
    @Override public ApprovalBusinessCallbackResult deliver(ApprovalBusinessCallbackRequest request)
    {
        return deliver(request, item -> client.callback(item, SecurityConstants.INNER));
    }
}
