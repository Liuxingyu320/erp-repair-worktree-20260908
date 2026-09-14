package com.erp.inventory.mapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.InvStockCheckDetail;

public interface InvStockCheckDetailMapper
{
    List<InvStockCheckDetail> selectInvStockCheckDetailByCheckId(Long checkId);
    List<InvStockCheckDetail> selectInvStockCheckDetailByCheckIdForUpdate(Long checkId);
    int batchInsertInvStockCheckDetail(List<InvStockCheckDetail> details);
    int updateInvStockCheckDetail(InvStockCheckDetail detail);
    int resetSnapshot(@Param("detailId") Long detailId,
            @Param("bookQty") BigDecimal bookQty, @Param("costPrice") BigDecimal costPrice);
    int refreshSnapshotCost(@Param("detailId") Long detailId, @Param("costPrice") BigDecimal costPrice);
    int deleteInvStockCheckDetailByCheckId(Long checkId);
    List<Map<String, Object>> selectStockForCheck(@Param("shopDeptId") Long shopDeptId, @Param("warehouseId") Long warehouseId);
}
