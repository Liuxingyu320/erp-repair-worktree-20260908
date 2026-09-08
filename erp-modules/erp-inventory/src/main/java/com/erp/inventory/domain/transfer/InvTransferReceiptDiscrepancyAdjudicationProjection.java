package com.erp.inventory.domain.transfer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationPolicy.ActionInput;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationPolicy.Actor;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationPolicy.PreparedAdjudication;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationPolicy.Request;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyBoundaryPolicy.ReadBoundary;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyConfirmationProjection.CaseBoundary;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyConfirmationProjection.Result;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyConfirmationProjection.Snapshot;

/** Pure fail-closed projection for pending and persisted adjudication plans. */
public final class InvTransferReceiptDiscrepancyAdjudicationProjection
{
    private static final String AWAITING = "awaiting_confirmation";

    private InvTransferReceiptDiscrepancyAdjudicationProjection()
    {
    }

    public static Projection project(ReadBoundary read,
            List<InvTransferReceiptDiscrepancyStoredAdjudication> headers,
            List<InvTransferReceiptDiscrepancyStoredAdjudicationAction>
                    actions)
    {
        if (read == null || headers == null || actions == null)
        {
            throw invalid();
        }
        String status = read.boundary().caseStatus();
        if (AWAITING.equals(status))
        {
            if (!headers.isEmpty() || !actions.isEmpty())
            {
                throw invalid();
            }
            Result confirmations =
                    InvTransferReceiptDiscrepancyConfirmationProjection
                            .project(read.boundary(), read.source(),
                                    read.target());
            return new Projection(confirmations, null,
                    confirmations.confirmationState(),
                    confirmations.readyForAdjudication());
        }
        if (!InvTransferReceiptDiscrepancyAdjudicationPolicy.PLAN_STATUS
                .equals(status) || headers.size() != 1)
        {
            throw invalid();
        }
        ValidatedPlan validated = validatePlan(read, headers.get(0),
                actions);
        return new Projection(validated.confirmations(),
                validated.prepared(),
                InvTransferReceiptDiscrepancyAdjudicationPolicy.PLAN_STATUS,
                false);
    }

    private static ValidatedPlan validatePlan(ReadBoundary read,
            InvTransferReceiptDiscrepancyStoredAdjudication stored,
            List<InvTransferReceiptDiscrepancyStoredAdjudicationAction>
                    actions)
    {
        if (stored == null)
        {
            throw invalid();
        }
        List<ActionInput> inputs = new ArrayList<>();
        for (InvTransferReceiptDiscrepancyStoredAdjudicationAction action
                : actions)
        {
            inputs.add(action == null ? null : new ActionInput(
                    action.getSequence(), action.getActionType(),
                    action.getQuantity(), action.getResponsibleParty(),
                    action.getNote()));
        }
        Request request = new Request(stored.getRequestId(),
                stored.getCaseId(), stored.getCaseVersionBefore(),
                stored.getFactFingerprint(), stored.getAdjudicationNote(),
                stored.getEvidenceRefs(), inputs);
        Actor actor = new Actor(stored.getAdjudicatorUserId(),
                stored.getAdjudicatorName(), Set.of(
                        InvTransferReceiptDiscrepancyAdjudicationPolicy
                                .REQUIRED_PERMISSION));
        PreparedAdjudication prepared =
                InvTransferReceiptDiscrepancyAdjudicationPolicy.replay(
                        request, stored, actions, actor);

        InvTransferReceiptDiscrepancyReadFact fact = read.fact();
        if (!Objects.equals(fact.getDiscrepancyCaseId(),
                prepared.caseId())
                || !Objects.equals(fact.getCaseVersion(),
                        prepared.caseVersionAfter())
                || !Objects.equals(fact.getCaseStatus(),
                        prepared.planStatus())
                || !Objects.equals(fact.getFactFingerprint(),
                        prepared.factFingerprint())
                || !Objects.equals(fact.getDiscrepancyType(),
                        prepared.discrepancyType())
                || different(fact.getDiscrepancyQuantity(),
                        prepared.discrepancyQuantity())
                || different(fact.getSourceCostPrice(),
                        prepared.sourceCostPrice())
                || different(fact.getDiscrepancyAmount(),
                        prepared.discrepancyAmount())
                || !AWAITING.equals(prepared.caseStatusBefore()))
        {
            throw invalid();
        }

        CaseBoundary planBasis = new CaseBoundary(
                prepared.caseId(), prepared.caseVersionBefore(),
                prepared.factFingerprint(), AWAITING,
                fact.getSourceDeptId(), fact.getTargetDeptId());
        Result confirmations =
                InvTransferReceiptDiscrepancyConfirmationProjection.project(
                        planBasis, read.source(), read.target());
        Snapshot source = read.source();
        Snapshot target = read.target();
        if (!confirmations.readyForAdjudication()
                || source == null || target == null
                || !Objects.equals(source.eventId(),
                        prepared.sourceConfirmationEventId())
                || !Objects.equals(target.eventId(),
                        prepared.targetConfirmationEventId())
                || Objects.equals(source.operatorUserId(),
                        prepared.adjudicatorUserId())
                || Objects.equals(target.operatorUserId(),
                        prepared.adjudicatorUserId()))
        {
            throw invalid();
        }
        return new ValidatedPlan(confirmations, prepared);
    }

    private static boolean different(BigDecimal left, BigDecimal right)
    {
        return left == null || right == null || left.compareTo(right) != 0;
    }

    private static ServiceException invalid()
    {
        return new ServiceException("差异裁决只读投影不可核验");
    }

    private record ValidatedPlan(
            Result confirmations,
            PreparedAdjudication prepared)
    {
    }

    public record Projection(
            Result confirmations,
            PreparedAdjudication plan,
            String workflowState,
            boolean readyForAdjudication)
    {
    }
}
