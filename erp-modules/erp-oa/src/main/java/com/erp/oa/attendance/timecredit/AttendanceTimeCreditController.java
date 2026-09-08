package com.erp.oa.attendance.timecredit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.IdempotentSubmit;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.oa.attendance.timecredit.AttendanceTimeCreditRequests.Apply;
import com.erp.oa.attendance.timecredit.AttendanceTimeCreditRequests.Reverse;
import com.erp.oa.controller.OaBaseController;

@Validated
@RestController
@RequestMapping("/attendance-v2/time-credits")
@RequiresPermissions("oa:attendance:time-credit:manage")
public class AttendanceTimeCreditController extends OaBaseController
{
    private final AttendanceTimeCreditService service;

    public AttendanceTimeCreditController(AttendanceTimeCreditService service)
    { this.service = service; }

    @GetMapping("/context/{targetDayResultId}")
    public AjaxResult context(@PathVariable Long targetDayResultId,
            HttpServletRequest request)
    {
        return success(service.context(targetDayResultId,
                resolveAttendanceShopDeptId(request)));
    }

    @IdempotentSubmit(timeout = 30)
    @Log(title = "加班抵扣早退", businessType = BusinessType.INSERT,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/adjustments")
    public AjaxResult apply(@Valid @RequestBody Apply body,
            HttpServletRequest request)
    {
        return success(service.apply(body, resolveAttendanceShopDeptId(request)));
    }

    @IdempotentSubmit(timeout = 30)
    @Log(title = "撤销加班抵扣早退", businessType = BusinessType.INSERT,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/adjustments/{adjustmentId}/reverse")
    public AjaxResult reverse(@PathVariable Long adjustmentId,
            @Valid @RequestBody Reverse body, HttpServletRequest request)
    {
        return success(service.reverse(adjustmentId, body,
                resolveAttendanceShopDeptId(request)));
    }
}
