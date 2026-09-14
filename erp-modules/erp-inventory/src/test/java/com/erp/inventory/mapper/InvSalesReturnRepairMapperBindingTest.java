package com.erp.inventory.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import com.erp.inventory.domain.InvSalesOrder;
import com.erp.inventory.domain.InvSalesReturn;
import com.erp.inventory.domain.InvSalesReturnDetail;

class InvSalesReturnRepairMapperBindingTest
{
    @Test
    void contentSaveMayClearRemarksButLifecycleUpdatesMustPreserveThem() throws Exception
    {
        Configuration config = configuration();
        InvSalesReturn update = new InvSalesReturn();
        update.setReturnId(3L);
        update.setStatus("returned");
        update.getParams().put("expectedStatus", "submitted");
        String stateSql = sql(config, InvSalesReturnMapper.class, "updateInvSalesReturn", update);
        assertThat(stateSql).contains("and status = ?").doesNotContain("remark =", "sales_order_id =");
        update.getParams().put("updateContent", true);
        update.setRemark(null);
        String contentSql = sql(config, InvSalesReturnMapper.class, "updateInvSalesReturn", update);
        assertThat(contentSql).contains("remark = ?", "sales_order_id = ?");

        InvSalesOrder order = new InvSalesOrder();
        order.setOrderId(1L);
        order.setStatus("submitted");
        assertThat(sql(config, InvSalesOrderMapper.class, "updateInvSalesOrder", order)).doesNotContain("remark =");
        order.getParams().put("updateContent", true);
        assertThat(sql(config, InvSalesOrderMapper.class, "updateInvSalesOrder", order)).contains("remark = ?");
    }

    @Test
    void materialHistoryUsesTypeAndSourceHistoryKeepsUnresolvedLegacyOccupancy() throws Exception
    {
        Configuration config = configuration();
        String materialSql = sql(config, InvSalesReturnDetailMapper.class, "sumHistoricalReturnQuantityByItem",
                Map.of("salesOrderId", 1L, "itemType", "gift", "itemId", 101L, "excludeReturnId", 9L));
        assertThat(materialSql).contains("coalesce(nullif(d.item_type, ''), 'product') = ?",
                "coalesce(d.item_id, d.product_id) = ?", "r.return_id != ?", "for update");
        String detailSql = sql(config, InvSalesReturnDetailMapper.class, "sumHistoricalReturnQuantityBySalesDetailId",
                Map.of("salesOrderId", 1L, "salesDetailId", 10L, "excludeReturnId", 9L));
        assertThat(detailSql).contains("d.sales_detail_id = ?", "d.sales_detail_id is null", "sd.order_id = r.sales_order_id", "for update");
    }

    private static Configuration configuration() throws Exception
    {
        Configuration config = new Configuration();
        config.getTypeAliasRegistry().registerAlias("InvSalesOrder", InvSalesOrder.class);
        config.getTypeAliasRegistry().registerAlias("InvSalesReturn", InvSalesReturn.class);
        config.getTypeAliasRegistry().registerAlias("InvSalesReturnDetail", InvSalesReturnDetail.class);
        for (String name : new String[] { "InvSalesOrderMapper", "InvSalesReturnMapper", "InvSalesReturnDetailMapper" })
        {
            String resource = "mapper/inventory/" + name + ".xml";
            try (InputStream input = Resources.getResourceAsStream(resource))
            {
                new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
            }
        }
        return config;
    }

    private static String sql(Configuration config, Class<?> mapper, String statement, Object parameter)
    {
        return config.getMappedStatement(mapper.getName() + "." + statement)
                .getBoundSql(parameter).getSql().replaceAll("\\s+", " ").trim();
    }
}
