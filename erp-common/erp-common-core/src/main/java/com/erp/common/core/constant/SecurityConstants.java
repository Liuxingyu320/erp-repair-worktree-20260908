package com.erp.common.core.constant;

/**
 * 权限相关通用常量
 * 
 * @author erp
 */
public class SecurityConstants
{
    /**
     * 用户ID字段
     */
    public static final String DETAILS_USER_ID = "user_id";

    /**
     * 用户名字段
     */
    public static final String DETAILS_USERNAME = "username";

    /**
     * 授权信息字段
     */
    public static final String AUTHORIZATION_HEADER = "Authorization";

    /**
     * HttpOnly Web 会话 Cookie
     */
    public static final String SESSION_COOKIE = "ERP_SESSION";

    /**
     * 浏览器可读的 CSRF Cookie，仅用于双提交校验，不包含登录凭证
     */
    public static final String CSRF_COOKIE = "XSRF-TOKEN";

    /**
     * CSRF 请求头
     */
    public static final String CSRF_HEADER = "X-XSRF-TOKEN";

    /**
     * 网关内部认证来源标记。入站同名请求头必须被覆盖，不能信任客户端值。
     */
    public static final String AUTH_SOURCE_HEADER = "X-ERP-Auth-Source";

    public static final String AUTH_SOURCE_BEARER = "bearer";

    public static final String AUTH_SOURCE_COOKIE = "cookie";

    /**
     * Web 登录协商头：cookie-preferred 客户端请求 dual 模式不返回 JWT。
     */
    public static final String SESSION_PREFERENCE_HEADER = "X-ERP-Session-Preference";

    public static final String SESSION_PREFERENCE_COOKIE = "cookie";

    public static final String CSRF_REQUIRED_HEADER = "X-ERP-CSRF-Required";

    /**
     * 请求来源
     */
    public static final String FROM_SOURCE = "from-source";

    /**
     * 内部请求
     */
    public static final String INNER = "inner";

    /**
     * 用户标识
     */
    public static final String USER_KEY = "user_key";

    /**
     * 登录用户
     */
    public static final String LOGIN_USER = "login_user";

    /**
     * 角色权限
     */
    public static final String ROLE_PERMISSION = "role_permission";
}
