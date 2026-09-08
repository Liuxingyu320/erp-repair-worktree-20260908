package com.erp.oa.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.oa.constant.OaSignOperatorType;
import com.erp.oa.constant.OaSignPackageStatus;
import com.erp.oa.constant.OaSignSigningSequence;
import com.erp.oa.constant.OaSignTaskStatus;
import com.erp.oa.domain.OaSignEvent;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignTask;
import com.erp.oa.domain.dto.OaSignPackageRefuseRequest;
import com.erp.oa.domain.dto.OaSignExceptionResolutionRequest;
import com.erp.oa.mapper.OaSignEventMapper;
import com.erp.oa.mapper.OaSignOnboardDataRequestMapper;
import com.erp.oa.mapper.OaSignOnboardImportRowMapper;
import com.erp.oa.mapper.OaSignPackageMapper;
import com.erp.oa.mapper.OaSignTaskMapper;

/**
 * Owns the immutable employee-side terminal transitions for signing packages.
 */
@Service
public class OaSignPackageLifecycleService
{
    private static final int MAX_EXPIRY_BATCH_SIZE = 200;
    private static final String RESOLUTION_OPEN = "OPEN";
    private static final String EXPIRY_REASON_CODE = "SIGN_DEADLINE_ELAPSED";
    private static final String EXPIRY_REASON_DETAIL = "员工未在签署截止时间前完成签约";
    private static final String EXCEPTION_ACTION_EVENT = "PACKAGE_EXCEPTION_ACTION";
    private static final ZoneId SIGNING_ZONE = ZoneId.of("Asia/Shanghai");

    private final OaSignPackageMapper packageMapper;
    private final OaSignTaskMapper taskMapper;
    private final OaSignEventMapper eventMapper;
    private final OaSignOnboardDataRequestMapper onboardDataRequestMapper;
    private final OaSignOnboardImportRowMapper onboardImportRowMapper;
    private final OaSignTaskEventService taskEventService;
    private final OaSignNotificationOutboxService notificationOutboxService;
    private final Clock clock;

    @Autowired
    public OaSignPackageLifecycleService(OaSignPackageMapper packageMapper,
            OaSignTaskMapper taskMapper,
            OaSignEventMapper eventMapper,
            OaSignOnboardDataRequestMapper onboardDataRequestMapper,
            OaSignOnboardImportRowMapper onboardImportRowMapper,
            OaSignTaskEventService taskEventService,
            OaSignNotificationOutboxService notificationOutboxService)
    {
        this(packageMapper, taskMapper, eventMapper, onboardDataRequestMapper,
                onboardImportRowMapper, taskEventService,
                notificationOutboxService, Clock.systemUTC());
    }

    OaSignPackageLifecycleService(OaSignPackageMapper packageMapper,
            OaSignTaskMapper taskMapper,
            OaSignEventMapper eventMapper,
            OaSignOnboardDataRequestMapper onboardDataRequestMapper,
            OaSignOnboardImportRowMapper onboardImportRowMapper,
            OaSignTaskEventService taskEventService,
            OaSignNotificationOutboxService notificationOutboxService,
            Clock clock)
    {
        this.packageMapper = packageMapper;
        this.taskMapper = taskMapper;
        this.eventMapper = eventMapper;
        this.onboardDataRequestMapper = onboardDataRequestMapper;
        this.onboardImportRowMapper = onboardImportRowMapper;
        this.taskEventService = taskEventService;
        this.notificationOutboxService = notificationOutboxService;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public OaSignPackage refuse(Long packageId, OaSignPackageRefuseRequest request,
            Long employeeUserId, String ipAddress, String userAgent)
    {
        requireRefusalRequest(request);
        OaSignPackage signPackage = requirePackage(packageId);
        if (!Objects.equals(signPackage.getEmployeeId(), employeeUserId))
        {
            throw new ServiceException("无权处理该签约包");
        }

        OaSignEvent repeated = eventMapper.selectEventByTypeAndRequestId(
                "PACKAGE_REFUSED", request.getRequestId());
        if (repeated != null)
        {
            if (Objects.equals(repeated.getPackageId(), packageId)
                    && OaSignPackageStatus.REFUSED.equals(signPackage.getStatus()))
            {
                return signPackage;
            }
            throw new ServiceException("拒签请求编号已被其他签约包使用");
        }

        assertEmployeeTerminalSource(signPackage.getStatus(), "拒签");
        assertDeadlineOpen(signPackage, now());
        if (!Objects.equals(activeDocumentVersion(signPackage), request.getDocumentVersion()))
        {
            throw new ServiceException("文档版本已更新，请重新打开后拒签");
        }

        OaSignTask task = prepareTerminalTask(signPackage, OaSignTaskStatus.REFUSED);
        boolean closeStagedFirstStage = requiresStagedFirstStageClose(task, signPackage);
        Date terminalTime = now();
        if (packageMapper.markTerminalWithVersion(packageId, signPackage.getStatus(),
                OaSignPackageStatus.REFUSED, version(signPackage), terminalTime,
                request.getReasonCode(), request.getReasonDetail(), "employee:" + employeeUserId) != 1)
        {
            return resolveConcurrentRefusal(packageId, request.getRequestId());
        }

        transitionTask(task, OaSignTaskStatus.REFUSED,
                OaSignOperatorType.EMPLOYEE, employeeUserId, terminalTime,
                request.getReasonCode(), request.getReasonDetail(), request.getRequestId(),
                ipAddress, userAgent);
        closeStagedFirstStage(task, signPackage, OaSignTaskStatus.REFUSED,
                OaSignPackageStatus.REFUSED, closeStagedFirstStage);
        OaSignEvent event = appendPackageEvent(signPackage, "PACKAGE_REFUSED",
                refusalPayload(request), employeeUserId, "EMPLOYEE", request.getRequestId(),
                ipAddress, userAgent, terminalTime);
        signPackage.setStatus(OaSignPackageStatus.REFUSED);
        signPackage.setVersion(version(signPackage) + 1);
        signPackage.setTerminalTime(terminalTime);
        signPackage.setTerminalReasonCode(request.getReasonCode());
        signPackage.setTerminalReasonDetail(request.getReasonDetail());
        signPackage.setResolutionStatus(RESOLUTION_OPEN);
        notificationOutboxService.enqueueRefused(task, signPackage, event.getEventId());
        return signPackage;
    }

    public List<OaSignPackage> selectExpiredCandidates(int batchSize)
    {
        int safeBatchSize = Math.max(1, Math.min(batchSize, MAX_EXPIRY_BATCH_SIZE));
        return packageMapper.selectExpiredCandidates(now(), safeBatchSize);
    }

    public OaSignPackage lockForResolution(Long packageId)
    {
        OaSignPackage signPackage = packageId == null ? null : packageMapper.lockOaSignPackageById(packageId);
        if (signPackage == null)
        {
            throw new ServiceException("签约包不存在");
        }
        return signPackage;
    }

    @Transactional(rollbackFor = Exception.class)
    public OaSignPackage resolve(OaSignTask task, OaSignPackage signPackage,
            OaSignExceptionResolutionRequest request, Long hrUserId,
            String hrUsername, String ipAddress, String userAgent)
    {
        requireResolutionContext(task, signPackage, request, hrUserId);
        OaSignEvent repeated = eventMapper.selectEventByTypeAndRequestId(
                EXCEPTION_ACTION_EVENT, request.getRequestId());
        if (repeated != null)
        {
            if (Objects.equals(repeated.getPackageId(), signPackage.getPackageId())
                    && sameResolutionAction(repeated, request.getAction()))
            {
                return packageMapper.selectOaSignPackageById(signPackage.getPackageId());
            }
            throw new ServiceException("处置请求编号已被其他签约包使用");
        }
        if (task.getVersion() == null || !Objects.equals(task.getVersion(), request.getExpectedVersion()))
        {
            throw new ServiceException("签约任务版本已变化，请刷新后重试");
        }
        if (!Objects.equals(activeDocumentVersion(signPackage), request.getDocumentVersion()))
        {
            throw new ServiceException("文档版本已更新，请刷新后处理");
        }

        return switch (request.getAction())
        {
            case "CLOSE" -> closeResolution(task, signPackage, request, hrUserId,
                    hrUsername, ipAddress, userAgent);
            case "EXTEND" -> extendDeadline(task, signPackage, request, hrUserId,
                    hrUsername, ipAddress, userAgent);
            case "REISSUE" -> throw new ServiceException("替代版本必须先创建新任务和签约包");
            default -> throw new ServiceException("不支持的异常处置动作");
        };
    }

    @Transactional(rollbackFor = Exception.class)
    public OaSignPackage markReissued(OaSignTask sourceTask, OaSignPackage sourcePackage,
            OaSignTask replacementTask, OaSignPackage replacementPackage,
            OaSignExceptionResolutionRequest request, Long hrUserId,
            String hrUsername, String ipAddress, String userAgent)
    {
        requireResolutionContext(sourceTask, sourcePackage, request, hrUserId);
        if (!"REISSUE".equals(request.getAction()) || request.getReplacementPlanVersionId() == null
                || request.getExtensionDays() != null)
        {
            throw new ServiceException("替代版本处置参数不完整");
        }
        if (replacementTask == null || replacementTask.getTaskId() == null
                || replacementPackage == null || replacementPackage.getPackageId() == null
                || !Objects.equals(replacementTask.getPackageId(), replacementPackage.getPackageId())
                || !Objects.equals(replacementPackage.getTaskId(), replacementTask.getTaskId())
                || !Objects.equals(replacementTask.getReissueOfTaskId(), sourceTask.getTaskId()))
        {
            throw new ServiceException("替代任务与签约包关联不完整");
        }
        if (!Objects.equals(activeDocumentVersion(sourcePackage), request.getDocumentVersion())
                || !Objects.equals(sourceTask.getVersion(), request.getExpectedVersion()))
        {
            throw new ServiceException("原签约任务或文档版本已变化");
        }
        if (packageMapper.bindReissueSource(replacementPackage.getPackageId(),
                version(replacementPackage), sourcePackage.getPackageId(), hrUsername) != 1)
        {
            throw new ServiceException("替代签约包来源关联失败");
        }
        replacementPackage.setReissueOfPackageId(sourcePackage.getPackageId());
        replacementPackage.setVersion(version(replacementPackage) + 1);

        Date resolvedTime = now();
        if (packageMapper.updateResolutionWithVersion(sourcePackage.getPackageId(),
                sourcePackage.getStatus(), version(sourcePackage), "OPEN", "REISSUED",
                hrUserId, resolvedTime, request.getReasonCode(), request.getReasonDetail(),
                replacementPackage.getPackageId(), hrUsername) != 1)
        {
            throw new ServiceException("原签约包处置状态已变化，请刷新后重试");
        }
        if (taskMapper.updateResolutionWithVersion(sourceTask.getTaskId(), sourceTask.getStatus(),
                sourceTask.getVersion(), sourceTask.getAssignedHrUserId(), "OPEN", "REISSUED",
                hrUserId, resolvedTime, request.getReasonCode(), request.getReasonDetail(),
                replacementTask.getTaskId()) != 1)
        {
            throw new ServiceException("原签约任务处置状态已变化，请刷新后重试");
        }
        appendPackageEvent(sourcePackage, EXCEPTION_ACTION_EVENT,
                resolutionPayload(request, null, null, replacementPackage.getPackageId()), hrUserId,
                "HR", request.getRequestId(), ipAddress, userAgent, resolvedTime);
        sourcePackage.setResolutionStatus("REISSUED");
        sourcePackage.setReissuedToPackageId(replacementPackage.getPackageId());
        sourcePackage.setResolvedBy(hrUserId);
        sourcePackage.setResolvedTime(resolvedTime);
        sourcePackage.setVersion(version(sourcePackage) + 1);
        sourceTask.setResolutionStatus("REISSUED");
        sourceTask.setReissuedToTaskId(replacementTask.getTaskId());
        sourceTask.setVersion(sourceTask.getVersion() + 1);
        return sourcePackage;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean expireOne(OaSignPackage candidate)
    {
        if (candidate == null || candidate.getPackageId() == null)
        {
            return false;
        }
        OaSignPackage signPackage = requirePackage(candidate.getPackageId());
        if (!Objects.equals(candidate.getVersion(), signPackage.getVersion())
                || !Objects.equals(candidate.getStatus(), signPackage.getStatus()))
        {
            return false;
        }
        if (!isEmployeeTerminalSource(signPackage.getStatus()))
        {
            return false;
        }
        Date terminalTime = now();
        if (!isDue(signPackage, terminalTime))
        {
            return false;
        }

        OaSignTask task = prepareTerminalTask(signPackage, OaSignTaskStatus.EXPIRED);
        boolean closeStagedFirstStage = requiresStagedFirstStageClose(task, signPackage);
        if (packageMapper.markTerminalWithVersion(signPackage.getPackageId(), signPackage.getStatus(),
                OaSignPackageStatus.EXPIRED, version(signPackage), terminalTime,
                EXPIRY_REASON_CODE, EXPIRY_REASON_DETAIL, "system") != 1)
        {
            return false;
        }
        String requestId = expiryRequestId(signPackage);
        transitionTask(task, OaSignTaskStatus.EXPIRED,
                OaSignOperatorType.SYSTEM, null, terminalTime,
                EXPIRY_REASON_CODE, EXPIRY_REASON_DETAIL, requestId,
                null, null);
        closeStagedFirstStage(task, signPackage, OaSignTaskStatus.EXPIRED,
                OaSignPackageStatus.EXPIRED, closeStagedFirstStage);
        appendPackageEvent(signPackage, "PACKAGE_EXPIRED",
                "deadline=" + signPackage.getSignDeadline().toInstant(), null, "SYSTEM",
                requestId, null, null, terminalTime);
        signPackage.setStatus(OaSignPackageStatus.EXPIRED);
        signPackage.setVersion(version(signPackage) + 1);
        signPackage.setTerminalTime(terminalTime);
        signPackage.setTerminalReasonCode(EXPIRY_REASON_CODE);
        signPackage.setTerminalReasonDetail(EXPIRY_REASON_DETAIL);
        signPackage.setResolutionStatus(RESOLUTION_OPEN);
        notificationOutboxService.enqueueExpired(task, signPackage);
        return true;
    }

    private OaSignPackage closeResolution(OaSignTask task, OaSignPackage signPackage,
            OaSignExceptionResolutionRequest request, Long hrUserId,
            String hrUsername, String ipAddress, String userAgent)
    {
        if (request.getExtensionDays() != null || request.getReplacementPlanVersionId() != null
                || !(OaSignTaskStatus.REFUSED.name().equals(task.getStatus())
                || OaSignTaskStatus.EXPIRED.name().equals(task.getStatus()))
                || !(OaSignPackageStatus.REFUSED.equals(signPackage.getStatus())
                || OaSignPackageStatus.EXPIRED.equals(signPackage.getStatus()))
                || !"OPEN".equals(task.getResolutionStatus())
                || !"OPEN".equals(signPackage.getResolutionStatus()))
        {
            throw new ServiceException("当前签约任务不能关闭异常处置");
        }
        assertTaskPackageStateMatches(task, signPackage);
        Date resolvedTime = now();
        if (packageMapper.updateResolutionWithVersion(signPackage.getPackageId(), signPackage.getStatus(),
                version(signPackage), "OPEN", "CLOSED", hrUserId, resolvedTime,
                request.getReasonCode(), request.getReasonDetail(), null, hrUsername) != 1)
        {
            throw new ServiceException("签约包处置状态已变化，请刷新后重试");
        }
        if (taskMapper.updateResolutionWithVersion(task.getTaskId(), task.getStatus(), task.getVersion(),
                task.getAssignedHrUserId(), "OPEN", "CLOSED", hrUserId, resolvedTime,
                request.getReasonCode(), request.getReasonDetail(), null) != 1)
        {
            throw new ServiceException("签约任务处置状态已变化，请刷新后重试");
        }
        appendPackageEvent(signPackage, EXCEPTION_ACTION_EVENT,
                resolutionPayload(request, null, null, null), hrUserId, "HR", request.getRequestId(),
                ipAddress, userAgent, resolvedTime);
        signPackage.setResolutionStatus("CLOSED");
        signPackage.setResolvedBy(hrUserId);
        signPackage.setResolvedTime(resolvedTime);
        signPackage.setResolutionReasonCode(request.getReasonCode());
        signPackage.setResolutionReasonDetail(request.getReasonDetail());
        signPackage.setVersion(version(signPackage) + 1);
        task.setResolutionStatus("CLOSED");
        task.setVersion(task.getVersion() + 1);
        return signPackage;
    }

    private OaSignPackage extendDeadline(OaSignTask task, OaSignPackage signPackage,
            OaSignExceptionResolutionRequest request, Long hrUserId,
            String hrUsername, String ipAddress, String userAgent)
    {
        if (request.getExtensionDays() == null || request.getExtensionDays() < 1
                || request.getExtensionDays() > 365 || request.getReplacementPlanVersionId() != null
                || task.getResolutionStatus() != null || signPackage.getResolutionStatus() != null)
        {
            throw new ServiceException("延期处置参数不完整");
        }
        assertTaskPackageStateMatches(task, signPackage);
        Date currentTime = now();
        assertDeadlineOpen(signPackage, currentTime);
        if (task.getSignDeadline() == null
                || !Objects.equals(task.getSignDeadline(), signPackage.getSignDeadline()))
        {
            throw new ServiceException("任务与签约包截止时间不一致");
        }
        Date newDeadline = Date.from(signPackage.getSignDeadline().toInstant()
                .atZone(SIGNING_ZONE).plusDays(request.getExtensionDays()).toInstant());
        if (packageMapper.extendDeadlineWithVersion(signPackage.getPackageId(),
                signPackage.getStatus(), version(signPackage), null, newDeadline, hrUserId,
                currentTime, request.getReasonCode(), request.getReasonDetail(), hrUsername) != 1)
        {
            throw new ServiceException("签约包已到期或状态已变化，不能延期");
        }
        if (taskMapper.extendDeadlineWithVersion(task.getTaskId(), task.getStatus(), task.getVersion(),
                task.getAssignedHrUserId(), null, newDeadline, hrUserId, currentTime,
                request.getReasonCode(), request.getReasonDetail()) != 1)
        {
            throw new ServiceException("签约任务已到期或状态已变化，不能延期");
        }
        Date oldDeadline = signPackage.getSignDeadline();
        appendPackageEvent(signPackage, EXCEPTION_ACTION_EVENT,
                resolutionPayload(request, oldDeadline, newDeadline, null), hrUserId, "HR",
                request.getRequestId(), ipAddress, userAgent, currentTime);
        signPackage.setSignDeadline(newDeadline);
        signPackage.setResolvedBy(hrUserId);
        signPackage.setResolvedTime(currentTime);
        signPackage.setResolutionReasonCode(request.getReasonCode());
        signPackage.setResolutionReasonDetail(request.getReasonDetail());
        signPackage.setVersion(version(signPackage) + 1);
        task.setSignDeadline(newDeadline);
        task.setVersion(task.getVersion() + 1);
        return signPackage;
    }

    private OaSignTask prepareTerminalTask(OaSignPackage signPackage, OaSignTaskStatus target)
    {
        if (signPackage.getTaskId() == null)
        {
            throw new ServiceException("已发送签约包缺少受控任务关联");
        }
        OaSignTask task = taskMapper.selectOaSignTaskById(signPackage.getTaskId());
        if (task == null || !Objects.equals(task.getPackageId(), signPackage.getPackageId())
                || !Objects.equals(task.getEmployeeId(), signPackage.getEmployeeId()))
        {
            throw new ServiceException("签约包与任务关联不一致");
        }
        taskEventService.lockRenewalGuardBeforeTerminal(task, target);
        return task;
    }

    private boolean requiresStagedFirstStageClose(OaSignTask task, OaSignPackage signPackage)
    {
        boolean pendingFirstStage = OaSignTaskStatus.PENDING_SIGN.name().equals(task.getStatus())
                && OaSignPackageStatus.PENDING_SIGN.equals(signPackage.getStatus())
                || OaSignTaskStatus.VIEWED.name().equals(task.getStatus())
                && OaSignPackageStatus.PART_VIEWED.equals(signPackage.getStatus());
        boolean stagedExcel = "MANUAL_SIGN_EXCEL_IMPORT".equalsIgnoreCase(
                StringUtils.trim(task.getSourceType()))
                && OaSignSigningSequence.SIGNATURE_FIRST.equals(
                        OaSignSigningSequence.normalize(signPackage.getSigningSequence()));
        if (!pendingFirstStage || !stagedExcel)
        {
            return false;
        }
        int linked = onboardDataRequestMapper.countActiveStagedFirstStageLink(
                task.getTaskId(), signPackage.getPackageId(), signPackage.getEmployeeId(),
                task.getStatus(), signPackage.getStatus());
        if (linked != 1)
        {
            throw new ServiceException("先留签名任务的入职导入关联不完整或重复，不能进入终态");
        }
        return true;
    }

    private void closeStagedFirstStage(OaSignTask task, OaSignPackage signPackage,
            OaSignTaskStatus terminalTaskStatus, String terminalPackageStatus, boolean required)
    {
        if (!required)
        {
            return;
        }
        String rowTerminalStatus = terminalTaskStatus.name();
        if (onboardDataRequestMapper.cancelStagedFirstStageByTerminal(
                task.getTaskId(), signPackage.getPackageId(), signPackage.getEmployeeId(),
                terminalTaskStatus.name(), terminalPackageStatus) != 1
                || onboardImportRowMapper.terminalizeStagedFirstStage(
                        task.getTaskId(), signPackage.getPackageId(), signPackage.getEmployeeId(),
                        terminalTaskStatus.name(), terminalPackageStatus,
                        rowTerminalStatus) != 1)
        {
            throw new ServiceException("入职签约首阶段终态联动失败，请刷新后重试");
        }
    }

    private void transitionTask(OaSignTask task, OaSignTaskStatus target,
            OaSignOperatorType operatorType, Long operatorUserId, Date terminalTime,
            String reasonCode, String reasonDetail, String requestId,
            String ipAddress, String userAgent)
    {
        taskEventService.transition(task, target, operatorType, operatorUserId,
                reasonCode, reasonDetail, requestId, ipAddress, userAgent);
        if (taskMapper.updateTerminalMetadata(task.getTaskId(), target.name(), task.getVersion(),
                task.getAssignedHrUserId(), terminalTime, reasonCode, reasonDetail,
                RESOLUTION_OPEN) != 1)
        {
            throw new ServiceException("任务终态审计信息保存失败");
        }
        task.setTerminalTime(terminalTime);
        task.setTerminalReasonCode(reasonCode);
        task.setTerminalReasonDetail(reasonDetail);
        task.setResolutionStatus(RESOLUTION_OPEN);
    }

    private void requireResolutionContext(OaSignTask task, OaSignPackage signPackage,
            OaSignExceptionResolutionRequest request, Long hrUserId)
    {
        if (task == null || task.getTaskId() == null || signPackage == null
                || signPackage.getPackageId() == null || request == null
                || StringUtils.isBlank(request.getRequestId())
                || StringUtils.isBlank(request.getAction())
                || StringUtils.isBlank(request.getDocumentVersion())
                || StringUtils.isBlank(request.getReasonCode())
                || StringUtils.isBlank(request.getReasonDetail())
                || request.getExpectedVersion() == null || hrUserId == null
                || !Objects.equals(task.getPackageId(), signPackage.getPackageId())
                || !Objects.equals(signPackage.getTaskId(), task.getTaskId()))
        {
            throw new ServiceException("异常处置上下文不完整或无权处理");
        }
    }

    private void assertTaskPackageStateMatches(OaSignTask task, OaSignPackage signPackage)
    {
        boolean matches = (OaSignTaskStatus.PENDING_SIGN.name().equals(task.getStatus())
                && OaSignPackageStatus.PENDING_SIGN.equals(signPackage.getStatus()))
                || (OaSignTaskStatus.VIEWED.name().equals(task.getStatus())
                && OaSignPackageStatus.PART_VIEWED.equals(signPackage.getStatus()))
                || (OaSignTaskStatus.PENDING_FINAL_CONFIRM.name().equals(task.getStatus())
                && OaSignPackageStatus.PENDING_FINAL_CONFIRM.equals(signPackage.getStatus()))
                || (OaSignTaskStatus.REFUSED.name().equals(task.getStatus())
                && OaSignPackageStatus.REFUSED.equals(signPackage.getStatus()))
                || (OaSignTaskStatus.EXPIRED.name().equals(task.getStatus())
                && OaSignPackageStatus.EXPIRED.equals(signPackage.getStatus()));
        if (!matches)
        {
            throw new ServiceException("签约任务与签约包状态不一致");
        }
    }

    private OaSignEvent appendPackageEvent(OaSignPackage signPackage, String eventType,
            String payload, Long operatorUserId, String operatorRole, String requestId,
            String ipAddress, String userAgent, Date createdTime)
    {
        OaSignEvent event = new OaSignEvent();
        event.setPackageId(signPackage.getPackageId());
        event.setEventType(eventType);
        event.setOperatorUserId(operatorUserId);
        event.setOperatorRole(operatorRole);
        event.setIpAddress(blankToNull(ipAddress));
        event.setUserAgent(blankToNull(userAgent));
        event.setEventPayload(payload);
        event.setRequestId(requestId);
        event.setPrevEventHash(eventMapper.selectLatestEventHashByPackageId(signPackage.getPackageId()));
        event.setCreateBy(operatorUserId == null ? "system" : "employee:" + operatorUserId);
        event.setCreateTime(createdTime);
        event.setEventHash(OaSignVerificationService.calculateEventHash(event));
        if (eventMapper.insertOaSignEvent(event) != 1)
        {
            throw new ServiceException("签约包终态事件记录失败");
        }
        return event;
    }

    private OaSignPackage resolveConcurrentRefusal(Long packageId, String requestId)
    {
        OaSignPackage persisted = requirePackage(packageId);
        OaSignEvent repeated = eventMapper.selectEventByTypeAndRequestId("PACKAGE_REFUSED", requestId);
        if (repeated != null && Objects.equals(repeated.getPackageId(), packageId)
                && OaSignPackageStatus.REFUSED.equals(persisted.getStatus()))
        {
            return persisted;
        }
        throw new ServiceException("签约包状态已变化，请刷新后重试");
    }

    private OaSignPackage requirePackage(Long packageId)
    {
        OaSignPackage signPackage = packageId == null ? null : packageMapper.selectOaSignPackageById(packageId);
        if (signPackage == null)
        {
            throw new ServiceException("签约包不存在");
        }
        return signPackage;
    }

    private void requireRefusalRequest(OaSignPackageRefuseRequest request)
    {
        if (request == null || StringUtils.isBlank(request.getRequestId())
                || StringUtils.isBlank(request.getDocumentVersion())
                || StringUtils.isBlank(request.getReasonCode())
                || StringUtils.isBlank(request.getReasonDetail()))
        {
            throw new ServiceException("拒签信息不完整");
        }
    }

    private void assertEmployeeTerminalSource(String status, String action)
    {
        if (!isEmployeeTerminalSource(status))
        {
            throw new ServiceException("当前签约包状态不能" + action);
        }
    }

    private boolean isEmployeeTerminalSource(String status)
    {
        return OaSignPackageStatus.PENDING_SIGN.equals(status)
                || OaSignPackageStatus.PART_VIEWED.equals(status)
                || OaSignPackageStatus.PENDING_FINAL_CONFIRM.equals(status);
    }

    private void assertDeadlineOpen(OaSignPackage signPackage, Date currentTime)
    {
        if (signPackage.getSignDeadline() == null
                || StringUtils.isBlank(signPackage.getDeadlinePolicySource())
                || signPackage.getDeadlineDaysSnapshot() == null
                || signPackage.getDeadlineDaysSnapshot() <= 0)
        {
            throw new ServiceException("签署期限策略缺失，请联系合同经办人");
        }
        if (!signPackage.getSignDeadline().after(currentTime))
        {
            throw new ServiceException("签约包已超过签署截止时间");
        }
    }

    private boolean isDue(OaSignPackage signPackage, Date currentTime)
    {
        return signPackage.getSignDeadline() != null
                && StringUtils.isNotBlank(signPackage.getDeadlinePolicySource())
                && signPackage.getDeadlineDaysSnapshot() != null
                && signPackage.getDeadlineDaysSnapshot() > 0
                && !signPackage.getSignDeadline().after(currentTime);
    }

    private String activeDocumentVersion(OaSignPackage signPackage)
    {
        return StringUtils.isNotBlank(signPackage.getFinalDocumentVersion())
                ? signPackage.getFinalDocumentVersion() : signPackage.getDocumentVersion();
    }

    private String refusalPayload(OaSignPackageRefuseRequest request)
    {
        JSONObject payload = new JSONObject();
        payload.put("documentVersion", request.getDocumentVersion());
        payload.put("reasonCode", request.getReasonCode());
        payload.put("reasonDetail", request.getReasonDetail());
        return JSON.toJSONString(payload);
    }

    private String resolutionPayload(OaSignExceptionResolutionRequest request,
            Date oldDeadline, Date newDeadline, Long replacementPackageId)
    {
        JSONObject payload = new JSONObject();
        payload.put("action", request.getAction());
        payload.put("documentVersion", request.getDocumentVersion());
        payload.put("reasonCode", request.getReasonCode());
        payload.put("reasonDetail", request.getReasonDetail());
        if (oldDeadline != null)
        {
            payload.put("oldDeadline", oldDeadline.toInstant().toString());
            payload.put("newDeadline", newDeadline == null ? null : newDeadline.toInstant().toString());
            payload.put("extensionDays", request.getExtensionDays());
        }
        if (replacementPackageId != null)
        {
            payload.put("replacementPackageId", replacementPackageId);
            payload.put("replacementPlanVersionId", request.getReplacementPlanVersionId());
        }
        return JSON.toJSONString(payload);
    }

    private boolean sameResolutionAction(OaSignEvent event, String action)
    {
        try
        {
            JSONObject payload = JSON.parseObject(event.getEventPayload());
            return payload != null && Objects.equals(payload.getString("action"), action);
        }
        catch (RuntimeException exception)
        {
            return false;
        }
    }

    private String expiryRequestId(OaSignPackage signPackage)
    {
        return "EXP-" + signPackage.getPackageId() + "-"
                + signPackage.getSignDeadline().toInstant().getEpochSecond();
    }

    private Date now()
    {
        Instant instant = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        return Date.from(instant);
    }

    private long version(OaSignPackage signPackage)
    {
        return signPackage.getVersion() == null ? 0L : signPackage.getVersion();
    }

    private String blankToNull(String value)
    {
        return StringUtils.isBlank(value) ? null : value;
    }
}
