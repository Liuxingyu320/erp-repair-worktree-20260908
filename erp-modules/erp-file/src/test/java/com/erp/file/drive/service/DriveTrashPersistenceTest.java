package com.erp.file.drive.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.domain.DriveAuditContext;
import com.erp.file.drive.domain.DriveNode;
import com.erp.file.drive.domain.DrivePurgeClaim;
import com.erp.file.drive.exception.DriveException;
import com.erp.file.drive.mapper.DriveNodeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

@DisplayName("云盘回收批次持久化")
class DriveTrashPersistenceTest
{
    private DriveNodeMapper nodeMapper;
    private DriveQuotaService quotaService;
    private DriveTrashPersistence persistence;
    private DriveAuditContext context;

    @BeforeEach
    void setUp()
    {
        nodeMapper = mock(DriveNodeMapper.class);
        quotaService = mock(DriveQuotaService.class);
        persistence = new DriveTrashPersistence(nodeMapper, quotaService);
        context = new DriveAuditContext(20L, 8L, "alice",
                "request-1", "127.0.0.1", "test");
    }

    @Test
    @DisplayName("认领事务锁定整批并原子切换到 PURGING")
    void shouldClaimWholeBatchAndCaptureImmutableWork()
    {
        DriveNode root = root(DriveConstants.STATUS_TRASHED, 5, new Date());
        DriveNode file = file(9L, 12L, "2026/07/private.pdf");
        when(nodeMapper.selectTrashBatchForUpdate(8L)).thenReturn(List.of(root, file));
        when(nodeMapper.claimTrashRoot(eq(8L), eq(5),
                eq(DriveConstants.STATUS_TRASHED), any(Date.class), eq("alice")))
                .thenReturn(1);
        when(nodeMapper.claimTrashDescendants(eq(8L), eq(8L),
                eq(DriveConstants.STATUS_TRASHED), any(Date.class), eq("alice")))
                .thenReturn(1);

        DrivePurgeClaim claim = persistence.claim(
                8L, new Date(), context, DriveConstants.ACTION_PURGE);

        assertThat(claim.claimVersion()).isEqualTo(6);
        assertThat(claim.rowCount()).isEqualTo(2);
        assertThat(claim.totalFileBytes()).isEqualTo(12L);
        assertThat(claim.storageKeys()).containsExactly("2026/07/private.pdf");
        assertThat(claim.toString()).doesNotContain("2026/07/private.pdf");
    }

    @Test
    @DisplayName("相同根版本只能被一个工作者认领")
    void shouldReturnNullWhenCompareAndSetLoses()
    {
        DriveNode root = root(DriveConstants.STATUS_TRASHED, 5, new Date());
        when(nodeMapper.selectTrashBatchForUpdate(8L)).thenReturn(List.of(root));
        when(nodeMapper.claimTrashRoot(eq(8L), eq(5),
                eq(DriveConstants.STATUS_TRASHED), any(Date.class), eq("alice")))
                .thenReturn(0);

        DrivePurgeClaim claim = persistence.claim(
                8L, new Date(), context, DriveConstants.ACTION_PURGE);

        assertThat(claim).isNull();
        verify(nodeMapper, never()).claimTrashDescendants(
                any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("部分后代认领会失败并由事务回滚根认领")
    void shouldRejectPartialDescendantClaim()
    {
        DriveNode root = root(DriveConstants.STATUS_TRASHED, 5, new Date());
        DriveNode file = file(9L, 12L, "2026/07/private.pdf");
        when(nodeMapper.selectTrashBatchForUpdate(8L)).thenReturn(List.of(root, file));
        when(nodeMapper.claimTrashRoot(eq(8L), eq(5),
                eq(DriveConstants.STATUS_TRASHED), any(Date.class), eq("alice")))
                .thenReturn(1);
        when(nodeMapper.claimTrashDescendants(eq(8L), eq(8L),
                eq(DriveConstants.STATUS_TRASHED), any(Date.class), eq("alice")))
                .thenReturn(0);

        assertThatThrownBy(() -> persistence.claim(
                8L, new Date(), context, DriveConstants.ACTION_PURGE))
                .isInstanceOf(DriveException.class)
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_CONCURRENT_MODIFICATION);
    }

    @Test
    @DisplayName("失败批次和超过十五分钟的 PURGING 批次均可重领")
    void shouldReclaimFailedAndStalePurgingBatches()
    {
        Date staleBefore = new Date(System.currentTimeMillis() - 15 * 60_000L);
        DriveNode failed = root(DriveConstants.STATUS_PURGE_FAILED, 6, new Date());
        when(nodeMapper.selectTrashBatchForUpdate(8L)).thenReturn(List.of(failed));
        when(nodeMapper.claimTrashRoot(8L, 6, DriveConstants.STATUS_PURGE_FAILED,
                staleBefore, "alice")).thenReturn(1);

        assertThat(persistence.claim(8L, staleBefore, context,
                DriveConstants.ACTION_PURGE)).isNotNull();

        DriveNode stale = root(DriveConstants.STATUS_PURGING, 7,
                new Date(staleBefore.getTime() - 1));
        when(nodeMapper.selectTrashBatchForUpdate(8L)).thenReturn(List.of(stale));
        when(nodeMapper.claimTrashRoot(8L, 7, DriveConstants.STATUS_PURGING,
                staleBefore, "alice")).thenReturn(1);
        assertThat(persistence.claim(8L, staleBefore, context,
                DriveConstants.ACTION_CLEANUP)).isNotNull();
    }

    @Test
    @DisplayName("未过期的 PURGING 批次不能再次认领")
    void shouldNotReclaimFreshPurgingBatch()
    {
        Date staleBefore = new Date(System.currentTimeMillis() - 15 * 60_000L);
        DriveNode fresh = root(DriveConstants.STATUS_PURGING, 7, new Date());
        when(nodeMapper.selectTrashBatchForUpdate(8L)).thenReturn(List.of(fresh));

        assertThat(persistence.claim(8L, staleBefore, context,
                DriveConstants.ACTION_CLEANUP)).isNull();

        verify(nodeMapper, never()).claimTrashRoot(
                any(), anyInt(), any(), any(), any());
    }

    @Test
    @DisplayName("成功完成先核对认领版本、删除完整批次并只释放一次额度")
    void shouldFinalizeClaimAndReleaseQuotaOnce()
    {
        DrivePurgeClaim claim = claim();
        DriveNode root = root(DriveConstants.STATUS_PURGING, 6, new Date());
        when(nodeMapper.selectClaimRootForUpdate(8L, 6)).thenReturn(root);
        when(nodeMapper.deletePurgeBatch(8L)).thenReturn(2);

        persistence.finalizeClaim(claim);

        verify(nodeMapper).deletePurgeBatch(8L);
        verify(quotaService).release(4L, 12L);
    }

    @Test
    @DisplayName("批次删除数量不一致时事务失败且不释放额度")
    void shouldRejectPartialMetadataDelete()
    {
        DrivePurgeClaim claim = claim();
        when(nodeMapper.selectClaimRootForUpdate(8L, 6))
                .thenReturn(root(DriveConstants.STATUS_PURGING, 6, new Date()));
        when(nodeMapper.deletePurgeBatch(8L)).thenReturn(1);

        assertThatThrownBy(() -> persistence.finalizeClaim(claim))
                .isInstanceOf(DriveException.class)
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_CONCURRENT_MODIFICATION);
        verify(quotaService, never()).release(any(), anyLong());
    }

    @Test
    @DisplayName("标记清理失败不释放任何额度")
    void shouldMarkFailedWithoutQuotaRelease()
    {
        DrivePurgeClaim claim = claim();
        when(nodeMapper.markPurgeFailedRoot(8L, 6, "alice")).thenReturn(1);
        when(nodeMapper.markPurgeFailedDescendants(8L, 8L, "alice")).thenReturn(1);

        persistence.markFailed(claim);

        verify(nodeMapper).markPurgeFailedRoot(8L, 6, "alice");
        verify(quotaService, never()).release(any(), anyLong());
    }

    @Test
    @DisplayName("额度精确扣减失败向事务边界传播并回滚元数据删除")
    void shouldPropagateQuotaReleaseFailure()
    {
        DrivePurgeClaim claim = claim();
        when(nodeMapper.selectClaimRootForUpdate(8L, 6))
                .thenReturn(root(DriveConstants.STATUS_PURGING, 6, new Date()));
        when(nodeMapper.deletePurgeBatch(8L)).thenReturn(2);
        doThrow(new DriveException(DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE, "underflow"))
                .when(quotaService).release(4L, 12L);

        assertThatThrownBy(() -> persistence.finalizeClaim(claim))
                .isInstanceOf(DriveException.class)
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE);
    }

    @Test
    @DisplayName("认领、失败标记和最终完成均声明独立事务边界")
    void shouldDeclareTransactionalBoundaries() throws Exception
    {
        assertThat(DriveTrashPersistence.class.getMethod("claim",
                Long.class, Date.class, DriveAuditContext.class, String.class)
                .getAnnotation(Transactional.class)).isNotNull();
        assertThat(DriveTrashPersistence.class.getMethod(
                "markFailed", DrivePurgeClaim.class)
                .getAnnotation(Transactional.class)).isNotNull();
        assertThat(DriveTrashPersistence.class.getMethod(
                "finalizeClaim", DrivePurgeClaim.class)
                .getAnnotation(Transactional.class)).isNotNull();
    }

    private DrivePurgeClaim claim()
    {
        return new DrivePurgeClaim(8L, 4L, 6, 2, 12L,
                List.of("2026/07/private.pdf"), "月报", context,
                DriveConstants.ACTION_PURGE);
    }

    private static DriveNode root(String status, int version, Date updateTime)
    {
        DriveNode node = new DriveNode();
        node.setNodeId(8L);
        node.setSpaceId(4L);
        node.setTrashRootId(8L);
        node.setNodeType(DriveConstants.NODE_FOLDER);
        node.setNodeName("月报");
        node.setStatus(status);
        node.setVersion(version);
        node.setUpdateTime(updateTime);
        return node;
    }

    private static DriveNode file(Long id, long size, String storageKey)
    {
        DriveNode node = new DriveNode();
        node.setNodeId(id);
        node.setSpaceId(4L);
        node.setTrashRootId(8L);
        node.setNodeType(DriveConstants.NODE_FILE);
        node.setNodeName("预算.pdf");
        node.setStorageKey(storageKey);
        node.setSizeBytes(size);
        node.setStatus(DriveConstants.STATUS_TRASHED);
        node.setVersion(1);
        return node;
    }
}
