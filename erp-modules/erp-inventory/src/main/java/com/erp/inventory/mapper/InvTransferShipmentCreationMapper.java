package com.erp.inventory.mapper;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.transfer.InvItemFulfillmentPolicy;
import com.erp.inventory.domain.transfer.InvLockedShipmentBalance;
import com.erp.inventory.domain.transfer.InvShipmentAllocationCandidate;
import com.erp.inventory.domain.transfer.InvShipmentPlanningItemKey;
import com.erp.inventory.domain.transfer.InvShipmentSerialCandidate;
import com.erp.inventory.domain.transfer.InvStockLedgerDetailRecord;
import com.erp.inventory.domain.transfer.InvTransferShipmentAllocationRecord;
import com.erp.inventory.domain.transfer.InvTransferShipmentSerialRecord;
import com.erp.inventory.domain.transfer.InvWarehouseStockMode;

/** Fixed-order locking and mutation boundary for V2 transfer shipments. */
public interface InvTransferShipmentCreationMapper
{
    List<Long> selectTransferDetailIdsForUpdate(
            @Param("transferId") Long transferId);

    InvWarehouseStockMode selectWarehouseModeForUpdate(
            @Param("warehouseId") Long warehouseId);

    List<InvItemFulfillmentPolicy> selectPoliciesForUpdate(
            @Param("itemKeys") List<InvShipmentPlanningItemKey> itemKeys);

    List<InvLockedShipmentBalance> selectBalancesForUpdate(
            @Param("warehouseId") Long warehouseId,
            @Param("itemKeys") List<InvShipmentPlanningItemKey> itemKeys);

    List<Long> selectLotsForUpdate(@Param("lotIds") List<Long> lotIds);

    List<Long> selectLocationsForUpdate(
            @Param("locationIds") List<Long> locationIds);

    List<InvShipmentAllocationCandidate> selectEligibleCandidateFacts(
            @Param("balanceIds") List<Long> balanceIds);

    List<InvShipmentSerialCandidate> selectSerialsForUpdate(
            @Param("balanceIds") List<Long> balanceIds);

    int deductBalance(@Param("balanceId") Long balanceId,
            @Param("version") Long version,
            @Param("quantity") BigDecimal quantity,
            @Param("deductCost") BigDecimal deductCost,
            @Param("updateBy") String updateBy);

    int markSerialShipped(@Param("serialId") Long serialId,
            @Param("balanceId") Long balanceId,
            @Param("warehouseId") Long warehouseId,
            @Param("lotId") Long lotId,
            @Param("locationId") Long locationId,
            @Param("updateBy") String updateBy);

    int insertAllocation(InvTransferShipmentAllocationRecord allocation);

    int batchInsertShipmentSerials(
            @Param("serials") List<InvTransferShipmentSerialRecord> serials);

    int insertStockLedger(InvStockLedgerDetailRecord ledger);
}
