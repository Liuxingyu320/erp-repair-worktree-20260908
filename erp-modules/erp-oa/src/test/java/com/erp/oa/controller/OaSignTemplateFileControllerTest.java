package com.erp.oa.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.oa.domain.vo.OaSignTemplateFile;
import com.erp.oa.service.impl.OaSignHrAccessService;
import com.erp.oa.service.impl.OaSignTemplateFileService;

@DisplayName("签约模板文件Controller")
class OaSignTemplateFileControllerTest
{
    @Test
    @DisplayName("预览使用模板维护权限和HR门禁并以PDF内联响应")
    void shouldReturnAuthenticatedInlinePreview() throws Exception
    {
        Method method = OaSignPackageController.class.getMethod("templatePreview", Long.class);
        assertThat(method.getAnnotation(RequiresPermissions.class).value())
                .containsExactly("oa:signPackage:template");
        assertThat(method.getAnnotation(GetMapping.class).value())
                .containsExactly("/template/{templateId}/preview");

        OaSignPackageController controller = new OaSignPackageController();
        OaSignTemplateFileService fileService = mock(OaSignTemplateFileService.class);
        OaSignHrAccessService accessService = mock(OaSignHrAccessService.class);
        byte[] pdf = "%PDF-1.7".getBytes(StandardCharsets.ISO_8859_1);
        when(fileService.preview(80L)).thenReturn(
                new OaSignTemplateFile(pdf, "劳动合同.pdf", "application/pdf"));
        ReflectionTestUtils.setField(controller, "signTemplateFileService", fileService);
        ReflectionTestUtils.setField(controller, "signHrAccessService", accessService);

        ResponseEntity<byte[]> response = controller.templatePreview(80L);

        verify(accessService).requireCurrentHr();
        verify(fileService).preview(80L);
        assertThat(response.getBody()).containsExactly(pdf);
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/pdf");
        assertThat(response.getHeaders().getContentDisposition().getType()).isEqualTo("inline");
        assertThat(response.getHeaders().getContentDisposition().getFilename()).isEqualTo("劳动合同.pdf");
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeaders().getContentLength()).isEqualTo(pdf.length);
    }

    @Test
    @DisplayName("原件下载使用同一权限和HR门禁并以附件响应")
    void shouldReturnAuthenticatedAttachment() throws Exception
    {
        Method method = OaSignPackageController.class.getMethod("templateFile", Long.class);
        assertThat(method.getAnnotation(RequiresPermissions.class).value())
                .containsExactly("oa:signPackage:template");
        assertThat(method.getAnnotation(GetMapping.class).value())
                .containsExactly("/template/{templateId}/file");

        OaSignPackageController controller = new OaSignPackageController();
        OaSignTemplateFileService fileService = mock(OaSignTemplateFileService.class);
        OaSignHrAccessService accessService = mock(OaSignHrAccessService.class);
        byte[] source = "docx".getBytes(StandardCharsets.UTF_8);
        String contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        when(fileService.download(81L)).thenReturn(
                new OaSignTemplateFile(source, "劳动合同.docx", contentType));
        ReflectionTestUtils.setField(controller, "signTemplateFileService", fileService);
        ReflectionTestUtils.setField(controller, "signHrAccessService", accessService);

        ResponseEntity<byte[]> response = controller.templateFile(81L);

        verify(accessService).requireCurrentHr();
        verify(fileService).download(81L);
        assertThat(response.getBody()).containsExactly(source);
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo(contentType);
        assertThat(response.getHeaders().getContentDisposition().getType()).isEqualTo("attachment");
        assertThat(response.getHeaders().getContentDisposition().getFilename()).isEqualTo("劳动合同.docx");
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeaders().getContentLength()).isEqualTo(source.length);
    }
}
