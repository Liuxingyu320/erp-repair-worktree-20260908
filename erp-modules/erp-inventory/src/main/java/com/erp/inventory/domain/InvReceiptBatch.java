package com.erp.inventory.domain;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import com.erp.common.core.web.domain.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;

/** 采购收货批次事实。 */
public class InvReceiptBatch extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long batchId;
    private String batchNo;
    private Long purchaseOrderId;
    private String orderNo;
    private Long warehouseId;
    private String warehouseName;
    private String supplierBatchNo;
    private String deliveryNoteNo;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date arrivedTime;
    private String status;
    private Long receivedUserId;
    private String receivedBy;
    private BigDecimal totalQuantity;
    private BigDecimal pendingQuantity;
    private List<InvReceiptBatchDetail> details;
    private List<InvQualityInspection> inspections;

    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
    public Long getPurchaseOrderId() { return purchaseOrderId; }
    public void setPurchaseOrderId(Long purchaseOrderId) { this.purchaseOrderId = purchaseOrderId; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public String getWarehouseName() { return warehouseName; }
    public void setWarehouseName(String warehouseName) { this.warehouseName = warehouseName; }
    public String getSupplierBatchNo() { return supplierBatchNo; }
    public void setSupplierBatchNo(String supplierBatchNo) { this.supplierBatchNo = supplierBatchNo; }
    public String getDeliveryNoteNo() { return deliveryNoteNo; }
    public void setDeliveryNoteNo(String deliveryNoteNo) { this.deliveryNoteNo = deliveryNoteNo; }
    public Date getArrivedTime() { return arrivedTime; }
    public void setArrivedTime(Date arrivedTime) { this.arrivedTime = arrivedTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getReceivedUserId() { return receivedUserId; }
    public void setReceivedUserId(Long receivedUserId) { this.receivedUserId = receivedUserId; }
    public String getReceivedBy() { return receivedBy; }
    public void setReceivedBy(String receivedBy) { this.receivedBy = receivedBy; }
    public BigDecimal getTotalQuantity() { return totalQuantity; }
    public void setTotalQuantity(BigDecimal totalQuantity) { this.totalQuantity = totalQuantity; }
    public BigDecimal getPendingQuantity() { return pendingQuantity; }
    public void setPendingQuantity(BigDecimal pendingQuantity) { this.pendingQuantity = pendingQuantity; }
    public List<InvReceiptBatchDetail> getDetails() { return details; }
    public void setDetails(List<InvReceiptBatchDetail> details) { this.details = details; }
    public List<InvQualityInspection> getInspections() { return inspections; }
    public void setInspections(List<InvQualityInspection> inspections) { this.inspections = inspections; }
}
