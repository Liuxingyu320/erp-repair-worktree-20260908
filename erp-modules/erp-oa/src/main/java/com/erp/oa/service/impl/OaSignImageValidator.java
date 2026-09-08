package com.erp.oa.service.impl;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import com.erp.common.core.exception.ServiceException;

/** Shared image contract for employee signatures and registered company seals. */
final class OaSignImageValidator
{
    private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;

    private OaSignImageValidator()
    {
    }

    static void requireSignaturePng(byte[] bytes)
    {
        if (validate(bytes, "签名") != ImageFormat.PNG)
        {
            throw invalid("签名");
        }
    }

    static String requireCompanySealExtension(byte[] bytes)
    {
        return validate(bytes, "企业章").extension;
    }

    static String companySealArchiveFilename(Long sealId, byte[] bytes)
    {
        return "company-seal-" + sealId + requireCompanySealExtension(bytes);
    }

    private static ImageFormat validate(byte[] bytes, String label)
    {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES)
        {
            throw invalid(label);
        }
        ImageFormat format = detect(bytes);
        if (format == null)
        {
            throw invalid(label);
        }
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes))
        {
            BufferedImage image = ImageIO.read(input);
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0)
            {
                throw invalid(label);
            }
            return format;
        }
        catch (IOException e)
        {
            throw invalid(label).setDetailMessage(e.getMessage());
        }
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
