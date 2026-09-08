package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.InvStock;
import com.erp.inventory.domain.InvStockLog;
import com.erp.inventory.domain.dto.InvExistingStockAdjustRequest;
import com.erp.inventory.mapper.InvStockLogMapper;
import com.erp.inventory.mapper.InvStockMapper;

@DisplayName("现有库存严格调整")
class InvExistingStockAdjustmentServiceTest
{
    private InvStockMapper stockMapper;
    private InvStockLogMapper stockLogMapper;
    private InvStockServiceImpl service;

    @BeforeEach
    void setUp()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("stock-operator");
        stockMapper = mock(InvStockMapper.class);
        stockLogMapper = mock(InvStockLogMapper.class);
        service = new InvStockServiceImpl();
        ReflectionTestUtils.setField(service, "stockMapper", stockMapper);
        ReflectionTestUtils.setField(service, "stockLogMapper", stockLogMapper);
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("按库存标识和预期版本调减并写入结构化流水")
    void shouldAdjustLockedStockRowAndWriteTraceableLog()
    {
        InvStock current = stock("10.0000", "8.0000", 7L, 201L);
        InvStock updated = stock("8.0000", "6.0000", 8L, 201L);
        when(stockMapper.selectInvStockByIdForUpdate(1001L)).thenReturn(current);
        when(stockMapper.deductInvStockWithCost(1001L, 7L, new BigDecimal("2.0000"),
                new BigDecimal("10.000000"), "stock-operator")).thenReturn(1);
        when(stockMapper.selectInvStockById(1001L)).thenReturn(updated);
        when(stockLogMapper.insertInvStockLog(any(InvStockLog.class))).thenReturn(1);

        InvStock result = service.adjustExistingStock(1001L,
                command(7L, "-2.0000", "破损复核后调减"), 201L);

        assertThat(result).isSameAs(updated);
        ArgumentCaptor<InvStockLog> logCaptor = ArgumentCaptor.forClass(InvStockLog.class);
        verify(stockLogMapper).insertInvStockLog(logCaptor.capture());
        InvStockLog log = logCaptor.getValue();
        assertThat(log.getBusinessId()).isEqualTo(1001L);
        assertThat(log.getBusinessNo()).isEqualTo("STOCK-ADJ-1001-V8");
        assertThat(log.getBeforeQuantity()).isEqualByComparingTo("10.0000");
        assertThat(log.getAfterQuantity()).isEqualByComparingTo("8.0000");
        assertThat(log.getChangeQuantity()).isEqualByComparingTo("-2.0000");
        assertThat(log.getRemark()).isEqualTo("破损复核后调减");
    }

    @Test
    @DisplayName("过期版本在写入前被拒绝")
    void shouldRejectStaleVersionBeforeMutation()
    {
        when(stockMapper.selectInvStockByIdForUpdate(1001L))
                .thenReturn(stock("10", "8", 7L, 201L));

        assertThatThrownBy(() -> service.adjustExistingStock(1001L,
                command(6L, "1", "补录盘盈"), 201L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("刷新后重试");

        verify(stockMapper, never()).addInvStockWithCost(any(), any(), any(), any(), any());
        verify(stockMapper, never()).deductInvStockWithCost(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("授权可见但不属于当前组织的库存不能调整")
    void shouldRejectCrossOrganizationMutation()
    {
        when(stockMapper.selectInvStockByIdForUpdate(1001L))
                .thenReturn(stock("10", "8", 7L, 202L));

        assertThatThrownBy(() -> service.adjustExistingStock(1001L,
                command(7L, "1", "跨组织尝试"), 201L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("只能调整当前组织库存");
    }

    @Test
    @DisplayName("零数量、超精度和过短原因均被服务层拒绝")
    void shouldRejectInvalidQuantityAndReason()
    {
        assertThatThrownBy(() -> service.adjustExistingStock(1001L,
                command(0L, "0", "合法原因"), 201L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不能为0");
        assertThatThrownBy(() -> service.adjustExistingStock(1001L,
                command(0L, "0.00001", "合法原因"), 201L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("4位小数");
        assertThatThrownBy(() -> service.adjustExistingStock(1001L,
                command(0L, "1", "短"), 201L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("2到500");
    }

    @Test
    @DisplayName("流水写入失败时命令失败以触发事务回滚")
    void shouldFailCommandWhenLogInsertFails()
    {
        InvStock current = stock("10", "10", 3L, 201L);
        InvStock updated = stock("11", "11", 4L, 201L);
        when(stockMapper.selectInvStockByIdForUpdate(1001L)).thenReturn(current);
        when(stockMapper.addInvStockWithCost(1001L, 3L, BigDecimal.ONE,
                new BigDecimal("5.00"), "stock-operator")).thenReturn(1);
        when(stockMapper.selectInvStockById(1001L)).thenReturn(updated);
        when(stockLogMapper.insertInvStockLog(any(InvStockLog.class))).thenReturn(0);

        assertThatThrownBy(() -> service.adjustExistingStock(1001L,
                command(3L, "1", "复核盘盈"), 201L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("流水写入失败");
    }

    private InvExistingStockAdjustRequest command(long version, String quantity, String reason)
    {
        InvExistingStockAdjustRequest request = new InvExistingStockAdjustRequest();
        request.setExpectedVersion(version);
        request.setAdjustQuantity(new BigDecimal(quantity));
        request.setReason(reason);
        return request;
    }

    private InvStock stock(String currentQuantity, String availableQuantity, long version,
            long organizationId)
    {
        InvStock stock = new InvStock();
        stock.setStockId(1001L);
        stock.setItemType("product");
        stock.setItemId(2001L);
        stock.setProductId(2001L);
        stock.setShopDeptId(organizationId);
        stock.setWarehouseId(organizationId);
        stock.setCurrentQuantity(new BigDecimal(currentQuantity));
        stock.setAvailableQuantity(new BigDecimal(availableQuantity));
        stock.setLockedQuantity(new BigDecimal("2"));
        stock.setCostPrice(new BigDecimal("5.00"));
        stock.setTotalCost(new BigDecimal(currentQuantity).multiply(new BigDecimal("5.00")));
        stock.setVersion(version);
        stock.setBatchNo("B-20260809");
        stock.setLocationCode("A-01");
        return stock;
    }
}
