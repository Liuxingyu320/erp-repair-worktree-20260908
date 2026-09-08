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

@DisplayName("V2调拨差异责任与短缺损失台账 Mapper")
class
        InvTransferReceiptDiscrepancyAdjudicationAtomicLedgerMapperBindingTest
{
    private static final String NAMESPACE =
            "com.erp.inventory.mapper."
                    + "InvTransferReceiptDiscrepancyAdjudicationAtomicLedgerMapper";
    private static final String XML = "mapper/inventory/"
            + "InvTransferReceiptDiscrepancyAdjudicationAtomicLedgerMapper.xml";

    @Test
    @DisplayName("两个窄插入方法全部绑定到可解析XML")
    void shouldBindBothAppendMethods() throws Exception
    {
        Configuration configuration = new Configuration();
        configuration.addMapper(
                InvTransferReceiptDiscrepancyAdjudicationAtomicLedgerMapper
                        .class);
        try (InputStream input = Resources.getResourceAsStream(XML))
        {
            new XMLMapperBuilder(input, configuration, XML,
                    configuration.getSqlFragments()).parse();
        }

        assertThat(Arrays.stream(
                InvTransferReceiptDiscrepancyAdjudicationAtomicLedgerMapper
                        .class.getDeclaredMethods())
                .map(method -> NAMESPACE + "." + method.getName()))
                .allMatch(configuration::hasStatement)
                .containsExactlyInAnyOrder(
                        NAMESPACE + ".insertResponsibilityLedger",
                        NAMESPACE + ".insertShortageLossLedger");
    }

    @Test
    @DisplayName("条件插入重核成本证据与版本且不触碰库存")
    void shouldKeepBothLedgersAppendOnlyAndAnchored() throws Exception
    {
        String xml = normalized();
        String responsibility = between(xml,
                "<insert id=\"insertresponsibilityledger\"",
                "</insert>");
        String loss = between(xml,
                "<insert id=\"insertshortagelossledger\"",
                "</insert>");

        assertCommonAnchors(responsibility);
        assertCommonAnchors(loss);
        assertMatchingProjectionWidth(responsibility);
        assertMatchingProjectionWidth(loss);
        assertThat(responsibility).contains(
                "inv_transfer_receipt_discrepancy_responsibility_ledger",
                "x.action_type = 'responsibility_adjustment'",
                "x.coverage_kind = #{effect.execution.coveragekind}",
                "x.coverage_kind = 'responsibility'",
                "x.coverage_kind = 'resolution'",
                "c.discrepancy_type in ('shortage', 'damaged')",
                "#{effect.ledgerkind} = 'responsibility_ledger'",
                "prior.action_version_before = #{effect.execution.executionversionbefore}");
        assertThat(loss).contains(
                "inv_transfer_receipt_discrepancy_shortage_loss_ledger",
                "'transport_shortage'",
                "x.action_type = 'transport_loss_write_off'",
                "x.coverage_kind = 'resolution'",
                "x.coverage_kind = #{effect.execution.coveragekind}",
                "c.discrepancy_type = 'shortage'",
                "#{effect.ledgerkind} = 'shortage_loss_ledger'",
                "prior.action_version_before = #{effect.execution.executionversionbefore}");

        assertThat(count(xml, "<insert ")).isEqualTo(2);
        assertThat(xml).doesNotContain(
                "<select ", "<update ", "<delete ",
                " inv_stock ", " inv_stock_balance_detail ",
                " inv_inventory_lot ", " inv_inventory_location ",
                " inv_inventory_serial ", " inv_stock_ledger_detail ",
                " inv_transfer_order ", " finance");
    }

    private static void assertCommonAnchors(String sql)
    {
        assertThat(sql).contains(
                "inner join inv_transfer_receipt_discrepancy_adjudication a",
                "inner join inv_transfer_receipt_discrepancy_adjudication_action x",
                "inner join inv_transfer_shipment_receipt_allocation ra",
                "c.case_version = #{effect.execution.caseversionbefore}",
                "c.status = #{effect.execution.casestatusbefore}",
                "a.plan_status = #{effect.execution.planstatusbefore}",
                "a.decision_fingerprint = #{effect.execution.decisionfingerprint}",
                "a.source_cost_price = #{effect.execution.sourcecostprice}",
                "x.action_quantity = #{effect.execution.quantity}",
                "x.action_amount = #{effect.execution.amount}",
                "x.execution_status = 'pending'",
                "x.execution_version = #{effect.execution.executionversionbefore}",
                "x.execution_effect_reference is null",
                "#{effect.execution.command} = 'dispatch'",
                "#{effect.execution.actionstatusafter} = 'completed'",
                "#{effect.execution.requiredpermission} = 'inv:transfer:discrepancy:execute'",
                "from inv_transfer_receipt_discrepancy_adjudication_execution_event event",
                "event.request_id = #{effect.execution.requestid}",
                "event.action_version_before = #{effect.execution.executionversionbefore}",
                "event.action_version_after = #{effect.execution.executionversionafter}",
                "event.effect_kind = #{effect.execution.effectkind}",
                "event.event_fingerprint = #{effect.execution.eventfingerprint}",
                "length(trim(a.adjudication_note)) between 1 and 500",
                "length(trim(x.action_note)) between 1 and 500",
                "length(trim(a.evidence_refs)) between 1 and 2000",
                "prior.request_id = #{effect.execution.requestid}");
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

    private static void assertMatchingProjectionWidth(String statement)
    {
        int columnsFrom = statement.indexOf('(');
        int selectFrom = statement.indexOf(") select ", columnsFrom);
        int tableFrom = statement.indexOf(" from ", selectFrom + 9);
        assertThat(columnsFrom).isGreaterThanOrEqualTo(0);
        assertThat(selectFrom).isGreaterThan(columnsFrom);
        assertThat(tableFrom).isGreaterThan(selectFrom);

        String columns = statement.substring(columnsFrom + 1, selectFrom);
        String projection = statement.substring(selectFrom + 9, tableFrom);
        assertThat(count(projection, ",")).isEqualTo(count(columns, ","));
    }
}
