package com.erp.inventory.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnReservationLifecycleFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnReservationLifecyclePolicy.PreparedConsumption;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnReservationLifecyclePolicy.PreparedRelease;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnReservationLifecyclePolicy.SerialTransition;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnReservationSerialLifecycleFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptGeneratedId;
import com.erp.inventory.domain.transfer.InvTransferReceiptTargetBalance;
import com.erp.inventory.domain.transfer.InvTransferReceiptTargetStock;

/** Fixed-lock lifecycle persistence for return quarantine reservations. */
public interface
        InvTransferReceiptDiscrepancyReturnReservationLifecycleMapper
{
    InvTransferReceiptDiscrepancyReturnReservationLifecycleFact
            selectReservationForUpdate(
                    @Param("childTransferId") Long childTransferId);

    InvTransferReceiptTargetStock selectStockForUpdate(
            @Param("fact")
            InvTransferReceiptDiscrepancyReturnReservationLifecycleFact fact);

    InvTransferReceiptTargetBalance selectBalanceForUpdate(
            @Param("fact")
            InvTransferReceiptDiscrepancyReturnReservationLifecycleFact fact);

    List<InvTransferReceiptDiscrepancyReturnReservationSerialLifecycleFact>
            selectSerialsForUpdate(
                    @Param("fact")
                    InvTransferReceiptDiscrepancyReturnReservationLifecycleFact
                            fact);

    int consumeStock(@Param("prepared") PreparedConsumption prepared);

    int consumeBalance(@Param("prepared") PreparedConsumption prepared);

    int consumePhysicalSerial(
            @Param("prepared") PreparedConsumption prepared,
            @Param("serial") SerialTransition serial);

    int insertConsumption(
            @Param("prepared") PreparedConsumption prepared,
            @Param("generatedId") InvTransferReceiptGeneratedId generatedId);

    int consumeSerialBinding(
            @Param("prepared") PreparedConsumption prepared,
            @Param("serial") SerialTransition serial,
            @Param("consumptionId") Long consumptionId);

    int updateConsumedReservation(
            @Param("prepared") PreparedConsumption prepared);

    int releaseStock(@Param("prepared") PreparedRelease prepared);

    int releaseBalance(@Param("prepared") PreparedRelease prepared);

    int releasePhysicalSerial(@Param("prepared") PreparedRelease prepared,
            @Param("serial") SerialTransition serial);

    int insertRelease(@Param("prepared") PreparedRelease prepared,
            @Param("generatedId") InvTransferReceiptGeneratedId generatedId);

    int releaseSerialBinding(@Param("prepared") PreparedRelease prepared,
            @Param("serial") SerialTransition serial,
            @Param("releaseId") Long releaseId);

    int updateReleasedReservation(
            @Param("prepared") PreparedRelease prepared);
}
