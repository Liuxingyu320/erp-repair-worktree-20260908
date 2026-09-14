package com.erp.oa.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.erp.common.core.exception.ServiceException;

/** Owns only four CREATE_NEW files of one legacy signing attempt; never sweeps a directory. */
final class OaLaborContractArchiveAttempt
{
    private static final Logger log = LoggerFactory.getLogger(OaLaborContractArchiveAttempt.class);
    private final String operationId = UUID.randomUUID().toString();
    private final Long contractId;
    private final List<OwnedFile> files = new ArrayList<>();

    OaLaborContractArchiveAttempt(Long contractId)
    {
        this.contractId = contractId;
    }

    void reserve(Path path) throws IOException
    {
        // Existing signed evidence or a previous orphan needs explicit investigation, never overwrite.
        try
        {
            Files.createFile(path);
        }
        catch (java.nio.file.FileAlreadyExistsException e)
        {
            throw new ServiceException("合同存在既有归档文件，请先核对历史签署记录和文件后再重试");
        }
        OwnedFile owned = new OwnedFile(path);
        files.add(owned);
        owned.fileKey = attributes(path).fileKey();
        if (owned.fileKey == null)
        {
            throw new ServiceException("归档存储不能验证文件归属，请联系管理员核对文件后再重试");
        }
    }

    void registerRollback()
    {
        if (TransactionSynchronizationManager.isSynchronizationActive())
        {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
            {
                @Override
                public void afterCompletion(int status)
                {
                    if (status != STATUS_COMMITTED) cleanup(null);
                }
            });
        }
    }

    void cleanup(Throwable original)
    {
        for (OwnedFile owned : files)
        {
            if (owned.removed) continue;
            try
            {
                if (!Files.exists(owned.path, LinkOption.NOFOLLOW_LINKS))
                {
                    owned.removed = true;
                    continue;
                }
                BasicFileAttributes current = attributes(owned.path);
                if (owned.fileKey == null || !current.isRegularFile()
                        || !Objects.equals(owned.fileKey, current.fileKey()))
                {
                    throw new IOException("file ownership no longer matches this attempt");
                }
                Files.delete(owned.path);
                owned.removed = true;
            }
            catch (IOException | RuntimeException failure)
            {
                if (original != null && original != failure) original.addSuppressed(failure);
                // Deliberately no employee/signature data; recovery must recheck DB references and ownership.
                log.error("LEGACY_SIGN_FILE_CLEANUP_PENDING operationId={} contractId={} file={} reason={}",
                        operationId, contractId, owned.path.getFileName(), failure.getClass().getSimpleName());
            }
        }
    }

    private static BasicFileAttributes attributes(Path path) throws IOException
    {
        return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }

    private static final class OwnedFile
    {
        private final Path path;
        private Object fileKey;
        private boolean removed;
        private OwnedFile(Path path) { this.path = path; }
    }
}
