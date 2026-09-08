package com.erp.oa.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.SignScopeHeaderUtils;
import com.erp.common.security.annotation.RequiresLogin;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.oa.domain.dto.OaSignOnboardDataRequestSendRequest;
import com.erp.oa.domain.dto.OaSignOnboardDataReviewRequest;
import com.erp.oa.domain.dto.OaSignOnboardDataSubmitRequest;
import com.erp.oa.domain.dto.OaSignOnboardGenerateRequest;
import com.erp.oa.domain.dto.OaSignOnboardImportRowUpdateRequest;
import com.erp.oa.service.impl.OaSignOnboardDataRequestService;
import com.erp.oa.service.impl.OaSignOnboardGenerationService;
import com.erp.oa.service.impl.OaSignOnboardImportService;

@DisplayName("入职签约 Excel 入口控制器")
class OaSignOnboardImportControllerTest
{
    @Test
    @DisplayName("入职导入默认开启且仍保留显式配置与权限边界")
    void shouldDefaultToEnabled() throws Exception
    {
        ConditionalOnProperty condition = OaSignOnboardImportController.class
                .getAnnotation(ConditionalOnProperty.class);

        assertThat(condition).isNotNull();
        assertThat(condition.prefix()).isEqualTo("oa.sign.excel-import");
        assertThat(condition.name()).containsExactly("enabled");
        assertThat(condition.havingValue()).isEqualTo("true");
        assertThat(condition.matchIfMissing()).isTrue();

        try (InputStream input = getClass().getClassLoader().getResourceAsStream("bootstrap.yml"))
        {
            assertThat(input).isNotNull();
            String yaml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(yaml).contains("enabled: ${OA_SIGN_EXCEL_IMPORT_ENABLED:true}");
        }
    }

    @Test
    @DisplayName("HR 操作要求发送权限而员工操作只要求登录")
    void shouldSeparateHrAndEmployeePermissions() throws Exception
    {
        assertSendPermission("preview", org.springframework.web.multipart.MultipartFile.class,
                String.class, String.class, jakarta.servlet.http.HttpServletRequest.class);
        assertSendPermission("batch", Long.class, jakarta.servlet.http.HttpServletRequest.class);
        assertSendPermission("updateRow", Long.class, Long.class,
                OaSignOnboardImportRowUpdateRequest.class,
                jakarta.servlet.http.HttpServletRequest.class);
        assertSendPermission("sendDataRequest", Long.class,
                OaSignOnboardDataRequestSendRequest.class,
                jakarta.servlet.http.HttpServletRequest.class);
        assertSendPermission("review", Long.class, OaSignOnboardDataReviewRequest.class,
                jakarta.servlet.http.HttpServletRequest.class);
        assertSendPermission("generate", Long.class, OaSignOnboardGenerateRequest.class,
                jakarta.servlet.http.HttpServletRequest.class);

        assertLoginOnly("mine");
        assertLoginOnly("dataRequest", Long.class, jakarta.servlet.http.HttpServletRequest.class);
        assertLoginOnly("submit", Long.class, OaSignOnboardDataSubmitRequest.class);
    }

    @Test
    @DisplayName("所有带组织边界的入口都向服务传递独立 Sign-Scope")
    void shouldPassDedicatedSignScopeToServices()
    {
        OaSignOnboardImportService importService = mock(OaSignOnboardImportService.class);
        OaSignOnboardDataRequestService dataRequestService =
                mock(OaSignOnboardDataRequestService.class);
        OaSignOnboardGenerationService generationService =
                mock(OaSignOnboardGenerationService.class);
        OaSignOnboardImportController controller = new OaSignOnboardImportController(
                importService, dataRequestService, generationService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(SignScopeHeaderUtils.SIGN_SCOPE_HEADER, "1171");
        MockMultipartFile file = new MockMultipartFile(
                "file", "sign.xlsx", "application/octet-stream", new byte[] { 1 });
        OaSignOnboardImportRowUpdateRequest rowAction =
                new OaSignOnboardImportRowUpdateRequest();
        OaSignOnboardDataRequestSendRequest sendAction =
                new OaSignOnboardDataRequestSendRequest();
        OaSignOnboardDataReviewRequest reviewAction = new OaSignOnboardDataReviewRequest();
        OaSignOnboardGenerateRequest generateAction = new OaSignOnboardGenerateRequest();
        when(importService.parseEmployeeIds("201,202")).thenReturn(List.of(201L, 202L));

        controller.preview(file, "201,202",
                OaSignOnboardImportService.MATCH_MODE_MANUAL_SELECTED, request);
        controller.batch(31L, request);
        controller.updateRow(31L, 71L, rowAction, request);
        controller.sendDataRequest(31L, sendAction, request);
        controller.review(501L, reviewAction, request);
        controller.generate(31L, generateAction, request);
        controller.dataRequest(501L, request);

        verify(importService).preview(same(file), eq(List.of(201L, 202L)),
                eq(OaSignOnboardImportService.MATCH_MODE_MANUAL_SELECTED), eq(1171L));
        verify(importService).detail(31L, 1171L);
        verify(importService).updateRow(31L, 71L, rowAction, 1171L);
        verify(importService).sendDataRequests(31L, sendAction, 1171L);
        verify(dataRequestService).review(501L, reviewAction, 1171L);
        verify(generationService).generate(31L, generateAction, 1171L);
        verify(dataRequestService).detail(501L, 1171L);
    }

    @Test
    @DisplayName("显式 Sign-Scope 非法时失败关闭且不调用业务服务")
    void shouldFailClosedForInvalidExplicitSignScope()
    {
        OaSignOnboardImportService importService = mock(OaSignOnboardImportService.class);
        OaSignOnboardDataRequestService dataRequestService =
                mock(OaSignOnboardDataRequestService.class);
        OaSignOnboardGenerationService generationService =
                mock(OaSignOnboardGenerationService.class);
        OaSignOnboardImportController controller = new OaSignOnboardImportController(
                importService, dataRequestService, generationService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(SignScopeHeaderUtils.SIGN_SCOPE_HEADER, "not-a-dept");

        assertThatThrownBy(() -> controller.batch(31L, request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("签约组织编号不正确");
        verifyNoInteractions(importService, dataRequestService, generationService);
    }

    private void assertSendPermission(String methodName, Class<?>... parameterTypes)
            throws Exception
    {
        Method method = OaSignOnboardImportController.class.getMethod(methodName, parameterTypes);
        RequiresPermissions permission = method.getAnnotation(RequiresPermissions.class);
        assertThat(permission).isNotNull();
        assertThat(permission.value()).containsExactly("oa:signTask:send");
        assertThat(method.getAnnotation(RequiresLogin.class)).isNull();
    }

    private void assertLoginOnly(String methodName, Class<?>... parameterTypes) throws Exception
    {
        Method method = OaSignOnboardImportController.class.getMethod(methodName, parameterTypes);
        assertThat(method.getAnnotation(RequiresLogin.class)).isNotNull();
        assertThat(method.getAnnotation(RequiresPermissions.class)).isNull();
    }
}
