package com.erp.oa.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.oa.attendance.domain.AttendanceModels.RemainingWorkConfirmation;
import com.erp.oa.attendance.domain.AttendanceModels.Schedule;
import com.erp.oa.attendance.domain.AttendanceModels.ScheduleSegmentSnapshot;
import com.erp.oa.attendance.domain.AttendanceSettlementModels.LeaveSegmentSource;
import com.erp.oa.attendance.dto.AttendanceRemainingWorkRequests.Confirm;
import com.erp.oa.attendance.mapper.AttendanceV2Mapper;
import com.erp.oa.attendance.support.AttendanceDaySettlementCalculator;
import com.erp.oa.attendance.support.AttendanceRuleEngine;
import com.erp.oa.service.BusinessFeatureGate;

class AttendanceRemainingWorkServiceTest
{
    private static final LocalDate DAY = LocalDate.of(2026, 9, 9);
    private AttendanceV2Mapper mapper;
    private AttendanceRemainingWorkService service;
    private RemainingWorkConfirmation previous;
    private AtomicReference<RemainingWorkConfirmation> inserted;

    @BeforeEach
    void setUp()
    {
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("manager");
        mapper = mock(AttendanceV2Mapper.class);
        ShopScopeService scope = mock(ShopScopeService.class);
        when(scope.resolveRequiredShopDept(101L)).thenReturn(101L);
        AttendanceRuleEngine rules = new AttendanceRuleEngine();
        service = new AttendanceRemainingWorkService(mapper,
                new AttendanceDaySettlementCalculator(rules), rules,
                mock(AttendanceRemainingWorkAttachmentStorage.class), scope,
                mock(BusinessFeatureGate.class));
        Schedule schedule = new Schedule();
        schedule.scheduleId = 31L; schedule.userId = 7L;
        schedule.userName = "employee"; schedule.shopId = 101L;
        schedule.businessDate = DAY; schedule.status = "PUBLISHED";
        schedule.punchModeSnapshot = "SHIFT_BOUNDARY";
        schedule.startTimeSnapshot = LocalTime.of(9, 0);
        schedule.endTimeSnapshot = LocalTime.of(18, 0);
        ScheduleSegmentSnapshot work = new ScheduleSegmentSnapshot();
        work.scheduleId = 31L; work.scheduleSegmentSnapshotId = 91L;
        work.segmentOrder = 1; work.segmentType = "WORK"; work.paid = true;
        work.startMinuteOffset = 540; work.endMinuteOffset = 1080;
        when(mapper.selectScheduleByIdForUpdate(31L)).thenReturn(schedule);
        when(mapper.selectScheduleSegmentSnapshots(31L, true)).thenReturn(List.of(work));
        LeaveSegmentSource start = new LeaveSegmentSource();
        start.startTime = DAY.atTime(9, 0); start.endTime = DAY.atTime(10, 0);
        LeaveSegmentSource end = new LeaveSegmentSource();
        end.startTime = DAY.atTime(17, 0); end.endTime = DAY.atTime(18, 0);
        when(mapper.selectApprovedLeaveSegments(7L, 101L,
                start.startTime, end.endTime, true)).thenReturn(List.of(start, end));
        when(mapper.selectDatabaseNow()).thenReturn(DAY.atTime(21, 0));
        previous = new RemainingWorkConfirmation();
        previous.confirmationId = 801L; previous.decision = "ATTENDED";
        previous.actualArrivalTime = DAY.atTime(10, 0);
        previous.actualDepartureTime = DAY.atTime(17, 0);
        when(mapper.selectLatestRemainingWorkConfirmation(31L,
                DAY.atTime(10, 0), DAY.atTime(17, 0), true)).thenReturn(previous);
        inserted = new AtomicReference<>();
        when(mapper.insertRemainingWorkConfirmation(any())).thenAnswer(invocation -> {
            RemainingWorkConfirmation value = invocation.getArgument(0);
            value.confirmationId = 802L;
            inserted.set(value);
            return 1;
        });
        when(mapper.selectRemainingWorkConfirmationById(802L))
                .thenAnswer(invocation -> inserted.get());
    }

    @AfterEach
    void tearDown() { SecurityContextHolder.remove(); }

    @Test
    void everyNewDecisionInvalidatesDailyEvidenceAndPreservesTheOriginalReview()
    {
        for (String decision : List.of("ATTENDED", "ABSENT", "RETURN_FOR_EVIDENCE"))
        {
            org.mockito.Mockito.clearInvocations(mapper);
            RemainingWorkConfirmation result = service.confirm(command(decision), 101L);
            assertThat(result.decision).isEqualTo(decision);
            assertThat(result.supersedesConfirmationId).isEqualTo(801L);
            assertThat(result.userId).isEqualTo(7L);
            assertThat(result.decidedBy).isEqualTo(9L);
            assertThat(previous.decision).isEqualTo("ATTENDED");
            assertThat(previous.actualDepartureTime).isEqualTo(DAY.atTime(17, 0));
            var ordered = inOrder(mapper);
            ordered.verify(mapper).selectScheduleByIdForUpdate(31L);
            ordered.verify(mapper).insertRemainingWorkConfirmation(any());
            ordered.verify(mapper).invalidateDayResultForRemainingWork(31L, "manager");
            ordered.verify(mapper).selectRemainingWorkConfirmationById(802L);
        }
    }

    @Test
    void staleIntervalCannotInsertAReviewOrInvalidateAnyDailyResult()
    {
        Confirm command = command("ABSENT");
        command.remainingStart = command.remainingStart.plusMinutes(1);
        assertThatThrownBy(() -> service.confirm(command, 101L))
                .hasMessage("REMAINING_WORK_INTERVAL_STALE");
        verify(mapper, never()).insertRemainingWorkConfirmation(any());
        verify(mapper, never()).invalidateDayResultForRemainingWork(any(), any());
    }

    @Test
    void dailyInvalidationFailureIsPropagatedInsteadOfReturningSuccess()
    {
        doThrow(new IllegalStateException("controlled invalidation failure"))
                .when(mapper).invalidateDayResultForRemainingWork(31L, "manager");
        assertThatThrownBy(() -> service.confirm(command("ABSENT"), 101L))
                .hasMessage("controlled invalidation failure");
        verify(mapper, never()).selectRemainingWorkConfirmationById(802L);
    }

    private Confirm command(String decision)
    {
        Confirm value = new Confirm();
        value.scheduleId = 31L; value.remainingStart = DAY.atTime(10, 0);
        value.remainingEnd = DAY.atTime(17, 0); value.decision = decision;
        value.reason = "核验更新测试";
        if ("ATTENDED".equals(decision))
        {
            value.actualArrivalTime = value.remainingStart;
            value.actualDepartureTime = value.remainingEnd;
        }
        return value;
    }
}
