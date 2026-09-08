package com.erp.oa.attendance.correction;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import com.erp.common.core.web.domain.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;

/** Persistence models for attendance correction requests and approval delivery. */
public final class AttendanceCorrectionModels
{
    private AttendanceCorrectionModels() { }

    public static class CorrectionRequest extends BaseEntity
    {
        private static final long serialVersionUID = 1L;
        public Long correctionRequestId;
        public String correctionRequestNo;
        public String clientRequestId;
        @JsonIgnore
        public String clientRequestFingerprint;
        public Long userId;
        public String userName;
        public Long shopId;
        public Long scheduleId;
        public LocalDate businessDate;
        public String correctionType;
        public String targetPunchType;
        public Long targetScheduleSegmentSnapshotId;
        public String targetPunchSlotKey;
        public Integer targetSegmentOrder;
        public String targetSegmentLabelSnapshot;
        public Long originalPunchEventId;
        public LocalDateTime originalPunchTime;
        public String originalPunchType;
        public LocalDateTime requestedPunchTime;
        public String reason;
        @JsonIgnore
        public String attachmentRefs;
        public String status;
        public Integer businessRound;
        public Long approvalInstanceId;
        public Long rowVersion;
        public String lastApprovalEventKey;
        public LocalDateTime submittedAt;
        public LocalDateTime approvedAt;
        public LocalDateTime rejectedAt;
        public LocalDateTime cancelledAt;
    }

    /** Immutable schedule snapshot fields used to validate a correction. */
    public static class ScheduleRef
    {
        public Long scheduleId;
        public Long userId;
        public String userName;
        public Long shopId;
        public LocalDate businessDate;
        public String status;
        public String shiftNameSnapshot;
        public String punchModeSnapshot;
        public LocalTime startTimeSnapshot;
        public LocalTime endTimeSnapshot;
        public Boolean crossDaySnapshot;
        public Integer checkInOpenMinutesSnapshot;
        public Integer checkInCloseMinutesSnapshot;
        public Integer checkOutOpenMinutesSnapshot;
        public Integer checkOutCloseMinutesSnapshot;
        public List<PunchSlotRef> punchSlots = new ArrayList<>();
    }

    /** Minimal immutable punch event projection; source events are never updated. */
    public static class PunchEventRef
    {
        public Long punchEventId;
        public Long scheduleId;
        public Long userId;
        public Long shopId;
        public LocalDate businessDate;
        public String punchType;
        public Long scheduleSegmentSnapshotId;
        public String punchSlotKey;
        public Integer segmentOrder;
        public String segmentLabel;
        public LocalDateTime serverPunchTime;
        public String verificationStatus;
    }

    /** Minimal immutable work-segment projection for slot-targeted corrections. */
    public static class ScheduleSegmentRef
    {
        public Long scheduleSegmentSnapshotId;
        public Long scheduleId;
        public Integer segmentOrder;
        public String segmentType;
        public Integer startMinuteOffset;
        public Integer endMinuteOffset;
    }

    /** Server-derived correction target; clients must submit both identifiers. */
    public static class PunchSlotRef
    {
        public String punchSlotKey;
        public String punchType;
        public Long scheduleSegmentSnapshotId;
        public Integer segmentOrder;
        public String segmentLabel;
        public LocalDateTime startAt;
        public LocalDateTime endAt;
        public LocalDateTime opensAt;
        public LocalDateTime closesAt;
        public String status;
        public boolean completed;
        public boolean eligibleForMissingPunch;
        public boolean correctionPending;
        public boolean coveredByApprovedLeave;
        public boolean requiresRemainingWorkConfirmation;
        public Long punchEventId;
    }

    public static class ApprovalOutbox
    {
        public Long outboxId;
        public Long correctionRequestId;
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
