package com.erp.oa.service.impl;

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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.oa.attendance.domain.AttendanceModels.DayResult;
import com.erp.oa.attendance.payroll.AttendancePayrollMapper;
import com.erp.oa.attendance.timecredit.AttendanceTimeCreditMapper;
import com.erp.oa.domain.OaSalaryConfig;
import com.erp.oa.domain.OaSalaryEmployee;
import com.erp.oa.domain.OaSalaryRecord;
import com.erp.oa.domain.vo.OaSalaryAttendancePreflightVo;
import com.erp.oa.mapper.OaDeptScopeMapper;
import com.erp.oa.mapper.OaSalaryConfigMapper;
import com.erp.oa.mapper.OaSalaryEmployeeMapper;
import com.erp.oa.mapper.OaSalaryRecordMapper;
import com.erp.oa.service.BusinessFeatureGate;

@DisplayName("OA工资仅读取考勤V2已结算分钟")
class OaSalaryServiceImplTest
{
    private final AttendancePayrollMapper attendance =
            mock(AttendancePayrollMapper.class);
    private final AttendanceTimeCreditMapper timeCredits =
            mock(AttendanceTimeCreditMapper.class);
    private final OaSalaryEmployeeMapper employees =
            mock(OaSalaryEmployeeMapper.class);
    private final OaSalaryRecordMapper salaries =
            mock(OaSalaryRecordMapper.class);
    private final OaSalaryConfigMapper configs =
            mock(OaSalaryConfigMapper.class);
    private final OaDeptScopeMapper deptScope = mock(OaDeptScopeMapper.class);
    private final ShopScopeService shopScope = mock(ShopScopeService.class);
    private final BusinessFeatureGate featureGate =
            mock(BusinessFeatureGate.class);
    private final Map<Long, OaSalaryRecord> stored = new LinkedHashMap<>();
    private final AtomicLong ids = new AtomicLong(1000);
    private OaSalaryServiceImpl service;

    @BeforeEach
    void setUp()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        when(shopScope.resolveRequiredShopDept(any())).thenAnswer(value ->
                value.getArgument(0));
        when(configs.selectOaSalaryConfigByShopDeptId(201L))
                .thenReturn(config());
        doAnswer(invocation -> {
            OaSalaryRecord value = invocation.getArgument(0);
            value.setSalaryId(ids.incrementAndGet());
            stored.put(value.getSalaryId(), value);
            return 1;
        }).when(salaries).insertOaSalaryRecord(any(OaSalaryRecord.class));
        when(salaries.selectOaSalaryRecordById(anyLong())).thenAnswer(value ->
                stored.get(value.getArgument(0)));
        when(salaries.deleteByShopDeptIdAndMonth(anyLong(), anyString()))
                .thenAnswer(value -> { stored.clear(); return 1; });

        service = new OaSalaryServiceImpl();
        ReflectionTestUtils.setField(service, "configMapper", configs);
        ReflectionTestUtils.setField(service, "salaryMapper", salaries);
        ReflectionTestUtils.setField(service, "attendancePayrollMapper",
                attendance);
        ReflectionTestUtils.setField(service, "attendanceTimeCreditMapper",
                timeCredits);
        ReflectionTestUtils.setField(service, "salaryEmployeeMapper",
                employees);
        ReflectionTestUtils.setField(service, "deptScopeMapper", deptScope);
        ReflectionTestUtils.setField(service, "shopScopeService", shopScope);
        ReflectionTestUtils.setField(service, "featureGate", featureGate);
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("按已发布排班的已结算分钟聚合工资")
    void shouldCalculateFromSettledMinuteResults()
    {
        when(employees.selectSalaryEmployeesByShopDeptId(201L, "2026-06"))
                .thenReturn(List.of(employee(11L, "seller01", "3000.00")));
        when(attendance.selectPublishedDayResultsForPayroll(201L,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)))
                .thenReturn(List.of(
                        settled(1L, 11L, LocalDate.of(2026, 6, 1),
                                480, 540, 0, 0, 0, 10, 0, "LATE"),
                        settled(2L, 11L, LocalDate.of(2026, 6, 2),
                                480, 240, 120, 60, 60, 0, 0,
                                "LEAVE_PARTIAL")));

        OaSalaryRecord result = service.calculateSalary(201L, "2026-06",
                201L).get(0);

        assertThat(result.getScheduledMinutes()).isEqualTo(960);
        assertThat(result.getWorkedMinutes()).isEqualTo(780);
        assertThat(result.getPaidLeaveMinutes()).isEqualTo(120);
        assertThat(result.getUnpaidLeaveMinutes()).isEqualTo(60);
        assertThat(result.getAbsenceMinutes()).isEqualTo(60);
        assertThat(result.getWorkDays()).isEqualTo(2);
        assertThat(result.getLeaveDays()).isEqualTo(1);
        assertThat(result.getAbsentDays()).isEqualTo(1);
        assertThat(result.getOvertimeHours()).isEqualByComparingTo("1.00");
        assertThat(result.getAbsentDeduction()).isEqualByComparingTo("25.00");
        assertThat(result.getTotalSalary()).isEqualByComparingTo("3015.00");
        assertThat(result.getAttendanceSourceVersion())
                .isEqualTo("ATTENDANCE_V2_TIME_CREDIT_V2");
        verify(featureGate).requireEnabled(BusinessFeatureGate.ATTENDANCE_V2);
    }

    @Test
    @DisplayName("同月加班抵扣早退后不重复发加班费也不扣早退")
    void shouldUseTimeCreditExactlyOnceInPayroll()
    {
        when(employees.selectSalaryEmployeesByShopDeptId(201L, "2026-06"))
                .thenReturn(List.of(employee(11L, "seller01", "3000.00")));
        DayResult overtime = settled(1L, 11L, LocalDate.of(2026, 6, 1),
                480, 540, 0, 0, 0, 0, 0, "NORMAL");
        overtime.rawOvertimeMinutes = 60;
        overtime.timeCreditUsedMinutes = 60;
        overtime.netOvertimeMinutes = 0;
        DayResult early = settled(2L, 11L, LocalDate.of(2026, 6, 5),
                480, 480, 0, 0, 0, 0, 60, "EARLY");
        early.rawOvertimeMinutes = 0;
        early.timeCreditOffsetMinutes = 60;
        early.netEarlyLeaveMinutes = 0;
        when(attendance.selectPublishedDayResultsForPayroll(201L,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)))
                .thenReturn(List.of(overtime, early));

        OaSalaryRecord result = service.calculateSalary(201L, "2026-06",
                201L).get(0);

        assertThat(result.getOvertimeHours()).isEqualByComparingTo("0.00");
        assertThat(result.getOvertimePay()).isEqualByComparingTo("0.00");
        assertThat(result.getEarlyTotalMinutes()).isZero();
        assertThat(result.getEarlyDeduction()).isEqualByComparingTo("0.00");
        assertThat(result.getTotalSalary()).isEqualByComparingTo("3000.00");
        verify(timeCredits).lockPeriod(201L, "2026-06");
    }

    @Test
    @DisplayName("日结重算导致抵扣超过原始加班时工资失败关闭")
    void shouldBlockStaleTimeCreditAfterDayResultRecalculation()
    {
        when(employees.selectSalaryEmployeesByShopDeptId(201L, "2026-06"))
                .thenReturn(List.of(employee(11L, "seller01", "3000.00")));
        DayResult invalid = settled(1L, 11L, LocalDate.of(2026, 6, 1),
                480, 510, 0, 0, 0, 0, 0, "NORMAL");
        invalid.rawOvertimeMinutes = 30;
        invalid.timeCreditUsedMinutes = 60;
        invalid.netOvertimeMinutes = -30;
        when(attendance.selectPublishedDayResultsForPayroll(anyLong(), any(),
                any())).thenReturn(List.of(invalid));

        assertThat(service.preflightAttendance(201L, "2026-06", 201L)
                .getIssues()).singleElement()
                        .extracting(value -> value.getReason())
                        .isEqualTo("加班抵扣超过当前可核定加班分钟");
    }

    @Test
    @DisplayName("未结算结果在删除旧工资前阻断")
    void shouldBlockUnsettledResultBeforeReplacingSalary()
    {
        when(employees.selectSalaryEmployeesByShopDeptId(201L, "2026-06"))
                .thenReturn(List.of(employee(11L, "seller01", "3000.00")));
        DayResult pending = settled(1L, 11L, LocalDate.of(2026, 6, 1),
                480, 0, 0, 0, 0, 0, 0, "PENDING");
        pending.settledAt = null;
        pending.exceptionCodes = "MISSING_OUT";
        when(attendance.selectPublishedDayResultsForPayroll(anyLong(), any(),
                any())).thenReturn(List.of(pending));

        OaSalaryAttendancePreflightVo preflight = service.preflightAttendance(
                201L, "2026-06", 201L);

        assertThat(preflight.isBlocked()).isTrue();
        assertThat(preflight.getIssues()).singleElement()
                .extracting(value -> value.getReason()).isEqualTo("未签退");
        assertThatThrownBy(() -> service.calculateSalary(201L, "2026-06",
                201L)).isInstanceOf(ServiceException.class)
                .hasMessageContaining("未签退");
        verify(salaries, never()).deleteByShopDeptIdAndMonth(anyLong(),
                anyString());
    }

    @Test
    @DisplayName("已发布排班缺每日结果时失败关闭")
    void shouldBlockMissingDailyResult()
    {
        when(employees.selectSalaryEmployeesByShopDeptId(201L, "2026-06"))
                .thenReturn(List.of(employee(11L, "seller01", "3000.00")));
        DayResult missing = new DayResult();
        missing.scheduleId = 91L;
        missing.userId = 11L;
        missing.userName = "seller01";
        missing.businessDate = LocalDate.of(2026, 6, 1);
        missing.scheduledMinutes = 480;
        missing.resultStatus = "MISSING_RESULT";
        missing.exceptionCodes = "MISSING_DAY_RESULT";
        when(attendance.selectPublishedDayResultsForPayroll(anyLong(), any(),
                any())).thenReturn(List.of(missing));

        assertThat(service.preflightAttendance(201L, "2026-06", 201L)
                .getIssues()).singleElement()
                        .extracting(value -> value.getReason())
                        .isEqualTo("已发布排班缺少每日结果");
    }

    @Test
    @DisplayName("工资员工整月无已发布排班时阻断")
    void shouldBlockPayrollEmployeeWithoutPublishedSchedule()
    {
        when(employees.selectSalaryEmployeesByShopDeptId(201L, "2026-06"))
                .thenReturn(List.of(employee(11L, "seller01", "3000.00")));
        when(attendance.selectPublishedDayResultsForPayroll(anyLong(), any(),
                any())).thenReturn(List.of());

        OaSalaryAttendancePreflightVo result = service.preflightAttendance(
                201L, "2026-06", 201L);

        assertThat(result.isBlocked()).isTrue();
        assertThat(result.getIssues()).singleElement()
                .extracting(value -> value.getReason())
                .isEqualTo("本月没有已发布排班");
    }

    @Test
    @DisplayName("每日结果分钟不守恒时阻断")
    void shouldBlockInconsistentMinuteResult()
    {
        when(employees.selectSalaryEmployeesByShopDeptId(201L, "2026-06"))
                .thenReturn(List.of(employee(11L, "seller01", "3000.00")));
        DayResult invalid = settled(1L, 11L, LocalDate.of(2026, 6, 1),
                480, 300, 0, 0, 0, 0, 0, "NORMAL");
        when(attendance.selectPublishedDayResultsForPayroll(anyLong(), any(),
                any())).thenReturn(List.of(invalid));

        assertThat(service.preflightAttendance(201L, "2026-06", 201L)
                .getIssues()).singleElement()
                        .extracting(value -> value.getReason())
                        .isEqualTo("每日结果分钟不守恒");
    }

    @Test
    @DisplayName("未知的已结算状态不得进入工资")
    void shouldBlockUnknownSettledStatus()
    {
        when(employees.selectSalaryEmployeesByShopDeptId(201L, "2026-06"))
                .thenReturn(List.of(employee(11L, "seller01", "3000.00")));
        DayResult invalid = settled(1L, 11L, LocalDate.of(2026, 6, 1),
                480, 480, 0, 0, 0, 0, 0, "FUTURE_STATUS");
        when(attendance.selectPublishedDayResultsForPayroll(anyLong(), any(),
                any())).thenReturn(List.of(invalid));

        assertThat(service.preflightAttendance(201L, "2026-06", 201L)
                .getIssues()).singleElement()
                        .extracting(value -> value.getReason())
                        .isEqualTo("每日结果状态异常");
    }

    @Test
    @DisplayName("工资月份必须严格使用yyyy-MM")
    void shouldRejectInvalidMonth()
    {
        assertThatThrownBy(() -> service.preflightAttendance(201L, "2026-6",
                201L)).isInstanceOf(ServiceException.class)
                        .hasMessageContaining("yyyy-MM");
        verify(attendance, never()).selectPublishedDayResultsForPayroll(
                anyLong(), any(), any());
    }

    @Test
    void shouldUseImportedTotalIncludingAllowancesRatherThanBaseSalary()
    {
        OaSalaryEmployee employee = employee(11L, "seller01", "3000");
        employee.setPostSalary(new BigDecimal("1000"));
        employee.setFieldAllowance(new BigDecimal("500"));
        employee.setPerformanceSalary(new BigDecimal("500"));
        employee.setSalaryTotal(new BigDecimal("5000"));
        when(employees.selectSalaryEmployeesByShopDeptId(201L, "2026-06"))
                .thenReturn(List.of(employee));
        when(attendance.selectPublishedDayResultsForPayroll(anyLong(), any(), any()))
                .thenReturn(List.of(settled(1L, 11L, LocalDate.of(2026, 6, 1),
                        480, 540, 0, 0, 0, 10, 0, "LATE")));
        OaSalaryRecord result = service.calculateSalary(201L, "2026-06", 201L).get(0);
        assertThat(result.getBaseSalary()).isEqualByComparingTo("5000");
        assertThat(result.getTotalSalary()).isEqualByComparingTo("5040");
    }

    @Test
    void missingProfileSalaryMustNotBecomeZeroOrReplaceExistingSalary()
    {
        OaSalaryEmployee employee = employee(11L, "seller01", "3000");
        employee.setSalaryTotal(null);
        when(employees.selectSalaryEmployeesByShopDeptId(201L, "2026-06"))
                .thenReturn(List.of(employee));
        when(attendance.selectPublishedDayResultsForPayroll(anyLong(), any(), any()))
                .thenReturn(List.of(settled(1L, 11L, LocalDate.of(2026, 6, 1),
                        480, 480, 0, 0, 0, 0, 0, "NORMAL")));
        assertThatThrownBy(() -> service.calculateSalary(201L, "2026-06", 201L))
                .hasMessageContaining("未完整入档");
        verify(salaries, never()).deleteByShopDeptIdAndMonth(anyLong(), anyString());
    }

    private OaSalaryEmployee employee(Long userId, String userName,
            String baseSalary)
    {
        OaSalaryEmployee value = new OaSalaryEmployee();
        value.setUserId(userId);
        value.setUserName(userName);
        value.setDeptId(201L);
        value.setShopDeptId(201L);
        value.setBaseSalary(new BigDecimal(baseSalary));
        value.setPostSalary(BigDecimal.ZERO);
        value.setFieldAllowance(BigDecimal.ZERO);
        value.setPerformanceSalary(BigDecimal.ZERO);
        value.setSalaryTotal(new BigDecimal(baseSalary));
        return value;
    }

    private DayResult settled(Long id, Long userId, LocalDate date,
            int scheduled, int worked, int paid, int unpaid, int absence,
            int late, int early, String status)
    {
        DayResult value = new DayResult();
        value.dayResultId = id;
        value.scheduleId = id + 100;
        value.userId = userId;
        value.userName = "seller01";
        value.shopId = 201L;
        value.businessDate = date;
        value.scheduledMinutes = scheduled;
        value.workedMinutes = worked;
        value.paidLeaveMinutes = paid;
        value.unpaidLeaveMinutes = unpaid;
        value.absenceMinutes = absence;
        value.lateMinutes = late;
        value.earlyLeaveMinutes = early;
        value.resultStatus = status;
        value.settledAt = LocalDateTime.of(2026, 7, 1, 1, 0);
        return value;
    }

    private OaSalaryConfig config()
    {
        OaSalaryConfig value = new OaSalaryConfig();
        value.setConfigId(1L);
        value.setShopDeptId(201L);
        value.setWorkHoursPerDay(new BigDecimal("8.00"));
        value.setLatePenaltyPerMin(new BigDecimal("1.00"));
        value.setEarlyPenaltyPerMin(new BigDecimal("1.00"));
        value.setAbsentPenaltyPerDay(new BigDecimal("100.00"));
        value.setOvertimePayPerHour(new BigDecimal("50.00"));
        return value;
    }
}
