package com.erp.oa.service.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.oa.constant.OaSignPackageStatus;
import com.erp.oa.constant.OaSignTaskStatus;
import com.erp.oa.domain.OaSignFileEvidence;
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

/**
 * 每个未完成签约任务使用独立事务删除。文件清理在该事务成功提交后单独执行。
 */
@Service
public class OaSignTaskHardDeleteExecutor
{
    private final OaSignTaskMapper taskMapper;
    private final OaSignPackageMapper packageMapper;
    private final OaSignPackageDocumentMapper documentMapper;
    private final OaSignEventMapper signEventMapper;
    private final OaSignFileEvidenceMapper evidenceMapper;
    private final OaSignFinalConfirmationMapper finalConfirmationMapper;
    private final OaSignNotificationOutboxMapper notificationMapper;
    private final OaSignTaskEventMapper taskEventMapper;
    private final OaSignOnboardImportRowMapper importRowMapper;
    private final OaSignOnboardDataRequestMapper dataRequestMapper;
    private final OaSignOnboardImportBatchMapper importBatchMapper;
    private final OaHrRenewalGuardMapper renewalGuardMapper;
    private final OaSignFileStorageService fileStorageService;
    private final OaSignFileCleanupService fileCleanupService;
    private final OaSignFileCleanupProcessor fileCleanupProcessor;
    private final OaSignTaskHardDeleteLedgerService hardDeleteLedgerService;
    private final ShopScopeService shopScopeService;

    public OaSignTaskHardDeleteExecutor(OaSignTaskMapper taskMapper,
            OaSignPackageMapper packageMapper,
            OaSignPackageDocumentMapper documentMapper,
            OaSignEventMapper signEventMapper,
            OaSignFileEvidenceMapper evidenceMapper,
            OaSignFinalConfirmationMapper finalConfirmationMapper,
            OaSignNotificationOutboxMapper notificationMapper,
            OaSignTaskEventMapper taskEventMapper,
            OaSignOnboardImportRowMapper importRowMapper,
            OaSignOnboardDataRequestMapper dataRequestMapper,
            OaSignOnboardImportBatchMapper importBatchMapper,
            OaHrRenewalGuardMapper renewalGuardMapper,
            OaSignFileStorageService fileStorageService,
            OaSignFileCleanupService fileCleanupService,
            OaSignFileCleanupProcessor fileCleanupProcessor,
            OaSignTaskHardDeleteLedgerService hardDeleteLedgerService,
            @Qualifier("oaSignScopeService") ShopScopeService shopScopeService)
    {
        this.taskMapper = taskMapper;
        this.packageMapper = packageMapper;
        this.documentMapper = documentMapper;
        this.signEventMapper = signEventMapper;
        this.evidenceMapper = evidenceMapper;
        this.finalConfirmationMapper = finalConfirmationMapper;
        this.notificationMapper = notificationMapper;
        this.taskEventMapper = taskEventMapper;
        this.importRowMapper = importRowMapper;
        this.dataRequestMapper = dataRequestMapper;
        this.importBatchMapper = importBatchMapper;
        this.renewalGuardMapper = renewalGuardMapper;
        this.fileStorageService = fileStorageService;
        this.fileCleanupService = fileCleanupService;
        this.fileCleanupProcessor = fileCleanupProcessor;
        this.hardDeleteLedgerService = hardDeleteLedgerService;
        this.shopScopeService = shopScopeService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public DeleteCommit deleteDatabaseRecords(Long taskId, Long expectedVersion,
            Long selectedShopDeptId, OaSignTaskHardDeleteOperation operation,
            OaSignTaskBatchDeleteResult currentResult, int totalCount)
    {
        requireAdmin();
        OaSignTask task = taskId == null ? null : taskMapper.lockOaSignTaskById(taskId);
        if (task == null)
        {
            throw rejected("TASK_NOT_FOUND", "签约任务不存在或已被删除");
        }
        if (!Objects.equals(task.getVersion(), expectedVersion))
        {
            throw rejected("TASK_VERSION_CHANGED", "签约任务版本已变化，请刷新列表后重新选择");
        }
        assertInScope(task, selectedShopDeptId);
        assertTaskCanBeDeleted(task);

        OaSignPackage signPackage = lockBoundPackage(task);
        Long packageId = signPackage == null ? null : signPackage.getPackageId();
        if (signPackage != null)
        {
            assertPackageCanBeDeleted(task, signPackage);
        }

        List<OaSignPackageDocument> documents = packageId == null
                ? List.of() : documentMapper.selectDocumentsByPackageId(packageId);
        List<OaSignFileEvidence> evidence = packageId == null
                ? List.of() : evidenceMapper.selectEvidenceByPackageId(packageId);
        Set<String> fileReferences = collectFileReferences(documents, evidence);
        if (packageId != null)
        {
            add(fileReferences, signPackage.getSignatureSampleFileUrl());
            fileStorageService.validateManagedPackageReferences(task.getTaskId(), packageId, fileReferences);
        }

        // The ledger is committed atomically with the database delete. File-system
        // failures can therefore always be located and retried after the task row is gone.
        Long cleanupId = packageId == null ? null
                : fileCleanupService.enqueue(task.getTaskId(), packageId, fileReferences);

        notificationMapper.deleteByTaskId(task.getTaskId());
        List<Long> importBatchIds = importRowMapper.selectBatchIdsByTaskOrPackage(task.getTaskId(), packageId);
        List<Long> dataRequestIds = importRowMapper.selectDataRequestIdsByTaskOrPackage(
                task.getTaskId(), packageId);
        importRowMapper.unbindHardDeletedTask(task.getTaskId(), packageId);
        // 员工补资待办直接由 oa_sign_onboard_data_request 派生；删除请求同时移除待办及签名样本。
        if (dataRequestIds != null && !dataRequestIds.isEmpty()
                && dataRequestMapper.deleteRequestsByIds(dataRequestIds) != dataRequestIds.size())
        {
            throw new IllegalStateException("员工补资料任务删除结果异常");
        }

        if (packageId != null)
        {
            finalConfirmationMapper.deleteConfirmationDocumentsByPackageId(packageId);
            finalConfirmationMapper.deleteConfirmationsByPackageId(packageId);
            evidenceMapper.deleteEvidenceByPackageId(packageId);
            signEventMapper.deleteEventsByPackageId(packageId);
            documentMapper.deleteDocumentsByPackageId(packageId);
        }

        if ("RENEWAL".equals(normalize(task.getScenario())))
        {
            renewalGuardMapper.releaseByTaskForHardDelete(task.getEmployeeId(), "RENEWAL", task.getTaskId());
        }
        taskMapper.deleteReassignmentsByTaskId(task.getTaskId());
        taskEventMapper.deleteTaskEvents(task.getTaskId());
        if (packageId != null && packageMapper.deleteOaSignPackageById(packageId) != 1)
        {
            throw new IllegalStateException("签约包删除结果异常");
        }
        if (taskMapper.deleteOaSignTaskByIdAndVersion(
                task.getTaskId(), expectedVersion) != 1)
        {
            throw new IllegalStateException("签约任务删除结果异常");
        }
        for (Long batchId : importBatchIds == null ? List.<Long>of() : importBatchIds)
        {
            importBatchMapper.refreshAfterHardDelete(batchId);
        }
        OaSignTaskBatchDeleteItem item = new OaSignTaskBatchDeleteItem();
        item.setTaskId(task.getTaskId());
        item.setTaskNo(task.getTaskNo());
        item.setResult("DELETED");
        item.setCode("DELETED");
        item.setMessage(packageId == null
                ? "签约任务及其关联数据已删除"
                : "签约任务及其受管数据已删除，文件由持久清理台账保证");
        OaSignTaskBatchDeleteResult nextResult = hardDeleteLedgerService.appendResult(
                currentResult, item, totalCount);
        // Persist success in the same transaction as the destructive SQL. A crash can
        // therefore never turn an already-deleted task into a TASK_NOT_FOUND replay.
        hardDeleteLedgerService.recordProgressInCurrentTransaction(operation, nextResult);
        return new DeleteCommit(task.getTaskId(), task.getTaskNo(), packageId,
                fileReferences, cleanupId, nextResult);
    }

    public void deleteCommittedFiles(DeleteCommit commit)
    {
        if (commit != null && commit.packageId() != null)
        {
            if (commit.cleanupId() == null)
            {
                throw new IllegalStateException("签约文件清理台账缺失");
            }
            if (!fileCleanupProcessor.processById(commit.cleanupId()))
            {
                throw new IllegalStateException("签约文件清理已进入后台重试");
            }
        }
    }

    private OaSignPackage lockBoundPackage(OaSignTask task)
    {
        List<Long> packageIds = packageMapper.selectPackageIdsByTaskId(task.getTaskId());
        List<Long> linked = packageIds == null ? List.of() : packageIds;
        if (linked.size() > 1)
        {
            throw rejected("PACKAGE_LINK_CONFLICT", "签约任务关联了多个签约包，拒绝删除");
        }
        Long packageId = task.getPackageId();
        if (packageId == null && !linked.isEmpty())
        {
            packageId = linked.get(0);
        }
        if (packageId == null)
        {
            return null;
        }
        if (!linked.isEmpty() && !Objects.equals(linked.get(0), packageId))
        {
            throw rejected("PACKAGE_LINK_CONFLICT", "签约任务与签约包的关联不一致，拒绝删除");
        }
        OaSignPackage signPackage = packageMapper.lockOaSignPackageById(packageId);
        if (signPackage == null || !Objects.equals(signPackage.getTaskId(), task.getTaskId())
                || task.getPackageId() != null && !Objects.equals(task.getPackageId(), signPackage.getPackageId()))
        {
            throw rejected("PACKAGE_LINK_CONFLICT", "签约任务与签约包的关联不一致，拒绝删除");
        }
        return signPackage;
    }

    private void assertTaskCanBeDeleted(OaSignTask task)
    {
        String status = normalize(task.getStatus());
        if (OaSignTaskStatus.SIGNED.name().equals(status)
                || OaSignTaskStatus.NO_ACTION.name().equals(status)
                || task.getCompletedTime() != null)
        {
            throw rejected("FINAL_TASK_PROTECTED", "已完成的签约任务不允许硬删除，请使用作废和重签流程");
        }
        if (task.getReissueOfTaskId() != null || task.getReissuedToTaskId() != null
                || taskMapper.countLifecycleReferences(task.getTaskId()) > 0)
        {
            throw rejected("REISSUE_CHAIN_PROTECTED", "任务已进入作废重签链，不能单独硬删除");
        }
    }

    private void assertPackageCanBeDeleted(OaSignTask task, OaSignPackage signPackage)
    {
        String packageStatus = normalize(signPackage.getStatus());
        if (OaSignPackageStatus.SIGNED.toUpperCase(Locale.ROOT).equals(packageStatus)
                || "CONFIRMED".equals(normalize(signPackage.getFinalConfirmationStatus()))
                || signPackage.getFinalConfirmedTime() != null
                || finalConfirmationMapper.countByPackageId(signPackage.getPackageId()) > 0)
        {
            throw rejected("FINAL_PACKAGE_PROTECTED", "签约包已完成员工最终确认，不允许硬删除");
        }
        if (!Objects.equals(task.getEmployeeId(), signPackage.getEmployeeId()))
        {
            throw rejected("PACKAGE_LINK_CONFLICT", "签约任务与签约包的员工不一致，拒绝删除");
        }
        if (signPackage.getReissueOfPackageId() != null || signPackage.getReissuedToPackageId() != null
                || packageMapper.countLifecycleReferences(signPackage.getPackageId()) > 0)
        {
            throw rejected("REISSUE_CHAIN_PROTECTED", "签约包已进入作废重签链，不能单独硬删除");
        }
    }

    private void assertInScope(OaSignTask task, Long selectedShopDeptId)
    {
        if (task.getShopDeptId() == null)
        {
            return;
        }
        List<Long> scopeDeptIds = shopScopeService.resolveScopeDeptIds(selectedShopDeptId);
        if (scopeDeptIds == null || !scopeDeptIds.contains(task.getShopDeptId()))
        {
            throw rejected("OUT_OF_SCOPE", "无权删除该组织的签约任务");
        }
    }

    private Set<String> collectFileReferences(List<OaSignPackageDocument> documents,
            List<OaSignFileEvidence> evidence)
    {
        Set<String> references = new LinkedHashSet<>();
        for (OaSignPackageDocument document : documents == null
                ? List.<OaSignPackageDocument>of() : documents)
        {
            add(references, document.getGeneratedFileUrl());
            add(references, document.getGeneratedPdfUrl());
            add(references, document.getSignedFileUrl());
            add(references, document.getCertificateFileUrl());
            add(references, document.getReviewPdfUrl());
            add(references, document.getSignedPdfUrl());
            add(references, document.getFinalPdfUrl());
            add(references, document.getFinalArchivePdfUrl());
            add(references, document.getSignatureFileUrl());
        }
        for (OaSignFileEvidence row : evidence == null ? List.<OaSignFileEvidence>of() : evidence)
        {
            add(references, row.getFileUrl());
        }
        return references;
    }

    private void add(Collection<String> references, String reference)
    {
        if (reference != null && !reference.isBlank())
        {
            references.add(reference);
        }
    }

    private void requireAdmin()
    {
        if (!SecurityUtils.isAdmin())
        {
            throw rejected("ADMIN_REQUIRED", "仅系统管理员可删除签约测试任务");
        }
    }

    private static String normalize(String value)
    {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static DeleteRejected rejected(String code, String message)
    {
        return new DeleteRejected(code, message);
    }

    public record DeleteCommit(Long taskId, String taskNo, Long packageId,
            Set<String> fileReferences, Long cleanupId,
            OaSignTaskBatchDeleteResult resultSnapshot)
    {
        public DeleteCommit(Long taskId, String taskNo, Long packageId,
                Set<String> fileReferences)
        {
            this(taskId, taskNo, packageId, fileReferences, null, null);
        }

        public DeleteCommit(Long taskId, String taskNo, Long packageId,
                Set<String> fileReferences, Long cleanupId)
        {
            this(taskId, taskNo, packageId, fileReferences, cleanupId, null);
        }

        public DeleteCommit
        {
            fileReferences = fileReferences == null ? Set.of()
                    : Set.copyOf(new ArrayList<>(fileReferences));
        }
    }

    static final class DeleteRejected extends RuntimeException
    {
        private static final long serialVersionUID = 1L;
        private final String code;

        DeleteRejected(String code, String message)
        {
            super(message);
            this.code = code;
        }

        String getCode()
        {
            return code;
        }
    }
}
