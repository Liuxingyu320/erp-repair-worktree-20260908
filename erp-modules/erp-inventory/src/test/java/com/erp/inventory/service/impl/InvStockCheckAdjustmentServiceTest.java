package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.inventory.domain.InvStock;
import com.erp.inventory.domain.InvStockCheck;
import com.erp.inventory.domain.InvStockCheckDetail;
import com.erp.inventory.domain.InvStockLog;
import com.erp.inventory.constant.InvStatusConstants;
import com.erp.inventory.mapper.InvStockLogMapper;
import com.erp.inventory.mapper.InvStockMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("盘点库存调整服务")
class InvStockCheckAdjustmentServiceTest
{
    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("库存快照变化时返回失效证据且不写库存")
    void shouldReturnSnapshotChangesWithoutMutatingStock()
    {
        InvStockMapper stockMapper = mock(InvStockMapper.class);
        InvStockLogMapper logMapper = mock(InvStockLogMapper.class);
        InvStockCheckAdjustmentService service = new InvStockCheckAdjustmentService();
        ReflectionTestUtils.setField(service, "stockMapper", stockMapper);
        ReflectionTestUtils.setField(service, "stockLogMapper", logMapper);

        InvStockCheck check = check(1001L, 20L);
        InvStockCheckDetail detail = detail(501L, 101L, "测试茶", "10.00", "8.00");
        InvStock stock = stock(9001L, 101L, 20L, "9.00", "9.00");
        when(stockMapper.selectInvStockByProductShopWarehouseForUpdate(101L, 20L, 20L))
                .thenReturn(stock);

        var result = service.evaluate(check, List.of(detail), true);

        assertThat(result.hasSnapshotChanges()).isTrue();
        assertThat(result.getSnapshotChanges()).singleElement().satisfies(change -> {
            assertThat(change.getProductId()).isEqualTo(101L);
            assertThat(change.getBookQuantity()).isEqualByComparingTo("10.00");
            assertThat(change.getCurrentQuantity()).isEqualByComparingTo("9.00");
        });
        verify(stockMapper, never()).addInvStockWithCost(anyLong(), anyLong(), any(), any(), any());
        verify(stockMapper, never()).deductInvStockWithCost(anyLong(), anyLong(), any(), any(), any());
        verify(logMapper, never()).insertInvStockLog(any());
    }

    @Test
    @DisplayName("快照有效时在同一轮计算中写入盘盈和盘亏")
    void shouldApplyProfitAndLossAfterAllSnapshotsAreValid()
    {
        SecurityContextHolder.setUserName("ops-director");
        InvStockMapper stockMapper = mock(InvStockMapper.class);
        InvStockLogMapper logMapper = mock(InvStockLogMapper.class);
        InvStockCheckAdjustmentService service = new InvStockCheckAdjustmentService();
        ReflectionTestUtils.setField(service, "stockMapper", stockMapper);
        ReflectionTestUtils.setField(service, "stockLogMapper", logMapper);

        InvStockCheck check = check(1001L, 20L);
        InvStockCheckDetail profit = detail(501L, 101L, "盘盈茶", "10.00", "12.00");
        InvStockCheckDetail loss = detail(502L, 202L, "盘亏茶", "10.00", "7.00");
        InvStock profitStock = stock(9001L, 101L, 20L, "10.00", "10.00");
        InvStock lossStock = stock(9002L, 202L, 20L, "10.00", "10.00");
        InvStock profitAfter = stock(9001L, 101L, 20L, "12.00", "12.00");
        InvStock lossAfter = stock(9002L, 202L, 20L, "7.00", "7.00");

        when(stockMapper.selectInvStockByProductShopWarehouseForUpdate(101L, 20L, 20L))
                .thenReturn(profitStock);
        when(stockMapper.selectInvStockByProductShopWarehouseForUpdate(202L, 20L, 20L))
                .thenReturn(lossStock);
        when(stockMapper.addInvStockWithCost(9001L, 1L, new BigDecimal("2.00"),
                new BigDecimal("2.00"), "ops-director")).thenReturn(1);
        when(stockMapper.deductInvStockWithCost(9002L, 1L, new BigDecimal("3.00"),
                new BigDecimal("3.00"), "ops-director")).thenReturn(1);
        when(stockMapper.selectInvStockById(9001L)).thenReturn(profitAfter);
        when(stockMapper.selectInvStockById(9002L)).thenReturn(lossAfter);

        var result = service.evaluate(check, List.of(profit, loss), true);

        assertThat(result.hasSnapshotChanges()).isFalse();
        verify(stockMapper).addInvStockWithCost(9001L, 1L, new BigDecimal("2.00"),
                new BigDecimal("2.00"), "ops-director");
        verify(stockMapper).deductInvStockWithCost(9002L, 1L, new BigDecimal("3.00"),
                new BigDecimal("3.00"), "ops-director");
        ArgumentCaptor<InvStockLog> logCaptor = ArgumentCaptor.forClass(InvStockLog.class);
        verify(logMapper, times(2)).insertInvStockLog(logCaptor.capture());
        assertThat(logCaptor.getAllValues())
                .extracting(InvStockLog::getMovementType)
                .containsExactly(
                        InvStatusConstants.MOVEMENT_STOCK_CHECK_PROFIT,
                        InvStatusConstants.MOVEMENT_STOCK_CHECK_LOSS);
        assertThat(logCaptor.getAllValues())
                .extracting(InvStockLog::getAfterQuantity)
                .containsExactly(new BigDecimal("12.00"), new BigDecimal("7.00"));
        assertThat(result.getAdjustments()).hasSize(2);
        assertThat(result.getAdjustments())
                .extracting(item -> item.getProductName() + ":" + item.getBeforeQuantity()
                        + "->" + item.getAfterQuantity())
                .containsExactly("盘盈茶:10.00->12.00", "盘亏茶:10.00->7.00");
    }

    @Test
    @DisplayName("无差异明细不写库存和库存流水")
    void shouldSkipZeroDifferenceRows()
    {
        SecurityContextHolder.setUserName("counter");
        InvStockMapper stockMapper = mock(InvStockMapper.class);
        InvStockLogMapper logMapper = mock(InvStockLogMapper.class);
        InvStockCheckAdjustmentService service = new InvStockCheckAdjustmentService();
        ReflectionTestUtils.setField(service, "stockMapper", stockMapper);
        ReflectionTestUtils.setField(service, "stockLogMapper", logMapper);

        InvStockCheck check = check(1001L, 20L);
        InvStockCheckDetail detail = detail(501L, 101L, "无差异茶", "10.00", "10.00");
        when(stockMapper.selectInvStockByProductShopWarehouseForUpdate(101L, 20L, 20L))
                .thenReturn(stock(9001L, 101L, 20L, "10.00", "10.00"));

        var result = service.evaluate(check, List.of(detail), true);

        assertThat(result.hasSnapshotChanges()).isFalse();
        verify(stockMapper, never()).addInvStockWithCost(anyLong(), anyLong(), any(), any(), any());
        verify(stockMapper, never()).deductInvStockWithCost(anyLong(), anyLong(), any(), any(), any());
        verify(logMapper, never()).insertInvStockLog(any());
    }

    private static InvStockCheck check(Long checkId, Long deptId)
    {
        InvStockCheck check = new InvStockCheck();
        check.setCheckId(checkId);
        check.setCheckNo("SC202607100001");
        check.setShopDeptId(deptId);
        check.setWarehouseId(deptId);
        return check;
    }

    private static InvStockCheckDetail detail(Long detailId, Long productId, String productName,
            String bookQty, String actualQty)
    {
        InvStockCheckDetail detail = new InvStockCheckDetail();
        detail.setDetailId(detailId);
        detail.setProductId(productId);
        detail.setProductName(productName);
        detail.setBookQty(new BigDecimal(bookQty));
        detail.setActualQty(new BigDecimal(actualQty));
        detail.setDiffQty(new BigDecimal(actualQty).subtract(new BigDecimal(bookQty)));
        detail.setCostPrice(BigDecimal.ONE);
        return detail;
    }

    private static InvStock stock(Long stockId, Long productId, Long deptId,
            String currentQty, String availableQty)
    {
        InvStock stock = new InvStock();
        stock.setStockId(stockId);
        stock.setProductId(productId);
        stock.setShopDeptId(deptId);
        stock.setWarehouseId(deptId);
        stock.setCurrentQuantity(new BigDecimal(currentQty));
        stock.setAvailableQuantity(new BigDecimal(availableQty));
        stock.setCostPrice(BigDecimal.ONE);
        stock.setVersion(1L);
        return stock;
    }
}
