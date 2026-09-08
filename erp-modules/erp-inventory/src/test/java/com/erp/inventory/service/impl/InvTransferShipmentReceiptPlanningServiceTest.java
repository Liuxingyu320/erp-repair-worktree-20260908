package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.inventory.constant.InvTransferShipmentWriteVersions;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.InvTransferShipment;
import com.erp.inventory.domain.transfer.InvTransferReceiptLocationCandidate;
import com.erp.inventory.domain.transfer.InvTransferReceiptPlanningAllocationFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptPlanningSerialFact;
import com.erp.inventory.domain.transfer.InvWarehouseStockMode;
import com.erp.inventory.domain.vo.InvTransferShipmentReceiptPlanningVo;
import com.erp.inventory.mapper.InvDeptScopeMapper;
import com.erp.inventory.mapper.InvTransferOrderMapper;
import com.erp.inventory.mapper.InvTransferShipmentMapper;
import com.erp.inventory.mapper.InvTransferShipmentReceiptPlanningMapper;
import com.fasterxml.jackson.databind.ObjectMapper;

@DisplayName("V2调拨收货权威规划只读服务")
class InvTransferShipmentReceiptPlanningServiceTest
{
    private final InvTransferShipmentMapper shipmentMapper =
            mock(InvTransferShipmentMapper.class);
    private final InvTransferOrderMapper orderMapper =
            mock(InvTransferOrderMapper.class);
    private final InvTransferShipmentReceiptPlanningMapper planningMapper =
            mock(InvTransferShipmentReceiptPlanningMapper.class);
    private final InvDeptScopeMapper deptScopeMapper =
            mock(InvDeptScopeMapper.class);
    private final ShopScopeService shopScopeService =
            mock(ShopScopeService.class);
    private InvTransferShipmentReceiptPlanningService service;

    @BeforeEach
    void setUp()
    {
        SecurityContextHolder.setUserId("7");
        SecurityContextHolder.setUserName("receiving-user");
        when(shopScopeService.resolveRequiredShopDept(302L))
                .thenReturn(302L);
        when(shopScopeService.hasUserShopScope(7L, 302L)).thenReturn(true);
        when(deptScopeMapper.countDeptInScope(302L, 302L)).thenReturn(1);
        when(deptScopeMapper.selectDeptTypeById(302L)).thenReturn("STORE");
        service = new InvTransferShipmentReceiptPlanningService(
                shipmentMapper, orderMapper, planningMapper,
                deptScopeMapper, shopScopeService,
                Clock.fixed(Instant.parse("2026-08-02T10:30:00Z"),
                        ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("批量事实形成浏览器安全规划且不向收货端泄露成本")
    void shouldComposeBrowserSafeReceiptPlanWithoutCost() throws Exception
    {
        InvTransferShipment shipment = shipment();
        InvTransferOrder order = order();
        when(shipmentMapper.selectById(91L)).thenReturn(shipment);
        when(orderMapper.selectInvTransferOrderById(900L)).thenReturn(order);
        when(planningMapper.selectTargetWarehouseMode(302L))
                .thenReturn(mode());
        when(planningMapper.selectAllocations(91L))
                .thenReturn(List.of(allocation()));
        when(planningMapper.selectShipmentSerials(91L))
                .thenReturn(List.of());
        when(planningMapper.selectTargetLocations(302L))
                .thenReturn(locations());

        InvTransferShipmentReceiptPlanningVo result = service.getPlanning(
                91L, 302L);

        assertThat(result.canCreateReceipt()).isTrue();
        assertThat(result.receiptPlanVersion()).matches("[a-f0-9]{64}");
        assertThat(result.suggestedAcceptedLocationId()).isEqualTo("901");
        assertThat(result.suggestedQuarantineLocationId()).isEqualTo("902");
        assertThat(result.allocations()).hasSize(1);
        assertThat(result.allocations().get(0).remainingQuantity())
                .isEqualTo("2");
        assertThat(result.allocations().get(0)
                .suggestedAcceptedQuantity()).isEqualTo("2");
        assertThat(result.generatedAt())
                .isEqualTo("2026-08-02T10:30:00Z");
        assertThat(result.dataSource()).isEqualTo("server");
        String json = new ObjectMapper().writeValueAsString(result);
        assertThat(json).doesNotContain("costPrice", "totalCost",
                "5.100000", "10.200000");
        verify(planningMapper).selectAllocations(91L);
        verify(planningMapper).selectShipmentSerials(91L);
        verify(planningMapper).selectTargetLocations(302L);
    }

    @Test
    @DisplayName("旧写版本在读取调拨与明细库存事实前拒绝")
    void shouldRejectLegacyShipmentBeforeReceiptFacts()
    {
        InvTransferShipment legacy = shipment();
        legacy.setInventoryWriteVersion("LEGACY");
        when(shipmentMapper.selectById(91L)).thenReturn(legacy);

        assertThatThrownBy(() -> service.getPlanning(91L, 302L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("旧发货批次不适用");

        verify(orderMapper, never()).selectInvTransferOrderById(900L);
        verify(planningMapper, never()).selectAllocations(91L);
    }

    @Test
    @DisplayName("无目标组织可见范围时不读取目标仓规划事实")
    void shouldRejectInvisibleDestinationBeforePlanningFacts()
    {
        when(shipmentMapper.selectById(91L)).thenReturn(shipment());
        when(orderMapper.selectInvTransferOrderById(900L))
                .thenReturn(order());
        when(deptScopeMapper.countDeptInScope(302L, 302L)).thenReturn(0);

        assertThatThrownBy(() -> service.getPlanning(91L, 302L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("无权读取");

        verify(planningMapper, never()).selectTargetWarehouseMode(302L);
    }

    @Test
    @DisplayName("非权威目标仓返回可解释阻断而不伪造建议")
    void shouldReturnBlockedPlanForNonAuthoritativeTarget()
    {
        when(shipmentMapper.selectById(91L)).thenReturn(shipment());
        when(orderMapper.selectInvTransferOrderById(900L))
                .thenReturn(order());
        InvWarehouseStockMode mode = mode();
        mode.setReadMode("shadow");
        when(planningMapper.selectTargetWarehouseMode(302L)).thenReturn(mode);
        when(planningMapper.selectAllocations(91L))
                .thenReturn(List.of(allocation()));
        when(planningMapper.selectShipmentSerials(91L))
                .thenReturn(List.of());
        when(planningMapper.selectTargetLocations(302L))
                .thenReturn(locations());

        InvTransferShipmentReceiptPlanningVo result = service.getPlanning(
                91L, 302L);

        assertThat(result.canCreateReceipt()).isFalse();
        assertThat(result.blockingReasons())
                .contains("目标仓库尚未切换 detail 明细库存读取");
    }

    private static InvTransferShipment shipment()
    {
        InvTransferShipment value = new InvTransferShipment();
        value.setShipmentId(91L);
        value.setTransferId(900L);
        value.setShipmentNo("TS20260802ABCD");
        value.setWarehouseId(301L);
        value.setSourceLocationDeptId(301L);
        value.setInventoryWriteVersion(
                InvTransferShipmentWriteVersions.V2_DETAIL);
        value.setPlanVersion("a".repeat(64));
        value.setSealedRevisionId(700L);
        value.setReconcileBatch("source-reconcile-1");
        value.setStatus("pending_receive");
        return value;
    }

    private static InvTransferOrder order()
    {
        InvTransferOrder value = new InvTransferOrder();
        value.setTransferId(900L);
        value.setOrderNo("TF202608020001");
        value.setFromDeptId(201L);
        value.setFromDeptName("苏州门店");
        value.setFromWarehouseId(301L);
        value.setToDeptId(202L);
        value.setToDeptName("南京门店");
        value.setToWarehouseId(302L);
        value.setTransferType("cross_store");
        value.setStatus("delivered");
        value.setVersion(4L);
        return value;
    }

    private static InvWarehouseStockMode mode()
    {
        InvWarehouseStockMode value = new InvWarehouseStockMode();
        value.setWarehouseId(302L);
        value.setWriteMode("dual");
        value.setReadMode("detail");
        value.setReconcileStatus("passed");
        value.setLastReconcileBatch("target-reconcile-1");
        return value;
    }

    private static InvTransferReceiptPlanningAllocationFact allocation()
    {
        InvTransferReceiptPlanningAllocationFact value =
                new InvTransferReceiptPlanningAllocationFact();
        value.setAllocationId(81L);
        value.setShipmentId(91L);
        value.setShipmentDetailId(71L);
        value.setTransferId(900L);
        value.setTransferDetailId(11L);
        value.setItemType("product");
        value.setItemId(1001L);
        value.setProductId(1001L);
        value.setItemCode("SKU-1001");
        value.setItemName("演示商品");
        value.setUnit("件");
        value.setTrackingPolicy("lot");
        value.setAllocatedQuantity(new BigDecimal("2.0000"));
        value.setBalanceVersionAfter(5L);
        value.setCostPrice(new BigDecimal("5.100000"));
        value.setTotalCost(new BigDecimal("10.200000"));
        value.setSourceWarehouseId(301L);
        value.setSourceBalanceId(401L);
        value.setSourceLotId(501L);
        value.setSourceLocationId(601L);
        value.setSourceLotNo("LOT-202608-01");
        value.setSupplierBatchNo("SUP-01");
        value.setProductionDate(java.sql.Date.valueOf("2026-07-01"));
        value.setExpiryDate(java.sql.Date.valueOf("2027-07-01"));
        value.setQcStatus("passed");
        value.setLotStatus("active");
        value.setSourceLocationCode("A-01-01");
        value.setShipmentDetailShippedQuantity(
                new BigDecimal("2.0000"));
        value.setShipmentDetailReceivedQuantity(BigDecimal.ZERO);
        value.setAcceptedReceivedQuantity(BigDecimal.ZERO);
        value.setDamagedReceivedQuantity(BigDecimal.ZERO);
        value.setShortageReportedQuantity(BigDecimal.ZERO);
        value.setReceiptVersion(0L);
        return value;
    }

    private static List<InvTransferReceiptLocationCandidate> locations()
    {
        return List.of(location(901L, "storage"),
                location(902L, "quarantine"));
    }

    private static InvTransferReceiptLocationCandidate location(Long id,
            String type)
    {
        InvTransferReceiptLocationCandidate value =
                new InvTransferReceiptLocationCandidate();
        value.setLocationId(id);
        value.setWarehouseId(302L);
        value.setLocationCode(type.toUpperCase() + '-' + id);
        value.setLocationName(type + " location");
        value.setLocationType(type);
        value.setSortOrder(1);
        value.setStatus("0");
        value.setVirtualFlag("0");
        return value;
    }
}
