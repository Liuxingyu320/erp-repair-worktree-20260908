package com.erp.inventory.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.inventory.constant.InvStatusConstants;
import com.erp.inventory.domain.InvTransferApprovalInstance;
import com.erp.inventory.domain.InvTransferApprovalTask;

/**
 * Pure progression rules for legacy transfer approval instances.
 *
 * <p>The policy evaluates only the frozen rule snapshot and task facts. It
 * does not read or write the database, inspect the current rule, access the
 * security context, or own transaction boundaries.</p>
 */
final class InvTransferApprovalProgressPolicy
{
    private static final String STATUS_APPROVED = "approved";
    private static final String APPROVAL_ALL_NODES = "all_nodes";
    private static final String APPROVAL_ANY_ONE = "any_one";
    private static final String APPROVAL_ALL_POSTS = "all_posts";
    private static final String APPROVAL_QUORUM = "quorum";
    private static final String REJECT_CLOSE = "close";
    private static final String REJECT_REJECTED = "rejected";

    ApprovalSnapshot parseSnapshot(
            InvTransferApprovalInstance instance)
    {
        ApprovalSnapshot snapshot = new ApprovalSnapshot(
                defaultString(instance.getApprovalMode(),
                        APPROVAL_ALL_NODES),
                instance.getRequiredCount());
        if (StringUtils.isEmpty(instance.getRuleSnapshot()))
        {
            return snapshot;
        }
        try
        {
            JSONObject json = JSON.parseObject(
                    instance.getRuleSnapshot());
            snapshot.approvalMode = defaultString(
                    json.getString("approvalMode"),
                    snapshot.approvalMode);
            Integer frozenRequiredCount =
                    json.getInteger("requiredCount");
            if (frozenRequiredCount != null)
            {
                snapshot.requiredCount = frozenRequiredCount;
            }
            JSONArray nodes = json.getJSONArray("nodes");
            if (nodes != null)
            {
                for (int index = 0; index < nodes.size(); index++)
                {
                    JSONObject nodeJson =
                            nodes.getJSONObject(index);
                    if (nodeJson == null)
                    {
                        continue;
                    }
                    snapshot.nodes.add(new NodeSnapshot(
                            nodeJson.getInteger("nodeOrder"),
                            nodeJson.getString("nodeName"),
                            nodeJson.getString("postCode"),
                            defaultString(
                                    nodeJson.getString(
                                            "approvalMode"),
                                    APPROVAL_ANY_ONE),
                            nodeJson.getInteger("requiredCount")));
                }
            }
            snapshot.nodes.sort(Comparator.comparing(
                    InvTransferApprovalProgressPolicy::nodeOrderValue));
            return snapshot;
        }
        catch (RuntimeException exception)
        {
            throw new ServiceException("审批规则快照解析失败");
        }
    }

    String resolveRejectTargetStatus(String rejectAction)
    {
        if (REJECT_CLOSE.equals(rejectAction))
        {
            return InvStatusConstants.CLOSED;
        }
        if (REJECT_REJECTED.equals(rejectAction))
        {
            return InvStatusConstants.REJECTED;
        }
        return InvStatusConstants.DRAFT;
    }

    boolean isNodeComplete(NodeSnapshot node,
            List<InvTransferApprovalTask> tasks)
    {
        String mode = defaultString(
                node == null ? null : node.approvalMode,
                APPROVAL_ANY_ONE);
        if (APPROVAL_ALL_POSTS.equals(mode))
        {
            return isAllPostsApproved(tasks);
        }
        if (APPROVAL_QUORUM.equals(mode))
        {
            int requiredCount = node != null
                    && node.requiredCount != null
                    ? node.requiredCount : 1;
            return approvedCount(tasks)
                    >= Math.max(requiredCount, 1);
        }
        return approvedCount(tasks) > 0;
    }

    boolean isInstanceComplete(ApprovalSnapshot snapshot,
            InvTransferApprovalInstance instance,
            List<InvTransferApprovalTask> tasks)
    {
        String mode = defaultString(snapshot.approvalMode,
                defaultString(instance.getApprovalMode(),
                        APPROVAL_ALL_NODES));
        if (APPROVAL_ANY_ONE.equals(mode))
        {
            return approvedCount(tasks) > 0;
        }
        if (APPROVAL_ALL_POSTS.equals(mode))
        {
            return isAllPostsApproved(tasks);
        }
        if (APPROVAL_QUORUM.equals(mode))
        {
            Integer configuredRequiredCount =
                    snapshot.requiredCount != null
                            ? snapshot.requiredCount
                            : instance.getRequiredCount();
            int requiredCount = configuredRequiredCount == null
                    ? 1 : configuredRequiredCount;
            return approvedCount(tasks)
                    >= Math.max(requiredCount, 1);
        }
        if (snapshot.nodes.isEmpty())
        {
            return false;
        }
        for (NodeSnapshot node : snapshot.nodes)
        {
            if (!isNodeComplete(node,
                    tasksByNodeOrder(tasks, node.nodeOrder)))
            {
                return false;
            }
        }
        return true;
    }

    Integer resolveNextNodeOrder(ApprovalSnapshot snapshot,
            Integer currentNodeOrder,
            List<InvTransferApprovalTask> tasks)
    {
        Integer current = currentNodeOrder == null
                ? 0 : currentNodeOrder;
        if (!snapshot.nodes.isEmpty())
        {
            return snapshot.nodes.stream()
                    .map(NodeSnapshot::nodeOrder)
                    .filter(nodeOrder -> nodeOrder != null
                            && nodeOrder > current)
                    .min(Integer::compareTo)
                    .orElse(null);
        }
        return safeTasks(tasks).stream()
                .map(InvTransferApprovalTask::getNodeOrder)
                .filter(nodeOrder -> nodeOrder != null
                        && nodeOrder > current)
                .min(Integer::compareTo)
                .orElse(null);
    }

    List<InvTransferApprovalTask> tasksByNodeOrder(
            List<InvTransferApprovalTask> tasks, Integer nodeOrder)
    {
        return safeTasks(tasks).stream()
                .filter(task -> nodeOrder != null
                        && nodeOrder.equals(task.getNodeOrder()))
                .collect(Collectors.toList());
    }

    List<InvTransferApprovalTask> safeTasks(
            List<InvTransferApprovalTask> tasks)
    {
        return tasks == null ? new ArrayList<>() : tasks;
    }

    private boolean isAllPostsApproved(
            List<InvTransferApprovalTask> tasks)
    {
        Set<String> allPosts = new HashSet<>();
        Set<String> approvedPosts = new HashSet<>();
        for (InvTransferApprovalTask task : safeTasks(tasks))
        {
            String postKey = approvalPostKey(task);
            allPosts.add(postKey);
            if (STATUS_APPROVED.equals(task.getStatus()))
            {
                approvedPosts.add(postKey);
            }
        }
        return !allPosts.isEmpty()
                && approvedPosts.containsAll(allPosts);
    }

    private String approvalPostKey(InvTransferApprovalTask task)
    {
        String postIdentity = task.getPostId() != null
                ? "postId:" + task.getPostId()
                : "postCode:" + defaultString(task.getPostCode(),
                        "task:" + task.getTaskId());
        return defaultString(String.valueOf(task.getNodeOrder()),
                "node") + ":" + postIdentity;
    }

    private int approvedCount(
            List<InvTransferApprovalTask> tasks)
    {
        int count = 0;
        for (InvTransferApprovalTask task : safeTasks(tasks))
        {
            if (STATUS_APPROVED.equals(task.getStatus()))
            {
                count++;
            }
        }
        return count;
    }

    private static String defaultString(String value,
            String defaultValue)
    {
        return StringUtils.isEmpty(value) ? defaultValue : value;
    }

    private static int nodeOrderValue(NodeSnapshot node)
    {
        return node.nodeOrder == null
                ? Integer.MAX_VALUE : node.nodeOrder;
    }

    static final class ApprovalSnapshot
    {
        private String approvalMode;
        private Integer requiredCount;
        private final List<NodeSnapshot> nodes = new ArrayList<>();

        private ApprovalSnapshot(String approvalMode,
                Integer requiredCount)
        {
            this.approvalMode = approvalMode;
            this.requiredCount = requiredCount;
        }

        String approvalMode()
        {
            return approvalMode;
        }

        Integer requiredCount()
        {
            return requiredCount;
        }

        List<NodeSnapshot> nodes()
        {
            return List.copyOf(nodes);
        }

        NodeSnapshot nodeByOrder(Integer nodeOrder)
        {
            for (NodeSnapshot node : nodes)
            {
                if (nodeOrder != null
                        && nodeOrder.equals(node.nodeOrder))
                {
                    return node;
                }
            }
            return null;
        }

        boolean hasNodes()
        {
            return !nodes.isEmpty();
        }
    }

    static final class NodeSnapshot
    {
        private final Integer nodeOrder;
        private final String nodeName;
        private final String postCode;
        private final String approvalMode;
        private final Integer requiredCount;

        private NodeSnapshot(Integer nodeOrder, String nodeName,
                String postCode, String approvalMode,
                Integer requiredCount)
        {
            this.nodeOrder = nodeOrder;
            this.nodeName = nodeName;
            this.postCode = postCode;
            this.approvalMode = approvalMode;
            this.requiredCount = requiredCount;
        }

        Integer nodeOrder()
        {
            return nodeOrder;
        }

        String nodeName()
        {
            return nodeName;
        }

        String postCode()
        {
            return postCode;
        }

        String approvalMode()
        {
            return approvalMode;
        }

        Integer requiredCount()
        {
            return requiredCount;
        }
    }
}
