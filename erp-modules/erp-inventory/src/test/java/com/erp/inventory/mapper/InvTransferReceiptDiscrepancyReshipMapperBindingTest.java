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

@DisplayName("V2调拨差异裁决补发只读锁定契约")
class InvTransferReceiptDiscrepancyReshipMapperBindingTest
{
    private static final String SIMPLE_NAME =
            "InvTransferReceiptDiscrepancyReshipMapper";
    private static final String NAMESPACE =
            "com.erp.inventory.mapper." + SIMPLE_NAME;
    private static final String XML =
            "mapper/inventory/" + SIMPLE_NAME + ".xml";

    @Test
    @DisplayName("两个窄方法全部绑定到可解析XML")
    void shouldBindEveryNarrowMethod() throws Exception
    {
        Configuration configuration = configuration();

        assertThat(Arrays.stream(
                InvTransferReceiptDiscrepancyReshipMapper.class
                        .getDeclaredMethods())
                .map(method -> NAMESPACE + "." + method.getName()))
                .allMatch(configuration::hasStatement)
                .containsExactlyInAnyOrder(
                        NAMESPACE + ".selectSourceForUpdate",
                        NAMESPACE + ".selectCreatedChildForUpdate");
    }

    @Test
    @DisplayName("全部结果映射属性都必须存在于目标JavaBean")
    void shouldMapOnlyWritableBeanProperties() throws Exception
    {
        Configuration configuration = configuration();

        for (String id : Set.of("ReshipFactResult", "CreatedChildResult"))
        {
            var resultMap = configuration.getResultMap(NAMESPACE + "." + id);
            Set<String> writableProperties = Arrays.stream(Introspector
                    .getBeanInfo(resultMap.getType()).getPropertyDescriptors())
                    .filter(property -> property.getWriteMethod() != null)
                    .map(property -> property.getName())
                    .collect(Collectors.toSet());

            assertThat(resultMap.getResultMappings())
                    .extracting(mapping -> mapping.getProperty())
                    .allMatch(writableProperties::contains);
        }
    }

    @Test
    @DisplayName("来源与新子调拨均锁定读取且Mapper没有DML")
    void shouldRemainReadOnlyAndLockAuthoritativeFacts() throws Exception
    {
        String xml = normalized();
        String source = between(xml,
                "<select id=\"selectsourceforupdate\"", "</select>");
        String child = between(xml,
                "<select id=\"selectcreatedchildforupdate\"",
                "</select>");

        assertThat(source).contains(
                "from inv_transfer_receipt_discrepancy_case c",
                "inv_transfer_receipt_discrepancy_adjudication_action x",
                "inner join inv_transfer_shipment_receipt_allocation ra",
                "ra.receipt_id = c.receipt_id",
                "inner join inv_transfer_shipment_allocation sa",
                "sa.shipment_id = c.shipment_id",
                "inner join inv_transfer_order parent",
                "c.discrepancy_type = 'shortage'",
                "c.discrepancy_quantity = ra.shortage_quantity",
                "x.action_type = 'reship'",
                "x.action_quantity &gt; 0",
                "x.action_quantity &lt;= c.discrepancy_quantity",
                "round(x.action_quantity * c.source_cost_price, 6)",
                "x.action_amount &lt;= c.discrepancy_amount",
                "x.execution_status = 'pending'",
                "x.execution_version = #{actionversion}",
                "x.execution_effect_reference is null",
                "not exists ( select 1 from inv_transfer_receipt_discrepancy_adjudication_workflow_link link",
                "for update");
        assertThat(child).contains(
                "from inv_transfer_order child",
                "inner join inv_transfer_detail detail",
                "inner join inv_transfer_reservation reservation",
                "child.source_business_type = 'transfer_discrepancy_reship'",
                "child.source_business_id = #{actionid}",
                "reservation.reservation_round",
                "reservation.status = 'active'",
                "reservation.consumed_quantity = 0",
                "reservation.released_quantity = 0",
                "inventory_write_version = 'v2_detail'",
                "from inv_transfer_shipment_receipt receipt",
                "from inv_transfer_receipt_discrepancy_case discrepancy",
                "for update");
        assertThat(count(xml, "<select ")).isEqualTo(2);
        assertThat(count(xml, "for update")).isEqualTo(2);
        assertThat(xml).doesNotContain(
                "<insert ", "<update ", "<delete ",
                "insert into", "update inv_", "delete from");
    }

    @Test
    @DisplayName("只读Mapper只服务于未接线补发效果所有者")
    void shouldOnlyServeUnwiredOwner() throws Exception
    {
        Path root = Path.of(System.getProperty("user.dir"), "src", "main",
                "java", "com", "erp", "inventory").normalize();
        try (var paths = Files.walk(root))
        {
            assertThat(paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> contains(path, SIMPLE_NAME))
                    .map(path -> root.relativize(path))
                    .toList())
                    .containsExactlyInAnyOrder(
                            Path.of("mapper", SIMPLE_NAME + ".java"),
                            Path.of("service", "impl",
                                    "InvTransferReceiptDiscrepancyReshipEffectOwner.java"));
        }
    }

    private static Configuration configuration() throws Exception
    {
        Configuration configuration = new Configuration();
        configuration.addMapper(
                InvTransferReceiptDiscrepancyReshipMapper.class);
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
