package com.erp.inventory.domain.vo;

import java.io.Serializable;

/** Read-only report material identity; no master-data cost or private fields. */
public class InvReportItemOption implements Serializable
{
    private static final long serialVersionUID = 1L;
    private String itemType;
    private Long itemId;
    private String itemCode;
    private String itemName;
    private String spec;
    private String unit;
    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public String getSpec() { return spec; }
    public void setSpec(String spec) { this.spec = spec; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
}
