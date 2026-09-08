package com.erp.inventory.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("盘点审批 Mapper 绑定")
class StockCheckApprovalMapperBindingTest
{
    private static final String INSTANCE_MAPPER =
            "com.erp.inventory.mapper.InvStockCheckApprovalInstanceMapper";
    private static final String TASK_MAPPER =
            "com.erp.inventory.mapper.InvStockCheckApprovalTaskMapper";
    private static final String CANDIDATE_MAPPER =
            "com.erp.inventory.mapper.InvStockCheckApprovalCandidateMapper";
    private static final String CHECK_MAPPER =
            "com.erp.inventory.mapper.InvStockCheckMapper";
    private static final String DETAIL_MAPPER =
            "com.erp.inventory.mapper.InvStockCheckDetailMapper";

    @Test
    @DisplayName("盘点审批实例任务和候选人都有完整 XML 绑定")
    void shouldBindStockCheckApprovalPersistence() throws Exception
    {
        Configuration configuration = new Configuration();
        registerAlias(configuration, "InvStockCheckApprovalInstance",
                "com.erp.inventory.domain.InvStockCheckApprovalInstance");
        registerAlias(configuration, "InvStockCheckApprovalTask",
                "com.erp.inventory.domain.InvStockCheckApprovalTask");

        parseMapper(configuration, "mapper/inventory/InvStockCheckApprovalInstanceMapper.xml");
        parseMapper(configuration, "mapper/inventory/InvStockCheckApprovalTaskMapper.xml");
        parseMapper(configuration, "mapper/inventory/InvStockCheckApprovalCandidateMapper.xml");

        assertMapped(configuration, INSTANCE_MAPPER, "insertInstance");
        assertMapped(configuration, INSTANCE_MAPPER, "selectByIdForUpdate");
        assertMapped(configuration, INSTANCE_MAPPER, "selectByCheckId");
        assertMapped(configuration, TASK_MAPPER, "insertTask");
        assertMapped(configuration, TASK_MAPPER, "selectByInstanceIdForUpdate");
        assertMapped(configuration, CANDIDATE_MAPPER, "selectOperationsDirectorCandidates");
    }

    @Test
    @DisplayName("运营总监候选人同时校验岗位权限和组织授权")
    void shouldResolveCandidatesByPostPermissionAndShopScope() throws Exception
    {
        String resource = "mapper/inventory/InvStockCheckApprovalCandidateMapper.xml";
        assertThat(getClass().getClassLoader().getResource(resource)).isNotNull();
        String xml;
        try (InputStream inputStream = Resources.getResourceAsStream(resource))
        {
            xml = new String(inputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }

        assertThat(xml).contains(
                "p.post_code = 'yyzj'",
                "m.perms = 'inv:stockCheck:approve'",
                "target_dept.dept_id = #{deptId}",
                "find_in_set(scope_dept.dept_id, target_dept.ancestors)");
    }

    @Test
    @DisplayName("盘点主单待办查询和明细快照刷新都有 XML 绑定")
    void shouldBindApprovalStateMachinePersistence() throws Exception
    {
        Configuration configuration = new Configuration();
        registerAlias(configuration, "InvStockCheck", "com.erp.inventory.domain.InvStockCheck");
        registerAlias(configuration, "InvStockCheckDetail",
                "com.erp.inventory.domain.InvStockCheckDetail");
        parseMapper(configuration, "mapper/inventory/InvStockCheckMapper.xml");
        parseMapper(configuration, "mapper/inventory/InvStockCheckDetailMapper.xml");

        assertMapped(configuration, CHECK_MAPPER, "selectInvStockCheckApprovalTodoList");
        assertMapped(configuration, CHECK_MAPPER, "selectCounterCandidates");
        assertMapped(configuration, CHECK_MAPPER, "selectCounterCandidate");
        assertMapped(configuration, DETAIL_MAPPER, "resetSnapshot");
    }

    @Test
    @DisplayName("盘点人候选查询校验账号组织在职状态和盘点提交权限")
    void shouldConstrainCounterCandidatesToExecutableUsers() throws Exception
    {
        String resource = "mapper/inventory/InvStockCheckMapper.xml";
        String xml;
        try (InputStream inputStream = Resources.getResourceAsStream(resource))
        {
            xml = new String(inputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }

        assertThat(xml).contains(
                "selectCounterCandidates",
                "selectCounterCandidate",
                "m.perms = 'inv:stockCheck:submit'",
                "target_dept.dept_id = #{inventoryDeptId}",
                "find_in_set(scope_dept.dept_id, target_dept.ancestors)",
                "profile.employee_status");
    }

    private static void registerAlias(Configuration configuration, String alias, String className)
            throws Exception
    {
        configuration.getTypeAliasRegistry().registerAlias(alias, Class.forName(className));
    }

    private static void parseMapper(Configuration configuration, String resource) throws Exception
    {
        assertThat(StockCheckApprovalMapperBindingTest.class.getClassLoader().getResource(resource))
                .as(resource)
                .isNotNull();
        try (InputStream inputStream = Resources.getResourceAsStream(resource))
        {
            XMLMapperBuilder mapperBuilder = new XMLMapperBuilder(inputStream, configuration, resource,
                    configuration.getSqlFragments());
            mapperBuilder.parse();
        }
    }

    private static void assertMapped(Configuration configuration, String mapperType, String methodName)
    {
        assertThat(configuration.hasStatement(mapperType + "." + methodName))
                .as(mapperType + "." + methodName)
                .isTrue();
    }
}
