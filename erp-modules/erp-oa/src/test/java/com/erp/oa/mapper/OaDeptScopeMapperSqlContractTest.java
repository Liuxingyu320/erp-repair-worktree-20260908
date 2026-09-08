package com.erp.oa.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("OA 部门范围 Mapper SQL 契约")
class OaDeptScopeMapperSqlContractTest
{
    private static final String RESOURCE = "mapper/oa/OaDeptScopeMapper.xml";

    @Test
    @DisplayName("授权部门去重排序兼容 MySQL 严格模式并保持稳定顺序")
    void shouldOrderDistinctAuthorizedDepartmentsOutsideDerivedTable() throws Exception
    {
        Configuration configuration = new Configuration();
        try (InputStream stream = Resources.getResourceAsStream(RESOURCE))
        {
            new XMLMapperBuilder(stream, configuration, RESOURCE,
                    configuration.getSqlFragments()).parse();
        }

        BoundSql boundSql = configuration.getMappedStatement(
                OaDeptScopeMapper.class.getName() + ".selectUserAuthorizedOaDeptIds")
                .getBoundSql(Map.of("userId", 7L));
        String sql = normalize(boundSql.getSql());

        assertThat(sql)
                .startsWith("select authorized.dept_id from (")
                .contains(
                        "select distinct d.dept_id, d.parent_id, d.order_num",
                        "where us.user_id = ?",
                        "d.dept_type in ('STORE', 'WAREHOUSE')",
                        ") authorized order by authorized.parent_id, authorized.order_num, authorized.dept_id")
                .doesNotContain("select distinct authorized.dept_id");
        assertThat(boundSql.getParameterMappings())
                .extracting(mapping -> mapping.getProperty())
                .containsExactly("userId");
    }

    private static String normalize(String sql)
    {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
