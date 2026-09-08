package com.erp.common.security.aspect;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.auth.PasswordChangeRequiredException;
import com.erp.common.security.annotation.AllowPasswordChangeRequired;
import com.erp.common.security.annotation.RequiresLogin;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.model.LoginUser;

class PreAuthorizeAspectTest
{
    private final PreAuthorizeAspect aspect = new PreAuthorizeAspect();

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    void temporaryCredentialUserCannotCallOrdinarySecuredMethod() throws Exception
    {
        bindLoginUser("1");

        assertThatThrownBy(() -> aspect.checkPasswordChangeRequired(method("ordinary")))
                .isInstanceOf(PasswordChangeRequiredException.class)
                .hasMessage("当前账号必须先修改密码");
    }

    @Test
    void temporaryCredentialUserCanCallExplicitlyAllowedMethod() throws Exception
    {
        bindLoginUser("1");

        assertThatCode(() -> aspect.checkPasswordChangeRequired(method("passwordChange")))
                .doesNotThrowAnyException();
    }

    @Test
    void ordinaryUserCanCallSecuredMethod() throws Exception
    {
        bindLoginUser("0");

        assertThatCode(() -> aspect.checkPasswordChangeRequired(method("ordinary")))
                .doesNotThrowAnyException();
    }

    @Test
    void migrationKillSwitchDisablesPasswordChangeGate() throws Exception
    {
        bindLoginUser("1");
        ReflectionTestUtils.setField(aspect, "mustChangePasswordEnabled", false);

        assertThatCode(() -> aspect.checkPasswordChangeRequired(method("ordinary")))
                .doesNotThrowAnyException();
    }

    private Method method(String name) throws Exception
    {
        return SecuredMethods.class.getDeclaredMethod(name);
    }

    private void bindLoginUser(String mustChangePassword)
    {
        SysUser user = new SysUser();
        user.setUserId(12L);
        user.setUserName("temporary-user");
        user.setMustChangePassword(mustChangePassword);
        LoginUser loginUser = new LoginUser();
        loginUser.setUserid(user.getUserId());
        loginUser.setSysUser(user);
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);
    }

    private static class SecuredMethods
    {
        @RequiresLogin
        void ordinary()
        {
        }

        @RequiresLogin
        @AllowPasswordChangeRequired
        void passwordChange()
        {
        }
    }
}
