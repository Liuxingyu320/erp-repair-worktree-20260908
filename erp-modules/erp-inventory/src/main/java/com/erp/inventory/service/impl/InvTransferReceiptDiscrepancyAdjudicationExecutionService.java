package com.erp.inventory.service.impl;

import org.springframework.stereotype.Service;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionCommand;

/** Unwired and deliberately non-transactional action execution entry. */
@Service
public class InvTransferReceiptDiscrepancyAdjudicationExecutionService
{
    private final
            InvTransferReceiptDiscrepancyAdjudicationExecutionWriteGate
                    writeGate;

    public InvTransferReceiptDiscrepancyAdjudicationExecutionService(
            InvTransferReceiptDiscrepancyAdjudicationExecutionWriteGate
                    writeGate)
    {
        this.writeGate = writeGate;
    }

    public void execute(
            InvTransferReceiptDiscrepancyAdjudicationExecutionCommand
                    command)
    {
        writeGate.requireEnabled();
        throw new ServiceException(
                "差异裁决动作持久化与物理效果边界尚未实现");
    }
}
