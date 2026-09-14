package com.erp.inventory.domain.dto;

import java.math.BigDecimal;
import java.util.Date;

public class InvPurchaseReceiveResult
{
    private String requestId;
    private Long purchaseOrderId;
    private Long warehouseId;
    private Long receiptBatchId;
    private String batchNo;
    private Date arrivedTime;
    private BigDecimal receivedQuantity;

    public String getRequestId()
    {
        return requestId;
    }

    public void setRequestId(String requestId)
    {
        this.requestId = requestId;
    }

    public Long getPurchaseOrderId()
    {
        return purchaseOrderId;
    }

    public void setPurchaseOrderId(Long purchaseOrderId)
    {
        this.purchaseOrderId = purchaseOrderId;
    }

    public Long getWarehouseId()
    {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId)
    {
        this.warehouseId = warehouseId;
    }

    public Long getReceiptBatchId()
    {
        return receiptBatchId;
    }

    public void setReceiptBatchId(Long receiptBatchId)
    {
        this.receiptBatchId = receiptBatchId;
    }

    public String getBatchNo()
    {
        return batchNo;
    }

    public void setBatchNo(String batchNo)
    {
        this.batchNo = batchNo;
    }

    public Date getArrivedTime()
    {
        return arrivedTime;
    }

    public void setArrivedTime(Date arrivedTime)
    {
        this.arrivedTime = arrivedTime;
    }

    public BigDecimal getReceivedQuantity()
    {
        return receivedQuantity;
    }

    public void setReceivedQuantity(BigDecimal receivedQuantity)
    {
        this.receivedQuantity = receivedQuantity;
    }
}
