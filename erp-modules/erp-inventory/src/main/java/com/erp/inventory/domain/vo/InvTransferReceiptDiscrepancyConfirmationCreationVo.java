package com.erp.inventory.domain.vo;

/** Result of one server-authored append-only discrepancy confirmation. */
public record InvTransferReceiptDiscrepancyConfirmationCreationVo(
        String confirmationEventId,
        String discrepancyCaseId,
        String partyRole,
        String decision,
        String confirmationState,
        boolean readyForAdjudication,
        boolean replayed,
        String eventCreatedAt,
        String responseAt,
        String dataSource)
{
}
