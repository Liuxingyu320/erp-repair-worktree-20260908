package com.erp.oa.attendance.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class AttendanceV2SegmentPunchSchemaContractTest
{
    private static final String MIGRATION =
            "erp_oa_attendance_v2_20260820.sql";

    @Test
    void shipsOneIdenticalExpandOnlyMigrationThroughAllPaths()
            throws Exception
    {
        String release = readRepoFile("sql/" + MIGRATION);
        String docker = readRepoFile("docker/mysql/db/" + MIGRATION);
        String module = readRepoFile(
                "erp-modules/erp-oa/src/main/resources/db/migration/"
                        + MIGRATION);

        assertThat(docker).isEqualTo(release);
        assertThat(module).isEqualTo(release);
        assertThat(release).contains(
                "punch_mode varchar(24) NOT NULL DEFAULT 'SHIFT_BOUNDARY'",
                "COMMENT 'SHIFT_BOUNDARY/PER_WORK_SEGMENT'",
                "punch_mode_snapshot varchar(24) NOT NULL DEFAULT 'SHIFT_BOUNDARY'",
                "schedule_segment_snapshot_id bigint(20) DEFAULT NULL",
                "punch_slot_key varchar(64) DEFAULT NULL",
                "target_schedule_segment_snapshot_id bigint(20) DEFAULT NULL",
                "target_punch_slot_key varchar(64) DEFAULT NULL",
                "idx_oa_attendance_challenge_slot",
                "idx_oa_attendance_punch_slot",
                "idx_oa_attendance_correction_slot");
    }

    @Test
    void existingRowsKeepBoundaryModeAndNullableSlotIdentity()
            throws Exception
    {
        String sql = readRepoFile("sql/" + MIGRATION);

        assertThat(sql).contains(
                "ADD COLUMN punch_mode varchar(24) NOT NULL DEFAULT ''SHIFT_BOUNDARY''",
                "ADD COLUMN punch_mode_snapshot varchar(24) NOT NULL DEFAULT ''SHIFT_BOUNDARY''",
                "ADD COLUMN schedule_segment_snapshot_id bigint(20) DEFAULT NULL",
                "ADD COLUMN punch_slot_key varchar(64) DEFAULT NULL",
                "ADD COLUMN target_schedule_segment_snapshot_id bigint(20) DEFAULT NULL",
                "ADD COLUMN target_punch_slot_key varchar(64) DEFAULT NULL",
                "FROM information_schema.columns",
                "FROM information_schema.statistics")
                .doesNotContain(
                        "UPDATE oa_attendance_shift SET punch_mode",
                        "UPDATE oa_attendance_schedule SET punch_mode_snapshot",
                        "UPDATE oa_attendance_punch_event SET punch_slot_key",
                        "DELETE FROM oa_attendance_punch_event");
    }

    private String readRepoFile(String relativePath) throws Exception
    {
        Path base = Paths.get(System.getProperty("user.dir"));
        Path path = base.resolve(relativePath);
        if (!Files.exists(path))
        {
            path = base.resolve("../..").resolve(relativePath).normalize();
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
