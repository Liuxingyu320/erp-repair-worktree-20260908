package com.erp.inventory.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.inventory.constant.InvItemTypes;
import com.erp.inventory.constant.InvStatusConstants;
import com.erp.inventory.constant.InvTransferRevisionStatuses;
import com.erp.inventory.domain.InvTransferDetail;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.transfer.InvItemFulfillmentPolicy;
import com.erp.inventory.domain.transfer.InvShipmentAllocationCandidate;
import com.erp.inventory.domain.transfer.InvShipmentPlanningItemKey;
import com.erp.inventory.domain.transfer.InvShipmentSerialCandidate;
import com.erp.inventory.domain.transfer.InvTransferShipmentAllocationPolicy;
import com.erp.inventory.domain.transfer.InvTransferShipmentPlanComposer;
import com.erp.inventory.domain.transfer.InvWarehouseStockMode;
import com.erp.inventory.domain.vo.InvTransferRevisionHistoryVo;
import com.erp.inventory.domain.vo.InvTransferRevisionVo;
import com.erp.inventory.domain.vo.InvTransferShipmentPlanningVo;
import com.erp.inventory.mapper.InvDeptScopeMapper;
import com.erp.inventory.mapper.InvTransferDetailMapper;
import com.erp.inventory.mapper.InvTransferOrderMapper;
import com.erp.inventory.mapper.InvTransferShipmentPlanningMapper;

/** Produces read-only, fail-closed shipment suggestions from detail stock. */
@Service
public class InvTransferShipmentPlanningService extends InvBaseService
{
    private static final Set<String> DELIVERABLE_STATUSES = Set.of(
            InvStatusConstants.APPROVED,
            InvStatusConstants.RESERVED,
            InvStatusConstants.PARTIAL_DELIVERED);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final InvTransferOrderMapper orderMapper;
    private final InvTransferDetailMapper detailMapper;
    private final InvTransferShipmentPlanningMapper planningMapper;
    private final InvTransferRevisionService revisionService;
    private final Clock clock;

    @Autowired
    public InvTransferShipmentPlanningService(
            InvTransferOrderMapper orderMapper,
            InvTransferDetailMapper detailMapper,
            InvTransferShipmentPlanningMapper planningMapper,
            InvTransferRevisionService revisionService,
            InvDeptScopeMapper deptScopeMapper,
            ShopScopeService shopScopeService)
    {
        this(orderMapper, detailMapper, planningMapper, revisionService,
                deptScopeMapper, shopScopeService, Clock.systemUTC());
    }

    InvTransferShipmentPlanningService(
            InvTransferOrderMapper orderMapper,
            InvTransferDetailMapper detailMapper,
            InvTransferShipmentPlanningMapper planningMapper,
            InvTransferRevisionService revisionService,
            InvDeptScopeMapper deptScopeMapper,
            ShopScopeService shopScopeService,
            Clock clock)
    {
        this.orderMapper = orderMapper;
        this.detailMapper = detailMapper;
        this.planningMapper = planningMapper;
        this.revisionService = revisionService;
        this.deptScopeMapper = deptScopeMapper;
        this.shopScopeService = shopScopeService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public InvTransferShipmentPlanningVo getPlanning(Long transferId,
            Long selectedShopDeptId)
    {
        if (transferId == null || transferId <= 0)
        {
            throw new ServiceException("调拨单标识无效");
        }
        resolveAndValidateShopDept(selectedShopDeptId);
        InvTransferOrder order = orderMapper
                .selectInvTransferOrderById(transferId);
        if (order == null)
        {
            throw new ServiceException("调拨单不存在");
        }
        if (!transferId.equals(order.getTransferId())
                || order.getVersion() == null || order.getVersion() < 0)
        {
            throw new ServiceException("调拨单主数据归属或版本不可核验");
        }
        Long sourceWarehouseId = resolveLocationDeptId(
                order.getFromWarehouseId(), order.getFromDeptId());
        if (sourceWarehouseId == null || sourceWarehouseId <= 0)
        {
            throw new ServiceException("调拨单缺少有效发货仓库");
        }
        assertShopVisible(sourceWarehouseId, selectedShopDeptId,
                "无权读取该调拨单的发货规划");
        if (!DELIVERABLE_STATUSES.contains(order.getStatus()))
        {
            throw new ServiceException("调拨单当前状态不允许规划发货");
        }

        InvTransferRevisionVo revision = requireApprovedRevision(order);
        List<InvTransferDetail> details = detailMapper
                .selectByTransferId(transferId);
        if (details == null || details.isEmpty())
        {
            throw new ServiceException("调拨单缺少可核验明细");
        }
        validateDetails(details, transferId);

        InvWarehouseStockMode mode = planningMapper.selectWarehouseMode(
                sourceWarehouseId);
        boolean authoritative = InvTransferShipmentPlanComposer
                .canReadDetailStock(mode, sourceWarehouseId);

        List<InvShipmentPlanningItemKey> itemKeys = itemKeys(details);
        List<InvItemFulfillmentPolicy> policies = itemKeys.isEmpty()
                ? List.of() : safeList(planningMapper
                        .selectFulfillmentPolicies(itemKeys));
        List<InvShipmentAllocationCandidate> candidates = List.of();
        if (authoritative && !itemKeys.isEmpty())
        {
            candidates = safeList(planningMapper.selectEligibleBalances(
                    sourceWarehouseId, itemKeys));
            attachSerials(candidates);
        }
        Instant queriedAt = clock.instant();
        InvTransferShipmentPlanComposer.Composition composition =
                InvTransferShipmentPlanComposer.compose(order, revision,
                        mode, details, policies, candidates);
        List<InvTransferShipmentPlanningVo.Line> lines = composition.lines()
                .stream().map(line -> toLine(line.detail(), line.itemType(),
                        line.itemId(), line.plan())).toList();
        String status = InvStatusConstants.PARTIAL_DELIVERED.equals(
                order.getStatus()) ? "PARTIALLY_SHIPPED" : "APPROVED";
        String timestamp = DateTimeFormatter.ISO_INSTANT.format(queriedAt);
        InvWarehouseStockMode normalizedMode = composition.stockMode();

        return new InvTransferShipmentPlanningVo(
                identifier(order.getTransferId()),
                requireText(order.getOrderNo(), "调拨单号缺失"),
                composition.planVersion(),
                status,
                resolveName(order.getFromDeptName(), sourceWarehouseId,
                        "来源组织名称缺失"),
                resolveName(order.getToDeptName(), resolveLocationDeptId(
                        order.getToWarehouseId(), order.getToDeptId()),
                        "目标组织名称缺失"),
                new InvTransferShipmentPlanningVo.SealedRevision(
                        identifier(revision.getRevisionId()),
                        revision.getRevisionNo(),
                        revision.getSnapshotHash()),
                new InvTransferShipmentPlanningVo.StockAuthority(
                        normalizedMode.getWriteMode(),
                        normalizedMode.getReadMode(),
                        normalizedMode.getReconcileStatus(),
                        normalizedMode.getLastReconcileBatch(), "server",
                        timestamp, timestamp),
                List.copyOf(lines), composition.canCreateShipment(),
                composition.blockingReasons(), "server");
    }

    private InvTransferRevisionVo requireApprovedRevision(
            InvTransferOrder order)
    {
        InvTransferRevisionHistoryVo history = revisionService
                .getHistory(order);
        if (history == null
                || !order.getTransferId().equals(history.getTransferId())
                || history.getRevisions() == null
                || history.getRevisions().isEmpty())
        {
            throw new ServiceException("调拨单缺少可核验审批版本");
        }
        if (history.getRevisions().stream().anyMatch(revision ->
                revision == null || revision.getRevisionNo() == null))
        {
            throw new ServiceException("调拨单业务版本序列不可核验");
        }
        InvTransferRevisionVo latest = history.getRevisions().stream()
                .max(Comparator.comparing(
                        InvTransferRevisionVo::getRevisionNo))
                .orElseThrow(() -> new ServiceException(
                        "调拨单缺少可核验审批版本"));
        if (!InvTransferRevisionStatuses.APPROVED.equals(
                        latest.getStatus())
                || !latest.getRevisionNo().equals(
                        history.getCurrentRevisionNo())
                || !InvTransferRevisionStatuses.APPROVED.equals(
                        history.getCurrentRevisionStatus())
                || latest.getRevisionId() == null
                || latest.getRevisionNo() == null
                || latest.getRevisionNo() <= 0
                || latest.getSnapshotHash() == null
                || !latest.getSnapshotHash().matches("[a-f0-9]{64}"))
        {
            throw new ServiceException("调拨单最新业务版本未审批或不可核验");
        }
        return latest;
    }

    private static void validateDetails(List<InvTransferDetail> details,
            Long transferId)
    {
        Set<Long> detailIds = new java.util.HashSet<>();
        for (InvTransferDetail detail : details)
        {
            if (detail == null || detail.getDetailId() == null
                    || detail.getDetailId() <= 0
                    || !transferId.equals(detail.getTransferId())
                    || !detailIds.add(detail.getDetailId()))
            {
                throw new ServiceException("调拨明细归属、标识或唯一性不可核验");
            }
        }
    }

    private void attachSerials(
            List<InvShipmentAllocationCandidate> candidates)
    {
        List<Long> balanceIds = candidates.stream()
                .filter(candidate -> candidate != null
                        && candidate.getBalanceId() != null)
                .map(InvShipmentAllocationCandidate::getBalanceId)
                .distinct().toList();
        if (balanceIds.isEmpty())
        {
            return;
        }
        Map<Long, List<InvShipmentSerialCandidate>> byBalance =
                new HashMap<>();
        for (InvShipmentSerialCandidate serial : safeList(
                planningMapper.selectAvailableSerials(balanceIds)))
        {
            if (serial != null && serial.getBalanceId() != null)
            {
                byBalance.computeIfAbsent(serial.getBalanceId(),
                        ignored -> new ArrayList<>()).add(serial);
            }
        }
        for (InvShipmentAllocationCandidate candidate : candidates)
        {
            if (candidate != null)
            {
                candidate.setSerials(byBalance.getOrDefault(
                        candidate.getBalanceId(), List.of()));
            }
        }
    }

    private static List<InvShipmentPlanningItemKey> itemKeys(
            List<InvTransferDetail> details)
    {
        Map<String, InvShipmentPlanningItemKey> unique =
                new LinkedHashMap<>();
        for (InvTransferDetail detail : details)
        {
            if (detail == null || remaining(detail).signum() <= 0)
            {
                continue;
            }
            String itemType = InvItemTypes.normalize(detail.getItemType());
            Long itemId = InvItemTypes.resolveItemId(itemType,
                    detail.getItemId(), detail.getProductId());
            if (itemId != null && itemId > 0)
            {
                unique.putIfAbsent(itemKey(itemType, itemId),
                        new InvShipmentPlanningItemKey(itemType, itemId));
            }
        }
        return List.copyOf(unique.values());
    }

    private static InvTransferShipmentPlanningVo.Line toLine(
            InvTransferDetail detail, String itemType, Long itemId,
            InvTransferShipmentAllocationPolicy.LinePlan plan)
    {
        List<InvTransferShipmentPlanningVo.Allocation> allocations =
                plan.allocations().stream().map(allocation ->
                        new InvTransferShipmentPlanningVo.Allocation(
                                identifier(allocation.balanceId()),
                                identifier(allocation.lotId()),
                                requireText(allocation.lotNo(),
                                        "库存批次号缺失"),
                                formatDate(allocation.expiryDate()),
                                formatInstant(allocation.receivedAt()),
                                identifier(allocation.locationId()),
                                requireText(allocation.locationCode(),
                                        "库存库位编码缺失"),
                                decimal(allocation.availableQuantity()),
                                decimal(allocation.suggestedQuantity()),
                                allocation.policyRank(), "eligible",
                                allocation.serials().stream().map(serial ->
                                        new InvTransferShipmentPlanningVo.Serial(
                                                identifier(serial.serialId()),
                                                maskSerial(serial.serialNo()),
                                                "available"))
                                        .toList()))
                .toList();
        return new InvTransferShipmentPlanningVo.Line(
                identifier(detail.getDetailId()), itemType,
                identifier(itemId), resolveItemCode(detail),
                resolveItemName(detail),
                requireText(detail.getUnit(), "调拨明细单位缺失"),
                decimal(plan.approvedQuantity()),
                decimal(plan.shippedQuantity()),
                decimal(plan.remainingQuantity()),
                decimal(plan.suggestedShipmentQuantity()),
                plan.allocationPolicy(), plan.trackingPolicy(),
                plan.recommendationStatus(), allocations,
                List.copyOf(plan.blockers()));
    }

    private String resolveName(String storedName, Long deptId,
            String message)
    {
        if (storedName != null && !storedName.isBlank())
        {
            return storedName.trim();
        }
        return requireText(deptId == null ? null
                : deptScopeMapper.selectDeptNameById(deptId), message);
    }

    private static String resolveItemCode(InvTransferDetail detail)
    {
        return requireText(firstNonBlank(detail.getItemCode(),
                detail.getProductCode()), "调拨明细物料编码缺失");
    }

    private static String resolveItemName(InvTransferDetail detail)
    {
        return requireText(firstNonBlank(detail.getItemName(),
                detail.getProductName()), "调拨明细物料名称缺失");
    }

    private static String firstNonBlank(String first, String second)
    {
        return first != null && !first.isBlank() ? first : second;
    }

    private static BigDecimal remaining(InvTransferDetail detail)
    {
        if (detail.getQuantity() == null)
        {
            return BigDecimal.ZERO;
        }
        return detail.getQuantity().subtract(detail.getDeliveredQuantity()
                == null ? BigDecimal.ZERO : detail.getDeliveredQuantity());
    }

    private static Long resolveLocationDeptId(Long warehouseId, Long deptId)
    {
        return warehouseId != null && warehouseId > 0
                ? warehouseId : deptId;
    }

    private static String itemKey(String itemType, Long itemId)
    {
        return itemType + ':' + itemId;
    }

    private static String identifier(Long value)
    {
        if (value == null || value <= 0)
        {
            throw new ServiceException("发货规划包含无效业务标识");
        }
        return Long.toString(value);
    }

    private static String requireText(String value, String message)
    {
        if (value == null || value.isBlank())
        {
            throw new ServiceException(message);
        }
        return value.trim();
    }

    private static String decimal(BigDecimal value)
    {
        if (value == null)
        {
            throw new ServiceException("发货规划包含空数量");
        }
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.signum() == 0 ? "0" : normalized.toPlainString();
    }

    private static String formatDate(Date value)
    {
        return value == null ? null : value.toInstant()
                .atZone(BUSINESS_ZONE).toLocalDate().toString();
    }

    private static String formatInstant(Date value)
    {
        if (value == null)
        {
            throw new ServiceException("发货规划缺少库存时间事实");
        }
        return DateTimeFormatter.ISO_INSTANT.format(value.toInstant());
    }

    private static String maskSerial(String serialNo)
    {
        String value = requireText(serialNo, "发货规划缺少序列号事实");
        int visible = Math.min(4, value.length());
        return "SN…" + value.substring(value.length() - visible);
    }

    private static <T> List<T> safeList(List<T> values)
    {
        return values == null ? List.of() : values;
    }
}
