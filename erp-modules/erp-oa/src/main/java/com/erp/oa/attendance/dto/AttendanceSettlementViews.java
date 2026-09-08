package com.erp.oa.attendance.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import com.erp.oa.attendance.domain.AttendanceModels.DayResult;

/** Manager-facing preflight and settlement responses. */
public final class AttendanceSettlementViews
{
    private AttendanceSettlementViews() { }

    public static class Preflight
    {
        public Long shopId;
        public LocalDate dateFrom;
        public LocalDate dateTo;
        public int totalSchedules;
        public int readyCount;
        public int blockedCount;
        public int settledCount;
        public List<Item> items = new ArrayList<>();
    }

    public static class Item
    {
        public Long scheduleId;
        public Long userId;
        public String userName;
        public LocalDate businessDate;
        public String state;
        public boolean ready;
        public boolean settled;
        public List<String> issueCodes = new ArrayList<>();
        public DayResult current;
        public DayResult preview;
    }

    public static class Result
    {
        public Long shopId;
        public LocalDate dateFrom;
        public LocalDate dateTo;
        public boolean recalculated;
        public int settledCount;
        public int skippedCount;
        public List<Long> skippedScheduleIds = new ArrayList<>();
        public List<DayResult> dayResults = new ArrayList<>();
    }
}
