package com.erp.inventory.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.inventory.domain.InvDocumentStatusLog;

@DisplayName("库存单据状态审计日志 Mapper 绑定")
class InvDocumentStatusLogMapperBindingTest
{
    @Test
    @DisplayName("列表查询绑定审计表并带组织和时间过滤")
    void shouldBindListQueryWithAuditFilters() throws Exception
    {
        Configuration configuration = new Configuration();
        configuration.getTypeAliasRegistry().registerAlias("InvDocumentStatusLog", InvDocumentStatusLog.class);

        parseMapper(configuration, "mapper/inventory/InvDocumentStatusLogMapper.xml");

        assertThat(configuration.hasStatement(InvDocumentStatusLogMapper.class.getName()
                + ".selectInvDocumentStatusLogList")).isTrue();

        String mapperXml = resourceText("mapper/inventory/InvDocumentStatusLogMapper.xml");
        assertThat(mapperXml)
                .contains(
                        "from inv_document_status_log l",
                        "and l.document_type = #{documentType}",
                        "and l.document_id = #{documentId}",
                        "and l.shop_dept_id = #{shopDeptId}",
                        "params.scopeDeptIds",
                        "and l.operate_time &gt;= #{params.beginOperateTime}",
                        "and l.operate_time &lt;= #{params.endOperateTime}",
                        "order by l.operate_time desc, l.log_id desc");
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
}
