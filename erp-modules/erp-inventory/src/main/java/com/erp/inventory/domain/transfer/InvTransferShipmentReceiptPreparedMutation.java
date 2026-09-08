package com.erp.inventory.domain.transfer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Complete, deterministic write intent built before the first mutation. */
public record InvTransferShipmentReceiptPreparedMutation(
        Receipt receipt,
        List<TargetStock> targetStocks,
        List<StockLog> stockLogs,
        List<TargetLot> targetLots,
        List<TargetBalance> targetBalances,
        List<Allocation> allocations,
        List<DiscrepancyCase> discrepancyCases,
        List<Serial> serials,
        List<Ledger> ledgers,
        List<DetailProgress> shipmentDetails,
        List<DetailProgress> transferDetails,
        Lifecycle lifecycle)
{
    public enum ReceiptSemantic
    {
        ORDINARY,
        FIXED_RETURN
    }

    public InvTransferShipmentReceiptPreparedMutation
    {
        targetStocks = List.copyOf(targetStocks);
        stockLogs = List.copyOf(stockLogs);
        targetLots = List.copyOf(targetLots);
        targetBalances = List.copyOf(targetBalances);
        allocations = List.copyOf(allocations);
        discrepancyCases = List.copyOf(discrepancyCases);
        serials = List.copyOf(serials);
        ledgers = List.copyOf(ledgers);
        shipmentDetails = List.copyOf(shipmentDetails);
        transferDetails = List.copyOf(transferDetails);
    }

    public record Receipt(
            String commandRequestId,
            String requestFingerprint,
            String receiptNo,
            ReceiptSemantic receiptSemantic,
            Long shipmentId,
            Long transferId,
            String receiptPlanVersion,
            String sourceShipmentPlanVersion,
            String sourceReconcileBatch,
            Long targetWarehouseId,
            String targetReconcileBatch,
            Instant arrivedTime,
            Instant createdTime,
            boolean finalizeShipment,
            BigDecimal acceptedQuantity,
            BigDecimal damagedQuantity,
            BigDecimal shortageQuantity,
            BigDecimal remainingQuantityAfter,
            String receiptStatus,
            Long operatorUserId,
            String operatorName,
            String createBy,
            String remark)
    {
    }

    public record TargetStock(
            InvShipmentPlanningItemKey itemKey,
            Long productId,
            Long existingStockId,
            Long expectedVersion,
            BigDecimal currentQuantityBefore,
            BigDecimal currentQuantityAfter,
            BigDecimal acceptedQuantity,
            BigDecimal damagedQuantity,
            BigDecimal incomingCost)
    {
    }

    public record StockLog(
            InvShipmentPlanningItemKey itemKey,
            Long productId,
            BigDecimal changeQuantity,
            BigDecimal beforeQuantity,
            BigDecimal afterQuantity,
            BigDecimal costPrice,
            BigDecimal acceptedQuantity,
            BigDecimal damagedQuantity)
    {
    }

    public record TargetLot(
            InvTransferReceiptDerivedLotKey key,
            Long productId,
            Long existingLotId,
            String lotNo,
            String supplierBatchNo,
            java.util.Date productionDate,
            java.util.Date expiryDate,
            String qcStatus,
            String lotStatus)
    {
        public TargetLot
        {
            productionDate = copy(productionDate);
            expiryDate = copy(expiryDate);
        }

        @Override
        public java.util.Date productionDate()
        {
            return copy(productionDate);
        }

        @Override
        public java.util.Date expiryDate()
        {
            return copy(expiryDate);
        }
    }

    public record BalanceKey(
            InvTransferReceiptDerivedLotKey lotKey,
            Long locationId)
    {
    }

    public record TargetBalance(
            BalanceKey key,
            Long existingBalanceId,
            Long expectedVersion,
            BigDecimal currentQuantityBefore,
            BigDecimal currentQuantityAfter,
            BigDecimal availableQuantity,
            BigDecimal quarantineQuantity,
            BigDecimal incomingCost)
    {
    }

    public record Allocation(
            Long shipmentAllocationId,
            Long shipmentDetailId,
            Long transferDetailId,
            InvShipmentPlanningItemKey itemKey,
            Long productId,
            String trackingPolicy,
            Long sourceBalanceId,
            Long sourceLotId,
            Long sourceLocationId,
            BigDecimal sourceCostPrice,
            BigDecimal acceptedQuantity,
            BigDecimal damagedQuantity,
            BigDecimal shortageQuantity,
            BigDecimal acceptedCost,
            BigDecimal damagedCost,
            Long expectedReceiptVersion,
            Long acceptedLocationId,
            InvTransferReceiptDerivedLotKey acceptedLotKey,
            BigDecimal acceptedBalanceQuantityBefore,
            BigDecimal acceptedBalanceQuantityAfter,
            Long acceptedBalanceVersionBefore,
            Long acceptedBalanceVersionAfter,
            Long quarantineLocationId,
            InvTransferReceiptDerivedLotKey damagedLotKey,
            BigDecimal damagedBalanceQuantityBefore,
            BigDecimal damagedBalanceQuantityAfter,
            Long damagedBalanceVersionBefore,
            Long damagedBalanceVersionAfter,
            String discrepancyNote,
            String attachmentRefs)
    {
    }

    /** Immutable discrepancy fact prepared before the first receipt DML. */
    public record DiscrepancyCase(
            Long shipmentAllocationId,
            String discrepancyType,
            BigDecimal discrepancyQuantity,
            BigDecimal sourceCostPrice,
            BigDecimal discrepancyAmount,
            String factFingerprint,
            String discrepancyNote,
            String attachmentRefs)
    {
    }

    public record Serial(
            Long shipmentSerialId,
            Long shipmentAllocationId,
            Long serialId,
            String serialNo,
            String disposition,
            Long sourceWarehouseId,
            Long sourceBalanceId,
            Long sourceLotId,
            Long sourceLocationId,
            InvTransferReceiptDerivedLotKey targetLotKey,
            Long targetLocationId,
            String statusBefore,
            String statusAfter)
    {
    }

    public record Ledger(
            String requestId,
            Long shipmentAllocationId,
            String disposition,
            InvShipmentPlanningItemKey itemKey,
            Long productId,
            BalanceKey targetBalanceKey,
            BigDecimal changeQuantity,
            BigDecimal beforeQuantity,
            BigDecimal afterQuantity,
            BigDecimal costPrice,
            BigDecimal totalCost)
    {
    }

    public record DetailProgress(
            Long detailId,
            BigDecimal expectedReceivedQuantity,
            BigDecimal fulfillmentDelta,
            BigDecimal receivedQuantityAfter)
    {
    }

    public record Lifecycle(
            String expectedShipmentStatus,
            String expectedTransferStatus,
            Long expectedTransferVersion,
            String shipmentStatusAfter,
            String transferStatusAfter,
            String action)
    {
    }

    private static java.util.Date copy(java.util.Date value)
    {
        return value == null ? null : new java.util.Date(value.getTime());
    }
}
