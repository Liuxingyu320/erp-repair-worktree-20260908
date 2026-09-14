package com.erp.oa.attendance.leave;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.erp.common.core.web.domain.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;

/** Persistence models for attendance leave requests and approval delivery. */
public final class AttendanceLeaveModels
{
    private AttendanceLeaveModels() { }

    public static class LeaveType extends BaseEntity
    {
        private static final long serialVersionUID = 1L;
        public Long leaveTypeId;
        public String typeCode;
        public String typeName;
        public String unitMode;
        public String payPolicy;
        public BigDecimal paidRatio;
        public BigDecimal minutesPerDay;
        public Boolean balanceRequired;
        public Boolean attachmentRequired;
        public Integer attachmentThresholdMinutes;
        public Integer minMinutes;
        public Integer stepMinutes;
        public Integer maxMinutesPerRequest;
        public Boolean allowCrossDay;
        public Boolean approvalRequired;
        public Integer sortNo;
        public String status;
        public Long rowVersion;
    }

    public static class LeaveRequest extends BaseEntity
    {
        private static final long serialVersionUID = 1L;
        public Long leaveRequestId;
        public String leaveRequestNo;
        public String clientRequestId;
        @JsonIgnore
        public String clientRequestFingerprint;
        public Long userId;
        public String userName;
        public Long shopId;
        public Long leaveTypeId;
        public String leaveTypeCode;
        public String leaveTypeName;
        public LocalDateTime startTime;
        public LocalDateTime endTime;
        public Integer totalMinutes;
        public BigDecimal requestedDays;
        @JsonIgnore public String quotaPolicyJson;
        public com.erp.oa.attendance.leave.balance.AttendanceLeaveQuotaModels.PolicySnapshot quotaPolicySnapshot;
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(using=com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class)
        public Long quotaUnits;
        public String quotaStatus;
        public String reason;
        public String status;
        public Integer businessRound;
        public Long approvalInstanceId;
        public Long rowVersion;
        public String lastApprovalEventKey;
        public LocalDateTime submittedAt;
        public LocalDateTime approvedAt;
        public LocalDateTime rejectedAt;
        public LocalDateTime cancelledAt;
        public Integer attachmentCount;
        public List<LeaveSegment> segments = new ArrayList<>();
        public List<LeaveAttachment> attachments = new ArrayList<>();
    }

    public static class LeaveSegment
    {
        public Long leaveSegmentId;
        public Long leaveRequestId;
        public LocalDate businessDate;
        public LocalDateTime startTime;
        public LocalDateTime endTime;
        public Integer totalMinutes;
        public Integer paidMinutes;
        public Integer unpaidMinutes;
        public Long scheduleId;
        public String segmentStatus;
        public LocalDateTime createTime;
    }

    public static class LeaveAttachment
    {
        public Long attachmentId;
        public Long leaveRequestId;
        public String originalName;
        @JsonIgnore
        public String storagePath;
        public String contentType;
        public String fileExtension;
        public Long fileSize;
        public String sha256;
        public Long uploadedBy;
        public LocalDateTime createTime;
        /** Current parent version returned after an attachment mutation. */
        public Long requestRowVersion;
    }

    /** Minimal immutable schedule link used while splitting a leave request. */
    public static class ScheduleRef
    {
        public Long scheduleId;
        public Long shopId;
        public LocalDate businessDate;
        public LocalDateTime scheduleStart;
        public LocalDateTime scheduleEnd;
    }

    public static class ApprovalOutbox
    {
        public Long outboxId;
        public Long leaveRequestId;
        public Integer businessRound;
        public String idempotencyKey;
        public String requestJson;
        public String status;
        public Integer attemptCount;
        public LocalDateTime nextRetryAt;
        public String claimToken;
        public LocalDateTime claimedAt;
        public Integer lastHttpStatus;
        public String lastErrorCode;
        public String lastError;
        public Long remoteInstanceId;
        public String remoteStatus;
        public Integer remoteBusinessRound;
        public Long requestRowVersion;
        public Long rowVersion;
        public LocalDateTime remoteSucceededAt;
        public LocalDateTime completedAt;
        public String createBy;
        public LocalDateTime createTime;
        public LocalDateTime updateTime;
    }
}
