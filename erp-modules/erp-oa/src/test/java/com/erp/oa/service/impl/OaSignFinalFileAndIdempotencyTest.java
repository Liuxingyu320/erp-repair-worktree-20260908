package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.constant.OaSignPackageStatus;
import com.erp.oa.constant.OaSignSigningSequence;
import com.erp.oa.constant.OaSignTemplateType;
import com.erp.oa.config.OaSignFileProperties;
import com.erp.oa.domain.OaSignEvent;
import com.erp.oa.domain.OaSignFinalConfirmation;
import com.erp.oa.domain.OaSignFinalConfirmationDocument;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPackageDocument;
import com.erp.oa.domain.OaSignTemplate;
import com.erp.oa.domain.dto.OaSignFinalConfirmRequest;
import com.erp.oa.domain.dto.OaSignFinalDocumentReadRequest;
import com.erp.oa.domain.vo.OaSignPackageFile;
import com.erp.oa.domain.vo.SignedPdfResult;
import com.erp.oa.mapper.OaSignEventMapper;
import com.erp.oa.mapper.OaSignFinalConfirmationMapper;
import com.erp.oa.mapper.OaSignPackageDocumentMapper;
import com.erp.oa.mapper.OaSignPackageMapper;

@DisplayName("最终合同下载与持久幂等")
class OaSignFinalFileAndIdempotencyTest
{
    private static final String VERSION = "SP-90-F1";
    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);
    private static final String CONFIRMATION_TEXT =
            "本人已阅读并确认最终合同中的公司及印章信息";

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("HR在最终文件发送前后均预览候选PDF")
    void shouldResolveScopedFinalCandidateBeforeAndAfterSend() throws Exception
    {
        for (String status : List.of(OaSignPackageStatus.PENDING_COMPANY,
                OaSignPackageStatus.PENDING_FINAL_CONFIRM))
        {
            Fixture fixture = fixture(status);
            Path candidate = tempDir.resolve(status + "-final.pdf");
            Files.writeString(candidate, "%PDF-1.4 candidate " + status,
                    StandardCharsets.ISO_8859_1);
            OaSignPackageDocument document = finalCandidateDocument(candidate);
            fixture.signPackage.setFinalDocumentRootHash(rootHash(51L, HASH_B));
            when(fixture.documentMapper.selectDocumentsByPackageId(90L))
                    .thenReturn(List.of(document));
            when(fixture.documentMapper.selectOaSignPackageDocumentById(51L))
                    .thenReturn(document);
            when(fixture.documentService.resolveGeneratedSignPackageFile(
                    document.getFinalPdfUrl())).thenReturn(candidate);

            OaSignPackageFile result = fixture.service.resolveScopedFinalDocumentFile(
                    90L, 51L, null);

            assertThat(result.getPath()).isEqualTo(candidate);
            assertThat(result.getFileName()).isEqualTo("劳动合同-最终合同.pdf");
        }
    }

    @Test
    @DisplayName("HR最终文件预览对包状态和确认状态严格白名单")
    void shouldRejectScopedFinalFileOutsideStateWhitelist()
    {
        Object[][] rejectedStates = {
                { OaSignPackageStatus.PENDING_COMPANY, "WAITING_COMPANY" },
                { OaSignPackageStatus.PENDING_FINAL_CONFIRM, "PREPARED_NOT_SENT" },
                { OaSignPackageStatus.SIGNED, "PENDING" },
                { OaSignPackageStatus.PENDING_SIGN, "PENDING" }
        };
        for (Object[] state : rejectedStates)
        {
            Fixture fixture = fixture((String) state[0]);
            fixture.signPackage.setFinalConfirmationStatus((String) state[1]);

            assertThatThrownBy(() -> fixture.service.resolveScopedFinalDocumentFile(
                    90L, 51L, null))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("不存在可预览");

            verify(fixture.documentMapper, never())
                    .selectOaSignPackageDocumentById(any());
        }
    }

    @Test
    @DisplayName("HR最终文件预览要求冻结版本和根hash")
    void shouldRejectScopedFinalFileWithoutFrozenVersionOrRoot()
    {
        Fixture missingVersion = fixture(OaSignPackageStatus.PENDING_COMPANY);
        missingVersion.signPackage.setFinalDocumentVersion(" ");
        Fixture invalidRoot = fixture(OaSignPackageStatus.PENDING_FINAL_CONFIRM);
        invalidRoot.signPackage.setFinalDocumentRootHash("not-a-sha256");

        assertThatThrownBy(() -> missingVersion.service.resolveScopedFinalDocumentFile(
                90L, 51L, null)).isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> invalidRoot.service.resolveScopedFinalDocumentFile(
                90L, 51L, null)).isInstanceOf(ServiceException.class);
        verify(missingVersion.documentMapper, never())
                .selectOaSignPackageDocumentById(any());
        verify(invalidRoot.documentMapper, never())
                .selectOaSignPackageDocumentById(any());
    }

    @Test
    @DisplayName("HR最终文件预览拒绝不属于冻结集合根的候选文件")
    void shouldRejectScopedFinalFileWhenFrozenRootDoesNotMatch() throws Exception
    {
        Fixture fixture = fixture(OaSignPackageStatus.PENDING_COMPANY);
        Path candidate = tempDir.resolve("root-mismatch-final.pdf");
        Files.writeString(candidate, "%PDF-1.4 root mismatch",
                StandardCharsets.ISO_8859_1);
        OaSignPackageDocument document = finalCandidateDocument(candidate);
        fixture.signPackage.setFinalDocumentRootHash(HASH_A);
        when(fixture.documentMapper.selectDocumentsByPackageId(90L))
                .thenReturn(List.of(document));
        when(fixture.documentService.resolveGeneratedSignPackageFile(
                document.getFinalPdfUrl())).thenReturn(candidate);

        assertThatThrownBy(() -> fixture.service.resolveScopedFinalDocumentFile(
                90L, 51L, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("文件集合校验不一致");

        verify(fixture.documentMapper, never())
                .selectOaSignPackageDocumentById(any());
    }

    @Test
    @DisplayName("HR只在已签状态下载最终归档PDF")
    void shouldResolveScopedFinalArchiveOnlyAfterSigned() throws Exception
    {
        Fixture fixture = fixture(OaSignPackageStatus.SIGNED);
        Path candidate = tempDir.resolve("final-candidate.pdf");
        Path archive = tempDir.resolve("final-archive.pdf");
        Files.writeString(candidate, "%PDF-1.4 candidate", StandardCharsets.ISO_8859_1);
        Files.writeString(archive, "%PDF-1.4 archived", StandardCharsets.ISO_8859_1);
        OaSignPackageDocument document = new OaSignPackageDocument();
        document.setDocumentId(51L);
        document.setPackageId(90L);
        document.setDocumentName("劳动合同");
        document.setEmployeeVisible("Y");
        document.setFinalDocumentVersion(VERSION);
        document.setFinalPdfUrl("/profile/final-candidate.pdf");
        document.setFinalPdfHash(sha256(Files.readAllBytes(candidate)));
        document.setFinalContentHash(HASH_B);
        document.setFinalArchivePdfUrl("/profile/final-archive.pdf");
        document.setFinalArchivePdfHash(sha256(Files.readAllBytes(archive)));
        fixture.signPackage.setFinalDocumentRootHash(rootHash(51L, HASH_B));
        when(fixture.documentMapper.selectDocumentsByPackageId(90L))
                .thenReturn(List.of(document));
        when(fixture.documentMapper.selectOaSignPackageDocumentById(51L)).thenReturn(document);
        when(fixture.documentService.resolveGeneratedSignPackageFile(
                document.getFinalPdfUrl())).thenReturn(candidate);
        when(fixture.documentService.resolveGeneratedSignPackageFile(
                document.getFinalArchivePdfUrl())).thenReturn(archive);

        OaSignPackageFile result = fixture.service.resolveScopedFinalDocumentFile(90L, 51L, null);

        assertThat(result.getPath()).isEqualTo(archive);
        assertThat(result.getFileName()).isEqualTo("劳动合同-最终归档.pdf");
    }

    @Test
    @DisplayName("真实已确认PDF导出恢复冻结正文位置且不产生任何签约写入")
    void shouldExportConfirmedArchiveWithoutMutation() throws Exception
    {
        RealExportFixture real = realExportFixture(false, false);
        Fixture fixture = real.fixture();
        OaSignPackageDocument document = real.document();
        String originalArchiveHash = sha256(Files.readAllBytes(real.archivePath()));

        OaSignPackageFile result = fixture.service.resolveScopedFinalDocumentExportFile(
                90L, 51L, null);
        OaSignPackageFile replay = fixture.service.resolveScopedFinalDocumentExportFile(
                90L, 51L, null);

        assertThat(result.getPath()).isNotEqualTo(real.archivePath());
        assertThat(replay.getPath()).isNotEqualTo(real.archivePath());
        assertThat(Files.readAllBytes(replay.getPath()))
                .isEqualTo(Files.readAllBytes(result.getPath()));
        assertThat(result.getFileName()).isEqualTo("SP-90-员工甲-劳动合同-签章展示版.pdf");
        assertThat(result.getContentType()).isEqualTo("application/pdf");
        assertThat(result.isDeleteAfterStreaming()).isTrue();
        assertThat(result.getPath()).isNotEqualTo(real.archivePath());
        assertThat(Files.exists(result.getPath())).isTrue();
        try (org.apache.pdfbox.pdmodel.PDDocument exported =
                org.apache.pdfbox.Loader.loadPDF(result.getPath().toFile()))
        {
            assertThat(exported.getNumberOfPages()).isEqualTo(1);
            assertThat(exported.getDocumentInformation().getCustomMetadataValue(
                    "ERP-Final-Export-Type")).isEqualTo("SIGNED_PLACEMENT_DISPLAY_DERIVATIVE");
            List<float[]> matrices = imagePlacementMatrices(exported.getPage(0));
            assertThat(matrices).hasSize(2);
            assertThat(matrices.get(0)).containsExactly(180F, 0F, 0F, 70F, 72F, 80F);
            assertThat(matrices.get(1)).containsExactly(100F, 0F, 0F, 100F, 330F, 60F);
            String text = normalizedText(exported);
            assertThat(text).doesNotContain("最终合同电子签署确认页",
                    "最终合同集合 SHA-256");
            assertThat(imageCount(exported.getPage(0))).isEqualTo(2);
        }
        verify(fixture.packageMapper, never()).updateOaSignPackage(any());
        verify(fixture.documentMapper, never()).updateOaSignPackageDocument(any());
        verify(fixture.confirmationMapper, never()).insertConfirmation(any());
        verify(fixture.confirmationMapper, never()).insertConfirmationDocument(any());
        assertThat(document.getFinalArchivePdfHash()).isEqualTo(originalArchiveHash);
        assertThat(sha256(Files.readAllBytes(real.archivePath()))).isEqualTo(originalArchiveHash);
        Files.deleteIfExists(result.getPath());
        Files.deleteIfExists(replay.getPath());
    }

    @Test
    @DisplayName("原APPENDED快照经正式服务链恢复真实样本位置且重复并发导出无写入")
    void shouldApplyFrozenTextRepairThroughPackageExport() throws Exception
    {
        RealExportFixture real = historicalSampleExportFixture(false);
        Fixture fixture = real.fixture();
        OaSignPackageDocument document = real.document();
        String signaturePolicyBefore = document.getSignaturePositionJson();
        String sealPolicyBefore = document.getCompanySealPositionJson();
        String archiveHashBefore = sha256(real.archivePath());

        OaSignPackageFile result = fixture.service.resolveScopedFinalDocumentExportFile(
                90L, 51L, null);
        OaSignPackageFile retry = fixture.service.resolveScopedFinalDocumentExportFile(
                90L, 51L, null);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<OaSignPackageFile> firstConcurrent = executor.submit(() ->
                fixture.service.resolveScopedFinalDocumentExportFile(90L, 51L, null));
        Future<OaSignPackageFile> secondConcurrent = executor.submit(() ->
                fixture.service.resolveScopedFinalDocumentExportFile(90L, 51L, null));
        OaSignPackageFile concurrentOne;
        OaSignPackageFile concurrentTwo;
        try
        {
            concurrentOne = firstConcurrent.get(30, TimeUnit.SECONDS);
            concurrentTwo = secondConcurrent.get(30, TimeUnit.SECONDS);
        }
        finally
        {
            executor.shutdownNow();
        }

        try (org.apache.pdfbox.pdmodel.PDDocument exported =
                org.apache.pdfbox.Loader.loadPDF(result.getPath().toFile()))
        {
            assertThat(exported.getNumberOfPages()).isEqualTo(18);
            assertThat(normalizedText(exported)).contains("☑")
                    .doesNotContain("最终合同电子签署确认页");
            assertThat(exported.getDocumentInformation().getCustomMetadataValue(
                    "ERP-Final-Export-Text-Repair-Fields"))
                    .isEqualTo("archiveEvidenceNotice,attachmentHandbookMark,"
                            + "companyLegalRepresentative");
            assertThat(exported.getDocumentInformation().getCustomMetadataValue(
                    "ERP-Final-Export-Historical-Repair-Fields"))
                    .isEqualTo("archiveEvidenceNotice,attachmentHandbookMark,"
                            + "companyLegalRepresentative,companySealPosition,"
                            + "employeeSignaturePositions");
            assertThat(imagePlacementMatrices(exported.getPage(13)))
                    .contains(new float[] {90F, 0F, 0F, 42.3F, 319F, 699F},
                            new float[] {76F, 0F, 0F, 35.8F, 232F, 316F},
                            new float[] {62F, 0F, 0F, 61F, 180F, 688F});
            assertThat(imagePlacementMatrices(exported.getPage(15)))
                    .contains(new float[] {78F, 0F, 0F, 36.7F, 309F, 225F});
            assertThat(imagePlacementMatrices(exported.getPage(17)))
                    .contains(new float[] {45F, 0F, 0F, 16.9F, 447.4F, 221.3F});
            assertGoldenPlacementAnchorBindings(exported);
            assertInsideRenderedTableRightBoundary(exported,
                    historicalGoldenConfirmationSignaturePlacement());
        }
        byte[] expected = Files.readAllBytes(result.getPath());
        assertThat(Files.readAllBytes(retry.getPath())).isEqualTo(expected);
        assertThat(Files.readAllBytes(concurrentOne.getPath())).isEqualTo(expected);
        assertThat(Files.readAllBytes(concurrentTwo.getPath())).isEqualTo(expected);
        assertUnmodifiedGoldenPagesRenderIdentically(real.archivePath(), result.getPath(),
                Set.of(14, 16, 18));
        writeRedactedGoldenQa(result.getPath());
        assertThat(document.getSignaturePositionJson()).isEqualTo(signaturePolicyBefore)
                .isEqualTo(OaSignPlacementPolicyService.APPENDED_CONFIRMATION_PAGE);
        assertThat(document.getCompanySealPositionJson()).isEqualTo(sealPolicyBefore)
                .isEqualTo(OaSignPlacementPolicyService.APPENDED_CONFIRMATION_PAGE);
        assertThat(sha256(real.archivePath())).isEqualTo(archiveHashBefore);
        verify(fixture.packageMapper, never()).updateOaSignPackage(any());
        verify(fixture.documentMapper, never()).updateOaSignPackageDocument(any());
        verify(fixture.confirmationMapper, never()).insertConfirmation(any());
        verify(fixture.confirmationMapper, never()).insertConfirmationDocument(any());
        for (Path path : List.of(result.getPath(), retry.getPath(),
                concurrentOne.getPath(), concurrentTwo.getPath()))
        {
            Files.deleteIfExists(path);
        }
    }

    @Test
    @DisplayName("原APPENDED历史导出对未知模板版本和缺失源PDF指纹失败关闭且不写业务")
    void shouldFailClosedForUnapprovedHistoricalCompatibilityWithoutMutation() throws Exception
    {
        RealExportFixture unknownVersion = historicalSampleExportFixture(false);
        unknownVersion.fixture().signPackage.setPackageNo("UNAPPROVED-HISTORY");
        unknownVersion.document().setTemplateVersionSnapshot("unapproved-history");
        assertThatThrownBy(() -> unknownVersion.fixture().service
                .resolveScopedFinalDocumentExportFile(90L, 51L, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("未纳入正文落位清单");
        assertThatThrownBy(() -> unknownVersion.fixture().service
                .resolveScopedFinalDocumentExportFile(90L, 51L, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("未纳入正文落位清单");
        verify(unknownVersion.fixture().packageMapper, never()).updateOaSignPackage(any());
        verify(unknownVersion.fixture().documentMapper, never())
                .updateOaSignPackageDocument(any());

        RealExportFixture missingHash = historicalSampleExportFixture(false);
        missingHash.document().setReviewPdfHash(null);
        assertThatThrownBy(() -> missingHash.fixture().service
                .resolveScopedFinalDocumentExportFile(90L, 51L, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("源PDF指纹必须是SHA-256");
        verify(missingHash.fixture().packageMapper, never()).updateOaSignPackage(any());
        verify(missingHash.fixture().documentMapper, never())
                .updateOaSignPackageDocument(any());
    }

    @Test
    @DisplayName("正式导出禁止用历史最终候选文件冒充缺失的归档证据")
    void shouldFailClosedWhenHistoricalArchiveEvidenceIsMissing() throws Exception
    {
        Fixture fixture = fixture(OaSignPackageStatus.SIGNED);
        Path candidate = tempDir.resolve("missing-archive-candidate.pdf");
        Files.writeString(candidate, "%PDF-1.4 historical candidate",
                StandardCharsets.ISO_8859_1);
        configureStrictExportFixture(fixture, candidate, null);

        assertThatThrownBy(() -> fixture.service.resolveScopedFinalDocumentExportFile(
                90L, 51L, null))
                .isInstanceOf(ServiceException.class)
                .hasMessage("该历史合同缺少签章位置或证据，暂不能恢复");
        verify(fixture.documentService, never()).resolveGeneratedSignPackageFile(
                "/profile/final-archive.pdf");
    }

    @Test
    @DisplayName("历史法律主体缺少冻结代表人时正式导出失败关闭")
    void shouldFailClosedWhenHistoricalFrozenRepresentativeIsMissing() throws Exception
    {
        RealExportFixture real = historicalSampleExportFixture(false);
        Fixture fixture = real.fixture();
        fixture.signPackage.setPackageNo("MISSING-FROZEN-REPRESENTATIVE");
        fixture.signPackage.setLegalEntityIdSnapshot(301L);
        fixture.signPackage.setLegalRepresentativeSnapshot(null);
        fixture.signPackage.setRecommendedLegalRepresentativeSnapshot("推荐代表");

        assertThatThrownBy(() -> fixture.service.resolveScopedFinalDocumentExportFile(
                90L, 51L, null))
                .isInstanceOf(ServiceException.class)
                .hasMessage("历史合同缺少冻结甲方代表，暂不能恢复");
    }

    @Test
    @DisplayName("历史缺归档时原预览和原下载保留候选兼容，但正式导出失败关闭")
    void shouldKeepLegacyPreviewDownloadFallbackOnlyOutsideFormalExport() throws Exception
    {
        RealExportFixture real = realExportFixture(true, false);
        Fixture fixture = real.fixture();
        OaSignPackageDocument document = real.document();
        document.setFinalArchivePdfUrl(null);
        document.setFinalArchivePdfHash(null);
        SecurityContextHolder.setUserId("960");

        assertThat(fixture.service.resolveMyFinalDocumentFile(90L, 51L).getPath())
                .isEqualTo(real.pendingPath());
        assertThat(fixture.service.resolveMyFinalDocumentPreviewFile(90L, 51L).getPath())
                .isEqualTo(real.pendingPath());
        SecurityContextHolder.setUserId("1");
        assertThat(fixture.service.resolveScopedFinalDocumentFile(90L, 51L, null).getPath())
                .isEqualTo(real.pendingPath());
        assertThatThrownBy(() -> fixture.service.resolveScopedFinalDocumentExportFile(
                90L, 51L, null))
                .isInstanceOf(ServiceException.class)
                .hasMessage("该历史合同缺少签章位置或证据，暂不能恢复");
    }

    @Test
    @DisplayName("员工正式导出复用本人访问边界，跨员工请求在解析文件前失败")
    void shouldEnforceEmployeeOwnershipBeforeFinalExport() throws Exception
    {
        RealExportFixture real = realExportFixture(true, false);
        Fixture fixture = real.fixture();
        SecurityContextHolder.setUserId("960");

        OaSignPackageFile result = fixture.service.resolveMyFinalDocumentExportFile(90L, 51L);
        assertThat(result.getPath()).isNotEqualTo(real.archivePath());
        assertThat(result.isDeleteAfterStreaming()).isTrue();

        SecurityContextHolder.setUserId("961");
        assertThatThrownBy(() -> fixture.service.resolveMyFinalDocumentExportFile(90L, 51L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("只能查看和签署本人的签约包");
    }

    @Test
    @DisplayName("公司先签历史归档导出保持正文稳定并恢复冻结印章位置")
    void shouldExportCompanyFirstHistoricalArchiveWithoutChangingStableBody() throws Exception
    {
        RealExportFixture real = historicalSampleExportFixture(true);
        Fixture fixture = real.fixture();
        String originalArchiveHash = sha256(real.archivePath());

        OaSignPackageFile result = fixture.service.resolveScopedFinalDocumentExportFile(
                90L, 51L, null);
        try (PDDocument exported = Loader.loadPDF(result.getPath().toFile()))
        {
            assertThat(exported.getNumberOfPages()).isEqualTo(18);
            assertThat(normalizedText(exported))
                    .doesNotContain("最终合同电子签署确认页");
        }
        verify(fixture.packageMapper, never()).updateOaSignPackage(any());
        verify(fixture.documentMapper, never()).updateOaSignPackageDocument(any());
        assertThat(sha256(real.archivePath())).isEqualTo(originalArchiveHash);
        Files.deleteIfExists(result.getPath());
    }

    @Test
    @DisplayName("exact-v7常规18页公司先行经正式服务链稳定正文并导出四签一章")
    void shouldCompleteExactV7NormalCompanyFirstFormalExport() throws Exception
    {
        assertExactV7FormalExport(false, OaSignSigningSequence.COMPANY_FIRST, true);
    }

    @Test
    @DisplayName("exact-v7数据库最大23页公司先行经正式服务链动态落位")
    void shouldCompleteExactV7DatabaseMaxCompanyFirstFormalExport() throws Exception
    {
        assertExactV7FormalExport(true, OaSignSigningSequence.COMPANY_FIRST, true);
    }

    @Test
    @DisplayName("exact-v7常规18页签名优先经正式服务链不重复叠签")
    void shouldCompleteExactV7NormalSignatureFirstFormalExport() throws Exception
    {
        assertExactV7FormalExport(false, OaSignSigningSequence.SIGNATURE_FIRST, false);
    }

    @Test
    @DisplayName("exact-v7数据库最大23页签名优先经正式服务链动态落位")
    void shouldCompleteExactV7DatabaseMaxSignatureFirstFormalExport() throws Exception
    {
        assertExactV7FormalExport(true, OaSignSigningSequence.SIGNATURE_FIRST, false);
    }

    @Test
    @DisplayName("exact-v7旧APPENDED公司先行快照无需改库即可续签并正式导出")
    void shouldContinueExactV7AppendedCompanyFirstSnapshotThroughArchiveAndExport()
            throws Exception
    {
        assertExactV7FormalExport(false, OaSignSigningSequence.COMPANY_FIRST, false,
                true);
    }

    @Test
    @DisplayName("exact-v7旧APPENDED数据库最大23页以双PDF一致锚点续签并稳定导出")
    void shouldContinueDatabaseMaxExactV7AppendedSnapshotWithDualPdfEvidence()
            throws Exception
    {
        assertExactV7FormalExport(true, OaSignSigningSequence.COMPANY_FIRST, false,
                true);
    }

    @Test
    @DisplayName("exact-v7正式导出对缺失隐藏或重复员工可见支持文档失败关闭")
    void shouldFailClosedExactV7FormalExportWhenVisibleDocumentSetDrifts()
            throws Exception
    {
        assertExactV7FormalExport(false, OaSignSigningSequence.COMPANY_FIRST,
                false, false, true);
        assertExactV7FormalExport(true, OaSignSigningSequence.COMPANY_FIRST,
                false, false, true);
    }

    @Test
    @DisplayName("多文档正式导出逐份校验归档根且只生成目标文档响应")
    void shouldValidateMultiDocumentArchivesWhileExportingOneDocument() throws Exception
    {
        Fixture fixture = fixture(OaSignPackageStatus.SIGNED);
        OaSignFileStorageService storage = signingStorage();
        OaSignedPdfService realSignedPdfService = new OaSignedPdfService(storage);
        ReflectionTestUtils.setField(fixture.service, "signedPdfService", realSignedPdfService);
        Path review = tempDir.resolve("multi-document-review.pdf");
        try (java.io.InputStream input = getClass().getResourceAsStream("/signing/sample-review.pdf"))
        {
            assertThat(input).isNotNull();
            Files.copy(input, review);
        }

        OaSignPackage signPackage = fixture.signPackage;
        signPackage.setPackageNo("SP-MULTI");
        signPackage.setEmployeeNameSnapshot("员工甲");
        signPackage.setEmployeeIdCardSnapshot("TEST-ID-****-1234");
        signPackage.setDocumentVersion(VERSION);
        signPackage.setFinalDocumentVersion(VERSION);
        Date confirmationTime = new Date(1_700_000_120_000L);
        signPackage.setFinalConfirmedTime(confirmationTime);
        signPackage.setFinalEvidenceGeneratedTime(confirmationTime);

        OaSignPackageDocument first = noMarkDocument(51L, review);
        OaSignPackageDocument second = noMarkDocument(52L, review);
        SignedPdfResult firstPending = realSignedPdfService.generatePendingFinalPdfWithoutMarks(
                null, signPackage, first, review);
        SignedPdfResult secondPending = realSignedPdfService.generatePendingFinalPdfWithoutMarks(
                null, signPackage, second, review);
        Path firstPendingPath = storage.resolveAuthorizedFile(firstPending.getArchiveRelativePath());
        Path secondPendingPath = storage.resolveAuthorizedFile(secondPending.getArchiveRelativePath());
        String finalRoot = sha256(("51:" + firstPending.getContentPdfHash() + "\n"
                + "52:" + secondPending.getContentPdfHash() + "\n")
                .getBytes(StandardCharsets.UTF_8));
        SignedPdfResult firstArchive = realSignedPdfService.archiveConfirmedFinalPdf(
                null, signPackage, first, firstPendingPath, firstPending.getSignedPdfHash(),
                firstPending.getContentPdfHash(), finalRoot, null, null, null, null,
                confirmationTime, null, null);
        SignedPdfResult secondArchive = realSignedPdfService.archiveConfirmedFinalPdf(
                null, signPackage, second, secondPendingPath, secondPending.getSignedPdfHash(),
                secondPending.getContentPdfHash(), finalRoot, null, null, null, null,
                confirmationTime, null, null);
        Path firstArchivePath = storage.resolveAuthorizedFile(firstArchive.getArchiveRelativePath());
        Path secondArchivePath = storage.resolveAuthorizedFile(secondArchive.getArchiveRelativePath());
        first.setFinalPdfUrl("/profile/multi-pending-51.pdf");
        first.setFinalPdfHash(firstPending.getSignedPdfHash());
        first.setFinalContentHash(firstPending.getContentPdfHash());
        first.setFinalArchivePdfUrl("/profile/multi-archive-51.pdf");
        first.setFinalArchivePdfHash(firstArchive.getSignedPdfHash());
        second.setFinalPdfUrl("/profile/multi-pending-52.pdf");
        second.setFinalPdfHash(secondPending.getSignedPdfHash());
        second.setFinalContentHash(secondPending.getContentPdfHash());
        second.setFinalArchivePdfUrl("/profile/multi-archive-52.pdf");
        second.setFinalArchivePdfHash(secondArchive.getSignedPdfHash());
        signPackage.setFinalDocumentRootHash(finalRoot);
        signPackage.setFinalArchiveRootHash(sha256(("51:" + firstArchive.getSignedPdfHash()
                + "\n52:" + secondArchive.getSignedPdfHash() + "\n")
                .getBytes(StandardCharsets.UTF_8)));
        when(fixture.documentService.resolveGeneratedSignPackageFile(
                "/profile/multi-archive-51.pdf")).thenReturn(firstArchivePath);
        when(fixture.documentService.resolveGeneratedSignPackageFile(
                "/profile/multi-archive-52.pdf")).thenReturn(secondArchivePath);
        when(fixture.documentMapper.selectDocumentsByPackageId(90L))
                .thenReturn(List.of(first, second));
        when(fixture.documentMapper.selectOaSignPackageDocumentById(51L)).thenReturn(first);
        OaSignFinalConfirmation confirmation = finalConfirmation(
                90L, 960L, VERSION, finalRoot, CONFIRMATION_TEXT, "LOGIN_TOKEN");
        confirmation.setConfirmedTime(confirmationTime);
        when(fixture.confirmationMapper.selectByPackageAndVersion(90L, VERSION))
                .thenReturn(confirmation);
        when(fixture.confirmationMapper.selectDocumentsByPackageAndVersion(90L, VERSION))
                .thenReturn(confirmationEvidence(confirmation, List.of(first, second)));

        OaSignPackageFile result = fixture.service.resolveScopedFinalDocumentExportFile(
                90L, 51L, null);

        assertThat(result.getPath()).isNotEqualTo(firstArchivePath);
        assertThat(result.isDeleteAfterStreaming()).isTrue();
        assertThat(Files.exists(secondArchivePath)).isTrue();
    }

    @Test
    @DisplayName("最终文件打开请求同ID同不可变载荷返回既有结果")
    void shouldReplayFinalReadWhenImmutablePayloadMatches()
    {
        Fixture fixture = employeeFixture(OaSignPackageStatus.SIGNED);
        OaSignEvent existing = finalReadEvent(90L, 51L, VERSION, HASH_A);
        when(fixture.eventMapper.selectEventByTypeAndRequestId(
                "FINAL_DOCUMENT_OPENED", "read-request-1")).thenReturn(existing);

        OaSignPackage result = fixture.service.confirmFinalDocumentRead(90L, 51L,
                finalReadRequest(VERSION, HASH_A.toUpperCase(), "read-request-1"),
                "127.0.0.1", "test-agent");

        assertThat(result).isNotSameAs(fixture.signPackage);
        assertThat(result.getPackageId()).isEqualTo(fixture.signPackage.getPackageId());
        assertThat(result.getEmployeeId()).isNull();
        verify(fixture.documentMapper, never()).markFinalReadConfirmed(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("正式导出要求确认明细与全部员工可见文件逐份且唯一匹配")
    void shouldRejectMissingDuplicateOrMismatchedConfirmationDocumentEvidence()
            throws Exception
    {
        RealExportFixture real = realExportFixture(false, false);
        Fixture fixture = real.fixture();
        OaSignFinalConfirmation confirmation = fixture.confirmationMapper
                .selectByPackageAndVersion(90L, VERSION);
        List<OaSignFinalConfirmationDocument> correct = confirmationEvidence(
                confirmation, List.of(real.document()));

        when(fixture.confirmationMapper.selectDocumentsByPackageAndVersion(90L, VERSION))
                .thenReturn(List.of());
        assertThatThrownBy(() -> fixture.service.resolveScopedFinalDocumentExportFile(
                90L, 51L, null)).isInstanceOf(ServiceException.class)
                .hasMessageContaining("缺少签章位置或证据");

        when(fixture.confirmationMapper.selectDocumentsByPackageAndVersion(90L, VERSION))
                .thenReturn(List.of(correct.get(0), correct.get(0)));
        assertThatThrownBy(() -> fixture.service.resolveScopedFinalDocumentExportFile(
                90L, 51L, null)).isInstanceOf(ServiceException.class)
                .hasMessageContaining("缺少签章位置或证据");

        OaSignFinalConfirmationDocument wrongHash = confirmationEvidence(
                confirmation, List.of(real.document())).get(0);
        wrongHash.setFinalPdfHash(HASH_A);
        when(fixture.confirmationMapper.selectDocumentsByPackageAndVersion(90L, VERSION))
                .thenReturn(List.of(wrongHash));
        assertThatThrownBy(() -> fixture.service.resolveScopedFinalDocumentExportFile(
                90L, 51L, null)).isInstanceOf(ServiceException.class)
                .hasMessageContaining("缺少签章位置或证据");
    }

    @Test
    @DisplayName("最终文件打开请求同ID异包文件版本或hash均冲突")
    void shouldRejectFinalReadReplayWhenAnyImmutableFieldDiffers()
    {
        Fixture fixture = employeeFixture(OaSignPackageStatus.SIGNED);
        OaSignEvent existing = finalReadEvent(91L, 51L, VERSION, HASH_A);
        when(fixture.eventMapper.selectEventByTypeAndRequestId(
                "FINAL_DOCUMENT_OPENED", "read-request-1")).thenReturn(existing);
        OaSignFinalDocumentReadRequest request = finalReadRequest(
                VERSION, HASH_A, "read-request-1");

        assertReadReplayConflict(fixture, request);
        existing.setPackageId(90L);
        existing.setDocumentId(52L);
        assertReadReplayConflict(fixture, request);
        existing.setDocumentId(51L);
        existing.setEventPayload("version=SP-90-F2;hash=" + HASH_A);
        assertReadReplayConflict(fixture, request);
        existing.setEventPayload("version=" + VERSION + ";hash=" + HASH_B);
        existing.setDocumentHash(HASH_B);
        assertReadReplayConflict(fixture, request);
    }

    @Test
    @DisplayName("最终确认请求同ID同不可变载荷返回既有结果")
    void shouldReplayFinalConfirmationWhenImmutablePayloadMatches()
    {
        Fixture fixture = employeeFixture(OaSignPackageStatus.SIGNED);
        OaSignFinalConfirmation existing = finalConfirmation(
                90L, 960L, VERSION, HASH_A, CONFIRMATION_TEXT, "LOGIN_TOKEN");
        when(fixture.confirmationMapper.selectByRequestId("confirm-request-1"))
                .thenReturn(existing);

        OaSignPackage result = fixture.service.confirmFinalPackage(90L,
                finalConfirmRequest(VERSION, HASH_A.toUpperCase(), "confirm-request-1"),
                "127.0.0.1", "test-agent");

        assertThat(result).isNotSameAs(fixture.signPackage);
        assertThat(result.getPackageId()).isEqualTo(fixture.signPackage.getPackageId());
        assertThat(result.getEmployeeId()).isNull();
        verify(fixture.confirmationMapper, never()).insertConfirmation(any());
    }

    @Test
    @DisplayName("最终确认请求同ID异包员工版本root短语或身份均冲突")
    void shouldRejectFinalConfirmationReplayWhenAnyImmutableFieldDiffers()
    {
        Fixture fixture = employeeFixture(OaSignPackageStatus.SIGNED);
        OaSignFinalConfirmation existing = finalConfirmation(
                91L, 960L, VERSION, HASH_A, CONFIRMATION_TEXT, "LOGIN_TOKEN");
        when(fixture.confirmationMapper.selectByRequestId("confirm-request-1"))
                .thenReturn(existing);
        OaSignFinalConfirmRequest request = finalConfirmRequest(
                VERSION, HASH_A, "confirm-request-1");

        assertFinalConfirmationReplayConflict(fixture, request);
        existing.setPackageId(90L);
        existing.setEmployeeId(961L);
        assertFinalConfirmationReplayConflict(fixture, request);
        existing.setEmployeeId(960L);
        existing.setFinalDocumentVersion("SP-90-F2");
        assertFinalConfirmationReplayConflict(fixture, request);
        existing.setFinalDocumentVersion(VERSION);
        existing.setDocumentRootHash(HASH_B);
        assertFinalConfirmationReplayConflict(fixture, request);
        existing.setDocumentRootHash(HASH_A);
        existing.setConfirmationText("旧确认短语");
        assertFinalConfirmationReplayConflict(fixture, request);
        existing.setConfirmationText(CONFIRMATION_TEXT);
        existing.setIdentityMethod("OTHER");
        assertFinalConfirmationReplayConflict(fixture, request);
    }

    private void assertReadReplayConflict(Fixture fixture,
            OaSignFinalDocumentReadRequest request)
    {
        assertThatThrownBy(() -> fixture.service.confirmFinalDocumentRead(
                90L, 51L, request, "127.0.0.1", "test-agent"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("请求编号已用于不同内容");
    }

    private void assertFinalConfirmationReplayConflict(Fixture fixture,
            OaSignFinalConfirmRequest request)
    {
        assertThatThrownBy(() -> fixture.service.confirmFinalPackage(
                90L, request, "127.0.0.1", "test-agent"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("请求编号已用于不同内容");
    }

    private void assertExactV7FormalExport(boolean databaseMax, String signingSequence,
            boolean writeQaPdf) throws Exception
    {
        assertExactV7FormalExport(databaseMax, signingSequence, writeQaPdf, false);
    }

    private void assertExactV7FormalExport(boolean databaseMax, String signingSequence,
            boolean writeQaPdf, boolean legacyAppended) throws Exception
    {
        assertExactV7FormalExport(databaseMax, signingSequence, writeQaPdf,
                legacyAppended, false);
    }

    private void assertExactV7FormalExport(boolean databaseMax, String signingSequence,
            boolean writeQaPdf, boolean legacyAppended,
            boolean verifyVisibleDocumentSetDrift) throws Exception
    {
        String formalVersion = "SP-90-V7";
        String label = (databaseMax ? "max" : "normal") + "-"
                + signingSequence.toLowerCase(java.util.Locale.ROOT)
                + (legacyAppended ? "-legacy-appended" : "");
        Fixture fixture = fixture(OaSignPackageStatus.SIGNED);
        ExactV7FormalRuntime runtime = exactV7FormalRuntime(label);
        OaSignPackage signPackage = fixture.signPackage;
        configureExactV7FormalPackage(signPackage, databaseMax, signingSequence,
                formalVersion);
        OaSignTemplate template = exactV7FormalTemplate(label);
        byte[] templateSource = Files.readAllBytes(Path.of(template.getFileUrl()));
        String templateSourceHash = sha256(templateSource);

        List<String> actualTemplateTypes = new ArrayList<>(List.of(
                OaSignTemplateType.ONBOARD_COMMITMENT,
                OaSignTemplateType.ONBOARD_LABOR_CONTRACT,
                OaSignTemplateType.ONBOARD_HANDBOOK_RECEIPT,
                OaSignTemplateType.ONBOARD_SALARY_CONFIRM));
        if (databaseMax)
        {
            // Plan 66 contains this extra employee-visible document.  It must not alter
            // the four-item labor-contract attachment checklist.
            actualTemplateTypes.add(OaSignTemplateType.ONBOARD_CONFIDENTIAL_NONCOMPETE);
        }
        GeneratedSignDocument generated = runtime.documentService().renderPackageDocument(
                signPackage, template, actualTemplateTypes);
        Path reviewPdf = runtime.storage().resolveAuthorizedFile(
                generated.getReviewPdfArchiveRelativePath());
        int expectedBodyPages = databaseMax ? 23 : 18;
        try (PDDocument review = Loader.loadPDF(reviewPdf.toFile()))
        {
            assertThat(review.getNumberOfPages()).isEqualTo(expectedBodyPages);
            assertThat(normalizedText(review))
                    .contains("签署完成后可在系统查看签署凭证及文件校验信息。")
                    .doesNotContain("见本合同末页《电子签署确认页》");
            assertCompleteAttachmentChecklist(review);
        }

        OaSignPlacementPolicyService placement = new OaSignPlacementPolicyService();
        OaSignPlacementPolicyService.GeneratedPlacementPolicies policies =
                placement.validateAndFreezeGeneratedPositions(
                        OaSignTemplateType.ONBOARD_LABOR_CONTRACT, "20260721-v7",
                        templateSourceHash,
                        OaSignPlacementPolicyService.APPENDED_CONFIRMATION_PAGE,
                        OaSignPlacementPolicyService.APPENDED_CONFIRMATION_PAGE,
                        reviewPdf, generated.getReviewPdfHash(), true, true,
                        signPackage.getLegalRepresentativeSnapshot(), true, signingSequence);

        OaSignPackageDocument labor = new OaSignPackageDocument();
        labor.setDocumentId(51L);
        labor.setPackageId(90L);
        labor.setDocumentName("合成劳动合同");
        labor.setTemplateType(OaSignTemplateType.ONBOARD_LABOR_CONTRACT);
        labor.setTemplateVersionSnapshot("20260721-v7");
        labor.setSourceFileUrlSnapshot("/profile/exact-v7-source.docx");
        labor.setReviewPdfUrl("/profile/exact-v7-review.pdf");
        labor.setReviewPdfHash(generated.getReviewPdfHash());
        labor.setEmployeeVisible("Y");
        labor.setEmployeeSignRequired("Y");
        labor.setCompanySealRequired("Y");
        labor.setDocumentPolicyMode(OaSignPlacementPolicyService.SNAPSHOT_V1);
        labor.setSignaturePositionJson(legacyAppended
                ? OaSignPlacementPolicyService.APPENDED_CONFIRMATION_PAGE
                : policies.signaturePositionJson());
        labor.setCompanySealPositionJson(legacyAppended
                ? OaSignPlacementPolicyService.APPENDED_CONFIRMATION_PAGE
                : policies.companySealPositionJson());
        labor.setDocumentVersion(formalVersion);
        labor.setFinalDocumentVersion(formalVersion);
        placement.assertSigningSequenceMatches(signPackage, labor);
        if (!legacyAppended)
        {
            assertThat(placement.resolveFinalExportExpectedBodyPageCount(labor))
                    .isEqualTo(expectedBodyPages);
            assertThat(placement.resolveDisplayTextPlacements(labor)).isEmpty();
        }

        OaSignedPdfService.PdfImagePlacement bodySignature =
                placement.resolveSignaturePlacement(labor);
        OaSignedPdfService.PdfImagePlacement bodySeal =
                placement.resolveCompanySealPlacement(labor);
        if (OaSignSigningSequence.COMPANY_FIRST.equals(signingSequence))
        {
            assertThat(bodySignature).isNull();
        }
        else
        {
            assertThat(bodySignature).isNotNull();
            assertThat(bodySignature.expanded()).hasSize(4);
        }
        if (legacyAppended)
        {
            assertThat(bodySeal).isNull();
        }
        else
        {
            assertThat(bodySeal.expanded()).hasSize(1)
                    .extracting(OaSignedPdfService.PdfImagePlacement::getPageNumber)
                    .containsExactly(databaseMax ? 17 : 14);
            assertNoProtectedRegionIntersection(bodySignature == null
                    ? placement.resolveFinalExportSignaturePlacement(labor)
                    : bodySignature);
            assertNoProtectedRegionIntersection(bodySeal);
        }

        byte[] signature = signaturePng();
        byte[] seal = sealPng();
        Path signaturePath = tempDir.resolve("exact-v7-signature-" + label + ".png");
        Files.write(signaturePath, signature);
        Date employeeTime = Date.from(Instant.parse(OaSignSigningSequence.COMPANY_FIRST
                .equals(signingSequence) ? "2026-08-09T01:05:00Z" : "2026-08-09T01:00:00Z"));
        Date companyTime = Date.from(Instant.parse(OaSignSigningSequence.COMPANY_FIRST
                .equals(signingSequence) ? "2026-08-09T01:00:00Z" : "2026-08-09T01:05:00Z"));
        Date confirmationTime = Date.from(Instant.parse("2026-08-09T01:10:00Z"));
        OaSignedPdfService signedPdfService = new OaSignedPdfService(runtime.storage());
        ReflectionTestUtils.setField(fixture.service, "signedPdfService", signedPdfService);

        SignedPdfResult employeeSigned = signedPdfService.generateSignedPdf(null,
                signPackage, labor, reviewPdf, signature, null, employeeTime, null,
                "合成员工签署确认", bodySignature, null);
        Path employeeSignedPath = runtime.storage().resolveAuthorizedFile(
                employeeSigned.getArchiveRelativePath());
        try (PDDocument employeeArtifact = Loader.loadPDF(employeeSignedPath.toFile()))
        {
            assertThat(employeeArtifact.getNumberOfPages())
                    .isEqualTo(OaSignSigningSequence.COMPANY_FIRST.equals(signingSequence)
                            ? expectedBodyPages + 1 : expectedBodyPages);
        }

        SignedPdfResult laborPending;
        if (OaSignSigningSequence.COMPANY_FIRST.equals(signingSequence))
        {
            laborPending = signedPdfService.generatePendingFinalPdf(null, signPackage, labor,
                    reviewPdf, null, seal, null, companyTime,
                    "冻结公司与印章的合成待签合同", null, bodySeal);
        }
        else
        {
            laborPending = signedPdfService.generatePendingFinalPdf(null, signPackage, labor,
                    reviewPdf, signature, seal, employeeTime, companyTime,
                    "合成签名优先最终候选", bodySignature, bodySeal);
        }
        Path laborPendingPath = runtime.storage().resolveAuthorizedFile(
                laborPending.getArchiveRelativePath());
        byte[] stableCandidateBytes = Files.readAllBytes(laborPendingPath);

        Path supportReview = tempDir.resolve("exact-v7-support-" + label + ".pdf");
        try (java.io.InputStream input = getClass()
                .getResourceAsStream("/signing/sample-review.pdf"))
        {
            assertThat(input).isNotNull();
            Files.copy(input, supportReview, StandardCopyOption.REPLACE_EXISTING);
        }
        Map<String, String> supportNames = Map.of(
                OaSignTemplateType.ONBOARD_COMMITMENT, "合成入职承诺书",
                OaSignTemplateType.ONBOARD_HANDBOOK_RECEIPT, "合成员工手册签收确认书",
                OaSignTemplateType.ONBOARD_SALARY_CONFIRM, "合成薪酬结构确认书",
                OaSignTemplateType.ONBOARD_CONFIDENTIAL_NONCOMPETE, "合成保密与竞业限制协议");
        List<OaSignPackageDocument> supportDocuments = new ArrayList<>();
        Map<Long, SignedPdfResult> supportPending = new LinkedHashMap<>();
        Map<Long, Path> supportPendingPaths = new LinkedHashMap<>();
        long supportDocumentId = 52L;
        for (String templateType : actualTemplateTypes)
        {
            if (OaSignTemplateType.ONBOARD_LABOR_CONTRACT.equals(templateType))
            {
                continue;
            }
            OaSignPackageDocument support = noMarkDocument(supportDocumentId++, supportReview);
            support.setDocumentName(supportNames.get(templateType));
            support.setTemplateType(templateType);
            support.setTemplateVersionSnapshot("20260721-v7");
            support.setDocumentVersion(formalVersion);
            support.setFinalDocumentVersion(formalVersion);
            SignedPdfResult pending = signedPdfService.generatePendingFinalPdfWithoutMarks(
                    null, signPackage, support, supportReview);
            supportDocuments.add(support);
            supportPending.put(support.getDocumentId(), pending);
            supportPendingPaths.put(support.getDocumentId(),
                    runtime.storage().resolveAuthorizedFile(pending.getArchiveRelativePath()));
        }

        Map<Long, String> contentHashes = new LinkedHashMap<>();
        contentHashes.put(labor.getDocumentId(), laborPending.getContentPdfHash());
        supportDocuments.forEach(support -> contentHashes.put(support.getDocumentId(),
                supportPending.get(support.getDocumentId()).getContentPdfHash()));
        String finalRoot = rootHash(contentHashes);
        signPackage.setFinalDocumentRootHash(finalRoot);
        SignedPdfResult laborArchive = signedPdfService.archiveConfirmedFinalPdf(null,
                signPackage, labor, laborPendingPath, laborPending.getSignedPdfHash(),
                laborPending.getContentPdfHash(), finalRoot, signature, seal, employeeTime,
                companyTime, confirmationTime, bodySignature, bodySeal);
        Path laborArchivePath = runtime.storage().resolveAuthorizedFile(
                laborArchive.getArchiveRelativePath());
        Map<Long, Path> supportArchivePaths = new LinkedHashMap<>();
        for (OaSignPackageDocument support : supportDocuments)
        {
            SignedPdfResult pending = supportPending.get(support.getDocumentId());
            SignedPdfResult archive = signedPdfService.archiveConfirmedFinalPdf(null,
                    signPackage, support, supportPendingPaths.get(support.getDocumentId()),
                    pending.getSignedPdfHash(), pending.getContentPdfHash(), finalRoot,
                    null, null, null, null, confirmationTime, null, null);
            support.setFinalPdfUrl("/profile/exact-v7-support-"
                    + support.getDocumentId() + "-pending-" + label + ".pdf");
            support.setFinalPdfHash(pending.getSignedPdfHash());
            support.setFinalContentHash(pending.getContentPdfHash());
            support.setFinalArchivePdfUrl("/profile/exact-v7-support-"
                    + support.getDocumentId() + "-archive-" + label + ".pdf");
            support.setFinalArchivePdfHash(archive.getSignedPdfHash());
            supportArchivePaths.put(support.getDocumentId(),
                    runtime.storage().resolveAuthorizedFile(archive.getArchiveRelativePath()));
        }
        String immutableArchiveHash = sha256(laborArchivePath);

        labor.setFinalPdfUrl("/profile/exact-v7-pending-" + label + ".pdf");
        labor.setFinalPdfHash(laborPending.getSignedPdfHash());
        labor.setFinalContentHash(laborPending.getContentPdfHash());
        labor.setFinalArchivePdfUrl("/profile/exact-v7-archive-" + label + ".pdf");
        labor.setFinalArchivePdfHash(laborArchive.getSignedPdfHash());
        Map<Long, String> archiveHashes = new LinkedHashMap<>();
        archiveHashes.put(labor.getDocumentId(), labor.getFinalArchivePdfHash());
        supportDocuments.forEach(support -> archiveHashes.put(support.getDocumentId(),
                support.getFinalArchivePdfHash()));
        signPackage.setFinalArchiveRootHash(rootHash(archiveHashes));
        signPackage.setSignatureSampleFileUrl("/profile/exact-v7-signature-" + label + ".png");
        signPackage.setSignatureSampleHash(sha256(signature));
        signPackage.setSignatureSampleTime(employeeTime);
        signPackage.setInitialSignedTime(employeeTime);
        signPackage.setSignedTime(employeeTime);
        signPackage.setSealImageUrlSnapshot("/profile/exact-v7-seal-" + label + ".png");
        signPackage.setSealImageHashSnapshot(sha256(seal));
        signPackage.setCompanyFrozenTime(companyTime);
        signPackage.setFinalGeneratedTime(companyTime);
        signPackage.setFinalConfirmedTime(confirmationTime);
        signPackage.setFinalEvidenceGeneratedTime(confirmationTime);

        OaSignPlacementPolicyService.HistoricalExportPolicy historicalPolicy =
                legacyAppended ? placement.resolveHistoricalExportPolicy(labor, signPackage,
                        templateSourceHash, reviewPdf, laborArchivePath,
                        labor.getFinalArchivePdfHash()) : null;
        if (legacyAppended)
        {
            assertThat(historicalPolicy.profileId())
                    .isEqualTo(OaSignLaborAnchorPlacementResolver.PROFILE_ID);
            assertThat(historicalPolicy.expectedBodyPageCount()).isEqualTo(expectedBodyPages);
            assertThat(sha256(reviewPdf)).isEqualTo(labor.getReviewPdfHash());
            assertThat(sha256(laborArchivePath)).isEqualTo(labor.getFinalArchivePdfHash());
        }
        OaSignedPdfService.PdfImagePlacement exportSignature =
                placement.resolveFinalExportSignaturePlacement(labor, historicalPolicy);
        OaSignedPdfService.PdfImagePlacement exportSeal =
                placement.resolveFinalExportCompanySealPlacement(labor, historicalPolicy);
        assertThat(exportSignature.expanded()).hasSize(4)
                .extracting(OaSignedPdfService.PdfImagePlacement::getPageNumber)
                .containsExactlyElementsOf(databaseMax
                        ? List.of(17, 18, 21, 23) : List.of(14, 14, 16, 18));
        assertThat(exportSeal.expanded()).hasSize(1)
                .extracting(OaSignedPdfService.PdfImagePlacement::getPageNumber)
                .containsExactly(databaseMax ? 17 : 14);
        assertNoProtectedRegionIntersection(exportSignature);
        assertNoProtectedRegionIntersection(exportSeal);

        when(fixture.documentService.readConfiguredFileBytes(
                "/profile/exact-v7-source.docx")).thenReturn(templateSource);
        when(fixture.documentService.readConfiguredFileBytes(
                signPackage.getSealImageUrlSnapshot())).thenReturn(seal);
        when(fixture.documentService.resolveGeneratedSignPackageFile(
                labor.getReviewPdfUrl())).thenReturn(reviewPdf);
        when(fixture.documentService.resolveGeneratedSignPackageFile(
                labor.getFinalArchivePdfUrl())).thenReturn(laborArchivePath);
        for (OaSignPackageDocument support : supportDocuments)
        {
            when(fixture.documentService.resolveGeneratedSignPackageFile(
                    support.getFinalArchivePdfUrl()))
                    .thenReturn(supportArchivePaths.get(support.getDocumentId()));
        }
        when(fixture.documentService.resolveGeneratedSignPackageFile(
                signPackage.getSignatureSampleFileUrl())).thenReturn(signaturePath);
        List<OaSignPackageDocument> visibleDocuments = new ArrayList<>();
        visibleDocuments.add(labor);
        visibleDocuments.addAll(supportDocuments);
        when(fixture.documentMapper.selectDocumentsByPackageId(90L))
                .thenReturn(visibleDocuments);
        when(fixture.documentMapper.selectOaSignPackageDocumentById(51L)).thenReturn(labor);
        OaSignFinalConfirmation confirmation = finalConfirmation(90L, 960L, formalVersion,
                finalRoot, CONFIRMATION_TEXT, "LOGIN_TOKEN");
        confirmation.setConfirmedTime(confirmationTime);
        when(fixture.confirmationMapper.selectByPackageAndVersion(90L, formalVersion))
                .thenReturn(confirmation);
        List<OaSignFinalConfirmationDocument> confirmationEvidence =
                confirmationEvidence(confirmation, visibleDocuments);
        assertThat(confirmationEvidence).hasSameSizeAs(visibleDocuments)
                .extracting(OaSignFinalConfirmationDocument::getDocumentId)
                .containsExactlyElementsOf(visibleDocuments.stream()
                        .map(OaSignPackageDocument::getDocumentId).toList());
        when(fixture.confirmationMapper.selectDocumentsByPackageAndVersion(
                90L, formalVersion)).thenReturn(confirmationEvidence);

        if (verifyVisibleDocumentSetDrift)
        {
            assertFormalExportRejectsVisibleDocumentSetDrift(fixture, signPackage, labor,
                    visibleDocuments);
            when(fixture.documentMapper.selectDocumentsByPackageId(90L))
                    .thenReturn(visibleDocuments);
        }

        List<OaSignPackageFile> exports = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try
        {
            exports.add(fixture.service.dryRunConfirmedFinalDocumentExport(
                    signPackage, labor));
            exports.add(fixture.service.resolveScopedFinalDocumentExportFile(
                    90L, 51L, null));
            exports.add(fixture.service.resolveScopedFinalDocumentExportFile(
                    90L, 51L, null));
            Future<OaSignPackageFile> first = executor.submit(() ->
                    fixture.service.resolveScopedFinalDocumentExportFile(90L, 51L, null));
            Future<OaSignPackageFile> second = executor.submit(() ->
                    fixture.service.resolveScopedFinalDocumentExportFile(90L, 51L, null));
            exports.add(first.get(60, TimeUnit.SECONDS));
            exports.add(second.get(60, TimeUnit.SECONDS));

            byte[] expectedExport = Files.readAllBytes(exports.get(0).getPath());
            for (OaSignPackageFile export : exports)
            {
                assertThat(Files.readAllBytes(export.getPath())).isEqualTo(expectedExport);
                assertThat(export.isDeleteAfterStreaming()).isTrue();
            }
            assertThat(Files.readAllBytes(laborPendingPath)).isEqualTo(stableCandidateBytes);
            assertThat(sha256(laborArchivePath)).isEqualTo(immutableArchiveHash);
            try (PDDocument archived = Loader.loadPDF(laborArchivePath.toFile());
                    PDDocument exported = Loader.loadPDF(exports.get(0).getPath().toFile()))
            {
                assertThat(archived.getNumberOfPages()).isEqualTo(expectedBodyPages + 1);
                assertThat(exported.getNumberOfPages()).isEqualTo(expectedBodyPages);
                assertThat(normalizedText(exported))
                        .contains("签署完成后可在系统查看签署凭证及文件校验信息。")
                        .doesNotContain("最终合同电子签署确认页",
                                "见本合同末页《电子签署确认页》");
                assertCompleteAttachmentChecklist(exported);
                for (OaSignedPdfService.PdfImagePlacement signaturePlacement
                        : exportSignature.expanded())
                {
                    assertMatrixOccurrences(exported, signaturePlacement, 1);
                    assertMatrixOccurrences(archived, signaturePlacement,
                            OaSignSigningSequence.COMPANY_FIRST.equals(signingSequence) ? 0 : 1);
                }
                assertInsideRenderedTableRightBoundary(exported,
                        exportSignature.expanded().get(3));
                OaSignedPdfService.PdfImagePlacement sealPlacement = exportSeal.expanded().get(0);
                assertMatrixOccurrences(exported, sealPlacement, 1);
                assertMatrixOccurrences(archived, sealPlacement, legacyAppended ? 0 : 1);
            }
            if (writeQaPdf)
            {
                Path qaPdf = Path.of(System.getProperty("user.dir"))
                        .resolve("../../output/pdf/")
                        .resolve(databaseMax
                                ? "erp-rev06-exact-v7-database-max-company-first-display.pdf"
                                : "erp-rev06-exact-v7-formal-company-first-display.pdf")
                        .normalize().toAbsolutePath();
                Files.createDirectories(qaPdf.getParent());
                Files.copy(exports.get(0).getPath(), qaPdf,
                        StandardCopyOption.REPLACE_EXISTING);
                System.out.println("REV06_FORMAL_SYNTHETIC_QA_PDF=" + qaPdf);
            }
            verify(fixture.packageMapper, never()).updateOaSignPackage(any());
            verify(fixture.documentMapper, never()).updateOaSignPackageDocument(any());
            verify(fixture.confirmationMapper, never()).insertConfirmation(any());
            verify(fixture.confirmationMapper, never()).insertConfirmationDocument(any());
            verify(fixture.eventMapper, never()).insertOaSignEvent(any());
        }
        finally
        {
            executor.shutdownNow();
            for (OaSignPackageFile export : exports)
            {
                Files.deleteIfExists(export.getPath());
            }
        }
    }

    private void assertFormalExportRejectsVisibleDocumentSetDrift(Fixture fixture,
            OaSignPackage signPackage, OaSignPackageDocument labor,
            List<OaSignPackageDocument> visibleDocuments)
    {
        List<OaSignPackageDocument> missingSalary = visibleDocuments.stream()
                .filter(value -> !OaSignTemplateType.ONBOARD_SALARY_CONFIRM.equals(
                        value.getTemplateType()))
                .toList();
        assertFormalExportDocumentSetRejected(fixture, signPackage, labor, missingSalary);

        for (String hiddenType : List.of(
                OaSignTemplateType.ONBOARD_HANDBOOK_RECEIPT,
                OaSignTemplateType.ONBOARD_SALARY_CONFIRM))
        {
            List<OaSignPackageDocument> hidden = visibleDocuments.stream()
                    .map(value -> hiddenType.equals(value.getTemplateType())
                            ? visibilityOnlyCopy(value, "N") : value)
                    .toList();
            assertFormalExportDocumentSetRejected(fixture, signPackage, labor, hidden);
        }

        long duplicateId = 8_800L;
        for (String duplicatedType : visibleDocuments.stream()
                .map(OaSignPackageDocument::getTemplateType).distinct().toList())
        {
            List<OaSignPackageDocument> duplicated = new ArrayList<>(visibleDocuments);
            duplicated.add(visibleTypeOnlyDocument(duplicateId++, duplicatedType));
            assertFormalExportDocumentSetRejected(fixture, signPackage, labor,
                    List.copyOf(duplicated));
        }
    }

    private void assertFormalExportDocumentSetRejected(Fixture fixture,
            OaSignPackage signPackage, OaSignPackageDocument labor,
            List<OaSignPackageDocument> driftedDocuments)
    {
        when(fixture.documentMapper.selectDocumentsByPackageId(90L))
                .thenReturn(driftedDocuments);
        assertThatThrownBy(() -> fixture.service.dryRunConfirmedFinalDocumentExport(
                signPackage, labor))
                .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> fixture.service.resolveScopedFinalDocumentExportFile(
                90L, 51L, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("员工可见文档集合");
    }

    private OaSignPackageDocument visibilityOnlyCopy(OaSignPackageDocument source,
            String employeeVisible)
    {
        OaSignPackageDocument copy = visibleTypeOnlyDocument(source.getDocumentId(),
                source.getTemplateType());
        copy.setEmployeeVisible(employeeVisible);
        return copy;
    }

    private OaSignPackageDocument visibleTypeOnlyDocument(Long documentId,
            String templateType)
    {
        OaSignPackageDocument document = new OaSignPackageDocument();
        document.setDocumentId(documentId);
        document.setPackageId(90L);
        document.setTemplateType(templateType);
        document.setEmployeeVisible("Y");
        return document;
    }

    private ExactV7FormalRuntime exactV7FormalRuntime(String label)
    {
        OaSignFileProperties properties = new OaSignFileProperties();
        properties.getStorage().setRootPath(
                tempDir.resolve("exact-v7-formal-" + label + "-archive").toString());
        properties.getStorage().setTempPath(
                tempDir.resolve("exact-v7-formal-" + label + "-staging").toString());
        properties.getStorage().setPublicPrefix("/profile/private/sign-package");
        properties.getPdf().setConverterCommand(resolveOfficeCommand());
        properties.getPdf().setTimeoutSeconds(180);
        OaSignFileStorageService storage = new OaSignFileStorageService(properties);
        OaSignDocumentService documentService = new OaSignDocumentService();
        ReflectionTestUtils.setField(documentService, "localFilePath", tempDir.toString());
        ReflectionTestUtils.setField(documentService, "localFilePrefix", "/profile");
        ReflectionTestUtils.setField(documentService, "fileStorageService", storage);
        ReflectionTestUtils.setField(documentService, "officePdfConverter",
                new OaOfficePdfConverter(properties));
        ReflectionTestUtils.setField(documentService, "pdfPageNumberService",
                new OaPdfPageNumberService());
        return new ExactV7FormalRuntime(documentService, storage);
    }

    private OaSignTemplate exactV7FormalTemplate(String label) throws Exception
    {
        Path source = tempDir.resolve("exact-v7-formal-source-" + label + ".docx");
        try (java.io.InputStream input = getClass().getResourceAsStream(
                "/oa/sign/templates/onboard-20260721-v7/09_ONBOARD_LABOR_CONTRACT.docx"))
        {
            assertThat(input).as("publishable exact-v7 template asset").isNotNull();
            Files.copy(input, source, StandardCopyOption.REPLACE_EXISTING);
        }
        assertThat(sha256(source)).isEqualTo(
                "1226728ec0e703d513efda83dc5578ee4cdd65cb3e7d0e7cb5afee813f466558");
        OaSignTemplate template = new OaSignTemplate();
        template.setTemplateId(2026072109L);
        template.setTemplateName("合成劳动合同");
        template.setTemplateType(OaSignTemplateType.ONBOARD_LABOR_CONTRACT);
        template.setTemplateVersion("20260721-v7");
        template.setFileUrl(source.toString());
        template.setFileHash(sha256(source));
        return template;
    }

    private void configureExactV7FormalPackage(OaSignPackage value, boolean databaseMax,
            String signingSequence, String formalVersion)
    {
        value.setPackageNo(databaseMax ? "SP-SYNTHETIC-MAX" : "SP-SYNTHETIC-NORMAL");
        value.setDocumentVersion(formalVersion);
        value.setFinalDocumentVersion(formalVersion);
        value.setSigningSequence(signingSequence);
        value.setScenario("onboard");
        value.setEmploymentType("劳动合同");
        value.setEmployeeNameSnapshot(databaseMax
                ? fixedWidth("合成长姓名", 64) : "合成员工甲");
        value.setEmployeePhoneSnapshot(databaseMax ? "1".repeat(32) : "18800000000");
        value.setEmployeeIdCardSnapshot(databaseMax
                ? "9".repeat(32) : "TEST-ID-****-0000");
        value.setEmployeeAddressSnapshot(databaseMax
                ? fixedWidth("合成长送达地址", 200) : "合成测试地址");
        value.setLegalEntityIdSnapshot(999L);
        value.setLegalEntityNameSnapshot(databaseMax
                ? fixedWidth("合成长法律主体", 160) : "合成法律主体有限公司");
        value.setLegalEntityAddressSnapshot(databaseMax
                ? fixedWidth("合成长注册地址", 200) : "合成注册地址");
        value.setLegalRepresentativeSnapshot(databaseMax
                ? fixedWidth("合成长代表", 64) : "合成代表甲");
        value.setSealIdSnapshot(999L);
        value.setSealNameSnapshot("合成企业章");
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
        value.setPerformanceSalary(new BigDecimal(
                databaseMax ? "99999999999999.99" : "0.00"));
        value.setSalaryTotal(new BigDecimal(databaseMax ? "99999999999999.99" : "6000.00"));
    }

    private String resolveOfficeCommand()
    {
        String configured = System.getProperty("sign.test.office.command");
        if (configured == null || configured.isBlank())
        {
            configured = System.getenv("SIGN_TEST_OFFICE_COMMAND");
        }
        if (configured != null && !configured.isBlank())
        {
            Path executable = Path.of(configured).toAbsolutePath().normalize();
            org.junit.jupiter.api.Assumptions.assumeTrue(Files.isExecutable(executable),
                    "exact-v7正式链未执行：SIGN_TEST_OFFICE_COMMAND不可执行");
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
                "exact-v7正式链未执行：请在PATH提供soffice/libreoffice，"
                        + "或设置SIGN_TEST_OFFICE_COMMAND");
        return "soffice";
    }

    private String fixedWidth(String seed, int width)
    {
        return seed.repeat((width / seed.length()) + 1).substring(0, width);
    }

    private void assertNoProtectedRegionIntersection(
            OaSignedPdfService.PdfImagePlacement placement)
    {
        for (OaSignedPdfService.PdfImagePlacement current : placement.expanded())
        {
            for (OaSignedPdfService.PdfProtectedRegion protectedRegion
                    : current.getProtectedRegions())
            {
                if (current.getPageNumber() != null
                        && current.getPageNumber() == protectedRegion.pageNumber())
                {
                    boolean intersects = current.getX() < protectedRegion.x()
                            + protectedRegion.width()
                            && current.getX() + current.getWidth() > protectedRegion.x()
                            && current.getY() < protectedRegion.y()
                            + protectedRegion.height()
                            && current.getY() + current.getHeight() > protectedRegion.y();
                    assertThat(intersects).as("signature/seal does not overlap protected text")
                            .isFalse();
                }
            }
        }
    }

    private void assertMatrixOccurrences(PDDocument document,
            OaSignedPdfService.PdfImagePlacement placement, long expected) throws Exception
    {
        float[] matrix = {placement.getWidth(), 0F, 0F, placement.getHeight(),
                placement.getX(), placement.getY()};
        long actual = imagePlacementMatrices(
                document.getPage(placement.getPageNumber() - 1)).stream()
                .filter(candidate -> sameMatrix(candidate, matrix)).count();
        assertThat(actual).as("matrix occurrences on page %s", placement.getPageNumber())
                .isEqualTo(expected);
    }

    private boolean sameMatrix(float[] left, float[] right)
    {
        if (left.length != right.length)
        {
            return false;
        }
        for (int index = 0; index < left.length; index++)
        {
            if (Math.abs(left[index] - right[index]) > 0.01F)
            {
                return false;
            }
        }
        return true;
    }

    private RealExportFixture realExportFixture(boolean bodyMarks, boolean companyFirst)
            throws Exception
    {
        Fixture fixture = fixture(OaSignPackageStatus.SIGNED);
        OaSignFileStorageService storage = signingStorage();
        OaSignedPdfService realSignedPdfService = new OaSignedPdfService(storage);
        ReflectionTestUtils.setField(fixture.service, "signedPdfService", realSignedPdfService);

        Path review = tempDir.resolve("real-export-review-" + bodyMarks + "-" + companyFirst + ".pdf");
        try (java.io.InputStream input = getClass().getResourceAsStream("/signing/sample-review.pdf"))
        {
            assertThat(input).isNotNull();
            Files.copy(input, review);
        }
        byte[] signature = signaturePng();
        byte[] seal = sealPng();
        Path signaturePath = tempDir.resolve("real-export-signature.png");
        Path sealPath = tempDir.resolve("real-export-seal.png");
        Files.write(signaturePath, signature);
        Files.write(sealPath, seal);

        Date employeeTime = new Date(1_700_000_000_000L);
        Date companyTime = new Date(1_700_000_060_000L);
        Date confirmationTime = new Date(1_700_000_120_000L);
        OaSignPackage signPackage = fixture.signPackage;
        signPackage.setPackageNo("SP-90");
        signPackage.setEmployeeNameSnapshot("员工甲");
        signPackage.setEmployeeIdCardSnapshot("TEST-ID-****-1234");
        signPackage.setLegalEntityIdSnapshot(301L);
        signPackage.setLegalEntityNameSnapshot("冻结主体甲公司");
        signPackage.setLegalRepresentativeSnapshot("合成代表甲");
        signPackage.setDocumentVersion(VERSION);
        signPackage.setFinalDocumentVersion(VERSION);
        signPackage.setSigningSequence(companyFirst
                ? OaSignSigningSequence.COMPANY_FIRST : OaSignSigningSequence.SIGNATURE_FIRST);
        signPackage.setSignatureSampleFileUrl("/profile/real-export-signature.png");
        signPackage.setSignatureSampleHash(sha256(signature));
        signPackage.setSignatureSampleTime(employeeTime);
        signPackage.setSealImageUrlSnapshot("/profile/real-export-seal.png");
        signPackage.setSealImageHashSnapshot(sha256(seal));
        signPackage.setFinalGeneratedTime(companyTime);
        signPackage.setFinalConfirmedTime(confirmationTime);
        signPackage.setFinalEvidenceGeneratedTime(confirmationTime);

        OaSignPackageDocument document = new OaSignPackageDocument();
        document.setDocumentId(51L);
        document.setPackageId(90L);
        document.setDocumentName("劳动合同");
        document.setTemplateType("ONBOARD_LABOR_CONTRACT");
        document.setTemplateVersionSnapshot("V3");
        document.setEmployeeVisible("Y");
        document.setEmployeeSignRequired("Y");
        document.setCompanySealRequired("Y");
        document.setDocumentPolicyMode(OaSignPlacementPolicyService.SNAPSHOT_V1);
        document.setSignaturePositionJson(companyFirst
                ? OaSignPlacementPolicyService.APPENDED_CONFIRMATION_PAGE
                : placementJson(1, 72, 80, 180, 70));
        document.setCompanySealPositionJson(placementJson(1, 330, 60, 100, 100));
        document.setDocumentVersion(VERSION);
        document.setFinalDocumentVersion(VERSION);
        document.setReviewPdfHash(sha256(review));

        OaSignPackageDocument sourceDocument = document;
        OaSignedPdfService.PdfImagePlacement signaturePlacement = companyFirst ? null
                : new OaSignedPdfService.PdfImagePlacement(1, 72, 80, 180, 70);
        OaSignedPdfService.PdfImagePlacement sealPlacement =
                new OaSignedPdfService.PdfImagePlacement(1, 330, 60, 100, 100);
        SignedPdfResult pending = realSignedPdfService.generatePendingFinalPdf(
                null, signPackage, sourceDocument, review, signature, seal, employeeTime,
                companyTime, "本人已确认最终合同", bodyMarks ? signaturePlacement : null,
                bodyMarks ? sealPlacement : null);
        Path pendingPath = storage.resolveAuthorizedFile(pending.getArchiveRelativePath());
        document.setFinalPdfUrl("/profile/real-export-pending.pdf");
        document.setFinalPdfHash(pending.getSignedPdfHash());
        document.setFinalContentHash(pending.getContentPdfHash());
        signPackage.setFinalDocumentRootHash(rootHash(51L, pending.getContentPdfHash()));

        SignedPdfResult archive = realSignedPdfService.archiveConfirmedFinalPdf(
                null, signPackage, document, pendingPath, pending.getSignedPdfHash(),
                pending.getContentPdfHash(), signPackage.getFinalDocumentRootHash(),
                signature, seal, employeeTime, companyTime, confirmationTime,
                signaturePlacement, sealPlacement);
        Path archivePath = storage.resolveAuthorizedFile(archive.getArchiveRelativePath());
        document.setFinalArchivePdfUrl("/profile/real-export-archive.pdf");
        document.setFinalArchivePdfHash(archive.getSignedPdfHash());
        signPackage.setFinalArchiveRootHash(rootHash(51L, archive.getSignedPdfHash()));

        when(fixture.documentService.resolveGeneratedSignPackageFile(
                "/profile/real-export-signature.png")).thenReturn(signaturePath);
        when(fixture.documentService.resolveGeneratedSignPackageFile(
                "/profile/real-export-pending.pdf")).thenReturn(pendingPath);
        when(fixture.documentService.resolveGeneratedSignPackageFile(
                "/profile/real-export-archive.pdf")).thenReturn(archivePath);
        when(fixture.documentService.readConfiguredFileBytes(
                "/profile/real-export-seal.png")).thenReturn(seal);
        when(fixture.documentMapper.selectDocumentsByPackageId(90L))
                .thenReturn(List.of(document));
        when(fixture.documentMapper.selectOaSignPackageDocumentById(51L))
                .thenReturn(document);
        OaSignFinalConfirmation confirmation = finalConfirmation(
                90L, 960L, VERSION, signPackage.getFinalDocumentRootHash(),
                CONFIRMATION_TEXT, "LOGIN_TOKEN");
        confirmation.setConfirmedTime(confirmationTime);
        when(fixture.confirmationMapper.selectByPackageAndVersion(90L, VERSION))
                .thenReturn(confirmation);
        when(fixture.confirmationMapper.selectDocumentsByPackageAndVersion(90L, VERSION))
                .thenReturn(confirmationEvidence(confirmation, List.of(document)));
        return new RealExportFixture(fixture, document, review, pendingPath, archivePath,
                signature, seal);
    }

    private RealExportFixture historicalSampleExportFixture(boolean companyFirst)
            throws Exception
    {
        Fixture fixture = fixture(OaSignPackageStatus.SIGNED);
        OaSignFileStorageService storage = signingStorage();
        OaSignedPdfService realSignedPdfService = new OaSignedPdfService(storage);
        ReflectionTestUtils.setField(fixture.service, "signedPdfService", realSignedPdfService);

        Path sampleRoot = OaSignGoldenFixture.requireRoot();
        Path review = sampleRoot.resolve("review.pdf");
        Path pending = sampleRoot.resolve("pending-final.pdf");
        Path archive = sampleRoot.resolve("archive.pdf");
        Path signaturePath = sampleRoot.resolve("signature.png");
        byte[] signature = Files.readAllBytes(signaturePath);
        byte[] seal = Files.readAllBytes(sampleRoot.resolve("seal.jpg"));
        Path templateSource = publishableExactV7Template();
        assertThat(sha256(review)).isEqualTo(
                "552e2b6a17e6206ea4c12c761d02abf698db3281ecf1861a858ce0024b7ce7d4");
        assertThat(sha256(archive)).isEqualTo(
                "731db734f3fe4dc73a2e18aafadacd5df78e53347209e7b0020ac5f942c6cc69");
        assertThat(sha256(templateSource)).isEqualTo(
                "1226728ec0e703d513efda83dc5578ee4cdd65cb3e7d0e7cb5afee813f466558");

        Path receiptReview = Files.createTempFile(tempDir,
                "historical-handbook-receipt-", ".pdf");
        try (java.io.InputStream input = getClass()
                .getResourceAsStream("/signing/sample-review.pdf"))
        {
            assertThat(input).isNotNull();
            Files.copy(input, receiptReview, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        Date employeeTime = Date.from(java.time.Instant.parse("2026-07-28T01:20:00Z"));
        Date companyTime = Date.from(java.time.Instant.parse("2026-07-28T01:25:00Z"));
        Date confirmationTime = Date.from(java.time.Instant.parse("2026-07-28T01:29:48Z"));
        OaSignPackage signPackage = fixture.signPackage;
        signPackage.setPackageNo("SP1785202186549261523");
        signPackage.setEmployeeNameSnapshot("历史样本员工");
        signPackage.setEmployeeIdCardSnapshot("330902********0011");
        signPackage.setLegalEntityIdSnapshot(4L);
        signPackage.setLegalEntityNameSnapshot("舟山茗汇文化传播有限公司");
        signPackage.setLegalRepresentativeSnapshot(null);
        signPackage.setDocumentVersion(VERSION);
        signPackage.setFinalDocumentVersion(VERSION);
        signPackage.setSigningSequence(companyFirst
                ? OaSignSigningSequence.COMPANY_FIRST : OaSignSigningSequence.SIGNATURE_FIRST);
        signPackage.setSignatureSampleFileUrl("/profile/historical-signature.png");
        signPackage.setSignatureSampleHash(sha256(signature));
        signPackage.setSignatureSampleTime(employeeTime);
        signPackage.setSealImageUrlSnapshot("/profile/historical-seal.jpg");
        signPackage.setSealImageHashSnapshot(sha256(seal));
        signPackage.setFinalGeneratedTime(companyTime);
        signPackage.setFinalConfirmedTime(confirmationTime);
        signPackage.setFinalEvidenceGeneratedTime(confirmationTime);

        OaSignPackageDocument document = new OaSignPackageDocument();
        document.setDocumentId(51L);
        document.setPackageId(90L);
        document.setDocumentName("劳动合同");
        document.setTemplateType("ONBOARD_LABOR_CONTRACT");
        document.setTemplateVersionSnapshot("20260721-v7");
        document.setEmployeeVisible("Y");
        document.setEmployeeSignRequired("Y");
        document.setCompanySealRequired("Y");
        document.setDocumentPolicyMode(OaSignPlacementPolicyService.SNAPSHOT_V1);
        document.setSignaturePositionJson(
                OaSignPlacementPolicyService.APPENDED_CONFIRMATION_PAGE);
        document.setCompanySealPositionJson(
                OaSignPlacementPolicyService.APPENDED_CONFIRMATION_PAGE);
        document.setDocumentVersion(VERSION);
        document.setFinalDocumentVersion(VERSION);
        document.setReviewPdfHash(sha256(review));
        document.setReviewPdfUrl("/profile/historical-labor-review.pdf");
        document.setSourceFileUrlSnapshot("/profile/historical-v7-template.docx");
        document.setFinalPdfUrl("/profile/historical-labor-pending.pdf");
        document.setFinalPdfHash(OaSignGoldenFixture.PENDING_FINAL_SHA256);
        document.setFinalContentHash(
                "4c5ac52dead9d598ffa410e0da70f7ad6f62c0395d3666b52223d3264e447f4d");
        document.setFinalArchivePdfUrl("/profile/historical-labor-archive.pdf");
        document.setFinalArchivePdfHash(sha256(archive));

        List<OaSignPackageDocument> supportDocuments = new ArrayList<>();
        supportDocuments.add(noMarkDocument(52L, receiptReview));
        supportDocuments.add(noMarkDocument(53L, receiptReview));
        supportDocuments.add(noMarkDocument(54L, receiptReview));
        supportDocuments.get(0).setDocumentName("入职承诺书");
        supportDocuments.get(0).setTemplateType("ONBOARD_COMMITMENT");
        supportDocuments.get(1).setDocumentName("员工手册签收确认书");
        supportDocuments.get(1).setTemplateType("ONBOARD_HANDBOOK_RECEIPT");
        supportDocuments.get(2).setDocumentName("薪资确认单");
        supportDocuments.get(2).setTemplateType("ONBOARD_SALARY_CONFIRM");
        supportDocuments.forEach(value -> value.setTemplateVersionSnapshot("20260721-v7"));

        Map<Long, SignedPdfResult> supportPending = new LinkedHashMap<>();
        Map<Long, Path> supportPendingPaths = new LinkedHashMap<>();
        Map<Long, String> contentHashes = new LinkedHashMap<>();
        contentHashes.put(document.getDocumentId(), document.getFinalContentHash());
        for (OaSignPackageDocument support : supportDocuments)
        {
            SignedPdfResult generated = realSignedPdfService.generatePendingFinalPdfWithoutMarks(
                    null, signPackage, support, receiptReview);
            Path generatedPath = storage.resolveAuthorizedFile(generated.getArchiveRelativePath());
            supportPending.put(support.getDocumentId(), generated);
            supportPendingPaths.put(support.getDocumentId(), generatedPath);
            contentHashes.put(support.getDocumentId(), generated.getContentPdfHash());
            support.setFinalPdfUrl("/profile/historical-support-"
                    + support.getDocumentId() + "-pending.pdf");
            support.setFinalPdfHash(generated.getSignedPdfHash());
            support.setFinalContentHash(generated.getContentPdfHash());
        }
        String finalRoot = rootHash(contentHashes);
        Map<Long, Path> supportArchivePaths = new LinkedHashMap<>();
        Map<Long, String> archiveHashes = new LinkedHashMap<>();
        archiveHashes.put(document.getDocumentId(), document.getFinalArchivePdfHash());
        for (OaSignPackageDocument support : supportDocuments)
        {
            SignedPdfResult generated = supportPending.get(support.getDocumentId());
            SignedPdfResult archived = realSignedPdfService.archiveConfirmedFinalPdf(
                    null, signPackage, support,
                    supportPendingPaths.get(support.getDocumentId()),
                    generated.getSignedPdfHash(), generated.getContentPdfHash(), finalRoot,
                    null, null, null, null, confirmationTime, null, null);
            Path archivedPath = storage.resolveAuthorizedFile(archived.getArchiveRelativePath());
            supportArchivePaths.put(support.getDocumentId(), archivedPath);
            archiveHashes.put(support.getDocumentId(), archived.getSignedPdfHash());
            support.setFinalArchivePdfUrl("/profile/historical-support-"
                    + support.getDocumentId() + "-archive.pdf");
            support.setFinalArchivePdfHash(archived.getSignedPdfHash());
        }
        signPackage.setFinalDocumentRootHash(finalRoot);
        signPackage.setFinalArchiveRootHash(rootHash(archiveHashes));

        when(fixture.documentService.resolveGeneratedSignPackageFile(
                "/profile/historical-signature.png")).thenReturn(signaturePath);
        when(fixture.documentService.resolveGeneratedSignPackageFile(
                "/profile/historical-labor-review.pdf")).thenReturn(review);
        when(fixture.documentService.resolveGeneratedSignPackageFile(
                "/profile/historical-labor-pending.pdf")).thenReturn(pending);
        when(fixture.documentService.resolveGeneratedSignPackageFile(
                "/profile/historical-labor-archive.pdf")).thenReturn(archive);
        when(fixture.documentService.readConfiguredFileBytes(
                "/profile/historical-v7-template.docx"))
                .thenReturn(Files.readAllBytes(templateSource));
        when(fixture.documentService.readConfiguredFileBytes(
                "/profile/historical-seal.jpg")).thenReturn(seal);
        for (OaSignPackageDocument support : supportDocuments)
        {
            when(fixture.documentService.resolveGeneratedSignPackageFile(
                    support.getFinalArchivePdfUrl()))
                    .thenReturn(supportArchivePaths.get(support.getDocumentId()));
        }
        List<OaSignPackageDocument> visibleDocuments = new ArrayList<>();
        visibleDocuments.add(document);
        visibleDocuments.addAll(supportDocuments);
        when(fixture.documentMapper.selectDocumentsByPackageId(90L))
                .thenReturn(List.copyOf(visibleDocuments));
        when(fixture.documentMapper.selectOaSignPackageDocumentById(51L))
                .thenReturn(document);
        OaSignFinalConfirmation confirmation = finalConfirmation(
                90L, 960L, VERSION, finalRoot, CONFIRMATION_TEXT, "LOGIN_TOKEN");
        confirmation.setConfirmedTime(confirmationTime);
        when(fixture.confirmationMapper.selectByPackageAndVersion(90L, VERSION))
                .thenReturn(confirmation);
        when(fixture.confirmationMapper.selectDocumentsByPackageAndVersion(90L, VERSION))
                .thenReturn(confirmationEvidence(confirmation, visibleDocuments));
        return new RealExportFixture(fixture, document, review, pending,
                archive, signature, seal);
    }

    private OaSignPackageDocument noMarkDocument(Long documentId, Path review) throws Exception
    {
        OaSignPackageDocument document = new OaSignPackageDocument();
        document.setDocumentId(documentId);
        document.setPackageId(90L);
        document.setDocumentName("合同" + documentId);
        document.setEmployeeVisible("Y");
        document.setEmployeeSignRequired("N");
        document.setCompanySealRequired("N");
        document.setDocumentPolicyMode(OaSignPlacementPolicyService.SNAPSHOT_V1);
        document.setDocumentVersion(VERSION);
        document.setFinalDocumentVersion(VERSION);
        document.setReviewPdfHash(sha256(review));
        return document;
    }

    private OaSignFileStorageService signingStorage()
    {
        OaSignFileProperties properties = new OaSignFileProperties();
        properties.getStorage().setRootPath(tempDir.resolve("real-export-archive").toString());
        properties.getStorage().setTempPath(tempDir.resolve("real-export-staging").toString());
        properties.getStorage().setPublicPrefix("/profile/private/sign-package");
        return new OaSignFileStorageService(properties);
    }

    private String placementJson(int page, float x, float y, float width, float height)
    {
        return "{\"mode\":\"PLACED\",\"pageNumber\":" + page
                + ",\"x\":" + x + ",\"y\":" + y
                + ",\"width\":" + width + ",\"height\":" + height + "}";
    }

    private byte[] signaturePng() throws Exception
    {
        return imageBytes(320, 120, Color.BLACK, false);
    }

    private byte[] sealPng() throws Exception
    {
        return imageBytes(180, 180, Color.RED, true);
    }

    private byte[] imageBytes(int width, int height, Color color, boolean circle)
            throws Exception
    {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try
        {
            graphics.setColor(new Color(255, 255, 255, 0));
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(color);
            graphics.setStroke(new BasicStroke(7F));
            if (circle)
            {
                graphics.drawOval(12, 12, width - 24, height - 24);
            }
            else
            {
                graphics.drawLine(20, height - 30, width / 2, 25);
                graphics.drawLine(width / 2, 25, width - 20, height - 35);
            }
        }
        finally
        {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private String normalizedText(PDDocument document) throws Exception
    {
        return Normalizer.normalize(new PDFTextStripper().getText(document),
                Normalizer.Form.NFKC).replace('⻚', '页');
    }

    private void assertCompleteAttachmentChecklist(PDDocument document) throws Exception
    {
        String compact = normalizedText(document).replaceAll("\\s+", "");
        assertThat(compact)
                .contains("☑1《职工宿舍免责协议书》",
                        "☑2《岗位职责说明书》",
                        "☑3《员工手册》",
                        "☑4《薪酬结构确认书》")
                .doesNotContain("□1《职工宿舍免责协议书》",
                        "□2《岗位职责说明书》",
                        "□3《员工手册》",
                        "□4《薪酬结构确认书》");
    }

    private void assertGoldenPlacementAnchorBindings(PDDocument document) throws Exception
    {
        OaSignLaborPlacementProfileRegistry.PlacementProfile profile =
                new OaSignLaborPlacementProfileRegistry()
                        .profile("labor-v7-layout-SP1785202186549261523");
        List<String> signatureAnchors = List.of(
                "primary-signing-row",
                "attachment-signature-row",
                "dormitory-signature-row",
                "position-confirmation-row");
        for (int index = 0; index < signatureAnchors.size(); index++)
        {
            assertGoldenPlacementInsideAnchor(document,
                    profile.signaturePlacements().get(index), profile,
                    signatureAnchors.get(index));
        }
        assertGoldenPlacementInsideAnchor(document, profile.sealPlacements().get(0),
                profile, "primary-signing-row");
    }

    private void assertGoldenPlacementInsideAnchor(PDDocument document,
            OaSignLaborPlacementProfileRegistry.PlacementRect placement,
            OaSignLaborPlacementProfileRegistry.PlacementProfile profile,
            String anchorName) throws Exception
    {
        OaSignLaborPlacementProfileRegistry.AnchorRect anchor = profile.anchors().stream()
                .filter(value -> anchorName.equals(value.name()))
                .findFirst().orElseThrow();
        assertThat(placement.pageNumber()).isEqualTo(anchor.pageNumber());
        float centerX = placement.x() + placement.width() / 2F;
        float centerY = placement.y() + placement.height() / 2F;
        assertThat(centerX).isBetween(anchor.x(), anchor.x() + anchor.width());
        assertThat(centerY).isBetween(anchor.y(), anchor.y() + anchor.height());

        PDPage page = document.getPage(anchor.pageNumber() - 1);
        PDFTextStripperByArea stripper = new PDFTextStripperByArea();
        stripper.addRegion(anchorName, new Rectangle2D.Float(anchor.x(),
                page.getCropBox().getHeight() - anchor.y() - anchor.height(),
                anchor.width(), anchor.height()));
        stripper.extractRegions(page);
        String anchorText = Normalizer.normalize(stripper.getTextForRegion(anchorName),
                Normalizer.Form.NFKC).replaceAll("\\s+", "");
        for (String required : anchor.requiredTexts())
        {
            assertThat(anchorText).as(anchorName + " required label")
                    .contains(required);
        }
    }

    private void writeRedactedGoldenQa(Path sourcePdf) throws Exception
    {
        Path output = Path.of(System.getProperty("user.dir"))
                .resolve("../../output/pdf/")
                .resolve("erp-rev06-golden-historical-redacted-pages-14-16-18.pdf")
                .normalize().toAbsolutePath();
        Files.createDirectories(output.getParent());
        Files.deleteIfExists(output);
        try (PDDocument source = Loader.loadPDF(sourcePdf.toFile());
                PDDocument redacted = new PDDocument())
        {
            PDFRenderer renderer = new PDFRenderer(source);
            for (int pageNumber : List.of(14, 16, 18))
            {
                PDPage sourcePage = source.getPage(pageNumber - 1);
                PDRectangle crop = sourcePage.getCropBox();
                BufferedImage image = renderer.renderImageWithDPI(
                        pageNumber - 1, 144F, ImageType.RGB);
                redactGoldenPage(image, crop, pageNumber);
                PDPage page = new PDPage(new PDRectangle(crop.getWidth(), crop.getHeight()));
                redacted.addPage(page);
                PDImageXObject pageImage = LosslessFactory.createFromImage(redacted, image);
                try (PDPageContentStream content = new PDPageContentStream(redacted, page))
                {
                    content.drawImage(pageImage, 0F, 0F, crop.getWidth(), crop.getHeight());
                }
            }
            redacted.getDocumentInformation().setTitle(
                    "REV-06黄金历史版式脱敏视觉复核页14-16-18");
            redacted.save(output.toFile());
        }
        try (PDDocument qa = Loader.loadPDF(output.toFile()))
        {
            assertThat(qa.getNumberOfPages()).isEqualTo(3);
            assertThat(normalizedText(qa)).isBlank();
            for (PDPage page : qa.getPages())
            {
                assertThat(imageCount(page)).isEqualTo(1);
            }
        }
        System.out.println("REV06_GOLDEN_REDACTED_QA_PDF=" + output);
    }

    private void redactGoldenPage(BufferedImage image, PDRectangle crop, int pageNumber)
    {
        Graphics2D graphics = image.createGraphics();
        try
        {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            if (pageNumber == 14)
            {
                fillPdfRect(graphics, image, crop, 108F, 638F, 174F, 48F);
                fillPdfRect(graphics, image, crop, 98F, 300F, 278F, 72F);
                redactPlacement(graphics, image, crop, 180F, 688F, 62F, 61F, true);
                redactPlacement(graphics, image, crop, 319F, 699F, 90F, 42.3F, false);
                drawPdfText(graphics, image, crop, "甲方代表：已脱敏", 112F, 655F, 12F);
                drawPdfText(graphics, image, crop, "乙方：脱敏员工；签名：",
                        112F, 328F, 12F);
                drawSyntheticSignature(graphics, image, crop,
                        232F, 316F, 76F, 35.8F);
            }
            else if (pageNumber == 16)
            {
                fillPdfRect(graphics, image, crop, 178F, 208F, 244F, 72F);
                drawPdfText(graphics, image, crop, "乙方：脱敏员工；签名：",
                        188F, 240F, 12F);
                drawSyntheticSignature(graphics, image, crop,
                        309F, 225F, 78F, 36.7F);
            }
            else
            {
                fillPdfRect(graphics, image, crop, 342F, 128F, 152F, 126F);
                drawPdfLine(graphics, image, crop, 345F, 215F, 497F, 215F);
                drawPdfLine(graphics, image, crop, 345F, 180F, 497F, 180F);
                drawPdfText(graphics, image, crop, "确认人：脱敏员工；签名：",
                        350F, 230F, 9F);
                drawSyntheticSignature(graphics, image, crop,
                        447.4F, 221.3F, 45F, 16.9F);
                drawPdfText(graphics, image, crop,
                        "身份证号码：TEST-ID-****-0000", 350F, 192F, 7.5F);
                drawPdfText(graphics, image, crop, "日期：2026-08-09",
                        420F, 158F, 7.5F);
            }
        }
        finally
        {
            graphics.dispose();
        }
    }

    private void redactPlacement(Graphics2D graphics, BufferedImage image, PDRectangle crop,
            float x, float y, float width, float height, boolean seal)
    {
        fillPdfRect(graphics, image, crop, x - 2F, y - 2F, width + 4F, height + 4F);
        if (seal)
        {
            java.awt.Rectangle rect = pixelRect(image, crop, x, y, width, height);
            graphics.setColor(Color.RED);
            graphics.setStroke(new BasicStroke(4F));
            graphics.drawOval(rect.x + 5, rect.y + 5,
                    Math.max(1, rect.width - 10), Math.max(1, rect.height - 10));
        }
        else
        {
            drawSyntheticSignature(graphics, image, crop, x, y, width, height);
        }
    }

    private void drawSyntheticSignature(Graphics2D graphics, BufferedImage image,
            PDRectangle crop, float x, float y, float width, float height)
    {
        java.awt.Rectangle rect = pixelRect(image, crop, x, y, width, height);
        graphics.setColor(Color.BLACK);
        graphics.setStroke(new BasicStroke(4F, BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
        int left = rect.x + Math.max(3, rect.width / 14);
        int right = rect.x + rect.width - Math.max(3, rect.width / 14);
        int middle = rect.x + rect.width / 2;
        graphics.drawLine(left, rect.y + (int) (rect.height * 0.72F),
                middle, rect.y + (int) (rect.height * 0.25F));
        graphics.drawLine(middle, rect.y + (int) (rect.height * 0.25F),
                right, rect.y + (int) (rect.height * 0.68F));
    }

    private void fillPdfRect(Graphics2D graphics, BufferedImage image, PDRectangle crop,
            float x, float y, float width, float height)
    {
        java.awt.Rectangle rect = pixelRect(image, crop, x, y, width, height);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(rect.x, rect.y, rect.width, rect.height);
    }

    private java.awt.Rectangle pixelRect(BufferedImage image, PDRectangle crop,
            float x, float y, float width, float height)
    {
        float scaleX = image.getWidth() / crop.getWidth();
        float scaleY = image.getHeight() / crop.getHeight();
        int pixelX = Math.max(0, Math.round((x - crop.getLowerLeftX()) * scaleX));
        int pixelY = Math.max(0, Math.round(
                (crop.getUpperRightY() - y - height) * scaleY));
        int pixelWidth = Math.min(image.getWidth() - pixelX,
                Math.max(1, Math.round(width * scaleX)));
        int pixelHeight = Math.min(image.getHeight() - pixelY,
                Math.max(1, Math.round(height * scaleY)));
        return new java.awt.Rectangle(pixelX, pixelY, pixelWidth, pixelHeight);
    }

    private void drawPdfText(Graphics2D graphics, BufferedImage image, PDRectangle crop,
            String value, float x, float y, float fontSize)
    {
        float scaleX = image.getWidth() / crop.getWidth();
        float scaleY = image.getHeight() / crop.getHeight();
        graphics.setColor(Color.BLACK);
        graphics.setFont(new Font("PingFang SC", Font.PLAIN,
                Math.max(8, Math.round(fontSize * scaleY))));
        graphics.drawString(value,
                Math.round((x - crop.getLowerLeftX()) * scaleX),
                Math.round((crop.getUpperRightY() - y) * scaleY));
    }

    private void drawPdfLine(Graphics2D graphics, BufferedImage image, PDRectangle crop,
            float startX, float startY, float endX, float endY)
    {
        float scaleX = image.getWidth() / crop.getWidth();
        float scaleY = image.getHeight() / crop.getHeight();
        graphics.setColor(Color.BLACK);
        graphics.setStroke(new BasicStroke(2F));
        graphics.drawLine(Math.round((startX - crop.getLowerLeftX()) * scaleX),
                Math.round((crop.getUpperRightY() - startY) * scaleY),
                Math.round((endX - crop.getLowerLeftX()) * scaleX),
                Math.round((crop.getUpperRightY() - endY) * scaleY));
    }

    private OaSignedPdfService.PdfImagePlacement
            historicalGoldenConfirmationSignaturePlacement()
    {
        OaSignLaborPlacementProfileRegistry.PlacementProfile profile =
                new OaSignLaborPlacementProfileRegistry()
                        .profile("labor-v7-layout-SP1785202186549261523");
        OaSignLaborPlacementProfileRegistry.PlacementRect rect =
                profile.signaturePlacements().get(3);
        List<OaSignedPdfService.PdfProtectedRegion> protectedRegions =
                profile.protectedRegions().stream()
                        .map(value -> new OaSignedPdfService.PdfProtectedRegion(
                                value.pageNumber(), value.x(), value.y(), value.width(),
                                value.height()))
                        .toList();
        assertThat(rect.pageNumber()).isEqualTo(18);
        assertThat(new float[] {rect.width(), rect.height(), rect.x(), rect.y()})
                .containsExactly(45F, 16.9F, 447.4F, 221.3F);
        return new OaSignedPdfService.PdfImagePlacement(rect.pageNumber(), rect.x(), rect.y(),
                rect.width(), rect.height(), protectedRegions);
    }

    private void assertInsideRenderedTableRightBoundary(PDDocument document,
            OaSignedPdfService.PdfImagePlacement placement) throws Exception
    {
        PDPage page = document.getPage(placement.getPageNumber() - 1);
        PDRectangle crop = page.getCropBox();
        BufferedImage rendered = new PDFRenderer(document).renderImageWithDPI(
                placement.getPageNumber() - 1, 144F, ImageType.RGB);
        float scaleX = rendered.getWidth() / crop.getWidth();
        int startX = Math.max(0, (int) Math.floor(
                (placement.getX() + placement.getWidth() - crop.getLowerLeftX()) * scaleX));
        int endX = Math.min(rendered.getWidth() - 1, (int) Math.ceil(
                (crop.getUpperRightX() - crop.getLowerLeftX() - 40F) * scaleX));
        int borderColumn = -1;
        int borderPixels = -1;
        for (int x = startX; x <= endX; x++)
        {
            int darkPixels = 0;
            for (int y = 0; y < rendered.getHeight(); y++)
            {
                Color color = new Color(rendered.getRGB(x, y));
                if (color.getRed() < 96 && color.getGreen() < 96 && color.getBlue() < 96)
                {
                    darkPixels++;
                }
            }
            if (darkPixels > borderPixels)
            {
                borderPixels = darkPixels;
                borderColumn = x;
            }
        }
        assertThat(borderPixels)
                .as("rendered confirmation table right border is present")
                .isGreaterThan(rendered.getHeight() / 3);
        float borderX = crop.getLowerLeftX() + borderColumn / scaleX;
        assertThat(placement.getX() + placement.getWidth())
                .as("confirmation signature matrix remains inside rendered table border")
                .isLessThanOrEqualTo(borderX - 3F);
        OaSignedPdfService.PdfProtectedRegion boundary = placement.getProtectedRegions().stream()
                .filter(region -> region.pageNumber() == placement.getPageNumber())
                .max(java.util.Comparator.comparingDouble(
                        OaSignedPdfService.PdfProtectedRegion::x))
                .orElseThrow();
        assertThat(boundary.x()).isGreaterThan(placement.getX() + placement.getWidth());
        assertThat(Math.abs(borderX - (boundary.x() + boundary.width())))
                .as("frozen boundary guard tracks the rendered table border")
                .isLessThanOrEqualTo(3F);
    }

    private void assertUnmodifiedGoldenPagesRenderIdentically(Path archivePath,
            Path exportPath, Set<Integer> modifiedPages) throws Exception
    {
        try (PDDocument archive = Loader.loadPDF(archivePath.toFile());
                PDDocument export = Loader.loadPDF(exportPath.toFile()))
        {
            assertThat(archive.getNumberOfPages()).isEqualTo(19);
            assertThat(export.getNumberOfPages()).isEqualTo(18);
            PDFRenderer archiveRenderer = new PDFRenderer(archive);
            PDFRenderer exportRenderer = new PDFRenderer(export);
            for (int pageNumber = 1; pageNumber <= export.getNumberOfPages(); pageNumber++)
            {
                if (modifiedPages.contains(pageNumber))
                {
                    continue;
                }
                BufferedImage archivePage = archiveRenderer.renderImageWithDPI(
                        pageNumber - 1, 72F, ImageType.RGB);
                BufferedImage exportPage = exportRenderer.renderImageWithDPI(
                        pageNumber - 1, 72F, ImageType.RGB);
                assertThat(exportPage.getWidth()).as("page %s width", pageNumber)
                        .isEqualTo(archivePage.getWidth());
                assertThat(exportPage.getHeight()).as("page %s height", pageNumber)
                        .isEqualTo(archivePage.getHeight());
                int width = archivePage.getWidth();
                int height = archivePage.getHeight();
                int[] exportPixels = exportPage.getRGB(0, 0, width, height, null, 0, width);
                int[] archivePixels = archivePage.getRGB(0, 0, width, height, null, 0, width);
                assertThat(java.util.Arrays.equals(exportPixels, archivePixels))
                        .as("unmodified golden page %s rendered pixels", pageNumber)
                        .isTrue();
            }
        }
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
        List<float[]> matrices = new java.util.ArrayList<>();
        float[] latest = null;
        for (int index = 0; index < tokens.size(); index++)
        {
            if (tokens.get(index) instanceof Operator operator && "cm".equals(operator.getName())
                    && index >= 6)
            {
                latest = new float[6];
                for (int offset = 0; offset < 6; offset++)
                {
                    latest[offset] = ((COSNumber) tokens.get(index - 6 + offset)).floatValue();
                }
            }
            if (tokens.get(index) instanceof Operator operator && "Do".equals(operator.getName())
                    && index > 0 && latest != null && tokens.get(index - 1) instanceof COSName name
                    && page.getResources() != null
                    && page.getResources().getXObject(name) instanceof PDImageXObject)
            {
                matrices.add(latest.clone());
            }
        }
        return matrices;
    }

    private Fixture fixture(String status)
    {
        OaSignPackageServiceImpl service = new OaSignPackageServiceImpl();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        OaSignPackageDocumentMapper documentMapper = mock(OaSignPackageDocumentMapper.class);
        OaSignEventMapper eventMapper = mock(OaSignEventMapper.class);
        OaSignFinalConfirmationMapper confirmationMapper =
                mock(OaSignFinalConfirmationMapper.class);
        OaSignDocumentService documentService = mock(OaSignDocumentService.class);
        OaSignedPdfService signedPdfService = mock(OaSignedPdfService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "documentMapper", documentMapper);
        ReflectionTestUtils.setField(service, "eventMapper", eventMapper);
        ReflectionTestUtils.setField(service, "finalConfirmationMapper", confirmationMapper);
        ReflectionTestUtils.setField(service, "documentService", documentService);
        ReflectionTestUtils.setField(service, "signedPdfService", signedPdfService);
        ReflectionTestUtils.setField(service, "placementPolicyService",
                new OaSignPlacementPolicyService());
        ReflectionTestUtils.setField(service, "signHrAccessService", mock(OaSignHrAccessService.class));
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        OaSignPackage signPackage = signPackage(status);
        when(packageMapper.selectOaSignPackageById(90L)).thenReturn(signPackage);
        return new Fixture(service, packageMapper, documentMapper, eventMapper,
                confirmationMapper, documentService, signedPdfService, signPackage);
    }

    private OaSignPackageDocument configureStrictExportFixture(Fixture fixture,
            Path candidate, Path archive) throws Exception
    {
        Date confirmedTime = new Date(1_700_000_000_000L);
        String finalRoot = rootHash(51L, HASH_B);
        fixture.signPackage.setPackageNo("SP-90");
        fixture.signPackage.setEmployeeNameSnapshot("员工甲");
        fixture.signPackage.setFinalDocumentRootHash(finalRoot);
        fixture.signPackage.setFinalConfirmedTime(confirmedTime);
        fixture.signPackage.setFinalEvidenceGeneratedTime(confirmedTime);

        OaSignPackageDocument document = new OaSignPackageDocument();
        document.setDocumentId(51L);
        document.setPackageId(90L);
        document.setDocumentName("劳动合同");
        document.setEmployeeVisible("Y");
        document.setEmployeeSignRequired("N");
        document.setCompanySealRequired("N");
        document.setDocumentPolicyMode(OaSignPlacementPolicyService.SNAPSHOT_V1);
        document.setFinalDocumentVersion(VERSION);
        document.setFinalPdfUrl("/profile/final-candidate.pdf");
        document.setFinalPdfHash(sha256(Files.readAllBytes(candidate)));
        document.setFinalContentHash(HASH_B);
        if (archive != null)
        {
            String archiveHash = sha256(Files.readAllBytes(archive));
            document.setFinalArchivePdfUrl("/profile/final-archive.pdf");
            document.setFinalArchivePdfHash(archiveHash);
            fixture.signPackage.setFinalArchiveRootHash(rootHash(51L, archiveHash));
            when(fixture.documentService.resolveGeneratedSignPackageFile(
                    document.getFinalArchivePdfUrl())).thenReturn(archive);
        }
        else
        {
            fixture.signPackage.setFinalArchiveRootHash(HASH_A);
        }
        when(fixture.documentMapper.selectDocumentsByPackageId(90L))
                .thenReturn(List.of(document));
        when(fixture.documentMapper.selectOaSignPackageDocumentById(51L))
                .thenReturn(document);
        when(fixture.documentService.resolveGeneratedSignPackageFile(
                document.getFinalPdfUrl())).thenReturn(candidate);
        OaSignFinalConfirmation confirmation = finalConfirmation(
                90L, 960L, VERSION, finalRoot, CONFIRMATION_TEXT, "LOGIN_TOKEN");
        confirmation.setConfirmedTime(confirmedTime);
        when(fixture.confirmationMapper.selectByPackageAndVersion(90L, VERSION))
                .thenReturn(confirmation);
        when(fixture.confirmationMapper.selectDocumentsByPackageAndVersion(90L, VERSION))
                .thenReturn(confirmationEvidence(confirmation, List.of(document)));
        return document;
    }

    private Fixture employeeFixture(String status)
    {
        Fixture fixture = fixture(status);
        SecurityContextHolder.setUserId("960");
        SecurityContextHolder.setUserName("员工甲");
        when(fixture.documentMapper.selectDocumentsByPackageId(90L)).thenReturn(List.of());
        when(fixture.eventMapper.selectEventsByPackageId(90L)).thenReturn(List.of());
        return fixture;
    }

    private static OaSignPackage signPackage(String status)
    {
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(90L);
        signPackage.setEmployeeId(960L);
        signPackage.setStatus(status);
        signPackage.setFinalDocumentVersion(VERSION);
        signPackage.setFinalDocumentRootHash(HASH_A);
        if (OaSignPackageStatus.PENDING_COMPANY.equals(status))
        {
            signPackage.setFinalConfirmationStatus("PREPARED_NOT_SENT");
        }
        else if (OaSignPackageStatus.PENDING_FINAL_CONFIRM.equals(status))
        {
            signPackage.setFinalConfirmationStatus("PENDING");
        }
        else if (OaSignPackageStatus.SIGNED.equals(status))
        {
            signPackage.setFinalConfirmationStatus("CONFIRMED");
        }
        return signPackage;
    }

    private OaSignPackageDocument finalCandidateDocument(Path candidate) throws Exception
    {
        OaSignPackageDocument document = new OaSignPackageDocument();
        document.setDocumentId(51L);
        document.setPackageId(90L);
        document.setDocumentName("劳动合同");
        document.setEmployeeVisible("Y");
        document.setFinalDocumentVersion(VERSION);
        document.setFinalPdfUrl("/profile/" + candidate.getFileName());
        document.setFinalPdfHash(sha256(Files.readAllBytes(candidate)));
        document.setFinalContentHash(HASH_B);
        return document;
    }

    private static String rootHash(Long documentId, String contentHash) throws Exception
    {
        return sha256((documentId + ":" + contentHash + "\n")
                .getBytes(StandardCharsets.UTF_8));
    }

    private static String rootHash(Map<Long, String> hashes) throws Exception
    {
        StringBuilder canonical = new StringBuilder();
        hashes.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> canonical.append(entry.getKey()).append(':')
                        .append(entry.getValue()).append('\n'));
        return sha256(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static OaSignEvent finalReadEvent(Long packageId, Long documentId,
            String version, String hash)
    {
        OaSignEvent event = new OaSignEvent();
        event.setPackageId(packageId);
        event.setDocumentId(documentId);
        event.setOperatorUserId(960L);
        event.setOperatorRole("EMPLOYEE");
        event.setEventPayload("version=" + version + ";hash=" + hash);
        event.setDocumentHash(hash);
        return event;
    }

    private static OaSignFinalDocumentReadRequest finalReadRequest(
            String version, String hash, String requestId)
    {
        OaSignFinalDocumentReadRequest request = new OaSignFinalDocumentReadRequest();
        request.setFinalDocumentVersion(version);
        request.setFinalPdfHash(hash);
        request.setRequestId(requestId);
        return request;
    }

    private static OaSignFinalConfirmation finalConfirmation(Long packageId,
            Long employeeId, String version, String rootHash, String phrase,
            String identityMethod)
    {
        OaSignFinalConfirmation confirmation = new OaSignFinalConfirmation();
        confirmation.setConfirmationId(990L);
        confirmation.setPackageId(packageId);
        confirmation.setEmployeeId(employeeId);
        confirmation.setFinalDocumentVersion(version);
        confirmation.setDocumentRootHash(rootHash);
        confirmation.setConfirmationText(phrase);
        confirmation.setIdentityMethod(identityMethod);
        return confirmation;
    }

    private static List<OaSignFinalConfirmationDocument> confirmationEvidence(
            OaSignFinalConfirmation confirmation,
            List<OaSignPackageDocument> documents)
    {
        List<OaSignFinalConfirmationDocument> evidence = new ArrayList<>();
        long evidenceId = 1L;
        for (OaSignPackageDocument document : documents)
        {
            OaSignFinalConfirmationDocument item = new OaSignFinalConfirmationDocument();
            item.setConfirmationDocumentId(evidenceId++);
            item.setConfirmationId(confirmation.getConfirmationId());
            item.setPackageId(confirmation.getPackageId());
            item.setDocumentId(document.getDocumentId());
            item.setFinalDocumentVersion(confirmation.getFinalDocumentVersion());
            item.setFinalPdfHash(document.getFinalPdfHash());
            evidence.add(item);
        }
        return List.copyOf(evidence);
    }

    private static OaSignFinalConfirmRequest finalConfirmRequest(
            String version, String rootHash, String requestId)
    {
        OaSignFinalConfirmRequest request = new OaSignFinalConfirmRequest();
        request.setFinalDocumentVersion(version);
        request.setDocumentRootHash(rootHash);
        request.setConfirmationText(CONFIRMATION_TEXT);
        request.setRequestId(requestId);
        return request;
    }

    private Path publishableExactV7Template() throws Exception
    {
        Path target = tempDir.resolve("publishable-onboard-20260721-v7.docx");
        if (!Files.exists(target))
        {
            try (java.io.InputStream input = getClass().getResourceAsStream(
                    "/oa/sign/templates/onboard-20260721-v7/09_ONBOARD_LABOR_CONTRACT.docx"))
            {
                assertThat(input).as("publishable exact-v7 template asset").isNotNull();
                Files.copy(input, target);
            }
        }
        assertThat(sha256(target)).isEqualTo(
                "1226728ec0e703d513efda83dc5578ee4cdd65cb3e7d0e7cb5afee813f466558");
        return target;
    }

    private static String sha256(byte[] content) throws Exception
    {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    private static String sha256(Path path) throws Exception
    {
        return sha256(Files.readAllBytes(path));
    }

    private record Fixture(OaSignPackageServiceImpl service,
            OaSignPackageMapper packageMapper,
            OaSignPackageDocumentMapper documentMapper,
            OaSignEventMapper eventMapper,
            OaSignFinalConfirmationMapper confirmationMapper,
            OaSignDocumentService documentService,
            OaSignedPdfService signedPdfService,
            OaSignPackage signPackage)
    {
    }

    private record RealExportFixture(Fixture fixture, OaSignPackageDocument document,
            Path reviewPath, Path pendingPath, Path archivePath,
            byte[] signature, byte[] seal)
    {
    }

    private record ExactV7FormalRuntime(OaSignDocumentService documentService,
            OaSignFileStorageService storage)
    {
    }
}
