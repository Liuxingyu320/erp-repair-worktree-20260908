package com.erp.inventory.domain.transfer;

import java.math.BigDecimal;

/** Locked V2 receipt-allocation anchor for one damaged-stock write-off. */
public class InvTransferReceiptDiscrepancyDamageWriteOffFact
{
    private Long caseId;
    private Long receiptId;
    private Long receiptAllocationId;
    private Long shipmentId;
    private Long transferId;
    private Long shipmentAllocationId;
    private Long targetWarehouseId;
    private String itemType;
    private Long itemId;
    private Long productId;
    private String trackingPolicy;
    private BigDecimal damagedQuantity;
    private BigDecimal sourceCostPrice;
    private BigDecimal damagedCost;
    private Long damagedTargetLotId;
    private Long quarantineLocationId;
    private Long damagedTargetBalanceId;

    public Long getCaseId() { return caseId; }
    public void setCaseId(Long value) { caseId = value; }
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
    public Long getTargetWarehouseId() { return targetWarehouseId; }
    public void setTargetWarehouseId(Long value) {
        targetWarehouseId = value;
    }
    public String getItemType() { return itemType; }
    public void setItemType(String value) { itemType = value; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long value) { itemId = value; }
    public Long getProductId() { return productId; }
    public void setProductId(Long value) { productId = value; }
    public String getTrackingPolicy() { return trackingPolicy; }
    public void setTrackingPolicy(String value) { trackingPolicy = value; }
    public BigDecimal getDamagedQuantity() { return damagedQuantity; }
    public void setDamagedQuantity(BigDecimal value) {
        damagedQuantity = value;
    }
    public BigDecimal getSourceCostPrice() { return sourceCostPrice; }
    public void setSourceCostPrice(BigDecimal value) {
        sourceCostPrice = value;
    }
    public BigDecimal getDamagedCost() { return damagedCost; }
    public void setDamagedCost(BigDecimal value) { damagedCost = value; }
    public Long getDamagedTargetLotId() { return damagedTargetLotId; }
    public void setDamagedTargetLotId(Long value) {
        damagedTargetLotId = value;
    }
    public Long getQuarantineLocationId() { return quarantineLocationId; }
    public void setQuarantineLocationId(Long value) {
        quarantineLocationId = value;
    }
    public Long getDamagedTargetBalanceId() {
        return damagedTargetBalanceId;
    }
    public void setDamagedTargetBalanceId(Long value) {
        damagedTargetBalanceId = value;
    }
}
