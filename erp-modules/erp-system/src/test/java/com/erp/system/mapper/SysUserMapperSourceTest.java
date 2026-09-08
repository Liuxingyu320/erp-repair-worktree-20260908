package com.erp.system.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.ibatis.io.Resources;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("系统用户签约候选查询")
class SysUserMapperSourceTest
{
    @Test
    @DisplayName("签约候选查询使用独立SQL并限制结果数")
    void shouldUseDedicatedSqlForSignCandidates() throws Exception
    {
        String xml = new String(Resources.getResourceAsStream("mapper/system/SysUserMapper.xml").readAllBytes(),
                StandardCharsets.UTF_8);

        assertThat(xml)
                .contains("id=\"selectSignCandidateUsers\"")
                .contains("u.status = '0'")
                .contains("sp.post_name = #{postName}")
                .contains("u.nick_name like concat('%', #{keyword}, '%')")
                .contains("<foreach collection=\"userIds\"")
                .contains("<foreach collection=\"phoneNumbers\"")
                .contains("AND u.phonenumber in")
                .contains("order by u.user_id")
                .contains("limit #{limit}");

        String selectUserListSql = xml.substring(xml.indexOf("id=\"selectUserList\""),
                xml.indexOf("id=\"selectUserManageList\""));
        assertThat(selectUserListSql)
                .doesNotContain("params.postName")
                .doesNotContain("p.id_number", "p.bank_account", "p.current_address",
                        "p.emergency_contact", "p.birth_date", "p.registered_residence")
                .contains("p.employee_no", "p.job_grade", "p.employee_status",
                        "p.employee_category", "p.contract_type", "p.social_type");
    }

    @Test
    @DisplayName("后台用户管理列表返回完整登录账号和手机号")
    void shouldReturnFullOperationalIdentityForUserManagement() throws Exception
    {
        String xml = new String(Resources.getResourceAsStream("mapper/system/SysUserMapper.xml").readAllBytes(),
                StandardCharsets.UTF_8);
        String manageColumns = xml.substring(xml.indexOf("id=\"selectUserManageColumns\""),
                xml.indexOf("id=\"userManageJoins\""));

        assertThat(manageColumns)
                .contains("u.user_name")
                .contains("u.phonenumber")
                .doesNotContain("****", "maskedUserName", "maskedPhoneNumber");
    }

    @Test
    @DisplayName("Excel签约手机号精确查询具有幂等索引迁移")
    void shouldShipExactPhoneLookupIndex() throws Exception
    {
        String sql = readRepoFile("sql/erp_system_sign_candidate_phone_index_20260719.sql");
        String docker = readRepoFile(
                "docker/mysql/db/erp_system_sign_candidate_phone_index_20260719.sql");

        assertThat(sql)
                .contains("INDEX_NAME = 'idx_sys_user_sign_phone'")
                .contains("ADD INDEX idx_sys_user_sign_phone")
                .contains("(phonenumber, del_flag, status, user_id)");
        assertThat(docker).isEqualTo(sql);
    }

    @Test
    @DisplayName("用户档案映射包含自动签约判断字段")
    void shouldMapAutoSignProfileFields() throws Exception
    {
        String userXml = new String(Resources.getResourceAsStream("mapper/system/SysUserMapper.xml").readAllBytes(),
                StandardCharsets.UTF_8);
        String profileXml = new String(Resources.getResourceAsStream("mapper/system/SysUserProfileMapper.xml").readAllBytes(),
                StandardCharsets.UTF_8);

        assertThat(userXml)
                .contains("property=\"contractType\"")
                .contains("property=\"socialType\"")
                .contains("p.contract_type")
                .contains("p.social_type");

        assertThat(profileXml)
                .contains("property=\"contractType\"")
                .contains("property=\"socialType\"")
                .contains("contract_type")
                .contains("social_type")
                .contains("#{contractType}")
                .contains("#{socialType}");
    }

    @Test
    @DisplayName("员工档案热修脚本补齐登录查询依赖字段")
    void shouldShipProfileColumnHotfixMigration() throws Exception
    {
        String sql = readRepoFile("sql/erp_user_profile_login_columns_20260709.sql");
        String dockerSql = readRepoFile("docker/mysql/db/erp_user_profile_login_columns_20260709.sql");

        assertThat(sql)
                .contains("TABLE_NAME = 'sys_user_profile'")
                .contains("COLUMN_NAME = 'contract_type'")
                .contains("ADD COLUMN contract_type")
                .contains("COLUMN_NAME = 'social_type'")
                .contains("ADD COLUMN social_type");
        assertThat(dockerSql).isEqualTo(sql);
    }

    @Test
    @DisplayName("签约候选查询返回自动预览所需档案字段")
    void shouldSelectProfileFieldsForSignCandidates() throws Exception
    {
        String xml = new String(Resources.getResourceAsStream("mapper/system/SysUserMapper.xml").readAllBytes(),
                StandardCharsets.UTF_8);
        String signCandidateSql = xml.substring(xml.indexOf("id=\"selectSignCandidateUsers\""),
                xml.indexOf("id=\"selectAllocatedList\""));

        assertThat(xml)
                .contains("property=\"accountStatus\"")
                .contains("property=\"employeeNo\"")
                .contains("property=\"employeeStatus\"")
                .contains("property=\"idType\"")
                .contains("property=\"idNumber\"")
                .contains("property=\"currentAddress\"")
                .contains("property=\"postId\"")
                .contains("property=\"postCode\"")
                .contains("property=\"postName\"")
                .contains("property=\"jobGrade\"")
                .contains("property=\"contractType\"")
                .contains("property=\"contractTerm\"")
                .contains("property=\"socialType\"")
                .contains("property=\"legalEntity\"")
                .contains("property=\"legalEntityId\"")
                .contains("property=\"legalEntityCode\"")
                .contains("property=\"entryDate\"")
                .contains("property=\"probationStartDate\"")
                .contains("property=\"probationEndDate\"")
                .contains("property=\"contractStartDate\"")
                .contains("property=\"contractEndDate\"")
                .contains("property=\"baseSalary\"")
                .contains("property=\"postSalary\"")
                .contains("property=\"fieldAllowance\"")
                .contains("property=\"performanceSalary\"")
                .contains("property=\"salaryTotal\"")
                .contains("property=\"salaryVersion\"");

        assertThat(signCandidateSql)
                .contains("left join sys_user_profile p on p.user_id = u.user_id")
                .contains("left join sys_post profile_sp")
                .contains("substring_index(p.position_no, '-', 1)")
                .contains("lower(profile_sp.post_code) collate utf8mb4_general_ci")
                .contains("lower(substring_index(p.position_no, '-', 1)) collate utf8mb4_general_ci")
                .contains("includeInactiveEmployees == null or includeInactiveEmployees == false")
                .contains("p.employee_status &lt;&gt; '离职'")
                .contains("u.status as account_status")
                .contains("p.employee_no")
                .contains("p.employee_status")
                .contains("p.id_type")
                .contains("p.id_number")
                .contains("p.current_address")
                .contains("main_sp.post_id")
                .contains("main_sp.post_code")
                .contains("main_sp.post_name")
                .contains("order by main_sp2.post_sort asc, main_sp2.post_id asc")
                .contains("p.job_grade")
                .contains("p.contract_type")
                .contains("p.contract_term")
                .contains("p.social_type")
                .contains("p.legal_entity")
                .contains("p.legal_entity_id")
                .contains("p.legal_entity_code")
                .contains("date_format(p.entry_date, '%Y-%m-%d') as entry_date")
                .contains("date_format(p.probation_start_date, '%Y-%m-%d') as probation_start_date")
                .contains("date_format(p.probation_end_date, '%Y-%m-%d') as probation_end_date")
                .contains("date_format(p.contract_start_date, '%Y-%m-%d') as contract_start_date")
                .contains("date_format(p.contract_end_date, '%Y-%m-%d') as contract_end_date")
                .contains("p.base_salary")
                .contains("p.post_salary")
                .contains("p.field_allowance")
                .contains("p.performance_salary")
                .contains("p.salary_total")
                .contains("p.salary_version");
    }

    @Test
    @DisplayName("通用用户查询不读取或映射签约薪资")
    void shouldNotReadSigningSalaryFromGeneralUserMapper() throws Exception
    {
        String xml = new String(Resources.getResourceAsStream("mapper/system/SysUserMapper.xml").readAllBytes(),
                StandardCharsets.UTF_8);

        String generalUserListSql = xml.substring(xml.indexOf("id=\"selectUserList\""),
                xml.indexOf("id=\"selectAllocatedList\""));
        String signCandidateSql = xml.substring(xml.indexOf("id=\"selectSignCandidateUsers\""),
                xml.indexOf("id=\"selectAllocatedList\""));
        generalUserListSql = generalUserListSql.replace(signCandidateSql, "");

        assertThat(generalUserListSql).doesNotContain(
                "p.base_salary", "p.post_salary", "p.field_allowance",
                "p.performance_salary", "p.salary_total", "p.salary_version");
        assertThat(signCandidateSql).contains(
                "p.base_salary", "p.post_salary", "p.field_allowance",
                "p.performance_salary", "p.salary_total", "p.salary_version");
    }

    @Test
    @DisplayName("登录查询不读取员工敏感档案")
    void loginQueryShouldUseAccountOnlyProjection() throws Exception
    {
        String xml = new String(Resources.getResourceAsStream("mapper/system/SysUserMapper.xml").readAllBytes(),
                StandardCharsets.UTF_8);
        String loginProjection = xml.substring(xml.indexOf("id=\"selectLoginUserVo\""),
                xml.indexOf("id=\"selectUserList\""));
        String loginQuery = xml.substring(xml.indexOf("id=\"selectUserByUserName\""),
                xml.indexOf("id=\"selectUserById\""));

        assertThat(loginProjection)
                .contains("u.password", "u.must_change_password", "r.role_key")
                .doesNotContain("sys_user_profile", "p.id_number", "p.bank_account",
                        "p.current_address", "p.emergency_contact");
        assertThat(loginQuery)
                .contains("refid=\"selectLoginUserVo\"")
                .doesNotContain("refid=\"selectUserVo\"");
    }

    @Test
    @DisplayName("门店授权用户候选只读取必要字段并保留数据范围")
    void shopAuthorizationOptionsShouldUseMinimalProjection() throws Exception
    {
        String xml = new String(Resources.getResourceAsStream("mapper/system/SysUserMapper.xml").readAllBytes(),
                StandardCharsets.UTF_8);
        String optionSql = xml.substring(xml.indexOf("id=\"selectUserOptionList\""),
                xml.indexOf("id=\"selectHrEmployeeList\""));

        assertThat(optionSql)
                .contains("u.user_id", "u.user_name", "u.nick_name", "u.dept_id", "u.status",
                        "post_names", "${params.dataScope}", "params.includeDisabled")
                .doesNotContain("u.phonenumber", "u.email", "p.id_number", "p.bank_account",
                        "p.current_address");
    }

    @Test
    @DisplayName("角色分配用户列表不返回联系方式和敏感档案")
    void roleAuthorizationListsShouldUseMinimalProjection() throws Exception
    {
        String xml = new String(Resources.getResourceAsStream("mapper/system/SysUserMapper.xml").readAllBytes(),
                StandardCharsets.UTF_8);
        String allocatedSql = xml.substring(xml.indexOf("id=\"selectAllocatedList\""),
                xml.indexOf("id=\"selectUnallocatedList\""));
        String unallocatedSql = xml.substring(xml.indexOf("id=\"selectUnallocatedList\""),
                xml.indexOf("id=\"selectAllocatedAssignmentList\""));

        assertThat(allocatedSql).contains("u.user_id", "u.user_name", "u.nick_name", "d.dept_name",
                "${params.dataScope}");
        assertThat(unallocatedSql).contains("u.user_id", "u.user_name", "u.nick_name", "d.dept_name",
                "${params.dataScope}");
        assertThat(allocatedSql + unallocatedSql).doesNotContain(
                "u.email", "u.phonenumber", "p.id_number", "p.bank_account", "p.current_address");
    }

    @Test
    @DisplayName("在线会话候选用户使用一次批量数据范围查询")
    void onlineSessionUsersShouldUseScopedBatchIdQuery() throws Exception
    {
        String xml = new String(Resources.getResourceAsStream("mapper/system/SysUserMapper.xml").readAllBytes(),
                StandardCharsets.UTF_8);
        String scopedIdsSql = xml.substring(xml.indexOf("id=\"selectVisibleUserIds\""),
                xml.indexOf("id=\"selectHrEmployeeList\""));

        assertThat(scopedIdsSql)
                .contains("select distinct u.user_id", "params.candidateUserIds", "${params.dataScope}")
                .doesNotContain("u.phonenumber", "u.email", "sys_user_profile");
    }

    private String readRepoFile(String relativePath) throws Exception
    {
        Path base = Paths.get(System.getProperty("user.dir"));
        Path path = base.resolve(relativePath);
        if (!Files.exists(path))
        {
            path = base.resolve("../..").resolve(relativePath).normalize();
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
