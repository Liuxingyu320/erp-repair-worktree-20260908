package com.erp.system.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.erp.common.core.constant.CacheConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.log.annotation.Log;
import com.erp.common.redis.service.RedisService;
import com.erp.common.redis.service.RedisService.KeyScanResult;
import com.erp.common.security.service.TokenService;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.model.LoginUser;
import com.erp.system.domain.SysUserOnline;
import com.erp.system.domain.SysUserOnlineTableDataInfo;
import com.erp.system.service.ISysUserService;
import com.erp.system.service.impl.SysUserOnlineServiceImpl;

@DisplayName("在线会话治理边界")
class SysUserOnlineControllerTest
{
    private RedisService redisService;

    private TokenService tokenService;

    private ISysUserService userService;

    private SysUserOnlineController controller;

    @BeforeEach
    void setUp()
    {
        redisService = mock(RedisService.class);
        tokenService = mock(TokenService.class);
        userService = mock(ISysUserService.class);
        controller = new SysUserOnlineController();
        ReflectionTestUtils.setField(controller, "redisService", redisService);
        ReflectionTestUtils.setField(controller, "tokenService", tokenService);
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "userOnlineService", new SysUserOnlineServiceImpl());
        SecurityContextHolder.remove();
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("列表使用有界 SCAN、服务端分页并公开截断元数据")
    void listUsesBoundedScanServerPaginationAndVisibleMetadata()
    {
        String firstToken = token('a');
        String secondToken = token('b');
        String firstKey = CacheConstants.LOGIN_TOKEN_KEY + firstToken;
        String secondKey = CacheConstants.LOGIN_TOKEN_KEY + secondToken;
        String invalidKey = CacheConstants.LOGIN_TOKEN_KEY + token('c');
        when(redisService.scanKeys(CacheConstants.LOGIN_TOKEN_KEY + "*", 500, 5000))
                .thenReturn(new KeyScanResult(List.of(firstKey, secondKey, invalidKey), true, 4, 5000));
        when(tokenService.getLoginUsersForDisplay(List.of(firstKey, secondKey, invalidKey)))
                .thenReturn(java.util.Arrays.asList(loginUser(21L, "older", 100L), loginUser(22L, "newer", 200L), null));
        when(userService.selectVisibleUserIds(Set.of(21L, 22L))).thenReturn(Set.of(21L, 22L));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("pageNum", "2");
        request.setParameter("pageSize", "1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.setUserKey(firstToken);

        SysUserOnlineTableDataInfo response = (SysUserOnlineTableDataInfo) controller.list(null, null);

        assertThat(response.getTotal()).isEqualTo(2);
        assertThat(response.getRows()).hasSize(1);
        SysUserOnline row = (SysUserOnline) response.getRows().get(0);
        assertThat(row.getUserName()).isEqualTo("older");
        assertThat(row.getTokenId()).isEqualTo(firstToken);
        assertThat(row.isCurrentSession()).isTrue();
        assertThat(response.isTruncated()).isTrue();
        assertThat(response.getScanLimit()).isEqualTo(5000);
        assertThat(response.getScannedCount()).isEqualTo(4);
        assertThat(response.getInvalidSessionCount()).isEqualTo(1);
        verify(redisService, never()).keys(anyString());
    }

    @Test
    @DisplayName("列表隐藏当前操作人数据范围外的在线会话")
    void listFiltersSessionsByCurrentDataScope()
    {
        String visibleToken = token('2');
        String hiddenToken = token('3');
        String visibleKey = CacheConstants.LOGIN_TOKEN_KEY + visibleToken;
        String hiddenKey = CacheConstants.LOGIN_TOKEN_KEY + hiddenToken;
        when(redisService.scanKeys(CacheConstants.LOGIN_TOKEN_KEY + "*", 500, 5000))
                .thenReturn(new KeyScanResult(List.of(visibleKey, hiddenKey), false, 2, 5000));
        when(tokenService.getLoginUsersForDisplay(List.of(visibleKey, hiddenKey)))
                .thenReturn(List.of(loginUser(21L, "visible", 100L), loginUser(99L, "hidden", 200L)));
        when(userService.selectVisibleUserIds(Set.of(21L, 99L))).thenReturn(Set.of(21L));

        SysUserOnlineTableDataInfo response = (SysUserOnlineTableDataInfo) controller.list(null, null);

        assertThat(response.getTotal()).isEqualTo(1);
        assertThat(response.getRows()).extracting(row -> ((SysUserOnline) row).getUserName())
                .containsExactly("visible");
    }

    @Test
    @DisplayName("Redis 故障不能伪装为空在线列表")
    void listFailsVisibleWhenRedisIsUnavailable()
    {
        when(redisService.scanKeys(CacheConstants.LOGIN_TOKEN_KEY + "*", 500, 5000))
                .thenThrow(new IllegalStateException("redis unavailable"));

        assertThatThrownBy(() -> controller.list(null, null))
                .isInstanceOf(ServiceException.class)
                .hasMessage("在线状态暂不可用，请稍后重试");
        verify(redisService, never()).keys(anyString());
    }

    @Test
    @DisplayName("当前会话不能通过强退接口结束")
    void forceLogoutRejectsCurrentSession()
    {
        String tokenId = token('d');
        SecurityContextHolder.setUserKey(tokenId);

        assertThatThrownBy(() -> controller.forceLogout(tokenId))
                .isInstanceOf(ServiceException.class)
                .hasMessage("不能强退当前会话，请使用退出登录");
        verify(tokenService, never()).getLoginUserByUserKey(anyString());
        verify(tokenService, never()).deleteLoginUserByUserKey(anyString());
    }

    @Test
    @DisplayName("非管理员不能强退管理员会话")
    void forceLogoutRejectsAdminTargetForNonAdmin()
    {
        String tokenId = token('e');
        SecurityContextHolder.setUserId("22");
        LoginUser admin = loginUser(1L, "admin", 200L);
        admin.setRoles(Set.of("admin"));
        when(tokenService.getLoginUserByUserKey(tokenId)).thenReturn(admin);

        assertThatThrownBy(() -> controller.forceLogout(tokenId))
                .isInstanceOf(ServiceException.class)
                .hasMessage("不允许强退管理员会话");
        verify(userService, never()).checkUserDataScope(1L);
        verify(tokenService, never()).deleteLoginUserByUserKey(tokenId);
    }

    @Test
    @DisplayName("普通会话通过数据范围校验后才能强退")
    void forceLogoutChecksTargetDataScopeBeforeDelete()
    {
        String tokenId = token('f');
        when(tokenService.getLoginUserByUserKey(tokenId)).thenReturn(loginUser(42L, "staff", 200L));
        when(tokenService.deleteLoginUserByUserKey(tokenId)).thenReturn(true);

        AjaxResult response = controller.forceLogout(tokenId);

        verify(userService).checkUserDataScope(42L);
        verify(tokenService).deleteLoginUserByUserKey(tokenId);
        assertThat(response.get(AjaxResult.MSG_TAG)).isEqualTo("强退成功");
    }

    @Test
    @DisplayName("已离线会话的重复强退保持幂等")
    void forceLogoutIsIdempotentForMissingSession()
    {
        String tokenId = token('1');
        when(tokenService.getLoginUserByUserKey(tokenId)).thenReturn(null);
        when(tokenService.deleteLoginUserByUserKey(tokenId)).thenReturn(false);

        AjaxResult response = controller.forceLogout(tokenId);

        assertThat(response.get(AjaxResult.MSG_TAG)).isEqualTo("会话已离线");
        verify(userService, never()).checkUserDataScope(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("强退审计不保存原始会话标识")
    void forceLogoutAuditDoesNotPersistRawToken() throws Exception
    {
        Method method = SysUserOnlineController.class.getMethod("forceLogout", String.class);
        Log annotation = method.getAnnotation(Log.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.isSaveRequestData()).isFalse();
        assertThat(annotation.isSaveResponseData()).isFalse();
    }

    @Test
    @DisplayName("强退拒绝非预期格式的会话标识")
    void forceLogoutRejectsMalformedSessionId()
    {
        assertThatThrownBy(() -> controller.forceLogout("../../login_tokens:admin"))
                .isInstanceOf(ServiceException.class)
                .hasMessage("会话标识无效");
        verify(tokenService, never()).getLoginUserByUserKey(anyString());
    }

    private LoginUser loginUser(Long userId, String userName, Long loginTime)
    {
        SysUser sysUser = new SysUser();
        sysUser.setUserId(userId);
        sysUser.setUserName(userName);
        LoginUser loginUser = new LoginUser();
        loginUser.setUserid(userId);
        loginUser.setUsername(userName);
        loginUser.setLoginTime(loginTime);
        loginUser.setIpaddr("127.0.0." + userId);
        loginUser.setSysUser(sysUser);
        return loginUser;
    }

    private String token(char value)
    {
        return String.valueOf(value).repeat(32);
    }
}
