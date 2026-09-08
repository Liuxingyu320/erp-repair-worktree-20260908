package com.erp.inventory.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.InvTransferShipment;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnReceiptPolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptLocationCandidate;
import com.erp.inventory.domain.transfer.InvTransferReceiptPlanningAllocationFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptPlanningSerialFact;
import com.erp.inventory.domain.transfer.InvWarehouseStockMode;
import com.erp.inventory.domain.vo.InvTransferReceiptDiscrepancyReturnReceiptPlanningVo;
import com.erp.inventory.mapper.InvDeptScopeMapper;
import com.erp.inventory.mapper.InvTransferOrderMapper;
import com.erp.inventory.mapper.InvTransferShipmentMapper;
import com.erp.inventory.mapper.InvTransferShipmentReceiptPlanningMapper;

/** Unwired read boundary for an expected damaged return receipt. */
@Service
public class
        InvTransferReceiptDiscrepancyReturnReceiptPlanningService
        extends InvBaseService
{
    private final InvTransferShipmentMapper shipmentMapper;
    private final InvTransferOrderMapper orderMapper;
    private final InvTransferShipmentReceiptPlanningMapper planningMapper;
    private final Clock clock;

    @Autowired
    public InvTransferReceiptDiscrepancyReturnReceiptPlanningService(
            InvTransferShipmentMapper shipmentMapper,
            InvTransferOrderMapper orderMapper,
            InvTransferShipmentReceiptPlanningMapper planningMapper,
            InvDeptScopeMapper deptScopeMapper,
            ShopScopeService shopScopeService)
    {
        this(shipmentMapper, orderMapper, planningMapper, deptScopeMapper,
                shopScopeService, Clock.systemUTC());
    }

    InvTransferReceiptDiscrepancyReturnReceiptPlanningService(
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
    public InvTransferReceiptDiscrepancyReturnReceiptPlanningVo getPlanning(
            Long shipmentId, Long selectedShopDeptId)
    {
        if (shipmentId == null || shipmentId <= 0)
        {
            throw new ServiceException("退回发货批次标识无效");
        }
        resolveAndValidateShopDept(selectedShopDeptId);
        InvTransferShipment shipment = shipmentMapper.selectById(shipmentId);
        if (shipment == null)
        {
            throw new ServiceException("退回发货批次不存在");
        }
        if (!shipmentId.equals(shipment.getShipmentId())
                || shipment.getTransferId() == null
                || shipment.getTransferId() <= 0)
        {
            throw new ServiceException("退回发货批次归属或标识不可核验");
        }
        InvTransferOrder order = orderMapper.selectInvTransferOrderById(
                shipment.getTransferId());
        if (order == null)
        {
            throw new ServiceException("退回子调拨不存在");
        }
        if (!shipment.getTransferId().equals(order.getTransferId())
                || order.getVersion() == null || order.getVersion() < 0)
        {
            throw new ServiceException("退回子调拨归属或版本不可核验");
        }
        Long targetWarehouseId = InvTransferDirectionPolicy
                .resolveStockLocationDeptId(order.getToWarehouseId(),
                        order.getToDeptId());
        if (targetWarehouseId == null || targetWarehouseId <= 0)
        {
            throw new ServiceException("退回子调拨缺少有效目标仓库");
        }
        assertShopVisible(targetWarehouseId, selectedShopDeptId,
                "无权读取该退回发货批次的收货规划");
        new InvTransferDirectionPolicy(deptScopeMapper, shopScopeService)
                .validateReceipt(order, selectedShopDeptId);

        InvWarehouseStockMode mode = planningMapper
                .selectTargetWarehouseMode(targetWarehouseId);
        List<InvTransferReceiptPlanningAllocationFact> allocations =
                safeList(planningMapper.selectAllocations(shipmentId));
        List<InvTransferReceiptPlanningSerialFact> serials = safeList(
                planningMapper.selectShipmentSerials(shipmentId));
        List<InvTransferReceiptLocationCandidate> locations = safeList(
                planningMapper.selectTargetLocations(targetWarehouseId));
        var plan = InvTransferReceiptDiscrepancyReturnReceiptPolicy.compose(
                shipment, order, mode, allocations, serials, locations);
        Instant generatedAt = clock.instant();

        List<InvTransferReceiptDiscrepancyReturnReceiptPlanningVo.Location>
                quarantineLocations = plan.quarantineLocations().stream()
                        .map(InvTransferReceiptDiscrepancyReturnReceiptPlanningService
                                ::toLocation)
                        .toList();
        List<InvTransferReceiptDiscrepancyReturnReceiptPlanningVo.Allocation>
                lines = plan.lines().stream().map(
                        InvTransferReceiptDiscrepancyReturnReceiptPlanningService
                                ::toLine)
                        .toList();
        InvWarehouseStockMode normalized = plan.targetStockMode();
        return new
                InvTransferReceiptDiscrepancyReturnReceiptPlanningVo(
                        identifier(shipment.getShipmentId(),
                                "退回发货批次标识无效"),
                        requireText(shipment.getShipmentNo(),
                                "退回发货批次号缺失"),
                        identifier(order.getTransferId(),
                                "退回子调拨标识无效"),
                        requireText(order.getOrderNo(), "退回子调拨单号缺失"),
                        plan.planVersion(),
                        requireText(shipment.getStatus(),
                                "退回发货批次状态缺失"),
                        resolveName(order.getFromDeptName(),
                                sourceWarehouse(order), "退回来源组织名称缺失"),
                        resolveName(order.getToDeptName(), targetWarehouseId,
                                "退回目标组织名称缺失"),
                        new InvTransferReceiptDiscrepancyReturnReceiptPlanningVo
                                .StockAuthority(
                                        identifier(targetWarehouseId,
                                                "退回目标仓库标识无效"),
                                        normalized.getWriteMode(),
                                        normalized.getReadMode(),
                                        normalized.getReconcileStatus(),
                                        normalized.getLastReconcileBatch()),
                        firstLocationId(quarantineLocations),
                        quarantineLocations, lines,
                        plan.canCreateReceipt(), plan.blockers(),
                        DateTimeFormatter.ISO_INSTANT.format(generatedAt),
                        InvTransferReceiptDiscrepancyReturnReceiptPolicy
                                .DATA_SOURCE);
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

    private static InvTransferReceiptDiscrepancyReturnReceiptPlanningVo
            .Location toLocation(InvTransferReceiptLocationCandidate value)
    {
        return new InvTransferReceiptDiscrepancyReturnReceiptPlanningVo
                .Location(
                        identifier(value.getLocationId(),
                                "退回隔离库位标识无效"),
                        requireText(value.getLocationCode(),
                                "退回隔离库位编码缺失"),
                        requireText(value.getLocationName(),
                                "退回隔离库位名称缺失"),
                        requireText(value.getLocationType(),
                                "退回隔离库位类型缺失"));
    }

    private static InvTransferReceiptDiscrepancyReturnReceiptPlanningVo
            .Allocation toLine(
                    InvTransferReceiptDiscrepancyReturnReceiptPolicy.Line line)
    {
        InvTransferReceiptPlanningAllocationFact fact = line.fact();
        if (fact == null)
        {
            throw new ServiceException("退回收货规划包含空来源分配");
        }
        return new InvTransferReceiptDiscrepancyReturnReceiptPlanningVo
                .Allocation(
                        identifier(fact.getAllocationId(),
                                "退回发货分配标识无效"),
                        identifier(fact.getShipmentDetailId(),
                                "退回发货明细标识无效"),
                        identifier(fact.getTransferDetailId(),
                                "退回调拨明细标识无效"),
                        requireText(fact.getItemType(),
                                "退回来源物料类型缺失"),
                        identifier(fact.getItemId(),
                                "退回来源物料标识无效"),
                        requireText(fact.getItemCode(),
                                "退回来源物料编码缺失"),
                        requireText(fact.getItemName(),
                                "退回来源物料名称缺失"),
                        requireText(fact.getUnit(), "退回来源单位缺失"),
                        identifier(fact.getSourceLotId(),
                                "退回来源批次标识无效"),
                        requireText(fact.getSourceLotNo(),
                                "退回来源批次号缺失"),
                        identifier(fact.getSourceLocationId(),
                                "退回来源库位标识无效"),
                        requireText(fact.getSourceLocationCode(),
                                "退回来源库位编码缺失"),
                        requireText(fact.getTrackingPolicy(),
                                "退回来源跟踪策略缺失"),
                        decimal(fact.getAllocatedQuantity()),
                        decimal(line.returnedQuantity()),
                        decimal(line.shortageQuantity()),
                        nullableDecimal(line.remainingQuantity()),
                        decimal(line.suggestedReturnedQuantity()),
                        line.status(), line.serials().stream().map(
                                InvTransferReceiptDiscrepancyReturnReceiptPlanningService
                                        ::toSerial)
                                .toList(), line.blockers());
    }

    private static InvTransferReceiptDiscrepancyReturnReceiptPlanningVo
            .Serial toSerial(InvTransferReceiptPlanningSerialFact value)
    {
        return new InvTransferReceiptDiscrepancyReturnReceiptPlanningVo
                .Serial(identifier(value.getSerialId(),
                        "退回发货序列号标识无效"),
                        maskSerial(value.getSerialNoSnapshot()),
                        requireText(value.getCurrentStatus(),
                                "退回序列号状态缺失"));
    }

    private static Long sourceWarehouse(InvTransferOrder order)
    {
        return InvTransferDirectionPolicy.resolveStockLocationDeptId(
                order.getFromWarehouseId(), order.getFromDeptId());
    }

    private static String firstLocationId(
            List<InvTransferReceiptDiscrepancyReturnReceiptPlanningVo
                    .Location> values)
    {
        return values.isEmpty() ? null : values.get(0).locationId();
    }

    private static String maskSerial(String serialNo)
    {
        String value = requireText(serialNo, "退回收货规划缺少序列号事实");
        int visible = Math.min(4, value.length());
        return "SN…" + value.substring(value.length() - visible);
    }

    private static String nullableDecimal(BigDecimal value)
    {
        return value == null ? null : decimal(value);
    }

    private static String decimal(BigDecimal value)
    {
        if (value == null)
        {
            throw new ServiceException("退回收货规划包含空数量");
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
        return value.toString();
    }

    private static String requireText(String value, String message)
    {
        if (value == null || value.isBlank())
        {
            throw new ServiceException(message);
        }
        return value.trim();
    }

    private static <T> List<T> safeList(List<T> values)
    {
        return values == null ? List.of() : List.copyOf(values);
    }
}
