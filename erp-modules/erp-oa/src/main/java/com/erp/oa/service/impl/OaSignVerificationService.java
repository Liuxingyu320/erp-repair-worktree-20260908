package com.erp.oa.service.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.oa.constant.OaSignFileEvidenceType;
import com.erp.oa.constant.OaSignPackageStatus;
import com.erp.oa.constant.OaSignSigningSequence;
import com.erp.oa.constant.OaSignVerificationStatus;
import com.erp.oa.domain.OaSignEvent;
import com.erp.oa.domain.OaSignFileEvidence;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPackageDocument;
import com.erp.oa.domain.vo.OaSignVerificationResult;
import com.erp.oa.mapper.OaSignEventMapper;
import com.erp.oa.mapper.OaSignFileEvidenceMapper;
import com.erp.oa.mapper.OaSignPackageDocumentMapper;

@Service
public class OaSignVerificationService
{
    private static final String VERIFIED_MESSAGE = "文件完整，验真通过";
    private static final String MISMATCH_MESSAGE = "文件与签署记录不一致";
    private static final String FILE_MISSING_MESSAGE = "文件缺失，暂时无法验证";
    private static final String LEGACY_MESSAGE = "历史合同，仅支持旧版验真";

    private final OaSignPackageDocumentMapper documentMapper;
    private final OaSignFileEvidenceMapper evidenceMapper;
    private final OaSignEventMapper eventMapper;
    private final OaSignFileStorageService fileStorageService;

    public OaSignVerificationService(OaSignPackageDocumentMapper documentMapper,
            OaSignFileEvidenceMapper evidenceMapper, OaSignEventMapper eventMapper,
            OaSignFileStorageService fileStorageService)
    {
        this.documentMapper = documentMapper;
        this.evidenceMapper = evidenceMapper;
        this.eventMapper = eventMapper;
        this.fileStorageService = fileStorageService;
    }

    public OaSignVerificationResult verify(OaSignPackage signPackage, boolean includeTechnical)
    {
        if (signPackage == null || signPackage.getPackageId() == null)
        {
            throw new ServiceException("签约包不存在");
        }
        List<OaSignFileEvidence> evidence = safeList(
                evidenceMapper.selectEvidenceByPackageId(signPackage.getPackageId()));
        if (evidence.isEmpty())
        {
            if (hasModernEvidenceSnapshot(signPackage))
            {
                return result(OaSignVerificationStatus.MISMATCH, MISMATCH_MESSAGE,
                        documentResults(signPackage.getPackageId(), OaSignVerificationStatus.MISMATCH),
                        includeTechnical ? new ArrayList<>() : null);
            }
            return result(OaSignVerificationStatus.LEGACY_LIMITED, LEGACY_MESSAGE,
                    documentResults(signPackage.getPackageId(), OaSignVerificationStatus.LEGACY_LIMITED),
                    null);
        }

        List<OaSignPackageDocument> documents = safeList(
                documentMapper.selectDocumentsByPackageId(signPackage.getPackageId()));
        Map<Long, List<OaSignFileEvidence>> evidenceByDocument = new HashMap<>();
        for (OaSignFileEvidence row : evidence)
        {
            evidenceByDocument.computeIfAbsent(row.getDocumentId(), ignored -> new ArrayList<>()).add(row);
        }

        List<OaSignVerificationResult.TechnicalEvidence> technical = includeTechnical
                ? new ArrayList<>() : null;
        List<OaSignVerificationResult.DocumentResult> documentResults = new ArrayList<>();
        OaSignVerificationStatus overall = OaSignVerificationStatus.VERIFIED;
        for (OaSignPackageDocument document : documents)
        {
            OaSignVerificationStatus documentStatus = verifyDocument(signPackage, document,
                    evidenceByDocument.getOrDefault(document.getDocumentId(), List.of()), technical);
            overall = combine(overall, documentStatus);
            documentResults.add(documentResult(document, documentStatus));
        }

        overall = combine(overall, verifyPackageEvidence(signPackage, evidence, technical));
        if (!onlyKnownDocuments(evidence, documents))
        {
            overall = combine(overall, OaSignVerificationStatus.MISMATCH);
        }
        if (!verifyFinalDocumentRoot(signPackage, documents))
        {
            overall = combine(overall, OaSignVerificationStatus.MISMATCH);
            markVerifiedDocumentsMismatch(documentResults);
        }
        if (!verifyFinalArchiveRoot(signPackage, documents))
        {
            overall = combine(overall, OaSignVerificationStatus.MISMATCH);
            markVerifiedDocumentsMismatch(documentResults);
        }
        if (!verifyEventChain(signPackage, safeList(eventMapper.selectEventsByPackageId(signPackage.getPackageId()))))
        {
            overall = combine(overall, OaSignVerificationStatus.MISMATCH);
            markVerifiedDocumentsMismatch(documentResults);
        }
        return result(overall, message(overall), documentResults, technical);
    }

    private OaSignVerificationStatus verifyPackageEvidence(OaSignPackage signPackage,
            List<OaSignFileEvidence> evidence,
            List<OaSignVerificationResult.TechnicalEvidence> technical)
    {
        List<OaSignFileEvidence> packageEvidence = evidence.stream()
                .filter(row -> row != null && row.getDocumentId() == null)
                .toList();
        boolean signatureFirst = OaSignSigningSequence.SIGNATURE_FIRST
                .equals(signPackage.getSigningSequence());
        boolean finalCandidateExists = StringUtils.isNotBlank(
                signPackage.getFinalDocumentVersion());
        boolean sampleRequired = signatureFirst && finalCandidateExists;
        boolean sampleSnapshotPresent = StringUtils.isNotBlank(
                signPackage.getSignatureSampleFileUrl())
                || StringUtils.isNotBlank(signPackage.getSignatureSampleHash())
                || signPackage.getSignatureSampleTime() != null;
        boolean sampleSnapshotComplete = StringUtils.isNotBlank(
                signPackage.getSignatureSampleFileUrl())
                && StringUtils.isNotBlank(signPackage.getSignatureSampleHash())
                && signPackage.getSignatureSampleTime() != null;

        if (packageEvidence.isEmpty())
        {
            return sampleRequired || sampleSnapshotPresent
                    ? OaSignVerificationStatus.MISMATCH
                    : OaSignVerificationStatus.VERIFIED;
        }

        OaSignVerificationStatus status = packageEvidence.size() == 1
                && sampleRequired && sampleSnapshotComplete
                        ? OaSignVerificationStatus.VERIFIED
                        : OaSignVerificationStatus.MISMATCH;
        for (OaSignFileEvidence row : packageEvidence)
        {
            status = combine(status, verifySignatureSampleEvidence(
                    signPackage, row, technical));
        }
        return status;
    }

    private OaSignVerificationStatus verifySignatureSampleEvidence(
            OaSignPackage signPackage, OaSignFileEvidence evidence,
            List<OaSignVerificationResult.TechnicalEvidence> technical)
    {
        String actualHash = null;
        Long actualSize = null;
        OaSignVerificationStatus status;
        try
        {
            if (evidence == null
                    || evidence.getEvidenceType() != OaSignFileEvidenceType.SIGNATURE_SAMPLE
                    || evidence.getDocumentId() != null
                    || !Objects.equals(signPackage.getPackageId(), evidence.getPackageId())
                    || !OaSignSigningSequence.SIGNATURE_FIRST
                            .equals(signPackage.getSigningSequence())
                    || StringUtils.isBlank(signPackage.getFinalDocumentVersion())
                    || StringUtils.isBlank(evidence.getDocumentVersion())
                    || !evidence.getDocumentVersion().matches("SAMPLE-[1-9][0-9]*")
                    || StringUtils.isBlank(evidence.getFileUrl())
                    || StringUtils.isBlank(evidence.getFileHash())
                    || evidence.getFileSize() == null || evidence.getFileSize() <= 0
                    || evidence.getGeneratedTime() == null
                    || StringUtils.isBlank(signPackage.getSignatureSampleFileUrl())
                    || StringUtils.isBlank(signPackage.getSignatureSampleHash())
                    || signPackage.getSignatureSampleTime() == null)
            {
                status = OaSignVerificationStatus.MISMATCH;
            }
            else
            {
                Path evidencePath = resolveEvidenceFile(evidence.getFileUrl());
                Path packagePath = resolveEvidenceFile(signPackage.getSignatureSampleFileUrl());
                byte[] bytes = Files.readAllBytes(evidencePath);
                actualHash = sha256(bytes);
                actualSize = (long) bytes.length;
                boolean matches = Files.isSameFile(evidencePath, packagePath)
                        && sameHash(evidence.getFileHash(), actualHash)
                        && sameHash(signPackage.getSignatureSampleHash(), actualHash)
                        && Objects.equals(evidence.getFileSize(), actualSize)
                        && sameSecond(evidence.getGeneratedTime(),
                                signPackage.getSignatureSampleTime());
                status = matches ? OaSignVerificationStatus.VERIFIED
                        : OaSignVerificationStatus.MISMATCH;
            }
        }
        catch (ServiceException e)
        {
            status = e.getMessage() != null && e.getMessage().contains("不存在")
                    ? OaSignVerificationStatus.FILE_MISSING
                    : OaSignVerificationStatus.MISMATCH;
        }
        catch (IOException e)
        {
            status = OaSignVerificationStatus.FILE_MISSING;
        }
        catch (RuntimeException e)
        {
            status = OaSignVerificationStatus.MISMATCH;
        }

        if (technical != null)
        {
            technical.add(technicalEvidence(evidence, actualHash, actualSize,
                    status == OaSignVerificationStatus.VERIFIED));
        }
        return status;
    }

    private OaSignVerificationStatus verifyDocument(OaSignPackage signPackage, OaSignPackageDocument document,
            List<OaSignFileEvidence> evidence, List<OaSignVerificationResult.TechnicalEvidence> technical)
    {
        OaSignVerificationStatus status = OaSignVerificationStatus.VERIFIED;
        Set<OaSignFileEvidenceType> initialTypes = EnumSet.noneOf(OaSignFileEvidenceType.class);
        Set<OaSignFileEvidenceType> finalTypes = EnumSet.noneOf(OaSignFileEvidenceType.class);
        for (OaSignFileEvidence row : evidence)
        {
            if (row.getEvidenceType() != null)
            {
                (isFinalEvidence(signPackage, row) ? finalTypes : initialTypes).add(row.getEvidenceType());
            }
            status = combine(status, verifyEvidence(signPackage, document, row, technical));
        }

        Set<OaSignFileEvidenceType> required = EnumSet.of(
                OaSignFileEvidenceType.RENDERED_SOURCE, OaSignFileEvidenceType.REVIEW_PDF);
        boolean signatureFirstFinalEvidence = OaSignSigningSequence.SIGNATURE_FIRST
                .equals(signPackage.getSigningSequence())
                && StringUtils.isNotBlank(document.getFinalDocumentVersion());
        boolean companyFirst = OaSignSigningSequence.COMPANY_FIRST
                .equals(signPackage.getSigningSequence());
        boolean employeeSigningIndicated = hasEmployeeSigningIndication(
                signPackage, document, initialTypes, finalTypes);
        boolean companyFirstPreSign = companyFirst
                && (OaSignPackageStatus.DRAFT.equals(signPackage.getStatus())
                        || OaSignPackageStatus.PENDING_SIGN.equals(signPackage.getStatus())
                        || OaSignPackageStatus.PART_VIEWED.equals(signPackage.getStatus()))
                && !employeeSigningIndicated;
        boolean companyFirstUnsignedTerminal = companyFirst
                && (OaSignPackageStatus.REFUSED.equals(signPackage.getStatus())
                        || OaSignPackageStatus.EXPIRED.equals(signPackage.getStatus())
                        || OaSignPackageStatus.VOIDED.equals(signPackage.getStatus()))
                && !employeeSigningIndicated;
        boolean signedEvidenceRequired = "Y".equalsIgnoreCase(document.getSigned())
                || OaSignPackageStatus.SIGNED.equals(signPackage.getStatus())
                || (companyFirst && !companyFirstPreSign
                        && !companyFirstUnsignedTerminal);
        if (signedEvidenceRequired)
        {
            if ("Y".equalsIgnoreCase(document.getEmployeeSignRequired())
                    && !signatureFirstFinalEvidence)
            {
                required.add(OaSignFileEvidenceType.SIGNATURE_IMAGE);
                required.add(OaSignFileEvidenceType.SIGNED_PDF);
                required.add(OaSignFileEvidenceType.SIGN_CERTIFICATE);
            }
        }
        if (!initialTypes.containsAll(required))
        {
            status = combine(status, OaSignVerificationStatus.MISMATCH);
        }
        if (StringUtils.isNotBlank(document.getFinalDocumentVersion()))
        {
            Set<OaSignFileEvidenceType> finalRequired = EnumSet.of(
                    OaSignFileEvidenceType.FINAL_RENDERED_SOURCE,
                    OaSignFileEvidenceType.FINAL_REVIEW_PDF,
                    OaSignFileEvidenceType.FINAL_PENDING_PDF);
            if (OaSignPackageStatus.SIGNED.equals(signPackage.getStatus()))
            {
                finalRequired.add(OaSignFileEvidenceType.FINAL_ARCHIVE_PDF);
            }
            if ("Y".equalsIgnoreCase(document.getEmployeeSignRequired())
                    && !OaSignSigningSequence.COMPANY_FIRST
                            .equals(signPackage.getSigningSequence()))
            {
                finalRequired.add(OaSignFileEvidenceType.SIGNATURE_IMAGE);
            }
            if ("Y".equalsIgnoreCase(document.getCompanySealRequired()))
            {
                finalRequired.add(OaSignFileEvidenceType.COMPANY_SEAL);
            }
            if (!finalTypes.containsAll(finalRequired))
            {
                status = combine(status, OaSignVerificationStatus.MISMATCH);
            }
        }
        return status;
    }

    private boolean hasEmployeeSigningIndication(OaSignPackage signPackage,
            OaSignPackageDocument document, Set<OaSignFileEvidenceType> initialTypes,
            Set<OaSignFileEvidenceType> finalTypes)
    {
        String finalConfirmationStatus = signPackage.getFinalConfirmationStatus();
        boolean postSignFinalStatus = StringUtils.isNotBlank(finalConfirmationStatus)
                && !"PREPARED_NOT_SENT".equalsIgnoreCase(finalConfirmationStatus);
        return "Y".equalsIgnoreCase(document.getSigned())
                || signPackage.getSignedTime() != null
                || signPackage.getInitialSignedTime() != null
                || signPackage.getFinalConfirmedTime() != null
                || signPackage.getSignatureSampleTime() != null
                || postSignFinalStatus
                || StringUtils.isNotBlank(signPackage.getSignatureSampleFileUrl())
                || StringUtils.isNotBlank(signPackage.getSignatureSampleHash())
                || StringUtils.isNotBlank(document.getSignedFileUrl())
                || StringUtils.isNotBlank(document.getSignatureFileUrl())
                || StringUtils.isNotBlank(document.getSignatureHash())
                || StringUtils.isNotBlank(document.getSignedPdfUrl())
                || StringUtils.isNotBlank(document.getSignedPdfHash())
                || StringUtils.isNotBlank(document.getCertificateFileUrl())
                || StringUtils.isNotBlank(document.getCertificateHash())
                || containsSigningEvidence(initialTypes)
                || containsSigningEvidence(finalTypes);
    }

    private boolean containsSigningEvidence(Set<OaSignFileEvidenceType> evidenceTypes)
    {
        return evidenceTypes.contains(OaSignFileEvidenceType.SIGNATURE_IMAGE)
                || evidenceTypes.contains(OaSignFileEvidenceType.SIGNED_PDF)
                || evidenceTypes.contains(OaSignFileEvidenceType.SIGN_CERTIFICATE);
    }

    private OaSignVerificationStatus verifyEvidence(OaSignPackage signPackage, OaSignPackageDocument document,
            OaSignFileEvidence evidence, List<OaSignVerificationResult.TechnicalEvidence> technical)
    {
        String actualHash = null;
        Long actualSize = null;
        OaSignVerificationStatus status;
        try
        {
            Path path = resolveEvidenceFile(evidence.getFileUrl());
            byte[] bytes = Files.readAllBytes(path);
            actualHash = sha256(bytes);
            actualSize = (long) bytes.length;
            boolean metadataMatches = StringUtils.isNotBlank(evidence.getFileHash())
                    && sameHash(evidence.getFileHash(), actualHash)
                    && (evidence.getFileSize() == null || evidence.getFileSize() <= 0
                            || Objects.equals(evidence.getFileSize(), actualSize));
            boolean versionMatches = versionMatches(signPackage, document, evidence);
            boolean documentHashMatches = matchesDocumentHash(signPackage, document, evidence);
            status = metadataMatches && versionMatches && documentHashMatches
                    ? OaSignVerificationStatus.VERIFIED : OaSignVerificationStatus.MISMATCH;
        }
        catch (ServiceException e)
        {
            status = e.getMessage() != null && e.getMessage().contains("不存在")
                    ? OaSignVerificationStatus.FILE_MISSING : OaSignVerificationStatus.MISMATCH;
        }
        catch (IOException e)
        {
            status = OaSignVerificationStatus.FILE_MISSING;
        }

        if (technical != null)
        {
            technical.add(technicalEvidence(evidence, actualHash, actualSize,
                    status == OaSignVerificationStatus.VERIFIED));
        }
        return status;
    }

    private Path resolveEvidenceFile(String fileUrl)
    {
        if (fileStorageService.isManagedPublicUrl(fileUrl))
        {
            return fileStorageService.resolveAuthorizedPublicUrl(fileUrl);
        }
        return fileStorageService.resolveAuthorizedFile(fileUrl);
    }

    private boolean versionMatches(OaSignPackage signPackage, OaSignPackageDocument document,
            OaSignFileEvidence evidence)
    {
        if (isFinalEvidence(signPackage, evidence))
        {
            return Objects.equals(signPackage.getFinalDocumentVersion(), evidence.getDocumentVersion())
                    && Objects.equals(document.getFinalDocumentVersion(), evidence.getDocumentVersion());
        }
        return Objects.equals(signPackage.getDocumentVersion(), evidence.getDocumentVersion())
                && Objects.equals(document.getDocumentVersion(), evidence.getDocumentVersion());
    }

    private boolean isFinalEvidence(OaSignPackage signPackage, OaSignFileEvidence evidence)
    {
        if (evidence == null || evidence.getEvidenceType() == null)
        {
            return false;
        }
        return switch (evidence.getEvidenceType())
        {
            case FINAL_RENDERED_SOURCE, FINAL_REVIEW_PDF, FINAL_SIGNED_PDF,
                    FINAL_PENDING_PDF, FINAL_ARCHIVE_PDF, COMPANY_SEAL -> true;
            default -> StringUtils.isNotBlank(signPackage.getFinalDocumentVersion())
                    && Objects.equals(signPackage.getFinalDocumentVersion(), evidence.getDocumentVersion());
        };
    }

    private boolean matchesDocumentHash(OaSignPackage signPackage, OaSignPackageDocument document,
            OaSignFileEvidence evidence)
    {
        if (evidence.getEvidenceType() == null)
        {
            return false;
        }
        if (evidence.getEvidenceType() == OaSignFileEvidenceType.SIGNATURE_IMAGE
                && OaSignSigningSequence.SIGNATURE_FIRST.equals(signPackage.getSigningSequence())
                && isFinalEvidence(signPackage, evidence))
        {
            String sampleHash = signPackage.getSignatureSampleHash();
            return StringUtils.isNotBlank(sampleHash)
                    && sameHash(sampleHash, evidence.getFileHash());
        }
        String expected = switch (evidence.getEvidenceType())
        {
            case REVIEW_PDF -> document.getReviewPdfHash();
            case SIGNED_PDF -> document.getSignedPdfHash();
            case FINAL_SIGNED_PDF -> document.getFinalPdfHash();
            case FINAL_PENDING_PDF -> document.getFinalPdfHash();
            case FINAL_ARCHIVE_PDF -> document.getFinalArchivePdfHash();
            case SIGNATURE_IMAGE -> document.getSignatureHash();
            case SIGN_CERTIFICATE -> document.getCertificateHash();
            case COMPANY_SEAL -> signPackage.getSealImageHashSnapshot();
            default -> null;
        };
        if (evidence.getEvidenceType() == OaSignFileEvidenceType.FINAL_PENDING_PDF
                || evidence.getEvidenceType() == OaSignFileEvidenceType.FINAL_ARCHIVE_PDF)
        {
            return StringUtils.isNotBlank(expected) && sameHash(expected, evidence.getFileHash());
        }
        return expected == null || sameHash(expected, evidence.getFileHash());
    }

    private boolean verifyFinalDocumentRoot(OaSignPackage signPackage,
            List<OaSignPackageDocument> documents)
    {
        boolean hasFinalVersion = StringUtils.isNotBlank(signPackage.getFinalDocumentVersion());
        boolean hasFinalRoot = StringUtils.isNotBlank(signPackage.getFinalDocumentRootHash());
        if (!hasFinalVersion && !hasFinalRoot)
        {
            return true;
        }
        if (!hasFinalVersion || !hasFinalRoot)
        {
            return false;
        }

        Map<Long, String> contentHashes = new HashMap<>();
        for (OaSignPackageDocument document : documents)
        {
            if (!Objects.equals(signPackage.getFinalDocumentVersion(), document.getFinalDocumentVersion()))
            {
                continue;
            }
            if (document.getDocumentId() == null || StringUtils.isBlank(document.getFinalContentHash())
                    || contentHashes.put(document.getDocumentId(), document.getFinalContentHash()) != null)
            {
                return false;
            }
        }
        if (contentHashes.isEmpty())
        {
            return false;
        }
        return sameHash(signPackage.getFinalDocumentRootHash(),
                finalDocumentRootHashFromHashes(contentHashes));
    }

    private boolean verifyFinalArchiveRoot(OaSignPackage signPackage,
            List<OaSignPackageDocument> documents)
    {
        boolean hasFinalVersion = StringUtils.isNotBlank(signPackage.getFinalDocumentVersion());
        boolean hasArchiveRoot = StringUtils.isNotBlank(signPackage.getFinalArchiveRootHash());
        if (!hasFinalVersion)
        {
            return !hasArchiveRoot;
        }
        if (!OaSignPackageStatus.SIGNED.equals(signPackage.getStatus()))
        {
            return !hasArchiveRoot;
        }
        if (!hasArchiveRoot)
        {
            return false;
        }

        Map<Long, String> archiveHashes = new HashMap<>();
        for (OaSignPackageDocument document : documents)
        {
            if (!Objects.equals(signPackage.getFinalDocumentVersion(),
                    document.getFinalDocumentVersion()))
            {
                continue;
            }
            if (document.getDocumentId() == null
                    || StringUtils.isBlank(document.getFinalArchivePdfHash())
                    || archiveHashes.put(document.getDocumentId(),
                            document.getFinalArchivePdfHash()) != null)
            {
                return false;
            }
        }
        return !archiveHashes.isEmpty()
                && sameHash(signPackage.getFinalArchiveRootHash(),
                        finalDocumentRootHashFromHashes(archiveHashes));
    }

    private String finalDocumentRootHashFromHashes(Map<Long, String> hashes)
    {
        StringBuilder payload = new StringBuilder();
        hashes.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                payload.append(entry.getKey()).append(':').append(entry.getValue()).append('\n'));
        return sha256(payload.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void markVerifiedDocumentsMismatch(
            List<OaSignVerificationResult.DocumentResult> documentResults)
    {
        for (OaSignVerificationResult.DocumentResult row : documentResults)
        {
            if (row.getStatus() == OaSignVerificationStatus.VERIFIED)
            {
                row.setStatus(OaSignVerificationStatus.MISMATCH);
                row.setMessage(MISMATCH_MESSAGE);
            }
        }
    }

    private boolean verifyEventChain(OaSignPackage signPackage, List<OaSignEvent> events)
    {
        if (events.isEmpty())
        {
            return false;
        }
        String previousHash = null;
        boolean signedEventFound = false;
        boolean finalConfirmedEventFound = false;
        for (OaSignEvent event : events)
        {
            if (!Objects.equals(signPackage.getPackageId(), event.getPackageId())
                    || !sameNullableHash(previousHash, event.getPrevEventHash())
                    || !sameHash(calculateEventHash(event), event.getEventHash()))
            {
                return false;
            }
            previousHash = event.getEventHash();
            if ("PACKAGE_SIGNED".equals(event.getEventType()))
            {
                signedEventFound = true;
            }
            if ("FINAL_CONTRACT_CONFIRMED".equals(event.getEventType()))
            {
                finalConfirmedEventFound = true;
            }
        }
        if (!OaSignPackageStatus.SIGNED.equals(signPackage.getStatus()))
        {
            return true;
        }
        return StringUtils.isNotBlank(signPackage.getFinalDocumentVersion())
                ? finalConfirmedEventFound : signedEventFound;
    }

    private boolean onlyKnownDocuments(List<OaSignFileEvidence> evidence, List<OaSignPackageDocument> documents)
    {
        Set<Long> knownIds = new java.util.HashSet<>();
        for (OaSignPackageDocument document : documents)
        {
            knownIds.add(document.getDocumentId());
        }
        return evidence.stream().allMatch(row -> row != null
                && (row.getEvidenceType() == OaSignFileEvidenceType.SIGNATURE_SAMPLE
                        ? row.getDocumentId() == null
                        : row.getDocumentId() != null
                                && knownIds.contains(row.getDocumentId())));
    }

    private boolean sameSecond(Date first, Date second)
    {
        return first != null && second != null
                && first.getTime() / 1_000L == second.getTime() / 1_000L;
    }

    private boolean hasModernEvidenceSnapshot(OaSignPackage signPackage)
    {
        return StringUtils.isNotBlank(signPackage.getFinalDocumentVersion())
                || StringUtils.isNotBlank(signPackage.getFinalDocumentRootHash())
                || StringUtils.isNotBlank(signPackage.getSignatureSampleFileUrl())
                || StringUtils.isNotBlank(signPackage.getSignatureSampleHash())
                || signPackage.getSignatureSampleTime() != null;
    }

    private List<OaSignVerificationResult.DocumentResult> documentResults(Long packageId,
            OaSignVerificationStatus status)
    {
        List<OaSignVerificationResult.DocumentResult> results = new ArrayList<>();
        for (OaSignPackageDocument document : safeList(documentMapper.selectDocumentsByPackageId(packageId)))
        {
            results.add(documentResult(document, status));
        }
        return results;
    }

    private OaSignVerificationResult.DocumentResult documentResult(OaSignPackageDocument document,
            OaSignVerificationStatus status)
    {
        OaSignVerificationResult.DocumentResult result = new OaSignVerificationResult.DocumentResult();
        result.setDocumentId(document.getDocumentId());
        result.setDocumentName(document.getDocumentName());
        result.setStatus(status);
        result.setMessage(message(status));
        return result;
    }

    private OaSignVerificationResult.TechnicalEvidence technicalEvidence(OaSignFileEvidence evidence,
            String actualHash, Long actualSize, boolean matches)
    {
        OaSignVerificationResult.TechnicalEvidence result = new OaSignVerificationResult.TechnicalEvidence();
        result.setEvidenceId(evidence.getEvidenceId());
        result.setDocumentId(evidence.getDocumentId());
        result.setEvidenceType(evidence.getEvidenceType());
        result.setFileUrl(evidence.getFileUrl());
        result.setExpectedHash(evidence.getFileHash());
        result.setActualHash(actualHash);
        result.setExpectedSize(evidence.getFileSize());
        result.setActualSize(actualSize);
        result.setMatches(matches);
        return result;
    }

    private OaSignVerificationResult result(OaSignVerificationStatus status, String message,
            List<OaSignVerificationResult.DocumentResult> documents,
            List<OaSignVerificationResult.TechnicalEvidence> technical)
    {
        OaSignVerificationResult result = new OaSignVerificationResult();
        result.setStatus(status);
        result.setMessage(message);
        result.setCheckedTime(new Date());
        result.setDocumentResults(documents);
        result.setTechnicalEvidence(technical);
        return result;
    }

    private OaSignVerificationStatus combine(OaSignVerificationStatus left, OaSignVerificationStatus right)
    {
        if (left == OaSignVerificationStatus.FILE_MISSING || right == OaSignVerificationStatus.FILE_MISSING)
        {
            return OaSignVerificationStatus.FILE_MISSING;
        }
        if (left == OaSignVerificationStatus.MISMATCH || right == OaSignVerificationStatus.MISMATCH)
        {
            return OaSignVerificationStatus.MISMATCH;
        }
        if (left == OaSignVerificationStatus.LEGACY_LIMITED || right == OaSignVerificationStatus.LEGACY_LIMITED)
        {
            return OaSignVerificationStatus.LEGACY_LIMITED;
        }
        return OaSignVerificationStatus.VERIFIED;
    }

    private String message(OaSignVerificationStatus status)
    {
        return switch (status)
        {
            case VERIFIED -> VERIFIED_MESSAGE;
            case MISMATCH -> MISMATCH_MESSAGE;
            case FILE_MISSING -> FILE_MISSING_MESSAGE;
            case LEGACY_LIMITED -> LEGACY_MESSAGE;
        };
    }

    private boolean sameHash(String left, String right)
    {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private boolean sameNullableHash(String left, String right)
    {
        if (StringUtils.isBlank(left) && StringUtils.isBlank(right))
        {
            return true;
        }
        return sameHash(left, right);
    }

    private <T> List<T> safeList(List<T> rows)
    {
        return rows == null ? List.of() : rows;
    }

    private String sha256(byte[] bytes)
    {
        try
        {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("SHA-256不可用", e);
        }
    }

    public static String calculateEventHash(OaSignEvent event)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = String.join("|",
                    String.valueOf(event.getPackageId()),
                    String.valueOf(event.getDocumentId()),
                    String.valueOf(event.getEventType()),
                    String.valueOf(event.getOperatorUserId()),
                    String.valueOf(event.getOperatorRole()),
                    String.valueOf(event.getDocumentHash()),
                    String.valueOf(event.getPrevEventHash()),
                    String.valueOf(event.getRequestId()),
                    String.valueOf(event.getCreateTime() == null ? 0 : event.getCreateTime().getTime()),
                    String.valueOf(event.getEventPayload()));
            return java.util.HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("SHA-256不可用", e);
        }
    }
}
