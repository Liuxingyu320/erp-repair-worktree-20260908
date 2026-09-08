package com.erp.inventory.mapper;

import java.util.List;
import com.erp.inventory.domain.InvTransferShipmentDetail;

public interface InvTransferShipmentDetailMapper
{
    List<InvTransferShipmentDetail> selectByShipmentId(Long shipmentId);
    List<InvTransferShipmentDetail> selectByTransferId(Long transferId);
    int insertShipmentDetail(InvTransferShipmentDetail shipmentDetail);
    int batchInsertShipmentDetail(List<InvTransferShipmentDetail> shipmentDetails);
    int updateReceivedQuantity(InvTransferShipmentDetail detail);
}
