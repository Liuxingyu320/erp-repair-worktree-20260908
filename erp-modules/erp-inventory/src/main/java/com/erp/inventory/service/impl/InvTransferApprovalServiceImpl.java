package com.erp.inventory.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.constant.InvTransferApprovalNodeRoles;
import com.erp.inventory.constant.InvStatusConstants;
import com.erp.inventory.constant.InvTransferSourceConfirmStatus;
import com.erp.inventory.constant.InvTransferRevisionStatuses;
import com.erp.inventory.constant.InvTransferTypes;
import com.erp.inventory.domain.InvTransferApprovalInstance;
import com.erp.inventory.domain.InvTransferApprovalNode;
import com.erp.inventory.domain.InvTransferApprovalRule;
import com.erp.inventory.domain.InvTransferApprovalTask;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.InvTransferStatusLog;
import com.erp.inventory.domain.dto.InvTransferApprovalRequest;
import com.erp.inventory.domain.vo.InvTransferApprovalSummary;
import com.erp.inventory.domain.vo.InvTransferApprovalTrack;
import com.erp.inventory.domain.vo.InvTransferApprovalPreview;
import com.erp.inventory.mapper.InvTransferApprovalCandidateMapper;
import com.erp.inventory.mapper.InvTransferApprovalInstanceMapper;
import com.erp.inventory.mapper.InvTransferApprovalTaskMapper;
import com.erp.inventory.mapper.InvTransferOrderMapper;
import com.erp.inventory.mapper.InvTransferStatusLogMapper;
import com.erp.inventory.service.IInvTransferApprovalRuleService;
import com.erp.inventory.service.IInvTransferApprovalService;

@Service
public class InvTransferApprovalServiceImpl extends InvBaseService implements IInvTransferApprovalService
{
    private static final int MIN_REJECTION_COMMENT_LENGTH = 4;
    private static final int MAX_APPROVAL_COMMENT_LENGTH = 500;
    private static final String STATUS_RUNNING = "running";
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_APPROVED = "approved";
    private static final String STATUS_REJECTED = "rejected";
    private static final String STATUS_CLOSED = "closed";
    private static final String CANCELLATION_REASON = "调拨已取消，审批终止";
    private static final String APPROVAL_ALL_NODES = "all_nodes";
    private static final String APPROVAL_ANY_ONE = "any_one";
    private static final String APPROVAL_ALL_POSTS = "all_posts";
    private static final String APPROVAL_QUORUM = "quorum";
    private static final String ACTION_APPROVE = "approve";
    private static final String ACTION_REJECT = "reject";
    private static final String REJECT_BACK_TO_DRAFT = "back_to_draft";
    private static final String DISALLOW_SELF_APPROVE = "0";
    private static final String NODE_ROLE_POST = "post";
    private static final String NODE_ROLE_TO_LEADER = "to_leader";
    private static final String NODE_ROLE_FROM_LEADER = "from_leader";
    private static final String NODE_ROLE_LEVEL4_HIGHEST = "level4_highest";
    private static final String NODE_ROLE_LEVEL3_HIGHEST = "level3_highest";
    private static final String STORE_MANAGER_POST_CODE = "dz";
    private static final String STORE_ASSISTANT_POST_CODE = "dzzy";
    private static final InvTransferApprovalProgressPolicy PROGRESS_POLICY =
            new InvTransferApprovalProgressPolicy();

    @Autowired
    private IInvTransferApprovalRuleService ruleService;

    @Autowired
    private InvTransferApprovalInstanceMapper instanceMapper;

    @Autowired
    private InvTransferApprovalTaskMapper taskMapper;

    @Autowired
    private InvTransferApprovalCandidateMapper candidateMapper;

    @Autowired
    private TransferApprovalCandidateResolver candidateResolver;

    @Autowired
    private InvTransferOrderMapper transferOrderMapper;

    @Autowired
    private InvTransferStatusLogMapper statusLogMapper;

    @Autowired
    private InvTransferReservationService transferReservationService;

    @Autowired
    private InvTransferRevisionService transferRevisionService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvTransferApprovalInstance createInstanceForSubmit(InvTransferOrder transfer)
    {
        if (transfer == null)
        {
            throw new ServiceException("调拨单不能为空");
        }
        InvTransferApprovalRule rule = ruleService.matchRule(transfer);
        if (rule == null)
        {
            throw new ServiceException("未匹配到调拨审批规则");
        }
        List<InvTransferApprovalNode> nodes = sortedNodes(rule);
        if (nodes.isEmpty())
        {
            throw new ServiceException("审批规则无审批节点");
        }
        if (isSystemFourLevelRule(nodes))
        {
            return createFourLevelInstance(transfer, rule);
        }

        DynamicLeaderResolution dynamicLeaders = resolveDynamicLeaders(nodes, transfer);
        List<NodeCandidates> nodeCandidates = new ArrayList<>();
        for (InvTransferApprovalNode node : nodes)
        {
            String nodeRole = defaultString(node.getNodeRole(), NODE_ROLE_POST);
            if (shouldSkipDynamicNode(nodeRole, dynamicLeaders))
            {
                continue;
            }
            List<CandidateUser> candidates = resolveCandidates(transfer, node, dynamicLeaders);
            if (candidates == null || candidates.isEmpty())
            {
                continue;
            }
            if (isDynamicNodeRole(nodeRole))
            {
                node.setResolvedPostSort(candidates.get(0).postSort);
            }
            validateCandidates(rule, node, candidates);
            nodeCandidates.add(new NodeCandidates(node, candidates));
        }
        if (nodeCandidates.isEmpty())
        {
            throw new ServiceException("审批规则未配置有效审批人");
        }

        List<InvTransferApprovalNode> activeNodes = nodeCandidates.stream()
                .map(snapshot -> snapshot.node)
                .collect(Collectors.toList());
        InvTransferApprovalInstance instance = buildInstance(transfer, rule, nodeCandidates.get(0).node, activeNodes);
        instance.setApprovalWarnings(buildDynamicLeaderWarnings(dynamicLeaders));
        instanceMapper.insertInstance(instance);
        for (NodeCandidates snapshot : nodeCandidates)
        {
            taskMapper.insertTask(buildTask(instance, transfer, snapshot.node, snapshot.candidates));
        }
        return instance;
    }

    @Override
    public InvTransferApprovalPreview previewCandidates(Long ruleId, Long targetDeptId,
            Long selectedShopDeptId)
    {
        if (ruleId == null)
        {
            throw new ServiceException("审批规则ID不能为空");
        }
        if (targetDeptId == null)
        {
            throw new ServiceException("目标门店不能为空");
        }
        InvTransferApprovalRule rule = ruleService.selectRuleById(ruleId, selectedShopDeptId);
        assertShopVisible(targetDeptId, selectedShopDeptId, "当前用户无权预览该目标门店审批人");

        InvTransferOrder transfer = new InvTransferOrder();
        transfer.setToDeptId(targetDeptId);
        TransferApprovalCandidateResolver.Resolution resolution = candidateResolver.resolve(transfer, rule);

        InvTransferApprovalPreview preview = new InvTransferApprovalPreview();
        preview.setRuleId(rule.getRuleId());
        preview.setRuleName(rule.getRuleName());
        preview.setTargetDeptId(targetDeptId);
        preview.setTargetDeptName(deptScopeMapper.selectDeptNameById(targetDeptId));
        preview.setManagerPostSort(resolution.managerPostSort());
        preview.setExecutiveBoundarySort(resolution.executiveBoundarySort());
        preview.setWarnings(new ArrayList<>(resolution.warnings()));
        preview.setBlocked(!resolution.missingMandatoryRoles().isEmpty());
        preview.setBlockedReason(previewBlockedReason(resolution));

        List<InvTransferApprovalPreview.Node> nodes = resolution.nodes().stream()
                .map(node -> previewNode(node, resolution.managerPostSort()))
                .collect(Collectors.toList());
        preview.setNodes(nodes);
        return preview;
    }

    private InvTransferApprovalPreview.Node previewNode(
            TransferApprovalCandidateResolver.ResolvedNode resolvedNode,
            Integer managerPostSort)
    {
        InvTransferApprovalPreview.Node node = new InvTransferApprovalPreview.Node();
        node.setNodeOrder(resolvedNode.node().getNodeOrder());
        node.setNodeName(resolvedNode.node().getNodeName());
        node.setNodeRole(resolvedNode.node().getNodeRole());
        node.setResolvedPostSort(resolvedNode.resolvedPostSort());
        node.setRequired(resolvedNode.required());
        node.setCandidateCount(resolvedNode.candidates().size());
        node.setCandidateDisplayNames(resolvedNode.candidates().stream()
                .map(candidate -> safeStoredDisplayName(candidate.displayName()))
                .collect(Collectors.toList()));
        if (resolvedNode.candidates().isEmpty())
        {
            node.setState(resolvedNode.required() ? "blocked" : "skipped");
        }
        else if (InvTransferApprovalNodeRoles.LEVEL4_HIGHEST.equals(node.getNodeRole())
                && managerPostSort != null
                && resolvedNode.resolvedPostSort() != null
                && !managerPostSort.equals(resolvedNode.resolvedPostSort()))
        {
            node.setState("assistant_fallback");
        }
        else
        {
            node.setState("ready");
        }
        return node;
    }

    private static String previewBlockedReason(
            TransferApprovalCandidateResolver.Resolution resolution)
    {
        if (resolution.missingMandatoryRoles().contains(
                InvTransferApprovalNodeRoles.OPERATIONS_DIRECTOR))
        {
            return "目标门店未配置有效运营总监";
        }
        if (resolution.missingMandatoryRoles().contains(
                InvTransferApprovalNodeRoles.GENERAL_MANAGER))
        {
            return "目标门店未配置有效总经理";
        }
        return null;
    }

    private InvTransferApprovalInstance createFourLevelInstance(InvTransferOrder transfer,
            InvTransferApprovalRule rule)
    {
        TransferApprovalCandidateResolver.Resolution resolution = candidateResolver.resolve(transfer, rule);
        validateMandatoryNodes(resolution, transfer);

        int skipThroughOrder = highestSubmitterNodeOrder(resolution, SecurityUtils.getUserId());
        if (skipThroughOrder >= 4)
        {
            throw new ServiceException("总经理不能发起需本人最终审批的调拨单");
        }

        List<NodeCandidates> nodeCandidates = new ArrayList<>();
        for (TransferApprovalCandidateResolver.ResolvedNode resolvedNode : resolution.nodes())
        {
            InvTransferApprovalNode node = resolvedNode.node();
            if (node == null || nodeOrderValue(node) <= skipThroughOrder
                    || resolvedNode.candidates().isEmpty())
            {
                continue;
            }
            node.setResolvedPostSort(resolvedNode.resolvedPostSort());
            List<CandidateUser> candidates = resolvedNode.candidates().stream()
                    .map(candidate -> new CandidateUser(
                            candidate.userId(), candidate.displayName(), candidate.postSort()))
                    .collect(Collectors.toList());
            validateCandidates(rule, node, candidates);
            nodeCandidates.add(new NodeCandidates(node, candidates));
        }
        if (nodeCandidates.isEmpty())
        {
            throw new ServiceException("审批规则未配置有效审批人");
        }

        List<InvTransferApprovalNode> activeNodes = nodeCandidates.stream()
                .map(snapshot -> snapshot.node)
                .collect(Collectors.toList());
        InvTransferApprovalInstance instance = buildInstance(
                transfer, rule, nodeCandidates.get(0).node, activeNodes);
        instance.setRuleSnapshot(buildRuleSnapshot(rule, activeNodes,
                resolution.managerPostSort(), resolution.executiveBoundarySort()));
        instance.setApprovalWarnings(buildFourLevelWarnings(resolution, skipThroughOrder));
        instanceMapper.insertInstance(instance);
        for (NodeCandidates snapshot : nodeCandidates)
        {
            taskMapper.insertTask(buildTask(instance, transfer, snapshot.node, snapshot.candidates));
        }
        return instance;
    }

    private void validateMandatoryNodes(TransferApprovalCandidateResolver.Resolution resolution,
            InvTransferOrder transfer)
    {
        if (resolution.missingMandatoryRoles().contains(
                InvTransferApprovalNodeRoles.OPERATIONS_DIRECTOR))
        {
            throw new ServiceException("目标门店未配置有效运营总监（门店ID："
                    + transfer.getToDeptId() + "）");
        }
        if (resolution.missingMandatoryRoles().contains(
                InvTransferApprovalNodeRoles.GENERAL_MANAGER))
        {
            throw new ServiceException("目标门店未配置有效总经理（门店ID："
                    + transfer.getToDeptId() + "）");
        }
    }

    private static int highestSubmitterNodeOrder(
            TransferApprovalCandidateResolver.Resolution resolution, Long submitterId)
    {
        if (submitterId == null)
        {
            return 0;
        }
        int highestOrder = 0;
        for (TransferApprovalCandidateResolver.ResolvedNode resolvedNode : resolution.nodes())
        {
            boolean matches = resolvedNode.candidates().stream()
                    .anyMatch(candidate -> submitterId.equals(candidate.userId()));
            if (matches && resolvedNode.node() != null)
            {
                highestOrder = Math.max(highestOrder, nodeOrderValue(resolvedNode.node()));
            }
        }
        return highestOrder;
    }

    private static List<String> buildFourLevelWarnings(
            TransferApprovalCandidateResolver.Resolution resolution, int skipThroughOrder)
    {
        List<String> warnings = new ArrayList<>(resolution.warnings());
        if (skipThroughOrder == 1)
        {
            warnings.add("发起人命中四级负责人，本次审批从三级负责人开始");
        }
        else if (skipThroughOrder == 2)
        {
            warnings.add("发起人命中三级负责人，本次审批从运营总监开始");
        }
        else if (skipThroughOrder == 3)
        {
            warnings.add("发起人命中运营总监，本次审批从总经理开始");
        }
        return warnings;
    }

    private static boolean isSystemFourLevelRule(List<InvTransferApprovalNode> nodes)
    {
        List<String> roles = nodes.stream()
                .filter(node -> node != null)
                .sorted(Comparator.comparing(InvTransferApprovalServiceImpl::nodeOrderValue))
                .map(InvTransferApprovalNode::getNodeRole)
                .collect(Collectors.toList());
        return roles.equals(List.of(
                InvTransferApprovalNodeRoles.LEVEL4_HIGHEST,
                InvTransferApprovalNodeRoles.LEVEL3_HIGHEST,
                InvTransferApprovalNodeRoles.OPERATIONS_DIRECTOR,
                InvTransferApprovalNodeRoles.GENERAL_MANAGER));
    }

    @Override
    public boolean canApproveTask(InvTransferApprovalTask task, Long userId)
    {
        if (task == null || userId == null || !STATUS_PENDING.equals(task.getStatus()))
        {
            return false;
        }
        String candidateUserIds = task.getCandidateUserIds();
        if (StringUtils.isEmpty(candidateUserIds))
        {
            return false;
        }
        for (String candidateUserId : candidateUserIds.split(","))
        {
            if (userId.toString().equals(candidateUserId.trim()))
            {
                return true;
            }
        }
        return false;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class)
    public void cancelRunning(Long transferId)
    {
        if (transferId == null)
        {
            return;
        }
        List<InvTransferApprovalInstance> runningInstances = defaultList(
                instanceMapper.selectRunningInstancesByTransferIdForUpdate(
                        transferId));
        String username = SecurityUtils.getUsername();
        taskMapper.skipPendingTasksByTransferId(transferId, username,
                CANCELLATION_REASON);
        int closed = instanceMapper.closeRunningInstancesByTransferId(
                transferId, username, CANCELLATION_REASON);
        if (closed != runningInstances.size())
        {
            throw new ServiceException("调拨审批状态已变化，请刷新后重试");
        }
    }

    @Override
    public void assertApprovedForDelivery(InvTransferOrder transfer)
    {
        if (transfer == null || transfer.getTransferId() == null)
        {
            throw new ServiceException("调拨单不能为空");
        }
        if (transfer.getApprovalInstanceId() == null)
        {
            throw new ServiceException("调拨审批数据异常：未关联审批实例，已阻止发货");
        }
        if (InventoryUnifiedApprovalService.ENGINE_NATIVE.equalsIgnoreCase(
                transfer.getApprovalEngine() == null ? ""
                        : transfer.getApprovalEngine()))
        {
            if (!Set.of(InvStatusConstants.APPROVED,
                    InvStatusConstants.RESERVED,
                    InvStatusConstants.PARTIAL_DELIVERED)
                    .contains(transfer.getStatus())
                    || transfer.getApprovalRound() == null
                    || StringUtils.isEmpty(
                            transfer.getLastApprovalEventKey()))
            {
                throw new ServiceException("调拨统一审批结果不完整，已阻止发货");
            }
            return;
        }

        InvTransferApprovalInstance instance = instanceMapper
                .selectInstanceByIdForUpdate(transfer.getApprovalInstanceId());
        if (instance == null
                || !transfer.getTransferId().equals(instance.getTransferId())
                || !STATUS_APPROVED.equals(instance.getStatus()))
        {
            throw new ServiceException("调拨审批数据异常：审批实例不存在、不匹配或未通过，已阻止发货");
        }

        List<InvTransferApprovalTask> tasks = PROGRESS_POLICY.safeTasks(
                taskMapper.selectTasksByInstanceId(instance.getInstanceId()));
        boolean taskRelationInvalid = tasks.stream().anyMatch(task -> task == null
                || !instance.getInstanceId().equals(task.getInstanceId())
                || !transfer.getTransferId().equals(task.getTransferId()));
        if (taskRelationInvalid)
        {
            throw new ServiceException("调拨审批数据异常：审批任务与调拨单不匹配，已阻止发货");
        }

        if (tasks.isEmpty())
        {
            boolean autoApproved = defaultList(statusLogMapper
                    .selectLogsByTransferId(transfer.getTransferId())).stream()
                    .anyMatch(log -> log != null && "auto_approve".equals(log.getAction()));
            if (!autoApproved)
            {
                throw new ServiceException("调拨审批数据异常：缺少审批任务及自动通过记录，已阻止发货");
            }
            return;
        }

        InvTransferApprovalProgressPolicy.ApprovalSnapshot snapshot =
                PROGRESS_POLICY.parseSnapshot(instance);
        if (!PROGRESS_POLICY.isInstanceComplete(
                snapshot, instance, tasks))
        {
            throw new ServiceException("调拨审批数据异常：审批任务未完整通过，已阻止发货");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(InvTransferApprovalRequest request, Long selectedShopDeptId)
    {
        validateApprovalRequest(request);
        Long userId = SecurityUtils.getUserId();
        String userName = SecurityUtils.getUsername();
        InvTransferOrder transfer = lockSubmittedTransfer(request.getTransferId());
        requireLegacyEngine(transfer);
        validateApprovalShopScope(transfer, selectedShopDeptId);
        InvTransferApprovalInstance instance = lockRunningInstance(transfer);
        InvTransferApprovalTask task = lockApprovalTask(request, instance, userId);
        validateApprovalPermission(transfer, instance, task, userId, userName);

        if (ACTION_APPROVE.equals(request.getAction()))
        {
            approveTask(request, task, userId, userName);
            completeNodeIfReady(transfer, instance);
            return;
        }

        rejectTask(request, transfer, instance, task, userId, userName);
    }

    @Override
    public InvTransferApprovalTrack selectTrack(InvTransferOrder transfer)
    {
        if (transfer == null || transfer.getTransferId() == null)
        {
            throw new ServiceException("调拨单不能为空");
        }
        requireLegacyEngine(transfer);

        InvTransferApprovalTrack track = new InvTransferApprovalTrack();
        track.setTransferId(transfer.getTransferId());
        track.setOrderNo(transfer.getOrderNo());
        track.setDocumentStatus(transfer.getStatus());

        List<InvTransferApprovalInstance> instances = instanceMapper
                .selectInstancesByTransferId(transfer.getTransferId());
        if (instances == null || instances.isEmpty())
        {
            String state;
            if (InvStatusConstants.CANCELLED.equals(transfer.getStatus()))
            {
                state = "cancelled";
            }
            else if (InvStatusConstants.DRAFT.equals(transfer.getStatus()))
            {
                state = "not_started";
            }
            else
            {
                state = "partial";
                track.setTraceCompleteness("partial");
            }
            track.setState(state);
            track.setSummaryText(summaryText(state, null, null, null));
            return track;
        }

        instances = new ArrayList<>(instances);
        instances.sort(Comparator
                .comparing(InvTransferApprovalInstance::getCreateTime,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(InvTransferApprovalInstance::getInstanceId,
                        Comparator.nullsLast(Comparator.naturalOrder())));
        List<Long> instanceIds = instances.stream()
                .map(InvTransferApprovalInstance::getInstanceId)
                .filter(id -> id != null)
                .collect(Collectors.toList());
        List<InvTransferApprovalTask> tasks = instanceIds.isEmpty()
                ? Collections.emptyList()
                : defaultList(taskMapper.selectTasksByInstanceIds(instanceIds));
        Map<Long, String> safeNames = selectSafeDisplayNames(tasks);
        Map<Long, List<InvTransferApprovalTask>> tasksByInstance = tasks.stream()
                .filter(task -> task.getInstanceId() != null)
                .collect(Collectors.groupingBy(InvTransferApprovalTask::getInstanceId));
        List<InvTransferStatusLog> logs = defaultList(statusLogMapper.selectLogsByTransferId(transfer.getTransferId()));
        InvTransferStatusLog autoApproveLog = latestLog(logs, "auto_approve");

        boolean cancelled = InvStatusConstants.CANCELLED.equals(transfer.getStatus());
        boolean partial = false;
        InvTransferApprovalTrack.Node currentNode = null;
        List<InvTransferApprovalTrack.Round> rounds = new ArrayList<>();
        for (int index = 0; index < instances.size(); index++)
        {
            InvTransferApprovalInstance instance = instances.get(index);
            List<InvTransferApprovalTask> roundTasks = new ArrayList<>(
                    tasksByInstance.getOrDefault(instance.getInstanceId(), Collections.emptyList()));
            roundTasks.sort(Comparator
                    .comparing(InvTransferApprovalTask::getNodeOrder,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(InvTransferApprovalTask::getTaskId,
                            Comparator.nullsLast(Comparator.naturalOrder())));

            SnapshotInfo snapshot = parseSnapshot(instance.getRuleSnapshot());
            if (!snapshot.complete)
            {
                partial = true;
            }
            InvTransferApprovalTrack.Round round = new InvTransferApprovalTrack.Round();
            round.setRoundNo(index + 1);
            round.setStatus(roundStatus(instance, cancelled));
            round.setSubmittedAt(instance.getCreateTime());
            round.setFinishedAt(roundFinishedAt(instance, roundTasks));

            List<InvTransferApprovalTrack.Node> nodes = new ArrayList<>();
            if (roundTasks.isEmpty() && STATUS_APPROVED.equals(instance.getStatus()) && autoApproveLog != null)
            {
                nodes.add(autoApprovedNode(autoApproveLog));
            }
            else
            {
                for (InvTransferApprovalTask task : roundTasks)
                {
                    if (task.getNodeOrder() == null || !snapshot.nodesByOrder.containsKey(task.getNodeOrder()))
                    {
                        partial = true;
                    }
                    InvTransferApprovalTrack.Node node = buildTrackNode(
                            task, instance, snapshot, safeNames, cancelled);
                    nodes.add(node);
                    if (index == instances.size() - 1 && "current".equals(node.getState()))
                    {
                        currentNode = node;
                    }
                }
            }
            round.setNodes(nodes);
            rounds.add(round);
        }
        track.setRounds(rounds);

        InvTransferApprovalInstance latest = instances.get(instances.size() - 1);
        List<InvTransferApprovalTask> latestTasks = tasksByInstance
                .getOrDefault(latest.getInstanceId(), Collections.emptyList());
        String state = trackState(transfer, latest, latestTasks, currentNode, autoApproveLog);
        if ("partial".equals(state))
        {
            partial = true;
        }
        if (partial && !"cancelled".equals(state) && !"auto_approved".equals(state))
        {
            state = "partial";
        }
        track.setState(state);
        track.setTraceCompleteness(partial ? "partial" : "complete");
        track.setCurrentNode("in_progress".equals(state) ? currentNode : null);
        Integer totalNodes = latestTasks.stream()
                .map(InvTransferApprovalTask::getNodeOrder)
                .filter(order -> order != null)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .size();
        track.setSummaryText(summaryText(state, latest.getCurrentNodeOrder(), totalNodes,
                currentNode == null ? null : currentNode.getNodeName()));
        return track;
    }

    @Override
    public Map<Long, InvTransferApprovalSummary> selectApprovalSummaries(List<Long> transferIds)
    {
        Map<Long, InvTransferApprovalSummary> result = new LinkedHashMap<>();
        if (transferIds == null || transferIds.isEmpty())
        {
            return result;
        }
        List<InvTransferApprovalSummary> summaries = defaultList(
                taskMapper.selectApprovalSummariesByTransferIds(transferIds));
        Set<Long> userIds = new LinkedHashSet<>();
        for (InvTransferApprovalSummary summary : summaries)
        {
            userIds.addAll(parseUserIds(summary.getCandidateUserIds()));
        }
        Map<Long, String> safeNames = selectSafeDisplayNames(userIds);
        for (InvTransferApprovalSummary summary : summaries)
        {
            List<String> names = resolveCandidateDisplayNames(
                    summary.getCandidateUserIds(), summary.getCandidateUserNames(), safeNames);
            summary.setCurrentCandidateDisplayNames(names);
            summary.setCurrentCandidateCount(names.size());
            if ("approved".equals(summary.getState())
                    && (summary.getTotalNodeCount() == null || summary.getTotalNodeCount() == 0))
            {
                summary.setState("auto_approved");
            }
            summary.setSummaryText(summaryText(summary.getState(), summary.getCurrentNodeOrder(),
                    summary.getTotalNodeCount(), summary.getCurrentNodeName()));
            result.put(summary.getTransferId(), summary);
        }
        return result;
    }

    private InvTransferApprovalTrack.Node buildTrackNode(InvTransferApprovalTask task,
            InvTransferApprovalInstance instance, SnapshotInfo snapshot, Map<Long, String> safeNames,
            boolean cancelled)
    {
        InvTransferApprovalTrack.Node node = new InvTransferApprovalTrack.Node();
        node.setNodeOrder(task.getNodeOrder());
        node.setNodeName(task.getNodeName());
        node.setPostName(task.getPostName());
        node.setState(nodeState(task, instance, cancelled));
        List<String> candidateNames = resolveCandidateDisplayNames(
                task.getCandidateUserIds(), task.getCandidateUserNames(), safeNames);
        node.setCandidateDisplayNames(candidateNames);
        node.setCandidateCount(candidateNames.size());
        JSONObject snapshotNode = snapshot.nodesByOrder.get(task.getNodeOrder());
        String approvalMode = snapshotNode == null ? null : snapshotNode.getString("approvalMode");
        Integer requiredCount = snapshotNode == null ? null : snapshotNode.getInteger("requiredCount");
        node.setApprovalModeText(approvalModeText(approvalMode, requiredCount));
        if (STATUS_APPROVED.equals(task.getStatus()))
        {
            node.setDecision(ACTION_APPROVE);
        }
        else if (STATUS_REJECTED.equals(task.getStatus()))
        {
            node.setDecision(ACTION_REJECT);
        }
        if (task.getApproverId() != null || StringUtils.isNotEmpty(task.getApproverName()))
        {
            node.setActualApproverDisplayName(safeNames.getOrDefault(task.getApproverId(),
                    safeStoredDisplayName(task.getApproverName())));
        }
        node.setHandledAt(task.getApproveTime());
        node.setComment(sanitizeComment(task.getComment()));
        return node;
    }

    private InvTransferApprovalTrack.Node autoApprovedNode(InvTransferStatusLog log)
    {
        InvTransferApprovalTrack.Node node = new InvTransferApprovalTrack.Node();
        node.setNodeOrder(0);
        node.setNodeName("系统自动审批");
        node.setState("auto_approved");
        node.setCandidateCount(0);
        node.setDecision("auto_approve");
        node.setActualApproverDisplayName("系统");
        node.setHandledAt(log.getCreateTime());
        node.setComment(sanitizeComment(log.getReason()));
        return node;
    }

    private String trackState(InvTransferOrder transfer, InvTransferApprovalInstance latest,
            List<InvTransferApprovalTask> tasks, InvTransferApprovalTrack.Node currentNode,
            InvTransferStatusLog autoApproveLog)
    {
        if (InvStatusConstants.CANCELLED.equals(transfer.getStatus()))
        {
            return "cancelled";
        }
        if (InvStatusConstants.DRAFT.equals(transfer.getStatus())
                && STATUS_CLOSED.equals(latest.getStatus()))
        {
            return "not_started";
        }
        if (STATUS_APPROVED.equals(latest.getStatus()) && tasks.isEmpty() && autoApproveLog != null)
        {
            return "auto_approved";
        }
        if (STATUS_RUNNING.equals(latest.getStatus()))
        {
            return currentNode == null ? "partial" : "in_progress";
        }
        if (STATUS_APPROVED.equals(latest.getStatus()))
        {
            return "approved";
        }
        if (STATUS_REJECTED.equals(latest.getStatus()))
        {
            return "rejected";
        }
        if (STATUS_CLOSED.equals(latest.getStatus()))
        {
            return "cancelled";
        }
        return "partial";
    }

    private String roundStatus(InvTransferApprovalInstance instance, boolean cancelled)
    {
        if (cancelled && STATUS_RUNNING.equals(instance.getStatus()))
        {
            return "cancelled";
        }
        if (STATUS_RUNNING.equals(instance.getStatus()))
        {
            return "in_progress";
        }
        if (STATUS_CLOSED.equals(instance.getStatus()))
        {
            return "cancelled";
        }
        return defaultString(instance.getStatus(), "partial");
    }

    private String nodeState(InvTransferApprovalTask task, InvTransferApprovalInstance instance,
            boolean cancelled)
    {
        if (cancelled)
        {
            return "terminated";
        }
        if (STATUS_APPROVED.equals(task.getStatus()))
        {
            return "approved";
        }
        if (STATUS_REJECTED.equals(task.getStatus()))
        {
            return "rejected";
        }
        if (!STATUS_RUNNING.equals(instance.getStatus()))
        {
            return "terminated";
        }
        if (task.getNodeOrder() == null || instance.getCurrentNodeOrder() == null)
        {
            return "partial";
        }
        if (task.getNodeOrder().equals(instance.getCurrentNodeOrder()))
        {
            return "current";
        }
        return task.getNodeOrder() > instance.getCurrentNodeOrder() ? "waiting" : "terminated";
    }

    private SnapshotInfo parseSnapshot(String rawSnapshot)
    {
        SnapshotInfo info = new SnapshotInfo();
        if (StringUtils.isEmpty(rawSnapshot))
        {
            return info;
        }
        try
        {
            JSONObject snapshot = JSON.parseObject(rawSnapshot);
            JSONArray nodes = snapshot.getJSONArray("nodes");
            if (nodes == null)
            {
                return info;
            }
            for (int index = 0; index < nodes.size(); index++)
            {
                JSONObject node = nodes.getJSONObject(index);
                Integer order = node == null ? null : node.getInteger("nodeOrder");
                if (order != null)
                {
                    info.nodesByOrder.put(order, node);
                }
            }
            info.complete = true;
        }
        catch (RuntimeException ignored)
        {
            info.complete = false;
        }
        return info;
    }

    private String approvalModeText(String approvalMode, Integer requiredCount)
    {
        if (APPROVAL_ANY_ONE.equals(approvalMode))
        {
            return "任一人通过即可";
        }
        if (APPROVAL_ALL_POSTS.equals(approvalMode))
        {
            return "配置岗位全部通过";
        }
        if (APPROVAL_QUORUM.equals(approvalMode))
        {
            return "达到" + (requiredCount == null ? 0 : requiredCount) + "人通过";
        }
        if (APPROVAL_ALL_NODES.equals(approvalMode))
        {
            return "所有有效节点依次完成";
        }
        return "审批方式未知";
    }

    private String summaryText(String state, Integer currentNodeOrder, Integer totalNodeCount,
            String currentNodeName)
    {
        if ("not_started".equals(state)) return "尚未提交审批";
        if ("in_progress".equals(state))
        {
            return "当前第 " + defaultInteger(currentNodeOrder) + "/" + defaultInteger(totalNodeCount)
                    + " 级，等待" + defaultString(currentNodeName, "当前节点") + "审批";
        }
        if ("approved".equals(state)) return "审批已通过";
        if ("rejected".equals(state)) return "上轮已驳回，待修改后重新提交";
        if ("auto_approved".equals(state)) return "系统自动通过，无需人工审批";
        if ("cancelled".equals(state)) return "调拨已取消，审批已终止";
        return "审批数据异常，请联系管理员";
    }

    private int defaultInteger(Integer value)
    {
        return value == null ? 0 : value;
    }

    private Map<Long, String> selectSafeDisplayNames(List<InvTransferApprovalTask> tasks)
    {
        Set<Long> userIds = new LinkedHashSet<>();
        for (InvTransferApprovalTask task : tasks)
        {
            userIds.addAll(parseUserIds(task.getCandidateUserIds()));
            if (task.getApproverId() != null)
            {
                userIds.add(task.getApproverId());
            }
        }
        return selectSafeDisplayNames(userIds);
    }

    private Map<Long, String> selectSafeDisplayNames(Set<Long> userIds)
    {
        Map<Long, String> result = new LinkedHashMap<>();
        if (userIds == null || userIds.isEmpty())
        {
            return result;
        }
        List<Map<String, Object>> rows = candidateMapper
                .selectSafeDisplayNamesByUserIds(new ArrayList<>(userIds));
        for (Map<String, Object> row : defaultList(rows))
        {
            Long userId = toLong(row.get("userId"));
            if (userId != null)
            {
                result.put(userId, safeStoredDisplayName(
                        row.get("displayName") == null ? null : row.get("displayName").toString()));
            }
        }
        return result;
    }

    private List<String> resolveCandidateDisplayNames(String candidateUserIds,
            String candidateUserNames, Map<Long, String> safeNames)
    {
        List<Long> userIds = parseUserIds(candidateUserIds);
        List<String> storedNames = splitCsv(candidateUserNames);
        List<String> result = new ArrayList<>();
        if (!userIds.isEmpty())
        {
            for (int index = 0; index < userIds.size(); index++)
            {
                String stored = index < storedNames.size() ? storedNames.get(index) : null;
                result.add(safeNames.getOrDefault(userIds.get(index), safeStoredDisplayName(stored)));
            }
            return result;
        }
        for (String storedName : storedNames)
        {
            result.add(safeStoredDisplayName(storedName));
        }
        return result;
    }

    private List<Long> parseUserIds(String csv)
    {
        List<Long> result = new ArrayList<>();
        for (String value : splitCsv(csv))
        {
            Long id = toLong(value);
            if (id != null)
            {
                result.add(id);
            }
        }
        return result;
    }

    private List<String> splitCsv(String csv)
    {
        if (StringUtils.isEmpty(csv))
        {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        Collections.addAll(result, csv.split(",", -1));
        return result;
    }

    private String safeStoredDisplayName(String stored)
    {
        String value = stored == null ? null : stored.trim();
        if (StringUtils.isEmpty(value) || value.matches("^1\\d{10}$") || value.contains("@"))
        {
            return "姓名未配置";
        }
        return value;
    }

    private String sanitizeComment(String comment)
    {
        return comment == null ? null : comment.replaceAll("[\\p{Cc}&&[^\\r\\n\\t]]", "").trim();
    }

    private InvTransferStatusLog latestLog(List<InvTransferStatusLog> logs, String action)
    {
        return logs.stream()
                .filter(log -> action.equals(log.getAction()))
                .max(Comparator.comparing(InvTransferStatusLog::getCreateTime,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
    }

    private Date roundFinishedAt(InvTransferApprovalInstance instance, List<InvTransferApprovalTask> tasks)
    {
        if (STATUS_RUNNING.equals(instance.getStatus()))
        {
            return null;
        }
        return tasks.stream()
                .map(InvTransferApprovalTask::getApproveTime)
                .filter(time -> time != null)
                .max(Date::compareTo)
                .orElse(instance.getUpdateTime());
    }

    private Long toLong(Object value)
    {
        if (value == null)
        {
            return null;
        }
        try
        {
            return Long.valueOf(value.toString());
        }
        catch (NumberFormatException ignored)
        {
            return null;
        }
    }

    private <T> List<T> defaultList(List<T> values)
    {
        return values == null ? Collections.emptyList() : values;
    }

    private static class SnapshotInfo
    {
        private boolean complete;
        private final Map<Integer, JSONObject> nodesByOrder = new LinkedHashMap<>();
    }

    private InvTransferApprovalInstance buildInstance(InvTransferOrder transfer, InvTransferApprovalRule rule,
            InvTransferApprovalNode firstNode)
    {
        return buildInstance(transfer, rule, firstNode, sortedNodes(rule));
    }

    private InvTransferApprovalInstance buildInstance(InvTransferOrder transfer, InvTransferApprovalRule rule,
            InvTransferApprovalNode firstNode, List<InvTransferApprovalNode> snapshotNodes)
    {
        InvTransferApprovalInstance instance = new InvTransferApprovalInstance();
        instance.setTransferId(transfer.getTransferId());
        instance.setRuleId(rule.getRuleId());
        instance.setRuleName(rule.getRuleName());
        instance.setRuleSnapshot(buildRuleSnapshot(rule, snapshotNodes));
        instance.setStatus(STATUS_RUNNING);
        instance.setCurrentNodeOrder(firstNode.getNodeOrder() == null ? 1 : firstNode.getNodeOrder());
        instance.setApprovalMode(defaultString(rule.getApprovalMode(), APPROVAL_ALL_NODES));
        instance.setRequiredCount(rule.getRequiredCount() == null ? 0 : rule.getRequiredCount());
        instance.setRejectAction(defaultString(rule.getRejectAction(), REJECT_BACK_TO_DRAFT));
        instance.setAllowSelfApprove(defaultString(rule.getAllowSelfApprove(), DISALLOW_SELF_APPROVE));
        instance.setCreateBy(SecurityUtils.getUsername());
        return instance;
    }

    private void validateApprovalRequest(InvTransferApprovalRequest request)
    {
        if (request == null)
        {
            throw new ServiceException("审批请求不能为空");
        }
        if (request.getTransferId() == null || request.getTransferId() <= 0)
        {
            throw new ServiceException("调拨单ID必须为正整数");
        }
        if (request.getTaskId() != null && request.getTaskId() <= 0)
        {
            throw new ServiceException("审批任务ID必须为正整数");
        }
        if (!ACTION_APPROVE.equals(request.getAction()) && !ACTION_REJECT.equals(request.getAction()))
        {
            throw new ServiceException("审批动作必须为approve或reject");
        }
        String comment = request.getComment() == null ? null
                : request.getComment().trim();
        if (comment != null && comment.length() > MAX_APPROVAL_COMMENT_LENGTH)
        {
            throw new ServiceException("审批意见不能超过500个字符");
        }
        if (ACTION_REJECT.equals(request.getAction())
                && (StringUtils.isEmpty(comment)
                        || comment.length() < MIN_REJECTION_COMMENT_LENGTH))
        {
            throw new ServiceException("驳回原因至少填写4个字符");
        }
        request.setComment(StringUtils.isEmpty(comment) ? null : comment);
    }

    private static void requireLegacyEngine(InvTransferOrder transfer)
    {
        if (InventoryUnifiedApprovalService.ENGINE_NATIVE.equalsIgnoreCase(
                transfer.getApprovalEngine() == null ? ""
                        : transfer.getApprovalEngine()))
        {
            throw new ServiceException("该调拨单已使用统一审批，请在工作台处理或查看轨迹");
        }
    }

    private InvTransferOrder lockSubmittedTransfer(Long transferId)
    {
        InvTransferOrder transfer = transferOrderMapper.selectInvTransferOrderByIdForUpdate(transferId);
        if (transfer == null)
        {
            throw new ServiceException("调拨单不存在");
        }
        if (!InvStatusConstants.SUBMITTED.equals(transfer.getStatus()))
        {
            throw new ServiceException("只有审核中的调拨单允许审批");
        }
        return transfer;
    }

    private void validateApprovalShopScope(InvTransferOrder transfer, Long selectedShopDeptId)
    {
        Long scopeRoot = resolveAndValidateShopDept(selectedShopDeptId);
        if (isTransferVisibleFromScope(scopeRoot, transfer))
        {
            return;
        }
        throw new ServiceException("当前用户无权审批该调拨单");
    }

    private boolean isTransferVisibleFromScope(Long scopeRoot, InvTransferOrder transfer)
    {
        return isDeptInScope(scopeRoot, transfer.getFromDeptId())
                || isDeptInScope(scopeRoot, transfer.getToDeptId())
                || isDeptInScope(scopeRoot, transfer.getFromWarehouseId())
                || isDeptInScope(scopeRoot, transfer.getToWarehouseId());
    }

    private boolean isDeptInScope(Long scopeRoot, Long deptId)
    {
        return scopeRoot != null && deptId != null && deptScopeMapper.countDeptInScope(scopeRoot, deptId) > 0;
    }

    private InvTransferApprovalInstance lockRunningInstance(InvTransferOrder transfer)
    {
        InvTransferApprovalInstance instance = null;
        if (transfer.getApprovalInstanceId() != null)
        {
            instance = instanceMapper.selectInstanceByIdForUpdate(transfer.getApprovalInstanceId());
        }
        if (instance == null)
        {
            instance = instanceMapper.selectRunningInstanceByTransferIdForUpdate(transfer.getTransferId());
        }
        if (instance == null)
        {
            throw new ServiceException("调拨审批实例不存在");
        }
        if (!Objects.equals(instance.getTransferId(), transfer.getTransferId()))
        {
            throw new ServiceException("调拨审批实例与调拨单不匹配");
        }
        if (!STATUS_RUNNING.equals(instance.getStatus()))
        {
            throw new ServiceException("调拨审批实例不在审批中");
        }
        return instance;
    }

    private InvTransferApprovalTask lockApprovalTask(InvTransferApprovalRequest request,
            InvTransferApprovalInstance instance, Long userId)
    {
        if (request.getTaskId() != null)
        {
            InvTransferApprovalTask task = taskMapper.selectTaskByIdForUpdate(request.getTaskId());
            if (task == null)
            {
                throw new ServiceException("审批任务不存在");
            }
            return task;
        }

        List<InvTransferApprovalTask> pendingTasks = taskMapper.selectPendingTasksByInstanceAndNodeForUpdate(
                instance.getInstanceId(), instance.getCurrentNodeOrder());
        if (pendingTasks != null)
        {
            for (InvTransferApprovalTask task : pendingTasks)
            {
                if (canApproveTask(task, userId))
                {
                    return task;
                }
            }
        }
        throw new ServiceException("未找到当前用户可审批的待审批任务");
    }

    private void validateApprovalPermission(InvTransferOrder transfer, InvTransferApprovalInstance instance,
            InvTransferApprovalTask task, Long userId, String userName)
    {
        if (!transfer.getTransferId().equals(task.getTransferId())
                || !instance.getInstanceId().equals(task.getInstanceId()))
        {
            throw new ServiceException("审批任务与调拨单不匹配");
        }
        if (instance.getCurrentNodeOrder() == null || !instance.getCurrentNodeOrder().equals(task.getNodeOrder()))
        {
            throw new ServiceException("审批任务不属于当前审批节点");
        }
        if (!STATUS_PENDING.equals(task.getStatus()))
        {
            throw new ServiceException("审批任务不是待审批状态");
        }
        if (!canApproveTask(task, userId))
        {
            throw new ServiceException("当前用户不是该审批任务候选人");
        }
        if (DISALLOW_SELF_APPROVE.equals(defaultString(instance.getAllowSelfApprove(), DISALLOW_SELF_APPROVE))
                && !StringUtils.isEmpty(transfer.getCreateBy())
                && transfer.getCreateBy().equals(userName))
        {
            throw new ServiceException("提交人不能审批自己的调拨单");
        }
    }

    private void approveTask(InvTransferApprovalRequest request, InvTransferApprovalTask task,
            Long userId, String userName)
    {
        task.setStatus(STATUS_APPROVED);
        task.setApproverId(userId);
        task.setApproverName(userName);
        task.setApproveTime(new Date());
        task.setComment(request.getComment());
        task.setUpdateBy(userName);
        taskMapper.updateTaskApproval(task);
    }

    private void completeNodeIfReady(InvTransferOrder transfer, InvTransferApprovalInstance instance)
    {
        InvTransferApprovalProgressPolicy.ApprovalSnapshot snapshot =
                PROGRESS_POLICY.parseSnapshot(instance);
        List<InvTransferApprovalTask> allTasks =
                PROGRESS_POLICY.safeTasks(taskMapper
                        .selectTasksByInstanceId(
                                instance.getInstanceId()));
        List<InvTransferApprovalTask> currentTasks =
                PROGRESS_POLICY.tasksByNodeOrder(
                        allTasks, instance.getCurrentNodeOrder());
        InvTransferApprovalProgressPolicy.NodeSnapshot currentNode =
                snapshot.nodeByOrder(instance.getCurrentNodeOrder());
        if (PROGRESS_POLICY.isInstanceComplete(
                snapshot, instance, allTasks))
        {
            approveInstanceAndTransfer(transfer, instance);
            return;
        }
        if (!PROGRESS_POLICY.isNodeComplete(
                currentNode, currentTasks))
        {
            return;
        }

        Integer nextNodeOrder = PROGRESS_POLICY.resolveNextNodeOrder(
                snapshot, instance.getCurrentNodeOrder(), allTasks);
        if (nextNodeOrder != null)
        {
            instance.setCurrentNodeOrder(nextNodeOrder);
            instance.setUpdateBy(SecurityUtils.getUsername());
            instanceMapper.updateInstance(instance);
            return;
        }
        if (!snapshot.hasNodes())
        {
            approveInstanceAndTransfer(transfer, instance);
        }
    }

    private void approveInstanceAndTransfer(InvTransferOrder transfer, InvTransferApprovalInstance instance)
    {
        instance.setStatus(STATUS_APPROVED);
        instance.setUpdateBy(SecurityUtils.getUsername());
        instanceMapper.updateInstance(instance);

        String previousStatus = transfer.getStatus();
        Date approvedTime = new Date();
        transferRevisionService.recordApprovalOutcome(transfer,
                InvTransferRevisionStatuses.APPROVED,
                InvStatusConstants.APPROVED, ACTION_APPROVE,
                "调拨审批通过", SecurityUtils.getUserId(),
                SecurityUtils.getUsername(), instance.getInstanceId());
        InvTransferOrder update = new InvTransferOrder();
        update.setTransferId(transfer.getTransferId());
        update.setStatus(InvStatusConstants.APPROVED);
        if (InvTransferTypes.requiresSourceConfirmation(
                transfer.getTransferType(),
                transfer.getSourceBusinessType()))
        {
            update.setSourceConfirmStatus(
                    InvTransferSourceConfirmStatus.PENDING);
        }
        if (transfer.getApprovalInstanceId() == null)
        {
            update.setApprovalInstanceId(instance.getInstanceId());
            update.setApprovalEngine(InventoryUnifiedApprovalService.ENGINE_LEGACY);
        }
        update.setApprovedTime(approvedTime);
        update.setUpdateBy(SecurityUtils.getUsername());
        transferOrderMapper.updateInvTransferOrder(update);
        writeStatusLog(transfer.getTransferId(), previousStatus, InvStatusConstants.APPROVED,
                ACTION_APPROVE, "调拨审批通过");
    }

    private void rejectTask(InvTransferApprovalRequest request, InvTransferOrder transfer,
            InvTransferApprovalInstance instance, InvTransferApprovalTask task, Long userId, String userName)
    {
        task.setStatus(STATUS_REJECTED);
        task.setApproverId(userId);
        task.setApproverName(userName);
        task.setApproveTime(new Date());
        task.setComment(request.getComment());
        task.setUpdateBy(userName);
        taskMapper.updateTaskApproval(task);

        instance.setStatus(STATUS_REJECTED);
        instance.setUpdateBy(userName);
        instanceMapper.updateInstance(instance);

        String previousStatus = transfer.getStatus();
        String targetStatus = PROGRESS_POLICY.resolveRejectTargetStatus(
                instance.getRejectAction());
        transferReservationService.releaseAllRemaining(transfer, userName);
        String revisionStatus = InvStatusConstants.CLOSED.equals(targetStatus)
                ? InvTransferRevisionStatuses.CLOSED
                : InvTransferRevisionStatuses.REJECTED;
        transferRevisionService.recordApprovalOutcome(transfer,
                revisionStatus, targetStatus, ACTION_REJECT,
                defaultString(request.getComment(), "审批驳回"), userId,
                userName, instance.getInstanceId());
        InvTransferOrder update = new InvTransferOrder();
        update.setTransferId(transfer.getTransferId());
        update.setStatus(targetStatus);
        if (InvStatusConstants.DRAFT.equals(targetStatus)
                && InvTransferTypes.requiresSourceConfirmation(
                        transfer.getTransferType(),
                        transfer.getSourceBusinessType()))
        {
            update.setSourceConfirmStatus(
                    InvTransferSourceConfirmStatus.NOT_STARTED);
        }
        if (transfer.getApprovalInstanceId() == null)
        {
            update.setApprovalInstanceId(instance.getInstanceId());
            update.setApprovalEngine(InventoryUnifiedApprovalService.ENGINE_LEGACY);
        }
        if (!InvStatusConstants.DRAFT.equals(targetStatus))
        {
            update.setArchivedTime(new Date());
            update.setCloseReason(defaultString(request.getComment(), "审批驳回"));
        }
        update.setUpdateBy(userName);
        transferOrderMapper.updateInvTransferOrder(update);
        writeStatusLog(transfer.getTransferId(), previousStatus, targetStatus,
                ACTION_REJECT, defaultString(request.getComment(), "审批驳回"));
    }

    private void writeStatusLog(Long transferId, String fromStatus, String toStatus, String action, String reason)
    {
        InvTransferStatusLog log = new InvTransferStatusLog();
        log.setTransferId(transferId);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setAction(action);
        log.setOperatorId(SecurityUtils.getUserId());
        log.setOperatorName(SecurityUtils.getUsername());
        log.setReason(reason);
        statusLogMapper.insertLog(log);
    }

    private InvTransferApprovalTask buildTask(InvTransferApprovalInstance instance, InvTransferOrder transfer,
            InvTransferApprovalNode node, List<CandidateUser> candidates)
    {
        InvTransferApprovalTask task = new InvTransferApprovalTask();
        task.setInstanceId(instance.getInstanceId());
        task.setTransferId(transfer.getTransferId());
        task.setNodeOrder(node.getNodeOrder());
        task.setNodeName(node.getNodeName());
        task.setPostId(node.getPostId());
        task.setPostCode(node.getPostCode());
        task.setPostName(node.getPostName());
        task.setCandidateUserIds(candidates.stream()
                .map(candidate -> String.valueOf(candidate.userId))
                .collect(Collectors.joining(",")));
        task.setCandidateUserNames(candidates.stream()
                .map(candidate -> candidate.userName)
                .collect(Collectors.joining(",")));
        task.setStatus(STATUS_PENDING);
        task.setCreateBy(SecurityUtils.getUsername());
        return task;
    }

    private List<InvTransferApprovalNode> sortedNodes(InvTransferApprovalRule rule)
    {
        List<InvTransferApprovalNode> nodes = rule.getNodes();
        if (nodes == null || nodes.isEmpty())
        {
            return new ArrayList<>();
        }
        return nodes.stream()
                .sorted(Comparator.comparing(InvTransferApprovalServiceImpl::nodeOrderValue))
                .collect(Collectors.toList());
    }

    private boolean hasUserShopScope(Long userId, Long deptId)
    {
        return deptId != null && deptScopeMapper.countUserShopScope(userId, deptId) > 0;
    }

    private DynamicLeaderResolution resolveDynamicLeaders(List<InvTransferApprovalNode> nodes,
            InvTransferOrder transfer)
    {
        DynamicLeaderResolution resolution = dynamicNodeFlags(nodes);
        if (!resolution.hasLevel4Node && !resolution.hasLevel3Node)
        {
            return resolution;
        }
        if (transfer.getToDeptId() == null
                || candidateMapper.countActiveTargetStore(transfer.getToDeptId()) == 0)
        {
            resolution.targetStoreInvalid = true;
            return resolution;
        }

        Integer managerSort = candidateMapper.selectActivePostSortByCode(STORE_MANAGER_POST_CODE);
        Integer operationsSort = candidateMapper.selectActivePostSortByCode(
                InvTransferApprovalNodeRoles.OPERATIONS_DIRECTOR_POST);
        Integer generalManagerSort = candidateMapper.selectActivePostSortByCode(
                InvTransferApprovalNodeRoles.GENERAL_MANAGER_POST);
        Integer executiveBoundarySort = maxPostSort(operationsSort, generalManagerSort);
        resolution.level4Candidates = distinctCandidates(toCandidateUsers(
                candidateMapper.selectDirectStoreUsersByPostCode(
                        transfer.getToDeptId(), STORE_MANAGER_POST_CODE)));
        if (resolution.level4Candidates.isEmpty())
        {
            resolution.level4Candidates = distinctCandidates(toCandidateUsers(
                    candidateMapper.selectDirectStoreUsersByPostCode(
                            transfer.getToDeptId(), STORE_ASSISTANT_POST_CODE)));
        }

        Set<String> excludedPostCodes = new LinkedHashSet<>(List.of(
                STORE_MANAGER_POST_CODE, STORE_ASSISTANT_POST_CODE));
        for (InvTransferApprovalNode node : nodes == null ? new ArrayList<InvTransferApprovalNode>() : nodes)
        {
            if (node == null || isDynamicNodeRole(defaultString(node.getNodeRole(), NODE_ROLE_POST)))
            {
                continue;
            }
            if (!StringUtils.isEmpty(node.getPostCode()))
            {
                excludedPostCodes.add(StringUtils.trim(node.getPostCode()));
            }
        }
        if (managerSort != null && executiveBoundarySort != null)
        {
            resolution.level3Candidates = closestHigherCandidates(toCandidateUsers(
                    candidateMapper.selectCoveredHigherPostUsers(
                            transfer.getToDeptId(), managerSort, executiveBoundarySort,
                            new ArrayList<>(excludedPostCodes))),
                    managerSort);
        }

        Long submitterId = SecurityUtils.getUserId();
        resolution.submitterIsLevel4Leader = containsCandidate(resolution.level4Candidates, submitterId);
        resolution.submitterIsLevel3Leader = containsCandidate(resolution.level3Candidates, submitterId);
        return resolution;
    }

    private DynamicLeaderResolution dynamicNodeFlags(List<InvTransferApprovalNode> nodes)
    {
        DynamicLeaderResolution resolution = new DynamicLeaderResolution();
        List<InvTransferApprovalNode> safeNodes = nodes == null ? new ArrayList<>() : nodes;
        resolution.hasLevel4Node = safeNodes.stream()
                .anyMatch(node -> node != null && NODE_ROLE_LEVEL4_HIGHEST.equals(node.getNodeRole()));
        resolution.hasLevel3Node = safeNodes.stream()
                .anyMatch(node -> node != null && NODE_ROLE_LEVEL3_HIGHEST.equals(node.getNodeRole()));
        return resolution;
    }

    private List<String> buildDynamicLeaderWarnings(DynamicLeaderResolution resolution)
    {
        List<String> warnings = new ArrayList<>();
        if (resolution.targetStoreInvalid)
        {
            warnings.add("调拨目标门店无效，无法匹配四级、三级负责人，本次审批直接进入已配置的固定审批");
            return warnings;
        }
        boolean level4Missing = resolution.hasLevel4Node
                && !resolution.submitterIsLevel4Leader
                && !resolution.submitterIsLevel3Leader
                && resolution.level4Candidates.isEmpty();
        boolean level3Missing = resolution.hasLevel3Node
                && !resolution.submitterIsLevel3Leader
                && resolution.level3Candidates.isEmpty();
        if (level4Missing && level3Missing)
        {
            warnings.add("未找到目标门店四级、三级负责人，本次审批直接进入已配置的固定审批");
        }
        else if (level4Missing)
        {
            warnings.add("未找到目标门店四级负责人，本次审批从三级负责人开始");
        }
        else if (level3Missing)
        {
            warnings.add("未找到目标门店三级负责人，本次审批跳过三级并进入已配置的固定审批");
        }
        return warnings;
    }

    private boolean shouldSkipDynamicNode(String nodeRole, DynamicLeaderResolution resolution)
    {
        if (NODE_ROLE_LEVEL4_HIGHEST.equals(nodeRole))
        {
            return resolution.submitterIsLevel4Leader || resolution.submitterIsLevel3Leader;
        }
        return NODE_ROLE_LEVEL3_HIGHEST.equals(nodeRole) && resolution.submitterIsLevel3Leader;
    }

    private boolean isDynamicNodeRole(String nodeRole)
    {
        return NODE_ROLE_LEVEL4_HIGHEST.equals(nodeRole) || NODE_ROLE_LEVEL3_HIGHEST.equals(nodeRole);
    }

    private boolean containsCandidate(List<CandidateUser> candidates, Long userId)
    {
        return userId != null && candidates != null
                && candidates.stream().anyMatch(candidate -> userId.equals(candidate.userId));
    }

    private List<CandidateUser> resolveCandidates(InvTransferOrder transfer, InvTransferApprovalNode node,
            DynamicLeaderResolution dynamicLeaders)
    {
        String nodeRole = defaultString(node.getNodeRole(), NODE_ROLE_POST);
        if (NODE_ROLE_LEVEL4_HIGHEST.equals(nodeRole))
        {
            return dynamicLeaders.level4Candidates;
        }
        if (NODE_ROLE_LEVEL3_HIGHEST.equals(nodeRole))
        {
            return dynamicLeaders.level3Candidates;
        }
        return resolveConfiguredCandidates(transfer, node);
    }

    private List<CandidateUser> resolveConfiguredCandidates(InvTransferOrder transfer,
            InvTransferApprovalNode node)
    {
        String postCode = StringUtils.trim(node.getPostCode());
        List<CandidateUser> candidates = new ArrayList<>();
        if (StringUtils.isEmpty(postCode) || isSkippedStoreManagerPostNode(node, postCode))
        {
            return candidates;
        }
        Long approvalScopeDeptId = resolveCandidateScopeDeptId(transfer, node);
        if (approvalScopeDeptId == null)
        {
            return candidates;
        }
        for (Long deptId : resolveCandidateDeptIds(transfer, node))
        {
            List<Map<String, Object>> rows = candidateMapper.selectUsersByDeptAndPostCode(deptId, postCode);
            candidates = filterAuthorizedCandidates(toCandidateUsers(rows), approvalScopeDeptId);
            if (!candidates.isEmpty())
            {
                return candidates;
            }
        }
        return candidates;
    }

    private Long resolveCandidateScopeDeptId(InvTransferOrder transfer, InvTransferApprovalNode node)
    {
        String nodeRole = defaultString(node.getNodeRole(), NODE_ROLE_POST);
        if (NODE_ROLE_FROM_LEADER.equals(nodeRole))
        {
            return transfer.getFromDeptId();
        }
        return transfer.getToDeptId();
    }

    private boolean isSkippedStoreManagerPostNode(InvTransferApprovalNode node, String postCode)
    {
        return NODE_ROLE_POST.equals(defaultString(node.getNodeRole(), NODE_ROLE_POST))
                && STORE_MANAGER_POST_CODE.equalsIgnoreCase(StringUtils.trim(postCode));
    }

    private List<Long> resolveCandidateDeptIds(InvTransferOrder transfer, InvTransferApprovalNode node)
    {
        String nodeRole = defaultString(node.getNodeRole(), NODE_ROLE_POST);
        if (NODE_ROLE_FROM_LEADER.equals(nodeRole))
        {
            return singletonDeptId(transfer.getFromDeptId());
        }
        if (NODE_ROLE_TO_LEADER.equals(nodeRole))
        {
            return singletonDeptId(transfer.getToDeptId());
        }
        List<Long> ancestorDeptIds = transfer.getToDeptId() == null
                ? new ArrayList<>() : deptScopeMapper.selectAncestorDeptIds(transfer.getToDeptId());
        return ancestorDeptIds == null ? new ArrayList<>() : ancestorDeptIds;
    }

    private List<Long> singletonDeptId(Long deptId)
    {
        List<Long> deptIds = new ArrayList<>();
        if (deptId != null)
        {
            deptIds.add(deptId);
        }
        return deptIds;
    }

    private List<CandidateUser> toCandidateUsers(List<Map<String, Object>> rows)
    {
        List<CandidateUser> candidates = new ArrayList<>();
        if (rows == null)
        {
            return candidates;
        }
        for (Map<String, Object> row : rows)
        {
            Long userId = asLong(firstPresent(row, "userId", "user_id", "USERID", "USER_ID"));
            if (userId == null)
            {
                continue;
            }
            String userName = asString(firstPresent(row, "userName", "user_name", "USERNAME", "USER_NAME"));
            Integer postSort = asInteger(firstPresent(row, "postSort", "post_sort", "POSTSORT", "POST_SORT"));
            candidates.add(new CandidateUser(userId, userName, postSort));
        }
        return candidates;
    }

    private List<CandidateUser> closestHigherCandidates(List<CandidateUser> candidates, Integer managerSort)
    {
        Integer closestSort = candidates.stream()
                .map(candidate -> candidate.postSort)
                .filter(postSort -> postSort != null && managerSort != null && postSort < managerSort)
                .max(Integer::compareTo)
                .orElse(null);
        if (closestSort == null)
        {
            return new ArrayList<>();
        }
        return distinctCandidates(candidates.stream()
                .filter(candidate -> closestSort.equals(candidate.postSort))
                .collect(Collectors.toList()));
    }

    private static Integer maxPostSort(Integer first, Integer second)
    {
        if (first == null)
        {
            return second;
        }
        if (second == null)
        {
            return first;
        }
        return Math.max(first, second);
    }

    private List<CandidateUser> distinctCandidates(List<CandidateUser> candidates)
    {
        Map<Long, CandidateUser> unique = new LinkedHashMap<>();
        for (CandidateUser candidate : candidates == null ? new ArrayList<CandidateUser>() : candidates)
        {
            if (candidate != null && candidate.userId != null)
            {
                unique.putIfAbsent(candidate.userId, candidate);
            }
        }
        return new ArrayList<>(unique.values());
    }

    private List<CandidateUser> filterAuthorizedCandidates(List<CandidateUser> candidates, Long approvalScopeDeptId)
    {
        if (candidates == null || candidates.isEmpty())
        {
            return new ArrayList<>();
        }
        return candidates.stream()
                .filter(candidate -> hasUserShopScope(candidate.userId, approvalScopeDeptId))
                .collect(Collectors.toList());
    }

    private void validateCandidates(InvTransferApprovalRule rule, InvTransferApprovalNode node,
            List<CandidateUser> candidates)
    {
        String nodeName = defaultString(node.getNodeName(), "未命名节点");
        if (candidates == null || candidates.isEmpty())
        {
            throw new ServiceException("审批节点无候选人: " + nodeName);
        }
        Long submitterId = SecurityUtils.getUserId();
        if (DISALLOW_SELF_APPROVE.equals(defaultString(rule.getAllowSelfApprove(), DISALLOW_SELF_APPROVE))
                && submitterId != null
                && candidates.stream().allMatch(candidate -> submitterId.equals(candidate.userId)))
        {
            throw new ServiceException("审批节点不能自审: " + nodeName);
        }
    }

    private String buildRuleSnapshot(InvTransferApprovalRule rule)
    {
        return buildRuleSnapshot(rule, sortedNodes(rule));
    }

    private String buildRuleSnapshot(InvTransferApprovalRule rule, List<InvTransferApprovalNode> snapshotNodes)
    {
        return buildRuleSnapshot(rule, snapshotNodes, null, null);
    }

    private String buildRuleSnapshot(InvTransferApprovalRule rule,
            List<InvTransferApprovalNode> snapshotNodes, Integer managerPostSort,
            Integer executiveBoundarySort)
    {
        StringBuilder json = new StringBuilder();
        json.append('{');
        appendNumberField(json, "ruleId", rule.getRuleId()).append(',');
        appendStringField(json, "ruleName", rule.getRuleName()).append(',');
        appendStringField(json, "approvalMode", defaultString(rule.getApprovalMode(), APPROVAL_ALL_NODES)).append(',');
        appendNumberField(json, "requiredCount", rule.getRequiredCount()).append(',');
        appendStringField(json, "rejectAction", defaultString(rule.getRejectAction(), REJECT_BACK_TO_DRAFT)).append(',');
        appendStringField(json, "allowSelfApprove", defaultString(rule.getAllowSelfApprove(), DISALLOW_SELF_APPROVE)).append(',');
        appendNumberField(json, "managerPostSort", managerPostSort).append(',');
        appendNumberField(json, "executiveBoundarySort", executiveBoundarySort).append(',');
        json.append("\"nodes\":[");
        List<InvTransferApprovalNode> nodes = snapshotNodes == null ? new ArrayList<>() : snapshotNodes;
        for (int i = 0; i < nodes.size(); i++)
        {
            InvTransferApprovalNode node = nodes.get(i);
            if (i > 0)
            {
                json.append(',');
            }
            json.append('{');
            appendNumberField(json, "nodeOrder", node.getNodeOrder()).append(',');
            appendStringField(json, "nodeName", node.getNodeName()).append(',');
            appendStringField(json, "nodeRole", node.getNodeRole()).append(',');
            appendNumberField(json, "postId", node.getPostId()).append(',');
            appendStringField(json, "postCode", node.getPostCode()).append(',');
            appendStringField(json, "postName", node.getPostName()).append(',');
            appendStringField(json, "approvalMode", node.getApprovalMode()).append(',');
            appendNumberField(json, "requiredCount", node.getRequiredCount()).append(',');
            appendNumberField(json, "resolvedPostSort", node.getResolvedPostSort());
            json.append('}');
        }
        json.append("]}");
        return json.toString();
    }

    private static StringBuilder appendStringField(StringBuilder json, String name, String value)
    {
        json.append('"').append(name).append("\":");
        if (value == null)
        {
            json.append("null");
        }
        else
        {
            json.append('"').append(escapeJson(value)).append('"');
        }
        return json;
    }

    private static StringBuilder appendNumberField(StringBuilder json, String name, Number value)
    {
        json.append('"').append(name).append("\":");
        json.append(value == null ? "null" : value);
        return json;
    }

    private static String escapeJson(String value)
    {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static Object firstPresent(Map<String, Object> row, String... keys)
    {
        for (String key : keys)
        {
            if (row.containsKey(key))
            {
                return row.get(key);
            }
        }
        return null;
    }

    private static Long asLong(Object value)
    {
        if (value instanceof Number)
        {
            return ((Number) value).longValue();
        }
        if (value == null || StringUtils.isEmpty(value.toString()))
        {
            return null;
        }
        return Long.valueOf(value.toString());
    }

    private static String asString(Object value)
    {
        return value == null ? "" : value.toString();
    }

    private static Integer asInteger(Object value)
    {
        if (value instanceof Number)
        {
            return ((Number) value).intValue();
        }
        if (value == null || StringUtils.isEmpty(value.toString()))
        {
            return null;
        }
        return Integer.valueOf(value.toString());
    }

    private static String defaultString(String value, String defaultValue)
    {
        return StringUtils.isEmpty(value) ? defaultValue : value;
    }

    private static int nodeOrderValue(InvTransferApprovalNode node)
    {
        return node.getNodeOrder() == null ? Integer.MAX_VALUE : node.getNodeOrder();
    }

    private static class NodeCandidates
    {
        private final InvTransferApprovalNode node;
        private final List<CandidateUser> candidates;

        private NodeCandidates(InvTransferApprovalNode node, List<CandidateUser> candidates)
        {
            this.node = node;
            this.candidates = candidates;
        }
    }

    private static class CandidateUser
    {
        private final Long userId;
        private final String userName;
        private final Integer postSort;

        private CandidateUser(Long userId, String userName)
        {
            this(userId, userName, null);
        }

        private CandidateUser(Long userId, String userName, Integer postSort)
        {
            this.userId = userId;
            this.userName = userName;
            this.postSort = postSort;
        }
    }

    private static class DynamicLeaderResolution
    {
        private List<CandidateUser> level4Candidates = new ArrayList<>();
        private List<CandidateUser> level3Candidates = new ArrayList<>();
        private boolean hasLevel4Node;
        private boolean hasLevel3Node;
        private boolean submitterIsLevel4Leader;
        private boolean submitterIsLevel3Leader;
        private boolean targetStoreInvalid;
    }
}
