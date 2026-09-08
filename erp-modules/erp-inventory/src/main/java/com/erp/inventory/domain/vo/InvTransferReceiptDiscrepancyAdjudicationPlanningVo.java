package com.erp.inventory.domain.vo;

import java.util.List;

/** Browser-safe V2 discrepancy and independent adjudication projection. */
public record InvTransferReceiptDiscrepancyAdjudicationPlanningVo(
        String discrepancyCaseId,
        String receiptId,
        String receiptNo,
        String shipmentId,
        String shipmentNo,
        String transferId,
        String orderNo,
        String itemType,
        String itemId,
        String itemCode,
        String itemName,
        String unit,
        String discrepancyType,
        String discrepancyQuantity,
        String discrepancyAmount,
        String discrepancyNote,
        String attachmentRefs,
        String caseStatus,
        String caseVersion,
        Party sourceParty,
        Party targetParty,
        String workflowState,
        boolean readyForAdjudication,
        Plan adjudicationPlan,
        String createdAt,
        String generatedAt,
        String dataSource)
{
    public record Party(
            String role,
            String organizationId,
            String organizationName,
            Confirmation latestConfirmation)
    {
    }

    public record Confirmation(
            String confirmationEventId,
            String decision,
            String note,
            String operatorName,
            String confirmedAt,
            boolean matchesBasis)
    {
    }

    public record Plan(
            String adjudicationId,
            String planStatus,
            String caseVersionBefore,
            String caseVersionAfter,
            String adjudicationNote,
            String evidenceRefs,
            String adjudicatorName,
            String plannedAt,
            List<Action> actions)
    {
        public Plan
        {
            actions = List.copyOf(actions);
        }
    }

    public record Action(
            String adjudicationActionId,
            int sequence,
            String actionType,
            String coverageKind,
            String quantity,
            String amount,
            String responsibleParty,
            String note,
            String executionStatus)
    {
    }
}
