package com.erp.inventory.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.constant.InvItemTypes;
import com.erp.inventory.constant.InvStatusConstants;
import com.erp.inventory.constant.InvTransferDiscrepancyDecisions;
import com.erp.inventory.domain.InvTransferDetail;
import com.erp.inventory.domain.InvTransferDiscrepancy;
import com.erp.inventory.domain.InvTransferDiscrepancyDetail;
import com.erp.inventory.domain.InvTransferDiscrepancyDisposition;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.InvTransferShipment;
import com.erp.inventory.domain.InvTransferShipmentDetail;
import com.erp.inventory.domain.dto.InvTransferDiscrepancyResolutionItem;
import com.erp.inventory.domain.dto.InvTransferDiscrepancyResolveRequest;

/** Owns discrepancy query, per-category resolution and costed disposition. */
final class InvTransferDiscrepancyProcessor extends InvTransferWorkflowSupport
{
    private static final Set<String> RESPONSIBLE_PARTIES = Set.of(
            "SOURCE", "TARGET", "LOGISTICS", "UNCONFIRMED");
    private static final String NO_STOCK_CHANGE = "NO_STOCK_CHANGE";
    private static final String SOURCE_STOCK_IN = "SOURCE_STOCK_IN";
    private static final String QC_HOLD = "QC_HOLD";

    InvTransferDiscrepancyProcessor(InvTransferWorkflowResources resources,
            InvTransferDirectionPolicy directionPolicy)
    {
        super(resources, directionPolicy);
    }

    List<InvTransferDiscrepancy> getTransferDiscrepancies(Long transferId,
            Long selectedShopDeptId)
    {
        assertAndGetScopedTransfer(transferId, selectedShopDeptId);
        List<InvTransferDiscrepancy> rows = resources.transferDiscrepancyMapper
                .selectByTransferId(transferId);
        for (InvTransferDiscrepancy row : rows)
        {
            fillAuditRows(row);
        }
        return rows;
    }

    InvTransferDiscrepancy getTransferDiscrepancy(Long discrepancyId,
            Long selectedShopDeptId)
    {
        InvTransferDiscrepancy discrepancy = resources.transferDiscrepancyMapper
                .selectById(discrepancyId);
        if (discrepancy == null)
        {
            throw new ServiceException("调拨差异单不存在");
        }
        assertAndGetScopedTransfer(discrepancy.getTransferId(),
                selectedShopDeptId);
        fillAuditRows(discrepancy);
        return discrepancy;
    }

    void resolveTransferDiscrepancy(Long discrepancyId,
            InvTransferDiscrepancyResolveRequest request,
            Long selectedShopDeptId)
    {
        validateRequestHeader(request);
        // The locator is not authority: lock the transfer root, then reread the child.
        InvTransferDiscrepancy locator = resources.transferDiscrepancyMapper.selectById(discrepancyId);
        if (locator == null)
        {
            throw new ServiceException("调拨差异单不存在");
        }
        InvTransferOrder locked = resources.transferOrderMapper
                .selectInvTransferOrderByIdForUpdate(locator.getTransferId());
        if (locked == null)
        {
            throw new ServiceException("调拨单不存在");
        }
        InvTransferDiscrepancy discrepancy = resources.transferDiscrepancyMapper
                .selectByIdForUpdate(discrepancyId);
        if (discrepancy == null || !Objects.equals(discrepancy.getTransferId(), locked.getTransferId()))
        {
            throw concurrentResolution();
        }
        assertTransferReceiveScope(locked, selectedShopDeptId);
        directionPolicy.validateReceipt(locked, selectedShopDeptId);

        List<InvTransferDiscrepancyDetail> discrepancyDetails =
                resources.transferDiscrepancyMapper.selectDetails(
                        discrepancyId);
        if (discrepancyDetails.isEmpty())
        {
            throw new ServiceException("调拨差异单无明细");
        }
        assertQuantityConservation(discrepancy, discrepancyDetails);
        List<InvTransferDiscrepancyResolutionItem> items = normalizeItems(
                request, discrepancy, discrepancyDetails);

        boolean staleVersion = !request.getVersion().equals(discrepancy.getVersion());
        boolean writableStatus = Set.of(InvTransferDiscrepancyDecisions.OPEN,
                InvTransferDiscrepancyDecisions.PENDING_QC).contains(discrepancy.getStatus());
        // Current reads are confined to replay/rejection: that branch cannot insert a new receipt.
        // Locking an absent receipt for fresh requests could deadlock different transfer roots on one gap.
        List<InvTransferDiscrepancyDisposition> replayRows = staleVersion || !writableStatus
                ? resources.dispositionMapper.selectByRequestIdForUpdate(discrepancyId, request.getRequestId())
                : resources.dispositionMapper.selectByRequestId(discrepancyId, request.getRequestId());
        if (replayRows != null && !replayRows.isEmpty())
        {
            assertExactReplay(request, items, discrepancyDetails,
                    replayRows);
            return;
        }

        if (staleVersion)
        {
            throw concurrentResolution();
        }
        if (!writableStatus)
        {
            throw new ServiceException("差异单已处理，请刷新后查看台账");
        }

        if (!InvStatusConstants.DISCREPANCY.equals(locked.getStatus()))
        {
            throw new ServiceException("调拨单当前不在差异处理状态");
        }

        Map<Long, InvTransferDiscrepancyDetail> detailById = discrepancyDetails
                .stream().collect(Collectors.toMap(
                        InvTransferDiscrepancyDetail::getDiscrepancyDetailId,
                        detail -> detail));
        Map<Long, InvTransferShipmentDetail> shipmentById = resources
                .transferShipmentDetailMapper
                .selectByShipmentId(discrepancy.getShipmentId()).stream()
                .collect(Collectors.toMap(
                        InvTransferShipmentDetail::getShipmentDetailId,
                        detail -> detail));
        List<InvTransferDiscrepancyDisposition> latestRows =
                resources.dispositionMapper.selectLatestByDiscrepancyId(
                        discrepancyId);
        if (latestRows == null)
        {
            latestRows = List.of();
        }

        ResolutionPlan plan = buildResolutionPlan(discrepancy, request,
                items, detailById, shipmentById, latestRows, locked);

        reserveReshipmentQuantities(locked, discrepancy, plan);

        int updated = resources.transferDiscrepancyMapper.resolve(
                discrepancyId, request.getVersion(), plan.targetStatus(),
                plan.summaryDecision(), normalizeResponsibleParty(
                        request.getResponsibleParty()), request.getNote().trim(),
                SecurityUtils.getUserId(), SecurityUtils.getUsername());
        if (updated == 0)
        {
            throw concurrentResolution();
        }

        applyInventoryEffects(locked, discrepancy, plan);
        for (InvTransferDiscrepancyDisposition row : plan.newRows())
        {
            if (resources.dispositionMapper.insertDisposition(row) != 1)
            {
                throw new ServiceException("差异处置台账写入失败");
            }
        }

        if (InvTransferDiscrepancyDecisions.PENDING_QC.equals(
                plan.targetStatus()))
        {
            writeStatusLog(locked.getTransferId(),
                    InvStatusConstants.DISCREPANCY,
                    InvStatusConstants.DISCREPANCY, "discrepancy_qc",
                    "差异单存在待质检明细："
                            + discrepancy.getDiscrepancyNo());
            recordTransferDiscrepancy("pending_qc");
            return;
        }

        finishResolvedDiscrepancy(locked, discrepancy, plan,
                request.getNote().trim());
    }

    private void fillAuditRows(InvTransferDiscrepancy discrepancy)
    {
        discrepancy.setDetails(resources.transferDiscrepancyMapper
                .selectDetails(discrepancy.getDiscrepancyId()));
        discrepancy.setDispositions(resources.dispositionMapper
                .selectByDiscrepancyId(discrepancy.getDiscrepancyId()));
    }

    private void validateRequestHeader(
            InvTransferDiscrepancyResolveRequest request)
    {
        if (request == null || request.getVersion() == null)
        {
            throw new ServiceException("差异单版本不能为空");
        }
        if (isBlank(request.getRequestId()))
        {
            throw new ServiceException("差异处置 requestId 不能为空");
        }
        if (request.getRequestId().trim().length() > 64)
        {
            throw new ServiceException("差异处置 requestId 过长");
        }
        request.setRequestId(request.getRequestId().trim());
        if (isBlank(request.getNote()))
        {
            throw new ServiceException("请填写差异处理说明");
        }
        if (request.getNote().trim().length() > 1000)
        {
            throw new ServiceException("差异处理说明过长");
        }
        normalizeResponsibleParty(request.getResponsibleParty());
    }

    private List<InvTransferDiscrepancyResolutionItem> normalizeItems(
            InvTransferDiscrepancyResolveRequest request,
            InvTransferDiscrepancy discrepancy,
            List<InvTransferDiscrepancyDetail> details)
    {
        if (request.getItems() != null && !request.getItems().isEmpty())
        {
            return request.getItems();
        }
        String category = singleLegacyCategory(details);
        if (category == null || isBlank(request.getDecision()))
        {
            throw new ServiceException(
                    "当前差异包含多个类别，请升级客户端后逐项处置");
        }
        List<InvTransferDiscrepancyResolutionItem> legacyItems =
                new ArrayList<>();
        for (InvTransferDiscrepancyDetail detail : details)
        {
            BigDecimal quantity = categoryQuantity(detail, category);
            if (quantity.compareTo(BigDecimal.ZERO) <= 0)
            {
                continue;
            }
            InvTransferDiscrepancyResolutionItem item =
                    new InvTransferDiscrepancyResolutionItem();
            item.setDetailId(detail.getDiscrepancyDetailId());
            item.setCategory(category);
            item.setDecision(request.getDecision());
            item.setQuantity(quantity);
            item.setNote(request.getNote());
            item.setAttachmentRefs(detail.getAttachmentRefs());
            legacyItems.add(item);
        }
        if (legacyItems.isEmpty())
        {
            throw new ServiceException("差异单无待处置数量");
        }
        return legacyItems;
    }

    private String singleLegacyCategory(
            List<InvTransferDiscrepancyDetail> details)
    {
        Set<String> categories = new HashSet<>();
        for (InvTransferDiscrepancyDetail detail : details)
        {
            if (nullToZero(detail.getShortageQuantity())
                    .compareTo(BigDecimal.ZERO) > 0)
            {
                categories.add(InvTransferDiscrepancyDecisions.SHORTAGE);
            }
            if (nullToZero(detail.getRejectedQuantity())
                    .compareTo(BigDecimal.ZERO) > 0)
            {
                categories.add(InvTransferDiscrepancyDecisions.REJECTED);
            }
            if (nullToZero(detail.getDamagedQuantity())
                    .compareTo(BigDecimal.ZERO) > 0)
            {
                categories.add(InvTransferDiscrepancyDecisions.DAMAGED);
            }
        }
        return categories.size() == 1 ? categories.iterator().next() : null;
    }

    private ResolutionPlan buildResolutionPlan(
            InvTransferDiscrepancy discrepancy,
            InvTransferDiscrepancyResolveRequest request,
            List<InvTransferDiscrepancyResolutionItem> items,
            Map<Long, InvTransferDiscrepancyDetail> detailById,
            Map<Long, InvTransferShipmentDetail> shipmentById,
            List<InvTransferDiscrepancyDisposition> latestRows,
            InvTransferOrder order)
    {
        Map<String, InvTransferDiscrepancyDisposition> latestByKey =
                new HashMap<>();
        for (InvTransferDiscrepancyDisposition row : latestRows)
        {
            latestByKey.put(key(row.getDiscrepancyDetailId(),
                    row.getCategory()), row);
        }
        Map<String, BigDecimal> expected = expectedQuantities(discrepancy,
                detailById, latestByKey);
        if (expected.isEmpty())
        {
            throw new ServiceException("差异单无待处置类别");
        }
        if (items.size() != expected.size())
        {
            throw new ServiceException(
                    "必须一次提交全部待处置明细和类别");
        }

        List<InvTransferDiscrepancyDisposition> newRows =
                new ArrayList<>();
        Set<String> requestKeys = new HashSet<>();
        Long sourceLocation = resolveStockLocationDeptId(
                order.getFromWarehouseId(), order.getFromDeptId());
        Long targetLocation = resolveStockLocationDeptId(
                order.getToWarehouseId(), order.getToDeptId());
        String responsibleParty = normalizeResponsibleParty(
                request.getResponsibleParty());
        Date handledTime = new Date();

        for (InvTransferDiscrepancyResolutionItem item : items)
        {
            if (item == null || item.getDetailId() == null)
            {
                throw new ServiceException("差异明细ID不能为空");
            }
            String category = InvTransferDiscrepancyDecisions
                    .requireCategory(item.getCategory());
            String itemKey = key(item.getDetailId(), category);
            if (!requestKeys.add(itemKey))
            {
                throw new ServiceException("同一差异明细类别不能重复处置");
            }
            InvTransferDiscrepancyDetail detail = detailById.get(
                    item.getDetailId());
            if (detail == null)
            {
                throw new ServiceException("差异明细不属于当前差异单");
            }
            BigDecimal expectedQuantity = expected.get(itemKey);
            if (expectedQuantity == null)
            {
                throw new ServiceException("差异明细类别已处理或数量为0");
            }
            if (item.getQuantity() == null
                    || item.getQuantity().compareTo(BigDecimal.ZERO) <= 0
                    || item.getQuantity().compareTo(expectedQuantity) != 0)
            {
                throw new ServiceException("每个差异明细类别必须按完整数量处置");
            }
            InvTransferDiscrepancyDisposition previous =
                    latestByKey.get(itemKey);
            boolean pendingQc = previous != null
                    ? InvTransferDiscrepancyDecisions.PENDING_QC.equals(
                            previous.getDecision())
                    : InvTransferDiscrepancyDecisions.PENDING_QC.equals(
                            discrepancy.getStatus());
            String attachmentRefs = firstNonBlank(item.getAttachmentRefs(),
                    detail.getAttachmentRefs());
            boolean explicitEvidence = !isBlank(attachmentRefs)
                    && !isBlank(firstNonBlank(item.getNote(),
                            request.getNote()));
            validateResolutionText(item.getNote(), 1000,
                    "差异明细说明过长");
            validateResolutionText(attachmentRefs, 2000,
                    "差异附件引用过长");
            String decision = InvTransferDiscrepancyDecisions.requireAllowed(
                    category, item.getDecision(), pendingQc,
                    explicitEvidence);

            boolean requiresControlledEvidence = "DAMAGED".equals(category) && !pendingQc
                    && Set.of("RETURN_SOURCE", "WRITE_OFF").contains(decision);
            resources.evidenceService.validateResolution(attachmentRefs,
                    detail.getAttachmentRefs(), previous == null ? null : previous.getAttachmentRefs(),
                    requiresControlledEvidence);

            InvTransferShipmentDetail shipmentDetail = shipmentById.get(
                    detail.getShipmentDetailId());
            if (shipmentDetail == null
                    || !Objects.equals(shipmentDetail.getTransferDetailId(),
                            detail.getTransferDetailId()))
            {
                throw new ServiceException("差异明细未匹配到原发货批次");
            }
            BigDecimal costPrice = requireFrozenCost(shipmentDetail);
            InvTransferDiscrepancyDisposition row =
                    new InvTransferDiscrepancyDisposition();
            row.setDiscrepancyId(discrepancy.getDiscrepancyId());
            row.setDiscrepancyDetailId(detail.getDiscrepancyDetailId());
            row.setShipmentDetailId(detail.getShipmentDetailId());
            row.setCategory(category);
            row.setDecision(decision);
            row.setQuantity(item.getQuantity());
            row.setCostPrice(costPrice);
            row.setAmount(item.getQuantity().multiply(costPrice)
                    .setScale(2, RoundingMode.HALF_UP));
            row.setInventoryImpact(inventoryImpact(decision));
            row.setSourceLocationDeptId(sourceLocation);
            row.setTargetLocationDeptId(targetLocation);
            row.setResponsibleParty(responsibleParty);
            row.setNote(firstNonBlank(item.getNote(), request.getNote()));
            row.setAttachmentRefs(attachmentRefs);
            row.setRequestId(request.getRequestId());
            row.setHandledByUserId(SecurityUtils.getUserId());
            row.setHandledByName(SecurityUtils.getUsername());
            row.setHandledTime(handledTime);
            row.setVersion(0L);
            newRows.add(row);
        }
        if (!requestKeys.equals(expected.keySet()))
        {
            throw new ServiceException(
                    "必须一次提交全部待处置明细和类别");
        }

        Map<String, InvTransferDiscrepancyDisposition> resulting =
                new LinkedHashMap<>(latestByKey);
        for (InvTransferDiscrepancyDisposition row : newRows)
        {
            resulting.put(key(row.getDiscrepancyDetailId(),
                    row.getCategory()), row);
        }
        boolean pendingQc = resulting.values().stream().anyMatch(row ->
                InvTransferDiscrepancyDecisions.PENDING_QC.equals(
                        row.getDecision()));
        String targetStatus = pendingQc
                ? InvTransferDiscrepancyDecisions.PENDING_QC
                : InvTransferDiscrepancyDecisions.RESOLVED;
        Set<String> decisions = resulting.values().stream()
                .map(InvTransferDiscrepancyDisposition::getDecision)
                .collect(Collectors.toSet());
        String summaryDecision = decisions.size() == 1
                ? decisions.iterator().next() : "MIXED";
        return new ResolutionPlan(newRows, targetStatus,
                summaryDecision);
    }

    private Map<String, BigDecimal> expectedQuantities(
            InvTransferDiscrepancy discrepancy,
            Map<Long, InvTransferDiscrepancyDetail> detailById,
            Map<String, InvTransferDiscrepancyDisposition> latestByKey)
    {
        Map<String, BigDecimal> expected = new LinkedHashMap<>();
        boolean pendingHeader = InvTransferDiscrepancyDecisions.PENDING_QC
                .equals(discrepancy.getStatus());
        for (InvTransferDiscrepancyDetail detail : detailById.values())
        {
            for (String category : List.of(
                    InvTransferDiscrepancyDecisions.SHORTAGE,
                    InvTransferDiscrepancyDecisions.REJECTED,
                    InvTransferDiscrepancyDecisions.DAMAGED))
            {
                BigDecimal quantity = categoryQuantity(detail, category);
                if (quantity.compareTo(BigDecimal.ZERO) <= 0)
                {
                    continue;
                }
                InvTransferDiscrepancyDisposition latest = latestByKey.get(
                        key(detail.getDiscrepancyDetailId(), category));
                if (pendingHeader)
                {
                    if (latest != null
                            && InvTransferDiscrepancyDecisions.PENDING_QC
                                    .equals(latest.getDecision()))
                    {
                        expected.put(key(detail.getDiscrepancyDetailId(),
                                category), quantity);
                    }
                }
                else if (latest == null)
                {
                    expected.put(key(detail.getDiscrepancyDetailId(),
                            category), quantity);
                }
            }
        }
        return expected;
    }

    private void assertExactReplay(
            InvTransferDiscrepancyResolveRequest request,
            List<InvTransferDiscrepancyResolutionItem> items,
            List<InvTransferDiscrepancyDetail> details,
            List<InvTransferDiscrepancyDisposition> replayRows)
    {
        if (items.size() != replayRows.size())
        {
            throw requestConflict();
        }
        Map<String, InvTransferDiscrepancyDisposition> rows = replayRows
                .stream().collect(Collectors.toMap(row -> key(
                        row.getDiscrepancyDetailId(), row.getCategory()),
                        row -> row));
        String responsible = normalizeResponsibleParty(
                request.getResponsibleParty());
        Map<Long, InvTransferDiscrepancyDetail> detailById = details.stream()
                .collect(Collectors.toMap(
                        InvTransferDiscrepancyDetail::getDiscrepancyDetailId,
                        detail -> detail));
        Set<String> replayKeys = new HashSet<>();
        for (InvTransferDiscrepancyResolutionItem item : items)
        {
            if (item == null || item.getDetailId() == null)
            {
                throw requestConflict();
            }
            String category = InvTransferDiscrepancyDecisions
                    .requireCategory(item.getCategory());
            String itemKey = key(item.getDetailId(), category);
            if (!replayKeys.add(itemKey))
            {
                throw requestConflict();
            }
            InvTransferDiscrepancyDisposition row = rows.get(itemKey);
            String itemNote = firstNonBlank(item.getNote(),
                    request.getNote());
            InvTransferDiscrepancyDetail detail = detailById.get(
                    item.getDetailId());
            String attachmentRefs = firstNonBlank(
                    item.getAttachmentRefs(),
                    detail == null ? null : detail.getAttachmentRefs());
            if (row == null
                    || !Objects.equals(row.getDecision(), normalize(
                            item.getDecision()))
                    || item.getQuantity() == null
                    || row.getQuantity().compareTo(item.getQuantity()) != 0
                    || !Objects.equals(row.getResponsibleParty(), responsible)
                    || !Objects.equals(row.getNote(), itemNote)
                    || !Objects.equals(emptyToNull(row.getAttachmentRefs()),
                            emptyToNull(attachmentRefs)))
            {
                throw requestConflict();
            }
        }
        if (!replayKeys.equals(rows.keySet()))
        {
            throw requestConflict();
        }
    }

    private void reserveReshipmentQuantities(InvTransferOrder order,
            InvTransferDiscrepancy discrepancy, ResolutionPlan plan)
    {
        Map<Long, InvTransferShipmentDetail> shipmentDetails = resources.transferShipmentDetailMapper
                .selectByShipmentId(discrepancy.getShipmentId()).stream()
                .collect(Collectors.toMap(InvTransferShipmentDetail::getShipmentDetailId, detail -> detail));
        Map<Long, BigDecimal> quantities = new LinkedHashMap<>();
        for (InvTransferDiscrepancyDisposition row : plan.newRows())
        {
            if (!InvTransferDiscrepancyDecisions.RESHIP.equals(row.getDecision()))
            {
                continue;
            }
            InvTransferShipmentDetail shipment = shipmentDetails.get(row.getShipmentDetailId());
            if (!InvTransferDiscrepancyDecisions.SHORTAGE.equals(row.getCategory())
                    || shipment == null || shipment.getTransferDetailId() == null)
            {
                throw new ServiceException("补发处置未匹配到原调拨明细");
            }
            quantities.merge(shipment.getTransferDetailId(), row.getQuantity(), BigDecimal::add);
        }
        if (!quantities.isEmpty())
        {
            List<InvTransferDetail> details = resources.transferDetailMapper
                    .selectByTransferIdForUpdate(order.getTransferId());
            resources.transferReservationService.reserveForReshipments(order, details,
                    quantities, SecurityUtils.getUsername());
        }
    }

    private void applyInventoryEffects(InvTransferOrder order,
            InvTransferDiscrepancy discrepancy, ResolutionPlan plan)
    {
        Map<Long, InvTransferDetail> transferDetails = resources
                .transferDetailMapper.selectByTransferIdForUpdate(
                        order.getTransferId()).stream()
                .collect(Collectors.toMap(InvTransferDetail::getDetailId,
                        detail -> detail));
        Map<Long, InvTransferShipmentDetail> shipmentDetails = resources
                .transferShipmentDetailMapper.selectByShipmentId(
                        discrepancy.getShipmentId())
                .stream().collect(Collectors.toMap(
                        InvTransferShipmentDetail::getShipmentDetailId,
                        detail -> detail));
        Long sourceLocation = resolveStockLocationDeptId(
                order.getFromWarehouseId(), order.getFromDeptId());
        for (InvTransferDiscrepancyDisposition row : plan.newRows())
        {
            InvTransferShipmentDetail shipmentDetail = shipmentDetails.get(
                    row.getShipmentDetailId());
            if (shipmentDetail == null)
            {
                throw new ServiceException("差异明细未匹配到原发货批次");
            }
            if (InvTransferDiscrepancyDecisions.RESHIP.equals(
                    row.getDecision()))
            {
                releaseDiscrepancyQuantityForReship(transferDetails,
                        shipmentDetail, row);
            }
            else if (InvTransferDiscrepancyDecisions.RETURN_SOURCE.equals(
                    row.getDecision()))
            {
                returnRejectedStockToSource(order, shipmentDetail, row,
                        sourceLocation);
            }
        }
    }

    /** Releases only the outstanding transfer quantity; no source stock is added. */
    private void releaseDiscrepancyQuantityForReship(
            Map<Long, InvTransferDetail> transferDetails,
            InvTransferShipmentDetail shipmentDetail,
            InvTransferDiscrepancyDisposition row)
    {
        InvTransferDetail transferDetail = transferDetails.get(
                shipmentDetail.getTransferDetailId());
        if (transferDetail == null
                || nullToZero(transferDetail.getDeliveredQuantity())
                        .compareTo(row.getQuantity()) < 0)
        {
            throw new ServiceException("差异补发数量与调拨明细不一致");
        }
        InvTransferDetail decrease = new InvTransferDetail();
        decrease.setDetailId(transferDetail.getDetailId());
        decrease.setDeliveredQuantity(row.getQuantity());
        if (resources.transferDetailMapper
                .decreaseDeliveredQuantity(decrease) == 0)
        {
            throw new ServiceException("释放待补发数量失败，请刷新后重试");
        }
    }

    /** Returns physical rejected/damaged goods using the shipment cost snapshot. */
    private void returnRejectedStockToSource(InvTransferOrder order,
            InvTransferShipmentDetail shipmentDetail,
            InvTransferDiscrepancyDisposition row, Long sourceLocation)
    {
        String itemType = InvItemTypes.normalize(shipmentDetail.getItemType());
        Long itemId = InvItemTypes.resolveItemId(itemType,
                shipmentDetail.getItemId(), shipmentDetail.getProductId());
        addStockAtLocationAndLog(order, itemType, itemId,
                shipmentDetail.getProductId(), sourceLocation,
                row.getQuantity(), row.getCostPrice(),
                "差异" + categoryLabel(row.getCategory()) + "退回来源←"
                        + order.getToDeptName());
    }

    private void finishResolvedDiscrepancy(InvTransferOrder locked,
            InvTransferDiscrepancy discrepancy, ResolutionPlan plan,
            String note)
    {
        InvTransferShipment shipmentUpdate = new InvTransferShipment();
        shipmentUpdate.setShipmentId(discrepancy.getShipmentId());
        shipmentUpdate.setStatus(InvStatusConstants.RECEIVED);
        shipmentUpdate.setUpdateBy(SecurityUtils.getUsername());
        resources.transferShipmentMapper.updateShipment(shipmentUpdate);

        int remainingOpen = resources.transferDiscrepancyMapper
                .countOpenByTransferId(locked.getTransferId(), null);
        String orderStatus;
        if (remainingOpen > 0)
        {
            orderStatus = InvStatusConstants.DISCREPANCY;
        }
        else
        {
            List<InvTransferDetail> orderDetails = resources
                    .transferDetailMapper.selectByTransferId(
                            locked.getTransferId());
            if (!allRequestedDelivered(orderDetails))
            {
                orderStatus = InvStatusConstants.PARTIAL_DELIVERED;
            }
            else if (hasPendingReceiveShipment(locked.getTransferId()))
            {
                orderStatus = InvStatusConstants.PARTIAL_RECEIVED;
            }
            else
            {
                orderStatus = allRequestedReceived(orderDetails)
                        ? InvStatusConstants.RECEIVED
                        : InvStatusConstants.CLOSED;
            }
        }
        InvTransferOrder orderUpdate = new InvTransferOrder();
        orderUpdate.setTransferId(locked.getTransferId());
        orderUpdate.setStatus(orderStatus);
        if (InvStatusConstants.RECEIVED.equals(orderStatus)
                || InvStatusConstants.CLOSED.equals(orderStatus))
        {
            orderUpdate.setArchivedTime(new Date());
            if (InvStatusConstants.CLOSED.equals(orderStatus))
            {
                orderUpdate.setCloseReason("差异逐项处置："
                        + plan.summaryDecision() + "；" + note);
            }
        }
        orderUpdate.setUpdateBy(SecurityUtils.getUsername());
        resources.transferOrderMapper.updateInvTransferOrder(orderUpdate);
        writeStatusLog(locked.getTransferId(),
                InvStatusConstants.DISCREPANCY, orderStatus,
                "resolve_discrepancy", "差异单 "
                        + discrepancy.getDiscrepancyNo()
                        + " 已按明细处置");
        recordTransferDiscrepancy(plan.summaryDecision());
    }

    private void assertQuantityConservation(
            InvTransferDiscrepancy discrepancy,
            List<InvTransferDiscrepancyDetail> details)
    {
        BigDecimal shipped = BigDecimal.ZERO;
        BigDecimal accepted = BigDecimal.ZERO;
        BigDecimal shortage = BigDecimal.ZERO;
        BigDecimal rejected = BigDecimal.ZERO;
        BigDecimal damaged = BigDecimal.ZERO;
        for (InvTransferDiscrepancyDetail detail : details)
        {
            BigDecimal detailShipped = requireNonNegative(
                    detail.getShippedQuantity(), "差异发货数量异常");
            BigDecimal detailAccepted = requireNonNegative(
                    detail.getAcceptedQuantity(), "差异实收数量异常");
            BigDecimal detailShortage = requireNonNegative(
                    detail.getShortageQuantity(), "差异短少数量异常");
            BigDecimal detailRejected = requireNonNegative(
                    detail.getRejectedQuantity(), "差异拒收数量异常");
            BigDecimal detailDamaged = requireNonNegative(
                    detail.getDamagedQuantity(), "差异残损数量异常");
            if (detailShipped.compareTo(detailAccepted
                    .add(detailShortage).add(detailRejected)
                    .add(detailDamaged)) != 0)
            {
                throw new ServiceException("差异明细数量不守恒，禁止处置");
            }
            shipped = shipped.add(detailShipped);
            accepted = accepted.add(detailAccepted);
            shortage = shortage.add(detailShortage);
            rejected = rejected.add(detailRejected);
            damaged = damaged.add(detailDamaged);
        }
        BigDecimal headerShipped = requireNonNegative(
                discrepancy.getShippedQuantity(), "差异主表发货数量异常");
        BigDecimal headerAccepted = requireNonNegative(
                discrepancy.getAcceptedQuantity(), "差异主表实收数量异常");
        BigDecimal headerShortage = requireNonNegative(
                discrepancy.getShortageQuantity(), "差异主表短少数量异常");
        BigDecimal headerRejected = requireNonNegative(
                discrepancy.getRejectedQuantity(), "差异主表拒收数量异常");
        BigDecimal headerDamaged = requireNonNegative(
                discrepancy.getDamagedQuantity(), "差异主表残损数量异常");
        BigDecimal omittedShipped = headerShipped.subtract(shipped);
        BigDecimal omittedAccepted = headerAccepted.subtract(accepted);
        if (headerShipped.compareTo(headerAccepted.add(headerShortage)
                        .add(headerRejected).add(headerDamaged)) != 0
                || headerShortage
                        .compareTo(shortage) != 0
                || headerRejected
                        .compareTo(rejected) != 0
                || headerDamaged.compareTo(damaged) != 0
                || omittedShipped.compareTo(BigDecimal.ZERO) < 0
                || omittedAccepted.compareTo(BigDecimal.ZERO) < 0
                || omittedShipped.compareTo(omittedAccepted) != 0)
        {
            throw new ServiceException("差异主表与明细数量不一致，禁止处置");
        }
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

    private BigDecimal requireFrozenCost(
            InvTransferShipmentDetail shipmentDetail)
    {
        if (shipmentDetail.getCostPrice() == null
                || shipmentDetail.getCostPrice()
                        .compareTo(BigDecimal.ZERO) < 0)
        {
            throw new ServiceException("原发货批次成本未冻结，禁止差异处置");
        }
        return shipmentDetail.getCostPrice();
    }

    private BigDecimal requireNonNegative(BigDecimal value, String message)
    {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0)
        {
            throw new ServiceException(message);
        }
        return value;
    }

    private static BigDecimal categoryQuantity(
            InvTransferDiscrepancyDetail detail, String category)
    {
        return switch (category)
        {
            case InvTransferDiscrepancyDecisions.SHORTAGE ->
                    nullToZero(detail.getShortageQuantity());
            case InvTransferDiscrepancyDecisions.REJECTED ->
                    nullToZero(detail.getRejectedQuantity());
            case InvTransferDiscrepancyDecisions.DAMAGED ->
                    nullToZero(detail.getDamagedQuantity());
            default -> BigDecimal.ZERO;
        };
    }

    private String normalizeResponsibleParty(String value)
    {
        String normalized = normalize(value);
        if (!RESPONSIBLE_PARTIES.contains(normalized))
        {
            throw new ServiceException("请选择有效责任方");
        }
        return normalized;
    }

    private static String inventoryImpact(String decision)
    {
        if (InvTransferDiscrepancyDecisions.RETURN_SOURCE.equals(decision))
        {
            return SOURCE_STOCK_IN;
        }
        if (InvTransferDiscrepancyDecisions.PENDING_QC.equals(decision))
        {
            return QC_HOLD;
        }
        return NO_STOCK_CHANGE;
    }

    private static String categoryLabel(String category)
    {
        return switch (category)
        {
            case InvTransferDiscrepancyDecisions.REJECTED -> "拒收";
            case InvTransferDiscrepancyDecisions.DAMAGED -> "残损";
            default -> "短少";
        };
    }

    private static String key(Long detailId, String category)
    {
        return detailId + ":" + normalize(category);
    }

    private static String normalize(String value)
    {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private static String firstNonBlank(String first, String fallback)
    {
        return isBlank(first) ? (isBlank(fallback) ? null : fallback.trim())
                : first.trim();
    }

    private static String emptyToNull(String value)
    {
        return isBlank(value) ? null : value.trim();
    }

    private static void validateResolutionText(String value, int maxLength,
            String message)
    {
        if (value != null && value.trim().length() > maxLength)
        {
            throw new ServiceException(message);
        }
    }

    private static ServiceException concurrentResolution()
    {
        return new ServiceException("差异单已被其他人处理，请刷新后重试");
    }

    private static ServiceException requestConflict()
    {
        return new ServiceException("requestId 已用于不同的差异处置内容");
    }

    private record ResolutionPlan(
            List<InvTransferDiscrepancyDisposition> newRows,
            String targetStatus,
            String summaryDecision)
    {
    }
}
