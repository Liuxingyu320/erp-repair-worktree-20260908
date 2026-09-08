package com.erp.inventory.domain;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import com.erp.common.core.web.domain.BaseEntity;

public class InvTransferDiscrepancy extends BaseEntity
{
    private static final long serialVersionUID = 1L;
    private Long discrepancyId;
    private Long transferId;
    private Long shipmentId;
    private String discrepancyNo;
    private String status;
    private String discrepancyType;
    private BigDecimal shippedQuantity;
    private BigDecimal acceptedQuantity;
    private BigDecimal shortageQuantity;
    private BigDecimal rejectedQuantity;
    private BigDecimal damagedQuantity;
    private String description;
    private String attachmentRefs;
    private String responsibleParty;
    private String resolutionDecision;
    private String resolutionNote;
    private Long handledByUserId;
    private String handledByName;
    private Date handledTime;
    private Long version;
    private List<InvTransferDiscrepancyDetail> details;
    private List<InvTransferDiscrepancyDisposition> dispositions;

    public Long getDiscrepancyId() { return discrepancyId; }
    public void setDiscrepancyId(Long value) { discrepancyId = value; }
    public Long getTransferId() { return transferId; }
    public void setTransferId(Long value) { transferId = value; }
    public Long getShipmentId() { return shipmentId; }
    public void setShipmentId(Long value) { shipmentId = value; }
    public String getDiscrepancyNo() { return discrepancyNo; }
    public void setDiscrepancyNo(String value) { discrepancyNo = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public String getDiscrepancyType() { return discrepancyType; }
    public void setDiscrepancyType(String value) { discrepancyType = value; }
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
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
    public String getAttachmentRefs() { return attachmentRefs; }
    public void setAttachmentRefs(String value) { attachmentRefs = value; }
    public String getResponsibleParty() { return responsibleParty; }
    public void setResponsibleParty(String value) { responsibleParty = value; }
    public String getResolutionDecision() { return resolutionDecision; }
    public void setResolutionDecision(String value) { resolutionDecision = value; }
    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String value) { resolutionNote = value; }
    public Long getHandledByUserId() { return handledByUserId; }
    public void setHandledByUserId(Long value) { handledByUserId = value; }
    public String getHandledByName() { return handledByName; }
    public void setHandledByName(String value) { handledByName = value; }
    public Date getHandledTime() { return handledTime; }
    public void setHandledTime(Date value) { handledTime = value; }
    public Long getVersion() { return version; }
    public void setVersion(Long value) { version = value; }
    public List<InvTransferDiscrepancyDetail> getDetails() { return details; }
    public void setDetails(List<InvTransferDiscrepancyDetail> value) { details = value; }
    public List<InvTransferDiscrepancyDisposition> getDispositions() { return dispositions; }
    public void setDispositions(List<InvTransferDiscrepancyDisposition> value) { dispositions = value; }
}
