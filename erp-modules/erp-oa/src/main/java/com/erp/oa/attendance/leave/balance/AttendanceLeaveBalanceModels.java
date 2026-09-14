package com.erp.oa.attendance.leave.balance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/** Amounts are exact millionths of a minute; no national entitlement is seeded. */
public final class AttendanceLeaveBalanceModels
{
    private AttendanceLeaveBalanceModels() { }
    public static final long UNITS_PER_MINUTE = 1_000_000L;

    public static class EmployeeContext
    {
        @JsonSerialize(using=ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) public Long userId, deptId, profileId, legalEntityId;
        public String workLocation, employeeStatus, userStatus, delFlag;
        public LocalDate workStartDate, entryDate;
        public LocalDateTime profileUpdatedAt;
    }
    public static class RuleConfig
    {
        public String leaveCategory; // ANNUAL / COMPENSATORY / OTHER
        public String calculation; // FIXED / TENURE / SOURCE_ONLY
        public String tenureBasis; // WORK_START / ENTRY
        public String tenureAt; // PERIOD_START / AS_OF_DATE
        public String unit; // DAYS / HOURS / MINUTES
        public BigDecimal minutesPerDay, amount, grantStepMinutes;
        public String proration; // NONE / CALENDAR_DAYS
        public String grantTiming; // UPFRONT / EARNED_DAILY
        public String rounding; // DOWN / HALF_UP / UP
        public Integer expiryMonthsAfterYear, carryExpiryMonths;
        public BigDecimal carryLimit;
    }
    public static class Rule
    {
        @JsonSerialize(using=ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) public Long ruleId, familyId, ownerDeptId, legalEntityId, leaveTypeId, rowVersion;
        public Integer version, priority;
        public String locationCode, name, status;
        public LocalDate effectiveFrom, effectiveTo;
        @JsonSerialize(using=ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) public Long createdBy, publishedBy;
        public LocalDateTime createdAt, publishedAt;
        @JsonIgnore public String configJson;
        public RuleConfig config;
        public List<Tier> tiers = new ArrayList<>();
    }
    public static class Tier
    {
        @JsonSerialize(using=ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) public Long ruleId;
        public Integer minYears, maxYearsExclusive;
        public BigDecimal amount;
    }
    public static class LocationMapping
    {
        @JsonSerialize(using=ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) public Long mappingId, ownerDeptId, legalEntityId, rowVersion, updatedBy;
        public String workLocation, locationCode, reason;
        public LocalDateTime updatedAt;
    }
    public static class Account
    {
        @JsonSerialize(using=ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) public Long accountId, userId, leaveTypeId, rowVersion;
    }
    public static class Bucket
    {
        @JsonSerialize(using=ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) public Long bucketId, accountId, userId, leaveTypeId, ruleId, mappingId;
        @JsonSerialize(using=ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) public Long mappingVersion, ownerDeptId, legalEntityId, rowVersion;
        public String sourceKey, sourceType, contextFingerprint, expiryState;
        public String sourceProblem;
        public Integer periodYear;
        public Integer ruleVersion;
        public LocalDate expiresOn;
        @JsonSerialize(using=ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) public long grantedUnits, ruleGrantedUnits, reservedUnits, consumedUnits, expiredUnits, carriedUnits;
        public BigDecimal minutesPerDay;
        public String displayUnit;
        public LocalDateTime createdAt;
        public long availableUnits()
        {
            return Math.subtractExact(Math.subtractExact(Math.subtractExact(
                    Math.subtractExact(grantedUnits, reservedUnits), consumedUnits), expiredUnits), carriedUnits);
        }
    }
    public static class Ledger
    {
        @JsonSerialize(using=ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) public Long ledgerId, accountId, bucketId, ruleId, operatorUserId;
        public String eventKey, action, reason, contextFingerprint, sourceKey, contextJson;
        @JsonSerialize(using=ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) public long units;
        public LocalDateTime createdAt;
    }
    public static class Command
    {
        @JsonSerialize(using=ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) public Long commandId, accountId;
        public String commandKey, fingerprint;
        @JsonSerialize(using=ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) public Long bucketId;
        @JsonSerialize(using=ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) public long resultUnits;
    }
    public static class Match
    {
        public String status, reason;
        public Rule rule;
        public LocationMapping mapping;
        @JsonSerialize(using=ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) public Long expectedUnits;
        public Integer periodYear;
        public LocalDate asOfDate;
    }
    public static class Balance
    {
        @JsonSerialize(using=ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) public Long userId, leaveTypeId;
        public String status, reason;
        @JsonSerialize(using=ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) public Long ruleId;
        public Integer ruleVersion;
        @JsonSerialize(using=ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) public Long availableUnits, reservedUnits, consumedUnits;
        public String internalUnit = "MICRO_MINUTE";
        public String ruleName, displayUnit;
        public BigDecimal minutesPerDay;
        public List<Bucket> buckets = new ArrayList<>();
    }
}
