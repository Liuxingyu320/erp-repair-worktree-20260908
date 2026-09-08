package com.erp.oa.service;

import java.util.List;
import java.util.Date;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.dto.OaSignDocumentReadRequest;
import com.erp.oa.domain.dto.OaSignPackageSignRequest;
import com.erp.oa.domain.dto.OaSignPackageFinalizeRequest;
import com.erp.oa.domain.dto.OaSignFinalConfirmRequest;
import com.erp.oa.domain.dto.OaSignFinalDocumentReadRequest;
import com.erp.oa.domain.vo.OaSignPackageFile;
import com.erp.oa.domain.vo.OaSignCompanyOptions;

public interface IOaSignPackageService
{
    final class DraftValidationException extends RuntimeException
    {
        private static final long serialVersionUID = 1L;
        private final String reasonCode;

        public DraftValidationException(String reasonCode, String message)
        {
            super(message);
            this.reasonCode = reasonCode;
        }

        public String getReasonCode()
        {
            return reasonCode;
        }
    }

    OaSignPackage createPackage(OaSignPackage signPackage, Long selectedShopDeptId);

    List<OaSignPackage> selectPackageList(OaSignPackage signPackage, Long selectedShopDeptId);

    OaSignPackage getPackageDetail(Long packageId, Long selectedShopDeptId);

    OaSignPackage getPackageForVerification(Long packageId, Long selectedShopDeptId);

    OaSignPackage updateDraftPackage(Long packageId, OaSignPackage signPackage, Long selectedShopDeptId);

    OaSignPackage sendPackage(Long packageId, Long selectedShopDeptId);

    OaSignPackage preparePackageDocuments(Long packageId, Long selectedShopDeptId);

    /** Internal system path for a server-bound task draft; it never impersonates the assigned HR. */
    OaSignPackage preparePackageDocumentsForSystem(Long packageId, Long taskId, Long assignedHrUserId);

    /**
     * Binds the employee's request-scoped PNG sample and prepares the immutable final candidate
     * while the package is still a draft. The sample is never copied to the employee profile.
     */
    OaSignPackage prepareSignatureFirstCandidateForSystem(Long packageId, Long taskId,
            Long assignedHrUserId, Long dataRequestId, String signatureRequestId,
            byte[] signatureSampleBytes, String signatureSampleHash, Date signatureSampleTime,
            String expectedDocumentVersion, Long expectedVersion);

    /**
     * Commits the employee's one-and-only task-scoped signature to an already sent staged
     * signature-first package, then pauses the employee clock while HR selects the company/seal.
     */
    OaSignPackage recordStagedSignatureFirstSampleForSystem(Long packageId, Long taskId,
            Long dataRequestId, String signatureRequestId, byte[] signatureSampleBytes,
            String signatureSampleHash, Date signatureSampleTime);

    /**
     * Generates the immutable final candidate on the same staged package. This prepares files
     * only; an explicit HR send is still required before the employee can confirm them.
     */
    OaSignPackage prepareStagedSignatureFirstCandidateForSystem(Long packageId, Long taskId,
            Long assignedHrUserId, OaSignPackage finalizedSnapshot, Long dataRequestId,
            String signatureRequestId, byte[] signatureSampleBytes,
            String signatureSampleHash, Date signatureSampleTime,
            String expectedDocumentVersion, Long expectedVersion);

    /** Sends a prepared staged final candidate without asking the employee to sign again. */
    OaSignPackage sendStagedSignatureFirstFinalForSystem(Long packageId, Long taskId,
            Long assignedHrUserId, String requestId);

    OaSignPackage sendPreparedPackage(Long packageId, Long taskId,
            String documentVersion, Long selectedShopDeptId,
            Date sentTime, Date signDeadline, String deadlinePolicySource,
            Integer deadlineDaysSnapshot);

    void markTaskConfirmation(Long packageId, Long taskId, String confirmStatus, Long planVersionId);

    OaSignPackage voidPackage(Long packageId, Long selectedShopDeptId, String voidReason);

    /** Package-only half of task cancellation; caller must lock the guard and transition the task. */
    OaSignPackage voidPackageForTask(Long packageId, Long taskId,
            Long selectedShopDeptId, String voidReason);

    List<OaSignPackage> selectMyPackages(OaSignPackage signPackage);

    OaSignPackage getMyPackageDetail(Long packageId);

    OaSignPackage confirmDocumentRead(Long packageId, Long documentId, OaSignDocumentReadRequest readRequest);

    default OaSignPackage signPackage(Long packageId, OaSignPackageSignRequest signRequest)
    {
        return signPackage(packageId, signRequest, null, null);
    }

    OaSignPackage signPackage(Long packageId, OaSignPackageSignRequest signRequest,
            String signerIp, String signerUserAgent);

    OaSignCompanyOptions getCompanyOptions(Long packageId, Long selectedShopDeptId);

    OaSignPackage finalizePackage(Long packageId, OaSignPackageFinalizeRequest request,
            Long selectedShopDeptId);

    OaSignPackage confirmFinalPackage(Long packageId, OaSignFinalConfirmRequest request,
            String confirmerIp, String confirmerUserAgent);

    OaSignPackage confirmFinalDocumentRead(Long packageId, Long documentId,
            OaSignFinalDocumentReadRequest request, String confirmerIp,
            String confirmerUserAgent);

    OaSignPackageFile resolveMyDocumentFile(Long packageId, Long documentId);

    /** Authenticated server-side image-preview source; never exposed as a raw download. */
    OaSignPackageFile resolveMyDocumentPreviewFile(Long packageId, Long documentId);

    OaSignPackageFile resolveMySignedDocumentFile(Long packageId, Long documentId);

    OaSignPackageFile resolveMyFinalDocumentFile(Long packageId, Long documentId);

    /** Strict final-archive export; unlike preview, this never falls back to a candidate file. */
    OaSignPackageFile resolveMyFinalDocumentExportFile(Long packageId, Long documentId);

    /** Image-preview source for a pending final candidate; never exposed as a raw download. */
    OaSignPackageFile resolveMyFinalDocumentPreviewFile(Long packageId, Long documentId);

    OaSignPackageFile resolveMyCertificateFile(Long packageId, Long documentId);

    OaSignPackageFile resolveScopedDocumentFile(Long packageId, Long documentId, Long selectedShopDeptId);

    OaSignPackageFile resolveScopedSignedDocumentFile(Long packageId, Long documentId, Long selectedShopDeptId);

    OaSignPackageFile resolveScopedFinalDocumentFile(Long packageId, Long documentId, Long selectedShopDeptId);

    /** Strict HR final-archive export; only confirmed archive evidence is eligible. */
    OaSignPackageFile resolveScopedFinalDocumentExportFile(Long packageId, Long documentId,
            Long selectedShopDeptId);

    OaSignPackageFile resolveScopedCertificateFile(Long packageId, Long documentId, Long selectedShopDeptId);
}
