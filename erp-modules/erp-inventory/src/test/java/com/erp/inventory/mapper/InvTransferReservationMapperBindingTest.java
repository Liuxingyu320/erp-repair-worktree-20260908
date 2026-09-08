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
import com.erp.inventory.domain.InvStock;
import com.erp.inventory.domain.InvTransferReservation;

@DisplayName("调拨库存预留 Mapper 绑定")
class InvTransferReservationMapperBindingTest
{
    private static final String NAMESPACE =
            "com.erp.inventory.mapper.InvTransferReservationMapper";
    private static final String XML =
            "mapper/inventory/InvTransferReservationMapper.xml";

    @Test
    @DisplayName("预留 Mapper 的所有方法均有可解析 XML 语句")
    void shouldBindEveryMapperMethod() throws Exception
    {
        Configuration configuration = new Configuration();
        configuration.getTypeAliasRegistry().registerAlias("InvStock",
                InvStock.class);
        configuration.getTypeAliasRegistry().registerAlias(
                "InvTransferReservation", InvTransferReservation.class);
        try (InputStream input = Resources.getResourceAsStream(XML))
        {
            new XMLMapperBuilder(input, configuration, XML,
                    configuration.getSqlFragments()).parse();
        }

        Arrays.stream(InvTransferReservationMapper.class
                .getDeclaredMethods()).forEach(method ->
                assertThat(configuration.hasStatement(
                        NAMESPACE + "." + method.getName()))
                        .as(method.getName()).isTrue());
    }

    @Test
    @DisplayName("冻结释放与发货消耗保持库存三数量不变量")
    void shouldKeepAtomicStockQuantityInvariant() throws Exception
    {
        String xml = normalized(resourceText());
        String reserve = between(xml, "<update id=\"reservestock\"",
                "</update>");
        String release = between(xml, "<update id=\"releasestock\"",
                "</update>");
        String consume = between(xml,
                "<update id=\"consumereservedstockwithcost\"",
                "</update>");

        assertThat(reserve).contains(
                "locked_quantity = coalesce(locked_quantity, 0) + #{quantity}",
                "available_quantity = coalesce(available_quantity, 0) - #{quantity}",
                "coalesce(available_quantity, 0) &gt;= #{quantity}",
                "version = #{version}");
        assertThat(release).contains(
                "locked_quantity = coalesce(locked_quantity, 0) - #{quantity}",
                "available_quantity = coalesce(available_quantity, 0) + #{quantity}",
                "coalesce(locked_quantity, 0) &gt;= #{quantity}",
                "version = #{version}");
        assertThat(consume).contains(
                "current_quantity = coalesce(current_quantity, 0) - #{quantity}",
                "locked_quantity = coalesce(locked_quantity, 0) - #{quantity}",
                "coalesce(current_quantity, 0) &gt;= #{quantity}",
                "coalesce(locked_quantity, 0) &gt;= #{quantity}",
                "coalesce(total_cost, 0) &gt;= #{deductcost}",
                "version = #{version}");
        assertThat(consume).doesNotContain("available_quantity =");
    }

    @Test
    @DisplayName("批量发货按库存主键固定顺序锁定汇总库存")
    void shouldLockSummaryStocksInDeterministicOrder() throws Exception
    {
        String xml = normalized(resourceText());
        String lock = between(xml,
                "<select id=\"selectstocksbyidsforupdate\"", "</select>");

        assertThat(lock).contains(
                "where stock_id in",
                "#{stockid}",
                "order by stock_id",
                "for update");
    }

    @Test
    @DisplayName("草稿规划只读可用量且不锁行或读取成本")
    void shouldReadDraftAvailabilityWithoutLocksOrCosts() throws Exception
    {
        String xml = normalized(resourceText());
        String planning = between(xml,
                "<select id=\"selectstockforplanning\"", "</select>");

        assertThat(planning).contains(
                "available_quantity", "version",
                "shop_dept_id = #{locationdeptid}",
                "warehouse_id = #{locationdeptid}");
        assertThat(planning).doesNotContain(
                "for update", "cost_price", "total_cost",
                "current_quantity", "locked_quantity");
    }

    @Test
    @DisplayName("提交在锁库前锁定同根路线并原子锁定物料资格")
    void shouldLockRouteAndItemEligibilityWithStock() throws Exception
    {
        String xml = normalized(resourceText());
        String route = between(xml,
                "<select id=\"selectwarehousereplenishmentrouteforupdate\"",
                "</select>");
        String stock = between(xml,
                "<select id=\"selectstockforupdate\"", "</select>");

        assertThat(route).contains(
                "straight_join sys_dept target_dept",
                "straight_join sys_dept business_root",
                "source_dept.dept_type = 'warehouse'",
                "target_dept.dept_type = 'store'",
                "business_root.del_flag = '0'",
                "business_root.status = '0'",
                "substring_index(substring_index(",
                "for update");
        assertThat(stock).contains(
                "straight_join inv_product p",
                "straight_join sys_dept item_owner",
                "straight_join inv_gift_box gift",
                "p.del_flag = '0'",
                "p.status = '0'",
                "p.shop_dept_id is not null",
                "item_owner.del_flag = '0'",
                "item_owner.status = '0'",
                "find_in_set(source_dept.dept_id, item_owner.ancestors)",
                "find_in_set(item_owner.dept_id, source_dept.ancestors)",
                "gift.del_flag = '0'",
                "gift.status = '0'",
                "for update");
    }

    @Test
    @DisplayName("台账消耗与释放均以归属行版本和剩余量做条件更新")
    void shouldGuardLedgerMutationsByVersionAndRemainingQuantity()
            throws Exception
    {
        String xml = normalized(resourceText());
        String consume = between(xml,
                "<update id=\"consumereservation\"", "</update>");
        String release = between(xml,
                "<update id=\"releasereservation\"", "</update>");

        assertThat(consume).contains(
                "where reservation_id = #{reservationid}",
                "version = #{version}",
                "reserved_quantity - consumed_quantity - released_quantity &gt;= #{quantity}");
        assertThat(release).contains(
                "where reservation_id = #{reservationid}",
                "version = #{version}",
                "reserved_quantity - consumed_quantity - released_quantity &gt;= #{quantity}");
    }

    @Test
    @DisplayName("终态必须在数量字段变更前按旧值计算以兼容 MySQL 赋值顺序")
    void shouldResolveTerminalStatusBeforeMutatingLedgerQuantities()
            throws Exception
    {
        String xml = normalized(resourceText());
        String consume = between(xml,
                "<update id=\"consumereservation\"", "</update>");
        String release = between(xml,
                "<update id=\"releasereservation\"", "</update>");

        assertThat(consume).contains(
                "consumed_quantity + released_quantity + #{quantity} = reserved_quantity",
                "then 'consumed' else 'closed' end");
        assertThat(consume.indexOf("set status = case"))
                .isLessThan(consume.indexOf(
                        "consumed_quantity = consumed_quantity + #{quantity}"));

        assertThat(release).contains(
                "consumed_quantity + released_quantity + #{quantity} = reserved_quantity",
                "then 'released' else 'closed' end");
        assertThat(release.indexOf("set status = case"))
                .isLessThan(release.indexOf(
                        "released_quantity = released_quantity + #{quantity}"));
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
        assertThat(from).as(start).isGreaterThanOrEqualTo(0);
        int to = value.indexOf(end, from);
        assertThat(to).as(end).isGreaterThan(from);
        return value.substring(from, to + end.length());
    }
}
