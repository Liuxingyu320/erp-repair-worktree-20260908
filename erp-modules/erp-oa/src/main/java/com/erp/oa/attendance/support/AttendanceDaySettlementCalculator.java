package com.erp.oa.attendance.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import com.erp.oa.attendance.domain.AttendanceModels.DayResult;
import com.erp.oa.attendance.domain.AttendanceModels.PunchEvent;
import com.erp.oa.attendance.domain.AttendanceModels.Schedule;
import com.erp.oa.attendance.domain.AttendanceModels.ScheduleSegmentSnapshot;
import com.erp.oa.attendance.domain.AttendanceSettlementModels.CorrectionSource;
import com.erp.oa.attendance.domain.AttendanceSettlementModels.LeaveRequestState;
import com.erp.oa.attendance.domain.AttendanceSettlementModels.LeaveSegmentSource;
import com.erp.oa.attendance.domain.AttendanceSettlementModels.RemainingWorkConfirmationSource;
import com.erp.oa.attendance.support.AttendanceLeaveCoveragePolicy.Coverage;
import com.erp.oa.attendance.support.AttendanceLeaveCoveragePolicy.TimeInterval;
import com.erp.oa.attendance.support.AttendanceRuleEngine.PunchWindow;

/** Pure, deterministic calculation for an auditable final day result. */
@Component
public class AttendanceDaySettlementCalculator
{
    public static final int CALCULATION_VERSION = 4;
    private static final Set<String> SUPPORTED_CORRECTION_TYPES = Set.of(
            "MISSING_PUNCH", "WRONG_TIME", "WRONG_TYPE");
    private final AttendanceRuleEngine rules;

    public AttendanceDaySettlementCalculator(AttendanceRuleEngine rules)
    {
        this.rules = rules;
    }

    public Evaluation evaluate(Schedule schedule, PunchEvent acceptedIn,
            PunchEvent acceptedOut, List<CorrectionSource> corrections,
            List<LeaveRequestState> blockingLeaves,
            List<LeaveSegmentSource> approvedLeaves,
            List<ScheduleSegmentSnapshot> segmentSnapshots,
            LocalDateTime now)
    {
        List<CorrectionSource> values = corrections == null
                ? List.of() : corrections;
        if (values.stream().anyMatch(this::approvedWrongType))
        {
            List<PunchEvent> punches = new ArrayList<>();
            if (acceptedIn != null) punches.add(acceptedIn);
            if (acceptedOut != null && (acceptedIn == null
                    || !Objects.equals(acceptedIn.punchEventId,
                            acceptedOut.punchEventId)))
                punches.add(acceptedOut);
            return evaluate(schedule, punches, values, blockingLeaves,
                    approvedLeaves, segmentSnapshots, List.of(), now);
        }
        Set<String> issues = new LinkedHashSet<>();
        values = supportedCorrectionSources(values, issues);
        return evaluateBoundary(schedule, acceptedIn, acceptedOut,
                values, blockingLeaves, approvedLeaves,
                segmentSnapshots, List.of(), now, issues);
    }

    private Evaluation evaluateBoundary(Schedule schedule,
            PunchEvent acceptedIn, PunchEvent acceptedOut,
            List<CorrectionSource> corrections,
            List<LeaveRequestState> blockingLeaves,
            List<LeaveSegmentSource> approvedLeaves,
            List<ScheduleSegmentSnapshot> segmentSnapshots,
            List<RemainingWorkConfirmationSource> confirmations,
            LocalDateTime now, Set<String> initialIssues)
    {
        Set<String> issues = new LinkedHashSet<>(initialIssues);
        Bounds bounds = bounds(schedule);
        if (now == null || !now.isAfter(
                rules.window(schedule, "OUT").closesAt()))
            issues.add("SHIFT_NOT_ENDED");

        List<CorrectionSource> values = corrections == null
                ? List.of() : corrections;
        if (values.stream().anyMatch(value -> value != null
                && Set.of("SUBMITTING", "PENDING").contains(value.status)))
            issues.add("PENDING_CORRECTION");
        if (blockingLeaves != null && !blockingLeaves.isEmpty())
            issues.add("PENDING_LEAVE");
        List<WorkInterval> scheduledWork = scheduledWorkIntervals(schedule,
                segmentSnapshots, bounds, issues);
        LeaveTotals leave = leaveTotals(approvedLeaves, scheduledWork,
                nonNegative(schedule.standardMinutesSnapshot), issues);
        int scheduled = nonNegative(schedule.standardMinutesSnapshot);
        int leaveMinutes = Math.min(scheduled,
                leave.paidMinutes() + leave.unpaidMinutes());
        boolean fullyCoveredByLeave = scheduled > 0
                && !scheduledWork.isEmpty() && scheduledWork.stream()
                        .allMatch(work -> AttendanceLeaveCoveragePolicy
                                .analyze(work.start(), work.end(),
                                        approvedLeaves).fullyCovered());
        Coverage outerCoverage = scheduledWork.isEmpty()
                ? null : AttendanceLeaveCoveragePolicy.analyze(
                        scheduledWork.get(0).start(),
                        scheduledWork.get(scheduledWork.size() - 1).end(),
                        approvedLeaves);
        boolean inRequired = !fullyCoveredByLeave
                && (outerCoverage == null || !outerCoverage.startCovered());
        boolean outRequired = !fullyCoveredByLeave
                && (outerCoverage == null || !outerCoverage.endCovered());

        CorrectionSource correctionIn = approvedCorrection(schedule, values,
                "IN", issues);
        CorrectionSource correctionOut = approvedCorrection(schedule, values,
                "OUT", issues);
        PunchSource in = source("IN", acceptedIn, correctionIn, schedule,
                issues);
        PunchSource out = source("OUT", acceptedOut, correctionOut, schedule,
                issues);
        if (inRequired && in == null) issues.add("MISSING_IN");
        if (outRequired && out == null) issues.add("MISSING_OUT");
        if (in != null && out != null && !out.time().isAfter(in.time()))
            issues.add("PUNCH_ORDER_INVALID");

        DayResult result = base(schedule);
        result.firstInEventId = in == null ? null : in.eventId();
        result.lastOutEventId = out == null ? null : out.eventId();
        result.firstInCorrectionRequestId = in == null ? null
                : in.correctionRequestId();
        result.lastOutCorrectionRequestId = out == null ? null
                : out.correctionRequestId();
        result.paidLeaveMinutes = leave.paidMinutes();
        result.unpaidLeaveMinutes = leave.unpaidMinutes();

        scheduled = nonNegative(result.scheduledMinutes);
        if (inRequired && in != null)
        {
            LocalDateTime expected = bounds.start().plusMinutes(
                    nonNegative(schedule.graceInMinutesSnapshot));
            result.lateMinutes = uncoveredScheduledMinutes(scheduledWork,
                    leave.intervals(), expected, in.time());
        }
        if (outRequired && out != null)
        {
            LocalDateTime expected = bounds.end().minusMinutes(
                    nonNegative(schedule.graceOutMinutesSnapshot));
            result.earlyLeaveMinutes = uncoveredScheduledMinutes(
                    scheduledWork, leave.intervals(), out.time(), expected);
        }
        WorkTotals worked = WorkTotals.NONE;
        List<TimeInterval> remaining = remainingIntervals(scheduledWork,
                approvedLeaves);
        if (!fullyCoveredByLeave && outerCoverage != null
                && outerCoverage.startCovered()
                && outerCoverage.endCovered())
        {
            int confirmed = 0;
            for (TimeInterval interval : remaining)
                confirmed += confirmedWorkedMinutes(schedule, interval,
                        confirmations, issues);
            worked = new WorkTotals(confirmed, confirmed);
            result.workedMinutes = confirmed;
        }
        else
        {
            LocalDateTime actualStart = inRequired
                    ? in == null ? null : in.time()
                    : remaining.isEmpty() ? null : remaining.get(0).start();
            LocalDateTime actualEnd = outRequired
                    ? out == null ? null : out.time()
                    : remaining.isEmpty() ? null
                            : remaining.get(remaining.size() - 1).end();
            if (actualStart != null && actualEnd != null
                    && actualEnd.isAfter(actualStart))
            {
                worked = workedMinutes(actualStart, actualEnd, bounds,
                        scheduledWork, leave.intervals());
                result.workedMinutes = worked.totalMinutes();
            }
        }
        result.absenceMinutes = Math.max(0, scheduled
                - worked.scheduledMinutes() - leaveMinutes);

        if (!issues.isEmpty())
        {
            result.resultStatus = issues.contains("MISSING_IN")
                    && issues.contains("MISSING_OUT") ? "ABSENT"
                    : issues.contains("MISSING_IN") ? "MISSED_IN"
                    : issues.contains("MISSING_OUT") ? "MISSED_OUT"
                    : "EXCEPTION";
            result.exceptionCodes = String.join(",", issues);
        }
        else if (leaveMinutes >= scheduled && scheduled > 0)
            result.resultStatus = "LEAVE_FULL";
        else if (leaveMinutes > 0)
            result.resultStatus = "LEAVE_PARTIAL";
        else if (result.lateMinutes > 0)
            result.resultStatus = result.earlyLeaveMinutes > 0
                    ? "LATE_EARLY" : "LATE";
        else if (result.earlyLeaveMinutes > 0)
            result.resultStatus = "EARLY";
        else
            result.resultStatus = "NORMAL";

        result.calculationVersion = CALCULATION_VERSION;
        result.calculatedAt = now;
        result.settledAt = null;
        return new Evaluation(result, List.copyOf(issues));
    }

    /**
     * Calculates from the complete immutable punch stream.  Boundary shifts
     * intentionally delegate to the original first-IN/last-OUT algorithm;
     * segmented shifts require one independently paired IN/OUT for each WORK
     * snapshot.
     */
    public Evaluation evaluate(Schedule schedule,
            List<PunchEvent> acceptedPunches,
            List<CorrectionSource> corrections,
            List<LeaveRequestState> blockingLeaves,
            List<LeaveSegmentSource> approvedLeaves,
            List<ScheduleSegmentSnapshot> segmentSnapshots,
            LocalDateTime now)
    {
        return evaluate(schedule, acceptedPunches, corrections,
                blockingLeaves, approvedLeaves, segmentSnapshots, List.of(),
                now);
    }

    public Evaluation evaluate(Schedule schedule,
            List<PunchEvent> acceptedPunches,
            List<CorrectionSource> corrections,
            List<LeaveRequestState> blockingLeaves,
            List<LeaveSegmentSource> approvedLeaves,
            List<ScheduleSegmentSnapshot> segmentSnapshots,
            List<RemainingWorkConfirmationSource> confirmations,
            LocalDateTime now)
    {
        List<PunchEvent> punches = acceptedPunches == null
                ? List.of() : acceptedPunches;
        List<CorrectionSource> correctionValues = corrections == null
                ? List.of() : corrections;
        Set<String> issues = new LinkedHashSet<>();
        correctionValues = supportedCorrectionSources(correctionValues,
                issues);
        WrongTypeResolution resolved = resolveWrongTypeReplacements(schedule,
                punches, correctionValues, segmentSnapshots, issues);
        if (!"PER_WORK_SEGMENT".equals(schedule.punchModeSnapshot))
            return evaluateBoundary(schedule,
                    boundaryPunch(resolved.punches(), "IN", true),
                    boundaryPunch(resolved.punches(), "OUT", false),
                    resolved.corrections(), blockingLeaves, approvedLeaves,
                    segmentSnapshots, confirmations, now, issues);
        return evaluatePerWorkSegment(schedule, resolved.punches(),
                resolved.corrections(), blockingLeaves, approvedLeaves,
                segmentSnapshots, confirmations, now, issues);
    }

    private List<CorrectionSource> supportedCorrectionSources(
            List<CorrectionSource> corrections, Set<String> issues)
    {
        List<CorrectionSource> supported = new ArrayList<>();
        for (CorrectionSource value : corrections)
        {
            if (value != null && "APPROVED".equals(value.status)
                    && !SUPPORTED_CORRECTION_TYPES.contains(
                            value.correctionType))
            {
                issues.add("INVALID_APPROVED_CORRECTION_TYPE");
                continue;
            }
            supported.add(value);
        }
        return supported;
    }

    private Evaluation evaluatePerWorkSegment(Schedule schedule,
            List<PunchEvent> acceptedPunches,
            List<CorrectionSource> corrections,
            List<LeaveRequestState> blockingLeaves,
            List<LeaveSegmentSource> approvedLeaves,
            List<ScheduleSegmentSnapshot> segmentSnapshots,
            List<RemainingWorkConfirmationSource> confirmations,
            LocalDateTime now, Set<String> initialIssues)
    {
        Set<String> issues = new LinkedHashSet<>(initialIssues);
        Bounds bounds = bounds(schedule);
        if (now == null || !now.isAfter(
                rules.window(schedule, "OUT").closesAt()))
            issues.add("SHIFT_NOT_ENDED");

        List<CorrectionSource> correctionValues = corrections == null
                ? List.of() : corrections;
        if (correctionValues.stream().anyMatch(value -> value != null
                && Set.of("SUBMITTING", "PENDING").contains(value.status)))
            issues.add("PENDING_CORRECTION");
        if (blockingLeaves != null && !blockingLeaves.isEmpty())
            issues.add("PENDING_LEAVE");

        List<WorkInterval> scheduledWork = scheduledWorkIntervals(schedule,
                segmentSnapshots, bounds, issues);
        if (scheduledWork.stream().anyMatch(
                work -> work.scheduleSegmentSnapshotId() == null))
            issues.add("SCHEDULE_SEGMENT_SNAPSHOT_INVALID");
        validateSegmentPunches(schedule, acceptedPunches, scheduledWork,
                issues);
        validateSegmentCorrections(schedule, correctionValues, scheduledWork,
                issues);

        int scheduled = nonNegative(schedule.standardMinutesSnapshot);
        LeaveTotals leave = leaveTotals(approvedLeaves, scheduledWork,
                scheduled, issues);
        int leaveMinutes = Math.min(scheduled,
                leave.paidMinutes() + leave.unpaidMinutes());

        DayResult result = base(schedule);
        result.paidLeaveMinutes = leave.paidMinutes();
        result.unpaidLeaveMinutes = leave.unpaidMinutes();
        PunchSource firstIn = null;
        PunchSource lastOut = null;
        int workedScheduledMinutes = 0;

        for (WorkInterval work : scheduledWork)
        {
            Coverage coverage = AttendanceLeaveCoveragePolicy.analyze(
                    work.start(), work.end(), approvedLeaves);
            PunchSource in = segmentSource(schedule, acceptedPunches,
                    correctionValues, scheduledWork, work, "IN", issues);
            PunchSource out = segmentSource(schedule, acceptedPunches,
                    correctionValues, scheduledWork, work, "OUT", issues);
            boolean inRequired = !coverage.fullyCovered()
                    && !coverage.startCovered();
            boolean outRequired = !coverage.fullyCovered()
                    && !coverage.endCovered();
            if (inRequired && in == null) issues.add("MISSING_IN");
            if (outRequired && out == null) issues.add("MISSING_OUT");
            if (in != null && (firstIn == null
                    || in.time().isBefore(firstIn.time()))) firstIn = in;
            if (out != null && (lastOut == null
                    || out.time().isAfter(lastOut.time()))) lastOut = out;
            if (in != null && out != null
                    && !out.time().isAfter(in.time()))
            {
                issues.add("PUNCH_ORDER_INVALID");
                continue;
            }

            if (inRequired && in != null)
            {
                LocalDateTime expected = work.start().plusMinutes(
                        nonNegative(schedule.graceInMinutesSnapshot));
                result.lateMinutes += uncoveredScheduledMinutes(
                        List.of(work), leave.intervals(), expected, in.time());
            }
            if (outRequired && out != null)
            {
                LocalDateTime expected = work.end().minusMinutes(
                        nonNegative(schedule.graceOutMinutesSnapshot));
                result.earlyLeaveMinutes += uncoveredScheduledMinutes(
                        List.of(work), leave.intervals(), out.time(), expected);
            }
            if (coverage.fullyCovered()) continue;
            if (coverage.requiresRemainingWorkConfirmation())
            {
                for (TimeInterval interval : coverage.remainingIntervals())
                    workedScheduledMinutes += confirmedWorkedMinutes(schedule,
                            interval, confirmations, issues);
                continue;
            }
            LocalDateTime actualStart = inRequired
                    ? in == null ? null : in.time()
                    : coverage.remainingIntervals().get(0).start();
            LocalDateTime actualEnd = outRequired
                    ? out == null ? null : out.time()
                    : coverage.remainingIntervals().get(
                            coverage.remainingIntervals().size() - 1).end();
            if (actualStart != null && actualEnd != null
                    && actualEnd.isAfter(actualStart))
                workedScheduledMinutes += workedRemainingMinutes(actualStart,
                        actualEnd, coverage.remainingIntervals());
        }

        result.firstInEventId = firstIn == null ? null : firstIn.eventId();
        result.lastOutEventId = lastOut == null ? null : lastOut.eventId();
        result.firstInCorrectionRequestId = firstIn == null ? null
                : firstIn.correctionRequestId();
        result.lastOutCorrectionRequestId = lastOut == null ? null
                : lastOut.correctionRequestId();
        result.workedMinutes = workedScheduledMinutes;
        result.absenceMinutes = Math.max(0, scheduled
                - workedScheduledMinutes - leaveMinutes);

        if (!issues.isEmpty())
        {
            result.resultStatus = issues.contains("MISSING_IN")
                    && issues.contains("MISSING_OUT") ? "ABSENT"
                    : issues.contains("MISSING_IN") ? "MISSED_IN"
                    : issues.contains("MISSING_OUT") ? "MISSED_OUT"
                    : "EXCEPTION";
            result.exceptionCodes = String.join(",", issues);
        }
        else if (leaveMinutes >= scheduled && scheduled > 0)
            result.resultStatus = "LEAVE_FULL";
        else if (leaveMinutes > 0)
            result.resultStatus = "LEAVE_PARTIAL";
        else if (result.lateMinutes > 0)
            result.resultStatus = result.earlyLeaveMinutes > 0
                    ? "LATE_EARLY" : "LATE";
        else if (result.earlyLeaveMinutes > 0)
            result.resultStatus = "EARLY";
        else
            result.resultStatus = "NORMAL";

        result.calculationVersion = CALCULATION_VERSION;
        result.calculatedAt = now;
        result.settledAt = null;
        return new Evaluation(result, List.copyOf(issues));
    }

    /**
     * An approved WRONG_TYPE correction moves one immutable punch event to a
     * different slot for settlement purposes.  The source row is never
     * mutated: only the exact, validated original event is removed from the
     * effective punch stream and the correction remains as the new source.
     */
    private WrongTypeResolution resolveWrongTypeReplacements(
            Schedule schedule, List<PunchEvent> punches,
            List<CorrectionSource> corrections,
            List<ScheduleSegmentSnapshot> segmentSnapshots,
            Set<String> issues)
    {
        Map<Long, Integer> referenceCounts = new LinkedHashMap<>();
        for (CorrectionSource value : corrections)
        {
            if (!approvedWrongType(value)
                    || value.originalPunchEventId == null) continue;
            referenceCounts.merge(value.originalPunchEventId, 1,
                    Integer::sum);
        }

        Set<Long> replacedEventIds = new LinkedHashSet<>();
        List<CorrectionSource> effectiveCorrections = new ArrayList<>();
        for (CorrectionSource value : corrections)
        {
            if (!approvedWrongType(value))
            {
                effectiveCorrections.add(value);
                continue;
            }
            PunchEvent original = singlePunchById(punches,
                    value.originalPunchEventId);
            boolean valid = value.originalPunchEventId != null
                    && referenceCounts.getOrDefault(
                            value.originalPunchEventId, 0) == 1
                    && validWrongTypeReplacement(schedule, value, original,
                            segmentSnapshots);
            if (valid && !wrongTypeRequestedTimeInWindow(schedule, value,
                    segmentSnapshots))
            {
                issues.add("APPROVED_CORRECTION_OUTSIDE_WINDOW_"
                        + value.targetPunchType);
                valid = false;
            }
            if (valid && wrongTypeTargetOccupied(schedule, punches, value))
            {
                issues.add("WRONG_TYPE_TARGET_ALREADY_OCCUPIED");
                valid = false;
            }
            if (!valid)
            {
                issues.add("INVALID_APPROVED_WRONG_TYPE_CORRECTION");
                continue;
            }
            replacedEventIds.add(value.originalPunchEventId);
            effectiveCorrections.add(value);
        }

        List<PunchEvent> effectivePunches = new ArrayList<>();
        for (PunchEvent event : punches)
        {
            if (event == null || event.punchEventId == null
                    || !replacedEventIds.contains(event.punchEventId))
                effectivePunches.add(event);
        }
        return new WrongTypeResolution(effectivePunches,
                effectiveCorrections);
    }

    private boolean approvedWrongType(CorrectionSource value)
    {
        return value != null && "APPROVED".equals(value.status)
                && "WRONG_TYPE".equals(value.correctionType);
    }

    private PunchEvent singlePunchById(List<PunchEvent> punches, Long eventId)
    {
        if (eventId == null) return null;
        PunchEvent match = null;
        for (PunchEvent event : punches)
        {
            if (event == null || !Objects.equals(eventId,
                    event.punchEventId)) continue;
            if (match != null) return null;
            match = event;
        }
        return match;
    }

    private boolean validWrongTypeReplacement(Schedule schedule,
            CorrectionSource correction, PunchEvent original,
            List<ScheduleSegmentSnapshot> segmentSnapshots)
    {
        if (correction.correctionRequestId == null || original == null
                || !validPunchType(correction.targetPunchType)
                || !validPunchType(original.punchType)
                || Objects.equals(correction.targetPunchType,
                        original.punchType)
                || !"ACCEPTED".equals(original.verificationStatus)
                || original.serverPunchTime == null
                || correction.requestedPunchTime == null
                || !Objects.equals(schedule.scheduleId,
                        correction.scheduleId)
                || !Objects.equals(schedule.userId, correction.userId)
                || !Objects.equals(schedule.shopId, correction.shopId)
                || !Objects.equals(schedule.businessDate,
                        correction.businessDate)
                || !Objects.equals(schedule.scheduleId, original.scheduleId)
                || !Objects.equals(schedule.userId, original.userId)
                || !Objects.equals(schedule.shopId, original.shopId)
                || !Objects.equals(schedule.businessDate,
                        original.businessDate))
            return false;

        if (!"PER_WORK_SEGMENT".equals(schedule.punchModeSnapshot))
            return correction.targetScheduleSegmentSnapshotId == null
                    && correction.targetPunchSlotKey == null;
        Long segmentId = correction.targetScheduleSegmentSnapshotId;
        ScheduleSegmentSnapshot segment = singleSegmentById(segmentSnapshots,
                segmentId);
        return segment != null && "WORK".equals(segment.segmentType)
                && Boolean.TRUE.equals(segment.paid)
                && Objects.equals(schedule.scheduleId, segment.scheduleId)
                && segment.startMinuteOffset != null
                && segment.endMinuteOffset != null
                && segment.endMinuteOffset > segment.startMinuteOffset
                && Objects.equals(segmentId,
                        original.scheduleSegmentSnapshotId)
                && segmentSlotKey(segmentId, correction.targetPunchType)
                        .equals(correction.targetPunchSlotKey)
                && segmentSlotKey(segmentId, original.punchType)
                        .equals(original.punchSlotKey);
    }

    private ScheduleSegmentSnapshot singleSegmentById(
            List<ScheduleSegmentSnapshot> segments, Long segmentId)
    {
        if (segments == null || segmentId == null) return null;
        ScheduleSegmentSnapshot match = null;
        for (ScheduleSegmentSnapshot segment : segments)
        {
            if (segment == null || !Objects.equals(segmentId,
                    segment.scheduleSegmentSnapshotId)) continue;
            if (match != null) return null;
            match = segment;
        }
        return match;
    }

    private boolean wrongTypeRequestedTimeInWindow(Schedule schedule,
            CorrectionSource correction,
            List<ScheduleSegmentSnapshot> segmentSnapshots)
    {
        if (!"PER_WORK_SEGMENT".equals(schedule.punchModeSnapshot))
        {
            PunchWindow window = rules.window(schedule,
                    correction.targetPunchType);
            return !correction.requestedPunchTime.isBefore(window.opensAt())
                    && !correction.requestedPunchTime.isAfter(
                            window.closesAt());
        }
        ScheduleSegmentSnapshot segment = singleSegmentById(segmentSnapshots,
                correction.targetScheduleSegmentSnapshotId);
        List<ScheduleSegmentSnapshot> work = segmentSnapshots.stream()
                .filter(Objects::nonNull)
                .filter(value -> "WORK".equals(value.segmentType))
                .sorted(Comparator.comparing(value -> value.segmentOrder,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        PunchWindow window = rules.effectiveSegmentWindow(schedule, work,
                segment, correction.targetPunchType);
        return !correction.requestedPunchTime.isBefore(window.opensAt())
                && !correction.requestedPunchTime.isAfter(window.closesAt());
    }

    private boolean wrongTypeTargetOccupied(Schedule schedule,
            List<PunchEvent> punches, CorrectionSource correction)
    {
        boolean segmented = "PER_WORK_SEGMENT".equals(
                schedule.punchModeSnapshot);
        for (PunchEvent event : punches)
        {
            if (event == null || Objects.equals(event.punchEventId,
                    correction.originalPunchEventId)
                    || !"ACCEPTED".equals(event.verificationStatus)
                    || !Objects.equals(schedule.scheduleId, event.scheduleId)
                    || !Objects.equals(schedule.userId, event.userId))
                continue;
            if (segmented && Objects.equals(correction.targetPunchSlotKey,
                    event.punchSlotKey)) return true;
            if (!segmented && Objects.equals(correction.targetPunchType,
                    event.punchType)) return true;
        }
        return false;
    }

    private String segmentSlotKey(Long segmentId, String type)
    { return "SEGMENT:" + segmentId + ":" + type; }

    private PunchEvent boundaryPunch(List<PunchEvent> punches, String type,
            boolean first)
    {
        Comparator<PunchEvent> order = Comparator.comparing(
                (PunchEvent value) -> value.serverPunchTime,
                Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(value -> value.punchEventId,
                        Comparator.nullsLast(Comparator.naturalOrder()));
        return punches.stream().filter(Objects::nonNull)
                .filter(value -> type.equals(value.punchType))
                .min(first ? order : order.reversed()).orElse(null);
    }

    private void validateSegmentPunches(Schedule schedule,
            List<PunchEvent> punches, List<WorkInterval> workIntervals,
            Set<String> issues)
    {
        for (PunchEvent event : punches)
        {
            String type = event == null ? null : event.punchType;
            WorkInterval work = event == null ? null
                    : workBySnapshotId(workIntervals,
                            event.scheduleSegmentSnapshotId);
            if (event == null || !validPunchType(type)
                    || !"ACCEPTED".equals(event.verificationStatus)
                    || !Objects.equals(schedule.scheduleId, event.scheduleId)
                    || !Objects.equals(schedule.userId, event.userId)
                    || event.serverPunchTime == null || work == null
                    || !slotKey(work, type).equals(event.punchSlotKey))
                issues.add("INVALID_ACCEPTED_PUNCH_"
                        + (validPunchType(type)
                                ? type : "SLOT"));
        }
    }

    private void validateSegmentCorrections(Schedule schedule,
            List<CorrectionSource> corrections,
            List<WorkInterval> workIntervals, Set<String> issues)
    {
        for (CorrectionSource value : corrections)
        {
            if (value == null || !"APPROVED".equals(value.status)) continue;
            String type = value.targetPunchType;
            WorkInterval work = workBySnapshotId(workIntervals,
                    value.targetScheduleSegmentSnapshotId);
            if (!validPunchType(type) || work == null
                    || !slotKey(work, type).equals(value.targetPunchSlotKey))
                issues.add("INVALID_APPROVED_CORRECTION_"
                        + (validPunchType(type)
                                ? type : "SLOT"));
        }
    }

    private PunchSource segmentSource(Schedule schedule,
            List<PunchEvent> punches, List<CorrectionSource> corrections,
            List<WorkInterval> allWork, WorkInterval work, String type,
            Set<String> issues)
    {
        List<PunchEvent> events = punches.stream().filter(Objects::nonNull)
                .filter(value -> type.equals(value.punchType))
                .filter(value -> Objects.equals(
                        work.scheduleSegmentSnapshotId(),
                        value.scheduleSegmentSnapshotId))
                .filter(value -> slotKey(work, type).equals(
                        value.punchSlotKey)).toList();
        if (events.size() > 1) issues.add("MULTIPLE_ACCEPTED_PUNCH_" + type);
        PunchEvent event = events.isEmpty() ? null : events.get(0);
        CorrectionSource correction = approvedSegmentCorrection(schedule,
                corrections, allWork, work, type, issues);
        return source(type, event, correction, schedule, issues);
    }

    private CorrectionSource approvedSegmentCorrection(Schedule schedule,
            List<CorrectionSource> corrections, List<WorkInterval> allWork,
            WorkInterval work, String type, Set<String> issues)
    {
        List<CorrectionSource> approved = corrections.stream()
                .filter(Objects::nonNull)
                .filter(value -> "APPROVED".equals(value.status))
                .filter(value -> type.equals(value.targetPunchType))
                .filter(value -> Objects.equals(
                        work.scheduleSegmentSnapshotId(),
                        value.targetScheduleSegmentSnapshotId))
                .filter(value -> slotKey(work, type).equals(
                        value.targetPunchSlotKey)).toList();
        if (approved.size() > 1)
        {
            issues.add("MULTIPLE_APPROVED_CORRECTION_" + type);
            return null;
        }
        if (approved.isEmpty()) return null;
        CorrectionSource value = approved.get(0);
        if (!Objects.equals(schedule.scheduleId, value.scheduleId)
                || !Objects.equals(schedule.userId, value.userId)
                || !Objects.equals(schedule.shopId, value.shopId)
                || !Objects.equals(schedule.businessDate, value.businessDate)
                || value.requestedPunchTime == null)
            issues.add("INVALID_APPROVED_CORRECTION_" + type);
        else
        {
            int index = allWork.indexOf(work);
            PunchWindow window = rules.effectiveSegmentWindow(schedule,
                    work.start(), work.end(), index > 0
                            ? allWork.get(index - 1).end() : null,
                    index >= 0 && index + 1 < allWork.size()
                            ? allWork.get(index + 1).start() : null,
                    type);
            if (value.requestedPunchTime.isBefore(window.opensAt())
                    || value.requestedPunchTime.isAfter(window.closesAt()))
                issues.add("APPROVED_CORRECTION_OUTSIDE_WINDOW_" + type);
        }
        return value;
    }

    private WorkInterval workBySnapshotId(List<WorkInterval> values, Long id)
    {
        if (id == null) return null;
        return values.stream().filter(value -> Objects.equals(id,
                value.scheduleSegmentSnapshotId())).findFirst().orElse(null);
    }

    private String slotKey(WorkInterval work, String type)
    {
        return segmentSlotKey(work.scheduleSegmentSnapshotId(), type);
    }

    private boolean validPunchType(String value)
    {
        return "IN".equals(value) || "OUT".equals(value);
    }

    public Bounds bounds(Schedule schedule)
    {
        PunchWindow in = rules.window(schedule, "IN");
        PunchWindow out = rules.window(schedule, "OUT");
        LocalDateTime start = in.opensAt().plusMinutes(
                nonNegative(schedule.checkInOpenMinutesSnapshot));
        LocalDateTime end = out.closesAt().minusMinutes(
                nonNegative(schedule.checkOutCloseMinutesSnapshot));
        return new Bounds(start, end);
    }

    public boolean fullyCoveredByApprovedLeave(Schedule schedule,
            List<LeaveSegmentSource> approvedLeaves,
            List<ScheduleSegmentSnapshot> segmentSnapshots)
    {
        Set<String> issues = new LinkedHashSet<>();
        Bounds bounds = bounds(schedule);
        List<WorkInterval> scheduledWork = scheduledWorkIntervals(schedule,
                segmentSnapshots, bounds, issues);
        int scheduled = nonNegative(schedule.standardMinutesSnapshot);
        LeaveTotals leave = leaveTotals(approvedLeaves, scheduledWork,
                scheduled, issues);
        return issues.isEmpty() && scheduled > 0
                && leave.paidMinutes() + leave.unpaidMinutes() >= scheduled;
    }

    private CorrectionSource approvedCorrection(Schedule schedule,
            List<CorrectionSource> corrections, String type,
            Set<String> issues)
    {
        List<CorrectionSource> approved = corrections.stream()
                .filter(Objects::nonNull)
                .filter(value -> "APPROVED".equals(value.status))
                .filter(value -> type.equals(value.targetPunchType))
                .toList();
        if (approved.size() > 1)
        {
            issues.add("MULTIPLE_APPROVED_CORRECTION_" + type);
            return null;
        }
        if (approved.isEmpty()) return null;
        CorrectionSource value = approved.get(0);
        if (!Objects.equals(schedule.scheduleId, value.scheduleId)
                || !Objects.equals(schedule.userId, value.userId)
                || !Objects.equals(schedule.shopId, value.shopId)
                || !Objects.equals(schedule.businessDate, value.businessDate)
                || value.requestedPunchTime == null)
            issues.add("INVALID_APPROVED_CORRECTION_" + type);
        else
        {
            PunchWindow window = rules.window(schedule, type);
            if (value.requestedPunchTime.isBefore(window.opensAt())
                    || value.requestedPunchTime.isAfter(window.closesAt()))
                issues.add("APPROVED_CORRECTION_OUTSIDE_WINDOW_" + type);
        }
        return value;
    }

    private PunchSource source(String type, PunchEvent event,
            CorrectionSource correction, Schedule schedule,
            Set<String> issues)
    {
        if (correction != null && correction.requestedPunchTime != null)
            return new PunchSource(correction.requestedPunchTime, null,
                    correction.correctionRequestId);
        if (event == null) return null;
        if (!type.equals(event.punchType)
                || !"ACCEPTED".equals(event.verificationStatus)
                || !Objects.equals(schedule.scheduleId, event.scheduleId)
                || !Objects.equals(schedule.userId, event.userId)
                || event.serverPunchTime == null)
        {
            issues.add("INVALID_ACCEPTED_PUNCH_" + type);
            return null;
        }
        return new PunchSource(event.serverPunchTime, event.punchEventId,
                null);
    }

    private LeaveTotals leaveTotals(List<LeaveSegmentSource> values,
            List<WorkInterval> scheduledWork, int scheduled,
            Set<String> issues)
    {
        List<LeaveInterval> intervals = new ArrayList<>();
        if (values != null) for (LeaveSegmentSource value : values)
        {
            if (value == null || value.startTime == null
                    || value.endTime == null
                    || !value.endTime.isAfter(value.startTime))
            {
                issues.add("INVALID_APPROVED_LEAVE_SEGMENT");
                continue;
            }
            int denominator = value.totalMinutes != null
                    && value.totalMinutes > 0 ? value.totalMinutes
                            : minutes(value.startTime, value.endTime);
            int paidSource = Math.max(0,
                    value.paidMinutes == null ? 0 : value.paidMinutes);
            int unpaidSource = Math.max(0,
                    value.unpaidMinutes == null ? 0 : value.unpaidMinutes);
            int ratioBase = paidSource + unpaidSource > 0
                    ? paidSource + unpaidSource : denominator;
            for (WorkInterval work : scheduledWork)
            {
                LocalDateTime start = later(value.startTime, work.start());
                LocalDateTime end = earlier(value.endTime, work.end());
                if (!end.isAfter(start)) continue;
                int overlap = minutes(start, end);
                int paid = ratioBase <= 0 ? 0
                        : BigDecimal.valueOf(overlap)
                                .multiply(BigDecimal.valueOf(paidSource))
                                .divide(BigDecimal.valueOf(ratioBase), 0,
                                        RoundingMode.HALF_UP).intValue();
                intervals.add(new LeaveInterval(start, end,
                        Math.min(overlap, Math.max(0, paid)),
                        Math.max(0, overlap - paid)));
            }
        }
        intervals.sort(Comparator.comparing(LeaveInterval::start)
                .thenComparing(LeaveInterval::end));
        intervals = unionLeaveIntervals(intervals);
        int paid = intervals.stream().mapToInt(LeaveInterval::paidMinutes)
                .sum();
        int unpaid = intervals.stream().mapToInt(
                LeaveInterval::unpaidMinutes).sum();
        paid = Math.min(scheduled, paid);
        unpaid = Math.min(Math.max(0, scheduled - paid), unpaid);
        return new LeaveTotals(paid, unpaid, List.copyOf(intervals));
    }

    /**
     * Approved leave is consumed as an interval union.  Atomic slices avoid
     * double-counting overlapping requests; where paid/unpaid policies overlap,
     * the highest paid ratio wins for that exact slice.
     */
    private List<LeaveInterval> unionLeaveIntervals(
            List<LeaveInterval> source)
    {
        if (source == null || source.isEmpty()) return new ArrayList<>();
        List<LocalDateTime> points = source.stream()
                .flatMap(value -> java.util.stream.Stream.of(value.start(),
                        value.end())).distinct().sorted().toList();
        List<LeaveInterval> values = new ArrayList<>();
        for (int index = 0; index + 1 < points.size(); index++)
        {
            LocalDateTime start = points.get(index);
            LocalDateTime end = points.get(index + 1);
            if (!end.isAfter(start)) continue;
            List<LeaveInterval> covering = source.stream()
                    .filter(value -> !value.start().isAfter(start)
                            && !value.end().isBefore(end)).toList();
            if (covering.isEmpty()) continue;
            int sliceMinutes = minutes(start, end);
            BigDecimal paidRatio = BigDecimal.ZERO;
            for (LeaveInterval value : covering)
            {
                int duration = Math.max(1, minutes(value.start(), value.end()));
                BigDecimal ratio = BigDecimal.valueOf(value.paidMinutes())
                        .divide(BigDecimal.valueOf(duration), 8,
                                RoundingMode.HALF_UP);
                if (ratio.compareTo(paidRatio) > 0) paidRatio = ratio;
            }
            int paid = BigDecimal.valueOf(sliceMinutes).multiply(paidRatio)
                    .setScale(0, RoundingMode.HALF_UP).intValue();
            paid = Math.max(0, Math.min(sliceMinutes, paid));
            values.add(new LeaveInterval(start, end, paid,
                    sliceMinutes - paid));
        }
        return values;
    }

    private int coveredMinutes(List<LeaveInterval> values,
            LocalDateTime start, LocalDateTime end)
    {
        if (start == null || end == null || !end.isAfter(start)) return 0;
        int total = 0;
        for (LeaveInterval value : values)
        {
            LocalDateTime overlapStart = later(start, value.start());
            LocalDateTime overlapEnd = earlier(end, value.end());
            if (overlapEnd.isAfter(overlapStart))
                total += minutes(overlapStart, overlapEnd);
        }
        return total;
    }

    private int uncoveredScheduledMinutes(List<WorkInterval> scheduledWork,
            List<LeaveInterval> approvedLeave, LocalDateTime start,
            LocalDateTime end)
    {
        if (start == null || end == null || !end.isAfter(start)) return 0;
        int total = 0;
        for (WorkInterval work : scheduledWork)
        {
            LocalDateTime overlapStart = later(start, work.start());
            LocalDateTime overlapEnd = earlier(end, work.end());
            if (overlapEnd.isAfter(overlapStart))
                total += minutes(overlapStart, overlapEnd)
                        - coveredMinutes(approvedLeave, overlapStart,
                                overlapEnd);
        }
        return Math.max(0, total);
    }

    private List<WorkInterval> scheduledWorkIntervals(Schedule schedule,
            List<ScheduleSegmentSnapshot> segments, Bounds bounds,
            Set<String> issues)
    {
        if (segments == null || segments.isEmpty())
        {
            issues.add("SCHEDULE_SEGMENT_SNAPSHOT_MISSING");
            return List.of();
        }
        List<WorkInterval> values = new ArrayList<>();
        int paidWorkMinutes = 0;
        int previousEnd = -1;
        int expectedOrder = 1;
        int nominalStart = minutes(schedule.businessDate.atStartOfDay(),
                bounds.start());
        int nominalEnd = minutes(schedule.businessDate.atStartOfDay(),
                bounds.end());
        for (ScheduleSegmentSnapshot segment : segments)
        {
            if (segment == null || segment.startMinuteOffset == null
                    || segment.endMinuteOffset == null
                    || segment.startMinuteOffset < 0
                    || segment.endMinuteOffset <= segment.startMinuteOffset
                    || segment.startMinuteOffset < nominalStart
                    || segment.endMinuteOffset > nominalEnd
                    || segment.startMinuteOffset < previousEnd
                    || !Objects.equals(schedule.scheduleId,
                            segment.scheduleId)
                    || !Objects.equals(segment.segmentOrder, expectedOrder)
                    || segment.segmentType == null
                    || !Set.of("WORK", "BREAK").contains(
                            segment.segmentType))
            {
                issues.add("SCHEDULE_SEGMENT_SNAPSHOT_INVALID");
                continue;
            }
            previousEnd = segment.endMinuteOffset;
            expectedOrder++;
            if (!"WORK".equals(segment.segmentType)
                    || !Boolean.TRUE.equals(segment.paid)) continue;
            LocalDateTime start = schedule.businessDate.atStartOfDay()
                    .plusMinutes(segment.startMinuteOffset);
            LocalDateTime end = schedule.businessDate.atStartOfDay()
                    .plusMinutes(segment.endMinuteOffset);
            values.add(new WorkInterval(
                    segment.scheduleSegmentSnapshotId,
                    segment.segmentOrder, start, end));
            paidWorkMinutes += minutes(start, end);
        }
        if (paidWorkMinutes != nonNegative(schedule.standardMinutesSnapshot))
            issues.add("SCHEDULE_SEGMENT_SNAPSHOT_INVALID");
        return List.copyOf(values);
    }

    private WorkTotals workedMinutes(LocalDateTime actualStart,
            LocalDateTime actualEnd, Bounds bounds,
            List<WorkInterval> scheduledWork,
            List<LeaveInterval> approvedLeave)
    {
        int scheduled = 0;
        for (WorkInterval work : scheduledWork)
        {
            LocalDateTime start = later(actualStart, work.start());
            LocalDateTime end = earlier(actualEnd, work.end());
            if (end.isAfter(start))
                scheduled += minutes(start, end)
                        - coveredMinutes(approvedLeave, start, end);
        }
        int overtime = 0;
        if (actualStart.isBefore(bounds.start()))
        {
            LocalDateTime end = earlier(actualEnd, bounds.start());
            if (end.isAfter(actualStart))
                overtime += minutes(actualStart, end);
        }
        if (actualEnd.isAfter(bounds.end()))
        {
            LocalDateTime start = later(actualStart, bounds.end());
            if (actualEnd.isAfter(start))
                overtime += minutes(start, actualEnd);
        }
        scheduled = Math.max(0, scheduled);
        return new WorkTotals(scheduled, Math.max(0, scheduled + overtime));
    }

    private int workedRemainingMinutes(LocalDateTime actualStart,
            LocalDateTime actualEnd, List<TimeInterval> remaining)
    {
        if (actualStart == null || actualEnd == null
                || !actualEnd.isAfter(actualStart) || remaining == null)
            return 0;
        int value = 0;
        for (TimeInterval interval : remaining)
        {
            LocalDateTime start = later(actualStart, interval.start());
            LocalDateTime end = earlier(actualEnd, interval.end());
            if (end.isAfter(start)) value += minutes(start, end);
        }
        return Math.max(0, value);
    }

    private List<TimeInterval> remainingIntervals(
            List<WorkInterval> scheduledWork,
            List<LeaveSegmentSource> approvedLeaves)
    {
        List<TimeInterval> values = new ArrayList<>();
        if (scheduledWork != null) for (WorkInterval work : scheduledWork)
            values.addAll(AttendanceLeaveCoveragePolicy.analyze(work.start(),
                    work.end(), approvedLeaves).remainingIntervals());
        values.sort(Comparator.comparing(TimeInterval::start)
                .thenComparing(TimeInterval::end));
        return List.copyOf(values);
    }

    private int confirmedWorkedMinutes(Schedule schedule,
            TimeInterval interval,
            List<RemainingWorkConfirmationSource> confirmations,
            Set<String> issues)
    {
        List<RemainingWorkConfirmationSource> matches = confirmations == null
                ? List.of() : confirmations.stream().filter(Objects::nonNull)
                        .filter(value -> Objects.equals(schedule.scheduleId,
                                value.scheduleId)
                                && Objects.equals(schedule.userId,
                                        value.userId)
                                && Objects.equals(schedule.shopId,
                                        value.shopId)
                                && Objects.equals(schedule.businessDate,
                                        value.businessDate)
                                && interval.start().equals(
                                        value.remainingStart)
                                && interval.end().equals(value.remainingEnd))
                        .sorted(Comparator.comparing(
                                (RemainingWorkConfirmationSource value) ->
                                        value.confirmationId,
                                Comparator.nullsFirst(
                                        Comparator.naturalOrder())).reversed())
                        .toList();
        if (matches.isEmpty())
        {
            issues.add("REMAINING_WORK_CONFIRMATION_REQUIRED");
            return 0;
        }
        RemainingWorkConfirmationSource latest = matches.get(0);
        if ("RETURN_FOR_EVIDENCE".equals(latest.decision))
        {
            issues.add("REMAINING_WORK_EVIDENCE_REQUIRED");
            return 0;
        }
        if ("ABSENT".equals(latest.decision))
        {
            if (latest.actualArrivalTime != null
                    || latest.actualDepartureTime != null)
                issues.add("INVALID_REMAINING_WORK_CONFIRMATION");
            return 0;
        }
        if (!"ATTENDED".equals(latest.decision)
                || latest.actualArrivalTime == null
                || latest.actualDepartureTime == null
                || !latest.actualDepartureTime.isAfter(
                        latest.actualArrivalTime)
                || latest.actualArrivalTime.isBefore(interval.start())
                || latest.actualDepartureTime.isAfter(interval.end()))
        {
            issues.add("INVALID_REMAINING_WORK_CONFIRMATION");
            return 0;
        }
        return minutes(latest.actualArrivalTime,
                latest.actualDepartureTime);
    }

    private DayResult base(Schedule schedule)
    {
        DayResult value = new DayResult();
        value.scheduleId = schedule.scheduleId;
        value.userId = schedule.userId;
        value.userName = schedule.userName;
        value.shopId = schedule.shopId;
        value.businessDate = schedule.businessDate;
        value.shiftId = schedule.shiftId;
        value.scheduledMinutes = nonNegative(schedule.standardMinutesSnapshot);
        value.workedMinutes = 0;
        value.paidLeaveMinutes = 0;
        value.unpaidLeaveMinutes = 0;
        value.absenceMinutes = 0;
        value.lateMinutes = 0;
        value.earlyLeaveMinutes = 0;
        return value;
    }

    private int minutes(LocalDateTime start, LocalDateTime end)
    { return Math.toIntExact(Duration.between(start, end).toMinutes()); }
    private int nonNegative(Integer value)
    { return value == null ? 0 : Math.max(0, value); }
    private LocalDateTime later(LocalDateTime left, LocalDateTime right)
    { return left.isAfter(right) ? left : right; }
    private LocalDateTime earlier(LocalDateTime left, LocalDateTime right)
    { return left.isBefore(right) ? left : right; }

    public record Bounds(LocalDateTime start, LocalDateTime end) { }
    public record Evaluation(DayResult result, List<String> issueCodes)
    { public boolean ready() { return issueCodes.isEmpty(); } }
    private record WrongTypeResolution(List<PunchEvent> punches,
            List<CorrectionSource> corrections) { }
    private record PunchSource(LocalDateTime time, Long eventId,
            Long correctionRequestId) { }
    private record LeaveInterval(LocalDateTime start, LocalDateTime end,
            int paidMinutes, int unpaidMinutes) { }
    private record WorkInterval(Long scheduleSegmentSnapshotId,
            Integer segmentOrder, LocalDateTime start,
            LocalDateTime end) { }
    private record WorkTotals(int scheduledMinutes, int totalMinutes)
    { private static final WorkTotals NONE = new WorkTotals(0, 0); }
    private record LeaveTotals(int paidMinutes, int unpaidMinutes,
            List<LeaveInterval> intervals) { }
}
