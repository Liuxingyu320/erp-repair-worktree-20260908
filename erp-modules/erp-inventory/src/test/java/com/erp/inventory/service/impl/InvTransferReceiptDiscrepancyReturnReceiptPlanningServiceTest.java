package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.inventory.constant.InvStatusConstants;
import com.erp.inventory.constant.InvTransferShipmentWriteVersions;
import com.erp.inventory.constant.InvTransferTypes;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.InvTransferShipment;
import com.erp.inventory.domain.transfer.InvTransferReceiptLocationCandidate;
import com.erp.inventory.domain.transfer.InvTransferReceiptPlanningAllocationFact;
import com.erp.inventory.domain.transfer.InvWarehouseStockMode;
import com.erp.inventory.domain.vo.InvTransferReceiptDiscrepancyReturnReceiptPlanningVo;
import com.erp.inventory.mapper.InvDeptScopeMapper;
import com.erp.inventory.mapper.InvTransferOrderMapper;
import com.erp.inventory.mapper.InvTransferShipmentMapper;
import com.erp.inventory.mapper.InvTransferShipmentReceiptPlanningMapper;
import com.fasterxml.jackson.databind.ObjectMapper;

@DisplayName("V2差异退回专属隔离收货只读规划Service")
class InvTransferReceiptDiscrepancyReturnReceiptPlanningServiceTest
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
    private InvTransferReceiptDiscrepancyReturnReceiptPlanningService service;

    @BeforeEach
    void setUp()
    {
        SecurityContextHolder.setUserId("7");
        SecurityContextHolder.setUserName("return-receiving-user");
        when(shopScopeService.resolveRequiredShopDept(101L))
                .thenReturn(101L);
        when(shopScopeService.hasUserShopScope(7L, 101L)).thenReturn(true);
        when(deptScopeMapper.countDeptInScope(101L, 101L)).thenReturn(1);
        when(deptScopeMapper.selectDeptTypeById(101L))
                .thenReturn("WAREHOUSE");
        service =
                new InvTransferReceiptDiscrepancyReturnReceiptPlanningService(
                        shipmentMapper, orderMapper, planningMapper,
                        deptScopeMapper, shopScopeService,
                        Clock.fixed(Instant.parse("2026-08-03T12:00:00Z"),
                                ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("固定隔离来源形成浏览器安全退回实收建议且不泄露成本")
    void shouldComposeBrowserSafeReturnReceiptPlan() throws Exception
    {
        stubReadable();

        InvTransferReceiptDiscrepancyReturnReceiptPlanningVo result =
                service.getPlanning(3001L, 101L);

        assertThat(result.canCreateReturnReceipt()).isTrue();
        assertThat(result.returnReceiptPlanVersion())
                .matches("[a-f0-9]{64}");
        assertThat(result.suggestedQuarantineLocationId())
                .isEqualTo("902");
        assertThat(result.quarantineLocations()).hasSize(1);
        assertThat(result.allocations()).hasSize(1);
        assertThat(result.allocations().get(0).shippedQuantity())
                .isEqualTo("5");
        assertThat(result.allocations().get(0).returnedQuantity())
                .isEqualTo("0");
        assertThat(result.allocations().get(0).remainingQuantity())
                .isEqualTo("5");
        assertThat(result.allocations().get(0)
                .suggestedReturnedQuantity()).isEqualTo("5");
        assertThat(result.generatedAt())
                .isEqualTo("2026-08-03T12:00:00Z");
        assertThat(result.dataSource())
                .isEqualTo("return-receipt-quarantine-plan-v1");
        String json = new ObjectMapper().writeValueAsString(result);
        assertThat(json).doesNotContain("costPrice", "totalCost",
                "30.000000", "acceptedQuantity");
        verify(planningMapper).selectAllocations(3001L);
        verify(planningMapper).selectShipmentSerials(3001L);
        verify(planningMapper).selectTargetLocations(101L);
    }

    @Test
    @DisplayName("无目标组织可见范围时不读取退回收货规划事实")
    void shouldRejectInvisibleDestinationBeforePlanningFacts()
    {
        when(shipmentMapper.selectById(3001L)).thenReturn(shipment());
        when(orderMapper.selectInvTransferOrderById(2101L))
                .thenReturn(order());
        when(deptScopeMapper.countDeptInScope(101L, 101L)).thenReturn(0);

        assertThatThrownBy(() -> service.getPlanning(3001L, 101L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("无权读取");

        verify(planningMapper, never()).selectTargetWarehouseMode(101L);
        verify(planningMapper, never()).selectAllocations(3001L);
    }

    @Test
    @DisplayName("规划方法只读且Service保持生产零调用点")
    void shouldRemainReadOnlyAndUnwired() throws Exception
    {
        Transactional transaction =
                InvTransferReceiptDiscrepancyReturnReceiptPlanningService
                        .class.getMethod("getPlanning", Long.class,
                                Long.class)
                        .getAnnotation(Transactional.class);
        assertThat(transaction).isNotNull();
        assertThat(transaction.readOnly()).isTrue();
        assertThat(productionReferences(
                InvTransferReceiptDiscrepancyReturnReceiptPlanningService
                        .class.getSimpleName()))
                .containsExactly(Path.of("service", "impl",
                        "InvTransferReceiptDiscrepancyReturnReceiptPlanningService.java"));
    }

    private void stubReadable()
    {
        when(shipmentMapper.selectById(3001L)).thenReturn(shipment());
        when(orderMapper.selectInvTransferOrderById(2101L))
                .thenReturn(order());
        when(planningMapper.selectTargetWarehouseMode(101L))
                .thenReturn(mode());
        when(planningMapper.selectAllocations(3001L))
                .thenReturn(List.of(allocation()));
        when(planningMapper.selectShipmentSerials(3001L))
                .thenReturn(List.of());
        when(planningMapper.selectTargetLocations(101L))
                .thenReturn(List.of(location()));
    }

    private static InvTransferShipment shipment()
    {
        var value = new InvTransferShipment();
        value.setShipmentId(3001L);
        value.setTransferId(2101L);
        value.setShipmentNo("TSR220260803ABC");
        value.setWarehouseId(202L);
        value.setSourceLocationDeptId(202L);
        value.setInventoryWriteVersion(
                InvTransferShipmentWriteVersions.V2_DETAIL);
        value.setCommandRequestId("return-shipment-0001");
        value.setPlanVersion("a".repeat(64));
        value.setSealedRevisionId(2601L);
        value.setReconcileBatch("return-source-reconcile-01");
        value.setStatus(InvStatusConstants.PENDING_RECEIVE);
        return value;
    }

    private static InvTransferOrder order()
    {
        var value = new InvTransferOrder();
        value.setTransferId(2101L);
        value.setOrderNo("TR-RETURN-2101");
        value.setVersion(5L);
        value.setStatus(InvStatusConstants.DELIVERED);
        value.setTransferType(InvTransferTypes.STORE_RETURN);
        value.setReturnReasonCode("DAMAGED_RECEIPT_RETURN");
        value.setFromDeptId(202L);
        value.setFromDeptName("差异收货门店");
        value.setToDeptId(101L);
        value.setToDeptName("原发货仓");
        value.setSourceBusinessType("transfer_discrepancy_return");
        value.setSourceBusinessId(2001L);
        value.setApprovalRound(1);
        return value;
    }

    private static InvWarehouseStockMode mode()
    {
        var value = new InvWarehouseStockMode();
        value.setWarehouseId(101L);
        value.setWriteMode("dual");
        value.setReadMode("detail");
        value.setReconcileStatus("passed");
        value.setLastReconcileBatch("return-target-reconcile-01");
        return value;
    }

    private static InvTransferReceiptPlanningAllocationFact allocation()
    {
        var value = new InvTransferReceiptPlanningAllocationFact();
        value.setAllocationId(5003L);
        value.setShipmentId(3001L);
        value.setShipmentDetailId(3002L);
        value.setTransferId(2101L);
        value.setTransferDetailId(2102L);
        value.setItemType("product");
        value.setItemId(2401L);
        value.setProductId(2401L);
        value.setItemCode("P-2401");
        value.setItemName("受损测试物料");
        value.setUnit("件");
        value.setAllocationPolicy("FIXED_RETURN");
        value.setTrackingPolicy("lot");
        value.setAllocatedQuantity(new BigDecimal("5.0000"));
        value.setBalanceVersionAfter(9L);
        value.setCostPrice(new BigDecimal("6.000000"));
        value.setTotalCost(new BigDecimal("30.000000"));
        value.setSourceWarehouseId(202L);
        value.setSourceBalanceId(2302L);
        value.setSourceLotId(2303L);
        value.setSourceLocationId(2304L);
        value.setSourceLotNo("DMG-RETURN-01");
        value.setQcStatus("quarantine");
        value.setLotStatus("active");
        value.setSourceReceiptDisposition("damaged");
        value.setSourceLocationCode("QUARANTINE-202");
        value.setSourceLocationType("quarantine");
        value.setShipmentDetailShippedQuantity(new BigDecimal("5.0000"));
        value.setShipmentDetailReceivedQuantity(BigDecimal.ZERO.setScale(4));
        value.setTransferDetailRequestedQuantity(new BigDecimal("5.0000"));
        value.setTransferDetailDeliveredQuantity(new BigDecimal("5.0000"));
        value.setTransferDetailReceivedQuantity(BigDecimal.ZERO.setScale(4));
        value.setAcceptedReceivedQuantity(BigDecimal.ZERO.setScale(4));
        value.setDamagedReceivedQuantity(BigDecimal.ZERO.setScale(4));
        value.setShortageReportedQuantity(BigDecimal.ZERO.setScale(4));
        value.setReceiptVersion(0L);
        return value;
    }

    private static InvTransferReceiptLocationCandidate location()
    {
        var value = new InvTransferReceiptLocationCandidate();
        value.setLocationId(902L);
        value.setWarehouseId(101L);
        value.setLocationCode("QUARANTINE-902");
        value.setLocationName("退回残损隔离区");
        value.setLocationType("quarantine");
        value.setSortOrder(1);
        value.setStatus("0");
        value.setVirtualFlag("0");
        return value;
    }

    private static List<Path> productionReferences(String value)
            throws Exception
    {
        Path root = Path.of(System.getProperty("user.dir"), "src", "main",
                "java", "com", "erp", "inventory").normalize();
        try (var paths = Files.walk(root))
        {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        try
                        {
                            return Files.readString(path,
                                    StandardCharsets.UTF_8).contains(value);
                        }
                        catch (java.io.IOException error)
                        {
                            throw new IllegalStateException(error);
                        }
                    })
                    .map(root::relativize).toList();
        }
    }
}
