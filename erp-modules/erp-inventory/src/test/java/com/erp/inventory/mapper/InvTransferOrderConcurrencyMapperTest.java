package com.erp.inventory.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.inventory.domain.InvTransferOrder;

@DisplayName("调拨草稿并发更新 Mapper")
class InvTransferOrderConcurrencyMapperTest
{
    private static final String XML =
            "mapper/inventory/InvTransferOrderMapper.xml";

    @Test
    @DisplayName("草稿更新同时校验状态和版本且不能回写流程状态")
    void draftUpdateShouldUseStatusAndVersionCas() throws Exception
    {
        Configuration configuration = new Configuration();
        configuration.getTypeAliasRegistry().registerAlias(
                "InvTransferOrder", InvTransferOrder.class);
        try (InputStream input = Resources.getResourceAsStream(XML))
        {
            new XMLMapperBuilder(input, configuration, XML,
                    configuration.getSqlFragments()).parse();
        }

        InvTransferOrder order = new InvTransferOrder();
        order.setTransferId(900L);
        order.setVersion(7L);
        order.setStatus("submitted");
        order.setRemark("陈旧客户端状态不得覆盖流程状态");
        order.setUpdateBy("operator");
        BoundSql boundSql = configuration.getMappedStatement(
                InvTransferOrderMapper.class.getName()
                        + ".updateDraftIfVersionMatches")
                .getBoundSql(order);
        String sql = normalize(boundSql.getSql());

        assertThat(sql).contains(
                "update inv_transfer_order",
                "version = version + 1",
                "where transfer_id = ? and status = 'draft' and version = ?");
        assertThat(sql).doesNotContain(
                "status = ?",
                "approval_instance_id = ?",
                "submitted_time = ?");
        assertThat(boundSql.getParameterMappings())
                .extracting(mapping -> mapping.getProperty())
                .contains("transferId", "version");
    }

    private static String normalize(String sql)
    {
        return sql.replaceAll("\\s+", " ").trim().toLowerCase();
    }
}
