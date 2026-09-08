package com.erp.oa.attendance.controller;

import java.nio.charset.StandardCharsets;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.IdempotentSubmit;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.oa.attendance.dto.AttendanceRemainingWorkRequests.Confirm;
import com.erp.oa.attendance.service.AttendanceRemainingWorkService;
import com.erp.oa.attendance.service.AttendanceRemainingWorkService.AttachmentContent;
import com.erp.oa.controller.OaBaseController;

@Validated
@RestController
@RequestMapping("/attendance-v2/remaining-work")
@RequiresPermissions("oa:attendance:remaining-work:manage")
public class AttendanceRemainingWorkController extends OaBaseController
{
    private final AttendanceRemainingWorkService service;

    public AttendanceRemainingWorkController(
            AttendanceRemainingWorkService service)
    { this.service = service; }

    @GetMapping("/schedules/{scheduleId}/intervals")
    public AjaxResult intervals(@PathVariable Long scheduleId,
            HttpServletRequest request)
    { return success(service.intervals(scheduleId,
            resolveAttendanceShopDeptId(request))); }

    @GetMapping("/schedules/{scheduleId}/history")
    public AjaxResult history(@PathVariable Long scheduleId,
            HttpServletRequest request)
    { return success(service.history(scheduleId,
            resolveAttendanceShopDeptId(request))); }

    @IdempotentSubmit(timeout = 30)
    @Log(title = "确认部分请假剩余工作",
            businessType = BusinessType.INSERT,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/confirmations")
    public AjaxResult confirm(@Valid @RequestBody Confirm body,
            HttpServletRequest request)
    { return success(service.confirm(body, resolveAttendanceShopDeptId(request))); }

    @Log(title = "上传剩余工作补充证据",
            businessType = BusinessType.INSERT,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping(value = "/confirmations/{confirmationId}/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AjaxResult upload(@PathVariable Long confirmationId,
            @RequestPart("file") MultipartFile file,
            HttpServletRequest request)
    { return success(service.upload(confirmationId, file,
            resolveAttendanceShopDeptId(request))); }

    @GetMapping("/confirmations/{confirmationId}/attachments/{attachmentId}/content")
    public ResponseEntity<Resource> content(@PathVariable Long confirmationId,
            @PathVariable Long attachmentId, HttpServletRequest request)
    {
        AttachmentContent value = service.attachmentContent(confirmationId,
                attachmentId, resolveAttendanceShopDeptId(request));
        MediaType type;
        try { type = MediaType.parseMediaType(value.contentType()); }
        catch (RuntimeException ignored)
        { type = MediaType.APPLICATION_OCTET_STREAM; }
        ContentDisposition disposition = "image".equalsIgnoreCase(
                type.getType()) ? ContentDisposition.inline()
                        .filename(value.fileName(), StandardCharsets.UTF_8)
                        .build() : ContentDisposition.attachment()
                                .filename(value.fileName(),
                                        StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().contentType(type)
                .contentLength(value.size())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL,
                        "private, no-store, max-age=0")
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(value.path()));
    }
}
