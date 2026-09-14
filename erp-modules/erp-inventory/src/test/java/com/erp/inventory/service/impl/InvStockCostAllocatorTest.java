package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.InvStock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class InvStockCostAllocatorTest
{
    @ParameterizedTest
    @CsvSource({"2.00,0.67,0.67,0.66", "1.00,0.33,0.33,0.34"})
    void threeBatchesSettleExactly(String pool, String average, String first, String last)
    {
        InvStock stock = stock("3", average, pool);
        for (String amount : new String[]{first, first, last})
        {
            var result = InvStockCostAllocator.allocate(stock, BigDecimal.ONE);
            assertThat(result.amount()).isEqualTo(new BigDecimal(amount));
            stock.setCurrentQuantity(stock.getCurrentQuantity().subtract(BigDecimal.ONE));
            stock.setTotalCost(stock.getTotalCost().subtract(result.amount()));
        }
        assertThat(stock.getTotalCost()).isEqualByComparingTo("0");
    }

    @Test void wholeBatchUsesCostPoolAndNotRoundedAverage()
    { assertThat(InvStockCostAllocator.allocate(stock("3", "0.67", "2.00"), new BigDecimal("3.00")).amount()).isEqualTo(new BigDecimal("2.00")); }

    @Test void exhaustingAvailableDoesNotConsumeLockedStockCost()
    {
        InvStock stock = stock("3", "0.67", "2.00");
        stock.setAvailableQuantity(new BigDecimal("2")); stock.setLockedQuantity(BigDecimal.ONE);
        assertThat(InvStockCostAllocator.allocate(stock, new BigDecimal("2")).amount()).isEqualTo(new BigDecimal("1.34"));
    }

    @Test void fractionalDeductionRoundsHalfUp()
    { assertThat(InvStockCostAllocator.allocate(stock("3", "0.67", "2.00"), new BigDecimal("0.5")).amount()).isEqualTo(new BigDecimal("0.34")); }

    @Test void knownZeroCostIsValid()
    { assertThat(InvStockCostAllocator.allocate(stock("3", "0.00", "0.00"), BigDecimal.ONE).amount()).isEqualTo(new BigDecimal("0.00")); }

    @ParameterizedTest @NullSource @ValueSource(strings={"-1", "0.001"})
    void unknownNegativeOrUnrepresentablePoolIsRejected(String cost)
    {
        InvStock stock = stock("3", "0.67", "2.00"); stock.setTotalCost(cost == null ? null : new BigDecimal(cost));
        assertThatThrownBy(() -> InvStockCostAllocator.allocate(stock, BigDecimal.ONE)).isInstanceOf(ServiceException.class);
    }

    @ParameterizedTest @NullSource @ValueSource(strings={"-1", "0.671"})
    void unknownNegativeOrUnrepresentableAverageIsRejectedEvenForLastBatch(String cost)
    {
        InvStock stock = stock("3", "0.67", "2.00"); stock.setCostPrice(cost == null ? null : new BigDecimal(cost));
        assertThatThrownBy(() -> InvStockCostAllocator.allocate(stock, new BigDecimal("3"))).isInstanceOf(ServiceException.class);
    }

    @ParameterizedTest @NullSource @ValueSource(strings={"0", "-1", "4"})
    void invalidDeductionIsRejected(String quantity)
    {
        BigDecimal amount = quantity == null ? null : new BigDecimal(quantity);
        assertThatThrownBy(() -> InvStockCostAllocator.allocate(stock("3", "0.67", "2.00"), amount)).isInstanceOf(ServiceException.class);
    }

    @Test void partialCostCannotOverdrawPool()
    { assertThatThrownBy(() -> InvStockCostAllocator.allocate(stock("3", "0.67", "0.60"), BigDecimal.ONE)).isInstanceOf(ServiceException.class).hasMessageContaining("剩余成本不足"); }

    @ParameterizedTest @NullSource @ValueSource(strings={"0", "-1"})
    void invalidCurrentQuantityIsRejected(String quantity)
    {
        InvStock stock = stock("3", "0.67", "2.00"); stock.setCurrentQuantity(quantity == null ? null : new BigDecimal(quantity));
        assertThatThrownBy(() -> InvStockCostAllocator.allocate(stock, BigDecimal.ONE)).isInstanceOf(ServiceException.class);
    }

    private InvStock stock(String quantity, String price, String amount)
    {
        InvStock stock = new InvStock(); stock.setCurrentQuantity(new BigDecimal(quantity));
        stock.setCostPrice(new BigDecimal(price)); stock.setTotalCost(new BigDecimal(amount)); return stock;
    }
}
