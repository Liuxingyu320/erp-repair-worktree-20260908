package com.erp.approval.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import com.erp.approval.candidate.ApprovalDirectoryDept;
import com.erp.approval.candidate.ApprovalDirectoryUser;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class ApprovalReassignOptionsMapperTest
{
    private static final String NS = "com.erp.approval.mapper.ApprovalCandidateDirectoryMapper";

    @Test
    void bindsEligibilitySearchPagingAndTaskExclusionWithoutLoadingTheWholeDirectory() throws Exception
    {
        Configuration config = configuration();
        Map<String, Object> params = new HashMap<>(Map.of("taskId", 31L, "permission", "oa:todo:approve",
                "keyword", "张三采购部", "limit", 21, "offset", 20));
        BoundSql statement = config.getMappedStatement(NS + ".selectReassignOptions").getBoundSql(params);
        String sql = statement.getSql().replaceAll("\\s+", " ").trim();
        assertThat(sql).contains("u.del_flag = '0' and u.status = '0'", "trim(profile.employee_status) <> '离职'",
                "permission_role.del_flag = '0'", "permission_role.status = '0'", "permission_menu.status = '0'",
                "permission_menu.perms = ?", "existing_candidate.task_id = ?", "existing_candidate.user_id = u.user_id",
                "u.nick_name like concat('%', ?, '%')", "u.user_name like concat('%', ?, '%')",
                "d.dept_name like concat('%', ?, '%')", "u.user_name account_name", "limit ? offset ?");
        assertThat(sql).doesNotContain("张三采购部", "oa:todo:approve");
        assertThat(statement.getParameterMappings()).extracting("property")
                .contains("taskId", "permission", "keyword", "limit", "offset");
        assertThat(config.getResultMap(NS + ".DirectoryUserResult").getResultMappings())
                .extracting("property").contains("userId", "userName", "accountName", "deptName");
    }

    @Test
    void missingPermissionCannotTurnSearchIntoUnfilteredActiveUsers() throws Exception
    {
        Map<String, Object> params = new HashMap<>();
        params.put("permission", null); params.put("keyword", ""); params.put("taskId", 31L);
        params.put("limit", 21); params.put("offset", 0);
        String sql = configuration().getMappedStatement(NS + ".selectReassignOptions").getBoundSql(params).getSql();
        assertThat(sql.replaceAll("\\s+", " ")).contains("and ? is not null and ? <> ''", "not exists");
    }

    private Configuration configuration() throws Exception
    {
        Configuration config = new Configuration();
        config.getTypeAliasRegistry().registerAlias("ApprovalDirectoryUser", ApprovalDirectoryUser.class);
        config.getTypeAliasRegistry().registerAlias("ApprovalDirectoryDept", ApprovalDirectoryDept.class);
        String xml = "mapper/approval/ApprovalCandidateDirectoryMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(xml)) {
            new XMLMapperBuilder(input, config, xml, config.getSqlFragments()).parse();
        }
        return config;
    }
}
