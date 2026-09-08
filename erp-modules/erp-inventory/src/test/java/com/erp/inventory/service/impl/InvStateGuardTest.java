package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.constant.InvStatusConstants;

@DisplayName("进销存状态流转守卫")
class InvStateGuardTest
{
    @Test
    @DisplayName("当前状态在允许集合内时不抛异常")
    void shouldAllowExpectedState()
    {
        assertThatCode(() -> InvStateGuard.require(
                InvStatusConstants.DRAFT,
                Set.of(InvStatusConstants.DRAFT, InvStatusConstants.CHECKING),
                "取消")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("当前状态不在允许集合内时抛出业务异常")
    void shouldRejectUnexpectedState()
    {
        assertThatThrownBy(() -> InvStateGuard.require(
                InvStatusConstants.COMPLETED,
                Set.of(InvStatusConstants.DRAFT, InvStatusConstants.CHECKING),
                "取消"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("当前状态不允许取消")
                .hasMessageContaining(InvStatusConstants.COMPLETED);
    }

    @Test
    @DisplayName("草稿状态允许修改，已提交状态不允许修改")
    void shouldGuardDraftOnlyEdit()
    {
        assertThatCode(() -> InvStateGuard.requireDraftForEdit(InvStatusConstants.DRAFT))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> InvStateGuard.requireDraftForEdit(InvStatusConstants.SUBMITTED))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("当前状态不允许修改");
    }

    @Test
    @DisplayName("已提交状态允许收货和出库")
    void shouldGuardSubmittedReceiveAndDeliver()
    {
        assertThatCode(() -> InvStateGuard.requireSubmittedForReceive(InvStatusConstants.SUBMITTED))
                .doesNotThrowAnyException();
        assertThatCode(() -> InvStateGuard.requireSubmittedForDeliver(InvStatusConstants.SUBMITTED))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> InvStateGuard.requireSubmittedForReceive(InvStatusConstants.DRAFT))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("当前状态不允许收货");
        assertThatThrownBy(() -> InvStateGuard.requireSubmittedForDeliver(InvStatusConstants.DELIVERED))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("当前状态不允许出库");
    }

    @Test
    @DisplayName("调拨已出库状态允许收货")
    void shouldGuardDeliveredTransferReceive()
    {
        assertThatCode(() -> InvStateGuard.requireDeliveredForReceive(InvStatusConstants.DELIVERED))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> InvStateGuard.requireDeliveredForReceive(InvStatusConstants.SUBMITTED))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("当前状态不允许收货");
    }

    @Test
    @DisplayName("盘点流程仅允许草稿录入")
    void shouldGuardStockCheckFlow()
    {
        assertThatCode(() -> InvStateGuard.requireStockCheckInput(InvStatusConstants.DRAFT))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> InvStateGuard.requireStockCheckInput(InvStatusConstants.CHECKING))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("当前状态不允许录入实盘数量");
    }

    @Test
    @DisplayName("发货通知按待发货或发货中执行发货")
    void shouldGuardDeliveryNoticeFlow()
    {
        assertThatCode(() -> InvStateGuard.requireDeliveryNoticeCreatable(InvStatusConstants.SUBMITTED))
                .doesNotThrowAnyException();
        assertThatCode(() -> InvStateGuard.requireDeliveryNoticeCreatable(InvStatusConstants.NOTICED))
                .doesNotThrowAnyException();
        assertThatCode(() -> InvStateGuard.requireDeliveryNoticeDeliverable(InvStatusConstants.PENDING))
                .doesNotThrowAnyException();
        assertThatCode(() -> InvStateGuard.requireDeliveryNoticeDeliverable(InvStatusConstants.DELIVERING))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> InvStateGuard.requireDeliveryNoticeCreatable(InvStatusConstants.DELIVERED))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("当前状态不允许生成发货通知");
        assertThatThrownBy(() -> InvStateGuard.requireDeliveryNoticeDeliverable(InvStatusConstants.CANCELLED))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("当前状态不允许发货");
    }

    @Test
    @DisplayName("退货确认仅允许已提交状态")
    void shouldGuardReturnConfirm()
    {
        assertThatCode(() -> InvStateGuard.requireSubmittedForReturnConfirm(InvStatusConstants.SUBMITTED))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> InvStateGuard.requireSubmittedForReturnConfirm(InvStatusConstants.RETURNED))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("当前状态不允许确认退货");
    }
}
