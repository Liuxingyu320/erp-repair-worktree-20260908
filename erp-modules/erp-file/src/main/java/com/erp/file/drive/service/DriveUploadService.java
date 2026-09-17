package com.erp.file.drive.service;

import com.erp.common.core.utils.file.UploadImageNormalizer;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.DigestInputStream;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Date;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.domain.DriveNode;
import com.erp.file.drive.domain.DriveSpace;
import com.erp.file.drive.domain.vo.DriveNodeVo;
import com.erp.file.drive.exception.DriveException;
import com.erp.file.drive.exception.DriveUploadPreClaimRejectedException;
import com.erp.file.drive.metric.DriveMetrics;
import com.erp.file.drive.storage.DriveStorageProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DriveUploadService
{
    private static final Logger log = LoggerFactory.getLogger(DriveUploadService.class);
    private static final int MAX_NAME_ATTEMPTS = 10;
    private static final long[] COMPENSATION_BACKOFF_MS = {0L, 50L, 200L};

    private final DriveStorageProvider storage;
    private final DriveUploadPersistence persistence;
    private final DriveNodeService nodeService;
    private final DriveSpaceService spaceService;
    private final DriveQuotaService quotaService;
    private final DriveUploadReservationService reservationService;
    private final DriveFilePolicy filePolicy;
    private final DriveNamePolicy namePolicy;
    private final DriveOperationLogService logService;
    private final DriveMetrics metrics;

    public DriveUploadService(DriveStorageProvider storage, DriveUploadPersistence persistence,
            DriveNodeService nodeService, DriveSpaceService spaceService,
            DriveQuotaService quotaService,
            DriveUploadReservationService reservationService, DriveFilePolicy filePolicy,
            DriveNamePolicy namePolicy, DriveOperationLogService logService,
            DriveMetrics metrics)
    {
        this.storage = storage;
        this.persistence = persistence;
        this.nodeService = nodeService;
        this.spaceService = spaceService;
        this.quotaService = quotaService;
        this.reservationService = reservationService;
        this.filePolicy = filePolicy;
        this.namePolicy = namePolicy;
        this.logService = logService;
        this.metrics = metrics;
    }

    private DriveUploadOperationService operations;

    @org.springframework.beans.factory.annotation.Autowired
    public void setUploadOperations(DriveUploadOperationService operations)
    {
        this.operations = operations;
    }

    public DriveUploadOperationService.Receipt uploadWithReceipt(MultipartFile file, Long spaceId,
            Long parentId, DriveActor actor, String operationId)
    {
        Long effectiveParent = parentId == null ? DriveConstants.ROOT_PARENT_ID : parentId;
        String name;
        String hash;
        try
        {
            if (file == null || file.isEmpty())
                throw new DriveException(DriveErrorCodes.DRIVE_FILE_TYPE_REJECTED, "文件不能为空");
            spaceService.requireWritableSpace(spaceId, actor);
            nodeService.requireParent(spaceId, effectiveParent);
            filePolicy.validate(file.getOriginalFilename(), file.getContentType(), file.getSize());
            name = namePolicy.normalizeDisplayName(file.getOriginalFilename());
            MessageDigest digest = messageDigest();
            try (DigestInputStream input = new DigestInputStream(file.getInputStream(), digest))
            {
                input.transferTo(java.io.OutputStream.nullOutputStream());
            }
            hash = HexFormat.of().formatHex(digest.digest());
        }
        catch (DriveException | IOException ex)
        {
            // Preserve an earlier durable outcome even when a later request fails preflight.
            // Receipt lookup failure is UNKNOWN and must not produce a pre-claim rejection marker.
            DriveUploadOperationService.Receipt existing = operations.receipt(operationId, actor);
            if (!"NOT_OBSERVED".equals(existing.status())) return existing;
            throw new DriveUploadPreClaimRejectedException(operationId, translateUploadFailure(ex));
        }
        DriveUploadOperationService.Claim claim = operations.claim(operationId, actor,
                spaceId, effectiveParent, name, file.getSize(), hash);
        if (!claim.acquired()) return operations.receipt(operationId, actor);
        long startedAt = metrics.start();
        try
        {
            doUpload(file, spaceId, effectiveParent, actor, claim, hash);
            metrics.recordUploadSuccess(startedAt);
        }
        catch (RuntimeException ex)
        {
            DriveException translated = translateUploadFailure(ex);
            metrics.recordUploadFailure(startedAt, translated.getBusinessCode());
            logService.failure(DriveConstants.ACTION_UPLOAD, actor, translated.getBusinessCode());
            throw translated;
        }
        return operations.receipt(operationId, actor);
    }

    public DriveUploadOperationService.Receipt uploadReceipt(String operationId, DriveActor actor)
    {
        return operations.receipt(operationId, actor);
    }

    public DriveNodeVo upload(MultipartFile file, Long spaceId, Long parentId, DriveActor actor)
    {
        long startedAt = metrics.start();
        try
        {
            DriveNodeVo result = doUpload(file, spaceId, parentId, actor);
            metrics.recordUploadSuccess(startedAt);
            return result;
        }
        catch (DriveException ex)
        {
            metrics.recordUploadFailure(startedAt, ex.getBusinessCode());
            logService.failure(DriveConstants.ACTION_UPLOAD, actor, ex.getBusinessCode());
            throw ex;
        }
        catch (RuntimeException ex)
        {
            DriveException translated = translateUploadFailure(ex);
            metrics.recordUploadFailure(startedAt, translated.getBusinessCode());
            logService.failure(DriveConstants.ACTION_UPLOAD, actor, translated.getBusinessCode());
            throw translated;
        }
    }

    private DriveNodeVo doUpload(MultipartFile file, Long spaceId, Long parentId, DriveActor actor)
    {
        return doUpload(file, spaceId, parentId, actor, null, null);
    }

    private DriveNodeVo doUpload(MultipartFile file, Long spaceId, Long parentId, DriveActor actor,
            DriveUploadOperationService.Claim claim, String expectedHash)
    {
        String storageKey = null;
        String reservationId = null;
        boolean putStarted = false;
        boolean putCompleted = false;
        DriveNode persisted = null;
        try
        {
            if (file == null || file.isEmpty())
                throw new DriveException(DriveErrorCodes.DRIVE_FILE_TYPE_REJECTED, "文件不能为空");
            Long effectiveParent = parentId == null ? DriveConstants.ROOT_PARENT_ID : parentId;
            DriveSpace space = spaceService.requireWritableSpace(spaceId, actor);
            DriveNode parent = nodeService.requireParent(spaceId, effectiveParent);
            filePolicy.validate(file.getOriginalFilename(), file.getContentType(), file.getSize());
            if (UploadImageNormalizer.isImage(file.getOriginalFilename(), file.getContentType()))
            {
                file = UploadImageNormalizer.normalize(file, expectedHash);
                // Bind the bytes actually handed to storage, in addition to the original request claim.
                try (InputStream input = file.getInputStream())
                { expectedHash = HexFormat.of().formatHex(messageDigest().digest(input.readAllBytes())); }
            }
            quotaService.preflight(space, file.getSize());
            String originalName = namePolicy.normalizeDisplayName(file.getOriginalFilename());
            String extension = filePolicy.extension(originalName);
            storageKey = storageKey(extension);
            reservationId = reservationService.reserveBeforeStorage(spaceId, storageKey, file.getSize(), actor);
            MessageDigest sha256 = messageDigest();
            try (DigestInputStream input = new DigestInputStream(file.getInputStream(), sha256))
            {
                putStarted = true;
                storage.put(storageKey, input);
                putCompleted = true;
            }
            String hash = HexFormat.of().formatHex(sha256.digest());
            if (expectedHash != null && !expectedHash.equals(hash))
                throw new DriveException(DriveErrorCodes.DRIVE_CONCURRENT_MODIFICATION,
                        "上传内容已变化，请重新选择原文件");
            for (int attempt = 0; attempt < MAX_NAME_ATTEMPTS && persisted == null; attempt++)
            {
                String displayName = nodeService.availableFileName(spaceId, effectiveParent, originalName);
                DriveNode candidate = fileNode(space, parent, effectiveParent,
                        displayName, storageKey, file, extension, hash, actor);
                try
                {
                    persisted = claim == null ? persistence.persist(candidate, file.getSize(), reservationId)
                            : persistence.persist(candidate, file.getSize(), reservationId, claim);
                }
                catch (DuplicateKeyException ex)
                {
                    if (!isConstraint(ex, "uk_drive_node_active_name")) throw ex;
                }
            }
            if (persisted == null)
                throw new DriveException(DriveErrorCodes.DRIVE_NAME_CONFLICT, "同名文件较多，请重试");
        }
        catch (RuntimeException | IOException ex)
        {
            if (claim != null)
            {
                // A commit exception may arrive AFTER the DB committed. Never delete first.
                DriveUploadOperationService.Receipt receipt = operations.fenceForCleanup(claim);
                if ("SUCCEEDED".equals(receipt.status()))
                    return nodeService.detail(Long.valueOf(receipt.nodeId()), actor);
            }
            boolean absent = storageKey == null || compensateDelete(storageKey, actor);
            // An interrupted remote PUT can still finish later. Absence now does not make it retryable.
            boolean safelyAbsent = absent && (!putStarted || putCompleted);
            if (reservationId != null) settleReservation(reservationId, claim == null ? absent : safelyAbsent, actor);
            if (claim != null) operations.finishCleanup(claim, safelyAbsent);
            throw translateUploadFailure(ex);
        }
        logService.success(DriveConstants.ACTION_UPLOAD, actor, persisted, null, persisted.getNodeName());
        return nodeService.toVo(persisted, actor);
    }

    private DriveNode fileNode(DriveSpace space, DriveNode parent, Long parentId,
            String displayName, String storageKey, MultipartFile file,
            String extension, String hash, DriveActor actor)
    {
        DriveNode node = new DriveNode();
        node.setSpaceId(space.getSpaceId());
        node.setParentId(parentId);
        node.setAncestors(parent == null ? "0" : DriveNodeService.childAncestors(parent));
        node.setNodeType(DriveConstants.NODE_FILE);
        node.setNodeName(displayName);
        node.setNormalizedName(namePolicy.normalizedKey(displayName));
        node.setExtension(extension);
        node.setStorageKey(storageKey);
        node.setContentType(normalizedContentType(file.getContentType()));
        node.setSizeBytes(file.getSize());
        node.setSha256(hash);
        node.setStatus(DriveConstants.STATUS_ACTIVE);
        node.setActiveFlag(1);
        node.setVersion(0);
        node.setCreateBy(safeUsername(actor));
        node.setCreateTime(new Date());
        node.setUpdateBy(safeUsername(actor));
        node.setUpdateTime(new Date());
        return node;
    }

    private boolean compensateDelete(String storageKey, DriveActor actor)
    {
        RuntimeException lastRuntime = null;
        IOException lastIo = null;
        for (int attempt = 0; attempt < COMPENSATION_BACKOFF_MS.length; attempt++)
        {
            long delay = COMPENSATION_BACKOFF_MS[attempt];
            if (delay > 0 && !sleep(delay))
            {
                break;
            }
            try
            {
                if (!storage.exists(storageKey))
                {
                    return true;
                }
                storage.delete(storageKey);
                if (!storage.exists(storageKey)) return true;
            }
            catch (IOException ex)
            {
                lastIo = ex;
            }
            catch (RuntimeException ex)
            {
                lastRuntime = ex;
            }
        }
        String requestId = "unknown";
        try
        {
            requestId = logService.captureContext(actor).requestId();
        }
        catch (RuntimeException ignored)
        {
            // Compensation alerts must never replace the original client-safe failure.
        }
        Throwable cause = lastIo != null ? lastIo : lastRuntime;
        String failureType = cause == null ? "unknown" : cause.getClass().getSimpleName();
        log.error("drive_storage_compensation_failed requestId={} keyFingerprint={} failureType={}",
                requestId, fingerprint(storageKey), failureType);
        metrics.recordStorageFailure("compensation_delete");
        return false;
    }

    private void settleReservation(String reservationId, boolean objectAbsent,
            DriveActor actor)
    {
        reservationService.settleAfterCompensation(reservationId, objectAbsent, actor);
    }

    private static boolean sleep(long millis)
    {
        try
        {
            Thread.sleep(millis);
            return true;
        }
        catch (InterruptedException ex)
        {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String storageKey(String extension)
    {
        LocalDate now = LocalDate.now();
        return String.format(Locale.ROOT, "%04d/%02d/%s.%s",
                now.getYear(), now.getMonthValue(), UUID.randomUUID(), extension);
    }

    private static MessageDigest messageDigest()
    {
        try
        {
            return MessageDigest.getInstance("SHA-256");
        }
        catch (NoSuchAlgorithmException ex)
        {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String fingerprint(String storageKey)
    {
        MessageDigest digest = messageDigest();
        return HexFormat.of().formatHex(digest.digest(
                storageKey.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private static boolean isConstraint(Throwable error, String constraint)
    {
        String expected = constraint.toLowerCase(Locale.ROOT);
        for (Throwable current = error; current != null; current = current.getCause())
        {
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains(expected))
            {
                return true;
            }
        }
        return false;
    }

    private static DriveException translateUploadFailure(Throwable error)
    {
        if (error instanceof DriveException driveException)
        {
            return driveException;
        }
        if (error instanceof UploadImageNormalizer.ContentChangedException)
            return new DriveException(DriveErrorCodes.DRIVE_CONCURRENT_MODIFICATION, error.getMessage());
        if (error instanceof com.erp.common.core.exception.ServiceException serviceException)
            return new DriveException(DriveErrorCodes.DRIVE_FILE_TYPE_REJECTED, serviceException.getMessage());
        return new DriveException(DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE,
                "云盘上传失败，请稍后重试");
    }

    private static String normalizedContentType(String contentType)
    {
        if (contentType == null || contentType.isBlank())
        {
            return "application/octet-stream";
        }
        int parameters = contentType.indexOf(';');
        return (parameters >= 0 ? contentType.substring(0, parameters) : contentType)
                .trim().toLowerCase(Locale.ROOT);
    }

    private static String safeUsername(DriveActor actor)
    {
        return actor == null || actor.username() == null ? "" : actor.username();
    }
}
