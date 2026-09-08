package com.erp.inventory.domain.dto;

import java.math.BigDecimal;

/** One exact category disposition for one discrepancy detail. */
public class InvTransferDiscrepancyResolutionItem
{
    private Long detailId;
    private String category;
    private String decision;
    private BigDecimal quantity;
    private String note;
    private String attachmentRefs;

    public Long getDetailId() { return detailId; }
    public void setDetailId(Long value) { detailId = value; }
    public String getCategory() { return category; }
    public void setCategory(String value) { category = value; }
    public String getDecision() { return decision; }
    public void setDecision(String value) { decision = value; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal value) { quantity = value; }
    public String getNote() { return note; }
    public void setNote(String value) { note = value; }
    public String getAttachmentRefs() { return attachmentRefs; }
    public void setAttachmentRefs(String value) { attachmentRefs = value; }
}
