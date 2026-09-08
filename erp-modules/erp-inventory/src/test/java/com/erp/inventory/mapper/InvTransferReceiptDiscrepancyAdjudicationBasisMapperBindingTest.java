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

@DisplayName("V2调拨差异裁决不透明依据签发 Mapper")
class InvTransferReceiptDiscrepancyAdjudicationBasisMapperBindingTest
{
    private static final String NAMESPACE =
            "com.erp.inventory.mapper.InvTransferReceiptDiscrepancyAdjudicationBasisMapper";
    private static final String XML =
            "mapper/inventory/InvTransferReceiptDiscrepancyAdjudicationBasisMapper.xml";

    @Test
    @DisplayName("签发、令牌行锁和条件消费三个窄方法绑定到可解析XML")
    void shouldBindThreeNarrowMethods() throws Exception
    {
        Configuration configuration = new Configuration();
        configuration.addMapper(
                InvTransferReceiptDiscrepancyAdjudicationBasisMapper.class);
        try (InputStream input = Resources.getResourceAsStream(XML))
        {
            new XMLMapperBuilder(input, configuration, XML,
                    configuration.getSqlFragments()).parse();
        }

        assertThat(Arrays.stream(
                InvTransferReceiptDiscrepancyAdjudicationBasisMapper.class
                        .getDeclaredMethods())
                .map(method -> NAMESPACE + "." + method.getName()))
                .containsExactlyInAnyOrder(
                        NAMESPACE + ".insertIssuedBasis",
                        NAMESPACE + ".selectByTokenHashForUpdate",
                        NAMESPACE + ".consumeIssuedBasis")
                .allMatch(configuration::hasStatement);
    }

    @Test
    @DisplayName("消费只锁令牌摘要并条件绑定已写裁决")
    void shouldKeepAtomicHashedConsumptionBoundary() throws Exception
    {
        String xml = normalized();
        String insert = between(xml,
                "<insert id=\"insertissuedbasis\"", "</insert>");
        String select = between(xml,
                "<select id=\"selectbytokenhashforupdate\"", "</select>");
        String update = between(xml,
                "<update id=\"consumeissuedbasis\"", "</update>");

        assertThat(insert).contains(
                "keyproperty=\"generatedid.value\"",
                "insert into inv_transfer_receipt_discrepancy_adjudication_basis",
                "#{basis.tokenhash}",
                "c.case_version = #{basis.caseversion}",
                "c.fact_fingerprint = #{basis.factfingerprint}",
                "c.status = 'awaiting_confirmation'",
                "source_event.decision = 'confirmed'",
                "target_event.decision = 'confirmed'",
                "source_event.operator_user_id &lt;&gt;",
                "target_event.operator_user_id &lt;&gt;",
                "max(source_latest.confirmation_event_id)",
                "max(target_latest.confirmation_event_id)",
                "#{basis.requiredpermission} = 'inv:transfer:discrepancy:adjudicate'",
                "#{basis.selectedshopdeptid} in",
                "collection=\"basis.scopedeptids\"",
                "#{basis.tokenhash} regexp '^[a-f0-9]{64}$'",
                "#{basis.scopedigest} regexp '^[a-f0-9]{64}$'",
                "#{basis.expiresat} = date_add( #{basis.issuedat}, interval 5 minute)",
                "prior.token_hash = #{basis.tokenhash}");
        assertThat(insert)
                .doesNotContain("#{basis.token}", "basis_token",
                        "raw_token");
        assertThat(select).contains(
                "where token_hash = #{tokenhash}",
                "#{tokenhash} regexp '^[a-f0-9]{64}$'",
                "for update");
        assertThat(update).contains(
                "set basis_status = 'consumed'",
                "consumed_request_id = #{consumption.requestid}",
                "consumed_adjudication_id = #{consumption.adjudicationid}",
                "basis.basis_status = 'issued'",
                "basis.consumed_at is null",
                "basis.consumed_request_id is null",
                "basis.consumed_adjudication_id is null",
                "#{consumption.consumedat} &gt;= #{consumption.issuedat}",
                "#{consumption.consumedat} &lt; #{consumption.expiresat}",
                "exists ( select 1 from inv_transfer_receipt_discrepancy_adjudication adjudication",
                "adjudication.request_id = #{consumption.requestid}",
                "adjudication.case_version_after = #{consumption.caseversion} + 1",
                "adjudication.fact_fingerprint = #{consumption.factfingerprint}",
                "adjudication.adjudicator_name = #{consumption.adjudicatorname}",
                "adjudication.required_permission = #{consumption.requiredpermission}",
                "adjudication.plan_status = 'adjudication_planned'");
        assertThat(select + update).doesNotContain(
                "basis_token", "raw_token", "#{consumption.token}");
        assertThat(count(xml, "<insert ")).isEqualTo(1);
        assertThat(count(xml, "<update ")).isEqualTo(1);
        assertThat(count(xml, "<delete ")).isZero();
        assertThat(count(xml, "<select ")).isEqualTo(1);
        assertThat(xml).doesNotContain(
                "insert into inv_transfer_receipt_discrepancy_adjudication_action",
                "update inv_transfer_receipt_discrepancy_case",
                "inv_stock ", "inv_stock_lot ", "finance", "ledger");
    }

    private static String normalized() throws Exception
    {
        try (InputStream input = Resources.getResourceAsStream(XML))
        {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .toLowerCase().replaceAll("\\s+", " ");
        }
    }

    private static String between(String source, String start, String end)
    {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from);
        assertThat(from).isGreaterThanOrEqualTo(0);
        assertThat(to).isGreaterThan(from);
        return source.substring(from, to + end.length());
    }

    private static int count(String value, String token)
    {
        return value.split(java.util.regex.Pattern.quote(token), -1).length
                - 1;
    }
}
