package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.domain.OaSignPackageDocument;
import com.erp.oa.domain.vo.OaSignPackageFile;

@DisplayName("签约包文件解析")
class OaSignPackageFileResolverTest
{
    @TempDir
    Path tempDir;

    @Test
    @DisplayName("阅读文件优先使用 PDF 并净化下载文件名")
    void sourcePrefersPdfAndSanitizesFileName()
    {
        OaSignDocumentService documentService = mock(OaSignDocumentService.class);
        OaSignPackageDocument document = document("劳动/合同\n");
        document.setGeneratedFileUrl("/private/source.docx");
        document.setGeneratedPdfUrl("/private/review.pdf");
        Path review = tempDir.resolve("review.pdf");
        when(documentService.resolveGeneratedSignPackageFile("/private/review.pdf"))
                .thenReturn(review);

        OaSignPackageFile file = new OaSignPackageFileResolver(documentService).source(document);

        assertThat(file.getPath()).isEqualTo(review);
        assertThat(file.getFileName()).isEqualTo("劳动_合同_.pdf");
        assertThat(file.getContentType()).isEqualTo("application/pdf");
        verify(documentService, never())
                .resolveGeneratedSignPackageFile("/private/source.docx");
    }

    @Test
    @DisplayName("已签文件和签署证明都要求完成签署及独立文件")
    void signedArtifactsRequireSignedEvidence()
    {
        OaSignDocumentService documentService = mock(OaSignDocumentService.class);
        OaSignPackageFileResolver resolver = new OaSignPackageFileResolver(documentService);
        OaSignPackageDocument document = document("入职承诺书");

        assertThatThrownBy(() -> resolver.signed(document))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已签文件尚未生成");
        assertThatThrownBy(() -> resolver.certificate(document))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("签署证明尚未生成");

        document.setSigned("Y");
        document.setSignedPdfUrl("/private/signed.pdf");
        document.setCertificateFileUrl("/private/certificate.pdf");
        when(documentService.resolveGeneratedSignPackageFile("/private/signed.pdf"))
                .thenReturn(tempDir.resolve("signed.pdf"));
        when(documentService.resolveGeneratedSignPackageFile("/private/certificate.pdf"))
                .thenReturn(tempDir.resolve("certificate.pdf"));

        assertThat(resolver.signed(document).getFileName()).isEqualTo("入职承诺书-已签.pdf");
        assertThat(resolver.certificate(document).getFileName())
                .isEqualTo("入职承诺书-签署证明.pdf");
    }

    @Test
    @DisplayName("最终合同按冻结哈希验真且历史包可回退最终候选")
    void finalCandidateIsVerifiedAndSupportsHistoricalArchiveFallback() throws Exception
    {
        OaSignDocumentService documentService = mock(OaSignDocumentService.class);
        OaSignPackageFileResolver resolver = new OaSignPackageFileResolver(documentService);
        OaSignPackageDocument document = document("劳动合同");
        Path finalPdf = tempDir.resolve("final.pdf");
        Files.writeString(finalPdf, "%PDF-1.4 final", StandardCharsets.ISO_8859_1);
        document.setFinalPdfUrl("/private/final.pdf");
        document.setFinalPdfHash(sha256(finalPdf));
        when(documentService.resolveGeneratedSignPackageFile("/private/final.pdf"))
                .thenReturn(finalPdf);

        assertThat(resolver.finalCandidate(document).getFileName())
                .isEqualTo("劳动合同-最终合同.pdf");
        assertThat(resolver.finalArchive(document).getPath()).isEqualTo(finalPdf);

        document.setFinalPdfHash("0".repeat(64));
        assertThatThrownBy(() -> resolver.finalCandidate(document))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("最终合同文件校验不一致");
    }

    @Test
    @DisplayName("最终归档使用独立文件并校验归档哈希")
    void finalArchiveUsesIndependentVerifiedArtifact() throws Exception
    {
        OaSignDocumentService documentService = mock(OaSignDocumentService.class);
        OaSignPackageFileResolver resolver = new OaSignPackageFileResolver(documentService);
        OaSignPackageDocument document = document("劳动合同");
        Path archive = tempDir.resolve("archive.pdf");
        Files.writeString(archive, "%PDF-1.4 archive", StandardCharsets.ISO_8859_1);
        document.setFinalArchivePdfUrl("/private/archive.pdf");
        document.setFinalArchivePdfHash(sha256(archive));
        when(documentService.resolveGeneratedSignPackageFile("/private/archive.pdf"))
                .thenReturn(archive);

        OaSignPackageFile file = resolver.finalArchive(document);

        assertThat(file.getPath()).isEqualTo(archive);
        assertThat(file.getFileName()).isEqualTo("劳动合同-最终归档.pdf");
        document.setFinalArchivePdfHash("f".repeat(64));
        assertThatThrownBy(() -> resolver.finalArchive(document))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("最终归档文件校验不一致");
    }

    @Test
    @DisplayName("大归档文件哈希走流式读取且只返回一次已计算校验值")
    void finalArchiveOnlyStreamsLargeArchiveHash() throws Exception
    {
        OaSignDocumentService documentService = mock(OaSignDocumentService.class);
        OaSignPackageFileResolver resolver = new OaSignPackageFileResolver(documentService);
        OaSignPackageDocument document = document("大合同");
        Path archive = tempDir.resolve("large-archive.pdf");
        java.security.MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] block = new byte[1024 * 1024];
        for (int index = 0; index < block.length; index++)
        {
            block[index] = (byte) (index * 31);
        }
        try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(archive)))
        {
            for (int chunk = 0; chunk < 8; chunk++)
            {
                output.write(block);
                digest.update(block);
            }
        }
        document.setFinalArchivePdfUrl("/private/large-archive.pdf");
        document.setFinalArchivePdfHash(java.util.HexFormat.of().formatHex(digest.digest()));
        when(documentService.resolveGeneratedSignPackageFile("/private/large-archive.pdf"))
                .thenReturn(archive);

        OaSignPackageFile file = resolver.finalArchiveOnly(document);

        assertThat(file.getPath()).isEqualTo(archive);
        assertThat(file.getFileHash()).isEqualTo(document.getFinalArchivePdfHash());
        assertThat(Files.size(archive)).isEqualTo(8L * 1024L * 1024L);
    }

    private OaSignPackageDocument document(String name)
    {
        OaSignPackageDocument document = new OaSignPackageDocument();
        document.setDocumentName(name);
        return document;
    }

    private String sha256(Path path) throws Exception
    {
        return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
