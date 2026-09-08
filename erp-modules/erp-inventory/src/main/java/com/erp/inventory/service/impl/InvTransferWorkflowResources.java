package com.erp.inventory.service.impl;

import com.erp.common.security.shop.ShopScopeService;
import com.erp.inventory.mapper.InvDeptScopeMapper;
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

/** Immutable dependency bundle for transfer receipt and discrepancy workflows. */
final class InvTransferWorkflowResources
{
    final InvTransferOrderMapper transferOrderMapper;
    final InvTransferDetailMapper transferDetailMapper;
    final InvTransferDiscrepancyMapper transferDiscrepancyMapper;
    final InvTransferDiscrepancyDispositionMapper dispositionMapper;
    final InvTransferShipmentMapper transferShipmentMapper;
    final InvTransferShipmentDetailMapper transferShipmentDetailMapper;
    final InvStockMapper stockMapper;
    final InvStockLogMapper stockLogMapper;
    final InvNumberSequenceMapper numberSequenceMapper;
    final InvTransferStatusLogMapper statusLogMapper;
    final BusinessFeatureGate businessFeatureGate;
    final InventoryBusinessMetrics businessMetrics;
    final InvDeptScopeMapper deptScopeMapper;
    final ShopScopeService shopScopeService;
    final InvTransferReservationService transferReservationService;

    InvTransferWorkflowResources(
            InvTransferOrderMapper transferOrderMapper,
            InvTransferDetailMapper transferDetailMapper,
            InvTransferDiscrepancyMapper transferDiscrepancyMapper,
            InvTransferDiscrepancyDispositionMapper dispositionMapper,
            InvTransferShipmentMapper transferShipmentMapper,
            InvTransferShipmentDetailMapper transferShipmentDetailMapper,
            InvStockMapper stockMapper,
            InvStockLogMapper stockLogMapper,
            InvNumberSequenceMapper numberSequenceMapper,
            InvTransferStatusLogMapper statusLogMapper,
            BusinessFeatureGate businessFeatureGate,
            InventoryBusinessMetrics businessMetrics,
            InvDeptScopeMapper deptScopeMapper,
            ShopScopeService shopScopeService,
            InvTransferReservationService transferReservationService)
    {
        this.transferOrderMapper = transferOrderMapper;
        this.transferDetailMapper = transferDetailMapper;
        this.transferDiscrepancyMapper = transferDiscrepancyMapper;
        this.dispositionMapper = dispositionMapper;
        this.transferShipmentMapper = transferShipmentMapper;
        this.transferShipmentDetailMapper = transferShipmentDetailMapper;
        this.stockMapper = stockMapper;
        this.stockLogMapper = stockLogMapper;
        this.numberSequenceMapper = numberSequenceMapper;
        this.statusLogMapper = statusLogMapper;
        this.businessFeatureGate = businessFeatureGate;
        this.businessMetrics = businessMetrics;
        this.deptScopeMapper = deptScopeMapper;
        this.shopScopeService = shopScopeService;
        this.transferReservationService = transferReservationService;
    }
}
