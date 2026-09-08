package com.erp.inventory.domain.vo;

/** JavaScript-safe result of one atomic expected damaged-return receipt. */
public record InvTransferReceiptDiscrepancyReturnReceiptCreationVo(
        String receiptId,
        String receiptNo,
        String shipmentId,
        String transferId,
        String receiptStatus,
        String returnReceiptPlanVersion,
        String returnedQuantity,
        String shortageQuantity,
        String remainingQuantityAfter,
        String createdAt,
        String dataSource)
{
}
