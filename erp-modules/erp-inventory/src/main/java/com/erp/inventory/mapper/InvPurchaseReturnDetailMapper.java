package com.erp.inventory.mapper;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.InvPurchaseReturnDetail;

public interface InvPurchaseReturnDetailMapper
{
    List<InvPurchaseReturnDetail> selectInvPurchaseReturnDetailByReturnId(Long returnId);
    List<InvPurchaseReturnDetail> selectInvPurchaseReturnDetailByReturnIdForUpdate(Long returnId);
    int insertInvPurchaseReturnDetail(InvPurchaseReturnDetail detail);
    int updateInvPurchaseReturnDetail(InvPurchaseReturnDetail detail);
    int deleteInvPurchaseReturnDetailByReturnId(Long returnId);
    int batchInsertInvPurchaseReturnDetail(List<InvPurchaseReturnDetail> details);
    BigDecimal sumHistoricalReturnQuantity(@Param("purchaseOrderId") Long purchaseOrderId,
            @Param("productId") Long productId, @Param("excludeReturnId") Long excludeReturnId);
    BigDecimal sumHistoricalReturnQuantityByPurchaseDetailId(@Param("purchaseOrderId") Long purchaseOrderId,
            @Param("purchaseDetailId") Long purchaseDetailId, @Param("excludeReturnId") Long excludeReturnId);
}
