package com.erp.inventory.domain.transfer;

import java.math.BigDecimal;

/** Locked ownership facts for one adjudication return reservation. */
public class
        InvTransferReceiptDiscrepancyReturnReservationLifecycleFact
{
    private Long reservationId;
    private String reservationRequestId;
    private Long actionId;
    private Long childTransferId;
    private Long childTransferDetailId;
    private Integer reservationRound;
    private Long receiptAllocationId;
    private Long shipmentAllocationId;
    private Long stockId;
    private Long balanceId;
    private Long lotId;
    private Long locationId;
    private String itemType;
    private Long itemId;
    private Long productId;
    private String trackingPolicy;
    private BigDecimal reservedQuantity;
    private BigDecimal consumedQuantity;
    private BigDecimal releasedQuantity;
    private BigDecimal sourceCostPrice;
    private BigDecimal reservedAmount;
    private String decisionFingerprint;
    private String status;
    private Long version;
    private String childStatus;
    private Integer approvalRound;
    private Long sourceWarehouseId;
    private String sourceBusinessType;
    private Long sourceBusinessId;
    private BigDecimal requestedQuantity;
    private BigDecimal deliveredQuantity;
    private Long workflowId;
    private String workflowType;
    private String inventorySource;
    private String workflowFingerprint;

    public Long getReservationId() { return reservationId; }
    public void setReservationId(Long value) { reservationId = value; }
    public String getReservationRequestId() { return reservationRequestId; }
    public void setReservationRequestId(String value) {
        reservationRequestId = value;
    }
    public Long getActionId() { return actionId; }
    public void setActionId(Long value) { actionId = value; }
    public Long getChildTransferId() { return childTransferId; }
    public void setChildTransferId(Long value) { childTransferId = value; }
    public Long getChildTransferDetailId() {
        return childTransferDetailId;
    }
    public void setChildTransferDetailId(Long value) {
        childTransferDetailId = value;
    }
    public Integer getReservationRound() { return reservationRound; }
    public void setReservationRound(Integer value) {
        reservationRound = value;
    }
    public Long getReceiptAllocationId() { return receiptAllocationId; }
    public void setReceiptAllocationId(Long value) {
        receiptAllocationId = value;
    }
    public Long getShipmentAllocationId() { return shipmentAllocationId; }
    public void setShipmentAllocationId(Long value) {
        shipmentAllocationId = value;
    }
    public Long getStockId() { return stockId; }
    public void setStockId(Long value) { stockId = value; }
    public Long getBalanceId() { return balanceId; }
    public void setBalanceId(Long value) { balanceId = value; }
    public Long getLotId() { return lotId; }
    public void setLotId(Long value) { lotId = value; }
    public Long getLocationId() { return locationId; }
    public void setLocationId(Long value) { locationId = value; }
    public String getItemType() { return itemType; }
    public void setItemType(String value) { itemType = value; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long value) { itemId = value; }
    public Long getProductId() { return productId; }
    public void setProductId(Long value) { productId = value; }
    public String getTrackingPolicy() { return trackingPolicy; }
    public void setTrackingPolicy(String value) { trackingPolicy = value; }
    public BigDecimal getReservedQuantity() { return reservedQuantity; }
    public void setReservedQuantity(BigDecimal value) {
        reservedQuantity = value;
    }
    public BigDecimal getConsumedQuantity() { return consumedQuantity; }
    public void setConsumedQuantity(BigDecimal value) {
        consumedQuantity = value;
    }
    public BigDecimal getReleasedQuantity() { return releasedQuantity; }
    public void setReleasedQuantity(BigDecimal value) {
        releasedQuantity = value;
    }
    public BigDecimal getSourceCostPrice() { return sourceCostPrice; }
    public void setSourceCostPrice(BigDecimal value) {
        sourceCostPrice = value;
    }
    public BigDecimal getReservedAmount() { return reservedAmount; }
    public void setReservedAmount(BigDecimal value) {
        reservedAmount = value;
    }
    public String getDecisionFingerprint() { return decisionFingerprint; }
    public void setDecisionFingerprint(String value) {
        decisionFingerprint = value;
    }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public Long getVersion() { return version; }
    public void setVersion(Long value) { version = value; }
    public String getChildStatus() { return childStatus; }
    public void setChildStatus(String value) { childStatus = value; }
    public Integer getApprovalRound() { return approvalRound; }
    public void setApprovalRound(Integer value) { approvalRound = value; }
    public Long getSourceWarehouseId() { return sourceWarehouseId; }
    public void setSourceWarehouseId(Long value) {
        sourceWarehouseId = value;
    }
    public String getSourceBusinessType() { return sourceBusinessType; }
    public void setSourceBusinessType(String value) {
        sourceBusinessType = value;
    }
    public Long getSourceBusinessId() { return sourceBusinessId; }
    public void setSourceBusinessId(Long value) { sourceBusinessId = value; }
    public BigDecimal getRequestedQuantity() { return requestedQuantity; }
    public void setRequestedQuantity(BigDecimal value) {
        requestedQuantity = value;
    }
    public BigDecimal getDeliveredQuantity() { return deliveredQuantity; }
    public void setDeliveredQuantity(BigDecimal value) {
        deliveredQuantity = value;
    }
    public Long getWorkflowId() { return workflowId; }
    public void setWorkflowId(Long value) { workflowId = value; }
    public String getWorkflowType() { return workflowType; }
    public void setWorkflowType(String value) { workflowType = value; }
    public String getInventorySource() { return inventorySource; }
    public void setInventorySource(String value) { inventorySource = value; }
    public String getWorkflowFingerprint() { return workflowFingerprint; }
    public void setWorkflowFingerprint(String value) {
        workflowFingerprint = value;
    }
}
