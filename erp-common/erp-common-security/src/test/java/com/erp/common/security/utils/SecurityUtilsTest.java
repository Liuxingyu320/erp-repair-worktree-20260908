package com.erp.common.security.utils;

import java.util.Collections;
import java.util.HashSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.system.api.model.LoginUser;

import static org.assertj.core.api.Assertions.assertThat;

public class SecurityUtilsTest
{
    @AfterEach
    public void tearDown()
    {
        System.clearProperty("erp.security.legacy-user-id-admin");
        System.clearProperty("erp.security.admin-role-bypass-enabled");
        System.clearProperty("erp.security.wildcard-permission-bypass-enabled");
        SecurityContextHolder.remove();
    }

    @Test
    public void testAdminRoleDoesNotMarkCurrentUserAsAdminByDefault()
    {
        SecurityContextHolder.setUserId("200");
        LoginUser loginUser = new LoginUser();
        loginUser.setRoles(new HashSet<>(Collections.singletonList("admin")));
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);

        assertThat(SecurityUtils.isAdmin()).isFalse();
    }

    @Test
    public void testAdminRoleCanMarkCurrentUserAsAdminWhenBypassIsEnabled()
    {
        System.setProperty("erp.security.admin-role-bypass-enabled", "true");
        SecurityContextHolder.setUserId("200");
        LoginUser loginUser = new LoginUser();
        loginUser.setRoles(new HashSet<>(Collections.singletonList("admin")));
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);

        assertThat(SecurityUtils.isAdmin()).isTrue();
    }

    @Test
    public void testWildcardPermissionDoesNotMarkCurrentUserAsAdminByDefault()
    {
        SecurityContextHolder.setUserId("201");
        LoginUser loginUser = new LoginUser();
        loginUser.setPermissions(new HashSet<>(Collections.singletonList("*:*:*")));
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);

        assertThat(SecurityUtils.isAdmin()).isFalse();
    }

    @Test
    public void testWildcardPermissionCanMarkCurrentUserAsAdminWhenBypassIsEnabled()
    {
        System.setProperty("erp.security.wildcard-permission-bypass-enabled", "true");
        SecurityContextHolder.setUserId("201");
        LoginUser loginUser = new LoginUser();
        loginUser.setPermissions(new HashSet<>(Collections.singletonList("*:*:*")));
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);

        assertThat(SecurityUtils.isAdmin()).isTrue();
    }

    @Test
    public void testUserIdOneIsAdminEvenWhenLegacySwitchIsFalse()
    {
        System.setProperty("erp.security.legacy-user-id-admin", "false");
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, new LoginUser());

        assertThat(SecurityUtils.isAdmin()).isTrue();
    }

    @Test
    public void testUserIdOneIsAdminByDefault()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, new LoginUser());

        assertThat(SecurityUtils.isAdmin()).isTrue();
    }

    @Test
    public void testLegacyUserIdAdminSwitchDoesNotChangeUserIdOneAdmin()
    {
        System.setProperty("erp.security.legacy-user-id-admin", "true");
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, new LoginUser());

        assertThat(SecurityUtils.isAdmin()).isTrue();
    }

    @Test
    public void testDirectUserIdOneAdminCheckIsEnabledByDefault()
    {
        assertThat(SecurityUtils.isAdmin(1L)).isTrue();
    }

    @Test
    public void testDirectLegacyUserIdAdminSwitchDoesNotChangeUserIdOneAdmin()
    {
        System.setProperty("erp.security.legacy-user-id-admin", "true");

        assertThat(SecurityUtils.isAdmin(1L)).isTrue();
    }
}
