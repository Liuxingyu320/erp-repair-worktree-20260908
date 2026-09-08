package com.erp.inventory.service.impl;

import java.util.Set;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.constant.InvStatusConstants;

public final class InvStateGuard
{
    private InvStateGuard() {}

    public static void require(String actual, Set<String> allowed, String action)
    {
        if (!allowed.contains(actual))
        {
            throw new ServiceException("当前状态不允许" + action + "，当前状态: " + actual);
        }
    }

    public static void requireDraftForEdit(String actual)
    {
        require(actual, Set.of(InvStatusConstants.DRAFT), "修改");
    }

    public static void requireSubmittedForReceive(String actual)
    {
        require(actual, Set.of(InvStatusConstants.SUBMITTED), "收货");
    }

    public static void requireSubmittedForDeliver(String actual)
    {
        require(actual, Set.of(InvStatusConstants.SUBMITTED), "出库");
    }

    public static void requireApprovedForTransferDeliver(String actual)
    {
        require(actual, Set.of(InvStatusConstants.APPROVED), "出库");
    }

    public static void requireSubmittedForQualityCheck(String actual)
    {
        require(actual, Set.of(InvStatusConstants.SUBMITTED), "质检");
    }

    public static void requirePendingQualityCheck(String actual)
    {
        require(actual, Set.of(InvStatusConstants.QC_PENDING), "质检");
    }

    public static void requireCancelableDocument(String actual)
    {
        require(actual, Set.of(InvStatusConstants.DRAFT, InvStatusConstants.SUBMITTED), "取消");
    }

    public static void requireDeliveredForReceive(String actual)
    {
        require(actual, Set.of(InvStatusConstants.DELIVERED), "收货");
    }

    public static void requireStockCheckInput(String actual)
    {
        require(actual, Set.of(InvStatusConstants.DRAFT, InvStatusConstants.REJECTED,
                InvStatusConstants.RETURNED), "录入实盘数量");
    }

    public static void requireStockCheckSubmit(String actual)
    {
        require(actual, Set.of(InvStatusConstants.DRAFT, InvStatusConstants.REJECTED,
                InvStatusConstants.RETURNED), "提交盘点");
    }

    public static void requireStockCheckAssignment(String actual)
    {
        require(actual, Set.of(InvStatusConstants.DRAFT, InvStatusConstants.REJECTED,
                InvStatusConstants.RETURNED, InvStatusConstants.INVALIDATED), "调整盘点指派");
    }

    public static boolean isStockCheckEditable(String actual)
    {
        return InvStatusConstants.DRAFT.equals(actual)
                || InvStatusConstants.REJECTED.equals(actual)
                || InvStatusConstants.RETURNED.equals(actual);
    }

    public static void requireStockCheckRestart(String actual)
    {
        require(actual, Set.of(InvStatusConstants.INVALIDATED), "重新盘点");
    }

    public static void requireStockCheckCancelable(String actual)
    {
        require(actual, Set.of(InvStatusConstants.DRAFT, InvStatusConstants.REJECTED,
                InvStatusConstants.RETURNED, InvStatusConstants.PENDING_APPROVAL,
                InvStatusConstants.INVALIDATED), "取消");
    }

    public static void requireDeliveryNoticeCreatable(String actual)
    {
        require(actual, Set.of(InvStatusConstants.SUBMITTED, InvStatusConstants.NOTICED), "生成发货通知");
    }

    public static void requireDeliveryNoticeDeliverable(String actual)
    {
        require(actual, Set.of(InvStatusConstants.PENDING, InvStatusConstants.DELIVERING), "发货");
    }

    public static void requirePendingForCancel(String actual)
    {
        require(actual, Set.of(InvStatusConstants.PENDING), "取消");
    }

    public static void requireSubmittedForReturnConfirm(String actual)
    {
        require(actual, Set.of(InvStatusConstants.SUBMITTED), "确认退货");
    }
}
