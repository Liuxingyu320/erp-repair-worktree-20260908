package com.erp.approval.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.erp.approval.api.domain.ApprovalInstanceSnapshot;
import com.erp.approval.api.domain.ApprovalServiceStatus;
import com.erp.approval.api.domain.ApprovalStartRequest;
import com.erp.approval.api.domain.ApprovalStartResponse;
import com.erp.approval.api.domain.ApprovalWithdrawRequest;
import com.erp.common.core.constant.ServiceNameConstants;
import com.erp.common.core.domain.R;
import com.erp.common.security.annotation.InnerAuth;
import com.erp.approval.service.ApprovalRuntimeService;
import com.erp.approval.service.ApprovalMonitorService;

/**
 * Fail-closed internal API used by migrated business services.
 */
@RestController
@RequestMapping("/inner/approval")
public class ApprovalInternalController
{
    private final ApprovalRuntimeService runtimeService;
    private final ApprovalMonitorService monitorService;

    public ApprovalInternalController(ApprovalRuntimeService runtimeService,
            ApprovalMonitorService monitorService)
    {
        this.runtimeService = runtimeService;
        this.monitorService = monitorService;
    }

    @InnerAuth
    @PostMapping("/instances/start")
    public R<ApprovalStartResponse> start(
            @Valid @RequestBody ApprovalStartRequest request)
    {
        return R.ok(runtimeService.start(request));
    }

    @InnerAuth
    @PostMapping("/instances/withdraw")
    public R<Boolean> withdraw(
            @Valid @RequestBody ApprovalWithdrawRequest request)
    {
        return R.ok(runtimeService.withdraw(request));
    }

    @InnerAuth
    @GetMapping("/instances/{instanceId}")
    public R<ApprovalInstanceSnapshot> getInstance(
            @PathVariable Long instanceId)
    {
        return R.ok(runtimeService.getSnapshot(instanceId));
    }

    @InnerAuth
    @GetMapping("/instances/{instanceId}/participants/{userId}")
    public R<Boolean> canAccessInstance(@PathVariable Long instanceId,
            @PathVariable Long userId)
    {
        return R.ok(monitorService.canAccessInstance(instanceId, userId));
    }

    @InnerAuth
    @GetMapping("/status")
    public R<ApprovalServiceStatus> getServiceStatus()
    {
        return R.ok(new ApprovalServiceStatus(
                ServiceNameConstants.APPROVAL_SERVICE, true, "NATIVE_RUNTIME"));
    }
}
