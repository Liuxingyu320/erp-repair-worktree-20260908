package com.erp.system.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.domain.R;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.security.annotation.InnerAuth;
import com.erp.common.security.annotation.RequiresLogin;
import com.erp.system.api.domain.UserNotificationCommand;
import com.erp.system.api.domain.UserNotificationResult;
import com.erp.system.domain.SysUserDeviceToken;
import com.erp.system.service.ISysUserNotificationService;

@ExtendWith(MockitoExtension.class)
class SysUserNotificationControllerTest
{
    @Mock
    private ISysUserNotificationService notificationService;

    private SysUserNotificationController controller;

    @BeforeEach
    void setUp()
    {
        controller = new SysUserNotificationController();
        ReflectionTestUtils.setField(controller, "notificationService", notificationService);
        SecurityContextHolder.setUserId("42");
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    void internalPublishMustUseInnerAuthenticationAndFixedPostRoute() throws Exception
    {
        Method method = SysUserNotificationController.class.getMethod("publish", UserNotificationCommand.class);

        assertThat(method.getAnnotation(InnerAuth.class)).isNotNull();
        assertThat(method.getAnnotation(PostMapping.class).value()).containsExactly("/inner/publish");
    }

    @Test
    void allCurrentUserEndpointsMustRequireLoginAndUseFixedRoutes() throws Exception
    {
        assertLoginGet("list", "/list");
        assertLoginGet("unreadCount", "/unread-count");

        Method read = SysUserNotificationController.class.getMethod("read", Long.class);
        assertThat(read.getAnnotation(RequiresLogin.class)).isNotNull();
        assertThat(read.getAnnotation(PostMapping.class).value()).containsExactly("/{id}/read");

        Method register = SysUserNotificationController.class.getMethod("registerDeviceToken", SysUserDeviceToken.class);
        assertThat(register.getAnnotation(RequiresLogin.class)).isNotNull();
        assertThat(register.getAnnotation(PostMapping.class).value()).containsExactly("/device-token");

        Method disable = SysUserNotificationController.class.getMethod("disableDeviceToken", SysUserDeviceToken.class);
        assertThat(disable.getAnnotation(RequiresLogin.class)).isNotNull();
        assertThat(disable.getAnnotation(DeleteMapping.class).value()).containsExactly("/device-token");
    }

    @Test
    void currentUserOperationsMustIgnoreUserIdFromRequestBody()
    {
        SysUserDeviceToken request = new SysUserDeviceToken();
        request.setUserId(999L);
        request.setPlatform("IOS");
        request.setToken("secret-token-123456");
        UserNotificationResult bound = new UserNotificationResult();
        bound.setAccepted(true);
        bound.setTokenSuffix("123456");
        when(notificationService.registerDeviceToken(42L, request)).thenReturn(bound);
        when(notificationService.selectUserNotifications(42L)).thenReturn(Collections.emptyList());
        when(notificationService.countUnread(42L)).thenReturn(2L);
        when(notificationService.markRead(7L, 42L)).thenReturn(1);
        when(notificationService.disableDeviceToken(42L, request)).thenReturn(1);

        AjaxResult list = controller.list();
        AjaxResult unread = controller.unreadCount();
        AjaxResult registered = controller.registerDeviceToken(request);
        AjaxResult read = controller.read(7L);
        AjaxResult disabled = controller.disableDeviceToken(request);

        assertThat(list.get(AjaxResult.DATA_TAG)).isEqualTo(Collections.emptyList());
        assertThat(unread.get(AjaxResult.DATA_TAG)).isEqualTo(2L);
        assertThat(registered.get(AjaxResult.DATA_TAG)).isEqualTo(bound);
        assertThat(read.isSuccess()).isTrue();
        assertThat(disabled.isSuccess()).isTrue();
        verify(notificationService).registerDeviceToken(42L, request);
        verify(notificationService).disableDeviceToken(42L, request);
        verify(notificationService).markRead(7L, 42L);
    }

    @Test
    void publishReturnsRemoteServiceEnvelope()
    {
        UserNotificationCommand command = new UserNotificationCommand();
        UserNotificationResult result = new UserNotificationResult();
        result.setAccepted(true);
        when(notificationService.publish(any(UserNotificationCommand.class))).thenReturn(result);

        R<UserNotificationResult> response = controller.publish(command);

        assertThat(response.getCode()).isEqualTo(R.SUCCESS);
        assertThat(response.getData()).isSameAs(result);
    }

    @Test
    void readAndLogoutShouldRemainIdempotentWhenNothingChanges()
    {
        SysUserDeviceToken request = new SysUserDeviceToken();
        request.setPlatform("ANDROID");
        request.setToken("already-disabled-123456");
        when(notificationService.markRead(7L, 42L)).thenReturn(0);
        when(notificationService.disableDeviceToken(42L, request)).thenReturn(0);

        assertThat(controller.read(7L).isSuccess()).isTrue();
        assertThat(controller.disableDeviceToken(request).isSuccess()).isTrue();
    }

    private static void assertLoginGet(String methodName, String route) throws Exception
    {
        Method method = SysUserNotificationController.class.getMethod(methodName);
        assertThat(method.getAnnotation(RequiresLogin.class)).isNotNull();
        assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly(route);
    }
}
