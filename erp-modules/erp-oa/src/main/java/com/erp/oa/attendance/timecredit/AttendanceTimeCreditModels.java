package com.erp.oa.attendance.timecredit;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.erp.common.core.web.domain.BaseEntity;
import com.erp.oa.attendance.domain.AttendanceModels.DayResult;

/** DTOs for the append-only overtime-to-early-leave credit ledger. */
public final class AttendanceTimeCreditModels
{
    private AttendanceTimeCreditModels() { }

    public static class Adjustment extends BaseEntity
    {
        private static final long serialVersionUID = 1L;
        public Long adjustmentId;
        public String adjustmentNo;
        public String clientRequestId;
        public String requestFingerprint;
        public String adjustmentAction;
        public Long originalAdjustmentId;
        public Long shopId;
        public Long userId;
        public String userName;
        public String salaryMonth;
        public Long sourceDayResultId;
        public Long sourceScheduleId;
        public LocalDate sourceBusinessDate;
        public Long targetDayResultId;
        public Long targetScheduleId;
        public LocalDate targetBusinessDate;
        public Integer adjustmentMinutes;
        public String reason;
        public Long operatorUserId;
        public String operatorName;
        public LocalDateTime createTime;
        public Boolean reversed;
        public Long reversalAdjustmentId;
        public String reversalReason;
        public String reversalOperatorName;
        public LocalDateTime reversedAt;
    }

    public static class SourceCandidate
    {
        public Long dayResultId;
        public Long scheduleId;
        public LocalDate businessDate;
        public Integer scheduledMinutes;
        public Integer workedMinutes;
        public Integer rawOvertimeMinutes;
        public Integer usedMinutes;
        public Integer transferredMinutes;
        public Integer availableMinutes;
    }

    public static class Context
    {
        public DayResult target;
        public Integer targetRawEarlyLeaveMinutes;
        public Integer targetOffsetMinutes;
        public Integer targetRemainingEarlyLeaveMinutes;
        public Boolean ledgerInconsistent;
        public String ledgerWarning;
        public Boolean salaryLocked;
        public List<SourceCandidate> sources = new ArrayList<>();
        public List<Adjustment> history = new ArrayList<>();
    }
}
