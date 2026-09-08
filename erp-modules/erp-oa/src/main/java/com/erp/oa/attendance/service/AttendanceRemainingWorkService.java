package com.erp.oa.attendance.service;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.oa.attendance.domain.AttendanceModels.RemainingWorkAttachment;
import com.erp.oa.attendance.domain.AttendanceModels.RemainingWorkConfirmation;
import com.erp.oa.attendance.domain.AttendanceModels.Schedule;
import com.erp.oa.attendance.domain.AttendanceModels.ScheduleSegmentSnapshot;
import com.erp.oa.attendance.domain.AttendanceSettlementModels.LeaveSegmentSource;
import com.erp.oa.attendance.dto.AttendanceRemainingWorkRequests.Confirm;
import com.erp.oa.attendance.dto.AttendanceViews.RemainingWorkIntervalView;
import com.erp.oa.attendance.mapper.AttendanceV2Mapper;
import com.erp.oa.attendance.service.AttendanceRemainingWorkAttachmentStorage.StoredAttachment;
import com.erp.oa.attendance.support.AttendanceDaySettlementCalculator;
import com.erp.oa.attendance.support.AttendanceLeaveCoveragePolicy;
import com.erp.oa.attendance.support.AttendanceLeaveCoveragePolicy.Coverage;
import com.erp.oa.attendance.support.AttendanceLeaveCoveragePolicy.TimeInterval;
import com.erp.oa.attendance.support.AttendanceRuleEngine;
import com.erp.oa.service.BusinessFeatureGate;

/** Manager-only, append-only closure for partial-leave work islands. */
@Service
public class AttendanceRemainingWorkService
{
    private static final Set<String> DECISIONS = Set.of(
            "ATTENDED", "ABSENT", "RETURN_FOR_EVIDENCE");
    private final AttendanceV2Mapper mapper;
    private final AttendanceDaySettlementCalculator calculator;
    private final AttendanceRuleEngine rules;
    private final AttendanceRemainingWorkAttachmentStorage storage;
    private final ShopScopeService shopScopeService;
    private final BusinessFeatureGate featureGate;

    public AttendanceRemainingWorkService(AttendanceV2Mapper mapper,
            AttendanceDaySettlementCalculator calculator,
            AttendanceRuleEngine rules,
            AttendanceRemainingWorkAttachmentStorage storage,
            ShopScopeService shopScopeService, BusinessFeatureGate featureGate)
    {
        this.mapper = mapper;
        this.calculator = calculator;
        this.rules = rules;
        this.storage = storage;
        this.shopScopeService = shopScopeService;
        this.featureGate = featureGate;
    }

    public List<RemainingWorkIntervalView> intervals(Long scheduleId,
            Long selectedShopId)
    {
        requireEnabled();
        Schedule schedule = requireScopedSchedule(scheduleId, selectedShopId,
                false);
        return intervalViews(schedule, false);
    }

    public List<RemainingWorkConfirmation> history(Long scheduleId,
            Long selectedShopId)
    {
        requireEnabled();
        Schedule schedule = requireScopedSchedule(scheduleId, selectedShopId,
                false);
        Set<IntervalKey> current = new java.util.LinkedHashSet<>();
        for (RequiredInterval value : requiredIntervals(schedule, false))
            current.add(new IntervalKey(value.interval().start(),
                    value.interval().end()));
        List<RemainingWorkConfirmation> values = safe(
                mapper.selectRemainingWorkConfirmations(scheduleId));
        for (RemainingWorkConfirmation value : values)
        {
            value.currentInterval = current.contains(new IntervalKey(
                    value.remainingStart, value.remainingEnd));
            value.attachments = safe(mapper.selectRemainingWorkAttachments(
                    value.confirmationId));
        }
        return values;
    }

    @Transactional
    public RemainingWorkConfirmation confirm(Confirm command,
            Long selectedShopId)
    {
        requireEnabled();
        if (command == null || command.scheduleId == null
                || command.remainingStart == null
                || command.remainingEnd == null
                || !command.remainingEnd.isAfter(command.remainingStart))
            throw new ServiceException("REMAINING_WORK_COMMAND_INVALID");
        Schedule schedule = requireScopedSchedule(command.scheduleId,
                selectedShopId, true);
        RequiredInterval target = requiredIntervals(schedule, true).stream()
                .filter(value -> value.interval().start().equals(
                        command.remainingStart)
                        && value.interval().end().equals(command.remainingEnd))
                .findFirst().orElseThrow(() -> new ServiceException(
                        "REMAINING_WORK_INTERVAL_STALE"));
        LocalDateTime now = databaseNow();
        if (now.isBefore(target.interval().end()))
            throw new ServiceException("REMAINING_WORK_INTERVAL_NOT_ENDED");
        String decision = normalizeDecision(command.decision);
        validateDecisionTimes(command, decision, target.interval());
        String reason = command.reason == null ? "" : command.reason.trim();
        if (reason.length() < 2 || reason.length() > 500)
            throw new ServiceException("REMAINING_WORK_REASON_INVALID");

        RemainingWorkConfirmation previous = mapper
                .selectLatestRemainingWorkConfirmation(schedule.scheduleId,
                        target.interval().start(), target.interval().end(),
                        true);
        RemainingWorkConfirmation value = new RemainingWorkConfirmation();
        value.confirmationNo = "RWC" + now.format(
                DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().replace("-", "")
                        .substring(0, 8).toUpperCase(Locale.ROOT);
        value.scheduleId = schedule.scheduleId;
        value.userId = schedule.userId;
        value.userName = schedule.userName;
        value.shopId = schedule.shopId;
        value.businessDate = schedule.businessDate;
        value.remainingStart = target.interval().start();
        value.remainingEnd = target.interval().end();
        value.decision = decision;
        value.actualArrivalTime = command.actualArrivalTime;
        value.actualDepartureTime = command.actualDepartureTime;
        value.reason = reason;
        value.supersedesConfirmationId = previous == null ? null
                : previous.confirmationId;
        value.decidedBy = SecurityUtils.getUserId();
        value.decidedByName = SecurityUtils.getUsername();
        value.decidedAt = now;
        value.setCreateBy(SecurityUtils.getUsername());
        if (mapper.insertRemainingWorkConfirmation(value) != 1)
            throw new ServiceException("REMAINING_WORK_CONFIRMATION_FAILED");
        mapper.invalidateUnsettledDayResultForRemainingWork(
                schedule.scheduleId, SecurityUtils.getUsername());
        return mapper.selectRemainingWorkConfirmationById(
                value.confirmationId);
    }

    @Transactional
    public RemainingWorkAttachment upload(Long confirmationId,
            MultipartFile file, Long selectedShopId)
    {
        requireEnabled();
        RemainingWorkConfirmation confirmation = mapper
                .selectRemainingWorkConfirmationById(confirmationId);
        if (confirmation == null)
            throw new ServiceException("REMAINING_WORK_CONFIRMATION_NOT_FOUND");
        requireScopedSchedule(confirmation.scheduleId, selectedShopId, false);
        StoredAttachment stored = storage.store(confirmationId, file);
        registerRollbackCleanup(stored.relativePath());
        RemainingWorkAttachment value = new RemainingWorkAttachment();
        value.confirmationId = confirmationId;
        value.originalName = stored.originalName();
        value.storagePath = stored.relativePath();
        value.contentType = stored.contentType();
        value.fileExtension = stored.extension();
        value.fileSize = stored.size();
        value.sha256 = stored.sha256();
        value.uploadedBy = SecurityUtils.getUserId();
        if (mapper.insertRemainingWorkAttachment(value) != 1)
            throw new ServiceException("REMAINING_WORK_ATTACHMENT_SAVE_FAILED");
        return mapper.selectRemainingWorkAttachmentById(value.attachmentId);
    }

    public AttachmentContent attachmentContent(Long confirmationId,
            Long attachmentId, Long selectedShopId)
    {
        requireEnabled();
        RemainingWorkConfirmation confirmation = mapper
                .selectRemainingWorkConfirmationById(confirmationId);
        RemainingWorkAttachment attachment = mapper
                .selectRemainingWorkAttachmentById(attachmentId);
        if (confirmation == null || attachment == null
                || !Objects.equals(confirmationId, attachment.confirmationId))
            throw new ServiceException("REMAINING_WORK_ATTACHMENT_NOT_FOUND");
        requireScopedSchedule(confirmation.scheduleId, selectedShopId, false);
        Path path = storage.resolve(attachment.storagePath);
        return new AttachmentContent(path, attachment.originalName,
                attachment.contentType, attachment.fileSize == null
                        ? path.toFile().length() : attachment.fileSize);
    }

    private List<RemainingWorkIntervalView> intervalViews(Schedule schedule,
            boolean lockRows)
    {
        List<RemainingWorkIntervalView> values = new ArrayList<>();
        for (RequiredInterval required : requiredIntervals(schedule, lockRows))
        {
            RemainingWorkConfirmation latest = mapper
                    .selectLatestRemainingWorkConfirmation(schedule.scheduleId,
                            required.interval().start(),
                            required.interval().end(), lockRows);
            if (latest != null)
            {
                latest.currentInterval = true;
                latest.attachments = safe(mapper
                        .selectRemainingWorkAttachments(latest.confirmationId));
            }
            RemainingWorkIntervalView view = new RemainingWorkIntervalView();
            view.scheduleId = schedule.scheduleId;
            view.scheduleSegmentSnapshotId = required.segmentId();
            view.segmentOrder = required.segmentOrder();
            view.segmentLabel = required.label();
            view.remainingStart = required.interval().start();
            view.remainingEnd = required.interval().end();
            view.remainingMinutes = required.interval().minutes();
            view.latestConfirmation = latest;
            view.state = latest == null ? "PENDING"
                    : "ATTENDED".equals(latest.decision)
                            || "ABSENT".equals(latest.decision)
                                    ? "CONFIRMED"
                                    : "NEEDS_EVIDENCE";
            values.add(view);
        }
        return values;
    }

    private List<RequiredInterval> requiredIntervals(Schedule schedule,
            boolean lockRows)
    {
        List<ScheduleSegmentSnapshot> segments = safe(mapper
                .selectScheduleSegmentSnapshots(schedule.scheduleId,
                        lockRows)).stream().filter(Objects::nonNull)
                .filter(value -> "WORK".equals(value.segmentType)
                        && Boolean.TRUE.equals(value.paid))
                .sorted(Comparator.comparing(value -> value.segmentOrder,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        if (segments.isEmpty())
            throw new ServiceException("SCHEDULE_WORK_SEGMENT_REQUIRED");
        AttendanceDaySettlementCalculator.Bounds bounds = calculator
                .bounds(schedule);
        List<LeaveSegmentSource> leaves = safe(mapper
                .selectApprovedLeaveSegments(schedule.userId, schedule.shopId,
                        bounds.start(), bounds.end(), lockRows));
        List<RequiredInterval> values = new ArrayList<>();
        if (AttendanceRuleEngine.PER_WORK_SEGMENT.equals(
                rules.normalizePunchMode(schedule.punchModeSnapshot)))
        {
            for (int index = 0; index < segments.size(); index++)
            {
                ScheduleSegmentSnapshot segment = segments.get(index);
                Coverage coverage = AttendanceLeaveCoveragePolicy.analyze(
                        rules.segmentStart(schedule, segment),
                        rules.segmentEnd(schedule, segment), leaves);
                if (!coverage.requiresRemainingWorkConfirmation()) continue;
                for (TimeInterval interval : coverage.remainingIntervals())
                    values.add(new RequiredInterval(
                            segment.scheduleSegmentSnapshotId,
                            segment.segmentOrder,
                            label(index, segments.size()), interval));
            }
        }
        else
        {
            LocalDateTime first = rules.segmentStart(schedule,
                    segments.get(0));
            LocalDateTime last = rules.segmentEnd(schedule,
                    segments.get(segments.size() - 1));
            Coverage outer = AttendanceLeaveCoveragePolicy.analyze(first,
                    last, leaves);
            if (outer.startCovered() && outer.endCovered())
            {
                for (int index = 0; index < segments.size(); index++)
                {
                    ScheduleSegmentSnapshot segment = segments.get(index);
                    Coverage coverage = AttendanceLeaveCoveragePolicy.analyze(
                            rules.segmentStart(schedule, segment),
                            rules.segmentEnd(schedule, segment), leaves);
                    for (TimeInterval interval : coverage.remainingIntervals())
                        values.add(new RequiredInterval(
                                segment.scheduleSegmentSnapshotId,
                                segment.segmentOrder,
                                label(index, segments.size()), interval));
                }
            }
        }
        return List.copyOf(values);
    }

    private Schedule requireScopedSchedule(Long scheduleId,
            Long selectedShopId, boolean lockRows)
    {
        if (scheduleId == null || scheduleId <= 0)
            throw new ServiceException("REMAINING_WORK_SCHEDULE_INVALID");
        Long shopId = shopScopeService.resolveRequiredShopDept(selectedShopId);
        Schedule schedule = lockRows
                ? mapper.selectScheduleByIdForUpdate(scheduleId)
                : mapper.selectScheduleById(scheduleId);
        if (schedule == null || !"PUBLISHED".equals(schedule.status)
                || !Objects.equals(shopId, schedule.shopId))
            throw new ServiceException("REMAINING_WORK_SCHEDULE_NOT_FOUND");
        return schedule;
    }

    private void validateDecisionTimes(Confirm command, String decision,
            TimeInterval interval)
    {
        if ("ATTENDED".equals(decision))
        {
            if (command.actualArrivalTime == null
                    || command.actualDepartureTime == null
                    || !command.actualDepartureTime.isAfter(
                            command.actualArrivalTime)
                    || command.actualArrivalTime.isBefore(interval.start())
                    || command.actualDepartureTime.isAfter(interval.end()))
                throw new ServiceException(
                        "REMAINING_WORK_ATTENDANCE_TIME_INVALID");
        }
        else if (command.actualArrivalTime != null
                || command.actualDepartureTime != null)
            throw new ServiceException(
                    "REMAINING_WORK_DECISION_TIME_NOT_APPLICABLE");
    }

    private String normalizeDecision(String value)
    {
        String decision = value == null ? ""
                : value.trim().toUpperCase(Locale.ROOT);
        if (!DECISIONS.contains(decision))
            throw new ServiceException("REMAINING_WORK_DECISION_INVALID");
        return decision;
    }

    private LocalDateTime databaseNow()
    {
        LocalDateTime value = mapper.selectDatabaseNow();
        if (value == null)
            throw new ServiceException("ATTENDANCE_SERVER_TIME_UNAVAILABLE");
        return value.withNano(0);
    }

    private String label(int index, int count)
    {
        if (count == 1) return "工作段";
        if (count == 2) return index == 0 ? "上午工作段" : "下午工作段";
        return "第" + (index + 1) + "工作段";
    }

    private void registerRollbackCleanup(String relativePath)
    {
        if (!TransactionSynchronizationManager.isSynchronizationActive())
            throw new ServiceException("REMAINING_WORK_TRANSACTION_REQUIRED");
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization()
                {
                    @Override
                    public void afterCompletion(int status)
                    {
                        if (status != STATUS_COMMITTED)
                            storage.deleteQuietly(relativePath);
                    }
                });
    }

    private <T> List<T> safe(List<T> values)
    { return values == null ? List.of() : values; }
    private void requireEnabled()
    { featureGate.requireEnabled(BusinessFeatureGate.ATTENDANCE_V2); }

    private record RequiredInterval(Long segmentId, Integer segmentOrder,
            String label, TimeInterval interval) { }
    private record IntervalKey(LocalDateTime start, LocalDateTime end) { }
    public record AttachmentContent(Path path, String fileName,
            String contentType, long size) { }
}
