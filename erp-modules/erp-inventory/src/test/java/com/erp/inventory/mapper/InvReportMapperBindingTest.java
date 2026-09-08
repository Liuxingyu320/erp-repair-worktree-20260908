package com.erp.inventory.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("库存报表 Mapper 绑定")
class InvReportMapperBindingTest
{
    @Test
    @DisplayName("报表汇总库存预警和商品候选查询都有 XML 绑定")
    void shouldBindReportStatements() throws Exception
    {
        Class<?> mapperType = Class.forName("com.erp.inventory.mapper.InvReportMapper");
        Configuration configuration = new Configuration();
        parseMapper(configuration, "mapper/inventory/InvReportMapper.xml");

        assertMapped(configuration, mapperType, "selectReportSummary");
        assertMapped(configuration, mapperType, "selectStockWarningList");
        assertMapped(configuration, mapperType, "selectReportProductOptions");
    }

    @Test
    @DisplayName("报表 SQL 按实际收发、退货和库存流水计算经营口径")
    void reportSqlShouldUseScopedExistingInventoryTables() throws Exception
    {
        String mapperXml = resourceText("mapper/inventory/InvReportMapper.xml");

        assertThat(mapperXml).contains("params.scopeDeptIds");
        assertThat(mapperXml).contains("inv_stock");
        assertThat(mapperXml).contains("inv_purchase_order");
        assertThat(mapperXml).contains("inv_purchase_detail");
        assertThat(mapperXml).contains("inv_sales_order");
        assertThat(mapperXml).contains("inv_sales_detail");
        assertThat(mapperXml).contains("inv_purchase_return", "inv_sales_return", "inv_stock_log");
        assertThat(mapperXml).contains("d.received_quantity", "d.delivered_quantity", "d.returned_quantity");
        assertThat(mapperXml).contains("sales_out_cost", "sales_return_cost", "sales_cost");
        assertThat(mapperXml).contains("report_amounts.sales_amount - report_amounts.sales_cost");
        assertThat(mapperXml).doesNotContain("report_base.sales_amount - report_base.purchase_amount");
        assertThat(mapperXml).doesNotContain("coalesce(p.safety_stock_min, 10)");
        assertThat(mapperXml).contains("missing_safety_stock_count", "stockStatus == 'unconfigured'");
        assertThat(mapperXml).contains("params.beginTime", "params.endTime");
        assertThat(mapperXml).contains("stockStatus");

        String warningSql = statementSource(mapperXml, "selectStockWarningList");
        assertThat(warningSql).contains(") as category_full_path", "p.spec", "p.unit",
                "s.last_in_time", "s.last_out_time");
        assertThat(mapperXml).contains(
                "<result property=\"categoryFullPath\" column=\"category_full_path\"/>",
                "<result property=\"spec\" column=\"spec\"/>",
                "<result property=\"unit\" column=\"unit\"/>",
                "<result property=\"lastInTime\" column=\"last_in_time\"/>",
                "<result property=\"lastOutTime\" column=\"last_out_time\"/>");
    }

    @Test
    @DisplayName("报表控制器暴露读取商品候选和独立权限导出端点")
    void controllerShouldExposePermissionedReportEndpoints() throws Exception
    {
        String controllerSource = Files.readString(Paths.get(
                "src/main/java/com/erp/inventory/controller/InvReportController.java"), StandardCharsets.UTF_8);

        assertThat(controllerSource).contains("@RequestMapping(\"/report\")");
        assertThat(controllerSource).contains("@GetMapping(\"/summary\")");
        assertThat(controllerSource).contains("@GetMapping(\"/stock-warning\")");
        assertThat(controllerSource).contains("@GetMapping(\"/product-options\")");
        assertThat(controllerSource).contains("@PostMapping(\"/stock-warning/export\")");
        assertThat(controllerSource).contains("@RequiresPermissions(\"inv:report:list\")");
        assertThat(controllerSource).contains("@RequiresPermissions(\"inv:report:export\")");
        assertThat(controllerSource).contains("no-store, max-age=0");
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

    private static String statementSource(String mapperXml, String statementId)
    {
        String opening = "<select id=\"" + statementId + "\"";
        int start = mapperXml.indexOf(opening);
        int end = mapperXml.indexOf("</select>", start);
        assertThat(start).as(statementId + " opening tag").isGreaterThanOrEqualTo(0);
        assertThat(end).as(statementId + " closing tag").isGreaterThan(start);
        return mapperXml.substring(start, end);
    }

    private static void assertMapped(Configuration configuration, Class<?> mapperType, String methodName)
    {
        assertThat(configuration.hasStatement(mapperType.getName() + "." + methodName))
                .as(mapperType.getSimpleName() + "." + methodName)
                .isTrue();
    }
}
