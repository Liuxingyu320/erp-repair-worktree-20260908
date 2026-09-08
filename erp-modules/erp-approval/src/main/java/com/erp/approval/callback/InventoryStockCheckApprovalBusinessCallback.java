package com.erp.approval.callback;

import org.springframework.stereotype.Component;
import com.erp.approval.api.domain.ApprovalBusinessCallbackRequest;
import com.erp.approval.callback.client.InventoryApprovalCallbackClient;
import com.erp.approval.constant.ApprovalBusinessCodes;
import com.erp.common.core.constant.SecurityConstants;

@Component
public class InventoryStockCheckApprovalBusinessCallback
        extends AbstractRemoteApprovalBusinessCallback
{
    private final InventoryApprovalCallbackClient client;
    public InventoryStockCheckApprovalBusinessCallback(InventoryApprovalCallbackClient client)
    {
        this.client = client;
    }
    @Override public String businessCode() { return ApprovalBusinessCodes.INV_STOCK_CHECK; }
    @Override public ApprovalBusinessCallbackResult deliver(ApprovalBusinessCallbackRequest request)
    {
        return deliver(request, item -> client.callback(item, SecurityConstants.INNER));
    }
}
