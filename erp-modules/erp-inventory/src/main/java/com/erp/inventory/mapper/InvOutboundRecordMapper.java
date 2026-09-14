package com.erp.inventory.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.InvOutboundRecord;

public interface InvOutboundRecordMapper
{
    List<InvOutboundRecord> selectInvOutboundRecordByOrderId(Long orderId);
    List<InvOutboundRecord> selectInvOutboundRecordBySalesDetailId(@Param("salesOrderId") Long salesOrderId,
            @Param("salesDetailId") Long salesDetailId);
    int insertInvOutboundRecord(InvOutboundRecord record);
}
