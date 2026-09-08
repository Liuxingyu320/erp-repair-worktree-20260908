package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.InvStock;
import com.erp.inventory.domain.InvTransferDetail;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.InvTransferReservation;
import com.erp.inventory.mapper.InvTransferReservationMapper;

@DisplayName("调拨商品级库存预留")
class InvTransferReservationServiceTest
{
    @Test
    @DisplayName("草稿规划只读返回可用量与缺口且不锁定或修改库存")
    void shouldPreviewDraftAvailabilityWithoutLockingOrMutatingStock()
    {
        FakeReservationMapper mapper = new FakeReservationMapper();
        mapper.addStock(1001L, "5");
        InvTransferReservationService service =
                new InvTransferReservationService(mapper);

        List<InvTransferReservationService.DraftAvailability> result =
                service.previewForDraft(order(), List.of(
                        detail(11L, 1001L, "7"),
                        detail(12L, 1002L, "2")));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).itemId()).isEqualTo(1001L);
        assertThat(result.get(0).availableQuantity())
                .isEqualByComparingTo("5");
        assertThat(result.get(0).shortageQuantity())
                .isEqualByComparingTo("2");
        assertThat(result.get(1).availableQuantity())
                .isEqualByComparingTo("0");
        assertThat(result.get(1).shortageQuantity())
                .isEqualByComparingTo("2");
        assertThat(mapper.planningReads).isEqualTo(2);
        assertThat(mapper.lockingReads).isZero();
        assertThat(mapper.reserveCalls).isZero();
        assertThat(mapper.reservations).isEmpty();
        assertThat(mapper.stocksByItem.get(1001L).getAvailableQuantity())
                .isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("草稿规划在库存读取前拒绝重复物料")
    void shouldRejectDuplicateDraftItemsBeforeStockRead()
    {
        FakeReservationMapper mapper = new FakeReservationMapper();
        mapper.addStock(1001L, "5");
        InvTransferReservationService service =
                new InvTransferReservationService(mapper);

        assertThatThrownBy(() -> service.previewForDraft(order(), List.of(
                detail(11L, 1001L, "1"),
                detail(12L, 1001L, "2"))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不得重复规划");

        assertThat(mapper.planningReads).isZero();
        assertThat(mapper.lockingReads).isZero();
    }

    @Test
    @DisplayName("同一物料多条明细先聚合锁行再按明细记录归属")
    void shouldAggregateStockMutationAndKeepDetailOwnership()
    {
        FakeReservationMapper mapper = new FakeReservationMapper();
        mapper.addStock(1001L, "20");
        InvTransferReservationService service =
                new InvTransferReservationService(mapper);

        service.reserveForSubmission(order(), List.of(
                detail(11L, 1001L, "3"),
                detail(12L, 1001L, "4")), "admin");

        InvStock stock = mapper.stocksByItem.get(1001L);
        assertThat(mapper.reserveCalls).isEqualTo(1);
        assertThat(mapper.routeLockingReads).isEqualTo(1);
        assertThat(mapper.lastWarehouseReplenishment).isTrue();
        assertThat(stock.getCurrentQuantity()).isEqualByComparingTo("20");
        assertThat(stock.getLockedQuantity()).isEqualByComparingTo("7");
        assertThat(stock.getAvailableQuantity()).isEqualByComparingTo("13");
        assertThat(mapper.reservations).hasSize(2);
        assertThat(mapper.reservations)
                .extracting(InvTransferReservation::getTransferDetailId)
                .containsExactly(11L, 12L);
        assertThat(mapper.reservations)
                .extracting(InvTransferReservation::getReservationRound)
                .containsOnly(1);
        assertThat(mapper.reservations.stream()
                .map(InvTransferReservation::getReservedQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("7");
    }

    @Test
    @DisplayName("返仓预留不套用补货业务根和商品归属锁定契约")
    void shouldKeepStoreReturnReservationIndependent()
    {
        FakeReservationMapper mapper = new FakeReservationMapper();
        mapper.addStock(1001L, "5");
        InvTransferReservationService service =
                new InvTransferReservationService(mapper);
        InvTransferOrder order = order();
        order.setTransferType("store_return");

        service.reserveForSubmission(order,
                List.of(detail(11L, 1001L, "2")), "store");

        assertThat(mapper.routeLockingReads).isZero();
        assertThat(mapper.lastWarehouseReplenishment).isFalse();
        assertThat(mapper.reserveCalls).isEqualTo(1);
    }

    @Test
    @DisplayName("锁内业务根重验失败时不读取或冻结库存")
    void shouldRejectChangedRouteBeforeStockLock()
    {
        FakeReservationMapper mapper = new FakeReservationMapper();
        mapper.addStock(1001L, "5");
        mapper.routeRoot = null;
        InvTransferReservationService service =
                new InvTransferReservationService(mapper);

        assertThatThrownBy(() -> service.reserveForSubmission(order(),
                List.of(detail(11L, 1001L, "2")), "store"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("业务根已变化");

        assertThat(mapper.routeLockingReads).isEqualTo(1);
        assertThat(mapper.lockingReads).isZero();
        assertThat(mapper.reserveCalls).isZero();
    }

    @Test
    @DisplayName("驳回释放后新审批轮次可重新冻结且保留旧轮台账")
    void shouldReserveNewRoundWithoutOverwritingReleasedHistory()
    {
        FakeReservationMapper mapper = new FakeReservationMapper();
        mapper.addStock(1001L, "10");
        InvTransferReservationService service =
                new InvTransferReservationService(mapper);
        InvTransferOrder order = order();
        InvTransferDetail detail = detail(11L, 1001L, "4");

        service.reserveForSubmission(order, List.of(detail), "applicant");
        order.setApprovalRound(1);
        service.releaseAllRemaining(order, "approver");
        service.reserveForSubmission(order, List.of(detail), "applicant");

        assertThat(mapper.reservations).hasSize(2);
        assertThat(mapper.reservations)
                .extracting(InvTransferReservation::getReservationRound)
                .containsExactly(1, 2);
        assertThat(mapper.reservations.get(0).getStatus())
                .isEqualTo("RELEASED");
        assertThat(mapper.reservations.get(1).getStatus())
                .isEqualTo("ACTIVE");
        InvStock stock = mapper.stocksByItem.get(1001L);
        assertThat(stock.getLockedQuantity()).isEqualByComparingTo("4");
        assertThat(stock.getAvailableQuantity()).isEqualByComparingTo("6");
    }

    @Test
    @DisplayName("任一物料不足时校验阶段拒绝且不产生部分冻结")
    void shouldRejectWholeOrderBeforeAnyMutation()
    {
        FakeReservationMapper mapper = new FakeReservationMapper();
        mapper.addStock(1001L, "10");
        mapper.addStock(1002L, "1");
        InvTransferReservationService service =
                new InvTransferReservationService(mapper);

        assertThatThrownBy(() -> service.reserveForSubmission(order(),
                List.of(detail(11L, 1001L, "3"),
                        detail(12L, 1002L, "2")), "admin"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("可用库存不足");

        assertThat(mapper.reserveCalls).isZero();
        assertThat(mapper.reservations).isEmpty();
        assertThat(mapper.stocksByItem.get(1001L).getLockedQuantity())
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("发货只消耗本单冻结量且终止释放未发剩余量")
    void shouldConsumeOwnedReservationAndReleaseRemainder()
    {
        FakeReservationMapper mapper = new FakeReservationMapper();
        mapper.addStock(1001L, "10");
        InvTransferReservationService service =
                new InvTransferReservationService(mapper);
        InvTransferDetail detail = detail(11L, 1001L, "10");
        InvTransferOrder order = order();
        service.reserveForSubmission(order, List.of(detail), "admin");
        order.setApprovalRound(1);

        InvTransferReservationService.StockConsumption consumption =
                service.consumeForShipment(order, detail,
                        new BigDecimal("3"), "warehouse");
        service.releaseAllRemaining(order, "approver");

        InvStock stock = mapper.stocksByItem.get(1001L);
        InvTransferReservation reservation = mapper.reservations.get(0);
        assertThat(consumption.beforeQuantity()).isEqualByComparingTo("10");
        assertThat(consumption.afterQuantity()).isEqualByComparingTo("7");
        assertThat(stock.getCurrentQuantity()).isEqualByComparingTo("7");
        assertThat(stock.getLockedQuantity()).isEqualByComparingTo("0");
        assertThat(stock.getAvailableQuantity()).isEqualByComparingTo("7");
        assertThat(reservation.getConsumedQuantity())
                .isEqualByComparingTo("3");
        assertThat(reservation.getReleasedQuantity())
                .isEqualByComparingTo("7");
        assertThat(reservation.getStatus()).isEqualTo("CLOSED");
    }

    @Test
    @DisplayName("分批发完最后一件后预留台账进入已消耗终态")
    void shouldMarkReservationConsumedAfterFinalShipment()
    {
        FakeReservationMapper mapper = new FakeReservationMapper();
        mapper.addStock(1001L, "10");
        InvTransferReservationService service =
                new InvTransferReservationService(mapper);
        InvTransferDetail detail = detail(11L, 1001L, "5");
        InvTransferOrder order = order();
        service.reserveForSubmission(order, List.of(detail), "admin");
        order.setApprovalRound(1);

        service.consumeForShipment(order, detail, new BigDecimal("2"),
                "warehouse");
        assertThat(mapper.reservations.get(0).getStatus())
                .isEqualTo("PARTIAL");

        service.consumeForShipment(order, detail, new BigDecimal("3"),
                "warehouse");

        InvTransferReservation reservation = mapper.reservations.get(0);
        assertThat(reservation.getReservedQuantity())
                .isEqualByComparingTo("5");
        assertThat(reservation.getConsumedQuantity())
                .isEqualByComparingTo("5");
        assertThat(reservation.getReleasedQuantity())
                .isEqualByComparingTo("0");
        assertThat(reservation.getStatus()).isEqualTo("CONSUMED");
        assertThat(mapper.stocksByItem.get(1001L).getLockedQuantity())
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("批量发货先固定锁定汇总库存再按明细精确扣减成本")
    void shouldConsumeLockedShipmentWithExactDetailCosts()
    {
        FakeReservationMapper mapper = new FakeReservationMapper();
        mapper.addStock(1001L, "20");
        InvTransferReservationService service =
                new InvTransferReservationService(mapper);
        InvTransferOrder order = order();
        InvTransferDetail first = detail(11L, 1001L, "3");
        InvTransferDetail second = detail(12L, 1001L, "4");
        service.reserveForSubmission(order, List.of(first, second), "admin");
        order.setApprovalRound(1);

        InvTransferReservationService.LockedShipmentReservations locked =
                service.lockForShipment(order, List.of(first, second),
                        Map.of(12L, new BigDecimal("3"),
                                11L, new BigDecimal("2")));
        InvTransferReservationService.ShipmentConsumption consumption =
                service.consumeLockedShipment(locked,
                        Map.of(11L, new BigDecimal("3"),
                                12L, new BigDecimal("6")),
                        "warehouse");

        InvStock stock = mapper.stocksByItem.get(1001L);
        assertThat(mapper.lastLockedStockIds).containsExactly(1001L);
        assertThat(mapper.consumeCalls).isEqualTo(1);
        assertThat(stock.getCurrentQuantity()).isEqualByComparingTo("15");
        assertThat(stock.getLockedQuantity()).isEqualByComparingTo("2");
        assertThat(stock.getAvailableQuantity()).isEqualByComparingTo("13");
        assertThat(stock.getTotalCost()).isEqualByComparingTo("31");
        assertThat(mapper.reservations.get(0).getConsumedQuantity())
                .isEqualByComparingTo("2");
        assertThat(mapper.reservations.get(1).getConsumedQuantity())
                .isEqualByComparingTo("3");

        InvTransferReservationService.StockConsumption firstResult =
                consumption.byTransferDetailId().get(11L);
        InvTransferReservationService.StockConsumption secondResult =
                consumption.byTransferDetailId().get(12L);
        assertThat(firstResult.beforeQuantity()).isEqualByComparingTo("20");
        assertThat(firstResult.afterQuantity()).isEqualByComparingTo("18");
        assertThat(firstResult.costPrice()).isEqualByComparingTo("1.5");
        assertThat(secondResult.beforeQuantity()).isEqualByComparingTo("18");
        assertThat(secondResult.afterQuantity()).isEqualByComparingTo("15");
        assertThat(secondResult.costPrice()).isEqualByComparingTo("2");
    }

    @Test
    @DisplayName("差异补发重新冻结来源可用量并扩充原明细预留")
    void shouldReserveAgainForReshipment()
    {
        FakeReservationMapper mapper = new FakeReservationMapper();
        mapper.addStock(1001L, "10");
        InvTransferReservationService service =
                new InvTransferReservationService(mapper);
        InvTransferOrder order = order();
        InvTransferDetail detail = detail(11L, 1001L, "5");
        service.reserveForSubmission(order, List.of(detail), "admin");
        order.setApprovalRound(1);
        service.consumeForShipment(order, detail, new BigDecimal("5"),
                "warehouse");

        service.reserveForReshipment(order, detail, new BigDecimal("2"),
                "handler");

        InvStock stock = mapper.stocksByItem.get(1001L);
        InvTransferReservation reservation = mapper.reservations.get(0);
        assertThat(stock.getCurrentQuantity()).isEqualByComparingTo("5");
        assertThat(stock.getLockedQuantity()).isEqualByComparingTo("2");
        assertThat(stock.getAvailableQuantity()).isEqualByComparingTo("3");
        assertThat(reservation.getReservedQuantity())
                .isEqualByComparingTo("7");
        assertThat(reservation.getConsumedQuantity())
                .isEqualByComparingTo("5");
        assertThat(reservation.getStatus()).isEqualTo("PARTIAL");
    }

    private InvTransferOrder order()
    {
        InvTransferOrder order = new InvTransferOrder();
        order.setTransferId(900L);
        order.setTransferType("warehouse");
        order.setFromDeptId(201L);
        order.setFromWarehouseId(301L);
        order.setToDeptId(201L);
        order.setToWarehouseId(201L);
        return order;
    }

    private InvTransferDetail detail(Long detailId, Long itemId,
            String quantity)
    {
        InvTransferDetail detail = new InvTransferDetail();
        detail.setDetailId(detailId);
        detail.setTransferId(900L);
        detail.setItemType("product");
        detail.setItemId(itemId);
        detail.setProductId(itemId);
        detail.setItemName("物料" + itemId);
        detail.setQuantity(new BigDecimal(quantity));
        return detail;
    }

    private static final class FakeReservationMapper
            implements InvTransferReservationMapper
    {
        private final Map<Long, InvStock> stocksByItem =
                new LinkedHashMap<>();
        private final Map<Long, InvStock> stocksById =
                new LinkedHashMap<>();
        private final List<InvTransferReservation> reservations =
                new ArrayList<>();
        private int reserveCalls;
        private int consumeCalls;
        private int planningReads;
        private int lockingReads;
        private int routeLockingReads;
        private boolean lastWarehouseReplenishment;
        private Long routeRoot = 100L;
        private List<Long> lastLockedStockIds = List.of();
        private long nextReservationId = 1L;

        void addStock(Long itemId, String quantity)
        {
            InvStock stock = new InvStock();
            stock.setStockId(itemId);
            stock.setItemType("product");
            stock.setItemId(itemId);
            stock.setProductId(itemId);
            stock.setShopDeptId(301L);
            stock.setWarehouseId(301L);
            stock.setCurrentQuantity(new BigDecimal(quantity));
            stock.setLockedQuantity(BigDecimal.ZERO);
            stock.setAvailableQuantity(new BigDecimal(quantity));
            stock.setCostPrice(new BigDecimal("2"));
            stock.setTotalCost(new BigDecimal(quantity)
                    .multiply(new BigDecimal("2")));
            stock.setVersion(0L);
            stocksByItem.put(itemId, stock);
            stocksById.put(stock.getStockId(), stock);
        }

        @Override
        public Long selectWarehouseReplenishmentRouteForUpdate(
                Long sourceWarehouseId, Long targetStoreId)
        {
            routeLockingReads++;
            return routeRoot;
        }

        @Override
        public InvStock selectStockForUpdate(String itemType, Long itemId,
                Long locationDeptId, boolean warehouseReplenishment)
        {
            lockingReads++;
            lastWarehouseReplenishment = warehouseReplenishment;
            return stocksByItem.get(itemId);
        }

        @Override
        public InvStock selectStockForPlanning(String itemType, Long itemId,
                Long locationDeptId)
        {
            planningReads++;
            return stocksByItem.get(itemId);
        }

        @Override
        public InvStock selectStockByIdForUpdate(Long stockId)
        {
            return stocksById.get(stockId);
        }

        @Override
        public List<InvStock> selectStocksByIdsForUpdate(
                List<Long> stockIds)
        {
            lastLockedStockIds = List.copyOf(stockIds);
            return stockIds.stream().sorted().map(stocksById::get).toList();
        }

        @Override
        public int reserveStock(Long stockId, Long version,
                BigDecimal quantity, String updateBy)
        {
            InvStock stock = stocksById.get(stockId);
            if (!version.equals(stock.getVersion())
                    || stock.getAvailableQuantity().compareTo(quantity) < 0)
            {
                return 0;
            }
            reserveCalls++;
            stock.setLockedQuantity(stock.getLockedQuantity().add(quantity));
            stock.setAvailableQuantity(
                    stock.getAvailableQuantity().subtract(quantity));
            stock.setVersion(stock.getVersion() + 1);
            return 1;
        }

        @Override
        public int releaseStock(Long stockId, Long version,
                BigDecimal quantity, String updateBy)
        {
            InvStock stock = stocksById.get(stockId);
            if (!version.equals(stock.getVersion())
                    || stock.getLockedQuantity().compareTo(quantity) < 0)
            {
                return 0;
            }
            stock.setLockedQuantity(stock.getLockedQuantity().subtract(quantity));
            stock.setAvailableQuantity(
                    stock.getAvailableQuantity().add(quantity));
            stock.setVersion(stock.getVersion() + 1);
            return 1;
        }

        @Override
        public int consumeReservedStockWithCost(Long stockId, Long version,
                BigDecimal quantity, BigDecimal deductCost, String updateBy)
        {
            InvStock stock = stocksById.get(stockId);
            if (!version.equals(stock.getVersion())
                    || stock.getLockedQuantity().compareTo(quantity) < 0
                    || stock.getCurrentQuantity().compareTo(quantity) < 0
                    || stock.getTotalCost().compareTo(deductCost) < 0)
            {
                return 0;
            }
            consumeCalls++;
            stock.setCurrentQuantity(
                    stock.getCurrentQuantity().subtract(quantity));
            stock.setLockedQuantity(
                    stock.getLockedQuantity().subtract(quantity));
            stock.setTotalCost(stock.getTotalCost().subtract(deductCost));
            stock.setVersion(stock.getVersion() + 1);
            return 1;
        }

        @Override
        public List<InvTransferReservation> selectByTransferRoundForUpdate(
                Long transferId, Integer reservationRound)
        {
            return reservations.stream()
                    .filter(row -> transferId.equals(row.getTransferId()))
                    .filter(row -> reservationRound.equals(
                            row.getReservationRound()))
                    .sorted(Comparator.comparing(
                            InvTransferReservation::getStockId)
                            .thenComparing(
                                    InvTransferReservation::getReservationId))
                    .toList();
        }

        @Override
        public InvTransferReservation selectByTransferDetailRoundForUpdate(
                Long transferId, Long transferDetailId,
                Integer reservationRound)
        {
            return reservations.stream()
                    .filter(row -> transferId.equals(row.getTransferId()))
                    .filter(row -> transferDetailId.equals(
                            row.getTransferDetailId()))
                    .filter(row -> reservationRound.equals(
                            row.getReservationRound()))
                    .findFirst().orElse(null);
        }

        @Override
        public int insertReservation(InvTransferReservation reservation)
        {
            reservation.setReservationId(nextReservationId++);
            reservations.add(reservation);
            return 1;
        }

        @Override
        public int increaseReservation(Long reservationId, Long version,
                BigDecimal quantity, String updateBy)
        {
            InvTransferReservation row = reservation(reservationId);
            if (!version.equals(row.getVersion())) return 0;
            row.setReservedQuantity(row.getReservedQuantity().add(quantity));
            row.setStatus(row.getConsumedQuantity().signum() == 0
                    && row.getReleasedQuantity().signum() == 0
                            ? "ACTIVE" : "PARTIAL");
            row.setVersion(row.getVersion() + 1);
            return 1;
        }

        @Override
        public int consumeReservation(Long reservationId, Long version,
                BigDecimal quantity, String updateBy)
        {
            InvTransferReservation row = reservation(reservationId);
            if (!version.equals(row.getVersion())
                    || remaining(row).compareTo(quantity) < 0) return 0;
            row.setConsumedQuantity(row.getConsumedQuantity().add(quantity));
            finishStatus(row);
            row.setVersion(row.getVersion() + 1);
            return 1;
        }

        @Override
        public int releaseReservation(Long reservationId, Long version,
                BigDecimal quantity, String updateBy)
        {
            InvTransferReservation row = reservation(reservationId);
            if (!version.equals(row.getVersion())
                    || remaining(row).compareTo(quantity) < 0) return 0;
            row.setReleasedQuantity(row.getReleasedQuantity().add(quantity));
            finishStatus(row);
            row.setVersion(row.getVersion() + 1);
            return 1;
        }

        private InvTransferReservation reservation(Long reservationId)
        {
            return reservations.stream()
                    .filter(row -> reservationId.equals(row.getReservationId()))
                    .findFirst().orElseThrow();
        }

        private BigDecimal remaining(InvTransferReservation row)
        {
            return row.getReservedQuantity()
                    .subtract(row.getConsumedQuantity())
                    .subtract(row.getReleasedQuantity());
        }

        private void finishStatus(InvTransferReservation row)
        {
            if (remaining(row).signum() > 0)
            {
                row.setStatus("PARTIAL");
            }
            else if (row.getReleasedQuantity().signum() == 0)
            {
                row.setStatus("CONSUMED");
            }
            else if (row.getConsumedQuantity().signum() == 0)
            {
                row.setStatus("RELEASED");
            }
            else
            {
                row.setStatus("CLOSED");
            }
        }
    }
}
