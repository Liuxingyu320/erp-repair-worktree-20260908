package com.erp.oa.service.impl;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.oa.domain.OaSignNotificationOutbox;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignTask;
import com.erp.oa.domain.vo.OaSignReminderCandidate;
import com.erp.oa.mapper.OaSignNotificationOutboxMapper;
import com.erp.system.api.domain.UserNotificationCommand;

/**
 * Persists idempotent signing notifications before any remote delivery is attempted.
 */
@Service
public class OaSignNotificationOutboxService
{
    public static final String CHANNEL_IN_APP = "IN_APP";
    public static final String CHANNEL_MOBILE_PUSH = "MOBILE_PUSH";

    private static final int MAX_BATCH_SIZE = 100;
    private static final int MAX_RESULT_LENGTH = 1000;

    private final OaSignNotificationOutboxMapper mapper;

    public OaSignNotificationOutboxService(OaSignNotificationOutboxMapper mapper)
    {
        this.mapper = mapper;
    }

    public OaSignNotificationOutbox latestForTask(Long taskId)
    {
        if (taskId == null)
        {
            return null;
        }
        return mapper.selectLatestNotificationForTask(taskId);
    }

    /**
     * Creates one durable row for each channel. The database unique key makes this operation idempotent.
     */
    @Transactional(rollbackFor = Exception.class)
    public List<OaSignNotificationOutbox> enqueueBoth(UserNotificationCommand command,
            Long hrUserId, Long taskId, Long shopDeptId)
    {
        validate(command);
        JSONObject payload = new JSONObject();
        payload.put("title", command.getTitle());
        payload.put("body", command.getBody());
        payload.put("routeType", command.getRouteType());
        payload.put("routeParams", command.getRouteParams());
        payload.put("hrUserId", hrUserId);
        payload.put("taskId", taskId);
        payload.put("shopDeptId", shopDeptId);

        List<OaSignNotificationOutbox> rows = new ArrayList<>(2);
        rows.add(insertOrSelect(CHANNEL_IN_APP, command, JSON.toJSONString(payload)));
        rows.add(insertOrSelect(CHANNEL_MOBILE_PUSH, command, JSON.toJSONString(payload)));
        return rows;
    }

    @Transactional(rollbackFor = Exception.class)
    public List<OaSignNotificationOutbox> enqueueNeedsData(OaSignTask task)
    {
        requireTask(task);
        String sourceEventVersion = StringUtils.isBlank(task.getSourceEventVersion())
                ? "LEGACY" : task.getSourceEventVersion();
        UserNotificationCommand command = command(task.getAssignedHrUserId(),
                needsDataKey(task.getTaskId(), sourceEventVersion),
                "签约资料待补充", "签约任务资料不完整，请进入任务查看并补充",
                "OA_SIGN_HR_TASK", "taskId", task.getTaskId());
        return enqueueBoth(command, task.getAssignedHrUserId(), task.getTaskId(), task.getShopDeptId());
    }

    @Transactional(rollbackFor = Exception.class)
    public List<OaSignNotificationOutbox> enqueueWaitingHr(OaSignTask task, OaSignPackage signPackage)
    {
        requireTaskPackage(task, signPackage);
        UserNotificationCommand command = command(task.getAssignedHrUserId(),
                waitingHrKey(task.getTaskId(), signPackage.getDocumentVersion()),
                "合同草稿待发送", "合同草稿已生成并通过校验，请进入任务查看后发送",
                "OA_SIGN_HR_TASK", "taskId", task.getTaskId());
        return enqueueBoth(command, task.getAssignedHrUserId(), task.getTaskId(), task.getShopDeptId());
    }

    @Transactional(rollbackFor = Exception.class)
    public List<OaSignNotificationOutbox> enqueueSent(OaSignTask task, OaSignPackage signPackage)
    {
        requireTaskPackage(task, signPackage);
        UserNotificationCommand command = command(signPackage.getEmployeeId(),
                sentKey(signPackage.getPackageId(), signPackage.getDocumentVersion()),
                "合同待签署", "请进入系统阅读并签署合同",
                "OA_SIGN_PACKAGE_SIGN", "packageId", signPackage.getPackageId());
        return enqueueBoth(command, task.getAssignedHrUserId(), task.getTaskId(), task.getShopDeptId());
    }

    @Transactional(rollbackFor = Exception.class)
    public List<OaSignNotificationOutbox> enqueueInitialSigned(OaSignTask task,
            OaSignPackage signPackage)
    {
        requireTaskPackage(task, signPackage);
        UserNotificationCommand command = command(task.getAssignedHrUserId(),
                initialSignedKey(signPackage.getPackageId(), signPackage.getDocumentVersion()),
                "员工已完成首次签名", "请进入任务确认公司主体和印章并生成最终合同",
                "OA_SIGN_HR_TASK", "taskId", task.getTaskId());
        return enqueueBoth(command, task.getAssignedHrUserId(), task.getTaskId(), task.getShopDeptId());
    }

    @Transactional(rollbackFor = Exception.class)
    public List<OaSignNotificationOutbox> enqueueFinalReady(OaSignTask task,
            OaSignPackage signPackage)
    {
        requireTaskPackage(task, signPackage);
        if (StringUtils.isBlank(signPackage.getFinalDocumentVersion()))
        {
            throw new ServiceException("最终合同通知缺少文件版本");
        }
        UserNotificationCommand command = command(signPackage.getEmployeeId(),
                finalReadyKey(signPackage.getPackageId(), signPackage.getFinalDocumentVersion()),
                "最终合同待确认", "最终合同已生成，请阅读并确认合同中的公司与印章信息",
                "OA_SIGN_PACKAGE_SIGN", "packageId", signPackage.getPackageId());
        return enqueueBoth(command, task.getAssignedHrUserId(), task.getTaskId(), task.getShopDeptId());
    }

    @Transactional(rollbackFor = Exception.class)
    public List<OaSignNotificationOutbox> enqueueReminder(OaSignReminderCandidate candidate)
    {
        if (candidate == null || candidate.getPackageId() == null
                || candidate.getTaskId() == null || candidate.getEmployeeId() == null
                || candidate.getEmployeeId() <= 0 || candidate.getAssignedHrUserId() == null
                || candidate.getAssignedHrUserId() <= 0 || candidate.getSignDeadline() == null
                || candidate.getReminderBeforeHours() == null
                || candidate.getShopDeptId() == null || candidate.getShopDeptId() <= 0
                || candidate.getReminderBeforeHours() < 24
                || candidate.getReminderBeforeHours() > 720
                || candidate.getReminderBeforeHours() % 24 != 0
                || StringUtils.isBlank(candidate.getReminderStage())
                || StringUtils.isBlank(candidate.getActiveDocumentVersion()))
        {
            throw new ServiceException("签约提醒上下文不完整");
        }
        if (!("INITIAL".equals(candidate.getReminderStage())
                && ("pending_sign".equals(candidate.getPackageStatus())
                    || "part_viewed".equals(candidate.getPackageStatus())))
                && !("FINAL".equals(candidate.getReminderStage())
                    && "pending_final_confirm".equals(candidate.getPackageStatus())))
        {
            throw new ServiceException("签约提醒阶段与包状态不一致");
        }
        if (mapper.lockCurrentReminderCandidate(candidate, new Date()) == null)
        {
            return List.of();
        }
        String businessKey = reminderKey(candidate.getPackageId(), candidate.getReminderStage(),
                candidate.getActiveDocumentVersion(), candidate.getSignDeadline(),
                candidate.getReminderBeforeHours());
        String body = "FINAL".equals(candidate.getReminderStage())
                ? "签署期限临近，请尽快阅读并确认最终合同"
                : "签署期限临近，请尽快阅读并签署合同";
        UserNotificationCommand command = command(candidate.getEmployeeId(), businessKey,
                "合同签署即将截止", body,
                "OA_SIGN_PACKAGE_SIGN", "packageId", candidate.getPackageId());
        return enqueueBoth(command, candidate.getAssignedHrUserId(), candidate.getTaskId(),
                candidate.getShopDeptId());
    }

    @Transactional(rollbackFor = Exception.class)
    public List<OaSignNotificationOutbox> enqueueCompleted(OaSignTask task, OaSignPackage signPackage)
    {
        requireTaskPackage(task, signPackage);
        String documentVersion = StringUtils.isNotBlank(signPackage.getFinalDocumentVersion())
                ? signPackage.getFinalDocumentVersion() : signPackage.getDocumentVersion();
        String businessKey = completedKey(signPackage.getPackageId(), documentVersion);
        UserNotificationCommand hrCommand = command(task.getAssignedHrUserId(), businessKey,
                "合同已签署", "员工已完成合同签署，请进入任务查看归档结果",
                "OA_SIGN_HR_TASK", "taskId", task.getTaskId());
        UserNotificationCommand employeeCommand = command(signPackage.getEmployeeId(), businessKey,
                "合同签署已完成", "合同签署已完成，可进入签约详情查看归档文件和验真信息",
                "OA_SIGN_PACKAGE_SIGN", "packageId", signPackage.getPackageId());
        List<OaSignNotificationOutbox> rows = new ArrayList<>(4);
        rows.addAll(enqueueBoth(hrCommand, task.getAssignedHrUserId(), task.getTaskId(), task.getShopDeptId()));
        rows.addAll(enqueueBoth(employeeCommand, task.getAssignedHrUserId(), task.getTaskId(), task.getShopDeptId()));
        return rows;
    }

    @Transactional(rollbackFor = Exception.class)
    public List<OaSignNotificationOutbox> enqueueRefused(OaSignTask task,
            OaSignPackage signPackage, Long refusalEventId)
    {
        requireTaskPackage(task, signPackage);
        UserNotificationCommand command = command(task.getAssignedHrUserId(),
                refusedKey(signPackage.getPackageId(), refusalEventId),
                "员工已拒签合同", "员工已拒绝本次签约，请进入任务查看原因并处理",
                "OA_SIGN_HR_TASK", "taskId", task.getTaskId());
        return enqueueBoth(command, task.getAssignedHrUserId(), task.getTaskId(), task.getShopDeptId());
    }

    @Transactional(rollbackFor = Exception.class)
    public List<OaSignNotificationOutbox> enqueueExpired(OaSignTask task, OaSignPackage signPackage)
    {
        requireTaskPackage(task, signPackage);
        String businessKey = expiredKey(signPackage.getPackageId(), signPackage.getDocumentVersion());
        UserNotificationCommand hrCommand = command(task.getAssignedHrUserId(), businessKey,
                "员工合同已过期", "员工未在截止时间前完成签约，请进入任务处理",
                "OA_SIGN_HR_TASK", "taskId", task.getTaskId());
        UserNotificationCommand employeeCommand = command(signPackage.getEmployeeId(), businessKey,
                "合同签约已过期", "本次合同签约已超过截止时间，请联系合同经办人",
                "OA_SIGN_PACKAGE_SIGN", "packageId", signPackage.getPackageId());
        List<OaSignNotificationOutbox> rows = new ArrayList<>(4);
        rows.addAll(enqueueBoth(hrCommand, task.getAssignedHrUserId(), task.getTaskId(), task.getShopDeptId()));
        rows.addAll(enqueueBoth(employeeCommand, task.getAssignedHrUserId(), task.getTaskId(), task.getShopDeptId()));
        return rows;
    }

    public List<OaSignNotificationOutbox> selectDue(Date dueTime, Date staleSendingBefore, int limit)
    {
        if (dueTime == null || staleSendingBefore == null)
        {
            throw new ServiceException("通知调度时间不能为空");
        }
        int safeLimit = Math.max(1, Math.min(limit, MAX_BATCH_SIZE));
        return mapper.selectDueNotifications(dueTime, staleSendingBefore, safeLimit);
    }

    /**
     * Runs in a short independent transaction, so the claim is committed before the network call starts.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean claim(OaSignNotificationOutbox row)
    {
        if (row == null || row.getOutboxId() == null || row.getVersion() == null
                || StringUtils.isBlank(row.getStatus()))
        {
            return false;
        }
        int affected = mapper.claimForSending(row.getOutboxId(), row.getStatus(), row.getVersion());
        if (affected == 1)
        {
            row.setStatus("SENDING");
            row.setVersion(row.getVersion() + 1);
            return true;
        }
        return false;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markSent(OaSignNotificationOutbox row, String result)
    {
        assertUpdated(mapper.markSent(row.getOutboxId(), row.getVersion(), truncate(result)), "完成通知");
        row.setStatus("SENT");
        row.setNextRetryTime(null);
        row.setLastResult(truncate(result));
        row.setLastError(null);
        row.setVersion(row.getVersion() + 1);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markRetry(OaSignNotificationOutbox row, int retryCount, Date nextRetryTime, String error)
    {
        String safeError = truncate(error);
        assertUpdated(mapper.markRetry(row.getOutboxId(), row.getVersion(), retryCount,
                nextRetryTime, safeError), "安排通知重试");
        row.setStatus("RETRY");
        row.setRetryCount(retryCount);
        row.setNextRetryTime(nextRetryTime);
        row.setLastResult(null);
        row.setLastError(safeError);
        row.setVersion(row.getVersion() + 1);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markDead(OaSignNotificationOutbox row, String error)
    {
        String safeError = truncate(error);
        assertUpdated(mapper.markDead(row.getOutboxId(), row.getVersion(), safeError), "终止通知重试");
        row.setStatus("DEAD");
        row.setNextRetryTime(null);
        row.setLastResult(null);
        row.setLastError(safeError);
        row.setVersion(row.getVersion() + 1);
    }

    @Transactional(rollbackFor = Exception.class)
    public int requeueDead(OaSignTask task, String businessKey)
    {
        if (task == null || task.getTaskId() == null || task.getAssignedHrUserId() == null
                || StringUtils.isBlank(businessKey) || businessKey.length() > 180)
        {
            throw new ServiceException("通知重试参数不完整");
        }
        List<OaSignNotificationOutbox> rows = mapper.selectNotificationsForTaskBusinessKey(
                task.getAssignedHrUserId(), task.getTaskId(), businessKey);
        if (rows == null || rows.isEmpty())
        {
            throw new ServiceException("通知事件不存在或无权重试");
        }
        int requeued = 0;
        for (OaSignNotificationOutbox row : rows)
        {
            if (!"DEAD".equals(row.getStatus()) || row.getOutboxId() == null || row.getVersion() == null)
            {
                continue;
            }
            if (mapper.requeueDead(row.getOutboxId(), row.getVersion(),
                    task.getTaskId(), task.getAssignedHrUserId()) == 1)
            {
                row.setStatus("PENDING");
                row.setRetryCount(0);
                row.setNextRetryTime(null);
                row.setLastResult("MANUAL_REQUEUE");
                row.setLastError(null);
                row.setVersion(row.getVersion() + 1);
                requeued++;
            }
        }
        return requeued;
    }

    public static String needsDataKey(Long taskId, String sourceEventVersion)
    {
        return key("SIGN_NEEDS_DATA", taskId, sourceEventVersion);
    }

    public static String waitingHrKey(Long taskId, String documentVersion)
    {
        return key("SIGN_WAITING_HR", taskId, documentVersion);
    }

    public static String sentKey(Long packageId, String documentVersion)
    {
        return key("SIGN_SENT", packageId, documentVersion);
    }

    public static String initialSignedKey(Long packageId, String documentVersion)
    {
        return key("SIGN_INITIAL_SIGNED", packageId, documentVersion);
    }

    public static String finalReadyKey(Long packageId, String finalDocumentVersion)
    {
        return key("SIGN_FINAL_READY", packageId, finalDocumentVersion);
    }

    public static String reminderKey(Long packageId, Date deadline)
    {
        if (packageId == null || deadline == null)
        {
            throw new ServiceException("通知业务键参数不能为空");
        }
        return "SIGN_REMINDER:" + packageId + ":"
                + deadline.toInstant().truncatedTo(ChronoUnit.SECONDS) + ":24H";
    }

    public static String reminderKey(Long packageId, String stage, String documentVersion,
            Date deadline, int beforeHours)
    {
        if (packageId == null || deadline == null || StringUtils.isBlank(documentVersion)
                || (!("INITIAL".equals(stage)) && !("FINAL".equals(stage)))
                || beforeHours < 1 || beforeHours > 720)
        {
            throw new ServiceException("提醒通知业务键参数无效");
        }
        return "SIGN_REMINDER:" + packageId + ":" + stage + ":" + documentVersion + ":"
                + deadline.toInstant().truncatedTo(ChronoUnit.SECONDS) + ":" + beforeHours + "H";
    }

    public static String refusedKey(Long packageId, Long eventId)
    {
        return key("SIGN_REFUSED", packageId, eventId);
    }

    public static String expiredKey(Long packageId, String documentVersion)
    {
        return key("SIGN_EXPIRED", packageId, documentVersion);
    }

    public static String completedKey(Long packageId, String documentVersion)
    {
        return key("SIGN_COMPLETED", packageId, documentVersion);
    }

    private OaSignNotificationOutbox insertOrSelect(String channel,
            UserNotificationCommand command, String payloadJson)
    {
        OaSignNotificationOutbox row = new OaSignNotificationOutbox();
        row.setChannel(channel);
        row.setRecipientUserId(command.getRecipientUserId());
        row.setBusinessKey(command.getBusinessKey());
        row.setPayloadJson(payloadJson);
        row.setStatus("PENDING");
        row.setRetryCount(0);
        row.setVersion(0L);
        if (mapper.insertOaSignNotificationOutbox(row) == 1)
        {
            return row;
        }
        OaSignNotificationOutbox existing = mapper.selectByBusinessKey(channel,
                command.getRecipientUserId(), command.getBusinessKey());
        if (existing == null)
        {
            throw new ServiceException("通知任务保存失败");
        }
        return existing;
    }

    private static UserNotificationCommand command(Long recipientUserId, String businessKey,
            String title, String body, String routeType, String routeIdName, Long routeId)
    {
        JSONObject routeParams = new JSONObject();
        routeParams.put(routeIdName, routeId);
        UserNotificationCommand command = new UserNotificationCommand();
        command.setRecipientUserId(recipientUserId);
        command.setBusinessKey(businessKey);
        command.setTitle(title);
        command.setBody(body);
        command.setRouteType(routeType);
        command.setRouteParams(JSON.toJSONString(routeParams));
        return command;
    }

    private static void requireTaskPackage(OaSignTask task, OaSignPackage signPackage)
    {
        requireTask(task);
        if (signPackage == null || signPackage.getPackageId() == null || signPackage.getPackageId() <= 0
                || signPackage.getEmployeeId() == null || signPackage.getEmployeeId() <= 0
                || signPackage.getTaskId() == null || signPackage.getTaskId() <= 0
                || signPackage.getShopDeptId() == null || signPackage.getShopDeptId() <= 0
                || signPackage.getPlanVersionId() == null || signPackage.getPlanVersionId() <= 0
                || task.getPlanVersionId() == null || task.getPlanVersionId() <= 0
                || StringUtils.isBlank(signPackage.getDocumentVersion())
                || !Objects.equals(task.getTaskId(), signPackage.getTaskId())
                || !Objects.equals(task.getPackageId(), signPackage.getPackageId())
                || !Objects.equals(task.getEmployeeId(), signPackage.getEmployeeId())
                || !Objects.equals(task.getShopDeptId(), signPackage.getShopDeptId())
                || !Objects.equals(task.getPlanVersionId(), signPackage.getPlanVersionId()))
        {
            throw new ServiceException("签约通知上下文不完整");
        }
    }

    private static void requireTask(OaSignTask task)
    {
        if (task == null || task.getTaskId() == null || task.getTaskId() <= 0
                || task.getAssignedHrUserId() == null
                || task.getAssignedHrUserId() <= 0)
        {
            throw new ServiceException("签约通知上下文不完整");
        }
    }

    private static void validate(UserNotificationCommand command)
    {
        if (command == null || command.getRecipientUserId() == null || command.getRecipientUserId() <= 0
                || StringUtils.isBlank(command.getBusinessKey())
                || StringUtils.isBlank(command.getTitle())
                || StringUtils.isBlank(command.getBody())
                || StringUtils.isBlank(command.getRouteType())
                || StringUtils.isBlank(command.getRouteParams()))
        {
            throw new ServiceException("通知参数不完整");
        }
        if (command.getBusinessKey().length() > 180 || command.getTitle().length() > 120
                || command.getBody().length() > 1000 || command.getRouteType().length() > 64
                || command.getRouteParams().length() > 2000)
        {
            throw new ServiceException("通知参数长度超限");
        }
        if (!"OA_SIGN_HR_TASK".equals(command.getRouteType())
                && !"OA_SIGN_PACKAGE_SIGN".equals(command.getRouteType()))
        {
            throw new ServiceException("通知路由类型无效");
        }
        try
        {
            JSONObject routeParams = JSON.parseObject(command.getRouteParams());
            String routeIdName = "OA_SIGN_HR_TASK".equals(command.getRouteType())
                    ? "taskId" : "packageId";
            Long routeId = routeParams == null ? null : routeParams.getLong(routeIdName);
            if (routeId == null || routeId <= 0)
            {
                throw new ServiceException("通知路由参数无效");
            }
        }
        catch (ServiceException exception)
        {
            throw exception;
        }
        catch (RuntimeException exception)
        {
            throw new ServiceException("通知路由参数无效");
        }
    }

    private static String key(String prefix, Object first, Object second)
    {
        if (first == null || second == null || StringUtils.isBlank(String.valueOf(second)))
        {
            throw new ServiceException("通知业务键参数不能为空");
        }
        return prefix + ":" + first + ":" + second;
    }

    private static void assertUpdated(int affected, String action)
    {
        if (affected != 1)
        {
            throw new ServiceException(action + "失败，任务已被其他实例处理");
        }
    }

    private static String truncate(String value)
    {
        if (value == null || value.length() <= MAX_RESULT_LENGTH)
        {
            return value;
        }
        return value.substring(0, MAX_RESULT_LENGTH);
    }
}
