package com.erp.common.security.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.security.annotation.AllowsTemporaryCredential;
import com.erp.common.security.exception.CredentialRestrictionException;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.model.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class CredentialStateInterceptorTest
{
    private static final Instant NOW = Instant.parse("2026-07-14T02:00:00Z");

    @AfterEach
    void clearContext()
    {
        SecurityContextHolder.remove();
    }

    @Test
    void temporaryCredentialCanOnlyReachExplicitlyAnnotatedHandler() throws Exception
    {
        setLoginUser(SysUser.CREDENTIAL_STATE_TEMPORARY, Date.from(NOW.plusSeconds(600)));
        CredentialStateInterceptor interceptor = interceptor();

        assertThat(interceptor.preHandle(request(), response(), handler("allowed"))).isTrue();
        assertThatThrownBy(() -> interceptor.preHandle(request(), response(), handler("business")))
                .isInstanceOf(CredentialRestrictionException.class)
                .extracting("businessCode")
                .isEqualTo(CredentialRestrictionException.CHANGE_REQUIRED);
    }

    @Test
    void expiredTemporaryCredentialIsRejectedEvenForAllowedHandler() throws Exception
    {
        setLoginUser(SysUser.CREDENTIAL_STATE_TEMPORARY, Date.from(NOW.minusSeconds(1)));

        assertThatThrownBy(() -> interceptor().preHandle(request(), response(), handler("allowed")))
                .isInstanceOf(CredentialRestrictionException.class)
                .extracting("businessCode")
                .isEqualTo(CredentialRestrictionException.TEMPORARY_EXPIRED);
    }

    @Test
    void activeCredentialCanReachNormalBusinessHandler() throws Exception
    {
        setLoginUser(SysUser.CREDENTIAL_STATE_ACTIVE, null);
        assertThat(interceptor().preHandle(request(), response(), handler("business"))).isTrue();
    }

    private CredentialStateInterceptor interceptor()
    {
        return new CredentialStateInterceptor(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private HandlerMethod handler(String methodName) throws Exception
    {
        TestController controller = new TestController();
        Method method = TestController.class.getDeclaredMethod(methodName);
        return new HandlerMethod(controller, method);
    }

    private HttpServletRequest request()
    {
        return mock(HttpServletRequest.class);
    }

    private HttpServletResponse response()
    {
        return mock(HttpServletResponse.class);
    }

    private void setLoginUser(String state, Date expiresAt)
    {
        LoginUser user = new LoginUser();
        user.setUserid(42L);
        user.setCredentialState(state);
        user.setTemporaryPasswordExpiresAt(expiresAt);
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, user);
    }

    private static class TestController
    {
        @AllowsTemporaryCredential
        public void allowed()
        {
        }

        public void business()
        {
        }
    }
}
