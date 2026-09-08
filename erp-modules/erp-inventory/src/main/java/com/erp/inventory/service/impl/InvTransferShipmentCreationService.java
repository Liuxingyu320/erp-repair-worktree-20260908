package com.erp.inventory.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.constant.InvItemTypes;
import com.erp.inventory.constant.InvStatusConstants;
import com.erp.inventory.constant.InvTransferRevisionStatuses;
import com.erp.inventory.constant.InvTransferShipmentWriteVersions;
import com.erp.inventory.constant.InvTransferTypes;
import com.erp.inventory.domain.InvStockLog;
import com.erp.inventory.domain.InvTransferDetail;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.InvTransferRevision;
import com.erp.inventory.domain.InvTransferShipment;
import com.erp.inventory.domain.InvTransferShipmentDetail;
import com.erp.inventory.domain.InvTransferStatusLog;
import com.erp.inventory.domain.dto.InvTransferShipmentAllocationRequest;
import com.erp.inventory.domain.dto.InvTransferShipmentCreateRequest;
import com.erp.inventory.domain.transfer.InvItemFulfillmentPolicy;
import com.erp.inventory.domain.transfer.InvLockedShipmentBalance;
import com.erp.inventory.domain.transfer.InvShipmentAllocationCandidate;
import com.erp.inventory.domain.transfer.InvShipmentPlanningItemKey;
import com.erp.inventory.domain.transfer.InvShipmentSerialCandidate;
import com.erp.inventory.domain.transfer.InvStockLedgerDetailRecord;
import com.erp.inventory.domain.transfer.InvTransferLifecyclePolicy;
import com.erp.inventory.domain.transfer.InvTransferShipmentAllocationPolicy;
import com.erp.inventory.domain.transfer.InvTransferShipmentAllocationRecord;
import com.erp.inventory.domain.transfer.InvTransferShipmentPlanComposer;
import com.erp.inventory.domain.transfer.InvTransferShipmentSerialRecord;
import com.erp.inventory.domain.transfer.InvWarehouseStockMode;
import com.erp.inventory.domain.vo.InvTransferRevisionHistoryVo;
import com.erp.inventory.domain.vo.InvTransferRevisionVo;
import com.erp.inventory.domain.vo.InvTransferShipmentCreationVo;
import com.erp.inventory.mapper.InvDeptScopeMapper;
import com.erp.inventory.mapper.InvStockLogMapper;
import com.erp.inventory.mapper.InvTransferDetailMapper;
import com.erp.inventory.mapper.InvTransferOrderMapper;
import com.erp.inventory.mapper.InvTransferRevisionMapper;
import com.erp.inventory.mapper.InvTransferShipmentCreationMapper;
import com.erp.inventory.mapper.InvTransferShipmentDetailMapper;
import com.erp.inventory.mapper.InvTransferShipmentMapper;
import com.erp.inventory.mapper.InvTransferStatusLogMapper;
import com.erp.inventory.service.IInvTransferApprovalService;

/** Transactional owner of the disabled-by-default V2 shipment write path. */
@Service
public class InvTransferShipmentCreationService extends InvBaseService
{
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String BASIS_SERVER = "server-recommendation";

    private final InvTransferShipmentWriteGate writeGate;
    private final InvTransferOrderMapper orderMapper;
    private final InvTransferRevisionMapper revisionMapper;
    private final InvTransferDetailMapper detailMapper;
    private final InvTransferShipmentCreationMapper creationMapper;
    private final InvTransferReservationService reservationService;
    private final InvTransferRevisionService revisionService;
    private final IInvTransferApprovalService approvalService;
    private final InvTransferShipmentMapper shipmentMapper;
    private final InvTransferShipmentDetailMapper shipmentDetailMapper;
    private final InvStockLogMapper stockLogMapper;
    private final InvTransferStatusLogMapper statusLogMapper;

    public InvTransferShipmentCreationService(
            InvTransferShipmentWriteGate writeGate,
            InvTransferOrderMapper orderMapper,
            InvTransferRevisionMapper revisionMapper,
            InvTransferDetailMapper detailMapper,
            InvTransferShipmentCreationMapper creationMapper,
            InvTransferReservationService reservationService,
            InvTransferRevisionService revisionService,
            IInvTransferApprovalService approvalService,
            InvTransferShipmentMapper shipmentMapper,
            InvTransferShipmentDetailMapper shipmentDetailMapper,
            InvStockLogMapper stockLogMapper,
            InvTransferStatusLogMapper statusLogMapper,
            InvDeptScopeMapper deptScopeMapper,
            ShopScopeService shopScopeService)
    {
        this.writeGate = writeGate;
        this.orderMapper = orderMapper;
        this.revisionMapper = revisionMapper;
        this.detailMapper = detailMapper;
        this.creationMapper = creationMapper;
        this.reservationService = reservationService;
        this.revisionService = revisionService;
        this.approvalService = approvalService;
        this.shipmentMapper = shipmentMapper;
        this.shipmentDetailMapper = shipmentDetailMapper;
        this.stockLogMapper = stockLogMapper;
        this.statusLogMapper = statusLogMapper;
        this.deptScopeMapper = deptScopeMapper;
        this.shopScopeService = shopScopeService;
    }

    @Transactional(propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class)
    public InvTransferShipmentCreationVo create(String requestId,
            Long transferId, InvTransferShipmentCreateRequest request,
            Long selectedDeptId)
    {
        writeGate.requireEnabled();
        String commandRequestId = InvTransferCommandExecutor
                .requireRequestId(requestId);
        requireRequest(transferId, request);
        resolveAndValidateShopDept(selectedDeptId);

        InvTransferOrder order = orderMapper
                .selectInvTransferOrderByIdForUpdate(transferId);
        if (order == null)
        {
            throw new ServiceException("调拨单不存在");
        }
        Long sourceWarehouseId = InvTransferDirectionPolicy
                .resolveStockLocationDeptId(order.getFromWarehouseId(),
                        order.getFromDeptId());
        if (sourceWarehouseId == null || sourceWarehouseId <= 0)
        {
            throw new ServiceException("调拨单缺少有效发货仓库");
        }
        assertShopVisible(sourceWarehouseId, selectedDeptId,
                "无权操作该调拨单的发货仓库");
        new InvTransferDirectionPolicy(deptScopeMapper, shopScopeService)
                .validateDelivery(order, selectedDeptId);
        InvTransferLifecyclePolicy.requireDeliveryAllowed(order.getStatus());
        approvalService.assertApprovedForDelivery(order);

        InvTransferRevision lockedRevision = revisionMapper
                .selectLatestByTransferIdForUpdate(transferId);
        InvTransferRevisionVo revision = requireApprovedRevision(order,
                lockedRevision);

        List<Long> lockedDetailIds = creationMapper
                .selectTransferDetailIdsForUpdate(transferId);
        List<InvTransferDetail> details = detailMapper
                .selectByTransferId(transferId);
        validateLockedDetails(transferId, lockedDetailIds, details);
        List<InvShipmentPlanningItemKey> itemKeys = itemKeys(details);
        if (itemKeys.isEmpty())
        {
            throw new ServiceException("调拨单没有剩余待发物料");
        }

        InvWarehouseStockMode mode = creationMapper
                .selectWarehouseModeForUpdate(sourceWarehouseId);
        if (!InvTransferShipmentPlanComposer.canReadDetailStock(mode,
                sourceWarehouseId) || mode == null
                || isBlank(mode.getLastReconcileBatch()))
        {
            throw new ServiceException(
                    "来源仓库尚未达到dual/detail且对账通过的写入条件");
        }
        List<InvItemFulfillmentPolicy> policies = creationMapper
                .selectPoliciesForUpdate(itemKeys);

        Map<Long, BigDecimal> requestedByDetail = requestedByDetail(request);
        InvTransferReservationService.LockedShipmentReservations
                lockedReservations = reservationService.lockForShipment(
                        order, details, requestedByDetail);

        List<InvLockedShipmentBalance> balances = creationMapper
                .selectBalancesForUpdate(sourceWarehouseId, itemKeys);
        List<Long> lotIds = distinctSorted(balances,
                InvLockedShipmentBalance::getLotId);
        List<Long> locationIds = distinctSorted(balances,
                InvLockedShipmentBalance::getLocationId);
        requireCompleteLocks(lotIds, lotIds.isEmpty() ? List.of()
                : creationMapper.selectLotsForUpdate(lotIds),
                "批次锁定事实不完整");
        requireCompleteLocks(locationIds, locationIds.isEmpty() ? List.of()
                : creationMapper.selectLocationsForUpdate(locationIds),
                "库位锁定事实不完整");
        List<Long> balanceIds = distinctSorted(balances,
                InvLockedShipmentBalance::getBalanceId);
        List<InvShipmentAllocationCandidate> candidates = balanceIds.isEmpty()
                ? List.of() : creationMapper.selectEligibleCandidateFacts(
                        balanceIds);
        List<InvShipmentSerialCandidate> serialFacts = balanceIds.isEmpty()
                ? List.of() : creationMapper.selectSerialsForUpdate(
                        balanceIds);
        attachAvailableSerials(candidates, serialFacts);

        InvTransferShipmentPlanComposer.Composition composition =
                InvTransferShipmentPlanComposer.compose(order, revision,
                        mode, details, policies, candidates);
        validateExactRecommendation(request, revision, composition);

        Date occurredTime = new Date();
        String username = requireUsername();
        Map<Long, InvLockedShipmentBalance> balanceById = indexBalances(
                balances);
        PreparedMutation prepared = prepareMutation(composition,
                balanceById);
        InvTransferShipment shipment = insertShipment(order, revision,
                composition, commandRequestId, sourceWarehouseId,
                occurredTime, username);
        Map<Long, InvTransferShipmentDetail> shipmentDetailByTransferDetail =
                insertShipmentDetails(shipment, composition, prepared,
                        username);

        applyDetailStockMutations(prepared, sourceWarehouseId, username);
        InvTransferReservationService.ShipmentConsumption summaryConsumption =
                reservationService.consumeLockedShipment(
                        lockedReservations, prepared.costByDetail(), username);
        persistAuditAndLedgers(order, shipment, composition, prepared,
                shipmentDetailByTransferDetail, summaryConsumption,
                commandRequestId, sourceWarehouseId, occurredTime, username);
        updateTransfer(order, details, prepared.quantityByDetail(), shipment,
                occurredTime, username);

        return new InvTransferShipmentCreationVo(
                identifier(shipment.getShipmentId()), shipment.getShipmentNo(),
                identifier(order.getTransferId()), shipment.getStatus(),
                composition.planVersion(),
                identifier(revision.getRevisionId()),
                DateTimeFormatter.ISO_INSTANT.format(
                        occurredTime.toInstant()));
    }

    private void requireRequest(Long transferId,
            InvTransferShipmentCreateRequest request)
    {
        if (transferId == null || transferId <= 0 || request == null
                || request.getPlanVersion() == null
                || !request.getPlanVersion().matches("[a-f0-9]{64}")
                || request.getSealedRevisionId() == null
                || request.getSealedRevisionId() <= 0
                || !BASIS_SERVER.equals(request.getBasis())
                || request.getAllocations() == null
                || request.getAllocations().isEmpty())
        {
            throw new ServiceException("V2发货请求缺少有效服务端规划");
        }
    }

    private InvTransferRevisionVo requireApprovedRevision(
            InvTransferOrder order, InvTransferRevision locked)
    {
        if (locked == null
                || !Objects.equals(order.getTransferId(),
                        locked.getTransferId())
                || !InvTransferRevisionStatuses.APPROVED.equals(
                        locked.getStatus())
                || locked.getRevisionId() == null
                || locked.getRevisionNo() == null
                || locked.getRevisionNo() <= 0
                || locked.getSnapshotHash() == null
                || !locked.getSnapshotHash().matches("[a-f0-9]{64}"))
        {
            throw new ServiceException("调拨单最新业务版本未审批或不可核验");
        }
        InvTransferRevisionHistoryVo history = revisionService.getHistory(
                order);
        if (history == null || history.getRevisions() == null
                || history.getRevisions().isEmpty())
        {
            throw new ServiceException("调拨单缺少可核验审批版本");
        }
        InvTransferRevisionVo latest = history.getRevisions().stream()
                .max(Comparator.comparing(
                        InvTransferRevisionVo::getRevisionNo))
                .orElseThrow(() -> new ServiceException(
                        "调拨单缺少可核验审批版本"));
        if (!Objects.equals(latest.getRevisionId(), locked.getRevisionId())
                || !Objects.equals(latest.getRevisionNo(),
                        locked.getRevisionNo())
                || !Objects.equals(latest.getSnapshotHash(),
                        locked.getSnapshotHash())
                || !InvTransferRevisionStatuses.APPROVED.equals(
                        latest.getStatus())
                || !Objects.equals(history.getCurrentRevisionNo(),
                        latest.getRevisionNo())
                || !InvTransferRevisionStatuses.APPROVED.equals(
                        history.getCurrentRevisionStatus()))
        {
            throw new ServiceException("调拨单审批封存版本发生变化");
        }
        return latest;
    }

    private static void validateLockedDetails(Long transferId,
            List<Long> lockedIds, List<InvTransferDetail> details)
    {
        if (lockedIds == null || lockedIds.isEmpty() || details == null
                || lockedIds.size() != details.size())
        {
            throw new ServiceException("调拨明细锁定事实不完整");
        }
        Set<Long> expected = new HashSet<>(lockedIds);
        Set<Long> actual = new HashSet<>();
        for (InvTransferDetail detail : details)
        {
            if (detail == null || detail.getDetailId() == null
                    || !Objects.equals(transferId, detail.getTransferId())
                    || !actual.add(detail.getDetailId()))
            {
                throw new ServiceException("调拨明细归属、标识或唯一性不可核验");
            }
        }
        if (!expected.equals(actual))
        {
            throw new ServiceException("调拨明细在锁定期间发生变化");
        }
    }

    private static List<InvShipmentPlanningItemKey> itemKeys(
            List<InvTransferDetail> details)
    {
        Map<String, InvShipmentPlanningItemKey> result =
                new LinkedHashMap<>();
        for (InvTransferDetail detail : details)
        {
            BigDecimal remaining = nullToZero(detail.getQuantity())
                    .subtract(nullToZero(detail.getDeliveredQuantity()));
            if (remaining.signum() <= 0)
            {
                continue;
            }
            String type = InvItemTypes.normalize(detail.getItemType());
            Long itemId = InvItemTypes.resolveItemId(type,
                    detail.getItemId(), detail.getProductId());
            if (itemId != null && itemId > 0)
            {
                result.putIfAbsent(type + ':' + itemId,
                        new InvShipmentPlanningItemKey(type, itemId));
            }
        }
        return List.copyOf(result.values());
    }

    private static Map<Long, BigDecimal> requestedByDetail(
            InvTransferShipmentCreateRequest request)
    {
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        Set<String> detailBalances = new HashSet<>();
        Set<Long> serialIds = new HashSet<>();
        for (InvTransferShipmentAllocationRequest allocation
                : request.getAllocations())
        {
            requireAllocation(allocation);
            if (!detailBalances.add(allocation.getTransferDetailId()
                    + ":" + allocation.getBalanceId()))
            {
                throw new ServiceException("发货请求同一明细重复消费库存余额");
            }
            for (Long serialId : allocation.getSerialIds())
            {
                if (!serialIds.add(serialId))
                {
                    throw new ServiceException("发货请求重复消费同一序列号");
                }
            }
            result.merge(allocation.getTransferDetailId(),
                    allocation.getQuantity(), BigDecimal::add);
        }
        return result;
    }

    private static void requireAllocation(
            InvTransferShipmentAllocationRequest allocation)
    {
        if (allocation == null || allocation.getTransferDetailId() == null
                || allocation.getTransferDetailId() <= 0
                || allocation.getBalanceId() == null
                || allocation.getBalanceId() <= 0
                || allocation.getLotId() == null
                || allocation.getLotId() <= 0
                || allocation.getLocationId() == null
                || allocation.getLocationId() <= 0
                || allocation.getQuantity() == null
                || allocation.getQuantity().signum() <= 0
                || allocation.getQuantity().scale() > 4
                || allocation.getSerialIds() == null
                || allocation.getSerialIds().stream().anyMatch(id ->
                        id == null || id <= 0))
        {
            throw new ServiceException("发货分配包含无效标识或数量");
        }
    }

    private static <T> List<Long> distinctSorted(List<T> values,
            java.util.function.Function<T, Long> extractor)
    {
        if (values == null)
        {
            return List.of();
        }
        return values.stream().map(extractor).filter(Objects::nonNull)
                .distinct().sorted().toList();
    }

    private static void requireCompleteLocks(List<Long> expected,
            List<Long> actual, String message)
    {
        if (actual == null || !expected.equals(actual))
        {
            throw new ServiceException(message);
        }
    }

    private static void attachAvailableSerials(
            List<InvShipmentAllocationCandidate> candidates,
            List<InvShipmentSerialCandidate> serialFacts)
    {
        Map<Long, List<InvShipmentSerialCandidate>> byBalance =
                new HashMap<>();
        if (serialFacts != null)
        {
            for (InvShipmentSerialCandidate serial : serialFacts)
            {
                if (serial != null && serial.getBalanceId() != null
                        && "available".equals(serial.getSerialStatus()))
                {
                    byBalance.computeIfAbsent(serial.getBalanceId(),
                            ignored -> new ArrayList<>()).add(serial);
                }
            }
        }
        if (candidates != null)
        {
            for (InvShipmentAllocationCandidate candidate : candidates)
            {
                if (candidate != null)
                {
                    candidate.setSerials(byBalance.getOrDefault(
                            candidate.getBalanceId(), List.of()));
                }
            }
        }
    }

    private static void validateExactRecommendation(
            InvTransferShipmentCreateRequest request,
            InvTransferRevisionVo revision,
            InvTransferShipmentPlanComposer.Composition composition)
    {
        if (!composition.canCreateShipment()
                || !Objects.equals(request.getPlanVersion(),
                        composition.planVersion())
                || !Objects.equals(request.getSealedRevisionId(),
                        revision.getRevisionId()))
        {
            throw stalePlan();
        }
        List<AllocationSignature> requested = request.getAllocations().stream()
                .map(AllocationSignature::fromRequest).sorted().toList();
        List<AllocationSignature> expected = composition.lines().stream()
                .filter(line -> InvTransferShipmentAllocationPolicy.READY
                        .equals(line.plan().recommendationStatus()))
                .flatMap(line -> line.plan().allocations().stream().map(
                        allocation -> AllocationSignature.fromPlan(
                                line.detail().getDetailId(), allocation)))
                .sorted().toList();
        if (!requested.equals(expected))
        {
            throw stalePlan();
        }
    }

    private static ServiceException stalePlan()
    {
        return new ServiceException("发货规划已变化，请重新获取后再提交");
    }

    private static Map<Long, InvLockedShipmentBalance> indexBalances(
            List<InvLockedShipmentBalance> balances)
    {
        Map<Long, InvLockedShipmentBalance> result = new HashMap<>();
        if (balances != null)
        {
            for (InvLockedShipmentBalance balance : balances)
            {
                if (balance == null || balance.getBalanceId() == null
                        || result.put(balance.getBalanceId(), balance) != null)
                {
                    throw new ServiceException("明细库存余额锁定事实重复或无效");
                }
            }
        }
        return result;
    }

    private static PreparedMutation prepareMutation(
            InvTransferShipmentPlanComposer.Composition composition,
            Map<Long, InvLockedShipmentBalance> balanceById)
    {
        List<PreparedAllocation> allocations = new ArrayList<>();
        Map<Long, BigDecimal> quantityByDetail = new LinkedHashMap<>();
        Map<Long, BigDecimal> costByDetail = new LinkedHashMap<>();
        Map<Long, BigDecimal> consumedByBalance = new HashMap<>();
        for (InvTransferShipmentPlanComposer.Line line : composition.lines())
        {
            if (!InvTransferShipmentAllocationPolicy.READY.equals(
                    line.plan().recommendationStatus()))
            {
                continue;
            }
            for (InvTransferShipmentAllocationPolicy.Allocation allocation
                    : line.plan().allocations())
            {
                InvLockedShipmentBalance balance = balanceById.get(
                        allocation.balanceId());
                if (balance == null
                        || !Objects.equals(balance.getVersion(),
                                allocation.balanceVersion())
                        || !Objects.equals(balance.getLotId(),
                                allocation.lotId())
                        || !Objects.equals(balance.getLocationId(),
                                allocation.locationId())
                        || !Objects.equals(balance.getItemType(),
                                line.itemType())
                        || !Objects.equals(balance.getItemId(), line.itemId())
                        || balance.getCostPrice() == null
                        || balance.getCurrentQuantity() == null
                        || balance.getAvailableQuantity() == null)
                {
                    throw new ServiceException("服务端规划与锁定库存事实不一致");
                }
                BigDecimal quantity = allocation.suggestedQuantity();
                BigDecimal cost = quantity.multiply(balance.getCostPrice())
                        .setScale(6, RoundingMode.HALF_UP);
                BigDecimal consumedBefore = consumedByBalance.getOrDefault(
                        balance.getBalanceId(), BigDecimal.ZERO);
                BigDecimal beforeQuantity = balance.getCurrentQuantity()
                        .subtract(consumedBefore);
                BigDecimal afterQuantity = beforeQuantity.subtract(quantity);
                if (afterQuantity.signum() < 0)
                {
                    throw new ServiceException("服务端规划超出锁定库存余额");
                }
                allocations.add(new PreparedAllocation(line, allocation,
                        balance, cost, beforeQuantity, afterQuantity));
                consumedByBalance.put(balance.getBalanceId(),
                        consumedBefore.add(quantity));
                Long detailId = line.detail().getDetailId();
                quantityByDetail.merge(detailId, quantity, BigDecimal::add);
                costByDetail.merge(detailId, cost, BigDecimal::add);
            }
        }
        if (allocations.isEmpty())
        {
            throw new ServiceException("服务端规划没有可执行发货分配");
        }
        return new PreparedMutation(List.copyOf(allocations),
                Map.copyOf(quantityByDetail), Map.copyOf(costByDetail));
    }

    private InvTransferShipment insertShipment(InvTransferOrder order,
            InvTransferRevisionVo revision,
            InvTransferShipmentPlanComposer.Composition composition,
            String requestId, Long warehouseId, Date occurredTime,
            String username)
    {
        InvTransferShipment shipment = new InvTransferShipment();
        shipment.setTransferId(order.getTransferId());
        shipment.setShipmentNo(deterministicShipmentNo(requestId,
                occurredTime));
        shipment.setWarehouseId(warehouseId);
        shipment.setWarehouseDeptId(warehouseId);
        shipment.setSourceLocationDeptId(warehouseId);
        shipment.setInventoryWriteVersion(
                InvTransferShipmentWriteVersions.V2_DETAIL);
        shipment.setCommandRequestId(requestId);
        shipment.setPlanVersion(composition.planVersion());
        shipment.setSealedRevisionId(revision.getRevisionId());
        shipment.setReconcileBatch(
                composition.stockMode().getLastReconcileBatch());
        shipment.setStatus(InvStatusConstants.PENDING_RECEIVE);
        shipment.setShippedBy(username);
        shipment.setShippedTime(occurredTime);
        shipment.setCreateBy(username);
        shipment.setRemark("V2明细库存发货；旧收货入口禁止处理");
        if (shipmentMapper.insertShipment(shipment) != 1
                || shipment.getShipmentId() == null)
        {
            throw new ServiceException("V2发货批次写入失败");
        }
        return shipment;
    }

    private Map<Long, InvTransferShipmentDetail> insertShipmentDetails(
            InvTransferShipment shipment,
            InvTransferShipmentPlanComposer.Composition composition,
            PreparedMutation prepared, String username)
    {
        Map<Long, InvTransferShipmentDetail> result = new HashMap<>();
        for (InvTransferShipmentPlanComposer.Line line : composition.lines())
        {
            Long detailId = line.detail().getDetailId();
            BigDecimal quantity = prepared.quantityByDetail().get(detailId);
            if (quantity == null)
            {
                continue;
            }
            BigDecimal cost = prepared.costByDetail().get(detailId);
            InvTransferShipmentDetail shipmentDetail =
                    new InvTransferShipmentDetail();
            shipmentDetail.setShipmentId(shipment.getShipmentId());
            shipmentDetail.setTransferId(shipment.getTransferId());
            shipmentDetail.setTransferDetailId(detailId);
            shipmentDetail.setItemType(line.itemType());
            shipmentDetail.setItemId(line.itemId());
            shipmentDetail.setItemCode(line.detail().getItemCode());
            shipmentDetail.setItemName(line.detail().getItemName());
            shipmentDetail.setProductId(line.detail().getProductId());
            shipmentDetail.setProductName(line.detail().getProductName());
            shipmentDetail.setPlannedQuantity(line.detail().getQuantity());
            shipmentDetail.setShippedQuantity(quantity);
            shipmentDetail.setReceivedQuantity(BigDecimal.ZERO);
            shipmentDetail.setCostPrice(cost.divide(quantity, 6,
                    RoundingMode.HALF_UP));
            if (shipmentDetailMapper.insertShipmentDetail(shipmentDetail) != 1
                    || shipmentDetail.getShipmentDetailId() == null)
            {
                throw new ServiceException("V2发货明细写入失败");
            }
            result.put(detailId, shipmentDetail);
        }
        return result;
    }

    private void applyDetailStockMutations(PreparedMutation prepared,
            Long warehouseId, String username)
    {
        Map<Long, BigDecimal> quantityByBalance = new HashMap<>();
        Map<Long, BigDecimal> costByBalance = new HashMap<>();
        Map<Long, InvLockedShipmentBalance> balanceById = new HashMap<>();
        for (PreparedAllocation item : prepared.allocations())
        {
            Long balanceId = item.balance().getBalanceId();
            balanceById.put(balanceId, item.balance());
            quantityByBalance.merge(balanceId,
                    item.allocation().suggestedQuantity(), BigDecimal::add);
            costByBalance.merge(balanceId, item.cost(), BigDecimal::add);
        }
        for (Long balanceId : quantityByBalance.keySet().stream()
                .sorted().toList())
        {
            InvLockedShipmentBalance balance = balanceById.get(balanceId);
            if (creationMapper.deductBalance(balanceId,
                    balance.getVersion(), quantityByBalance.get(balanceId),
                    costByBalance.get(balanceId), username) != 1)
            {
                throw new ServiceException("批次库位库存扣减冲突，业务操作已回滚");
            }
        }
        for (PreparedAllocation item : prepared.allocations())
        {
            InvLockedShipmentBalance balance = item.balance();
            for (InvTransferShipmentAllocationPolicy.Serial serial
                    : item.allocation().serials())
            {
                if (creationMapper.markSerialShipped(serial.serialId(),
                        balance.getBalanceId(), warehouseId,
                        balance.getLotId(), balance.getLocationId(),
                        username) != 1)
                {
                    throw new ServiceException("序列号状态变更冲突，业务操作已回滚");
                }
            }
        }
    }

    private void persistAuditAndLedgers(InvTransferOrder order,
            InvTransferShipment shipment,
            InvTransferShipmentPlanComposer.Composition composition,
            PreparedMutation prepared,
            Map<Long, InvTransferShipmentDetail> shipmentDetails,
            InvTransferReservationService.ShipmentConsumption consumption,
            String requestId, Long warehouseId, Date occurredTime,
            String username)
    {
        Map<Long, Long> stockLogByDetail = new HashMap<>();
        for (Map.Entry<Long, BigDecimal> quantity
                : prepared.quantityByDetail().entrySet())
        {
            InvTransferReservationService.StockConsumption stock =
                    consumption.byTransferDetailId().get(quantity.getKey());
            InvTransferShipmentDetail shipmentDetail = shipmentDetails.get(
                    quantity.getKey());
            if (stock == null || shipmentDetail == null)
            {
                throw new ServiceException("汇总库存消费结果与发货明细不一致");
            }
            InvStockLog log = new InvStockLog();
            log.setItemType(shipmentDetail.getItemType());
            log.setItemId(shipmentDetail.getItemId());
            log.setProductId(shipmentDetail.getProductId());
            log.setShopDeptId(warehouseId);
            log.setWarehouseId(warehouseId);
            log.setMovementType(InvStatusConstants.MOVEMENT_TRANSFER_OUT);
            log.setBusinessType(InvTransferTypes.stockBusinessType(
                    order.getTransferType(), order.getSourceBusinessType()));
            log.setBusinessId(order.getTransferId());
            log.setBusinessNo(order.getOrderNo());
            log.setChangeQuantity(quantity.getValue().negate());
            log.setBeforeQuantity(stock.beforeQuantity());
            log.setAfterQuantity(stock.afterQuantity());
            log.setCostPrice(shipmentDetail.getCostPrice());
            log.setCreateBy(username);
            log.setCreateTime(occurredTime);
            log.setRemark("V2调拨出库→" + order.getToDeptName());
            if (stockLogMapper.insertInvStockLog(log) != 1
                    || log.getLogId() == null)
            {
                throw new ServiceException("汇总库存日志写入失败");
            }
            stockLogByDetail.put(quantity.getKey(), log.getLogId());
        }

        for (PreparedAllocation item : prepared.allocations())
        {
            Long detailId = item.line().detail().getDetailId();
            InvTransferShipmentDetail shipmentDetail = shipmentDetails.get(
                    detailId);
            Long summaryLogId = stockLogByDetail.get(detailId);
            InvTransferShipmentAllocationRecord record = allocationRecord(
                    shipment, shipmentDetail, item, summaryLogId, requestId,
                    username);
            if (creationMapper.insertAllocation(record) != 1
                    || record.getAllocationId() == null)
            {
                throw new ServiceException("发货分配审计写入失败");
            }
            List<InvTransferShipmentSerialRecord> serialRecords =
                    serialRecords(shipment, record, item, username);
            if (!serialRecords.isEmpty()
                    && creationMapper.batchInsertShipmentSerials(
                            serialRecords) != serialRecords.size())
            {
                throw new ServiceException("发货序列号审计写入失败");
            }
            InvStockLedgerDetailRecord ledger = ledgerRecord(order,
                    shipment, record, item, summaryLogId, requestId,
                    warehouseId, occurredTime, username);
            if (creationMapper.insertStockLedger(ledger) != 1
                    || ledger.getLedgerId() == null)
            {
                throw new ServiceException("明细库存不可变流水写入失败");
            }
        }
    }

    private static InvTransferShipmentAllocationRecord allocationRecord(
            InvTransferShipment shipment,
            InvTransferShipmentDetail shipmentDetail,
            PreparedAllocation item, Long summaryLogId, String requestId,
            String username)
    {
        InvLockedShipmentBalance balance = item.balance();
        BigDecimal quantity = item.allocation().suggestedQuantity();
        InvTransferShipmentAllocationRecord record =
                new InvTransferShipmentAllocationRecord();
        record.setShipmentId(shipment.getShipmentId());
        record.setShipmentDetailId(shipmentDetail.getShipmentDetailId());
        record.setTransferId(shipment.getTransferId());
        record.setTransferDetailId(item.line().detail().getDetailId());
        record.setBalanceId(balance.getBalanceId());
        record.setLotId(balance.getLotId());
        record.setLocationId(balance.getLocationId());
        record.setPolicyRank(item.allocation().policyRank());
        record.setAllocationPolicy(item.line().plan().allocationPolicy());
        record.setTrackingPolicy(item.line().plan().trackingPolicy());
        record.setAllocatedQuantity(quantity);
        record.setBalanceVersionBefore(balance.getVersion());
        record.setBalanceVersionAfter(balance.getVersion() + 1);
        record.setBeforeQuantity(item.beforeQuantity());
        record.setAfterQuantity(item.afterQuantity());
        record.setCostPrice(balance.getCostPrice());
        record.setTotalCost(item.cost());
        record.setSummaryStockLogId(summaryLogId);
        record.setCommandRequestId(requestId);
        record.setCreateBy(username);
        return record;
    }

    private static List<InvTransferShipmentSerialRecord> serialRecords(
            InvTransferShipment shipment,
            InvTransferShipmentAllocationRecord allocation,
            PreparedAllocation item, String username)
    {
        List<InvTransferShipmentSerialRecord> result = new ArrayList<>();
        for (InvTransferShipmentAllocationPolicy.Serial serial
                : item.allocation().serials())
        {
            InvTransferShipmentSerialRecord record =
                    new InvTransferShipmentSerialRecord();
            record.setAllocationId(allocation.getAllocationId());
            record.setShipmentId(shipment.getShipmentId());
            record.setSerialId(serial.serialId());
            record.setSerialNoSnapshot(serial.serialNo());
            record.setItemType(item.line().itemType());
            record.setItemId(item.line().itemId());
            record.setSourceBalanceId(item.balance().getBalanceId());
            record.setSourceLotId(item.balance().getLotId());
            record.setSourceLocationId(item.balance().getLocationId());
            record.setStatusBefore("available");
            record.setStatusAfter("shipped");
            record.setCreateBy(username);
            result.add(record);
        }
        return result;
    }

    private static InvStockLedgerDetailRecord ledgerRecord(
            InvTransferOrder order, InvTransferShipment shipment,
            InvTransferShipmentAllocationRecord allocation,
            PreparedAllocation item, Long summaryLogId, String requestId,
            Long warehouseId, Date occurredTime, String username)
    {
        InvStockLedgerDetailRecord ledger = new InvStockLedgerDetailRecord();
        ledger.setRequestId(sha256("transfer-shipment-ledger-v1|"
                + requestId + '|' + allocation.getAllocationId()));
        ledger.setCommandRequestId(requestId);
        ledger.setShipmentAllocationId(allocation.getAllocationId());
        ledger.setSummaryStockLogId(summaryLogId);
        ledger.setWarehouseId(warehouseId);
        ledger.setItemType(item.line().itemType());
        ledger.setItemId(item.line().itemId());
        ledger.setProductId(item.balance().getProductId());
        ledger.setMovementType(InvStatusConstants.MOVEMENT_TRANSFER_OUT);
        ledger.setBusinessType("transfer_shipment_v2");
        ledger.setBusinessId(shipment.getShipmentId());
        ledger.setBusinessNo(shipment.getShipmentNo());
        ledger.setFromBalanceId(item.balance().getBalanceId());
        ledger.setFromLotId(item.balance().getLotId());
        ledger.setFromLocationId(item.balance().getLocationId());
        ledger.setChangeQuantity(item.allocation().suggestedQuantity()
                .negate());
        ledger.setBeforeQuantity(item.beforeQuantity());
        ledger.setAfterQuantity(item.afterQuantity());
        ledger.setCostPrice(item.balance().getCostPrice());
        ledger.setTotalCost(item.cost().negate());
        ledger.setOperatorUserId(SecurityUtils.getUserId());
        ledger.setOperatorName(username);
        ledger.setOccurredTime(occurredTime);
        ledger.setCreateBy(username);
        ledger.setRemark("V2调拨出库；来源调拨单 " + order.getOrderNo());
        return ledger;
    }

    private void updateTransfer(InvTransferOrder order,
            List<InvTransferDetail> details,
            Map<Long, BigDecimal> quantityByDetail,
            InvTransferShipment shipment, Date occurredTime, String username)
    {
        for (InvTransferDetail detail : details)
        {
            BigDecimal quantity = quantityByDetail.get(detail.getDetailId());
            if (quantity == null)
            {
                continue;
            }
            detail.setDeliveredQuantity(nullToZero(
                    detail.getDeliveredQuantity()).add(quantity));
            if (detailMapper.updateDeliveredQuantity(detail) != 1)
            {
                throw new ServiceException("调拨明细累计发货量更新失败");
            }
        }
        String previousStatus = order.getStatus();
        InvTransferOrder update = new InvTransferOrder();
        update.setTransferId(order.getTransferId());
        update.setStatus(InvTransferLifecyclePolicy.afterDelivery(
                order.getStatus(), allRequestedDelivered(details)));
        update.setDeliveredTime(occurredTime);
        update.setUpdateBy(username);
        if (orderMapper.updateInvTransferOrder(update) != 1)
        {
            throw new ServiceException("调拨单发货状态更新失败");
        }
        InvTransferStatusLog log = new InvTransferStatusLog();
        log.setTransferId(order.getTransferId());
        log.setFromStatus(previousStatus);
        log.setToStatus(update.getStatus());
        log.setAction("deliver_v2");
        log.setOperatorId(SecurityUtils.getUserId());
        log.setOperatorName(username);
        log.setReason("V2明细库存发货批次 " + shipment.getShipmentNo());
        if (statusLogMapper.insertLog(log) != 1)
        {
            throw new ServiceException("调拨发货状态日志写入失败");
        }
    }

    private static boolean allRequestedDelivered(
            List<InvTransferDetail> details)
    {
        return !details.isEmpty() && details.stream().allMatch(detail ->
                nullToZero(detail.getQuantity()).compareTo(
                        nullToZero(detail.getDeliveredQuantity())) == 0);
    }

    private static String deterministicShipmentNo(String requestId,
            Date occurredTime)
    {
        String date = LocalDate.ofInstant(occurredTime.toInstant(),
                BUSINESS_ZONE).format(DateTimeFormatter.BASIC_ISO_DATE);
        return "TS2" + date + sha256(requestId).substring(0, 32);
    }

    private static String sha256(String value)
    {
        try
        {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(
                    value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : hash)
            {
                result.append(String.format("%02x", item & 0xff));
            }
            return result.toString();
        }
        catch (NoSuchAlgorithmException impossible)
        {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String requireUsername()
    {
        String username = SecurityUtils.getUsername();
        if (isBlank(username))
        {
            throw new ServiceException("发货命令缺少有效登录用户");
        }
        return username;
    }

    private static String identifier(Long value)
    {
        if (value == null || value <= 0)
        {
            throw new ServiceException("V2发货结果包含无效业务标识");
        }
        return value.toString();
    }

    private static boolean isBlank(String value)
    {
        return value == null || value.isBlank();
    }

    private static BigDecimal nullToZero(BigDecimal value)
    {
        return value == null ? BigDecimal.ZERO : value;
    }

    private record PreparedAllocation(
            InvTransferShipmentPlanComposer.Line line,
            InvTransferShipmentAllocationPolicy.Allocation allocation,
            InvLockedShipmentBalance balance, BigDecimal cost,
            BigDecimal beforeQuantity, BigDecimal afterQuantity)
    {
    }

    private record PreparedMutation(List<PreparedAllocation> allocations,
            Map<Long, BigDecimal> quantityByDetail,
            Map<Long, BigDecimal> costByDetail)
    {
    }

    private record AllocationSignature(Long transferDetailId, Long balanceId,
            Long lotId, Long locationId, String quantity,
            List<Long> serialIds) implements Comparable<AllocationSignature>
    {
        private static AllocationSignature fromRequest(
                InvTransferShipmentAllocationRequest value)
        {
            return new AllocationSignature(value.getTransferDetailId(),
                    value.getBalanceId(), value.getLotId(),
                    value.getLocationId(), decimal(value.getQuantity()),
                    value.getSerialIds().stream().sorted().toList());
        }

        private static AllocationSignature fromPlan(Long detailId,
                InvTransferShipmentAllocationPolicy.Allocation value)
        {
            return new AllocationSignature(detailId, value.balanceId(),
                    value.lotId(), value.locationId(),
                    decimal(value.suggestedQuantity()),
                    value.serials().stream().map(
                            InvTransferShipmentAllocationPolicy.Serial::serialId)
                            .sorted().toList());
        }

        @Override
        public int compareTo(AllocationSignature other)
        {
            int compared = transferDetailId.compareTo(
                    other.transferDetailId);
            return compared != 0 ? compared : balanceId.compareTo(
                    other.balanceId);
        }

        private static String decimal(BigDecimal value)
        {
            BigDecimal normalized = value.stripTrailingZeros();
            return normalized.signum() == 0 ? "0"
                    : normalized.toPlainString();
        }
    }
}
