package com.erp.common.security.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.erp.common.core.constant.CacheConstants;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.utils.JwtUtils;
import com.erp.common.core.utils.ServletUtils;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.core.utils.ip.IpUtils;
import com.erp.common.core.utils.uuid.IdUtils;
import com.erp.common.redis.service.RedisService;
import com.erp.common.redis.service.RedisService.KeyScanResult;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.system.api.model.LoginUser;

/**
 * token验证处理
 * 
 * @author erp
 */
@Component
public class TokenService
{
    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    @Autowired
    private RedisService redisService;

    protected static final long MILLIS_SECOND = 1000;

    protected static final long MILLIS_MINUTE = 60 * MILLIS_SECOND;

    private final static long TOKEN_EXPIRE_TIME = CacheConstants.EXPIRATION;

    private final static String ACCESS_TOKEN = CacheConstants.LOGIN_TOKEN_KEY;

    private final static String USER_TOKEN_INDEX = CacheConstants.USER_LOGIN_TOKEN_KEY;

    private final static Long TOKEN_REFRESH_THRESHOLD_MINUTES = CacheConstants.REFRESH_TIME * MILLIS_MINUTE;

    private static final int SESSION_SCAN_BATCH_SIZE = 500;

    private static final int SESSION_SCAN_LIMIT = 10000;

    private static final int SESSION_READ_BATCH_SIZE = 500;

    /**
     * 创建令牌
     */
    public Map<String, Object> createToken(LoginUser loginUser)
    {
        String token = IdUtils.fastUUID();
        Long userId = loginUser.getSysUser().getUserId();
        String userName = loginUser.getSysUser().getUserName();
        loginUser.setToken(token);
        loginUser.setUserid(userId);
        loginUser.setUsername(userName);
        loginUser.setIpaddr(IpUtils.getIpAddr());
        refreshToken(loginUser);

        // Jwt存储信息
        Map<String, Object> claimsMap = new HashMap<String, Object>();
        claimsMap.put(SecurityConstants.USER_KEY, token);
        claimsMap.put(SecurityConstants.DETAILS_USER_ID, userId);
        claimsMap.put(SecurityConstants.DETAILS_USERNAME, userName);

        // 接口返回信息
        Map<String, Object> rspMap = new HashMap<String, Object>();
        rspMap.put("access_token", JwtUtils.createToken(claimsMap));
        rspMap.put("expires_in", TOKEN_EXPIRE_TIME);
        return rspMap;
    }

    /**
     * 获取用户身份信息
     *
     * @return 用户信息
     */
    public LoginUser getLoginUser()
    {
        return getLoginUser(ServletUtils.getRequest());
    }

    /**
     * 获取用户身份信息
     *
     * @return 用户信息
     */
    public LoginUser getLoginUser(HttpServletRequest request)
    {
        // 获取请求携带的令牌
        String token = SecurityUtils.getToken(request);
        return getLoginUser(token);
    }

    /**
     * 获取用户身份信息
     *
     * @return 用户信息
     */
    public LoginUser getLoginUser(String token)
    {
        LoginUser user = null;
        try
        {
            if (StringUtils.isNotEmpty(token))
            {
                String userkey = JwtUtils.getUserKey(token);
                user = getLoginUserByUserKey(userkey);
                return user;
            }
        }
        catch (Exception e)
        {
            log.warn("读取登录会话失败，已按无效会话处理");
        }
        return user;
    }

    /**
     * 根据内部会话标识读取登录用户。该标识不是前端 JWT。
     *
     * @param userKey 内部会话标识
     * @return 登录用户；缓存不存在或内容无法解析时返回 null
     */
    public LoginUser getLoginUserByUserKey(String userKey)
    {
        if (StringUtils.isEmpty(userKey))
        {
            return null;
        }
        return getLoginUserByCacheKey(getTokenKey(userKey));
    }

    /**
     * 根据完整 Redis 键读取登录用户，供在线会话治理使用。
     * Redis 连接异常会继续向上抛出，避免把基础设施故障伪装成空列表。
     *
     * @param cacheKey 完整登录会话缓存键
     * @return 登录用户；缓存不存在或内容无法解析时返回 null
     */
    public LoginUser getLoginUserByCacheKey(String cacheKey)
    {
        if (StringUtils.isEmpty(cacheKey) || !cacheKey.startsWith(ACCESS_TOKEN))
        {
            return null;
        }
        Object cacheUser = redisService.getCacheObject(cacheKey);
        return parseCachedLoginUser(cacheUser);
    }

    /**
     * 根据内部会话标识删除登录会话。
     *
     * @param userKey 内部会话标识
     * @return 是否实际删除缓存
     */
    public boolean deleteLoginUserByUserKey(String userKey)
    {
        return StringUtils.isNotEmpty(userKey) && redisService.deleteObject(getTokenKey(userKey));
    }

    /**
     * 删除指定用户的全部在线会话。管理员重置密码后调用，避免旧会话继续使用。
     *
     * @param userId 用户ID
     * @return 删除的会话数量
     */
    public int deleteLoginUsersByUserId(Long userId)
    {
        if (userId == null)
        {
            return 0;
        }
        KeyScanResult scanResult = redisService.scanKeys(ACCESS_TOKEN + "*", SESSION_SCAN_BATCH_SIZE,
                SESSION_SCAN_LIMIT);
        if (scanResult.isTruncated())
        {
            throw new IllegalStateException("登录会话数量超过安全扫描上限，拒绝执行不完整的会话撤销");
        }
        Collection<String> keys = scanResult.getKeys();
        if (keys == null || keys.isEmpty())
        {
            return 0;
        }
        List<String> keyList = new ArrayList<>(keys);
        List<Object> cachedUsers = new ArrayList<>(keyList.size());
        for (int start = 0; start < keyList.size(); start += SESSION_READ_BATCH_SIZE)
        {
            int end = Math.min(start + SESSION_READ_BATCH_SIZE, keyList.size());
            List<String> batchKeys = keyList.subList(start, end);
            List<Object> batchUsers = redisService.getMultiCacheObject(batchKeys);
            if (batchUsers == null || batchUsers.size() != batchKeys.size())
            {
                throw new IllegalStateException("批量读取登录会话结果不完整，拒绝执行会话撤销");
            }
            cachedUsers.addAll(batchUsers);
        }
        List<String> matchedKeys = new ArrayList<>();
        for (int i = 0; i < keyList.size(); i++)
        {
            LoginUser loginUser = parseCachedLoginUser(cachedUsers.get(i));
            Long cachedUserId = loginUser == null ? null : loginUser.getUserid();
            if (cachedUserId == null && loginUser != null && loginUser.getSysUser() != null)
            {
                cachedUserId = loginUser.getSysUser().getUserId();
            }
            if (userId.equals(cachedUserId))
            {
                matchedKeys.add(keyList.get(i));
            }
        }
        return !matchedKeys.isEmpty() && redisService.deleteObject(matchedKeys) ? matchedKeys.size() : 0;
    }

    private LoginUser parseCachedLoginUser(Object cacheUser)
    {
        try
        {
            return asLoginUser(cacheUser);
        }
        catch (RuntimeException e)
        {
            String cacheType = cacheUser == null ? "null" : cacheUser.getClass().getName();
            log.warn("忽略无法解析的在线会话缓存，缓存类型={}", cacheType);
            return null;
        }
    }

    private LoginUser asLoginUser(Object cacheUser)
    {
        if (cacheUser instanceof LoginUser)
        {
            return (LoginUser) cacheUser;
        }
        if (cacheUser instanceof JSONObject || cacheUser instanceof Map)
        {
            return JSON.parseObject(JSON.toJSONString(cacheUser), LoginUser.class);
        }
        return null;
    }

    /**
     * 设置用户身份信息
     */
    public void setLoginUser(LoginUser loginUser)
    {
        if (StringUtils.isNotNull(loginUser) && StringUtils.isNotEmpty(loginUser.getToken()))
        {
            refreshToken(loginUser);
        }
    }

    /**
     * 删除用户缓存信息
     */
    public void delLoginUser(String token)
    {
        if (StringUtils.isNotEmpty(token))
        {
            String userkey = JwtUtils.getUserKey(token);
            String userId = JwtUtils.getUserId(token);
            redisService.deleteObject(getTokenKey(userkey));
            if (StringUtils.isNotEmpty(userId))
            {
                redisService.removeCacheSetValue(getUserTokenIndexKey(userId), userkey);
            }
        }
    }

    /**
     * Invalidates all cached sessions for one user without scanning the Redis keyspace.
     */
    public void invalidateUserSessions(Long userId)
    {
        invalidateUserSessions(userId, null);
    }

    /**
     * Invalidates all cached sessions except an optional token UUID (not the JWT string).
     */
    public void invalidateUserSessions(Long userId, String retainedToken)
    {
        if (userId == null)
        {
            return;
        }
        String indexKey = getUserTokenIndexKey(String.valueOf(userId));
        Set<String> tokens = redisService.getCacheSet(indexKey);
        if (tokens == null || tokens.isEmpty())
        {
            redisService.deleteObject(indexKey);
            return;
        }
        for (String token : tokens)
        {
            if (StringUtils.isEmpty(token) || token.equals(retainedToken))
            {
                continue;
            }
            redisService.deleteObject(getTokenKey(token));
            redisService.removeCacheSetValue(indexKey, token);
        }
        if (StringUtils.isEmpty(retainedToken))
        {
            redisService.deleteObject(indexKey);
        }
        else
        {
            redisService.expire(indexKey, TOKEN_EXPIRE_TIME, TimeUnit.MINUTES);
        }
    }

    /**
     * 验证令牌有效期，相差不足120分钟，自动刷新缓存
     *
     * @param loginUser
     */
    public void verifyToken(LoginUser loginUser)
    {
        long expireTime = loginUser.getExpireTime();
        long currentTime = System.currentTimeMillis();
        if (expireTime - currentTime <= TOKEN_REFRESH_THRESHOLD_MINUTES)
        {
            refreshToken(loginUser);
        }
    }

    /**
     * 刷新令牌有效期
     *
     * @param loginUser 登录信息
     */
    public void refreshToken(LoginUser loginUser)
    {
        loginUser.setLoginTime(System.currentTimeMillis());
        loginUser.setExpireTime(loginUser.getLoginTime() + TOKEN_EXPIRE_TIME * MILLIS_MINUTE);
        // 根据uuid将loginUser缓存
        String userKey = getTokenKey(loginUser.getToken());
        redisService.setCacheObject(userKey, loginUser, TOKEN_EXPIRE_TIME, TimeUnit.MINUTES);
        if (loginUser.getUserid() != null)
        {
            String indexKey = getUserTokenIndexKey(String.valueOf(loginUser.getUserid()));
            redisService.addCacheSetValue(indexKey, loginUser.getToken());
            redisService.expire(indexKey, TOKEN_EXPIRE_TIME, TimeUnit.MINUTES);
        }
    }

    private String getTokenKey(String token)
    {
        return ACCESS_TOKEN + token;
    }

    private String getUserTokenIndexKey(String userId)
    {
        return USER_TOKEN_INDEX + userId;
    }
}
