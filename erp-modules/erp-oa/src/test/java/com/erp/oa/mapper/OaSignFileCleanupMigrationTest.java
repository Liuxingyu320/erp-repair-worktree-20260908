package com.erp.oa.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("签约文件清理台账迁移")
class OaSignFileCleanupMigrationTest
{
    private static final String MIGRATION = "erp_oa_sign_file_cleanup_20260720.sql";

    @Test
    @DisplayName("三份迁移一致且进入自动初始化清单")
    void shouldShipOneIdenticalMigrationThroughEveryDeploymentPath() throws Exception
    {
        String sql = read("sql/" + MIGRATION);
        String dockerSql = read("docker/mysql/db/" + MIGRATION);
        String moduleSql = read("erp-modules/erp-oa/src/main/resources/db/migration/" + MIGRATION);
        String bootstrap = read("docker/mysql/bootstrap-files.list");

        assertThat(dockerSql).isEqualTo(sql);
        assertThat(moduleSql).isEqualTo(sql);
        assertThat(bootstrap.lines().filter(MIGRATION::equals).count()).isEqualTo(1L);
        assertThat(bootstrap.indexOf(MIGRATION)).isGreaterThan(
                bootstrap.indexOf("erp_oa_sign_task_batch_finalize_20260720.sql"));
        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS oa_sign_file_cleanup")
                .contains("file_references_json json NOT NULL")
                .contains("processing_token varchar(64) DEFAULT NULL")
                .contains("lease_expires_time datetime DEFAULT NULL")
                .contains("UNIQUE KEY uk_oa_sign_file_cleanup_task_package (task_id, package_id)")
                .contains("KEY idx_oa_sign_file_cleanup_due (status, next_retry_time)")
                .contains("KEY idx_oa_sign_file_cleanup_lease (status, lease_expires_time)")
                .doesNotContain("FOREIGN KEY")
                .doesNotContain("DELETE FROM oa_sign_")
                .doesNotContain("UPDATE oa_sign_");
    }

    private static String read(String relativePath) throws Exception
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
