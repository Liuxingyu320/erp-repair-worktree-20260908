package com.erp.oa.controller;

import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.core.utils.poi.ExcelUtil;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.core.web.page.TableDataInfo;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.IdempotentSubmit;
import com.erp.common.security.annotation.RequiresLogin;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.oa.domain.OaSalaryConfig;
import com.erp.oa.domain.OaSalaryRecord;
import com.erp.oa.service.IOaSalaryService;

@RestController
@RequestMapping("/salary")
public class OaSalaryController extends OaBaseController
{
    @Autowired
    private IOaSalaryService salaryService;

    @RequiresPermissions("oa:salary:config")
    @GetMapping("/config")
    public AjaxResult config(Long shopDeptId, HttpServletRequest request)
    {
        return success(salaryService.getConfig(shopDeptId, resolveShopDeptId(request)));
    }

    @RequiresPermissions("oa:salary:config")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "工资参数配置", businessType = BusinessType.UPDATE)
    @PutMapping("/config")
    public AjaxResult saveConfig(@RequestBody OaSalaryConfig config, HttpServletRequest request)
    {
        return success(salaryService.saveConfig(config, resolveShopDeptId(request)));
    }

    @RequiresPermissions("oa:salary:query")
    @GetMapping("/{salaryId}")
    public AjaxResult detail(@PathVariable("salaryId") Long salaryId, HttpServletRequest request)
    {
        return success(salaryService.getRecordById(salaryId, resolveShopDeptId(request)));
    }

    @RequiresLogin
    @GetMapping("/my")
    public TableDataInfo myList(OaSalaryRecord record, HttpServletRequest request)
    {
        startPage();
        List<OaSalaryRecord> list = salaryService.selectMyRecords(record, resolveShopDeptId(request));
        return getDataTable(list);
    }

    @RequiresPermissions("oa:salary:list")
    @GetMapping("/list")
    public TableDataInfo list(OaSalaryRecord record, HttpServletRequest request)
    {
        startPage();
        List<OaSalaryRecord> list = salaryService.selectAllRecords(record, resolveShopDeptId(request));
        return getDataTable(list);
    }

    @RequiresPermissions("oa:salary:calculate")
    @GetMapping("/attendance-preflight")
    public AjaxResult attendancePreflight(Long shopDeptId, String salaryMonth, HttpServletRequest request)
    {
        if (StringUtils.isEmpty(salaryMonth))
        {
            return error("工资月份不能为空");
        }
        return success(salaryService.preflightAttendance(
                shopDeptId, salaryMonth, resolveShopDeptId(request)));
    }

    @RequiresPermissions("oa:salary:calculate")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "工资计算", businessType = BusinessType.INSERT)
    @PostMapping("/calculate")
    public AjaxResult calculate(@RequestBody OaSalaryRecord params, HttpServletRequest request)
    {
        Long shopDeptId = params.getShopDeptId();
        String salaryMonth = params.getSalaryMonth();
        if (StringUtils.isEmpty(salaryMonth))
        {
            return error("工资月份不能为空");
        }
        return success(salaryService.calculateSalary(shopDeptId, salaryMonth, resolveShopDeptId(request)));
    }

    @RequiresPermissions("oa:salary:export")
    @Log(title = "工资记录", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, OaSalaryRecord record, HttpServletRequest request)
    {
        List<OaSalaryRecord> list = salaryService.selectAllRecords(record, resolveShopDeptId(request));
        ExcelUtil<OaSalaryRecord> util = new ExcelUtil<>(OaSalaryRecord.class);
        util.exportExcel(response, list, "工资记录");
    }

}
