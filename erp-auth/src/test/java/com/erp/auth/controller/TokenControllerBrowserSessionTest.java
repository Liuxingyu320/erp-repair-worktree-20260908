package com.erp.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.auth.config.BrowserSessionProperties;
import com.erp.auth.form.LoginBody;
import com.erp.auth.service.SysLoginService;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.constant.TokenConstants;
import com.erp.common.core.domain.R;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.security.BrowserSessionSecurity;
import com.erp.common.core.utils.JwtUtils;
import com.erp.common.security.service.TokenService;
import com.erp.system.api.model.LoginUser;

class TokenControllerBrowserSessionTest
{
    private static final String CSRF_SECRET = "erp-new-2-browser-csrf-test-secret-0001";

    private String originalProfile;

    private TokenController controller;

    private TokenService tokenService;

    private SysLoginService loginService;

    private BrowserSessionProperties properties;

    @BeforeEach
    void setUp()
    {
        originalProfile = System.getProperty("spring.profiles.active");
        System.setProperty("spring.profiles.active", "local");
        controller = new TokenController();
        tokenService = mock(TokenService.class);
        loginService = mock(SysLoginService.class);
        properties = new BrowserSessionProperties();
        properties.setEnabled(true);
        properties.setCsrfSecret(CSRF_SECRET);
        ReflectionTestUtils.setField(controller, "tokenService", tokenService);
        ReflectionTestUtils.setField(controller, "sysLoginService", loginService);
        ReflectionTestUtils.setField(controller, "browserSessionProperties", properties);
    }

    @AfterEach
    void tearDown()
    {
        if (originalProfile == null)
        {
            System.clearProperty("spring.profiles.active");
        }
        else
        {
            System.setProperty("spring.profiles.active", originalProfile);
        }
    }

    @Test
    void browserLoginStoresTokenOnlyInDedicatedHttpOnlyCookie()
    {
        LoginBody form = new LoginBody();
        form.setUsername("erp-new-2-user");
        form.setPassword("test-password");
        LoginUser loginUser = new LoginUser();
        when(loginService.login("erp-new-2-user", "test-password")).thenReturn(loginUser);
        when(tokenService.createToken(loginUser)).thenReturn(Map.of(
                "access_token", "signed-browser-token",
                "expires_in", 720L));
        MockHttpServletResponse response = new MockHttpServletResponse();

        R<Map<String, Object>> result = controller.browserLogin(form, response);

        assertThat(result.getData())
                .containsEntry("csrfToken", BrowserSessionSecurity.csrfToken(
                        "signed-browser-token", CSRF_SECRET))
                .containsEntry("expiresInMinutes", 720L)
                .doesNotContainKey("access_token");
        String cookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(cookie)
                .startsWith(BrowserSessionSecurity.COOKIE_NAME + "=signed-browser-token;")
                .contains("Path=/")
                .contains("HttpOnly")
                .contains("SameSite=Strict")
                .contains("Secure");
        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store, max-age=0");
        assertThat(response.getHeader(HttpHeaders.PRAGMA)).isEqualTo("no-cache");
    }

    @Test
    void browserSessionRefreshesOnlyTheBoundCsrfProof()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(SecurityConstants.AUTHORIZATION_HEADER,
                TokenConstants.PREFIX + "signed-browser-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        R<Map<String, Object>> result = controller.browserSession(request, response);

        assertThat(result.getData()).containsOnlyKeys("csrfToken");
        assertThat(result.getData().get("csrfToken")).isEqualTo(
                BrowserSessionSecurity.csrfToken("signed-browser-token", CSRF_SECRET));
        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
    }

    @Test
    void disabledBrowserSessionFailsBeforeCredentialsAreChecked()
    {
        properties.setEnabled(false);
        LoginBody form = new LoginBody();
        form.setUsername("erp-new-2-user");
        form.setPassword("test-password");

        assertThatThrownBy(() -> controller.browserLogin(form, new MockHttpServletResponse()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("ERP_NEW_2_BROWSER_SESSION_DISABLED");
        verifyNoInteractions(loginService, tokenService);
    }

    @Test
    void browserLogoutInvalidatesSessionAndClearsTheSameCookie()
    {
        String accessToken = JwtUtils.createToken(Map.of(
                SecurityConstants.USER_KEY, "browser-session-id",
                SecurityConstants.DETAILS_USER_ID, "42",
                SecurityConstants.DETAILS_USERNAME, "erp-new-2-user"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(SecurityConstants.AUTHORIZATION_HEADER,
                TokenConstants.PREFIX + accessToken);
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.browserLogout(request, response);

        verify(tokenService).delLoginUser(accessToken);
        verify(loginService).logout("erp-new-2-user");
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .startsWith(BrowserSessionSecurity.COOKIE_NAME + "=;")
                .contains("Max-Age=0")
                .contains("HttpOnly")
                .contains("SameSite=Strict")
                .contains("Secure");
    }
}
