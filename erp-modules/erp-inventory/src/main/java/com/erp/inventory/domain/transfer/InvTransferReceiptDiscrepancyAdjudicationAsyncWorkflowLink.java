package com.erp.inventory.domain.transfer;

import java.math.BigDecimal;
import java.util.Date;

/** Immutable-workflow relation as stored by MyBatis. */
public class InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowLink
{
    private Long workflowId;
    private String requestId;
    private Long caseId;
    private Long caseVersionBefore;
    private Long adjudicationId;
    private Long actionId;
    private Long actionVersionBefore;
    private String discrepancyType;
    private String actionType;
    private String effectKind;
    private String workflowType;
    private Long parentTransferId;
    private String parentTransferType;
    private Long childTransferId;
    private String childTransferType;
    private Long originalSourceLocationDeptId;
    private Long originalTargetLocationDeptId;
    private Long childSourceLocationDeptId;
    private Long childTargetLocationDeptId;
    private Long receiptAllocationId;
    private Long shipmentAllocationId;
    private String itemType;
    private Long itemId;
    private Long productId;
    private String trackingPolicy;
    private String inventorySource;
    private Long quarantineBalanceId;
    private Long quarantineLotId;
    private Long quarantineLocationId;
    private BigDecimal quantity;
    private BigDecimal sourceCostPrice;
    private BigDecimal amount;
    private String sourceBusinessType;
    private Long sourceBusinessId;
    private String effectReference;
    private String dispatchChildStatus;
    private Integer reservationCount;
    private BigDecimal reservedQuantity;
    private String decisionFingerprint;
    private String workflowFingerprint;
    private Long executorUserId;
    private String executorName;
    private Date createTime;

    public Long getWorkflowId() { return workflowId; }
    public void setWorkflowId(Long value) { workflowId = value; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String value) { requestId = value; }
    public Long getCaseId() { return caseId; }
    public void setCaseId(Long value) { caseId = value; }
    public Long getCaseVersionBefore() { return caseVersionBefore; }
    public void setCaseVersionBefore(Long value) {
        caseVersionBefore = value;
    }
    public Long getAdjudicationId() { return adjudicationId; }
    public void setAdjudicationId(Long value) { adjudicationId = value; }
    public Long getActionId() { return actionId; }
    public void setActionId(Long value) { actionId = value; }
    public Long getActionVersionBefore() { return actionVersionBefore; }
    public void setActionVersionBefore(Long value) {
        actionVersionBefore = value;
    }
    public String getDiscrepancyType() { return discrepancyType; }
    public void setDiscrepancyType(String value) {
        discrepancyType = value;
    }
    public String getActionType() { return actionType; }
    public void setActionType(String value) { actionType = value; }
    public String getEffectKind() { return effectKind; }
    public void setEffectKind(String value) { effectKind = value; }
    public String getWorkflowType() { return workflowType; }
    public void setWorkflowType(String value) { workflowType = value; }
    public Long getParentTransferId() { return parentTransferId; }
    public void setParentTransferId(Long value) { parentTransferId = value; }
    public String getParentTransferType() { return parentTransferType; }
    public void setParentTransferType(String value) {
        parentTransferType = value;
    }
    public Long getChildTransferId() { return childTransferId; }
    public void setChildTransferId(Long value) { childTransferId = value; }
    public String getChildTransferType() { return childTransferType; }
    public void setChildTransferType(String value) {
        childTransferType = value;
    }
    public Long getOriginalSourceLocationDeptId() {
        return originalSourceLocationDeptId;
    }
    public void setOriginalSourceLocationDeptId(Long value) {
        originalSourceLocationDeptId = value;
    }
    public Long getOriginalTargetLocationDeptId() {
        return originalTargetLocationDeptId;
    }
    public void setOriginalTargetLocationDeptId(Long value) {
        originalTargetLocationDeptId = value;
    }
    public Long getChildSourceLocationDeptId() {
        return childSourceLocationDeptId;
    }
    public void setChildSourceLocationDeptId(Long value) {
        childSourceLocationDeptId = value;
    }
    public Long getChildTargetLocationDeptId() {
        return childTargetLocationDeptId;
    }
    public void setChildTargetLocationDeptId(Long value) {
        childTargetLocationDeptId = value;
    }
    public Long getReceiptAllocationId() { return receiptAllocationId; }
    public void setReceiptAllocationId(Long value) {
        receiptAllocationId = value;
    }
    public Long getShipmentAllocationId() { return shipmentAllocationId; }
    public void setShipmentAllocationId(Long value) {
        shipmentAllocationId = value;
    }
    public String getItemType() { return itemType; }
    public void setItemType(String value) { itemType = value; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long value) { itemId = value; }
    public Long getProductId() { return productId; }
    public void setProductId(Long value) { productId = value; }
    public String getTrackingPolicy() { return trackingPolicy; }
    public void setTrackingPolicy(String value) { trackingPolicy = value; }
    public String getInventorySource() { return inventorySource; }
    public void setInventorySource(String value) { inventorySource = value; }
    public Long getQuarantineBalanceId() { return quarantineBalanceId; }
    public void setQuarantineBalanceId(Long value) {
        quarantineBalanceId = value;
    }
    public Long getQuarantineLotId() { return quarantineLotId; }
    public void setQuarantineLotId(Long value) { quarantineLotId = value; }
    public Long getQuarantineLocationId() { return quarantineLocationId; }
    public void setQuarantineLocationId(Long value) {
        quarantineLocationId = value;
    }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal value) { quantity = value; }
    public BigDecimal getSourceCostPrice() { return sourceCostPrice; }
    public void setSourceCostPrice(BigDecimal value) {
        sourceCostPrice = value;
    }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal value) { amount = value; }
    public String getSourceBusinessType() { return sourceBusinessType; }
    public void setSourceBusinessType(String value) {
        sourceBusinessType = value;
    }
    public Long getSourceBusinessId() { return sourceBusinessId; }
    public void setSourceBusinessId(Long value) { sourceBusinessId = value; }
    public String getEffectReference() { return effectReference; }
    public void setEffectReference(String value) { effectReference = value; }
    public String getDispatchChildStatus() { return dispatchChildStatus; }
    public void setDispatchChildStatus(String value) {
        dispatchChildStatus = value;
    }
    public Integer getReservationCount() { return reservationCount; }
    public void setReservationCount(Integer value) {
        reservationCount = value;
    }
    public BigDecimal getReservedQuantity() { return reservedQuantity; }
    public void setReservedQuantity(BigDecimal value) {
        reservedQuantity = value;
    }
    public String getDecisionFingerprint() { return decisionFingerprint; }
    public void setDecisionFingerprint(String value) {
        decisionFingerprint = value;
    }
    public String getWorkflowFingerprint() { return workflowFingerprint; }
    public void setWorkflowFingerprint(String value) {
        workflowFingerprint = value;
    }
    public Long getExecutorUserId() { return executorUserId; }
    public void setExecutorUserId(Long value) { executorUserId = value; }
    public String getExecutorName() { return executorName; }
    public void setExecutorName(String value) { executorName = value; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date value) { createTime = value; }

    public InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
            .PreparedLink toPolicyLink()
    {
        return new InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
                .PreparedLink(requestId, caseId, caseVersionBefore,
                        adjudicationId, actionId, actionVersionBefore,
                        discrepancyType, actionType, effectKind,
                        workflowType, parentTransferId, parentTransferType,
                        childTransferId, childTransferType,
                        originalSourceLocationDeptId,
                        originalTargetLocationDeptId,
                        childSourceLocationDeptId,
                        childTargetLocationDeptId, receiptAllocationId,
                        shipmentAllocationId, itemType, itemId, productId,
                        trackingPolicy, inventorySource,
                        quarantineBalanceId, quarantineLotId,
                        quarantineLocationId, quantity, sourceCostPrice,
                        amount, sourceBusinessType, sourceBusinessId,
                        effectReference, dispatchChildStatus,
                        reservationCount, reservedQuantity,
                        decisionFingerprint, executorUserId, executorName,
                        createTime == null ? null : createTime.toInstant(),
                        workflowFingerprint);
    }
}
