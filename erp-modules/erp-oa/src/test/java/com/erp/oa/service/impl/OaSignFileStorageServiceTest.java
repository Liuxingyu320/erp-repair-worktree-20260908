package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.config.OaSignFileProperties;
import com.erp.oa.domain.vo.StagedSignFile;

@DisplayName("签约文件临时生成与原子归档")
class OaSignFileStorageServiceTest
{
    @TempDir
    Path workDir;

    private Path root;
    private Path temp;
    private OaSignFileStorageService service;

    @BeforeEach
    void setUp()
    {
        root = workDir.resolve("archive");
        temp = workDir.resolve("temp");
        OaSignFileProperties properties = new OaSignFileProperties();
        properties.getStorage().setRootPath(root.toString());
        properties.getStorage().setTempPath(temp.toString());
        properties.getStorage().setPublicPrefix("/profile/private/sign-package");
        service = new OaSignFileStorageService(properties);
    }

    @Test
    @DisplayName("临时文件使用服务端文件名并记录hash和大小")
    void shouldStageWithServerFilenameHashAndSize() throws Exception
    {
        byte[] content = "合同阅读件".getBytes(StandardCharsets.UTF_8);

        StagedSignFile staged = service.stage(7L, 20L, "SP-20-V1", "劳动合同.pdf", content);

        assertThat(staged.getTempPath()).isRegularFile();
        assertThat(staged.getServerFilename()).isNotEqualTo("劳动合同.pdf").endsWith(".pdf");
        assertThat(staged.getFileSize()).isEqualTo(content.length);
        assertThat(staged.getFileHash()).isEqualTo(sha256(content));
        assertThat(staged.getArchiveRelativePath()).doesNotContain("劳动合同").doesNotContain("..");
    }

    @Test
    @DisplayName("路径穿越和客户端路径必须拒绝")
    void shouldRejectPathTraversalAndClientPaths()
    {
        assertThatThrownBy(() -> service.stage(7L, 20L, "SP-20-V1", "../contract.pdf", new byte[] {1}))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("文件名");
        assertThatThrownBy(() -> service.stage(7L, 20L, "../V1", "contract.pdf", new byte[] {1}))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("文档版本");
        assertThatThrownBy(() -> service.resolveAuthorizedFile("../outside.pdf"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("非法文件路径");
        assertThatThrownBy(() -> service.resolveAuthorizedFile(workDir.resolve("outside.pdf").toString()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("非法文件路径");
    }

    @Test
    @DisplayName("归档使用不可覆盖的原子移动")
    void shouldPromoteAtomicallyWithoutOverwrite() throws Exception
    {
        byte[] content = "immutable".getBytes(StandardCharsets.UTF_8);
        StagedSignFile staged = service.stage(7L, 20L, "SP-20-V1", "contract.pdf", content);

        StagedSignFile promoted = service.promote(staged);

        assertThat(promoted.getArchivePath()).isRegularFile().startsWith(root);
        assertThat(promoted.getTempPath()).doesNotExist();
        assertThat(Files.readAllBytes(promoted.getArchivePath())).isEqualTo(content);
        assertThat(promoted.getPublicUrl()).startsWith("/profile/private/sign-package/");
        assertThat(service.resolveAuthorizedFile(promoted.getArchiveRelativePath()))
                .isEqualTo(promoted.getArchivePath());

        Files.createDirectories(staged.getTempPath().getParent());
        Files.write(staged.getTempPath(), "replacement".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> service.promote(staged))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不可覆盖");
        assertThat(Files.readAllBytes(promoted.getArchivePath())).isEqualTo(content);
    }

    @Test
    @DisplayName("数据库保存的私有公开地址只能解析到受控归档根目录")
    void shouldResolveManagedPublicUrl() throws Exception
    {
        StagedSignFile promoted = service.promote(service.stage(7L, 20L, "SP-20-V1",
                "contract.pdf", "managed".getBytes(StandardCharsets.UTF_8)));

        assertThat(service.isManagedPublicUrl(promoted.getPublicUrl())).isTrue();
        assertThat(service.resolveAuthorizedPublicUrl(promoted.getPublicUrl()))
                .isEqualTo(promoted.getArchivePath());
        assertThatThrownBy(() -> service.resolveAuthorizedPublicUrl(
                "/profile/private/sign-package/../../outside.pdf"))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("事务未提交时只允许按原始hash清理本次归档文件")
    void shouldDiscardOnlyMatchingUncommittedArchive() throws Exception
    {
        StagedSignFile keep = service.promote(service.stage(7L, 20L, "SP-20-V1",
                "keep.pdf", "keep".getBytes(StandardCharsets.UTF_8)));
        StagedSignFile discard = service.promote(service.stage(7L, 20L, "SP-20-V1",
                "discard.pdf", "discard".getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> service.discardUncommitted(keep.getArchiveRelativePath(), "wrong-hash"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("校验值不一致");
        assertThat(keep.getArchivePath()).exists();

        service.discardUncommitted(discard.getArchiveRelativePath(), discard.getFileHash());
        assertThat(discard.getArchivePath()).doesNotExist();
        assertThat(keep.getArchivePath()).exists();
    }

    @Test
    @DisplayName("历史证据的受控公开地址和归档相对路径都可按hash安全删除")
    void shouldDiscardArchivedEvidenceFromManagedPublicUrlOrRelativePath() throws Exception
    {
        StagedSignFile publicReference = service.promote(service.stage(7L, 20L, "SP-20-V1",
                "public.pdf", "public".getBytes(StandardCharsets.UTF_8)));
        StagedSignFile relativeReference = service.promote(service.stage(7L, 20L, "SP-20-V1",
                "relative.pdf", "relative".getBytes(StandardCharsets.UTF_8)));

        service.discardArchivedEvidence(publicReference.getPublicUrl(), publicReference.getFileHash());
        service.discardArchivedEvidence(relativeReference.getArchiveRelativePath(), relativeReference.getFileHash());

        assertThat(publicReference.getArchivePath()).doesNotExist();
        assertThat(relativeReference.getArchivePath()).doesNotExist();
        assertThatThrownBy(() -> service.discardArchivedEvidence("/etc/passwd", "hash"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("非法文件路径");
    }

    @Test
    @DisplayName("管理员删除任务只能清理精确签约包目录")
    void shouldDeleteOnlyExactManagedTaskPackageDirectories() throws Exception
    {
        StagedSignFile target = service.promote(service.stage(7L, 20L, "SP-20-V1",
                "target.pdf", "target".getBytes(StandardCharsets.UTF_8)));
        StagedSignFile keep = service.promote(service.stage(8L, 21L, "SP-21-V1",
                "keep.pdf", "keep".getBytes(StandardCharsets.UTF_8)));

        service.deleteManagedPackageFiles(7L, 20L, List.of(target.getPublicUrl()));

        assertThat(target.getArchivePath()).doesNotExist();
        assertThat(keep.getArchivePath()).exists();
        assertThatThrownBy(() -> service.validateManagedPackageReferences(
                7L, 20L, List.of(keep.getPublicUrl())))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("与目标任务不一致");
    }

    @Test
    @DisplayName("失败清理只删除当前临时目录")
    void shouldCleanupOnlyCurrentStagingDirectory() throws Exception
    {
        StagedSignFile staged = service.stage(7L, 20L, "SP-20-V1", "contract.pdf", new byte[] {1, 2, 3});
        Path archiveSentinel = root.resolve("keep.txt");
        Path tempSentinel = temp.resolve("other-stage/keep.txt");
        Files.createDirectories(archiveSentinel.getParent());
        Files.createDirectories(tempSentinel.getParent());
        Files.writeString(archiveSentinel, "keep");
        Files.writeString(tempSentinel, "keep");

        service.cleanup(staged);

        assertThat(staged.getTempPath()).doesNotExist();
        assertThat(archiveSentinel).exists();
        assertThat(tempSentinel).exists();
    }

    @Test
    @DisplayName("OA配置提供私有归档、临时目录和PDF转换默认值")
    void shouldConfigurePrivateStorageAndPdfConversion() throws Exception
    {
        String yaml = new String(getClass().getClassLoader().getResourceAsStream("bootstrap.yml").readAllBytes(),
                StandardCharsets.UTF_8);

        assertThat(yaml)
                .contains("sign-package:")
                .contains("root-path: ${SIGN_PACKAGE_STORAGE_ROOT:${file.path:./uploadPath}/private/sign-package}")
                .contains("temp-path: ${SIGN_PACKAGE_TEMP_ROOT:${java.io.tmpdir}/erp-sign-package}")
                .contains("public-prefix: ${SIGN_PACKAGE_PUBLIC_PREFIX:/profile/private/sign-package}")
                .contains("converter-command: ${SIGN_PACKAGE_PDF_CONVERTER_COMMAND:libreoffice}")
                .contains("timeout-seconds: ${SIGN_PACKAGE_PDF_TIMEOUT_SECONDS:60}");
    }

    private String sha256(byte[] bytes) throws Exception
    {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
