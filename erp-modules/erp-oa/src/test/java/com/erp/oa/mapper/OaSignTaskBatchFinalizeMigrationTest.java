package com.erp.oa.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("批量选公司盖章权限迁移")
class OaSignTaskBatchFinalizeMigrationTest
{
    private static final String MIGRATION =
            "erp_oa_sign_task_batch_finalize_20260720.sql";

    @Test
    @DisplayName("独立权限迁移三份一致并排在菜单修复之后")
    void shouldShipDedicatedPermissionAfterTaskCenterRepair() throws Exception
    {
        String sql = readRepoFile("sql/" + MIGRATION);
        String dockerSql = readRepoFile("docker/mysql/db/" + MIGRATION);
        String moduleSql = readRepoFile(
                "erp-modules/erp-oa/src/main/resources/db/migration/" + MIGRATION);
        String bootstrap = readRepoFile("docker/mysql/bootstrap-files.list");

        assertThat(dockerSql).isEqualTo(sql);
        assertThat(moduleSql).isEqualTo(sql);
        assertThat(bootstrap.lines().filter(MIGRATION::equals).count()).isEqualTo(1L);
        assertThat(bootstrap.indexOf(MIGRATION)).isGreaterThan(
                bootstrap.indexOf("erp_oa_sign_menu_permission_repair_20260716.sql"));
        assertThat(sql)
                .contains("(9670, '批量选公司盖章'")
                .contains("'oa:signTask:batchFinalize'")
                .contains("BINARY role.role_key = BINARY 'sign_single_hr'")
                .contains("INSERT IGNORE INTO sys_role_menu")
                .contains("INSERT INTO sys_sign_hr_menu_grant")
                .contains("SIGNAL SQLSTATE '45000'")
                .doesNotContain("DELETE FROM sys_role_menu")
                .doesNotContain("UPDATE oa_sign_")
                .doesNotContain("DELETE FROM oa_sign_");
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
