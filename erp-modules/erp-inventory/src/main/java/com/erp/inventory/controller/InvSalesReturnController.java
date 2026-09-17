package com.erp.inventory.controller;

import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.erp.common.core.utils.poi.ExcelUtil;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.core.web.page.TableDataInfo;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.IdempotentSubmit;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.common.security.annotation.Logical;
import com.erp.inventory.domain.InvSalesReturn;
import com.erp.inventory.domain.InvSalesOrder;
import com.erp.inventory.domain.dto.InvSalesReturnSourceQuery;
import com.erp.inventory.domain.dto.InvSalesReturnSaveRequest;
import com.erp.inventory.service.IInvSalesReturnService;

@RestController
@RequestMapping("/salesReturn")
public class InvSalesReturnController extends InvBaseController
{
    @Autowired
    private com.erp.inventory.service.impl.InvDraftCommandService draftCommands;

    @Autowired
    private IInvSalesReturnService salesReturnService;

    @RequiresPermissions(value = { "inv:salesReturn:add", "inv:salesReturn:submit", "inv:salesReturn:confirm", "inv:salesReturn:remove" }, logical = Logical.OR)
    @GetMapping("/action-context/{returnId}")
    public AjaxResult actionContext(@PathVariable("returnId") Long returnId, HttpServletRequest request,
            HttpServletResponse response)
    {
        response.setHeader("Cache-Control", "no-store, max-age=0");
        return success(salesReturnService.getActionContext(returnId, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:salesReturn:list")
    @GetMapping("/list")
    public TableDataInfo list(InvSalesReturn salesReturn, HttpServletRequest request)
    {
        startPage();
        List<InvSalesReturn> list = salesReturnService.selectReturnList(salesReturn, resolveShopDeptId(request));
        return getDataTable(list);
    }

    @RequiresPermissions("inv:salesReturn:list")
    @GetMapping("/my")
    public TableDataInfo my(InvSalesReturn salesReturn, HttpServletRequest request)
    {
        startPage();
        List<InvSalesReturn> list = salesReturnService.selectMyReturns(salesReturn, resolveShopDeptId(request));
        return getDataTable(list);
    }

    @RequiresPermissions("inv:salesReturn:add")
    @GetMapping("/draft/{returnId}")
    public AjaxResult draft(@PathVariable("returnId") Long returnId, HttpServletRequest request,
            HttpServletResponse response)
    {
        response.setHeader("Cache-Control", "no-store, max-age=0");
        return success(salesReturnService.getReturnDraft(returnId, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:salesReturn:add")
    @GetMapping("/source-orders")
    public TableDataInfo sourceOrders(InvSalesReturnSourceQuery query, HttpServletRequest request,
            HttpServletResponse response)
    {
        response.setHeader("Cache-Control", "no-store, max-age=0");
        query.validate();
        List<InvSalesOrder> rows = salesReturnService.selectReturnableSourceOrders(query, resolveShopDeptId(request));
        return getDataTable(rows);
    }

    @RequiresPermissions("inv:salesReturn:add")
    @GetMapping("/source-orders/{orderId}")
    public AjaxResult sourceOrder(@PathVariable("orderId") Long orderId, HttpServletRequest request,
            HttpServletResponse response)
    {
        response.setHeader("Cache-Control", "no-store, max-age=0");
        return success(salesReturnService.getReturnableSourceOrder(orderId, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:salesReturn:submit")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "销售退货管理", businessType = BusinessType.UPDATE)
    @PostMapping("/submit/{returnId}")
    public AjaxResult submitSaved(@PathVariable("returnId") Long returnId, HttpServletRequest request,
            HttpServletResponse response)
    {
        response.setHeader("Cache-Control", "no-store, max-age=0");
        return success(com.erp.inventory.domain.vo.InvSpecialistActionContext.salesReturn(
                salesReturnService.submitSavedReturn(returnId, requireDraftVersion(request), resolveShopDeptId(request))));
    }

    @RequiresPermissions("inv:salesReturn:query")
    @GetMapping("/{returnId}")
    public AjaxResult getInfo(@PathVariable("returnId") Long returnId, HttpServletRequest request)
    {
        return success(salesReturnService.getReturnDetail(returnId, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:salesReturn:add")
    @com.erp.inventory.annotation.PersistentCommand
    @Log(title = "销售退货管理", businessType = BusinessType.INSERT)
    @PostMapping("/save")
    public AjaxResult save(@Validated @RequestBody InvSalesReturnSaveRequest request, @org.springframework.web.bind.annotation.RequestHeader("X-Request-Id") String requestId, HttpServletRequest httpRequest)
    {
        try { return success(draftCommands.salesReturn(requestId, request, resolveShopDeptId(httpRequest), false)); }
        catch (com.erp.common.core.exception.ServiceException exception)
        {
            AjaxResult rejected = AjaxResult.error(exception.getMessage());
            rejected.put("draftOutcome", "REJECTED");
            return rejected;
        }
    }

    @RequiresPermissions(value = { "inv:salesReturn:add", "inv:salesReturn:submit" })
    @com.erp.inventory.annotation.PersistentCommand
    @Log(title = "销售退货管理", businessType = BusinessType.UPDATE)
    @PostMapping("/submit")
    public AjaxResult submit(@Validated @RequestBody InvSalesReturnSaveRequest request, @org.springframework.web.bind.annotation.RequestHeader("X-Request-Id") String requestId, HttpServletRequest httpRequest)
    {
        try { return success(draftCommands.salesReturn(requestId, request, resolveShopDeptId(httpRequest), true)); }
        catch (com.erp.common.core.exception.ServiceException exception)
        {
            AjaxResult rejected = AjaxResult.error(exception.getMessage());
            rejected.put("draftOutcome", "REJECTED");
            return rejected;
        }
    }

    @RequiresPermissions("inv:salesReturn:confirm")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "销售退货管理", businessType = BusinessType.UPDATE)
    @PostMapping("/confirm/{returnId}")
    public AjaxResult confirm(@PathVariable("returnId") Long returnId, HttpServletRequest request)
    {
        salesReturnService.confirmReturn(returnId, resolveShopDeptId(request));
        return success();
    }

    @RequiresPermissions("inv:salesReturn:remove")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "销售退货管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{returnId}")
    public AjaxResult remove(@PathVariable("returnId") Long returnId, HttpServletRequest request)
    {
        salesReturnService.cancelReturn(returnId, resolveShopDeptId(request));
        return success();
    }

    @RequiresPermissions("inv:salesReturn:export")
    @Log(title = "销售退货管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, InvSalesReturn salesReturn, HttpServletRequest request)
    {
        List<InvSalesReturn> list = salesReturnService.selectReturnList(salesReturn, resolveShopDeptId(request));
        ExcelUtil<InvSalesReturn> util = new ExcelUtil<>(InvSalesReturn.class);
        util.exportExcel(response, list, "销售退货数据");
    }
    private Long requireDraftVersion(HttpServletRequest request)
    {
        try { return Long.valueOf(request.getParameter("version")); }
        catch (RuntimeException exception) { throw new com.erp.common.core.exception.ServiceException("提交草稿缺少有效版本，请刷新页面"); }
    }
}
