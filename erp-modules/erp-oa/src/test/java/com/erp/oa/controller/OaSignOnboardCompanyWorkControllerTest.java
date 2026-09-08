package com.erp.oa.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mock.web.MockHttpServletRequest;
import com.erp.common.core.utils.SignScopeHeaderUtils;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.oa.domain.dto.OaSignOnboardCompanyWorkRequest;
import com.erp.oa.service.impl.OaSignOnboardCompanyWorkService;

@DisplayName("入职签名优先公司工作区控制器")
class OaSignOnboardCompanyWorkControllerTest
{
    @Test
    void usesDedicatedBatchFinalizePermissionAndFeatureGate() throws Exception
    {
        ConditionalOnProperty condition = OaSignOnboardCompanyWorkController.class
                .getAnnotation(ConditionalOnProperty.class);
        assertThat(condition).isNotNull();
        assertThat(condition.prefix()).isEqualTo("oa.sign.excel-import");
        assertThat(condition.matchIfMissing()).isTrue();
        assertThat(permission("list", jakarta.servlet.http.HttpServletRequest.class))
                .isEqualTo("oa:signTask:list");
        assertThat(permission("preview", OaSignOnboardCompanyWorkRequest.class,
                jakarta.servlet.http.HttpServletRequest.class))
                .isEqualTo("oa:signTask:batchFinalize");
        assertThat(permission("options", Long.class))
                .isEqualTo("oa:signTask:batchFinalize");
        assertThat(permission("execute", OaSignOnboardCompanyWorkRequest.class,
                jakarta.servlet.http.HttpServletRequest.class))
                .isEqualTo("oa:signTask:batchFinalize");
    }

    @Test
    void passesDedicatedSignScopeToEveryOperation()
    {
        OaSignOnboardCompanyWorkService service = mock(OaSignOnboardCompanyWorkService.class);
        OaSignOnboardCompanyWorkController controller =
                new OaSignOnboardCompanyWorkController(service);
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addHeader(SignScopeHeaderUtils.SIGN_SCOPE_HEADER, "1171");
        OaSignOnboardCompanyWorkRequest action = new OaSignOnboardCompanyWorkRequest();

        controller.list(servletRequest);
        controller.options(4L);
        controller.preview(action, servletRequest);
        controller.execute(action, servletRequest);

        verify(service).list(1171L);
        verify(service).options(4L);
        verify(service).preview(action, 1171L);
        verify(service).execute(action, 1171L);
    }

    private String permission(String methodName, Class<?>... parameterTypes) throws Exception
    {
        Method method = OaSignOnboardCompanyWorkController.class
                .getMethod(methodName, parameterTypes);
        return method.getAnnotation(RequiresPermissions.class).value()[0];
    }
}
