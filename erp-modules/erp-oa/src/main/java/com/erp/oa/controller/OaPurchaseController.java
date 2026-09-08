package com.erp.oa.controller;

import java.util.List;
import java.util.Map;
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
import com.erp.common.security.annotation.Logical;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.oa.domain.OaPurchase;
import com.erp.oa.domain.dto.OaPurchaseWithdrawRequest;
import com.erp.oa.service.BusinessFeatureGate;
import com.erp.oa.service.IOaPurchaseService;

@RestController
@RequestMapping("/purchase")
public class OaPurchaseController extends OaBaseController
{
    private final IOaPurchaseService purchaseService;
    private final BusinessFeatureGate businessFeatureGate;

    @Autowired
    public OaPurchaseController(IOaPurchaseService purchaseService,
            BusinessFeatureGate businessFeatureGate)
    {
        this.purchaseService = purchaseService;
        this.businessFeatureGate = businessFeatureGate;
    }

    @RequiresPermissions("oa:purchase:add")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "采购申请", businessType = BusinessType.INSERT)
    @PostMapping("/save")
    public AjaxResult save(@Validated @RequestBody OaPurchase purchase, HttpServletRequest request)
    {
        businessFeatureGate.requireEnabled(BusinessFeatureGate.OA_PURCHASE);
        return success(purchaseService.saveDraft(purchase, resolveShopDeptId(request)));
    }

    @RequiresPermissions("oa:purchase:add")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "采购申请", businessType = BusinessType.INSERT)
    @PostMapping("/submit")
    public AjaxResult submit(@Validated @RequestBody OaPurchase purchase, HttpServletRequest request)
    {
        businessFeatureGate.requireEnabled(BusinessFeatureGate.OA_PURCHASE);
        return success(purchaseService.submitPurchase(purchase, resolveShopDeptId(request)));
    }

    @RequiresPermissions("oa:purchase:add")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "关闭采购申请", businessType = BusinessType.UPDATE)
    @PostMapping("/{purchaseId}/close")
    public AjaxResult close(@PathVariable("purchaseId") Long purchaseId, HttpServletRequest request)
    {
        businessFeatureGate.requireEnabled(BusinessFeatureGate.OA_PURCHASE);
        return success(purchaseService.closeRejectedPurchase(purchaseId, resolveShopDeptId(request)));
    }

    @RequiresPermissions("oa:purchase:add")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "撤回采购申请", businessType = BusinessType.UPDATE)
    @PostMapping("/{purchaseId}/withdraw")
    public AjaxResult withdraw(@PathVariable("purchaseId") Long purchaseId,
            @RequestBody(required = false) OaPurchaseWithdrawRequest body,
            HttpServletRequest request)
    {
        String reason = body == null ? null : body.getReason();
        return success(purchaseService.withdrawPurchase(purchaseId, reason,
                resolveShopDeptId(request)));
    }

    @RequiresPermissions("oa:purchase:list")
    @GetMapping("/my")
    public TableDataInfo myList(OaPurchase purchase, HttpServletRequest request)
    {
        startPage();
        List<OaPurchase> list = purchaseService.selectMyPurchases(purchase, resolveShopDeptId(request));
        return getDataTable(list);
    }

    @RequiresPermissions("oa:purchase:add")
    @GetMapping("/availability")
    public AjaxResult availability()
    {
        return success(Map.of("enabled", purchaseService.isSubmissionEnabled()));
    }

    @RequiresPermissions(value = {
            "oa:purchase:query", "oa:purchase:add", "oa:todo:approve"
    },
            logical = Logical.OR)
    @GetMapping("/{purchaseId}")
    public AjaxResult detail(@PathVariable("purchaseId") Long purchaseId, HttpServletRequest request)
    {
        return success(purchaseService.getPurchaseDetail(purchaseId, resolveShopDeptId(request)));
    }

    @RequiresPermissions("oa:purchase:export")
    @Log(title = "采购申请", businessType = BusinessType.EXPORT)
    @PostMapping("/export/my")
    public void exportMy(HttpServletResponse response, OaPurchase purchase, HttpServletRequest request)
    {
        List<OaPurchase> list = purchaseService.selectMyPurchases(purchase, resolveShopDeptId(request));
        ExcelUtil<OaPurchase> util = new ExcelUtil<>(OaPurchase.class);
        util.exportExcel(response, list, "我的采购申请");
    }

}
