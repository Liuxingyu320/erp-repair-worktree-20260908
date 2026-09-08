package com.erp.file.drive.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.file.drive.config.DriveProperties;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.domain.vo.DriveContent;
import com.erp.file.drive.exception.DriveException;
import com.erp.file.drive.service.DriveActorResolver;
import com.erp.file.drive.service.DriveContentService;
import com.erp.file.drive.service.DriveFeatureGuard;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@DisplayName("云盘内容控制器契约")
class DriveContentControllerTest
{
    @Test
    @DisplayName("内容接口要求 drive:access 且节点 ID 必须为正数")
    void shouldRequirePermissionAndPositiveNodeId() throws Exception
    {
        Method method = DriveContentController.class.getMethod(
                "content", Long.class, String.class);
        assertThat(method.getAnnotation(RequiresPermissions.class).value())
                .containsExactly(DriveConstants.PERMISSION_ACCESS);
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        DriveContentController controller = controller(enabledProperties(),
                mock(DriveActorResolver.class), mock(DriveContentService.class));

        Set<?> violations = validator.forExecutables().validateParameters(
                controller, method, new Object[] {0L, "preview"});

        assertThat(violations).isNotEmpty();
    }

    @Test
    @DisplayName("功能关闭在身份解析和内容服务之前失败")
    void shouldCheckFeatureBeforeActorAndService()
    {
        DriveActorResolver resolver = mock(DriveActorResolver.class);
        DriveContentService service = mock(DriveContentService.class);
        DriveContentController controller = controller(new DriveProperties(), resolver, service);

        assertThatThrownBy(() -> controller.content(22L, "preview"))
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_DISABLED);
        verifyNoInteractions(resolver, service);
    }

    @Test
    @DisplayName("非精确模式在身份和内容服务之前被拒绝")
    void shouldRejectInvalidModeBeforeService()
    {
        DriveActorResolver resolver = mock(DriveActorResolver.class);
        DriveContentService service = mock(DriveContentService.class);
        DriveContentController controller = controller(enabledProperties(), resolver, service);

        assertThatThrownBy(() -> controller.content(22L, "Preview"))
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_PREVIEW_UNSUPPORTED);
        verifyNoInteractions(resolver, service);
    }

    @Test
    @DisplayName("PDF 预览使用内联 UTF-8 文件名和禁止缓存安全头")
    void shouldBuildSecureInlineResponse()
    {
        DriveActor actor = actor();
        DriveActorResolver resolver = mock(DriveActorResolver.class);
        DriveContentService service = mock(DriveContentService.class);
        DriveContentController controller = controller(enabledProperties(), resolver, service);
        DriveContent content = content("员工手册.pdf", "application/pdf", true);
        when(resolver.resolve()).thenReturn(actor);
        when(service.resolve(22L, "preview", actor)).thenReturn(content);

        ResponseEntity<org.springframework.core.io.Resource> response =
                controller.content(22L, "preview");

        assertThat(response.getHeaders().getContentDisposition().getType()).isEqualTo("inline");
        assertThat(response.getHeaders().getContentDisposition().getFilename())
                .isEqualTo("员工手册.pdf");
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getHeaders().getFirst("Pragma")).isEqualTo("no-cache");
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options"))
                .isEqualTo("nosniff");
        assertThat(response.getHeaders().getContentLength()).isEqualTo(12L);
        assertThat(response.getHeaders().getContentType().toString())
                .isEqualTo("application/pdf");
    }

    @Test
    @DisplayName("DOCX 下载使用附件响应")
    void shouldBuildAttachmentResponse()
    {
        DriveActor actor = actor();
        DriveActorResolver resolver = mock(DriveActorResolver.class);
        DriveContentService service = mock(DriveContentService.class);
        DriveContentController controller = controller(enabledProperties(), resolver, service);
        DriveContent content = content("员工手册.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", false);
        when(resolver.resolve()).thenReturn(actor);
        when(service.resolve(22L, "download", actor)).thenReturn(content);

        ResponseEntity<org.springframework.core.io.Resource> response =
                controller.content(22L, "download");

        assertThat(response.getHeaders().getContentDisposition().getType())
                .isEqualTo("attachment");
        assertThat(response.getHeaders().getContentDisposition().getFilename())
                .isEqualTo("员工手册.docx");
    }

    @Test
    @DisplayName("非法或空内容类型降级为二进制流")
    void shouldFallbackInvalidContentType()
    {
        DriveActor actor = actor();
        DriveActorResolver resolver = mock(DriveActorResolver.class);
        DriveContentService service = mock(DriveContentService.class);
        DriveContentController controller = controller(enabledProperties(), resolver, service);
        when(resolver.resolve()).thenReturn(actor);
        when(service.resolve(22L, "download", actor))
                .thenReturn(content("报告.bin", "not a/media type;bad", false));

        ResponseEntity<org.springframework.core.io.Resource> response =
                controller.content(22L, "download");

        assertThat(response.getHeaders().getContentType().toString())
                .isEqualTo("application/octet-stream");
    }

    @Test
    @DisplayName("异常处理器仅作用于云盘控制器包")
    void shouldScopeAdviceToDriveControllers()
    {
        RestControllerAdvice annotation =
                DriveExceptionHandler.class.getAnnotation(RestControllerAdvice.class);
        Order order = DriveExceptionHandler.class.getAnnotation(Order.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.basePackages())
                .containsExactly("com.erp.file.drive.controller");
        assertThat(order).isNotNull();
        assertThat(order.value()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }

    @Test
    @DisplayName("稳定业务错误映射为约定 HTTP 状态且响应不泄露内部字段")
    void shouldMapBusinessCodesToSafeErrors()
    {
        DriveExceptionHandler handler = new DriveExceptionHandler();
        Map<String, HttpStatus> mappings = Map.ofEntries(
                Map.entry(DriveErrorCodes.DRIVE_ACCESS_DENIED, HttpStatus.FORBIDDEN),
                Map.entry(DriveErrorCodes.DRIVE_SPACE_NOT_FOUND, HttpStatus.NOT_FOUND),
                Map.entry(DriveErrorCodes.DRIVE_NODE_NOT_FOUND, HttpStatus.NOT_FOUND),
                Map.entry(DriveErrorCodes.DRIVE_STORAGE_OBJECT_MISSING, HttpStatus.NOT_FOUND),
                Map.entry(DriveErrorCodes.DRIVE_NAME_CONFLICT, HttpStatus.CONFLICT),
                Map.entry(DriveErrorCodes.DRIVE_INVALID_MOVE, HttpStatus.CONFLICT),
                Map.entry(DriveErrorCodes.DRIVE_CONCURRENT_MODIFICATION, HttpStatus.CONFLICT),
                Map.entry(DriveErrorCodes.DRIVE_FILE_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE),
                Map.entry(DriveErrorCodes.DRIVE_FILE_TYPE_REJECTED,
                        HttpStatus.UNPROCESSABLE_ENTITY),
                Map.entry(DriveErrorCodes.DRIVE_PREVIEW_UNSUPPORTED,
                        HttpStatus.UNPROCESSABLE_ENTITY),
                Map.entry(DriveErrorCodes.DRIVE_QUOTA_EXCEEDED,
                        HttpStatus.INSUFFICIENT_STORAGE),
                Map.entry(DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE,
                        HttpStatus.SERVICE_UNAVAILABLE),
                Map.entry(DriveErrorCodes.DRIVE_DISABLED, HttpStatus.SERVICE_UNAVAILABLE));

        mappings.forEach((code, status) -> {
            ResponseEntity<AjaxResult> response = handler.handleDriveException(
                    new DriveException(code, "安全消息"));
            assertThat(response.getStatusCode()).isEqualTo(status);
            assertThat(response.getBody()).containsEntry("businessCode", code)
                    .containsEntry("msg", "安全消息")
                    .doesNotContainKeys("storageKey", "uploadPath", "sql", "exception");
        });
    }

    private static DriveContentController controller(DriveProperties properties,
            DriveActorResolver resolver, DriveContentService service)
    {
        return new DriveContentController(new DriveFeatureGuard(properties), resolver, service);
    }

    private static DriveProperties enabledProperties()
    {
        DriveProperties properties = new DriveProperties();
        properties.setEnabled(true);
        return properties;
    }

    private static DriveActor actor()
    {
        return new DriveActor(20L, 8L, "财务部", "alice",
                Set.of(DriveConstants.PERMISSION_ACCESS), false);
    }

    private static DriveContent content(String name, String contentType, boolean inline)
    {
        byte[] bytes = "pdf-content!".getBytes(StandardCharsets.UTF_8);
        return new DriveContent(new ByteArrayResource(bytes), bytes.length,
                name, contentType, inline);
    }
}
