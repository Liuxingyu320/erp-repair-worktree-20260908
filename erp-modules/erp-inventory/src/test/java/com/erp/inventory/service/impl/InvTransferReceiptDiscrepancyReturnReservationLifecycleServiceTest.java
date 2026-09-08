package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnReservationLifecycleFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnReservationSerialLifecycleFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnShipmentPolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptGeneratedId;
import com.erp.inventory.domain.transfer.InvTransferReceiptTargetBalance;
import com.erp.inventory.domain.transfer.InvTransferReceiptTargetStock;
import com.erp.inventory.mapper.InvTransferReceiptDiscrepancyReturnReservationLifecycleMapper;

@DisplayName("V2差异退回隔离预留消费释放事务所有者")
class InvTransferReceiptDiscrepancyReturnReservationLifecycleServiceTest
{
    private static final Instant NOW = Instant.parse(
            "2026-08-03T10:00:00Z");

    @Test
    @DisplayName("按预留汇总余额事件和回读顺序原子消费批次库存")
    void shouldConsumeLotReservationInFixedOrder()
    {
        Fixture fixture = fixture();
        var initial = fact("lot", "0.0000", "0.0000", "ACTIVE", 2L);
        var updated = fact("lot", "5.0000", "0.0000", "CONSUMED", 3L);
        when(fixture.mapper().selectReservationForUpdate(2101L))
                .thenReturn(initial, updated);
        when(fixture.mapper().selectStockForUpdate(initial))
                .thenReturn(stock("0.0000"));
        when(fixture.mapper().selectBalanceForUpdate(initial))
                .thenReturn(balance("0.0000"));
        when(fixture.mapper().selectSerialsForUpdate(initial))
                .thenReturn(List.of());
        stubConsumptionWrites(fixture, 5001L);

        var result = fixture.service().consume("return-consume-0001",
                2101L, 3001L, 3002L, new BigDecimal("5.0000"),
                "operator");

        assertThat(result.consumptionId()).isEqualTo(5001L);
        assertThat(result.status()).isEqualTo("CONSUMED");
        assertThat(result.remainingQuantity()).isEqualByComparingTo("0.0000");
        InOrder order = inOrder(fixture.mapper());
        order.verify(fixture.mapper()).selectReservationForUpdate(2101L);
        order.verify(fixture.mapper()).selectStockForUpdate(initial);
        order.verify(fixture.mapper()).selectBalanceForUpdate(initial);
        order.verify(fixture.mapper()).selectSerialsForUpdate(initial);
        order.verify(fixture.mapper()).consumeStock(any());
        order.verify(fixture.mapper()).consumeBalance(any());
        order.verify(fixture.mapper()).insertConsumption(any(), any());
        order.verify(fixture.mapper()).updateConsumedReservation(any());
        order.verify(fixture.mapper()).selectReservationForUpdate(2101L);
        verify(fixture.mapper(), never()).consumePhysicalSerial(any(), any());
        verify(fixture.mapper(), never())
                .consumeSerialBinding(any(), any(), anyLong());
    }

    @Test
    @DisplayName("消费后的剩余序列号逐件释放并写关闭事件")
    void shouldReleaseRemainingSerialsAfterPartialConsumption()
    {
        Fixture fixture = fixture();
        var initial = fact("serial", "2.0000", "0.0000", "PARTIAL", 2L);
        var updated = fact("serial", "2.0000", "3.0000", "CLOSED", 3L);
        List<InvTransferReceiptDiscrepancyReturnReservationSerialLifecycleFact>
                serials = List.of(
                        serial(initial, 13L, 103L, "S-3"),
                        serial(initial, 14L, 104L, "S-4"),
                        serial(initial, 15L, 105L, "S-5"));
        when(fixture.mapper().selectReservationForUpdate(2101L))
                .thenReturn(initial, updated);
        when(fixture.mapper().selectStockForUpdate(initial))
                .thenReturn(stock("2.0000"));
        when(fixture.mapper().selectBalanceForUpdate(initial))
                .thenReturn(balance("2.0000"));
        when(fixture.mapper().selectSerialsForUpdate(initial))
                .thenReturn(serials);
        stubReleaseWrites(fixture, 6001L);

        var result = fixture.service().releaseAllRemaining(
                "return-release-0001", 2101L, "APPROVAL_REJECT",
                "approval:event-1", "operator");

        assertThat(result.releaseId()).isEqualTo(6001L);
        assertThat(result.status()).isEqualTo("CLOSED");
        assertThat(result.quantity()).isEqualByComparingTo("3.0000");
        InOrder order = inOrder(fixture.mapper());
        order.verify(fixture.mapper()).selectReservationForUpdate(2101L);
        order.verify(fixture.mapper()).selectStockForUpdate(initial);
        order.verify(fixture.mapper()).selectBalanceForUpdate(initial);
        order.verify(fixture.mapper()).selectSerialsForUpdate(initial);
        order.verify(fixture.mapper()).releaseStock(any());
        order.verify(fixture.mapper()).releaseBalance(any());
        order.verify(fixture.mapper(), times(3))
                .releasePhysicalSerial(any(), any());
        order.verify(fixture.mapper()).insertRelease(any(), any());
        order.verify(fixture.mapper(), times(3))
                .releaseSerialBinding(any(), any(), anyLong());
        order.verify(fixture.mapper()).updateReleasedReservation(any());
        order.verify(fixture.mapper()).selectReservationForUpdate(2101L);
    }

    @Test
    @DisplayName("事件或累计更新冲突均失败并要求外层事务回滚")
    void shouldFailClosedOnEventOrAggregateConflict()
    {
        Fixture eventConflict = fixture();
        stubLockedLot(eventConflict);
        when(eventConflict.mapper().consumeStock(any())).thenReturn(1);
        when(eventConflict.mapper().consumeBalance(any())).thenReturn(1);
        when(eventConflict.mapper().insertConsumption(any(), any()))
                .thenReturn(0);

        assertThatThrownBy(() -> eventConflict.service().consume(
                "return-consume-0001", 2101L, 3001L, 3002L,
                new BigDecimal("5.0000"), "operator"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("消费事件写入冲突")
                .hasMessageContaining("已回滚");
        verify(eventConflict.mapper(), never())
                .updateConsumedReservation(any());

        Fixture aggregateConflict = fixture();
        stubLockedLot(aggregateConflict);
        stubConsumptionWrites(aggregateConflict, 5001L);
        when(aggregateConflict.mapper().updateConsumedReservation(any()))
                .thenReturn(0);
        assertThatThrownBy(() -> aggregateConflict.service().consume(
                "return-consume-0001", 2101L, 3001L, 3002L,
                new BigDecimal("5.0000"), "operator"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("累计消费冲突")
                .hasMessageContaining("已回滚");
    }

    @Test
    @DisplayName("外层创建先锁定校验计划再绑定真实发货标识消费")
    void shouldValidatePlanBeforeDmlAndConsumeLockedFacts()
    {
        Fixture fixture = fixture();
        var initial = fact("lot", "0.0000", "0.0000", "ACTIVE", 2L);
        var updated = fact("lot", "2.0000", "0.0000", "PARTIAL", 3L);
        var stock = stock("0.0000");
        var balance = balance("0.0000");
        String planVersion = InvTransferReceiptDiscrepancyReturnShipmentPolicy
                .prepare(NOW, initial, stock, balance, List.of())
                .planVersion();
        when(fixture.mapper().selectReservationForUpdate(2101L))
                .thenReturn(initial, updated);
        when(fixture.mapper().selectStockForUpdate(initial)).thenReturn(stock);
        when(fixture.mapper().selectBalanceForUpdate(initial))
                .thenReturn(balance);
        when(fixture.mapper().selectSerialsForUpdate(initial))
                .thenReturn(List.of());
        stubConsumptionWrites(fixture, 5001L);

        var locked = fixture.service().lockForConsumption(2101L,
                planVersion, NOW);
        var preview = fixture.service().planLockedConsumption(
                new BigDecimal("2.0000"), locked);
        assertThat(preview.quantity()).isEqualByComparingTo("2.0000");
        assertThat(preview.remainingAfter()).isEqualByComparingTo("3.0000");
        verify(fixture.mapper(), never()).consumeStock(any());
        var result = fixture.service().consumeLocked(
                "return-shipment-0001", 3001L, 3002L,
                new BigDecimal("2.0000"), "operator", NOW, locked);

        assertThat(result.quantity()).isEqualByComparingTo("2.0000");
        assertThat(result.status()).isEqualTo("PARTIAL");
        assertThat(result.remainingQuantity()).isEqualByComparingTo("3.0000");

        Fixture stale = fixture();
        when(stale.mapper().selectReservationForUpdate(2101L))
                .thenReturn(initial);
        when(stale.mapper().selectStockForUpdate(initial)).thenReturn(stock);
        when(stale.mapper().selectBalanceForUpdate(initial))
                .thenReturn(balance);
        when(stale.mapper().selectSerialsForUpdate(initial))
                .thenReturn(List.of());
        assertThatThrownBy(() -> stale.service().lockForConsumption(2101L,
                "c".repeat(64), NOW))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("规划已变化");
        verify(stale.mapper(), never()).consumeStock(any());
        verify(stale.mapper(), never()).insertConsumption(any(), any());
    }

    @Test
    @DisplayName("写方法强制外层事务且只接入未接线退回发货Owner")
    void shouldRequireOuterTransactionAndRemainUnwired() throws Exception
    {
        for (Method method : List.of(
                InvTransferReceiptDiscrepancyReturnReservationLifecycleService
                        .class.getMethod("consume", String.class, Long.class,
                                Long.class, Long.class, BigDecimal.class,
                                String.class),
                InvTransferReceiptDiscrepancyReturnReservationLifecycleService
                        .class.getMethod("releaseAllRemaining", String.class,
                                Long.class, String.class, String.class,
                                String.class)))
        {
            Transactional transaction = method.getAnnotation(
                    Transactional.class);
            assertThat(transaction).isNotNull();
            assertThat(transaction.propagation())
                    .isEqualTo(Propagation.MANDATORY);
            assertThat(List.of(transaction.rollbackFor()))
                    .contains(Exception.class);
        }
        assertThat(productionReferences(
                InvTransferReceiptDiscrepancyReturnReservationLifecycleService
                        .class.getSimpleName()))
                .containsExactlyInAnyOrder(
                        Path.of("service", "impl",
                                "InvTransferReceiptDiscrepancyReturnReservationLifecycleService.java"),
                        Path.of("service", "impl",
                                "InvTransferReceiptDiscrepancyReturnShipmentCreationService.java"));
    }

    private static void stubLockedLot(Fixture fixture)
    {
        var initial = fact("lot", "0.0000", "0.0000", "ACTIVE", 2L);
        when(fixture.mapper().selectReservationForUpdate(2101L))
                .thenReturn(initial);
        when(fixture.mapper().selectStockForUpdate(initial))
                .thenReturn(stock("0.0000"));
        when(fixture.mapper().selectBalanceForUpdate(initial))
                .thenReturn(balance("0.0000"));
        when(fixture.mapper().selectSerialsForUpdate(initial))
                .thenReturn(List.of());
    }

    private static void stubConsumptionWrites(Fixture fixture, Long eventId)
    {
        when(fixture.mapper().consumeStock(any())).thenReturn(1);
        when(fixture.mapper().consumeBalance(any())).thenReturn(1);
        when(fixture.mapper().insertConsumption(any(), any()))
                .thenAnswer(invocation -> {
                    InvTransferReceiptGeneratedId value =
                            invocation.getArgument(1);
                    value.setValue(eventId);
                    return 1;
                });
        when(fixture.mapper().consumePhysicalSerial(any(), any()))
                .thenReturn(1);
        when(fixture.mapper().consumeSerialBinding(any(), any(), anyLong()))
                .thenReturn(1);
        when(fixture.mapper().updateConsumedReservation(any())).thenReturn(1);
    }

    private static void stubReleaseWrites(Fixture fixture, Long eventId)
    {
        when(fixture.mapper().releaseStock(any())).thenReturn(1);
        when(fixture.mapper().releaseBalance(any())).thenReturn(1);
        when(fixture.mapper().releasePhysicalSerial(any(), any()))
                .thenReturn(1);
        when(fixture.mapper().insertRelease(any(), any()))
                .thenAnswer(invocation -> {
                    InvTransferReceiptGeneratedId value =
                            invocation.getArgument(1);
                    value.setValue(eventId);
                    return 1;
                });
        when(fixture.mapper().releaseSerialBinding(any(), any(), anyLong()))
                .thenReturn(1);
        when(fixture.mapper().updateReleasedReservation(any())).thenReturn(1);
    }

    private static
            InvTransferReceiptDiscrepancyReturnReservationLifecycleFact fact(
                    String trackingPolicy, String consumed, String released,
                    String status, Long version)
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
        value.setConsumedQuantity(new BigDecimal(consumed));
        value.setReleasedQuantity(new BigDecimal(released));
        value.setSourceCostPrice(new BigDecimal("6.000000"));
        value.setReservedAmount(new BigDecimal("30.000000"));
        value.setDecisionFingerprint("a".repeat(64));
        value.setStatus(status);
        value.setVersion(version);
        value.setChildStatus("approved");
        value.setApprovalRound(1);
        value.setSourceWarehouseId(202L);
        value.setSourceBusinessType("transfer_discrepancy_return");
        value.setSourceBusinessId(2001L);
        value.setRequestedQuantity(new BigDecimal("5.0000"));
        value.setDeliveredQuantity(new BigDecimal(consumed));
        value.setWorkflowId(2501L);
        value.setWorkflowType("return");
        value.setInventorySource("quarantine_detail");
        value.setWorkflowFingerprint("b".repeat(64));
        return value;
    }

    private static InvTransferReceiptTargetStock stock(String consumed)
    {
        BigDecimal used = new BigDecimal(consumed);
        var value = new InvTransferReceiptTargetStock();
        value.setStockId(2301L);
        value.setItemType("product");
        value.setItemId(2401L);
        value.setProductId(2401L);
        value.setShopDeptId(202L);
        value.setWarehouseId(202L);
        value.setCurrentQuantity(new BigDecimal("10.0000").subtract(used));
        value.setAvailableQuantity(new BigDecimal("2.0000"));
        value.setLockedQuantity(new BigDecimal("5.0000").subtract(used));
        value.setQuarantineQuantity(new BigDecimal("3.0000"));
        value.setCostPrice(new BigDecimal("6.000000"));
        value.setTotalCost(new BigDecimal("60.000000")
                .subtract(used.multiply(new BigDecimal("6.000000"))));
        value.setVersion(7L);
        return value;
    }

    private static InvTransferReceiptTargetBalance balance(String consumed)
    {
        BigDecimal used = new BigDecimal(consumed);
        var value = new InvTransferReceiptTargetBalance();
        value.setBalanceId(2302L);
        value.setWarehouseId(202L);
        value.setItemType("product");
        value.setItemId(2401L);
        value.setProductId(2401L);
        value.setLotId(2303L);
        value.setLocationId(2304L);
        value.setCurrentQuantity(new BigDecimal("5.0000").subtract(used));
        value.setAvailableQuantity(BigDecimal.ZERO.setScale(4));
        value.setLockedQuantity(new BigDecimal("5.0000").subtract(used));
        value.setQuarantineQuantity(BigDecimal.ZERO.setScale(4));
        value.setCostPrice(new BigDecimal("6.000000"));
        value.setTotalCost(new BigDecimal("30.000000")
                .subtract(used.multiply(new BigDecimal("6.000000"))));
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

    private static Fixture fixture()
    {
        var mapper = mock(
                InvTransferReceiptDiscrepancyReturnReservationLifecycleMapper
                        .class);
        var service =
                new InvTransferReceiptDiscrepancyReturnReservationLifecycleService(
                        mapper, Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(mapper, service);
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
            InvTransferReceiptDiscrepancyReturnReservationLifecycleMapper
                    mapper,
            InvTransferReceiptDiscrepancyReturnReservationLifecycleService
                    service)
    {
    }
}
