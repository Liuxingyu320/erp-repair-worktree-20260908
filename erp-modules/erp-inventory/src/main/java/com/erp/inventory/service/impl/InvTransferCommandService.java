package com.erp.inventory.service.impl;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.inventory.constant.InvTransferCommandTypes;
import com.erp.inventory.domain.InvTransferDetail;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.dto.InvDeliverRequest;
import com.erp.inventory.domain.dto.InvReceiveRequest;
import com.erp.inventory.domain.dto.InvTransferApprovalRequest;
import com.erp.inventory.domain.dto.InvTransferDiscrepancyResolveRequest;
import com.erp.inventory.domain.dto.InvTransferShipmentCreateRequest;
import com.erp.inventory.domain.dto.InvTransferShipmentReceiptCreateRequest;
import com.erp.inventory.domain.vo.InvTransferShipmentCreationVo;
import com.erp.inventory.domain.vo.InvTransferShipmentReceiptCreationVo;
import com.erp.inventory.service.IInvTransferApprovalService;
import com.erp.inventory.service.IInvTransferService;

@Service
public class InvTransferCommandService
{
    private final InvTransferCommandExecutor commandExecutor;
    private final IInvTransferService transferService;
    private final IInvTransferApprovalService transferApprovalService;
    private final InvTransferShipmentCreationService shipmentCreationService;
    private final InvTransferShipmentReceiptWriteGate receiptWriteGate;
    private final InvTransferShipmentReceiptCreationService
            receiptCreationService;

    public InvTransferCommandService(InvTransferCommandExecutor commandExecutor,
            IInvTransferService transferService,
            IInvTransferApprovalService transferApprovalService,
            InvTransferShipmentCreationService shipmentCreationService,
            InvTransferShipmentReceiptWriteGate receiptWriteGate,
            InvTransferShipmentReceiptCreationService
                    receiptCreationService)
    {
        this.commandExecutor = commandExecutor;
        this.transferService = transferService;
        this.transferApprovalService = transferApprovalService;
        this.shipmentCreationService = shipmentCreationService;
        this.receiptWriteGate = receiptWriteGate;
        this.receiptCreationService = receiptCreationService;
    }

    @Transactional(rollbackFor = Exception.class)
    public InvTransferOrder saveDraft(String requestId,
            InvTransferOrder order, List<InvTransferDetail> details,
            Long selectedDeptId)
    {
        return commandExecutor.execute(requestId,
                InvTransferCommandTypes.DRAFT_SAVE, selectedDeptId,
                transferResource(order == null ? null : order.getTransferId()),
                new DraftPayload(order, details), InvTransferOrder.class,
                () -> transferService.saveDraft(order, details,
                        selectedDeptId));
    }

    @Transactional(rollbackFor = Exception.class)
    public InvTransferOrder submit(String requestId, InvTransferOrder order,
            List<InvTransferDetail> details, Long selectedDeptId)
    {
        return commandExecutor.execute(requestId,
                InvTransferCommandTypes.SUBMIT, selectedDeptId,
                transferResource(order == null ? null : order.getTransferId()),
                new DraftPayload(order, details), InvTransferOrder.class,
                () -> transferService.submitTransfer(order, details,
                        selectedDeptId));
    }

    @Transactional(rollbackFor = Exception.class)
    public Void approve(String requestId,
            InvTransferApprovalRequest request, Long selectedDeptId)
    {
        return commandExecutor.execute(requestId,
                InvTransferCommandTypes.APPROVE, selectedDeptId,
                transferResource(request == null ? null
                        : request.getTransferId()),
                request, Void.class, () -> {
                    transferApprovalService.approve(request, selectedDeptId);
                    return null;
                });
    }

    @Transactional(rollbackFor = Exception.class)
    public Void deliver(String requestId, Long transferId,
            InvDeliverRequest request, Long selectedDeptId)
    {
        return commandExecutor.execute(requestId,
                InvTransferCommandTypes.DELIVER, selectedDeptId,
                transferResource(transferId), request, Void.class, () -> {
                    transferService.deliverTransfer(transferId, request,
                            selectedDeptId);
                    return null;
                });
    }

    @Transactional(rollbackFor = Exception.class)
    public InvTransferShipmentCreationVo createShipmentV2(String requestId,
            Long transferId, InvTransferShipmentCreateRequest request,
            Long selectedDeptId)
    {
        return commandExecutor.execute(requestId,
                InvTransferCommandTypes.SHIPMENT_CREATE_V2, selectedDeptId,
                transferResource(transferId), request,
                InvTransferShipmentCreationVo.class,
                () -> shipmentCreationService.create(requestId, transferId,
                        request, selectedDeptId));
    }

    @Transactional(rollbackFor = Exception.class)
    public InvTransferShipmentReceiptCreationVo createReceiptV2(
            String requestId, Long shipmentId,
            InvTransferShipmentReceiptCreateRequest request,
            Long selectedDeptId)
    {
        receiptWriteGate.requireEnabled();
        return commandExecutor.execute(requestId,
                InvTransferCommandTypes.SHIPMENT_RECEIPT_CREATE_V2,
                selectedDeptId, shipmentResource(shipmentId), request,
                InvTransferShipmentReceiptCreationVo.class,
                () -> receiptCreationService.create(requestId, shipmentId,
                        request, selectedDeptId));
    }

    @Transactional(rollbackFor = Exception.class)
    public Void receive(String requestId, Long transferId,
            Long selectedDeptId)
    {
        return commandExecutor.execute(requestId,
                InvTransferCommandTypes.RECEIVE, selectedDeptId,
                transferResource(transferId), null, Void.class, () -> {
                    transferService.receiveTransfer(transferId,
                            selectedDeptId);
                    return null;
                });
    }

    @Transactional(rollbackFor = Exception.class)
    public Void receiveShipment(String requestId, Long shipmentId,
            InvReceiveRequest request, Long selectedDeptId)
    {
        return commandExecutor.execute(requestId,
                InvTransferCommandTypes.SHIPMENT_RECEIVE, selectedDeptId,
                shipmentResource(shipmentId), request, Void.class, () -> {
                    transferService.receiveTransferShipment(shipmentId,
                            request, selectedDeptId);
                    return null;
                });
    }

    @Transactional(rollbackFor = Exception.class)
    public Void resolveDiscrepancy(String requestId, Long discrepancyId,
            InvTransferDiscrepancyResolveRequest request,
            Long selectedDeptId)
    {
        return commandExecutor.execute(requestId,
                InvTransferCommandTypes.DISCREPANCY_RESOLVE,
                selectedDeptId, discrepancyResource(discrepancyId), request,
                Void.class, () -> {
                    transferService.resolveTransferDiscrepancy(discrepancyId,
                            request, selectedDeptId);
                    return null;
                });
    }

    @Transactional(rollbackFor = Exception.class)
    public Void deleteDraft(String requestId, Long transferId,
            Long selectedDeptId)
    {
        return commandExecutor.execute(requestId,
                InvTransferCommandTypes.DRAFT_DELETE, selectedDeptId,
                transferResource(transferId), null, Void.class, () -> {
                    transferService.deleteTransfer(transferId,
                            selectedDeptId);
                    return null;
                });
    }

    @Transactional(rollbackFor = Exception.class)
    public Void cancel(String requestId, Long transferId,
            Long selectedDeptId)
    {
        return commandExecutor.execute(requestId,
                InvTransferCommandTypes.CANCEL, selectedDeptId,
                transferResource(transferId), null, Void.class, () -> {
                    transferService.cancelTransfer(transferId,
                            selectedDeptId);
                    return null;
                });
    }

    @Transactional(rollbackFor = Exception.class)
    public String withdraw(String requestId, Long transferId,
            Long selectedDeptId)
    {
        return commandExecutor.execute(requestId,
                InvTransferCommandTypes.WITHDRAW, selectedDeptId,
                transferResource(transferId), null, String.class,
                () -> transferService.withdrawApproval(transferId,
                        selectedDeptId));
    }

    private static String transferResource(Long transferId)
    {
        return transferId == null ? "transfer:new"
                : "transfer:" + transferId;
    }

    private static String shipmentResource(Long shipmentId)
    {
        return "transfer-shipment:" + String.valueOf(shipmentId);
    }

    private static String discrepancyResource(Long discrepancyId)
    {
        return "transfer-discrepancy:" + String.valueOf(discrepancyId);
    }

    private record DraftPayload(InvTransferOrder order,
            List<InvTransferDetail> details)
    {
    }
}
