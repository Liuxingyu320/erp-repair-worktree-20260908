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
import com.erp.inventory.domain.InvStockCheck;

@DisplayName("盘点审批发起发件箱 Mapper 绑定")
class InvStockCheckApprovalStartOutboxMapperBindingTest
{
    private static final String OUTBOX_MAPPER =
            "com.erp.inventory.mapper.InvStockCheckApprovalStartOutboxMapper";
    private static final String CHECK_MAPPER =
            "com.erp.inventory.mapper.InvStockCheckMapper";
    private static final String OUTBOX_XML =
            "mapper/inventory/InvStockCheckApprovalStartOutboxMapper.xml";
    private static final String CHECK_XML =
            "mapper/inventory/InvStockCheckMapper.xml";

    @Test
    @DisplayName("发件箱所有 Mapper 方法都有 XML 语句")
    void shouldBindEveryOutboxMapperMethod() throws Exception
    {
        Configuration configuration = new Configuration();
        parseMapper(configuration, OUTBOX_XML);

        Arrays.stream(InvStockCheckApprovalStartOutboxMapper.class
                .getDeclaredMethods()).forEach(method ->
                assertThat(configuration.hasStatement(
                        OUTBOX_MAPPER + "." + method.getName()))
                        .as(method.getName()).isTrue());
    }

    @Test
    @DisplayName("超时处理中间态和远端成功中间态都可恢复")
    void shouldBindRecoverableStatesAndClaimCas() throws Exception
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
                "set status = 'remote_succeeded'",
                "remote_business_round = #{remotebusinessround}",
                "remote_succeeded_time = sysdate()",
                "case when o.remote_instance_id is not null and o.remote_business_round = o.business_round then 'remote_succeeded' else 'retry' end",
                "coalesce(o.last_error_code, '') not in (",
                "'remote_round_mismatch'", "'instance_conflict'",
                "'stock_check_not_found'", "'stock_check_state_changed'",
                "'stock_check_version_conflict'");
    }

    @Test
    @DisplayName("列表汇总、锁定读取和重放共享店铺组织范围")
    void shouldScopeEveryOpsReadAndReplayCas() throws Exception
    {
        String xml = normalized(resourceText(OUTBOX_XML));

        assertThat(between(xml, "<select id=\"selectopsoutboxes\"",
                "</select>")).contains(
                        "left join inv_stock_check c",
                        "left join sys_dept d on d.dept_id = c.shop_dept_id",
                        "${params.datascope}");
        assertThat(between(xml, "<select id=\"selectsummary\"",
                "</select>")).contains("left join inv_stock_check c",
                        "${params.datascope}");
        assertThat(between(xml,
                "<select id=\"selectscopedbyidforupdate\"",
                "</select>")).contains("inner join inv_stock_check c",
                        "${params.datascope}", "for update");
        assertThat(between(xml, "<update id=\"replayfailedscoped\"",
                "</update>")).contains("${query.params.datascope}",
                        "o.version = #{version}");
    }

    @Test
    @DisplayName("运维查询不暴露请求快照")
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
    @DisplayName("盘点实例关联校验状态引擎轮次与 rowVersion")
    void shouldBindBusinessFinalizeCas() throws Exception
    {
        Configuration configuration = new Configuration();
        configuration.getTypeAliasRegistry().registerAlias("InvStockCheck",
                Class.forName("com.erp.inventory.domain.InvStockCheck"));
        parseMapper(configuration, CHECK_XML);
        assertThat(configuration.hasStatement(
                CHECK_MAPPER + ".finalizeNativeApprovalStart")).isTrue();

        String xml = normalized(resourceText(CHECK_XML));
        String statement = between(xml,
                "<update id=\"finalizenativeapprovalstart\">", "</update>");
        assertThat(statement).contains(
                "status = 'pending_approval'",
                "approval_engine = 'native'",
                "approval_round = #{businessround}",
                "row_version = #{expectedrowversion}",
                "approval_instance_id is null",
                "row_version = row_version + 1");

        String generalUpdate = between(xml,
                "<update id=\"updateinvstockcheck\"", "</update>");
        assertThat(generalUpdate).contains(
                "'native'.equals(approvalengine)",
                "approval_instance_id = null");

        InvStockCheck nativeSubmit = new InvStockCheck();
        nativeSubmit.setCheckId(88L);
        nativeSubmit.setStatus("pending_approval");
        nativeSubmit.setApprovalEngine("NATIVE");
        nativeSubmit.setApprovalRound(2);
        nativeSubmit.setUpdateBy("tester");
        String rendered = normalized(configuration.getMappedStatement(
                CHECK_MAPPER + ".updateInvStockCheck")
                .getBoundSql(nativeSubmit).getSql());
        assertThat(rendered).contains("approval_instance_id = null");
    }

    private static void parseMapper(Configuration configuration,
            String resource) throws Exception
    {
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
