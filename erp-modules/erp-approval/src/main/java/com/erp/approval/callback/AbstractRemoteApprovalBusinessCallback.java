package com.erp.approval.callback;

import java.util.function.Function;
import com.erp.approval.api.domain.ApprovalBusinessCallbackRequest;
import com.erp.approval.api.domain.ApprovalBusinessCallbackResponse;
import com.erp.common.core.domain.R;

abstract class AbstractRemoteApprovalBusinessCallback
        implements ApprovalBusinessCallback
{
    protected ApprovalBusinessCallbackResult deliver(
            ApprovalBusinessCallbackRequest request,
            Function<ApprovalBusinessCallbackRequest,
                    R<ApprovalBusinessCallbackResponse>> remote)
    {
        R<ApprovalBusinessCallbackResponse> result = remote.apply(request);
        if (result == null || !R.isSuccess(result) || result.getData() == null)
        {
            return new ApprovalBusinessCallbackResult(false, false,
                    "REMOTE_REJECTED", result == null ? "回调无响应" : result.getMsg());
        }
        ApprovalBusinessCallbackResponse response = result.getData();
        boolean invalidated = Boolean.TRUE.equals(response.getInvalidated());
        boolean accepted = Boolean.TRUE.equals(response.getAccepted());
        return new ApprovalBusinessCallbackResult(accepted || invalidated,
                invalidated, response.getCode(), response.getMessage());
    }
}
