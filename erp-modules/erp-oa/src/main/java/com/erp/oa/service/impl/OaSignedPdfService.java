package com.erp.oa.service.impl;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.fontbox.ttf.CmapLookup;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.operator.OperatorName;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.FontMappers;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdfwriter.ContentStreamWriter;
import org.apache.pdfbox.util.Matrix;
import org.apache.pdfbox.util.Vector;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.pdfwriter.compress.CompressParameters;
import org.springframework.stereotype.Service;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPackageDocument;
import com.erp.oa.domain.vo.SignedPdfResult;
import com.erp.oa.domain.vo.StagedSignFile;

@Service
public class OaSignedPdfService
{
    private static final String FINAL_CONFIRMATION_PAGE_TITLE =
            "最终合同电子签署确认页";
    private static final String FINAL_CONFIRMATION_COMPLETED_FIELD =
            "员工最终确认: 已完成";
    private static final String FINAL_CONFIRMATION_ROOT_HASH_LABEL =
            "最终合同集合 SHA-256";
    private static final DateTimeFormatter SIGNED_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));
    static final float EVIDENCE_FONT_SIZE = 11F;
    static final float EVIDENCE_TEXT_X = 48F;
    static final float EVIDENCE_TEXT_TOP_MARGIN = 60F;
    static final float EVIDENCE_TEXT_LEADING = 24F;
    static final float EVIDENCE_IMAGE_TOP_Y = 187F;
    static final float EVIDENCE_TEXT_IMAGE_GAP = 24F;
    private static final List<String> CJK_FONT_CANDIDATES = Arrays.asList(
            "NotoSansCJKsc-Regular", "NotoSansSC-Regular", "SourceHanSansSC-Regular",
            "MicrosoftYaHei", "WenQuanYiZenHei", "STHeitiSC-Medium", "STHeitiSC-Light",
            "STHeiti", "Heiti SC", "SimSun", "ArialUnicodeMS");

    private static final Logger log = LoggerFactory.getLogger(OaSignedPdfService.class);
    private final OaSignFileStorageService fileStorageService;

    public OaSignedPdfService(OaSignFileStorageService fileStorageService)
    {
        if (fileStorageService == null)
        {
            throw new IllegalArgumentException("签约文件存储服务不能为空");
        }
        this.fileStorageService = fileStorageService;
    }

    public SignedPdfResult generateSignedPdf(Long taskId, OaSignPackage signPackage,
            OaSignPackageDocument signDocument, Path reviewPdfPath, byte[] signaturePng,
            byte[] companySealPng, Date signedTime, String confirmationText,
            PdfImagePlacement signaturePlacement, PdfImagePlacement sealPlacement)
    {
        return generateSignedPdf(taskId, signPackage, signDocument, reviewPdfPath,
                signaturePng, companySealPng,
                signaturePng == null ? null : signedTime,
                companySealPng == null ? null : signedTime,
                confirmationText, signaturePlacement, sealPlacement);
    }

    /**
     * Generates a signed PDF while keeping the employee-signature event time distinct from the
     * later company-seal/final-generation event time. The two-stage contract workflow must use
     * this overload so the evidence page never attributes a company seal to the employee's
     * earlier signature timestamp.
     */
    public SignedPdfResult generateSignedPdf(Long taskId, OaSignPackage signPackage,
            OaSignPackageDocument signDocument, Path reviewPdfPath, byte[] signaturePng,
            byte[] companySealPng, Date employeeSignedTime, Date companySealTime,
            String confirmationText, PdfImagePlacement signaturePlacement,
            PdfImagePlacement sealPlacement)
    {
        validateSigningRequest(signPackage, signDocument, reviewPdfPath, signaturePng, companySealPng,
                employeeSignedTime, companySealTime, confirmationText,
                signaturePlacement, sealPlacement);
        String reviewPdfHash = sha256(readFile(reviewPdfPath, "读取员工阅读文件失败"));
        validateReviewPdfHash(signDocument, reviewPdfHash);
        String signatureHash = signaturePng == null ? null : sha256(signaturePng);
        String sealHash = companySealPng == null ? null : sha256(companySealPng);
        byte[] signedPdfBytes = buildSignedPdf(signPackage, reviewPdfPath, signaturePng,
                companySealPng, employeeSignedTime, companySealTime,
                confirmationText, reviewPdfHash,
                signatureHash, sealHash,
                signaturePlacement, sealPlacement);
        boolean appendedEvidencePage = (signaturePng != null && signaturePlacement == null)
                || (companySealPng != null && sealPlacement == null);
        int pageCount = verifySignedPdf(signedPdfBytes, reviewPdfPath,
                appendedEvidencePage);
        if (reviewPdfHash.equals(sha256(signedPdfBytes)))
        {
            throw new ServiceException("已签文件必须与员工阅读文件相互独立");
        }

        StagedSignFile signatureFile = null;
        StagedSignFile signedPdf = null;
        try
        {
            if (signaturePng != null)
            {
                signatureFile = fileStorageService.stage(taskId, signPackage.getPackageId(),
                        signPackage.getDocumentVersion(),
                        "signature-" + signDocument.getDocumentId() + ".png", signaturePng);
            }
            signedPdf = fileStorageService.stage(taskId, signPackage.getPackageId(),
                    signPackage.getDocumentVersion(), "signed-" + signDocument.getDocumentId() + ".pdf",
                    signedPdfBytes);
            if (signatureFile != null)
            {
                fileStorageService.promote(signatureFile);
            }
            fileStorageService.promote(signedPdf);
            return new SignedPdfResult(signedPdf.getArchiveRelativePath(), signedPdf.getPublicUrl(),
                    signedPdf.getFileHash(), signedPdf.getFileSize(),
                    signatureFile == null ? null : signatureFile.getArchiveRelativePath(),
                    signatureFile == null ? null : signatureFile.getPublicUrl(),
                    signatureFile == null ? null : signatureFile.getFileHash(),
                    signatureFile == null ? 0L : signatureFile.getFileSize(),
                    sealHash, pageCount);
        }
        catch (RuntimeException | Error failure)
        {
            discardPromotedAfterFailure(signatureFile, failure);
            discardPromotedAfterFailure(signedPdf, failure);
            throw failure;
        }
        finally
        {
            cleanupQuietly(signatureFile);
            cleanupQuietly(signedPdf);
        }
    }

    /**
     * Archives a newly rendered final PDF for a read-only document that requires neither an
     * employee signature nor a company seal. This deliberately has a separate entry point from
     * {@link #generateSignedPdf} so an accidental empty signing request remains fail-closed.
     */
    public SignedPdfResult generateFinalPdfWithoutMarks(Long taskId, OaSignPackage signPackage,
            OaSignPackageDocument signDocument, Path reviewPdfPath)
    {
        validateDocumentRequest(signPackage, signDocument, reviewPdfPath);
        String reviewPdfHash = sha256(readFile(reviewPdfPath, "读取最终渲染文件失败"));
        validateReviewPdfHash(signDocument, reviewPdfHash);
        byte[] finalPdfBytes = buildFinalPdfWithoutMarks(signPackage, signDocument, reviewPdfPath);
        int pageCount = verifySignedPdf(finalPdfBytes, reviewPdfPath, false);

        StagedSignFile finalPdf = null;
        try
        {
            finalPdf = fileStorageService.stage(taskId, signPackage.getPackageId(),
                    signPackage.getDocumentVersion(),
                    "final-" + signDocument.getDocumentId() + ".pdf", finalPdfBytes);
            fileStorageService.promote(finalPdf);
            return new SignedPdfResult(finalPdf.getArchiveRelativePath(), finalPdf.getPublicUrl(),
                    finalPdf.getFileHash(), finalPdf.getFileSize(), null, null, null, 0L,
                    null, pageCount);
        }
        catch (RuntimeException | Error failure)
        {
            discardPromotedAfterFailure(finalPdf, failure);
            throw failure;
        }
        finally
        {
            cleanupQuietly(finalPdf);
        }
    }

    /**
     * Builds the employee-visible pending-final candidate. Its final page is deliberately a
     * replaceable pending evidence page; it never claims that final confirmation is complete.
     */
    public SignedPdfResult generatePendingFinalPdf(Long taskId, OaSignPackage signPackage,
            OaSignPackageDocument signDocument, Path reviewPdfPath, byte[] signaturePng,
            byte[] companySealPng, Date employeeSignedTime, Date companySealTime,
            String confirmationText, PdfImagePlacement signaturePlacement,
            PdfImagePlacement sealPlacement)
    {
        validateSigningRequest(signPackage, signDocument, reviewPdfPath, signaturePng,
                companySealPng, employeeSignedTime, companySealTime, confirmationText,
                signaturePlacement, sealPlacement);
        return generatePendingFinalPdfInternal(taskId, signPackage, signDocument,
                reviewPdfPath, signaturePng, companySealPng, employeeSignedTime,
                companySealTime, signaturePlacement, sealPlacement);
    }

    public SignedPdfResult generatePendingFinalPdfWithoutMarks(Long taskId,
            OaSignPackage signPackage, OaSignPackageDocument signDocument,
            Path reviewPdfPath)
    {
        validateDocumentRequest(signPackage, signDocument, reviewPdfPath);
        return generatePendingFinalPdfInternal(taskId, signPackage, signDocument,
                reviewPdfPath, null, null, null, null, null, null);
    }

    private SignedPdfResult generatePendingFinalPdfInternal(Long taskId,
            OaSignPackage signPackage, OaSignPackageDocument signDocument,
            Path reviewPdfPath, byte[] signaturePng, byte[] companySealPng,
            Date employeeSignedTime, Date companySealTime,
            PdfImagePlacement signaturePlacement, PdfImagePlacement sealPlacement)
    {
        String reviewPdfHash = sha256(readFile(reviewPdfPath, "读取最终渲染文件失败"));
        validateReviewPdfHash(signDocument, reviewPdfHash);
        String signatureHash = signaturePng == null ? null : sha256(signaturePng);
        String sealHash = companySealPng == null ? null : sha256(companySealPng);
        PendingFinalPdf pending = buildPendingFinalPdf(signPackage, reviewPdfPath,
                signaturePng, companySealPng, employeeSignedTime, companySealTime,
                signatureHash, sealHash, signaturePlacement, sealPlacement);
        int pageCount = verifySignedPdf(pending.pdfBytes(), reviewPdfPath, true);

        StagedSignFile signatureFile = null;
        StagedSignFile pendingPdf = null;
        try
        {
            if (signaturePng != null)
            {
                signatureFile = fileStorageService.stage(taskId, signPackage.getPackageId(),
                        signPackage.getDocumentVersion(),
                        "signature-" + signDocument.getDocumentId() + ".png", signaturePng);
            }
            pendingPdf = fileStorageService.stage(taskId, signPackage.getPackageId(),
                    signPackage.getDocumentVersion(),
                    "pending-final-" + signDocument.getDocumentId() + ".pdf",
                    pending.pdfBytes());
            if (signatureFile != null)
            {
                fileStorageService.promote(signatureFile);
            }
            fileStorageService.promote(pendingPdf);
            return new SignedPdfResult(pendingPdf.getArchiveRelativePath(),
                    pendingPdf.getPublicUrl(), pendingPdf.getFileHash(),
                    pendingPdf.getFileSize(),
                    signatureFile == null ? null : signatureFile.getArchiveRelativePath(),
                    signatureFile == null ? null : signatureFile.getPublicUrl(),
                    signatureFile == null ? null : signatureFile.getFileHash(),
                    signatureFile == null ? 0L : signatureFile.getFileSize(),
                    sealHash, pageCount, pending.contentHash());
        }
        catch (RuntimeException | Error failure)
        {
            discardPromotedAfterFailure(signatureFile, failure);
            discardPromotedAfterFailure(pendingPdf, failure);
            throw failure;
        }
        finally
        {
            cleanupQuietly(signatureFile);
            cleanupQuietly(pendingPdf);
        }
    }

    /**
     * Replaces, rather than appends, the pending evidence page so total page count is stable.
     * Signature/seal placements describe marks already frozen into the stable body; archive never
     * reapplies them to body pages and instead carries both images on the confirmation page.
     */
    public SignedPdfResult archiveConfirmedFinalPdf(Long taskId, OaSignPackage signPackage,
            OaSignPackageDocument signDocument, Path pendingFinalPath,
            String expectedPendingHash, String expectedContentHash, String finalRootHash,
            byte[] signaturePng, byte[] companySealPng, Date employeeSignedTime,
            Date companySealTime, Date finalConfirmedTime,
            PdfImagePlacement signaturePlacement, PdfImagePlacement sealPlacement)
    {
        if (signPackage == null || signDocument == null || pendingFinalPath == null
                || StringUtils.isBlank(expectedPendingHash)
                || StringUtils.isBlank(expectedContentHash)
                || StringUtils.isBlank(finalRootHash) || finalConfirmedTime == null)
        {
            throw new ServiceException("最终归档证据不完整");
        }
        validateFinalArchiveEvidenceInput(signDocument, signaturePng, companySealPng,
                employeeSignedTime, companySealTime, signaturePlacement, sealPlacement);
        byte[] pendingBytes = readFile(pendingFinalPath, "读取待确认最终合同失败");
        if (!expectedPendingHash.equalsIgnoreCase(sha256(pendingBytes)))
        {
            throw new ServiceException("待确认最终合同校验不一致");
        }
        String signatureHash = signaturePng == null ? null : sha256(signaturePng);
        String sealHash = companySealPng == null ? null : sha256(companySealPng);
        byte[] archiveBytes = replacePendingEvidencePage(signPackage, pendingBytes,
                expectedContentHash, finalRootHash, signaturePng, companySealPng,
                signatureHash, sealHash, employeeSignedTime, companySealTime,
                finalConfirmedTime, signaturePlacement, sealPlacement);
        validateFinalArchiveEvidencePage(archiveBytes, expectedContentHash, finalRootHash,
                signatureHash, sealHash);
        int pendingPageCount = pdfPageCount(pendingBytes);
        int archivePageCount = pdfPageCount(archiveBytes);
        if (pendingPageCount != archivePageCount)
        {
            throw new ServiceException("最终归档替换证据页后页数发生变化");
        }
        StagedSignFile archive = null;
        try
        {
            archive = fileStorageService.stage(taskId, signPackage.getPackageId(),
                    signPackage.getFinalDocumentVersion(),
                    "final-archive-" + signDocument.getDocumentId() + ".pdf", archiveBytes);
            fileStorageService.promote(archive);
            return new SignedPdfResult(archive.getArchiveRelativePath(), archive.getPublicUrl(),
                    archive.getFileHash(), archive.getFileSize(), null, null, null, 0L,
                    sealHash, archivePageCount, expectedContentHash);
        }
        catch (RuntimeException | Error failure)
        {
            discardPromotedAfterFailure(archive, failure);
            throw failure;
        }
        finally
        {
            cleanupQuietly(archive);
        }
    }

    public void validatePendingFinalContentHash(Path pendingFinalPath,
            String expectedPendingHash, String expectedContentHash)
    {
        byte[] bytes = readFile(pendingFinalPath, "读取待确认最终合同失败");
        if (StringUtils.isBlank(expectedPendingHash)
                || !expectedPendingHash.equalsIgnoreCase(sha256(bytes)))
        {
            throw new ServiceException("待确认最终合同校验不一致");
        }
        try (PDDocument document = Loader.loadPDF(bytes))
        {
            String metadataHash = document.getDocumentInformation()
                    .getCustomMetadataValue("ERP-Final-Content-Hash");
            if (StringUtils.isBlank(expectedContentHash)
                    || !expectedContentHash.equalsIgnoreCase(metadataHash))
            {
                throw new ServiceException("待确认最终合同正文校验不一致");
            }
        }
        catch (IOException e)
        {
            throw new ServiceException("读取待确认最终合同失败")
                    .setDetailMessage(e.getMessage());
        }
    }

    /**
     * Validates an already persisted final archive without generating or rewriting it.
     * Export uses this boundary so the confirmation-page metadata and image evidence are
     * checked before immutable archive bytes are returned to a client.
     */
    public void validateFinalArchiveEvidence(Path archivePath, String expectedContentHash,
            String expectedFinalRootHash, String signatureHash, String sealHash)
    {
        if (archivePath == null || !Files.isRegularFile(archivePath))
        {
            throw new ServiceException("该历史合同缺少签章位置或证据，暂不能恢复");
        }
        try (PDDocument document = Loader.loadPDF(archivePath.toFile()))
        {
            validateFinalArchiveEvidenceDocument(document, expectedContentHash,
                    expectedFinalRootHash, signatureHash, sealHash);
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (IOException | RuntimeException e)
        {
            throw new ServiceException("读取最终归档文件失败")
                    .setDetailMessage(e.getMessage());
        }
    }

    /**
     * Validates an immutable 19-page archive and creates a disposable 18-page display
     * derivative. The confirmation page remains part of the immutable archive only; it is
     * never copied into the ordinary print/display export and the original bytes are untouched.
     */
    public FinalExportPreparation prepareFinalExport(Path archivePath, String archiveHash,
            String expectedContentHash, String expectedFinalRootHash, String signatureHash,
            String sealHash, PdfImagePlacement signaturePlacement,
            PdfImagePlacement sealPlacement, byte[] signaturePng, byte[] companySealPng)
    {
        return prepareFinalExport(archivePath, archiveHash, expectedContentHash,
                expectedFinalRootHash, signatureHash, sealHash, signaturePlacement,
                sealPlacement, signaturePng, companySealPng, List.of(), null, null);
    }

    /**
     * Creates a display derivative with optional text repairs from the same frozen document
     * policy as the image placements.  Text repairs are never written back to the immutable
     * archive and are applied only to an explicitly versioned, hash-bound target rectangle.
     */
    public FinalExportPreparation prepareFinalExport(Path archivePath, String archiveHash,
            String expectedContentHash, String expectedFinalRootHash, String signatureHash,
            String sealHash, PdfImagePlacement signaturePlacement,
            PdfImagePlacement sealPlacement, byte[] signaturePng, byte[] companySealPng,
            List<PdfTextOverlay> textOverlays)
    {
        return prepareFinalExport(archivePath, archiveHash, expectedContentHash,
                expectedFinalRootHash, signatureHash, sealHash, signaturePlacement,
                sealPlacement, signaturePng, companySealPng, textOverlays, null, null);
    }

    public FinalExportPreparation prepareFinalExport(Path archivePath, String archiveHash,
            String expectedContentHash, String expectedFinalRootHash, String signatureHash,
            String sealHash, PdfImagePlacement signaturePlacement,
            PdfImagePlacement sealPlacement, byte[] signaturePng, byte[] companySealPng,
            List<PdfTextOverlay> textOverlays, Integer expectedBodyPageCount)
    {
        return prepareFinalExport(archivePath, archiveHash, expectedContentHash,
                expectedFinalRootHash, signatureHash, sealHash, signaturePlacement,
                sealPlacement, signaturePng, companySealPng, textOverlays,
                expectedBodyPageCount, null);
    }

    public FinalExportPreparation prepareFinalExport(Path archivePath, String archiveHash,
            String expectedContentHash, String expectedFinalRootHash, String signatureHash,
            String sealHash, PdfImagePlacement signaturePlacement,
            PdfImagePlacement sealPlacement, byte[] signaturePng, byte[] companySealPng,
            List<PdfTextOverlay> textOverlays, Integer expectedBodyPageCount,
            FinalExportAuditMetadata auditMetadata)
    {
        if (archivePath == null || !Files.isRegularFile(archivePath)
                || StringUtils.isBlank(archiveHash))
        {
            throw new ServiceException("该历史合同缺少签章位置或证据，暂不能恢复");
        }
        Path derivedPath = null;
        try
        {
            byte[] archiveBytes = readFile(archivePath, "读取最终归档文件失败");
            if (!archiveHash.equalsIgnoreCase(sha256(archiveBytes)))
            {
                throw new ServiceException("最终归档文件校验不一致");
            }
            try (PDDocument document = Loader.loadPDF(archiveBytes))
            {
                validateFinalArchiveEvidenceDocument(document, expectedContentHash,
                        expectedFinalRootHash, signatureHash, sealHash);
                int bodyPageCount = document.getNumberOfPages() - 1;
                if (bodyPageCount < 1)
                {
                    throw new ServiceException("该历史合同缺少签章位置或证据，暂不能恢复");
                }
                if (expectedBodyPageCount != null && expectedBodyPageCount != bodyPageCount)
                {
                    throw new ServiceException("该历史合同正文页数与冻结签章策略不匹配");
                }
                if ((signaturePng != null && signaturePlacement == null)
                        || (companySealPng != null && sealPlacement == null))
                {
                    throw new ServiceException("该历史合同缺少签章位置或证据，暂不能恢复");
                }
                PdfImagePlacement missingSignature = missingPlacedImage(document, bodyPageCount,
                        signaturePlacement, signaturePng, "签名");
                PdfImagePlacement missingSeal = missingPlacedImage(document, bodyPageCount,
                        sealPlacement, companySealPng, "企业章");

                if (missingSignature != null)
                {
                    OaSignImageValidator.requireSignaturePng(signaturePng);
                }
                if (missingSeal != null)
                {
                    OaSignImageValidator.requireCompanySealExtension(companySealPng);
                }
                // validateFinalArchiveEvidenceDocument proves that this exact page is the
                // unique completed confirmation page.  Never infer removability from page
                // count, metadata, or image resources alone.
                document.removePage(document.getNumberOfPages() - 1);
                if (missingSignature != null)
                {
                    drawPlacedImage(document,
                            PDImageXObject.createFromByteArray(document, signaturePng,
                                    "employee-signature-export.png"),
                            missingSignature, "签名", bodyPageCount);
                }
                if (missingSeal != null)
                {
                    drawPlacedImage(document,
                            PDImageXObject.createFromByteArray(document, companySealPng,
                                    "company-seal-export.png"),
                            missingSeal, "企业章", bodyPageCount);
                }
                drawTextOverlays(document, textOverlays, bodyPageCount);

            document.getDocumentInformation().setCustomMetadataValue(
                    "ERP-Final-Export-Type", "SIGNED_PLACEMENT_DISPLAY_DERIVATIVE");
            document.getDocumentInformation().setCustomMetadataValue(
                    "ERP-Final-Export-Source-Archive-SHA256", archiveHash);
            document.getDocumentInformation().setCustomMetadataValue(
                    "ERP-Final-Export-Note", "仅供签章位置展示；原最终归档为法务证据源");
            document.getDocumentInformation().setCustomMetadataValue(
                    "ERP-Final-Export-Body-Pages", String.valueOf(bodyPageCount));
            document.getDocumentInformation().setCustomMetadataValue(
                    "ERP-Final-Export-Original-Archive-Pages",
                    String.valueOf(bodyPageCount + 1));
            document.getDocumentInformation().setCustomMetadataValue(
                    "ERP-Final-Export-Text-Repair-Fields", textRepairFields(textOverlays));
            writeFinalExportAuditMetadata(document, auditMetadata);
            derivedPath = Files.createTempFile("erp-final-signature-display-", ".pdf");
            document.save(derivedPath.toFile(), CompressParameters.NO_COMPRESSION);
            }
        }
        catch (ServiceException e)
        {
            deleteQuietly(derivedPath);
            throw e;
        }
        catch (IOException | RuntimeException e)
        {
            deleteQuietly(derivedPath);
            throw new ServiceException("生成签章展示导出副本失败")
                    .setDetailMessage(e.getMessage());
        }

        try (PDDocument derived = Loader.loadPDF(derivedPath.toFile()))
        {
            validateFinalExportDerivative(derived, archiveHash, expectedContentHash,
                    signaturePlacement, sealPlacement, signaturePng, companySealPng,
                    textOverlays, auditMetadata);
            return new FinalExportPreparation(derivedPath, true);
        }
        catch (ServiceException e)
        {
            deleteQuietly(derivedPath);
            throw e;
        }
        catch (IOException | RuntimeException e)
        {
            deleteQuietly(derivedPath);
            throw new ServiceException("校验签章展示导出副本失败")
                    .setDetailMessage(e.getMessage());
        }
    }

    private void validateFinalExportDerivative(PDDocument document, String archiveHash,
            String expectedContentHash, PdfImagePlacement signaturePlacement,
            PdfImagePlacement sealPlacement, byte[] signaturePng, byte[] companySealPng,
            List<PdfTextOverlay> textOverlays, FinalExportAuditMetadata auditMetadata)
            throws IOException
    {
        String type = document.getDocumentInformation()
                .getCustomMetadataValue("ERP-Final-Export-Type");
        String sourceHash = document.getDocumentInformation()
                .getCustomMetadataValue("ERP-Final-Export-Source-Archive-SHA256");
        String contentHash = document.getDocumentInformation()
                .getCustomMetadataValue("ERP-Final-Content-Hash");
        if (!"SIGNED_PLACEMENT_DISPLAY_DERIVATIVE".equals(type)
                || !archiveHash.equalsIgnoreCase(sourceHash)
                || !expectedContentHash.equalsIgnoreCase(contentHash))
        {
            throw new ServiceException("签章展示导出副本证据标识不一致");
        }
        String originalPages = document.getDocumentInformation()
                .getCustomMetadataValue("ERP-Final-Export-Original-Archive-Pages");
        if (document.getNumberOfPages() < 1 || !String.valueOf(document.getNumberOfPages() + 1)
                .equals(originalPages))
        {
            throw new ServiceException("签章展示导出副本页数异常");
        }
        String text = new PDFTextStripper().getText(document);
        if (text.contains("最终合同电子签署确认页"))
        {
            throw new ServiceException("签章展示导出副本不得包含最终确认页");
        }
        String expectedTextFields = textRepairFields(textOverlays);
        String actualTextFields = document.getDocumentInformation()
                .getCustomMetadataValue("ERP-Final-Export-Text-Repair-Fields");
        if (!expectedTextFields.equals(actualTextFields))
        {
            throw new ServiceException("签章展示文本修复证据标识不一致");
        }
        validateFinalExportAuditMetadata(document, auditMetadata);
        for (PdfTextOverlay overlay : textOverlays == null ? List.<PdfTextOverlay>of() : textOverlays)
        {
            String expected = canonicalPositionText(overlay.value());
            String positionedText = textAtPlacement(document, overlay.placement());
            if (StringUtils.isBlank(expected) || countOccurrences(positionedText, expected) != 1)
            {
                throw new ServiceException("签章展示文本修复位置校验失败：" + overlay.field());
            }
            if ("companyLegalRepresentative".equals(overlay.field())
                    && positionedText.matches(".*[_＿]{2,}.*"))
            {
                throw new ServiceException("签章展示甲方代表旧占位文字未移除");
            }
            if ("attachmentHandbookMark".equals(overlay.field())
                    && (positionedText.contains("□3《员工手册》")
                            || positionedText.contains("☐3《员工手册》")))
            {
                throw new ServiceException("签章展示员工手册旧勾选文字未移除");
            }
            if ("archiveEvidenceNotice".equals(overlay.field())
                    && positionedText.contains(canonicalPositionText(
                            "签署时间及文件校验信息见本合同末页《电子签署确认页》。")))
            {
                throw new ServiceException("签章展示旧确认页引用文字未移除");
            }
        }
        int bodyPageCount = document.getNumberOfPages();
        if ((signaturePlacement != null
                && !containsPlacedImage(document, bodyPageCount, signaturePlacement,
                        signaturePng, "签名"))
                || (sealPlacement != null
                && !containsPlacedImage(document, bodyPageCount, sealPlacement,
                        companySealPng, "企业章")))
        {
            throw new ServiceException("签章展示导出副本位置校验失败");
        }
    }

    private String textAtPlacement(PDDocument document, PdfTextPlacement placement)
            throws IOException
    {
        if (placement == null || placement.pageNumber() <= 0
                || placement.pageNumber() > document.getNumberOfPages())
        {
            return "";
        }
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
        return canonicalPositionText(stripper.getTextForRegion("target"));
    }

    private String canonicalPositionText(String value)
    {
        return java.text.Normalizer.normalize(StringUtils.defaultString(value),
                java.text.Normalizer.Form.NFKC).replace('⻚', '页')
                .replaceAll("[\\s_＿]+", "");
    }

    private int countOccurrences(String value, String expected)
    {
        if (StringUtils.isBlank(value) || StringUtils.isBlank(expected))
        {
            return 0;
        }
        int count = 0;
        int from = 0;
        while ((from = value.indexOf(expected, from)) >= 0)
        {
            count++;
            from += expected.length();
        }
        return count;
    }

    private void drawTextOverlays(PDDocument document, List<PdfTextOverlay> textOverlays,
            int maxPageNumber) throws IOException
    {
        if (textOverlays == null || textOverlays.isEmpty())
        {
            return;
        }
        List<String> textValues = textOverlays.stream().map(PdfTextOverlay::value).toList();
        PDFont font = loadEvidenceFont(document, textValues);
        Map<Integer, List<PdfTextOverlay>> overlaysByPage = new HashMap<>();
        for (PdfTextOverlay overlay : textOverlays)
        {
            if (overlay == null || StringUtils.isBlank(overlay.field())
                    || StringUtils.isBlank(overlay.value()) || overlay.placement() == null)
            {
                throw new ServiceException("签章展示文本修复证据不完整");
            }
            PdfTextPlacement placement = overlay.placement();
            if (!"companyLegalRepresentative".equals(overlay.field())
                    && !"attachmentHandbookMark".equals(overlay.field())
                    && !"archiveEvidenceNotice".equals(overlay.field()))
            {
                throw new ServiceException("签章展示文本字段不支持：" + overlay.field());
            }
            int pageNumber = resolveAndValidateTextPlacementPage(document, placement,
                    maxPageNumber, overlay.field());
            validateTextPlacementBounds(document, pageNumber, placement, overlay.field());
            float textWidth = font.getStringWidth(overlay.value()) / 1000F
                    * placement.fontSize();
            float textPadding = Math.min(5F, placement.width() * 0.05F);
            if (textWidth + textPadding > placement.width())
            {
                throw new ServiceException("签章展示文本超出定位矩形：" + overlay.field());
            }
            overlaysByPage.computeIfAbsent(pageNumber, ignored -> new ArrayList<>()).add(overlay);
        }

        // Removing the old text operators is intentional and limited to this disposable
        // derivative.  Painting a white rectangle alone would leave conflicting searchable and
        // copyable text in the PDF accessibility layer.
        for (Map.Entry<Integer, List<PdfTextOverlay>> entry : overlaysByPage.entrySet())
        {
            TextLayerRegionRewriter rewriter = new TextLayerRegionRewriter(document,
                    document.getPage(entry.getKey() - 1), entry.getValue());
            rewriter.rewrite();
            Set<String> expectedFields = entry.getValue().stream()
                    .map(PdfTextOverlay::field)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            if (!rewriter.removedFields().equals(expectedFields))
            {
                throw new ServiceException("签章展示旧文字层未完整移除：第"
                        + entry.getKey() + "页");
            }
        }

        for (PdfTextOverlay overlay : textOverlays)
        {
            PdfTextPlacement placement = overlay.placement();
            int pageNumber = placement.pageNumber();
            float textPadding = Math.min(5F, placement.width() * 0.05F);
            PDPage page = document.getPage(pageNumber - 1);
            try (PDPageContentStream content = new PDPageContentStream(document, page,
                    AppendMode.APPEND, true, true))
            {
                content.setNonStrokingColor(1F, 1F, 1F);
                content.addRect(placement.x(), placement.y(), placement.width(),
                        placement.height());
                content.fill();
                content.setNonStrokingColor(0F, 0F, 0F);
                content.beginText();
                content.setFont(font, placement.fontSize());
                content.newLineAtOffset(placement.x() + textPadding,
                        placement.y() + Math.max(0F, (placement.height()
                                - placement.fontSize()) / 2F));
                content.showText(overlay.value());
                content.endText();
            }
        }
    }

    private int resolveAndValidateTextPlacementPage(PDDocument document,
            PdfTextPlacement placement, int maxPageNumber, String label)
    {
        if (placement.pageNumber() <= 0 || placement.pageNumber() > document.getNumberOfPages()
                || placement.pageNumber() > maxPageNumber)
        {
            throw new ServiceException("签章展示文本定位页码不存在：" + label);
        }
        return placement.pageNumber();
    }

    private void validateTextPlacementBounds(PDDocument document, int pageNumber,
            PdfTextPlacement placement, String label)
    {
        PDRectangle visibleBox = document.getPage(pageNumber - 1).getCropBox();
        if (!Float.isFinite(placement.x()) || !Float.isFinite(placement.y())
                || !Float.isFinite(placement.width()) || !Float.isFinite(placement.height())
                || !Float.isFinite(placement.fontSize()) || placement.x() < visibleBox.getLowerLeftX()
                || placement.y() < visibleBox.getLowerLeftY() || placement.width() <= 0
                || placement.height() <= 0 || placement.fontSize() <= 0
                || (double) placement.x() + placement.width() > visibleBox.getUpperRightX()
                || (double) placement.y() + placement.height() > visibleBox.getUpperRightY())
        {
            throw new ServiceException("签章展示文本定位越界：" + label);
        }
        for (PdfProtectedRegion region : placement.protectedRegions())
        {
            if (region.pageNumber() == pageNumber
                    && intersects(placement.x(), placement.y(), placement.width(),
                            placement.height(), region.x(), region.y(), region.width(),
                            region.height()))
            {
                throw new ServiceException("签章展示文本定位与受保护区域重叠：" + label);
            }
        }
    }

    private String textRepairFields(List<PdfTextOverlay> overlays)
    {
        if (overlays == null || overlays.isEmpty())
        {
            return "";
        }
        return overlays.stream().map(PdfTextOverlay::field).sorted()
                .reduce((left, right) -> left + "," + right).orElse("");
    }

    private void writeFinalExportAuditMetadata(PDDocument document,
            FinalExportAuditMetadata audit)
    {
        document.getDocumentInformation().setCustomMetadataValue(
                "ERP-Final-Export-Placement-Config-Version",
                audit == null ? "" : metadataValue(audit.configVersion()));
        document.getDocumentInformation().setCustomMetadataValue(
                "ERP-Final-Export-Placement-Profile",
                audit == null ? "" : metadataValue(audit.profileId()));
        document.getDocumentInformation().setCustomMetadataValue(
                "ERP-Final-Export-Historical-Repair-Id",
                audit == null ? "" : metadataValue(audit.repairId()));
        document.getDocumentInformation().setCustomMetadataValue(
                "ERP-Final-Export-Legal-Representative-Source",
                audit == null ? "" : metadataValue(audit.legalRepresentativeSource()));
        document.getDocumentInformation().setCustomMetadataValue(
                "ERP-Final-Export-Legal-Representative-Evidence",
                audit == null ? "" : metadataValue(audit.legalRepresentativeEvidence()));
        document.getDocumentInformation().setCustomMetadataValue(
                "ERP-Final-Export-Historical-Repair-Fields",
                audit == null || audit.repairFields() == null ? ""
                        : audit.repairFields().stream().filter(StringUtils::isNotBlank)
                                .sorted().reduce((left, right) -> left + "," + right)
                                .orElse(""));
    }

    private void validateFinalExportAuditMetadata(PDDocument document,
            FinalExportAuditMetadata audit)
    {
        String expectedConfig = audit == null ? "" : metadataValue(audit.configVersion());
        String expectedProfile = audit == null ? "" : metadataValue(audit.profileId());
        String expectedRepair = audit == null ? "" : metadataValue(audit.repairId());
        String expectedRepresentativeSource = audit == null ? ""
                : metadataValue(audit.legalRepresentativeSource());
        String expectedEvidence = audit == null ? ""
                : metadataValue(audit.legalRepresentativeEvidence());
        String expectedFields = audit == null || audit.repairFields() == null ? ""
                : audit.repairFields().stream().filter(StringUtils::isNotBlank).sorted()
                        .reduce((left, right) -> left + "," + right).orElse("");
        if (!expectedConfig.equals(document.getDocumentInformation().getCustomMetadataValue(
                    "ERP-Final-Export-Placement-Config-Version"))
                || !expectedProfile.equals(document.getDocumentInformation()
                        .getCustomMetadataValue("ERP-Final-Export-Placement-Profile"))
                || !expectedRepair.equals(document.getDocumentInformation()
                        .getCustomMetadataValue("ERP-Final-Export-Historical-Repair-Id"))
                || !expectedRepresentativeSource.equals(document.getDocumentInformation()
                        .getCustomMetadataValue(
                                "ERP-Final-Export-Legal-Representative-Source"))
                || !expectedEvidence.equals(document.getDocumentInformation()
                        .getCustomMetadataValue(
                                "ERP-Final-Export-Legal-Representative-Evidence"))
                || !expectedFields.equals(document.getDocumentInformation()
                        .getCustomMetadataValue("ERP-Final-Export-Historical-Repair-Fields")))
        {
            throw new ServiceException("签章展示导出副本修复审计元数据不一致");
        }
    }

    private String metadataValue(String value)
    {
        return StringUtils.defaultString(value).trim();
    }

    public void discardUncommitted(SignedPdfResult result)
    {
        if (result == null)
        {
            return;
        }
        ServiceException failure = null;
        try
        {
            fileStorageService.discardUncommitted(result.getArchiveRelativePath(), result.getSignedPdfHash());
        }
        catch (ServiceException e)
        {
            failure = e;
        }
        try
        {
            if (StringUtils.isNotBlank(result.getSignatureArchiveRelativePath())
                    && StringUtils.isNotBlank(result.getSignatureHash()))
            {
                fileStorageService.discardUncommitted(
                        result.getSignatureArchiveRelativePath(), result.getSignatureHash());
            }
        }
        catch (ServiceException e)
        {
            if (failure == null)
            {
                failure = e;
            }
        }
        if (failure != null)
        {
            throw failure;
        }
    }

    private byte[] buildSignedPdf(OaSignPackage signPackage, Path reviewPdfPath, byte[] signaturePng,
            byte[] companySealPng, Date employeeSignedTime, Date companySealTime,
            String confirmationText,
            String reviewPdfHash, String signatureHash, String sealHash,
            PdfImagePlacement signaturePlacement, PdfImagePlacement sealPlacement)
    {
        try (PDDocument document = Loader.loadPDF(reviewPdfPath.toFile());
                ByteArrayOutputStream output = new ByteArrayOutputStream())
        {
            PDImageXObject signatureImage = signaturePng == null ? null
                    : PDImageXObject.createFromByteArray(document, signaturePng,
                            "employee-signature.png");
            PDImageXObject sealImage = companySealPng == null ? null
                    : PDImageXObject.createFromByteArray(document, companySealPng, "company-seal.png");
            if (signatureImage != null && signaturePlacement != null)
            {
                drawPlacedImage(document, signatureImage, signaturePlacement, "签名");
            }
            if (sealImage != null && sealPlacement != null)
            {
                drawPlacedImage(document, sealImage, sealPlacement, "企业章");
            }
            PDImageXObject appendedSignature = signaturePlacement == null ? signatureImage : null;
            PDImageXObject appendedSeal = sealPlacement == null ? sealImage : null;
            if (appendedSignature != null || appendedSeal != null)
            {
                appendEvidencePage(document, signPackage, appendedSignature, appendedSeal,
                        employeeSignedTime, companySealTime, confirmationText, reviewPdfHash,
                        appendedSignature == null ? null : signatureHash,
                        appendedSeal == null ? null : sealHash);
            }
            document.save(output, CompressParameters.NO_COMPRESSION);
            return output.toByteArray();
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (IOException e)
        {
            throw new ServiceException("生成独立已签文件失败").setDetailMessage(e.getMessage());
        }
    }

    private byte[] buildFinalPdfWithoutMarks(OaSignPackage signPackage,
            OaSignPackageDocument signDocument, Path reviewPdfPath)
    {
        try (PDDocument document = Loader.loadPDF(reviewPdfPath.toFile());
                ByteArrayOutputStream output = new ByteArrayOutputStream())
        {
            document.getDocumentInformation().setCustomMetadataValue(
                    "ERP-Final-Archive",
                    signPackage.getPackageId() + ":" + signDocument.getDocumentId()
                            + ":" + signPackage.getDocumentVersion());
            document.save(output, CompressParameters.NO_COMPRESSION);
            return output.toByteArray();
        }
        catch (IOException e)
        {
            throw new ServiceException("生成无标记最终文件失败").setDetailMessage(e.getMessage());
        }
    }

    private PendingFinalPdf buildPendingFinalPdf(OaSignPackage signPackage,
            Path reviewPdfPath, byte[] signaturePng, byte[] companySealPng,
            Date employeeSignedTime, Date companySealTime, String signatureHash,
            String sealHash, PdfImagePlacement signaturePlacement,
            PdfImagePlacement sealPlacement)
    {
        try (PDDocument document = Loader.loadPDF(reviewPdfPath.toFile());
                ByteArrayOutputStream bodyOutput = new ByteArrayOutputStream())
        {
            PDImageXObject signatureImage = signaturePng == null ? null
                    : PDImageXObject.createFromByteArray(document, signaturePng,
                            "employee-signature.png");
            PDImageXObject sealImage = companySealPng == null ? null
                    : PDImageXObject.createFromByteArray(document, companySealPng,
                            "company-seal.png");
            if (signatureImage != null && signaturePlacement != null)
            {
                drawPlacedImage(document, signatureImage, signaturePlacement, "签名");
            }
            if (sealImage != null && sealPlacement != null)
            {
                drawPlacedImage(document, sealImage, sealPlacement, "企业章");
            }
            document.save(bodyOutput, CompressParameters.NO_COMPRESSION);
            byte[] bodyBytes = bodyOutput.toByteArray();
            String contentHash = sha256(bodyBytes);
            try (PDDocument candidate = Loader.loadPDF(bodyBytes);
                    ByteArrayOutputStream candidateOutput = new ByteArrayOutputStream())
            {
                candidate.getDocumentInformation().setCustomMetadataValue(
                        "ERP-Final-Content-Hash", contentHash);
                PDImageXObject appendedSignature = signaturePng != null && signaturePlacement == null
                        ? PDImageXObject.createFromByteArray(candidate, signaturePng,
                                "employee-signature.png") : null;
                PDImageXObject appendedSeal = companySealPng != null && sealPlacement == null
                        ? PDImageXObject.createFromByteArray(candidate, companySealPng,
                                "company-seal.png") : null;
                appendFinalEvidencePage(candidate, signPackage, false, appendedSignature,
                        appendedSeal, employeeSignedTime, companySealTime, null,
                        contentHash, null, signatureHash, sealHash);
                candidate.save(candidateOutput, CompressParameters.NO_COMPRESSION);
                return new PendingFinalPdf(candidateOutput.toByteArray(), contentHash);
            }
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (IOException e)
        {
            throw new ServiceException("生成待最终确认合同失败").setDetailMessage(e.getMessage());
        }
    }

    private byte[] replacePendingEvidencePage(OaSignPackage signPackage,
            byte[] pendingBytes, String contentHash, String finalRootHash,
            byte[] signaturePng, byte[] companySealPng, String signatureHash,
            String sealHash, Date employeeSignedTime, Date companySealTime,
            Date finalConfirmedTime, PdfImagePlacement signaturePlacement,
            PdfImagePlacement sealPlacement)
    {
        try (PDDocument document = Loader.loadPDF(pendingBytes);
                ByteArrayOutputStream output = new ByteArrayOutputStream())
        {
            String metadataHash = document.getDocumentInformation()
                    .getCustomMetadataValue("ERP-Final-Content-Hash");
            if (!contentHash.equalsIgnoreCase(metadataHash))
            {
                throw new ServiceException("待确认最终合同正文校验值缺失");
            }
            if (document.getNumberOfPages() < 1)
            {
                throw new ServiceException("待确认最终合同页数异常");
            }
            document.removePage(document.getNumberOfPages() - 1);
            document.getDocumentInformation().setCustomMetadataValue(
                    "ERP-Final-Root-Hash", finalRootHash);
            if (StringUtils.isNotBlank(signatureHash))
            {
                document.getDocumentInformation().setCustomMetadataValue(
                        "ERP-Employee-Signature-Hash", signatureHash);
            }
            if (StringUtils.isNotBlank(sealHash))
            {
                document.getDocumentInformation().setCustomMetadataValue(
                        "ERP-Company-Seal-Hash", sealHash);
            }
            // The employee has already opened the pending-final PDF whose stable body is bound
            // by contentHash/finalRootHash.  Never draw a newly collected signature into those
            // body pages during archive.  Even when a historical placement snapshot is present,
            // put the signature (and the frozen seal evidence) on the replaceable confirmation
            // page so the archived PDF visibly contains the evidence without changing the body.
            PDImageXObject appendedSignature = signaturePng == null ? null
                    : PDImageXObject.createFromByteArray(document, signaturePng,
                            "employee-signature.png");
            PDImageXObject appendedSeal = companySealPng == null ? null
                    : PDImageXObject.createFromByteArray(document, companySealPng,
                            "company-seal.png");
            appendFinalEvidencePage(document, signPackage, true, appendedSignature,
                    appendedSeal, employeeSignedTime, companySealTime,
                    finalConfirmedTime, contentHash, finalRootHash, signatureHash, sealHash);
            document.save(output, CompressParameters.NO_COMPRESSION);
            return output.toByteArray();
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (IOException e)
        {
            throw new ServiceException("生成最终归档证据页失败").setDetailMessage(e.getMessage());
        }
    }

    private void appendFinalEvidencePage(PDDocument document, OaSignPackage signPackage,
            boolean confirmed, PDImageXObject signatureImage, PDImageXObject sealImage,
            Date employeeSignedTime, Date companySealTime, Date finalConfirmedTime,
            String contentHash, String finalRootHash, String signatureHash, String sealHash)
            throws IOException
    {
        PDPage evidencePage = new PDPage(PDRectangle.A4);
        evidencePage.setResources(new PDResources());
        document.addPage(evidencePage);
        List<String> lines = finalEvidenceLines(signPackage, confirmed,
                employeeSignedTime, companySealTime, finalConfirmedTime,
                contentHash, finalRootHash, signatureHash, sealHash);
        PDFont font = loadEvidenceFont(document, lines);
        validateEvidencePageLayout(evidencePage, font, lines);
        try (PDPageContentStream content = new PDPageContentStream(document, evidencePage,
                AppendMode.OVERWRITE, true, true))
        {
            float y = evidencePage.getMediaBox().getHeight() - EVIDENCE_TEXT_TOP_MARGIN;
            content.beginText();
            content.setFont(font, EVIDENCE_FONT_SIZE);
            content.setLeading(EVIDENCE_TEXT_LEADING);
            content.newLineAtOffset(EVIDENCE_TEXT_X, y);
            for (String line : lines)
            {
                content.showText(line);
                content.newLine();
            }
            content.endText();
            if (signatureImage != null)
            {
                content.drawImage(signatureImage, 60, 95, 230, 86);
            }
            if (sealImage != null)
            {
                content.drawImage(sealImage, signatureImage == null ? 60 : 360, 82, 105, 105);
            }
        }
    }

    private List<String> finalEvidenceLines(OaSignPackage signPackage, boolean confirmed,
            Date employeeSignedTime, Date companySealTime, Date finalConfirmedTime,
            String contentHash, String finalRootHash, String signatureHash, String sealHash)
    {
        List<String> lines = new ArrayList<>();
        lines.add(confirmed ? "最终合同电子签署确认页" : "待最终确认版电子证据页");
        lines.add(confirmed
                ? "本页记录员工对当前版本合同的最终确认事实。"
                : "本文件尚未完成员工最终确认，不得作为最终归档件。");
        lines.add("签约包编号: " + displayPackageNo(signPackage.getPackageNo()));
        lines.add("员工姓名: " + truncateForDisplay(signPackage.getEmployeeNameSnapshot(), 20)
                + "，证件号码: " + maskIdCard(signPackage.getEmployeeIdCardSnapshot()));
        lines.add("用人法律主体: "
                + truncateForDisplay(signPackage.getLegalEntityNameSnapshot(), 32));
        lines.add(confirmed ? "员工最终确认: 已完成" : "员工最终确认: 待完成");
        if (StringUtils.isNotBlank(signatureHash) && employeeSignedTime != null)
        {
            lines.add("手写签名样本留存时间: " + formatTime(employeeSignedTime));
            lines.add("员工手写签名: 已留存于本确认页");
            addHashLines(lines, "员工签名 SHA-256", signatureHash);
        }
        if (StringUtils.isNotBlank(sealHash) && companySealTime != null)
        {
            lines.add("公司印章生成时间: " + formatTime(companySealTime));
            lines.add("公司印章证据: 已留存于本确认页");
            addHashLines(lines, "企业印章 SHA-256", sealHash);
        }
        if (confirmed)
        {
            lines.add("员工最终确认时间: " + formatTime(finalConfirmedTime));
            addHashLines(lines, "最终合同集合 SHA-256", finalRootHash);
        }
        addHashLines(lines, "稳定正文 SHA-256", contentHash);
        return lines;
    }

    private void validateFinalArchiveEvidenceInput(OaSignPackageDocument signDocument,
            byte[] signaturePng, byte[] companySealPng, Date employeeSignedTime,
            Date companySealTime, PdfImagePlacement signaturePlacement,
            PdfImagePlacement sealPlacement)
    {
        boolean signatureRequired = "Y".equalsIgnoreCase(signDocument.getEmployeeSignRequired());
        boolean sealRequired = "Y".equalsIgnoreCase(signDocument.getCompanySealRequired());
        if (signatureRequired && signaturePng == null)
        {
            throw new ServiceException("最终归档缺少员工手写签名证据");
        }
        if (sealRequired && companySealPng == null)
        {
            throw new ServiceException("最终归档缺少公司印章证据");
        }
        if (signaturePng == null)
        {
            if (employeeSignedTime != null || signaturePlacement != null)
            {
                throw new ServiceException("最终归档签名时间或定位缺少签名图片");
            }
        }
        else
        {
            OaSignImageValidator.requireSignaturePng(signaturePng);
            if (employeeSignedTime == null)
            {
                throw new ServiceException("最终归档缺少员工签名时间");
            }
        }
        if (companySealPng == null)
        {
            if (companySealTime != null || sealPlacement != null)
            {
                throw new ServiceException("最终归档盖章时间或定位缺少印章图片");
            }
        }
        else
        {
            OaSignImageValidator.requireCompanySealExtension(companySealPng);
            if (companySealTime == null)
            {
                throw new ServiceException("最终归档缺少公司盖章时间");
            }
        }
    }

    private void validateFinalArchiveEvidencePage(byte[] archiveBytes,
            String contentHash, String finalRootHash, String signatureHash, String sealHash)
    {
        try (PDDocument document = Loader.loadPDF(archiveBytes))
        {
            validateFinalArchiveEvidenceDocument(document, contentHash, finalRootHash,
                    signatureHash, sealHash);
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (IOException | RuntimeException e)
        {
            throw new ServiceException("校验最终归档确认页失败")
                    .setDetailMessage(e.getMessage());
        }
    }

    private void validateFinalArchiveEvidenceDocument(PDDocument document,
            String contentHash, String finalRootHash, String signatureHash, String sealHash)
            throws IOException
    {
        if (StringUtils.isBlank(contentHash) || StringUtils.isBlank(finalRootHash)
                || document == null || document.getNumberOfPages() < 1)
        {
            throw new ServiceException("最终归档正文或集合根校验值缺失");
        }
        String metadataContentHash = document.getDocumentInformation()
                .getCustomMetadataValue("ERP-Final-Content-Hash");
        String metadataRootHash = document.getDocumentInformation()
                .getCustomMetadataValue("ERP-Final-Root-Hash");
        if (!contentHash.equalsIgnoreCase(metadataContentHash)
                || !finalRootHash.equalsIgnoreCase(metadataRootHash))
        {
            throw new ServiceException("最终归档正文或集合根校验值缺失");
        }
        if (StringUtils.isNotBlank(signatureHash)
                && !signatureHash.equalsIgnoreCase(document.getDocumentInformation()
                        .getCustomMetadataValue("ERP-Employee-Signature-Hash")))
        {
            throw new ServiceException("最终归档员工签名校验值缺失");
        }
        if (StringUtils.isNotBlank(sealHash)
                && !sealHash.equalsIgnoreCase(document.getDocumentInformation()
                        .getCustomMetadataValue("ERP-Company-Seal-Hash")))
        {
            throw new ServiceException("最终归档公司印章校验值缺失");
        }
        validateCompletedFinalConfirmationPage(document);
        PDPage evidencePage = document.getPage(document.getNumberOfPages() - 1);
        int expectedImages = (StringUtils.isNotBlank(signatureHash) ? 1 : 0)
                + (StringUtils.isNotBlank(sealHash) ? 1 : 0);
        if (countDrawnImages(evidencePage) != expectedImages)
        {
            throw new ServiceException("最终归档确认页签名或印章图像证据不完整");
        }
    }

    /**
     * Proves that the removable last page is the one and only completed final-confirmation
     * page.  This check intentionally reads the page text rather than trusting metadata or
     * image counts, because those properties do not distinguish an ordinary body page or a
     * pending-confirmation evidence page from the completed legal confirmation page.
     */
    private void validateCompletedFinalConfirmationPage(PDDocument document) throws IOException
    {
        if (document == null || document.getNumberOfPages() < 2)
        {
            throw new ServiceException("最终归档缺少正文或最终确认页");
        }
        String expectedTitle = canonicalPositionText(FINAL_CONFIRMATION_PAGE_TITLE);
        String completedField = canonicalPositionText(FINAL_CONFIRMATION_COMPLETED_FIELD);
        String rootHashLabel = canonicalPositionText(FINAL_CONFIRMATION_ROOT_HASH_LABEL);
        int lastPageNumber = document.getNumberOfPages();
        List<StringBuilder> extractedPages = new ArrayList<>(lastPageNumber);
        for (int pageNumber = 0; pageNumber < lastPageNumber; pageNumber++)
        {
            extractedPages.add(new StringBuilder());
        }
        PDFTextStripper stripper = new PDFTextStripper()
        {
            @Override
            protected void writeString(String text, List<TextPosition> positions)
            {
                int pageIndex = getCurrentPageNo() - 1;
                if (pageIndex >= 0 && pageIndex < extractedPages.size())
                {
                    extractedPages.get(pageIndex).append(text);
                }
            }
        };
        stripper.setStartPage(1);
        stripper.setEndPage(lastPageNumber);
        stripper.getText(document);
        for (int pageNumber = 1; pageNumber <= lastPageNumber; pageNumber++)
        {
            String pageText = canonicalPositionText(
                    extractedPages.get(pageNumber - 1).toString());
            int titleCount = countOccurrences(pageText, expectedTitle);
            if (pageNumber < lastPageNumber && titleCount != 0)
            {
                throw new ServiceException("最终归档正文不得包含最终确认页标题");
            }
            if (pageNumber == lastPageNumber
                    && (titleCount != 1 || !pageText.contains(completedField)
                            || !pageText.contains(rootHashLabel)))
            {
                throw new ServiceException("最终归档最后一页不是唯一完整的最终确认页");
            }
        }
    }

    private int countDrawnImages(PDPage page) throws IOException
    {
        if (page.getResources() == null)
        {
            return 0;
        }
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
        int imageCount = 0;
        for (int index = 1; index < tokens.size(); index++)
        {
            Object token = tokens.get(index);
            if (token instanceof Operator operator && "Do".equals(operator.getName())
                    && tokens.get(index - 1) instanceof COSName imageName
                    && page.getResources().getXObject(imageName) instanceof PDImageXObject)
            {
                imageCount++;
            }
        }
        return imageCount;
    }

    private String formatTime(Date value)
    {
        if (value == null)
        {
            throw new ServiceException("证据时间不完整");
        }
        return SIGNED_TIME_FORMATTER.format(value.toInstant());
    }

    private int pdfPageCount(byte[] bytes)
    {
        try (PDDocument document = Loader.loadPDF(bytes))
        {
            return document.getNumberOfPages();
        }
        catch (IOException e)
        {
            throw new ServiceException("读取PDF页数失败").setDetailMessage(e.getMessage());
        }
    }

    private record PendingFinalPdf(byte[] pdfBytes, String contentHash) {}

    private void appendEvidencePage(PDDocument document, OaSignPackage signPackage,
            PDImageXObject signatureImage, PDImageXObject sealImage, Date employeeSignedTime,
            Date companySealTime, String confirmationText, String reviewPdfHash, String signatureHash,
            String sealHash)
            throws IOException
    {
        PDPage evidencePage = new PDPage(PDRectangle.A4);
        evidencePage.setResources(new PDResources());
        document.addPage(evidencePage);
        List<String> lines = evidenceLines(signPackage, employeeSignedTime, companySealTime,
                confirmationText,
                reviewPdfHash, signatureHash, sealHash);
        PDFont font = loadEvidenceFont(document, lines);
        validateEvidencePageLayout(evidencePage, font, lines);
        try (PDPageContentStream content = new PDPageContentStream(document, evidencePage,
                AppendMode.OVERWRITE, true, true))
        {
            float y = evidencePage.getMediaBox().getHeight() - EVIDENCE_TEXT_TOP_MARGIN;
            content.beginText();
            content.setFont(font, EVIDENCE_FONT_SIZE);
            content.setLeading(EVIDENCE_TEXT_LEADING);
            content.newLineAtOffset(EVIDENCE_TEXT_X, y);
            for (String line : lines)
            {
                content.showText(line);
                content.newLine();
            }
            content.endText();

            if (signatureImage != null)
            {
                content.drawImage(signatureImage, 60, 95, 230, 86);
            }
            if (sealImage != null)
            {
                content.drawImage(sealImage, signatureImage == null ? 60 : 360, 82, 105, 105);
            }
        }
    }

    private List<String> evidenceLines(OaSignPackage signPackage, Date employeeSignedTime,
            Date companySealTime, String confirmationText, String reviewPdfHash, String signatureHash,
            String sealHash)
    {
        List<String> lines = new ArrayList<>();
        boolean hasSignature = StringUtils.isNotBlank(signatureHash);
        boolean hasSeal = StringUtils.isNotBlank(sealHash);
        lines.add(hasSignature && hasSeal ? "最终合同电子签署确认页"
                : hasSignature ? "员工首次签名电子确认页" : "公司盖章电子确认页");
        lines.add("本页为该合同不可分割的电子签署确认页，以源PDF SHA-256与合同正文绑定。");
        lines.add("签约包编号: " + displayPackageNo(signPackage.getPackageNo()));
        lines.add("员工姓名: " + truncateForDisplay(signPackage.getEmployeeNameSnapshot(), 20)
                + "，证件号码: " + maskIdCard(signPackage.getEmployeeIdCardSnapshot()));
        lines.add("用人法律主体: "
                + truncateForDisplay(signPackage.getLegalEntityNameSnapshot(), 32));
        if (hasSignature)
        {
            lines.add("员工签名时间: "
                    + SIGNED_TIME_FORMATTER.format(employeeSignedTime.toInstant()));
        }
        if (hasSeal)
        {
            lines.add("公司盖章/最终生成时间: "
                    + SIGNED_TIME_FORMATTER.format(companySealTime.toInstant()));
        }
        lines.add("确认短语: " + truncateForDisplay(confirmationText, 32));
        addHashLines(lines, "源PDF SHA-256", reviewPdfHash);
        if (hasSignature)
        {
            lines.add("员工阅读确认: 已完成");
            lines.add("员工手写签名: 已留存");
            addHashLines(lines, "员工签名 SHA-256", signatureHash);
        }
        if (hasSeal)
        {
            lines.add("公司印章: 已加盖");
            addHashLines(lines, "企业印章 SHA-256", sealHash);
        }
        return lines;
    }

    private void addHashLines(List<String> lines, String label, String hash)
    {
        if (hash == null || !hash.matches("[0-9a-f]{64}"))
        {
            throw new ServiceException(label + "不合法");
        }
        lines.add(label + " (1/2): " + hash.substring(0, 32));
        lines.add(label + " (2/2): " + hash.substring(32));
    }

    private void validateEvidencePageLayout(PDPage evidencePage, PDFont font, List<String> lines)
            throws IOException
    {
        float availableWidth = evidencePage.getMediaBox().getWidth() - (2 * EVIDENCE_TEXT_X);
        for (String line : lines)
        {
            float lineWidth = font.getStringWidth(line) / 1000F * EVIDENCE_FONT_SIZE;
            if (!Float.isFinite(lineWidth) || lineWidth > availableWidth)
            {
                throw new ServiceException("电子签署确认页文本超出可用宽度");
            }
        }
        float firstBaseline = evidencePage.getMediaBox().getHeight() - EVIDENCE_TEXT_TOP_MARGIN;
        float lastBaseline = firstBaseline - Math.max(0, lines.size() - 1) * EVIDENCE_TEXT_LEADING;
        if (lastBaseline < EVIDENCE_IMAGE_TOP_Y + EVIDENCE_TEXT_IMAGE_GAP)
        {
            throw new ServiceException("电子签署确认页文本与签章区域重叠");
        }
    }

    private String displayPackageNo(String packageNo)
    {
        if (StringUtils.isBlank(packageNo))
        {
            return "-";
        }
        return packageNo.trim().replaceFirst("(?i)^SP[-_]*", "签约-");
    }

    private PDFont loadEvidenceFont(PDDocument document, List<String> lines) throws IOException
    {
        String requiredText = String.join("", lines);
        String configuredFont = System.getenv("SIGN_PACKAGE_PDF_FONT_PATH");
        if (StringUtils.isNotBlank(configuredFont))
        {
            Path configuredPath = Path.of(configuredFont).toAbsolutePath().normalize();
            if (!Files.isRegularFile(configuredPath))
            {
                throw new ServiceException("签署页字体文件不存在");
            }
            return PDType0Font.load(document, configuredPath.toFile());
        }
        for (String fontName : CJK_FONT_CANDIDATES)
        {
            try
            {
                TrueTypeFont trueTypeFont = FontMappers.instance().getTrueTypeFont(fontName, null).getFont();
                if (supports(trueTypeFont, requiredText))
                {
                    return PDType0Font.load(document, trueTypeFont, true);
                }
            }
            catch (RuntimeException | IOException ignored)
            {
                // Try the next configured system font; generation fails if none is usable.
            }
        }
        throw new ServiceException("缺少支持中文的签署页字体，请联系系统管理员配置");
    }

    private boolean supports(TrueTypeFont font, String text) throws IOException
    {
        if (font == null)
        {
            return false;
        }
        CmapLookup cmap = font.getUnicodeCmapLookup();
        if (cmap == null)
        {
            return false;
        }
        return text.codePoints().filter(codePoint -> !Character.isWhitespace(codePoint))
                .allMatch(codePoint -> cmap.getGlyphId(codePoint) != 0);
    }

    private void drawPlacedImage(PDDocument document, PDImageXObject image,
            PdfImagePlacement placement, String label) throws IOException
    {
        drawPlacedImage(document, image, placement, label, document.getNumberOfPages());
    }

    private void drawPlacedImage(PDDocument document, PDImageXObject image,
            PdfImagePlacement placement, String label, int maxPageNumber) throws IOException
    {
        if (placement != null && placement.isComposite())
        {
            for (PdfImagePlacement child : placement.expanded())
            {
                drawPlacedImage(document, image, child, label, maxPageNumber);
            }
            return;
        }
        int resolvedPageNumber = resolveAndValidatePlacementPage(document, placement, label,
                maxPageNumber);
        validatePlacementBounds(document, resolvedPageNumber, placement, label);
        PDPage page = document.getPage(resolvedPageNumber - 1);
        try (PDPageContentStream content = new PDPageContentStream(document, page,
                AppendMode.APPEND, true, true))
        {
            content.drawImage(image, placement.getX(), placement.getY(),
                    placement.getWidth(), placement.getHeight());
        }
    }

    private int resolveAndValidatePlacementPage(PDDocument document,
            PdfImagePlacement placement, String label)
    {
        return resolveAndValidatePlacementPage(document, placement, label,
                document.getNumberOfPages());
    }

    private int resolveAndValidatePlacementPage(PDDocument document,
            PdfImagePlacement placement, String label, int maxPageNumber)
    {
        if (placement == null)
        {
            throw new ServiceException(label + "坐标不能为空");
        }
        int pageCount = document.getNumberOfPages();
        Integer configuredPageNumber = placement.getPageNumber();
        int resolvedPageNumber;
        if (placement.isLastPage())
        {
            resolvedPageNumber = maxPageNumber;
        }
        else
        {
            if (configuredPageNumber == null)
            {
                throw new ServiceException(label + "页码不存在");
            }
            resolvedPageNumber = configuredPageNumber;
        }
        if (resolvedPageNumber <= 0 || resolvedPageNumber > pageCount
                || resolvedPageNumber > maxPageNumber)
        {
            throw new ServiceException(label + "页码不存在");
        }
        return resolvedPageNumber;
    }

    private boolean containsPlacedImage(PDDocument document, int bodyPageCount,
            PdfImagePlacement placement, byte[] expectedImage, String label)
    {
        if (placement == null)
        {
            return false;
        }
        List<PdfImagePlacement> allowedPlacements = placement.expanded();
        return allowedPlacements.stream().allMatch(child -> containsPlacedImageSingle(
                document, bodyPageCount, child, allowedPlacements, expectedImage, label));
    }

    private boolean containsPlacedImageSingle(PDDocument document, int bodyPageCount,
            PdfImagePlacement placement, List<PdfImagePlacement> allowedPlacements,
            byte[] expectedImage, String label)
    {
        int pageNumber = resolveAndValidatePlacementPage(document, placement, label,
                bodyPageCount);
        validatePlacementBounds(document, pageNumber, placement, label);
        PDPage page = document.getPage(pageNumber - 1);
        try
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
            boolean sameMatrixFound = false;
            int exactMatches = 0;
            for (int index = 6; index < tokens.size(); index++)
            {
                Object token = tokens.get(index);
                if (!(token instanceof Operator operator)
                        || !"Do".equals(operator.getName())
                        || !(tokens.get(index - 1) instanceof COSName imageName)
                        || page.getResources() == null
                        || !(page.getResources().getXObject(imageName) instanceof PDImageXObject image))
                {
                    continue;
                }
                float[] matrix = imageMatrixBefore(tokens, index);
                boolean pixelsMatch = expectedImage != null
                        && imagePixelsMatch(image, expectedImage);
                if (!matchesPlacementMatrix(matrix, placement))
                {
                    if (pixelsMatch && !isAllowedPlacementMatrix(document, bodyPageCount,
                            pageNumber, matrix, allowedPlacements, label))
                    {
                        throw new ServiceException("最终合同已有" + label + "位置证据不一致");
                    }
                    continue;
                }
                sameMatrixFound = true;
                if (pixelsMatch)
                {
                    exactMatches++;
                }
            }
            if (sameMatrixFound)
            {
                if (exactMatches > 1)
                {
                    throw new ServiceException("最终合同已有重复的" + label + "位置证据");
                }
                if (exactMatches == 0)
                {
                    throw new ServiceException("最终合同已有" + label + "位置证据不一致");
                }
            }
            return exactMatches == 1;
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (IOException | RuntimeException e)
        {
            throw new ServiceException("校验最终合同" + label + "位置失败")
                    .setDetailMessage(e.getMessage());
        }
    }

    private boolean isAllowedPlacementMatrix(PDDocument document, int bodyPageCount,
            int pageNumber, float[] matrix, List<PdfImagePlacement> placements, String label)
    {
        return placements.stream().anyMatch(candidate -> {
            int candidatePage = resolveAndValidatePlacementPage(document, candidate, label,
                    bodyPageCount);
            return candidatePage == pageNumber && matchesPlacementMatrix(matrix, candidate);
        });
    }

    private PdfImagePlacement missingPlacedImage(PDDocument document, int bodyPageCount,
            PdfImagePlacement placement, byte[] expectedImage, String label)
    {
        if (placement == null)
        {
            return null;
        }
        List<PdfImagePlacement> missing = new ArrayList<>();
        List<PdfImagePlacement> allowedPlacements = placement.expanded();
        for (PdfImagePlacement child : placement.expanded())
        {
            if (!containsPlacedImageSingle(document, bodyPageCount, child, allowedPlacements,
                    expectedImage, label))
            {
                missing.add(child);
            }
        }
        return withCompositePlacements(placement, missing);
    }

    private float[] imageMatrixBefore(List<Object> tokens, int operatorIndex)
    {
        for (int index = operatorIndex - 1; index >= 6; index--)
        {
            if (tokens.get(index) instanceof Operator operator && "cm".equals(operator.getName()))
            {
                float[] matrix = new float[6];
                for (int offset = 0; offset < matrix.length; offset++)
                {
                    Object number = tokens.get(index - 6 + offset);
                    if (!(number instanceof org.apache.pdfbox.cos.COSNumber cosNumber))
                    {
                        return null;
                    }
                    matrix[offset] = cosNumber.floatValue();
                }
                return matrix;
            }
        }
        return null;
    }

    private boolean matchesPlacementMatrix(float[] matrix, PdfImagePlacement placement)
    {
        if (matrix == null)
        {
            return false;
        }
        return close(matrix[0], placement.getWidth())
                && close(matrix[1], 0F)
                && close(matrix[2], 0F)
                && close(matrix[3], placement.getHeight())
                && close(matrix[4], placement.getX())
                && close(matrix[5], placement.getY());
    }

    private boolean close(float left, float right)
    {
        return Math.abs(left - right) < 0.01F;
    }

    private boolean imagePixelsMatch(PDImageXObject actual, byte[] expectedBytes)
            throws IOException
    {
        if (actual == null || expectedBytes == null || expectedBytes.length == 0)
        {
            return false;
        }
        BufferedImage expected;
        try (ByteArrayInputStream input = new ByteArrayInputStream(expectedBytes))
        {
            expected = ImageIO.read(input);
        }
        BufferedImage actualImage = actual.getImage();
        if (expected == null || actualImage == null
                || expected.getWidth() != actualImage.getWidth()
                || expected.getHeight() != actualImage.getHeight())
        {
            return false;
        }
        for (int y = 0; y < expected.getHeight(); y++)
        {
            for (int x = 0; x < expected.getWidth(); x++)
            {
                if (expected.getRGB(x, y) != actualImage.getRGB(x, y))
                {
                    return false;
                }
            }
        }
        return true;
    }

    private void validatePlacementBounds(PDDocument document, int resolvedPageNumber,
            PdfImagePlacement placement, String label)
    {
        PDRectangle visibleBox = document.getPage(resolvedPageNumber - 1).getCropBox();
        if (!Float.isFinite(placement.getX()) || !Float.isFinite(placement.getY())
                || !Float.isFinite(placement.getWidth()) || !Float.isFinite(placement.getHeight())
                || placement.getX() < visibleBox.getLowerLeftX()
                || placement.getY() < visibleBox.getLowerLeftY()
                || placement.getWidth() <= 0
                || placement.getHeight() <= 0
                || (double) placement.getX() + placement.getWidth() > visibleBox.getUpperRightX()
                || (double) placement.getY() + placement.getHeight() > visibleBox.getUpperRightY())
        {
            throw new ServiceException(label + "坐标越界");
        }
        for (PdfProtectedRegion region : placement.getProtectedRegions())
        {
            if (region.pageNumber() == resolvedPageNumber
                    && intersects(placement.getX(), placement.getY(), placement.getWidth(),
                            placement.getHeight(), region.x(), region.y(), region.width(),
                            region.height()))
            {
                throw new ServiceException(label + "坐标与受保护区域重叠");
            }
        }
    }

    private boolean intersects(float leftX, float leftY, float leftWidth, float leftHeight,
            float rightX, float rightY, float rightWidth, float rightHeight)
    {
        return Math.min(leftX + leftWidth, rightX + rightWidth) > Math.max(leftX, rightX)
                && Math.min(leftY + leftHeight, rightY + rightHeight) > Math.max(leftY, rightY);
    }

    private int verifySignedPdf(byte[] signedPdfBytes, Path reviewPdfPath, boolean appendedEvidencePage)
    {
        try (PDDocument source = Loader.loadPDF(reviewPdfPath.toFile());
                PDDocument signed = Loader.loadPDF(signedPdfBytes))
        {
            int expectedPages = source.getNumberOfPages() + (appendedEvidencePage ? 1 : 0);
            if (signed.getNumberOfPages() != expectedPages)
            {
                throw new ServiceException("已签文件页数校验失败");
            }
            return signed.getNumberOfPages();
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (IOException e)
        {
            throw new ServiceException("已签文件重新打开校验失败").setDetailMessage(e.getMessage());
        }
    }

    private void validateSigningRequest(OaSignPackage signPackage, OaSignPackageDocument signDocument,
            Path reviewPdfPath, byte[] signaturePng, byte[] companySealPng,
            Date employeeSignedTime, Date companySealTime, String confirmationText,
            PdfImagePlacement signaturePlacement, PdfImagePlacement sealPlacement)
    {
        validateDocumentRequest(signPackage, signDocument, reviewPdfPath);
        if (signaturePng == null && companySealPng == null)
        {
            throw new ServiceException("至少提供签名或企业章图片");
        }
        if (signaturePng == null)
        {
            if (signaturePlacement != null)
            {
                throw new ServiceException("签名图片不存在，不能配置签名坐标");
            }
        }
        else
        {
            OaSignImageValidator.requireSignaturePng(signaturePng);
            if (employeeSignedTime == null)
            {
                throw new ServiceException("员工签名时间不能为空");
            }
        }
        if (companySealPng == null)
        {
            if (sealPlacement != null)
            {
                throw new ServiceException("企业章图片不存在，不能配置盖章坐标");
            }
        }
        else
        {
            OaSignImageValidator.requireCompanySealExtension(companySealPng);
            if (companySealTime == null)
            {
                throw new ServiceException("公司盖章时间不能为空");
            }
        }
        if (signaturePng == null && employeeSignedTime != null)
        {
            throw new ServiceException("员工签名时间不能脱离签名证据");
        }
        if (companySealPng == null && companySealTime != null)
        {
            throw new ServiceException("公司盖章时间不能脱离印章证据");
        }
        if (employeeSignedTime != null && companySealTime != null
                && companySealTime.before(employeeSignedTime))
        {
            throw new ServiceException("公司盖章时间不能早于员工签名时间");
        }
        if (StringUtils.isBlank(confirmationText))
        {
            throw new ServiceException("确认短语不能为空");
        }
    }

    private void validateDocumentRequest(OaSignPackage signPackage,
            OaSignPackageDocument signDocument, Path reviewPdfPath)
    {
        if (signPackage == null || signPackage.getPackageId() == null || signPackage.getPackageId() <= 0)
        {
            throw new ServiceException("签约包编号不合法");
        }
        if (signDocument == null || signDocument.getDocumentId() == null || signDocument.getDocumentId() <= 0
                || !signPackage.getPackageId().equals(signDocument.getPackageId()))
        {
            throw new ServiceException("签约文档不属于当前签约包");
        }
        if (StringUtils.isBlank(signPackage.getDocumentVersion())
                || !signPackage.getDocumentVersion().equals(signDocument.getDocumentVersion()))
        {
            throw new ServiceException("签约文档版本不一致");
        }
        if (reviewPdfPath == null || !Files.isRegularFile(reviewPdfPath))
        {
            throw new ServiceException("员工阅读文件不存在");
        }
    }

    private void validateReviewPdfHash(OaSignPackageDocument signDocument, String reviewPdfHash)
    {
        if (StringUtils.isNotBlank(signDocument.getReviewPdfHash())
                && !reviewPdfHash.equalsIgnoreCase(signDocument.getReviewPdfHash()))
        {
            throw new ServiceException("员工阅读文件校验不一致，禁止生成最终文件");
        }
    }

    private byte[] readFile(Path path, String message)
    {
        try
        {
            return Files.readAllBytes(path);
        }
        catch (IOException e)
        {
            throw new ServiceException(message).setDetailMessage(e.getMessage());
        }
    }

    private String maskIdCard(String idCard)
    {
        if (StringUtils.isBlank(idCard) || idCard.length() < 10)
        {
            return "****";
        }
        return idCard.substring(0, 6) + "********" + idCard.substring(idCard.length() - 4);
    }

    private String sha256(byte[] bytes)
    {
        try
        {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("SHA-256不可用", e);
        }
    }

    private String truncateForDisplay(String value, int maxCodePoints)
    {
        if (value == null)
        {
            return "";
        }
        String normalized = value.trim();
        int codePointCount = normalized.codePointCount(0, normalized.length());
        if (codePointCount <= maxCodePoints)
        {
            return normalized;
        }
        int retainedCodePoints = Math.max(0, maxCodePoints - 1);
        int endIndex = normalized.offsetByCodePoints(0, retainedCodePoints);
        return normalized.substring(0, endIndex) + "…";
    }

    /** Only a successfully promoted, UUID-owned artifact is eligible for this rollback. */
    private void discardPromotedAfterFailure(StagedSignFile stagedFile, Throwable failure)
    {
        if (stagedFile == null || stagedFile.getArchivePath() == null) return;
        try
        {
            fileStorageService.discardUncommitted(
                    stagedFile.getArchiveRelativePath(), stagedFile.getFileHash());
        }
        catch (RuntimeException cleanupFailure)
        {
            failure.addSuppressed(cleanupFailure);
            log.error("SIGN_FILE_CLEANUP_PENDING operation={} archive={} expectedHash={}",
                    stagedFile.getStagingDirectory().getFileName(),
                    stagedFile.getArchiveRelativePath(), stagedFile.getFileHash(), cleanupFailure);
        }
    }

    private void cleanupQuietly(StagedSignFile stagedFile)
    {
        try
        {
            fileStorageService.cleanup(stagedFile);
        }
        catch (ServiceException ignored)
        {
            // Preserve the signing result; a scheduled staging sweep can retry cleanup.
        }
    }

    private void deleteQuietly(Path path)
    {
        if (path == null)
        {
            return;
        }
        try
        {
            Files.deleteIfExists(path);
        }
        catch (IOException ignored)
        {
            // A failed disposable-file cleanup is handled by the host temp-file policy.
        }
    }

    /**
     * Rewrites only text-show operations whose glyphs intersect an audited repair rectangle.
     * Removed glyphs are replaced with numeric TJ advances, preserving the layout of adjacent
     * text (for example the immutable signature date) without retaining searchable old text.
     */
    private static final class TextLayerRegionRewriter extends PDFGraphicsStreamEngine
    {
        private final PDDocument document;
        private final PDPage page;
        private final Map<String, Rectangle2D.Float> targets = new java.util.LinkedHashMap<>();
        private final Set<String> removedFields = new LinkedHashSet<>();
        private final List<GlyphDecision> glyphDecisions = new ArrayList<>();
        private ContentStreamWriter writer;
        private Point2D currentPoint;

        private TextLayerRegionRewriter(PDDocument document, PDPage page,
                List<PdfTextOverlay> overlays)
        {
            super(page);
            this.document = document;
            this.page = page;
            for (PdfTextOverlay overlay : overlays)
            {
                PdfTextPlacement value = overlay.placement();
                targets.put(overlay.field(), new Rectangle2D.Float(value.x(), value.y(),
                        value.width(), value.height()));
            }
        }

        private void rewrite() throws IOException
        {
            PDStream rewritten = new PDStream(document);
            try (OutputStream output = rewritten.createOutputStream(COSName.FLATE_DECODE))
            {
                writer = new ContentStreamWriter(output);
                processPage(page);
            }
            page.setContents(rewritten);
        }

        private Set<String> removedFields()
        {
            return Set.copyOf(removedFields);
        }

        @Override
        protected void processOperator(Operator operator, List<COSBase> operands)
                throws IOException
        {
            String name = operator.getName();
            if (OperatorName.DRAW_OBJECT.equals(name)
                    || OperatorName.BEGIN_INLINE_IMAGE.equals(name))
            {
                writeOriginal(operands, operator);
                return;
            }

            boolean textShow = OperatorName.SHOW_TEXT.equals(name)
                    || OperatorName.SHOW_TEXT_ADJUSTED.equals(name)
                    || OperatorName.SHOW_TEXT_LINE.equals(name)
                    || OperatorName.SHOW_TEXT_LINE_AND_SPACE.equals(name);
            glyphDecisions.clear();
            PDFont textFont = textShow ? getGraphicsState().getTextState().getFont() : null;
            super.processOperator(operator, operands);
            boolean intersectsTarget = textShow && glyphDecisions.stream()
                    .anyMatch(GlyphDecision::removed);
            if (!intersectsTarget)
            {
                writeOriginal(operands, operator);
                return;
            }
            if (textFont == null)
            {
                throw new IOException("Cannot redact a text operation without a font");
            }
            glyphDecisions.stream().flatMap(value -> value.fields().stream())
                    .forEach(removedFields::add);
            writeFilteredText(name, operands, textFont);
        }

        private void writeFilteredText(String operation, List<COSBase> operands, PDFont font)
                throws IOException
        {
            COSBase textOperand;
            if (OperatorName.SHOW_TEXT_LINE.equals(operation))
            {
                writeOperator(List.of(), OperatorName.NEXT_LINE);
                textOperand = operands.get(0);
            }
            else if (OperatorName.SHOW_TEXT_LINE_AND_SPACE.equals(operation))
            {
                writeOperator(List.of(operands.get(0)), OperatorName.SET_WORD_SPACING);
                writeOperator(List.of(operands.get(1)), OperatorName.SET_CHAR_SPACING);
                writeOperator(List.of(), OperatorName.NEXT_LINE);
                textOperand = operands.get(2);
            }
            else
            {
                textOperand = operands.get(0);
            }

            COSArray source = new COSArray();
            if (textOperand instanceof COSArray array)
            {
                source.addAll(array);
            }
            else if (textOperand instanceof COSString string)
            {
                source.add(string);
            }
            else
            {
                throw new IOException("Unsupported PDF text operand");
            }
            COSArray filtered = filterTextArray(source, font);
            writeOperator(List.of(filtered), OperatorName.SHOW_TEXT_ADJUSTED);
        }

        private COSArray filterTextArray(COSArray source, PDFont font) throws IOException
        {
            COSArray filtered = new COSArray();
            int decisionIndex = 0;
            for (COSBase part : source)
            {
                if (part instanceof COSNumber)
                {
                    filtered.add(part);
                    continue;
                }
                if (!(part instanceof COSString string))
                {
                    throw new IOException("Unsupported nested PDF text array value");
                }
                byte[] encoded = string.getBytes();
                try (InputStream input = new ByteArrayInputStream(encoded))
                {
                    ByteArrayOutputStream kept = new ByteArrayOutputStream();
                    int offset = 0;
                    while (input.available() > 0)
                    {
                        int before = input.available();
                        int code = font.readCode(input);
                        int length = before - input.available();
                        if (decisionIndex >= glyphDecisions.size())
                        {
                            throw new IOException("PDF text glyph audit count mismatch");
                        }
                        GlyphDecision decision = glyphDecisions.get(decisionIndex++);
                        if (decision.code() != code)
                        {
                            throw new IOException("PDF text glyph audit code mismatch");
                        }
                        if (decision.removed())
                        {
                            flushKeptString(filtered, kept, string.getForceHexForm());
                            filtered.add(new COSFloat(decision.tjAdjustment(length)));
                        }
                        else
                        {
                            kept.write(encoded, offset, length);
                        }
                        offset += length;
                    }
                    flushKeptString(filtered, kept, string.getForceHexForm());
                }
            }
            if (decisionIndex != glyphDecisions.size())
            {
                throw new IOException("PDF text glyph audit result mismatch");
            }
            return filtered;
        }

        private void flushKeptString(COSArray output, ByteArrayOutputStream kept,
                boolean forceHex)
        {
            if (kept.size() == 0)
            {
                return;
            }
            COSString value = new COSString(kept.toByteArray());
            value.setForceHexForm(forceHex);
            output.add(value);
            kept.reset();
        }

        private void writeOriginal(List<COSBase> operands, Operator operator) throws IOException
        {
            writer.writeTokens(operands);
            writer.writeToken(operator);
        }

        private void writeOperator(List<COSBase> operands, String operation) throws IOException
        {
            writer.writeTokens(operands);
            writer.writeToken(Operator.getOperator(operation));
        }

        @Override
        protected void showGlyph(Matrix textRenderingMatrix, PDFont font, int code,
                Vector displacement) throws IOException
        {
            Point2D.Float origin = textRenderingMatrix.transformPoint(0F, 0F);
            float horizontalAdvance = Math.max(0.15F, Math.abs(displacement.getX()));
            Point2D.Float advance = textRenderingMatrix.transformPoint(horizontalAdvance, 0F);
            Point2D.Float emTop = textRenderingMatrix.transformPoint(0F, 1F);
            float emHeight = (float) origin.distance(emTop);
            float minX = Math.min(origin.x, advance.x) - 0.75F;
            float maxX = Math.max(origin.x, advance.x) + 0.75F;
            float minY = Math.min(origin.y, emTop.y) - Math.max(1F, emHeight * 0.28F);
            float maxY = Math.max(origin.y, emTop.y) + Math.max(1F, emHeight * 0.08F);
            Rectangle2D.Float glyphBounds = new Rectangle2D.Float(minX, minY,
                    Math.max(1F, maxX - minX), Math.max(1F, maxY - minY));
            Set<String> fields = new LinkedHashSet<>();
            for (Map.Entry<String, Rectangle2D.Float> target : targets.entrySet())
            {
                if (target.getValue().intersects(glyphBounds)
                        || target.getValue().contains(origin))
                {
                    fields.add(target.getKey());
                }
            }
            var textState = getGraphicsState().getTextState();
            glyphDecisions.add(new GlyphDecision(code, displacement.getX(), displacement.getY(),
                    textState.getFontSize(), textState.getHorizontalScaling() / 100F,
                    textState.getCharacterSpacing(), textState.getWordSpacing(),
                    font.isVertical(), Set.copyOf(fields)));
        }

        @Override
        public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3)
        {
            currentPoint = p0;
        }

        @Override public void drawImage(PDImage pdImage) {}
        @Override public void clip(int windingRule) {}
        @Override public void moveTo(float x, float y) { currentPoint = new Point2D.Float(x, y); }
        @Override public void lineTo(float x, float y) { currentPoint = new Point2D.Float(x, y); }
        @Override public void curveTo(float x1, float y1, float x2, float y2,
                float x3, float y3) { currentPoint = new Point2D.Float(x3, y3); }
        @Override public Point2D getCurrentPoint() { return currentPoint; }
        @Override public void closePath() {}
        @Override public void endPath() { currentPoint = null; }
        @Override public void strokePath() { currentPoint = null; }
        @Override public void fillPath(int windingRule) { currentPoint = null; }
        @Override public void fillAndStrokePath(int windingRule) { currentPoint = null; }
        @Override public void shadingFill(COSName shadingName) {}

        private record GlyphDecision(int code, float displacementX, float displacementY,
                float fontSize, float horizontalScaling, float characterSpacing,
                float wordSpacing, boolean vertical, Set<String> fields)
        {
            private boolean removed()
            {
                return !fields.isEmpty();
            }

            private float tjAdjustment(int encodedLength) throws IOException
            {
                if (!Float.isFinite(fontSize) || fontSize == 0F
                        || !Float.isFinite(horizontalScaling) || horizontalScaling == 0F)
                {
                    throw new IOException("Invalid PDF text state while redacting glyphs");
                }
                float word = encodedLength == 1 && code == 32 ? wordSpacing : 0F;
                if (vertical)
                {
                    float advance = displacementY * fontSize + characterSpacing + word;
                    return -advance * 1000F / fontSize;
                }
                float advance = (displacementX * fontSize + characterSpacing + word)
                        * horizontalScaling;
                return -advance * 1000F / (fontSize * horizontalScaling);
            }
        }
    }

    public record FinalExportPreparation(Path path, boolean derived) {}

    public record FinalExportAuditMetadata(String configVersion, String profileId,
            String repairId, String legalRepresentativeSource,
            String legalRepresentativeEvidence, List<String> repairFields) {}

    public record PdfTextOverlay(String field, String value, PdfTextPlacement placement) {}

    public record PdfTextPlacement(String field, int pageNumber, float x, float y,
            float width, float height, float fontSize,
            List<PdfProtectedRegion> protectedRegions)
    {
        public PdfTextPlacement
        {
            protectedRegions = protectedRegions == null ? List.of() : List.copyOf(protectedRegions);
        }
    }

    public static final class PdfImagePlacement
    {
        private final Integer pageNumber;
        private final boolean lastPage;
        private final float x;
        private final float y;
        private final float width;
        private final float height;
        private final List<PdfImagePlacement> placements;
        private final List<PdfProtectedRegion> protectedRegions;

        public PdfImagePlacement(int pageNumber, float x, float y, float width, float height)
        {
            this(pageNumber, false, x, y, width, height, List.of());
        }

        public PdfImagePlacement(int pageNumber, float x, float y, float width, float height,
                List<PdfProtectedRegion> protectedRegions)
        {
            this(pageNumber, false, x, y, width, height, protectedRegions);
        }

        private PdfImagePlacement(Integer pageNumber, boolean lastPage,
                float x, float y, float width, float height,
                List<PdfProtectedRegion> protectedRegions)
        {
            this(pageNumber, lastPage, x, y, width, height, protectedRegions, List.of());
        }

        private PdfImagePlacement(Integer pageNumber, boolean lastPage,
                float x, float y, float width, float height,
                List<PdfProtectedRegion> protectedRegions,
                List<PdfImagePlacement> placements)
        {
            this.pageNumber = pageNumber;
            this.lastPage = lastPage;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.placements = placements == null ? List.of() : List.copyOf(placements);
            this.protectedRegions = protectedRegions == null ? List.of()
                    : List.copyOf(protectedRegions);
        }

        public static PdfImagePlacement lastPage(float x, float y, float width, float height)
        {
            return lastPage(x, y, width, height, List.of());
        }

        public static PdfImagePlacement lastPage(float x, float y, float width, float height,
                List<PdfProtectedRegion> protectedRegions)
        {
            return new PdfImagePlacement(null, true, x, y, width, height, protectedRegions);
        }

        public static PdfImagePlacement composite(List<PdfImagePlacement> placements)
        {
            if (placements == null || placements.isEmpty()
                    || placements.stream().anyMatch(PdfImagePlacement::isComposite))
            {
                throw new IllegalArgumentException("签章位置集合不能为空且不得嵌套");
            }
            return new PdfImagePlacement(null, false, 0F, 0F, 0F, 0F,
                    List.of(), placements);
        }

        public boolean isComposite() { return !placements.isEmpty(); }

        public List<PdfImagePlacement> expanded()
        {
            return isComposite() ? placements : List.of(this);
        }

        public Integer getPageNumber() { return pageNumber; }
        public boolean isLastPage() { return lastPage; }
        public float getX() { return x; }
        public float getY() { return y; }
        public float getWidth() { return width; }
        public float getHeight() { return height; }
        public List<PdfProtectedRegion> getProtectedRegions() { return protectedRegions; }
    }

    public record PdfProtectedRegion(int pageNumber, float x, float y,
            float width, float height) {}

    private static PdfImagePlacement withCompositePlacements(PdfImagePlacement source,
            List<PdfImagePlacement> placements)
    {
        if (placements == null || placements.isEmpty())
        {
            return null;
        }
        if (!source.isComposite() && placements.size() == 1
                && placements.get(0) == source)
        {
            return source;
        }
        return PdfImagePlacement.composite(placements);
    }
}
