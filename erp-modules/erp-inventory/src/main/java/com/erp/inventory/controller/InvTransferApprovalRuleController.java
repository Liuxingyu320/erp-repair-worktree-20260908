package com.erp.inventory.controller;

import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.core.web.page.TableDataInfo;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.IdempotentSubmit;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.inventory.domain.InvTransferApprovalRule;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.dto.InvTransferApprovalRuleValidationRequest;
import com.erp.inventory.service.IInvTransferApprovalRuleService;
import com.erp.inventory.service.IInvTransferApprovalService;

@RestController
@RequestMapping("/transfer")
public class InvTransferApprovalRuleController extends InvBaseController
{
    @Autowired
    private IInvTransferApprovalRuleService ruleService;

    @Autowired
    private IInvTransferApprovalService approvalService;

    @RequiresPermissions("inv:transfer:rule:list")
    @GetMapping("/rule/list")
    public TableDataInfo list(InvTransferApprovalRule rule, HttpServletRequest request)
    {
        startPage();
        List<InvTransferApprovalRule> list = ruleService.selectRuleList(rule, resolveShopDeptId(request));
        return getDataTable(list);
    }

    @RequiresPermissions("inv:transfer:rule:query")
    @GetMapping("/rule/{ruleId}")
    public AjaxResult getInfo(@PathVariable("ruleId") Long ruleId, HttpServletRequest request)
    {
        return success(ruleService.selectRuleById(ruleId, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:transfer:rule:add")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "调拨审批配置", businessType = BusinessType.INSERT)
    @PostMapping("/rule")
    public AjaxResult add(@Validated @RequestBody InvTransferApprovalRule rule, HttpServletRequest request)
    {
        return success(ruleService.saveRule(rule, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:transfer:rule:edit")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "调拨审批配置", businessType = BusinessType.UPDATE)
    @PutMapping("/rule")
    public AjaxResult edit(@Validated @RequestBody InvTransferApprovalRule rule, HttpServletRequest request)
    {
        return success(ruleService.saveRule(rule, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:transfer:rule:remove")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "调拨审批配置", businessType = BusinessType.DELETE)
    @DeleteMapping("/rule/{ruleId}")
    public AjaxResult remove(@PathVariable("ruleId") Long ruleId,
            @RequestParam("expectedVersion") Integer expectedVersion, HttpServletRequest request)
    {
        return toAjax(ruleService.deleteRuleById(ruleId, expectedVersion, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:transfer:rule:query")
    @PostMapping("/rule/preview")
    public AjaxResult preview(@RequestBody InvTransferOrder transfer, HttpServletRequest request)
    {
        return success(ruleService.previewRule(transfer, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:transfer:rule:query")
    @PostMapping("/rule/validate")
    public AjaxResult validate(@RequestBody InvTransferApprovalRuleValidationRequest validationRequest,
            HttpServletRequest request)
    {
        return success(ruleService.validateRule(validationRequest, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:transfer:rule:query")
    @PostMapping("/rule/{ruleId}/candidate-preview")
    public AjaxResult candidatePreview(@PathVariable("ruleId") Long ruleId,
            @RequestParam("targetDeptId") Long targetDeptId, HttpServletRequest request)
    {
        return success(approvalService.previewCandidates(
                ruleId, targetDeptId, resolveShopDeptId(request)));
    }
}
