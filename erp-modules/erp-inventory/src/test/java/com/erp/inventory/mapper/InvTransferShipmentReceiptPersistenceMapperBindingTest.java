package com.erp.inventory.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.inventory.domain.transfer.InvShipmentPlanningItemKey;
import com.erp.inventory.domain.transfer.InvTransferReceiptBalanceKey;
import com.erp.inventory.domain.transfer.InvTransferReceiptDerivedLotKey;

@DisplayName("V2调拨收货持久化锁与条件写 Mapper")
class InvTransferShipmentReceiptPersistenceMapperBindingTest
{
    private static final String NAMESPACE =
            "com.erp.inventory.mapper."
                    + "InvTransferShipmentReceiptPersistenceMapper";
    private static final String XML = "mapper/inventory/"
            + "InvTransferShipmentReceiptPersistenceMapper.xml";

    @Test
    @DisplayName("全部锁定与条件写方法均有可解析 XML 绑定")
    void shouldBindEveryMethod() throws Exception
    {
        Configuration configuration = configuration();

        Arrays.stream(InvTransferShipmentReceiptPersistenceMapper.class
                .getDeclaredMethods()).forEach(method ->
                assertThat(configuration.hasStatement(
                        NAMESPACE + "." + method.getName()))
                        .as(method.getName()).isTrue());
    }

    @Test
    @DisplayName("十级业务事实分别按稳定键升序锁定")
    void shouldUseDeterministicBoundLocks() throws Exception
    {
        Configuration configuration = configuration();
        List<InvShipmentPlanningItemKey> itemKeys = List.of(
                new InvShipmentPlanningItemKey("product", 1001L));
        List<InvTransferReceiptDerivedLotKey> lotKeys = List.of(
                new InvTransferReceiptDerivedLotKey(302L, "product",
                        1001L, 501L, "accepted"));
        List<InvTransferReceiptBalanceKey> balanceKeys = List.of(
                new InvTransferReceiptBalanceKey(701L, 901L));

        assertLock(configuration, "selectTransferDetailsForUpdate",
                Map.of("transferId", 900L),
                "order by detail_id for update");
        assertLock(configuration, "selectShipmentDetailsForUpdate",
                Map.of("shipmentId", 91L),
                "order by shipment_detail_id for update");
        assertLock(configuration, "selectAllocationsForUpdate",
                Map.of("shipmentId", 91L),
                "order by allocation_id for update");
        assertThat(sql(configuration, "selectAllocationsForUpdate",
                Map.of("shipmentId", 91L)))
                .contains("allocation_policy", "shipment_id = ?");
        assertLock(configuration, "selectTargetWarehouseModeForUpdate",
                Map.of("warehouseId", 302L), "for update");
        assertLock(configuration, "selectPoliciesForUpdate",
                Map.of("itemKeys", itemKeys),
                "order by item_type, item_id for update");
        assertLock(configuration, "selectTargetStocksForUpdate",
                Map.of("shopDeptId", 302L, "warehouseId", 302L,
                        "itemKeys", itemKeys),
                "order by stock_id for update");
        assertLock(configuration, "selectSourceLotsForUpdate",
                Map.of("lotIds", List.of(501L, 502L)),
                "order by lot_id for update");
        assertLock(configuration, "selectLocationsForUpdate",
                Map.of("targetWarehouseId", 302L,
                        "sourceLocationIds", List.of(801L, 802L)),
                "order by location_id for update");
        assertThat(sql(configuration, "selectLocationsForUpdate",
                Map.of("targetWarehouseId", 302L,
                        "sourceLocationIds", List.of(801L, 802L))))
                .contains("location_id in ( ? , ? )",
                        "warehouse_id = ?", "status = '0'",
                        "is_virtual = '0'",
                        "location_type in ('storage', 'quarantine')");
        assertLock(configuration, "selectDerivedLotsForUpdate",
                Map.of("keys", lotKeys), "order by lot_id for update");
        assertLock(configuration, "selectTargetBalancesForUpdate",
                Map.of("warehouseId", 302L, "keys", balanceKeys),
                "order by balance_id for update");
        assertLock(configuration, "selectShipmentSerialsForUpdate",
                Map.of("shipmentId", 91L),
                "order by serial_current.serial_id for update");

        String all = normalized(resourceText());
        assertThat(all).contains(
                "left join inv_transfer_shipment_receipt_serial receipt_serial",
                "receipt_serial.receipt_serial_id is null");
        assertThat(all).doesNotContain("1001", "501l", "302l");
    }

    @Test
    @DisplayName("每项数量写入同时约束版本归属非负和守恒")
    void shouldGuardEveryMutation() throws Exception
    {
        String xml = normalized(resourceText());
        String allocation = between(xml,
                "<update id=\"updateallocationprogress\"", "</update>");
        String stock = between(xml, "<update id=\"addtargetstock\"",
                "</update>");
        String balance = between(xml,
                "<update id=\"addtargetbalance\"", "</update>");
        String serial = between(xml,
                "<update id=\"moveserialtotarget\"", "</update>");

        assertThat(allocation).contains(
                "shipment_id = #{shipmentid}",
                "receipt_version = #{receiptversion}",
                "#{accepteddelta} &gt;= 0",
                "#{damageddelta} &gt;= 0",
                "#{shortagedelta} &gt;= 0",
                "&lt;= allocated_quantity");
        assertThat(stock).contains(
                "shop_dept_id = #{shopdeptid}",
                "warehouse_id = #{warehouseid}",
                "version = #{version}",
                "#{incomingcost} &gt;= 0",
                "coalesce(total_cost, 0) &gt;= 0",
                "current_quantity = available_quantity + locked_quantity + quarantine_quantity");
        assertThat(balance).contains(
                "warehouse_id = #{warehouseid}",
                "lot_id = #{lotid}",
                "location_id = #{locationid}",
                "version = #{version}",
                "coalesce(total_cost, 0) &gt;= 0",
                "current_quantity = available_quantity + locked_quantity + quarantine_quantity");
        assertThat(serial).contains(
                "warehouse_id = #{sourcewarehouseid}",
                "balance_id = #{sourcebalanceid}",
                "lot_id = #{sourcelotid}",
                "location_id = #{sourcelocationid}",
                "serial_status = 'shipped'",
                "#{statusafter} in ('available', 'quarantine')");
    }

    @Test
    @DisplayName("原子写入方法使用不可变审计和旧值版本守卫")
    void shouldBindAuditsGeneratedKeysAndLifecycleGuards() throws Exception
    {
        String xml = normalized(resourceText());
        String receipt = between(xml, "<insert id=\"insertreceipt\"",
                "</insert>");
        String stock = between(xml,
                "<insert id=\"inserttargetstock\"", "</insert>");
        String lot = between(xml, "<insert id=\"inserttargetlot\"",
                "</insert>");
        String balance = between(xml,
                "<insert id=\"inserttargetbalance\"", "</insert>");
        String allocation = between(xml,
                "<insert id=\"insertreceiptallocation\"", "</insert>");
        String discrepancy = between(xml,
                "<insert id=\"insertreceiptdiscrepancycase\"",
                "</insert>");
        String serial = between(xml,
                "<insert id=\"insertreceiptserial\"", "</insert>");
        String ledger = between(xml,
                "<insert id=\"insertreceiptledger\"", "</insert>");
        String shipmentDetail = between(xml,
                "<update id=\"addshipmentdetailfulfilled\"", "</update>");
        String transferDetail = between(xml,
                "<update id=\"addtransferdetailfulfilled\"", "</update>");
        String shipmentLifecycle = between(xml,
                "<update id=\"updateshipmentlifecycle\"", "</update>");
        String transferLifecycle = between(xml,
                "<update id=\"updatetransferlifecycle\"", "</update>");

        assertThat(receipt).contains(
                "keyproperty=\"generatedid.value\"",
                "command_request_id", "request_fingerprint",
                "receipt_plan_version", "source_shipment_plan_version",
                "target_reconcile_batch", "#{receipt.createdtime}");
        assertThat(stock).contains(
                "keyproperty=\"generatedid.value\"", "insert into inv_stock",
                "quarantine_quantity", "#{stock.currentquantitybefore} = 0",
                "#{stock.currentquantityafter} =");
        assertThat(lot).contains(
                "keyproperty=\"generatedid.value\"", "source_lot_id",
                "receipt_disposition",
                "#{lot.key.disposition} in ('accepted', 'damaged')");
        assertThat(balance).contains(
                "keyproperty=\"generatedid.value\"",
                "quarantine_quantity",
                "#{balance.currentquantitybefore} = 0",
                "#{balance.currentquantityafter} =");
        assertThat(allocation).contains(
                "keyproperty=\"generatedid.value\"", "#{shipmentid}",
                "allocation_version_before", "allocation_version_after",
                "accepted_balance_version_before",
                "damaged_balance_version_after");
        assertThat(discrepancy).contains(
                "keyproperty=\"generatedid.value\"",
                "insert into inv_transfer_receipt_discrepancy_case",
                "from inv_transfer_shipment_receipt_allocation ra",
                "ra.receipt_allocation_id = #{receiptallocationid}",
                "ra.receipt_id = #{receiptid}",
                "ra.shipment_id = #{receipt.shipmentid}",
                "#{discrepancy.discrepancytype} in ('damaged', 'shortage')",
                "then ra.damaged_quantity else ra.shortage_quantity end",
                "#{discrepancy.sourcecostprice} = ra.cost_price",
                "round( ra.cost_price * #{discrepancy.discrepancyquantity}, 6)",
                "ra.discrepancy_note = #{discrepancy.discrepancynote}",
                "ra.attachment_refs = #{discrepancy.attachmentrefs}",
                "'awaiting_confirmation', 0",
                "#{receipt.createdtime}");
        assertThat(serial).contains(
                "shipment_serial_id", "disposition", "status_before",
                "status_after", "#{shipmentid}");
        assertThat(ledger).contains(
                "receipt_allocation_id", "receipt_disposition",
                "to_balance_id", "to_lot_id", "to_location_id",
                "#{ledger.beforequantity}", "#{ledger.afterquantity}");
        assertThat(ledger).doesNotContain("shipment_allocation_id");
        assertThat(shipmentDetail).contains(
                "received_quantity = #{detail.expectedreceivedquantity}",
                "#{detail.fulfillmentdelta} &gt; 0",
                "&lt;= shipped_quantity");
        assertThat(transferDetail).contains(
                "received_quantity = #{detail.expectedreceivedquantity}",
                "&lt;= delivered_quantity",
                "delivered_quantity &lt;= quantity");
        assertThat(shipmentLifecycle).contains(
                "inventory_write_version = 'v2_detail'",
                "status = #{expectedstatus}",
                "('pending_receive', 'partial_received')");
        assertThat(transferLifecycle).contains(
                "status = #{expectedstatus}", "version = #{expectedversion}",
                "version = version + 1",
                "('partial_delivered', 'delivered', 'partial_received')");
    }

    private static void assertLock(Configuration configuration, String id,
            Map<String, Object> params, String expected)
    {
        assertThat(sql(configuration, id, params)).contains(expected);
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

    private static String sql(Configuration configuration, String id,
            Map<String, Object> params)
    {
        return normalized(configuration.getMappedStatement(
                NAMESPACE + "." + id).getBoundSql(params).getSql());
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

    private static String between(String value, String start, String end)
    {
        int from = value.indexOf(start);
        assertThat(from).isGreaterThanOrEqualTo(0);
        int to = value.indexOf(end, from);
        assertThat(to).isGreaterThan(from);
        return value.substring(from, to + end.length());
    }
}
