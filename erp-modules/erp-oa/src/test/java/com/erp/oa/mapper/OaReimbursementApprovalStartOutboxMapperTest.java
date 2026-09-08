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
import com.erp.oa.domain.OaReimbursement;
import com.erp.oa.domain.OaReimbursementExportBatch;
import com.erp.oa.domain.OaReimbursementInvoice;
import com.erp.oa.domain.OaReimbursementItem;
import com.erp.oa.domain.vo.OaReimbursementApprovalStartOutboxQuery;
import com.erp.oa.domain.vo.OaReimbursementApprovalStartOutboxVo;

class OaReimbursementApprovalStartOutboxMapperTest
{
    private static final String OUTBOX_XML =
            "mapper/oa/OaReimbursementApprovalStartOutboxMapper.xml";
    private static final String REIMBURSEMENT_XML =
            "mapper/oa/OaReimbursementMapper.xml";

    @Test
    void bindsEveryOutboxStatementAndKeepsOpsProjectionSafe()
            throws Exception
    {
        Configuration configuration = parse(OUTBOX_XML, false);
        String namespace = OaReimbursementApprovalStartOutboxMapper.class
                .getName() + ".";

        for (String statement : new String[] { "insertOutbox", "selectById",
                "selectByIdForUpdate", "selectByReimbursementRound",
                "selectDueOutboxes", "selectOpsOutboxes", "selectSummary",
                "selectScopedByIdForUpdate",
                "claimForSubmitting", "markRemoteSucceeded", "markRetry",
                "markFailed", "markSucceeded", "replayFailedScoped" })
        {
            assertThat(configuration.hasStatement(namespace + statement))
                    .as("mapped statement %s", statement).isTrue();
        }

        OaReimbursementApprovalStartOutboxQuery params =
                new OaReimbursementApprovalStartOutboxQuery();
        params.setStatus("FAILED");
        params.getParams().put("dataScope", "");
        String opsSql = normalized(configuration.getMappedStatement(
                namespace + "selectOpsOutboxes").getBoundSql(params).getSql());
        assertThat(opsSql)
                .contains("idempotency_key", "last_error_code",
                        "manual_replay_count", "remote_business_round")
                .doesNotContain("request_json", "reimbursement_version");
        assertThat(OaReimbursementApprovalStartOutboxVo.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("requestJson", "reimbursementVersion");

        String mapperXml = normalized(resourceText(OUTBOX_XML))
                .toLowerCase();
        assertThat(mapperXml).contains(
                "remote_business_round = #{remotebusinessround}",
                "case when o.remote_instance_id is not null and o.remote_business_round = o.business_round then 'remote_succeeded' else 'retry' end",
                "coalesce(o.last_error_code, '') not in (",
                "'remote_round_mismatch'", "'instance_conflict'",
                "'reimbursement_not_found'", "'reimbursement_state_changed'",
                "'reimbursement_version_conflict'",
                "from oa_reimbursement_approval_start_outbox o left join oa_reimbursement p on p.reimbursement_id = o.reimbursement_id",
                "select o.* from oa_reimbursement_approval_start_outbox o inner join oa_reimbursement p on p.reimbursement_id = o.reimbursement_id",
                "left join sys_dept d on d.dept_id = p.shop_dept_id",
                "${params.datascope}", "${query.params.datascope}",
                "for update");
    }

    @Test
    void reimbursementFinalizationUsesExactStateRoundVersionAndInstanceCas()
            throws Exception
    {
        Configuration configuration = parse(REIMBURSEMENT_XML, true);
        String namespace = OaReimbursementMapper.class.getName() + ".";

        Map<String, Object> finalizeParams = Map.of(
                "reimbursementId", 88L,
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
                "reimbursementId", 88L,
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
    void reimbursementMigrationCreatesDurableUniqueOutboxInfrastructure()
            throws Exception
    {
        Path migrationPath = Path.of(System.getProperty("user.dir"))
                .resolve("sql/erp_oa_reimbursement_20260730.sql");
        if (!Files.exists(migrationPath))
        {
            migrationPath = Path.of(System.getProperty("user.dir"))
                    .resolve("../..").resolve(
                            "sql/erp_oa_reimbursement_20260730.sql")
                    .normalize();
        }
        String migration = Files.readString(migrationPath,
                StandardCharsets.UTF_8);
        String normalized = normalized(migration).toLowerCase();

        assertThat(normalized)
                .contains("create table if not exists oa_reimbursement_approval_start_outbox (",
                        "unique key uk_oa_reimbursement_approval_round (reimbursement_id, business_round)",
                        "unique key uk_oa_reimbursement_approval_idempotency (idempotency_key)",
                        "key idx_oa_reimbursement_approval_dispatch (status, next_retry_time, update_time)",
                        "remote_business_round int default null",
                        "reimbursement_version bigint(20) not null",
                        "version bigint(20) not null default 0",
                        "'oa:reimbursement:approvalstartoutbox:list'",
                        "'oa:reimbursement:approvalstartoutbox:replay'",
                        "role_key = 'admin'")
                .doesNotContain(
                        "drop table oa_reimbursement_approval_start_outbox");
    }

    private static Configuration parse(String resource,
            boolean registerReimbursementAlias) throws Exception
    {
        Configuration configuration = new Configuration();
        if (registerReimbursementAlias)
        {
            configuration.getTypeAliasRegistry().registerAlias(
                    "OaReimbursement", OaReimbursement.class);
            configuration.getTypeAliasRegistry().registerAlias(
                    "OaReimbursementItem", OaReimbursementItem.class);
            configuration.getTypeAliasRegistry().registerAlias(
                    "OaReimbursementInvoice", OaReimbursementInvoice.class);
            configuration.getTypeAliasRegistry().registerAlias(
                    "OaReimbursementExportBatch",
                    OaReimbursementExportBatch.class);
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
