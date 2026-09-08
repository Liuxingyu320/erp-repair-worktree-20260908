package com.erp.inventory.service.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.erp.common.core.exception.ServiceException;

/** Fail-closed activation gate for adjudication action execution. */
@Component
public class
        InvTransferReceiptDiscrepancyAdjudicationExecutionWriteGate
{
    private final boolean writeEnabled;
    private final boolean effectBoundaryReady;

    public InvTransferReceiptDiscrepancyAdjudicationExecutionWriteGate(
            @Value("${erp.inventory.transfer-discrepancy-execution-v2.write-enabled:false}")
            boolean writeEnabled,
            @Value("${erp.inventory.transfer-discrepancy-execution-v2.effect-boundary-ready:false}")
            boolean effectBoundaryReady)
    {
        this.writeEnabled = writeEnabled;
        this.effectBoundaryReady = effectBoundaryReady;
    }

    public void requireEnabled()
    {
        if (!writeEnabled)
        {
            throw new ServiceException("差异裁决动作执行写事务尚未启用");
        }
        if (!effectBoundaryReady)
        {
            throw new ServiceException("差异裁决动作物理效果边界尚未就绪");
        }
    }
}
