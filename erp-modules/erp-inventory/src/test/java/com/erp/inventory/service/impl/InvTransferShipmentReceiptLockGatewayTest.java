package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.constant.InvStatusConstants;
import com.erp.inventory.constant.InvTransferShipmentWriteVersions;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.InvTransferShipment;
import com.erp.inventory.domain.dto.InvTransferReceiptDiscrepancyReturnReceiptAllocationRequest;
import com.erp.inventory.domain.dto.InvTransferReceiptDiscrepancyReturnReceiptCreateRequest;
import com.erp.inventory.domain.dto.InvTransferShipmentReceiptAllocationRequest;
import com.erp.inventory.domain.dto.InvTransferShipmentReceiptCreateRequest;
import com.erp.inventory.domain.transfer.InvShipmentPlanningItemKey;
import com.erp.inventory.domain.transfer.InvTransferReceiptBalanceKey;
import com.erp.inventory.domain.transfer.InvTransferReceiptDerivedLotKey;
import com.erp.inventory.domain.transfer.InvTransferReceiptLocationCandidate;
import com.erp.inventory.domain.transfer.InvTransferReceiptLockedAllocation;
import com.erp.inventory.domain.transfer.InvTransferReceiptLockedDetail;
import com.erp.inventory.domain.transfer.InvTransferReceiptLockedTransferDetail;
import com.erp.inventory.domain.transfer.InvTransferReceiptPlanningAllocationFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptTargetLot;
import com.erp.inventory.domain.transfer.InvTransferShipmentReceiptPlanComposer;
import com.erp.inventory.domain.transfer.InvTransferShipmentReceiptLockedBoundary;
import com.erp.inventory.domain.transfer.InvWarehouseStockMode;
import com.erp.inventory.mapper.InvTransferOrderMapper;
import com.erp.inventory.mapper.InvTransferShipmentMapper;
import com.erp.inventory.mapper.InvTransferShipmentReceiptPersistenceMapper;
import com.erp.inventory.mapper.InvTransferShipmentReceiptPlanningMapper;

@DisplayName("V2调拨收货固定锁序 Gateway")
class InvTransferShipmentReceiptLockGatewayTest
{
    @Test
    @DisplayName("全部事实按固定顺序锁定且集合参数先去重升序")
    void shouldLockEveryBoundaryInDeterministicOrder()
    {
        Fixture fixture = fixture();
        InvTransferShipment shipment = shipment();
        InvTransferOrder order = order();
        List<InvTransferReceiptLockedDetail> details = List.of(
                detail(72L, 402L, "gift", 1002L, "1"),
                detail(71L, 401L, "product", 1001L, "2"));
        List<InvTransferReceiptLockedAllocation> allocations = List.of(
                allocation(82L, 72L, 402L, 502L, 802L),
                allocation(81L, 71L, 401L, 501L, 801L));
        InvWarehouseStockMode mode = mode();
        List<InvTransferReceiptLocationCandidate> locations = List.of(
                location(902L, 302L, "quarantine"),
                location(802L, 301L, "storage"),
                location(901L, 302L, "storage"),
                location(801L, 301L, "storage"));
        List<InvTransferReceiptTargetLot> derivedLots = List.of(
                derivedLot(704L, "gift", 1002L, 502L, "damaged"),
                derivedLot(701L, "product", 1001L, 501L, "accepted"),
                derivedLot(703L, "gift", 1002L, 502L, "accepted"),
                derivedLot(702L, "product", 1001L, 501L, "damaged"));
        List<InvTransferReceiptPlanningAllocationFact> planningFacts =
                List.of(planningFact(82L, 72L, 402L, "gift", 1002L,
                        502L, 802L, "1", "5"),
                        planningFact(81L, 71L, 401L, "product", 1001L,
                                501L, 801L, "2", "10"));

        when(fixture.shipmentMapper().selectByIdForUpdate(91L))
                .thenReturn(shipment);
        when(fixture.orderMapper()
                .selectInvTransferOrderByIdForUpdate(900L))
                .thenReturn(order);
        when(fixture.lockMapper().selectTransferDetailsForUpdate(900L))
                .thenReturn(List.of(
                        transferDetail(402L, "gift", 1002L, "1"),
                        transferDetail(401L, "product", 1001L, "2")));
        when(fixture.lockMapper().selectShipmentDetailsForUpdate(91L))
                .thenReturn(details);
        when(fixture.lockMapper().selectAllocationsForUpdate(91L))
                .thenReturn(allocations);
        when(fixture.lockMapper()
                .selectTargetWarehouseModeForUpdate(302L))
                .thenReturn(mode);
        when(fixture.lockMapper().selectPoliciesForUpdate(anyList()))
                .thenReturn(List.of());
        when(fixture.lockMapper().selectTargetStocksForUpdate(eq(302L),
                eq(302L), anyList())).thenReturn(List.of());
        when(fixture.lockMapper().selectSourceLotsForUpdate(anyList()))
                .thenReturn(List.of(sourceLot(502L), sourceLot(501L)));
        when(fixture.lockMapper().selectLocationsForUpdate(anyList(),
                eq(302L))).thenReturn(locations);
        when(fixture.lockMapper().selectDerivedLotsForUpdate(anyList()))
                .thenReturn(derivedLots);
        when(fixture.lockMapper().selectTargetBalancesForUpdate(eq(302L),
                anyList())).thenReturn(List.of());
        when(fixture.lockMapper().selectShipmentSerialsForUpdate(91L))
                .thenReturn(List.of());
        when(fixture.planningMapper().selectAllocations(91L))
                .thenReturn(planningFacts);

        InvTransferShipmentReceiptLockGateway.LockedHeader header =
                fixture.gateway().lockHeader(91L);
        InvTransferShipmentReceiptLockedBoundary result =
                fixture.gateway().lockAndCompose(header, 302L,
                        receiptRequest());

        assertThat(result.composition().canCreateReceipt()).isTrue();
        assertThat(result.composition().lines()).hasSize(2);
        assertThat(result.allocations()).containsOnlyKeys(81L, 82L);
        InOrder orderVerifier = inOrder(fixture.shipmentMapper(),
                fixture.orderMapper(), fixture.lockMapper(),
                fixture.planningMapper());
        orderVerifier.verify(fixture.shipmentMapper())
                .selectByIdForUpdate(91L);
        orderVerifier.verify(fixture.orderMapper())
                .selectInvTransferOrderByIdForUpdate(900L);
        orderVerifier.verify(fixture.lockMapper())
                .selectTransferDetailsForUpdate(900L);
        orderVerifier.verify(fixture.lockMapper())
                .selectShipmentDetailsForUpdate(91L);
        orderVerifier.verify(fixture.lockMapper())
                .selectAllocationsForUpdate(91L);
        orderVerifier.verify(fixture.lockMapper())
                .selectTargetWarehouseModeForUpdate(302L);
        List<InvShipmentPlanningItemKey> itemKeys = List.of(
                new InvShipmentPlanningItemKey("gift", 1002L),
                new InvShipmentPlanningItemKey("product", 1001L));
        orderVerifier.verify(fixture.lockMapper())
                .selectPoliciesForUpdate(itemKeys);
        orderVerifier.verify(fixture.lockMapper())
                .selectTargetStocksForUpdate(302L, 302L, itemKeys);
        orderVerifier.verify(fixture.lockMapper())
                .selectSourceLotsForUpdate(List.of(501L, 502L));
        orderVerifier.verify(fixture.lockMapper())
                .selectLocationsForUpdate(List.of(801L, 802L), 302L);
        List<InvTransferReceiptDerivedLotKey> lotKeys = List.of(
                new InvTransferReceiptDerivedLotKey(302L, "gift", 1002L,
                        502L, "accepted"),
                new InvTransferReceiptDerivedLotKey(302L, "gift", 1002L,
                        502L, "damaged"),
                new InvTransferReceiptDerivedLotKey(302L, "product",
                        1001L, 501L, "accepted"),
                new InvTransferReceiptDerivedLotKey(302L, "product",
                        1001L, 501L, "damaged"));
        orderVerifier.verify(fixture.lockMapper())
                .selectDerivedLotsForUpdate(lotKeys);
        orderVerifier.verify(fixture.lockMapper())
                .selectTargetBalancesForUpdate(302L, List.of(
                        new InvTransferReceiptBalanceKey(701L, 901L),
                        new InvTransferReceiptBalanceKey(702L, 902L),
                        new InvTransferReceiptBalanceKey(703L, 901L),
                        new InvTransferReceiptBalanceKey(704L, 902L)));
        orderVerifier.verify(fixture.lockMapper())
                .selectShipmentSerialsForUpdate(91L);
        orderVerifier.verify(fixture.planningMapper())
                .selectAllocations(91L);
        verifyNoMoreInteractions(fixture.shipmentMapper(),
                fixture.orderMapper(), fixture.lockMapper(),
                fixture.planningMapper());
    }

    @Test
    @DisplayName("规划分配与锁定分配不完全一致时立即拒绝")
    void shouldRejectPlanningFactsOutsideLockedBoundary()
    {
        Fixture fixture = fixture();
        InvTransferShipment shipment = shipment();
        InvTransferOrder order = order();
        InvTransferReceiptLockedDetail detail = detail(71L, 401L,
                "product", 1001L, "2");
        InvTransferReceiptLockedAllocation allocation = allocation(81L,
                71L, 401L, 501L, 801L);
        stubSingleBoundary(fixture, shipment, order, detail, allocation);
        when(fixture.planningMapper().selectAllocations(91L))
                .thenReturn(List.of());

        InvTransferShipmentReceiptLockGateway.LockedHeader header =
                fixture.gateway().lockHeader(91L);
        assertThatThrownBy(() -> fixture.gateway().lockAndCompose(header,
                302L, receiptRequest())).isInstanceOf(ServiceException.class)
                .hasMessageContaining("规划与锁定来源分配不一致");
    }

    @Test
    @DisplayName("任一发货明细缺少来源分配时立即拒绝")
    void shouldRejectShipmentDetailWithoutAllocation()
    {
        Fixture fixture = fixture();
        InvTransferShipment shipment = shipment();
        InvTransferOrder order = order();
        when(fixture.shipmentMapper().selectByIdForUpdate(91L))
                .thenReturn(shipment);
        when(fixture.orderMapper()
                .selectInvTransferOrderByIdForUpdate(900L))
                .thenReturn(order);
        when(fixture.lockMapper().selectTransferDetailsForUpdate(900L))
                .thenReturn(List.of(
                        transferDetail(401L, "product", 1001L, "2"),
                        transferDetail(402L, "gift", 1002L, "1")));
        when(fixture.lockMapper().selectShipmentDetailsForUpdate(91L))
                .thenReturn(List.of(
                        detail(71L, 401L, "product", 1001L, "2"),
                        detail(72L, 402L, "gift", 1002L, "1")));
        when(fixture.lockMapper().selectAllocationsForUpdate(91L))
                .thenReturn(List.of(allocation(81L, 71L, 401L, 501L,
                        801L)));

        InvTransferShipmentReceiptLockGateway.LockedHeader header =
                fixture.gateway().lockHeader(91L);
        assertThatThrownBy(() -> fixture.gateway().lockAndCompose(header,
                302L, receiptRequest())).isInstanceOf(ServiceException.class)
                .hasMessageContaining("明细与来源分配锁定事实不完整");
    }

    @Test
    @DisplayName("专属退回复用固定锁序且只派生残损隔离目标维度")
    void shouldLockReturnReceiptWithDamagedTargetOnly()
    {
        Fixture fixture = fixture();
        InvTransferShipment shipment = shipment();
        InvTransferOrder order = order();
        InvTransferReceiptLockedDetail detail = detail(71L, 401L,
                "product", 1001L, "2");
        InvTransferReceiptLockedAllocation allocation = allocation(81L,
                71L, 401L, 501L, 801L);
        stubSingleBoundary(fixture, shipment, order, detail, allocation);
        when(fixture.planningMapper().selectAllocations(91L))
                .thenReturn(List.of(planningFact(81L, 71L, 401L,
                        "product", 1001L, 501L, 801L, "2", "10")));

        InvTransferShipmentReceiptLockGateway.LockedHeader header =
                fixture.gateway().lockHeader(91L);
        var result = fixture.gateway().lockAndComposeReturnReceipt(header,
                302L, returnReceiptRequest());

        assertThat(result.lockedFacts().allocations()).containsOnlyKeys(81L);
        assertThat(result.returnPlan()).isNotNull();
        verify(fixture.lockMapper()).selectDerivedLotsForUpdate(List.of(
                new InvTransferReceiptDerivedLotKey(302L, "product", 1001L,
                        501L, "damaged")));
    }

    private static void stubSingleBoundary(Fixture fixture,
            InvTransferShipment shipment, InvTransferOrder order,
            InvTransferReceiptLockedDetail detail,
            InvTransferReceiptLockedAllocation allocation)
    {
        when(fixture.shipmentMapper().selectByIdForUpdate(91L))
                .thenReturn(shipment);
        when(fixture.orderMapper()
                .selectInvTransferOrderByIdForUpdate(900L))
                .thenReturn(order);
        when(fixture.lockMapper().selectTransferDetailsForUpdate(900L))
                .thenReturn(List.of(transferDetail(401L,
                        detail.getItemType(), detail.getItemId(),
                        detail.getShippedQuantity().toPlainString())));
        when(fixture.lockMapper().selectShipmentDetailsForUpdate(91L))
                .thenReturn(List.of(detail));
        when(fixture.lockMapper().selectAllocationsForUpdate(91L))
                .thenReturn(List.of(allocation));
        when(fixture.lockMapper()
                .selectTargetWarehouseModeForUpdate(302L))
                .thenReturn(mode());
        when(fixture.lockMapper().selectSourceLotsForUpdate(anyList()))
                .thenReturn(List.of(sourceLot(501L)));
        when(fixture.lockMapper().selectLocationsForUpdate(anyList(),
                eq(302L))).thenReturn(List.of(
                        location(801L, 301L, "storage"),
                        location(901L, 302L, "storage"),
                        location(902L, 302L, "quarantine")));
        when(fixture.lockMapper().selectDerivedLotsForUpdate(anyList()))
                .thenReturn(List.of());
        when(fixture.lockMapper().selectShipmentSerialsForUpdate(91L))
                .thenReturn(List.of());
    }

    private static Fixture fixture()
    {
        InvTransferShipmentMapper shipmentMapper = mock(
                InvTransferShipmentMapper.class);
        InvTransferOrderMapper orderMapper = mock(
                InvTransferOrderMapper.class);
        InvTransferShipmentReceiptPersistenceMapper lockMapper = mock(
                InvTransferShipmentReceiptPersistenceMapper.class);
        InvTransferShipmentReceiptPlanningMapper planningMapper = mock(
                InvTransferShipmentReceiptPlanningMapper.class);
        return new Fixture(new InvTransferShipmentReceiptLockGateway(
                shipmentMapper, orderMapper, lockMapper, planningMapper),
                shipmentMapper, orderMapper, lockMapper, planningMapper);
    }

    private static InvTransferShipmentReceiptCreateRequest receiptRequest()
    {
        InvTransferShipmentReceiptAllocationRequest first =
                receiptAllocation(81L, "1", "1");
        InvTransferShipmentReceiptAllocationRequest second =
                receiptAllocation(82L, "0.5", "0.5");
        InvTransferShipmentReceiptCreateRequest value =
                new InvTransferShipmentReceiptCreateRequest();
        value.setAllocations(List.of(first, second));
        return value;
    }

    private static InvTransferReceiptDiscrepancyReturnReceiptCreateRequest
            returnReceiptRequest()
    {
        var allocation =
                new InvTransferReceiptDiscrepancyReturnReceiptAllocationRequest();
        allocation.setShipmentAllocationId(81L);
        allocation.setQuarantineLocationId(902L);
        allocation.setReturnedQuantity(new BigDecimal("2"));
        allocation.setShortageQuantity(BigDecimal.ZERO);
        var value =
                new InvTransferReceiptDiscrepancyReturnReceiptCreateRequest();
        value.setAllocations(List.of(allocation));
        return value;
    }

    private static InvTransferShipmentReceiptAllocationRequest
            receiptAllocation(Long allocationId, String accepted,
                    String damaged)
    {
        InvTransferShipmentReceiptAllocationRequest value =
                new InvTransferShipmentReceiptAllocationRequest();
        value.setShipmentAllocationId(allocationId);
        value.setAcceptedQuantity(new BigDecimal(accepted));
        value.setAcceptedLocationId(901L);
        value.setDamagedQuantity(new BigDecimal(damaged));
        value.setQuarantineLocationId(902L);
        value.setShortageQuantity(BigDecimal.ZERO);
        return value;
    }

    private static InvTransferShipment shipment()
    {
        InvTransferShipment value = new InvTransferShipment();
        value.setShipmentId(91L);
        value.setTransferId(900L);
        value.setInventoryWriteVersion(
                InvTransferShipmentWriteVersions.V2_DETAIL);
        value.setStatus(InvStatusConstants.PENDING_RECEIVE);
        value.setPlanVersion("b".repeat(64));
        value.setSealedRevisionId(11L);
        value.setReconcileBatch("source-reconcile-1");
        value.setWarehouseId(301L);
        value.setSourceLocationDeptId(301L);
        return value;
    }

    private static InvTransferOrder order()
    {
        InvTransferOrder value = new InvTransferOrder();
        value.setTransferId(900L);
        value.setVersion(4L);
        value.setStatus(InvStatusConstants.DELIVERED);
        value.setToDeptId(302L);
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

    private static InvTransferReceiptLockedDetail detail(Long detailId,
            Long transferDetailId, String itemType, Long itemId,
            String quantity)
    {
        InvTransferReceiptLockedDetail value =
                new InvTransferReceiptLockedDetail();
        value.setShipmentDetailId(detailId);
        value.setShipmentId(91L);
        value.setTransferId(900L);
        value.setTransferDetailId(transferDetailId);
        value.setItemType(itemType);
        value.setItemId(itemId);
        value.setProductId(itemId);
        value.setShippedQuantity(new BigDecimal(quantity));
        value.setReceivedQuantity(BigDecimal.ZERO);
        return value;
    }

    private static InvTransferReceiptLockedTransferDetail transferDetail(
            Long detailId, String itemType, Long itemId, String quantity)
    {
        InvTransferReceiptLockedTransferDetail value =
                new InvTransferReceiptLockedTransferDetail();
        value.setDetailId(detailId);
        value.setTransferId(900L);
        value.setItemType(itemType);
        value.setItemId(itemId);
        value.setProductId(itemId);
        value.setQuantity(new BigDecimal(quantity));
        value.setDeliveredQuantity(new BigDecimal(quantity));
        value.setReceivedQuantity(BigDecimal.ZERO);
        return value;
    }

    private static InvTransferReceiptLockedAllocation allocation(Long id,
            Long detailId, Long transferDetailId, Long lotId,
            Long locationId)
    {
        InvTransferReceiptLockedAllocation value =
                new InvTransferReceiptLockedAllocation();
        value.setAllocationId(id);
        value.setShipmentId(91L);
        value.setShipmentDetailId(detailId);
        value.setTransferId(900L);
        value.setTransferDetailId(transferDetailId);
        value.setSourceBalanceId(600L + id);
        value.setSourceLotId(lotId);
        value.setSourceLocationId(locationId);
        value.setTrackingPolicy("lot");
        value.setAllocatedQuantity(id == 81L ? new BigDecimal("2")
                : BigDecimal.ONE);
        value.setBalanceVersionAfter(3L);
        value.setCostPrice(id == 81L ? new BigDecimal("10")
                : new BigDecimal("5"));
        value.setTotalCost(id == 81L ? new BigDecimal("20")
                : new BigDecimal("5"));
        value.setAcceptedReceivedQuantity(BigDecimal.ZERO);
        value.setDamagedReceivedQuantity(BigDecimal.ZERO);
        value.setShortageReportedQuantity(BigDecimal.ZERO);
        value.setReceiptVersion(0L);
        return value;
    }

    private static InvTransferReceiptPlanningAllocationFact planningFact(
            Long id, Long detailId, Long transferDetailId, String itemType,
            Long itemId, Long lotId, Long locationId, String quantity,
            String cost)
    {
        InvTransferReceiptPlanningAllocationFact value =
                new InvTransferReceiptPlanningAllocationFact();
        value.setAllocationId(id);
        value.setShipmentId(91L);
        value.setShipmentDetailId(detailId);
        value.setTransferId(900L);
        value.setTransferDetailId(transferDetailId);
        value.setItemType(itemType);
        value.setItemId(itemId);
        value.setProductId(itemId);
        value.setItemCode("ITEM-" + itemId);
        value.setItemName("物料" + itemId);
        value.setUnit("件");
        value.setTrackingPolicy("lot");
        value.setAllocatedQuantity(new BigDecimal(quantity));
        value.setBalanceVersionAfter(3L);
        value.setCostPrice(new BigDecimal(cost));
        value.setTotalCost(new BigDecimal(quantity).multiply(
                new BigDecimal(cost)).setScale(6));
        value.setSourceWarehouseId(301L);
        value.setSourceBalanceId(600L + id);
        value.setSourceLotId(lotId);
        value.setSourceLocationId(locationId);
        value.setSourceLotNo("LOT-" + lotId);
        value.setSourceLocationCode("LOC-" + locationId);
        value.setShipmentDetailShippedQuantity(new BigDecimal(quantity));
        value.setShipmentDetailReceivedQuantity(BigDecimal.ZERO);
        value.setAcceptedReceivedQuantity(BigDecimal.ZERO);
        value.setDamagedReceivedQuantity(BigDecimal.ZERO);
        value.setShortageReportedQuantity(BigDecimal.ZERO);
        value.setReceiptVersion(0L);
        return value;
    }

    private static InvTransferReceiptTargetLot sourceLot(Long id)
    {
        InvTransferReceiptTargetLot value =
                new InvTransferReceiptTargetLot();
        value.setLotId(id);
        return value;
    }

    private static InvTransferReceiptTargetLot derivedLot(Long id,
            String itemType, Long itemId, Long sourceLotId,
            String disposition)
    {
        InvTransferReceiptTargetLot value = sourceLot(id);
        value.setWarehouseId(302L);
        value.setItemType(itemType);
        value.setItemId(itemId);
        value.setSourceLotId(sourceLotId);
        value.setReceiptDisposition(disposition);
        return value;
    }

    private static InvTransferReceiptLocationCandidate location(Long id,
            Long warehouseId, String type)
    {
        InvTransferReceiptLocationCandidate value =
                new InvTransferReceiptLocationCandidate();
        value.setLocationId(id);
        value.setWarehouseId(warehouseId);
        value.setLocationCode("LOC-" + id);
        value.setLocationName("库位" + id);
        value.setLocationType(type);
        value.setSortOrder(id.intValue());
        value.setStatus("0");
        value.setVirtualFlag("0");
        return value;
    }

    private record Fixture(
            InvTransferShipmentReceiptLockGateway gateway,
            InvTransferShipmentMapper shipmentMapper,
            InvTransferOrderMapper orderMapper,
            InvTransferShipmentReceiptPersistenceMapper lockMapper,
            InvTransferShipmentReceiptPlanningMapper planningMapper)
    {
    }
}
