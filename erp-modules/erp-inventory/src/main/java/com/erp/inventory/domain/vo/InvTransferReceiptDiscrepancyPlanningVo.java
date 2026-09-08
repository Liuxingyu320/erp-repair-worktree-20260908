package com.erp.inventory.domain.vo;

/** Browser-safe V2 discrepancy fact and bilateral confirmation projection. */
public record InvTransferReceiptDiscrepancyPlanningVo(
        String discrepancyCaseId,
        String receiptId,
        String receiptNo,
        String shipmentId,
        String shipmentNo,
        String transferId,
        String orderNo,
        String shipmentAllocationId,
        String shipmentDetailId,
        String transferDetailId,
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
        String factFingerprint,
        String caseStatus,
        String caseVersion,
        Party sourceParty,
        Party targetParty,
        String currentPartyRole,
        String confirmationState,
        boolean readyForAdjudication,
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
            boolean matchesCurrentFact)
    {
    }
}
