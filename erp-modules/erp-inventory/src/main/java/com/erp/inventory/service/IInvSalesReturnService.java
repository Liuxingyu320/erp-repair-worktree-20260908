package com.erp.inventory.service;

import java.util.List;
import com.erp.inventory.domain.InvSalesReturn;
import com.erp.inventory.domain.InvSalesReturnDetail;

public interface IInvSalesReturnService
{
    InvSalesReturn saveDraft(InvSalesReturn salesReturn, List<InvSalesReturnDetail> details, Long selectedShopDeptId);
    InvSalesReturn submitReturn(InvSalesReturn salesReturn, List<InvSalesReturnDetail> details, Long selectedShopDeptId);
    InvSalesReturn getReturnDetail(Long returnId, Long selectedShopDeptId);
    List<InvSalesReturn> selectReturnList(InvSalesReturn salesReturn, Long selectedShopDeptId);
    List<InvSalesReturn> selectMyReturns(InvSalesReturn salesReturn, Long selectedShopDeptId);
    void confirmReturn(Long returnId, Long selectedShopDeptId);
    void cancelReturn(Long returnId, Long selectedShopDeptId);
}
