package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import com.erp.system.api.domain.SysDept;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.domain.SysUserProfile;
import com.erp.system.domain.HrOnboardingPositionConfig;
import com.erp.system.domain.SysPost;
import com.erp.system.domain.SysUserPost;
import com.erp.system.domain.vo.HrMasterDataIssueVo;
import com.erp.system.mapper.HrNativeMySqlTestSupport;

/** Compares the pure Java evaluator with the read-only DBA report. */
@EnabledIfEnvironmentVariable(named = "ERP_HR_NATIVE_TEST_URL",
        matches = "jdbc:mysql:.*")
class HrMasterDataReadinessNativeMySqlTest
{
    @Test
    void javaAndDbaSqlProduceTheSameNormalizedFindings() throws Exception
    {
        String url = System.getenv("ERP_HR_NATIVE_TEST_URL");
        String username = System.getenv().getOrDefault(
                "ERP_HR_NATIVE_TEST_USERNAME", "root");
        String password = System.getenv().getOrDefault(
                "ERP_HR_NATIVE_TEST_PASSWORD", "");
        try (Connection connection = DriverManager.getConnection(url, username,
                password))
        {
            HrNativeMySqlTestSupport.requireIsolatedDatabase(connection);
            HrMasterDataReadinessEvaluator evaluator =
                    new HrMasterDataReadinessEvaluator();
            HrMasterDataScanResult javaResult = evaluator.evaluate(
                    snapshot(connection));
            Map<FindingKey, FindingValue> javaFindings = normalizeJava(
                    javaResult.issues());
            Map<FindingKey, FindingValue> sqlFindings = executeDbaReport(
                    connection);

            Set<FindingKey> missingFromSql = new LinkedHashSet<>(
                    javaFindings.keySet());
            missingFromSql.removeAll(sqlFindings.keySet());
            Set<FindingKey> extraInSql = new LinkedHashSet<>(
                    sqlFindings.keySet());
            extraInSql.removeAll(javaFindings.keySet());
            assertThat(missingFromSql).as("DBA SQL 缺少的 Java 问题键")
                    .isEmpty();
            assertThat(extraInSql).as("DBA SQL 多出的非 Java 问题键")
                    .isEmpty();
            assertThat(sqlFindings).containsExactlyInAnyOrderEntriesOf(
                    javaFindings);
            assertThat(javaFindings.keySet()).noneMatch(key ->
                    "EMPLOYEE".equals(key.resourceType())
                            && "1".equals(key.resourceId()));
        }
    }

    private HrMasterDataReadinessEvaluator.Snapshot snapshot(
            Connection connection) throws Exception
    {
        List<SysDept> allDepartments = departments(connection, false);
        List<SysDept> scopedDepartments = departments(connection, true);
        List<SysUser> employees = employees(connection);
        List<SysPost> posts = posts(connection);
        List<SysUserPost> userPosts = userPosts(connection);
        List<HrOnboardingPositionConfig> configs = configs(connection);
        Map<Long, List<Long>> roleIdsByConfig = configRoleIds(connection);
        Set<Long> activeRoleIds = activeRoleIds(connection);
        Map<String, HrMasterDataReadinessEvaluator.DictionaryRouteSnapshot>
                routes = dictionaryRoutes(connection);
        return new HrMasterDataReadinessEvaluator.Snapshot(scopedDepartments,
                allDepartments, employees, posts, userPosts, configs,
                roleIdsByConfig, activeRoleIds, routes);
    }

    private List<SysDept> departments(Connection connection, boolean activeOnly)
            throws Exception
    {
        String sql = "select dept_id,parent_id,ancestors,dept_name,dept_type,"
                + "leader,leader_user_id,status,del_flag from sys_dept "
                + "where del_flag='0'"
                + (activeOnly ? " and status='0'" : "")
                + " order by parent_id,order_num,dept_id";
        List<SysDept> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet rows = statement.executeQuery())
        {
            while (rows.next())
            {
                SysDept value = new SysDept();
                value.setDeptId(rows.getLong("dept_id"));
                value.setParentId(nullableLong(rows, "parent_id"));
                value.setAncestors(rows.getString("ancestors"));
                value.setDeptName(rows.getString("dept_name"));
                value.setDeptType(rows.getString("dept_type"));
                value.setLeader(rows.getString("leader"));
                value.setLeaderUserId(nullableLong(rows, "leader_user_id"));
                value.setStatus(rows.getString("status"));
                value.setDelFlag(rows.getString("del_flag"));
                result.add(value);
            }
        }
        return result;
    }

    private List<SysUser> employees(Connection connection) throws Exception
    {
        List<SysUser> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "select u.user_id,u.dept_id,u.nick_name,u.status,u.del_flag,"
                        + "p.profile_id,p.employee_status,p.employee_category "
                        + "from sys_user u left join sys_user_profile p "
                        + "on p.user_id=u.user_id where u.del_flag='0' "
                        + "and u.user_id<>1 and (p.employee_status is null "
                        + "or p.employee_status<>'离职') order by u.user_id");
                ResultSet rows = statement.executeQuery())
        {
            while (rows.next())
            {
                SysUser value = new SysUser();
                value.setUserId(rows.getLong("user_id"));
                value.setDeptId(nullableLong(rows, "dept_id"));
                value.setNickName(rows.getString("nick_name"));
                value.setStatus(rows.getString("status"));
                value.setDelFlag(rows.getString("del_flag"));
                Long profileId = nullableLong(rows, "profile_id");
                if (profileId != null)
                {
                    SysUserProfile profile = new SysUserProfile();
                    profile.setProfileId(profileId);
                    profile.setUserId(value.getUserId());
                    profile.setEmployeeStatus(
                            rows.getString("employee_status"));
                    profile.setEmployeeCategory(
                            rows.getString("employee_category"));
                    value.setProfile(profile);
                }
                result.add(value);
            }
        }
        return result;
    }

    private List<SysPost> posts(Connection connection) throws Exception
    {
        List<SysPost> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "select post_id,post_name,post_sort,status from sys_post "
                        + "order by post_sort,post_id");
                ResultSet rows = statement.executeQuery())
        {
            while (rows.next())
            {
                SysPost value = new SysPost();
                value.setPostId(rows.getLong("post_id"));
                value.setPostName(rows.getString("post_name"));
                value.setPostSort(rows.getInt("post_sort"));
                value.setStatus(rows.getString("status"));
                result.add(value);
            }
        }
        return result;
    }

    private List<SysUserPost> userPosts(Connection connection)
            throws Exception
    {
        List<SysUserPost> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "select up.user_id,up.post_id from sys_user_post up "
                        + "join sys_user u on u.user_id=up.user_id "
                        + "left join sys_user_profile p on p.user_id=u.user_id "
                        + "where u.del_flag='0' and u.user_id<>1 "
                        + "and (p.employee_status is null "
                        + "or p.employee_status<>'离职') "
                        + "order by up.user_id,up.post_id");
                ResultSet rows = statement.executeQuery())
        {
            while (rows.next())
            {
                SysUserPost value = new SysUserPost();
                value.setUserId(rows.getLong("user_id"));
                value.setPostId(rows.getLong("post_id"));
                result.add(value);
            }
        }
        return result;
    }

    private List<HrOnboardingPositionConfig> configs(Connection connection)
            throws Exception
    {
        List<HrOnboardingPositionConfig> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "select config_id,post_id,employee_category,account_enabled,"
                        + "status from hr_onboarding_position_config "
                        + "order by status,post_id,employee_category,config_id");
                ResultSet rows = statement.executeQuery())
        {
            while (rows.next())
            {
                HrOnboardingPositionConfig value =
                        new HrOnboardingPositionConfig();
                value.setConfigId(rows.getLong("config_id"));
                value.setPostId(rows.getLong("post_id"));
                value.setEmployeeCategory(
                        rows.getString("employee_category"));
                value.setAccountEnabled(
                        "1".equals(rows.getString("account_enabled")));
                value.setStatus(rows.getString("status"));
                result.add(value);
            }
        }
        return result;
    }

    private Map<Long, List<Long>> configRoleIds(Connection connection)
            throws Exception
    {
        Map<Long, List<Long>> result = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "select c.config_id,cr.role_id "
                        + "from hr_onboarding_position_config c "
                        + "left join hr_onboarding_position_config_role cr "
                        + "on cr.config_id=c.config_id "
                        + "where c.status='0' and c.account_enabled='1' "
                        + "order by c.config_id,cr.role_id");
                ResultSet rows = statement.executeQuery())
        {
            while (rows.next())
            {
                Long configId = rows.getLong("config_id");
                result.computeIfAbsent(configId,
                        ignored -> new ArrayList<>());
                Long roleId = nullableLong(rows, "role_id");
                if (roleId != null)
                {
                    result.get(configId).add(roleId);
                }
            }
        }
        return result;
    }

    private Set<Long> activeRoleIds(Connection connection) throws Exception
    {
        Set<Long> result = new LinkedHashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "select role_id from sys_role "
                        + "where status='0' and del_flag='0'");
                ResultSet rows = statement.executeQuery())
        {
            while (rows.next())
            {
                result.add(rows.getLong(1));
            }
        }
        return result;
    }

    private Map<String, HrMasterDataReadinessEvaluator.DictionaryRouteSnapshot>
            dictionaryRoutes(Connection connection) throws Exception
    {
        Map<String, HrMasterDataReadinessEvaluator.DictionaryRouteSnapshot>
                result = new LinkedHashMap<>();
        for (String fieldKey :
                HrOnboardingPositionConfigServiceImpl.DICTIONARY_FIELDS)
        {
            String configKey = HrOnboardingPositionConfigServiceImpl
                    .DICT_ROUTE_PREFIX + fieldKey;
            String dictType = scalar(connection,
                    "select config_value from sys_config where config_key=? "
                            + "order by config_id limit 1",
                    configKey);
            String status = dictType == null ? null : scalar(connection,
                    "select status from sys_dict_type where dict_type=? "
                            + "order by dict_id limit 1",
                    dictType.trim());
            boolean typeActive = "0".equals(status);
            List<String> values = typeActive
                    ? strings(connection,
                            "select dict_value from sys_dict_data "
                                    + "where dict_type=? and status='0' "
                                    + "order by dict_sort,dict_code",
                            dictType.trim())
                    : Collections.emptyList();
            result.put(fieldKey,
                    new HrMasterDataReadinessEvaluator.DictionaryRouteSnapshot(
                            fieldKey, configKey,
                            dictType == null ? null : dictType.trim(),
                            typeActive, values));
        }
        return result;
    }

    private Map<FindingKey, FindingValue> normalizeJava(
            List<HrMasterDataIssueVo> issues)
    {
        Map<FindingKey, FindingValue> result = new LinkedHashMap<>();
        for (HrMasterDataIssueVo issue : issues)
        {
            FindingKey key = new FindingKey(issue.getIssueCode(),
                    issue.getResourceType(), issue.getResourceId());
            assertThat(result.put(key, new FindingValue(issue.getSeverity(),
                    issue.getAffectedEmployeeCount()))).as("重复 Java 问题键 " + key)
                    .isNull();
        }
        return result;
    }

    private Map<FindingKey, FindingValue> executeDbaReport(
            Connection connection) throws Exception
    {
        String source = Files.readString(repoFile(
                "scripts/hr-master-data-readiness.sql"),
                StandardCharsets.UTF_8)
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)^\\s*--.*$", "");
        Map<FindingKey, FindingValue> result = new LinkedHashMap<>();
        int resultIndex = 0;
        for (String fragment : source.split(";"))
        {
            String sql = fragment.trim();
            if (sql.isEmpty())
            {
                continue;
            }
            try (Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery(sql))
            {
                if (resultIndex++ == 0)
                {
                    continue;
                }
                Set<String> columns = columns(rows.getMetaData());
                while (rows.next())
                {
                    String code = rows.getString("issue_code");
                    if (code == null || "READY".equals(code))
                    {
                        continue;
                    }
                    if (columns.contains("readiness_status")
                            && "ready".equalsIgnoreCase(
                                    rows.getString("readiness_status")))
                    {
                        continue;
                    }
                    String resourceType;
                    String resourceId;
                    if (columns.contains("resource_type"))
                    {
                        resourceType = rows.getString("resource_type");
                        resourceId = rows.getString("resource_id");
                    }
                    else if (POSITION_CONFIG_ROLE_MISSING.equals(code))
                    {
                        resourceType = "POSITION_CONFIG";
                        resourceId = rows.getString("config_id");
                    }
                    else
                    {
                        resourceType = "POSITION_CONFIG_PAIR";
                        resourceId = rows.getString("post_id") + ":"
                                + rows.getString("employee_category");
                    }
                    FindingKey key = new FindingKey(code, resourceType,
                            resourceId);
                    FindingValue value = new FindingValue(
                            rows.getString("severity"),
                            rows.getLong("affected_employee_count"));
                    FindingValue previous = result.putIfAbsent(key, value);
                    assertThat(previous).as("重复 DBA SQL 问题键 " + key)
                            .satisfiesAnyOf(existing -> assertThat(existing)
                                    .isNull(), existing -> assertThat(existing)
                                            .isEqualTo(value));
                }
            }
        }
        assertThat(resultIndex).as("readiness SQL 结果集数量").isEqualTo(5);
        return result;
    }

    private String scalar(Connection connection, String sql, String parameter)
            throws Exception
    {
        List<String> values = strings(connection, sql, parameter);
        return values.isEmpty() ? null : values.get(0);
    }

    private List<String> strings(Connection connection, String sql,
            String parameter) throws Exception
    {
        List<String> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql))
        {
            statement.setString(1, parameter);
            try (ResultSet rows = statement.executeQuery())
            {
                while (rows.next())
                {
                    result.add(rows.getString(1));
                }
            }
        }
        return result;
    }

    private Set<String> columns(ResultSetMetaData metadata) throws Exception
    {
        Set<String> result = new LinkedHashSet<>();
        for (int index = 1; index <= metadata.getColumnCount(); index++)
        {
            result.add(metadata.getColumnLabel(index).toLowerCase(Locale.ROOT));
        }
        return result;
    }

    private Long nullableLong(ResultSet rows, String column) throws Exception
    {
        long value = rows.getLong(column);
        return rows.wasNull() ? null : value;
    }

    private Path repoFile(String relative)
    {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath()
                .normalize();
        while (current != null && !Files.exists(current.resolve("erp-modules")))
        {
            current = current.getParent();
        }
        if (current == null)
        {
            throw new IllegalStateException("repository root not found");
        }
        return current.resolve(relative);
    }

    private static final String POSITION_CONFIG_ROLE_MISSING =
            "POSITION_CONFIG_ROLE_MISSING";

    private record FindingKey(String issueCode, String resourceType,
            String resourceId)
    {
    }

    private record FindingValue(String severity, long affectedEmployeeCount)
    {
    }
}
