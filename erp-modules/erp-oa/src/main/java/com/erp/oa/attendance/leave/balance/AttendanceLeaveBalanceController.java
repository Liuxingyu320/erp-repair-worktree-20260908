package com.erp.oa.attendance.leave.balance;

import org.springframework.web.bind.annotation.*;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.oa.attendance.leave.balance.AttendanceLeaveBalanceModels.LocationMapping;
import com.erp.oa.attendance.leave.balance.AttendanceLeaveBalanceRequests.*;
import com.erp.oa.controller.OaBaseController;

@RestController
@RequestMapping("/attendance-v2/leave/balance")
public class AttendanceLeaveBalanceController extends OaBaseController
{
    private final AttendanceLeaveBalanceService service;
    public AttendanceLeaveBalanceController(AttendanceLeaveBalanceService service) { this.service = service; }

    @GetMapping("/my") @RequiresPermissions(AttendanceLeaveBalanceAccess.PREFIX + "self")
    public AjaxResult my(@RequestParam Long leaveTypeId) { return success(service.my(leaveTypeId)); }
    @PostMapping("/my/recalculate") @RequiresPermissions(AttendanceLeaveBalanceAccess.PREFIX + "self")
    public AjaxResult recalculateMy(@RequestBody Recalculate body) { return success(service.recalculateMy(body.leaveTypeId)); }
    @GetMapping("/employees/{userId}") @RequiresPermissions(AttendanceLeaveBalanceAccess.PREFIX + "read")
    public AjaxResult employee(@PathVariable Long userId, @RequestParam Long leaveTypeId)
    { return success(service.employee(userId, leaveTypeId)); }
    @GetMapping("/employees/{userId}/ledger") @RequiresPermissions(AttendanceLeaveBalanceAccess.PREFIX + "read")
    public AjaxResult ledger(@PathVariable Long userId, @RequestParam Long leaveTypeId, @RequestParam(required=false) Long beforeId)
    { return success(service.ledger(userId, leaveTypeId, beforeId)); }
    @PostMapping("/employees/{userId}/recalculate") @RequiresPermissions(AttendanceLeaveBalanceAccess.PREFIX + "adjust")
    public AjaxResult recalculateEmployee(@PathVariable Long userId, @RequestBody Recalculate body)
    { return success(service.recalculateEmployee(userId, body.leaveTypeId)); }
    @PostMapping("/employees/{userId}/adjustments") @RequiresPermissions(AttendanceLeaveBalanceAccess.PREFIX + "adjust")
    @Log(title="调整假期额度", businessType=BusinessType.INSERT, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult adjust(@PathVariable Long userId, @RequestBody Adjustment body) { return success(service.adjust(userId, body)); }
    @GetMapping("/rules") @RequiresPermissions(AttendanceLeaveBalanceAccess.PREFIX + "rule")
    public AjaxResult rules() { return success(service.rules()); }
    @GetMapping("/rules/{ruleId}") @RequiresPermissions(AttendanceLeaveBalanceAccess.PREFIX + "rule")
    public AjaxResult rule(@PathVariable Long ruleId) { return success(service.rule(ruleId)); }
    @PostMapping("/rules") @RequiresPermissions(AttendanceLeaveBalanceAccess.PREFIX + "rule")
    @Log(title="新增假期额度规则版本", businessType=BusinessType.INSERT, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult createRule(@RequestBody RuleDraft body) { return success(service.saveRule(null, body)); }
    @PutMapping("/rules/{ruleId}/draft") @RequiresPermissions(AttendanceLeaveBalanceAccess.PREFIX + "rule")
    @Log(title="修改假期额度规则草稿", businessType=BusinessType.UPDATE, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult updateRule(@PathVariable Long ruleId, @RequestBody RuleDraft body) { return success(service.saveRule(ruleId, body)); }
    @PostMapping("/rules/{ruleId}/publish") @RequiresPermissions(AttendanceLeaveBalanceAccess.PREFIX + "rule")
    @Log(title="发布假期额度规则版本", businessType=BusinessType.UPDATE, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult publishRule(@PathVariable Long ruleId, @RequestBody Version body) { return success(service.publishRule(ruleId, body.rowVersion)); }
    @GetMapping("/locations") @RequiresPermissions(AttendanceLeaveBalanceAccess.PREFIX + "rule")
    public AjaxResult locations() { return success(service.locations()); }
    @PostMapping("/locations") @RequiresPermissions(AttendanceLeaveBalanceAccess.PREFIX + "rule")
    @Log(title="新增额度工作地映射", businessType=BusinessType.INSERT, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult createLocation(@RequestBody LocationMapping body) { return success(service.saveLocation(null, body)); }
    @PutMapping("/locations/{mappingId}") @RequiresPermissions(AttendanceLeaveBalanceAccess.PREFIX + "rule")
    @Log(title="修改额度工作地映射", businessType=BusinessType.UPDATE, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult updateLocation(@PathVariable Long mappingId, @RequestBody LocationMapping body)
    { return success(service.saveLocation(mappingId, body)); }
}
