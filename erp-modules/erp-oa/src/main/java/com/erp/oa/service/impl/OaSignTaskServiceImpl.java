package com.erp.oa.service.impl;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.exception.auth.NotPermissionException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.oa.constant.OaSignOperatorType;
import com.erp.oa.constant.OaSignPackageStatus;
import com.erp.oa.constant.OaSignPlanScope;
import com.erp.oa.constant.OaSignScenarioCodes;
import com.erp.oa.constant.OaSignTaskStatus;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPackageDocument;
import com.erp.oa.domain.OaSignNotificationOutbox;
import com.erp.oa.domain.OaSignTask;
import com.erp.oa.domain.OaSignTaskEvent;
import com.erp.oa.domain.OaSignTaskHardDeleteOperation;
import com.erp.oa.domain.OaSignPlanVersion;
import com.erp.oa.api.domain.HrRenewalGuard;
import com.erp.oa.domain.dto.OaSignExceptionResolutionRequest;
import com.erp.oa.domain.dto.OaSignTaskBatchDeleteAction;
import com.erp.oa.domain.dto.OaSignTaskBatchDeleteRequest;
import com.erp.oa.domain.dto.OaSignTaskConfirmRequest;
import com.erp.oa.domain.dto.OaSignTaskNotificationRetryRequest;
import com.erp.oa.domain.dto.OaSignTaskRetryRequest;
import com.erp.oa.domain.vo.OaSignTaskDetail;
import com.erp.oa.domain.vo.OaSignTaskBatchDeleteItem;
import com.erp.oa.domain.vo.OaSignTaskBatchDeleteResult;
import com.erp.oa.domain.vo.OaSignTaskMetrics;
import com.erp.oa.mapper.OaSignTaskEventMapper;
import com.erp.oa.mapper.OaSignTaskMapper;
import com.erp.oa.mapper.OaHrRenewalGuardMapper;
import com.erp.oa.mapper.OaSignPlanVersionMapper;
import com.erp.oa.service.IOaSignPackageService;
import com.erp.oa.service.IOaSignTaskService;
import com.erp.system.api.model.LoginUser;

@Service
public class OaSignTaskServiceImpl implements IOaSignTaskService
{
    private static final Logger LOG = LoggerFactory.getLogger(OaSignTaskServiceImpl.class);
    private static final String CONFIRM_TEXT = "确认本次签约资料和文件无误";

    private final OaSignTaskMapper taskMapper;
    private final OaSignTaskEventMapper eventMapper;
    private final OaSignTaskEventService eventService;
    private final OaSignAutomationSettingsService settings;
    private final IOaSignPackageService packageService;
    private final ShopScopeService shopScopeService;
    private final OaSignTaskWorkflowService workflowService;
    private final OaSignNotificationOutboxService notificationOutboxService;
    private final OaSignConfirmationTokenService confirmationTokenService;
    private final OaSignPackageLifecycleService packageLifecycleService;
    private final OaHrRenewalGuardMapper renewalGuardMapper;
    private final OaSignPlanVersionMapper planVersionMapper;
    private final OaSignTaskHardDeleteExecutor hardDeleteExecutor;
    private final OaSignTaskHardDeleteLedgerService hardDeleteLedgerService;

    public OaSignTaskServiceImpl(OaSignTaskMapper taskMapper,
            OaSignTaskEventMapper eventMapper,
            OaSignTaskEventService eventService,
            OaSignAutomationSettingsService settings,
            IOaSignPackageService packageService,
            @Qualifier("oaSignScopeService") ShopScopeService shopScopeService,
            OaSignTaskWorkflowService workflowService,
            OaSignNotificationOutboxService notificationOutboxService,
            OaSignConfirmationTokenService confirmationTokenService,
            OaSignPackageLifecycleService packageLifecycleService,
            OaHrRenewalGuardMapper renewalGuardMapper,
            OaSignPlanVersionMapper planVersionMapper,
            OaSignTaskHardDeleteExecutor hardDeleteExecutor,
            OaSignTaskHardDeleteLedgerService hardDeleteLedgerService)
    {
        this.taskMapper = taskMapper;
        this.eventMapper = eventMapper;
        this.eventService = eventService;
        this.settings = settings;
        this.packageService = packageService;
        this.shopScopeService = shopScopeService;
        this.workflowService = workflowService;
        this.notificationOutboxService = notificationOutboxService;
        this.confirmationTokenService = confirmationTokenService;
        this.packageLifecycleService = packageLifecycleService;
        this.renewalGuardMapper = renewalGuardMapper;
        this.planVersionMapper = planVersionMapper;
        this.hardDeleteExecutor = hardDeleteExecutor;
        this.hardDeleteLedgerService = hardDeleteLedgerService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaSignTask createTask(OaSignTask task)
    {
        validateNewTask(task);
        OaSignTask existing = taskMapper.selectOaSignTaskByDedupeKey(task.getDedupeKey());
        if (existing != null)
        {
            return existing;
        }
        if (task.getAssignedHrUserId() == null)
        {
            Long currentUserId = SecurityUtils.getUserId();
            task.setAssignedHrUserId(isCurrentHr() && currentUserId != null
                    ? currentUserId : settings.resolveRequiredHrUserId());
        }
        task.setTaskNo(generateTaskNo());
        task.setStatus(OaSignTaskStatus.NEW.name());
        if (blank(task.getAutomationLevel()))
        {
            task.setAutomationLevel("MANUAL");
        }
        if (blank(task.getRiskLevel()))
        {
            task.setRiskLevel("NORMAL");
        }
        task.setRetryCount(0);
        task.setVersion(0L);
        task.setCreatedTime(nowToSecond());
        try
        {
            if (taskMapper.insertOaSignTask(task) != 1)
            {
                throw new ServiceException("签约任务创建失败");
            }
            return task;
        }
        catch (DuplicateKeyException ex)
        {
            OaSignTask duplicate = taskMapper.selectOaSignTaskByDedupeKey(task.getDedupeKey());
            if (duplicate != null)
            {
                return duplicate;
            }
            if (OaSignScenarioCodes.ONBOARD.equals(
                    OaSignScenarioCodes.normalizeTaskScenario(task.getScenario())))
            {
                duplicate = taskMapper.selectOpenOnboardTaskByEmployeeId(task.getEmployeeId());
                if (duplicate != null)
                {
                    return duplicate;
                }
            }
            throw ex;
        }
    }

    @Override
    public List<OaSignTask> selectTaskList(OaSignTask filter, Long selectedShopDeptId)
    {
        OaSignTask query = filter == null ? new OaSignTask() : filter;
        if (!blank(query.getScenario()))
        {
            query.setScenario(OaSignScenarioCodes.normalizeTaskScenario(query.getScenario()));
        }
        boolean currentHr = isCurrentHr();
        boolean technicalEvidenceReader = !currentHr && isTechnicalEvidenceReader();
        if (technicalEvidenceReader)
        {
            // Read-only evidence viewers may inspect task state across the scoped organization.
        }
        else if (!currentHr)
        {
            throw new ServiceException("当前角色未配置合同签约或技术证据验真权限");
        }
        else if (!SecurityUtils.isAdmin())
        {
            query.setAssignedHrUserId(requireCurrentUserId());
        }
        shopScopeService.appendShopScope(query, selectedShopDeptId);
        List<OaSignTask> tasks = taskMapper.selectOaSignTaskList(query);
        return technicalEvidenceReader ? tasks : OaSignResponseSanitizer.businessTasks(tasks);
    }

    @Override
    public OaSignTaskMetrics getTaskMetrics(Long selectedShopDeptId)
    {
        if (!isCurrentHr())
        {
            throw new ServiceException("当前角色未配置合同签约业务权限");
        }
        OaSignTask query = new OaSignTask();
        if (!SecurityUtils.isAdmin())
        {
            query.setAssignedHrUserId(requireCurrentUserId());
        }
        shopScopeService.appendShopScope(query, selectedShopDeptId);
        return taskMapper.selectTaskMetrics(query);
    }

    @Override
    public OaSignTaskDetail getTaskDetail(Long taskId, Long selectedShopDeptId)
    {
        OaSignTask task = loadTask(taskId);
        assertInScope(task, selectedShopDeptId);
        if (canHandle(task))
        {
            return buildDetail(task, selectedShopDeptId);
        }
        if (isTechnicalEvidenceReader())
        {
            return buildTechnicalDetail(task, selectedShopDeptId);
        }
        if (isCurrentHr())
        {
            throw new ServiceException("签约任务不存在或无权访问");
        }
        throw new ServiceException("当前角色未配置合同签约或技术证据验真权限");
    }

    @Override
    public OaSignTaskDetail revalidate(Long taskId, OaSignTaskRetryRequest request,
            Long selectedShopDeptId, String ipAddress, String userAgent)
    {
        requireRequest(request);
        OaSignTask task = loadForHandling(taskId, selectedShopDeptId);
        if (task.getConfirmedSnapshotHash() != null)
        {
            throw new ServiceException("任务已完成确认，不能重新生成文件，请使用发送重试");
        }
        OaSignTaskDetail repeated = resolveRepeatedAction(task, request.getRequestId(),
                OaSignTaskStatus.VALIDATING, selectedShopDeptId);
        if (repeated != null)
        {
            return repeated;
        }
        if (task.getPackageId() == null)
        {
            throw new ServiceException("请先创建并补充签约草稿，再运行校验");
        }
        OaSignTaskStatus status = status(task);
        if (status == OaSignTaskStatus.NEW || status == OaSignTaskStatus.NEEDS_DATA
                || status == OaSignTaskStatus.FAILED)
        {
            clearFailure(task);
            eventService.transition(task, OaSignTaskStatus.VALIDATING, OaSignOperatorType.HR,
                    SecurityUtils.getUserId(), "REVALIDATE", request.getReasonDetail(),
                    request.getRequestId(), ipAddress, userAgent);
        }
        else if (status != OaSignTaskStatus.VALIDATING && status != OaSignTaskStatus.DRAFT_CREATED)
        {
            throw new ServiceException("当前任务状态不能重新校验");
        }

        try
        {
            workflowService.completeValidation(task, selectedShopDeptId, ipAddress, userAgent);
        }
        catch (RuntimeException ex)
        {
            OaSignTask persisted = taskMapper.selectOaSignTaskById(taskId);
            if (persisted != null && OaSignTaskStatus.VALIDATING.name().equals(persisted.getStatus()))
            {
                persisted.setFailureCode("VALIDATION_FAILED");
                persisted.setFailureDetail(limit(ex.getMessage(), 1000));
                eventService.transition(persisted, OaSignTaskStatus.FAILED, OaSignOperatorType.SYSTEM,
                        null, "VALIDATION_FAILED", persisted.getFailureDetail(), null, ipAddress, userAgent);
                return buildDetail(persisted, selectedShopDeptId);
            }
            throw ex;
        }
        return buildDetail(task, selectedShopDeptId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaSignTaskDetail confirm(Long taskId, OaSignTaskConfirmRequest request,
            Long selectedShopDeptId, String ipAddress, String userAgent)
    {
        if (request == null)
        {
            throw new ServiceException("确认信息不能为空");
        }
        if (blank(request.getRequestId()))
        {
            throw new ServiceException("请求编号不能为空");
        }
        if (blank(request.getDocumentVersion()) || blank(request.getConfirmationToken()))
        {
            throw new ServiceException("确认的文档版本和令牌不能为空");
        }
        if (!CONFIRM_TEXT.equals(request.getConfirmText()))
        {
            throw new ServiceException("确认短语不正确");
        }
        OaSignTask task = loadForHandling(taskId, selectedShopDeptId);
        OaSignTaskDetail repeated = resolveRepeatedAction(task, request.getRequestId(),
                OaSignTaskStatus.READY_TO_SEND, selectedShopDeptId);
        if (repeated != null)
        {
            return repeated;
        }
        OaSignTaskDetail detail = buildInternalDetail(task, selectedShopDeptId);
        OaSignPackage signPackage = requirePreparedPackage(detail);
        String snapshotHash = calculateSnapshotHash(task, signPackage);
        assertFrozenSnapshot(task, request, signPackage, snapshotHash);

        if (OaSignTaskStatus.READY_TO_SEND.name().equals(task.getStatus())
                && Objects.equals(task.getConfirmedSnapshotHash(), snapshotHash)
                && Objects.equals(request.getDocumentVersion(), signPackage.getDocumentVersion()))
        {
            return buildDetail(task, selectedShopDeptId);
        }
        if (!OaSignTaskStatus.WAITING_HR_CONFIRM.name().equals(task.getStatus()))
        {
            throw new ServiceException("当前任务状态不能确认");
        }
        clearFailure(task);
        eventService.transition(task, OaSignTaskStatus.READY_TO_SEND, OaSignOperatorType.HR,
                SecurityUtils.getUserId(), "HR_CONFIRMED", "合同经办人确认资料和文件",
                request.getRequestId(), ipAddress, userAgent);
        Date confirmedTime = nowToSecond();
        if (taskMapper.updateConfirmation(task.getTaskId(), SecurityUtils.getUserId(), confirmedTime,
                snapshotHash, task.getStatus(), task.getVersion(), task.getAssignedHrUserId()) != 1)
        {
            throw new ServiceException("任务确认已被其他操作更新");
        }
        task.setConfirmedBy(SecurityUtils.getUserId());
        task.setConfirmedTime(confirmedTime);
        task.setConfirmedSnapshotHash(snapshotHash);
        packageService.markTaskConfirmation(task.getPackageId(), task.getTaskId(),
                "CONFIRMED", task.getPlanVersionId());
        return buildDetail(task, selectedShopDeptId);
    }

    @Override
    public OaSignTaskDetail send(Long taskId, OaSignTaskRetryRequest request,
            Long selectedShopDeptId, String ipAddress, String userAgent)
    {
        requireRequest(request);
        OaSignTask task = loadForHandling(taskId, selectedShopDeptId);
        OaSignTaskDetail repeated = resolveRepeatedAction(task, request.getRequestId(),
                OaSignTaskStatus.SENDING, selectedShopDeptId);
        if (repeated != null)
        {
            return repeated;
        }
        OaSignTaskDetail detail = buildInternalDetail(task, selectedShopDeptId);
        OaSignPackage signPackage = requirePreparedPackage(detail);
        OaSignTaskStatus currentStatus = status(task);
        if (currentStatus == OaSignTaskStatus.READY_TO_SEND)
        {
            clearFailure(task);
            eventService.transition(task, OaSignTaskStatus.SENDING, OaSignOperatorType.HR,
                    SecurityUtils.getUserId(), "SEND_REQUESTED", null,
                    request.getRequestId(), ipAddress, userAgent);
        }
        else if (currentStatus != OaSignTaskStatus.SENDING)
        {
            throw new ServiceException("当前任务状态不能发送");
        }

        try
        {
            workflowService.sendPrepared(task, signPackage, selectedShopDeptId, ipAddress, userAgent);
        }
        catch (RuntimeException ex)
        {
            OaSignTask persisted = taskMapper.selectOaSignTaskById(taskId);
            if (persisted == null || !OaSignTaskStatus.SENDING.name().equals(persisted.getStatus()))
            {
                throw ex;
            }
            persisted.setFailureCode("SEND_FAILED");
            persisted.setFailureDetail(limit(ex.getMessage(), 1000));
            eventService.transition(persisted, OaSignTaskStatus.FAILED, OaSignOperatorType.SYSTEM,
                    null, "SEND_FAILED", persisted.getFailureDetail(), null, ipAddress, userAgent);
            return buildDetail(persisted, selectedShopDeptId);
        }
        return buildDetail(task, selectedShopDeptId);
    }

    @Override
    public OaSignTaskDetail retry(Long taskId, OaSignTaskRetryRequest request,
            Long selectedShopDeptId, String ipAddress, String userAgent)
    {
        requireRequest(request);
        OaSignTask task = loadForHandling(taskId, selectedShopDeptId);
        OaSignTaskStatus retryTarget = "SEND_FAILED".equals(task.getFailureCode())
                ? OaSignTaskStatus.READY_TO_SEND : OaSignTaskStatus.VALIDATING;
        OaSignTaskDetail repeated = resolveRepeatedAction(task, request.getRequestId(),
                retryTarget, selectedShopDeptId);
        if (repeated != null)
        {
            return repeated;
        }
        if (!OaSignTaskStatus.FAILED.name().equals(task.getStatus()))
        {
            throw new ServiceException("仅失败任务可以重试");
        }
        if ("SEND_FAILED".equals(task.getFailureCode()))
        {
            clearFailure(task);
            eventService.transition(task, OaSignTaskStatus.READY_TO_SEND, OaSignOperatorType.HR,
                    SecurityUtils.getUserId(), "RETRY_SEND", request.getReasonDetail(),
                    request.getRequestId(), ipAddress, userAgent);
            return buildDetail(task, selectedShopDeptId);
        }
        return revalidate(taskId, request, selectedShopDeptId, ipAddress, userAgent);
    }

    @Override
    public int retryNotification(Long taskId, OaSignTaskNotificationRetryRequest request,
            Long selectedShopDeptId)
    {
        if (request == null || blank(request.getRequestId()) || blank(request.getBusinessKey()))
        {
            throw new ServiceException("通知重试信息不能为空");
        }
        OaSignTask task = loadForHandling(taskId, selectedShopDeptId);
        return notificationOutboxService.requeueDead(task, request.getBusinessKey());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaSignTaskDetail cancel(Long taskId, OaSignTaskRetryRequest request,
            Long selectedShopDeptId, String ipAddress, String userAgent)
    {
        requireRequest(request);
        OaSignTask task = loadForHandling(taskId, selectedShopDeptId);
        OaSignTaskDetail repeated = resolveRepeatedAction(task, request.getRequestId(),
                OaSignTaskStatus.CANCELLED, selectedShopDeptId);
        if (repeated != null)
        {
            return repeated;
        }
        eventService.lockRenewalGuardBeforeTerminal(task, OaSignTaskStatus.CANCELLED);
        if (task.getPackageId() != null)
        {
            OaSignPackage signPackage = packageService.getPackageDetail(task.getPackageId(), selectedShopDeptId);
            if (!OaSignPackageStatus.SIGNED.equals(signPackage.getStatus())
                    && !OaSignPackageStatus.VOIDED.equals(signPackage.getStatus()))
            {
                packageService.voidPackageForTask(task.getPackageId(), task.getTaskId(), selectedShopDeptId,
                        blank(request.getReasonDetail()) ? "取消签约任务" : request.getReasonDetail());
            }
        }
        eventService.transition(task, OaSignTaskStatus.CANCELLED, OaSignOperatorType.HR,
                SecurityUtils.getUserId(), blank(request.getReasonCode()) ? "CANCELLED" : request.getReasonCode(),
                request.getReasonDetail(), request.getRequestId(), ipAddress, userAgent);
        return buildDetail(task, selectedShopDeptId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaSignTaskDetail resolveException(Long taskId, OaSignExceptionResolutionRequest request,
            Long selectedShopDeptId, String ipAddress, String userAgent)
    {
        if (request == null || blank(request.getRequestId()) || blank(request.getAction())
                || blank(request.getDocumentVersion()) || blank(request.getReasonCode())
                || blank(request.getReasonDetail()) || request.getExpectedVersion() == null)
        {
            throw new ServiceException("异常处置信息不完整");
        }
        OaSignTask task = loadForHandling(taskId, selectedShopDeptId);
        assertResolutionPermission(task, request.getAction());
        if ("REISSUE".equals(request.getAction())
                && "REISSUED".equals(task.getResolutionStatus())
                && task.getReissuedToTaskId() != null)
        {
            return getTaskDetail(task.getReissuedToTaskId(), selectedShopDeptId);
        }
        if (task.getPackageId() == null)
        {
            throw new ServiceException("签约任务缺少签约包，不能处置");
        }
        OaSignPackage sourcePackage = packageService.getPackageForVerification(
                task.getPackageId(), selectedShopDeptId);
        if (!"REISSUE".equals(request.getAction()))
        {
            packageLifecycleService.resolve(task, sourcePackage, request,
                    SecurityUtils.getUserId(), SecurityUtils.getUsername(), ipAddress, userAgent);
            OaSignTask persisted = taskMapper.selectOaSignTaskById(taskId);
            return buildDetail(persisted == null ? task : persisted, selectedShopDeptId);
        }
        return reissue(task, sourcePackage, request, selectedShopDeptId, ipAddress, userAgent);
    }

    @Override
    public OaSignTaskBatchDeleteResult hardDeleteUnfinishedTasks(OaSignTaskBatchDeleteRequest request,
            Long selectedShopDeptId)
    {
        if (!SecurityUtils.isAdmin())
        {
            throw new NotPermissionException("oa:signTask:delete");
        }
        if (request == null || blank(request.getRequestId()) || !request.isIrreversibleConfirmed()
                || request.getItems() == null || request.getItems().isEmpty())
        {
            throw new ServiceException("删除请求不完整，请确认删除后不可恢复");
        }
        String requestId = request.getRequestId().trim();
        if (requestId.length() > 64)
        {
            throw new ServiceException("请求编号不能超过64个字符");
        }
        List<OaSignTaskBatchDeleteAction> actions = normalizeHardDeleteActions(request.getItems());
        Long administratorUserId = SecurityUtils.getUserId();
        String payloadHash = hardDeletePayloadHash(actions, selectedShopDeptId);
        OaSignTaskHardDeleteLedgerService.Claim claim = hardDeleteLedgerService.claim(
                requestId, administratorUserId, payloadHash, actions.size());
        if (claim.completed())
        {
            return claim.result();
        }
        OaSignTaskHardDeleteOperation operation = claim.operation();
        OaSignTaskBatchDeleteResult result = claim.result();
        int startIndex = operation.getProcessedCount() == null ? 0 : operation.getProcessedCount();
        try
        {
            for (int index = startIndex; index < actions.size(); index++)
            {
                OaSignTaskBatchDeleteAction action = actions.get(index);
                Long taskId = action.getTaskId();
                try
                {
                    OaSignTaskHardDeleteExecutor.DeleteCommit commit =
                            hardDeleteExecutor.deleteDatabaseRecords(taskId,
                                    action.getExpectedVersion(), selectedShopDeptId,
                                    operation, result, actions.size());
                    result = commit.resultSnapshot();
                    try
                    {
                        hardDeleteExecutor.deleteCommittedFiles(commit);
                        logHardDelete(requestId, taskId, "DELETED");
                    }
                    catch (RuntimeException fileException)
                    {
                        // Database deletion and the durable file-cleanup instruction have
                        // already committed. The scheduler owns physical-file recovery.
                        logHardDelete(requestId, taskId, "DELETED_FILE_CLEANUP_QUEUED");
                        LOG.warn("签约任务文件清理已转后台重试: taskId={}, type={}",
                                taskId, fileException.getClass().getSimpleName());
                    }
                }
                catch (OaSignTaskHardDeleteExecutor.DeleteRejected rejected)
                {
                    OaSignTaskBatchDeleteItem item = deleteFailureItem(taskId, "REJECTED",
                            rejected.getCode(), rejected.getMessage());
                    result = hardDeleteLedgerService.appendResult(result, item, actions.size());
                    hardDeleteLedgerService.recordProgressRequiresNew(operation, result);
                    logHardDelete(requestId, taskId, rejected.getCode());
                }
                catch (RuntimeException exception)
                {
                    OaSignTaskBatchDeleteItem item = deleteFailureItem(taskId, "FAILED",
                            "DELETE_FAILED", "删除失败，请刷新后重试；如仍失败请联系系统管理员");
                    result = hardDeleteLedgerService.appendResult(result, item, actions.size());
                    hardDeleteLedgerService.recordProgressRequiresNew(operation, result);
                    logHardDelete(requestId, taskId, "DELETE_FAILED");
                    LOG.warn("签约任务硬删除事务失败: taskId={}, type={}",
                            taskId, exception.getClass().getSimpleName());
                }
            }
            hardDeleteLedgerService.complete(operation, result);
            return result;
        }
        catch (RuntimeException failure)
        {
            if (OaSignTaskHardDeleteLedgerService.PROCESSING.equals(operation.getStatus()))
            {
                try
                {
                    hardDeleteLedgerService.markRetry(operation, failure);
                }
                catch (RuntimeException ledgerFailure)
                {
                    failure.addSuppressed(ledgerFailure);
                }
            }
            throw failure;
        }
    }

    private List<OaSignTaskBatchDeleteAction> normalizeHardDeleteActions(
            List<OaSignTaskBatchDeleteAction> requested)
    {
        if (requested == null || requested.isEmpty() || requested.size() > 20)
        {
            throw new ServiceException("单次必须删除1至20个签约任务");
        }
        List<OaSignTaskBatchDeleteAction> actions = new ArrayList<>();
        LinkedHashSet<Long> taskIds = new LinkedHashSet<>();
        for (OaSignTaskBatchDeleteAction requestedAction : requested)
        {
            if (requestedAction == null || requestedAction.getTaskId() == null
                    || requestedAction.getTaskId() <= 0
                    || requestedAction.getExpectedVersion() == null
                    || requestedAction.getExpectedVersion() < 0)
            {
                throw new ServiceException("签约任务编号或版本无效");
            }
            if (!taskIds.add(requestedAction.getTaskId()))
            {
                throw new ServiceException("删除请求中存在重复签约任务");
            }
            OaSignTaskBatchDeleteAction action = new OaSignTaskBatchDeleteAction();
            action.setTaskId(requestedAction.getTaskId());
            action.setExpectedVersion(requestedAction.getExpectedVersion());
            actions.add(action);
        }
        actions.sort(Comparator.comparing(OaSignTaskBatchDeleteAction::getTaskId));
        return actions;
    }

    private String hardDeletePayloadHash(List<OaSignTaskBatchDeleteAction> actions,
            Long selectedShopDeptId)
    {
        StringBuilder payload = new StringBuilder(256);
        payload.append("operation=SIGN_TASK_HARD_DELETE_V1\n")
                .append("shopScopeDeptId=").append(selectedShopDeptId).append('\n')
                .append("irreversibleConfirmed=true\n");
        for (OaSignTaskBatchDeleteAction action : actions)
        {
            payload.append("taskId=").append(action.getTaskId())
                    .append(";expectedVersion=").append(action.getExpectedVersion()).append('\n');
        }
        try
        {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.toString().getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException("SHA-256不可用", exception);
        }
    }

    private OaSignTaskBatchDeleteItem deleteFailureItem(Long taskId,
            String result, String code, String message)
    {
        OaSignTaskBatchDeleteItem item = new OaSignTaskBatchDeleteItem();
        item.setTaskId(taskId);
        item.setResult(result);
        item.setCode(code);
        item.setMessage(message);
        return item;
    }

    private void logHardDelete(String requestId, Long taskId, String result)
    {
        LOG.info("签约任务管理员硬删除: adminUserId={}, requestId={}, taskId={}, result={}",
                SecurityUtils.getUserId(), limit(requestId, 64), taskId, result);
    }

    private OaSignTaskDetail reissue(OaSignTask sourceTask, OaSignPackage sourcePackage,
            OaSignExceptionResolutionRequest request, Long selectedShopDeptId,
            String ipAddress, String userAgent)
    {
        if (request.getReplacementPlanVersionId() == null || request.getReplacementPlanVersionId() <= 0
                || request.getExtensionDays() != null
                || !(OaSignTaskStatus.REFUSED.name().equals(sourceTask.getStatus())
                || OaSignTaskStatus.EXPIRED.name().equals(sourceTask.getStatus()))
                || !(OaSignPackageStatus.REFUSED.equals(sourcePackage.getStatus())
                || OaSignPackageStatus.EXPIRED.equals(sourcePackage.getStatus()))
                || !"OPEN".equals(sourceTask.getResolutionStatus())
                || !"OPEN".equals(sourcePackage.getResolutionStatus()))
        {
            throw new ServiceException("当前签约任务不能创建替代版本");
        }
        if (!Objects.equals(sourceTask.getVersion(), request.getExpectedVersion()))
        {
            throw new ServiceException("签约任务版本已变化，请刷新后重试");
        }

        validateReplacementPlan(sourceTask, request.getReplacementPlanVersionId());
        HrRenewalGuard renewalGuard = lockIdleRenewalGuard(sourceTask);
        OaSignPackage lockedPackage = packageLifecycleService.lockForResolution(sourcePackage.getPackageId());
        if (!Objects.equals(lockedPackage.getTaskId(), sourceTask.getTaskId())
                || !Objects.equals(lockedPackage.getVersion(), sourcePackage.getVersion()))
        {
            throw new ServiceException("签约包状态已变化，请刷新后重试");
        }
        OaSignTask lockedTask = taskMapper.lockOaSignTaskById(sourceTask.getTaskId());
        if (lockedTask == null || !Objects.equals(lockedTask.getVersion(), request.getExpectedVersion())
                || !"OPEN".equals(lockedTask.getResolutionStatus())
                || !(OaSignTaskStatus.REFUSED.name().equals(lockedTask.getStatus())
                || OaSignTaskStatus.EXPIRED.name().equals(lockedTask.getStatus())))
        {
            throw new ServiceException("签约任务状态已变化，请刷新后重试");
        }
        assertInScope(lockedTask, selectedShopDeptId);
        OaSignTask replacementTask = createTask(replacementTask(lockedTask, request));
        OaSignPackage replacementPackage = packageService.createPackage(
                replacementPackageDraft(lockedPackage, replacementTask.getTaskId()), selectedShopDeptId);
        replacementTask.setPackageId(replacementPackage.getPackageId());
        replacementPackage.setTaskId(replacementTask.getTaskId());
        packageLifecycleService.markReissued(lockedTask, lockedPackage,
                replacementTask, replacementPackage, request, SecurityUtils.getUserId(),
                SecurityUtils.getUsername(), ipAddress, userAgent);
        activateReplacementRenewalGuard(lockedTask, replacementTask, renewalGuard);
        return buildDetail(replacementTask, selectedShopDeptId);
    }

    private void assertResolutionPermission(OaSignTask task, String action)
    {
        if (OaSignTaskStatus.REFUSED.name().equals(task.getStatus()))
        {
            requireExactPermission("oa:signTask:resolveRefusal");
            return;
        }
        if (OaSignTaskStatus.EXPIRED.name().equals(task.getStatus()) || "EXTEND".equals(action))
        {
            requireExactPermission("oa:signTask:resolveExpiry");
            return;
        }
        throw new ServiceException("当前签约任务不能执行异常处置");
    }

    private void requireExactPermission(String permission)
    {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (!SecurityUtils.isAdmin() && (loginUser == null || loginUser.getPermissions() == null
                || !loginUser.getPermissions().contains(permission)))
        {
            throw new NotPermissionException(permission);
        }
    }

    private void validateReplacementPlan(OaSignTask sourceTask, Long replacementPlanVersionId)
    {
        OaSignPlanVersion version = replacementPlanVersionId == null ? null
                : planVersionMapper.selectPlanVersionById(replacementPlanVersionId);
        if (version == null || !"PUBLISHED".equals(version.getPublishStatus())
                || !"ENABLED".equals(version.getMatchingStatus())
                || version.getSignDeadlineDays() == null || version.getSignDeadlineDays() < 1
                || version.getSignDeadlineDays() > 365)
        {
            throw new ServiceException("替代签约方案版本未发布、已停用或缺少期限策略");
        }
        String versionScenario = OaSignScenarioCodes.normalizeTaskScenario(version.getScenario());
        String taskScenario = OaSignScenarioCodes.normalizeTaskScenario(sourceTask.getScenario());
        if (!Objects.equals(versionScenario, taskScenario)
                || !OaSignPlanScope.appliesTo(version.getShopDeptId(), sourceTask.getShopDeptId())
                || version.getLegalEntityId() != null
                        && !Objects.equals(version.getLegalEntityId(), sourceTask.getLegalEntityId()))
        {
            throw new ServiceException("替代签约方案版本与任务场景或适用范围不一致");
        }
    }

    private HrRenewalGuard lockIdleRenewalGuard(OaSignTask sourceTask)
    {
        if (!"RENEWAL".equalsIgnoreCase(sourceTask.getScenario()))
        {
            return null;
        }
        HrRenewalGuard guard = renewalGuardMapper.selectForUpdate(sourceTask.getEmployeeId(), "RENEWAL");
        if (guard == null || guard.getVersion() == null || !"IDLE".equals(guard.getStatus())
                || guard.getActionId() != null || guard.getTaskId() != null)
        {
            throw new ServiceException("续签任务门闩未处于可创建替代版本的空闲状态");
        }
        renewalActionId(sourceTask);
        return guard;
    }

    private void activateReplacementRenewalGuard(OaSignTask sourceTask,
            OaSignTask replacementTask, HrRenewalGuard guard)
    {
        if (guard == null)
        {
            return;
        }
        if (renewalGuardMapper.activateReplacement(sourceTask.getEmployeeId(), "RENEWAL",
                renewalActionId(sourceTask), sourceTask.getTaskId(), replacementTask.getTaskId(),
                guard.getVersion()) != 1)
        {
            throw new ServiceException("续签替代任务门闩激活失败");
        }
    }

    private Long renewalActionId(OaSignTask sourceTask)
    {
        try
        {
            if (sourceTask.getSourceBusinessId() == null
                    || !sourceTask.getSourceBusinessId().matches("[1-9][0-9]*"))
            {
                throw new NumberFormatException("invalid renewal action");
            }
            return Long.valueOf(sourceTask.getSourceBusinessId());
        }
        catch (NumberFormatException exception)
        {
            throw new ServiceException("续签替代任务缺少有效生命周期业务编号");
        }
    }

    private OaSignTask replacementTask(OaSignTask source, OaSignExceptionResolutionRequest request)
    {
        OaSignTask replacement = new OaSignTask();
        replacement.setScenario(source.getScenario());
        replacement.setEmployeeId(source.getEmployeeId());
        replacement.setShopDeptId(source.getShopDeptId());
        replacement.setLegalEntityId(source.getLegalEntityId());
        replacement.setAssignedHrUserId(SecurityUtils.getUserId());
        replacement.setSourceType(source.getSourceType());
        replacement.setSourceBusinessId(source.getSourceBusinessId());
        replacement.setSourceEventVersion("REISSUE-OF-" + source.getTaskId());
        replacement.setDedupeKey("SIGN_REISSUE:" + source.getTaskId());
        replacement.setBeforeSnapshotJson(source.getBeforeSnapshotJson());
        replacement.setAfterSnapshotJson(source.getAfterSnapshotJson());
        replacement.setBusinessEffectiveDate(source.getBusinessEffectiveDate());
        replacement.setHistoricalSupplement(source.getHistoricalSupplement());
        replacement.setAutomationLevel("MANUAL");
        replacement.setRiskLevel(source.getRiskLevel());
        replacement.setPlanVersionId(request.getReplacementPlanVersionId());
        replacement.setReissueOfTaskId(source.getTaskId());
        return replacement;
    }

    private OaSignPackage replacementPackageDraft(OaSignPackage source, Long replacementTaskId)
    {
        OaSignPackage replacement = new OaSignPackage();
        replacement.setEmployeeId(source.getEmployeeId());
        replacement.setDeptIdSnapshot(source.getDeptIdSnapshot());
        replacement.setDeptNameSnapshot(source.getDeptNameSnapshot());
        replacement.setEmployeeNameSnapshot(source.getEmployeeNameSnapshot());
        replacement.setEmployeePhoneSnapshot(source.getEmployeePhoneSnapshot());
        replacement.setEmployeeIdCardSnapshot(source.getEmployeeIdCardSnapshot());
        replacement.setEmployeeAddressSnapshot(source.getEmployeeAddressSnapshot());
        replacement.setScenario(source.getScenario());
        replacement.setEmploymentType(source.getEmploymentType());
        replacement.setContractTermCodeSnapshot(source.getContractTermCodeSnapshot());
        replacement.setSocialType(source.getSocialType());
        replacement.setServicePersonType(source.getServicePersonType());
        replacement.setInsuranceType(source.getInsuranceType());
        replacement.setPostNameSnapshot(source.getPostNameSnapshot());
        replacement.setPostLevelSnapshot(source.getPostLevelSnapshot());
        replacement.setSalaryVersion(source.getSalaryVersion());
        replacement.setEntryDate(source.getEntryDate());
        replacement.setContractStartDate(source.getContractStartDate());
        replacement.setContractEndDate(source.getContractEndDate());
        replacement.setPreviousContractEndDate(source.getPreviousContractEndDate());
        replacement.setPreviousEmploymentType(source.getPreviousEmploymentType());
        replacement.setPreviousLegalEntityIdSnapshot(source.getPreviousLegalEntityIdSnapshot());
        replacement.setPreviousRenewalCount(source.getPreviousRenewalCount());
        replacement.setRenewalCount(source.getRenewalCount());
        replacement.setProbationStartDate(source.getProbationStartDate());
        replacement.setProbationEndDate(source.getProbationEndDate());
        replacement.setActualRegularizationDate(source.getActualRegularizationDate());
        replacement.setTransferEffectiveDate(source.getTransferEffectiveDate());
        replacement.setHistoricalSupplement(source.getHistoricalSupplement());
        replacement.setBeforeDeptNameSnapshot(source.getBeforeDeptNameSnapshot());
        replacement.setBeforePostNameSnapshot(source.getBeforePostNameSnapshot());
        replacement.setWorkStartDate(source.getWorkStartDate());
        replacement.setWorkEndDate(source.getWorkEndDate());
        replacement.setLeaveDate(source.getLeaveDate());
        replacement.setLeaveReason(source.getLeaveReason());
        replacement.setOffboardingType(source.getOffboardingType());
        replacement.setSalarySettlementStatus(source.getSalarySettlementStatus());
        replacement.setAssetHandoverStatus(source.getAssetHandoverStatus());
        replacement.setNonCompeteDecision(source.getNonCompeteDecision());
        replacement.setCompensationAmount(source.getCompensationAmount());
        replacement.setCompensationNote(source.getCompensationNote());
        replacement.setBaseSalary(source.getBaseSalary());
        replacement.setPostSalary(source.getPostSalary());
        replacement.setFieldAllowance(source.getFieldAllowance());
        replacement.setPerformanceSalary(source.getPerformanceSalary());
        replacement.setSalaryTotal(source.getSalaryTotal());
        replacement.setRemark(source.getRemark());
        replacement.setTaskId(replacementTaskId);
        replacement.setStatus(OaSignPackageStatus.DRAFT);
        replacement.setVersion(0L);
        return replacement;
    }

    private OaSignTask loadForHandling(Long taskId, Long selectedShopDeptId)
    {
        OaSignTask task = loadTask(taskId);
        assertInScope(task, selectedShopDeptId);
        assertCanHandle(task);
        return task;
    }

    private OaSignTaskDetail resolveRepeatedAction(OaSignTask task, String requestId,
            OaSignTaskStatus expectedTarget, Long selectedShopDeptId)
    {
        if (blank(requestId))
        {
            return null;
        }
        OaSignTaskEvent existing = eventMapper.selectEventByRequestId(requestId);
        if (existing == null)
        {
            return null;
        }
        if (!Objects.equals(task.getTaskId(), existing.getTaskId())
                || !expectedTarget.name().equals(existing.getToStatus()))
        {
            throw new ServiceException("请求编号已用于其他任务操作");
        }
        return buildDetail(task, selectedShopDeptId);
    }

    private OaSignTask loadTask(Long taskId)
    {
        OaSignTask task = taskId == null ? null : taskMapper.selectOaSignTaskById(taskId);
        if (task == null)
        {
            throw new ServiceException("签约任务不存在");
        }
        return task;
    }

    private OaSignTaskDetail buildDetail(OaSignTask task, Long selectedShopDeptId)
    {
        OaSignTaskDetail internal = buildInternalDetail(task, selectedShopDeptId);
        internal.setHasConfirmation(task.getConfirmedSnapshotHash() != null);
        internal.setBusinessActionsAllowed(true);
        internal.setTechnicalEvidenceView(false);
        OaSignTaskDetail business = OaSignResponseSanitizer.businessTaskDetail(internal);
        return business;
    }

    private OaSignTaskDetail buildInternalDetail(OaSignTask task, Long selectedShopDeptId)
    {
        OaSignTaskDetail detail = new OaSignTaskDetail();
        detail.setTask(task);
        detail.setEvents(eventMapper.selectTaskEvents(task.getTaskId()));
        OaSignNotificationOutbox latestNotification = notificationOutboxService
                .latestForTask(task.getTaskId());
        if (latestNotification != null)
        {
            detail.setLatestNotificationStatus(latestNotification.getStatus());
            detail.setLatestNotificationChannel(latestNotification.getChannel());
            detail.setLatestNotificationRetryCount(latestNotification.getRetryCount());
            detail.setLatestNotificationTime(latestNotification.getUpdatedTime());
            detail.setLatestNotificationNextRetryTime(latestNotification.getNextRetryTime());
            if ("DEAD".equals(latestNotification.getStatus()))
            {
                detail.setLatestNotificationBusinessKey(latestNotification.getBusinessKey());
            }
        }
        if (task.getPackageId() != null)
        {
            OaSignPackage signPackage = packageService.getPackageForVerification(
                    task.getPackageId(), selectedShopDeptId);
            detail.setSignPackage(signPackage);
            detail.setDocumentVersion(signPackage.getDocumentVersion());
            String snapshotHash = calculateSnapshotHash(task, signPackage);
            detail.setSnapshotHash(snapshotHash);
            detail.setConfirmationCurrent(task.getConfirmedSnapshotHash() != null
                    && task.getConfirmedSnapshotHash().equals(snapshotHash));
        }
        else
        {
            detail.setConfirmationCurrent(false);
        }
        return detail;
    }

    private OaSignPackage requirePreparedPackage(OaSignTaskDetail detail)
    {
        OaSignPackage signPackage = detail.getSignPackage();
        if (signPackage == null || blank(signPackage.getDocumentVersion())
                || signPackage.getDocuments() == null || signPackage.getDocuments().isEmpty()
                || signPackage.getDocuments().stream().anyMatch(document ->
                        blank(document.getReviewPdfHash())
                        || !Objects.equals(signPackage.getDocumentVersion(), document.getDocumentVersion())))
        {
            throw new ServiceException("签约文件尚未准备完成");
        }
        return signPackage;
    }

    private void assertFrozenSnapshot(OaSignTask task, OaSignTaskConfirmRequest request,
            OaSignPackage signPackage, String snapshotHash)
    {
        if (!Objects.equals(request.getDocumentVersion(), signPackage.getDocumentVersion()))
        {
            throw new ServiceException("文档版本已变化，请重新确认");
        }
        confirmationTokenService.verify(request.getConfirmationToken(), task.getTaskId(),
                SecurityUtils.getUserId(), signPackage.getDocumentVersion(), snapshotHash);
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
            throw new ServiceException("无权访问该门店的签约任务");
        }
    }

    private void assertCanHandle(OaSignTask task)
    {
        if (!canHandle(task))
        {
            throw new ServiceException("签约任务不存在或无权访问");
        }
    }

    private boolean canHandle(OaSignTask task)
    {
        return task != null && isCurrentHr()
                && OaSignHrAccessService.isCurrentUserTaskOwner(task);
    }

    private Long requireCurrentUserId()
    {
        Long userId = SecurityUtils.getUserId();
        if (userId == null || userId <= 0)
        {
            throw new ServiceException("无法识别当前合同经办人");
        }
        return userId;
    }

    private boolean isCurrentHr()
    {
        return SecurityUtils.isAdmin()
                || OaSignHrAccessService.hasSigningBusinessPermission(SecurityUtils.getLoginUser());
    }

    private boolean isTechnicalEvidenceReader()
    {
        if (SecurityUtils.isAdmin())
        {
            return true;
        }
        LoginUser loginUser = SecurityUtils.getLoginUser();
        return loginUser != null && loginUser.getPermissions() != null
                && loginUser.getPermissions().contains("oa:signTask:technicalEvidence");
    }

    private OaSignTaskDetail buildTechnicalDetail(OaSignTask task, Long selectedShopDeptId)
    {
        OaSignTaskDetail detail = buildInternalDetail(task, selectedShopDeptId);
        detail.setHasConfirmation(task.getConfirmedSnapshotHash() != null);
        detail.setBusinessActionsAllowed(false);
        detail.setTechnicalEvidenceView(true);
        return detail;
    }

    private void validateNewTask(OaSignTask task)
    {
        if (task == null || task.getEmployeeId() == null || blank(task.getScenario())
                || blank(task.getSourceType()) || blank(task.getSourceBusinessId())
                || blank(task.getSourceEventVersion()) || blank(task.getDedupeKey()))
        {
            throw new ServiceException("签约任务来源信息不完整");
        }
    }

    private void requireRequest(OaSignTaskRetryRequest request)
    {
        if (request == null || blank(request.getRequestId()))
        {
            throw new ServiceException("请求编号不能为空");
        }
    }

    private OaSignTaskStatus status(OaSignTask task)
    {
        try
        {
            return OaSignTaskStatus.valueOf(task.getStatus());
        }
        catch (RuntimeException ex)
        {
            throw new ServiceException("签约任务状态不正确");
        }
    }

    private void clearFailure(OaSignTask task)
    {
        task.setFailureCode(null);
        task.setFailureDetail(null);
        task.setNextRetryTime(null);
    }

    private String generateTaskNo()
    {
        return "ST" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private static Date nowToSecond()
    {
        return new Date(System.currentTimeMillis() / 1000 * 1000);
    }

    private static boolean blank(String value)
    {
        return value == null || value.isBlank();
    }

    private static String limit(String value, int max)
    {
        if (value == null || value.length() <= max)
        {
            return value;
        }
        return value.substring(0, max);
    }

    public static String calculateSnapshotHash(OaSignTask task, OaSignPackage signPackage)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, task == null ? null : task.getEmployeeId());
            update(digest, task == null ? null : task.getShopDeptId());
            update(digest, task == null ? null : task.getLegalEntityId());
            update(digest, task == null ? null : task.getScenario());
            update(digest, task == null ? null : task.getPlanVersionId());
            if (signPackage != null)
            {
                update(digest, signPackage.getPackageId());
                update(digest, signPackage.getEmployeeId());
                update(digest, signPackage.getEmployeeNameSnapshot());
                update(digest, signPackage.getEmployeePhoneSnapshot());
                update(digest, signPackage.getEmployeeIdCardSnapshot());
                update(digest, signPackage.getEmployeeAddressSnapshot());
                update(digest, signPackage.getDeptIdSnapshot());
                update(digest, signPackage.getDeptNameSnapshot());
                update(digest, signPackage.getShopDeptId());
                update(digest, signPackage.getSourcePlanId());
                update(digest, signPackage.getPlanVersionId());
                update(digest, signPackage.getScenario());
                update(digest, signPackage.getEmploymentType());
                if (!blank(signPackage.getContractTermCodeSnapshot()))
                {
                    // Preserve every pre-migration confirmation hash when the new column is NULL.
                    update(digest, "contractTermCodeSnapshot");
                    update(digest, signPackage.getContractTermCodeSnapshot());
                }
                update(digest, signPackage.getSocialType());
                update(digest, signPackage.getServicePersonType());
                update(digest, signPackage.getInsuranceType());
                update(digest, signPackage.getPostNameSnapshot());
                update(digest, signPackage.getPostLevelSnapshot());
                update(digest, signPackage.getSalaryVersion());
                update(digest, signPackage.getEntryDate());
                update(digest, signPackage.getContractStartDate());
                update(digest, signPackage.getContractEndDate());
                update(digest, signPackage.getProbationStartDate());
                update(digest, signPackage.getProbationEndDate());
                update(digest, signPackage.getActualRegularizationDate());
                update(digest, signPackage.getWorkStartDate());
                update(digest, signPackage.getWorkEndDate());
                update(digest, signPackage.getLeaveDate());
                update(digest, signPackage.getLeaveReason());
                update(digest, signPackage.getBaseSalary());
                update(digest, signPackage.getPostSalary());
                update(digest, signPackage.getFieldAllowance());
                update(digest, signPackage.getPerformanceSalary());
                update(digest, signPackage.getSalaryTotal());
                update(digest, signPackage.getDocumentVersion());
                update(digest, signPackage.getSignDeadline() == null
                        ? null : signPackage.getSignDeadline().getTime());
                List<OaSignPackageDocument> documents = signPackage.getDocuments() == null
                        ? new ArrayList<>() : new ArrayList<>(signPackage.getDocuments());
                documents.sort(Comparator.comparing(OaSignPackageDocument::getDocumentId,
                        Comparator.nullsLast(Long::compareTo)));
                for (OaSignPackageDocument document : documents)
                {
                    update(digest, document.getDocumentId());
                    update(digest, document.getTemplateId());
                    update(digest, document.getTemplateType());
                    update(digest, document.getDocumentName());
                    update(digest, document.getTemplateVersionSnapshot());
                    update(digest, document.getEmployeeSignRequired());
                    update(digest, document.getSortOrder());
                    update(digest, document.getDocumentVersion());
                    update(digest, document.getReviewPdfHash());
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        }
        catch (NoSuchAlgorithmException ex)
        {
            throw new IllegalStateException("SHA-256不可用", ex);
        }
    }

    private static void update(MessageDigest digest, Object value)
    {
        String canonical = value == null ? "" : String.valueOf(value);
        byte[] bytes = canonical.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }
}
