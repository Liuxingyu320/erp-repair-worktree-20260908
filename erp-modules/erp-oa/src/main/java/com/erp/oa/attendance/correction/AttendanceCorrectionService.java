package com.erp.oa.attendance.correction;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
import com.erp.approval.api.domain.ApprovalStartRequest;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.oa.attendance.correction.AttendanceCorrectionModels.ApprovalOutbox;
import com.erp.oa.attendance.correction.AttendanceCorrectionModels.CorrectionRequest;
import com.erp.oa.attendance.correction.AttendanceCorrectionModels.PunchEventRef;
import com.erp.oa.attendance.correction.AttendanceCorrectionModels.PunchSlotRef;
import com.erp.oa.attendance.correction.AttendanceCorrectionModels.ScheduleRef;
import com.erp.oa.attendance.correction.AttendanceCorrectionModels.ScheduleSegmentRef;
import com.erp.oa.attendance.correction.AttendanceCorrectionRequests.SaveDraft;
import com.erp.oa.attendance.domain.AttendanceModels.Schedule;
import com.erp.oa.attendance.domain.AttendanceModels.ScheduleSegmentSnapshot;
import com.erp.oa.attendance.domain.AttendanceSettlementModels.LeaveSegmentSource;
import com.erp.oa.attendance.support.AttendanceLeaveCoveragePolicy;
import com.erp.oa.attendance.support.AttendanceLeaveCoveragePolicy.Coverage;
import com.erp.oa.attendance.support.AttendanceRuleEngine;
import com.erp.oa.attendance.support.AttendanceRuleEngine.PunchWindow;
import com.erp.oa.attendance.support.AttendanceClientRequestSupport;
import com.erp.oa.service.BusinessFeatureGate;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.model.LoginUser;

@Service
public class AttendanceCorrectionService
{
    private static final Set<String> EDITABLE = Set.of("DRAFT", "RETURNED");
    private static final Set<String> CORRECTION_TYPES = Set.of(
            "MISSING_PUNCH", "WRONG_TIME", "WRONG_TYPE");
    private static final Set<String> PUNCH_TYPES = Set.of("IN", "OUT");
    private static final String SHIFT_BOUNDARY =
            AttendanceRuleEngine.SHIFT_BOUNDARY;
    private static final String PER_WORK_SEGMENT =
            AttendanceRuleEngine.PER_WORK_SEGMENT;
    private static final int MAX_RANGE_DAYS = 366;

    private final AttendanceCorrectionMapper mapper;
    private final AttendanceCorrectionApprovalOutboxService outboxService;
    private final AttendanceCorrectionApprovalAfterCommitTrigger trigger;
    private final ShopScopeService shopScopeService;
    private final BusinessFeatureGate featureGate;
    private final AttendanceRuleEngine rules;
    private final Clock clock;

    @Autowired
    public AttendanceCorrectionService(AttendanceCorrectionMapper mapper,
            AttendanceCorrectionApprovalOutboxService outboxService,
            AttendanceCorrectionApprovalAfterCommitTrigger trigger,
            ShopScopeService shopScopeService,
            BusinessFeatureGate featureGate, AttendanceRuleEngine rules)
    {
        this(mapper, outboxService, trigger, shopScopeService, featureGate,
                rules, Clock.systemDefaultZone());
    }

    public AttendanceCorrectionService(AttendanceCorrectionMapper mapper,
            AttendanceCorrectionApprovalOutboxService outboxService,
            AttendanceCorrectionApprovalAfterCommitTrigger trigger,
            ShopScopeService shopScopeService,
            BusinessFeatureGate featureGate)
    {
        this(mapper, outboxService, trigger, shopScopeService, featureGate,
                new AttendanceRuleEngine(), Clock.systemDefaultZone());
    }

    AttendanceCorrectionService(AttendanceCorrectionMapper mapper,
            AttendanceCorrectionApprovalOutboxService outboxService,
            AttendanceCorrectionApprovalAfterCommitTrigger trigger,
            ShopScopeService shopScopeService,
            BusinessFeatureGate featureGate, AttendanceRuleEngine rules,
            Clock clock)
    {
        this.mapper = mapper;
        this.outboxService = outboxService;
        this.trigger = trigger;
        this.shopScopeService = shopScopeService;
        this.featureGate = featureGate;
        this.rules = rules;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public CorrectionRequest saveDraft(SaveDraft body, Long selectedShopId)
    {
        requireEnabled();
        if (body == null)
            throw new ServiceException("CORRECTION_DRAFT_REQUIRED");
        Long userId = SecurityUtils.getUserId();
        if (body.correctionRequestId == null)
        {
            Long shopId = requireSelectedShop(selectedShopId);
            String clientRequestId = AttendanceClientRequestSupport
                    .normalizeOptional(body.clientRequestId,
                            "CORRECTION_CLIENT_REQUEST_ID_INVALID");
            String fingerprint = clientRequestId == null ? null
                    : draftFingerprint(body);
            if (clientRequestId != null)
            {
                CorrectionRequest replay = mapper
                        .selectCorrectionRequestByClientRequestId(userId,
                                shopId, clientRequestId);
                if (replay != null)
                    return replay(replay, fingerprint);
            }
            ScheduleRef schedule = requireOwnedScheduleForUpdate(
                    body.scheduleId, userId, selectedShopId);
            String userName = requireActiveEmployee(userId, schedule.shopId);
            DraftValues values = validateDraft(body, schedule, userId, null);
            CorrectionRequest row = new CorrectionRequest();
            row.correctionRequestNo = nextNo();
            row.clientRequestId = clientRequestId;
            row.clientRequestFingerprint = fingerprint;
            row.userId = userId;
            row.userName = userName;
            row.shopId = schedule.shopId;
            apply(row, body, schedule, values);
            row.status = "DRAFT";
            row.businessRound = 0;
            row.rowVersion = 0L;
            row.setCreateBy(operator());
            try
            {
                if (mapper.insertCorrectionRequest(row) != 1)
                    throw new ServiceException(
                            "CORRECTION_DRAFT_CREATE_FAILED");
            }
            catch (DuplicateKeyException duplicate)
            {
                if (clientRequestId == null) throw duplicate;
                CorrectionRequest replay = mapper
                        .selectCorrectionRequestByClientRequestIdForUpdate(
                                userId, shopId, clientRequestId);
                if (replay == null) throw duplicate;
                return replay(replay, fingerprint);
            }
            return detail(row.correctionRequestId, selectedShopId);
        }

        ScheduleRef schedule = requireOwnedScheduleForUpdate(body.scheduleId,
                userId, selectedShopId);
        String userName = requireActiveEmployee(userId, schedule.shopId);
        DraftValues values = validateDraft(body, schedule, userId,
                body.correctionRequestId);
        CorrectionRequest current = requireOwnedForUpdate(
                body.correctionRequestId, selectedShopId);
        if (!EDITABLE.contains(current.status))
            throw new ServiceException("CORRECTION_STATUS_NOT_EDITABLE");
        requireVersion(body.rowVersion, current.rowVersion);
        if (!Objects.equals(current.userId, schedule.userId))
            throw new ServiceException("CORRECTION_SCHEDULE_NOT_OWNED");
        apply(current, body, schedule, values);
        current.userName = userName;
        current.setUpdateBy(operator());
        if (mapper.updateCorrectionDraft(current) != 1)
            throw new ServiceException("CORRECTION_DRAFT_VERSION_CONFLICT");
        return detail(current.correctionRequestId, selectedShopId);
    }

    public CorrectionRequest detail(Long id, Long selectedShopId)
    {
        requireEnabled();
        CorrectionRequest value = mapper.selectCorrectionRequestById(id);
        requireReadable(value, selectedShopId);
        enrichCorrectionTarget(value);
        return value;
    }

    public CorrectionRequest byClientRequest(String clientRequestId,
            Long selectedShopId)
    {
        requireEnabled();
        String key = AttendanceClientRequestSupport.normalizeOptional(
                clientRequestId, "CORRECTION_CLIENT_REQUEST_ID_INVALID");
        if (key == null)
            throw new ServiceException(
                    "CORRECTION_CLIENT_REQUEST_ID_INVALID");
        Long shopId = requireSelectedShop(selectedShopId);
        CorrectionRequest value = mapper
                .selectCorrectionRequestByClientRequestId(
                        SecurityUtils.getUserId(), shopId, key);
        if (value == null)
            throw new ServiceException(
                    "CORRECTION_CLIENT_REQUEST_NOT_FOUND");
        enrichCorrectionTarget(value);
        return value;
    }

    public List<ScheduleRef> eligibleSchedules(LocalDate dateFrom,
            LocalDate dateTo, Long selectedShopId)
    {
        requireEnabled();
        validateRange(dateFrom, dateTo);
        if (dateFrom == null)
            throw new ServiceException("CORRECTION_QUERY_RANGE_REQUIRED");
        Long shopId = requireSelectedShop(selectedShopId);
        Long userId = SecurityUtils.getUserId();
        requireActiveEmployee(userId, shopId);
        List<ScheduleRef> rows = mapper.selectOwnedScheduleRefs(userId,
                shopId, dateFrom, dateTo);
        if (rows == null) return List.of();
        for (ScheduleRef row : rows)
            populateCorrectionPunchSlots(row, userId);
        return rows;
    }

    public List<PunchEventRef> eligiblePunchEvents(Long scheduleId,
            Long selectedShopId)
    {
        requireEnabled();
        Long userId = SecurityUtils.getUserId();
        ScheduleRef schedule = requireOwnedSchedule(scheduleId, userId,
                selectedShopId);
        List<PunchEventRef> rows = mapper.selectSchedulePunchEvents(
                schedule.scheduleId, userId);
        if (rows == null) return List.of();
        String mode = punchMode(schedule);
        Map<Long, ScheduleSegmentRef> workById = new LinkedHashMap<>();
        Map<Long, String> labels = Map.of();
        if (PER_WORK_SEGMENT.equals(mode))
        {
            List<ScheduleSegmentRef> segments = mapper
                    .selectScheduleSegmentRefs(schedule.scheduleId);
            for (ScheduleSegmentRef segment : workSegments(segments,
                    schedule.scheduleId))
                workById.put(segment.scheduleSegmentSnapshotId, segment);
            labels = segmentLabels(segments);
        }
        List<PunchEventRef> eligible = new ArrayList<>();
        for (PunchEventRef row : rows)
        {
            if (!eligiblePunchEvent(row, schedule, userId, mode, workById))
                continue;
            if (mapper.countActiveCorrectionForOriginalEvent(userId,
                    row.punchEventId, null) > 0)
                continue;
            if (PER_WORK_SEGMENT.equals(mode))
                row.segmentLabel = labels.get(
                        row.scheduleSegmentSnapshotId);
            eligible.add(row);
        }
        return eligible;
    }

    public List<CorrectionRequest> listMy(String status, LocalDate dateFrom,
            LocalDate dateTo, Long selectedShopId)
    {
        requireEnabled();
        validateRange(dateFrom, dateTo);
        Long shopId = requireSelectedShop(selectedShopId);
        List<CorrectionRequest> rows = mapper.selectCorrectionRequestsByUser(
                SecurityUtils.getUserId(), shopId, normalizeStatus(status),
                dateFrom, dateTo);
        if (rows == null) return List.of();
        for (CorrectionRequest row : rows) enrichCorrectionTarget(row);
        return rows;
    }

    public List<CorrectionRequest> listShop(Long shopId, Long userId,
            String status, LocalDate dateFrom, LocalDate dateTo,
            Long selectedShopId)
    {
        requireEnabled();
        validateRange(dateFrom, dateTo);
        Long target = requireSameShop(shopId, selectedShopId);
        List<CorrectionRequest> rows = mapper.selectCorrectionRequestsByShop(
                target, userId, normalizeStatus(status), dateFrom, dateTo);
        if (rows == null) return List.of();
        for (CorrectionRequest row : rows) enrichCorrectionTarget(row);
        return rows;
    }

    @Transactional(rollbackFor = Exception.class)
    public CorrectionRequest submit(Long correctionRequestId,
            Long expectedVersion, Long selectedShopId)
    {
        requireEnabled();
        CorrectionRequest observed = requireOwned(correctionRequestId,
                selectedShopId);
        ScheduleRef schedule = requireOwnedScheduleForUpdate(
                observed.scheduleId, observed.userId, selectedShopId);
        CorrectionRequest current = requireOwnedForUpdate(
                correctionRequestId, selectedShopId);
        if (!Objects.equals(current.scheduleId, schedule.scheduleId))
            throw new ServiceException(
                    "CORRECTION_SCHEDULE_CHANGED_RETRY");
        if ("PENDING".equals(current.status)
                && current.approvalInstanceId != null)
            return detail(correctionRequestId, selectedShopId);
        if ("SUBMITTING".equals(current.status))
        {
            ApprovalOutbox existing = outboxService.selectByRound(
                    correctionRequestId, current.businessRound);
            if (existing == null)
                throw new ServiceException(
                        "CORRECTION_APPROVAL_OUTBOX_MISSING");
            if (AttendanceCorrectionApprovalOutboxService.FAILED.equals(
                    existing.status))
                throw new ServiceException(
                        "CORRECTION_APPROVAL_START_FAILED");
            if (AttendanceCorrectionApprovalOutboxService.COMPLETED.equals(
                    existing.status))
                throw new ServiceException(
                        "CORRECTION_APPROVAL_LOCAL_LINK_INCONSISTENT");
            trigger.trigger(existing.outboxId);
            return detail(correctionRequestId, selectedShopId);
        }
        requireVersion(expectedVersion, current.rowVersion);
        if (!EDITABLE.contains(current.status))
            throw new ServiceException("CORRECTION_STATUS_NOT_EDITABLE");
        requireActiveEmployee(current.userId, current.shopId);
        validateCurrent(current, schedule);
        int round = (current.businessRound == null ? 0
                : current.businessRound) + 1;
        Long version = current.rowVersion;
        if (mapper.markCorrectionSubmitting(correctionRequestId,
                current.status, version, round, operator()) != 1)
            throw new ServiceException("CORRECTION_SUBMIT_VERSION_CONFLICT");
        mapper.invalidateDayResultForCorrection(correctionRequestId,
                operator());
        current.status = "SUBMITTING";
        current.businessRound = round;
        current.approvalInstanceId = null;
        current.rowVersion = version + 1;
        ApprovalOutbox outbox = outboxService.enqueue(current,
                buildApprovalRequest(current), operator());
        trigger.trigger(outbox.outboxId);
        return detail(correctionRequestId, selectedShopId);
    }

    private DraftValues validateDraft(SaveDraft body, ScheduleRef schedule,
            Long userId, Long excludeId)
    {
        String correctionType = upper(body.correctionType);
        String punchType = upper(body.targetPunchType);
        if ("OTHER".equals(correctionType))
            throw new ServiceException("CORRECTION_OTHER_NOT_SUPPORTED");
        if (!CORRECTION_TYPES.contains(correctionType))
            throw new ServiceException("CORRECTION_TYPE_INVALID");
        if (!PUNCH_TYPES.contains(punchType))
            throw new ServiceException("CORRECTION_PUNCH_TYPE_INVALID");
        PunchTarget target = resolvePunchTarget(body, schedule, punchType);
        validateRequestedTime(body.requestedPunchTime, schedule, punchType,
                target.segment());
        validateApprovedLeave(correctionType, punchType, schedule, target);
        PunchEventRef event = validateOriginalEvent(body.originalPunchEventId,
                correctionType, punchType, schedule, userId,
                body.requestedPunchTime, target);
        String reason = body.reason == null ? "" : body.reason.trim();
        if (reason.isEmpty() || reason.length() > 1000)
            throw new ServiceException("CORRECTION_REASON_INVALID");
        if (event != null && mapper.countActiveCorrectionForOriginalEvent(
                userId, event.punchEventId, excludeId) > 0)
            throw new ServiceException(
                    "CORRECTION_ORIGINAL_EVENT_ALREADY_CORRECTED");
        int duplicates = target.segment() == null
                ? mapper.countActiveCorrection(userId, schedule.scheduleId,
                        punchType, excludeId)
                : mapper.countActiveCorrectionForSlot(userId,
                        schedule.scheduleId, target.punchSlotKey(), excludeId);
        if (duplicates > 0)
            throw new ServiceException("CORRECTION_ALREADY_EXISTS");
        return new DraftValues(correctionType, punchType, reason,
                event == null ? null : event.punchEventId,
                target.scheduleSegmentSnapshotId(), target.punchSlotKey());
    }

    private void validateCurrent(CorrectionRequest current,
            ScheduleRef schedule)
    {
        SaveDraft body = new SaveDraft();
        body.correctionRequestId = current.correctionRequestId;
        body.scheduleId = current.scheduleId;
        body.correctionType = current.correctionType;
        body.targetPunchType = current.targetPunchType;
        body.targetScheduleSegmentSnapshotId =
                current.targetScheduleSegmentSnapshotId;
        body.targetPunchSlotKey = current.targetPunchSlotKey;
        body.originalPunchEventId = current.originalPunchEventId;
        body.requestedPunchTime = current.requestedPunchTime;
        body.reason = current.reason;
        validateDraft(body, schedule, current.userId,
                current.correctionRequestId);
    }

    private PunchEventRef validateOriginalEvent(Long eventId,
            String correctionType, String targetPunchType,
            ScheduleRef schedule, Long userId,
            LocalDateTime requestedPunchTime, PunchTarget target)
    {
        if ("MISSING_PUNCH".equals(correctionType))
        {
            if (eventId != null)
                throw new ServiceException(
                        "CORRECTION_MISSING_PUNCH_EVENT_FORBIDDEN");
            int accepted = target.segment() == null
                    ? mapper.countAcceptedPunchEvents(schedule.scheduleId,
                            userId, targetPunchType)
                    : mapper.countAcceptedPunchEventsForSlot(
                            schedule.scheduleId, userId,
                            target.punchSlotKey());
            if (accepted > 0)
                throw new ServiceException(
                        "CORRECTION_PUNCH_ALREADY_EXISTS");
            return null;
        }
        if (("WRONG_TIME".equals(correctionType)
                || "WRONG_TYPE".equals(correctionType)) && eventId == null)
            throw new ServiceException("CORRECTION_ORIGINAL_EVENT_REQUIRED");
        if (eventId == null) return null;
        if ("WRONG_TYPE".equals(correctionType))
        {
            int acceptedAtTarget = target.segment() == null
                    ? mapper.countAcceptedPunchEvents(schedule.scheduleId,
                            userId, targetPunchType)
                    : mapper.countAcceptedPunchEventsForSlot(
                            schedule.scheduleId, userId,
                            target.punchSlotKey());
            if (acceptedAtTarget > 0)
                throw new ServiceException(
                        "CORRECTION_TARGET_PUNCH_ALREADY_EXISTS");
        }
        PunchEventRef event = mapper.selectPunchEventRef(eventId);
        if (event == null || !Objects.equals(event.scheduleId,
                schedule.scheduleId) || !Objects.equals(event.userId, userId)
                || !Objects.equals(event.shopId, schedule.shopId)
                || !Objects.equals(event.businessDate, schedule.businessDate)
                || !"ACCEPTED".equals(event.verificationStatus)
                || event.serverPunchTime == null
                || !PUNCH_TYPES.contains(upper(event.punchType)))
            throw new ServiceException("CORRECTION_ORIGINAL_EVENT_INVALID");
        if (target.segment() != null)
        {
            if (!Objects.equals(event.scheduleSegmentSnapshotId,
                    target.scheduleSegmentSnapshotId())
                    || !Objects.equals(event.punchSlotKey,
                            slotKey(target.scheduleSegmentSnapshotId(),
                                    event.punchType)))
                throw new ServiceException(
                        "CORRECTION_ORIGINAL_EVENT_SLOT_INVALID");
            if (!"WRONG_TYPE".equals(correctionType)
                    && !Objects.equals(event.punchSlotKey,
                            target.punchSlotKey()))
                throw new ServiceException(
                        "CORRECTION_ORIGINAL_EVENT_SLOT_MISMATCH");
        }
        else if (event.scheduleSegmentSnapshotId != null
                || event.punchSlotKey != null
                        && !event.punchSlotKey.isBlank())
            throw new ServiceException(
                    "CORRECTION_ORIGINAL_EVENT_SLOT_INVALID");
        if ("WRONG_TYPE".equals(correctionType)
                && Objects.equals(event.punchType, targetPunchType))
            throw new ServiceException(
                    "CORRECTION_TARGET_TYPE_MUST_CHANGE");
        if ("WRONG_TIME".equals(correctionType)
                && !Objects.equals(event.punchType, targetPunchType))
            throw new ServiceException(
                    "CORRECTION_WRONG_TIME_TYPE_MISMATCH");
        if ("WRONG_TIME".equals(correctionType)
                && event.serverPunchTime != null
                && event.serverPunchTime.withSecond(0).withNano(0)
                        .equals(requestedPunchTime))
            throw new ServiceException("CORRECTION_TIME_UNCHANGED");
        return event;
    }

    private void validateApprovedLeave(String correctionType,
            String punchType, ScheduleRef schedule, PunchTarget target)
    {
        if (!"MISSING_PUNCH".equals(correctionType)) return;
        List<ScheduleSegmentRef> work = workSegments(
                mapper.selectScheduleSegmentRefs(schedule.scheduleId),
                schedule.scheduleId);
        Coverage coverage;
        boolean fullyCovered;
        if (target.segment() != null)
        {
            LocalDateTime start = schedule.businessDate.atStartOfDay()
                    .plusMinutes(
                    target.segment().startMinuteOffset);
            LocalDateTime end = schedule.businessDate.atStartOfDay()
                    .plusMinutes(
                    target.segment().endMinuteOffset);
            coverage = AttendanceLeaveCoveragePolicy.analyze(start, end,
                    approvedLeaves(schedule));
            fullyCovered = coverage.fullyCovered();
        }
        else
        {
            List<LeaveSegmentSource> leaves = approvedLeaves(schedule);
            LocalDateTime first = segmentStart(schedule, work.get(0));
            LocalDateTime last = segmentEnd(schedule,
                    work.get(work.size() - 1));
            coverage = AttendanceLeaveCoveragePolicy.analyze(first, last,
                    leaves);
            fullyCovered = work.stream().allMatch(segment ->
                    AttendanceLeaveCoveragePolicy.analyze(
                            segmentStart(schedule, segment),
                            segmentEnd(schedule, segment), leaves)
                            .fullyCovered());
        }
        if (coverage.startCovered() && coverage.endCovered()
                && !fullyCovered)
            throw new ServiceException(
                    "CORRECTION_TARGET_REQUIRES_REMAINING_WORK_CONFIRMATION");
        boolean boundaryCovered = "IN".equals(punchType)
                ? coverage.startCovered() : coverage.endCovered();
        if (fullyCovered || boundaryCovered)
            throw new ServiceException(
                    "CORRECTION_TARGET_COVERED_BY_APPROVED_LEAVE");
    }

    private void validateRequestedTime(LocalDateTime value,
            ScheduleRef schedule, String punchType,
            ScheduleSegmentRef segment)
    {
        if (value == null || value.getSecond() != 0 || value.getNano() != 0)
            throw new ServiceException(
                    "CORRECTION_TIME_MUST_ALIGN_TO_MINUTE");
        if (schedule.businessDate == null)
            throw new ServiceException("CORRECTION_SCHEDULE_SNAPSHOT_INVALID");
        LocalDateTime anchor;
        int openMinutes;
        int closeMinutes;
        if (segment != null)
        {
            validateSegmentSnapshot(segment, schedule.scheduleId);
            List<ScheduleSegmentRef> work = workSegments(
                    mapper.selectScheduleSegmentRefs(schedule.scheduleId),
                    schedule.scheduleId);
            int index = workIndex(work,
                    segment.scheduleSegmentSnapshotId);
            PunchWindow window = effectiveCorrectionWindow(schedule, work,
                    index, punchType);
            if (value.isBefore(window.opensAt())
                    || value.isAfter(window.closesAt()))
                throw new ServiceException(
                        "CORRECTION_TIME_OUTSIDE_PUNCH_WINDOW");
            return;
        }
        else if ("IN".equals(punchType))
        {
            if (schedule.startTimeSnapshot == null)
                throw new ServiceException(
                        "CORRECTION_SCHEDULE_SNAPSHOT_INVALID");
            anchor = schedule.businessDate.atTime(
                    schedule.startTimeSnapshot);
            openMinutes = requiredNonNegative(
                    schedule.checkInOpenMinutesSnapshot);
            closeMinutes = requiredNonNegative(
                    schedule.checkInCloseMinutesSnapshot);
        }
        else
        {
            if (schedule.endTimeSnapshot == null)
                throw new ServiceException(
                        "CORRECTION_SCHEDULE_SNAPSHOT_INVALID");
            LocalDate outDate = Boolean.TRUE.equals(schedule.crossDaySnapshot)
                    ? schedule.businessDate.plusDays(1)
                    : schedule.businessDate;
            anchor = outDate.atTime(schedule.endTimeSnapshot);
            openMinutes = requiredNonNegative(
                    schedule.checkOutOpenMinutesSnapshot);
            closeMinutes = requiredNonNegative(
                    schedule.checkOutCloseMinutesSnapshot);
        }
        LocalDateTime open = anchor.minusMinutes(openMinutes);
        LocalDateTime close = anchor.plusMinutes(closeMinutes);
        if (value.isBefore(open) || value.isAfter(close))
            throw new ServiceException("CORRECTION_TIME_OUTSIDE_PUNCH_WINDOW");
    }

    private PunchTarget resolvePunchTarget(SaveDraft body,
            ScheduleRef schedule, String punchType)
    {
        String mode = punchMode(schedule);
        Long snapshotId = body.targetScheduleSegmentSnapshotId;
        String requestedKey = body.targetPunchSlotKey == null ? null
                : body.targetPunchSlotKey.trim().toUpperCase(Locale.ROOT);
        if (SHIFT_BOUNDARY.equals(mode))
        {
            if (snapshotId != null || requestedKey != null
                    && !requestedKey.isEmpty())
                throw new ServiceException(
                        "CORRECTION_PUNCH_SLOT_NOT_ALLOWED");
            return new PunchTarget(null, null, null);
        }
        if (!PER_WORK_SEGMENT.equals(mode))
            throw new ServiceException(
                    "CORRECTION_PUNCH_MODE_INVALID");
        if (snapshotId == null || snapshotId <= 0 || requestedKey == null
                || requestedKey.isEmpty())
            throw new ServiceException(
                    "CORRECTION_PUNCH_SLOT_REQUIRED");
        ScheduleSegmentRef segment = mapper.selectScheduleSegmentRef(
                schedule.scheduleId, snapshotId);
        validateSegmentSnapshot(segment, schedule.scheduleId);
        String expectedKey = slotKey(snapshotId, punchType);
        if (!expectedKey.equals(requestedKey))
            throw new ServiceException(
                    "CORRECTION_PUNCH_SLOT_MISMATCH");
        return new PunchTarget(snapshotId, expectedKey, segment);
    }

    private void populateCorrectionPunchSlots(ScheduleRef schedule,
            Long userId)
    {
        schedule.punchSlots = new ArrayList<>();
        if (!PER_WORK_SEGMENT.equals(punchMode(schedule))) return;
        List<ScheduleSegmentRef> segments = mapper
                .selectScheduleSegmentRefs(schedule.scheduleId);
        List<ScheduleSegmentRef> work = workSegments(segments,
                schedule.scheduleId);
        List<PunchEventRef> events = mapper.selectSchedulePunchEvents(
                schedule.scheduleId, userId);
        List<LeaveSegmentSource> approvedLeaves = mapper
                .selectApprovedLeaveSegmentsForSchedule(schedule.scheduleId,
                        userId, schedule.shopId);
        Map<Long, ScheduleSegmentRef> workById = new LinkedHashMap<>();
        for (ScheduleSegmentRef segment : work)
            workById.put(segment.scheduleSegmentSnapshotId, segment);
        Map<String, PunchEventRef> accepted = new LinkedHashMap<>();
        if (events != null)
            for (PunchEventRef event : events)
            {
                if (!eligiblePunchEvent(event, schedule, userId,
                        PER_WORK_SEGMENT, workById))
                    continue;
                ScheduleSegmentRef segment = workById.get(
                        event.scheduleSegmentSnapshotId);
                String eventType = upper(event.punchType);
                String expectedKey = slotKey(
                        segment.scheduleSegmentSnapshotId, eventType);
                if (expectedKey.equals(event.punchSlotKey))
                    accepted.putIfAbsent(expectedKey, event);
            }
        for (int index = 0; index < work.size(); index++)
        {
            ScheduleSegmentRef segment = work.get(index);
            String label = workSegmentLabel(index, work.size());
            schedule.punchSlots.add(correctionSlot(schedule, work, index,
                    "IN", label, accepted, approvedLeaves));
            schedule.punchSlots.add(correctionSlot(schedule, work, index,
                    "OUT", label, accepted, approvedLeaves));
        }
    }

    private void enrichCorrectionTarget(CorrectionRequest value)
    {
        if (value == null || value.targetScheduleSegmentSnapshotId == null)
            return;
        ScheduleSegmentRef segment = mapper.selectScheduleSegmentRef(
                value.scheduleId, value.targetScheduleSegmentSnapshotId);
        validateSegmentSnapshot(segment, value.scheduleId);
        value.targetSegmentOrder = segment.segmentOrder;
        value.targetSegmentLabelSnapshot = segmentLabels(
                mapper.selectScheduleSegmentRefs(value.scheduleId)).get(
                        value.targetScheduleSegmentSnapshotId);
    }

    private PunchSlotRef correctionSlot(ScheduleRef schedule,
            List<ScheduleSegmentRef> work, int index, String punchType,
            String label,
            Map<String, PunchEventRef> accepted,
            List<LeaveSegmentSource> approvedLeaves)
    {
        ScheduleSegmentRef segment = work.get(index);
        PunchSlotRef slot = new PunchSlotRef();
        slot.scheduleSegmentSnapshotId =
                segment.scheduleSegmentSnapshotId;
        slot.segmentOrder = segment.segmentOrder;
        slot.segmentLabel = label;
        slot.punchType = punchType;
        slot.punchSlotKey = slotKey(segment.scheduleSegmentSnapshotId,
                punchType);
        slot.startAt = schedule.businessDate.atStartOfDay().plusMinutes(
                segment.startMinuteOffset);
        slot.endAt = schedule.businessDate.atStartOfDay().plusMinutes(
                segment.endMinuteOffset);
        PunchWindow window = effectiveCorrectionWindow(schedule, work, index,
                punchType);
        slot.opensAt = window.opensAt();
        slot.closesAt = window.closesAt();
        PunchEventRef event = accepted.get(slot.punchSlotKey);
        slot.completed = event != null;
        slot.correctionPending = mapper.countActiveCorrectionForSlot(
                schedule.userId, schedule.scheduleId, slot.punchSlotKey,
                null) > 0;
        Coverage coverage = AttendanceLeaveCoveragePolicy.analyze(
                slot.startAt, slot.endAt, approvedLeaves);
        slot.requiresRemainingWorkConfirmation = coverage
                .requiresRemainingWorkConfirmation();
        slot.coveredByApprovedLeave = "IN".equals(punchType)
                ? coverage.startCovered() : coverage.endCovered();
        if (event != null) slot.status = "PUNCHED";
        else if (slot.requiresRemainingWorkConfirmation)
            slot.status = "REMAINING_WORK_PENDING";
        else if (slot.correctionPending)
            slot.status = "CORRECTION_PENDING";
        else if (slot.coveredByApprovedLeave)
            slot.status = "APPROVED_LEAVE";
        else slot.status = "MISSING";
        slot.eligibleForMissingPunch = "MISSING".equals(slot.status);
        slot.punchEventId = event == null ? null : event.punchEventId;
        return slot;
    }

    private boolean eligiblePunchEvent(PunchEventRef event,
            ScheduleRef schedule, Long userId, String mode,
            Map<Long, ScheduleSegmentRef> workById)
    {
        if (event == null || event.punchEventId == null
                || !Objects.equals(event.scheduleId, schedule.scheduleId)
                || !Objects.equals(event.userId, userId)
                || !Objects.equals(event.shopId, schedule.shopId)
                || !Objects.equals(event.businessDate,
                        schedule.businessDate)
                || !"ACCEPTED".equals(event.verificationStatus)
                || event.serverPunchTime == null
                || !PUNCH_TYPES.contains(upper(event.punchType)))
            return false;
        if (SHIFT_BOUNDARY.equals(mode))
            return event.scheduleSegmentSnapshotId == null
                    && (event.punchSlotKey == null
                            || event.punchSlotKey.isBlank());
        ScheduleSegmentRef segment = workById.get(
                event.scheduleSegmentSnapshotId);
        return segment != null && Objects.equals(event.punchSlotKey,
                slotKey(segment.scheduleSegmentSnapshotId,
                        event.punchType));
    }

    private PunchWindow effectiveCorrectionWindow(ScheduleRef schedule,
            List<ScheduleSegmentRef> work, int index, String punchType)
    {
        if (work == null || index < 0 || index >= work.size())
            throw new ServiceException(
                    "CORRECTION_WORK_SEGMENT_SNAPSHOT_MISSING");
        ScheduleSegmentRef segment = work.get(index);
        return rules.effectiveSegmentWindow(ruleSchedule(schedule),
                segmentStart(schedule, segment), segmentEnd(schedule, segment),
                index > 0 ? segmentEnd(schedule, work.get(index - 1)) : null,
                index + 1 < work.size()
                        ? segmentStart(schedule, work.get(index + 1)) : null,
                punchType);
    }

    private int workIndex(List<ScheduleSegmentRef> work, Long snapshotId)
    {
        for (int index = 0; index < work.size(); index++)
            if (Objects.equals(snapshotId,
                    work.get(index).scheduleSegmentSnapshotId)) return index;
        throw new ServiceException("CORRECTION_TARGET_SEGMENT_INVALID");
    }

    private List<LeaveSegmentSource> approvedLeaves(ScheduleRef schedule)
    {
        List<LeaveSegmentSource> values = mapper
                .selectApprovedLeaveSegmentsForSchedule(schedule.scheduleId,
                        schedule.userId, schedule.shopId);
        return values == null ? List.of() : values;
    }

    private LocalDateTime segmentStart(ScheduleRef schedule,
            ScheduleSegmentRef segment)
    {
        return schedule.businessDate.atStartOfDay().plusMinutes(
                segment.startMinuteOffset);
    }

    private LocalDateTime segmentEnd(ScheduleRef schedule,
            ScheduleSegmentRef segment)
    {
        return schedule.businessDate.atStartOfDay().plusMinutes(
                segment.endMinuteOffset);
    }

    private Schedule ruleSchedule(ScheduleRef source)
    {
        Schedule value = new Schedule();
        value.scheduleId = source.scheduleId;
        value.status = "PUBLISHED";
        value.businessDate = source.businessDate;
        value.startTimeSnapshot = source.startTimeSnapshot;
        value.endTimeSnapshot = source.endTimeSnapshot;
        value.crossDaySnapshot = source.crossDaySnapshot;
        value.checkInOpenMinutesSnapshot =
                source.checkInOpenMinutesSnapshot;
        value.checkInCloseMinutesSnapshot =
                source.checkInCloseMinutesSnapshot;
        value.checkOutOpenMinutesSnapshot =
                source.checkOutOpenMinutesSnapshot;
        value.checkOutCloseMinutesSnapshot =
                source.checkOutCloseMinutesSnapshot;
        return value;
    }

    private Map<Long, String> segmentLabels(
            List<ScheduleSegmentRef> segments)
    {
        List<ScheduleSegmentRef> work = workSegments(segments, null);
        Map<Long, String> labels = new LinkedHashMap<>();
        for (int index = 0; index < work.size(); index++)
            labels.put(work.get(index).scheduleSegmentSnapshotId,
                    workSegmentLabel(index, work.size()));
        return labels;
    }

    private List<ScheduleSegmentRef> workSegments(
            List<ScheduleSegmentRef> segments, Long expectedScheduleId)
    {
        List<ScheduleSegmentRef> work = new ArrayList<>();
        if (segments != null)
            for (ScheduleSegmentRef segment : segments)
                if (segment != null && "WORK".equals(
                        upper(segment.segmentType)))
                {
                    validateSegmentSnapshot(segment,
                            expectedScheduleId == null
                                    ? segment.scheduleId
                                    : expectedScheduleId);
                    work.add(segment);
                }
        if (work.isEmpty())
            throw new ServiceException(
                    "CORRECTION_WORK_SEGMENT_SNAPSHOT_MISSING");
        return work;
    }

    private String workSegmentLabel(int index, int count)
    {
        if (count == 2) return index == 0 ? "上午" : "下午";
        return "第" + (index + 1) + "工作段";
    }

    private void validateSegmentSnapshot(ScheduleSegmentRef segment,
            Long expectedScheduleId)
    {
        if (segment == null
                || segment.scheduleSegmentSnapshotId == null
                || !Objects.equals(segment.scheduleId, expectedScheduleId)
                || !"WORK".equals(upper(segment.segmentType))
                || segment.segmentOrder == null
                || segment.segmentOrder <= 0
                || segment.startMinuteOffset == null
                || segment.endMinuteOffset == null
                || segment.startMinuteOffset < 0
                || segment.endMinuteOffset <= segment.startMinuteOffset
                || segment.endMinuteOffset > 2880)
            throw new ServiceException(
                    "CORRECTION_TARGET_SEGMENT_INVALID");
    }

    private String punchMode(ScheduleRef schedule)
    {
        try
        {
            return rules.normalizePunchMode(schedule == null ? null
                    : schedule.punchModeSnapshot);
        }
        catch (ServiceException error)
        {
            throw new ServiceException("CORRECTION_PUNCH_MODE_INVALID");
        }
    }

    private String slotKey(Long snapshotId, String punchType)
    {
        ScheduleSegmentSnapshot segment = new ScheduleSegmentSnapshot();
        segment.scheduleSegmentSnapshotId = snapshotId;
        return rules.slotKey(segment, punchType);
    }

    private ScheduleRef requireOwnedSchedule(Long scheduleId, Long userId,
            Long selectedShopId)
    {
        ScheduleRef schedule = scheduleId == null ? null
                : mapper.selectScheduleRef(scheduleId);
        return validateOwnedSchedule(schedule, userId, selectedShopId);
    }

    private ScheduleRef requireOwnedScheduleForUpdate(Long scheduleId,
            Long userId, Long selectedShopId)
    {
        ScheduleRef schedule = scheduleId == null ? null
                : mapper.selectScheduleRefForUpdate(scheduleId);
        return validateOwnedSchedule(schedule, userId, selectedShopId);
    }

    private ScheduleRef validateOwnedSchedule(ScheduleRef schedule,
            Long userId, Long selectedShopId)
    {
        if (schedule == null)
            throw new ServiceException("CORRECTION_SCHEDULE_NOT_FOUND");
        if (!Objects.equals(schedule.userId, userId))
            throw new ServiceException("CORRECTION_SCHEDULE_NOT_OWNED");
        requireSameShop(schedule.shopId, selectedShopId);
        if (!Set.of("PUBLISHED", "CHANGED").contains(schedule.status))
            throw new ServiceException("CORRECTION_SCHEDULE_NOT_PUBLISHED");
        return schedule;
    }

    private void apply(CorrectionRequest row, SaveDraft body,
            ScheduleRef schedule, DraftValues values)
    {
        row.scheduleId = schedule.scheduleId;
        row.shopId = schedule.shopId;
        row.businessDate = schedule.businessDate;
        row.correctionType = values.correctionType();
        row.targetPunchType = values.punchType();
        row.targetScheduleSegmentSnapshotId =
                values.scheduleSegmentSnapshotId();
        row.targetPunchSlotKey = values.punchSlotKey();
        row.originalPunchEventId = values.originalEventId();
        row.requestedPunchTime = body.requestedPunchTime;
        row.reason = values.reason();
        row.attachmentRefs = null;
    }

    private CorrectionRequest replay(CorrectionRequest value,
            String fingerprint)
    {
        if (!Objects.equals(value.clientRequestFingerprint, fingerprint))
            throw new ServiceException(
                    "CORRECTION_CLIENT_REQUEST_ID_REUSED");
        enrichCorrectionTarget(value);
        return value;
    }

    private String draftFingerprint(SaveDraft body)
    {
        String slot = body.targetPunchSlotKey == null ? null
                : upper(body.targetPunchSlotKey);
        if (slot != null && slot.isEmpty()) slot = null;
        return AttendanceClientRequestSupport.fingerprint(
                "OA_ATTENDANCE_CORRECTION_DRAFT_V1", body.scheduleId,
                upper(body.correctionType), upper(body.targetPunchType),
                body.targetScheduleSegmentSnapshotId, slot,
                body.originalPunchEventId, body.requestedPunchTime,
                body.reason == null ? null : body.reason.trim());
    }

    private ApprovalStartRequest buildApprovalRequest(CorrectionRequest value)
    {
        ApprovalStartRequest request = new ApprovalStartRequest();
        request.setBusinessCode(
                AttendanceCorrectionApprovalOutboxService.BUSINESS_CODE);
        request.setBusinessId(String.valueOf(value.correctionRequestId));
        request.setBusinessRound(value.businessRound);
        request.setApplicantId(value.userId);
        request.setApplicantName(value.userName);
        LoginUser login = SecurityUtils.getLoginUser();
        SysUser user = login == null ? null : login.getSysUser();
        request.setApplicantDeptId(user == null ? null : user.getDeptId());
        request.setAnchorDeptId(value.shopId);
        request.setAnchorDeptName(shopScopeService.resolveShopDeptName(
                value.shopId));
        request.setBusinessSubtype(value.correctionType);
        request.setIdempotencyKey(
                AttendanceCorrectionApprovalOutboxService.BUSINESS_CODE + ":"
                        + value.correctionRequestId + ":"
                        + value.businessRound);
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("correctionRequestId", value.correctionRequestId);
        variables.put("correctionRequestNo", value.correctionRequestNo);
        variables.put("scheduleId", value.scheduleId);
        variables.put("businessDate", value.businessDate.toString());
        variables.put("correctionType", value.correctionType);
        variables.put("targetPunchType", value.targetPunchType);
        variables.put("targetScheduleSegmentSnapshotId",
                value.targetScheduleSegmentSnapshotId);
        variables.put("targetPunchSlotKey", value.targetPunchSlotKey);
        variables.put("originalPunchEventId", value.originalPunchEventId);
        variables.put("originalPunchTime", value.originalPunchTime == null
                ? null : value.originalPunchTime.toString());
        variables.put("requestedPunchTime",
                value.requestedPunchTime.toString());
        variables.put("targetSegmentLabel",
                value.targetSegmentLabelSnapshot);
        variables.put("reason", value.reason);
        variables.put("shopId", value.shopId);
        request.setVariables(variables);
        Map<String, String> route = new LinkedHashMap<>();
        route.put("businessId", String.valueOf(value.correctionRequestId));
        route.put("correctionRequestId",
                String.valueOf(value.correctionRequestId));
        route.put("todoType", "OA_ATTENDANCE_CORRECTION_APPROVAL");
        route.put("desktopPath",
                "/oa/attendance-v2?tab=correction");
        route.put("mobilePath", "/mobile/attendance?tab=correction");
        request.setRouteSnapshot(route);
        return request;
    }

    private CorrectionRequest requireOwnedForUpdate(Long id,
            Long selectedShopId)
    {
        CorrectionRequest value = id == null ? null
                : mapper.selectCorrectionRequestByIdForUpdate(id);
        if (value == null)
            throw new ServiceException("CORRECTION_REQUEST_NOT_FOUND");
        if (!Objects.equals(value.userId, SecurityUtils.getUserId()))
            throw new ServiceException("CORRECTION_REQUEST_NOT_OWNED");
        requireSameShop(value.shopId, selectedShopId);
        return value;
    }

    private CorrectionRequest requireOwned(Long id, Long selectedShopId)
    {
        CorrectionRequest value = id == null ? null
                : mapper.selectCorrectionRequestById(id);
        if (value == null)
            throw new ServiceException("CORRECTION_REQUEST_NOT_FOUND");
        if (!Objects.equals(value.userId, SecurityUtils.getUserId()))
            throw new ServiceException("CORRECTION_REQUEST_NOT_OWNED");
        requireSameShop(value.shopId, selectedShopId);
        return value;
    }

    private void requireReadable(CorrectionRequest value,
            Long selectedShopId)
    {
        if (value == null)
            throw new ServiceException("CORRECTION_REQUEST_NOT_FOUND");
        if (Objects.equals(value.userId, SecurityUtils.getUserId()))
        {
            requireSameShop(value.shopId, selectedShopId);
            return;
        }
        if (!hasPermission("oa:attendance:correction:list")
                && !hasPermission("oa:attendance:correction:approve"))
            throw new ServiceException("CORRECTION_REQUEST_FORBIDDEN");
        requireSameShop(value.shopId, selectedShopId);
    }

    private String requireActiveEmployee(Long userId, Long shopId)
    {
        String name = mapper.selectActiveEmployeeNameInShop(userId, shopId);
        if (name == null || name.isBlank())
            throw new ServiceException("EMPLOYEE_NOT_ACTIVE_IN_SHOP");
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
            throw new ServiceException("CORRECTION_QUERY_RANGE_INVALID");
    }

    private String normalizeStatus(String status)
    {
        if (status == null || status.isBlank()) return null;
        String value = upper(status);
        if (!Set.of("DRAFT", "SUBMITTING", "PENDING", "APPROVED",
                "REJECTED", "RETURNED", "CANCELLED").contains(value))
            throw new ServiceException("CORRECTION_STATUS_INVALID");
        return value;
    }

    private void requireVersion(Long expected, Long actual)
    {
        if (expected == null || !Objects.equals(expected, actual))
            throw new ServiceException("CORRECTION_VERSION_CONFLICT");
    }

    private boolean hasPermission(String permission)
    {
        LoginUser login = SecurityUtils.getLoginUser();
        return SecurityUtils.isAdmin() || login != null
                && login.getPermissions() != null
                && (login.getPermissions().contains(permission)
                        || login.getPermissions().contains("*:*:*"));
    }

    private int requiredNonNegative(Integer value)
    {
        if (value == null || value < 0 || value > 1440)
            throw new ServiceException("CORRECTION_SCHEDULE_WINDOW_INVALID");
        return value;
    }

    private String operator()
    {
        String value = SecurityUtils.getUsername();
        return value == null || value.isBlank()
                ? String.valueOf(SecurityUtils.getUserId()) : value.trim();
    }

    private String nextNo()
    {
        return "AC" + LocalDateTime.now(clock).withNano(0)
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().replace("-", "")
                        .substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private String upper(String value)
    { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT); }

    private void requireEnabled()
    { featureGate.requireEnabled(BusinessFeatureGate.ATTENDANCE_V2); }

    private record DraftValues(String correctionType, String punchType,
            String reason, Long originalEventId,
            Long scheduleSegmentSnapshotId, String punchSlotKey) { }

    private record PunchTarget(Long scheduleSegmentSnapshotId,
            String punchSlotKey,
            ScheduleSegmentRef segment) { }
}
