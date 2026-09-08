package com.erp.oa.attendance.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class AttendanceClientIdempotencyMigrationTest
{
    private static final String MIGRATION =
            "erp_oa_attendance_client_idempotency_20260822.sql";

    @Test
    void shipsOneIdenticalRepeatSafeExpandOnlyMigration() throws Exception
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
        assertThat(bootstrap.indexOf("erp_oa_attendance_v2_20260820.sql"))
                .isLessThan(bootstrap.indexOf(MIGRATION));
        assertThat(release).contains(
                "oa_attendance_leave_request ADD COLUMN client_request_id varchar(64) DEFAULT NULL",
                "oa_attendance_leave_request ADD COLUMN client_request_fingerprint char(64) DEFAULT NULL",
                "uk_oa_attendance_leave_client_request (user_id, shop_id, client_request_id)",
                "oa_attendance_correction_request ADD COLUMN client_request_id varchar(64) DEFAULT NULL",
                "oa_attendance_correction_request ADD COLUMN client_request_fingerprint char(64) DEFAULT NULL",
                "uk_oa_attendance_correction_client_request (user_id, shop_id, client_request_id)",
                "information_schema.columns",
                "information_schema.statistics")
                .doesNotContain("DROP TABLE", "DELETE FROM", "UPDATE oa_");
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
