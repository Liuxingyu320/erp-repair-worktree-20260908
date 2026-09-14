package com.erp.oa.attendance.leave.balance;

import java.math.BigDecimal;
import com.erp.oa.attendance.leave.balance.AttendanceLeaveBalanceModels.Rule;

public final class AttendanceLeaveBalanceRequests
{
    private AttendanceLeaveBalanceRequests() { }
    public static class RuleDraft extends Rule
    {
        public Long previousRuleId;
    }
    public static class Version
    {
        public Long rowVersion;
    }
    public static class Recalculate
    {
        public Long leaveTypeId;
    }
    public static class Adjustment
    {
        public Long leaveTypeId, bucketId;
        public Long bucketVersion, ruleId;
        public Integer ruleVersion;
        public String displayUnit;
        public BigDecimal minutesPerDay;
        public String clientRequestId, reason;
        /** Client must echo the displayed bucket/rule/version and exact conversion it reviewed. */
        public BigDecimal amount;
    }
}
