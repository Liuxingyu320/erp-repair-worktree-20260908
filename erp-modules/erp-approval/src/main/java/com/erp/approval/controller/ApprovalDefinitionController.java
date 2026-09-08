package com.erp.approval.controller;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.erp.approval.domain.ApprovalRule;
import com.erp.approval.domain.ApprovalTemplate;
import com.erp.approval.domain.dto.ApprovalDraftCreateRequest;
import com.erp.approval.domain.dto.ApprovalPreviewRequest;
import com.erp.approval.domain.dto.ApprovalPublishRequest;
import com.erp.approval.domain.dto.ApprovalRuleSaveRequest;
import com.erp.approval.domain.dto.ApprovalVersionSaveRequest;
import com.erp.approval.service.ApprovalDefinitionService;
import com.erp.common.core.web.controller.BaseController;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.core.web.page.TableDataInfo;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.common.security.annotation.IdempotentSubmit;
import com.erp.common.security.utils.SecurityUtils;

@RestController
@RequestMapping
public class ApprovalDefinitionController extends BaseController
{
    private final ApprovalDefinitionService definitionService;
    public ApprovalDefinitionController(ApprovalDefinitionService definitionService)
    {
        this.definitionService = definitionService;
    }

    @RequiresPermissions("approval:template:list")
    @GetMapping("/templates")
    public TableDataInfo templates(ApprovalTemplate filter,
            HttpServletResponse response)
    {
        disableCaching(response);
        startPage();
        return getDataTable(definitionService.listTemplates(filter));
    }

    @RequiresPermissions("approval:template:query")
    @GetMapping("/templates/{id}")
    public AjaxResult template(@PathVariable Long id,
            HttpServletResponse response)
    {
        disableCaching(response);
        return success(definitionService.getTemplate(id));
    }

    @RequiresPermissions("approval:template:list")
    @GetMapping("/rules")
    public TableDataInfo rules(ApprovalRule filter,
            HttpServletResponse response)
    {
        disableCaching(response);
        startPage();
        return getDataTable(definitionService.listRules(filter));
    }

    @RequiresPermissions("approval:template:query")
    @GetMapping("/rules/{id}")
    public AjaxResult rule(@PathVariable Long id,
            HttpServletResponse response)
    {
        disableCaching(response);
        return success(definitionService.getRule(id));
    }

    @RequiresPermissions("approval:template:edit")
    @IdempotentSubmit(timeout = 30)
    @PostMapping("/rules")
    public AjaxResult createRule(@Valid @RequestBody ApprovalRuleSaveRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        return success(definitionService.createRule(request,
                SecurityUtils.getUsername()));
    }

    @RequiresPermissions("approval:template:edit")
    @IdempotentSubmit(timeout = 30)
    @PutMapping("/rules/{id}")
    public AjaxResult updateRule(@PathVariable Long id,
            @Valid @RequestBody ApprovalRuleSaveRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        return success(definitionService.updateRule(id, request,
                SecurityUtils.getUsername()));
    }

    @RequiresPermissions("approval:template:edit")
    @IdempotentSubmit(timeout = 30)
    @PostMapping("/rules/{id}/drafts")
    public AjaxResult draft(@PathVariable Long id,
            @RequestBody(required = false) ApprovalDraftCreateRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        return success(definitionService.createDraft(id,
                request == null ? null : request.getSourceVersionId(),
                SecurityUtils.getUsername()));
    }

    @RequiresPermissions("approval:template:edit")
    @IdempotentSubmit(timeout = 30)
    @PutMapping("/versions/{id}")
    public AjaxResult saveVersion(@PathVariable Long id,
            @Valid @RequestBody ApprovalVersionSaveRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        return success(definitionService.saveDraft(id, request,
                SecurityUtils.getUsername()));
    }

    @RequiresPermissions("approval:template:publish")
    @IdempotentSubmit(timeout = 30)
    @PostMapping("/versions/{id}/publish")
    public AjaxResult publish(@PathVariable Long id,
            @Valid @RequestBody ApprovalPublishRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        return success(definitionService.publish(id,
                request.getExpectedLockVersion(), request.getRemark(),
                SecurityUtils.getUserId(), SecurityUtils.getUsername()));
    }

    @RequiresPermissions("approval:template:query")
    @PostMapping("/versions/{id}/preview")
    public AjaxResult preview(@PathVariable Long id,
            @RequestBody ApprovalPreviewRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        return success(definitionService.preview(id, request));
    }

    @RequiresPermissions("approval:template:edit")
    @IdempotentSubmit(timeout = 30)
    @PostMapping("/rules/{id}/disable")
    public AjaxResult disable(@PathVariable Long id,
            @Valid @RequestBody ApprovalPublishRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        definitionService.disableRule(id, request.getExpectedLockVersion(),
                request.getRemark(), SecurityUtils.getUsername());
        return success();
    }

    private static void disableCaching(HttpServletResponse response)
    {
        response.setHeader("Cache-Control", "no-store, max-age=0");
        response.setHeader("Pragma", "no-cache");
    }
}
