package com.erp.oa.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.log.annotation.Log;

@DisplayName("OA 敏感操作日志策略")
class OaSensitiveAuditLogPolicyTest
{
    @Test
    @DisplayName("签约与劳动合同控制器显式禁止保存请求和响应正文")
    void signingControllersShouldBeMetadataOnly()
    {
        assertMetadataOnly(OaSignPackageController.class);
        assertMetadataOnly(OaLaborContractController.class);
    }

    private static void assertMetadataOnly(Class<?> controller)
    {
        Arrays.stream(controller.getDeclaredMethods())
                .filter(method -> method.getAnnotation(Log.class) != null)
                .forEach(OaSensitiveAuditLogPolicyTest::assertMetadataOnly);
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
