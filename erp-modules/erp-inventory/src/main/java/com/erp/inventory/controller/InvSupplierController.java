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
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.domain.InvProduct;
import com.erp.inventory.domain.InvSupplier;
import com.erp.inventory.service.IInvSupplierService;
import com.erp.system.api.model.LoginUser;

@RestController
@RequestMapping("/supplier")
public class InvSupplierController extends InvBaseController
{
    private static final String INV_COST_VIEW_PERMISSION = "inv:cost:view";

    @Autowired
    private IInvSupplierService supplierService;

    @RequiresPermissions("inv:supplier:list")
    @GetMapping("/list")
    public TableDataInfo list(InvSupplier supplier, HttpServletRequest request, HttpServletResponse response)
    {
        disableCaching(response);
        startPage();
        List<InvSupplier> list = supplierService.selectSupplierList(supplier, resolveShopDeptId(request));
        return getDataTable(list);
    }

    @RequiresPermissions("inv:supplier:query")
    @GetMapping("/{supplierId}")
    public AjaxResult getInfo(@PathVariable("supplierId") Long supplierId, HttpServletRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        return success(supplierService.selectSupplierById(supplierId, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:supplier:query")
    @GetMapping("/{supplierId}/products")
    public AjaxResult products(@PathVariable("supplierId") Long supplierId, HttpServletRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        List<InvProduct> list = supplierService.selectSupplierProductList(supplierId, resolveShopDeptId(request));
        hideProductCostFieldsIfNeeded(list);
        return success(list);
    }

    @RequiresPermissions("inv:supplier:add")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "供应商管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody InvSupplier supplier, HttpServletRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        return success(supplierService.saveSupplier(supplier, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:supplier:edit")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "供应商管理", businessType = BusinessType.UPDATE)
    @PostMapping("/update")
    public AjaxResult edit(@Validated @RequestBody InvSupplier supplier, HttpServletRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        return success(supplierService.saveSupplier(supplier, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:supplier:remove")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "供应商管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{supplierIds}")
    public AjaxResult remove(@PathVariable Long[] supplierIds, HttpServletRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        supplierService.deleteSupplierByIds(supplierIds, resolveShopDeptId(request));
        return success();
    }

    @RequiresPermissions("inv:supplier:export")
    @Log(title = "供应商管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, InvSupplier supplier, HttpServletRequest request)
    {
        disableCaching(response);
        List<InvSupplier> list = supplierService.selectSupplierList(supplier, resolveShopDeptId(request));
        ExcelUtil<InvSupplier> util = new ExcelUtil<>(InvSupplier.class);
        util.exportExcel(response, list, "供应商数据");
    }

    private boolean hasCostViewPermission()
    {
        if (SecurityUtils.isAdmin())
        {
            return true;
        }
        LoginUser loginUser = SecurityUtils.getLoginUser();
        return loginUser != null
                && loginUser.getPermissions() != null
                && loginUser.getPermissions().contains(INV_COST_VIEW_PERMISSION);
    }

    private void hideProductCostFieldsIfNeeded(List<InvProduct> products)
    {
        if (hasCostViewPermission() || products == null)
        {
            return;
        }
        products.forEach(product ->
        {
            if (product != null)
            {
                product.setPurchasePrice(null);
                product.setCostPrice(null);
            }
        });
    }

    private void disableCaching(HttpServletResponse response)
    {
        response.setHeader("Cache-Control", "no-store, max-age=0");
        response.setHeader("Pragma", "no-cache");
    }
}
