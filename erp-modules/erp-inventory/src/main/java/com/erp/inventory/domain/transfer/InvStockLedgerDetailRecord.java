package com.erp.inventory.domain.transfer;

import java.math.BigDecimal;
import java.util.Date;

public class InvStockLedgerDetailRecord
{
    private Long ledgerId;
    private String requestId;
    private String commandRequestId;
    private Long shipmentAllocationId;
    private Long summaryStockLogId;
    private Long warehouseId;
    private String itemType;
    private Long itemId;
    private Long productId;
    private String movementType;
    private String businessType;
    private Long businessId;
    private String businessNo;
    private Long fromBalanceId;
    private Long fromLotId;
    private Long fromLocationId;
    private BigDecimal changeQuantity;
    private BigDecimal beforeQuantity;
    private BigDecimal afterQuantity;
    private BigDecimal costPrice;
    private BigDecimal totalCost;
    private Long operatorUserId;
    private String operatorName;
    private Date occurredTime;
    private String createBy;
    private String remark;

    public Long getLedgerId() { return ledgerId; }
    public void setLedgerId(Long value) { ledgerId = value; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String value) { requestId = value; }
    public String getCommandRequestId() { return commandRequestId; }
    public void setCommandRequestId(String value) { commandRequestId = value; }
    public Long getShipmentAllocationId() { return shipmentAllocationId; }
    public void setShipmentAllocationId(Long value) { shipmentAllocationId = value; }
    public Long getSummaryStockLogId() { return summaryStockLogId; }
    public void setSummaryStockLogId(Long value) { summaryStockLogId = value; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long value) { warehouseId = value; }
    public String getItemType() { return itemType; }
    public void setItemType(String value) { itemType = value; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long value) { itemId = value; }
    public Long getProductId() { return productId; }
    public void setProductId(Long value) { productId = value; }
    public String getMovementType() { return movementType; }
    public void setMovementType(String value) { movementType = value; }
    public String getBusinessType() { return businessType; }
    public void setBusinessType(String value) { businessType = value; }
    public Long getBusinessId() { return businessId; }
    public void setBusinessId(Long value) { businessId = value; }
    public String getBusinessNo() { return businessNo; }
    public void setBusinessNo(String value) { businessNo = value; }
    public Long getFromBalanceId() { return fromBalanceId; }
    public void setFromBalanceId(Long value) { fromBalanceId = value; }
    public Long getFromLotId() { return fromLotId; }
    public void setFromLotId(Long value) { fromLotId = value; }
    public Long getFromLocationId() { return fromLocationId; }
    public void setFromLocationId(Long value) { fromLocationId = value; }
    public BigDecimal getChangeQuantity() { return changeQuantity; }
    public void setChangeQuantity(BigDecimal value) { changeQuantity = value; }
    public BigDecimal getBeforeQuantity() { return beforeQuantity; }
    public void setBeforeQuantity(BigDecimal value) { beforeQuantity = value; }
    public BigDecimal getAfterQuantity() { return afterQuantity; }
    public void setAfterQuantity(BigDecimal value) { afterQuantity = value; }
    public BigDecimal getCostPrice() { return costPrice; }
    public void setCostPrice(BigDecimal value) { costPrice = value; }
    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal value) { totalCost = value; }
    public Long getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(Long value) { operatorUserId = value; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String value) { operatorName = value; }
    public Date getOccurredTime() { return occurredTime; }
    public void setOccurredTime(Date value) { occurredTime = value; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String value) { createBy = value; }
    public String getRemark() { return remark; }
    public void setRemark(String value) { remark = value; }
}
