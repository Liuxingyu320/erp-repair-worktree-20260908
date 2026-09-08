package com.erp.inventory.domain;

import java.math.BigDecimal;

public class InvTransferDiscrepancyDetail
{
    private Long discrepancyDetailId;
    private Long discrepancyId;
    private Long transferDetailId;
    private Long shipmentDetailId;
    private String itemType;
    private Long itemId;
    private String itemCode;
    private String itemName;
    private BigDecimal shippedQuantity;
    private BigDecimal acceptedQuantity;
    private BigDecimal shortageQuantity;
    private BigDecimal rejectedQuantity;
    private BigDecimal damagedQuantity;
    private BigDecimal resolutionQuantity;
    private String note;
    private String attachmentRefs;

    public Long getDiscrepancyDetailId() { return discrepancyDetailId; }
    public void setDiscrepancyDetailId(Long value) { discrepancyDetailId = value; }
    public Long getDiscrepancyId() { return discrepancyId; }
    public void setDiscrepancyId(Long value) { discrepancyId = value; }
    public Long getTransferDetailId() { return transferDetailId; }
    public void setTransferDetailId(Long value) { transferDetailId = value; }
    public Long getShipmentDetailId() { return shipmentDetailId; }
    public void setShipmentDetailId(Long value) { shipmentDetailId = value; }
    public String getItemType() { return itemType; }
    public void setItemType(String value) { itemType = value; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long value) { itemId = value; }
    public String getItemCode() { return itemCode; }
    public void setItemCode(String value) { itemCode = value; }
    public String getItemName() { return itemName; }
    public void setItemName(String value) { itemName = value; }
    public BigDecimal getShippedQuantity() { return shippedQuantity; }
    public void setShippedQuantity(BigDecimal value) { shippedQuantity = value; }
    public BigDecimal getAcceptedQuantity() { return acceptedQuantity; }
    public void setAcceptedQuantity(BigDecimal value) { acceptedQuantity = value; }
    public BigDecimal getShortageQuantity() { return shortageQuantity; }
    public void setShortageQuantity(BigDecimal value) { shortageQuantity = value; }
    public BigDecimal getRejectedQuantity() { return rejectedQuantity; }
    public void setRejectedQuantity(BigDecimal value) { rejectedQuantity = value; }
    public BigDecimal getDamagedQuantity() { return damagedQuantity; }
    public void setDamagedQuantity(BigDecimal value) { damagedQuantity = value; }
    public BigDecimal getResolutionQuantity() { return resolutionQuantity; }
    public void setResolutionQuantity(BigDecimal value) { resolutionQuantity = value; }
    public String getNote() { return note; }
    public void setNote(String value) { note = value; }
    public String getAttachmentRefs() { return attachmentRefs; }
    public void setAttachmentRefs(String value) { attachmentRefs = value; }
}
