package com.erp.inventory.service;

import java.util.List;
import com.erp.inventory.domain.InvSalesDetail;
import com.erp.inventory.domain.InvSalesOrder;

public interface IInvSalesService
{
    InvSalesOrder saveDraft(InvSalesOrder order, List<InvSalesDetail> details, Long selectedShopDeptId);
    InvSalesOrder submitSales(InvSalesOrder order, List<InvSalesDetail> details, Long selectedShopDeptId);
    InvSalesOrder getSalesDetail(Long orderId, Long selectedShopDeptId);
    List<InvSalesOrder> selectSalesList(InvSalesOrder order, Long selectedShopDeptId);
    List<InvSalesOrder> selectMySales(InvSalesOrder order, Long selectedShopDeptId);
    void cancelSales(Long orderId, Long selectedShopDeptId);
}
