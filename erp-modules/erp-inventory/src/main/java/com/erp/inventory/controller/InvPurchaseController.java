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
import org.springframework.web.bind.annotation.RequestHeader;
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
import com.erp.inventory.annotation.PersistentCommand;
import com.erp.inventory.domain.InvPurchaseOrder;
import com.erp.inventory.domain.InvGiftBox;
import com.erp.inventory.domain.InvOeItem;
import com.erp.inventory.domain.InvProduct;
import com.erp.inventory.domain.InvSupplier;
import com.erp.inventory.domain.dto.InvPurchaseSaveRequest;
import com.erp.inventory.domain.dto.InvQualityCheckRequest;
import com.erp.inventory.domain.dto.InvReceiveRequest;
import com.erp.inventory.service.IInvPurchaseService;

@RestController
@RequestMapping("/purchase")
public class InvPurchaseController extends InvBaseController
{
    @Autowired
    private com.erp.inventory.service.impl.InvDraftCommandService draftCommands;

    @Autowired
    private IInvPurchaseService purchaseService;

    @RequiresPermissions(value = { "inv:purchase:add", "inv:purchase:submit", "inv:purchase:receive", "inv:purchase:qc", "inv:purchase:remove" }, logical = Logical.OR)
    @GetMapping("/action-context/{orderId}")
    public AjaxResult actionContext(@PathVariable("orderId") Long orderId, HttpServletRequest request,
            HttpServletResponse response)
    {
        response.setHeader("Cache-Control", "no-store, max-age=0");
        return success(purchaseService.getActionContext(orderId, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:purchase:add")
    @com.erp.inventory.annotation.PersistentCommand
    @Log(title = "采购管理", businessType = BusinessType.INSERT)
    @PostMapping("/save")
    public AjaxResult save(@Validated @RequestBody InvPurchaseSaveRequest purchase,
            @org.springframework.web.bind.annotation.RequestHeader("X-Request-Id") String requestId, HttpServletRequest request, HttpServletResponse response)
    {
        disableCaching(response);
        try { return success(draftCommands.purchase(requestId, purchase, resolveShopDeptId(request), false)); }
        catch (com.erp.common.core.exception.ServiceException exception)
        {
            AjaxResult rejected = AjaxResult.error(exception.getMessage());
            rejected.put("draftOutcome", "REJECTED");
            return rejected;
        }
    }

    @RequiresPermissions(value = { "inv:purchase:add", "inv:purchase:submit" })
    @com.erp.inventory.annotation.PersistentCommand
    @Log(title = "采购管理", businessType = BusinessType.INSERT)
    @PostMapping("/submit")
    public AjaxResult submit(@Validated @RequestBody InvPurchaseSaveRequest purchase,
            @org.springframework.web.bind.annotation.RequestHeader("X-Request-Id") String requestId, HttpServletRequest request, HttpServletResponse response)
    {
        disableCaching(response);
        try { return success(draftCommands.purchase(requestId, purchase, resolveShopDeptId(request), true)); }
        catch (com.erp.common.core.exception.ServiceException exception)
        {
            AjaxResult rejected = AjaxResult.error(exception.getMessage());
            rejected.put("draftOutcome", "REJECTED");
            return rejected;
        }
    }

    @RequiresPermissions("inv:purchase:submit")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "采购管理", businessType = BusinessType.UPDATE)
    @PostMapping("/submit/{orderId}")
    public AjaxResult submitSaved(@PathVariable("orderId") Long orderId,
            HttpServletRequest request, HttpServletResponse response)
    {
        disableCaching(response);
        return success(purchaseService.submitSavedPurchase(orderId, requireDraftVersion(request), resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:purchase:list")
    @GetMapping("/list")
    public TableDataInfo list(InvPurchaseOrder purchase, HttpServletRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        startPage();
        List<InvPurchaseOrder> list = purchaseService.selectPurchaseList(purchase, resolveShopDeptId(request));
        return getDataTable(list);
    }

    @RequiresPermissions("inv:purchase:list")
    @GetMapping("/my")
    public TableDataInfo myList(InvPurchaseOrder purchase, HttpServletRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        startPage();
        List<InvPurchaseOrder> list = purchaseService.selectMyPurchases(purchase, resolveShopDeptId(request));
        return getDataTable(list);
    }

    @RequiresPermissions("inv:purchase:add")
    @GetMapping("/draft/{orderId}")
    public AjaxResult draft(@PathVariable("orderId") Long orderId, HttpServletRequest request,
            HttpServletResponse response)
    {
        response.setHeader("Cache-Control", "no-store, max-age=0");
        return success(purchaseService.getPurchaseDraft(orderId, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:purchase:query")
    @GetMapping("/{orderId}")
    public AjaxResult detail(@PathVariable("orderId") Long orderId, HttpServletRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        return success(purchaseService.getPurchaseDetail(orderId, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:purchase:add")
    @GetMapping("/catalog/suppliers")
    public TableDataInfo suppliers(InvSupplier supplier, HttpServletRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        startPage();
        return getDataTable(purchaseService.selectPurchaseSuppliers(supplier, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:purchase:add")
    @GetMapping("/catalog/products")
    public TableDataInfo products(InvProduct product, HttpServletRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        startPage();
        return getDataTable(purchaseService.selectPurchaseProducts(product, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:purchase:add")
    @GetMapping("/catalog/oe")
    public TableDataInfo oeItems(InvOeItem item, HttpServletRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        startPage();
        return getDataTable(purchaseService.selectPurchaseOeItems(item, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:purchase:add")
    @GetMapping("/catalog/gifts")
    public TableDataInfo gifts(InvGiftBox gift, HttpServletRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        startPage();
        return getDataTable(purchaseService.selectPurchaseGifts(gift, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:purchase:receive")
    @GetMapping("/receive-context/{orderId}")
    public AjaxResult receiveContext(@PathVariable("orderId") Long orderId, HttpServletRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        return success(purchaseService.getReceiveContext(orderId, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:purchase:receive")
    @PersistentCommand
    @Log(title = "采购收货", businessType = BusinessType.UPDATE)
    @PostMapping("/receive/{orderId}")
    public AjaxResult receive(@PathVariable("orderId") Long orderId,
            @Validated @RequestBody InvReceiveRequest receiveRequest,
            @RequestHeader(name = "X-Request-Id") String requestId, HttpServletRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        return success(purchaseService.receivePurchase(orderId, receiveRequest,
                resolveShopDeptId(request), requestId));
    }

    @RequiresPermissions("inv:purchase:query")
    @GetMapping("/{orderId}/receipt-batches")
    public AjaxResult receiptBatches(@PathVariable("orderId") Long orderId,
            HttpServletRequest request, HttpServletResponse response)
    {
        disableCaching(response);
        return success(purchaseService.selectReceiptBatches(orderId, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:purchase:qc")
    @GetMapping("/{orderId}/receipt-batches/pending")
    public AjaxResult pendingReceiptBatches(@PathVariable("orderId") Long orderId,
            HttpServletRequest request, HttpServletResponse response)
    {
        disableCaching(response);
        return success(purchaseService.selectPendingReceiptBatches(orderId, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:purchase:qc")
    @com.erp.inventory.annotation.PersistentCommand
    @Log(title = "采购逐行质检", businessType = BusinessType.UPDATE)
    @PostMapping("/qc/batch/{orderId}")
    public AjaxResult qualityCheckBatch(@PathVariable("orderId") Long orderId,
            @Validated @RequestBody InvQualityCheckRequest qualityCheckRequest,
            HttpServletRequest request, HttpServletResponse response)
    {
        disableCaching(response);
        try
        {
            purchaseService.qualityCheckBatch(orderId, qualityCheckRequest, resolveShopDeptId(request));
            return success();
        }
        catch (com.erp.common.core.exception.ServiceException failure)
        {
            // The proxied service transaction has rolled back before this response is sent.
            AjaxResult rejected = AjaxResult.error(failure.getMessage());
            rejected.put("qualityCheckOutcome", "REJECTED");
            return rejected;
        }
    }

    @RequiresPermissions("inv:purchase:qc")
    @com.erp.inventory.annotation.PersistentCommand
    @Log(title = "采购质检", businessType = BusinessType.UPDATE)
    @PostMapping("/qc/{orderId}")
    public AjaxResult qualityCheck(@PathVariable("orderId") Long orderId,
            @RequestBody java.util.Map<String, String> body, HttpServletRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        String qcResult = body.get("qcResult");
        String qcRemark = body.get("qcRemark");
        if (qcResult == null || qcResult.isEmpty())
        {
            return error("质检结果不能为空");
        }
        try
        {
            purchaseService.qualityCheckWithRequest(orderId, qcResult, qcRemark, resolveShopDeptId(request), body.get("requestId"));
            return success();
        }
        catch (com.erp.common.core.exception.ServiceException failure)
        {
            AjaxResult rejected = AjaxResult.error(failure.getMessage());
            rejected.put("qualityCheckOutcome", "REJECTED");
            return rejected;
        }
    }

    @RequiresPermissions("inv:purchase:remove")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "采购管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/delete/{orderId}")
    public AjaxResult delete(@PathVariable("orderId") Long orderId, HttpServletRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        purchaseService.deletePurchase(orderId, resolveShopDeptId(request));
        return success();
    }

    @RequiresPermissions("inv:purchase:remove")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "采购管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{orderId}")
    public AjaxResult cancel(@PathVariable("orderId") Long orderId, HttpServletRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        purchaseService.cancelPurchase(orderId, resolveShopDeptId(request));
        return success();
    }

    @RequiresPermissions("inv:purchase:export")
    @Log(title = "采购管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, InvPurchaseOrder purchase, HttpServletRequest request)
    {
        disableCaching(response);
        List<InvPurchaseOrder> list = purchaseService.selectPurchaseList(purchase, resolveShopDeptId(request));
        ExcelUtil<InvPurchaseOrder> util = new ExcelUtil<>(InvPurchaseOrder.class);
        util.exportExcel(response, list, "采购单数据");
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
