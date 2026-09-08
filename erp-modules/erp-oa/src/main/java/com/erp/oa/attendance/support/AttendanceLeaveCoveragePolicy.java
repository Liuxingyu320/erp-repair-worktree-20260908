package com.erp.oa.attendance.support;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import com.erp.oa.attendance.domain.AttendanceSettlementModels.LeaveSegmentSource;

/** Shared boundary and remaining-work semantics for approved leave. */
public final class AttendanceLeaveCoveragePolicy
{
    private AttendanceLeaveCoveragePolicy() { }

    public static Coverage analyze(LocalDateTime workStart,
            LocalDateTime workEnd, List<LeaveSegmentSource> leaves)
    {
        if (workStart == null || workEnd == null
                || !workEnd.isAfter(workStart))
            throw new IllegalArgumentException("invalid work interval");
        List<TimeInterval> covered = mergedCoverage(workStart, workEnd,
                leaves);
        List<TimeInterval> remaining = new ArrayList<>();
        LocalDateTime cursor = workStart;
        for (TimeInterval interval : covered)
        {
            if (interval.start().isAfter(cursor))
                remaining.add(new TimeInterval(cursor, interval.start()));
            if (interval.end().isAfter(cursor)) cursor = interval.end();
        }
        if (cursor.isBefore(workEnd))
            remaining.add(new TimeInterval(cursor, workEnd));

        boolean startCovered = covered.stream().anyMatch(value ->
                !value.start().isAfter(workStart)
                        && value.end().isAfter(workStart));
        boolean endCovered = covered.stream().anyMatch(value ->
                value.start().isBefore(workEnd)
                        && !value.end().isBefore(workEnd));
        return new Coverage(startCovered, endCovered,
                List.copyOf(covered), List.copyOf(remaining));
    }

    public static List<TimeInterval> mergedCoverage(LocalDateTime workStart,
            LocalDateTime workEnd, List<LeaveSegmentSource> leaves)
    {
        List<TimeInterval> clipped = new ArrayList<>();
        if (leaves != null) for (LeaveSegmentSource leave : leaves)
        {
            if (leave == null || leave.startTime == null
                    || leave.endTime == null
                    || !leave.endTime.isAfter(leave.startTime)) continue;
            LocalDateTime start = later(workStart, leave.startTime);
            LocalDateTime end = earlier(workEnd, leave.endTime);
            if (end.isAfter(start)) clipped.add(new TimeInterval(start, end));
        }
        clipped.sort(Comparator.comparing(TimeInterval::start)
                .thenComparing(TimeInterval::end));
        List<TimeInterval> merged = new ArrayList<>();
        for (TimeInterval value : clipped)
        {
            if (merged.isEmpty())
            {
                merged.add(value);
                continue;
            }
            TimeInterval previous = merged.get(merged.size() - 1);
            if (!value.start().isAfter(previous.end()))
                merged.set(merged.size() - 1, new TimeInterval(
                        previous.start(), later(previous.end(), value.end())));
            else merged.add(value);
        }
        return List.copyOf(merged);
    }

    public record Coverage(boolean startCovered, boolean endCovered,
            List<TimeInterval> coveredIntervals,
            List<TimeInterval> remainingIntervals)
    {
        public boolean fullyCovered()
        { return remainingIntervals == null || remainingIntervals.isEmpty(); }

        /** Both original boundary slots are impossible, but work remains. */
        public boolean requiresRemainingWorkConfirmation()
        { return startCovered && endCovered && !fullyCovered(); }

        public int remainingMinutes()
        {
            return remainingIntervals == null ? 0 : remainingIntervals.stream()
                    .filter(Objects::nonNull)
                    .mapToInt(TimeInterval::minutes).sum();
        }
    }

    public record TimeInterval(LocalDateTime start, LocalDateTime end)
    {
        public TimeInterval
        {
            if (start == null || end == null || !end.isAfter(start))
                throw new IllegalArgumentException("invalid time interval");
        }

        public int minutes()
        { return Math.toIntExact(Duration.between(start, end).toMinutes()); }
    }

    private static LocalDateTime later(LocalDateTime left,
            LocalDateTime right)
    { return left.isAfter(right) ? left : right; }

    private static LocalDateTime earlier(LocalDateTime left,
            LocalDateTime right)
    { return left.isBefore(right) ? left : right; }
}
