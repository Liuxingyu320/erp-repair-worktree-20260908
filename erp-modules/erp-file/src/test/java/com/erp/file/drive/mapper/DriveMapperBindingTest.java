package com.erp.file.drive.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import com.erp.file.drive.mapper.DriveNodeMapper;
import com.erp.file.drive.mapper.DriveCapacityMapper;
import com.erp.file.drive.mapper.DriveOrganizationMapper;
import com.erp.file.drive.mapper.DriveOperationLogMapper;
import com.erp.file.drive.mapper.DrivePersonalQuotaPolicyMapper;
import com.erp.file.drive.mapper.DriveSpaceMapper;
import com.erp.file.drive.mapper.DriveUploadReservationMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("云盘 Mapper 绑定")
class DriveMapperBindingTest
{
    @Test
    @DisplayName("空间、节点、操作日志和实时身份查询均有 XML 绑定")
    void shouldBindFoundationStatements() throws Exception
    {
        Configuration configuration = new Configuration();
        parseMapper(configuration, "mapper/drive/DriveSpaceMapper.xml");
        parseMapper(configuration, "mapper/drive/DriveNodeMapper.xml");
        parseMapper(configuration, "mapper/drive/DriveOperationLogMapper.xml");
        parseMapper(configuration, "mapper/drive/DriveIdentityMapper.xml");
        parseMapper(configuration, "mapper/drive/DrivePersonalQuotaPolicyMapper.xml");
        parseMapper(configuration, "mapper/drive/DriveOrganizationMapper.xml");
        parseMapper(configuration, "mapper/drive/DriveCapacityMapper.xml");
        parseMapper(configuration, "mapper/drive/DriveUploadReservationMapper.xml");

        assertMapped(configuration, DriveSpaceMapper.class, "selectById");
        assertMapped(configuration, DriveSpaceMapper.class, "selectByKey");
        assertMapped(configuration, DriveSpaceMapper.class, "insertIgnore");
        assertMapped(configuration, DriveSpaceMapper.class, "reserveQuota");
        assertMapped(configuration, DriveSpaceMapper.class, "releaseQuota");
        assertMapped(configuration, DriveSpaceMapper.class, "updateQuota");
        assertMapped(configuration, DriveNodeMapper.class, "selectById");
        assertMapped(configuration, DriveNodeMapper.class, "insertNode");
        assertMapped(configuration, DriveOperationLogMapper.class, "insertOperation");
        assertMapped(configuration, DriveIdentityMapper.class, "selectCurrentIdentity");
        assertMapped(configuration, DriveNodeMapper.class, "selectActiveChildren");
        assertMapped(configuration, DriveNodeMapper.class, "selectActiveSearch");
        assertMapped(configuration, DriveNodeMapper.class, "selectActiveById");
        assertMapped(configuration, DriveNodeMapper.class, "selectByIdForUpdate");
        assertMapped(configuration, DriveNodeMapper.class, "existsActiveName");
        assertMapped(configuration, DriveNodeMapper.class, "updateName");
        assertMapped(configuration, DriveNodeMapper.class, "updateParent");
        assertMapped(configuration, DriveNodeMapper.class, "updateDescendantAncestors");
        assertMapped(configuration, DriveNodeMapper.class, "selectPathNodes");
        assertMapped(configuration, DriveOperationLogMapper.class, "selectRecentNodes");
        assertMapped(configuration, DriveNodeMapper.class, "selectTrashRoots");
        assertMapped(configuration, DriveNodeMapper.class, "selectTrashBatch");
        assertMapped(configuration, DriveNodeMapper.class, "selectTrashBatchForUpdate");
        assertMapped(configuration, DriveNodeMapper.class, "trashActiveSubtree");
        assertMapped(configuration, DriveNodeMapper.class, "restoreTrashBatch");
        assertMapped(configuration, DriveNodeMapper.class, "claimTrashRoot");
        assertMapped(configuration, DriveNodeMapper.class, "claimTrashDescendants");
        assertMapped(configuration, DriveNodeMapper.class, "selectClaimRootForUpdate");
        assertMapped(configuration, DriveNodeMapper.class, "markPurgeFailedRoot");
        assertMapped(configuration, DriveNodeMapper.class, "markPurgeFailedDescendants");
        assertMapped(configuration, DriveNodeMapper.class, "deletePurgeBatch");
        assertMapped(configuration, DriveNodeMapper.class, "selectExpiredTrashRoots");
        assertMapped(configuration, DriveSpaceMapper.class, "selectByIdForUpdate");
        assertMapped(configuration, DriveSpaceMapper.class, "selectByDeptIds");
        assertMapped(configuration, DriveSpaceMapper.class, "selectByType");
        assertMapped(configuration, DriveSpaceMapper.class, "updateEffectiveQuota");
        assertMapped(configuration, DriveSpaceMapper.class, "updateOrganizationMetadata");
        assertMapped(configuration, DrivePersonalQuotaPolicyMapper.class, "selectGlobalPolicy");
        assertMapped(configuration, DrivePersonalQuotaPolicyMapper.class, "selectActivePostPolicies");
        assertMapped(configuration, DrivePersonalQuotaPolicyMapper.class, "selectActiveUserContexts");
        assertMapped(configuration, DrivePersonalQuotaPolicyMapper.class, "updatePolicy");
        assertMapped(configuration, DriveOrganizationMapper.class, "selectAllOrganizations");
        assertMapped(configuration, DriveOrganizationMapper.class, "selectRoleScopes");
        assertMapped(configuration, DriveOrganizationMapper.class, "selectConfigForUpdate");
        assertMapped(configuration, DriveOrganizationMapper.class, "updateConfig");
        assertMapped(configuration, DriveCapacityMapper.class, "selectConfigForUpdate");
        assertMapped(configuration, DriveCapacityMapper.class, "updateConfig");
        assertMapped(configuration, DriveUploadReservationMapper.class, "selectForUpdate");
        assertMapped(configuration, DriveUploadReservationMapper.class, "sumPendingBytes");
        assertMapped(configuration, DriveUploadReservationMapper.class, "claimCleanup");
        assertMapped(configuration, DriveUploadReservationMapper.class, "markCleanupFailed");
        assertMapped(configuration, DriveUploadReservationMapper.class, "deleteExpected");
    }

    @Test
    @DisplayName("额度更新应原子校验上限并使用乐观锁")
    void shouldBindAtomicQuotaUpdates() throws Exception
    {
        String xml = normalized(resourceText("mapper/drive/DriveSpaceMapper.xml"));

        assertThat(xml).contains(
                "insert ignore into drive_space",
                "used_bytes = used_bytes + #{bytes}",
                "used_bytes + #{bytes} &lt;= quota_bytes",
                "used_bytes = used_bytes - #{bytes}",
                "used_bytes &gt;= #{bytes}",
                "used_bytes &lt;= #{quotaBytes}",
                "quota_source_type = #{quotaSourceType}",
                "status in ('ACTIVE', 'READ_ONLY')",
                "version = #{version}",
                "version = version + 1");
    }

    @Test
    @DisplayName("身份查询只接受启用用户及经验证的启用部门")
    void shouldReadLiveValidatedIdentity() throws Exception
    {
        String xml = normalized(resourceText("mapper/drive/DriveIdentityMapper.xml"));

        assertThat(xml).contains(
                "d.dept_id as deptId",
                "d.dept_name as deptName",
                "d.parent_id as parentId",
                "d.ancestors as ancestors",
                "d.dept_type as deptType",
                "d.status = '0'",
                "d.del_flag = '0'",
                "u.status = '0'",
                "u.del_flag = '0'");
        assertThat(xml).doesNotContain("u.dept_id as deptId");
    }

    @Test
    @DisplayName("节点列表和全空间搜索只使用固定排序片段")
    void shouldUseFixedSortFragmentsAndEscapedSearch() throws Exception
    {
        String xml = normalized(resourceText("mapper/drive/DriveNodeMapper.xml"));

        assertThat(xml).contains(
                "node_type = 'FOLDER' desc",
                "normalized_name like concat('%', #{keywordPattern}, '%') escape '!'",
                "<choose>",
                "<foreach collection=\"nodeIds\"");
        assertThat(xml).doesNotContain("${");
    }

    @Test
    @DisplayName("最近使用查询保持 MySQL 5.7 兼容并绑定限制数量")
    void shouldUseGroupedRecentQueryWithoutWindowFunctions() throws Exception
    {
        String xml = normalized(resourceText("mapper/drive/DriveOperationLogMapper.xml"));

        assertThat(xml).contains(
                "max(operation_id)",
                "operator_user_id = #{userId}",
                "n.active_flag = 1",
                "n.status, n.active_flag, n.version",
                "property=\"activeFlag\" column=\"active_flag\"",
                "limit #{limit}");
        assertThat(xml).doesNotContain("row_number", "over (", "${limit}");
    }

    @Test
    @DisplayName("回收批次 SQL 使用精确子树、状态认领和绑定批次上限")
    void shouldBindAtomicTrashBatchTransitions() throws Exception
    {
        String xml = normalized(resourceText("mapper/drive/DriveNodeMapper.xml"));

        assertThat(xml).contains(
                "active_flag = null",
                "original_parent_id = parent_id",
                "trash_root_id = #{rootNodeId}",
                "ancestors like concat(#{rootPath}, ',%')",
                "status = 'PURGING'",
                "version = #{expectedVersion}",
                "update_time &lt; #{staleBefore}",
                "status = 'PURGE_FAILED'",
                "delete from drive_node",
                "order by purge_after asc, trash_root_id asc",
                "limit #{limit}");
        assertThat(xml).doesNotContain("${limit}");
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

    private static String normalized(String value)
    {
        return value.replaceAll("\\s+", " ").trim();
    }

    private static void assertMapped(Configuration configuration, Class<?> mapper, String id)
    {
        assertThat(configuration.hasStatement(mapper.getName() + "." + id))
                .as(mapper.getSimpleName() + "." + id)
                .isTrue();
    }
}
