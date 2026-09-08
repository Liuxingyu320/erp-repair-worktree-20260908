package com.erp.inventory.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.InvPurchaseOrder;

public interface InvPurchaseOrderMapper
{
    InvPurchaseOrder selectInvPurchaseOrderById(Long orderId);
    InvPurchaseOrder selectInvPurchaseOrderByIdForUpdate(Long orderId);
    List<InvPurchaseOrder> selectInvPurchaseOrderList(InvPurchaseOrder order);
    List<InvPurchaseOrder> selectReturnablePurchaseOrderList(InvPurchaseOrder order);
    List<InvPurchaseOrder> selectMyInvPurchaseOrderList(InvPurchaseOrder order);
    int countBySupplierNameAndShop(@Param("supplierName") String supplierName, @Param("shopDeptId") Long shopDeptId);
    int insertInvPurchaseOrder(InvPurchaseOrder order);
    int updateInvPurchaseOrder(InvPurchaseOrder order);
    int updatePurchaseContent(InvPurchaseOrder order);
    int transitionPurchaseStatus(@Param("orderId") Long orderId,
            @Param("expectedStatus") String expectedStatus,
            @Param("status") String status,
            @Param("qcStatus") String qcStatus,
            @Param("updateBy") String updateBy);
    int deleteInvPurchaseOrderByIds(Long[] orderIds);
}
