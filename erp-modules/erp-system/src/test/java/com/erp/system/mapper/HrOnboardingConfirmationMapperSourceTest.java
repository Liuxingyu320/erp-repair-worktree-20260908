package com.erp.system.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import com.erp.system.api.domain.SysDept;
import com.erp.system.api.domain.SysRole;
import com.erp.system.api.domain.SysUser;
import com.erp.system.domain.SysUserPost;
import com.erp.system.domain.SysUserRole;
import com.erp.system.domain.SysUserShop;
import com.erp.system.domain.vo.HrOnboardingQuery;

class HrOnboardingConfirmationMapperSourceTest
{
    @Test
    void everyModifiedMapperMethodHasAParseableXmlBinding() throws Exception
    {
        assertBound(HrOnboardingMapper.class, "mapper/system/HrOnboardingMapper.xml");
        assertBound(SysUserMapper.class, "mapper/system/SysUserMapper.xml");
        assertBound(SysUserProfileMapper.class, "mapper/system/SysUserProfileMapper.xml");
        assertBound(SysUserRoleMapper.class, "mapper/system/SysUserRoleMapper.xml");
        assertBound(SysUserPostMapper.class, "mapper/system/SysUserPostMapper.xml");
        assertBound(SysUserShopMapper.class, "mapper/system/SysUserShopMapper.xml");
        assertBound(SysRoleMapper.class, "mapper/system/SysRoleMapper.xml");
    }

    @Test
    void confirmationAndConflictSqlAreScopedOptimisticAndNonDestructive() throws Exception
    {
        String onboarding = resource("mapper/system/HrOnboardingMapper.xml");
        String users = resource("mapper/system/SysUserMapper.xml");
        String profiles = resource("mapper/system/SysUserProfileMapper.xml");
        String roles = resource("mapper/system/SysUserRoleMapper.xml");
        String posts = resource("mapper/system/SysUserPostMapper.xml");
        String shops = resource("mapper/system/SysUserShopMapper.xml");

        assertThat(onboarding)
                .contains("id=\"confirmOnboardingByVersion\"")
                .contains("confirm_idempotency_key = #{confirmIdempotencyKey}")
                .contains("and version = #{version}")
                .contains("and status = 'READY'")
                .contains("id=\"selectScopedConflictCandidates\"")
                .contains("${query.params.dataScope}")
                .contains("id=\"selectGlobalOpenIdentitySetForUpdate\"")
                .contains("o.status in ('DRAFT', 'READY')")
                .contains("for update");
        String openIdentityLock = onboarding.substring(
                onboarding.indexOf("id=\"selectGlobalOpenIdentitySetForUpdate\""),
                onboarding.indexOf("<insert id=\"insertOnboarding\""));
        assertThat(openIdentityLock)
                .contains("select o.onboarding_id, o.status", "from hr_onboarding o FORCE INDEX (PRIMARY)",
                        "order by o.onboarding_id asc", "for update")
                .doesNotContain("o.*", "join sys_dept", "dataScope", "target_dept_id",
                        "employee_name", "phone_number as", "id_number as", "o.onboarding_id &lt;&gt;");
        assertThat(users)
                .contains("id=\"selectScopedOnboardingConflictUsers\"")
                .contains("id=\"selectScopedOnboardingConflictUserForUpdate\"")
                .contains("u.phonenumber = #{phone}")
                .contains("p.id_number = #{idNumber}")
                .contains("${query.params.dataScope}")
                .contains("for update");
        String hrUpdate = users.substring(users.indexOf("id=\"updateHrEmployeeProfileUser\""),
                users.indexOf("id=\"updateUserStatus\""));
        assertThat(hrUpdate)
                .contains("dept_id = #{deptId}")
                .doesNotContain("user_name =", "password =");
        assertThat(profiles).contains("direct_supervisor_user_id", "id=\"updateOnboardingProfile\"");
        assertThat(roles).contains("id=\"selectRoleIdsByUserId\"", "id=\"insertUserRoleIfAbsent\"",
                "from sys_role r", "r.status = '0'", "r.del_flag = '0'", "not exists (");
        String lockedExistingRoles = roles.substring(roles.indexOf("id=\"selectRoleIdsByUserId\""),
                roles.indexOf("id=\"insertUserRoleIfAbsent\""));
        assertThat(lockedExistingRoles).contains("for update");
        assertThat(posts).contains("id=\"selectPostIdsByUserId\"", "id=\"insertUserPostIfAbsent\"", "where not exists");
        assertThat(shops).contains("id=\"insertUserShopIfAbsent\"", "where not exists");
    }

    @Test
    void globalOpenIdentityLockBoundSqlIsUnscopedMinimalAndPrimaryKeyOrdered() throws Exception
    {
        Configuration onboarding = parse("mapper/system/HrOnboardingMapper.xml");
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("phone", "13800008000");
        parameters.put("idNumber", "110101199001010000");
        parameters.put("employeeNo", "E88");
        BoundSql lock = onboarding.getMappedStatement(HrOnboardingMapper.class.getName()
                + ".selectGlobalOpenIdentitySetForUpdate").getBoundSql(parameters);
        String sql = lock.getSql().replaceAll("\\s+", " ").trim().toLowerCase();

        assertThat(sql)
                .startsWith("select o.onboarding_id, o.status from hr_onboarding o force index (primary)")
                .contains("o.status in ('draft', 'ready')", "o.phone_number = ?", "o.id_number = ?",
                        "o.employee_no = ?")
                .endsWith("order by o.onboarding_id asc for update")
                .doesNotContain("sys_dept", "datascope", "target_dept_id", "select o.*");
    }

    @Test
    void candidateAndRoleLockBoundSqlContainScopeIdentityAndForUpdate() throws Exception
    {
        Configuration users = parse("mapper/system/SysUserMapper.xml");
        HrOnboardingQuery query = new HrOnboardingQuery();
        query.setOnboardingId(42L);
        query.getParams().put("dataScope", "and d.dept_id = 10");
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("query", query);
        parameters.put("userId", 88L);
        parameters.put("phone", "13800008000");
        parameters.put("idNumber", "110101199001010000");
        parameters.put("employeeNo", "E88");
        BoundSql candidate = users.getMappedStatement(SysUserMapper.class.getName()
                + ".selectScopedOnboardingConflictUserForUpdate").getBoundSql(parameters);
        String candidateSql = candidate.getSql().replaceAll("\\s+", " ").trim().toLowerCase();
        assertThat(candidateSql)
                .contains("u.user_id = ?", "u.phonenumber = ?", "p.id_number = ?", "p.employee_no = ?",
                        "d.dept_id = 10")
                .endsWith("for update");

        Configuration roles = parse("mapper/system/SysRoleMapper.xml");
        BoundSql role = roles.getMappedStatement(SysRoleMapper.class.getName()
                + ".selectRoleByIdForUpdate").getBoundSql(7L);
        assertThat(role.getSql().replaceAll("\\s+", " ").trim().toLowerCase())
                .contains("from sys_role r", "where r.role_id = ?")
                .endsWith("for update");
    }

    private String resource(String path) throws Exception
    {
        return new String(Resources.getResourceAsStream(path).readAllBytes(), StandardCharsets.UTF_8);
    }

    private void assertBound(Class<?> mapperType, String resource) throws Exception
    {
        Configuration configuration = parse(resource);
        for (java.lang.reflect.Method method : mapperType.getDeclaredMethods())
        {
            assertThat(configuration.hasStatement(mapperType.getName() + "." + method.getName()))
                    .as("%s.%s", mapperType.getSimpleName(), method.getName()).isTrue();
        }
    }

    private Configuration parse(String resource) throws Exception
    {
        Configuration configuration = new Configuration();
        HealthCertificateMapperFragments.register(configuration);
        configuration.getTypeAliasRegistry().registerAlias("SysDept", SysDept.class);
        configuration.getTypeAliasRegistry().registerAlias("SysRole", SysRole.class);
        configuration.getTypeAliasRegistry().registerAlias("SysUser", SysUser.class);
        configuration.getTypeAliasRegistry().registerAlias("SysUserPost", SysUserPost.class);
        configuration.getTypeAliasRegistry().registerAlias("SysUserRole", SysUserRole.class);
        configuration.getTypeAliasRegistry().registerAlias("SysUserShop", SysUserShop.class);
        try (InputStream stream = Resources.getResourceAsStream(resource))
        {
            new XMLMapperBuilder(stream, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return configuration;
    }
}
