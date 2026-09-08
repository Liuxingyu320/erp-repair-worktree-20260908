package com.erp.file.drive.service;

import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.domain.DriveSpace;
import com.erp.file.drive.exception.DriveException;
import com.erp.file.drive.mapper.DriveSpaceMapper;
import org.springframework.stereotype.Service;

/**
 * 用条件更新实现并发安全的空间额度预占、释放和调整。
 */
@Service
public class DriveQuotaService
{
    private final DriveSpaceMapper spaceMapper;

    public DriveQuotaService(DriveSpaceMapper spaceMapper)
    {
        this.spaceMapper = spaceMapper;
    }

    public void reserve(Long spaceId, long bytes)
    {
        if (bytes < 0)
        {
            throw quotaExceeded();
        }
        if (bytes == 0)
        {
            return;
        }
        if (spaceMapper.reserveQuota(spaceId, bytes) != 1)
        {
            throw quotaExceeded();
        }
    }

    public void preflight(DriveSpace space, long bytes)
    {
        if (space == null || !DriveConstants.STATUS_ACTIVE.equals(space.getStatus())
                || bytes < 0 || space.getQuotaBytes() == null)
        {
            throw quotaExceeded();
        }
        long used = usedBytes(space);
        if (used > space.getQuotaBytes() || bytes > space.getQuotaBytes() - used)
        {
            throw quotaExceeded();
        }
    }

    public void release(Long spaceId, long bytes)
    {
        if (bytes < 0)
        {
            throw quotaExceeded();
        }
        if (bytes == 0)
        {
            return;
        }
        if (spaceMapper.releaseQuota(spaceId, bytes) != 1)
        {
            DriveSpace current = spaceId == null ? null : spaceMapper.selectById(spaceId);
            if (current == null || !DriveConstants.STATUS_ACTIVE.equals(current.getStatus()))
            {
                throw spaceNotFound();
            }
            throw storageUnavailable();
        }
    }

    public DriveSpace updateQuota(Long spaceId, long quotaBytes, int version, String updateBy)
    {
        if (quotaBytes <= 0)
        {
            throw quotaExceeded();
        }
        if (version < 0)
        {
            throw concurrentModification();
        }

        DriveSpace before = requireActiveSpace(spaceId);
        if (usedBytes(before) > quotaBytes)
        {
            throw quotaExceeded();
        }

        int updated = spaceMapper.updateQuota(spaceId, quotaBytes, version,
                updateBy == null ? "" : updateBy);
        if (updated != 1)
        {
            DriveSpace latest = requireActiveSpace(spaceId);
            if (usedBytes(latest) > quotaBytes)
            {
                throw quotaExceeded();
            }
            throw concurrentModification();
        }
        return requireActiveSpace(spaceId);
    }

    private DriveSpace requireActiveSpace(Long spaceId)
    {
        DriveSpace space = spaceId == null ? null : spaceMapper.selectById(spaceId);
        if (space == null || !DriveConstants.STATUS_ACTIVE.equals(space.getStatus()))
        {
            throw spaceNotFound();
        }
        return space;
    }

    private static long usedBytes(DriveSpace space)
    {
        return space.getUsedBytes() == null ? 0L : space.getUsedBytes();
    }

    private static DriveException quotaExceeded()
    {
        return new DriveException(DriveErrorCodes.DRIVE_QUOTA_EXCEEDED, "云盘空间额度不足");
    }

    private static DriveException concurrentModification()
    {
        return new DriveException(DriveErrorCodes.DRIVE_CONCURRENT_MODIFICATION,
                "空间已被其他操作更新，请刷新后重试");
    }

    private static DriveException spaceNotFound()
    {
        return new DriveException(DriveErrorCodes.DRIVE_SPACE_NOT_FOUND, "云盘空间不存在");
    }

    private static DriveException storageUnavailable()
    {
        return new DriveException(DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE,
                "云盘空间用量数据不一致，暂时无法完成清理");
    }
}
