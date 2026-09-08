package com.erp.oa.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class OaSignScopeMapperBindingTest
{
    private static final String RESOURCE = "mapper/oa/OaSignScopeMapper.xml";

    @Test
    void shouldBindDedicatedStoreAndCompanySigningScope() throws Exception
    {
        Configuration configuration = new Configuration();
        try (InputStream stream = Resources.getResourceAsStream(RESOURCE))
        {
            new XMLMapperBuilder(stream, configuration, RESOURCE,
                    configuration.getSqlFragments()).parse();
        }

        assertThat(configuration.hasStatement(OaSignScopeMapper.class.getName()
                + ".countUserSignScope")).isTrue();
        assertThat(configuration.hasStatement(OaSignScopeMapper.class.getName()
                + ".selectSignScopeDeptIds")).isTrue();
        assertThat(configuration.hasStatement(OaSignScopeMapper.class.getName()
                + ".selectUserSignScopeOptions")).isTrue();

        String xml;
        try (InputStream stream = Resources.getResourceAsStream(RESOURCE))
        {
            xml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(xml)
                .contains("target_dept.dept_type in ('STORE', 'COMPANY')")
                .contains("target_dept.dept_id = scope_dept.dept_id")
                .contains("find_in_set(scope_dept.dept_id, target_dept.ancestors)")
                .contains("d.dept_id = #{deptId} or find_in_set(#{deptId}, d.ancestors)")
                .doesNotContain("find_in_set(target_dept.dept_id, scope_dept.ancestors)");
    }

    @Test
    void shouldLeaveGlobalInventoryScopeStoreOnly() throws Exception
    {
        String globalXml;
        try (InputStream stream = Resources.getResourceAsStream("mapper/oa/OaDeptScopeMapper.xml"))
        {
            globalXml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(globalXml)
                .contains("target_dept.dept_type = 'STORE'")
                .doesNotContain("target_dept.dept_type in ('STORE', 'COMPANY')");
    }
}
