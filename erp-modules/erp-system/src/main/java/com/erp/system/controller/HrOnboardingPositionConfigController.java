package com.erp.system.controller;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.erp.common.core.web.controller.BaseController;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.core.web.page.TableDataInfo;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.system.domain.HrOnboardingPositionConfig;
import com.erp.system.domain.vo.HrOnboardingVersionRequest;
import com.erp.system.service.IHrOnboardingPositionConfigService;

@RestController
@RequestMapping("/hr/onboarding/config")
public class HrOnboardingPositionConfigController extends BaseController
{
    private final IHrOnboardingPositionConfigService service;

    public HrOnboardingPositionConfigController(IHrOnboardingPositionConfigService service)
    {
        this.service = service;
    }

    @RequiresPermissions("hr:onboarding:config")
    @GetMapping("/list")
    public TableDataInfo list(HrOnboardingPositionConfig query)
    {
        startPage();
        List<HrOnboardingPositionConfig> rows = service.list(
                query == null ? new HrOnboardingPositionConfig() : query);
        return getDataTable(rows);
    }

    @RequiresPermissions("hr:onboarding:config")
    @GetMapping("/{id}")
    public AjaxResult get(@PathVariable Long id) { return success(service.get(id)); }

    @RequiresPermissions("hr:onboarding:config")
    @Log(title = "岗位入职配置", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult create(@RequestBody HrOnboardingPositionConfig input)
    {
        return success(service.create(input, SecurityUtils.getUsername()));
    }

    @RequiresPermissions("hr:onboarding:config")
    @Log(title = "岗位入职配置", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}")
    public AjaxResult update(@PathVariable Long id, @RequestBody HrOnboardingPositionConfig input)
    {
        return success(service.update(id, input, SecurityUtils.getUsername()));
    }

    @RequiresPermissions("hr:onboarding:config")
    @Log(title = "岗位入职配置", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/disable")
    public AjaxResult disable(@PathVariable Long id, @RequestBody HrOnboardingVersionRequest input)
    {
        service.disable(id, input == null ? null : input.getVersion(), SecurityUtils.getUsername());
        return success();
    }

    @RequiresPermissions("hr:onboarding:config")
    @GetMapping("/options")
    public AjaxResult options() { return success(service.options()); }
}
