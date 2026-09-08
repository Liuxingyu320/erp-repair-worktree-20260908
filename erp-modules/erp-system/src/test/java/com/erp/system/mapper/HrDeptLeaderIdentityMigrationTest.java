package com.erp.system.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HrDeptLeaderIdentityMigrationTest
{
    @Test
    void migrationCopiesAreIdempotentAndNeverGuessLegacyLeaderIdentity() throws Exception
    {
        String sql=Files.readString(repoFile("sql/erp_hr_dept_leader_identity_20260713.sql"),StandardCharsets.UTF_8);
        String docker=Files.readString(repoFile("docker/mysql/db/erp_hr_dept_leader_identity_20260713.sql"),StandardCharsets.UTF_8);

        assertThat(docker).isEqualTo(sql);
        assertThat(sql).contains("information_schema.columns","leader_user_id",
                "information_schema.statistics","idx_sys_dept_leader_user_id","PREPARE");
        assertThat(sql.toLowerCase()).doesNotContain("update sys_dept","insert into sys_dept","delete from sys_dept");
        assertThat(sql).contains("历史负责人待人工确认");
    }

    private static Path repoFile(String relative)
    {
        Path current=Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while(current!=null&&!Files.exists(current.resolve("erp-modules")))current=current.getParent();
        if(current==null)throw new IllegalStateException("repository root not found");
        return current.resolve(relative);
    }
}
