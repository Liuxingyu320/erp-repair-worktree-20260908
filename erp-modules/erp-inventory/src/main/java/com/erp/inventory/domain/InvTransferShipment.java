package com.erp.inventory.domain;

import java.util.Date;
import java.util.List;
import com.erp.common.core.web.domain.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public class InvTransferShipment extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long shipmentId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long transferId;
    private String shipmentNo;
    private Long warehouseId;
    private Long warehouseDeptId;
    private Long sourceLocationDeptId;
    private String inventoryWriteVersion;
    private String commandRequestId;
    private String planVersion;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sealedRevisionId;
    private String reconcileBatch;
    private String status;
    private String shippedBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date shippedTime;
    private String receivedBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date receivedTime;
    private List<InvTransferShipmentDetail> details;

    public Long getShipmentId() { return shipmentId; }
    public void setShipmentId(Long shipmentId) { this.shipmentId = shipmentId; }
    public Long getTransferId() { return transferId; }
    public void setTransferId(Long transferId) { this.transferId = transferId; }
    public String getShipmentNo() { return shipmentNo; }
    public void setShipmentNo(String shipmentNo) { this.shipmentNo = shipmentNo; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public Long getWarehouseDeptId() { return warehouseDeptId; }
    public void setWarehouseDeptId(Long warehouseDeptId) { this.warehouseDeptId = warehouseDeptId; }
    public Long getSourceLocationDeptId() { return sourceLocationDeptId; }
    public void setSourceLocationDeptId(Long value) { sourceLocationDeptId = value; }
    public String getInventoryWriteVersion() { return inventoryWriteVersion; }
    public void setInventoryWriteVersion(String value) { inventoryWriteVersion = value; }
    public String getCommandRequestId() { return commandRequestId; }
    public void setCommandRequestId(String value) { commandRequestId = value; }
    public String getPlanVersion() { return planVersion; }
    public void setPlanVersion(String value) { planVersion = value; }
    public Long getSealedRevisionId() { return sealedRevisionId; }
    public void setSealedRevisionId(Long value) { sealedRevisionId = value; }
    public String getReconcileBatch() { return reconcileBatch; }
    public void setReconcileBatch(String value) { reconcileBatch = value; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getShippedBy() { return shippedBy; }
    public void setShippedBy(String shippedBy) { this.shippedBy = shippedBy; }
    public Date getShippedTime() { return shippedTime; }
    public void setShippedTime(Date shippedTime) { this.shippedTime = shippedTime; }
    public String getReceivedBy() { return receivedBy; }
    public void setReceivedBy(String receivedBy) { this.receivedBy = receivedBy; }
    public Date getReceivedTime() { return receivedTime; }
    public void setReceivedTime(Date receivedTime) { this.receivedTime = receivedTime; }
    public List<InvTransferShipmentDetail> getDetails() { return details; }
    public void setDetails(List<InvTransferShipmentDetail> details) { this.details = details; }
}
