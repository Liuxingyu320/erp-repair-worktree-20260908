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

@DisplayName("V2差异退回隔离预留只读发货规划Mapper契约")
class
        InvTransferReceiptDiscrepancyReturnShipmentPlanningMapperBindingTest
{
    private static final String SIMPLE_NAME =
            "InvTransferReceiptDiscrepancyReturnShipmentPlanningMapper";
    private static final String NAMESPACE =
            "com.erp.inventory.mapper." + SIMPLE_NAME;
    private static final String XML =
            "mapper/inventory/" + SIMPLE_NAME + ".xml";

    @Test
    @DisplayName("四个只读事实方法全部绑定到可解析XML")
    void shouldBindEveryReadMethod() throws Exception
    {
        Configuration configuration = configuration();

        assertThat(Arrays.stream(
                InvTransferReceiptDiscrepancyReturnShipmentPlanningMapper
                        .class.getDeclaredMethods())
                .map(method -> NAMESPACE + "." + method.getName()))
                .allMatch(configuration::hasStatement)
                .containsExactlyInAnyOrder(
                        NAMESPACE + ".selectReservation",
                        NAMESPACE + ".selectStock",
                        NAMESPACE + ".selectBalance",
                        NAMESPACE + ".selectActiveSerials");
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
    @DisplayName("规划只读当前轮次退回关系和ACTIVE隔离单件")
    void shouldReadExactCurrentReturnFactsWithoutLocksOrDml()
            throws Exception
    {
        String xml = normalized();
        String reservation = between(xml,
                "<select id=\"selectreservation\"", "</select>");
        String serials = between(xml,
                "<select id=\"selectactiveserials\"", "</select>");

        assertThat(reservation).contains(
                "from inv_transfer_quarantine_reservation reservation",
                "child.approval_round = reservation.reservation_round",
                "inv_transfer_receipt_discrepancy_adjudication_workflow_link",
                "workflow.workflow_type = 'return'",
                "workflow.inventory_source = 'quarantine_detail'",
                "child.source_business_type = 'transfer_discrepancy_return'");
        assertThat(serials).contains(
                "from inv_transfer_quarantine_reservation_serial binding",
                "inner join inv_inventory_serial serial",
                "binding.lifecycle_status = 'active'",
                "order by binding.receipt_serial_id, binding.serial_id");
        assertThat(count(xml, "<select ")).isEqualTo(4);
        assertThat(xml).doesNotContain(
                "for update", "<insert ", "<update ", "<delete ",
                "<selectkey ", "jdbc:", "bosserp_new", "nacos",
                "docker");
    }

    @Test
    @DisplayName("只读Mapper仅由未接线规划Service引用")
    void shouldOnlyServeUnwiredPlanningOwner() throws Exception
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
                                    "InvTransferReceiptDiscrepancyReturnShipmentPlanningService.java"));
        }
    }

    private static Configuration configuration() throws Exception
    {
        Configuration configuration = new Configuration();
        configuration.addMapper(
                InvTransferReceiptDiscrepancyReturnShipmentPlanningMapper
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
