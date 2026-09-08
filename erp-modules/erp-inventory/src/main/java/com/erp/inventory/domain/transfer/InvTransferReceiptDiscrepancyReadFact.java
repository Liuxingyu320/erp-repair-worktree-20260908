package com.erp.inventory.domain.transfer;

import java.math.BigDecimal;
import java.util.Date;

/** Authoritative joined fact for one V2 receipt discrepancy case. */
public class InvTransferReceiptDiscrepancyReadFact
{
    private Long discrepancyCaseId;
    private Long receiptId;
    private Long receiptAllocationId;
    private Long shipmentId;
    private Long transferId;
    private Long shipmentAllocationId;
    private Long shipmentDetailId;
    private Long transferDetailId;
    private Long sourceDeptId;
    private Long targetDeptId;
    private String sourceName;
    private String targetName;
    private String orderNo;
    private String shipmentNo;
    private String receiptNo;
    private String receiptPlanVersion;
    private String itemType;
    private Long itemId;
    private String itemCode;
    private String itemName;
    private String unit;
    private String discrepancyType;
    private BigDecimal discrepancyQuantity;
    private BigDecimal sourceCostPrice;
    private BigDecimal discrepancyAmount;
    private String factFingerprint;
    private String discrepancyNote;
    private String attachmentRefs;
    private String caseStatus;
    private Long caseVersion;
    private String caseCreateBy;
    private Date caseCreateTime;
    private Confirmation sourceConfirmation;
    private Confirmation targetConfirmation;

    public Long getDiscrepancyCaseId() { return discrepancyCaseId; }
    public void setDiscrepancyCaseId(Long value) {
        discrepancyCaseId = value;
    }
    public Long getReceiptId() { return receiptId; }
    public void setReceiptId(Long value) { receiptId = value; }
    public Long getReceiptAllocationId() { return receiptAllocationId; }
    public void setReceiptAllocationId(Long value) {
        receiptAllocationId = value;
    }
    public Long getShipmentId() { return shipmentId; }
    public void setShipmentId(Long value) { shipmentId = value; }
    public Long getTransferId() { return transferId; }
    public void setTransferId(Long value) { transferId = value; }
    public Long getShipmentAllocationId() { return shipmentAllocationId; }
    public void setShipmentAllocationId(Long value) {
        shipmentAllocationId = value;
    }
    public Long getShipmentDetailId() { return shipmentDetailId; }
    public void setShipmentDetailId(Long value) {
        shipmentDetailId = value;
    }
    public Long getTransferDetailId() { return transferDetailId; }
    public void setTransferDetailId(Long value) {
        transferDetailId = value;
    }
    public Long getSourceDeptId() { return sourceDeptId; }
    public void setSourceDeptId(Long value) { sourceDeptId = value; }
    public Long getTargetDeptId() { return targetDeptId; }
    public void setTargetDeptId(Long value) { targetDeptId = value; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String value) { sourceName = value; }
    public String getTargetName() { return targetName; }
    public void setTargetName(String value) { targetName = value; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String value) { orderNo = value; }
    public String getShipmentNo() { return shipmentNo; }
    public void setShipmentNo(String value) { shipmentNo = value; }
    public String getReceiptNo() { return receiptNo; }
    public void setReceiptNo(String value) { receiptNo = value; }
    public String getReceiptPlanVersion() { return receiptPlanVersion; }
    public void setReceiptPlanVersion(String value) {
        receiptPlanVersion = value;
    }
    public String getItemType() { return itemType; }
    public void setItemType(String value) { itemType = value; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long value) { itemId = value; }
    public String getItemCode() { return itemCode; }
    public void setItemCode(String value) { itemCode = value; }
    public String getItemName() { return itemName; }
    public void setItemName(String value) { itemName = value; }
    public String getUnit() { return unit; }
    public void setUnit(String value) { unit = value; }
    public String getDiscrepancyType() { return discrepancyType; }
    public void setDiscrepancyType(String value) {
        discrepancyType = value;
    }
    public BigDecimal getDiscrepancyQuantity() {
        return discrepancyQuantity;
    }
    public void setDiscrepancyQuantity(BigDecimal value) {
        discrepancyQuantity = value;
    }
    public BigDecimal getSourceCostPrice() { return sourceCostPrice; }
    public void setSourceCostPrice(BigDecimal value) {
        sourceCostPrice = value;
    }
    public BigDecimal getDiscrepancyAmount() { return discrepancyAmount; }
    public void setDiscrepancyAmount(BigDecimal value) {
        discrepancyAmount = value;
    }
    public String getFactFingerprint() { return factFingerprint; }
    public void setFactFingerprint(String value) {
        factFingerprint = value;
    }
    public String getDiscrepancyNote() { return discrepancyNote; }
    public void setDiscrepancyNote(String value) {
        discrepancyNote = value;
    }
    public String getAttachmentRefs() { return attachmentRefs; }
    public void setAttachmentRefs(String value) { attachmentRefs = value; }
    public String getCaseStatus() { return caseStatus; }
    public void setCaseStatus(String value) { caseStatus = value; }
    public Long getCaseVersion() { return caseVersion; }
    public void setCaseVersion(Long value) { caseVersion = value; }
    public String getCaseCreateBy() { return caseCreateBy; }
    public void setCaseCreateBy(String value) { caseCreateBy = value; }
    public Date getCaseCreateTime() { return caseCreateTime; }
    public void setCaseCreateTime(Date value) { caseCreateTime = value; }
    public Confirmation getSourceConfirmation() {
        return sourceConfirmation;
    }
    public void setSourceConfirmation(Confirmation value) {
        sourceConfirmation = value;
    }
    public Confirmation getTargetConfirmation() {
        return targetConfirmation;
    }
    public void setTargetConfirmation(Confirmation value) {
        targetConfirmation = value;
    }

    /** Latest append-only event for one party, when present. */
    public static class Confirmation
    {
        private Long eventId;
        private String requestId;
        private Long caseVersion;
        private String factFingerprint;
        private String partyRole;
        private Long partyDeptId;
        private String decision;
        private String note;
        private Long operatorUserId;
        private String operatorName;
        private Date createTime;

        public Long getEventId() { return eventId; }
        public void setEventId(Long value) { eventId = value; }
        public String getRequestId() { return requestId; }
        public void setRequestId(String value) { requestId = value; }
        public Long getCaseVersion() { return caseVersion; }
        public void setCaseVersion(Long value) { caseVersion = value; }
        public String getFactFingerprint() { return factFingerprint; }
        public void setFactFingerprint(String value) {
            factFingerprint = value;
        }
        public String getPartyRole() { return partyRole; }
        public void setPartyRole(String value) { partyRole = value; }
        public Long getPartyDeptId() { return partyDeptId; }
        public void setPartyDeptId(Long value) { partyDeptId = value; }
        public String getDecision() { return decision; }
        public void setDecision(String value) { decision = value; }
        public String getNote() { return note; }
        public void setNote(String value) { note = value; }
        public Long getOperatorUserId() { return operatorUserId; }
        public void setOperatorUserId(Long value) {
            operatorUserId = value;
        }
        public String getOperatorName() { return operatorName; }
        public void setOperatorName(String value) { operatorName = value; }
        public Date getCreateTime() { return createTime; }
        public void setCreateTime(Date value) { createTime = value; }
    }
}
