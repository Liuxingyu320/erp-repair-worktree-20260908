package com.erp.inventory.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.transfer.InvItemFulfillmentPolicy;
import com.erp.inventory.domain.transfer.InvShipmentPlanningItemKey;
import com.erp.inventory.domain.transfer.InvTransferReceiptBalanceKey;
import com.erp.inventory.domain.transfer.InvTransferReceiptDerivedLotKey;
import com.erp.inventory.domain.transfer.InvTransferReceiptGeneratedId;
import com.erp.inventory.domain.transfer.InvTransferReceiptLocationCandidate;
import com.erp.inventory.domain.transfer.InvTransferReceiptLockedAllocation;
import com.erp.inventory.domain.transfer.InvTransferReceiptLockedDetail;
import com.erp.inventory.domain.transfer.InvTransferReceiptLockedTransferDetail;
import com.erp.inventory.domain.transfer.InvTransferReceiptPlanningSerialFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptTargetBalance;
import com.erp.inventory.domain.transfer.InvTransferReceiptTargetLot;
import com.erp.inventory.domain.transfer.InvTransferReceiptTargetStock;
import com.erp.inventory.domain.transfer.InvTransferShipmentReceiptPreparedMutation;
import com.erp.inventory.domain.transfer.InvWarehouseStockMode;

/**
 * Stage 12C fixed-order lock and guarded-mutation contract.
 *
 * <p>The receipt service deliberately does not inject this mapper in 12C1.</p>
 */
public interface InvTransferShipmentReceiptPersistenceMapper
{
    List<InvTransferReceiptLockedTransferDetail> selectTransferDetailsForUpdate(
            @Param("transferId") Long transferId);

    List<InvTransferReceiptLockedDetail> selectShipmentDetailsForUpdate(
            @Param("shipmentId") Long shipmentId);

    List<InvTransferReceiptLockedAllocation> selectAllocationsForUpdate(
            @Param("shipmentId") Long shipmentId);

    InvWarehouseStockMode selectTargetWarehouseModeForUpdate(
            @Param("warehouseId") Long warehouseId);

    List<InvItemFulfillmentPolicy> selectPoliciesForUpdate(
            @Param("itemKeys") List<InvShipmentPlanningItemKey> itemKeys);

    List<InvTransferReceiptTargetStock> selectTargetStocksForUpdate(
            @Param("shopDeptId") Long shopDeptId,
            @Param("warehouseId") Long warehouseId,
            @Param("itemKeys") List<InvShipmentPlanningItemKey> itemKeys);

    List<InvTransferReceiptTargetLot> selectSourceLotsForUpdate(
            @Param("lotIds") List<Long> lotIds);

    List<InvTransferReceiptLocationCandidate> selectLocationsForUpdate(
            @Param("sourceLocationIds") List<Long> sourceLocationIds,
            @Param("targetWarehouseId") Long targetWarehouseId);

    List<InvTransferReceiptTargetLot> selectDerivedLotsForUpdate(
            @Param("keys") List<InvTransferReceiptDerivedLotKey> keys);

    List<InvTransferReceiptTargetBalance> selectTargetBalancesForUpdate(
            @Param("warehouseId") Long warehouseId,
            @Param("keys") List<InvTransferReceiptBalanceKey> keys);

    List<InvTransferReceiptPlanningSerialFact> selectShipmentSerialsForUpdate(
            @Param("shipmentId") Long shipmentId);

    int insertReceipt(
            @Param("receipt") InvTransferShipmentReceiptPreparedMutation
                    .Receipt receipt,
            @Param("generatedId") InvTransferReceiptGeneratedId generatedId);

    int insertTargetStock(
            @Param("stock") InvTransferShipmentReceiptPreparedMutation
                    .TargetStock stock,
            @Param("warehouseId") Long warehouseId,
            @Param("createBy") String createBy,
            @Param("generatedId") InvTransferReceiptGeneratedId generatedId);

    int insertTargetLot(
            @Param("lot") InvTransferShipmentReceiptPreparedMutation
                    .TargetLot lot,
            @Param("transferId") Long transferId,
            @Param("createBy") String createBy,
            @Param("generatedId") InvTransferReceiptGeneratedId generatedId);

    int insertTargetBalance(
            @Param("balance") InvTransferShipmentReceiptPreparedMutation
                    .TargetBalance balance,
            @Param("targetLotId") Long targetLotId,
            @Param("productId") Long productId,
            @Param("warehouseId") Long warehouseId,
            @Param("createBy") String createBy,
            @Param("generatedId") InvTransferReceiptGeneratedId generatedId);

    int insertStockLog(
            @Param("stockLog") InvTransferShipmentReceiptPreparedMutation
                    .StockLog stockLog,
            @Param("receipt") InvTransferShipmentReceiptPreparedMutation
                    .Receipt receipt,
            @Param("generatedId") InvTransferReceiptGeneratedId generatedId);

    int insertReceiptAllocation(
            @Param("allocation") InvTransferShipmentReceiptPreparedMutation
                    .Allocation allocation,
            @Param("receiptId") Long receiptId,
            @Param("shipmentId") Long shipmentId,
            @Param("acceptedTargetLotId") Long acceptedTargetLotId,
            @Param("acceptedTargetBalanceId") Long acceptedTargetBalanceId,
            @Param("damagedTargetLotId") Long damagedTargetLotId,
            @Param("damagedTargetBalanceId") Long damagedTargetBalanceId,
            @Param("createBy") String createBy,
            @Param("generatedId") InvTransferReceiptGeneratedId generatedId);

    int insertReceiptDiscrepancyCase(
            @Param("discrepancy") InvTransferShipmentReceiptPreparedMutation
                    .DiscrepancyCase discrepancy,
            @Param("receipt") InvTransferShipmentReceiptPreparedMutation
                    .Receipt receipt,
            @Param("receiptId") Long receiptId,
            @Param("receiptAllocationId") Long receiptAllocationId,
            @Param("generatedId") InvTransferReceiptGeneratedId generatedId);

    int insertReceiptSerial(
            @Param("serial") InvTransferShipmentReceiptPreparedMutation
                    .Serial serial,
            @Param("receiptId") Long receiptId,
            @Param("shipmentId") Long shipmentId,
            @Param("receiptAllocationId") Long receiptAllocationId,
            @Param("targetBalanceId") Long targetBalanceId,
            @Param("targetLotId") Long targetLotId,
            @Param("createBy") String createBy);

    int insertReceiptLedger(
            @Param("ledger") InvTransferShipmentReceiptPreparedMutation
                    .Ledger ledger,
            @Param("receipt") InvTransferShipmentReceiptPreparedMutation
                    .Receipt receipt,
            @Param("receiptId") Long receiptId,
            @Param("receiptAllocationId") Long receiptAllocationId,
            @Param("targetBalanceId") Long targetBalanceId,
            @Param("targetLotId") Long targetLotId,
            @Param("summaryStockLogId") Long summaryStockLogId);

    int updateAllocationProgress(
            @Param("allocationId") Long allocationId,
            @Param("shipmentId") Long shipmentId,
            @Param("receiptVersion") Long receiptVersion,
            @Param("acceptedDelta") BigDecimal acceptedDelta,
            @Param("damagedDelta") BigDecimal damagedDelta,
            @Param("shortageDelta") BigDecimal shortageDelta);

    int addTargetStock(
            @Param("stockId") Long stockId,
            @Param("shopDeptId") Long shopDeptId,
            @Param("warehouseId") Long warehouseId,
            @Param("version") Long version,
            @Param("acceptedQuantity") BigDecimal acceptedQuantity,
            @Param("damagedQuantity") BigDecimal damagedQuantity,
            @Param("incomingCost") BigDecimal incomingCost,
            @Param("updateBy") String updateBy);

    int addTargetBalance(
            @Param("balanceId") Long balanceId,
            @Param("warehouseId") Long warehouseId,
            @Param("lotId") Long lotId,
            @Param("locationId") Long locationId,
            @Param("version") Long version,
            @Param("availableQuantity") BigDecimal availableQuantity,
            @Param("quarantineQuantity") BigDecimal quarantineQuantity,
            @Param("incomingCost") BigDecimal incomingCost,
            @Param("updateBy") String updateBy);

    int moveSerialToTarget(
            @Param("serialId") Long serialId,
            @Param("sourceWarehouseId") Long sourceWarehouseId,
            @Param("sourceBalanceId") Long sourceBalanceId,
            @Param("sourceLotId") Long sourceLotId,
            @Param("sourceLocationId") Long sourceLocationId,
            @Param("targetWarehouseId") Long targetWarehouseId,
            @Param("targetBalanceId") Long targetBalanceId,
            @Param("targetLotId") Long targetLotId,
            @Param("targetLocationId") Long targetLocationId,
            @Param("statusAfter") String statusAfter,
            @Param("updateBy") String updateBy);

    int addShipmentDetailFulfilled(
            @Param("detail") InvTransferShipmentReceiptPreparedMutation
                    .DetailProgress detail,
            @Param("shipmentId") Long shipmentId,
            @Param("transferId") Long transferId,
            @Param("transferDetailId") Long transferDetailId);

    int addTransferDetailFulfilled(
            @Param("detail") InvTransferShipmentReceiptPreparedMutation
                    .DetailProgress detail,
            @Param("transferId") Long transferId);

    int updateShipmentLifecycle(
            @Param("shipmentId") Long shipmentId,
            @Param("transferId") Long transferId,
            @Param("expectedStatus") String expectedStatus,
            @Param("statusAfter") String statusAfter,
            @Param("receivedBy") String receivedBy,
            @Param("arrivedTime") Instant arrivedTime);

    int updateTransferLifecycle(
            @Param("transferId") Long transferId,
            @Param("expectedStatus") String expectedStatus,
            @Param("expectedVersion") Long expectedVersion,
            @Param("statusAfter") String statusAfter,
            @Param("receivedTime") Instant receivedTime,
            @Param("updateBy") String updateBy);

    int insertTransferStatusLog(
            @Param("transferId") Long transferId,
            @Param("fromStatus") String fromStatus,
            @Param("toStatus") String toStatus,
            @Param("action") String action,
            @Param("operatorId") Long operatorId,
            @Param("operatorName") String operatorName,
            @Param("reason") String reason);
}
