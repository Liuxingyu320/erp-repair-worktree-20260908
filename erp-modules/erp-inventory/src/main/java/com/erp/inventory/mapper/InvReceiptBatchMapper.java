package com.erp.inventory.mapper;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.InvReceiptBatch;

public interface InvReceiptBatchMapper
{
    InvReceiptBatch selectByIdForUpdate(@Param("batchId") Long batchId);
    List<InvReceiptBatch> selectByOrderId(@Param("orderId") Long orderId);
    List<InvReceiptBatch> selectPendingByOrderId(@Param("orderId") Long orderId);
    int countPendingByOrderId(@Param("orderId") Long orderId);
    int insertInvReceiptBatch(InvReceiptBatch batch);
    int updateProgress(@Param("batchId") Long batchId,
            @Param("pendingQuantity") BigDecimal pendingQuantity,
            @Param("status") String status,
            @Param("updateBy") String updateBy);
}
