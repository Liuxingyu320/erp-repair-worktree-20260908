package com.erp.file.drive.task;

import com.erp.file.drive.config.DriveProperties;
import com.erp.file.drive.service.DriveOrganizationReconcileService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 默认每十分钟幂等对账组织盘，两个发布开关均开启才执行。 */
@Component
public class DriveOrganizationSyncTask
{
    private final DriveProperties properties;
    private final DriveOrganizationReconcileService reconcileService;

    public DriveOrganizationSyncTask(DriveProperties properties,
            DriveOrganizationReconcileService reconcileService)
    {
        this.properties = properties;
        this.reconcileService = reconcileService;
    }

    @Scheduled(cron = "${drive.organization-sync-cron:0 */10 * * * *}",
            zone = "${drive.cleanup-zone:Asia/Shanghai}")
    public void synchronize()
    {
        if (!properties.isEnabled() || !properties.isOrganizationSyncEnabled()) return;
        reconcileService.reconcileAll("system");
    }
}
