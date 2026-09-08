package com.erp.inventory.domain.transfer;

/** Read-only audit/current-position pair for a shipped serial. */
public class InvTransferReceiptPlanningSerialFact
{
    private Long shipmentSerialId;
    private Long allocationId;
    private Long shipmentId;
    private Long serialId;
    private String serialNoSnapshot;
    private String currentSerialNo;
    private String itemType;
    private Long itemId;
    private Long sourceBalanceId;
    private Long sourceLotId;
    private Long sourceLocationId;
    private String statusAfter;
    private Long currentWarehouseId;
    private Long currentBalanceId;
    private Long currentLotId;
    private Long currentLocationId;
    private String currentStatus;

    public Long getShipmentSerialId() { return shipmentSerialId; }
    public void setShipmentSerialId(Long value) { shipmentSerialId = value; }
    public Long getAllocationId() { return allocationId; }
    public void setAllocationId(Long value) { allocationId = value; }
    public Long getShipmentId() { return shipmentId; }
    public void setShipmentId(Long value) { shipmentId = value; }
    public Long getSerialId() { return serialId; }
    public void setSerialId(Long value) { serialId = value; }
    public String getSerialNoSnapshot() { return serialNoSnapshot; }
    public void setSerialNoSnapshot(String value) {
        serialNoSnapshot = value;
    }
    public String getCurrentSerialNo() { return currentSerialNo; }
    public void setCurrentSerialNo(String value) { currentSerialNo = value; }
    public String getItemType() { return itemType; }
    public void setItemType(String value) { itemType = value; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long value) { itemId = value; }
    public Long getSourceBalanceId() { return sourceBalanceId; }
    public void setSourceBalanceId(Long value) { sourceBalanceId = value; }
    public Long getSourceLotId() { return sourceLotId; }
    public void setSourceLotId(Long value) { sourceLotId = value; }
    public Long getSourceLocationId() { return sourceLocationId; }
    public void setSourceLocationId(Long value) {
        sourceLocationId = value;
    }
    public String getStatusAfter() { return statusAfter; }
    public void setStatusAfter(String value) { statusAfter = value; }
    public Long getCurrentWarehouseId() { return currentWarehouseId; }
    public void setCurrentWarehouseId(Long value) {
        currentWarehouseId = value;
    }
    public Long getCurrentBalanceId() { return currentBalanceId; }
    public void setCurrentBalanceId(Long value) { currentBalanceId = value; }
    public Long getCurrentLotId() { return currentLotId; }
    public void setCurrentLotId(Long value) { currentLotId = value; }
    public Long getCurrentLocationId() { return currentLocationId; }
    public void setCurrentLocationId(Long value) {
        currentLocationId = value;
    }
    public String getCurrentStatus() { return currentStatus; }
    public void setCurrentStatus(String value) { currentStatus = value; }
}
