package com.erp.oa.attendance.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import com.erp.oa.attendance.approval.OaAttendanceApprovalCallbackMapper;
import com.erp.oa.attendance.payroll.AttendancePayrollMapper;
import com.erp.oa.attendance.correction.AttendanceCorrectionMapper;
import com.erp.oa.attendance.leave.AttendanceLeaveMapper;
import com.erp.oa.attendance.timecredit.AttendanceTimeCreditMapper;

class AttendanceLeaveCorrectionMapperBindingTest
{
    @Test
    void packageLocalMappersAreExplicitlyRegistered() throws Exception
    {
        assertRegistration(
                "com.erp.oa.attendance.leave.AttendanceLeaveMapperRegistration",
                AttendanceLeaveMapper.class);
        assertRegistration(
                "com.erp.oa.attendance.correction.AttendanceCorrectionMapperRegistration",
                AttendanceCorrectionMapper.class);
        assertRegistration(
                "com.erp.oa.attendance.approval.AttendanceApprovalMapperRegistration",
                OaAttendanceApprovalCallbackMapper.class);
        assertRegistration(
                "com.erp.oa.attendance.payroll.AttendancePayrollMapperRegistration",
                AttendancePayrollMapper.class);
        assertRegistration(
                "com.erp.oa.attendance.timecredit.AttendanceTimeCreditMapperRegistration",
                AttendanceTimeCreditMapper.class);
    }

    @Test
    void leaveWritesAndOutboxClaimsUseStatusAndVersionCas() throws Exception
    {
        Configuration configuration = parse(
                "mapper/oa/AttendanceLeaveMapper.xml");
        Map<String, Object> params = new HashMap<>();
        params.put("leaveRequestId", 9L);
        params.put("expectedStatus", "DRAFT");
        params.put("expectedVersion", 3L);
        params.put("businessRound", 2);
        params.put("updateBy", "tester");
        String submit = sql(configuration, AttendanceLeaveMapper.class,
                "markLeaveSubmitting", params);
        assertThat(submit).contains("status = 'SUBMITTING'",
                "business_round = ?", "status = ?", "row_version = ?");

        params.clear();
        params.put("outboxId", 7L);
        params.put("expectedStatus", "RETRY");
        params.put("expectedVersion", 4L);
        params.put("claimToken", "claim");
        String claim = sql(configuration, AttendanceLeaveMapper.class,
                "claimApprovalOutbox", params);
        assertThat(claim).contains("status = 'PROCESSING'",
                "attempt_count = attempt_count + 1", "status = ?",
                "row_version = ?");

        params.clear();
        params.put("userId", 5L);
        params.put("excludeId", 8L);
        params.put("startTime", LocalDateTime.parse("2026-08-20T08:00"));
        params.put("endTime", LocalDateTime.parse("2026-08-20T12:00"));
        String overlap = sql(configuration, AttendanceLeaveMapper.class,
                "countOverlappingLeave", params);
        assertThat(overlap).contains("status in ('DRAFT', 'SUBMITTING',",
                "start_time < ?", "end_time > ?");

        params.clear();
        params.put("userId", 5L);
        params.put("shopId", 101L);
        assertDirectAttendanceMembership(sql(configuration,
                AttendanceLeaveMapper.class,
                "selectActiveEmployeeNameInShop", params));

        params.put("clientRequestId", "leave:req:0001");
        String replay = sql(configuration, AttendanceLeaveMapper.class,
                "selectLeaveRequestByClientRequestId", params);
        assertThat(replay).contains("r.user_id = ?", "r.shop_id = ?",
                "r.client_request_id = ?", "limit 1");
        String lockedReplay = sql(configuration,
                AttendanceLeaveMapper.class,
                "selectLeaveRequestByClientRequestIdForUpdate", params);
        assertThat(lockedReplay).contains("r.client_request_id = ?",
                "limit 1 for update");

        params.put("dateFrom", LocalDate.parse("2026-08-20"));
        params.put("dateTo", LocalDate.parse("2026-08-21"));
        String scheduleRefs = sql(configuration, AttendanceLeaveMapper.class,
                "selectScheduleRefs", params);
        assertThat(scheduleRefs).contains("shop_id = ?",
                "date_sub(?, interval 1 day)", "scheduleStart",
                "scheduleEnd");

        params.put("startTime", LocalDateTime.parse("2026-08-20T12:00"));
        params.put("endTime", LocalDateTime.parse("2026-08-20T13:00"));
        String workMinutes = sql(configuration, AttendanceLeaveMapper.class,
                "calculateScheduledWorkMinutes", params);
        assertThat(workMinutes).contains("segment_type = 'WORK'",
                "timestampdiff(minute", "start_minute_offset",
                "end_minute_offset");
        String scheduleCount = sql(configuration, AttendanceLeaveMapper.class,
                "countOverlappingPublishedSchedules", params);
        assertThat(scheduleCount).contains("s.status in ('PUBLISHED', 'CHANGED')",
                "s.start_time_snapshot", "s.end_time_snapshot",
                "s.cross_day_snapshot");

        String invalidate = sql(configuration, AttendanceLeaveMapper.class,
                "invalidateDayResultsForLeaveRequest", params);
        assertThat(invalidate).contains("d.settled_at = null",
                "d.result_status = 'PENDING'",
                "LEAVE_REQUEST_REVIEW_REQUIRED",
                "where s.status in ('PUBLISHED', 'CHANGED')",
                "timestamp(s.business_date, s.start_time_snapshot)",
                "r.start_time")
                .doesNotContain("where d.settled_at is null");
    }

    @Test
    void correctionDraftAndApprovalLinkNeverOverwritePunchEvents()
            throws Exception
    {
        Configuration configuration = parse(
                "mapper/oa/AttendanceCorrectionMapper.xml");
        Map<String, Object> params = new HashMap<>();
        params.put("correctionRequestId", 9L);
        params.put("businessRound", 2);
        params.put("expectedVersion", 3L);
        params.put("instanceId", 44L);
        params.put("updateBy", "tester");
        String finalize = sql(configuration, AttendanceCorrectionMapper.class,
                "finalizeCorrectionApprovalStart", params);
        assertThat(finalize).contains("status = 'PENDING'",
                "approval_instance_id = ?", "status = 'SUBMITTING'",
                "business_round = ?", "row_version = ?",
                "approval_instance_id is null");

        params.clear();
        params.put("userId", 5L);
        params.put("scheduleId", 10L);
        params.put("targetPunchType", "IN");
        params.put("excludeId", 9L);
        String duplicate = sql(configuration,
                AttendanceCorrectionMapper.class, "countActiveCorrection",
                params);
        assertThat(duplicate).contains("schedule_id = ?",
                "target_punch_type = ?", "status in ('DRAFT',",
                "correction_request_id != coalesce(?, -1)");

        params.put("targetPunchSlotKey", "SEGMENT:91:IN");
        String duplicateSlot = sql(configuration,
                AttendanceCorrectionMapper.class,
                "countActiveCorrectionForSlot", params);
        assertThat(duplicateSlot).contains(
                "target_punch_slot_key = ?", "status in ('DRAFT',",
                "correction_request_id != coalesce(?, -1)");

        params.clear();
        params.put("userId", 5L);
        params.put("shopId", 101L);
        params.put("dateFrom", LocalDate.parse("2026-08-01"));
        params.put("dateTo", LocalDate.parse("2026-08-20"));
        String schedules = sql(configuration,
                AttendanceCorrectionMapper.class, "selectOwnedScheduleRefs",
                params);
        assertThat(schedules).contains("user_id = ?", "shop_id = ?",
                "shift_name_snapshot shiftNameSnapshot",
                "punch_mode_snapshot punchModeSnapshot",
                "status in ('PUBLISHED', 'CHANGED')");
        assertThat(schedules).doesNotContain("longitude_snapshot",
                "latitude_snapshot");

        assertDirectAttendanceMembership(sql(configuration,
                AttendanceCorrectionMapper.class,
                "selectActiveEmployeeNameInShop", params));

        params.put("clientRequestId", "correction:req:0001");
        String replay = sql(configuration, AttendanceCorrectionMapper.class,
                "selectCorrectionRequestByClientRequestId", params);
        assertThat(replay).contains("r.user_id = ?", "r.shop_id = ?",
                "r.client_request_id = ?", "limit 1");
        String lockedReplay = sql(configuration,
                AttendanceCorrectionMapper.class,
                "selectCorrectionRequestByClientRequestIdForUpdate", params);
        assertThat(lockedReplay).contains("r.client_request_id = ?",
                "limit 1 for update");

        String invalidate = sql(configuration,
                AttendanceCorrectionMapper.class,
                "invalidateDayResultForCorrection", params);
        assertThat(invalidate).contains("c.schedule_id = d.schedule_id",
                "d.settled_at = null", "d.result_status = 'PENDING'",
                "CORRECTION_REQUEST_REVIEW_REQUIRED")
                .doesNotContain("where d.settled_at is null");

        params.clear();
        params.put("scheduleId", 10L);
        params.put("scheduleSegmentSnapshotId", 91L);
        String segment = sql(configuration,
                AttendanceCorrectionMapper.class,
                "selectScheduleSegmentRef", params);
        assertThat(segment).contains(
                "schedule_segment_snapshot_id = ?", "schedule_id = ?");

        String segments = sql(configuration,
                AttendanceCorrectionMapper.class,
                "selectScheduleSegmentRefs", params);
        assertThat(segments).contains("where schedule_id = ?",
                "order by segment_order",
                "start_minute_offset startMinuteOffset",
                "end_minute_offset endMinuteOffset");

        String lockedSchedule = sql(configuration,
                AttendanceCorrectionMapper.class,
                "selectScheduleRefForUpdate", params);
        assertThat(lockedSchedule).contains("where schedule_id = ?",
                "for update");

        params.put("userId", 5L);
        params.put("shopId", 101L);
        params.put("punchSlotKey", "SEGMENT:91:IN");
        String punchSlot = sql(configuration,
                AttendanceCorrectionMapper.class,
                "countAcceptedPunchEventsForSlot", params);
        assertThat(punchSlot).contains("punch_slot_key = ?",
                "verification_status = 'ACCEPTED'");

        String approvedLeave = sql(configuration,
                AttendanceCorrectionMapper.class,
                "selectApprovedLeaveSegmentsForSchedule", params);
        assertThat(approvedLeave).contains("lr.status = 'APPROVED'",
                "ls.segment_status = 'ACTIVE'",
                "join oa_attendance_schedule sch",
                "sch.schedule_id = ?", "ls.start_time < timestamp(",
                "ls.end_time > timestamp(", "order by ls.start_time")
                .doesNotContain("where ls.schedule_id = ?");

        params.put("originalPunchEventId", 700L);
        String originalDuplicate = sql(configuration,
                AttendanceCorrectionMapper.class,
                "countActiveCorrectionForOriginalEvent", params);
        assertThat(originalDuplicate).contains(
                "original_punch_event_id = ?", "status in ('DRAFT',",
                "correction_request_id != coalesce(?, -1)");
    }

    @Test
    void approvalDecisionsReopenSettledPayrollSourcesForRecalculation()
            throws Exception
    {
        Configuration configuration = parse(
                "mapper/oa/OaAttendanceApprovalCallbackMapper.xml");
        Map<String, Object> params = new HashMap<>();
        params.put("businessId", 9L);
        params.put("decisionTime", new Date());
        String locked = sql(configuration,
                OaAttendanceApprovalCallbackMapper.class,
                "selectLeaveForUpdate", params);
        assertThat(locked).contains("user_id", "shop_id", "for update");
        String validationRead = sql(configuration,
                OaAttendanceApprovalCallbackMapper.class,
                "selectLeave", params);
        assertThat(validationRead).contains("user_id", "shop_id",
                "approval_instance_id", "business_round")
                .doesNotContain("for update");
        String attachmentCount = sql(configuration,
                OaAttendanceApprovalCallbackMapper.class,
                "countLeaveAttachments", params);
        assertThat(attachmentCount).contains(
                "from oa_attendance_leave_attachment",
                "where leave_request_id = ?");
        String leave = sql(configuration,
                OaAttendanceApprovalCallbackMapper.class,
                "invalidateLeaveDayResults", params);
        assertThat(leave).contains("d.settled_at = null",
                "LEAVE_DECISION_CHANGED",
                "where s.status in ('PUBLISHED', 'CHANGED')",
                "timestamp(s.business_date, s.start_time_snapshot)")
                .doesNotContain("where d.settled_at is null");

        String correction = sql(configuration,
                OaAttendanceApprovalCallbackMapper.class,
                "invalidateCorrectionDayResult", params);
        assertThat(correction).contains("c.schedule_id = d.schedule_id",
                "d.settled_at = null", "CORRECTION_DECISION_CHANGED")
                .doesNotContain("where d.settled_at is null");
    }

    private void assertDirectAttendanceMembership(String statement)
    {
        assertThat(statement).contains(
                "employee_dept.dept_id = u.dept_id",
                "find_in_set(target_dept.dept_id, employee_dept.ancestors)",
                "us.dept_id = target_dept.dept_id")
                .doesNotContain(
                        "find_in_set(scope_dept.dept_id, target_dept.ancestors)");
    }

    @Test
    void correctionEligibilityExcludesOnlyScopedApprovedTypeReplacements() throws Exception
    {
        var configuration = parse("mapper/oa/AttendanceCorrectionMapper.xml");
        Map<String, Object> params = Map.of("scheduleId", 31L, "userId", 9L, "punchType", "IN", "punchSlotKey", "SEGMENT:1031:IN");
        for (String method : java.util.List.of("selectSchedulePunchEvents", "countAcceptedPunchEvents", "countAcceptedPunchEventsForSlot"))
        {
            String query = sql(configuration, AttendanceCorrectionMapper.class, method, params);
            assertThat(query).contains("not exists", "moved.original_punch_event_id = e.punch_event_id",
                    "moved.schedule_id = e.schedule_id", "moved.user_id = e.user_id", "moved.shop_id = e.shop_id",
                    "moved.business_date = e.business_date", "moved.status = 'APPROVED'",
                    "moved.correction_type = 'WRONG_TYPE'", "moved.target_punch_type != e.punch_type");
        }
        assertThat(sql(configuration, AttendanceCorrectionMapper.class, "selectPunchEventRef", Map.of("punchEventId", 902L)))
                .doesNotContain("not exists");
    }

    private Configuration parse(String resource) throws Exception
    {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(resource))
        {
            new XMLMapperBuilder(input, configuration, resource,
                    configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    private void assertRegistration(String className, Class<?> mapper)
            throws Exception
    {
        MapperScan scan = Class.forName(className).getAnnotation(
                MapperScan.class);
        assertThat(scan).isNotNull();
        assertThat(scan.basePackageClasses()).contains(mapper);
    }

    private String sql(Configuration configuration, Class<?> mapper,
            String statement, Object params)
    {
        return configuration.getMappedStatement(
                mapper.getName() + "." + statement).getBoundSql(params)
                .getSql().replaceAll("\\s+", " ").trim();
    }
}
