package com.erp.system.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import com.erp.common.core.domain.todo.TodoQuery;

class SysTodoMapperBindingTest
{
    @Test
    void sharedCandidatesUseCurrentOrganizationAndPersonalReturnedLaneStaysOwnerScoped() throws Exception
    {
        String xml = new String(Resources.getResourceAsStream("mapper/system/SysTodoMapper.xml").readAllBytes(),
                StandardCharsets.UTF_8);

        assertThat(xml).contains("selectScopedTodoCandidates", "p.dept_id", "currentScopeDeptIds",
                "find_in_set", "left join sys_user", "linked_account_status");
        assertThat(xml).contains("u.nick_name as employee_name", "u.phonenumber as phone_number",
                "u.email as employee_email", "u.sex as employee_sex", "upc.post_names",
                "d.leader as dept_leader", "d.status as dept_status");
        assertThat(xml).contains("selectHealthCertificateTodoCandidates",
                "HR_HEALTH_CERT_REVIEW", "HR_HEALTH_CERT_RETURNED",
                "c.review_status = 'PENDING_REVIEW'",
                "c.review_status in ('REJECTED', 'RETURNED')",
                "and c.user_id = #{userId}",
                "currentScopeDeptIds", "authorizedScopeDeptIds",
                "find_in_set(root.dept_id, d.ancestors)");
        assertThat(xml).doesNotContainIgnoringCase(" limit ");
    }

    @Test
    void scopedEmployeeCandidatesRequireBothCurrentAndAuthorizedDeptIds() throws Exception
    {
        Configuration configuration = new Configuration();
        HealthCertificateMapperFragments.register(configuration);
        try (InputStream input = Resources.getResourceAsStream("mapper/system/SysTodoMapper.xml"))
        {
            new XMLMapperBuilder(input, configuration, "mapper/system/SysTodoMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        TodoQuery query = new TodoQuery();
        Map<String, Object> both = params(query, Set.of("HR_PROFILE_INCOMPLETE"),
                List.of(10L), List.of(10L, 20L));
        both.put("asOfDate", java.time.LocalDate.of(2026, 9, 17));
        String bothSql = candidateSql(configuration, both);
        assertThat(bothSql).contains("root.dept_id in ( ? )", "auth.dept_id in ( ? , ? )")
                .doesNotContain("and 1 = 0");

        Map<String, Object> emptyCurrent = params(query, Set.of("HR_PROFILE_INCOMPLETE"),
                List.of(), List.of(10L, 20L));
        emptyCurrent.put("asOfDate", java.time.LocalDate.of(2026, 9, 17));
        assertThat(candidateSql(configuration, emptyCurrent)).contains("and 1 = 0");

        Map<String, Object> emptyAuthorized = params(query, Set.of("HR_PROFILE_INCOMPLETE"),
                List.of(10L), List.of());
        emptyAuthorized.put("asOfDate", java.time.LocalDate.of(2026, 9, 17));
        assertThat(candidateSql(configuration, emptyAuthorized)).contains("and 1 = 0");
    }

    @Test
    void rendersCrossOrganizationReviewAndPersonalReturnedLaneIndependently() throws Exception
    {
        Configuration configuration = new Configuration();
        HealthCertificateMapperFragments.register(configuration);
        try (InputStream input = Resources.getResourceAsStream("mapper/system/SysTodoMapper.xml"))
        {
            new XMLMapperBuilder(input, configuration, "mapper/system/SysTodoMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        TodoQuery current = new TodoQuery();
        current.setScopeMode("current_org");
        Map<String, Object> both = params(current,
                Set.of("HR_HEALTH_CERT_REVIEW", "HR_HEALTH_CERT_RETURNED"),
                List.of(10L), List.of(10L, 20L));
        String bothSql = sql(configuration, both);
        assertThat(bothSql).contains("c.review_status = 'PENDING_REVIEW'",
                "c.review_status in ('REJECTED', 'RETURNED') and c.user_id = ?",
                "root.dept_id in ( ? , ? )");

        Map<String, Object> returnedOnly = params(new TodoQuery(),
                Set.of("HR_HEALTH_CERT_RETURNED"), List.of(), List.of());
        String returnedSql = sql(configuration, returnedOnly);
        assertThat(returnedSql).contains(
                "c.review_status in ('REJECTED', 'RETURNED') and c.user_id = ?")
                .doesNotContain("PENDING_REVIEW", "and 1 = 0", "root.dept_id in");
    }

    private static Map<String, Object> params(TodoQuery query, Set<String> enabled,
            List<Long> current, List<Long> authorized)
    {
        Map<String, Object> params = new HashMap<>();
        params.put("query", query);
        params.put("enabledTypes", enabled);
        params.put("currentScopeDeptIds", current);
        params.put("authorizedScopeDeptIds", authorized);
        params.put("userId", 7L);
        return params;
    }

    private static String sql(Configuration configuration, Map<String, Object> params)
    {
        BoundSql bound = configuration.getMappedStatement(
                "com.erp.system.mapper.SysTodoMapper.selectHealthCertificateTodoCandidates")
                .getBoundSql(params);
        return bound.getSql().replaceAll("\\s+", " ").trim();
    }

    private static String candidateSql(Configuration configuration, Map<String, Object> params)
    {
        BoundSql bound = configuration.getMappedStatement(
                "com.erp.system.mapper.SysTodoMapper.selectScopedTodoCandidates")
                .getBoundSql(params);
        return bound.getSql().replaceAll("\\s+", " ").trim();
    }
}
