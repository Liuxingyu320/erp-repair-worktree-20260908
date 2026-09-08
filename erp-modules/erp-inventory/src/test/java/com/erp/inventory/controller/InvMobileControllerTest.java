package com.erp.inventory.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.web.page.TableDataInfo;
import com.erp.common.security.annotation.Logical;
import com.erp.common.security.annotation.RequiresLogin;
import com.erp.common.security.annotation.RequiresPermissions;

@DisplayName("手机端库存 Controller 权限边界")
class InvMobileControllerTest
{
    @Test
    @DisplayName("手机端动态选项入口声明登录和任一支持类型列表权限")
    void optionsShouldDeclareLoginAndAnySupportedListPermissionBoundary() throws Exception
    {
        Method method = InvMobileController.class.getMethod("options", String.class, String.class, Integer.class,
                HttpServletRequest.class);

        assertThat(method.getAnnotation(RequiresLogin.class)).isNotNull();
        RequiresPermissions permissions = method.getAnnotation(RequiresPermissions.class);
        assertThat(permissions).isNotNull();
        assertThat(permissions.value()).containsExactly("inv:product:list", "inv:customer:list", "inv:supplier:list",
                "inv:stock:list");
        assertThat(permissions.logical()).isEqualTo(Logical.OR);
    }

    @Test
    @DisplayName("手机端工作台 summary 只声明登录边界并由待办 provider 逐类型过滤")
    void workbenchSummaryShouldRequireLoginWithoutBroadStockPermission() throws Exception
    {
        Method method = InvMobileController.class.getMethod("workbenchSummary", HttpServletRequest.class);

        assertThat(method.getAnnotation(RequiresLogin.class)).isNotNull();
        assertThat(method.getAnnotation(RequiresPermissions.class)).isNull();
    }

    @Test
    @DisplayName("手机端调拨审批待办使用调拨审批权限")
    void transferApprovalTodosShouldRequireTransferApprovePermission() throws Exception
    {
        Method method = InvMobileController.class.getMethod("transferApprovalTodos", HttpServletRequest.class);

        RequiresPermissions permissions = method.getAnnotation(RequiresPermissions.class);

        assertThat(permissions).isNotNull();
        assertThat(permissions.value()).containsExactly("inv:transfer:approve");
        assertThat(method.getReturnType()).isEqualTo(TableDataInfo.class);
    }
}
