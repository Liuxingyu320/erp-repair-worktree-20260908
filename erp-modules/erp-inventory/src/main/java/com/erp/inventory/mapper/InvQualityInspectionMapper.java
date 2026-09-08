package com.erp.inventory.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.InvQualityInspection;

public interface InvQualityInspectionMapper
{
    int insertInvQualityInspection(InvQualityInspection inspection);
    List<InvQualityInspection> selectByReceiptBatchId(@Param("receiptBatchId") Long receiptBatchId);
}
