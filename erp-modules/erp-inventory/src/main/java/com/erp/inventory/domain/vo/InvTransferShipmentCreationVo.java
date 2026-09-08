package com.erp.inventory.domain.vo;

public record InvTransferShipmentCreationVo(
        String shipmentId,
        String shipmentNo,
        String transferId,
        String status,
        String planVersion,
        String sealedRevisionId,
        String createdTime)
{
}
