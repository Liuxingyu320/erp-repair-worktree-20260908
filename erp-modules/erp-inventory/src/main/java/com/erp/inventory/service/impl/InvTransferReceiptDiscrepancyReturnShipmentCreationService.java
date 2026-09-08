package com.erp.inventory.service.impl;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.constant.InvStatusConstants;
import com.erp.inventory.constant.InvTransferShipmentWriteVersions;
import com.erp.inventory.constant.InvTransferTypes;
import com.erp.inventory.domain.InvStockLog;
import com.erp.inventory.domain.InvTransferDetail;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.InvTransferRevision;
import com.erp.inventory.domain.InvTransferShipment;
import com.erp.inventory.domain.InvTransferShipmentDetail;
import com.erp.inventory.domain.InvTransferStatusLog;
import com.erp.inventory.domain.dto.InvTransferReceiptDiscrepancyReturnShipmentCreateRequest;
import com.erp.inventory.domain.transfer.InvStockLedgerDetailRecord;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnReservationLifecyclePolicy.SerialTransition;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnShipmentCreationPolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnShipmentCreationPolicy.PreparedCreation;
import com.erp.inventory.domain.transfer.InvTransferShipmentAllocationRecord;
import com.erp.inventory.domain.transfer.InvTransferShipmentSerialRecord;
import com.erp.inventory.domain.transfer.InvWarehouseStockMode;
import com.erp.inventory.domain.vo.InvTransferReceiptDiscrepancyReturnShipmentCreationVo;
import com.erp.inventory.mapper.InvDeptScopeMapper;
import com.erp.inventory.mapper.InvStockLogMapper;
import com.erp.inventory.mapper.InvTransferDetailMapper;
import com.erp.inventory.mapper.InvTransferOrderMapper;
import com.erp.inventory.mapper.InvTransferReceiptDiscrepancyReturnShipmentCreationMapper;
import com.erp.inventory.mapper.InvTransferRevisionMapper;
import com.erp.inventory.mapper.InvTransferShipmentCreationMapper;
import com.erp.inventory.mapper.InvTransferShipmentDetailMapper;
import com.erp.inventory.mapper.InvTransferShipmentMapper;
import com.erp.inventory.mapper.InvTransferStatusLogMapper;

/** Unwired atomic participant for one adjudication return shipment. */
@Service
public class
        InvTransferReceiptDiscrepancyReturnShipmentCreationService
        extends InvBaseService
{
    private final InvTransferReceiptDiscrepancyReturnShipmentWriteGate gate;
    private final InvTransferOrderMapper orderMapper;
    private final InvTransferRevisionMapper revisionMapper;
    private final InvTransferDetailMapper detailMapper;
    private final InvTransferShipmentCreationMapper v2Mapper;
    private final
            InvTransferReceiptDiscrepancyReturnShipmentCreationMapper
                    returnMapper;
    private final
            InvTransferReceiptDiscrepancyReturnReservationLifecycleService
                    lifecycleService;
    private final InvTransferShipmentMapper shipmentMapper;
    private final InvTransferShipmentDetailMapper shipmentDetailMapper;
    private final InvStockLogMapper stockLogMapper;
    private final InvTransferStatusLogMapper statusLogMapper;
    private final Clock clock;

    @Autowired
    public InvTransferReceiptDiscrepancyReturnShipmentCreationService(
            InvTransferReceiptDiscrepancyReturnShipmentWriteGate gate,
            InvTransferOrderMapper orderMapper,
            InvTransferRevisionMapper revisionMapper,
            InvTransferDetailMapper detailMapper,
            InvTransferShipmentCreationMapper v2Mapper,
            InvTransferReceiptDiscrepancyReturnShipmentCreationMapper
                    returnMapper,
            InvTransferReceiptDiscrepancyReturnReservationLifecycleService
                    lifecycleService,
            InvTransferShipmentMapper shipmentMapper,
            InvTransferShipmentDetailMapper shipmentDetailMapper,
            InvStockLogMapper stockLogMapper,
            InvTransferStatusLogMapper statusLogMapper,
            InvDeptScopeMapper deptScopeMapper,
            ShopScopeService shopScopeService)
    {
        this(gate, orderMapper, revisionMapper, detailMapper, v2Mapper,
                returnMapper, lifecycleService, shipmentMapper,
                shipmentDetailMapper, stockLogMapper, statusLogMapper,
                deptScopeMapper, shopScopeService, Clock.systemUTC());
    }

    InvTransferReceiptDiscrepancyReturnShipmentCreationService(
            InvTransferReceiptDiscrepancyReturnShipmentWriteGate gate,
            InvTransferOrderMapper orderMapper,
            InvTransferRevisionMapper revisionMapper,
            InvTransferDetailMapper detailMapper,
            InvTransferShipmentCreationMapper v2Mapper,
            InvTransferReceiptDiscrepancyReturnShipmentCreationMapper
                    returnMapper,
            InvTransferReceiptDiscrepancyReturnReservationLifecycleService
                    lifecycleService,
            InvTransferShipmentMapper shipmentMapper,
            InvTransferShipmentDetailMapper shipmentDetailMapper,
            InvStockLogMapper stockLogMapper,
            InvTransferStatusLogMapper statusLogMapper,
            InvDeptScopeMapper deptScopeMapper,
            ShopScopeService shopScopeService, Clock clock)
    {
        this.gate = gate;
        this.orderMapper = orderMapper;
        this.revisionMapper = revisionMapper;
        this.detailMapper = detailMapper;
        this.v2Mapper = v2Mapper;
        this.returnMapper = returnMapper;
        this.lifecycleService = lifecycleService;
        this.shipmentMapper = shipmentMapper;
        this.shipmentDetailMapper = shipmentDetailMapper;
        this.stockLogMapper = stockLogMapper;
        this.statusLogMapper = statusLogMapper;
        this.deptScopeMapper = deptScopeMapper;
        this.shopScopeService = shopScopeService;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class)
    public InvTransferReceiptDiscrepancyReturnShipmentCreationVo create(
            String requestId, Long childTransferId,
            InvTransferReceiptDiscrepancyReturnShipmentCreateRequest request,
            Long selectedDeptId)
    {
        gate.requireEnabled();
        String normalizedRequestId = InvTransferCommandExecutor
                .requireRequestId(requestId);
        requireRequest(childTransferId, request);
        resolveAndValidateShopDept(selectedDeptId);
        Long actorUserId = SecurityUtils.getUserId();
        String actorName = SecurityUtils.getUsername();

        InvTransferOrder order = orderMapper
                .selectInvTransferOrderByIdForUpdate(childTransferId);
        if (order == null)
        {
            throw new ServiceException("退回子调拨不存在");
        }
        Long sourceWarehouseId = InvTransferDirectionPolicy
                .resolveStockLocationDeptId(order.getFromWarehouseId(),
                        order.getFromDeptId());
        if (sourceWarehouseId == null || sourceWarehouseId <= 0)
        {
            throw new ServiceException("退回子调拨缺少有效来源仓库");
        }
        assertShopVisible(sourceWarehouseId, selectedDeptId,
                "无权操作退回子调拨来源仓库");
        new InvTransferDirectionPolicy(deptScopeMapper, shopScopeService)
                .validateDelivery(order, selectedDeptId);

        InvTransferRevision revision = revisionMapper
                .selectLatestByTransferIdForUpdate(childTransferId);
        List<InvTransferDetail> details = detailMapper
                .selectByTransferIdForUpdate(childTransferId);
        if (details == null || details.size() != 1
                || details.get(0) == null)
        {
            throw new ServiceException("退回子调拨唯一明细锁定事实无效");
        }
        InvTransferDetail detail = details.get(0);
        InvWarehouseStockMode mode = v2Mapper
                .selectWarehouseModeForUpdate(sourceWarehouseId);
        Instant occurredAt = now();
        var locked = lifecycleService.lockForConsumption(childTransferId,
                request.getPlanVersion(), occurredAt);
        var requestedConsumption = lifecycleService.planLockedConsumption(
                request.getQuantity(), locked);
        PreparedCreation prepared =
                InvTransferReceiptDiscrepancyReturnShipmentCreationPolicy
                        .prepare(normalizedRequestId,
                                request.getPlanVersion(), request.getBasis(),
                                request.getQuantity(), actorUserId, actorName,
                                occurredAt, order, revision, detail, mode,
                                locked.prepared(), requestedConsumption);

        InvTransferShipment shipment = insertShipment(prepared);
        InvTransferShipmentDetail shipmentDetail = insertShipmentDetail(
                prepared, shipment);
        var consumption = lifecycleService.consumeLocked(
                normalizedRequestId, shipment.getShipmentId(),
                shipmentDetail.getShipmentDetailId(), prepared.quantity(),
                actorName, occurredAt, locked);
        verifyConsumption(prepared, consumption);

        InvStockLog stockLog = insertStockLog(prepared, shipment,
                consumption);
        InvTransferShipmentAllocationRecord allocation = insertAllocation(
                prepared, shipment, shipmentDetail, stockLog, consumption);
        insertSerials(prepared, shipment, allocation, consumption.serials());
        InvStockLedgerDetailRecord ledger = insertLedger(prepared, shipment,
                allocation, stockLog, consumption);
        requireGenerated(ledger.getLedgerId(),
                "退回发货明细库存流水标识缺失");

        requireOne(returnMapper.advanceChildDetail(prepared),
                "退回子调拨累计发货数量推进冲突");
        requireOne(returnMapper.advanceChildOrder(prepared),
                "退回子调拨发货状态推进冲突");
        insertStatusLog(prepared, shipment);

        return new InvTransferReceiptDiscrepancyReturnShipmentCreationVo(
                identifier(shipment.getShipmentId()),
                identifier(shipmentDetail.getShipmentDetailId()),
                shipment.getShipmentNo(),
                identifier(prepared.fact().getChildTransferId()),
                identifier(prepared.fact().getChildTransferDetailId()),
                prepared.statusAfter(), decimal(prepared.quantity()),
                decimal(prepared.amount()),
                prepared.command().expectedPlanVersion(),
                identifier(consumption.reservationId()),
                identifier(consumption.consumptionId()),
                consumption.status(),
                version(prepared.fact().getVersion() + 1),
                identifier(prepared.revision().getRevisionId()),
                prepared.command().occurredAt().toString(),
                InvTransferReceiptDiscrepancyReturnShipmentCreationPolicy
                        .DATA_SOURCE);
    }

    private static void requireRequest(Long childTransferId,
            InvTransferReceiptDiscrepancyReturnShipmentCreateRequest request)
    {
        if (childTransferId == null || childTransferId <= 0
                || request == null || request.getPlanVersion() == null
                || !request.getPlanVersion().matches("[a-f0-9]{64}")
                || !InvTransferReceiptDiscrepancyReturnShipmentCreationPolicy
                        .BASIS.equals(request.getBasis())
                || request.getQuantity() == null
                || request.getQuantity().signum() <= 0
                || request.getQuantity().scale() > 4)
        {
            throw new ServiceException("退回发货请求缺少有效服务端规划");
        }
    }

    private InvTransferShipment insertShipment(PreparedCreation prepared)
    {
        InvTransferShipment value = new InvTransferShipment();
        value.setTransferId(prepared.fact().getChildTransferId());
        value.setShipmentNo(prepared.shipmentNo());
        value.setWarehouseId(prepared.fact().getSourceWarehouseId());
        value.setWarehouseDeptId(prepared.fact().getSourceWarehouseId());
        value.setSourceLocationDeptId(
                prepared.fact().getSourceWarehouseId());
        value.setInventoryWriteVersion(
                InvTransferShipmentWriteVersions.V2_DETAIL);
        value.setCommandRequestId(prepared.command().requestId());
        value.setPlanVersion(prepared.command().expectedPlanVersion());
        value.setSealedRevisionId(prepared.revision().getRevisionId());
        value.setReconcileBatch(prepared.mode().getLastReconcileBatch());
        value.setStatus(InvStatusConstants.PENDING_RECEIVE);
        value.setShippedBy(prepared.command().actorName());
        value.setShippedTime(Date.from(prepared.command().occurredAt()));
        value.setCreateBy(prepared.command().actorName());
        value.setRemark("V2差异退回固定隔离来源发货；旧收货入口禁止处理");
        if (shipmentMapper.insertShipment(value) != 1)
        {
            throw new ServiceException("退回V2发货批次写入失败，业务操作已回滚");
        }
        requireGenerated(value.getShipmentId(), "退回V2发货批次标识缺失");
        return value;
    }

    private InvTransferShipmentDetail insertShipmentDetail(
            PreparedCreation prepared, InvTransferShipment shipment)
    {
        InvTransferDetail source = prepared.detail();
        InvTransferShipmentDetail value = new InvTransferShipmentDetail();
        value.setShipmentId(shipment.getShipmentId());
        value.setTransferId(prepared.fact().getChildTransferId());
        value.setTransferDetailId(
                prepared.fact().getChildTransferDetailId());
        value.setItemType(prepared.fact().getItemType());
        value.setItemId(prepared.fact().getItemId());
        value.setItemCode(source.getItemCode());
        value.setItemName(source.getItemName());
        value.setProductId(prepared.fact().getProductId());
        value.setProductName(source.getProductName());
        value.setPlannedQuantity(source.getQuantity());
        value.setShippedQuantity(prepared.quantity());
        value.setReceivedQuantity(BigDecimal.ZERO.setScale(4));
        value.setCostPrice(prepared.fact().getSourceCostPrice());
        if (shipmentDetailMapper.insertShipmentDetail(value) != 1)
        {
            throw new ServiceException("退回V2发货明细写入失败，业务操作已回滚");
        }
        requireGenerated(value.getShipmentDetailId(),
                "退回V2发货明细标识缺失");
        return value;
    }

    private static void verifyConsumption(PreparedCreation prepared,
            InvTransferReceiptDiscrepancyReturnReservationLifecycleService
                    .ConsumptionResult consumption)
    {
        if (consumption == null || consumption.consumptionId() == null
                || consumption.consumptionId() <= 0
                || !Objects.equals(consumption.reservationId(),
                        prepared.fact().getReservationId())
                || !same(consumption.quantity(), prepared.quantity())
                || !same(consumption.amount(), prepared.amount())
                || !Objects.equals(consumption.stock(),
                        prepared.consumption().stock())
                || !Objects.equals(consumption.balance(),
                        prepared.consumption().balance())
                || !Objects.equals(consumption.serials(),
                        prepared.consumption().serials())
                || consumption.stock() == null
                || consumption.balance() == null
                || consumption.serials() == null)
        {
            throw new ServiceException("退回隔离预留消费结果无效，业务操作已回滚");
        }
    }

    private InvStockLog insertStockLog(PreparedCreation prepared,
            InvTransferShipment shipment,
            InvTransferReceiptDiscrepancyReturnReservationLifecycleService
                    .ConsumptionResult consumption)
    {
        InvStockLog value = new InvStockLog();
        value.setItemType(prepared.fact().getItemType());
        value.setItemId(prepared.fact().getItemId());
        value.setProductId(prepared.fact().getProductId());
        value.setShopDeptId(prepared.fact().getSourceWarehouseId());
        value.setWarehouseId(prepared.fact().getSourceWarehouseId());
        value.setMovementType(InvStatusConstants.MOVEMENT_TRANSFER_OUT);
        value.setBusinessType(InvTransferTypes.stockBusinessType(
                prepared.order().getTransferType(),
                prepared.order().getSourceBusinessType()));
        value.setBusinessId(prepared.fact().getChildTransferId());
        value.setBusinessNo(prepared.order().getOrderNo());
        value.setChangeQuantity(prepared.quantity().negate());
        value.setBeforeQuantity(consumption.stock().currentBefore());
        value.setAfterQuantity(consumption.stock().currentAfter());
        value.setCostPrice(prepared.fact().getSourceCostPrice());
        value.setCreateBy(prepared.command().actorName());
        value.setCreateTime(Date.from(prepared.command().occurredAt()));
        value.setRemark("V2差异退回固定隔离库存出库→"
                + prepared.order().getToDeptName() + "；发货批次 "
                + shipment.getShipmentNo());
        if (stockLogMapper.insertInvStockLog(value) != 1)
        {
            throw new ServiceException("退回汇总库存日志写入失败，业务操作已回滚");
        }
        requireGenerated(value.getLogId(), "退回汇总库存日志标识缺失");
        return value;
    }

    private InvTransferShipmentAllocationRecord insertAllocation(
            PreparedCreation prepared, InvTransferShipment shipment,
            InvTransferShipmentDetail shipmentDetail, InvStockLog stockLog,
            InvTransferReceiptDiscrepancyReturnReservationLifecycleService
                    .ConsumptionResult consumption)
    {
        InvTransferShipmentAllocationRecord value =
                new InvTransferShipmentAllocationRecord();
        value.setShipmentId(shipment.getShipmentId());
        value.setShipmentDetailId(shipmentDetail.getShipmentDetailId());
        value.setTransferId(prepared.fact().getChildTransferId());
        value.setTransferDetailId(
                prepared.fact().getChildTransferDetailId());
        value.setBalanceId(prepared.fact().getBalanceId());
        value.setLotId(prepared.fact().getLotId());
        value.setLocationId(prepared.fact().getLocationId());
        value.setPolicyRank(1);
        value.setAllocationPolicy(
                InvTransferReceiptDiscrepancyReturnShipmentCreationPolicy
                        .ALLOCATION_POLICY);
        value.setTrackingPolicy(prepared.fact().getTrackingPolicy());
        value.setAllocatedQuantity(prepared.quantity());
        value.setBalanceVersionBefore(
                consumption.balance().versionBefore());
        value.setBalanceVersionAfter(consumption.balance().versionAfter());
        value.setBeforeQuantity(consumption.balance().currentBefore());
        value.setAfterQuantity(consumption.balance().currentAfter());
        value.setCostPrice(prepared.fact().getSourceCostPrice());
        value.setTotalCost(prepared.amount());
        value.setSummaryStockLogId(stockLog.getLogId());
        value.setCommandRequestId(prepared.command().requestId());
        value.setCreateBy(prepared.command().actorName());
        if (v2Mapper.insertAllocation(value) != 1)
        {
            throw new ServiceException("退回发货固定来源分配写入失败，业务操作已回滚");
        }
        requireGenerated(value.getAllocationId(), "退回发货分配标识缺失");
        return value;
    }

    private void insertSerials(PreparedCreation prepared,
            InvTransferShipment shipment,
            InvTransferShipmentAllocationRecord allocation,
            List<SerialTransition> transitions)
    {
        List<InvTransferShipmentSerialRecord> values = transitions.stream()
                .map(item -> serial(prepared, shipment, allocation, item))
                .toList();
        if (!values.isEmpty()
                && v2Mapper.batchInsertShipmentSerials(values)
                        != values.size())
        {
            throw new ServiceException("退回发货序列号审计写入失败，业务操作已回滚");
        }
    }

    private static InvTransferShipmentSerialRecord serial(
            PreparedCreation prepared, InvTransferShipment shipment,
            InvTransferShipmentAllocationRecord allocation,
            SerialTransition transition)
    {
        InvTransferShipmentSerialRecord value =
                new InvTransferShipmentSerialRecord();
        value.setAllocationId(allocation.getAllocationId());
        value.setShipmentId(shipment.getShipmentId());
        value.setSerialId(transition.serialId());
        value.setSerialNoSnapshot(transition.serialNo());
        value.setItemType(prepared.fact().getItemType());
        value.setItemId(prepared.fact().getItemId());
        value.setSourceBalanceId(prepared.fact().getBalanceId());
        value.setSourceLotId(prepared.fact().getLotId());
        value.setSourceLocationId(prepared.fact().getLocationId());
        value.setStatusBefore(transition.physicalBefore());
        value.setStatusAfter(transition.physicalAfter());
        value.setCreateBy(prepared.command().actorName());
        return value;
    }

    private InvStockLedgerDetailRecord insertLedger(
            PreparedCreation prepared, InvTransferShipment shipment,
            InvTransferShipmentAllocationRecord allocation,
            InvStockLog stockLog,
            InvTransferReceiptDiscrepancyReturnReservationLifecycleService
                    .ConsumptionResult consumption)
    {
        InvStockLedgerDetailRecord value = new InvStockLedgerDetailRecord();
        String root = prepared.command().requestId();
        value.setRequestId(sha256("return-shipment-ledger-v1|"
                + root.length() + ':' + root + '|'
                + allocation.getAllocationId()));
        value.setCommandRequestId(root);
        value.setShipmentAllocationId(allocation.getAllocationId());
        value.setSummaryStockLogId(stockLog.getLogId());
        value.setWarehouseId(prepared.fact().getSourceWarehouseId());
        value.setItemType(prepared.fact().getItemType());
        value.setItemId(prepared.fact().getItemId());
        value.setProductId(prepared.fact().getProductId());
        value.setMovementType(InvStatusConstants.MOVEMENT_TRANSFER_OUT);
        value.setBusinessType("transfer_discrepancy_return_shipment_v2");
        value.setBusinessId(shipment.getShipmentId());
        value.setBusinessNo(shipment.getShipmentNo());
        value.setFromBalanceId(prepared.fact().getBalanceId());
        value.setFromLotId(prepared.fact().getLotId());
        value.setFromLocationId(prepared.fact().getLocationId());
        value.setChangeQuantity(prepared.quantity().negate());
        value.setBeforeQuantity(consumption.balance().currentBefore());
        value.setAfterQuantity(consumption.balance().currentAfter());
        value.setCostPrice(prepared.fact().getSourceCostPrice());
        value.setTotalCost(prepared.amount().negate());
        value.setOperatorUserId(prepared.command().actorUserId());
        value.setOperatorName(prepared.command().actorName());
        value.setOccurredTime(Date.from(prepared.command().occurredAt()));
        value.setCreateBy(prepared.command().actorName());
        value.setRemark("V2差异退回固定隔离来源出库；子调拨 "
                + prepared.order().getOrderNo());
        if (v2Mapper.insertStockLedger(value) != 1)
        {
            throw new ServiceException("退回发货明细库存流水写入失败，业务操作已回滚");
        }
        return value;
    }

    private void insertStatusLog(PreparedCreation prepared,
            InvTransferShipment shipment)
    {
        InvTransferStatusLog value = new InvTransferStatusLog();
        value.setTransferId(prepared.fact().getChildTransferId());
        value.setFromStatus(prepared.statusBefore());
        value.setToStatus(prepared.statusAfter());
        value.setAction("deliver_discrepancy_return_v2");
        value.setOperatorId(prepared.command().actorUserId());
        value.setOperatorName(prepared.command().actorName());
        value.setReason("V2差异退回发货批次 " + shipment.getShipmentNo());
        if (statusLogMapper.insertLog(value) != 1)
        {
            throw new ServiceException("退回子调拨状态日志写入失败，业务操作已回滚");
        }
    }

    private Instant now()
    {
        return clock.instant().truncatedTo(ChronoUnit.SECONDS);
    }

    private static void requireOne(int rows, String message)
    {
        if (rows != 1)
        {
            throw new ServiceException(message + "，业务操作已回滚");
        }
    }

    private static void requireGenerated(Long value, String message)
    {
        if (value == null || value <= 0)
        {
            throw new ServiceException(message + "，业务操作已回滚");
        }
    }

    private static String identifier(Long value)
    {
        requireGenerated(value, "退回发货结果包含无效业务标识");
        return value.toString();
    }

    private static String version(Long value)
    {
        if (value == null || value < 0)
        {
            throw new ServiceException("退回发货结果包含无效版本");
        }
        return value.toString();
    }

    private static String decimal(BigDecimal value)
    {
        if (value == null)
        {
            throw new ServiceException("退回发货结果包含无效数量或金额");
        }
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.signum() == 0 ? "0" : normalized.toPlainString();
    }

    private static boolean same(BigDecimal left, BigDecimal right)
    {
        return left != null && right != null && left.compareTo(right) == 0;
    }

    private static String sha256(String value)
    {
        try
        {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(
                    value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : bytes)
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
}
