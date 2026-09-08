package com.erp.approval.controller;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.erp.approval.api.domain.LegacyApprovalDetail;
import com.erp.approval.api.domain.LegacyApprovalPage;
import com.erp.approval.api.domain.LegacyApprovalQuery;
import com.erp.approval.api.domain.LegacyApprovalTemplateSummary;
import com.erp.approval.service.LegacyApprovalMonitorService;
import com.erp.common.security.annotation.RequiresPermissions;

/** Read-only compatibility feed merged by the central approval monitor. */
@RestController
@RequestMapping("/legacy")
public class ApprovalLegacyController
{
    private final LegacyApprovalMonitorService service;

    public ApprovalLegacyController(LegacyApprovalMonitorService service)
    {
        this.service = service;
    }

    @RequiresPermissions("approval:instance:list")
    @GetMapping("/templates")
    public List<LegacyApprovalTemplateSummary> templates()
    {
        return service.templates();
    }

    @RequiresPermissions("approval:instance:list")
    @GetMapping("/instances")
    public LegacyApprovalPage instances(LegacyApprovalQuery query)
    {
        return service.instances(query);
    }

    @RequiresPermissions("approval:instance:query")
    @GetMapping("/instances/{businessCode}/{legacyInstanceId}")
    public LegacyApprovalDetail detail(
            @PathVariable String businessCode,
            @PathVariable Long legacyInstanceId)
    {
        return service.detail(businessCode, legacyInstanceId);
    }
}
