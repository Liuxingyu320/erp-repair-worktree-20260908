package com.erp.inventory.domain.vo;

public record InvTransferShipmentReceiptCreationVo(
        String receiptId,
        String receiptNo,
        String shipmentId,
        String transferId,
        String status,
        String receiptPlanVersion,
        String acceptedQuantity,
        String damagedQuantity,
        String shortageQuantity,
        String remainingQuantity,
        String createdTime)
{
}
