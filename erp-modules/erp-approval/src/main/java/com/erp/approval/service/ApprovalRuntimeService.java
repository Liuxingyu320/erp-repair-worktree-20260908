package com.erp.approval.service;

import static com.erp.approval.constant.ApprovalDefinitionConstants.APPROVAL_ALL;
import static com.erp.approval.constant.ApprovalDefinitionConstants.ENGINE_NATIVE;
import static com.erp.approval.constant.ApprovalDefinitionConstants.STATUS_ACTIVE;
import static com.erp.approval.constant.ApprovalRuntimeConstants.ACTION_SKIP;
import static com.erp.approval.constant.ApprovalRuntimeConstants.ACTION_START;
import static com.erp.approval.constant.ApprovalRuntimeConstants.ACTION_TERMINATE;
import static com.erp.approval.constant.ApprovalRuntimeConstants.ACTION_WITHDRAW;
import static com.erp.approval.constant.ApprovalRuntimeConstants.CALLBACK_NONE;
import static com.erp.approval.constant.ApprovalRuntimeConstants.CALLBACK_PENDING;
import static com.erp.approval.constant.ApprovalRuntimeConstants.CANDIDATE_CANCELLED;
import static com.erp.approval.constant.ApprovalRuntimeConstants.CANDIDATE_PENDING;
import static com.erp.approval.constant.ApprovalRuntimeConstants.INSTANCE_COMPLETING;
import static com.erp.approval.constant.ApprovalRuntimeConstants.INSTANCE_RUNNING;
import static com.erp.approval.constant.ApprovalRuntimeConstants.INSTANCE_TERMINATING;
import static com.erp.approval.constant.ApprovalRuntimeConstants.INSTANCE_WITHDRAWING;
import static com.erp.approval.constant.ApprovalRuntimeConstants.TASK_PENDING;
import static com.erp.approval.constant.ApprovalRuntimeConstants.TASK_SKIPPED;
import static com.erp.approval.constant.ApprovalRuntimeConstants.TASK_WAITING;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.approval.api.domain.ApprovalInstanceSnapshot;
import com.erp.approval.api.domain.ApprovalStartRequest;
import com.erp.approval.api.domain.ApprovalStartResponse;
import com.erp.approval.api.domain.ApprovalWithdrawRequest;
import com.erp.approval.candidate.ApprovalDirectoryDept;
import com.erp.approval.constant.ApprovalBusinessCodes;
import com.erp.approval.domain.ApprovalActionLog;
import com.erp.approval.domain.ApprovalCallbackOutbox;
import com.erp.approval.domain.ApprovalInstance;
import com.erp.approval.domain.ApprovalTask;
import com.erp.approval.domain.ApprovalTaskCandidate;
import com.erp.approval.domain.ApprovalTemplate;
import com.erp.approval.domain.dto.ApprovalTerminateRequest;
import com.erp.approval.mapper.ApprovalCandidateDirectoryMapper;
import com.erp.approval.mapper.ApprovalRuntimeMapper;
import com.erp.approval.mapper.ApprovalTemplateMapper;
import com.erp.approval.service.ApprovalRoutePlan.PlannedNode;
import com.erp.approval.support.ApprovalJsonSupport;
import com.erp.common.core.exception.ServiceException;

@Service
public class ApprovalRuntimeService
{
    private final ApprovalTemplateMapper templateMapper;
    private final ApprovalRuntimeMapper runtimeMapper;
    private final ApprovalCandidateDirectoryMapper directoryMapper;
    private final ApprovalRuleMatchService ruleMatchService;
    private final ApprovalRoutePlanner routePlanner;
    private final ApprovalMonitorService monitorService;
    private final ApprovalJsonSupport jsonSupport;

    public ApprovalRuntimeService(ApprovalTemplateMapper templateMapper,
            ApprovalRuntimeMapper runtimeMapper,
            ApprovalCandidateDirectoryMapper directoryMapper,
            ApprovalRuleMatchService ruleMatchService,
            ApprovalRoutePlanner routePlanner,
            ApprovalMonitorService monitorService,
            ApprovalJsonSupport jsonSupport)
    {
        this.templateMapper = templateMapper;
        this.runtimeMapper = runtimeMapper;
        this.directoryMapper = directoryMapper;
        this.ruleMatchService = ruleMatchService;
        this.routePlanner = routePlanner;
        this.monitorService = monitorService;
        this.jsonSupport = jsonSupport;
    }

    @Transactional
    public ApprovalStartResponse start(ApprovalStartRequest request)
    {
        ApprovalInstance existing = runtimeMapper.selectInstanceByIdempotencyKey(
                request.getIdempotencyKey());
        if (existing != null)
        {
            requireSameSubmission(existing, request);
            return response(existing, false);
        }
        ApprovalInstance sameRound = runtimeMapper.selectInstanceByBusinessRound(
                request.getBusinessCode(), request.getBusinessId(),
                request.getBusinessRound());
        if (sameRound != null)
        {
            throw new ServiceException("业务轮次已存在但幂等键不一致，已拒绝重复发起");
        }

        ApprovalTemplate template = templateMapper.selectTemplateByBusinessCode(
                request.getBusinessCode());
        requireNativeActive(template);
        Long anchorDeptId = resolveAndValidateAnchor(request);
        ApprovalDirectoryDept anchor = anchorDeptId == null ? null
                : directoryMapper.selectActiveDeptById(anchorDeptId);
        if (anchorDeptId != null && anchor == null)
        {
            throw new ServiceException("审批锚点组织不存在或已停用");
        }
        String subtype = ApprovalRuleMatchService.normalizeSubtype(
                request.getBusinessSubtype());
        MatchedApprovalRule matched = ruleMatchService.match(template,
                anchorDeptId, request);
        ApprovalRoutePlan route = routePlanner.plan(template, matched.rule(),
                matched.version(), anchorDeptId, request.getApplicantId(),
                subtype, request.getVariables());
        if (!route.valid())
        {
            throw new ServiceException("审批路由不可用: "
                    + String.join("；", route.errors()));
        }
        ApprovalInstance previous = validatePrevious(request);

        PlannedNode first = route.nodes().stream()
                .filter(item -> !item.skipped()).findFirst().orElse(null);
        ApprovalInstance instance = new ApprovalInstance();
        instance.setRootInstanceId(previous == null ? null
                : (previous.getRootInstanceId() == null
                        ? previous.getInstanceId() : previous.getRootInstanceId()));
        instance.setPreviousInstanceId(previous == null ? null
                : previous.getInstanceId());
        instance.setTemplateId(template.getTemplateId());
        instance.setBusinessCode(template.getBusinessCode());
        instance.setBusinessSource(template.getBusinessSource());
        instance.setBusinessId(request.getBusinessId());
        instance.setBusinessSubtype(subtype);
        instance.setBusinessRound(request.getBusinessRound());
        instance.setIdempotencyKey(request.getIdempotencyKey());
        instance.setApplicantUserId(request.getApplicantId());
        instance.setApplicantName(request.getApplicantName());
        instance.setApplicantDeptId(request.getApplicantDeptId());
        instance.setApplicantDeptName(request.getApplicantDeptName());
        instance.setAnchorDeptId(anchorDeptId);
        instance.setAnchorDeptName(request.getAnchorDeptName() == null
                ? (anchor == null ? null : anchor.getDeptName())
                : request.getAnchorDeptName());
        instance.setRuleId(matched.rule().getRuleId());
        instance.setRuleVersionId(matched.version().getVersionId());
        instance.setRuleVersionNo(matched.version().getVersionNo());
        instance.setBusinessSnapshot(jsonSupport.write(request.getVariables()));
        instance.setBusinessDigest(jsonSupport.sha256(instance.getBusinessSnapshot()));
        Map<String, Object> routeSnapshot = new LinkedHashMap<>();
        routeSnapshot.put("route", request.getRouteSnapshot());
        routeSnapshot.put("warnings", route.warnings());
        routeSnapshot.put("skipThroughOrder", route.skipThroughOrder());
        instance.setRouteSnapshot(jsonSupport.write(routeSnapshot));
        instance.setStatus(first == null ? INSTANCE_COMPLETING : INSTANCE_RUNNING);
        instance.setCurrentNodeId(first == null ? null : first.node().getNodeId());
        instance.setCurrentNodeOrder(first == null ? null
                : first.node().getNodeOrder());
        instance.setCallbackStatus(first == null ? CALLBACK_PENDING : CALLBACK_NONE);
        instance.setApprovedActionCount(0);
        instance.setStartedTime(new Date());
        instance.setCreateBy(request.getApplicantName());
        try
        {
            runtimeMapper.insertInstance(instance);
        }
        catch (DuplicateKeyException exception)
        {
            ApprovalInstance raced = runtimeMapper.selectInstanceByIdempotencyKey(
                    request.getIdempotencyKey());
            if (raced != null)
            {
                requireSameSubmission(raced, request);
                return response(raced, false);
            }
            throw new ServiceException("审批实例重复，请使用原幂等键重试");
        }
        if (instance.getRootInstanceId() == null)
        {
            runtimeMapper.setRootInstanceIdIfNull(instance.getInstanceId());
            instance.setRootInstanceId(instance.getInstanceId());
        }

        boolean activeCreated = false;
        for (PlannedNode planned : route.nodes())
        {
            boolean active = !planned.skipped() && !activeCreated;
            ApprovalTask task = createTask(instance, planned, active);
            runtimeMapper.insertTask(task);
            int order = 0;
            for (var candidate : planned.candidates())
            {
                ApprovalTaskCandidate snapshot = new ApprovalTaskCandidate();
                snapshot.setTaskId(task.getTaskId());
                snapshot.setInstanceId(instance.getInstanceId());
                snapshot.setUserId(candidate.userId());
                snapshot.setUserName(candidate.userName());
                snapshot.setDeptId(candidate.deptId());
                snapshot.setDeptName(candidate.deptName());
                snapshot.setCandidateSourceType(candidate.sourceType());
                snapshot.setCandidateSourceCode(candidate.sourceCode());
                snapshot.setCandidateOrder(++order);
                snapshot.setCandidateStatus(planned.skipped()
                        ? CANDIDATE_CANCELLED : CANDIDATE_PENDING);
                snapshot.setCandidateReason(planned.skipped()
                        ? planned.reason() : candidate.reason());
                snapshot.setCreateBy(request.getApplicantName());
                runtimeMapper.insertCandidate(snapshot);
            }
            if (planned.skipped())
            {
                insertAction(instance, task, null, ACTION_SKIP, null,
                        "SYSTEM", TASK_WAITING, TASK_SKIPPED,
                        planned.reason(), "skip:" + planned.node().getNodeOrder(),
                        request.getApplicantName());
            }
            activeCreated = activeCreated || active;
        }
        insertAction(instance, null, null, ACTION_START,
                request.getApplicantId(), "USER", null, instance.getStatus(),
                null, request.getIdempotencyKey(), request.getApplicantName());
        if (first == null)
        {
            createOutbox(instance, template, "APPROVE", "APPROVED", null,
                    request.getApplicantId(), request.getApplicantName());
        }
        return response(instance, true);
    }

    @Transactional
    public boolean withdraw(ApprovalWithdrawRequest request)
    {
        ApprovalInstance instance = requireInstance(request.getInstanceId());
        if (!Objects.equals(instance.getApplicantUserId(), request.getApplicantId()))
        {
            throw new ServiceException("只有申请人可撤回");
        }
        if (List.of(INSTANCE_WITHDRAWING, "WITHDRAWN")
                .contains(instance.getStatus()))
        {
            return true;
        }
        if (!INSTANCE_RUNNING.equals(instance.getStatus()))
        {
            throw new ServiceException("只有审批中的实例可撤回");
        }
        if (instance.getApprovedActionCount() != null
                && instance.getApprovedActionCount() > 0)
        {
            throw new ServiceException("已有人同意，不允许撤回");
        }
        runtimeMapper.cancelOpenCandidatesByInstance(instance.getInstanceId(),
                "申请人撤回", instance.getApplicantName());
        runtimeMapper.cancelOpenTasksByInstance(instance.getInstanceId(),
                "申请人撤回", instance.getApplicantName());
        String before = instance.getStatus();
        Long expected = instance.getLockVersion();
        instance.setStatus(INSTANCE_WITHDRAWING);
        instance.setCurrentNodeId(null);
        instance.setCurrentNodeOrder(null);
        instance.setCallbackStatus(CALLBACK_PENDING);
        instance.setUpdateBy(instance.getApplicantName());
        if (runtimeMapper.updateInstanceWithLock(instance, expected, before) != 1)
        {
            throw concurrent();
        }
        insertAction(instance, null, null, ACTION_WITHDRAW,
                request.getApplicantId(), "USER", before,
                INSTANCE_WITHDRAWING, request.getReason(),
                "withdraw", instance.getApplicantName());
        ApprovalTemplate template = requireTemplate(instance.getTemplateId());
        createOutbox(instance, template, "WITHDRAW", "WITHDRAWN",
                request.getReason(), request.getApplicantId(),
                instance.getApplicantName());
        return true;
    }

    @Transactional
    public ApprovalInstance terminate(Long instanceId,
            ApprovalTerminateRequest request, Long operatorId,
            String operatorName)
    {
        ApprovalInstance instance = requireInstance(instanceId);
        String actionKey = actionKey(instanceId, ACTION_TERMINATE,
                request.getRequestId());
        ApprovalActionLog existingAction = runtimeMapper.selectActionByKey(
                actionKey);
        if (existingAction != null)
        {
            if (!ACTION_TERMINATE.equals(existingAction.getActionType())
                    || !Objects.equals(existingAction.getOperatorUserId(),
                            operatorId)
                    || !Objects.equals(existingAction.getActionReason(),
                            request.getReason()))
            {
                throw new ServiceException("请求ID已被其他终止操作使用");
            }
            return requireInstance(instanceId);
        }
        if (!INSTANCE_RUNNING.equals(instance.getStatus()))
        {
            throw new ServiceException("只能终止审批中的实例");
        }
        runtimeMapper.cancelOpenCandidatesByInstance(instanceId,
                "管理员终止: " + request.getReason(), operatorName);
        runtimeMapper.cancelOpenTasksByInstance(instanceId,
                "管理员终止: " + request.getReason(), operatorName);
        String before = instance.getStatus();
        Long expected = instance.getLockVersion();
        instance.setStatus(INSTANCE_TERMINATING);
        instance.setCurrentNodeId(null);
        instance.setCurrentNodeOrder(null);
        instance.setCallbackStatus(CALLBACK_PENDING);
        instance.setUpdateBy(operatorName);
        if (runtimeMapper.updateInstanceWithLock(instance, expected, before) != 1)
        {
            throw concurrent();
        }
        insertAction(instance, null, null, ACTION_TERMINATE, operatorId,
                "ADMIN", before, INSTANCE_TERMINATING, request.getReason(),
                request.getRequestId(), operatorName);
        ApprovalTemplate template = requireTemplate(instance.getTemplateId());
        createOutbox(instance, template, "TERMINATE", "TERMINATED",
                request.getReason(), operatorId, operatorName);
        return instance;
    }

    public ApprovalInstanceSnapshot getSnapshot(Long instanceId)
    {
        ApprovalInstance instance = requireInstance(instanceId);
        ApprovalInstanceSnapshot snapshot = new ApprovalInstanceSnapshot();
        snapshot.setInstanceId(instance.getInstanceId());
        snapshot.setBusinessCode(instance.getBusinessCode());
        snapshot.setBusinessId(instance.getBusinessId());
        snapshot.setBusinessRound(instance.getBusinessRound());
        snapshot.setApplicantUserId(instance.getApplicantUserId());
        snapshot.setRuleVersionId(instance.getRuleVersionId());
        snapshot.setStatus(instance.getStatus());
        snapshot.setCurrentNodeName(monitorService.currentNodeName(instanceId));
        return snapshot;
    }

    ApprovalCallbackOutbox createOutbox(ApprovalInstance instance,
            ApprovalTemplate template, String action, String targetStatus,
            String reason, Long operatorId, String operatorName)
    {
        ApprovalCallbackOutbox callback = new ApprovalCallbackOutbox();
        callback.setEventKey(instance.getInstanceId() + ":" + action);
        callback.setInstanceId(instance.getInstanceId());
        callback.setBusinessCode(instance.getBusinessCode());
        callback.setBusinessSource(instance.getBusinessSource());
        callback.setBusinessId(instance.getBusinessId());
        callback.setBusinessRound(instance.getBusinessRound());
        callback.setCallbackAction(action);
        callback.setCallbackService(template.getCallbackService());
        callback.setCallbackCode(instance.getBusinessCode());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("targetStatus", targetStatus);
        payload.put("reason", reason);
        payload.put("operatorId", operatorId);
        payload.put("operatorName", operatorName);
        payload.put("businessSnapshot", instance.getBusinessSnapshot());
        callback.setPayload(jsonSupport.write(payload));
        callback.setCallbackStatus(CALLBACK_PENDING);
        callback.setRetryCount(0);
        callback.setMaxRetryCount(10);
        callback.setNextRetryTime(new Date());
        callback.setCreateBy(operatorName);
        runtimeMapper.insertCallback(callback);
        return callback;
    }

    void insertAction(ApprovalInstance instance, ApprovalTask task,
            ApprovalTaskCandidate candidate, String type, Long operatorId,
            String source, String before, String after, String reason,
            String requestId, String operatorName)
    {
        ApprovalActionLog action = new ApprovalActionLog();
        action.setActionKey(actionKey(instance.getInstanceId(), type, requestId));
        action.setInstanceId(instance.getInstanceId());
        action.setTaskId(task == null ? null : task.getTaskId());
        action.setCandidateId(candidate == null ? null : candidate.getCandidateId());
        action.setActionType(type);
        action.setOperatorUserId(operatorId);
        action.setOperatorName(operatorName);
        action.setOperatorSource(source);
        action.setFromStatus(before);
        action.setToStatus(after);
        action.setActionReason(reason);
        action.setRequestId(requestId);
        action.setCreateBy(operatorName);
        runtimeMapper.insertAction(action);
    }

    static String actionKey(Long instanceId, String type, String requestId)
    {
        return instanceId + ":" + type + ":"
                + (requestId == null || requestId.isBlank() ? "default" : requestId);
    }

    private ApprovalTask createTask(ApprovalInstance instance,
            PlannedNode planned, boolean active)
    {
        ApprovalTask task = new ApprovalTask();
        task.setInstanceId(instance.getInstanceId());
        task.setNodeId(planned.node().getNodeId());
        task.setNodeOrder(planned.node().getNodeOrder());
        task.setNodeCode(planned.node().getNodeCode());
        task.setNodeName(planned.node().getNodeName());
        task.setTaskStatus(planned.skipped() ? TASK_SKIPPED
                : active ? TASK_PENDING : TASK_WAITING);
        task.setApprovalMode(planned.node().getApprovalMode());
        task.setRequiredCount(APPROVAL_ALL.equals(planned.node().getApprovalMode())
                ? planned.candidates().size() : 1);
        task.setCompletedCount(0);
        task.setActivatedTime(active && !planned.skipped() ? new Date() : null);
        task.setCompletedTime(planned.skipped() ? new Date() : null);
        task.setCreateBy(instance.getApplicantName());
        task.setRemark(planned.reason());
        return task;
    }

    private ApprovalInstance validatePrevious(ApprovalStartRequest request)
    {
        if (request.getBusinessRound() == 1)
        {
            if (request.getPreviousInstanceId() != null)
            {
                throw new ServiceException("首轮审批不应指定上一轮实例");
            }
            return null;
        }
        if (request.getPreviousInstanceId() == null)
        {
            throw new ServiceException("重新提交必须指定上一轮审批实例");
        }
        ApprovalInstance previous = requireInstance(request.getPreviousInstanceId());
        if (!Objects.equals(previous.getBusinessCode(), request.getBusinessCode())
                || !Objects.equals(previous.getBusinessId(), request.getBusinessId())
                || previous.getBusinessRound() + 1 != request.getBusinessRound())
        {
            throw new ServiceException("上一轮审批实例与当前业务轮次不匹配");
        }
        if (!List.of("RETURNED", "WITHDRAWN").contains(previous.getStatus()))
        {
            throw new ServiceException("只有已退回或已撤回的申请可重新提交");
        }
        return previous;
    }

    private Long resolveAndValidateAnchor(ApprovalStartRequest request)
    {
        if (!ApprovalBusinessCodes.INV_TRANSFER.equals(request.getBusinessCode()))
        {
            return request.getAnchorDeptId();
        }
        String type = value(request.getVariables().get("transferType"));
        Long source = longValue(request.getVariables().get("sourceDeptId"));
        Long target = longValue(request.getVariables().get("targetDeptId"));
        Long expected = "store_return".equalsIgnoreCase(type) ? source : target;
        if (expected == null)
        {
            throw new ServiceException("调拨发起必须提供transferType/sourceDeptId/targetDeptId");
        }
        if (request.getAnchorDeptId() != null
                && !request.getAnchorDeptId().equals(expected))
        {
            throw new ServiceException("调拨锚点错误：门店返仓取来源门店，其他调拨取目标门店");
        }
        return expected;
    }

    private void requireSameSubmission(ApprovalInstance existing,
            ApprovalStartRequest request)
    {
        if (!Objects.equals(existing.getBusinessCode(), request.getBusinessCode())
                || !Objects.equals(existing.getBusinessId(), request.getBusinessId())
                || !Objects.equals(existing.getBusinessRound(), request.getBusinessRound())
                || !Objects.equals(existing.getApplicantUserId(), request.getApplicantId()))
        {
            throw new ServiceException("幂等键已被不同业务请求使用");
        }
        Long expectedAnchor = resolveAndValidateAnchor(request);
        String expectedSubtype = ApprovalRuleMatchService.normalizeSubtype(
                request.getBusinessSubtype());
        String expectedDigest = jsonSupport.sha256(jsonSupport.write(
                request.getVariables()));
        if (!Objects.equals(existing.getAnchorDeptId(), expectedAnchor)
                || !Objects.equals(existing.getBusinessSubtype(),
                        expectedSubtype)
                || !Objects.equals(existing.getApplicantDeptId(),
                        request.getApplicantDeptId())
                || !Objects.equals(existing.getPreviousInstanceId(),
                        request.getPreviousInstanceId())
                || !Objects.equals(existing.getBusinessDigest(),
                        expectedDigest))
        {
            throw new ServiceException("幂等键已被不同路由内容使用");
        }
    }

    private void requireNativeActive(ApprovalTemplate template)
    {
        if (template == null || !ENGINE_NATIVE.equals(template.getEngineMode())
                || !STATUS_ACTIVE.equals(template.getTemplateStatus()))
        {
            throw new ServiceException("该业务尚未启用统一审批引擎");
        }
    }

    ApprovalInstance requireInstance(Long id)
    {
        ApprovalInstance value = runtimeMapper.selectInstanceById(id);
        if (value == null) throw new ServiceException("审批实例不存在");
        return value;
    }

    ApprovalTemplate requireTemplate(Long id)
    {
        ApprovalTemplate value = templateMapper.selectTemplateById(id);
        if (value == null) throw new ServiceException("审批模板不存在");
        return value;
    }

    private ApprovalStartResponse response(ApprovalInstance instance, boolean created)
    {
        ApprovalStartResponse response = new ApprovalStartResponse();
        response.setInstanceId(instance.getInstanceId());
        response.setBusinessRound(instance.getBusinessRound());
        response.setStatus(instance.getStatus());
        response.setCreated(created);
        return response;
    }

    static ServiceException concurrent()
    {
        return new ServiceException("审批状态已变更，请刷新后重试");
    }

    private static String value(Object value)
    {
        return value == null ? null : value.toString();
    }

    private static Long longValue(Object value)
    {
        if (value instanceof Number number) return number.longValue();
        try { return value == null ? null : Long.valueOf(value.toString()); }
        catch (NumberFormatException ignored) { return null; }
    }
}
