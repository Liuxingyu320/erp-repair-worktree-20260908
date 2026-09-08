package com.erp.file.drive.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import com.erp.file.drive.config.DriveProperties;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.domain.DriveAuditContext;
import com.erp.file.drive.domain.DriveNode;
import com.erp.file.drive.domain.DrivePurgeClaim;
import com.erp.file.drive.domain.DriveSpace;
import com.erp.file.drive.domain.vo.DriveNodeVo;
import com.erp.file.drive.exception.DriveException;
import com.erp.file.drive.mapper.DriveNodeMapper;
import com.erp.file.drive.mapper.DriveSpaceMapper;
import com.erp.file.drive.metric.DriveMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;

@DisplayName("云盘回收站服务")
class DriveTrashServiceTest
{
    private DriveNodeMapper nodeMapper;
    private DriveSpaceMapper spaceMapper;
    private DriveSpaceService spaceService;
    private DriveNodeService nodeService;
    private DriveTrashPersistence persistence;
    private DriveTrashPurgeWorker worker;
    private TaskExecutor executor;
    private DriveOperationLogService logService;
    private DriveMetrics metrics;
    private DriveTrashService service;
    private DriveActor actor;
    private DriveSpace space;

    @BeforeEach
    void setUp()
    {
        nodeMapper = mock(DriveNodeMapper.class);
        spaceMapper = mock(DriveSpaceMapper.class);
        spaceService = mock(DriveSpaceService.class);
        nodeService = mock(DriveNodeService.class);
        persistence = mock(DriveTrashPersistence.class);
        worker = mock(DriveTrashPurgeWorker.class);
        executor = mock(TaskExecutor.class);
        logService = mock(DriveOperationLogService.class);
        metrics = mock(DriveMetrics.class);
        DriveProperties properties = new DriveProperties();
        properties.setTrashRetentionDays(30);
        service = new DriveTrashService(nodeMapper, spaceMapper, spaceService, nodeService,
                new DriveNamePolicy(), properties, persistence, worker, executor, logService,
                metrics);
        actor = new DriveActor(20L, 8L, "财务部", "alice",
                Set.of(DriveConstants.PERMISSION_ACCESS), false);
        space = personalSpace();
    }

    @Test
    @DisplayName("陈旧版本删除不更新子树且额度保持不变")
    void shouldRejectStaleTrashBeforeSubtreeUpdate()
    {
        DriveNode root = folder(8L, 4L, 0L, "月报");
        root.setVersion(3);
        when(nodeMapper.selectByIdForUpdate(8L)).thenReturn(root);
        when(spaceService.requireCleanupSpace(4L, actor)).thenReturn(space);

        assertCode(() -> service.trash(8L, 2, actor),
                DriveErrorCodes.DRIVE_CONCURRENT_MODIFICATION);

        verify(nodeMapper, never()).trashActiveSubtree(any(), any(), anyString(),
                any(), anyInt(), anyString());
        verifyNoInteractions(persistence);
    }

    @Test
    @DisplayName("删除文件夹将完整活动子树标记为同一回收批次")
    void shouldTrashWholeActiveSubtreeWithoutReleasingQuota()
    {
        DriveNode root = folder(8L, 4L, 6L, "月报");
        root.setAncestors("0,6");
        root.setVersion(3);
        when(nodeMapper.selectByIdForUpdate(8L)).thenReturn(root);
        when(spaceService.requireCleanupSpace(4L, actor)).thenReturn(space);
        when(nodeMapper.trashActiveSubtree(
                4L, 8L, "0,6,8", 20L, 30, "alice")).thenReturn(3);

        service.trash(8L, 3, actor);

        verify(nodeMapper).trashActiveSubtree(
                4L, 8L, "0,6,8", 20L, 30, "alice");
        verifyNoInteractions(persistence);
        verify(logService).success(DriveConstants.ACTION_TRASH, actor, root,
                "月报", "count=3");
    }

    @Test
    @DisplayName("回收站列表只消费根批次并保留三个处理状态")
    void shouldListOnlyTrashRootsWithLifecycleStatuses()
    {
        DriveNode trashed = folder(8L, 4L, 0L, "待恢复");
        trashed.setStatus(DriveConstants.STATUS_TRASHED);
        trashed.setActiveFlag(null);
        trashed.setTrashRootId(8L);
        DriveNode purging = folder(9L, 4L, 0L, "处理中");
        purging.setStatus(DriveConstants.STATUS_PURGING);
        purging.setActiveFlag(null);
        purging.setTrashRootId(9L);
        DriveNode failed = folder(10L, 4L, 0L, "清理失败");
        failed.setStatus(DriveConstants.STATUS_PURGE_FAILED);
        failed.setActiveFlag(null);
        failed.setTrashRootId(10L);
        when(spaceService.requireCleanupSpace(4L, actor)).thenReturn(space);
        when(nodeMapper.selectTrashRoots(4L)).thenReturn(List.of(trashed, purging, failed));
        when(nodeService.toVo(trashed, actor)).thenReturn(nodeVo(8L, DriveConstants.STATUS_TRASHED));
        when(nodeService.toVo(purging, actor)).thenReturn(nodeVo(9L, DriveConstants.STATUS_PURGING));
        when(nodeService.toVo(failed, actor)).thenReturn(nodeVo(10L, DriveConstants.STATUS_PURGE_FAILED));

        List<DriveNodeVo> result = service.listTrash(4L, actor);

        assertThat(result).extracting(DriveNodeVo::status).containsExactly(
                DriveConstants.STATUS_TRASHED,
                DriveConstants.STATUS_PURGING,
                DriveConstants.STATUS_PURGE_FAILED);
        verify(nodeMapper).selectTrashRoots(4L);
    }

    @Test
    @DisplayName("原目录丢失时整批恢复到根目录并自动解决重名")
    void shouldRestoreWholeBatchToRootWithCollisionSuffix()
    {
        DriveNode root = trashedFolder(8L, 4L, 99L, "月报");
        root.setAncestors("0,99");
        DriveNode child = trashedFile(9L, 4L, 8L, "预算.pdf", 12L);
        child.setAncestors("0,99,8");
        when(nodeMapper.selectTrashBatchForUpdate(8L)).thenReturn(List.of(root, child));
        when(spaceService.requireWritableSpace(4L, actor)).thenReturn(space);
        when(nodeMapper.selectByIdForUpdate(99L)).thenReturn(null);
        when(spaceMapper.selectByIdForUpdate(4L)).thenReturn(space);
        when(nodeService.availableFileName(4L, 0L, "月报")).thenReturn("月报 (1)");
        when(nodeMapper.restoreTrashBatch(8L, 8L, 0L, "0",
                "0,99,8", "0,8", "月报 (1)", "月报 (1)", "alice"))
                .thenReturn(2);
        DriveNodeVo restored = nodeVo(8L, DriveConstants.STATUS_ACTIVE);
        when(nodeService.toVo(root, actor)).thenReturn(restored);

        DriveNodeVo result = service.restore(8L, actor);

        assertThat(result).isSameAs(restored);
        verify(spaceMapper).selectByIdForUpdate(4L);
        verify(nodeMapper).restoreTrashBatch(8L, 8L, 0L, "0",
                "0,99,8", "0,8", "月报 (1)", "月报 (1)", "alice");
        verify(logService).success(DriveConstants.ACTION_RESTORE, actor, root,
                "月报", "月报 (1);parentId=0;count=2");
    }

    @Test
    @DisplayName("清理已开始的批次不允许恢复")
    void shouldRejectRestoreAfterPurgeStarted()
    {
        DriveNode root = trashedFolder(8L, 4L, 0L, "月报");
        root.setStatus(DriveConstants.STATUS_PURGING);
        when(nodeMapper.selectTrashBatchForUpdate(8L)).thenReturn(List.of(root));

        assertCode(() -> service.restore(8L, actor),
                DriveErrorCodes.DRIVE_CONCURRENT_MODIFICATION);

        verify(nodeMapper, never()).restoreTrashBatch(any(), any(), any(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("手工清理在提交任务后立即返回且不在请求线程删除对象")
    void shouldReturnPurgeClaimBeforeWorkerRuns()
    {
        DriveNode root = trashedFolder(8L, 4L, 0L, "月报");
        when(nodeMapper.selectById(8L)).thenReturn(root);
        when(spaceService.requireCleanupSpace(4L, actor)).thenReturn(space);
        DriveAuditContext context = context();
        when(logService.captureContext(actor)).thenReturn(context);
        DrivePurgeClaim claim = claim(context, DriveConstants.ACTION_PURGE);
        when(persistence.claim(eq(8L), any(Date.class), eq(context),
                eq(DriveConstants.ACTION_PURGE)))
                .thenReturn(claim);
        AtomicReference<Runnable> submitted = new AtomicReference<>();
        doAnswer(invocation -> {
            submitted.set(invocation.getArgument(0));
            return null;
        }).when(executor).execute(any(Runnable.class));

        DrivePurgeClaim result = service.requestPurge(8L, actor);

        assertThat(result).isSameAs(claim);
        verifyNoInteractions(worker);
        assertThat(submitted.get()).isNotNull();
        submitted.get().run();
        verify(worker).execute(claim);
    }

    @Test
    @DisplayName("执行器拒绝任务时立即标记失败并返回安全错误")
    void shouldMarkClaimFailedWhenExecutorRejects()
    {
        DriveNode root = trashedFolder(8L, 4L, 0L, "月报");
        when(nodeMapper.selectById(8L)).thenReturn(root);
        when(spaceService.requireCleanupSpace(4L, actor)).thenReturn(space);
        DriveAuditContext context = context();
        when(logService.captureContext(actor)).thenReturn(context);
        DrivePurgeClaim claim = claim(context, DriveConstants.ACTION_PURGE);
        when(persistence.claim(eq(8L), any(Date.class), eq(context),
                eq(DriveConstants.ACTION_PURGE)))
                .thenReturn(claim);
        doThrow(new TaskRejectedException("queue full")).when(executor)
                .execute(any(Runnable.class));

        assertCode(() -> service.requestPurge(8L, actor),
                DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE);

        verify(persistence).markFailed(claim);
        verify(logService).failure(DriveConstants.ACTION_PURGE, context,
                claim.auditNode(), DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE,
                null, "count=2");
        verify(metrics).recordCleanupFailure("executor_rejected");
    }

    @Test
    @DisplayName("到期清理使用系统审计上下文且不经过终端用户授权")
    void shouldDispatchExpiredWithSystemAuditContext()
    {
        DriveNode root = trashedFolder(8L, 4L, 0L, "月报");
        root.setStatus(DriveConstants.STATUS_PURGE_FAILED);
        when(nodeMapper.selectExpiredTrashRoots(
                any(Date.class), any(Date.class), eq(100))).thenReturn(List.of(root));
        when(persistence.claim(eq(8L), any(Date.class),
                any(DriveAuditContext.class), eq(DriveConstants.ACTION_CLEANUP)))
                .thenAnswer(invocation -> claim(invocation.getArgument(2),
                        DriveConstants.ACTION_CLEANUP));

        int submitted = service.dispatchExpired(999);

        assertThat(submitted).isEqualTo(1);
        ArgumentCaptor<DriveAuditContext> context =
                ArgumentCaptor.forClass(DriveAuditContext.class);
        verify(persistence).claim(eq(8L), any(Date.class), context.capture(),
                eq(DriveConstants.ACTION_CLEANUP));
        assertThat(context.getValue().operatorUserId()).isZero();
        assertThat(context.getValue().operatorName()).isEqualTo("system");
        assertThat(context.getValue().requestId()).startsWith("cleanup-8-");
        verifyNoInteractions(spaceService);
    }

    @Test
    @DisplayName("手工清理的元数据异常不能把 SQL 或物理字段泄露给客户端")
    void shouldTranslatePurgeMetadataQueryFailure()
    {
        when(nodeMapper.selectById(8L)).thenThrow(new IllegalStateException(
                "select storage_key from drive_node where node_id=8"));

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.requestPurge(8L, actor));

        assertThat(thrown).isInstanceOf(DriveException.class)
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE);
        assertThat(thrown.getMessage()).doesNotContain("select", "storage_key");
    }

    @Test
    @DisplayName("定时认领失败写入 CLEANUP 安全失败审计")
    void shouldAuditScheduledClaimFailure()
    {
        DriveNode root = trashedFolder(8L, 4L, 0L, "月报");
        when(nodeMapper.selectExpiredTrashRoots(
                any(Date.class), any(Date.class), eq(100))).thenReturn(List.of(root));
        when(persistence.claim(eq(8L), any(Date.class),
                any(DriveAuditContext.class), eq(DriveConstants.ACTION_CLEANUP)))
                .thenThrow(new DriveException(
                        DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE, "unavailable"));

        assertThat(service.dispatchExpired(100)).isZero();

        verify(logService).failure(eq(DriveConstants.ACTION_CLEANUP),
                any(DriveAuditContext.class), eq(root),
                eq(DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE), eq(null), eq(null));
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
            String code)
    {
        assertThatThrownBy(callable).isInstanceOf(DriveException.class)
                .extracting("businessCode").isEqualTo(code);
    }

    private static DrivePurgeClaim claim(DriveAuditContext context, String action)
    {
        return new DrivePurgeClaim(8L, 4L, 6, 2, 12L,
                List.of("2026/07/private.pdf"), "月报", context, action);
    }

    private static DriveAuditContext context()
    {
        return new DriveAuditContext(20L, 8L, "alice",
                "request-1", "127.0.0.1", "test");
    }

    private static DriveSpace personalSpace()
    {
        DriveSpace value = new DriveSpace();
        value.setSpaceId(4L);
        value.setSpaceType(DriveConstants.SPACE_PERSONAL);
        value.setOwnerUserId(20L);
        value.setStatus(DriveConstants.STATUS_ACTIVE);
        return value;
    }

    private static DriveNode trashedFolder(Long id, Long spaceId, Long originalParent, String name)
    {
        DriveNode node = folder(id, spaceId, originalParent, name);
        node.setStatus(DriveConstants.STATUS_TRASHED);
        node.setActiveFlag(null);
        node.setOriginalParentId(originalParent);
        node.setTrashRootId(id);
        node.setVersion(5);
        return node;
    }

    private static DriveNode trashedFile(Long id, Long spaceId, Long parentId,
            String name, long size)
    {
        DriveNode node = file(id, spaceId, parentId, name, size);
        node.setStatus(DriveConstants.STATUS_TRASHED);
        node.setActiveFlag(null);
        node.setTrashRootId(8L);
        return node;
    }

    private static DriveNode folder(Long id, Long spaceId, Long parentId, String name)
    {
        DriveNode node = baseNode(id, spaceId, parentId, name);
        node.setNodeType(DriveConstants.NODE_FOLDER);
        node.setSizeBytes(0L);
        return node;
    }

    private static DriveNode file(Long id, Long spaceId, Long parentId, String name, long size)
    {
        DriveNode node = baseNode(id, spaceId, parentId, name);
        node.setNodeType(DriveConstants.NODE_FILE);
        node.setExtension("pdf");
        node.setStorageKey("2026/07/private.pdf");
        node.setSizeBytes(size);
        return node;
    }

    private static DriveNode baseNode(Long id, Long spaceId, Long parentId, String name)
    {
        DriveNode node = new DriveNode();
        node.setNodeId(id);
        node.setSpaceId(spaceId);
        node.setParentId(parentId);
        node.setAncestors("0");
        node.setNodeName(name);
        node.setNormalizedName(name.toLowerCase());
        node.setStatus(DriveConstants.STATUS_ACTIVE);
        node.setActiveFlag(1);
        node.setVersion(0);
        node.setUpdateTime(new Date());
        return node;
    }

    private static DriveNodeVo nodeVo(Long id, String status)
    {
        return new DriveNodeVo(id, 4L, "我的文件", 0L,
                DriveConstants.NODE_FOLDER, "月报", null, null, 0L,
                status, 6, "alice", null, "alice", null,
                "/月报", List.of(), List.of(), true, true, false);
    }
}
