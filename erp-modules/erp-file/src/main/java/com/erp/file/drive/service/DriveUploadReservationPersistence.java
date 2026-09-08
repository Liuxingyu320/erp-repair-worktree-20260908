package com.erp.file.drive.service;

import java.util.Date;
import java.util.List;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.domain.DriveUploadReservation;
import com.erp.file.drive.exception.DriveException;
import com.erp.file.drive.mapper.DriveUploadReservationMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 上传容量预占的短事务边界。对象存储 I/O 不得在这些事务中执行。 */
@Service
public class DriveUploadReservationPersistence
{
    private static final long MIN_RETRY_MILLIS = 60_000L;
    private static final long MAX_RETRY_MILLIS = 3_600_000L;

    private final DriveUploadReservationMapper mapper;
    private final DriveCapacityService capacityService;

    public DriveUploadReservationPersistence(DriveUploadReservationMapper mapper,
            DriveCapacityService capacityService)
    {
        this.mapper = mapper;
        this.capacityService = capacityService;
    }

    @Transactional
    public DriveUploadReservation create(DriveUploadReservation reservation)
    {
        capacityService.requireUploadCapacityAndLock(value(reservation.getReservedBytes()));
        try
        {
            if (mapper.insertReservation(reservation) != 1) throw concurrentModification();
        }
        catch (DuplicateKeyException ex)
        {
            throw concurrentModification();
        }
        return reservation;
    }

    public List<DriveUploadReservation> selectCleanupCandidates(Date now,
            Date staleCleaningBefore, int limit)
    {
        List<DriveUploadReservation> values = mapper.selectCleanupCandidates(
                now, staleCleaningBefore, limit);
        return values == null ? List.of() : values;
    }

    @Transactional
    public DriveUploadReservation claimCleanup(DriveUploadReservation candidate,
            Date now, Date staleCleaningBefore, String updateBy)
    {
        if (candidate == null || candidate.getReservationId() == null
                || candidate.getVersion() == null)
        {
            return null;
        }
        if (mapper.claimCleanup(candidate.getReservationId(), candidate.getVersion(),
                now, staleCleaningBefore, safe(updateBy)) != 1)
        {
            return null;
        }
        return mapper.selectById(candidate.getReservationId());
    }

    @Transactional
    public void releaseConfirmedAbsent(String reservationId)
    {
        capacityService.lockForAllocationChange();
        DriveUploadReservation current = mapper.selectForUpdate(reservationId);
        if (current == null) return;
        if (mapper.deleteExpected(reservationId, value(current.getVersion()),
                current.getStatus()) != 1)
        {
            throw concurrentModification();
        }
    }

    @Transactional
    public void completeCleanup(String reservationId)
    {
        capacityService.lockForAllocationChange();
        DriveUploadReservation current = mapper.selectForUpdate(reservationId);
        if (current == null) return;
        if (!DriveConstants.RESERVATION_CLEANING.equals(current.getStatus())
                || mapper.deleteExpected(reservationId, value(current.getVersion()),
                    DriveConstants.RESERVATION_CLEANING) != 1)
        {
            throw concurrentModification();
        }
    }

    @Transactional
    public void markCleanupFailed(String reservationId, String errorCode, String updateBy)
    {
        capacityService.lockForAllocationChange();
        DriveUploadReservation current = mapper.selectForUpdate(reservationId);
        if (current == null
                || DriveConstants.RESERVATION_CLEANUP_FAILED.equals(current.getStatus()))
        {
            return;
        }
        if (!DriveConstants.RESERVATION_RESERVED.equals(current.getStatus())
                && !DriveConstants.RESERVATION_CLEANING.equals(current.getStatus()))
        {
            throw concurrentModification();
        }
        int retryCount = Math.max(0, value(current.getRetryCount())) + 1;
        Date nextRetry = new Date(System.currentTimeMillis() + retryDelay(retryCount));
        if (mapper.markCleanupFailed(reservationId, value(current.getVersion()), retryCount,
                nextRetry, boundedError(errorCode), safe(updateBy)) != 1)
        {
            throw concurrentModification();
        }
    }

    private static long retryDelay(int retryCount)
    {
        int shift = Math.max(0, Math.min(6, retryCount - 1));
        return Math.min(MAX_RETRY_MILLIS, MIN_RETRY_MILLIS << shift);
    }

    private static long value(Long value)
    {
        return value == null ? 0L : value;
    }

    private static int value(Integer value)
    {
        return value == null ? 0 : value;
    }

    private static String safe(String value)
    {
        return value == null ? "" : value;
    }

    private static String boundedError(String value)
    {
        if (value == null || value.isBlank()) return DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE;
        String normalized = value.trim();
        return normalized.length() <= 64 ? normalized : normalized.substring(0, 64);
    }

    private static DriveException concurrentModification()
    {
        return new DriveException(DriveErrorCodes.DRIVE_CONCURRENT_MODIFICATION,
                "上传容量预占正在变化，请重试");
    }
}
