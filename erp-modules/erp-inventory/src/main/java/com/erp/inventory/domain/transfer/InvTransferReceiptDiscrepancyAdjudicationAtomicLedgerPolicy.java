package com.erp.inventory.domain.transfer;

import java.math.BigDecimal;
import java.util.Set;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy.PreparedExecution;

/** Pure fail-closed classifier for ledger-only adjudication effects. */
public final class
        InvTransferReceiptDiscrepancyAdjudicationAtomicLedgerPolicy
{
    public static final String LEDGER_RESPONSIBILITY =
            "responsibility_ledger";
    public static final String LEDGER_SHORTAGE_LOSS =
            "shortage_loss_ledger";

    private static final String ACTION_RESPONSIBILITY =
            "responsibility_adjustment";
    private static final String ACTION_TRANSPORT_LOSS =
            "transport_loss_write_off";

    private InvTransferReceiptDiscrepancyAdjudicationAtomicLedgerPolicy()
    {
    }

    public static Prepared prepare(PreparedExecution execution)
    {
        requireAtomicExecution(execution);
        String ledgerKind = classify(execution);
        return new Prepared(ledgerKind, execution);
    }

    private static void requireAtomicExecution(PreparedExecution value)
    {
        if (value == null || !requestId(value.requestId())
                || !positive(value.caseId())
                || !positive(value.adjudicationId())
                || !positive(value.actionId())
                || value.actionSequence() == null
                || value.actionSequence() <= 0
                || value.caseVersionBefore() == null
                || value.caseVersionAfter() == null
                || !exactlyNext(value.caseVersionBefore(),
                        value.caseVersionAfter())
                || value.executionVersionBefore() == null
                || value.executionVersionAfter() == null
                || value.executionVersionBefore() != 0
                || !exactlyNext(value.executionVersionBefore(),
                        value.executionVersionAfter())
                || !"dispatch".equals(value.command())
                || value.effectReference() != null
                || !"pending".equals(value.actionStatusBefore())
                || !"completed".equals(value.actionStatusAfter())
                || !Set.of("adjudication_planned",
                        "adjudication_executing").contains(
                                value.caseStatusBefore())
                || !Set.of("adjudication_executing", "resolved")
                        .contains(value.caseStatusAfter())
                || !value.caseStatusBefore().equals(
                        value.planStatusBefore())
                || !value.caseStatusAfter().equals(value.planStatusAfter())
                || !Set.of("shortage", "damaged").contains(
                        value.discrepancyType())
                || !quantity(value.quantity())
                || !amount(value.sourceCostPrice())
                || !amount(value.amount())
                || value.responsibleParty() == null
                || !Set.of("source", "target", "carrier", "company")
                        .contains(value.responsibleParty())
                || !InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                        .REQUIRED_PERMISSION.equals(
                                value.requiredPermission())
                || !positive(value.executorUserId())
                || value.executorName() == null
                || value.executorName().isBlank()
                || value.executorName().length() > 64
                || value.executedAt() == null
                || !hash(value.decisionFingerprint())
                || !hash(value.eventFingerprint()))
        {
            throw new ServiceException("差异裁决原子台账执行事实无效");
        }
    }

    private static String classify(PreparedExecution value)
    {
        if (ACTION_RESPONSIBILITY.equals(value.actionType())
                && LEDGER_RESPONSIBILITY.equals(value.effectKind())
                && Set.of("shortage", "damaged").contains(
                        value.discrepancyType()))
        {
            return LEDGER_RESPONSIBILITY;
        }
        if (ACTION_TRANSPORT_LOSS.equals(value.actionType())
                && "shortage".equals(value.discrepancyType())
                && LEDGER_SHORTAGE_LOSS.equals(value.effectKind()))
        {
            return LEDGER_SHORTAGE_LOSS;
        }
        throw new ServiceException("差异裁决动作不属于当前原子台账边界");
    }

    private static boolean requestId(String value)
    {
        return value != null && value.matches(
                "[A-Za-z0-9][A-Za-z0-9._:-]{7,127}");
    }

    private static boolean exactlyNext(Long before, Long after)
    {
        try
        {
            return Math.addExact(before, 1L) == after;
        }
        catch (NullPointerException | ArithmeticException invalid)
        {
            return false;
        }
    }

    private static boolean hash(String value)
    {
        return value != null && value.matches("[a-f0-9]{64}");
    }

    private static boolean positive(Long value)
    {
        return value != null && value > 0;
    }

    private static boolean quantity(BigDecimal value)
    {
        return value != null && value.signum() > 0 && value.scale() <= 4;
    }

    private static boolean amount(BigDecimal value)
    {
        return value != null && value.signum() >= 0 && value.scale() <= 6;
    }

    public record Prepared(String ledgerKind, PreparedExecution execution)
    {
    }
}
