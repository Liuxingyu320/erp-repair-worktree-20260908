package com.erp.inventory.domain.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy.ActionSnapshot;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy.Actor;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy.Boundary;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy.PreparedExecution;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy.Request;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyDamageWriteOffPolicy.Prepared;

@DisplayName("V2调拨受损隔离库存原子写销策略")
class InvTransferReceiptDiscrepancyDamageWriteOffPolicyTest
{
    @Test
    @DisplayName("序列号写销稳定选择单件并守恒库存余额与成本")
    void shouldPrepareConservedSerialWriteOff()
    {
        Fixture fixture = fixture("serial");

        Prepared prepared = prepare(fixture, execution("2.0000",
                "damage_write_off"), "0.0000", List.of(
                        serial(3L, 103L), serial(1L, 101L),
                        serial(2L, 102L)));

        assertThat(prepared.lossType()).isEqualTo("damaged_goods");
        assertThat(prepared.stockLedgerRequestId()).isEqualTo(
                "adjdmg:900:0");
        assertThat(prepared.allocationWrittenOffBefore()).isEqualByComparingTo(
                "0.0000");
        assertThat(prepared.allocationWrittenOffAfter()).isEqualByComparingTo(
                "2.0000");
        assertThat(prepared.stockCurrentAfter()).isEqualByComparingTo(
                "18.0000");
        assertThat(prepared.stockQuarantineAfter()).isEqualByComparingTo(
                "3.0000");
        assertThat(prepared.stockTotalCostAfter()).isEqualByComparingTo(
                "180.000000");
        assertThat(prepared.balanceCurrentAfter()).isEqualByComparingTo(
                "3.0000");
        assertThat(prepared.balanceQuarantineAfter()).isEqualByComparingTo(
                "3.0000");
        assertThat(prepared.balanceTotalCostAfter()).isEqualByComparingTo(
                "30.000000");
        assertThat(prepared.serials())
                .extracting(
                        InvTransferReceiptDiscrepancyDamageWriteOffPolicy.Serial
                                ::serialId)
                .containsExactly(101L, 102L);
    }

    @Test
    @DisplayName("批次跟踪运输破损写销不产生序列号且使用独立损失类型")
    void shouldPrepareLotTrackedTransportDamage()
    {
        Fixture fixture = fixture("lot");

        Prepared prepared = prepare(fixture, execution("1.0000",
                "transport_loss_write_off"), "1.0000", List.of());

        assertThat(prepared.lossType()).isEqualTo("transport_damage");
        assertThat(prepared.serials()).isEmpty();
        assertThat(prepared.allocationWrittenOffAfter()).isEqualByComparingTo(
                "2.0000");
    }

    @Test
    @DisplayName("收货分配累计写销不得超过原受损数量")
    void shouldRejectAllocationOverConsumption()
    {
        Fixture fixture = fixture("lot");

        assertThatThrownBy(() -> prepare(fixture,
                execution("2.0000", "damage_write_off"), "4.0000",
                List.of()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("剩余数量");
    }

    @Test
    @DisplayName("收货受损总成本必须由冻结成本价和数量独立复算")
    void shouldRejectCorruptedReceiptDamageCost()
    {
        Fixture fixture = fixture("lot");
        fixture.fact().setDamagedCost(new BigDecimal("49.999999"));

        assertThatThrownBy(() -> prepare(fixture,
                execution("1.0000", "damage_write_off"), "0",
                List.of()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("收货分配锚点");
    }

    @Test
    @DisplayName("汇总或隔离余额维度和成本守恒漂移全部失败关闭")
    void shouldRejectInventoryDrift()
    {
        Fixture brokenInvariant = fixture("lot");
        brokenInvariant.stock().setAvailableQuantity(new BigDecimal("16"));
        assertThatThrownBy(() -> prepare(brokenInvariant,
                execution("1.0000", "damage_write_off"), "0",
                List.of()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("汇总库存维度或守恒");

        Fixture mixedBalance = fixture("lot");
        mixedBalance.balance().setAvailableQuantity(BigDecimal.ONE);
        mixedBalance.balance().setCurrentQuantity(new BigDecimal("6"));
        assertThatThrownBy(() -> prepare(mixedBalance,
                execution("1.0000", "damage_write_off"), "0",
                List.of()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("混入可用数量");

        Fixture insufficientCost = fixture("lot");
        insufficientCost.balance().setTotalCost(new BigDecimal("5"));
        assertThatThrownBy(() -> prepare(insufficientCost,
                execution("1.0000", "damage_write_off"), "0",
                List.of()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("成本不可核验");
    }

    @Test
    @DisplayName("其他退回动作已锁定隔离数量时仍可写销剩余隔离数量")
    void shouldAllowLockedQuarantineOwnedByReturnReservation()
    {
        Fixture fixture = fixture("lot");
        fixture.stock().setLockedQuantity(new BigDecimal("1.0000"));
        fixture.stock().setQuarantineQuantity(new BigDecimal("4.0000"));
        fixture.balance().setLockedQuantity(new BigDecimal("1.0000"));
        fixture.balance().setQuarantineQuantity(new BigDecimal("4.0000"));

        var prepared = prepare(fixture,
                execution("1.0000", "damage_write_off"), "0",
                List.of());

        assertThat(prepared.stockLockedQuantity())
                .isEqualByComparingTo("1.0000");
        assertThat(prepared.balanceLockedQuantity())
                .isEqualByComparingTo("1.0000");
        assertThat(prepared.balanceQuarantineAfter())
                .isEqualByComparingTo("3.0000");
    }

    @Test
    @DisplayName("序列号数量必须为整数且单件审计位置必须唯一一致")
    void shouldRejectInvalidSerialSelection()
    {
        Fixture decimal = fixture("serial");
        assertThatThrownBy(() -> prepare(decimal,
                execution("1.5000", "damage_write_off"), "0", List.of(
                        serial(1L, 101L), serial(2L, 102L))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("数量必须为整数");

        Fixture missing = fixture("serial");
        assertThatThrownBy(() -> prepare(missing,
                execution("2.0000", "damage_write_off"), "0",
                List.of(serial(1L, 101L))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("缺少足量");

        Fixture duplicate = fixture("serial");
        assertThatThrownBy(() -> prepare(duplicate,
                execution("2.0000", "damage_write_off"), "0", List.of(
                        serial(1L, 101L), serial(2L, 101L))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("审计或当前位置");

        Fixture wrongBalance = fixture("serial");
        var moved = serial(2L, 102L);
        moved.setCurrentBalanceId(999L);
        assertThatThrownBy(() -> prepare(wrongBalance,
                execution("2.0000", "damage_write_off"), "0", List.of(
                        serial(1L, 101L), moved)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("审计或当前位置");
    }

    @Test
    @DisplayName("非受损原子效果在读取库存前即可拒绝")
    void shouldRejectForeignAtomicEffect()
    {
        PreparedExecution responsibility = execution("5.0000",
                "responsibility_adjustment");

        assertThatThrownBy(() ->
                InvTransferReceiptDiscrepancyDamageWriteOffPolicy
                        .validateExecution(responsibility))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("执行事实无效");
    }

    private static Prepared prepare(Fixture fixture,
            PreparedExecution execution, String writtenOff,
            List<InvTransferReceiptDiscrepancyDamageWriteOffSerialFact>
                    serials)
    {
        return InvTransferReceiptDiscrepancyDamageWriteOffPolicy.prepare(
                execution, fixture.fact(), new BigDecimal(writtenOff),
                fixture.stock(), fixture.lot(), fixture.location(),
                fixture.balance(), serials);
    }

    private static Fixture fixture(String trackingPolicy)
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
        fact.setTrackingPolicy(trackingPolicy);
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
        stock.setCostPrice(new BigDecimal("10.000000"));
        stock.setTotalCost(new BigDecimal("200.000000"));
        stock.setVersion(4L);

        var lot = new InvTransferReceiptTargetLot();
        lot.setLotId(780L);
        lot.setItemType("product");
        lot.setItemId(770L);
        lot.setProductId(770L);
        lot.setWarehouseId(760L);
        lot.setSourceLotId(701L);
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
        balance.setCostPrice(new BigDecimal("10.000000"));
        balance.setTotalCost(new BigDecimal("50.000000"));
        balance.setVersion(6L);
        return new Fixture(fact, stock, lot, location, balance);
    }

    private static InvTransferReceiptDiscrepancyDamageWriteOffSerialFact
            serial(Long receiptSerialId, Long serialId)
    {
        var value =
                new InvTransferReceiptDiscrepancyDamageWriteOffSerialFact();
        value.setReceiptSerialId(receiptSerialId);
        value.setReceiptId(710L);
        value.setReceiptAllocationId(720L);
        value.setShipmentId(730L);
        value.setShipmentAllocationId(750L);
        value.setSerialId(serialId);
        value.setSerialNoSnapshot("SN-" + serialId);
        value.setCurrentSerialNo("SN-" + serialId);
        value.setDisposition("damaged");
        value.setItemType("product");
        value.setItemId(770L);
        value.setCurrentWarehouseId(760L);
        value.setCurrentBalanceId(800L);
        value.setCurrentLotId(780L);
        value.setCurrentLocationId(790L);
        value.setReceiptStatusAfter("quarantine");
        value.setCurrentStatus("quarantine");
        return value;
    }

    private static PreparedExecution execution(String quantity,
            String actionType)
    {
        BigDecimal targetQuantity = new BigDecimal(quantity);
        BigDecimal remaining = new BigDecimal("5.0000")
                .subtract(targetQuantity);
        boolean responsibility =
                "responsibility_adjustment".equals(actionType);
        List<ActionSnapshot> actions = responsibility
                ? List.of(action(901L, 1, "damage_write_off",
                        new BigDecimal("5.0000")),
                        new ActionSnapshot(900L, 2, actionType,
                                "responsibility", targetQuantity,
                                targetQuantity.multiply(
                                        new BigDecimal("10.000000"))
                                        .setScale(6),
                                "company", "pending", 0L, null))
                : remaining.signum() == 0
                        ? List.of(action(900L, 1, actionType,
                                targetQuantity))
                        : List.of(action(900L, 1, actionType,
                                targetQuantity), action(901L, 2,
                                        "return_to_source", remaining));
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

    private static ActionSnapshot action(Long id, int sequence, String type,
            BigDecimal quantity)
    {
        return new ActionSnapshot(id, sequence, type, "resolution",
                quantity,
                quantity.multiply(new BigDecimal("10.000000"))
                        .setScale(6),
                "company", "pending", 0L, null);
    }

    private record Fixture(
            InvTransferReceiptDiscrepancyDamageWriteOffFact fact,
            InvTransferReceiptTargetStock stock,
            InvTransferReceiptTargetLot lot,
            InvTransferReceiptLocationCandidate location,
            InvTransferReceiptTargetBalance balance)
    {
    }
}
