package com.erp.file.drive.mapper;

import java.util.Date;
import java.util.List;
import com.erp.file.drive.domain.DriveUploadReservation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DriveUploadReservationMapper
{
    DriveUploadReservation selectById(@Param("reservationId") String reservationId);

    DriveUploadReservation selectForUpdate(@Param("reservationId") String reservationId);

    List<DriveUploadReservation> selectCleanupCandidates(@Param("now") Date now,
            @Param("staleCleaningBefore") Date staleCleaningBefore,
            @Param("limit") int limit);

    Long sumPendingBytes();

    int countStale(@Param("now") Date now,
            @Param("staleCleaningBefore") Date staleCleaningBefore);

    int countCleanupFailed();

    int insertReservation(DriveUploadReservation reservation);

    int claimCleanup(@Param("reservationId") String reservationId,
            @Param("version") int version,
            @Param("now") Date now,
            @Param("staleCleaningBefore") Date staleCleaningBefore,
            @Param("updateBy") String updateBy);

    int markCleanupFailed(@Param("reservationId") String reservationId,
            @Param("version") int version,
            @Param("retryCount") int retryCount,
            @Param("nextRetryTime") Date nextRetryTime,
            @Param("lastErrorCode") String lastErrorCode,
            @Param("updateBy") String updateBy);

    int deleteExpected(@Param("reservationId") String reservationId,
            @Param("version") int version,
            @Param("status") String status);
}
