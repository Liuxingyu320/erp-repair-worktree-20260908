package com.erp.oa.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.security.annotation.RequiresLogin;
import com.erp.oa.domain.vo.OaSignPackageFile;
import com.erp.oa.service.IOaSignPackageService;
import com.erp.oa.service.impl.OaSignPdfPreviewService;

@DisplayName("员工签约移动端图片预览Controller")
class OaSignPackageMobilePreviewControllerTest
{
    private OaSignPackageController controller;
    private IOaSignPackageService signPackageService;
    private OaSignPdfPreviewService previewService;

    @BeforeEach
    void setUp()
    {
        controller = new OaSignPackageController();
        signPackageService = mock(IOaSignPackageService.class);
        previewService = mock(OaSignPdfPreviewService.class);
        ReflectionTestUtils.setField(controller, "signPackageService", signPackageService);
        ReflectionTestUtils.setField(controller, "signPdfPreviewService", previewService);
    }

    @Test
    @DisplayName("review页数接口应复用员工本人首次文件权限并禁止缓存")
    void shouldResolveReviewFileAndReturnPageCount()
    {
        OaSignPackageFile file = packageFile("review.pdf");
        when(signPackageService.resolveMyDocumentPreviewFile(10L, 20L)).thenReturn(file);
        when(previewService.pageCount(file)).thenReturn(5);

        ResponseEntity<AjaxResult> response = controller.mobileDocumentPreview(10L, 20L, "review");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getBody()).isNotNull();
        assertThat(((Map<?, ?>) response.getBody().get("data")).get("pageCount")).isEqualTo(5);
        verify(signPackageService).resolveMyDocumentPreviewFile(10L, 20L);
        verify(signPackageService, never()).resolveMyDocumentFile(10L, 20L);
        verify(previewService).pageCount(file);
    }

    @Test
    @DisplayName("final单页接口应复用员工本人最终文件权限并返回PNG")
    void shouldResolveFinalFileAndReturnPngPage()
    {
        OaSignPackageFile file = packageFile("final.pdf");
        byte[] png = { (byte) 0x89, 0x50, 0x4e, 0x47 };
        when(signPackageService.resolveMyFinalDocumentPreviewFile(10L, 20L)).thenReturn(file);
        when(previewService.renderPage(file, 2)).thenReturn(png);

        ResponseEntity<byte[]> response = controller.mobileDocumentPreviewPage(10L, 20L, 2, "final");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeaders().getContentLength()).isEqualTo(png.length);
        assertThat(response.getBody()).containsExactly(png);
        verify(signPackageService).resolveMyFinalDocumentPreviewFile(10L, 20L);
        verify(signPackageService, never()).resolveMyFinalDocumentFile(10L, 20L);
        verify(previewService).renderPage(file, 2);
    }

    @Test
    @DisplayName("预览类型仅允许review或final")
    void shouldRejectUnsupportedPreviewKind()
    {
        assertThatThrownBy(() -> controller.mobileDocumentPreview(10L, 20L, "signed"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("review 或 final");

        verify(signPackageService, never()).resolveMyDocumentFile(10L, 20L);
        verify(signPackageService, never()).resolveMyDocumentPreviewFile(10L, 20L);
        verify(signPackageService, never()).resolveMyFinalDocumentFile(10L, 20L);
        verify(signPackageService, never()).resolveMyFinalDocumentPreviewFile(10L, 20L);
    }

    @Test
    @DisplayName("两个移动端预览接口都必须登录")
    void previewEndpointsShouldRequireLogin() throws NoSuchMethodException
    {
        Method metadata = OaSignPackageController.class.getMethod("mobileDocumentPreview",
                Long.class, Long.class, String.class);
        Method page = OaSignPackageController.class.getMethod("mobileDocumentPreviewPage",
                Long.class, Long.class, int.class, String.class);

        assertThat(metadata.getAnnotation(RequiresLogin.class)).isNotNull();
        assertThat(page.getAnnotation(RequiresLogin.class)).isNotNull();
        assertThat(metadata.getAnnotation(GetMapping.class).value())
                .containsExactly("/mobile/{packageId}/documents/{documentId}/preview");
        assertThat(page.getAnnotation(GetMapping.class).value())
                .containsExactly("/mobile/{packageId}/documents/{documentId}/preview/{pageNumber}");
    }

    private OaSignPackageFile packageFile(String fileName)
    {
        return new OaSignPackageFile(Path.of(fileName), fileName, "application/pdf");
    }
}
