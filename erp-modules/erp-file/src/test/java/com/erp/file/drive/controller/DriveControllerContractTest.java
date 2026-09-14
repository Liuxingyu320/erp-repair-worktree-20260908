package com.erp.file.drive.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.core.web.page.TableDataInfo;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.file.drive.config.DriveProperties;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.domain.DriveAuditContext;
import com.erp.file.drive.domain.DrivePurgeClaim;
import com.erp.file.drive.domain.dto.DriveFolderCreateRequest;
import com.erp.file.drive.domain.dto.DriveMoveRequest;
import com.erp.file.drive.domain.dto.DriveRenameRequest;
import com.erp.file.drive.domain.vo.DriveNodeVo;
import com.erp.file.drive.service.DriveActorResolver;
import com.erp.file.drive.service.DriveFeatureGuard;
import com.erp.file.drive.service.DriveNodeService;
import com.erp.file.drive.service.DriveTrashService;
import com.erp.file.drive.service.DriveUploadService;
import com.github.pagehelper.Page;
import com.github.pagehelper.page.PageMethod;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

@DisplayName("云盘节点控制器契约")
class DriveControllerContractTest
{
    @AfterEach
    void clearPagination()
    {
        PageMethod.clearPage();
    }

    @Test
    @DisplayName("浏览、详情、上传和生命周期接口都要求 drive:access")
    void shouldRequireDriveAccessOnEveryEndpoint() throws Exception
    {
        assertPermission(DriveNodeController.class.getMethod("list",
                Long.class, Long.class, String.class, String.class, String.class,
                Integer.class, Integer.class));
        assertPermission(DriveNodeController.class.getMethod("detail", Long.class));
        assertPermission(DriveNodeController.class.getMethod(
                "createFolder", DriveFolderCreateRequest.class));
        assertPermission(DriveNodeController.class.getMethod(
                "upload", MultipartFile.class, Long.class, Long.class, String.class));
        assertPermission(DriveNodeController.class.getMethod("uploadReceipt", String.class));
        assertPermission(DriveNodeController.class.getMethod(
                "rename", Long.class, DriveRenameRequest.class));
        assertPermission(DriveNodeController.class.getMethod(
                "move", Long.class, DriveMoveRequest.class));
        assertPermission(DriveNodeController.class.getMethod("recent", Integer.class));
        assertPermission(DriveNodeController.class.getMethod(
                "trash", Long.class, Integer.class));
        assertPermission(DriveNodeController.class.getMethod("listTrash", Long.class));
        assertPermission(DriveNodeController.class.getMethod("restore", Long.class));
        assertPermission(DriveNodeController.class.getMethod("purge", Long.class));
        assertPermission(DriveNodeController.class.getMethod("emptyTrash", Long.class));
    }

    @Test
    @DisplayName("页大小超过 100 被方法参数校验拒绝")
    void shouldRejectOversizedPage()
            throws Exception
    {
        DriveNodeController controller = controller(new DriveProperties(),
                mock(DriveActorResolver.class), mock(DriveNodeService.class),
                mock(DriveUploadService.class));
        Method method = DriveNodeController.class.getMethod("list",
                Long.class, Long.class, String.class, String.class, String.class,
                Integer.class, Integer.class);
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        Set<?> violations = validator.forExecutables().validateParameters(controller, method,
                new Object[] {4L, 0L, null, "updated", "desc", 1, 101});

        assertThat(violations).isNotEmpty();
    }

    @Test
    @DisplayName("列表映射后仍保留数据库分页总数并清理线程变量")
    void shouldPreserveTotalAndClearPageHelper()
    {
        DriveProperties properties = new DriveProperties();
        properties.setEnabled(true);
        DriveActorResolver resolver = mock(DriveActorResolver.class);
        DriveNodeService nodeService = mock(DriveNodeService.class);
        DriveUploadService uploadService = mock(DriveUploadService.class);
        DriveNodeController controller = controller(properties, resolver, nodeService, uploadService);
        DriveActor actor = actor();
        Page<DriveNodeVo> rows = new Page<>(2, 2);
        rows.setTotal(237L);
        rows.add(nodeVo(21L));
        rows.add(nodeVo(22L));
        when(resolver.resolve()).thenReturn(actor);
        when(nodeService.listPaged(4L, 0L, null, "updated", "desc", actor, 2, 2))
                .thenReturn(rows);

        TableDataInfo result = controller.list(4L, 0L, null, "updated", "desc", 2, 2);

        assertThat(result.getRows()).hasSize(2);
        assertThat(result.getTotal()).isEqualTo(237L);
        assertThat(PageMethod.getLocalPage()).isNull();
    }

    @Test
    @DisplayName("功能关闭时控制器在身份和服务前失败")
    void shouldFailBeforeActorAndServicesWhenDisabled()
    {
        DriveProperties properties = new DriveProperties();
        DriveActorResolver resolver = mock(DriveActorResolver.class);
        DriveNodeService nodeService = mock(DriveNodeService.class);
        DriveUploadService uploadService = mock(DriveUploadService.class);
        DriveNodeController controller = controller(properties, resolver, nodeService, uploadService);

        assertThatThrownBy(() -> controller.list(
                4L, 0L, null, "updated", "desc", 1, 50))
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_DISABLED);
        verifyNoInteractions(resolver, nodeService, uploadService);
    }

    @Test
    @DisplayName("空 multipart 文件在进入上传服务前被拒绝")
    void shouldRejectEmptyMultipartBeforeUploadService()
    {
        DriveProperties properties = new DriveProperties();
        properties.setEnabled(true);
        DriveActorResolver resolver = mock(DriveActorResolver.class);
        DriveNodeService nodeService = mock(DriveNodeService.class);
        DriveUploadService uploadService = mock(DriveUploadService.class);
        DriveNodeController controller = controller(properties, resolver, nodeService, uploadService);
        when(resolver.resolve()).thenReturn(actor());
        MockMultipartFile empty = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> controller.upload(empty, 4L, 0L))
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_FILE_TYPE_REJECTED);
        verifyNoInteractions(uploadService);
    }

    @Test
    @DisplayName("最近使用始终包装在 AjaxResult.data 中")
    void shouldWrapRecentNodesInData()
    {
        DriveProperties properties = new DriveProperties();
        properties.setEnabled(true);
        DriveActorResolver resolver = mock(DriveActorResolver.class);
        DriveNodeService nodeService = mock(DriveNodeService.class);
        DriveUploadService uploadService = mock(DriveUploadService.class);
        DriveNodeController controller = controller(properties, resolver, nodeService, uploadService);
        DriveActor actor = actor();
        List<DriveNodeVo> recent = List.of(nodeVo(22L));
        when(resolver.resolve()).thenReturn(actor);
        when(nodeService.recent(actor, 20)).thenReturn(recent);

        AjaxResult result = controller.recent(20);

        assertThat(result.get(AjaxResult.DATA_TAG)).isEqualTo(recent);
    }

    @Test
    @DisplayName("软删除版本参数必填且不能为负数")
    void shouldValidateTrashVersion() throws Exception
    {
        DriveNodeController controller = controller(new DriveProperties(),
                mock(DriveActorResolver.class), mock(DriveNodeService.class),
                mock(DriveUploadService.class));
        Method method = DriveNodeController.class.getMethod(
                "trash", Long.class, Integer.class);
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        assertThat(validator.forExecutables().validateParameters(
                controller, method, new Object[] {8L, null})).isNotEmpty();
        assertThat(validator.forExecutables().validateParameters(
                controller, method, new Object[] {8L, -1})).isNotEmpty();
    }

    @Test
    @DisplayName("彻底清理只返回安全的 202 受理响应")
    void shouldReturnAcceptedWithoutExposingPurgeClaim()
    {
        DriveProperties properties = new DriveProperties();
        properties.setEnabled(true);
        DriveActorResolver resolver = mock(DriveActorResolver.class);
        DriveNodeService nodeService = mock(DriveNodeService.class);
        DriveUploadService uploadService = mock(DriveUploadService.class);
        DriveTrashService trashService = mock(DriveTrashService.class);
        DriveNodeController controller = controller(
                properties, resolver, nodeService, uploadService, trashService);
        DriveActor actor = actor();
        DriveAuditContext context = new DriveAuditContext(
                20L, 8L, "alice", "request-1", "127.0.0.1", "test");
        DrivePurgeClaim claim = new DrivePurgeClaim(8L, 4L, 6, 2, 12L,
                List.of("2026/07/private.pdf"), "月报", context,
                DriveConstants.ACTION_PURGE);
        when(resolver.resolve()).thenReturn(actor);
        when(trashService.requestPurge(8L, actor)).thenReturn(claim);

        ResponseEntity<AjaxResult> response = controller.purge(8L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().toString())
                .contains("清理请求已受理")
                .doesNotContain("2026/07/private.pdf", "storageKeys", "DrivePurgeClaim");
    }

    private static DriveNodeController controller(DriveProperties properties,
            DriveActorResolver resolver, DriveNodeService nodes, DriveUploadService uploads)
    {
        return controller(properties, resolver, nodes, uploads, mock(DriveTrashService.class));
    }

    private static DriveNodeController controller(DriveProperties properties,
            DriveActorResolver resolver, DriveNodeService nodes, DriveUploadService uploads,
            DriveTrashService trashService)
    {
        return new DriveNodeController(
                new DriveFeatureGuard(properties), resolver, nodes, uploads, trashService);
    }

    private static void assertPermission(Method method)
    {
        assertThat(method.getAnnotation(RequiresPermissions.class).value())
                .containsExactly(DriveConstants.PERMISSION_ACCESS);
    }

    private static DriveActor actor()
    {
        return new DriveActor(20L, 8L, "财务部", "alice",
                Set.of(DriveConstants.PERMISSION_ACCESS), false);
    }

    private static DriveNodeVo nodeVo(Long id)
    {
        return new DriveNodeVo(id, 4L, "我的文件", 0L,
                DriveConstants.NODE_FILE, "报告.pdf", "pdf", "application/pdf",
                12L, DriveConstants.STATUS_ACTIVE, 0, "alice", null, "alice", null,
                "/报告.pdf", List.of(), List.of(), true, true, true);
    }
}
