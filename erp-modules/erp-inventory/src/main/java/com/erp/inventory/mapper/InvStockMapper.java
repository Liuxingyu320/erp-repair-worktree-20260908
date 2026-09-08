package com.erp.inventory.mapper;

import java.util.List;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.InvStock;
import com.erp.inventory.domain.vo.InvStockSummary;

public interface InvStockMapper
{
    List<InvStock> selectInvStockList(InvStock stock);
    InvStockSummary selectInvStockSummary(InvStock stock);
    InvStock selectInvStockById(Long stockId);
    InvStock selectInvStockByIdForUpdate(Long stockId);
    InvStock selectInvStockByProductAndShop(@Param("productId") Long productId, @Param("shopDeptId") Long shopDeptId);
    InvStock selectInvStockByProductAndShopForUpdate(@Param("productId") Long productId, @Param("shopDeptId") Long shopDeptId);
    InvStock selectInvStockByProductShopWarehouse(@Param("productId") Long productId, @Param("shopDeptId") Long shopDeptId, @Param("warehouseId") Long warehouseId);
    InvStock selectInvStockByProductShopWarehouseForUpdate(@Param("productId") Long productId, @Param("shopDeptId") Long shopDeptId, @Param("warehouseId") Long warehouseId);
    InvStock selectInvStockByItemShopWarehouse(@Param("itemType") String itemType, @Param("itemId") Long itemId, @Param("shopDeptId") Long shopDeptId, @Param("warehouseId") Long warehouseId);
    InvStock selectInvStockByItemShopWarehouseForUpdate(@Param("itemType") String itemType, @Param("itemId") Long itemId, @Param("shopDeptId") Long shopDeptId, @Param("warehouseId") Long warehouseId);
    int insertInvStock(InvStock stock);
    int updateInvStock(InvStock stock);
    int addInvStock(@Param("stockId") Long stockId, @Param("version") Long version, @Param("quantity") BigDecimal quantity, @Param("updateBy") String updateBy);
    int addInvStockWithCost(@Param("stockId") Long stockId, @Param("version") Long version, @Param("quantity") BigDecimal quantity, @Param("incomingCost") BigDecimal incomingCost, @Param("updateBy") String updateBy);
    int deductInvStock(@Param("stockId") Long stockId, @Param("version") Long version, @Param("quantity") BigDecimal quantity, @Param("updateBy") String updateBy);
    int deductInvStockWithCost(@Param("stockId") Long stockId, @Param("version") Long version, @Param("quantity") BigDecimal quantity, @Param("deductCost") BigDecimal deductCost, @Param("updateBy") String updateBy);
}
