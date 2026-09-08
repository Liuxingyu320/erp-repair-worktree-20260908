package com.erp.inventory.domain.transfer;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyConfirmationProjection.CaseBoundary;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyConfirmationProjection.Result;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyConfirmationProjection.Snapshot;

/** Pure contract for one future append-only bilateral confirmation command. */
public final class InvTransferReceiptDiscrepancyConfirmationPolicy
{
    private static final Set<String> DECISIONS = Set.of(
            InvTransferReceiptDiscrepancyConfirmationProjection.CONFIRMED,
            InvTransferReceiptDiscrepancyConfirmationProjection.DISPUTED);

    private InvTransferReceiptDiscrepancyConfirmationPolicy()
    {
    }

    public static Prepared prepare(Request request, CaseBoundary boundary,
            Long selectedDeptId, Long operatorUserId, String operatorName,
            Instant createdAt, Snapshot source, Snapshot target)
    {
        Result current = InvTransferReceiptDiscrepancyConfirmationProjection
                .project(boundary, source, target);
        validateRequest(request, boundary, selectedDeptId, operatorUserId,
                operatorName, createdAt);
        String partyRole = partyRole(boundary, selectedDeptId);
        String note = trimToNull(request.note());
        Snapshot proposed = new Snapshot(Long.MAX_VALUE,
                request.requestId(), request.caseVersion(),
                request.factFingerprint(), partyRole, selectedDeptId,
                request.decision(), note, operatorUserId,
                operatorName.trim(), createdAt);
        Result after = InvTransferReceiptDiscrepancyConfirmationProjection
                .project(boundary,
                        InvTransferReceiptDiscrepancyConfirmationProjection
                                .SOURCE.equals(partyRole)
                                        ? proposed : source,
                        InvTransferReceiptDiscrepancyConfirmationProjection
                                .TARGET.equals(partyRole)
                                        ? proposed : target);
        return new Prepared(request.requestId(), request.caseId(),
                request.caseVersion(), request.factFingerprint(), partyRole,
                selectedDeptId, request.decision(), note, operatorUserId,
                operatorName.trim(), createdAt, current.confirmationState(),
                after.confirmationState(),
                after.readyForAdjudication());
    }

    private static void validateRequest(Request request,
            CaseBoundary boundary, Long selectedDeptId, Long operatorUserId,
            String operatorName, Instant createdAt)
    {
        if (request == null || !requestId(request.requestId())
                || !Objects.equals(request.caseId(),
                        boundary.discrepancyCaseId())
                || !Objects.equals(request.caseVersion(),
                        boundary.caseVersion())
                || !Objects.equals(request.factFingerprint(),
                        boundary.factFingerprint())
                || !DECISIONS.contains(request.decision())
                || !positive(selectedDeptId) || !positive(operatorUserId)
                || blank(operatorName) || operatorName.trim().length() > 64
                || createdAt == null)
        {
            throw new ServiceException("差异事实确认请求不可核验");
        }
        String note = trimToNull(request.note());
        if (note != null && note.length() > 500)
        {
            throw new ServiceException("差异确认说明不能超过500字");
        }
        if (InvTransferReceiptDiscrepancyConfirmationProjection.DISPUTED
                .equals(request.decision()) && note == null)
        {
            throw new ServiceException("质疑差异事实时必须填写说明");
        }
    }

    private static String partyRole(CaseBoundary boundary,
            Long selectedDeptId)
    {
        if (Objects.equals(boundary.sourceDeptId(), selectedDeptId))
        {
            return InvTransferReceiptDiscrepancyConfirmationProjection.SOURCE;
        }
        if (Objects.equals(boundary.targetDeptId(), selectedDeptId))
        {
            return InvTransferReceiptDiscrepancyConfirmationProjection.TARGET;
        }
        throw new ServiceException("当前组织不是该差异事项的调出方或调入方");
    }

    private static boolean requestId(String value)
    {
        return value != null && value.matches(
                "[A-Za-z0-9][A-Za-z0-9._:-]{7,127}");
    }

    private static boolean positive(Long value)
    {
        return value != null && value > 0;
    }

    private static boolean blank(String value)
    {
        return value == null || value.isBlank();
    }

    private static String trimToNull(String value)
    {
        if (value == null || value.trim().isEmpty())
        {
            return null;
        }
        return value.trim();
    }

    public record Request(
            String requestId,
            Long caseId,
            Long caseVersion,
            String factFingerprint,
            String decision,
            String note)
    {
    }

    public record Prepared(
            String requestId,
            Long caseId,
            Long caseVersion,
            String factFingerprint,
            String partyRole,
            Long partyDeptId,
            String decision,
            String note,
            Long operatorUserId,
            String operatorName,
            Instant createdAt,
            String confirmationStateBefore,
            String confirmationStateAfter,
            boolean readyForAdjudicationAfter)
    {
    }
}
