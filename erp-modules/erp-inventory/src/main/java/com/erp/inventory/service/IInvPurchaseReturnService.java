package com.erp.inventory.service;

import java.util.List;
import com.erp.inventory.domain.InvPurchaseOrder;
import com.erp.inventory.domain.InvPurchaseReturn;
import com.erp.inventory.domain.InvPurchaseReturnDetail;

public interface IInvPurchaseReturnService
{
    com.erp.inventory.domain.vo.InvSpecialistActionContext getActionContext(Long returnId, Long selectedShopDeptId);
    InvPurchaseReturn saveDraft(InvPurchaseReturn purchaseReturn, List<InvPurchaseReturnDetail> details, Long selectedShopDeptId);
    InvPurchaseReturn submitReturn(InvPurchaseReturn purchaseReturn, List<InvPurchaseReturnDetail> details, Long selectedShopDeptId);
    InvPurchaseReturn submitSavedReturn(Long returnId, Long selectedShopDeptId);
    InvPurchaseReturn getReturnDraft(Long returnId, Long selectedShopDeptId);
    InvPurchaseReturn getReturnDetail(Long returnId, Long selectedShopDeptId);
    List<InvPurchaseReturn> selectReturnList(InvPurchaseReturn purchaseReturn, Long selectedShopDeptId);
    List<InvPurchaseReturn> selectMyReturns(InvPurchaseReturn purchaseReturn, Long selectedShopDeptId);
    List<InvPurchaseOrder> selectReturnableSourceOrders(InvPurchaseOrder purchaseOrder, Long selectedShopDeptId);
    InvPurchaseOrder getReturnableSourceOrder(Long orderId, Long selectedShopDeptId);
    void confirmReturn(Long returnId, Long selectedShopDeptId);
    void cancelReturn(Long returnId, Long selectedShopDeptId);
}
