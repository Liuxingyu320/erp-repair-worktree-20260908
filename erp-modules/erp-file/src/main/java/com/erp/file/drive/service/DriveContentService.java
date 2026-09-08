package com.erp.file.drive.service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.NoSuchFileException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.domain.DriveNode;
import com.erp.file.drive.domain.DriveSpace;
import com.erp.file.drive.domain.vo.DriveContent;
import com.erp.file.drive.exception.DriveException;
import com.erp.file.drive.mapper.DriveNodeMapper;
import com.erp.file.drive.storage.DriveStorageProvider;
import com.erp.file.drive.storage.DriveStoredObject;
import com.erp.system.api.domain.DriveBusinessFile;
import org.springframework.stereotype.Service;

@Service
public class DriveContentService
{
    private final DriveNodeMapper nodeMapper;
    private final DriveSpaceService spaceService;
    private final DriveAuthorizationService authorization;
    private final DriveFilePolicy filePolicy;
    private final DriveStorageProvider storage;
    private final DriveOperationLogService logService;

    public DriveContentService(DriveNodeMapper nodeMapper, DriveSpaceService spaceService,
            DriveAuthorizationService authorization, DriveFilePolicy filePolicy,
            DriveStorageProvider storage, DriveOperationLogService logService)
    {
        this.nodeMapper = nodeMapper;
        this.spaceService = spaceService;
        this.authorization = authorization;
        this.filePolicy = filePolicy;
        this.storage = storage;
        this.logService = logService;
    }

    public DriveContent resolve(Long nodeId, String mode, DriveActor actor)
    {
        return resolve(nodeId, mode, actor, true);
    }

    /**
     * 业务服务已对关联记录授权后的内部读取入口。
     */
    public DriveContent resolveBusiness(Long nodeId, String mode,
            DriveActor actor)
    {
        return resolve(nodeId, mode, actor, false);
    }

    /**
     * 绑定业务记录前校验当前用户的云盘读权限与存储完整性。
     */
    public DriveBusinessFile validateBusinessBinding(Long nodeId,
            DriveActor actor)
    {
        DriveNode node = nodeId == null ? null : nodeMapper.selectById(nodeId);
        if (node == null)
        {
            throw nodeNotFound();
        }
        DriveSpace space = spaceService.requireSpace(node.getSpaceId());
        authorization.requireRead(actor, space);
        requireActiveFile(node);
        DriveStoredObject stored = openMatchingObject(node);
        try
        {
            requireMatchingSha256(node, stored);
        }
        finally
        {
            closeQuietly(stored);
        }

        DriveBusinessFile result = new DriveBusinessFile();
        result.setNodeId(node.getNodeId());
        result.setFileName(node.getNodeName());
        result.setContentType(node.getContentType());
        result.setSize(node.getSizeBytes());
        return result;
    }

    private DriveContent resolve(Long nodeId, String mode, DriveActor actor,
            boolean requireDriveAuthorization)
    {
        String action = actionFor(mode);
        DriveNode node = null;
        try
        {
            node = nodeId == null ? null : nodeMapper.selectById(nodeId);
            if (node == null)
            {
                throw nodeNotFound();
            }

            DriveSpace space = spaceService.requireSpace(node.getSpaceId());
            if (requireDriveAuthorization)
            {
                authorization.requireRead(actor, space);
            }
            requireActiveFile(node);
            if (DriveConstants.ACTION_PREVIEW.equals(action)
                    && !filePolicy.isPreviewable(node.getExtension(), node.getContentType()))
            {
                throw new DriveException(DriveErrorCodes.DRIVE_PREVIEW_UNSUPPORTED,
                        "该文件类型不支持在线预览");
            }

            DriveStoredObject stored = openMatchingObject(node);
            DriveContent content = new DriveContent(stored.resource(), stored.size(),
                    node.getNodeName(), node.getContentType(),
                    DriveConstants.ACTION_PREVIEW.equals(action));
            logService.success(action, actor, node, null, node.getNodeName());
            return content;
        }
        catch (DriveException ex)
        {
            recordFailure(action, actor, node, ex.getBusinessCode());
            throw ex;
        }
        catch (RuntimeException ex)
        {
            DriveException translated = storageUnavailable();
            recordFailure(action, actor, node, translated.getBusinessCode());
            throw translated;
        }
    }

    private DriveStoredObject open(String storageKey)
    {
        if (storageKey == null || storageKey.isBlank())
        {
            throw storageUnavailable();
        }
        try
        {
            return storage.open(storageKey);
        }
        catch (NoSuchFileException | FileNotFoundException ex)
        {
            throw new DriveException(DriveErrorCodes.DRIVE_STORAGE_OBJECT_MISSING,
                    "文件内容不存在");
        }
        catch (IOException | RuntimeException ex)
        {
            throw storageUnavailable();
        }
    }

    private DriveStoredObject openMatchingObject(DriveNode node)
    {
        DriveStoredObject stored = open(node.getStorageKey());
        try
        {
            requireMatchingObject(node, stored);
            return stored;
        }
        catch (RuntimeException ex)
        {
            closeQuietly(stored);
            throw ex;
        }
    }

    private static void requireActiveFile(DriveNode node)
    {
        if (!DriveConstants.STATUS_ACTIVE.equals(node.getStatus())
                || !Objects.equals(1, node.getActiveFlag())
                || !DriveConstants.NODE_FILE.equals(node.getNodeType()))
        {
            throw nodeNotFound();
        }
        if (node.getSizeBytes() == null || node.getSizeBytes() < 0)
        {
            throw storageUnavailable();
        }
    }

    private static void requireMatchingObject(DriveNode node, DriveStoredObject stored)
    {
        if (stored == null || stored.resource() == null || stored.size() < 0
                || !Objects.equals(node.getSizeBytes(), stored.size()))
        {
            throw storageUnavailable();
        }
    }

    /**
     * Business binding is the integrity checkpoint: when an object has a persisted
     * digest, verify the physical bytes before another business record can trust it.
     * Ordinary preview/download deliberately stays single-pass and only streams the
     * already-authorized resource to the caller.
     */
    private static void requireMatchingSha256(DriveNode node,
            DriveStoredObject stored)
    {
        String expected = node.getSha256();
        if (expected == null || expected.isBlank())
        {
            return;
        }

        try
        {
            byte[] expectedDigest = HexFormat.of().parseHex(expected.trim());
            if (expectedDigest.length != 32)
            {
                throw storageUnavailable();
            }

            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream input = new DigestInputStream(
                    stored.resource().getInputStream(), sha256))
            {
                input.transferTo(OutputStream.nullOutputStream());
            }

            if (!MessageDigest.isEqual(expectedDigest, sha256.digest()))
            {
                throw storageUnavailable();
            }
        }
        catch (DriveException ex)
        {
            throw ex;
        }
        catch (IOException | NoSuchAlgorithmException | RuntimeException ex)
        {
            throw storageUnavailable();
        }
    }

    private static void closeQuietly(DriveStoredObject stored)
    {
        if (stored == null)
        {
            return;
        }
        try
        {
            stored.close();
        }
        catch (IOException | RuntimeException ignored)
        {
            // The client-safe storage error remains the primary failure.
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

    private static String actionFor(String mode)
    {
        if ("preview".equals(mode))
        {
            return DriveConstants.ACTION_PREVIEW;
        }
        if ("download".equals(mode))
        {
            return DriveConstants.ACTION_DOWNLOAD;
        }
        throw new DriveException(DriveErrorCodes.DRIVE_PREVIEW_UNSUPPORTED,
                "不支持的文件内容访问模式");
    }

    private static DriveException nodeNotFound()
    {
        return new DriveException(DriveErrorCodes.DRIVE_NODE_NOT_FOUND, "云盘节点不存在");
    }

    private static DriveException storageUnavailable()
    {
        return new DriveException(DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE,
                "文件内容暂时不可用，请稍后重试");
    }
}
