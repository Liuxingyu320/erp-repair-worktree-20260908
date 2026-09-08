package com.erp.inventory.mapper;

import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowChildFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowLink;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy.PreparedLink;

/** Narrow persistence contract for async dispatch and completion owners. */
public interface
        InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowMapper
{
    InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowLink
            selectLinkForUpdate(@Param("actionId") Long actionId);

    int insertLink(@Param("link") PreparedLink link);

    InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowChildFact
            selectChildForUpdate(
                    @Param("actionId") Long actionId,
                    @Param("childTransferId") Long childTransferId);
}
