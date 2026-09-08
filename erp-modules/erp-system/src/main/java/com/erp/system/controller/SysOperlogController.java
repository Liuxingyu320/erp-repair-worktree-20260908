package com.erp.system.controller;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.erp.common.core.utils.poi.ExcelUtil;
import com.erp.common.core.constant.HttpStatus;
import com.erp.common.core.web.controller.BaseController;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.core.web.page.TableDataInfo;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.InnerAuth;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.system.api.domain.SysOperLog;
import com.erp.system.domain.vo.SysOperLogExportVo;
import com.erp.system.domain.vo.SysOperLogListVo;
import com.erp.system.service.ISysOperLogService;

/**
 * 操作日志记录
 * 
 * @author erp
 */
@RestController
@RequestMapping("/operlog")
public class SysOperlogController extends BaseController
{
    @Autowired
    private ISysOperLogService operLogService;

    @RequiresPermissions("system:operlog:list")
    @GetMapping("/list")
    public TableDataInfo list(SysOperLog operLog)
    {
        startPage();
        List<SysOperLogListVo> list = operLogService.selectOperLogSummaryList(operLog);
        return getDataTable(list);
    }

    @Log(title = "操作日志", businessType = BusinessType.EXPORT,
            isSaveRequestData = false, isSaveResponseData = false)
    @RequiresPermissions("system:operlog:export")
    @PostMapping("/export")
    public void export(HttpServletResponse response, SysOperLog operLog)
    {
        List<SysOperLogExportVo> list = operLogService.selectOperLogExportList(operLog);
        ExcelUtil<SysOperLogExportVo> util = new ExcelUtil<>(SysOperLogExportVo.class);
        util.exportExcel(response, list, "操作日志");
    }

    @RequiresPermissions("system:operlog:detail")
    @GetMapping("/{operId}")
    public AjaxResult getInfo(@PathVariable Long operId)
    {
        return success(operLogService.selectOperLogDetailById(operId));
    }

    @Log(title = "操作日志", businessType = BusinessType.DELETE,
            isSaveRequestData = false, isSaveResponseData = false)
    @RequiresPermissions("system:operlog:remove")
    @DeleteMapping("/{operIds}")
    public AjaxResult remove(@PathVariable Long[] operIds)
    {
        return toAjax(operLogService.deleteOperLogByIds(operIds));
    }

    @RequiresPermissions("system:operlog:remove")
    @Log(title = "操作日志", businessType = BusinessType.CLEAN,
            isSaveRequestData = false, isSaveResponseData = false)
    @DeleteMapping("/clean")
    public AjaxResult clean()
    {
        return AjaxResult.error(HttpStatus.CONFLICT, "一键清空已停用，请按审计留存制度完成归档与复核");
    }

    @InnerAuth
    @PostMapping
    public AjaxResult add(@RequestBody SysOperLog operLog)
    {
        return toAjax(operLogService.insertOperlog(operLog));
    }
}
