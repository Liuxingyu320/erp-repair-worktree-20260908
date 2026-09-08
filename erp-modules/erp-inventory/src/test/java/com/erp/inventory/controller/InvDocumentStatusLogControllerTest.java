package com.erp.inventory.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.inventory.domain.InvDocumentStatusLog;

@DisplayName("库存单据状态审计日志 Controller")
class InvDocumentStatusLogControllerTest
{
    @Test
    @DisplayName("列表接口使用只读权限并挂在审计路径")
    void listShouldDeclareReadOnlyAuditPermission() throws Exception
    {
        RequestMapping classMapping = InvDocumentStatusLogController.class.getAnnotation(RequestMapping.class);
        Method method = InvDocumentStatusLogController.class.getMethod("list",
                InvDocumentStatusLog.class, HttpServletRequest.class);

        RequiresPermissions permissions = method.getAnnotation(RequiresPermissions.class);
        GetMapping getMapping = method.getAnnotation(GetMapping.class);

        assertThat(classMapping.value()).containsExactly("/audit");
        assertThat(permissions.value()).containsExactly("inv:audit:list");
        assertThat(getMapping.value()).containsExactly("/list");
    }
}
