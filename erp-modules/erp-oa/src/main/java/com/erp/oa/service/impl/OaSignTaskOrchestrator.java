package com.erp.oa.service.impl;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.oa.api.domain.HrEmployeeSigningSnapshot;
import com.erp.oa.api.domain.HrRenewalGuard;
import com.erp.oa.api.domain.HrSignBusinessEvent;
import com.erp.oa.constant.OaSignOperatorType;
import com.erp.oa.constant.OaSignPackageStatus;
import com.erp.oa.constant.OaSignPlanScope;
import com.erp.oa.constant.OaSignSigningSequence;
import com.erp.oa.constant.OaSignTaskStatus;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPlanVersion;
import com.erp.oa.domain.OaSignTask;
import com.erp.oa.domain.OaSignOnboardImportBatch;
import com.erp.oa.domain.OaSignOnboardImportRow;
import com.erp.oa.domain.vo.OaSignDraftDecision;
import com.erp.oa.mapper.OaSignPackageMapper;
import com.erp.oa.mapper.OaSignPlanVersionMapper;
import com.erp.oa.mapper.OaHrRenewalGuardMapper;
import com.erp.oa.mapper.OaSignTaskMapper;
import com.erp.oa.service.IOaSignPackageService;
import com.erp.oa.service.IOaSignTaskService;
import com.erp.oa.service.rule.OaSignScenarioRule;

/**
 * Coordinator for versioned HR signing events.
 *
 * <p>Each durable step uses an explicit REQUIRES_NEW transaction so a later rule, file or
 * infrastructure failure cannot roll the already-created task out of existence. The dedicated
 * employee-first staging entry point is the exception: it intentionally joins the import service's
 * transaction so its data request, task and real package shell are atomic.</p>
 */
@Service
public class OaSignTaskOrchestrator
{
    private static final String PUBLISHED = "PUBLISHED";
    private static final String MATCHING_ENABLED = "ENABLED";
    private static final String DEFAULT_RISK = "NORMAL";
    private static final int MAX_FAILURE_DETAIL = 1000;
    private static final ZoneId SIGNING_ZONE = ZoneId.of("Asia/Shanghai");

    private final List<OaSignScenarioRule> rules;
    private final IOaSignTaskService taskService;
    private final OaSignTaskMapper taskMapper;
    private final OaSignTaskEventService eventService;
    private final OaSignPlanVersionMapper versionMapper;
    private final OaSignPackageMapper packageMapper;
    private final IOaSignPackageService packageService;
    private final OaHrRenewalGuardMapper renewalGuardMapper;
    private final OaSignNotificationOutboxService notificationOutboxService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate requiresNew;

    public OaSignTaskOrchestrator(List<OaSignScenarioRule> rules,
            IOaSignTaskService taskService,
            OaSignTaskMapper taskMapper,
            OaSignTaskEventService eventService,
            OaSignPlanVersionMapper versionMapper,
            OaSignPackageMapper packageMapper,
            IOaSignPackageService packageService,
            OaHrRenewalGuardMapper renewalGuardMapper,
            OaSignNotificationOutboxService notificationOutboxService,
            PlatformTransactionManager transactionManager,
            ObjectMapper objectMapper)
    {
        this.rules = rules == null ? List.of() : List.copyOf(rules);
        this.taskService = taskService;
        this.taskMapper = taskMapper;
        this.eventService = eventService;
        this.versionMapper = versionMapper;
        this.packageMapper = packageMapper;
        this.packageService = packageService;
        this.renewalGuardMapper = renewalGuardMapper;
        this.notificationOutboxService = notificationOutboxService;
        this.objectMapper = objectMapper;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public Long orchestrate(HrSignBusinessEvent event)
    {
        validateEventEnvelope(event);
        OaSignTask task = findExistingTask(event);
        boolean existingTask = task != null;
        if (existingTask && isTerminalRenewalTask(task))
        {
            OaSignTask terminalTask = task;
            inNewTransaction(() -> {
                eventService.reconcileTerminalRenewalGuard(terminalTask);
                return terminalTask;
            });
        }
        OaSignScenarioRule selectedRule = null;
        String selectionFailure = null;
        DedupeResolution dedupe = null;
        if (!existingTask)
        {
            List<OaSignScenarioRule> matchedRules = selectRules(event.getScenario());
            selectionFailure = matchedRules.isEmpty() ? "PLAN_NOT_FOUND"
                    : matchedRules.size() > 1 ? "PLAN_CONFLICT" : null;
            selectedRule = matchedRules.size() == 1 ? matchedRules.get(0) : null;
            dedupe = resolveDedupeKey(selectedRule, event);
            DedupeResolution creationDedupe = dedupe;
            task = inNewTransaction(() -> createTaskWithRenewalGuard(
                    event, creationDedupe.key));
        }
        if (task == null || task.getTaskId() == null)
        {
            throw new ServiceException("签约任务创建失败");
        }
        Long taskId = task.getTaskId();
        // ONBOARD的员工级开放任务唯一约束可能在不同来源并发创建时
        // 返回已有任务。此时只能复用事实，不能用本次事件快照推进其状态。
        if (!existingTask && "ONBOARD".equalsIgnoreCase(event.getScenario().trim())
                && !sameSourceEvent(task, event))
        {
            return taskId;
        }
        OaSignDraftDecision evaluatedDecision = null;
        try
        {
            task = startOrResumeValidation(taskId, event);
            if (isAlreadyHandled(task))
            {
                return taskId;
            }
            if (hasAnyVersionedPackageBinding(task))
            {
                resumeVersionedDraft(taskId, event);
                return taskId;
            }
            if (existingTask)
            {
                List<OaSignScenarioRule> matchedRules = selectRules(event.getScenario());
                selectionFailure = matchedRules.isEmpty() ? "PLAN_NOT_FOUND"
                        : matchedRules.size() > 1 ? "PLAN_CONFLICT" : null;
                selectedRule = matchedRules.size() == 1 ? matchedRules.get(0) : null;
                dedupe = resolveDedupeKey(selectedRule, event);
            }
            if (dedupe.infrastructureFailure != null)
            {
                throw dedupe.infrastructureFailure;
            }
            if (dedupe.businessFailure != null)
            {
                if ("REGULARIZE".equalsIgnoreCase(task.getScenario()))
                {
                    evaluatedDecision = safelyEvaluateDedupeFailureContext(
                            selectedRule, event);
                    persistPostDecisionBusinessFailure(taskId, "DEDUPE_KEY_INVALID",
                            safeMessage(dedupe.businessFailure), evaluatedDecision, task, event);
                }
                else
                {
                    persistBusinessFailure(taskId, "DEDUPE_KEY_INVALID",
                            safeMessage(dedupe.businessFailure), event);
                }
                return taskId;
            }
            if (selectionFailure != null)
            {
                persistBusinessFailure(taskId, selectionFailure, selectionFailure, event);
                return taskId;
            }

            try
            {
                evaluatedDecision = selectedRule.decide(event);
            }
            catch (ServiceException ex)
            {
                persistBusinessFailure(taskId, "VALIDATION_FAILED", safeMessage(ex), event);
                return taskId;
            }
            applyDecision(taskId, event, evaluatedDecision);
            return taskId;
        }
        catch (IOaSignPackageService.DraftValidationException ex)
        {
            persistPostDecisionBusinessFailure(taskId, ex.getReasonCode(),
                    safeMessage(ex), evaluatedDecision, task, event);
            return taskId;
        }
        catch (BusinessValidationException ex)
        {
            persistPostDecisionBusinessFailure(taskId, ex.code,
                    ex.getMessage(), evaluatedDecision, task, event);
            return taskId;
        }
        catch (RuntimeException ex)
        {
            persistInfrastructureFailure(taskId, ex, event);
            throw ex;
        }
    }

    /**
     * Creates and sends the real package shell used by the employee-first onboarding flow.
     *
     * <p>This deliberately joins the caller's transaction. The import row, its internal data
     * request, the task, package, lifecycle events and employee notification therefore commit or
     * roll back as one unit. No contract PDF is prepared at this stage: the employee is confirming
     * the frozen facts and planned document list, while company and seal snapshots stay empty until
     * HR performs the company stage.</p>
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public OaSignPackage stageSignatureFirstPackageInCurrentTransaction(HrSignBusinessEvent event)
    {
        validateEventEnvelope(event);
        if (!OaSignSigningSequence.signatureFirst(
                attributeText(event, "signingSequence")))
        {
            throw new ServiceException("仅先确认事实并留签名流程可创建首阶段签约包");
        }
        if (event.getOperatorUserId() == null || event.getOperatorUserId() <= 0)
        {
            throw new ServiceException("首阶段签约包缺少有效HR操作人");
        }

        OaSignTask existing = findExistingTask(event);
        if (existing != null)
        {
            OaSignTask locked = lockTaskForEvent(existing.getTaskId(), event);
            return requireSameStagedPackage(locked, event);
        }

        List<OaSignScenarioRule> matchedRules = selectRules(event.getScenario());
        if (matchedRules.size() != 1)
        {
            throw new ServiceException(matchedRules.isEmpty()
                    ? "未匹配到已发布签约方案版本" : "匹配到多个签约方案规则");
        }
        OaSignScenarioRule selectedRule = matchedRules.get(0);
        DedupeResolution dedupe = resolveDedupeKey(selectedRule, event);
        if (dedupe.infrastructureFailure != null)
        {
            throw dedupe.infrastructureFailure;
        }
        if (dedupe.businessFailure != null)
        {
            throw dedupe.businessFailure;
        }

        OaSignTask stagedTask = newTask(event, dedupe.key);
        // The event may carry an Excel recommendation solely so the rule can select a plan.
        // It is not the HR-frozen employer decision for this employee-first flow.
        stagedTask.setLegalEntityId(null);
        OaSignTask task = taskService.createTask(stagedTask);
        if (task == null || task.getTaskId() == null || !sameSourceEvent(task, event))
        {
            throw new ServiceException("该员工已有其他未完成的入职签约任务");
        }
        task = lockTaskForEvent(task.getTaskId(), event);
        if (parseStatus(task) != OaSignTaskStatus.NEW)
        {
            return requireSameStagedPackage(task, event);
        }
        eventService.transition(task, OaSignTaskStatus.VALIDATING,
                OaSignOperatorType.HR, event.getOperatorUserId(),
                "SIGNATURE_FIRST_PACKAGE_STAGE", "发送员工首阶段签约包",
                stagedEventRequestId(event), null, null);

        OaSignDraftDecision decision = selectedRule.decide(event);
        if (decision == null || decision.getAction() != OaSignDraftDecision.Action.CREATE_DRAFT
                || decision.getPlanVersionId() == null || decision.getDraftPackage() == null)
        {
            throw new ServiceException("签约事实尚不足以创建首阶段签约包");
        }
        OaSignPlanVersion version = versionMapper.lockPlanVersionById(decision.getPlanVersionId());
        validateLockedVersion(version, task);
        requireDeadlineDays(version);

        OaSignPackage signPackage = decision.getDraftPackage();
        bindServerOwnedDraft(signPackage, task, event, version);
        clearUnfrozenCompanyAndSeal(signPackage);
        signPackage.setSigningSequence(OaSignSigningSequence.SIGNATURE_FIRST);
        if (packageMapper.insertOaSignPackage(signPackage) != 1
                || signPackage.getPackageId() == null)
        {
            throw new ServiceException("首阶段签约包创建失败");
        }
        if (taskMapper.bindVersionedPackage(task.getTaskId(), signPackage.getPackageId(),
                version.getVersionId(), normalizeRisk(decision.getRiskLevel()), task.getVersion(),
                task.getAssignedHrUserId()) != 1)
        {
            throw new ServiceException("签约任务与首阶段签约包关联失败");
        }
        task.setPackageId(signPackage.getPackageId());
        task.setPlanVersionId(version.getVersionId());
        task.setRiskLevel(normalizeRisk(decision.getRiskLevel()));

        DeadlineSnapshot deadline = deadline(version);
        signPackage.setStatus(OaSignPackageStatus.PENDING_SIGN);
        signPackage.setConfirmStatus(OaSignTaskStatus.PENDING_SIGN.name());
        signPackage.setDocumentVersion("SP-" + signPackage.getPackageId() + "-V1");
        signPackage.setSentTime(deadline.sentTime());
        signPackage.setSignDeadline(deadline.signDeadline());
        signPackage.setDeadlinePolicySource(deadline.policySource());
        signPackage.setDeadlineDaysSnapshot(deadline.days());
        signPackage.setUpdateBy("system");
        Long stagedPackageVersion = signPackage.getVersion();
        if (packageMapper.sendStagedShell(signPackage, stagedPackageVersion) != 1)
        {
            throw new ServiceException("首阶段签约包发送状态已变化");
        }
        signPackage.setVersion(signPackage.getVersion() + 1);

        eventService.transition(task, OaSignTaskStatus.DRAFT_CREATED,
                OaSignOperatorType.SYSTEM, null, "STAGED_PACKAGE_CREATED", null,
                null, null, null);
        eventService.transition(task, OaSignTaskStatus.READY_TO_SEND,
                OaSignOperatorType.SYSTEM, null, "STAGED_PACKAGE_READY", null,
                null, null, null);
        eventService.transition(task, OaSignTaskStatus.SENDING,
                OaSignOperatorType.HR, event.getOperatorUserId(), "STAGED_PACKAGE_SENDING", null,
                null, null, null);
        eventService.transition(task, OaSignTaskStatus.PENDING_SIGN,
                OaSignOperatorType.SYSTEM, null, "STAGED_PACKAGE_SENT", null,
                null, null, null);
        if (taskMapper.updateSentLifecycle(task.getTaskId(), task.getStatus(), task.getVersion(),
                task.getAssignedHrUserId(), deadline.sentTime(), deadline.signDeadline(),
                deadline.policySource(), deadline.days()) != 1)
        {
            throw new ServiceException("首阶段签约任务期限冻结失败");
        }
        task.setSentTime(deadline.sentTime());
        task.setSignDeadline(deadline.signDeadline());
        task.setDeadlinePolicySource(deadline.policySource());
        task.setDeadlineDaysSnapshot(deadline.days());
        notificationOutboxService.enqueueSent(task, signPackage);
        return signPackage;
    }

    private OaSignTask findExistingTask(HrSignBusinessEvent event)
    {
        OaSignTask query = new OaSignTask();
        query.setScenario(event.getScenario().trim().toUpperCase(Locale.ROOT));
        query.setEmployeeId(event.getEmployeeId());
        query.setSourceType(event.getSourceType());
        query.setSourceBusinessId(event.getSourceBusinessId());
        query.setSourceEventVersion(String.valueOf(event.getSourceEventVersion()));
        return taskMapper.selectCanonicalTaskBySourceEvent(query);
    }

    private OaSignPackage requireSameStagedPackage(OaSignTask task,
            HrSignBusinessEvent event)
    {
        if (!sameSourceEvent(task, event) || task.getPackageId() == null
                || task.getPlanVersionId() == null)
        {
            throw new ServiceException("签约任务已存在但不是当前首阶段签约包");
        }
        OaSignTaskStatus status = parseStatus(task);
        if (!List.of(OaSignTaskStatus.PENDING_SIGN, OaSignTaskStatus.VIEWED,
                OaSignTaskStatus.PENDING_COMPANY, OaSignTaskStatus.PENDING_FINAL_CONFIRM,
                OaSignTaskStatus.SIGNED).contains(status))
        {
            throw new ServiceException("已有同源签约任务尚未形成可复用的首阶段签约包");
        }
        OaSignPackage signPackage = packageMapper.selectOaSignPackageById(task.getPackageId());
        if (signPackage == null || !Objects.equals(signPackage.getTaskId(), task.getTaskId())
                || !Objects.equals(signPackage.getPlanVersionId(), task.getPlanVersionId())
                || !OaSignSigningSequence.signatureFirst(signPackage.getSigningSequence())
                || StringUtils.isBlank(signPackage.getDocumentVersion()))
        {
            throw new ServiceException("首阶段签约包关联不一致");
        }
        return signPackage;
    }

    private String attributeText(HrSignBusinessEvent event, String name)
    {
        Object value = attributeValue(event, name);
        return value == null ? null : String.valueOf(value).trim();
    }

    private String stagedEventRequestId(HrSignBusinessEvent event)
    {
        String requestId = "STAGE:" + safeKeyPart(event.getSourceBusinessId()) + ":"
                + event.getSourceEventVersion();
        return requestId.length() <= 64 ? requestId
                : "STAGE:" + UUID.nameUUIDFromBytes(
                        requestId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private void clearUnfrozenCompanyAndSeal(OaSignPackage signPackage)
    {
        signPackage.setLegalEntityIdSnapshot(null);
        signPackage.setLegalEntityCodeSnapshot(null);
        signPackage.setLegalEntityNameSnapshot(null);
        signPackage.setLegalEntitySourceDeptId(null);
        signPackage.setLegalEntityResolveMode(null);
        signPackage.setLegalEntityCreditCodeSnapshot(null);
        signPackage.setLegalEntityAddressSnapshot(null);
        signPackage.setLegalRepresentativeSnapshot(null);
        signPackage.setLegalEntityPhoneSnapshot(null);
        signPackage.setLegalEntityOverrideReason(null);
        signPackage.setSealIdSnapshot(null);
        signPackage.setSealNameSnapshot(null);
        signPackage.setSealImageUrlSnapshot(null);
        signPackage.setSealImageHashSnapshot(null);
        signPackage.setCompanyFrozenTime(null);
    }

    private void requireDeadlineDays(OaSignPlanVersion version)
    {
        Integer days = version == null ? null : version.getSignDeadlineDays();
        if (days == null || days < 1 || days > 365)
        {
            throw new ServiceException("冻结方案版本的签署期限必须在1至365天之间");
        }
    }

    private DeadlineSnapshot deadline(OaSignPlanVersion version)
    {
        requireDeadlineDays(version);
        Date sentTime = new Date(System.currentTimeMillis() / 1000 * 1000);
        Date signDeadline = Date.from(sentTime.toInstant().atZone(SIGNING_ZONE).toLocalDate()
                .plusDays(version.getSignDeadlineDays()).atTime(LocalTime.of(23, 59, 59))
                .atZone(SIGNING_ZONE).toInstant().truncatedTo(ChronoUnit.SECONDS));
        return new DeadlineSnapshot(sentTime, signDeadline,
                "PLAN_VERSION", version.getSignDeadlineDays());
    }

    private OaSignTask createTaskWithRenewalGuard(HrSignBusinessEvent event,
            String dedupeKey)
    {
        if (!"RENEWAL".equalsIgnoreCase(event.getScenario().trim()))
        {
            return taskService.createTask(newTask(event, dedupeKey));
        }

        Long actionId = requireRenewalActionId(event);
        HrRenewalGuard guard = renewalGuardMapper.selectForUpdate(
                event.getEmployeeId(), "RENEWAL");
        if (guard == null || guard.getVersion() == null)
        {
            throw new ServiceException("续签任务门闩不存在，等待系统事件恢复");
        }
        if ("IDLE".equals(guard.getStatus()))
        {
            if (renewalGuardMapper.reserveIdleForEarliestRecoverableAction(
                    event.getEmployeeId(), "RENEWAL", actionId, guard.getVersion()) != 1)
            {
                throw new ServiceException("续签任务门闩未为当前最早未结业务动作预留");
            }
            guard.setStatus("RESERVED");
            guard.setActionId(actionId);
            guard.setTaskId(null);
            guard.setVersion(guard.getVersion() + 1);
        }
        if ("ACTIVE".equals(guard.getStatus()))
        {
            if (Objects.equals(actionId, guard.getActionId()) && guard.getTaskId() != null)
            {
                OaSignTask sameSource = taskMapper.lockOaSignTaskById(guard.getTaskId());
                if (sameSourceEvent(sameSource, event))
                {
                    reconcileGuardIfTerminal(sameSource);
                    return sameSource;
                }
            }
            throw new ServiceException("该员工已有未完成续签任务");
        }
        if (!"RESERVED".equals(guard.getStatus())
                || !Objects.equals(actionId, guard.getActionId())
                || guard.getTaskId() != null)
        {
            throw new ServiceException("续签任务门闩未为当前业务动作预留");
        }

        OaSignTask open = taskMapper.selectOpenRenewalTaskForUpdate(event.getEmployeeId());
        if (open != null)
        {
            if (!sameSourceEvent(open, event))
            {
                throw new ServiceException("该员工已有未完成续签任务");
            }
            if (reconcileGuardIfTerminal(open))
            {
                return open;
            }
            activateRenewalGuard(guard, actionId, open.getTaskId());
            return open;
        }

        OaSignTask created = taskService.createTask(newTask(event, dedupeKey));
        if (created == null || created.getTaskId() == null || !sameSourceEvent(created, event))
        {
            throw new ServiceException("续签任务创建结果与来源业务动作不一致");
        }
        if (reconcileGuardIfTerminal(created))
        {
            return created;
        }
        activateRenewalGuard(guard, actionId, created.getTaskId());
        return created;
    }

    private boolean reconcileGuardIfTerminal(OaSignTask task)
    {
        if (!isTerminalRenewalTask(task))
        {
            return false;
        }
        eventService.reconcileTerminalRenewalGuard(task);
        return true;
    }

    private void activateRenewalGuard(HrRenewalGuard guard, Long actionId, Long taskId)
    {
        if (renewalGuardMapper.activate(guard.getEmployeeId(), "RENEWAL", actionId,
                taskId, guard.getVersion()) != 1)
        {
            throw new ServiceException("续签任务门闩激活失败");
        }
    }

    private Long requireRenewalActionId(HrSignBusinessEvent event)
    {
        if (!"HR_LIFECYCLE_ACTION".equalsIgnoreCase(event.getSourceType().trim())
                || !event.getSourceBusinessId().matches("[1-9][0-9]*"))
        {
            throw new ServiceException("续签事件必须来源于有效生命周期业务动作");
        }
        try
        {
            return Long.valueOf(event.getSourceBusinessId());
        }
        catch (NumberFormatException ex)
        {
            throw new ServiceException("续签事件生命周期业务编号无效");
        }
    }

    private boolean sameSourceEvent(OaSignTask task, HrSignBusinessEvent event)
    {
        return task != null && equalsIgnoreCase(task.getScenario(), event.getScenario())
                && Objects.equals(task.getEmployeeId(), event.getEmployeeId())
                && equalsIgnoreCase(task.getSourceType(), event.getSourceType())
                && Objects.equals(task.getSourceBusinessId(), event.getSourceBusinessId())
                && Objects.equals(task.getSourceEventVersion(),
                        String.valueOf(event.getSourceEventVersion()));
    }

    private boolean isTerminalRenewalTask(OaSignTask task)
    {
        return task != null && "RENEWAL".equalsIgnoreCase(task.getScenario())
                && parseStatus(task).isTerminal();
    }

    private boolean hasAnyVersionedPackageBinding(OaSignTask task)
    {
        return task.getPackageId() != null;
    }

    private void resumeVersionedDraft(Long taskId, HrSignBusinessEvent event)
    {
        inNewTransaction(() -> {
            OaSignTask current = lockTaskForEvent(taskId, event);
            if (!OaSignTaskStatus.VALIDATING.name().equals(current.getStatus()))
            {
                if (hasAnyVersionedPackageBinding(current)
                        && hasReached(current, OaSignTaskStatus.DRAFT_CREATED))
                {
                    return packageMapper.selectOaSignPackageById(current.getPackageId());
                }
                throw new ServiceException("签约任务状态已变化");
            }
            return resumeBoundPackage(current);
        });
        transition(taskId, OaSignTaskStatus.DRAFT_CREATED, "DRAFT_CREATED", null, event);
        transition(taskId, OaSignTaskStatus.READY_TO_SEND,
                "REVIEW_NOT_REQUIRED", "当前流程无需审核，可直接发送", event);
    }

    private void applyDecision(Long taskId, HrSignBusinessEvent event, OaSignDraftDecision decision)
    {
        if (decision == null || decision.getAction() == null)
        {
            throw new BusinessValidationException("DECISION_INVALID", "场景规则未返回有效决策");
        }
        if (decision.getAction() == OaSignDraftDecision.Action.NEEDS_DATA)
        {
            String reason = firstReason(decision, "NEEDS_DATA");
            persistBusinessFailure(taskId, reason, joinReasons(decision, reason),
                    decision.getRiskLevel(), event);
            return;
        }
        if (decision.getAction() == OaSignDraftDecision.Action.NO_ACTION)
        {
            if (decision.getPlanVersionId() != null)
            {
                persistNoActionDecision(taskId, decision, event);
            }
            else
            {
                transition(taskId, OaSignTaskStatus.NO_ACTION,
                        firstReason(decision, "NO_ACTION"),
                        joinReasons(decision, "NO_ACTION"), event);
            }
            return;
        }
        if (decision.getPlanVersionId() == null)
        {
            throw new BusinessValidationException("PLAN_NOT_FOUND", "未匹配到已发布签约方案版本");
        }
        if (decision.getDraftPackage() == null)
        {
            throw new BusinessValidationException("DRAFT_DATA_MISSING", "场景规则未生成签约草稿");
        }

        persistVersionedDraft(taskId, event, decision);
        transition(taskId, OaSignTaskStatus.DRAFT_CREATED, "DRAFT_CREATED", null, event);
        transition(taskId, OaSignTaskStatus.READY_TO_SEND,
                "REVIEW_NOT_REQUIRED", "当前流程无需审核，可直接发送", event);
    }

    private void persistNoActionDecision(Long taskId, OaSignDraftDecision decision,
            HrSignBusinessEvent event)
    {
        String riskLevel = normalizeRisk(decision.getRiskLevel());
        String reasonCode = firstReason(decision, "NO_ACTION");
        String reasonDetail = joinReasons(decision, "NO_ACTION");
        try
        {
            inNewTransaction(() -> {
                OaSignTask current = lockTaskForEvent(taskId, event);
                if (OaSignTaskStatus.NO_ACTION.name().equals(current.getStatus()))
                {
                    if (sameNoActionDecision(current, decision.getPlanVersionId(), riskLevel))
                    {
                        return current;
                    }
                    throw new BusinessValidationException(
                            "PLAN_CONFLICT", "NO_ACTION任务终态决定不一致");
                }
                if (!OaSignTaskStatus.VALIDATING.name().equals(current.getStatus()))
                {
                    throw new ServiceException("签约任务状态已变化");
                }
                if (current.getPackageId() != null)
                {
                    throw new ServiceException("无需处理的任务不能绑定签约包");
                }
                if (current.getPlanVersionId() == null)
                {
                    OaSignPlanVersion version =
                            versionMapper.lockPlanVersionById(decision.getPlanVersionId());
                    validateLockedVersion(version, current);
                    if (taskMapper.bindNoActionDecision(current.getTaskId(),
                            version.getVersionId(), riskLevel, current.getVersion(),
                            current.getAssignedHrUserId()) != 1)
                    {
                        throw new ServiceException("无需处理的任务决定信息绑定失败");
                    }
                    current.setPlanVersionId(version.getVersionId());
                    current.setRiskLevel(riskLevel);
                    current.setVersion(current.getVersion() + 1);
                }
                else if (!sameNoActionDecision(
                        current, decision.getPlanVersionId(), riskLevel))
                {
                    throw new BusinessValidationException(
                            "PLAN_CONFLICT", "NO_ACTION任务方案版本已被其他决定绑定");
                }
                return eventService.transition(current, OaSignTaskStatus.NO_ACTION,
                        OaSignOperatorType.SYSTEM, null, reasonCode, limit(reasonDetail),
                        null, null, null);
            });
        }
        catch (ServiceException ex)
        {
            OaSignTask persisted = reloadTaskForEvent(taskId, event);
            if (persisted != null
                    && OaSignTaskStatus.NO_ACTION.name().equals(persisted.getStatus())
                    && sameNoActionDecision(
                        persisted, decision.getPlanVersionId(), riskLevel))
            {
                return;
            }
            throw ex;
        }
    }

    private boolean sameNoActionDecision(OaSignTask task, Long planVersionId,
            String riskLevel)
    {
        return task != null && Objects.equals(task.getPlanVersionId(), planVersionId)
                && Objects.equals(normalizeRisk(task.getRiskLevel()), riskLevel)
                && task.getPackageId() == null;
    }

    private OaSignTask startOrResumeValidation(Long taskId, HrSignBusinessEvent event)
    {
        OaSignTask current = loadTask(taskId);
        OaSignTaskStatus status = parseStatus(current);
        if (status == OaSignTaskStatus.NEW || status == OaSignTaskStatus.NEEDS_DATA
                || status == OaSignTaskStatus.FAILED)
        {
            boolean manualInitiation = status == OaSignTaskStatus.NEW
                    && event != null
                    && "HR_PROFILE_CONTRACT_INITIATION".equalsIgnoreCase(event.getSourceType());
            if (manualInitiation)
            {
                return transitionWithFailure(taskId, OaSignTaskStatus.VALIDATING,
                        "MANUAL_ONBOARD_INITIATION", manualInitiationAuditDetail(event),
                        null, null, true, OaSignOperatorType.HR,
                        requiredManualInitiationOperator(event),
                        manualInitiationEventRequestId(event));
            }
            boolean excelImport = event != null
                    && "MANUAL_SIGN_EXCEL_IMPORT".equalsIgnoreCase(event.getSourceType());
            if (excelImport)
            {
                return transitionWithFailure(taskId, OaSignTaskStatus.VALIDATING,
                        "MANUAL_SIGN_EXCEL_IMPORT", excelImportAuditDetail(event),
                        null, null, true, OaSignOperatorType.HR,
                        requiredManualInitiationOperator(event), excelImportEventRequestId(event),
                        event);
            }
            return transitionWithFailure(taskId, OaSignTaskStatus.VALIDATING,
                    status == OaSignTaskStatus.NEW ? "EVENT_RECEIVED" : "EVENT_REDELIVERED",
                    null, null, null, event);
        }
        if (status == OaSignTaskStatus.DRAFT_CREATED)
        {
            return transition(taskId, OaSignTaskStatus.READY_TO_SEND,
                    "EVENT_REDELIVERED", "当前流程无需审核，可直接发送", event);
        }
        if (status == OaSignTaskStatus.READY_TO_SEND)
        {
            return inNewTransaction(() -> {
                OaSignTask locked = lockTaskForEvent(taskId, event);
                if (parseStatus(locked) != OaSignTaskStatus.READY_TO_SEND)
                {
                    throw new ServiceException("签约任务状态已变化");
                }
                enqueueReadyToSendNotification(locked);
                return locked;
            });
        }
        return current;
    }

    private String manualInitiationAuditDetail(HrSignBusinessEvent event)
    {
        Object confirmed = event.getAttributes() == null ? null
                : event.getAttributes().get("noExternalContractConfirmed");
        Object requestId = event.getAttributes() == null ? null
                : event.getAttributes().get("initiationRequestId");
        Object batchRequestId = event.getAttributes() == null ? null
                : event.getAttributes().get("initiationBatchRequestId");
        Object reason = event.getAttributes() == null ? null
                : event.getAttributes().get("initiationReason");
        String detail = "noExternalContractConfirmed=" + Boolean.TRUE.equals(confirmed)
                + ";requestId=" + auditValue(requestId)
                + ";initiationBatchRequestId=" + auditValue(batchRequestId)
                + ";operatorUserId=" + String.valueOf(event.getOperatorUserId())
                + ";initiationReason=" + auditValue(reason);
        return limit(detail);
    }

    private String excelImportAuditDetail(HrSignBusinessEvent event)
    {
        return limit("batchId=" + auditAttribute(event, "importBatchId")
                + ";rowId=" + auditAttribute(event, "importRowId")
                + ";requestId=" + auditAttribute(event, "generationRequestId")
                + ";contractEffectiveDate=" + auditAttribute(event, "contractEffectiveDate")
                + ";noExternalContractConfirmed="
                + Boolean.TRUE.equals(attributeValue(event, "noExternalContractConfirmed"))
                + ";historicalSupplement="
                + Boolean.TRUE.equals(attributeValue(event, "historicalSupplement"))
                + ";historicalReason=" + auditAttribute(event, "historicalSupplementReason")
                + ";salaryWarningReason=" + auditAttribute(event, "salaryWarningReason"));
    }

    private Object attributeValue(HrSignBusinessEvent event, String name)
    {
        return event == null || event.getAttributes() == null ? null
                : event.getAttributes().get(name);
    }

    private String auditAttribute(HrSignBusinessEvent event, String name)
    {
        return auditValue(attributeValue(event, name));
    }

    private String excelImportEventRequestId(HrSignBusinessEvent event)
    {
        String generationRequestId = attributeText(event, "generationRequestId");
        if (StringUtils.isBlank(generationRequestId)
                || generationRequestId.indexOf('\r') >= 0
                || generationRequestId.indexOf('\n') >= 0)
        {
            throw new ServiceException("Excel导入生成请求编号无效");
        }
        String identity = event.getSourceBusinessId() + ":"
                + event.getSourceEventVersion() + ":" + generationRequestId;
        return "EXCEL:" + UUID.nameUUIDFromBytes(
                identity.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private Long requiredManualInitiationOperator(HrSignBusinessEvent event)
    {
        if (event.getOperatorUserId() == null || event.getOperatorUserId() <= 0)
        {
            throw new ServiceException("历史员工首次发起缺少有效操作人");
        }
        return event.getOperatorUserId();
    }

    private String manualInitiationEventRequestId(HrSignBusinessEvent event)
    {
        Object value = event.getAttributes() == null ? null
                : event.getAttributes().get("initiationRequestId");
        String requestId = value == null ? null : String.valueOf(value).trim();
        if (StringUtils.isBlank(requestId) || requestId.length() > 64
                || requestId.indexOf('\r') >= 0 || requestId.indexOf('\n') >= 0)
        {
            throw new ServiceException("历史员工首次发起请求编号无效");
        }
        return requestId;
    }

    private String auditValue(Object value)
    {
        if (value == null)
        {
            return "";
        }
        return String.valueOf(value).trim()
                .replace('\r', ' ').replace('\n', ' ')
                .replace(';', '，').replace('=', '-');
    }

    private boolean isAlreadyHandled(OaSignTask task)
    {
        return parseStatus(task) != OaSignTaskStatus.VALIDATING;
    }

    private void persistVersionedDraft(Long taskId, HrSignBusinessEvent event,
            OaSignDraftDecision decision)
    {
        inNewTransaction(() -> {
            OaSignTask current = lockTaskForEvent(taskId, event);
            if (!OaSignTaskStatus.VALIDATING.name().equals(current.getStatus()))
            {
                if (current.getPackageId() != null
                        && Objects.equals(current.getPlanVersionId(), decision.getPlanVersionId())
                        && hasReached(current, OaSignTaskStatus.DRAFT_CREATED))
                {
                    return packageMapper.selectOaSignPackageById(current.getPackageId());
                }
                throw new ServiceException("签约任务状态已变化");
            }
            if (current.getPackageId() != null || current.getPlanVersionId() != null)
            {
                return resumeBoundPackage(current);
            }
            OaSignPlanVersion version = versionMapper.lockPlanVersionById(decision.getPlanVersionId());
            validateLockedVersion(version, current);
            OaSignPackage signPackage = resolveOrCreatePackage(current, event, decision, version);
            packageService.preparePackageDocumentsForSystem(
                    signPackage.getPackageId(), current.getTaskId(), current.getAssignedHrUserId());
            current.setPlanVersionId(version.getVersionId());
            current.setPackageId(signPackage.getPackageId());
            current.setRiskLevel(normalizeRisk(decision.getRiskLevel()));
            return signPackage;
        });
    }

    private OaSignPackage resumeBoundPackage(OaSignTask task)
    {
        if (task.getPackageId() == null || task.getPlanVersionId() == null)
        {
            throw new ServiceException("签约任务历史草稿关联不完整");
        }
        OaSignPlanVersion version = versionMapper.lockPlanVersionById(task.getPlanVersionId());
        validateBoundVersion(version, task);
        OaSignPackage signPackage = resolveOrCreatePackage(task, null, null, version);
        packageService.preparePackageDocumentsForSystem(
                signPackage.getPackageId(), task.getTaskId(), task.getAssignedHrUserId());
        return signPackage;
    }

    private OaSignPackage resolveOrCreatePackage(OaSignTask task, HrSignBusinessEvent event,
            OaSignDraftDecision decision, OaSignPlanVersion version)
    {
        if (task.getPackageId() != null)
        {
            OaSignPackage existing = packageMapper.selectOaSignPackageById(task.getPackageId());
            if (existing == null || !Objects.equals(existing.getTaskId(), task.getTaskId())
                    || !Objects.equals(existing.getPlanVersionId(), version.getVersionId()))
            {
                throw new ServiceException("签约任务草稿关联不一致");
            }
            return existing;
        }
        OaSignPackage signPackage = decision.getDraftPackage();
        bindServerOwnedDraft(signPackage, task, event, version);
        if (packageMapper.insertOaSignPackage(signPackage) != 1 || signPackage.getPackageId() == null)
        {
            throw new ServiceException("签约包草稿创建失败");
        }
        if (taskMapper.bindVersionedPackage(task.getTaskId(), signPackage.getPackageId(),
                version.getVersionId(), normalizeRisk(decision.getRiskLevel()), task.getVersion(),
                task.getAssignedHrUserId()) != 1)
        {
            throw new ServiceException("签约任务版本草稿关联失败");
        }
        task.setPackageId(signPackage.getPackageId());
        task.setPlanVersionId(version.getVersionId());
        task.setRiskLevel(normalizeRisk(decision.getRiskLevel()));
        return signPackage;
    }

    private void validateLockedVersion(OaSignPlanVersion version, OaSignTask task)
    {
        if (version == null)
        {
            throw new BusinessValidationException("PLAN_NOT_FOUND", "签约方案版本不存在");
        }
        if (!PUBLISHED.equals(version.getPublishStatus())
                || !MATCHING_ENABLED.equals(version.getMatchingStatus()))
        {
            throw new BusinessValidationException("PLAN_NOT_FOUND", "签约方案版本未发布或已停止新匹配");
        }
        if (!equalsIgnoreCase(version.getScenario(), task.getScenario())
                || !OaSignPlanScope.appliesTo(version.getShopDeptId(), task.getShopDeptId()))
        {
            throw new BusinessValidationException("PLAN_CONFLICT", "签约方案版本与任务范围不一致");
        }
    }

    private void validateBoundVersion(OaSignPlanVersion version, OaSignTask task)
    {
        if (version == null || !PUBLISHED.equals(version.getPublishStatus()))
        {
            throw new ServiceException("签约任务历史方案版本无效");
        }
        if (!equalsIgnoreCase(version.getScenario(), task.getScenario())
                || !OaSignPlanScope.appliesTo(version.getShopDeptId(), task.getShopDeptId()))
        {
            throw new ServiceException("签约任务历史方案版本范围不一致");
        }
    }

    private void bindServerOwnedDraft(OaSignPackage signPackage, OaSignTask task,
            HrSignBusinessEvent event, OaSignPlanVersion version)
    {
        HrEmployeeSigningSnapshot snapshot = event.getAfterSnapshot() != null
                ? event.getAfterSnapshot() : event.getBeforeSnapshot();
        signPackage.setPackageId(null);
        signPackage.setPackageNo(generatePackageNo());
        signPackage.setEmployeeId(task.getEmployeeId());
        signPackage.setShopDeptId(task.getShopDeptId());
        signPackage.setSourcePlanId(version.getPlanId());
        signPackage.setSourcePlanName(version.getPlanName());
        signPackage.setScenario(task.getScenario().toLowerCase(Locale.ROOT));
        signPackage.setTaskId(task.getTaskId());
        signPackage.setPlanVersionId(version.getVersionId());
        signPackage.setStatus(OaSignPackageStatus.DRAFT);
        signPackage.setConfirmStatus(OaSignTaskStatus.NEEDS_DATA.name());
        signPackage.setVersion(0L);
        signPackage.setCreateBy("system");
        signPackage.setDocumentVersion(null);
        signPackage.setSentTime(null);
        signPackage.setViewedTime(null);
        signPackage.setSignedTime(null);
        if (snapshot != null)
        {
            fillMissingSnapshot(signPackage, snapshot);
        }
    }

    private void fillMissingSnapshot(OaSignPackage signPackage, HrEmployeeSigningSnapshot snapshot)
    {
        if (StringUtils.isBlank(signPackage.getEmployeeNameSnapshot()))
            signPackage.setEmployeeNameSnapshot(snapshot.getEmployeeName());
        if (StringUtils.isBlank(signPackage.getEmployeePhoneSnapshot()))
            signPackage.setEmployeePhoneSnapshot(snapshot.getPhone());
        if (StringUtils.isBlank(signPackage.getEmployeeIdCardSnapshot()))
            signPackage.setEmployeeIdCardSnapshot(snapshot.getIdNumber());
        if (StringUtils.isBlank(signPackage.getEmployeeAddressSnapshot()))
            signPackage.setEmployeeAddressSnapshot(snapshot.getCurrentAddress());
        if (signPackage.getDeptIdSnapshot() == null)
            signPackage.setDeptIdSnapshot(snapshot.getDeptId());
        if (StringUtils.isBlank(signPackage.getDeptNameSnapshot()))
            signPackage.setDeptNameSnapshot(snapshot.getDeptName());
        if (StringUtils.isBlank(signPackage.getShopDeptName()))
            signPackage.setShopDeptName(snapshot.getShopDeptName());
        if (StringUtils.isBlank(signPackage.getPostNameSnapshot()))
            signPackage.setPostNameSnapshot(snapshot.getPostName());
        if (StringUtils.isBlank(signPackage.getPostLevelSnapshot()))
            signPackage.setPostLevelSnapshot(snapshot.getJobGradeCode());
        if (StringUtils.isBlank(signPackage.getEmploymentType()))
            signPackage.setEmploymentType(snapshot.getContractTypeCode());
        if (StringUtils.isBlank(signPackage.getContractTermCodeSnapshot()))
            signPackage.setContractTermCodeSnapshot(snapshot.getContractTermCode());
        if (StringUtils.isBlank(signPackage.getSocialType()))
            signPackage.setSocialType(snapshot.getSocialTypeCode());
        if (StringUtils.isBlank(signPackage.getSalaryVersion()))
            signPackage.setSalaryVersion(snapshot.getSalaryVersion());
        if (StringUtils.isBlank(signPackage.getEntryDate()))
            signPackage.setEntryDate(text(snapshot.getEntryDate()));
        if (StringUtils.isBlank(signPackage.getContractStartDate()))
            signPackage.setContractStartDate(text(snapshot.getContractStartDate()));
        if (StringUtils.isBlank(signPackage.getContractEndDate()))
            signPackage.setContractEndDate(text(snapshot.getContractEndDate()));
        if (StringUtils.isBlank(signPackage.getProbationStartDate()))
            signPackage.setProbationStartDate(text(snapshot.getProbationStartDate()));
        if (StringUtils.isBlank(signPackage.getProbationEndDate()))
            signPackage.setProbationEndDate(text(snapshot.getProbationEndDate()));
        if (StringUtils.isBlank(signPackage.getActualRegularizationDate()))
            signPackage.setActualRegularizationDate(text(snapshot.getActualRegularizationDate()));
        if (signPackage.getBaseSalary() == null) signPackage.setBaseSalary(snapshot.getBaseSalary());
        if (signPackage.getPostSalary() == null) signPackage.setPostSalary(snapshot.getPostSalary());
        if (signPackage.getFieldAllowance() == null) signPackage.setFieldAllowance(snapshot.getFieldAllowance());
        if (signPackage.getPerformanceSalary() == null) signPackage.setPerformanceSalary(snapshot.getPerformanceSalary());
        if (signPackage.getSalaryTotal() == null) signPackage.setSalaryTotal(snapshot.getSalaryTotal());
    }

    private OaSignTask transition(Long taskId, OaSignTaskStatus target,
            String reasonCode, String reasonDetail)
    {
        return transitionWithFailure(taskId, target, reasonCode, reasonDetail,
                null, null, false, OaSignOperatorType.SYSTEM, null, null);
    }

    private OaSignTask transition(Long taskId, OaSignTaskStatus target,
            String reasonCode, String reasonDetail, HrSignBusinessEvent event)
    {
        return transitionWithFailure(taskId, target, reasonCode, reasonDetail,
                null, null, false, OaSignOperatorType.SYSTEM, null, null, event);
    }

    private OaSignTask transitionWithFailure(Long taskId, OaSignTaskStatus target,
            String reasonCode, String reasonDetail, String failureCode, String failureDetail)
    {
        return transitionWithFailure(taskId, target, reasonCode, reasonDetail,
                failureCode, failureDetail, true, OaSignOperatorType.SYSTEM, null, null);
    }

    private OaSignTask transitionWithFailure(Long taskId, OaSignTaskStatus target,
            String reasonCode, String reasonDetail, String failureCode, String failureDetail,
            HrSignBusinessEvent event)
    {
        return transitionWithFailure(taskId, target, reasonCode, reasonDetail,
                failureCode, failureDetail, true, OaSignOperatorType.SYSTEM, null, null, event);
    }

    private OaSignTask transitionWithFailure(Long taskId, OaSignTaskStatus target,
            String reasonCode, String reasonDetail, String failureCode, String failureDetail,
            boolean replaceFailure, OaSignOperatorType operatorType,
            Long operatorUserId, String requestId)
    {
        return transitionWithFailure(taskId, target, reasonCode, reasonDetail,
                failureCode, failureDetail, replaceFailure, operatorType,
                operatorUserId, requestId, null);
    }

    private OaSignTask transitionWithFailure(Long taskId, OaSignTaskStatus target,
            String reasonCode, String reasonDetail, String failureCode, String failureDetail,
            boolean replaceFailure, OaSignOperatorType operatorType,
            Long operatorUserId, String requestId, HrSignBusinessEvent event)
    {
        try
        {
            return inNewTransaction(() -> {
                OaSignTask current = isExcelImportEvent(event)
                        ? lockTaskForEvent(taskId, event) : loadTask(taskId);
                if (hasReached(current, target))
                {
                    return current;
                }
                if (replaceFailure)
                {
                    current.setFailureCode(failureCode);
                    current.setFailureDetail(limit(failureDetail));
                    current.setNextRetryTime(null);
                }
                OaSignTask transitioned = eventService.transition(current, target, operatorType,
                        operatorUserId, reasonCode, limit(reasonDetail), requestId, null, null);
                if (target == OaSignTaskStatus.READY_TO_SEND)
                {
                    enqueueReadyToSendNotification(transitioned);
                }
                return transitioned;
            });
        }
        catch (ServiceException ex)
        {
            OaSignTask persisted = reloadTaskForEvent(taskId, event);
            if (hasReached(persisted, target))
            {
                return persisted;
            }
            throw ex;
        }
    }

    private void enqueueReadyToSendNotification(OaSignTask task)
    {
        if (task.getPackageId() == null)
        {
            throw new ServiceException("待发送任务缺少签约包");
        }
        OaSignPackage signPackage = packageMapper.selectOaSignPackageById(task.getPackageId());
        if (signPackage == null || !Objects.equals(signPackage.getTaskId(), task.getTaskId())
                || !Objects.equals(signPackage.getPlanVersionId(), task.getPlanVersionId()))
        {
            throw new ServiceException("待发送任务与签约包关联不一致");
        }
        notificationOutboxService.enqueueWaitingHr(task, signPackage);
    }

    private boolean hasReached(OaSignTask task, OaSignTaskStatus target)
    {
        OaSignTaskStatus current = parseStatus(task);
        if (current == target)
        {
            return true;
        }
        if (target == OaSignTaskStatus.VALIDATING)
        {
            return current != OaSignTaskStatus.NEW
                    && current != OaSignTaskStatus.NEEDS_DATA
                    && current != OaSignTaskStatus.FAILED;
        }
        if (target == OaSignTaskStatus.DRAFT_CREATED)
        {
            return current == OaSignTaskStatus.WAITING_HR_CONFIRM
                    || isAfterHrConfirmation(current);
        }
        if (target == OaSignTaskStatus.WAITING_HR_CONFIRM)
        {
            return isAfterHrConfirmation(current);
        }
        return false;
    }

    private boolean isAfterHrConfirmation(OaSignTaskStatus status)
    {
        return status == OaSignTaskStatus.READY_TO_SEND
                || status == OaSignTaskStatus.SENDING
                || status == OaSignTaskStatus.PENDING_SIGN
                || status == OaSignTaskStatus.VIEWED
                || status == OaSignTaskStatus.PENDING_COMPANY
                || status == OaSignTaskStatus.PENDING_FINAL_CONFIRM
                || status == OaSignTaskStatus.SIGNED
                || status == OaSignTaskStatus.REFUSED
                || status == OaSignTaskStatus.EXPIRED
                || status == OaSignTaskStatus.CANCELLED;
    }

    private void persistBusinessFailure(Long taskId, String code, String detail)
    {
        persistBusinessFailure(taskId, code, detail, null, null);
    }

    private void persistBusinessFailure(Long taskId, String code, String detail,
            HrSignBusinessEvent event)
    {
        persistBusinessFailure(taskId, code, detail, null, event);
    }

    private OaSignDraftDecision safelyEvaluateDedupeFailureContext(
            OaSignScenarioRule rule, HrSignBusinessEvent event)
    {
        try
        {
            return rule == null ? null : rule.decide(event);
        }
        catch (RuntimeException ignored)
        {
            return null;
        }
    }

    private void persistPostDecisionBusinessFailure(Long taskId, String code,
            String detail, OaSignDraftDecision decision, OaSignTask task,
            HrSignBusinessEvent event)
    {
        boolean salaryChanged = decision != null
                && decision.getReasonCodes().stream()
                    .anyMatch("SALARY_CHANGED"::equals);
        if (!salaryChanged && task != null
                && "REGULARIZE".equalsIgnoreCase(task.getScenario())
                && "REVIEW_REQUIRED".equals(normalizeRisk(task.getRiskLevel())))
        {
            salaryChanged = true;
        }
        String riskLevel = salaryChanged ? "REVIEW_REQUIRED"
                : decision != null && !StringUtils.isBlank(decision.getRiskLevel())
                    ? decision.getRiskLevel() : task == null ? null : task.getRiskLevel();
        String enrichedDetail = detail;
        if (salaryChanged && (enrichedDetail == null
                || !enrichedDetail.contains("SALARY_CHANGED")))
        {
            enrichedDetail = StringUtils.isBlank(enrichedDetail)
                    ? "SALARY_CHANGED" : enrichedDetail + ",SALARY_CHANGED";
        }
        persistBusinessFailure(taskId, code, enrichedDetail, riskLevel, event);
    }

    private void persistBusinessFailure(Long taskId, String code, String detail,
            String riskLevel, HrSignBusinessEvent event)
    {
        String normalizedRisk = StringUtils.isBlank(riskLevel)
                ? null : normalizeRisk(riskLevel);
        try
        {
            inNewTransaction(() -> {
                OaSignTask current = lockTaskForEvent(taskId, event);
                OaSignTaskStatus status = parseStatus(current);
                if (status == OaSignTaskStatus.NEEDS_DATA)
                {
                    if (normalizedRisk == null || normalizedRisk.equals(
                            normalizeRisk(current.getRiskLevel())))
                    {
                        notificationOutboxService.enqueueNeedsData(current);
                        return current;
                    }
                    throw new ServiceException("待补资料任务风险决定不一致");
                }
                if (status != OaSignTaskStatus.VALIDATING)
                {
                    return current;
                }
                if (normalizedRisk != null
                        && !normalizedRisk.equals(normalizeRisk(current.getRiskLevel())))
                {
                    if (taskMapper.updateRiskLevelForValidation(current.getTaskId(),
                            normalizedRisk, current.getVersion(),
                            current.getAssignedHrUserId()) != 1)
                    {
                        throw new ServiceException("待补资料任务风险更新失败");
                    }
                    current.setRiskLevel(normalizedRisk);
                }
                current.setFailureCode(code);
                current.setFailureDetail(limit(detail));
                current.setNextRetryTime(null);
                OaSignTask transitioned = eventService.transition(current, OaSignTaskStatus.NEEDS_DATA,
                        OaSignOperatorType.SYSTEM, null, code, limit(detail),
                        null, null, null);
                notificationOutboxService.enqueueNeedsData(transitioned);
                return transitioned;
            });
        }
        catch (ServiceException ex)
        {
            OaSignTask persisted = reloadTaskForEvent(taskId, event);
            if (parseStatus(persisted) == OaSignTaskStatus.NEEDS_DATA
                    && (normalizedRisk == null || normalizedRisk.equals(
                        normalizeRisk(persisted.getRiskLevel()))))
            {
                return;
            }
            throw ex;
        }
    }

    private void persistInfrastructureFailure(Long taskId, RuntimeException failure,
            HrSignBusinessEvent event)
    {
        try
        {
            OaSignTask current = reloadTaskForEvent(taskId, event);
            OaSignTaskStatus status = parseStatus(current);
            if (status == OaSignTaskStatus.FAILED
                    || status == OaSignTaskStatus.NEEDS_DATA
                    || status == OaSignTaskStatus.WAITING_HR_CONFIRM
                    || isAfterHrConfirmation(status) || status.isTerminal())
            {
                return;
            }
            transitionWithFailure(taskId, OaSignTaskStatus.FAILED,
                    "INFRASTRUCTURE_FAILURE", safeMessage(failure),
                    "INFRASTRUCTURE_FAILURE", safeMessage(failure), event);
        }
        catch (RuntimeException persistenceFailure)
        {
            failure.addSuppressed(persistenceFailure);
        }
    }

    private OaSignTask newTask(HrSignBusinessEvent event, String dedupeKey)
    {
        HrEmployeeSigningSnapshot snapshot = event.getAfterSnapshot() != null
                ? event.getAfterSnapshot() : event.getBeforeSnapshot();
        OaSignTask task = new OaSignTask();
        task.setScenario(event.getScenario().trim().toUpperCase(Locale.ROOT));
        task.setEmployeeId(event.getEmployeeId());
        task.setShopDeptId(snapshot == null ? null : snapshot.getShopDeptId());
        task.setLegalEntityId(snapshot == null ? null : snapshot.getLegalEntityId());
        task.setAssignedHrUserId(event.getOperatorUserId());
        task.setSourceType(event.getSourceType());
        task.setSourceBusinessId(event.getSourceBusinessId());
        task.setSourceEventVersion(String.valueOf(event.getSourceEventVersion()));
        task.setDedupeKey(dedupeKey);
        task.setBeforeSnapshotJson(writeSnapshot(event.getBeforeSnapshot()));
        task.setAfterSnapshotJson(writeSnapshot(event.getAfterSnapshot()));
        if ("TRANSFER".equalsIgnoreCase(task.getScenario())
                || "OFFBOARD".equalsIgnoreCase(task.getScenario())
                || "MANUAL_SIGN_EXCEL_IMPORT".equalsIgnoreCase(task.getSourceType()))
        {
            task.setBusinessEffectiveDate("MANUAL_SIGN_EXCEL_IMPORT".equalsIgnoreCase(
                    task.getSourceType()) ? attributeDate(event, "contractEffectiveDate")
                    : lifecycleEffectiveDate(event));
            Object historical = event.getAttributes() == null ? null
                    : event.getAttributes().get("historicalSupplement");
            task.setHistoricalSupplement(historical instanceof Boolean value ? value : null);
        }
        task.setAutomationLevel("MANUAL");
        task.setRiskLevel(DEFAULT_RISK);
        return task;
    }

    private LocalDate attributeDate(HrSignBusinessEvent event, String name)
    {
        Object value = attributeValue(event, name);
        try
        {
            return value == null ? null : value instanceof LocalDate date
                    ? date : LocalDate.parse(String.valueOf(value).trim());
        }
        catch (RuntimeException ignored)
        {
            return null;
        }
    }

    private String writeSnapshot(HrEmployeeSigningSnapshot snapshot)
    {
        if (snapshot == null) return null;
        try
        {
            return objectMapper.writer()
                    .without(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .writeValueAsString(snapshot);
        }
        catch (JsonProcessingException exception)
        {
            throw new ServiceException("签约任务快照序列化失败")
                    .setDetailMessage(exception.getMessage());
        }
    }

    private LocalDate lifecycleEffectiveDate(HrSignBusinessEvent event)
    {
        Object value = event.getAttributes() == null ? null
                : event.getAttributes().get("effectiveDate");
        try
        {
            return value == null ? null : LocalDate.parse(String.valueOf(value).trim());
        }
        catch (RuntimeException ignored)
        {
            return null;
        }
    }

    private List<OaSignScenarioRule> selectRules(String scenario)
    {
        String normalizedScenario = scenario.trim().toUpperCase(Locale.ROOT);
        List<OaSignScenarioRule> matched = new ArrayList<>();
        for (OaSignScenarioRule rule : rules)
        {
            if (rule != null && rule.supports(normalizedScenario))
            {
                matched.add(rule);
            }
        }
        return matched;
    }

    private String requireRuleDedupeKey(OaSignScenarioRule rule, HrSignBusinessEvent event)
    {
        String key = rule.dedupeKey(event);
        if (StringUtils.isBlank(key) || key.length() > 180)
        {
            throw new ServiceException("场景规则去重键无效");
        }
        return key.trim();
    }

    private DedupeResolution resolveDedupeKey(OaSignScenarioRule rule, HrSignBusinessEvent event)
    {
        String fallback = fallbackDedupeKey(event);
        if (rule == null)
        {
            return new DedupeResolution(fallback, null, null);
        }
        try
        {
            return new DedupeResolution(requireRuleDedupeKey(rule, event), null, null);
        }
        catch (ServiceException ex)
        {
            return new DedupeResolution(fallback, ex, null);
        }
        catch (RuntimeException ex)
        {
            return new DedupeResolution(fallback, null, ex);
        }
    }

    private String fallbackDedupeKey(HrSignBusinessEvent event)
    {
        return "EVENT:" + safeKeyPart(event.getEventId()) + ":" + event.getSourceEventVersion();
    }

    private void validateEventEnvelope(HrSignBusinessEvent event)
    {
        if (event == null || StringUtils.isBlank(event.getEventId())
                || StringUtils.isBlank(event.getScenario()) || event.getEmployeeId() == null
                || StringUtils.isBlank(event.getSourceType())
                || StringUtils.isBlank(event.getSourceBusinessId())
                || event.getSourceEventVersion() == null)
        {
            throw new ServiceException("人事签约事件来源信息不完整");
        }
    }

    private OaSignTask loadTask(Long taskId)
    {
        OaSignTask task = taskMapper.selectOaSignTaskById(taskId);
        if (task == null)
        {
            throw new ServiceException("签约任务不存在");
        }
        return task;
    }

    private OaSignTask lockTaskForEvent(Long taskId, HrSignBusinessEvent event)
    {
        OaSignTask task = taskMapper.lockOaSignTaskById(taskId);
        if (task == null)
        {
            throw new ServiceException("签约任务不存在");
        }
        requireExcelImportTaskOwner(task, event);
        return task;
    }

    /**
     * Keeps the initiating Excel-import owner stable across package and import-row writes.
     * Reassignment waits for this transaction and cannot make an old request self-authorize by
     * reloading the new owner.
     */
    <T> T withLockedExcelImportTask(OaSignOnboardImportRow row,
            OaSignOnboardImportBatch batch, Long taskId, Long initiatingOperatorUserId,
            Function<OaSignTask, T> work)
    {
        if (row == null || batch == null || taskId == null || work == null
                || !Objects.equals(row.getBatchId(), batch.getBatchId()))
        {
            throw new ServiceException("Excel导入生成任务绑定不完整");
        }
        return inNewTransaction(() -> {
            OaSignTask query = new OaSignTask();
            query.setScenario("ONBOARD");
            query.setEmployeeId(row.getEmployeeId());
            query.setSourceType("MANUAL_SIGN_EXCEL_IMPORT");
            query.setSourceBusinessId(String.valueOf(row.getRowId()));
            query.setSourceEventVersion(String.valueOf(row.getSourceEventVersion()));
            List<OaSignTask> exactTasks = taskMapper.selectExactTasksBySourceEvent(query);
            OaSignTask canonical = taskMapper.selectCanonicalTaskBySourceEvent(query);
            if (exactTasks == null || exactTasks.size() != 1
                    || canonical == null
                    || !Objects.equals(exactTasks.get(0).getTaskId(), taskId)
                    || !Objects.equals(canonical.getTaskId(), taskId))
            {
                throw new ServiceException("Excel导入签约任务不是当前来源事件的规范任务");
            }
            OaSignTask locked = taskMapper.lockOaSignTaskById(taskId);
            List<OaSignTask> lockedSourceTasks = taskMapper.selectExactTasksBySourceEvent(query);
            if (lockedSourceTasks == null || lockedSourceTasks.size() != 1
                    || !Objects.equals(lockedSourceTasks.get(0).getTaskId(), taskId))
            {
                throw new ServiceException("同一Excel来源事件存在多个签约任务");
            }
            if (!sameExcelImportSource(locked, row)
                    || !Objects.equals(locked.getShopDeptId(), batch.getShopDeptId()))
            {
                throw new ServiceException("Excel导入签约任务来源或门店已变化");
            }
            if (initiatingOperatorUserId == null || initiatingOperatorUserId <= 0
                    || !Objects.equals(initiatingOperatorUserId,
                            locked.getAssignedHrUserId()))
            {
                throw new ServiceException("Excel导入签约任务已改派，原经办人不能继续生成");
            }
            return work.apply(locked);
        });
    }

    private boolean sameExcelImportSource(OaSignTask task, OaSignOnboardImportRow row)
    {
        return task != null && row != null && row.getRowId() != null
                && row.getSourceEventVersion() != null
                && "ONBOARD".equalsIgnoreCase(task.getScenario())
                && Objects.equals(task.getEmployeeId(), row.getEmployeeId())
                && "MANUAL_SIGN_EXCEL_IMPORT".equalsIgnoreCase(task.getSourceType())
                && Objects.equals(task.getSourceBusinessId(), String.valueOf(row.getRowId()))
                && Objects.equals(task.getSourceEventVersion(),
                        String.valueOf(row.getSourceEventVersion()));
    }

    private OaSignTask reloadTaskForEvent(Long taskId, HrSignBusinessEvent event)
    {
        if (!isExcelImportEvent(event)) return loadTask(taskId);
        return inNewTransaction(() -> lockTaskForEvent(taskId, event));
    }

    /**
     * Excel import writes follow the current task assignment, not the caller's administrator
     * role. An administrator must first become the assigned HR owner before replaying the event.
     * The comparison runs while the task row is locked so reassignment cannot race the write.
     */
    private void requireExcelImportTaskOwner(OaSignTask task, HrSignBusinessEvent event)
    {
        if (!isExcelImportEvent(event)) return;
        if (!sameSourceEvent(task, event))
        {
            throw new ServiceException("Excel导入签约任务来源已变化，不能继续生成");
        }
        HrEmployeeSigningSnapshot snapshot = event.getAfterSnapshot() != null
                ? event.getAfterSnapshot() : event.getBeforeSnapshot();
        if (snapshot == null || snapshot.getShopDeptId() == null
                || !Objects.equals(task.getShopDeptId(), snapshot.getShopDeptId()))
        {
            throw new ServiceException("Excel导入签约任务门店与事件快照不一致");
        }
        Long operatorUserId = event.getOperatorUserId();
        if (operatorUserId == null || operatorUserId <= 0
                || !Objects.equals(operatorUserId, task.getAssignedHrUserId()))
        {
            throw new ServiceException("Excel导入签约任务已改派，原经办人不能继续生成");
        }
    }

    private boolean isExcelImportEvent(HrSignBusinessEvent event)
    {
        return event != null && "MANUAL_SIGN_EXCEL_IMPORT".equalsIgnoreCase(
                event.getSourceType());
    }

    private OaSignTaskStatus parseStatus(OaSignTask task)
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

    private <T> T inNewTransaction(java.util.function.Supplier<T> work)
    {
        return requiresNew.execute(status -> work.get());
    }

    private String firstReason(OaSignDraftDecision decision, String fallback)
    {
        return decision.getReasonCodes().stream()
                .filter(code -> !StringUtils.isBlank(code)).findFirst().orElse(fallback);
    }

    private String joinReasons(OaSignDraftDecision decision, String fallback)
    {
        List<String> reasons = decision.getReasonCodes().stream()
                .filter(code -> !StringUtils.isBlank(code)).toList();
        return reasons.isEmpty() ? fallback : String.join(",", reasons);
    }

    private static String normalizeRisk(String risk)
    {
        return StringUtils.isBlank(risk) ? DEFAULT_RISK : risk.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean equalsIgnoreCase(String left, String right)
    {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private static String safeKeyPart(String value)
    {
        return value.trim().replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private static String safeMessage(Throwable throwable)
    {
        return throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
    }

    private static String limit(String value)
    {
        return value == null || value.length() <= MAX_FAILURE_DETAIL
                ? value : value.substring(0, MAX_FAILURE_DETAIL);
    }

    private static String text(LocalDate value)
    {
        return value == null ? null : value.toString();
    }

    private static String generatePackageNo()
    {
        return "SP" + System.currentTimeMillis()
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
    }

    private static final class BusinessValidationException extends RuntimeException
    {
        private static final long serialVersionUID = 1L;
        private final String code;

        private BusinessValidationException(String code, String message)
        {
            super(message);
            this.code = code;
        }
    }

    private static final class DedupeResolution
    {
        private final String key;
        private final ServiceException businessFailure;
        private final RuntimeException infrastructureFailure;

        private DedupeResolution(String key, ServiceException businessFailure,
                RuntimeException infrastructureFailure)
        {
            this.key = key;
            this.businessFailure = businessFailure;
            this.infrastructureFailure = infrastructureFailure;
        }
    }

    private record DeadlineSnapshot(Date sentTime, Date signDeadline,
            String policySource, Integer days) {}
}
