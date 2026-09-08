package com.erp.oa.attendance.correction;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.oa.attendance.correction.AttendanceCorrectionModels.ApprovalOutbox;
import com.erp.oa.attendance.correction.AttendanceCorrectionModels.CorrectionRequest;
import com.erp.oa.attendance.correction.AttendanceCorrectionModels.PunchEventRef;
import com.erp.oa.attendance.correction.AttendanceCorrectionModels.ScheduleRef;
import com.erp.oa.attendance.correction.AttendanceCorrectionModels.ScheduleSegmentRef;
import com.erp.oa.attendance.domain.AttendanceSettlementModels.LeaveSegmentSource;

public interface AttendanceCorrectionMapper
{
    String selectActiveEmployeeNameInShop(@Param("userId") Long userId,
            @Param("shopId") Long shopId);
    ScheduleRef selectScheduleRef(@Param("scheduleId") Long scheduleId);
    ScheduleRef selectScheduleRefForUpdate(
            @Param("scheduleId") Long scheduleId);
    List<ScheduleRef> selectOwnedScheduleRefs(
            @Param("userId") Long userId, @Param("shopId") Long shopId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo);
    ScheduleSegmentRef selectScheduleSegmentRef(
            @Param("scheduleId") Long scheduleId,
            @Param("scheduleSegmentSnapshotId") Long snapshotId);
    List<ScheduleSegmentRef> selectScheduleSegmentRefs(
            @Param("scheduleId") Long scheduleId);
    PunchEventRef selectPunchEventRef(@Param("punchEventId") Long eventId);
    List<PunchEventRef> selectSchedulePunchEvents(
            @Param("scheduleId") Long scheduleId,
            @Param("userId") Long userId);
    int countAcceptedPunchEvents(@Param("scheduleId") Long scheduleId,
            @Param("userId") Long userId,
            @Param("punchType") String punchType);
    int countAcceptedPunchEventsForSlot(
            @Param("scheduleId") Long scheduleId,
            @Param("userId") Long userId,
            @Param("punchSlotKey") String punchSlotKey);
    List<LeaveSegmentSource> selectApprovedLeaveSegmentsForSchedule(
            @Param("scheduleId") Long scheduleId,
            @Param("userId") Long userId,
            @Param("shopId") Long shopId);

    int insertCorrectionRequest(CorrectionRequest value);
    int updateCorrectionDraft(CorrectionRequest value);
    CorrectionRequest selectCorrectionRequestById(
            @Param("correctionRequestId") Long id);
    CorrectionRequest selectCorrectionRequestByIdForUpdate(
            @Param("correctionRequestId") Long id);
    CorrectionRequest selectCorrectionRequestByClientRequestId(
            @Param("userId") Long userId, @Param("shopId") Long shopId,
            @Param("clientRequestId") String clientRequestId);
    CorrectionRequest selectCorrectionRequestByClientRequestIdForUpdate(
            @Param("userId") Long userId, @Param("shopId") Long shopId,
            @Param("clientRequestId") String clientRequestId);
    List<CorrectionRequest> selectCorrectionRequestsByUser(
            @Param("userId") Long userId, @Param("shopId") Long shopId,
            @Param("status") String status,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo);
    List<CorrectionRequest> selectCorrectionRequestsByShop(
            @Param("shopId") Long shopId, @Param("userId") Long userId,
            @Param("status") String status,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo);
    int countActiveCorrection(@Param("userId") Long userId,
            @Param("scheduleId") Long scheduleId,
            @Param("targetPunchType") String targetPunchType,
            @Param("excludeId") Long excludeId);
    int countActiveCorrectionForSlot(@Param("userId") Long userId,
            @Param("scheduleId") Long scheduleId,
            @Param("targetPunchSlotKey") String targetPunchSlotKey,
            @Param("excludeId") Long excludeId);
    int countActiveCorrectionForOriginalEvent(
            @Param("userId") Long userId,
            @Param("originalPunchEventId") Long originalPunchEventId,
            @Param("excludeId") Long excludeId);
    int markCorrectionSubmitting(
            @Param("correctionRequestId") Long correctionRequestId,
            @Param("expectedStatus") String expectedStatus,
            @Param("expectedVersion") Long expectedVersion,
            @Param("businessRound") Integer businessRound,
            @Param("updateBy") String updateBy);
    int invalidateDayResultForCorrection(
            @Param("correctionRequestId") Long correctionRequestId,
            @Param("updateBy") String updateBy);
    int finalizeCorrectionApprovalStart(
            @Param("correctionRequestId") Long correctionRequestId,
            @Param("businessRound") Integer businessRound,
            @Param("expectedVersion") Long expectedVersion,
            @Param("instanceId") Long instanceId,
            @Param("updateBy") String updateBy);

    int insertApprovalOutbox(ApprovalOutbox value);
    ApprovalOutbox selectApprovalOutboxById(@Param("outboxId") Long id);
    ApprovalOutbox selectApprovalOutboxByIdForUpdate(
            @Param("outboxId") Long id);
    ApprovalOutbox selectApprovalOutboxByRound(
            @Param("correctionRequestId") Long correctionRequestId,
            @Param("businessRound") Integer businessRound);
    List<ApprovalOutbox> selectDueApprovalOutboxes(
            @Param("dueAt") LocalDateTime dueAt,
            @Param("staleClaimedBefore") LocalDateTime staleClaimedBefore,
            @Param("limit") int limit);
    int claimApprovalOutbox(@Param("outboxId") Long outboxId,
            @Param("expectedStatus") String expectedStatus,
            @Param("expectedVersion") Long expectedVersion,
            @Param("claimToken") String claimToken);
    int markApprovalRemoteSucceeded(@Param("outboxId") Long outboxId,
            @Param("expectedStatus") String expectedStatus,
            @Param("expectedVersion") Long expectedVersion,
            @Param("remoteInstanceId") Long remoteInstanceId,
            @Param("remoteStatus") String remoteStatus,
            @Param("remoteBusinessRound") Integer remoteBusinessRound,
            @Param("lastHttpStatus") Integer lastHttpStatus);
    int markApprovalRetry(@Param("outboxId") Long outboxId,
            @Param("expectedStatus") String expectedStatus,
            @Param("expectedVersion") Long expectedVersion,
            @Param("nextRetryAt") LocalDateTime nextRetryAt,
            @Param("lastHttpStatus") Integer lastHttpStatus,
            @Param("lastErrorCode") String lastErrorCode,
            @Param("lastError") String lastError);
    int markApprovalFailed(@Param("outboxId") Long outboxId,
            @Param("expectedStatus") String expectedStatus,
            @Param("expectedVersion") Long expectedVersion,
            @Param("lastHttpStatus") Integer lastHttpStatus,
            @Param("lastErrorCode") String lastErrorCode,
            @Param("lastError") String lastError);
    int markApprovalCompleted(@Param("outboxId") Long outboxId,
            @Param("expectedStatus") String expectedStatus,
            @Param("expectedVersion") Long expectedVersion);
}
