package com.erp.oa.attendance.controller;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.attendance.support.AttendancePunchRejectionPolicy;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.Logical;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.oa.attendance.domain.AttendanceModels.Shift;
import com.erp.oa.attendance.domain.AttendanceModels.Site;
import com.erp.oa.attendance.dto.AttendanceRequests.ChallengeCreate;
import com.erp.oa.attendance.dto.AttendanceRequests.DaySettlementCommand;
import com.erp.oa.attendance.dto.AttendanceRequests.PunchCommand;
import com.erp.oa.attendance.dto.AttendanceRequests.PunchStatusRequest;
import com.erp.oa.attendance.dto.AttendanceRequests.ScheduleBatch;
import com.erp.oa.attendance.dto.AttendanceRequests.SchedulePublish;
import com.erp.oa.attendance.dto.AttendanceRequests.StatusChange;
import com.erp.oa.attendance.service.AttendanceDaySettlementService;
import com.erp.oa.attendance.service.AttendanceV2Service;
import com.erp.oa.attendance.service.AttendanceV2Service.EvidenceContent;
import com.erp.oa.controller.OaBaseController;

@Validated
@RestController
@RequestMapping("/attendance-v2")
public class AttendanceV2Controller extends OaBaseController
{
    private final AttendanceV2Service service;
    private final AttendanceDaySettlementService settlementService;

    public AttendanceV2Controller(AttendanceV2Service service,
            AttendanceDaySettlementService settlementService)
    {
        this.service = service;
        this.settlementService = settlementService;
    }

    @RequiresPermissions("oa:attendance:shift:list")
    @GetMapping("/shifts")
    public AjaxResult shifts(@RequestParam(required = false) String status)
    { return success(service.listShifts(status)); }

    @RequiresPermissions("oa:attendance:shift:query")
    @GetMapping("/shifts/{shiftId}")
    public AjaxResult shift(@PathVariable Long shiftId)
    { return success(service.getShift(shiftId)); }

    @RequiresPermissions("oa:attendance:shift:add")
    @Log(title = "新建考勤班次", businessType = BusinessType.INSERT,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/shifts")
    public AjaxResult createShift(@RequestBody Shift shift)
    { return success(service.createShift(shift)); }

    @RequiresPermissions("oa:attendance:shift:edit")
    @Log(title = "修改考勤班次", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping("/shifts/{shiftId}")
    public AjaxResult updateShift(@PathVariable Long shiftId,
            @RequestBody Shift shift)
    { return success(service.updateShift(shiftId, shift)); }

    @RequiresPermissions("oa:attendance:shift:edit")
    @Log(title = "启停考勤班次", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping("/shifts/{shiftId}/status")
    public AjaxResult shiftStatus(@PathVariable Long shiftId,
            @Valid @RequestBody StatusChange request)
    { return success(service.changeShiftStatus(shiftId, request.status,
            request.rowVersion)); }

    @RequiresPermissions("oa:attendance:shift:remove")
    @Log(title = "删除未引用班次", businessType = BusinessType.DELETE,
            isSaveRequestData = false, isSaveResponseData = false)
    @DeleteMapping("/shifts/{shiftId}")
    public AjaxResult deleteShift(@PathVariable Long shiftId,
            @RequestParam @NotNull Long rowVersion)
    {
        service.deleteShift(shiftId, rowVersion);
        return success();
    }

    @RequiresPermissions("oa:attendance:site:list")
    @GetMapping("/sites")
    public AjaxResult sites(@RequestParam(required = false) Long shopId,
            @RequestParam(required = false) String status,
            HttpServletRequest request)
    { return success(service.listSites(shopId, status,
            resolveAttendanceShopDeptId(request))); }

    @RequiresPermissions("oa:attendance:site:query")
    @GetMapping("/sites/{siteId}")
    public AjaxResult site(@PathVariable Long siteId,
            HttpServletRequest request)
    { return success(service.getSite(siteId, resolveAttendanceShopDeptId(request))); }

    @RequiresPermissions("oa:attendance:site:add")
    @Log(title = "新建考勤地点", businessType = BusinessType.INSERT,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/sites")
    public AjaxResult createSite(@RequestBody Site site,
            HttpServletRequest request)
    { return success(service.createSite(site, resolveAttendanceShopDeptId(request))); }

    @RequiresPermissions("oa:attendance:site:edit")
    @Log(title = "修改考勤地点", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping("/sites/{siteId}")
    public AjaxResult updateSite(@PathVariable Long siteId,
            @RequestBody Site site, HttpServletRequest request)
    { return success(service.updateSite(siteId, site,
            resolveAttendanceShopDeptId(request))); }

    @RequiresPermissions("oa:attendance:site:edit")
    @Log(title = "启停考勤地点", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping("/sites/{siteId}/status")
    public AjaxResult siteStatus(@PathVariable Long siteId,
            @Valid @RequestBody StatusChange body,
            HttpServletRequest request)
    { return success(service.changeSiteStatus(siteId, body.status,
            body.rowVersion, resolveAttendanceShopDeptId(request))); }

    @RequiresPermissions("oa:attendance:site:remove")
    @Log(title = "删除未引用考勤地点", businessType = BusinessType.DELETE,
            isSaveRequestData = false, isSaveResponseData = false)
    @DeleteMapping("/sites/{siteId}")
    public AjaxResult deleteSite(@PathVariable Long siteId,
            @RequestParam @NotNull Long rowVersion,
            HttpServletRequest request)
    {
        service.deleteSite(siteId, rowVersion, resolveAttendanceShopDeptId(request));
        return success();
    }

    @RequiresPermissions("oa:attendance:schedule:list")
    @GetMapping("/employee-options")
    public AjaxResult employeeOptions(@RequestParam Long shopId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize,
            HttpServletRequest request)
    {
        Long selectedShopId = resolveAttendanceShopDeptId(request);
        if (pageNum == null && pageSize == null)
            return success(service.employeeOptions(shopId, keyword,
                    selectedShopId));
        return success(service.employeeOptionsPage(shopId, keyword,
                pageNum, pageSize, selectedShopId));
    }

    @RequiresPermissions("oa:attendance:schedule:list")
    @GetMapping("/schedules")
    public AjaxResult schedules(@RequestParam Long shopId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate dateTo,
            @RequestParam(required = false) Long userId,
            HttpServletRequest request)
    { return success(service.listSchedules(shopId, dateFrom, dateTo, userId,
            resolveAttendanceShopDeptId(request))); }

    @RequiresPermissions(value = { "oa:attendance:schedule:add",
            "oa:attendance:schedule:edit" })
    @Log(title = "保存员工排班", businessType = BusinessType.INSERT,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/schedules/batch")
    public AjaxResult saveSchedules(@Valid @RequestBody ScheduleBatch body,
            HttpServletRequest request)
    { return success(service.saveScheduleBatch(body,
            resolveAttendanceShopDeptId(request))); }

    @RequiresPermissions("oa:attendance:schedule:remove")
    @Log(title = "删除草稿排班", businessType = BusinessType.DELETE,
            isSaveRequestData = false, isSaveResponseData = false)
    @DeleteMapping("/schedules/{scheduleId}")
    public AjaxResult deleteSchedule(@PathVariable Long scheduleId,
            @RequestParam @NotNull Long rowVersion,
            HttpServletRequest request)
    {
        service.deleteDraftSchedule(scheduleId, rowVersion,
                resolveAttendanceShopDeptId(request));
        return success();
    }

    @RequiresPermissions("oa:attendance:schedule:publish")
    @Log(title = "发布员工排班", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/schedules/publish")
    public AjaxResult publishSchedules(
            @Valid @RequestBody SchedulePublish body,
            HttpServletRequest request)
    { return success(service.publishSchedules(body,
            resolveAttendanceShopDeptId(request))); }

    @RequiresPermissions("oa:attendance:punch:self")
    @GetMapping("/today")
    public AjaxResult today(HttpServletRequest request)
    { return success(service.today(resolveAttendanceShopDeptId(request))); }

    @RequiresPermissions("oa:attendance:punch:self")
    @Log(title = "申请打卡凭证", businessType = BusinessType.INSERT,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/punch/challenge")
    public AjaxResult challenge(@Valid @RequestBody ChallengeCreate body,
            HttpServletRequest request)
    { return success(service.issueChallenge(body, resolveAttendanceShopDeptId(request))); }

    @RequiresPermissions("oa:attendance:punch:self")
    @Log(title = "员工现场打卡", businessType = BusinessType.INSERT,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping(value = "/punch",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AjaxResult punch(
            @RequestParam @NotNull String challengeToken,
            @RequestParam @NotNull String punchType,
            @RequestParam(required = false) String punchSlotKey,
            @RequestParam @NotNull BigDecimal latitude,
            @RequestParam @NotNull BigDecimal longitude,
            @RequestParam @NotNull BigDecimal accuracyMeters,
            @RequestParam @NotNull String clientCoordinateSystem,
            @RequestParam String clientCaptureTime,
            @RequestParam(required = false) String clientRequestId,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String appVersion,
            @RequestPart("photo") MultipartFile photo,
            HttpServletRequest request)
    {
        PunchCommand command = new PunchCommand();
        command.challengeToken = challengeToken;
        command.punchType = punchType;
        command.punchSlotKey = punchSlotKey;
        command.latitude = latitude;
        command.longitude = longitude;
        command.accuracyMeters = accuracyMeters;
        command.clientCoordinateSystem = clientCoordinateSystem;
        command.clientCaptureTimestamp = clientCaptureTime;
        command.clientRequestId = clientRequestId;
        command.deviceId = deviceId;
        command.appVersion = appVersion;
        command.clientIp = request.getRemoteAddr();
        command.userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        Long shopId = resolveAttendanceShopDeptId(request);
        try
        {
            return success(service.punch(command, photo, shopId));
        }
        catch (ServiceException ex)
        {
            // The proxied transactional service has already rolled back here.
            if (!AttendancePunchRejectionPolicy.isDefiniteRejection(ex)) throw ex;
            return AjaxResult.error(422, ex.getMessage())
                    .put("businessCode", AttendancePunchRejectionPolicy.BUSINESS_CODE)
                    .put("clientRequestId", clientRequestId);
        }
    }

    @RequiresPermissions("oa:attendance:punch:self")
    @Log(title = "查询员工打卡终态", businessType = BusinessType.OTHER,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/punch/status")
    public AjaxResult punchStatus(
            @Valid @RequestBody PunchStatusRequest body,
            HttpServletRequest request)
    {
        return success(service.punchStatus(body,
                resolveAttendanceShopDeptId(request)));
    }

    @RequiresPermissions(value = { "oa:attendance:record:self",
            "oa:attendance:record:evidence" }, logical = Logical.OR)
    @Log(title = "查看考勤照片证据", businessType = BusinessType.OTHER,
            isSaveRequestData = false, isSaveResponseData = false)
    @GetMapping("/evidence/{evidenceId}/content")
    public ResponseEntity<Resource> evidence(@PathVariable Long evidenceId,
            HttpServletRequest request)
    {
        EvidenceContent file = service.evidenceContent(evidenceId,
                resolveAttendanceShopDeptId(request));
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .contentLength(file.size())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(file.fileName(),
                                        StandardCharsets.UTF_8)
                                .build().toString())
                .header(HttpHeaders.CACHE_CONTROL,
                        "private, no-store, max-age=0")
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(file.path()));
    }

    @RequiresPermissions("oa:attendance:record:self")
    @GetMapping("/day-results/my")
    public AjaxResult myDayResults(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate dateTo)
    { return success(service.dayResults(SecurityUtils.getUserId(), dateFrom,
            dateTo)); }

    @RequiresPermissions("oa:attendance:day:list")
    @GetMapping("/day-results")
    public AjaxResult dayResults(@RequestParam Long shopId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate dateTo,
            @RequestParam(required = false) Long userId,
            HttpServletRequest request)
    { return success(service.listDayResults(shopId, dateFrom, dateTo,
            userId, resolveAttendanceShopDeptId(request))); }

    @RequiresPermissions("oa:attendance:day:list")
    @GetMapping("/day-results/unfinalized")
    public AjaxResult unfinalized(@RequestParam Long shopId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM")
                    YearMonth month,
            HttpServletRequest request)
    { return success(service.countUnfinalized(shopId, month,
            resolveAttendanceShopDeptId(request))); }

    @RequiresPermissions("oa:attendance:day:list")
    @GetMapping("/day-results/preflight")
    public AjaxResult settlementPreflight(@RequestParam Long shopId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate dateTo,
            HttpServletRequest request)
    { return success(settlementService.preflight(shopId, dateFrom, dateTo,
            resolveAttendanceShopDeptId(request))); }

    @RequiresPermissions("oa:attendance:day:settle")
    @Log(title = "结算考勤日结果", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/day-results/settle")
    public AjaxResult settleDayResults(
            @Valid @RequestBody DaySettlementCommand command,
            HttpServletRequest request)
    { return success(settlementService.settle(command,
            resolveAttendanceShopDeptId(request))); }
}
