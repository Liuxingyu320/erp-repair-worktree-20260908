package com.erp.file.drive.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.List;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.domain.DriveAuditContext;
import com.erp.file.drive.domain.DrivePurgeClaim;
import com.erp.file.drive.metric.DriveMetrics;
import com.erp.file.drive.storage.DriveStorageProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("云盘回收站物理清理工作者")
class DriveTrashPurgeWorkerTest
{
    private DriveStorageProvider storage;
    private DriveTrashPersistence persistence;
    private DriveOperationLogService logService;
    private DriveMetrics metrics;
    private DriveTrashPurgeWorker worker;
    private DrivePurgeClaim claim;

    @BeforeEach
    void setUp()
    {
        storage = mock(DriveStorageProvider.class);
        persistence = mock(DriveTrashPersistence.class);
        logService = mock(DriveOperationLogService.class);
        metrics = mock(DriveMetrics.class);
        worker = new DriveTrashPurgeWorker(storage, persistence, logService, metrics);
        DriveAuditContext context = new DriveAuditContext(20L, 8L, "alice",
                "request-1", "127.0.0.1", "test");
        claim = new DrivePurgeClaim(8L, 4L, 6, 2, 12L,
                List.of("2026/07/private.pdf"), "月报", context,
                DriveConstants.ACTION_PURGE);
    }

    @Test
    @DisplayName("物理对象已不存在按幂等成功处理")
    void shouldTreatMissingObjectAsSuccess() throws Exception
    {
        when(storage.exists("2026/07/private.pdf")).thenReturn(false);

        worker.execute(claim);

        verify(storage, never()).delete("2026/07/private.pdf");
        verify(persistence).finalizeClaim(claim);
        verify(logService).success(DriveConstants.ACTION_PURGE, claim.auditContext(),
                claim.auditNode(), null, "count=2;bytes=12");
    }

    @Test
    @DisplayName("检查后对象并发消失仍按幂等成功处理")
    void shouldIgnoreNoSuchFileDuringDelete() throws Exception
    {
        when(storage.exists("2026/07/private.pdf")).thenReturn(true);
        doThrow(new NoSuchFileException("2026/07/private.pdf"))
                .when(storage).delete("2026/07/private.pdf");

        worker.execute(claim);

        verify(persistence).finalizeClaim(claim);
        verify(persistence, never()).markFailed(claim);
    }

    @Test
    @DisplayName("任一对象删除失败则整批标记失败且不完成元数据")
    void shouldMarkBatchFailedWhenStorageDeleteFails() throws Exception
    {
        when(storage.exists("2026/07/private.pdf")).thenReturn(true);
        doThrow(new IOException("failed for 2026/07/private.pdf"))
                .when(storage).delete("2026/07/private.pdf");

        assertThatCode(() -> worker.execute(claim)).doesNotThrowAnyException();

        verify(persistence).markFailed(claim);
        verify(persistence, never()).finalizeClaim(claim);
        verify(logService).failure(DriveConstants.ACTION_PURGE, claim.auditContext(),
                claim.auditNode(), DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE,
                null, "count=2");
        verify(metrics).recordCleanupFailure("worker");
    }

    @Test
    @DisplayName("完成元数据失败也必须尝试把批次标记为失败")
    void shouldMarkFailedWhenFinalizeFails()
    {
        when(storage.exists("2026/07/private.pdf")).thenReturn(false);
        doThrow(new IllegalStateException("db unavailable"))
                .when(persistence).finalizeClaim(claim);

        assertThatCode(() -> worker.execute(claim)).doesNotThrowAnyException();

        verify(persistence).markFailed(claim);
        verify(logService).failure(DriveConstants.ACTION_PURGE, claim.auditContext(),
                claim.auditNode(), DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE,
                null, "count=2");
    }
}
