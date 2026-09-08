package com.erp.inventory.service.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.erp.common.core.exception.ServiceException;

/** Independent fail-closed gate for adjudication return shipments. */
@Component
public class InvTransferReceiptDiscrepancyReturnShipmentWriteGate
{
    private final boolean writeEnabled;
    private final boolean receiptBoundaryReady;

    public InvTransferReceiptDiscrepancyReturnShipmentWriteGate(
            @Value("${erp.inventory.transfer-discrepancy-return-shipment-v2.write-enabled:false}")
            boolean writeEnabled,
            @Value("${erp.inventory.transfer-discrepancy-return-shipment-v2.receipt-boundary-ready:false}")
            boolean receiptBoundaryReady)
    {
        this.writeEnabled = writeEnabled;
        this.receiptBoundaryReady = receiptBoundaryReady;
    }

    public void requireEnabled()
    {
        if (!writeEnabled)
        {
            throw new ServiceException("差异退回发货写事务尚未启用");
        }
        if (!receiptBoundaryReady)
        {
            throw new ServiceException("差异退回V2收货边界尚未就绪");
        }
    }
}
