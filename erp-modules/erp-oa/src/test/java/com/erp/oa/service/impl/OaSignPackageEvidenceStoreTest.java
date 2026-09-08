package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.constant.OaSignFileEvidenceType;
import com.erp.oa.domain.OaSignFileEvidence;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPackageDocument;
import com.erp.oa.domain.vo.SignedPdfResult;
import com.erp.oa.domain.vo.StagedSignFile;
import com.erp.oa.mapper.OaSignFileEvidenceMapper;

@DisplayName("签约包证据存储")
class OaSignPackageEvidenceStoreTest
{
    private final OaSignFileEvidenceMapper evidenceMapper =
            mock(OaSignFileEvidenceMapper.class);
    private final OaSignPlacementPolicyService placementPolicyService =
            mock(OaSignPlacementPolicyService.class);
    private final List<OaSignFileEvidence> inserted = new ArrayList<>();
    private final AtomicLong nextEvidenceId = new AtomicLong(1L);
    private final OaSignPackageEvidenceStore store =
            new OaSignPackageEvidenceStore(
                    evidenceMapper, placementPolicyService);

    @BeforeEach
    void captureInsertedEvidence()
    {
        when(evidenceMapper.insertOaSignFileEvidence(any()))
                .thenAnswer(invocation -> {
                    OaSignFileEvidence evidence = invocation.getArgument(0);
                    evidence.setEvidenceId(nextEvidenceId.getAndIncrement());
                    inserted.add(evidence);
                    return 1;
                });
    }

    @Test
    @DisplayName("生成文档证据优先使用归档路径并关联阅读PDF到源文件")
    void recordsGeneratedDocumentEvidenceChain()
    {
        OaSignPackage signPackage = signPackage("SP-90-V1");
        OaSignPackageDocument document = document("SP-90-V1");
        GeneratedSignDocument generated = generatedDocument("generated");
        Date generatedTime = new Date(1_000L);

        store.recordGeneratedDocument(
                signPackage, document, generated, generatedTime);

        OaSignFileEvidence rendered =
                evidence(OaSignFileEvidenceType.RENDERED_SOURCE);
        OaSignFileEvidence review =
                evidence(OaSignFileEvidenceType.REVIEW_PDF);
        assertThat(rendered.getFileUrl())
                .isEqualTo("archive/generated.docx");
        assertThat(review.getFileUrl())
                .isEqualTo("archive/generated.pdf");
        assertThat(review.getSourceEvidenceId())
                .isEqualTo(rendered.getEvidenceId());
        assertThat(review.getGeneratedTime()).isEqualTo(generatedTime);
    }

    @Test
    @DisplayName("员工签署证据关联阅读PDF并独立记录签名、已签PDF和证书")
    void recordsSignedEvidenceChain()
    {
        OaSignPackage signPackage = signPackage("SP-90-V1");
        OaSignPackageDocument document = document("SP-90-V1");
        OaSignFileEvidence review = sourceEvidence(
                77L, "review.pdf", "review-hash", 20L);
        when(evidenceMapper.selectEvidenceByType(
                11L, "SP-90-V1", OaSignFileEvidenceType.REVIEW_PDF))
                .thenReturn(review);
        SignedPdfResult signed = signedPdf("signed");
        GeneratedSignDocument certificate = generatedDocument("certificate");

        store.recordSigned(signPackage, document, signed,
                certificate, new Date(2_000L));

        assertThat(inserted).extracting(OaSignFileEvidence::getEvidenceType)
                .containsExactly(
                        OaSignFileEvidenceType.SIGNATURE_IMAGE,
                        OaSignFileEvidenceType.SIGNED_PDF,
                        OaSignFileEvidenceType.SIGN_CERTIFICATE);
        assertThat(evidence(OaSignFileEvidenceType.SIGNED_PDF)
                .getSourceEvidenceId()).isEqualTo(77L);
        assertThat(evidence(OaSignFileEvidenceType.SIGN_CERTIFICATE)
                .getFileUrl()).isEqualTo("archive/certificate.docx");
    }

    @Test
    @DisplayName("最终候选证据保持源文件、阅读PDF和待确认PDF父子链")
    void recordsFinalCandidateEvidenceChain()
    {
        OaSignPackage signPackage = signPackage("SP-90-F1");
        OaSignPackageDocument document = document("SP-90-V1");
        GeneratedSignDocument generated = generatedDocument("final");
        SignedPdfResult signed = signedPdf("final");
        StagedSignFile seal = stagedFile("seal");
        when(placementPolicyService.requiresEmployeeSignature(document))
                .thenReturn(true);
        when(placementPolicyService.requiresCompanySeal(document))
                .thenReturn(true);

        store.recordFinalCandidate(signPackage, document, generated,
                signed, seal, new Date(3_000L));

        assertThat(inserted).extracting(OaSignFileEvidence::getEvidenceType)
                .containsExactly(
                        OaSignFileEvidenceType.FINAL_RENDERED_SOURCE,
                        OaSignFileEvidenceType.FINAL_REVIEW_PDF,
                        OaSignFileEvidenceType.SIGNATURE_IMAGE,
                        OaSignFileEvidenceType.COMPANY_SEAL,
                        OaSignFileEvidenceType.FINAL_PENDING_PDF);
        OaSignFileEvidence rendered =
                evidence(OaSignFileEvidenceType.FINAL_RENDERED_SOURCE);
        OaSignFileEvidence review =
                evidence(OaSignFileEvidenceType.FINAL_REVIEW_PDF);
        assertThat(review.getSourceEvidenceId())
                .isEqualTo(rendered.getEvidenceId());
        assertThat(evidence(OaSignFileEvidenceType.FINAL_PENDING_PDF)
                .getSourceEvidenceId()).isEqualTo(review.getEvidenceId());
        assertThat(evidence(OaSignFileEvidenceType.COMPANY_SEAL)
                .getFileUrl()).isEqualTo("archive/seal.png");
    }

    @Test
    @DisplayName("要求企业章的最终候选缺少归档证据时失败关闭")
    void rejectsMissingFinalCandidateSealEvidence()
    {
        OaSignPackage signPackage = signPackage("SP-90-F1");
        OaSignPackageDocument document = document("SP-90-V1");
        when(placementPolicyService.requiresCompanySeal(document))
                .thenReturn(true);

        assertThatThrownBy(() -> store.recordFinalCandidate(
                signPackage, document, generatedDocument("final"),
                signedPdf("final"), null, new Date(4_000L)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("企业章归档证据缺失");
    }

    @Test
    @DisplayName("公司先签候选复制冻结源证据并保持最终版本关联")
    void recordsCompanyFirstFinalEvidenceChain()
    {
        OaSignPackage signPackage = signPackage("SP-90-V1");
        OaSignPackageDocument document = document("SP-90-V1");
        OaSignFileEvidence source = sourceEvidence(
                80L, "archive/source.docx", "source-hash", 30L);
        OaSignFileEvidence review = sourceEvidence(
                81L, "archive/review.pdf", "review-hash", 40L);
        when(evidenceMapper.selectEvidenceByType(
                11L, "SP-90-V1", OaSignFileEvidenceType.RENDERED_SOURCE))
                .thenReturn(source);
        when(evidenceMapper.selectEvidenceByType(
                11L, "SP-90-V1", OaSignFileEvidenceType.REVIEW_PDF))
                .thenReturn(review);
        when(placementPolicyService.requiresCompanySeal(document))
                .thenReturn(true);

        store.recordCompanyFirstFinalCandidate(
                signPackage, document, "SP-90-F1", signedPdf("final"),
                stagedFile("seal"), new Date(5_000L));

        OaSignFileEvidence finalRendered =
                evidence(OaSignFileEvidenceType.FINAL_RENDERED_SOURCE);
        OaSignFileEvidence finalReview =
                evidence(OaSignFileEvidenceType.FINAL_REVIEW_PDF);
        assertThat(finalRendered.getDocumentVersion()).isEqualTo("SP-90-F1");
        assertThat(finalRendered.getFileUrl()).isEqualTo("archive/source.docx");
        assertThat(finalReview.getSourceEvidenceId())
                .isEqualTo(finalRendered.getEvidenceId());
        assertThat(evidence(OaSignFileEvidenceType.FINAL_PENDING_PDF)
                .getSourceEvidenceId()).isEqualTo(finalReview.getEvidenceId());
    }

    @Test
    @DisplayName("公司先签候选拒绝缺失或不完整的冻结源证据")
    void rejectsIncompleteCompanyFirstSourceEvidence()
    {
        OaSignPackage signPackage = signPackage("SP-90-V1");
        OaSignPackageDocument document = document("SP-90-V1");
        OaSignFileEvidence incomplete = sourceEvidence(
                80L, null, "source-hash", 30L);
        when(evidenceMapper.selectEvidenceByType(
                11L, "SP-90-V1", OaSignFileEvidenceType.RENDERED_SOURCE))
                .thenReturn(incomplete);

        assertThatThrownBy(() -> store.recordCompanyFirstFinalCandidate(
                signPackage, document, "SP-90-F1", signedPdf("final"),
                null, new Date(6_000L)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("冻结待发文件证据不完整");
        assertThat(inserted).isEmpty();
    }

    @Test
    @DisplayName("任务签名样本和最终归档使用各自不可变版本")
    void recordsSignatureSampleAndFinalArchive()
    {
        OaSignPackage signPackage = signPackage("SP-90-V1");
        signPackage.setFinalDocumentVersion("SP-90-A1");
        OaSignPackageDocument document = document("SP-90-V1");

        store.recordTaskSignatureSample(
                signPackage, stagedFile("sample"), 501L, new Date(7_000L));
        store.recordFinalArchive(
                signPackage, document, signedPdf("archive"), new Date(8_000L));

        assertThat(evidence(OaSignFileEvidenceType.SIGNATURE_SAMPLE)
                .getDocumentVersion()).isEqualTo("SAMPLE-501");
        assertThat(evidence(OaSignFileEvidenceType.FINAL_ARCHIVE_PDF)
                .getDocumentVersion()).isEqualTo("SP-90-A1");
    }

    private OaSignPackage signPackage(String documentVersion)
    {
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(90L);
        signPackage.setDocumentVersion(documentVersion);
        return signPackage;
    }

    private OaSignPackageDocument document(String documentVersion)
    {
        OaSignPackageDocument document = new OaSignPackageDocument();
        document.setDocumentId(11L);
        document.setPackageId(90L);
        document.setDocumentVersion(documentVersion);
        return document;
    }

    private GeneratedSignDocument generatedDocument(String prefix)
    {
        return new GeneratedSignDocument(
                "/private/" + prefix + ".docx", prefix + "-source-hash",
                "/private/" + prefix + ".pdf", prefix + "-pdf-hash", "SP-90-F1",
                "archive/" + prefix + ".docx", 30L,
                "archive/" + prefix + ".pdf", 40L);
    }

    private SignedPdfResult signedPdf(String prefix)
    {
        return new SignedPdfResult(
                "archive/" + prefix + ".pdf", "/private/" + prefix + ".pdf",
                prefix + "-pdf-hash", 40L,
                "archive/" + prefix + ".png", "/private/" + prefix + ".png",
                prefix + "-signature-hash", 10L, "seal-hash", 2);
    }

    private StagedSignFile stagedFile(String prefix)
    {
        return new StagedSignFile(
                Path.of("stage"), Path.of("stage/" + prefix + ".png"),
                "archive/" + prefix + ".png", prefix + ".png",
                prefix + "-hash", 10L);
    }

    private OaSignFileEvidence sourceEvidence(Long evidenceId,
            String fileUrl, String fileHash, Long fileSize)
    {
        OaSignFileEvidence evidence = new OaSignFileEvidence();
        evidence.setEvidenceId(evidenceId);
        evidence.setFileUrl(fileUrl);
        evidence.setFileHash(fileHash);
        evidence.setFileSize(fileSize);
        return evidence;
    }

    private OaSignFileEvidence evidence(OaSignFileEvidenceType type)
    {
        return inserted.stream()
                .filter(value -> type == value.getEvidenceType())
                .findFirst()
                .orElseThrow();
    }
}
