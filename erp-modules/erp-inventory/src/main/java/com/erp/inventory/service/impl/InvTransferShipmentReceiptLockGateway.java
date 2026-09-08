package com.erp.inventory.service.impl;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.constant.InvItemTypes;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.InvTransferShipment;
import com.erp.inventory.domain.dto.InvTransferReceiptDiscrepancyReturnReceiptAllocationRequest;
import com.erp.inventory.domain.dto.InvTransferReceiptDiscrepancyReturnReceiptCreateRequest;
import com.erp.inventory.domain.dto.InvTransferShipmentReceiptAllocationRequest;
import com.erp.inventory.domain.dto.InvTransferShipmentReceiptCreateRequest;
import com.erp.inventory.domain.transfer.InvShipmentPlanningItemKey;
import com.erp.inventory.domain.transfer.InvTransferReceiptBalanceKey;
import com.erp.inventory.domain.transfer.InvTransferReceiptDerivedLotKey;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnReceiptLockedBoundary;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnReceiptPolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptLocationCandidate;
import com.erp.inventory.domain.transfer.InvTransferReceiptLockedAllocation;
import com.erp.inventory.domain.transfer.InvTransferReceiptLockedDetail;
import com.erp.inventory.domain.transfer.InvTransferReceiptLockedTransferDetail;
import com.erp.inventory.domain.transfer.InvTransferReceiptPlanningAllocationFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptPlanningSerialFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptTargetBalance;
import com.erp.inventory.domain.transfer.InvTransferReceiptTargetLot;
import com.erp.inventory.domain.transfer.InvTransferReceiptTargetStock;
import com.erp.inventory.domain.transfer.InvTransferShipmentReceiptPlanComposer;
import com.erp.inventory.domain.transfer.InvTransferShipmentReceiptLockedBoundary;
import com.erp.inventory.domain.transfer.InvWarehouseStockMode;
import com.erp.inventory.mapper.InvTransferOrderMapper;
import com.erp.inventory.mapper.InvTransferShipmentMapper;
import com.erp.inventory.mapper.InvTransferShipmentReceiptPersistenceMapper;
import com.erp.inventory.mapper.InvTransferShipmentReceiptPlanningMapper;

/**
 * Fixed-order, read-only lock boundary for one V2 transfer receipt.
 *
 * <p>This is the only Stage 12C2 component allowed to hold the persistence
 * mapper. It calls only its {@code select...ForUpdate} methods, then reads the
 * already-locked projection used by the shared Composer.</p>
 */
@Component
public class InvTransferShipmentReceiptLockGateway
{
    private static final Comparator<InvShipmentPlanningItemKey> ITEM_ORDER =
            Comparator.comparing(InvShipmentPlanningItemKey::itemType)
                    .thenComparing(InvShipmentPlanningItemKey::itemId);
    private static final Comparator<InvTransferReceiptDerivedLotKey>
            LOT_KEY_ORDER = Comparator
                    .comparing(InvTransferReceiptDerivedLotKey::warehouseId)
                    .thenComparing(InvTransferReceiptDerivedLotKey::itemType)
                    .thenComparing(InvTransferReceiptDerivedLotKey::itemId)
                    .thenComparing(
                            InvTransferReceiptDerivedLotKey::sourceLotId)
                    .thenComparing(
                            InvTransferReceiptDerivedLotKey::disposition);
    private static final Comparator<InvTransferReceiptBalanceKey>
            BALANCE_KEY_ORDER = Comparator
                    .comparing(InvTransferReceiptBalanceKey::lotId)
                    .thenComparing(
                            InvTransferReceiptBalanceKey::locationId);

    private final InvTransferShipmentMapper shipmentMapper;
    private final InvTransferOrderMapper orderMapper;
    private final InvTransferShipmentReceiptPersistenceMapper lockMapper;
    private final InvTransferShipmentReceiptPlanningMapper planningMapper;

    public InvTransferShipmentReceiptLockGateway(
            InvTransferShipmentMapper shipmentMapper,
            InvTransferOrderMapper orderMapper,
            InvTransferShipmentReceiptPersistenceMapper lockMapper,
            InvTransferShipmentReceiptPlanningMapper planningMapper)
    {
        this.shipmentMapper = shipmentMapper;
        this.orderMapper = orderMapper;
        this.lockMapper = lockMapper;
        this.planningMapper = planningMapper;
    }

    public LockedHeader lockHeader(Long shipmentId)
    {
        if (shipmentId == null || shipmentId <= 0)
        {
            throw new ServiceException("发货批次标识无效");
        }
        InvTransferShipment shipment = shipmentMapper.selectByIdForUpdate(
                shipmentId);
        if (shipment == null)
        {
            throw new ServiceException("发货批次不存在");
        }
        if (!Objects.equals(shipmentId, shipment.getShipmentId())
                || shipment.getTransferId() == null
                || shipment.getTransferId() <= 0)
        {
            throw new ServiceException("发货批次归属或标识不可核验");
        }
        InvTransferOrder order = orderMapper
                .selectInvTransferOrderByIdForUpdate(
                        shipment.getTransferId());
        if (order == null)
        {
            throw new ServiceException("调拨单不存在");
        }
        if (!Objects.equals(shipment.getTransferId(), order.getTransferId())
                || order.getVersion() == null || order.getVersion() < 0)
        {
            throw new ServiceException("调拨单主数据归属或版本不可核验");
        }
        return new LockedHeader(shipment, order);
    }

    public InvTransferShipmentReceiptLockedBoundary lockAndCompose(
            LockedHeader header, Long targetWarehouseId,
            InvTransferShipmentReceiptCreateRequest request)
    {
        return lockAndCompose(header, targetWarehouseId,
                requestedTargets(request), false).lockedFacts();
    }

    public InvTransferReceiptDiscrepancyReturnReceiptLockedBoundary
            lockAndComposeReturnReceipt(LockedHeader header,
                    Long targetWarehouseId,
                    InvTransferReceiptDiscrepancyReturnReceiptCreateRequest
                            request)
    {
        LockedAssembly assembly = lockAndCompose(header, targetWarehouseId,
                requestedTargets(request), true);
        return new InvTransferReceiptDiscrepancyReturnReceiptLockedBoundary(
                assembly.lockedFacts(), assembly.returnPlan());
    }

    private LockedAssembly lockAndCompose(LockedHeader header,
            Long targetWarehouseId, List<RequestedTarget> requestedTargets,
            boolean returnReceipt)
    {
        requireHeader(header, targetWarehouseId);
        InvTransferShipment shipment = header.shipment();
        InvTransferOrder order = header.order();
        Long shipmentId = shipment.getShipmentId();
        Long transferId = order.getTransferId();

        List<InvTransferReceiptLockedTransferDetail> transferDetails =
                safeList(lockMapper.selectTransferDetailsForUpdate(
                        transferId));
        List<InvTransferReceiptLockedDetail> details = safeList(lockMapper
                .selectShipmentDetailsForUpdate(shipmentId));
        List<InvTransferReceiptLockedAllocation> allocations = safeList(
                lockMapper.selectAllocationsForUpdate(shipmentId));
        LockedStructure structure = validateStructure(header,
                transferDetails, details, allocations);

        InvWarehouseStockMode mode = lockMapper
                .selectTargetWarehouseModeForUpdate(targetWarehouseId);
        lockMapper.selectPoliciesForUpdate(structure.itemKeys());
        List<InvTransferReceiptTargetStock> targetStocks = safeList(
                lockMapper.selectTargetStocksForUpdate(targetWarehouseId,
                        targetWarehouseId, structure.itemKeys()));

        List<InvTransferReceiptTargetLot> sourceLots = safeList(lockMapper
                .selectSourceLotsForUpdate(structure.sourceLotIds()));
        requireExactIds(Set.copyOf(structure.sourceLotIds()), sourceLots
                .stream().map(InvTransferReceiptTargetLot::getLotId).toList(),
                "来源批次锁定事实不完整");

        List<InvTransferReceiptLocationCandidate> lockedLocations = safeList(
                lockMapper.selectLocationsForUpdate(
                        structure.sourceLocationIds(), targetWarehouseId));
        requireContainedIds(structure.sourceLocationIds(), lockedLocations
                .stream().map(InvTransferReceiptLocationCandidate
                        ::getLocationId).toList(),
                "来源库位锁定事实不完整");
        List<InvTransferReceiptLocationCandidate> targetLocations =
                targetLocations(lockedLocations, targetWarehouseId);

        List<InvTransferReceiptDerivedLotKey> derivedKeys = derivedKeys(
                structure, targetWarehouseId, requestedTargets);
        List<InvTransferReceiptTargetLot> derivedLots = derivedKeys.isEmpty()
                ? List.of() : safeList(lockMapper
                        .selectDerivedLotsForUpdate(derivedKeys));
        validateDerivedLots(derivedKeys, derivedLots);

        List<InvTransferReceiptBalanceKey> balanceKeys = balanceKeys(
                derivedLots, targetLocations, requestedTargets);
        List<InvTransferReceiptTargetBalance> balances = List.of();
        if (!balanceKeys.isEmpty())
        {
            balances = safeList(
                    lockMapper.selectTargetBalancesForUpdate(
                            targetWarehouseId, balanceKeys));
            validateBalances(balanceKeys, balances, targetWarehouseId);
        }

        List<InvTransferReceiptPlanningSerialFact> serials = safeList(
                lockMapper.selectShipmentSerialsForUpdate(shipmentId));
        List<InvTransferReceiptPlanningAllocationFact> planningAllocations =
                safeList(planningMapper.selectAllocations(shipmentId));
        requireExactIds(structure.allocationIds(), planningAllocations
                .stream().map(InvTransferReceiptPlanningAllocationFact
                        ::getAllocationId).toList(),
                "权威收货规划与锁定来源分配不一致");

        InvTransferShipmentReceiptPlanComposer.Composition composition =
                InvTransferShipmentReceiptPlanComposer.compose(shipment,
                order, mode, planningAllocations, serials, targetLocations);
        InvTransferReceiptDiscrepancyReturnReceiptPolicy.Plan returnPlan =
                returnReceipt
                        ? InvTransferReceiptDiscrepancyReturnReceiptPolicy
                                .compose(shipment, order, mode,
                                        planningAllocations, serials,
                                        targetLocations)
                        : null;
        InvTransferShipmentReceiptLockedBoundary lockedFacts = lockedBoundary(
                header, targetWarehouseId, composition, structure,
                planningAllocations, targetStocks, sourceLots,
                targetLocations, derivedLots, balances, serials);
        return new LockedAssembly(lockedFacts, returnPlan);
    }

    private static void requireHeader(LockedHeader header,
            Long targetWarehouseId)
    {
        if (header == null || header.shipment() == null
                || header.order() == null || targetWarehouseId == null
                || targetWarehouseId <= 0
                || !Objects.equals(targetWarehouseId,
                        InvTransferDirectionPolicy.resolveStockLocationDeptId(
                                header.order().getToWarehouseId(),
                                header.order().getToDeptId())))
        {
            throw new ServiceException("目标收货仓库与锁定调拨单不一致");
        }
    }

    private static LockedStructure validateStructure(LockedHeader header,
            List<InvTransferReceiptLockedTransferDetail> transferDetails,
            List<InvTransferReceiptLockedDetail> details,
            List<InvTransferReceiptLockedAllocation> allocations)
    {
        if (transferDetails.isEmpty() || details.isEmpty()
                || allocations.isEmpty())
        {
            throw new ServiceException("V2发货批次缺少可锁定来源事实");
        }
        Map<Long, InvTransferReceiptLockedTransferDetail>
                transferDetailById = new HashMap<>();
        for (InvTransferReceiptLockedTransferDetail detail : transferDetails)
        {
            if (detail == null || detail.getDetailId() == null
                    || detail.getDetailId() <= 0
                    || transferDetailById.put(detail.getDetailId(), detail)
                            != null
                    || !Objects.equals(header.order().getTransferId(),
                            detail.getTransferId())
                    || detail.getItemType() == null
                    || detail.getItemType().isBlank()
                    || detail.getItemId() == null || detail.getItemId() <= 0
                    || !nonNegative(detail.getQuantity())
                    || !nonNegative(detail.getDeliveredQuantity())
                    || !nonNegative(detail.getReceivedQuantity())
                    || detail.getReceivedQuantity().compareTo(
                            detail.getDeliveredQuantity()) > 0
                    || detail.getDeliveredQuantity().compareTo(
                            detail.getQuantity()) > 0)
            {
                throw new ServiceException("调拨明细锁定事实归属或数量不可核验");
            }
        }
        Map<Long, InvTransferReceiptLockedDetail> detailById =
                new HashMap<>();
        TreeSet<InvShipmentPlanningItemKey> itemKeys = new TreeSet<>(
                ITEM_ORDER);
        for (InvTransferReceiptLockedDetail detail : details)
        {
            if (detail == null || detail.getShipmentDetailId() == null
                    || detail.getShipmentDetailId() <= 0
                    || detailById.put(detail.getShipmentDetailId(), detail)
                            != null
                    || !Objects.equals(header.shipment().getShipmentId(),
                            detail.getShipmentId())
                    || !Objects.equals(header.order().getTransferId(),
                            detail.getTransferId())
                    || !transferDetailById.containsKey(
                            detail.getTransferDetailId())
                    || detail.getItemType() == null
                    || detail.getItemType().isBlank()
                    || detail.getItemId() == null || detail.getItemId() <= 0)
            {
                throw new ServiceException("发货明细锁定事实归属或标识不可核验");
            }
            itemKeys.add(new InvShipmentPlanningItemKey(
                    InvItemTypes.normalize(detail.getItemType()),
                    detail.getItemId()));
        }

        Set<Long> allocationIds = new HashSet<>();
        Set<Long> allocatedDetailIds = new HashSet<>();
        TreeSet<Long> sourceLotIds = new TreeSet<>();
        TreeSet<Long> sourceLocationIds = new TreeSet<>();
        Map<Long, InvTransferReceiptLockedDetail> detailByAllocation =
                new HashMap<>();
        Map<Long, InvTransferReceiptLockedAllocation> allocationById =
                new HashMap<>();
        for (InvTransferReceiptLockedAllocation allocation : allocations)
        {
            InvTransferReceiptLockedDetail detail = allocation == null
                    ? null : detailById.get(allocation.getShipmentDetailId());
            if (allocation == null || allocation.getAllocationId() == null
                    || allocation.getAllocationId() <= 0
                    || !allocationIds.add(allocation.getAllocationId())
                    || detail == null
                    || !Objects.equals(header.shipment().getShipmentId(),
                            allocation.getShipmentId())
                    || !Objects.equals(header.order().getTransferId(),
                            allocation.getTransferId())
                    || !Objects.equals(detail.getTransferDetailId(),
                            allocation.getTransferDetailId())
                    || allocation.getSourceLotId() == null
                    || allocation.getSourceLotId() <= 0
                    || allocation.getSourceLocationId() == null
                    || allocation.getSourceLocationId() <= 0)
            {
                throw new ServiceException("来源分配锁定事实归属或标识不可核验");
            }
            allocatedDetailIds.add(allocation.getShipmentDetailId());
            detailByAllocation.put(allocation.getAllocationId(), detail);
            allocationById.put(allocation.getAllocationId(), allocation);
            sourceLotIds.add(allocation.getSourceLotId());
            sourceLocationIds.add(allocation.getSourceLocationId());
        }
        if (!allocatedDetailIds.equals(detailById.keySet()))
        {
            throw new ServiceException("发货明细与来源分配锁定事实不完整");
        }
        return new LockedStructure(List.copyOf(itemKeys),
                List.copyOf(sourceLotIds), List.copyOf(sourceLocationIds),
                Set.copyOf(allocationIds), Map.copyOf(allocationById),
                Map.copyOf(detailByAllocation),
                Map.copyOf(transferDetailById));
    }

    private static List<InvTransferReceiptLocationCandidate> targetLocations(
            List<InvTransferReceiptLocationCandidate> values,
            Long targetWarehouseId)
    {
        return values.stream().filter(Objects::nonNull)
                .filter(value -> Objects.equals(targetWarehouseId,
                        value.getWarehouseId()))
                .filter(value -> "0".equals(value.getStatus()))
                .filter(value -> "0".equals(value.getVirtualFlag()))
                .filter(value -> Set.of("storage", "quarantine").contains(
                        normalize(value.getLocationType())))
                .toList();
    }

    private static List<InvTransferReceiptDerivedLotKey> derivedKeys(
            LockedStructure structure, Long targetWarehouseId,
            List<RequestedTarget> requestedTargets)
    {
        TreeSet<InvTransferReceiptDerivedLotKey> result = new TreeSet<>(
                LOT_KEY_ORDER);
        for (RequestedTarget requested : requestedTargets)
        {
            InvTransferReceiptLockedAllocation allocation = requested == null
                    ? null : structure.allocationById().get(
                            requested.allocationId());
            if (allocation == null)
            {
                continue;
            }
            InvTransferReceiptLockedDetail detail = structure
                    .detailByAllocation().get(allocation.getAllocationId());
            String itemType = InvItemTypes.normalize(detail.getItemType());
            if (positive(requested.acceptedQuantity()))
            {
                result.add(new InvTransferReceiptDerivedLotKey(
                        targetWarehouseId, itemType, detail.getItemId(),
                        allocation.getSourceLotId(), "accepted"));
            }
            if (positive(requested.damagedQuantity()))
            {
                result.add(new InvTransferReceiptDerivedLotKey(
                        targetWarehouseId, itemType, detail.getItemId(),
                        allocation.getSourceLotId(), "damaged"));
            }
        }
        return List.copyOf(result);
    }

    private static void validateDerivedLots(
            List<InvTransferReceiptDerivedLotKey> requested,
            List<InvTransferReceiptTargetLot> values)
    {
        Set<InvTransferReceiptDerivedLotKey> allowed = new HashSet<>(
                requested);
        Set<Long> ids = new HashSet<>();
        Set<InvTransferReceiptDerivedLotKey> actualKeys = new HashSet<>();
        for (InvTransferReceiptTargetLot value : values)
        {
            InvTransferReceiptDerivedLotKey actual = value == null ? null
                    : new InvTransferReceiptDerivedLotKey(
                            value.getWarehouseId(),
                            normalize(value.getItemType()), value.getItemId(),
                            value.getSourceLotId(),
                            normalize(value.getReceiptDisposition()));
            if (value == null || value.getLotId() == null
                    || value.getLotId() <= 0 || !ids.add(value.getLotId())
                    || !actualKeys.add(actual)
                    || !allowed.contains(actual))
            {
                throw new ServiceException("目标派生批次锁定事实越界");
            }
        }
    }

    private static List<InvTransferReceiptBalanceKey> balanceKeys(
            List<InvTransferReceiptTargetLot> lots,
            List<InvTransferReceiptLocationCandidate> locations,
            List<RequestedTarget> requestedTargets)
    {
        TreeSet<InvTransferReceiptBalanceKey> result = new TreeSet<>(
                BALANCE_KEY_ORDER);
        Set<Long> acceptedLocationIds = requestedLocationIds(requestedTargets,
                true);
        Set<Long> damagedLocationIds = requestedLocationIds(requestedTargets,
                false);
        for (InvTransferReceiptTargetLot lot : lots)
        {
            for (InvTransferReceiptLocationCandidate location : locations)
            {
                boolean accepted = "accepted".equals(normalize(
                        lot.getReceiptDisposition()))
                        && "storage".equals(normalize(
                                location.getLocationType()))
                        && acceptedLocationIds.contains(
                                location.getLocationId());
                boolean damaged = "damaged".equals(normalize(
                        lot.getReceiptDisposition()))
                        && "quarantine".equals(normalize(
                                location.getLocationType()))
                        && damagedLocationIds.contains(
                                location.getLocationId());
                if (accepted || damaged)
                {
                    result.add(new InvTransferReceiptBalanceKey(
                            lot.getLotId(), location.getLocationId()));
                }
            }
        }
        return List.copyOf(result);
    }

    private static void validateBalances(
            List<InvTransferReceiptBalanceKey> requested,
            List<InvTransferReceiptTargetBalance> values,
            Long targetWarehouseId)
    {
        Set<InvTransferReceiptBalanceKey> allowed = new HashSet<>(requested);
        Set<Long> ids = new HashSet<>();
        Set<InvTransferReceiptBalanceKey> actualKeys = new HashSet<>();
        for (InvTransferReceiptTargetBalance value : values)
        {
            InvTransferReceiptBalanceKey actual = value == null ? null
                    : new InvTransferReceiptBalanceKey(value.getLotId(),
                            value.getLocationId());
            if (value == null || value.getBalanceId() == null
                    || value.getBalanceId() <= 0
                    || !ids.add(value.getBalanceId())
                    || !actualKeys.add(actual)
                    || !Objects.equals(targetWarehouseId,
                            value.getWarehouseId())
                    || !allowed.contains(actual))
            {
                throw new ServiceException("目标余额锁定事实越界");
            }
        }
    }

    private static InvTransferShipmentReceiptLockedBoundary lockedBoundary(
            LockedHeader lockedHeader, Long targetWarehouseId,
            InvTransferShipmentReceiptPlanComposer.Composition composition,
            LockedStructure structure,
            List<InvTransferReceiptPlanningAllocationFact> planning,
            List<InvTransferReceiptTargetStock> targetStocks,
            List<InvTransferReceiptTargetLot> sourceLots,
            List<InvTransferReceiptLocationCandidate> targetLocations,
            List<InvTransferReceiptTargetLot> targetLots,
            List<InvTransferReceiptTargetBalance> targetBalances,
            List<InvTransferReceiptPlanningSerialFact> serials)
    {
        InvTransferShipment shipment = lockedHeader.shipment();
        InvTransferOrder order = lockedHeader.order();
        Map<Long, InvTransferShipmentReceiptLockedBoundary.Allocation>
                allocations = new HashMap<>();
        for (InvTransferReceiptPlanningAllocationFact fact : planning)
        {
            InvTransferReceiptLockedAllocation locked = fact == null ? null
                    : structure.allocationById().get(fact.getAllocationId());
            InvTransferReceiptLockedDetail shipmentDetail = locked == null
                    ? null : structure.detailByAllocation().get(
                            locked.getAllocationId());
            InvTransferReceiptLockedTransferDetail transferDetail =
                    locked == null ? null : structure.transferDetailById()
                            .get(locked.getTransferDetailId());
            requireSameLockedAllocation(fact, locked, shipmentDetail,
                    transferDetail, shipment);
            InvTransferShipmentReceiptLockedBoundary.Allocation snapshot =
                    new InvTransferShipmentReceiptLockedBoundary.Allocation(
                            fact.getAllocationId(),
                            fact.getShipmentDetailId(),
                            fact.getTransferDetailId(),
                            InvItemTypes.normalize(fact.getItemType()),
                            fact.getItemId(), fact.getProductId(),
                            normalize(fact.getAllocationPolicy()),
                            normalize(fact.getTrackingPolicy()),
                            fact.getAllocatedQuantity(),
                            fact.getSourceBalanceId(), fact.getSourceLotId(),
                            fact.getSourceLocationId(), fact.getCostPrice(),
                            fact.getTotalCost(),
                            fact.getAcceptedReceivedQuantity(),
                            fact.getDamagedReceivedQuantity(),
                            fact.getShortageReportedQuantity(),
                            fact.getReceiptVersion(),
                            fact.getShipmentDetailShippedQuantity(),
                            fact.getShipmentDetailReceivedQuantity(),
                            transferDetail.getDeliveredQuantity(),
                            transferDetail.getReceivedQuantity());
            if (allocations.put(fact.getAllocationId(), snapshot) != null)
            {
                throw new ServiceException("权威收货规划分配重复");
            }
        }

        Map<Long, InvTransferShipmentReceiptLockedBoundary.TransferDetail>
                transferDetails = new HashMap<>();
        structure.transferDetailById().forEach((id, detail) ->
                transferDetails.put(id,
                        new InvTransferShipmentReceiptLockedBoundary
                                .TransferDetail(detail.getDetailId(),
                                        InvItemTypes.normalize(
                                                detail.getItemType()),
                                        detail.getItemId(),
                                        detail.getProductId(),
                                        detail.getQuantity(),
                                        detail.getDeliveredQuantity(),
                                        detail.getReceivedQuantity())));

        Map<InvShipmentPlanningItemKey,
                InvTransferShipmentReceiptLockedBoundary.TargetStock>
                stocks = new HashMap<>();
        Set<InvShipmentPlanningItemKey> allowedItems = Set.copyOf(
                structure.itemKeys());
        for (InvTransferReceiptTargetStock stock : targetStocks)
        {
            InvShipmentPlanningItemKey key = stock == null ? null
                    : new InvShipmentPlanningItemKey(InvItemTypes.normalize(
                            stock.getItemType()), stock.getItemId());
            if (stock == null || stock.getStockId() == null
                    || stock.getStockId() <= 0 || !allowedItems.contains(key)
                    || !Objects.equals(targetWarehouseId,
                            stock.getShopDeptId())
                    || !Objects.equals(targetWarehouseId,
                            stock.getWarehouseId())
                    || stocks.put(key,
                            new InvTransferShipmentReceiptLockedBoundary
                                    .TargetStock(stock.getStockId(), key,
                                            stock.getProductId(),
                                            stock.getShopDeptId(),
                                            stock.getWarehouseId(),
                                            stock.getCurrentQuantity(),
                                            stock.getLockedQuantity(),
                                            stock.getAvailableQuantity(),
                                            stock.getQuarantineQuantity(),
                                            stock.getCostPrice(),
                                            stock.getTotalCost(),
                                            stock.getVersion())) != null)
            {
                throw new ServiceException("目标汇总库存锁定事实越界或重复");
            }
        }

        Map<Long, InvTransferShipmentReceiptLockedBoundary.SourceLot>
                sources = new HashMap<>();
        for (InvTransferReceiptTargetLot source : sourceLots)
        {
            if (source == null || source.getLotId() == null
                    || sources.put(source.getLotId(),
                            new InvTransferShipmentReceiptLockedBoundary
                                    .SourceLot(source.getLotId(),
                                            source.getLotNo(),
                                            InvItemTypes.normalize(
                                                    source.getItemType()),
                                            source.getItemId(),
                                            source.getProductId(),
                                            source.getWarehouseId(),
                                            source.getSupplierBatchNo(),
                                            source.getProductionDate(),
                                            source.getExpiryDate(),
                                            source.getQcStatus(),
                                            source.getLotStatus())) != null)
            {
                throw new ServiceException("来源批次锁定事实重复");
            }
        }

        Map<InvTransferReceiptDerivedLotKey,
                InvTransferShipmentReceiptLockedBoundary.TargetLot>
                lots = new HashMap<>();
        for (InvTransferReceiptTargetLot lot : targetLots)
        {
            InvTransferReceiptDerivedLotKey key = lot == null ? null
                    : new InvTransferReceiptDerivedLotKey(
                            lot.getWarehouseId(), InvItemTypes.normalize(
                                    lot.getItemType()), lot.getItemId(),
                            lot.getSourceLotId(),
                            normalize(lot.getReceiptDisposition()));
            if (lot == null || lots.put(key,
                    new InvTransferShipmentReceiptLockedBoundary.TargetLot(
                            lot.getLotId(), key, lot.getLotNo(),
                            lot.getProductId(), lot.getQcStatus(),
                            lot.getLotStatus())) != null)
            {
                throw new ServiceException("目标派生批次锁定事实重复");
            }
        }

        Map<Long, InvTransferShipmentReceiptLockedBoundary.Location>
                locations = new HashMap<>();
        for (InvTransferReceiptLocationCandidate location : targetLocations)
        {
            if (location == null || location.getLocationId() == null
                    || locations.put(location.getLocationId(),
                            new InvTransferShipmentReceiptLockedBoundary
                                    .Location(location.getLocationId(),
                                            location.getWarehouseId(),
                                            location.getLocationCode(),
                                            location.getLocationName(),
                                            normalize(location
                                                    .getLocationType())))
                            != null)
            {
                throw new ServiceException("目标库位锁定事实重复");
            }
        }

        Map<InvTransferReceiptBalanceKey,
                InvTransferShipmentReceiptLockedBoundary.TargetBalance>
                balances = new HashMap<>();
        for (InvTransferReceiptTargetBalance balance : targetBalances)
        {
            InvTransferReceiptBalanceKey key = balance == null ? null
                    : new InvTransferReceiptBalanceKey(balance.getLotId(),
                            balance.getLocationId());
            if (balance == null || balances.put(key,
                    new InvTransferShipmentReceiptLockedBoundary
                            .TargetBalance(balance.getBalanceId(),
                                    balance.getWarehouseId(),
                                    balance.getLotId(),
                                    balance.getLocationId(),
                                    balance.getCurrentQuantity(),
                                    balance.getLockedQuantity(),
                                    balance.getAvailableQuantity(),
                                    balance.getQuarantineQuantity(),
                                    balance.getCostPrice(),
                                    balance.getTotalCost(),
                                    balance.getVersion())) != null)
            {
                throw new ServiceException("目标余额锁定事实重复");
            }
        }

        Map<Long, InvTransferShipmentReceiptLockedBoundary.Serial>
                serialSnapshots = new HashMap<>();
        for (InvTransferReceiptPlanningSerialFact serial : serials)
        {
            if (serial == null || serial.getSerialId() == null
                    || serialSnapshots.put(serial.getSerialId(),
                            new InvTransferShipmentReceiptLockedBoundary
                                    .Serial(serial.getShipmentSerialId(),
                                            serial.getAllocationId(),
                                            serial.getShipmentId(),
                                            serial.getSerialId(),
                                            serial.getCurrentSerialNo(),
                                            InvItemTypes.normalize(
                                                    serial.getItemType()),
                                            serial.getItemId(),
                                            serial.getCurrentWarehouseId(),
                                            serial.getCurrentBalanceId(),
                                            serial.getCurrentLotId(),
                                            serial.getCurrentLocationId(),
                                            normalize(serial
                                                    .getCurrentStatus())))
                            != null)
            {
                throw new ServiceException("收货序列号锁定事实重复");
            }
        }

        InvTransferShipmentReceiptLockedBoundary.Header header =
                new InvTransferShipmentReceiptLockedBoundary.Header(
                        shipment.getShipmentId(), order.getTransferId(),
                        shipment.getWarehouseId(),
                        shipment.getSourceLocationDeptId(),
                        targetWarehouseId, shipment.getShipmentNo(),
                        shipment.getPlanVersion(),
                        shipment.getSealedRevisionId(),
                        shipment.getReconcileBatch(),
                        composition.targetStockMode()
                                .getLastReconcileBatch(),
                        shipment.getStatus(), order.getStatus(),
                        order.getVersion());
        return new InvTransferShipmentReceiptLockedBoundary(header,
                composition, allocations, transferDetails, stocks, sources,
                lots, locations, balances, serialSnapshots);
    }

    private static void requireSameLockedAllocation(
            InvTransferReceiptPlanningAllocationFact fact,
            InvTransferReceiptLockedAllocation locked,
            InvTransferReceiptLockedDetail shipmentDetail,
            InvTransferReceiptLockedTransferDetail transferDetail,
            InvTransferShipment shipment)
    {
        if (fact == null || locked == null || shipmentDetail == null
                || transferDetail == null
                || !Objects.equals(fact.getShipmentId(),
                        locked.getShipmentId())
                || !Objects.equals(fact.getShipmentDetailId(),
                        locked.getShipmentDetailId())
                || !Objects.equals(fact.getTransferId(),
                        locked.getTransferId())
                || !Objects.equals(fact.getTransferDetailId(),
                        locked.getTransferDetailId())
                || !Objects.equals(fact.getSourceBalanceId(),
                        locked.getSourceBalanceId())
                || !Objects.equals(fact.getSourceLotId(),
                        locked.getSourceLotId())
                || !Objects.equals(fact.getSourceLocationId(),
                        locked.getSourceLocationId())
                || !Objects.equals(normalize(fact.getAllocationPolicy()),
                        normalize(locked.getAllocationPolicy()))
                || !Objects.equals(normalize(fact.getTrackingPolicy()),
                        normalize(locked.getTrackingPolicy()))
                || !same(fact.getAllocatedQuantity(),
                        locked.getAllocatedQuantity())
                || !same(fact.getCostPrice(), locked.getCostPrice())
                || !same(fact.getTotalCost(), locked.getTotalCost())
                || !same(fact.getAcceptedReceivedQuantity(),
                        locked.getAcceptedReceivedQuantity())
                || !same(fact.getDamagedReceivedQuantity(),
                        locked.getDamagedReceivedQuantity())
                || !same(fact.getShortageReportedQuantity(),
                        locked.getShortageReportedQuantity())
                || !Objects.equals(fact.getReceiptVersion(),
                        locked.getReceiptVersion())
                || !Objects.equals(fact.getBalanceVersionAfter(),
                        locked.getBalanceVersionAfter())
                || !Objects.equals(fact.getShipmentDetailId(),
                        shipmentDetail.getShipmentDetailId())
                || !Objects.equals(fact.getTransferDetailId(),
                        shipmentDetail.getTransferDetailId())
                || !Objects.equals(InvItemTypes.normalize(fact.getItemType()),
                        InvItemTypes.normalize(shipmentDetail.getItemType()))
                || !Objects.equals(fact.getItemId(),
                        shipmentDetail.getItemId())
                || !same(fact.getShipmentDetailShippedQuantity(),
                        shipmentDetail.getShippedQuantity())
                || !same(fact.getShipmentDetailReceivedQuantity(),
                        shipmentDetail.getReceivedQuantity())
                || !Objects.equals(fact.getTransferDetailId(),
                        transferDetail.getDetailId())
                || !Objects.equals(InvItemTypes.normalize(fact.getItemType()),
                        InvItemTypes.normalize(transferDetail.getItemType()))
                || !Objects.equals(fact.getItemId(),
                        transferDetail.getItemId())
                || !Objects.equals(fact.getSourceWarehouseId(),
                        shipment.getWarehouseId()))
        {
            throw new ServiceException("权威收货规划与锁定来源事实不一致");
        }
    }

    private static List<Long> sortedPositiveIds(List<Long> values,
            String message)
    {
        TreeSet<Long> result = new TreeSet<>();
        for (Long value : values)
        {
            if (value == null || value <= 0 || !result.add(value))
            {
                throw new ServiceException(message);
            }
        }
        return List.copyOf(result);
    }

    private static void requireContainedIds(List<Long> expected,
            List<Long> actual, String message)
    {
        Set<Long> present = new HashSet<>(actual);
        if (actual.stream().anyMatch(value -> value == null || value <= 0)
                || !present.containsAll(expected))
        {
            throw new ServiceException(message);
        }
    }

    private static void requireExactIds(Set<Long> expected,
            List<Long> actual, String message)
    {
        Set<Long> values = new HashSet<>();
        if (actual.stream().anyMatch(value -> value == null || value <= 0
                || !values.add(value)) || !values.equals(expected))
        {
            throw new ServiceException(message);
        }
    }

    private static String normalize(String value)
    {
        return value == null ? null : value.trim().toLowerCase();
    }

    private static List<RequestedTarget> requestedTargets(
            InvTransferShipmentReceiptCreateRequest request)
    {
        if (request == null || request.getAllocations() == null)
        {
            return List.of();
        }
        List<RequestedTarget> result = new java.util.ArrayList<>();
        for (InvTransferShipmentReceiptAllocationRequest value
                : request.getAllocations())
        {
            if (value != null)
            {
                result.add(new RequestedTarget(
                        value.getShipmentAllocationId(),
                        value.getAcceptedQuantity(),
                        value.getAcceptedLocationId(),
                        value.getDamagedQuantity(),
                        value.getQuarantineLocationId()));
            }
        }
        return List.copyOf(result);
    }

    private static List<RequestedTarget> requestedTargets(
            InvTransferReceiptDiscrepancyReturnReceiptCreateRequest request)
    {
        if (request == null || request.getAllocations() == null)
        {
            return List.of();
        }
        List<RequestedTarget> result = new java.util.ArrayList<>();
        for (InvTransferReceiptDiscrepancyReturnReceiptAllocationRequest value
                : request.getAllocations())
        {
            if (value != null)
            {
                result.add(new RequestedTarget(
                        value.getShipmentAllocationId(), BigDecimal.ZERO, null,
                        value.getReturnedQuantity(),
                        value.getQuarantineLocationId()));
            }
        }
        return List.copyOf(result);
    }

    private static Set<Long> requestedLocationIds(
            List<RequestedTarget> requestedTargets, boolean accepted)
    {
        Set<Long> result = new HashSet<>();
        for (RequestedTarget value : requestedTargets)
        {
            Long locationId = accepted ? value.acceptedLocationId()
                    : value.damagedLocationId();
            BigDecimal quantity = accepted ? value.acceptedQuantity()
                    : value.damagedQuantity();
            if (positive(quantity) && locationId != null && locationId > 0)
            {
                result.add(locationId);
            }
        }
        return result;
    }

    private static boolean positive(java.math.BigDecimal value)
    {
        return value != null && value.signum() > 0;
    }

    private static boolean nonNegative(java.math.BigDecimal value)
    {
        return value != null && value.signum() >= 0;
    }

    private static boolean same(java.math.BigDecimal left,
            java.math.BigDecimal right)
    {
        return left != null && right != null
                && left.compareTo(right) == 0;
    }

    private static <T> List<T> safeList(List<T> values)
    {
        return values == null ? List.of() : values;
    }

    public record LockedHeader(InvTransferShipment shipment,
            InvTransferOrder order)
    {
    }

    private record LockedAssembly(
            InvTransferShipmentReceiptLockedBoundary lockedFacts,
            InvTransferReceiptDiscrepancyReturnReceiptPolicy.Plan returnPlan)
    {
    }

    private record RequestedTarget(Long allocationId,
            BigDecimal acceptedQuantity, Long acceptedLocationId,
            BigDecimal damagedQuantity, Long damagedLocationId)
    {
    }

    private record LockedStructure(
            List<InvShipmentPlanningItemKey> itemKeys,
            List<Long> sourceLotIds,
            List<Long> sourceLocationIds,
            Set<Long> allocationIds,
            Map<Long, InvTransferReceiptLockedAllocation> allocationById,
            Map<Long, InvTransferReceiptLockedDetail> detailByAllocation,
            Map<Long, InvTransferReceiptLockedTransferDetail>
                    transferDetailById)
    {
    }
}
