package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.constant.OaSignSigningSequence;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPackageDocument;

@DisplayName("签约文档签章位置策略")
class OaSignPlacementPolicyServiceTest
{
    @TempDir
    Path tempDir;

    private static final String SAMPLE_REVIEW_HASH =
            "552e2b6a17e6206ea4c12c761d02abf698db3281ecf1861a858ce0024b7ce7d4";
    private static final String SAMPLE_ARCHIVE_HASH =
            "731db734f3fe4dc73a2e18aafadacd5df78e53347209e7b0020ac5f942c6cc69";
    private static final String SAMPLE_CONTENT_HASH =
            "4c5ac52dead9d598ffa410e0da70f7ad6f62c0395d3666b52223d3264e447f4d";
    private static final String V7_SOURCE_HASH =
            "1226728ec0e703d513efda83dc5578ee4cdd65cb3e7d0e7cb5afee813f466558";
    private static final String V4_SOURCE_HASH =
            "fdbe2df12eb3704370126e09f2c26fd7cd09e0a06db1e0fe960f42f9513f2118";
    private static final String MULTI_AUDIT =
            "\"placementConfigVersion\":\"test-placement-v1\","
                    + "\"profileId\":\"test-profile\","
                    + "\"expectedBodyPageCount\":18,";
    private final OaSignPlacementPolicyService service = new OaSignPlacementPolicyService();

    @Test
    @DisplayName("必签文件未配置坐标时冻结为显式追加页")
    void freezesExplicitAppendPolicyForRequiredSignature()
    {
        assertThat(service.freezeSignaturePosition(null, true))
                .isEqualTo(OaSignPlacementPolicyService.APPENDED_CONFIRMATION_PAGE);
        assertThat(service.freezeSignaturePosition("{\"mode\":\"PLACED\",\"pageNumber\":1,"
                + "\"x\":10,\"y\":20,\"width\":120,\"height\":50}", true))
                .isEqualTo("{\"mode\":\"PLACED\",\"pageNumber\":1,\"x\":10.0,"
                        + "\"y\":20.0,\"width\":120.0,\"height\":50.0}");
    }

    @Test
    @DisplayName("最后一页定位被规范化且同时适用于签名和企业章")
    void freezesExplicitLastPagePolicyWithoutPageNumber()
    {
        String configured = "{\"height\":80,\"x\":114,\"mode\":\"LAST_PAGE\","
                + "\"width\":120,\"y\":375}";
        String canonical = "{\"mode\":\"LAST_PAGE\",\"x\":114.0,\"y\":375.0,"
                + "\"width\":120.0,\"height\":80.0}";

        assertThat(service.freezeSignaturePosition(configured, true)).isEqualTo(canonical);
        assertThat(service.freezeCompanySealPosition(configured, true)).isEqualTo(canonical);

        OaSignPackageDocument document = document();
        document.setSignaturePositionJson(configured);
        document.setCompanySealPositionJson(configured);
        assertThat(service.resolveSignaturePlacement(document).isLastPage()).isTrue();
        assertThat(service.resolveSignaturePlacement(document).getPageNumber()).isNull();
        assertThat(service.resolveCompanySealPlacement(document).isLastPage()).isTrue();
    }

    @Test
    @DisplayName("坐标和尺寸拒绝float下溢为零或上溢为无穷")
    void rejectsCoordinatesThatCannotBeRepresentedAsFiniteFloats()
    {
        for (String field : new String[] { "x", "y", "width", "height" })
        {
            for (String value : new String[] { "1e-50", "1e39" })
            {
                assertThatThrownBy(() -> service.freezeCompanySealPosition(
                        lastPagePlacement(field, value), true))
                        .as("%s=%s应在冻结策略时被拒绝", field, value)
                        .isInstanceOf(ServiceException.class)
                        .hasMessageContaining("企业章定位")
                        .hasMessageContaining(field);
            }
        }
    }

    @Test
    @DisplayName("企业章可显式冻结为追加确认页并从文档快照解析")
    void freezesExplicitAppendPolicyForRequiredCompanySeal()
    {
        assertThat(service.freezeCompanySealPosition(
                "{ \"mode\" : \"APPENDED_CONFIRMATION_PAGE\" }", true))
                .isEqualTo(OaSignPlacementPolicyService.APPENDED_CONFIRMATION_PAGE);

        OaSignPackageDocument document = document();
        document.setSignaturePositionJson(OaSignPlacementPolicyService.APPENDED_CONFIRMATION_PAGE);
        document.setCompanySealPositionJson(OaSignPlacementPolicyService.APPENDED_CONFIRMATION_PAGE);
        assertThat(service.resolveSignaturePlacement(document)).isNull();
        assertThat(service.resolveCompanySealPlacement(document)).isNull();
    }

    @Test
    @DisplayName("正式导出按模板源指纹和每份PDF锚点解析历史追加页而非包号白名单")
    void resolvesHistoricalAppendedPolicyOnlyAtFormalExportBoundary()
    {
        OaSignPackageDocument document = document();
        document.setTemplateType("ONBOARD_LABOR_CONTRACT");
        document.setTemplateVersionSnapshot("20260721-v7");
        document.setReviewPdfHash(SAMPLE_REVIEW_HASH);
        document.setFinalContentHash(SAMPLE_CONTENT_HASH);
        document.setSignaturePositionJson(OaSignPlacementPolicyService.APPENDED_CONFIRMATION_PAGE);
        document.setCompanySealPositionJson(OaSignPlacementPolicyService.APPENDED_CONFIRMATION_PAGE);
        OaSignPackage signPackage = historicalPackage();
        Path root = sampleAuditRoot();
        OaSignPlacementPolicyService.HistoricalExportPolicy policy =
                service.resolveHistoricalExportPolicy(document, signPackage, V7_SOURCE_HASH,
                        root.resolve("review.pdf"), root.resolve("archive.pdf"),
                        SAMPLE_ARCHIVE_HASH);

        assertThat(service.resolveSignaturePlacement(document)).isNull();
        assertThat(service.resolveCompanySealPlacement(document)).isNull();
        assertThat(service.resolveFinalExportSignaturePlacement(document, policy).expanded())
                .hasSize(4);
        assertThat(service.resolveFinalExportCompanySealPlacement(document, policy).expanded())
                .hasSize(1);
        assertThat(service.resolveFinalExportDisplayTextPlacements(document, policy))
                .extracting(OaSignedPdfService.PdfTextPlacement::field)
                .containsExactly("companyLegalRepresentative", "attachmentHandbookMark",
                        "archiveEvidenceNotice");
        assertThat(service.resolveFinalExportExpectedBodyPageCount(document, policy)).isEqualTo(18);
        assertThat(service.resolveFinalExportSignaturePlacement(document, policy).expanded())
                .allSatisfy(placement -> assertThat(placement.getProtectedRegions()).hasSize(4));
        assertThat(service.resolveFinalExportSignaturePlacement(document, policy).expanded()
                .get(3).getProtectedRegions())
                .anySatisfy(region -> {
                    assertThat(region.pageNumber()).isEqualTo(18);
                    assertThat(region.x()).isEqualTo(495.5F);
                    assertThat(region.width()).isEqualTo(1.5F);
                });
        assertThat(policy.legalRepresentativeFallback())
                .isEqualTo(historicalRepresentativeRepair().legalRepresentative());
        assertThat(policy.repairFields()).containsExactly(
                "archiveEvidenceNotice", "attachmentHandbookMark",
                "companyLegalRepresentative", "companySealPosition",
                "employeeSignaturePositions");
        assertThat(policy.repairId()).contains("legal-entity-4-representative-20260719-v1");

        signPackage.setPackageNo("ANOTHER-SAME-LAYOUT-PACKAGE");
        OaSignPlacementPolicyService.HistoricalExportPolicy genericPolicy =
                service.resolveHistoricalExportPolicy(document, signPackage, V7_SOURCE_HASH,
                        root.resolve("review.pdf"), root.resolve("archive.pdf"),
                        SAMPLE_ARCHIVE_HASH);
        assertThat(genericPolicy.profileId())
                .isEqualTo(OaSignLaborAnchorPlacementResolver.PROFILE_ID);
        assertThat(genericPolicy.expectedArchiveFinalRootHash()).isNull();
        assertThat(genericPolicy.repairFields()).containsExactly(
                "archiveEvidenceNotice", "attachmentHandbookMark",
                "companyLegalRepresentative", "companySealPosition",
                "employeeSignaturePositions");

        document.setTemplateVersionSnapshot("20260721-v7-unknown");
        assertThatThrownBy(() -> service.resolveHistoricalExportPolicy(document, signPackage,
                V7_SOURCE_HASH, root.resolve("review.pdf"), root.resolve("archive.pdf"),
                SAMPLE_ARCHIVE_HASH))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("未纳入正文落位清单");
        document.setTemplateVersionSnapshot("20260721-v7");
        document.setReviewPdfHash("f".repeat(64));
        assertThatThrownBy(() -> service.resolveHistoricalExportPolicy(document, signPackage,
                V7_SOURCE_HASH, root.resolve("review.pdf"), root.resolve("archive.pdf"),
                SAMPLE_ARCHIVE_HASH))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("源PDF指纹不一致");
    }

    @Test
    @DisplayName("通用v7历史导出拒绝review与archive正文页数或锚点不一致")
    void rejectsHistoricalReviewArchiveLayoutDrift() throws Exception
    {
        Path root = sampleAuditRoot();
        Path driftedArchive = tempDir.resolve("drifted-history-archive.pdf");
        try (org.apache.pdfbox.pdmodel.PDDocument archive =
                org.apache.pdfbox.Loader.loadPDF(root.resolve("archive.pdf").toFile()))
        {
            archive.removePage(0);
            archive.save(driftedArchive.toFile());
        }
        String driftedHash = org.apache.commons.codec.digest.DigestUtils.sha256Hex(
                java.nio.file.Files.newInputStream(driftedArchive));
        OaSignPackageDocument document = document();
        document.setTemplateType("ONBOARD_LABOR_CONTRACT");
        document.setTemplateVersionSnapshot("20260721-v7");
        document.setReviewPdfHash(SAMPLE_REVIEW_HASH);
        document.setFinalContentHash(SAMPLE_CONTENT_HASH);
        document.setSignaturePositionJson(OaSignPlacementPolicyService.APPENDED_CONFIRMATION_PAGE);
        document.setCompanySealPositionJson(
                OaSignPlacementPolicyService.APPENDED_CONFIRMATION_PAGE);
        OaSignPackage signPackage = historicalPackage();
        signPackage.setPackageNo("ANOTHER-SAME-LAYOUT-PACKAGE");

        assertThatThrownBy(() -> service.resolveHistoricalExportPolicy(document, signPackage,
                V7_SOURCE_HASH, root.resolve("review.pdf"), driftedArchive, driftedHash))
                .isInstanceOf(ServiceException.class)
                .hasMessageMatching(".*(版式证据不一致|必须唯一|正文页数).*?");
    }

    @Test
    @DisplayName("仅核准的exact-v7源指纹可进入动态冻结且v4继续失败关闭")
    void allowsOnlyAuditedFutureLaborTemplateSource()
    {
        service.assertTemplateGenerationAllowed(
                "ONBOARD_LABOR_CONTRACT", "20260721-v7", V7_SOURCE_HASH);
        assertThatThrownBy(() -> service.assertTemplateGenerationAllowed(
                "ONBOARD_LABOR_CONTRACT", "20260718-v4-draft", V4_SOURCE_HASH))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("未获准正文落位");
        assertThatThrownBy(() -> service.assertTemplateGenerationAllowed(
                "ONBOARD_LABOR_CONTRACT", "unreviewed-v8", V7_SOURCE_HASH))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("未纳入正文落位清单");
    }

    @Test
    @DisplayName("追加页未知字段和含糊坐标被拒绝")
    void rejectsUnsafeOrAmbiguousPlacement()
    {
        assertThatThrownBy(() -> service.freezeCompanySealPosition(
                "{\"mode\":\"APPENDED_CONFIRMATION_PAGE\",\"x\":0}", true))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("未知字段：x");
        assertThatThrownBy(() -> service.freezeSignaturePosition(
                "{\"mode\":\"PLACED\",\"pageNumber\":1,\"x\":0,\"y\":0,"
                        + "\"width\":80,\"height\":40,\"rotation\":90}", true))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("未知字段");
        assertThatThrownBy(() -> service.freezeCompanySealPosition(
                "{\"mode\":\"LAST_PAGE\",\"pageNumber\":1,\"x\":0,\"y\":0,"
                        + "\"width\":80,\"height\":40}", true))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("未知字段：pageNumber");
        assertThatThrownBy(() -> service.freezeSignaturePosition(
                "{\"pageNumber\":1,\"x\":0,\"y\":0,\"width\":80,\"height\":40}", true))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("缺少明确mode");
    }

    @Test
    @DisplayName("印章业务要求必须显式传入且与坐标一致")
    void neverInfersSealRequirementFromPlacementPresence()
    {
        assertThat(service.normalizeCompanySealRequired("N", true)).isEqualTo("N");
        assertThatThrownBy(() -> service.normalizeCompanySealRequired(null, true))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("必须显式配置");
        assertThatThrownBy(() -> service.freezeCompanySealPosition(
                "{\"mode\":\"PLACED\",\"pageNumber\":1,\"x\":0,\"y\":0,"
                        + "\"width\":80,\"height\":40}", false))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不得配置印章坐标");
        assertThatThrownBy(() -> service.freezeCompanySealPosition(null, true))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("缺少企业章定位");
    }

    @Test
    @DisplayName("只从包文档快照解析坐标并校验必需标记")
    void resolvesOnlyConsistentFrozenDocumentPolicy()
    {
        OaSignPackageDocument document = document();
        document.setSignaturePositionJson("{\"mode\":\"PLACED\",\"pageNumber\":2,"
                + "\"x\":1,\"y\":2,\"width\":3,\"height\":4}");
        document.setCompanySealPositionJson("{\"mode\":\"PLACED\",\"pageNumber\":1,"
                + "\"x\":5,\"y\":6,\"width\":7,\"height\":8}");

        assertThat(service.resolveSignaturePlacement(document).getPageNumber()).isEqualTo(2);
        assertThat(service.resolveCompanySealPlacement(document).getWidth()).isEqualTo(7);

        document.setCompanySealRequired("N");
        assertThatThrownBy(() -> service.resolveCompanySealPlacement(document))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不得携带印章定位");
    }

    @Test
    @DisplayName("多签章位置携带模板版本和源PDF指纹并拒绝保护区重叠")
    void resolvesVersionedMultiPlacementSnapshot()
    {
        String json = "{\"mode\":\"PLACED_MULTI\","
                + "\"templateType\":\"ONBOARD_LABOR_CONTRACT\","
                + "\"templateVersion\":\"V3\","
                + "\"reviewPdfHash\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\","
                + MULTI_AUDIT
                + "\"placements\":["
                + "{\"pageNumber\":14,\"x\":180,\"y\":688,\"width\":62,\"height\":61},"
                + "{\"pageNumber\":16,\"x\":309,\"y\":225,\"width\":78,\"height\":37}],"
                + "\"protectedRegions\":[{\"pageNumber\":18,\"x\":420,\"y\":210,"
                + "\"width\":130,\"height\":80}]}";
        OaSignPackageDocument document = document();
        document.setTemplateType("ONBOARD_LABOR_CONTRACT");
        document.setTemplateVersionSnapshot("V3");
        document.setReviewPdfHash("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        document.setSignaturePositionJson(json);
        document.setCompanySealRequired("N");

        OaSignPackageDocument resolved = document;
        assertThat(service.resolveSignaturePlacement(resolved).isComposite()).isTrue();
        assertThat(service.resolveSignaturePlacement(resolved).expanded()).hasSize(2);
        assertThat(service.resolveSignaturePlacement(resolved).expanded().get(0).getPageNumber())
                .isEqualTo(14);
        assertThat(service.resolveSignaturePlacement(resolved).expanded().get(0)
                .getProtectedRegions()).hasSize(1);

        resolved.setTemplateVersionSnapshot("V4");
        assertThatThrownBy(() -> service.resolveSignaturePlacement(resolved))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("模板版本指纹不匹配");
    }

    @Test
    @DisplayName("多位置快照不接受缺少模板版本或空位置数组")
    void rejectsIncompleteMultiPlacementSnapshot()
    {
        String missingFingerprint = "{\"mode\":\"PLACED_MULTI\","
                + "\"templateType\":\"ONBOARD_LABOR_CONTRACT\",\"placements\":["
                + "{\"pageNumber\":14,\"x\":1,\"y\":1,\"width\":10,\"height\":10}]}";
        assertThatThrownBy(() -> service.freezeSignaturePosition(missingFingerprint, true))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("缺少templateVersion");

        OaSignPackageDocument document = document();
        document.setTemplateType("ONBOARD_LABOR_CONTRACT");
        document.setTemplateVersionSnapshot("V3");
        document.setReviewPdfHash("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        document.setSignaturePositionJson("{\"mode\":\"PLACED_MULTI\","
                + "\"templateType\":\"ONBOARD_LABOR_CONTRACT\",\"templateVersion\":\"V3\","
                + "\"reviewPdfHash\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\","
                + MULTI_AUDIT
                + "\"placements\":[]}");
        document.setCompanySealRequired("N");
        assertThatThrownBy(() -> service.resolveSignaturePlacement(document))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("至少一个签章位置");
    }

    @Test
    @DisplayName("多位置策略缺少错误或不匹配源PDF哈希均失败关闭")
    void rejectsMissingInvalidOrMismatchedMultiPlacementHash()
    {
        String prefix = "{\"mode\":\"PLACED_MULTI\","
                + "\"templateType\":\"ONBOARD_LABOR_CONTRACT\","
                + "\"templateVersion\":\"V3\",";
        String placements = "\"placements\":[{\"pageNumber\":14,\"x\":1,\"y\":1,"
                + "\"width\":10,\"height\":10}]}";
        assertThatThrownBy(() -> service.freezeSignaturePosition(prefix + placements, true))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("reviewPdfHash必须是SHA-256");
        assertThatThrownBy(() -> service.freezeSignaturePosition(prefix
                + "\"reviewPdfHash\":\"bad\"," + placements, true))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("reviewPdfHash必须是SHA-256");

        OaSignPackageDocument document = document();
        document.setTemplateType("ONBOARD_LABOR_CONTRACT");
        document.setTemplateVersionSnapshot("V3");
        document.setReviewPdfHash("b".repeat(64));
        document.setSignaturePositionJson(prefix + "\"reviewPdfHash\":\""
                + "a".repeat(64) + "\"," + MULTI_AUDIT + placements);
        document.setCompanySealRequired("N");
        assertThatThrownBy(() -> service.resolveSignaturePlacement(document))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("源PDF指纹不匹配");
    }

    @Test
    @DisplayName("多位置快照可携带代表人和员工手册展示修复定位")
    void resolvesVersionedDisplayTextRepairPlacements()
    {
        String json = "{\"mode\":\"PLACED_MULTI\","
                + "\"templateType\":\"ONBOARD_LABOR_CONTRACT\","
                + "\"templateVersion\":\"V3\","
                + "\"reviewPdfHash\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\","
                + MULTI_AUDIT
                + "\"placements\":[{\"pageNumber\":14,\"x\":319,\"y\":699,"
                + "\"width\":90,\"height\":42.3}],"
                + "\"textOverlays\":["
                + "{\"field\":\"companyLegalRepresentative\",\"pageNumber\":14,"
                + "\"x\":172.5,\"y\":651,\"width\":75,\"height\":17,\"fontSize\":12},"
                + "{\"field\":\"attachmentHandbookMark\",\"pageNumber\":14,"
                + "\"x\":112.5,\"y\":432,\"width\":14.5,\"height\":15.5,\"fontSize\":12}]}";
        OaSignPackageDocument document = document();
        document.setTemplateType("ONBOARD_LABOR_CONTRACT");
        document.setTemplateVersionSnapshot("V3");
        document.setReviewPdfHash("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        document.setSignaturePositionJson(json);
        document.setCompanySealRequired("N");

        assertThat(service.resolveDisplayTextPlacements(document))
                .extracting(OaSignedPdfService.PdfTextPlacement::field)
                .containsExactly("companyLegalRepresentative", "attachmentHandbookMark");
    }

    @Test
    @DisplayName("MULTI签名与印章必须冻结相同配置来源且拒绝未知字段")
    void rejectsInconsistentOrUnknownMultiAuditSnapshot()
    {
        OaSignPackageDocument document = document();
        document.setTemplateType("ONBOARD_LABOR_CONTRACT");
        document.setTemplateVersionSnapshot("20260721-v7");
        document.setReviewPdfHash("a".repeat(64));
        document.setSignaturePositionJson(multiPolicy("same-profile", true));
        document.setCompanySealPositionJson(multiPolicy("another-profile", false));

        assertThatThrownBy(() -> service.resolveSignaturePlacement(document))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("冻结证据不一致");

        document.setCompanySealPositionJson(multiPolicy("same-profile", false));
        document.setSignaturePositionJson(multiPolicy("same-profile", true).replace(
                "\"placements\":", "\"unexpected\":true,\"placements\":"));
        assertThatThrownBy(() -> service.resolveFinalExportExpectedBodyPageCount(document))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("未知字段：unexpected");
    }

    @Test
    @DisplayName("公司先行多位置快照保持稳定正文且仅在展示导出落签")
    void keepsCompanyFirstBodyStableWhileRetainingAuditedExportPlacements()
    {
        OaSignPackageDocument document = document();
        document.setTemplateType("ONBOARD_LABOR_CONTRACT");
        document.setTemplateVersionSnapshot("20260721-v7");
        document.setReviewPdfHash("a".repeat(64));
        document.setSignaturePositionJson(multiPolicy("same-profile", true,
                "COMPANY_FIRST_STABLE_BODY_DISPLAY_EXPORT"));
        document.setCompanySealPositionJson(multiPolicy("same-profile", false,
                "COMPANY_FIRST_STABLE_BODY_DISPLAY_EXPORT"));

        OaSignPackage companyFirst = new OaSignPackage();
        companyFirst.setSigningSequence(OaSignSigningSequence.COMPANY_FIRST);
        service.assertSigningSequenceMatches(companyFirst, document);

        assertThat(service.resolveSignaturePlacement(document))
                .as("员工确认时不得改写公司先行稳定正文")
                .isNull();
        assertThat(service.resolveFinalExportSignaturePlacement(document).expanded())
                .as("只读展示导出仍使用冻结的正文坐标")
                .hasSize(1);
        assertThat(service.resolveCompanySealPlacement(document)).isNotNull();

        document.setSignaturePositionJson(multiPolicy("same-profile", true,
                "SIGNATURE_FIRST_BODY_PLACEMENT"));
        document.setCompanySealPositionJson(multiPolicy("same-profile", false,
                "SIGNATURE_FIRST_BODY_PLACEMENT"));
        OaSignPackage signatureFirst = new OaSignPackage();
        signatureFirst.setSigningSequence(OaSignSigningSequence.SIGNATURE_FIRST);
        service.assertSigningSequenceMatches(signatureFirst, document);
        assertThat(service.resolveSignaturePlacement(document).expanded()).hasSize(1);

        document.setCompanySealPositionJson(multiPolicy("same-profile", false,
                "COMPANY_FIRST_STABLE_BODY_DISPLAY_EXPORT"));
        assertThatThrownBy(() -> service.resolveSignaturePlacement(document))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("冻结证据不一致");
    }

    @Test
    @DisplayName("包顺序与MULTI快照必须双向一致且旧快照只兼容签名优先")
    void rejectsBothSigningSequenceMismatchDirections()
    {
        OaSignPackageDocument document = document();
        document.setTemplateType("ONBOARD_LABOR_CONTRACT");
        document.setTemplateVersionSnapshot("20260721-v7");
        document.setReviewPdfHash("a".repeat(64));
        OaSignPackage companyFirst = new OaSignPackage();
        companyFirst.setSigningSequence(OaSignSigningSequence.COMPANY_FIRST);
        OaSignPackage signatureFirst = new OaSignPackage();
        signatureFirst.setSigningSequence(OaSignSigningSequence.SIGNATURE_FIRST);

        document.setSignaturePositionJson(multiPolicy("same-profile", true,
                "SIGNATURE_FIRST_BODY_PLACEMENT"));
        document.setCompanySealPositionJson(multiPolicy("same-profile", false,
                "SIGNATURE_FIRST_BODY_PLACEMENT"));
        assertThatThrownBy(() -> service.assertSigningSequenceMatches(companyFirst, document))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("顺序与MULTI签章策略冻结语义不一致");

        document.setSignaturePositionJson(multiPolicy("same-profile", true,
                "COMPANY_FIRST_STABLE_BODY_DISPLAY_EXPORT"));
        document.setCompanySealPositionJson(multiPolicy("same-profile", false,
                "COMPANY_FIRST_STABLE_BODY_DISPLAY_EXPORT"));
        assertThatThrownBy(() -> service.assertSigningSequenceMatches(signatureFirst, document))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("顺序与MULTI签章策略冻结语义不一致");

        document.setSignaturePositionJson(multiPolicy("same-profile", true));
        document.setCompanySealPositionJson(multiPolicy("same-profile", false));
        service.assertSigningSequenceMatches(signatureFirst, document);
        assertThatThrownBy(() -> service.assertSigningSequenceMatches(companyFirst, document))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("顺序与MULTI签章策略冻结语义不一致");
    }

    @Test
    @DisplayName("生成策略显式冻结两种签署顺序语义")
    void freezesExplicitSigningSequenceSemantics()
    {
        assertThat(OaSignSigningSequence.normalize(null))
                .isEqualTo(OaSignSigningSequence.COMPANY_FIRST);
        assertThat(multiPolicy("same-profile", true,
                "COMPANY_FIRST_STABLE_BODY_DISPLAY_EXPORT"))
                .contains("\"signingSequencePolicy\":"
                        + "\"COMPANY_FIRST_STABLE_BODY_DISPLAY_EXPORT\"");
        assertThat(multiPolicy("same-profile", true,
                "SIGNATURE_FIRST_BODY_PLACEMENT"))
                .contains("\"signingSequencePolicy\":\"SIGNATURE_FIRST_BODY_PLACEMENT\"");
    }

    @Test
    @DisplayName("历史甲方代表修复严格采用左闭右开有效期")
    void appliesHistoricalRepresentativeRepairOnlyInsideAuditedWindow()
    {
        OaSignLaborPlacementProfileRegistry registry =
                new OaSignLaborPlacementProfileRegistry();
        assertThat(registry.historicalRepresentativeRepair(4L,
                "舟山茗汇文化传播有限公司",
                Date.from(Instant.parse("2026-08-08T23:59:59Z")))).isNotNull();
        assertThat(registry.historicalRepresentativeRepair(4L,
                "舟山茗汇文化传播有限公司",
                Date.from(Instant.parse("2026-08-09T00:00:00Z")))).isNull();
        assertThat(registry.historicalRepresentativeRepair(4L,
                "舟山茗汇文化传播有限公司",
                Date.from(Instant.parse("2026-08-09T00:00:01Z")))).isNull();
    }

    @Test
    @DisplayName("同一模板三元组不能同时绑定固定profile和动态resolver")
    void rejectsAmbiguousFutureBindingForSameTemplateFingerprint()
    {
        OaSignLaborPlacementProfileRegistry.PlacementProfile profile = minimalProfile();
        OaSignLaborPlacementProfileRegistry.Ledger ledger =
                new OaSignLaborPlacementProfileRegistry.Ledger("test-v1",
                        List.of(profile),
                        List.of(
                                new OaSignLaborPlacementProfileRegistry.FutureBinding(
                                        "ONBOARD_LABOR_CONTRACT", "v-test", "a".repeat(64),
                                        profile.profileId(), null),
                                new OaSignLaborPlacementProfileRegistry.FutureBinding(
                                        "ONBOARD_LABOR_CONTRACT", "v-test", "a".repeat(64),
                                        null, OaSignLaborAnchorPlacementResolver.PROFILE_ID)),
                        List.of(), List.of(), List.of());

        assertThatThrownBy(() -> new OaSignLaborPlacementProfileRegistry(ledger))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("未来模板绑定重复");
    }

    @Test
    @DisplayName("同一模板三元组不能同时进入未来可用和阻断集合")
    void rejectsOverlapBetweenFutureAndBlockedTemplate()
    {
        OaSignLaborPlacementProfileRegistry.Ledger ledger =
                new OaSignLaborPlacementProfileRegistry.Ledger("test-v1", List.of(),
                        List.of(new OaSignLaborPlacementProfileRegistry.FutureBinding(
                                "ONBOARD_LABOR_CONTRACT", "v-test", "b".repeat(64),
                                null, OaSignLaborAnchorPlacementResolver.PROFILE_ID)),
                        List.of(new OaSignLaborPlacementProfileRegistry.BlockedTemplate(
                                "ONBOARD_LABOR_CONTRACT", "v-test", "b".repeat(64),
                                "not audited", "v-next")),
                        List.of(), List.of());

        assertThatThrownBy(() -> new OaSignLaborPlacementProfileRegistry(ledger))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不能同时配置为未来可用与阻断");
    }

    @Test
    @DisplayName("静态四签一章必须依次绑定核准的命名锚点几何")
    void bindsEveryStaticPlacementToItsApprovedNamedAnchor()
    {
        OaSignLaborPlacementProfileRegistry registry =
                new OaSignLaborPlacementProfileRegistry();
        OaSignLaborPlacementProfileRegistry.PlacementProfile golden =
                registry.profile("labor-v7-layout-SP1785202186549261523");
        assertThat(golden.signaturePlacements())
                .extracting(OaSignLaborPlacementProfileRegistry.PlacementRect::pageNumber)
                .containsExactly(14, 14, 16, 18);
        assertThat(golden.sealPlacements())
                .extracting(OaSignLaborPlacementProfileRegistry.PlacementRect::pageNumber)
                .containsExactly(14);

        List<OaSignLaborPlacementProfileRegistry.PlacementRect> misplaced =
                new java.util.ArrayList<>(golden.signaturePlacements());
        misplaced.set(1, new OaSignLaborPlacementProfileRegistry.PlacementRect(
                14, 10F, 10F, 20F, 20F));
        OaSignLaborPlacementProfileRegistry.PlacementProfile invalid =
                new OaSignLaborPlacementProfileRegistry.PlacementProfile(
                        "misplaced-attachment-signature", golden.expectedBodyPageCount(),
                        List.copyOf(misplaced), golden.sealPlacements(),
                        golden.protectedRegions(), golden.textOverlays(), golden.anchors());
        OaSignLaborPlacementProfileRegistry.Ledger ledger =
                new OaSignLaborPlacementProfileRegistry.Ledger("test-v1",
                        List.of(invalid), List.of(), List.of(), List.of(), List.of());

        assertThatThrownBy(() -> new OaSignLaborPlacementProfileRegistry(ledger))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("attachment-signature-row");
    }

    private OaSignPackageDocument document()
    {
        OaSignPackageDocument document = new OaSignPackageDocument();
        document.setDocumentId(1L);
        document.setDocumentPolicyMode(OaSignPlacementPolicyService.SNAPSHOT_V1);
        document.setEmployeeSignRequired("Y");
        document.setCompanySealRequired("Y");
        return document;
    }

    private OaSignLaborPlacementProfileRegistry.PlacementProfile minimalProfile()
    {
        OaSignLaborPlacementProfileRegistry.PlacementRect placement =
                new OaSignLaborPlacementProfileRegistry.PlacementRect(1, 10F, 10F, 10F, 10F);
        return new OaSignLaborPlacementProfileRegistry.PlacementProfile("fixed-test", 1,
                List.of(placement, placement, placement, placement),
                List.of(placement),
                List.of(new OaSignLaborPlacementProfileRegistry.PlacementRect(
                        1, 40F, 40F, 5F, 5F)), List.of(),
                List.of(
                        semanticAnchor("primary-signing-row"),
                        semanticAnchor("attachment-signature-row"),
                        semanticAnchor("dormitory-signature-row"),
                        semanticAnchor("position-confirmation-row")));
    }

    private OaSignLaborPlacementProfileRegistry.AnchorRect semanticAnchor(String name)
    {
        return new OaSignLaborPlacementProfileRegistry.AnchorRect(
                name, 1, 5F, 5F, 20F, 20F, List.of(name));
    }

    private OaSignPackage historicalPackage()
    {
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageNo("SP1785202186549261523");
        signPackage.setLegalEntityIdSnapshot(4L);
        signPackage.setLegalEntityNameSnapshot("舟山茗汇文化传播有限公司");
        signPackage.setFinalConfirmedTime(Date.from(Instant.parse("2026-07-28T14:09:33Z")));
        signPackage.setSignatureSampleHash(
                "8b03cc9e6898f1edd1d6ad4dd3594f380aaef771c67de90dbb3aa5bfe0ae59d7");
        signPackage.setSealImageHashSnapshot(
                "b14583e12e150ff25482e07d9ae9162c7bd5b274260254c8696d24b0926982d1");
        return signPackage;
    }

    private OaSignLaborPlacementProfileRegistry.HistoricalRepresentativeRepair
            historicalRepresentativeRepair()
    {
        return new OaSignLaborPlacementProfileRegistry().historicalRepresentativeRepair(
                4L, "舟山茗汇文化传播有限公司",
                Date.from(Instant.parse("2026-07-28T14:09:33Z")));
    }

    private String multiPolicy(String profileId, boolean signature)
    {
        return multiPolicy(profileId, signature, null);
    }

    private String multiPolicy(String profileId, boolean signature,
            String signingSequencePolicy)
    {
        String placements = signature
                ? "[{\"pageNumber\":14,\"x\":319,\"y\":699,\"width\":90,\"height\":42.3}]"
                : "[{\"pageNumber\":14,\"x\":180,\"y\":688,\"width\":62,\"height\":61}]";
        return "{\"mode\":\"PLACED_MULTI\","
                + "\"templateType\":\"ONBOARD_LABOR_CONTRACT\","
                + "\"templateVersion\":\"20260721-v7\","
                + "\"reviewPdfHash\":\"" + "a".repeat(64) + "\","
                + "\"placementConfigVersion\":\"20260809-rev06-v1\","
                + "\"profileId\":\"" + profileId + "\","
                + "\"expectedBodyPageCount\":18,"
                + (signingSequencePolicy == null ? ""
                        : "\"signingSequencePolicy\":\"" + signingSequencePolicy + "\",")
                + "\"placements\":" + placements + "}";
    }

    private Path sampleAuditRoot()
    {
        return OaSignGoldenFixture.requireRoot();
    }

    private String lastPagePlacement(String overriddenField, String value)
    {
        return "{\"mode\":\"LAST_PAGE\",\"x\":"
                + ("x".equals(overriddenField) ? value : "10")
                + ",\"y\":" + ("y".equals(overriddenField) ? value : "20")
                + ",\"width\":" + ("width".equals(overriddenField) ? value : "120")
                + ",\"height\":" + ("height".equals(overriddenField) ? value : "40")
                + "}";
    }
}
