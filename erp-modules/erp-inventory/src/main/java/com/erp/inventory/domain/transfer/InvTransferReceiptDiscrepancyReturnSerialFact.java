package com.erp.inventory.domain.transfer;

/** Locked V2 damaged-receipt serial and its current quarantine position. */
public class InvTransferReceiptDiscrepancyReturnSerialFact
{
    private Long receiptSerialId;
    private Long receiptId;
    private Long receiptAllocationId;
    private Long shipmentId;
    private Long shipmentAllocationId;
    private Long serialId;
    private String serialNoSnapshot;
    private String currentSerialNo;
    private String disposition;
    private String itemType;
    private Long itemId;
    private Long currentWarehouseId;
    private Long currentBalanceId;
    private Long currentLotId;
    private Long currentLocationId;
    private String receiptStatusAfter;
    private String currentStatus;

    public Long getReceiptSerialId() { return receiptSerialId; }
    public void setReceiptSerialId(Long value) { receiptSerialId = value; }
    public Long getReceiptId() { return receiptId; }
    public void setReceiptId(Long value) { receiptId = value; }
    public Long getReceiptAllocationId() { return receiptAllocationId; }
    public void setReceiptAllocationId(Long value) {
        receiptAllocationId = value;
    }
    public Long getShipmentId() { return shipmentId; }
    public void setShipmentId(Long value) { shipmentId = value; }
    public Long getShipmentAllocationId() { return shipmentAllocationId; }
    public void setShipmentAllocationId(Long value) {
        shipmentAllocationId = value;
    }
    public Long getSerialId() { return serialId; }
    public void setSerialId(Long value) { serialId = value; }
    public String getSerialNoSnapshot() { return serialNoSnapshot; }
    public void setSerialNoSnapshot(String value) {
        serialNoSnapshot = value;
    }
    public String getCurrentSerialNo() { return currentSerialNo; }
    public void setCurrentSerialNo(String value) { currentSerialNo = value; }
    public String getDisposition() { return disposition; }
    public void setDisposition(String value) { disposition = value; }
    public String getItemType() { return itemType; }
    public void setItemType(String value) { itemType = value; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long value) { itemId = value; }
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
    public String getReceiptStatusAfter() { return receiptStatusAfter; }
    public void setReceiptStatusAfter(String value) {
        receiptStatusAfter = value;
    }
    public String getCurrentStatus() { return currentStatus; }
    public void setCurrentStatus(String value) { currentStatus = value; }
}
