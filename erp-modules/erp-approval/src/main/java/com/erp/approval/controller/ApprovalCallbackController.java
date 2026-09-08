package com.erp.approval.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.erp.approval.domain.dto.ApprovalCallbackReplayRequest;
import com.erp.approval.service.ApprovalCallbackOutboxService;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.common.security.utils.SecurityUtils;

@RestController
@RequestMapping("/callbacks")
public class ApprovalCallbackController
{
    private final ApprovalCallbackOutboxService outboxService;
    public ApprovalCallbackController(ApprovalCallbackOutboxService outboxService)
    {
        this.outboxService = outboxService;
    }

    @RequiresPermissions("approval:callback:replay")
    @PostMapping("/{id}/replay")
    public AjaxResult replay(@PathVariable Long id,
            @Valid @RequestBody ApprovalCallbackReplayRequest request)
    {
        return AjaxResult.success(outboxService.replay(id, request,
                SecurityUtils.getUserId(), SecurityUtils.getUsername()));
    }
}
