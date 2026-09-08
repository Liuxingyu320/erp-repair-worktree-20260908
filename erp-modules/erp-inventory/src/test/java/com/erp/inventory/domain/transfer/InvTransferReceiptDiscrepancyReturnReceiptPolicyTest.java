package com.erp.inventory.domain.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.inventory.constant.InvStatusConstants;
import com.erp.inventory.constant.InvTransferShipmentWriteVersions;
import com.erp.inventory.constant.InvTransferTypes;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.InvTransferShipment;

@DisplayName("V2差异退回专属隔离收货规划纯策略")
class InvTransferReceiptDiscrepancyReturnReceiptPolicyTest
{
    @Test
    @DisplayName("整批残损退回只建议隔离实收且不要求正常库位")
    void shouldPlanExpectedDamagedReturnWithoutStorageLocation()
    {
        var plan = compose(allocation("lot"), List.of(),
                List.of(location(902L, "quarantine")), mode());

        assertThat(plan.canCreateReceipt()).isTrue();
        assertThat(plan.planVersion()).matches("[a-f0-9]{64}");
        assertThat(plan.quarantineLocations()).extracting(
                InvTransferReceiptLocationCandidate::getLocationId)
                .containsExactly(902L);
        assertThat(plan.lines()).hasSize(1);
        assertThat(plan.lines().get(0).returnedQuantity())
                .isEqualByComparingTo("0.0000");
        assertThat(plan.lines().get(0).remainingQuantity())
                .isEqualByComparingTo("5.0000");
        assertThat(plan.lines().get(0).suggestedReturnedQuantity())
                .isEqualByComparingTo("5.0000");
        assertThat(plan.lines().get(0).status()).isEqualTo("ready");
        assertThat(compose(allocation("lot"), List.of(),
                List.of(location(902L, "quarantine")), mode())
                        .planVersion())
                .isEqualTo(plan.planVersion());
    }

    @Test
    @DisplayName("序列号分批实收只规划仍在途的确定序列号")
    void shouldPlanRemainingSerialReturnAfterPartialReceipt()
    {
        var fact = allocation("serial");
        fact.setAllocatedQuantity(new BigDecimal("3.0000"));
        fact.setTotalCost(new BigDecimal("18.000000"));
        fact.setShipmentDetailShippedQuantity(new BigDecimal("3.0000"));
        fact.setShipmentDetailReceivedQuantity(new BigDecimal("1.0000"));
        fact.setTransferDetailRequestedQuantity(new BigDecimal("3.0000"));
        fact.setTransferDetailDeliveredQuantity(new BigDecimal("3.0000"));
        fact.setTransferDetailReceivedQuantity(new BigDecimal("1.0000"));
        fact.setDamagedReceivedQuantity(new BigDecimal("1.0000"));
        fact.setReceiptVersion(1L);
        var plan = compose(fact,
                List.of(serial(7002L, "SN-0002"),
                        serial(7003L, "SN-0003")),
                List.of(location(902L, "quarantine")), mode());

        assertThat(plan.canCreateReceipt()).isTrue();
        assertThat(plan.lines().get(0).returnedQuantity())
                .isEqualByComparingTo("1.0000");
        assertThat(plan.lines().get(0).remainingQuantity())
                .isEqualByComparingTo("2.0000");
        assertThat(plan.lines().get(0).serials())
                .extracting(InvTransferReceiptPlanningSerialFact::getSerialId)
                .containsExactly(7002L, 7003L);
    }

    @Test
    @DisplayName("非固定来源、合格收货和来源隔离漂移均失败关闭")
    void shouldBlockWrongSourceOrOrdinaryReceiptProgress()
    {
        var ordinary = allocation("lot");
        ordinary.setAllocationPolicy("FEFO");
        assertThat(compose(ordinary, List.of(), locations(), mode())
                .lines().get(0).blockers())
                .anyMatch(value -> value.contains("FIXED_RETURN"));

        var accepted = allocation("lot");
        accepted.setAcceptedReceivedQuantity(new BigDecimal("1.0000"));
        assertThat(compose(accepted, List.of(), locations(), mode())
                .lines().get(0).blockers())
                .anyMatch(value -> value.contains("逐次实收进度"));

        var drifted = allocation("lot");
        drifted.setSourceLocationType("storage");
        assertThat(compose(drifted, List.of(), locations(), mode())
                .lines().get(0).blockers())
                .anyMatch(value -> value.contains("残损隔离库存"));

        var wrongSourceOrder = order();
        wrongSourceOrder.setFromDeptId(203L);
        assertThat(InvTransferReceiptDiscrepancyReturnReceiptPolicy.compose(
                shipment(), wrongSourceOrder, mode(),
                List.of(allocation("lot")), List.of(), locations()).blockers())
                .contains("退回V2发货来源仓与子调拨快照不可核验");
    }

    @Test
    @DisplayName("库存模式、序列号和进度漂移进入指纹并阻断创建")
    void shouldFingerprintAndBlockAuthorityOrSerialDrift()
    {
        var base = allocation("serial");
        base.setAllocatedQuantity(new BigDecimal("1.0000"));
        base.setTotalCost(new BigDecimal("6.000000"));
        base.setShipmentDetailShippedQuantity(new BigDecimal("1.0000"));
        base.setTransferDetailRequestedQuantity(new BigDecimal("1.0000"));
        base.setTransferDetailDeliveredQuantity(new BigDecimal("1.0000"));
        var serial = serial(7001L, "SN-0001");
        var ready = compose(base, List.of(serial), locations(), mode());

        base.setExpiryDate(new Date(1_893_456_000_000L));
        var metadataDrift = compose(base, List.of(serial), locations(), mode());
        assertThat(metadataDrift.canCreateReceipt()).isTrue();
        assertThat(metadataDrift.planVersion())
                .isNotEqualTo(ready.planVersion());

        serial.setCurrentStatus("available");
        var drifted = compose(base, List.of(serial), locations(), mode());
        assertThat(drifted.canCreateReceipt()).isFalse();
        assertThat(drifted.planVersion()).isNotEqualTo(ready.planVersion());
        assertThat(drifted.lines().get(0).blockers())
                .anyMatch(value -> value.contains("序列号发货审计"));

        var nonAuthoritative = mode();
        nonAuthoritative.setReadMode("shadow");
        var blocked = compose(allocation("lot"), List.of(), locations(),
                nonAuthoritative);
        assertThat(blocked.canCreateReceipt()).isFalse();
        assertThat(blocked.blockers())
                .contains("退回目标仓库尚未切换detail明细库存读取");
    }

    @Test
    @DisplayName("FIXED_RETURN在普通V2收货规划中始终阻断")
    void shouldBlockFixedReturnInOrdinaryReceiptComposer()
    {
        var fixedReturn = allocation("lot");
        var blocked = InvTransferShipmentReceiptPlanComposer.compose(
                shipment(), order(), mode(), List.of(fixedReturn),
                List.of(), locations());
        assertThat(blocked.canCreateReceipt()).isFalse();
        assertThat(blocked.lines().get(0).blockers())
                .contains("差异退回发货必须使用专属收货边界");

        fixedReturn.setAllocationPolicy("FEFO");
        assertThat(InvTransferShipmentReceiptPlanComposer.compose(
                shipment(), order(), mode(), List.of(fixedReturn),
                List.of(), locations()).canCreateReceipt()).isTrue();
    }

    private static InvTransferReceiptDiscrepancyReturnReceiptPolicy.Plan
            compose(InvTransferReceiptPlanningAllocationFact fact,
                    List<InvTransferReceiptPlanningSerialFact> serials,
                    List<InvTransferReceiptLocationCandidate> locations,
                    InvWarehouseStockMode mode)
    {
        return InvTransferReceiptDiscrepancyReturnReceiptPolicy.compose(
                shipment(), order(), mode, List.of(fact), serials,
                locations);
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

    private static InvTransferReceiptPlanningAllocationFact allocation(
            String tracking)
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
        value.setTrackingPolicy(tracking);
        value.setAllocatedQuantity(new BigDecimal("5.0000"));
        value.setBalanceVersionAfter(9L);
        value.setCostPrice(new BigDecimal("6.000000"));
        value.setTotalCost(new BigDecimal("30.000000"));
        value.setSourceWarehouseId(202L);
        value.setSourceBalanceId(2302L);
        value.setSourceLotId(2303L);
        value.setSourceLocationId(2304L);
        value.setSourceLotNo("DMG-RETURN-01");
        value.setSupplierBatchNo("SUP-DMG-RETURN-01");
        value.setProductionDate(new Date(1_735_689_600_000L));
        value.setExpiryDate(new Date(1_767_225_600_000L));
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

    private static InvTransferReceiptPlanningSerialFact serial(Long id,
            String serialNo)
    {
        var value = new InvTransferReceiptPlanningSerialFact();
        value.setShipmentSerialId(id + 1000);
        value.setAllocationId(5003L);
        value.setShipmentId(3001L);
        value.setSerialId(id);
        value.setSerialNoSnapshot(serialNo);
        value.setCurrentSerialNo(serialNo);
        value.setItemType("product");
        value.setItemId(2401L);
        value.setSourceBalanceId(2302L);
        value.setSourceLotId(2303L);
        value.setSourceLocationId(2304L);
        value.setStatusAfter("shipped");
        value.setCurrentWarehouseId(202L);
        value.setCurrentBalanceId(2302L);
        value.setCurrentLotId(2303L);
        value.setCurrentLocationId(2304L);
        value.setCurrentStatus("shipped");
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
        var value = new InvTransferReceiptLocationCandidate();
        value.setLocationId(id);
        value.setWarehouseId(101L);
        value.setLocationCode(type.toUpperCase() + '-' + id);
        value.setLocationName(type + " location");
        value.setLocationType(type);
        value.setSortOrder(1);
        value.setStatus("0");
        value.setVirtualFlag("0");
        return value;
    }
}
