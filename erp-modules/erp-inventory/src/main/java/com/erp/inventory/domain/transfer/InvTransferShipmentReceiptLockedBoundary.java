package com.erp.inventory.domain.transfer;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable value snapshot of every fact protected by the receipt lock order.
 *
 * <p>No mapper entity or mutable collection crosses this boundary. The shared
 * composition remains available only for the already-pure request policy;
 * mutation planning reads the scalar snapshots below.</p>
 */
public record InvTransferShipmentReceiptLockedBoundary(
        Header header,
        InvTransferShipmentReceiptPlanComposer.Composition composition,
        Map<Long, Allocation> allocations,
        Map<Long, TransferDetail> transferDetails,
        Map<InvShipmentPlanningItemKey, TargetStock> targetStocks,
        Map<Long, SourceLot> sourceLots,
        Map<InvTransferReceiptDerivedLotKey, TargetLot> targetLots,
        Map<Long, Location> targetLocations,
        Map<InvTransferReceiptBalanceKey, TargetBalance> targetBalances,
        Map<Long, Serial> serials)
{
    public InvTransferShipmentReceiptLockedBoundary
    {
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(composition, "composition");
        allocations = Map.copyOf(allocations);
        transferDetails = Map.copyOf(transferDetails);
        targetStocks = Map.copyOf(targetStocks);
        sourceLots = Map.copyOf(sourceLots);
        targetLots = Map.copyOf(targetLots);
        targetLocations = Map.copyOf(targetLocations);
        targetBalances = Map.copyOf(targetBalances);
        serials = Map.copyOf(serials);
    }

    public record Header(
            Long shipmentId,
            Long transferId,
            Long sourceWarehouseId,
            Long sourceLocationDeptId,
            Long targetWarehouseId,
            String shipmentNo,
            String shipmentPlanVersion,
            Long sealedRevisionId,
            String sourceReconcileBatch,
            String targetReconcileBatch,
            String shipmentStatus,
            String transferStatus,
            Long transferVersion)
    {
    }

    public record Allocation(
            Long allocationId,
            Long shipmentDetailId,
            Long transferDetailId,
            String itemType,
            Long itemId,
            Long productId,
            String allocationPolicy,
            String trackingPolicy,
            BigDecimal allocatedQuantity,
            Long sourceBalanceId,
            Long sourceLotId,
            Long sourceLocationId,
            BigDecimal costPrice,
            BigDecimal totalCost,
            BigDecimal acceptedReceivedQuantity,
            BigDecimal damagedReceivedQuantity,
            BigDecimal shortageReportedQuantity,
            Long receiptVersion,
            BigDecimal shipmentDetailShippedQuantity,
            BigDecimal shipmentDetailReceivedQuantity,
            BigDecimal transferDetailDeliveredQuantity,
            BigDecimal transferDetailReceivedQuantity)
    {
    }

    public record TargetStock(
            Long stockId,
            InvShipmentPlanningItemKey itemKey,
            Long productId,
            Long shopDeptId,
            Long warehouseId,
            BigDecimal currentQuantity,
            BigDecimal lockedQuantity,
            BigDecimal availableQuantity,
            BigDecimal quarantineQuantity,
            BigDecimal costPrice,
            BigDecimal totalCost,
            Long version)
    {
    }

    public record TransferDetail(
            Long detailId,
            String itemType,
            Long itemId,
            Long productId,
            BigDecimal quantity,
            BigDecimal deliveredQuantity,
            BigDecimal receivedQuantity)
    {
    }

    public record SourceLot(
            Long lotId,
            String lotNo,
            String itemType,
            Long itemId,
            Long productId,
            Long warehouseId,
            String supplierBatchNo,
            Date productionDate,
            Date expiryDate,
            String qcStatus,
            String lotStatus)
    {
        public SourceLot
        {
            productionDate = copy(productionDate);
            expiryDate = copy(expiryDate);
        }

        @Override
        public Date productionDate()
        {
            return copy(productionDate);
        }

        @Override
        public Date expiryDate()
        {
            return copy(expiryDate);
        }
    }

    public record TargetLot(
            Long lotId,
            InvTransferReceiptDerivedLotKey key,
            String lotNo,
            Long productId,
            String qcStatus,
            String lotStatus)
    {
    }

    public record Location(
            Long locationId,
            Long warehouseId,
            String locationCode,
            String locationName,
            String locationType)
    {
    }

    public record TargetBalance(
            Long balanceId,
            Long warehouseId,
            Long lotId,
            Long locationId,
            BigDecimal currentQuantity,
            BigDecimal lockedQuantity,
            BigDecimal availableQuantity,
            BigDecimal quarantineQuantity,
            BigDecimal costPrice,
            BigDecimal totalCost,
            Long version)
    {
    }

    public record Serial(
            Long shipmentSerialId,
            Long allocationId,
            Long shipmentId,
            Long serialId,
            String serialNo,
            String itemType,
            Long itemId,
            Long sourceWarehouseId,
            Long sourceBalanceId,
            Long sourceLotId,
            Long sourceLocationId,
            String currentStatus)
    {
    }

    private static Date copy(Date value)
    {
        return value == null ? null : new Date(value.getTime());
    }
}
