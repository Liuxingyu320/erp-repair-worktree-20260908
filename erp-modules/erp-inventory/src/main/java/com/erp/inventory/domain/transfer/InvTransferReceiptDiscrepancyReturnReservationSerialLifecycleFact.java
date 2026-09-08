package com.erp.inventory.domain.transfer;

/** Locked serial binding and its current physical state. */
public class
        InvTransferReceiptDiscrepancyReturnReservationSerialLifecycleFact
{
    private Long bindingId;
    private Long reservationId;
    private Long receiptSerialId;
    private Long serialId;
    private String serialNoSnapshot;
    private Long warehouseId;
    private Long balanceId;
    private Long lotId;
    private Long locationId;
    private String lifecycleStatus;
    private Long version;
    private String currentSerialNo;
    private String currentSerialStatus;
    private Long currentWarehouseId;
    private Long currentBalanceId;
    private Long currentLotId;
    private Long currentLocationId;

    public Long getBindingId() { return bindingId; }
    public void setBindingId(Long value) { bindingId = value; }
    public Long getReservationId() { return reservationId; }
    public void setReservationId(Long value) { reservationId = value; }
    public Long getReceiptSerialId() { return receiptSerialId; }
    public void setReceiptSerialId(Long value) { receiptSerialId = value; }
    public Long getSerialId() { return serialId; }
    public void setSerialId(Long value) { serialId = value; }
    public String getSerialNoSnapshot() { return serialNoSnapshot; }
    public void setSerialNoSnapshot(String value) {
        serialNoSnapshot = value;
    }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long value) { warehouseId = value; }
    public Long getBalanceId() { return balanceId; }
    public void setBalanceId(Long value) { balanceId = value; }
    public Long getLotId() { return lotId; }
    public void setLotId(Long value) { lotId = value; }
    public Long getLocationId() { return locationId; }
    public void setLocationId(Long value) { locationId = value; }
    public String getLifecycleStatus() { return lifecycleStatus; }
    public void setLifecycleStatus(String value) { lifecycleStatus = value; }
    public Long getVersion() { return version; }
    public void setVersion(Long value) { version = value; }
    public String getCurrentSerialNo() { return currentSerialNo; }
    public void setCurrentSerialNo(String value) { currentSerialNo = value; }
    public String getCurrentSerialStatus() { return currentSerialStatus; }
    public void setCurrentSerialStatus(String value) {
        currentSerialStatus = value;
    }
    public Long getCurrentWarehouseId() { return currentWarehouseId; }
    public void setCurrentWarehouseId(Long value) {
        currentWarehouseId = value;
    }
    public Long getCurrentBalanceId() { return currentBalanceId; }
    public void setCurrentBalanceId(Long value) {
        currentBalanceId = value;
    }
    public Long getCurrentLotId() { return currentLotId; }
    public void setCurrentLotId(Long value) { currentLotId = value; }
    public Long getCurrentLocationId() { return currentLocationId; }
    public void setCurrentLocationId(Long value) {
        currentLocationId = value;
    }
}
