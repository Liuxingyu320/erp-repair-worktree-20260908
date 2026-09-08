package com.erp.oa.attendance.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.erp.oa.attendance.domain.AttendanceModels.Schedule;
import com.erp.oa.attendance.domain.AttendanceModels.ScheduleSegmentSnapshot;

class AttendanceRuleEngineTest
{
    private final AttendanceRuleEngine rules = new AttendanceRuleEngine();

    @Test
    void crossDayWindowsAndGraceUseFrozenScheduleSnapshot()
    {
        Schedule value = new Schedule();
        value.status = "PUBLISHED";
        value.businessDate = LocalDate.of(2026, 8, 20);
        value.startTimeSnapshot = LocalTime.of(20, 0);
        value.endTimeSnapshot = LocalTime.of(8, 0);
        value.crossDaySnapshot = true;
        value.checkInOpenMinutesSnapshot = 60;
        value.checkInCloseMinutesSnapshot = 30;
        value.checkOutOpenMinutesSnapshot = 60;
        value.checkOutCloseMinutesSnapshot = 30;
        value.graceInMinutesSnapshot = 5;
        value.graceOutMinutesSnapshot = 5;

        assertThat(rules.window(value, "OUT").opensAt())
                .isEqualTo(LocalDateTime.of(2026, 8, 21, 7, 0));
        assertThatCode(() -> rules.requireWithinWindow(value, "OUT",
                LocalDateTime.of(2026, 8, 21, 8, 10)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> rules.requireWithinWindow(value, "OUT",
                LocalDateTime.of(2026, 8, 21, 9, 0)))
                .hasMessage("OUTSIDE_PUNCH_WINDOW");
        assertThat(rules.lateMinutes(value,
                LocalDateTime.of(2026, 8, 20, 20, 20))).isEqualTo(15);
        assertThat(rules.earlyMinutes(value,
                LocalDateTime.of(2026, 8, 21, 7, 30))).isEqualTo(25);
    }

    @Test
    void workSegmentWindowsUseOffsetsAndProduceStableSlotKeys()
    {
        Schedule schedule = new Schedule();
        schedule.scheduleId = 31L;
        schedule.status = "PUBLISHED";
        schedule.businessDate = LocalDate.of(2026, 8, 20);
        schedule.startTimeSnapshot = LocalTime.of(8, 0);
        schedule.endTimeSnapshot = LocalTime.of(4, 0);
        schedule.crossDaySnapshot = true;
        schedule.checkInOpenMinutesSnapshot = 30;
        schedule.checkInCloseMinutesSnapshot = 15;
        schedule.checkOutOpenMinutesSnapshot = 20;
        schedule.checkOutCloseMinutesSnapshot = 30;
        ScheduleSegmentSnapshot segment = new ScheduleSegmentSnapshot();
        segment.scheduleSegmentSnapshotId = 91L;
        segment.scheduleId = 31L;
        segment.segmentOrder = 3;
        segment.segmentType = "WORK";
        segment.startMinuteOffset = 1500;
        segment.endMinuteOffset = 1680;

        assertThat(rules.slotKey(segment, " in "))
                .isEqualTo("SEGMENT:91:IN");
        assertThat(rules.window(schedule, segment, "IN").opensAt())
                .isEqualTo(LocalDateTime.of(2026, 8, 21, 0, 30));
        assertThat(rules.window(schedule, segment, "OUT").closesAt())
                .isEqualTo(LocalDateTime.of(2026, 8, 21, 4, 30));
    }

    @Test
    void punchModeDefaultsToBoundaryAndRejectsUnknownValues()
    {
        assertThat(rules.normalizePunchMode(null))
                .isEqualTo(AttendanceRuleEngine.SHIFT_BOUNDARY);
        assertThat(rules.normalizePunchMode(" per_work_segment "))
                .isEqualTo(AttendanceRuleEngine.PER_WORK_SEGMENT);
        assertThatThrownBy(() -> rules.normalizePunchMode("AUTO"))
                .hasMessage("SHIFT_PUNCH_MODE_INVALID");
    }

    @Test
    void adjacentSegmentFacingWindowsMeetAtBreakMidpoint()
    {
        Schedule schedule = publishedSchedule(41L, LocalTime.of(8, 0),
                LocalTime.of(18, 0), false);
        ScheduleSegmentSnapshot morning = workSegment(41L, 101L, 1,
                480, 720);
        ScheduleSegmentSnapshot afternoon = workSegment(41L, 102L, 2,
                840, 1080);
        List<ScheduleSegmentSnapshot> work = List.of(morning, afternoon);

        assertThat(rules.effectiveSegmentWindow(schedule, work, morning,
                "OUT").closesAt())
                        .isEqualTo(LocalDateTime.of(2026, 8, 20, 13, 0));
        assertThat(rules.effectiveSegmentWindow(schedule, work, afternoon,
                "IN").opensAt())
                        .isEqualTo(LocalDateTime.of(2026, 8, 20, 13, 0));
        assertThat(rules.effectiveSegmentWindow(schedule, work, morning,
                "IN").opensAt())
                        .isEqualTo(LocalDateTime.of(2026, 8, 20, 6, 0));
        assertThat(rules.effectiveSegmentWindow(schedule, work, afternoon,
                "OUT").closesAt())
                        .isEqualTo(LocalDateTime.of(2026, 8, 20, 20, 0));
    }

    @Test
    void adjacentSegmentMidpointRetainsAbsoluteCrossDayTime()
    {
        Schedule schedule = publishedSchedule(42L, LocalTime.of(20, 0),
                LocalTime.of(4, 0), true);
        ScheduleSegmentSnapshot evening = workSegment(42L, 111L, 1,
                1200, 1380);
        ScheduleSegmentSnapshot overnight = workSegment(42L, 112L, 2,
                1500, 1680);
        List<ScheduleSegmentSnapshot> work = List.of(evening, overnight);

        assertThat(rules.effectiveSegmentWindow(schedule, work, evening,
                "OUT").closesAt())
                        .isEqualTo(LocalDateTime.of(2026, 8, 21, 0, 0));
        assertThat(rules.effectiveSegmentWindow(schedule, work, overnight,
                "IN").opensAt())
                        .isEqualTo(LocalDateTime.of(2026, 8, 21, 0, 0));
    }

    @Test
    void adjacentOddMinuteGapKeepsExactThirtySecondMidpoint()
    {
        Schedule schedule = publishedSchedule(43L, LocalTime.of(8, 0),
                LocalTime.of(18, 0), false);
        ScheduleSegmentSnapshot morning = workSegment(43L, 121L, 1,
                480, 720);
        ScheduleSegmentSnapshot afternoon = workSegment(43L, 122L, 2,
                721, 1080);
        List<ScheduleSegmentSnapshot> work = List.of(morning, afternoon);

        assertThat(rules.effectiveSegmentWindow(schedule, work, morning,
                "OUT").closesAt())
                        .isEqualTo(LocalDateTime.of(2026, 8, 20, 12, 0,
                                30));
        assertThat(rules.effectiveSegmentWindow(schedule, work, afternoon,
                "IN").opensAt())
                        .isEqualTo(LocalDateTime.of(2026, 8, 20, 12, 0,
                                30));
    }

    private Schedule publishedSchedule(Long scheduleId, LocalTime start,
            LocalTime end, boolean crossDay)
    {
        Schedule value = new Schedule();
        value.scheduleId = scheduleId;
        value.status = "PUBLISHED";
        value.businessDate = LocalDate.of(2026, 8, 20);
        value.startTimeSnapshot = start;
        value.endTimeSnapshot = end;
        value.crossDaySnapshot = crossDay;
        value.checkInOpenMinutesSnapshot = 120;
        value.checkInCloseMinutesSnapshot = 120;
        value.checkOutOpenMinutesSnapshot = 120;
        value.checkOutCloseMinutesSnapshot = 120;
        return value;
    }

    private ScheduleSegmentSnapshot workSegment(Long scheduleId, Long id,
            int order, int start, int end)
    {
        ScheduleSegmentSnapshot value = new ScheduleSegmentSnapshot();
        value.scheduleSegmentSnapshotId = id;
        value.scheduleId = scheduleId;
        value.segmentOrder = order;
        value.segmentType = "WORK";
        value.startMinuteOffset = start;
        value.endMinuteOffset = end;
        return value;
    }
}
