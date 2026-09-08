package com.erp.system.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

@DisplayName("公告发布工作流持久化契约")
class SysNoticeWorkflowMapperSourceTest
{
    @Test
    @DisplayName("状态迁移使用行锁、期望状态和版本围栏")
    void lifecycleWritesShouldUseRowLockAndCompareAndSetFence() throws Exception
    {
        String xml = classpath("mapper/system/SysNoticeMapper.xml");

        assertThat(xml)
                .contains("<select id=\"selectnoticebyidforupdate\"")
                .contains("where notice_id = #{noticeid} for update")
                .contains("where notice_id = #{notice.noticeid}")
                .contains("and lifecycle_status = #{expectedlifecycle}")
                .contains("and version = #{notice.version}")
                .contains("where notice_id = #{noticeid} and lifecycle_status = 'draft'");
    }

    @Test
    @DisplayName("收件箱、未读和已读均以不可变接收人快照及有效期为边界")
    void inboxQueriesShouldRequirePublishedRecipientSnapshot() throws Exception
    {
        String readXml = classpath("mapper/system/SysNoticeReadMapper.xml");

        assertThat(readXml)
                .contains("from sys_notice_recipient nr")
                .contains("where nr.notice_id = #{noticeid} and nr.user_id = #{userid}")
                .contains("n.lifecycle_status = 'published'")
                .contains("n.expire_time is null or n.expire_time &gt; sysdate()")
                .contains("inner join sys_notice_recipient nr on nr.notice_id = r.notice_id and nr.user_id = r.user_id");
    }

    @Test
    @DisplayName("受众解析只选择启用账号并在SQL与服务层双重去重")
    void audienceResolutionShouldUseActiveUsersAndDistinctRules() throws Exception
    {
        String audienceXml = classpath("mapper/system/SysNoticeAudienceMapper.xml");
        String recipientXml = classpath("mapper/system/SysNoticeRecipientMapper.xml");

        assertThat(audienceXml)
                .contains("select distinct u.user_id")
                .contains("where u.del_flag = '0' and u.status = '0'")
                .contains("exists (select 1 from sys_user_role ur")
                .contains("find_in_set(#{audience.targetid}, d.ancestors)");
        assertThat(recipientXml)
                .contains("insert into sys_notice_recipient")
                .contains("(#{noticeid}, #{userid}, #{deliveredtime}, #{recipientsource})");
    }

    @Test
    @DisplayName("迁移默认关闭开关、不给角色自动扩权且回滚有数据护栏")
    void migrationShouldBeFailClosedAndRollbackGuarded() throws Exception
    {
        String migration = repository("sql/erp_system_notice_workflow_20260714.sql");
        String rollback = repository("sql/erp_system_notice_workflow_rollback_20260714.sql");

        assertThat(migration)
                .contains("primary key (notice_id, user_id)")
                .contains("'feature.system.notice-workflow.enabled', 'false'")
                .contains("'system:notice:publish'")
                .doesNotContain("insert into sys_role_menu", "insert ignore into sys_role_menu");
        assertThat(rollback)
                .contains("recipient_source <> 'legacy_migration'")
                .contains("lifecycle_status in ('draft', 'scheduled')")
                .contains("if new_workflow_rows > 0 then")
                .contains("signal sqlstate '45000'")
                .doesNotContain("&gt;");
    }

    private static String classpath(String path) throws Exception
    {
        return normalize(new String(new ClassPathResource(path).getInputStream().readAllBytes(),
                StandardCharsets.UTF_8));
    }

    private static String repository(String relative) throws Exception
    {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null && !Files.exists(current.resolve("erp-modules")))
        {
            current = current.getParent();
        }
        if (current == null)
        {
            throw new IllegalStateException("repository root not found");
        }
        return normalize(Files.readString(current.resolve(relative), StandardCharsets.UTF_8));
    }

    private static String normalize(String source)
    {
        return source.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }
}
