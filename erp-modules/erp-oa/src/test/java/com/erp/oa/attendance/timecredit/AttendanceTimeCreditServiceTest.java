package com.erp.oa.attendance.timecredit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.oa.attendance.domain.AttendanceModels.DayResult;
import com.erp.oa.attendance.timecredit.AttendanceTimeCreditModels.Adjustment;
import com.erp.oa.attendance.timecredit.AttendanceTimeCreditModels.SourceCandidate;
import com.erp.oa.attendance.timecredit.AttendanceTimeCreditRequests.Apply;
import com.erp.oa.attendance.timecredit.AttendanceTimeCreditRequests.Reverse;
import com.erp.oa.service.BusinessFeatureGate;

@DisplayName("店长加班抵扣早退台账")
class AttendanceTimeCreditServiceTest
{
    private final AttendanceTimeCreditMapper mapper =
            mock(AttendanceTimeCreditMapper.class);
    private final ShopScopeService shopScope = mock(ShopScopeService.class);
    private final BusinessFeatureGate featureGate =
            mock(BusinessFeatureGate.class);
    private AttendanceTimeCreditService service;

    @BeforeEach
    void setUp()
    {
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("store-manager");
        when(shopScope.resolveRequiredShopDept(201L)).thenReturn(201L);
        when(mapper.selectDatabaseNow()).thenReturn(
                LocalDateTime.of(2026, 8, 23, 10, 0));
        doAnswer(invocation -> {
            Adjustment value = invocation.getArgument(0);
            value.adjustmentId = 100L;
            return 1;
        }).when(mapper).insertAdjustment(any(Adjustment.class));
        when(mapper.selectAdjustmentById(100L)).thenAnswer(invocation -> {
            Adjustment value = new Adjustment();
            value.adjustmentId = 100L;
            return value;
        });
        service = new AttendanceTimeCreditService(mapper, shopScope,
                featureGate);
    }

    @AfterEach
    void tearDown()
    { SecurityContextHolder.remove(); }

    @Test
    @DisplayName("同店同人同月且来源在前时追加抵扣")
    void shouldAppendValidCredit()
    {
        DayResult source = result(1L, LocalDate.of(2026, 8, 20),
                480, 540, 0);
        DayResult target = result(2L, LocalDate.of(2026, 8, 23),
                480, 480, 60);
        when(mapper.selectDayResultById(2L)).thenReturn(target);
        when(mapper.selectDayResultsForUpdate(any()))
                .thenReturn(List.of(source, target));
        when(mapper.selectNetSourceUsed(1L)).thenReturn(0);
        when(mapper.selectNetTargetOffset(2L)).thenReturn(0);
        when(mapper.countSalaryRecords(11L, "2026-08"))
                .thenReturn(0);

        Adjustment saved = service.apply(apply(1L, 2L, 60), 201L);

        assertThat(saved.adjustmentId).isEqualTo(100L);
        verify(mapper).lockPeriod(201L, "2026-08");
        verify(mapper).insertAdjustment(any(Adjustment.class));
    }

    @Test
    @DisplayName("候选加班日不包含当前早退日")
    void shouldExcludeTargetDayFromSourceCandidates()
    {
        DayResult target = result(2L, LocalDate.of(2026, 8, 23),
                480, 540, 60);
        when(mapper.selectDayResultById(2L)).thenReturn(target);
        when(mapper.selectNetTargetOffset(2L)).thenReturn(0);
        when(mapper.countSalaryRecords(11L, "2026-08"))
                .thenReturn(0);
        when(mapper.selectSourceCandidates(anyLong(), anyLong(), any(),
                any())).thenReturn(List.of(sourceCandidate(1L),
                        sourceCandidate(2L)));

        var context = service.context(2L, 201L);

        assertThat(context.sources).extracting(value -> value.dayResultId)
                .containsExactly(1L);
    }

    @Test
    @DisplayName("日结重算导致抵扣超额时仍返回历史并只允许撤销")
    void shouldExposeHistoryWhenRecalculationMakesLedgerInconsistent()
    {
        DayResult target = result(2L, LocalDate.of(2026, 8, 23),
                480, 480, 30);
        Adjustment original = original();
        when(mapper.selectDayResultById(2L)).thenReturn(target);
        when(mapper.selectNetTargetOffset(2L)).thenReturn(60);
        when(mapper.countSalaryRecords(11L, "2026-08"))
                .thenReturn(0);
        when(mapper.selectHistoryByTargetDayResultId(2L))
                .thenReturn(List.of(original));

        var context = service.context(2L, 201L);

        assertThat(context.ledgerInconsistent).isTrue();
        assertThat(context.ledgerWarning).contains("只能先撤销");
        assertThat(context.targetRemainingEarlyLeaveMinutes).isZero();
        assertThat(context.sources).isEmpty();
        assertThat(context.history).containsExactly(original);
        verify(mapper, never()).selectSourceCandidates(anyLong(), anyLong(),
                any(), any());
    }

    @Test
    @DisplayName("不能跨月或借用未来加班")
    void shouldRejectCrossMonthAndFutureCredit()
    {
        DayResult source = result(1L, LocalDate.of(2026, 7, 31),
                480, 540, 0);
        DayResult target = result(2L, LocalDate.of(2026, 8, 1),
                480, 480, 60);
        when(mapper.selectDayResultById(2L)).thenReturn(target);
        when(mapper.selectDayResultsForUpdate(any()))
                .thenReturn(List.of(source, target));

        assertThatThrownBy(() -> service.apply(apply(1L, 2L, 30), 201L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("同一工资月份");
        verify(mapper, never()).insertAdjustment(any());
    }

    @Test
    @DisplayName("工资已生成后禁止新增抵扣")
    void shouldRejectCreditAfterPayrollGeneration()
    {
        DayResult source = result(1L, LocalDate.of(2026, 8, 20),
                480, 540, 0);
        DayResult target = result(2L, LocalDate.of(2026, 8, 23),
                480, 480, 60);
        when(mapper.selectDayResultById(2L)).thenReturn(target);
        when(mapper.selectDayResultsForUpdate(any()))
                .thenReturn(List.of(source, target));
        when(mapper.countSalaryRecords(11L, "2026-08"))
                .thenReturn(1);

        assertThatThrownBy(() -> service.apply(apply(1L, 2L, 30), 201L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("工资已生成");
        verify(mapper, never()).insertAdjustment(any());
    }

    @Test
    @DisplayName("撤销只追加反向事件且保留原记录")
    void shouldAppendReverseEvent()
    {
        Adjustment original = original();
        when(mapper.selectAdjustmentById(50L)).thenReturn(original);
        when(mapper.selectAdjustmentByIdForUpdate(50L)).thenReturn(original);
        when(mapper.countReverse(50L)).thenReturn(0);
        when(mapper.countSalaryRecords(11L, "2026-08"))
                .thenReturn(0);
        when(mapper.selectDayResultsForUpdate(any())).thenReturn(List.of(
                result(1L, LocalDate.of(2026, 8, 20), 480, 540, 0),
                result(2L, LocalDate.of(2026, 8, 23), 480, 480, 60)));
        Reverse command = new Reverse();
        command.clientRequestId = "reverse:20260823:0001";
        command.reason = "店长核对后撤销错误抵扣";

        service.reverse(50L, command, 201L);

        verify(mapper).insertAdjustment(any(Adjustment.class));
        verify(mapper, never()).selectNetSourceUsed(anyLong());
    }

    private Apply apply(Long sourceId, Long targetId, int minutes)
    {
        Apply value = new Apply();
        value.sourceDayResultId = sourceId;
        value.targetDayResultId = targetId;
        value.adjustmentMinutes = minutes;
        value.clientRequestId = "credit:20260823:0001";
        value.reason = "店长确认使用当月加班抵扣早退";
        return value;
    }

    private DayResult result(Long id, LocalDate date, int scheduled,
            int worked, int early)
    {
        DayResult value = new DayResult();
        value.dayResultId = id;
        value.scheduleId = 100L + id;
        value.userId = 11L;
        value.userName = "seller01";
        value.shopId = 201L;
        value.businessDate = date;
        value.scheduledMinutes = scheduled;
        value.workedMinutes = worked;
        value.earlyLeaveMinutes = early;
        value.settledAt = LocalDateTime.of(2026, 8, 24, 1, 0);
        return value;
    }

    private SourceCandidate sourceCandidate(Long dayResultId)
    {
        SourceCandidate value = new SourceCandidate();
        value.dayResultId = dayResultId;
        return value;
    }

    private Adjustment original()
    {
        Adjustment value = new Adjustment();
        value.adjustmentId = 50L;
        value.adjustmentAction = "APPLY";
        value.shopId = 201L;
        value.userId = 11L;
        value.userName = "seller01";
        value.salaryMonth = "2026-08";
        value.sourceDayResultId = 1L;
        value.sourceScheduleId = 101L;
        value.sourceBusinessDate = LocalDate.of(2026, 8, 20);
        value.targetDayResultId = 2L;
        value.targetScheduleId = 102L;
        value.targetBusinessDate = LocalDate.of(2026, 8, 23);
        value.adjustmentMinutes = 60;
        return value;
    }
}
