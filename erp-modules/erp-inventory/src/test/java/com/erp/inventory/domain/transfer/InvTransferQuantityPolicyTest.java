package com.erp.inventory.domain.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.transfer.InvTransferQuantityPolicy.FulfillmentSnapshot;
import com.erp.inventory.domain.transfer.InvTransferQuantityPolicy.ReceiptBreakdown;

class InvTransferQuantityPolicyTest
{
    @Test
    void shouldCalculateRemainingShipmentUsingDecimals()
    {
        assertThat(InvTransferQuantityPolicy.remainingToShip(
                decimal("10.5000"), decimal("3.1250")))
                .isEqualByComparingTo("7.3750");
    }

    @Test
    void shouldRejectShipmentBeyondApprovedQuantity()
    {
        assertThatThrownBy(() ->
                InvTransferQuantityPolicy.requireShipmentWithinApproved(
                        decimal("10"), decimal("8"), decimal("2.01")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("超过剩余待发数量");
    }

    @Test
    void shouldClassifyReceiptAndDeriveShortage()
    {
        ReceiptBreakdown breakdown =
                InvTransferQuantityPolicy.classifyReceipt(
                        decimal("10"), decimal("6"), decimal("1"),
                        decimal("2"));

        assertThat(breakdown.shortageQuantity())
                .isEqualByComparingTo("1");
        assertThat(breakdown.classifiedQuantity())
                .isEqualByComparingTo("9");
        assertThat(breakdown.hasDiscrepancy()).isTrue();
    }

    @Test
    void shouldAcceptExactNormalReceiptWithoutDiscrepancy()
    {
        ReceiptBreakdown breakdown =
                InvTransferQuantityPolicy.classifyReceipt(
                        decimal("3"), decimal("3"), BigDecimal.ZERO,
                        BigDecimal.ZERO);

        assertThat(breakdown.shortageQuantity()).isZero();
        assertThat(breakdown.hasDiscrepancy()).isFalse();
    }

    @Test
    void shouldRejectNegativeOrOverClassifiedReceipt()
    {
        assertThatThrownBy(() ->
                InvTransferQuantityPolicy.classifyReceipt(decimal("3"),
                        decimal("-1"), BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不能小于0");
        assertThatThrownBy(() ->
                InvTransferQuantityPolicy.classifyReceipt(decimal("3"),
                        decimal("2"), decimal("1"), decimal("0.01")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("超过待收数量");
    }

    @Test
    void shouldValidateBothConservationEquations()
    {
        FulfillmentSnapshot snapshot = new FulfillmentSnapshot(
                decimal("10"), decimal("2"), decimal("1"), decimal("7"),
                decimal("4"), decimal("1"), decimal("0.5"),
                decimal("0.5"), decimal("1"));

        assertThat(snapshot.isComplete()).isFalse();
    }

    @Test
    void shouldRecognizeClosedConservedFulfillment()
    {
        FulfillmentSnapshot snapshot = new FulfillmentSnapshot(
                decimal("10"), BigDecimal.ZERO, decimal("1"), decimal("9"),
                decimal("7"), decimal("1"), decimal("1"),
                BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(snapshot.isComplete()).isTrue();
    }

    @Test
    void shouldRejectBrokenApprovalOrShipmentConservation()
    {
        assertThatThrownBy(() -> new FulfillmentSnapshot(
                decimal("10"), decimal("2"), decimal("1"), decimal("6"),
                decimal("4"), decimal("1"), BigDecimal.ZERO,
                BigDecimal.ZERO, decimal("1")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("审批数量守恒");

        assertThatThrownBy(() -> new FulfillmentSnapshot(
                decimal("10"), decimal("2"), decimal("1"), decimal("7"),
                decimal("4"), decimal("1"), BigDecimal.ZERO,
                BigDecimal.ZERO, decimal("1")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("发货数量守恒");
    }

    private static BigDecimal decimal(String value)
    {
        return new BigDecimal(value);
    }
}
