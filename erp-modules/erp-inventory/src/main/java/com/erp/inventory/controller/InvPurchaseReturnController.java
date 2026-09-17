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
import com.erp.inventory.domain.InvPurchaseOrder;
import com.erp.inventory.domain.InvPurchaseReturn;
import com.erp.inventory.domain.dto.InvPurchaseReturnSaveRequest;
import com.erp.inventory.service.IInvPurchaseReturnService;

@RestController
@RequestMapping("/purchaseReturn")
public class InvPurchaseReturnController extends InvBaseController
{
    @Autowired
    private com.erp.inventory.service.impl.InvDraftCommandService draftCommands;

    @Autowired
    private IInvPurchaseReturnService purchaseReturnService;

    @RequiresPermissions(value = { "inv:purchaseReturn:add", "inv:purchaseReturn:submit", "inv:purchaseReturn:confirm", "inv:purchaseReturn:remove" }, logical = Logical.OR)
    @GetMapping("/action-context/{returnId}")
    public AjaxResult actionContext(@PathVariable("returnId") Long returnId, HttpServletRequest request,
            HttpServletResponse response)
    {
        response.setHeader("Cache-Control", "no-store, max-age=0");
        return success(purchaseReturnService.getActionContext(returnId, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:purchaseReturn:list")
    @GetMapping("/list")
    public TableDataInfo list(InvPurchaseReturn purchaseReturn, HttpServletRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        startPage();
        List<InvPurchaseReturn> list = purchaseReturnService.selectReturnList(purchaseReturn, resolveShopDeptId(request));
        return getDataTable(list);
    }

    @RequiresPermissions("inv:purchaseReturn:list")
    @GetMapping("/my")
    public TableDataInfo my(InvPurchaseReturn purchaseReturn, HttpServletRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        startPage();
        List<InvPurchaseReturn> list = purchaseReturnService.selectMyReturns(purchaseReturn, resolveShopDeptId(request));
        return getDataTable(list);
    }

    @RequiresPermissions("inv:purchaseReturn:add")
    @GetMapping("/source-orders")
    public TableDataInfo sourceOrders(InvPurchaseOrder purchaseOrder,
            HttpServletRequest request, HttpServletResponse response)
    {
        disableCaching(response);
        startPage();
        return getDataTable(purchaseReturnService.selectReturnableSourceOrders(
                purchaseOrder, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:purchaseReturn:add")
    @GetMapping("/source-orders/{orderId}")
    public AjaxResult sourceOrder(@PathVariable("orderId") Long orderId,
            HttpServletRequest request, HttpServletResponse response)
    {
        disableCaching(response);
        return success(purchaseReturnService.getReturnableSourceOrder(orderId,
                resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:purchaseReturn:add")
    @GetMapping("/draft/{returnId}")
    public AjaxResult draft(@PathVariable("returnId") Long returnId, HttpServletRequest request,
            HttpServletResponse response)
    {
        response.setHeader("Cache-Control", "no-store, max-age=0");
        return success(purchaseReturnService.getReturnDraft(returnId, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:purchaseReturn:query")
    @GetMapping("/{returnId}")
    public AjaxResult getInfo(@PathVariable("returnId") Long returnId,
            HttpServletRequest request, HttpServletResponse response)
    {
        disableCaching(response);
        return success(purchaseReturnService.getReturnDetail(returnId, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:purchaseReturn:add")
    @com.erp.inventory.annotation.PersistentCommand
    @Log(title = "采购退货管理", businessType = BusinessType.INSERT)
    @PostMapping("/save")
    public AjaxResult save(@Validated @RequestBody InvPurchaseReturnSaveRequest request,
            @org.springframework.web.bind.annotation.RequestHeader("X-Request-Id") String requestId, HttpServletRequest httpRequest, HttpServletResponse response)
    {
        disableCaching(response);
        try { return success(draftCommands.purchaseReturn(requestId, request, resolveShopDeptId(httpRequest), false)); }
        catch (com.erp.common.core.exception.ServiceException exception)
        {
            AjaxResult rejected = AjaxResult.error(exception.getMessage());
            rejected.put("draftOutcome", "REJECTED");
            return rejected;
        }
    }

    @RequiresPermissions(value = { "inv:purchaseReturn:add", "inv:purchaseReturn:submit" })
    @com.erp.inventory.annotation.PersistentCommand
    @Log(title = "采购退货管理", businessType = BusinessType.UPDATE)
    @PostMapping("/submit")
    public AjaxResult submit(@Validated @RequestBody InvPurchaseReturnSaveRequest request,
            @org.springframework.web.bind.annotation.RequestHeader("X-Request-Id") String requestId, HttpServletRequest httpRequest, HttpServletResponse response)
    {
        disableCaching(response);
        try { return success(draftCommands.purchaseReturn(requestId, request, resolveShopDeptId(httpRequest), true)); }
        catch (com.erp.common.core.exception.ServiceException exception)
        {
            AjaxResult rejected = AjaxResult.error(exception.getMessage());
            rejected.put("draftOutcome", "REJECTED");
            return rejected;
        }
    }

    @RequiresPermissions("inv:purchaseReturn:submit")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "采购退货管理", businessType = BusinessType.UPDATE)
    @PostMapping("/submit/{returnId}")
    public AjaxResult submitSaved(@PathVariable("returnId") Long returnId,
            HttpServletRequest request, HttpServletResponse response)
    {
        disableCaching(response);
        return success(purchaseReturnService.submitSavedReturn(returnId, requireDraftVersion(request), resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:purchaseReturn:confirm")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "采购退货管理", businessType = BusinessType.UPDATE)
    @PostMapping("/confirm/{returnId}")
    public AjaxResult confirm(@PathVariable("returnId") Long returnId,
            HttpServletRequest request, HttpServletResponse response)
    {
        disableCaching(response);
        purchaseReturnService.confirmReturn(returnId, resolveShopDeptId(request));
        return success();
    }

    @RequiresPermissions("inv:purchaseReturn:remove")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "采购退货管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{returnId}")
    public AjaxResult remove(@PathVariable("returnId") Long returnId,
            HttpServletRequest request, HttpServletResponse response)
    {
        disableCaching(response);
        purchaseReturnService.cancelReturn(returnId, resolveShopDeptId(request));
        return success();
    }

    @RequiresPermissions("inv:purchaseReturn:export")
    @Log(title = "采购退货管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, InvPurchaseReturn purchaseReturn, HttpServletRequest request)
    {
        disableCaching(response);
        List<InvPurchaseReturn> list = purchaseReturnService.selectReturnList(purchaseReturn, resolveShopDeptId(request));
        ExcelUtil<InvPurchaseReturn> util = new ExcelUtil<>(InvPurchaseReturn.class);
        util.exportExcel(response, list, "采购退货数据");
    }

    private void disableCaching(HttpServletResponse response)
    {
        response.setHeader("Cache-Control", "no-store, max-age=0");
        response.setHeader("Pragma", "no-cache");
    }
    private Long requireDraftVersion(HttpServletRequest request)
    {
        try { return Long.valueOf(request.getParameter("version")); }
        catch (RuntimeException exception) { throw new com.erp.common.core.exception.ServiceException("提交草稿缺少有效版本，请刷新页面"); }
    }
}
