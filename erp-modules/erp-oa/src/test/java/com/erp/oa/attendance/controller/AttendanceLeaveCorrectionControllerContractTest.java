package com.erp.oa.attendance.controller;

import static org.assertj.core.api.Assertions.assertThat;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import com.erp.common.log.annotation.Log;
import com.erp.common.security.annotation.IdempotentSubmit;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.oa.attendance.correction.AttendanceCorrectionController;
import com.erp.oa.attendance.leave.AttendanceLeaveController;

class AttendanceLeaveCorrectionControllerContractTest
{
    private static final Set<String> SEEDED = Set.of(
            "oa:attendance:leave:type:list",
            "oa:attendance:leave:type:add",
            "oa:attendance:leave:type:edit",
            "oa:attendance:leave:type:remove",
            "oa:attendance:leave:self", "oa:attendance:leave:list",
            "oa:attendance:leave:approve",
            "oa:attendance:correction:self",
            "oa:attendance:correction:list",
            "oa:attendance:correction:approve");

    @Test
    void controllersUseStableBasesSeededPermissionsAndMetadataAudit()
    {
        assertController(AttendanceLeaveController.class,
                "/attendance-v2/leave");
        assertController(AttendanceCorrectionController.class,
                "/attendance-v2/corrections");
    }

    @Test
    void approvalSubmitsAreExplicitlyIdempotent() throws Exception
    {
        Method leave = AttendanceLeaveController.class.getDeclaredMethod(
                "submit", Long.class,
                com.erp.oa.attendance.leave.AttendanceLeaveRequests.Submit.class,
                jakarta.servlet.http.HttpServletRequest.class);
        Method correction = AttendanceCorrectionController.class
                .getDeclaredMethod("submit", Long.class,
                        com.erp.oa.attendance.correction.AttendanceCorrectionRequests.Submit.class,
                        jakarta.servlet.http.HttpServletRequest.class);
        assertThat(leave.getAnnotation(IdempotentSubmit.class)).isNotNull();
        assertThat(correction.getAnnotation(IdempotentSubmit.class))
                .isNotNull();
    }

    @Test
    void draftCreationKeepsTheLegacyAppThrottleAfterSuccess()
            throws Exception
    {
        Method leave = AttendanceLeaveController.class.getDeclaredMethod(
                "createDraft",
                com.erp.oa.attendance.leave.AttendanceLeaveRequests.SaveDraft.class,
                jakarta.servlet.http.HttpServletRequest.class);
        Method correction = AttendanceCorrectionController.class
                .getDeclaredMethod("createDraft",
                        com.erp.oa.attendance.correction.AttendanceCorrectionRequests.SaveDraft.class,
                        jakarta.servlet.http.HttpServletRequest.class);
        assertThat(leave.getAnnotation(IdempotentSubmit.class)
                .releaseOnSuccess()).isFalse();
        assertThat(correction.getAnnotation(IdempotentSubmit.class)
                .releaseOnSuccess()).isFalse();
    }

    @Test
    void correctionDetailAllowsTodoApprovers() throws Exception
    {
        Method detail = AttendanceCorrectionController.class
                .getDeclaredMethod("detail", Long.class,
                        jakarta.servlet.http.HttpServletRequest.class);
        RequiresPermissions permission = detail.getAnnotation(
                RequiresPermissions.class);
        assertThat(permission.value()).contains(
                "oa:attendance:correction:self",
                "oa:attendance:correction:list",
                "oa:attendance:correction:approve");
        assertThat(permission.logical())
                .isEqualTo(com.erp.common.security.annotation.Logical.OR);
    }

    @Test
    void leaveDetailAndAttachmentsAllowTodoApprovers() throws Exception
    {
        Method detail = AttendanceLeaveController.class.getDeclaredMethod(
                "detail", Long.class,
                jakarta.servlet.http.HttpServletRequest.class);
        assertTodoApproverRead(detail, "oa:attendance:leave:approve");

        Method attachment = AttendanceLeaveController.class.getDeclaredMethod(
                "attachment", Long.class, Long.class,
                jakarta.servlet.http.HttpServletRequest.class);
        assertTodoApproverRead(attachment, "oa:attendance:leave:approve");
    }

    private void assertTodoApproverRead(Method method, String permissionValue)
    {
        RequiresPermissions permission = method.getAnnotation(
                RequiresPermissions.class);
        assertThat(permission.value()).contains(permissionValue);
        assertThat(permission.logical())
                .isEqualTo(com.erp.common.security.annotation.Logical.OR);
    }

    private void assertController(Class<?> type, String base)
    {
        assertThat(type.getAnnotation(RequestMapping.class).value())
                .containsExactly(base);
        for (Method method : type.getDeclaredMethods())
        {
            RequiresPermissions permission = method.getAnnotation(
                    RequiresPermissions.class);
            assertThat(permission).as(method.getName()).isNotNull();
            assertThat(Arrays.asList(permission.value()))
                    .allMatch(SEEDED::contains);
            boolean write = method.isAnnotationPresent(PostMapping.class)
                    || method.isAnnotationPresent(PutMapping.class)
                    || method.isAnnotationPresent(DeleteMapping.class);
            if (write)
            {
                Log audit = method.getAnnotation(Log.class);
                assertThat(audit).as(method.getName()).isNotNull();
                assertThat(audit.isSaveRequestData()).isFalse();
                assertThat(audit.isSaveResponseData()).isFalse();
            }
        }
    }
}
