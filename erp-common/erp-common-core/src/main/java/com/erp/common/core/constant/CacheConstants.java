package com.erp.common.core.constant;

/**
 * 缓存常量信息
 * 
 * @author erp
 */
public class CacheConstants
{
    /**
     * 缓存有效期，默认720（分钟）
     */
    public final static long EXPIRATION = 720;

    /**
     * 缓存刷新时间，默认120（分钟）
     */
    public final static long REFRESH_TIME = 120;

    /**
     * 密码最大错误次数
     */
    public final static int PASSWORD_MAX_RETRY_COUNT = 5;

    /**
     * 密码锁定时间，默认10（分钟）
     */
    public final static long PASSWORD_LOCK_TIME = 10;

    /**
     * 权限缓存前缀
     */
    public final static String LOGIN_TOKEN_KEY = "login_tokens:";

    /** 每个用户持有的 token UUID 索引，在线失效会话时禁止使用 Redis KEYS。 */
    public final static String USER_LOGIN_TOKEN_KEY = "user_login_tokens:";

    /**
     * CSRF token 缓存前缀。值与 JWT 内的 session UUID 一一绑定。
     */
    public final static String CSRF_TOKEN_KEY = "csrf_tokens:";

    /**
     * 验证码 redis key
     */
    public static final String CAPTCHA_CODE_KEY = "captcha_codes:";

    /**
     * 参数管理 cache key
     */
    public static final String SYS_CONFIG_KEY = "sys_config:";

    /**
     * 字典管理 cache key
     */
    public static final String SYS_DICT_KEY = "sys_dict:";

    /**
     * 登录账户密码错误次数 redis key
     */
    public static final String PWD_ERR_CNT_KEY = "pwd_err_cnt:";

    /**
     * 登录IP黑名单 cache key
     */
    public static final String SYS_LOGIN_BLACKIPLIST = SYS_CONFIG_KEY + "sys.login.blackIPList";

    /**
     * 进销存-分类树 cache key
     */
    public static final String INV_CATEGORY_KEY = "inv_category:";

    /**
     * 进销存-商品 cache key
     */
    public static final String INV_PRODUCT_KEY = "inv_product:";

    /**
     * OA-采购申请 cache key
     */
    public static final String OA_PURCHASE_KEY = "oa_purchase:";
}
