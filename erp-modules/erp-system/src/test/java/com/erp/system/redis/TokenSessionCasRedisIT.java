package com.erp.system.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import com.alibaba.fastjson2.JSON;
import com.erp.common.core.constant.CacheConstants;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.exception.auth.NotLoginException;
import com.erp.common.core.utils.JwtUtils;
import com.erp.common.redis.service.RedisService;
import com.erp.common.security.service.IdempotentSubmitService;
import com.erp.common.security.service.TokenService;
import com.erp.system.api.model.LoginUser;
import com.erp.system.testsupport.NativeRedisIntegrationTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.test.util.ReflectionTestUtils;

/** Exercises actual Fastjson bytes and Lua against a separately supplied nonproduction Redis. */
class TokenSessionCasRedisIT
{
    private static final long USER_ID = 7987654321L;
    private static NativeRedisIntegrationTestSupport support;
    private RedisService redis;
    private TokenService tokens;
    private String id;
    private String key;
    private static String indexKey() { return CacheConstants.USER_LOGIN_TOKEN_KEY + USER_ID; }

    @BeforeAll static void open() { support = NativeRedisIntegrationTestSupport.create("session_cas"); }
    @BeforeEach void prepare()
    {
        redis = support.getRedisService();
        tokens = support.getTokenService();
        support.cleanupGeneratedKeys();
        redis.deleteObject(indexKey());
        id = support.sessionId("original", 1);
        key = support.sessionKey(id);
        support.putLoginUser(id, USER_ID, "synthetic-cas-user", 60);
    }
    @AfterAll static void close()
    {
        if (support != null)
        {
            support.getRedisService().deleteObject(indexKey());
            support.close();
        }
    }
    private LoginUser read() { return tokens.getLoginUserByUserKey(id); }

    @Test void displayReadCannotRenewOrWriteAStoredSession()
    {
        byte[] before = redis.getCacheSnapshot(key).getBytes();
        long ttl = redis.getExpire(key);
        LoginUser display = tokens.getLoginUsersForDisplay(java.util.List.of(key)).get(0);
        assertThat(display.getUserid()).isEqualTo(USER_ID);
        assertThatThrownBy(() -> tokens.setLoginUser(display)).isInstanceOf(NotLoginException.class);
        assertThat(redis.getCacheSnapshot(key).getBytes()).isEqualTo(before);
        assertThat(redis.getExpire(key)).isBetween(1L, ttl);
        assertThat(redis.hasKey(indexKey())).isFalse();
    }

    @Test void forcedLogoutCannotBeUndoneByAnAlreadyReadRequest()
    {
        LoginUser stale = read();
        assertThat(tokens.deleteLoginUserByUserKey(id)).isTrue();
        assertThatThrownBy(() -> tokens.refreshToken(stale)).isInstanceOf(NotLoginException.class);
        assertThat(redis.hasKey(key)).isFalse();
        assertThat(redis.hasKey(indexKey())).isFalse();
    }

    @Test void logoutByJwtCannotBeUndoneByAProfileWrite()
    {
        LoginUser stale = read();
        stale.getSysUser().setNickName("late profile");
        String previous = System.getProperty("erp.jwt.secret");
        try
        {
            System.setProperty("erp.jwt.secret", "isolated-integration-test-only-secret-for-session-cas-verification-20260913");
            String jwt = JwtUtils.createToken(Map.of(SecurityConstants.USER_KEY, id,
                    SecurityConstants.DETAILS_USER_ID, USER_ID, SecurityConstants.DETAILS_USERNAME, "synthetic-cas-user"));
            tokens.delLoginUser(jwt);
            assertThatThrownBy(() -> tokens.setLoginUser(stale)).isInstanceOf(NotLoginException.class);
            assertThat(redis.hasKey(key)).isFalse();
        }
        finally
        {
            if (previous == null) System.clearProperty("erp.jwt.secret");
            else System.setProperty("erp.jwt.secret", previous);
        }
    }

    @Test void passwordResetScanRevokesReadSnapshots()
    {
        LoginUser stale = read();
        assertThat(tokens.deleteLoginUsersByUserId(USER_ID)).isEqualTo(1);
        assertThatThrownBy(() -> tokens.refreshToken(stale)).isInstanceOf(NotLoginException.class);
        assertThat(read()).isNull();
    }

    @Test void missingAuxiliaryIndexNeverMakesInvalidationAFalseSuccess()
    {
        LoginUser stale = read();
        assertThat(redis.hasKey(indexKey())).isFalse();
        tokens.invalidateUserSessions(USER_ID);
        assertThatThrownBy(() -> tokens.refreshToken(stale)).isInstanceOf(NotLoginException.class);
        assertThat(read()).isNull();
    }

    @Test void partialIndexRevokesUnindexedSessionsAndRetainsOnlyTheRequestedSession()
    {
        String kept = support.sessionId("retained", 1);
        String omitted = support.sessionId("unindexed", 1);
        support.putLoginUser(kept, USER_ID, "synthetic-cas-user", 60);
        support.putLoginUser(omitted, USER_ID, "synthetic-cas-user", 60);
        redis.addCacheSetValue(indexKey(), id);
        redis.addCacheSetValue(indexKey(), kept);
        LoginUser stale = read();
        tokens.invalidateUserSessions(USER_ID, kept);
        assertThatThrownBy(() -> tokens.refreshToken(stale)).isInstanceOf(NotLoginException.class);
        assertThat(tokens.getLoginUserByUserKey(omitted)).isNull();
        assertThat(tokens.getLoginUserByUserKey(kept)).isNotNull();
        assertThat(redis.<String>getCacheSet(indexKey())).containsExactly(kept);
    }

    @Test void renewalUsesExactStoredBytesIncludingLegacyUntypedJsonAndDoesNotRebuildIndex()
    {
        byte[] legacy = (" \n" + JSON.toJSONString(read()) + "\n ").getBytes(StandardCharsets.UTF_8);
        support.getRedisTemplate().execute((RedisCallback<Void>) connection -> {
            connection.stringCommands().pSetEx(key.getBytes(StandardCharsets.UTF_8), 60_000, legacy);
            return null;
        });
        assertThat(redis.getCacheSnapshot(key).getBytes()).isEqualTo(legacy);
        LoginUser existing = read();
        tokens.refreshToken(existing);
        assertThat(read().getUserid()).isEqualTo(USER_ID);
        assertThat(redis.getExpire(key)).isGreaterThan(60);
        assertThat(redis.hasKey(indexKey())).isFalse();
        assertThat(JSON.toJSONString(existing)).doesNotContain("sessionReads", "snapshot", "bytes");
    }

    @Test void concurrentRenewalsConvergeOnAuthoritativePermissions()
    {
        LoginUser oldRenewal = read();
        LoginUser permissionUpdate = read();
        permissionUpdate.setPermissions(Set.of("new:permission"));
        tokens.setLoginUser(permissionUpdate);
        tokens.refreshToken(oldRenewal);
        assertThat(oldRenewal.getPermissions()).containsExactly("new:permission");
        assertThat(read().getPermissions()).containsExactly("new:permission");
        LoginUser renewalA = read();
        LoginUser renewalB = read();
        tokens.refreshToken(renewalA);
        tokens.refreshToken(renewalB);
        assertThat(read()).isNotNull();
    }

    @Test void conflictingProfileOrPermissionWriteCannotReplaceNewerContents()
    {
        LoginUser first = read();
        LoginUser second = read();
        first.getSysUser().setNickName("first committed");
        tokens.setLoginUser(first);
        second.setPermissions(Set.of("stale:permission"));
        assertThatThrownBy(() -> tokens.setLoginUser(second))
                .isInstanceOf(ServiceException.class).hasMessageContaining("已被更新");
        assertThat(read().getSysUser().getNickName()).isEqualTo("first committed");
        assertThat(read().getPermissions()).isNullOrEmpty();
    }

    @Test void profileWriteCanRetryAcrossAnUnrelatedRenewal()
    {
        LoginUser profile = read();
        LoginUser renewal = read();
        tokens.refreshToken(renewal);
        profile.getSysUser().setNickName("new profile");
        tokens.setLoginUser(profile);
        assertThat(read().getSysUser().getNickName()).isEqualTo("new profile");
    }

    @Test void untrackedOrRetargetedObjectsCannotCreateOrOverwriteSessions()
    {
        LoginUser forged = JSON.parseObject(JSON.toJSONString(read()), LoginUser.class);
        assertThatThrownBy(() -> tokens.refreshToken(forged)).isInstanceOf(NotLoginException.class);
        LoginUser changed = read();
        String another = support.sessionId("another", 1);
        changed.setToken(another);
        assertThatThrownBy(() -> tokens.refreshToken(changed)).isInstanceOf(NotLoginException.class);
        assertThat(redis.hasKey(support.sessionKey(another))).isFalse();
    }

    @Test void anExpiredLockOwnerCannotReleaseTheNextOwnersLease() throws Exception
    {
        IdempotentSubmitService submits = new IdempotentSubmitService();
        ReflectionTestUtils.setField(submits, "redisService", redis);
        String lock = support.businessKey("owner_lock");
        String first = submits.tryAcquire(lock, 1);
        assertThat(first).isNotBlank();
        assertThat(submits.tryAcquire(lock, 30)).isNull();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (redis.hasKey(lock) && System.nanoTime() < deadline) Thread.sleep(20);
        assertThat(redis.hasKey(lock)).isFalse();
        String second = submits.tryAcquire(lock, 30);
        assertThat(second).isNotBlank().isNotEqualTo(first);
        submits.release(lock, first);
        assertThat(redis.<String>getCacheObject(lock)).isEqualTo(second);
        submits.release(lock, second);
        assertThat(redis.hasKey(lock)).isFalse();
    }
}
