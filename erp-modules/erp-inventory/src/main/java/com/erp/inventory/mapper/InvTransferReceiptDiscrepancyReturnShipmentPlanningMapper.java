package com.erp.inventory.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnReservationLifecycleFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnReservationSerialLifecycleFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptTargetBalance;
import com.erp.inventory.domain.transfer.InvTransferReceiptTargetStock;

/** Read-only facts for a fixed adjudication return shipment plan. */
public interface
        InvTransferReceiptDiscrepancyReturnShipmentPlanningMapper
{
    InvTransferReceiptDiscrepancyReturnReservationLifecycleFact
            selectReservation(
                    @Param("childTransferId") Long childTransferId);

    InvTransferReceiptTargetStock selectStock(
            @Param("fact")
            InvTransferReceiptDiscrepancyReturnReservationLifecycleFact fact);

    InvTransferReceiptTargetBalance selectBalance(
            @Param("fact")
            InvTransferReceiptDiscrepancyReturnReservationLifecycleFact fact);

    List<InvTransferReceiptDiscrepancyReturnReservationSerialLifecycleFact>
            selectActiveSerials(
                    @Param("fact")
                    InvTransferReceiptDiscrepancyReturnReservationLifecycleFact
                            fact);
}
