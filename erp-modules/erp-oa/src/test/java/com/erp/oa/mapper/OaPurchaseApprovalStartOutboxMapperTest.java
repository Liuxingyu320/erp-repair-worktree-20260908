package com.erp.oa.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import com.erp.oa.domain.OaPurchase;
import com.erp.oa.domain.vo.OaPurchaseApprovalStartOutboxQuery;
import com.erp.oa.domain.vo.OaPurchaseApprovalStartOutboxVo;

class OaPurchaseApprovalStartOutboxMapperTest
{
    private static final String OUTBOX_XML =
            "mapper/oa/OaPurchaseApprovalStartOutboxMapper.xml";
    private static final String PURCHASE_XML =
            "mapper/oa/OaPurchaseMapper.xml";

    @Test
    void bindsEveryOutboxStatementAndKeepsOpsProjectionSafe()
            throws Exception
    {
        Configuration configuration = parse(OUTBOX_XML, false);
        String namespace = OaPurchaseApprovalStartOutboxMapper.class
                .getName() + ".";

        for (String statement : new String[] { "insertOutbox", "selectById",
                "selectByIdForUpdate", "selectByPurchaseRound",
                "selectDueOutboxes", "selectOpsOutboxes", "selectSummary",
                "selectScopedByIdForUpdate",
                "claimForSubmitting", "markRemoteSucceeded", "markRetry",
                "markFailed", "markSucceeded", "replayFailedScoped" })
        {
            assertThat(configuration.hasStatement(namespace + statement))
                    .as("mapped statement %s", statement).isTrue();
        }

        OaPurchaseApprovalStartOutboxQuery params =
                new OaPurchaseApprovalStartOutboxQuery();
        params.setStatus("FAILED");
        params.getParams().put("dataScope", "");
        String opsSql = normalized(configuration.getMappedStatement(
                namespace + "selectOpsOutboxes").getBoundSql(params).getSql());
        assertThat(opsSql)
                .contains("idempotency_key", "last_error_code",
                        "manual_replay_count", "remote_business_round")
                .doesNotContain("request_json", "purchase_version");
        assertThat(OaPurchaseApprovalStartOutboxVo.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("requestJson", "purchaseVersion");

        String mapperXml = normalized(resourceText(OUTBOX_XML))
                .toLowerCase();
        assertThat(mapperXml).contains(
                "remote_business_round = #{remotebusinessround}",
                "case when o.remote_instance_id is not null and o.remote_business_round = o.business_round then 'remote_succeeded' else 'retry' end",
                "coalesce(o.last_error_code, '') not in (",
                "'remote_round_mismatch'", "'instance_conflict'",
                "'purchase_not_found'", "'purchase_state_changed'",
                "'purchase_version_conflict'",
                "from oa_purchase_approval_start_outbox o left join oa_purchase p on p.purchase_id = o.purchase_id",
                "select o.* from oa_purchase_approval_start_outbox o inner join oa_purchase p on p.purchase_id = o.purchase_id",
                "left join sys_dept d on d.dept_id = p.shop_dept_id",
                "${params.datascope}", "${query.params.datascope}",
                "for update");
    }

    @Test
    void purchaseFinalizationUsesExactStateRoundVersionAndInstanceCas()
            throws Exception
    {
        Configuration configuration = parse(PURCHASE_XML, true);
        String namespace = OaPurchaseMapper.class.getName() + ".";

        Map<String, Object> finalizeParams = Map.of(
                "purchaseId", 88L,
                "businessRound", 3,
                "expectedVersion", 7L,
                "instanceId", 990L,
                "updateBy", "approval-outbox");
        String finalizeSql = normalized(configuration.getMappedStatement(
                namespace + "finalizeApprovalStart")
                .getBoundSql(finalizeParams).getSql());
        assertThat(finalizeSql)
                .contains("status = 'pending'", "approval_instance_id = ?",
                        "status = 'submitting'", "approval_round = ?",
                        "row_version = ?", "approval_instance_id is null",
                        "row_version = row_version + 1");

        Map<String, Object> submitParams = Map.of(
                "purchaseId", 88L,
                "expectedStatus", "draft",
                "expectedVersion", 6L,
                "businessRound", 3,
                "updateBy", "applicant");
        String submitSql = normalized(configuration.getMappedStatement(
                namespace + "markApprovalSubmitting")
                .getBoundSql(submitParams).getSql());
        assertThat(submitSql)
                .contains("status = 'submitting'",
                        "approval_instance_id = null",
                        "last_approval_event_key = null",
                        "status = ?", "row_version = ?",
                        "row_version = row_version + 1");
    }

    @Test
    void migrationCreatesOnlyDurableUniqueOutboxInfrastructure()
            throws Exception
    {
        String migration = Files.readString(
                Path.of("../../sql/erp_oa_purchase_approval_start_outbox_20260716.sql"),
                StandardCharsets.UTF_8);
        String normalized = normalized(migration).toLowerCase();

        assertThat(normalized)
                .contains("create table if not exists `oa_purchase_approval_start_outbox`",
                        "unique key `uk_oa_purchase_approval_round` (`purchase_id`, `business_round`)",
                        "unique key `uk_oa_purchase_approval_idempotency` (`idempotency_key`)",
                        "key `idx_oa_purchase_approval_dispatch` (`status`, `next_retry_time`, `update_time`)",
                        "`remote_business_round` int default null",
                        "`purchase_version` bigint not null",
                        "`version` bigint not null default 0",
                        "from information_schema.columns",
                        "column_name = 'remote_business_round'",
                        "alter table oa_purchase_approval_start_outbox add column remote_business_round int default null")
                .doesNotContain("insert into", "insert ignore into");
    }

    private static Configuration parse(String resource,
            boolean registerPurchaseAlias) throws Exception
    {
        Configuration configuration = new Configuration();
        if (registerPurchaseAlias)
        {
            configuration.getTypeAliasRegistry().registerAlias(
                    "OaPurchase", OaPurchase.class);
        }
        try (InputStream input = Resources.getResourceAsStream(resource))
        {
            new XMLMapperBuilder(input, configuration, resource,
                    configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    private static String normalized(String value)
    {
        return value.replaceAll("\\s+", " ").trim();
    }

    private static String resourceText(String resource) throws Exception
    {
        try (InputStream input = Resources.getResourceAsStream(resource))
        {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
