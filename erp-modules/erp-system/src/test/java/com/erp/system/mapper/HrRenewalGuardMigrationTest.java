package com.erp.system.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("续签任务权威门闩迁移")
class HrRenewalGuardMigrationTest
{
    @Test
    @DisplayName("标准与Docker迁移一致并定义员工场景唯一门闩")
    void shouldMirrorAuthoritativeGuardMigration() throws Exception
    {
        String standard = Files.readString(repoFile(
                "sql/erp_hr_renewal_guard_20260712.sql"), StandardCharsets.UTF_8);
        String docker = Files.readString(repoFile(
                "docker/mysql/db/erp_hr_renewal_guard_20260712.sql"),
                StandardCharsets.UTF_8);

        assertThat(docker).isEqualTo(standard);
        assertThat(standard).contains(
                "CREATE TABLE IF NOT EXISTS sys_hr_renewal_guard",
                "PRIMARY KEY (employee_id, scenario)",
                "status varchar(16) NOT NULL DEFAULT 'IDLE'",
                "action_id bigint DEFAULT NULL",
                "task_id bigint DEFAULT NULL",
                "version bigint NOT NULL DEFAULT 0");
    }

    @Test
    @DisplayName("迁移回填未投递action与已有OA非终态且OA表缺失时安全")
    void shouldBackfillRecoverableStateAndGuardOptionalOaTable() throws Exception
    {
        String sql = Files.readString(repoFile(
                "sql/erp_hr_renewal_guard_20260712.sql"), StandardCharsets.UTF_8);

        assertThat(sql).contains(
                "RENEWAL_CONFIRMED", "RENEWAL_DECLINED",
                "PENDING", "RETRY", "SENDING", "DEAD",
                "information_schema.tables", "oa_sign_task",
                "PREPARE", "EXECUTE", "DEALLOCATE PREPARE",
                "SIGNED", "REFUSED", "EXPIRED", "CANCELLED", "NO_ACTION",
                "RESERVED", "ACTIVE", "MIN(task_id)",
                "older.action_id < a.action_id");
        assertThat(sql).doesNotContain("version = version + 1");
    }

    @Test
    @DisplayName("System预留必须排除同员工其他未结final action且保留IDLE版本CAS")
    void shouldReserveOnlyWhenNoOtherFinalRenewalActionIsUnsettled() throws Exception
    {
        String xml = Files.readString(repoFile(
                "erp-modules/erp-system/src/main/resources/mapper/system/"
                        + "SysHrRenewalGuardMapper.xml"), StandardCharsets.UTF_8);
        int start = xml.indexOf("<update id=\"reserve\"");
        int end = xml.indexOf("</update>", start);
        assertThat(start).isGreaterThanOrEqualTo(0);
        String reserve = xml.substring(start, end);

        assertThat(reserve).contains(
                "status = 'IDLE'",
                "action_id is null",
                "task_id is null",
                "version = #{expectedVersion}",
                "and not exists (",
                "from sys_hr_lifecycle_action pending",
                "left join sys_hr_sign_event_outbox pending_o",
                "pending.employee_id = #{employeeId}",
                "pending.action_id &lt;&gt; #{actionId}",
                "pending.action_type in ('RENEWAL_CONFIRMED', 'RENEWAL_DECLINED')",
                "pending_o.outbox_id is null",
                "PENDING", "RETRY", "SENDING", "DEAD");
        assertThat(reserve).doesNotContain("'SENT'");
    }

    private static Path repoFile(String relativePath)
    {
        Path base = Paths.get(System.getProperty("user.dir"));
        Path path = base.resolve(relativePath);
        if (!Files.exists(path))
        {
            path = base.resolve("../..").resolve(relativePath).normalize();
        }
        return path;
    }
}
