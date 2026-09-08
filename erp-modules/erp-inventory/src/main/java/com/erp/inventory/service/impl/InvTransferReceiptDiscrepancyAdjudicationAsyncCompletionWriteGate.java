package com.erp.inventory.service.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.erp.common.core.exception.ServiceException;

/** Fail-closed activation gate for authoritative async-workflow completion. */
@Component
public class
        InvTransferReceiptDiscrepancyAdjudicationAsyncCompletionWriteGate
{
    private final boolean writeEnabled;
    private final boolean workflowBoundaryReady;

    public InvTransferReceiptDiscrepancyAdjudicationAsyncCompletionWriteGate(
            @Value("${erp.inventory.transfer-discrepancy-async-completion-v2.write-enabled:false}")
            boolean writeEnabled,
            @Value("${erp.inventory.transfer-discrepancy-async-completion-v2.workflow-boundary-ready:false}")
            boolean workflowBoundaryReady)
    {
        this.writeEnabled = writeEnabled;
        this.workflowBoundaryReady = workflowBoundaryReady;
    }

    public void requireEnabled()
    {
        if (!writeEnabled)
        {
            throw new ServiceException("差异裁决异步完成写事务尚未启用");
        }
        if (!workflowBoundaryReady)
        {
            throw new ServiceException("差异裁决异步完成证据边界尚未就绪");
        }
    }
}
