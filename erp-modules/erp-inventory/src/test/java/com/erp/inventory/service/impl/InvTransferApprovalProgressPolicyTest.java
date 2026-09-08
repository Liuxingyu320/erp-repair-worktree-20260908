package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.constant.InvStatusConstants;
import com.erp.inventory.domain.InvTransferApprovalInstance;
import com.erp.inventory.domain.InvTransferApprovalTask;

@DisplayName("调拨审批推进策略")
class InvTransferApprovalProgressPolicyTest
{
    private final InvTransferApprovalProgressPolicy policy =
            new InvTransferApprovalProgressPolicy();

    @Test
    @DisplayName("提交时规则快照按节点顺序解析并覆盖实例默认值")
    void parsesFrozenSnapshotInNodeOrder()
    {
        InvTransferApprovalInstance instance =
                instance("any_one", 9);
        instance.setRuleSnapshot("""
                {
                  "approvalMode": "all_nodes",
                  "requiredCount": 2,
                  "nodes": [
                    {"nodeOrder": 3, "nodeName": "运营总监",
                     "postCode": "ops", "approvalMode": "quorum",
                     "requiredCount": 2},
                    {"nodeOrder": 1, "nodeName": "店长",
                     "postCode": "dz", "approvalMode": "any_one"}
                  ]
                }
                """);

        InvTransferApprovalProgressPolicy.ApprovalSnapshot snapshot =
                policy.parseSnapshot(instance);

        assertThat(snapshot.approvalMode()).isEqualTo("all_nodes");
        assertThat(snapshot.requiredCount()).isEqualTo(2);
        assertThat(snapshot.nodes())
                .extracting(
                        InvTransferApprovalProgressPolicy.NodeSnapshot::nodeOrder)
                .containsExactly(1, 3);
        assertThat(snapshot.nodeByOrder(3).approvalMode())
                .isEqualTo("quorum");
    }

    @Test
    @DisplayName("损坏规则快照失败关闭而不是回退到当前规则")
    void rejectsMalformedSnapshot()
    {
        InvTransferApprovalInstance instance =
                instance("all_nodes", 1);
        instance.setRuleSnapshot("{broken");

        assertThatThrownBy(() -> policy.parseSnapshot(instance))
                .isInstanceOf(ServiceException.class)
                .hasMessage("审批规则快照解析失败");
    }

    @Test
    @DisplayName("驳回动作稳定映射草稿、关闭和已驳回状态")
    void resolvesRejectTargetStatus()
    {
        assertThat(policy.resolveRejectTargetStatus("back_to_draft"))
                .isEqualTo(InvStatusConstants.DRAFT);
        assertThat(policy.resolveRejectTargetStatus("close"))
                .isEqualTo(InvStatusConstants.CLOSED);
        assertThat(policy.resolveRejectTargetStatus("rejected"))
                .isEqualTo(InvStatusConstants.REJECTED);
        assertThat(policy.resolveRejectTargetStatus("unknown"))
                .isEqualTo(InvStatusConstants.DRAFT);
    }

    @Test
    @DisplayName("实例任一通过和法定人数按冻结任务结果判定")
    void evaluatesAnyOneAndQuorumInstanceModes()
    {
        List<InvTransferApprovalTask> tasks = List.of(
                task(1L, 1, 10L, "dz", "approved"),
                task(2L, 1, 11L, "dzzy", "pending"));

        InvTransferApprovalInstance anyOne =
                instance("any_one", null);
        assertThat(policy.isInstanceComplete(
                policy.parseSnapshot(anyOne), anyOne, tasks)).isTrue();

        InvTransferApprovalInstance quorum =
                instance("quorum", 2);
        assertThat(policy.isInstanceComplete(
                policy.parseSnapshot(quorum), quorum, tasks)).isFalse();
        tasks.get(1).setStatus("approved");
        assertThat(policy.isInstanceComplete(
                policy.parseSnapshot(quorum), quorum, tasks)).isTrue();

        InvTransferApprovalInstance defaultOne =
                instance("quorum", null);
        assertThatCode(() -> policy.isInstanceComplete(
                policy.parseSnapshot(defaultOne), defaultOne,
                List.of(task(3L, 1, null, "ops", "approved"))))
                .doesNotThrowAnyException();
        assertThat(policy.isInstanceComplete(
                policy.parseSnapshot(defaultOne), defaultOne,
                List.of(task(3L, 1, null, "ops", "approved"))))
                .isTrue();
    }

    @Test
    @DisplayName("全部岗位模式按节点和岗位联合身份区分同代码任务")
    void requiresEveryNodePost()
    {
        InvTransferApprovalInstance instance =
                instance("all_posts", null);
        List<InvTransferApprovalTask> tasks = List.of(
                task(1L, 1, null, "dz", "approved"),
                task(2L, 2, null, "dz", "pending"));

        InvTransferApprovalProgressPolicy.ApprovalSnapshot snapshot =
                policy.parseSnapshot(instance);
        assertThat(policy.isInstanceComplete(
                snapshot, instance, tasks)).isFalse();

        tasks.get(1).setStatus("approved");
        assertThat(policy.isInstanceComplete(
                snapshot, instance, tasks)).isTrue();
    }

    @Test
    @DisplayName("全部节点模式逐节点使用冻结的任一人和法定人数规则")
    void evaluatesEveryFrozenNode()
    {
        InvTransferApprovalInstance instance =
                instance("all_nodes", null);
        instance.setRuleSnapshot("""
                {"approvalMode":"all_nodes","nodes":[
                  {"nodeOrder":1,"approvalMode":"any_one"},
                  {"nodeOrder":2,"approvalMode":"quorum",
                   "requiredCount":2}
                ]}
                """);
        List<InvTransferApprovalTask> tasks = List.of(
                task(1L, 1, 10L, "dz", "approved"),
                task(2L, 2, 20L, "ops", "approved"),
                task(3L, 2, 21L, "ops", "pending"));

        InvTransferApprovalProgressPolicy.ApprovalSnapshot snapshot =
                policy.parseSnapshot(instance);
        assertThat(policy.isInstanceComplete(
                snapshot, instance, tasks)).isFalse();

        tasks.get(2).setStatus("approved");
        assertThat(policy.isInstanceComplete(
                snapshot, instance, tasks)).isTrue();
    }

    @Test
    @DisplayName("下一节点优先使用冻结快照且兼容旧实例任务回退")
    void resolvesNextNodeFromSnapshotThenLegacyTasks()
    {
        InvTransferApprovalInstance frozen =
                instance("all_nodes", null);
        frozen.setRuleSnapshot("""
                {"nodes":[{"nodeOrder":4},{"nodeOrder":2}]}
                """);
        InvTransferApprovalProgressPolicy.ApprovalSnapshot snapshot =
                policy.parseSnapshot(frozen);

        assertThat(policy.resolveNextNodeOrder(
                snapshot, 1, List.of())).isEqualTo(2);
        assertThat(policy.resolveNextNodeOrder(
                snapshot, 2, List.of())).isEqualTo(4);

        InvTransferApprovalInstance legacy =
                instance("all_nodes", null);
        InvTransferApprovalProgressPolicy.ApprovalSnapshot empty =
                policy.parseSnapshot(legacy);
        assertThat(policy.resolveNextNodeOrder(empty, 1, List.of(
                task(1L, 3, null, "ops", "pending"),
                task(2L, 2, null, "dz", "pending"))))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("当前节点任务筛选对空集合安全且不混入其他节点")
    void filtersTasksByNodeOrder()
    {
        assertThat(policy.tasksByNodeOrder(null, 1)).isEmpty();
        assertThat(policy.tasksByNodeOrder(List.of(
                task(1L, 1, null, "dz", "pending"),
                task(2L, 2, null, "ops", "pending")), 2))
                .extracting(InvTransferApprovalTask::getTaskId)
                .containsExactly(2L);
    }

    private InvTransferApprovalInstance instance(
            String approvalMode, Integer requiredCount)
    {
        InvTransferApprovalInstance instance =
                new InvTransferApprovalInstance();
        instance.setApprovalMode(approvalMode);
        instance.setRequiredCount(requiredCount);
        return instance;
    }

    private InvTransferApprovalTask task(Long taskId, Integer nodeOrder,
            Long postId, String postCode, String status)
    {
        InvTransferApprovalTask task =
                new InvTransferApprovalTask();
        task.setTaskId(taskId);
        task.setNodeOrder(nodeOrder);
        task.setPostId(postId);
        task.setPostCode(postCode);
        task.setStatus(status);
        return task;
    }
}
