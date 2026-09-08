package com.erp.oa.attendance.correction;

import java.time.LocalDate;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.IdempotentSubmit;
import com.erp.common.security.annotation.Logical;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.oa.attendance.correction.AttendanceCorrectionRequests.SaveDraft;
import com.erp.oa.attendance.correction.AttendanceCorrectionRequests.Submit;
import com.erp.oa.controller.OaBaseController;

/** Scoped, feature-gated employee correction API for attendance V2. */
@Validated
@RestController
@RequestMapping("/attendance-v2/corrections")
public class AttendanceCorrectionController extends OaBaseController
{
    private static final String SELF = "oa:attendance:correction:self";
    private static final String LIST = "oa:attendance:correction:list";
    private static final String APPROVE = "oa:attendance:correction:approve";

    private final AttendanceCorrectionService service;

    public AttendanceCorrectionController(AttendanceCorrectionService service)
    { this.service = service; }

    @RequiresPermissions(SELF)
    @GetMapping("/eligible-schedules")
    public AjaxResult eligibleSchedules(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            HttpServletRequest request)
    { return success(service.eligibleSchedules(dateFrom, dateTo,
            resolveAttendanceShopDeptId(request))); }

    @RequiresPermissions(SELF)
    @GetMapping("/eligible-schedules/{scheduleId}/punch-events")
    public AjaxResult eligiblePunchEvents(@PathVariable Long scheduleId,
            HttpServletRequest request)
    { return success(service.eligiblePunchEvents(scheduleId,
            resolveAttendanceShopDeptId(request))); }

    @RequiresPermissions(SELF)
    @GetMapping("/my")
    public AjaxResult my(@RequestParam(required = false) String status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            HttpServletRequest request)
    { return success(service.listMy(status, dateFrom, dateTo,
            resolveAttendanceShopDeptId(request))); }

    @RequiresPermissions(LIST)
    @GetMapping("/shop")
    public AjaxResult shop(@RequestParam Long shopId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            HttpServletRequest request)
    { return success(service.listShop(shopId, userId, status, dateFrom,
            dateTo, resolveAttendanceShopDeptId(request))); }

    @RequiresPermissions(value = { SELF, LIST, APPROVE }, logical = Logical.OR)
    @GetMapping("/{correctionRequestId}")
    public AjaxResult detail(@PathVariable Long correctionRequestId,
            HttpServletRequest request)
    { return success(service.detail(correctionRequestId,
            resolveAttendanceShopDeptId(request))); }

    @RequiresPermissions(SELF)
    @GetMapping("/by-client-request/{clientRequestId}")
    public AjaxResult byClientRequest(@PathVariable String clientRequestId,
            HttpServletRequest request)
    { return success(service.byClientRequest(clientRequestId,
            resolveAttendanceShopDeptId(request))); }

    @RequiresPermissions(SELF)
    @IdempotentSubmit(timeout = 30)
    @Log(title = "保存补卡草稿", businessType = BusinessType.INSERT,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/drafts")
    public AjaxResult createDraft(@Valid @RequestBody SaveDraft body,
            HttpServletRequest request)
    {
        body.correctionRequestId = null;
        body.rowVersion = null;
        return success(service.saveDraft(body, resolveAttendanceShopDeptId(request)));
    }

    @RequiresPermissions(SELF)
    @Log(title = "修改补卡草稿", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping("/{correctionRequestId}/draft")
    public AjaxResult updateDraft(@PathVariable Long correctionRequestId,
            @Valid @RequestBody SaveDraft body, HttpServletRequest request)
    {
        body.correctionRequestId = correctionRequestId;
        return success(service.saveDraft(body, resolveAttendanceShopDeptId(request)));
    }

    @RequiresPermissions(SELF)
    @IdempotentSubmit(timeout = 30)
    @Log(title = "提交补卡申请", businessType = BusinessType.INSERT,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/{correctionRequestId}/submit")
    public AjaxResult submit(@PathVariable Long correctionRequestId,
            @Valid @RequestBody Submit body, HttpServletRequest request)
    { return success(service.submit(correctionRequestId, body.rowVersion,
            resolveAttendanceShopDeptId(request))); }
}
