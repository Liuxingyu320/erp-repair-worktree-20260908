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

@DisplayName("V2调拨差异裁决原子持久化 Mapper")
class InvTransferReceiptDiscrepancyAdjudicationMapperBindingTest
{
    private static final String NAMESPACE =
            "com.erp.inventory.mapper.InvTransferReceiptDiscrepancyAdjudicationMapper";
    private static final String XML =
            "mapper/inventory/InvTransferReceiptDiscrepancyAdjudicationMapper.xml";

    @Test
    @DisplayName("全部窄方法均绑定到可解析XML")
    void shouldBindEveryNarrowMethod() throws Exception
    {
        Configuration configuration = new Configuration();
        configuration.addMapper(
                InvTransferReceiptDiscrepancyAdjudicationMapper.class);
        try (InputStream input = Resources.getResourceAsStream(XML))
        {
            new XMLMapperBuilder(input, configuration, XML,
                    configuration.getSqlFragments()).parse();
        }

        assertThat(Arrays.stream(
                InvTransferReceiptDiscrepancyAdjudicationMapper.class
                        .getDeclaredMethods())
                .map(method -> NAMESPACE + "." + method.getName()))
                .allMatch(configuration::hasStatement)
                .containsExactlyInAnyOrder(
                        NAMESPACE + ".selectCaseForUpdate",
                        NAMESPACE + ".selectByRequestIdForUpdate",
                        NAMESPACE + ".selectActionsByAdjudicationId",
                        NAMESPACE + ".insertAdjudication",
                        NAMESPACE + ".insertActions",
                        NAMESPACE + ".transitionCaseToPlanned");
    }

    @Test
    @DisplayName("锁序与DML严格限定为计划持久化和条件状态推进")
    void shouldKeepAtomicPlanDmlBoundary() throws Exception
    {
        String xml = normalized();
        String caseLock = between(xml,
                "<select id=\"selectcaseforupdate\"", "</select>");
        String requestLock = between(xml,
                "<select id=\"selectbyrequestidforupdate\"", "</select>");
        String actionRead = between(xml,
                "<select id=\"selectactionsbyadjudicationid\"",
                "</select>");
        String headerInsert = between(xml,
                "<insert id=\"insertadjudication\"", "</insert>");
        String actionInsert = between(xml,
                "<insert id=\"insertactions\"", "</insert>");
        String transition = between(xml,
                "<update id=\"transitioncasetoplanned\"", "</update>");

        assertThat(caseLock).contains(
                "from inv_transfer_receipt_discrepancy_case c",
                "inner join inv_transfer_shipment_receipt_allocation ra",
                "inner join inv_transfer_shipment_receipt r",
                "inner join inv_transfer_shipment_allocation allocation",
                "inner join inv_transfer_shipment_detail sd",
                "inner join inv_transfer_shipment s",
                "inner join inv_transfer_detail td",
                "inner join inv_transfer_order o",
                "max(source_latest.confirmation_event_id)",
                "max(target_latest.confirmation_event_id)",
                "where c.discrepancy_case_id = #{caseid}",
                "<if test=\"scopedeptids != null\">",
                "collection=\"scopedeptids\"",
                "for update").doesNotContain("selecteddeptid");
        assertThat(requestLock).contains(
                "from inv_transfer_receipt_discrepancy_adjudication",
                "where request_id = #{requestid}", "for update");
        assertThat(actionRead).contains(
                "from inv_transfer_receipt_discrepancy_adjudication_action",
                "coverage_kind",
                "where adjudication_id = #{adjudicationid}",
                "order by action_sequence");

        assertThat(headerInsert).contains(
                "keyproperty=\"generatedid.value\"",
                "insert into inv_transfer_receipt_discrepancy_adjudication",
                "c.status = 'awaiting_confirmation'",
                "source_event.decision = 'confirmed'",
                "target_event.decision = 'confirmed'",
                "#{adjudication.requiredpermission} = 'inv:transfer:discrepancy:adjudicate'",
                "#{adjudication.planstatus} = 'adjudication_planned'",
                "source_event.operator_user_id &lt;&gt;",
                "target_event.operator_user_id &lt;&gt;",
                "max(source_latest.confirmation_event_id)",
                "max(target_latest.confirmation_event_id)",
                "not exists ( select 1 from inv_transfer_receipt_discrepancy_adjudication prior");
        assertThat(actionInsert).contains(
                "insert into inv_transfer_receipt_discrepancy_adjudication_action",
                "<foreach collection=\"adjudication.actions\"",
                "coverage_kind", "#{action.coveragekind}",
                "#{action.quantity}", "#{action.amount}", "'pending'");
        assertThat(transition).contains(
                "update inv_transfer_receipt_discrepancy_case",
                "set status = 'adjudication_planned'",
                "case_version = #{adjudication.caseversionafter}",
                "case_version = #{adjudication.caseversionbefore}",
                "status = 'awaiting_confirmation'",
                "persisted.decision_fingerprint =");

        assertThat(count(xml, "<insert ")).isEqualTo(2);
        assertThat(count(xml, "<update ")).isEqualTo(1);
        assertThat(count(xml, "<select ")).isEqualTo(3);
        assertThat(xml).doesNotContain("<delete ",
                "inv_transfer_discrepancy ", "inv_stock ",
                "inv_stock_lot ", "inv_stock_location ",
                "inv_product_serial ", "finance", "ledger");
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
