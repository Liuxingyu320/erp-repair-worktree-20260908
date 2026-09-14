package com.erp.oa.service.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.erp.common.core.utils.StringUtils;
import com.erp.oa.domain.OaSignFileEvidence;
import com.erp.oa.domain.vo.SignedPdfResult;
import com.erp.oa.domain.vo.StagedSignFile;

/**
 * Coordinates physical sign-package files with database transaction outcomes.
 *
 * <p>Newly generated artifacts are discarded after rollback, while obsolete
 * committed evidence is discarded only after the replacing transaction commits.
 * The component deliberately owns no business state and does not alter database
 * records.</p>
 */
final class OaSignPackageFileLifecycle
{
    private static final Logger log =
            LoggerFactory.getLogger(OaSignPackageFileLifecycle.class);

    private final OaSignFileStorageService fileStorageService;
    private final OaSignDocumentService documentService;
    private final OaSignedPdfService signedPdfService;

    OaSignPackageFileLifecycle(OaSignFileStorageService fileStorageService,
            OaSignDocumentService documentService,
            OaSignedPdfService signedPdfService)
    {
        this.fileStorageService = fileStorageService;
        this.documentService = documentService;
        this.signedPdfService = signedPdfService;
    }

    void scheduleObsoleteEvidenceDeletion(List<OaSignFileEvidence> obsoleteEvidence)
    {
        if (obsoleteEvidence == null || obsoleteEvidence.isEmpty())
        {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive())
        {
            log.warn("签约草稿旧文件未进入事务提交回调，保留物理文件等待清理");
            return;
        }
        List<OaSignFileEvidence> evidenceSnapshot =
                new ArrayList<>(obsoleteEvidence);
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization()
                {
                    @Override
                    public void afterCommit()
                    {
                        for (OaSignFileEvidence evidence : evidenceSnapshot)
                        {
                            if (StringUtils.isBlank(evidence.getFileUrl())
                                    || StringUtils.isBlank(evidence.getFileHash()))
                            {
                                continue;
                            }
                            try
                            {
                                fileStorageService.discardArchivedEvidence(
                                        evidence.getFileUrl(), evidence.getFileHash());
                            }
                            catch (RuntimeException cleanupFailure)
                            {
                                log.error("签约草稿旧物理文件安全删除失败: {}",
                                        evidence.getFileUrl(), cleanupFailure);
                            }
                        }
                    }
                });
    }

    void registerArchivedEvidenceRollbackCleanup(StagedSignFile archivedFile)
    {
        if (archivedFile == null || archivedFile.getArchivePath() == null)
        {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive())
        {
            log.warn("归档证据文件未进入事务回滚回调，无法提供事务级物理文件清理");
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization()
                {
                    @Override
                    public void afterCompletion(int status)
                    {
                        if (status != TransactionSynchronization.STATUS_COMMITTED)
                        {
                            try
                            {
                                fileStorageService.discardUncommitted(
                                        archivedFile.getArchiveRelativePath(),
                                        archivedFile.getFileHash());
                            }
                            catch (RuntimeException cleanupFailure)
                            {
                                log.error("回滚印章证据归档文件失败", cleanupFailure);
                            }
                        }
                    }
                });
    }

    void registerSignedPdfRollbackCleanup(Collection<SignedPdfResult> results)
    {
        if (!TransactionSynchronizationManager.isSynchronizationActive())
        {
            log.warn("签名PDF未进入事务回滚回调；调用者必须在同步失败时回收本次生成文件");
            return;
        }
        List<SignedPdfResult> snapshot = List.copyOf(results);
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization()
                {
                    @Override
                    public void afterCompletion(int status)
                    {
                        if (status != TransactionSynchronization.STATUS_COMMITTED)
                        {
                            discardSignedPdfResults(snapshot, null);
                        }
                    }
                });
    }

    void registerCertificateRollbackCleanup(GeneratedSignDocument certificate)
    {
        if (certificate == null
                || !TransactionSynchronizationManager.isSynchronizationActive())
        {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization()
                {
                    @Override
                    public void afterCompletion(int status)
                    {
                        if (status != TransactionSynchronization.STATUS_COMMITTED)
                        {
                            discardGeneratedDocument(certificate, null);
                        }
                    }
                });
    }

    void registerGeneratedDocumentRollbackCleanup(GeneratedSignDocument generated)
    {
        if (generated == null)
        {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive())
        {
            log.warn("新生成签约文件未进入事务回滚回调，无法提供事务级物理文件清理");
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization()
                {
                    @Override
                    public void afterCompletion(int status)
                    {
                        if (status != TransactionSynchronization.STATUS_COMMITTED)
                        {
                            try
                            {
                                documentService.discardUncommitted(generated);
                            }
                            catch (RuntimeException cleanupFailure)
                            {
                                log.error("回滚新生成签约文件失败: {}",
                                        generated.getSourceFileUrl(), cleanupFailure);
                            }
                        }
                    }
                });
    }

    void discardSignedPdfResults(Collection<SignedPdfResult> results,
            Throwable originalFailure)
    {
        for (SignedPdfResult result : results)
        {
            try
            {
                signedPdfService.discardUncommitted(result);
            }
            catch (RuntimeException cleanupFailure)
            {
                log.error("SIGN_FILE_CLEANUP_PENDING archive={} expectedHash={} signature={} signatureHash={}",
                        result.getArchiveRelativePath(), result.getSignedPdfHash(),
                        result.getSignatureArchiveRelativePath(), result.getSignatureHash(), cleanupFailure);
                if (originalFailure != null)
                {
                    originalFailure.addSuppressed(cleanupFailure);
                }
            }
        }
    }

    void discardGeneratedDocument(GeneratedSignDocument generated,
            Throwable originalFailure)
    {
        try
        {
            documentService.discardUncommitted(generated);
        }
        catch (RuntimeException cleanupFailure)
        {
            if (originalFailure != null)
            {
                originalFailure.addSuppressed(cleanupFailure);
            }
        }
    }
}
