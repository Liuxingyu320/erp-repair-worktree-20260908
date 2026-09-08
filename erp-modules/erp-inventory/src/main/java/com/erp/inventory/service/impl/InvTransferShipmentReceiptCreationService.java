package com.erp.inventory.service.impl;

import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.constant.InvTransferShipmentWriteVersions;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.InvTransferShipment;
import com.erp.inventory.domain.dto.InvTransferShipmentReceiptCreateRequest;
import com.erp.inventory.domain.transfer.InvTransferShipmentReceiptLockedBoundary;
import com.erp.inventory.domain.transfer.InvTransferShipmentReceiptMutationPlanner;
import com.erp.inventory.domain.transfer.InvTransferShipmentReceiptPreparedMutation;
import com.erp.inventory.domain.transfer.InvTransferShipmentReceiptRequestPolicy;
import com.erp.inventory.domain.transfer.InvTransferShipmentReceiptValidatedCommand;
import com.erp.inventory.domain.vo.InvTransferShipmentReceiptCreationVo;
import com.erp.inventory.mapper.InvDeptScopeMapper;

/**
 * Transaction owner reserved for Stage 12C.
 *
 * <p>Locks and validates through a narrow gateway, prepares the complete
 * mutation before the first DML, and delegates writes to the only mutation
 * executor. This service remains the sole transaction owner.</p>
 */
@Service
public class InvTransferShipmentReceiptCreationService extends InvBaseService
{
    private final InvTransferShipmentReceiptWriteGate writeGate;
    private final InvTransferShipmentReceiptLockGateway lockGateway;
    private final InvTransferShipmentReceiptMutationExecutor mutationExecutor;

    public InvTransferShipmentReceiptCreationService(
            InvTransferShipmentReceiptWriteGate writeGate,
            InvTransferShipmentReceiptLockGateway lockGateway,
            InvTransferShipmentReceiptMutationExecutor mutationExecutor,
            InvDeptScopeMapper deptScopeMapper,
            ShopScopeService shopScopeService)
    {
        this.writeGate = writeGate;
        this.lockGateway = lockGateway;
        this.mutationExecutor = mutationExecutor;
        this.deptScopeMapper = deptScopeMapper;
        this.shopScopeService = shopScopeService;
    }

    @Transactional(propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class)
    public InvTransferShipmentReceiptCreationVo create(String requestId,
            Long shipmentId,
            InvTransferShipmentReceiptCreateRequest request,
            Long selectedDeptId)
    {
        writeGate.requireEnabled();
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
            throw new ServiceException("旧发货批次不适用V2收货事务");
        }
        Long targetWarehouseId = InvTransferDirectionPolicy
                .resolveStockLocationDeptId(order.getToWarehouseId(),
                        order.getToDeptId());
        if (targetWarehouseId == null || targetWarehouseId <= 0)
        {
            throw new ServiceException("调拨单缺少有效目标仓库");
        }
        assertShopVisible(targetWarehouseId, selectedDeptId,
                "无权操作该发货批次的收货仓库");
        new InvTransferDirectionPolicy(deptScopeMapper, shopScopeService)
                .validateReceipt(order, selectedDeptId);

        InvTransferShipmentReceiptLockedBoundary boundary =
                lockGateway.lockAndCompose(header, targetWarehouseId,
                        request);
        InvTransferShipmentReceiptValidatedCommand command =
                InvTransferShipmentReceiptRequestPolicy.validate(request,
                        boundary.composition());
        Long operatorUserId = SecurityUtils.getUserId();
        String operatorName = SecurityUtils.getUsername();
        if (operatorUserId == null || operatorUserId <= 0
                || operatorName == null || operatorName.isBlank())
        {
            throw new ServiceException("收货命令缺少有效登录用户");
        }
        InvTransferShipmentReceiptPreparedMutation mutation =
                InvTransferShipmentReceiptMutationPlanner.prepare(
                        commandRequestId, boundary, command, operatorUserId,
                        operatorName, operatorName, Instant.now());
        return mutationExecutor.execute(mutation);
    }

    private static void requireRequest(Long shipmentId,
            InvTransferShipmentReceiptCreateRequest request)
    {
        if (shipmentId == null || shipmentId <= 0 || request == null
                || request.getReceiptPlanVersion() == null
                || !request.getReceiptPlanVersion().matches("[a-f0-9]{64}")
                || !"server-recommendation".equals(request.getBasis())
                || request.getArrivedTime() == null
                || request.getFinalizeShipment() == null
                || request.getAllocations() == null
                || request.getAllocations().isEmpty()
                || request.getAllocations().size() > 10000)
        {
            throw new ServiceException("V2收货请求缺少有效服务端规划");
        }
    }
}
