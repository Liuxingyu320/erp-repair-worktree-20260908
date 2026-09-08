package com.erp.file.drive.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 云盘功能与存储配置。默认关闭，避免未完成部署时暴露入口。
 */
@Component
@ConfigurationProperties(prefix = "drive")
public class DriveProperties
{
    private boolean enabled = false;
    private String storageType = "local";
    private String localPath = "./uploadPath/private/drive";
    private String minioBucket = "erp-drive-private";
    private long maxFileSize = 104857600L;
    private long personalQuota = 2147483648L;
    private long departmentQuota = 21474836480L;
    private long companyQuota = 107374182400L;
    private boolean quotaPolicyEnabled = false;
    private boolean organizationSyncEnabled = false;
    private String organizationSyncCron = "0 */10 * * * *";
    private boolean capacityReservationEnabled = false;
    private int uploadReservationTimeoutMinutes = 120;
    private String uploadReservationCleanupCron = "0 */5 * * * *";
    private int uploadReservationCleanupBatchSize = 100;
    private int trashRetentionDays = 30;
    private String cleanupCron = "0 30 2 * * *";
    private String cleanupZone = "Asia/Shanghai";
    private long healthCacheMillis = 5000L;

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    public String getStorageType()
    {
        return storageType;
    }

    public void setStorageType(String storageType)
    {
        this.storageType = storageType;
    }

    public String getLocalPath()
    {
        return localPath;
    }

    public void setLocalPath(String localPath)
    {
        this.localPath = localPath;
    }

    public String getMinioBucket()
    {
        return minioBucket;
    }

    public void setMinioBucket(String minioBucket)
    {
        this.minioBucket = minioBucket;
    }

    public long getMaxFileSize()
    {
        return maxFileSize;
    }

    public void setMaxFileSize(long maxFileSize)
    {
        this.maxFileSize = maxFileSize;
    }

    public long getPersonalQuota()
    {
        return personalQuota;
    }

    public void setPersonalQuota(long personalQuota)
    {
        this.personalQuota = personalQuota;
    }

    public long getDepartmentQuota()
    {
        return departmentQuota;
    }

    public void setDepartmentQuota(long departmentQuota)
    {
        this.departmentQuota = departmentQuota;
    }

    public long getCompanyQuota()
    {
        return companyQuota;
    }

    public void setCompanyQuota(long companyQuota)
    {
        this.companyQuota = companyQuota;
    }

    public boolean isQuotaPolicyEnabled()
    {
        return quotaPolicyEnabled;
    }

    public void setQuotaPolicyEnabled(boolean quotaPolicyEnabled)
    {
        this.quotaPolicyEnabled = quotaPolicyEnabled;
    }

    public boolean isOrganizationSyncEnabled()
    {
        return organizationSyncEnabled;
    }

    public void setOrganizationSyncEnabled(boolean organizationSyncEnabled)
    {
        this.organizationSyncEnabled = organizationSyncEnabled;
    }

    public String getOrganizationSyncCron()
    {
        return organizationSyncCron;
    }

    public void setOrganizationSyncCron(String organizationSyncCron)
    {
        this.organizationSyncCron = organizationSyncCron;
    }

    public boolean isCapacityReservationEnabled()
    {
        return capacityReservationEnabled;
    }

    public void setCapacityReservationEnabled(boolean capacityReservationEnabled)
    {
        this.capacityReservationEnabled = capacityReservationEnabled;
    }

    public int getUploadReservationTimeoutMinutes()
    {
        return uploadReservationTimeoutMinutes;
    }

    public void setUploadReservationTimeoutMinutes(int uploadReservationTimeoutMinutes)
    {
        this.uploadReservationTimeoutMinutes = uploadReservationTimeoutMinutes;
    }

    public String getUploadReservationCleanupCron()
    {
        return uploadReservationCleanupCron;
    }

    public void setUploadReservationCleanupCron(String uploadReservationCleanupCron)
    {
        this.uploadReservationCleanupCron = uploadReservationCleanupCron;
    }

    public int getUploadReservationCleanupBatchSize()
    {
        return uploadReservationCleanupBatchSize;
    }

    public void setUploadReservationCleanupBatchSize(int uploadReservationCleanupBatchSize)
    {
        this.uploadReservationCleanupBatchSize = uploadReservationCleanupBatchSize;
    }

    public int getTrashRetentionDays()
    {
        return trashRetentionDays;
    }

    public void setTrashRetentionDays(int trashRetentionDays)
    {
        this.trashRetentionDays = trashRetentionDays;
    }

    public String getCleanupCron()
    {
        return cleanupCron;
    }

    public void setCleanupCron(String cleanupCron)
    {
        this.cleanupCron = cleanupCron;
    }

    public String getCleanupZone()
    {
        return cleanupZone;
    }

    public void setCleanupZone(String cleanupZone)
    {
        this.cleanupZone = cleanupZone;
    }

    public long getHealthCacheMillis()
    {
        return healthCacheMillis;
    }

    public void setHealthCacheMillis(long healthCacheMillis)
    {
        this.healthCacheMillis = healthCacheMillis;
    }
}
