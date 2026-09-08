package com.erp.approval.service;

import static com.erp.approval.constant.ApprovalDefinitionConstants.APPROVAL_ALL;
import static com.erp.approval.constant.ApprovalDefinitionConstants.APPROVAL_ANY_ONE;
import static com.erp.approval.constant.ApprovalDefinitionConstants.APPROVAL_UNIQUE_BEST;
import static com.erp.approval.constant.ApprovalRuntimeConstants.ACTION_APPROVE;
import static com.erp.approval.constant.ApprovalRuntimeConstants.ACTION_REASSIGN;
import static com.erp.approval.constant.ApprovalRuntimeConstants.ACTION_REJECT;
import static com.erp.approval.constant.ApprovalRuntimeConstants.ACTION_RETURN;
import static com.erp.approval.constant.ApprovalRuntimeConstants.CALLBACK_PENDING;
import static com.erp.approval.constant.ApprovalRuntimeConstants.CANDIDATE_APPROVED;
import static com.erp.approval.constant.ApprovalRuntimeConstants.CANDIDATE_PENDING;
import static com.erp.approval.constant.ApprovalRuntimeConstants.CANDIDATE_REASSIGNED;
import static com.erp.approval.constant.ApprovalRuntimeConstants.CANDIDATE_REJECTED;
import static com.erp.approval.constant.ApprovalRuntimeConstants.CANDIDATE_RETURNED;
import static com.erp.approval.constant.ApprovalRuntimeConstants.INSTANCE_COMPLETING;
import static com.erp.approval.constant.ApprovalRuntimeConstants.INSTANCE_REJECTING;
import static com.erp.approval.constant.ApprovalRuntimeConstants.INSTANCE_RETURNING;
import static com.erp.approval.constant.ApprovalRuntimeConstants.INSTANCE_RUNNING;
import static com.erp.approval.constant.ApprovalRuntimeConstants.TASK_APPROVED;
import static com.erp.approval.constant.ApprovalRuntimeConstants.TASK_PENDING;
import static com.erp.approval.constant.ApprovalRuntimeConstants.TASK_REJECTED;
import static com.erp.approval.constant.ApprovalRuntimeConstants.TASK_RETURNED;
import static com.erp.approval.constant.ApprovalRuntimeConstants.TASK_WAITING;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.approval.candidate.ApprovalDirectoryUser;
import com.erp.approval.domain.ApprovalActionLog;
import com.erp.approval.domain.ApprovalInstance;
import com.erp.approval.domain.ApprovalTask;
import com.erp.approval.domain.ApprovalTaskCandidate;
import com.erp.approval.domain.ApprovalTemplate;
import com.erp.approval.domain.ApprovalVersionNode;
import com.erp.approval.domain.dto.ApprovalReassignRequest;
import com.erp.approval.domain.dto.ApprovalTaskActionRequest;
import com.erp.approval.guard.ApprovalBusinessActionGuardRegistry;
import com.erp.approval.mapper.ApprovalCandidateDirectoryMapper;
import com.erp.approval.mapper.ApprovalDefinitionMapper;
import com.erp.approval.mapper.ApprovalRuntimeMapper;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.auth.AuthUtil;

@Service
public class ApprovalTaskService
{
    private final ApprovalRuntimeMapper runtimeMapper;
    private final ApprovalDefinitionMapper definitionMapper;
    private final ApprovalCandidateDirectoryMapper directoryMapper;
    private final ApprovalPermissionPolicy permissionPolicy;
    private final ApprovalRuntimeService runtimeService;
    private final ApprovalBusinessActionGuardRegistry actionGuards;

    public ApprovalTaskService(ApprovalRuntimeMapper runtimeMapper,
            ApprovalDefinitionMapper definitionMapper,
            ApprovalCandidateDirectoryMapper directoryMapper,
            ApprovalPermissionPolicy permissionPolicy,
            ApprovalRuntimeService runtimeService,
            ApprovalBusinessActionGuardRegistry actionGuards)
    {
        this.runtimeMapper = runtimeMapper;
        this.definitionMapper = definitionMapper;
        this.directoryMapper = directoryMapper;
        this.permissionPolicy = permissionPolicy;
        this.runtimeService = runtimeService;
        this.actionGuards = actionGuards;
    }

    @Transactional
    public ApprovalInstance approve(Long taskId,
            ApprovalTaskActionRequest request, Long operatorId,
            String operatorName)
    {
        request.setReason(optionalReason(request.getReason()));
        ApprovalTask task = requireTask(taskId);
        ApprovalInstance instance = runtimeService.requireInstance(
                task.getInstanceId());
        String actionKey = ApprovalRuntimeService.actionKey(
                instance.getInstanceId(), ACTION_APPROVE, request.getRequestId());
        ApprovalActionLog existing = runtimeMapper.selectActionByKey(actionKey);
        if (existing != null)
        {
            requireSameAction(existing, taskId, operatorId, ACTION_APPROVE,
                    request.getReason());
            return runtimeService.requireInstance(instance.getInstanceId());
        }
        requirePending(task, instance);
        ApprovalTaskCandidate candidate = requireCandidate(taskId, operatorId);
        requireCurrentApprovalPermission(instance, candidate, operatorId);
        actionGuards.validate(instance, ACTION_APPROVE, operatorId);

        int completed = (task.getCompletedCount() == null ? 0
                : task.getCompletedCount()) + 1;
        boolean complete = APPROVAL_UNIQUE_BEST.equals(task.getApprovalMode())
                || APPROVAL_ANY_ONE.equals(task.getApprovalMode())
                || (APPROVAL_ALL.equals(task.getApprovalMode())
                    && completed >= task.getRequiredCount());
        if (!claimTaskAction(instance, task, candidate, ACTION_APPROVE,
                operatorId, "USER", TASK_PENDING,
                complete ? TASK_APPROVED : TASK_PENDING,
                request.getReason(), request.getRequestId(), operatorName))
        {
            return requireCurrentInstance(instance.getInstanceId());
        }

        Long candidateLock = candidate.getLockVersion();
        candidate.setCandidateStatus(CANDIDATE_APPROVED);
        candidate.setCandidateReason(request.getReason());
        candidate.setActedTime(new Date());
        candidate.setUpdateBy(operatorName);
        if (runtimeMapper.updateCandidateWithLock(candidate, candidateLock,
                CANDIDATE_PENDING) != 1)
        {
            throw ApprovalRuntimeService.concurrent();
        }

        Long taskLock = task.getLockVersion();
        task.setCompletedCount(completed);
        task.setTaskStatus(complete ? TASK_APPROVED : TASK_PENDING);
        task.setCompletedTime(complete ? new Date() : null);
        task.setUpdateBy(operatorName);
        if (runtimeMapper.updateTaskWithLock(task, taskLock, TASK_PENDING) != 1)
        {
            throw ApprovalRuntimeService.concurrent();
        }
        if (complete)
        {
            runtimeMapper.cancelOtherCandidates(taskId, candidate.getCandidateId(),
                    "当前节点已完成", operatorName);
        }

        String before = instance.getStatus();
        Long instanceLock = instance.getLockVersion();
        instance.setApprovedActionCount((instance.getApprovedActionCount() == null
                ? 0 : instance.getApprovedActionCount()) + 1);
        ApprovalTask next = null;
        if (complete)
        {
            next = runtimeMapper.selectNextWaitingTask(instance.getInstanceId(),
                    task.getNodeOrder());
            if (next != null)
            {
                Long nextLock = next.getLockVersion();
                next.setTaskStatus(TASK_PENDING);
                next.setActivatedTime(new Date());
                next.setUpdateBy(operatorName);
                if (runtimeMapper.updateTaskWithLock(next, nextLock,
                        TASK_WAITING) != 1)
                {
                    throw ApprovalRuntimeService.concurrent();
                }
                instance.setCurrentNodeId(next.getNodeId());
                instance.setCurrentNodeOrder(next.getNodeOrder());
            }
            else
            {
                instance.setStatus(INSTANCE_COMPLETING);
                instance.setCurrentNodeId(null);
                instance.setCurrentNodeOrder(null);
                instance.setCallbackStatus(CALLBACK_PENDING);
            }
        }
        instance.setUpdateBy(operatorName);
        if (runtimeMapper.updateInstanceWithLock(instance, instanceLock,
                before) != 1)
        {
            throw ApprovalRuntimeService.concurrent();
        }
        if (complete && next == null)
        {
            ApprovalTemplate template = runtimeService.requireTemplate(
                    instance.getTemplateId());
            runtimeService.createOutbox(instance, template, "APPROVE",
                    "APPROVED", request.getReason(), operatorId, operatorName);
        }
        return instance;
    }

    @Transactional
    public ApprovalInstance returnForModification(Long taskId,
            ApprovalTaskActionRequest request, Long operatorId,
            String operatorName)
    {
        request.setReason(requiredReason(request.getReason(), "退回"));
        return close(taskId, request, operatorId, operatorName,
                ACTION_RETURN, TASK_RETURNED, CANDIDATE_RETURNED,
                INSTANCE_RETURNING, "RETURN", "RETURNED", true);
    }

    @Transactional
    public ApprovalInstance reject(Long taskId, ApprovalTaskActionRequest request,
            Long operatorId, String operatorName)
    {
        request.setReason(requiredReason(request.getReason(), "拒绝"));
        return close(taskId, request, operatorId, operatorName,
                ACTION_REJECT, TASK_REJECTED, CANDIDATE_REJECTED,
                INSTANCE_REJECTING, "REJECT", "REJECTED", false);
    }

    @Transactional
    public ApprovalTaskCandidate reassign(Long taskId,
            ApprovalReassignRequest request, Long operatorId,
            String operatorName)
    {
        request.setReason(requiredReason(request.getReason(), "改派"));
        ApprovalTask task = requireTask(taskId);
        ApprovalInstance instance = runtimeService.requireInstance(
                task.getInstanceId());
        String key = ApprovalRuntimeService.actionKey(instance.getInstanceId(),
                ACTION_REASSIGN, request.getRequestId());
        ApprovalActionLog existing = runtimeMapper.selectActionByKey(key);
        if (existing != null)
        {
            requireSameReassignAction(existing, taskId, request, operatorId);
            return requireCurrentReassignedCandidate(taskId, request);
        }
        requirePending(task, instance);
        ApprovalTaskCandidate from = runtimeMapper.selectCandidateById(
                request.getFromCandidateId());
        if (from == null || !Objects.equals(from.getTaskId(), taskId)
                || !CANDIDATE_PENDING.equals(from.getCandidateStatus()))
        {
            throw new ServiceException("改派源候选人不是当前待办候选人");
        }
        if (runtimeMapper.selectCandidatesByTaskId(taskId).stream()
                .anyMatch(item -> Objects.equals(item.getUserId(),
                        request.getToUserId())))
        {
            throw new ServiceException("新审批人已在该任务候选人快照中");
        }
        String permission = candidatePermission(instance, from);
        ApprovalDirectoryUser user = directoryMapper.selectActiveUserById(
                request.getToUserId(), permission);
        if (user == null)
        {
            throw new ServiceException("新审批人已停用、已离职或无业务审批权限");
        }
        if (!claimReassignAction(instance, task, from, request, operatorId,
                operatorName))
        {
            return requireCurrentReassignedCandidate(taskId, request);
        }
        Long fromLock = from.getLockVersion();
        from.setCandidateStatus(CANDIDATE_REASSIGNED);
        from.setCandidateReason(request.getReason());
        from.setActedTime(new Date());
        from.setUpdateBy(operatorName);
        if (runtimeMapper.updateCandidateWithLock(from, fromLock,
                CANDIDATE_PENDING) != 1)
        {
            throw ApprovalRuntimeService.concurrent();
        }
        ApprovalTaskCandidate target = new ApprovalTaskCandidate();
        target.setTaskId(taskId);
        target.setInstanceId(instance.getInstanceId());
        target.setUserId(user.getUserId());
        target.setUserName(user.getUserName());
        target.setDeptId(user.getDeptId());
        target.setDeptName(user.getDeptName());
        target.setCandidateSourceType("ADMIN_REASSIGN");
        target.setCandidateSourceCode(permission);
        target.setCandidateOrder(runtimeMapper.selectCandidatesByTaskId(taskId)
                .size() + 1);
        target.setCandidateStatus(CANDIDATE_PENDING);
        target.setCandidateReason(request.getReason());
        target.setReassignedFromCandidateId(from.getCandidateId());
        target.setCreateBy(operatorName);
        try
        {
            runtimeMapper.insertCandidate(target);
        }
        catch (DuplicateKeyException exception)
        {
            throw new ServiceException("新审批人已在当前任务中");
        }
        return target;
    }

    private static String requiredReason(String reason, String action)
    {
        String normalized = optionalReason(reason);
        if (normalized == null)
        {
            throw new ServiceException(action + "原因不能为空");
        }
        return normalized;
    }

    private static String optionalReason(String reason)
    {
        if (reason == null || reason.trim().isEmpty())
        {
            return null;
        }
        String normalized = reason.trim();
        if (normalized.length() > 500)
        {
            throw new ServiceException("审批意见不能超过500个字符");
        }
        return normalized;
    }

    private ApprovalInstance close(Long taskId, ApprovalTaskActionRequest request,
            Long operatorId, String operatorName, String actionType,
            String taskStatus, String candidateStatus, String intermediateStatus,
            String callbackAction, String targetStatus, boolean returning)
    {
        ApprovalTask task = requireTask(taskId);
        ApprovalInstance instance = runtimeService.requireInstance(
                task.getInstanceId());
        String actionKey = ApprovalRuntimeService.actionKey(
                instance.getInstanceId(), actionType, request.getRequestId());
        ApprovalActionLog existing = runtimeMapper.selectActionByKey(actionKey);
        if (existing != null)
        {
            requireSameAction(existing, taskId, operatorId, actionType,
                    request.getReason());
            return runtimeService.requireInstance(instance.getInstanceId());
        }
        requirePending(task, instance);
        ApprovalVersionNode node = definitionMapper.selectNodeById(task.getNodeId());
        if (node == null)
        {
            throw new ServiceException("审批节点快照不存在");
        }
        if (returning && !"1".equals(node.getReturnAllowed()))
        {
            throw new ServiceException("当前节点不允许退回修改");
        }
        if (!returning && !"1".equals(node.getRejectAllowed()))
        {
            throw new ServiceException("当前节点不允许拒绝");
        }
        ApprovalTaskCandidate candidate = requireCandidate(taskId, operatorId);
        requireCurrentApprovalPermission(instance, candidate, operatorId);
        if (!claimTaskAction(instance, task, candidate, actionType,
                operatorId, "USER", TASK_PENDING, taskStatus,
                request.getReason(), request.getRequestId(), operatorName))
        {
            return requireCurrentInstance(instance.getInstanceId());
        }
        Long candidateLock = candidate.getLockVersion();
        candidate.setCandidateStatus(candidateStatus);
        candidate.setCandidateReason(request.getReason());
        candidate.setActedTime(new Date());
        candidate.setUpdateBy(operatorName);
        if (runtimeMapper.updateCandidateWithLock(candidate, candidateLock,
                CANDIDATE_PENDING) != 1)
        {
            throw ApprovalRuntimeService.concurrent();
        }
        Long taskLock = task.getLockVersion();
        task.setTaskStatus(taskStatus);
        task.setCompletedTime(new Date());
        task.setUpdateBy(operatorName);
        if (runtimeMapper.updateTaskWithLock(task, taskLock, TASK_PENDING) != 1)
        {
            throw ApprovalRuntimeService.concurrent();
        }
        runtimeMapper.cancelOtherCandidates(taskId, candidate.getCandidateId(),
                "当前节点已" + (returning ? "退回" : "拒绝"),
                operatorName);
        runtimeMapper.cancelOpenCandidatesByInstance(instance.getInstanceId(),
                "审批已" + (returning ? "退回" : "拒绝"),
                operatorName);
        runtimeMapper.cancelOpenTasksByInstance(instance.getInstanceId(),
                "审批已" + (returning ? "退回" : "拒绝"),
                operatorName);
        String before = instance.getStatus();
        Long instanceLock = instance.getLockVersion();
        instance.setStatus(intermediateStatus);
        instance.setCurrentNodeId(null);
        instance.setCurrentNodeOrder(null);
        instance.setCallbackStatus(CALLBACK_PENDING);
        instance.setUpdateBy(operatorName);
        if (runtimeMapper.updateInstanceWithLock(instance, instanceLock,
                before) != 1)
        {
            throw ApprovalRuntimeService.concurrent();
        }
        ApprovalTemplate template = runtimeService.requireTemplate(
                instance.getTemplateId());
        runtimeService.createOutbox(instance, template, callbackAction,
                targetStatus, request.getReason(), operatorId, operatorName);
        return instance;
    }

    private ApprovalTask requireTask(Long taskId)
    {
        ApprovalTask task = runtimeMapper.selectTaskById(taskId);
        if (task == null) throw new ServiceException("审批任务不存在");
        return task;
    }

    private ApprovalTaskCandidate requireCandidate(Long taskId, Long userId)
    {
        ApprovalTaskCandidate candidate = runtimeMapper.selectPendingCandidate(
                taskId, userId);
        if (candidate == null)
        {
            throw new ServiceException("您不是当前审批任务候选人，或任务已处理");
        }
        return candidate;
    }

    private void requireCurrentApprovalPermission(ApprovalInstance instance,
            ApprovalTaskCandidate candidate, Long operatorId)
    {
        String permission = candidatePermission(instance, candidate);
        // Keep the normal login-session permission model, then verify the
        // active directory assignment as well. The latter intentionally
        // prevents the administrator wildcard and a stale token from
        // bypassing a revoked business approval permission.
        AuthUtil.checkPermi(permission);
        if (directoryMapper.selectActiveUserById(operatorId, permission) == null)
        {
            throw new ServiceException(
                    "当前账号已停用、离职或业务审批权限已发生变化");
        }
    }

    private String candidatePermission(ApprovalInstance instance,
            ApprovalTaskCandidate candidate)
    {
        String sourcePermission = candidate == null
                ? null : candidate.getCandidateSourceCode();
        if (sourcePermission != null
                && permissionPolicy.isCandidatePermissionAllowed(
                        instance.getBusinessCode(), sourcePermission))
        {
            return sourcePermission;
        }
        return permissionPolicy.requiredPermission(
                instance.getBusinessCode());
    }

    private void requirePending(ApprovalTask task, ApprovalInstance instance)
    {
        if (!TASK_PENDING.equals(task.getTaskStatus())
                || !INSTANCE_RUNNING.equals(instance.getStatus())
                || !Objects.equals(instance.getCurrentNodeId(), task.getNodeId()))
        {
            throw new ServiceException("当前任务已处理或不是活动节点");
        }
    }

    private void requireSameAction(ApprovalActionLog existing, Long taskId,
            Long operatorId, String actionType, String reason)
    {
        if (!Objects.equals(existing.getTaskId(), taskId)
                || !Objects.equals(existing.getOperatorUserId(), operatorId)
                || !Objects.equals(existing.getActionType(), actionType)
                || !Objects.equals(existing.getActionReason(), reason))
        {
            throw new ServiceException("请求ID已被其他审批内容使用");
        }
    }

    /**
     * The immutable action row is also the transaction-local idempotency
     * reservation. Its unique action_key serializes equal request IDs before
     * any mutable task state is changed. A duplicate insert only returns after
     * the winning transaction commits, so locking reads below observe the
     * winner even under MySQL REPEATABLE READ.
     */
    private boolean claimTaskAction(ApprovalInstance instance,
            ApprovalTask task, ApprovalTaskCandidate candidate,
            String actionType, Long operatorId, String source,
            String before, String after, String reason, String requestId,
            String operatorName)
    {
        try
        {
            runtimeService.insertAction(instance, task, candidate, actionType,
                    operatorId, source, before, after, reason, requestId,
                    operatorName);
            return true;
        }
        catch (DuplicateKeyException exception)
        {
            String key = ApprovalRuntimeService.actionKey(
                    instance.getInstanceId(), actionType, requestId);
            ApprovalActionLog raced = runtimeMapper.selectActionByKeyForShare(
                    key);
            if (raced == null)
            {
                throw ApprovalRuntimeService.concurrent();
            }
            requireSameAction(raced, task.getTaskId(), operatorId, actionType,
                    reason);
            return false;
        }
    }

    private boolean claimReassignAction(ApprovalInstance instance,
            ApprovalTask task, ApprovalTaskCandidate from,
            ApprovalReassignRequest request, Long operatorId,
            String operatorName)
    {
        try
        {
            runtimeService.insertAction(instance, task, from, ACTION_REASSIGN,
                    operatorId, "ADMIN", CANDIDATE_PENDING,
                    CANDIDATE_REASSIGNED, request.getReason(),
                    request.getRequestId(), operatorName);
            return true;
        }
        catch (DuplicateKeyException exception)
        {
            String key = ApprovalRuntimeService.actionKey(
                    instance.getInstanceId(), ACTION_REASSIGN,
                    request.getRequestId());
            ApprovalActionLog raced = runtimeMapper.selectActionByKeyForShare(
                    key);
            if (raced == null)
            {
                throw ApprovalRuntimeService.concurrent();
            }
            requireSameReassignAction(raced, task.getTaskId(), request,
                    operatorId);
            return false;
        }
    }

    private void requireSameReassignAction(ApprovalActionLog existing,
            Long taskId, ApprovalReassignRequest request, Long operatorId)
    {
        if (!ACTION_REASSIGN.equals(existing.getActionType())
                || !Objects.equals(existing.getTaskId(), taskId)
                || !Objects.equals(existing.getCandidateId(),
                        request.getFromCandidateId())
                || !Objects.equals(existing.getOperatorUserId(), operatorId)
                || !Objects.equals(existing.getActionReason(),
                        request.getReason()))
        {
            throw new ServiceException("请求ID已被其他改派内容使用");
        }
    }

    private ApprovalInstance requireCurrentInstance(Long instanceId)
    {
        ApprovalInstance current = runtimeMapper.selectInstanceByIdForShare(
                instanceId);
        if (current == null)
        {
            throw new ServiceException("审批实例不存在");
        }
        return current;
    }

    private ApprovalTaskCandidate requireCurrentReassignedCandidate(
            Long taskId, ApprovalReassignRequest request)
    {
        ApprovalTaskCandidate target = runtimeMapper
                .selectReassignedCandidateForShare(taskId,
                        request.getFromCandidateId(), request.getToUserId());
        if (target == null)
        {
            throw new ServiceException("请求ID已被其他改派内容使用");
        }
        return target;
    }
}
