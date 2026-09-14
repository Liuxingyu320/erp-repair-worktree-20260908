package com.erp.oa.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.oa.attendance.domain.AttendanceModels.DayResult;
import com.erp.oa.attendance.domain.AttendanceModels.Schedule;
import com.erp.oa.attendance.dto.AttendanceRequests.DaySettlementCommand;
import com.erp.oa.attendance.dto.AttendanceSettlementViews.Result;
import com.erp.oa.attendance.mapper.AttendanceV2Mapper;
import com.erp.oa.attendance.support.AttendanceDaySettlementCalculator;
import com.erp.oa.attendance.support.AttendanceDaySettlementCalculator.Bounds;
import com.erp.oa.attendance.support.AttendanceDaySettlementCalculator.Evaluation;
import com.erp.oa.service.BusinessFeatureGate;

class AttendanceDaySettlementServiceTest
{
    private AttendanceV2Mapper mapper;
    private AttendanceDaySettlementCalculator calculator;
    private ShopScopeService shopScope;
    private AttendanceDaySettlementService service;

    @BeforeEach
    void setUp()
    {
        mapper = mock(AttendanceV2Mapper.class);
        calculator = mock(AttendanceDaySettlementCalculator.class);
        shopScope = mock(ShopScopeService.class);
        BusinessFeatureGate gate = mock(BusinessFeatureGate.class);
        doNothing().when(gate).requireEnabled(
                BusinessFeatureGate.ATTENDANCE_V2);
        when(mapper.selectDatabaseNow()).thenReturn(
                LocalDateTime.of(2026, 8, 20, 20, 0));
        service = new AttendanceDaySettlementService(mapper, calculator,
                shopScope, gate);
    }

    @Test
    void preflightRejectsARequestedShopOutsideSelectedShop()
    {
        when(shopScope.resolveRequiredShopDept(101L)).thenReturn(101L);

        assertThatThrownBy(() -> service.preflight(202L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 20), 101L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("ATTENDANCE_SHOP_SCOPE_MISMATCH");
        verifyNoInteractions(mapper, calculator);
    }

    @Test
    void alreadySettledRowsAreIdempotentlySkippedWithoutRecalculate()
    {
        Schedule schedule = schedule();
        DayResult current = settledResult();
        stubRange(schedule, current);

        Result value = service.settle(command(false), 101L);

        assertThat(value.skippedCount).isEqualTo(1);
        assertThat(value.settledCount).isZero();
        assertThat(value.skippedScheduleIds).containsExactly(31L);
        verify(mapper, never()).upsertDayResult(any());
        verifyNoInteractions(calculator);
    }

    @Test
    void alreadySettledRowsRequireExplicitRecalculateToBeRewritten()
    {
        Schedule schedule = schedule();
        DayResult current = settledResult();
        stubRange(schedule, current);
        when(calculator.bounds(schedule)).thenReturn(new Bounds(
                LocalDateTime.of(2026, 8, 20, 9, 0),
                LocalDateTime.of(2026, 8, 20, 18, 0)));
        DayResult calculated = new DayResult();
        calculated.scheduleId = schedule.scheduleId;
        Evaluation evaluation = new Evaluation(calculated, List.of());
        when(calculator.evaluate(any(), anyList(), anyList(), anyList(),
                anyList(), anyList(), anyList(), any())).thenReturn(evaluation);
        when(mapper.upsertDayResult(calculated)).thenReturn(2);
        DayResult saved = new DayResult();
        saved.scheduleId = schedule.scheduleId;
        saved.settledAt = LocalDateTime.of(2026, 8, 20, 20, 0);
        when(mapper.selectDayResultByScheduleId(schedule.scheduleId))
                .thenReturn(saved);

        Result value = service.settle(command(true), 101L);

        assertThat(value.settledCount).isEqualTo(1);
        assertThat(value.skippedCount).isZero();
        assertThat(value.recalculated).isTrue();
        assertThat(calculated.settledAt)
                .isEqualTo(LocalDateTime.of(2026, 8, 20, 20, 0));
        verify(mapper).upsertDayResult(calculated);
        verify(mapper).selectScheduleSegmentSnapshots(31L, true);
        verify(mapper).selectAcceptedPunches(31L);
        verify(mapper, never()).selectFirstAcceptedPunch(anyLong(), any());
        verify(mapper, never()).selectLastAcceptedPunch(anyLong(), any());
        verify(mapper, never()).selectShiftSegments(anyLong());
    }

    @Test
    void changedRemainingWorkIsIncludedByNormalSettlementAfterInvalidation()
    {
        Schedule schedule = schedule();
        DayResult current = settledResult();
        current.settledAt = null;
        current.resultStatus = "PENDING";
        current.exceptionCodes = "REMAINING_WORK_CONFIRMATION_CHANGED";
        current.workedMinutes = 420;
        stubRange(schedule, current);
        when(calculator.bounds(schedule)).thenReturn(new Bounds(
                schedule.businessDate.atTime(9, 0), schedule.businessDate.atTime(18, 0)));
        DayResult recalculated = new DayResult();
        recalculated.scheduleId = schedule.scheduleId;
        recalculated.workedMinutes = 0;
        recalculated.absenceMinutes = 420;
        when(calculator.evaluate(any(), anyList(), anyList(), anyList(),
                anyList(), anyList(), anyList(), any()))
                .thenReturn(new Evaluation(recalculated, List.of()));
        when(mapper.upsertDayResult(recalculated)).thenReturn(1);
        when(mapper.selectDayResultByScheduleId(31L)).thenReturn(recalculated);

        Result result = service.settle(command(false), 101L);

        assertThat(result.skippedCount).isZero();
        assertThat(result.settledCount).isEqualTo(1);
        assertThat(result.recalculated).isFalse();
        assertThat(result.dayResults.get(0).workedMinutes).isZero();
        assertThat(result.dayResults.get(0).absenceMinutes).isEqualTo(420);
        assertThat(result.dayResults.get(0).settledAt).isNotNull();
        verify(mapper).selectRemainingWorkConfirmationSources(31L, true);
    }

    private void stubRange(Schedule schedule, DayResult current)
    {
        when(shopScope.resolveRequiredShopDept(101L)).thenReturn(101L);
        when(mapper.selectPublishedSchedulesForSettlement(101L,
                LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 20)))
                .thenReturn(List.of(schedule));
        when(mapper.selectScheduleByIdForUpdate(schedule.scheduleId))
                .thenReturn(schedule);
        when(mapper.selectDayResultByScheduleIdForUpdate(
                schedule.scheduleId)).thenReturn(current);
    }

    private DaySettlementCommand command(boolean recalculate)
    {
        DaySettlementCommand value = new DaySettlementCommand();
        value.shopId = 101L;
        value.dateFrom = LocalDate.of(2026, 8, 20);
        value.dateTo = LocalDate.of(2026, 8, 20);
        value.recalculate = recalculate;
        return value;
    }

    private Schedule schedule()
    {
        Schedule value = new Schedule();
        value.scheduleId = 31L;
        value.shopId = 101L;
        value.userId = 7L;
        value.businessDate = LocalDate.of(2026, 8, 20);
        value.status = "PUBLISHED";
        return value;
    }

    private DayResult settledResult()
    {
        DayResult value = new DayResult();
        value.scheduleId = 31L;
        value.settledAt = LocalDateTime.of(2026, 8, 20, 19, 0);
        return value;
    }
}
