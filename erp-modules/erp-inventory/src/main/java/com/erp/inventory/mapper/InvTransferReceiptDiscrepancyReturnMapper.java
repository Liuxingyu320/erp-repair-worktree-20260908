package com.erp.inventory.mapper;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.InvTransferDetail;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowChildFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnPolicy.PreparedReservation;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnPolicy.Serial;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnSerialFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptGeneratedId;
import com.erp.inventory.domain.transfer.InvTransferReceiptLocationCandidate;
import com.erp.inventory.domain.transfer.InvTransferReceiptTargetBalance;
import com.erp.inventory.domain.transfer.InvTransferReceiptTargetLot;
import com.erp.inventory.domain.transfer.InvTransferReceiptTargetStock;

/** Fixed-order lock and narrow DML owner for return quarantine reservation. */
public interface InvTransferReceiptDiscrepancyReturnMapper
{
    InvTransferReceiptDiscrepancyReturnFact selectSourceForUpdate(
            @Param("caseId") Long caseId,
            @Param("caseVersion") Long caseVersion,
            @Param("adjudicationId") Long adjudicationId,
            @Param("actionId") Long actionId,
            @Param("actionVersion") Long actionVersion);

    InvTransferDetail selectCreatedDraftDetailForUpdate(
            @Param("actionId") Long actionId,
            @Param("childTransferId") Long childTransferId);

    BigDecimal selectDisposedQuantity(
            @Param("receiptAllocationId") Long receiptAllocationId);

    InvTransferReceiptTargetStock selectStockForUpdate(
            @Param("fact") InvTransferReceiptDiscrepancyReturnFact fact);

    InvTransferReceiptTargetLot selectLotForUpdate(
            @Param("fact") InvTransferReceiptDiscrepancyReturnFact fact);

    InvTransferReceiptLocationCandidate selectLocationForUpdate(
            @Param("fact") InvTransferReceiptDiscrepancyReturnFact fact);

    InvTransferReceiptTargetBalance selectBalanceForUpdate(
            @Param("fact") InvTransferReceiptDiscrepancyReturnFact fact);

    List<InvTransferReceiptDiscrepancyReturnSerialFact>
            selectEligibleSerialsForUpdate(
                    @Param("fact")
                    InvTransferReceiptDiscrepancyReturnFact fact);

    int reserveStock(@Param("reservation") PreparedReservation reservation);

    int reserveBalance(
            @Param("reservation") PreparedReservation reservation);

    int reserveSerial(@Param("reservation") PreparedReservation reservation,
            @Param("serial") Serial serial);

    int insertReservation(
            @Param("reservation") PreparedReservation reservation,
            @Param("generatedId") InvTransferReceiptGeneratedId generatedId);

    int insertReservationSerial(
            @Param("reservation") PreparedReservation reservation,
            @Param("serial") Serial serial,
            @Param("reservationId") Long reservationId);

    InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowChildFact
            selectCreatedChildForUpdate(
                    @Param("actionId") Long actionId,
                    @Param("childTransferId") Long childTransferId);
}
