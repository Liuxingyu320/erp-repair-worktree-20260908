package com.erp.inventory.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import com.erp.common.security.annotation.Logical;
import com.erp.common.security.annotation.RequiresLogin;
import com.erp.common.security.annotation.RequiresPermissions;

@DisplayName("inventory 控制器声明式登录边界")
class InvControllerAuthBoundaryTest
{
    @Test
    @DisplayName("商品导入模板下载要求登录")
    void productImportTemplateShouldRequireLogin() throws Exception
    {
        assertRequiresLogin(InvProductController.class.getMethod("importTemplate", HttpServletResponse.class));
    }

    @Test
    @DisplayName("商品页分类树支持分类管理权限或商品分类树只读权限")
    void productCategoryTreeShouldAllowManagementOrReadonlyTreePermission() throws Exception
    {
        Method method = InvProductCategoryController.class.getMethod("tree", HttpServletRequest.class,
                jakarta.servlet.http.HttpServletResponse.class);

        RequiresPermissions permissions = method.getAnnotation(RequiresPermissions.class);

        assertThat(permissions).isNotNull();
        assertThat(permissions.value()).containsExactly("inv:category:list", "inv:category:tree");
        assertThat(permissions.logical()).isEqualTo(Logical.OR);
    }

    private static void assertRequiresLogin(Method method)
    {
        assertThat(method.getAnnotation(RequiresLogin.class))
                .as(method.getDeclaringClass().getSimpleName() + "." + method.getName() + " should require login")
                .isNotNull();
    }
}
