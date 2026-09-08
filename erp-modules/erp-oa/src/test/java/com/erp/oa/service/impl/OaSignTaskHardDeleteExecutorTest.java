package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPackageDocument;
import com.erp.oa.domain.OaSignTask;
import com.erp.oa.domain.OaSignTaskHardDeleteOperation;
import com.erp.oa.domain.vo.OaSignTaskBatchDeleteItem;
import com.erp.oa.domain.vo.OaSignTaskBatchDeleteResult;
import com.erp.oa.mapper.OaHrRenewalGuardMapper;
import com.erp.oa.mapper.OaSignEventMapper;
import com.erp.oa.mapper.OaSignFileEvidenceMapper;
import com.erp.oa.mapper.OaSignFinalConfirmationMapper;
import com.erp.oa.mapper.OaSignNotificationOutboxMapper;
import com.erp.oa.mapper.OaSignOnboardDataRequestMapper;
import com.erp.oa.mapper.OaSignOnboardImportBatchMapper;
import com.erp.oa.mapper.OaSignOnboardImportRowMapper;
import com.erp.oa.mapper.OaSignPackageDocumentMapper;
import com.erp.oa.mapper.OaSignPackageMapper;
import com.erp.oa.mapper.OaSignTaskEventMapper;
import com.erp.oa.mapper.OaSignTaskMapper;

@DisplayName("管理员硬删除未完成签约任务")
class OaSignTaskHardDeleteExecutorTest
{
    private OaSignTaskMapper taskMapper;
    private OaSignPackageMapper packageMapper;
    private OaSignPackageDocumentMapper documentMapper;
    private OaSignEventMapper eventMapper;
    private OaSignFileEvidenceMapper evidenceMapper;
    private OaSignFinalConfirmationMapper confirmationMapper;
    private OaSignNotificationOutboxMapper notificationMapper;
    private OaSignTaskEventMapper taskEventMapper;
    private OaSignOnboardImportRowMapper importRowMapper;
    private OaSignOnboardDataRequestMapper dataRequestMapper;
    private OaSignOnboardImportBatchMapper importBatchMapper;
    private OaHrRenewalGuardMapper renewalGuardMapper;
    private OaSignFileStorageService storageService;
    private OaSignFileCleanupService fileCleanupService;
    private OaSignFileCleanupProcessor fileCleanupProcessor;
    private OaSignTaskHardDeleteLedgerService hardDeleteLedgerService;
    private ShopScopeService shopScopeService;
    private OaSignTaskHardDeleteExecutor executor;

    @BeforeEach
    void setUp()
    {
        taskMapper = mock(OaSignTaskMapper.class);
        packageMapper = mock(OaSignPackageMapper.class);
        documentMapper = mock(OaSignPackageDocumentMapper.class);
        eventMapper = mock(OaSignEventMapper.class);
        evidenceMapper = mock(OaSignFileEvidenceMapper.class);
        confirmationMapper = mock(OaSignFinalConfirmationMapper.class);
        notificationMapper = mock(OaSignNotificationOutboxMapper.class);
        taskEventMapper = mock(OaSignTaskEventMapper.class);
        importRowMapper = mock(OaSignOnboardImportRowMapper.class);
        dataRequestMapper = mock(OaSignOnboardDataRequestMapper.class);
        importBatchMapper = mock(OaSignOnboardImportBatchMapper.class);
        renewalGuardMapper = mock(OaHrRenewalGuardMapper.class);
        storageService = mock(OaSignFileStorageService.class);
        fileCleanupService = mock(OaSignFileCleanupService.class);
        fileCleanupProcessor = mock(OaSignFileCleanupProcessor.class);
        hardDeleteLedgerService = mock(OaSignTaskHardDeleteLedgerService.class);
        when(hardDeleteLedgerService.appendResult(any(), any(), anyInt()))
                .thenAnswer(invocation -> appendResult(invocation.getArgument(0),
                        invocation.getArgument(1), invocation.getArgument(2)));
        shopScopeService = mock(ShopScopeService.class);
        executor = new OaSignTaskHardDeleteExecutor(taskMapper, packageMapper, documentMapper,
                eventMapper, evidenceMapper, confirmationMapper, notificationMapper,
                taskEventMapper, importRowMapper, dataRequestMapper, importBatchMapper, renewalGuardMapper,
                storageService, fileCleanupService, fileCleanupProcessor,
                hardDeleteLedgerService, shopScopeService);
        SecurityContextHolder.setUserId("1");
    }

    @AfterEach
    void clearSecurity()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("待最终确认任务可删除关联数据、员工补资请求并解除Excel导入绑定")
    void shouldDeleteUnfinishedTaskDependenciesAndKeepImportRow()
    {
        OaSignTask task = task("PENDING_FINAL_CONFIRM");
        OaSignPackage signPackage = signPackage("pending_final_confirm");
        signPackage.setSignatureSampleFileUrl(
                "/profile/private/sign-package/task-9/package-90/SP-90-V1/signature.png");
        OaSignPackageDocument document = new OaSignPackageDocument();
        document.setFinalArchivePdfUrl(
                "/profile/private/sign-package/task-9/package-90/SP-90-V1/final-archive.pdf");
        when(taskMapper.lockOaSignTaskById(9L)).thenReturn(task);
        when(shopScopeService.resolveScopeDeptIds(1171L)).thenReturn(List.of(1171L));
        when(packageMapper.selectPackageIdsByTaskId(9L)).thenReturn(List.of(90L));
        when(packageMapper.lockOaSignPackageById(90L)).thenReturn(signPackage);
        when(documentMapper.selectDocumentsByPackageId(90L)).thenReturn(List.of(document));
        when(evidenceMapper.selectEvidenceByPackageId(90L)).thenReturn(List.of());
        when(importRowMapper.selectBatchIdsByTaskOrPackage(9L, 90L)).thenReturn(List.of(7L));
        when(importRowMapper.selectDataRequestIdsByTaskOrPackage(9L, 90L)).thenReturn(List.of(501L));
        when(dataRequestMapper.deleteRequestsByIds(List.of(501L))).thenReturn(1);
        when(packageMapper.deleteOaSignPackageById(90L)).thenReturn(1);
        when(taskMapper.deleteOaSignTaskByIdAndVersion(9L, 3L)).thenReturn(1);
        when(fileCleanupService.enqueue(any(), any(), any())).thenReturn(700L);
        when(fileCleanupProcessor.processById(700L)).thenReturn(true);

        OaSignTaskHardDeleteExecutor.DeleteCommit commit =
                executor.deleteDatabaseRecords(9L, 3L, 1171L,
                        operation(1), emptyResult(1), 1);
        executor.deleteCommittedFiles(commit);

        assertThat(commit.taskId()).isEqualTo(9L);
        assertThat(commit.packageId()).isEqualTo(90L);
        assertThat(commit.cleanupId()).isEqualTo(700L);
        assertThat(commit.fileReferences()).contains(
                signPackage.getSignatureSampleFileUrl(), document.getFinalArchivePdfUrl());
        assertThat(commit.resultSnapshot().getItems()).singleElement()
                .extracting("taskId", "result", "code")
                .containsExactly(9L, "DELETED", "DELETED");
        verify(storageService).validateManagedPackageReferences(9L, 90L, commit.fileReferences());
        verify(fileCleanupService).enqueue(9L, 90L, commit.fileReferences());
        verify(hardDeleteLedgerService).recordProgressInCurrentTransaction(
                any(OaSignTaskHardDeleteOperation.class), any(OaSignTaskBatchDeleteResult.class));
        verify(confirmationMapper).deleteConfirmationDocumentsByPackageId(90L);
        verify(confirmationMapper).deleteConfirmationsByPackageId(90L);
        verify(evidenceMapper).deleteEvidenceByPackageId(90L);
        verify(eventMapper).deleteEventsByPackageId(90L);
        verify(documentMapper).deleteDocumentsByPackageId(90L);
        verify(notificationMapper).deleteByTaskId(9L);
        verify(importRowMapper).selectDataRequestIdsByTaskOrPackage(9L, 90L);
        verify(importRowMapper).unbindHardDeletedTask(9L, 90L);
        verify(dataRequestMapper).deleteRequestsByIds(List.of(501L));
        verify(importBatchMapper).refreshAfterHardDelete(7L);
        verify(taskEventMapper).deleteTaskEvents(9L);
        verify(taskMapper).deleteReassignmentsByTaskId(9L);
        verify(fileCleanupProcessor).processById(700L);
        verify(storageService, never()).deleteManagedPackageFiles(any(), any(), any());
    }

    @Test
    @DisplayName("已签署任务在进入任何删除SQL前硬拒绝")
    void shouldRejectSignedTaskBeforeDeletingDependencies()
    {
        when(taskMapper.lockOaSignTaskById(9L)).thenReturn(task("SIGNED"));
        when(shopScopeService.resolveScopeDeptIds(1171L)).thenReturn(List.of(1171L));

        assertThatThrownBy(() -> executor.deleteDatabaseRecords(9L, 3L, 1171L,
                operation(1), emptyResult(1), 1))
                .isInstanceOf(OaSignTaskHardDeleteExecutor.DeleteRejected.class)
                .hasMessageContaining("不允许硬删除");
        verify(packageMapper, never()).deleteOaSignPackageById(any());
        verify(taskMapper, never()).deleteOaSignTaskByIdAndVersion(any(), any());
        verify(dataRequestMapper, never()).deleteRequestsByIds(any());
    }

    @Test
    @DisplayName("任务状态未完成但签约包已有最终确认时仍硬拒绝")
    void shouldRejectPackageWithFinalConfirmationEvidence()
    {
        when(taskMapper.lockOaSignTaskById(9L)).thenReturn(task("PENDING_FINAL_CONFIRM"));
        when(shopScopeService.resolveScopeDeptIds(1171L)).thenReturn(List.of(1171L));
        when(packageMapper.selectPackageIdsByTaskId(9L)).thenReturn(List.of(90L));
        when(packageMapper.lockOaSignPackageById(90L)).thenReturn(signPackage("pending_final_confirm"));
        when(confirmationMapper.countByPackageId(90L)).thenReturn(1);

        assertThatThrownBy(() -> executor.deleteDatabaseRecords(9L, 3L, 1171L,
                operation(1), emptyResult(1), 1))
                .isInstanceOf(OaSignTaskHardDeleteExecutor.DeleteRejected.class)
                .hasMessageContaining("最终确认");
        verify(taskMapper, never()).deleteOaSignTaskByIdAndVersion(any(), any());
    }

    @Test
    @DisplayName("清理台账无法写入时在任何删除SQL前失败关闭")
    void shouldFailClosedBeforeDeletingWhenCleanupLedgerCannotBePersisted()
    {
        OaSignTask task = task("PENDING_FINAL_CONFIRM");
        OaSignPackage signPackage = signPackage("pending_final_confirm");
        when(taskMapper.lockOaSignTaskById(9L)).thenReturn(task);
        when(shopScopeService.resolveScopeDeptIds(1171L)).thenReturn(List.of(1171L));
        when(packageMapper.selectPackageIdsByTaskId(9L)).thenReturn(List.of(90L));
        when(packageMapper.lockOaSignPackageById(90L)).thenReturn(signPackage);
        when(documentMapper.selectDocumentsByPackageId(90L)).thenReturn(List.of());
        when(evidenceMapper.selectEvidenceByPackageId(90L)).thenReturn(List.of());
        when(fileCleanupService.enqueue(9L, 90L, Set.of()))
                .thenThrow(new IllegalStateException("台账不可用"));

        assertThatThrownBy(() -> executor.deleteDatabaseRecords(9L, 3L, 1171L,
                operation(1), emptyResult(1), 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("台账");

        verify(notificationMapper, never()).deleteByTaskId(any());
        verify(documentMapper, never()).deleteDocumentsByPackageId(any());
        verify(packageMapper, never()).deleteOaSignPackageById(any());
        verify(taskMapper, never()).deleteOaSignTaskByIdAndVersion(any(), any());
    }

    @Test
    @DisplayName("任务版本变化时在任何删除语句前拒绝")
    void shouldRejectChangedVersionBeforeDeletingDependencies()
    {
        when(taskMapper.lockOaSignTaskById(9L)).thenReturn(task("PENDING_FINAL_CONFIRM"));

        assertThatThrownBy(() -> executor.deleteDatabaseRecords(9L, 2L, 1171L,
                operation(1), emptyResult(1), 1))
                .isInstanceOf(OaSignTaskHardDeleteExecutor.DeleteRejected.class)
                .hasMessageContaining("版本已变化");

        verify(packageMapper, never()).selectPackageIdsByTaskId(any());
        verify(notificationMapper, never()).deleteByTaskId(any());
        verify(taskMapper, never()).deleteOaSignTaskByIdAndVersion(any(), any());
        verify(hardDeleteLedgerService, never()).recordProgressInCurrentTransaction(any(), any());
    }

    @Test
    @DisplayName("每个任务的数据删除使用独立新事务")
    void shouldUseAnIndependentTransactionPerTask() throws Exception
    {
        Method method = OaSignTaskHardDeleteExecutor.class.getMethod(
                "deleteDatabaseRecords", Long.class, Long.class, Long.class,
                OaSignTaskHardDeleteOperation.class, OaSignTaskBatchDeleteResult.class,
                int.class);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
        assertThat(transactional.rollbackFor()).contains(Exception.class);
    }

    private OaSignTask task(String status)
    {
        OaSignTask task = new OaSignTask();
        task.setTaskId(9L);
        task.setTaskNo("ST-9");
        task.setEmployeeId(200L);
        task.setShopDeptId(1171L);
        task.setScenario("ONBOARD");
        task.setStatus(status);
        task.setPackageId(90L);
        task.setVersion(3L);
        return task;
    }

    private OaSignTaskHardDeleteOperation operation(int totalCount)
    {
        OaSignTaskHardDeleteOperation operation = new OaSignTaskHardDeleteOperation();
        operation.setOperationId(7000L);
        operation.setRequestId("hard-delete-1");
        operation.setAdministratorUserId(1L);
        operation.setStatus(OaSignTaskHardDeleteLedgerService.PROCESSING);
        operation.setClaimToken("claim-token");
        operation.setTotalCount(totalCount);
        operation.setProcessedCount(0);
        operation.setVersion(0L);
        return operation;
    }

    private OaSignTaskBatchDeleteResult emptyResult(int totalCount)
    {
        OaSignTaskBatchDeleteResult result = new OaSignTaskBatchDeleteResult();
        result.setTotalCount(totalCount);
        return result;
    }

    private OaSignTaskBatchDeleteResult appendResult(OaSignTaskBatchDeleteResult current,
            OaSignTaskBatchDeleteItem item, int totalCount)
    {
        OaSignTaskBatchDeleteResult result = new OaSignTaskBatchDeleteResult();
        result.setTotalCount(totalCount);
        result.setItems(List.of(item));
        result.setDeletedCount("DELETED".equals(item.getResult()) ? 1 : 0);
        result.setFailedCount("DELETED".equals(item.getResult()) ? 0 : 1);
        return result;
    }

    private OaSignPackage signPackage(String status)
    {
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(90L);
        signPackage.setTaskId(9L);
        signPackage.setEmployeeId(200L);
        signPackage.setStatus(status);
        return signPackage;
    }
}
