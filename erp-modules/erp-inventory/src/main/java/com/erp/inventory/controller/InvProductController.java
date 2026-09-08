package com.erp.inventory.controller;

import java.io.ByteArrayInputStream;
import java.io.IOException;
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
import org.springframework.web.multipart.MultipartFile;
import com.erp.common.core.utils.poi.ExcelUtil;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.core.web.page.TableDataInfo;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.IdempotentSubmit;
import com.erp.common.security.annotation.RequiresLogin;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.domain.InvProduct;
import com.erp.inventory.service.IInvProductService;
import com.erp.inventory.service.impl.TeaProductExcelParser;
import com.erp.inventory.util.InvProductPurchaseExcelExporter;
import com.erp.system.api.model.LoginUser;

@RestController
@RequestMapping("/product")
public class InvProductController extends InvBaseController
{
    private static final String INV_COST_VIEW_PERMISSION = "inv:cost:view";

    @Autowired
    private IInvProductService productService;

    @RequiresPermissions("inv:product:list")
    @GetMapping("/list")
    public TableDataInfo list(InvProduct product, HttpServletRequest request, HttpServletResponse response)
    {
        disableCaching(response);
        startPage();
        List<InvProduct> list = productService.selectProductList(product, resolveShopDeptId(request));
        hideResponsePurchaseFieldsIfNeeded(list);
        return getDataTable(list);
    }

    @RequiresPermissions("inv:product:query")
    @GetMapping("/{productId}")
    public AjaxResult getInfo(@PathVariable("productId") Long productId, HttpServletRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        return success(hideResponsePurchaseFieldsIfNeeded(
                productService.selectProductById(productId, resolveShopDeptId(request))));
    }

    @RequiresPermissions("inv:product:add")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "商品管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody InvProduct product, HttpServletRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        hideRequestPurchaseFieldsIfNeeded(product);
        return success(hideResponsePurchaseFieldsIfNeeded(
                productService.saveProduct(product, resolveShopDeptId(request))));
    }

    @RequiresPermissions("inv:product:edit")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "商品管理", businessType = BusinessType.UPDATE)
    @PostMapping("/update")
    public AjaxResult edit(@Validated @RequestBody InvProduct product, HttpServletRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        hideRequestPurchaseFieldsIfNeeded(product);
        return success(hideResponsePurchaseFieldsIfNeeded(
                productService.saveProduct(product, resolveShopDeptId(request))));
    }

    @RequiresPermissions("inv:product:remove")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "商品管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{productIds}")
    public AjaxResult remove(@PathVariable Long[] productIds, HttpServletRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        productService.deleteProductByIds(productIds, resolveShopDeptId(request));
        return success();
    }

    @RequiresPermissions("inv:product:export")
    @Log(title = "商品管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, InvProduct product, HttpServletRequest request) throws IOException
    {
        disableCaching(response);
        List<InvProduct> list = productService.selectProductList(product, resolveShopDeptId(request));
        hideResponsePurchaseFieldsIfNeeded(list);
        InvProductPurchaseExcelExporter.export(response, list);
    }

    @RequiresPermissions("inv:product:import")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "商品管理", businessType = BusinessType.IMPORT)
    @PostMapping("/importData")
    public AjaxResult importData(MultipartFile file, boolean updateSupport, HttpServletRequest request) throws IOException
    {
        byte[] bytes = file.getBytes();
        try
        {
            TeaProductExcelParser.ParseResult parseResult = TeaProductExcelParser.parse(new ByteArrayInputStream(bytes));
            if (parseResult.isTeaProductSheet())
            {
                hideRequestPurchaseFieldsIfNeeded(parseResult.getProducts());
                String message = productService.importProduct(parseResult.getProducts(), updateSupport, resolveShopDeptId(request));
                return success(message + parseResult.toNoticeMessage());
            }
        }
        catch (Exception e)
        {
            throw new IOException("解析茶叶商品Excel失败：" + e.getMessage(), e);
        }
        ExcelUtil<InvProduct> util = new ExcelUtil<>(InvProduct.class);
        List<InvProduct> productList = util.importExcel(new ByteArrayInputStream(bytes));
        hideRequestPurchaseFieldsIfNeeded(productList);
        String message = productService.importProduct(productList, updateSupport, resolveShopDeptId(request));
        return success(message);
    }

    @RequiresLogin
    @PostMapping("/importTemplate")
    public void importTemplate(HttpServletResponse response) throws IOException
    {
        ExcelUtil<InvProduct> util = new ExcelUtil<>(InvProduct.class);
        util.importTemplateExcel(response, "商品导入模板");
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

    private void hideResponsePurchaseFieldsIfNeeded(List<InvProduct> products)
    {
        if (hasCostViewPermission() || products == null)
        {
            return;
        }
        products.forEach(this::hideProductPurchaseFields);
    }

    private void hideRequestPurchaseFieldsIfNeeded(List<InvProduct> products)
    {
        if (hasCostViewPermission() || products == null)
        {
            return;
        }
        products.forEach(this::hideProductPurchaseFields);
    }

    private void hideRequestPurchaseFieldsIfNeeded(InvProduct product)
    {
        if (!hasCostViewPermission())
        {
            hideProductPurchaseFields(product);
        }
    }

    private InvProduct hideResponsePurchaseFieldsIfNeeded(InvProduct product)
    {
        if (!hasCostViewPermission())
        {
            hideProductPurchaseFields(product);
        }
        return product;
    }

    private void hideProductPurchaseFields(InvProduct product)
    {
        if (product == null)
        {
            return;
        }
        product.setCostPrice(null);
        product.setPurchasePrice(null);
    }

    private void disableCaching(HttpServletResponse response)
    {
        response.setHeader("Cache-Control", "no-store, max-age=0");
        response.setHeader("Pragma", "no-cache");
    }
}
