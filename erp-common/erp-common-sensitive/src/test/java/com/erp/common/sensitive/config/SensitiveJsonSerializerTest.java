package com.erp.common.sensitive.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.context.SecurityContextHolder;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SensitiveJsonSerializerTest
{
    @AfterEach
    void tearDown()
    {
        System.clearProperty("erp.security.admin-role-bypass-enabled");
        System.clearProperty("erp.security.wildcard-permission-bypass-enabled");
        SecurityContextHolder.remove();
    }

    @Test
    void adminRoleShouldStillBeDesensitizedByDefault() throws Exception
    {
        SecurityContextHolder.setUserId("200");
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER,
                new LoginUserFixture(Collections.singleton("admin"), Collections.emptySet()));

        assertThat(currentUserIsAdmin()).isFalse();
    }

    @Test
    void adminRoleCanBypassDesensitizationOnlyWhenEnabled() throws Exception
    {
        System.setProperty("erp.security.admin-role-bypass-enabled", "true");
        SecurityContextHolder.setUserId("200");
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER,
                new LoginUserFixture(Collections.singleton("admin"), Collections.emptySet()));

        assertThat(currentUserIsAdmin()).isTrue();
    }

    @Test
    void wildcardPermissionShouldStillBeDesensitizedByDefault() throws Exception
    {
        SecurityContextHolder.setUserId("201");
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER,
                new LoginUserFixture(Collections.emptySet(), Collections.singleton("*:*:*")));

        assertThat(currentUserIsAdmin()).isFalse();
    }

    @Test
    void wildcardPermissionCanBypassDesensitizationOnlyWhenEnabled() throws Exception
    {
        System.setProperty("erp.security.wildcard-permission-bypass-enabled", "true");
        SecurityContextHolder.setUserId("201");
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER,
                new LoginUserFixture(Collections.emptySet(), Collections.singleton("*:*:*")));

        assertThat(currentUserIsAdmin()).isTrue();
    }

    private boolean currentUserIsAdmin() throws Exception
    {
        Method method = SensitiveJsonSerializer.class.getDeclaredMethod("currentUserIsAdmin");
        method.setAccessible(true);
        return (boolean) method.invoke(new SensitiveJsonSerializer());
    }

    static class LoginUserFixture
    {
        private final Set<String> roles;

        private final Set<String> permissions;

        LoginUserFixture(Set<String> roles, Set<String> permissions)
        {
            this.roles = roles;
            this.permissions = permissions;
        }

        public Set<String> getRoles()
        {
            return roles;
        }

        public Set<String> getPermissions()
        {
            return permissions;
        }
    }
}
