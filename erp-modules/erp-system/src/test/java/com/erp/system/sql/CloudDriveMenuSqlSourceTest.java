package com.erp.system.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("云盘最终菜单迁移")
class CloudDriveMenuSqlSourceTest
{
    @Test
    @DisplayName("迁移注册稳定菜单与按角色键授权")
    void shouldRegisterStableCloudDriveMenusAndAdminGrant() throws Exception
    {
        String sql = Files.readString(repoRoot().resolve("sql/erp_cloud_drive_20260711.sql"), StandardCharsets.UTF_8);

        assertThat(sql).contains(
                "(9600, '云盘'",
                "'drive', 'drive/index'",
                "'drive:access'",
                "'drive:company:manage'",
                "'drive:department:manage'",
                "'drive:quota:manage'",
                "COALESCE(perms, '') <> 'drive:access'",
                "FIND_IN_SET('drive:access'",
                "role_key = 'admin'",
                "SIGNAL SQLSTATE '45000'");
        assertThat(sql).doesNotContain("VALUES\n    (1, 9600)");
    }

    @Test
    @DisplayName("system 与 file 使用同一个默认关闭开关")
    void shouldKeepSystemAndFileFeatureFlagsAligned() throws Exception
    {
        Path root = repoRoot();
        assertThat(Files.readString(root.resolve("erp-modules/erp-system/src/main/resources/application-dev.yml")))
                .contains("drive:", "enabled: ${DRIVE_ENABLED:false}");
        assertThat(Files.readString(root.resolve("erp-modules/erp-system/src/main/resources/bootstrap.yml")))
                .contains("drive:", "enabled: ${DRIVE_ENABLED:false}");
        assertThat(Files.readString(root.resolve("erp-modules/erp-file/src/main/resources/application-dev.yml")))
                .contains("drive:", "enabled: ${DRIVE_ENABLED:false}");
        assertThat(Files.readString(root.resolve("erp-modules/erp-file/src/main/resources/application-local.yml")))
                .contains("drive:", "enabled: ${DRIVE_ENABLED:false}");
    }

    private static Path repoRoot()
    {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (Files.exists(current.resolve("erp-modules/erp-system/pom.xml"))) return current;
        if (Files.exists(current.resolve("pom.xml")) && current.endsWith(Path.of("erp-modules", "erp-system")))
        {
            return current.getParent().getParent();
        }
        throw new IllegalStateException("Cannot locate repository root from " + current);
    }
}
