package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.oa.config.OaSignFileProperties;
import com.erp.oa.constant.OaSignTemplateType;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPackageDocument;
import com.erp.oa.domain.OaSignTemplate;
import com.erp.oa.domain.vo.SignedPdfResult;

@DisplayName("独立已签PDF生成")
class OaSignedPdfServiceTest
{
    private static final String SAMPLE_REVIEW_HASH =
            "552e2b6a17e6206ea4c12c761d02abf698db3281ecf1861a858ce0024b7ce7d4";
    private static final String SAMPLE_ARCHIVE_HASH =
            "731db734f3fe4dc73a2e18aafadacd5df78e53347209e7b0020ac5f942c6cc69";
    private static final String SAMPLE_CONTENT_HASH =
            "4c5ac52dead9d598ffa410e0da70f7ad6f62c0395d3666b52223d3264e447f4d";
    private static final String SAMPLE_FINAL_ROOT_HASH =
            "2e2a61497054e8261b9cc68d54c19923f39aca79239c7d957dc1675b74a5cd98";
    private static final String SAMPLE_SIGNATURE_HASH =
            "8b03cc9e6898f1edd1d6ad4dd3594f380aaef771c67de90dbb3aa5bfe0ae59d7";
    private static final String SAMPLE_SEAL_HASH =
            "b14583e12e150ff25482e07d9ae9162c7bd5b274260254c8696d24b0926982d1";
    @TempDir
    Path tempDir;

    @Test
    @DisplayName("多页合同的签名和企业章共用唯一追加证据页")
    void shouldAppendEvidencePageAndCreateIndependentSignedPdf() throws Exception
    {
        OaSignedPdfService service = service();
        Path reviewPdf = twoPageReviewPdf();
        OaSignPackage signPackage = signPackage(400L);
        OaSignPackageDocument document = signDocument(40L, 400L, reviewPdf);
        byte[] signature = signaturePng();
        byte[] seal = sealPng();
        Date employeeSignedTime = Date.from(Instant.parse("2026-07-11T08:30:00Z"));
        Date companySealTime = Date.from(Instant.parse("2026-07-11T12:45:00Z"));

        SignedPdfResult result = service.generateSignedPdf(null, signPackage, document, reviewPdf,
                signature, seal, employeeSignedTime, companySealTime,
                "本人确认签署本签约包", null, null);

        Path signedPdf = storage().resolveAuthorizedFile(result.getArchiveRelativePath());
        assertThat(signedPdf).isNotEqualTo(reviewPdf).isNotEmptyFile();
        assertUsesPortableCrossReferenceFormat(signedPdf);
        assertThat(result.getSignedPdfUrl()).startsWith("/profile/private/sign-package/");
        assertThat(result.getSignedPdfHash()).hasSize(64).isNotEqualTo(sha256(reviewPdf));
        assertThat(result.getSignatureFileUrl()).endsWith(".png");
        assertThat(result.getSignatureHash()).isEqualTo(sha256(signature));
        assertThat(result.getSignatureFileSize()).isEqualTo(signature.length);
        assertThat(result.getCompanySealHash()).isEqualTo(sha256(seal));

        try (PDDocument source = Loader.loadPDF(reviewPdf.toFile());
                PDDocument signed = Loader.loadPDF(signedPdf.toFile()))
        {
            assertThat(signed.getNumberOfPages()).isEqualTo(source.getNumberOfPages() + 1);
            assertThat(result.getPageCount()).isEqualTo(signed.getNumberOfPages());
            String text = normalizedText(signed);
            assertThat(text).contains("最终合同电子签署确认页",
                    "本页为该合同不可分割的电子签署确认页,以源PDF SHA-256与合同正文绑定。",
                    "签约-202607110001", "Alice", "Shanghai Legal Entity",
                    "员工签名时间: 2026-07-11 16:30:00",
                    "公司盖章/最终生成时间: 2026-07-11 20:45:00",
                    "本人确认签署本签约包", "员工手写签名: 已留存", "公司印章: 已加盖",
                    hashLine("源PDF SHA-256", sha256(reviewPdf), 1),
                    hashLine("源PDF SHA-256", sha256(reviewPdf), 2),
                    hashLine("员工签名 SHA-256", sha256(signature), 1),
                    hashLine("员工签名 SHA-256", sha256(signature), 2),
                    hashLine("企业印章 SHA-256", sha256(seal), 1),
                    hashLine("企业印章 SHA-256", sha256(seal), 2));
            assertThat(text).doesNotContain("SP202607110001", "SP-400-V1",
                    "Hangzhou Store Department");
            assertThat(imageCount(signed.getPage(signed.getNumberOfPages() - 1))).isEqualTo(2);
        }

        String originalHash = sha256(signedPdf);
        Files.write(signedPdf, new byte[] { 'X' }, java.nio.file.StandardOpenOption.APPEND);
        assertThat(sha256(signedPdf)).isNotEqualTo(originalHash);
    }

    @Test
    @DisplayName("追加页对数据库上限中文值按码点截断且文本不与签章区重叠")
    @SuppressWarnings("unchecked")
    void shouldKeepMaximumBusinessTextInsideEvidencePageLayout() throws Exception
    {
        OaSignedPdfService service = service();
        OaSignPackage signPackage = signPackage(413L);
        signPackage.setEmployeeNameSnapshot("员".repeat(64));
        signPackage.setLegalEntityNameSnapshot("司".repeat(160));
        String confirmationText = "确".repeat(256);
        String reviewHash = "a".repeat(64);
        String signatureHash = "b".repeat(64);
        String sealHash = "c".repeat(64);

        List<String> lines = ReflectionTestUtils.invokeMethod(service, "evidenceLines",
                signPackage, Date.from(Instant.parse("2026-07-11T08:30:00Z")),
                Date.from(Instant.parse("2026-07-11T12:45:00Z")),
                confirmationText, reviewHash, signatureHash, sealHash);

        assertThat(lines).isNotNull()
                .contains("最终合同电子签署确认页",
                        "本页为该合同不可分割的电子签署确认页，以源PDF SHA-256与合同正文绑定。",
                        hashLine("源PDF SHA-256", reviewHash, 1),
                        hashLine("源PDF SHA-256", reviewHash, 2),
                        hashLine("员工签名 SHA-256", signatureHash, 1),
                        hashLine("员工签名 SHA-256", signatureHash, 2),
                        hashLine("企业印章 SHA-256", sealHash, 1),
                        hashLine("企业印章 SHA-256", sealHash, 2))
                .anySatisfy(line -> assertThat(line).contains("员".repeat(19) + "…"))
                .anySatisfy(line -> assertThat(line).contains("司".repeat(31) + "…"))
                .anySatisfy(line -> assertThat(line).contains("确".repeat(31) + "…"));

        try (PDDocument document = new PDDocument())
        {
            PDFont font = ReflectionTestUtils.invokeMethod(service, "loadEvidenceFont",
                    document, lines);
            assertThat(font).isNotNull();
            float availableWidth = PDRectangle.A4.getWidth()
                    - (2 * OaSignedPdfService.EVIDENCE_TEXT_X);
            assertThat(lines).allSatisfy(line -> {
                try
                {
                    float width = font.getStringWidth(line) / 1000F
                            * OaSignedPdfService.EVIDENCE_FONT_SIZE;
                    assertThat(width).as(line).isLessThanOrEqualTo(availableWidth);
                }
                catch (Exception e)
                {
                    throw new AssertionError(e);
                }
            });
        }

        float firstBaseline = PDRectangle.A4.getHeight()
                - OaSignedPdfService.EVIDENCE_TEXT_TOP_MARGIN;
        float lastBaseline = firstBaseline
                - (lines.size() - 1) * OaSignedPdfService.EVIDENCE_TEXT_LEADING;
        assertThat(lastBaseline).isGreaterThanOrEqualTo(
                OaSignedPdfService.EVIDENCE_IMAGE_TOP_Y
                        + OaSignedPdfService.EVIDENCE_TEXT_IMAGE_GAP);
    }

    @Test
    @DisplayName("追加证据页仅绘制实际存在的签名")
    void shouldAppendOnlyExistingSignatureEvidence() throws Exception
    {
        OaSignedPdfService service = service();
        Path reviewPdf = sampleReviewPdf();
        OaSignPackage signPackage = signPackage(404L);
        OaSignPackageDocument document = signDocument(44L, 404L, reviewPdf);
        byte[] signature = signaturePng();

        SignedPdfResult result = service.generateSignedPdf(null, signPackage, document, reviewPdf,
                signature, null, Date.from(Instant.parse("2026-07-11T08:30:00Z")),
                "本人确认签署本签约包", null, null);

        assertThat(result.getSignatureHash()).isEqualTo(sha256(signature));
        assertThat(result.getCompanySealHash()).isNull();
        try (PDDocument source = Loader.loadPDF(reviewPdf.toFile());
                PDDocument signed = Loader.loadPDF(storage().resolveAuthorizedFile(
                        result.getArchiveRelativePath()).toFile()))
        {
            assertThat(signed.getNumberOfPages()).isEqualTo(source.getNumberOfPages() + 1);
            PDPage evidencePage = signed.getPage(signed.getNumberOfPages() - 1);
            assertThat(imageCount(evidencePage)).isEqualTo(1);
            String text = normalizedText(signed);
            assertThat(text).contains("员工首次签名电子确认页", "员工手写签名: 已留存");
            assertThat(text).doesNotContain("公司印章:");
        }
    }

    @Test
    @DisplayName("追加证据页支持仅印章且不伪造签名元数据")
    void shouldAppendOnlySealEvidenceWithoutSignatureMetadata() throws Exception
    {
        OaSignedPdfService service = service();
        Path reviewPdf = sampleReviewPdf();
        OaSignPackage signPackage = signPackage(405L);
        OaSignPackageDocument document = signDocument(45L, 405L, reviewPdf);
        byte[] seal = sealPng();

        SignedPdfResult result = service.generateSignedPdf(null, signPackage, document, reviewPdf,
                null, seal, Date.from(Instant.parse("2026-07-11T08:30:00Z")),
                "公司完成最终文件生成", null, null);

        assertThat(result.getSignatureArchiveRelativePath()).isNull();
        assertThat(result.getSignatureFileUrl()).isNull();
        assertThat(result.getSignatureHash()).isNull();
        assertThat(result.getSignatureFileSize()).isZero();
        assertThat(result.getCompanySealHash()).isEqualTo(sha256(seal));
        try (PDDocument source = Loader.loadPDF(reviewPdf.toFile());
                PDDocument signed = Loader.loadPDF(storage().resolveAuthorizedFile(
                        result.getArchiveRelativePath()).toFile()))
        {
            assertThat(signed.getNumberOfPages()).isEqualTo(source.getNumberOfPages() + 1);
            PDPage evidencePage = signed.getPage(signed.getNumberOfPages() - 1);
            assertThat(imageCount(evidencePage)).isEqualTo(1);
            String text = normalizedText(signed);
            assertThat(text).contains("公司盖章电子确认页", "公司印章: 已加盖");
            assertThat(text).doesNotContain("员工手写签名:", "员工阅读确认:");
        }
    }

    @Test
    @DisplayName("有模板坐标时把签名和企业章写入指定页且不追加页")
    void shouldEmbedImagesAtValidatedTemplateCoordinates() throws Exception
    {
        OaSignedPdfService service = service();
        Path reviewPdf = sampleReviewPdf();
        OaSignPackage signPackage = signPackage(401L);
        OaSignPackageDocument document = signDocument(41L, 401L, reviewPdf);
        OaSignedPdfService.PdfImagePlacement signaturePlacement =
                new OaSignedPdfService.PdfImagePlacement(1, 72, 80, 180, 70);
        OaSignedPdfService.PdfImagePlacement sealPlacement =
                new OaSignedPdfService.PdfImagePlacement(1, 330, 60, 100, 100);

        SignedPdfResult result = service.generateSignedPdf(9L, signPackage, document, reviewPdf,
                signaturePng(), sealPng(), Date.from(Instant.parse("2026-07-11T08:30:00Z")),
                "本人确认签署本签约包", signaturePlacement, sealPlacement);

        try (PDDocument source = Loader.loadPDF(reviewPdf.toFile());
                PDDocument signed = Loader.loadPDF(storage().resolveAuthorizedFile(
                        result.getArchiveRelativePath()).toFile()))
        {
            assertThat(signed.getNumberOfPages()).isEqualTo(source.getNumberOfPages());
        }
    }

    @Test
    @DisplayName("最后一页定位会把企业章绘制到渲染后文档的实际尾页")
    void shouldDrawSealOnActualLastPageWithoutSentinelPageNumber() throws Exception
    {
        OaSignedPdfService service = service();
        Path reviewPdf = twoPageReviewPdf();
        OaSignPackage signPackage = signPackage(411L);
        OaSignPackageDocument document = signDocument(51L, 411L, reviewPdf);
        OaSignedPdfService.PdfImagePlacement sealPlacement =
                OaSignedPdfService.PdfImagePlacement.lastPage(330, 60, 100, 100);

        assertThat(sealPlacement.isLastPage()).isTrue();
        assertThat(sealPlacement.getPageNumber()).isNull();
        SignedPdfResult result = service.generateSignedPdf(null, signPackage, document, reviewPdf,
                null, sealPng(), Date.from(Instant.parse("2026-07-11T08:30:00Z")),
                "公司完成最终文件生成", null, sealPlacement);

        try (PDDocument source = Loader.loadPDF(reviewPdf.toFile());
                PDDocument signed = Loader.loadPDF(storage().resolveAuthorizedFile(
                        result.getArchiveRelativePath()).toFile()))
        {
            assertThat(signed.getNumberOfPages()).isEqualTo(2);
            assertThat(imageCount(signed.getPage(0))).isEqualTo(imageCount(source.getPage(0)));
            assertThat(imageCount(signed.getPage(1)))
                    .isEqualTo(imageCount(source.getPage(1)) + 1);
            assertThat(imagePlacementMatrices(signed.getPage(1))).singleElement()
                    .satisfies(matrix -> assertThat(matrix)
                            .containsExactly(100F, 0F, 0F, 100F, 330F, 60F));
        }
    }

    @Test
    @DisplayName("多页文档中追加签名页与最后一页印章可独立混合")
    void shouldMixAppendedSignatureAndLastPageSealOnMultiPageDocument() throws Exception
    {
        OaSignedPdfService service = service();
        Path reviewPdf = twoPageReviewPdf();
        OaSignPackage signPackage = signPackage(412L);
        OaSignPackageDocument document = signDocument(52L, 412L, reviewPdf);
        OaSignedPdfService.PdfImagePlacement sealPlacement =
                OaSignedPdfService.PdfImagePlacement.lastPage(330, 60, 100, 100);

        SignedPdfResult result = service.generateSignedPdf(null, signPackage, document, reviewPdf,
                signaturePng(), sealPng(), Date.from(Instant.parse("2026-07-11T08:30:00Z")),
                "本人确认签署本签约包", null, sealPlacement);

        try (PDDocument source = Loader.loadPDF(reviewPdf.toFile());
                PDDocument signed = Loader.loadPDF(storage().resolveAuthorizedFile(
                        result.getArchiveRelativePath()).toFile()))
        {
            assertThat(signed.getNumberOfPages()).isEqualTo(source.getNumberOfPages() + 1);
            PDPage contractLastPage = signed.getPage(source.getNumberOfPages() - 1);
            assertThat(imageCount(contractLastPage))
                    .isEqualTo(imageCount(source.getPage(source.getNumberOfPages() - 1)) + 1);
            assertThat(imagePlacementMatrices(contractLastPage)).singleElement()
                    .satisfies(matrix -> assertThat(matrix)
                            .containsExactly(100F, 0F, 0F, 100F, 330F, 60F));

            PDPage evidencePage = signed.getPage(signed.getNumberOfPages() - 1);
            assertThat(imageCount(evidencePage)).isEqualTo(1);
            String evidenceText = normalizedText(signed);
            assertThat(evidenceText).contains("员工手写签名: 已留存");
            assertThat(evidenceText).doesNotContain("公司印章:");
        }
    }

    @Test
    @DisplayName("仅印章可定位绘制且不追加证据页")
    void shouldPlaceOnlySealWithoutAppendingEvidencePage() throws Exception
    {
        OaSignedPdfService service = service();
        Path reviewPdf = sampleReviewPdf();
        OaSignPackage signPackage = signPackage(406L);
        OaSignPackageDocument document = signDocument(46L, 406L, reviewPdf);
        OaSignedPdfService.PdfImagePlacement sealPlacement =
                new OaSignedPdfService.PdfImagePlacement(1, 330, 60, 100, 100);

        SignedPdfResult result = service.generateSignedPdf(null, signPackage, document, reviewPdf,
                null, sealPng(), Date.from(Instant.parse("2026-07-11T08:30:00Z")),
                "公司完成最终文件生成", null, sealPlacement);

        try (PDDocument source = Loader.loadPDF(reviewPdf.toFile());
                PDDocument signed = Loader.loadPDF(storage().resolveAuthorizedFile(
                        result.getArchiveRelativePath()).toFile()))
        {
            assertThat(signed.getNumberOfPages()).isEqualTo(source.getNumberOfPages());
            assertThat(imageCount(signed.getPage(0))).isEqualTo(imageCount(source.getPage(0)) + 1);
        }
        assertThat(result.getSignatureHash()).isNull();
        assertThat(result.getCompanySealHash()).isNotBlank();
    }

    @Test
    @DisplayName("JPEG企业章可定位绘制并以JPG扩展名归档")
    void shouldAcceptJpegCompanySealAndEmbedIt() throws Exception
    {
        OaSignedPdfService service = service();
        Path reviewPdf = sampleReviewPdf();
        OaSignPackage signPackage = signPackage(416L);
        OaSignPackageDocument document = signDocument(56L, 416L, reviewPdf);
        byte[] seal = sealJpeg();
        OaSignedPdfService.PdfImagePlacement sealPlacement =
                new OaSignedPdfService.PdfImagePlacement(1, 330, 60, 100, 100);

        SignedPdfResult result = service.generateSignedPdf(null, signPackage, document, reviewPdf,
                null, seal, Date.from(Instant.parse("2026-07-11T08:30:00Z")),
                "公司完成最终文件生成", null, sealPlacement);

        assertThat(result.getCompanySealHash()).isEqualTo(sha256(seal));
        assertThat(OaSignImageValidator.companySealArchiveFilename(5L, seal))
                .isEqualTo("company-seal-5.jpg");
        try (PDDocument source = Loader.loadPDF(reviewPdf.toFile());
                PDDocument signed = Loader.loadPDF(storage().resolveAuthorizedFile(
                        result.getArchiveRelativePath()).toFile()))
        {
            assertThat(signed.getNumberOfPages()).isEqualTo(source.getNumberOfPages());
            assertThat(imageCount(signed.getPage(0)))
                    .isEqualTo(imageCount(source.getPage(0)) + 1);
        }
    }

    @Test
    @DisplayName("签名追加与印章定位可独立混合")
    void shouldMixAppendedSignatureAndPlacedSeal() throws Exception
    {
        OaSignedPdfService service = service();
        Path reviewPdf = sampleReviewPdf();
        OaSignPackage signPackage = signPackage(407L);
        OaSignPackageDocument document = signDocument(47L, 407L, reviewPdf);
        OaSignedPdfService.PdfImagePlacement sealPlacement =
                new OaSignedPdfService.PdfImagePlacement(1, 330, 60, 100, 100);

        SignedPdfResult result = service.generateSignedPdf(null, signPackage, document, reviewPdf,
                signaturePng(), sealPng(), Date.from(Instant.parse("2026-07-11T08:30:00Z")),
                "本人确认签署本签约包", null, sealPlacement);

        try (PDDocument source = Loader.loadPDF(reviewPdf.toFile());
                PDDocument signed = Loader.loadPDF(storage().resolveAuthorizedFile(
                        result.getArchiveRelativePath()).toFile()))
        {
            assertThat(signed.getNumberOfPages()).isEqualTo(source.getNumberOfPages() + 1);
            assertThat(imageCount(signed.getPage(0))).isEqualTo(imageCount(source.getPage(0)) + 1);
            PDPage evidencePage = signed.getPage(signed.getNumberOfPages() - 1);
            assertThat(imageCount(evidencePage)).isEqualTo(1);
            String evidenceText = normalizedText(signed);
            assertThat(evidenceText).contains("员工手写签名: 已留存");
            assertThat(evidenceText).doesNotContain("公司印章:");
        }
    }

    @Test
    @DisplayName("签名定位与印章追加可独立混合")
    void shouldMixPlacedSignatureAndAppendedSeal() throws Exception
    {
        OaSignedPdfService service = service();
        Path reviewPdf = sampleReviewPdf();
        OaSignPackage signPackage = signPackage(408L);
        OaSignPackageDocument document = signDocument(48L, 408L, reviewPdf);
        OaSignedPdfService.PdfImagePlacement signaturePlacement =
                new OaSignedPdfService.PdfImagePlacement(1, 72, 80, 180, 70);

        SignedPdfResult result = service.generateSignedPdf(null, signPackage, document, reviewPdf,
                signaturePng(), sealPng(), Date.from(Instant.parse("2026-07-11T08:30:00Z")),
                "公司完成最终文件生成", signaturePlacement, null);

        try (PDDocument source = Loader.loadPDF(reviewPdf.toFile());
                PDDocument signed = Loader.loadPDF(storage().resolveAuthorizedFile(
                        result.getArchiveRelativePath()).toFile()))
        {
            assertThat(signed.getNumberOfPages()).isEqualTo(source.getNumberOfPages() + 1);
            assertThat(imageCount(signed.getPage(0))).isEqualTo(imageCount(source.getPage(0)) + 1);
            PDPage evidencePage = signed.getPage(signed.getNumberOfPages() - 1);
            assertThat(imageCount(evidencePage)).isEqualTo(1);
            String evidenceText = normalizedText(signed);
            assertThat(evidenceText).contains("公司印章: 已加盖");
            assertThat(evidenceText).doesNotContain("员工手写签名:", "员工阅读确认:");
        }
    }

    @Test
    @DisplayName("两阶段证据时间缺失、脱离图片或先后倒置时失败关闭")
    void shouldRejectInvalidTwoStageEvidenceTimes() throws Exception
    {
        OaSignedPdfService service = service();
        Path reviewPdf = sampleReviewPdf();
        OaSignPackage signPackage = signPackage(414L);
        OaSignPackageDocument document = signDocument(54L, 414L, reviewPdf);
        Date employeeTime = Date.from(Instant.parse("2026-07-11T08:30:00Z"));
        Date companyTime = Date.from(Instant.parse("2026-07-11T12:45:00Z"));

        assertThatThrownBy(() -> service.generateSignedPdf(null, signPackage, document, reviewPdf,
                signaturePng(), null, null, null, "员工确认", null, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("员工签名时间不能为空");
        assertThatThrownBy(() -> service.generateSignedPdf(null, signPackage, document, reviewPdf,
                null, sealPng(), null, null, "公司确认", null, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("公司盖章时间不能为空");
        assertThatThrownBy(() -> service.generateSignedPdf(null, signPackage, document, reviewPdf,
                null, sealPng(), employeeTime, companyTime, "公司确认", null, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("员工签名时间不能脱离签名证据");
        assertThatThrownBy(() -> service.generateSignedPdf(null, signPackage, document, reviewPdf,
                signaturePng(), null, employeeTime, companyTime, "员工确认", null, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("公司盖章时间不能脱离印章证据");
        assertThatThrownBy(() -> service.generateSignedPdf(null, signPackage, document, reviewPdf,
                signaturePng(), sealPng(), companyTime, employeeTime,
                "最终确认", null, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("公司盖章时间不能早于员工签名时间");
        assertThat(archiveFiles()).isEmpty();
    }

    @Test
    @DisplayName("无签名印章的只读文件可独立归档")
    void shouldArchiveFinalPdfWithoutMarks() throws Exception
    {
        OaSignedPdfService service = service();
        Path reviewPdf = sampleReviewPdf();
        OaSignPackage signPackage = signPackage(409L);
        OaSignPackageDocument document = signDocument(49L, 409L, reviewPdf);

        SignedPdfResult result = service.generateFinalPdfWithoutMarks(
                null, signPackage, document, reviewPdf);

        Path archived = storage().resolveAuthorizedFile(result.getArchiveRelativePath());
        assertThat(archived).isNotEqualTo(reviewPdf).isNotEmptyFile();
        assertUsesPortableCrossReferenceFormat(archived);
        assertThat(result.getSignatureArchiveRelativePath()).isNull();
        assertThat(result.getSignatureFileUrl()).isNull();
        assertThat(result.getSignatureHash()).isNull();
        assertThat(result.getSignatureFileSize()).isZero();
        assertThat(result.getCompanySealHash()).isNull();
        try (PDDocument source = Loader.loadPDF(reviewPdf.toFile());
                PDDocument archivedPdf = Loader.loadPDF(archived.toFile()))
        {
            assertThat(archivedPdf.getNumberOfPages()).isEqualTo(source.getNumberOfPages());
            assertThat(normalizedText(archivedPdf)).isEqualTo(normalizedText(source));
            assertThat(result.getPageCount()).isEqualTo(source.getNumberOfPages());
        }
    }

    @Test
    @DisplayName("先选公司归档保持冻结正文且最终确认页包含员工手写签名证据")
    void shouldArchiveCompanyFirstSignatureOnEvidencePageWithoutChangingFrozenBody()
            throws Exception
    {
        OaSignedPdfService service = service();
        Path reviewPdf = sampleReviewPdf();
        OaSignPackage signPackage = signPackage(415L);
        signPackage.setFinalDocumentVersion("SP-415-V2");
        OaSignPackageDocument document = signDocument(55L, 415L, reviewPdf);
        byte[] signature = signaturePng();
        byte[] seal = sealJpeg();
        Date companySealTime = Date.from(Instant.parse("2026-07-11T08:00:00Z"));
        Date employeeSignedTime = Date.from(Instant.parse("2026-07-11T09:00:00Z"));
        Date finalConfirmedTime = Date.from(Instant.parse("2026-07-11T09:01:00Z"));
        OaSignedPdfService.PdfImagePlacement sealPlacement =
                new OaSignedPdfService.PdfImagePlacement(1, 330, 60, 100, 100);

        SignedPdfResult pending = service.generatePendingFinalPdf(null, signPackage, document,
                reviewPdf, null, seal, null, companySealTime,
                "公司已完成先行盖章", null, sealPlacement);
        Path pendingPath = storage().resolveAuthorizedFile(pending.getArchiveRelativePath());
        String finalRootHash = sha256((document.getDocumentId() + ":"
                + pending.getContentPdfHash() + "\n").getBytes(StandardCharsets.UTF_8));
        // A placement snapshot must not cause archive to alter the already-opened body.
        OaSignedPdfService.PdfImagePlacement historicalSignaturePlacement =
                new OaSignedPdfService.PdfImagePlacement(1, 72, 80, 180, 70);

        SignedPdfResult archive = service.archiveConfirmedFinalPdf(null, signPackage, document,
                pendingPath, pending.getSignedPdfHash(), pending.getContentPdfHash(),
                finalRootHash, signature, seal, employeeSignedTime, companySealTime,
                finalConfirmedTime, historicalSignaturePlacement, sealPlacement);
        Path archivePath = storage().resolveAuthorizedFile(archive.getArchiveRelativePath());

        assertThat(archive.getContentPdfHash()).isEqualTo(pending.getContentPdfHash());
        assertThat(archive.getSignedPdfHash()).isEqualTo(sha256(archivePath));
        try (PDDocument pendingPdf = Loader.loadPDF(pendingPath.toFile());
                PDDocument archivedPdf = Loader.loadPDF(archivePath.toFile()))
        {
            assertThat(archivedPdf.getNumberOfPages()).isEqualTo(pendingPdf.getNumberOfPages());
            assertThat(archivedPdf.getDocumentInformation().getCustomMetadataValue(
                    "ERP-Final-Content-Hash")).isEqualTo(pending.getContentPdfHash());
            assertThat(archivedPdf.getDocumentInformation().getCustomMetadataValue(
                    "ERP-Final-Root-Hash")).isEqualTo(finalRootHash);
            assertThat(archivedPdf.getDocumentInformation().getCustomMetadataValue(
                    "ERP-Employee-Signature-Hash")).isEqualTo(sha256(signature));
            assertThat(archivedPdf.getDocumentInformation().getCustomMetadataValue(
                    "ERP-Company-Seal-Hash")).isEqualTo(sha256(seal));
            assertThat(imagePlacementMatrices(archivedPdf.getPage(0)))
                    .containsExactlyElementsOf(imagePlacementMatrices(pendingPdf.getPage(0)));
            assertThat(imageCount(archivedPdf.getPage(0)))
                    .isEqualTo(imageCount(pendingPdf.getPage(0)));

            PDPage evidencePage = archivedPdf.getPage(archivedPdf.getNumberOfPages() - 1);
            assertThat(imageCount(evidencePage)).isEqualTo(2);
            String text = normalizedText(archivedPdf);
            assertThat(text).contains(
                    "最终合同电子签署确认页",
                    "员工手写签名: 已留存于本确认页",
                    "公司印章证据: 已留存于本确认页",
                    hashLine("员工签名 SHA-256", sha256(signature), 1),
                    hashLine("员工签名 SHA-256", sha256(signature), 2),
                    hashLine("企业印章 SHA-256", sha256(seal), 1),
                    hashLine("企业印章 SHA-256", sha256(seal), 2),
                    hashLine("最终合同集合 SHA-256", finalRootHash, 1),
                    hashLine("最终合同集合 SHA-256", finalRootHash, 2),
                    hashLine("稳定正文 SHA-256", pending.getContentPdfHash(), 1),
                    hashLine("稳定正文 SHA-256", pending.getContentPdfHash(), 2));
        }
    }

    @Test
    @DisplayName("签名优先归档替换确认页时不重复统计已移除页面的签名资源")
    void shouldReplaceSignatureFirstEvidencePageWithoutCountingRemovedSignatureResource()
            throws Exception
    {
        OaSignedPdfService service = service();
        Path reviewPdf = reviewPdfWithInheritedImageResource();
        OaSignPackage signPackage = signPackage(419L);
        signPackage.setFinalDocumentVersion("SP-419-V2");
        OaSignPackageDocument document = signDocument(59L, 419L, reviewPdf);
        document.setEmployeeSignRequired("Y");
        document.setCompanySealRequired("N");
        byte[] signature = signaturePng();
        Date employeeSignedTime = Date.from(Instant.parse("2026-07-11T09:00:00Z"));
        Date finalConfirmedTime = Date.from(Instant.parse("2026-07-11T09:01:00Z"));

        SignedPdfResult pending = service.generatePendingFinalPdf(null, signPackage, document,
                reviewPdf, signature, null, employeeSignedTime, null,
                "首次签名已冻结", null, null);
        Path pendingPath = storage().resolveAuthorizedFile(pending.getArchiveRelativePath());
        String finalRootHash = sha256((document.getDocumentId() + ":"
                + pending.getContentPdfHash() + "\n").getBytes(StandardCharsets.UTF_8));

        SignedPdfResult archive = service.archiveConfirmedFinalPdf(null, signPackage, document,
                pendingPath, pending.getSignedPdfHash(), pending.getContentPdfHash(),
                finalRootHash, signature, null, employeeSignedTime, null,
                finalConfirmedTime, null, null);
        Path archivePath = storage().resolveAuthorizedFile(archive.getArchiveRelativePath());

        try (PDDocument pendingPdf = Loader.loadPDF(pendingPath.toFile());
                PDDocument archivedPdf = Loader.loadPDF(archivePath.toFile()))
        {
            PDPage pendingEvidence = pendingPdf.getPage(pendingPdf.getNumberOfPages() - 1);
            PDPage archivedEvidence = archivedPdf.getPage(archivedPdf.getNumberOfPages() - 1);
            assertThat(imagePlacementMatrices(pendingEvidence)).hasSize(1);
            assertThat(imagePlacementMatrices(archivedEvidence)).hasSize(1);
            assertThat(imageCount(archivedEvidence)).isEqualTo(1);
            assertThat(archivedEvidence.getCOSObject().containsKey(COSName.RESOURCES)).isTrue();
        }
    }

    @Test
    @DisplayName("签名优先归档只校验确认页实际绘制的签名和透明印章")
    void shouldArchiveDrawnSignatureAndSealDespiteInheritedImageResources()
            throws Exception
    {
        OaSignedPdfService service = service();
        Path reviewPdf = reviewPdfWithInheritedImageResource();
        OaSignPackage signPackage = signPackage(420L);
        signPackage.setFinalDocumentVersion("SP-420-V2");
        OaSignPackageDocument document = signDocument(60L, 420L, reviewPdf);
        document.setEmployeeSignRequired("Y");
        document.setCompanySealRequired("Y");
        byte[] signature = signaturePng();
        byte[] seal = sealPng();
        Date employeeSignedTime = Date.from(Instant.parse("2026-07-11T09:00:00Z"));
        Date companySealTime = Date.from(Instant.parse("2026-07-11T10:00:00Z"));
        Date finalConfirmedTime = Date.from(Instant.parse("2026-07-11T10:01:00Z"));

        SignedPdfResult pending = service.generatePendingFinalPdf(null, signPackage, document,
                reviewPdf, signature, seal, employeeSignedTime, companySealTime,
                "首次签名已冻结", null, null);
        Path pendingPath = storage().resolveAuthorizedFile(pending.getArchiveRelativePath());
        String finalRootHash = sha256((document.getDocumentId() + ":"
                + pending.getContentPdfHash() + "\n").getBytes(StandardCharsets.UTF_8));

        SignedPdfResult archive = service.archiveConfirmedFinalPdf(null, signPackage, document,
                pendingPath, pending.getSignedPdfHash(), pending.getContentPdfHash(),
                finalRootHash, signature, seal, employeeSignedTime, companySealTime,
                finalConfirmedTime, null, null);
        Path archivePath = storage().resolveAuthorizedFile(archive.getArchiveRelativePath());

        try (PDDocument pendingPdf = Loader.loadPDF(pendingPath.toFile());
                PDDocument archivedPdf = Loader.loadPDF(archivePath.toFile()))
        {
            PDPage pendingEvidence = pendingPdf.getPage(pendingPdf.getNumberOfPages() - 1);
            PDPage archivedEvidence = archivedPdf.getPage(archivedPdf.getNumberOfPages() - 1);
            assertThat(imagePlacementMatrices(pendingEvidence)).hasSize(2);
            assertThat(imagePlacementMatrices(archivedEvidence)).hasSize(2);
            assertThat(imageCount(archivedEvidence)).isEqualTo(2);
            assertThat(archivedEvidence.getCOSObject().containsKey(COSName.RESOURCES)).isTrue();
        }
    }

    @Test
    @DisplayName("确认页图像校验不把本页未绘制的图片资源算作证据")
    void shouldCountOnlyImagesDrawnByEvidencePageContent() throws Exception
    {
        OaSignedPdfService service = service();
        try (PDDocument document = new PDDocument())
        {
            PDPage page = new PDPage(PDRectangle.A4);
            page.setResources(new PDResources());
            document.addPage(page);
            page.getResources().add(PDImageXObject.createFromByteArray(document,
                    signaturePng(), "unused-signature-resource.png"));
            PDImageXObject drawnSeal = PDImageXObject.createFromByteArray(document,
                    sealPng(), "drawn-seal.png");
            try (PDPageContentStream content = new PDPageContentStream(document, page))
            {
                content.drawImage(drawnSeal, 60, 82, 105, 105);
            }

            Integer drawnImages = ReflectionTestUtils.invokeMethod(service,
                    "countDrawnImages", page);
            assertThat(drawnImages).isEqualTo(1);
            assertThat(imageCount(page)).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("历史归档缺少正文签章时生成一次性展示副本并保持原归档不变")
    void shouldRestoreMissingPlacedMarksIntoDisposableExport() throws Exception
    {
        OaSignedPdfService service = service();
        Path reviewPdf = sampleReviewPdf();
        OaSignPackage signPackage = signPackage(421L);
        signPackage.setFinalDocumentVersion("SP-421-V2");
        OaSignPackageDocument document = signDocument(61L, 421L, reviewPdf);
        document.setEmployeeSignRequired("Y");
        document.setCompanySealRequired("Y");
        byte[] signature = signaturePng();
        byte[] seal = sealPng();
        Date employeeTime = Date.from(Instant.parse("2026-07-11T09:00:00Z"));
        Date companyTime = Date.from(Instant.parse("2026-07-11T10:00:00Z"));
        Date confirmationTime = Date.from(Instant.parse("2026-07-11T10:01:00Z"));
        OaSignedPdfService.PdfImagePlacement signaturePlacement =
                new OaSignedPdfService.PdfImagePlacement(1, 72, 80, 180, 70);
        OaSignedPdfService.PdfImagePlacement sealPlacement =
                new OaSignedPdfService.PdfImagePlacement(1, 330, 60, 100, 100);

        SignedPdfResult pending = service.generatePendingFinalPdf(null, signPackage, document,
                reviewPdf, signature, seal, employeeTime, companyTime,
                "历史归档展示", null, null);
        Path pendingPath = storage().resolveAuthorizedFile(pending.getArchiveRelativePath());
        String finalRootHash = sha256((document.getDocumentId() + ":"
                + pending.getContentPdfHash() + "\n").getBytes(StandardCharsets.UTF_8));
        SignedPdfResult archive = service.archiveConfirmedFinalPdf(null, signPackage, document,
                pendingPath, pending.getSignedPdfHash(), pending.getContentPdfHash(),
                finalRootHash, signature, seal, employeeTime, companyTime,
                confirmationTime, signaturePlacement, sealPlacement);
        Path archivePath = storage().resolveAuthorizedFile(archive.getArchiveRelativePath());
        String originalHash = sha256(archivePath);

        OaSignedPdfService.FinalExportPreparation preparation = service.prepareFinalExport(
                archivePath, originalHash, pending.getContentPdfHash(), finalRootHash,
                sha256(signature), sha256(seal), signaturePlacement, sealPlacement,
                signature, seal);

        assertThat(preparation.derived()).isTrue();
        assertThat(preparation.path()).isNotEqualTo(archivePath);
        assertThat(sha256(archivePath)).isEqualTo(originalHash);
        Path qaPdf = Path.of("/tmp/erp-p005-final-export-qa.pdf");
        Files.copy(preparation.path(), qaPdf, StandardCopyOption.REPLACE_EXISTING);
        assertThat(qaPdf).isRegularFile();
        System.out.println("P0-05 QA PDF: " + qaPdf.toAbsolutePath());
        try (PDDocument exported = Loader.loadPDF(preparation.path().toFile()))
        {
            assertThat(imagePlacementMatrices(exported.getPage(0)))
                    .contains(new float[] {180F, 0F, 0F, 70F, 72F, 80F},
                            new float[] {100F, 0F, 0F, 100F, 330F, 60F});
            assertThat(exported.getDocumentInformation().getCustomMetadataValue(
                    "ERP-Final-Export-Type")).isEqualTo("SIGNED_PLACEMENT_DISPLAY_DERIVATIVE");
            assertThat(exported.getNumberOfPages()).isEqualTo(1);
            assertThat(normalizedText(exported))
                    .doesNotContain("最终合同电子签署确认页", "最终合同集合 SHA-256");
            assertThat(imageCount(exported.getPage(0)))
                    .isEqualTo(2);
        }
        Files.deleteIfExists(preparation.path());
    }

    @Test
    @DisplayName("正文已有签名仅缺印章时只恢复印章且不重复绘制签名")
    void shouldRestoreOnlyMissingSealWithoutRedrawingSignature() throws Exception
    {
        assertPartialPlacementExportDoesNotDuplicate(true);
    }

    @Test
    @DisplayName("正文已有印章仅缺签名时只恢复签名且不重复绘制印章")
    void shouldRestoreOnlyMissingSignatureWithoutRedrawingSeal() throws Exception
    {
        assertPartialPlacementExportDoesNotDuplicate(false);
    }

    @Test
    @DisplayName("仅唯一完整最终确认页可被展示导出移除")
    void shouldRejectOrdinaryPendingOrDuplicatedConfirmationLastPage() throws Exception
    {
        OaSignedPdfService service = service();
        Path reviewPdf = sampleReviewPdf();
        OaSignPackage signPackage = signPackage(429L);
        signPackage.setFinalDocumentVersion("SP-429-V2");
        OaSignPackageDocument document = signDocument(69L, 429L, reviewPdf);
        document.setEmployeeSignRequired("Y");
        document.setCompanySealRequired("Y");
        byte[] signature = signaturePng();
        byte[] seal = sealPng();
        Date employeeTime = Date.from(Instant.parse("2026-07-11T09:00:00Z"));
        Date companyTime = Date.from(Instant.parse("2026-07-11T10:00:00Z"));
        Date confirmationTime = Date.from(Instant.parse("2026-07-11T10:01:00Z"));
        OaSignedPdfService.PdfImagePlacement signaturePlacement =
                new OaSignedPdfService.PdfImagePlacement(1, 72, 80, 180, 70);
        OaSignedPdfService.PdfImagePlacement sealPlacement =
                new OaSignedPdfService.PdfImagePlacement(1, 330, 60, 100, 100);

        SignedPdfResult pending = service.generatePendingFinalPdf(null, signPackage, document,
                reviewPdf, signature, seal, employeeTime, companyTime,
                "确认页文本边界", null, null);
        Path pendingPath = storage().resolveAuthorizedFile(pending.getArchiveRelativePath());
        String finalRootHash = sha256((document.getDocumentId() + ":"
                + pending.getContentPdfHash() + "\n").getBytes(StandardCharsets.UTF_8));
        SignedPdfResult archive = service.archiveConfirmedFinalPdf(null, signPackage, document,
                pendingPath, pending.getSignedPdfHash(), pending.getContentPdfHash(),
                finalRootHash, signature, seal, employeeTime, companyTime,
                confirmationTime, null, null);
        Path archivePath = storage().resolveAuthorizedFile(archive.getArchiveRelativePath());

        Path ordinaryLastPage = tempDir.resolve("ordinary-last-page-with-evidence-images.pdf");
        try (PDDocument malformed = Loader.loadPDF(archivePath.toFile()))
        {
            malformed.removePage(malformed.getNumberOfPages() - 1);
            PDPage ordinary = new PDPage(PDRectangle.A4);
            ordinary.setResources(new PDResources());
            malformed.addPage(ordinary);
            PDImageXObject signatureImage = PDImageXObject.createFromByteArray(malformed,
                    signature, "ordinary-signature.png");
            PDImageXObject sealImage = PDImageXObject.createFromByteArray(malformed,
                    seal, "ordinary-seal.png");
            try (PDPageContentStream content = new PDPageContentStream(malformed, ordinary))
            {
                content.drawImage(signatureImage, 60, 95, 230, 86);
                content.drawImage(sealImage, 360, 82, 105, 105);
            }
            malformed.save(ordinaryLastPage.toFile());
        }

        Path pendingMasquerade = tempDir.resolve("pending-page-masquerading-as-final.pdf");
        try (PDDocument malformed = Loader.loadPDF(pendingPath.toFile()))
        {
            malformed.getDocumentInformation().setCustomMetadataValue(
                    "ERP-Final-Root-Hash", finalRootHash);
            malformed.getDocumentInformation().setCustomMetadataValue(
                    "ERP-Employee-Signature-Hash", sha256(signature));
            malformed.getDocumentInformation().setCustomMetadataValue(
                    "ERP-Company-Seal-Hash", sha256(seal));
            malformed.save(pendingMasquerade.toFile());
        }

        Path duplicatedConfirmation = tempDir.resolve("body-contains-confirmation-title.pdf");
        try (PDDocument malformed = Loader.loadPDF(archivePath.toFile()))
        {
            malformed.importPage(malformed.getPage(malformed.getNumberOfPages() - 1));
            malformed.save(duplicatedConfirmation.toFile());
        }

        Set<Path> before = finalExportTempFiles();
        assertThatThrownBy(() -> service.prepareFinalExport(ordinaryLastPage,
                sha256(ordinaryLastPage), pending.getContentPdfHash(), finalRootHash,
                sha256(signature), sha256(seal), signaturePlacement, sealPlacement,
                signature, seal, List.of(), 1))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("最后一页不是唯一完整的最终确认页");
        assertThat(finalExportTempFiles()).containsExactlyInAnyOrderElementsOf(before);

        assertThatThrownBy(() -> service.prepareFinalExport(pendingMasquerade,
                sha256(pendingMasquerade), pending.getContentPdfHash(), finalRootHash,
                sha256(signature), sha256(seal), signaturePlacement, sealPlacement,
                signature, seal, List.of(), 1))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("最后一页不是唯一完整的最终确认页");
        assertThat(finalExportTempFiles()).containsExactlyInAnyOrderElementsOf(before);

        assertThatThrownBy(() -> service.prepareFinalExport(duplicatedConfirmation,
                sha256(duplicatedConfirmation), pending.getContentPdfHash(), finalRootHash,
                sha256(signature), sha256(seal), signaturePlacement, sealPlacement,
                signature, seal, List.of(), 2))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("正文不得包含最终确认页标题");
        assertThat(finalExportTempFiles()).containsExactlyInAnyOrderElementsOf(before);
        assertThat(sha256(archivePath)).isEqualTo(archive.getSignedPdfHash());
    }

    @Test
    @DisplayName("真实历史APPENDED归档按兼容清单生成18页签章展示版且原件不可变")
    void shouldRestoreRealHistoricalAppendedArchiveAtExactProtectedPositions()
            throws Exception
    {
        Path sampleRoot = sampleAuditRoot();
        Path archivePath = sampleRoot.resolve("archive.pdf").toAbsolutePath();
        Path reviewPath = sampleRoot.resolve("review.pdf").toAbsolutePath();
        byte[] signature = Files.readAllBytes(sampleRoot.resolve("signature.png"));
        byte[] seal = Files.readAllBytes(sampleRoot.resolve("seal.jpg"));
        assertThat(sha256(archivePath)).isEqualTo(SAMPLE_ARCHIVE_HASH);
        assertThat(sha256(reviewPath)).isEqualTo(SAMPLE_REVIEW_HASH);
        assertThat(sha256(signature)).isEqualTo(SAMPLE_SIGNATURE_HASH);
        assertThat(sha256(seal)).isEqualTo(SAMPLE_SEAL_HASH);

        OaSignPackageDocument snapshot = new OaSignPackageDocument();
        snapshot.setDocumentId(1785202186549261523L);
        snapshot.setDocumentPolicyMode(OaSignPlacementPolicyService.SNAPSHOT_V1);
        snapshot.setTemplateType("ONBOARD_LABOR_CONTRACT");
        snapshot.setTemplateVersionSnapshot("20260721-v7");
        snapshot.setReviewPdfHash(SAMPLE_REVIEW_HASH);
        snapshot.setFinalContentHash(SAMPLE_CONTENT_HASH);
        snapshot.setEmployeeSignRequired("Y");
        snapshot.setCompanySealRequired("Y");
        snapshot.setSignaturePositionJson(
                OaSignPlacementPolicyService.APPENDED_CONFIRMATION_PAGE);
        snapshot.setCompanySealPositionJson(
                OaSignPlacementPolicyService.APPENDED_CONFIRMATION_PAGE);
        OaSignPlacementPolicyService policy = new OaSignPlacementPolicyService();
        OaSignPackage historicalPackage = historicalGoldenPackage();
        OaSignPlacementPolicyService.HistoricalExportPolicy historicalPolicy =
                policy.resolveHistoricalExportPolicy(snapshot, historicalPackage,
                        "1226728ec0e703d513efda83dc5578ee4cdd65cb3e7d0e7cb5afee813f466558",
                        reviewPath, archivePath, SAMPLE_ARCHIVE_HASH);
        OaSignedPdfService.PdfImagePlacement signaturePlacement =
                policy.resolveFinalExportSignaturePlacement(snapshot, historicalPolicy);
        OaSignedPdfService.PdfImagePlacement sealPlacement =
                policy.resolveFinalExportCompanySealPlacement(snapshot, historicalPolicy);
        List<OaSignedPdfService.PdfTextPlacement> textPlacements =
                policy.resolveFinalExportDisplayTextPlacements(snapshot, historicalPolicy);
        String historicalRepresentative = historicalRepresentativeValue();
        List<OaSignedPdfService.PdfTextOverlay> overlays = textPlacements.stream()
                .map(placement -> new OaSignedPdfService.PdfTextOverlay(placement.field(),
                        "companyLegalRepresentative".equals(placement.field())
                                ? "甲方代表：" + historicalRepresentative
                                : "archiveEvidenceNotice".equals(placement.field())
                                        ? "签署校验证据请查看原最终归档及签署凭证。"
                                        : "☑ 3《员工手册》",
                        placement))
                .toList();

        OaSignedPdfService service = service();
        OaSignedPdfService.FinalExportPreparation preparation = service.prepareFinalExport(
                archivePath, SAMPLE_ARCHIVE_HASH, SAMPLE_CONTENT_HASH,
                SAMPLE_FINAL_ROOT_HASH, SAMPLE_SIGNATURE_HASH, SAMPLE_SEAL_HASH,
                signaturePlacement, sealPlacement, signature, seal, overlays,
                policy.resolveFinalExportExpectedBodyPageCount(snapshot, historicalPolicy));

        assertThat(sha256(archivePath)).isEqualTo(SAMPLE_ARCHIVE_HASH);
        try (PDDocument exported = Loader.loadPDF(preparation.path().toFile()))
        {
            assertThat(exported.getNumberOfPages()).isEqualTo(18);
            String exportedText = normalizedText(exported);
            assertThat(exportedText)
                    .doesNotContain("最终合同电子签署确认页",
                            "签署时间及文件校验信息见本合同末页《电子签署确认页》",
                            "□ 3 《员工手册》", "□ 3《员工手册》")
                    .contains("☑ 3《员工手册》",
                            "签署校验证据请查看原最终归档及签署凭证。");
            assertThat(imagePlacementMatrices(exported.getPage(13)))
                    .contains(new float[] {90F, 0F, 0F, 42.3F, 319F, 699F},
                            new float[] {76F, 0F, 0F, 35.8F, 232F, 316F},
                            new float[] {62F, 0F, 0F, 61F, 180F, 688F});
            assertThat(imagePlacementMatrices(exported.getPage(15)))
                    .contains(new float[] {78F, 0F, 0F, 36.7F, 309F, 225F});
            assertThat(imagePlacementMatrices(exported.getPage(17)))
                    .contains(new float[] {45F, 0F, 0F, 16.9F, 447.4F, 221.3F});
            assertThat(textAt(exported, textPlacements.get(0)))
                    .contains("甲方代表:" + historicalRepresentative)
                    .doesNotContain("____________");
            assertThat(textAt(exported, textPlacements.get(1)))
                    .contains("☑3《员工手册》")
                    .doesNotContain("□");
            assertThat(textAt(exported, textPlacements.get(2)))
                    .contains("签署校验证据", "原最终归档");
            try (PDDocument sourceArchive = Loader.loadPDF(archivePath.toFile()))
            {
                assertProtectedTextHashUnchanged(sourceArchive, exported, 16, "日期:");
                assertProtectedTextHashUnchanged(sourceArchive, exported, 18,
                        "身份证号码:");
                assertProtectedTextHashUnchanged(sourceArchive, exported, 18, "日期:");
            }

            OaSignedPdfService.PdfTextOverlay wrongTarget =
                    new OaSignedPdfService.PdfTextOverlay("attachmentHandbookMark",
                            "☑ 3《员工手册》",
                            new OaSignedPdfService.PdfTextPlacement(
                                    "attachmentHandbookMark", 14, 400F, 500F,
                                    20F, 20F, 12F, List.of()));
            assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service,
                    "validateFinalExportDerivative", exported, SAMPLE_ARCHIVE_HASH,
                    SAMPLE_CONTENT_HASH, signaturePlacement, sealPlacement, signature, seal,
                    List.of(overlays.get(0), wrongTarget, overlays.get(2)), null))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("文本修复位置校验失败");
        }
        System.out.println("CONTROLLED_GOLDEN_AUDIT_EXECUTED: derivative verified; fixture excluded");
        Files.deleteIfExists(preparation.path());
    }

    @Test
    @DisplayName("真实劳动合同坐标与日期保护区相交或正文页数不符时失败关闭")
    void shouldFailClosedForRealProtectedRegionIntersectionOrPageCountMismatch()
            throws Exception
    {
        Path sampleRoot = sampleAuditRoot();
        Path archivePath = sampleRoot.resolve("archive.pdf").toAbsolutePath();
        byte[] signature = Files.readAllBytes(sampleRoot.resolve("signature.png"));
        byte[] seal = Files.readAllBytes(sampleRoot.resolve("seal.jpg"));
        List<OaSignedPdfService.PdfProtectedRegion> protectedRegions = List.of(
                new OaSignedPdfService.PdfProtectedRegion(16, 395F, 218F, 150F, 42F),
                new OaSignedPdfService.PdfProtectedRegion(18, 380F, 175F, 165F, 32F),
                new OaSignedPdfService.PdfProtectedRegion(18, 450F, 135F, 95F, 30F));
        OaSignedPdfService.PdfImagePlacement intersecting =
                new OaSignedPdfService.PdfImagePlacement(16, 400F, 225F, 78F, 36.7F,
                        protectedRegions);
        OaSignedPdfService service = service();

        assertThatThrownBy(() -> service.prepareFinalExport(archivePath,
                SAMPLE_ARCHIVE_HASH, SAMPLE_CONTENT_HASH, SAMPLE_FINAL_ROOT_HASH,
                SAMPLE_SIGNATURE_HASH, SAMPLE_SEAL_HASH, intersecting,
                new OaSignedPdfService.PdfImagePlacement(14, 180F, 688F, 62F, 61F,
                        protectedRegions), signature, seal, List.of(), 18))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("与受保护区域重叠");
        assertThatThrownBy(() -> service.prepareFinalExport(archivePath,
                SAMPLE_ARCHIVE_HASH, SAMPLE_CONTENT_HASH, SAMPLE_FINAL_ROOT_HASH,
                SAMPLE_SIGNATURE_HASH, SAMPLE_SEAL_HASH,
                new OaSignedPdfService.PdfImagePlacement(14, 319F, 699F, 90F, 42.3F),
                new OaSignedPdfService.PdfImagePlacement(14, 180F, 688F, 62F, 61F),
                signature, seal, List.of(), 17))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("正文页数与冻结签章策略不匹配");
        assertThat(sha256(archivePath)).isEqualTo(SAMPLE_ARCHIVE_HASH);
    }

    @Test
    @DisplayName("18页劳动合同按四个员工签名位和一个印章位导出并移除确认页")
    void shouldExportMultiPositionEighteenPageDisplayPdfWithoutChangingArchive()
            throws Exception
    {
        OaSignedPdfService service = service();
        Path reviewPdf = eighteenPageReviewPdf();
        OaSignPackage signPackage = signPackage(428L);
        signPackage.setFinalDocumentVersion("SP-428-V2");
        OaSignPackageDocument document = signDocument(68L, 428L, reviewPdf);
        document.setEmployeeSignRequired("Y");
        document.setCompanySealRequired("Y");
        byte[] signature = signaturePng();
        byte[] seal = sealPng();
        Date employeeTime = Date.from(Instant.parse("2026-07-11T09:00:00Z"));
        Date companyTime = Date.from(Instant.parse("2026-07-11T10:00:00Z"));
        Date confirmationTime = Date.from(Instant.parse("2026-07-11T10:01:00Z"));
        OaSignedPdfService.PdfImagePlacement signaturePlacement =
                OaSignedPdfService.PdfImagePlacement.composite(List.of(
                        new OaSignedPdfService.PdfImagePlacement(14, 319, 699, 90, 42.3F),
                        new OaSignedPdfService.PdfImagePlacement(14, 232, 316, 76, 35.8F),
                        new OaSignedPdfService.PdfImagePlacement(16, 309, 225, 78, 36.7F),
                        new OaSignedPdfService.PdfImagePlacement(18, 447.4F, 221.3F,
                                45F, 16.9F)));
        OaSignedPdfService.PdfImagePlacement sealPlacement =
                new OaSignedPdfService.PdfImagePlacement(14, 180, 688, 62, 61);

        SignedPdfResult pending = service.generatePendingFinalPdf(null, signPackage, document,
                reviewPdf, signature, seal, employeeTime, companyTime,
                "多位置劳动合同展示", signaturePlacement, sealPlacement);
        Path pendingPath = storage().resolveAuthorizedFile(pending.getArchiveRelativePath());
        String finalRootHash = sha256((document.getDocumentId() + ":"
                + pending.getContentPdfHash() + "\n").getBytes(StandardCharsets.UTF_8));
        SignedPdfResult archive = service.archiveConfirmedFinalPdf(null, signPackage, document,
                pendingPath, pending.getSignedPdfHash(), pending.getContentPdfHash(),
                finalRootHash, signature, seal, employeeTime, companyTime,
                confirmationTime, signaturePlacement, sealPlacement);
        Path archivePath = storage().resolveAuthorizedFile(archive.getArchiveRelativePath());
        String originalArchiveHash = sha256(archivePath);

        OaSignedPdfService.FinalExportPreparation preparation = service.prepareFinalExport(
                archivePath, originalArchiveHash, pending.getContentPdfHash(), finalRootHash,
                sha256(signature), sha256(seal), signaturePlacement, sealPlacement,
                signature, seal);
        assertThat(preparation.derived()).isTrue();
        assertThat(sha256(archivePath)).isEqualTo(originalArchiveHash);
        try (PDDocument exported = Loader.loadPDF(preparation.path().toFile()))
        {
            assertThat(exported.getNumberOfPages()).isEqualTo(18);
            assertThat(normalizedText(exported))
                    .doesNotContain("最终合同电子签署确认页");
            assertThat(imagePlacementMatrices(exported.getPage(13))).hasSize(3);
            assertThat(imagePlacementMatrices(exported.getPage(13)))
                    .contains(new float[] {90F, 0F, 0F, 42.3F, 319F, 699F},
                            new float[] {76F, 0F, 0F, 35.8F, 232F, 316F},
                            new float[] {62F, 0F, 0F, 61F, 180F, 688F});
            assertThat(imagePlacementMatrices(exported.getPage(15)))
                    .contains(new float[] {78F, 0F, 0F, 36.7F, 309F, 225F});
            assertThat(imagePlacementMatrices(exported.getPage(17)))
                    .contains(new float[] {45F, 0F, 0F, 16.9F, 447.4F, 221.3F});
        }
        Files.deleteIfExists(preparation.path());
    }

    @Test
    @DisplayName("exact-v7常规与数据库最大值均经正式渲染签章归档导出且重试并发稳定")
    void shouldRenderArchiveAndExportExactV7LayoutsWithoutMutatingArchive()
            throws Exception
    {
        ExactV7RenderRuntime runtime = exactV7RenderRuntime();
        for (boolean databaseMax : List.of(false, true))
        {
            OaSignPackage signPackage = exactV7Package(databaseMax);
            OaSignTemplate template = exactV7Template();
            GeneratedSignDocument generated = runtime.documentService().renderPackageDocument(
                    signPackage, template, List.of(
                            OaSignTemplateType.ONBOARD_LABOR_CONTRACT,
                            OaSignTemplateType.ONBOARD_HANDBOOK_RECEIPT));
            Path reviewPdf = runtime.storage().resolveAuthorizedFile(
                    generated.getReviewPdfArchiveRelativePath());
            OaSignLaborPlacementProfileRegistry.PlacementProfile profile =
                    new OaSignLaborAnchorPlacementResolver().resolve(reviewPdf, false,
                            signPackage.getLegalRepresentativeSnapshot(), true, false);
            List<OaSignedPdfService.PdfProtectedRegion> protectedRegions =
                    profile.protectedRegions().stream()
                            .map(value -> new OaSignedPdfService.PdfProtectedRegion(
                                    value.pageNumber(), value.x(), value.y(), value.width(),
                                    value.height()))
                            .toList();
            OaSignedPdfService.PdfImagePlacement signatures =
                    OaSignedPdfService.PdfImagePlacement.composite(
                            profile.signaturePlacements().stream()
                                    .map(value -> new OaSignedPdfService.PdfImagePlacement(
                                            value.pageNumber(), value.x(), value.y(),
                                            value.width(), value.height(), protectedRegions))
                                    .toList());
            OaSignedPdfService.PdfImagePlacement seal = profile.sealPlacements().stream()
                    .map(value -> new OaSignedPdfService.PdfImagePlacement(
                            value.pageNumber(), value.x(), value.y(), value.width(),
                            value.height(), protectedRegions))
                    .findFirst().orElseThrow();

            OaSignPackageDocument document = new OaSignPackageDocument();
            document.setDocumentId(databaseMax ? 902L : 901L);
            document.setPackageId(signPackage.getPackageId());
            document.setDocumentName("合成劳动合同");
            document.setTemplateType(OaSignTemplateType.ONBOARD_LABOR_CONTRACT);
            document.setTemplateVersionSnapshot("20260721-v7");
            document.setReviewPdfUrl(generated.getReviewPdfUrl());
            document.setReviewPdfHash(generated.getReviewPdfHash());
            document.setDocumentVersion(signPackage.getDocumentVersion());
            document.setFinalDocumentVersion(signPackage.getDocumentVersion());
            document.setEmployeeSignRequired("Y");
            document.setCompanySealRequired("Y");

            byte[] signature = signaturePng();
            byte[] companySeal = sealPng();
            Date employeeTime = Date.from(Instant.parse("2026-08-09T01:00:00Z"));
            Date companyTime = Date.from(Instant.parse("2026-08-09T01:05:00Z"));
            Date confirmationTime = Date.from(Instant.parse("2026-08-09T01:06:00Z"));
            OaSignFileStorageService signingStorage = storage();
            OaSignedPdfService signedService = new OaSignedPdfService(signingStorage);
            SignedPdfResult pending = signedService.generatePendingFinalPdf(null,
                    signPackage, document, reviewPdf, signature, companySeal,
                    employeeTime, companyTime, "合成签署确认", signatures, seal);
            Path pendingPath = signingStorage.resolveAuthorizedFile(
                    pending.getArchiveRelativePath());
            String finalRootHash = sha256((document.getDocumentId() + ":"
                    + pending.getContentPdfHash() + "\n").getBytes(StandardCharsets.UTF_8));
            signPackage.setFinalDocumentRootHash(finalRootHash);
            SignedPdfResult archived = signedService.archiveConfirmedFinalPdf(null,
                    signPackage, document, pendingPath, pending.getSignedPdfHash(),
                    pending.getContentPdfHash(), finalRootHash, signature, companySeal,
                    employeeTime, companyTime, confirmationTime, signatures, seal);
            Path archivePath = signingStorage.resolveAuthorizedFile(
                    archived.getArchiveRelativePath());
            String immutableArchiveHash = sha256(archivePath);

            OaSignedPdfService.FinalExportPreparation first =
                    signedService.prepareFinalExport(archivePath, immutableArchiveHash,
                            pending.getContentPdfHash(), finalRootHash, sha256(signature),
                            sha256(companySeal), signatures, seal, signature, companySeal,
                            List.of(), profile.expectedBodyPageCount(), null);
            OaSignedPdfService.FinalExportPreparation retry =
                    signedService.prepareFinalExport(archivePath, immutableArchiveHash,
                            pending.getContentPdfHash(), finalRootHash, sha256(signature),
                            sha256(companySeal), signatures, seal, signature, companySeal,
                            List.of(), profile.expectedBodyPageCount(), null);
            assertThat(Files.readAllBytes(retry.path()))
                    .isEqualTo(Files.readAllBytes(first.path()));
            assertThat(sha256(archivePath)).isEqualTo(immutableArchiveHash);

            try (PDDocument exported = Loader.loadPDF(first.path().toFile()))
            {
                assertThat(exported.getNumberOfPages())
                        .isEqualTo(profile.expectedBodyPageCount());
                String exportText = normalizedText(exported);
                assertThat(exportText.replaceAll("\\s+", ""))
                        .contains(signPackage.getLegalRepresentativeSnapshot()
                                .replaceAll("\\s+", ""));
                assertThat(exportText).doesNotContain("最终合同电子签署确认页",
                        "见本合同末页《电子签署确认页》");
                for (OaSignLaborPlacementProfileRegistry.PlacementRect placement
                        : profile.signaturePlacements())
                {
                    assertThat(imagePlacementMatrices(
                            exported.getPage(placement.pageNumber() - 1)))
                            .contains(new float[] { placement.width(), 0F, 0F,
                                    placement.height(), placement.x(), placement.y() });
                }
                OaSignLaborPlacementProfileRegistry.PlacementRect sealRect =
                        profile.sealPlacements().get(0);
                assertThat(imagePlacementMatrices(
                        exported.getPage(sealRect.pageNumber() - 1)))
                        .contains(new float[] { sealRect.width(), 0F, 0F,
                                sealRect.height(), sealRect.x(), sealRect.y() });
            }

            if (!databaseMax)
            {
                Path qaPdf = Path.of(System.getProperty("user.dir"))
                        .resolve("../../output/pdf/")
                        .resolve("erp-rev06-exact-v7-synthetic-signature-display.pdf")
                        .normalize().toAbsolutePath();
                Files.createDirectories(qaPdf.getParent());
                Files.copy(first.path(), qaPdf, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("REV06_SYNTHETIC_QA_PDF=" + qaPdf);
            }
            Files.deleteIfExists(first.path());
            Files.deleteIfExists(retry.path());
        }
    }

    @Test
    @DisplayName("历史展示版按冻结文本定位补齐甲方代表和员工手册且不改归档")
    void shouldRepairHistoricalRepresentativeAndHandbookInDisplayDerivative()
            throws Exception
    {
        OaSignedPdfService service = service();
        Path reviewPdf = syntheticHistoricalRepairReviewPdf(service);
        OaSignPackage signPackage = signPackage(430L);
        signPackage.setFinalDocumentVersion("SP-430-V2");
        OaSignPackageDocument document = signDocument(70L, 430L, reviewPdf);
        document.setEmployeeSignRequired("Y");
        document.setCompanySealRequired("Y");
        byte[] signature = signaturePng();
        byte[] seal = sealPng();
        Date employeeTime = Date.from(Instant.parse("2026-07-11T09:00:00Z"));
        Date companyTime = Date.from(Instant.parse("2026-07-11T10:00:00Z"));
        Date confirmationTime = Date.from(Instant.parse("2026-07-11T10:01:00Z"));
        OaSignedPdfService.PdfImagePlacement signaturePlacement =
                new OaSignedPdfService.PdfImagePlacement(14, 319, 699, 90, 42.3F);
        OaSignedPdfService.PdfImagePlacement sealPlacement =
                new OaSignedPdfService.PdfImagePlacement(14, 180, 688, 62, 61);
        SignedPdfResult pending = service.generatePendingFinalPdf(null, signPackage, document,
                reviewPdf, signature, seal, employeeTime, companyTime,
                "历史文字展示", signaturePlacement, sealPlacement);
        Path pendingPath = storage().resolveAuthorizedFile(pending.getArchiveRelativePath());
        String finalRootHash = sha256((document.getDocumentId() + ":"
                + pending.getContentPdfHash() + "\n").getBytes(StandardCharsets.UTF_8));
        SignedPdfResult archive = service.archiveConfirmedFinalPdf(null, signPackage, document,
                pendingPath, pending.getSignedPdfHash(), pending.getContentPdfHash(),
                finalRootHash, signature, seal, employeeTime, companyTime,
                confirmationTime, signaturePlacement, sealPlacement);
        Path archivePath = storage().resolveAuthorizedFile(archive.getArchiveRelativePath());
        String archiveHash = sha256(archivePath);
        String syntheticRepresentative = "合成代表";
        List<OaSignedPdfService.PdfTextOverlay> textOverlays = List.of(
                new OaSignedPdfService.PdfTextOverlay("companyLegalRepresentative",
                        "甲方代表：" + syntheticRepresentative,
                        new OaSignedPdfService.PdfTextPlacement("companyLegalRepresentative",
                                14, 112.1F, 651F, 143F, 19F, 12,
                                List.of())),
                new OaSignedPdfService.PdfTextOverlay("attachmentHandbookMark",
                        "☑ 3《员工手册》",
                        new OaSignedPdfService.PdfTextPlacement("attachmentHandbookMark",
                                14, 112.1F, 430F, 108F, 19F, 12,
                                List.of())),
                new OaSignedPdfService.PdfTextOverlay("archiveEvidenceNotice",
                        "签署校验证据请查看原最终归档及签署凭证。",
                        new OaSignedPdfService.PdfTextPlacement("archiveEvidenceNotice",
                                14, 112.1F, 611.8F, 332F, 19F, 8.5F,
                                List.of())));

        OaSignedPdfService.FinalExportPreparation preparation = service.prepareFinalExport(
                archivePath, archiveHash, pending.getContentPdfHash(), finalRootHash,
                sha256(signature), sha256(seal), signaturePlacement, sealPlacement,
                signature, seal, textOverlays);

        assertThat(sha256(archivePath)).isEqualTo(archiveHash);
        try (PDDocument exported = Loader.loadPDF(preparation.path().toFile()))
        {
            assertThat(exported.getNumberOfPages()).isEqualTo(18);
            assertThat(normalizedText(exported))
                    .contains(syntheticRepresentative, "☑ 3《员工手册》",
                            "签署校验证据请查看原最终归档及签署凭证。")
                    .doesNotContain("最终合同电子签署确认页",
                            "签署时间及文件校验信息见本合同末页《电子签署确认页》",
                            "□ 3《员工手册》", "____________");
            assertThat(exported.getDocumentInformation().getCustomMetadataValue(
                    "ERP-Final-Export-Text-Repair-Fields"))
                    .isEqualTo("archiveEvidenceNotice,attachmentHandbookMark,"
                            + "companyLegalRepresentative");
        }
        Files.copy(preparation.path(),
                Path.of("/tmp/erp-p004-sign-export-text-repair-qa.pdf"),
                StandardCopyOption.REPLACE_EXISTING);
        Files.deleteIfExists(preparation.path());
    }

    @Test
    @DisplayName("签章目标矩形与身份证或日期保护区相交时失败关闭")
    void shouldRejectPlacementIntersectingProtectedRegion() throws Exception
    {
        OaSignedPdfService service = service();
        Path reviewPdf = sampleReviewPdf();
        OaSignPackage signPackage = signPackage(429L);
        OaSignPackageDocument document = signDocument(69L, 429L, reviewPdf);
        OaSignedPdfService.PdfImagePlacement placement =
                new OaSignedPdfService.PdfImagePlacement(1, 72, 80, 180, 70,
                        List.of(new OaSignedPdfService.PdfProtectedRegion(
                                1, 100, 100, 20, 20)));

        assertThatThrownBy(() -> service.generatePendingFinalPdf(null, signPackage, document,
                reviewPdf, signaturePng(), null,
                Date.from(Instant.parse("2026-07-11T09:00:00Z")), null,
                "保护区冲突", placement, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("与受保护区域重叠");
    }

    private void assertPartialPlacementExportDoesNotDuplicate(boolean signatureAlreadyPresent)
            throws Exception
    {
        OaSignedPdfService service = service();
        Path reviewPdf = sampleReviewPdf();
        long packageId = signatureAlreadyPresent ? 426L : 427L;
        long documentId = signatureAlreadyPresent ? 66L : 67L;
        OaSignPackage signPackage = signPackage(packageId);
        signPackage.setFinalDocumentVersion("SP-" + packageId + "-V2");
        OaSignPackageDocument document = signDocument(documentId, packageId, reviewPdf);
        document.setEmployeeSignRequired("Y");
        document.setCompanySealRequired("Y");
        byte[] signature = signaturePng();
        byte[] seal = sealPng();
        Date employeeTime = Date.from(Instant.parse("2026-07-11T09:00:00Z"));
        Date companyTime = Date.from(Instant.parse("2026-07-11T10:00:00Z"));
        Date confirmationTime = Date.from(Instant.parse("2026-07-11T10:01:00Z"));
        OaSignedPdfService.PdfImagePlacement signaturePlacement =
                new OaSignedPdfService.PdfImagePlacement(1, 72, 80, 180, 70);
        OaSignedPdfService.PdfImagePlacement sealPlacement =
                new OaSignedPdfService.PdfImagePlacement(1, 330, 60, 100, 100);

        SignedPdfResult pending = service.generatePendingFinalPdf(null, signPackage, document,
                reviewPdf, signature, seal, employeeTime, companyTime,
                "部分正文签章历史归档",
                signatureAlreadyPresent ? signaturePlacement : null,
                signatureAlreadyPresent ? null : sealPlacement);
        Path pendingPath = storage().resolveAuthorizedFile(pending.getArchiveRelativePath());
        String finalRootHash = sha256((document.getDocumentId() + ":"
                + pending.getContentPdfHash() + "\n").getBytes(StandardCharsets.UTF_8));
        SignedPdfResult archive = service.archiveConfirmedFinalPdf(null, signPackage, document,
                pendingPath, pending.getSignedPdfHash(), pending.getContentPdfHash(),
                finalRootHash, signature, seal, employeeTime, companyTime,
                confirmationTime, signaturePlacement, sealPlacement);
        Path archivePath = storage().resolveAuthorizedFile(archive.getArchiveRelativePath());

        OaSignedPdfService.FinalExportPreparation preparation = service.prepareFinalExport(
                archivePath, sha256(archivePath), pending.getContentPdfHash(), finalRootHash,
                sha256(signature), sha256(seal), signaturePlacement, sealPlacement,
                signature, seal);

        assertThat(preparation.derived()).isTrue();
        try (PDDocument exported = Loader.loadPDF(preparation.path().toFile()))
        {
            PDPage bodyPage = exported.getPage(0);
            List<float[]> matrices = imagePlacementMatrices(bodyPage);
            assertThat(matrices).hasSize(2);
            assertThat(matrices).anySatisfy(matrix -> assertThat(matrix)
                    .containsExactly(180F, 0F, 0F, 70F, 72F, 80F));
            assertThat(matrices).anySatisfy(matrix -> assertThat(matrix)
                    .containsExactly(100F, 0F, 0F, 100F, 330F, 60F));
            assertThat(imageCount(bodyPage)).isEqualTo(2);
        }
        Files.deleteIfExists(preparation.path());
    }

    @Test
    @DisplayName("历史正文已有签章但不在冻结坐标时拒绝伪造恢复")
    void shouldRejectHistoricalMarkAtDifferentFrozenCoordinate() throws Exception
    {
        OaSignedPdfService service = service();
        Path reviewPdf = sampleReviewPdf();
        OaSignPackage signPackage = signPackage(425L);
        signPackage.setFinalDocumentVersion("SP-425-V2");
        OaSignPackageDocument document = signDocument(65L, 425L, reviewPdf);
        document.setEmployeeSignRequired("Y");
        document.setCompanySealRequired("N");
        byte[] signature = signaturePng();
        Date employeeTime = Date.from(Instant.parse("2026-07-11T09:00:00Z"));
        Date confirmationTime = Date.from(Instant.parse("2026-07-11T10:01:00Z"));
        OaSignedPdfService.PdfImagePlacement wrongPlacement =
                new OaSignedPdfService.PdfImagePlacement(1, 72, 80, 180, 70);
        OaSignedPdfService.PdfImagePlacement frozenPlacement =
                new OaSignedPdfService.PdfImagePlacement(1, 100, 80, 180, 70);
        SignedPdfResult pending = service.generatePendingFinalPdf(null, signPackage, document,
                reviewPdf, signature, null, employeeTime, null,
                "历史签章位置冲突", wrongPlacement, null);
        Path pendingPath = storage().resolveAuthorizedFile(pending.getArchiveRelativePath());
        String root = sha256((document.getDocumentId() + ":"
                + pending.getContentPdfHash() + "\n").getBytes(StandardCharsets.UTF_8));
        SignedPdfResult archive = service.archiveConfirmedFinalPdf(null, signPackage, document,
                pendingPath, pending.getSignedPdfHash(), pending.getContentPdfHash(), root,
                signature, null, employeeTime, null, confirmationTime,
                wrongPlacement, null);
        Path archivePath = storage().resolveAuthorizedFile(archive.getArchiveRelativePath());

        assertThatThrownBy(() -> service.prepareFinalExport(archivePath, sha256(archivePath),
                pending.getContentPdfHash(), root, sha256(signature), null,
                frozenPlacement, null, signature, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("签名位置证据不一致");
    }

    @Test
    @DisplayName("已正确落章的归档直接复用原字节，追加确认页策略不重复落章")
    void shouldReuseCorrectPlacedAndAppendedArchives() throws Exception
    {
        OaSignedPdfService service = service();
        Path reviewPdf = sampleReviewPdf();
        OaSignPackage signPackage = signPackage(422L);
        signPackage.setFinalDocumentVersion("SP-422-V2");
        OaSignPackageDocument document = signDocument(62L, 422L, reviewPdf);
        document.setEmployeeSignRequired("Y");
        document.setCompanySealRequired("N");
        byte[] signature = signaturePng();
        Date employeeTime = Date.from(Instant.parse("2026-07-11T09:00:00Z"));
        Date confirmationTime = Date.from(Instant.parse("2026-07-11T10:01:00Z"));
        OaSignedPdfService.PdfImagePlacement placement =
                new OaSignedPdfService.PdfImagePlacement(1, 72, 80, 180, 70);

        SignedPdfResult pending = service.generatePendingFinalPdf(null, signPackage, document,
                reviewPdf, signature, null, employeeTime, null,
                "签名优先归档", placement, null);
        Path pendingPath = storage().resolveAuthorizedFile(pending.getArchiveRelativePath());
        String finalRootHash = sha256((document.getDocumentId() + ":"
                + pending.getContentPdfHash() + "\n").getBytes(StandardCharsets.UTF_8));
        SignedPdfResult archive = service.archiveConfirmedFinalPdf(null, signPackage, document,
                pendingPath, pending.getSignedPdfHash(), pending.getContentPdfHash(),
                finalRootHash, signature, null, employeeTime, null,
                confirmationTime, placement, null);
        Path archivePath = storage().resolveAuthorizedFile(archive.getArchiveRelativePath());
        String archiveHash = sha256(archivePath);
        OaSignedPdfService.FinalExportPreparation placed = service.prepareFinalExport(
                archivePath, archiveHash, pending.getContentPdfHash(), finalRootHash,
                sha256(signature), null, placement, null, signature, null);
        assertThat(placed.path()).isNotEqualTo(archivePath);
        assertThat(placed.derived()).isTrue();
        try (PDDocument exported = Loader.loadPDF(placed.path().toFile()))
        {
            assertThat(exported.getNumberOfPages()).isEqualTo(1);
            assertThat(normalizedText(exported))
                    .doesNotContain("最终合同电子签署确认页");
        }

        OaSignPackage appendedPackage = signPackage(423L);
        appendedPackage.setFinalDocumentVersion("SP-423-V2");
        OaSignPackageDocument appendedDocument = signDocument(63L, 423L, reviewPdf);
        appendedDocument.setEmployeeSignRequired("Y");
        appendedDocument.setCompanySealRequired("N");
        SignedPdfResult appendedPending = service.generatePendingFinalPdf(null,
                appendedPackage, appendedDocument, reviewPdf, signature, null,
                employeeTime, null, "追加确认页", null, null);
        Path appendedPendingPath = storage().resolveAuthorizedFile(
                appendedPending.getArchiveRelativePath());
        String appendedRoot = sha256((appendedDocument.getDocumentId() + ":"
                + appendedPending.getContentPdfHash() + "\n")
                .getBytes(StandardCharsets.UTF_8));
        SignedPdfResult appendedArchive = service.archiveConfirmedFinalPdf(null,
                appendedPackage, appendedDocument, appendedPendingPath,
                appendedPending.getSignedPdfHash(), appendedPending.getContentPdfHash(),
                appendedRoot, signature, null, employeeTime, null, confirmationTime,
                null, null);
        Path appendedArchivePath = storage().resolveAuthorizedFile(
                appendedArchive.getArchiveRelativePath());
        assertThatThrownBy(() -> service.prepareFinalExport(
                appendedArchivePath, sha256(appendedArchivePath),
                appendedPending.getContentPdfHash(), appendedRoot, sha256(signature),
                null, null, null, signature, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("缺少签章位置或证据");
    }

    @Test
    @DisplayName("最后一页冻结坐标恢复到正文尾页而不是确认页")
    void shouldRestoreLastPagePlacementBeforeConfirmationPage() throws Exception
    {
        OaSignedPdfService service = service();
        Path reviewPdf = twoPageReviewPdf();
        OaSignPackage signPackage = signPackage(424L);
        signPackage.setFinalDocumentVersion("SP-424-V2");
        OaSignPackageDocument document = signDocument(64L, 424L, reviewPdf);
        document.setEmployeeSignRequired("N");
        document.setCompanySealRequired("Y");
        byte[] seal = sealPng();
        Date companyTime = Date.from(Instant.parse("2026-07-11T10:00:00Z"));
        Date confirmationTime = Date.from(Instant.parse("2026-07-11T10:01:00Z"));
        OaSignedPdfService.PdfImagePlacement placement =
                OaSignedPdfService.PdfImagePlacement.lastPage(330, 60, 100, 100);
        SignedPdfResult pending = service.generatePendingFinalPdf(null, signPackage, document,
                reviewPdf, null, seal, null, companyTime, "最后一页盖章", null, null);
        Path pendingPath = storage().resolveAuthorizedFile(pending.getArchiveRelativePath());
        String root = sha256((document.getDocumentId() + ":"
                + pending.getContentPdfHash() + "\n").getBytes(StandardCharsets.UTF_8));
        SignedPdfResult archive = service.archiveConfirmedFinalPdf(null, signPackage, document,
                pendingPath, pending.getSignedPdfHash(), pending.getContentPdfHash(), root,
                null, seal, null, companyTime, confirmationTime, null, placement);
        Path archivePath = storage().resolveAuthorizedFile(archive.getArchiveRelativePath());
        OaSignedPdfService.FinalExportPreparation preparation = service.prepareFinalExport(
                archivePath, sha256(archivePath), pending.getContentPdfHash(), root, null,
                sha256(seal), null, placement, null, seal);

        assertThat(preparation.derived()).isTrue();
        try (PDDocument exported = Loader.loadPDF(preparation.path().toFile()))
        {
            assertThat(imagePlacementMatrices(exported.getPage(1))).contains(
                    new float[] {100F, 0F, 0F, 100F, 330F, 60F});
            assertThat(exported.getNumberOfPages()).isEqualTo(2);
            assertThat(normalizedText(exported))
                    .doesNotContain("最终合同电子签署确认页");
            assertThat(imageCount(exported.getPage(exported.getNumberOfPages() - 1)))
                    .isEqualTo(1);
        }
        Files.deleteIfExists(preparation.path());
    }

    @Test
    @DisplayName("页码或图片坐标越界时拒绝签署且不归档")
    void shouldRejectOutOfBoundsPlacementWithoutArchive() throws Exception
    {
        OaSignedPdfService service = service();
        Path reviewPdf = sampleReviewPdf();
        OaSignPackage signPackage = signPackage(402L);
        OaSignPackageDocument document = signDocument(42L, 402L, reviewPdf);

        assertThatThrownBy(() -> service.generateSignedPdf(null, signPackage, document, reviewPdf,
                signaturePng(), null, new Date(), "本人确认签署本签约包",
                new OaSignedPdfService.PdfImagePlacement(2, 10, 10, 100, 40), null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("签名页码不存在");
        assertThatThrownBy(() -> service.generateSignedPdf(null, signPackage, document, reviewPdf,
                signaturePng(), null, new Date(), "本人确认签署本签约包",
                new OaSignedPdfService.PdfImagePlacement(1, 560, 10, 100, 40), null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("签名坐标越界");
        assertThatThrownBy(() -> service.generateSignedPdf(null, signPackage, document, reviewPdf,
                signaturePng(), null, new Date(), "本人确认签署本签约包",
                new OaSignedPdfService.PdfImagePlacement(1, Float.NaN, 10, 100, 40), null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("签名坐标越界");
        assertThat(archiveFiles()).isEmpty();
    }

    @Test
    @DisplayName("无图像或图像缺失却配置坐标时失败关闭")
    void shouldRejectNoImagesAndPlacementsForMissingImages() throws Exception
    {
        OaSignedPdfService service = service();
        Path reviewPdf = sampleReviewPdf();
        OaSignPackage signPackage = signPackage(410L);
        OaSignPackageDocument document = signDocument(50L, 410L, reviewPdf);
        OaSignedPdfService.PdfImagePlacement placement =
                new OaSignedPdfService.PdfImagePlacement(1, 72, 80, 180, 70);

        assertThatThrownBy(() -> service.generateSignedPdf(null, signPackage, document, reviewPdf,
                null, null, new Date(), "最终文件", null, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("至少提供签名或企业章图片");
        assertThatThrownBy(() -> service.generateSignedPdf(null, signPackage, document, reviewPdf,
                null, sealPng(), new Date(), "最终文件", placement, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("签名图片不存在");
        assertThatThrownBy(() -> service.generateSignedPdf(null, signPackage, document, reviewPdf,
                signaturePng(), null, new Date(), "最终文件", null, placement))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("企业章图片不存在");
        assertThat(archiveFiles()).isEmpty();
    }

    @Test
    @DisplayName("签名图片无效时拒绝生成")
    void shouldRejectInvalidSignatureImage() throws Exception
    {
        Path reviewPdf = sampleReviewPdf();
        OaSignPackage signPackage = signPackage(403L);
        OaSignPackageDocument document = signDocument(43L, 403L, reviewPdf);

        assertThatThrownBy(() -> service().generateSignedPdf(null, signPackage, document, reviewPdf,
                "not-a-png".getBytes(), null, new Date(), "本人确认签署本签约包", null, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("签名图片不合法");
        assertThat(archiveFiles()).isEmpty();
    }

    @Test
    @DisplayName("企业章支持JPEG时员工签名仍只允许PNG")
    void shouldKeepEmployeeSignaturePngOnlyWhenSealAllowsJpeg() throws Exception
    {
        Path reviewPdf = sampleReviewPdf();
        OaSignPackage signPackage = signPackage(417L);
        OaSignPackageDocument document = signDocument(57L, 417L, reviewPdf);

        assertThatThrownBy(() -> service().generateSignedPdf(null, signPackage, document, reviewPdf,
                sealJpeg(), null, new Date(), "本人确认签署本签约包", null, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("签名图片不合法");
        assertThat(archiveFiles()).isEmpty();
    }

    @Test
    @DisplayName("企业章不接受PNG和JPEG以外的可解码图片")
    void shouldRejectDecodableButUnsupportedCompanySealFormat() throws Exception
    {
        Path reviewPdf = sampleReviewPdf();
        OaSignPackage signPackage = signPackage(418L);
        OaSignPackageDocument document = signDocument(58L, 418L, reviewPdf);

        assertThatThrownBy(() -> service().generateSignedPdf(null, signPackage, document, reviewPdf,
                null, sealGif(), new Date(), "公司完成最终文件生成", null, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("企业章图片不合法");
        assertThat(archiveFiles()).isEmpty();
    }

    private String normalizedText(PDDocument document) throws Exception
    {
        return java.text.Normalizer.normalize(new PDFTextStripper().getText(document),
                java.text.Normalizer.Form.NFKC).replace('⻚', '页');
    }

    private Set<Path> finalExportTempFiles() throws Exception
    {
        Path root = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
        try (java.util.stream.Stream<Path> files = Files.list(root))
        {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString()
                            .startsWith("erp-final-signature-display-"))
                    .map(path -> path.toAbsolutePath().normalize())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
    }

    private Path sampleAuditRoot()
    {
        return OaSignGoldenFixture.requireRoot();
    }

    private String normalizedPageText(PDDocument document, int pageNumber) throws Exception
    {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(pageNumber);
        stripper.setEndPage(pageNumber);
        return java.text.Normalizer.normalize(stripper.getText(document),
                java.text.Normalizer.Form.NFKC).replace('⻚', '页').replaceAll("\\s+", "");
    }

    private void assertProtectedTextHashUnchanged(PDDocument source, PDDocument exported,
            int pageNumber, String label) throws Exception
    {
        String sourceText = protectedText(source, pageNumber, label);
        String exportedText = protectedText(exported, pageNumber, label);
        assertThat(sha256(sourceText.getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(sha256(exportedText.getBytes(StandardCharsets.UTF_8)));
    }

    private String protectedText(PDDocument document, int pageNumber, String label)
            throws Exception
    {
        String page = normalizedPageText(document, pageNumber);
        int start = page.indexOf(label);
        assertThat(start).as("受保护标签必须存在").isGreaterThanOrEqualTo(0);
        int end = Math.min(page.length(), start + label.length() + 40);
        return page.substring(start, end);
    }

    private OaSignPackage historicalGoldenPackage() throws Exception
    {
        com.fasterxml.jackson.databind.JsonNode evidence = historicalRepresentativeEvidence();
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageNo("SP1785202186549261523");
        signPackage.setLegalEntityIdSnapshot(evidence.path("legalEntityId").longValue());
        signPackage.setLegalEntityNameSnapshot(evidence.path("legalEntityName").asText());
        signPackage.setFinalConfirmedTime(Date.from(Instant.parse("2026-07-28T00:00:00Z")));
        signPackage.setSignatureSampleHash(SAMPLE_SIGNATURE_HASH);
        signPackage.setSealImageHashSnapshot(SAMPLE_SEAL_HASH);
        return signPackage;
    }

    private String historicalRepresentativeValue() throws Exception
    {
        return historicalRepresentativeEvidence().path("legalRepresentative").asText();
    }

    private com.fasterxml.jackson.databind.JsonNode historicalRepresentativeEvidence()
            throws Exception
    {
        try (java.io.InputStream input = getClass().getResourceAsStream(
                "/oa/sign/evidence/legal-entity-4-representative-20260719-v1.json"))
        {
            assertThat(input).isNotNull();
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(input);
        }
    }

    private String textAt(PDDocument document,
            OaSignedPdfService.PdfTextPlacement placement) throws Exception
    {
        PDPage page = document.getPage(placement.pageNumber() - 1);
        PDRectangle cropBox = page.getCropBox();
        float top = cropBox.getHeight()
                - (placement.y() - cropBox.getLowerLeftY()) - placement.height();
        float left = placement.x() - cropBox.getLowerLeftX();
        PDFTextStripperByArea stripper = new PDFTextStripperByArea();
        stripper.setSortByPosition(true);
        stripper.addRegion("target", new Rectangle2D.Float(left, top,
                placement.width(), placement.height()));
        stripper.extractRegions(page);
        return java.text.Normalizer.normalize(stripper.getTextForRegion("target"),
                java.text.Normalizer.Form.NFKC).replaceAll("[\\s_＿]+", "");
    }

    private long imageCount(PDPage page) throws Exception
    {
        if (page.getResources() == null)
        {
            return 0;
        }
        long count = 0;
        for (COSName name : page.getResources().getXObjectNames())
        {
            if (page.getResources().getXObject(name) instanceof PDImageXObject)
            {
                count++;
            }
        }
        return count;
    }

    private List<float[]> imagePlacementMatrices(PDPage page) throws Exception
    {
        PDFStreamParser parser = new PDFStreamParser(page);
        List<Object> tokens;
        try
        {
            tokens = parser.parse();
        }
        finally
        {
            parser.close();
        }

        List<float[]> matrices = new ArrayList<>();
        float[] latestMatrix = null;
        for (int index = 0; index < tokens.size(); index++)
        {
            Object token = tokens.get(index);
            if (!(token instanceof Operator operator))
            {
                continue;
            }
            if ("cm".equals(operator.getName()) && index >= 6)
            {
                latestMatrix = new float[6];
                for (int offset = 0; offset < latestMatrix.length; offset++)
                {
                    latestMatrix[offset] = ((COSNumber) tokens.get(index - 6 + offset)).floatValue();
                }
                continue;
            }
            if ("Do".equals(operator.getName()) && index >= 1 && latestMatrix != null
                    && tokens.get(index - 1) instanceof COSName imageName
                    && page.getResources().getXObject(imageName) instanceof PDImageXObject)
            {
                matrices.add(latestMatrix.clone());
            }
        }
        return matrices;
    }

    private void assertUsesPortableCrossReferenceFormat(Path pdf) throws Exception
    {
        String syntax = new String(Files.readAllBytes(pdf), StandardCharsets.ISO_8859_1);
        assertThat(syntax).as("PDF应避免对部分Poppler产生黑页的压缩对象流")
                .doesNotContain("/Type /ObjStm");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = { false, true })
    void partialPromotionFailureCleansOnlyThisAttempt(boolean failAfterSecondPromotion) throws Exception
    {
        OaSignFileStorageService storage = org.mockito.Mockito.spy(storage());
        java.util.concurrent.atomic.AtomicInteger promotions = new java.util.concurrent.atomic.AtomicInteger();
        org.mockito.Mockito.doAnswer(call -> {
            int attempt = promotions.incrementAndGet();
            if (attempt == 2 && !failAfterSecondPromotion) throw new ServiceException("second promote failed");
            Object result = call.callRealMethod();
            if (attempt == 2) throw new ServiceException("second promote failed after move");
            return result;
        }).when(storage).promote(org.mockito.ArgumentMatchers.any());
        Path original = tempDir.resolve("archive/retained.pdf");
        Files.createDirectories(original.getParent());
        Files.writeString(original, "previous committed evidence");
        Path review = twoPageReviewPdf();
        String reviewHash = sha256(review);
        OaSignPackage signPackage = signPackage(400L);
        OaSignPackageDocument document = signDocument(40L, 400L, review);
        assertThatThrownBy(() -> new OaSignedPdfService(storage).generateSignedPdf(null,
                signPackage, document, review, signaturePng(), null, new Date(), null,
                "本人确认签署本签约包", null, null)).hasMessageContaining("second promote failed");
        assertThat(Files.readString(original)).isEqualTo("previous committed evidence");
        assertThat(sha256(review)).isEqualTo(reviewHash);
        try (var files = Files.walk(tempDir.resolve("archive")))
        {
            assertThat(files.filter(Files::isRegularFile).toList()).containsExactly(original);
        }
        try (var files = Files.walk(tempDir.resolve("staging")))
        {
            assertThat(files.filter(Files::isRegularFile).toList()).isEmpty();
        }
    }

    @Test
    void cleanupFailureDoesNotMaskThePromotionFailure() throws Exception
    {
        OaSignFileStorageService storage = org.mockito.Mockito.spy(storage());
        java.util.concurrent.atomic.AtomicInteger promotions = new java.util.concurrent.atomic.AtomicInteger();
        org.mockito.Mockito.doAnswer(call -> {
            if (promotions.incrementAndGet() == 2) throw new ServiceException("original promote failure");
            return call.callRealMethod();
        }).when(storage).promote(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.doThrow(new ServiceException("cleanup unavailable"))
                .when(storage).discardUncommitted(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        Path review = twoPageReviewPdf();
        OaSignPackage signPackage = signPackage(400L);
        OaSignPackageDocument document = signDocument(40L, 400L, review);
        assertThatThrownBy(() -> new OaSignedPdfService(storage).generateSignedPdf(null,
                signPackage, document, review, signaturePng(), null, new Date(), null,
                "本人确认签署本签约包", null, null))
                .hasMessageContaining("original promote failure")
                .satisfies(error -> assertThat(error.getSuppressed()).singleElement()
                        .extracting(Throwable::getMessage).isEqualTo("cleanup unavailable"));
    }

    private OaSignedPdfService service()
    {
        return new OaSignedPdfService(storage());
    }

    private ExactV7RenderRuntime exactV7RenderRuntime()
    {
        OaSignFileProperties properties = new OaSignFileProperties();
        properties.getStorage().setRootPath(tempDir.resolve("exact-v7-generated").toString());
        properties.getStorage().setTempPath(tempDir.resolve("exact-v7-staging").toString());
        properties.getStorage().setPublicPrefix("/profile/private/sign-package");
        properties.getPdf().setConverterCommand(resolveOfficeCommand());
        properties.getPdf().setTimeoutSeconds(180);
        OaSignFileStorageService generatedStorage = new OaSignFileStorageService(properties);
        OaSignDocumentService documentService = new OaSignDocumentService();
        ReflectionTestUtils.setField(documentService, "localFilePath", tempDir.toString());
        ReflectionTestUtils.setField(documentService, "localFilePrefix", "/profile");
        ReflectionTestUtils.setField(documentService, "fileStorageService", generatedStorage);
        ReflectionTestUtils.setField(documentService, "officePdfConverter",
                new OaOfficePdfConverter(properties));
        ReflectionTestUtils.setField(documentService, "pdfPageNumberService",
                new OaPdfPageNumberService());
        return new ExactV7RenderRuntime(documentService, generatedStorage);
    }

    private String resolveOfficeCommand()
    {
        String configured = System.getProperty("sign.test.office.command");
        if (StringUtils.isBlank(configured))
        {
            configured = System.getenv("SIGN_TEST_OFFICE_COMMAND");
        }
        if (StringUtils.isNotBlank(configured))
        {
            Path executable = Path.of(configured).toAbsolutePath().normalize();
            org.junit.jupiter.api.Assumptions.assumeTrue(Files.isExecutable(executable),
                    "exact-v7签章链未执行：SIGN_TEST_OFFICE_COMMAND不可执行");
            return executable.toString();
        }
        String path = System.getenv("PATH");
        if (path != null)
        {
            for (String directory : path.split(java.io.File.pathSeparator))
            {
                for (String name : List.of("soffice", "libreoffice", "soffice.exe",
                        "libreoffice.exe"))
                {
                    Path executable = Path.of(directory, name).toAbsolutePath().normalize();
                    if (Files.isExecutable(executable))
                    {
                        return executable.toString();
                    }
                }
            }
        }
        org.junit.jupiter.api.Assumptions.assumeTrue(false,
                "exact-v7签章链未执行：请在PATH提供soffice/libreoffice，"
                        + "或设置SIGN_TEST_OFFICE_COMMAND");
        return "soffice";
    }

    private OaSignTemplate exactV7Template() throws Exception
    {
        Path file = tempDir.resolve("exact-v7-source.docx");
        if (!Files.exists(file))
        {
            try (java.io.InputStream input = getClass().getResourceAsStream(
                    "/oa/sign/templates/onboard-20260721-v7/09_ONBOARD_LABOR_CONTRACT.docx"))
            {
                assertThat(input).as("exact-v7 publishable source").isNotNull();
                Files.copy(input, file);
            }
        }
        assertThat(sha256(file)).isEqualTo(
                "1226728ec0e703d513efda83dc5578ee4cdd65cb3e7d0e7cb5afee813f466558");
        OaSignTemplate template = new OaSignTemplate();
        template.setTemplateId(2026072109L);
        template.setTemplateType(OaSignTemplateType.ONBOARD_LABOR_CONTRACT);
        template.setTemplateVersion("20260721-v7");
        template.setFileUrl(file.toString());
        template.setFileHash(
                "1226728ec0e703d513efda83dc5578ee4cdd65cb3e7d0e7cb5afee813f466558");
        return template;
    }

    private OaSignPackage exactV7Package(boolean databaseMax)
    {
        OaSignPackage value = new OaSignPackage();
        value.setPackageId(databaseMax ? 9902L : 9901L);
        value.setPackageNo(databaseMax ? "SP-9902" : "SP-9901");
        value.setDocumentVersion(databaseMax ? "SP-9902-V1" : "SP-9901-V1");
        value.setFinalDocumentVersion(databaseMax ? "SP-9902-V2" : "SP-9901-V2");
        value.setScenario("onboard");
        value.setEmploymentType("劳动合同");
        value.setEmployeeNameSnapshot(databaseMax ? fixedWidth("合成长姓名", 64) : "合成员工甲");
        value.setEmployeePhoneSnapshot(databaseMax ? "1".repeat(32) : "18800000000");
        value.setEmployeeIdCardSnapshot(databaseMax ? "9".repeat(32) : "TEST-ID-****-0000");
        value.setEmployeeAddressSnapshot(databaseMax
                ? fixedWidth("合成长送达地址", 200) : "合成测试地址");
        value.setLegalEntityIdSnapshot(999L);
        value.setLegalEntityNameSnapshot(databaseMax
                ? fixedWidth("合成长法律主体", 160) : "合成法律主体有限公司");
        value.setLegalEntityAddressSnapshot(databaseMax
                ? fixedWidth("合成长注册地址", 200) : "合成注册地址");
        value.setLegalRepresentativeSnapshot(databaseMax
                ? fixedWidth("合成长代表", 64) : "合成代表甲");
        value.setPostNameSnapshot(databaseMax ? fixedWidth("合成长岗位", 64) : "合成岗位");
        value.setPostLevelSnapshot(databaseMax ? "99" : "1");
        value.setContractTermCodeSnapshot("FIXED_TERM");
        value.setContractStartDate(databaseMax ? "9999-12-31" : "2026-08-09");
        value.setContractEndDate(databaseMax ? "9999-12-31" : "2029-08-08");
        value.setProbationStartDate(databaseMax ? "9999-12-31" : "2026-08-09");
        value.setProbationEndDate(databaseMax ? "9999-12-31" : "2026-11-08");
        value.setBaseSalary(new BigDecimal(databaseMax ? "99999999999999.99" : "5000.00"));
        value.setPostSalary(new BigDecimal(databaseMax ? "99999999999999.99" : "1000.00"));
        value.setFieldAllowance(new BigDecimal(databaseMax ? "99999999999999.99" : "0.00"));
        value.setPerformanceSalary(new BigDecimal(databaseMax ? "99999999999999.99" : "0.00"));
        value.setSalaryTotal(new BigDecimal(databaseMax ? "99999999999999.99" : "6000.00"));
        return value;
    }

    private String fixedWidth(String seed, int width)
    {
        return seed.repeat((width / seed.length()) + 1).substring(0, width);
    }

    private record ExactV7RenderRuntime(OaSignDocumentService documentService,
            OaSignFileStorageService storage) {}

    private OaSignFileStorageService storage()
    {
        OaSignFileProperties properties = new OaSignFileProperties();
        properties.getStorage().setRootPath(tempDir.resolve("archive").toString());
        properties.getStorage().setTempPath(tempDir.resolve("staging").toString());
        properties.getStorage().setPublicPrefix("/profile/private/sign-package");
        return new OaSignFileStorageService(properties);
    }

    private Path sampleReviewPdf() throws Exception
    {
        Path target = tempDir.resolve("sample-review.pdf");
        if (!Files.exists(target))
        {
            try (java.io.InputStream input = getClass().getResourceAsStream("/signing/sample-review.pdf"))
            {
                assertThat(input).isNotNull();
                Files.copy(input, target);
            }
        }
        return target;
    }

    private Path reviewPdfWithInheritedImageResource() throws Exception
    {
        Path target = tempDir.resolve("inherited-image-review.pdf");
        if (!Files.exists(target))
        {
            try (PDDocument document = Loader.loadPDF(sampleReviewPdf().toFile()))
            {
                PDResources inheritedResources = new PDResources();
                inheritedResources.add(PDImageXObject.createFromByteArray(document,
                        signaturePng(), "inherited-unused-image.png"));
                document.getDocumentCatalog().getPages().getCOSObject()
                        .setItem(COSName.RESOURCES, inheritedResources);
                document.save(target.toFile());
            }
        }
        return target;
    }

    private Path twoPageReviewPdf() throws Exception
    {
        Path target = tempDir.resolve("two-page-review.pdf");
        if (!Files.exists(target))
        {
            try (PDDocument document = Loader.loadPDF(sampleReviewPdf().toFile()))
            {
                document.addPage(new PDPage(PDRectangle.A4));
                document.save(target.toFile());
            }
        }
        return target;
    }

    private Path eighteenPageReviewPdf() throws Exception
    {
        Path target = tempDir.resolve("eighteen-page-review.pdf");
        if (!Files.exists(target))
        {
            try (PDDocument document = Loader.loadPDF(sampleReviewPdf().toFile()))
            {
                while (document.getNumberOfPages() < 18)
                {
                    document.addPage(new PDPage(PDRectangle.A4));
                }
                document.save(target.toFile());
            }
        }
        return target;
    }

    private Path syntheticHistoricalRepairReviewPdf(OaSignedPdfService service) throws Exception
    {
        Path target = tempDir.resolve("synthetic-historical-repair-review.pdf");
        if (Files.exists(target))
        {
            return target;
        }
        Path base = eighteenPageReviewPdf();
        List<String> oldValues = List.of(
                "甲方代表：____________",
                "签署时间及文件校验信息见本合同末页《电子签署确认页》。",
                "□ 3《员工手册》");
        try (PDDocument document = Loader.loadPDF(base.toFile()))
        {
            PDFont font = ReflectionTestUtils.invokeMethod(service, "loadEvidenceFont",
                    document, oldValues);
            try (PDPageContentStream content = new PDPageContentStream(document,
                    document.getPage(13), PDPageContentStream.AppendMode.APPEND, true, true))
            {
                content.beginText();
                content.setFont(font, 12F);
                content.newLineAtOffset(114.1F, 655F);
                content.showText(oldValues.get(0));
                content.endText();

                content.beginText();
                content.setFont(font, 12F);
                content.newLineAtOffset(114.1F, 615F);
                content.showText(oldValues.get(1));
                content.endText();

                content.beginText();
                content.setFont(font, 12F);
                content.newLineAtOffset(114.1F, 434F);
                content.showText(oldValues.get(2));
                content.endText();
            }
            document.save(target.toFile());
        }
        return target;
    }

    private OaSignPackage signPackage(Long packageId)
    {
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(packageId);
        signPackage.setPackageNo("SP202607110001");
        signPackage.setDocumentVersion("SP-" + packageId + "-V1");
        signPackage.setEmployeeNameSnapshot("Alice");
        signPackage.setEmployeeIdCardSnapshot("TEST-ID-****-1234");
        signPackage.setDeptNameSnapshot("Hangzhou Store Department");
        signPackage.setLegalEntityNameSnapshot("Shanghai Legal Entity");
        return signPackage;
    }

    private OaSignPackageDocument signDocument(Long documentId, Long packageId, Path reviewPdf) throws Exception
    {
        OaSignPackageDocument document = new OaSignPackageDocument();
        document.setDocumentId(documentId);
        document.setPackageId(packageId);
        document.setDocumentName("Employment Contract");
        document.setDocumentVersion("SP-" + packageId + "-V1");
        document.setReviewPdfHash(sha256(reviewPdf));
        return document;
    }

    private byte[] signaturePng() throws Exception
    {
        BufferedImage image = new BufferedImage(320, 120, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try
        {
            graphics.setColor(new Color(255, 255, 255, 0));
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(Color.BLACK);
            graphics.setStroke(new BasicStroke(7f));
            graphics.drawLine(20, 90, 100, 30);
            graphics.drawLine(100, 30, 180, 95);
            graphics.drawLine(180, 95, 300, 25);
        }
        finally
        {
            graphics.dispose();
        }
        return png(image);
    }

    private byte[] sealPng() throws Exception
    {
        BufferedImage image = new BufferedImage(180, 180, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try
        {
            graphics.setColor(new Color(255, 255, 255, 0));
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(Color.RED);
            graphics.setStroke(new BasicStroke(8f));
            graphics.drawOval(12, 12, 156, 156);
            graphics.drawLine(50, 90, 130, 90);
        }
        finally
        {
            graphics.dispose();
        }
        return png(image);
    }

    private byte[] sealJpeg() throws Exception
    {
        BufferedImage image = new BufferedImage(180, 180, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try
        {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(Color.RED);
            graphics.setStroke(new BasicStroke(8f));
            graphics.drawOval(12, 12, 156, 156);
            graphics.drawLine(50, 90, 130, 90);
        }
        finally
        {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, "jpeg", output)).isTrue();
        return output.toByteArray();
    }

    private byte[] sealGif() throws Exception
    {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, "gif", output)).isTrue();
        return output.toByteArray();
    }

    private byte[] png(BufferedImage image) throws Exception
    {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private String sha256(Path path) throws Exception
    {
        return sha256(Files.readAllBytes(path));
    }

    private String sha256(byte[] bytes) throws Exception
    {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private String hashLine(String label, String hash, int part)
    {
        int start = (part - 1) * 32;
        return label + " (" + part + "/2): " + hash.substring(start, start + 32);
    }

    private java.util.List<Path> archiveFiles() throws Exception
    {
        Path root = tempDir.resolve("archive");
        if (!Files.exists(root))
        {
            return java.util.List.of();
        }
        try (java.util.stream.Stream<Path> files = Files.walk(root))
        {
            return files.filter(Files::isRegularFile).toList();
        }
    }
}
