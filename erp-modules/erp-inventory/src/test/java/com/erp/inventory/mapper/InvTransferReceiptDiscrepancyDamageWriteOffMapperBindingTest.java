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

@DisplayName("V2调拨受损隔离库存原子写销 Mapper")
class InvTransferReceiptDiscrepancyDamageWriteOffMapperBindingTest
{
    private static final String NAMESPACE = "com.erp.inventory.mapper."
            + "InvTransferReceiptDiscrepancyDamageWriteOffMapper";
    private static final String XML = "mapper/inventory/"
            + "InvTransferReceiptDiscrepancyDamageWriteOffMapper.xml";

    @Test
    @DisplayName("全部锁定读取和窄DML方法绑定到可解析XML")
    void shouldBindEveryDeclaredMethod() throws Exception
    {
        Configuration configuration = new Configuration();
        configuration.addMapper(
                InvTransferReceiptDiscrepancyDamageWriteOffMapper.class);
        try (InputStream input = Resources.getResourceAsStream(XML))
        {
            new XMLMapperBuilder(input, configuration, XML,
                    configuration.getSqlFragments()).parse();
        }

        assertThat(Arrays.stream(
                InvTransferReceiptDiscrepancyDamageWriteOffMapper.class
                        .getDeclaredMethods())
                .map(method -> NAMESPACE + "." + method.getName()))
                .allMatch(configuration::hasStatement)
                .hasSize(13);
    }

    @Test
    @DisplayName("锁定查询只沿V2受损收货锚点并稳定排定序列号")
    void shouldLockOnlyAuthoritativeDamageDimensions() throws Exception
    {
        String xml = normalized();

        assertThat(statement(xml, "select", "selectfactforupdate"))
                .contains(
                        "inner join inv_transfer_shipment_receipt r",
                        "inner join inv_transfer_shipment_receipt_allocation ra",
                        "c.discrepancy_type = 'damaged'",
                        "c.discrepancy_quantity = ra.damaged_quantity",
                        "c.source_cost_price = ra.cost_price",
                        "c.discrepancy_amount = ra.damaged_cost",
                        "for update");
        assertThat(statement(xml, "select", "selectstockforupdate"))
                .contains("from inv_stock",
                        "shop_dept_id = #{fact.targetwarehouseid}",
                        "warehouse_id = #{fact.targetwarehouseid}",
                        "for update");
        assertThat(statement(xml, "select", "selectlotforupdate"))
                .contains("from inv_inventory_lot", "for update");
        assertThat(statement(xml, "select", "selectlocationforupdate"))
                .contains("from inv_warehouse_location", "for update");
        assertThat(statement(xml, "select", "selectbalanceforupdate"))
                .contains("from inv_stock_balance_detail", "for update");
        assertThat(statement(xml, "select",
                "selecteligibleserialsforupdate"))
                .contains(
                        "from inv_transfer_shipment_receipt_serial rs",
                        "inner join inv_inventory_serial serial",
                        "rs.disposition = 'damaged'",
                        "serial.serial_status = 'quarantine'",
                        "inv_transfer_receipt_discrepancy_damage_loss_serial used",
                        "order by rs.receipt_serial_id, serial.serial_id",
                        "for update");
    }

    @Test
    @DisplayName("库存余额和序列号DML全部带精确前值及守恒条件")
    void shouldGuardEveryPhysicalMutation() throws Exception
    {
        String xml = normalized();
        String stock = statement(xml, "update", "decrementstock");
        String balance = statement(xml, "update", "decrementbalance");
        String serial = statement(xml, "update", "scrapserial");

        assertThat(stock).contains(
                "version = #{effect.stockversionbefore}",
                "current_quantity = #{effect.stockcurrentbefore}",
                "quarantine_quantity = #{effect.stockquarantinebefore}",
                "total_cost = #{effect.stocktotalcostbefore}",
                "current_quantity = available_quantity + locked_quantity + quarantine_quantity",
                "#{effect.stockversionafter} = version + 1");
        assertThat(balance).contains(
                "version = #{effect.balanceversionbefore}",
                "available_quantity = 0",
                "locked_quantity = #{effect.balancelockedquantity}",
                "current_quantity = #{effect.balancecurrentbefore}",
                "quarantine_quantity = #{effect.balancequarantinebefore}",
                "total_cost = #{effect.balancetotalcostbefore}",
                "#{effect.balanceversionafter} = version + 1");
        assertThat(serial).contains(
                "set serial_status = 'scrapped'",
                "serial_id = #{serial.serialid}",
                "serial_no = #{serial.serialno}",
                "balance_id = #{effect.fact.damagedtargetbalanceid}",
                "serial_status = 'quarantine'");
    }

    @Test
    @DisplayName("不可变损失台账重核动作事实并保持列投影等宽")
    void shouldAppendAnchoredImmutableFacts() throws Exception
    {
        String xml = normalized();
        String stockLedger = statement(xml, "insert", "insertstockledger");
        String loss = statement(xml, "insert", "insertdamagelossledger");
        String lossSerial = statement(xml, "insert",
                "insertdamagelossserial");

        assertThat(stockLedger).contains(
                "insert into inv_stock_ledger_detail",
                "#{effect.stockledgerrequestid}",
                "'damage_write_off'", "'transfer_discrepancy'",
                "-#{effect.quantity}");
        assertThat(loss).contains(
                "inv_transfer_receipt_discrepancy_damage_loss_ledger",
                "x.action_type in ('damage_write_off', 'transport_loss_write_off')",
                "x.coverage_kind = 'resolution'",
                "x.coverage_kind = #{effect.execution.coveragekind}",
                "x.execution_status = 'pending'",
                "x.execution_effect_reference is null",
                "#{effect.allocationwrittenoffafter} &lt;= ra.damaged_quantity",
                "'quarantine_write_off_and_loss_ledger'",
                "ra.damaged_cost = round( ra.cost_price * ra.damaged_quantity, 6)",
                "inv_transfer_receipt_discrepancy_adjudication_execution_event event",
                "event.event_fingerprint = #{effect.execution.eventfingerprint}",
                "prior.request_id = #{effect.execution.requestid}",
                "prior.stock_ledger_request_id = #{effect.stockledgerrequestid}");
        assertMatchingProjectionWidth(loss);
        assertThat(lossSerial).contains(
                "inv_transfer_receipt_discrepancy_damage_loss_serial",
                "serial_current.serial_status = 'scrapped'",
                "prior.receipt_serial_id = #{serial.receiptserialid}",
                "prior.serial_id = #{serial.serialid}");

        assertThat(count(xml, "<select ")).isEqualTo(7);
        assertThat(count(xml, "<update ")).isEqualTo(3);
        assertThat(count(xml, "<insert ")).isEqualTo(3);
        assertThat(xml).doesNotContain(
                "<delete ", " inv_transfer_order ", " finance",
                "set execution_status", "set plan_status",
                "set status = #{effect.execution.casestatusafter}");
    }

    private static String normalized() throws Exception
    {
        try (InputStream input = Resources.getResourceAsStream(XML))
        {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .toLowerCase().replaceAll("\\s+", " ");
        }
    }

    private static String statement(String source, String kind, String id)
    {
        String start = "<" + kind + " id=\"" + id + "\"";
        String end = "</" + kind + ">";
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
