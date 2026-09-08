package com.erp.file.drive.task;

import com.erp.file.drive.config.DriveProperties;
import com.erp.file.drive.service.DriveTrashService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DriveTrashCleanupTask
{
    private final DriveProperties properties;
    private final DriveTrashService trashService;

    public DriveTrashCleanupTask(DriveProperties properties, DriveTrashService trashService)
    {
        this.properties = properties;
        this.trashService = trashService;
    }

    @Scheduled(cron = "${drive.cleanup-cron:0 30 2 * * *}",
            zone = "${drive.cleanup-zone:Asia/Shanghai}")
    public void cleanup()
    {
        if (!properties.isEnabled())
        {
            return;
        }
        trashService.dispatchExpired(100);
    }
}
