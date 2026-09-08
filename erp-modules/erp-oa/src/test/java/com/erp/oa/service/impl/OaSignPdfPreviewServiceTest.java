package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.domain.vo.OaSignPackageFile;

@DisplayName("签约PDF移动端图片预览")
class OaSignPdfPreviewServiceTest
{
    static
    {
        System.setProperty("java.awt.headless", "true");
    }

    private final OaSignPdfPreviewService service = new OaSignPdfPreviewService();

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("应返回PDF页数并把指定页渲染为PNG")
    void shouldCountPagesAndRenderPng() throws Exception
    {
        OaSignPackageFile file = pdfFile("two-pages.pdf", 2);

        assertThat(service.pageCount(file)).isEqualTo(2);
        byte[] content = service.renderPage(file, 1);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));

        assertThat(content).startsWith(0x89, 0x50, 0x4e, 0x47);
        assertThat(image).isNotNull();
        assertThat(image.getWidth()).isPositive();
        assertThat(image.getHeight()).isPositive();
    }

    @Test
    @DisplayName("应拒绝从0开始或超出范围的页码")
    void shouldRejectInvalidPageNumber() throws Exception
    {
        OaSignPackageFile file = pdfFile("two-pages.pdf", 2);

        assertThatThrownBy(() -> service.renderPage(file, 0))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("从1开始");
        assertThatThrownBy(() -> service.renderPage(file, 3))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("共2页");
    }

    @Test
    @DisplayName("应拒绝非PDF内容类型")
    void shouldRejectNonPdfContentType() throws Exception
    {
        Path path = tempDir.resolve("document.txt");
        java.nio.file.Files.writeString(path, "not a pdf");
        OaSignPackageFile file = new OaSignPackageFile(path, "document.txt", "text/plain");

        assertThatThrownBy(() -> service.pageCount(file))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("仅支持PDF");
    }

    @Test
    @DisplayName("应拒绝超过移动端上限的PDF")
    void shouldRejectPdfOverPageLimit() throws Exception
    {
        OaSignPackageFile file = pdfFile("too-many-pages.pdf",
                OaSignPdfPreviewService.MAX_PREVIEW_PAGE_COUNT + 1);

        assertThatThrownBy(() -> service.pageCount(file))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("200页");
    }

    private OaSignPackageFile pdfFile(String fileName, int pageCount) throws Exception
    {
        Path path = tempDir.resolve(fileName);
        try (PDDocument document = new PDDocument())
        {
            for (int index = 0; index < pageCount; index++)
            {
                document.addPage(new PDPage(PDRectangle.A4));
            }
            document.save(path.toFile());
        }
        return new OaSignPackageFile(path, fileName, "application/pdf");
    }
}
