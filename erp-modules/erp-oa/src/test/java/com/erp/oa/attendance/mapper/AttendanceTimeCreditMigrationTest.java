package com.erp.oa.attendance.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class AttendanceTimeCreditMigrationTest
{
    private static final String MIGRATION =
            "erp_oa_attendance_time_credit_20260823.sql";

    @Test
    void shipsOneIdenticalAppendOnlyMigrationWithStoreManagerPermission()
            throws Exception
    {
        String release = readRepoFile("sql/" + MIGRATION);
        String docker = readRepoFile("docker/mysql/db/" + MIGRATION);
        String module = readRepoFile(
                "erp-modules/erp-oa/src/main/resources/db/migration/"
                        + MIGRATION);
        String bootstrap = readRepoFile("docker/mysql/bootstrap-files.list");

        assertThat(docker).isEqualTo(release);
        assertThat(module).isEqualTo(release);
        assertThat(bootstrap.lines().filter(MIGRATION::equals).count())
                .isEqualTo(1L);
        assertThat(bootstrap.indexOf(
                "erp_oa_attendance_client_idempotency_20260822.sql"))
                        .isLessThan(bootstrap.indexOf(MIGRATION));
        assertThat(release).contains(
                "oa_attendance_time_credit_period_lock",
                "oa_attendance_time_credit_adjustment",
                "adjustment_action varchar(16) NOT NULL",
                "original_adjustment_id bigint(20) DEFAULT NULL",
                "uk_oa_attendance_time_credit_client",
                "uk_oa_attendance_time_credit_reverse",
                "oa:attendance:time-credit:manage",
                "r.role_key IN ('dz', 'yyjl', 'zdjl')")
                .doesNotContain("DROP TABLE", "DELETE FROM",
                        "UPDATE oa_attendance_day_result",
                        "UPDATE oa_attendance_punch_event");
    }

    private String readRepoFile(String relativePath) throws Exception
    {
        Path base = Paths.get(System.getProperty("user.dir"));
        Path path = base.resolve(relativePath);
        if (!Files.exists(path))
            path = base.resolve("../..").resolve(relativePath).normalize();
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
