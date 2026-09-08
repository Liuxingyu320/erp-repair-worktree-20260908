package com.erp.common.security.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Collections;
import java.util.HashSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.system.api.model.LoginUser;

class AuthLogicTest
{
    private final AuthLogic authLogic = new AuthLogic();

    @AfterEach
    void tearDown()
    {
        System.clearProperty("erp.security.admin-role-bypass-enabled");
        System.clearProperty("erp.security.wildcard-permission-bypass-enabled");
        SecurityContextHolder.remove();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void wildcardPermissionShouldNotBypassByDefault()
    {
        assertThat(authLogic.hasPermi(Collections.singleton("*:*:*"), "system:user:list")).isFalse();
    }

    @Test
    void wildcardPermissionShouldBypassWhenExplicitlyEnabled()
    {
        System.setProperty("erp.security.wildcard-permission-bypass-enabled", "true");

        assertThat(authLogic.hasPermi(Collections.singleton("*:*:*"), "system:user:list")).isTrue();
    }

    @Test
    void adminRoleShouldNotBypassByDefault()
    {
        assertThat(authLogic.hasRole(Collections.singleton("admin"), "store_manager")).isFalse();
    }

    @Test
    void adminRoleShouldBypassWhenExplicitlyEnabled()
    {
        System.setProperty("erp.security.admin-role-bypass-enabled", "true");

        assertThat(authLogic.hasRole(Collections.singleton("admin"), "store_manager")).isTrue();
    }

    @Test
    void userIdOneShouldBypassPermissionChecksByDefault()
    {
        loginAs(1L);

        assertThat(authLogic.hasPermi("system:salary:role")).isTrue();
        assertThatCode(() -> authLogic.checkPermi("system:salary:role")).doesNotThrowAnyException();
        assertThatCode(() -> authLogic.checkPermiAnd("system:salary:list", "system:salary:role"))
                .doesNotThrowAnyException();
        assertThatCode(() -> authLogic.checkPermiOr("system:salary:list", "system:salary:role"))
                .doesNotThrowAnyException();
    }

    private static void loginAs(Long userId)
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(SecurityConstants.AUTHORIZATION_HEADER, "Bearer test-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.setUserId(String.valueOf(userId));
        LoginUser loginUser = new LoginUser();
        loginUser.setUserid(userId);
        loginUser.setRoles(new HashSet<>());
        loginUser.setPermissions(new HashSet<>());
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);
    }
}
