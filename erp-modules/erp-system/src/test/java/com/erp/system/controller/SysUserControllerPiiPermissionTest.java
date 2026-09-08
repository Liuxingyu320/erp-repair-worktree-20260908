package com.erp.system.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import com.erp.common.log.annotation.Log;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.system.api.domain.SysUser;
import com.erp.system.domain.dto.SysUserManageUpdateRequest;
import com.erp.system.domain.dto.SysUserPiiAccessReason;
import com.erp.system.domain.dto.SysUserPiiUpdateRequest;

class SysUserControllerPiiPermissionTest
{
    @Test
    void piiReadUpdateAndExportUseDedicatedCombinedPermissionsAndSafeLogs() throws Exception
    {
        assertPermissions(SysUserController.class.getMethod("getPii", Long.class,
                SysUserPiiAccessReason.class), "system:user:query", "system:user:pii:read");
        Method update = SysUserController.class.getMethod("updatePii", Long.class,
                SysUserPiiAccessReason.class, SysUserPiiUpdateRequest.class);
        assertPermissions(update, "system:user:edit", "system:user:pii:edit");
        Method export = SysUserController.class.getMethod("exportSensitive",
                HttpServletResponse.class, SysUser.class, SysUserPiiAccessReason.class,
                Boolean.class, HttpServletRequest.class);
        assertPermissions(export, "system:user:export", "system:user:pii:export");
        assertMetadataOnlyLog(update);
        assertMetadataOnlyLog(export);
    }

    @Test
    void accountCreateAndEditAcceptOnlyFixedBaseDto() throws Exception
    {
        assertThat(SysUserController.class.getMethod("add", SysUserManageUpdateRequest.class,
                HttpServletResponse.class)).isNotNull();
        assertThat(SysUserController.class.getMethod("edit", SysUserManageUpdateRequest.class)).isNotNull();
    }

    private void assertPermissions(Method method, String... expected)
    {
        RequiresPermissions annotation = method.getAnnotation(RequiresPermissions.class);
        assertThat(annotation).isNotNull();
        Set<String> actual = Arrays.stream(annotation.value()).collect(Collectors.toSet());
        assertThat(actual).containsExactlyInAnyOrder(expected);
    }

    private void assertMetadataOnlyLog(Method method)
    {
        Log log = method.getAnnotation(Log.class);
        assertThat(log).isNotNull();
        assertThat(log.isSaveRequestData()).isFalse();
        assertThat(log.isSaveResponseData()).isFalse();
    }
}
