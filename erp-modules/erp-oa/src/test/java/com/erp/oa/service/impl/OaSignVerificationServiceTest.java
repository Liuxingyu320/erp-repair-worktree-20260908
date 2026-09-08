package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.oa.config.OaSignFileProperties;
import com.erp.oa.constant.OaSignFileEvidenceType;
import com.erp.oa.constant.OaSignPackageStatus;
import com.erp.oa.constant.OaSignSigningSequence;
import com.erp.oa.constant.OaSignVerificationStatus;
import com.erp.oa.domain.OaSignEvent;
import com.erp.oa.domain.OaSignFileEvidence;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPackageDocument;
import com.erp.oa.domain.vo.OaSignVerificationResult;
import com.erp.oa.domain.vo.StagedSignFile;
import com.erp.oa.mapper.OaSignEventMapper;
import com.erp.oa.mapper.OaSignFileEvidenceMapper;
import com.erp.oa.mapper.OaSignPackageDocumentMapper;

@DisplayName("签约证据业务化验真")
class OaSignVerificationServiceTest
{
    private static final String SIGNATURE_SAMPLE_CONTENT = "task-signature-sample";

    @TempDir
    Path tempDir;

    private OaSignPackageDocumentMapper documentMapper;
    private OaSignFileEvidenceMapper evidenceMapper;
    private OaSignEventMapper eventMapper;
    private OaSignFileStorageService storage;
    private OaSignVerificationService service;
    private OaSignPackage signPackage;
    private OaSignPackageDocument document;
    private List<OaSignFileEvidence> evidence;
    private List<OaSignEvent> events;

    @BeforeEach
    void setUp() throws Exception
    {
        documentMapper = mock(OaSignPackageDocumentMapper.class);
        evidenceMapper = mock(OaSignFileEvidenceMapper.class);
        eventMapper = mock(OaSignEventMapper.class);
        OaSignFileProperties properties = new OaSignFileProperties();
        properties.getStorage().setRootPath(tempDir.resolve("archive").toString());
        properties.getStorage().setTempPath(tempDir.resolve("staging").toString());
        properties.getStorage().setPublicPrefix("/profile/private/sign-package");
        storage = new OaSignFileStorageService(properties);
        service = new OaSignVerificationService(documentMapper, evidenceMapper, eventMapper, storage);

        signPackage = new OaSignPackage();
        signPackage.setPackageId(700L);
        signPackage.setPackageNo("SP202607110700");
        signPackage.setDocumentVersion("SP-700-V1");
        signPackage.setStatus("signed");

        document = new OaSignPackageDocument();
        document.setDocumentId(71L);
        document.setPackageId(700L);
        document.setDocumentName("劳动合同");
        document.setDocumentVersion("SP-700-V1");
        document.setEmployeeSignRequired("Y");
        document.setSigned("Y");

        evidence = new ArrayList<>();
        archiveEvidence(OaSignFileEvidenceType.RENDERED_SOURCE,
                "rendered.docx", "rendered-source".getBytes(StandardCharsets.UTF_8));
        OaSignFileEvidence review = archiveEvidence(OaSignFileEvidenceType.REVIEW_PDF,
                "review.pdf", "%PDF-1.4 review".getBytes(StandardCharsets.ISO_8859_1));
        OaSignFileEvidence signature = archiveEvidence(OaSignFileEvidenceType.SIGNATURE_IMAGE,
                "signature.png", "png-signature".getBytes(StandardCharsets.UTF_8));
        OaSignFileEvidence signed = archiveEvidence(OaSignFileEvidenceType.SIGNED_PDF,
                "signed.pdf", "%PDF-1.4 signed".getBytes(StandardCharsets.ISO_8859_1));
        OaSignFileEvidence certificate = archiveEvidence(OaSignFileEvidenceType.SIGN_CERTIFICATE,
                "certificate.pdf", "%PDF-1.4 certificate".getBytes(StandardCharsets.ISO_8859_1));
        document.setReviewPdfHash(review.getFileHash());
        document.setSignedPdfHash(signed.getFileHash());
        document.setSignatureHash(signature.getFileHash());
        document.setCertificateHash(certificate.getFileHash());

        events = validEvents();
        when(documentMapper.selectDocumentsByPackageId(700L)).thenReturn(List.of(document));
        when(evidenceMapper.selectEvidenceByPackageId(700L)).thenReturn(evidence);
        when(eventMapper.selectEventsByPackageId(700L)).thenReturn(events);
    }

    @Test
    @DisplayName("证据、文件和事件链完整时返回验真通过且默认不暴露hash")
    void shouldReturnVerifiedWithoutTechnicalHashesByDefault() throws Exception
    {
        OaSignVerificationResult result = service.verify(signPackage, false);

        assertThat(result.getStatus()).isEqualTo(OaSignVerificationStatus.VERIFIED);
        assertThat(result.getMessage()).isEqualTo("文件完整，验真通过");
        assertThat(result.getDocumentResults()).hasSize(1)
                .allSatisfy(row -> assertThat(row.getStatus()).isEqualTo(OaSignVerificationStatus.VERIFIED));
        assertThat(result.getTechnicalEvidence()).isNull();
        String json = new ObjectMapper().writeValueAsString(result);
        assertThat(json).doesNotContain(document.getReviewPdfHash(), document.getSignedPdfHash(), "eventHash",
                "expectedHash", "actualHash", "fileUrl");
    }

    @Test
    @DisplayName("技术模式才返回原始证据hash")
    void shouldIncludeRawEvidenceOnlyInTechnicalMode()
    {
        OaSignVerificationResult result = service.verify(signPackage, true);

        assertThat(result.getTechnicalEvidence()).hasSize(evidence.size());
        assertThat(result.getTechnicalEvidence()).extracting(
                OaSignVerificationResult.TechnicalEvidence::getExpectedHash)
                .contains(document.getReviewPdfHash(), document.getSignedPdfHash());
    }

    @Test
    @DisplayName("文件被篡改、事件链断裂或证书hash不符时返回不一致")
    void shouldReturnMismatchForTamperBrokenChainOrCertificateHash() throws Exception
    {
        Path signedPath = storage.resolveAuthorizedFile(evidenceOf(OaSignFileEvidenceType.SIGNED_PDF).getFileUrl());
        Files.write(signedPath, new byte[] { 'X' }, java.nio.file.StandardOpenOption.APPEND);
        assertThat(service.verify(signPackage, false).getStatus()).isEqualTo(OaSignVerificationStatus.MISMATCH);

        Files.write(signedPath, "%PDF-1.4 signed".getBytes(StandardCharsets.ISO_8859_1));
        events.get(1).setPrevEventHash("broken-chain");
        assertThat(service.verify(signPackage, false).getStatus()).isEqualTo(OaSignVerificationStatus.MISMATCH);

        events = validEvents();
        when(eventMapper.selectEventsByPackageId(700L)).thenReturn(events);
        evidenceOf(OaSignFileEvidenceType.SIGN_CERTIFICATE).setFileHash("0".repeat(64));
        assertThat(service.verify(signPackage, false).getStatus()).isEqualTo(OaSignVerificationStatus.MISMATCH);
    }

    @Test
    @DisplayName("证据文件不存在时返回文件缺失")
    void shouldReturnFileMissingWhenArchivedEvidenceDisappears() throws Exception
    {
        Files.delete(storage.resolveAuthorizedFile(
                evidenceOf(OaSignFileEvidenceType.SIGN_CERTIFICATE).getFileUrl()));

        OaSignVerificationResult result = service.verify(signPackage, false);

        assertThat(result.getStatus()).isEqualTo(OaSignVerificationStatus.FILE_MISSING);
        assertThat(result.getMessage()).isEqualTo("文件缺失，暂时无法验证");
    }

    @Test
    @DisplayName("旧记录没有证据行时返回旧版验真")
    void shouldReturnLegacyLimitedWhenEvidenceWasNeverRecorded()
    {
        when(evidenceMapper.selectEvidenceByPackageId(700L)).thenReturn(List.of());

        OaSignVerificationResult result = service.verify(signPackage, false);

        assertThat(result.getStatus()).isEqualTo(OaSignVerificationStatus.LEGACY_LIMITED);
        assertThat(result.getMessage()).isEqualTo("历史合同，仅支持旧版验真");
    }

    @Test
    @DisplayName("新流程快照存在但证据行被删空时必须失败关闭")
    void shouldRejectModernPackageWhenAllEvidenceRowsDisappear()
    {
        prepareSignatureFirstFinalPackage(true);
        evidence.clear();

        OaSignVerificationResult result = service.verify(signPackage, true);

        assertThat(result.getStatus()).isEqualTo(OaSignVerificationStatus.MISMATCH);
        assertThat(result.getMessage()).isEqualTo("文件与签署记录不一致");
        assertThat(result.getTechnicalEvidence()).isEmpty();
        assertThat(result.getDocumentResults()).singleElement()
                .satisfies(row -> assertThat(row.getStatus())
                        .isEqualTo(OaSignVerificationStatus.MISMATCH));
    }

    @Test
    @DisplayName("待最终确认时校验当前正文根且不要求尚未生成的归档PDF")
    void shouldVerifyPendingFinalEvidenceBeforeArchiveExists()
    {
        signPackage.setStatus("pending_final_confirm");
        signPackage.setFinalDocumentVersion("SP-700-V2");
        addFinalEvidence(document, "labor", sha256("labor-content"), false);
        signPackage.setFinalDocumentRootHash(finalRoot(Map.of(
                document.getDocumentId(), document.getFinalContentHash())));

        OaSignVerificationResult result = service.verify(signPackage, false);

        assertThat(result.getStatus()).isEqualTo(OaSignVerificationStatus.VERIFIED);
        assertThat(result.getDocumentResults()).singleElement()
                .satisfies(row -> assertThat(row.getStatus()).isEqualTo(OaSignVerificationStatus.VERIFIED));
        assertThat(evidence).extracting(OaSignFileEvidence::getEvidenceType)
                .contains(OaSignFileEvidenceType.FINAL_PENDING_PDF)
                .doesNotContain(OaSignFileEvidenceType.FINAL_SIGNED_PDF,
                        OaSignFileEvidenceType.FINAL_ARCHIVE_PDF);
    }

    @Test
    @DisplayName("公司优先预签候选不要求尚未产生的员工签名证据")
    void shouldVerifyCompanyFirstCandidateBeforeEmployeeSigns()
    {
        prepareCompanyFirstPreSignCandidate();

        OaSignVerificationResult result = service.verify(signPackage, false);

        assertThat(result.getStatus()).isEqualTo(OaSignVerificationStatus.VERIFIED);
        assertThat(result.getDocumentResults()).singleElement()
                .satisfies(row -> assertThat(row.getStatus())
                        .isEqualTo(OaSignVerificationStatus.VERIFIED));
        assertThat(evidence).extracting(OaSignFileEvidence::getEvidenceType)
                .doesNotContain(OaSignFileEvidenceType.SIGNATURE_IMAGE,
                        OaSignFileEvidenceType.SIGNED_PDF,
                        OaSignFileEvidenceType.SIGN_CERTIFICATE);
    }

    @Test
    @DisplayName("公司优先待发送草稿不要求尚未产生的员工签名证据")
    void shouldVerifyCompanyFirstDraftBeforeItIsSent()
    {
        prepareCompanyFirstPreSignCandidate();
        signPackage.setStatus(OaSignPackageStatus.DRAFT);

        OaSignVerificationResult result = service.verify(signPackage, false);

        assertThat(result.getStatus()).isEqualTo(OaSignVerificationStatus.VERIFIED);
        assertThat(result.getDocumentResults()).singleElement()
                .satisfies(row -> assertThat(row.getStatus())
                        .isEqualTo(OaSignVerificationStatus.VERIFIED));
    }

    @Test
    @DisplayName("公司优先预签候选缺失最终待签PDF仍验真失败")
    void shouldRejectCompanyFirstCandidateWhenFinalPendingPdfIsMissing()
    {
        prepareCompanyFirstPreSignCandidate();
        evidence.removeIf(row -> row.getEvidenceType()
                == OaSignFileEvidenceType.FINAL_PENDING_PDF);

        OaSignVerificationResult result = service.verify(signPackage, false);

        assertThat(result.getStatus()).isEqualTo(OaSignVerificationStatus.MISMATCH);
        assertThat(result.getDocumentResults()).singleElement()
                .satisfies(row -> assertThat(row.getStatus())
                        .isEqualTo(OaSignVerificationStatus.MISMATCH));
    }

    @Test
    @DisplayName("公司优先签后仍必须保留初始版本签名PDF和证书证据")
    void shouldRejectSignedCompanyFirstCandidateWithoutInitialSigningEvidence()
    {
        prepareCompanyFirstPreSignCandidate();
        signPackage.setStatus(OaSignPackageStatus.PENDING_FINAL_CONFIRM);
        signPackage.setFinalConfirmationStatus("PENDING");
        document.setSigned("Y");

        OaSignVerificationResult result = service.verify(signPackage, false);

        assertThat(result.getStatus()).isEqualTo(OaSignVerificationStatus.MISMATCH);
        assertThat(result.getDocumentResults()).singleElement()
                .satisfies(row -> assertThat(row.getStatus())
                        .isEqualTo(OaSignVerificationStatus.MISMATCH));
    }

    @Test
    @DisplayName("公司优先状态已推进时不得以未签标记绕过签署证据")
    void shouldRejectCompanyFirstPendingFinalConfirmWhenDocumentStillUnsigned()
    {
        prepareCompanyFirstPreSignCandidate();
        signPackage.setStatus(OaSignPackageStatus.PENDING_FINAL_CONFIRM);
        signPackage.setFinalConfirmationStatus("PENDING");
        document.setSigned("N");

        OaSignVerificationResult result = service.verify(signPackage, false);

        assertThat(result.getStatus()).isEqualTo(OaSignVerificationStatus.MISMATCH);
        assertThat(result.getDocumentResults()).singleElement()
                .satisfies(row -> assertThat(row.getStatus())
                        .isEqualTo(OaSignVerificationStatus.MISMATCH));
    }

    @Test
    @DisplayName("公司优先未签直接拒签保留预签验真语义")
    void shouldVerifyUnsignedCompanyFirstRefusal()
    {
        prepareCompanyFirstPreSignCandidate();
        signPackage.setStatus(OaSignPackageStatus.REFUSED);

        OaSignVerificationResult result = service.verify(signPackage, false);

        assertThat(result.getStatus()).isEqualTo(OaSignVerificationStatus.VERIFIED);
        assertThat(result.getDocumentResults()).singleElement()
                .satisfies(row -> assertThat(row.getStatus())
                        .isEqualTo(OaSignVerificationStatus.VERIFIED));
    }

    @Test
    @DisplayName("公司优先未签直接过期保留预签验真语义")
    void shouldVerifyUnsignedCompanyFirstExpiry()
    {
        prepareCompanyFirstPreSignCandidate();
        signPackage.setStatus(OaSignPackageStatus.EXPIRED);

        OaSignVerificationResult result = service.verify(signPackage, false);

        assertThat(result.getStatus()).isEqualTo(OaSignVerificationStatus.VERIFIED);
        assertThat(result.getDocumentResults()).singleElement()
                .satisfies(row -> assertThat(row.getStatus())
                        .isEqualTo(OaSignVerificationStatus.VERIFIED));
    }

    @Test
    @DisplayName("公司优先未签直接撤回保留预签验真语义")
    void shouldVerifyUnsignedCompanyFirstVoid()
    {
        prepareCompanyFirstPreSignCandidate();
        signPackage.setStatus(OaSignPackageStatus.VOIDED);

        OaSignVerificationResult result = service.verify(signPackage, false);

        assertThat(result.getStatus()).isEqualTo(OaSignVerificationStatus.VERIFIED);
        assertThat(result.getDocumentResults()).singleElement()
                .satisfies(row -> assertThat(row.getStatus())
                        .isEqualTo(OaSignVerificationStatus.VERIFIED));
    }

    @Test
    @DisplayName("公司优先终态存在签署时间时必须保留完整签署证据")
    void shouldRejectTerminalCompanyFirstWithSigningIndicationButNoEvidence()
    {
        prepareCompanyFirstPreSignCandidate();
        signPackage.setStatus(OaSignPackageStatus.REFUSED);
        signPackage.setSignedTime(new Date(1_700_000_001_000L));

        OaSignVerificationResult result = service.verify(signPackage, false);

        assertThat(result.getStatus()).isEqualTo(OaSignVerificationStatus.MISMATCH);
        assertThat(result.getDocumentResults()).singleElement()
                .satisfies(row -> assertThat(row.getStatus())
                        .isEqualTo(OaSignVerificationStatus.MISMATCH));
    }

    @Test
    @DisplayName("签名优先最终候选仍必须保留最终版签名图")
    void shouldRejectSignatureFirstCandidateWithoutFinalSignatureImage()
    {
        prepareSignatureFirstFinalPackage(true);
        evidence.removeIf(row -> row.getEvidenceType()
                == OaSignFileEvidenceType.SIGNATURE_IMAGE
                && "SP-700-V2".equals(row.getDocumentVersion()));

        OaSignVerificationResult result = service.verify(signPackage, false);

        assertThat(result.getStatus()).isEqualTo(OaSignVerificationStatus.MISMATCH);
        assertThat(result.getDocumentResults()).singleElement()
                .satisfies(row -> assertThat(row.getStatus())
                        .isEqualTo(OaSignVerificationStatus.MISMATCH));
    }

    @Test
    @DisplayName("签名优先最终候选的唯一包级签名样本验真通过")
    void shouldVerifySinglePackageLevelSignatureSample()
    {
        OaSignFileEvidence sample = prepareSignatureFirstFinalPackage(true);

        OaSignVerificationResult result = service.verify(signPackage, true);

        assertThat(result.getStatus()).isEqualTo(OaSignVerificationStatus.VERIFIED);
        assertThat(result.getDocumentResults()).singleElement()
                .satisfies(row -> assertThat(row.getStatus())
                        .isEqualTo(OaSignVerificationStatus.VERIFIED));
        assertThat(result.getTechnicalEvidence()).filteredOn(row ->
                row.getEvidenceType() == OaSignFileEvidenceType.SIGNATURE_SAMPLE)
                .singleElement().satisfies(row ->
                {
                    assertThat(row.getDocumentId()).isNull();
                    assertThat(row.getExpectedHash()).isEqualTo(sample.getFileHash());
                    assertThat(row.isMatches()).isTrue();
                });
    }

    @Test
    @DisplayName("包级签名样本的版本、哈希、大小或时间不一致时验真异常")
    void shouldRejectSignatureSampleMetadataMismatch()
    {
        OaSignFileEvidence sample = prepareSignatureFirstFinalPackage(true);
        String validVersion = sample.getDocumentVersion();
        String validPackageHash = signPackage.getSignatureSampleHash();
        String validEvidenceHash = sample.getFileHash();
        Long validSize = sample.getFileSize();
        Date validTime = sample.getGeneratedTime();

        sample.setDocumentVersion("SP-700-V2");
        assertThat(service.verify(signPackage, false).getStatus())
                .isEqualTo(OaSignVerificationStatus.MISMATCH);
        sample.setDocumentVersion(validVersion);

        signPackage.setSignatureSampleHash("0".repeat(64));
        assertThat(service.verify(signPackage, false).getStatus())
                .isEqualTo(OaSignVerificationStatus.MISMATCH);
        signPackage.setSignatureSampleHash(validPackageHash);

        sample.setFileHash("f".repeat(64));
        assertThat(service.verify(signPackage, false).getStatus())
                .isEqualTo(OaSignVerificationStatus.MISMATCH);
        sample.setFileHash(validEvidenceHash);

        sample.setFileSize(validSize + 1);
        assertThat(service.verify(signPackage, false).getStatus())
                .isEqualTo(OaSignVerificationStatus.MISMATCH);
        sample.setFileSize(validSize);

        sample.setGeneratedTime(new Date(validTime.getTime() + 1_000L));
        assertThat(service.verify(signPackage, false).getStatus())
                .isEqualTo(OaSignVerificationStatus.MISMATCH);
    }

    @Test
    @DisplayName("签名样本缺失、重复、绑到文档或出现未知包级证据时拒绝验真")
    void shouldRejectMissingDuplicateMisboundOrUnknownPackageEvidence()
    {
        prepareSignatureFirstFinalPackage(false);
        assertThat(service.verify(signPackage, false).getStatus())
                .isEqualTo(OaSignVerificationStatus.MISMATCH);

        OaSignFileEvidence sample = addValidSignatureSample();
        OaSignFileEvidence duplicate = archiveEvidence(null,
                OaSignFileEvidenceType.SIGNATURE_SAMPLE, "duplicate-sample.png",
                "duplicate-signature".getBytes(StandardCharsets.UTF_8), "SAMPLE-9900002");
        assertThat(service.verify(signPackage, false).getStatus())
                .isEqualTo(OaSignVerificationStatus.MISMATCH);
        evidence.remove(duplicate);

        sample.setDocumentId(document.getDocumentId());
        assertThat(service.verify(signPackage, false).getStatus())
                .isEqualTo(OaSignVerificationStatus.MISMATCH);
        sample.setDocumentId(null);

        archiveEvidence(null, OaSignFileEvidenceType.REVIEW_PDF,
                "unknown-package-evidence.pdf", "%PDF-1.4 unknown"
                        .getBytes(StandardCharsets.ISO_8859_1), "SP-700-V2");
        assertThat(service.verify(signPackage, false).getStatus())
                .isEqualTo(OaSignVerificationStatus.MISMATCH);
    }

    @Test
    @DisplayName("包级签名样本归档文件不存在时返回文件缺失")
    void shouldReportMissingPackageSignatureSampleFile() throws Exception
    {
        OaSignFileEvidence sample = prepareSignatureFirstFinalPackage(true);
        Files.delete(storage.resolveAuthorizedFile(sample.getFileUrl()));

        assertThat(service.verify(signPackage, false).getStatus())
                .isEqualTo(OaSignVerificationStatus.FILE_MISSING);
    }

    @Test
    @DisplayName("早期签名优先可无样本但公司优先不接受包级签名样本")
    void shouldAllowEarlySignatureFirstWithoutSampleAndRejectCompanyFirstSample()
    {
        signPackage.setSigningSequence(OaSignSigningSequence.SIGNATURE_FIRST);
        assertThat(service.verify(signPackage, false).getStatus())
                .isEqualTo(OaSignVerificationStatus.VERIFIED);

        prepareSignatureFirstFinalPackage(true);
        signPackage.setSigningSequence(OaSignSigningSequence.COMPANY_FIRST);
        assertThat(service.verify(signPackage, false).getStatus())
                .isEqualTo(OaSignVerificationStatus.MISMATCH);
    }

    @Test
    @DisplayName("已确认包按documentId稳定顺序重算正文根并校验归档PDF")
    void shouldVerifyCurrentFinalArchiveEvidenceAndStableContentRoot()
    {
        OaSignPackageDocument secondDocument = addUnsignedDocument(19L, "薪酬确认书", "salary");
        when(documentMapper.selectDocumentsByPackageId(700L))
                .thenReturn(List.of(document, secondDocument));

        signPackage.setFinalDocumentVersion("SP-700-V2");
        addFinalEvidence(document, "labor", sha256("labor-content"), true);
        addFinalEvidence(secondDocument, "salary", sha256("salary-content"), true);
        Map<Long, String> contentHashes = new LinkedHashMap<>();
        contentHashes.put(document.getDocumentId(), document.getFinalContentHash());
        contentHashes.put(secondDocument.getDocumentId(), secondDocument.getFinalContentHash());
        signPackage.setFinalDocumentRootHash(finalRoot(contentHashes));
        signPackage.setFinalArchiveRootHash(finalRoot(Map.of(
                document.getDocumentId(), document.getFinalArchivePdfHash(),
                secondDocument.getDocumentId(), secondDocument.getFinalArchivePdfHash())));
        events = validFinalEvents();
        when(eventMapper.selectEventsByPackageId(700L)).thenReturn(events);

        OaSignVerificationResult result = service.verify(signPackage, false);

        assertThat(result.getStatus()).isEqualTo(OaSignVerificationStatus.VERIFIED);
        assertThat(result.getDocumentResults()).hasSize(2)
                .allSatisfy(row -> assertThat(row.getStatus()).isEqualTo(OaSignVerificationStatus.VERIFIED));
        assertThat(evidence).extracting(OaSignFileEvidence::getEvidenceType)
                .contains(OaSignFileEvidenceType.FINAL_PENDING_PDF,
                        OaSignFileEvidenceType.FINAL_ARCHIVE_PDF)
                .doesNotContain(OaSignFileEvidenceType.FINAL_SIGNED_PDF);
    }

    @Test
    @DisplayName("签名优先归档只要求最终文件证据，不强制旧流程签后PDF和证书")
    void shouldVerifySignatureFirstArchiveWithoutLegacySignedArtifacts()
    {
        prepareSignatureFirstFinalPackage(true);
        signPackage.setStatus("signed");
        document.setSigned("N");
        document.setSignedPdfHash(null);
        document.setSignatureHash(null);
        document.setCertificateHash(null);
        evidence.removeIf(row -> "SP-700-V1".equals(row.getDocumentVersion())
                && (row.getEvidenceType() == OaSignFileEvidenceType.SIGNATURE_IMAGE
                        || row.getEvidenceType() == OaSignFileEvidenceType.SIGNED_PDF
                        || row.getEvidenceType() == OaSignFileEvidenceType.SIGN_CERTIFICATE));
        OaSignFileEvidence archive = archiveEvidence(document.getDocumentId(),
                OaSignFileEvidenceType.FINAL_ARCHIVE_PDF, "labor-final-archive.pdf",
                "%PDF-1.4 labor final archive".getBytes(StandardCharsets.ISO_8859_1),
                "SP-700-V2");
        document.setFinalArchivePdfHash(archive.getFileHash());
        signPackage.setFinalArchiveRootHash(finalRoot(Map.of(
                document.getDocumentId(), document.getFinalArchivePdfHash())));
        events = validFinalEvents();
        when(eventMapper.selectEventsByPackageId(700L)).thenReturn(events);

        OaSignVerificationResult result = service.verify(signPackage, false);

        assertThat(result.getStatus()).isEqualTo(OaSignVerificationStatus.VERIFIED);
        assertThat(result.getDocumentResults()).singleElement()
                .satisfies(row -> assertThat(row.getStatus())
                        .isEqualTo(OaSignVerificationStatus.VERIFIED));
        assertThat(evidence).extracting(OaSignFileEvidence::getEvidenceType)
                .contains(OaSignFileEvidenceType.SIGNATURE_SAMPLE,
                        OaSignFileEvidenceType.SIGNATURE_IMAGE,
                        OaSignFileEvidenceType.FINAL_PENDING_PDF,
                        OaSignFileEvidenceType.FINAL_ARCHIVE_PDF)
                .doesNotContain(OaSignFileEvidenceType.SIGNED_PDF,
                        OaSignFileEvidenceType.SIGN_CERTIFICATE);
    }

    @Test
    @DisplayName("签名优先最终文档签名图必须绑定唯一包级签名样本")
    void shouldRejectFinalSignatureImageDifferentFromPackageSample() throws Exception
    {
        prepareSignatureFirstFinalPackage(true);
        OaSignFileEvidence finalSignature = evidence.stream()
                .filter(row -> row.getEvidenceType() == OaSignFileEvidenceType.SIGNATURE_IMAGE)
                .filter(row -> "SP-700-V2".equals(row.getDocumentVersion()))
                .findFirst().orElseThrow();
        byte[] differentSignature = "different-final-signature".getBytes(StandardCharsets.UTF_8);
        Files.write(storage.resolveAuthorizedFile(finalSignature.getFileUrl()), differentSignature);
        finalSignature.setFileHash(sha256("different-final-signature"));
        finalSignature.setFileSize((long) differentSignature.length);

        OaSignVerificationResult result = service.verify(signPackage, false);

        assertThat(result.getStatus()).isEqualTo(OaSignVerificationStatus.MISMATCH);
        assertThat(result.getDocumentResults()).singleElement()
                .satisfies(row -> assertThat(row.getStatus())
                        .isEqualTo(OaSignVerificationStatus.MISMATCH));
    }

    @Test
    @DisplayName("公司优先已签文件仍必须保留旧流程签名PDF和证书")
    void shouldRejectCompanyFirstArchiveWithoutLegacySignedArtifacts()
    {
        signPackage.setSigningSequence(OaSignSigningSequence.COMPANY_FIRST);
        evidence.removeIf(row -> row.getEvidenceType() == OaSignFileEvidenceType.SIGNATURE_IMAGE
                || row.getEvidenceType() == OaSignFileEvidenceType.SIGNED_PDF
                || row.getEvidenceType() == OaSignFileEvidenceType.SIGN_CERTIFICATE);

        OaSignVerificationResult result = service.verify(signPackage, false);

        assertThat(result.getStatus()).isEqualTo(OaSignVerificationStatus.MISMATCH);
        assertThat(result.getDocumentResults()).singleElement()
                .satisfies(row -> assertThat(row.getStatus())
                        .isEqualTo(OaSignVerificationStatus.MISMATCH));
    }

    @Test
    @DisplayName("正文根或当前归档PDF证据不匹配时返回不一致")
    void shouldReturnMismatchForFinalContentRootOrArchiveEvidence()
    {
        signPackage.setFinalDocumentVersion("SP-700-V2");
        addFinalEvidence(document, "labor", sha256("labor-content"), true);
        String validRoot = finalRoot(Map.of(document.getDocumentId(), document.getFinalContentHash()));
        String validArchiveRoot = finalRoot(Map.of(
                document.getDocumentId(), document.getFinalArchivePdfHash()));
        signPackage.setFinalArchiveRootHash(validArchiveRoot);
        signPackage.setFinalDocumentRootHash("0".repeat(64));
        events = validFinalEvents();
        when(eventMapper.selectEventsByPackageId(700L)).thenReturn(events);

        OaSignVerificationResult rootMismatch = service.verify(signPackage, false);

        assertThat(rootMismatch.getStatus()).isEqualTo(OaSignVerificationStatus.MISMATCH);
        assertThat(rootMismatch.getDocumentResults()).singleElement()
                .satisfies(row -> assertThat(row.getStatus()).isEqualTo(OaSignVerificationStatus.MISMATCH));

        signPackage.setFinalDocumentRootHash(validRoot);
        evidenceOf(OaSignFileEvidenceType.FINAL_ARCHIVE_PDF).setFileHash("f".repeat(64));

        assertThat(service.verify(signPackage, false).getStatus())
                .isEqualTo(OaSignVerificationStatus.MISMATCH);

        evidenceOf(OaSignFileEvidenceType.FINAL_ARCHIVE_PDF)
                .setFileHash(document.getFinalArchivePdfHash());
        signPackage.setFinalArchiveRootHash("f".repeat(64));

        assertThat(service.verify(signPackage, false).getStatus())
                .isEqualTo(OaSignVerificationStatus.MISMATCH);
    }

    private OaSignFileEvidence archiveEvidence(OaSignFileEvidenceType type, String name, byte[] bytes)
    {
        return archiveEvidence(type, name, bytes, "SP-700-V1");
    }

    private OaSignFileEvidence archiveEvidence(OaSignFileEvidenceType type, String name, byte[] bytes,
            String documentVersion)
    {
        return archiveEvidence(71L, type, name, bytes, documentVersion);
    }

    private OaSignFileEvidence archiveEvidence(Long documentId, OaSignFileEvidenceType type,
            String name, byte[] bytes, String documentVersion)
    {
        StagedSignFile file = storage.promote(storage.stage(null, 700L, documentVersion, name, bytes));
        OaSignFileEvidence row = new OaSignFileEvidence();
        row.setEvidenceId((long) evidence.size() + 1);
        row.setPackageId(700L);
        row.setDocumentId(documentId);
        row.setDocumentVersion(documentVersion);
        row.setEvidenceType(type);
        row.setFileUrl(file.getArchiveRelativePath());
        row.setFileHash(file.getFileHash());
        row.setFileSize(file.getFileSize());
        row.setGeneratedTime(new Date(1_700_000_000_000L));
        evidence.add(row);
        return row;
    }

    private OaSignPackageDocument addUnsignedDocument(Long documentId, String documentName,
            String filePrefix)
    {
        OaSignPackageDocument row = new OaSignPackageDocument();
        row.setDocumentId(documentId);
        row.setPackageId(700L);
        row.setDocumentName(documentName);
        row.setDocumentVersion("SP-700-V1");
        row.setEmployeeSignRequired("N");
        row.setCompanySealRequired("N");
        row.setSigned("Y");
        archiveEvidence(documentId, OaSignFileEvidenceType.RENDERED_SOURCE,
                filePrefix + "-rendered.docx", (filePrefix + "-rendered").getBytes(StandardCharsets.UTF_8),
                "SP-700-V1");
        OaSignFileEvidence review = archiveEvidence(documentId, OaSignFileEvidenceType.REVIEW_PDF,
                filePrefix + "-review.pdf", ("%PDF-1.4 " + filePrefix + " review")
                        .getBytes(StandardCharsets.ISO_8859_1), "SP-700-V1");
        row.setReviewPdfHash(review.getFileHash());
        return row;
    }

    private OaSignFileEvidence prepareSignatureFirstFinalPackage(boolean includeSample)
    {
        signPackage.setSigningSequence(OaSignSigningSequence.SIGNATURE_FIRST);
        signPackage.setStatus("pending_final_confirm");
        signPackage.setFinalDocumentVersion("SP-700-V2");
        addFinalEvidence(document, "labor", sha256("labor-content"), false);
        signPackage.setFinalDocumentRootHash(finalRoot(Map.of(
                document.getDocumentId(), document.getFinalContentHash())));
        return includeSample ? addValidSignatureSample() : null;
    }

    private void prepareCompanyFirstPreSignCandidate()
    {
        signPackage.setSigningSequence(OaSignSigningSequence.COMPANY_FIRST);
        signPackage.setStatus(OaSignPackageStatus.PENDING_SIGN);
        signPackage.setFinalDocumentVersion("SP-700-V2");
        signPackage.setFinalConfirmationStatus("PREPARED_NOT_SENT");
        document.setSigned("N");
        document.setSignedPdfHash(null);
        document.setSignatureHash(null);
        document.setCertificateHash(null);
        evidence.removeIf(row -> row.getEvidenceType() == OaSignFileEvidenceType.SIGNATURE_IMAGE
                || row.getEvidenceType() == OaSignFileEvidenceType.SIGNED_PDF
                || row.getEvidenceType() == OaSignFileEvidenceType.SIGN_CERTIFICATE);
        document.setEmployeeSignRequired("N");
        addFinalEvidence(document, "company-first", sha256("company-first-content"), false);
        document.setEmployeeSignRequired("Y");
        signPackage.setFinalDocumentRootHash(finalRoot(Map.of(
                document.getDocumentId(), document.getFinalContentHash())));
        OaSignEvent prepared = event(1L, "FINAL_CONTRACT_PREPARED", null,
                new Date(1_700_000_000_000L));
        prepared.setEventHash(OaSignVerificationService.calculateEventHash(prepared));
        events = new ArrayList<>(List.of(prepared));
        when(eventMapper.selectEventsByPackageId(700L)).thenReturn(events);
    }

    private OaSignFileEvidence addValidSignatureSample()
    {
        OaSignFileEvidence sample = archiveEvidence(null,
                OaSignFileEvidenceType.SIGNATURE_SAMPLE, "task-signature-sample.png",
                SIGNATURE_SAMPLE_CONTENT.getBytes(StandardCharsets.UTF_8), "SAMPLE-9900001");
        signPackage.setSignatureSampleFileUrl(
                "/profile/private/sign-package/" + sample.getFileUrl());
        signPackage.setSignatureSampleHash(sample.getFileHash());
        signPackage.setSignatureSampleTime(sample.getGeneratedTime());
        return sample;
    }

    private void addFinalEvidence(OaSignPackageDocument target, String filePrefix,
            String finalContentHash, boolean includeArchive)
    {
        target.setFinalDocumentVersion("SP-700-V2");
        target.setFinalContentHash(finalContentHash);
        target.setCompanySealRequired("N");
        archiveEvidence(target.getDocumentId(), OaSignFileEvidenceType.FINAL_RENDERED_SOURCE,
                filePrefix + "-final-rendered.docx",
                (filePrefix + "-final-rendered").getBytes(StandardCharsets.UTF_8), "SP-700-V2");
        archiveEvidence(target.getDocumentId(), OaSignFileEvidenceType.FINAL_REVIEW_PDF,
                filePrefix + "-final-review.pdf", ("%PDF-1.4 " + filePrefix + " final review")
                        .getBytes(StandardCharsets.ISO_8859_1), "SP-700-V2");
        if ("Y".equalsIgnoreCase(target.getEmployeeSignRequired()))
        {
            String signatureContent = OaSignSigningSequence.SIGNATURE_FIRST
                    .equals(signPackage.getSigningSequence())
                            ? SIGNATURE_SAMPLE_CONTENT : "png-signature";
            archiveEvidence(target.getDocumentId(), OaSignFileEvidenceType.SIGNATURE_IMAGE,
                    filePrefix + "-final-signature.png",
                    signatureContent.getBytes(StandardCharsets.UTF_8), "SP-700-V2");
        }
        OaSignFileEvidence pending = archiveEvidence(target.getDocumentId(),
                OaSignFileEvidenceType.FINAL_PENDING_PDF, filePrefix + "-final-pending.pdf",
                ("%PDF-1.4 " + filePrefix + " final pending")
                        .getBytes(StandardCharsets.ISO_8859_1), "SP-700-V2");
        target.setFinalPdfHash(pending.getFileHash());
        if (includeArchive)
        {
            OaSignFileEvidence archive = archiveEvidence(target.getDocumentId(),
                    OaSignFileEvidenceType.FINAL_ARCHIVE_PDF, filePrefix + "-final-archive.pdf",
                    ("%PDF-1.4 " + filePrefix + " final archive")
                            .getBytes(StandardCharsets.ISO_8859_1), "SP-700-V2");
            target.setFinalArchivePdfHash(archive.getFileHash());
        }
    }

    private OaSignFileEvidence evidenceOf(OaSignFileEvidenceType type)
    {
        return evidence.stream().filter(row -> type == row.getEvidenceType()).findFirst().orElseThrow();
    }

    private List<OaSignEvent> validEvents()
    {
        OaSignEvent first = event(1L, "PACKAGE_SIGN_REQUESTED", null, new Date(1_700_000_000_000L));
        first.setEventHash(OaSignVerificationService.calculateEventHash(first));
        OaSignEvent second = event(2L, "PACKAGE_SIGNED", first.getEventHash(), new Date(1_700_000_001_000L));
        second.setEventHash(OaSignVerificationService.calculateEventHash(second));
        return new ArrayList<>(List.of(first, second));
    }

    private List<OaSignEvent> validFinalEvents()
    {
        List<OaSignEvent> rows = validEvents();
        OaSignEvent confirmed = event(3L, "FINAL_CONTRACT_CONFIRMED",
                rows.get(rows.size() - 1).getEventHash(), new Date(1_700_000_002_000L));
        confirmed.setEventHash(OaSignVerificationService.calculateEventHash(confirmed));
        rows.add(confirmed);
        return rows;
    }

    private String finalRoot(Map<Long, String> hashes)
    {
        StringBuilder payload = new StringBuilder();
        hashes.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                payload.append(entry.getKey()).append(':').append(entry.getValue()).append('\n'));
        return sha256(payload.toString());
    }

    private String sha256(String value)
    {
        try
        {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException(e);
        }
    }

    private OaSignEvent event(Long id, String type, String previousHash, Date createTime)
    {
        OaSignEvent event = new OaSignEvent();
        event.setEventId(id);
        event.setPackageId(700L);
        event.setEventType(type);
        event.setOperatorUserId(960L);
        event.setOperatorRole("EMPLOYEE");
        event.setEventPayload("documentVersion=SP-700-V1");
        event.setPrevEventHash(previousHash);
        event.setRequestId("request-700");
        event.setCreateTime(createTime);
        return event;
    }
}
