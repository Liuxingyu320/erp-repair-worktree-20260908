package com.erp.system.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.erp.system.domain.vo.HrEmployeeQuery;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class HrEmployeeMapperBindingTest
{
    @Test
    void scopedEmployeeQueryBindsFiltersAndCarriesAspectDataScopeIntoBoundSql()
    {
        Configuration configuration=new Configuration();
        configuration.getTypeAliasRegistry().registerAlias("SysUser", com.erp.system.api.domain.SysUser.class);
        configuration.getTypeAliasRegistry().registerAlias("SysDept", com.erp.system.api.domain.SysDept.class);
        configuration.getTypeAliasRegistry().registerAlias("SysRole", com.erp.system.api.domain.SysRole.class);
        try(InputStream stream=getClass().getClassLoader().getResourceAsStream("mapper/system/SysUserMapper.xml"))
        {
            new XMLMapperBuilder(stream,configuration,"mapper/system/SysUserMapper.xml",configuration.getSqlFragments()).parse();
        }
        catch(Exception ex){throw new AssertionError(ex);}
        MappedStatement statement=configuration.getMappedStatement(
                "com.erp.system.mapper.SysUserMapper.selectHrEmployeeList");
        HrEmployeeQuery query=new HrEmployeeQuery();
        query.setUserId(42L); query.setKeyword("employee"); query.setAccountConfigurationStatus("MISSING");
        query.setMaxRows(10001);
        query.getParams().put("dataScope"," AND (d.dept_id = 20)");

        BoundSql bound=statement.getBoundSql(query);

        assertThat(bound.getSql()).contains("u.user_id = ?", "u.nick_name like concat('%', ?, '%')",
                "u.user_name like concat('%', ?, '%')",
                "p.position_no like concat('%', ?, '%')",
                "u.user_id <> 1",
                "d.dept_id = 20", "hoc.account_configuration_status = 'MISSING'",
                "hoc.account_configuration_status is null", "coalesce(urc.role_count, 0) = 0",
                "limit ?")
                .doesNotContain("${params.dataScope}");
        assertThat(bound.getParameterMappings().stream().map(ParameterMapping::getProperty))
                .containsExactly("userId", "keyword", "keyword", "keyword", "keyword", "keyword", "keyword", "keyword", "maxRows");
    }

    @Test
    void reminderQueueFiltersAreAppliedInsideTheEmployeeSql()
    {
        Configuration configuration=userConfiguration();
        MappedStatement statement=configuration.getMappedStatement(
                "com.erp.system.mapper.SysUserMapper.selectHrEmployeeList");
        HrEmployeeQuery query=new HrEmployeeQuery();
        query.setContractDue(true);
        query.setContractDueFrom(java.time.LocalDate.of(2026,7,13));
        query.setContractDueTo(java.time.LocalDate.of(2026,8,12));
        query.setOffboardAccountOnly(true);
        query.getParams().put("dataScope","");

        BoundSql sql=statement.getBoundSql(query);

        assertThat(sql.getSql()).contains("p.contract_end_date >= ?","p.contract_end_date <= ?",
                "p.employee_status in ('待离职','离职')","u.status = '0'");
        assertThat(sql.getParameterMappings()).extracting(ParameterMapping::getProperty)
                .contains("contractDueFrom","contractDueTo");
    }

    @Test
    void canonicalFormalStatusAlsoMatchesHistoricalActiveRows()
    {
        Configuration configuration=userConfiguration();
        HrEmployeeQuery hrQuery=new HrEmployeeQuery();
        hrQuery.setEmployeeStatus("正式");
        hrQuery.getParams().put("dataScope", "");
        String hrSql=configuration.getMappedStatement(
                "com.erp.system.mapper.SysUserMapper.selectHrEmployeeList").getBoundSql(hrQuery).getSql();

        com.erp.system.api.domain.SysUser userQuery=new com.erp.system.api.domain.SysUser();
        userQuery.setEmployeeStatus("正式");
        userQuery.getParams().put("dataScope", "");
        String userSql=configuration.getMappedStatement(
                "com.erp.system.mapper.SysUserMapper.selectUserList").getBoundSql(userQuery).getSql();

        assertThat(hrSql).contains("p.employee_status in ('正式','在职')");
        assertThat(userSql).contains("p.employee_status in ('正式','在职')");
    }

    @Test
    void persistedAccountConfigurationStatusIsAuthoritativeBeforeLegacyRoleAndScopeCounts()
    {
        Configuration configuration=userConfiguration();
        MappedStatement statement=configuration.getMappedStatement(
                "com.erp.system.mapper.SysUserMapper.selectHrEmployeeList");
        HrEmployeeQuery missing=new HrEmployeeQuery();missing.setAccountConfigurationStatus("MISSING");
        missing.getParams().put("dataScope","");
        String missingSql=statement.getBoundSql(missing).getSql();
        HrEmployeeQuery complete=new HrEmployeeQuery();complete.setAccountConfigurationStatus("COMPLETE");
        complete.getParams().put("dataScope","");
        String completeSql=statement.getBoundSql(complete).getSql();

        assertThat(missingSql).contains("hoc.account_configuration_status = 'MISSING'",
                "hoc.account_configuration_status is null", "coalesce(urc.role_count, 0) = 0")
                .doesNotContain("hoc.account_configuration_status = 'CONFIGURED' and coalesce(urc.role_count");
        assertThat(completeSql).contains("hoc.account_configuration_status = 'CONFIGURED'",
                "hoc.account_configuration_status is null", "coalesce(urc.role_count, 0) > 0",
                "coalesce(usc.shop_scope_count, 0) > 0");
        assertThat(missingSql).contains("when hoc.account_configuration_status = 'CONFIGURED' then 'complete'",
                "when hoc.account_configuration_status = 'MISSING' then 'missingConfiguration'");
    }

    @Test
    void onboardingRiskLinkFilterBindsLinkedUserWithoutWeakeningDataScope()
    {
        Configuration configuration=new Configuration();
        try(InputStream stream=getClass().getClassLoader().getResourceAsStream("mapper/system/HrOnboardingMapper.xml"))
        {
            new XMLMapperBuilder(stream,configuration,"mapper/system/HrOnboardingMapper.xml",configuration.getSqlFragments()).parse();
        }
        catch(Exception ex){throw new AssertionError(ex);}
        MappedStatement statement=configuration.getMappedStatement(
                "com.erp.system.mapper.HrOnboardingMapper.selectOnboardingList");
        com.erp.system.domain.vo.HrOnboardingQuery query=new com.erp.system.domain.vo.HrOnboardingQuery();
        query.setLinkedUserId(42L);
        query.getParams().put("dataScope"," AND (d.dept_id = 20)");

        BoundSql bound=statement.getBoundSql(query);

        assertThat(bound.getSql()).contains("o.linked_user_id = ?", "d.dept_id = 20")
                .doesNotContain("${params.dataScope}");
        assertThat(bound.getParameterMappings()).extracting(ParameterMapping::getProperty)
                .containsExactly("linkedUserId");
    }

    @Test
    void employeePatchLockIsScopedAndUsesForUpdate()
    {
        Configuration configuration=userConfiguration();
        HrEmployeeQuery query=new HrEmployeeQuery(); query.setUserId(42L);
        query.getParams().put("dataScope"," AND (d.dept_id = 20)");

        BoundSql bound=configuration.getMappedStatement(
                "com.erp.system.mapper.SysUserMapper.selectHrEmployeeForUpdate").getBoundSql(query);

        assertThat(bound.getSql().toLowerCase()).contains("u.user_id = ?","u.user_id <> 1",
                "d.dept_id = 20","for update")
                .doesNotContain("${params.datascope}");
        assertThat(bound.getParameterMappings()).extracting(ParameterMapping::getProperty)
                .containsExactly("userId");
    }

    @Test
    void activeGovernancePopulationExcludesDepartedProfilesButKeepsMissingProfiles()
    {
        Configuration configuration=userConfiguration();
        HrEmployeeQuery query=new HrEmployeeQuery();
        query.getParams().put("dataScope","");
        query.getParams().put("_hrActiveGovernanceOnly",true);

        String listSql=configuration.getMappedStatement(
                "com.erp.system.mapper.SysUserMapper.selectHrEmployeeList").getBoundSql(query).getSql();
        query.setUserId(42L);
        String lockSql=configuration.getMappedStatement(
                "com.erp.system.mapper.SysUserMapper.selectHrEmployeeForUpdate").getBoundSql(query).getSql();

        assertThat(listSql).contains("p.employee_status is null", "p.employee_status <> '离职'");
        assertThat(lockSql).contains("p.employee_status is null", "p.employee_status <> '离职'");
    }

    @Test
    void healthCertificateFiltersUseOnlyTheApprovedCurrentCertificate()
    {
        Configuration configuration=userConfiguration();
        HrEmployeeQuery query=new HrEmployeeQuery();
        query.setHealthCertificateStatus("EXPIRING");
        query.setHealthCertificateExpiresFrom(java.time.LocalDate.of(2026,7,13));
        query.setHealthCertificateExpiresTo(java.time.LocalDate.of(2026,8,12));
        query.getParams().put("dataScope","");

        BoundSql bound=configuration.getMappedStatement(
                "com.erp.system.mapper.SysUserMapper.selectHrEmployeeList").getBoundSql(query);

        assertThat(bound.getSql()).contains(
                "left join hr_employee_health_certificate hc on hc.user_id = u.user_id",
                "hc.current_flag = 'Y'", "hc.review_status = 'APPROVED'",
                "hc.del_flag = '0'",
                "hc.expires_on between current_date() and date_add(current_date(), interval 30 day)",
                "hc.expires_on >= ?", "hc.expires_on <= ?");
        assertThat(bound.getParameterMappings()).extracting(ParameterMapping::getProperty)
                .containsExactly("healthCertificateExpiresFrom","healthCertificateExpiresTo");
    }

    @Test
    void explicitPatchSqlContainsOnlyPresentWhitelistedColumns()
    {
        Configuration users=userConfiguration();
        Map<String,Object> params=Map.of("userId",42L,"operator","hr",
                "values",Map.of("email","new@example.com","remark","new"));
        String userSql=users.getMappedStatement("com.erp.system.mapper.SysUserMapper.patchHrEmployeeUser")
                .getBoundSql(params).getSql().toLowerCase();
        assertThat(userSql).contains("email = ?","remark = ?","where user_id = ?")
                .doesNotContain("phonenumber =","nick_name =","password =","status =");

        Configuration profiles=new Configuration();
        try(InputStream stream=getClass().getClassLoader().getResourceAsStream("mapper/system/SysUserProfileMapper.xml"))
        {new XMLMapperBuilder(stream,profiles,"mapper/system/SysUserProfileMapper.xml",profiles.getSqlFragments()).parse();}
        catch(Exception ex){throw new AssertionError(ex);}
        String profileSql=profiles.getMappedStatement("com.erp.system.mapper.SysUserProfileMapper.patchUserProfile")
                .getBoundSql(Map.of("userId",42L,"operator","hr","values",Map.of("jobGrade","P6")))
                .getSql().toLowerCase();
        assertThat(profileSql).contains("job_grade = ?","where user_id = ?")
                .doesNotContain("id_number =","company_name =","work_years =","contract_term =");
    }

    @Test
    void minimalProfileInitializationUsesAnIdempotentUserKeyUpsert()
    {
        Configuration profiles=new Configuration();
        try(InputStream stream=getClass().getClassLoader().getResourceAsStream("mapper/system/SysUserProfileMapper.xml"))
        {new XMLMapperBuilder(stream,profiles,"mapper/system/SysUserProfileMapper.xml",profiles.getSqlFragments()).parse();}
        catch(Exception ex){throw new AssertionError(ex);}

        BoundSql bound=profiles.getMappedStatement(
                "com.erp.system.mapper.SysUserProfileMapper.insertUserProfileIfAbsent")
                .getBoundSql(Map.of("userId",42L,"operator","hr"));

        assertThat(bound.getSql().toLowerCase()).contains(
                "insert into sys_user_profile", "user_id", "create_by", "update_by",
                "on duplicate key update", "user_id = values(user_id)")
                .doesNotContain("employee_no", "employee_status", "id_number", "bank_account");
    }

    private Configuration userConfiguration()
    {
        Configuration configuration=new Configuration();
        configuration.getTypeAliasRegistry().registerAlias("SysUser", com.erp.system.api.domain.SysUser.class);
        configuration.getTypeAliasRegistry().registerAlias("SysDept", com.erp.system.api.domain.SysDept.class);
        configuration.getTypeAliasRegistry().registerAlias("SysRole", com.erp.system.api.domain.SysRole.class);
        try(InputStream stream=getClass().getClassLoader().getResourceAsStream("mapper/system/SysUserMapper.xml"))
        {new XMLMapperBuilder(stream,configuration,"mapper/system/SysUserMapper.xml",configuration.getSqlFragments()).parse();}
        catch(Exception ex){throw new AssertionError(ex);}
        return configuration;
    }
}
