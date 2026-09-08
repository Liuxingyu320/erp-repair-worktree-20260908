package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy.ActionSnapshot;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy.Actor;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy.Boundary;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy.PreparedExecution;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy.Request;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyDamageWriteOffFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptLocationCandidate;
import com.erp.inventory.domain.transfer.InvTransferReceiptTargetBalance;
import com.erp.inventory.domain.transfer.InvTransferReceiptTargetLot;
import com.erp.inventory.domain.transfer.InvTransferReceiptTargetStock;
import com.erp.inventory.mapper.InvTransferReceiptDiscrepancyDamageWriteOffMapper;

@DisplayName("V2调拨受损隔离库存原子写销效果所有者")
class InvTransferReceiptDiscrepancyDamageWriteOffEffectOwnerTest
{
    private static final String OWNER =
            "InvTransferReceiptDiscrepancyDamageWriteOffEffectOwner";

    @Test
    @DisplayName("按固定锁序执行守恒DML并追加两类不可变台账")
    void shouldUseFixedLockAndMutationOrder()
    {
        Fixture fixture = fixture();
        stubFacts(fixture.mapper());
        when(fixture.mapper().decrementStock(any())).thenReturn(1);
        when(fixture.mapper().decrementBalance(any())).thenReturn(1);
        when(fixture.mapper().insertStockLedger(any())).thenReturn(1);
        when(fixture.mapper().insertDamageLossLedger(any())).thenReturn(1);

        fixture.owner().apply(execution("damage_write_off"));

        InOrder order = inOrder(fixture.mapper());
        order.verify(fixture.mapper()).selectFactForUpdate(700L);
        order.verify(fixture.mapper()).selectWrittenOffQuantity(720L);
        order.verify(fixture.mapper()).selectStockForUpdate(any());
        order.verify(fixture.mapper()).selectLotForUpdate(any());
        order.verify(fixture.mapper()).selectLocationForUpdate(any());
        order.verify(fixture.mapper()).selectBalanceForUpdate(any());
        order.verify(fixture.mapper()).selectEligibleSerialsForUpdate(any());
        order.verify(fixture.mapper()).decrementStock(any());
        order.verify(fixture.mapper()).decrementBalance(any());
        order.verify(fixture.mapper()).insertStockLedger(any());
        order.verify(fixture.mapper()).insertDamageLossLedger(any());
        order.verifyNoMoreInteractions();
        verify(fixture.mapper(), never()).scrapSerial(any(), any());
        verify(fixture.mapper(), never()).insertDamageLossSerial(any(), any());
    }

    @Test
    @DisplayName("任一库存DML非单行时立即失败并等待外层事务回滚")
    void shouldFailClosedOnNonSingletonMutation()
    {
        Fixture fixture = fixture();
        stubFacts(fixture.mapper());
        when(fixture.mapper().decrementStock(any())).thenReturn(1);

        assertThatThrownBy(() -> fixture.owner().apply(
                execution("damage_write_off")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("隔离余额更新冲突")
                .hasMessageContaining("业务操作已回滚");

        verify(fixture.mapper(), never()).insertStockLedger(any());
        verify(fixture.mapper(), never()).insertDamageLossLedger(any());
    }

    @Test
    @DisplayName("其他原子效果在首个Mapper调用前拒绝")
    void shouldRejectForeignEffectBeforeLocking()
    {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> fixture.owner().apply(
                execution("responsibility_adjustment")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("执行事实无效");
        verifyNoInteractions(fixture.mapper());
    }

    @Test
    @DisplayName("效果所有者必须加入既有回滚事务且只被未接线事务调用")
    void shouldRequireExistingTransactionAndOnlyServeTransaction()
            throws Exception
    {
        Method apply =
                InvTransferReceiptDiscrepancyDamageWriteOffEffectOwner.class
                        .getMethod("apply", PreparedExecution.class);
        Transactional transaction = apply.getAnnotation(Transactional.class);

        assertThat(transaction).isNotNull();
        assertThat(transaction.propagation()).isEqualTo(
                Propagation.MANDATORY);
        assertThat(Arrays.asList(transaction.rollbackFor()))
                .contains(Exception.class);
        assertThat(productionReferences()).containsExactly(
                Path.of("service", "impl",
                        "InvTransferReceiptDiscrepancyAdjudicationExecutionTransaction.java"),
                Path.of("service", "impl", OWNER + ".java"));
    }

    private static Fixture fixture()
    {
        var mapper = mock(
                InvTransferReceiptDiscrepancyDamageWriteOffMapper.class);
        return new Fixture(
                new InvTransferReceiptDiscrepancyDamageWriteOffEffectOwner(
                        mapper),
                mapper);
    }

    private static void stubFacts(
            InvTransferReceiptDiscrepancyDamageWriteOffMapper mapper)
    {
        var fact = new InvTransferReceiptDiscrepancyDamageWriteOffFact();
        fact.setCaseId(700L);
        fact.setReceiptId(710L);
        fact.setReceiptAllocationId(720L);
        fact.setShipmentId(730L);
        fact.setTransferId(740L);
        fact.setShipmentAllocationId(750L);
        fact.setTargetWarehouseId(760L);
        fact.setItemType("product");
        fact.setItemId(770L);
        fact.setProductId(770L);
        fact.setTrackingPolicy("lot");
        fact.setDamagedQuantity(new BigDecimal("5.0000"));
        fact.setSourceCostPrice(new BigDecimal("10.000000"));
        fact.setDamagedCost(new BigDecimal("50.000000"));
        fact.setDamagedTargetLotId(780L);
        fact.setQuarantineLocationId(790L);
        fact.setDamagedTargetBalanceId(800L);

        var stock = new InvTransferReceiptTargetStock();
        stock.setStockId(810L);
        stock.setItemType("product");
        stock.setItemId(770L);
        stock.setProductId(770L);
        stock.setShopDeptId(760L);
        stock.setWarehouseId(760L);
        stock.setCurrentQuantity(new BigDecimal("20.0000"));
        stock.setAvailableQuantity(new BigDecimal("15.0000"));
        stock.setLockedQuantity(new BigDecimal("0.0000"));
        stock.setQuarantineQuantity(new BigDecimal("5.0000"));
        stock.setTotalCost(new BigDecimal("200.000000"));
        stock.setVersion(4L);

        var lot = new InvTransferReceiptTargetLot();
        lot.setLotId(780L);
        lot.setItemType("product");
        lot.setItemId(770L);
        lot.setProductId(770L);
        lot.setWarehouseId(760L);
        lot.setReceiptDisposition("damaged");
        lot.setQcStatus("quarantine");
        lot.setLotStatus("active");

        var location = new InvTransferReceiptLocationCandidate();
        location.setLocationId(790L);
        location.setWarehouseId(760L);
        location.setLocationType("quarantine");
        location.setStatus("0");
        location.setVirtualFlag("0");

        var balance = new InvTransferReceiptTargetBalance();
        balance.setBalanceId(800L);
        balance.setItemType("product");
        balance.setItemId(770L);
        balance.setProductId(770L);
        balance.setWarehouseId(760L);
        balance.setLotId(780L);
        balance.setLocationId(790L);
        balance.setCurrentQuantity(new BigDecimal("5.0000"));
        balance.setAvailableQuantity(new BigDecimal("0.0000"));
        balance.setLockedQuantity(new BigDecimal("0.0000"));
        balance.setQuarantineQuantity(new BigDecimal("5.0000"));
        balance.setTotalCost(new BigDecimal("50.000000"));
        balance.setVersion(6L);

        when(mapper.selectFactForUpdate(anyLong())).thenReturn(fact);
        when(mapper.selectWrittenOffQuantity(720L))
                .thenReturn(new BigDecimal("0.0000"));
        when(mapper.selectStockForUpdate(fact)).thenReturn(stock);
        when(mapper.selectLotForUpdate(fact)).thenReturn(lot);
        when(mapper.selectLocationForUpdate(fact)).thenReturn(location);
        when(mapper.selectBalanceForUpdate(fact)).thenReturn(balance);
        when(mapper.selectEligibleSerialsForUpdate(fact))
                .thenReturn(List.of());
    }

    private static PreparedExecution execution(String actionType)
    {
        boolean responsibility =
                "responsibility_adjustment".equals(actionType);
        ActionSnapshot action = new ActionSnapshot(900L,
                responsibility ? 2 : 1, actionType,
                responsibility ? "responsibility" : "resolution",
                new BigDecimal("5.0000"), new BigDecimal("50.000000"),
                "company", "pending", 0L, null);
        List<ActionSnapshot> actions = responsibility
                ? List.of(new ActionSnapshot(901L, 1,
                        "damage_write_off", "resolution",
                        new BigDecimal("5.0000"),
                        new BigDecimal("50.000000"), "company",
                        "pending", 0L, null), action)
                : List.of(action);
        Boundary boundary = new Boundary(700L, 4L, 4L,
                "adjudication_planned", 800L, "a".repeat(64), "damaged",
                new BigDecimal("5.0000"), new BigDecimal("10.000000"),
                new BigDecimal("50.000000"), "adjudication_planned",
                actions);
        Request request = new Request("execute-damage-0001", 700L, 4L,
                800L, 900L, 0L, "dispatch", null);
        Actor actor = new Actor(199L, "execution-user", Set.of(
                InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                        .REQUIRED_PERMISSION));
        return InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                .prepare(request, boundary, actor,
                        Instant.parse("2026-08-03T03:00:00Z"));
    }

    private static List<Path> productionReferences() throws Exception
    {
        Path root = Path.of(System.getProperty("user.dir"), "src", "main",
                "java", "com", "erp", "inventory").normalize();
        try (var paths = Files.walk(root))
        {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> contains(path, OWNER))
                    .map(root::relativize)
                    .sorted()
                    .toList();
        }
    }

    private static boolean contains(Path path, String value)
    {
        try
        {
            return Files.readString(path, StandardCharsets.UTF_8)
                    .contains(value);
        }
        catch (java.io.IOException error)
        {
            throw new IllegalStateException(error);
        }
    }

    private record Fixture(
            InvTransferReceiptDiscrepancyDamageWriteOffEffectOwner owner,
            InvTransferReceiptDiscrepancyDamageWriteOffMapper mapper)
    {
    }
}
