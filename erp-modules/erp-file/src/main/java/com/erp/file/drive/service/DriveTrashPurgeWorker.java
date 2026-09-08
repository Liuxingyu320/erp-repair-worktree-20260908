package com.erp.file.drive.service;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.domain.DrivePurgeClaim;
import com.erp.file.drive.metric.DriveMetrics;
import com.erp.file.drive.storage.DriveStorageProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 在短事务之外删除回收批次的物理对象。
 */
@Service
public class DriveTrashPurgeWorker
{
    private static final Logger log = LoggerFactory.getLogger(DriveTrashPurgeWorker.class);

    private final DriveStorageProvider storage;
    private final DriveTrashPersistence persistence;
    private final DriveOperationLogService logService;
    private final DriveMetrics metrics;

    public DriveTrashPurgeWorker(DriveStorageProvider storage,
            DriveTrashPersistence persistence, DriveOperationLogService logService,
            DriveMetrics metrics)
    {
        this.storage = storage;
        this.persistence = persistence;
        this.logService = logService;
        this.metrics = metrics;
    }

    public void execute(DrivePurgeClaim claim)
    {
        try
        {
            for (String storageKey : claim.storageKeys())
            {
                deleteObject(storageKey);
            }
            persistence.finalizeClaim(claim);
            logService.success(claim.auditAction(), claim.auditContext(),
                    claim.auditNode(), null, successSummary(claim));
        }
        catch (RuntimeException | IOException ex)
        {
            try
            {
                persistence.markFailed(claim);
            }
            catch (RuntimeException markFailure)
            {
                metrics.recordCleanupFailure("mark_failed");
                log.error("drive_purge_mark_failed requestId={} trashRootId={} failureType={}",
                        claim.auditContext().requestId(), claim.trashRootId(),
                        markFailure.getClass().getSimpleName());
            }
            logService.failure(claim.auditAction(), claim.auditContext(),
                    claim.auditNode(), DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE,
                    null, "count=" + claim.rowCount());
            log.error("drive_purge_failed requestId={} trashRootId={} failureType={}",
                    claim.auditContext().requestId(), claim.trashRootId(),
                    ex.getClass().getSimpleName());
            metrics.recordCleanupFailure("worker");
        }
    }

    private void deleteObject(String storageKey) throws IOException
    {
        if (storageKey == null || storageKey.isBlank())
        {
            throw new IOException("invalid private object reference");
        }
        if (!storage.exists(storageKey))
        {
            return;
        }
        try
        {
            storage.delete(storageKey);
        }
        catch (NoSuchFileException ex)
        {
            // Another retry may already have removed this immutable object.
        }
    }

    private static String successSummary(DrivePurgeClaim claim)
    {
        return "count=" + claim.rowCount() + ";bytes=" + claim.totalFileBytes();
    }
}
