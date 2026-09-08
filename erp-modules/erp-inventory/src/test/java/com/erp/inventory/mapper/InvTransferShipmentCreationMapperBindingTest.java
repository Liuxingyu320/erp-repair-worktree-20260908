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

@DisplayName("V2调拨发货写 Mapper")
class InvTransferShipmentCreationMapperBindingTest
{
    private static final String NAMESPACE =
            "com.erp.inventory.mapper.InvTransferShipmentCreationMapper";
    private static final String XML =
            "mapper/inventory/InvTransferShipmentCreationMapper.xml";

    @Test
    @DisplayName("全部锁定与写入方法均有可解析 XML 绑定")
    void shouldBindEveryMethod() throws Exception
    {
        Configuration configuration = configuration();

        Arrays.stream(InvTransferShipmentCreationMapper.class
                .getDeclaredMethods()).forEach(method ->
                assertThat(configuration.hasStatement(
                        NAMESPACE + "." + method.getName()))
                        .as(method.getName()).isTrue());
    }

    @Test
    @DisplayName("业务行按固定键排序锁定且集合使用绑定参数")
    void shouldUseDeterministicBoundLocks() throws Exception
    {
        Configuration configuration = configuration();
        List<InvShipmentPlanningItemKey> itemKeys = List.of(
                new InvShipmentPlanningItemKey("product", 1001L));
        String detailSql = sql(configuration,
                "selectTransferDetailIdsForUpdate",
                Map.of("transferId", 900L));
        String policySql = sql(configuration, "selectPoliciesForUpdate",
                Map.of("itemKeys", itemKeys));
        String balanceSql = sql(configuration, "selectBalancesForUpdate",
                Map.of("warehouseId", 301L, "itemKeys", itemKeys));
        String serialSql = sql(configuration, "selectSerialsForUpdate",
                Map.of("balanceIds", List.of(1L, 2L)));

        assertThat(detailSql).contains("order by detail_id for update");
        assertThat(policySql).contains(
                "item_type = ? and item_id = ?",
                "order by item_type, item_id for update");
        assertThat(balanceSql).contains(
                "warehouse_id = ?", "order by balance_id for update");
        assertThat(serialSql).contains(
                "balance_id in ( ? , ? )", "order by serial_id for update");
        assertThat(policySql + balanceSql).doesNotContain("1001");
    }

    @Test
    @DisplayName("余额和序列号写入同时约束版本归属与非负数量")
    void shouldGuardEveryStockMutation() throws Exception
    {
        String xml = normalized(resourceText());
        String balance = between(xml, "<update id=\"deductbalance\"",
                "</update>");
        String serial = between(xml, "<update id=\"markserialshipped\"",
                "</update>");

        assertThat(balance).contains(
                "version = #{version}",
                "current_quantity &gt;= #{quantity}",
                "available_quantity &gt;= #{quantity}",
                "total_cost &gt;= #{deductcost}");
        assertThat(serial).contains(
                "balance_id = #{balanceid}",
                "warehouse_id = #{warehouseid}",
                "lot_id = #{lotid}",
                "location_id = #{locationid}",
                "serial_status = 'available'");
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
