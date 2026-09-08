package com.erp.inventory.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.inventory.domain.InvPurchaseOrder;
import com.erp.inventory.domain.InvPurchaseReturn;

@DisplayName("采购退货 Mapper 绑定")
class InvPurchaseReturnMapperBindingTest
{
    @Test
    @DisplayName("来源采购单与退货查询都有 XML 绑定")
    void shouldBindPurchaseReturnStatements() throws Exception
    {
        Configuration configuration = new Configuration();
        configuration.getTypeAliasRegistry().registerAlias(
                "InvPurchaseOrder", InvPurchaseOrder.class);
        configuration.getTypeAliasRegistry().registerAlias(
                "InvPurchaseReturn", InvPurchaseReturn.class);
        parseMapper(configuration, "mapper/inventory/InvPurchaseOrderMapper.xml");
        parseMapper(configuration, "mapper/inventory/InvPurchaseReturnMapper.xml");

        assertThat(configuration.hasStatement(InvPurchaseOrderMapper.class.getName()
                + ".selectReturnablePurchaseOrderList")).isTrue();
        assertThat(configuration.hasStatement(InvPurchaseReturnMapper.class.getName()
                + ".selectMyInvPurchaseReturnList")).isTrue();
        assertThat(configuration.hasStatement(InvPurchaseReturnMapper.class.getName()
                + ".updateInvPurchaseReturnStatus")).isTrue();
    }

    @Test
    @DisplayName("来源单和我的退货使用精确仓库及申请人条件")
    void sqlShouldUseExactWarehouseAndApplicantBoundaries() throws Exception
    {
        String purchaseXml = resourceText("mapper/inventory/InvPurchaseOrderMapper.xml");
        String returnXml = resourceText("mapper/inventory/InvPurchaseReturnMapper.xml");

        assertThat(purchaseXml).contains(
                "where o.shop_dept_id = #{shopDeptId}",
                "o.status not in ('draft', 'cancelled')",
                "purchase_return.status in ('submitted', 'returned')",
                "return_detail.purchase_detail_id = source_detail.detail_id");
        assertThat(returnXml).contains(
                "where r.applicant_id = #{applicantId}",
                "remark = #{remark}",
                "set status = #{status}, update_by = #{updateBy}, update_time = sysdate()");
    }

    private static void parseMapper(Configuration configuration, String resource)
            throws Exception
    {
        try (InputStream input = Resources.getResourceAsStream(resource))
        {
            new XMLMapperBuilder(input, configuration, resource,
                    configuration.getSqlFragments()).parse();
        }
    }

    private static String resourceText(String resource) throws Exception
    {
        try (InputStream input = Resources.getResourceAsStream(resource))
        {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
