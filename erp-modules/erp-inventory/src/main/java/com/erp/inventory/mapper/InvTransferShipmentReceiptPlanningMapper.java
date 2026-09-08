package com.erp.inventory.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.transfer.InvTransferReceiptLocationCandidate;
import com.erp.inventory.domain.transfer.InvTransferReceiptPlanningAllocationFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptPlanningSerialFact;
import com.erp.inventory.domain.transfer.InvWarehouseStockMode;

/** Read-only facts used to compose V2 transfer receipt boundaries. */
public interface InvTransferShipmentReceiptPlanningMapper
{
    InvWarehouseStockMode selectTargetWarehouseMode(
            @Param("warehouseId") Long warehouseId);

    List<InvTransferReceiptPlanningAllocationFact> selectAllocations(
            @Param("shipmentId") Long shipmentId);

    List<InvTransferReceiptPlanningSerialFact> selectShipmentSerials(
            @Param("shipmentId") Long shipmentId);

    List<InvTransferReceiptLocationCandidate> selectTargetLocations(
            @Param("warehouseId") Long warehouseId);
}
