package com.erp.file.drive.task;

import com.erp.file.drive.service.DriveUploadReservationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 清理进程退出或对象补偿失败后遗留的上传容量预占。 */
@Component
public class DriveUploadReservationCleanupTask
{
    private final DriveUploadReservationService reservationService;

    public DriveUploadReservationCleanupTask(
            DriveUploadReservationService reservationService)
    {
        this.reservationService = reservationService;
    }

    @Scheduled(cron = "${drive.upload-reservation-cleanup-cron:0 */5 * * * *}",
            zone = "${drive.cleanup-zone:Asia/Shanghai}")
    public void cleanup()
    {
        reservationService.cleanupExpired();
    }
}
