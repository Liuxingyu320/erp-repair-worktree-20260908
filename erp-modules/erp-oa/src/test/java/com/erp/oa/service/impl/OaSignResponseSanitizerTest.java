package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.oa.constant.OaSignPackageStatus;
import com.erp.oa.constant.OaSignSigningSequence;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPackageDocument;

@DisplayName("员工签约响应脱敏")
class OaSignResponseSanitizerTest
{
    @Test
    @DisplayName("签名先行最终候选显式发送前隐藏候选事实和文件绑定")
    void shouldHidePreparedSignatureFirstCandidateUntilSent()
    {
        OaSignPackage source = new OaSignPackage();
        source.setPackageId(18L);
        source.setStatus(OaSignPackageStatus.PENDING_COMPANY);
        source.setSigningSequence(OaSignSigningSequence.SIGNATURE_FIRST);
        source.setFinalConfirmationStatus("PREPARED_NOT_SENT");
        source.setLegalEntityNameSnapshot("尚未发送公司");
        source.setSealNameSnapshot("尚未发送印章");
        source.setDocumentVersion("REGENERATED-V2");
        source.setFinalDocumentVersion("FINAL-V3");
        source.setFinalDocumentRootHash("root-hash");
        OaSignPackageDocument document = new OaSignPackageDocument();
        document.setDocumentId(101L);
        document.setReviewPdfHash("regenerated-review-hash");
        document.setFinalPdfHash("pending-final-hash");
        source.setDocuments(List.of(document));

        OaSignPackage hidden = OaSignResponseSanitizer.employeePackage(source);

        assertThat(hidden.getLegalEntityNameSnapshot()).isNull();
        assertThat(hidden.getSealNameSnapshot()).isNull();
        assertThat(hidden.getDocumentVersion()).isNull();
        assertThat(hidden.getFinalDocumentVersion()).isNull();
        assertThat(hidden.getFinalDocumentRootHash()).isNull();
        assertThat(hidden.getFinalConfirmationStatus()).isEqualTo("WAITING_COMPANY");
        assertThat(hidden.getDocuments()).isEmpty();

        source.setStatus(OaSignPackageStatus.PENDING_FINAL_CONFIRM);
        source.setFinalConfirmationStatus("PENDING");
        OaSignPackage sent = OaSignResponseSanitizer.employeePackage(source);
        assertThat(sent.getLegalEntityNameSnapshot()).isEqualTo("尚未发送公司");
        assertThat(sent.getSealNameSnapshot()).isEqualTo("尚未发送印章");
        assertThat(sent.getFinalDocumentVersion()).isEqualTo("FINAL-V3");
        assertThat(sent.getFinalDocumentRootHash()).isEqualTo("root-hash");
        assertThat(sent.getDocuments()).hasSize(1);
        assertThat(sent.getDocuments().get(0).getFinalPdfHash())
                .isEqualTo("pending-final-hash");
    }

    @Test
    @DisplayName("只返回展示事实、绑定哈希和可用性标志")
    void shouldExposeCapabilitiesWithoutStoragePathsOrInternalEvidence()
    {
        OaSignPackage source = new OaSignPackage();
        source.setPackageId(18L);
        source.setPackageNo("SP-18");
        source.setEmployeeId(940L);
        source.setEmployeeNameSnapshot("段继康");
        source.setEmployeePhoneSnapshot("16657049808");
        source.setEmployeeIdCardSnapshot("410381198909272516");
        source.setLegalEntityIdSnapshot(1L);
        source.setLegalEntityCodeSnapshot("91330100TEST");
        source.setLegalEntityNameSnapshot("杭州翕然茶业有限公司");
        source.setLegalEntityCreditCodeSnapshot("91330100TEST");
        source.setSealIdSnapshot(9L);
        source.setSealNameSnapshot("合同专用章");
        source.setSealImageUrlSnapshot("private/seal.png");
        source.setSealImageHashSnapshot("seal-hash");
        source.setSignatureSampleFileUrl("private/signature.png");
        source.setSignatureSampleHash("signature-hash");
        source.setFinalDocumentVersion("V2");
        source.setFinalDocumentRootHash("root-hash");

        OaSignPackageDocument document = new OaSignPackageDocument();
        document.setDocumentId(101L);
        document.setPackageId(18L);
        document.setTemplateId(7L);
        document.setDocumentName("劳动合同");
        document.setReviewPdfUrl("private/review.pdf");
        document.setReviewPdfHash("review-hash");
        document.setSignedPdfUrl("private/signed.pdf");
        document.setSignedPdfHash("signed-hash");
        document.setCertificateFileUrl("private/certificate.pdf");
        document.setCertificateHash("certificate-hash");
        document.setFinalPdfUrl("private/pending-final.pdf");
        document.setFinalPdfHash("final-bind-hash");
        document.setFinalContentHash("stable-content-hash");
        document.setFinalArchivePdfUrl("private/final-archive.pdf");
        document.setFinalArchivePdfHash("archive-hash");
        document.setSignatureFileUrl("private/signature.png");
        document.setSignatureHash("signature-hash");
        source.setDocuments(List.of(document));

        OaSignPackage result = OaSignResponseSanitizer.employeePackage(source);
        OaSignPackageDocument resultDocument = result.getDocuments().get(0);

        assertThat(result.getPackageId()).isEqualTo(18L);
        assertThat(result.getLegalEntityNameSnapshot()).isEqualTo("杭州翕然茶业有限公司");
        assertThat(result.getSealNameSnapshot()).isEqualTo("合同专用章");
        assertThat(result.getFinalDocumentRootHash()).isEqualTo("root-hash");
        assertThat(result.getEmployeeId()).isNull();
        assertThat(result.getEmployeePhoneSnapshot()).isNull();
        assertThat(result.getEmployeeIdCardSnapshot()).isNull();
        assertThat(result.getLegalEntityIdSnapshot()).isNull();
        assertThat(result.getLegalEntityCreditCodeSnapshot()).isNull();
        assertThat(result.getSealIdSnapshot()).isNull();
        assertThat(result.getSealImageUrlSnapshot()).isNull();
        assertThat(result.getSealImageHashSnapshot()).isNull();
        assertThat(result.getSignatureSampleFileUrl()).isNull();
        assertThat(result.getSignatureSampleHash()).isNull();

        assertThat(resultDocument.getReviewPdfHash()).isEqualTo("review-hash");
        assertThat(resultDocument.getFinalPdfHash()).isEqualTo("final-bind-hash");
        assertThat(resultDocument.getSignedFileAvailable()).isTrue();
        assertThat(resultDocument.getCertificateAvailable()).isTrue();
        assertThat(resultDocument.getFinalFileAvailable()).isTrue();
        assertThat(resultDocument.getPackageId()).isNull();
        assertThat(resultDocument.getTemplateId()).isNull();
        assertThat(resultDocument.getReviewPdfUrl()).isNull();
        assertThat(resultDocument.getSignedPdfUrl()).isNull();
        assertThat(resultDocument.getSignedPdfHash()).isNull();
        assertThat(resultDocument.getCertificateFileUrl()).isNull();
        assertThat(resultDocument.getCertificateHash()).isNull();
        assertThat(resultDocument.getFinalPdfUrl()).isNull();
        assertThat(resultDocument.getFinalContentHash()).isNull();
        assertThat(resultDocument.getFinalArchivePdfUrl()).isNull();
        assertThat(resultDocument.getFinalArchivePdfHash()).isNull();
        assertThat(resultDocument.getSignatureFileUrl()).isNull();
        assertThat(resultDocument.getSignatureHash()).isNull();
    }
}
