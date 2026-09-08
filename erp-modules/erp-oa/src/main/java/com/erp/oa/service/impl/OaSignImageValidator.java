package com.erp.oa.service.impl;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import com.erp.common.core.exception.ServiceException;

/** Shared image contract for employee signatures and registered company seals. */
final class OaSignImageValidator
{
    private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    private static final long MAX_IMAGE_PIXELS = 16_000_000L;
    private static final int MAX_IMAGE_DIMENSION = 8192;

    private OaSignImageValidator()
    {
    }

    static void requireSignaturePng(byte[] bytes)
    {
        validate(bytes, "签名", true);
    }

    static boolean isValidSignaturePng(byte[] bytes)
    {
        try
        {
            requireSignaturePng(bytes);
            return true;
        }
        catch (ServiceException exception)
        {
            return false;
        }
    }

    static String requireCompanySealExtension(byte[] bytes)
    {
        return validate(bytes, "企业章", false).extension;
    }

    static String companySealArchiveFilename(Long sealId, byte[] bytes)
    {
        return "company-seal-" + sealId + requireCompanySealExtension(bytes);
    }

    private static ImageFormat validate(byte[] bytes, String label, boolean signature)
    {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES)
        {
            throw invalid(label);
        }
        ImageFormat format = detect(bytes);
        if (format == null || signature && format != ImageFormat.PNG)
        {
            throw invalid(label);
        }
        try (MemoryCacheImageInputStream input = new MemoryCacheImageInputStream(
                new ByteArrayInputStream(bytes)))
        {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid(label);
            ImageReader reader = readers.next();
            try
            {
                reader.setInput(input, true, false);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                // Reject oversized headers before allocating a decoded raster.
                if (width <= 0 || height <= 0 || width > MAX_IMAGE_DIMENSION
                        || height > MAX_IMAGE_DIMENSION
                        || (long) width * height > MAX_IMAGE_PIXELS
                        || signature && (width < 16 || height < 16))
                    throw invalid(label);
                BufferedImage image = reader.read(0);
                if (image == null) throw invalid(label);
                if (signature) requireVisibleInk(image);
                return format;
            }
            finally
            {
                reader.dispose();
            }
        }
        catch (IOException | IllegalArgumentException e)
        {
            throw invalid(label).setDetailMessage(e.getMessage());
        }
    }

    private static void requireVisibleInk(BufferedImage image)
    {
        int width = image.getWidth();
        int height = image.getHeight();
        long ink = 0;
        int minX = width, minY = height, maxX = -1, maxY = -1;
        int[] row = new int[width];
        for (int y = 0; y < height; y++)
        {
            image.getRGB(0, y, width, 1, row, 0, width);
            for (int x = 0; x < width; x++)
            {
                int pixel = row[x];
                int alpha = pixel >>> 24;
                int luminance = (((pixel >>> 16) & 255) * 299
                        + ((pixel >>> 8) & 255) * 587 + (pixel & 255) * 114) / 1000;
                // Match the signature as it will appear over white PDF paper.
                int onWhite = 255 - (255 - luminance) * alpha / 255;
                if (onWhite <= 220)
                {
                    ink++;
                    minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                }
            }
        }
        if (ink < 20 || maxX - minX < 7 || maxY - minY < 3
                || ink * 10 >= (long) width * height * 9)
            throw new ServiceException("签名图片缺少清晰可见的笔迹，请重新手写签名");
    }

    private static ImageFormat detect(byte[] bytes)
    {
        if (bytes.length >= 8
                && bytes[0] == (byte) 0x89 && bytes[1] == 'P'
                && bytes[2] == 'N' && bytes[3] == 'G'
                && bytes[4] == (byte) 0x0D && bytes[5] == (byte) 0x0A
                && bytes[6] == (byte) 0x1A && bytes[7] == (byte) 0x0A)
        {
            return ImageFormat.PNG;
        }
        if (bytes.length >= 3
                && bytes[0] == (byte) 0xFF
                && bytes[1] == (byte) 0xD8
                && bytes[2] == (byte) 0xFF)
        {
            return ImageFormat.JPEG;
        }
        return null;
    }

    private static ServiceException invalid(String label)
    {
        return new ServiceException(label + "图片不合法");
    }

    private enum ImageFormat
    {
        PNG(".png"),
        JPEG(".jpg");

        private final String extension;

        ImageFormat(String extension)
        {
            this.extension = extension;
        }
    }
}
