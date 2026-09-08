package com.erp.inventory.domain.transfer;

import java.math.BigDecimal;

/** Immutable shipment allocation plus mutable receipt-progress version. */
public class InvTransferReceiptLockedAllocation
{
    private Long allocationId;
    private Long shipmentId;
    private Long shipmentDetailId;
    private Long transferId;
    private Long transferDetailId;
    private Long sourceBalanceId;
    private Long sourceLotId;
    private Long sourceLocationId;
    private String allocationPolicy;
    private String trackingPolicy;
    private BigDecimal allocatedQuantity;
    private Long balanceVersionAfter;
    private BigDecimal costPrice;
    private BigDecimal totalCost;
    private BigDecimal acceptedReceivedQuantity;
    private BigDecimal damagedReceivedQuantity;
    private BigDecimal shortageReportedQuantity;
    private Long receiptVersion;

    public Long getAllocationId() { return allocationId; }
    public void setAllocationId(Long value) { allocationId = value; }
    public Long getShipmentId() { return shipmentId; }
    public void setShipmentId(Long value) { shipmentId = value; }
    public Long getShipmentDetailId() { return shipmentDetailId; }
    public void setShipmentDetailId(Long value) { shipmentDetailId = value; }
    public Long getTransferId() { return transferId; }
    public void setTransferId(Long value) { transferId = value; }
    public Long getTransferDetailId() { return transferDetailId; }
    public void setTransferDetailId(Long value) { transferDetailId = value; }
    public Long getSourceBalanceId() { return sourceBalanceId; }
    public void setSourceBalanceId(Long value) { sourceBalanceId = value; }
    public Long getSourceLotId() { return sourceLotId; }
    public void setSourceLotId(Long value) { sourceLotId = value; }
    public Long getSourceLocationId() { return sourceLocationId; }
    public void setSourceLocationId(Long value) {
        sourceLocationId = value;
    }
    public String getAllocationPolicy() { return allocationPolicy; }
    public void setAllocationPolicy(String value) {
        allocationPolicy = value;
    }
    public String getTrackingPolicy() { return trackingPolicy; }
    public void setTrackingPolicy(String value) { trackingPolicy = value; }
    public BigDecimal getAllocatedQuantity() { return allocatedQuantity; }
    public void setAllocatedQuantity(BigDecimal value) {
        allocatedQuantity = value;
    }
    public Long getBalanceVersionAfter() { return balanceVersionAfter; }
    public void setBalanceVersionAfter(Long value) {
        balanceVersionAfter = value;
    }
    public BigDecimal getCostPrice() { return costPrice; }
    public void setCostPrice(BigDecimal value) { costPrice = value; }
    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal value) { totalCost = value; }
    public BigDecimal getAcceptedReceivedQuantity() {
        return acceptedReceivedQuantity;
    }
    public void setAcceptedReceivedQuantity(BigDecimal value) {
        acceptedReceivedQuantity = value;
    }
    public BigDecimal getDamagedReceivedQuantity() {
        return damagedReceivedQuantity;
    }
    public void setDamagedReceivedQuantity(BigDecimal value) {
        damagedReceivedQuantity = value;
    }
    public BigDecimal getShortageReportedQuantity() {
        return shortageReportedQuantity;
    }
    public void setShortageReportedQuantity(BigDecimal value) {
        shortageReportedQuantity = value;
    }
    public Long getReceiptVersion() { return receiptVersion; }
    public void setReceiptVersion(Long value) { receiptVersion = value; }
}
