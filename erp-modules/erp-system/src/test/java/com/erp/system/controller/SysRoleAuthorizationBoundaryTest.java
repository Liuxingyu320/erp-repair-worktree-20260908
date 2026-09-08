package com.erp.system.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.system.domain.SysUserRole;
import com.erp.system.service.ISysRoleService;
import com.erp.system.service.ISysUserService;

@DisplayName("角色分配用户数据范围边界")
class SysRoleAuthorizationBoundaryTest
{
    @Test
    @DisplayName("取消单个授权前同时检查角色和用户数据范围")
    void cancelShouldCheckRoleAndUserScopeBeforeMutation()
    {
        ISysRoleService roleService = mock(ISysRoleService.class);
        ISysUserService userService = mock(ISysUserService.class);
        SysRoleController controller = controller(roleService, userService);
        SysUserRole relation = new SysUserRole();
        relation.setRoleId(7L);
        relation.setUserId(21L);
        when(roleService.deleteAuthUser(relation)).thenReturn(1);

        AjaxResult result = controller.cancelAuthUser(relation);

        assertThat(result.get(AjaxResult.CODE_TAG)).isEqualTo(200);
        InOrder order = inOrder(roleService, userService);
        order.verify(roleService).checkRoleDataScope(7L);
        order.verify(userService).checkUserDataScope(21L);
        order.verify(roleService).deleteAuthUser(relation);
    }

    @Test
    @DisplayName("批量授权前逐个检查目标用户数据范围")
    void selectAllShouldCheckEveryUserScopeBeforeMutation()
    {
        ISysRoleService roleService = mock(ISysRoleService.class);
        ISysUserService userService = mock(ISysUserService.class);
        SysRoleController controller = controller(roleService, userService);
        Long[] userIds = { 21L, null, 22L };
        when(roleService.insertAuthUsers(7L, userIds)).thenReturn(2);

        controller.selectAuthUserAll(7L, userIds);

        verify(roleService).checkRoleDataScope(7L);
        verify(userService).checkUserDataScope(21L);
        verify(userService).checkUserDataScope(22L);
        verify(roleService).insertAuthUsers(7L, userIds);
    }

    private static SysRoleController controller(ISysRoleService roleService, ISysUserService userService)
    {
        SysRoleController controller = new SysRoleController();
        ReflectionTestUtils.setField(controller, "roleService", roleService);
        ReflectionTestUtils.setField(controller, "userService", userService);
        return controller;
    }
}
