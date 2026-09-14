package com.erp.oa.attendance.leave.balance;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.oa.attendance.leave.balance.AttendanceLeaveQuotaModels.*;
public interface AttendanceLeaveQuotaMapper
{
    List<Allocation> selectAllocations(@Param("requestId") Long requestId,@Param("round") Integer round,@Param("lock") boolean lock);
    Event selectEvent(@Param("requestId") Long requestId,@Param("round") Integer round,@Param("eventKey") String eventKey);
    int insertAllocation(Allocation allocation);
    int transitionAllocation(@Param("id") Long id,@Param("expected") String expected,@Param("status") String status);
    int insertEvent(Event event);
    int updateRequestQuotaStatus(@Param("requestId") Long requestId,@Param("round") Integer round,@Param("status") String status);
    int returnFailedSubmission(@Param("requestId") Long requestId,@Param("round") Integer round);
    List<DayLock> selectOvertimeSourceDays(@Param("userId") Long userId,@Param("typeId") Long typeId);
    List<DayLock> selectAffectedDays(@Param("userId") Long userId,@Param("shopId") Long shopId,@Param("start") LocalDateTime start,@Param("end") LocalDateTime end);
}
