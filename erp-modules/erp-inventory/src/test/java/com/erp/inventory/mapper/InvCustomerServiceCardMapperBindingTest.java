package com.erp.inventory.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import com.erp.inventory.domain.vo.InvCustomerServiceAuditQuery;
import com.erp.inventory.domain.vo.InvCustomerServiceCardQuery;

class InvCustomerServiceCardMapperBindingTest
{
    private static final String MAPPER =
            "com.erp.inventory.mapper.InvCustomerServiceCardMapper";
    private static final String XML =
            "mapper/inventory/InvCustomerServiceCardMapper.xml";

    @Test
    void auditQueryBindsToBothLogAndCustomerStoreScopes() throws Exception
    {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(XML))
        {
            new XMLMapperBuilder(input, configuration, XML,
                    configuration.getSqlFragments()).parse();
        }
        assertThat(configuration.hasStatement(MAPPER + ".selectAuditList"))
                .isTrue();

        InvCustomerServiceAuditQuery query = new InvCustomerServiceAuditQuery();
        query.setShopDeptId(10L);
        query.setCustomerId(50L);
        query.setChangeType("ADD_RECORD");
        query.setSourceClient("MOBILE");
        query.setOperatorName("store-user");
        query.getParams().put("beginTime", "2026-07-01 00:00:00");
        query.getParams().put("endTime", "2026-07-31 23:59:59");

        BoundSql boundSql = configuration.getMappedStatement(
                MAPPER + ".selectAuditList").getBoundSql(query);
        String sql = boundSql.getSql().replaceAll("\\s+", " ").trim()
                .toLowerCase();

        assertThat(sql).contains(
                "l.shop_dept_id = ?",
                "c.shop_dept_id = ?",
                "l.customer_id = ?",
                "l.change_type = ?",
                "l.source_client = ?",
                "l.operator_name like concat('%', ?, '%')",
                "l.create_time >= ?",
                "l.create_time <= ?");
        assertThat(sql).doesNotContain("request_key", "service_note",
                "contact_phone", "select *");
    }

    @Test
    void cardKeywordSearchAndMetadataMatchTheServiceCardContract()
            throws Exception
    {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(XML))
        {
            new XMLMapperBuilder(input, configuration, XML,
                    configuration.getSqlFragments()).parse();
        }
        InvCustomerServiceCardQuery query = new InvCustomerServiceCardQuery();
        query.setShopDeptId(10L);
        query.setKeyword("岩茶");

        BoundSql boundSql = configuration.getMappedStatement(
                MAPPER + ".selectCardList").getBoundSql(query);
        String sql = boundSql.getSql().replaceAll("\\s+", " ").trim()
                .toLowerCase();

        assertThat(sql).contains(
                "c.shop_dept_id = ?",
                "c.customer_name like concat('%', ?, '%')",
                "c.contact_phone like concat('%', ?, '%')",
                "p.tea_preferences like concat('%', ?, '%')",
                "p.preference_tags like concat('%', ?, '%')",
                "p.brewing_service_preferences like concat('%', ?, '%')",
                "p.cautions like concat('%', ?, '%')",
                "c.create_by",
                "coalesce(p.update_by, c.update_by, c.create_by) update_by");
        Set<String> properties = configuration.getResultMap(
                MAPPER + ".CardResult").getResultMappings().stream()
                .map(mapping -> mapping.getProperty())
                .collect(Collectors.toSet());
        assertThat(properties).contains("createBy", "createTime", "updateBy",
                "updateTime");
    }
}
