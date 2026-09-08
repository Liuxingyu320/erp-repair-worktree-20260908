package com.erp.inventory.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.core.web.page.TableDataInfo;
import com.erp.common.security.annotation.Logical;
import com.erp.common.security.annotation.RequiresLogin;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.inventory.service.IInvMobileService;

@RestController
@RequestMapping("/mobile")
public class InvMobileController extends InvBaseController
{
    @Autowired
    private IInvMobileService mobileService;

    @RequiresLogin
    @RequiresPermissions(value = { "inv:product:list", "inv:customer:list", "inv:supplier:list", "inv:stock:list" },
            logical = Logical.OR)
    @GetMapping("/options/{type}")
    public AjaxResult options(@PathVariable String type, String keyword, Integer limit, HttpServletRequest request)
    {
        return success(mobileService.selectOptions(type, keyword, limit, resolveShopDeptId(request)));
    }

    @RequiresLogin
    @GetMapping("/workbench/summary")
    public AjaxResult workbenchSummary(HttpServletRequest request)
    {
        return success(mobileService.selectWorkbenchSummary(resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:transfer:approve")
    @GetMapping("/transfer-approval/todo")
    public TableDataInfo transferApprovalTodos(HttpServletRequest request)
    {
        startPage();
        return getDataTable(mobileService.selectPendingTransferApprovals(resolveShopDeptId(request)));
    }
}
