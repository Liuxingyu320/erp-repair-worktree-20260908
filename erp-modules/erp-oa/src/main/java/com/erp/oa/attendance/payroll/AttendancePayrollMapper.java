package com.erp.oa.attendance.payroll;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.erp.oa.attendance.domain.AttendanceModels.DayResult;

/**
 * Read-only payroll projection over published attendance V2 schedules.
 *
 * <p>The left join is intentional: a published schedule without a daily
 * result is returned as an incomplete row so payroll preflight fails closed
 * instead of silently treating the day as time off.</p>
 */
@Mapper
public interface AttendancePayrollMapper
{
    List<DayResult> selectPublishedDayResultsForPayroll(
            @Param("shopId") Long shopId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo);
}
