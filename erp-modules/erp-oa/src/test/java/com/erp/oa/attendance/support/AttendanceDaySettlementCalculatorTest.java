package com.erp.oa.attendance.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.erp.oa.attendance.domain.AttendanceModels.DayResult;
import com.erp.oa.attendance.domain.AttendanceModels.PunchEvent;
import com.erp.oa.attendance.domain.AttendanceModels.Schedule;
import com.erp.oa.attendance.domain.AttendanceModels.ScheduleSegmentSnapshot;
import com.erp.oa.attendance.domain.AttendanceSettlementModels.CorrectionSource;
import com.erp.oa.attendance.domain.AttendanceSettlementModels.LeaveRequestState;
import com.erp.oa.attendance.domain.AttendanceSettlementModels.LeaveSegmentSource;
import com.erp.oa.attendance.domain.AttendanceSettlementModels.RemainingWorkConfirmationSource;
import com.erp.oa.attendance.support.AttendanceDaySettlementCalculator.Evaluation;

class AttendanceDaySettlementCalculatorTest
{
    private final AttendanceDaySettlementCalculator calculator =
            new AttendanceDaySettlementCalculator(new AttendanceRuleEngine());

    @Test
    void crossNightUsesBusinessDateOffsets()
    {
        Schedule schedule = schedule(LocalTime.of(20, 0),
                LocalTime.of(4, 0), true, 480);
        List<ScheduleSegmentSnapshot> segments = List.of(segment(schedule, 1,
                "WORK", 1200, 1680, true));

        Evaluation value = calculator.evaluate(schedule,
                punch(schedule, 1L, "IN", at(20, 0)),
                punch(schedule, 2L, "OUT", nextDay(4, 0)), List.of(),
                List.of(), List.of(), segments, nextDay(8, 0));

        assertThat(value.ready()).isTrue();
        assertThat(value.result().workedMinutes).isEqualTo(480);
        assertThat(value.result().resultStatus).isEqualTo("NORMAL");
    }

    @Test
    void partialPaidLeaveOnlyCountsPaidWorkSegmentsAndClearsLateTime()
    {
        Schedule schedule = daytime();
        LeaveSegmentSource leave = new LeaveSegmentSource();
        leave.leaveSegmentId = 10L;
        leave.leaveRequestId = 20L;
        leave.startTime = at(9, 0);
        leave.endTime = at(13, 0);
        leave.totalMinutes = 240;
        leave.paidMinutes = 240;
        leave.unpaidMinutes = 0;

        Evaluation value = calculator.evaluate(schedule,
                punch(schedule, 1L, "IN", at(13, 0)),
                punch(schedule, 2L, "OUT", at(18, 0)), List.of(),
                List.of(), List.of(leave), daySegments(schedule),
                at(20, 0));

        DayResult result = value.result();
        assertThat(value.ready()).isTrue();
        assertThat(result.paidLeaveMinutes).isEqualTo(180);
        assertThat(result.workedMinutes).isEqualTo(300);
        assertThat(result.lateMinutes).isZero();
        assertThat(result.absenceMinutes).isZero();
        assertThat(result.resultStatus).isEqualTo("LEAVE_PARTIAL");
    }

    @Test
    void fullApprovedLeaveSettlesWithoutFabricatedPunches()
    {
        Schedule schedule = daytime();
        LeaveSegmentSource leave = new LeaveSegmentSource();
        leave.leaveSegmentId = 11L;
        leave.leaveRequestId = 21L;
        leave.startTime = at(9, 0);
        leave.endTime = at(18, 0);
        leave.totalMinutes = 540;
        leave.paidMinutes = 540;
        leave.unpaidMinutes = 0;

        Evaluation value = calculator.evaluate(schedule, null, null,
                List.of(), List.of(), List.of(leave), daySegments(schedule),
                at(20, 0));

        assertThat(value.ready()).isTrue();
        assertThat(value.issueCodes()).doesNotContain("MISSING_IN",
                "MISSING_OUT");
        assertThat(value.result().paidLeaveMinutes).isEqualTo(480);
        assertThat(value.result().workedMinutes).isZero();
        assertThat(value.result().absenceMinutes).isZero();
        assertThat(value.result().resultStatus).isEqualTo("LEAVE_FULL");
    }

    @Test
    void approvedInCorrectionIsTheOnlyRecordedInSource()
    {
        Schedule schedule = daytime();
        CorrectionSource correction = correction(schedule, 31L, "IN",
                at(9, 0), "APPROVED");

        Evaluation value = calculator.evaluate(schedule, null,
                punch(schedule, 2L, "OUT", at(18, 0)),
                List.of(correction), List.of(), List.of(),
                daySegments(schedule), at(20, 0));

        assertThat(value.ready()).isTrue();
        assertThat(value.result().firstInCorrectionRequestId).isEqualTo(31L);
        assertThat(value.result().firstInEventId).isNull();
        assertThat(value.result().lastOutEventId).isEqualTo(2L);
    }

    @Test
    void approvedOutCorrectionIsTheOnlyRecordedOutSource()
    {
        Schedule schedule = daytime();
        CorrectionSource correction = correction(schedule, 32L, "OUT",
                at(18, 0), "APPROVED");

        Evaluation value = calculator.evaluate(schedule,
                punch(schedule, 1L, "IN", at(9, 0)), null,
                List.of(correction), List.of(), List.of(),
                daySegments(schedule), at(20, 0));

        assertThat(value.ready()).isTrue();
        assertThat(value.result().lastOutCorrectionRequestId).isEqualTo(32L);
        assertThat(value.result().lastOutEventId).isNull();
        assertThat(value.result().firstInEventId).isEqualTo(1L);
    }

    @Test
    void boundaryApprovedOtherFailsClosedAndCannotBecomePunchSource()
    {
        Schedule schedule = daytime();
        CorrectionSource correction = correction(schedule, 34L, "OUT",
                at(18, 0), "APPROVED");
        correction.correctionType = "OTHER";

        Evaluation value = calculator.evaluate(schedule,
                punch(schedule, 1L, "IN", at(9, 0)), null,
                List.of(correction), List.of(), List.of(),
                daySegments(schedule), at(20, 0));

        assertThat(value.ready()).isFalse();
        assertThat(value.issueCodes()).contains(
                "INVALID_APPROVED_CORRECTION_TYPE", "MISSING_OUT");
        assertThat(value.result().lastOutCorrectionRequestId).isNull();
        assertThat(value.result().calculationVersion).isEqualTo(4);
    }

    @Test
    void pendingLeaveAndCorrectionBlockSettlement()
    {
        Schedule schedule = daytime();
        CorrectionSource correction = correction(schedule, 33L, "IN",
                at(9, 0), "PENDING");
        LeaveRequestState leave = new LeaveRequestState();
        leave.leaveRequestId = 44L;
        leave.status = "SUBMITTING";

        Evaluation value = calculator.evaluate(schedule,
                punch(schedule, 1L, "IN", at(9, 0)),
                punch(schedule, 2L, "OUT", at(18, 0)),
                List.of(correction), List.of(leave), List.of(),
                daySegments(schedule), at(20, 0));

        assertThat(value.ready()).isFalse();
        assertThat(value.issueCodes()).contains("PENDING_CORRECTION",
                "PENDING_LEAVE");
        assertThat(value.result().settledAt).isNull();
    }

    @Test
    void missingPublishedSegmentSnapshotFailsClosed()
    {
        Schedule schedule = daytime();
        Evaluation value = calculator.evaluate(schedule,
                punch(schedule, 1L, "IN", at(9, 0)),
                punch(schedule, 2L, "OUT", at(18, 0)), List.of(),
                List.of(), List.of(), List.of(), at(20, 0));

        assertThat(value.ready()).isFalse();
        assertThat(value.issueCodes())
                .contains("SCHEDULE_SEGMENT_SNAPSHOT_MISSING");
    }

    @Test
    void overtimeIsPreservedAndBreakSnapshotIsNotWorkedTime()
    {
        Schedule schedule = daytime();
        Evaluation value = calculator.evaluate(schedule,
                punch(schedule, 1L, "IN", at(9, 0)),
                punch(schedule, 2L, "OUT", at(20, 0)), List.of(),
                List.of(), List.of(), daySegments(schedule),
                at(21, 0));

        assertThat(value.ready()).isTrue();
        assertThat(value.result().scheduledMinutes).isEqualTo(480);
        assertThat(value.result().workedMinutes).isEqualTo(600);
        assertThat(value.result().absenceMinutes).isZero();
    }

    @Test
    void overtimeDoesNotEraseMissingScheduledWork()
    {
        Schedule schedule = daytime();
        Evaluation value = calculator.evaluate(schedule,
                punch(schedule, 1L, "IN", at(10, 0)),
                punch(schedule, 2L, "OUT", at(19, 0)), List.of(),
                List.of(), List.of(), daySegments(schedule),
                at(20, 0));

        assertThat(value.ready()).isTrue();
        assertThat(value.result().workedMinutes).isEqualTo(480);
        assertThat(value.result().lateMinutes).isEqualTo(60);
        assertThat(value.result().absenceMinutes).isEqualTo(60);
    }

    @Test
    void perWorkSegmentPairsEveryWorkPeriodAndNeverCountsBreak()
    {
        Schedule schedule = segmentedDaytime();
        List<ScheduleSegmentSnapshot> segments = daySegments(schedule);

        Evaluation value = calculator.evaluate(schedule, List.of(
                segmentPunch(schedule, 1L, "IN", at(9, 0), segments.get(0)),
                segmentPunch(schedule, 2L, "OUT", at(12, 0),
                        segments.get(0)),
                segmentPunch(schedule, 3L, "IN", at(13, 0),
                        segments.get(2)),
                segmentPunch(schedule, 4L, "OUT", at(18, 0),
                        segments.get(2))),
                List.of(), List.of(), List.of(), segments, at(20, 0));

        assertThat(value.ready()).isTrue();
        assertThat(value.result().workedMinutes).isEqualTo(480);
        assertThat(value.result().absenceMinutes).isZero();
        assertThat(value.result().firstInEventId).isEqualTo(1L);
        assertThat(value.result().lastOutEventId).isEqualTo(4L);
        assertThat(value.result().resultStatus).isEqualTo("NORMAL");
    }

    @Test
    void perWorkSegmentMissingCardMakesOnlyThatWorkPeriodAbsent()
    {
        Schedule schedule = segmentedDaytime();
        List<ScheduleSegmentSnapshot> segments = daySegments(schedule);

        Evaluation value = calculator.evaluate(schedule, List.of(
                segmentPunch(schedule, 1L, "IN", at(9, 0), segments.get(0)),
                segmentPunch(schedule, 2L, "OUT", at(12, 0),
                        segments.get(0)),
                segmentPunch(schedule, 4L, "OUT", at(18, 0),
                        segments.get(2))),
                List.of(), List.of(), List.of(), segments, at(20, 0));

        assertThat(value.ready()).isFalse();
        assertThat(value.issueCodes()).contains("MISSING_IN")
                .doesNotContain("MISSING_OUT");
        assertThat(value.result().workedMinutes).isEqualTo(180);
        assertThat(value.result().absenceMinutes).isEqualTo(300);
        assertThat(value.result().resultStatus).isEqualTo("MISSED_IN");
    }

    @Test
    void approvedLeaveCoveringAWholeWorkPeriodWaivesItsTwoCards()
    {
        Schedule schedule = segmentedDaytime();
        List<ScheduleSegmentSnapshot> segments = daySegments(schedule);
        LeaveSegmentSource leave = new LeaveSegmentSource();
        leave.leaveSegmentId = 91L;
        leave.leaveRequestId = 92L;
        leave.startTime = at(13, 0);
        leave.endTime = at(18, 0);
        leave.totalMinutes = 300;
        leave.paidMinutes = 300;
        leave.unpaidMinutes = 0;

        Evaluation value = calculator.evaluate(schedule, List.of(
                segmentPunch(schedule, 1L, "IN", at(9, 0), segments.get(0)),
                segmentPunch(schedule, 2L, "OUT", at(12, 0),
                        segments.get(0))),
                List.of(), List.of(), List.of(leave), segments, at(20, 0));

        assertThat(value.ready()).isTrue();
        assertThat(value.issueCodes()).doesNotContain("MISSING_IN",
                "MISSING_OUT");
        assertThat(value.result().paidLeaveMinutes).isEqualTo(300);
        assertThat(value.result().workedMinutes).isEqualTo(180);
        assertThat(value.result().absenceMinutes).isZero();
        assertThat(value.result().resultStatus).isEqualTo("LEAVE_PARTIAL");
    }

    @Test
    void perWorkSegmentAddsLateAndEarlyMinutesForEachWorkPeriod()
    {
        Schedule schedule = segmentedDaytime();
        List<ScheduleSegmentSnapshot> segments = daySegments(schedule);

        Evaluation value = calculator.evaluate(schedule, List.of(
                segmentPunch(schedule, 1L, "IN", at(9, 15), segments.get(0)),
                segmentPunch(schedule, 2L, "OUT", at(11, 45),
                        segments.get(0)),
                segmentPunch(schedule, 3L, "IN", at(13, 30),
                        segments.get(2)),
                segmentPunch(schedule, 4L, "OUT", at(17, 30),
                        segments.get(2))),
                List.of(), List.of(), List.of(), segments, at(20, 0));

        assertThat(value.ready()).isTrue();
        assertThat(value.result().lateMinutes).isEqualTo(45);
        assertThat(value.result().earlyLeaveMinutes).isEqualTo(45);
        assertThat(value.result().workedMinutes).isEqualTo(390);
        assertThat(value.result().absenceMinutes).isEqualTo(90);
        assertThat(value.result().resultStatus).isEqualTo("LATE_EARLY");
    }

    @Test
    void perWorkSegmentPendingLeaveStillBlocksFinalSettlement()
    {
        Schedule schedule = segmentedDaytime();
        List<ScheduleSegmentSnapshot> segments = daySegments(schedule);
        LeaveRequestState pending = new LeaveRequestState();
        pending.leaveRequestId = 93L;
        pending.status = "PENDING";

        Evaluation value = calculator.evaluate(schedule, List.of(
                segmentPunch(schedule, 1L, "IN", at(9, 0), segments.get(0)),
                segmentPunch(schedule, 2L, "OUT", at(12, 0),
                        segments.get(0)),
                segmentPunch(schedule, 3L, "IN", at(13, 0),
                        segments.get(2)),
                segmentPunch(schedule, 4L, "OUT", at(18, 0),
                        segments.get(2))),
                List.of(), List.of(pending), List.of(), segments, at(20, 0));

        assertThat(value.ready()).isFalse();
        assertThat(value.issueCodes()).contains("PENDING_LEAVE");
        assertThat(value.result().settledAt).isNull();
    }

    @Test
    void segmentApprovedUnknownTypeFailsClosedAndCannotBecomePunchSource()
    {
        Schedule schedule = segmentedDaytime();
        List<ScheduleSegmentSnapshot> segments = daySegments(schedule);
        CorrectionSource correction = correction(schedule, 35L, "OUT",
                at(18, 0), "APPROVED");
        correction.correctionType = "FUTURE_UNKNOWN_TYPE";
        correction.targetScheduleSegmentSnapshotId =
                segments.get(2).scheduleSegmentSnapshotId;
        correction.targetPunchSlotKey = "SEGMENT:"
                + segments.get(2).scheduleSegmentSnapshotId + ":OUT";

        Evaluation value = calculator.evaluate(schedule, List.of(
                segmentPunch(schedule, 1L, "IN", at(9, 0), segments.get(0)),
                segmentPunch(schedule, 2L, "OUT", at(12, 0),
                        segments.get(0)),
                segmentPunch(schedule, 3L, "IN", at(13, 0),
                        segments.get(2))),
                List.of(correction), List.of(), List.of(), segments,
                at(20, 0));

        assertThat(value.ready()).isFalse();
        assertThat(value.issueCodes()).contains(
                "INVALID_APPROVED_CORRECTION_TYPE", "MISSING_OUT");
        assertThat(value.result().lastOutCorrectionRequestId).isNull();
        assertThat(value.result().lastOutEventId).isEqualTo(2L);
        assertThat(value.result().calculationVersion).isEqualTo(4);
    }

    @Test
    void approvedWrongTypeMovesOnlyNamedPunchIntoNewSegmentSlot()
    {
        Schedule schedule = segmentedDaytime();
        List<ScheduleSegmentSnapshot> segments = daySegments(schedule);
        CorrectionSource correction = wrongTypeCorrection(schedule, 61L, 2L,
                "OUT", at(12, 0), segments.get(0));

        Evaluation value = calculator.evaluate(schedule, List.of(
                segmentPunch(schedule, 1L, "IN", at(9, 0), segments.get(0)),
                segmentPunch(schedule, 2L, "IN", at(12, 0), segments.get(0)),
                segmentPunch(schedule, 3L, "IN", at(13, 0), segments.get(2)),
                segmentPunch(schedule, 4L, "OUT", at(18, 0), segments.get(2))),
                List.of(correction), List.of(), List.of(), segments,
                at(20, 0));

        assertThat(value.ready()).isTrue();
        assertThat(value.issueCodes()).doesNotContain(
                "MULTIPLE_ACCEPTED_PUNCH_IN", "MISSING_OUT");
        assertThat(value.result().workedMinutes).isEqualTo(480);
        assertThat(value.result().firstInEventId).isEqualTo(1L);
        assertThat(value.result().lastOutEventId).isEqualTo(4L);
        assertThat(value.result().resultStatus).isEqualTo("NORMAL");
    }

    @Test
    void boundaryWrongTypeKeepsUnrelatedPunchAndUsesCorrectionAsNewType()
    {
        Schedule schedule = daytime();
        schedule.punchModeSnapshot = "SHIFT_BOUNDARY";
        CorrectionSource correction = wrongTypeCorrection(schedule, 62L, 1L,
                "OUT", at(18, 0), null);

        Evaluation value = calculator.evaluate(schedule, List.of(
                punch(schedule, 1L, "IN", at(8, 0)),
                punch(schedule, 2L, "IN", at(9, 0))),
                List.of(correction), List.of(), List.of(),
                daySegments(schedule), at(20, 0));

        assertThat(value.ready()).isTrue();
        assertThat(value.result().firstInEventId).isEqualTo(2L);
        assertThat(value.result().lastOutEventId).isNull();
        assertThat(value.result().lastOutCorrectionRequestId).isEqualTo(62L);
        assertThat(value.result().workedMinutes).isEqualTo(480);
        assertThat(value.result().resultStatus).isEqualTo("NORMAL");
    }

    @Test
    void segmentWrongTypeFailsClosedWhenTargetSlotIsOccupied()
    {
        Schedule schedule = segmentedDaytime();
        List<ScheduleSegmentSnapshot> segments = daySegments(schedule);
        CorrectionSource correction = wrongTypeCorrection(schedule, 65L, 1L,
                "OUT", at(11, 30), segments.get(0));

        Evaluation value = calculator.evaluate(schedule, List.of(
                segmentPunch(schedule, 1L, "IN", at(9, 0), segments.get(0)),
                segmentPunch(schedule, 2L, "OUT", at(12, 0), segments.get(0)),
                segmentPunch(schedule, 3L, "IN", at(13, 0), segments.get(2)),
                segmentPunch(schedule, 4L, "OUT", at(18, 0), segments.get(2))),
                List.of(correction), List.of(), List.of(), segments,
                at(20, 0));

        assertThat(value.ready()).isFalse();
        assertThat(value.issueCodes()).contains(
                "WRONG_TYPE_TARGET_ALREADY_OCCUPIED",
                "INVALID_APPROVED_WRONG_TYPE_CORRECTION");
        assertThat(value.result().firstInEventId).isEqualTo(1L);
        assertThat(value.result().workedMinutes).isEqualTo(480);
        assertThat(value.result().earlyLeaveMinutes).isZero();
    }

    @Test
    void boundaryWrongTypeFailsClosedWhenTargetTypeIsOccupied()
    {
        Schedule schedule = daytime();
        schedule.punchModeSnapshot = "SHIFT_BOUNDARY";
        CorrectionSource correction = wrongTypeCorrection(schedule, 66L, 1L,
                "OUT", at(17, 0), null);

        Evaluation value = calculator.evaluate(schedule, List.of(
                punch(schedule, 1L, "IN", at(9, 0)),
                punch(schedule, 2L, "OUT", at(18, 0))),
                List.of(correction), List.of(), List.of(),
                daySegments(schedule), at(20, 0));

        assertThat(value.ready()).isFalse();
        assertThat(value.issueCodes()).contains(
                "WRONG_TYPE_TARGET_ALREADY_OCCUPIED",
                "INVALID_APPROVED_WRONG_TYPE_CORRECTION");
        assertThat(value.result().firstInEventId).isEqualTo(1L);
        assertThat(value.result().lastOutEventId).isEqualTo(2L);
        assertThat(value.result().lastOutCorrectionRequestId).isNull();
        assertThat(value.result().workedMinutes).isEqualTo(480);
        assertThat(value.result().earlyLeaveMinutes).isZero();
    }

    @Test
    void wrongTypeOutsideWindowKeepsOriginalAndRejectsCorrectionSource()
    {
        Schedule schedule = daytime();
        schedule.punchModeSnapshot = "SHIFT_BOUNDARY";
        CorrectionSource correction = wrongTypeCorrection(schedule, 67L, 1L,
                "OUT", at(13, 0), null);

        Evaluation value = calculator.evaluate(schedule,
                List.of(punch(schedule, 1L, "IN", at(9, 0))),
                List.of(correction), List.of(), List.of(),
                daySegments(schedule), at(20, 0));

        assertThat(value.ready()).isFalse();
        assertThat(value.issueCodes()).contains(
                "APPROVED_CORRECTION_OUTSIDE_WINDOW_OUT",
                "INVALID_APPROVED_WRONG_TYPE_CORRECTION", "MISSING_OUT");
        assertThat(value.result().firstInEventId).isEqualTo(1L);
        assertThat(value.result().lastOutCorrectionRequestId).isNull();
    }

    @Test
    void wrongTypeOriginalFromAnotherUserFailsClosed()
    {
        Schedule schedule = segmentedDaytime();
        List<ScheduleSegmentSnapshot> segments = daySegments(schedule);
        PunchEvent foreignOriginal = segmentPunch(schedule, 9L, "IN",
                at(11, 50), segments.get(0));
        foreignOriginal.userId = 999L;
        CorrectionSource correction = wrongTypeCorrection(schedule, 63L, 9L,
                "OUT", at(12, 0), segments.get(0));

        Evaluation value = calculator.evaluate(schedule, List.of(
                segmentPunch(schedule, 1L, "IN", at(9, 0), segments.get(0)),
                foreignOriginal,
                segmentPunch(schedule, 2L, "OUT", at(12, 0), segments.get(0)),
                segmentPunch(schedule, 3L, "IN", at(13, 0), segments.get(2)),
                segmentPunch(schedule, 4L, "OUT", at(18, 0), segments.get(2))),
                List.of(correction), List.of(), List.of(), segments,
                at(20, 0));

        assertThat(value.ready()).isFalse();
        assertThat(value.issueCodes()).contains(
                "INVALID_APPROVED_WRONG_TYPE_CORRECTION",
                "INVALID_ACCEPTED_PUNCH_IN");
        assertThat(value.result().resultStatus).isEqualTo("EXCEPTION");
    }

    @Test
    void wrongTypeCannotReplaceAnEventWithItsExistingType()
    {
        Schedule schedule = segmentedDaytime();
        List<ScheduleSegmentSnapshot> segments = daySegments(schedule);
        CorrectionSource correction = wrongTypeCorrection(schedule, 64L, 2L,
                "OUT", at(12, 0), segments.get(0));

        Evaluation value = calculator.evaluate(schedule, List.of(
                segmentPunch(schedule, 1L, "IN", at(9, 0), segments.get(0)),
                segmentPunch(schedule, 2L, "OUT", at(12, 0), segments.get(0)),
                segmentPunch(schedule, 3L, "IN", at(13, 0), segments.get(2)),
                segmentPunch(schedule, 4L, "OUT", at(18, 0), segments.get(2))),
                List.of(correction), List.of(), List.of(), segments,
                at(20, 0));

        assertThat(value.ready()).isFalse();
        assertThat(value.issueCodes()).containsExactly(
                "INVALID_APPROVED_WRONG_TYPE_CORRECTION");
        assertThat(value.result().workedMinutes).isEqualTo(480);
        assertThat(value.result().resultStatus).isEqualTo("EXCEPTION");
    }

    @Test
    void boundaryModeListOverloadKeepsFirstInAndLastOutBehavior()
    {
        Schedule schedule = daytime();
        schedule.punchModeSnapshot = "SHIFT_BOUNDARY";

        Evaluation value = calculator.evaluate(schedule, List.of(
                punch(schedule, 1L, "IN", at(9, 0)),
                punch(schedule, 2L, "IN", at(9, 30)),
                punch(schedule, 3L, "OUT", at(17, 30)),
                punch(schedule, 4L, "OUT", at(18, 0))),
                List.of(), List.of(), List.of(), daySegments(schedule),
                at(20, 0));

        assertThat(value.ready()).isTrue();
        assertThat(value.result().firstInEventId).isEqualTo(1L);
        assertThat(value.result().lastOutEventId).isEqualTo(4L);
        assertThat(value.result().workedMinutes).isEqualTo(480);
        assertThat(value.result().resultStatus).isEqualTo("NORMAL");
        assertThat(value.result().calculationVersion).isEqualTo(4);
    }

    @Test
    void approvedLeaveWaivesOnlyTheCoveredFacesOfAdjacentWorkSegments()
    {
        Schedule schedule = segmentedDaytime();
        List<ScheduleSegmentSnapshot> segments = daySegments(schedule);
        LeaveSegmentSource leave = leave(201L, at(10, 0), at(16, 0), 360);

        Evaluation value = calculator.evaluate(schedule, List.of(
                segmentPunch(schedule, 1L, "IN", at(9, 0), segments.get(0)),
                segmentPunch(schedule, 4L, "OUT", at(18, 0),
                        segments.get(2))),
                List.of(), List.of(), List.of(leave), segments, at(20, 0));

        assertThat(value.ready()).isTrue();
        assertThat(value.issueCodes()).doesNotContain("MISSING_IN",
                "MISSING_OUT");
        assertThat(value.result().workedMinutes).isEqualTo(180);
        assertThat(value.result().paidLeaveMinutes).isEqualTo(300);
        assertThat(value.result().absenceMinutes).isZero();
    }

    @Test
    void workIslandBlocksUntilAnExactRemainingIntervalIsConfirmed()
    {
        Schedule schedule = segmentedDaytime();
        List<ScheduleSegmentSnapshot> segments = daySegments(schedule);
        List<LeaveSegmentSource> leaves = List.of(
                leave(211L, at(9, 0), at(10, 0), 60),
                leave(212L, at(11, 0), at(12, 0), 60));
        List<PunchEvent> afternoon = List.of(
                segmentPunch(schedule, 3L, "IN", at(13, 0),
                        segments.get(2)),
                segmentPunch(schedule, 4L, "OUT", at(18, 0),
                        segments.get(2)));

        Evaluation blocked = calculator.evaluate(schedule, afternoon,
                List.of(), List.of(), leaves, segments, List.of(), at(20, 0));
        assertThat(blocked.ready()).isFalse();
        assertThat(blocked.issueCodes())
                .contains("REMAINING_WORK_CONFIRMATION_REQUIRED")
                .doesNotContain("MISSING_IN", "MISSING_OUT");

        RemainingWorkConfirmationSource confirmation = confirmation(schedule,
                301L, at(10, 0), at(11, 0), "ATTENDED",
                at(10, 5), at(10, 55));
        Evaluation confirmed = calculator.evaluate(schedule, afternoon,
                List.of(), List.of(), leaves, segments,
                List.of(confirmation), at(20, 0));

        assertThat(confirmed.ready()).isTrue();
        assertThat(confirmed.result().workedMinutes).isEqualTo(350);
        assertThat(confirmed.result().paidLeaveMinutes).isEqualTo(120);
        assertThat(confirmed.result().absenceMinutes).isEqualTo(10);
    }

    @Test
    void boundaryModeUsesRemainingWorkConfirmationsWithoutAddingPunchSlots()
    {
        Schedule schedule = daytime();
        schedule.punchModeSnapshot = "SHIFT_BOUNDARY";
        List<ScheduleSegmentSnapshot> segments = daySegments(schedule);
        List<LeaveSegmentSource> leaves = List.of(
                leave(221L, at(9, 0), at(10, 0), 60),
                leave(222L, at(17, 0), at(18, 0), 60));
        List<RemainingWorkConfirmationSource> confirmations = List.of(
                confirmation(schedule, 311L, at(10, 0), at(12, 0),
                        "ATTENDED", at(10, 0), at(12, 0)),
                confirmation(schedule, 312L, at(13, 0), at(17, 0),
                        "ATTENDED", at(13, 0), at(17, 0)));

        Evaluation value = calculator.evaluate(schedule, List.of(),
                List.of(), List.of(), leaves, segments, confirmations,
                at(20, 0));

        assertThat(value.ready()).isTrue();
        assertThat(value.result().firstInEventId).isNull();
        assertThat(value.result().lastOutEventId).isNull();
        assertThat(value.result().workedMinutes).isEqualTo(360);
        assertThat(value.result().paidLeaveMinutes).isEqualTo(120);
        assertThat(value.result().absenceMinutes).isZero();
    }

    private Schedule daytime()
    { return schedule(LocalTime.of(9, 0), LocalTime.of(18, 0), false, 480); }

    private Schedule segmentedDaytime()
    {
        Schedule value = daytime();
        value.punchModeSnapshot = "PER_WORK_SEGMENT";
        return value;
    }

    private Schedule schedule(LocalTime start, LocalTime end,
            boolean crossDay, int standardMinutes)
    {
        Schedule value = new Schedule();
        value.scheduleId = 100L;
        value.userId = 7L;
        value.userName = "张三";
        value.shopId = 101L;
        value.businessDate = LocalDate.of(2026, 8, 20);
        value.shiftId = 5L;
        value.status = "PUBLISHED";
        value.startTimeSnapshot = start;
        value.endTimeSnapshot = end;
        value.crossDaySnapshot = crossDay;
        value.standardMinutesSnapshot = standardMinutes;
        value.graceInMinutesSnapshot = 0;
        value.graceOutMinutesSnapshot = 0;
        value.checkInOpenMinutesSnapshot = 120;
        value.checkInCloseMinutesSnapshot = 240;
        value.checkOutOpenMinutesSnapshot = 240;
        value.checkOutCloseMinutesSnapshot = 60;
        return value;
    }

    private List<ScheduleSegmentSnapshot> daySegments(Schedule schedule)
    {
        return List.of(
                segment(schedule, 1, "WORK", 540, 720, true),
                segment(schedule, 2, "BREAK", 720, 780, false),
                segment(schedule, 3, "WORK", 780, 1080, true));
    }

    private ScheduleSegmentSnapshot segment(Schedule schedule, int order,
            String type, int start, int end, boolean paid)
    {
        ScheduleSegmentSnapshot value = new ScheduleSegmentSnapshot();
        value.scheduleSegmentSnapshotId = 1000L + order;
        value.scheduleId = schedule.scheduleId;
        value.segmentOrder = order;
        value.segmentType = type;
        value.startMinuteOffset = start;
        value.endMinuteOffset = end;
        value.paid = paid;
        return value;
    }

    private PunchEvent segmentPunch(Schedule schedule, Long id, String type,
            LocalDateTime time, ScheduleSegmentSnapshot segment)
    {
        PunchEvent value = punch(schedule, id, type, time);
        value.scheduleSegmentSnapshotId =
                segment.scheduleSegmentSnapshotId;
        value.punchSlotKey = "SEGMENT:"
                + segment.scheduleSegmentSnapshotId + ":" + type;
        return value;
    }

    private PunchEvent punch(Schedule schedule, Long id, String type,
            LocalDateTime time)
    {
        PunchEvent value = new PunchEvent();
        value.punchEventId = id;
        value.scheduleId = schedule.scheduleId;
        value.userId = schedule.userId;
        value.shopId = schedule.shopId;
        value.businessDate = schedule.businessDate;
        value.punchType = type;
        value.verificationStatus = "ACCEPTED";
        value.serverPunchTime = time;
        return value;
    }

    private CorrectionSource correction(Schedule schedule, Long id,
            String type, LocalDateTime time, String status)
    {
        CorrectionSource value = new CorrectionSource();
        value.correctionRequestId = id;
        value.scheduleId = schedule.scheduleId;
        value.userId = schedule.userId;
        value.shopId = schedule.shopId;
        value.businessDate = schedule.businessDate;
        value.correctionType = "MISSING_PUNCH";
        value.targetPunchType = type;
        value.requestedPunchTime = time;
        value.status = status;
        return value;
    }

    private CorrectionSource wrongTypeCorrection(Schedule schedule, Long id,
            Long originalEventId, String targetType, LocalDateTime time,
            ScheduleSegmentSnapshot segment)
    {
        CorrectionSource value = correction(schedule, id, targetType, time,
                "APPROVED");
        value.correctionType = "WRONG_TYPE";
        value.originalPunchEventId = originalEventId;
        if (segment != null)
        {
            value.targetScheduleSegmentSnapshotId =
                    segment.scheduleSegmentSnapshotId;
            value.targetPunchSlotKey = "SEGMENT:"
                    + segment.scheduleSegmentSnapshotId + ":" + targetType;
        }
        return value;
    }

    private LeaveSegmentSource leave(Long id, LocalDateTime start,
            LocalDateTime end, int paidMinutes)
    {
        LeaveSegmentSource value = new LeaveSegmentSource();
        value.leaveSegmentId = id;
        value.leaveRequestId = id + 1000;
        value.startTime = start;
        value.endTime = end;
        value.totalMinutes = Math.toIntExact(
                java.time.Duration.between(start, end).toMinutes());
        value.paidMinutes = paidMinutes;
        value.unpaidMinutes = Math.max(0,
                value.totalMinutes - paidMinutes);
        return value;
    }

    private RemainingWorkConfirmationSource confirmation(Schedule schedule,
            Long id, LocalDateTime start, LocalDateTime end, String decision,
            LocalDateTime arrival, LocalDateTime departure)
    {
        RemainingWorkConfirmationSource value =
                new RemainingWorkConfirmationSource();
        value.confirmationId = id;
        value.scheduleId = schedule.scheduleId;
        value.userId = schedule.userId;
        value.shopId = schedule.shopId;
        value.businessDate = schedule.businessDate;
        value.remainingStart = start;
        value.remainingEnd = end;
        value.decision = decision;
        value.actualArrivalTime = arrival;
        value.actualDepartureTime = departure;
        return value;
    }

    private LocalDateTime at(int hour, int minute)
    { return LocalDateTime.of(2026, 8, 20, hour, minute); }
    private LocalDateTime nextDay(int hour, int minute)
    { return LocalDateTime.of(2026, 8, 21, hour, minute); }
}
