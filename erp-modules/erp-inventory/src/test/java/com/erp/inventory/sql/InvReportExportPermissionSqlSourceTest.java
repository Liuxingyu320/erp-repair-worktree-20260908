package com.erp.inventory.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("库存报表导出权限迁移")
class InvReportExportPermissionSqlSourceTest
{
    @Test
    @DisplayName("迁移幂等新增独立权限且不自动授予角色")
    void shouldAddPermissionWithoutGrantingRoles() throws Exception
    {
        String sql = Files.readString(Path.of(System.getProperty("user.dir"), "..", "..",
                "sql", "erp_inventory_report_export_permission_20260810.sql").normalize(),
                StandardCharsets.UTF_8);

        assertThat(sql).contains("inv:report:export");
        assertThat(sql.toLowerCase()).contains("not exists");
        assertThat(sql).contains("inv:report:list");
        assertThat(sql).doesNotContain("sys_role_menu");
        assertThat(sql).doesNotContain("BossERP_NEW");
    }
}
