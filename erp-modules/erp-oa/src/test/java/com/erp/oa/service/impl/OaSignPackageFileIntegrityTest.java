package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPackageDocument;
import com.erp.oa.domain.vo.SignedPdfResult;

@DisplayName("签约包文件完整性")
class OaSignPackageFileIntegrityTest
{
    @TempDir
    Path tempDir;

    private final OaSignDocumentService documentService =
            mock(OaSignDocumentService.class);
    private final OaSignedPdfService signedPdfService =
            mock(OaSignedPdfService.class);
    private final OaSignPackageFileIntegrity integrity =
            new OaSignPackageFileIntegrity(documentService, signedPdfService);

    @Test
    @DisplayName("哈希比较忽略十六进制大小写但拒绝空值")
    void comparesHashesWithoutWeakeningNullChecks() throws Exception
    {
        byte[] bytes = "stable-content".getBytes(StandardCharsets.UTF_8);
        String expected = sha256(bytes);

        assertThat(integrity.sha256(bytes)).isEqualTo(expected);
        assertThat(integrity.sameHash(expected, expected.toUpperCase())).isTrue();
        assertThat(integrity.sameHash(null, expected)).isFalse();
        assertThat(integrity.sameHash(expected, null)).isFalse();
    }

    @Test
    @DisplayName("最终正文和归档根哈希按文档编号排序且使用各自哈希")
    void buildsDeterministicFinalAndArchiveRoots() throws Exception
    {
        Map<Long, SignedPdfResult> results = new LinkedHashMap<>();
        results.put(20L, signedPdf("signed-20", "content-20"));
        results.put(10L, signedPdf("signed-10", "content-10"));

        assertThat(integrity.finalDocumentRootHash(results))
                .isEqualTo(sha256("10:content-10\n20:content-20\n"
                        .getBytes(StandardCharsets.UTF_8)));
        assertThat(integrity.archiveDocumentRootHash(results))
                .isEqualTo(sha256("10:signed-10\n20:signed-20\n"
                        .getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("最终文档复验磁盘哈希和稳定正文哈希后返回根哈希输入")
    void validatesFinalDocumentsAgainstDiskAndStableContent() throws Exception
    {
        byte[] bytes = "%PDF-1.4 final".getBytes(StandardCharsets.ISO_8859_1);
        Path finalPdf = tempDir.resolve("final.pdf");
        Files.write(finalPdf, bytes);
        String fileHash = sha256(bytes);
        OaSignPackage signPackage = signPackage("FINAL-V1");
        OaSignPackageDocument document = finalDocument(
                11L, "FINAL-V1", "/private/final.pdf", fileHash, "content-hash");
        when(documentService.resolveGeneratedSignPackageFile(
                "/private/final.pdf")).thenReturn(finalPdf);

        Map<Long, String> hashes = integrity.validateFinalDocuments(
                signPackage, List.of(document));

        assertThat(hashes).containsExactlyEntriesOf(
                Map.of(11L, "content-hash"));
        verify(signedPdfService).validatePendingFinalContentHash(
                finalPdf, fileHash, "content-hash");
    }

    @Test
    @DisplayName("最终文档元数据不完整时在读取磁盘前失败关闭")
    void rejectsIncompleteFinalDocumentBeforeDiskRead()
    {
        OaSignPackage signPackage = signPackage("FINAL-V1");
        OaSignPackageDocument document = finalDocument(
                11L, "FINAL-V2", "/private/final.pdf",
                "file-hash", "content-hash");

        assertThatThrownBy(() -> integrity.validateFinalDocuments(
                signPackage, List.of(document)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("最终合同文件不完整");
        verify(documentService, never())
                .resolveGeneratedSignPackageFile("/private/final.pdf");
    }

    @Test
    @DisplayName("最终文档磁盘哈希变化时拒绝继续验证稳定正文")
    void rejectsChangedFinalDocumentBytes() throws Exception
    {
        Path finalPdf = tempDir.resolve("changed-final.pdf");
        Files.writeString(finalPdf, "%PDF-1.4 changed",
                StandardCharsets.ISO_8859_1);
        OaSignPackage signPackage = signPackage("FINAL-V1");
        OaSignPackageDocument document = finalDocument(
                11L, "FINAL-V1", "/private/final.pdf",
                "different-hash", "content-hash");
        when(documentService.resolveGeneratedSignPackageFile(
                "/private/final.pdf")).thenReturn(finalPdf);

        assertThatThrownBy(() -> integrity.validateFinalDocuments(
                signPackage, List.of(document)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("最终合同文件校验不一致");
        verify(signedPdfService, never()).validatePendingFinalContentHash(
                finalPdf, "different-hash", "content-hash");
    }

    @Test
    @DisplayName("最终归档优先复验逐文件首次签名")
    void readsAndValidatesDocumentSignature() throws Exception
    {
        byte[] signature = pngBytes("document");
        Path signaturePath = tempDir.resolve("document-signature.png");
        Files.write(signaturePath, signature);
        OaSignPackageDocument document = new OaSignPackageDocument();
        document.setSignatureFileUrl("/private/document-signature.png");
        document.setSignatureHash(sha256(signature));
        when(documentService.resolveGeneratedSignPackageFile(
                document.getSignatureFileUrl())).thenReturn(signaturePath);

        assertThat(integrity.readFinalSignatureBytes(
                new OaSignPackage(), document)).containsExactly(signature);
    }

    @Test
    @DisplayName("逐文件签名缺失时复验任务级手写签名样本")
    void fallsBackToValidatedTaskSignatureSample() throws Exception
    {
        byte[] signature = pngBytes("task");
        Path signaturePath = tempDir.resolve("task-signature.png");
        Files.write(signaturePath, signature);
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setSignatureSampleFileUrl("/private/task-signature.png");
        signPackage.setSignatureSampleHash(sha256(signature));
        when(documentService.resolveGeneratedSignPackageFile(
                signPackage.getSignatureSampleFileUrl())).thenReturn(signaturePath);

        assertThat(integrity.readFinalSignatureBytes(
                signPackage, new OaSignPackageDocument()))
                .containsExactly(signature);
    }

    @Test
    @DisplayName("任务级手写签名样本哈希不一致时失败关闭")
    void rejectsChangedTaskSignatureSample() throws Exception
    {
        Path signaturePath = tempDir.resolve("changed-task-signature.png");
        Files.write(signaturePath, pngBytes("changed"));
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setSignatureSampleFileUrl("/private/task-signature.png");
        signPackage.setSignatureSampleHash("different-hash");
        when(documentService.resolveGeneratedSignPackageFile(
                signPackage.getSignatureSampleFileUrl())).thenReturn(signaturePath);

        assertThatThrownBy(() -> integrity.readFinalSignatureBytes(
                signPackage, new OaSignPackageDocument()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("本任务手写签名样本校验不一致");
    }

    private OaSignPackage signPackage(String finalVersion)
    {
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setFinalDocumentVersion(finalVersion);
        return signPackage;
    }

    private OaSignPackageDocument finalDocument(Long documentId,
            String finalVersion, String fileUrl, String fileHash,
            String contentHash)
    {
        OaSignPackageDocument document = new OaSignPackageDocument();
        document.setDocumentId(documentId);
        document.setFinalDocumentVersion(finalVersion);
        document.setFinalPdfUrl(fileUrl);
        document.setFinalPdfHash(fileHash);
        document.setFinalContentHash(contentHash);
        return document;
    }

    private SignedPdfResult signedPdf(String signedHash, String contentHash)
    {
        return new SignedPdfResult(
                "archive/final.pdf", "/private/final.pdf",
                signedHash, 20L, null, null, null, 0L,
                null, 1, contentHash);
    }

    private byte[] pngBytes(String suffix)
    {
        byte[] tail = suffix.getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[8 + tail.length];
        byte[] header = new byte[] {
                (byte) 0x89, 0x50, 0x4e, 0x47,
                0x0d, 0x0a, 0x1a, 0x0a };
        System.arraycopy(header, 0, bytes, 0, header.length);
        System.arraycopy(tail, 0, bytes, header.length, tail.length);
        return bytes;
    }

    private String sha256(byte[] bytes) throws Exception
    {
        return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
