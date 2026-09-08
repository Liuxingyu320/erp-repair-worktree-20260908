package com.erp.inventory.domain.transfer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class InvShipmentAllocationCandidate
{
    private Long balanceId;
    private String itemType;
    private Long itemId;
    private Long warehouseId;
    private Long lotId;
    private String lotNo;
    private Date expiryDate;
    private Date receivedAt;
    private Long locationId;
    private String locationCode;
    private BigDecimal availableQuantity;
    private Long version;
    private List<InvShipmentSerialCandidate> serials = new ArrayList<>();

    public Long getBalanceId() { return balanceId; }
    public void setBalanceId(Long value) { balanceId = value; }
    public String getItemType() { return itemType; }
    public void setItemType(String value) { itemType = value; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long value) { itemId = value; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long value) { warehouseId = value; }
    public Long getLotId() { return lotId; }
    public void setLotId(Long value) { lotId = value; }
    public String getLotNo() { return lotNo; }
    public void setLotNo(String value) { lotNo = value; }
    public Date getExpiryDate() { return expiryDate; }
    public void setExpiryDate(Date value) { expiryDate = value; }
    public Date getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Date value) { receivedAt = value; }
    public Long getLocationId() { return locationId; }
    public void setLocationId(Long value) { locationId = value; }
    public String getLocationCode() { return locationCode; }
    public void setLocationCode(String value) { locationCode = value; }
    public BigDecimal getAvailableQuantity() { return availableQuantity; }
    public void setAvailableQuantity(BigDecimal value) { availableQuantity = value; }
    public Long getVersion() { return version; }
    public void setVersion(Long value) { version = value; }
    public List<InvShipmentSerialCandidate> getSerials() { return serials; }
    public void setSerials(List<InvShipmentSerialCandidate> value) {
        serials = value == null ? new ArrayList<>() : new ArrayList<>(value);
    }
}
