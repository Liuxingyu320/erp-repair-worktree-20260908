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

@DisplayName("V2差异退回隔离预留生命周期Mapper契约")
class
        InvTransferReceiptDiscrepancyReturnReservationLifecycleMapperBindingTest
{
    private static final String SIMPLE_NAME =
            "InvTransferReceiptDiscrepancyReturnReservationLifecycleMapper";
    private static final String NAMESPACE =
            "com.erp.inventory.mapper." + SIMPLE_NAME;
    private static final String XML =
            "mapper/inventory/" + SIMPLE_NAME + ".xml";

    @Test
    @DisplayName("十六个固定锁序和条件生命周期方法全部绑定")
    void shouldBindEveryMethod() throws Exception
    {
        Configuration configuration = configuration();

        assertThat(Arrays.stream(
                InvTransferReceiptDiscrepancyReturnReservationLifecycleMapper
                        .class.getDeclaredMethods())
                .map(method -> NAMESPACE + "." + method.getName()))
                .allMatch(configuration::hasStatement)
                .hasSize(16);
    }

    @Test
    @DisplayName("全部结果映射只使用可写JavaBean属性")
    void shouldMapOnlyWritableProperties() throws Exception
    {
        Configuration configuration = configuration();
        for (String id : Set.of("LifecycleFactResult", "StockResult",
                "BalanceResult", "SerialResult"))
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
    @DisplayName("锁序只接受退回工作流当前审批轮次和ACTIVE序列号")
    void shouldLockExactReturnOwnershipInFixedOrder() throws Exception
    {
        String xml = normalized();
        String source = between(xml,
                "<select id=\"selectreservationforupdate\"", "</select>");
        String serials = between(xml,
                "<select id=\"selectserialsforupdate\"", "</select>");

        assertThat(source).contains(
                "from inv_transfer_quarantine_reservation reservation",
                "inner join inv_transfer_order child",
                "child.approval_round = reservation.reservation_round",
                "inv_transfer_receipt_discrepancy_adjudication_workflow_link",
                "workflow.workflow_type = 'return'",
                "workflow.inventory_source = 'quarantine_detail'",
                "for update");
        assertThat(serials).contains(
                "from inv_transfer_quarantine_reservation_serial binding",
                "inner join inv_inventory_serial serial",
                "binding.lifecycle_status = 'active'",
                "order by binding.receipt_serial_id, binding.serial_id",
                "for update");
    }

    @Test
    @DisplayName("消费和释放DML均固定数量成本版本事件与单件状态")
    void shouldGuardEveryLifecycleMutation() throws Exception
    {
        String xml = normalized();
        String consumeStock = between(xml,
                "<update id=\"consumestock\"", "</update>");
        String consumeBalance = between(xml,
                "<update id=\"consumebalance\"", "</update>");
        String consumption = between(xml,
                "<insert id=\"insertconsumption\"", "</insert>");
        String consumed = between(xml,
                "<update id=\"updateconsumedreservation\"", "</update>");
        String releaseStock = between(xml,
                "<update id=\"releasestock\"", "</update>");
        String release = between(xml,
                "<insert id=\"insertrelease\"", "</insert>");
        String released = between(xml,
                "<update id=\"updatereleasedreservation\"", "</update>");

        assertThat(consumeStock).contains(
                "current_quantity = #{prepared.stock.currentafter}",
                "locked_quantity = #{prepared.stock.lockedafter}",
                "total_cost = #{prepared.stock.totalcostafter}",
                "current_quantity = available_quantity + locked_quantity + quarantine_quantity",
                "#{prepared.stock.versionafter} = version + 1");
        assertThat(consumeBalance).contains(
                "update inv_stock_balance_detail",
                "available_quantity = 0",
                "cost_price = #{prepared.fact.sourcecostprice}");
        assertThat(consumption).contains(
                "insert into inv_transfer_quarantine_reservation_consumption",
                "shipment.inventory_write_version = 'v2_detail'",
                "not exists (");
        assertThat(consumed).contains(
                "consumed_quantity = #{prepared.consumedafter}",
                "reserved_quantity - #{prepared.consumedafter} - released_quantity",
                "from inv_transfer_quarantine_reservation_consumption event");
        assertThat(releaseStock).contains(
                "#{prepared.stock.currentafter} = current_quantity",
                "#{prepared.stock.quarantineafter} = quarantine_quantity + #{prepared.quantity}");
        assertThat(release).contains(
                "insert into inv_transfer_quarantine_reservation_release",
                "prior.quarantine_reservation_id");
        assertThat(released).contains(
                "released_quantity = #{prepared.releasedafter}",
                "reserved_quantity = consumed_quantity + #{prepared.releasedafter}");
        assertThat(count(xml, "<select ")).isEqualTo(4);
        assertThat(count(xml, "<update ")).isEqualTo(10);
        assertThat(count(xml, "<insert ")).isEqualTo(2);
        assertThat(xml).doesNotContain("<delete ", "<truncate ");
    }

    @Test
    @DisplayName("生命周期Mapper只由未接线生命周期Service调用")
    void shouldOnlyServeUnwiredLifecycleOwner() throws Exception
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
                                    "InvTransferReceiptDiscrepancyReturnReservationLifecycleService.java"));
        }
    }

    private static Configuration configuration() throws Exception
    {
        Configuration configuration = new Configuration();
        configuration.addMapper(
                InvTransferReceiptDiscrepancyReturnReservationLifecycleMapper
                        .class);
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
