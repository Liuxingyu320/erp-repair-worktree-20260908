package com.erp.oa.attendance.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;
import com.erp.common.core.exception.ServiceException;

/** Stable validation and fingerprints for multi-client draft creation. */
public final class AttendanceClientRequestSupport
{
    private static final Pattern CLIENT_REQUEST_ID = Pattern.compile(
            "[A-Za-z0-9][A-Za-z0-9._:-]{7,63}");

    private AttendanceClientRequestSupport() { }

    public static String normalizeOptional(String value, String errorCode)
    {
        if (value == null) return null;
        String normalized = value.trim();
        if (!CLIENT_REQUEST_ID.matcher(normalized).matches())
            throw new ServiceException(errorCode);
        return normalized;
    }

    /**
     * Hashes length-delimited values so separators inside user input cannot
     * create an ambiguous canonical payload.
     */
    public static String fingerprint(String namespace, Object... values)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, namespace);
            if (values != null)
                for (Object value : values)
                    update(digest, value == null ? null : String.valueOf(value));
            return HexFormat.of().formatHex(digest.digest());
        }
        catch (NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException(exception);
        }
    }

    private static void update(MessageDigest digest, String value)
    {
        if (value == null)
        {
            digest.update((byte) 0);
            return;
        }
        digest.update((byte) 1);
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }
}
