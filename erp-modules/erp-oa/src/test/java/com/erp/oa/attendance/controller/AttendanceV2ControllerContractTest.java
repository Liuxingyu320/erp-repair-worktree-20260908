package com.erp.oa.attendance.controller;

import static org.assertj.core.api.Assertions.assertThat;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import com.erp.common.log.annotation.Log;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.oa.attendance.dto.AttendanceRequests.PunchStatusRequest;
import com.erp.oa.attendance.dto.AttendanceViews.PunchStatusResult;
import com.erp.oa.attendance.dto.AttendanceViews.TodayContext;

class AttendanceV2ControllerContractTest
{
    private static final Set<String> SEEDED = Set.of(
            "oa:attendance:shift:list", "oa:attendance:shift:query",
            "oa:attendance:shift:add", "oa:attendance:shift:edit",
            "oa:attendance:shift:remove",
            "oa:attendance:site:list", "oa:attendance:site:query",
            "oa:attendance:site:add", "oa:attendance:site:edit",
            "oa:attendance:site:remove",
            "oa:attendance:schedule:list", "oa:attendance:schedule:add",
            "oa:attendance:schedule:edit", "oa:attendance:schedule:remove",
            "oa:attendance:schedule:publish",
            "oa:attendance:punch:self", "oa:attendance:record:self",
            "oa:attendance:record:evidence", "oa:attendance:day:list",
            "oa:attendance:day:settle");

    @Test
    void allEndpointsUseStableBaseAndSeededPermissions()
    {
        RequestMapping mapping = AttendanceV2Controller.class
                .getAnnotation(RequestMapping.class);
        assertThat(mapping.value()).containsExactly("/attendance-v2");
        for (Method method : AttendanceV2Controller.class
                .getDeclaredMethods())
        {
            RequiresPermissions permissions = method.getAnnotation(
                    RequiresPermissions.class);
            assertThat(permissions)
                    .as(method.getName() + " must have an explicit permission")
                    .isNotNull();
            assertThat(Arrays.asList(permissions.value()))
                    .allMatch(SEEDED::contains);
            boolean write = method.isAnnotationPresent(PostMapping.class)
                    || method.isAnnotationPresent(PutMapping.class)
                    || method.isAnnotationPresent(DeleteMapping.class);
            if (write)
            {
                Log log = method.getAnnotation(Log.class);
                assertThat(log).as(method.getName()
                        + " must create metadata audit").isNotNull();
                assertThat(log.isSaveRequestData()).isFalse();
                assertThat(log.isSaveResponseData()).isFalse();
            }
        }
    }

    @Test
    void todayContextExposesOnlyThePrivateEvidenceIdentifier()
            throws Exception
    {
        assertThat(TodayContext.class.getField("latestEvidenceId").getType())
                .isEqualTo(Long.class);
        assertThat(Arrays.stream(TodayContext.class.getFields())
                .map(field -> field.getName().toLowerCase()))
                .noneMatch(name -> name.contains("path")
                        || name.contains("sha") || name.contains("hash"));
    }

    @Test
    void punchStatusUsesSensitivePostContractAndPrivateEvidenceId()
            throws Exception
    {
        Method method = AttendanceV2Controller.class.getDeclaredMethod(
                "punchStatus", PunchStatusRequest.class,
                HttpServletRequest.class);
        assertThat(method.getAnnotation(PostMapping.class).value())
                .containsExactly("/punch/status");
        assertThat(method.getAnnotation(RequiresPermissions.class).value())
                .containsExactly("oa:attendance:punch:self");
        Log log = method.getAnnotation(Log.class);
        assertThat(log.isSaveRequestData()).isFalse();
        assertThat(log.isSaveResponseData()).isFalse();
        assertThat(Arrays.stream(PunchStatusRequest.class.getFields())
                .map(field -> field.getName()))
                .containsExactlyInAnyOrder("clientRequestId",
                        "challengeToken");
        assertThat(Arrays.stream(PunchStatusResult.class.getFields())
                .map(field -> field.getName()))
                .containsExactlyInAnyOrder("status", "clientRequestId",
                        "event", "evidenceId");
    }
}
