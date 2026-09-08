package com.erp.inventory.domain.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.constant.InvStatusConstants;

class InvTransferLifecyclePolicyTest
{
    @Test
    void shouldChoosePartialOrCompleteDeliveryState()
    {
        assertThat(InvTransferLifecyclePolicy.afterDelivery(
                InvStatusConstants.APPROVED, false))
                .isEqualTo(InvStatusConstants.PARTIAL_DELIVERED);
        assertThat(InvTransferLifecyclePolicy.afterDelivery(
                InvStatusConstants.PARTIAL_DELIVERED, true))
                .isEqualTo(InvStatusConstants.DELIVERED);
    }

    @Test
    void shouldRejectDeliveryOutsideApprovedLifecycle()
    {
        assertThatThrownBy(() -> InvTransferLifecyclePolicy.afterDelivery(
                InvStatusConstants.SUBMITTED, false))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("当前状态不允许发货");
    }

    @Test
    void shouldPrioritizeOpenDiscrepancyAfterReceipt()
    {
        assertThat(InvTransferLifecyclePolicy.afterReceipt(
                InvStatusConstants.DELIVERED, true, true))
                .isEqualTo(InvStatusConstants.DISCREPANCY);
    }

    @Test
    void shouldChoosePartialOrCompletedReceiptState()
    {
        assertThat(InvTransferLifecyclePolicy.afterReceipt(
                InvStatusConstants.PARTIAL_DELIVERED, false, false))
                .isEqualTo(InvStatusConstants.PARTIAL_RECEIVED);
        assertThat(InvTransferLifecyclePolicy.afterReceipt(
                InvStatusConstants.PARTIAL_RECEIVED, false, true))
                .isEqualTo(InvStatusConstants.RECEIVED);
    }

    @Test
    void shouldResolveDiscrepancyWithoutLosingRemainingWork()
    {
        assertThat(InvTransferLifecyclePolicy.afterDiscrepancyResolution(
                InvStatusConstants.DISCREPANCY, 1, false, true))
                .isEqualTo(InvStatusConstants.DISCREPANCY);
        assertThat(InvTransferLifecyclePolicy.afterDiscrepancyResolution(
                InvStatusConstants.DISCREPANCY, 0, true, false))
                .isEqualTo(InvStatusConstants.PARTIAL_DELIVERED);
        assertThat(InvTransferLifecyclePolicy.afterDiscrepancyResolution(
                InvStatusConstants.DISCREPANCY, 0, false, false))
                .isEqualTo(InvStatusConstants.CLOSED);
        assertThat(InvTransferLifecyclePolicy.afterDiscrepancyResolution(
                InvStatusConstants.DISCREPANCY, 0, false, true))
                .isEqualTo(InvStatusConstants.RECEIVED);
    }

    @Test
    void shouldRejectInvalidOpenDiscrepancyCount()
    {
        assertThatThrownBy(() ->
                InvTransferLifecyclePolicy.afterDiscrepancyResolution(
                        InvStatusConstants.DISCREPANCY, -1, false, false))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不能小于0");
    }
}
