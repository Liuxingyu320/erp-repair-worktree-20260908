package com.erp.file.drive.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import com.erp.file.drive.config.DriveProperties;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.domain.DriveNode;
import com.erp.file.drive.domain.DriveSpace;
import com.erp.file.drive.domain.vo.DriveContent;
import com.erp.file.drive.exception.DriveException;
import com.erp.file.drive.mapper.DriveNodeMapper;
import com.erp.file.drive.storage.DriveStorageProvider;
import com.erp.file.drive.storage.DriveStoredObject;
import com.erp.system.api.domain.DriveBusinessFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;

@DisplayName("云盘受控内容读取")
class DriveContentServiceTest
{
    private DriveNodeMapper nodeMapper;
    private DriveSpaceService spaceService;
    private DriveStorageProvider storage;
    private DriveOperationLogService logService;
    private DriveContentService service;
    private DriveActor actor;

    @BeforeEach
    void setUp()
    {
        nodeMapper = mock(DriveNodeMapper.class);
        spaceService = mock(DriveSpaceService.class);
        storage = mock(DriveStorageProvider.class);
        logService = mock(DriveOperationLogService.class);
        service = new DriveContentService(nodeMapper, spaceService,
                new DriveAuthorizationService(), new DriveFilePolicy(new DriveProperties()),
                storage, logService);
        actor = new DriveActor(20L, 8L, "财务部", "alice",
                Set.of(DriveConstants.PERMISSION_ACCESS), false);
    }

    @Test
    @DisplayName("未知节点在空间和存储访问前返回稳定错误")
    void shouldRejectUnknownNodeBeforeStorage()
    {
        when(nodeMapper.selectById(22L)).thenReturn(null);

        assertCode(() -> service.resolve(22L, "download", actor),
                DriveErrorCodes.DRIVE_NODE_NOT_FOUND);

        verifyNoInteractions(spaceService, storage);
        verify(logService).failure(DriveConstants.ACTION_DOWNLOAD, actor,
                DriveErrorCodes.DRIVE_NODE_NOT_FOUND);
    }

    @Test
    @DisplayName("回收站节点不读取物理对象")
    void shouldRejectTrashedNodeBeforeStorage()
    {
        DriveNode node = pdfNode();
        node.setStatus(DriveConstants.STATUS_TRASHED);
        arrange(node, personalSpace(20L));

        assertCode(() -> service.resolve(22L, "download", actor),
                DriveErrorCodes.DRIVE_NODE_NOT_FOUND);

        verifyNoInteractions(storage);
    }

    @Test
    @DisplayName("文件夹不进入内容存储")
    void shouldRejectFolderBeforeStorage()
    {
        DriveNode node = pdfNode();
        node.setNodeType(DriveConstants.NODE_FOLDER);
        node.setStorageKey(null);
        arrange(node, personalSpace(20L));

        assertCode(() -> service.resolve(22L, "download", actor),
                DriveErrorCodes.DRIVE_NODE_NOT_FOUND);

        verifyNoInteractions(storage);
    }

    @Test
    @DisplayName("个人盘外部用户在对象读取前被拒绝")
    void shouldAuthorizePersonalSpaceBeforeStorage()
    {
        DriveNode node = pdfNode();
        arrange(node, personalSpace(99L));

        assertCode(() -> service.resolve(22L, "preview", actor),
                DriveErrorCodes.DRIVE_ACCESS_DENIED);

        verifyNoInteractions(storage);
        verify(logService).failure(eq(DriveConstants.ACTION_PREVIEW), eq(actor),
                eq(node), eq(DriveErrorCodes.DRIVE_ACCESS_DENIED), any(), any());
    }

    @Test
    @DisplayName("业务绑定依然校验当前用户云盘读权限")
    void shouldAuthorizeAndVerifyStorageBeforeBusinessBinding()
            throws Exception
    {
        DriveNode node = pdfNode();
        arrange(node, personalSpace(20L));
        when(storage.open(node.getStorageKey()))
                .thenReturn(stored("pdf-content!", 12L));

        DriveBusinessFile file = service.validateBusinessBinding(22L,
                actor);

        assertThat(file.getNodeId()).isEqualTo(22L);
        assertThat(file.getFileName()).isEqualTo("员工手册.pdf");
        assertThat(file.getContentType()).isEqualTo("application/pdf");
        assertThat(file.getSize()).isEqualTo(12L);
    }

    @Test
    @DisplayName("业务授权后的内部读取不再依赖原云盘空间成员关系")
    void shouldResolveBusinessContentAfterOwningServiceAuthorization()
            throws Exception
    {
        DriveNode node = pdfNode();
        arrange(node, personalSpace(99L));
        when(storage.open(node.getStorageKey()))
                .thenReturn(stored("pdf-content!", 12L));

        DriveContent content = service.resolveBusiness(22L, "preview",
                actor);

        assertThat(content.inline()).isTrue();
        verify(logService).success(DriveConstants.ACTION_PREVIEW, actor,
                node, null, "员工手册.pdf");
    }

    @Test
    @DisplayName("同部门用户可以预览 PDF 且仅在尺寸校验后记录成功")
    void shouldOpenDepartmentPdfInline() throws Exception
    {
        DriveNode node = pdfNode();
        arrange(node, departmentSpace(8L));
        when(storage.open("2026/07/private-object.pdf"))
                .thenReturn(stored("pdf-content!", 12L));

        DriveContent content = service.resolve(22L, "preview", actor);

        assertThat(content.inline()).isTrue();
        assertThat(content.fileName()).isEqualTo("员工手册.pdf");
        assertThat(content.contentType()).isEqualTo("application/pdf");
        assertThat(content.size()).isEqualTo(12L);
        verify(logService).success(DriveConstants.ACTION_PREVIEW, actor,
                node, null, "员工手册.pdf");
    }

    @Test
    @DisplayName("缺失对象映射为对象不存在且消息不泄露存储键")
    void shouldTranslateMissingObject() throws Exception
    {
        DriveNode node = pdfNode();
        arrange(node, personalSpace(20L));
        when(storage.open(node.getStorageKey()))
                .thenThrow(new NoSuchFileException(node.getStorageKey()));

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.resolve(22L, "download", actor));

        assertThat(thrown).isInstanceOf(DriveException.class)
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_STORAGE_OBJECT_MISSING);
        assertThat(thrown.getMessage()).doesNotContain(node.getStorageKey());
        verify(logService).failure(DriveConstants.ACTION_DOWNLOAD, actor, node,
                DriveErrorCodes.DRIVE_STORAGE_OBJECT_MISSING, null, null);
    }

    @Test
    @DisplayName("存储异常映射为服务不可用且不暴露客户端异常")
    void shouldTranslateStorageFailure() throws Exception
    {
        DriveNode node = pdfNode();
        arrange(node, personalSpace(20L));
        when(storage.open(node.getStorageKey()))
                .thenThrow(new IOException("failed for " + node.getStorageKey()));

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.resolve(22L, "download", actor));

        assertThat(thrown).isInstanceOf(DriveException.class)
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE);
        assertThat(thrown.getMessage()).doesNotContain(node.getStorageKey(), "failed for");
    }

    @Test
    @DisplayName("元数据读取异常也不能把 SQL 或存储字段暴露给客户端")
    void shouldTranslateUnexpectedMetadataFailure()
    {
        when(nodeMapper.selectById(22L)).thenThrow(new IllegalStateException(
                "select storage_key from drive_node: private/object.pdf"));

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.resolve(22L, "download", actor));

        assertThat(thrown).isInstanceOf(DriveException.class)
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE);
        assertThat(thrown.getMessage()).doesNotContain(
                "select", "storage_key", "private/object.pdf");
        verify(logService).failure(DriveConstants.ACTION_DOWNLOAD, actor,
                DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE);
    }

    @Test
    @DisplayName("物理对象尺寸与元数据不一致时拒绝流式返回")
    void shouldRejectStorageSizeMismatch() throws Exception
    {
        DriveNode node = pdfNode();
        arrange(node, personalSpace(20L));
        AtomicBoolean closed = new AtomicBoolean();
        ByteArrayInputStream stream = new ByteArrayInputStream(
                "short".getBytes(StandardCharsets.UTF_8))
        {
            @Override
            public void close() throws IOException
            {
                closed.set(true);
                super.close();
            }
        };
        when(storage.open(node.getStorageKey())).thenReturn(new DriveStoredObject(
                new InputStreamResource(stream), 5L));

        assertCode(() -> service.resolve(22L, "download", actor),
                DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE);

        assertThat(closed).isTrue();
        verify(logService, never()).success(any(), any(DriveActor.class),
                any(), any(), any());
        verify(logService).failure(DriveConstants.ACTION_DOWNLOAD, actor, node,
                DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE, null, null);
    }

    @Test
    @DisplayName("DOCX 不允许预览且在访问存储前失败")
    void shouldRejectUnsupportedPreviewBeforeStorage()
    {
        DriveNode node = docxNode();
        arrange(node, personalSpace(20L));

        assertCode(() -> service.resolve(22L, "preview", actor),
                DriveErrorCodes.DRIVE_PREVIEW_UNSUPPORTED);

        verifyNoInteractions(storage);
    }

    @Test
    @DisplayName("DOCX 可作为附件下载")
    void shouldDownloadDocxAsAttachment() throws Exception
    {
        DriveNode node = docxNode();
        arrange(node, personalSpace(20L));
        when(storage.open(node.getStorageKey())).thenReturn(stored("docx-content", 12L));

        DriveContent content = service.resolve(22L, "download", actor);

        assertThat(content.inline()).isFalse();
        assertThat(content.fileName()).isEqualTo("员工手册.docx");
        verify(logService).success(DriveConstants.ACTION_DOWNLOAD, actor,
                node, null, "员工手册.docx");
    }

    @Test
    @DisplayName("服务层也拒绝非精确内容模式并避免存储访问")
    void shouldRejectInvalidModeDefensively()
    {
        arrange(pdfNode(), personalSpace(20L));

        assertCode(() -> service.resolve(22L, "Preview", actor),
                DriveErrorCodes.DRIVE_PREVIEW_UNSUPPORTED);

        verifyNoInteractions(storage);
    }

    private void arrange(DriveNode node, DriveSpace space)
    {
        when(nodeMapper.selectById(22L)).thenReturn(node);
        when(spaceService.requireSpace(node.getSpaceId())).thenReturn(space);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
            String code)
    {
        assertThatThrownBy(callable).isInstanceOf(DriveException.class)
                .extracting("businessCode").isEqualTo(code);
    }

    private static DriveStoredObject stored(String value, long size)
    {
        return new DriveStoredObject(
                new ByteArrayResource(value.getBytes(StandardCharsets.UTF_8)), size);
    }

    private static DriveNode pdfNode()
    {
        return fileNode("员工手册.pdf", "pdf", "application/pdf");
    }

    private static DriveNode docxNode()
    {
        return fileNode("员工手册.docx", "docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    private static DriveNode fileNode(String name, String extension, String contentType)
    {
        DriveNode node = new DriveNode();
        node.setNodeId(22L);
        node.setSpaceId(4L);
        node.setParentId(0L);
        node.setNodeType(DriveConstants.NODE_FILE);
        node.setNodeName(name);
        node.setExtension(extension);
        node.setContentType(contentType);
        node.setStorageKey("2026/07/private-object.pdf");
        node.setSizeBytes(12L);
        node.setStatus(DriveConstants.STATUS_ACTIVE);
        node.setActiveFlag(1);
        return node;
    }

    private static DriveSpace personalSpace(Long ownerId)
    {
        DriveSpace space = activeSpace(DriveConstants.SPACE_PERSONAL);
        space.setOwnerUserId(ownerId);
        return space;
    }

    private static DriveSpace departmentSpace(Long deptId)
    {
        DriveSpace space = activeSpace(DriveConstants.SPACE_DEPARTMENT);
        space.setDeptId(deptId);
        return space;
    }

    private static DriveSpace activeSpace(String type)
    {
        DriveSpace space = new DriveSpace();
        space.setSpaceId(4L);
        space.setSpaceType(type);
        space.setStatus(DriveConstants.STATUS_ACTIVE);
        return space;
    }
}
