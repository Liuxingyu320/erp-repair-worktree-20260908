package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.erp.oa.domain.OaSignFileEvidence;
import com.erp.oa.domain.vo.SignedPdfResult;
import com.erp.oa.domain.vo.StagedSignFile;

@DisplayName("签约包物理文件生命周期")
class OaSignPackageFileLifecycleTest
{
    private final OaSignFileStorageService fileStorageService =
            mock(OaSignFileStorageService.class);
    private final OaSignDocumentService documentService =
            mock(OaSignDocumentService.class);
    private final OaSignedPdfService signedPdfService =
            mock(OaSignedPdfService.class);
    private final OaSignPackageFileLifecycle lifecycle =
            new OaSignPackageFileLifecycle(
                    fileStorageService, documentService, signedPdfService);

    @AfterEach
    void clearTransactionSynchronization()
    {
        if (TransactionSynchronizationManager.isSynchronizationActive())
        {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("旧证据只在事务提交后删除且忽略不完整文件引用")
    void discardsObsoleteEvidenceOnlyAfterCommit()
    {
        OaSignFileEvidence valid = evidence("archive/old.pdf", "old-hash");
        OaSignFileEvidence missingHash = evidence("archive/keep.pdf", null);

        lifecycle.scheduleObsoleteEvidenceDeletion(List.of(valid, missingHash));
        verify(fileStorageService, never()).discardArchivedEvidence(
                "archive/old.pdf", "old-hash");

        TransactionSynchronizationManager.initSynchronization();
        lifecycle.scheduleObsoleteEvidenceDeletion(List.of(valid, missingHash));

        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations())
        {
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }
        verify(fileStorageService, never()).discardArchivedEvidence(
                "archive/old.pdf", "old-hash");

        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations())
        {
            synchronization.afterCommit();
        }
        verify(fileStorageService).discardArchivedEvidence(
                "archive/old.pdf", "old-hash");
        verify(fileStorageService, never()).discardArchivedEvidence(
                "archive/keep.pdf", null);
    }

    @Test
    @DisplayName("已提升的归档证据只在事务未提交时回收")
    void discardsPromotedEvidenceOnlyAfterRollback()
    {
        StagedSignFile archived = new StagedSignFile(
                Path.of("stage"), Path.of("stage/seal.png"),
                "archive/seal.png", "seal.png", "seal-hash", 8L);
        archived.markPromoted(Path.of("archive/seal.png"), "/private/seal.png");
        TransactionSynchronizationManager.initSynchronization();

        lifecycle.registerArchivedEvidenceRollbackCleanup(archived);
        TransactionSynchronization synchronization =
                TransactionSynchronizationManager.getSynchronizations().get(0);

        synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        verify(fileStorageService, never()).discardUncommitted(
                "archive/seal.png", "seal-hash");

        synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(fileStorageService).discardUncommitted(
                "archive/seal.png", "seal-hash");
    }

    @Test
    @DisplayName("生成文档、签名PDF和证书在回滚后统一回收")
    void discardsGeneratedArtifactsAfterRollback()
    {
        GeneratedSignDocument generated = generatedDocument("generated");
        GeneratedSignDocument certificate = generatedDocument("certificate");
        SignedPdfResult signed = signedPdf("signed");
        TransactionSynchronizationManager.initSynchronization();

        lifecycle.registerGeneratedDocumentRollbackCleanup(generated);
        lifecycle.registerSignedPdfRollbackCleanup(List.of(signed));
        lifecycle.registerCertificateRollbackCleanup(certificate);
        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations())
        {
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }

        verify(documentService).discardUncommitted(generated);
        verify(signedPdfService).discardUncommitted(signed);
        verify(documentService).discardUncommitted(certificate);
    }

    @Test
    @DisplayName("即时回收会尝试全部签名文件并把清理失败附加到原异常")
    void preservesAllSignedPdfCleanupFailures()
    {
        SignedPdfResult first = signedPdf("first");
        SignedPdfResult second = signedPdf("second");
        RuntimeException firstCleanup = new RuntimeException("first cleanup");
        RuntimeException secondCleanup = new RuntimeException("second cleanup");
        RuntimeException original = new RuntimeException("business failure");
        doThrow(firstCleanup).when(signedPdfService).discardUncommitted(first);
        doThrow(secondCleanup).when(signedPdfService).discardUncommitted(second);

        lifecycle.discardSignedPdfResults(List.of(first, second), original);

        verify(signedPdfService).discardUncommitted(first);
        verify(signedPdfService).discardUncommitted(second);
        assertThat(original.getSuppressed())
                .containsExactly(firstCleanup, secondCleanup);
    }

    @Test
    @DisplayName("回滚登记对后续集合修改使用防御性快照")
    void snapshotsSignedPdfResultsForRollback()
    {
        SignedPdfResult first = signedPdf("first");
        SignedPdfResult second = signedPdf("second");
        List<SignedPdfResult> results = new ArrayList<>();
        results.add(first);
        TransactionSynchronizationManager.initSynchronization();

        lifecycle.registerSignedPdfRollbackCleanup(results);
        results.add(second);
        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations())
        {
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }

        verify(signedPdfService).discardUncommitted(first);
        verify(signedPdfService, never()).discardUncommitted(second);
    }

    private OaSignFileEvidence evidence(String fileUrl, String fileHash)
    {
        OaSignFileEvidence evidence = new OaSignFileEvidence();
        evidence.setFileUrl(fileUrl);
        evidence.setFileHash(fileHash);
        return evidence;
    }

    private GeneratedSignDocument generatedDocument(String prefix)
    {
        return new GeneratedSignDocument(
                "/private/" + prefix + ".docx", prefix + "-source-hash",
                "/private/" + prefix + ".pdf", prefix + "-pdf-hash", "SP-1-V1",
                "archive/" + prefix + ".docx", 10L,
                "archive/" + prefix + ".pdf", 20L);
    }

    private SignedPdfResult signedPdf(String prefix)
    {
        return new SignedPdfResult(
                "archive/" + prefix + ".pdf", "/private/" + prefix + ".pdf",
                prefix + "-pdf-hash", 20L,
                "archive/" + prefix + ".png", "/private/" + prefix + ".png",
                prefix + "-signature-hash", 8L, null, 1);
    }
}
