package com.erp.oa.attendance.leave;

import java.time.LocalDateTime;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public final class AttendanceLeaveRequests
{
    private AttendanceLeaveRequests() { }

    public static class SaveDraft
    {
        public Long leaveRequestId;
        @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:-]{7,63}")
        public String clientRequestId;
        @NotNull @Positive public Long leaveTypeId;
        @NotNull public LocalDateTime startTime;
        @NotNull public LocalDateTime endTime;
        @NotBlank @Size(max = 1000) public String reason;
        public Long rowVersion;
    }

    public static class Submit
    {
        @NotNull @PositiveOrZero public Long rowVersion;
    }

    public static class Cancel
    {
        @NotNull @PositiveOrZero public Long rowVersion;
        @NotBlank @Size(max = 500) public String reason;
    }

    public static class TypeStatus
    {
        @NotBlank
        @Pattern(regexp = "ENABLED|DISABLED")
        public String status;
        @NotNull @PositiveOrZero public Long rowVersion;
    }

    public static class AttachmentDelete
    {
        @NotNull @PositiveOrZero public Long rowVersion;
    }
}
