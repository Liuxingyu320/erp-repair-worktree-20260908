package com.erp.common.core.utils;

/**
 * Global security bypass feature switches.
 */
public final class SecurityBypassUtils
{
    private static final String ADMIN_ROLE_BYPASS_PROPERTY = "erp.security.admin-role-bypass-enabled";

    private static final String ADMIN_ROLE_BYPASS_ENV = "ERP_SECURITY_ADMIN_ROLE_BYPASS_ENABLED";

    private static final String WILDCARD_PERMISSION_BYPASS_PROPERTY =
            "erp.security.wildcard-permission-bypass-enabled";

    private static final String WILDCARD_PERMISSION_BYPASS_ENV =
            "ERP_SECURITY_WILDCARD_PERMISSION_BYPASS_ENABLED";

    private SecurityBypassUtils()
    {
    }

    public static boolean adminRoleBypassEnabled()
    {
        return enabled(ADMIN_ROLE_BYPASS_PROPERTY, ADMIN_ROLE_BYPASS_ENV);
    }

    public static boolean wildcardPermissionBypassEnabled()
    {
        return enabled(WILDCARD_PERMISSION_BYPASS_PROPERTY, WILDCARD_PERMISSION_BYPASS_ENV);
    }

    private static boolean enabled(String property, String env)
    {
        String configured = System.getProperty(property);
        if (StringUtils.isEmpty(configured))
        {
            configured = System.getenv(env);
        }
        return configured != null && Boolean.parseBoolean(configured);
    }
}
