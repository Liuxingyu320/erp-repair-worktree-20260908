package com.erp.inventory.domain.vo;

import java.util.List;

/** JavaScript-safe read model for one fixed quarantine shipment source. */
public record InvTransferReceiptDiscrepancyReturnShipmentPlanningVo(
        String transferId,
        String transferDetailId,
        String adjudicationActionId,
        String planVersion,
        String transferStatus,
        String reservationId,
        String reservationStatus,
        String reservationVersion,
        String sourceWarehouseId,
        String itemType,
        String itemId,
        String productId,
        String trackingPolicy,
        String reservedQuantity,
        String consumedQuantity,
        String releasedQuantity,
        String remainingQuantity,
        String suggestedShipmentQuantity,
        String sourceCostPrice,
        String suggestedAmount,
        Allocation allocation,
        boolean canCreateShipment,
        List<String> blockingReasons,
        String generatedAt,
        String dataSource)
{
    public record Allocation(
            String balanceId,
            String lotId,
            String locationId,
            String frozenQuantity,
            List<Serial> serials)
    {
        public Allocation
        {
            serials = List.copyOf(serials);
        }
    }

    public record Serial(
            String bindingId,
            String receiptSerialId,
            String serialId,
            String serialLabel,
            String lifecycleStatus,
            String physicalStatus,
            String version)
    {
    }

    public InvTransferReceiptDiscrepancyReturnShipmentPlanningVo
    {
        blockingReasons = List.copyOf(blockingReasons);
    }
}
