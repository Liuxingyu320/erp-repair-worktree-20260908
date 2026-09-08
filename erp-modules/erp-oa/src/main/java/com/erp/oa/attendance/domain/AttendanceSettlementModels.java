package com.erp.oa.attendance.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Minimal auditable projections consumed by final day settlement. */
public final class AttendanceSettlementModels
{
    private AttendanceSettlementModels() { }

    public static class CorrectionSource
    {
        public Long correctionRequestId;
        public Long scheduleId;
        public Long userId;
        public Long shopId;
        public LocalDate businessDate;
        public String correctionType;
        public Long originalPunchEventId;
        public String targetPunchType;
        public Long targetScheduleSegmentSnapshotId;
        public String targetPunchSlotKey;
        public LocalDateTime requestedPunchTime;
        public String status;
        public LocalDateTime approvedAt;
    }

    public static class LeaveRequestState
    {
        public Long leaveRequestId;
        public Long userId;
        public Long shopId;
        public LocalDateTime startTime;
        public LocalDateTime endTime;
        public String status;
    }

    public static class LeaveSegmentSource
    {
        public Long leaveSegmentId;
        public Long leaveRequestId;
        public LocalDateTime startTime;
        public LocalDateTime endTime;
        public Integer totalMinutes;
        public Integer paidMinutes;
        public Integer unpaidMinutes;
    }

    /** Immutable, append-only source consumed only by final settlement. */
    public static class RemainingWorkConfirmationSource
    {
        public Long confirmationId;
        public Long scheduleId;
        public Long userId;
        public Long shopId;
        public LocalDate businessDate;
        public LocalDateTime remainingStart;
        public LocalDateTime remainingEnd;
        public String decision;
        public LocalDateTime actualArrivalTime;
        public LocalDateTime actualDepartureTime;
        public Long supersedesConfirmationId;
        public LocalDateTime decidedAt;
    }
}
