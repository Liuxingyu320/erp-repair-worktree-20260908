package com.erp.inventory.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAtomicLedgerPolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAtomicLedgerPolicy.Prepared;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy.PreparedExecution;
import com.erp.inventory.mapper.InvTransferReceiptDiscrepancyAdjudicationAtomicLedgerMapper;

/**
 * Ledger-only physical effect owner reserved for the future execution
 * transaction. It has no runtime caller while the entry remains hard closed.
 */
@Service
public class
        InvTransferReceiptDiscrepancyAdjudicationAtomicLedgerEffectOwner
{
    private final
            InvTransferReceiptDiscrepancyAdjudicationAtomicLedgerMapper
                    mapper;

    public InvTransferReceiptDiscrepancyAdjudicationAtomicLedgerEffectOwner(
            InvTransferReceiptDiscrepancyAdjudicationAtomicLedgerMapper
                    mapper)
    {
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class)
    public void apply(PreparedExecution execution)
    {
        Prepared effect =
                InvTransferReceiptDiscrepancyAdjudicationAtomicLedgerPolicy
                        .prepare(execution);
        int rows = switch (effect.ledgerKind())
        {
            case InvTransferReceiptDiscrepancyAdjudicationAtomicLedgerPolicy
                    .LEDGER_RESPONSIBILITY ->
                    mapper.insertResponsibilityLedger(effect);
            case InvTransferReceiptDiscrepancyAdjudicationAtomicLedgerPolicy
                    .LEDGER_SHORTAGE_LOSS ->
                    mapper.insertShortageLossLedger(effect);
            default -> throw new ServiceException(
                    "差异裁决原子台账类型无效");
        };
        if (rows != 1)
        {
            throw new ServiceException("差异裁决原子台账写入冲突");
        }
    }
}
