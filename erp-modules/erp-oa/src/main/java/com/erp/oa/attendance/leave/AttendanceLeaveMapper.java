package com.erp.oa.attendance.leave;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.oa.attendance.leave.AttendanceLeaveModels.ApprovalOutbox;
import com.erp.oa.attendance.leave.AttendanceLeaveModels.LeaveAttachment;
import com.erp.oa.attendance.leave.AttendanceLeaveModels.LeaveRequest;
import com.erp.oa.attendance.leave.AttendanceLeaveModels.LeaveSegment;
import com.erp.oa.attendance.leave.AttendanceLeaveModels.LeaveType;
import com.erp.oa.attendance.leave.AttendanceLeaveModels.ScheduleRef;

public interface AttendanceLeaveMapper
{
    List<LeaveType> selectLeaveTypes(@Param("status") String status);
    LeaveType selectLeaveTypeById(@Param("leaveTypeId") Long leaveTypeId);
    int insertLeaveType(LeaveType value);
    int updateLeaveType(LeaveType value);
    int updateLeaveTypeStatus(@Param("leaveTypeId") Long leaveTypeId,
            @Param("status") String status,
            @Param("rowVersion") Long rowVersion,
            @Param("updateBy") String updateBy);

    String selectActiveEmployeeNameInShop(@Param("userId") Long userId,
            @Param("shopId") Long shopId);
    int insertLeaveRequest(LeaveRequest value);
    int updateLeaveDraft(LeaveRequest value);
    LeaveRequest selectLeaveRequestById(@Param("leaveRequestId") Long id);
    LeaveRequest selectLeaveRequestByIdForUpdate(
            @Param("leaveRequestId") Long id);
    LeaveRequest selectLeaveRequestByClientRequestId(
            @Param("userId") Long userId, @Param("shopId") Long shopId,
            @Param("clientRequestId") String clientRequestId);
    LeaveRequest selectLeaveRequestByClientRequestIdForUpdate(
            @Param("userId") Long userId, @Param("shopId") Long shopId,
            @Param("clientRequestId") String clientRequestId);
    List<LeaveRequest> selectLeaveRequestsByUser(
            @Param("userId") Long userId, @Param("shopId") Long shopId,
            @Param("status") String status,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo);
    List<LeaveRequest> selectLeaveRequestsByShop(
            @Param("shopId") Long shopId, @Param("userId") Long userId,
            @Param("status") String status,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo);
    int countOverlappingLeave(@Param("userId") Long userId,
            @Param("excludeId") Long excludeId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
    List<ScheduleRef> selectScheduleRefs(@Param("userId") Long userId,
            @Param("shopId") Long shopId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo);
    Integer calculateScheduledWorkMinutes(@Param("userId") Long userId,
            @Param("shopId") Long shopId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
    int countOverlappingPublishedSchedules(@Param("userId") Long userId,
            @Param("shopId") Long shopId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
    int deleteLeaveSegments(@Param("leaveRequestId") Long leaveRequestId);
    int insertLeaveSegment(LeaveSegment value);
    List<LeaveSegment> selectLeaveSegments(
            @Param("leaveRequestId") Long leaveRequestId);

    int insertLeaveAttachment(LeaveAttachment value);
    LeaveAttachment selectLeaveAttachmentById(
            @Param("attachmentId") Long attachmentId);
    LeaveAttachment selectLeaveAttachmentByHash(
            @Param("leaveRequestId") Long leaveRequestId,
            @Param("sha256") String sha256);
    List<LeaveAttachment> selectLeaveAttachments(
            @Param("leaveRequestId") Long leaveRequestId);
    int countLeaveAttachments(@Param("leaveRequestId") Long leaveRequestId);
    int deleteLeaveAttachment(@Param("attachmentId") Long attachmentId,
            @Param("leaveRequestId") Long leaveRequestId);

    int markLeaveSubmitting(@Param("leaveRequestId") Long leaveRequestId,
            @Param("expectedStatus") String expectedStatus,
            @Param("expectedVersion") Long expectedVersion,
            @Param("businessRound") Integer businessRound,
            @Param("updateBy") String updateBy);
    int markLeaveAutoApproved(@Param("leaveRequestId") Long leaveRequestId,
            @Param("expectedStatus") String expectedStatus,
            @Param("expectedVersion") Long expectedVersion,
            @Param("updateBy") String updateBy);
    int invalidateDayResultsForLeaveRequest(
            @Param("leaveRequestId") Long leaveRequestId,
            @Param("updateBy") String updateBy);
    int finalizeLeaveApprovalStart(
            @Param("leaveRequestId") Long leaveRequestId,
            @Param("businessRound") Integer businessRound,
            @Param("expectedVersion") Long expectedVersion,
            @Param("instanceId") Long instanceId,
            @Param("updateBy") String updateBy);
    int cancelLeaveDraft(@Param("leaveRequestId") Long leaveRequestId,
            @Param("expectedStatus") String expectedStatus,
            @Param("expectedVersion") Long expectedVersion,
            @Param("reason") String reason,
            @Param("updateBy") String updateBy);

    int insertApprovalOutbox(ApprovalOutbox value);
    ApprovalOutbox selectApprovalOutboxById(@Param("outboxId") Long id);
    ApprovalOutbox selectApprovalOutboxByIdForUpdate(
            @Param("outboxId") Long id);
    ApprovalOutbox selectApprovalOutboxByRound(
            @Param("leaveRequestId") Long leaveRequestId,
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
