package com.erp.system.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SysSigningProfileSupplementMigrationTest
{
    @Test
    void migrationAddsReviewedFactsAndPrivacySafeUniqueAudit() throws Exception
    {
        Path sqlPath = Path.of(System.getProperty("user.dir"))
                .resolve("../../sql/erp_system_sign_profile_supplement_20260718.sql").normalize();
        Path dockerPath = Path.of(System.getProperty("user.dir"))
                .resolve("../../docker/mysql/db/erp_system_sign_profile_supplement_20260718.sql")
                .normalize();
        Path bootstrapList = Path.of(System.getProperty("user.dir"))
                .resolve("../../docker/mysql/bootstrap-files.list").normalize();
        assertThat(sqlPath).exists();
        assertThat(dockerPath).exists();
        String sql = Files.readString(sqlPath, StandardCharsets.UTF_8);

        assertThat(Files.readString(dockerPath, StandardCharsets.UTF_8)).isEqualTo(sql);
        assertThat(Files.readAllLines(bootstrapList, StandardCharsets.UTF_8))
                .contains("erp_system_sign_profile_supplement_20260718.sql");
        assertThat(sql).contains("student_status varchar(20)", "school_name varchar(200)",
                "retirement_status varchar(20)", "income_start_year_month char(7)",
                "CREATE TABLE IF NOT EXISTS sys_sign_profile_supplement_audit",
                "UNIQUE KEY uk_sign_profile_supplement_request (request_id)",
                "CHARACTER SET ascii COLLATE ascii_bin",
                "MODIFY COLUMN request_id varchar(64) CHARACTER SET ascii COLLATE ascii_bin",
                "request_hash char(64)", "before_hash char(64)", "after_hash char(64)")
                .doesNotContain("current_address text", "school_name text");
    }
}
