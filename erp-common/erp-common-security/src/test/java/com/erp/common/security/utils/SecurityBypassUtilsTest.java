package com.erp.common.security.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SecurityBypassUtilsTest
{
    @AfterEach
    void tearDown()
    {
        System.clearProperty("erp.security.admin-role-bypass-enabled");
        System.clearProperty("erp.security.wildcard-permission-bypass-enabled");
    }

    @Test
    void bypassesShouldBeDisabledByDefault()
    {
        assertThat(SecurityBypassUtils.adminRoleBypassEnabled()).isFalse();
        assertThat(SecurityBypassUtils.wildcardPermissionBypassEnabled()).isFalse();
    }

    @Test
    void bypassesShouldRequireExplicitOptIn()
    {
        System.setProperty("erp.security.admin-role-bypass-enabled", "true");
        System.setProperty("erp.security.wildcard-permission-bypass-enabled", "true");

        assertThat(SecurityBypassUtils.adminRoleBypassEnabled()).isTrue();
        assertThat(SecurityBypassUtils.wildcardPermissionBypassEnabled()).isTrue();
    }
}
