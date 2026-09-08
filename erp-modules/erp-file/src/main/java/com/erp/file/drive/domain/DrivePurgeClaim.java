package com.erp.file.drive.domain;

import java.util.List;
import java.util.Objects;
import com.erp.file.drive.constant.DriveConstants;

/**
 * 已提交的回收批次清理认领。仅在服务内部传递，字符串表示会主动隐藏物理键。
 */
public final class DrivePurgeClaim
{
    private final Long trashRootId;
    private final Long spaceId;
    private final int claimVersion;
    private final int rowCount;
    private final long totalFileBytes;
    private final List<String> storageKeys;
    private final DriveAuditContext auditContext;
    private final String auditAction;
    private final DriveNode auditNode;

    public DrivePurgeClaim(Long trashRootId, Long spaceId, int claimVersion,
            int rowCount, long totalFileBytes, List<String> storageKeys,
            String rootName, DriveAuditContext auditContext, String auditAction)
    {
        if (trashRootId == null || trashRootId <= 0 || spaceId == null || spaceId <= 0
                || claimVersion < 0 || rowCount <= 0 || totalFileBytes < 0)
        {
            throw new IllegalArgumentException("invalid purge claim metadata");
        }
        if (!DriveConstants.ACTION_PURGE.equals(auditAction)
                && !DriveConstants.ACTION_CLEANUP.equals(auditAction))
        {
            throw new IllegalArgumentException("invalid purge audit action");
        }
        this.trashRootId = trashRootId;
        this.spaceId = spaceId;
        this.claimVersion = claimVersion;
        this.rowCount = rowCount;
        this.totalFileBytes = totalFileBytes;
        this.storageKeys = List.copyOf(Objects.requireNonNull(storageKeys, "storageKeys"));
        this.auditContext = Objects.requireNonNull(auditContext, "auditContext");
        this.auditAction = auditAction;
        this.auditNode = new DriveNode();
        this.auditNode.setNodeId(trashRootId);
        this.auditNode.setSpaceId(spaceId);
        this.auditNode.setNodeName(rootName == null ? "" : rootName);
    }

    public Long trashRootId()
    {
        return trashRootId;
    }

    public Long spaceId()
    {
        return spaceId;
    }

    public int claimVersion()
    {
        return claimVersion;
    }

    public int rowCount()
    {
        return rowCount;
    }

    public long totalFileBytes()
    {
        return totalFileBytes;
    }

    public List<String> storageKeys()
    {
        return storageKeys;
    }

    public DriveAuditContext auditContext()
    {
        return auditContext;
    }

    public String auditAction()
    {
        return auditAction;
    }

    public DriveNode auditNode()
    {
        return auditNode;
    }

    @Override
    public String toString()
    {
        return "DrivePurgeClaim[trashRootId=" + trashRootId
                + ", spaceId=" + spaceId
                + ", claimVersion=" + claimVersion
                + ", rowCount=" + rowCount
                + ", totalFileBytes=" + totalFileBytes
                + ", storageObjectCount=" + storageKeys.size()
                + ", auditAction=" + auditAction + "]";
    }
}
