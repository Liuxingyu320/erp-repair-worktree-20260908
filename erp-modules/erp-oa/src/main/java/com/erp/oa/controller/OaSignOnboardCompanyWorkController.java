package com.erp.oa.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.IdempotentSubmit;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.oa.domain.dto.OaSignOnboardCompanyWorkRequest;
import com.erp.oa.service.impl.OaSignOnboardCompanyWorkService;

/** Contract-centre bridge for completed signature-first Excel onboarding rows. */
@RestController
@RequestMapping("/signTask/onboard/company-work")
@ConditionalOnProperty(prefix = "oa.sign.excel-import", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class OaSignOnboardCompanyWorkController extends OaBaseController
{
    private final OaSignOnboardCompanyWorkService companyWorkService;

    public OaSignOnboardCompanyWorkController(OaSignOnboardCompanyWorkService companyWorkService)
    {
        this.companyWorkService = companyWorkService;
    }

    @RequiresPermissions("oa:signTask:list")
    @GetMapping
    public AjaxResult list(HttpServletRequest request)
    {
        return success(companyWorkService.list(resolveSignScopeDeptId(request)));
    }

    @RequiresPermissions("oa:signTask:batchFinalize")
    @GetMapping("/options")
    public AjaxResult options(@RequestParam(required = false) Long legalEntityId)
    {
        return success(companyWorkService.options(legalEntityId));
    }

    @RequiresPermissions("oa:signTask:batchFinalize")
    @PostMapping("/preview")
    public AjaxResult preview(@Validated @RequestBody OaSignOnboardCompanyWorkRequest action,
            HttpServletRequest request)
    {
        return success(companyWorkService.preview(action, resolveSignScopeDeptId(request)));
    }

    @RequiresPermissions("oa:signTask:batchFinalize")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "批量选择入职合同公司并盖章", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/execute")
    public AjaxResult execute(@Validated @RequestBody OaSignOnboardCompanyWorkRequest action,
            HttpServletRequest request)
    {
        return success(companyWorkService.execute(action, resolveSignScopeDeptId(request)));
    }
}
