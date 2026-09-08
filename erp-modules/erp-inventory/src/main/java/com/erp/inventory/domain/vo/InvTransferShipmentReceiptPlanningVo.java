package com.erp.inventory.domain.vo;

import java.util.List;

/** Browser-safe immutable read model for an authoritative receipt boundary. */
public record InvTransferShipmentReceiptPlanningVo(
        String shipmentId,
        String shipmentNo,
        String transferId,
        String orderNo,
        String receiptPlanVersion,
        String status,
        String sourceName,
        String destinationName,
        StockAuthority targetStockAuthority,
        String suggestedAcceptedLocationId,
        String suggestedQuarantineLocationId,
        List<Location> acceptedLocations,
        List<Location> quarantineLocations,
        List<Allocation> allocations,
        boolean canCreateReceipt,
        List<String> blockingReasons,
        String generatedAt,
        String dataSource)
{
    public record StockAuthority(
            String warehouseId,
            String writeMode,
            String readMode,
            String reconcileStatus,
            String lastReconcileBatch)
    {
    }

    public record Location(
            String locationId,
            String locationCode,
            String locationName,
            String locationType)
    {
    }

    public record Allocation(
            String shipmentAllocationId,
            String shipmentDetailId,
            String transferDetailId,
            String itemType,
            String itemId,
            String itemCode,
            String itemName,
            String unit,
            String sourceLotId,
            String sourceLotNo,
            String supplierBatchNo,
            String productionDate,
            String expiryDate,
            String sourceLocationId,
            String sourceLocationCode,
            String trackingPolicy,
            String shippedQuantity,
            String acceptedQuantity,
            String damagedQuantity,
            String shortageQuantity,
            String remainingQuantity,
            String suggestedAcceptedQuantity,
            String recommendationStatus,
            List<Serial> serials,
            List<String> blockers)
    {
    }

    public record Serial(
            String serialId,
            String serialLabel,
            String status)
    {
    }
}
