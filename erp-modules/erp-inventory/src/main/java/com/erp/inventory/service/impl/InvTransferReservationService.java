package com.erp.inventory.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.constant.InvItemTypes;
import com.erp.inventory.constant.InvTransferTypes;
import com.erp.inventory.domain.InvStock;
import com.erp.inventory.domain.InvTransferDetail;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.InvTransferReservation;
import com.erp.inventory.mapper.InvTransferReservationMapper;

/**
 * Transactional owner of transfer stock reservations.
 *
 * <p>All stock rows are locked and validated in deterministic order before
 * the first quantity mutation. The caller must already own a transaction, so
 * a failure in any detail rolls back the whole order instead of leaving a
 * partial reservation.</p>
 */
@Service
public class InvTransferReservationService
{
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final InvTransferReservationMapper reservationMapper;

    public InvTransferReservationService(
            InvTransferReservationMapper reservationMapper)
    {
        this.reservationMapper = reservationMapper;
    }

    /**
     * Reads the same aggregate stock dimension used by submission without
     * taking row locks or mutating inventory. The returned snapshot is only a
     * user-facing preview; submission always re-reads under lock.
     */
    @Transactional(readOnly = true)
    public List<DraftAvailability> previewForDraft(InvTransferOrder order,
            List<InvTransferDetail> details)
    {
        if (order == null)
        {
            throw new ServiceException("调拨草稿不能为空");
        }
        if (details == null || details.isEmpty())
        {
            throw new ServiceException("请至少添加一条调拨明细");
        }
        Long sourceLocationDeptId = requireSourceLocation(order);
        List<ReservationDraft> drafts = new ArrayList<>();
        Set<ReservationKey> keys = new HashSet<>();
        long syntheticDetailId = 1L;
        for (InvTransferDetail detail : details)
        {
            if (detail == null)
            {
                throw new ServiceException("调拨草稿包含空明细");
            }
            String itemType = InvItemTypes.normalize(detail.getItemType());
            Long itemId = InvItemTypes.resolveItemId(itemType,
                    detail.getItemId(), detail.getProductId());
            if (itemId == null)
            {
                throw new ServiceException("调拨明细缺少物料ID");
            }
            BigDecimal quantity = requirePositive(detail.getQuantity(),
                    "物料 [" + displayName(detail)
                            + "] 调拨数量必须大于0");
            ReservationKey key = new ReservationKey(itemType, itemId,
                    sourceLocationDeptId);
            if (!keys.add(key))
            {
                throw new ServiceException("同一物料不得重复规划");
            }
            drafts.add(new ReservationDraft(syntheticDetailId++, key,
                    quantity, displayName(detail)));
        }

        List<DraftAvailability> result = new ArrayList<>();
        for (AggregateReservation aggregate :
                aggregateAndSort(drafts).values())
        {
            InvStock stock = reservationMapper.selectStockForPlanning(
                    aggregate.key.itemType, aggregate.key.itemId,
                    aggregate.key.locationDeptId);
            BigDecimal available = stock == null ? ZERO
                    : nullToZero(stock.getAvailableQuantity());
            if (available.compareTo(ZERO) < 0)
            {
                throw new ServiceException("物料 [" + aggregate.itemName
                        + "] 可用库存事实无效");
            }
            BigDecimal shortage = aggregate.quantity.subtract(available)
                    .max(ZERO);
            result.add(new DraftAvailability(aggregate.key.itemType,
                    aggregate.key.itemId, aggregate.quantity, available,
                    shortage, stock == null ? null : stock.getStockId(),
                    stock == null ? 0L
                            : normalizedVersion(stock.getVersion())));
        }
        return List.copyOf(result);
    }

    @Transactional(propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class)
    public void reserveForSubmission(InvTransferOrder order,
            List<InvTransferDetail> details, String username)
    {
        requireOrder(order);
        if (details == null || details.isEmpty())
        {
            throw new ServiceException("请至少添加一条调拨明细");
        }
        int reservationRound = nextReservationRound(order);
        List<InvTransferReservation> existing = reservationMapper
                .selectByTransferRoundForUpdate(order.getTransferId(),
                        reservationRound);
        if (existing != null && !existing.isEmpty())
        {
            throw new ServiceException("调拨单库存已冻结，请勿重复提交");
        }

        Long sourceLocationDeptId = requireSourceLocation(order);
        boolean warehouseReplenishment = InvTransferTypes.WAREHOUSE.equals(
                InvTransferTypes.requireSupported(order.getTransferType()));
        if (warehouseReplenishment)
        {
            Long targetLocationDeptId = requireTargetLocation(order);
            Long lockedBusinessRoot = reservationMapper
                    .selectWarehouseReplenishmentRouteForUpdate(
                            sourceLocationDeptId, targetLocationDeptId);
            if (lockedBusinessRoot == null || lockedBusinessRoot <= 0)
            {
                throw new ServiceException(
                        "补货来源、目标或业务根已变化，请刷新后重试");
            }
        }
        List<ReservationDraft> drafts = buildDrafts(order, details,
                sourceLocationDeptId);
        Map<ReservationKey, AggregateReservation> aggregates =
                aggregateAndSort(drafts);

        // Lock and validate the complete order before any stock mutation.
        for (AggregateReservation aggregate : aggregates.values())
        {
            InvStock stock = reservationMapper.selectStockForUpdate(
                    aggregate.key.itemType, aggregate.key.itemId,
                    aggregate.key.locationDeptId,
                    warehouseReplenishment);
            BigDecimal available = stock == null
                    ? ZERO : nullToZero(stock.getAvailableQuantity());
            if (stock == null && warehouseReplenishment)
            {
                throw new ServiceException("物料 [" + aggregate.itemName
                        + "] 来源库存或物料资格已变化，请刷新后重试");
            }
            if (stock == null || available.compareTo(
                    aggregate.quantity) < 0)
            {
                throw new ServiceException("物料 [" + aggregate.itemName
                        + "] 可用库存不足，需 " + aggregate.quantity
                        + "，当前可用 " + available);
            }
            aggregate.stock = stock;
        }

        for (AggregateReservation aggregate : aggregates.values())
        {
            InvStock stock = aggregate.stock;
            if (reservationMapper.reserveStock(stock.getStockId(),
                    normalizedVersion(stock.getVersion()),
                    aggregate.quantity, username) != 1)
            {
                throw new ServiceException("物料 [" + aggregate.itemName
                        + "] 库存冻结失败，请刷新后重试");
            }
        }

        drafts.sort(Comparator.comparing(ReservationDraft::detailId));
        for (ReservationDraft draft : drafts)
        {
            AggregateReservation aggregate = aggregates.get(draft.key);
            InvTransferReservation reservation = new InvTransferReservation();
            reservation.setTransferId(order.getTransferId());
            reservation.setTransferDetailId(draft.detailId);
            reservation.setReservationRound(reservationRound);
            reservation.setStockId(aggregate.stock.getStockId());
            reservation.setItemType(draft.key.itemType);
            reservation.setItemId(draft.key.itemId);
            reservation.setSourceLocationDeptId(sourceLocationDeptId);
            reservation.setReservedQuantity(draft.quantity);
            reservation.setConsumedQuantity(ZERO);
            reservation.setReleasedQuantity(ZERO);
            reservation.setStatus("ACTIVE");
            reservation.setVersion(0L);
            reservation.setCreateBy(username);
            if (reservationMapper.insertReservation(reservation) != 1)
            {
                throw new ServiceException("调拨库存预留台账写入失败");
            }
        }
    }

    @Transactional(propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class)
    public StockConsumption consumeForShipment(InvTransferOrder order,
            InvTransferDetail detail, BigDecimal quantity, String username)
    {
        requireOrder(order);
        if (detail == null || detail.getDetailId() == null)
        {
            throw new ServiceException("调拨发货明细不能为空");
        }
        BigDecimal requested = requirePositive(quantity, "发货数量必须大于0");
        int reservationRound = requireCurrentReservationRound(order);
        InvTransferReservation reservation = reservationMapper
                .selectByTransferDetailRoundForUpdate(order.getTransferId(),
                        detail.getDetailId(),
                        reservationRound);
        assertReservationOwnership(order, detail, reservation);
        BigDecimal remaining = remaining(reservation);
        if (remaining.compareTo(requested) < 0)
        {
            throw new ServiceException("物料 [" + displayName(detail)
                    + "] 剩余冻结库存不足");
        }

        InvStock stock = reservationMapper.selectStockByIdForUpdate(
                reservation.getStockId());
        if (stock == null)
        {
            throw new ServiceException("物料 [" + displayName(detail)
                    + "] 冻结库存记录不存在");
        }
        BigDecimal beforeQuantity = nullToZero(stock.getCurrentQuantity());
        BigDecimal lockedQuantity = nullToZero(stock.getLockedQuantity());
        if (beforeQuantity.compareTo(requested) < 0
                || lockedQuantity.compareTo(requested) < 0)
        {
            throw new ServiceException("物料 [" + displayName(detail)
                    + "] 冻结库存与台账不一致，已拒绝发货");
        }
        BigDecimal costPrice = nullToZero(stock.getCostPrice());
        BigDecimal deductCost = requested.multiply(costPrice);
        if (reservationMapper.consumeReservedStockWithCost(
                stock.getStockId(), normalizedVersion(stock.getVersion()),
                requested, deductCost, username) != 1)
        {
            throw new ServiceException("物料 [" + displayName(detail)
                    + "] 冻结库存消耗失败，请刷新后重试");
        }
        if (reservationMapper.consumeReservation(
                reservation.getReservationId(),
                normalizedVersion(reservation.getVersion()), requested,
                username) != 1)
        {
            throw new ServiceException("物料 [" + displayName(detail)
                    + "] 预留台账消耗失败，请刷新后重试");
        }
        return new StockConsumption(stock.getStockId(), beforeQuantity,
                beforeQuantity.subtract(requested), costPrice, deductCost);
    }

    @Transactional(propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class)
    public LockedShipmentReservations lockForShipment(
            InvTransferOrder order, List<InvTransferDetail> details,
            Map<Long, BigDecimal> requestedByDetail)
    {
        requireOrder(order);
        if (details == null || details.isEmpty()
                || requestedByDetail == null || requestedByDetail.isEmpty())
        {
            throw new ServiceException("发货预留锁定缺少有效明细");
        }
        Map<Long, InvTransferDetail> detailById = new HashMap<>();
        for (InvTransferDetail detail : details)
        {
            if (detail == null || detail.getDetailId() == null
                    || !Objects.equals(order.getTransferId(),
                            detail.getTransferId())
                    || detailById.put(detail.getDetailId(), detail) != null)
            {
                throw new ServiceException("发货预留锁定的调拨明细归属无效");
            }
        }
        for (Map.Entry<Long, BigDecimal> request : requestedByDetail.entrySet())
        {
            if (!detailById.containsKey(request.getKey()))
            {
                throw new ServiceException("发货数量包含未知调拨明细");
            }
            requirePositive(request.getValue(), "发货数量必须大于0");
        }

        int reservationRound = requireCurrentReservationRound(order);
        List<InvTransferReservation> reservations = reservationMapper
                .selectByTransferRoundForUpdate(order.getTransferId(),
                        reservationRound);
        if (reservations == null || reservations.isEmpty())
        {
            throw new ServiceException("调拨库存预留台账缺失，已拒绝发货");
        }
        Map<Long, InvTransferReservation> reservationByDetail =
                new HashMap<>();
        Set<Long> stockIds = new HashSet<>();
        for (InvTransferReservation reservation : reservations)
        {
            if (reservation == null
                    || reservation.getTransferDetailId() == null
                    || reservation.getStockId() == null
                    || reservationByDetail.put(
                            reservation.getTransferDetailId(),
                            reservation) != null)
            {
                throw new ServiceException("调拨库存预留台账存在重复或无效归属");
            }
            stockIds.add(reservation.getStockId());
        }
        List<Long> sortedStockIds = stockIds.stream().sorted().toList();
        List<InvStock> stocks = sortedStockIds.isEmpty() ? List.of()
                : reservationMapper.selectStocksByIdsForUpdate(
                        sortedStockIds);
        if (stocks == null || stocks.size() != sortedStockIds.size())
        {
            throw new ServiceException("调拨冻结库存记录缺失，已拒绝发货");
        }
        Map<Long, InvStock> stockById = new LinkedHashMap<>();
        for (InvStock stock : stocks)
        {
            if (stock == null || stock.getStockId() == null
                    || stockById.put(stock.getStockId(), stock) != null)
            {
                throw new ServiceException("调拨冻结库存记录重复或无效");
            }
        }

        List<LockedDetailReservation> lockedDetails = new ArrayList<>();
        Map<Long, BigDecimal> quantityByStock = new LinkedHashMap<>();
        requestedByDetail.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(request -> {
                    InvTransferDetail detail = detailById.get(
                            request.getKey());
                    InvTransferReservation reservation =
                            reservationByDetail.get(request.getKey());
                    assertReservationOwnership(order, detail, reservation);
                    BigDecimal quantity = requirePositive(request.getValue(),
                            "发货数量必须大于0");
                    if (remaining(reservation).compareTo(quantity) < 0)
                    {
                        throw new ServiceException("物料 ["
                                + displayName(detail) + "] 剩余冻结库存不足");
                    }
                    lockedDetails.add(new LockedDetailReservation(detail,
                            reservation, quantity));
                    quantityByStock.merge(reservation.getStockId(), quantity,
                            BigDecimal::add);
                });
        for (Map.Entry<Long, BigDecimal> aggregate : quantityByStock.entrySet())
        {
            InvStock stock = stockById.get(aggregate.getKey());
            if (stock == null
                    || nullToZero(stock.getCurrentQuantity()).compareTo(
                            aggregate.getValue()) < 0
                    || nullToZero(stock.getLockedQuantity()).compareTo(
                            aggregate.getValue()) < 0)
            {
                throw new ServiceException("调拨冻结库存与预留台账不一致，已拒绝发货");
            }
        }
        return new LockedShipmentReservations(List.copyOf(lockedDetails),
                Map.copyOf(stockById), Map.copyOf(quantityByStock));
    }

    @Transactional(propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class)
    public ShipmentConsumption consumeLockedShipment(
            LockedShipmentReservations locked,
            Map<Long, BigDecimal> deductedCostByDetail, String username)
    {
        if (locked == null || locked.details() == null
                || locked.details().isEmpty() || deductedCostByDetail == null)
        {
            throw new ServiceException("发货预留消费上下文无效");
        }
        Map<Long, BigDecimal> costByStock = new LinkedHashMap<>();
        for (LockedDetailReservation detail : locked.details())
        {
            BigDecimal cost = deductedCostByDetail.get(
                    detail.detail().getDetailId());
            if (cost == null || cost.signum() < 0)
            {
                throw new ServiceException("发货成本缺失或无效");
            }
            costByStock.merge(detail.reservation().getStockId(), cost,
                    BigDecimal::add);
        }
        if (deductedCostByDetail.size() != locked.details().size())
        {
            throw new ServiceException("发货成本包含未知调拨明细");
        }

        Map<Long, BigDecimal> beforeQuantityByStock = new HashMap<>();
        for (Long stockId : locked.quantityByStock().keySet().stream()
                .sorted().toList())
        {
            InvStock stock = locked.stocksById().get(stockId);
            beforeQuantityByStock.put(stockId,
                    nullToZero(stock.getCurrentQuantity()));
            BigDecimal quantity = locked.quantityByStock().get(stockId);
            BigDecimal deductedCost = costByStock.getOrDefault(stockId, ZERO);
            if (reservationMapper.consumeReservedStockWithCost(stockId,
                    normalizedVersion(stock.getVersion()), quantity,
                    deductedCost, username) != 1)
            {
                throw new ServiceException("调拨冻结库存消耗失败，请刷新后重试");
            }
        }

        Map<Long, StockConsumption> byDetail = new LinkedHashMap<>();
        Map<Long, BigDecimal> runningQuantityByStock = new HashMap<>();
        for (LockedDetailReservation lockedDetail : locked.details())
        {
            InvTransferReservation reservation = lockedDetail.reservation();
            if (reservationMapper.consumeReservation(
                    reservation.getReservationId(),
                    normalizedVersion(reservation.getVersion()),
                    lockedDetail.quantity(), username) != 1)
            {
                throw new ServiceException("调拨预留台账消耗失败，请刷新后重试");
            }
            InvStock stock = locked.stocksById().get(
                    reservation.getStockId());
            BigDecimal consumedBefore = runningQuantityByStock.getOrDefault(
                    stock.getStockId(), ZERO);
            BigDecimal before = beforeQuantityByStock.get(stock.getStockId())
                    .subtract(consumedBefore);
            BigDecimal after = before.subtract(lockedDetail.quantity());
            runningQuantityByStock.put(stock.getStockId(),
                    consumedBefore.add(lockedDetail.quantity()));
            BigDecimal deductedCost = deductedCostByDetail.get(
                    lockedDetail.detail().getDetailId());
            BigDecimal unitCost = deductedCost.divide(
                    lockedDetail.quantity(), 6,
                    java.math.RoundingMode.HALF_UP);
            byDetail.put(lockedDetail.detail().getDetailId(),
                    new StockConsumption(stock.getStockId(), before, after,
                            unitCost, deductedCost));
        }
        return new ShipmentConsumption(Map.copyOf(byDetail));
    }

    @Transactional(propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class)
    public void releaseAllRemaining(InvTransferOrder order, String username)
    {
        requireOrder(order);
        int reservationRound = requireCurrentReservationRound(order);
        List<InvTransferReservation> reservations = reservationMapper
                .selectByTransferRoundForUpdate(order.getTransferId(),
                        reservationRound);
        if (reservations == null || reservations.isEmpty())
        {
            throw new ServiceException("调拨库存预留台账缺失，已拒绝释放");
        }

        Map<Long, BigDecimal> releaseByStock = new LinkedHashMap<>();
        reservations.stream()
                .sorted(Comparator.comparing(
                                InvTransferReservation::getItemType)
                        .thenComparing(InvTransferReservation::getItemId)
                        .thenComparing(InvTransferReservation::getSourceLocationDeptId)
                        .thenComparing(InvTransferReservation::getReservationId))
                .forEach(reservation -> {
                    BigDecimal quantity = remaining(reservation);
                    if (quantity.compareTo(ZERO) > 0)
                    {
                        releaseByStock.merge(reservation.getStockId(),
                                quantity, BigDecimal::add);
                    }
                });

        for (Map.Entry<Long, BigDecimal> release : releaseByStock.entrySet())
        {
            InvStock stock = reservationMapper.selectStockByIdForUpdate(
                    release.getKey());
            if (stock == null
                    || nullToZero(stock.getLockedQuantity())
                            .compareTo(release.getValue()) < 0)
            {
                throw new ServiceException("调拨冻结库存与预留台账不一致，已拒绝释放");
            }
            if (reservationMapper.releaseStock(stock.getStockId(),
                    normalizedVersion(stock.getVersion()), release.getValue(),
                    username) != 1)
            {
                throw new ServiceException("调拨冻结库存释放失败，请刷新后重试");
            }
        }

        for (InvTransferReservation reservation : reservations)
        {
            BigDecimal quantity = remaining(reservation);
            if (quantity.compareTo(ZERO) <= 0)
            {
                continue;
            }
            if (reservationMapper.releaseReservation(
                    reservation.getReservationId(),
                    normalizedVersion(reservation.getVersion()), quantity,
                    username) != 1)
            {
                throw new ServiceException("调拨预留台账释放失败，请刷新后重试");
            }
        }
    }

    @Transactional(propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class)
    public void reserveForReshipment(InvTransferOrder order,
            InvTransferDetail detail, BigDecimal quantity, String username)
    {
        requireOrder(order);
        BigDecimal requested = requirePositive(quantity, "补发数量必须大于0");
        int reservationRound = requireCurrentReservationRound(order);
        InvTransferReservation reservation = reservationMapper
                .selectByTransferDetailRoundForUpdate(order.getTransferId(),
                        detail.getDetailId(),
                        reservationRound);
        assertReservationOwnership(order, detail, reservation);
        InvStock stock = reservationMapper.selectStockByIdForUpdate(
                reservation.getStockId());
        if (stock == null
                || nullToZero(stock.getAvailableQuantity())
                        .compareTo(requested) < 0)
        {
            throw new ServiceException("物料 [" + displayName(detail)
                    + "] 补发可用库存不足");
        }
        if (reservationMapper.reserveStock(stock.getStockId(),
                normalizedVersion(stock.getVersion()), requested,
                username) != 1)
        {
            throw new ServiceException("物料 [" + displayName(detail)
                    + "] 补发库存冻结失败，请刷新后重试");
        }
        if (reservationMapper.increaseReservation(
                reservation.getReservationId(),
                normalizedVersion(reservation.getVersion()), requested,
                username) != 1)
        {
            throw new ServiceException("物料 [" + displayName(detail)
                    + "] 补发预留台账更新失败，请刷新后重试");
        }
    }

    private List<ReservationDraft> buildDrafts(InvTransferOrder order,
            List<InvTransferDetail> details, Long sourceLocationDeptId)
    {
        List<ReservationDraft> drafts = new ArrayList<>();
        for (InvTransferDetail detail : details)
        {
            if (detail == null || detail.getDetailId() == null
                    || !Objects.equals(order.getTransferId(),
                            detail.getTransferId()))
            {
                throw new ServiceException("调拨明细归属无效，不能冻结库存");
            }
            String itemType = InvItemTypes.normalize(detail.getItemType());
            Long itemId = InvItemTypes.resolveItemId(itemType,
                    detail.getItemId(), detail.getProductId());
            if (itemId == null)
            {
                throw new ServiceException("调拨明细缺少物料ID");
            }
            BigDecimal quantity = requirePositive(detail.getQuantity(),
                    "物料 [" + displayName(detail) + "] 调拨数量必须大于0");
            drafts.add(new ReservationDraft(detail.getDetailId(),
                    new ReservationKey(itemType, itemId,
                            sourceLocationDeptId), quantity,
                    displayName(detail)));
        }
        return drafts;
    }

    private Map<ReservationKey, AggregateReservation> aggregateAndSort(
            List<ReservationDraft> drafts)
    {
        Map<ReservationKey, AggregateReservation> unsorted =
                new LinkedHashMap<>();
        for (ReservationDraft draft : drafts)
        {
            unsorted.compute(draft.key, (key, value) -> {
                if (value == null)
                {
                    return new AggregateReservation(key, draft.quantity,
                            draft.itemName);
                }
                value.quantity = value.quantity.add(draft.quantity);
                return value;
            });
        }
        List<Map.Entry<ReservationKey, AggregateReservation>> entries =
                new ArrayList<>(unsorted.entrySet());
        entries.sort(Map.Entry.comparingByKey(
                Comparator.comparing(ReservationKey::itemType)
                        .thenComparing(ReservationKey::itemId)
                        .thenComparing(ReservationKey::locationDeptId)));
        Map<ReservationKey, AggregateReservation> sorted =
                new LinkedHashMap<>();
        for (Map.Entry<ReservationKey, AggregateReservation> entry : entries)
        {
            sorted.put(entry.getKey(), entry.getValue());
        }
        return sorted;
    }

    private void assertReservationOwnership(InvTransferOrder order,
            InvTransferDetail detail, InvTransferReservation reservation)
    {
        String itemType = InvItemTypes.normalize(detail.getItemType());
        Long itemId = InvItemTypes.resolveItemId(itemType,
                detail.getItemId(), detail.getProductId());
        if (reservation == null
                || !Objects.equals(order.getTransferId(),
                        reservation.getTransferId())
                || !Objects.equals(detail.getDetailId(),
                        reservation.getTransferDetailId())
                || !Objects.equals(order.getApprovalRound(),
                        reservation.getReservationRound())
                || !Objects.equals(itemType, reservation.getItemType())
                || !Objects.equals(itemId, reservation.getItemId()))
        {
            throw new ServiceException("调拨明细库存预留归属校验失败");
        }
    }

    private Long requireSourceLocation(InvTransferOrder order)
    {
        Long sourceLocationDeptId = InvTransferDirectionPolicy
                .resolveStockLocationDeptId(order.getFromWarehouseId(),
                        order.getFromDeptId());
        if (sourceLocationDeptId == null || sourceLocationDeptId == 0)
        {
            throw new ServiceException("调拨来源库存组织不能为空");
        }
        return sourceLocationDeptId;
    }

    private Long requireTargetLocation(InvTransferOrder order)
    {
        Long targetLocationDeptId = InvTransferDirectionPolicy
                .resolveStockLocationDeptId(order.getToWarehouseId(),
                        order.getToDeptId());
        if (targetLocationDeptId == null || targetLocationDeptId == 0)
        {
            throw new ServiceException("调拨目标库存组织不能为空");
        }
        return targetLocationDeptId;
    }

    private int nextReservationRound(InvTransferOrder order)
    {
        int current = order.getApprovalRound() == null
                ? 0 : order.getApprovalRound();
        if (current == Integer.MAX_VALUE)
        {
            throw new ServiceException("调拨审批轮次已达上限");
        }
        return current + 1;
    }

    private int requireCurrentReservationRound(InvTransferOrder order)
    {
        if (order.getApprovalRound() == null || order.getApprovalRound() <= 0)
        {
            throw new ServiceException("调拨库存预留轮次缺失");
        }
        return order.getApprovalRound();
    }

    private void requireOrder(InvTransferOrder order)
    {
        if (order == null || order.getTransferId() == null)
        {
            throw new ServiceException("调拨单不能为空");
        }
    }

    private BigDecimal remaining(InvTransferReservation reservation)
    {
        BigDecimal remaining = nullToZero(reservation.getReservedQuantity())
                .subtract(nullToZero(reservation.getConsumedQuantity()))
                .subtract(nullToZero(reservation.getReleasedQuantity()));
        if (remaining.compareTo(ZERO) < 0)
        {
            throw new ServiceException("调拨预留数量守恒校验失败");
        }
        return remaining;
    }

    private BigDecimal requirePositive(BigDecimal value, String message)
    {
        BigDecimal normalized = nullToZero(value);
        if (normalized.compareTo(ZERO) <= 0)
        {
            throw new ServiceException(message);
        }
        return normalized;
    }

    private BigDecimal nullToZero(BigDecimal value)
    {
        return value == null ? ZERO : value;
    }

    private Long normalizedVersion(Long version)
    {
        return version == null ? 0L : version;
    }

    private String displayName(InvTransferDetail detail)
    {
        if (detail.getItemName() != null && !detail.getItemName().isBlank())
        {
            return detail.getItemName();
        }
        if (detail.getProductName() != null
                && !detail.getProductName().isBlank())
        {
            return detail.getProductName();
        }
        return String.valueOf(detail.getItemId() != null
                ? detail.getItemId() : detail.getProductId());
    }

    public record StockConsumption(Long stockId, BigDecimal beforeQuantity,
            BigDecimal afterQuantity, BigDecimal costPrice,
            BigDecimal deductedCost)
    {
    }

    public record DraftAvailability(String itemType, Long itemId,
            BigDecimal requestedQuantity, BigDecimal availableQuantity,
            BigDecimal shortageQuantity, Long stockId, Long stockVersion)
    {
    }

    public record LockedDetailReservation(InvTransferDetail detail,
            InvTransferReservation reservation, BigDecimal quantity)
    {
    }

    public record LockedShipmentReservations(
            List<LockedDetailReservation> details,
            Map<Long, InvStock> stocksById,
            Map<Long, BigDecimal> quantityByStock)
    {
    }

    public record ShipmentConsumption(
            Map<Long, StockConsumption> byTransferDetailId)
    {
    }

    private record ReservationKey(String itemType, Long itemId,
            Long locationDeptId)
    {
    }

    private record ReservationDraft(Long detailId, ReservationKey key,
            BigDecimal quantity, String itemName)
    {
    }

    private static final class AggregateReservation
    {
        private final ReservationKey key;
        private BigDecimal quantity;
        private final String itemName;
        private InvStock stock;

        private AggregateReservation(ReservationKey key,
                BigDecimal quantity, String itemName)
        {
            this.key = key;
            this.quantity = quantity;
            this.itemName = itemName;
        }
    }
}
