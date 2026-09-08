package com.erp.oa.service.impl;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HexFormat;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.api.domain.HrRenewalGuard;
import com.erp.oa.constant.OaSignOperatorType;
import com.erp.oa.constant.OaSignTaskStatus;
import com.erp.oa.domain.OaSignTask;
import com.erp.oa.domain.OaSignTaskEvent;
import com.erp.oa.mapper.OaSignTaskEventMapper;
import com.erp.oa.mapper.OaHrRenewalGuardMapper;
import com.erp.oa.mapper.OaSignTaskMapper;
import com.erp.oa.service.OaSignTaskStateMachine;

@Service
public class OaSignTaskEventService
{
    private final OaSignTaskMapper taskMapper;
    private final OaSignTaskEventMapper eventMapper;
    private final OaHrRenewalGuardMapper renewalGuardMapper;
    private final OaSignTaskStateMachine stateMachine;

    public OaSignTaskEventService(OaSignTaskMapper taskMapper,
            OaSignTaskEventMapper eventMapper,
            OaHrRenewalGuardMapper renewalGuardMapper,
            OaSignTaskStateMachine stateMachine)
    {
        this.taskMapper = taskMapper;
        this.eventMapper = eventMapper;
        this.renewalGuardMapper = renewalGuardMapper;
        this.stateMachine = stateMachine;
    }

    @Transactional(rollbackFor = Exception.class)
    public OaSignTask transition(OaSignTask current,
            OaSignTaskStatus target,
            OaSignOperatorType operatorType,
            Long operatorUserId,
            String reasonCode,
            String reasonDetail,
            String requestId,
            String ipAddress,
            String userAgent)
    {
        if (current == null || current.getTaskId() == null)
        {
            throw new ServiceException("签约任务不存在");
        }
        if (target == null || operatorType == null)
        {
            throw new ServiceException("任务状态或操作方不能为空");
        }

        OaSignTask repeated = resolveRepeatedRequest(current.getTaskId(), target, requestId);
        if (repeated != null)
        {
            return repeated;
        }

        HrRenewalGuard renewalGuard = lockRenewalGuardForTerminal(current, target);

        OaSignTaskStatus from = parseStatus(current.getStatus());
        stateMachine.assertAllowed(from, target);
        boolean sendFailure = target == OaSignTaskStatus.FAILED
                && "SEND_FAILED".equals(current.getFailureCode());
        int updated = sendFailure
                ? taskMapper.updateStatusWithRetryIncrement(current.getTaskId(), from.name(), target.name(),
                        current.getVersion(), current.getFailureCode(), current.getFailureDetail(),
                        current.getNextRetryTime(), current.getAssignedHrUserId())
                : taskMapper.updateStatusWithVersion(current.getTaskId(), from.name(), target.name(),
                        current.getVersion(), current.getFailureCode(), current.getFailureDetail(),
                        current.getNextRetryTime(), current.getAssignedHrUserId());
        if (updated != 1)
        {
            OaSignTask persisted = taskMapper.selectOaSignTaskById(current.getTaskId());
            if (persisted != null && target.isTerminal() && target.name().equals(persisted.getStatus()))
            {
                return persisted;
            }
            throw new ServiceException("任务已被其他操作更新");
        }

        OaSignTaskEvent previous = eventMapper.selectLastTaskEvent(current.getTaskId());
        OaSignTaskEvent event = new OaSignTaskEvent();
        event.setTaskId(current.getTaskId());
        event.setFromStatus(from.name());
        event.setToStatus(target.name());
        event.setOperatorType(operatorType.name());
        event.setOperatorUserId(operatorUserId);
        event.setReasonCode(reasonCode);
        event.setReasonDetail(reasonDetail);
        event.setRequestId(blankToNull(requestId));
        event.setIpAddress(blankToNull(ipAddress));
        event.setUserAgent(blankToNull(userAgent));
        event.setPrevEventHash(previous == null ? null : previous.getEventHash());
        event.setCreatedTime(new Date(System.currentTimeMillis() / 1000 * 1000));
        event.setEventHash(calculateEventHash(event));
        if (eventMapper.insertOaSignTaskEvent(event) != 1)
        {
            throw new ServiceException("任务事件记录失败");
        }
        releaseRenewalGuardIfTerminal(current, target, renewalGuard);

        current.setStatus(target.name());
        current.setVersion(current.getVersion() + 1);
        if (sendFailure)
        {
            current.setRetryCount(current.getRetryCount() == null ? 1 : current.getRetryCount() + 1);
        }
        return current;
    }

    /**
     * Acquires the renewal guard before a package row is terminalized, keeping the
     * renewal lock order consistent with the event orchestrator.
     */
    public void lockRenewalGuardBeforeTerminal(OaSignTask current, OaSignTaskStatus target)
    {
        HrRenewalGuard guard = lockRenewalGuardForTerminal(current, target);
        if (guard == null)
        {
            return;
        }
        if (!"ACTIVE".equals(guard.getStatus())
                || !guardBoundTo(guard, renewalActionId(current), current.getTaskId()))
        {
            throw new ServiceException("续签任务终态门闩绑定不一致");
        }
    }

    private HrRenewalGuard lockRenewalGuardForTerminal(OaSignTask current,
            OaSignTaskStatus target)
    {
        if (!target.isTerminal() || current.getScenario() == null
                || !"RENEWAL".equalsIgnoreCase(current.getScenario().trim()))
        {
            return null;
        }
        return renewalGuardMapper.selectForUpdate(current.getEmployeeId(), "RENEWAL");
    }

    private void releaseRenewalGuardIfTerminal(OaSignTask current,
            OaSignTaskStatus target, HrRenewalGuard guard)
    {
        if (!target.isTerminal() || current.getScenario() == null
                || !"RENEWAL".equalsIgnoreCase(current.getScenario().trim()))
        {
            return;
        }
        if (guard == null || !"ACTIVE".equals(guard.getStatus()))
        {
            throw new ServiceException("续签任务终态门闩不存在或状态无效");
        }
        reconcileActiveTerminalRenewalGuard(current, guard);
    }

    /**
     * Repairs a stale renewal guard when the source event resolves to an already-terminal task.
     * The orchestrator calls this inside its explicit REQUIRES_NEW transaction.
     */
    public void reconcileTerminalRenewalGuard(OaSignTask terminalTask)
    {
        if (terminalTask == null || terminalTask.getTaskId() == null
                || terminalTask.getScenario() == null
                || !"RENEWAL".equalsIgnoreCase(terminalTask.getScenario().trim())
                || !parseStatus(terminalTask.getStatus()).isTerminal())
        {
            throw new ServiceException("仅续签终态任务允许修复门闩");
        }
        Long currentActionId = renewalActionId(terminalTask);
        HrRenewalGuard guard = renewalGuardMapper.selectForUpdate(
                terminalTask.getEmployeeId(), "RENEWAL");
        if (guard == null)
        {
            return;
        }
        if (guard.getStatus() == null || guard.getVersion() == null)
        {
            throw new ServiceException("续签任务终态门闩不存在或状态无效");
        }
        if ("IDLE".equals(guard.getStatus()))
        {
            if (guard.getActionId() != null || guard.getTaskId() != null)
            {
                throw new ServiceException("续签任务空闲门闩绑定不一致");
            }
            return;
        }
        if ("RESERVED".equals(guard.getStatus()))
        {
            if (guard.getActionId() == null || guard.getTaskId() != null)
            {
                throw new ServiceException("续签任务预留门闩绑定不一致");
            }
            if (!Objects.equals(guard.getActionId(), currentActionId))
            {
                return;
            }
            if (renewalGuardMapper.releaseReserved(terminalTask.getEmployeeId(), "RENEWAL",
                    currentActionId, guard.getVersion()) != 1)
            {
                throw new ServiceException("续签任务预留门闩释放失败");
            }
            return;
        }
        if (!"ACTIVE".equals(guard.getStatus()))
        {
            throw new ServiceException("续签任务终态门闩状态无效");
        }
        reconcileActiveTerminalRenewalGuard(terminalTask, guard);
    }

    private void reconcileActiveTerminalRenewalGuard(OaSignTask current,
            HrRenewalGuard guard)
    {
        Long currentActionId = renewalActionId(current);
        OaSignTask remaining = taskMapper.selectOpenRenewalTaskForUpdate(
                current.getEmployeeId());
        if (remaining != null)
        {
            Long remainingActionId = renewalActionId(remaining);
            if (guardBoundTo(guard, currentActionId, current.getTaskId()))
            {
                if (renewalGuardMapper.rebindActive(current.getEmployeeId(), "RENEWAL",
                        currentActionId, current.getTaskId(), remainingActionId,
                        remaining.getTaskId()) != 1)
                {
                    throw new ServiceException("续签任务终态门闩移交失败");
                }
            }
            else if (!guardBoundTo(guard, remainingActionId, remaining.getTaskId()))
            {
                throw new ServiceException("续签任务门闩未绑定规范的未完成任务");
            }
            return;
        }
        if (!guardBoundTo(guard, currentActionId, current.getTaskId()))
        {
            throw new ServiceException("续签任务终态门闩绑定不一致");
        }
        if (renewalGuardMapper.release(current.getEmployeeId(), "RENEWAL",
                currentActionId, current.getTaskId()) != 1)
        {
            throw new ServiceException("续签任务终态门闩释放失败");
        }
    }

    private boolean guardBoundTo(HrRenewalGuard guard, Long actionId, Long taskId)
    {
        return Objects.equals(guard.getActionId(), actionId)
                && Objects.equals(guard.getTaskId(), taskId);
    }

    private Long renewalActionId(OaSignTask task)
    {
        try
        {
            if (task.getSourceBusinessId() == null
                    || !task.getSourceBusinessId().matches("[1-9][0-9]*"))
            {
                throw new NumberFormatException("invalid action id");
            }
            return Long.valueOf(task.getSourceBusinessId());
        }
        catch (NumberFormatException ex)
        {
            throw new ServiceException("续签任务来源业务编号无效，不能更新门闩");
        }
    }

    private OaSignTask resolveRepeatedRequest(Long taskId, OaSignTaskStatus target, String requestId)
    {
        if (requestId == null || requestId.isBlank())
        {
            return null;
        }
        OaSignTaskEvent existing = eventMapper.selectEventByRequestId(requestId);
        if (existing == null)
        {
            return null;
        }
        if (!Objects.equals(taskId, existing.getTaskId()) || !target.name().equals(existing.getToStatus()))
        {
            throw new ServiceException("请求编号已用于其他任务操作");
        }
        OaSignTask persisted = taskMapper.selectOaSignTaskById(taskId);
        if (persisted == null)
        {
            throw new ServiceException("签约任务不存在");
        }
        return persisted;
    }

    private OaSignTaskStatus parseStatus(String status)
    {
        try
        {
            return OaSignTaskStatus.valueOf(status);
        }
        catch (RuntimeException ex)
        {
            throw new ServiceException("签约任务状态不正确");
        }
    }

    public static String calculateEventHash(OaSignTaskEvent event)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, event.getTaskId());
            update(digest, event.getFromStatus());
            update(digest, event.getToStatus());
            update(digest, event.getOperatorType());
            update(digest, event.getOperatorUserId());
            update(digest, event.getReasonCode());
            update(digest, event.getReasonDetail());
            update(digest, event.getRequestId());
            update(digest, event.getIpAddress());
            update(digest, event.getUserAgent());
            update(digest, event.getPrevEventHash());
            update(digest, event.getCreatedTime() == null ? null : event.getCreatedTime().getTime());
            return HexFormat.of().formatHex(digest.digest());
        }
        catch (NoSuchAlgorithmException ex)
        {
            throw new IllegalStateException("SHA-256不可用", ex);
        }
    }

    private static void update(MessageDigest digest, Object value)
    {
        byte[] bytes = (value == null ? "" : String.valueOf(value)).getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private String blankToNull(String value)
    {
        return value == null || value.isBlank() ? null : value;
    }
}
