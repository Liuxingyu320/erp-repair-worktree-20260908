package com.erp.system.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HrMigrationSafetyTest
{
    @Test
    void migrationsPreserveExistingFlagsAndUseWarningFreeRoleGrants() throws Exception
    {
        assertSafeMirroredMigration("erp_hr_data_quality_20260713.sql",true);
        assertSafeMirroredMigration("erp_hr_health_certificate_20260713.sql",false);
    }

    @Test
    void preflightScriptsAreReadOnlyAndCoverRequiredChecks() throws Exception
    {
        String health=read("scripts/verify-hr-health-certificate-migration.sql");
        String leader=read("scripts/verify-hr-dept-leader-identity.sql");
        String readiness=read("scripts/hr-master-data-readiness.sql");
        for(String sql:new String[]{health,leader,readiness})
        {
            for(String line:sql.split("\\R"))
            {
                String statement=line.trim().toLowerCase();
                for(String forbidden:new String[]{"insert ","update ","delete ","alter ","drop ","create ","set "})
                    assertThat(statement).doesNotStartWith(forbidden);
            }
        }
        assertThat(health).contains("expected_column_count","uk_hr_health_user_current",
                "users_with_multiple_current_certificates","authorized_role_count");
        assertThat(leader).contains("leader_user_id_column_exists","invalid_leader_identity_nodes",
                "legacy_name_without_identity_nodes","leader_snapshot_mismatch_nodes",
                "unresolved_responsibility_node_p0_count");
        assertThat(readiness).contains("nearest_group_id","owner_dept_id",
                "GROUP node must be the root organization");
    }

    private static void assertSafeMirroredMigration(String file,boolean dataQuality) throws Exception
    {
        String sql=read("sql/"+file);
        assertThat(read("docker/mysql/db/"+file)).isEqualTo(sql);
        assertThat(sql.toLowerCase()).doesNotContain("insert ignore into sys_role_menu");
        assertThat(sql).contains("INSERT INTO sys_role_menu","AND NOT EXISTS (");
        if(dataQuality)assertThat(sql.toLowerCase()).doesNotContain("update sys_config");
        else assertThat(sql).contains("'feature.hr.health-certificate.enabled', 'false'");
    }

    private static String read(String relative) throws Exception
    {
        Path current=Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while(current!=null&&!Files.exists(current.resolve("erp-modules")))current=current.getParent();
        if(current==null)throw new IllegalStateException("repository root not found");
        return Files.readString(current.resolve(relative),StandardCharsets.UTF_8);
    }
}
