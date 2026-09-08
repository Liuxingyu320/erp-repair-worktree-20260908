package com.erp.inventory.domain.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.transfer
        .InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
        .ActionSnapshot;
import com.erp.inventory.domain.transfer
        .InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy.Actor;
import com.erp.inventory.domain.transfer
        .InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy.Boundary;
import com.erp.inventory.domain.transfer
        .InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
        .PreparedExecution;
import com.erp.inventory.domain.transfer
        .InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy.Request;

@DisplayName("V2调拨差异裁决动作纯执行生命周期")
class InvTransferReceiptDiscrepancyAdjudicationExecutionPolicyTest
{
    private static final Instant EXECUTED = Instant.parse(
            "2026-08-03T02:00:00Z");
    private static final Actor EXECUTOR = new Actor(199L,
            "execution-user", Set.of(
                    InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                            .REQUIRED_PERMISSION));

    @Test
    @DisplayName("七种事项动作组合唯一推导效果和同步异步状态")
    void shouldClassifyEverySupportedEffect()
    {
        assertDispatch("shortage", "reship", "reship_workflow",
                "in_progress");
        assertDispatch("damaged", "return_to_source",
                "return_workflow", "in_progress");
        assertDispatch("damaged", "damage_write_off",
                "quarantine_write_off_and_loss_ledger", "completed");
        assertDispatch("shortage", "responsibility_adjustment",
                "responsibility_ledger", "completed");
        assertDispatch("damaged", "responsibility_adjustment",
                "responsibility_ledger", "completed");
        assertDispatch("shortage", "transport_loss_write_off",
                "shortage_loss_ledger", "completed");
        assertDispatch("damaged", "transport_loss_write_off",
                "quarantine_write_off_and_loss_ledger", "completed");
    }

    @Test
    @DisplayName("异步补发只有权威子流程引用存在时才能完成")
    void shouldCompleteAsyncEffectWithAuthorityReference()
    {
        Boundary boundary = boundary("shortage", "adjudication_executing",
                List.of(action(101L, 1, "reship", "1.0000",
                        "10.000000", "source", "in_progress", 1L)));
        Request request = request(boundary, 101L, 1L, "complete",
                "reship_transfer:9001");

        PreparedExecution result = prepare(request, boundary);

        assertThat(result.effectKind()).isEqualTo("reship_workflow");
        assertThat(result.actionStatusBefore()).isEqualTo("in_progress");
        assertThat(result.actionStatusAfter()).isEqualTo("completed");
        assertThat(result.effectReference()).isEqualTo(
                "reship_transfer:9001");
        assertThat(result.resultingActions().get(0).effectReference())
                .isEqualTo("reship_transfer:9001");
        assertThat(result.executionVersionAfter()).isEqualTo(2L);
        assertThat(result.caseVersionAfter()).isEqualTo(6L);
        assertThat(result.caseStatusAfter()).isEqualTo("resolved");
        assertThat(result.planStatusAfter()).isEqualTo("resolved");
    }

    @Test
    @DisplayName("只有最后一个未完成动作成功后聚合状态才进入已解决")
    void shouldResolveOnlyAfterFinalActionCompletes()
    {
        ActionSnapshot completed = action(101L, 1, "reship",
                "0.6000", "6.000000", "source", "completed", 2L);
        ActionSnapshot pending = action(102L, 2,
                "responsibility_adjustment", "0.4000", "4.000000",
                "carrier", "pending", 0L);
        Boundary boundary = boundary("shortage", "adjudication_executing",
                List.of(completed, pending));

        PreparedExecution result = prepare(request(boundary, 102L, 0L,
                "dispatch", null), boundary);

        assertThat(result.caseStatusBefore()).isEqualTo(
                "adjudication_executing");
        assertThat(result.caseStatusAfter()).isEqualTo("resolved");
        assertThat(result.resultingActions()).hasSize(2);
        assertThat(result.resultingActions().get(0)).isSameAs(completed);
        assertThat(result.resultingActions().get(1).executionStatus())
                .isEqualTo("completed");
        assertThat(result.resultingActions().get(1).executionVersion())
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("重复调度、同步动作二次完成和无效异步引用全部失败关闭")
    void shouldRejectInvalidTransitionsAndReferences()
    {
        Boundary progressing = boundary("shortage",
                "adjudication_executing", List.of(action(101L, 1,
                        "reship", "1.0000", "10.000000", "source",
                        "in_progress", 1L)));
        assertThatThrownBy(() -> prepare(request(progressing, 101L, 1L,
                "dispatch", null), progressing))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不可重复调度");
        assertThatThrownBy(() -> prepare(request(progressing, 101L, 1L,
                "complete", "return_transfer:9001"), progressing))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("引用无效");
        assertThatThrownBy(() -> prepare(request(progressing, 101L, 1L,
                "complete", "reship_transfer:9999999999999999999"),
                progressing))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("引用无效");
        assertThatThrownBy(() -> prepare(request(progressing, 101L, 1L,
                "complete", "reship_transfer:9002"), progressing))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("引用已变化");

        Boundary atomic = boundary("damaged", "adjudication_planned",
                List.of(action(201L, 1, "damage_write_off", "1.0000",
                        "10.000000", "company", "pending", 0L)));
        assertThatThrownBy(() -> prepare(request(atomic, 201L, 0L,
                "complete", "return_transfer:9001"), atomic))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不可直接完成");

        Boundary pending = boundary("shortage", "adjudication_planned",
                List.of(action(101L, 1, "reship", "1.0000",
                        "10.000000", "source", "pending", 0L)));
        assertThatThrownBy(() -> prepare(request(pending, 101L, 0L,
                "complete", "reship_transfer:9001"), pending))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不可直接完成");
        assertThatThrownBy(() -> prepare(request(pending, 101L, 0L,
                "dispatch", null), pending))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("引用无效");
    }

    @Test
    @DisplayName("动作序号、数量金额和聚合状态必须与权威计划一致")
    void shouldEnforceConservationAndAggregateState()
    {
        Boundary valid = boundary("shortage", "adjudication_planned",
                List.of(action(101L, 1, "reship", "1.0000",
                        "10.000000", "source", "pending", 0L)));
        Boundary badQuantity = new Boundary(valid.caseId(),
                valid.caseVersionAtPlan(), valid.caseVersion(),
                valid.caseStatus(),
                valid.adjudicationId(), valid.decisionFingerprint(),
                valid.discrepancyType(), new BigDecimal("2.0000"),
                new BigDecimal("5.000000"), valid.discrepancyAmount(),
                valid.planStatus(),
                valid.actions());
        assertThatThrownBy(() -> prepare(request(badQuantity, 101L, 0L,
                "dispatch", null), badQuantity))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不守恒");

        Boundary badCost = new Boundary(valid.caseId(),
                valid.caseVersionAtPlan(), valid.caseVersion(),
                valid.caseStatus(), valid.adjudicationId(),
                valid.decisionFingerprint(), valid.discrepancyType(),
                valid.discrepancyQuantity(), new BigDecimal("9.000000"),
                valid.discrepancyAmount(), valid.planStatus(),
                valid.actions());
        assertThatThrownBy(() -> prepare(request(badCost, 101L, 0L,
                "dispatch", "reship_transfer:9001"), badCost))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("边界或权限无效");

        Boundary duplicateSequence = boundary("shortage",
                "adjudication_planned", List.of(
                        action(101L, 1, "reship", "0.6000",
                                "6.000000", "source", "pending", 0L),
                        action(102L, 1, "responsibility_adjustment",
                                "0.4000", "4.000000", "carrier",
                                "pending", 0L)));
        assertThatThrownBy(() -> prepare(request(duplicateSequence, 101L,
                0L, "dispatch", null), duplicateSequence))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("序号");

        Boundary wrongAggregate = new Boundary(valid.caseId(),
                valid.caseVersionAtPlan(), valid.caseVersion(),
                "adjudication_executing",
                valid.adjudicationId(), valid.decisionFingerprint(),
                valid.discrepancyType(), valid.discrepancyQuantity(),
                valid.sourceCostPrice(), valid.discrepancyAmount(),
                "adjudication_executing",
                valid.actions());
        assertThatThrownBy(() -> prepare(request(wrongAggregate, 101L, 0L,
                "dispatch", null), wrongAggregate))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("聚合状态");
    }

    @Test
    @DisplayName("受损责任动作不能替代完整处置且覆盖维度不可伪造")
    void shouldEnforceDamagedCoverageDimensions()
    {
        ActionSnapshot responsibility = action(101L, 1,
                "responsibility_adjustment", "1.0000", "10.000000",
                "carrier", "pending", 0L, "responsibility");
        Boundary responsibilityOnly = new Boundary(700L, 4L, 4L,
                "adjudication_planned", 800L, "a".repeat(64), "damaged",
                new BigDecimal("1.0000"), new BigDecimal("10.000000"),
                new BigDecimal("10.000000"), "adjudication_planned",
                List.of(responsibility));
        assertThatThrownBy(() -> prepare(request(responsibilityOnly, 101L,
                0L, "dispatch", null), responsibilityOnly))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("覆盖维度");

        Boundary forged = new Boundary(700L, 4L, 4L,
                "adjudication_planned", 800L, "a".repeat(64), "damaged",
                new BigDecimal("1.0000"), new BigDecimal("10.000000"),
                new BigDecimal("10.000000"), "adjudication_planned",
                List.of(action(201L, 1, "damage_write_off", "1.0000",
                        "10.000000", "company", "pending", 0L,
                        "responsibility")));
        assertThatThrownBy(() -> prepare(request(forged, 201L, 0L,
                "dispatch", null), forged))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("序号、类型、状态或版本无效");

        Boundary valid = boundary("damaged", "adjudication_planned",
                List.of(
                        action(301L, 1, "damage_write_off", "1.0000",
                                "10.000000", "company", "pending", 0L),
                        action(302L, 2, "responsibility_adjustment",
                                "0.4000", "4.000000", "carrier",
                                "pending", 0L, "responsibility")));
        PreparedExecution execution = prepare(request(valid, 302L, 0L,
                "dispatch", null), valid);
        assertThat(execution.coverageKind()).isEqualTo("responsibility");
        assertThat(execution.caseStatusAfter()).isEqualTo(
                "adjudication_executing");
    }

    @Test
    @DisplayName("专属权限、事项版本和动作版本任一漂移均拒绝")
    void shouldRequirePermissionAndExactVersions()
    {
        Boundary boundary = boundary("shortage", "adjudication_planned",
                List.of(action(101L, 1, "reship", "1.0000",
                        "10.000000", "source", "pending", 0L)));
        Request valid = request(boundary, 101L, 0L, "dispatch", null);

        assertThatThrownBy(() ->
                InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                        .prepare(valid, boundary,
                                new Actor(199L, "user", Set.of()),
                                EXECUTED))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("权限无效");
        assertThatThrownBy(() -> prepare(new Request(valid.requestId(),
                valid.caseId(), 3L, valid.adjudicationId(),
                valid.actionId(), valid.executionVersion(),
                valid.command(), valid.effectReference()), boundary))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("边界或权限无效");
        assertThatThrownBy(() -> prepare(request(boundary, 101L, 1L,
                "dispatch", null), boundary))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("动作版本已变化");
    }

    @Test
    @DisplayName("动作输入顺序不影响事件指纹且请求变化会改变指纹")
    void shouldCreateDeterministicSensitiveFingerprint()
    {
        ActionSnapshot first = action(101L, 1, "reship", "0.6000",
                "6.000000", "source", "pending", 0L);
        ActionSnapshot second = action(102L, 2,
                "responsibility_adjustment", "0.4000", "4.000000",
                "carrier", "pending", 0L);
        Boundary left = boundary("shortage", "adjudication_planned",
                List.of(first, second));
        Boundary right = boundary("shortage", "adjudication_planned",
                List.of(second, first));

        PreparedExecution a = prepare(request(left, 101L, 0L,
                "dispatch", "reship_transfer:9001"), left);
        PreparedExecution b = prepare(request(right, 101L, 0L,
                "dispatch", "reship_transfer:9001"), right);
        Request changed = new Request("execute-action-0002", 700L, 4L,
                800L, 101L, 0L, "dispatch", "reship_transfer:9001");
        PreparedExecution c = prepare(changed, left);

        Boundary distributionLeft = boundary("shortage",
                "adjudication_executing", List.of(
                        action(201L, 1, "reship", "0.3000",
                                "3.000000", "source", "in_progress", 1L),
                        action(202L, 2, "responsibility_adjustment",
                                "0.3000", "3.000000", "carrier",
                                "completed", 1L),
                        action(203L, 3, "transport_loss_write_off",
                                "0.4000", "4.000000", "carrier",
                                "pending", 0L)));
        Boundary distributionRight = boundary("shortage",
                "adjudication_executing", List.of(
                        action(201L, 1, "reship", "0.3000",
                                "3.000000", "source", "completed", 2L),
                        action(202L, 2, "responsibility_adjustment",
                                "0.3000", "3.000000", "carrier",
                                "pending", 0L),
                        action(203L, 3, "transport_loss_write_off",
                                "0.4000", "4.000000", "carrier",
                                "pending", 0L)));
        PreparedExecution distributedA = prepare(request(distributionLeft,
                203L, 0L, "dispatch", null), distributionLeft);
        PreparedExecution distributedB = prepare(request(distributionRight,
                203L, 0L, "dispatch", null), distributionRight);

        assertThat(a.eventFingerprint()).matches("[a-f0-9]{64}");
        assertThat(a.eventFingerprint()).isEqualTo(b.eventFingerprint());
        assertThat(a.eventFingerprint()).isNotEqualTo(
                c.eventFingerprint());
        assertThat(distributedA.eventFingerprint()).isNotEqualTo(
                distributedB.eventFingerprint());
    }

    @Test
    @DisplayName("边界动作和权限集合均执行防御性复制")
    void shouldDefensivelyCopyInputsAndOutputs()
    {
        List<ActionSnapshot> actions = new ArrayList<>();
        actions.add(action(101L, 1, "reship", "1.0000",
                "10.000000", "source", "pending", 0L));
        Set<String> permissions = new HashSet<>();
        permissions.add(
                InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                        .REQUIRED_PERMISSION);
        Boundary boundary = boundary("shortage", "adjudication_planned",
                actions);
        Actor actor = new Actor(199L, "execution-user", permissions);
        actions.clear();
        permissions.clear();

        PreparedExecution result =
                InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                        .prepare(request(boundary, 101L, 0L, "dispatch",
                                "reship_transfer:9001"), boundary, actor,
                                EXECUTED);

        assertThat(boundary.actions()).hasSize(1);
        assertThat(actor.permissions()).contains(
                InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                        .REQUIRED_PERMISSION);
        assertThatThrownBy(() -> result.resultingActions().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static void assertDispatch(String discrepancyType,
            String actionType, String effectKind, String actionStatusAfter)
    {
        List<ActionSnapshot> actions = "damaged".equals(discrepancyType)
                && "responsibility_adjustment".equals(actionType)
                ? List.of(
                        action(102L, 1, "damage_write_off", "1.0000",
                                "10.000000", "company", "pending", 0L),
                        action(101L, 2, actionType, "1.0000",
                                "10.000000", "company", "pending", 0L,
                                "responsibility"))
                : List.of(action(101L, 1, actionType, "1.0000",
                        "10.000000", "company", "pending", 0L));
        Boundary boundary = boundary(discrepancyType,
                "adjudication_planned", actions);
        String reference = switch (actionType)
        {
            case "reship" -> "reship_transfer:9001";
            case "return_to_source" -> "return_transfer:9001";
            default -> null;
        };

        PreparedExecution result = prepare(request(boundary, 101L, 0L,
                "dispatch", reference), boundary);

        assertThat(result.effectKind()).isEqualTo(effectKind);
        assertThat(result.coverageKind()).isEqualTo(
                "damaged".equals(discrepancyType)
                        && "responsibility_adjustment".equals(actionType)
                        ? "responsibility" : "resolution");
        assertThat(result.actionStatusAfter()).isEqualTo(actionStatusAfter);
        assertThat(result.executionVersionAfter()).isEqualTo(1L);
        assertThat(result.caseVersionAfter()).isEqualTo(5L);
        assertThat(result.requiredPermission()).isEqualTo(
                "inv:transfer:discrepancy:execute");
        assertThat(result.sourceCostPrice())
                .isEqualByComparingTo("10.000000");
        assertThat(result.effectReference()).isEqualTo(reference);
        assertThat(result.resultingActions().get(0).effectReference())
                .isEqualTo(reference);
    }

    private static PreparedExecution prepare(Request request,
            Boundary boundary)
    {
        return InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                .prepare(request, boundary, EXECUTOR, EXECUTED);
    }

    private static Request request(Boundary boundary, Long actionId,
            Long actionVersion, String command, String reference)
    {
        return new Request("execute-action-0001", boundary.caseId(),
                boundary.caseVersion(), boundary.adjudicationId(),
                actionId, actionVersion, command, reference);
    }

    private static Boundary boundary(String discrepancyType,
            String aggregateStatus, List<ActionSnapshot> actions)
    {
        BigDecimal quantity = actions.stream()
                .filter(action -> "resolution".equals(
                        action.coverageKind()))
                .map(ActionSnapshot::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal amount = actions.stream()
                .filter(action -> "resolution".equals(
                        action.coverageKind()))
                .map(ActionSnapshot::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long currentCaseVersion = 4L + actions.stream()
                .mapToLong(ActionSnapshot::executionVersion).sum();
        return new Boundary(700L, 4L, currentCaseVersion,
                aggregateStatus, 800L,
                "a".repeat(64), discrepancyType, quantity,
                new BigDecimal("10.000000"), amount, aggregateStatus,
                actions);
    }

    private static ActionSnapshot action(Long id, int sequence,
            String type, String quantity, String amount,
            String responsibleParty, String status, Long version)
    {
        return action(id, sequence, type, quantity, amount,
                responsibleParty, status, version, "resolution");
    }

    private static ActionSnapshot action(Long id, int sequence,
            String type, String quantity, String amount,
            String responsibleParty, String status, Long version,
            String coverageKind)
    {
        String reference = null;
        if (!"pending".equals(status))
        {
            reference = switch (type)
            {
                case "reship" -> "reship_transfer:9001";
                case "return_to_source" -> "return_transfer:9001";
                default -> null;
            };
        }
        return new ActionSnapshot(id, sequence, type, coverageKind,
                new BigDecimal(quantity), new BigDecimal(amount),
                responsibleParty, status, version, reference);
    }
}
