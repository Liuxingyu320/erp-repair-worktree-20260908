package com.erp.inventory.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.transfer.InvItemFulfillmentPolicy;
import com.erp.inventory.domain.transfer.InvShipmentAllocationCandidate;
import com.erp.inventory.domain.transfer.InvShipmentPlanningItemKey;
import com.erp.inventory.domain.transfer.InvShipmentSerialCandidate;
import com.erp.inventory.domain.transfer.InvWarehouseStockMode;

/** Read-only facts used to produce transfer shipment suggestions. */
public interface InvTransferShipmentPlanningMapper
{
    InvWarehouseStockMode selectWarehouseMode(
            @Param("warehouseId") Long warehouseId);

    List<InvItemFulfillmentPolicy> selectFulfillmentPolicies(
            @Param("itemKeys") List<InvShipmentPlanningItemKey> itemKeys);

    List<InvShipmentAllocationCandidate> selectEligibleBalances(
            @Param("warehouseId") Long warehouseId,
            @Param("itemKeys") List<InvShipmentPlanningItemKey> itemKeys);

    List<InvShipmentSerialCandidate> selectAvailableSerials(
            @Param("balanceIds") List<Long> balanceIds);
}
