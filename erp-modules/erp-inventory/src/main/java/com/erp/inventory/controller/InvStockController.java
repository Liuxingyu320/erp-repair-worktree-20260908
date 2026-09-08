package com.erp.inventory.controller;

import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
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
import com.erp.inventory.domain.InvStock;
import com.erp.inventory.domain.InvStockLog;
import com.erp.inventory.domain.dto.InvExistingStockAdjustRequest;
import com.erp.inventory.domain.dto.InvStockAdjustRequest;
import com.erp.inventory.domain.vo.InvStockSummary;
import com.erp.inventory.service.IInvStockService;
import com.erp.system.api.model.LoginUser;

@RestController
@RequestMapping("/stock")
public class InvStockController extends InvBaseController
{
    private static final String INV_COST_VIEW_PERMISSION = "inv:cost:view";

    @Autowired
    private IInvStockService stockService;

    @RequiresPermissions("inv:stock:list")
    @GetMapping("/list")
    public TableDataInfo list(InvStock stock, HttpServletRequest request, HttpServletResponse response)
    {
        disableCaching(response);
        startPage();
        List<InvStock> list = stockService.selectStockList(stock, resolveShopDeptId(request));
        hideStockCostFieldsIfNeeded(list);
        return getDataTable(list);
    }

    @RequiresPermissions("inv:stock:list")
    @GetMapping("/summary")
    public AjaxResult summary(InvStock stock, HttpServletRequest request, HttpServletResponse response)
    {
        disableCaching(response);
        InvStockSummary summary = stockService.selectStockSummary(stock, resolveShopDeptId(request));
        if (!hasCostViewPermission())
        {
            summary.setTotalCost(null);
        }
        return success(summary);
    }

    @RequiresPermissions("inv:stock:query")
    @GetMapping("/{stockId}")
    public AjaxResult getInfo(@PathVariable("stockId") Long stockId, HttpServletRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        return success(hideStockCostFieldsIfNeeded(
                stockService.selectStockById(stockId, resolveShopDeptId(request))));
    }

    @RequiresPermissions("inv:stock:adjust")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "库存调整", businessType = BusinessType.UPDATE)
    @PostMapping("/{stockId}/adjust")
    public AjaxResult adjustExisting(@PathVariable("stockId") Long stockId,
            @Validated @RequestBody InvExistingStockAdjustRequest adjustRequest,
            HttpServletRequest request, HttpServletResponse response)
    {
        disableCaching(response);
        return success(hideStockCostFieldsIfNeeded(
                stockService.adjustExistingStock(stockId, adjustRequest, resolveShopDeptId(request))));
    }

    @RequiresPermissions("inv:stock:adjust")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "库存调整", businessType = BusinessType.UPDATE)
    @PostMapping("/adjust")
    public AjaxResult adjust(@Validated @RequestBody InvStockAdjustRequest adjustRequest,
            HttpServletRequest request, HttpServletResponse response)
    {
        disableCaching(response);
        stockService.adjustStock(adjustRequest, resolveShopDeptId(request));
        return success();
    }

    @RequiresPermissions("inv:stock:log")
    @GetMapping("/log/list")
    public TableDataInfo logList(InvStockLog stockLog, HttpServletRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        startPage();
        List<InvStockLog> list = stockService.selectStockLogList(stockLog, resolveShopDeptId(request));
        hideStockLogCostFieldsIfNeeded(list);
        return getDataTable(list);
    }

    @RequiresPermissions("inv:stock:export")
    @Log(title = "库存管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, InvStock stock, HttpServletRequest request)
    {
        disableCaching(response);
        List<InvStock> list = stockService.selectStockList(stock, resolveShopDeptId(request));
        hideStockCostFieldsIfNeeded(list);
        ExcelUtil<InvStock> util = new ExcelUtil<>(InvStock.class);
        util.exportExcel(response, list, "库存数据");
    }

    @RequiresPermissions("inv:stock:export")
    @Log(title = "库存管理", businessType = BusinessType.EXPORT)
    @PostMapping("/log/export")
    public void exportLog(HttpServletResponse response, InvStockLog stockLog, HttpServletRequest request)
    {
        disableCaching(response);
        List<InvStockLog> list = stockService.selectStockLogList(stockLog, resolveShopDeptId(request));
        hideStockLogCostFieldsIfNeeded(list);
        ExcelUtil<InvStockLog> util = new ExcelUtil<>(InvStockLog.class);
        util.exportExcel(response, list, "库存变动日志");
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

    private void hideStockCostFieldsIfNeeded(List<InvStock> stocks)
    {
        if (hasCostViewPermission() || stocks == null)
        {
            return;
        }
        stocks.forEach(this::hideStockCostFields);
    }

    private InvStock hideStockCostFieldsIfNeeded(InvStock stock)
    {
        if (!hasCostViewPermission())
        {
            hideStockCostFields(stock);
        }
        return stock;
    }

    private void hideStockCostFields(InvStock stock)
    {
        if (stock == null)
        {
            return;
        }
        stock.setCostPrice(null);
        stock.setTotalCost(null);
    }

    private void hideStockLogCostFieldsIfNeeded(List<InvStockLog> logs)
    {
        if (hasCostViewPermission() || logs == null)
        {
            return;
        }
        logs.forEach(log -> {
            if (log != null)
            {
                log.setCostPrice(null);
            }
        });
    }

    private void disableCaching(HttpServletResponse response)
    {
        response.setHeader("Cache-Control", "no-store, max-age=0");
        response.setHeader("Pragma", "no-cache");
    }
}
