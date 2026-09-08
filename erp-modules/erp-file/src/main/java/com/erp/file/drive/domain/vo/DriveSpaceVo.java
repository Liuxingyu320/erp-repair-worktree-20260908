package com.erp.file.drive.domain.vo;

import com.erp.file.drive.constant.DriveConstants;

/**
 * 不暴露空间所有者或部门内部标识的云盘空间响应。
 */
public record DriveSpaceVo(Long spaceId, String spaceType, String spaceName,
        Long quotaBytes, Long usedBytes, Integer version,
        boolean canWrite, boolean canManageQuota, String quotaSourceLabel,
        boolean overQuota, long overQuotaBytes, double usagePercent,
        boolean canOpenQuotaCenter, String lifecycleStatus, boolean canCleanup,
        String writeBlockedCode, String writeBlockedMessage)
{
    public DriveSpaceVo(Long spaceId, String spaceType, String spaceName,
            Long quotaBytes, Long usedBytes, Integer version,
            boolean canWrite, boolean canManageQuota, String quotaSourceLabel,
            boolean overQuota, long overQuotaBytes, double usagePercent,
            boolean canOpenQuotaCenter, String lifecycleStatus, boolean canCleanup)
    {
        this(spaceId, spaceType, spaceName, quotaBytes, usedBytes, version,
                canWrite, canManageQuota, quotaSourceLabel, overQuota, overQuotaBytes,
                usagePercent, canOpenQuotaCenter, lifecycleStatus, canCleanup,
                null, null);
    }

    public DriveSpaceVo(Long spaceId, String spaceType, String spaceName,
            Long quotaBytes, Long usedBytes, Integer version,
            boolean canWrite, boolean canManageQuota)
    {
        this(spaceId, spaceType, spaceName, quotaBytes, usedBytes, version,
                canWrite, canManageQuota, "",
                value(usedBytes) > value(quotaBytes),
                Math.max(0L, value(usedBytes) - value(quotaBytes)),
                0D, canManageQuota, DriveConstants.STATUS_ACTIVE, canWrite,
                null, null);
    }

    private static long value(Long value)
    {
        return value == null ? 0L : value;
    }
}
