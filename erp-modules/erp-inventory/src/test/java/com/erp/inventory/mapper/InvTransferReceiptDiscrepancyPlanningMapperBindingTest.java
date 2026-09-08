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

@DisplayName("V2调拨差异权威只读 Mapper")
class InvTransferReceiptDiscrepancyPlanningMapperBindingTest
{
    private static final String NAMESPACE =
            "com.erp.inventory.mapper.InvTransferReceiptDiscrepancyPlanningMapper";
    private static final String XML =
            "mapper/inventory/InvTransferReceiptDiscrepancyPlanningMapper.xml";

    @Test
    @DisplayName("全部方法绑定到可解析的只读查询")
    void shouldBindEveryMethodToReadOnlyXml() throws Exception
    {
        Configuration configuration = new Configuration();
        configuration.addMapper(
                InvTransferReceiptDiscrepancyPlanningMapper.class);
        try (InputStream input = Resources.getResourceAsStream(XML))
        {
            new XMLMapperBuilder(input, configuration, XML,
                    configuration.getSqlFragments()).parse();
        }

        assertThat(Arrays.stream(
                InvTransferReceiptDiscrepancyPlanningMapper.class
                        .getDeclaredMethods())
                .map(method -> NAMESPACE + "." + method.getName()))
                .allMatch(configuration::hasStatement)
                .containsExactlyInAnyOrder(
                        NAMESPACE + ".selectCase",
                        NAMESPACE + ".selectCaseForAdjudication",
                        NAMESPACE + ".selectAdjudicationsByCaseId",
                        NAMESPACE + ".selectAdjudicationActionsByCaseId");
    }

    @Test
    @DisplayName("查询逐层核验V2归属并严格限定当前组织")
    void shouldKeepAuthoritativeJoinsAndPartyScope() throws Exception
    {
        String xml;
        try (InputStream input = Resources.getResourceAsStream(XML))
        {
            xml = new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .toLowerCase().replaceAll("\\s+", " ");
        }
        String select = between(xml, "<select id=\"selectcase\"",
                "</select>");

        assertThat(select).contains(
                "from inv_transfer_receipt_discrepancy_case c",
                "inner join inv_transfer_shipment_receipt_allocation ra",
                "ra.receipt_allocation_id = c.receipt_allocation_id",
                "inner join inv_transfer_shipment_receipt r",
                "r.transfer_id = c.transfer_id",
                "inner join inv_transfer_shipment_allocation allocation",
                "allocation.allocation_id = c.shipment_allocation_id",
                "inner join inv_transfer_shipment_detail sd",
                "inner join inv_transfer_shipment s",
                "s.inventory_write_version = 'v2_detail'",
                "inner join inv_transfer_detail td",
                "inner join inv_transfer_order o",
                "source_latest.party_role = 'source'",
                "target_latest.party_role = 'target'",
                "where c.discrepancy_case_id = #{caseid}",
                "#{selecteddeptid} = coalesce( nullif(o.from_warehouse_id, 0), o.from_dept_id)",
                "#{selecteddeptid} = coalesce( nullif(o.to_warehouse_id, 0), o.to_dept_id)",
                "c.discrepancy_amount = round( c.source_cost_price * c.discrepancy_quantity, 6)",
                "c.discrepancy_note = ra.discrepancy_note",
                "c.attachment_refs = ra.attachment_refs");
        assertThat(select).doesNotContain("insert into", "update ",
                "delete from", "for update", "inv_transfer_discrepancy ");
    }

    @Test
    @DisplayName("裁决读取同时限定组织范围且计划查询保持只读")
    void shouldScopeAdjudicationProjectionAndKeepEveryStatementReadOnly()
            throws Exception
    {
        String xml;
        try (InputStream input = Resources.getResourceAsStream(XML))
        {
            xml = new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .toLowerCase().replaceAll("\\s+", " ");
        }
        String scoped = between(xml,
                "<select id=\"selectcaseforadjudication\"", "</select>");
        String headers = between(xml,
                "<select id=\"selectadjudicationsbycaseid\"", "</select>");
        String actions = between(xml,
                "<select id=\"selectadjudicationactionsbycaseid\"",
                "</select>");

        assertThat(scoped).contains(
                "from inv_transfer_receipt_discrepancy_case c",
                "s.inventory_write_version = 'v2_detail'",
                "collection=\"scopedeptids\"",
                "nullif(o.from_warehouse_id, 0)",
                "nullif(o.to_warehouse_id, 0)",
                "#{deptid}",
                "c.discrepancy_amount = round( c.source_cost_price * c.discrepancy_quantity, 6)");
        assertThat(headers).contains(
                "from inv_transfer_receipt_discrepancy_adjudication",
                "where discrepancy_case_id = #{caseid}",
                "order by adjudication_id");
        assertThat(actions).contains(
                "from inv_transfer_receipt_discrepancy_adjudication_action",
                "coverage_kind",
                "where discrepancy_case_id = #{caseid}",
                "order by adjudication_id, action_sequence");
        assertThat(scoped + headers + actions).doesNotContain(
                "insert into", "update ", "delete from", "for update",
                "inv_transfer_discrepancy ");
    }

    private static String between(String source, String start, String end)
    {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from);
        assertThat(from).isGreaterThanOrEqualTo(0);
        assertThat(to).isGreaterThan(from);
        return source.substring(from, to + end.length());
    }
}
