package com.erp.file.drive.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.domain.DriveAuditContext;
import com.erp.file.drive.domain.DriveNode;
import com.erp.file.drive.domain.DrivePurgeClaim;
import com.erp.file.drive.exception.DriveException;
import com.erp.file.drive.mapper.DriveNodeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 回收批次的短事务边界。物理对象操作不得在这些事务内执行。
 */
@Service
public class DriveTrashPersistence
{
    private final DriveNodeMapper nodeMapper;
    private final DriveQuotaService quotaService;

    public DriveTrashPersistence(DriveNodeMapper nodeMapper, DriveQuotaService quotaService)
    {
        this.nodeMapper = nodeMapper;
        this.quotaService = quotaService;
    }

    @Transactional
    public DrivePurgeClaim claim(Long trashRootId, Date staleBefore,
            DriveAuditContext context, String auditAction)
    {
        List<DriveNode> batch = nodeMapper.selectTrashBatchForUpdate(trashRootId);
        DriveNode root = findRoot(batch, trashRootId);
        if (root == null || !eligible(root, staleBefore))
        {
            return null;
        }
        String expectedStatus = root.getStatus();
        String updateBy = context.operatorName() == null ? "" : context.operatorName();
        if (nodeMapper.claimTrashRoot(trashRootId, root.getVersion(), expectedStatus,
                staleBefore, updateBy) != 1)
        {
            return null;
        }
        int expectedDescendants = batch.size() - 1;
        int claimedDescendants = nodeMapper.claimTrashDescendants(trashRootId,
                trashRootId, expectedStatus, staleBefore, updateBy);
        if (claimedDescendants != expectedDescendants)
        {
            throw concurrentModification();
        }

        List<String> storageKeys = new ArrayList<>();
        long totalBytes = 0L;
        for (DriveNode node : batch)
        {
            requireSameBatch(root, node, expectedStatus);
            if (DriveConstants.NODE_FILE.equals(node.getNodeType()))
            {
                if (node.getSizeBytes() == null || node.getSizeBytes() < 0)
                {
                    throw storageUnavailable();
                }
                try
                {
                    totalBytes = Math.addExact(totalBytes, node.getSizeBytes());
                }
                catch (ArithmeticException ex)
                {
                    throw storageUnavailable();
                }
                storageKeys.add(node.getStorageKey());
            }
        }
        return new DrivePurgeClaim(trashRootId, root.getSpaceId(),
                root.getVersion() + 1, batch.size(), totalBytes, storageKeys,
                root.getNodeName(), context, auditAction);
    }

    @Transactional
    public void markFailed(DrivePurgeClaim claim)
    {
        String updateBy = claim.auditContext().operatorName() == null
                ? "" : claim.auditContext().operatorName();
        int rootUpdated = nodeMapper.markPurgeFailedRoot(
                claim.trashRootId(), claim.claimVersion(), updateBy);
        if (rootUpdated == 0)
        {
            return;
        }
        int descendants = nodeMapper.markPurgeFailedDescendants(
                claim.trashRootId(), claim.trashRootId(), updateBy);
        if (descendants != claim.rowCount() - 1)
        {
            throw concurrentModification();
        }
    }

    @Transactional
    public void finalizeClaim(DrivePurgeClaim claim)
    {
        DriveNode root = nodeMapper.selectClaimRootForUpdate(
                claim.trashRootId(), claim.claimVersion());
        if (root == null || !Objects.equals(root.getSpaceId(), claim.spaceId())
                || !DriveConstants.STATUS_PURGING.equals(root.getStatus()))
        {
            throw concurrentModification();
        }
        int deleted = nodeMapper.deletePurgeBatch(claim.trashRootId());
        if (deleted != claim.rowCount())
        {
            throw concurrentModification();
        }
        quotaService.release(claim.spaceId(), claim.totalFileBytes());
    }

    private static DriveNode findRoot(List<DriveNode> batch, Long trashRootId)
    {
        if (batch == null)
        {
            return null;
        }
        return batch.stream().filter(node -> node != null
                && Objects.equals(node.getNodeId(), trashRootId)
                && Objects.equals(node.getTrashRootId(), trashRootId))
                .findFirst().orElse(null);
    }

    private static boolean eligible(DriveNode root, Date staleBefore)
    {
        if (root.getVersion() == null || root.getSpaceId() == null)
        {
            return false;
        }
        if (DriveConstants.STATUS_TRASHED.equals(root.getStatus())
                || DriveConstants.STATUS_PURGE_FAILED.equals(root.getStatus()))
        {
            return true;
        }
        return DriveConstants.STATUS_PURGING.equals(root.getStatus())
                && staleBefore != null
                && (root.getUpdateTime() == null || root.getUpdateTime().before(staleBefore));
    }

    private static void requireSameBatch(DriveNode root, DriveNode node, String status)
    {
        if (node == null || !Objects.equals(root.getSpaceId(), node.getSpaceId())
                || !Objects.equals(root.getNodeId(), node.getTrashRootId())
                || !Objects.equals(status, node.getStatus()))
        {
            throw concurrentModification();
        }
    }

    private static DriveException concurrentModification()
    {
        return new DriveException(DriveErrorCodes.DRIVE_CONCURRENT_MODIFICATION,
                "回收批次已被其他任务更新，请稍后重试");
    }

    private static DriveException storageUnavailable()
    {
        return new DriveException(DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE,
                "回收批次数据异常，暂时无法清理");
    }
}
