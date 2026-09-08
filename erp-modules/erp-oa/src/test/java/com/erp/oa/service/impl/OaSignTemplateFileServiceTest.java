package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.config.OaSignFileProperties;
import com.erp.oa.constant.OaSignTemplateType;
import com.erp.oa.domain.OaSignTemplate;
import com.erp.oa.domain.vo.OaSignTemplateFile;
import com.erp.oa.mapper.OaSignTemplateMapper;

@DisplayName("签约模板文件访问")
class OaSignTemplateFileServiceTest
{
    @TempDir
    Path tempDir;

    @Test
    @DisplayName("原件下载按数据库ID解析并校验大小和摘要")
    void shouldDownloadValidatedTemplateById() throws Exception
    {
        Fixture fixture = fixture();
        byte[] source = "validated-docx".getBytes(StandardCharsets.UTF_8);
        OaSignTemplate template = fixture.store(7L, "release/contract.docx", source,
                OaSignTemplateType.ONBOARD_LABOR_CONTRACT);
        template.setFileName("../劳动\r\n合同.docx");

        OaSignTemplateFile result = fixture.service.download(7L);

        assertThat(result.getContent()).containsExactly(source);
        assertThat(result.getContentType()).isEqualTo(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(result.getFileName()).isEqualTo("__劳动_合同.docx");
        verify(fixture.converter, never()).convert(any(), any());
    }

    @Test
    @DisplayName("预览在独立临时目录复制已校验源文件并在成功后清理")
    void shouldConvertValidatedCopyAndCleanupAfterSuccess() throws Exception
    {
        Fixture fixture = fixture();
        byte[] source = "source-docx".getBytes(StandardCharsets.UTF_8);
        fixture.store(8L, "release/template.docx", source,
                OaSignTemplateType.ONBOARD_LABOR_CONTRACT).setFileName("劳动合同.docx");
        AtomicReference<Path> staging = new AtomicReference<>();
        when(fixture.converter.convert(any(Path.class), any(Path.class))).thenAnswer(invocation -> {
            Path stagedSource = invocation.getArgument(0, Path.class);
            Path directory = invocation.getArgument(1, Path.class);
            staging.set(directory);
            assertThat(stagedSource).startsWith(directory);
            assertThat(Files.readAllBytes(stagedSource)).containsExactly(source);
            Path pdf = directory.resolve("template-source.pdf");
            Files.write(pdf, "%PDF-1.7\npreview".getBytes(StandardCharsets.ISO_8859_1));
            return pdf;
        });

        OaSignTemplateFile result = fixture.service.preview(8L);

        assertThat(result.getContentType()).isEqualTo("application/pdf");
        assertThat(result.getFileName()).isEqualTo("劳动合同.pdf");
        assertThat(new String(result.getContent(), StandardCharsets.ISO_8859_1)).startsWith("%PDF-");
        assertThat(staging.get()).doesNotExist();
    }

    @Test
    @DisplayName("转换失败后仍清理独立临时目录")
    void shouldCleanupAfterConversionFailure() throws Exception
    {
        Fixture fixture = fixture();
        fixture.store(9L, "release/failure.docx", "docx".getBytes(StandardCharsets.UTF_8),
                OaSignTemplateType.ONBOARD_LABOR_CONTRACT);
        AtomicReference<Path> staging = new AtomicReference<>();
        when(fixture.converter.convert(any(Path.class), any(Path.class))).thenAnswer(invocation -> {
            staging.set(invocation.getArgument(1, Path.class));
            throw new ServiceException("PDF_CONVERSION_FAILED: test");
        });

        assertThatThrownBy(() -> fixture.service.preview(9L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("PDF_CONVERSION_FAILED");
        assertThat(staging.get()).doesNotExist();
    }

    @Test
    @DisplayName("拒绝逃离sign-template根目录的遍历路径")
    void shouldRejectTraversalPath() throws Exception
    {
        Fixture fixture = fixture();
        Files.createDirectories(fixture.templateRoot);
        fixture.map(template(10L, "/profile/sign-template/../outside.docx",
                OaSignTemplateType.ONBOARD_LABOR_CONTRACT, 4L, sha256("test".getBytes())));

        assertThatThrownBy(() -> fixture.service.download(10L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("地址无效");
    }

    @Test
    @DisplayName("拒绝数据库中的绝对路径和网络URL")
    void shouldRejectNonConfiguredUrl()
    {
        Fixture fixture = fixture();
        fixture.map(template(11L, tempDir.resolve("outside.docx").toString(),
                OaSignTemplateType.ONBOARD_LABOR_CONTRACT, 4L, sha256("test".getBytes())));

        assertThatThrownBy(() -> fixture.service.download(11L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("地址无效");

        fixture.map(template(12L, "https://example.test/template.docx",
                OaSignTemplateType.ONBOARD_LABOR_CONTRACT, 4L, sha256("test".getBytes())));
        assertThatThrownBy(() -> fixture.service.download(12L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("地址无效");
    }

    @Test
    @DisplayName("使用realpath拒绝通过符号链接逃离模板根目录")
    void shouldRejectSymlinkEscape() throws Exception
    {
        Fixture fixture = fixture();
        Path release = Files.createDirectories(fixture.templateRoot.resolve("release"));
        Path outside = Files.write(tempDir.resolve("outside.docx"),
                "outside".getBytes(StandardCharsets.UTF_8));
        Path link = release.resolve("escape.docx");
        try
        {
            Files.createSymbolicLink(link, outside);
        }
        catch (UnsupportedOperationException | IOException | SecurityException e)
        {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "当前文件系统不支持符号链接测试");
        }
        fixture.map(template(13L, "/profile/sign-template/release/escape.docx",
                OaSignTemplateType.ONBOARD_LABOR_CONTRACT, Files.size(outside), sha256(Files.readAllBytes(outside))));

        assertThatThrownBy(() -> fixture.service.download(13L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("地址无效");
    }

    @Test
    @DisplayName("拒绝文件扩展名与模板类型不一致")
    void shouldRejectTypeMismatch() throws Exception
    {
        Fixture fixture = fixture();
        byte[] source = "not-xlsx".getBytes(StandardCharsets.UTF_8);
        fixture.store(14L, "release/application.docx", source,
                OaSignTemplateType.ONBOARD_APPLICATION_FORM);

        assertThatThrownBy(() -> fixture.service.download(14L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("文件类型");
    }

    @Test
    @DisplayName("拒绝文件大小或SHA256与登记值不一致")
    void shouldRejectSizeAndHashMismatch() throws Exception
    {
        Fixture fixture = fixture();
        byte[] source = "immutable-source".getBytes(StandardCharsets.UTF_8);
        OaSignTemplate sizeMismatch = fixture.store(15L, "release/size.docx", source,
                OaSignTemplateType.ONBOARD_LABOR_CONTRACT);
        sizeMismatch.setFileSize(sizeMismatch.getFileSize() + 1);

        assertThatThrownBy(() -> fixture.service.download(15L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("大小校验");

        OaSignTemplate hashMismatch = fixture.store(16L, "release/hash.docx", source,
                OaSignTemplateType.ONBOARD_LABOR_CONTRACT);
        hashMismatch.setFileHash("0".repeat(64));
        assertThatThrownBy(() -> fixture.service.download(16L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("校验值不一致");
    }

    @Test
    @DisplayName("拒绝未登记文件大小或摘要的模板")
    void shouldRejectMissingIntegrityMetadata() throws Exception
    {
        Fixture fixture = fixture();
        OaSignTemplate template = fixture.store(17L, "release/missing.docx",
                "source".getBytes(StandardCharsets.UTF_8), OaSignTemplateType.ONBOARD_LABOR_CONTRACT);
        template.setFileHash(null);

        assertThatThrownBy(() -> fixture.service.download(17L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("校验值未正确登记");
    }

    @Test
    @DisplayName("拒绝空编号和不存在的模板")
    void shouldRejectInvalidOrMissingTemplateId()
    {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> fixture.service.download(0L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("编号不合法");
        assertThatThrownBy(() -> fixture.service.download(999L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("模板不存在");
    }

    private Fixture fixture()
    {
        return new Fixture();
    }

    private static OaSignTemplate template(Long id, String fileUrl, String type, Long size, String hash)
    {
        OaSignTemplate template = new OaSignTemplate();
        template.setTemplateId(id);
        template.setTemplateType(type);
        template.setTemplateName("签约模板");
        template.setFileUrl(fileUrl);
        template.setFileName("签约模板.docx");
        template.setFileSize(size);
        template.setFileHash(hash);
        return template;
    }

    private static String sha256(byte[] bytes)
    {
        try
        {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
    }

    private final class Fixture
    {
        private final Path uploadRoot = tempDir.resolve("uploadPath");
        private final Path templateRoot = uploadRoot.resolve("sign-template");
        private final OaSignTemplateMapper mapper = mock(OaSignTemplateMapper.class);
        private final OaOfficePdfConverter converter = mock(OaOfficePdfConverter.class);
        private final OaSignFileProperties properties = properties();
        private final OaSignTemplateFileService service = new OaSignTemplateFileService(
                mapper, converter, properties, uploadRoot.toString(), "/profile");

        private OaSignFileProperties properties()
        {
            OaSignFileProperties value = new OaSignFileProperties();
            value.getStorage().setTempPath(tempDir.resolve("sign-temp").toString());
            return value;
        }

        private OaSignTemplate store(Long id, String relative, byte[] content, String type) throws Exception
        {
            Path file = templateRoot.resolve(relative);
            Files.createDirectories(file.getParent());
            Files.write(file, content);
            String extension = file.getFileName().toString().substring(file.getFileName().toString().lastIndexOf('.'));
            OaSignTemplate template = template(id, "/profile/sign-template/" + relative,
                    type, (long) content.length, sha256(content));
            template.setFileName("签约模板" + extension);
            map(template);
            return template;
        }

        private void map(OaSignTemplate template)
        {
            when(mapper.selectOaSignTemplateById(template.getTemplateId())).thenReturn(template);
        }
    }
}
