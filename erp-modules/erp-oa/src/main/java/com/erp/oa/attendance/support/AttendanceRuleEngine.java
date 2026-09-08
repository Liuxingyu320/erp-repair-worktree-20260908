package com.erp.oa.attendance.support;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Component;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.attendance.domain.AttendanceModels.Schedule;
import com.erp.oa.attendance.domain.AttendanceModels.ScheduleSegmentSnapshot;

@Component
public class AttendanceRuleEngine
{
    public static final String SHIFT_BOUNDARY = "SHIFT_BOUNDARY";
    public static final String PER_WORK_SEGMENT = "PER_WORK_SEGMENT";

    public PunchWindow window(Schedule schedule, String punchType)
    {
        requirePublished(schedule);
        LocalDate date = schedule.businessDate;
        LocalDateTime start = at(date, schedule.startTimeSnapshot);
        LocalDateTime end = at(date, schedule.endTimeSnapshot);
        if (Boolean.TRUE.equals(schedule.crossDaySnapshot)
                || !end.isAfter(start))
        {
            end = end.plusDays(1);
        }
        return switch (normalizeType(punchType))
        {
            case "IN" -> new PunchWindow(
                    start.minusMinutes(nonNegative(
                            schedule.checkInOpenMinutesSnapshot)),
                    start.plusMinutes(nonNegative(
                            schedule.checkInCloseMinutesSnapshot)));
            case "OUT" -> new PunchWindow(
                    end.minusMinutes(nonNegative(
                            schedule.checkOutOpenMinutesSnapshot)),
                    end.plusMinutes(nonNegative(
                            schedule.checkOutCloseMinutesSnapshot)));
            default -> throw new ServiceException("PUNCH_TYPE_INVALID");
        };
    }

    public void requireWithinWindow(Schedule schedule, String punchType,
            LocalDateTime now)
    {
        PunchWindow window = window(schedule, punchType);
        if (now.isBefore(window.opensAt()) || now.isAfter(window.closesAt()))
        {
            throw new ServiceException("OUTSIDE_PUNCH_WINDOW");
        }
    }

    /**
     * Resolves the independently frozen punch window for a published WORK
     * segment.  Segment offsets are measured from the business-date midnight,
     * so values above 1440 naturally retain cross-day semantics.
     */
    public PunchWindow window(Schedule schedule,
            ScheduleSegmentSnapshot segment, String punchType)
    {
        requirePublished(schedule);
        requireWorkSegment(schedule, segment);
        LocalDateTime start = segmentStart(schedule, segment);
        LocalDateTime end = segmentEnd(schedule, segment);
        return switch (normalizeType(punchType))
        {
            case "IN" -> new PunchWindow(
                    start.minusMinutes(nonNegative(
                            schedule.checkInOpenMinutesSnapshot)),
                    start.plusMinutes(nonNegative(
                            schedule.checkInCloseMinutesSnapshot)));
            case "OUT" -> new PunchWindow(
                    end.minusMinutes(nonNegative(
                            schedule.checkOutOpenMinutesSnapshot)),
                    end.plusMinutes(nonNegative(
                            schedule.checkOutCloseMinutesSnapshot)));
            default -> throw new ServiceException("PUNCH_TYPE_INVALID");
        };
    }

    /**
     * Returns the effective window used by a per-work-segment schedule.
     * Only the two facing sides of adjacent WORK segments are clipped: the
     * previous OUT close and the next IN open meet at the absolute midpoint
     * of the break.  This keeps the configured first-IN/last-OUT windows
     * intact while ensuring two adjacent slots never advertise overlapping
     * availability.
     */
    public PunchWindow effectiveSegmentWindow(Schedule schedule,
            List<ScheduleSegmentSnapshot> workSegments,
            ScheduleSegmentSnapshot segment, String punchType)
    {
        window(schedule, segment, punchType);
        List<ScheduleSegmentSnapshot> ordered = workSegments == null
                ? List.of() : workSegments.stream()
                        .filter(Objects::nonNull)
                        .filter(value -> "WORK".equals(value.segmentType))
                        .sorted(Comparator.comparing(
                                value -> value.segmentOrder,
                                Comparator.nullsLast(
                                        Comparator.naturalOrder())))
                        .toList();
        int index = -1;
        for (int i = 0; i < ordered.size(); i++)
            if (Objects.equals(segment.scheduleSegmentSnapshotId,
                    ordered.get(i).scheduleSegmentSnapshotId))
                index = i;
        if (index < 0)
            throw new ServiceException("SCHEDULE_WORK_SEGMENT_INVALID");

        String type = normalizeType(punchType);
        return effectiveSegmentWindow(schedule,
                segmentStart(schedule, segment), segmentEnd(schedule, segment),
                index > 0 ? segmentEnd(schedule, ordered.get(index - 1))
                        : null,
                index + 1 < ordered.size()
                        ? segmentStart(schedule, ordered.get(index + 1))
                        : null,
                type);
    }

    public PunchWindow effectiveSegmentWindow(Schedule schedule,
            LocalDateTime segmentStart, LocalDateTime segmentEnd,
            LocalDateTime previousWorkEnd, LocalDateTime nextWorkStart,
            String punchType)
    {
        requirePublished(schedule);
        if (segmentStart == null || segmentEnd == null
                || !segmentEnd.isAfter(segmentStart))
            throw new ServiceException("SCHEDULE_WORK_SEGMENT_INVALID");
        String type = normalizeType(punchType);
        PunchWindow configured = switch (type)
        {
            case "IN" -> new PunchWindow(segmentStart.minusMinutes(
                    nonNegative(schedule.checkInOpenMinutesSnapshot)),
                    segmentStart.plusMinutes(nonNegative(
                            schedule.checkInCloseMinutesSnapshot)));
            case "OUT" -> new PunchWindow(segmentEnd.minusMinutes(
                    nonNegative(schedule.checkOutOpenMinutesSnapshot)),
                    segmentEnd.plusMinutes(nonNegative(
                            schedule.checkOutCloseMinutesSnapshot)));
            default -> throw new ServiceException("PUNCH_TYPE_INVALID");
        };
        LocalDateTime opensAt = configured.opensAt();
        LocalDateTime closesAt = configured.closesAt();
        if ("IN".equals(type) && previousWorkEnd != null)
        {
            LocalDateTime midpoint = midpoint(previousWorkEnd, segmentStart);
            if (opensAt.isBefore(midpoint)) opensAt = midpoint;
        }
        if ("OUT".equals(type) && nextWorkStart != null)
        {
            LocalDateTime midpoint = midpoint(segmentEnd, nextWorkStart);
            if (closesAt.isAfter(midpoint)) closesAt = midpoint;
        }
        if (closesAt.isBefore(opensAt))
            throw new ServiceException("PUNCH_EFFECTIVE_WINDOW_INVALID");
        return new PunchWindow(opensAt, closesAt);
    }

    public void requireWithinWindow(Schedule schedule,
            ScheduleSegmentSnapshot segment, String punchType,
            LocalDateTime now)
    {
        PunchWindow window = window(schedule, segment, punchType);
        if (now == null || now.isBefore(window.opensAt())
                || now.isAfter(window.closesAt()))
            throw new ServiceException("OUTSIDE_PUNCH_WINDOW");
    }

    public LocalDateTime segmentStart(Schedule schedule,
            ScheduleSegmentSnapshot segment)
    {
        requirePublished(schedule);
        requireWorkSegment(schedule, segment);
        return schedule.businessDate.atStartOfDay().plusMinutes(
                segment.startMinuteOffset);
    }

    public LocalDateTime segmentEnd(Schedule schedule,
            ScheduleSegmentSnapshot segment)
    {
        requirePublished(schedule);
        requireWorkSegment(schedule, segment);
        return schedule.businessDate.atStartOfDay().plusMinutes(
                segment.endMinuteOffset);
    }

    public String slotKey(ScheduleSegmentSnapshot segment, String punchType)
    {
        if (segment == null || segment.scheduleSegmentSnapshotId == null)
            throw new ServiceException("SCHEDULE_SEGMENT_SNAPSHOT_INVALID");
        return "SEGMENT:" + segment.scheduleSegmentSnapshotId + ":"
                + normalizeType(punchType);
    }

    public String normalizePunchMode(String punchMode)
    {
        String value = punchMode == null || punchMode.isBlank()
                ? SHIFT_BOUNDARY
                : punchMode.trim().toUpperCase(Locale.ROOT);
        if (!SHIFT_BOUNDARY.equals(value)
                && !PER_WORK_SEGMENT.equals(value))
            throw new ServiceException("SHIFT_PUNCH_MODE_INVALID");
        return value;
    }

    public int lateMinutes(Schedule schedule, LocalDateTime actual)
    {
        LocalDateTime expected = at(schedule.businessDate,
                schedule.startTimeSnapshot).plusMinutes(nonNegative(
                        schedule.graceInMinutesSnapshot));
        return actual.isAfter(expected)
                ? Math.toIntExact(ChronoUnit.MINUTES.between(expected, actual))
                : 0;
    }

    public int earlyMinutes(Schedule schedule, LocalDateTime actual)
    {
        LocalDateTime expected = at(schedule.businessDate,
                schedule.endTimeSnapshot);
        if (Boolean.TRUE.equals(schedule.crossDaySnapshot)
                || !expected.isAfter(at(schedule.businessDate,
                        schedule.startTimeSnapshot)))
        {
            expected = expected.plusDays(1);
        }
        expected = expected.minusMinutes(nonNegative(
                schedule.graceOutMinutesSnapshot));
        return actual.isBefore(expected)
                ? Math.toIntExact(ChronoUnit.MINUTES.between(actual, expected))
                : 0;
    }

    public String normalizeType(String punchType)
    {
        String value = punchType == null ? "" : punchType.trim()
                .toUpperCase(java.util.Locale.ROOT);
        if (!"IN".equals(value) && !"OUT".equals(value))
        {
            throw new ServiceException("PUNCH_TYPE_INVALID");
        }
        return value;
    }

    private void requirePublished(Schedule schedule)
    {
        if (schedule == null || !"PUBLISHED".equals(schedule.status)
                || schedule.businessDate == null
                || schedule.startTimeSnapshot == null
                || schedule.endTimeSnapshot == null)
        {
            throw new ServiceException("PUBLISHED_SCHEDULE_REQUIRED");
        }
    }

    private void requireWorkSegment(Schedule schedule,
            ScheduleSegmentSnapshot segment)
    {
        if (segment == null || segment.scheduleSegmentSnapshotId == null
                || segment.scheduleId == null
                || !Objects.equals(schedule.scheduleId, segment.scheduleId)
                || !"WORK".equals(segment.segmentType)
                || segment.segmentOrder == null || segment.segmentOrder <= 0
                || segment.startMinuteOffset == null
                || segment.endMinuteOffset == null
                || segment.startMinuteOffset < 0
                || segment.endMinuteOffset <= segment.startMinuteOffset)
            throw new ServiceException("SCHEDULE_WORK_SEGMENT_INVALID");
    }

    private LocalDateTime at(LocalDate date, LocalTime time)
    {
        return LocalDateTime.of(date, time);
    }

    private int nonNegative(Integer value)
    {
        return value == null ? 0 : Math.max(0, value);
    }

    public record PunchWindow(LocalDateTime opensAt,
            LocalDateTime closesAt) { }

    private LocalDateTime midpoint(LocalDateTime left, LocalDateTime right)
    {
        if (left == null || right == null || right.isBefore(left))
            throw new ServiceException("SCHEDULE_WORK_SEGMENT_INVALID");
        return left.plusNanos(Duration.between(left, right).toNanos() / 2L);
    }
}
