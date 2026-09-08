package com.erp.oa.service.impl;

import java.util.Date;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.oa.constant.OaSignFileEvidenceType;
import com.erp.oa.domain.OaSignFileEvidence;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPackageDocument;
import com.erp.oa.domain.vo.SignedPdfResult;
import com.erp.oa.domain.vo.StagedSignFile;
import com.erp.oa.mapper.OaSignFileEvidenceMapper;

/**
 * Persists immutable file-evidence rows and their source relationships.
 *
 * <p>The store does not generate files, mutate package state, or own transaction
 * boundaries. Callers remain responsible for producing and cleaning up physical
 * artifacts.</p>
 */
final class OaSignPackageEvidenceStore
{
    private final OaSignFileEvidenceMapper evidenceMapper;
    private final OaSignPlacementPolicyService placementPolicyService;

    OaSignPackageEvidenceStore(OaSignFileEvidenceMapper evidenceMapper,
            OaSignPlacementPolicyService placementPolicyService)
    {
        this.evidenceMapper = evidenceMapper;
        this.placementPolicyService = placementPolicyService;
    }

    void recordTaskSignatureSample(OaSignPackage signPackage,
            StagedSignFile sample, Long dataRequestId, Date capturedTime)
    {
        OaSignFileEvidence evidence = new OaSignFileEvidence();
        evidence.setPackageId(signPackage.getPackageId());
        evidence.setDocumentVersion("SAMPLE-" + dataRequestId);
        evidence.setEvidenceType(OaSignFileEvidenceType.SIGNATURE_SAMPLE);
        evidence.setFileUrl(sample.getArchiveRelativePath());
        evidence.setFileHash(sample.getFileHash());
        evidence.setFileSize(sample.getFileSize());
        evidence.setGeneratedTime(capturedTime);
        evidenceMapper.insertOaSignFileEvidence(evidence);
    }

    void recordGeneratedDocument(OaSignPackage signPackage,
            OaSignPackageDocument document, GeneratedSignDocument generated,
            Date generatedTime)
    {
        OaSignFileEvidence renderedSource = new OaSignFileEvidence();
        renderedSource.setPackageId(signPackage.getPackageId());
        renderedSource.setDocumentId(document.getDocumentId());
        renderedSource.setDocumentVersion(document.getDocumentVersion());
        renderedSource.setEvidenceType(OaSignFileEvidenceType.RENDERED_SOURCE);
        renderedSource.setFileUrl(
                StringUtils.isNotBlank(generated.getSourceArchiveRelativePath())
                        ? generated.getSourceArchiveRelativePath()
                        : generated.getSourceFileUrl());
        renderedSource.setFileHash(generated.getSourceFileHash());
        renderedSource.setFileSize(generated.getSourceFileSize());
        renderedSource.setGeneratedTime(generatedTime);
        evidenceMapper.insertOaSignFileEvidence(renderedSource);

        OaSignFileEvidence reviewPdf = new OaSignFileEvidence();
        reviewPdf.setPackageId(signPackage.getPackageId());
        reviewPdf.setDocumentId(document.getDocumentId());
        reviewPdf.setDocumentVersion(document.getDocumentVersion());
        reviewPdf.setEvidenceType(OaSignFileEvidenceType.REVIEW_PDF);
        reviewPdf.setFileUrl(
                StringUtils.isNotBlank(generated.getReviewPdfArchiveRelativePath())
                        ? generated.getReviewPdfArchiveRelativePath()
                        : generated.getReviewPdfUrl());
        reviewPdf.setFileHash(generated.getReviewPdfHash());
        reviewPdf.setFileSize(generated.getReviewPdfSize());
        reviewPdf.setSourceEvidenceId(renderedSource.getEvidenceId());
        reviewPdf.setGeneratedTime(generatedTime);
        evidenceMapper.insertOaSignFileEvidence(reviewPdf);
    }

    void recordSigned(OaSignPackage signPackage,
            OaSignPackageDocument document, SignedPdfResult result,
            GeneratedSignDocument certificate, Date generatedTime)
    {
        OaSignFileEvidence reviewEvidence = evidenceMapper.selectEvidenceByType(
                document.getDocumentId(), document.getDocumentVersion(),
                OaSignFileEvidenceType.REVIEW_PDF);
        insert(signPackage, document, OaSignFileEvidenceType.SIGNATURE_IMAGE,
                result.getSignatureArchiveRelativePath(), result.getSignatureHash(),
                result.getSignatureFileSize(), null, generatedTime);
        insert(signPackage, document, OaSignFileEvidenceType.SIGNED_PDF,
                result.getArchiveRelativePath(), result.getSignedPdfHash(),
                result.getSignedPdfSize(),
                reviewEvidence == null ? null : reviewEvidence.getEvidenceId(),
                generatedTime);
        insert(signPackage, document, OaSignFileEvidenceType.SIGN_CERTIFICATE,
                StringUtils.isNotBlank(certificate.getSourceArchiveRelativePath())
                        ? certificate.getSourceArchiveRelativePath()
                        : certificate.getFileUrl(),
                certificate.getSha256(), certificate.getSourceFileSize(),
                null, generatedTime);
    }

    void recordFinalCandidate(OaSignPackage signPackage,
            OaSignPackageDocument document, GeneratedSignDocument generated,
            SignedPdfResult result, StagedSignFile sealArchive,
            Date generatedTime)
    {
        OaSignFileEvidence renderedSource = new OaSignFileEvidence();
        renderedSource.setPackageId(signPackage.getPackageId());
        renderedSource.setDocumentId(document.getDocumentId());
        renderedSource.setDocumentVersion(signPackage.getDocumentVersion());
        renderedSource.setEvidenceType(
                OaSignFileEvidenceType.FINAL_RENDERED_SOURCE);
        renderedSource.setFileUrl(
                StringUtils.isNotBlank(generated.getSourceArchiveRelativePath())
                        ? generated.getSourceArchiveRelativePath()
                        : generated.getSourceFileUrl());
        renderedSource.setFileHash(generated.getSourceFileHash());
        renderedSource.setFileSize(generated.getSourceFileSize());
        renderedSource.setGeneratedTime(generatedTime);
        evidenceMapper.insertOaSignFileEvidence(renderedSource);

        OaSignFileEvidence reviewPdf = new OaSignFileEvidence();
        reviewPdf.setPackageId(signPackage.getPackageId());
        reviewPdf.setDocumentId(document.getDocumentId());
        reviewPdf.setDocumentVersion(signPackage.getDocumentVersion());
        reviewPdf.setEvidenceType(OaSignFileEvidenceType.FINAL_REVIEW_PDF);
        reviewPdf.setFileUrl(
                StringUtils.isNotBlank(generated.getReviewPdfArchiveRelativePath())
                        ? generated.getReviewPdfArchiveRelativePath()
                        : generated.getReviewPdfUrl());
        reviewPdf.setFileHash(generated.getReviewPdfHash());
        reviewPdf.setFileSize(generated.getReviewPdfSize());
        reviewPdf.setSourceEvidenceId(renderedSource.getEvidenceId());
        reviewPdf.setGeneratedTime(generatedTime);
        evidenceMapper.insertOaSignFileEvidence(reviewPdf);

        OaSignPackageDocument finalDocument = new OaSignPackageDocument();
        finalDocument.setDocumentId(document.getDocumentId());
        finalDocument.setPackageId(document.getPackageId());
        finalDocument.setDocumentVersion(signPackage.getDocumentVersion());
        if (placementPolicyService.requiresEmployeeSignature(document))
        {
            insert(signPackage, finalDocument,
                    OaSignFileEvidenceType.SIGNATURE_IMAGE,
                    result.getSignatureArchiveRelativePath(),
                    result.getSignatureHash(),
                    result.getSignatureFileSize(), null, generatedTime);
        }
        if (placementPolicyService.requiresCompanySeal(document))
        {
            if (sealArchive == null)
            {
                throw new ServiceException("企业章归档证据缺失");
            }
            insert(signPackage, finalDocument,
                    OaSignFileEvidenceType.COMPANY_SEAL,
                    sealArchive.getArchiveRelativePath(),
                    sealArchive.getFileHash(), sealArchive.getFileSize(),
                    null, generatedTime);
        }
        insert(signPackage, finalDocument,
                OaSignFileEvidenceType.FINAL_PENDING_PDF,
                result.getArchiveRelativePath(), result.getSignedPdfHash(),
                result.getSignedPdfSize(), reviewPdf.getEvidenceId(),
                generatedTime);
    }

    void recordCompanyFirstFinalCandidate(OaSignPackage signPackage,
            OaSignPackageDocument document, String finalVersion,
            SignedPdfResult result, StagedSignFile sealArchive,
            Date generatedTime)
    {
        OaSignFileEvidence sourceRendered = evidenceMapper.selectEvidenceByType(
                document.getDocumentId(), document.getDocumentVersion(),
                OaSignFileEvidenceType.RENDERED_SOURCE);
        OaSignFileEvidence sourceReview = evidenceMapper.selectEvidenceByType(
                document.getDocumentId(), document.getDocumentVersion(),
                OaSignFileEvidenceType.REVIEW_PDF);
        if (sourceRendered == null || sourceReview == null
                || StringUtils.isBlank(sourceRendered.getFileUrl())
                || StringUtils.isBlank(sourceRendered.getFileHash())
                || sourceRendered.getFileSize() == null
                || StringUtils.isBlank(sourceReview.getFileUrl())
                || StringUtils.isBlank(sourceReview.getFileHash())
                || sourceReview.getFileSize() == null)
        {
            throw new ServiceException("冻结待发文件证据不完整");
        }
        OaSignFileEvidence finalRendered = copyFinalSource(
                signPackage, document, finalVersion,
                OaSignFileEvidenceType.FINAL_RENDERED_SOURCE,
                sourceRendered, null, generatedTime);
        OaSignFileEvidence finalReview = copyFinalSource(
                signPackage, document, finalVersion,
                OaSignFileEvidenceType.FINAL_REVIEW_PDF,
                sourceReview, finalRendered.getEvidenceId(), generatedTime);

        OaSignPackageDocument finalDocument = new OaSignPackageDocument();
        finalDocument.setDocumentId(document.getDocumentId());
        finalDocument.setPackageId(document.getPackageId());
        finalDocument.setDocumentVersion(finalVersion);
        if (placementPolicyService.requiresCompanySeal(document))
        {
            if (sealArchive == null)
            {
                throw new ServiceException("企业章归档证据缺失");
            }
            insert(signPackage, finalDocument,
                    OaSignFileEvidenceType.COMPANY_SEAL,
                    sealArchive.getArchiveRelativePath(),
                    sealArchive.getFileHash(), sealArchive.getFileSize(),
                    null, generatedTime);
        }
        insert(signPackage, finalDocument,
                OaSignFileEvidenceType.FINAL_PENDING_PDF,
                result.getArchiveRelativePath(), result.getSignedPdfHash(),
                result.getSignedPdfSize(), finalReview.getEvidenceId(),
                generatedTime);
    }

    void recordFinalArchive(OaSignPackage signPackage,
            OaSignPackageDocument document, SignedPdfResult archive,
            Date generatedTime)
    {
        OaSignPackageDocument archiveDocument = new OaSignPackageDocument();
        archiveDocument.setDocumentId(document.getDocumentId());
        archiveDocument.setDocumentVersion(
                signPackage.getFinalDocumentVersion());
        insert(signPackage, archiveDocument,
                OaSignFileEvidenceType.FINAL_ARCHIVE_PDF,
                archive.getArchiveRelativePath(), archive.getSignedPdfHash(),
                archive.getSignedPdfSize(), null, generatedTime);
    }

    private OaSignFileEvidence copyFinalSource(OaSignPackage signPackage,
            OaSignPackageDocument document, String finalVersion,
            OaSignFileEvidenceType evidenceType, OaSignFileEvidence source,
            Long sourceEvidenceId, Date generatedTime)
    {
        OaSignFileEvidence evidence = new OaSignFileEvidence();
        evidence.setPackageId(signPackage.getPackageId());
        evidence.setDocumentId(document.getDocumentId());
        evidence.setDocumentVersion(finalVersion);
        evidence.setEvidenceType(evidenceType);
        evidence.setFileUrl(source.getFileUrl());
        evidence.setFileHash(source.getFileHash());
        evidence.setFileSize(source.getFileSize());
        evidence.setSourceEvidenceId(sourceEvidenceId);
        evidence.setGeneratedTime(generatedTime);
        evidenceMapper.insertOaSignFileEvidence(evidence);
        return evidence;
    }

    private void insert(OaSignPackage signPackage,
            OaSignPackageDocument document,
            OaSignFileEvidenceType evidenceType, String fileUrl,
            String fileHash, long fileSize, Long sourceEvidenceId,
            Date generatedTime)
    {
        OaSignFileEvidence evidence = new OaSignFileEvidence();
        evidence.setPackageId(signPackage.getPackageId());
        evidence.setDocumentId(document.getDocumentId());
        evidence.setDocumentVersion(document.getDocumentVersion());
        evidence.setEvidenceType(evidenceType);
        evidence.setFileUrl(fileUrl);
        evidence.setFileHash(fileHash);
        evidence.setFileSize(fileSize);
        evidence.setSourceEvidenceId(sourceEvidenceId);
        evidence.setGeneratedTime(generatedTime);
        evidenceMapper.insertOaSignFileEvidence(evidence);
    }
}
