package com.erp.inventory.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.List;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import com.erp.inventory.domain.InvTransferOrder;

class NewBusinessOpsMapperBindingTest
{
    @Test
    void transferOpsSummaryBindsSelectedOrganizationScope() throws Exception
    {
        Configuration configuration = new Configuration();
        configuration.getTypeAliasRegistry().registerAlias(
                "InvTransferOrder", InvTransferOrder.class);
        parse(configuration, "mapper/inventory/InvTransferOrderMapper.xml");

        InvTransferOrder query = new InvTransferOrder();
        query.getParams().put("scopeDeptIds", List.of(20L, 21L));
        query.getParams().put("hideDraftTransfers", true);
        BoundSql boundSql = configuration.getMappedStatement(
                InvTransferOrderMapper.class.getName() + ".selectOpsSummary")
                .getBoundSql(query);
        String sql = normalize(boundSql.getSql());

        assertThat(sql).contains(
                "o.status = 'submitted'",
                "o.status in ('approved', 'reserved', 'partial_delivered')",
                "o.status in ('partial_delivered', 'delivered', 'partial_received')",
                "o.transfer_type = 'store_return'",
                "from inv_transfer_discrepancy",
                "o.status <> 'draft'")
                .containsPattern(
                        "where o\\.status <> 'draft' and \\(o\\.from_dept_id in \\(\\s*\\?\\s*,\\s*\\?\\s*\\)\\s+"
                                + "or o\\.to_dept_id in \\(\\s*\\?\\s*,\\s*\\?\\s*\\)\\)");
        assertThat(boundSql.getParameterMappings())
                .hasSizeGreaterThanOrEqualTo(8);
    }

    private static void parse(Configuration configuration, String resource)
            throws Exception
    {
        try (InputStream input = Resources.getResourceAsStream(resource))
        {
            new XMLMapperBuilder(input, configuration, resource,
                    configuration.getSqlFragments()).parse();
        }
    }

    private static String normalize(String sql)
    {
        return sql.replaceAll("\\s+", " ").trim().toLowerCase();
    }
}
