package com.erp.inventory.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("调拨审批发起发件箱 Mapper 绑定")
class InvTransferApprovalStartOutboxMapperBindingTest
{
    private static final String OUTBOX_MAPPER =
            "com.erp.inventory.mapper.InvTransferApprovalStartOutboxMapper";
    private static final String ORDER_MAPPER =
            "com.erp.inventory.mapper.InvTransferOrderMapper";
    private static final String OUTBOX_XML =
            "mapper/inventory/InvTransferApprovalStartOutboxMapper.xml";
    private static final String ORDER_XML =
            "mapper/inventory/InvTransferOrderMapper.xml";

    @Test
    @DisplayName("发件箱所有 Mapper 方法都有 XML 语句")
    void shouldBindEveryOutboxMapperMethod() throws Exception
    {
        Configuration configuration = new Configuration();
        parseMapper(configuration, OUTBOX_XML);

        Arrays.stream(InvTransferApprovalStartOutboxMapper.class
                .getDeclaredMethods()).forEach(method ->
                assertThat(configuration.hasStatement(
                        OUTBOX_MAPPER + "." + method.getName()))
                        .as(method.getName()).isTrue());
    }

    @Test
    @DisplayName("超时 SUBMITTING 和已固化远端成功都可恢复")
    void shouldBindRecoverableDueAndClaimCas() throws Exception
    {
        String xml = normalized(resourceText(OUTBOX_XML));

        assertThat(xml).contains(
                "status in ('pending', 'retry')",
                "status = 'remote_succeeded'",
                "status = 'submitting'",
                "update_time &lt;= #{stalesubmittingbefore}",
                "status = #{expectedstatus}",
                "version = #{version}",
                "set status = 'submitting'",
                "version = version + 1");
    }

    @Test
    @DisplayName("远端成功先落中间态且人工重放保留已知实例")
    void shouldPersistRemoteSuccessBeforeFinalizeAndReplaySafely()
            throws Exception
    {
        String xml = normalized(resourceText(OUTBOX_XML));

        assertThat(xml).contains(
                "set status = 'remote_succeeded'",
                "remote_instance_id = #{remoteinstanceid}",
                "remote_business_round = #{remotebusinessround}",
                "remote_succeeded_time = sysdate()",
                "case when o.remote_instance_id is not null and o.remote_business_round = o.business_round then 'remote_succeeded' else 'retry' end",
                "coalesce(o.last_error_code, '') not in (",
                "'remote_round_mismatch'", "'instance_conflict'",
                "'transfer_not_found'", "'transfer_state_changed'",
                "'transfer_version_conflict'",
                "where o.outbox_id = #{query.outboxid} and o.status = 'failed'");
    }

    @Test
    @DisplayName("列表汇总、锁定读取和重放共享发货组织范围")
    void shouldScopeEveryOpsReadAndReplayCas() throws Exception
    {
        String xml = normalized(resourceText(OUTBOX_XML));

        assertThat(between(xml, "<select id=\"selectopsoutboxes\"",
                "</select>")).contains(
                        "left join inv_transfer_order t",
                        "left join sys_dept d on d.dept_id = t.from_dept_id",
                        "${params.datascope}");
        assertThat(between(xml, "<select id=\"selectsummary\"",
                "</select>")).contains("left join inv_transfer_order t",
                        "${params.datascope}");
        assertThat(between(xml,
                "<select id=\"selectscopedbyidforupdate\"",
                "</select>")).contains("inner join inv_transfer_order t",
                        "${params.datascope}", "for update");
        assertThat(between(xml, "<update id=\"replayfailedscoped\"",
                "</update>")).contains("${query.params.datascope}",
                        "o.version = #{version}");
    }

    @Test
    @DisplayName("运维查询不暴露审批请求 JSON")
    void shouldKeepRequestSnapshotOutOfOpsProjection() throws Exception
    {
        String xml = normalized(resourceText(OUTBOX_XML));
        String ops = between(xml, "<select id=\"selectopsoutboxes\"",
                "</select>");

        assertThat(ops).doesNotContain("request_json");
        assertThat(ops).contains("last_error_code", "remote_instance_id",
                "remote_business_round");
    }

    @Test
    @DisplayName("调拨实例关联同时校验状态引擎轮次与版本")
    void shouldBindBusinessFinalizeCas() throws Exception
    {
        Configuration configuration = new Configuration();
        configuration.getTypeAliasRegistry().registerAlias(
                "InvTransferOrder",
                Class.forName("com.erp.inventory.domain.InvTransferOrder"));
        parseMapper(configuration, ORDER_XML);
        assertThat(configuration.hasStatement(
                ORDER_MAPPER + ".finalizeNativeApprovalStart")).isTrue();

        String xml = normalized(resourceText(ORDER_XML));
        String statement = between(xml,
                "<update id=\"finalizenativeapprovalstart\">", "</update>");
        assertThat(statement).contains(
                "status = 'submitted'",
                "approval_engine = 'native'",
                "approval_round = #{businessround}",
                "version = #{expectedversion}",
                "approval_instance_id is null",
                "version = version + 1");
    }

    private static void parseMapper(Configuration configuration,
            String resource) throws Exception
    {
        assertThat(InvTransferApprovalStartOutboxMapperBindingTest.class
                .getClassLoader().getResource(resource)).as(resource)
                .isNotNull();
        try (InputStream input = Resources.getResourceAsStream(resource))
        {
            new XMLMapperBuilder(input, configuration, resource,
                    configuration.getSqlFragments()).parse();
        }
    }

    private static String resourceText(String resource) throws Exception
    {
        try (InputStream input = Resources.getResourceAsStream(resource))
        {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String normalized(String value)
    {
        return value.toLowerCase().replaceAll("\\s+", " ").trim();
    }

    private static String between(String value, String start, String end)
    {
        int from = value.indexOf(start);
        assertThat(from).as(start).isGreaterThanOrEqualTo(0);
        int to = value.indexOf(end, from);
        assertThat(to).as(end).isGreaterThan(from);
        return value.substring(from, to + end.length());
    }
}
