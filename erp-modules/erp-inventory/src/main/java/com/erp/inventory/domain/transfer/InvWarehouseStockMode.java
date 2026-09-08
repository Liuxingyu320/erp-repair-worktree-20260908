package com.erp.inventory.domain.transfer;

import java.util.Date;

public class InvWarehouseStockMode
{
    private Long warehouseId;
    private String writeMode;
    private String readMode;
    private String reconcileStatus;
    private Date lastReconcileTime;
    private String lastReconcileBatch;
    private String approvedBy;
    private Date approvedTime;

    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long value) { warehouseId = value; }
    public String getWriteMode() { return writeMode; }
    public void setWriteMode(String value) { writeMode = value; }
    public String getReadMode() { return readMode; }
    public void setReadMode(String value) { readMode = value; }
    public String getReconcileStatus() { return reconcileStatus; }
    public void setReconcileStatus(String value) { reconcileStatus = value; }
    public Date getLastReconcileTime() { return lastReconcileTime; }
    public void setLastReconcileTime(Date value) { lastReconcileTime = value; }
    public String getLastReconcileBatch() { return lastReconcileBatch; }
    public void setLastReconcileBatch(String value) { lastReconcileBatch = value; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String value) { approvedBy = value; }
    public Date getApprovedTime() { return approvedTime; }
    public void setApprovedTime(Date value) { approvedTime = value; }
}
