package com.erp.oa.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.oa.domain.OaSignEvent;
import com.erp.oa.domain.OaSignPackageDocument;

@DisplayName("签约任务管理员硬删除Mapper绑定")
class OaSignTaskHardDeleteMapperBindingTest
{
    @Test
    @DisplayName("所有任务、签约包、证据和导入关联删除语句均可解析")
    void shouldBindTheCompleteHardDeleteCascade() throws Exception
    {
        Configuration configuration = new Configuration();
        configuration.getTypeAliasRegistry().registerAlias(
                "OaSignPackageDocument", OaSignPackageDocument.class);
        configuration.getTypeAliasRegistry().registerAlias("OaSignEvent", OaSignEvent.class);
        for (String resource : List.of(
                "mapper/oa/OaSignTaskMapper.xml",
                "mapper/oa/OaSignPackageMapper.xml",
                "mapper/oa/OaSignPackageDocumentMapper.xml",
                "mapper/oa/OaSignEventMapper.xml",
                "mapper/oa/OaSignFileEvidenceMapper.xml",
                "mapper/oa/OaSignFinalConfirmationMapper.xml",
                "mapper/oa/OaSignNotificationOutboxMapper.xml",
                "mapper/oa/OaSignTaskEventMapper.xml",
                "mapper/oa/OaSignTaskHardDeleteOperationMapper.xml",
                "mapper/oa/OaSignOnboardDataRequestMapper.xml",
                "mapper/oa/OaSignOnboardImportRowMapper.xml",
                "mapper/oa/OaSignOnboardImportBatchMapper.xml",
                "mapper/oa/OaHrRenewalGuardMapper.xml"))
        {
            parseMapper(configuration, resource);
        }

        assertMapped(configuration, OaSignTaskMapper.class,
                "countLifecycleReferences", "deleteReassignmentsByTaskId",
                "deleteOaSignTaskById", "deleteOaSignTaskByIdAndVersion");
        assertMapped(configuration, OaSignPackageMapper.class,
                "selectPackageIdsByTaskId", "countLifecycleReferences", "deleteOaSignPackageById");
        assertMapped(configuration, OaSignPackageDocumentMapper.class, "deleteDocumentsByPackageId");
        assertMapped(configuration, OaSignEventMapper.class, "deleteEventsByPackageId");
        assertMapped(configuration, OaSignFileEvidenceMapper.class, "deleteEvidenceByPackageId");
        assertMapped(configuration, OaSignFinalConfirmationMapper.class,
                "countByPackageId", "deleteConfirmationDocumentsByPackageId", "deleteConfirmationsByPackageId");
        assertMapped(configuration, OaSignNotificationOutboxMapper.class, "deleteByTaskId");
        assertMapped(configuration, OaSignTaskEventMapper.class, "deleteTaskEvents");
        assertMapped(configuration, OaSignTaskHardDeleteOperationMapper.class,
                "register", "lockByRequestId", "claimForProcessing",
                "saveProgress", "markCompleted", "markRetry");
        assertMapped(configuration, OaSignOnboardDataRequestMapper.class, "deleteRequestsByIds");
        assertMapped(configuration, OaSignOnboardImportRowMapper.class,
                "selectBatchIdsByTaskOrPackage", "selectDataRequestIdsByTaskOrPackage",
                "unbindHardDeletedTask");
        assertMapped(configuration, OaSignOnboardImportBatchMapper.class, "refreshAfterHardDelete");
        assertMapped(configuration, OaHrRenewalGuardMapper.class, "releaseByTaskForHardDelete");

        String unbindSql = normalized(configuration, OaSignOnboardImportRowMapper.class,
                "unbindHardDeletedTask");
        assertThat(unbindSql)
                .contains("task_id = null", "package_id = null", "data_request_id = null",
                        "generated_time = null")
                .contains("where task_id = ?", "package_id = ?");
        assertThat(normalized(configuration, OaSignOnboardDataRequestMapper.class,
                "deleteRequestsByIds", Map.of("requestIds", List.of(501L))))
                .contains("delete from oa_sign_onboard_data_request", "where request_id in ( ? )");
        assertThat(normalized(configuration, OaSignNotificationOutboxMapper.class, "deleteByTaskId"))
                .contains("json_extract(payload_json, '$.taskId')");
        assertThat(normalized(configuration, OaHrRenewalGuardMapper.class, "releaseByTaskForHardDelete"))
                .contains("task_id = null", "action_id = null", "and task_id = ?");
        assertThat(normalized(configuration, OaSignTaskMapper.class,
                "deleteOaSignTaskByIdAndVersion"))
                .contains("delete from oa_sign_task", "where task_id = ?", "and version = ?");
        assertThat(normalized(configuration, OaSignTaskHardDeleteOperationMapper.class,
                "register"))
                .contains("insert into oa_sign_task_hard_delete_operation",
                        "on duplicate key update", "replay_count = replay_count + 1")
                .doesNotContain("payload_hash = values(payload_hash)",
                        "administrator_user_id = values(administrator_user_id)");
        assertThat(normalized(configuration, OaSignTaskHardDeleteOperationMapper.class,
                "saveProgress"))
                .contains("status = 'PROCESSING'", "version = ?", "claim_token = ?",
                        "processed_count = ?", "? = processed_count + 1");
    }

    private static void parseMapper(Configuration configuration, String resource) throws Exception
    {
        try (InputStream input = Resources.getResourceAsStream(resource))
        {
            new XMLMapperBuilder(input, configuration, resource,
                    configuration.getSqlFragments()).parse();
        }
    }

    private static void assertMapped(Configuration configuration, Class<?> mapper, String... statementIds)
    {
        for (String statementId : statementIds)
        {
            assertThat(configuration.hasStatement(mapper.getName() + "." + statementId))
                    .as("mapped statement %s.%s", mapper.getName(), statementId)
                    .isTrue();
        }
    }

    private static String normalized(Configuration configuration, Class<?> mapper, String statementId)
    {
        return normalized(configuration, mapper, statementId, new Object());
    }

    private static String normalized(Configuration configuration, Class<?> mapper,
            String statementId, Object parameter)
    {
        return configuration.getMappedStatement(mapper.getName() + "." + statementId)
                .getBoundSql(parameter).getSql().replaceAll("\\s+", " ").trim();
    }
}
