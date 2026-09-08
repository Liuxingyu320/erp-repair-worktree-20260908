package com.erp.inventory.domain.vo;

/** Result of one server-authored discrepancy adjudication plan. */
public record InvTransferReceiptDiscrepancyAdjudicationCreationVo(
        String adjudicationId,
        String discrepancyCaseId,
        String planStatus,
        int actionCount,
        boolean replayed,
        String planCreatedAt,
        String responseAt,
        String dataSource)
{
}
