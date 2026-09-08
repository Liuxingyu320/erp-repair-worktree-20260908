package com.erp.inventory.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.inventory.constant.InvTransferShipmentWriteVersions;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.InvTransferShipment;
import com.erp.inventory.domain.transfer.InvTransferReceiptLocationCandidate;
import com.erp.inventory.domain.transfer.InvTransferReceiptPlanningAllocationFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptPlanningSerialFact;
import com.erp.inventory.domain.transfer.InvTransferShipmentReceiptPlanComposer;
import com.erp.inventory.domain.transfer.InvWarehouseStockMode;
import com.erp.inventory.domain.vo.InvTransferShipmentReceiptPlanningVo;
import com.erp.inventory.mapper.InvDeptScopeMapper;
import com.erp.inventory.mapper.InvTransferOrderMapper;
import com.erp.inventory.mapper.InvTransferShipmentMapper;
import com.erp.inventory.mapper.InvTransferShipmentReceiptPlanningMapper;

/** Produces a read-only, fail-closed boundary for a V2 receipt command. */
@Service
public class InvTransferShipmentReceiptPlanningService extends InvBaseService
{
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final InvTransferShipmentMapper shipmentMapper;
    private final InvTransferOrderMapper orderMapper;
    private final InvTransferShipmentReceiptPlanningMapper planningMapper;
    private final Clock clock;

    @Autowired
    public InvTransferShipmentReceiptPlanningService(
            InvTransferShipmentMapper shipmentMapper,
            InvTransferOrderMapper orderMapper,
            InvTransferShipmentReceiptPlanningMapper planningMapper,
            InvDeptScopeMapper deptScopeMapper,
            ShopScopeService shopScopeService)
    {
        this(shipmentMapper, orderMapper, planningMapper, deptScopeMapper,
                shopScopeService, Clock.systemUTC());
    }

    InvTransferShipmentReceiptPlanningService(
            InvTransferShipmentMapper shipmentMapper,
            InvTransferOrderMapper orderMapper,
            InvTransferShipmentReceiptPlanningMapper planningMapper,
            InvDeptScopeMapper deptScopeMapper,
            ShopScopeService shopScopeService, Clock clock)
    {
        this.shipmentMapper = shipmentMapper;
        this.orderMapper = orderMapper;
        this.planningMapper = planningMapper;
        this.deptScopeMapper = deptScopeMapper;
        this.shopScopeService = shopScopeService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public InvTransferShipmentReceiptPlanningVo getPlanning(Long shipmentId,
            Long selectedShopDeptId)
    {
        if (shipmentId == null || shipmentId <= 0)
        {
            throw new ServiceException("发货批次标识无效");
        }
        resolveAndValidateShopDept(selectedShopDeptId);
        InvTransferShipment shipment = shipmentMapper.selectById(shipmentId);
        if (shipment == null)
        {
            throw new ServiceException("发货批次不存在");
        }
        if (!InvTransferShipmentWriteVersions.V2_DETAIL.equals(
                shipment.getInventoryWriteVersion()))
        {
            throw new ServiceException("旧发货批次不适用V2收货规划");
        }
        if (!shipmentId.equals(shipment.getShipmentId())
                || shipment.getTransferId() == null
                || shipment.getTransferId() <= 0)
        {
            throw new ServiceException("发货批次归属或标识不可核验");
        }

        InvTransferOrder order = orderMapper.selectInvTransferOrderById(
                shipment.getTransferId());
        if (order == null)
        {
            throw new ServiceException("调拨单不存在");
        }
        if (!shipment.getTransferId().equals(order.getTransferId())
                || order.getVersion() == null || order.getVersion() < 0)
        {
            throw new ServiceException("调拨单主数据归属或版本不可核验");
        }
        Long targetWarehouseId = InvTransferDirectionPolicy
                .resolveStockLocationDeptId(order.getToWarehouseId(),
                        order.getToDeptId());
        if (targetWarehouseId == null || targetWarehouseId <= 0)
        {
            throw new ServiceException("调拨单缺少有效目标仓库");
        }
        assertShopVisible(targetWarehouseId, selectedShopDeptId,
                "无权读取该发货批次的收货规划");
        new InvTransferDirectionPolicy(deptScopeMapper, shopScopeService)
                .validateReceipt(order, selectedShopDeptId);

        InvWarehouseStockMode mode = planningMapper
                .selectTargetWarehouseMode(targetWarehouseId);
        List<InvTransferReceiptPlanningAllocationFact> allocations =
                safeList(planningMapper.selectAllocations(shipmentId));
        List<InvTransferReceiptPlanningSerialFact> serials =
                safeList(planningMapper.selectShipmentSerials(shipmentId));
        List<InvTransferReceiptLocationCandidate> locations =
                safeList(planningMapper.selectTargetLocations(
                        targetWarehouseId));
        InvTransferShipmentReceiptPlanComposer.Composition composition =
                InvTransferShipmentReceiptPlanComposer.compose(shipment,
                        order, mode, allocations, serials, locations);
        Instant generatedAt = clock.instant();

        List<InvTransferShipmentReceiptPlanningVo.Location>
                acceptedLocations = composition.acceptedLocations().stream()
                        .map(InvTransferShipmentReceiptPlanningService
                                ::toLocation)
                        .toList();
        List<InvTransferShipmentReceiptPlanningVo.Location>
                quarantineLocations = composition.quarantineLocations()
                        .stream().map(
                                InvTransferShipmentReceiptPlanningService
                                        ::toLocation)
                        .toList();
        List<InvTransferShipmentReceiptPlanningVo.Allocation> lines =
                composition.lines().stream().map(
                        InvTransferShipmentReceiptPlanningService::toLine)
                        .toList();
        InvWarehouseStockMode normalized = composition.targetStockMode();

        return new InvTransferShipmentReceiptPlanningVo(
                identifier(shipment.getShipmentId(), "发货批次标识无效"),
                requireText(shipment.getShipmentNo(), "发货批次号缺失"),
                identifier(order.getTransferId(), "调拨单标识无效"),
                requireText(order.getOrderNo(), "调拨单号缺失"),
                composition.receiptPlanVersion(),
                requireText(shipment.getStatus(), "发货批次状态缺失"),
                resolveName(order.getFromDeptName(),
                        resolveSourceWarehouse(order), "来源组织名称缺失"),
                resolveName(order.getToDeptName(), targetWarehouseId,
                        "目标组织名称缺失"),
                new InvTransferShipmentReceiptPlanningVo.StockAuthority(
                        identifier(targetWarehouseId, "目标仓库标识无效"),
                        normalized.getWriteMode(), normalized.getReadMode(),
                        normalized.getReconcileStatus(),
                        normalized.getLastReconcileBatch()),
                firstLocationId(acceptedLocations),
                firstLocationId(quarantineLocations),
                acceptedLocations, quarantineLocations, lines,
                composition.canCreateReceipt(),
                composition.blockingReasons(),
                DateTimeFormatter.ISO_INSTANT.format(generatedAt), "server");
    }

    private String resolveName(String stored, Long deptId, String message)
    {
        if (stored != null && !stored.isBlank())
        {
            return stored.trim();
        }
        return requireText(deptId == null ? null
                : deptScopeMapper.selectDeptNameById(deptId), message);
    }

    private static Long resolveSourceWarehouse(InvTransferOrder order)
    {
        return InvTransferDirectionPolicy.resolveStockLocationDeptId(
                order.getFromWarehouseId(), order.getFromDeptId());
    }

    private static InvTransferShipmentReceiptPlanningVo.Location toLocation(
            InvTransferReceiptLocationCandidate value)
    {
        return new InvTransferShipmentReceiptPlanningVo.Location(
                identifier(value.getLocationId(), "收货库位标识无效"),
                requireText(value.getLocationCode(), "收货库位编码缺失"),
                requireText(value.getLocationName(), "收货库位名称缺失"),
                requireText(value.getLocationType(), "收货库位类型缺失"));
    }

    private static InvTransferShipmentReceiptPlanningVo.Allocation toLine(
            InvTransferShipmentReceiptPlanComposer.Line line)
    {
        InvTransferReceiptPlanningAllocationFact fact = line.fact();
        if (fact == null)
        {
            throw new ServiceException("收货规划包含空来源分配");
        }
        return new InvTransferShipmentReceiptPlanningVo.Allocation(
                identifier(fact.getAllocationId(), "发货分配标识无效"),
                identifier(fact.getShipmentDetailId(), "发货明细标识无效"),
                identifier(fact.getTransferDetailId(), "调拨明细标识无效"),
                requireText(fact.getItemType(), "来源分配物料类型缺失"),
                identifier(fact.getItemId(), "来源分配物料标识无效"),
                requireText(fact.getItemCode(), "来源分配物料编码缺失"),
                requireText(fact.getItemName(), "来源分配物料名称缺失"),
                requireText(fact.getUnit(), "来源分配单位缺失"),
                identifier(fact.getSourceLotId(), "来源批次标识无效"),
                requireText(fact.getSourceLotNo(), "来源批次号缺失"),
                trimToNull(fact.getSupplierBatchNo()),
                formatDate(fact.getProductionDate()),
                formatDate(fact.getExpiryDate()),
                identifier(fact.getSourceLocationId(), "来源库位标识无效"),
                requireText(fact.getSourceLocationCode(), "来源库位编码缺失"),
                requireText(fact.getTrackingPolicy(), "来源跟踪策略缺失"),
                decimal(fact.getAllocatedQuantity()),
                decimal(line.acceptedQuantity()),
                decimal(line.damagedQuantity()),
                decimal(line.shortageQuantity()),
                nullableDecimal(line.remainingQuantity()),
                decimal(line.suggestedAcceptedQuantity()),
                line.recommendationStatus(),
                line.serials().stream().map(
                        InvTransferShipmentReceiptPlanningService::toSerial)
                        .toList(), line.blockers());
    }

    private static InvTransferShipmentReceiptPlanningVo.Serial toSerial(
            InvTransferReceiptPlanningSerialFact value)
    {
        return new InvTransferShipmentReceiptPlanningVo.Serial(
                identifier(value.getSerialId(), "发货序列号标识无效"),
                maskSerial(value.getSerialNoSnapshot()),
                requireText(value.getCurrentStatus(), "序列号状态缺失"));
    }

    private static String firstLocationId(
            List<InvTransferShipmentReceiptPlanningVo.Location> values)
    {
        return values.isEmpty() ? null : values.get(0).locationId();
    }

    private static String maskSerial(String serialNo)
    {
        String value = requireText(serialNo, "收货规划缺少序列号事实");
        int visible = Math.min(4, value.length());
        return "SN…" + value.substring(value.length() - visible);
    }

    private static String formatDate(Date value)
    {
        if (value == null)
        {
            return null;
        }
        if (value instanceof java.sql.Date sqlDate)
        {
            return sqlDate.toLocalDate().toString();
        }
        return value.toInstant().atZone(BUSINESS_ZONE).toLocalDate()
                .toString();
    }

    private static String nullableDecimal(BigDecimal value)
    {
        return value == null ? null : decimal(value);
    }

    private static String decimal(BigDecimal value)
    {
        if (value == null)
        {
            throw new ServiceException("收货规划包含空数量");
        }
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.signum() == 0 ? "0" : normalized.toPlainString();
    }

    private static String identifier(Long value, String message)
    {
        if (value == null || value <= 0)
        {
            throw new ServiceException(message);
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

    private static String trimToNull(String value)
    {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static <T> List<T> safeList(List<T> values)
    {
        return values == null ? List.of() : values;
    }
}
