package com.erp.inventory.domain.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationPolicy.ActionInput;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationPolicy.Actor;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationPolicy.PreparedAdjudication;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationPolicy.Request;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationProjection.Projection;

@DisplayName("V2调拨差异裁决只读投影")
class InvTransferReceiptDiscrepancyAdjudicationProjectionTest
{
    private static final String PLAN = "a".repeat(64);
    private static final Instant NOW = Instant.parse(
            "2026-08-02T12:00:00Z");
    private static final String PERMISSION =
            InvTransferReceiptDiscrepancyAdjudicationPolicy
                    .REQUIRED_PERMISSION;

    @Test
    @DisplayName("未形成计划时只由双方当前事件推导可裁决状态")
    void shouldProjectReadyCaseWithoutPersistedPlan()
    {
        InvTransferReceiptDiscrepancyReadFact fact = fact();

        Projection projection =
                InvTransferReceiptDiscrepancyAdjudicationProjection.project(
                        InvTransferReceiptDiscrepancyBoundaryPolicy
                                .resolveForAdjudicationRead(fact, 700L),
                        List.of(), List.of());

        assertThat(projection.workflowState()).isEqualTo(
                "ready_for_adjudication");
        assertThat(projection.readyForAdjudication()).isTrue();
        assertThat(projection.plan()).isNull();
    }

    @Test
    @DisplayName("已形成计划时核验头、动作、版本和双方锚点")
    void shouldProjectExactPersistedPlan()
    {
        Fixture fixture = plannedFixture();

        Projection projection =
                InvTransferReceiptDiscrepancyAdjudicationProjection.project(
                        InvTransferReceiptDiscrepancyBoundaryPolicy
                                .resolveForAdjudicationRead(
                                        fixture.fact(), 700L),
                        List.of(fixture.stored()),
                        List.of(fixture.action()));

        assertThat(projection.workflowState()).isEqualTo(
                "adjudication_planned");
        assertThat(projection.readyForAdjudication()).isFalse();
        assertThat(projection.plan()).isNotNull();
        assertThat(projection.plan().actions()).hasSize(1);
        assertThat(projection.confirmations().source().currentFact()).isTrue();
        assertThat(projection.confirmations().target().currentFact()).isTrue();
    }

    @Test
    @DisplayName("待确认状态混入计划或动作时失败关闭")
    void shouldRejectPlanRowsBeforeCaseTransition()
    {
        InvTransferReceiptDiscrepancyReadFact fact = fact();
        Fixture fixture = plannedFixture();

        assertThatThrownBy(() ->
                InvTransferReceiptDiscrepancyAdjudicationProjection.project(
                        InvTransferReceiptDiscrepancyBoundaryPolicy
                                .resolveForAdjudicationRead(fact, 700L),
                        List.of(fixture.stored()),
                        List.of(fixture.action())))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("只读投影不可核验");
    }

    @Test
    @DisplayName("计划动作金额、覆盖维度或执行状态被篡改时拒绝读取")
    void shouldRejectTamperedPersistedAction()
    {
        Fixture amount = plannedFixture();
        amount.action().setAmount(new BigDecimal("9.000000"));
        assertInvalid(amount);

        Fixture status = plannedFixture();
        status.action().setExecutionStatus("completed");
        assertInvalid(status);

        Fixture coverage = plannedFixture();
        coverage.action().setCoverageKind("responsibility");
        assertInvalid(coverage);
    }

    @Test
    @DisplayName("未知事项状态不得绕过尚未定义的物理执行边界")
    void shouldRejectUnknownPhysicalExecutionState()
    {
        InvTransferReceiptDiscrepancyReadFact fact = fact();
        fact.setCaseStatus("resolved");

        assertThatThrownBy(() ->
                InvTransferReceiptDiscrepancyAdjudicationProjection.project(
                        InvTransferReceiptDiscrepancyBoundaryPolicy
                                .resolveForAdjudicationRead(fact, 700L),
                        List.of(), List.of()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("只读投影不可核验");
    }

    private static void assertInvalid(Fixture fixture)
    {
        assertThatThrownBy(() ->
                InvTransferReceiptDiscrepancyAdjudicationProjection.project(
                        InvTransferReceiptDiscrepancyBoundaryPolicy
                                .resolveForAdjudicationRead(
                                        fixture.fact(), 700L),
                        List.of(fixture.stored()),
                        List.of(fixture.action())))
                .isInstanceOf(ServiceException.class);
    }

    private static Fixture plannedFixture()
    {
        InvTransferReceiptDiscrepancyReadFact fact = fact();
        PreparedAdjudication prepared =
                InvTransferReceiptDiscrepancyAdjudicationPolicy.prepare(
                        request(),
                        InvTransferReceiptDiscrepancyBoundaryPolicy
                                .resolveForAdjudication(fact, 700L),
                        new Actor(99L, "independent-user",
                                Set.of(PERMISSION)),
                        NOW);
        InvTransferReceiptDiscrepancyStoredAdjudication stored =
                stored(prepared);
        InvTransferReceiptDiscrepancyStoredAdjudicationAction action =
                action(prepared);
        fact.setCaseStatus("adjudication_planned");
        fact.setCaseVersion(prepared.caseVersionAfter());
        return new Fixture(fact, stored, action);
    }

    private static InvTransferReceiptDiscrepancyStoredAdjudication stored(
            PreparedAdjudication prepared)
    {
        InvTransferReceiptDiscrepancyStoredAdjudication value =
                new InvTransferReceiptDiscrepancyStoredAdjudication();
        value.setAdjudicationId(901L);
        value.setRequestId(prepared.requestId());
        value.setCaseId(prepared.caseId());
        value.setCaseVersionBefore(prepared.caseVersionBefore());
        value.setCaseVersionAfter(prepared.caseVersionAfter());
        value.setFactFingerprint(prepared.factFingerprint());
        value.setSourceConfirmationEventId(
                prepared.sourceConfirmationEventId());
        value.setTargetConfirmationEventId(
                prepared.targetConfirmationEventId());
        value.setDiscrepancyType(prepared.discrepancyType());
        value.setDiscrepancyQuantity(prepared.discrepancyQuantity());
        value.setSourceCostPrice(prepared.sourceCostPrice());
        value.setDiscrepancyAmount(prepared.discrepancyAmount());
        value.setDecisionFingerprint(prepared.decisionFingerprint());
        value.setAdjudicationNote(prepared.adjudicationNote());
        value.setEvidenceRefs(prepared.evidenceRefs());
        value.setRequiredPermission(prepared.requiredPermission());
        value.setAdjudicatorUserId(prepared.adjudicatorUserId());
        value.setAdjudicatorName(prepared.adjudicatorName());
        value.setPlanStatus(prepared.planStatus());
        value.setCreateTime(Date.from(NOW));
        return value;
    }

    private static InvTransferReceiptDiscrepancyStoredAdjudicationAction
            action(PreparedAdjudication prepared)
    {
        var source = prepared.actions().get(0);
        InvTransferReceiptDiscrepancyStoredAdjudicationAction value =
                new InvTransferReceiptDiscrepancyStoredAdjudicationAction();
        value.setActionId(902L);
        value.setAdjudicationId(901L);
        value.setCaseId(prepared.caseId());
        value.setSequence(source.sequence());
        value.setActionType(source.actionType());
        value.setCoverageKind(source.coverageKind());
        value.setQuantity(source.quantity());
        value.setAmount(source.amount());
        value.setResponsibleParty(source.responsibleParty());
        value.setNote(source.note());
        value.setExecutionStatus(source.executionStatus());
        return value;
    }

    private static Request request()
    {
        return new Request("adjudicate-0001", 700L, 3L,
                fingerprint(), "独立裁决", "evidence-1", List.of(
                        new ActionInput(1, "reship",
                                new BigDecimal("1.0000"), "source",
                                "来源补发")));
    }

    private static InvTransferReceiptDiscrepancyReadFact fact()
    {
        InvTransferReceiptDiscrepancyReadFact value =
                new InvTransferReceiptDiscrepancyReadFact();
        value.setDiscrepancyCaseId(700L);
        value.setReceiptId(600L);
        value.setReceiptAllocationId(601L);
        value.setShipmentId(91L);
        value.setTransferId(900L);
        value.setShipmentAllocationId(81L);
        value.setShipmentDetailId(71L);
        value.setTransferDetailId(11L);
        value.setSourceDeptId(301L);
        value.setTargetDeptId(302L);
        value.setSourceName("苏州门店");
        value.setTargetName("南京门店");
        value.setOrderNo("TF202608020001");
        value.setShipmentNo("TS20260802ABCD");
        value.setReceiptNo("TR20260802ABCD");
        value.setReceiptPlanVersion(PLAN);
        value.setItemType("product");
        value.setItemId(1001L);
        value.setItemCode("SKU-1001");
        value.setItemName("演示商品");
        value.setUnit("件");
        value.setDiscrepancyType("shortage");
        value.setDiscrepancyQuantity(new BigDecimal("1.0000"));
        value.setSourceCostPrice(new BigDecimal("10.000000"));
        value.setDiscrepancyAmount(new BigDecimal("10.000000"));
        value.setDiscrepancyNote("封签完整但箱内短少");
        value.setAttachmentRefs("attachment-1");
        value.setFactFingerprint(fingerprint());
        value.setCaseStatus("awaiting_confirmation");
        value.setCaseVersion(3L);
        value.setCaseCreateBy("receiving-user");
        value.setCaseCreateTime(Date.from(NOW.minusSeconds(600)));
        value.setSourceConfirmation(confirmation(1L, "source", 301L,
                91L));
        value.setTargetConfirmation(confirmation(2L, "target", 302L,
                92L));
        return value;
    }

    private static String fingerprint()
    {
        return InvTransferReceiptDiscrepancyFacts.fingerprint(PLAN, 81L,
                "shortage", new BigDecimal("1.0000"),
                new BigDecimal("10.000000"),
                new BigDecimal("10.000000"), "封签完整但箱内短少",
                "attachment-1");
    }

    private static InvTransferReceiptDiscrepancyReadFact.Confirmation
            confirmation(Long id, String role, Long deptId, Long userId)
    {
        InvTransferReceiptDiscrepancyReadFact.Confirmation value =
                new InvTransferReceiptDiscrepancyReadFact.Confirmation();
        value.setEventId(id);
        value.setRequestId("confirm-" + role);
        value.setCaseVersion(3L);
        value.setFactFingerprint(fingerprint());
        value.setPartyRole(role);
        value.setPartyDeptId(deptId);
        value.setDecision("confirmed");
        value.setOperatorUserId(userId);
        value.setOperatorName(role + "-user");
        value.setCreateTime(Date.from(NOW.minusSeconds(id)));
        return value;
    }

    private record Fixture(
            InvTransferReceiptDiscrepancyReadFact fact,
            InvTransferReceiptDiscrepancyStoredAdjudication stored,
            InvTransferReceiptDiscrepancyStoredAdjudicationAction action)
    {
    }
}
