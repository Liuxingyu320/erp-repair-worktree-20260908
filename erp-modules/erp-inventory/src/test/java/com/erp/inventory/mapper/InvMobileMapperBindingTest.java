package com.erp.inventory.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("手机端库存聚合 Mapper 绑定")
class InvMobileMapperBindingTest
{
    @Test
    @DisplayName("手机端 options 和工作台统计查询都有 XML 绑定")
    void shouldBindMobileOptionAndWorkbenchStatements() throws Exception
    {
        Class<?> mapperType = Class.forName("com.erp.inventory.mapper.InvMobileMapper");
        Configuration configuration = new Configuration();
        parseMapper(configuration, "mapper/inventory/InvMobileMapper.xml");

        assertMapped(configuration, mapperType, "selectProductOptions");
        assertMapped(configuration, mapperType, "selectCustomerOptions");
        assertMapped(configuration, mapperType, "selectSupplierOptions");
        assertMapped(configuration, mapperType, "selectPendingTransferApprovalTasks");
    }

    @Test
    @DisplayName("手机端聚合 SQL 必须使用当前组织 scope")
    void mobileSqlShouldBeScopedBySelectedDept() throws Exception
    {
        String mapperXml = resourceText("mapper/inventory/InvMobileMapper.xml");

        assertThat(mapperXml).contains("params.scopeDeptIds");
        assertThat(mapperXml).contains("find_in_set");
        assertThat(mapperXml).contains("selectPendingTransferApprovalTasks");
        assertThat(mapperXml).contains("inv_transfer_approval_task");
        assertThat(mapperXml).contains("t.status = 'pending'");
        assertThat(mapperXml).contains("o.status = 'submitted'");
    }

    @Test
    @DisplayName("手机端调拨审批待办只暴露当前审批节点任务")
    void transferApprovalTodoSqlShouldOnlyExposeCurrentNodeTasks() throws Exception
    {
        String mapperXml = resourceText("mapper/inventory/InvMobileMapper.xml");
        String approvalTodoSql = statementXml(mapperXml, "selectPendingTransferApprovalTasks");

        assertThat(approvalTodoSql)
                .contains("inv_transfer_approval_instance")
                .contains("i.status = 'running'")
                .contains("t.node_order = i.current_node_order")
                .contains("and find_in_set(#{candidateUserId}, t.candidate_user_ids)")
                .doesNotContain("candidateUserId != null");
    }

    @Test
    @DisplayName("工作台迁移到统一待办后删除全部旧统计 Mapper 和 SQL")
    void legacyWorkbenchCountStatementsShouldBeRemoved() throws Exception
    {
        Class<?> mapperType = Class.forName("com.erp.inventory.mapper.InvMobileMapper");
        String mapperXml = resourceText("mapper/inventory/InvMobileMapper.xml");
        String[] legacyMethods = {
                "countLowStock", "countPendingPurchaseReceive", "countPendingTransferReceive",
                "countPendingDeliveryNotice", "countPendingTransferDeliver", "countPendingApprovalTasks"
        };

        assertThat(mapperType.getDeclaredMethods())
                .extracting(java.lang.reflect.Method::getName)
                .doesNotContain(legacyMethods);
        assertThat(mapperXml).doesNotContain(legacyMethods);
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

    private static String statementXml(String mapperXml, String statementId)
    {
        int start = mapperXml.indexOf("id=\"" + statementId + "\"");
        assertThat(start).as(statementId + " statement id").isGreaterThanOrEqualTo(0);
        int selectStart = mapperXml.lastIndexOf("<select", start);
        int selectEnd = mapperXml.indexOf("</select>", start);
        assertThat(selectStart).as(statementId + " select start").isGreaterThanOrEqualTo(0);
        assertThat(selectEnd).as(statementId + " select end").isGreaterThan(selectStart);
        return mapperXml.substring(selectStart, selectEnd);
    }

    private static void assertMapped(Configuration configuration, Class<?> mapperType, String methodName)
    {
        assertThat(configuration.hasStatement(mapperType.getName() + "." + methodName))
                .as(mapperType.getSimpleName() + "." + methodName)
                .isTrue();
    }
}
