package com.erp.inventory.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface InvSalesWarehouseRepairMapper
{
    List<Long> selectOutboundIdsForUpdate(Long orderId);
    int fillMissingWarehouse(@Param("orderId") Long orderId, @Param("detailId") Long detailId,
            @Param("warehouseId") Long warehouseId);
    int insertAudit(@Param("orderId") Long orderId, @Param("detailId") Long detailId,
            @Param("warehouseId") Long warehouseId, @Param("expectedVersion") Long expectedVersion,
            @Param("noticeId") Long noticeId, @Param("actorId") Long actorId,
            @Param("actorName") String actorName, @Param("shopDeptId") Long shopDeptId);
}
