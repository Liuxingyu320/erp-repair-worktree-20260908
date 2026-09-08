package com.erp.inventory.domain.transfer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.Objects;
import java.util.Set;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyConfirmationProjection.CaseBoundary;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyConfirmationProjection.Result;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyConfirmationProjection.Snapshot;

/** Shared fail-closed authority used by V2 discrepancy readers and writers. */
public final class InvTransferReceiptDiscrepancyBoundaryPolicy
{
    private static final Set<String> TYPES = Set.of("damaged", "shortage");

    private InvTransferReceiptDiscrepancyBoundaryPolicy()
    {
    }

    public static Resolved resolve(
            InvTransferReceiptDiscrepancyReadFact fact,
            Long expectedCaseId, Long selectedDeptId)
    {
        return resolve(fact, expectedCaseId, selectedDeptId, true);
    }

    public static Resolved resolveForAdjudication(
            InvTransferReceiptDiscrepancyReadFact fact,
            Long expectedCaseId)
    {
        return resolve(fact, expectedCaseId, null, false);
    }

    /** Validates the immutable fact without assuming a confirmation lifecycle state. */
    public static ReadBoundary resolveForAdjudicationRead(
            InvTransferReceiptDiscrepancyReadFact fact,
            Long expectedCaseId)
    {
        return readBoundary(fact, expectedCaseId, null, false);
    }

    private static Resolved resolve(
            InvTransferReceiptDiscrepancyReadFact fact,
            Long expectedCaseId, Long selectedDeptId,
            boolean requirePartyScope)
    {
        ReadBoundary read = readBoundary(fact, expectedCaseId,
                selectedDeptId, requirePartyScope);
        Result projection = InvTransferReceiptDiscrepancyConfirmationProjection
                .project(read.boundary(), read.source(), read.target());
        String currentRole = !requirePartyScope ? null
                : Objects.equals(selectedDeptId, fact.getSourceDeptId())
                        ? InvTransferReceiptDiscrepancyConfirmationProjection
                                .SOURCE
                        : InvTransferReceiptDiscrepancyConfirmationProjection
                                .TARGET;
        return new Resolved(fact, read.boundary(), read.source(),
                read.target(), projection, currentRole);
    }

    private static ReadBoundary readBoundary(
            InvTransferReceiptDiscrepancyReadFact fact,
            Long expectedCaseId, Long selectedDeptId,
            boolean requirePartyScope)
    {
        validateFact(expectedCaseId, selectedDeptId, fact,
                requirePartyScope);
        CaseBoundary boundary = new CaseBoundary(
                fact.getDiscrepancyCaseId(), fact.getCaseVersion(),
                fact.getFactFingerprint(), fact.getCaseStatus(),
                fact.getSourceDeptId(), fact.getTargetDeptId());
        return new ReadBoundary(fact, boundary,
                snapshot(fact.getSourceConfirmation()),
                snapshot(fact.getTargetConfirmation()));
    }

    private static void validateFact(Long expectedCaseId,
            Long selectedDeptId, InvTransferReceiptDiscrepancyReadFact fact,
            boolean requirePartyScope)
    {
        if (fact == null || !Objects.equals(expectedCaseId,
                fact.getDiscrepancyCaseId())
                || !positive(fact.getReceiptId())
                || !positive(fact.getReceiptAllocationId())
                || !positive(fact.getShipmentId())
                || !positive(fact.getTransferId())
                || !positive(fact.getShipmentAllocationId())
                || !positive(fact.getShipmentDetailId())
                || !positive(fact.getTransferDetailId())
                || !positive(fact.getSourceDeptId())
                || !positive(fact.getTargetDeptId())
                || Objects.equals(fact.getSourceDeptId(),
                        fact.getTargetDeptId())
                || requirePartyScope
                        && !Objects.equals(selectedDeptId,
                                fact.getSourceDeptId())
                        && !Objects.equals(selectedDeptId,
                                fact.getTargetDeptId())
                || blank(fact.getOrderNo()) || blank(fact.getShipmentNo())
                || blank(fact.getReceiptNo())
                || blank(fact.getSourceName()) || blank(fact.getTargetName())
                || blank(fact.getItemType()) || !positive(fact.getItemId())
                || blank(fact.getItemCode()) || blank(fact.getItemName())
                || blank(fact.getUnit())
                || !TYPES.contains(fact.getDiscrepancyType())
                || !quantity(fact.getDiscrepancyQuantity(), 4, true)
                || !quantity(fact.getSourceCostPrice(), 6, false)
                || !quantity(fact.getDiscrepancyAmount(), 6, false)
                || blank(fact.getDiscrepancyNote())
                || fact.getDiscrepancyNote().length() > 500
                || blank(fact.getAttachmentRefs())
                || fact.getAttachmentRefs().length() > 2000
                || blank(fact.getCaseCreateBy())
                || fact.getCaseCreateTime() == null)
        {
            throw new ServiceException("差异事项权威事实不可核验");
        }
        BigDecimal amount = fact.getSourceCostPrice()
                .multiply(fact.getDiscrepancyQuantity())
                .setScale(6, RoundingMode.HALF_UP);
        String fingerprint = InvTransferReceiptDiscrepancyFacts.fingerprint(
                fact.getReceiptPlanVersion(),
                fact.getShipmentAllocationId(), fact.getDiscrepancyType(),
                fact.getDiscrepancyQuantity(), fact.getSourceCostPrice(),
                fact.getDiscrepancyAmount(), fact.getDiscrepancyNote(),
                fact.getAttachmentRefs());
        if (!hash(fact.getReceiptPlanVersion())
                || fact.getDiscrepancyAmount().compareTo(amount) != 0
                || !Objects.equals(fact.getFactFingerprint(), fingerprint))
        {
            throw new ServiceException("差异事项金额或事实指纹不可核验");
        }
    }

    private static Snapshot snapshot(
            InvTransferReceiptDiscrepancyReadFact.Confirmation value)
    {
        if (value == null)
        {
            return null;
        }
        return new Snapshot(value.getEventId(), value.getRequestId(),
                value.getCaseVersion(), value.getFactFingerprint(),
                value.getPartyRole(), value.getPartyDeptId(),
                value.getDecision(), value.getNote(),
                value.getOperatorUserId(), value.getOperatorName(),
                instant(value.getCreateTime()));
    }

    private static boolean quantity(BigDecimal value, int scale,
            boolean positive)
    {
        return value != null && value.scale() <= scale
                && (positive ? value.signum() > 0 : value.signum() >= 0);
    }

    private static java.time.Instant instant(Date value)
    {
        if (value == null)
        {
            throw new ServiceException("差异事项缺少时间事实");
        }
        return value.toInstant();
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

    public record Resolved(
            InvTransferReceiptDiscrepancyReadFact fact,
            CaseBoundary boundary,
            Snapshot source,
            Snapshot target,
            Result projection,
            String currentPartyRole)
    {
    }

    public record ReadBoundary(
            InvTransferReceiptDiscrepancyReadFact fact,
            CaseBoundary boundary,
            Snapshot source,
            Snapshot target)
    {
    }
}
