package com.erp.inventory.mapper;

import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowChildFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReshipFact;

/** Read-only lock contract for the unwired reship effect owner. */
public interface InvTransferReceiptDiscrepancyReshipMapper
{
    InvTransferReceiptDiscrepancyReshipFact selectSourceForUpdate(
            @Param("caseId") Long caseId,
            @Param("caseVersion") Long caseVersion,
            @Param("adjudicationId") Long adjudicationId,
            @Param("actionId") Long actionId,
            @Param("actionVersion") Long actionVersion);

    InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowChildFact
            selectCreatedChildForUpdate(
                    @Param("actionId") Long actionId,
                    @Param("childTransferId") Long childTransferId);
}
