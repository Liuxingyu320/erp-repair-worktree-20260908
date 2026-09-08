package com.erp.inventory.mapper;

import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAtomicLedgerPolicy.Prepared;

/** Append-only boundary for the two ledger-only adjudication effects. */
public interface
        InvTransferReceiptDiscrepancyAdjudicationAtomicLedgerMapper
{
    int insertResponsibilityLedger(@Param("effect") Prepared effect);

    int insertShortageLossLedger(@Param("effect") Prepared effect);
}
