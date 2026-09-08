package com.erp.inventory.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.InvPurchaseReturn;

public interface InvPurchaseReturnMapper
{
    InvPurchaseReturn selectInvPurchaseReturnById(Long returnId);
    List<InvPurchaseReturn> selectInvPurchaseReturnList(InvPurchaseReturn purchaseReturn);
    List<InvPurchaseReturn> selectMyInvPurchaseReturnList(InvPurchaseReturn purchaseReturn);
    int countBySupplierNameAndShop(@Param("supplierName") String supplierName, @Param("shopDeptId") Long shopDeptId);
    int insertInvPurchaseReturn(InvPurchaseReturn purchaseReturn);
    int updateInvPurchaseReturn(InvPurchaseReturn purchaseReturn);
    int updateInvPurchaseReturnStatus(@Param("returnId") Long returnId,
            @Param("status") String status, @Param("updateBy") String updateBy);
    InvPurchaseReturn selectInvPurchaseReturnByIdForUpdate(Long returnId);
    int deleteInvPurchaseReturnByIds(Long[] returnIds);
}
