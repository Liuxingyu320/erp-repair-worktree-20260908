package com.erp.system.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.erp.common.core.web.controller.BaseController;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.Logical;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.system.api.domain.SysLegalEntity;
import com.erp.system.service.ISysLegalEntityService;

@RestController
@RequestMapping("/legalEntity")
public class SysLegalEntityController extends BaseController
{
    private final ISysLegalEntityService legalEntityService;

    public SysLegalEntityController(ISysLegalEntityService legalEntityService)
    {
        this.legalEntityService = legalEntityService;
    }

    @RequiresPermissions(value = { "system:dept:list", "oa:signCompany:list" }, logical = Logical.OR)
    @GetMapping("/options")
    public AjaxResult options()
    {
        return success(legalEntityService.selectActiveLegalEntities());
    }

    @RequiresPermissions(value = { "system:dept:list", "oa:signCompany:list" }, logical = Logical.OR)
    @GetMapping("/list")
    public AjaxResult list(SysLegalEntity query)
    {
        return success(legalEntityService.selectLegalEntityList(query));
    }

    @RequiresPermissions(value = { "system:dept:query", "oa:signCompany:list" }, logical = Logical.OR)
    @GetMapping("/{legalEntityId}")
    public AjaxResult detail(@PathVariable Long legalEntityId)
    {
        return success(legalEntityService.selectLegalEntityById(legalEntityId));
    }

    @RequiresPermissions(value = { "system:dept:edit", "oa:signCompany:edit" }, logical = Logical.OR)
    @Log(title = "公司主体管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody SysLegalEntity legalEntity)
    {
        legalEntity.setLegalEntityId(null);
        legalEntity.setVersion(null);
        return success(legalEntityService.saveLegalEntity(legalEntity));
    }

    @RequiresPermissions(value = { "system:dept:edit", "oa:signCompany:edit" }, logical = Logical.OR)
    @Log(title = "公司主体管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody SysLegalEntity legalEntity)
    {
        return success(legalEntityService.saveLegalEntity(legalEntity));
    }
}
