package com.erp.inventory.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationPolicy.PreparedAdjudication;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReadFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyStoredAdjudication;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyStoredAdjudicationAction;
import com.erp.inventory.domain.transfer.InvTransferReceiptGeneratedId;

/** Narrow lock, append and state-transition boundary for one adjudication. */
public interface InvTransferReceiptDiscrepancyAdjudicationMapper
{
    InvTransferReceiptDiscrepancyReadFact selectCaseForUpdate(
            @Param("caseId") Long caseId,
            @Param("scopeDeptIds") List<Long> scopeDeptIds);

    InvTransferReceiptDiscrepancyStoredAdjudication
            selectByRequestIdForUpdate(
                    @Param("requestId") String requestId);

    List<InvTransferReceiptDiscrepancyStoredAdjudicationAction>
            selectActionsByAdjudicationId(
                    @Param("adjudicationId") Long adjudicationId);

    int insertAdjudication(
            @Param("adjudication") PreparedAdjudication adjudication,
            @Param("generatedId") InvTransferReceiptGeneratedId generatedId);

    int insertActions(@Param("adjudicationId") Long adjudicationId,
            @Param("adjudication") PreparedAdjudication adjudication);

    int transitionCaseToPlanned(
            @Param("adjudication") PreparedAdjudication adjudication);
}
