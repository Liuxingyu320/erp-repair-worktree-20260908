package com.erp.oa.attendance.leave;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import com.erp.approval.api.RemoteApprovalService;
import com.erp.approval.api.domain.ApprovalStartRequest;
import com.erp.approval.api.domain.ApprovalWithdrawRequest;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.domain.R;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.oa.attendance.leave.AttendanceLeaveAttachmentStorage.StoredAttachment;
import com.erp.oa.attendance.leave.AttendanceLeaveAttachmentStorage.PreparedAttachment;
import com.erp.oa.attendance.leave.AttendanceLeaveModels.ApprovalOutbox;
import com.erp.oa.attendance.leave.AttendanceLeaveModels.LeaveAttachment;
import com.erp.oa.attendance.leave.AttendanceLeaveModels.LeaveRequest;
import com.erp.oa.attendance.leave.AttendanceLeaveModels.LeaveSegment;
import com.erp.oa.attendance.leave.AttendanceLeaveModels.LeaveType;
import com.erp.oa.attendance.leave.AttendanceLeaveModels.ScheduleRef;
import com.erp.oa.attendance.leave.AttendanceLeaveRequests.SaveDraft;
import com.erp.oa.attendance.support.AttendanceClientRequestSupport;
import com.erp.oa.service.BusinessFeatureGate;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.model.LoginUser;

@Service
public class AttendanceLeaveService
{
    private static final Set<String> EDITABLE = Set.of("DRAFT", "RETURNED");
    private static final Set<String> UNIT_MODES = Set.of(
            "MINUTE", "HALF_DAY", "DAY", "MIXED");
    private static final Set<String> PAY_POLICIES = Set.of(
            "PAID", "UNPAID", "POLICY");
    private static final int MAX_ATTACHMENTS = 10;
    private static final int MAX_RANGE_DAYS = 366;

    private final AttendanceLeaveMapper mapper;
    private final AttendanceLeaveAttachmentStorage storage;
    private final AttendanceLeaveApprovalOutboxService outboxService;
    private final AttendanceLeaveApprovalAfterCommitTrigger trigger;
    private final RemoteApprovalService approvalService;
    private final ShopScopeService shopScopeService;
    private final BusinessFeatureGate featureGate;
    private final Clock clock;

    @Autowired
    public AttendanceLeaveService(AttendanceLeaveMapper mapper,
            AttendanceLeaveAttachmentStorage storage,
            AttendanceLeaveApprovalOutboxService outboxService,
            AttendanceLeaveApprovalAfterCommitTrigger trigger,
            RemoteApprovalService approvalService,
            ShopScopeService shopScopeService,
            BusinessFeatureGate featureGate)
    {
        this(mapper, storage, outboxService, trigger, approvalService,
                shopScopeService, featureGate, Clock.systemDefaultZone());
    }

    AttendanceLeaveService(AttendanceLeaveMapper mapper,
            AttendanceLeaveAttachmentStorage storage,
            AttendanceLeaveApprovalOutboxService outboxService,
            AttendanceLeaveApprovalAfterCommitTrigger trigger,
            RemoteApprovalService approvalService,
            ShopScopeService shopScopeService,
            BusinessFeatureGate featureGate, Clock clock)
    {
        this.mapper = mapper;
        this.storage = storage;
        this.outboxService = outboxService;
        this.trigger = trigger;
        this.approvalService = approvalService;
        this.shopScopeService = shopScopeService;
        this.featureGate = featureGate;
        this.clock = clock;
    }

    public List<LeaveType> listTypes(String status)
    {
        String normalized = blank(status) ? null : status.trim()
                .toUpperCase(Locale.ROOT);
        if (normalized != null
                && !Set.of("ENABLED", "DISABLED").contains(normalized))
            throw new ServiceException("LEAVE_TYPE_STATUS_INVALID");
        if (!hasPermission("oa:attendance:leave:type:list"))
            normalized = "ENABLED";
        return mapper.selectLeaveTypes(normalized);
    }

    @Transactional(rollbackFor = Exception.class)
    public LeaveType createType(LeaveType value)
    {
        validateType(value, true);
        value.leaveTypeId = null;
        value.status = "DISABLED";
        value.rowVersion = 0L;
        value.setCreateBy(operator());
        if (mapper.insertLeaveType(value) != 1)
            throw new ServiceException("LEAVE_TYPE_CREATE_FAILED");
        return mapper.selectLeaveTypeById(value.leaveTypeId);
    }

    @Transactional(rollbackFor = Exception.class)
    public LeaveType updateType(Long leaveTypeId, LeaveType value)
    {
        if (value == null) throw new ServiceException("LEAVE_TYPE_REQUIRED");
        LeaveType current = requireType(leaveTypeId);
        value.leaveTypeId = leaveTypeId;
        value.typeCode = current.typeCode;
        value.status = current.status;
        requireVersion(value.rowVersion, current.rowVersion);
        validateType(value, false);
        value.setUpdateBy(operator());
        if (mapper.updateLeaveType(value) != 1)
            throw new ServiceException("LEAVE_TYPE_VERSION_CONFLICT");
        return mapper.selectLeaveTypeById(leaveTypeId);
    }

    @Transactional(rollbackFor = Exception.class)
    public LeaveType changeTypeStatus(Long leaveTypeId, String status,
            Long rowVersion)
    {
        requireType(leaveTypeId);
        String target = status == null ? ""
                : status.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ENABLED", "DISABLED").contains(target))
            throw new ServiceException("LEAVE_TYPE_STATUS_INVALID");
        if (mapper.updateLeaveTypeStatus(leaveTypeId, target, rowVersion,
                operator()) != 1)
            throw new ServiceException("LEAVE_TYPE_VERSION_CONFLICT");
        return mapper.selectLeaveTypeById(leaveTypeId);
    }

    @Transactional(rollbackFor = Exception.class)
    public LeaveRequest saveDraft(SaveDraft body, Long selectedShopId)
    {
        requireEnabled();
        if (body == null) throw new ServiceException("LEAVE_DRAFT_REQUIRED");
        Long userId = SecurityUtils.getUserId();
        if (body.leaveRequestId == null)
        {
            Long shopId = requireSelectedShop(selectedShopId);
            String clientRequestId = AttendanceClientRequestSupport
                    .normalizeOptional(body.clientRequestId,
                            "LEAVE_CLIENT_REQUEST_ID_INVALID");
            String fingerprint = clientRequestId == null ? null
                    : draftFingerprint(body);
            if (clientRequestId != null)
            {
                LeaveRequest replay = mapper
                        .selectLeaveRequestByClientRequestId(userId, shopId,
                                clientRequestId);
                if (replay != null)
                    return replay(replay, fingerprint);
            }
            String userName = requireActiveEmployee(userId, shopId);
            LeaveType type = requireEnabledType(body.leaveTypeId);
            LeaveRequest value = new LeaveRequest();
            value.leaveRequestNo = nextNo("AL");
            value.clientRequestId = clientRequestId;
            value.clientRequestFingerprint = fingerprint;
            value.userId = userId;
            value.userName = userName;
            value.shopId = shopId;
            applyDraft(value, body, type);
            value.status = "DRAFT";
            value.businessRound = 0;
            value.rowVersion = 0L;
            value.setCreateBy(operator());
            try
            {
                if (mapper.insertLeaveRequest(value) != 1)
                    throw new ServiceException("LEAVE_DRAFT_CREATE_FAILED");
            }
            catch (DuplicateKeyException duplicate)
            {
                if (clientRequestId == null) throw duplicate;
                LeaveRequest replay = mapper
                        .selectLeaveRequestByClientRequestIdForUpdate(userId,
                                shopId, clientRequestId);
                if (replay == null) throw duplicate;
                return replay(replay, fingerprint);
            }
            replaceSegments(value, type);
            return detail(value.leaveRequestId, selectedShopId);
        }

        LeaveRequest current = requireOwnedForUpdate(body.leaveRequestId,
                selectedShopId);
        if (!EDITABLE.contains(current.status))
            throw new ServiceException("LEAVE_STATUS_NOT_EDITABLE");
        requireVersion(body.rowVersion, current.rowVersion);
        LeaveType type = requireEnabledType(body.leaveTypeId);
        applyDraft(current, body, type);
        current.setUpdateBy(operator());
        if (mapper.updateLeaveDraft(current) != 1)
            throw new ServiceException("LEAVE_DRAFT_VERSION_CONFLICT");
        current.rowVersion++;
        replaceSegments(current, type);
        return detail(current.leaveRequestId, selectedShopId);
    }

    public LeaveRequest detail(Long leaveRequestId, Long selectedShopId)
    {
        requireEnabled();
        LeaveRequest value = mapper.selectLeaveRequestById(leaveRequestId);
        requireReadable(value, selectedShopId);
        return attachChildren(value);
    }

    public LeaveRequest byClientRequest(String clientRequestId,
            Long selectedShopId)
    {
        requireEnabled();
        String key = AttendanceClientRequestSupport.normalizeOptional(
                clientRequestId, "LEAVE_CLIENT_REQUEST_ID_INVALID");
        if (key == null)
            throw new ServiceException("LEAVE_CLIENT_REQUEST_ID_INVALID");
        Long shopId = requireSelectedShop(selectedShopId);
        LeaveRequest value = mapper.selectLeaveRequestByClientRequestId(
                SecurityUtils.getUserId(), shopId, key);
        if (value == null)
            throw new ServiceException("LEAVE_CLIENT_REQUEST_NOT_FOUND");
        return attachChildren(value);
    }

    public List<LeaveRequest> listMy(String status, LocalDate dateFrom,
            LocalDate dateTo, Long selectedShopId)
    {
        requireEnabled();
        validateRange(dateFrom, dateTo);
        Long shopId = requireSelectedShop(selectedShopId);
        List<LeaveRequest> rows = mapper.selectLeaveRequestsByUser(
                SecurityUtils.getUserId(), shopId, normalizeStatus(status),
                dateFrom, dateTo);
        return rows == null ? List.of() : rows;
    }

    public List<LeaveRequest> listShop(Long shopId, Long userId,
            String status, LocalDate dateFrom, LocalDate dateTo,
            Long selectedShopId)
    {
        requireEnabled();
        validateRange(dateFrom, dateTo);
        Long target = requireSameShop(shopId, selectedShopId);
        List<LeaveRequest> rows = mapper.selectLeaveRequestsByShop(target,
                userId, normalizeStatus(status), dateFrom, dateTo);
        return rows == null ? List.of() : rows;
    }

    @Transactional(rollbackFor = Exception.class)
    public LeaveAttachment uploadAttachment(Long leaveRequestId,
            Long expectedVersion, MultipartFile file, Long selectedShopId)
    {
        requireEnabled();
        LeaveRequest current = requireOwnedForUpdate(leaveRequestId,
                selectedShopId);
        requireEditable(current);
        PreparedAttachment prepared = storage.prepare(file);
        LeaveAttachment existing = mapper.selectLeaveAttachmentByHash(
                leaveRequestId, prepared.sha256());
        if (existing != null)
        {
            existing.requestRowVersion = current.rowVersion;
            return existing;
        }
        requireVersion(expectedVersion, current.rowVersion);
        if (mapper.countLeaveAttachments(leaveRequestId) >= MAX_ATTACHMENTS)
            throw new ServiceException("LEAVE_ATTACHMENT_LIMIT_EXCEEDED");
        StoredAttachment stored = storage.store(leaveRequestId, prepared);
        registerRollbackDelete(stored.relativePath());
        LeaveAttachment value = new LeaveAttachment();
        value.leaveRequestId = leaveRequestId;
        value.originalName = stored.originalName();
        value.storagePath = stored.relativePath();
        value.contentType = stored.contentType();
        value.fileExtension = stored.extension();
        value.fileSize = stored.size();
        value.sha256 = stored.sha256();
        value.uploadedBy = SecurityUtils.getUserId();
        try
        {
            if (mapper.insertLeaveAttachment(value) != 1)
                throw new ServiceException("LEAVE_ATTACHMENT_SAVE_FAILED");
        }
        catch (DuplicateKeyException duplicate)
        {
            LeaveAttachment replay = mapper.selectLeaveAttachmentByHash(
                    leaveRequestId, stored.sha256());
            if (replay == null) throw duplicate;
            storage.deleteQuietly(stored.relativePath());
            replay.requestRowVersion = current.rowVersion;
            return replay;
        }
        touchDraft(current);
        LeaveAttachment saved = mapper.selectLeaveAttachmentById(
                value.attachmentId);
        if (saved == null)
            throw new ServiceException("LEAVE_ATTACHMENT_SAVE_FAILED");
        saved.requestRowVersion = current.rowVersion;
        return saved;
    }

    @Transactional(rollbackFor = Exception.class)
    public LeaveRequest deleteAttachment(Long leaveRequestId,
            Long attachmentId, Long expectedVersion, Long selectedShopId)
    {
        requireEnabled();
        LeaveRequest current = requireOwnedForUpdate(leaveRequestId,
                selectedShopId);
        requireEditable(current);
        requireVersion(expectedVersion, current.rowVersion);
        LeaveAttachment attachment = mapper.selectLeaveAttachmentById(
                attachmentId);
        if (attachment == null || !Objects.equals(leaveRequestId,
                attachment.leaveRequestId))
            throw new ServiceException("LEAVE_ATTACHMENT_NOT_FOUND");
        if (mapper.deleteLeaveAttachment(attachmentId, leaveRequestId) != 1)
            throw new ServiceException("LEAVE_ATTACHMENT_DELETE_FAILED");
        touchDraft(current);
        registerCommitDelete(attachment.storagePath);
        return detail(leaveRequestId, selectedShopId);
    }

    public AttachmentContent attachmentContent(Long leaveRequestId,
            Long attachmentId, Long selectedShopId)
    {
        requireEnabled();
        LeaveRequest request = mapper.selectLeaveRequestById(leaveRequestId);
        requireReadable(request, selectedShopId);
        LeaveAttachment attachment = mapper.selectLeaveAttachmentById(
                attachmentId);
        if (attachment == null || !Objects.equals(leaveRequestId,
                attachment.leaveRequestId))
            throw new ServiceException("LEAVE_ATTACHMENT_NOT_FOUND");
        Path path = storage.resolve(attachment.storagePath);
        try
        {
            return new AttachmentContent(path, attachment.originalName,
                    attachment.contentType, Files.size(path));
        }
        catch (java.io.IOException exception)
        {
            throw new ServiceException("LEAVE_ATTACHMENT_READ_FAILED");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public LeaveRequest submit(Long leaveRequestId, Long expectedVersion,
            Long selectedShopId)
    {
        requireEnabled();
        LeaveRequest current = requireOwnedForUpdate(leaveRequestId,
                selectedShopId);
        if ("PENDING".equals(current.status)
                && current.approvalInstanceId != null)
            return detail(leaveRequestId, selectedShopId);
        if ("SUBMITTING".equals(current.status))
        {
            ApprovalOutbox existing = outboxService.selectByRound(
                    leaveRequestId, current.businessRound);
            if (existing == null)
                throw new ServiceException("LEAVE_APPROVAL_OUTBOX_MISSING");
            if (AttendanceLeaveApprovalOutboxService.FAILED.equals(
                    existing.status))
                throw new ServiceException("LEAVE_APPROVAL_START_FAILED");
            if (AttendanceLeaveApprovalOutboxService.COMPLETED.equals(
                    existing.status))
                throw new ServiceException(
                        "LEAVE_APPROVAL_LOCAL_LINK_INCONSISTENT");
            trigger.trigger(existing.outboxId);
            return detail(leaveRequestId, selectedShopId);
        }
        requireVersion(expectedVersion, current.rowVersion);
        requireEditable(current);
        requireActiveEmployee(current.userId, current.shopId);
        LeaveType type = requireEnabledType(current.leaveTypeId);
        validateRequest(current, type, current.leaveRequestId);
        int overlappingSchedules = mapper.countOverlappingPublishedSchedules(
                current.userId, current.shopId, current.startTime,
                current.endTime);
        if (overlappingSchedules > 0)
        {
            Integer scheduledWorkMinutes = mapper
                    .calculateScheduledWorkMinutes(current.userId,
                            current.shopId, current.startTime,
                            current.endTime);
            if (scheduledWorkMinutes == null || scheduledWorkMinutes <= 0)
                throw new ServiceException("LEAVE_NO_SCHEDULED_WORK");
        }
        // Drafts can outlive both leave-type policy edits and schedule changes.
        // Refresh the derived pay/schedule snapshot at the submit boundary so
        // approval and settlement never consume stale draft segments.
        replaceSegments(current, type);
        int attachments = mapper.countLeaveAttachments(leaveRequestId);
        if (requiresAttachment(type, current.totalMinutes)
                && attachments <= 0)
            throw new ServiceException("LEAVE_ATTACHMENT_REQUIRED_BY_POLICY");
        if (!Boolean.TRUE.equals(type.approvalRequired))
        {
            if (mapper.markLeaveAutoApproved(leaveRequestId, current.status,
                    current.rowVersion, operator()) != 1)
                throw new ServiceException("LEAVE_SUBMIT_VERSION_CONFLICT");
            mapper.invalidateDayResultsForLeaveRequest(leaveRequestId,
                    operator());
            return detail(leaveRequestId, selectedShopId);
        }
        int round = (current.businessRound == null ? 0
                : current.businessRound) + 1;
        Long version = current.rowVersion;
        if (mapper.markLeaveSubmitting(leaveRequestId, current.status,
                version, round, operator()) != 1)
            throw new ServiceException("LEAVE_SUBMIT_VERSION_CONFLICT");
        mapper.invalidateDayResultsForLeaveRequest(leaveRequestId,
                operator());
        current.status = "SUBMITTING";
        current.businessRound = round;
        current.approvalInstanceId = null;
        current.rowVersion = version + 1;
        ApprovalStartRequest command = buildApprovalRequest(current, type,
                attachments);
        ApprovalOutbox outbox = outboxService.enqueue(current, command,
                operator());
        trigger.trigger(outbox.outboxId);
        return detail(leaveRequestId, selectedShopId);
    }

    @Transactional(rollbackFor = Exception.class)
    public LeaveRequest withdraw(Long leaveRequestId, Long expectedVersion,
            String reason, Long selectedShopId)
    {
        requireEnabled();
        LeaveRequest current = requireOwnedForUpdate(leaveRequestId,
                selectedShopId);
        if ("CANCELLED".equals(current.status))
            return detail(leaveRequestId, selectedShopId);
        requireVersion(expectedVersion, current.rowVersion);
        String safeReason = normalizeReason(reason);
        if (EDITABLE.contains(current.status))
        {
            if (mapper.cancelLeaveDraft(leaveRequestId, current.status,
                    current.rowVersion, safeReason, operator()) != 1)
                throw new ServiceException("LEAVE_WITHDRAW_VERSION_CONFLICT");
            return detail(leaveRequestId, selectedShopId);
        }
        if (!"PENDING".equals(current.status)
                || current.approvalInstanceId == null)
            throw new ServiceException("LEAVE_STATUS_NOT_WITHDRAWABLE");
        ApprovalWithdrawRequest command = new ApprovalWithdrawRequest();
        command.setInstanceId(current.approvalInstanceId);
        command.setApplicantId(SecurityUtils.getUserId());
        command.setReason(safeReason);
        R<Boolean> response = approvalService.withdraw(command,
                SecurityConstants.INNER);
        if (response == null || !R.isSuccess(response)
                || !Boolean.TRUE.equals(response.getData()))
            throw new ServiceException(response == null
                    ? "LEAVE_APPROVAL_WITHDRAW_UNAVAILABLE"
                    : StringUtils.defaultIfEmpty(response.getMsg(),
                            "LEAVE_APPROVAL_WITHDRAW_FAILED"));
        return detail(leaveRequestId, selectedShopId);
    }

    private void applyDraft(LeaveRequest value, SaveDraft body, LeaveType type)
    {
        value.leaveTypeId = body.leaveTypeId;
        value.startTime = body.startTime;
        value.endTime = body.endTime;
        value.reason = body.reason == null ? null : body.reason.trim();
        value.rowVersion = body.rowVersion == null ? value.rowVersion
                : body.rowVersion;
        validateRequest(value, type, value.leaveRequestId);
    }

    private void validateRequest(LeaveRequest value, LeaveType type,
            Long excludeId)
    {
        if (value.startTime == null || value.endTime == null
                || !value.endTime.isAfter(value.startTime))
            throw new ServiceException("LEAVE_TIME_RANGE_INVALID");
        if (value.startTime.getSecond() != 0 || value.startTime.getNano() != 0
                || value.endTime.getSecond() != 0
                || value.endTime.getNano() != 0)
            throw new ServiceException("LEAVE_TIME_MUST_ALIGN_TO_MINUTE");
        long days = ChronoUnit.DAYS.between(value.startTime.toLocalDate(),
                value.endTime.toLocalDate());
        if (days < 0 || days > MAX_RANGE_DAYS)
            throw new ServiceException("LEAVE_TIME_RANGE_TOO_LARGE");
        long minutes = Duration.between(value.startTime, value.endTime)
                .toMinutes();
        if (minutes <= 0 || minutes > MAX_RANGE_DAYS * 24L * 60L)
            throw new ServiceException("LEAVE_MINUTES_INVALID");
        if (type.minMinutes != null && minutes < type.minMinutes)
            throw new ServiceException("LEAVE_BELOW_MINIMUM");
        if (type.maxMinutesPerRequest != null
                && minutes > type.maxMinutesPerRequest)
            throw new ServiceException("LEAVE_ABOVE_MAXIMUM");
        int step = type.stepMinutes == null ? 1 : type.stepMinutes;
        if (minutes % step != 0)
            throw new ServiceException("LEAVE_STEP_MISMATCH");
        LocalDate lastDate = value.endTime.minusNanos(1).toLocalDate();
        if (!Boolean.TRUE.equals(type.allowCrossDay)
                && !value.startTime.toLocalDate().equals(lastDate))
            throw new ServiceException("LEAVE_CROSS_DAY_NOT_ALLOWED");
        if (blank(value.reason) || value.reason.length() > 1000)
            throw new ServiceException("LEAVE_REASON_INVALID");
        if (mapper.countOverlappingLeave(value.userId, excludeId,
                value.startTime, value.endTime) > 0)
            throw new ServiceException("LEAVE_TIME_OVERLAP");
        value.totalMinutes = Math.toIntExact(minutes);
    }

    private void replaceSegments(LeaveRequest request, LeaveType type)
    {
        mapper.deleteLeaveSegments(request.leaveRequestId);
        LocalDate first = request.startTime.toLocalDate();
        LocalDate last = request.endTime.minusNanos(1).toLocalDate();
        List<ScheduleRef> refs = mapper.selectScheduleRefs(request.userId,
                request.shopId, first, last);
        for (LocalDate date = first; !date.isAfter(last);
                date = date.plusDays(1))
        {
            LocalDateTime start = request.startTime.isAfter(date.atStartOfDay())
                    ? request.startTime : date.atStartOfDay();
            LocalDateTime next = date.plusDays(1).atStartOfDay();
            LocalDateTime end = request.endTime.isBefore(next)
                    ? request.endTime : next;
            int minutes = Math.toIntExact(Duration.between(start, end)
                    .toMinutes());
            int paid = paidMinutes(type, minutes);
            ScheduleRef schedule = overlappingSchedule(refs, start, end);
            LeaveSegment segment = new LeaveSegment();
            segment.leaveRequestId = request.leaveRequestId;
            segment.businessDate = schedule == null
                    ? date : schedule.businessDate;
            segment.startTime = start;
            segment.endTime = end;
            segment.totalMinutes = minutes;
            segment.paidMinutes = paid;
            segment.unpaidMinutes = minutes - paid;
            segment.scheduleId = schedule == null
                    ? null : schedule.scheduleId;
            segment.segmentStatus = "ACTIVE";
            if (mapper.insertLeaveSegment(segment) != 1)
                throw new ServiceException("LEAVE_SEGMENT_SAVE_FAILED");
        }
    }

    private ScheduleRef overlappingSchedule(List<ScheduleRef> refs,
            LocalDateTime start, LocalDateTime end)
    {
        if (refs == null || refs.isEmpty()) return null;
        ScheduleRef selected = null;
        long longestOverlap = -1L;
        for (ScheduleRef ref : refs)
        {
            if (ref == null || ref.scheduleId == null
                    || ref.scheduleStart == null || ref.scheduleEnd == null)
                continue;
            LocalDateTime overlapStart = start.isAfter(ref.scheduleStart)
                    ? start : ref.scheduleStart;
            LocalDateTime overlapEnd = end.isBefore(ref.scheduleEnd)
                    ? end : ref.scheduleEnd;
            if (!overlapEnd.isAfter(overlapStart)) continue;
            long overlap = Duration.between(overlapStart, overlapEnd)
                    .toMinutes();
            if (overlap > longestOverlap)
            {
                longestOverlap = overlap;
                selected = ref;
            }
        }
        return selected;
    }

    private int paidMinutes(LeaveType type, int minutes)
    {
        return switch (type.payPolicy)
        {
            case "PAID" -> minutes;
            case "UNPAID" -> 0;
            default -> BigDecimal.valueOf(minutes)
                    .multiply(type.paidRatio == null ? BigDecimal.ZERO
                            : type.paidRatio)
                    .setScale(0, RoundingMode.HALF_UP).intValueExact();
        };
    }

    ApprovalStartRequest buildApprovalRequest(LeaveRequest value,
            LeaveType type, int attachmentCount)
    {
        ApprovalStartRequest request = new ApprovalStartRequest();
        request.setBusinessCode(
                AttendanceLeaveApprovalOutboxService.BUSINESS_CODE);
        request.setBusinessId(String.valueOf(value.leaveRequestId));
        request.setBusinessRound(value.businessRound);
        request.setApplicantId(value.userId);
        request.setApplicantName(value.userName);
        LoginUser login = SecurityUtils.getLoginUser();
        SysUser user = login == null ? null : login.getSysUser();
        request.setApplicantDeptId(user == null ? null : user.getDeptId());
        request.setAnchorDeptId(value.shopId);
        request.setAnchorDeptName(shopScopeService.resolveShopDeptName(
                value.shopId));
        request.setBusinessSubtype(type.typeCode);
        request.setIdempotencyKey(
                AttendanceLeaveApprovalOutboxService.BUSINESS_CODE + ":"
                        + value.leaveRequestId + ":" + value.businessRound);
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("leaveRequestId", value.leaveRequestId);
        variables.put("leaveRequestNo", value.leaveRequestNo);
        variables.put("leaveTypeCode", type.typeCode);
        variables.put("leaveTypeName", value.leaveTypeName);
        variables.put("startTime", value.startTime.toString());
        variables.put("endTime", value.endTime.toString());
        variables.put("totalMinutes", value.totalMinutes);
        variables.put("reason", value.reason);
        variables.put("attachmentCount", Math.max(0, attachmentCount));
        variables.put("attachmentRequired",
                requiresAttachment(type, value.totalMinutes));
        variables.put("shopId", value.shopId);
        request.setVariables(variables);
        Map<String, String> route = new LinkedHashMap<>();
        route.put("businessId", String.valueOf(value.leaveRequestId));
        route.put("leaveRequestId", String.valueOf(value.leaveRequestId));
        route.put("todoType", "OA_ATTENDANCE_LEAVE_APPROVAL");
        route.put("desktopPath", "/oa/attendance-v2?tab=leave");
        route.put("mobilePath", "/mobile/attendance?tab=leave");
        request.setRouteSnapshot(route);
        return request;
    }

    private LeaveRequest requireOwnedForUpdate(Long id, Long selectedShopId)
    {
        LeaveRequest value = id == null ? null
                : mapper.selectLeaveRequestByIdForUpdate(id);
        if (value == null) throw new ServiceException("LEAVE_REQUEST_NOT_FOUND");
        if (!Objects.equals(value.userId, SecurityUtils.getUserId()))
            throw new ServiceException("LEAVE_REQUEST_NOT_OWNED");
        requireSameShop(value.shopId, selectedShopId);
        return value;
    }

    private void requireReadable(LeaveRequest value, Long selectedShopId)
    {
        if (value == null) throw new ServiceException("LEAVE_REQUEST_NOT_FOUND");
        if (Objects.equals(value.userId, SecurityUtils.getUserId()))
        {
            requireSameShop(value.shopId, selectedShopId);
            return;
        }
        if (!hasPermission("oa:attendance:leave:list")
                && !hasPermission("oa:attendance:leave:approve"))
            throw new ServiceException("LEAVE_REQUEST_FORBIDDEN");
        requireSameShop(value.shopId, selectedShopId);
    }

    private LeaveRequest attachChildren(LeaveRequest value)
    {
        value.segments = mapper.selectLeaveSegments(value.leaveRequestId);
        value.attachments = mapper.selectLeaveAttachments(value.leaveRequestId);
        value.attachmentCount = value.attachments == null ? 0
                : value.attachments.size();
        return value;
    }

    private LeaveRequest replay(LeaveRequest value, String fingerprint)
    {
        if (!Objects.equals(value.clientRequestFingerprint, fingerprint))
            throw new ServiceException("LEAVE_CLIENT_REQUEST_ID_REUSED");
        return attachChildren(value);
    }

    private String draftFingerprint(SaveDraft body)
    {
        return AttendanceClientRequestSupport.fingerprint(
                "OA_ATTENDANCE_LEAVE_DRAFT_V1", body.leaveTypeId,
                body.startTime, body.endTime,
                body.reason == null ? null : body.reason.trim());
    }

    private LeaveType requireType(Long id)
    {
        LeaveType value = id == null ? null : mapper.selectLeaveTypeById(id);
        if (value == null) throw new ServiceException("LEAVE_TYPE_NOT_FOUND");
        return value;
    }

    private LeaveType requireEnabledType(Long id)
    {
        LeaveType value = requireType(id);
        if (!"ENABLED".equals(value.status))
            throw new ServiceException("LEAVE_TYPE_DISABLED");
        return value;
    }

    private void validateType(LeaveType value, boolean creating)
    {
        if (value == null) throw new ServiceException("LEAVE_TYPE_REQUIRED");
        if (creating)
        {
            value.typeCode = value.typeCode == null ? ""
                    : value.typeCode.trim().toUpperCase(Locale.ROOT);
            if (!value.typeCode.matches("[A-Z0-9_-]{2,32}"))
                throw new ServiceException("LEAVE_TYPE_CODE_INVALID");
        }
        value.typeName = value.typeName == null ? "" : value.typeName.trim();
        if (value.typeName.isEmpty() || value.typeName.length() > 64)
            throw new ServiceException("LEAVE_TYPE_NAME_INVALID");
        value.unitMode = upperOr(value.unitMode, "MINUTE");
        value.payPolicy = upperOr(value.payPolicy, "UNPAID");
        if (!UNIT_MODES.contains(value.unitMode)
                || !PAY_POLICIES.contains(value.payPolicy))
            throw new ServiceException("LEAVE_TYPE_POLICY_INVALID");
        value.paidRatio = value.paidRatio == null ? BigDecimal.ZERO
                : value.paidRatio;
        if ("PAID".equals(value.payPolicy)) value.paidRatio = BigDecimal.ONE;
        if ("UNPAID".equals(value.payPolicy)) value.paidRatio = BigDecimal.ZERO;
        if (value.paidRatio.compareTo(BigDecimal.ZERO) < 0
                || value.paidRatio.compareTo(BigDecimal.ONE) > 0)
            throw new ServiceException("LEAVE_PAID_RATIO_INVALID");
        value.balanceRequired = bool(value.balanceRequired, false);
        if (Boolean.TRUE.equals(value.balanceRequired))
            throw new ServiceException("LEAVE_BALANCE_POLICY_NOT_CONFIGURED");
        value.attachmentRequired = bool(value.attachmentRequired, false);
        value.allowCrossDay = bool(value.allowCrossDay, true);
        value.approvalRequired = bool(value.approvalRequired, true);
        value.minMinutes = positive(value.minMinutes, 30, 525600);
        value.stepMinutes = positive(value.stepMinutes, 30, 1440);
        if (value.minMinutes % value.stepMinutes != 0)
            throw new ServiceException("LEAVE_MINIMUM_STEP_MISMATCH");
        if (value.maxMinutesPerRequest != null
                && value.maxMinutesPerRequest < value.minMinutes)
            throw new ServiceException("LEAVE_MAXIMUM_INVALID");
        if (value.attachmentThresholdMinutes != null
                && value.attachmentThresholdMinutes <= 0)
            throw new ServiceException("LEAVE_ATTACHMENT_THRESHOLD_INVALID");
        value.sortNo = value.sortNo == null ? 0 : value.sortNo;
        if (value.sortNo < 0 || value.sortNo > 9999)
            throw new ServiceException("LEAVE_TYPE_SORT_INVALID");
    }

    private void touchDraft(LeaveRequest current)
    {
        current.setUpdateBy(operator());
        if (mapper.updateLeaveDraft(current) != 1)
            throw new ServiceException("LEAVE_DRAFT_VERSION_CONFLICT");
        current.rowVersion++;
    }

    private void requireEditable(LeaveRequest value)
    {
        if (!EDITABLE.contains(value.status))
            throw new ServiceException("LEAVE_STATUS_NOT_EDITABLE");
    }

    private String requireActiveEmployee(Long userId, Long shopId)
    {
        String name = mapper.selectActiveEmployeeNameInShop(userId, shopId);
        if (blank(name)) throw new ServiceException("EMPLOYEE_NOT_ACTIVE_IN_SHOP");
        return name;
    }

    private Long requireSelectedShop(Long selectedShopId)
    { return shopScopeService.resolveRequiredShopDept(selectedShopId); }

    private Long requireSameShop(Long requested, Long selectedShopId)
    {
        Long scope = requireSelectedShop(selectedShopId);
        if (requested == null || !requested.equals(scope))
            throw new ServiceException("ATTENDANCE_SHOP_SCOPE_MISMATCH");
        return scope;
    }

    private void validateRange(LocalDate from, LocalDate to)
    {
        if (from == null && to == null) return;
        if (from == null || to == null || to.isBefore(from)
                || ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS)
            throw new ServiceException("LEAVE_QUERY_RANGE_INVALID");
    }

    private boolean requiresAttachment(LeaveType type, Integer minutes)
    {
        return Boolean.TRUE.equals(type.attachmentRequired)
                || type.attachmentThresholdMinutes != null
                && minutes != null
                && minutes >= type.attachmentThresholdMinutes;
    }

    private void registerRollbackDelete(String path)
    {
        requireTransactionSynchronization();
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization()
                {
                    @Override public void afterCompletion(int status)
                    { if (status != STATUS_COMMITTED) storage.deleteQuietly(path); }
                });
    }

    private void registerCommitDelete(String path)
    {
        requireTransactionSynchronization();
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization()
                {
                    @Override public void afterCommit()
                    { storage.deleteQuietly(path); }
                });
    }

    private void requireTransactionSynchronization()
    {
        if (!TransactionSynchronizationManager.isSynchronizationActive())
            throw new ServiceException("LEAVE_ATTACHMENT_TRANSACTION_REQUIRED");
    }

    private void requireVersion(Long expected, Long actual)
    {
        if (expected == null || !Objects.equals(expected, actual))
            throw new ServiceException("LEAVE_VERSION_CONFLICT");
    }

    private String normalizeStatus(String status)
    {
        if (blank(status)) return null;
        String value = status.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("DRAFT", "SUBMITTING", "PENDING", "APPROVED",
                "REJECTED", "RETURNED", "CANCELLED").contains(value))
            throw new ServiceException("LEAVE_STATUS_INVALID");
        return value;
    }

    private String normalizeReason(String reason)
    {
        String value = reason == null ? "" : reason.trim();
        if (value.isEmpty() || value.length() > 500)
            throw new ServiceException("LEAVE_WITHDRAW_REASON_INVALID");
        return value;
    }

    private boolean hasPermission(String permission)
    {
        LoginUser login = SecurityUtils.getLoginUser();
        return SecurityUtils.isAdmin() || login != null
                && login.getPermissions() != null
                && (login.getPermissions().contains(permission)
                        || login.getPermissions().contains("*:*:*"));
    }

    private String operator()
    {
        String value = SecurityUtils.getUsername();
        return blank(value) ? String.valueOf(SecurityUtils.getUserId())
                : value.trim();
    }

    private String nextNo(String prefix)
    {
        return prefix + LocalDateTime.now(clock).withNano(0)
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().replace("-", "")
                        .substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private String upperOr(String value, String fallback)
    { return blank(value) ? fallback : value.trim().toUpperCase(Locale.ROOT); }
    private Boolean bool(Boolean value, boolean fallback)
    { return value == null ? fallback : value; }
    private int positive(Integer value, int fallback, int max)
    {
        int result = value == null ? fallback : value;
        if (result <= 0 || result > max)
            throw new ServiceException("LEAVE_POLICY_MINUTE_INVALID");
        return result;
    }
    private boolean blank(String value)
    { return value == null || value.isBlank(); }
    private void requireEnabled()
    { featureGate.requireEnabled(BusinessFeatureGate.ATTENDANCE_V2); }

    public record AttachmentContent(Path path, String fileName,
            String contentType, long size) { }
}
