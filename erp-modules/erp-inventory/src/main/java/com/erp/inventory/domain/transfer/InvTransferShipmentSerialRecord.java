package com.erp.inventory.domain.transfer;

public class InvTransferShipmentSerialRecord
{
    private Long shipmentSerialId;
    private Long allocationId;
    private Long shipmentId;
    private Long serialId;
    private String serialNoSnapshot;
    private String itemType;
    private Long itemId;
    private Long sourceBalanceId;
    private Long sourceLotId;
    private Long sourceLocationId;
    private String statusBefore;
    private String statusAfter;
    private String createBy;

    public Long getShipmentSerialId() { return shipmentSerialId; }
    public void setShipmentSerialId(Long value) { shipmentSerialId = value; }
    public Long getAllocationId() { return allocationId; }
    public void setAllocationId(Long value) { allocationId = value; }
    public Long getShipmentId() { return shipmentId; }
    public void setShipmentId(Long value) { shipmentId = value; }
    public Long getSerialId() { return serialId; }
    public void setSerialId(Long value) { serialId = value; }
    public String getSerialNoSnapshot() { return serialNoSnapshot; }
    public void setSerialNoSnapshot(String value) { serialNoSnapshot = value; }
    public String getItemType() { return itemType; }
    public void setItemType(String value) { itemType = value; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long value) { itemId = value; }
    public Long getSourceBalanceId() { return sourceBalanceId; }
    public void setSourceBalanceId(Long value) { sourceBalanceId = value; }
    public Long getSourceLotId() { return sourceLotId; }
    public void setSourceLotId(Long value) { sourceLotId = value; }
    public Long getSourceLocationId() { return sourceLocationId; }
    public void setSourceLocationId(Long value) { sourceLocationId = value; }
    public String getStatusBefore() { return statusBefore; }
    public void setStatusBefore(String value) { statusBefore = value; }
    public String getStatusAfter() { return statusAfter; }
    public void setStatusAfter(String value) { statusAfter = value; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String value) { createBy = value; }
}
