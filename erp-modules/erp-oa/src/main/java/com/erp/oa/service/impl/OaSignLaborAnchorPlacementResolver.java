package com.erp.oa.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;

/**
 * Resolves the labor-contract signing rectangles from unique local anchors in the actual PDF.
 *
 * <p>The template source hash decides whether this resolver may run.  The resolver then treats
 * every generated or historical PDF independently: labels, blank signature lines, page count and
 * protected identity/date rows must all be unique and geometrically consistent.  No page number
 * is assumed up front, so legitimate pagination caused by long fields cannot move a signature to
 * an unrelated page.</p>
 */
final class OaSignLaborAnchorPlacementResolver
{
    static final String PROFILE_ID = "labor-v7-anchor-relative-v1";

    private static final float PRIMARY_SIGNATURE_WIDTH = 90F;
    private static final float PRIMARY_SIGNATURE_HEIGHT = 42.3F;
    private static final float ATTACHMENT_SIGNATURE_WIDTH = 76F;
    private static final float ATTACHMENT_SIGNATURE_HEIGHT = 35.8F;
    private static final float DORMITORY_SIGNATURE_WIDTH = 78F;
    private static final float DORMITORY_SIGNATURE_HEIGHT = 36.7F;
    private static final float CONFIRMATION_SIGNATURE_WIDTH = 45F;
    private static final float CONFIRMATION_SIGNATURE_HEIGHT = 16.9F;
    private static final float SEAL_WIDTH = 62F;
    private static final float SEAL_HEIGHT = 61F;

    OaSignLaborPlacementProfileRegistry.PlacementProfile resolve(Path pdfPath,
            boolean archiveWithConfirmationPage, String expectedLegalRepresentative,
            Boolean expectedHandbookIncluded, boolean includeHistoricalTextOverlays)
    {
        if (pdfPath == null || !Files.isRegularFile(pdfPath))
        {
            throw new ServiceException("劳动合同锚点解析源PDF不存在");
        }
        try (PDDocument document = Loader.loadPDF(pdfPath.toFile()))
        {
            int bodyPageCount = document.getNumberOfPages()
                    - (archiveWithConfirmationPage ? 1 : 0);
            if (bodyPageCount < 1)
            {
                throw new ServiceException("劳动合同正文页数不正确");
            }
            for (int index = 0; index < bodyPageCount; index++)
            {
                if (Math.floorMod(document.getPage(index).getRotation(), 360) != 0)
                {
                    throw new ServiceException("劳动合同包含旋转页，不能安全定位签章");
                }
            }

            PositionCollector collector = new PositionCollector(bodyPageCount);
            collector.setSortByPosition(true);
            collector.getText(document);
            List<PageText> pages = collector.pages(document, bodyPageCount);

            Match primarySealLabel = unique(pages, "甲方盖章", "甲方盖章锚点");
            Match primarySignatureLabel = unique(pages, "乙方签名", "乙方签名锚点");
            requireSameLine(primarySealLabel, primarySignatureLabel, "合同首页签署行");
            int primaryPage = primarySealLabel.pageNumber();
            Match sealBlank = uniqueBlankAfter(pages.get(primaryPage - 1), primarySealLabel,
                    "甲方盖章空白栏");
            Match primarySignatureBlank = uniqueBlankAfter(pages.get(primaryPage - 1),
                    primarySignatureLabel, "乙方签名空白栏");

            Match legalRepresentativeLabel = uniqueOnPage(pages.get(primaryPage - 1),
                    "甲方代表", "甲方代表锚点");
            Match attachmentChecklist = uniqueOnPage(pages.get(primaryPage - 1),
                    "附件清单", "附件清单锚点");
            Match legalRepresentativeDate = uniqueRepresentativeDate(
                    pages.get(primaryPage - 1), legalRepresentativeLabel,
                    attachmentChecklist);
            boolean representativeAlreadyRendered = includeHistoricalTextOverlays
                    && StringUtils.isNotBlank(expectedLegalRepresentative)
                    && renderedRepresentativeCount(pages.get(primaryPage - 1),
                            legalRepresentativeLabel, legalRepresentativeDate,
                            expectedLegalRepresentative) == 1;
            // The phrase also appears in the attachment checklist.  Bind the section
            // boundary to the actual appendix heading ("附件 1" + title) so the
            // checklist entry can never be mistaken for the body anchor.
            Match dormitoryTitle = unique(pages, "附件1职工宿舍免责协议书",
                    "宿舍协议正文标题锚点");
            requireAfter(dormitoryTitle, attachmentChecklist, "宿舍协议标题锚点");
            Match confirmationLabel = unique(pages, "确认人", "岗位确认人锚点");
            requireAfter(confirmationLabel, dormitoryTitle, "岗位确认人锚点");
            Match identityLabel = uniqueOnPage(pages.get(confirmationLabel.pageNumber() - 1),
                    "身份证号码", "岗位确认身份证锚点");
            requireAfter(identityLabel, confirmationLabel, "岗位确认身份证锚点");

            List<SignatureBlank> allSignatureBlanks = signatureBlanks(pages);
            SignatureBlank attachmentSignature = uniqueSignatureBlank(
                    allSignatureBlanks.stream()
                            .filter(value -> !sameBounds(value.blank(), primarySignatureBlank))
                            .filter(value -> after(value.label(), attachmentChecklist))
                            .filter(value -> before(value.label(), dormitoryTitle))
                            .toList(), "劳动合同附件签名栏");

            SignatureBlank dormitorySignature = uniqueSignatureBlank(
                    allSignatureBlanks.stream()
                            .filter(value -> after(value.label(), dormitoryTitle))
                            .filter(value -> before(value.label(), confirmationLabel))
                            .toList(), "宿舍协议签名栏");
            SignatureBlank confirmationSignature = uniqueSignatureBlank(
                    allSignatureBlanks.stream()
                            .filter(value -> value.blank().pageNumber()
                                    == confirmationLabel.pageNumber())
                            .filter(value -> after(value.label(), confirmationLabel))
                            .filter(value -> before(value.label(), identityLabel))
                            .toList(), "岗位确认签名栏");

            Match dormitoryDate = uniqueDateRightOf(pages.get(
                    dormitorySignature.blank().pageNumber() - 1), dormitorySignature.blank(),
                    "宿舍协议日期栏");
            Match confirmationDate = uniqueDateBelow(pages.get(
                    confirmationLabel.pageNumber() - 1), identityLabel, "岗位确认日期栏");

            Match handbookLabel = uniqueHandbookChecklistMatch(
                    pages.get(primaryPage - 1), attachmentChecklist,
                    attachmentSignature.blank());
            if (expectedHandbookIncluded != null)
            {
                assertChecklistMark(pages.get(primaryPage - 1), handbookLabel,
                        expectedHandbookIncluded);
            }
            if (!includeHistoricalTextOverlays)
            {
                if (StringUtils.isBlank(expectedLegalRepresentative))
                {
                    throw new ServiceException("冻结甲方代表缺失");
                }
                assertRenderedRepresentative(pages.get(primaryPage - 1),
                        legalRepresentativeLabel, legalRepresentativeDate,
                        expectedLegalRepresentative);
            }

            OaSignLaborPlacementProfileRegistry.PlacementRect confirmationTableBoundary =
                    confirmationTableRightBoundary(document,
                            pages.get(confirmationLabel.pageNumber() - 1),
                            confirmationLabel, confirmationSignature.blank(), identityLabel,
                            confirmationDate);
            List<OaSignLaborPlacementProfileRegistry.PlacementRect> protectedRegions = List.of(
                    protectedSegment(document, pages, dormitoryDate, 5F, 8F),
                    protectedSegment(document, pages, identityLabel, 5F, 8F),
                    protectedSegment(document, pages, confirmationDate, 5F, 8F),
                    confirmationTableBoundary);

            List<OaSignLaborPlacementProfileRegistry.PlacementRect> signatures = List.of(
                    placement(document, primarySignatureBlank, -2.4F, 11.3F,
                            PRIMARY_SIGNATURE_WIDTH, PRIMARY_SIGNATURE_HEIGHT),
                    placement(document, attachmentSignature.blank(), -2F, 8.8F,
                            ATTACHMENT_SIGNATURE_WIDTH, ATTACHMENT_SIGNATURE_HEIGHT),
                    placement(document, dormitorySignature.blank(), -3.5F, 8.4F,
                            DORMITORY_SIGNATURE_WIDTH, DORMITORY_SIGNATURE_HEIGHT),
                    placement(document, confirmationSignature.blank(), 0.5F, 12F,
                            CONFIRMATION_SIGNATURE_WIDTH, CONFIRMATION_SIGNATURE_HEIGHT));
            List<OaSignLaborPlacementProfileRegistry.PlacementRect> seals = List.of(
                    placement(document, sealBlank, 6F, 19.1F, SEAL_WIDTH, SEAL_HEIGHT));

            List<OaSignLaborPlacementProfileRegistry.TextRect> textOverlays = new ArrayList<>();
            if (includeHistoricalTextOverlays)
            {
                if (!representativeAlreadyRendered)
                {
                    textOverlays.add(representativeOverlay(document,
                            pages.get(primaryPage - 1), legalRepresentativeLabel,
                            legalRepresentativeDate));
                }
                textOverlays.add(handbookOverlay(document,
                        pages.get(primaryPage - 1), handbookLabel));
                OaSignLaborPlacementProfileRegistry.TextRect notice =
                        archiveEvidenceNoticeOverlay(document, pages, primaryPage);
                if (notice != null)
                {
                    textOverlays.add(notice);
                }
            }

            validateTargets(document, bodyPageCount, signatures, seals, textOverlays,
                    protectedRegions);
            List<OaSignLaborPlacementProfileRegistry.AnchorRect> anchors = List.of(
                    anchor(document, lineBounds(pages.get(primaryPage - 1),
                            primarySealLabel), "primary-signing-row",
                            List.of("甲方盖章", "乙方签名")),
                    anchor(document, lineBounds(pages.get(primaryPage - 1),
                            legalRepresentativeLabel), "legal-representative-row",
                            List.of("甲方代表", "日期")),
                    anchor(document, attachmentChecklist, "attachment-checklist",
                            List.of("附件清单")),
                    anchor(document, handbookLabel, "handbook-row", List.of("员工手册")),
                    anchor(document, lineBounds(pages.get(
                            attachmentSignature.blank().pageNumber() - 1),
                            attachmentSignature.blank()), "attachment-signature-row",
                            List.of("签名", "日期")),
                    anchor(document, lineBounds(pages.get(
                            dormitorySignature.blank().pageNumber() - 1),
                            dormitorySignature.blank()), "dormitory-signature-row",
                            List.of("签名", "日期")),
                    anchor(document, lineBounds(pages.get(confirmationLabel.pageNumber() - 1),
                            confirmationLabel), "position-confirmation-row",
                            List.of("确认人", "签名", "身份证号码", "日期")));

            return new OaSignLaborPlacementProfileRegistry.PlacementProfile(PROFILE_ID,
                    bodyPageCount, signatures, seals, protectedRegions, textOverlays, anchors);
        }
        catch (ServiceException failure)
        {
            throw failure;
        }
        catch (IOException | RuntimeException failure)
        {
            throw new ServiceException("劳动合同唯一锚点定位失败")
                    .setDetailMessage(failure.getMessage());
        }
    }

    boolean sameGeometry(OaSignLaborPlacementProfileRegistry.PlacementProfile left,
            OaSignLaborPlacementProfileRegistry.PlacementProfile right)
    {
        return left != null && right != null
                && Objects.equals(left.expectedBodyPageCount(), right.expectedBodyPageCount())
                && sameRects(left.signaturePlacements(), right.signaturePlacements())
                && sameRects(left.sealPlacements(), right.sealPlacements())
                && sameRects(left.protectedRegions(), right.protectedRegions())
                && sameTextRects(left.textOverlays(), right.textOverlays());
    }

    private boolean sameRects(List<? extends OaSignLaborPlacementProfileRegistry.Rect> left,
            List<? extends OaSignLaborPlacementProfileRegistry.Rect> right)
    {
        if (left == null || right == null || left.size() != right.size())
        {
            return false;
        }
        for (int index = 0; index < left.size(); index++)
        {
            if (!sameRect(left.get(index), right.get(index)))
            {
                return false;
            }
        }
        return true;
    }

    private boolean sameTextRects(List<OaSignLaborPlacementProfileRegistry.TextRect> left,
            List<OaSignLaborPlacementProfileRegistry.TextRect> right)
    {
        if (left == null || right == null || left.size() != right.size())
        {
            return false;
        }
        for (int index = 0; index < left.size(); index++)
        {
            OaSignLaborPlacementProfileRegistry.TextRect a = left.get(index);
            OaSignLaborPlacementProfileRegistry.TextRect b = right.get(index);
            if (!Objects.equals(a.field(), b.field()) || !sameRect(a, b)
                    || !near(a.fontSize(), b.fontSize()))
            {
                return false;
            }
        }
        return true;
    }

    private boolean sameRect(OaSignLaborPlacementProfileRegistry.Rect left,
            OaSignLaborPlacementProfileRegistry.Rect right)
    {
        return Objects.equals(left.pageNumber(), right.pageNumber())
                && near(left.x(), right.x()) && near(left.y(), right.y())
                && near(left.width(), right.width()) && near(left.height(), right.height());
    }

    private boolean near(Float left, Float right)
    {
        return left != null && right != null && Math.abs(left - right) <= 0.6F;
    }

    private List<SignatureBlank> signatureBlanks(List<PageText> pages)
    {
        List<SignatureBlank> result = new ArrayList<>();
        for (PageText page : pages)
        {
            for (Match label : page.find("签名"))
            {
                List<Match> blanks = blanksAfter(page, label);
                if (blanks.size() == 1)
                {
                    SignatureBlank value = new SignatureBlank(label, blanks.get(0));
                    if (result.stream().noneMatch(existing -> sameBounds(existing.blank(),
                            value.blank())))
                    {
                        result.add(value);
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    private SignatureBlank uniqueSignatureBlank(List<SignatureBlank> matches, String label)
    {
        if (matches.size() != 1)
        {
            throw new ServiceException(label + "必须唯一，实际命中" + matches.size() + "处");
        }
        return matches.get(0);
    }

    private Match uniqueBlankAfter(PageText page, Match label, String description)
    {
        List<Match> matches = blanksAfter(page, label);
        if (matches.size() != 1)
        {
            throw new ServiceException(description + "必须唯一，实际命中" + matches.size()
                    + "处");
        }
        return matches.get(0);
    }

    private List<Match> blanksAfter(PageText page, Match label)
    {
        return page.find("____________").stream()
                .filter(blank -> sameLine(blank, label))
                .filter(blank -> blank.left() >= label.right() - 1F)
                .filter(blank -> blank.left() <= label.right() + 22F)
                .toList();
    }

    private Match unique(List<PageText> pages, String phrase, String label)
    {
        List<Match> matches = pages.stream().flatMap(page -> page.find(phrase).stream()).toList();
        if (matches.size() != 1)
        {
            throw new ServiceException(label + "必须唯一，实际命中" + matches.size() + "处");
        }
        return matches.get(0);
    }

    private Match uniqueOnPage(PageText page, String phrase, String label)
    {
        List<Match> matches = page.find(phrase);
        if (matches.size() != 1)
        {
            throw new ServiceException(label + "必须唯一，实际命中" + matches.size() + "处");
        }
        return matches.get(0);
    }

    private Match uniqueDateRightOf(PageText page, Match blank, String label)
    {
        List<Match> matches = page.find("日期").stream()
                .filter(value -> sameLine(value, blank))
                .filter(value -> value.left() > blank.right() + 5F).toList();
        if (matches.size() != 1)
        {
            throw new ServiceException(label + "必须唯一，实际命中" + matches.size() + "处");
        }
        return matches.get(0);
    }

    private Match uniqueRepresentativeDate(PageText page, Match label, Match nextSection)
    {
        List<Match> matches = page.find("日期").stream()
                .filter(value -> value.top() >= label.top() - 3F)
                .filter(value -> value.top() < nextSection.top() - 3F)
                .filter(value -> value.top() > label.top() + 3F
                        || value.left() > label.right() + 5F)
                .toList();
        if (matches.size() != 1)
        {
            throw new ServiceException("甲方代表日期栏必须唯一，实际命中"
                    + matches.size() + "处");
        }
        return matches.get(0);
    }

    private Match uniqueDateBelow(PageText page, Match identity, String label)
    {
        List<Match> matches = page.find("日期").stream()
                .filter(value -> value.top() > identity.top() + 3F)
                .sorted(Comparator.comparingDouble(value -> value.top() - identity.top()))
                .toList();
        if (matches.isEmpty())
        {
            throw new ServiceException(label + "缺失");
        }
        if (matches.size() > 1
                && Math.abs(matches.get(0).top() - matches.get(1).top()) < 2F)
        {
            throw new ServiceException(label + "不唯一");
        }
        return matches.get(0);
    }

    private Match uniqueHandbookChecklistMatch(PageText page, Match checklist,
            Match attachmentSignature)
    {
        List<Match> matches = page.find("员工手册").stream()
                .filter(value -> value.top() > checklist.top())
                .filter(value -> attachmentSignature.pageNumber() > page.pageNumber()
                        || value.top() < attachmentSignature.top()).toList();
        if (matches.size() != 1)
        {
            throw new ServiceException("员工手册附件项锚点必须唯一，实际命中"
                    + matches.size() + "处");
        }
        return matches.get(0);
    }

    private void assertChecklistMark(PageText page, Match handbook, boolean included)
    {
        String expected = included ? "☑" : "□";
        String opposite = included ? "□" : "☑";
        List<ChecklistMark> localMarks = new ArrayList<>();
        page.find(expected).stream()
                .filter(value -> sameLine(value, handbook))
                .filter(value -> value.right() <= handbook.left() + 1F)
                .filter(value -> value.left() >= handbook.left() - 60F)
                .map(value -> new ChecklistMark(expected, value))
                .forEach(localMarks::add);
        page.find(opposite).stream()
                .filter(value -> sameLine(value, handbook))
                .filter(value -> value.right() <= handbook.left() + 1F)
                .filter(value -> value.left() >= handbook.left() - 60F)
                .map(value -> new ChecklistMark(opposite, value))
                .forEach(localMarks::add);
        localMarks.sort(Comparator.comparingDouble(
                (ChecklistMark value) -> value.match().right()).reversed());
        if (localMarks.size() != 1 || !expected.equals(localMarks.get(0).value()))
        {
            throw new ServiceException("劳动合同员工手册附件勾选未落入核准局部区域");
        }
    }

    private void assertRenderedRepresentative(PageText page, Match label, Match date,
            String expected)
    {
        if (StringUtils.isBlank(canonical(expected)))
        {
            throw new ServiceException("冻结甲方代表缺失");
        }
        if (renderedRepresentativeCount(page, label, date, expected) != 1)
        {
            throw new ServiceException("冻结甲方代表未落入劳动合同签署栏");
        }
    }

    private int renderedRepresentativeCount(PageText page, Match label, Match date,
            String expected)
    {
        String canonicalExpected = canonical(expected);
        if (StringUtils.isBlank(canonicalExpected))
        {
            return 0;
        }
        List<Match> matches = page.findCanonical(canonicalExpected).stream()
                .filter(value -> value.startIndex() >= label.endIndex())
                .filter(value -> value.endIndex() <= date.startIndex())
                .filter(value -> renderedRepresentativeInsideField(page, value, label, date))
                .toList();
        if (matches.size() > 1)
        {
            throw new ServiceException("冻结甲方代表在劳动合同签署栏命中多处");
        }
        return matches.size();
    }

    private boolean renderedRepresentativeInsideField(PageText page, Match value,
            Match label, Match date)
    {
        List<Glyph> glyphs = page.glyphs().subList(value.startIndex(), value.endIndex());
        if (glyphs.isEmpty())
        {
            return false;
        }
        Glyph first = glyphs.get(0);
        if (Math.abs(first.top() - label.top()) > 3F
                || first.left() < label.right() - 1F)
        {
            return false;
        }
        for (Glyph glyph : glyphs)
        {
            if (glyph.top() < label.top() - 3F || glyph.bottom() > date.bottom() + 3F)
            {
                return false;
            }
            if (Math.abs(glyph.top() - label.top()) <= 3F
                    && glyph.left() < label.right() - 1F)
            {
                return false;
            }
            if (Math.abs(glyph.top() - date.top()) <= 3F
                    && glyph.right() >= date.left() - 1F)
            {
                return false;
            }
        }
        return true;
    }

    private OaSignLaborPlacementProfileRegistry.PlacementRect placement(PDDocument document,
            Match blank, float xOffset, float topOffset, float width, float height)
    {
        PDRectangle box = document.getPage(blank.pageNumber() - 1).getCropBox();
        float x = round(box.getLowerLeftX() + blank.left() + xOffset);
        float y = round(box.getLowerLeftY() + box.getHeight() - blank.top() - height
                + topOffset);
        return new OaSignLaborPlacementProfileRegistry.PlacementRect(blank.pageNumber(), x, y,
                width, height);
    }

    private OaSignLaborPlacementProfileRegistry.TextRect representativeOverlay(
            PDDocument document, PageText page, Match label, Match date)
    {
        List<Glyph> segment = page.glyphs().stream()
                .filter(glyph -> Math.abs(glyph.top() - label.top()) <= 3F)
                .filter(glyph -> glyph.left() >= label.left() - 2F)
                .filter(glyph -> glyph.right() < date.left() - 1F).toList();
        Match bounds = bounds(label.pageNumber(), segment,
                label.startIndex(), label.endIndex());
        return fullLineTextOverlay(document, bounds, "companyLegalRepresentative", 12F);
    }

    private OaSignLaborPlacementProfileRegistry.TextRect handbookOverlay(PDDocument document,
            PageText page, Match handbook)
    {
        return fullLineTextOverlay(document, lineBounds(page, handbook),
                "attachmentHandbookMark", 12F);
    }

    private OaSignLaborPlacementProfileRegistry.TextRect fullLineTextOverlay(
            PDDocument document, Match line, String field, float fontSize)
    {
        PDRectangle box = document.getPage(line.pageNumber() - 1).getCropBox();
        float horizontalMargin = 2F;
        float verticalMargin = 3F;
        float x = round(box.getLowerLeftX() + Math.max(0F, line.left() - horizontalMargin));
        float y = round(box.getLowerLeftY() + box.getHeight() - line.bottom()
                - verticalMargin);
        float width = round(Math.min(box.getUpperRightX() - x,
                line.width() + horizontalMargin * 2F));
        return new OaSignLaborPlacementProfileRegistry.TextRect(field, line.pageNumber(), x, y,
                width, round(line.height() + verticalMargin * 2F), fontSize);
    }

    private OaSignLaborPlacementProfileRegistry.TextRect archiveEvidenceNoticeOverlay(
            PDDocument document, List<PageText> pages, int primaryPage)
    {
        PageText page = pages.get(primaryPage - 1);
        List<Match> legacy = page.find(
                "签署时间及文件校验信息见本合同末页《电子签署确认页》");
        if (legacy.size() > 1)
        {
            throw new ServiceException("历史电子签署确认页引用锚点不唯一");
        }
        if (legacy.isEmpty())
        {
            List<Match> current = page.find(
                    "签署完成后可在系统查看签署凭证及文件校验信息");
            if (current.size() != 1)
            {
                throw new ServiceException("签署校验说明锚点必须唯一，实际命中"
                        + current.size() + "处");
            }
            return null;
        }
        Match notice = legacy.get(0);
        Match line = lineBounds(page, notice);
        PDRectangle box = document.getPage(primaryPage - 1).getCropBox();
        float x = round(box.getLowerLeftX() + Math.max(0F, line.left() - 2F));
        float y = round(box.getLowerLeftY() + box.getHeight() - line.bottom() - 3F);
        float width = round(Math.min(box.getUpperRightX() - x - 28F,
                Math.max(260F, line.width() + 8F)));
        return new OaSignLaborPlacementProfileRegistry.TextRect(
                "archiveEvidenceNotice", primaryPage, x, y, width,
                round(line.height() + 7F), 8.5F);
    }

    private OaSignLaborPlacementProfileRegistry.PlacementRect protectedSegment(PDDocument document,
            List<PageText> pages, Match anchor, float horizontalMargin, float verticalMargin)
    {
        List<Glyph> segment = pages.get(anchor.pageNumber() - 1).glyphs().stream()
                .filter(glyph -> Math.abs(glyph.top() - anchor.top()) <= 3F)
                .filter(glyph -> glyph.right() >= anchor.left() - 1F).toList();
        Match line = bounds(anchor.pageNumber(), segment,
                anchor.startIndex(), anchor.endIndex());
        PDRectangle box = document.getPage(anchor.pageNumber() - 1).getCropBox();
        float x = round(box.getLowerLeftX() + Math.max(0F, line.left() - horizontalMargin));
        float y = round(box.getLowerLeftY() + box.getHeight() - line.bottom()
                - verticalMargin);
        float width = round(Math.min(box.getWidth() - (x - box.getLowerLeftX()),
                line.width() + horizontalMargin * 2F));
        float height = round(line.height() + verticalMargin * 2F);
        return new OaSignLaborPlacementProfileRegistry.PlacementRect(anchor.pageNumber(), x, y,
                width, height);
    }

    /**
     * Freezes a narrow guard immediately inside the common right-aligned edge of the
     * confirmation rows.  The exact-v7 table border sits just to the right of this edge; keeping
     * the signature left of the guard prevents either the image matrix or visible ink from
     * crossing the table boundary after a long employee name wraps onto a second row.
     */
    private OaSignLaborPlacementProfileRegistry.PlacementRect confirmationTableRightBoundary(
            PDDocument document, PageText page, Match confirmationLabel, Match signatureBlank,
            Match identityLabel, Match confirmationDate)
    {
        Match confirmationLine = lineBounds(page, signatureBlank);
        Match identityLine = lineBounds(page, identityLabel);
        Match dateLine = lineBounds(page, confirmationDate);
        float alignedTextRight = Math.max(confirmationLine.right(),
                Math.max(identityLine.right(), dateLine.right()));
        PDRectangle box = document.getPage(confirmationLabel.pageNumber() - 1).getCropBox();
        float x = round(box.getLowerLeftX() + alignedTextRight + 3.5F);
        float top = Math.min(confirmationLabel.top(), signatureBlank.top()) - 4F;
        float bottom = Math.max(identityLine.bottom(), dateLine.bottom()) + 4F;
        float y = round(box.getLowerLeftY() + box.getHeight() - bottom);
        float width = round(Math.min(1.5F, box.getUpperRightX() - x));
        if (width < 1F)
        {
            throw new ServiceException("岗位确认表格右边界无法安全定位");
        }
        return new OaSignLaborPlacementProfileRegistry.PlacementRect(
                confirmationLabel.pageNumber(), x, y, width, round(bottom - top));
    }

    private OaSignLaborPlacementProfileRegistry.AnchorRect anchor(PDDocument document,
            Match match, String name, List<String> requiredTexts)
    {
        PDRectangle box = document.getPage(match.pageNumber() - 1).getCropBox();
        float margin = 4F;
        float x = round(box.getLowerLeftX() + Math.max(0F, match.left() - margin));
        float y = round(box.getLowerLeftY() + box.getHeight() - match.bottom() - margin);
        float width = round(Math.min(box.getWidth() - (x - box.getLowerLeftX()),
                match.width() + margin * 2F));
        float height = round(match.height() + margin * 2F);
        return new OaSignLaborPlacementProfileRegistry.AnchorRect(name, match.pageNumber(), x, y,
                width, height, requiredTexts);
    }

    private Match lineBounds(PageText page, Match anchor)
    {
        List<Glyph> line = page.glyphs().stream()
                .filter(glyph -> Math.abs(glyph.top() - anchor.top()) <= 3F).toList();
        return bounds(anchor.pageNumber(), line, anchor.startIndex(), anchor.endIndex());
    }

    private void validateTargets(PDDocument document, int bodyPageCount,
            List<OaSignLaborPlacementProfileRegistry.PlacementRect> signatures,
            List<OaSignLaborPlacementProfileRegistry.PlacementRect> seals,
            List<OaSignLaborPlacementProfileRegistry.TextRect> textOverlays,
            List<OaSignLaborPlacementProfileRegistry.PlacementRect> protectedRegions)
    {
        List<OaSignLaborPlacementProfileRegistry.Rect> targets = new ArrayList<>();
        targets.addAll(signatures);
        targets.addAll(seals);
        targets.addAll(textOverlays);
        for (OaSignLaborPlacementProfileRegistry.Rect target : targets)
        {
            validateBounds(document, bodyPageCount, target);
            for (OaSignLaborPlacementProfileRegistry.Rect region : protectedRegions)
            {
                if (target.pageNumber().equals(region.pageNumber())
                        && intersects(target, region))
                {
                    throw new ServiceException("劳动合同签章或文本位置与身份证/日期保护区域重叠");
                }
            }
        }
        protectedRegions.forEach(value -> validateBounds(document, bodyPageCount, value));
    }

    private void validateBounds(PDDocument document, int bodyPageCount,
            OaSignLaborPlacementProfileRegistry.Rect value)
    {
        if (value.pageNumber() == null || value.pageNumber() <= 0
                || value.pageNumber() > bodyPageCount)
        {
            throw new ServiceException("劳动合同签章或保护区域页码越界");
        }
        PDRectangle box = document.getPage(value.pageNumber() - 1).getCropBox();
        if (value.x() < box.getLowerLeftX() || value.y() < box.getLowerLeftY()
                || value.width() <= 0 || value.height() <= 0
                || value.x() + value.width() > box.getUpperRightX()
                || value.y() + value.height() > box.getUpperRightY())
        {
            throw new ServiceException("劳动合同签章或保护区域越界");
        }
    }

    private boolean intersects(OaSignLaborPlacementProfileRegistry.Rect left,
            OaSignLaborPlacementProfileRegistry.Rect right)
    {
        return Math.min(left.x() + left.width(), right.x() + right.width())
                > Math.max(left.x(), right.x())
                && Math.min(left.y() + left.height(), right.y() + right.height())
                        > Math.max(left.y(), right.y());
    }

    private void requireSameLine(Match left, Match right, String label)
    {
        if (!sameLine(left, right) || left.pageNumber() != right.pageNumber())
        {
            throw new ServiceException(label + "局部锚点不在同一行");
        }
    }

    private void requireAfter(Match value, Match anchor, String label)
    {
        if (!after(value, anchor))
        {
            throw new ServiceException(label + "顺序不合法");
        }
    }

    private boolean after(Match value, Match anchor)
    {
        return value.pageNumber() > anchor.pageNumber()
                || (value.pageNumber() == anchor.pageNumber()
                        && (value.top() > anchor.top() + 1F
                                || (sameLine(value, anchor)
                                        && value.left() > anchor.right())));
    }

    private boolean before(Match value, Match anchor)
    {
        return value.pageNumber() < anchor.pageNumber()
                || (value.pageNumber() == anchor.pageNumber()
                        && (value.top() < anchor.top() - 1F
                                || (sameLine(value, anchor)
                                        && value.right() < anchor.left())));
    }

    private boolean sameLine(Match left, Match right)
    {
        return left.pageNumber() == right.pageNumber()
                && Math.abs(left.top() - right.top()) <= 3F;
    }

    private boolean sameBounds(Match left, Match right)
    {
        return left.pageNumber() == right.pageNumber()
                && Math.abs(left.left() - right.left()) <= 0.5F
                && Math.abs(left.top() - right.top()) <= 0.5F;
    }

    private float round(float value)
    {
        return Math.round(value * 10F) / 10F;
    }

    private static String canonical(String value)
    {
        return Normalizer.normalize(StringUtils.defaultString(value), Normalizer.Form.NFKC)
                .replaceAll("\\s+", "");
    }

    private static Match bounds(int pageNumber, List<Glyph> glyphs, int start, int end)
    {
        if (glyphs == null || glyphs.isEmpty())
        {
            throw new ServiceException("劳动合同局部锚点无法计算坐标");
        }
        float left = glyphs.stream().map(Glyph::left).min(Float::compare).orElseThrow();
        float right = glyphs.stream().map(Glyph::right).max(Float::compare).orElseThrow();
        float top = glyphs.stream().map(Glyph::top).min(Float::compare).orElseThrow();
        float bottom = glyphs.stream().map(Glyph::bottom).max(Float::compare).orElseThrow();
        return new Match(pageNumber, left, right, top, bottom, start, end);
    }

    private record SignatureBlank(Match label, Match blank) {}

    private record ChecklistMark(String value, Match match) {}

    private record Match(int pageNumber, float left, float right, float top, float bottom,
            int startIndex, int endIndex)
    {
        float width() { return right - left; }
        float height() { return bottom - top; }
    }

    private record Glyph(String value, float left, float right, float top, float bottom) {}

    private record PageText(int pageNumber, List<Glyph> glyphs, String canonicalText)
    {
        List<Match> find(String phrase)
        {
            return findCanonical(canonical(phrase));
        }

        List<Match> findCanonical(String phrase)
        {
            if (StringUtils.isBlank(phrase))
            {
                return List.of();
            }
            List<Match> result = new ArrayList<>();
            int from = 0;
            while (from <= canonicalText.length() - phrase.length())
            {
                int index = canonicalText.indexOf(phrase, from);
                if (index < 0)
                {
                    break;
                }
                result.add(bounds(pageNumber,
                        glyphs.subList(index, index + phrase.length()), index,
                        index + phrase.length()));
                from = index + 1;
            }
            return List.copyOf(result);
        }
    }

    private static final class PositionCollector extends PDFTextStripper
    {
        private final int maxPage;
        private final List<List<Glyph>> pages = new ArrayList<>();

        private PositionCollector(int maxPage) throws IOException
        {
            this.maxPage = maxPage;
            for (int index = 0; index < maxPage; index++)
            {
                pages.add(new ArrayList<>());
            }
            setStartPage(1);
            setEndPage(maxPage);
        }

        @Override
        protected void writeString(String text, List<TextPosition> positions)
        {
            if (getCurrentPageNo() > maxPage)
            {
                return;
            }
            List<Glyph> target = pages.get(getCurrentPageNo() - 1);
            for (TextPosition position : positions)
            {
                String normalized = canonical(position.getUnicode());
                if (normalized.isEmpty())
                {
                    continue;
                }
                int count = normalized.length();
                float width = position.getWidthDirAdj() / Math.max(1, count);
                for (int index = 0; index < count; index++)
                {
                    char value = normalized.charAt(index);
                    if (Character.isWhitespace(value))
                    {
                        continue;
                    }
                    float left = position.getXDirAdj() + width * index;
                    target.add(new Glyph(String.valueOf(value), left, left + width,
                            position.getYDirAdj(),
                            position.getYDirAdj() + position.getHeightDir()));
                }
            }
        }

        private List<PageText> pages(PDDocument document, int bodyPageCount)
        {
            List<PageText> result = new ArrayList<>();
            for (int pageIndex = 0; pageIndex < bodyPageCount; pageIndex++)
            {
                List<Glyph> pageGlyphs = List.copyOf(pages.get(pageIndex));
                StringBuilder text = new StringBuilder(pageGlyphs.size());
                pageGlyphs.forEach(glyph -> text.append(glyph.value()));
                result.add(new PageText(pageIndex + 1, pageGlyphs, text.toString()));
            }
            return List.copyOf(result);
        }
    }
}
