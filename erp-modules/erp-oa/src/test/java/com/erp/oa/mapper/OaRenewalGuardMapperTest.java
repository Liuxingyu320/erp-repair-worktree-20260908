package com.erp.oa.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("OA续签权威门闩Mapper")
class OaRenewalGuardMapperTest
{
    @Test
    @DisplayName("消费必须锁门闩且ACTIVE绑定和终态释放使用精确条件")
    void shouldLockActivateAndReleaseAuthoritativeGuard() throws Exception
    {
        String xml = Files.readString(repoFile(
                "erp-modules/erp-oa/src/main/resources/mapper/oa/"
                        + "OaHrRenewalGuardMapper.xml"), StandardCharsets.UTF_8);

        assertThat(xml).contains(
                "<select id=\"selectForUpdate\"",
                "from sys_hr_renewal_guard",
                "for update",
                "<update id=\"activate\"",
                "status = 'ACTIVE'",
                "status = 'RESERVED'",
                "action_id = #{actionId}",
                "task_id = #{taskId}",
                "<update id=\"rebindActive\"",
                "action_id = #{expectedActionId}",
                "task_id = #{expectedTaskId}",
                "<update id=\"release\"",
                "status = 'IDLE'",
                "action_id = null",
                "task_id = null");
    }

    @Test
    @DisplayName("IDLE自愈必须原子证明当前action为最早未结本地outbox")
    void shouldReserveIdleOnlyForEarliestRecoverableLocalAction() throws Exception
    {
        String xml = Files.readString(repoFile(
                "erp-modules/erp-oa/src/main/resources/mapper/oa/"
                        + "OaHrRenewalGuardMapper.xml"), StandardCharsets.UTF_8);
        int start = xml.indexOf(
                "<update id=\"reserveIdleForEarliestRecoverableAction\"");
        int end = xml.indexOf("</update>", start);
        assertThat(start).isGreaterThanOrEqualTo(0);
        String claim = xml.substring(start, end);

        assertThat(claim).contains(
                "<update id=\"reserveIdleForEarliestRecoverableAction\"",
                "status = 'IDLE'",
                "action_id is null",
                "task_id is null",
                "version = #{expectedVersion}",
                "a.action_id = #{actionId}",
                "a.employee_id = #{employeeId}",
                "a.action_type in ('RENEWAL_CONFIRMED', 'RENEWAL_DECLINED')",
                "inner join sys_hr_sign_event_outbox o",
                "PENDING", "RETRY", "SENDING", "DEAD",
                "older.action_id &lt; a.action_id");
        assertThat(claim).doesNotContain("o.outbox_id is null");
        assertThat(xml).contains(
                "<update id=\"releaseReserved\"",
                "status = 'RESERVED'",
                "action_id = #{actionId}",
                "task_id is null");
    }

    @Test
    @DisplayName("有效续签任务查询锁行并仅排除五个终态")
    void shouldLockAnyNonTerminalRenewalTask() throws Exception
    {
        String xml = Files.readString(repoFile(
                "erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignTaskMapper.xml"),
                StandardCharsets.UTF_8);
        int start = xml.indexOf("<select id=\"selectOpenRenewalTaskForUpdate\"");
        int end = xml.indexOf("</select>", start);
        assertThat(start).isGreaterThanOrEqualTo(0);
        String query = xml.substring(start, end);

        assertThat(query).contains(
                "scenario = 'RENEWAL'", "employee_id = #{employeeId}",
                "SIGNED", "REFUSED", "EXPIRED", "CANCELLED", "NO_ACTION",
                "for update");
        assertThat(query).doesNotContain(
                "WAITING_HR_CONFIRM'", "PENDING_SIGN'");
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
