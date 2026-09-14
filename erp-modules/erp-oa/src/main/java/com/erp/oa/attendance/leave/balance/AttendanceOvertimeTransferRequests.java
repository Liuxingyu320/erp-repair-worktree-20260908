package com.erp.oa.attendance.leave.balance;

public final class AttendanceOvertimeTransferRequests
{
    private AttendanceOvertimeTransferRequests() { }
    public static class Apply {
        public Long sourceDayResultId, sourceVersion, leaveTypeId;
        public Integer transferMinutes;
        public String clientRequestId, reason;
    }
    public static class Reverse { public String clientRequestId, reason; }
}
