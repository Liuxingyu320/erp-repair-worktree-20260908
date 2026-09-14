package com.erp.system.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.erp.common.core.web.controller.BaseController;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.system.domain.dto.HrOnboardSalaryArchiveRequest;
import com.erp.system.service.impl.HrOnboardSalaryArchiveService;

@RestController
@RequestMapping("/hr/employee/onboard-salary")
public class HrOnboardSalaryArchiveController extends BaseController
{
    private final HrOnboardSalaryArchiveService service;
    public HrOnboardSalaryArchiveController(HrOnboardSalaryArchiveService service)
    { this.service = service; }

    @RequiresPermissions("oa:signTask:send")
    @PostMapping("/preview")
    public AjaxResult preview(@RequestBody HrOnboardSalaryArchiveRequest request)
    { return success(service.preview(request)); }

    @RequiresPermissions("oa:signTask:send")
    @PostMapping("/status")
    public AjaxResult status(@RequestBody HrOnboardSalaryArchiveRequest request)
    { return success(service.status(request)); }

    @RequiresPermissions("oa:signTask:send")
    @Log(title = "入职合同Excel工资入档", businessType = BusinessType.IMPORT,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/archive")
    public AjaxResult archive(@Valid @RequestBody HrOnboardSalaryArchiveRequest request)
    { return success(service.archive(request)); }
}
