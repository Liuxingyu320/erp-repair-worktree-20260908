package com.erp.inventory.domain.vo;

/** JavaScript-safe result of one atomic quarantine return shipment. */
public record InvTransferReceiptDiscrepancyReturnShipmentCreationVo(
        String shipmentId,
        String shipmentDetailId,
        String shipmentNo,
        String transferId,
        String transferDetailId,
        String transferStatus,
        String quantity,
        String amount,
        String planVersion,
        String reservationId,
        String consumptionId,
        String reservationStatus,
        String reservationVersion,
        String sealedRevisionId,
        String createdAt,
        String dataSource)
{
}
