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
import com.erp.inventory.domain.InvSalesOrder;
import com.erp.inventory.service.IInvSalesService;

@RestController
@RequestMapping("/sales")
public class InvSalesController extends InvBaseController
{
    @Autowired
    private IInvSalesService salesService;

    @RequiresPermissions("inv:sales:add")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "销售管理", businessType = BusinessType.INSERT)
    @PostMapping("/save")
    public AjaxResult save(@Validated @RequestBody InvSalesOrder sales, HttpServletRequest request)
    {
        return success(salesService.saveDraft(sales, sales.getDetails(), resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:sales:submit")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "销售管理", businessType = BusinessType.INSERT)
    @PostMapping("/submit")
    public AjaxResult submit(@Validated @RequestBody InvSalesOrder sales, HttpServletRequest request)
    {
        return success(salesService.submitSales(sales, sales.getDetails(), resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:sales:list")
    @GetMapping("/list")
    public TableDataInfo list(InvSalesOrder sales, HttpServletRequest request)
    {
        startPage();
        List<InvSalesOrder> list = salesService.selectSalesList(sales, resolveShopDeptId(request));
        return getDataTable(list);
    }

    @RequiresPermissions("inv:sales:list")
    @GetMapping("/my")
    public TableDataInfo myList(InvSalesOrder sales, HttpServletRequest request)
    {
        startPage();
        List<InvSalesOrder> list = salesService.selectMySales(sales, resolveShopDeptId(request));
        return getDataTable(list);
    }

    @RequiresPermissions("inv:sales:query")
    @GetMapping("/{orderId}")
    public AjaxResult detail(@PathVariable("orderId") Long orderId, HttpServletRequest request)
    {
        return success(salesService.getSalesDetail(orderId, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:sales:deliver")
    @Log(title = "销售出库", businessType = BusinessType.UPDATE)
    @PostMapping("/deliver/{orderId}")
    public AjaxResult deliver(@PathVariable("orderId") Long orderId, HttpServletRequest request)
    {
        return error("请通过发货通知执行发货");
    }

    @RequiresPermissions("inv:sales:remove")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "销售管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{orderId}")
    public AjaxResult cancel(@PathVariable("orderId") Long orderId, HttpServletRequest request)
    {
        salesService.cancelSales(orderId, resolveShopDeptId(request));
        return success();
    }

    @RequiresPermissions("inv:sales:export")
    @Log(title = "销售管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, InvSalesOrder sales, HttpServletRequest request)
    {
        List<InvSalesOrder> list = salesService.selectSalesList(sales, resolveShopDeptId(request));
        ExcelUtil<InvSalesOrder> util = new ExcelUtil<>(InvSalesOrder.class);
        util.exportExcel(response, list, "销售单数据");
    }
}
