package com.erp.system.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.erp.common.core.web.controller.BaseController;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.core.web.page.TableDataInfo;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.system.domain.vo.HrEmployeeListVo;
import com.erp.system.domain.vo.HrEmployeeQuery;
import com.erp.system.service.IHrEmployeeProfileService;
import com.erp.system.service.impl.HrCompletenessService;

@RestController
@RequestMapping("/hr/completeness")
public class HrCompletenessController extends BaseController
{
    @Autowired private IHrEmployeeProfileService employeeService;
    @Autowired private HrCompletenessService completenessService;

    @RequiresPermissions("hr:completeness:list")
    @GetMapping("/summary")
    public AjaxResult summary(HrEmployeeQuery query){return success(employeeService.summary(query));}

    @RequiresPermissions("hr:completeness:list")
    @GetMapping("/employees")
    public TableDataInfo employees(HrEmployeeQuery query)
    {
        if(query==null)query=new HrEmployeeQuery();
        if("MISSING".equalsIgnoreCase(query.getAccountConfigurationStatus()))
            query.setAccountConfigurationStatus("MISSING");
        if(!query.hasCompletenessFilter())startPage();
        List<? extends HrEmployeeListVo> rows=completenessService.employees(query);
        return getDataTable(rows);
    }

    @RequiresPermissions("hr:completeness:list")
    @GetMapping("/departments")
    public AjaxResult departments(HrEmployeeQuery query)
    {return success(employeeService.completenessDepartments(query));}
}
