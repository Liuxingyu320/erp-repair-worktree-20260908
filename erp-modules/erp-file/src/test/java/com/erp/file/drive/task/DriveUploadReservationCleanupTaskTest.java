package com.erp.file.drive.task;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.erp.file.drive.service.DriveUploadReservationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("云盘上传预占定时清理")
class DriveUploadReservationCleanupTaskTest
{
    @Test
    @DisplayName("定时入口委托可幂等清理服务")
    void shouldDelegateCleanup()
    {
        DriveUploadReservationService service =
                mock(DriveUploadReservationService.class);

        new DriveUploadReservationCleanupTask(service).cleanup();

        verify(service).cleanupExpired();
    }
}
