package com.erp.approval.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.erp.approval.domain.ApprovalValidationRun;
import com.erp.approval.domain.dto.ApprovalValidationRequest;
import com.erp.approval.mapper.ApprovalValidationMapper;
import com.erp.approval.service.ApprovalCoverageValidationService;
import com.erp.common.core.web.controller.BaseController;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.core.web.page.TableDataInfo;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.common.security.utils.SecurityUtils;

@RestController
@RequestMapping("/validation")
public class ApprovalValidationController extends BaseController
{
    private final ApprovalValidationMapper validationMapper;
    private final ApprovalCoverageValidationService validationService;

    public ApprovalValidationController(ApprovalValidationMapper validationMapper,
            ApprovalCoverageValidationService validationService)
    {
        this.validationMapper = validationMapper;
        this.validationService = validationService;
    }

    @RequiresPermissions("approval:validation:list")
    @GetMapping("/runs")
    public TableDataInfo runs(ApprovalValidationRun filter)
    {
        startPage();
        return getDataTable(validationMapper.selectValidationRuns(filter));
    }

    @RequiresPermissions("approval:validation:list")
    @GetMapping("/runs/{id}")
    public AjaxResult detail(@PathVariable Long id)
    {
        return success(validationService.getDetail(id));
    }

    @RequiresPermissions("approval:validation:run")
    @PostMapping("/run")
    public AjaxResult run(@Valid @RequestBody ApprovalValidationRequest request)
    {
        return success(validationService.validate(request.getVersionId(),
                request.getValidationType(), SecurityUtils.getUserId(),
                SecurityUtils.getUsername()));
    }
}
