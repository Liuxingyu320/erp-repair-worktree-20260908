package com.erp.system.controller;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.erp.common.core.utils.poi.ExcelUtil;
import com.erp.common.core.web.controller.BaseController;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.core.web.page.TableDataInfo;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.system.domain.vo.HrMasterDataIssueQuery;
import com.erp.system.domain.vo.HrMasterDataIssueVo;
import com.erp.system.service.impl.HrMasterDataReadinessService;

/** Read-only endpoints for the HR master-data governance queue. */
@RestController
@RequestMapping("/hr/completeness/master-data")
public class HrMasterDataReadinessController extends BaseController
{
    private final HrMasterDataReadinessService readinessService;

    public HrMasterDataReadinessController(HrMasterDataReadinessService readinessService)
    {
        this.readinessService=readinessService;
    }

    @RequiresPermissions("hr:masterData:list")
    @GetMapping("/summary")
    public AjaxResult summary(HrMasterDataIssueQuery query)
    {
        return success(readinessService.summary(query));
    }

    @RequiresPermissions("hr:masterData:list")
    @GetMapping("/issues")
    public TableDataInfo issues(HrMasterDataIssueQuery query)
    {
        return getDataTable(readinessService.issues(query));
    }

    @RequiresPermissions("hr:masterData:list")
    @GetMapping("/codes")
    public AjaxResult codes()
    {
        return success(readinessService.definitions());
    }

    @RequiresPermissions("hr:masterData:export")
    @PostMapping("/export")
    public void export(HttpServletResponse response,HrMasterDataIssueQuery query)
    {
        List<HrMasterDataIssueVo> rows=readinessService.issues(query);
        new ExcelUtil<HrMasterDataIssueVo>(HrMasterDataIssueVo.class)
                .exportExcel(response,rows,"人事主数据问题");
    }
}
