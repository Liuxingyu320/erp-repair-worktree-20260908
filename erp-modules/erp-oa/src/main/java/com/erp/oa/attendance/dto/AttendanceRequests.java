package com.erp.oa.attendance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AttendanceRequests
{
    private AttendanceRequests() { }

    public static class StatusChange
    {
        @NotBlank public String status;
        @NotNull public Long rowVersion;
    }

    public static class ScheduleBatch
    {
        @NotNull @Positive public Long shopId;
        @NotEmpty @Valid public List<ScheduleItem> items = new ArrayList<>();
    }

    public static class ScheduleItem
    {
        public Long scheduleId;
        @NotNull @Positive public Long userId;
        public String userName;
        @NotNull public LocalDate businessDate;
        @NotNull @Positive public Long shiftId;
        /** Fixed enabled attendance site required by the V2 geofence policy. */
        @NotNull @Positive public Long siteId;
        public Long rowVersion;
    }

    public static class SchedulePublish
    {
        @NotNull @Positive public Long shopId;
        @NotEmpty public List<Long> scheduleIds = new ArrayList<>();
    }

    public static class DaySettlementCommand
    {
        @NotNull @Positive public Long shopId;
        @NotNull public LocalDate dateFrom;
        @NotNull public LocalDate dateTo;
        /** Reopen and recompute rows that already have settled_at. */
        public boolean recalculate;
    }

    public static class ChallengeCreate
    {
        @NotNull @Positive public Long scheduleId;
        @NotBlank public String punchType;
        @Size(max = 64) public String punchSlotKey;
    }

    public static class ChallengeView
    {
        public String challengeToken;
        public LocalDateTime expiresAt;
        public Long scheduleId;
        public String punchType;
        public String punchSlotKey;

        public ChallengeView(String challengeToken, LocalDateTime expiresAt,
                Long scheduleId, String punchType)
        {
            this.challengeToken = challengeToken;
            this.expiresAt = expiresAt;
            this.scheduleId = scheduleId;
            this.punchType = punchType;
        }

        public ChallengeView(String challengeToken, LocalDateTime expiresAt,
                Long scheduleId, String punchType, String punchSlotKey)
        {
            this(challengeToken, expiresAt, scheduleId, punchType);
            this.punchSlotKey = punchSlotKey;
        }
    }

    public static class PunchCommand
    {
        public String challengeToken;
        public String punchType;
        @Size(max = 64) public String punchSlotKey;
        public BigDecimal latitude;
        public BigDecimal longitude;
        public BigDecimal accuracyMeters;
        public String clientCoordinateSystem;
        public LocalDateTime clientCaptureTime;
        public String clientRequestId;
        public String deviceId;
        public String appVersion;
        public String clientIp;
        public String userAgent;
    }

    /**
     * Identifies one already-dispatched punch without putting the one-time
     * challenge token in the URL or server access logs.
     */
    public static class PunchStatusRequest
    {
        @NotBlank
        @Size(min = 8, max = 64)
        @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:-]{7,63}")
        public String clientRequestId;

        @NotBlank
        @Size(min = 64, max = 64)
        @Pattern(regexp = "[0-9a-f]{64}")
        public String challengeToken;
    }
}
