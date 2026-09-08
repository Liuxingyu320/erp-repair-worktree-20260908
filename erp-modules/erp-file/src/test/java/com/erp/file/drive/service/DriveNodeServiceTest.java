package com.erp.file.drive.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;
import java.util.Set;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.domain.DriveNode;
import com.erp.file.drive.domain.DriveSpace;
import com.erp.file.drive.domain.dto.DriveFolderCreateRequest;
import com.erp.file.drive.domain.dto.DriveMoveRequest;
import com.erp.file.drive.domain.dto.DriveRenameRequest;
import com.erp.file.drive.domain.vo.DriveNodeVo;
import com.erp.file.drive.exception.DriveException;
import com.erp.file.drive.mapper.DriveNodeMapper;
import com.erp.file.drive.mapper.DriveOperationLogMapper;
import com.erp.file.drive.mapper.DriveSpaceMapper;
import com.github.pagehelper.Page;
import com.github.pagehelper.page.PageMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DuplicateKeyException;

@DisplayName("云盘节点浏览与文件夹")
class DriveNodeServiceTest
{
    private DriveNodeMapper nodeMapper;
    private DriveSpaceMapper spaceMapper;
    private DriveOperationLogMapper operationLogMapper;
    private DriveSpaceService spaceService;
    private DriveOperationLogService logService;
    private DriveNodeService service;
    private DriveActor actor;
    private DriveSpace space;

    @BeforeEach
    void setUp()
    {
        nodeMapper = mock(DriveNodeMapper.class);
        spaceMapper = mock(DriveSpaceMapper.class);
        operationLogMapper = mock(DriveOperationLogMapper.class);
        spaceService = mock(DriveSpaceService.class);
        logService = mock(DriveOperationLogService.class);
        service = new DriveNodeService(nodeMapper, spaceMapper, spaceService,
                new DriveAuthorizationService(), new DriveNamePolicy(),
                new DriveFilePolicy(new com.erp.file.drive.config.DriveProperties()),
                logService, operationLogMapper);
        actor = new DriveActor(20L, 8L, "财务部", "alice",
                Set.of(DriveConstants.PERMISSION_ACCESS), false);
        space = activeSpace(4L, 20L);
        when(spaceService.canWriteSpace(space, actor)).thenReturn(true);
    }

    @Test
    @DisplayName("空间不可读时不执行节点查询")
    void shouldRequireReadableSpaceBeforeListing()
    {
        doThrow(new DriveException(DriveErrorCodes.DRIVE_ACCESS_DENIED, "denied"))
                .when(spaceService).requireReadableSpace(4L, actor);

        assertThatThrownBy(() -> service.list(4L, 0L, "", "name", "asc", actor))
                .isInstanceOf(DriveException.class);
        verifyNoInteractions(nodeMapper);
    }

    @Test
    @DisplayName("空关键词只列当前目录且排序参数被收敛")
    void shouldListDirectChildrenForBlankKeyword()
    {
        when(spaceService.requireReadableSpace(4L, actor)).thenReturn(space);
        DriveNode node = file(22L, 4L, 8L, "预算.pdf");
        node.setAncestors("0,8");
        DriveNode parent = folder(8L, 4L, 0L, "报表");
        when(nodeMapper.selectActiveById(8L)).thenReturn(parent);
        when(nodeMapper.selectActiveChildren(4L, 8L, "updated", "desc"))
                .thenReturn(List.of(node));
        when(nodeMapper.selectPathNodes(4L, List.of(8L))).thenReturn(List.of(parent));

        List<DriveNodeVo> result = service.list(4L, 8L, "   ", " unsafe ", "sideways", actor);

        assertThat(result).singleElement().satisfies(vo -> {
            assertThat(vo.logicalPath()).isEqualTo("/报表/预算.pdf");
            assertThat(vo.breadcrumbs()).extracting("nodeName").containsExactly("报表");
        });
        verify(nodeMapper).selectActiveChildren(4L, 8L, "updated", "desc");
        verify(nodeMapper, never()).selectActiveSearch(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("非空关键词忽略当前目录并搜索整个所选空间")
    void shouldSearchWholeSpaceForNonblankKeyword()
    {
        when(spaceService.requireReadableSpace(4L, actor)).thenReturn(space);
        when(nodeMapper.selectActiveSearch(4L, "预算!%", "name", "asc"))
                .thenReturn(List.of());

        service.list(4L, 999L, " 预算% ", " NAME ", " ASC ", actor);

        verify(nodeMapper).selectActiveSearch(4L, "预算!%", "name", "asc");
        verify(nodeMapper, never()).selectActiveById(999L);
        verify(nodeMapper, never()).selectActiveChildren(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("映射分页节点时保留原始总数")
    void shouldPreservePageMetadataWhenMappingNodes()
    {
        when(spaceService.requireReadableSpace(4L, actor)).thenReturn(space);
        Page<DriveNode> page = new Page<>(3, 2);
        page.setTotal(237L);
        page.add(file(22L, 4L, 0L, "预算.pdf"));
        when(nodeMapper.selectActiveChildren(4L, 0L, "updated", "desc")).thenReturn(page);

        List<DriveNodeVo> result = service.list(4L, 0L, null, null, null, actor);

        assertThat(result).isInstanceOf(Page.class);
        assertThat(((Page<?>) result).getTotal()).isEqualTo(237L);
        assertThat(((Page<?>) result).getPageNum()).isEqualTo(3);
    }

    @Test
    @DisplayName("分页在空间和目录校验后开启并始终清理线程变量")
    void shouldPageOnlyTheNodeQueryAndClearThreadState()
    {
        when(spaceService.requireReadableSpace(4L, actor)).thenReturn(space);
        DriveNode parent = folder(8L, 4L, 0L, "报表");
        when(nodeMapper.selectActiveById(8L)).thenReturn(parent);
        Page<DriveNode> page = new Page<>(2, 2);
        page.setTotal(237L);
        page.add(file(22L, 4L, 8L, "预算.pdf"));
        when(nodeMapper.selectActiveChildren(4L, 8L, "updated", "desc")).thenReturn(page);

        List<DriveNodeVo> result = service.listPaged(
                4L, 8L, null, "updated", "desc", actor, 2, 2);

        assertThat(((Page<?>) result).getTotal()).isEqualTo(237L);
        assertThat(PageMethod.getLocalPage()).isNull();
    }

    @Test
    @DisplayName("根目录建文件夹锁空间并写入稳定根路径")
    void shouldCreateFolderAtVirtualRootUnderSpaceLock()
    {
        DriveActor manager = writableActor();
        when(spaceService.requireWritableSpace(4L, manager)).thenReturn(space);
        when(spaceMapper.selectByIdForUpdate(4L)).thenReturn(space);
        when(nodeMapper.existsActiveName(4L, 0L, "月报", null)).thenReturn(0);
        when(nodeMapper.insertNode(any())).thenAnswer(invocation -> {
            DriveNode node = invocation.getArgument(0);
            node.setNodeId(88L);
            return 1;
        });
        when(spaceService.requireSpace(4L)).thenReturn(space);
        DriveFolderCreateRequest request = folderRequest(4L, 0L, "  月报  ");

        DriveNodeVo result = service.createFolder(request, manager);

        assertThat(result.nodeId()).isEqualTo(88L);
        ArgumentCaptor<DriveNode> inserted = ArgumentCaptor.forClass(DriveNode.class);
        verify(nodeMapper).insertNode(inserted.capture());
        assertThat(inserted.getValue().getAncestors()).isEqualTo("0");
        assertThat(inserted.getValue().getNormalizedName()).isEqualTo("月报");
        verify(spaceMapper).selectByIdForUpdate(4L);
        verify(logService).success(DriveConstants.ACTION_CREATE_FOLDER, manager,
                inserted.getValue(), null, "月报");
    }

    @Test
    @DisplayName("非根目录建文件夹从锁定后的父节点重建祖先路径")
    void shouldDeriveFolderPathFromLockedParent()
    {
        DriveActor manager = writableActor();
        when(spaceService.requireWritableSpace(4L, manager)).thenReturn(space);
        DriveNode movedParent = folder(8L, 4L, 6L, "当前目录");
        movedParent.setAncestors("0,6");
        when(nodeMapper.selectByIdForUpdate(8L)).thenReturn(movedParent);
        when(nodeMapper.existsActiveName(4L, 8L, "新目录", null)).thenReturn(0);
        when(nodeMapper.insertNode(any())).thenAnswer(invocation -> {
            DriveNode node = invocation.getArgument(0);
            node.setNodeId(89L);
            return 1;
        });
        when(spaceService.requireSpace(4L)).thenReturn(space);

        service.createFolder(folderRequest(4L, 8L, "新目录"), manager);

        ArgumentCaptor<DriveNode> inserted = ArgumentCaptor.forClass(DriveNode.class);
        verify(nodeMapper).insertNode(inserted.capture());
        assertThat(inserted.getValue().getAncestors()).isEqualTo("0,6,8");
    }

    @Test
    @DisplayName("同名文件夹在写入前返回名称冲突")
    void shouldRejectDuplicateFolderName()
    {
        DriveActor manager = writableActor();
        when(spaceService.requireWritableSpace(4L, manager)).thenReturn(space);
        when(spaceMapper.selectByIdForUpdate(4L)).thenReturn(space);
        when(nodeMapper.existsActiveName(4L, 0L, "月报", null)).thenReturn(1);

        assertThatThrownBy(() -> service.createFolder(folderRequest(4L, 0L, "月报"), manager))
                .isInstanceOf(DriveException.class)
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_NAME_CONFLICT);
        verify(nodeMapper, never()).insertNode(any());
        verify(logService).failure(DriveConstants.ACTION_CREATE_FOLDER, manager,
                DriveErrorCodes.DRIVE_NAME_CONFLICT);
    }

    @Test
    @DisplayName("文件重命名执行 NFKC 规范化、保留扩展名并使用请求版本")
    void shouldRenameFileWithNormalizedNameAndVersion()
    {
        DriveNode node = file(22L, 4L, 0L, "旧预算.pdf");
        node.setVersion(3);
        when(nodeMapper.selectByIdForUpdate(22L)).thenReturn(node);
        when(spaceService.requireWritableSpace(4L, actor)).thenReturn(space);
        when(nodeMapper.existsActiveName(4L, 0L, "新预算.pdf", 22L)).thenReturn(0);
        when(nodeMapper.updateName(22L, "新预算.PDF", "新预算.pdf", "pdf", 3, "alice"))
                .thenReturn(1);

        DriveNodeVo result = service.rename(22L, renameRequest("  新预算．PDF  ", 3), actor);

        assertThat(result.nodeName()).isEqualTo("新预算.PDF");
        assertThat(result.extension()).isEqualTo("pdf");
        assertThat(result.version()).isEqualTo(4);
        verify(logService).success(DriveConstants.ACTION_RENAME, actor, node,
                "旧预算.pdf", "新预算.PDF");
    }

    @Test
    @DisplayName("文件重命名不能改变已存扩展名")
    void shouldRejectFileExtensionChange()
    {
        DriveNode node = file(22L, 4L, 0L, "预算.pdf");
        when(nodeMapper.selectByIdForUpdate(22L)).thenReturn(node);
        when(spaceService.requireWritableSpace(4L, actor)).thenReturn(space);

        assertBusinessCode(() -> service.rename(
                22L, renameRequest("预算.docx", 0), actor),
                DriveErrorCodes.DRIVE_FILE_TYPE_REJECTED);

        verify(nodeMapper, never()).updateName(anyLong(), anyString(), anyString(),
                anyString(), anyInt(), anyString());
    }

    @Test
    @DisplayName("文件夹重命名使用完整规范化名称且不推断扩展名")
    void shouldRenameFolderAsWholeName()
    {
        DriveNode node = folder(31L, 4L, 0L, "旧目录");
        node.setVersion(2);
        when(nodeMapper.selectByIdForUpdate(31L)).thenReturn(node);
        when(spaceService.requireWritableSpace(4L, actor)).thenReturn(space);
        when(nodeMapper.existsActiveName(4L, 0L, "季度.归档", 31L)).thenReturn(0);
        when(nodeMapper.updateName(31L, "季度.归档", "季度.归档", null, 2, "alice"))
                .thenReturn(1);

        DriveNodeVo result = service.rename(
                31L, renameRequest(" 季度．归档 ", 2), actor);

        assertThat(result.nodeName()).isEqualTo("季度.归档");
        assertThat(result.extension()).isNull();
    }

    @Test
    @DisplayName("重命名更新数为零时返回并发冲突")
    void shouldRejectStaleRename()
    {
        DriveNode node = file(22L, 4L, 0L, "预算.pdf");
        node.setVersion(3);
        when(nodeMapper.selectByIdForUpdate(22L)).thenReturn(node);
        when(spaceService.requireWritableSpace(4L, actor)).thenReturn(space);
        when(nodeMapper.existsActiveName(4L, 0L, "新预算.pdf", 22L)).thenReturn(0);

        assertBusinessCode(() -> service.rename(
                22L, renameRequest("新预算.pdf", 3), actor),
                DriveErrorCodes.DRIVE_CONCURRENT_MODIFICATION);

        verify(nodeMapper).updateName(
                22L, "新预算.pdf", "新预算.pdf", "pdf", 3, "alice");
    }

    @Test
    @DisplayName("非根移动按节点 ID 升序加锁并先更新后代路径")
    void shouldMoveFolderWithDeterministicLocksAndDescendantPathUpdate()
    {
        DriveNode source = folder(20L, 4L, 6L, "月报");
        source.setAncestors("0,6");
        source.setVersion(4);
        DriveNode target = folder(8L, 4L, 3L, "归档");
        target.setAncestors("0,3");
        when(nodeMapper.selectByIdForUpdate(8L)).thenReturn(target);
        when(nodeMapper.selectByIdForUpdate(20L)).thenReturn(source);
        when(spaceService.requireWritableSpace(4L, actor)).thenReturn(space);
        when(nodeMapper.existsActiveName(4L, 8L, "月报", 20L)).thenReturn(0);
        when(nodeMapper.updateParent(20L, 8L, "0,3,8", 4, "alice")).thenReturn(1);

        DriveNodeVo result = service.move(20L, moveRequest(8L, 4), actor);

        InOrder locks = inOrder(nodeMapper);
        locks.verify(nodeMapper).selectByIdForUpdate(8L);
        locks.verify(nodeMapper).selectByIdForUpdate(20L);
        InOrder updates = inOrder(nodeMapper);
        updates.verify(nodeMapper).updateDescendantAncestors(
                4L, "0,6,20", "0,3,8,20", "alice");
        updates.verify(nodeMapper).updateParent(20L, 8L, "0,3,8", 4, "alice");
        assertThat(result.parentId()).isEqualTo(8L);
        assertThat(result.version()).isEqualTo(5);
        verify(logService).success(DriveConstants.ACTION_MOVE, actor, source,
                "parentId=6", "parentId=8");
    }

    @Test
    @DisplayName("移动到虚拟根目录只锁源节点和空间行且不查询节点零")
    void shouldMoveToVirtualRootWithoutLoadingNodeZero()
    {
        DriveNode source = file(22L, 4L, 8L, "预算.pdf");
        source.setAncestors("0,8");
        source.setVersion(1);
        when(nodeMapper.selectByIdForUpdate(22L)).thenReturn(source);
        when(spaceService.requireWritableSpace(4L, actor)).thenReturn(space);
        when(spaceMapper.selectByIdForUpdate(4L)).thenReturn(space);
        when(nodeMapper.existsActiveName(4L, 0L, "预算.pdf", 22L)).thenReturn(0);
        when(nodeMapper.updateParent(22L, 0L, "0", 1, "alice")).thenReturn(1);

        DriveNodeVo result = service.move(22L, moveRequest(0L, 1), actor);

        assertThat(result.parentId()).isZero();
        assertThat(result.logicalPath()).isEqualTo("/预算.pdf");
        verify(nodeMapper, never()).selectByIdForUpdate(0L);
        verify(spaceMapper).selectByIdForUpdate(4L);
    }

    @Test
    @DisplayName("文件夹不能移动到自身或精确祖先路径包含自身的后代")
    void shouldRejectSelfAndDescendantMoves()
    {
        DriveNode source = folder(2L, 4L, 0L, "源目录");
        DriveNode descendant = folder(12L, 4L, 2L, "后代");
        descendant.setAncestors("0,2,7");
        when(nodeMapper.selectByIdForUpdate(2L)).thenReturn(source);
        when(nodeMapper.selectByIdForUpdate(12L)).thenReturn(descendant);
        when(spaceService.requireWritableSpace(4L, actor)).thenReturn(space);

        assertBusinessCode(() -> service.move(2L, moveRequest(2L, 0), actor),
                DriveErrorCodes.DRIVE_INVALID_MOVE);
        assertBusinessCode(() -> service.move(2L, moveRequest(12L, 0), actor),
                DriveErrorCodes.DRIVE_INVALID_MOVE);

        verify(nodeMapper, never()).updateParent(anyLong(), anyLong(), anyString(),
                anyInt(), anyString());
    }

    @Test
    @DisplayName("祖先 ID 使用完整逗号令牌比较而不是子串比较")
    void shouldAvoidAncestorSubstringFalsePositive()
    {
        DriveNode source = folder(2L, 4L, 0L, "源目录");
        DriveNode target = folder(30L, 4L, 12L, "目标目录");
        target.setAncestors("0,12");
        when(nodeMapper.selectByIdForUpdate(2L)).thenReturn(source);
        when(nodeMapper.selectByIdForUpdate(30L)).thenReturn(target);
        when(spaceService.requireWritableSpace(4L, actor)).thenReturn(space);
        when(nodeMapper.existsActiveName(4L, 30L, "源目录", 2L)).thenReturn(0);
        when(nodeMapper.updateParent(2L, 30L, "0,12,30", 0, "alice")).thenReturn(1);

        DriveNodeVo result = service.move(2L, moveRequest(30L, 0), actor);

        assertThat(result.parentId()).isEqualTo(30L);
        verify(nodeMapper).updateParent(2L, 30L, "0,12,30", 0, "alice");
    }

    @Test
    @DisplayName("移动目标必须是同空间活动文件夹")
    void shouldRejectForeignOrInactiveTarget()
    {
        DriveNode source = file(22L, 4L, 0L, "预算.pdf");
        DriveNode foreign = folder(30L, 9L, 0L, "其他空间");
        when(nodeMapper.selectByIdForUpdate(22L)).thenReturn(source);
        when(nodeMapper.selectByIdForUpdate(30L)).thenReturn(foreign);
        when(spaceService.requireWritableSpace(4L, actor)).thenReturn(space);

        assertBusinessCode(() -> service.move(22L, moveRequest(30L, 0), actor),
                DriveErrorCodes.DRIVE_INVALID_MOVE);

        foreign.setSpaceId(4L);
        foreign.setStatus(DriveConstants.STATUS_TRASHED);
        assertBusinessCode(() -> service.move(22L, moveRequest(30L, 0), actor),
                DriveErrorCodes.DRIVE_NODE_NOT_FOUND);
        verify(nodeMapper, never()).updateParent(anyLong(), anyLong(), anyString(),
                anyInt(), anyString());
    }

    @Test
    @DisplayName("目标名称预检和唯一索引竞争都返回名称冲突")
    void shouldMapMoveNameConflicts()
    {
        DriveNode source = file(22L, 4L, 0L, "预算.pdf");
        DriveNode target = folder(30L, 4L, 0L, "目标目录");
        when(nodeMapper.selectByIdForUpdate(22L)).thenReturn(source);
        when(nodeMapper.selectByIdForUpdate(30L)).thenReturn(target);
        when(spaceService.requireWritableSpace(4L, actor)).thenReturn(space);
        when(nodeMapper.existsActiveName(4L, 30L, "预算.pdf", 22L))
                .thenReturn(1, 0);

        assertBusinessCode(() -> service.move(22L, moveRequest(30L, 0), actor),
                DriveErrorCodes.DRIVE_NAME_CONFLICT);

        when(nodeMapper.updateParent(22L, 30L, "0,30", 0, "alice"))
                .thenThrow(new DuplicateKeyException("uk_drive_node_active_name"));
        assertBusinessCode(() -> service.move(22L, moveRequest(30L, 0), actor),
                DriveErrorCodes.DRIVE_NAME_CONFLICT);
    }

    @Test
    @DisplayName("移动更新数为零时返回并发冲突")
    void shouldRejectStaleMove()
    {
        DriveNode source = file(22L, 4L, 0L, "预算.pdf");
        DriveNode target = folder(30L, 4L, 0L, "目标目录");
        when(nodeMapper.selectByIdForUpdate(22L)).thenReturn(source);
        when(nodeMapper.selectByIdForUpdate(30L)).thenReturn(target);
        when(spaceService.requireWritableSpace(4L, actor)).thenReturn(space);
        when(nodeMapper.existsActiveName(4L, 30L, "预算.pdf", 22L)).thenReturn(0);
        when(nodeMapper.updateParent(22L, 30L, "0,30", 0, "alice")).thenReturn(0);

        assertBusinessCode(() -> service.move(22L, moveRequest(30L, 0), actor),
                DriveErrorCodes.DRIVE_CONCURRENT_MODIFICATION);
    }

    @Test
    @DisplayName("最近使用限制数量并按当前空间权限过滤失效节点")
    void shouldFilterRecentNodesThroughCurrentPermissions()
    {
        DriveNode allowed = file(1L, 4L, 0L, "可读.pdf");
        DriveNode denied = file(2L, 5L, 0L, "他人.pdf");
        DriveNode missingSpace = file(3L, 6L, 0L, "失效.pdf");
        DriveNode trashed = file(4L, 4L, 0L, "已删除.pdf");
        trashed.setStatus(DriveConstants.STATUS_TRASHED);
        when(operationLogMapper.selectRecentNodes(20L, 50))
                .thenReturn(List.of(allowed, denied, missingSpace, trashed));
        when(spaceService.requireReadableSpace(4L, actor)).thenReturn(space);
        doThrow(new DriveException(DriveErrorCodes.DRIVE_ACCESS_DENIED, "denied"))
                .when(spaceService).requireReadableSpace(5L, actor);
        doThrow(new DriveException(DriveErrorCodes.DRIVE_SPACE_NOT_FOUND, "missing"))
                .when(spaceService).requireReadableSpace(6L, actor);

        List<DriveNodeVo> result = service.recent(actor, 999);

        assertThat(result).extracting(DriveNodeVo::nodeId).containsExactly(1L);
        verify(operationLogMapper).selectRecentNodes(20L, 50);
    }

    @Test
    @DisplayName("最近使用下限收敛为一条")
    void shouldClampRecentLowerLimit()
    {
        when(operationLogMapper.selectRecentNodes(20L, 1)).thenReturn(List.of());

        assertThat(service.recent(actor, 0)).isEmpty();

        verify(operationLogMapper).selectRecentNodes(20L, 1);
    }

    @Test
    @DisplayName("最近使用查询异常返回安全错误而不泄露 SQL")
    void shouldTranslateRecentQueryFailure()
    {
        when(operationLogMapper.selectRecentNodes(20L, 20)).thenThrow(
                new IllegalStateException("select storage_key from drive_node"));

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.recent(actor, 20));

        assertThat(thrown).isInstanceOf(DriveException.class)
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE);
        assertThat(thrown.getMessage()).doesNotContain("select", "storage_key");
    }

    @Test
    @DisplayName("读取节点详情成功后记录 OPEN 用于最近使用")
    void shouldAuditSuccessfulOpen()
    {
        DriveNode node = folder(8L, 4L, 0L, "报表");
        when(nodeMapper.selectActiveById(8L)).thenReturn(node);
        when(spaceService.requireReadableSpace(4L, actor)).thenReturn(space);

        service.detail(8L, actor);

        verify(logService).success(DriveConstants.ACTION_OPEN, actor, node,
                null, "报表");
    }

    @Test
    @DisplayName("PURGING 节点不声明写入、删除或预览能力")
    void shouldDisableAllActionsForPurgingNode()
    {
        DriveNode node = file(22L, 4L, 0L, "预算.pdf");
        node.setStatus(DriveConstants.STATUS_PURGING);
        node.setActiveFlag(null);
        when(spaceService.requireSpace(4L)).thenReturn(space);

        DriveNodeVo result = service.toVo(node, actor);

        assertThat(result.canWrite()).isFalse();
        assertThat(result.canDelete()).isFalse();
        assertThat(result.canPreview()).isFalse();
    }

    @Test
    @DisplayName("组织预算阻断时节点只禁用写操作并保留清理能力")
    void shouldExposeReadOnlyNodeCapabilitiesWhenOrganizationBudgetBlocksWrites()
    {
        DriveNode node = file(22L, 4L, 0L, "预算.pdf");
        when(spaceService.requireSpace(4L)).thenReturn(space);
        when(spaceService.canWriteSpace(space, actor)).thenReturn(false);

        DriveNodeVo result = service.toVo(node, actor);

        assertThat(result.canWrite()).isFalse();
        assertThat(result.canDelete()).isTrue();
    }

    private DriveActor writableActor()
    {
        return new DriveActor(20L, 8L, "财务部", "alice",
                Set.of(DriveConstants.PERMISSION_ACCESS), false);
    }

    private static DriveFolderCreateRequest folderRequest(Long spaceId, Long parentId, String name)
    {
        DriveFolderCreateRequest request = new DriveFolderCreateRequest();
        request.setSpaceId(spaceId);
        request.setParentId(parentId);
        request.setName(name);
        return request;
    }

    private static DriveRenameRequest renameRequest(String name, int version)
    {
        DriveRenameRequest request = new DriveRenameRequest();
        request.setName(name);
        request.setVersion(version);
        return request;
    }

    private static DriveMoveRequest moveRequest(Long targetParentId, int version)
    {
        DriveMoveRequest request = new DriveMoveRequest();
        request.setTargetParentId(targetParentId);
        request.setVersion(version);
        return request;
    }

    private static void assertBusinessCode(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, String code)
    {
        assertThatThrownBy(callable).isInstanceOf(DriveException.class)
                .extracting("businessCode").isEqualTo(code);
    }

    private static DriveSpace activeSpace(Long id, Long ownerId)
    {
        DriveSpace value = new DriveSpace();
        value.setSpaceId(id);
        value.setSpaceType(DriveConstants.SPACE_PERSONAL);
        value.setSpaceName("我的文件");
        value.setOwnerUserId(ownerId);
        value.setStatus(DriveConstants.STATUS_ACTIVE);
        value.setQuotaBytes(1000L);
        value.setUsedBytes(0L);
        return value;
    }

    private static DriveNode file(Long id, Long spaceId, Long parentId, String name)
    {
        DriveNode node = baseNode(id, spaceId, parentId, name);
        node.setNodeType(DriveConstants.NODE_FILE);
        node.setExtension("pdf");
        node.setContentType("application/pdf");
        node.setSizeBytes(10L);
        return node;
    }

    private static DriveNode folder(Long id, Long spaceId, Long parentId, String name)
    {
        DriveNode node = baseNode(id, spaceId, parentId, name);
        node.setNodeType(DriveConstants.NODE_FOLDER);
        node.setSizeBytes(0L);
        return node;
    }

    private static DriveNode baseNode(Long id, Long spaceId, Long parentId, String name)
    {
        DriveNode node = new DriveNode();
        node.setNodeId(id);
        node.setSpaceId(spaceId);
        node.setParentId(parentId);
        node.setAncestors("0");
        node.setNodeName(name);
        node.setNormalizedName(name.toLowerCase());
        node.setStatus(DriveConstants.STATUS_ACTIVE);
        node.setActiveFlag(1);
        node.setVersion(0);
        node.setCreateBy("alice");
        node.setCreateTime(new Date());
        node.setUpdateBy("alice");
        node.setUpdateTime(new Date());
        return node;
    }
}
