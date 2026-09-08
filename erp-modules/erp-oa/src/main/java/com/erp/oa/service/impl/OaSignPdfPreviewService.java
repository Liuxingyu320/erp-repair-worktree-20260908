package com.erp.oa.service.impl;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.domain.vo.OaSignPackageFile;

/**
 * Renders authenticated employee signing PDFs into mobile-browser-safe PNG pages.
 */
@Service
public class OaSignPdfPreviewService
{
    public static final int MAX_PREVIEW_PAGE_COUNT = 200;

    private static final long MAX_PDF_FILE_SIZE = 50L * 1024L * 1024L;
    private static final long MAX_RENDER_PIXELS = 24_000_000L;
    private static final float RENDER_DPI = 144.0F;

    public int pageCount(OaSignPackageFile file)
    {
        Path path = requirePdfFile(file);
        try (PDDocument document = Loader.loadPDF(path.toFile()))
        {
            return requireReasonablePageCount(document);
        }
        catch (InvalidPasswordException e)
        {
            throw new ServiceException("签约PDF受密码保护，无法在移动端预览");
        }
        catch (IOException e)
        {
            throw previewException("读取签约PDF失败", e);
        }
    }

    public byte[] renderPage(OaSignPackageFile file, int pageNumber)
    {
        if (pageNumber < 1)
        {
            throw new ServiceException("PDF页码必须从1开始");
        }
        Path path = requirePdfFile(file);
        try (PDDocument document = Loader.loadPDF(path.toFile()))
        {
            int pageCount = requireReasonablePageCount(document);
            if (pageNumber > pageCount)
            {
                throw new ServiceException("PDF页码超出范围，当前文件共" + pageCount + "页");
            }
            assertReasonablePageSize(document.getPage(pageNumber - 1));
            BufferedImage image = new PDFRenderer(document)
                    .renderImageWithDPI(pageNumber - 1, RENDER_DPI, ImageType.RGB);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", output))
            {
                throw new ServiceException("当前服务无法生成PNG预览图");
            }
            return output.toByteArray();
        }
        catch (InvalidPasswordException e)
        {
            throw new ServiceException("签约PDF受密码保护，无法在移动端预览");
        }
        catch (IOException e)
        {
            throw previewException("生成签约PDF预览图失败", e);
        }
    }

    private Path requirePdfFile(OaSignPackageFile file)
    {
        if (file == null || file.getPath() == null || !Files.isRegularFile(file.getPath()))
        {
            throw new ServiceException("签约PDF文件不存在");
        }
        if (!"application/pdf".equalsIgnoreCase(file.getContentType()))
        {
            throw new ServiceException("移动端图片预览仅支持PDF文件");
        }
        try
        {
            if (Files.size(file.getPath()) <= 0L)
            {
                throw new ServiceException("签约PDF文件为空");
            }
            if (Files.size(file.getPath()) > MAX_PDF_FILE_SIZE)
            {
                throw new ServiceException("签约PDF超过50MB，无法在移动端预览");
            }
        }
        catch (IOException e)
        {
            throw previewException("读取签约PDF文件信息失败", e);
        }
        return file.getPath();
    }

    private int requireReasonablePageCount(PDDocument document)
    {
        int pageCount = document == null ? 0 : document.getNumberOfPages();
        if (pageCount < 1)
        {
            throw new ServiceException("签约PDF没有可预览页面");
        }
        if (pageCount > MAX_PREVIEW_PAGE_COUNT)
        {
            throw new ServiceException("签约PDF超过移动端预览上限（" + MAX_PREVIEW_PAGE_COUNT + "页）");
        }
        return pageCount;
    }

    private void assertReasonablePageSize(PDPage page)
    {
        PDRectangle box = page == null ? null : page.getCropBox();
        float width = box == null ? 0.0F : box.getWidth();
        float height = box == null ? 0.0F : box.getHeight();
        if (!Float.isFinite(width) || !Float.isFinite(height) || width <= 0.0F || height <= 0.0F)
        {
            throw new ServiceException("签约PDF页面尺寸不合法");
        }
        long pixelWidth = (long) Math.ceil(width * RENDER_DPI / 72.0F);
        long pixelHeight = (long) Math.ceil(height * RENDER_DPI / 72.0F);
        if (pixelWidth > 8192L || pixelHeight > 8192L || pixelWidth * pixelHeight > MAX_RENDER_PIXELS)
        {
            throw new ServiceException("签约PDF页面尺寸过大，无法在移动端预览");
        }
    }

    private ServiceException previewException(String message, Exception cause)
    {
        return new ServiceException(message).setDetailMessage(cause == null ? null : cause.getMessage());
    }
}
