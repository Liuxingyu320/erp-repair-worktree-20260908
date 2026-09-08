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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.erp.common.core.utils.poi.ExcelUtil;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.core.web.page.TableDataInfo;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.IdempotentSubmit;
import com.erp.common.security.annotation.Logical;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.inventory.domain.InvStockCheck;
import com.erp.inventory.domain.InvStockCheckApprovalInstance;
import com.erp.inventory.domain.InvStockCheckDetail;
import com.erp.inventory.domain.dto.InvStockCheckApprovalRequest;
import com.erp.inventory.domain.dto.InvStockCheckAssignmentRequest;
import com.erp.inventory.service.IInvStockCheckApprovalService;
import com.erp.inventory.service.IInvStockCheckService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.system.api.model.LoginUser;

@RestController
@RequestMapping("/stockCheck")
public class InvStockCheckController extends InvBaseController
{
    private static final String INV_COST_VIEW_PERMISSION = "inv:cost:view";

    @Autowired
    private IInvStockCheckService stockCheckService;

    @Autowired
    private IInvStockCheckApprovalService stockCheckApprovalService;

    @RequiresPermissions("inv:stockCheck:list")
    @GetMapping("/list")
    public TableDataInfo list(InvStockCheck stockCheck, HttpServletRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        startPage();
        List<InvStockCheck> list = stockCheckService.selectCheckList(stockCheck, resolveShopDeptId(request));
        hideCheckCostFieldsIfNeeded(list);
        return getDataTable(list);
    }

    @RequiresPermissions(value = { "inv:stockCheck:query",
            "inv:stockCheck:approve", "inv:stockCheck:submit" },
            logical = Logical.OR)
    @GetMapping("/{checkId}")
    public AjaxResult detail(@PathVariable("checkId") Long checkId, HttpServletRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        return success(hideCheckCostFieldsIfNeeded(
                stockCheckService.getCheckDetail(checkId, resolveShopDeptId(request))));
    }

    @RequiresPermissions("inv:stockCheck:add")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "库存盘点", businessType = BusinessType.INSERT)
    @PostMapping("/create")
    public AjaxResult create(@Validated @RequestBody InvStockCheck stockCheck,
            HttpServletRequest request, HttpServletResponse response)
    {
        disableCaching(response);
        return success(hideCheckCostFieldsIfNeeded(
                stockCheckService.createCheck(stockCheck, resolveShopDeptId(request))));
    }

    @RequiresPermissions(value = { "inv:stockCheck:add", "inv:stockCheck:edit" },
            logical = Logical.OR)
    @GetMapping("/counter-candidates")
    public AjaxResult counterCandidates(
            @RequestParam(value = "warehouseId", required = false) Long warehouseId,
            @RequestParam(value = "keyword", required = false) String keyword,
            HttpServletRequest request, HttpServletResponse response)
    {
        disableCaching(response);
        return success(stockCheckService.selectCounterCandidates(
                warehouseId, keyword, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:stockCheck:edit")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "库存盘点指派", businessType = BusinessType.UPDATE)
    @PutMapping("/{checkId}/assignment")
    public AjaxResult assign(@PathVariable("checkId") Long checkId,
            @Validated @RequestBody InvStockCheckAssignmentRequest assignment,
            HttpServletRequest request, HttpServletResponse response)
    {
        disableCaching(response);
        return success(hideCheckCostFieldsIfNeeded(stockCheckService.assignCounter(
                checkId, assignment, resolveShopDeptId(request))));
    }

    @RequiresPermissions("inv:stockCheck:submit")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "库存盘点录入实盘", businessType = BusinessType.UPDATE)
    @PostMapping("/input/{checkId}")
    public AjaxResult input(@PathVariable("checkId") Long checkId,
            @RequestBody(required = false) InvStockCheck stockCheck, HttpServletRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        return success(hideCheckCostFieldsIfNeeded(stockCheckService.inputActualQty(
                checkId, stockCheck, resolveShopDeptId(request))));
    }

    @RequiresPermissions("inv:stockCheck:submit")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "库存盘点提交", businessType = BusinessType.UPDATE)
    @PostMapping("/submit/{checkId}")
    public AjaxResult submit(@PathVariable("checkId") Long checkId,
            @RequestBody(required = false) InvStockCheck stockCheck, HttpServletRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        Long shopDeptId = resolveShopDeptId(request);
        List<InvStockCheckDetail> details = stockCheck == null ? null : stockCheck.getDetails();
        stockCheckService.submitCheck(checkId, details, shopDeptId);
        return success(hideCheckCostFieldsIfNeeded(
                stockCheckService.getCheckDetail(checkId, shopDeptId)));
    }

    @RequiresPermissions("inv:stockCheck:submit")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "库存盘点重新盘点", businessType = BusinessType.UPDATE)
    @PostMapping("/restart/{checkId}")
    public AjaxResult restart(@PathVariable("checkId") Long checkId, HttpServletRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        Long shopDeptId = resolveShopDeptId(request);
        stockCheckService.restartCheck(checkId, shopDeptId);
        return success(hideCheckCostFieldsIfNeeded(
                stockCheckService.getCheckDetail(checkId, shopDeptId)));
    }

    @RequiresPermissions("inv:stockCheck:approve")
    @GetMapping("/approval/todo")
    public TableDataInfo approvalTodo(InvStockCheck stockCheck, HttpServletRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        startPage();
        List<InvStockCheck> list = stockCheckService.selectApprovalTodoList(
                stockCheck, resolveShopDeptId(request));
        hideCheckCostFieldsIfNeeded(list);
        return getDataTable(list);
    }

    @RequiresPermissions("inv:stockCheck:approve")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "库存盘点审批通过", businessType = BusinessType.UPDATE)
    @PostMapping("/approval/{checkId}/approve")
    public AjaxResult approve(@PathVariable("checkId") Long checkId,
            @RequestBody InvStockCheckApprovalRequest approvalRequest, HttpServletRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        Long shopDeptId = resolveShopDeptId(request);
        stockCheckApprovalService.approve(checkId, approvalRequest, shopDeptId);
        return success(hideCheckCostFieldsIfNeeded(
                stockCheckService.getCheckDetail(checkId, shopDeptId)));
    }

    @RequiresPermissions("inv:stockCheck:approve")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "库存盘点审批驳回", businessType = BusinessType.UPDATE)
    @PostMapping("/approval/{checkId}/reject")
    public AjaxResult reject(@PathVariable("checkId") Long checkId,
            @RequestBody InvStockCheckApprovalRequest approvalRequest, HttpServletRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        Long shopDeptId = resolveShopDeptId(request);
        stockCheckApprovalService.reject(checkId, approvalRequest, shopDeptId);
        return success(hideCheckCostFieldsIfNeeded(
                stockCheckService.getCheckDetail(checkId, shopDeptId)));
    }

    @RequiresPermissions(value = { "inv:stockCheck:query",
            "inv:stockCheck:approve", "inv:stockCheck:submit" },
            logical = Logical.OR)
    @GetMapping("/{checkId}/approval-track")
    public AjaxResult approvalTrack(@PathVariable("checkId") Long checkId,
            HttpServletRequest request, HttpServletResponse response)
    {
        disableCaching(response);
        return success(hideApprovalCostFieldsIfNeeded(
                stockCheckApprovalService.selectTrack(checkId, resolveShopDeptId(request))));
    }

    @RequiresPermissions("inv:stockCheck:remove")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "库存盘点取消", businessType = BusinessType.UPDATE)
    @PostMapping("/cancel/{checkId}")
    public AjaxResult cancel(@PathVariable("checkId") Long checkId, HttpServletRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        stockCheckService.cancelCheck(checkId, resolveShopDeptId(request));
        return success("已取消");
    }

    @RequiresPermissions("inv:stockCheck:submit")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "库存盘点撤回审批", businessType = BusinessType.UPDATE)
    @PostMapping("/{checkId}/withdraw")
    public AjaxResult withdraw(@PathVariable("checkId") Long checkId,
            HttpServletRequest request, HttpServletResponse response)
    {
        disableCaching(response);
        return success(stockCheckService.withdrawApproval(checkId,
                resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:stockCheck:remove")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "库存盘点删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{checkIds}")
    public AjaxResult delete(@PathVariable("checkIds") Long[] checkIds, HttpServletRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        stockCheckService.deleteCheck(checkIds, resolveShopDeptId(request));
        return success("删除成功");
    }

    @RequiresPermissions("inv:stockCheck:export")
    @Log(title = "库存盘点", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, InvStockCheck stockCheck, HttpServletRequest request)
    {
        disableCaching(response);
        List<InvStockCheck> list = stockCheckService.selectCheckList(stockCheck, resolveShopDeptId(request));
        hideCheckCostFieldsIfNeeded(list);
        ExcelUtil<InvStockCheck> util = new ExcelUtil<>(InvStockCheck.class);
        util.exportExcel(response, list, "库存盘点数据");
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

    private void hideCheckCostFieldsIfNeeded(List<InvStockCheck> checks)
    {
        if (hasCostViewPermission() || checks == null)
        {
            return;
        }
        checks.forEach(this::hideCheckCostFields);
    }

    private InvStockCheck hideCheckCostFieldsIfNeeded(InvStockCheck check)
    {
        if (!hasCostViewPermission())
        {
            hideCheckCostFields(check);
        }
        return check;
    }

    private void hideCheckCostFields(InvStockCheck check)
    {
        if (check == null || check.getDetails() == null)
        {
            return;
        }
        check.getDetails().forEach(detail -> {
            if (detail != null)
            {
                detail.setCostPrice(null);
            }
        });
    }

    private List<InvStockCheckApprovalInstance> hideApprovalCostFieldsIfNeeded(
            List<InvStockCheckApprovalInstance> instances)
    {
        if (hasCostViewPermission() || instances == null)
        {
            return instances;
        }
        instances.forEach(instance -> {
            if (instance != null)
            {
                instance.setDetailSnapshot(null);
            }
        });
        return instances;
    }

    private void disableCaching(HttpServletResponse response)
    {
        response.setHeader("Cache-Control", "no-store, max-age=0");
        response.setHeader("Pragma", "no-cache");
    }
}
