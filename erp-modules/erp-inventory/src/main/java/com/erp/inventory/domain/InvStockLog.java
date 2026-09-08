package com.erp.inventory.domain;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import com.erp.common.core.annotation.Excel;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;

public class InvStockLog
{
    private Map<String, Object> params = new HashMap<>();

    private Long logId;

    private String keyword;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date beginTime;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date endTime;

    private String itemType;

    private Long itemId;

    private Long productId;

    private String itemCode;

    private String itemName;

    @Excel(name = "商品编码")
    private String productCode;

    @Excel(name = "商品名称")
    private String productName;

    private Long shopDeptId;

    @Excel(name = "库存组织")
    private String shopDeptName;

    private Long warehouseId;

    @Excel(name = "库存仓库")
    private String warehouseName;

    @Excel(name = "批次号")
    private String batchNo;

    @Excel(name = "效期", dateFormat = "yyyy-MM-dd")
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date expiryDate;

    @Excel(name = "序列号")
    private String serialNo;

    @Excel(name = "库位编码")
    private String locationCode;

    @Excel(name = "库位名称")
    private String locationName;

    @Excel(name = "变动类型", readConverterExp = "purchase_in=采购入库,outbound_out=出库,sales_out=销售出库,adjustment=库存调整,purchase_return=采购退货,sales_return=销售退货,transfer_out=调拨出库,transfer_in=调拨入库,check=库存盘点,purchase_return_out=采购退货出库,sales_return_in=销售退货入库,stock_check_profit=盘盈入库,stock_check_loss=盘亏出库")
    private String movementType;

    @Excel(name = "业务类型", readConverterExp = "purchase=采购单,sales=销售发货,outbound=销售出库,stock=库存调整,stock_check=库存盘点,purchase_return=采购退货,sales_return=销售退货,transfer=调拨,cross_store_transfer=异店调货")
    private String businessType;

    private Long businessId;

    @Excel(name = "业务单号")
    private String businessNo;

    @Excel(name = "变动数量")
    private BigDecimal changeQuantity;

    @Excel(name = "变动前数量")
    private BigDecimal beforeQuantity;

    @Excel(name = "变动后数量")
    private BigDecimal afterQuantity;

    @Excel(name = "单位成本")
    private BigDecimal costPrice;

    @Excel(name = "操作人")
    private String createBy;

    @Excel(name = "操作时间", dateFormat = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    @Excel(name = "备注")
    private String remark;

    public Long getLogId() { return logId; }
    public void setLogId(Long logId) { this.logId = logId; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public Date getBeginTime() { return beginTime; }
    public void setBeginTime(Date beginTime) { this.beginTime = beginTime; }
    public Date getEndTime() { return endTime; }
    public void setEndTime(Date endTime) { this.endTime = endTime; }
    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public Long getShopDeptId() { return shopDeptId; }
    public void setShopDeptId(Long shopDeptId) { this.shopDeptId = shopDeptId; }
    public String getShopDeptName() { return shopDeptName; }
    public void setShopDeptName(String shopDeptName) { this.shopDeptName = shopDeptName; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public String getWarehouseName() { return warehouseName; }
    public void setWarehouseName(String warehouseName) { this.warehouseName = warehouseName; }
    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
    public Date getExpiryDate() { return expiryDate; }
    public void setExpiryDate(Date expiryDate) { this.expiryDate = expiryDate; }
    public String getSerialNo() { return serialNo; }
    public void setSerialNo(String serialNo) { this.serialNo = serialNo; }
    public String getLocationCode() { return locationCode; }
    public void setLocationCode(String locationCode) { this.locationCode = locationCode; }
    public String getLocationName() { return locationName; }
    public void setLocationName(String locationName) { this.locationName = locationName; }
    public String getMovementType() { return movementType; }
    public void setMovementType(String movementType) { this.movementType = movementType; }
    public String getBusinessType() { return businessType; }
    public void setBusinessType(String businessType) { this.businessType = businessType; }
    public Long getBusinessId() { return businessId; }
    public void setBusinessId(Long businessId) { this.businessId = businessId; }
    public String getBusinessNo() { return businessNo; }
    public void setBusinessNo(String businessNo) { this.businessNo = businessNo; }
    public BigDecimal getChangeQuantity() { return changeQuantity; }
    public void setChangeQuantity(BigDecimal changeQuantity) { this.changeQuantity = changeQuantity; }
    public BigDecimal getBeforeQuantity() { return beforeQuantity; }
    public void setBeforeQuantity(BigDecimal beforeQuantity) { this.beforeQuantity = beforeQuantity; }
    public BigDecimal getAfterQuantity() { return afterQuantity; }
    public void setAfterQuantity(BigDecimal afterQuantity) { this.afterQuantity = afterQuantity; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public BigDecimal getCostPrice() { return costPrice; }
    public void setCostPrice(BigDecimal costPrice) { this.costPrice = costPrice; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Map<String, Object> getParams() { return params; }
    public void setParams(Map<String, Object> params) { this.params = params; }
}
