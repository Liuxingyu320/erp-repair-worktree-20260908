package com.erp.inventory.domain.transfer;

/** Stable target-location fact exposed by V2 receipt planning. */
public class InvTransferReceiptLocationCandidate
{
    private Long locationId;
    private Long warehouseId;
    private String locationCode;
    private String locationName;
    private String locationType;
    private Integer sortOrder;
    private String status;
    private String virtualFlag;

    public Long getLocationId() { return locationId; }
    public void setLocationId(Long value) { locationId = value; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long value) { warehouseId = value; }
    public String getLocationCode() { return locationCode; }
    public void setLocationCode(String value) { locationCode = value; }
    public String getLocationName() { return locationName; }
    public void setLocationName(String value) { locationName = value; }
    public String getLocationType() { return locationType; }
    public void setLocationType(String value) { locationType = value; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer value) { sortOrder = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public String getVirtualFlag() { return virtualFlag; }
    public void setVirtualFlag(String value) { virtualFlag = value; }
}
