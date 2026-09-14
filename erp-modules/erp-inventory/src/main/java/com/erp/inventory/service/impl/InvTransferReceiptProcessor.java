package com.erp.inventory.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.constant.InvItemTypes;
import com.erp.inventory.constant.InvStatusConstants;
import com.erp.inventory.constant.InvTransferDiscrepancyDecisions;
import com.erp.inventory.constant.InvTransferShipmentWriteVersions;
import com.erp.inventory.domain.InvTransferDetail;
import com.erp.inventory.domain.InvTransferDiscrepancy;
import com.erp.inventory.domain.InvTransferDiscrepancyDetail;
import com.erp.inventory.domain.InvTransferDiscrepancyDisposition;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.InvTransferShipment;
import com.erp.inventory.domain.InvTransferShipmentDetail;
import com.erp.inventory.domain.transfer.InvTransferLifecyclePolicy;
import com.erp.inventory.domain.transfer.InvTransferQuantityPolicy;
import com.erp.inventory.domain.transfer.InvTransferQuantityPolicy.ReceiptBreakdown;
import com.erp.inventory.domain.dto.InvReceiveItem;
import com.erp.inventory.domain.dto.InvReceiveRequest;
import com.erp.inventory.service.BusinessFeatureGate;

/** Owns the transactional business steps executed by transfer receipt APIs. */
final class InvTransferReceiptProcessor extends InvTransferWorkflowSupport
{
    InvTransferReceiptProcessor(InvTransferWorkflowResources resources,
            InvTransferDirectionPolicy directionPolicy)
    {
        super(resources, directionPolicy);
    }

    void receiveTransfer(Long transferId, Long selectedShopDeptId)
    {
        List<InvTransferShipment> shipments = resources.transferShipmentMapper
                .selectByTransferId(transferId);
        boolean receivedAny = false;
        for (InvTransferShipment shipment : shipments)
        {
            if (InvStatusConstants.PENDING_RECEIVE.equals(
                    shipment.getStatus()))
            {
                receiveTransferShipment(shipment.getShipmentId(), null,
                        selectedShopDeptId);
                receivedAny = true;
            }
        }
        if (!receivedAny)
        {
            throw new ServiceException("该调拨单没有待收货发货批次");
        }
    }

    void receiveTransferShipment(Long shipmentId,
            InvReceiveRequest receiveRequest, Long selectedShopDeptId)
    {
        InvTransferShipment locator = resources.transferShipmentMapper.selectById(shipmentId);
        if (locator == null)
        {
            throw new ServiceException("发货批次不存在");
        }
        InvTransferOrder locked = resources.transferOrderMapper
                .selectInvTransferOrderByIdForUpdate(locator.getTransferId());
        if (locked == null)
        {
            throw new ServiceException("调拨单不存在");
        }
        InvTransferShipment shipment = resources.transferShipmentMapper.selectByIdForUpdate(shipmentId);
        if (shipment == null || !java.util.Objects.equals(shipment.getTransferId(), locked.getTransferId()))
        {
            throw new ServiceException("发货批次归属已变化，请刷新后重试");
        }
        if (InvTransferShipmentWriteVersions.V2_DETAIL.equals(
                shipment.getInventoryWriteVersion()))
        {
            throw new ServiceException(
                    "明细库存发货批次必须使用V2收货事务，已拒绝旧收货入口");
        }
        if (!InvStatusConstants.PENDING_RECEIVE.equals(shipment.getStatus()))
        {
            throw new ServiceException("发货批次不是待收货状态");
        }
        assertTransferReceiveScope(locked, selectedShopDeptId);
        directionPolicy.validateReceipt(locked, selectedShopDeptId);
        InvTransferLifecyclePolicy.requireReceiptAllowed(
                locked.getStatus());

        List<InvTransferDetail> orderDetails = resources.transferDetailMapper
                .selectByTransferId(shipment.getTransferId());
        Map<Long, InvTransferDetail> orderDetailMap = new HashMap<>();
        for (InvTransferDetail detail : orderDetails)
        {
            orderDetailMap.put(detail.getDetailId(), detail);
        }
        List<InvTransferShipmentDetail> shipmentDetails =
                resources.transferShipmentDetailMapper
                        .selectByShipmentId(shipmentId);
        if (shipmentDetails.isEmpty())
        {
            throw new ServiceException("发货批次无明细");
        }
        boolean explicitReceiveRequest = hasReceiveItems(receiveRequest);
        Map<Long, InvReceiveItem> receiveItemMap = explicitReceiveRequest
                ? resolveReceiveItems(receiveRequest) : new HashMap<>();
        if (explicitReceiveRequest)
        {
            validateExplicitReceiveItems(receiveItemMap, shipmentDetails);
        }
        Long targetWarehouseId = resolveStockLocationDeptId(
                locked.getToWarehouseId(), locked.getToDeptId());
        if (receiveRequest != null
                && receiveRequest.getWarehouseId() != null
                && !targetWarehouseId.equals(receiveRequest.getWarehouseId()))
        {
            throw new ServiceException("收货仓库必须与调拨目标一致");
        }

        assertDiscrepancyCreationEnabled(explicitReceiveRequest,
                receiveItemMap, shipmentDetails);

        // Scope, shipment state and request shape are checked before reading any file node.
        // Validate every supplied reference before the first inventory/discrepancy write.
        for (InvReceiveItem item : receiveItemMap.values())
        {
            resources.evidenceService.validate(item.getAttachmentRefs());
        }

        List<InvTransferDiscrepancyDetail> discrepancyDetails =
                new ArrayList<>();
        BigDecimal totalShipped = BigDecimal.ZERO;
        BigDecimal totalAccepted = BigDecimal.ZERO;
        BigDecimal totalShortage = BigDecimal.ZERO;
        BigDecimal totalRejected = BigDecimal.ZERO;
        BigDecimal totalDamaged = BigDecimal.ZERO;
        Set<String> discrepancyAttachments = new HashSet<>();
        for (InvTransferShipmentDetail shipmentDetail : shipmentDetails)
        {
            BigDecimal pendingQty = InvTransferQuantityPolicy
                    .remainingToReceive(
                            shipmentDetail.getShippedQuantity(),
                            shipmentDetail.getReceivedQuantity());
            if (pendingQty.compareTo(BigDecimal.ZERO) <= 0)
            {
                continue;
            }
            InvReceiveItem receiveItem = receiveItemMap.get(
                    shipmentDetail.getTransferDetailId());
            BigDecimal acceptedQty = explicitReceiveRequest
                    ? nullToZero(receiveItem.getReceiveQuantity())
                    : pendingQty;
            BigDecimal rejectedQty = explicitReceiveRequest
                    ? nullToZero(receiveItem.getRejectedQuantity())
                    : BigDecimal.ZERO;
            BigDecimal damagedQty = explicitReceiveRequest
                    ? nullToZero(receiveItem.getDamagedQuantity())
                    : BigDecimal.ZERO;
            ReceiptBreakdown receipt = InvTransferQuantityPolicy
                    .classifyReceipt(pendingQty, acceptedQty, rejectedQty,
                            damagedQty);
            BigDecimal shortageQty = receipt.shortageQuantity();
            InvTransferDetail orderDetail = orderDetailMap.get(
                    shipmentDetail.getTransferDetailId());
            if (orderDetail == null)
            {
                throw new ServiceException("发货批次明细未匹配到调拨明细");
            }

            if (shipmentDetail.getCostPrice() == null
                    || shipmentDetail.getCostPrice()
                            .compareTo(BigDecimal.ZERO) < 0)
            {
                throw new ServiceException(
                        "原发货批次成本未冻结，禁止收货和差异登记");
            }
            BigDecimal costPrice = shipmentDetail.getCostPrice();
            String itemType = InvItemTypes.normalize(
                    shipmentDetail.getItemType());
            Long itemId = InvItemTypes.resolveItemId(itemType,
                    shipmentDetail.getItemId(),
                    shipmentDetail.getProductId());
            if (acceptedQty.compareTo(BigDecimal.ZERO) > 0)
            {
                addTargetStockAndLog(locked, itemType, itemId,
                        shipmentDetail.getProductId(), targetWarehouseId,
                        acceptedQty, costPrice,
                        "调拨批次验收入库←" + locked.getFromDeptName());

                shipmentDetail.setReceivedQuantity(nullToZero(
                        shipmentDetail.getReceivedQuantity())
                        .add(acceptedQty));
                resources.transferShipmentDetailMapper
                        .updateReceivedQuantity(shipmentDetail);
                orderDetail.setReceivedQuantity(nullToZero(
                        orderDetail.getReceivedQuantity()).add(acceptedQty));
                resources.transferDetailMapper
                        .updateReceivedQuantity(orderDetail);
            }

            totalShipped = totalShipped.add(pendingQty);
            totalAccepted = totalAccepted.add(acceptedQty);
            totalShortage = totalShortage.add(shortageQty);
            totalRejected = totalRejected.add(rejectedQty);
            totalDamaged = totalDamaged.add(damagedQty);
            if (shortageQty.add(rejectedQty).add(damagedQty)
                    .compareTo(BigDecimal.ZERO) > 0)
            {
                InvTransferDiscrepancyDetail discrepancyDetail =
                        new InvTransferDiscrepancyDetail();
                discrepancyDetail.setTransferDetailId(
                        orderDetail.getDetailId());
                discrepancyDetail.setShipmentDetailId(
                        shipmentDetail.getShipmentDetailId());
                discrepancyDetail.setItemType(itemType);
                discrepancyDetail.setItemId(itemId);
                discrepancyDetail.setItemCode(
                        shipmentDetail.getItemCode());
                discrepancyDetail.setItemName(
                        shipmentDetail.getItemName());
                discrepancyDetail.setShippedQuantity(pendingQty);
                discrepancyDetail.setAcceptedQuantity(acceptedQty);
                discrepancyDetail.setShortageQuantity(shortageQty);
                discrepancyDetail.setRejectedQuantity(rejectedQty);
                discrepancyDetail.setDamagedQuantity(damagedQty);
                discrepancyDetail.setResolutionQuantity(
                        shortageQty.add(rejectedQty).add(damagedQty));
                discrepancyDetail.setNote(receiveItem == null
                        ? null : receiveItem.getDiscrepancyNote());
                discrepancyDetail.setAttachmentRefs(receiveItem == null
                        ? null : receiveItem.getAttachmentRefs());
                discrepancyDetails.add(discrepancyDetail);
                if (receiveItem != null
                        && !isBlank(receiveItem.getAttachmentRefs()))
                {
                    discrepancyAttachments.add(
                            receiveItem.getAttachmentRefs().trim());
                }
            }
        }

        boolean hasDiscrepancy = !discrepancyDetails.isEmpty();
        if (hasDiscrepancy)
        {
            InvTransferDiscrepancy discrepancy =
                    new InvTransferDiscrepancy();
            discrepancy.setTransferId(locked.getTransferId());
            discrepancy.setShipmentId(shipmentId);
            discrepancy.setDiscrepancyNo(generateOrderNo("TD"));
            discrepancy.setStatus(InvTransferDiscrepancyDecisions.OPEN);
            discrepancy.setDiscrepancyType(resolveDiscrepancyType(
                    totalShortage, totalRejected, totalDamaged));
            discrepancy.setShippedQuantity(totalShipped);
            discrepancy.setAcceptedQuantity(totalAccepted);
            discrepancy.setShortageQuantity(totalShortage);
            discrepancy.setRejectedQuantity(totalRejected);
            discrepancy.setDamagedQuantity(totalDamaged);
            discrepancy.setDescription(receiveRequest != null
                    && receiveRequest.getRemark() != null
                    ? receiveRequest.getRemark()
                    : "调拨收货存在数量或货况差异");
            discrepancy.setAttachmentRefs(
                    String.join(",", discrepancyAttachments));
            discrepancy.setCreateBy(SecurityUtils.getUsername());
            resources.transferDiscrepancyMapper
                    .insertDiscrepancy(discrepancy);
            for (InvTransferDiscrepancyDetail detail : discrepancyDetails)
            {
                detail.setDiscrepancyId(discrepancy.getDiscrepancyId());
            }
            resources.transferDiscrepancyMapper
                    .batchInsertDetails(discrepancyDetails);
        }

        InvTransferShipment shipmentUpdate = new InvTransferShipment();
        shipmentUpdate.setShipmentId(shipmentId);
        shipmentUpdate.setStatus(hasDiscrepancy
                ? InvStatusConstants.DISCREPANCY
                : InvStatusConstants.RECEIVED);
        shipmentUpdate.setReceivedBy(SecurityUtils.getUsername());
        shipmentUpdate.setReceivedTime(new Date());
        shipmentUpdate.setUpdateBy(SecurityUtils.getUsername());
        resources.transferShipmentMapper.updateShipment(shipmentUpdate);

        String previousStatus = locked.getStatus();
        InvTransferOrder orderUpdate = new InvTransferOrder();
        orderUpdate.setTransferId(locked.getTransferId());
        String nextStatus = resolvePostReceiptStatus(locked.getTransferId(),
                orderDetails, hasDiscrepancy);
        orderUpdate.setStatus(nextStatus);
        orderUpdate.setReceivedTime(new Date());
        if (InvStatusConstants.RECEIVED.equals(nextStatus)
                || InvStatusConstants.CLOSED.equals(nextStatus))
        {
            orderUpdate.setArchivedTime(new Date());
            if (InvStatusConstants.CLOSED.equals(nextStatus))
            {
                orderUpdate.setCloseReason(
                        "差异补发收货完成，剩余未入库数量已有终态处置台账");
            }
        }
        orderUpdate.setUpdateBy(SecurityUtils.getUsername());
        resources.transferOrderMapper
                .updateInvTransferOrder(orderUpdate);
        if (!previousStatus.equals(orderUpdate.getStatus()))
        {
            writeStatusLog(locked.getTransferId(), previousStatus,
                    orderUpdate.getStatus(), "receive",
                    (hasDiscrepancy ? "调拨批次差异收货 "
                            : "调拨批次收货 ")
                            + shipment.getShipmentNo());
        }
        if (hasDiscrepancy)
        {
            recordTransferDiscrepancy("created");
        }
    }

    private String resolvePostReceiptStatus(Long transferId,
            List<InvTransferDetail> orderDetails, boolean hasDiscrepancy)
    {
        if (hasDiscrepancy)
        {
            return InvStatusConstants.DISCREPANCY;
        }
        if (allRequestedReceived(orderDetails))
        {
            return InvStatusConstants.RECEIVED;
        }

        DispositionCoverage coverage = terminalDispositionCoverage(
                transferId, orderDetails);
        if (coverage.hasLedger() && !allRequestedDelivered(orderDetails))
        {
            return InvStatusConstants.PARTIAL_DELIVERED;
        }
        if (coverage.fullyAccounted()
                && !hasPendingReceiveShipment(transferId))
        {
            return InvStatusConstants.CLOSED;
        }
        return InvStatusConstants.PARTIAL_RECEIVED;
    }

    private DispositionCoverage terminalDispositionCoverage(Long transferId,
            List<InvTransferDetail> orderDetails)
    {
        List<InvTransferDiscrepancy> discrepancies = resources
                .transferDiscrepancyMapper.selectByTransferId(transferId);
        if (discrepancies == null || discrepancies.isEmpty()
                || orderDetails == null || orderDetails.isEmpty())
        {
            return new DispositionCoverage(false, false);
        }

        Map<Long, BigDecimal> accountedByTransferDetail = new HashMap<>();
        boolean hasLedger = false;
        for (InvTransferDiscrepancy discrepancy : discrepancies)
        {
            List<InvTransferDiscrepancyDisposition> latest = resources
                    .dispositionMapper.selectLatestByDiscrepancyId(
                            discrepancy.getDiscrepancyId());
            if (latest == null || latest.isEmpty())
            {
                continue;
            }
            hasLedger = true;
            if (!InvTransferDiscrepancyDecisions.RESOLVED.equals(
                    discrepancy.getStatus()))
            {
                return new DispositionCoverage(true, false);
            }
            Map<Long, Long> transferDetailByDiscrepancyDetail =
                    new HashMap<>();
            List<InvTransferDiscrepancyDetail> details = resources
                    .transferDiscrepancyMapper.selectDetails(
                            discrepancy.getDiscrepancyId());
            if (details != null)
            {
                for (InvTransferDiscrepancyDetail detail : details)
                {
                    transferDetailByDiscrepancyDetail.put(
                            detail.getDiscrepancyDetailId(),
                            detail.getTransferDetailId());
                }
            }
            for (InvTransferDiscrepancyDisposition disposition : latest)
            {
                if (!isTerminalNonReceivedDisposition(disposition))
                {
                    continue;
                }
                Long transferDetailId = transferDetailByDiscrepancyDetail.get(
                        disposition.getDiscrepancyDetailId());
                if (transferDetailId == null || disposition.getQuantity() == null
                        || disposition.getQuantity()
                                .compareTo(BigDecimal.ZERO) <= 0)
                {
                    return new DispositionCoverage(true, false);
                }
                accountedByTransferDetail.merge(transferDetailId,
                        disposition.getQuantity(), BigDecimal::add);
            }
        }
        if (!hasLedger)
        {
            return new DispositionCoverage(false, false);
        }

        Set<Long> orderDetailIds = new HashSet<>();
        for (InvTransferDetail detail : orderDetails)
        {
            orderDetailIds.add(detail.getDetailId());
            BigDecimal shortfall = nullToZero(detail.getQuantity())
                    .subtract(nullToZero(detail.getReceivedQuantity()));
            if (shortfall.compareTo(BigDecimal.ZERO) < 0
                    || shortfall.compareTo(accountedByTransferDetail
                            .getOrDefault(detail.getDetailId(),
                                    BigDecimal.ZERO)) != 0)
            {
                return new DispositionCoverage(true, false);
            }
        }
        if (!orderDetailIds.containsAll(accountedByTransferDetail.keySet()))
        {
            return new DispositionCoverage(true, false);
        }
        return new DispositionCoverage(true, true);
    }

    private boolean isTerminalNonReceivedDisposition(
            InvTransferDiscrepancyDisposition disposition)
    {
        String decision = disposition.getDecision() == null ? ""
                : disposition.getDecision().trim().toUpperCase();
        return Set.of(InvTransferDiscrepancyDecisions.RETURN_SOURCE,
                InvTransferDiscrepancyDecisions.ACCEPT_ACTUAL,
                InvTransferDiscrepancyDecisions.WRITE_OFF)
                .contains(decision);
    }

    private boolean allRequestedDelivered(List<InvTransferDetail> details)
    {
        for (InvTransferDetail detail : details)
        {
            if (nullToZero(detail.getDeliveredQuantity()).compareTo(
                    nullToZero(detail.getQuantity())) < 0)
            {
                return false;
            }
        }
        return !details.isEmpty();
    }

    private boolean hasPendingReceiveShipment(Long transferId)
    {
        List<InvTransferShipment> shipments = resources.transferShipmentMapper
                .selectByTransferId(transferId);
        return shipments != null && shipments.stream().anyMatch(shipment ->
                InvStatusConstants.PENDING_RECEIVE.equals(
                        shipment.getStatus()));
    }

    private record DispositionCoverage(boolean hasLedger,
            boolean fullyAccounted)
    {
    }

    private Map<Long, InvReceiveItem> resolveReceiveItems(
            InvReceiveRequest request)
    {
        Map<Long, InvReceiveItem> result = new HashMap<>();
        if (!hasReceiveItems(request))
        {
            return result;
        }
        for (InvReceiveItem item : request.getItems())
        {
            if (item.getDetailId() == null)
            {
                throw new ServiceException("收货明细ID不能为空");
            }
            validateNonNegative(item.getReceiveQuantity(),
                    "验收入库数量不能小于0");
            validateNonNegative(item.getRejectedQuantity(),
                    "拒收数量不能小于0");
            validateNonNegative(item.getDamagedQuantity(),
                    "残损数量不能小于0");
            if (result.containsKey(item.getDetailId()))
            {
                throw new ServiceException("收货明细ID重复");
            }
            BigDecimal classified = nullToZero(item.getReceiveQuantity())
                    .add(nullToZero(item.getRejectedQuantity()))
                    .add(nullToZero(item.getDamagedQuantity()));
            if (classified.compareTo(BigDecimal.ZERO) < 0)
            {
                throw new ServiceException("收货数量无效");
            }
            result.put(item.getDetailId(), item);
        }
        return result;
    }

    private boolean hasReceiveItems(InvReceiveRequest request)
    {
        return request != null && request.getItems() != null
                && !request.getItems().isEmpty();
    }

    private void assertDiscrepancyCreationEnabled(
            boolean explicitReceiveRequest,
            Map<Long, InvReceiveItem> receiveItemMap,
            List<InvTransferShipmentDetail> shipmentDetails)
    {
        if (!explicitReceiveRequest || resources.businessFeatureGate == null)
        {
            return;
        }
        for (InvTransferShipmentDetail shipmentDetail : shipmentDetails)
        {
            BigDecimal pendingQty = InvTransferQuantityPolicy
                    .remainingToReceive(
                            shipmentDetail.getShippedQuantity(),
                            shipmentDetail.getReceivedQuantity());
            if (pendingQty.compareTo(BigDecimal.ZERO) <= 0)
            {
                continue;
            }
            InvReceiveItem item = receiveItemMap.get(
                    shipmentDetail.getTransferDetailId());
            BigDecimal accepted = nullToZero(item.getReceiveQuantity());
            BigDecimal rejected = nullToZero(item.getRejectedQuantity());
            BigDecimal damaged = nullToZero(item.getDamagedQuantity());
            ReceiptBreakdown receipt = InvTransferQuantityPolicy
                    .classifyReceipt(pendingQty, accepted, rejected,
                            damaged);
            if (receipt.hasDiscrepancy())
            {
                resources.businessFeatureGate.requireEnabled(
                        BusinessFeatureGate.TRANSFER_DISCREPANCY);
                return;
            }
        }
    }

    private void validateExplicitReceiveItems(
            Map<Long, InvReceiveItem> receiveItemMap,
            List<InvTransferShipmentDetail> shipmentDetails)
    {
        Set<Long> expectedDetailIds = new HashSet<>();
        for (InvTransferShipmentDetail shipmentDetail : shipmentDetails)
        {
            BigDecimal pendingQty = InvTransferQuantityPolicy
                    .remainingToReceive(
                            shipmentDetail.getShippedQuantity(),
                            shipmentDetail.getReceivedQuantity());
            if (pendingQty.compareTo(BigDecimal.ZERO) > 0)
            {
                expectedDetailIds.add(
                        shipmentDetail.getTransferDetailId());
            }
        }
        for (Long detailId : receiveItemMap.keySet())
        {
            if (!expectedDetailIds.contains(detailId))
            {
                throw new ServiceException("收货明细不属于当前发货批次");
            }
        }
        if (!receiveItemMap.keySet().containsAll(expectedDetailIds))
        {
            throw new ServiceException("收货明细不完整");
        }
        for (InvTransferShipmentDetail shipmentDetail : shipmentDetails)
        {
            BigDecimal pendingQty = shipmentDetail.getShippedQuantity()
                    .subtract(nullToZero(
                            shipmentDetail.getReceivedQuantity()));
            if (pendingQty.compareTo(BigDecimal.ZERO) <= 0)
            {
                continue;
            }
            InvReceiveItem item = receiveItemMap.get(
                    shipmentDetail.getTransferDetailId());
            BigDecimal accepted = nullToZero(item.getReceiveQuantity());
            BigDecimal rejected = nullToZero(item.getRejectedQuantity());
            BigDecimal damaged = nullToZero(item.getDamagedQuantity());
            ReceiptBreakdown receipt;
            try
            {
                receipt = InvTransferQuantityPolicy.classifyReceipt(
                        pendingQty, accepted, rejected, damaged);
            }
            catch (ServiceException exception)
            {
                throw new ServiceException("物料 ["
                        + shipmentDetail.getItemName() + "] "
                        + exception.getMessage());
            }
            if (receipt.hasDiscrepancy()
                    && isBlank(item.getDiscrepancyNote())
                    && isBlank(item.getAttachmentRefs()))
            {
                throw new ServiceException("物料 ["
                        + shipmentDetail.getItemName()
                        + "] 存在差异，请填写说明或上传凭证");
            }
        }
    }

    private void validateNonNegative(BigDecimal value, String message)
    {
        if (value != null && value.compareTo(BigDecimal.ZERO) < 0)
        {
            throw new ServiceException(message);
        }
    }

    private String resolveDiscrepancyType(BigDecimal shortage,
            BigDecimal rejected, BigDecimal damaged)
    {
        List<String> types = new ArrayList<>();
        if (shortage.compareTo(BigDecimal.ZERO) > 0)
        {
            types.add("SHORTAGE");
        }
        if (rejected.compareTo(BigDecimal.ZERO) > 0)
        {
            types.add("REJECTED");
        }
        if (damaged.compareTo(BigDecimal.ZERO) > 0)
        {
            types.add("DAMAGED");
        }
        return String.join(",", types);
    }
}
