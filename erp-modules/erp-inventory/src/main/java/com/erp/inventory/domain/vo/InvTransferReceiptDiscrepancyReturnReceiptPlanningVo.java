package com.erp.inventory.domain.vo;

import java.util.List;

/** Browser-safe plan for receiving an expected damaged return. */
public record InvTransferReceiptDiscrepancyReturnReceiptPlanningVo(
        String shipmentId,
        String shipmentNo,
        String transferId,
        String orderNo,
        String returnReceiptPlanVersion,
        String status,
        String sourceName,
        String destinationName,
        StockAuthority targetStockAuthority,
        String suggestedQuarantineLocationId,
        List<Location> quarantineLocations,
        List<Allocation> allocations,
        boolean canCreateReturnReceipt,
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
            String sourceLocationId,
            String sourceLocationCode,
            String trackingPolicy,
            String shippedQuantity,
            String returnedQuantity,
            String shortageQuantity,
            String remainingQuantity,
            String suggestedReturnedQuantity,
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
