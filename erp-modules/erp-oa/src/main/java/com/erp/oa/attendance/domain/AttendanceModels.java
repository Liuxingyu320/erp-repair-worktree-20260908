package com.erp.oa.attendance.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import com.erp.common.core.web.domain.BaseEntity;

/**
 * Persistence models for the rebuilt attendance domain.  They deliberately do
 * not reuse the legacy one-row-per-day attendance object.
 */
public final class AttendanceModels
{
    private AttendanceModels() { }

    /** Two representations of the same database clock sample. */
    public static class DatabaseClock
    {
        public LocalDateTime localTime;
        public Long epochMillis;
    }

    public static class Shift extends BaseEntity
    {
        private static final long serialVersionUID = 1L;
        public Long shiftId;
        public String shiftCode;
        public String shiftName;
        /** SHIFT_BOUNDARY or PER_WORK_SEGMENT. */
        public String punchMode = "SHIFT_BOUNDARY";
        public LocalTime startTime;
        public LocalTime endTime;
        public Boolean crossDay;
        public Integer standardMinutes;
        public Integer graceInMinutes;
        public Integer graceOutMinutes;
        public Integer checkInOpenMinutes;
        public Integer checkInCloseMinutes;
        public Integer checkOutOpenMinutes;
        public Integer checkOutCloseMinutes;
        public Boolean photoRequired;
        public Boolean locationRequired;
        public LocalDate effectiveFrom;
        public LocalDate effectiveTo;
        public String status;
        public Integer version;
        public Long rowVersion;
        public List<ShiftSegment> segments = new ArrayList<>();
    }

    public static class ShiftSegment
    {
        public Long segmentId;
        public Long shiftId;
        public String segmentType;
        public Integer segmentOrder;
        public Integer startMinuteOffset;
        public Integer endMinuteOffset;
        public Boolean paid;
        public LocalDateTime createTime;
    }

    public static class ScheduleSegmentSnapshot
    {
        public Long scheduleSegmentSnapshotId;
        public Long scheduleId;
        public Integer segmentOrder;
        public String segmentType;
        public Integer startMinuteOffset;
        public Integer endMinuteOffset;
        public Boolean paid;
        public LocalDateTime createTime;
    }

    public static class Site extends BaseEntity
    {
        private static final long serialVersionUID = 1L;
        public Long siteId;
        public String siteCode;
        public String siteName;
        public Long shopId;
        public String shopName;
        public String address;
        public BigDecimal longitude;
        public BigDecimal latitude;
        public String coordinateSystem;
        public Integer radiusMeters;
        public Integer maxAccuracyMeters;
        public String status;
        public Long rowVersion;
    }

    public static class Schedule extends BaseEntity
    {
        private static final long serialVersionUID = 1L;
        public Long scheduleId;
        public String scheduleNo;
        public Long userId;
        public String userName;
        public Long shopId;
        public String shopName;
        public LocalDate businessDate;
        public Long shiftId;
        public Long siteId;
        public String status;
        public Integer shiftVersion;
        public String shiftCodeSnapshot;
        public String shiftNameSnapshot;
        /** Immutable copy of the shift punch mode at publication. */
        public String punchModeSnapshot = "SHIFT_BOUNDARY";
        public LocalTime startTimeSnapshot;
        public LocalTime endTimeSnapshot;
        public Boolean crossDaySnapshot;
        public Integer standardMinutesSnapshot;
        public Integer graceInMinutesSnapshot;
        public Integer graceOutMinutesSnapshot;
        public Integer checkInOpenMinutesSnapshot;
        public Integer checkInCloseMinutesSnapshot;
        public Integer checkOutOpenMinutesSnapshot;
        public Integer checkOutCloseMinutesSnapshot;
        public Boolean photoRequiredSnapshot;
        public Boolean locationRequiredSnapshot;
        public String siteNameSnapshot;
        public String addressSnapshot;
        public BigDecimal longitudeSnapshot;
        public BigDecimal latitudeSnapshot;
        public String coordinateSystemSnapshot;
        public Integer radiusMetersSnapshot;
        public Integer maxAccuracyMetersSnapshot;
        public Long publishedBy;
        public LocalDateTime publishedAt;
        public Long cancelledBy;
        public LocalDateTime cancelledAt;
        public String changeReason;
        public Long rowVersion;
        public List<ScheduleSegmentSnapshot> segmentSnapshots =
                new ArrayList<>();
    }

    public static class Challenge
    {
        public Long challengeId;
        public String challengeToken;
        public Long scheduleId;
        public Long userId;
        public Long shopId;
        public LocalDate businessDate;
        public String punchType;
        public Long scheduleSegmentSnapshotId;
        public String punchSlotKey;
        public String status;
        public LocalDateTime issuedAt;
        public LocalDateTime expiresAt;
        public LocalDateTime consumedAt;
        public Long consumedEventId;
        public Long rowVersion;
        public LocalDateTime createTime;
    }

    public static class PunchEvent
    {
        public Long punchEventId;
        public String eventNo;
        public Long scheduleId;
        public Long challengeId;
        public Long userId;
        public String userName;
        public Long shopId;
        public LocalDate businessDate;
        public String punchType;
        public Long scheduleSegmentSnapshotId;
        public String punchSlotKey;
        public LocalDateTime serverPunchTime;
        public LocalDateTime clientCaptureTime;
        public BigDecimal longitude;
        public BigDecimal latitude;
        public BigDecimal accuracyMeters;
        public BigDecimal distanceMeters;
        public String coordinateSystem;
        public String resolvedAddress;
        public String geofenceStatus;
        public String verificationStatus;
        public String rejectionCode;
        public String rejectionMessage;
        public String clientRequestId;
        public String clientIp;
        public String userAgent;
        public String deviceId;
        public String appVersion;
        public String riskFlags;
        public LocalDateTime createTime;
        public Evidence evidence;
    }

    public static class Evidence
    {
        public Long evidenceId;
        public Long punchEventId;
        public String originalName;
        public String originalStoragePath;
        public String watermarkedStoragePath;
        public String contentType;
        public String fileExtension;
        public Long originalSize;
        public Long watermarkedSize;
        public String originalSha256;
        public String watermarkedSha256;
        public Integer imageWidth;
        public Integer imageHeight;
        public String watermarkPayload;
        public LocalDateTime capturedAt;
        public Long uploadedBy;
        public LocalDateTime createTime;
    }

    /**
     * Append-only management fact for a work interval left between approved
     * leave ranges.  It is deliberately not a punch event and never receives
     * a challenge, slot key, location or generated punch watermark.
     */
    public static class RemainingWorkConfirmation extends BaseEntity
    {
        private static final long serialVersionUID = 1L;
        public Long confirmationId;
        public String confirmationNo;
        public Long scheduleId;
        public Long userId;
        public String userName;
        public Long shopId;
        public LocalDate businessDate;
        public LocalDateTime remainingStart;
        public LocalDateTime remainingEnd;
        /** ATTENDED / ABSENT / RETURN_FOR_EVIDENCE. */
        public String decision;
        public LocalDateTime actualArrivalTime;
        public LocalDateTime actualDepartureTime;
        public String reason;
        public Long supersedesConfirmationId;
        public Long decidedBy;
        public String decidedByName;
        public LocalDateTime decidedAt;
        /** Transient response flag; false means leave ranges changed. */
        public Boolean currentInterval;
        public List<RemainingWorkAttachment> attachments = new ArrayList<>();
    }

    public static class RemainingWorkAttachment
    {
        public Long attachmentId;
        public Long confirmationId;
        public String originalName;
        public String storagePath;
        public String contentType;
        public String fileExtension;
        public Long fileSize;
        public String sha256;
        public Long uploadedBy;
        public LocalDateTime createTime;
    }

    public static class DayResult extends BaseEntity
    {
        private static final long serialVersionUID = 1L;
        public Long dayResultId;
        public Long scheduleId;
        public Long userId;
        public String userName;
        public Long shopId;
        public LocalDate businessDate;
        public Long shiftId;
        public Integer scheduledMinutes;
        public Integer workedMinutes;
        public Integer paidLeaveMinutes;
        public Integer unpaidLeaveMinutes;
        public Integer absenceMinutes;
        public Integer lateMinutes;
        public Integer earlyLeaveMinutes;
        /** Derived from worked - scheduled; the daily result itself stays raw. */
        public Integer rawOvertimeMinutes;
        /** Net APPLY - REVERSE minutes consumed from this overtime day. */
        public Integer timeCreditUsedMinutes;
        /** Independently confirmed overtime allocated to compensatory leave. */
        public Integer overtimeTransferredMinutes;
        public Integer overtimeTransferInvalid;
        public Integer netOvertimeMinutes;
        /** Net APPLY - REVERSE minutes applied to this early-leave day. */
        public Integer timeCreditOffsetMinutes;
        public Integer netEarlyLeaveMinutes;
        public String resultStatus;
        public String exceptionCodes;
        public Long firstInEventId;
        public Long lastOutEventId;
        public Long firstInCorrectionRequestId;
        public Long lastOutCorrectionRequestId;
        public Integer calculationVersion;
        public LocalDateTime calculatedAt;
        public LocalDateTime settledAt;
        public Long rowVersion;
    }

    public static class MonthlySummary
    {
        public Long userId;
        public Integer scheduledMinutes;
        public Integer workedMinutes;
        public Integer paidLeaveMinutes;
        public Integer unpaidLeaveMinutes;
        public Integer lateMinutes;
        public Integer earlyLeaveMinutes;
        public Integer absenceMinutes;
        public Integer unfinalizedCount;
    }

    public static class EmployeeOption
    {
        public Long userId;
        public String userName;
        public String employeeNo;
    }
}
