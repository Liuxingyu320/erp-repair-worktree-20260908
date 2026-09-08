package com.erp.inventory.domain.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.InvTransferDetail;

@DisplayName("调拨发货批次库位纯分配策略")
class InvTransferShipmentAllocationPolicyTest
{
    @Test
    @DisplayName("FEFO 按有效期和入库时间分配并允许跨批次")
    void shouldAllocateByFefo()
    {
        InvTransferShipmentAllocationPolicy.LinePlan plan = plan(
                policy("FEFO", "lot"), List.of(
                        candidate(2L, "2026-10-01", "2026-07-01", "10"),
                        candidate(1L, "2026-09-01", "2026-07-10", "3")),
                "8", "0");

        assertThat(plan.recommendationStatus()).isEqualTo("ready");
        assertThat(plan.suggestedShipmentQuantity())
                .isEqualByComparingTo("8");
        assertThat(plan.allocations())
                .extracting(InvTransferShipmentAllocationPolicy.Allocation::balanceId)
                .containsExactly(1L, 2L);
        assertThat(plan.allocations())
                .extracting(InvTransferShipmentAllocationPolicy.Allocation::suggestedQuantity)
                .containsExactly(new BigDecimal("3"), new BigDecimal("5"));
    }

    @Test
    @DisplayName("FIFO 忽略余额主键并按入库时间排序")
    void shouldAllocateByFifoReceivedTime()
    {
        InvShipmentAllocationCandidate later = candidate(1L, null,
                "2026-07-10", "10");
        InvShipmentAllocationCandidate earlier = candidate(2L, null,
                "2026-07-01", "10");

        InvTransferShipmentAllocationPolicy.LinePlan plan = plan(
                policy("FIFO", "lot"), List.of(later, earlier), "4", "0");

        assertThat(plan.allocations()).hasSize(1);
        assertThat(plan.allocations().get(0).balanceId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("库存不足时形成正数部分发货建议而不伪造余量")
    void shouldCreatePartialPositiveSuggestion()
    {
        InvTransferShipmentAllocationPolicy.LinePlan plan = plan(
                policy("FIFO", "lot"),
                List.of(candidate(1L, null, "2026-07-01", "3")),
                "8", "2");

        assertThat(plan.remainingQuantity()).isEqualByComparingTo("6");
        assertThat(plan.suggestedShipmentQuantity()).isEqualByComparingTo("3");
        assertThat(plan.recommendationStatus()).isEqualTo("ready");
    }

    @Test
    @DisplayName("序列号物料逐件选择且顺序稳定")
    void shouldAllocateExactSerialFacts()
    {
        InvShipmentAllocationCandidate candidate = candidate(1L, null,
                "2026-07-01", "2");
        candidate.setSerials(List.of(
                serial(2L, 1L, "SN-2"), serial(1L, 1L, "SN-1")));

        InvTransferShipmentAllocationPolicy.LinePlan plan = plan(
                policy("FIFO", "serial"), List.of(candidate), "2", "0");

        assertThat(plan.allocations().get(0).serials())
                .extracting(InvTransferShipmentAllocationPolicy.Serial::serialNo)
                .containsExactly("SN-1", "SN-2");
    }

    @Test
    @DisplayName("序列号数量与余额不一致时整行阻断")
    void shouldBlockSerialBalanceMismatch()
    {
        InvShipmentAllocationCandidate candidate = candidate(1L, null,
                "2026-07-01", "2");
        candidate.setSerials(List.of(serial(1L, 1L, "SN-1")));

        InvTransferShipmentAllocationPolicy.LinePlan plan = plan(
                policy("FIFO", "serial"), List.of(candidate), "2", "0");

        assertThat(plan.recommendationStatus()).isEqualTo("blocked");
        assertThat(plan.blockers()).containsExactly(
                "序列号可用事实与批次库位余额不一致");
    }

    @Test
    @DisplayName("缺少履约策略时不猜测 FEFO FIFO 或序列号")
    void shouldBlockMissingPolicy()
    {
        InvTransferShipmentAllocationPolicy.LinePlan plan = plan(null,
                List.of(candidate(1L, null, "2026-07-01", "2")),
                "2", "0");

        assertThat(plan.allocationPolicy()).isNull();
        assertThat(plan.trackingPolicy()).isNull();
        assertThat(plan.blockers()).containsExactly("物料履约策略未配置");
    }

    @Test
    @DisplayName("履约策略缺少版本事实时不形成建议")
    void shouldBlockPolicyWithoutVersion()
    {
        InvItemFulfillmentPolicy policy = policy("FIFO", "lot");
        policy.setVersion(null);

        InvTransferShipmentAllocationPolicy.LinePlan plan = plan(policy,
                List.of(candidate(1L, null, "2026-07-01", "2")),
                "2", "0");

        assertThat(plan.recommendationStatus()).isEqualTo("blocked");
        assertThat(plan.blockers()).containsExactly(
                "物料履约策略缺少可核验版本");
    }

    @Test
    @DisplayName("已全部发货的明细直接完成且不要求策略")
    void shouldMarkFullyShippedLineComplete()
    {
        InvTransferShipmentAllocationPolicy.LinePlan plan = plan(null,
                List.of(), "2", "2");

        assertThat(plan.recommendationStatus()).isEqualTo("complete");
        assertThat(plan.allocations()).isEmpty();
    }

    @Test
    @DisplayName("累计发货超过审批数量视为数据损坏")
    void shouldRejectBrokenQuantityConservation()
    {
        assertThatThrownBy(() -> plan(policy("FIFO", "lot"), List.of(),
                "2", "3"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("数量守恒");
    }

    private static InvTransferShipmentAllocationPolicy.LinePlan plan(
            InvItemFulfillmentPolicy policy,
            List<InvShipmentAllocationCandidate> candidates,
            String approved, String shipped)
    {
        InvTransferDetail detail = new InvTransferDetail();
        detail.setDetailId(11L);
        detail.setItemType("product");
        detail.setItemId(1001L);
        detail.setQuantity(new BigDecimal(approved));
        detail.setDeliveredQuantity(new BigDecimal(shipped));
        return InvTransferShipmentAllocationPolicy.plan(301L, detail,
                policy, candidates);
    }

    private static InvItemFulfillmentPolicy policy(String allocation,
            String tracking)
    {
        InvItemFulfillmentPolicy policy = new InvItemFulfillmentPolicy();
        policy.setPolicyId(1L);
        policy.setItemType("product");
        policy.setItemId(1001L);
        policy.setAllocationPolicy(allocation);
        policy.setTrackingPolicy(tracking);
        policy.setStatus("0");
        policy.setVersion(1L);
        return policy;
    }

    private static InvShipmentAllocationCandidate candidate(Long balanceId,
            String expiry, String received, String available)
    {
        InvShipmentAllocationCandidate candidate =
                new InvShipmentAllocationCandidate();
        candidate.setBalanceId(balanceId);
        candidate.setItemType("product");
        candidate.setItemId(1001L);
        candidate.setWarehouseId(301L);
        candidate.setLotId(100L + balanceId);
        candidate.setLotNo("LOT-" + balanceId);
        candidate.setExpiryDate(expiry == null ? null : date(expiry));
        candidate.setReceivedAt(date(received));
        candidate.setLocationId(200L + balanceId);
        candidate.setLocationCode("A-" + balanceId);
        candidate.setAvailableQuantity(new BigDecimal(available));
        candidate.setVersion(1L);
        candidate.setSerials(new ArrayList<>());
        return candidate;
    }

    private static InvShipmentSerialCandidate serial(Long serialId,
            Long balanceId, String serialNo)
    {
        InvShipmentSerialCandidate serial = new InvShipmentSerialCandidate();
        serial.setSerialId(serialId);
        serial.setBalanceId(balanceId);
        serial.setSerialNo(serialNo);
        serial.setSerialStatus("available");
        return serial;
    }

    private static Date date(String value)
    {
        return Date.from(LocalDate.parse(value).atStartOfDay(
                ZoneId.of("Asia/Shanghai")).toInstant());
    }
}
