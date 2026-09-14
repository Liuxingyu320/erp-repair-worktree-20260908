package com.erp.file.drive.service;

import java.util.Objects;
import java.util.UUID;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.domain.DriveUploadOperation;
import com.erp.file.drive.exception.DriveException;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.mapper.DriveUploadOperationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** A receipt is durable; elapsed time and missing responses never authorize another writer. */
@Service
public class DriveUploadOperationService
{
    public record Claim(String operationId, String owner, boolean acquired) { }
    public record Receipt(String operationId, String status, String nodeId) { }

    private final DriveUploadOperationMapper mapper;
    private final DriveSpaceService spaces;
    private final TransactionTemplate independent;

    public DriveUploadOperationService(DriveUploadOperationMapper mapper, DriveSpaceService spaces,
            PlatformTransactionManager transactions)
    {
        this.mapper = mapper;
        this.spaces = spaces;
        this.independent = new TransactionTemplate(transactions);
        this.independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public Claim claim(String id, DriveActor actor, Long spaceId, Long parentId,
            String name, long size, String sha256)
    {
        validateId(id);
        String owner = UUID.randomUUID().toString();
        DriveUploadOperation proposed = new DriveUploadOperation();
        proposed.setOperationId(id);
        proposed.setActorId(actor.userId());
        proposed.setSpaceId(spaceId);
        proposed.setParentId(parentId);
        proposed.setFileName(name);
        proposed.setSizeBytes(size);
        proposed.setSha256(sha256);
        proposed.setOwner(owner);
        return independent.execute(tx -> {
            mapper.insertIfAbsent(proposed);
            DriveUploadOperation current = mapper.selectForUpdate(id);
            if (current == null || !Objects.equals(current.getActorId(), actor.userId())) throw denied();
            if (!Objects.equals(current.getSpaceId(), spaceId)
                    || !Objects.equals(current.getParentId(), parentId)
                    || !Objects.equals(current.getFileName(), name)
                    || !Objects.equals(current.getSizeBytes(), size)
                    || !Objects.equals(current.getSha256(), sha256))
                throw new DriveException(DriveErrorCodes.DRIVE_CONCURRENT_MODIFICATION,
                        "此上传编号已用于另一个文件或目录，请先核对原上传结果");
            if ("FAILED_SAFE".equals(current.getStatus()))
            {
                if (mapper.reclaim(id, owner) != 1) throw pending();
                return new Claim(id, owner, true);
            }
            return new Claim(id, owner, Objects.equals(current.getOwner(), owner)
                    && "PROCESSING".equals(current.getStatus()));
        });
    }

    public Receipt receipt(String id, DriveActor actor)
    {
        validateId(id);
        DriveUploadOperation row = mapper.selectById(id);
        // Not observed is NOT proof of failure: a request may still be hashing/claiming.
        if (row == null) return new Receipt(id, "NOT_OBSERVED", null);
        if (!Objects.equals(row.getActorId(), actor.userId())) throw denied();
        spaces.requireReadableSpace(row.getSpaceId(), actor);
        return publicReceipt(row);
    }

    /** Called inside the SAME transaction as node/quota persistence. */
    public void lockWriter(Claim claim)
    {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("upload receipt requires node transaction");
        DriveUploadOperation row = mapper.selectForUpdate(claim.operationId());
        if (row == null || !Objects.equals(row.getOwner(), claim.owner())
                || !"PROCESSING".equals(row.getStatus())) throw pending();
    }

    public void succeed(Claim claim, Long nodeId)
    {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || nodeId == null
                || mapper.changeStatus(claim.operationId(), claim.owner(), "PROCESSING", "SUCCEEDED", nodeId) != 1)
            throw pending();
    }

    /** Resolves an ambiguous commit BEFORE deletion, and fences a late node transaction. */
    public Receipt fenceForCleanup(Claim claim)
    {
        return independent.execute(tx -> {
            DriveUploadOperation row = mapper.selectForUpdate(claim.operationId());
            if (row == null || !Objects.equals(row.getOwner(), claim.owner())) throw pending();
            if ("SUCCEEDED".equals(row.getStatus())) return publicReceipt(row);
            if (!"PROCESSING".equals(row.getStatus())
                    || mapper.changeStatus(claim.operationId(), claim.owner(), "PROCESSING", "CLEANING", null) != 1)
                throw pending();
            return new Receipt(claim.operationId(), "CLEANING", null);
        });
    }

    public void finishCleanup(Claim claim, boolean safelyAbsent)
    {
        independent.executeWithoutResult(tx -> {
            if (mapper.changeStatus(claim.operationId(), claim.owner(), "CLEANING",
                    safelyAbsent ? "FAILED_SAFE" : "REVIEW_REQUIRED", null) != 1) throw pending();
        });
    }

    private static Receipt publicReceipt(DriveUploadOperation row)
    {
        return new Receipt(row.getOperationId(), row.getStatus(),
                row.getNodeId() == null ? null : row.getNodeId().toString());
    }

    private static void validateId(String id)
    {
        if (id == null || !id.matches("[A-Za-z0-9_-]{20,64}"))
            throw new DriveException(DriveErrorCodes.DRIVE_POLICY_INVALID, "上传编号无效");
    }

    private static DriveException denied()
    {
        return new DriveException(DriveErrorCodes.DRIVE_ACCESS_DENIED, "无权查询或继续此上传");
    }

    private static DriveException pending()
    {
        return new DriveException(DriveErrorCodes.DRIVE_CONCURRENT_MODIFICATION, "上传结果待核对，请查询原上传结果");
    }
}
