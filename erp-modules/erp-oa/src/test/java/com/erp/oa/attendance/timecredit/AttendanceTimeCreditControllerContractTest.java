package com.erp.oa.attendance.timecredit;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import com.erp.common.log.annotation.Log;
import com.erp.common.security.annotation.IdempotentSubmit;
import com.erp.common.security.annotation.RequiresPermissions;

class AttendanceTimeCreditControllerContractTest
{
    @Test
    void controllerIsManagerOnlyAndUsesIndependentRoute()
    {
        RequestMapping mapping = AttendanceTimeCreditController.class
                .getAnnotation(RequestMapping.class);
        RequiresPermissions permission = AttendanceTimeCreditController.class
                .getAnnotation(RequiresPermissions.class);

        assertThat(mapping.value())
                .containsExactly("/attendance-v2/time-credits");
        assertThat(permission.value())
                .containsExactly("oa:attendance:time-credit:manage");
    }

    @Test
    void applyAndReverseAreAuditedAndIdempotent()
    {
        for (Method method : AttendanceTimeCreditController.class
                .getDeclaredMethods())
        {
            if (!method.isAnnotationPresent(PostMapping.class)) continue;
            assertThat(method.getAnnotation(Log.class))
                    .as(method.getName()).isNotNull();
            assertThat(method.getAnnotation(IdempotentSubmit.class))
                    .as(method.getName()).isNotNull();
        }
    }
}
