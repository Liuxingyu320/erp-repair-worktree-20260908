package com.erp.approval.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.erp.approval.domain.dto.ApprovalReassignRequest;
import com.erp.approval.domain.dto.ApprovalTaskActionRequest;
import com.erp.approval.service.ApprovalTaskService;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.security.annotation.RequiresLogin;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.common.security.utils.SecurityUtils;

@RestController
@RequestMapping("/tasks")
public class ApprovalTaskController
{
    private final ApprovalTaskService taskService;
    public ApprovalTaskController(ApprovalTaskService taskService)
    {
        this.taskService = taskService;
    }

    @RequiresLogin
    @PostMapping("/{id}/approve")
    public AjaxResult approve(@PathVariable Long id,
            @Valid @RequestBody ApprovalTaskActionRequest request)
    {
        return AjaxResult.success(taskService.approve(id, request,
                SecurityUtils.getUserId(), SecurityUtils.getUsername()));
    }

    @RequiresLogin
    @PostMapping("/{id}/return")
    public AjaxResult returnTask(@PathVariable Long id,
            @Valid @RequestBody ApprovalTaskActionRequest request)
    {
        return AjaxResult.success(taskService.returnForModification(id, request,
                SecurityUtils.getUserId(), SecurityUtils.getUsername()));
    }

    @RequiresLogin
    @PostMapping("/{id}/reject")
    public AjaxResult reject(@PathVariable Long id,
            @Valid @RequestBody ApprovalTaskActionRequest request)
    {
        return AjaxResult.success(taskService.reject(id, request,
                SecurityUtils.getUserId(), SecurityUtils.getUsername()));
    }

    @RequiresPermissions("approval:task:reassign")
    @GetMapping("/{id}/reassign-options")
    public AjaxResult reassignOptions(@PathVariable Long id,
            @RequestParam Long fromCandidateId,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int pageNum)
    {
        return AjaxResult.success(taskService.reassignOptions(id,
                fromCandidateId, keyword, pageNum, SecurityUtils.getUserId()));
    }

    @RequiresPermissions("approval:task:reassign")
    @PostMapping("/{id}/reassign")
    public AjaxResult reassign(@PathVariable Long id,
            @Valid @RequestBody ApprovalReassignRequest request)
    {
        return AjaxResult.success(taskService.reassign(id, request,
                SecurityUtils.getUserId(), SecurityUtils.getUsername()));
    }
}
