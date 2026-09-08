package com.erp.inventory.domain.transfer;

import java.math.BigDecimal;
import java.time.Instant;

/** Locked adjudication, parent-transfer and damaged receipt-allocation fact. */
public class InvTransferReceiptDiscrepancyReturnFact
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
    private Long receiptId;
    private Long receiptAllocationId;
    private Long shipmentId;
    private Long shipmentAllocationId;
    private Long targetWarehouseId;
    private String itemType;
    private Long itemId;
    private Long productId;
    private String trackingPolicy;
    private BigDecimal damagedQuantity;
    private BigDecimal quantity;
    private BigDecimal sourceCostPrice;
    private BigDecimal amount;
    private Long quarantineBalanceId;
    private Long quarantineLotId;
    private Long quarantineLocationId;
    private String decisionFingerprint;

    public Long getCaseId() { return caseId; }
    public void setCaseId(Long value) { caseId = value; }
    public Long getCaseVersionBefore() { return caseVersionBefore; }
    public void setCaseVersionBefore(Long value) { caseVersionBefore = value; }
    public Long getAdjudicationId() { return adjudicationId; }
    public void setAdjudicationId(Long value) { adjudicationId = value; }
    public Long getActionId() { return actionId; }
    public void setActionId(Long value) { actionId = value; }
    public Long getActionVersionBefore() { return actionVersionBefore; }
    public void setActionVersionBefore(Long value) {
        actionVersionBefore = value;
    }
    public String getDiscrepancyType() { return discrepancyType; }
    public void setDiscrepancyType(String value) { discrepancyType = value; }
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
    public Long getReceiptId() { return receiptId; }
    public void setReceiptId(Long value) { receiptId = value; }
    public Long getReceiptAllocationId() { return receiptAllocationId; }
    public void setReceiptAllocationId(Long value) {
        receiptAllocationId = value;
    }
    public Long getShipmentId() { return shipmentId; }
    public void setShipmentId(Long value) { shipmentId = value; }
    public Long getShipmentAllocationId() { return shipmentAllocationId; }
    public void setShipmentAllocationId(Long value) {
        shipmentAllocationId = value;
    }
    public Long getTargetWarehouseId() { return targetWarehouseId; }
    public void setTargetWarehouseId(Long value) { targetWarehouseId = value; }
    public String getItemType() { return itemType; }
    public void setItemType(String value) { itemType = value; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long value) { itemId = value; }
    public Long getProductId() { return productId; }
    public void setProductId(Long value) { productId = value; }
    public String getTrackingPolicy() { return trackingPolicy; }
    public void setTrackingPolicy(String value) { trackingPolicy = value; }
    public BigDecimal getDamagedQuantity() { return damagedQuantity; }
    public void setDamagedQuantity(BigDecimal value) { damagedQuantity = value; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal value) { quantity = value; }
    public BigDecimal getSourceCostPrice() { return sourceCostPrice; }
    public void setSourceCostPrice(BigDecimal value) { sourceCostPrice = value; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal value) { amount = value; }
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
                        quarantineBalanceId, quarantineLotId,
                        quarantineLocationId, quantity, sourceCostPrice,
                        amount, decisionFingerprint, executorUserId,
                        executorName, createdAt);
    }

    private static Long location(Long warehouseId, Long deptId)
    {
        return warehouseId != null && warehouseId != 0
                ? warehouseId : deptId;
    }
}
