package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.*;
import com.erp.inventory.domain.dto.*;

class InvPurchaseReceiveCommandTest
{
    @Test void equivalentOrderingScaleAndTrimHaveOneFrozenPayload()
    {
        InvReceiveRequest a = request();
        a.setItems(List.of(item(201L, "20.00"), item(202L, "3.0")));
        a.setRemark(" receipt ");
        InvReceiveRequest b = request();
        b.setItems(List.of(item(202L, "3"), item(201L, "20"))); b.setRemark("receipt");
        assertThat(InvPurchaseServiceImpl.freezeReceivePayload(10L, a))
                .isEqualTo(InvPurchaseServiceImpl.freezeReceivePayload(10L, b));
        Map<String, Object> frozen = InvPurchaseServiceImpl.freezeReceivePayload(10L, a);
        a.getItems().get(0).setReceiveQuantity(BigDecimal.ONE); a.getArrivedTime().setTime(1);
        assertThat(frozen).isEqualTo(InvPurchaseServiceImpl.freezeReceivePayload(10L, b));
    }

    @Test void everyEffectiveReceiptFieldChangesTheFrozenPayload()
    {
        List<Consumer<InvReceiveRequest>> changes = List.of(r -> r.setWarehouseId(30L),
                r -> r.setArrivedTime(new Date(2)), r -> r.setRemark("changed"),
                r -> r.setDeliveryNoteNo("changed"), r -> r.setSupplierBatchNo("changed"),
                r -> r.getItems().get(0).setReceiveQuantity(BigDecimal.ONE),
                r -> r.getItems().get(0).setDetailId(202L));
        var original = InvPurchaseServiceImpl.freezeReceivePayload(10L, request());
        for (var change : changes)
        {
            var altered = request(); change.accept(altered);
            assertThat(InvPurchaseServiceImpl.freezeReceivePayload(10L, altered)).isNotEqualTo(original);
        }
        assertThat(InvPurchaseServiceImpl.freezeReceivePayload(11L, request())).isNotEqualTo(original);
    }

    @Test void malformedRowsCannotDisappearIntoTheOriginalFingerprint()
    {
        for (List<InvReceiveItem> items : Arrays.asList(Arrays.asList(item(201L, "20"), null),
                List.of(item(201L, "20"), item(201L, "20")), List.of(item(null, "20")), List.<InvReceiveItem>of()))
        {
            var request = request(); request.setItems(items);
            assertThatThrownBy(() -> InvPurchaseServiceImpl.freezeReceivePayload(10L, request))
                    .isInstanceOf(ServiceException.class);
        }
    }

    @Test void totalsIncludeAllBatchesAndHistoricalFactsWithoutDoubleCounting()
    {
        var a = batchDetail(); a.setAcceptedQuantity(new BigDecimal("20"));
        var b = batchDetail(); b.setBatchDetailId(302L); b.setBatchId(102L);
        b.setRejectedQuantity(new BigDecimal("5")); b.setPendingQuantity(new BigDecimal("25"));
        var batchInbound = linkedInbound(); batchInbound.setAcceptedQuantity(new BigDecimal("20"));
        var legacy = new InvInboundRecord(); legacy.setQuantity(new BigDecimal("7")); legacy.setQcResult("passed");
        var totals = InvPurchaseServiceImpl.summarizePurchaseQualityFacts(List.of(a, b), List.of(batchInbound, legacy));
        assertThat((BigDecimal) ReflectionTestUtils.getField(totals, "accepted")).isEqualByComparingTo("27");
        assertThat((BigDecimal) ReflectionTestUtils.getField(totals, "rejected")).isEqualByComparingTo("5");
        assertThat(ReflectionTestUtils.getField(totals, "anyPending")).isEqualTo(true);
    }

    @Test void historicalPendingSelectionNeverIncludesBatchBackedOrHalfLinkedRows()
    {
        var legacy = new InvInboundRecord(); legacy.setQcResult("pending");
        var batch = linkedInbound(); batch.setQcResult("pending");
        var halfLinked = new InvInboundRecord(); halfLinked.setReceiptBatchId(101L);
        assertThat(InvPurchaseServiceImpl.historicalPendingInboundRecords(List.of(legacy, batch, halfLinked)))
                .containsExactly(legacy);
    }

    @Test void corruptBatchOrPurchaseDetailLinksBlockAggregation()
    {
        List<Consumer<InvInboundRecord>> corruptions = List.of(r -> r.setReceiptBatchId(null),
                r -> r.setReceiptBatchDetailId(null), r -> r.setReceiptBatchDetailId(999L),
                r -> r.setReceiptBatchId(999L), r -> r.setPurchaseDetailId(999L));
        for (var corrupt : corruptions)
        {
            var inbound = linkedInbound(); corrupt.accept(inbound);
            assertThatThrownBy(() -> InvPurchaseServiceImpl.summarizePurchaseQualityFacts(
                    List.of(batchDetail()), List.of(inbound))).isInstanceOf(ServiceException.class)
                    .hasMessageContaining("已阻断");
        }
    }

    private static InvReceiveItem item(Long id, String quantity)
    {
        var item = new InvReceiveItem(); item.setDetailId(id); item.setReceiveQuantity(new BigDecimal(quantity)); return item;
    }
    private static InvReceiveRequest request()
    {
        var r = new InvReceiveRequest(); r.setWarehouseId(20L); r.setArrivedTime(new Date(1752637800000L));
        r.setItems(List.of(item(201L, "20"))); return r;
    }
    private static InvReceiptBatchDetail batchDetail()
    {
        var d = new InvReceiptBatchDetail(); d.setBatchId(101L); d.setBatchDetailId(301L);
        d.setPurchaseDetailId(201L); return d;
    }
    private static InvInboundRecord linkedInbound()
    {
        var r = new InvInboundRecord(); r.setReceiptBatchId(101L); r.setReceiptBatchDetailId(301L);
        r.setPurchaseDetailId(201L); return r;
    }
}
