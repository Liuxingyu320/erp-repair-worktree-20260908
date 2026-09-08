package com.erp.file.drive.service;

import com.erp.file.drive.config.DriveProperties;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.exception.DriveException;
import org.springframework.stereotype.Service;

@Service
public class DriveFeatureGuard
{
    private final DriveProperties properties;

    public DriveFeatureGuard(DriveProperties properties)
    {
        this.properties = properties;
    }

    public void requireEnabled()
    {
        if (!properties.isEnabled())
        {
            throw new DriveException(DriveErrorCodes.DRIVE_DISABLED, "云盘功能尚未开启");
        }
    }

    public boolean isQuotaPolicyEnabled()
    {
        return properties.isQuotaPolicyEnabled();
    }

    public boolean isOrganizationSyncEnabled()
    {
        return properties.isOrganizationSyncEnabled();
    }

    public boolean isCapacityReservationEnabled()
    {
        return properties.isCapacityReservationEnabled();
    }

    public void requireOrganizationSyncEnabled()
    {
        if (!properties.isOrganizationSyncEnabled())
        {
            throw new DriveException(DriveErrorCodes.DRIVE_DISABLED,
                    "组织盘同步运行开关尚未开启");
        }
    }

    public String organizationSyncCron()
    {
        return properties.getOrganizationSyncCron();
    }

    public int uploadReservationTimeoutMinutes()
    {
        return properties.getUploadReservationTimeoutMinutes();
    }

    public String uploadReservationCleanupCron()
    {
        return properties.getUploadReservationCleanupCron();
    }

    public int uploadReservationCleanupBatchSize()
    {
        return properties.getUploadReservationCleanupBatchSize();
    }
}
