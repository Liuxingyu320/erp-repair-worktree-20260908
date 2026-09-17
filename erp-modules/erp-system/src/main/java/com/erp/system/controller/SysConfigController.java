package com.erp.system.controller;

import java.util.List;
import java.util.stream.Collectors;
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
import org.springframework.web.bind.annotation.RestController;
import com.erp.common.core.domain.R;
import com.erp.common.core.constant.HttpStatus;
import com.erp.common.core.utils.poi.ExcelUtil;
import com.erp.common.core.web.controller.BaseController;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.core.web.page.TableDataInfo;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.InnerAuth;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.system.domain.SysConfig;
import com.erp.system.config.SysConfigDescriptorRegistry;
import com.erp.system.service.ISysConfigService;
import com.erp.system.domain.vo.SysConfigListVo;
import com.erp.system.domain.vo.SysConfigExportVo;
import com.erp.system.service.support.SysConfigSensitivityPolicy;
import com.github.pagehelper.PageInfo;

/**
 * 参数配置 信息操作处理
 * 
 * @author erp
 */
@RestController
@RequestMapping("/config")
public class SysConfigController extends BaseController
{
    @Autowired
    private ISysConfigService configService;

    @Autowired
    private SysConfigSensitivityPolicy sensitivityPolicy;

    @Autowired
    private SysConfigDescriptorRegistry configDescriptorRegistry;

    @RequiresPermissions("system:config:list")
    @GetMapping("/descriptors")
    public AjaxResult descriptors()
    {
        return success(configDescriptorRegistry.registeredDescriptors());
    }

    /**
     * 获取参数配置列表
     */
    @RequiresPermissions("system:config:list")
    @GetMapping("/list")
    public TableDataInfo list(SysConfig config)
    {
        startPage();
        List<SysConfig> list = configService.selectConfigList(config);
        long total = new PageInfo<>(list).getTotal();
        List<SysConfigListVo> safeRows = list.stream().map(sensitivityPolicy::toListVo)
                .collect(Collectors.toList());
        TableDataInfo result = new TableDataInfo(safeRows, total);
        result.setCode(HttpStatus.SUCCESS);
        result.setMsg("查询成功");
        return result;
    }

    @Log(title = "参数管理", businessType = BusinessType.EXPORT,
            isSaveRequestData = false, isSaveResponseData = false)
    @RequiresPermissions("system:config:export")
    @PostMapping("/export")
    public void export(HttpServletResponse response, SysConfig config)
    {
        List<SysConfig> list = configService.selectConfigList(config);
        List<SysConfigExportVo> safeRows = list.stream().map(sensitivityPolicy::toExportVo)
                .collect(Collectors.toList());
        ExcelUtil<SysConfigExportVo> util = new ExcelUtil<SysConfigExportVo>(SysConfigExportVo.class);
        util.exportExcel(response, safeRows, "参数数据");
    }

    /**
     * 根据参数编号获取详细信息
     */
    @RequiresPermissions("system:config:query")
    @GetMapping(value = "/{configId}")
    public AjaxResult getInfo(@PathVariable Long configId)
    {
        return success(sensitivityPolicy.toDetailVo(configService.selectConfigById(configId)));
    }

    /**
     * 根据参数键名查询参数值
     */
    @Deprecated
    @RequiresPermissions("system:config:query")
    @GetMapping(value = "/configKey/{configKey}")
    public AjaxResult getConfigKey(@PathVariable String configKey)
    {
        return success(configService.selectConfigByKeyForExternal(configKey));
    }

    /**
     * 内部服务根据参数键名查询参数值
     */
    @InnerAuth
    @GetMapping(value = "/inner/configKey/{configKey}")
    public R<String> innerConfigKey(@PathVariable String configKey)
    {
        return R.ok(configService.selectConfigByKey(configKey));
    }

    /**
     * 新增参数配置
     */
    @RequiresPermissions("system:config:add")
    @Log(title = "参数管理", businessType = BusinessType.INSERT,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody SysConfig config)
    {
        if (!configService.checkConfigKeyUnique(config))
        {
            return error("新增参数'" + config.getConfigName() + "'失败，参数键名已存在");
        }
        config.setCreateBy(SecurityUtils.getUsername());
        return toAjax(configService.insertConfig(config));
    }

    /**
     * 修改参数配置
     */
    @RequiresPermissions("system:config:edit")
    @Log(title = "参数管理", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody SysConfig config)
    {
        if (!configService.checkConfigKeyUnique(config))
        {
            return error("修改参数'" + config.getConfigName() + "'失败，参数键名已存在");
        }
        config.setUpdateBy(SecurityUtils.getUsername());
        return toAjax(configService.updateConfig(config));
    }

    /**
     * 删除参数配置
     */
    @RequiresPermissions("system:config:remove")
    @Log(title = "参数管理", businessType = BusinessType.DELETE,
            isSaveRequestData = false, isSaveResponseData = false)
    @DeleteMapping("/{configIds}")
    public AjaxResult remove(@PathVariable Long[] configIds)
    {
        configService.deleteConfigByIds(configIds);
        return success();
    }

    /**
     * 刷新参数缓存
     */
    @RequiresPermissions("system:config:refresh")
    @Log(title = "参数管理", businessType = BusinessType.CLEAN,
            isSaveRequestData = false, isSaveResponseData = false)
    @DeleteMapping("/refreshCache")
    public AjaxResult refreshCache()
    {
        configService.resetConfigCache();
        return success();
    }
}
