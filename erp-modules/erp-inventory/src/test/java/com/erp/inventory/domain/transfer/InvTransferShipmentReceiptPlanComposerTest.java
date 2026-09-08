package com.erp.inventory.domain.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.inventory.constant.InvTransferShipmentWriteVersions;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.InvTransferShipment;

@DisplayName("V2调拨收货规划共享 Composer")
class InvTransferShipmentReceiptPlanComposerTest
{
    @Test
    @DisplayName("批次分配和两类目标库位形成稳定权威边界")
    void shouldComposeStableLotReceiptBoundary()
    {
        InvTransferReceiptPlanningAllocationFact allocation = allocation(
                "lot", "2", "0");

        InvTransferShipmentReceiptPlanComposer.Composition first = compose(
                List.of(allocation), List.of(), locations());
        InvTransferShipmentReceiptPlanComposer.Composition second = compose(
                List.of(allocation), List.of(), locations());

        assertThat(first.canCreateReceipt()).isTrue();
        assertThat(first.receiptPlanVersion()).matches("[a-f0-9]{64}")
                .isEqualTo(second.receiptPlanVersion());
        assertThat(first.lines()).hasSize(1);
        assertThat(first.lines().get(0).recommendationStatus())
                .isEqualTo("ready");
        assertThat(first.lines().get(0).remainingQuantity())
                .isEqualByComparingTo("2");
        assertThat(first.lines().get(0).suggestedAcceptedQuantity())
                .isEqualByComparingTo("2");
    }

    @Test
    @DisplayName("目标库位事实变化必须改变收货规划版本")
    void shouldFingerprintTargetLocationFacts()
    {
        String first = compose(List.of(allocation("lot", "2", "0")),
                List.of(), locations()).receiptPlanVersion();
        List<InvTransferReceiptLocationCandidate> changed = locations();
        changed.get(0).setLocationCode("STORAGE-CHANGED");

        String second = compose(List.of(allocation("lot", "2", "0")),
                List.of(), changed).receiptPlanVersion();

        assertThat(second).isNotEqualTo(first);
    }

    @Test
    @DisplayName("序列号必须逐件保持 shipped 状态和来源位置")
    void shouldRequireEverySerialAtShippedSourcePosition()
    {
        InvTransferReceiptPlanningAllocationFact allocation = allocation(
                "serial", "2", "0");
        InvTransferReceiptPlanningSerialFact first = serial(51L);
        InvTransferReceiptPlanningSerialFact second = serial(52L);

        InvTransferShipmentReceiptPlanComposer.Composition ready = compose(
                List.of(allocation), List.of(first, second), locations());
        second.setCurrentStatus("available");
        InvTransferShipmentReceiptPlanComposer.Composition blocked = compose(
                List.of(allocation), List.of(first, second), locations());

        assertThat(ready.canCreateReceipt()).isTrue();
        assertThat(ready.lines().get(0).serials()).hasSize(2);
        assertThat(blocked.canCreateReceipt()).isFalse();
        assertThat(blocked.lines().get(0).blockers())
                .contains("序列号发货审计或当前位置不可核验");
        assertThat(blocked.receiptPlanVersion())
                .isNotEqualTo(ready.receiptPlanVersion());
    }

    @Test
    @DisplayName("逐来源累计支持同一发货分配多次部分收货")
    void shouldPlanFromAuthoritativeAllocationProgress()
    {
        InvTransferReceiptPlanningAllocationFact allocation = allocation(
                "lot", "2", "1");
        allocation.setAcceptedReceivedQuantity(new BigDecimal("1"));
        allocation.setReceiptVersion(1L);

        InvTransferShipmentReceiptPlanComposer.Composition result = compose(
                List.of(allocation), List.of(), locations());

        assertThat(result.canCreateReceipt()).isTrue();
        assertThat(result.lines().get(0).acceptedQuantity())
                .isEqualByComparingTo("1");
        assertThat(result.lines().get(0).remainingQuantity())
                .isEqualByComparingTo("1");
        assertThat(result.lines().get(0).suggestedAcceptedQuantity())
                .isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("发货批次进入部分收货后仍可规划下一次收货")
    void shouldContinuePlanningPartiallyReceivedShipment()
    {
        InvTransferReceiptPlanningAllocationFact allocation = allocation(
                "lot", "2", "1");
        allocation.setAcceptedReceivedQuantity(BigDecimal.ONE);
        allocation.setReceiptVersion(1L);
        InvTransferShipment shipment = shipment();
        shipment.setStatus("partial_received");
        InvTransferOrder order = order();
        order.setStatus("partial_received");

        InvTransferShipmentReceiptPlanComposer.Composition result =
                InvTransferShipmentReceiptPlanComposer.compose(shipment,
                        order, mode(), List.of(allocation), List.of(),
                        locations());

        assertThat(result.canCreateReceipt()).isTrue();
        assertThat(result.lines()).singleElement().satisfies(line ->
                assertThat(line.remainingQuantity())
                        .isEqualByComparingTo("1"));
    }

    @Test
    @DisplayName("发货明细累计合格与逐来源累计不一致必须阻断")
    void shouldBlockMismatchedAcceptedProgress()
    {
        InvTransferShipmentReceiptPlanComposer.Composition result = compose(
                List.of(allocation("lot", "2", "1")), List.of(),
                locations());

        assertThat(result.canCreateReceipt()).isFalse();
        assertThat(result.lines().get(0).blockers())
                .contains("发货明细累计合格数量与来源分配不守恒");
    }

    @Test
    @DisplayName("逐来源剩余为零时分配标记为完成且不再建议数量")
    void shouldMarkFullyClassifiedAllocationComplete()
    {
        InvTransferReceiptPlanningAllocationFact allocation = allocation(
                "lot", "2", "2");
        allocation.setAcceptedReceivedQuantity(new BigDecimal("2"));
        allocation.setReceiptVersion(1L);

        InvTransferShipmentReceiptPlanComposer.Composition result = compose(
                List.of(allocation), List.of(), locations());

        assertThat(result.lines().get(0).recommendationStatus())
                .isEqualTo("complete");
        assertThat(result.lines().get(0).remainingQuantity())
                .isEqualByComparingTo("0");
        assertThat(result.lines().get(0).suggestedAcceptedQuantity())
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("来源分配合计必须与发货明细已发数量精确守恒")
    void shouldBlockAllocationShipmentMismatch()
    {
        InvTransferReceiptPlanningAllocationFact allocation = allocation(
                "lot", "1", "0");
        allocation.setShipmentDetailShippedQuantity(
                new BigDecimal("2.0000"));

        InvTransferShipmentReceiptPlanComposer.Composition result = compose(
                List.of(allocation), List.of(), locations());

        assertThat(result.canCreateReceipt()).isFalse();
        assertThat(result.lines().get(0).blockers())
                .contains("发货明细分配数量与已发数量不守恒");
    }

    @Test
    @DisplayName("目标仓模式和隔离库位缺失均为全局阻断")
    void shouldFailClosedWithoutTargetAuthorityAndQuarantine()
    {
        InvWarehouseStockMode mode = mode();
        mode.setReadMode("shadow");

        InvTransferShipmentReceiptPlanComposer.Composition result =
                InvTransferShipmentReceiptPlanComposer.compose(shipment(),
                        order(), mode,
                        List.of(allocation("lot", "2", "0")), List.of(),
                        List.of(location(901L, "storage")));

        assertThat(result.canCreateReceipt()).isFalse();
        assertThat(result.blockingReasons()).contains(
                "目标仓库尚未切换 detail 明细库存读取",
                "目标仓库缺少启用的残损隔离库位");
    }

    @Test
    @DisplayName("目标仓库存模式行必须属于调拨目标仓")
    void shouldBlockTargetModeOwnedByAnotherWarehouse()
    {
        InvWarehouseStockMode mode = mode();
        mode.setWarehouseId(999L);

        InvTransferShipmentReceiptPlanComposer.Composition result =
                InvTransferShipmentReceiptPlanComposer.compose(shipment(),
                        order(), mode,
                        List.of(allocation("lot", "2", "0")), List.of(),
                        locations());

        assertThat(result.canCreateReceipt()).isFalse();
        assertThat(result.blockingReasons())
                .contains("目标仓库存模式行归属不可核验");
    }

    @Test
    @DisplayName("来源分配仓库必须匹配V2发货快照")
    void shouldBlockAllocationFromAnotherSourceWarehouse()
    {
        InvTransferReceiptPlanningAllocationFact allocation = allocation(
                "lot", "2", "0");
        allocation.setSourceWarehouseId(999L);

        InvTransferShipmentReceiptPlanComposer.Composition result = compose(
                List.of(allocation), List.of(), locations());

        assertThat(result.canCreateReceipt()).isFalse();
        assertThat(result.lines().get(0).blockers())
                .contains("来源分配仓库与V2发货快照不一致");
    }

    private static InvTransferShipmentReceiptPlanComposer.Composition compose(
            List<InvTransferReceiptPlanningAllocationFact> allocations,
            List<InvTransferReceiptPlanningSerialFact> serials,
            List<InvTransferReceiptLocationCandidate> locations)
    {
        return InvTransferShipmentReceiptPlanComposer.compose(shipment(),
                order(), mode(), allocations, serials, locations);
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
        value.setFromWarehouseId(301L);
        value.setToDeptId(202L);
        value.setToWarehouseId(302L);
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

    private static InvTransferReceiptPlanningAllocationFact allocation(
            String tracking, String quantity, String received)
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
        value.setTrackingPolicy(tracking);
        value.setAllocatedQuantity(new BigDecimal(quantity));
        value.setBalanceVersionAfter(5L);
        value.setCostPrice(new BigDecimal("5.100000"));
        value.setTotalCost(new BigDecimal(quantity)
                .multiply(new BigDecimal("5.100000")));
        value.setSourceWarehouseId(301L);
        value.setSourceBalanceId(401L);
        value.setSourceLotId(501L);
        value.setSourceLocationId(601L);
        value.setSourceLotNo("LOT-202608-01");
        value.setQcStatus("passed");
        value.setLotStatus("active");
        value.setSourceLocationCode("A-01-01");
        value.setShipmentDetailShippedQuantity(new BigDecimal(quantity));
        value.setShipmentDetailReceivedQuantity(new BigDecimal(received));
        value.setAcceptedReceivedQuantity(BigDecimal.ZERO);
        value.setDamagedReceivedQuantity(BigDecimal.ZERO);
        value.setShortageReportedQuantity(BigDecimal.ZERO);
        value.setReceiptVersion(0L);
        return value;
    }

    private static InvTransferReceiptPlanningSerialFact serial(Long id)
    {
        InvTransferReceiptPlanningSerialFact value =
                new InvTransferReceiptPlanningSerialFact();
        value.setShipmentSerialId(id + 100L);
        value.setAllocationId(81L);
        value.setShipmentId(91L);
        value.setSerialId(id);
        value.setSerialNoSnapshot("DEVICE-" + id);
        value.setCurrentSerialNo("DEVICE-" + id);
        value.setItemType("product");
        value.setItemId(1001L);
        value.setSourceBalanceId(401L);
        value.setSourceLotId(501L);
        value.setSourceLocationId(601L);
        value.setStatusAfter("shipped");
        value.setCurrentWarehouseId(301L);
        value.setCurrentBalanceId(401L);
        value.setCurrentLotId(501L);
        value.setCurrentLocationId(601L);
        value.setCurrentStatus("shipped");
        return value;
    }

    private static List<InvTransferReceiptLocationCandidate> locations()
    {
        return new java.util.ArrayList<>(List.of(
                location(901L, "storage"),
                location(902L, "quarantine")));
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
