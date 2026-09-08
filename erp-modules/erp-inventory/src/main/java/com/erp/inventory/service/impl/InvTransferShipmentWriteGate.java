package com.erp.inventory.service.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.erp.common.core.exception.ServiceException;

/** Fail-closed activation gate for the detail-stock shipment transaction. */
@Component
public class InvTransferShipmentWriteGate
{
    private final boolean writeEnabled;
    private final boolean receiptBoundaryReady;

    public InvTransferShipmentWriteGate(
            @Value("${erp.inventory.transfer-shipment-v2.write-enabled:false}")
            boolean writeEnabled,
            @Value("${erp.inventory.transfer-shipment-v2.receipt-boundary-ready:false}")
            boolean receiptBoundaryReady)
    {
        this.writeEnabled = writeEnabled;
        this.receiptBoundaryReady = receiptBoundaryReady;
    }

    public void requireEnabled()
    {
        if (!writeEnabled)
        {
            throw new ServiceException("明细库存发货写事务尚未启用");
        }
        if (!receiptBoundaryReady)
        {
            throw new ServiceException("明细库存收货事务尚未就绪，已拒绝创建发货批次");
        }
    }
}
