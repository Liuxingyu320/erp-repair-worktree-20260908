package com.erp.oa.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.constant.OaSignOperatorType;
import com.erp.oa.constant.OaSignPackageStatus;
import com.erp.oa.constant.OaSignTaskStatus;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPlanVersion;
import com.erp.oa.domain.OaSignTask;
import com.erp.oa.mapper.OaSignPlanVersionMapper;
import com.erp.oa.mapper.OaSignTaskMapper;
import com.erp.oa.service.IOaSignPackageService;

/** 负责签约任务、签约包及通知记录的一致性事务。 */
@Service
public class OaSignTaskWorkflowService
{
    static final ZoneId SIGNING_ZONE = ZoneId.of("Asia/Shanghai");

    private final OaSignTaskMapper taskMapper;
    private final OaSignTaskEventService eventService;
    private final IOaSignPackageService packageService;
    private final OaSignNotificationOutboxService outboxService;
    private final OaSignPlanVersionMapper planVersionMapper;
    private final Clock clock;

    @Autowired
    public OaSignTaskWorkflowService(OaSignTaskMapper taskMapper,
            OaSignTaskEventService eventService,
            IOaSignPackageService packageService,
            OaSignNotificationOutboxService outboxService,
            OaSignPlanVersionMapper planVersionMapper)
    {
        this(taskMapper, eventService, packageService, outboxService,
                planVersionMapper, Clock.system(SIGNING_ZONE));
    }

    OaSignTaskWorkflowService(OaSignTaskMapper taskMapper,
            OaSignTaskEventService eventService,
            IOaSignPackageService packageService,
            OaSignNotificationOutboxService outboxService,
            OaSignPlanVersionMapper planVersionMapper,
            Clock clock)
    {
        this.taskMapper = taskMapper;
        this.eventService = eventService;
        this.packageService = packageService;
        this.outboxService = outboxService;
        this.planVersionMapper = planVersionMapper;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public OaSignPackage completeValidation(OaSignTask task, Long selectedShopDeptId,
            String ipAddress, String userAgent)
    {
        OaSignPackage prepared;
        if (OaSignTaskStatus.VALIDATING.name().equals(task.getStatus()))
        {
            prepared = packageService.preparePackageDocuments(task.getPackageId(), selectedShopDeptId);
            eventService.transition(task, OaSignTaskStatus.DRAFT_CREATED, OaSignOperatorType.SYSTEM,
                    null, "DRAFT_READY", null, null, ipAddress, userAgent);
        }
        else if (OaSignTaskStatus.DRAFT_CREATED.name().equals(task.getStatus()))
        {
            prepared = packageService.getPackageDetail(task.getPackageId(), selectedShopDeptId);
        }
        else
        {
            throw new ServiceException("当前任务状态不能完成草稿校验");
        }

        eventService.transition(task, OaSignTaskStatus.READY_TO_SEND, OaSignOperatorType.SYSTEM,
                null, "REVIEW_NOT_REQUIRED", "当前流程无需审核，可直接发送", null, ipAddress, userAgent);
        taskMapper.clearConfirmation(task.getTaskId(), task.getStatus(), task.getVersion(),
                task.getAssignedHrUserId());
        task.setConfirmedBy(null);
        task.setConfirmedTime(null);
        task.setConfirmedSnapshotHash(null);
        packageService.markTaskConfirmation(task.getPackageId(), task.getTaskId(),
                "NOT_REQUIRED", task.getPlanVersionId());
        outboxService.enqueueWaitingHr(task, prepared);
        return prepared;
    }

    @Transactional(rollbackFor = Exception.class)
    public OaSignPackage sendPrepared(OaSignTask task, OaSignPackage signPackage,
            Long selectedShopDeptId, String ipAddress, String userAgent)
    {
        DeadlineSnapshot deadline = requireDeadlineSnapshot(task);
        OaSignPackage sent = packageService.sendPreparedPackage(task.getPackageId(), task.getTaskId(),
                signPackage.getDocumentVersion(), selectedShopDeptId, deadline.sentTime(),
                deadline.signDeadline(), deadline.policySource(), deadline.days());
        OaSignTaskStatus sentStatus = sent != null
                && OaSignPackageStatus.PENDING_FINAL_CONFIRM.equals(sent.getStatus())
                ? OaSignTaskStatus.PENDING_FINAL_CONFIRM : OaSignTaskStatus.PENDING_SIGN;
        eventService.transition(task, sentStatus, OaSignOperatorType.SYSTEM,
                null, sentStatus == OaSignTaskStatus.PENDING_FINAL_CONFIRM
                        ? "FINAL_CANDIDATE_SENT" : "PACKAGE_SENT",
                null, null, ipAddress, userAgent);
        if (taskMapper.updateSentLifecycle(task.getTaskId(), sentStatus.name(),
                task.getVersion(), task.getAssignedHrUserId(), deadline.sentTime(),
                deadline.signDeadline(), deadline.policySource(), deadline.days()) != 1)
        {
            throw new ServiceException("签约任务发送期限已被其他操作更新");
        }
        task.setSentTime(deadline.sentTime());
        task.setSignDeadline(deadline.signDeadline());
        task.setDeadlinePolicySource(deadline.policySource());
        task.setDeadlineDaysSnapshot(deadline.days());
        OaSignPackage notificationPackage = sent == null ? signPackage : sent;
        if (sentStatus == OaSignTaskStatus.PENDING_FINAL_CONFIRM)
        {
            outboxService.enqueueFinalReady(task, notificationPackage);
        }
        else
        {
            outboxService.enqueueSent(task, notificationPackage);
        }
        return notificationPackage;
    }

    @Transactional(rollbackFor = Exception.class)
    public void invalidateStaleConfirmation(OaSignTask task, String ipAddress, String userAgent)
    {
        eventService.transition(task, OaSignTaskStatus.WAITING_HR_CONFIRM,
                OaSignOperatorType.SYSTEM, null, "CONFIRMATION_STALE",
                "资料或文件已变化", null, ipAddress, userAgent);
        if (taskMapper.clearConfirmation(task.getTaskId(), task.getStatus(), task.getVersion(),
                task.getAssignedHrUserId()) != 1)
        {
            throw new ServiceException("任务确认已被其他操作更新");
        }
        packageService.markTaskConfirmation(task.getPackageId(), task.getTaskId(),
                "WAITING_HR_CONFIRM", task.getPlanVersionId());
        task.setConfirmedBy(null);
        task.setConfirmedTime(null);
        task.setConfirmedSnapshotHash(null);
    }

    private DeadlineSnapshot requireDeadlineSnapshot(OaSignTask task)
    {
        if (task == null || task.getPlanVersionId() == null || planVersionMapper == null)
        {
            throw new ServiceException("签约任务缺少冻结方案版本或签署期限");
        }
        OaSignPlanVersion version = planVersionMapper.selectPlanVersionById(task.getPlanVersionId());
        Integer days = version == null ? null : version.getSignDeadlineDays();
        if (version == null || !task.getPlanVersionId().equals(version.getVersionId())
                || !"PUBLISHED".equals(version.getPublishStatus())
                || days == null || days < 1 || days > 365)
        {
            throw new ServiceException("冻结方案版本的签署期限必须在1至365天之间");
        }
        Instant sentInstant = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Date sentTime = Date.from(sentInstant);
        Instant deadlineInstant = sentInstant.atZone(SIGNING_ZONE).toLocalDate()
                .plusDays(days)
                .atTime(LocalTime.of(23, 59, 59))
                .atZone(SIGNING_ZONE)
                .toInstant();
        return new DeadlineSnapshot(sentTime, Date.from(deadlineInstant), "PLAN_VERSION", days);
    }

    private record DeadlineSnapshot(Date sentTime, Date signDeadline,
            String policySource, Integer days) {}
}
