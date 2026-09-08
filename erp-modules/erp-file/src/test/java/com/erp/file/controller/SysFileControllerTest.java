package com.erp.file.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import jakarta.servlet.http.HttpServletResponse;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.common.security.annotation.InnerAuth;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import com.erp.file.service.ISysFileService;

public class SysFileControllerTest
{
    @Test
    public void testUploadRequiresFileUploadPermission() throws Exception
    {
        RequiresPermissions permissions = SysFileController.class.getMethod("upload",
                MultipartFile.class, HttpServletResponse.class).getAnnotation(RequiresPermissions.class);

        assertPermission(permissions, "file:upload");
    }

    @Test
    public void testDeleteRequiresFileDeletePermission() throws Exception
    {
        RequiresPermissions permissions = SysFileController.class.getMethod("delete", String.class,
                HttpServletResponse.class)
                .getAnnotation(RequiresPermissions.class);

        assertPermission(permissions, "file:delete");
    }

    @Test
    public void testInnerUploadRequiresInternalServiceAuthentication() throws Exception
    {
        InnerAuth innerAuth = SysFileController.class.getMethod("uploadInner",
                org.springframework.web.multipart.MultipartFile.class).getAnnotation(InnerAuth.class);
        if (innerAuth == null)
        {
            throw new AssertionError("expected @InnerAuth on internal upload endpoint");
        }
        if (!innerAuth.isUser())
        {
            throw new AssertionError("internal upload must carry the authenticated business user");
        }
    }

    private static void assertPermission(RequiresPermissions permissions, String expected)
    {
        if (permissions == null)
        {
            throw new AssertionError("expected @RequiresPermissions(\"" + expected + "\")");
        }
        String[] actual = permissions.value();
        if (actual.length != 1 || !expected.equals(actual[0]))
        {
            throw new AssertionError("expected permission " + expected);
        }
    }

    private static void assertNoStore(MockHttpServletResponse response)
    {
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store, max-age=0");
        assertThat(response.getHeader("Pragma")).isEqualTo("no-cache");
    }
}
