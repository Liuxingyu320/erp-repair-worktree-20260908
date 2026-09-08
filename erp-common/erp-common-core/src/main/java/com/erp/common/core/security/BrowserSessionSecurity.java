package com.erp.common.core.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import com.erp.common.core.utils.StringUtils;

/**
 * Shared, storage-free security contract for the ERP-NEW_2 browser session.
 *
 * <p>Browser JavaScript never receives the signed access token. The token is
 * transported only by the dedicated HttpOnly cookie; JavaScript receives a
 * session-bound CSRF proof generated with a separate ERP-NEW_2 secret.</p>
 */
public final class BrowserSessionSecurity
{
    public static final String COOKIE_NAME = "ERP_NEW_2_BROWSER_SESSION";

    public static final String CSRF_HEADER_NAME = "X-CSRF-Token";

    public static final int MINIMUM_SECRET_BYTES = 32;

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private static final byte[] DOMAIN_SEPARATOR =
            "ERP-NEW_2/browser-session/csrf/v1".getBytes(StandardCharsets.UTF_8);

    private BrowserSessionSecurity()
    {
    }

    public static String csrfToken(String accessToken, String secret)
    {
        requireAccessToken(accessToken);
        byte[] secretBytes = requireSecret(secret);
        try
        {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretBytes, HMAC_ALGORITHM));
            mac.update(DOMAIN_SEPARATOR);
            mac.update((byte) 0);
            byte[] digest = mac.doFinal(accessToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        }
        catch (GeneralSecurityException ex)
        {
            throw new IllegalStateException("浏览器会话 CSRF 算法不可用", ex);
        }
    }

    public static boolean matches(String accessToken, String suppliedToken, String secret)
    {
        if (StringUtils.isEmpty(suppliedToken))
        {
            return false;
        }
        byte[] expected = csrfToken(accessToken, secret).getBytes(StandardCharsets.US_ASCII);
        byte[] supplied = suppliedToken.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, supplied);
    }

    public static void validateSecret(String secret)
    {
        requireSecret(secret);
    }

    private static void requireAccessToken(String accessToken)
    {
        if (StringUtils.isEmpty(accessToken) || StringUtils.isEmpty(accessToken.trim()))
        {
            throw new IllegalArgumentException("浏览器会话令牌不能为空");
        }
    }

    private static byte[] requireSecret(String secret)
    {
        if (StringUtils.isEmpty(secret) || StringUtils.isEmpty(secret.trim()))
        {
            throw new IllegalStateException("ERP-NEW_2 浏览器会话 CSRF 密钥未配置");
        }
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MINIMUM_SECRET_BYTES)
        {
            throw new IllegalStateException("ERP-NEW_2 浏览器会话 CSRF 密钥至少需要 32 字节");
        }
        return secretBytes;
    }
}
