package com.erp.inventory.domain;

import java.math.BigDecimal;
import java.util.Date;

/** Costed, append-only disposition ledger for one discrepancy category. */
public class InvTransferDiscrepancyDisposition
{
    private Long dispositionId;
    private Long discrepancyId;
    private Long discrepancyDetailId;
    private Long shipmentDetailId;
    private String category;
    private String decision;
    private BigDecimal quantity;
    private BigDecimal costPrice;
    private BigDecimal amount;
    private String inventoryImpact;
    private Long sourceLocationDeptId;
    private Long targetLocationDeptId;
    private String responsibleParty;
    private String note;
    private String attachmentRefs;
    private String requestId;
    private Long handledByUserId;
    private String handledByName;
    private Date handledTime;
    private Long version;

    public Long getDispositionId() { return dispositionId; }
    public void setDispositionId(Long value) { dispositionId = value; }
    public Long getDiscrepancyId() { return discrepancyId; }
    public void setDiscrepancyId(Long value) { discrepancyId = value; }
    public Long getDiscrepancyDetailId() { return discrepancyDetailId; }
    public void setDiscrepancyDetailId(Long value) { discrepancyDetailId = value; }
    public Long getShipmentDetailId() { return shipmentDetailId; }
    public void setShipmentDetailId(Long value) { shipmentDetailId = value; }
    public String getCategory() { return category; }
    public void setCategory(String value) { category = value; }
    public String getDecision() { return decision; }
    public void setDecision(String value) { decision = value; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal value) { quantity = value; }
    public BigDecimal getCostPrice() { return costPrice; }
    public void setCostPrice(BigDecimal value) { costPrice = value; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal value) { amount = value; }
    public String getInventoryImpact() { return inventoryImpact; }
    public void setInventoryImpact(String value) { inventoryImpact = value; }
    public Long getSourceLocationDeptId() { return sourceLocationDeptId; }
    public void setSourceLocationDeptId(Long value) { sourceLocationDeptId = value; }
    public Long getTargetLocationDeptId() { return targetLocationDeptId; }
    public void setTargetLocationDeptId(Long value) { targetLocationDeptId = value; }
    public String getResponsibleParty() { return responsibleParty; }
    public void setResponsibleParty(String value) { responsibleParty = value; }
    public String getNote() { return note; }
    public void setNote(String value) { note = value; }
    public String getAttachmentRefs() { return attachmentRefs; }
    public void setAttachmentRefs(String value) { attachmentRefs = value; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String value) { requestId = value; }
    public Long getHandledByUserId() { return handledByUserId; }
    public void setHandledByUserId(Long value) { handledByUserId = value; }
    public String getHandledByName() { return handledByName; }
    public void setHandledByName(String value) { handledByName = value; }
    public Date getHandledTime() { return handledTime; }
    public void setHandledTime(Date value) { handledTime = value; }
    public Long getVersion() { return version; }
    public void setVersion(Long value) { version = value; }
}
