package com.erp.inventory.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.InvSalesOrder;

public interface InvSalesOrderMapper
{
    InvSalesOrder selectInvSalesOrderById(Long orderId);
    InvSalesOrder selectInvSalesOrderByIdForUpdate(Long orderId);
    List<InvSalesOrder> selectInvSalesOrderList(InvSalesOrder order);
    List<InvSalesOrder> selectMyInvSalesOrderList(InvSalesOrder order);
    int countByCustomerNameAndShop(@Param("customerName") String customerName, @Param("shopDeptId") Long shopDeptId);
    int insertInvSalesOrder(InvSalesOrder order);
    int updateInvSalesOrder(InvSalesOrder order);
    int deleteInvSalesOrderByIds(Long[] orderIds);
}
