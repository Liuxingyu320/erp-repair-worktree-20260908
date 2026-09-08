package com.erp.inventory.service;

import java.util.List;
import com.erp.inventory.domain.InvTransferDetail;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.InvTransferDiscrepancy;
import com.erp.inventory.domain.dto.InvDeliverRequest;
import com.erp.inventory.domain.dto.InvReceiveRequest;
import com.erp.inventory.domain.dto.InvTransferSourceConfirmRequest;
import com.erp.inventory.domain.dto.InvTransferDiscrepancyResolveRequest;
import com.erp.inventory.domain.vo.InvTransferApprovalTrack;
import com.erp.inventory.domain.vo.InvTransferOpsSummaryVo;
import com.erp.inventory.domain.vo.InvTransferSourceConfirmResult;
import com.erp.inventory.domain.vo.InvTransferRevisionHistoryVo;

public interface IInvTransferService
{
    InvTransferOrder saveDraft(InvTransferOrder order, List<InvTransferDetail> details, Long selectedShopDeptId);
    InvTransferOrder submitTransfer(InvTransferOrder order, List<InvTransferDetail> details, Long selectedShopDeptId);
    InvTransferOrder createDeliveredCrossStoreTransfer(InvTransferOrder order, List<InvTransferDetail> details, Long selectedShopDeptId);
    InvTransferOrder getTransferDetail(Long transferId, Long selectedShopDeptId);
    InvTransferApprovalTrack getApprovalTrack(Long transferId, Long selectedShopDeptId);
    InvTransferRevisionHistoryVo getRevisionHistory(Long transferId,
            Long selectedShopDeptId);
    List<InvTransferOrder> selectTransferList(InvTransferOrder order, Long selectedShopDeptId);
    InvTransferOpsSummaryVo selectOpsSummary(Long selectedShopDeptId);
    void deliverTransfer(Long transferId, Long selectedShopDeptId);
    void deliverTransfer(Long transferId, InvDeliverRequest deliverRequest, Long selectedShopDeptId);
    InvTransferSourceConfirmResult confirmSourceTransfer(Long transferId,
            InvTransferSourceConfirmRequest request, Long selectedShopDeptId);
    void cancelTransfer(Long transferId, Long selectedShopDeptId);
    String withdrawApproval(Long transferId, Long selectedShopDeptId);
    InvTransferOrder createFromPurchase(Long purchaseId, Long selectedShopDeptId);
    InvTransferOrder getTransferByPurchaseId(Long purchaseId);
    void deleteTransfer(Long transferId, Long selectedShopDeptId);
    void receiveTransfer(Long transferId, Long selectedShopDeptId);
    void receiveTransferShipment(Long shipmentId, InvReceiveRequest receiveRequest, Long selectedShopDeptId);
    List<InvTransferDiscrepancy> getTransferDiscrepancies(Long transferId, Long selectedShopDeptId);
    InvTransferDiscrepancy getTransferDiscrepancy(Long discrepancyId, Long selectedShopDeptId);
    void resolveTransferDiscrepancy(Long discrepancyId, InvTransferDiscrepancyResolveRequest request,
            Long selectedShopDeptId);
}
