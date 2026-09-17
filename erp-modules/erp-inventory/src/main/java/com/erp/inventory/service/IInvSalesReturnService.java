package com.erp.inventory.service;

import java.util.List;
import com.erp.inventory.domain.InvSalesReturn;
import com.erp.inventory.domain.InvSalesReturnDetail;

public interface IInvSalesReturnService
{
    com.erp.inventory.domain.vo.InvSpecialistActionContext getActionContext(Long returnId, Long selectedShopDeptId);
    InvSalesReturn saveDraft(InvSalesReturn salesReturn, List<InvSalesReturnDetail> details, Long selectedShopDeptId);
    InvSalesReturn submitReturn(InvSalesReturn salesReturn, List<InvSalesReturnDetail> details, Long selectedShopDeptId);
    InvSalesReturn getReturnDraft(Long returnId, Long selectedShopDeptId);
    InvSalesReturn submitSavedReturn(Long returnId, Long version, Long selectedShopDeptId);
    List<com.erp.inventory.domain.InvSalesOrder> selectReturnableSourceOrders(com.erp.inventory.domain.dto.InvSalesReturnSourceQuery query, Long selectedShopDeptId);
    com.erp.inventory.domain.InvSalesOrder getReturnableSourceOrder(Long orderId, Long selectedShopDeptId);
    InvSalesReturn getReturnDetail(Long returnId, Long selectedShopDeptId);
    List<InvSalesReturn> selectReturnList(InvSalesReturn salesReturn, Long selectedShopDeptId);
    List<InvSalesReturn> selectMyReturns(InvSalesReturn salesReturn, Long selectedShopDeptId);
    void confirmReturn(Long returnId, Long selectedShopDeptId);
    void cancelReturn(Long returnId, Long selectedShopDeptId);
}
