package com.erp.approval.service;

import static com.erp.approval.constant.ApprovalRuntimeConstants.ACTION_CALLBACK;
import static com.erp.approval.constant.ApprovalRuntimeConstants.ACTION_CALLBACK_REPLAY;
import static com.erp.approval.constant.ApprovalRuntimeConstants.CALLBACK_DEAD;
import static com.erp.approval.constant.ApprovalRuntimeConstants.CALLBACK_PENDING;
import static com.erp.approval.constant.ApprovalRuntimeConstants.CALLBACK_RETRY;
import static com.erp.approval.constant.ApprovalRuntimeConstants.CALLBACK_SUCCEEDED;
import static com.erp.approval.constant.ApprovalRuntimeConstants.INSTANCE_APPROVED;
import static com.erp.approval.constant.ApprovalRuntimeConstants.INSTANCE_COMPLETING;
import static com.erp.approval.constant.ApprovalRuntimeConstants.INSTANCE_INVALIDATED;
import static com.erp.approval.constant.ApprovalRuntimeConstants.INSTANCE_REJECTING;
import static com.erp.approval.constant.ApprovalRuntimeConstants.INSTANCE_REJECTED;
import static com.erp.approval.constant.ApprovalRuntimeConstants.INSTANCE_RETURNING;
import static com.erp.approval.constant.ApprovalRuntimeConstants.INSTANCE_RETURNED;
import static com.erp.approval.constant.ApprovalRuntimeConstants.INSTANCE_TERMINATING;
import static com.erp.approval.constant.ApprovalRuntimeConstants.INSTANCE_TERMINATED;
import static com.erp.approval.constant.ApprovalRuntimeConstants.INSTANCE_WITHDRAWING;
import static com.erp.approval.constant.ApprovalRuntimeConstants.INSTANCE_WITHDRAWN;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.approval.callback.ApprovalBusinessCallbackResult;
import com.erp.approval.domain.ApprovalActionLog;
import com.erp.approval.domain.ApprovalCallbackOutbox;
import com.erp.approval.domain.ApprovalInstance;
import com.erp.approval.domain.dto.ApprovalCallbackReplayRequest;
import com.erp.approval.mapper.ApprovalRuntimeMapper;
import com.erp.common.core.exception.ServiceException;

@Service
public class ApprovalCallbackOutboxService
{
    private final ApprovalRuntimeMapper runtimeMapper;
    private final ApprovalRuntimeService runtimeService;

    public ApprovalCallbackOutboxService(ApprovalRuntimeMapper runtimeMapper,
            ApprovalRuntimeService runtimeService)
    {
        this.runtimeMapper = runtimeMapper;
        this.runtimeService = runtimeService;
    }

    public List<ApprovalCallbackOutbox> dispatchable(int limit)
    {
        return runtimeMapper.selectDispatchableCallbacks(Math.max(1,
                Math.min(limit, 100)));
    }

    @Transactional
    public ApprovalCallbackOutbox claim(Long outboxId, Long expectedVersion,
            String worker, Date lockUntil)
    {
        if (runtimeMapper.claimCallback(outboxId, expectedVersion, worker,
                lockUntil) != 1)
        {
            return null;
        }
        return runtimeMapper.selectCallbackById(outboxId);
    }

    @Transactional
    public void succeed(Long outboxId, Long expectedVersion,
            ApprovalBusinessCallbackResult result, String worker)
    {
        if (result == null || !result.success())
        {
            throw new ServiceException("不能以失败结果完成审批回调");
        }
        ApprovalCallbackOutbox outbox = requireOutbox(outboxId);
        if (!"PROCESSING".equals(outbox.getCallbackStatus())
                || !expectedVersion.equals(outbox.getLockVersion()))
        {
            throw ApprovalRuntimeService.concurrent();
        }
        ApprovalInstance instance = runtimeService.requireInstance(
                outbox.getInstanceId());
        String before = instance.getStatus();
        String expectedIntermediate = intermediateStatus(
                outbox.getCallbackAction());
        if (!expectedIntermediate.equals(before))
        {
            throw new ServiceException("审批回调动作与实例中间状态不匹配");
        }
        if (result.invalidated()
                && !"APPROVE".equals(outbox.getCallbackAction()))
        {
            throw new ServiceException("仅最终同意回调可标记业务失效");
        }
        Long instanceLock = instance.getLockVersion();
        instance.setStatus(result.invalidated() ? INSTANCE_INVALIDATED
                : finalStatus(outbox.getCallbackAction()));
        instance.setCallbackStatus(CALLBACK_SUCCEEDED);
        instance.setFinishedTime(new Date());
        instance.setUpdateBy(worker);
        if (runtimeMapper.updateInstanceWithLock(instance, instanceLock,
                before) != 1)
        {
            throw ApprovalRuntimeService.concurrent();
        }
        if (runtimeMapper.markCallbackSucceeded(outboxId, expectedVersion,
                worker) != 1)
        {
            throw ApprovalRuntimeService.concurrent();
        }
        runtimeService.insertAction(instance, null, null, ACTION_CALLBACK,
                null, "CALLBACK", before, instance.getStatus(),
                result.message(), outbox.getEventKey(), worker);
    }

    @Transactional
    public void fail(Long outboxId, Long expectedVersion, String errorCode,
            String errorMessage, Date nextRetryTime, String worker)
    {
        ApprovalCallbackOutbox outbox = requireOutbox(outboxId);
        if (!"PROCESSING".equals(outbox.getCallbackStatus())
                || !expectedVersion.equals(outbox.getLockVersion()))
        {
            return;
        }
        int retry = (outbox.getRetryCount() == null ? 0
                : outbox.getRetryCount()) + 1;
        int max = outbox.getMaxRetryCount() == null ? 10
                : outbox.getMaxRetryCount();
        String status = retry >= max ? CALLBACK_DEAD : CALLBACK_RETRY;
        if (runtimeMapper.markCallbackFailure(outboxId, expectedVersion,
                status, retry, retry >= max ? null : nextRetryTime,
                truncate(errorCode, 64), truncate(errorMessage, 500), worker) != 1)
        {
            throw ApprovalRuntimeService.concurrent();
        }
        ApprovalInstance instance = runtimeService.requireInstance(
                outbox.getInstanceId());
        String before = instance.getStatus();
        Long instanceLock = instance.getLockVersion();
        instance.setCallbackStatus(retry >= max ? CALLBACK_DEAD : "FAILED");
        instance.setUpdateBy(worker);
        if (runtimeMapper.updateInstanceWithLock(instance, instanceLock,
                before) != 1)
        {
            throw ApprovalRuntimeService.concurrent();
        }
    }

    @Transactional
    public ApprovalCallbackOutbox replay(Long outboxId,
            ApprovalCallbackReplayRequest request, Long operatorId,
            String operatorName)
    {
        ApprovalCallbackOutbox outbox = requireOutbox(outboxId);
        String replayRequestId = outboxId + ":" + request.getRequestId();
        String actionKey = ApprovalRuntimeService.actionKey(outbox.getInstanceId(),
                ACTION_CALLBACK_REPLAY, replayRequestId);
        ApprovalActionLog existing = runtimeMapper.selectActionByKey(actionKey);
        if (existing != null)
        {
            if (!ACTION_CALLBACK_REPLAY.equals(existing.getActionType())
                    || !Objects.equals(existing.getOperatorUserId(),
                            operatorId)
                    || !Objects.equals(existing.getActionReason(),
                            request.getReason()))
            {
                throw new ServiceException("请求ID已被其他回调重放操作使用");
            }
            return requireOutbox(outboxId);
        }
        if (!List.of(CALLBACK_DEAD, CALLBACK_RETRY)
                .contains(outbox.getCallbackStatus()))
        {
            throw new ServiceException("只能重放死信或待重试回调");
        }
        if (runtimeMapper.replayCallback(outboxId, outbox.getLockVersion(),
                operatorName) != 1)
        {
            throw ApprovalRuntimeService.concurrent();
        }
        ApprovalInstance instance = runtimeService.requireInstance(
                outbox.getInstanceId());
        String before = instance.getStatus();
        Long instanceLock = instance.getLockVersion();
        instance.setCallbackStatus(CALLBACK_PENDING);
        instance.setUpdateBy(operatorName);
        if (runtimeMapper.updateInstanceWithLock(instance, instanceLock,
                before) != 1)
        {
            throw ApprovalRuntimeService.concurrent();
        }
        runtimeService.insertAction(instance, null, null,
                ACTION_CALLBACK_REPLAY, operatorId, "ADMIN",
                outbox.getCallbackStatus(), CALLBACK_PENDING,
                request.getReason(), replayRequestId, operatorName);
        return requireOutbox(outboxId);
    }

    private ApprovalCallbackOutbox requireOutbox(Long id)
    {
        ApprovalCallbackOutbox value = runtimeMapper.selectCallbackById(id);
        if (value == null) throw new ServiceException("审批回调事件不存在");
        return value;
    }

    private String finalStatus(String action)
    {
        return switch (action)
        {
            case "APPROVE" -> INSTANCE_APPROVED;
            case "RETURN" -> INSTANCE_RETURNED;
            case "REJECT" -> INSTANCE_REJECTED;
            case "WITHDRAW" -> INSTANCE_WITHDRAWN;
            case "TERMINATE" -> INSTANCE_TERMINATED;
            default -> throw new ServiceException("未知回调动作: " + action);
        };
    }

    private String intermediateStatus(String action)
    {
        return switch (action)
        {
            case "APPROVE" -> INSTANCE_COMPLETING;
            case "RETURN" -> INSTANCE_RETURNING;
            case "REJECT" -> INSTANCE_REJECTING;
            case "WITHDRAW" -> INSTANCE_WITHDRAWING;
            case "TERMINATE" -> INSTANCE_TERMINATING;
            default -> throw new ServiceException("未知回调动作: " + action);
        };
    }

    private static String truncate(String value, int max)
    {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max);
    }
}
