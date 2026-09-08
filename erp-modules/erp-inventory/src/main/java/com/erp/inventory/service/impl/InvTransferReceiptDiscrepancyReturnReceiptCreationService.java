package com.erp.inventory.service.impl;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.constant.InvTransferShipmentWriteVersions;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.InvTransferShipment;
import com.erp.inventory.domain.dto.InvTransferReceiptDiscrepancyReturnReceiptCreateRequest;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnReceiptLockedBoundary;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnReceiptPolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnReceiptRequestPolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnReceiptValidatedCommand;
import com.erp.inventory.domain.transfer.InvTransferShipmentReceiptMutationPlanner;
import com.erp.inventory.domain.transfer.InvTransferShipmentReceiptPreparedMutation;
import com.erp.inventory.domain.vo.InvTransferReceiptDiscrepancyReturnReceiptCreationVo;
import com.erp.inventory.mapper.InvDeptScopeMapper;

/**
 * Unwired atomic participant for receiving an expected damaged return.
 *
 * <p>The independent gate is checked before any read. The outer caller owns
 * the transaction; this participant locks all receipt facts through the same
 * fixed-order gateway as ordinary V2 receipt, prepares the full mutation
 * before the first DML, and delegates every write to the sole receipt
 * mutation executor.</p>
 */
@Service
public class InvTransferReceiptDiscrepancyReturnReceiptCreationService
        extends InvBaseService
{
    private final InvTransferReceiptDiscrepancyReturnReceiptWriteGate gate;
    private final InvTransferShipmentReceiptLockGateway lockGateway;
    private final InvTransferShipmentReceiptMutationExecutor mutationExecutor;
    private final Clock clock;

    @Autowired
    public InvTransferReceiptDiscrepancyReturnReceiptCreationService(
            InvTransferReceiptDiscrepancyReturnReceiptWriteGate gate,
            InvTransferShipmentReceiptLockGateway lockGateway,
            InvTransferShipmentReceiptMutationExecutor mutationExecutor,
            InvDeptScopeMapper deptScopeMapper,
            ShopScopeService shopScopeService)
    {
        this(gate, lockGateway, mutationExecutor, deptScopeMapper,
                shopScopeService, Clock.systemUTC());
    }

    InvTransferReceiptDiscrepancyReturnReceiptCreationService(
            InvTransferReceiptDiscrepancyReturnReceiptWriteGate gate,
            InvTransferShipmentReceiptLockGateway lockGateway,
            InvTransferShipmentReceiptMutationExecutor mutationExecutor,
            InvDeptScopeMapper deptScopeMapper,
            ShopScopeService shopScopeService, Clock clock)
    {
        this.gate = gate;
        this.lockGateway = lockGateway;
        this.mutationExecutor = mutationExecutor;
        this.deptScopeMapper = deptScopeMapper;
        this.shopScopeService = shopScopeService;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class)
    public InvTransferReceiptDiscrepancyReturnReceiptCreationVo create(
            String requestId, Long shipmentId,
            InvTransferReceiptDiscrepancyReturnReceiptCreateRequest request,
            Long selectedDeptId)
    {
        gate.requireEnabled();
        String commandRequestId = InvTransferCommandExecutor
                .requireRequestId(requestId);
        requireRequest(shipmentId, request);

        InvTransferShipmentReceiptLockGateway.LockedHeader header =
                lockGateway.lockHeader(shipmentId);
        resolveAndValidateShopDept(selectedDeptId);
        InvTransferShipment shipment = header.shipment();
        InvTransferOrder order = header.order();
        if (!InvTransferShipmentWriteVersions.V2_DETAIL.equals(
                shipment.getInventoryWriteVersion()))
        {
            throw new ServiceException("旧发货批次不适用差异退回收货事务");
        }
        Long targetWarehouseId = InvTransferDirectionPolicy
                .resolveStockLocationDeptId(order.getToWarehouseId(),
                        order.getToDeptId());
        if (targetWarehouseId == null || targetWarehouseId <= 0)
        {
            throw new ServiceException("退回子调拨缺少有效目标仓库");
        }
        assertShopVisible(targetWarehouseId, selectedDeptId,
                "无权操作该退回发货批次的目标仓库");
        new InvTransferDirectionPolicy(deptScopeMapper, shopScopeService)
                .validateReceipt(order, selectedDeptId);

        InvTransferReceiptDiscrepancyReturnReceiptLockedBoundary boundary =
                lockGateway.lockAndComposeReturnReceipt(header,
                        targetWarehouseId, request);
        InvTransferReceiptDiscrepancyReturnReceiptValidatedCommand command =
                InvTransferReceiptDiscrepancyReturnReceiptRequestPolicy
                        .validate(request, boundary.returnPlan());
        Long operatorUserId = SecurityUtils.getUserId();
        String operatorName = SecurityUtils.getUsername();
        if (operatorUserId == null || operatorUserId <= 0
                || operatorName == null || operatorName.isBlank())
        {
            throw new ServiceException("退回收货命令缺少有效登录用户");
        }
        InvTransferShipmentReceiptPreparedMutation mutation =
                InvTransferShipmentReceiptMutationPlanner.prepareReturn(
                        commandRequestId, boundary, command, operatorUserId,
                        operatorName, operatorName, clock.instant());
        return mutationExecutor.executeReturnReceipt(mutation);
    }

    private static void requireRequest(Long shipmentId,
            InvTransferReceiptDiscrepancyReturnReceiptCreateRequest request)
    {
        if (shipmentId == null || shipmentId <= 0 || request == null
                || request.getReturnReceiptPlanVersion() == null
                || !request.getReturnReceiptPlanVersion()
                        .matches("[a-f0-9]{64}")
                || !InvTransferReceiptDiscrepancyReturnReceiptPolicy
                        .DATA_SOURCE.equals(request.getBasis())
                || request.getArrivedTime() == null
                || request.getFinalizeShipment() == null
                || request.getAllocations() == null
                || request.getAllocations().isEmpty()
                || request.getAllocations().size() > 10000)
        {
            throw new ServiceException("差异退回收货请求缺少有效服务端规划");
        }
    }
}
