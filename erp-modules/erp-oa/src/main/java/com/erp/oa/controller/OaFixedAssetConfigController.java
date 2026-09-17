package com.erp.oa.controller;

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
import org.springframework.web.bind.annotation.RequestParam;
import com.erp.common.security.annotation.Logical;
import com.erp.oa.domain.dto.OaFixedAssetConfigBatchRequest;
import org.springframework.web.bind.annotation.RestController;
import com.erp.common.core.utils.poi.ExcelUtil;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.core.web.page.TableDataInfo;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.IdempotentSubmit;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.oa.domain.OaFixedAssetConfig;
import com.erp.oa.domain.OaFixedAssetRepair;
import com.erp.oa.service.IOaFixedAssetService;

@RestController
@RequestMapping("/fixedAsset/config")
public class OaFixedAssetConfigController extends OaBaseController
{
    @Autowired
    private IOaFixedAssetService fixedAssetService;

    @RequiresPermissions("oa:fixedAsset:config:list")
    @GetMapping("/list")
    public TableDataInfo list(OaFixedAssetConfig config, HttpServletRequest request)
    {
        startPage();
        List<OaFixedAssetConfig> list = fixedAssetService.selectConfigList(config, resolveShopDeptId(request));
        return getDataTable(list);
    }

    @RequiresPermissions("oa:fixedAsset:config:list")
    @GetMapping("/stores")
    public TableDataInfo stores(OaFixedAssetConfig config, HttpServletRequest request)
    {
        startPage();
        return getDataTable(fixedAssetService.selectConfigStoreList(config, resolveShopDeptId(request)));
    }

    @RequiresPermissions("oa:fixedAsset:config:list")
    @GetMapping("/store-details")
    public AjaxResult storeDetails(OaFixedAssetConfig config, @RequestParam Long shopDeptId, HttpServletRequest request)
    {
        config.setShopDeptId(shopDeptId);
        return success(fixedAssetService.selectConfigList(config, resolveShopDeptId(request)));
    }

    @RequiresPermissions("oa:fixedAsset:config:query")
    @GetMapping("/{configId}")
    public AjaxResult getInfo(@PathVariable Long configId, HttpServletRequest request)
    {
        return success(fixedAssetService.selectConfigById(configId, resolveShopDeptId(request)));
    }

    @RequiresPermissions("oa:fixedAsset:config:edit")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "固定资产配置", businessType = BusinessType.UPDATE)
    @PostMapping("/save")
    public AjaxResult save(@Validated @RequestBody OaFixedAssetConfig config, HttpServletRequest request)
    {
        if (config.getExpectedVersion() == null || config.getExpectedVersion() < 0)
            throw new com.erp.common.core.exception.ServiceException("缺少店铺配置快照版本，请刷新页面使用整店保存");
        return success(fixedAssetService.saveConfig(config, resolveShopDeptId(request)));
    }

    @RequiresPermissions("oa:fixedAsset:config:delete")
    @Log(title = "固定资产配置", businessType = BusinessType.DELETE)
    @DeleteMapping("/{configId}")
    public AjaxResult remove(@PathVariable Long configId, @RequestParam(required=false) Long expectedVersion, HttpServletRequest request)
    {
        if (expectedVersion == null || expectedVersion < 0) throw new com.erp.common.core.exception.ServiceException("缺少店铺配置快照版本，请刷新页面使用整店删除");
        return toAjax(fixedAssetService.deleteConfigById(configId, resolveShopDeptId(request), expectedVersion));
    }

    @RequiresPermissions("oa:fixedAsset:config:list")
    @GetMapping("/snapshot")
    public AjaxResult snapshot(@RequestParam Long shopDeptId, HttpServletRequest request)
    {
        return success(fixedAssetService.selectConfigSnapshot(shopDeptId, resolveShopDeptId(request)));
    }

    @RequiresPermissions(value={"oa:fixedAsset:config:edit", "oa:fixedAsset:config:delete"}, logical=Logical.OR)
    @Log(title="整店固定资产配置", businessType=BusinessType.UPDATE)
    @PostMapping("/batch-save")
    public AjaxResult saveBatch(@Validated @RequestBody OaFixedAssetConfigBatchRequest body, HttpServletRequest request)
    {
        return success(fixedAssetService.saveConfigBatch(body, resolveShopDeptId(request)));
    }

    @RequiresPermissions(value={"oa:fixedAsset:config:edit", "oa:fixedAsset:config:delete"}, logical=Logical.OR)
    @GetMapping("/batch-commands/{requestId}")
    public AjaxResult command(@PathVariable String requestId, @RequestParam Long shopDeptId, HttpServletRequest request)
    {
        com.erp.oa.domain.vo.OaFixedAssetConfigSnapshot result = fixedAssetService.selectConfigCommand(requestId,shopDeptId,resolveShopDeptId(request));
        return success(java.util.Map.of("state",result == null ? "NOT_OBSERVED" : "SUCCEEDED", "snapshot",result == null ? java.util.Map.of() : result));
    }

    @RequiresPermissions("oa:fixedAsset:config:list")
    @GetMapping("/quota")
    public AjaxResult quota(Long shopDeptId, Integer quotaYear, HttpServletRequest request)
    {
        return success(fixedAssetService.getQuotaSummary(shopDeptId, quotaYear, resolveShopDeptId(request)));
    }

    @RequiresPermissions("oa:fixedAsset:config:approve")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "固定资产异常批准", businessType = BusinessType.INSERT)
    @PostMapping("/exception/approve")
    public AjaxResult approveException(@Validated @RequestBody OaFixedAssetRepair repair, HttpServletRequest request)
    {
        return success(fixedAssetService.approveExceptionRepair(repair, resolveShopDeptId(request)));
    }

    @RequiresPermissions("oa:fixedAsset:config:export")
    @Log(title = "固定资产配置", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, OaFixedAssetConfig config, HttpServletRequest request)
    {
        List<OaFixedAssetConfig> list = fixedAssetService.selectConfigList(config, resolveShopDeptId(request));
        ExcelUtil<OaFixedAssetConfig> util = new ExcelUtil<>(OaFixedAssetConfig.class);
        util.exportExcel(response, list, "固定资产配置");
    }
}
