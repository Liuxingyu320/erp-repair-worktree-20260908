package com.erp.system.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.erp.system.api.domain.SysUser;
import com.erp.system.domain.SysUserRole;
import com.erp.common.security.annotation.RequiresLogin;
import com.erp.common.security.annotation.RequiresPermissions;

@DisplayName("system 控制器声明式登录边界")
class SysControllerAuthBoundaryTest
{
    @Test
    @DisplayName("登录后辅助读取接口至少要求登录")
    void helperReadEndpointsShouldRequireLogin() throws Exception
    {
        assertRequiresLogin(SysDeptController.class.getMethod("shopTree"));
        assertRequiresLogin(SysDeptController.class.getMethod("warehouseList", String.class, Long.class));
        assertRequiresLogin(SysDictDataController.class.getMethod("dictType", String.class));
        assertRequiresLogin(SysDictTypeController.class.getMethod("optionselect"));
        assertRequiresLogin(SysMenuController.class.getMethod("getRouters"));
        assertRequiresLogin(SysPostController.class.getMethod("optionselect"));
        assertRequiresLogin(SysUserController.class.getMethod("getInfo"));
    }

    @Test
    @DisplayName("配置详情、按键读取和导入模板要求最小管理权限")
    void managementHelpersShouldRequireExplicitPermission() throws Exception
    {
        assertRequiresPermission(SysConfigController.class.getMethod("getInfo", Long.class),
                "system:config:query");
        assertRequiresPermission(SysConfigController.class.getMethod("getConfigKey", String.class),
                "system:config:query");
        assertRequiresPermission(SysUserController.class.getMethod("importTemplate", HttpServletResponse.class),
                "system:user:import");
        assertRequiresPermission(SysConfigController.class.getMethod("refreshCache"),
                "system:config:refresh");
    }

    private static void assertRequiresLogin(Method method)
    {
        assertThat(method.getAnnotation(RequiresLogin.class))
                .as(method.getDeclaringClass().getSimpleName() + "." + method.getName() + " should require login")
                .isNotNull();
    }

    private static void assertRequiresPermission(Method method, String permission)
    {
        RequiresPermissions annotation = method.getAnnotation(RequiresPermissions.class);
        assertThat(annotation)
                .as(method.getDeclaringClass().getSimpleName() + "." + method.getName()
                        + " should require " + permission)
                .isNotNull();
        assertThat(annotation.value()).containsExactly(permission);
    }
}
