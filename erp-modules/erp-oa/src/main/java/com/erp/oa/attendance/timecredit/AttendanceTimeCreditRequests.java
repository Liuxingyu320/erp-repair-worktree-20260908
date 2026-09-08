package com.erp.oa.attendance.timecredit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class AttendanceTimeCreditRequests
{
    private AttendanceTimeCreditRequests() { }

    public static class Apply
    {
        @NotNull @Positive public Long sourceDayResultId;
        @NotNull @Positive public Long targetDayResultId;
        @NotNull @Positive public Integer adjustmentMinutes;
        @NotBlank @Size(max = 64) public String clientRequestId;
        @NotBlank @Size(max = 500) public String reason;
    }

    public static class Reverse
    {
        @NotBlank @Size(max = 64) public String clientRequestId;
        @NotBlank @Size(max = 500) public String reason;
    }
}
