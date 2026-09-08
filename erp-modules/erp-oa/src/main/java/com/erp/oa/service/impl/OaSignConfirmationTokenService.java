package com.erp.oa.service.impl;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.JwtUtils;
import io.jsonwebtoken.Claims;

/** Issues short-lived signed confirmation tokens without exposing the real snapshot digest. */
@Service
public class OaSignConfirmationTokenService
{
    private static final String PURPOSE = "OA_SIGN_TASK_CONFIRM_V1";
    private static final Duration TOKEN_TTL = Duration.ofMinutes(10);

    private final Clock clock;

    public OaSignConfirmationTokenService()
    {
        this(Clock.systemUTC());
    }

    OaSignConfirmationTokenService(Clock clock)
    {
        this.clock = clock;
    }

    public String issue(Long taskId, Long userId, String documentVersion, String snapshotHash)
    {
        requireContext(taskId, userId, documentVersion, snapshotHash);
        Instant issuedAt = clock.instant();
        Map<String, Object> claims = new HashMap<>();
        claims.put("purpose", PURPOSE);
        claims.put("taskId", String.valueOf(taskId));
        claims.put("userId", String.valueOf(userId));
        claims.put("documentVersion", documentVersion);
        claims.put("snapshotBinding", snapshotBinding(taskId, userId, documentVersion, snapshotHash));
        claims.put("issuedAtEpochSecond", issuedAt.getEpochSecond());
        claims.put("expiresAtEpochSecond", issuedAt.plus(TOKEN_TTL).getEpochSecond());
        return JwtUtils.createToken(claims);
    }

    public void verify(String token, Long taskId, Long userId,
            String documentVersion, String snapshotHash)
    {
        requireContext(taskId, userId, documentVersion, snapshotHash);
        if (token == null || token.isBlank())
        {
            throw invalidToken();
        }
        Claims claims;
        try
        {
            claims = JwtUtils.parseToken(token);
        }
        catch (RuntimeException ex)
        {
            throw invalidToken();
        }
        Long expiresAtEpochSecond = claimLong(claims, "expiresAtEpochSecond");
        if (expiresAtEpochSecond == null || expiresAtEpochSecond <= clock.instant().getEpochSecond()
                || !PURPOSE.equals(claimString(claims, "purpose"))
                || !String.valueOf(taskId).equals(claimString(claims, "taskId"))
                || !String.valueOf(userId).equals(claimString(claims, "userId"))
                || !documentVersion.equals(claimString(claims, "documentVersion")))
        {
            throw invalidToken();
        }
        String expectedBinding = snapshotBinding(taskId, userId, documentVersion, snapshotHash);
        String actualBinding = claimString(claims, "snapshotBinding");
        if (actualBinding == null || !MessageDigest.isEqual(
                expectedBinding.getBytes(StandardCharsets.US_ASCII),
                actualBinding.getBytes(StandardCharsets.US_ASCII)))
        {
            throw new ServiceException("资料或文件快照已变化，请重新打开确认");
        }
    }

    private String snapshotBinding(Long taskId, Long userId,
            String documentVersion, String snapshotHash)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, PURPOSE);
            update(digest, taskId);
            update(digest, userId);
            update(digest, documentVersion);
            update(digest, snapshotHash);
            return HexFormat.of().formatHex(digest.digest());
        }
        catch (NoSuchAlgorithmException ex)
        {
            throw new IllegalStateException("SHA-256不可用", ex);
        }
    }

    private void requireContext(Long taskId, Long userId,
            String documentVersion, String snapshotHash)
    {
        if (taskId == null || userId == null || documentVersion == null || documentVersion.isBlank()
                || snapshotHash == null || snapshotHash.isBlank())
        {
            throw new ServiceException("确认令牌上下文不完整");
        }
    }

    private void update(MessageDigest digest, Object value)
    {
        byte[] bytes = String.valueOf(value).getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private String claimString(Claims claims, String name)
    {
        Object value = claims == null ? null : claims.get(name);
        return value == null ? null : String.valueOf(value);
    }

    private Long claimLong(Claims claims, String name)
    {
        Object value = claims == null ? null : claims.get(name);
        if (value instanceof Number number)
        {
            return number.longValue();
        }
        try
        {
            return value == null ? null : Long.valueOf(String.valueOf(value));
        }
        catch (NumberFormatException ex)
        {
            return null;
        }
    }

    private ServiceException invalidToken()
    {
        return new ServiceException("确认令牌无效或已过期，请重新打开确认");
    }
}
