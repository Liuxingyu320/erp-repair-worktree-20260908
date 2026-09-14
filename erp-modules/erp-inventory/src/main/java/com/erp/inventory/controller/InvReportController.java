package com.erp.inventory.controller;

import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.erp.common.core.utils.poi.ExcelUtil;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.core.web.page.TableDataInfo;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.domain.InvStock;
import com.erp.inventory.domain.vo.InvReportProductOption;
import com.erp.inventory.domain.vo.InvReportItemOption;
import com.erp.inventory.domain.vo.InvReportSummary;
import com.erp.inventory.domain.vo.InvReportWarningExportRow;
import com.erp.inventory.service.IInvReportService;
import com.erp.system.api.model.LoginUser;

@RestController
@RequestMapping("/report")
public class InvReportController extends InvBaseController
{
    private static final String INV_COST_VIEW_PERMISSION = "inv:cost:view";

    @Autowired
    private IInvReportService reportService;

    @RequiresPermissions("inv:report:list")
    @GetMapping("/summary")
    public AjaxResult summary(InvStock stock, HttpServletRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        InvReportSummary summary = reportService.selectReportSummary(stock, resolveShopDeptId(request));
        hideCostMetricsIfNeeded(summary);
        return success(summary);
    }

    @RequiresPermissions("inv:report:list")
    @GetMapping("/stock-warning")
    public TableDataInfo stockWarning(InvStock stock, HttpServletRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        startPage();
        List<InvStock> list = reportService.selectStockWarningList(stock, resolveShopDeptId(request));
        return getDataTable(list);
    }

    @RequiresPermissions("inv:report:list")
    @GetMapping("/product-options")
    public AjaxResult productOptions(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "limit", defaultValue = "20") Integer limit,
            HttpServletRequest request, HttpServletResponse response)
    {
        disableCaching(response);
        List<InvReportProductOption> options = reportService.selectProductOptions(
                keyword, limit, resolveShopDeptId(request));
        return success(options);
    }

    @RequiresPermissions("inv:report:list")
    @GetMapping("/item-options")
    public AjaxResult itemOptions(
            @RequestParam(value = "itemType", required = false) String itemType,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "limit", defaultValue = "20") Integer limit,
            HttpServletRequest request, HttpServletResponse response)
    {
        disableCaching(response);
        return success(reportService.selectItemOptions(itemType, keyword, limit, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:report:export")
    @Log(title = "库存报表预警", businessType = BusinessType.EXPORT)
    @PostMapping("/stock-warning/export")
    public void exportStockWarning(HttpServletResponse response, InvStock stock,
            HttpServletRequest request)
    {
        disableCaching(response);
        List<InvReportWarningExportRow> rows = reportService.selectStockWarningList(
                stock, resolveShopDeptId(request)).stream()
                .map(InvReportWarningExportRow::from)
                .toList();
        ExcelUtil<InvReportWarningExportRow> util =
                new ExcelUtil<>(InvReportWarningExportRow.class);
        util.exportExcel(response, rows, "库存预警数据");
    }

    private void hideCostMetricsIfNeeded(InvReportSummary summary)
    {
        if (summary == null || hasCostViewPermission())
        {
            return;
        }
        summary.setTotalStockCost(null);
        summary.setPurchaseAmount(null);
        summary.setSalesCost(null);
        summary.setGrossMargin(null);
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

    private void disableCaching(HttpServletResponse response)
    {
        response.setHeader("Cache-Control", "no-store, max-age=0");
        response.setHeader("Pragma", "no-cache");
    }
}
