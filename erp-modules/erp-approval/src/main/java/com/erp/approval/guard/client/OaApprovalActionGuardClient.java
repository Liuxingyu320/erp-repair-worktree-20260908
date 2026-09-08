package com.erp.approval.guard.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import com.erp.approval.api.domain.ApprovalBusinessActionValidationRequest;
import com.erp.approval.api.domain.ApprovalBusinessActionValidationResponse;
import com.erp.approval.api.constant.ApprovalApiPaths;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.constant.ServiceNameConstants;
import com.erp.common.core.domain.R;

@FeignClient(contextId = "oaApprovalActionGuardClient",
        value = ServiceNameConstants.OA_SERVICE)
public interface OaApprovalActionGuardClient
{
    @PostMapping(ApprovalApiPaths.VALIDATE_ACTION_FULL)
    R<ApprovalBusinessActionValidationResponse> validateAction(
            @RequestBody ApprovalBusinessActionValidationRequest request,
            @RequestHeader(SecurityConstants.FROM_SOURCE) String source);
}
