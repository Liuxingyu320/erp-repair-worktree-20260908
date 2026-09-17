package com.erp.common.core.utils.file;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import com.erp.common.core.exception.ServiceException;

/**
 * Reads the EXIF orientation from a JPEG and applies it to the decoded pixels.
 * The normalized image can then be safely re-encoded without EXIF metadata.
 */
public final class ImageExifOrientation
{
    private static final int JPEG_APP1 = 0xe1;
    private static final int JPEG_SOS = 0xda;
    private static final int JPEG_EOI = 0xd9;
    private static final int EXIF_ORIENTATION_TAG = 0x0112;
    private static final int TIFF_TYPE_SHORT = 3;

    private ImageExifOrientation() { }

    public static int read(byte[] source)
    {
        if (!isJpeg(source)) return 1;
        int offset = 2;
        Integer orientation = null;
        while (offset < source.length)
        {
            if (unsigned(source[offset]) != 0xff) throw invalid();
            while (offset < source.length
                    && unsigned(source[offset]) == 0xff) offset++;
            if (offset >= source.length) throw invalid();
            int marker = unsigned(source[offset++]);
            if (marker == JPEG_SOS || marker == JPEG_EOI) break;
            if (marker == 0x00 || marker == 0x01
                    || marker >= 0xd0 && marker <= 0xd7)
                continue;
            if (offset + 2 > source.length) throw invalid();
            int length = readBigEndianUnsignedShort(source, offset);
            if (length < 2 || (long) offset + length > source.length)
                throw invalid();
            int dataStart = offset + 2;
            int dataEnd = offset + length;
            if (marker == JPEG_APP1 && isExif(source, dataStart, dataEnd))
            {
                Integer candidate = readExifIfd0Orientation(source,
                        dataStart + 6, dataEnd);
                if (candidate != null)
                {
                    if (orientation != null
                            && !orientation.equals(candidate)) throw invalid();
                    orientation = candidate;
                }
            }
            offset = dataEnd;
        }
        return orientation == null ? 1 : orientation;
    }

    public static boolean swapsDimensions(int orientation)
    {
        requireValid(orientation);
        return orientation >= 5;
    }

    public static BufferedImage apply(BufferedImage source, int orientation)
    {
        if (source == null) throw invalid();
        requireValid(orientation);
        if (orientation == 1) return source;

        int width = source.getWidth();
        int height = source.getHeight();
        int targetWidth = swapsDimensions(orientation) ? height : width;
        int targetHeight = swapsDimensions(orientation) ? width : height;
        BufferedImage target = new BufferedImage(targetWidth, targetHeight,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = target.createGraphics();
        try
        {
            graphics.setComposite(AlphaComposite.Src);
            graphics.drawImage(source, transform(orientation, width, height),
                    null);
        }
        finally
        {
            graphics.dispose();
        }
        return target;
    }

    private static AffineTransform transform(int orientation, int width,
            int height)
    {
        return switch (orientation)
        {
            case 2 -> new AffineTransform(-1, 0, 0, 1, width, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, width, height);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, height);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, height, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, height, width);
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, width);
            default -> new AffineTransform();
        };
    }

    private static Integer readExifIfd0Orientation(byte[] source,
            int tiffStart, int dataEnd)
    {
        if (tiffStart < 0 || tiffStart + 8 > dataEnd) throw invalid();
        boolean littleEndian;
        int first = unsigned(source[tiffStart]);
        int second = unsigned(source[tiffStart + 1]);
        if (first == 'I' && second == 'I') littleEndian = true;
        else if (first == 'M' && second == 'M') littleEndian = false;
        else throw invalid();
        if (readUnsignedShort(source, tiffStart + 2, littleEndian) != 42)
            throw invalid();
        long ifdOffset = readUnsignedInt(source, tiffStart + 4, littleEndian);
        long ifdPosition = (long) tiffStart + ifdOffset;
        if (ifdOffset < 8 || ifdPosition + 2 > dataEnd) throw invalid();
        int ifdStart = (int) ifdPosition;
        int entries = readUnsignedShort(source, ifdStart, littleEndian);
        long entriesEnd = (long) ifdStart + 2L + entries * 12L;
        if (entriesEnd + 4L > dataEnd) throw invalid();

        Integer orientation = null;
        for (int index = 0; index < entries; index++)
        {
            int entry = ifdStart + 2 + index * 12;
            if (readUnsignedShort(source, entry, littleEndian)
                    != EXIF_ORIENTATION_TAG) continue;
            int type = readUnsignedShort(source, entry + 2, littleEndian);
            long count = readUnsignedInt(source, entry + 4, littleEndian);
            if (type != TIFF_TYPE_SHORT || count != 1) throw invalid();
            int candidate = readUnsignedShort(source, entry + 8,
                    littleEndian);
            requireValid(candidate);
            if (orientation != null && !orientation.equals(candidate))
                throw invalid();
            orientation = candidate;
        }
        return orientation;
    }

    private static boolean isExif(byte[] source, int start, int end)
    {
        return end - start >= 6
                && source[start] == 'E' && source[start + 1] == 'x'
                && source[start + 2] == 'i' && source[start + 3] == 'f'
                && source[start + 4] == 0 && source[start + 5] == 0;
    }

    private static boolean isJpeg(byte[] source)
    {
        return source != null && source.length >= 4
                && unsigned(source[0]) == 0xff
                && unsigned(source[1]) == 0xd8
                && unsigned(source[2]) == 0xff;
    }

    private static int readBigEndianUnsignedShort(byte[] source, int offset)
    {
        return unsigned(source[offset]) << 8 | unsigned(source[offset + 1]);
    }

    private static int readUnsignedShort(byte[] source, int offset,
            boolean littleEndian)
    {
        int first = unsigned(source[offset]);
        int second = unsigned(source[offset + 1]);
        return littleEndian ? first | second << 8 : first << 8 | second;
    }

    private static long readUnsignedInt(byte[] source, int offset,
            boolean littleEndian)
    {
        long b0 = unsigned(source[offset]);
        long b1 = unsigned(source[offset + 1]);
        long b2 = unsigned(source[offset + 2]);
        long b3 = unsigned(source[offset + 3]);
        return littleEndian ? b0 | b1 << 8 | b2 << 16 | b3 << 24
                : b0 << 24 | b1 << 16 | b2 << 8 | b3;
    }

    private static int unsigned(byte value)
    {
        return value & 0xff;
    }

    private static void requireValid(int orientation)
    {
        if (orientation < 1 || orientation > 8) throw invalid();
    }

    private static ServiceException invalid()
    {
        return new ServiceException("图片方向信息无效");
    }
}
