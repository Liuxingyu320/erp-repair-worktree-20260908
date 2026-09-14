package com.erp.oa.attendance.leave.balance;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.oa.controller.OaBaseController;
import com.erp.oa.attendance.leave.balance.AttendanceOvertimeTransferRequests.*;

@RestController
@RequestMapping("/attendance-v2/leave/balance/overtime-transfers")
@RequiresPermissions(AttendanceLeaveBalanceAccess.PREFIX+"convert")
public class AttendanceOvertimeTransferController extends OaBaseController
{
    private final AttendanceOvertimeTransferService service;
    public AttendanceOvertimeTransferController(AttendanceOvertimeTransferService service){this.service=service;}
    @GetMapping("/context/{sourceDayResultId}")
    public AjaxResult context(@PathVariable Long sourceDayResultId,@RequestParam Long leaveTypeId,HttpServletRequest request)
    {return success(service.context(sourceDayResultId,leaveTypeId,resolveAttendanceShopDeptId(request)));}
    @PostMapping
    @Log(title="核定已日结加班转调休",businessType=BusinessType.INSERT,isSaveRequestData=false,isSaveResponseData=false)
    public AjaxResult apply(@RequestBody Apply body,HttpServletRequest request)
    {return success(service.apply(body,resolveAttendanceShopDeptId(request)));}
    @PostMapping("/{transferId}/reverse")
    @Log(title="审计撤销加班转调休",businessType=BusinessType.INSERT,isSaveRequestData=false,isSaveResponseData=false)
    public AjaxResult reverse(@PathVariable Long transferId,@RequestBody Reverse body,HttpServletRequest request)
    {return success(service.reverse(transferId,body,resolveAttendanceShopDeptId(request)));}
}
