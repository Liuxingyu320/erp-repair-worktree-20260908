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

@DisplayName("调拨发货规划只读 Mapper")
class InvTransferShipmentPlanningMapperBindingTest
{
    private static final String NAMESPACE =
            "com.erp.inventory.mapper.InvTransferShipmentPlanningMapper";
    private static final String XML =
            "mapper/inventory/InvTransferShipmentPlanningMapper.xml";

    @Test
    @DisplayName("全部方法均绑定到可解析的 XML 查询")
    void shouldBindEveryMapperMethod() throws Exception
    {
        Configuration configuration = configuration();

        Arrays.stream(InvTransferShipmentPlanningMapper.class
                .getDeclaredMethods())
                .forEach(method -> assertThat(configuration.hasStatement(
                        NAMESPACE + "." + method.getName()))
                        .as(method.getName()).isTrue());
    }

    @Test
    @DisplayName("物料和余额集合展开为绑定参数而不是字符串拼接")
    void shouldExpandBatchFactsAsBoundParameters() throws Exception
    {
        Configuration configuration = configuration();
        List<InvShipmentPlanningItemKey> keys = List.of(
                new InvShipmentPlanningItemKey("product", 1001L),
                new InvShipmentPlanningItemKey("material", 1002L));
        String balanceSql = normalized(configuration.getMappedStatement(
                NAMESPACE + ".selectEligibleBalances").getBoundSql(Map.of(
                        "warehouseId", 301L, "itemKeys", keys)).getSql());
        String serialSql = normalized(configuration.getMappedStatement(
                NAMESPACE + ".selectAvailableSerials").getBoundSql(Map.of(
                        "balanceIds", List.of(1L, 2L))).getSql());

        assertThat(balanceSql).contains(
                "b.warehouse_id = ?",
                "(b.item_type = ? and b.item_id = ?) or (b.item_type = ? and b.item_id = ?)");
        assertThat(serialSql).contains("balance_id in ( ? , ? )");
        assertThat(balanceSql).doesNotContain("product", "material", "1001");
    }

    @Test
    @DisplayName("明细库存读取批量化且排除不可发批次与库位")
    void shouldRemainReadOnlyBatchedAndFailClosed() throws Exception
    {
        String xml = normalized(resourceText());

        assertThat(xml).contains(
                "from inv_stock_balance_detail b",
                "b.available_quantity &gt; 0",
                "l.qc_status in ('passed', 'concession')",
                "l.lot_status = 'active'",
                "l.expiry_date &gt;= current_date",
                "w.status = '0'",
                "w.is_virtual = '0'",
                "w.location_type not in ('receiving', 'quarantine', 'virtual')",
                "collection=\"itemkeys\"",
                "collection=\"balanceids\"");
        assertThat(xml).doesNotContain("select *", " for update",
                "insert into", "update inv_", "delete from");
    }

    private static String resourceText() throws Exception
    {
        try (InputStream input = Resources.getResourceAsStream(XML))
        {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
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

    private static String normalized(String value)
    {
        return value.toLowerCase().replaceAll("\\s+", " ").trim();
    }
}
