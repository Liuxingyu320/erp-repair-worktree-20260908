package com.erp.system.controller;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.domain.R;
import com.erp.common.security.aspect.IdempotentSubmitAspect;
import com.erp.common.security.service.IdempotentSubmitService;
import com.erp.common.security.service.TokenService;
import com.erp.system.api.RemoteFileService;
import com.erp.system.api.domain.SysFile;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.model.LoginUser;
import com.erp.system.service.ISysUserService;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SysProfileAvatarUploadTest
{
    @AfterEach void clear() { SecurityContextHolder.remove(); }

    @Test void ordinaryUserCanUploadAndBusinessFailureReleasesGuardImmediately()
    {
        LoginUser login = new LoginUser();
        login.setUserid(42L);
        login.setSysUser(new SysUser(42L));
        login.getSysUser().setAvatar("/old.png");
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, login);
        RemoteFileService remote = mock(RemoteFileService.class);
        ISysUserService users = mock(ISysUserService.class);
        TokenService tokens = mock(TokenService.class);
        SysProfileController target = new SysProfileController();
        ReflectionTestUtils.setField(target, "remoteFileService", remote);
        ReflectionTestUtils.setField(target, "userService", users);
        ReflectionTestUtils.setField(target, "tokenService", tokens);
        IdempotentSubmitService guard = mock(IdempotentSubmitService.class);
        Set<String> acquired = new HashSet<>();
        when(guard.tryAcquire(anyString(), anyLong())).thenAnswer(call -> acquired.add(call.getArgument(0)) ? "test-owner" : null);
        doAnswer(call -> { acquired.remove(call.getArgument(0)); return null; }).when(guard).release(anyString(), anyString());
        IdempotentSubmitAspect aspect = new IdempotentSubmitAspect();
        ReflectionTestUtils.setField(aspect, "idempotentSubmitService", guard);
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(aspect);
        SysProfileController controller = factory.getProxy();
        MockMultipartFile file = new MockMultipartFile("avatarfile", "phone.PNG", "image/png", new byte[] {1});
        SysFile uploaded = new SysFile(); uploaded.setUrl("/new.png");
        when(remote.uploadInner(file, SecurityConstants.INNER)).thenReturn(R.fail("offline"), R.ok(uploaded));
        when(users.updateUserAvatar(42L, "/new.png")).thenReturn(true);
        assertThatThrownBy(() -> controller.avatar(file)).hasMessageContaining("头像上传失败");
        assertThat(acquired).isEmpty();
        assertThat(controller.avatar(file).get("imgUrl")).isEqualTo("/new.png");
        assertThatThrownBy(() -> controller.avatar(file)).hasMessageContaining("请勿重复提交");
        verify(remote, times(2)).uploadInner(file, SecurityConstants.INNER);
        verify(remote, never()).upload(any());
        verify(remote, never()).delete(anyString());
        verify(users).updateUserAvatar(42L, "/new.png");
        verify(tokens).setLoginUser(login);
    }

    @Test void unsupportedFormatFailsBeforeRemoteUpload()
    {
        SysProfileController controller = new SysProfileController();
        LoginUser login = new LoginUser(); login.setUserid(42L); login.setSysUser(new SysUser(42L));
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, login);
        assertThatThrownBy(() -> controller.avatar(new MockMultipartFile("avatarfile", "photo.HEIC", "image/heic", new byte[] {1})))
                .hasMessageContaining("JPG、JPEG、PNG");
    }
}
