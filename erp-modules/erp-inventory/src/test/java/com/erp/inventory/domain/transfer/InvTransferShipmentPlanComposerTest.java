package com.erp.inventory.domain.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.inventory.domain.InvTransferDetail;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.vo.InvTransferRevisionVo;

@DisplayName("调拨发货规划共享 Composer")
class InvTransferShipmentPlanComposerTest
{
    @Test
    @DisplayName("同一锁定事实稳定生成可执行规划且余额版本进入指纹")
    void shouldComposeStableAuthoritativePlan()
    {
        InvShipmentAllocationCandidate candidate = candidate(3L);

        InvTransferShipmentPlanComposer.Composition first =
                InvTransferShipmentPlanComposer.compose(order(), revision(),
                        mode(), List.of(detail()), List.of(policy()),
                        List.of(candidate));
        InvTransferShipmentPlanComposer.Composition repeated =
                InvTransferShipmentPlanComposer.compose(order(), revision(),
                        mode(), List.of(detail()), List.of(policy()),
                        List.of(candidate));
        candidate.setVersion(4L);
        InvTransferShipmentPlanComposer.Composition changed =
                InvTransferShipmentPlanComposer.compose(order(), revision(),
                        mode(), List.of(detail()), List.of(policy()),
                        List.of(candidate));

        assertThat(first.canCreateShipment()).isTrue();
        assertThat(first.lines()).hasSize(1);
        assertThat(first.lines().get(0).plan().allocations())
                .extracting(InvTransferShipmentAllocationPolicy.Allocation::balanceId)
                .containsExactly(500L);
        assertThat(first.planVersion()).isEqualTo(repeated.planVersion());
        assertThat(changed.planVersion()).isNotEqualTo(first.planVersion());
    }

    @Test
    @DisplayName("非权威模式只返回阻断且不采用候选余额")
    void shouldFailClosedBeforeAuthorityCutover()
    {
        InvWarehouseStockMode mode = mode();
        mode.setReadMode("shadow");

        InvTransferShipmentPlanComposer.Composition result =
                InvTransferShipmentPlanComposer.compose(order(), revision(),
                        mode, List.of(detail()), List.of(policy()),
                        List.of(candidate(1L)));

        assertThat(result.canCreateShipment()).isFalse();
        assertThat(result.blockingReasons())
                .containsExactly("来源仓库尚未切换 detail 明细库存读取");
        assertThat(result.lines().get(0).plan().allocations()).isEmpty();
    }

    @Test
    @DisplayName("同一物料多条明细共享候选池且不超出锁定余额")
    void shouldConsumeCandidateCapacityAcrossTransferDetails()
    {
        InvShipmentAllocationCandidate candidate = candidate(3L);
        candidate.setAvailableQuantity(new BigDecimal("7"));
        InvTransferDetail second = detail();
        second.setDetailId(12L);
        second.setQuantity(new BigDecimal("3"));

        InvTransferShipmentPlanComposer.Composition result =
                InvTransferShipmentPlanComposer.compose(order(), revision(),
                        mode(), List.of(detail(), second), List.of(policy()),
                        List.of(candidate));

        assertThat(result.lines().get(0).plan().suggestedShipmentQuantity())
                .isEqualByComparingTo("5");
        assertThat(result.lines().get(1).plan().suggestedShipmentQuantity())
                .isEqualByComparingTo("2");
        assertThat(result.lines().stream()
                .flatMap(line -> line.plan().allocations().stream())
                .map(InvTransferShipmentAllocationPolicy.Allocation::suggestedQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("7");
        assertThat(candidate.getAvailableQuantity()).isEqualByComparingTo("7");
    }

    private static InvTransferOrder order()
    {
        InvTransferOrder value = new InvTransferOrder();
        value.setTransferId(900L);
        value.setVersion(2L);
        value.setStatus("approved");
        value.setFromWarehouseId(301L);
        return value;
    }

    private static InvTransferRevisionVo revision()
    {
        InvTransferRevisionVo value = new InvTransferRevisionVo();
        value.setRevisionId(700L);
        value.setRevisionNo(2);
        value.setStatus("APPROVED");
        value.setSnapshotHash("a".repeat(64));
        return value;
    }

    private static InvTransferDetail detail()
    {
        InvTransferDetail value = new InvTransferDetail();
        value.setDetailId(11L);
        value.setTransferId(900L);
        value.setItemType("product");
        value.setItemId(1001L);
        value.setProductId(1001L);
        value.setQuantity(new BigDecimal("5"));
        value.setDeliveredQuantity(BigDecimal.ZERO);
        return value;
    }

    private static InvItemFulfillmentPolicy policy()
    {
        InvItemFulfillmentPolicy value = new InvItemFulfillmentPolicy();
        value.setPolicyId(1L);
        value.setItemType("product");
        value.setItemId(1001L);
        value.setAllocationPolicy("FEFO");
        value.setTrackingPolicy("lot");
        value.setStatus("0");
        value.setVersion(1L);
        return value;
    }

    private static InvWarehouseStockMode mode()
    {
        InvWarehouseStockMode value = new InvWarehouseStockMode();
        value.setWarehouseId(301L);
        value.setWriteMode("dual");
        value.setReadMode("detail");
        value.setReconcileStatus("passed");
        value.setLastReconcileBatch("reconcile-01");
        return value;
    }

    private static InvShipmentAllocationCandidate candidate(Long version)
    {
        InvShipmentAllocationCandidate value =
                new InvShipmentAllocationCandidate();
        value.setBalanceId(500L);
        value.setItemType("product");
        value.setItemId(1001L);
        value.setWarehouseId(301L);
        value.setLotId(600L);
        value.setLotNo("LOT-600");
        value.setExpiryDate(date("2027-01-01"));
        value.setReceivedAt(date("2026-07-01"));
        value.setLocationId(700L);
        value.setLocationCode("A-01");
        value.setAvailableQuantity(new BigDecimal("5"));
        value.setVersion(version);
        return value;
    }

    private static Date date(String value)
    {
        return Date.from(LocalDate.parse(value).atStartOfDay(
                ZoneId.of("Asia/Shanghai")).toInstant());
    }
}
