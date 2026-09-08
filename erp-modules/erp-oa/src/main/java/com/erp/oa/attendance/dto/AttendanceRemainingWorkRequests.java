package com.erp.oa.attendance.dto;

import java.time.LocalDateTime;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class AttendanceRemainingWorkRequests
{
    private AttendanceRemainingWorkRequests() { }

    public static class Confirm
    {
        @NotNull @Positive public Long scheduleId;
        @NotNull public LocalDateTime remainingStart;
        @NotNull public LocalDateTime remainingEnd;
        @NotBlank public String decision;
        public LocalDateTime actualArrivalTime;
        public LocalDateTime actualDepartureTime;
        @NotBlank @Size(max = 500) public String reason;
    }
}
