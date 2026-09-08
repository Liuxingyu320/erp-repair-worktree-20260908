package com.erp.oa.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Date;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("签约文件清理台账Mapper绑定")
class OaSignFileCleanupMapperBindingTest
{
    @Test
    @DisplayName("台账入队、乐观锁抢占和恢复查询均可解析")
    void shouldBindDurableCleanupTransitions() throws Exception
    {
        Configuration configuration = new Configuration();
        String resource = "mapper/oa/OaSignFileCleanupMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource))
        {
            new XMLMapperBuilder(input, configuration, resource,
                    configuration.getSqlFragments()).parse();
        }

        for (String statement : new String[] { "insertCleanup", "selectById", "selectDue",
                "claimForProcessing", "markCompleted", "markRetry" })
        {
            assertThat(configuration.hasStatement(
                    OaSignFileCleanupMapper.class.getName() + "." + statement)).isTrue();
        }

        Map<String, Object> parameters = Map.of(
                "cleanupId", 700L,
                "version", 2L,
                "dueTime", new Date(),
                "processingToken", "worker-1",
                "leaseExpiresTime", new Date());
        String claimSql = sql(configuration, "claimForProcessing", parameters);
        assertThat(claimSql)
                .contains("status = 'PROCESSING'", "processing_token = ?",
                        "lease_expires_time = ?", "version = version + 1")
                .contains("cleanup_id = ?", "version = ?")
                .contains("status in ('PENDING', 'RETRY')", "status = 'PROCESSING'")
                .contains("lease_expires_time <= ?");
    }

    private static String sql(Configuration configuration, String statement, Object parameters)
    {
        return configuration.getMappedStatement(
                OaSignFileCleanupMapper.class.getName() + "." + statement)
                .getBoundSql(parameters).getSql().replaceAll("\\s+", " ").trim();
    }
}
