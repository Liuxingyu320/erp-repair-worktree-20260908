package com.erp.inventory.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionBoundary;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionLockedAction;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy.PreparedExecution;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionStoredEvent;

/** Narrow lock, event and conditional-state contract for execution owners. */
public interface
        InvTransferReceiptDiscrepancyAdjudicationExecutionMapper
{
    Long selectCaseIdForUpdate(@Param("caseId") Long caseId);

    int countCaseInScope(
            @Param("caseId") Long caseId,
            @Param("scopeDeptIds") List<Long> scopeDeptIds);

    InvTransferReceiptDiscrepancyAdjudicationExecutionBoundary
            selectBoundaryForUpdate(
                    @Param("caseId") Long caseId,
                    @Param("adjudicationId") Long adjudicationId);

    List<InvTransferReceiptDiscrepancyAdjudicationExecutionLockedAction>
            selectActionsForUpdate(
                    @Param("caseId") Long caseId,
                    @Param("adjudicationId") Long adjudicationId);

    InvTransferReceiptDiscrepancyAdjudicationExecutionStoredEvent
            selectEventByRequestIdForUpdate(
                    @Param("requestId") String requestId);

    int insertExecutionEvent(
            @Param("execution") PreparedExecution execution);

    int transitionAction(
            @Param("execution") PreparedExecution execution);

    int transitionAdjudication(
            @Param("execution") PreparedExecution execution);

    int transitionCase(
            @Param("execution") PreparedExecution execution);
}
