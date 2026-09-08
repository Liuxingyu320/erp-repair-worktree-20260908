package com.erp.system.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.session.Configuration;
import com.erp.system.api.domain.SysOperLog;
import com.erp.system.domain.vo.SysOperLogExportVo;
import com.erp.system.domain.vo.SysOperLogListVo;

@DisplayName("操作日志最小化查询")
class SysOperLogMapperSourceTest
{
    private static final String XML_RESOURCE = "mapper/system/SysOperLogMapper.xml";
    private static final String NAMESPACE = "com.erp.system.mapper.SysOperLogMapper";
    private static final Map<String, String> EXPECTED_MAPPINGS = Map.of(
            "oper_id", "operId",
            "title", "title",
            "business_type", "businessType",
            "request_method", "requestMethod",
            "oper_name", "operName",
            "oper_ip", "operIp",
            "status", "status",
            "oper_time", "operTime",
            "cost_time", "costTime");

    @Test
    @DisplayName("列表和导出在关闭驼峰映射时仍显式绑定九个字段")
    void listAndExportShouldUseExplicitResultMaps() throws Exception
    {
        Configuration configuration = configuration();

        assertThat(configuration.isMapUnderscoreToCamelCase()).isFalse();
        assertResultMap(configuration, "selectOperLogSummaryList", "SysOperLogListResult", SysOperLogListVo.class);
        assertResultMap(configuration, "selectOperLogExportList", "SysOperLogExportResult", SysOperLogExportVo.class);

        assertSafeSummarySql(configuration, "selectOperLogSummaryList");
        assertSafeSummarySql(configuration, "selectOperLogExportList");
    }

    private static Configuration configuration() throws Exception
    {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(false);
        configuration.getTypeAliasRegistry().registerAlias("SysOperLog", SysOperLog.class);
        try (InputStream inputStream = Resources.getResourceAsStream(XML_RESOURCE))
        {
            XMLMapperBuilder mapperBuilder = new XMLMapperBuilder(inputStream, configuration, XML_RESOURCE,
                    configuration.getSqlFragments());
            mapperBuilder.parse();
        }
        return configuration;
    }

    private static void assertResultMap(Configuration configuration, String statementId, String resultMapId,
            Class<?> expectedType)
    {
        MappedStatement statement = configuration.getMappedStatement(NAMESPACE + "." + statementId);
        assertThat(statement.getResultMaps()).hasSize(1);
        ResultMap resultMap = statement.getResultMaps().get(0);
        assertThat(resultMap.getId()).isEqualTo(NAMESPACE + "." + resultMapId);
        assertThat(resultMap.getType()).isEqualTo(expectedType);

        Map<String, String> mappings = resultMap.getResultMappings().stream()
                .collect(Collectors.toMap(mapping -> mapping.getColumn(), mapping -> mapping.getProperty()));
        assertThat(mappings).containsExactlyInAnyOrderEntriesOf(EXPECTED_MAPPINGS);
    }

    private static void assertSafeSummarySql(Configuration configuration, String statementId)
    {
        String sql = configuration.getMappedStatement(NAMESPACE + "." + statementId)
                .getBoundSql(new SysOperLog()).getSql()
                .replaceAll("\\s+", " ").trim().toLowerCase();

        assertThat(sql)
                .doesNotContain("oper_param", "json_result", "error_msg")
                .contains("select oper_id, title, business_type, request_method, oper_name, oper_ip, status, oper_time, cost_time from sys_oper_log");
    }
}
