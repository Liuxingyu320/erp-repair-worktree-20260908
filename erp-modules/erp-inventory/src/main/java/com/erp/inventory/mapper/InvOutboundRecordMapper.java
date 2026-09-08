package com.erp.inventory.mapper;

import java.util.List;
import com.erp.inventory.domain.InvOutboundRecord;

public interface InvOutboundRecordMapper
{
    List<InvOutboundRecord> selectInvOutboundRecordByOrderId(Long orderId);
    int insertInvOutboundRecord(InvOutboundRecord record);
}
