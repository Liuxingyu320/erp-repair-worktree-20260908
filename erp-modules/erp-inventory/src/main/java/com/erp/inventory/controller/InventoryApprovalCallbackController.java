package com.erp.inventory.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.erp.approval.api.domain.ApprovalBusinessCallbackRequest;
import com.erp.approval.api.domain.ApprovalBusinessCallbackResponse;
import com.erp.common.core.domain.R;
import com.erp.common.security.annotation.InnerAuth;
import com.erp.inventory.service.impl.InventoryUnifiedApprovalService;

/** Internal, idempotent callback endpoint for native inventory approvals. */
@RestController
@RequestMapping("/inner/approval")
public class InventoryApprovalCallbackController
{
    private final InventoryUnifiedApprovalService approvalService;

    public InventoryApprovalCallbackController(
            InventoryUnifiedApprovalService approvalService)
    {
        this.approvalService = approvalService;
    }

    @InnerAuth
    @PostMapping("/callback")
    public R<ApprovalBusinessCallbackResponse> callback(
            @RequestBody ApprovalBusinessCallbackRequest request)
    {
        return R.ok(approvalService.applyCallback(request));
    }
}
