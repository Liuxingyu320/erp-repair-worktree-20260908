package com.erp.approval.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.approval.domain.ApprovalActionLog;
import com.erp.approval.domain.ApprovalCallbackOutbox;
import com.erp.approval.domain.ApprovalInstance;
import com.erp.approval.domain.ApprovalTask;
import com.erp.approval.domain.ApprovalTaskCandidate;
import com.erp.approval.domain.vo.ApprovalTodoAccessRule;
import com.erp.approval.domain.vo.ApprovalTodoCountRow;
import com.erp.approval.domain.vo.ApprovalTodoRow;
import com.erp.common.core.domain.todo.TodoQuery;

@DisplayName("统一审批运行时 Mapper 绑定")
class ApprovalRuntimeMapperBindingTest
{
    private static final String XML =
            "mapper/approval/ApprovalRuntimeMapper.xml";
    private static final String NAMESPACE =
            "com.erp.approval.mapper.ApprovalRuntimeMapper";

    @Test
    @DisplayName("审批待办只按候选人与业务条件过滤")
    void shouldBindListCountsAndBoundedRecentQueries() throws Exception
    {
        Configuration configuration = configuration();
        assertThat(configuration.hasStatement(NAMESPACE + ".selectTodoRows"))
                .isTrue();
        assertThat(configuration.hasStatement(
                NAMESPACE + ".selectTodoTypeCounts")).isTrue();
        assertThat(configuration.hasStatement(
                NAMESPACE + ".selectRecentTodoRows")).isTrue();

        Map<String, Object> params = new HashMap<>();
        TodoQuery query = new TodoQuery();
        query.setScopeMode("current_org");
        params.put("query", query);
        params.put("userId", 9L);
        params.put("accessRules", List.of(new ApprovalTodoAccessRule(
                "INV_TRANSFER", true, List.of(), List.of())));
        String listSql = normalized(configuration.getMappedStatement(
                NAMESPACE + ".selectTodoRows")
                .getBoundSql(params).getSql());
        String countSql = normalized(configuration.getMappedStatement(
                NAMESPACE + ".selectTodoTypeCounts")
                .getBoundSql(params).getSql());
        String recentSql = normalized(configuration.getMappedStatement(
                NAMESPACE + ".selectRecentTodoRows")
                .getBoundSql(params).getSql());

        assertThat(listSql).contains("c.user_id = ?",
                "i.business_code = ?", "c.candidate_source_code");
        assertThat(countSql).contains("count(distinct t.task_id)",
                "c.user_id = ?", "group by case");
        assertThat(recentSql).contains("c.user_id = ?",
                "order by t.activated_time asc, sort_business_id asc, i.business_source asc, sort_todo_type asc, t.task_id asc limit 5");
        assertThat(List.of(listSql, countSql, recentSql))
                .allSatisfy(sql -> assertThat(sql)
                        .doesNotContain("i.anchor_dept_id = ?"));
    }

    @Test
    @DisplayName("审批待办搜索覆盖页面生成标题且空权限集合拒绝返回")
    void shouldSearchVisibleTitlesAndFailClosedWithoutBusinessPermissions()
            throws Exception
    {
        Configuration configuration = configuration();
        TodoQuery query = new TodoQuery();
        query.setKeyword("调拨申请");
        Map<String, Object> params = new HashMap<>();
        params.put("query", query);
        params.put("userId", 9L);
        params.put("accessRules", List.of());

        String sql = normalized(configuration.getMappedStatement(
                NAMESPACE + ".selectTodoRows").getBoundSql(params).getSql());

        assertThat(sql).contains("else '审批申请' end) like concat('%', ?, '%')",
                "and 1 = 0");
    }

    @Test
    @DisplayName("报销财务待办按候选快照权限精确过滤")
    void shouldBindExactReimbursementCandidatePermissionRules()
            throws Exception
    {
        Configuration configuration = configuration();
        TodoQuery query = new TodoQuery();
        query.setType("OA_REIMBURSEMENT_APPROVAL");
        query.setKeyword("费用报销");
        Map<String, Object> params = new HashMap<>();
        params.put("query", query);
        params.put("userId", 9L);
        params.put("accessRules", List.of(new ApprovalTodoAccessRule(
                "OA_REIMBURSEMENT", false,
                List.of("oa:reimbursement:finance:approve"),
                List.of("oa:reimbursement:finance:approve"))));

        String financeSql = normalized(configuration.getMappedStatement(
                NAMESPACE + ".selectTodoRows")
                .getBoundSql(params).getSql());

        assertThat(financeSql).contains(
                "i.business_code = ? and ( c.candidate_source_code in ( ? )",
                "when 'oa_reimbursement' then 'oa_reimbursement_approval'",
                "when 'oa_reimbursement' then '费用报销'");

        params.put("accessRules", List.of(new ApprovalTodoAccessRule(
                "OA_REIMBURSEMENT", true,
                List.of("oa:reimbursement:finance:approve"), List.of())));
        String baseSql = normalized(configuration.getMappedStatement(
                NAMESPACE + ".selectTodoRows")
                .getBoundSql(params).getSql());
        assertThat(baseSql).contains(
                "c.candidate_source_code is null or c.candidate_source_code not in ( ? )");
    }

    @Test
    @DisplayName("并发幂等重读使用当前读而非事务快照")
    void shouldLockActionResultAndReassignedCandidateForReplay()
            throws Exception
    {
        Configuration configuration = configuration();
        String actionSql = normalized(configuration.getMappedStatement(
                NAMESPACE + ".selectActionByKeyForShare")
                .getBoundSql(Map.of("actionKey", "88:APPROVE:req-1"))
                .getSql());
        String instanceSql = normalized(configuration.getMappedStatement(
                NAMESPACE + ".selectInstanceByIdForShare")
                .getBoundSql(Map.of("instanceId", 88L)).getSql());
        String candidateSql = normalized(configuration.getMappedStatement(
                NAMESPACE + ".selectReassignedCandidateForShare")
                .getBoundSql(Map.of("taskId", 31L,
                        "fromCandidateId", 45L, "toUserId", 77L))
                .getSql());

        assertThat(actionSql).contains(
                "where action_key = ? lock in share mode");
        assertThat(instanceSql).contains(
                "where instance_id = ? lock in share mode");
        assertThat(candidateSql).contains(
                "where task_id = ? and reassigned_from_candidate_id = ?",
                "and user_id = ? lock in share mode");
    }

    private static Configuration configuration() throws Exception
    {
        Configuration configuration = new Configuration();
        configuration.getTypeAliasRegistry().registerAlias(
                "ApprovalInstance", ApprovalInstance.class);
        configuration.getTypeAliasRegistry().registerAlias(
                "ApprovalTask", ApprovalTask.class);
        configuration.getTypeAliasRegistry().registerAlias(
                "ApprovalTaskCandidate", ApprovalTaskCandidate.class);
        configuration.getTypeAliasRegistry().registerAlias(
                "ApprovalActionLog", ApprovalActionLog.class);
        configuration.getTypeAliasRegistry().registerAlias(
                "ApprovalCallbackOutbox", ApprovalCallbackOutbox.class);
        configuration.getTypeAliasRegistry().registerAlias(
                "ApprovalTodoRow", ApprovalTodoRow.class);
        configuration.getTypeAliasRegistry().registerAlias(
                "ApprovalTodoCountRow", ApprovalTodoCountRow.class);
        try (InputStream input = Resources.getResourceAsStream(XML))
        {
            new XMLMapperBuilder(input, configuration, XML,
                    configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    private static String normalized(String value)
    {
        return value.replaceAll("\\s+", " ").trim().toLowerCase();
    }
}
