package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.constant.InvStatusConstants;
import com.erp.inventory.constant.InvTransferDiscrepancyDecisions;
import com.erp.inventory.constant.InvTransferTypes;
import com.erp.inventory.domain.InvStock;
import com.erp.inventory.domain.InvTransferDetail;
import com.erp.inventory.domain.InvTransferDiscrepancy;
import com.erp.inventory.domain.InvTransferDiscrepancyDetail;
import com.erp.inventory.domain.InvTransferDiscrepancyDisposition;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.InvTransferShipment;
import com.erp.inventory.domain.InvTransferShipmentDetail;
import com.erp.inventory.domain.dto.InvTransferDiscrepancyResolutionItem;
import com.erp.inventory.domain.dto.InvTransferDiscrepancyResolveRequest;
import com.erp.inventory.mapper.InvDeptScopeMapper;
import com.erp.inventory.mapper.InvNumberSequenceMapper;
import com.erp.inventory.mapper.InvStockLogMapper;
import com.erp.inventory.mapper.InvStockMapper;
import com.erp.inventory.mapper.InvTransferDetailMapper;
import com.erp.inventory.mapper.InvTransferDiscrepancyDispositionMapper;
import com.erp.inventory.mapper.InvTransferDiscrepancyMapper;
import com.erp.inventory.mapper.InvTransferOrderMapper;
import com.erp.inventory.mapper.InvTransferShipmentDetailMapper;
import com.erp.inventory.mapper.InvTransferShipmentMapper;
import com.erp.inventory.mapper.InvTransferStatusLogMapper;

@DisplayName("调拨差异逐项处置")
class InvTransferDiscrepancyProcessorTest
{
    private Harness harness;

    @BeforeEach
    void setUp()
    {
        SecurityContextHolder.setUserId("7");
        SecurityContextHolder.setUserName("operator");
        harness = new Harness();
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("混合差异必须按明细和类别分别处置")
    void resolvesMixedCategoriesIndependently()
    {
        harness.addDetail(11L, 111L, "10", "8", "1", "1", "0", "5.00");

        InvTransferDiscrepancyResolveRequest request = harness.request(
                "req-mixed-1", 0L,
                harness.item(11L, "SHORTAGE", "WRITE_OFF", "1"),
                harness.item(11L, "REJECTED", "RETURN_SOURCE", "1"));

        harness.processor.resolveTransferDiscrepancy(1L, request, 202L);

        assertThat(harness.dispositions)
                .extracting(InvTransferDiscrepancyDisposition::getCategory,
                        InvTransferDiscrepancyDisposition::getDecision)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(
                                "SHORTAGE", "WRITE_OFF"),
                        org.assertj.core.groups.Tuple.tuple(
                                "REJECTED", "RETURN_SOURCE"));
    }

    @Test
    @DisplayName("短少补发只释放已发数量且不给源仓加库存")
    void reshipReleasesDeliveredQuantityWithoutReturningStock()
    {
        harness.addDetail(11L, 111L, "10", "8", "2", "0", "0", "5.00");
        InvTransferDiscrepancyResolveRequest request = harness.request(
                "req-reship-1", 0L,
                harness.item(11L, "SHORTAGE", "RESHIP", "2"));

        harness.processor.resolveTransferDiscrepancy(1L, request, 202L);

        ArgumentCaptor<InvTransferDetail> released = ArgumentCaptor.forClass(
                InvTransferDetail.class);
        verify(harness.detailMapper).decreaseDeliveredQuantity(
                released.capture());
        assertThat(released.getValue().getDeliveredQuantity())
                .isEqualByComparingTo("2");
        verify(harness.stockMapper, never()).insertInvStock(any());
        verify(harness.stockMapper, never()).addInvStockWithCost(
                anyLong(), anyLong(), any(), any(), any());
        assertThat(harness.dispositions).singleElement().satisfies(row -> {
            assertThat(row.getInventoryImpact()).isEqualTo("NO_STOCK_CHANGE");
            assertThat(row.getAmount()).isEqualByComparingTo("10.00");
        });
    }

    @Test
    @DisplayName("混合差异缺少任一类别时零副作用拒绝")
    void rejectsIncompleteMixedDisposition()
    {
        harness.addDetail(11L, 111L, "10", "8", "1", "1", "0", "5.00");
        InvTransferDiscrepancyResolveRequest request = harness.request(
                "req-incomplete-1", 0L,
                harness.item(11L, "SHORTAGE", "WRITE_OFF", "1"));

        assertThatThrownBy(() -> harness.processor
                .resolveTransferDiscrepancy(1L, request, 202L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("全部待处置");

        assertThat(harness.dispositions).isEmpty();
        verify(harness.discrepancyMapper, never()).resolve(anyLong(),
                anyLong(), any(), any(), any(), any(), anyLong(), any());
        verify(harness.stockMapper, never()).insertInvStock(any());
    }

    @Test
    @DisplayName("退回来源重放同一requestId只增加一次源仓库存")
    void returnSourceReplayAddsStockOnce()
    {
        harness.addDetail(11L, 111L, "10", "9", "0", "1", "0", "5.00");
        harness.prepareExistingSourceStock("4");
        InvTransferDiscrepancyResolveRequest request = harness.request(
                "req-return-1", 0L,
                harness.item(11L, "REJECTED", "RETURN_SOURCE", "1"));

        harness.processor.resolveTransferDiscrepancy(1L, request, 202L);
        harness.processor.resolveTransferDiscrepancy(1L, request, 202L);

        verify(harness.stockMapper, times(1)).addInvStockWithCost(
                eq(66L), eq(0L), eq(new BigDecimal("1")),
                eq(new BigDecimal("5.00")), eq("operator"));
        verify(harness.stockLogMapper, times(1)).insertInvStockLog(any());
        assertThat(harness.dispositions).hasSize(1);
    }

    @Test
    @DisplayName("退回来源库存乐观锁失败时不得写流水或处置台账")
    void returnSourceFailsClosedWhenStockUpdateLosesCas()
    {
        harness.addDetail(11L, 111L, "10", "9", "0", "1", "0", "5.00");
        harness.prepareExistingSourceStock("4");
        when(harness.stockMapper.addInvStockWithCost(eq(66L), eq(0L),
                any(), any(), any())).thenReturn(0);
        InvTransferDiscrepancyResolveRequest request = harness.request(
                "req-return-cas-1", 0L,
                harness.item(11L, "REJECTED", "RETURN_SOURCE", "1"));

        assertThatThrownBy(() -> harness.processor
                .resolveTransferDiscrepancy(1L, request, 202L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("库存");

        verify(harness.stockLogMapper, never()).insertInvStockLog(any());
        assertThat(harness.dispositions).isEmpty();
    }

    @Test
    @DisplayName("核销只写冻结成本台账不伪造库存流水")
    void writeOffUsesShipmentCostWithoutStockMovement()
    {
        harness.addDetail(11L, 111L, "10", "8", "2", "0", "0", "7.25");
        InvTransferDiscrepancyResolveRequest request = harness.request(
                "req-writeoff-1", 0L,
                harness.item(11L, "SHORTAGE", "WRITE_OFF", "2"));

        harness.processor.resolveTransferDiscrepancy(1L, request, 202L);

        verify(harness.stockMapper, never()).insertInvStock(any());
        verify(harness.stockLogMapper, never()).insertInvStockLog(any());
        assertThat(harness.dispositions).singleElement().satisfies(row -> {
            assertThat(row.getQuantity()).isEqualByComparingTo("2");
            assertThat(row.getCostPrice()).isEqualByComparingTo("7.25");
            assertThat(row.getAmount()).isEqualByComparingTo("14.50");
            assertThat(row.getResponsibleParty()).isEqualTo("LOGISTICS");
            assertThat(row.getHandledByUserId()).isEqualTo(7L);
            assertThat(row.getHandledByName()).isEqualTo("operator");
            assertThat(row.getNote()).isEqualTo("逐项核对");
        });
    }

    @Test
    @DisplayName("残损待质检后可核销且版本冲突零副作用")
    void pendingQcCanTransitionToWriteOffWithCas()
    {
        harness.addDetail(11L, 111L, "10", "9", "0", "0", "1", "6.00");
        InvTransferDiscrepancyResolveRequest hold = harness.request(
                "req-qc-1", 0L,
                harness.item(11L, "DAMAGED", "PENDING_QC", "1"));
        harness.processor.resolveTransferDiscrepancy(1L, hold, 202L);

        assertThat(harness.discrepancy.getStatus()).isEqualTo("PENDING_QC");
        assertThat(harness.dispositions).singleElement()
                .extracting(InvTransferDiscrepancyDisposition::getInventoryImpact)
                .isEqualTo("QC_HOLD");

        InvTransferDiscrepancyResolveRequest stale = harness.request(
                "req-qc-stale", 0L,
                harness.item(11L, "DAMAGED", "WRITE_OFF", "1"));
        assertThatThrownBy(() -> harness.processor
                .resolveTransferDiscrepancy(1L, stale, 202L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("刷新后重试");
        assertThat(harness.dispositions).hasSize(1);

        InvTransferDiscrepancyResolveRequest finish = harness.request(
                "req-qc-2", 1L,
                harness.item(11L, "DAMAGED", "WRITE_OFF", "1"));
        harness.processor.resolveTransferDiscrepancy(1L, finish, 202L);

        assertThat(harness.discrepancy.getStatus()).isEqualTo("RESOLVED");
        assertThat(harness.dispositions)
                .extracting(InvTransferDiscrepancyDisposition::getDecision)
                .containsExactly("PENDING_QC", "WRITE_OFF");
        verify(harness.stockLogMapper, never()).insertInvStockLog(any());
    }

    @Test
    @DisplayName("数量不守恒时在CAS和库存之前失败")
    void rejectsBrokenQuantityConservation()
    {
        harness.addDetail(11L, 111L, "10", "8", "1", "0", "0", "5.00");
        InvTransferDiscrepancyResolveRequest request = harness.request(
                "req-broken-1", 0L,
                harness.item(11L, "SHORTAGE", "WRITE_OFF", "1"));

        assertThatThrownBy(() -> harness.processor
                .resolveTransferDiscrepancy(1L, request, 202L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("数量不守恒");
        verify(harness.discrepancyMapper, never()).resolve(anyLong(),
                anyLong(), any(), any(), any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("原发货成本未知时fail-closed且零副作用")
    void rejectsUnknownFrozenShipmentCost()
    {
        harness.addDetail(11L, 111L, "10", "8", "2", "0", "0", null);
        InvTransferDiscrepancyResolveRequest request = harness.request(
                "req-cost-1", 0L,
                harness.item(11L, "SHORTAGE", "WRITE_OFF", "2"));

        assertThatThrownBy(() -> harness.processor
                .resolveTransferDiscrepancy(1L, request, 202L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("成本未冻结");
        verify(harness.discrepancyMapper, never()).resolve(anyLong(),
                anyLong(), any(), any(), any(), any(), anyLong(), any());
        assertThat(harness.dispositions).isEmpty();
    }

    @Test
    @DisplayName("同requestId改变处置内容必须拒绝")
    void rejectsRequestIdPayloadConflict()
    {
        harness.addDetail(11L, 111L, "10", "8", "2", "0", "0", "5.00");
        InvTransferDiscrepancyResolveRequest first = harness.request(
                "req-conflict-1", 0L,
                harness.item(11L, "SHORTAGE", "WRITE_OFF", "2"));
        harness.processor.resolveTransferDiscrepancy(1L, first, 202L);

        InvTransferDiscrepancyResolveRequest conflict = harness.request(
                "req-conflict-1", 0L,
                harness.item(11L, "SHORTAGE", "ACCEPT_ACTUAL", "2"));
        assertThatThrownBy(() -> harness.processor
                .resolveTransferDiscrepancy(1L, conflict, 202L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("requestId");
        assertThat(harness.dispositions).hasSize(1);
    }

    @Test
    @DisplayName("同requestId重放重复一行并遗漏另一行必须拒绝")
    void rejectsReplayThatDuplicatesOneKeyAndOmitsAnother()
    {
        harness.addDetail(11L, 111L, "10", "8", "2", "0", "0", "5.00");
        harness.addDetail(12L, 112L, "6", "5", "1", "0", "0", "4.00");
        InvTransferDiscrepancyResolveRequest first = harness.request(
                "req-replay-keyset-1", 0L,
                harness.item(11L, "SHORTAGE", "WRITE_OFF", "2"),
                harness.item(12L, "SHORTAGE", "WRITE_OFF", "1"));
        harness.processor.resolveTransferDiscrepancy(1L, first, 202L);

        InvTransferDiscrepancyResolutionItem duplicated = harness.item(
                11L, "SHORTAGE", "WRITE_OFF", "2");
        InvTransferDiscrepancyResolveRequest conflict = harness.request(
                "req-replay-keyset-1", 0L, duplicated, duplicated);

        assertThatThrownBy(() -> harness.processor
                .resolveTransferDiscrepancy(1L, conflict, 202L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("requestId");
        assertThat(harness.dispositions).hasSize(2);
    }

    @Test
    @DisplayName("单类别旧header请求仍可临时兼容")
    void supportsLegacyHeaderForSingleCategory()
    {
        harness.addDetail(11L, 111L, "10", "8", "2", "0", "0", "5.00");
        InvTransferDiscrepancyResolveRequest request =
                new InvTransferDiscrepancyResolveRequest();
        request.setRequestId("req-legacy-single-1");
        request.setVersion(0L);
        request.setDecision("WRITE_OFF");
        request.setResponsibleParty("LOGISTICS");
        request.setNote("旧客户端单类别处置");
        request.setItems(null);

        harness.processor.resolveTransferDiscrepancy(1L, request, 202L);

        assertThat(harness.dispositions).singleElement().satisfies(row -> {
            assertThat(row.getCategory()).isEqualTo("SHORTAGE");
            assertThat(row.getDecision()).isEqualTo("WRITE_OFF");
            assertThat(row.getQuantity()).isEqualByComparingTo("2");
        });
    }

    @Test
    @DisplayName("混合类别旧header请求必须升级后逐项处置")
    void rejectsLegacyHeaderForMixedCategories()
    {
        harness.addDetail(11L, 111L, "10", "8", "1", "1", "0", "5.00");
        InvTransferDiscrepancyResolveRequest request =
                new InvTransferDiscrepancyResolveRequest();
        request.setRequestId("req-legacy-mixed-1");
        request.setVersion(0L);
        request.setDecision("WRITE_OFF");
        request.setResponsibleParty("LOGISTICS");
        request.setNote("旧客户端混合处置");
        request.setItems(null);

        assertThatThrownBy(() -> harness.processor
                .resolveTransferDiscrepancy(1L, request, 202L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("升级客户端");
        assertThat(harness.dispositions).isEmpty();
    }

    @Test
    @DisplayName("接受实收只写台账且不重复扣减库存")
    void acceptActualOnlyWritesFrozenDisposition()
    {
        harness.addDetail(11L, 111L, "10", "8", "2", "0", "0", "7.25");
        InvTransferDiscrepancyResolveRequest request = harness.request(
                "req-accept-actual-1", 0L,
                harness.item(11L, "SHORTAGE", "ACCEPT_ACTUAL", "2"));

        harness.processor.resolveTransferDiscrepancy(1L, request, 202L);

        verify(harness.detailMapper, never()).decreaseDeliveredQuantity(any());
        verify(harness.stockMapper, never()).insertInvStock(any());
        verify(harness.stockMapper, never()).addInvStockWithCost(
                anyLong(), anyLong(), any(), any(), any());
        verify(harness.stockLogMapper, never()).insertInvStockLog(any());
        assertThat(harness.dispositions).singleElement().satisfies(row -> {
            assertThat(row.getInventoryImpact()).isEqualTo("NO_STOCK_CHANGE");
            assertThat(row.getCostPrice()).isEqualByComparingTo("7.25");
            assertThat(row.getAmount()).isEqualByComparingTo("14.50");
        });
    }

    @Test
    @DisplayName("拒收待质检后只能按新版本回源并写一次库存流水")
    void rejectedPendingQcCanReturnSourceAfterCas()
    {
        harness.addDetail(11L, 111L, "10", "9", "0", "1", "0", "5.00");
        harness.prepareExistingSourceStock("4");
        InvTransferDiscrepancyResolveRequest hold = harness.request(
                "req-rejected-qc-1", 0L,
                harness.item(11L, "REJECTED", "PENDING_QC", "1"));
        harness.processor.resolveTransferDiscrepancy(1L, hold, 202L);

        InvTransferDiscrepancyResolveRequest finish = harness.request(
                "req-rejected-qc-2", 1L,
                harness.item(11L, "REJECTED", "RETURN_SOURCE", "1"));
        harness.processor.resolveTransferDiscrepancy(1L, finish, 202L);

        assertThat(harness.discrepancy.getStatus()).isEqualTo("RESOLVED");
        assertThat(harness.dispositions)
                .extracting(InvTransferDiscrepancyDisposition::getDecision)
                .containsExactly("PENDING_QC", "RETURN_SOURCE");
        verify(harness.stockMapper, times(1)).addInvStockWithCost(
                eq(66L), eq(0L), eq(new BigDecimal("1")),
                eq(new BigDecimal("5.00")), eq("operator"));
        verify(harness.stockLogMapper, times(1)).insertInvStockLog(any());
    }

    @Test
    @DisplayName("残损无附件证据时不得直接终结")
    void damagedCannotTerminateWithoutEvidence()
    {
        harness.addDetail(11L, 111L, "10", "9", "0", "0", "1", "6.00");
        InvTransferDiscrepancyResolutionItem item = harness.item(
                11L, "DAMAGED", "WRITE_OFF", "1");
        item.setAttachmentRefs(null);
        InvTransferDiscrepancyResolveRequest request = harness.request(
                "req-damaged-no-evidence-1", 0L, item);

        assertThatThrownBy(() -> harness.processor
                .resolveTransferDiscrepancy(1L, request, 202L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不匹配");
        assertThat(harness.dispositions).isEmpty();
        assertThat(harness.discrepancy.getStatus()).isEqualTo("OPEN");
    }

    @Test
    @DisplayName("重复类别和跨差异明细均拒绝")
    void rejectsDuplicateAndCrossDiscrepancyItems()
    {
        harness.addDetail(11L, 111L, "10", "8", "2", "0", "0", "5.00");
        InvTransferDiscrepancyResolveRequest duplicate = harness.request(
                "req-duplicate-1", 0L,
                harness.item(11L, "SHORTAGE", "WRITE_OFF", "2"),
                harness.item(11L, "SHORTAGE", "WRITE_OFF", "2"));

        assertThatThrownBy(() -> harness.processor
                .resolveTransferDiscrepancy(1L, duplicate, 202L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("全部待处置");
        assertThat(harness.dispositions).isEmpty();

        InvTransferDiscrepancyResolveRequest crossDetail = harness.request(
                "req-cross-detail-1", 0L,
                harness.item(999L, "SHORTAGE", "WRITE_OFF", "2"));
        assertThatThrownBy(() -> harness.processor
                .resolveTransferDiscrepancy(1L, crossDetail, 202L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不属于当前差异单");
        assertThat(harness.dispositions).isEmpty();
    }

    @Test
    @DisplayName("差异明细可省略同批次完全验收行且仍保持主表守恒")
    void acceptsFullyReceivedShipmentRowsOmittedFromDiscrepancyDetails()
    {
        harness.addDetail(11L, 111L, "10", "8", "2", "0", "0", "5.00");
        harness.addFullyAcceptedHeaderQuantity("5");
        InvTransferDiscrepancyResolveRequest request = harness.request(
                "req-clean-row-1", 0L,
                harness.item(11L, "SHORTAGE", "WRITE_OFF", "2"));

        harness.processor.resolveTransferDiscrepancy(1L, request, 202L);

        assertThat(harness.dispositions).singleElement()
                .extracting(InvTransferDiscrepancyDisposition::getDecision)
                .isEqualTo("WRITE_OFF");
    }

    @Test
    @DisplayName("处置完成后仍有未发数量时调拨必须回到可发货状态")
    void keepsTransferDeliverableWhenRequestedQuantityIsStillOutstanding()
    {
        harness.addDetail(11L, 111L, "10", "8", "2", "0", "0", "5.00");
        harness.transferDetails.get(0).setQuantity(new BigDecimal("12"));
        InvTransferDiscrepancyResolveRequest request = harness.request(
                "req-outstanding-delivery-1", 0L,
                harness.item(11L, "SHORTAGE", "WRITE_OFF", "2"));

        harness.processor.resolveTransferDiscrepancy(1L, request, 202L);

        ArgumentCaptor<InvTransferOrder> update = ArgumentCaptor.forClass(
                InvTransferOrder.class);
        verify(harness.orderMapper).updateInvTransferOrder(update.capture());
        assertThat(update.getValue().getStatus())
                .isEqualTo(InvStatusConstants.PARTIAL_DELIVERED);
    }

    @Test
    @DisplayName("处置完成但另有待收批次时调拨必须保持可收货状态")
    void keepsTransferReceivableWhenAnotherShipmentIsPending()
    {
        harness.addDetail(11L, 111L, "10", "8", "2", "0", "0", "5.00");
        harness.addPendingShipment(801L);
        InvTransferDiscrepancyResolveRequest request = harness.request(
                "req-pending-shipment-1", 0L,
                harness.item(11L, "SHORTAGE", "WRITE_OFF", "2"));

        harness.processor.resolveTransferDiscrepancy(1L, request, 202L);

        ArgumentCaptor<InvTransferOrder> update = ArgumentCaptor.forClass(
                InvTransferOrder.class);
        verify(harness.orderMapper).updateInvTransferOrder(update.capture());
        assertThat(update.getValue().getStatus())
                .isEqualTo(InvStatusConstants.PARTIAL_RECEIVED);
    }

    @Test
    @DisplayName("待质检二阶段完成后主表决定必须汇总全部最终类别")
    void finalSummaryIncludesTerminalDecisionsFromBothQcStages()
    {
        harness.addDetail(11L, 111L, "10", "8", "1", "0", "1", "5.00");
        InvTransferDiscrepancyResolveRequest hold = harness.request(
                "req-summary-qc-1", 0L,
                harness.item(11L, "SHORTAGE", "ACCEPT_ACTUAL", "1"),
                harness.item(11L, "DAMAGED", "PENDING_QC", "1"));
        harness.processor.resolveTransferDiscrepancy(1L, hold, 202L);

        InvTransferDiscrepancyResolveRequest finish = harness.request(
                "req-summary-qc-2", 1L,
                harness.item(11L, "DAMAGED", "WRITE_OFF", "1"));
        harness.processor.resolveTransferDiscrepancy(1L, finish, 202L);

        assertThat(harness.discrepancy.getResolutionDecision())
                .isEqualTo("MIXED");
    }

    private static final class Harness
    {
        private final InvTransferOrderMapper orderMapper = mock(
                InvTransferOrderMapper.class);
        private final InvTransferDetailMapper detailMapper = mock(
                InvTransferDetailMapper.class);
        private final InvTransferDiscrepancyMapper discrepancyMapper = mock(
                InvTransferDiscrepancyMapper.class);
        private final InvTransferDiscrepancyDispositionMapper dispositionMapper =
                mock(InvTransferDiscrepancyDispositionMapper.class);
        private final InvTransferShipmentMapper shipmentMapper = mock(
                InvTransferShipmentMapper.class);
        private final InvTransferShipmentDetailMapper shipmentDetailMapper = mock(
                InvTransferShipmentDetailMapper.class);
        private final InvStockMapper stockMapper = mock(InvStockMapper.class);
        private final InvStockLogMapper stockLogMapper = mock(
                InvStockLogMapper.class);
        private final InvTransferStatusLogMapper statusLogMapper = mock(
                InvTransferStatusLogMapper.class);
        private final InvDeptScopeMapper deptScopeMapper = mock(
                InvDeptScopeMapper.class);
        private final InvTransferOrder order = order();
        private final InvTransferDiscrepancy discrepancy = discrepancy();
        private final List<InvTransferDiscrepancyDetail> discrepancyDetails =
                new ArrayList<>();
        private final List<InvTransferShipmentDetail> shipmentDetails =
                new ArrayList<>();
        private final List<InvTransferDetail> transferDetails =
                new ArrayList<>();
        private final List<InvTransferShipment> shipments = new ArrayList<>();
        private final List<InvTransferDiscrepancyDisposition> dispositions =
                new ArrayList<>();
        private final InvTransferDiscrepancyProcessor processor;

        Harness()
        {
            when(orderMapper.selectInvTransferOrderById(900L))
                    .thenReturn(order);
            when(orderMapper.selectInvTransferOrderByIdForUpdate(900L))
                    .thenReturn(order);
            when(discrepancyMapper.selectByIdForUpdate(1L))
                    .thenReturn(discrepancy);
            when(discrepancyMapper.selectDetails(1L))
                    .thenReturn(discrepancyDetails);
            when(discrepancyMapper.resolve(anyLong(), anyLong(), any(), any(),
                    any(), any(), anyLong(), any())).thenAnswer(invocation -> {
                        long expected = invocation.getArgument(1);
                        if (discrepancy.getVersion() != expected)
                        {
                            return 0;
                        }
                        discrepancy.setStatus(invocation.getArgument(2));
                        discrepancy.setResolutionDecision(
                                invocation.getArgument(3));
                        discrepancy.setVersion(expected + 1);
                        return 1;
                    });
            when(dispositionMapper.insertDisposition(any())).thenAnswer(
                    invocation -> {
                        InvTransferDiscrepancyDisposition row =
                                invocation.getArgument(0);
                        row.setDispositionId((long) dispositions.size() + 1);
                        dispositions.add(row);
                        return 1;
                    });
            when(dispositionMapper.selectByRequestId(anyLong(), any()))
                    .thenAnswer(invocation -> dispositions.stream()
                            .filter(row -> invocation.<Long>getArgument(0)
                                    .equals(row.getDiscrepancyId()))
                            .filter(row -> invocation.<String>getArgument(1)
                                    .equals(row.getRequestId()))
                            .toList());
            when(dispositionMapper.selectLatestByDiscrepancyId(anyLong()))
                    .thenAnswer(invocation -> latest(dispositions));
            when(dispositionMapper.selectByDiscrepancyId(anyLong()))
                    .thenAnswer(invocation -> new ArrayList<>(dispositions));
            when(detailMapper.selectByTransferIdForUpdate(900L))
                    .thenReturn(transferDetails);
            when(detailMapper.selectByTransferId(900L))
                    .thenReturn(transferDetails);
            when(detailMapper.decreaseDeliveredQuantity(any()))
                    .thenAnswer(invocation -> {
                        InvTransferDetail decrease = invocation.getArgument(0);
                        InvTransferDetail stored = transferDetails.stream()
                                .filter(detail -> detail.getDetailId().equals(
                                        decrease.getDetailId()))
                                .findFirst().orElse(null);
                        if (stored == null || stored.getDeliveredQuantity()
                                .compareTo(decrease.getDeliveredQuantity()) < 0)
                        {
                            return 0;
                        }
                        stored.setDeliveredQuantity(stored.getDeliveredQuantity()
                                .subtract(decrease.getDeliveredQuantity()));
                        return 1;
                    });
            when(shipmentMapper.selectByTransferId(900L))
                    .thenReturn(shipments);
            when(shipmentDetailMapper.selectByShipmentId(800L))
                    .thenReturn(shipmentDetails);
            when(stockMapper.insertInvStock(any())).thenReturn(1);
            when(deptScopeMapper.countDeptInScope(anyLong(), anyLong()))
                    .thenReturn(1);
            when(deptScopeMapper.countUserShopScope(anyLong(), anyLong()))
                    .thenReturn(1);
            when(deptScopeMapper.selectDeptTypeById(anyLong()))
                    .thenReturn("STORE");
            InvTransferWorkflowResources resources =
                    new InvTransferWorkflowResources(orderMapper,
                            detailMapper, discrepancyMapper,
                            dispositionMapper, shipmentMapper,
                            shipmentDetailMapper, stockMapper,
                            stockLogMapper, mock(InvNumberSequenceMapper.class),
                            statusLogMapper, null, null, deptScopeMapper, null,
                            mock(InvTransferReservationService.class));
            processor = new InvTransferDiscrepancyProcessor(resources,
                    new InvTransferDirectionPolicy(deptScopeMapper, null));
        }

        void addDetail(Long discrepancyDetailId, Long shipmentDetailId,
                String shipped, String accepted, String shortage,
                String rejected, String damaged, String costPrice)
        {
            InvTransferDiscrepancyDetail detail =
                    new InvTransferDiscrepancyDetail();
            detail.setDiscrepancyDetailId(discrepancyDetailId);
            detail.setDiscrepancyId(1L);
            detail.setTransferDetailId(discrepancyDetailId + 1000);
            detail.setShipmentDetailId(shipmentDetailId);
            detail.setItemType("product");
            detail.setItemId(1001L);
            detail.setItemName("测试商品");
            detail.setShippedQuantity(decimal(shipped));
            detail.setAcceptedQuantity(decimal(accepted));
            detail.setShortageQuantity(decimal(shortage));
            detail.setRejectedQuantity(decimal(rejected));
            detail.setDamagedQuantity(decimal(damaged));
            detail.setResolutionQuantity(decimal(shortage)
                    .add(decimal(rejected)).add(decimal(damaged)));
            discrepancyDetails.add(detail);

            InvTransferShipmentDetail shipmentDetail =
                    new InvTransferShipmentDetail();
            shipmentDetail.setShipmentDetailId(shipmentDetailId);
            shipmentDetail.setShipmentId(800L);
            shipmentDetail.setTransferId(900L);
            shipmentDetail.setTransferDetailId(detail.getTransferDetailId());
            shipmentDetail.setItemType("product");
            shipmentDetail.setItemId(1001L);
            shipmentDetail.setProductId(1001L);
            shipmentDetail.setShippedQuantity(decimal(shipped));
            shipmentDetail.setReceivedQuantity(decimal(accepted));
            shipmentDetail.setCostPrice(costPrice == null
                    ? null : decimal(costPrice));
            shipmentDetails.add(shipmentDetail);

            InvTransferDetail transferDetail = new InvTransferDetail();
            transferDetail.setDetailId(detail.getTransferDetailId());
            transferDetail.setQuantity(decimal(shipped));
            transferDetail.setDeliveredQuantity(decimal(shipped));
            transferDetail.setReceivedQuantity(decimal(accepted));
            transferDetails.add(transferDetail);

            discrepancy.setShippedQuantity(nullToZero(
                    discrepancy.getShippedQuantity()).add(decimal(shipped)));
            discrepancy.setAcceptedQuantity(nullToZero(
                    discrepancy.getAcceptedQuantity()).add(decimal(accepted)));
            discrepancy.setShortageQuantity(nullToZero(
                    discrepancy.getShortageQuantity()).add(decimal(shortage)));
            discrepancy.setRejectedQuantity(nullToZero(
                    discrepancy.getRejectedQuantity()).add(decimal(rejected)));
            discrepancy.setDamagedQuantity(nullToZero(
                    discrepancy.getDamagedQuantity()).add(decimal(damaged)));
        }

        InvTransferDiscrepancyResolveRequest request(String requestId,
                Long version, InvTransferDiscrepancyResolutionItem... items)
        {
            InvTransferDiscrepancyResolveRequest request =
                    new InvTransferDiscrepancyResolveRequest();
            request.setRequestId(requestId);
            request.setVersion(version);
            request.setResponsibleParty("LOGISTICS");
            request.setNote("核对处置");
            request.setItems(List.of(items));
            return request;
        }

        InvTransferDiscrepancyResolutionItem item(Long detailId,
                String category, String decision, String quantity)
        {
            InvTransferDiscrepancyResolutionItem item =
                    new InvTransferDiscrepancyResolutionItem();
            item.setDetailId(detailId);
            item.setCategory(category);
            item.setDecision(decision);
            item.setQuantity(decimal(quantity));
            item.setNote("逐项核对");
            item.setAttachmentRefs("node-1");
            return item;
        }

        void prepareExistingSourceStock(String quantity)
        {
            InvStock source = new InvStock();
            source.setStockId(66L);
            source.setItemType("product");
            source.setItemId(1001L);
            source.setProductId(1001L);
            source.setShopDeptId(201L);
            source.setWarehouseId(201L);
            source.setCurrentQuantity(decimal(quantity));
            source.setAvailableQuantity(decimal(quantity));
            source.setCostPrice(decimal("4.00"));
            source.setTotalCost(decimal(quantity).multiply(decimal("4.00")));
            source.setVersion(0L);
            when(stockMapper.selectInvStockByItemShopWarehouseForUpdate(
                    "product", 1001L, 201L, 201L)).thenReturn(source);
            when(stockMapper.addInvStockWithCost(eq(66L), eq(0L), any(),
                    any(), any())).thenAnswer(invocation -> {
                        BigDecimal incoming = invocation.getArgument(2);
                        source.setCurrentQuantity(source.getCurrentQuantity()
                                .add(incoming));
                        source.setAvailableQuantity(source.getAvailableQuantity()
                                .add(incoming));
                        source.setVersion(1L);
                        return 1;
                    });
            when(stockMapper.selectInvStockById(66L)).thenReturn(source);
        }

        void addFullyAcceptedHeaderQuantity(String quantity)
        {
            BigDecimal accepted = decimal(quantity);
            discrepancy.setShippedQuantity(discrepancy.getShippedQuantity()
                    .add(accepted));
            discrepancy.setAcceptedQuantity(discrepancy.getAcceptedQuantity()
                    .add(accepted));
        }

        void addPendingShipment(Long shipmentId)
        {
            InvTransferShipment shipment = new InvTransferShipment();
            shipment.setShipmentId(shipmentId);
            shipment.setTransferId(900L);
            shipment.setStatus(InvStatusConstants.PENDING_RECEIVE);
            shipments.add(shipment);
        }

        private static InvTransferOrder order()
        {
            InvTransferOrder order = new InvTransferOrder();
            order.setTransferId(900L);
            order.setOrderNo("TF-900");
            order.setStatus(InvStatusConstants.DISCREPANCY);
            order.setTransferType(InvTransferTypes.CROSS_STORE);
            order.setFromDeptId(201L);
            order.setFromWarehouseId(201L);
            order.setToDeptId(202L);
            order.setToWarehouseId(202L);
            order.setFromDeptName("来源店");
            order.setToDeptName("目标店");
            return order;
        }

        private static InvTransferDiscrepancy discrepancy()
        {
            InvTransferDiscrepancy discrepancy =
                    new InvTransferDiscrepancy();
            discrepancy.setDiscrepancyId(1L);
            discrepancy.setTransferId(900L);
            discrepancy.setShipmentId(800L);
            discrepancy.setDiscrepancyNo("TD-1");
            discrepancy.setStatus(InvTransferDiscrepancyDecisions.OPEN);
            discrepancy.setVersion(0L);
            return discrepancy;
        }

        private static List<InvTransferDiscrepancyDisposition> latest(
                List<InvTransferDiscrepancyDisposition> rows)
        {
            Map<String, InvTransferDiscrepancyDisposition> latest =
                    new HashMap<>();
            for (InvTransferDiscrepancyDisposition row : rows)
            {
                latest.put(row.getDiscrepancyDetailId() + ":"
                        + row.getCategory(), row);
            }
            return latest.values().stream()
                    .sorted(Comparator.comparing(
                            InvTransferDiscrepancyDisposition::getDispositionId))
                    .collect(Collectors.toList());
        }

        private static BigDecimal decimal(String value)
        {
            return new BigDecimal(value);
        }

        private static BigDecimal nullToZero(BigDecimal value)
        {
            return value == null ? BigDecimal.ZERO : value;
        }
    }
}
