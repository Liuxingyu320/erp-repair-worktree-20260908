package com.erp.inventory.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.beans.Introspector;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("V2差异裁决退回隔离预留Mapper契约")
class InvTransferReceiptDiscrepancyReturnMapperBindingTest
{
    private static final String SIMPLE_NAME =
            "InvTransferReceiptDiscrepancyReturnMapper";
    private static final String NAMESPACE =
            "com.erp.inventory.mapper." + SIMPLE_NAME;
    private static final String XML =
            "mapper/inventory/" + SIMPLE_NAME + ".xml";

    @Test
    @DisplayName("十四个固定锁序与条件写方法全部绑定")
    void shouldBindEveryMethod() throws Exception
    {
        Configuration configuration = configuration();

        assertThat(Arrays.stream(
                InvTransferReceiptDiscrepancyReturnMapper.class
                        .getDeclaredMethods())
                .map(method -> NAMESPACE + "." + method.getName()))
                .allMatch(configuration::hasStatement)
                .containsExactlyInAnyOrder(
                        NAMESPACE + ".selectSourceForUpdate",
                        NAMESPACE + ".selectCreatedDraftDetailForUpdate",
                        NAMESPACE + ".selectDisposedQuantity",
                        NAMESPACE + ".selectStockForUpdate",
                        NAMESPACE + ".selectLotForUpdate",
                        NAMESPACE + ".selectLocationForUpdate",
                        NAMESPACE + ".selectBalanceForUpdate",
                        NAMESPACE + ".selectEligibleSerialsForUpdate",
                        NAMESPACE + ".reserveStock",
                        NAMESPACE + ".reserveBalance",
                        NAMESPACE + ".reserveSerial",
                        NAMESPACE + ".insertReservation",
                        NAMESPACE + ".insertReservationSerial",
                        NAMESPACE + ".selectCreatedChildForUpdate");
    }

    @Test
    @DisplayName("全部结果映射只使用可写JavaBean属性")
    void shouldMapOnlyWritableProperties() throws Exception
    {
        Configuration configuration = configuration();
        for (String id : Set.of("ReturnFactResult", "DetailResult",
                "StockResult", "LotResult", "LocationResult",
                "BalanceResult", "SerialResult", "CreatedChildResult"))
        {
            var resultMap = configuration.getResultMap(NAMESPACE + "." + id);
            Set<String> writable = Arrays.stream(Introspector.getBeanInfo(
                            resultMap.getType()).getPropertyDescriptors())
                    .filter(property -> property.getWriteMethod() != null)
                    .map(property -> property.getName())
                    .collect(Collectors.toSet());
            assertThat(resultMap.getResultMappings())
                    .extracting(mapping -> mapping.getProperty())
                    .allMatch(writable::contains);
        }
    }

    @Test
    @DisplayName("锁定V2受损来源并排除写销与既有退回单件")
    void shouldLockAuthoritativeDamagedFacts() throws Exception
    {
        String xml = normalized();
        String source = between(xml,
                "<select id=\"selectsourceforupdate\"", "</select>");
        String serials = between(xml,
                "<select id=\"selecteligibleserialsforupdate\"",
                "</select>");

        assertThat(source).contains(
                "from inv_transfer_receipt_discrepancy_case c",
                "inv_transfer_receipt_discrepancy_adjudication_action x",
                "inner join inv_transfer_shipment_receipt receipt",
                "inner join inv_transfer_shipment_receipt_allocation ra",
                "ra.receipt_id = c.receipt_id",
                "inner join inv_transfer_shipment_allocation sa",
                "sa.shipment_id = c.shipment_id",
                "c.discrepancy_type = 'damaged'",
                "x.action_type = 'return_to_source'",
                "x.execution_status = 'pending'",
                "ra.damaged_target_balance_id is not null",
                "receipt.target_warehouse_id = coalesce(",
                "from inv_transfer_quarantine_reservation prior",
                "for update");
        assertThat(serials).contains(
                "from inv_transfer_shipment_receipt_serial rs",
                "serial.serial_status = 'quarantine'",
                "from inv_transfer_receipt_discrepancy_damage_loss_serial used",
                "from inv_transfer_quarantine_reservation_serial reserved",
                "order by rs.receipt_serial_id, serial.serial_id",
                "for update");
    }

    @Test
    @DisplayName("条件写只执行隔离转锁定、单件冻结和专属预留追加")
    void shouldOwnOnlyQuarantineReservationDml() throws Exception
    {
        String xml = normalized();
        String stock = between(xml, "<update id=\"reservestock\"",
                "</update>");
        String balance = between(xml, "<update id=\"reservebalance\"",
                "</update>");
        String serial = between(xml, "<update id=\"reserveserial\"",
                "</update>");
        String reservation = between(xml,
                "<insert id=\"insertreservation\"", "</insert>");
        String reservationSerial = between(xml,
                "<insert id=\"insertreservationserial\"", "</insert>");

        assertThat(stock).contains(
                "update inv_stock",
                "locked_quantity = #{reservation.stock.lockedafter}",
                "quarantine_quantity = #{reservation.stock.quarantineafter}",
                "#{reservation.stock.currentafter} = current_quantity",
                "#{reservation.stock.lockedafter} = locked_quantity + #{reservation.source.quantity}",
                "#{reservation.stock.quarantineafter} = quarantine_quantity - #{reservation.source.quantity}",
                "current_quantity = available_quantity + locked_quantity + quarantine_quantity");
        assertThat(balance).contains(
                "update inv_stock_balance_detail",
                "available_quantity = 0",
                "cost_price = #{reservation.source.sourcecostprice}",
                "#{reservation.balance.lockedafter} = locked_quantity + #{reservation.source.quantity}");
        assertThat(serial).contains(
                "update inv_inventory_serial",
                "serial_status = #{serial.statusafter}",
                "#{serial.statusafter} = 'quarantine_reserved'");
        assertThat(reservation).contains(
                "insert into inv_transfer_quarantine_reservation",
                "disposed_quantity_before", "disposed_quantity_after",
                "'active'");
        assertThat(reservationSerial).contains(
                "insert into inv_transfer_quarantine_reservation_serial",
                "current_serial.serial_status = #{serial.statusafter}",
                "not exists (");
        assertThat(count(xml, "<select ")).isEqualTo(9);
        assertThat(count(xml, "<update ")).isEqualTo(3);
        assertThat(count(xml, "<insert ")).isEqualTo(2);
        assertThat(xml).doesNotContain("<delete ", "<truncate ");
        assertThat(xml).doesNotContain(
                "insert into inv_transfer_order",
                "insert into inv_transfer_detail",
                "insert into inv_transfer_reservation");
    }

    @Test
    @DisplayName("专属Mapper只有未接线退回效果所有者调用")
    void shouldOnlyServeUnwiredReturnOwner() throws Exception
    {
        Path root = Path.of(System.getProperty("user.dir"), "src", "main",
                "java", "com", "erp", "inventory").normalize();
        try (var paths = Files.walk(root))
        {
            assertThat(paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> contains(path, SIMPLE_NAME))
                    .map(root::relativize).toList())
                    .containsExactlyInAnyOrder(
                            Path.of("mapper", SIMPLE_NAME + ".java"),
                            Path.of("service", "impl",
                                    "InvTransferReceiptDiscrepancyReturnEffectOwner.java"));
        }
    }

    private static Configuration configuration() throws Exception
    {
        Configuration configuration = new Configuration();
        configuration.addMapper(
                InvTransferReceiptDiscrepancyReturnMapper.class);
        try (InputStream input = Resources.getResourceAsStream(XML))
        {
            new XMLMapperBuilder(input, configuration, XML,
                    configuration.getSqlFragments()).parse();
        }
        return configuration;
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

    private static boolean contains(Path path, String value)
    {
        try
        {
            return Files.readString(path, StandardCharsets.UTF_8)
                    .contains(value);
        }
        catch (java.io.IOException error)
        {
            throw new IllegalStateException(error);
        }
    }
}
