package com.erp.inventory.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.InvTransferShipment;

public interface InvTransferShipmentMapper
{
    InvTransferShipment selectById(Long shipmentId);
    InvTransferShipment selectByIdForUpdate(Long shipmentId);
    List<InvTransferShipment> selectByTransferId(Long transferId);
    List<InvTransferShipment> selectByIds(@Param("shipmentIds") List<Long> shipmentIds);
    int insertShipment(InvTransferShipment shipment);
    int updateShipment(InvTransferShipment shipment);
}
