package com.erp.inventory.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.domain.todo.TodoQuery;

@DisplayName("库存统一待办 Mapper 绑定")
class InvTodoMapperBindingTest
{
    private static final String MAPPER_CLASS = "com.erp.inventory.mapper.InvTodoMapper";
    private static final String TYPES_CLASS = "com.erp.inventory.constant.InvTodoTypes";
    private static final String COUNT_ROW_CLASS = "com.erp.inventory.domain.vo.InvTodoCountRow";
    private static final String MAPPER_XML = "mapper/inventory/InvTodoMapper.xml";

    private static final Set<String> TODO_TYPES = Set.of(
            "INV_TRANSFER_APPROVAL",
            "INV_STOCK_CHECK_APPROVAL",
            "INV_STOCK_CHECK_EXECUTE",
            "INV_PURCHASE_QC",
            "INV_PURCHASE_RECEIVE",
            "INV_SALES_NOTICE_CREATE",
            "INV_DELIVERY_EXECUTE",
            "INV_TRANSFER_DELIVER",
            "INV_TRANSFER_SOURCE_CONFIRM",
            "INV_TRANSFER_RECEIVE",
            "INV_TRANSFER_DISCREPANCY",
            "INV_PURCHASE_RETURN_CONFIRM",
            "INV_SALES_RETURN_CONFIRM",
            "INV_TRANSFER_RETURNED",
            "INV_TRANSFER_SOURCE_RESELECT",
            "INV_STOCK_CHECK_RETURNED",
            "INV_STOCK_CHECK_RESTART",
            "INV_OUT_OF_STOCK",
            "INV_LOW_STOCK");

    @Test
    @DisplayName("Mapper 仅暴露三个统一事实查询且参数名稳定")
    void mapperShouldExposeExactReadContracts() throws Exception
    {
        Class<?> mapperType = Class.forName(MAPPER_CLASS);
        Map<String, Method> methods = Arrays.stream(mapperType.getDeclaredMethods())
                .collect(Collectors.toMap(Method::getName, Function.identity()));

        assertThat(methods.keySet()).containsExactlyInAnyOrder(
                "selectInventoryTodoList", "selectRecentInventoryTodos", "selectInventoryTodoCounts");
        assertMethod(methods.get("selectInventoryTodoList"),
                "java.util.List<com.erp.common.core.domain.todo.TodoItem>",
                List.of("query", "enabledTypes", "currentScopeDeptIds", "authorizedScopeDeptIds",
                        "userId", "username", "approvalUrgentHours", "stockCheckDueSoonHours"));
        assertMethod(methods.get("selectRecentInventoryTodos"),
                "java.util.List<com.erp.common.core.domain.todo.TodoItem>",
                List.of("query", "enabledTypes", "currentScopeDeptIds", "authorizedScopeDeptIds",
                        "userId", "username", "approvalUrgentHours", "stockCheckDueSoonHours", "limit"));
        assertMethod(methods.get("selectInventoryTodoCounts"),
                "java.util.List<com.erp.inventory.domain.vo.InvTodoCountRow>",
                List.of("query", "enabledTypes", "currentScopeDeptIds", "authorizedScopeDeptIds",
                        "userId", "username", "approvalUrgentHours", "stockCheckDueSoonHours"));
    }

    @Test
    @DisplayName("待办类型和统计行是稳定的公共契约")
    void todoTypesAndCountRowShouldBeStableBeans() throws Exception
    {
        Class<?> typesClass = Class.forName(TYPES_CLASS);
        Map<String, String> constants = Arrays.stream(typesClass.getDeclaredFields())
                .filter(field -> Modifier.isPublic(field.getModifiers()))
                .filter(field -> Modifier.isStatic(field.getModifiers()))
                .filter(field -> Modifier.isFinal(field.getModifiers()))
                .filter(field -> field.getType() == String.class)
                .collect(Collectors.toMap(Field::getName, field -> fieldValue(field)));
        assertThat(constants.keySet()).isEqualTo(TODO_TYPES);
        assertThat(constants).allSatisfy((name, value) -> assertThat(value).isEqualTo(name));
        Constructor<?> constructor = typesClass.getDeclaredConstructor();
        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();

        Class<?> countRowType = Class.forName(COUNT_ROW_CLASS);
        assertThat(Serializable.class.isAssignableFrom(countRowType)).isTrue();
        assertBeanProperty(countRowType, "type", String.class);
        assertBeanProperty(countRowType, "category", String.class);
        assertBeanProperty(countRowType, "priority", String.class);
        assertBeanProperty(countRowType, "count", long.class);
    }

    @Test
    @DisplayName("三个查询绑定到同一个库存事实 union")
    void shouldParseAndBindAllStatementsToSharedUnion() throws Exception
    {
        Class<?> mapperType = Class.forName(MAPPER_CLASS);
        Configuration configuration = new Configuration();
        parseMapper(configuration, MAPPER_XML);

        assertMapped(configuration, mapperType, "selectInventoryTodoList");
        assertMapped(configuration, mapperType, "selectRecentInventoryTodos");
        assertMapped(configuration, mapperType, "selectInventoryTodoCounts");

        String xml = resourceText(MAPPER_XML);
        assertThat(xml).contains(
                "namespace=\"" + MAPPER_CLASS + "\"",
                "<sql id=\"InventoryTodoUnion\">",
                "<include refid=\"InventoryTodoUnion\"/>",
                "resultMap=\"TodoItemResult\"",
                "resultType=\"com.erp.inventory.domain.vo.InvTodoCountRow\"");
        assertThat(countOccurrences(xml, "<include refid=\"InventoryTodoUnion\"/>")).isEqualTo(3);
        assertThat(xml).contains("where 1 = 0");
        assertThat(xml).doesNotContain("todo_key", "route_params", "json_object");
        TODO_TYPES.forEach(type -> assertThat(xml).contains(type));
    }

    @Test
    @DisplayName("所有组织文本事实使用 MySQL 5.7 兼容排序规则")
    void organizationTextFactsShouldUseOneExplicitCollation() throws Exception
    {
        String xml = normalized(resourceText(MAPPER_XML));
        assertThat(countOccurrences(xml, " as dept_name")).isEqualTo(21);
        assertThat(countOccurrences(xml, " as dept_type")).isEqualTo(21);
        assertThat(countOccurrences(xml, "collate utf8mb4_general_ci as dept_name")).isEqualTo(21);
        assertThat(countOccurrences(xml, "collate utf8mb4_general_ci as dept_type")).isEqualTo(21);
    }

    @Test
    @DisplayName("所有标题和摘要事实使用 MySQL 5.7 兼容排序规则")
    void presentationTextFactsShouldUseOneExplicitCollation() throws Exception
    {
        String xml = normalized(resourceText(MAPPER_XML));
        assertThat(countOccurrences(xml, " as title")).isEqualTo(21);
        assertThat(countOccurrences(xml, " as summary")).isEqualTo(21);
        assertThat(countOccurrences(xml, "collate utf8mb4_general_ci as title")).isEqualTo(21);
        assertThat(countOccurrences(xml, "collate utf8mb4_general_ci as summary")).isEqualTo(21);
    }

    @Test
    @DisplayName("UNION 文本类型锚点统一使用 MySQL 5.7 兼容排序规则")
    void unionTextAnchorsShouldUseOneExplicitCollation() throws Exception
    {
        String xml = normalized(resourceText(MAPPER_XML));
        assertThat(xml).contains(
                "cast(null as char(16)) collate utf8mb4_general_ci as source",
                "cast(null as char(64)) collate utf8mb4_general_ci as type",
                "cast(null as char(16)) collate utf8mb4_general_ci as category",
                "cast(null as char(128)) collate utf8mb4_general_ci as business_no",
                "cast(null as char(32)) collate utf8mb4_general_ci as status",
                "cast(null as char(16)) collate utf8mb4_general_ci as priority",
                "cast(null as char(32)) collate utf8mb4_general_ci as scope_mode",
                "cast(null as char(32)) collate utf8mb4_general_ci as route_type",
                "cast(null as char(128)) collate utf8mb4_general_ci as required_permission");
    }

    @Test
    @DisplayName("风险聚合摘要先转换为 utf8mb4 再应用排序规则")
    void riskAggregateSummariesShouldConvertBeforeCollation() throws Exception
    {
        String xml = normalized(resourceText(MAPPER_XML));
        assertThat(todoBranch(xml, "INV_OUT_OF_STOCK")).contains(
                "convert(concat('缺货商品：', count(distinct st.product_id), ' 种') using utf8mb4) collate utf8mb4_general_ci as summary");
        assertThat(todoBranch(xml, "INV_LOW_STOCK")).contains(
                "convert(concat('低库存商品：', count(distinct st.product_id), ' 种') using utf8mb4) collate utf8mb4_general_ci as summary");
    }

    @Test
    @DisplayName("动态 SQL 对空类型合法并能渲染全部已启用事实")
    void shouldRenderEmptyAndFullyEnabledTypeSets() throws Exception
    {
        Configuration configuration = new Configuration();
        parseMapper(configuration, MAPPER_XML);

        Map<String, Object> emptyParams = queryParams(Set.of(), null, List.of(), List.of());
        String emptySql = normalized(configuration.getMappedStatement(
                MAPPER_CLASS + ".selectInventoryTodoList").getBoundSql(emptyParams).getSql());
        assertThat(emptySql).contains("from dual where 1 = 0").doesNotContain("union all");

        TodoQuery emptyScopeQuery = new TodoQuery();
        emptyScopeQuery.setScopeMode("current_org");
        Map<String, Object> emptyScopeParams = queryParams(
                Set.of("INV_LOW_STOCK"), emptyScopeQuery, List.of(), List.of(20L));
        String emptyScopeSql = normalized(configuration.getMappedStatement(
                MAPPER_CLASS + ".selectInventoryTodoList").getBoundSql(emptyScopeParams).getSql());
        assertThat(emptyScopeSql).contains("and 1 = 0");

        TodoQuery query = new TodoQuery();
        query.setScopeMode("current_org");
        Map<String, Object> fullParams = queryParams(TODO_TYPES, query, List.of(10L), List.of(10L, 20L));
        String fullSql = normalized(configuration.getMappedStatement(
                MAPPER_CLASS + ".selectInventoryTodoList").getBoundSql(fullParams).getSql());
        assertThat(countOccurrences(fullSql, "union all")).isEqualTo(20);
        assertThat(fullSql).doesNotContain("${scopeColumn}");
        TODO_TYPES.forEach(type -> assertThat(fullSql).contains("'" + type + "' as type"));
    }

    @Test
    @DisplayName("类型过滤在统一事实外层生效")
    void shouldRenderExactTypeFilter() throws Exception
    {
        Configuration configuration = new Configuration();
        parseMapper(configuration, MAPPER_XML);
        TodoQuery query = new TodoQuery();
        query.setType("INV_TRANSFER_RECEIVE");

        String sql = normalized(configuration.getMappedStatement(
                MAPPER_CLASS + ".selectInventoryTodoList")
                .getBoundSql(queryParams(TODO_TYPES, query, List.of(10L), List.of(10L, 20L)))
                .getSql());

        assertThat(sql).containsIgnoringCase("where type = ?");
    }

    @Test
    @DisplayName("审批事实严格绑定当前任务候选人和有效运营总监授权")
    void approvalFactsShouldEnforceCurrentCandidateAndAuthorization() throws Exception
    {
        String sql = normalized(resourceText(MAPPER_XML));

        assertThat(sql).contains(
                "o.status = 'submitted'",
                "i.status = 'running'",
                "t.status = 'pending'",
                "t.node_order = i.current_node_order",
                "find_in_set(#{userId}, t.candidate_user_ids)",
                "i.allow_self_approve",
                "s.status = 'pending_approval'",
                "ai.instance_id = coalesce( s.approval_instance_id, (select fallback_ai.instance_id",
                "fallback_ai.check_id = s.check_id",
                "fallback_ai.status = 'running'",
                "order by fallback_ai.round_no desc, fallback_ai.instance_id desc limit 1",
                "ai.status = 'running'",
                "at.status = 'pending'",
                "find_in_set(#{userId}, at.candidate_user_ids)",
                "p.post_code = 'yyzj'",
                "m.perms = 'inv:stockCheck:approve'",
                "u.del_flag = '0'",
                "u.status = '0'",
                "scope_dept.status = '0'",
                "target_dept.status = '0'");
        assertThat(sql).doesNotContain("userId == 1", "userId != 1", "isAdmin");
    }

    @Test
    @DisplayName("本人专属待办跨当前门店且公共待办只取当前组织")
    void actionableScopeShouldSeparateDirectOwnersFromSharedQueues() throws Exception
    {
        String xml = resourceText(MAPPER_XML);
        for (String type : List.of(
                "INV_TRANSFER_APPROVAL", "INV_STOCK_CHECK_APPROVAL", "INV_TRANSFER_RETURNED",
                "INV_STOCK_CHECK_EXECUTE", "INV_STOCK_CHECK_RETURNED", "INV_STOCK_CHECK_RESTART"))
        {
            assertThat(todoBranch(xml, type))
                    .as(type + " direct owner scope")
                    .contains("DirectOwnerAuthorizedOrganizationScope")
                    .doesNotContain("SharedCurrentOrganizationScope");
        }
        for (String type : List.of(
                "INV_PURCHASE_QC", "INV_PURCHASE_RECEIVE", "INV_SALES_NOTICE_CREATE",
                "INV_DELIVERY_EXECUTE", "INV_TRANSFER_DELIVER", "INV_TRANSFER_RECEIVE",
                "INV_TRANSFER_DISCREPANCY", "INV_TRANSFER_SOURCE_CONFIRM",
                "INV_TRANSFER_SOURCE_RESELECT",
                "INV_PURCHASE_RETURN_CONFIRM", "INV_SALES_RETURN_CONFIRM",
                "INV_OUT_OF_STOCK", "INV_LOW_STOCK"))
        {
            assertThat(todoBranch(xml, type))
                    .as(type + " shared current organization scope")
                    .contains("SharedCurrentOrganizationScope")
                    .doesNotContain("DirectOwnerAuthorizedOrganizationScope");
        }
    }

    @Test
    @DisplayName("当前门店A下本人任务可跨到已授权B但公共队列不能越过A或进入未授权C")
    void actionableDynamicSqlShouldUseAuthorizedStoresForDirectOwnersAndCurrentStoreForSharedQueues()
            throws Exception
    {
        Configuration configuration = new Configuration();
        parseMapper(configuration, MAPPER_XML);

        BoundSql direct = todoBoundSql(configuration, "INV_TRANSFER_APPROVAL",
                "actionable", List.of(10L), List.of(10L, 20L));
        assertThat(normalized(direct.getSql())).contains("and visible_dept.dept_id in ( ? , ? )");
        assertThat(foreachValues(direct)).containsOnly(10L, 20L).doesNotContain(30L);

        BoundSql directWithoutSelectedStore = todoBoundSql(configuration, "INV_TRANSFER_RETURNED",
                "actionable", List.of(), List.of(10L, 20L));
        assertThat(normalized(directWithoutSelectedStore.getSql()))
                .contains("and context_dept.dept_id in ( ? , ? )")
                .doesNotContain("and 1 = 0");
        assertThat(foreachValues(directWithoutSelectedStore)).containsOnly(10L, 20L).doesNotContain(30L);

        BoundSql shared = todoBoundSql(configuration, "INV_PURCHASE_RECEIVE",
                "actionable", List.of(10L), List.of(10L, 20L));
        assertThat(normalized(shared.getSql())).contains("and o.shop_dept_id in ( ? )");
        assertThat(foreachValues(shared)).containsOnly(10L).doesNotContain(20L, 30L);

        BoundSql sharedWithoutSelectedStore = todoBoundSql(configuration, "INV_PURCHASE_RECEIVE",
                "actionable", List.of(), List.of(10L, 20L));
        assertThat(normalized(sharedWithoutSelectedStore.getSql())).contains("and 1 = 0");
    }

    @Test
    @DisplayName("调拨禁止自审必须按原单创建人判断")
    void transferApprovalShouldCompareOrderCreatorForSelfApproval() throws Exception
    {
        String branch = todoBranch(resourceText(MAPPER_XML), "INV_TRANSFER_APPROVAL");

        assertThat(branch).contains("coalesce(o.create_by, '') &lt;&gt; coalesce(#{username}, '')");
        assertThat(branch).doesNotContain("coalesce(i.create_by, '') &lt;&gt; coalesce(#{username}, '')");
    }

    @Test
    @DisplayName("五类调拨待办按门店要货返仓和异店调货显示业务文案")
    void transferTodosShouldUseBusinessSpecificTitles() throws Exception
    {
        String xml = resourceText(MAPPER_XML);
        Map<String, String> actions = Map.of(
                "INV_TRANSFER_APPROVAL", "待审批",
                "INV_TRANSFER_DELIVER", "待发货",
                "INV_TRANSFER_RECEIVE", "待收货",
                "INV_TRANSFER_DISCREPANCY", "差异待处理",
                "INV_TRANSFER_RETURNED", "已退回");

        actions.forEach((type, action) -> {
            String branch = todoBranch(xml, type);
            assertThat(branch).contains(
                    "case o.transfer_type",
                    "when 'warehouse' then '门店要货" + action + "'",
                    "when 'store_return' then '门店返仓" + action + "'",
                    "when 'cross_store' then '异店调货" + action + "'");
        });
    }

    @Test
    @DisplayName("执行事实采用真实剩余量并保持互斥")
    void executionFactsShouldUseActionableGuards() throws Exception
    {
        String sql = normalized(resourceText(MAPPER_XML));

        assertThat(sql).contains(
                "from inv_inbound_record inbound",
                "inbound.qc_result = 'pending'",
                "o.qc_status = 'pending'",
                "min(inbound.create_time)",
                "sum(coalesce(d.quantity, 0) - coalesce(d.received_quantity, 0))",
                "coalesce(o.qc_status, '') &lt;&gt; 'pending'",
                "sum(coalesce(d.quantity, 0) - coalesce(d.delivered_quantity, 0))",
                "not exists",
                "active_notice.status in ('pending', 'delivering')",
                "n.status in ('pending', 'delivering')",
                "sum(coalesce(nd.notice_qty, 0) - coalesce(nd.delivered_qty, 0))",
                "o.status in ('approved', 'reserved', 'partial_delivered')",
                "sh.status = 'pending_receive'",
                "sh.shipment_id as business_id",
                "r.status = 'submitted'",
                "coalesce(rd.quantity, 0) - coalesce(rd.returned_quantity, 0)");
    }

    @Test
    @DisplayName("多仓发货通知从当前有效范围内的剩余明细选择一个仓库上下文")
    void deliveryExecuteShouldResolveOneScopedWarehouseFromRemainingDetails() throws Exception
    {
        String branch = todoBranch(resourceText(MAPPER_XML), "INV_DELIVERY_EXECUTE");

        assertThat(branch).contains(
                "context_dept.dept_id = ( select min(scoped_sd.warehouse_id)",
                "select min(scoped_sd.warehouse_id)",
                "from inv_delivery_notice_detail scoped_nd",
                "inner join inv_sales_detail scoped_sd",
                "scoped_sd.detail_id = scoped_nd.sales_detail_id",
                "scoped_nd.notice_id = n.notice_id",
                "coalesce(scoped_nd.notice_qty, 0) - coalesce(scoped_nd.delivered_qty, 0) &gt; 0",
                "<property name=\"scopeColumn\" value=\"scoped_sd.warehouse_id\"/>",
                "<property name=\"scopeColumn\" value=\"context_dept.dept_id\"/>");
        assertThat(branch).doesNotContain(
                "coalesce(n.warehouse_id",
                "n.warehouse_id",
                "coalesce(n.warehouse_id, n.shop_dept_id)",
                "n.shop_dept_id");
        assertThat(countOccurrences(branch, "select min(scoped_sd.warehouse_id)")).isEqualTo(1);
    }

    @Test
    @DisplayName("部分发货后通知上的最近仓库不得短路剩余明细仓库")
    void deliveryExecuteShouldIgnoreLastOperatedNoticeWarehouse() throws Exception
    {
        String branch = todoBranch(resourceText(MAPPER_XML), "INV_DELIVERY_EXECUTE");

        assertThat(branch).contains(
                "context_dept.dept_id = ( select min(scoped_sd.warehouse_id)",
                "scoped_nd.notice_id = n.notice_id",
                "coalesce(scoped_nd.notice_qty, 0) - coalesce(scoped_nd.delivered_qty, 0) &gt; 0");
        assertThat(branch).doesNotContain(
                "coalesce(n.warehouse_id",
                "n.warehouse_id");
    }

    @Test
    @DisplayName("多仓通知子查询和外层仓库都只使用当前组织范围")
    void deliveryExecuteDynamicSqlShouldApplyCurrentOrganizationScopeTwice() throws Exception
    {
        Configuration configuration = new Configuration();
        parseMapper(configuration, MAPPER_XML);

        BoundSql actionableSql = deliveryBoundSql(configuration, "actionable", List.of(101L), List.of(202L));
        assertThat(normalized(actionableSql.getSql())).contains(
                "and scoped_sd.warehouse_id in ( ? )",
                "and context_dept.dept_id in ( ? )");
        assertThat(foreachValues(actionableSql)).containsOnly(101L);

        BoundSql legacyScopeSql = deliveryBoundSql(
                configuration, "all_authorized", List.of(101L), List.of(202L));
        assertThat(normalized(legacyScopeSql.getSql())).contains(
                "and scoped_sd.warehouse_id in ( ? )",
                "and context_dept.dept_id in ( ? )");
        assertThat(foreachValues(legacyScopeSql)).containsOnly(101L);
    }

    @Test
    @DisplayName("调拨收货待办只允许目标组织看到")
    void transferReceiveTodoShouldBeScopedToDestination() throws Exception
    {
        String branch = todoBranch(resourceText(MAPPER_XML), "INV_TRANSFER_RECEIVE");

        assertThat(branch).contains(
                "sh.status = 'pending_receive'",
                "sh.shipment_id as business_id",
                "o.to_warehouse_id",
                "o.to_dept_id");
        assertThat(branch).doesNotContain(
                "sh.warehouse_dept_id",
                "o.from_warehouse_id",
                "o.from_dept_id");
    }

    @Test
    @DisplayName("退回类待办按各自提交人和状态事实精确归属")
    void returnedTodosShouldUseBranchLocalOwnerAndTimeGuards() throws Exception
    {
        String xml = resourceText(MAPPER_XML);
        String transferReturned = todoBranch(xml, "INV_TRANSFER_RETURNED");
        String stockExecute = todoBranch(xml, "INV_STOCK_CHECK_EXECUTE");
        String stockReturned = todoBranch(xml, "INV_STOCK_CHECK_RETURNED");
        String stockRestart = todoBranch(xml, "INV_STOCK_CHECK_RESTART");

        assertThat(transferReturned).contains(
                "o.status = 'draft'",
                "o.create_by = #{username}",
                "rejected_instance.status = 'rejected'",
                "rejected_instance.update_time");
        assertThat(stockExecute).contains(
                "s.status = 'draft'",
                "s.counter_user_id = #{userId}",
                "s.deadline is not null",
                "'overdue'",
                "'due_soon'",
                "#{stockCheckDueSoonHours} * 3600");
        assertThat(stockReturned).contains(
                "s.status = 'rejected'",
                "coalesce(s.counter_user_id, s.submitted_user_id) = #{userId}",
                "s.last_rejected_time");
        assertThat(stockRestart).contains(
                "s.status = 'invalidated'",
                "coalesce(s.counter_user_id, s.submitted_user_id) = #{userId}",
                "s.last_invalidated_time");
    }

    @Test
    @DisplayName("调拨退回修改权限由服务按 add 或 edit 实际权限装饰")
    void transferReturnedShouldLeaveOrPermissionToService() throws Exception
    {
        String branch = todoBranch(resourceText(MAPPER_XML), "INV_TRANSFER_RETURNED");

        assertThat(branch).contains(
                "service-owned add/edit OR permission",
                "cast(null as char) as required_permission");
        assertThat(branch).doesNotContain("'inv:transfer:edit' as required_permission");
    }

    @Test
    @DisplayName("调拨退回待办必须切换到实际拥有草稿编辑权的组织")
    void transferReturnedShouldUseTheDraftOwnerOrganization() throws Exception
    {
        String branch = normalized(
                todoBranch(resourceText(MAPPER_XML), "INV_TRANSFER_RETURNED"));

        assertThat(branch).contains(
                "when o.transfer_type = 'store_return' or o.source_business_type = 'oe_replenishment'",
                "then coalesce(o.from_dept_id, o.from_warehouse_id)",
                "else coalesce(o.to_dept_id, o.to_warehouse_id)",
                "<property name=\"scopeColumn\" value=\"context_dept.dept_id\"/>");
        assertThat(branch).doesNotContain(
                "visible_dept.dept_id in ( o.from_dept_id, o.to_dept_id");
    }

    @Test
    @DisplayName("退回与风险事实不会混入普通草稿或重叠库存阈值")
    void returnedAndRiskFactsShouldBeExactAndMutuallyExclusive() throws Exception
    {
        String sql = normalized(resourceText(MAPPER_XML));

        assertThat(sql).contains(
                "o.status = 'draft'",
                "o.create_by = #{username}",
                "rejected_instance.status = 'rejected'",
                "not exists",
                "newer_instance.instance_id &gt; rejected_instance.instance_id",
                "s.status = 'rejected'",
                "coalesce(s.counter_user_id, s.submitted_user_id) = #{userId}",
                "s.status = 'invalidated'",
                "s.last_invalidated_time",
                "coalesce(st.available_quantity, st.current_quantity, 0) &lt;= 0",
                "coalesce(st.available_quantity, st.current_quantity, 0) &gt; 0",
                "coalesce(st.available_quantity, st.current_quantity, 0) &lt;= coalesce(p.safety_stock_min, 10)",
                "group by st.shop_dept_id");
    }

    @Test
    @DisplayName("风险汇总卡片按组织展示但统计值按互斥风险商品数累加")
    void riskCardsShouldCarryDistinctProductCountsForSummaryAggregation() throws Exception
    {
        String xml = resourceText(MAPPER_XML);
        String outOfStock = todoBranch(xml, "INV_OUT_OF_STOCK");
        String lowStock = todoBranch(xml, "INV_LOW_STOCK");
        String counts = statementXml(xml, "selectInventoryTodoCounts");
        String list = statementXml(xml, "selectInventoryTodoList");
        String recent = statementXml(xml, "selectRecentInventoryTodos");

        assertThat(outOfStock)
                .contains("count(distinct st.product_id) as count_value")
                .contains("coalesce(st.available_quantity, st.current_quantity, 0) &lt;= 0")
                .contains("group by st.shop_dept_id");
        assertThat(lowStock)
                .contains("count(distinct st.product_id) as count_value")
                .contains("coalesce(st.available_quantity, st.current_quantity, 0) &gt; 0")
                .contains("coalesce(st.available_quantity, st.current_quantity, 0) &lt;= coalesce(p.safety_stock_min, 10)")
                .contains("group by st.shop_dept_id");
        TODO_TYPES.stream()
                .filter(type -> !"INV_OUT_OF_STOCK".equals(type))
                .filter(type -> !"INV_LOW_STOCK".equals(type))
                .forEach(type -> assertThat(todoBranch(xml, type))
                        .as(type + " count value")
                        .contains("1 as count_value"));
        assertThat(counts)
                .contains("sum(count_value) as count")
                .doesNotContain("count(*) as count");
        assertThat(list.substring(0, list.indexOf("from ("))).doesNotContain("count_value");
        assertThat(recent.substring(0, recent.indexOf("from ("))).doesNotContain("count_value");
    }

    @Test
    @DisplayName("范围、过滤、优先级和稳定排序由统一事实外层处理")
    void shouldApplyFailClosedScopeFilteringPriorityAndOrdering() throws Exception
    {
        String xml = resourceText(MAPPER_XML);
        String normalized = normalized(xml);
        String ordering = "field(priority, 'urgent', 'important', 'normal'), created_time asc, "
                + "business_id asc, type asc, route_type asc";

        assertThat(xml).contains(
                "<sql id=\"ActionableScopeMode\">'actionable'</sql>",
                "<sql id=\"DirectOwnerAuthorizedOrganizationScope\">",
                "<sql id=\"SharedCurrentOrganizationScope\">",
                "currentScopeDeptIds != null and currentScopeDeptIds.size() > 0",
                "authorizedScopeDeptIds != null and authorizedScopeDeptIds.size() > 0",
                "<otherwise>and 1 = 0</otherwise>",
                "query.type",
                "query.category",
                "query.keyword",
                "query.priority");
        assertThat(xml).doesNotContain("query.scopeMode == 'current_org'",
                "query.scopeMode == 'all_authorized'");
        assertThat(normalized).contains(
                "type = #{query.type}",
                "category = #{query.category}",
                "priority = #{query.priority}",
                "business_no like concat('%', #{query.keyword}, '%')",
                "title like concat('%', #{query.keyword}, '%')",
                "summary like concat('%', #{query.keyword}, '%')",
                "timestampdiff(second,",
                "#{approvalUrgentHours} * 3600",
                "#{stockCheckDueSoonHours} * 3600",
                "greatest(timestampdiff(second,",
                "as waiting_seconds",
                ordering,
                "limit #{limit}",
                "group by type, category, priority");
        assertThat(countOccurrences(normalized, ordering)).isEqualTo(2);
        assertThat(countOccurrences(xml, "priority = #{query.priority}")).isEqualTo(1);
        assertThat(countOccurrences(xml, "<include refid=\"InventoryTodoFilters\"/>")).isEqualTo(3);
    }

    @Test
    @DisplayName("优先级与分类关键词在统一事实外层按 AND 关系渲染")
    void shouldRenderPriorityWithCategoryAndKeywordInSharedOuterFilter() throws Exception
    {
        Configuration configuration = new Configuration();
        parseMapper(configuration, MAPPER_XML);
        TodoQuery query = new TodoQuery();
        query.setPriority("urgent");
        query.setCategory("risk");
        query.setKeyword("缺货");
        Map<String, Object> params = queryParams(TODO_TYPES, query, List.of(10L), List.of(10L, 20L));

        String sql = normalized(configuration.getMappedStatement(
                MAPPER_CLASS + ".selectInventoryTodoList").getBoundSql(params).getSql());

        assertThat(sql).containsIgnoringCase("where category = ? and priority = ? and (")
                .contains("business_no like concat('%', ?, '%')")
                .contains("title like concat('%', ?, '%')")
                .contains("summary like concat('%', ?, '%')");
    }

    private static void assertMethod(Method method, String returnType, List<String> paramNames)
    {
        assertThat(method).isNotNull();
        assertThat(method.getGenericReturnType().getTypeName()).isEqualTo(returnType);
        assertThat(Arrays.stream(method.getParameterTypes())
                .map(Class::getName)
                .collect(Collectors.toList()))
                .containsExactlyElementsOf(paramNames.stream()
                        .map(InvTodoMapperBindingTest::expectedParameterType)
                        .collect(Collectors.toList()));
        assertThat(Arrays.stream(method.getParameters())
                .map(parameter -> parameter.getAnnotation(Param.class))
                .map(Param::value)
                .collect(Collectors.toList())).containsExactlyElementsOf(paramNames);
    }

    private static String expectedParameterType(String paramName)
    {
        return switch (paramName)
        {
            case "query" -> "com.erp.common.core.domain.todo.TodoQuery";
            case "enabledTypes" -> "java.util.Set";
            case "currentScopeDeptIds", "authorizedScopeDeptIds" -> "java.util.List";
            case "userId" -> "java.lang.Long";
            case "username" -> "java.lang.String";
            case "approvalUrgentHours", "stockCheckDueSoonHours", "limit" -> "int";
            default -> throw new AssertionError("Unexpected parameter: " + paramName);
        };
    }

    private static void assertBeanProperty(Class<?> type, String property, Class<?> propertyType)
            throws Exception
    {
        Field field = type.getDeclaredField(property);
        assertThat(field.getType()).isEqualTo(propertyType);
        String suffix = Character.toUpperCase(property.charAt(0)) + property.substring(1);
        assertThat(type.getMethod("get" + suffix).getReturnType()).isEqualTo(propertyType);
        assertThat(type.getMethod("set" + suffix, propertyType).getReturnType()).isEqualTo(void.class);
    }

    private static String fieldValue(Field field)
    {
        try
        {
            return (String) field.get(null);
        }
        catch (IllegalAccessException exception)
        {
            throw new AssertionError(exception);
        }
    }

    private static void parseMapper(Configuration configuration, String resource) throws Exception
    {
        try (InputStream inputStream = Resources.getResourceAsStream(resource))
        {
            XMLMapperBuilder mapperBuilder = new XMLMapperBuilder(inputStream, configuration, resource,
                    configuration.getSqlFragments());
            mapperBuilder.parse();
        }
    }

    private static String resourceText(String resource) throws Exception
    {
        try (InputStream inputStream = Resources.getResourceAsStream(resource))
        {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void assertMapped(Configuration configuration, Class<?> mapperType, String methodName)
    {
        assertThat(configuration.hasStatement(mapperType.getName() + "." + methodName))
                .as(mapperType.getSimpleName() + "." + methodName)
                .isTrue();
    }

    private static Map<String, Object> queryParams(Set<String> enabledTypes, TodoQuery query,
            List<Long> currentScopeDeptIds, List<Long> authorizedScopeDeptIds)
    {
        Map<String, Object> params = new HashMap<>();
        params.put("query", query);
        params.put("enabledTypes", enabledTypes);
        params.put("currentScopeDeptIds", currentScopeDeptIds);
        params.put("authorizedScopeDeptIds", authorizedScopeDeptIds);
        params.put("userId", 99L);
        params.put("username", "tester");
        params.put("approvalUrgentHours", 24);
        params.put("stockCheckDueSoonHours", 24);
        params.put("limit", 5);
        return params;
    }

    private static BoundSql deliveryBoundSql(Configuration configuration, String scopeMode,
            List<Long> currentScopeDeptIds, List<Long> authorizedScopeDeptIds)
    {
        return todoBoundSql(configuration, "INV_DELIVERY_EXECUTE", scopeMode,
                currentScopeDeptIds, authorizedScopeDeptIds);
    }

    private static BoundSql todoBoundSql(Configuration configuration, String type, String scopeMode,
            List<Long> currentScopeDeptIds, List<Long> authorizedScopeDeptIds)
    {
        TodoQuery query = new TodoQuery();
        query.setScopeMode(scopeMode);
        Map<String, Object> params = queryParams(Set.of(type), query,
                currentScopeDeptIds, authorizedScopeDeptIds);
        return configuration.getMappedStatement(MAPPER_CLASS + ".selectInventoryTodoList")
                .getBoundSql(params);
    }

    private static List<Object> foreachValues(BoundSql boundSql)
    {
        return boundSql.getParameterMappings().stream()
                .map(mapping -> mapping.getProperty())
                .filter(boundSql::hasAdditionalParameter)
                .map(boundSql::getAdditionalParameter)
                .collect(Collectors.toList());
    }

    private static int countOccurrences(String value, String needle)
    {
        return (value.length() - value.replace(needle, "").length()) / needle.length();
    }

    private static String todoBranch(String mapperXml, String type)
    {
        String marker = "enabledTypes.contains('" + type + "')";
        int markerIndex = mapperXml.indexOf(marker);
        assertThat(markerIndex).as(type + " branch marker").isGreaterThanOrEqualTo(0);
        int branchStart = mapperXml.lastIndexOf("<if", markerIndex);
        int nextBranch = mapperXml.indexOf("<if test=\"enabledTypes", markerIndex + marker.length());
        int unionEnd = mapperXml.indexOf("</sql>", markerIndex);
        int branchEnd = nextBranch >= 0 && nextBranch < unionEnd ? nextBranch : unionEnd;
        assertThat(branchStart).as(type + " branch start").isGreaterThanOrEqualTo(0);
        assertThat(branchEnd).as(type + " branch end").isGreaterThan(branchStart);
        return normalized(mapperXml.substring(branchStart, branchEnd));
    }

    private static String statementXml(String mapperXml, String statementId)
    {
        int idIndex = mapperXml.indexOf("id=\"" + statementId + "\"");
        assertThat(idIndex).as(statementId + " statement id").isGreaterThanOrEqualTo(0);
        int start = mapperXml.lastIndexOf("<select", idIndex);
        int end = mapperXml.indexOf("</select>", idIndex);
        assertThat(start).as(statementId + " statement start").isGreaterThanOrEqualTo(0);
        assertThat(end).as(statementId + " statement end").isGreaterThan(start);
        return normalized(mapperXml.substring(start, end));
    }

    private static String normalized(String value)
    {
        return value.replaceAll("\\s+", " ").trim();
    }
}
