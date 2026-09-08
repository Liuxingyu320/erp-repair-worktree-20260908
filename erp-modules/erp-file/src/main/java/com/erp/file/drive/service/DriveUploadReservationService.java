package com.erp.file.drive.service;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import com.erp.file.drive.config.DriveProperties;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.domain.DriveUploadReservation;
import com.erp.file.drive.exception.DriveException;
import com.erp.file.drive.metric.DriveMetrics;
import com.erp.file.drive.storage.DriveStorageProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 建立上传前预占，并回收进程中断后遗留的对象与容量。 */
@Service
public class DriveUploadReservationService
{
    private static final Logger log = LoggerFactory.getLogger(DriveUploadReservationService.class);

    private final DriveProperties properties;
    private final DriveUploadReservationPersistence persistence;
    private final DriveCapacityService capacityService;
    private final DriveStorageProvider storage;
    private final DriveMetrics metrics;

    public DriveUploadReservationService(DriveProperties properties,
            DriveUploadReservationPersistence persistence,
            DriveCapacityService capacityService,
            DriveStorageProvider storage, DriveMetrics metrics)
    {
        this.properties = properties;
        this.persistence = persistence;
        this.capacityService = capacityService;
        this.storage = storage;
        this.metrics = metrics;
    }

    public String reserveBeforeStorage(Long spaceId, String storageKey,
            long bytes, DriveActor actor)
    {
        if (!properties.isCapacityReservationEnabled())
        {
            capacityService.requireLegacyUploadAllowedWithoutReservation();
            return null;
        }
        if (spaceId == null || storageKey == null || storageKey.isBlank() || bytes <= 0)
        {
            throw new DriveException(DriveErrorCodes.DRIVE_POLICY_INVALID,
                    "上传容量预占参数无效");
        }
        Date now = new Date();
        DriveUploadReservation value = new DriveUploadReservation();
        value.setReservationId(UUID.randomUUID().toString());
        value.setSpaceId(spaceId);
        value.setStorageKey(storageKey);
        value.setReservedBytes(bytes);
        value.setStatus(DriveConstants.RESERVATION_RESERVED);
        value.setExpireTime(new Date(now.getTime() + timeoutMillis()));
        value.setRetryCount(0);
        value.setVersion(0);
        value.setCreateBy(username(actor));
        value.setCreateTime(now);
        value.setUpdateBy(username(actor));
        value.setUpdateTime(now);
        persistence.create(value);
        return value.getReservationId();
    }

    /** 上传外层已经确认对象是否清理成功；预占结算失败不得替换原始上传错误。 */
    public void settleAfterCompensation(String reservationId, boolean objectAbsent,
            DriveActor actor)
    {
        if (reservationId == null) return;
        try
        {
            if (objectAbsent)
            {
                persistence.releaseConfirmedAbsent(reservationId);
            }
            else
            {
                persistence.markCleanupFailed(reservationId,
                        DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE, username(actor));
            }
        }
        catch (RuntimeException ex)
        {
            log.error("drive_upload_reservation_settlement_failed failureType={}",
                    ex.getClass().getSimpleName());
            metrics.recordCleanupFailure("upload_reservation");
        }
    }

    public void cleanupExpired()
    {
        if (!properties.isEnabled() || !properties.isCapacityReservationEnabled()) return;
        Date now = new Date();
        Date staleCleaningBefore = new Date(now.getTime() - timeoutMillis());
        int limit = Math.max(1, Math.min(500,
                properties.getUploadReservationCleanupBatchSize()));
        List<DriveUploadReservation> candidates = persistence.selectCleanupCandidates(
                now, staleCleaningBefore, limit);
        for (DriveUploadReservation candidate : candidates)
        {
            DriveUploadReservation claimed = persistence.claimCleanup(
                    candidate, now, staleCleaningBefore, "system");
            if (claimed == null) continue;
            cleanupClaim(claimed);
        }
    }

    private void cleanupClaim(DriveUploadReservation claim)
    {
        try
        {
            if (storage.exists(claim.getStorageKey()))
            {
                storage.delete(claim.getStorageKey());
            }
            if (storage.exists(claim.getStorageKey()))
            {
                throw new IOException("object still exists after cleanup");
            }
            persistence.completeCleanup(claim.getReservationId());
        }
        catch (RuntimeException | IOException ex)
        {
            try
            {
                persistence.markCleanupFailed(claim.getReservationId(),
                        DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE, "system");
            }
            catch (RuntimeException markFailure)
            {
                log.error("drive_upload_reservation_mark_failed failureType={}",
                        markFailure.getClass().getSimpleName());
            }
            log.warn("drive_upload_reservation_cleanup_failed failureType={}",
                    ex.getClass().getSimpleName());
            metrics.recordCleanupFailure("upload_reservation");
        }
    }

    private long timeoutMillis()
    {
        long minutes = Math.max(1L, properties.getUploadReservationTimeoutMinutes());
        return Math.multiplyExact(minutes, 60_000L);
    }

    private static String username(DriveActor actor)
    {
        return actor == null || actor.username() == null ? "" : actor.username();
    }
}
