package com.erp.oa.attendance.approval;

import java.util.Date;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OaAttendanceApprovalCallbackMapper
{
    OaAttendanceApprovalState selectLeaveForUpdate(Long businessId);

    OaAttendanceApprovalState selectLeave(Long businessId);

    int countLeaveAttachments(Long businessId);

    OaAttendanceApprovalState selectCorrectionForUpdate(Long businessId);

    int updateLeaveDecision(@Param("businessId") Long businessId,
            @Param("expectedStatus") String expectedStatus,
            @Param("expectedVersion") Long expectedVersion,
            @Param("targetStatus") String targetStatus,
            @Param("eventKey") String eventKey,
            @Param("decisionTime") Date decisionTime);

    int updateCorrectionDecision(@Param("businessId") Long businessId,
            @Param("expectedStatus") String expectedStatus,
            @Param("expectedVersion") Long expectedVersion,
            @Param("targetStatus") String targetStatus,
            @Param("eventKey") String eventKey,
            @Param("decisionTime") Date decisionTime);

    int invalidateLeaveDayResults(@Param("businessId") Long businessId,
            @Param("decisionTime") Date decisionTime);

    int invalidateCorrectionDayResult(@Param("businessId") Long businessId,
            @Param("decisionTime") Date decisionTime);
}
