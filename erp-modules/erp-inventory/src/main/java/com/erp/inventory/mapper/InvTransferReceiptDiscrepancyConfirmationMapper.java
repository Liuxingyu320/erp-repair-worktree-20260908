package com.erp.inventory.mapper;

import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyConfirmationPolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyConfirmationStoredEvent;
import com.erp.inventory.domain.transfer.InvTransferReceiptGeneratedId;

/** Narrow lock and append boundary for one V2 confirmation transaction. */
public interface InvTransferReceiptDiscrepancyConfirmationMapper
{
    Long selectCaseIdForUpdate(@Param("caseId") Long caseId);

    InvTransferReceiptDiscrepancyConfirmationStoredEvent
            selectByRequestIdForUpdate(
                    @Param("requestId") String requestId);

    int insertConfirmationEvent(
            @Param("confirmation") InvTransferReceiptDiscrepancyConfirmationPolicy
                    .Prepared confirmation,
            @Param("generatedId") InvTransferReceiptGeneratedId generatedId);
}
