package com.erp.inventory.domain.transfer;

/** Untrusted command envelope for a future adjudication action execution. */
public record InvTransferReceiptDiscrepancyAdjudicationExecutionCommand(
        String requestId,
        Long caseId,
        Long caseVersion,
        Long adjudicationId,
        Long actionId,
        Long executionVersion,
        String command,
        String effectReference)
{
}
