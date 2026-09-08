package com.erp.inventory.domain.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAtomicLedgerPolicy.Prepared;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy.ActionSnapshot;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy.Actor;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy.Boundary;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy.PreparedExecution;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy.Request;

@DisplayName("V2调拨差异裁决责任与短缺损失纯效果策略")
class
        InvTransferReceiptDiscrepancyAdjudicationAtomicLedgerPolicyTest
{
    @Test
    @DisplayName("短缺和受损责任调整唯一进入责任台账")
    void shouldClassifyBothResponsibilityAdjustments()
    {
        Prepared shortage = prepare(execution("shortage",
                "responsibility_adjustment", null));
        Prepared damaged = prepare(execution("damaged",
                "responsibility_adjustment", null));

        assertThat(shortage.ledgerKind()).isEqualTo(
                "responsibility_ledger");
        assertThat(damaged.ledgerKind()).isEqualTo(
                "responsibility_ledger");
        assertThat(shortage.execution().sourceCostPrice())
                .isEqualByComparingTo("10.000000");
        assertThat(shortage.execution().amount())
                .isEqualByComparingTo("10.000000");
    }

    @Test
    @DisplayName("只有短缺运输损耗核销进入短缺损失台账")
    void shouldClassifyShortageTransportLoss()
    {
        Prepared result = prepare(execution("shortage",
                "transport_loss_write_off", null));

        assertThat(result.ledgerKind()).isEqualTo(
                "shortage_loss_ledger");
        assertThat(result.execution().actionStatusBefore())
                .isEqualTo("pending");
        assertThat(result.execution().actionStatusAfter())
                .isEqualTo("completed");
        assertThat(result.execution().effectReference()).isNull();
    }

    @Test
    @DisplayName("库存或子调拨效果全部拒绝进入双台账边界")
    void shouldRejectEffectsOwnedByOtherBoundaries()
    {
        assertRejected(execution("shortage", "reship",
                "reship_transfer:9001"));
        assertRejected(execution("damaged", "return_to_source",
                "return_transfer:9001"));
        assertRejected(execution("damaged", "damage_write_off", null));
        assertRejected(execution("damaged",
                "transport_loss_write_off", null));
    }

    @Test
    @DisplayName("空执行事实失败关闭")
    void shouldRejectMissingExecution()
    {
        assertThatThrownBy(() -> prepare(null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("执行事实无效");
    }

    private static void assertRejected(PreparedExecution execution)
    {
        assertThatThrownBy(() -> prepare(execution))
                .isInstanceOf(ServiceException.class);
    }

    private static Prepared prepare(PreparedExecution execution)
    {
        return InvTransferReceiptDiscrepancyAdjudicationAtomicLedgerPolicy
                .prepare(execution);
    }

    private static PreparedExecution execution(String discrepancyType,
            String actionType, String reference)
    {
        boolean damagedResponsibility = "damaged".equals(discrepancyType)
                && "responsibility_adjustment".equals(actionType);
        ActionSnapshot action = new ActionSnapshot(900L,
                damagedResponsibility ? 2 : 1, actionType,
                damagedResponsibility ? "responsibility" : "resolution",
                new BigDecimal("1.0000"),
                new BigDecimal("10.000000"), "company", "pending", 0L,
                null);
        List<ActionSnapshot> actions = damagedResponsibility
                ? List.of(new ActionSnapshot(901L, 1,
                        "damage_write_off", "resolution",
                        new BigDecimal("1.0000"),
                        new BigDecimal("10.000000"), "company",
                        "pending", 0L, null), action)
                : List.of(action);
        Boundary boundary = new Boundary(700L, 4L, 4L,
                "adjudication_planned", 800L, "a".repeat(64),
                discrepancyType, new BigDecimal("1.0000"),
                new BigDecimal("10.000000"),
                new BigDecimal("10.000000"),
                "adjudication_planned", actions);
        Request request = new Request("execute-action-0001", 700L, 4L,
                800L, 900L, 0L, "dispatch", reference);
        Actor actor = new Actor(199L, "execution-user", Set.of(
                InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                        .REQUIRED_PERMISSION));
        return InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                .prepare(request, boundary, actor,
                        Instant.parse("2026-08-03T03:00:00Z"));
    }
}
