package com.erp.inventory.domain.transfer;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import com.erp.common.core.exception.ServiceException;

/** Pure projection of the latest bilateral opinions for one immutable fact. */
public final class InvTransferReceiptDiscrepancyConfirmationProjection
{
    public static final String SOURCE = "source";
    public static final String TARGET = "target";
    public static final String CONFIRMED = "confirmed";
    public static final String DISPUTED = "disputed";
    public static final String AWAITING_CONFIRMATION =
            "awaiting_confirmation";
    public static final String AWAITING_COUNTERPARTY =
            "awaiting_counterparty";
    public static final String READY_FOR_ADJUDICATION =
            "ready_for_adjudication";

    private static final Set<String> DECISIONS = Set.of(CONFIRMED, DISPUTED);

    private InvTransferReceiptDiscrepancyConfirmationProjection()
    {
    }

    public static Result project(CaseBoundary boundary, Snapshot source,
            Snapshot target)
    {
        validateBoundary(boundary);
        PartyProjection sourceProjection = party(source, SOURCE,
                boundary.sourceDeptId(), boundary);
        PartyProjection targetProjection = party(target, TARGET,
                boundary.targetDeptId(), boundary);
        String state;
        if (currentDecision(sourceProjection, DISPUTED)
                || currentDecision(targetProjection, DISPUTED))
        {
            state = DISPUTED;
        }
        else if (currentDecision(sourceProjection, CONFIRMED)
                && currentDecision(targetProjection, CONFIRMED))
        {
            state = READY_FOR_ADJUDICATION;
        }
        else if (currentDecision(sourceProjection, CONFIRMED)
                || currentDecision(targetProjection, CONFIRMED))
        {
            state = AWAITING_COUNTERPARTY;
        }
        else
        {
            state = AWAITING_CONFIRMATION;
        }
        return new Result(sourceProjection, targetProjection, state,
                READY_FOR_ADJUDICATION.equals(state));
    }

    private static PartyProjection party(Snapshot snapshot,
            String expectedRole, Long expectedDeptId, CaseBoundary boundary)
    {
        if (snapshot == null)
        {
            return new PartyProjection(null, false);
        }
        if (!positive(snapshot.eventId()) || !requestId(snapshot.requestId())
                || snapshot.caseVersion() == null
                || snapshot.caseVersion() < 0
                || !hash(snapshot.factFingerprint())
                || !expectedRole.equals(snapshot.partyRole())
                || !Objects.equals(expectedDeptId,
                        snapshot.partyDeptId())
                || !DECISIONS.contains(snapshot.decision())
                || DISPUTED.equals(snapshot.decision())
                        && blank(snapshot.note())
                || snapshot.note() != null && snapshot.note().length() > 500
                || !positive(snapshot.operatorUserId())
                || blank(snapshot.operatorName())
                || snapshot.operatorName().length() > 64
                || snapshot.createdAt() == null)
        {
            throw new ServiceException("差异确认事件不可核验");
        }
        boolean current = Objects.equals(boundary.caseVersion(),
                snapshot.caseVersion()) && Objects.equals(
                        boundary.factFingerprint(),
                        snapshot.factFingerprint());
        return new PartyProjection(snapshot, current);
    }

    private static void validateBoundary(CaseBoundary boundary)
    {
        if (boundary == null || !positive(boundary.discrepancyCaseId())
                || boundary.caseVersion() == null
                || boundary.caseVersion() < 0
                || !hash(boundary.factFingerprint())
                || !AWAITING_CONFIRMATION.equals(boundary.caseStatus())
                || !positive(boundary.sourceDeptId())
                || !positive(boundary.targetDeptId())
                || Objects.equals(boundary.sourceDeptId(),
                        boundary.targetDeptId()))
        {
            throw new ServiceException("差异事项确认边界不可核验");
        }
    }

    private static boolean currentDecision(PartyProjection value,
            String decision)
    {
        return value.currentFact() && decision.equals(
                value.snapshot().decision());
    }

    private static boolean positive(Long value)
    {
        return value != null && value > 0;
    }

    private static boolean blank(String value)
    {
        return value == null || value.isBlank();
    }

    private static boolean hash(String value)
    {
        return value != null && value.matches("[a-f0-9]{64}");
    }

    private static boolean requestId(String value)
    {
        return value != null && value.matches(
                "[A-Za-z0-9][A-Za-z0-9._:-]{7,127}");
    }

    public record CaseBoundary(
            Long discrepancyCaseId,
            Long caseVersion,
            String factFingerprint,
            String caseStatus,
            Long sourceDeptId,
            Long targetDeptId)
    {
    }

    public record Snapshot(
            Long eventId,
            String requestId,
            Long caseVersion,
            String factFingerprint,
            String partyRole,
            Long partyDeptId,
            String decision,
            String note,
            Long operatorUserId,
            String operatorName,
            Instant createdAt)
    {
    }

    public record PartyProjection(
            Snapshot snapshot,
            boolean currentFact)
    {
    }

    public record Result(
            PartyProjection source,
            PartyProjection target,
            String confirmationState,
            boolean readyForAdjudication)
    {
    }
}
