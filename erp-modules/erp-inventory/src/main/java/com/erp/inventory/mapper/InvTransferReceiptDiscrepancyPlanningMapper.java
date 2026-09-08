package com.erp.inventory.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReadFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyStoredAdjudication;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyStoredAdjudicationAction;

/** Strictly organization-scoped read boundary for V2 discrepancy facts. */
public interface InvTransferReceiptDiscrepancyPlanningMapper
{
    InvTransferReceiptDiscrepancyReadFact selectCase(
            @Param("caseId") Long caseId,
            @Param("selectedDeptId") Long selectedDeptId);

    InvTransferReceiptDiscrepancyReadFact selectCaseForAdjudication(
            @Param("caseId") Long caseId,
            @Param("scopeDeptIds") List<Long> scopeDeptIds);

    List<InvTransferReceiptDiscrepancyStoredAdjudication>
            selectAdjudicationsByCaseId(@Param("caseId") Long caseId);

    List<InvTransferReceiptDiscrepancyStoredAdjudicationAction>
            selectAdjudicationActionsByCaseId(
                    @Param("caseId") Long caseId);
}
