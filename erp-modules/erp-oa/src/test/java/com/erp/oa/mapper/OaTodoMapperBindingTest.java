package com.erp.oa.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import com.erp.common.core.domain.todo.TodoQuery;

class OaTodoMapperBindingTest
{
    private static final String MAPPER = "com.erp.oa.mapper.OaTodoMapper";
    private static final String XML = "mapper/oa/OaTodoMapper.xml";
    private static final Set<String> TYPES = Set.of(
            "OA_LABOR_CONTRACT_SIGN", "OA_SIGN_PACKAGE_SIGN",
            "OA_SIGN_ONBOARD_DATA_REQUEST",
            "OA_SIGN_NEEDS_DATA", "OA_SIGN_COMPANY_FINALIZE", "OA_SIGN_SEND_FAILED",
            "OA_SIGN_REFUSED", "OA_SIGN_EXPIRED");

    @Test
    void bindsThreeQueriesToOneUnifiedOaFactUnion() throws Exception
    {
        Configuration configuration = configuration();
        assertThat(configuration.hasStatement(MAPPER + ".selectOaTodoList")).isTrue();
        assertThat(configuration.hasStatement(MAPPER + ".selectRecentOaTodos")).isTrue();
        assertThat(configuration.hasStatement(MAPPER + ".selectOaTodoCounts")).isTrue();
        assertThat(configuration.hasStatement(MAPPER + ".selectExistingTodoTables")).isTrue();

        String xml = text();
        assertThat(xml).contains("<sql id=\"OaTodoUnion\">",
                "resultMap=\"TodoItemResult\"",
                "type=\"com.erp.oa.domain.vo.OaTodoCandidate\"",
                "property=\"routeReference\" column=\"route_reference\"",
                "resultType=\"com.erp.oa.domain.vo.OaTodoCountRow\"");
        assertThat(count(xml, "<include refid=\"OaTodoUnion\"/>")).isEqualTo(3);
        TYPES.forEach(type -> assertThat(xml).contains(type));
    }

    @Test
    void normalizesEveryOrganizationTextUnionColumnToMysql57Collation() throws Exception
    {
        String xml = normalized(text());

        assertThat(count(xml, " as dept_name")).isEqualTo(11);
        assertThat(count(xml, " as dept_type")).isEqualTo(11);
        assertThat(count(xml, "collate utf8mb4_general_ci as dept_name")).isEqualTo(11);
        assertThat(count(xml, "collate utf8mb4_general_ci as dept_type")).isEqualTo(11);
        assertThat(count(xml, "collate utf8mb4_general_ci as route_reference")).isEqualTo(11);
    }

    @Test
    void rendersEmptyOrAllNineTypesWithTwoDistinctFailureActions() throws Exception
    {
        Configuration configuration = configuration();
        TodoQuery query = new TodoQuery();
        query.setScopeMode("current_org");
        Map<String, Object> empty = params(Set.of(), List.of(), List.of());
        String emptySql = sql(configuration, empty);
        assertThat(emptySql).contains("from dual where 1 = 0").doesNotContain("union all");

        Map<String, Object> all = params(TYPES, List.of(10L), List.of(10L, 20L));
        all.put("query", query);
        String allSql = sql(configuration, all);
        assertThat(count(allSql, "union all")).isEqualTo(9);
        assertThat(count(allSql, "'OA_SIGN_SEND_FAILED' as type")).isEqualTo(2);
        assertThat(allSql).contains("'retry_sign_task' as route_type",
                "'retry_sign_notification' as route_type");
    }

    @Test
    void rendersExactTypeFilter() throws Exception
    {
        Configuration configuration = configuration();
        TodoQuery query = new TodoQuery();
        query.setType("OA_SIGN_PACKAGE_SIGN");
        Map<String, Object> params = params(TYPES, List.of(10L), List.of(10L, 20L));
        params.put("query", query);

        assertThat(sql(configuration, params)).containsIgnoringCase("where type = ?");
        assertThat(text()).contains("type = #{query.type}");
    }

    @Test
    void encodesExactStatusesOwnersScopesContextsAndPriorities() throws Exception
    {
        String xml = normalized(text());
        assertThat(xml).contains(
                "c.status = 'pending_sign'", "c.employee_id = #{userId}",
                "sp.status in ('pending_sign', 'part_viewed', 'pending_final_confirm')", "sp.employee_id = #{userId}",
                "r.status in ('PENDING_EMPLOYEE', 'REJECTED')", "r.employee_id = #{userId}",
                "cast(r.request_id as char(180))", "'onboard_data_request' as route_type",
                "coalesce(c.sent_time, c.update_time, c.create_time, current_timestamp)",
                "coalesce(sp.sent_time, sp.update_time, sp.create_time, current_timestamp)",
                "t.status = 'NEEDS_DATA'", "t.status = 'PENDING_COMPANY'",
                "t.status = 'FAILED'", "t.status = 'REFUSED'", "t.status = 'EXPIRED'",
                "t.resolution_status = 'OPEN'",
                "t.assigned_hr_user_id = #{userId}",
                "dead.status = 'DEAD'",
                "inner join oa_sign_task current_task",
                "dead.recipient_user_id = current_task.assigned_hr_user_id",
                "dead.recipient_user_id = current_task.employee_id",
                "json_extract(dead.payload_json, '$.routeType')",
                "group by cast(json_unquote(json_extract(dead.payload_json, '$.taskId')) as unsigned)",
                "min(dead.outbox_id) as outbox_id",
                ") oldest_dead on oldest_dead.task_id = t.task_id",
                "o.business_key collate utf8mb4_general_ci as route_reference",
                "'sign' as route_type",
                "'important' as priority");
        int notificationFailureStart = xml.indexOf("One oldest actionable notification failure per task");
        String notificationFailure = xml.substring(notificationFailureStart,
                xml.indexOf("</if>", notificationFailureStart));
        assertThat(notificationFailure)
                .contains("'OA_SIGN_HR_TASK'", "'OA_SIGN_PACKAGE_SIGN'")
                .doesNotContain("json_extract(dead.payload_json, '$.hrUserId')");
        assertThat(xml).contains("One oldest actionable notification failure per task",
                "requeueing that key reveals the next oldest failed event")
                .doesNotContain("max(dead.outbox_id) as outbox_id");
        assertThat(xml).doesNotContain("OA_PURCHASE_RETURNED");
        assertThat(todoBranch(xml, "OA_PURCHASE_APPROVAL"))
                .contains("'returned' as category",
                        "p.status = 'returned'",
                        "p.applicant_id = #{userId}",
                        "OperationalOrganizationScope",
                        "p.shop_dept_id",
                        "'revise' as route_type",
                        "'oa:purchase:add' as required_permission")
                .doesNotContain("ACT_RU_TASK", "authorizedTaskIds", "oa:todo:approve");
        assertThat(todoBranch(xml, "OA_LABOR_CONTRACT_SIGN"))
                .doesNotContain("OrganizationScope");
        assertThat(todoBranch(xml, "OA_SIGN_PACKAGE_SIGN"))
                .doesNotContain("OrganizationScope");
        assertThat(todoBranch(xml, "OA_SIGN_ONBOARD_DATA_REQUEST"))
                .doesNotContain("OrganizationScope")
                .contains("oa_sign_onboard_data_request",
                        "r.status in ('PENDING_EMPLOYEE', 'REJECTED')",
                        "r.employee_id = #{userId}")
                .doesNotContain("oa_sign_onboard_import_batch", "SUBMITTED",
                        "PROFILE_SYNC_FAILED", "created_by_user_id");
        for (String type : List.of("OA_SIGN_NEEDS_DATA", "OA_SIGN_COMPANY_FINALIZE",
                "OA_SIGN_SEND_FAILED", "OA_SIGN_REFUSED", "OA_SIGN_EXPIRED"))
        {
            assertThat(todoBranch(xml, type))
                    .contains("OperationalOrganizationScope", "t.shop_dept_id",
                            "t.assigned_hr_user_id = #{userId}");
        }
        assertThat(todoBranch(xml, "OA_SIGN_REFUSED"))
                .contains("'oa:signTask:resolveRefusal' as required_permission");
        assertThat(todoBranch(xml, "OA_SIGN_EXPIRED"))
                .contains("'oa:signTask:resolveExpiry' as required_permission");
    }

    @Test
    void listAndRecentShouldUseTodoKeyStableTailOrdering() throws Exception
    {
        String xml = normalized(text());
        String ordering = "field(priority, 'urgent', 'important', 'normal'), created_time asc, "
                + "business_id asc, type asc, route_type asc";

        assertThat(xml).containsOnlyOnce(ordering + " </select>");
        assertThat(xml).contains(ordering + " limit #{limit}");
    }

    private static Configuration configuration() throws Exception
    {
        Configuration configuration = new Configuration();
        try (InputStream in = Resources.getResourceAsStream(XML))
        {
            new XMLMapperBuilder(in, configuration, XML, configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    private static Map<String, Object> params(Set<String> types, List<Long> current,
            List<Long> authorized)
    {
        return new java.util.HashMap<>(Map.of(
                "query", new TodoQuery(), "enabledTypes", types,
                "currentScopeDeptIds", current, "authorizedScopeDeptIds", authorized,
                "userId", 7L, "username", "operator",
                "approvalUrgentHours", 24, "limit", 5));
    }

    private static String sql(Configuration configuration, Map<String, Object> params)
    {
        BoundSql bound = configuration.getMappedStatement(MAPPER + ".selectOaTodoList").getBoundSql(params);
        return normalized(bound.getSql());
    }

    private static List<Object> foreachValues(BoundSql boundSql)
    {
        return boundSql.getParameterMappings().stream()
                .map(mapping -> mapping.getProperty())
                .filter(boundSql::hasAdditionalParameter)
                .map(boundSql::getAdditionalParameter)
                .toList();
    }

    private static String text() throws Exception
    {
        try (InputStream in = Resources.getResourceAsStream(XML))
        {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String normalized(String value)
    {
        return value.replaceAll("\\s+", " ").trim();
    }

    private static int count(String text, String needle)
    {
        return (text.length() - text.replace(needle, "").length()) / needle.length();
    }

    private static String todoBranch(String xml, String type)
    {
        int start = xml.indexOf("contains('" + type + "')");
        int next = xml.indexOf("<if test=", start + 1);
        return next < 0 ? xml.substring(start) : xml.substring(start, next);
    }
}
