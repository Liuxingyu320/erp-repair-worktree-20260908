package com.erp.system.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.web.controller.BaseController;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.core.web.page.TableDataInfo;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.common.security.annotation.Logical;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.system.domain.vo.HrOnboardingCancelRequest;
import com.erp.system.domain.vo.HrOnboardingCreateRequest;
import com.erp.system.domain.vo.HrOnboardingConfirmRequest;
import com.erp.system.domain.vo.HrOnboardingListVo;
import com.erp.system.domain.vo.HrOnboardingOwnerOptionVo;
import com.erp.system.domain.vo.HrOnboardingOwnerQuery;
import com.erp.system.domain.vo.HrOnboardingQuery;
import com.erp.system.domain.vo.HrOnboardingUpdateRequest;
import com.erp.system.domain.vo.HrOnboardingVersionRequest;
import com.erp.system.service.IHrOnboardingService;
import com.erp.system.service.impl.HrOnboardingConfirmationService;
import com.erp.system.service.impl.HrOnboardingConflictService;

@RestController
@RequestMapping("/hr/onboarding")
public class HrOnboardingController extends BaseController
{
    @Autowired
    private IHrOnboardingService onboardingService;

    @Autowired
    private HrOnboardingConfirmationService confirmationService;

    @Autowired
    private HrOnboardingConflictService conflictService;

    @RequiresPermissions("hr:onboarding:list")
    @GetMapping("/list")
    public TableDataInfo list(HrOnboardingQuery query)
    {
        if (query == null) query = new HrOnboardingQuery();
        startPage();
        List<HrOnboardingListVo> rows = onboardingService.list(query);
        return getDataTable(rows);
    }

    @RequiresPermissions("hr:onboarding:workbench")
    @GetMapping("/summary")
    public AjaxResult summary(HrOnboardingQuery query)
    {
        return success(onboardingService.summary(query == null ? new HrOnboardingQuery() : query));
    }

    @RequiresPermissions(value = {
            "hr:onboarding:list", "hr:onboarding:add", "hr:onboarding:edit"
    }, logical = Logical.OR)
    @GetMapping("/form-options")
    public AjaxResult formOptions(
            @RequestParam(defaultValue = "true") Boolean includeOwners,
            @RequestParam(defaultValue = "true") Boolean includeSupervisors)
    {
        return success(onboardingService.formOptions(
                !Boolean.FALSE.equals(includeOwners),
                !Boolean.FALSE.equals(includeSupervisors)));
    }

    @RequiresPermissions(value = {
            "hr:onboarding:list", "hr:onboarding:add", "hr:onboarding:edit"
    }, logical = Logical.OR)
    @GetMapping("/owner-options")
    public TableDataInfo ownerOptions(HrOnboardingOwnerQuery query,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize)
    {
        if (pageNum == null || pageNum < 1) throw new ServiceException("页码必须从1开始");
        if (pageSize == null || pageSize < 1 || pageSize > 50)
            throw new ServiceException("每页数量必须在1到50之间");
        List<HrOnboardingOwnerOptionVo> rows = onboardingService.ownerOptions(
                query, pageNum, pageSize);
        return getDataTable(rows);
    }

    @RequiresPermissions("hr:onboarding:add")
    @Log(title = "人事入职新建", businessType = BusinessType.INSERT,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping
    public AjaxResult create(@RequestBody HrOnboardingCreateRequest input)
    {
        return success(onboardingService.create(input, SecurityUtils.getUsername()));
    }

    @RequiresPermissions("hr:onboarding:query")
    @GetMapping("/{id}")
    public AjaxResult get(@PathVariable Long id) { return success(onboardingService.get(id)); }

    @RequiresPermissions("hr:onboarding:confirm")
    @GetMapping("/{id}/conflicts")
    public AjaxResult conflicts(@PathVariable Long id)
    {
        return success(conflictService.preview(id));
    }

    @RequiresPermissions("hr:onboarding:confirm")
    @Log(title = "人事确认入职", businessType = BusinessType.OTHER,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/{id}/confirm")
    public AjaxResult confirm(@PathVariable Long id, @RequestBody HrOnboardingConfirmRequest input)
    {
        return success(confirmationService.confirm(id, input, SecurityUtils.getUsername()));
    }

    @RequiresPermissions("hr:onboarding:edit")
    @Log(title = "人事入职编辑", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping("/{id}")
    public AjaxResult update(@PathVariable Long id, @RequestBody HrOnboardingUpdateRequest input)
    {
        return success(onboardingService.update(id, input, SecurityUtils.getUsername()));
    }

    @RequiresPermissions("hr:onboarding:ready")
    @Log(title = "人事入职状态", businessType = BusinessType.OTHER,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/{id}/ready")
    public AjaxResult ready(@PathVariable Long id, @RequestBody HrOnboardingVersionRequest input)
    {
        return success(onboardingService.markReady(id, input.getVersion(), SecurityUtils.getUsername()));
    }

    @RequiresPermissions("hr:onboarding:return")
    @Log(title = "人事入职状态", businessType = BusinessType.OTHER,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/{id}/return-to-draft")
    public AjaxResult returnToDraft(@PathVariable Long id, @RequestBody HrOnboardingVersionRequest input)
    {
        return success(onboardingService.returnToDraft(id, input.getVersion(), SecurityUtils.getUsername()));
    }

    @RequiresPermissions("hr:onboarding:cancel")
    @Log(title = "人事入职状态", businessType = BusinessType.OTHER,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/{id}/cancel")
    public AjaxResult cancel(@PathVariable Long id, @RequestBody HrOnboardingCancelRequest input)
    {
        return success(onboardingService.cancel(id, input.getVersion(), input.getReason(), SecurityUtils.getUsername()));
    }

    @RequiresPermissions("hr:onboarding:restore")
    @Log(title = "人事入职状态", businessType = BusinessType.OTHER,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/{id}/restore")
    public AjaxResult restore(@PathVariable Long id, @RequestBody HrOnboardingVersionRequest input)
    {
        return success(onboardingService.restore(id, input.getVersion(), SecurityUtils.getUsername()));
    }
}
