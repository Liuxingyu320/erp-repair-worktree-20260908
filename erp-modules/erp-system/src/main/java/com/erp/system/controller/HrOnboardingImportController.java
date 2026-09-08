package com.erp.system.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.erp.common.core.web.controller.BaseController;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.system.domain.vo.HrOnboardingImportConfirmRequest;
import com.erp.system.service.IHrOnboardingImportService;

@RestController
@RequestMapping("/hr/onboarding/import")
public class HrOnboardingImportController extends BaseController
{
    @Autowired private IHrOnboardingImportService importService;

    @RequiresPermissions("hr:onboarding:import:preview")
    @Log(title = "人事入职导入", businessType = BusinessType.IMPORT,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/preview")
    public AjaxResult preview(@RequestParam("file") MultipartFile file)
    {
        return success(importService.preview(file, SecurityUtils.getUsername()));
    }

    @RequiresPermissions("hr:onboarding:import:preview")
    @GetMapping("/{batchId}")
    public AjaxResult get(@PathVariable Long batchId)
    {
        return success(importService.getBatch(batchId));
    }

    @RequiresPermissions("hr:onboarding:import:confirm")
    @Log(title = "人事入职导入", businessType = BusinessType.IMPORT,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/{batchId}/confirm")
    public AjaxResult confirm(@PathVariable Long batchId, @RequestBody HrOnboardingImportConfirmRequest request)
    {
        return success(importService.confirmBatch(batchId, request, SecurityUtils.getUsername()));
    }

    @RequiresPermissions("hr:onboarding:import:template")
    @Log(title = "人事入职导入", businessType = BusinessType.EXPORT,
            isSaveRequestData = false, isSaveResponseData = false)
    @GetMapping("/template")
    public void template(HttpServletResponse response)
    {
        importService.writeTemplate(response);
    }

    @RequiresPermissions("hr:onboarding:import:preview")
    @Log(title = "人事入职导入", businessType = BusinessType.EXPORT,
            isSaveRequestData = false, isSaveResponseData = false)
    @GetMapping("/{batchId}/errors")
    public void errors(@PathVariable Long batchId, HttpServletResponse response)
    {
        importService.writeErrorRows(batchId, response);
    }
}
