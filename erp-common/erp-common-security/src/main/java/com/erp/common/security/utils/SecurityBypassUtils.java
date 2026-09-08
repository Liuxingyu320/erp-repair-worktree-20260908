package com.erp.common.security.utils;

/**
 * Compatibility facade for the shared core bypass switches.
 *
 * <p>The implementation lives in {@code com.erp.common.core.utils.SecurityBypassUtils}
 * so modules outside {@code erp-common-security}, such as sensitive serialization, can
 * use the same switches without depending on the security module. Keep this class as a
 * thin delegate only; do not add independent bypass logic here.
 */
public final class SecurityBypassUtils
{
    private SecurityBypassUtils()
    {
    }

    public static boolean adminRoleBypassEnabled()
    {
        return com.erp.common.core.utils.SecurityBypassUtils.adminRoleBypassEnabled();
    }

    public static boolean wildcardPermissionBypassEnabled()
    {
        return com.erp.common.core.utils.SecurityBypassUtils.wildcardPermissionBypassEnabled();
    }
}
