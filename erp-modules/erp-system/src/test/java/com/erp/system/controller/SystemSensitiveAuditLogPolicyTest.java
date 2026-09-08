package com.erp.system.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.log.annotation.Log;

@DisplayName("系统管理敏感操作日志策略")
class SystemSensitiveAuditLogPolicyTest
{
    private static final Class<?>[] SENSITIVE_CONTROLLERS = {
            SysUserController.class,
            SysProfileController.class,
            SysConfigController.class,
            SysSalaryConfigController.class,
            SysNoticeController.class
    };

    @Test
    @DisplayName("敏感控制器显式禁止保存请求和响应正文")
    void sensitiveControllersShouldBeMetadataOnly()
    {
        for (Class<?> controller : SENSITIVE_CONTROLLERS)
        {
            Arrays.stream(controller.getDeclaredMethods())
                    .filter(method -> method.getAnnotation(Log.class) != null)
                    .forEach(SystemSensitiveAuditLogPolicyTest::assertMetadataOnly);
        }
    }

    private static void assertMetadataOnly(Method method)
    {
        Log annotation = method.getAnnotation(Log.class);
        String description = method.getDeclaringClass().getSimpleName() + "." + method.getName();
        assertThat(annotation.isSaveRequestData())
                .as(description + " must not persist request payload")
                .isFalse();
        assertThat(annotation.isSaveResponseData())
                .as(description + " must not persist response payload")
                .isFalse();
    }
}
