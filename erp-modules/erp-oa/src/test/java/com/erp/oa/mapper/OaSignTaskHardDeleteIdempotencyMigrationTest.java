package com.erp.oa.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("签约任务硬删除持久幂等迁移")
class OaSignTaskHardDeleteIdempotencyMigrationTest
{
    private static final String MIGRATION =
            "erp_oa_sign_task_hard_delete_idempotency_20260720.sql";

    @Test
    @DisplayName("三份迁移一致且在文件清理台账之后初始化")
    void shouldShipOneForwardOnlyMigrationThroughEveryDeploymentPath() throws Exception
    {
        String sql = read("sql/" + MIGRATION);
        assertThat(read("docker/mysql/db/" + MIGRATION)).isEqualTo(sql);
        assertThat(read("erp-modules/erp-oa/src/main/resources/db/migration/" + MIGRATION))
                .isEqualTo(sql);

        String bootstrap = read("docker/mysql/bootstrap-files.list");
        assertThat(bootstrap.lines().filter(MIGRATION::equals).count()).isEqualTo(1L);
        assertThat(bootstrap.indexOf(MIGRATION)).isGreaterThan(
                bootstrap.indexOf("erp_oa_sign_file_cleanup_20260720.sql"));
        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS oa_sign_task_hard_delete_operation")
                .contains("UNIQUE KEY uk_oa_sign_task_hard_delete_request (request_id)")
                .contains("administrator_user_id bigint NOT NULL")
                .contains("payload_hash char(64) NOT NULL")
                .contains("claim_token varchar(64) DEFAULT NULL")
                .contains("lease_expires_time datetime DEFAULT NULL")
                .contains("processed_count int NOT NULL DEFAULT 0")
                .contains("result_json json NOT NULL")
                .contains("version bigint NOT NULL DEFAULT 0")
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
