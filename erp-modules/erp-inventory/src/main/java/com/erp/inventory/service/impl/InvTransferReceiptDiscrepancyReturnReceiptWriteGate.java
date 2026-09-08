package com.erp.inventory.service.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.erp.common.core.exception.ServiceException;

/** Independent fail-closed gate for expected damaged-return receipts. */
@Component
public class InvTransferReceiptDiscrepancyReturnReceiptWriteGate
{
    private final boolean writeEnabled;
    private final boolean lifecycleBoundaryReady;

    public InvTransferReceiptDiscrepancyReturnReceiptWriteGate(
            @Value("${erp.inventory.transfer-discrepancy-return-receipt-v2.write-enabled:false}")
            boolean writeEnabled,
            @Value("${erp.inventory.transfer-discrepancy-return-receipt-v2.lifecycle-boundary-ready:false}")
            boolean lifecycleBoundaryReady)
    {
        this.writeEnabled = writeEnabled;
        this.lifecycleBoundaryReady = lifecycleBoundaryReady;
    }

    public void requireEnabled()
    {
        if (!writeEnabled)
        {
            throw new ServiceException("差异退回收货写事务尚未启用");
        }
        if (!lifecycleBoundaryReady)
        {
            throw new ServiceException("差异退回收货生命周期边界尚未就绪");
        }
    }
}
