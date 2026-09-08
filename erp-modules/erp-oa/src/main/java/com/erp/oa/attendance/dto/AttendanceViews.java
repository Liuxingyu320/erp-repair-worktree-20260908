package com.erp.oa.attendance.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.erp.oa.attendance.domain.AttendanceModels.DayResult;
import com.erp.oa.attendance.domain.AttendanceModels.PunchEvent;
import com.erp.oa.attendance.domain.AttendanceModels.Schedule;

public final class AttendanceViews
{
    private AttendanceViews() { }

    public static class TodayContext
    {
        public String state;
        public boolean canPunch;
        public String lockReason;
        public String punchModeSnapshot;
        public String allowedPunchType;
        public String allowedPunchSlotKey;
        public List<PunchSlotView> punchSlots = new ArrayList<>();
        public PunchSlotView nextPunchSlot;
        public LocalDateTime serverTime;
        public Schedule schedule;
        public PunchEvent latestPunch;
        public Long latestEvidenceId;
        public DayResult dayResult;
    }

    public static class PunchSlotView
    {
        public String punchSlotKey;
        public String punchType;
        public Long scheduleSegmentSnapshotId;
        public Integer segmentOrder;
        public String segmentLabel;
        public LocalDateTime startAt;
        public LocalDateTime endAt;
        public LocalDateTime opensAt;
        public LocalDateTime closesAt;
        public String status;
        public boolean completed;
        public boolean requiresRemainingWorkConfirmation;
        public Long punchEventId;
    }

    public static class RemainingWorkIntervalView
    {
        public Long scheduleId;
        public Long scheduleSegmentSnapshotId;
        public Integer segmentOrder;
        public String segmentLabel;
        public LocalDateTime remainingStart;
        public LocalDateTime remainingEnd;
        public int remainingMinutes;
        public String state;
        public com.erp.oa.attendance.domain.AttendanceModels
                .RemainingWorkConfirmation latestConfirmation;
    }

    public static class PunchResult
    {
        public boolean success;
        public PunchEvent event;
        public DayResult dayResult;
        public Long evidenceId;
    }

    /**
     * Terminal-state reconciliation response for a punch whose original HTTP
     * response may have been lost. Evidence remains represented by its private
     * identifier so storage paths and hashes are never serialized.
     */
    public static class PunchStatusResult
    {
        public String status;
        public String clientRequestId;
        public PunchEvent event;
        public Long evidenceId;
    }
}
