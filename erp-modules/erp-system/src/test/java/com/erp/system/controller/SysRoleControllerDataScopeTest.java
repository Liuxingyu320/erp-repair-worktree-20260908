package com.erp.system.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Proxy;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.validation.annotation.Validated;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.system.api.domain.SysRole;
import com.erp.system.service.ISysDeptService;
import com.erp.system.service.ISysRoleService;

@DisplayName("角色控制器数据范围")
class SysRoleControllerDataScopeTest
{
    @Test
    @DisplayName("数据范围修改使用独立权限并启用参数校验")
    void dataScopeMutationShouldUseDedicatedPermissionAndValidation() throws Exception
    {
        Method method = SysRoleController.class.getMethod("dataScope", SysRole.class);
        RequiresPermissions permissions = method.getAnnotation(RequiresPermissions.class);

        assertThat(permissions).isNotNull();
        assertThat(permissions.value()).containsExactly("system:role:dataScope");
        assertThat(method.getParameters()[0].getAnnotation(Validated.class)).isNotNull();
    }

    @Test
    @DisplayName("部门树读取前校验角色数据范围")
    void deptTreeShouldCheckRoleDataScopeBeforeLoadingDeptKeys()
    {
        List<String> events = new ArrayList<>();
        SysRoleController controller = new SysRoleController();
        ReflectionTestUtils.setField(controller, "roleService", roleService(events));
        ReflectionTestUtils.setField(controller, "deptService", deptService(events));

        assertThatThrownBy(() -> controller.deptTree(20L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("没有权限访问角色数据");

        assertThat(events).containsExactly("role:20");
    }

    @Test
    @DisplayName("角色新增修改必须同时具备基础写权限和数据范围权限")
    void roleWritesShouldRequireDataScopePermission() throws Exception
    {
        RequiresPermissions add = SysRoleController.class.getMethod("add", SysRole.class)
                .getAnnotation(RequiresPermissions.class);
        RequiresPermissions edit = SysRoleController.class.getMethod("edit", SysRole.class)
                .getAnnotation(RequiresPermissions.class);

        assertThat(add.value()).containsExactly("system:role:add", "system:role:dataScope");
        assertThat(edit.value()).containsExactly("system:role:edit", "system:role:dataScope");
    }

    private static ISysRoleService roleService(List<String> events)
    {
        return (ISysRoleService) Proxy.newProxyInstance(ISysRoleService.class.getClassLoader(),
                new Class<?>[] { ISysRoleService.class }, (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class)
                    {
                        return method.invoke(new Object(), args);
                    }
                    if ("checkRoleDataScope".equals(method.getName()))
                    {
                        Long[] roleIds = (Long[]) args[0];
                        events.add("role:" + roleIds[0]);
                        throw new ServiceException("没有权限访问角色数据！");
                    }
                    throw new AssertionError("Unexpected role service call: " + method.getName());
                });
    }

    private static ISysDeptService deptService(List<String> events)
    {
        return (ISysDeptService) Proxy.newProxyInstance(ISysDeptService.class.getClassLoader(),
                new Class<?>[] { ISysDeptService.class }, (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class)
                    {
                        return method.invoke(new Object(), args);
                    }
                    if ("selectDeptListByRoleId".equals(method.getName())
                            || "selectDeptTreeList".equals(method.getName()))
                    {
                        events.add("dept:" + method.getName());
                        return Collections.emptyList();
                    }
                    throw new AssertionError("Unexpected dept service call: " + method.getName());
                });
    }
}
