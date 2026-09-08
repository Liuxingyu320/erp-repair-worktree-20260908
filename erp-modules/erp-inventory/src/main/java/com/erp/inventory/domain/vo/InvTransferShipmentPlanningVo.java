package com.erp.inventory.domain.vo;

import java.util.List;

/**
 * Immutable read model for an authoritative shipment suggestion. Identifiers
 * and decimals are strings so JavaScript clients never lose precision.
 */
public record InvTransferShipmentPlanningVo(
        String transferId,
        String orderNo,
        String planVersion,
        String status,
        String sourceName,
        String destinationName,
        SealedRevision sealedRevision,
        StockAuthority stockAuthority,
        List<Line> lines,
        boolean canCreateShipment,
        List<String> blockingReasons,
        String dataSource)
{
    public record SealedRevision(
            String revisionId,
            int revisionNo,
            String snapshotHash)
    {
    }

    public record StockAuthority(
            String writeMode,
            String readMode,
            String reconcileStatus,
            String lastReconcileBatch,
            String recommendationSource,
            String generatedAt,
            String balanceAsOf)
    {
    }

    public record Line(
            String transferDetailId,
            String itemType,
            String itemId,
            String itemCode,
            String itemName,
            String unit,
            String approvedQuantity,
            String shippedQuantity,
            String remainingQuantity,
            String suggestedShipmentQuantity,
            String allocationPolicy,
            String trackingPolicy,
            String recommendationStatus,
            List<Allocation> allocations,
            List<String> blockers)
    {
    }

    public record Allocation(
            String balanceId,
            String lotId,
            String lotNo,
            String expiryDate,
            String receivedAt,
            String locationId,
            String locationCode,
            String availableQuantity,
            String suggestedQuantity,
            int policyRank,
            String eligibility,
            List<Serial> serials)
    {
    }

    public record Serial(
            String serialId,
            String serialLabel,
            String status)
    {
    }
}
