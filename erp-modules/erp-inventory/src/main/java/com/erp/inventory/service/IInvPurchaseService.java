package com.erp.inventory.service;

import java.util.List;
import com.erp.inventory.domain.InvPurchaseDetail;
import com.erp.inventory.domain.InvGiftBox;
import com.erp.inventory.domain.InvOeItem;
import com.erp.inventory.domain.InvProduct;
import com.erp.inventory.domain.InvPurchaseOrder;
import com.erp.inventory.domain.InvReceiptBatch;
import com.erp.inventory.domain.InvSupplier;
import com.erp.inventory.domain.dto.InvQualityCheckRequest;
import com.erp.inventory.domain.dto.InvReceiveRequest;

public interface IInvPurchaseService
{
    InvPurchaseOrder saveDraft(InvPurchaseOrder order, List<InvPurchaseDetail> details, Long selectedShopDeptId);
    InvPurchaseOrder submitPurchase(InvPurchaseOrder order, List<InvPurchaseDetail> details, Long selectedShopDeptId);
    InvPurchaseOrder submitSavedPurchase(Long orderId, Long selectedShopDeptId);
    InvPurchaseOrder getPurchaseDetail(Long orderId, Long selectedShopDeptId);
    List<InvPurchaseOrder> selectPurchaseList(InvPurchaseOrder order, Long selectedShopDeptId);
    List<InvPurchaseOrder> selectMyPurchases(InvPurchaseOrder order, Long selectedShopDeptId);
    List<InvSupplier> selectPurchaseSuppliers(InvSupplier supplier, Long selectedShopDeptId);
    List<InvProduct> selectPurchaseProducts(InvProduct product, Long selectedShopDeptId);
    List<InvOeItem> selectPurchaseOeItems(InvOeItem item, Long selectedShopDeptId);
    List<InvGiftBox> selectPurchaseGifts(InvGiftBox gift, Long selectedShopDeptId);
    void receivePurchase(Long orderId, InvReceiveRequest receiveRequest, Long selectedShopDeptId);
    List<InvReceiptBatch> selectReceiptBatches(Long orderId, Long selectedShopDeptId);
    List<InvReceiptBatch> selectPendingReceiptBatches(Long orderId, Long selectedShopDeptId);
    void qualityCheckBatch(Long orderId, InvQualityCheckRequest qualityCheckRequest, Long selectedShopDeptId);
    void qualityCheck(Long orderId, String qcResult, String qcRemark, Long selectedShopDeptId);
    void cancelPurchase(Long orderId, Long selectedShopDeptId);
    void deletePurchase(Long orderId, Long selectedShopDeptId);
}
