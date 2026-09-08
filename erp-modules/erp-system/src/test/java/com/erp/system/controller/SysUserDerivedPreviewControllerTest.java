package com.erp.system.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Proxy;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.security.annotation.Logical;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.domain.SysUserProfile;
import com.erp.system.service.ISysDeptService;
import com.erp.system.service.ISysUserService;

@DisplayName("用户档案派生预览接口")
class SysUserDerivedPreviewControllerTest
{
    @Test
    @DisplayName("新增或编辑权限均可调用派生预览")
    void previewShouldAllowAddOrEditPermission() throws Exception
    {
        Method method = SysUserController.class.getMethod("previewDerivedProfile", SysUser.class);
        RequiresPermissions permissions = method.getAnnotation(RequiresPermissions.class);

        assertThat(permissions).isNotNull();
        assertThat(permissions.value()).containsExactlyInAnyOrder("system:user:add", "system:user:edit");
        assertThat(permissions.logical()).isEqualTo(Logical.OR);
    }

    @Test
    @DisplayName("预览前先校验归属部门数据权限")
    void previewShouldCheckDepartmentScopeBeforeDeriving()
    {
        List<String> events = new ArrayList<>();
        SysUserController controller = controller(events, false);
        SysUser request = new SysUser();
        request.setDeptId(202L);

        AjaxResult result = controller.previewDerivedProfile(request);

        assertThat(events).containsExactly("checkDeptDataScope:202", "previewDerivedProfile:202");
        assertThat(result.get(AjaxResult.DATA_TAG)).isInstanceOf(SysUserProfile.class);
    }

    @Test
    @DisplayName("归属部门越权时不执行派生预览")
    void deniedDepartmentShouldStopBeforeDeriving()
    {
        List<String> events = new ArrayList<>();
        SysUserController controller = controller(events, true);
        SysUser request = new SysUser();
        request.setDeptId(303L);

        assertThatThrownBy(() -> controller.previewDerivedProfile(request))
                .isInstanceOf(ServiceException.class)
                .hasMessage("没有部门数据权限");
        assertThat(events).containsExactly("checkDeptDataScope:303");
    }

    private static SysUserController controller(List<String> events, boolean denyDepartment)
    {
        SysUserController controller = new SysUserController();
        ReflectionTestUtils.setField(controller, "deptService", deptService(events, denyDepartment));
        ReflectionTestUtils.setField(controller, "userService", userService(events));
        return controller;
    }

    private static ISysDeptService deptService(List<String> events, boolean denyDepartment)
    {
        return mapper(ISysDeptService.class, (method, args) -> {
            if ("checkDeptDataScope".equals(method))
            {
                events.add("checkDeptDataScope:" + args[0]);
                if (denyDepartment)
                {
                    throw new ServiceException("没有部门数据权限");
                }
                return null;
            }
            throw unexpected(method);
        });
    }

    private static ISysUserService userService(List<String> events)
    {
        return mapper(ISysUserService.class, (method, args) -> {
            if ("previewDerivedProfile".equals(method))
            {
                SysUser request = (SysUser) args[0];
                events.add("previewDerivedProfile:" + request.getDeptId());
                return new SysUserProfile();
            }
            throw unexpected(method);
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T mapper(Class<T> type, BiFunction<String, Object[], Object> handler)
    {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class)
            {
                return method.invoke(new Object(), args);
            }
            return handler.apply(method.getName(), args == null ? new Object[0] : args);
        });
    }

    private static AssertionError unexpected(String method)
    {
        return new AssertionError("Unexpected call: " + method);
    }
}
