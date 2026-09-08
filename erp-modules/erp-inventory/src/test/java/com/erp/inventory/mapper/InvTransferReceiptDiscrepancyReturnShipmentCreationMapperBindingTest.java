package com.erp.inventory.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("V2差异退回发货条件推进Mapper契约")
class
        InvTransferReceiptDiscrepancyReturnShipmentCreationMapperBindingTest
{
    private static final String SIMPLE_NAME =
            "InvTransferReceiptDiscrepancyReturnShipmentCreationMapper";
    private static final String NAMESPACE =
            "com.erp.inventory.mapper." + SIMPLE_NAME;
    private static final String XML =
            "mapper/inventory/" + SIMPLE_NAME + ".xml";

    @Test
    @DisplayName("两个条件推进方法均绑定到可解析XML")
    void shouldBindEveryMethod() throws Exception
    {
        Configuration configuration = configuration();
        assertThat(Arrays.stream(
                InvTransferReceiptDiscrepancyReturnShipmentCreationMapper
                        .class.getDeclaredMethods())
                .map(method -> NAMESPACE + "." + method.getName()))
                .allMatch(configuration::hasStatement)
                .containsExactlyInAnyOrder(
                        NAMESPACE + ".advanceChildDetail",
                        NAMESPACE + ".advanceChildOrder");
    }

    @Test
    @DisplayName("明细和主单只允许按锁定旧事实条件推进")
    void shouldConditionEveryProgressMutation() throws Exception
    {
        String xml = normalized();
        String detail = between(xml,
                "<update id=\"advancechilddetail\"", "</update>");
        String order = between(xml,
                "<update id=\"advancechildorder\"", "</update>");

        assertThat(detail).contains(
                "where detail_id = #{prepared.fact.childtransferdetailid}",
                "and transfer_id = #{prepared.fact.childtransferid}",
                "and product_id &lt;=&gt; #{prepared.fact.productid}",
                "and quantity = #{prepared.fact.requestedquantity}",
                "coalesce(delivered_quantity, 0) = #{prepared.deliveredbefore}",
                "coalesce(received_quantity, 0) = #{prepared.receivedbefore}",
                "#{prepared.deliveredafter} &lt;= quantity");
        assertThat(order).contains(
                "where transfer_id = #{prepared.fact.childtransferid}",
                "and status = #{prepared.statusbefore}",
                "and version = #{prepared.orderversionbefore}",
                "and approval_round = #{prepared.fact.reservationround}",
                "and source_business_type = #{prepared.fact.sourcebusinesstype}",
                "and source_business_id = #{prepared.fact.sourcebusinessid}");
        assertThat(count(xml, "<update ")).isEqualTo(2);
        assertThat(xml).doesNotContain("<select ", "<insert ",
                "<delete ", "for update", "jdbc:", "bosserp_new",
                "nacos", "docker");
    }

    @Test
    @DisplayName("专属Mapper仅由未接线创建Service引用")
    void shouldOnlyServeUnwiredCreationOwner() throws Exception
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
                                    "InvTransferReceiptDiscrepancyReturnShipmentCreationService.java"));
        }
    }

    private static Configuration configuration() throws Exception
    {
        Configuration configuration = new Configuration();
        configuration.addMapper(
                InvTransferReceiptDiscrepancyReturnShipmentCreationMapper
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
