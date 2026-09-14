package com.erp.system.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.erp.common.core.web.controller.BaseController;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.system.domain.vo.HrEmployeeQuery;
import com.erp.system.service.impl.HrEmployeeAccessService;
import com.erp.system.service.impl.HrSalarySourceService;

/** Metadata only. Amounts continue to use the existing audited sensitive-field reveal endpoint. */
@RestController
@RequestMapping("/hr/employee")
public class HrSalarySourceController extends BaseController
{
    private final HrEmployeeAccessService access;
    private final HrSalarySourceService sources;
    public HrSalarySourceController(HrEmployeeAccessService access, HrSalarySourceService sources)
    { this.access = access; this.sources = sources; }

    @RequiresPermissions("hr:employee:query")
    @GetMapping("/{userId}/salary-source")
    public AjaxResult detail(@PathVariable Long userId)
    {
        HrEmployeeQuery query = new HrEmployeeQuery(); query.setUserId(userId);
        access.findScoped(query);
        var source = sources.current(userId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", source == null ? "MISSING" : sources.matchesProfile(source) ? "CONFIRMED" : "REVIEW_REQUIRED");
        if (source != null)
        {
            data.put("sourceId", source.sourceId); data.put("previousSourceId", source.previousSourceId);
            data.put("sourceType", source.sourceType); data.put("businessId", source.businessId);
            data.put("batchId", source.batchId); data.put("rowId", source.rowId); data.put("rowVersion", source.rowVersion);
            data.put("confirmedAt", source.confirmedAt); data.put("effectiveDate", source.effectiveDate);
            data.put("operatorName", source.operatorName);
            data.put("contracts", sources.contracts(source));
        }
        return success(data);
    }
}
