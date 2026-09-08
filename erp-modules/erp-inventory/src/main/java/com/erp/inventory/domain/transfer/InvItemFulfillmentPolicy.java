package com.erp.inventory.domain.transfer;

public class InvItemFulfillmentPolicy
{
    private Long policyId;
    private String itemType;
    private Long itemId;
    private String allocationPolicy;
    private String trackingPolicy;
    private String status;
    private Long version;

    public Long getPolicyId() { return policyId; }
    public void setPolicyId(Long value) { policyId = value; }
    public String getItemType() { return itemType; }
    public void setItemType(String value) { itemType = value; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long value) { itemId = value; }
    public String getAllocationPolicy() { return allocationPolicy; }
    public void setAllocationPolicy(String value) { allocationPolicy = value; }
    public String getTrackingPolicy() { return trackingPolicy; }
    public void setTrackingPolicy(String value) { trackingPolicy = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public Long getVersion() { return version; }
    public void setVersion(Long value) { version = value; }
}
