package com.erp.inventory.mapper;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyDamageWriteOffFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyDamageWriteOffPolicy.Prepared;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyDamageWriteOffPolicy.Serial;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyDamageWriteOffSerialFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptLocationCandidate;
import com.erp.inventory.domain.transfer.InvTransferReceiptTargetBalance;
import com.erp.inventory.domain.transfer.InvTransferReceiptTargetLot;
import com.erp.inventory.domain.transfer.InvTransferReceiptTargetStock;

/** Fixed-order lock and narrow DML boundary for damaged-stock write-off. */
public interface InvTransferReceiptDiscrepancyDamageWriteOffMapper
{
    InvTransferReceiptDiscrepancyDamageWriteOffFact selectFactForUpdate(
            @Param("caseId") Long caseId);

    BigDecimal selectWrittenOffQuantity(
            @Param("receiptAllocationId") Long receiptAllocationId);

    InvTransferReceiptTargetStock selectStockForUpdate(
            @Param("fact")
            InvTransferReceiptDiscrepancyDamageWriteOffFact fact);

    InvTransferReceiptTargetLot selectLotForUpdate(
            @Param("fact")
            InvTransferReceiptDiscrepancyDamageWriteOffFact fact);

    InvTransferReceiptLocationCandidate selectLocationForUpdate(
            @Param("fact")
            InvTransferReceiptDiscrepancyDamageWriteOffFact fact);

    InvTransferReceiptTargetBalance selectBalanceForUpdate(
            @Param("fact")
            InvTransferReceiptDiscrepancyDamageWriteOffFact fact);

    List<InvTransferReceiptDiscrepancyDamageWriteOffSerialFact>
            selectEligibleSerialsForUpdate(
                    @Param("fact")
                    InvTransferReceiptDiscrepancyDamageWriteOffFact fact);

    int decrementStock(@Param("effect") Prepared effect);

    int decrementBalance(@Param("effect") Prepared effect);

    int scrapSerial(@Param("effect") Prepared effect,
            @Param("serial") Serial serial);

    int insertStockLedger(@Param("effect") Prepared effect);

    int insertDamageLossLedger(@Param("effect") Prepared effect);

    int insertDamageLossSerial(@Param("effect") Prepared effect,
            @Param("serial") Serial serial);
}
