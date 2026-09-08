package com.erp.inventory.domain.transfer;

import java.math.BigDecimal;
import java.util.Date;

/** Read-only source allocation fact used by V2 receipt planning. */
public class InvTransferReceiptPlanningAllocationFact
{
    private Long allocationId;
    private Long shipmentId;
    private Long shipmentDetailId;
    private Long transferId;
    private Long transferDetailId;
    private String itemType;
    private Long itemId;
    private Long productId;
    private String itemCode;
    private String itemName;
    private String unit;
    private String allocationPolicy;
    private String trackingPolicy;
    private BigDecimal allocatedQuantity;
    private Long balanceVersionAfter;
    private BigDecimal costPrice;
    private BigDecimal totalCost;
    private Long sourceWarehouseId;
    private Long sourceBalanceId;
    private Long sourceLotId;
    private Long sourceLocationId;
    private String sourceLotNo;
    private String supplierBatchNo;
    private Date productionDate;
    private Date expiryDate;
    private String qcStatus;
    private String lotStatus;
    private String sourceReceiptDisposition;
    private String sourceLocationCode;
    private String sourceLocationType;
    private BigDecimal shipmentDetailShippedQuantity;
    private BigDecimal shipmentDetailReceivedQuantity;
    private BigDecimal transferDetailRequestedQuantity;
    private BigDecimal transferDetailDeliveredQuantity;
    private BigDecimal transferDetailReceivedQuantity;
    private BigDecimal acceptedReceivedQuantity;
    private BigDecimal damagedReceivedQuantity;
    private BigDecimal shortageReportedQuantity;
    private Long receiptVersion;

    public Long getAllocationId() { return allocationId; }
    public void setAllocationId(Long value) { allocationId = value; }
    public Long getShipmentId() { return shipmentId; }
    public void setShipmentId(Long value) { shipmentId = value; }
    public Long getShipmentDetailId() { return shipmentDetailId; }
    public void setShipmentDetailId(Long value) {
        shipmentDetailId = value;
    }
    public Long getTransferId() { return transferId; }
    public void setTransferId(Long value) { transferId = value; }
    public Long getTransferDetailId() { return transferDetailId; }
    public void setTransferDetailId(Long value) {
        transferDetailId = value;
    }
    public String getItemType() { return itemType; }
    public void setItemType(String value) { itemType = value; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long value) { itemId = value; }
    public Long getProductId() { return productId; }
    public void setProductId(Long value) { productId = value; }
    public String getItemCode() { return itemCode; }
    public void setItemCode(String value) { itemCode = value; }
    public String getItemName() { return itemName; }
    public void setItemName(String value) { itemName = value; }
    public String getUnit() { return unit; }
    public void setUnit(String value) { unit = value; }
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
    public Long getSourceWarehouseId() { return sourceWarehouseId; }
    public void setSourceWarehouseId(Long value) {
        sourceWarehouseId = value;
    }
    public Long getSourceBalanceId() { return sourceBalanceId; }
    public void setSourceBalanceId(Long value) { sourceBalanceId = value; }
    public Long getSourceLotId() { return sourceLotId; }
    public void setSourceLotId(Long value) { sourceLotId = value; }
    public Long getSourceLocationId() { return sourceLocationId; }
    public void setSourceLocationId(Long value) {
        sourceLocationId = value;
    }
    public String getSourceLotNo() { return sourceLotNo; }
    public void setSourceLotNo(String value) { sourceLotNo = value; }
    public String getSupplierBatchNo() { return supplierBatchNo; }
    public void setSupplierBatchNo(String value) { supplierBatchNo = value; }
    public Date getProductionDate() { return productionDate; }
    public void setProductionDate(Date value) { productionDate = value; }
    public Date getExpiryDate() { return expiryDate; }
    public void setExpiryDate(Date value) { expiryDate = value; }
    public String getQcStatus() { return qcStatus; }
    public void setQcStatus(String value) { qcStatus = value; }
    public String getLotStatus() { return lotStatus; }
    public void setLotStatus(String value) { lotStatus = value; }
    public String getSourceReceiptDisposition() {
        return sourceReceiptDisposition;
    }
    public void setSourceReceiptDisposition(String value) {
        sourceReceiptDisposition = value;
    }
    public String getSourceLocationCode() { return sourceLocationCode; }
    public void setSourceLocationCode(String value) {
        sourceLocationCode = value;
    }
    public String getSourceLocationType() { return sourceLocationType; }
    public void setSourceLocationType(String value) {
        sourceLocationType = value;
    }
    public BigDecimal getShipmentDetailShippedQuantity() {
        return shipmentDetailShippedQuantity;
    }
    public void setShipmentDetailShippedQuantity(BigDecimal value) {
        shipmentDetailShippedQuantity = value;
    }
    public BigDecimal getShipmentDetailReceivedQuantity() {
        return shipmentDetailReceivedQuantity;
    }
    public void setShipmentDetailReceivedQuantity(BigDecimal value) {
        shipmentDetailReceivedQuantity = value;
    }
    public BigDecimal getTransferDetailRequestedQuantity() {
        return transferDetailRequestedQuantity;
    }
    public void setTransferDetailRequestedQuantity(BigDecimal value) {
        transferDetailRequestedQuantity = value;
    }
    public BigDecimal getTransferDetailDeliveredQuantity() {
        return transferDetailDeliveredQuantity;
    }
    public void setTransferDetailDeliveredQuantity(BigDecimal value) {
        transferDetailDeliveredQuantity = value;
    }
    public BigDecimal getTransferDetailReceivedQuantity() {
        return transferDetailReceivedQuantity;
    }
    public void setTransferDetailReceivedQuantity(BigDecimal value) {
        transferDetailReceivedQuantity = value;
    }
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
