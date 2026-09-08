package com.erp.inventory.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.erp.approval.api.domain.ApprovalStartRequest;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.constant.InvItemTypes;
import com.erp.inventory.constant.InvStatusConstants;
import com.erp.inventory.constant.InvTransferSourceConfirmStatus;
import com.erp.inventory.constant.InvTransferTypes;
import com.erp.inventory.domain.InvStock;
import com.erp.inventory.domain.InvStockLog;
import com.erp.inventory.domain.InvTransferApprovalStartOutbox;
import com.erp.inventory.domain.InvTransferApprovalInstance;
import com.erp.inventory.domain.InvTransferDetail;
import com.erp.inventory.domain.InvTransferDiscrepancy;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.InvTransferShipment;
import com.erp.inventory.domain.InvTransferShipmentDetail;
import com.erp.inventory.domain.InvTransferStatusLog;
import com.erp.inventory.domain.transfer.InvTransferLifecyclePolicy;
import com.erp.inventory.domain.transfer.InvTransferQuantityPolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy;
import com.erp.inventory.domain.dto.InvDeliverItem;
import com.erp.inventory.domain.dto.InvDeliverRequest;
import com.erp.inventory.domain.dto.InvReceiveRequest;
import com.erp.inventory.domain.dto.InvTransferDiscrepancyResolveRequest;
import com.erp.inventory.domain.dto.InvTransferSourceConfirmItem;
import com.erp.inventory.domain.dto.InvTransferSourceConfirmRequest;
import com.erp.inventory.domain.vo.InvTransferApprovalSummary;
import com.erp.inventory.domain.vo.InvTransferApprovalTrack;
import com.erp.inventory.domain.vo.InvTransferOpsSummaryVo;
import com.erp.inventory.domain.vo.InvTransferRevisionHistoryVo;
import com.erp.inventory.domain.vo.InvTransferSourceConfirmResult;
import com.erp.inventory.domain.vo.InventoryItemSnapshot;
import com.erp.inventory.mapper.InvNumberSequenceMapper;
import com.erp.inventory.mapper.InvStockLogMapper;
import com.erp.inventory.mapper.InvStockMapper;
import com.erp.inventory.mapper.InvTransferDetailMapper;
import com.erp.inventory.mapper.InvTransferDiscrepancyMapper;
import com.erp.inventory.mapper.InvTransferDiscrepancyDispositionMapper;
import com.erp.inventory.mapper.InvTransferOrderMapper;
import com.erp.inventory.mapper.InvTransferShipmentDetailMapper;
import com.erp.inventory.mapper.InvTransferShipmentMapper;
import com.erp.inventory.mapper.InvTransferStatusLogMapper;
import com.erp.inventory.metric.InventoryBusinessMetrics;
import com.erp.inventory.service.BusinessFeatureGate;
import com.erp.inventory.service.IInvTransferApprovalService;
import com.erp.inventory.service.IInvTransferService;
import com.erp.system.api.domain.SysUser;

@Service
public class InvTransferServiceImpl extends InvBaseService implements IInvTransferService
{
    @Autowired
    private InvTransferOrderMapper transferOrderMapper;

    @Autowired
    private InvTransferDetailMapper transferDetailMapper;

    @Autowired
    private InvTransferDiscrepancyMapper transferDiscrepancyMapper;

    @Autowired
    private InvTransferDiscrepancyDispositionMapper transferDiscrepancyDispositionMapper;

    @Autowired
    private InvTransferShipmentMapper transferShipmentMapper;

    @Autowired
    private InvTransferShipmentDetailMapper transferShipmentDetailMapper;

    @Autowired
    private InvStockMapper stockMapper;

    @Autowired
    private InvTransferReservationService transferReservationService;

    @Autowired
    private InvTransferRevisionService transferRevisionService;

    @Autowired
    private InvStockLogMapper stockLogMapper;

    @Autowired
    private InventoryItemResolver itemResolver;

    @Autowired
    private InvNumberSequenceMapper numberSequenceMapper;

    @Autowired
    private IInvTransferApprovalService transferApprovalService;

    @Autowired
    private InventoryUnifiedApprovalService unifiedApprovalService;

    @Autowired
    private InvTransferApprovalStartOutboxService approvalStartOutboxService;

    @Autowired
    private InvTransferApprovalStartAfterCommitTrigger approvalStartAfterCommitTrigger;

    @Autowired
    private InvTransferStatusLogMapper statusLogMapper;

    @Autowired
    private BusinessFeatureGate businessFeatureGate;

    @Autowired(required = false)
    private InventoryBusinessMetrics businessMetrics;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvTransferOrder saveDraft(InvTransferOrder order, List<InvTransferDetail> details, Long selectedShopDeptId)
    {
        return saveDraft(order, details, selectedShopDeptId, true, false);
    }

    private InvTransferOrder saveDraft(InvTransferOrder order, List<InvTransferDetail> details, Long selectedShopDeptId,
            boolean validateTargetDept, boolean salesDeliveryInTransit)
    {
        rejectRetiredOeReplenishmentSource(order);
        validateSalesDeliverySource(order, salesDeliveryInTransit);
        requireStoreReturnEntryEnabled(order, false);
        Long shopDeptId = resolveAndValidateShopDept(selectedShopDeptId);
        order.setTransferType(InvTransferTypes.requireSupported(
                order.getTransferType()));
        InvTransferOrder existing = null;
        if (order.getTransferId() != null)
        {
            existing = assertAndGetScopedTransfer(order.getTransferId(),
                    selectedShopDeptId);
            if (!order.getTransferType().equals(
                    InvTransferTypes.requireSupported(
                            existing.getTransferType())))
            {
                throw new ServiceException("调拨类型创建后不能修改");
            }
            rejectRetiredOeReplenishmentSource(existing);
            validateSalesDeliverySource(existing, salesDeliveryInTransit);
        }
        List<InvTransferDetail> detailsToValidate = details;
        if (detailsToValidate == null && order.getTransferId() != null)
        {
            detailsToValidate = transferDetailMapper.selectByTransferId(
                    order.getTransferId());
        }
        validateTransferItemTypes(detailsToValidate,
                order.getTransferType());
        fillAndValidateDeptNames(order, shopDeptId, selectedShopDeptId,
                validateTargetDept);
        validateTransferCreationRules(order, shopDeptId,
                salesDeliveryInTransit);
        // 补货商品归属始终以实际来源仓库为锚点；不能使用目标门店
        // 的商品范围替代来源资格。
        Set<Long> itemOwnerScopeDeptIds = sourceItemScopePolicy()
                .resolveOwnerScopeDeptIds(order);
        if (details == null && detailsToValidate != null)
        {
            validatePersistedTransferItems(detailsToValidate,
                    order.getTransferType(), itemOwnerScopeDeptIds);
        }
        prepareSourceConfirmationForDraft(order, existing);
        if (order.getTransferId() == null)
        {
            TransferTotals totals = details != null && !details.isEmpty()
                    ? prepareDetails(details, null,
                            order.getTransferType(),
                            itemOwnerScopeDeptIds, salesDeliveryInTransit)
                    : null;
            order.setCreateBy(SecurityUtils.getUsername());
            applyCreatedBySnapshot(order);
            // 草稿创建不接受客户端伪造的提交人和提交时间快照。
            order.setSubmittedTime(null);
            order.setSubmittedByUserId(null);
            order.setSubmittedByName(null);
            order.setStatus(InvStatusConstants.DRAFT);
            order.setOrderNo(generateOrderNo("TF"));
            order.setTotalQuantity(BigDecimal.ZERO);
            order.setTotalAmount(BigDecimal.ZERO);
            transferOrderMapper.insertInvTransferOrder(order);
            if (totals != null)
            {
                for (InvTransferDetail detail : details)
                {
                    detail.setTransferId(order.getTransferId());
                }
                transferDetailMapper.batchInsertInvTransferDetail(details);
                order.setTotalQuantity(totals.totalQuantity);
                order.setTotalAmount(totals.totalAmount);
                InvTransferOrder update = new InvTransferOrder();
                update.setTransferId(order.getTransferId());
                update.setTotalQuantity(totals.totalQuantity);
                update.setTotalAmount(totals.totalAmount);
                update.setUpdateBy(SecurityUtils.getUsername());
                transferOrderMapper.updateInvTransferOrder(update);
            }
        }
        else
        {
            InvStateGuard.requireDraftForEdit(existing.getStatus());
            Long expectedVersion = order.getVersion() == null
                    ? existing.getVersion() : order.getVersion();
            if (expectedVersion == null)
            {
                expectedVersion = 0L;
            }
            order.setVersion(expectedVersion);
            order.setUpdateBy(SecurityUtils.getUsername());
            if (details != null)
            {
                TransferTotals totals = prepareDetails(details,
                        order.getTransferId(), order.getTransferType(),
                        itemOwnerScopeDeptIds, salesDeliveryInTransit);
                order.setTotalQuantity(totals.totalQuantity);
                order.setTotalAmount(totals.totalAmount);
            }
            if (transferOrderMapper.updateDraftIfVersionMatches(order) != 1)
            {
                throw new ServiceException("调拨单已被其他人修改或状态已变化，请刷新后重试");
            }
            order.setVersion(expectedVersion + 1);
            if (details != null)
            {
                transferDetailMapper.deleteByTransferId(order.getTransferId());
                if (!details.isEmpty())
                {
                    transferDetailMapper.batchInsertInvTransferDetail(details);
                }
            }
        }
        InvTransferOrder persisted = transferOrderMapper
                .selectInvTransferOrderById(order.getTransferId());
        List<InvTransferDetail> persistedDetails = transferDetailMapper
                .selectByTransferId(order.getTransferId());
        transferRevisionService.syncDraft(persisted, persistedDetails,
                SecurityUtils.getUsername());
        return persisted;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvTransferOrder submitTransfer(InvTransferOrder order, List<InvTransferDetail> details, Long selectedShopDeptId)
    {
        return submitTransfer(order, details, selectedShopDeptId, true, false);
    }

    private InvTransferOrder submitTransfer(InvTransferOrder order, List<InvTransferDetail> details, Long selectedShopDeptId,
            boolean validateTargetDept, boolean salesDeliveryInTransit)
    {
        requireStoreReturnEntryEnabled(order, true);
        InvTransferOrder saved = saveDraft(order, details,
                selectedShopDeptId, validateTargetDept,
                salesDeliveryInTransit);
        List<InvTransferDetail> savedDetails = transferDetailMapper.selectByTransferId(saved.getTransferId());
        if (savedDetails == null || savedDetails.isEmpty())
        {
            throw new ServiceException("请至少添加一条调拨明细");
        }
        validatePersistedTransferItems(savedDetails,
                saved.getTransferType(), sourceItemScopePolicy()
                        .resolveOwnerScopeDeptIds(saved));
        requireSourceReselectionComplete(saved);
        transferReservationService.reserveForSubmission(saved, savedDetails,
                SecurityUtils.getUsername());
        return finalizeReservedSubmission(saved, savedDetails);
    }

    /**
     * Internal-only submission seam for an adjudication return whose V2
     * quarantine reservation was already created by its sole effect owner.
     */
    @Transactional(propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class)
    public InvTransferOrder finalizeAdjudicationReturnSubmission(
            InvTransferOrder saved, List<InvTransferDetail> savedDetails)
    {
        if (saved == null || saved.getTransferId() == null
                || saved.getTransferId() <= 0
                || !InvStatusConstants.DRAFT.equals(saved.getStatus())
                || !InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
                        .SOURCE_RETURN.equals(saved.getSourceBusinessType())
                || saved.getSourceBusinessId() == null
                || saved.getSourceBusinessId() <= 0
                || savedDetails == null || savedDetails.size() != 1
                || savedDetails.get(0) == null
                || !saved.getTransferId().equals(
                        savedDetails.get(0).getTransferId()))
        {
            throw new ServiceException("退回子调拨隔离预留提交事实无效");
        }
        return finalizeReservedSubmission(saved, savedDetails);
    }

    private InvTransferOrder finalizeReservedSubmission(
            InvTransferOrder saved, List<InvTransferDetail> savedDetails)
    {
        String previousStatus = saved.getStatus();
        int nextRound = (saved.getApprovalRound() == null
                ? 0 : saved.getApprovalRound()) + 1;
        InvTransferApprovalInstance instance = null;
        ApprovalStartRequest nativeRequest = null;
        if (unifiedApprovalService.useNativeTransfer(saved))
        {
            nativeRequest = unifiedApprovalService
                    .buildTransferStartRequest(saved, nextRound);
        }
        else
        {
            instance = transferApprovalService.createInstanceForSubmit(saved);
        }
        boolean autoApproved = instance != null
                && "approved".equals(instance.getStatus());
        String targetStatus = autoApproved ? InvStatusConstants.APPROVED : InvStatusConstants.SUBMITTED;
        transferRevisionService.sealForSubmission(saved, savedDetails,
                nextRound, instance == null ? null : instance.getInstanceId(),
                autoApproved, SecurityUtils.getUserId(),
                SecurityUtils.getUsername());
        InvTransferOrder update = new InvTransferOrder();
        update.setTransferId(saved.getTransferId());
        update.setStatus(targetStatus);
        if (autoApproved && requiresSourceConfirmation(saved))
        {
            update.setSourceConfirmStatus(
                    InvTransferSourceConfirmStatus.PENDING);
        }
        if (nativeRequest != null)
        {
            update.setApprovalRound(nativeRequest.getBusinessRound());
            update.setApprovalEngine(
                    InventoryUnifiedApprovalService.ENGINE_NATIVE);
        }
        else
        {
            update.setApprovalInstanceId(instance.getInstanceId());
            update.setApprovalRound(nextRound);
            update.setApprovalEngine(
                    InventoryUnifiedApprovalService.ENGINE_LEGACY);
        }
        update.setSubmittedTime(new Date());
        applySubmittedBySnapshot(update);
        if (autoApproved)
        {
            update.setApprovedTime(new Date());
        }
        update.setUpdateBy(SecurityUtils.getUsername());
        transferOrderMapper.updateInvTransferOrder(update);
        InvTransferOrder submitted = transferOrderMapper
                .selectInvTransferOrderById(saved.getTransferId());
        InvTransferApprovalStartOutbox approvalStartOutbox = null;
        if (nativeRequest != null)
        {
            approvalStartOutbox = approvalStartOutboxService.enqueue(submitted,
                    nativeRequest, SecurityUtils.getUsername());
        }
        InvTransferStatusLog log = new InvTransferStatusLog();
        log.setTransferId(saved.getTransferId());
        log.setFromStatus(previousStatus);
        log.setToStatus(targetStatus);
        log.setAction(autoApproved ? "auto_approve" : "submit");
        log.setOperatorId(SecurityUtils.getUserId());
        log.setOperatorName(SecurityUtils.getUsername());
        List<String> approvalWarnings = instance == null
                ? new ArrayList<>() : instance.getApprovalWarnings();
        String warningText = approvalWarnings == null || approvalWarnings.isEmpty()
                ? "" : "；" + approvalWarnings.stream().collect(Collectors.joining("；"));
        log.setReason((autoApproved ? "提交调拨审批自动通过" : "提交调拨审批") + warningText);
        statusLogMapper.insertLog(log);
        if (approvalStartOutbox != null)
        {
            approvalStartAfterCommitTrigger.trigger(
                    approvalStartOutbox.getOutboxId());
        }
        submitted.setApprovalWarnings(approvalWarnings);
        return submitted;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvTransferOrder createDeliveredCrossStoreTransfer(InvTransferOrder order, List<InvTransferDetail> details,
            Long selectedShopDeptId)
    {
        validateSalesDeliverySource(order, true);
        validateFrozenSalesDeliveryDetails(details);
        InvTransferOrder existing = findSalesDeliveryTransfer(order);
        if (existing != null)
        {
            return requireMatchingSalesDeliveryTransfer(existing, order,
                    details);
        }

        InvTransferOrder saved;
        try
        {
            saved = saveDraft(order, details, selectedShopDeptId, false, true);
        }
        catch (DuplicateKeyException duplicate)
        {
            if (order != null && order.getTransferId() != null)
            {
                throw duplicate;
            }
            existing = findSalesDeliveryTransferForUpdate(order);
            if (existing == null)
            {
                throw duplicate;
            }
            return requireMatchingSalesDeliveryTransfer(existing, order,
                    details);
        }
        List<InvTransferDetail> savedDetails = transferDetailMapper.selectByTransferId(saved.getTransferId());
        if (savedDetails == null || savedDetails.isEmpty())
        {
            throw new ServiceException("请至少添加一条调拨明细");
        }
        validateFrozenSalesDeliveryDetails(savedDetails);

        Long fromWarehouseId = requireWarehouseId(resolveStockLocationDeptId(saved.getFromWarehouseId(), saved.getFromDeptId()),
                "调拨出库仓库不能为空");
        InvTransferShipment shipment = new InvTransferShipment();
        shipment.setTransferId(saved.getTransferId());
        shipment.setShipmentNo(generateOrderNo("TS"));
        shipment.setWarehouseId(fromWarehouseId);
        shipment.setWarehouseDeptId(fromWarehouseId);
        shipment.setSourceLocationDeptId(fromWarehouseId);
        shipment.setStatus(InvStatusConstants.PENDING_RECEIVE);
        shipment.setShippedBy(SecurityUtils.getUsername());
        shipment.setShippedTime(new Date());
        shipment.setCreateBy(SecurityUtils.getUsername());
        shipment.setRemark("销售发货已出库，待目标门店收货");
        transferShipmentMapper.insertShipment(shipment);

        List<InvTransferShipmentDetail> shipmentDetails = new ArrayList<>();
        for (InvTransferDetail detail : savedDetails)
        {
            String itemType = InvItemTypes.normalize(detail.getItemType());
            Long itemId = InvItemTypes.resolveItemId(itemType, detail.getItemId(), detail.getProductId());
            BigDecimal shippedQuantity = nullToZero(detail.getQuantity());
            BigDecimal costPrice = detail.getCostPrice();
            detail.setDeliveredQuantity(shippedQuantity);
            transferDetailMapper.updateDeliveredQuantity(detail);

            InvTransferShipmentDetail shipmentDetail = new InvTransferShipmentDetail();
            shipmentDetail.setShipmentId(shipment.getShipmentId());
            shipmentDetail.setTransferId(saved.getTransferId());
            shipmentDetail.setTransferDetailId(detail.getDetailId());
            shipmentDetail.setItemType(itemType);
            shipmentDetail.setItemId(itemId);
            shipmentDetail.setItemCode(detail.getItemCode());
            shipmentDetail.setItemName(detail.getItemName());
            shipmentDetail.setProductId(detail.getProductId());
            shipmentDetail.setProductName(detail.getProductName());
            shipmentDetail.setPlannedQuantity(detail.getQuantity());
            shipmentDetail.setShippedQuantity(shippedQuantity);
            shipmentDetail.setReceivedQuantity(BigDecimal.ZERO);
            shipmentDetail.setCostPrice(costPrice);
            shipmentDetails.add(shipmentDetail);
        }
        transferShipmentDetailMapper.batchInsertShipmentDetail(shipmentDetails);

        transferRevisionService.sealSystemDelivered(saved, savedDetails,
                SecurityUtils.getUsername());

        String previousStatus = saved.getStatus();
        InvTransferOrder update = new InvTransferOrder();
        update.setTransferId(saved.getTransferId());
        update.setStatus(InvStatusConstants.DELIVERED);
        update.setDeliveredTime(new Date());
        update.setUpdateBy(SecurityUtils.getUsername());
        transferOrderMapper.updateInvTransferOrder(update);
        writeStatusLog(saved.getTransferId(), previousStatus, InvStatusConstants.DELIVERED, "deliver",
                "销售发货已出库，生成待收货调拨批次 " + shipment.getShipmentNo());
        return transferOrderMapper.selectInvTransferOrderById(saved.getTransferId());
    }

    @Override
    public InvTransferOrder getTransferDetail(Long transferId, Long selectedShopDeptId)
    {
        InvTransferOrder order = assertAndGetScopedTransfer(transferId, selectedShopDeptId);
        assertDraftReadScope(order, selectedShopDeptId);
        Map<Long, InvTransferApprovalSummary> summaries = transferApprovalService
                .selectApprovalSummaries(Collections.singletonList(transferId));
        if (summaries != null)
        {
            order.setApprovalSummary(summaries.get(transferId));
        }
        order.setDetails(transferDetailMapper.selectByTransferId(transferId));
        List<InvTransferShipment> shipments = transferShipmentMapper.selectByTransferId(transferId);
        for (InvTransferShipment shipment : shipments)
        {
            shipment.setDetails(transferShipmentDetailMapper.selectByShipmentId(shipment.getShipmentId()));
        }
        order.setShipments(shipments);
        List<InvTransferDiscrepancy> discrepancies = transferDiscrepancyMapper.selectByTransferId(transferId);
        for (InvTransferDiscrepancy discrepancy : discrepancies)
        {
            discrepancy.setDetails(transferDiscrepancyMapper.selectDetails(discrepancy.getDiscrepancyId()));
        }
        order.setDiscrepancies(discrepancies);
        return order;
    }

    @Override
    public InvTransferRevisionHistoryVo getRevisionHistory(Long transferId,
            Long selectedShopDeptId)
    {
        InvTransferOrder order = assertAndGetScopedTransfer(transferId,
                selectedShopDeptId);
        return transferRevisionService.getHistory(order);
    }

    @Override
    public InvTransferApprovalTrack getApprovalTrack(Long transferId, Long selectedShopDeptId)
    {
        InvTransferOrder order = assertAndGetScopedTransfer(transferId, selectedShopDeptId);
        assertDraftReadScope(order, selectedShopDeptId);
        return transferApprovalService.selectTrack(order);
    }

    @Override
    public List<InvTransferOrder> selectTransferList(InvTransferOrder order, Long selectedShopDeptId)
    {
        // 仓库出库管理：按发货仓库(from_dept_id)做数据隔离
        appendTransferScopeToParams(order.getParams(), selectedShopDeptId,
                order.getStoreDeptId());
        List<InvTransferOrder> rows = transferOrderMapper.selectInvTransferOrderList(order);
        if (rows == null || rows.isEmpty())
        {
            return rows;
        }
        // 列表/导出只返回作业字段；身份快照仅允许通过详情接口返回。
        // MyBatis 每次查询创建独立对象，这里只清理当前列表对象，不触碰详情查询对象。
        for (InvTransferOrder row : rows)
        {
            if (row != null)
            {
                row.setCreatedByUserId(null);
                row.setCreatedByName(null);
                row.setSubmittedByUserId(null);
                row.setSubmittedByName(null);
            }
        }
        List<Long> transferIds = rows.stream()
                .filter(row -> row != null && row.getTransferId() != null)
                .map(InvTransferOrder::getTransferId)
                .distinct()
                .collect(Collectors.toList());
        if (transferIds.isEmpty())
        {
            return rows;
        }
        Map<Long, InvTransferApprovalSummary> summaries = transferApprovalService
                .selectApprovalSummaries(transferIds);
        if (summaries != null && !summaries.isEmpty())
        {
            for (InvTransferOrder row : rows)
            {
                if (row != null)
                {
                    row.setApprovalSummary(summaries.get(row.getTransferId()));
                }
            }
        }
        return rows;
    }

    @Override
    public InvTransferOpsSummaryVo selectOpsSummary(Long selectedShopDeptId)
    {
        InvTransferOrder query = new InvTransferOrder();
        appendTransferScopeToParams(query.getParams(), selectedShopDeptId,
                null);
        InvTransferOpsSummaryVo summary =
                transferOrderMapper.selectOpsSummary(query);
        if (summary == null)
        {
            summary = new InvTransferOpsSummaryVo();
        }
        summary.setOrganizationId(selectedShopDeptId);
        return summary;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvTransferSourceConfirmResult confirmSourceTransfer(
            Long transferId, InvTransferSourceConfirmRequest request,
            Long selectedShopDeptId)
    {
        InvTransferOrder order = assertAndGetScopedTransfer(transferId,
                selectedShopDeptId);
        assertTransferDeliverScope(order, selectedShopDeptId);

        InvTransferOrder locked = transferOrderMapper
                .selectInvTransferOrderByIdForUpdate(transferId);
        if (locked == null)
        {
            throw new ServiceException("调拨单不存在");
        }
        assertTransferDeliverScope(locked, selectedShopDeptId);
        assertTransferDeliverDirection(locked, selectedShopDeptId);
        if (!requiresSourceConfirmation(locked))
        {
            throw new ServiceException("该调拨单不需要调出店确认");
        }
        InvStateGuard.require(locked.getStatus(),
                Set.of(InvStatusConstants.APPROVED), "调出店确认");
        String confirmStatus = locked.getSourceConfirmStatus();
        if (confirmStatus != null
                && !InvTransferSourceConfirmStatus.PENDING.equals(
                        confirmStatus)
                && !InvTransferSourceConfirmStatus.NOT_STARTED.equals(
                        confirmStatus))
        {
            throw new ServiceException("调出店已确认或当前状态已变化，请刷新后重试");
        }
        transferApprovalService.assertApprovedForDelivery(locked);

        List<InvTransferDetail> details = transferDetailMapper
                .selectByTransferIdForUpdate(transferId);
        if (details == null || details.isEmpty())
        {
            throw new ServiceException("调拨单无明细");
        }
        Map<Long, BigDecimal> confirmedQuantities =
                validateSourceConfirmRequest(request, details);
        String remark = request == null || request.getRemark() == null
                ? "" : request.getRemark().trim();
        Long sourceWarehouseId = requireWarehouseId(
                resolveStockLocationDeptId(locked.getFromWarehouseId(),
                        locked.getFromDeptId()),
                "异店调货调出库存位置不能为空");
        assertSourceConfirmStockAvailable(details, confirmedQuantities,
                sourceWarehouseId);

        List<InvTransferDetail> acceptedDetails = new ArrayList<>();
        List<InvTransferDetail> remainderDetails = new ArrayList<>();
        BigDecimal acceptedQuantity = BigDecimal.ZERO;
        BigDecimal acceptedAmount = BigDecimal.ZERO;
        BigDecimal remainderQuantity = BigDecimal.ZERO;
        BigDecimal remainderAmount = BigDecimal.ZERO;
        for (InvTransferDetail detail : details)
        {
            BigDecimal requested = nullToZero(detail.getQuantity());
            BigDecimal confirmed = confirmedQuantities
                    .get(detail.getDetailId());
            BigDecimal remainder = requested.subtract(confirmed);
            if (confirmed.compareTo(BigDecimal.ZERO) > 0)
            {
                InvTransferDetail accepted = copyTransferDetail(detail,
                        transferId, confirmed, acceptedDetails.size());
                acceptedDetails.add(accepted);
                acceptedQuantity = acceptedQuantity.add(confirmed);
                acceptedAmount = acceptedAmount.add(
                        nullToZero(accepted.getAmount()));
            }
            if (remainder.compareTo(BigDecimal.ZERO) > 0)
            {
                InvTransferDetail rejected = copyTransferDetail(detail,
                        null, remainder, remainderDetails.size());
                remainderDetails.add(rejected);
                remainderQuantity = remainderQuantity.add(remainder);
                remainderAmount = remainderAmount.add(
                        nullToZero(rejected.getAmount()));
            }
        }

        if (!remainderDetails.isEmpty() && remark.isBlank())
        {
            throw new ServiceException("部分确认或无法调出时必须填写说明");
        }

        InvTransferOrder reselectionDraft = null;
        String resultStatus;
        String previousStatus = locked.getStatus();
        InvTransferOrder update = new InvTransferOrder();
        update.setTransferId(transferId);
        update.setSourceConfirmedUserId(SecurityUtils.getUserId());
        update.setSourceConfirmedBy(SecurityUtils.getUsername());
        update.setSourceConfirmedTime(new Date());
        update.setSourceConfirmRemark(remark.isBlank()
                ? "调出店已全量确认" : remark);
        update.setUpdateBy(SecurityUtils.getUsername());

        if (acceptedDetails.isEmpty())
        {
            resultStatus = InvTransferSourceConfirmStatus.REJECTED;
            reselectionDraft = createSourceReselectionDraft(locked,
                    remainderDetails, remainderQuantity, remainderAmount,
                    remark);
            update.setStatus(InvStatusConstants.CLOSED);
            update.setArchivedTime(new Date());
            update.setCloseReason("调出店无法调出，已生成待重选调出店草稿 "
                    + reselectionDraft.getOrderNo());
            update.setSourceConfirmStatus(resultStatus);
        }
        else if (!remainderDetails.isEmpty())
        {
            resultStatus = InvTransferSourceConfirmStatus.PARTIAL;
            transferDetailMapper.deleteByTransferId(transferId);
            transferDetailMapper.batchInsertInvTransferDetail(
                    acceptedDetails);
            reselectionDraft = createSourceReselectionDraft(locked,
                    remainderDetails, remainderQuantity, remainderAmount,
                    remark);
            update.setTotalQuantity(acceptedQuantity);
            update.setTotalAmount(acceptedAmount);
            update.setSourceConfirmStatus(resultStatus);
        }
        else
        {
            resultStatus = InvTransferSourceConfirmStatus.CONFIRMED;
            update.setSourceConfirmStatus(resultStatus);
        }

        if (transferOrderMapper.updateInvTransferOrder(update) != 1)
        {
            throw new ServiceException("调出店确认失败，请刷新后重试");
        }
        String logReason = resultStatus.equals(
                InvTransferSourceConfirmStatus.CONFIRMED)
                        ? "调出店全量确认"
                        : (resultStatus.equals(
                                InvTransferSourceConfirmStatus.PARTIAL)
                                        ? "调出店部分确认，剩余数量已生成草稿 "
                                                + reselectionDraft
                                                        .getOrderNo()
                                        : "调出店无法调出，全部数量已生成草稿 "
                                                + reselectionDraft
                                                        .getOrderNo());
        if (!remark.isBlank())
        {
            logReason = logReason + "；" + remark;
        }
        writeStatusLog(transferId, previousStatus,
                update.getStatus() == null ? previousStatus
                        : update.getStatus(),
                "source_confirm", logReason);

        InvTransferSourceConfirmResult result =
                new InvTransferSourceConfirmResult();
        result.setTransferId(transferId);
        result.setSourceConfirmStatus(resultStatus);
        if (reselectionDraft != null)
        {
            result.setReselectionTransferId(
                    reselectionDraft.getTransferId());
            result.setReselectionOrderNo(reselectionDraft.getOrderNo());
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deliverTransfer(Long transferId, Long selectedShopDeptId)
    {
        deliverTransfer(transferId, null, selectedShopDeptId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deliverTransfer(Long transferId, InvDeliverRequest deliverRequest, Long selectedShopDeptId)
    {
        InvTransferOrder order = assertAndGetScopedTransfer(transferId, selectedShopDeptId);
        assertTransferDeliverScope(order, selectedShopDeptId);
        InvStateGuard.require(order.getStatus(), java.util.Set.of(
                InvStatusConstants.APPROVED,
                InvStatusConstants.RESERVED,
                InvStatusConstants.PARTIAL_DELIVERED), "发货");

        // 行级锁防重
        InvTransferOrder locked = transferOrderMapper.selectInvTransferOrderByIdForUpdate(transferId);
        assertTransferDeliverScope(locked, selectedShopDeptId);
        assertTransferDeliverDirection(locked, selectedShopDeptId);
        InvStateGuard.require(locked.getStatus(), java.util.Set.of(
                InvStatusConstants.APPROVED,
                InvStatusConstants.RESERVED,
                InvStatusConstants.PARTIAL_DELIVERED), "发货");
        transferApprovalService.assertApprovedForDelivery(locked);
        requireSourceConfirmationForDelivery(locked);
        Long fromWarehouseId = requireWarehouseId(resolveStockLocationDeptId(locked.getFromWarehouseId(), locked.getFromDeptId()), "调拨出库仓库不能为空");

        List<InvTransferDetail> details = transferDetailMapper.selectByTransferIdForUpdate(transferId);
        if (details.isEmpty())
        {
            throw new ServiceException("调拨单无明细");
        }
        Map<Long, BigDecimal> deliverQtyMap = resolveDeliverQuantities(deliverRequest, details);
        if (deliverQtyMap.isEmpty())
        {
            throw new ServiceException("请至少录入一条有效发货数量");
        }

        InvTransferShipment shipment = new InvTransferShipment();
        shipment.setTransferId(transferId);
        shipment.setShipmentNo(generateOrderNo("TS"));
        shipment.setWarehouseId(fromWarehouseId);
        shipment.setWarehouseDeptId(fromWarehouseId);
        shipment.setSourceLocationDeptId(fromWarehouseId);
        shipment.setStatus(InvStatusConstants.PENDING_RECEIVE);
        shipment.setShippedBy(SecurityUtils.getUsername());
        shipment.setShippedTime(new Date());
        shipment.setCreateBy(SecurityUtils.getUsername());
        transferShipmentMapper.insertShipment(shipment);

        List<InvTransferShipmentDetail> shipmentDetails = new ArrayList<>();
        for (InvTransferDetail detail : details)
        {
            BigDecimal toDeliver = deliverQtyMap.get(detail.getDetailId());
            if (toDeliver == null || toDeliver.compareTo(BigDecimal.ZERO) <= 0)
            {
                continue;
            }
            try
            {
                InvTransferQuantityPolicy.requireShipmentWithinApproved(
                        detail.getQuantity(), detail.getDeliveredQuantity(),
                        toDeliver);
            }
            catch (ServiceException exception)
            {
                throw new ServiceException("物料 [" + detail.getProductName()
                        + "] " + exception.getMessage());
            }

            String itemType = InvItemTypes.normalize(detail.getItemType());
            Long itemId = InvItemTypes.resolveItemId(itemType, detail.getItemId(), detail.getProductId());
            if (itemId == null)
            {
                throw new ServiceException("调拨明细缺少物料ID");
            }

            // 发货只消耗本调拨明细拥有的冻结量；可用量在提交时已扣减。
            InvTransferReservationService.StockConsumption consumption =
                    transferReservationService.consumeForShipment(locked,
                            detail, toDeliver, SecurityUtils.getUsername());
            BigDecimal costPrice = consumption.costPrice();

            // 写 from 库存日志（transfer_out）
            writeStockLog(itemType, itemId, detail.getProductId(), fromWarehouseId, fromWarehouseId,
                    InvStatusConstants.MOVEMENT_TRANSFER_OUT,
                    InvTransferTypes.stockBusinessType(locked.getTransferType(),
                            locked.getSourceBusinessType()),
                    locked.getTransferId(), locked.getOrderNo(), toDeliver.negate(),
                    consumption.beforeQuantity(), consumption.afterQuantity(),
                    costPrice, "调拨出库→" + locked.getToDeptName());

            // ===== 二、待门店收货后增加目标门店库存 =====

            // ===== 三、更新明细已出数量 =====
            detail.setDeliveredQuantity(detail.getDeliveredQuantity() != null
                    ? detail.getDeliveredQuantity().add(toDeliver) : toDeliver);
            transferDetailMapper.updateDeliveredQuantity(detail);

            InvTransferShipmentDetail shipmentDetail = new InvTransferShipmentDetail();
            shipmentDetail.setShipmentId(shipment.getShipmentId());
            shipmentDetail.setTransferId(transferId);
            shipmentDetail.setTransferDetailId(detail.getDetailId());
            shipmentDetail.setItemType(itemType);
            shipmentDetail.setItemId(itemId);
            shipmentDetail.setItemCode(detail.getItemCode());
            shipmentDetail.setItemName(detail.getItemName());
            shipmentDetail.setProductId(detail.getProductId());
            shipmentDetail.setProductName(detail.getProductName());
            shipmentDetail.setPlannedQuantity(detail.getQuantity());
            shipmentDetail.setShippedQuantity(toDeliver);
            shipmentDetail.setReceivedQuantity(BigDecimal.ZERO);
            shipmentDetail.setCostPrice(costPrice);
            shipmentDetails.add(shipmentDetail);
        }
        if (shipmentDetails.isEmpty())
        {
            throw new ServiceException("请至少录入一条有效发货数量");
        }
        transferShipmentDetailMapper.batchInsertShipmentDetail(shipmentDetails);

        String previousStatus = locked.getStatus();
        InvTransferOrder update = new InvTransferOrder();
        update.setTransferId(transferId);
        update.setStatus(InvTransferLifecyclePolicy.afterDelivery(
                locked.getStatus(), allRequestedDelivered(details)));
        update.setDeliveredTime(new Date());
        update.setUpdateBy(SecurityUtils.getUsername());
        transferOrderMapper.updateInvTransferOrder(update);
        writeStatusLog(transferId, previousStatus, update.getStatus(), "deliver", "调拨发货批次 " + shipment.getShipmentNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelTransfer(Long transferId, Long selectedShopDeptId)
    {
        InvTransferOrder order = assertAndGetScopedTransfer(transferId, selectedShopDeptId);
        InvStateGuard.requireCancelableDocument(order.getStatus());
        InvTransferOrder locked = transferOrderMapper
                .selectInvTransferOrderByIdForUpdate(transferId);
        if (locked == null)
        {
            throw new ServiceException("调拨单不存在");
        }
        InvStateGuard.requireCancelableDocument(locked.getStatus());
        directionPolicy().validateDraftManagement(locked,
                selectedShopDeptId);
        if (InvStatusConstants.SUBMITTED.equals(locked.getStatus())
                && InventoryUnifiedApprovalService.ENGINE_NATIVE
                        .equalsIgnoreCase(locked.getApprovalEngine() == null
                                ? "" : locked.getApprovalEngine()))
        {
            throw new ServiceException("该调拨单正在统一审批中，请使用撤回审批操作");
        }
        if (InvStatusConstants.SUBMITTED.equals(locked.getStatus()))
        {
            transferApprovalService.cancelRunning(transferId);
            transferReservationService.releaseAllRemaining(locked,
                    SecurityUtils.getUsername());
        }
        transferRevisionService.recordCancellation(locked,
                SecurityUtils.getUsername());
        String previousStatus = locked.getStatus();
        InvTransferOrder update = new InvTransferOrder();
        update.setTransferId(transferId);
        update.setStatus(InvStatusConstants.CANCELLED);
        update.setArchivedTime(new Date());
        update.setUpdateBy(SecurityUtils.getUsername());
        transferOrderMapper.updateInvTransferOrder(update);
        writeStatusLog(transferId, previousStatus, InvStatusConstants.CANCELLED, "cancel", "取消调拨单");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String withdrawApproval(Long transferId, Long selectedShopDeptId)
    {
        assertAndGetScopedTransfer(transferId, selectedShopDeptId);
        InvTransferOrder locked = transferOrderMapper
                .selectInvTransferOrderByIdForUpdate(transferId);
        if (locked == null)
        {
            throw new ServiceException("调拨单不存在");
        }
        if (!InvStatusConstants.SUBMITTED.equals(locked.getStatus())
                || !InventoryUnifiedApprovalService.ENGINE_NATIVE
                        .equalsIgnoreCase(locked.getApprovalEngine() == null
                                ? "" : locked.getApprovalEngine()))
        {
            throw new ServiceException("只有统一审批中的调拨单可以撤回");
        }
        unifiedApprovalService.withdraw(
                InventoryUnifiedApprovalService.TRANSFER, transferId,
                locked.getApprovalRound(), locked.getApprovalInstanceId(),
                "申请人撤回调拨审批");
        return "撤回请求已提交，审批结果同步后调拨单将恢复为草稿";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvTransferOrder createFromPurchase(Long purchaseId, Long selectedShopDeptId)
    {
        throw new ServiceException("请从前端传入采购申请数据创建调拨单");
    }

    @Override
    public InvTransferOrder getTransferByPurchaseId(Long purchaseId)
    {
        return transferOrderMapper.selectByPurchaseId(purchaseId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTransfer(Long transferId, Long selectedShopDeptId)
    {
        InvTransferOrder order = assertAndGetScopedTransfer(transferId, selectedShopDeptId);
        InvStateGuard.require(order.getStatus(), Set.of(InvStatusConstants.DRAFT, InvStatusConstants.CANCELLED), "删除");
        directionPolicy().validateDraftManagement(order,
                selectedShopDeptId);
        transferDetailMapper.deleteByTransferId(transferId);
        transferOrderMapper.deleteInvTransferOrderById(transferId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void receiveTransfer(Long transferId, Long selectedShopDeptId)
    {
        receiptProcessor().receiveTransfer(transferId, selectedShopDeptId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void receiveTransferShipment(Long shipmentId, InvReceiveRequest receiveRequest, Long selectedShopDeptId)
    {
        receiptProcessor().receiveTransferShipment(shipmentId,
                receiveRequest, selectedShopDeptId);
    }

    @Override
    public List<InvTransferDiscrepancy> getTransferDiscrepancies(Long transferId, Long selectedShopDeptId)
    {
        return discrepancyProcessor().getTransferDiscrepancies(
                transferId, selectedShopDeptId);
    }

    @Override
    public InvTransferDiscrepancy getTransferDiscrepancy(Long discrepancyId, Long selectedShopDeptId)
    {
        return discrepancyProcessor().getTransferDiscrepancy(
                discrepancyId, selectedShopDeptId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resolveTransferDiscrepancy(Long discrepancyId,
            InvTransferDiscrepancyResolveRequest request, Long selectedShopDeptId)
    {
        discrepancyProcessor().resolveTransferDiscrepancy(
                discrepancyId, request, selectedShopDeptId);
    }

    private InvTransferOrder assertAndGetScopedTransfer(Long transferId, Long selectedShopDeptId)
    {
        Long scopeRoot = resolveAndValidateShopDept(selectedShopDeptId);
        InvTransferOrder db = transferOrderMapper.selectInvTransferOrderById(transferId);
        if (db == null)
        {
            throw new ServiceException("调拨单不存在");
        }
        if (!isDeptVisible(db.getFromDeptId(), scopeRoot)
                && !isDeptVisible(db.getToDeptId(), scopeRoot)
                && !isDeptVisible(db.getFromWarehouseId(), scopeRoot)
                && !isDeptVisible(db.getToWarehouseId(), scopeRoot))
        {
            throw new ServiceException("无权访问该调拨单");
        }
        if (requiresSourceConfirmation(db)
                && Set.of(InvStatusConstants.DRAFT,
                        InvStatusConstants.SUBMITTED)
                        .contains(db.getStatus())
                && !isDeptVisible(db.getToDeptId(), scopeRoot))
        {
            throw new ServiceException("该异店调货单尚未进入调出店确认阶段");
        }
        return db;
    }

    private void assertDraftReadScope(InvTransferOrder order,
            Long selectedShopDeptId)
    {
        if (order != null
                && InvStatusConstants.DRAFT.equals(order.getStatus())
                && isWarehouseDept(selectedShopDeptId))
        {
            // 草稿仅对发起门店可见，避免通过详情或审批轨迹直链绕过仓库列表过滤。
            throw new ServiceException("无权访问该调拨单");
        }
    }

    void assertTransferDeliverScope(InvTransferOrder order, Long selectedShopDeptId)
    {
        assertShopVisible(resolveStockLocationDeptId(order.getFromWarehouseId(), order.getFromDeptId()), selectedShopDeptId, "无权操作该调拨单的发货仓库");
    }

    void assertTransferReceiveScope(InvTransferOrder order, Long selectedShopDeptId)
    {
        assertShopVisible(resolveStockLocationDeptId(order.getToWarehouseId(), order.getToDeptId()), selectedShopDeptId, "无权操作该调拨单的目标门店");
    }

    private void validateTransferCreationRules(InvTransferOrder order,
            Long selectedDeptId, boolean salesDeliveryInTransit)
    {
        directionPolicy().validateCreation(order, selectedDeptId,
                salesDeliveryInTransit);
    }

    private boolean requiresSourceConfirmation(InvTransferOrder order)
    {
        return order != null && InvTransferTypes.requiresSourceConfirmation(
                order.getTransferType(), order.getSourceBusinessType());
    }

    private void prepareSourceConfirmationForDraft(InvTransferOrder order,
            InvTransferOrder existing)
    {
        if (!requiresSourceConfirmation(order))
        {
            order.setSourceConfirmStatus(
                    InvTransferSourceConfirmStatus.NOT_REQUIRED);
            return;
        }
        boolean unchangedReselectionSource = existing != null
                && InvTransferSourceConfirmStatus.RESELECT_REQUIRED.equals(
                        existing.getSourceConfirmStatus())
                && Objects.equals(existing.getFromDeptId(),
                        order.getFromDeptId());
        order.setSourceConfirmStatus(unchangedReselectionSource
                ? InvTransferSourceConfirmStatus.RESELECT_REQUIRED
                : InvTransferSourceConfirmStatus.NOT_STARTED);
    }

    private void requireSourceReselectionComplete(InvTransferOrder order)
    {
        if (order != null
                && InvTransferSourceConfirmStatus.RESELECT_REQUIRED.equals(
                        order.getSourceConfirmStatus()))
        {
            throw new ServiceException("请先重新选择其他调出店，再提交异店调货");
        }
    }

    private void requireSourceConfirmationForDelivery(
            InvTransferOrder order)
    {
        if (requiresSourceConfirmation(order)
                && !InvTransferSourceConfirmStatus.allowsDelivery(
                        order.getSourceConfirmStatus()))
        {
            throw new ServiceException("调出店尚未确认本次异店调货，不能发货");
        }
    }

    private Map<Long, BigDecimal> validateSourceConfirmRequest(
            InvTransferSourceConfirmRequest request,
            List<InvTransferDetail> details)
    {
        if (request == null || request.getItems() == null
                || request.getItems().isEmpty())
        {
            throw new ServiceException("请逐行填写调出店确认数量");
        }
        Map<Long, BigDecimal> requested = new HashMap<>();
        for (InvTransferSourceConfirmItem item : request.getItems())
        {
            if (item == null || item.getDetailId() == null
                    || item.getConfirmedQuantity() == null)
            {
                throw new ServiceException("调出店确认明细不完整");
            }
            if (item.getConfirmedQuantity()
                    .compareTo(BigDecimal.ZERO) < 0)
            {
                throw new ServiceException("调出店确认数量不能小于0");
            }
            if (requested.put(item.getDetailId(),
                    item.getConfirmedQuantity()) != null)
            {
                throw new ServiceException("调出店确认明细不能重复");
            }
        }
        if (requested.size() != details.size())
        {
            throw new ServiceException("请逐行确认全部调拨明细");
        }
        for (InvTransferDetail detail : details)
        {
            BigDecimal confirmed = requested.get(detail.getDetailId());
            if (confirmed == null)
            {
                throw new ServiceException("请逐行确认全部调拨明细");
            }
            if (confirmed.compareTo(nullToZero(detail.getQuantity())) > 0)
            {
                throw new ServiceException("物料 ["
                        + displayItemName(detail)
                        + "] 确认数量不能超过申请数量");
            }
        }
        return requested;
    }

    private void assertSourceConfirmStockAvailable(
            List<InvTransferDetail> details,
            Map<Long, BigDecimal> confirmedQuantities,
            Long sourceWarehouseId)
    {
        Map<String, BigDecimal> confirmedByItem = new HashMap<>();
        Map<String, InvTransferDetail> representativeByItem =
                new HashMap<>();
        for (InvTransferDetail detail : details)
        {
            BigDecimal confirmed = confirmedQuantities
                    .get(detail.getDetailId());
            if (confirmed == null
                    || confirmed.compareTo(BigDecimal.ZERO) <= 0)
            {
                continue;
            }
            String itemType = InvItemTypes.normalize(
                    detail.getItemType());
            Long itemId = InvItemTypes.resolveItemId(itemType,
                    detail.getItemId(), detail.getProductId());
            if (itemId == null)
            {
                throw new ServiceException("调拨明细缺少物料ID");
            }
            String itemKey = itemType + ":" + itemId;
            confirmedByItem.merge(itemKey, confirmed, BigDecimal::add);
            representativeByItem.putIfAbsent(itemKey, detail);
        }

        for (Map.Entry<String, BigDecimal> entry
                : confirmedByItem.entrySet())
        {
            InvTransferDetail detail = representativeByItem
                    .get(entry.getKey());
            String itemType = InvItemTypes.normalize(
                    detail.getItemType());
            Long itemId = InvItemTypes.resolveItemId(itemType,
                    detail.getItemId(), detail.getProductId());
            InvStock stock = stockMapper
                    .selectInvStockByItemShopWarehouseForUpdate(
                            itemType, itemId, sourceWarehouseId,
                            sourceWarehouseId);
            BigDecimal available = stock == null ? BigDecimal.ZERO
                    : nullToZero(stock.getAvailableQuantity());
            if (available.compareTo(entry.getValue()) < 0)
            {
                throw new ServiceException("物料 ["
                        + displayItemName(detail)
                        + "] 合计确认数量超过调出店当前可用库存 "
                        + available.stripTrailingZeros().toPlainString());
            }
        }
    }

    private InvTransferOrder createSourceReselectionDraft(
            InvTransferOrder parent,
            List<InvTransferDetail> remainderDetails,
            BigDecimal totalQuantity, BigDecimal totalAmount,
            String confirmRemark)
    {
        InvTransferOrder draft = new InvTransferOrder();
        draft.setOrderNo(generateOrderNo("TF"));
        draft.setFromDeptId(parent.getFromDeptId());
        draft.setFromDeptName(parent.getFromDeptName());
        draft.setFromWarehouseId(parent.getFromWarehouseId());
        draft.setToDeptId(parent.getToDeptId());
        draft.setToDeptName(parent.getToDeptName());
        draft.setToWarehouseId(parent.getToWarehouseId());
        draft.setStatus(InvStatusConstants.DRAFT);
        draft.setTotalQuantity(totalQuantity);
        draft.setTotalAmount(totalAmount);
        draft.setTransferType(InvTransferTypes.CROSS_STORE);
        draft.setRecipientName(parent.getRecipientName());
        draft.setRecipientPhone(parent.getRecipientPhone());
        draft.setShippingAddress(parent.getShippingAddress());
        draft.setSourceConfirmStatus(
                InvTransferSourceConfirmStatus.RESELECT_REQUIRED);
        draft.setReselectionFromTransferId(parent.getTransferId());
        draft.setCreateBy(parent.getCreateBy() == null
                || parent.getCreateBy().isBlank()
                        ? SecurityUtils.getUsername()
                        : parent.getCreateBy());
        draft.setCreatedByUserId(parent.getCreatedByUserId());
        draft.setCreatedByName(parent.getCreatedByName());
        String systemRemark = "调出店确认后剩余数量，请重新选择其他调出店"
                + (confirmRemark == null || confirmRemark.isBlank()
                        ? "" : "；原调出店说明：" + confirmRemark);
        draft.setRemark(systemRemark);
        transferOrderMapper.insertInvTransferOrder(draft);
        if (draft.getTransferId() == null)
        {
            throw new ServiceException("生成待重选调出店草稿失败");
        }
        for (int index = 0; index < remainderDetails.size(); index++)
        {
            InvTransferDetail detail = remainderDetails.get(index);
            detail.setTransferId(draft.getTransferId());
            detail.setSortOrder(index);
        }
        transferDetailMapper.batchInsertInvTransferDetail(
                remainderDetails);
        writeStatusLog(draft.getTransferId(), null,
                InvStatusConstants.DRAFT, "source_reselect_created",
                "由异店调货单 " + parent.getOrderNo()
                        + " 的未确认数量自动生成");
        return draft;
    }

    private InvTransferDetail copyTransferDetail(
            InvTransferDetail source, Long transferId,
            BigDecimal quantity, int sortOrder)
    {
        InvTransferDetail copy = new InvTransferDetail();
        copy.setTransferId(transferId);
        copy.setItemType(source.getItemType());
        copy.setItemId(source.getItemId());
        copy.setItemCode(source.getItemCode());
        copy.setItemName(source.getItemName());
        copy.setProductId(source.getProductId());
        copy.setProductName(source.getProductName());
        copy.setProductCode(source.getProductCode());
        copy.setQuantity(quantity);
        copy.setDeliveredQuantity(BigDecimal.ZERO);
        copy.setReceivedQuantity(BigDecimal.ZERO);
        copy.setCostPrice(nullToZero(source.getCostPrice()));
        copy.setAmount(quantity.multiply(copy.getCostPrice())
                .setScale(2, RoundingMode.HALF_UP));
        copy.setUnit(source.getUnit());
        copy.setSpec(source.getSpec());
        copy.setGrade(source.getGrade());
        copy.setSortOrder(sortOrder);
        copy.setGoodsCondition(source.getGoodsCondition());
        copy.setConditionNote(source.getConditionNote());
        copy.setLotId(source.getLotId());
        copy.setSourceLocationId(source.getSourceLocationId());
        return copy;
    }

    private String displayItemName(InvTransferDetail detail)
    {
        if (detail == null)
        {
            return "未知物料";
        }
        if (detail.getItemName() != null
                && !detail.getItemName().isBlank())
        {
            return detail.getItemName();
        }
        if (detail.getProductName() != null
                && !detail.getProductName().isBlank())
        {
            return detail.getProductName();
        }
        return String.valueOf(detail.getItemId());
    }

    private void requireStoreReturnEntryEnabled(InvTransferOrder order,
            boolean submitting)
    {
        if (order == null || !InvTransferTypes.STORE_RETURN.equalsIgnoreCase(
                order.getTransferType()))
        {
            return;
        }
        if ((submitting || order.getTransferId() == null)
                && businessFeatureGate != null)
        {
            businessFeatureGate.requireEnabled(BusinessFeatureGate.STORE_RETURN);
        }
    }

    private void assertTransferDeliverDirection(InvTransferOrder order, Long selectedShopDeptId)
    {
        directionPolicy().validateDelivery(order, selectedShopDeptId);
    }

    private InvTransferDirectionPolicy directionPolicy()
    {
        return new InvTransferDirectionPolicy(deptScopeMapper, shopScopeService);
    }

    private InvTransferSourceItemScopePolicy sourceItemScopePolicy()
    {
        return new InvTransferSourceItemScopePolicy(deptScopeMapper);
    }

    private InvTransferReceiptProcessor receiptProcessor()
    {
        return new InvTransferReceiptProcessor(workflowResources(),
                directionPolicy());
    }

    private InvTransferDiscrepancyProcessor discrepancyProcessor()
    {
        return new InvTransferDiscrepancyProcessor(workflowResources(),
                directionPolicy());
    }

    private InvTransferWorkflowResources workflowResources()
    {
        return new InvTransferWorkflowResources(
                transferOrderMapper, transferDetailMapper,
                transferDiscrepancyMapper,
                transferDiscrepancyDispositionMapper,
                transferShipmentMapper,
                transferShipmentDetailMapper, stockMapper, stockLogMapper,
                numberSequenceMapper,
                statusLogMapper, businessFeatureGate, businessMetrics,
                deptScopeMapper, shopScopeService,
                transferReservationService);
    }

    private void fillAndValidateDeptNames(InvTransferOrder order,
            Long fallbackFromDeptId, Long selectedShopDeptId,
            boolean validateTargetDept)
    {
        if (order.getFromDeptId() == null)
        {
            order.setFromDeptId(fallbackFromDeptId);
        }
        if (order.getFromWarehouseId() == null)
        {
            order.setFromWarehouseId(order.getFromDeptId());
        }
        if (order.getToWarehouseId() == null && order.getToDeptId() != null)
        {
            order.setToWarehouseId(order.getToDeptId());
        }
        if (InvTransferTypes.STORE_RETURN.equals(order.getTransferType()))
        {
            assertShopVisible(order.getFromDeptId(), selectedShopDeptId,
                    "无权操作该调拨单的来源组织");
            if (validateTargetDept && order.getToDeptId() != null)
            {
                assertAuthorizedInventoryDept(order.getToDeptId(),
                        resolveAuthorizedInventoryDeptIds(),
                        "无权选择返仓目标仓库");
            }
        }
        if (validateTargetDept && order.getToDeptId() != null
                && !InvTransferTypes.STORE_RETURN.equals(order.getTransferType()))
        {
            assertShopVisible(order.getToDeptId(), selectedShopDeptId,
                    "无权操作该调拨单的目标组织");
        }
        order.setFromDeptName(deptScopeMapper.selectDeptNameById(order.getFromDeptId()));
        if (order.getToDeptId() != null)
        {
            order.setToDeptName(deptScopeMapper.selectDeptNameById(order.getToDeptId()));
        }
    }

    private TransferTotals prepareDetails(List<InvTransferDetail> details,
            Long transferId, String transferType,
            Set<Long> itemOwnerScopeDeptIds,
            boolean salesDeliveryInTransit)
    {
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (int i = 0; i < details.size(); i++)
        {
            InvTransferDetail d = details.get(i);
            InventoryItemSnapshot item = resolveTransferItem(d,
                    transferType, itemOwnerScopeDeptIds);
            applyTransferItemSnapshot(d, item);
            if (d.getQuantity() == null || d.getQuantity().compareTo(BigDecimal.ZERO) <= 0)
            {
                throw new ServiceException("调拨明细数量必须大于0");
            }
            d.setTransferId(transferId);
            d.setDeliveredQuantity(BigDecimal.ZERO);
            d.setReceivedQuantity(BigDecimal.ZERO);
            d.setSortOrder(i);
            BigDecimal costPrice;
            BigDecimal amount;
            if (salesDeliveryInTransit)
            {
                costPrice = normalizeMoney(d.getCostPrice());
                amount = normalizeMoney(d.getAmount());
            }
            else
            {
                // Browser/API callers must never choose ledger cost. Only the
                // internal sales-delivery seam above may preserve a frozen
                // source cost; ordinary transfers always use master data.
                costPrice = normalizeMoney(item.getCostPrice());
                amount = d.getQuantity().multiply(costPrice)
                        .setScale(2, RoundingMode.HALF_UP);
            }
            d.setCostPrice(costPrice);
            d.setAmount(amount);
            totalQty = totalQty.add(d.getQuantity());
            totalAmount = totalAmount.add(amount);
        }
        return new TransferTotals(totalQty, totalAmount);
    }

    private InventoryItemSnapshot resolveTransferItem(InvTransferDetail detail,
            String transferType, Set<Long> itemOwnerScopeDeptIds)
    {
        String itemType = InvItemTypes.normalize(detail.getItemType());
        validateTransferItemType(itemType, transferType);
        InventoryItemSnapshot item = itemResolver.resolve(itemType,
                detail.getItemId(), detail.getProductId());
        if (!"0".equals(item.getStatus()))
        {
            throw new ServiceException("物料 [" + item.getItemName() + "] 已停用");
        }
        sourceItemScopePolicy().assertEligible(transferType, item,
                itemOwnerScopeDeptIds, "无权调拨该物料");
        return item;
    }

    private void validatePersistedTransferItems(
            List<InvTransferDetail> details, String transferType,
            Set<Long> itemOwnerScopeDeptIds)
    {
        if (details == null)
        {
            return;
        }
        for (InvTransferDetail detail : details)
        {
            if (detail == null)
            {
                throw new ServiceException("调拨明细无效");
            }
            resolveTransferItem(detail, transferType,
                    itemOwnerScopeDeptIds);
        }
    }

    private void validateTransferItemTypes(List<InvTransferDetail> details,
            String transferType)
    {
        if (details == null)
        {
            return;
        }
        for (InvTransferDetail detail : details)
        {
            if (detail != null)
            {
                validateTransferItemType(
                        InvItemTypes.normalize(detail.getItemType()),
                        transferType);
            }
        }
    }

    private void validateTransferItemType(String itemType,
            String transferType)
    {
        boolean allowed = InvTransferTypes.allowsItemType(transferType,
                itemType);
        if (!allowed)
        {
            if (InvItemTypes.OE.equals(itemType))
            {
                throw new ServiceException(
                        "OE器皿固定资产仅通过固定资产上报，不能从调拨管理发起");
            }
            throw new ServiceException("当前调拨类型不支持该物料类型");
        }
    }

    private void validateSalesDeliverySource(InvTransferOrder order,
            boolean salesDeliveryInTransit)
    {
        String sourceType = order != null ? order.getSourceBusinessType() : null;
        if (!salesDeliveryInTransit)
        {
            if (InvTransferTypes.SOURCE_SALES_DELIVERY_NOTICE.equals(sourceType))
            {
                throw new ServiceException("销售发货通知调拨只能由发货通知生成");
            }
            if (InvTransferTypes.SOURCE_SALES_DELIVERY.equals(sourceType))
            {
                throw new ServiceException("销售发货在途调拨只能由销售发货生成；旧来源仅兼容历史查询");
            }
            return;
        }
        if (order == null
                || order.getTransferId() != null
                || !InvTransferTypes.SOURCE_SALES_DELIVERY_NOTICE.equals(
                        sourceType)
                || order.getPurchaseId() != null
                || order.getSourceBusinessId() == null
                || order.getSourceBusinessId() <= 0
                || order.getFromWarehouseId() == null
                || order.getFromWarehouseId() <= 0
                || !InvTransferTypes.CROSS_STORE.equalsIgnoreCase(
                        order.getTransferType()))
        {
            throw new ServiceException("销售发货通知调拨来源无效：必须使用noticeId、冻结源仓且purchase_id为空");
        }
    }

    private InvTransferOrder findSalesDeliveryTransfer(
            InvTransferOrder order)
    {
        return transferOrderMapper.selectBySourceBusinessTypeIdWarehouse(
                order.getSourceBusinessType(), order.getSourceBusinessId(),
                order.getFromWarehouseId());
    }

    private InvTransferOrder findSalesDeliveryTransferForUpdate(
            InvTransferOrder order)
    {
        return transferOrderMapper
                .selectBySourceBusinessTypeIdWarehouseForUpdate(
                        order.getSourceBusinessType(),
                        order.getSourceBusinessId(),
                        order.getFromWarehouseId());
    }

    private InvTransferOrder requireMatchingSalesDeliveryTransfer(
            InvTransferOrder existing, InvTransferOrder requested,
            List<InvTransferDetail> requestedDetails)
    {
        Long requestedToWarehouseId = requested.getToWarehouseId() != null
                ? requested.getToWarehouseId() : requested.getToDeptId();
        boolean sameOrderSnapshot = existing.getPurchaseId() == null
                && InvTransferTypes.SOURCE_SALES_DELIVERY_NOTICE.equals(
                        existing.getSourceBusinessType())
                && Objects.equals(existing.getSourceBusinessId(),
                        requested.getSourceBusinessId())
                && Objects.equals(existing.getFromDeptId(),
                        requested.getFromDeptId())
                && Objects.equals(existing.getFromWarehouseId(),
                        requested.getFromWarehouseId())
                && Objects.equals(existing.getToDeptId(),
                        requested.getToDeptId())
                && Objects.equals(existing.getToWarehouseId(),
                        requestedToWarehouseId)
                && InvTransferTypes.CROSS_STORE.equalsIgnoreCase(
                        existing.getTransferType());
        List<InvTransferDetail> existingDetails = transferDetailMapper
                .selectByTransferId(existing.getTransferId());
        boolean sameDetailSnapshot = false;
        try
        {
            sameDetailSnapshot = frozenDetailSnapshot(existingDetails).equals(
                    frozenDetailSnapshot(requestedDetails));
        }
        catch (ServiceException invalidExistingSnapshot)
        {
            // A partial/corrupt existing row is a source conflict, never a
            // reason to create a second transfer for the same source key.
            sameDetailSnapshot = false;
        }
        if (!sameOrderSnapshot || !sameDetailSnapshot)
        {
            throw new ServiceException("销售发货通知来源冲突：已有调拨与本次目标组织、数量或冻结成本不一致");
        }
        return existing;
    }

    private void validateFrozenSalesDeliveryDetails(
            List<InvTransferDetail> details)
    {
        if (details == null || details.isEmpty())
        {
            throw new ServiceException("请至少添加一条调拨明细");
        }
        for (InvTransferDetail detail : details)
        {
            if (detail == null || detail.getQuantity() == null
                    || detail.getQuantity().compareTo(BigDecimal.ZERO) <= 0)
            {
                throw new ServiceException("调拨明细数量必须大于0");
            }
            if (detail.getCostPrice() == null || detail.getAmount() == null)
            {
                throw new ServiceException("调拨明细成本未知，无法使用冻结成本");
            }
            BigDecimal costPrice = detail.getCostPrice()
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal amount = detail.getAmount()
                    .setScale(2, RoundingMode.HALF_UP);
            if (costPrice.compareTo(BigDecimal.ZERO) < 0
                    || amount.compareTo(BigDecimal.ZERO) < 0)
            {
                throw new ServiceException("调拨明细冻结成本非法");
            }
            BigDecimal expectedCostPrice = amount.divide(
                    detail.getQuantity(), 2, RoundingMode.HALF_UP);
            if (costPrice.compareTo(expectedCostPrice) != 0)
            {
                throw new ServiceException("调拨明细冻结成本单价与金额不一致");
            }
            detail.setCostPrice(costPrice);
            detail.setAmount(amount);
        }
    }

    private List<String> frozenDetailSnapshot(
            List<InvTransferDetail> details)
    {
        validateFrozenSalesDeliveryDetails(details);
        return details.stream()
                .map(detail ->
                {
                    String itemType = InvItemTypes.normalize(
                            detail.getItemType());
                    Long itemId = InvItemTypes.resolveItemId(itemType,
                            detail.getItemId(), detail.getProductId());
                    return itemType + ":" + itemId + ":"
                            + decimalKey(detail.getQuantity()) + ":"
                            + decimalKey(detail.getCostPrice()) + ":"
                            + decimalKey(detail.getAmount());
                })
                .sorted()
                .toList();
    }

    private String decimalKey(BigDecimal value)
    {
        return value.stripTrailingZeros().toPlainString();
    }

    private void rejectRetiredOeReplenishmentSource(InvTransferOrder order)
    {
        if (order != null && "oe_replenishment".equals(
                order.getSourceBusinessType()))
        {
            throw new ServiceException(
                    "独立OE补货链路已退役，OE固定资产仅通过固定资产上报");
        }
    }

    private void applyTransferItemSnapshot(InvTransferDetail detail, InventoryItemSnapshot item)
    {
        detail.setItemType(item.getItemType());
        detail.setItemId(item.getItemId());
        detail.setItemCode(item.getItemCode());
        detail.setItemName(item.getItemName());
        detail.setProductId(item.getProductId());
        detail.setProductCode(item.getItemCode());
        detail.setProductName(item.getItemName());
        detail.setSpec(item.getSpec());
        detail.setUnit(item.getUnit());
        detail.setGrade(item.getGrade());
    }

    private Map<Long, BigDecimal> resolveDeliverQuantities(InvDeliverRequest request, List<InvTransferDetail> details)
    {
        Map<Long, BigDecimal> result = new HashMap<>();
        if (request == null || request.getItems() == null)
        {
            for (InvTransferDetail detail : details)
            {
                BigDecimal remaining = InvTransferQuantityPolicy
                        .remainingToShip(detail.getQuantity(),
                                detail.getDeliveredQuantity());
                if (remaining.compareTo(BigDecimal.ZERO) > 0)
                {
                    result.put(detail.getDetailId(), remaining);
                }
            }
            return result;
        }
        for (InvDeliverItem item : request.getItems())
        {
            if (item.getDetailId() == null)
            {
                throw new ServiceException("发货明细ID不能为空");
            }
            BigDecimal quantity = nullToZero(item.getDeliverQuantity());
            if (quantity.compareTo(BigDecimal.ZERO) > 0)
            {
                result.put(item.getDetailId(), quantity);
            }
        }
        return result;
    }

    private BigDecimal nullToZero(BigDecimal value)
    {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal normalizeMoney(BigDecimal value)
    {
        return nullToZero(value).setScale(2, RoundingMode.HALF_UP);
    }

    private boolean allRequestedDelivered(List<InvTransferDetail> details)
    {
        for (InvTransferDetail detail : details)
        {
            if (!InvTransferQuantityPolicy.isFullyAccounted(
                    detail.getQuantity(), detail.getDeliveredQuantity(),
                    "累计发货数量不能超过审批数量"))
            {
                return false;
            }
        }
        return !details.isEmpty();
    }

    private Long resolveStockLocationDeptId(Long warehouseId, Long deptId)
    {
        if (warehouseId != null && warehouseId != 0)
        {
            return warehouseId;
        }
        return deptId;
    }

    private boolean isDeptVisible(Long deptId, Long scopeRoot)
    {
        if (deptId == null) return false;
        return deptScopeMapper.countDeptInScope(scopeRoot, deptId) > 0;
    }

    private void appendTransferScopeToParams(Map<String, Object> params,
            Long selectedShopDeptId, Long requestedStoreDeptId)
    {
        Long scopeRoot = requireSelectedShopDept(selectedShopDeptId);
        params.put("currentScopeDeptId", scopeRoot);
        if (requestedStoreDeptId != null)
        {
            assertDeptType(requestedStoreDeptId, DEPT_TYPE_STORE,
                    "店铺筛选无效");
            // 当前选中的仓库与可筛选店铺不要求存在父子组织关系，
            // 也不要求仓库员工直接拥有该店铺选店权限；
            // 查询结果由下方 currentScopeDeptId/scopeDeptIds/dataScope 共同收窄。
            params.put("storeDeptId", requestedStoreDeptId);
        }
        if (isWarehouseDept(scopeRoot))
        {
            // 仓库作业隐藏草稿；门店已提交且尚未审批的单据仍需可见。
            params.put("hideDraftTransfers", true);
        }
        if (SecurityUtils.isAdmin())
        {
            params.put("scopeDeptIds", deptScopeMapper.selectSubDeptIds(scopeRoot));
            return;
        }
        appendShopScopeToParams(params, selectedShopDeptId);
    }

    private void applyCreatedBySnapshot(InvTransferOrder order)
    {
        SysUser user = currentSysUser();
        order.setCreatedByUserId(user != null && user.getUserId() != null
                ? user.getUserId() : SecurityUtils.getUserId());
        order.setCreatedByName(normalizeNickName(user));
    }

    private void applySubmittedBySnapshot(InvTransferOrder update)
    {
        SysUser user = currentSysUser();
        update.setSubmittedByUserId(user != null && user.getUserId() != null
                ? user.getUserId() : SecurityUtils.getUserId());
        update.setSubmittedByName(normalizeNickName(user));
    }

    private SysUser currentSysUser()
    {
        if (SecurityUtils.getLoginUser() == null)
        {
            return null;
        }
        return SecurityUtils.getLoginUser().getSysUser();
    }

    private String normalizeNickName(SysUser user)
    {
        if (user == null || user.getNickName() == null)
        {
            return null;
        }
        String value = user.getNickName().trim();
        return value.isEmpty() ? null : value;
    }

    private Long requireWarehouseId(Long warehouseId, String message)
    {
        if (warehouseId == null || warehouseId == 0)
        {
            throw new ServiceException(message);
        }
        return warehouseId;
    }

    private void writeStockLog(String itemType, Long itemId, Long productId, Long shopDeptId, Long warehouseId,
                                String movementType, String businessType,
                                Long businessId, String businessNo, BigDecimal changeQty,
                                BigDecimal beforeQty, BigDecimal afterQty, BigDecimal costPrice, String remark)
    {
        InvStockLog log = new InvStockLog();
        log.setItemType(itemType);
        log.setItemId(itemId);
        log.setProductId(productId);
        log.setShopDeptId(shopDeptId);
        log.setWarehouseId(warehouseId);
        log.setMovementType(movementType);
        log.setBusinessType(businessType);
        log.setBusinessId(businessId);
        log.setBusinessNo(businessNo);
        log.setChangeQuantity(changeQty);
        log.setBeforeQuantity(beforeQty);
        log.setAfterQuantity(afterQty);
        log.setCostPrice(costPrice);
        log.setCreateBy(SecurityUtils.getUsername());
        log.setCreateTime(new Date());
        log.setRemark(remark);
        stockLogMapper.insertInvStockLog(log);
    }

    private void writeStatusLog(Long transferId, String fromStatus, String toStatus, String action, String reason)
    {
        InvTransferStatusLog log = new InvTransferStatusLog();
        log.setTransferId(transferId);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setAction(action);
        log.setOperatorId(SecurityUtils.getUserId());
        log.setOperatorName(SecurityUtils.getUsername());
        log.setReason(reason);
        statusLogMapper.insertLog(log);
    }

    private String generateOrderNo(String prefix)
    {
        String dateStr = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        numberSequenceMapper.insertOrUpdateSequence(prefix, dateStr, 0, prefix);
        numberSequenceMapper.incrementAndGetSequence(prefix, dateStr);
        Long seq = numberSequenceMapper.selectLastInsertId();
        return prefix + dateStr + String.format("%04d", seq);
    }

    private static class TransferTotals
    {
        private final BigDecimal totalQuantity;
        private final BigDecimal totalAmount;

        private TransferTotals(BigDecimal totalQuantity, BigDecimal totalAmount)
        {
            this.totalQuantity = totalQuantity;
            this.totalAmount = totalAmount;
        }
    }
}
