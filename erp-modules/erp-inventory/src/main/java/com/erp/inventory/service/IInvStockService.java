package com.erp.inventory.service;

import java.util.List;
import com.erp.inventory.domain.InvStock;
import com.erp.inventory.domain.InvStockLog;
import com.erp.inventory.domain.dto.InvExistingStockAdjustRequest;
import com.erp.inventory.domain.dto.InvStockAdjustRequest;
import com.erp.inventory.domain.vo.InvStockSummary;

public interface IInvStockService
{
    List<InvStock> selectStockList(InvStock stock, Long selectedShopDeptId);
    InvStockSummary selectStockSummary(InvStock stock, Long selectedShopDeptId);
    InvStock selectStockById(Long stockId, Long selectedShopDeptId);
    InvStock adjustExistingStock(Long stockId, InvExistingStockAdjustRequest request, Long selectedShopDeptId);
    void adjustStock(InvStockAdjustRequest request, Long selectedShopDeptId);
    void adjustStock(Long productId, Long shopDeptId, Long warehouseId, java.math.BigDecimal adjustQuantity, String reason, Long selectedShopDeptId);
    List<InvStockLog> selectStockLogList(InvStockLog stockLog, Long selectedShopDeptId);
}
