package com.erp.oa.attendance.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import com.erp.oa.attendance.domain.AttendanceModels.DayResult;
import com.erp.oa.attendance.domain.AttendanceModels.Challenge;
import com.erp.oa.attendance.domain.AttendanceModels.PunchEvent;
import com.erp.oa.attendance.domain.AttendanceModels.Shift;

class AttendanceV2MapperBindingTest
{
    private static final String XML = "mapper/oa/AttendanceV2Mapper.xml";

    @Test
    void candidateQueryBindsTheWholeWindowDateRange() throws Exception
    {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(XML))
        {
            new XMLMapperBuilder(input, configuration, XML,
                    configuration.getSqlFragments()).parse();
        }
        var params = Map.of("userId", 9L,
                "dateFrom", java.time.LocalDate.of(2026, 9, 6),
                "dateTo", java.time.LocalDate.of(2026, 9, 9));
        var bound = configuration.getMappedStatement(AttendanceV2Mapper.class.getName()
                + ".selectPublishedScheduleCandidatesForUser").getBoundSql(params);
        assertThat(normalize(bound.getSql())).contains("s.business_date between ? and ?");
        assertThat(bound.getParameterMappings()).extracting(value -> value.getProperty())
                .containsExactly("userId", "dateFrom", "dateTo");
    }

    @Test
    void challengeConsumptionAndSchedulePublishAreFailClosed()
            throws Exception
    {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(XML))
        {
            new XMLMapperBuilder(input, configuration, XML,
                    configuration.getSqlFragments()).parse();
        }
        String databaseNow = normalize(configuration.getMappedStatement(
                AttendanceV2Mapper.class.getName() + ".selectDatabaseNow")
                .getBoundSql(null).getSql());
        assertThat(databaseNow).isEqualTo("select current_timestamp");
        var clockStatement = configuration.getMappedStatement(AttendanceV2Mapper.class.getName() + ".selectDatabaseClock");
        assertThat(clockStatement.getResultMaps().get(0).getResultMappings())
                .extracting(org.apache.ibatis.mapping.ResultMapping::getProperty)
                .containsExactly("localTime", "epochMillis");
        assertThat(normalize(clockStatement.getBoundSql(null).getSql()))
                .contains("current_timestamp(3)", "unix_timestamp(current_timestamp(3))", "epoch_millis");

        Map<String, Object> params = new HashMap<>();
        params.put("challengeId", 1L);
        params.put("eventId", 2L);
        params.put("rowVersion", 0L);
        params.put("consumedAt", java.time.LocalDateTime.now());
        BoundSql consume = configuration.getMappedStatement(
                AttendanceV2Mapper.class.getName() + ".consumeChallenge")
                .getBoundSql(params);
        assertThat(normalize(consume.getSql())).contains(
                "status='ISSUED'", "consumed_at is null",
                "expires_at >= ?", "row_version=?");

        params.clear();
        params.put("scheduleId", 1L);
        params.put("shopId", 9L);
        params.put("publishedBy", 7L);
        params.put("updateBy", "tester");
        String publish = normalize(configuration.getMappedStatement(
                AttendanceV2Mapper.class.getName() + ".publishSchedule")
                .getBoundSql(params).getSql());
        assertThat(publish).contains("s.status='DRAFT'",
                "sh.status='ENABLED'", "s.shift_version=sh.version",
                "s.punch_mode_snapshot=coalesce(sh.punch_mode,'SHIFT_BOUNDARY')",
                "join oa_attendance_site st",
                "st.site_id=s.site_id and st.shop_id=s.shop_id",
                "s.site_name_snapshot=st.site_name",
                "s.longitude_snapshot=st.longitude",
                "s.coordinate_system_snapshot=st.coordinate_system",
                "s.radius_meters_snapshot=st.radius_meters",
                "s.max_accuracy_meters_snapshot=st.max_accuracy_meters",
                "st.status='ENABLED'")
                .doesNotContain("s.site_id=null");

        params.clear();
        String insertDraft = normalize(configuration.getMappedStatement(
                AttendanceV2Mapper.class.getName() + ".insertDraftSchedule")
                .getBoundSql(params).getSql());
        assertThat(insertDraft).contains("sh.shift_id,?,'DRAFT'",
                "sh.shift_name, coalesce(sh.punch_mode,'SHIFT_BOUNDARY')",
                "from oa_attendance_shift sh");

        String updateDraft = normalize(configuration.getMappedStatement(
                AttendanceV2Mapper.class.getName() + ".updateDraftSchedule")
                .getBoundSql(params).getSql());
        assertThat(updateDraft).contains("site_id=?",
                "punch_mode_snapshot=(select coalesce(punch_mode,'SHIFT_BOUNDARY')",
                "site_name_snapshot=null", "longitude_snapshot=null",
                "status='DRAFT'");

        String insertShift = normalize(configuration.getMappedStatement(
                AttendanceV2Mapper.class.getName() + ".insertShift")
                .getBoundSql(new Shift()).getSql());
        assertThat(insertShift).contains("shift_name, punch_mode, start_time",
                "coalesce(?,'SHIFT_BOUNDARY')");

        String updateShift = normalize(configuration.getMappedStatement(
                AttendanceV2Mapper.class.getName() + ".updateShift")
                .getBoundSql(new Shift()).getSql());
        assertThat(updateShift).contains(
                "punch_mode=coalesce(?,punch_mode,'SHIFT_BOUNDARY')");

        params.clear();
        params.put("userId", 7L);
        params.put("shopId", 9L);
        String activeEmployee = normalize(configuration.getMappedStatement(
                AttendanceV2Mapper.class.getName()
                        + ".countActiveEmployeeInShop")
                .getBoundSql(params).getSql());
        assertThat(activeEmployee).contains(
                "employee_dept.dept_id=u.dept_id",
                "find_in_set(target_dept.dept_id,employee_dept.ancestors)",
                "us.dept_id=target_dept.dept_id")
                .doesNotContain(
                        "find_in_set(scope_dept.dept_id,target_dept.ancestors)");

        params.clear();
        params.put("shopId", 1176L);
        String shopPath = normalize(configuration.getMappedStatement(
                AttendanceV2Mapper.class.getName()
                        + ".selectShopOrganizationPath")
                .getBoundSql(params).getSql());
        assertThat(shopPath).contains(
                "group_concat(path_dept.dept_name",
                "concat(target_dept.ancestors, ',', target_dept.dept_id)",
                "find_in_set(path_dept.dept_id",
                "separator ' / '",
                "path_dept.dept_type<>'GROUP'",
                "target_dept.dept_type='STORE'",
                "target_dept.dept_id=?")
                .doesNotContain("limit 3");

        params.clear();
        params.put("punchEventId", 41L);
        params.put("scheduleId", 31L);
        params.put("userId", 9L);
        String latestEvidence = normalize(configuration.getMappedStatement(
                AttendanceV2Mapper.class.getName()
                        + ".selectEvidenceIdForPunchInScope")
                .getBoundSql(params).getSql());
        assertThat(latestEvidence).startsWith("select e.evidence_id")
                .contains("e.punch_event_id=?", "p.schedule_id=?",
                        "p.user_id=?",
                        "p.verification_status='ACCEPTED'")
                .doesNotContain("storage_path", "sha256",
                        "watermark_payload");

        params.clear();
        params.put("scheduleId", 1L);
        params.put("shopId", 9L);
        params.put("rowVersion", 3L);
        String delete = normalize(configuration.getMappedStatement(
                AttendanceV2Mapper.class.getName() + ".deleteDraftSchedule")
                .getBoundSql(params).getSql());
        assertThat(delete).contains("shop_id=?", "status='DRAFT'",
                "row_version=?");

        params.clear();
        params.put("shiftId", 12L);
        params.put("rowVersion", 2L);
        String deleteShift = normalize(configuration.getMappedStatement(
                AttendanceV2Mapper.class.getName() + ".deleteShift")
                .getBoundSql(params).getSql());
        assertThat(deleteShift).contains("status='DISABLED'",
                "row_version=?", "not exists",
                "from oa_attendance_schedule");

        params.clear();
        params.put("siteId", 13L);
        params.put("shopId", 9L);
        params.put("rowVersion", 5L);
        String deleteSite = normalize(configuration.getMappedStatement(
                AttendanceV2Mapper.class.getName() + ".deleteSite")
                .getBoundSql(params).getSql());
        assertThat(deleteSite).contains("shop_id=?", "status='DISABLED'",
                "row_version=?", "not exists",
                "from oa_attendance_schedule");

        params.clear();
        params.put("scheduleId", 51L);
        params.put("shiftId", 12L);
        String segmentSnapshot = normalize(configuration.getMappedStatement(
                AttendanceV2Mapper.class.getName()
                        + ".insertScheduleSegmentSnapshotsFromShift")
                .getBoundSql(params).getSql());
        assertThat(segmentSnapshot).contains(
                "insert into oa_attendance_schedule_segment_snapshot",
                "from oa_attendance_shift_segment", "where shift_id=?");

        params.put("scheduleSegmentSnapshotId", 91L);
        params.put("lockRows", true);
        String segmentById = normalize(configuration.getMappedStatement(
                AttendanceV2Mapper.class.getName()
                        + ".selectScheduleSegmentSnapshotById")
                .getBoundSql(params).getSql());
        assertThat(segmentById).contains("schedule_id=?",
                "schedule_segment_snapshot_id=?", "for update");

        String insertChallenge = normalize(configuration.getMappedStatement(
                AttendanceV2Mapper.class.getName() + ".insertChallenge")
                .getBoundSql(new Challenge()).getSql());
        assertThat(insertChallenge).contains(
                "schedule_segment_snapshot_id,punch_slot_key",
                "values (?,?,?,?,?, ?,?,?,'ISSUED'");

        params.clear();
        params.put("challengeToken", "a".repeat(64));
        String challengeRead = normalize(configuration.getMappedStatement(
                AttendanceV2Mapper.class.getName()
                        + ".selectChallengeByToken")
                .getBoundSql(params).getSql());
        assertThat(challengeRead).contains("challenge_token=?")
                .doesNotContain("for update");

        params.clear();
        params.put("userId", 9L);
        params.put("clientRequestId", "attendance-request-0001");
        params.put("lockRows", false);
        String punchByRequest = normalize(configuration.getMappedStatement(
                AttendanceV2Mapper.class.getName()
                        + ".selectPunchByClientRequestForUser")
                .getBoundSql(params).getSql());
        assertThat(punchByRequest).contains("user_id=?",
                "client_request_id=?", "limit 1")
                .doesNotContain("for update");
        params.put("lockRows", true);
        String lockedPunchByRequest = normalize(configuration
                .getMappedStatement(AttendanceV2Mapper.class.getName()
                        + ".selectPunchByClientRequestForUser")
                .getBoundSql(params).getSql());
        assertThat(lockedPunchByRequest).endsWith("for update");

        String insertPunch = normalize(configuration.getMappedStatement(
                AttendanceV2Mapper.class.getName() + ".insertPunchEvent")
                .getBoundSql(new PunchEvent()).getSql());
        assertThat(insertPunch).contains(
                "schedule_segment_snapshot_id,punch_slot_key");

        params.clear();
        params.put("scheduleId", 51L);
        params.put("punchSlotKey", "SEGMENT:91:IN");
        String acceptedBySlot = normalize(configuration.getMappedStatement(
                AttendanceV2Mapper.class.getName()
                        + ".selectAcceptedPunchBySlot")
                .getBoundSql(params).getSql());
        assertThat(acceptedBySlot).contains("punch_slot_key=?",
                "verification_status='ACCEPTED'", "limit 1");

        params.clear();
        params.put("scheduleId", 51L);
        params.put("lockRows", true);
        String correctionSources = normalize(configuration.getMappedStatement(
                AttendanceV2Mapper.class.getName()
                        + ".selectSettlementCorrectionSources")
                .getBoundSql(params).getSql());
        assertThat(correctionSources).contains(
                "correction_type correctionType",
                "original_punch_event_id originalPunchEventId",
                "target_schedule_segment_snapshot_id",
                "target_punch_slot_key",
                "status in ('SUBMITTING','PENDING','APPROVED')",
                "for update");

        String dayResultUpsert = normalize(configuration.getMappedStatement(
                AttendanceV2Mapper.class.getName() + ".upsertDayResult")
                .getBoundSql(new DayResult()).getSql());
        assertThat(dayResultUpsert).contains(
                "first_in_correction_request_id",
                "last_out_correction_request_id");

        params.clear();
        params.put("scheduleId", 51L);
        params.put("updateBy", "manager");
        String remainingWorkInvalidate = normalize(configuration
                .getMappedStatement(AttendanceV2Mapper.class.getName()
                        + ".invalidateDayResultForRemainingWork")
                .getBoundSql(params).getSql());
        assertThat(remainingWorkInvalidate).contains(
                "exception_codes='REMAINING_WORK_CONFIRMATION_CHANGED'",
                "schedule_id=?", "settled_at=null", "result_status='PENDING'",
                "row_version=row_version+1")
                .doesNotContain("settled_at is null");

        params.clear();
        params.put("shopId", 9L);
        params.put("dateFrom", java.time.LocalDate.of(2026, 8, 1));
        params.put("dateTo", java.time.LocalDate.of(2026, 8, 31));
        String unfinalized = normalize(configuration.getMappedStatement(
                AttendanceV2Mapper.class.getName()
                        + ".countUnfinalizedDayResults")
                .getBoundSql(params).getSql());
        assertThat(unfinalized).contains(
                "from oa_attendance_schedule s",
                "left join oa_attendance_day_result d",
                "s.status='PUBLISHED'",
                "d.day_result_id is null or d.settled_at is null");

        params.put("userId", 7L);
        String managerResults = normalize(configuration.getMappedStatement(
                AttendanceV2Mapper.class.getName()
                        + ".selectDayResultsByShopAndRange")
                .getBoundSql(params).getSql());
        assertThat(managerResults).contains(
                "from oa_attendance_schedule s",
                "left join oa_attendance_day_result d",
                "s.shop_id=?", "s.status='PUBLISHED'", "s.user_id=?",
                "'MISSING_DAY_RESULT'");
    }

    private String normalize(String sql)
    { return sql.replaceAll("\\s+", " ").trim(); }
}
