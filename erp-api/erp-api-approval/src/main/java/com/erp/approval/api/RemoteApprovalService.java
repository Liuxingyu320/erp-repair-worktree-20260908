package com.erp.approval.api;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import com.erp.approval.api.domain.ApprovalInstanceSnapshot;
import com.erp.approval.api.domain.ApprovalServiceStatus;
import com.erp.approval.api.domain.ApprovalStartRequest;
import com.erp.approval.api.domain.ApprovalStartResponse;
import com.erp.approval.api.domain.ApprovalWithdrawRequest;
import com.erp.approval.api.factory.RemoteApprovalFallbackFactory;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.constant.ServiceNameConstants;
import com.erp.common.core.domain.R;

/**
 * Unified approval internal service contract.
 *
 * <p>The bootstrap implementation is deliberately fail-closed until the
 * native runtime and schema are enabled.</p>
 */
@FeignClient(contextId = "remoteApprovalService",
        value = ServiceNameConstants.APPROVAL_SERVICE,
        fallbackFactory = RemoteApprovalFallbackFactory.class)
public interface RemoteApprovalService
{
    @PostMapping("/inner/approval/instances/start")
    R<ApprovalStartResponse> start(@RequestBody ApprovalStartRequest request,
            @RequestHeader(SecurityConstants.FROM_SOURCE) String source);

    @PostMapping("/inner/approval/instances/withdraw")
    R<Boolean> withdraw(@RequestBody ApprovalWithdrawRequest request,
            @RequestHeader(SecurityConstants.FROM_SOURCE) String source);

    @GetMapping("/inner/approval/instances/{instanceId}")
    R<ApprovalInstanceSnapshot> getInstance(
            @PathVariable("instanceId") Long instanceId,
            @RequestHeader(SecurityConstants.FROM_SOURCE) String source);

    @GetMapping("/inner/approval/instances/{instanceId}/participants/{userId}")
    R<Boolean> canAccessInstance(
            @PathVariable("instanceId") Long instanceId,
            @PathVariable("userId") Long userId,
            @RequestHeader(SecurityConstants.FROM_SOURCE) String source);

    @GetMapping("/inner/approval/status")
    R<ApprovalServiceStatus> getServiceStatus(
            @RequestHeader(SecurityConstants.FROM_SOURCE) String source);
}
