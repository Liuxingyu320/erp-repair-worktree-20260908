package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.inventory.domain.InvDeliveryNotice;
import com.erp.inventory.domain.InvDeliveryNoticeDetail;
import com.erp.inventory.domain.InvSalesDetail;
import com.erp.inventory.domain.InvSalesOrder;
import com.erp.inventory.domain.InvStock;
import com.erp.inventory.domain.InvTransferDetail;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.dto.InvDeliverRequest;
import com.erp.inventory.domain.dto.InvDeliverItem;
import com.erp.inventory.domain.dto.InvReceiveRequest;
import com.erp.inventory.mapper.InvDeliveryNoticeDetailMapper;
import com.erp.inventory.mapper.InvDeliveryNoticeMapper;
import com.erp.inventory.mapper.InvDeptScopeMapper;
import com.erp.inventory.mapper.InvOutboundRecordMapper;
import com.erp.inventory.mapper.InvSalesDetailMapper;
import com.erp.inventory.mapper.InvSalesOrderMapper;
import com.erp.inventory.mapper.InvStockLogMapper;
import com.erp.inventory.mapper.InvStockMapper;
import com.erp.inventory.service.IInvTransferService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("发货通知服务")
class InvDeliveryNoticeServiceImplTest
{
    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("存在未完成发货通知时禁止重复生成")
    void shouldBlockDuplicateActiveDeliveryNotice()
    {
        InvDeliveryNotice pending = new InvDeliveryNotice();
        pending.setStatus("pending");

        InvDeliveryNotice delivering = new InvDeliveryNotice();
        delivering.setStatus("delivering");

        InvDeliveryNotice completed = new InvDeliveryNotice();
        completed.setStatus("completed");

        InvDeliveryNotice cancelled = new InvDeliveryNotice();
        cancelled.setStatus("cancelled");

        assertThat(InvDeliveryNoticeServiceImpl.hasActiveNotice(List.of(pending))).isTrue();
        assertThat(InvDeliveryNoticeServiceImpl.hasActiveNotice(List.of(delivering))).isTrue();
        assertThat(InvDeliveryNoticeServiceImpl.hasActiveNotice(List.of(completed, cancelled))).isFalse();
        assertThat(InvDeliveryNoticeServiceImpl.hasActiveNotice(null)).isFalse();
    }

    @Test
    @DisplayName("跨店发货完成时返回创建出的调拨单号")
    void shouldReturnTransferOrderNoForCrossStoreDelivery()
    {
        String message = InvDeliveryNoticeServiceImpl.buildDeliveryResultMessage(true, "TF202606050001");

        assertThat(message)
                .contains("发货成功")
                .contains("跨店发货")
                .contains("调拨单号")
                .contains("TF202606050001")
                .doesNotContain("待集成")
                .doesNotContain("调拨模块稳定后接入");
    }

    @Test
    @DisplayName("跨店发货完成时通过调拨服务创建调拨单")
    void shouldCreateCrossStoreTransferThroughTransferService()
    {
        InvDeliveryNoticeServiceImpl service = new InvDeliveryNoticeServiceImpl();
        RecordingTransferService transferService = new RecordingTransferService("TF202606050002");
        ReflectionTestUtils.setField(service, "transferService", transferService);

        InvDeliveryNotice notice = new InvDeliveryNotice();
        notice.setNoticeId(101L);
        notice.setNoticeNo("DN202606050001");
        notice.setSalesOrderId(101L);
        notice.setSalesOrderNo("SO202606050001");
        notice.setShopDeptId(201L);

        InvSalesOrder salesOrder = new InvSalesOrder();
        salesOrder.setOrderId(10L);
        salesOrder.setStatus("noticed");
        salesOrder.setTargetDeptId(202L);
        salesOrder.setTargetDeptName("福田店");

        InvDeliveryNoticeDetail noticeDetail = new InvDeliveryNoticeDetail();
        noticeDetail.setSalesDetailId(401L);
        noticeDetail.setProductId(301L);
        noticeDetail.setProductName("高山红茶");
        noticeDetail.setDeliveredQty(new BigDecimal("2.50"));
        noticeDetail.setDeliveredCostAmount(new BigDecimal("20.83"));
        noticeDetail.setWarehouseId(901L);

        InvSalesDetail salesDetail = new InvSalesDetail();
        salesDetail.setDetailId(401L);
        salesDetail.setProductId(301L);
        salesDetail.setSku("SKU-301");
        salesDetail.setSpec("100g");
        salesDetail.setUnit("盒");

        String orderNo = service.createCrossStoreTransferForReceipt(
                notice,
                salesOrder,
                List.of(noticeDetail),
                Map.of(401L, salesDetail),
                201L);

        assertThat(orderNo).isEqualTo("TF202606050002");
        assertThat(transferService.selectedShopDeptId).isEqualTo(201L);
        assertThat(transferService.order.getPurchaseId()).isNull();
        assertThat(transferService.order.getFromDeptId()).isEqualTo(201L);
        assertThat(transferService.order.getToDeptId()).isEqualTo(202L);
        assertThat(transferService.order.getToDeptName()).isEqualTo("福田店");
        assertThat(transferService.order.getFromWarehouseId()).isEqualTo(901L);
        assertThat(transferService.order.getToWarehouseId()).isEqualTo(202L);
        assertThat(transferService.order.getTransferType()).isEqualTo("cross_store");
        assertThat(transferService.order.getSourceBusinessType()).isEqualTo("sales_delivery_notice");
        assertThat(transferService.order.getSourceBusinessId()).isEqualTo(101L);
        assertThat(transferService.order.getRemark()).contains("DN202606050001").contains("SO202606050001");
        assertThat(transferService.createdDeliveredCrossStoreTransfer).isTrue();
        assertThat(transferService.details).singleElement().satisfies(detail ->
        {
            assertThat(detail.getProductId()).isEqualTo(301L);
            assertThat(detail.getProductName()).isEqualTo("高山红茶");
            assertThat(detail.getProductCode()).isEqualTo("SKU-301");
            assertThat(detail.getSpec()).isEqualTo("100g");
            assertThat(detail.getUnit()).isEqualTo("盒");
            assertThat(detail.getQuantity()).isEqualByComparingTo("2.50");
            assertThat(detail.getCostPrice()).isEqualByComparingTo("8.33");
            assertThat(detail.getAmount()).isEqualByComparingTo("20.83");
        });
    }

    @Test
    @DisplayName("跨店发货按通知明细冻结仓库拆单并保留精确成本额")
    void shouldSplitCrossStoreTransferByFrozenWarehouseAndAmount()
    {
        InvDeliveryNoticeServiceImpl service = new InvDeliveryNoticeServiceImpl();
        RecordingTransferService transferService = new RecordingTransferService("TF202606050010");
        ReflectionTestUtils.setField(service, "transferService", transferService);

        InvDeliveryNotice notice = new InvDeliveryNotice();
        notice.setNoticeId(101L);
        notice.setNoticeNo("DN202606050009");
        notice.setSalesOrderId(501L);
        notice.setSalesOrderNo("SO202606050009");
        notice.setShopDeptId(201L);

        InvSalesOrder salesOrder = new InvSalesOrder();
        salesOrder.setOrderId(10L);
        salesOrder.setStatus("noticed");
        salesOrder.setTargetDeptId(202L);
        salesOrder.setTargetDeptName("福田店");

        InvDeliveryNoticeDetail first = frozenNoticeDetail(401L, 301L,
                "3.00", "10.00");
        InvDeliveryNoticeDetail second = frozenNoticeDetail(402L, 302L,
                "2.00", "18.00");

        String orderNos = service.createCrossStoreTransferForReceipt(
                notice, salesOrder, List.of(first, second),
                Collections.emptyMap(), 201L);

        assertThat(orderNos).isEqualTo("TF202606050010、TF202606050011");
        assertThat(transferService.orders)
                .extracting(InvTransferOrder::getFromWarehouseId)
                .containsExactlyInAnyOrder(301L, 302L);
        assertThat(transferService.detailGroups).hasSize(2);
        assertThat(transferService.detailGroups.stream()
                .flatMap(List::stream)
                .map(InvTransferDetail::getAmount))
                .containsExactlyInAnyOrder(new BigDecimal("10.00"),
                        new BigDecimal("18.00"));
        assertThat(transferService.detailGroups.stream()
                .flatMap(List::stream)
                .filter(detail -> detail.getQuantity()
                        .compareTo(new BigDecimal("3.00")) == 0)
                .findFirst().orElseThrow().getCostPrice())
                .isEqualByComparingTo("3.33");
    }

    @Test
    @DisplayName("已发通知明细成本未知时禁止创建调拨")
    void shouldRejectCrossStoreTransferWhenFrozenCostIsUnknown()
    {
        InvDeliveryNoticeServiceImpl service = new InvDeliveryNoticeServiceImpl();
        RecordingTransferService transferService = new RecordingTransferService("TF202606050020");
        ReflectionTestUtils.setField(service, "transferService", transferService);

        InvDeliveryNotice notice = new InvDeliveryNotice();
        notice.setNoticeId(101L);
        notice.setShopDeptId(201L);
        InvSalesOrder salesOrder = new InvSalesOrder();
        salesOrder.setOrderId(10L);
        salesOrder.setStatus("noticed");
        salesOrder.setTargetDeptId(202L);
        InvDeliveryNoticeDetail detail = frozenNoticeDetail(401L, 301L,
                "1.00", null);

        assertThatThrownBy(() -> service.createCrossStoreTransferForReceipt(
                notice, salesOrder, List.of(detail), Collections.emptyMap(),
                201L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("成本未知");
        assertThat(transferService.orders).isEmpty();
    }

    @Test
    @DisplayName("同店发货完成时返回普通成功提示")
    void shouldReturnNormalMessageForSameStoreDelivery()
    {
        assertThat(InvDeliveryNoticeServiceImpl.buildDeliveryResultMessage(false, null))
                .isEqualTo("发货成功");
    }

    @Test
    @DisplayName("发货仓库优先使用请求仓库并在缺失时提示")
    void shouldResolveDeliveryWarehouseId()
    {
        InvDeliveryNotice notice = new InvDeliveryNotice();
        notice.setWarehouseId(201L);
        InvDeliverRequest request = new InvDeliverRequest();
        request.setWarehouseId(202L);

        assertThat(InvDeliveryNoticeServiceImpl.resolveDeliveryWarehouseId(notice, request)).isEqualTo(202L);

        request.setWarehouseId(null);
        assertThat(InvDeliveryNoticeServiceImpl.resolveDeliveryWarehouseId(notice, request)).isEqualTo(201L);

        notice.setWarehouseId(null);
        assertThatThrownBy(() -> InvDeliveryNoticeServiceImpl.resolveDeliveryWarehouseId(notice, request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("发货仓库不能为空");
    }

    @Test
    @DisplayName("发货通知仓库取所有待发销售明细的唯一仓库")
    void shouldResolveNoticeWarehouseIdFromPendingSalesDetails()
    {
        InvSalesDetail first = new InvSalesDetail();
        first.setWarehouseId(200L);
        InvSalesDetail second = new InvSalesDetail();
        second.setWarehouseId(200L);

        assertThat(InvDeliveryNoticeServiceImpl.resolveNoticeWarehouseId(List.of(first, second))).isEqualTo(200L);

        second.setWarehouseId(201L);
        assertThat(InvDeliveryNoticeServiceImpl.resolveNoticeWarehouseId(List.of(first, second))).isNull();

        second.setWarehouseId(null);
        assertThat(InvDeliveryNoticeServiceImpl.resolveNoticeWarehouseId(List.of(first, second))).isNull();
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({ "1.00, 8.00, 8.00, 4.00", "0.50, 0.01, 0.01, 4.50" })
    @DisplayName("仓库上下文可以按通用物料键发出礼盒")
    void shouldDeliverStoreNoticeFromWarehouseContext(String quantity, String unitCost, String exactCost, String afterQuantity)
    {
        SecurityContextHolder.setUserId("2");
        SecurityContextHolder.setUserName("operator");
        InvDeliveryNoticeServiceImpl service = new InvDeliveryNoticeServiceImpl();
        InvDeliveryNoticeMapper noticeMapper = mock(InvDeliveryNoticeMapper.class);
        InvDeliveryNoticeDetailMapper noticeDetailMapper = mock(InvDeliveryNoticeDetailMapper.class);
        when(noticeDetailMapper.selectInvDeliveryNoticeDetailByNoticeId(1L))
                .thenAnswer(invocation -> noticeDetailMapper.selectInvDeliveryNoticeDetailByNoticeIdForUpdate(1L));
        InvSalesDetailMapper salesDetailMapper = mock(InvSalesDetailMapper.class);
        InvSalesOrderMapper salesOrderMapper = mock(InvSalesOrderMapper.class);
        when(salesOrderMapper.updateInvSalesOrder(any())).thenReturn(1);
        InvStockMapper stockMapper = mock(InvStockMapper.class);
        InvStockLogMapper stockLogMapper = mock(InvStockLogMapper.class);
        when(stockLogMapper.insertInvStockLog(any())).thenReturn(1);
        InvOutboundRecordMapper outboundRecordMapper = mock(InvOutboundRecordMapper.class);
        when(outboundRecordMapper.insertInvOutboundRecord(any())).thenReturn(1);

        InvDeliveryNotice notice = new InvDeliveryNotice();
        notice.setNoticeId(1L);
        notice.setSalesOrderId(10L);
        notice.setSalesOrderNo("SO001");
        notice.setStatus("pending");
        notice.setShopDeptId(100L);
        when(noticeMapper.selectInvDeliveryNoticeById(1L)).thenReturn(notice);
        when(noticeMapper.selectInvDeliveryNoticeByIdForUpdate(1L)).thenReturn(notice);

        InvDeliveryNoticeDetail detail = new InvDeliveryNoticeDetail();
        detail.setDetailId(11L);
        detail.setSalesDetailId(21L);
        detail.setItemType("gift");
        detail.setItemId(501L);
        detail.setItemName("测试礼盒");
        detail.setProductName("测试礼盒");
        detail.setNoticeQty(new BigDecimal(quantity));
        detail.setDeliveredQty(BigDecimal.ZERO);
        detail.setDeliveredCostAmount(BigDecimal.ZERO);
        detail.setWarehouseId(200L);

        InvDeliveryNoticeDetail deliveredDetail = new InvDeliveryNoticeDetail();
        deliveredDetail.setDetailId(11L);
        deliveredDetail.setSalesDetailId(21L);
        deliveredDetail.setItemType("gift");
        deliveredDetail.setItemId(501L);
        deliveredDetail.setItemName("测试礼盒");
        deliveredDetail.setProductName("测试礼盒");
        deliveredDetail.setNoticeQty(new BigDecimal(quantity));
        deliveredDetail.setDeliveredQty(new BigDecimal(quantity));
        deliveredDetail.setDeliveredCostAmount(new BigDecimal(exactCost));
        deliveredDetail.setWarehouseId(200L);
        when(noticeDetailMapper.selectInvDeliveryNoticeDetailByNoticeIdForUpdate(1L))
                .thenReturn(List.of(detail))
                .thenReturn(List.of(detail))
                .thenReturn(List.of(detail))
                .thenReturn(List.of(deliveredDetail));

        InvSalesDetail salesDetail = new InvSalesDetail();
        salesDetail.setDetailId(21L);
        salesDetail.setItemType("gift");
        salesDetail.setItemId(501L);
        salesDetail.setItemName("测试礼盒");
        // 销售明细后来被改仓，也不能覆盖通知创建时冻结的仓库。
        salesDetail.setWarehouseId(999L);
        salesDetail.setQuantity(new BigDecimal(quantity));
        salesDetail.setDeliveredQuantity(BigDecimal.ZERO);
        when(salesDetailMapper.selectInvSalesDetailByOrderIdForUpdate(10L)).thenReturn(List.of(salesDetail));

        InvSalesOrder salesOrder = new InvSalesOrder();
        salesOrder.setOrderId(10L);
        salesOrder.setStatus("noticed");
        salesOrder.setOrderId(10L);
        salesOrder.setShopDeptId(100L);
        salesOrder.setTargetDeptId(100L);
        when(salesOrderMapper.selectInvSalesOrderByIdForUpdate(10L)).thenReturn(salesOrder);

        InvStock stock = new InvStock();
        stock.setStockId(31L);
        stock.setVersion(0L);
        stock.setCurrentQuantity(new BigDecimal("5.00"));
        stock.setAvailableQuantity(new BigDecimal("5.00"));
        stock.setCostPrice(new BigDecimal(unitCost));
        stock.setTotalCost(stock.getCurrentQuantity().multiply(stock.getCostPrice()).setScale(2, java.math.RoundingMode.HALF_UP));
        when(stockMapper.selectInvStockByItemShopWarehouseForUpdate("gift", 501L, 200L, 200L)).thenReturn(stock);

        InvStock updatedStock = new InvStock();
        updatedStock.setStockId(31L);
        updatedStock.setCurrentQuantity(new BigDecimal(afterQuantity));
        when(stockMapper.deductInvStockWithCost(eq(31L), eq(0L), any(BigDecimal.class),
                any(BigDecimal.class), eq("operator"))).thenReturn(1);
        when(stockMapper.selectInvStockById(31L)).thenReturn(updatedStock);
        when(noticeDetailMapper.accumulateDelivery(11L, BigDecimal.ZERO,
                new BigDecimal(quantity), new BigDecimal(exactCost)))
                .thenReturn(1);

        InvDeliverItem item = new InvDeliverItem();
        item.setDetailId(11L);
        item.setDeliverQuantity(new BigDecimal(quantity));
        InvDeliverRequest request = new InvDeliverRequest();
        request.setWarehouseId(200L);
        request.setItems(List.of(item));

        ReflectionTestUtils.setField(service, "noticeMapper", noticeMapper);
        ReflectionTestUtils.setField(service, "noticeDetailMapper", noticeDetailMapper);
        ReflectionTestUtils.setField(service, "salesDetailMapper", salesDetailMapper);
        ReflectionTestUtils.setField(service, "salesOrderMapper", salesOrderMapper);
        ReflectionTestUtils.setField(service, "stockMapper", stockMapper);
        ReflectionTestUtils.setField(service, "stockLogMapper", stockLogMapper);
        ReflectionTestUtils.setField(service, "outboundRecordMapper", outboundRecordMapper);
        ReflectionTestUtils.setField(service, "deptScopeMapper", new DeliveryDeptScopeMapper());

        assertThat(service.deliverNotice(1L, request, 200L)).isEqualTo("发货成功");

        verify(stockMapper).deductInvStockWithCost(eq(31L), eq(0L), eq(new BigDecimal(quantity)),
                eq(new BigDecimal(exactCost)), eq("operator"));
        verify(outboundRecordMapper).insertInvOutboundRecord(argThat(record ->
                "gift".equals(record.getItemType())
                        && Long.valueOf(501L).equals(record.getItemId())
                        && record.getProductId() == null
                        && Long.valueOf(1L).equals(record.getNoticeId())
                        && Long.valueOf(11L).equals(record.getNoticeDetailId())
                        && record.getCostPrice().compareTo(new BigDecimal(unitCost)) == 0
                        && record.getCostAmount().compareTo(new BigDecimal(exactCost)) == 0));
        verify(stockLogMapper).insertInvStockLog(argThat(log ->
                "gift".equals(log.getItemType())
                        && Long.valueOf(501L).equals(log.getItemId())
                        && log.getProductId() == null
                        && log.getCostAmount().compareTo(new BigDecimal(exactCost)) == 0
                        && log.getChangeQuantity().compareTo(new BigDecimal(quantity).negate()) == 0
                        && log.getBeforeQuantity().compareTo(new BigDecimal("5.00")) == 0
                        && log.getAfterQuantity().compareTo(new BigDecimal(afterQuantity)) == 0));
        verify(noticeMapper).updateInvDeliveryNotice(argThat(update ->
                Long.valueOf(1L).equals(update.getNoticeId())
                        && "completed".equals(update.getStatus())));
        verify(salesOrderMapper).updateInvSalesOrder(argThat(update ->
                Long.valueOf(10L).equals(update.getOrderId())
                        && "delivered".equals(update.getStatus())));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "-1.00")
    @DisplayName("库存成本未知或为负时在任何出库写入前失败")
    void shouldFailClosedBeforeOutboundWhenStockCostIsInvalid(
            String stockCost)
    {
        SecurityContextHolder.setUserId("2");
        SecurityContextHolder.setUserName("operator");
        InvDeliveryNoticeServiceImpl service = new InvDeliveryNoticeServiceImpl();
        InvDeliveryNoticeMapper noticeMapper = mock(InvDeliveryNoticeMapper.class);
        InvDeliveryNoticeDetailMapper noticeDetailMapper = mock(InvDeliveryNoticeDetailMapper.class);
        when(noticeDetailMapper.selectInvDeliveryNoticeDetailByNoticeId(1L))
                .thenAnswer(invocation -> noticeDetailMapper.selectInvDeliveryNoticeDetailByNoticeIdForUpdate(1L));
        InvSalesDetailMapper salesDetailMapper = mock(InvSalesDetailMapper.class);
        InvSalesOrderMapper salesOrderMapper = mock(InvSalesOrderMapper.class);
        when(salesOrderMapper.updateInvSalesOrder(any())).thenReturn(1);
        InvStockMapper stockMapper = mock(InvStockMapper.class);
        InvStockLogMapper stockLogMapper = mock(InvStockLogMapper.class);
        when(stockLogMapper.insertInvStockLog(any())).thenReturn(1);
        InvOutboundRecordMapper outboundRecordMapper = mock(InvOutboundRecordMapper.class);
        when(outboundRecordMapper.insertInvOutboundRecord(any())).thenReturn(1);

        InvDeliveryNotice notice = new InvDeliveryNotice();
        notice.setNoticeId(1L);
        notice.setSalesOrderId(10L);
        notice.setSalesOrderNo("SO001");
        notice.setStatus("pending");
        notice.setShopDeptId(100L);
        when(noticeMapper.selectInvDeliveryNoticeById(1L)).thenReturn(notice);
        when(noticeMapper.selectInvDeliveryNoticeByIdForUpdate(1L)).thenReturn(notice);

        InvDeliveryNoticeDetail detail = frozenNoticeDetail(21L, 200L,
                "0.00", "0.00");
        detail.setDetailId(11L);
        detail.setNoticeQty(new BigDecimal("1.00"));
        detail.setItemType("gift");
        detail.setItemId(501L);
        detail.setItemName("测试礼盒");
        detail.setProductName("测试礼盒");
        when(noticeDetailMapper.selectInvDeliveryNoticeDetailByNoticeIdForUpdate(1L))
                .thenReturn(List.of(detail));

        InvSalesDetail salesDetail = new InvSalesDetail();
        salesDetail.setDetailId(21L);
        salesDetail.setItemType("gift");
        salesDetail.setItemId(501L);
        salesDetail.setQuantity(BigDecimal.ONE);
        salesDetail.setDeliveredQuantity(BigDecimal.ZERO);
        when(salesDetailMapper.selectInvSalesDetailByOrderIdForUpdate(10L))
                .thenReturn(List.of(salesDetail));

        InvSalesOrder salesOrder = new InvSalesOrder();
        salesOrder.setOrderId(10L);
        salesOrder.setStatus("noticed");
        salesOrder.setTargetDeptId(100L);
        when(salesOrderMapper.selectInvSalesOrderByIdForUpdate(10L))
                .thenReturn(salesOrder);

        InvStock stock = new InvStock();
        stock.setStockId(31L);
        stock.setVersion(0L);
        stock.setCurrentQuantity(new BigDecimal("5.00"));
        stock.setAvailableQuantity(new BigDecimal("5.00"));
        stock.setCostPrice(stockCost == null ? null
                : new BigDecimal(stockCost));
        when(stockMapper.selectInvStockByItemShopWarehouseForUpdate(
                "gift", 501L, 200L, 200L)).thenReturn(stock);

        InvDeliverItem item = new InvDeliverItem();
        item.setDetailId(11L);
        item.setDeliverQuantity(BigDecimal.ONE);
        InvDeliverRequest request = new InvDeliverRequest();
        request.setWarehouseId(200L);
        request.setItems(List.of(item));

        ReflectionTestUtils.setField(service, "noticeMapper", noticeMapper);
        ReflectionTestUtils.setField(service, "noticeDetailMapper", noticeDetailMapper);
        ReflectionTestUtils.setField(service, "salesDetailMapper", salesDetailMapper);
        ReflectionTestUtils.setField(service, "salesOrderMapper", salesOrderMapper);
        ReflectionTestUtils.setField(service, "stockMapper", stockMapper);
        ReflectionTestUtils.setField(service, "stockLogMapper", stockLogMapper);
        ReflectionTestUtils.setField(service, "outboundRecordMapper", outboundRecordMapper);
        ReflectionTestUtils.setField(service, "deptScopeMapper", new DeliveryDeptScopeMapper());

        assertThatThrownBy(() -> service.deliverNotice(1L, request, 200L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("库存成本")
                .hasMessageContaining("无法出库");
        verify(outboundRecordMapper, never()).insertInvOutboundRecord(any());
        verify(stockMapper, never()).deductInvStockWithCost(any(), any(), any(), any(), any());
        verify(stockLogMapper, never()).insertInvStockLog(any());
        verify(noticeDetailMapper, never()).accumulateDelivery(any(), any(), any(), any());
    }

    @Test
    @DisplayName("发货仓库必须在当前选择范围内")
    void shouldRejectOutOfScopeDeliveryWarehouse()
    {
        SecurityContextHolder.setUserId("2");
        SecurityContextHolder.setUserName("operator");
        InvDeliveryNoticeServiceImpl service = new InvDeliveryNoticeServiceImpl();
        InvDeliveryNoticeMapper noticeMapper = mock(InvDeliveryNoticeMapper.class);
        InvDeliveryNoticeDetailMapper noticeDetailMapper = mock(InvDeliveryNoticeDetailMapper.class);
        when(noticeDetailMapper.selectInvDeliveryNoticeDetailByNoticeId(1L))
                .thenAnswer(invocation -> noticeDetailMapper.selectInvDeliveryNoticeDetailByNoticeIdForUpdate(1L));
        InvSalesDetailMapper salesDetailMapper = mock(InvSalesDetailMapper.class);
        InvSalesOrderMapper salesOrderMapper = mock(InvSalesOrderMapper.class);
        when(salesOrderMapper.updateInvSalesOrder(any())).thenReturn(1);

        InvDeliveryNotice notice = new InvDeliveryNotice();
        notice.setNoticeId(1L);
        notice.setSalesOrderId(10L);
        notice.setStatus("pending");
        notice.setShopDeptId(100L);
        when(noticeMapper.selectInvDeliveryNoticeById(1L)).thenReturn(notice);
        when(noticeMapper.selectInvDeliveryNoticeByIdForUpdate(1L)).thenReturn(notice);

        InvDeliveryNoticeDetail detail = new InvDeliveryNoticeDetail();
        detail.setDetailId(11L);
        detail.setSalesDetailId(21L);
        detail.setProductId(101L);
        detail.setProductName("测试商品");
        detail.setNoticeQty(new BigDecimal("1.00"));
        detail.setDeliveredQty(BigDecimal.ZERO);
        when(noticeDetailMapper.selectInvDeliveryNoticeDetailByNoticeIdForUpdate(1L)).thenReturn(List.of(detail));
        when(salesDetailMapper.selectInvSalesDetailByOrderIdForUpdate(10L)).thenReturn(Collections.emptyList());
        InvSalesOrder original = new InvSalesOrder(); original.setOrderId(10L); original.setStatus("noticed");
        when(salesOrderMapper.selectInvSalesOrderByIdForUpdate(10L)).thenReturn(original);

        InvDeliverItem item = new InvDeliverItem();
        item.setDetailId(11L);
        item.setDeliverQuantity(new BigDecimal("1.00"));
        InvDeliverRequest request = new InvDeliverRequest();
        request.setWarehouseId(200L);
        request.setItems(List.of(item));

        ReflectionTestUtils.setField(service, "noticeMapper", noticeMapper);
        ReflectionTestUtils.setField(service, "noticeDetailMapper", noticeDetailMapper);
        ReflectionTestUtils.setField(service, "salesDetailMapper", salesDetailMapper);
        ReflectionTestUtils.setField(service, "salesOrderMapper", salesOrderMapper);
        ReflectionTestUtils.setField(service, "deptScopeMapper", new DeliveryDeptScopeMapper());

        assertThatThrownBy(() -> service.deliverNotice(1L, request, 100L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("无权操作该发货仓库");
    }

    @Test
    @DisplayName("仓库上下文可以取消该仓库发货的门店发货通知")
    void shouldCancelStoreNoticeFromWarehouseContext()
    {
        SecurityContextHolder.setUserId("2");
        SecurityContextHolder.setUserName("operator");
        InvDeliveryNoticeServiceImpl service = new InvDeliveryNoticeServiceImpl();
        InvDeliveryNoticeMapper noticeMapper = mock(InvDeliveryNoticeMapper.class);
        InvSalesDetailMapper salesDetailMapper = mock(InvSalesDetailMapper.class);
        InvSalesOrderMapper salesOrderMapper = mock(InvSalesOrderMapper.class);
        when(salesOrderMapper.updateInvSalesOrder(any())).thenReturn(1);

        InvDeliveryNotice notice = new InvDeliveryNotice();
        notice.setNoticeId(1L);
        notice.setSalesOrderId(10L);
        notice.setStatus("pending");
        notice.setShopDeptId(100L);
        notice.setWarehouseId(200L);
        when(noticeMapper.selectInvDeliveryNoticeById(1L)).thenReturn(notice);
        when(noticeMapper.selectInvDeliveryNoticeByIdForUpdate(1L)).thenReturn(notice);

        InvSalesOrder lockedSalesOrder = new InvSalesOrder();
        lockedSalesOrder.setOrderId(10L);
        lockedSalesOrder.setStatus("noticed");
        when(salesOrderMapper.selectInvSalesOrderByIdForUpdate(10L)).thenReturn(lockedSalesOrder);

        InvSalesDetail salesDetail = new InvSalesDetail();
        salesDetail.setQuantity(new BigDecimal("2.00"));
        salesDetail.setDeliveredQuantity(BigDecimal.ZERO);
        when(salesDetailMapper.selectInvSalesDetailByOrderIdForUpdate(10L)).thenReturn(List.of(salesDetail));

        InvDeliveryNotice cancelledNotice = new InvDeliveryNotice();
        cancelledNotice.setStatus("cancelled");
        when(noticeMapper.selectInvDeliveryNoticeBySalesOrderIdForUpdate(10L)).thenReturn(List.of(cancelledNotice));

        ReflectionTestUtils.setField(service, "noticeMapper", noticeMapper);
        ReflectionTestUtils.setField(service, "salesDetailMapper", salesDetailMapper);
        ReflectionTestUtils.setField(service, "salesOrderMapper", salesOrderMapper);
        ReflectionTestUtils.setField(service, "deptScopeMapper", new DeliveryDeptScopeMapper());

        service.cancelNotice(1L, 200L);
        org.mockito.InOrder locks = org.mockito.Mockito.inOrder(salesOrderMapper, noticeMapper);
        locks.verify(salesOrderMapper).selectInvSalesOrderByIdForUpdate(10L);
        locks.verify(noticeMapper).selectInvDeliveryNoticeByIdForUpdate(1L);

        verify(noticeMapper).updateInvDeliveryNotice(argThat(update ->
                Long.valueOf(1L).equals(update.getNoticeId())
                        && "cancelled".equals(update.getStatus())
                        && "operator".equals(update.getUpdateBy())));
        verify(salesOrderMapper).updateInvSalesOrder(argThat(update ->
                Long.valueOf(10L).equals(update.getOrderId())
                        && "submitted".equals(update.getStatus())));
    }

    private static InvDeliveryNoticeDetail frozenNoticeDetail(
            Long salesDetailId, Long warehouseId, String deliveredQuantity,
            String deliveredCostAmount)
    {
        InvDeliveryNoticeDetail detail = new InvDeliveryNoticeDetail();
        detail.setSalesDetailId(salesDetailId);
        detail.setWarehouseId(warehouseId);
        detail.setItemType("product");
        detail.setItemId(salesDetailId);
        detail.setItemCode("SKU-" + salesDetailId);
        detail.setItemName("测试商品" + salesDetailId);
        detail.setProductId(salesDetailId);
        detail.setProductName(detail.getItemName());
        detail.setNoticeQty(new BigDecimal(deliveredQuantity));
        detail.setDeliveredQty(new BigDecimal(deliveredQuantity));
        detail.setDeliveredCostAmount(deliveredCostAmount == null
                ? null : new BigDecimal(deliveredCostAmount));
        return detail;
    }

    private static class RecordingTransferService implements IInvTransferService
    {
        private final String orderNo;
        private final List<InvTransferOrder> orders = new java.util.ArrayList<>();
        private final List<List<InvTransferDetail>> detailGroups = new java.util.ArrayList<>();
        private InvTransferOrder order;
        private List<InvTransferDetail> details = Collections.emptyList();
        private Long selectedShopDeptId;
        private boolean createdDeliveredCrossStoreTransfer;

        private RecordingTransferService(String orderNo)
        {
            this.orderNo = orderNo;
        }

        @Override
        public InvTransferOrder saveDraft(InvTransferOrder order, List<InvTransferDetail> details, Long selectedShopDeptId)
        {
            throw new AssertionError("delivery notice should not save transfer drafts directly");
        }

        @Override
        public InvTransferOrder submitTransfer(InvTransferOrder order, List<InvTransferDetail> details, Long selectedShopDeptId)
        {
            throw new AssertionError("delivery notice should ask transfer service for a delivered cross-store transfer");
        }

        @Override
        public InvTransferOrder createDeliveredCrossStoreTransfer(InvTransferOrder order, List<InvTransferDetail> details,
                Long selectedShopDeptId)
        {
            this.order = order;
            this.details = details;
            this.selectedShopDeptId = selectedShopDeptId;
            this.createdDeliveredCrossStoreTransfer = true;
            this.orders.add(order);
            this.detailGroups.add(details);

            InvTransferOrder result = new InvTransferOrder();
            int callIndex = orders.size() - 1;
            if (callIndex == 0)
            {
                result.setOrderNo(orderNo);
            }
            else
            {
                int width = 0;
                int split = orderNo.length();
                while (split > 0 && Character.isDigit(orderNo.charAt(split - 1)))
                {
                    split--;
                    width++;
                }
                long sequence = Long.parseLong(orderNo.substring(split))
                        + callIndex;
                result.setOrderNo(orderNo.substring(0, split)
                        + String.format("%0" + width + "d", sequence));
            }
            return result;
        }

        @Override
        public InvTransferOrder getTransferDetail(Long transferId, Long selectedShopDeptId)
        {
            throw new AssertionError("unexpected getTransferDetail");
        }

        @Override
        public com.erp.inventory.domain.vo.InvTransferApprovalTrack getApprovalTrack(
                Long transferId, Long selectedShopDeptId)
        {
            throw new AssertionError("unexpected getApprovalTrack");
        }

        @Override
        public com.erp.inventory.domain.vo.InvTransferRevisionHistoryVo getRevisionHistory(
                Long transferId, Long selectedShopDeptId)
        {
            throw new AssertionError("unexpected getRevisionHistory");
        }

        @Override
        public List<InvTransferOrder> selectTransferList(InvTransferOrder order, Long selectedShopDeptId)
        {
            throw new AssertionError("unexpected selectTransferList");
        }

        @Override
        public com.erp.inventory.domain.vo.InvTransferOpsSummaryVo selectOpsSummary(Long selectedShopDeptId)
        {
            throw new AssertionError("unexpected selectOpsSummary");
        }

        @Override
        public void deliverTransfer(Long transferId, Long selectedShopDeptId)
        {
            throw new AssertionError("delivery notice must not call transfer out and double-deduct stock");
        }

        @Override
        public void deliverTransfer(Long transferId, InvDeliverRequest deliverRequest, Long selectedShopDeptId)
        {
            throw new AssertionError("delivery notice must not call transfer out and double-deduct stock");
        }

        @Override
        public com.erp.inventory.domain.vo.InvTransferSourceConfirmResult confirmSourceTransfer(
                Long transferId,
                com.erp.inventory.domain.dto.InvTransferSourceConfirmRequest request,
                Long selectedShopDeptId)
        {
            throw new AssertionError("unexpected confirmSourceTransfer");
        }

        @Override
        public void cancelTransfer(Long transferId, Long selectedShopDeptId)
        {
            throw new AssertionError("unexpected cancelTransfer");
        }

        @Override
        public String withdrawApproval(Long transferId,
                Long selectedShopDeptId)
        {
            throw new AssertionError("unexpected withdrawApproval");
        }

        @Override
        public InvTransferOrder createFromPurchase(Long purchaseId, Long selectedShopDeptId)
        {
            throw new AssertionError("unexpected createFromPurchase");
        }

        @Override
        public InvTransferOrder getTransferByPurchaseId(Long purchaseId)
        {
            throw new AssertionError("unexpected getTransferByPurchaseId");
        }

        @Override
        public void deleteTransfer(Long transferId, Long selectedShopDeptId)
        {
            throw new AssertionError("unexpected deleteTransfer");
        }

        @Override
        public void receiveTransfer(Long transferId, Long selectedShopDeptId)
        {
            throw new AssertionError("unexpected receiveTransfer");
        }

        @Override
        public void receiveTransferShipment(Long shipmentId, InvReceiveRequest receiveRequest, Long selectedShopDeptId)
        {
            throw new AssertionError("unexpected receiveTransferShipment");
        }

        @Override
        public List<com.erp.inventory.domain.InvTransferDiscrepancy> getTransferDiscrepancies(
                Long transferId, Long selectedShopDeptId)
        {
            throw new AssertionError("unexpected getTransferDiscrepancies");
        }

        @Override
        public com.erp.inventory.domain.InvTransferDiscrepancy getTransferDiscrepancy(
                Long discrepancyId, Long selectedShopDeptId)
        {
            throw new AssertionError("unexpected getTransferDiscrepancy");
        }

        @Override
        public void resolveTransferDiscrepancy(Long discrepancyId,
                com.erp.inventory.domain.dto.InvTransferDiscrepancyResolveRequest request,
                Long selectedShopDeptId)
        {
            throw new AssertionError("unexpected resolveTransferDiscrepancy");
        }

    }

    private static class DeliveryDeptScopeMapper implements InvDeptScopeMapper
    {
        @Override public List<Long> selectUserAuthorizedInventoryDeptIds(Long userId) { return java.util.Collections.emptyList(); }
        @Override public List<Long> selectAllActiveInventoryDeptIds() { return java.util.Collections.emptyList(); }
        @Override
        public List<Long> selectSubDeptIds(Long deptId)
        {
            return Collections.singletonList(deptId);
        }

        @Override
        public List<Long> selectRelatedDeptIds(Long deptId)
        {
            return Collections.singletonList(deptId);
        }

        @Override
        public List<Long> selectActiveRelatedDeptIdsForReplenishment(
                Long deptId)
        {
            return selectRelatedDeptIds(deptId);
        }

        @Override
        public List<Long> selectAncestorDeptIds(Long deptId)
        {
            return Collections.emptyList();
        }

        @Override
        public Long selectRawBusinessRootDeptId(Long deptId)
        {
            return deptId;
        }

        @Override
        public List<Long> selectUserStoreScopeDeptIds(Long userId)
        {
            return Collections.emptyList();
        }

        @Override
        public List<Long> selectAllStoreDeptIds()
        {
            return Collections.emptyList();
        }

        @Override
        public int countDeptInScope(Long scopeDeptId, Long targetDeptId)
        {
            return scopeDeptId != null && scopeDeptId.equals(targetDeptId) ? 1 : 0;
        }

        @Override
        public int countUserShopScope(Long userId, Long deptId)
        {
            return 1;
        }

        @Override
        public String selectDeptNameById(Long deptId)
        {
            return "测试组织";
        }

        @Override
        public String selectDeptTypeById(Long deptId)
        {
            return Long.valueOf(200L).equals(deptId) ? "WAREHOUSE" : "STORE";
        }
    }
}
