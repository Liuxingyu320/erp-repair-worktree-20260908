package com.erp.inventory.domain.transfer;

import java.math.BigDecimal;

/** Child-transfer facts read under the asynchronous workflow lock. */
public class
        InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowChildFact
{
    private Long transferId;
    private String transferType;
    private String sourceBusinessType;
    private Long sourceBusinessId;
    private Long sourceLocationDeptId;
    private Long targetLocationDeptId;
    private String status;
    private Integer detailCount;
    private String itemType;
    private Long itemId;
    private Long productId;
    private BigDecimal requestedQuantity;
    private BigDecimal deliveredQuantity;
    private String reservationKind;
    private Integer reservationCount;
    private BigDecimal reservedQuantity;
    private String reservationStatus;
    private BigDecimal consumedQuantity;
    private BigDecimal releasedQuantity;
    private Integer v2ShipmentCount;
    private Integer legacyShipmentCount;
    private Integer receiptCount;
    private Integer openDiscrepancyCount;

    public Long getTransferId() { return transferId; }
    public void setTransferId(Long value) { transferId = value; }
    public String getTransferType() { return transferType; }
    public void setTransferType(String value) { transferType = value; }
    public String getSourceBusinessType() { return sourceBusinessType; }
    public void setSourceBusinessType(String value) {
        sourceBusinessType = value;
    }
    public Long getSourceBusinessId() { return sourceBusinessId; }
    public void setSourceBusinessId(Long value) { sourceBusinessId = value; }
    public Long getSourceLocationDeptId() { return sourceLocationDeptId; }
    public void setSourceLocationDeptId(Long value) {
        sourceLocationDeptId = value;
    }
    public Long getTargetLocationDeptId() { return targetLocationDeptId; }
    public void setTargetLocationDeptId(Long value) {
        targetLocationDeptId = value;
    }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public Integer getDetailCount() { return detailCount; }
    public void setDetailCount(Integer value) { detailCount = value; }
    public String getItemType() { return itemType; }
    public void setItemType(String value) { itemType = value; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long value) { itemId = value; }
    public Long getProductId() { return productId; }
    public void setProductId(Long value) { productId = value; }
    public BigDecimal getRequestedQuantity() { return requestedQuantity; }
    public void setRequestedQuantity(BigDecimal value) {
        requestedQuantity = value;
    }
    public BigDecimal getDeliveredQuantity() { return deliveredQuantity; }
    public void setDeliveredQuantity(BigDecimal value) {
        deliveredQuantity = value;
    }
    public String getReservationKind() { return reservationKind; }
    public void setReservationKind(String value) { reservationKind = value; }
    public Integer getReservationCount() { return reservationCount; }
    public void setReservationCount(Integer value) {
        reservationCount = value;
    }
    public BigDecimal getReservedQuantity() { return reservedQuantity; }
    public void setReservedQuantity(BigDecimal value) {
        reservedQuantity = value;
    }
    public String getReservationStatus() { return reservationStatus; }
    public void setReservationStatus(String value) {
        reservationStatus = value;
    }
    public BigDecimal getConsumedQuantity() { return consumedQuantity; }
    public void setConsumedQuantity(BigDecimal value) {
        consumedQuantity = value;
    }
    public BigDecimal getReleasedQuantity() { return releasedQuantity; }
    public void setReleasedQuantity(BigDecimal value) {
        releasedQuantity = value;
    }
    public Integer getV2ShipmentCount() { return v2ShipmentCount; }
    public void setV2ShipmentCount(Integer value) {
        v2ShipmentCount = value;
    }
    public Integer getLegacyShipmentCount() { return legacyShipmentCount; }
    public void setLegacyShipmentCount(Integer value) {
        legacyShipmentCount = value;
    }
    public Integer getReceiptCount() { return receiptCount; }
    public void setReceiptCount(Integer value) { receiptCount = value; }
    public Integer getOpenDiscrepancyCount() {
        return openDiscrepancyCount;
    }
    public void setOpenDiscrepancyCount(Integer value) {
        openDiscrepancyCount = value;
    }

    public InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
            .Child toPolicyChild()
    {
        return new InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
                .Child(transferId, transferType, sourceBusinessType,
                        sourceBusinessId, sourceLocationDeptId,
                        targetLocationDeptId, status, detailCount,
                        itemType, itemId, productId, requestedQuantity,
                        deliveredQuantity, reservationKind,
                        reservationCount, reservedQuantity,
                        reservationStatus, consumedQuantity,
                        releasedQuantity,
                        v2ShipmentCount, legacyShipmentCount,
                        receiptCount, openDiscrepancyCount);
    }
}
