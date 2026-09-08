package com.erp.inventory.service.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.erp.common.core.exception.ServiceException;

/** Fail-closed activation gate for the unfinished V2 receipt boundary. */
@Component
public class InvTransferShipmentReceiptWriteGate
{
    private final boolean writeEnabled;
    private final boolean persistenceReady;

    public InvTransferShipmentReceiptWriteGate(
            @Value("${erp.inventory.transfer-receipt-v2.write-enabled:false}")
            boolean writeEnabled,
            @Value("${erp.inventory.transfer-receipt-v2.persistence-ready:false}")
            boolean persistenceReady)
    {
        this.writeEnabled = writeEnabled;
        this.persistenceReady = persistenceReady;
    }

    public void requireEnabled()
    {
        if (!writeEnabled)
        {
            throw new ServiceException("明细库存收货写事务尚未启用");
        }
        if (!persistenceReady)
        {
            throw new ServiceException("明细库存收货持久化边界尚未就绪");
        }
    }
}
