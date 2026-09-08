package com.erp.approval.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.erp.approval.domain.ApprovalInstance;
import com.erp.approval.domain.dto.ApprovalTerminateRequest;
import com.erp.approval.service.ApprovalMonitorService;
import com.erp.approval.service.ApprovalRuntimeService;
import com.erp.common.core.web.controller.BaseController;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.core.web.page.TableDataInfo;
import com.erp.common.security.annotation.RequiresLogin;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.common.security.utils.SecurityUtils;

@RestController
@RequestMapping("/instances")
public class ApprovalRuntimeController extends BaseController
{
    private final ApprovalMonitorService monitorService;
    private final ApprovalRuntimeService runtimeService;
    public ApprovalRuntimeController(ApprovalMonitorService monitorService,
            ApprovalRuntimeService runtimeService)
    {
        this.monitorService = monitorService;
        this.runtimeService = runtimeService;
    }

    @RequiresPermissions("approval:instance:list")
    @GetMapping
    public TableDataInfo list(ApprovalInstance filter)
    {
        startPage();
        return getDataTable(monitorService.listInstances(filter));
    }

    @RequiresLogin
    @GetMapping("/{id}")
    public AjaxResult detail(@PathVariable Long id)
    {
        return success(monitorService.getInstanceDetail(id,
                SecurityUtils.getUserId()));
    }

    @RequiresPermissions("approval:instance:terminate")
    @PostMapping("/{id}/terminate")
    public AjaxResult terminate(@PathVariable Long id,
            @Valid @RequestBody ApprovalTerminateRequest request)
    {
        return success(runtimeService.terminate(id, request,
                SecurityUtils.getUserId(), SecurityUtils.getUsername()));
    }
}
