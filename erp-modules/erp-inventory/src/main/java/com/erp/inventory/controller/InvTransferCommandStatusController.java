package com.erp.inventory.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.security.annotation.RequiresLogin;
import com.erp.inventory.service.impl.InvTransferCommandStatusService;

@RestController
@RequestMapping("/transfer/commands")
public class InvTransferCommandStatusController extends InvBaseController
{
    private final InvTransferCommandStatusService service;
    public InvTransferCommandStatusController(InvTransferCommandStatusService service)
    { this.service = service; }

    @RequiresLogin
    @GetMapping("/{requestId}/status")
    public AjaxResult status(@PathVariable String requestId, HttpServletRequest request)
    { return success(service.status(requestId, resolveShopDeptId(request))); }
}
