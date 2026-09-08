package com.erp.common.core.utils;

import java.util.Map;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.constant.TokenConstants;
import com.erp.common.core.text.Convert;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

/**
 * Jwt工具类
 *
 * @author erp
 */
public class JwtUtils
{
    private static final Logger log = LoggerFactory.getLogger(JwtUtils.class);

    private static final String REQUIRED_SECRET_MESSAGE = "生产环境必须配置外部JWT密钥";

    private static final String ENV_SECRET = "ERP_JWT_SECRET";

    private static final String SYS_PROP_SECRET = "erp.jwt.secret";

    private static final String SYS_PROP_LEGACY_SECRET = "jwt.secret";

    private static final String SYS_PROP_TOKEN_SECRET = "token.secret";

    private static final String ACTIVE_PROFILE_PROPERTY = "spring.profiles.active";

    private static final String[] DEVELOPMENT_PROFILES = { "dev", "local", "test" };

    public static String secret = TokenConstants.SECRET;

    /**
     * 从数据声明生成令牌
     *
     * @param claims 数据声明
     * @return 令牌
     */
    public static String createToken(Map<String, Object> claims)
    {
        String token = Jwts.builder().setClaims(claims).signWith(SignatureAlgorithm.HS512, currentSecret()).compact();
        return token;
    }

    /**
     * 从令牌中获取数据声明
     *
     * @param token 令牌
     * @return 数据声明
     */
    public static Claims parseToken(String token)
    {
        return Jwts.parser().setSigningKey(currentSecret()).parseClaimsJws(token).getBody();
    }

    private static String currentSecret()
    {
        secret = resolveSecret();
        return secret;
    }

    static String resolveSecret()
    {
        return resolveSecret(springEnvironment(), System.getenv(ENV_SECRET));
    }

    static String resolveSecret(String envSecret)
    {
        return resolveSecret(springEnvironment(), envSecret);
    }

    public static void validateExternalSecretForProduction(Environment environment)
    {
        resolveSecret(environment, null);
    }

    private static String resolveSecret(Environment environment, String envSecret)
    {
        String configuredSecret = firstNotBlank(System.getProperty(SYS_PROP_SECRET),
                System.getProperty(SYS_PROP_LEGACY_SECRET), System.getProperty(SYS_PROP_TOKEN_SECRET),
                environmentProperty(environment, SYS_PROP_SECRET), environmentProperty(environment, SYS_PROP_LEGACY_SECRET),
                environmentProperty(environment, SYS_PROP_TOKEN_SECRET), environmentProperty(environment, ENV_SECRET),
                envSecret);
        if (configuredSecret == null && requiresExternalSecret(environment))
        {
            throw new IllegalStateException(REQUIRED_SECRET_MESSAGE);
        }
        return configuredSecret == null ? TokenConstants.SECRET : configuredSecret;
    }

    private static String environmentProperty(Environment environment, String key)
    {
        if (environment == null)
        {
            return null;
        }
        try
        {
            return environment.getProperty(key);
        }
        catch (Exception ex)
        {
            log.debug("读取JWT配置失败: {}", key, ex);
            return null;
        }
    }

    private static boolean requiresExternalSecret(Environment environment)
    {
        String configuredProfiles = firstNotBlank(System.getProperty(ACTIVE_PROFILE_PROPERTY),
                environmentProperty(environment, ACTIVE_PROFILE_PROPERTY));
        if (environment != null)
        {
            String[] activeProfiles = environment.getActiveProfiles();
            if (activeProfiles != null && activeProfiles.length > 0)
            {
                return !allDevelopmentProfiles(activeProfiles);
            }
        }
        if (configuredProfiles != null)
        {
            return !allDevelopmentProfiles(configuredProfiles.split("[,;\\s]+"));
        }
        return true;
    }

    private static boolean allDevelopmentProfiles(String[] profiles)
    {
        if (profiles == null || profiles.length == 0)
        {
            return false;
        }
        for (String profile : profiles)
        {
            if (!isDevelopmentProfile(profile))
            {
                return false;
            }
        }
        return true;
    }

    private static boolean isDevelopmentProfile(String profile)
    {
        if (profile == null)
        {
            return false;
        }
        for (String developmentProfile : DEVELOPMENT_PROFILES)
        {
            if (developmentProfile.equalsIgnoreCase(profile.trim()))
            {
                return true;
            }
        }
        return false;
    }

    private static Environment springEnvironment()
    {
        try
        {
            return SpringUtils.getBean(Environment.class);
        }
        catch (Exception ex)
        {
            log.debug("Spring Environment未就绪，使用静态JWT配置解析", ex);
            return null;
        }
    }

    private static String firstNotBlank(String... values)
    {
        if (values == null)
        {
            return null;
        }
        for (String value : values)
        {
            if (StringUtils.isNotEmpty(value) && StringUtils.isNotEmpty(value.trim()))
            {
                return value.trim();
            }
        }
        return null;
    }

    /**
     * 根据令牌获取用户标识
     * 
     * @param token 令牌
     * @return 用户ID
     */
    public static String getUserKey(String token)
    {
        Claims claims = parseToken(token);
        return getValue(claims, SecurityConstants.USER_KEY);
    }

    /**
     * 根据令牌获取用户标识
     * 
     * @param claims 身份信息
     * @return 用户ID
     */
    public static String getUserKey(Claims claims)
    {
        return getValue(claims, SecurityConstants.USER_KEY);
    }

    /**
     * 根据令牌获取用户ID
     * 
     * @param token 令牌
     * @return 用户ID
     */
    public static String getUserId(String token)
    {
        Claims claims = parseToken(token);
        return getValue(claims, SecurityConstants.DETAILS_USER_ID);
    }

    /**
     * 根据身份信息获取用户ID
     * 
     * @param claims 身份信息
     * @return 用户ID
     */
    public static String getUserId(Claims claims)
    {
        return getValue(claims, SecurityConstants.DETAILS_USER_ID);
    }

    /**
     * 根据令牌获取用户名
     * 
     * @param token 令牌
     * @return 用户名
     */
    public static String getUserName(String token)
    {
        Claims claims = parseToken(token);
        return getValue(claims, SecurityConstants.DETAILS_USERNAME);
    }

    /**
     * 根据身份信息获取用户名
     * 
     * @param claims 身份信息
     * @return 用户名
     */
    public static String getUserName(Claims claims)
    {
        return getValue(claims, SecurityConstants.DETAILS_USERNAME);
    }

    /**
     * 根据身份信息获取键值
     * 
     * @param claims 身份信息
     * @param key 键
     * @return 值
     */
    public static String getValue(Claims claims, String key)
    {
        return Convert.toStr(claims.get(key), "");
    }
}
