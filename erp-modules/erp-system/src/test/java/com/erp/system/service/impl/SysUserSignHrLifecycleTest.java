package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.domain.SysRole;
import java.util.List;
import com.erp.system.mapper.SysConfigMapper;
import com.erp.system.mapper.SysUserMapper;
import com.erp.system.mapper.SysUserPostMapper;
import com.erp.system.mapper.SysUserProfileMapper;
import com.erp.system.mapper.SysUserRoleMapper;
import com.erp.system.mapper.SysRoleMapper;
import com.erp.system.mapper.SysUserShopMapper;
import com.erp.system.service.SigningProfileNormalizer;
import com.erp.system.service.support.UserSessionInvalidationService;

@ExtendWith(MockitoExtension.class)
@DisplayName("唯一HR用户生命周期锁序")
class SysUserSignHrLifecycleTest
{
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private SysUserProfileMapper profileMapper;
    @Mock
    private SigningProfileNormalizer signingProfileNormalizer;
    @Mock
    private SysUserRoleMapper userRoleMapper;
    @Mock
    private SysRoleMapper roleMapper;
    @Mock
    private SysUserPostMapper userPostMapper;
    @Mock
    private SysUserShopMapper userShopMapper;
    @Mock
    private SysConfigMapper configMapper;
    @Mock
    private UserSessionInvalidationService userSessionInvalidationService;

    @InjectMocks
    private SysUserServiceImpl service;

    @Test
    @DisplayName("新增用户先锁状态并在写入后同步")
    void shouldLockBeforeInsertAndSyncAfterward()
    {
        SysUser user = user(88L, "0");
        when(userMapper.insertUser(user)).thenReturn(1);

        service.insertUser(user);

        InOrder order = inOrder(configMapper, userMapper);
        order.verify(configMapper).lockSignHrState();
        order.verify(userMapper).insertUser(user);
        order.verify(configMapper).syncSignHrPermissions();
    }

    @Test
    @DisplayName("用户授权先锁状态再改关联并同步")
    void shouldLockBeforeChangingUserRoles()
    {
        SysRole role = new SysRole();
        role.setRoleId(2L);
        role.setStatus("0");
        role.setDelFlag("0");
        when(roleMapper.selectRoleByIdForUpdate(2L)).thenReturn(role);
        when(userRoleMapper.lockUserForRoleAssignment(88L)).thenReturn(88L);
        when(userRoleMapper.selectRoleIdsByUserId(88L)).thenReturn(List.of());
        SecurityContextHolder.setUserId("1");
        try
        {
            service.insertUserAuth(88L, new Long[] { 2L });
        }
        finally
        {
            SecurityContextHolder.remove();
        }

        InOrder order = inOrder(configMapper, roleMapper, userRoleMapper);
        order.verify(configMapper).lockSignHrState();
        order.verify(roleMapper).selectRoleByIdForUpdate(2L);
        order.verify(userRoleMapper).lockUserForRoleAssignment(88L);
        order.verify(userRoleMapper).selectRoleIdsByUserId(88L);
        verify(userRoleMapper, never()).deleteUserRoleByUserId(88L);
        order.verify(userRoleMapper).batchUserRole(org.mockito.ArgumentMatchers.anyList());
        order.verify(configMapper).syncSignHrPermissions();
    }

    @Test
    @DisplayName("停用当前配置HR前明确拒绝")
    void shouldRejectDisablingConfiguredHr()
    {
        when(configMapper.selectConfiguredSignHrUserId()).thenReturn(88L);

        assertThatThrownBy(() -> service.updateUserStatus(user(88L, "1")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("请先更换签约默认接收人配置");

        verify(userMapper, never()).updateUserStatus(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("普通用户状态写入遵守状态锁到用户表的顺序并同步")
    void shouldLockBeforeUpdatingOtherUserStatus()
    {
        when(configMapper.selectConfiguredSignHrUserId()).thenReturn(99L);
        when(userMapper.updateUserStatus(88L, "1")).thenReturn(1);

        service.updateUserStatus(user(88L, "1"));

        InOrder order = inOrder(configMapper, userMapper);
        order.verify(configMapper).lockSignHrState();
        order.verify(configMapper).selectConfiguredSignHrUserId();
        order.verify(userMapper).updateUserStatus(88L, "1");
        order.verify(configMapper).syncSignHrPermissions();
    }

    @Test
    @DisplayName("通用用户更新不能把当前配置HR改为停用")
    void shouldRejectInvalidatingConfiguredHrThroughUpdate()
    {
        when(configMapper.selectConfiguredSignHrUserId()).thenReturn(88L);

        assertThatThrownBy(() -> service.updateUser(user(88L, "1")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("请先更换签约默认接收人配置");

        verify(userRoleMapper, never()).deleteUserRoleByUserId(88L);
        verify(userMapper, never()).updateUser(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("单删或批删当前配置HR都在删除关联前拒绝")
    void shouldRejectDeletingConfiguredHr()
    {
        when(configMapper.selectConfiguredSignHrUserId()).thenReturn(88L);

        assertThatThrownBy(() -> service.deleteUserById(88L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("请先更换签约默认接收人配置");
        assertThatThrownBy(() -> service.deleteUserByIds(new Long[] { 77L, 88L }))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("请先更换签约默认接收人配置");

        verify(userRoleMapper, never()).deleteUserRoleByUserId(88L);
        verify(userMapper, never()).deleteUserByIds(org.mockito.ArgumentMatchers.any());
    }

    private SysUser user(Long userId, String status)
    {
        SysUser user = new SysUser();
        user.setUserId(userId);
        user.setStatus(status);
        user.setDelFlag("0");
        return user;
    }
}
