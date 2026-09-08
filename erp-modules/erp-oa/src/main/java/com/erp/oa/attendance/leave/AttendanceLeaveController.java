package com.erp.oa.attendance.leave;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.IdempotentSubmit;
import com.erp.common.security.annotation.Logical;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.oa.attendance.leave.AttendanceLeaveModels.LeaveType;
import com.erp.oa.attendance.leave.AttendanceLeaveRequests.AttachmentDelete;
import com.erp.oa.attendance.leave.AttendanceLeaveRequests.Cancel;
import com.erp.oa.attendance.leave.AttendanceLeaveRequests.SaveDraft;
import com.erp.oa.attendance.leave.AttendanceLeaveRequests.Submit;
import com.erp.oa.attendance.leave.AttendanceLeaveRequests.TypeStatus;
import com.erp.oa.attendance.leave.AttendanceLeaveService.AttachmentContent;
import com.erp.oa.controller.OaBaseController;

/** Scoped, feature-gated leave API for attendance V2. */
@Validated
@RestController
@RequestMapping("/attendance-v2/leave")
public class AttendanceLeaveController extends OaBaseController
{
    private static final String SELF = "oa:attendance:leave:self";
    private static final String LIST = "oa:attendance:leave:list";
    private static final String APPROVE = "oa:attendance:leave:approve";
    private static final String TYPE_LIST =
            "oa:attendance:leave:type:list";

    private final AttendanceLeaveService service;

    public AttendanceLeaveController(AttendanceLeaveService service)
    { this.service = service; }

    @RequiresPermissions(value = { TYPE_LIST, SELF }, logical = Logical.OR)
    @GetMapping("/types")
    public AjaxResult types(@RequestParam(required = false) String status)
    { return success(service.listTypes(status)); }

    @RequiresPermissions("oa:attendance:leave:type:add")
    @Log(title = "新增请假类型", businessType = BusinessType.INSERT,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/types")
    public AjaxResult createType(@RequestBody LeaveType body)
    { return success(service.createType(body)); }

    @RequiresPermissions("oa:attendance:leave:type:edit")
    @Log(title = "修改请假类型", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping("/types/{leaveTypeId}")
    public AjaxResult updateType(@PathVariable Long leaveTypeId,
            @RequestBody LeaveType body)
    { return success(service.updateType(leaveTypeId, body)); }

    @RequiresPermissions(value = { "oa:attendance:leave:type:edit",
            "oa:attendance:leave:type:remove" }, logical = Logical.OR)
    @Log(title = "启停请假类型", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping("/types/{leaveTypeId}/status")
    public AjaxResult typeStatus(@PathVariable Long leaveTypeId,
            @Valid @RequestBody TypeStatus body)
    { return success(service.changeTypeStatus(leaveTypeId, body.status,
            body.rowVersion)); }

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
    @GetMapping("/{leaveRequestId}")
    public AjaxResult detail(@PathVariable Long leaveRequestId,
            HttpServletRequest request)
    { return success(service.detail(leaveRequestId,
            resolveAttendanceShopDeptId(request))); }

    @RequiresPermissions(SELF)
    @GetMapping("/by-client-request/{clientRequestId}")
    public AjaxResult byClientRequest(@PathVariable String clientRequestId,
            HttpServletRequest request)
    { return success(service.byClientRequest(clientRequestId,
            resolveAttendanceShopDeptId(request))); }

    @RequiresPermissions(SELF)
    @IdempotentSubmit(timeout = 30)
    @Log(title = "保存请假草稿", businessType = BusinessType.INSERT,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/drafts")
    public AjaxResult createDraft(@Valid @RequestBody SaveDraft body,
            HttpServletRequest request)
    {
        body.leaveRequestId = null;
        body.rowVersion = null;
        return success(service.saveDraft(body, resolveAttendanceShopDeptId(request)));
    }

    @RequiresPermissions(SELF)
    @Log(title = "修改请假草稿", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping("/{leaveRequestId}/draft")
    public AjaxResult updateDraft(@PathVariable Long leaveRequestId,
            @Valid @RequestBody SaveDraft body, HttpServletRequest request)
    {
        body.leaveRequestId = leaveRequestId;
        return success(service.saveDraft(body, resolveAttendanceShopDeptId(request)));
    }

    @RequiresPermissions(SELF)
    @IdempotentSubmit(timeout = 30)
    @Log(title = "提交请假申请", businessType = BusinessType.INSERT,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/{leaveRequestId}/submit")
    public AjaxResult submit(@PathVariable Long leaveRequestId,
            @Valid @RequestBody Submit body, HttpServletRequest request)
    { return success(service.submit(leaveRequestId, body.rowVersion,
            resolveAttendanceShopDeptId(request))); }

    @RequiresPermissions(SELF)
    @IdempotentSubmit(timeout = 30)
    @Log(title = "撤回请假申请", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/{leaveRequestId}/withdraw")
    public AjaxResult withdraw(@PathVariable Long leaveRequestId,
            @Valid @RequestBody Cancel body, HttpServletRequest request)
    { return success(service.withdraw(leaveRequestId, body.rowVersion,
            body.reason, resolveAttendanceShopDeptId(request))); }

    @RequiresPermissions(SELF)
    @Log(title = "上传请假附件", businessType = BusinessType.INSERT,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping(value = "/{leaveRequestId}/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AjaxResult upload(@PathVariable Long leaveRequestId,
            @RequestParam Long rowVersion,
            @RequestPart("file") MultipartFile file,
            HttpServletRequest request)
    { return success(service.uploadAttachment(leaveRequestId, rowVersion,
            file, resolveAttendanceShopDeptId(request))); }

    @RequiresPermissions(SELF)
    @Log(title = "删除请假附件", businessType = BusinessType.DELETE,
            isSaveRequestData = false, isSaveResponseData = false)
    @DeleteMapping("/{leaveRequestId}/attachments/{attachmentId}")
    public AjaxResult deleteAttachment(@PathVariable Long leaveRequestId,
            @PathVariable Long attachmentId,
            @Valid @RequestBody AttachmentDelete body,
            HttpServletRequest request)
    { return success(service.deleteAttachment(leaveRequestId, attachmentId,
            body.rowVersion, resolveAttendanceShopDeptId(request))); }

    @RequiresPermissions(value = { SELF, LIST, APPROVE }, logical = Logical.OR)
    @Log(title = "查看请假附件", businessType = BusinessType.OTHER,
            isSaveRequestData = false, isSaveResponseData = false)
    @GetMapping("/{leaveRequestId}/attachments/{attachmentId}/content")
    public ResponseEntity<Resource> attachment(
            @PathVariable Long leaveRequestId,
            @PathVariable Long attachmentId, HttpServletRequest request)
    {
        AttachmentContent file = service.attachmentContent(leaveRequestId,
                attachmentId, resolveAttendanceShopDeptId(request));
        MediaType type;
        try { type = MediaType.parseMediaType(file.contentType()); }
        catch (RuntimeException invalid) { type = MediaType.APPLICATION_OCTET_STREAM; }
        ContentDisposition disposition = type.getType()
                .equalsIgnoreCase("image")
                        ? ContentDisposition.inline()
                                .filename(file.fileName(),
                                        StandardCharsets.UTF_8).build()
                        : ContentDisposition.attachment()
                                .filename(file.fileName(),
                                        StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().contentType(type).contentLength(file.size())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL,
                        "private, no-store, max-age=0")
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(file.path()));
    }
}
