package com.erp.system.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import com.erp.system.domain.vo.HrHealthCertificateVo;

class HrHealthCertificateOpsMapperBindingTest
{
    private static final String XML =
            "mapper/system/HrHealthCertificateMapper.xml";
    private static final String STATEMENT = HrHealthCertificateMapper.class
            .getName() + ".selectOpsSummary";

    @Test
    void opsSummaryKeepsDepartmentAndDataScopeFilters() throws Exception
    {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(XML))
        {
            new XMLMapperBuilder(input, configuration, XML,
                    configuration.getSqlFragments()).parse();
        }

        HrHealthCertificateVo query = new HrHealthCertificateVo();
        query.setCurrentDeptId(10L);
        query.getParams().put("dataScope", " and d.dept_id in (10, 11)");
        BoundSql boundSql = configuration.getMappedStatement(STATEMENT)
                .getBoundSql(query);
        String sql = normalize(boundSql.getSql());

        assertThat(sql).contains(
                "c.review_status = 'pending_review'",
                "c.current_flag = 'y'",
                "c.expires_on between current_date()",
                "and u.dept_id = ?",
                "and d.dept_id in (10, 11)");
        assertThat(sql).doesNotContain("select *");
    }

    private static String normalize(String sql)
    {
        return sql.replaceAll("\\s+", " ").trim().toLowerCase();
    }
}
