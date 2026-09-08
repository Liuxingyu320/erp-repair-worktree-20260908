package com.erp.inventory.mapper;

import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnShipmentCreationPolicy.PreparedCreation;

/** Conditional child progress owned only by return shipment creation. */
public interface
        InvTransferReceiptDiscrepancyReturnShipmentCreationMapper
{
    int advanceChildDetail(
            @Param("prepared") PreparedCreation prepared);

    int advanceChildOrder(
            @Param("prepared") PreparedCreation prepared);
}
