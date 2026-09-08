package com.erp.inventory.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.InvReceiptBatchDetail;

public interface InvReceiptBatchDetailMapper
{
    List<InvReceiptBatchDetail> selectByBatchId(@Param("batchId") Long batchId);
    List<InvReceiptBatchDetail> selectByBatchIdForUpdate(@Param("batchId") Long batchId);
    int insertInvReceiptBatchDetail(InvReceiptBatchDetail detail);
    int updateInspectionProgress(InvReceiptBatchDetail detail);
}
