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

@DisplayName("V2调拨差异裁决异步子调拨惰性持久化契约")
class
        InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowMapperBindingTest
{
    private static final String SIMPLE_NAME =
            "InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowMapper";
    private static final String NAMESPACE =
            "com.erp.inventory.mapper." + SIMPLE_NAME;
    private static final String XML =
            "mapper/inventory/" + SIMPLE_NAME + ".xml";

    @Test
    @DisplayName("三个窄方法全部绑定到可解析XML")
    void shouldBindEveryNarrowMethod() throws Exception
    {
        Configuration configuration = configuration();

        assertThat(Arrays.stream(
                InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowMapper
                        .class.getDeclaredMethods())
                .map(method -> NAMESPACE + "." + method.getName()))
                .allMatch(configuration::hasStatement)
                .containsExactlyInAnyOrder(
                        NAMESPACE + ".selectLinkForUpdate",
                        NAMESPACE + ".insertLink",
                        NAMESPACE + ".selectChildForUpdate");
    }

    @Test
    @DisplayName("全部结果映射属性都必须存在于目标JavaBean")
    void shouldMapOnlyWritableBeanProperties() throws Exception
    {
        Configuration configuration = configuration();

        for (String id : Set.of("WorkflowLinkResult", "ChildFactResult"))
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
    @DisplayName("只锁唯一关系和子调拨事实且DML仅追加关系")
    void shouldFreezeLocksAndConditionalInsert() throws Exception
    {
        String xml = normalized();
        String linkLock = between(xml,
                "<select id=\"selectlinkforupdate\"", "</select>");
        String insert = between(xml,
                "<insert id=\"insertlink\"", "</insert>");
        String childLock = between(xml,
                "<select id=\"selectchildforupdate\"", "</select>");

        assertThat(linkLock).contains(
                "from inv_transfer_receipt_discrepancy_adjudication_workflow_link",
                "where adjudication_action_id = #{actionid}",
                "for update");
        assertThat(insert).contains(
                "insert into inv_transfer_receipt_discrepancy_adjudication_workflow_link",
                "from inv_transfer_receipt_discrepancy_case c",
                "inv_transfer_receipt_discrepancy_adjudication_action x",
                "c.discrepancy_quantity &gt;= #{link.quantity}",
                "c.discrepancy_amount &gt;= #{link.amount}",
                "inner join inv_transfer_shipment_receipt_allocation ra",
                "ra.receipt_id = c.receipt_id",
                "sa.shipment_id = c.shipment_id",
                "inner join inv_transfer_shipment_allocation sa",
                "inner join inv_transfer_order p",
                "inner join inv_transfer_order child",
                "inner join inv_transfer_detail d",
                "x.execution_status = 'pending'",
                "x.execution_effect_reference is null",
                "child.source_business_id = x.adjudication_action_id",
                "d.delivered_quantity = 0",
                "#{link.reservationcount} = 1",
                "#{link.reservedquantity} = #{link.quantity}",
                "'transfer_discrepancy_reship'",
                "'transfer_discrepancy_return'",
                "'available_stock'", "'quarantine_detail'",
                "ra.damaged_target_balance_id",
                "ra.damaged_target_lot_id",
                "ra.quarantine_location_id",
                "prior.adjudication_action_id = #{link.actionid}",
                "prior.child_transfer_id = #{link.childtransferid}",
                "prior.workflow_fingerprint = #{link.workflowfingerprint}");
        assertThat(childLock).contains(
                "inner join inv_transfer_order child",
                "inner join inv_transfer_detail d",
                "link.adjudication_action_id = #{actionid}",
                "link.child_transfer_id = #{childtransferid}",
                "inventory_write_version = 'v2_detail'",
                "coalesce(legacy_shipment.inventory_write_version, 'legacy') &lt;&gt; 'v2_detail'",
                "from inv_transfer_reservation actual",
                "from inv_transfer_quarantine_reservation actual",
                "standard_reservation.status",
                "standard_reservation.consumed_quantity",
                "standard_reservation.released_quantity",
                "quarantine_reservation.status",
                "quarantine_reservation.consumed_quantity",
                "quarantine_reservation.released_quantity",
                "from inv_transfer_shipment_receipt receipt",
                "from inv_transfer_receipt_discrepancy_case discrepancy",
                "discrepancy.status &lt;&gt; 'resolved'", "for update");

        assertThat(count(xml, "<select ")).isEqualTo(2);
        assertThat(count(xml, "<insert ")).isEqualTo(1);
        assertThat(xml).doesNotContain("<update ", "<delete ");
        assertThat(insert).doesNotContain(
                "insert into inv_transfer_order",
                "insert into inv_transfer_detail",
                "insert into inv_transfer_reservation",
                "insert into inv_transfer_shipment",
                "insert into inv_transfer_shipment_receipt",
                "insert into inv_stock",
                "insert into inv_stock_balance_detail",
                "insert into inv_inventory_lot",
                "insert into inv_inventory_location",
                "insert into inv_inventory_serial",
                "insert into inv_stock_ledger_detail",
                " finance");
    }

    @Test
    @DisplayName("Mapper只服务于补发退回派发所有者和未接线完成事务")
    void shouldOnlyServeAsyncOwnersAndCompletionTransaction()
            throws Exception
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
                                    "InvTransferReceiptDiscrepancyReshipEffectOwner.java"),
                            Path.of("service", "impl",
                                    "InvTransferReceiptDiscrepancyReturnEffectOwner.java"),
                            Path.of("service", "impl",
                                    "InvTransferReceiptDiscrepancyAdjudicationAsyncCompletionTransaction.java"));
        }
    }

    private static Configuration configuration() throws Exception
    {
        Configuration configuration = new Configuration();
        configuration.addMapper(
                InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowMapper
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
