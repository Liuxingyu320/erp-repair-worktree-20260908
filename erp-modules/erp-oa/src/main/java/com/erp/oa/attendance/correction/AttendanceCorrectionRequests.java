package com.erp.oa.attendance.correction;

import java.time.LocalDateTime;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public final class AttendanceCorrectionRequests
{
    private AttendanceCorrectionRequests() { }

    public static class SaveDraft
    {
        public Long correctionRequestId;
        @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:-]{7,63}")
        public String clientRequestId;
        @NotNull @Positive public Long scheduleId;
        @NotBlank @Size(max = 24) public String correctionType;
        @NotBlank @Size(max = 8) public String targetPunchType;
        @Positive public Long targetScheduleSegmentSnapshotId;
        @Size(max = 64) public String targetPunchSlotKey;
        @Positive public Long originalPunchEventId;
        @NotNull public LocalDateTime requestedPunchTime;
        @NotBlank @Size(max = 1000) public String reason;
        public Long rowVersion;
    }

    public static class Submit
    {
        @NotNull @PositiveOrZero public Long rowVersion;
    }
}
