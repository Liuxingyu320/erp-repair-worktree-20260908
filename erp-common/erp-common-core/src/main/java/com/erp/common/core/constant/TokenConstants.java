package com.erp.common.core.constant;

/**
 * Token的Key常量
 * 
 * @author erp
 */
public class TokenConstants
{
    /**
     * 令牌前缀
     */
    public static final String PREFIX = "Bearer ";

    /**
     * 令牌秘钥。
     * 仅作为本地开发兜底值，生产环境必须通过 ERP_JWT_SECRET 或 erp.jwt.secret 配置外部密钥。
     */
    public final static String SECRET = "erp-local-dev-jwt-secret-8c0d4f2b79a1";

}
