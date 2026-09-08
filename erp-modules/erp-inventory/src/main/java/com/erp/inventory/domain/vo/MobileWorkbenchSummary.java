package com.erp.inventory.domain.vo;

import java.io.Serializable;

public class MobileWorkbenchSummary implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long selectedDeptId;
    private String selectedDeptType;
    private Long todoCount;
    private Long lowStockCount;
    private Long pendingReceiveCount;
    private Long pendingDeliverCount;
    private Long pendingApprovalCount;
    private Long purchaseReceiveCount;
    private Long purchaseQcCount;
    private Long purchasePendingCount;
    private Long transferReceiveCount;
    private Long deliveryNoticeCount;
    private Long transferDeliverCount;

    public Long getSelectedDeptId()
    {
        return selectedDeptId;
    }

    public void setSelectedDeptId(Long selectedDeptId)
    {
        this.selectedDeptId = selectedDeptId;
    }

    public String getSelectedDeptType()
    {
        return selectedDeptType;
    }

    public void setSelectedDeptType(String selectedDeptType)
    {
        this.selectedDeptType = selectedDeptType;
    }

    public Long getTodoCount()
    {
        return todoCount;
    }

    public void setTodoCount(Long todoCount)
    {
        this.todoCount = todoCount;
    }

    public Long getLowStockCount()
    {
        return lowStockCount;
    }

    public void setLowStockCount(Long lowStockCount)
    {
        this.lowStockCount = lowStockCount;
    }

    public Long getPendingReceiveCount()
    {
        return pendingReceiveCount;
    }

    public void setPendingReceiveCount(Long pendingReceiveCount)
    {
        this.pendingReceiveCount = pendingReceiveCount;
    }

    public Long getPendingDeliverCount()
    {
        return pendingDeliverCount;
    }

    public void setPendingDeliverCount(Long pendingDeliverCount)
    {
        this.pendingDeliverCount = pendingDeliverCount;
    }

    public Long getPendingApprovalCount()
    {
        return pendingApprovalCount;
    }

    public void setPendingApprovalCount(Long pendingApprovalCount)
    {
        this.pendingApprovalCount = pendingApprovalCount;
    }

    public Long getPurchaseReceiveCount()
    {
        return purchaseReceiveCount;
    }

    public void setPurchaseReceiveCount(Long purchaseReceiveCount)
    {
        this.purchaseReceiveCount = purchaseReceiveCount;
    }

    public Long getPurchaseQcCount() { return purchaseQcCount; }
    public void setPurchaseQcCount(Long purchaseQcCount) { this.purchaseQcCount = purchaseQcCount; }
    public Long getPurchasePendingCount() { return purchasePendingCount; }
    public void setPurchasePendingCount(Long purchasePendingCount) { this.purchasePendingCount = purchasePendingCount; }

    public Long getTransferReceiveCount()
    {
        return transferReceiveCount;
    }

    public void setTransferReceiveCount(Long transferReceiveCount)
    {
        this.transferReceiveCount = transferReceiveCount;
    }

    public Long getDeliveryNoticeCount()
    {
        return deliveryNoticeCount;
    }

    public void setDeliveryNoticeCount(Long deliveryNoticeCount)
    {
        this.deliveryNoticeCount = deliveryNoticeCount;
    }

    public Long getTransferDeliverCount()
    {
        return transferDeliverCount;
    }

    public void setTransferDeliverCount(Long transferDeliverCount)
    {
        this.transferDeliverCount = transferDeliverCount;
    }
}
