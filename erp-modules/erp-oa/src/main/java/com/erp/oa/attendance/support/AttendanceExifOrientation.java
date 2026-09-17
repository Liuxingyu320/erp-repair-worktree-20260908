package com.erp.oa.attendance.support;

import java.awt.image.BufferedImage;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.file.ImageExifOrientation;

/** Compatibility facade retaining attendance error codes. */
public final class AttendanceExifOrientation
{
    private AttendanceExifOrientation() { }
    public static int read(byte[] source)
    {
        try { return ImageExifOrientation.read(source); }
        catch (ServiceException ex) { throw new ServiceException("PHOTO_CONTENT_INVALID"); }
    }
    public static boolean swapsDimensions(int orientation)
    {
        try { return ImageExifOrientation.swapsDimensions(orientation); }
        catch (ServiceException ex) { throw new ServiceException("PHOTO_CONTENT_INVALID"); }
    }
    public static BufferedImage apply(BufferedImage source, int orientation)
    {
        try { return ImageExifOrientation.apply(source, orientation); }
        catch (ServiceException ex) { throw new ServiceException("PHOTO_CONTENT_INVALID"); }
    }
}
