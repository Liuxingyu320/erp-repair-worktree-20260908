package com.erp.oa.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.RequiresLogin;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.oa.domain.dto.OaSignOnboardDataRequestSendRequest;
import com.erp.oa.domain.dto.OaSignOnboardDataReviewRequest;
import com.erp.oa.domain.dto.OaSignOnboardDataSubmitRequest;
import com.erp.oa.domain.dto.OaSignOnboardGenerateRequest;
import com.erp.oa.domain.dto.OaSignOnboardImportRowUpdateRequest;
import com.erp.oa.service.impl.OaSignOnboardDataRequestService;
import com.erp.oa.service.impl.OaSignOnboardGenerationService;
import com.erp.oa.service.impl.OaSignOnboardImportService;

/** New onboarding Excel entry. The old preview/initiate endpoints are intentionally absent. */
@RestController
@RequestMapping("/signTask/onboard")
@ConditionalOnProperty(prefix = "oa.sign.excel-import", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class OaSignOnboardImportController extends OaBaseController
{
    private final OaSignOnboardImportService importService;
    private final OaSignOnboardDataRequestService dataRequestService;
    private final OaSignOnboardGenerationService generationService;

    public OaSignOnboardImportController(OaSignOnboardImportService importService,
            OaSignOnboardDataRequestService dataRequestService,
            OaSignOnboardGenerationService generationService)
    {
        this.importService = importService;
        this.dataRequestService = dataRequestService;
        this.generationService = generationService;
    }

    @RequiresPermissions("oa:signTask:send")
    @Log(title = "入职签约Excel预览", businessType = BusinessType.IMPORT,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AjaxResult preview(@RequestPart("file") MultipartFile file,
            @RequestParam(value = "employeeIds", required = false, defaultValue = "[]") String employeeIds,
            @RequestParam(value = "matchMode", required = false,
                    defaultValue = OaSignOnboardImportService.MATCH_MODE_MANUAL_SELECTED) String matchMode,
            HttpServletRequest request)
    {
        return success(importService.preview(file, importService.parseEmployeeIds(employeeIds),
                matchMode, resolveSignScopeDeptId(request)));
    }

    @RequiresPermissions("oa:signTask:send")
    @GetMapping("/import/{batchId}")
    public AjaxResult batch(@PathVariable Long batchId, HttpServletRequest request)
    {
        return success(importService.detail(batchId, resolveSignScopeDeptId(request)));
    }

    @RequiresPermissions("oa:signTask:send")
    @Log(title = "修改入职签约导入行", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping("/import/{batchId}/rows/{rowId}")
    public AjaxResult updateRow(@PathVariable Long batchId, @PathVariable Long rowId,
            @Validated @RequestBody OaSignOnboardImportRowUpdateRequest action,
            HttpServletRequest request)
    {
        return success(importService.updateRow(batchId, rowId, action,
                resolveSignScopeDeptId(request)));
    }

    @RequiresPermissions("oa:signTask:send")
    @Log(title = "发送入职签约资料补充任务", businessType = BusinessType.INSERT,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/import/{batchId}/data-request/send")
    public AjaxResult sendDataRequest(@PathVariable Long batchId,
            @Validated @RequestBody OaSignOnboardDataRequestSendRequest action,
            HttpServletRequest request)
    {
        return success(importService.sendDataRequests(batchId, action,
                resolveSignScopeDeptId(request)));
    }

    @RequiresPermissions("oa:signTask:send")
    @Log(title = "审核入职签约资料补充", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/data-request/{requestId}/review")
    public AjaxResult review(@PathVariable Long requestId,
            @Validated @RequestBody OaSignOnboardDataReviewRequest action,
            HttpServletRequest request)
    {
        return success(dataRequestService.review(requestId, action,
                resolveSignScopeDeptId(request)));
    }

    @RequiresPermissions("oa:signTask:send")
    @Log(title = "生成入职签约合同", businessType = BusinessType.INSERT,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/import/{batchId}/generate")
    public AjaxResult generate(@PathVariable Long batchId,
            @Validated @RequestBody OaSignOnboardGenerateRequest action,
            HttpServletRequest request)
    {
        return success(generationService.generate(batchId, action,
                resolveSignScopeDeptId(request)));
    }

    @RequiresLogin
    @GetMapping("/data-request/mine")
    public AjaxResult mine()
    {
        return success(dataRequestService.mine());
    }

    @RequiresLogin
    @GetMapping("/data-request/{requestId}")
    public AjaxResult dataRequest(@PathVariable Long requestId, HttpServletRequest request)
    {
        return success(dataRequestService.detail(requestId, resolveSignScopeDeptId(request)));
    }

    @RequiresLogin
    @Log(title = "提交入职签约个人资料", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/data-request/{requestId}/submit")
    public AjaxResult submit(@PathVariable Long requestId,
            @Validated @RequestBody OaSignOnboardDataSubmitRequest action)
    {
        return success(dataRequestService.submit(requestId, action));
    }
}
