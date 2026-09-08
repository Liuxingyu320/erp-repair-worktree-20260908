package com.erp.inventory.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.InvInboundRecord;

public interface InvInboundRecordMapper
{
    List<InvInboundRecord> selectInvInboundRecordByOrderId(@Param("orderId") Long orderId);
    List<InvInboundRecord> selectPendingInvInboundRecordByOrderId(@Param("orderId") Long orderId);
    InvInboundRecord selectByBatchDetailIdForUpdate(@Param("batchDetailId") Long batchDetailId);
    int insertInvInboundRecord(InvInboundRecord record);
    int updateInvInboundRecord(InvInboundRecord record);
    int deleteByOrderId(@Param("orderId") Long orderId);
    int deleteRejectedByOrderId(@Param("orderId") Long orderId);
}
