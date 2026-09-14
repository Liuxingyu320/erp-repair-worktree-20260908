package com.erp.inventory.service.impl;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.inventory.domain.InvOutboundRecord;
import com.erp.inventory.domain.InvSalesDetail;
import com.erp.inventory.domain.InvSalesReturnDetail;
import com.erp.inventory.domain.InvStockLog;
import com.erp.inventory.domain.InvSalesOrder;
import com.erp.inventory.mapper.InvOutboundRecordMapper;
import com.erp.inventory.mapper.InvStockLogMapper;
import com.erp.inventory.mapper.InvSalesOrderMapper;
import com.erp.inventory.mapper.InvSalesReturnDetailMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class InvSalesReturnCostEvidenceTest {
    private final InvSalesReturnServiceImpl service = new InvSalesReturnServiceImpl();
    private final InvOutboundRecordMapper outbound = mock(InvOutboundRecordMapper.class);
    private final InvSalesReturnDetailMapper returns = mock(InvSalesReturnDetailMapper.class);
    private final InvStockLogMapper logs = mock(InvStockLogMapper.class);
    private final InvSalesOrderMapper orders = mock(InvSalesOrderMapper.class);
    InvSalesReturnCostEvidenceTest() {
        ReflectionTestUtils.setField(service, "outboundRecordMapper", outbound);
        ReflectionTestUtils.setField(service, "salesReturnDetailMapper", returns);
        ReflectionTestUtils.setField(service, "stockLogMapper", logs);
        ReflectionTestUtils.setField(service, "salesOrderMapper", orders);
        InvSalesOrder order = new InvSalesOrder(); order.setOrderNo("SO-10");
        when(orders.selectInvSalesOrderById(10L)).thenReturn(order);
    }
    @Test void selectsExactLineCostWhenSameMaterialHasDifferentPricesAndCosts() {
        InvSalesDetail first = original(601L, "product", "100");
        InvSalesDetail second = original(602L, "product", "200");
        when(outbound.selectInvOutboundRecordBySalesDetailId(10L, 601L)).thenReturn(List.of(record("product", "20")));
        when(outbound.selectInvOutboundRecordBySalesDetailId(10L, 602L)).thenReturn(List.of(record("product", "60")));
        assertThat(cost(first, List.of(first, second))).isEqualByComparingTo("10");
        assertThat(cost(second, List.of(first, second))).isEqualByComparingTo("30");
        verifyNoInteractions(logs);
    }
    @Test void sameNumberGiftAndProductUseSeparateFrozenEvidence() {
        InvSalesDetail product = original(601L, "product", "100");
        InvSalesDetail gift = original(602L, "gift", "200");
        when(outbound.selectInvOutboundRecordBySalesDetailId(10L, 601L)).thenReturn(List.of(record("product", "20")));
        when(outbound.selectInvOutboundRecordBySalesDetailId(10L, 602L)).thenReturn(List.of(record("gift", "80")));
        assertThat(cost(product, List.of(product, gift))).isEqualByComparingTo("10");
        assertThat(cost(gift, List.of(product, gift))).isEqualByComparingTo("40");
    }
    @Test void uniqueLegacyLineUsesCompleteHistoricalCostLog() {
        InvSalesDetail old = original(601L, "product", "100"); old.setItemType(null); old.setItemId(null);
        when(logs.selectInvStockLogList(any())).thenReturn(List.of(log("sales", "2", "6")));
        assertThat(cost(old, List.of(old))).isEqualByComparingTo("6");
    }
    @Test void ambiguousOldLinesCannotBorrowWholeMaterialAverage() {
        InvSalesDetail first = original(601L, "product", "100");
        InvSalesDetail second = original(602L, "product", "200");
        assertThatThrownBy(() -> cost(first, List.of(first, second))).hasMessageContaining("成本证据不完整");
        verifyNoInteractions(logs);
    }
    @Test void missingOrPartialCostEvidenceFailsInsteadOfUsingCurrentInventory() {
        InvSalesDetail original = original(601L, "product", "100");
        assertThatThrownBy(() -> cost(original, List.of(original))).hasMessageContaining("成本证据不完整");
        when(logs.selectInvStockLogList(any())).thenReturn(List.of(log("sales", "1", "6")));
        assertThatThrownBy(() -> cost(original, List.of(original))).hasMessageContaining("成本证据不完整");
    }
    @Test void unrelatedLegacyOutboundIdCannotMasqueradeAsSalesOrderCost() {
        InvSalesDetail original = original(601L, "product", "100");
        InvStockLog unrelated = log("outbound", "2", "99"); unrelated.setBusinessNo("OTHER-10");
        when(logs.selectInvStockLogList(any())).thenReturn(List.of(unrelated));
        assertThatThrownBy(() -> cost(original, List.of(original))).hasMessageContaining("成本证据不完整");
    }
    @Test void laterLowCostDeliverySubtractsPreviouslyRestoredCost() {
        InvSalesDetail source = original(601L, "product", "100"); source.setDeliveredQuantity(new BigDecimal("10"));
        InvOutboundRecord first = record("product", "50"); first.setQuantity(new BigDecimal("5"));
        InvOutboundRecord later = record("product", "10"); later.setQuantity(new BigDecimal("5"));
        when(outbound.selectInvOutboundRecordBySalesDetailId(10L, 601L)).thenReturn(List.of(first, later));
        when(returns.selectReturnedCostFacts(10L, 601L, "product", 101L)).thenReturn(List.of(fact(601L, "5", "50")));
        assertThat(cost(source, List.of(source), "5")).isEqualByComparingTo("10");
    }
    @Test void wholeReturnUsesExactAmountAndPartialReturnsSettleLastCent() {
        InvSalesDetail source = original(601L, "product", "100"); source.setDeliveredQuantity(new BigDecimal("3"));
        InvOutboundRecord first = record("product", "1"); first.setQuantity(BigDecimal.ONE);
        InvOutboundRecord later = record("product", "4"); later.setQuantity(new BigDecimal("2"));
        when(outbound.selectInvOutboundRecordBySalesDetailId(10L, 601L)).thenReturn(List.of(first, later));
        assertThat(cost(source, List.of(source), "3")).isEqualByComparingTo("5");
        assertThat(cost(source, List.of(source), "1")).isEqualByComparingTo("1.67");
        when(returns.selectReturnedCostFacts(10L, 601L, "product", 101L)).thenReturn(List.of(fact(601L, "1", "1.67")));
        assertThat(cost(source, List.of(source), "2")).isEqualByComparingTo("3.33");
    }
    @Test void oldCompletedReturnsWithoutLineCostOrSourceAreNotInventedFromTotalLogs() {
        InvSalesDetail source = original(601L, "product", "100");
        when(outbound.selectInvOutboundRecordBySalesDetailId(10L, 601L)).thenReturn(List.of(record("product", "20")));
        when(returns.selectReturnedCostFacts(10L, 601L, "product", 101L)).thenReturn(List.of(fact(601L, "1", null)));
        assertThatThrownBy(() -> cost(source, List.of(source))).hasMessageContaining("历史退货明细成本证据不完整");
        when(returns.selectReturnedCostFacts(10L, 601L, "product", 101L)).thenReturn(List.of(fact(null, "1", "10")));
        assertThatThrownBy(() -> cost(source, List.of(source))).hasMessageContaining("历史退货明细成本证据不完整");
    }
    @Test void historicalExcessCostCannotCreateNegativeInventoryCost() {
        InvSalesDetail source = original(601L, "product", "100");
        when(outbound.selectInvOutboundRecordBySalesDetailId(10L, 601L)).thenReturn(List.of(record("product", "20")));
        when(returns.selectReturnedCostFacts(10L, 601L, "product", 101L)).thenReturn(List.of(fact(601L, "1", "30")));
        assertThatThrownBy(() -> cost(source, List.of(source))).hasMessageContaining("剩余可退数量或成本异常");
    }
    @Test void zeroCostIsKnownAndReturnedCostIsNotAcceptedOrExposedOverJson() throws Exception {
        InvSalesDetail source = original(601L, "product", "100");
        when(outbound.selectInvOutboundRecordBySalesDetailId(10L, 601L)).thenReturn(List.of(record("product", "0")));
        assertThat(cost(source, List.of(source), "2")).isEqualByComparingTo("0");
        com.fasterxml.jackson.databind.ObjectMapper json = new com.fasterxml.jackson.databind.ObjectMapper();
        assertThat(json.writeValueAsString(fact(601L, "1", "10"))).doesNotContain("returnedCostAmount");
        assertThat(json.readValue("{\"returnedCostAmount\":999}", InvSalesReturnDetail.class).getReturnedCostAmount()).isNull();
    }
    private static InvSalesReturnDetail fact(Long sourceId, String quantity, String amount) {
        InvSalesReturnDetail fact = new InvSalesReturnDetail(); fact.setSalesDetailId(sourceId);
        fact.setItemType("product"); fact.setItemId(101L); fact.setReturnedQuantity(new BigDecimal(quantity));
        fact.setReturnedCostAmount(amount == null ? null : new BigDecimal(amount)); return fact;
    }
    private BigDecimal cost(InvSalesDetail source, List<InvSalesDetail> all) {
        return cost(source, all, "1");
    }
    private BigDecimal cost(InvSalesDetail source, List<InvSalesDetail> all, String quantity) {
        InvSalesReturnDetail detail = new InvSalesReturnDetail();
        detail.setItemType(source.getItemType() == null ? "product" : source.getItemType());
        detail.setItemId(101L); detail.setProductId("gift".equals(detail.getItemType()) ? null : 101L);
        detail.setSalesDetailId(source.getDetailId());
        return ReflectionTestUtils.invokeMethod(service, "resolveReturnCostAmount", 10L, detail, source, all, new BigDecimal(quantity));
    }
    private static InvSalesDetail original(Long id, String type, String price) {
        InvSalesDetail row = new InvSalesDetail(); row.setDetailId(id); row.setItemType(type); row.setItemId(101L);
        row.setProductId("gift".equals(type) ? null : 101L); row.setDeliveredQuantity(new BigDecimal("2"));
        row.setUnitPrice(new BigDecimal(price)); return row;
    }
    private static InvOutboundRecord record(String type, String amount) {
        InvOutboundRecord row = new InvOutboundRecord(); row.setItemType(type); row.setItemId(101L);
        row.setQuantity(new BigDecimal("2")); row.setCostAmount(new BigDecimal(amount)); return row;
    }
    private static InvStockLog log(String type, String qty, String price) {
        InvStockLog log = new InvStockLog(); log.setBusinessType(type); log.setBusinessId(10L);
        log.setChangeQuantity(new BigDecimal(qty).negate()); log.setCostPrice(new BigDecimal(price)); return log;
    }
}
