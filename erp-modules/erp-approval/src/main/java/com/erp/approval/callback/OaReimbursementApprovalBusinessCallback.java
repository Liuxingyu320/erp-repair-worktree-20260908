package com.erp.approval.callback;

import org.springframework.stereotype.Component;
import com.erp.approval.api.domain.ApprovalBusinessCallbackRequest;
import com.erp.approval.callback.client.OaApprovalCallbackClient;
import com.erp.approval.constant.ApprovalBusinessCodes;
import com.erp.common.core.constant.SecurityConstants;

@Component
public class OaReimbursementApprovalBusinessCallback
        extends AbstractRemoteApprovalBusinessCallback
{
    private final OaApprovalCallbackClient client;

    public OaReimbursementApprovalBusinessCallback(
            OaApprovalCallbackClient client)
    {
        this.client = client;
    }

    @Override
    public String businessCode()
    {
        return ApprovalBusinessCodes.OA_REIMBURSEMENT;
    }

    @Override
    public ApprovalBusinessCallbackResult deliver(
            ApprovalBusinessCallbackRequest request)
    {
        return deliver(request,
                item -> client.callback(item, SecurityConstants.INNER));
    }
}
