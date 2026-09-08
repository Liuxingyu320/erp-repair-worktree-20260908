package com.erp.inventory.controller;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.erp.approval.api.domain.LegacyApprovalDetail;
import com.erp.approval.api.domain.LegacyApprovalPage;
import com.erp.approval.api.domain.LegacyApprovalQuery;
import com.erp.approval.api.domain.LegacyApprovalTemplateSummary;
import com.erp.common.core.domain.R;
import com.erp.common.security.annotation.InnerAuth;
import com.erp.inventory.service.impl.InventoryLegacyApprovalService;

@RestController
@RequestMapping("/inner/approval/legacy")
public class InventoryLegacyApprovalController
{
    private final InventoryLegacyApprovalService service;

    public InventoryLegacyApprovalController(
            InventoryLegacyApprovalService service)
    {
        this.service = service;
    }

    @GetMapping("/templates")
    @InnerAuth
    public R<List<LegacyApprovalTemplateSummary>> templates()
    {
        return R.ok(service.templates());
    }

    @GetMapping("/instances")
    @InnerAuth
    public R<LegacyApprovalPage> instances(
            @RequestParam(required = false) String businessCode,
            @RequestParam(required = false) String businessId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize)
    {
        LegacyApprovalQuery query = new LegacyApprovalQuery();
        query.setBusinessCode(businessCode);
        query.setBusinessId(businessId);
        query.setStatus(status);
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);
        return R.ok(service.instances(query));
    }

    @GetMapping("/instances/{businessCode}/{legacyInstanceId}")
    @InnerAuth
    public R<LegacyApprovalDetail> detail(
            @PathVariable String businessCode,
            @PathVariable Long legacyInstanceId)
    {
        return R.ok(service.detail(businessCode, legacyInstanceId));
    }
}
