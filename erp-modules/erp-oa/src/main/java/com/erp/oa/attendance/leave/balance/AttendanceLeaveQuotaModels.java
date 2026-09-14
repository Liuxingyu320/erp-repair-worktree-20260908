package com.erp.oa.attendance.leave.balance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/** Immutable request policy evidence and per-round allocation facts. */
public final class AttendanceLeaveQuotaModels
{
    private AttendanceLeaveQuotaModels() { }
    public static class PolicySnapshot
    {
        public Integer schemaVersion = 1;
        @JsonSerialize(using=ToStringSerializer.class) @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) public Long leaveTypeId, leaveTypeVersion, ruleId, mappingId, mappingVersion, legalEntityId;
        public Integer ruleVersion;
        public String unitMode, amountUnit, displayUnit;
        public Boolean balanceRequired;
        public BigDecimal minutesPerDay;
    }
    /** Preview of one exact proposed edit; never a persisted request or an entitlement grant. */
    public static class PolicyPreview
    {
        @JsonSerialize(using=ToStringSerializer.class) @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class)
        public Long leaveRequestId, rowVersion, userId, shopId, leaveTypeId;
        public LocalDateTime startTime, endTime;
        public String reason;
        public BigDecimal requestedDays;
        public Integer totalMinutes;
        @JsonSerialize(using=ToStringSerializer.class) @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class)
        public Long quotaUnits;
        public PolicySnapshot quotaPolicySnapshot;
    }
    public static class Allocation
    {
        @JsonSerialize(using=ToStringSerializer.class) @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) public Long allocationId, leaveRequestId, accountId, bucketId, userId, leaveTypeId;
        public Integer businessRound;
        @JsonSerialize(using=ToStringSerializer.class) @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) public long units;
        public String status;
        public LocalDate sourceExpiresOn;
        public LocalDateTime createTime, updateTime;
    }
    public static class Event
    {
        @JsonSerialize(using=ToStringSerializer.class) @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) public Long eventId, leaveRequestId;
        public Integer businessRound;
        public String eventKey, action, fingerprint, sourceJson;
        @JsonSerialize(using=ToStringSerializer.class) @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) public long units;
        public LocalDateTime createTime;
    }
    public static class DayLock
    {
        public Long dayResultId, shopId;
        public LocalDate businessDate;
    }
}
