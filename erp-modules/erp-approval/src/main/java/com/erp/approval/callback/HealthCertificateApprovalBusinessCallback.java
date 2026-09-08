package com.erp.approval.callback;

import org.springframework.stereotype.Component;
import com.erp.approval.api.domain.ApprovalBusinessCallbackRequest;
import com.erp.approval.callback.client.SystemApprovalCallbackClient;
import com.erp.approval.constant.ApprovalBusinessCodes;
import com.erp.common.core.constant.SecurityConstants;

@Component
public class HealthCertificateApprovalBusinessCallback
        extends AbstractRemoteApprovalBusinessCallback
{
    private final SystemApprovalCallbackClient client;
    public HealthCertificateApprovalBusinessCallback(SystemApprovalCallbackClient client)
    {
        this.client = client;
    }
    @Override public String businessCode() { return ApprovalBusinessCodes.HR_HEALTH_CERTIFICATE; }
    @Override public ApprovalBusinessCallbackResult deliver(ApprovalBusinessCallbackRequest request)
    {
        return deliver(request, item -> client.callback(item, SecurityConstants.INNER));
    }
}
