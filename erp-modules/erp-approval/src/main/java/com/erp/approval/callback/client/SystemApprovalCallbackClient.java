package com.erp.approval.callback.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import com.erp.approval.api.domain.ApprovalBusinessCallbackRequest;
import com.erp.approval.api.domain.ApprovalBusinessCallbackResponse;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.constant.ServiceNameConstants;
import com.erp.common.core.domain.R;

@FeignClient(contextId = "systemApprovalCallbackClient",
        value = ServiceNameConstants.SYSTEM_SERVICE)
public interface SystemApprovalCallbackClient
{
    @PostMapping("/inner/approval/callback")
    R<ApprovalBusinessCallbackResponse> callback(
            @RequestBody ApprovalBusinessCallbackRequest request,
            @RequestHeader(SecurityConstants.FROM_SOURCE) String source);
}
