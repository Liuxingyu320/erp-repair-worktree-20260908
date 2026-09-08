package com.erp.file.drive.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import com.erp.file.drive.config.DriveProperties;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.domain.DriveAuditContext;
import com.erp.file.drive.domain.DriveNode;
import com.erp.file.drive.domain.DrivePurgeClaim;
import com.erp.file.drive.domain.DriveSpace;
import com.erp.file.drive.domain.vo.DriveNodeVo;
import com.erp.file.drive.exception.DriveException;
import com.erp.file.drive.mapper.DriveNodeMapper;
import com.erp.file.drive.mapper.DriveSpaceMapper;
import com.erp.file.drive.metric.DriveMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DriveTrashService
{
    private static final Logger log = LoggerFactory.getLogger(DriveTrashService.class);
    private static final long PURGE_STALE_MILLIS = 15L * 60L * 1000L;

    private final DriveNodeMapper nodeMapper;
    private final DriveSpaceMapper spaceMapper;
    private final DriveSpaceService spaceService;
    private final DriveNodeService nodeService;
    private final DriveNamePolicy namePolicy;
    private final DriveProperties properties;
    private final DriveTrashPersistence persistence;
    private final DriveTrashPurgeWorker worker;
    private final TaskExecutor purgeExecutor;
    private final DriveOperationLogService logService;
    private final DriveMetrics metrics;

    public DriveTrashService(DriveNodeMapper nodeMapper, DriveSpaceMapper spaceMapper,
            DriveSpaceService spaceService, DriveNodeService nodeService,
            DriveNamePolicy namePolicy, DriveProperties properties,
            DriveTrashPersistence persistence, DriveTrashPurgeWorker worker,
            @Qualifier("drivePurgeExecutor") TaskExecutor purgeExecutor,
            DriveOperationLogService logService, DriveMetrics metrics)
    {
        this.nodeMapper = nodeMapper;
        this.spaceMapper = spaceMapper;
        this.spaceService = spaceService;
        this.nodeService = nodeService;
        this.namePolicy = namePolicy;
        this.properties = properties;
        this.persistence = persistence;
        this.worker = worker;
        this.purgeExecutor = purgeExecutor;
        this.logService = logService;
        this.metrics = metrics;
    }

    @Transactional
    public void trash(Long nodeId, Integer version, DriveActor actor)
    {
        DriveNode root = null;
        try
        {
            root = nodeId == null ? null : nodeMapper.selectByIdForUpdate(nodeId);
            requireActiveNode(root);
            spaceService.requireCleanupSpace(root.getSpaceId(), actor);
            requireVersion(root, version);
            String rootPath = DriveNodeService.childAncestors(root);
            int count = nodeMapper.trashActiveSubtree(root.getSpaceId(), root.getNodeId(),
                    rootPath, actor.userId(), Math.max(1, properties.getTrashRetentionDays()),
                    safeUsername(actor));
            if (count < 1)
            {
                throw concurrentModification();
            }
            logService.success(DriveConstants.ACTION_TRASH, actor, root,
                    root.getNodeName(), "count=" + count);
        }
        catch (DriveException ex)
        {
            recordFailure(DriveConstants.ACTION_TRASH, actor, root, ex.getBusinessCode());
            throw ex;
        }
        catch (RuntimeException ex)
        {
            DriveException translated = storageUnavailable();
            recordFailure(DriveConstants.ACTION_TRASH, actor, root,
                    translated.getBusinessCode());
            throw translated;
        }
    }

    public List<DriveNodeVo> listTrash(Long spaceId, DriveActor actor)
    {
        try
        {
            return resolveTrashList(spaceId, actor);
        }
        catch (DriveException ex)
        {
            throw ex;
        }
        catch (RuntimeException ex)
        {
            throw storageUnavailable();
        }
    }

    private List<DriveNodeVo> resolveTrashList(Long spaceId, DriveActor actor)
    {
        spaceService.requireCleanupSpace(spaceId, actor);
        List<DriveNode> roots = nodeMapper.selectTrashRoots(spaceId);
        if (roots == null || roots.isEmpty())
        {
            return List.of();
        }
        List<DriveNodeVo> result = new ArrayList<>(roots.size());
        for (DriveNode root : roots)
        {
            if (isVisibleTrashRoot(root, spaceId))
            {
                result.add(nodeService.toVo(root, actor));
            }
        }
        return List.copyOf(result);
    }

    @Transactional
    public DriveNodeVo restore(Long trashRootId, DriveActor actor)
    {
        DriveNode root = null;
        try
        {
            List<DriveNode> batch = nodeMapper.selectTrashBatchForUpdate(trashRootId);
            root = findRoot(batch, trashRootId);
            if (root == null)
            {
                throw nodeNotFound();
            }
            if (!DriveConstants.STATUS_TRASHED.equals(root.getStatus()))
            {
                throw concurrentModification();
            }
            DriveSpace space = spaceService.requireWritableSpace(root.getSpaceId(), actor);
            Long parentId = root.getOriginalParentId() == null
                    ? DriveConstants.ROOT_PARENT_ID : root.getOriginalParentId();
            DriveNode parent = null;
            if (parentId != DriveConstants.ROOT_PARENT_ID)
            {
                parent = nodeMapper.selectByIdForUpdate(parentId);
                if (!isActiveFolderInSpace(parent, root.getSpaceId()))
                {
                    parentId = DriveConstants.ROOT_PARENT_ID;
                    parent = null;
                }
            }

            String rootAncestors;
            if (parentId == DriveConstants.ROOT_PARENT_ID)
            {
                DriveSpace lockedSpace = spaceMapper.selectByIdForUpdate(root.getSpaceId());
                requireSameActiveSpace(space, lockedSpace);
                rootAncestors = "0";
            }
            else
            {
                rootAncestors = DriveNodeService.childAncestors(parent);
            }

            String restoredName = nodeService.availableFileName(
                    root.getSpaceId(), parentId, root.getNodeName());
            String normalizedName = namePolicy.normalizedKey(restoredName);
            String oldPrefix = DriveNodeService.childAncestors(root);
            String newPrefix = rootAncestors + "," + root.getNodeId();
            int count;
            try
            {
                count = nodeMapper.restoreTrashBatch(trashRootId, root.getNodeId(),
                        parentId, rootAncestors, oldPrefix, newPrefix,
                        restoredName, normalizedName, safeUsername(actor));
            }
            catch (DuplicateKeyException ex)
            {
                throw nameConflict();
            }
            if (batch == null || count != batch.size())
            {
                throw concurrentModification();
            }

            String oldName = root.getNodeName();
            root.setParentId(parentId);
            root.setAncestors(rootAncestors);
            root.setNodeName(restoredName);
            root.setNormalizedName(normalizedName);
            root.setStatus(DriveConstants.STATUS_ACTIVE);
            root.setActiveFlag(1);
            root.setOriginalParentId(null);
            root.setTrashRootId(null);
            root.setTrashedBy(null);
            root.setTrashedTime(null);
            root.setPurgeAfter(null);
            root.setVersion(root.getVersion() == null ? 1 : root.getVersion() + 1);
            root.setUpdateBy(safeUsername(actor));
            root.setUpdateTime(new Date());
            logService.success(DriveConstants.ACTION_RESTORE, actor, root,
                    oldName, restoredName + ";parentId=" + parentId + ";count=" + count);
            return nodeService.toVo(root, actor);
        }
        catch (DriveException ex)
        {
            recordFailure(DriveConstants.ACTION_RESTORE, actor, root, ex.getBusinessCode());
            throw ex;
        }
        catch (RuntimeException ex)
        {
            DriveException translated = storageUnavailable();
            recordFailure(DriveConstants.ACTION_RESTORE, actor, root,
                    translated.getBusinessCode());
            throw translated;
        }
    }

    public DrivePurgeClaim requestPurge(Long trashRootId, DriveActor actor)
    {
        DriveNode root = null;
        DriveAuditContext context = null;
        DrivePurgeClaim claim = null;
        try
        {
            root = trashRootId == null ? null : nodeMapper.selectById(trashRootId);
            if (!isPurgeableRoot(root, trashRootId))
            {
                throw nodeNotFound();
            }
            spaceService.requireCleanupSpace(root.getSpaceId(), actor);
            context = logService.captureContext(actor);
            claim = persistence.claim(trashRootId, staleBefore(new Date()),
                    context, DriveConstants.ACTION_PURGE);
            if (claim == null)
            {
                throw concurrentModification();
            }
            submitClaim(claim);
            return claim;
        }
        catch (DriveException ex)
        {
            if (claim == null)
            {
                recordPurgeFailure(context, actor, root, ex.getBusinessCode());
            }
            throw ex;
        }
        catch (RuntimeException ex)
        {
            DriveException translated = storageUnavailable();
            if (claim == null)
            {
                recordPurgeFailure(context, actor, root, translated.getBusinessCode());
            }
            throw translated;
        }
    }

    public int requestEmptyTrash(Long spaceId, DriveActor actor)
    {
        try
        {
            return submitEmptyTrash(spaceId, actor);
        }
        catch (DriveException ex)
        {
            throw ex;
        }
        catch (RuntimeException ex)
        {
            throw storageUnavailable();
        }
    }

    private int submitEmptyTrash(Long spaceId, DriveActor actor)
    {
        spaceService.requireCleanupSpace(spaceId, actor);
        List<DriveNode> roots = nodeMapper.selectTrashRoots(spaceId);
        if (roots == null || roots.isEmpty())
        {
            return 0;
        }
        DriveAuditContext context = logService.captureContext(actor);
        int submitted = 0;
        for (DriveNode root : roots)
        {
            if (!isPurgeableRoot(root, root == null ? null : root.getNodeId()))
            {
                continue;
            }
            DrivePurgeClaim claim = null;
            try
            {
                claim = persistence.claim(root.getNodeId(),
                        staleBefore(new Date()), context, DriveConstants.ACTION_PURGE);
                if (claim != null)
                {
                    submitClaim(claim);
                    submitted++;
                }
            }
            catch (RuntimeException ex)
            {
                if (claim == null)
                {
                    String code = ex instanceof DriveException driveException
                            ? driveException.getBusinessCode()
                            : DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE;
                    logService.failure(DriveConstants.ACTION_PURGE, context, root,
                            code, null, null);
                }
                throw ex;
            }
        }
        return submitted;
    }

    public int dispatchExpired(int batchLimit)
    {
        int boundedLimit = Math.max(1, Math.min(100, batchLimit));
        Date now = new Date();
        Date staleBefore = staleBefore(now);
        List<DriveNode> roots = nodeMapper.selectExpiredTrashRoots(
                now, staleBefore, boundedLimit);
        if (roots == null || roots.isEmpty())
        {
            return 0;
        }
        int submitted = 0;
        for (DriveNode root : roots)
        {
            if (root == null || root.getNodeId() == null)
            {
                continue;
            }
            DriveAuditContext context = systemContext(root.getNodeId());
            DrivePurgeClaim claim = null;
            try
            {
                claim = persistence.claim(root.getNodeId(), staleBefore,
                        context, DriveConstants.ACTION_CLEANUP);
                if (claim != null)
                {
                    submitClaim(claim);
                    submitted++;
                }
            }
            catch (RuntimeException ex)
            {
                if (claim == null)
                {
                    String code = ex instanceof DriveException driveException
                            ? driveException.getBusinessCode()
                            : DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE;
                    logService.failure(DriveConstants.ACTION_CLEANUP, context, root,
                            code, null, null);
                }
                log.error("drive_cleanup_dispatch_failed requestId={} trashRootId={} failureType={}",
                        context.requestId(), root.getNodeId(), ex.getClass().getSimpleName());
                metrics.recordCleanupFailure("dispatch");
            }
        }
        return submitted;
    }

    private void submitClaim(DrivePurgeClaim claim)
    {
        try
        {
            purgeExecutor.execute(() -> worker.execute(claim));
        }
        catch (RuntimeException ex)
        {
            try
            {
                persistence.markFailed(claim);
            }
            catch (RuntimeException markFailure)
            {
                metrics.recordCleanupFailure("mark_failed");
                log.error("drive_purge_rejection_mark_failed requestId={} trashRootId={} failureType={}",
                        claim.auditContext().requestId(), claim.trashRootId(),
                        markFailure.getClass().getSimpleName());
            }
            logService.failure(claim.auditAction(), claim.auditContext(), claim.auditNode(),
                    DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE, null,
                    "count=" + claim.rowCount());
            log.error("drive_purge_executor_rejected requestId={} trashRootId={} failureType={}",
                    claim.auditContext().requestId(), claim.trashRootId(),
                    ex.getClass().getSimpleName());
            metrics.recordCleanupFailure("executor_rejected");
            throw storageUnavailable();
        }
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

    private static boolean isVisibleTrashRoot(DriveNode root, Long spaceId)
    {
        return root != null && Objects.equals(root.getNodeId(), root.getTrashRootId())
                && Objects.equals(spaceId, root.getSpaceId())
                && (DriveConstants.STATUS_TRASHED.equals(root.getStatus())
                || DriveConstants.STATUS_PURGING.equals(root.getStatus())
                || DriveConstants.STATUS_PURGE_FAILED.equals(root.getStatus()));
    }

    private static boolean isPurgeableRoot(DriveNode root, Long trashRootId)
    {
        return root != null && Objects.equals(root.getNodeId(), trashRootId)
                && Objects.equals(root.getTrashRootId(), trashRootId)
                && (DriveConstants.STATUS_TRASHED.equals(root.getStatus())
                || DriveConstants.STATUS_PURGING.equals(root.getStatus())
                || DriveConstants.STATUS_PURGE_FAILED.equals(root.getStatus()));
    }

    private static boolean isActiveFolderInSpace(DriveNode node, Long spaceId)
    {
        return node != null && Objects.equals(node.getSpaceId(), spaceId)
                && DriveConstants.STATUS_ACTIVE.equals(node.getStatus())
                && Objects.equals(1, node.getActiveFlag())
                && DriveConstants.NODE_FOLDER.equals(node.getNodeType());
    }

    private static void requireActiveNode(DriveNode node)
    {
        if (node == null || !DriveConstants.STATUS_ACTIVE.equals(node.getStatus())
                || !Objects.equals(1, node.getActiveFlag())
                || (!DriveConstants.NODE_FILE.equals(node.getNodeType())
                && !DriveConstants.NODE_FOLDER.equals(node.getNodeType())))
        {
            throw nodeNotFound();
        }
    }

    private static void requireVersion(DriveNode node, Integer version)
    {
        if (version == null || node.getVersion() == null
                || !Objects.equals(node.getVersion(), version))
        {
            throw concurrentModification();
        }
    }

    private static void requireSameActiveSpace(DriveSpace expected, DriveSpace locked)
    {
        if (locked == null || !Objects.equals(expected.getSpaceId(), locked.getSpaceId())
                || !DriveConstants.STATUS_ACTIVE.equals(locked.getStatus()))
        {
            throw new DriveException(DriveErrorCodes.DRIVE_SPACE_NOT_FOUND,
                    "云盘空间不存在");
        }
    }

    private void recordFailure(String action, DriveActor actor, DriveNode node, String code)
    {
        if (node == null)
        {
            logService.failure(action, actor, code);
        }
        else
        {
            logService.failure(action, actor, node, code, null, null);
        }
    }

    private void recordPurgeFailure(DriveAuditContext context, DriveActor actor,
            DriveNode node, String code)
    {
        if (context != null && node != null)
        {
            logService.failure(DriveConstants.ACTION_PURGE, context,
                    node, code, null, null);
        }
        else
        {
            recordFailure(DriveConstants.ACTION_PURGE, actor, node, code);
        }
    }

    private static Date staleBefore(Date now)
    {
        return new Date(now.getTime() - PURGE_STALE_MILLIS);
    }

    private static DriveAuditContext systemContext(Long trashRootId)
    {
        return new DriveAuditContext(0L, null, "system",
                "cleanup-" + trashRootId + "-" + UUID.randomUUID(), "", "drive-cleanup");
    }

    private static String safeUsername(DriveActor actor)
    {
        return actor == null || actor.username() == null ? "" : actor.username();
    }

    private static DriveException nodeNotFound()
    {
        return new DriveException(DriveErrorCodes.DRIVE_NODE_NOT_FOUND,
                "回收批次不存在");
    }

    private static DriveException concurrentModification()
    {
        return new DriveException(DriveErrorCodes.DRIVE_CONCURRENT_MODIFICATION,
                "回收批次已被其他操作更新，请刷新后重试");
    }

    private static DriveException nameConflict()
    {
        return new DriveException(DriveErrorCodes.DRIVE_NAME_CONFLICT,
                "恢复位置存在同名文件或文件夹");
    }

    private static DriveException storageUnavailable()
    {
        return new DriveException(DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE,
                "回收站清理服务暂时不可用，请稍后重试");
    }
}
