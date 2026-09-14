package com.erp.inventory.mapper;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.InvSalesReturnDetail;

public interface InvSalesReturnDetailMapper
{
    List<InvSalesReturnDetail> selectInvSalesReturnDetailByReturnId(Long returnId);
    List<InvSalesReturnDetail> selectInvSalesReturnDetailByReturnIdForUpdate(Long returnId);
    List<InvSalesReturnDetail> selectReturnedCostFacts(@Param("salesOrderId") Long salesOrderId,
            @Param("salesDetailId") Long salesDetailId, @Param("itemType") String itemType,
            @Param("itemId") Long itemId);
    int insertInvSalesReturnDetail(InvSalesReturnDetail detail);
    int updateInvSalesReturnDetail(InvSalesReturnDetail detail);
    int deleteInvSalesReturnDetailByReturnId(Long returnId);
    int batchInsertInvSalesReturnDetail(List<InvSalesReturnDetail> details);
    BigDecimal sumHistoricalReturnQuantity(@Param("salesOrderId") Long salesOrderId,
            @Param("productId") Long productId, @Param("excludeReturnId") Long excludeReturnId);
    BigDecimal sumHistoricalReturnQuantityByItem(@Param("salesOrderId") Long salesOrderId,
            @Param("itemType") String itemType, @Param("itemId") Long itemId,
            @Param("excludeReturnId") Long excludeReturnId);
    BigDecimal sumHistoricalReturnQuantityBySalesDetailId(@Param("salesOrderId") Long salesOrderId,
            @Param("salesDetailId") Long salesDetailId, @Param("excludeReturnId") Long excludeReturnId);
}
