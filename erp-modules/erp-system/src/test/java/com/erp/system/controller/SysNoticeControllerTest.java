package com.erp.system.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.security.annotation.RequiresLogin;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.system.domain.SysNotice;
import com.erp.system.domain.dto.SysNoticePublishRequest;
import com.erp.system.domain.dto.SysNoticeVersionRequest;
import com.erp.system.service.ISysNoticeReadService;

@DisplayName("通知公告控制器")
class SysNoticeControllerTest
{
    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("后台详情接口需要公告查询权限")
    void getInfoShouldRequireNoticeQueryPermission() throws Exception
    {
        RequiresPermissions permissions = SysNoticeController.class.getMethod("getInfo", Long.class)
                .getAnnotation(RequiresPermissions.class);

        assertThat(permissions).isNotNull();
        assertThat(permissions.value()).containsExactly("system:notice:query");
    }

    @Test
    @DisplayName("当前用户公告和已读操作要求登录")
    void currentUserNoticeEndpointsShouldRequireLogin() throws Exception
    {
        assertRequiresLogin(SysNoticeController.class.getMethod("listTop"));
        assertRequiresLogin(SysNoticeController.class.getMethod("getInboxInfo", Long.class));
        assertRequiresLogin(SysNoticeController.class.getMethod("markRead", Long.class));
        assertRequiresLogin(SysNoticeController.class.getMethod("markReadAll"));
    }

    @Test
    @DisplayName("发布、取消计划和下线均使用独立发布权限")
    void lifecycleEndpointsShouldRequireIndependentPublishPermission() throws Exception
    {
        assertPermission(SysNoticeController.class.getMethod(
                "publish", Long.class, SysNoticePublishRequest.class), "system:notice:publish");
        assertPermission(SysNoticeController.class.getMethod(
                "cancelSchedule", Long.class, SysNoticeVersionRequest.class), "system:notice:publish");
        assertPermission(SysNoticeController.class.getMethod(
                "offline", Long.class, SysNoticeVersionRequest.class), "system:notice:publish");
        assertPermission(SysNoticeController.class.getMethod(
                "createNewVersion", Long.class, SysNoticeVersionRequest.class), "system:notice:edit");
    }

    @Test
    @DisplayName("顶部公告未读数来自当前用户全部正常公告")
    void listTopUnreadCountShouldUseAllNormalNotices()
    {
        SecurityContextHolder.setUserId("7");
        List<SysNotice> topNotices = Arrays.asList(notice(5L, true), notice(4L, true), notice(3L, true),
                notice(2L, true), notice(1L, true));
        AtomicBoolean unreadCountQueried = new AtomicBoolean(false);
        ISysNoticeReadService noticeReadService = service((method, args) -> {
            if ("selectNoticeListWithReadStatus".equals(method))
            {
                assertThat(args[0]).isEqualTo(7L);
                assertThat(args[1]).isEqualTo(5);
                return topNotices;
            }
            if ("selectUnreadCount".equals(method))
            {
                assertThat(args[0]).isEqualTo(7L);
                unreadCountQueried.set(true);
                return 12;
            }
            throw unexpected(method);
        });
        SysNoticeController controller = new SysNoticeController();
        ReflectionTestUtils.setField(controller, "noticeReadService", noticeReadService);

        AjaxResult result = controller.listTop();

        assertThat(result.get(AjaxResult.DATA_TAG)).isEqualTo(topNotices);
        assertThat(result.get("unreadCount")).isEqualTo(12L);
        assertThat(unreadCountQueried).isTrue();
    }

    @Test
    @DisplayName("全部已读不接收顶部公告ID并返回数据库真实未读数")
    void markReadAllShouldUseCurrentUserAndReturnActualUnreadCount()
    {
        SecurityContextHolder.setUserId("7");
        AtomicReference<Long> markedUserId = new AtomicReference<>();
        ISysNoticeReadService noticeReadService = service((method, args) -> {
            if ("markAllRead".equals(method))
            {
                markedUserId.set((Long) args[0]);
                return 2;
            }
            throw unexpected(method);
        });
        SysNoticeController controller = new SysNoticeController();
        ReflectionTestUtils.setField(controller, "noticeReadService", noticeReadService);

        AjaxResult result = controller.markReadAll();

        assertThat(markedUserId.get()).isEqualTo(7L);
        assertThat(result.get("unreadCount")).isEqualTo(2);
    }

    private static SysNotice notice(Long noticeId, boolean isRead)
    {
        SysNotice notice = new SysNotice();
        notice.setNoticeId(noticeId);
        notice.setIsRead(isRead);
        return notice;
    }

    private static void assertRequiresLogin(Method method)
    {
        assertThat(method.getAnnotation(RequiresLogin.class))
                .as(method.getName() + " should require login")
                .isNotNull();
    }

    private static void assertPermission(Method method, String permission)
    {
        RequiresPermissions annotation = method.getAnnotation(RequiresPermissions.class);
        assertThat(annotation).as(method.getName() + " should require a permission").isNotNull();
        assertThat(annotation.value()).containsExactly(permission);
    }

    @SuppressWarnings("unchecked")
    private static ISysNoticeReadService service(BiFunction<String, Object[], Object> handler)
    {
        return (ISysNoticeReadService) Proxy.newProxyInstance(ISysNoticeReadService.class.getClassLoader(),
                new Class<?>[] { ISysNoticeReadService.class }, (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class)
                    {
                        return method.invoke(thisProxy(), args);
                    }
                    return handler.apply(method.getName(), args == null ? new Object[0] : args);
                });
    }

    private static Object thisProxy()
    {
        return new Object()
        {
            @Override
            public String toString()
            {
                return "SysNoticeReadServiceTestProxy";
            }
        };
    }

    private static AssertionError unexpected(String method)
    {
        return new AssertionError("Unexpected service call: " + method);
    }
}
