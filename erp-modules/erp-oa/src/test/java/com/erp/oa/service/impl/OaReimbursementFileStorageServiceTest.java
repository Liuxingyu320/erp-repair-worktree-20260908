package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.config.OaReimbursementProperties;

@DisplayName("报销发票私有存储")
class OaReimbursementFileStorageServiceTest
{
    @TempDir
    Path workDir;

    private OaReimbursementFileStorageService service;

    @BeforeEach
    void setUp()
    {
        OaReimbursementProperties properties =
                new OaReimbursementProperties();
        properties.setStorageRoot(workDir.resolve("private").toString());
        properties.setTempRoot(workDir.resolve("temp").toString());
        properties.setMaxInvoiceBytes(1024);
        service = new OaReimbursementFileStorageService(properties);
    }

    @Test
    @DisplayName("PDF按服务端随机文件名保存并记录内容校验值")
    void shouldStoreValidatedPdfWithoutClientPath() throws Exception
    {
        byte[] content = "%PDF-1.4\n%%EOF"
                .getBytes(StandardCharsets.US_ASCII);
        MockMultipartFile file = new MockMultipartFile("file",
                "C:\\fakepath\\差旅发票.pdf", "application/octet-stream",
                content);

        OaReimbursementFileStorageService.StoredInvoice stored =
                service.storeInvoice(17L, file);

        assertThat(stored.originalName()).isEqualTo("差旅发票.pdf");
        assertThat(stored.storedName())
                .matches("[0-9a-f]{32}\\.pdf")
                .isNotEqualTo(stored.originalName());
        assertThat(stored.relativePath())
                .startsWith("invoices/17/")
                .doesNotContain("fakepath");
        assertThat(stored.contentType()).isEqualTo("application/pdf");
        assertThat(stored.sha256()).hasSize(64);
        assertThat(service.resolve(stored.relativePath()))
                .isRegularFile()
                .hasBinaryContent(content);
    }

    @Test
    @DisplayName("扩展名与内容不一致、超限文件及越界路径全部拒绝")
    void shouldRejectDisguisedOversizedAndEscapingFiles()
    {
        MockMultipartFile disguised = new MockMultipartFile("file",
                "invoice.pdf", "application/pdf",
                "<html>not an invoice</html>"
                        .getBytes(StandardCharsets.UTF_8));
        MockMultipartFile oversized = new MockMultipartFile("file",
                "invoice.pdf", "application/pdf", new byte[1025]);

        assertThatThrownBy(() -> service.storeInvoice(17L, disguised))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("内容与格式");
        assertThatThrownBy(() -> service.storeInvoice(17L, oversized))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不能超过");
        assertThatThrownBy(() -> service.resolve("../outside.pdf"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("路径越界");
        assertThatThrownBy(() -> service.resolve("..\\outside.pdf"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("路径无效");
    }

    @Test
    @DisplayName("会计资料包只能从受控临时目录原子归档")
    void shouldPromoteOnlyManagedExportFile() throws Exception
    {
        String batchNo = "BXDC20260730123000ABCD1234";
        Path temporary = service.createExportTemp(batchNo);
        Files.writeString(temporary, "archive", StandardCharsets.UTF_8);

        OaReimbursementFileStorageService.StoredExport stored =
                service.promoteExport(temporary, batchNo);

        assertThat(temporary).doesNotExist();
        assertThat(stored.relativePath()).isEqualTo(
                "exports/" + batchNo + ".zip");
        assertThat(service.resolve(stored.relativePath()))
                .hasContent("archive");
        assertThatThrownBy(() -> service.promoteExport(
                workDir.resolve("outside.zip"), batchNo))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("临时文件无效");
    }
}
