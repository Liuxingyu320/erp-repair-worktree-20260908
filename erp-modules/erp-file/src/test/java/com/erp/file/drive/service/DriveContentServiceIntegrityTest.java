package com.erp.file.drive.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import com.erp.file.drive.config.DriveProperties;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.domain.DriveNode;
import com.erp.file.drive.domain.DriveSpace;
import com.erp.file.drive.exception.DriveException;
import com.erp.file.drive.mapper.DriveNodeMapper;
import com.erp.file.drive.storage.DriveStorageProvider;
import com.erp.file.drive.storage.DriveStoredObject;
import com.erp.system.api.domain.DriveBusinessFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.InputStreamResource;

@DisplayName("云盘业务绑定内容完整性")
class DriveContentServiceIntegrityTest
{
    private DriveNodeMapper nodeMapper;
    private DriveSpaceService spaceService;
    private DriveStorageProvider storage;
    private DriveContentService service;
    private DriveActor actor;

    @BeforeEach
    void setUp()
    {
        nodeMapper = mock(DriveNodeMapper.class);
        spaceService = mock(DriveSpaceService.class);
        storage = mock(DriveStorageProvider.class);
        service = new DriveContentService(nodeMapper, spaceService,
                new DriveAuthorizationService(),
                new DriveFilePolicy(new DriveProperties()), storage,
                mock(DriveOperationLogService.class));
        actor = new DriveActor(20L, 8L, "财务部", "alice",
                Set.of(DriveConstants.PERMISSION_ACCESS), false);
    }

    @Test
    @DisplayName("已有 SHA-256 与物理对象内容一致时允许绑定并关闭资源")
    void shouldAcceptMatchingDigestAndCloseResource() throws Exception
    {
        byte[] content = bytes("pdf-content!");
        DriveNode node = arrangeNode(content.length, sha256(content));
        AtomicBoolean closed = new AtomicBoolean();
        when(storage.open(node.getStorageKey())).thenReturn(
                trackedStoredObject(content, closed));

        DriveBusinessFile result = service.validateBusinessBinding(22L, actor);

        assertThat(result.getNodeId()).isEqualTo(22L);
        assertThat(closed).isTrue();
    }

    @Test
    @DisplayName("同尺寸内容被篡改时以客户端安全错误拒绝并关闭资源")
    void shouldRejectSameSizeTamperingAndCloseResource() throws Exception
    {
        byte[] expected = bytes("pdf-content!");
        byte[] tampered = bytes("pdf-contEnt!");
        assertThat(tampered).hasSameSizeAs(expected);
        DriveNode node = arrangeNode(expected.length, sha256(expected));
        AtomicBoolean closed = new AtomicBoolean();
        when(storage.open(node.getStorageKey())).thenReturn(
                trackedStoredObject(tampered, closed));

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.validateBusinessBinding(22L, actor));

        assertSafeStorageError(thrown);
        assertThat(closed).isTrue();
    }

    @Test
    @DisplayName("校验流读取失败时 fail closed 且不泄露存储细节")
    void shouldFailClosedAndCloseResourceWhenDigestReadFails() throws Exception
    {
        byte[] expected = bytes("pdf-content!");
        DriveNode node = arrangeNode(expected.length, sha256(expected));
        AtomicBoolean closed = new AtomicBoolean();
        InputStream failing = new InputStream()
        {
            @Override
            public int read() throws IOException
            {
                throw new IOException("read failed for private/object.pdf");
            }

            @Override
            public int read(byte[] buffer, int offset, int length) throws IOException
            {
                throw new IOException("read failed for private/object.pdf");
            }

            @Override
            public void close()
            {
                closed.set(true);
            }
        };
        when(storage.open(node.getStorageKey())).thenReturn(new DriveStoredObject(
                new InputStreamResource(failing), expected.length));

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.validateBusinessBinding(22L, actor));

        assertSafeStorageError(thrown);
        assertThat(thrown.getMessage()).doesNotContain("private/object.pdf", "read failed");
        assertThat(closed).isTrue();
    }

    @Test
    @DisplayName("历史无摘要对象保持尺寸校验且不读取内容")
    void shouldKeepLegacyObjectsCompatibleWithoutReadingContent() throws Exception
    {
        DriveNode node = arrangeNode(12L, null);
        AtomicInteger opens = new AtomicInteger();
        when(storage.open(node.getStorageKey())).thenReturn(new DriveStoredObject(
                unopenedResource(opens), 12L));

        DriveBusinessFile result = service.validateBusinessBinding(22L, actor);

        assertThat(result.getNodeId()).isEqualTo(22L);
        assertThat(opens).hasValue(0);
    }

    @Test
    @DisplayName("普通下载即使有摘要也不为校验预读物理内容")
    void shouldNotPreReadDigestDuringOrdinaryDownload() throws Exception
    {
        byte[] content = bytes("pdf-content!");
        DriveNode node = arrangeNode(content.length, sha256(content));
        AtomicInteger opens = new AtomicInteger();
        when(storage.open(node.getStorageKey())).thenReturn(new DriveStoredObject(
                unopenedResource(opens), content.length));

        service.resolve(22L, "download", actor);

        assertThat(opens).hasValue(0);
    }

    private DriveNode arrangeNode(long size, String digest)
    {
        DriveNode node = new DriveNode();
        node.setNodeId(22L);
        node.setSpaceId(4L);
        node.setNodeType(DriveConstants.NODE_FILE);
        node.setNodeName("员工手册.pdf");
        node.setExtension("pdf");
        node.setContentType("application/pdf");
        node.setStorageKey("2026/07/private-object.pdf");
        node.setSizeBytes(size);
        node.setSha256(digest);
        node.setStatus(DriveConstants.STATUS_ACTIVE);
        node.setActiveFlag(1);
        when(nodeMapper.selectById(22L)).thenReturn(node);

        DriveSpace space = new DriveSpace();
        space.setSpaceId(4L);
        space.setSpaceType(DriveConstants.SPACE_PERSONAL);
        space.setOwnerUserId(20L);
        space.setStatus(DriveConstants.STATUS_ACTIVE);
        when(spaceService.requireSpace(4L)).thenReturn(space);
        return node;
    }

    private static DriveStoredObject trackedStoredObject(byte[] content,
            AtomicBoolean closed)
    {
        ByteArrayInputStream input = new ByteArrayInputStream(content)
        {
            @Override
            public void close() throws IOException
            {
                closed.set(true);
                super.close();
            }
        };
        return new DriveStoredObject(new InputStreamResource(input), content.length);
    }

    private static AbstractResource unopenedResource(AtomicInteger opens)
    {
        return new AbstractResource()
        {
            @Override
            public String getDescription()
            {
                return "single-pass integrity test resource";
            }

            @Override
            public InputStream getInputStream()
            {
                opens.incrementAndGet();
                throw new AssertionError("content must remain unread");
            }
        };
    }

    private static void assertSafeStorageError(Throwable thrown)
    {
        assertThat(thrown).isInstanceOf(DriveException.class)
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE);
        assertThat(thrown.getMessage()).isEqualTo("文件内容暂时不可用，请稍后重试");
    }

    private static byte[] bytes(String value)
    {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String sha256(byte[] value) throws Exception
    {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value));
    }
}
