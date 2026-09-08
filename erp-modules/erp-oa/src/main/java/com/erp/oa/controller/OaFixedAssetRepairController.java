package com.erp.oa.controller;

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
import com.erp.oa.domain.OaFixedAssetRepair;
import com.erp.oa.domain.dto.OaFixedAssetRepairBatchRequest;
import com.erp.oa.service.IOaFixedAssetService;

@RestController
@RequestMapping("/fixedAsset/repair")
public class OaFixedAssetRepairController extends OaBaseController
{
    @Autowired
    private IOaFixedAssetService fixedAssetService;

    @RequiresPermissions("oa:fixedAsset:repair:list")
    @GetMapping("/list")
    public TableDataInfo list(OaFixedAssetRepair repair, HttpServletRequest request)
    {
        startPage();
        List<OaFixedAssetRepair> list = fixedAssetService.selectRepairList(repair, resolveShopDeptId(request));
        return getDataTable(list);
    }

    @RequiresPermissions("oa:fixedAsset:repair:query")
    @GetMapping("/{repairId}")
    public AjaxResult getInfo(@PathVariable Long repairId, HttpServletRequest request)
    {
        return success(fixedAssetService.selectRepairById(repairId, resolveShopDeptId(request)));
    }

    @RequiresPermissions("oa:fixedAsset:repair:add")
    @PostMapping("/precheck")
    public AjaxResult precheck(@Validated @RequestBody OaFixedAssetRepair repair,
            HttpServletRequest request)
    {
        return success(fixedAssetService.precheckRepair(repair,
                resolveShopDeptId(request)));
    }

    @RequiresPermissions("oa:fixedAsset:repair:add")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "固定资产维修上报", businessType = BusinessType.INSERT)
    @PostMapping("/submit")
    public AjaxResult submit(@Validated @RequestBody OaFixedAssetRepair repair, HttpServletRequest request)
    {
        return success(fixedAssetService.submitRepair(repair, resolveShopDeptId(request)));
    }

    @RequiresPermissions("oa:fixedAsset:repair:add")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "固定资产批量维修上报", businessType = BusinessType.INSERT)
    @PostMapping("/submit/batch")
    public AjaxResult submitBatch(@Validated @RequestBody OaFixedAssetRepairBatchRequest batchRequest,
            HttpServletRequest request)
    {
        return success(fixedAssetService.submitRepairBatch(batchRequest, resolveShopDeptId(request)));
    }

    @RequiresPermissions("oa:fixedAsset:repair:confirm")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "固定资产维修确认上报", businessType = BusinessType.UPDATE)
    @PostMapping("/{repairId}/confirm")
    public AjaxResult confirm(@PathVariable Long repairId, HttpServletRequest request)
    {
        return success(fixedAssetService.confirmApprovedRepair(repairId, resolveShopDeptId(request)));
    }

    @RequiresPermissions("oa:fixedAsset:repair:export")
    @Log(title = "固定资产维修上报", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, OaFixedAssetRepair repair, HttpServletRequest request)
    {
        List<OaFixedAssetRepair> list = fixedAssetService.selectRepairList(repair, resolveShopDeptId(request));
        ExcelUtil<OaFixedAssetRepair> util = new ExcelUtil<>(OaFixedAssetRepair.class);
        util.exportExcel(response, list, "固定资产维修上报");
    }
}
