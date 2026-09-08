package com.erp.inventory.domain.transfer;

import java.math.BigDecimal;

public class InvTransferShipmentAllocationRecord
{
    private Long allocationId;
    private Long shipmentId;
    private Long shipmentDetailId;
    private Long transferId;
    private Long transferDetailId;
    private Long balanceId;
    private Long lotId;
    private Long locationId;
    private Integer policyRank;
    private String allocationPolicy;
    private String trackingPolicy;
    private BigDecimal allocatedQuantity;
    private Long balanceVersionBefore;
    private Long balanceVersionAfter;
    private BigDecimal beforeQuantity;
    private BigDecimal afterQuantity;
    private BigDecimal costPrice;
    private BigDecimal totalCost;
    private Long summaryStockLogId;
    private String commandRequestId;
    private String createBy;

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
    public Long getBalanceId() { return balanceId; }
    public void setBalanceId(Long value) { balanceId = value; }
    public Long getLotId() { return lotId; }
    public void setLotId(Long value) { lotId = value; }
    public Long getLocationId() { return locationId; }
    public void setLocationId(Long value) { locationId = value; }
    public Integer getPolicyRank() { return policyRank; }
    public void setPolicyRank(Integer value) { policyRank = value; }
    public String getAllocationPolicy() { return allocationPolicy; }
    public void setAllocationPolicy(String value) { allocationPolicy = value; }
    public String getTrackingPolicy() { return trackingPolicy; }
    public void setTrackingPolicy(String value) { trackingPolicy = value; }
    public BigDecimal getAllocatedQuantity() { return allocatedQuantity; }
    public void setAllocatedQuantity(BigDecimal value) { allocatedQuantity = value; }
    public Long getBalanceVersionBefore() { return balanceVersionBefore; }
    public void setBalanceVersionBefore(Long value) { balanceVersionBefore = value; }
    public Long getBalanceVersionAfter() { return balanceVersionAfter; }
    public void setBalanceVersionAfter(Long value) { balanceVersionAfter = value; }
    public BigDecimal getBeforeQuantity() { return beforeQuantity; }
    public void setBeforeQuantity(BigDecimal value) { beforeQuantity = value; }
    public BigDecimal getAfterQuantity() { return afterQuantity; }
    public void setAfterQuantity(BigDecimal value) { afterQuantity = value; }
    public BigDecimal getCostPrice() { return costPrice; }
    public void setCostPrice(BigDecimal value) { costPrice = value; }
    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal value) { totalCost = value; }
    public Long getSummaryStockLogId() { return summaryStockLogId; }
    public void setSummaryStockLogId(Long value) { summaryStockLogId = value; }
    public String getCommandRequestId() { return commandRequestId; }
    public void setCommandRequestId(String value) { commandRequestId = value; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String value) { createBy = value; }
}
