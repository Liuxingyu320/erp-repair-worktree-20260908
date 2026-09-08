package com.erp.system.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Locale;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import com.erp.system.api.domain.SysDept;
import com.erp.system.api.domain.SysRole;
import com.erp.system.api.domain.SysUser;

class SysUserManageMapperSourceTest
{
    private static final String XML = "mapper/system/SysUserMapper.xml";

    @Test
    void managementListSelectsProductApprovedIdentityColumnsWithoutSensitiveProfileFields()
            throws Exception
    {
        Configuration configuration = parse();
        SysUser query = new SysUser();
        query.getParams().put("dataScope", "");
        BoundSql bound = configuration.getMappedStatement(
                "com.erp.system.mapper.SysUserMapper.selectUserManageList").getBoundSql(query);
        String sql = normalize(bound.getSql());
        String projection = sql.substring(0, sql.indexOf(" from sys_user u"));

        assertThat(projection)
                .contains("u.user_name", "u.nick_name", "u.phonenumber")
                .doesNotContain("'****'")
                .doesNotContain("u.email", "u.password", "u.login_ip", "u.login_date", "u.remark",
                        "p.id_number", "p.current_address", "p.health_status", "p.emergency_contact",
                        "p.bank_account", "p.bank_name", "base_salary", "salary_total");
        assertThat(sql).doesNotContain("u.user_name regexp '^1[3-9][0-9]{9}$'");
    }

    @Test
    void externalSelectorsDoNotReuseFullUserEntityQuery() throws Exception
    {
        Configuration configuration = parse();
        SysUser query = new SysUser();
        query.setRoleId(2L);
        query.setStatus("0");
        query.getParams().put("dataScope", "");
        for (String statement : new String[] { "selectAllocatedAssignmentList",
                "selectUnallocatedAssignmentList", "selectSalaryUserOptions" })
        {
            String sql = normalize(configuration.getMappedStatement(
                    "com.erp.system.mapper.SysUserMapper." + statement).getBoundSql(query).getSql());
            assertThat(sql).contains("'****'")
                    .doesNotContain("u.email", "u.password", "p.id_number", "p.bank_account",
                            "sys_user_profile");
        }
    }

    @Test
    void setupSummaryUsesTheSameScopedFiltersAndPartitionsConfigurationStates() throws Exception
    {
        Configuration configuration = parse();
        SysUser query = new SysUser();
        query.setNickName("张");
        query.getParams().put("dataScope", "and u.dept_id in (42)");
        String sql = normalize(configuration.getMappedStatement(
                "com.erp.system.mapper.SysUserMapper.selectUserSetupSummary").getBoundSql(query).getSql());

        assertThat(sql)
                .contains("count(1) as total_count")
                .contains("as active_count")
                .contains("as disabled_count")
                .contains("as missing_role_count")
                .contains("as missing_shop_scope_count")
                .contains("as complete_count")
                .contains("u.nick_name like concat('%', ?, '%')")
                .contains("and u.dept_id in (42)")
                .contains("coalesce(urc.role_count, 0) = 0")
                .contains("coalesce(urc.role_count, 0) > 0 and coalesce(usc.shop_scope_count, 0) = 0")
                .contains("coalesce(urc.role_count, 0) > 0 and coalesce(usc.shop_scope_count, 0) > 0");
    }

    private Configuration parse() throws Exception
    {
        Configuration configuration = new Configuration();
        configuration.getTypeAliasRegistry().registerAlias("SysUser", SysUser.class);
        configuration.getTypeAliasRegistry().registerAlias("SysDept", SysDept.class);
        configuration.getTypeAliasRegistry().registerAlias("SysRole", SysRole.class);
        try (InputStream input = Resources.getResourceAsStream(XML))
        {
            new XMLMapperBuilder(input, configuration, XML, configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    private String normalize(String sql)
    {
        return sql.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }
}
