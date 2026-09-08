package com.erp.oa.service.impl;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.apache.fontbox.ttf.CmapLookup;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.FontMappers;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdfwriter.compress.CompressParameters;
import org.springframework.stereotype.Service;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;

/** Adds deterministic, actual-page-count footers after headless Office conversion. */
@Service
public class OaPdfPageNumberService
{
    static final float FONT_SIZE = 9F;
    static final float BASELINE_FROM_BOTTOM = 36F;
    private static final List<String> CJK_FONT_CANDIDATES = Arrays.asList(
            "NotoSansCJKsc-Regular", "NotoSansSC-Regular", "SourceHanSansSC-Regular",
            "MicrosoftYaHei", "WenQuanYiZenHei", "STHeitiSC-Medium", "STHeitiSC-Light",
            "STHeiti", "Heiti SC", "SimSun", "ArialUnicodeMS");

    public Path stampDynamicPageNumbers(Path sourcePdf)
    {
        if (sourcePdf == null || !Files.isRegularFile(sourcePdf))
        {
            throw new ServiceException("待添加页码的PDF不存在");
        }
        Path normalized = sourcePdf.toAbsolutePath().normalize();
        Path temporary = normalized.resolveSibling(normalized.getFileName()
                + ".page-numbered-" + UUID.randomUUID().toString().replace("-", "") + ".tmp");
        try
        {
            try (PDDocument document = Loader.loadPDF(normalized.toFile()))
            {
                int pageCount = document.getNumberOfPages();
                if (pageCount < 1)
                {
                    throw new ServiceException("PDF页数不正确，无法添加页码");
                }
                PDFont font = loadFont(document, pageCount);
                for (int index = 0; index < pageCount; index++)
                {
                    PDPage page = document.getPage(index);
                    if (Math.floorMod(page.getRotation(), 360) != 0)
                    {
                        throw new ServiceException("劳动合同包含旋转页，无法安全添加页码");
                    }
                    String footer = footerText(index + 1, pageCount);
                    PDRectangle box = page.getCropBox();
                    float textWidth = font.getStringWidth(footer) / 1000F * FONT_SIZE;
                    float x = box.getLowerLeftX() + Math.max(0F, (box.getWidth() - textWidth) / 2F);
                    float y = box.getLowerLeftY() + BASELINE_FROM_BOTTOM;
                    try (PDPageContentStream content = new PDPageContentStream(document, page,
                            AppendMode.APPEND, true, true))
                    {
                        content.beginText();
                        content.setFont(font, FONT_SIZE);
                        content.newLineAtOffset(x, y);
                        content.showText(footer);
                        content.endText();
                    }
                }
                document.save(temporary.toFile(), CompressParameters.NO_COMPRESSION);
            }
            replace(normalized, temporary);
            return normalized;
        }
        catch (ServiceException e)
        {
            deleteQuietly(temporary);
            throw e;
        }
        catch (IOException e)
        {
            deleteQuietly(temporary);
            throw new ServiceException("劳动合同PDF动态页码生成失败")
                    .setDetailMessage(e.getMessage());
        }
    }

    static String footerText(int page, int pages)
    {
        return "第 " + page + " 页 共 " + pages + " 页";
    }

    private PDFont loadFont(PDDocument document, int pageCount) throws IOException
    {
        String requiredText = footerText(pageCount, pageCount);
        String configuredFont = System.getenv("SIGN_PACKAGE_PDF_FONT_PATH");
        if (StringUtils.isNotBlank(configuredFont))
        {
            Path configuredPath = Path.of(configuredFont).toAbsolutePath().normalize();
            if (!Files.isRegularFile(configuredPath))
            {
                throw new ServiceException("签约PDF字体文件不存在");
            }
            return PDType0Font.load(document, configuredPath.toFile());
        }
        for (String fontName : CJK_FONT_CANDIDATES)
        {
            try
            {
                TrueTypeFont trueTypeFont = FontMappers.instance()
                        .getTrueTypeFont(fontName, null).getFont();
                if (supports(trueTypeFont, requiredText))
                {
                    return PDType0Font.load(document, trueTypeFont, true);
                }
            }
            catch (RuntimeException | IOException ignored)
            {
                // Try the next installed CJK font.
            }
        }
        throw new ServiceException("缺少支持中文的签约PDF字体，请联系系统管理员配置");
    }

    private boolean supports(TrueTypeFont font, String text) throws IOException
    {
        if (font == null)
        {
            return false;
        }
        CmapLookup cmap = font.getUnicodeCmapLookup();
        return cmap != null && text.codePoints()
                .filter(codePoint -> !Character.isWhitespace(codePoint))
                .allMatch(codePoint -> cmap.getGlyphId(codePoint) != 0);
    }

    private void replace(Path target, Path temporary) throws IOException
    {
        try
        {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        }
        catch (AtomicMoveNotSupportedException ignored)
        {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void deleteQuietly(Path path)
    {
        try
        {
            Files.deleteIfExists(path);
        }
        catch (IOException ignored)
        {
            // Staging-directory cleanup is authoritative.
        }
    }
}
