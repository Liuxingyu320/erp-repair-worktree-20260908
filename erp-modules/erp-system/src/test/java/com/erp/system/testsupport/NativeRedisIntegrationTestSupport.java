package com.erp.system.testsupport;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.constant.CacheConstants;
import com.erp.common.redis.configure.RedisConfig;
import com.erp.common.redis.service.RedisService;
import com.erp.common.security.service.TokenService;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.model.LoginUser;

/**
 * 原生 Redis 集成测试支持。只操作本次 run id 生成的会话与探针键，禁止清库。
 */
public final class NativeRedisIntegrationTestSupport implements AutoCloseable
{
    private static final int DELETE_BATCH_SIZE = 250;

    private static final Pattern SAFE_HOST = Pattern.compile("^[A-Za-z0-9._:-]+$");

    private static final Pattern SAFE_ID = Pattern.compile("^[a-z0-9_]+$");

    private final String host;

    private final int port;

    private final int database;

    private final String prefix;

    private final LettuceConnectionFactory connectionFactory;

    private final RedisTemplate<Object, Object> redisTemplate;

    private final RedisService redisService;

    private final TokenService tokenService;

    private boolean closed;

    private NativeRedisIntegrationTestSupport(String suiteName)
    {
        host = requireEnvironment("ERP_IT_REDIS_HOST");
        port = parseInteger(environmentOrDefault("ERP_IT_REDIS_PORT", "6379"), 1, 65535,
                "Redis 端口");
        database = parseInteger(environmentOrDefault("ERP_IT_REDIS_DATABASE", "15"), 0, 63,
                "Redis 数据库");
        String username = System.getenv().getOrDefault("ERP_IT_REDIS_USERNAME", "").trim();
        String password = System.getenv().getOrDefault("ERP_IT_REDIS_PASSWORD", "");
        validateHost(host, System.getenv("ERP_IT_ALLOW_REMOTE_NONPROD"));
        if (database == 0 && !"1".equals(System.getenv("ERP_IT_REDIS_ALLOW_DATABASE_ZERO")))
        {
            throw new IllegalArgumentException("默认拒绝在 Redis DB 0 执行集成测试");
        }

        String runId = sanitizeIdentifier(environmentOrDefault("ERP_IT_RUN_ID",
                "manual_" + randomSuffix(8)), 20, "run id");
        String suite = sanitizeIdentifier(suiteName, 20, "suite name");
        prefix = runId + "_" + suite + "_" + randomSuffix(8) + "_";

        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(host, port);
        configuration.setDatabase(database);
        if (!username.isEmpty())
        {
            configuration.setUsername(username);
        }
        if (!password.isEmpty())
        {
            configuration.setPassword(RedisPassword.of(password));
        }

        connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        try (var connection = connectionFactory.getConnection())
        {
            String pong = connection.ping();
            if (!"PONG".equalsIgnoreCase(pong))
            {
                throw new IllegalStateException("原生 Redis PING 失败");
            }
        }
        catch (RuntimeException e)
        {
            connectionFactory.destroy();
            throw new IllegalStateException("无法连接原生 Redis（host=" + host + ":" + port
                    + ", db=" + database + "）", e);
        }

        redisTemplate = new RedisConfig().erpRedisTemplate(connectionFactory);
        redisService = new RedisService();
        ReflectionTestUtils.setField(redisService, "redisTemplate", redisTemplate);
        tokenService = new TokenService();
        ReflectionTestUtils.setField(tokenService, "redisService", redisService);
        cleanupGeneratedKeys();
        System.out.printf("[native-redis-it] suite=%s host=%s:%d db=%d prefix=%s%n",
                suite, host, port, database, prefix);
    }

    public static NativeRedisIntegrationTestSupport create(String suiteName)
    {
        return new NativeRedisIntegrationTestSupport(suiteName);
    }

    public RedisService getRedisService()
    {
        return redisService;
    }

    public TokenService getTokenService()
    {
        return tokenService;
    }

    public RedisTemplate<Object, Object> getRedisTemplate()
    {
        return redisTemplate;
    }

    public String getPrefix()
    {
        return prefix;
    }

    public String sessionId(String category, int sequence)
    {
        String normalizedCategory = sanitizeIdentifier(category, 20, "session category");
        return prefix + normalizedCategory + "_" + String.format(Locale.ROOT, "%06d", sequence);
    }

    public String sessionKey(String sessionId)
    {
        return CacheConstants.LOGIN_TOKEN_KEY + sessionId;
    }

    public String businessKey(String suffix)
    {
        return "erp_it_redis:" + prefix + sanitizeIdentifier(suffix, 30, "business key suffix");
    }

    public LoginUser putLoginUser(String sessionId, Long userId, String userName,
            long ttlSeconds)
    {
        SysUser sysUser = new SysUser();
        sysUser.setUserId(userId);
        sysUser.setUserName(userName);
        LoginUser loginUser = new LoginUser();
        loginUser.setToken(sessionId);
        loginUser.setUserid(userId);
        loginUser.setUsername(userName);
        loginUser.setIpaddr("127.0.0.1");
        loginUser.setLoginTime(System.currentTimeMillis());
        loginUser.setExpireTime(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(ttlSeconds));
        loginUser.setSysUser(sysUser);
        if (Long.valueOf(1L).equals(userId))
        {
            loginUser.setRoles(Set.of("admin"));
        }
        redisService.setCacheObject(sessionKey(sessionId), loginUser, ttlSeconds, TimeUnit.SECONDS);
        return loginUser;
    }

    public void putMalformedSession(String sessionId, long ttlSeconds)
    {
        redisService.setCacheObject(sessionKey(sessionId), "malformed-session", ttlSeconds,
                TimeUnit.SECONDS);
    }

    public void cleanupGeneratedKeys()
    {
        deletePattern(CacheConstants.LOGIN_TOKEN_KEY + prefix + "*");
        deletePattern("erp_it_redis:" + prefix + "*");
    }

    private void deletePattern(String pattern)
    {
        Collection<String> keys = redisService.scanKeys(pattern, 500, Integer.MAX_VALUE).getKeys();
        if (keys == null || keys.isEmpty())
        {
            return;
        }
        List<String> batch = new ArrayList<>(DELETE_BATCH_SIZE);
        for (String key : keys)
        {
            batch.add(key);
            if (batch.size() == DELETE_BATCH_SIZE)
            {
                redisService.deleteObject(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty())
        {
            redisService.deleteObject(batch);
        }
    }

    @Override
    public synchronized void close()
    {
        if (closed)
        {
            return;
        }
        try
        {
            cleanupGeneratedKeys();
            closed = true;
            System.out.printf("[native-redis-it] cleaned prefix=%s db=%d%n", prefix, database);
        }
        finally
        {
            connectionFactory.destroy();
        }
    }

    private static void validateHost(String value, String remoteConfirmation)
    {
        if (!SAFE_HOST.matcher(value).matches())
        {
            throw new IllegalArgumentException("Redis 主机格式无效");
        }
        if (!isLocalHost(value) && !"1".equals(remoteConfirmation))
        {
            throw new IllegalArgumentException(
                    "默认拒绝远端 Redis；确认是非生产实例后设置 ERP_IT_ALLOW_REMOTE_NONPROD=1");
        }
    }

    static boolean isLocalHost(String value)
    {
        return "127.0.0.1".equals(value) || "localhost".equalsIgnoreCase(value)
                || "::1".equals(value);
    }

    private static int parseInteger(String value, int min, int max, String label)
    {
        try
        {
            int parsed = Integer.parseInt(value);
            if (parsed < min || parsed > max)
            {
                throw new IllegalArgumentException(label + "超出范围");
            }
            return parsed;
        }
        catch (NumberFormatException e)
        {
            throw new IllegalArgumentException(label + "必须是数字", e);
        }
    }

    private static String requireEnvironment(String name)
    {
        String value = System.getenv(name);
        if (value == null || value.isBlank())
        {
            throw new IllegalStateException("缺少原生 Redis 集成测试环境变量: " + name);
        }
        return value.trim();
    }

    private static String environmentOrDefault(String name, String defaultValue)
    {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static String sanitizeIdentifier(String value, int maxLength, String label)
    {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replace('-', '_');
        if (normalized.isEmpty() || normalized.length() > maxLength
                || !SAFE_ID.matcher(normalized).matches())
        {
            throw new IllegalArgumentException(label + "格式无效");
        }
        return normalized;
    }

    private static String randomSuffix(int length)
    {
        return UUID.randomUUID().toString().replace("-", "").substring(0, length);
    }
}
