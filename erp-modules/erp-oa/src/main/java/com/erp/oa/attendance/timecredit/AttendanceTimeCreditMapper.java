package com.erp.oa.attendance.timecredit;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.erp.oa.attendance.domain.AttendanceModels.DayResult;
import com.erp.oa.attendance.timecredit.AttendanceTimeCreditModels.Adjustment;
import com.erp.oa.attendance.timecredit.AttendanceTimeCreditModels.SourceCandidate;

@Mapper
public interface AttendanceTimeCreditMapper
{
    LocalDateTime selectDatabaseNow();

    DayResult selectDayResultById(@Param("dayResultId") Long dayResultId);

    List<DayResult> selectDayResultsForUpdate(
            @Param("dayResultIds") List<Long> dayResultIds);

    Integer selectNetSourceUsed(
            @Param("sourceDayResultId") Long sourceDayResultId);

    Integer selectNetTargetOffset(
            @Param("targetDayResultId") Long targetDayResultId);

    List<SourceCandidate> selectSourceCandidates(
            @Param("shopId") Long shopId, @Param("userId") Long userId,
            @Param("monthStart") LocalDate monthStart,
            @Param("targetDate") LocalDate targetDate);

    List<Adjustment> selectHistoryByTargetDayResultId(
            @Param("targetDayResultId") Long targetDayResultId);

    Adjustment selectAdjustmentById(
            @Param("adjustmentId") Long adjustmentId);

    Adjustment selectAdjustmentByIdForUpdate(
            @Param("adjustmentId") Long adjustmentId);

    Adjustment selectByClientRequest(@Param("shopId") Long shopId,
            @Param("operatorUserId") Long operatorUserId,
            @Param("clientRequestId") String clientRequestId);

    int countReverse(@Param("originalAdjustmentId") Long originalAdjustmentId);

    int countSalaryRecords(@Param("userId") Long userId,
            @Param("salaryMonth") String salaryMonth);

    int ensurePeriodLock(@Param("shopId") Long shopId,
            @Param("salaryMonth") String salaryMonth);

    Integer lockPeriod(@Param("shopId") Long shopId,
            @Param("salaryMonth") String salaryMonth);

    int insertAdjustment(Adjustment adjustment);
}
