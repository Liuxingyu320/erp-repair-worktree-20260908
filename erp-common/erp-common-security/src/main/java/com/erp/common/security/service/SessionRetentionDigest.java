package com.erp.common.security.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** One-way, user-bound identifier for an existing internal session; never a JWT or a credential. */
public final class SessionRetentionDigest
{
    private static final String DOMAIN = "erp:security-session-retention:v1\n";

    private SessionRetentionDigest() { }

    public static String fromUserKey(Long userId, String userKey)
    {
        if (userKey == null || userKey.isBlank()) return null;
        if (userId == null || userId <= 0) throw new IllegalArgumentException("invalid session owner");
        try
        {
            byte[] input = (DOMAIN + userId + "\n" + userKey).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
        }
        catch (NoSuchAlgorithmException impossible)
        {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    public static void validate(String digest)
    {
        if (digest != null && !digest.matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("invalid retained session digest");
    }

    public static boolean matches(Long userId, String userKey, String digest)
    {
        if (digest == null || userKey == null || userKey.isBlank()) return false;
        return MessageDigest.isEqual(digest.getBytes(StandardCharsets.US_ASCII),
                fromUserKey(userId, userKey).getBytes(StandardCharsets.US_ASCII));
    }
}
