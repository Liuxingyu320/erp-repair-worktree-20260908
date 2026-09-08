package com.erp.inventory.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("V2调拨收货规划只读 Mapper")
class InvTransferShipmentReceiptPlanningMapperBindingTest
{
    private static final String NAMESPACE =
            "com.erp.inventory.mapper.InvTransferShipmentReceiptPlanningMapper";
    private static final String XML =
            "mapper/inventory/InvTransferShipmentReceiptPlanningMapper.xml";

    @Test
    @DisplayName("全部方法均绑定到可解析的 XML 查询")
    void shouldBindEveryMapperMethod() throws Exception
    {
        Configuration configuration = configuration();

        Arrays.stream(InvTransferShipmentReceiptPlanningMapper.class
                .getDeclaredMethods()).forEach(method -> assertThat(
                        configuration.hasStatement(
                                NAMESPACE + "." + method.getName()))
                        .as(method.getName()).isTrue());
    }

    @Test
    @DisplayName("发货和目标仓参数始终使用绑定占位符")
    void shouldBindShipmentAndWarehouseIdentifiers() throws Exception
    {
        Configuration configuration = configuration();
        String allocationSql = normalized(configuration.getMappedStatement(
                NAMESPACE + ".selectAllocations").getBoundSql(Map.of(
                        "shipmentId", 91L)).getSql());
        String locationSql = normalized(configuration.getMappedStatement(
                NAMESPACE + ".selectTargetLocations").getBoundSql(Map.of(
                        "warehouseId", 302L)).getSql());

        assertThat(allocationSql).contains("a.shipment_id = ?")
                .doesNotContain("91");
        assertThat(locationSql).contains("warehouse_id = ?")
                .doesNotContain("302");
    }

    @Test
    @DisplayName("查询批量读取不可变发货事实并保持严格只读")
    void shouldReadBatchedAuditFactsWithoutMutation() throws Exception
    {
        String xml = normalized(resourceText());

        assertThat(xml).contains(
                "from inv_transfer_shipment_allocation a",
                "from inv_transfer_shipment_serial audit",
                "left join inv_inventory_serial serial_current",
                "a.allocation_policy",
                "lot.receipt_disposition as source_receipt_disposition",
                "loc.location_type as source_location_type",
                "td.quantity as transfer_detail_requested_quantity",
                "td.delivered_quantity as transfer_detail_delivered_quantity",
                "td.received_quantity as transfer_detail_received_quantity",
                "a.accepted_received_quantity",
                "a.damaged_received_quantity",
                "a.shortage_reported_quantity",
                "a.receipt_version",
                "left join inv_transfer_shipment_receipt_serial receipt_serial",
                "receipt_serial.receipt_serial_id is null",
                "a.shipment_id = #{shipmentid}",
                "audit.shipment_id = #{shipmentid}",
                "location_type in ('storage', 'quarantine')",
                "order by a.allocation_id",
                "order by audit.allocation_id, audit.serial_id");
        assertThat(xml).doesNotContain("select *", " for update",
                "insert into", "update inv_", "delete from");
    }

    private static Configuration configuration() throws Exception
    {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(XML))
        {
            new XMLMapperBuilder(input, configuration, XML,
                    configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    private static String resourceText() throws Exception
    {
        try (InputStream input = Resources.getResourceAsStream(XML))
        {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String normalized(String value)
    {
        return value.toLowerCase().replaceAll("\\s+", " ").trim();
    }
}
