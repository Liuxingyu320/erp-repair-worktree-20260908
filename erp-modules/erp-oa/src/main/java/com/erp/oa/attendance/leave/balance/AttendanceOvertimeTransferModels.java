package com.erp.oa.attendance.leave.balance;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.erp.oa.attendance.domain.AttendanceModels.DayResult;

/** Source confirmation is separate from the early-leave offset ledger. */
public final class AttendanceOvertimeTransferModels
{
    private AttendanceOvertimeTransferModels() { }
    public static class Transfer {
        @JsonSerialize(using=ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) public Long transferId, originalTransferId, bucketId;
        @JsonSerialize(using=ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) public Long userId, shopId, leaveTypeId, legalEntityId, ownerDeptId;
        @JsonSerialize(using=ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) public Long sourceDayResultId, sourceScheduleId, sourceVersion;
        @JsonSerialize(using=ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) public Long ruleId, mappingId, mappingVersion, operatorUserId;
        public String action, clientRequestId, requestFingerprint, reason, salaryMonth;
        public LocalDate sourceBusinessDate;
        public LocalDateTime sourceSettledAt, createTime;
        public Integer sourceWorkedMinutes, sourceScheduledMinutes, transferMinutes;
        public boolean reversed;
    }
    public static class Source {
        @JsonSerialize(using=ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) public Long dayResultId,scheduleId,userId,shopId,rowVersion;
        public String userName,resultStatus;
        public Integer workedMinutes,scheduledMinutes;
        public LocalDate businessDate;
        public LocalDateTime settledAt;
        public static Source from(DayResult d){Source s=new Source();s.dayResultId=d.dayResultId;s.scheduleId=d.scheduleId;s.userId=d.userId;s.shopId=d.shopId;s.rowVersion=d.rowVersion;s.userName=d.userName;s.resultStatus=d.resultStatus;s.workedMinutes=d.workedMinutes;s.scheduledMinutes=d.scheduledMinutes;s.businessDate=d.businessDate;s.settledAt=d.settledAt;return s;}
    }
    public static class Context {
        public Source source;
        public int rawOvertimeMinutes, offsetMinutes, transferredMinutes;
        public Integer availableMinutes;
        public boolean salaryLocked;
        public String status, reason;
        public AttendanceLeaveBalanceModels.Balance balance;
        public List<Transfer> history;
    }
}
