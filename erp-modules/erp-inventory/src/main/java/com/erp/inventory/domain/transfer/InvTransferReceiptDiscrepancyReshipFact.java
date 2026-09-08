package com.erp.inventory.domain.transfer;

import java.math.BigDecimal;
import java.time.Instant;

/** Locked shortage-adjudication and parent-transfer facts for one reship. */
public class InvTransferReceiptDiscrepancyReshipFact
{
    private Long caseId;
    private Long caseVersionBefore;
    private Long adjudicationId;
    private Long actionId;
    private Long actionVersionBefore;
    private String discrepancyType;
    private String actionType;
    private Long parentTransferId;
    private String parentTransferType;
    private Long fromDeptId;
    private Long fromWarehouseId;
    private Long toDeptId;
    private Long toWarehouseId;
    private String returnReasonCode;
    private String returnReasonText;
    private Long receiptAllocationId;
    private Long shipmentAllocationId;
    private String itemType;
    private Long itemId;
    private Long productId;
    private String trackingPolicy;
    private BigDecimal quantity;
    private BigDecimal sourceCostPrice;
    private BigDecimal amount;
    private String decisionFingerprint;

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
    public Long getParentTransferId() { return parentTransferId; }
    public void setParentTransferId(Long value) { parentTransferId = value; }
    public String getParentTransferType() { return parentTransferType; }
    public void setParentTransferType(String value) {
        parentTransferType = value;
    }
    public Long getFromDeptId() { return fromDeptId; }
    public void setFromDeptId(Long value) { fromDeptId = value; }
    public Long getFromWarehouseId() { return fromWarehouseId; }
    public void setFromWarehouseId(Long value) { fromWarehouseId = value; }
    public Long getToDeptId() { return toDeptId; }
    public void setToDeptId(Long value) { toDeptId = value; }
    public Long getToWarehouseId() { return toWarehouseId; }
    public void setToWarehouseId(Long value) { toWarehouseId = value; }
    public String getReturnReasonCode() { return returnReasonCode; }
    public void setReturnReasonCode(String value) {
        returnReasonCode = value;
    }
    public String getReturnReasonText() { return returnReasonText; }
    public void setReturnReasonText(String value) {
        returnReasonText = value;
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
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal value) { quantity = value; }
    public BigDecimal getSourceCostPrice() { return sourceCostPrice; }
    public void setSourceCostPrice(BigDecimal value) {
        sourceCostPrice = value;
    }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal value) { amount = value; }
    public String getDecisionFingerprint() { return decisionFingerprint; }
    public void setDecisionFingerprint(String value) {
        decisionFingerprint = value;
    }

    public InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
            .Source toWorkflowSource(String requestId, Long executorUserId,
                    String executorName, Instant createdAt)
    {
        return new InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
                .Source(requestId, caseId, caseVersionBefore,
                        adjudicationId, actionId, actionVersionBefore,
                        discrepancyType, actionType, parentTransferId,
                        parentTransferType, location(fromWarehouseId,
                                fromDeptId),
                        location(toWarehouseId, toDeptId),
                        receiptAllocationId, shipmentAllocationId,
                        itemType, itemId, productId, trackingPolicy,
                        null, null, null, quantity, sourceCostPrice, amount,
                        decisionFingerprint, executorUserId, executorName,
                        createdAt);
    }

    private static Long location(Long warehouseId, Long deptId)
    {
        return warehouseId != null && warehouseId != 0
                ? warehouseId : deptId;
    }
}
