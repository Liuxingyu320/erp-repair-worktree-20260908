package com.erp.oa.attendance.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import com.erp.common.log.annotation.Log;
import com.erp.common.security.annotation.IdempotentSubmit;
import com.erp.common.security.annotation.RequiresPermissions;

class AttendanceRemainingWorkControllerContractTest
{
    @Test
    void managerOnlyControllerUsesIndependentAppendOnlyRoute()
    {
        RequestMapping mapping = AttendanceRemainingWorkController.class
                .getAnnotation(RequestMapping.class);
        RequiresPermissions permission = AttendanceRemainingWorkController.class
                .getAnnotation(RequiresPermissions.class);

        assertThat(mapping.value())
                .containsExactly("/attendance-v2/remaining-work");
        assertThat(permission.value())
                .containsExactly("oa:attendance:remaining-work:manage");
    }

    @Test
    void writesAreAuditedAndConfirmationIsIdempotent()
    {
        for (Method method : AttendanceRemainingWorkController.class
                .getDeclaredMethods())
        {
            if (!method.isAnnotationPresent(PostMapping.class)) continue;
            Log audit = method.getAnnotation(Log.class);
            assertThat(audit).as(method.getName()).isNotNull();
            assertThat(audit.isSaveRequestData()).isFalse();
            assertThat(audit.isSaveResponseData()).isFalse();
        }
        Method confirm = java.util.Arrays.stream(
                AttendanceRemainingWorkController.class.getDeclaredMethods())
                .filter(method -> "confirm".equals(method.getName()))
                .findFirst().orElseThrow();
        assertThat(confirm.getAnnotation(IdempotentSubmit.class)).isNotNull();
    }
}
