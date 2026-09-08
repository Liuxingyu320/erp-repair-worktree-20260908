package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnReservationLifecycleFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnReservationSerialLifecycleFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptTargetBalance;
import com.erp.inventory.domain.transfer.InvTransferReceiptTargetStock;
import com.erp.inventory.mapper.InvTransferReceiptDiscrepancyReturnShipmentPlanningMapper;

@DisplayName("V2差异退回隔离预留只读发货规划Service")
class InvTransferReceiptDiscrepancyReturnShipmentPlanningServiceTest
{
    private static final Instant NOW = Instant.parse(
            "2026-08-03T10:00:00Z");

    @Test
    @DisplayName("按预留汇总余额和序列号顺序映射JavaScript安全计划")
    void shouldMapAuthoritativeSerialPlanInReadOrder()
    {
        Fixture fixture = fixture();
        var fact = fact("serial");
        var stock = stock();
        var balance = balance();
        List<InvTransferReceiptDiscrepancyReturnReservationSerialLifecycleFact>
                serials = List.of(
                        serial(fact, 11L, 101L, "SERIAL-0001"),
                        serial(fact, 12L, 102L, "SERIAL-0002"),
                        serial(fact, 13L, 103L, "SERIAL-0003"),
                        serial(fact, 14L, 104L, "SERIAL-0004"),
                        serial(fact, 15L, 105L, "SERIAL-0005"));
        when(fixture.mapper().selectReservation(2101L)).thenReturn(fact);
        when(fixture.mapper().selectStock(fact)).thenReturn(stock);
        when(fixture.mapper().selectBalance(fact)).thenReturn(balance);
        when(fixture.mapper().selectActiveSerials(fact))
                .thenReturn(serials);

        var result = fixture.service().plan(2101L);

        assertThat(result.transferId()).isEqualTo("2101");
        assertThat(result.planVersion()).matches("[a-f0-9]{64}");
        assertThat(result.reservationVersion()).isEqualTo("2");
        assertThat(result.remainingQuantity()).isEqualTo("5");
        assertThat(result.suggestedShipmentQuantity()).isEqualTo("5");
        assertThat(result.suggestedAmount()).isEqualTo("30");
        assertThat(result.canCreateShipment()).isTrue();
        assertThat(result.blockingReasons()).isEmpty();
        assertThat(result.generatedAt()).isEqualTo(NOW.toString());
        assertThat(result.dataSource())
                .isEqualTo("return-quarantine-reservation-v2");
        assertThat(result.allocation().balanceId()).isEqualTo("2302");
        assertThat(result.allocation().serials())
                .extracting(value -> value.serialId())
                .containsExactly("101", "102", "103", "104", "105");
        assertThat(result.allocation().serials())
                .extracting(value -> value.version())
                .containsOnly("0");
        InOrder order = inOrder(fixture.mapper());
        order.verify(fixture.mapper()).selectReservation(2101L);
        order.verify(fixture.mapper()).selectStock(fact);
        order.verify(fixture.mapper()).selectBalance(fact);
        order.verify(fixture.mapper()).selectActiveSerials(fact);
    }

    @Test
    @DisplayName("无效标识和缺失预留在读取库存前失败关闭")
    void shouldFailBeforeReadingDependentFacts()
    {
        Fixture invalid = fixture();
        assertThatThrownBy(() -> invalid.service().plan(0L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("标识无效");
        verify(invalid.mapper(), never()).selectReservation(0L);

        Fixture missing = fixture();
        when(missing.mapper().selectReservation(2101L)).thenReturn(null);
        assertThatThrownBy(() -> missing.service().plan(2101L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("规划事实不存在");
        verify(missing.mapper(), never()).selectStock(
                org.mockito.ArgumentMatchers.any());
        verify(missing.mapper(), never()).selectBalance(
                org.mockito.ArgumentMatchers.any());
        verify(missing.mapper(), never()).selectActiveSerials(
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("缺失汇总库存或精确余额时在读取后续事实前失败关闭")
    void shouldFailClosedWhenInventoryFactsAreMissing()
    {
        var fact = fact("lot");
        Fixture missingStock = fixture();
        when(missingStock.mapper().selectReservation(2101L))
                .thenReturn(fact);
        when(missingStock.mapper().selectStock(fact)).thenReturn(null);
        assertThatThrownBy(() -> missingStock.service().plan(2101L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("汇总库存规划事实不存在");
        verify(missingStock.mapper(), never()).selectBalance(fact);
        verify(missingStock.mapper(), never()).selectActiveSerials(fact);

        Fixture missingBalance = fixture();
        when(missingBalance.mapper().selectReservation(2101L))
                .thenReturn(fact);
        when(missingBalance.mapper().selectStock(fact)).thenReturn(stock());
        when(missingBalance.mapper().selectBalance(fact)).thenReturn(null);
        assertThatThrownBy(() -> missingBalance.service().plan(2101L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("批次库位规划事实不存在");
        verify(missingBalance.mapper(), never()).selectActiveSerials(fact);
    }

    @Test
    @DisplayName("规划方法只读事务且Service保持零生产调用点")
    void shouldRemainReadOnlyAndUnwired() throws Exception
    {
        Transactional transactional =
                InvTransferReceiptDiscrepancyReturnShipmentPlanningService
                        .class.getMethod("plan", Long.class)
                        .getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
        assertThat(productionReferences(
                InvTransferReceiptDiscrepancyReturnShipmentPlanningService
                        .class.getSimpleName()))
                .containsExactly(Path.of("service", "impl",
                        "InvTransferReceiptDiscrepancyReturnShipmentPlanningService.java"));
    }

    private static Fixture fixture()
    {
        var mapper = mock(
                InvTransferReceiptDiscrepancyReturnShipmentPlanningMapper
                        .class);
        var service =
                new InvTransferReceiptDiscrepancyReturnShipmentPlanningService(
                        mapper, Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(mapper, service);
    }

    private static
            InvTransferReceiptDiscrepancyReturnReservationLifecycleFact fact(
                    String trackingPolicy)
    {
        var value =
                new InvTransferReceiptDiscrepancyReturnReservationLifecycleFact();
        value.setReservationId(1001L);
        value.setReservationRequestId("return-dispatch-0001");
        value.setActionId(2001L);
        value.setChildTransferId(2101L);
        value.setChildTransferDetailId(2102L);
        value.setReservationRound(1);
        value.setReceiptAllocationId(2201L);
        value.setShipmentAllocationId(2202L);
        value.setStockId(2301L);
        value.setBalanceId(2302L);
        value.setLotId(2303L);
        value.setLocationId(2304L);
        value.setItemType("product");
        value.setItemId(2401L);
        value.setProductId(2401L);
        value.setTrackingPolicy(trackingPolicy);
        value.setReservedQuantity(new BigDecimal("5.0000"));
        value.setConsumedQuantity(BigDecimal.ZERO.setScale(4));
        value.setReleasedQuantity(BigDecimal.ZERO.setScale(4));
        value.setSourceCostPrice(new BigDecimal("6.000000"));
        value.setReservedAmount(new BigDecimal("30.000000"));
        value.setDecisionFingerprint("a".repeat(64));
        value.setStatus("ACTIVE");
        value.setVersion(2L);
        value.setChildStatus("approved");
        value.setApprovalRound(1);
        value.setSourceWarehouseId(202L);
        value.setSourceBusinessType("transfer_discrepancy_return");
        value.setSourceBusinessId(2001L);
        value.setRequestedQuantity(new BigDecimal("5.0000"));
        value.setDeliveredQuantity(BigDecimal.ZERO.setScale(4));
        value.setWorkflowId(2501L);
        value.setWorkflowType("return");
        value.setInventorySource("quarantine_detail");
        value.setWorkflowFingerprint("b".repeat(64));
        return value;
    }

    private static InvTransferReceiptTargetStock stock()
    {
        var value = new InvTransferReceiptTargetStock();
        value.setStockId(2301L);
        value.setItemType("product");
        value.setItemId(2401L);
        value.setProductId(2401L);
        value.setShopDeptId(202L);
        value.setWarehouseId(202L);
        value.setCurrentQuantity(new BigDecimal("10.0000"));
        value.setAvailableQuantity(new BigDecimal("2.0000"));
        value.setLockedQuantity(new BigDecimal("5.0000"));
        value.setQuarantineQuantity(new BigDecimal("3.0000"));
        value.setCostPrice(new BigDecimal("6.000000"));
        value.setTotalCost(new BigDecimal("60.000000"));
        value.setVersion(7L);
        return value;
    }

    private static InvTransferReceiptTargetBalance balance()
    {
        var value = new InvTransferReceiptTargetBalance();
        value.setBalanceId(2302L);
        value.setWarehouseId(202L);
        value.setItemType("product");
        value.setItemId(2401L);
        value.setProductId(2401L);
        value.setLotId(2303L);
        value.setLocationId(2304L);
        value.setCurrentQuantity(new BigDecimal("5.0000"));
        value.setAvailableQuantity(BigDecimal.ZERO.setScale(4));
        value.setLockedQuantity(new BigDecimal("5.0000"));
        value.setQuarantineQuantity(BigDecimal.ZERO.setScale(4));
        value.setCostPrice(new BigDecimal("6.000000"));
        value.setTotalCost(new BigDecimal("30.000000"));
        value.setVersion(8L);
        return value;
    }

    private static
            InvTransferReceiptDiscrepancyReturnReservationSerialLifecycleFact
            serial(
                    InvTransferReceiptDiscrepancyReturnReservationLifecycleFact
                            fact,
                    Long receiptSerialId, Long serialId, String serialNo)
    {
        var value =
                new InvTransferReceiptDiscrepancyReturnReservationSerialLifecycleFact();
        value.setBindingId(4000L + serialId);
        value.setReservationId(fact.getReservationId());
        value.setReceiptSerialId(receiptSerialId);
        value.setSerialId(serialId);
        value.setSerialNoSnapshot(serialNo);
        value.setWarehouseId(202L);
        value.setBalanceId(2302L);
        value.setLotId(2303L);
        value.setLocationId(2304L);
        value.setLifecycleStatus("ACTIVE");
        value.setVersion(0L);
        value.setCurrentSerialNo(serialNo);
        value.setCurrentSerialStatus("quarantine_reserved");
        value.setCurrentWarehouseId(202L);
        value.setCurrentBalanceId(2302L);
        value.setCurrentLotId(2303L);
        value.setCurrentLocationId(2304L);
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

    private record Fixture(
            InvTransferReceiptDiscrepancyReturnShipmentPlanningMapper mapper,
            InvTransferReceiptDiscrepancyReturnShipmentPlanningService service)
    {
    }
}
