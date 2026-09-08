package com.erp.inventory.domain.dto;

import java.math.BigDecimal;
import java.util.Date;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonFormat;

public class InvStockAdjustRequest
{
    private String itemType;
    private Long itemId;
    private Long productId;
    private Long shopDeptId;
    private Long warehouseId;
    private String batchNo;
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date expiryDate;
    private String serialNo;
    private String locationCode;
    private String locationName;
    private BigDecimal adjustQuantity;
    private String reason;

    @Size(max = 20, message = "物料类型长度不能超过20个字符")
    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }
    @Positive(message = "物料ID必须大于0")
    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    @Positive(message = "商品ID必须大于0")
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    @Positive(message = "库存组织ID必须大于0")
    public Long getShopDeptId() { return shopDeptId; }
    public void setShopDeptId(Long shopDeptId) { this.shopDeptId = shopDeptId; }
    @Positive(message = "仓库ID必须大于0")
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    @Size(max = 64, message = "批次号长度不能超过64个字符")
    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
    public Date getExpiryDate() { return expiryDate; }
    public void setExpiryDate(Date expiryDate) { this.expiryDate = expiryDate; }
    @Size(max = 64, message = "序列号长度不能超过64个字符")
    public String getSerialNo() { return serialNo; }
    public void setSerialNo(String serialNo) { this.serialNo = serialNo; }
    @Size(max = 64, message = "库位编码长度不能超过64个字符")
    public String getLocationCode() { return locationCode; }
    public void setLocationCode(String locationCode) { this.locationCode = locationCode; }
    @Size(max = 128, message = "库位名称长度不能超过128个字符")
    public String getLocationName() { return locationName; }
    public void setLocationName(String locationName) { this.locationName = locationName; }
    @NotNull(message = "调整数量不能为空")
    @Digits(integer = 14, fraction = 4, message = "调整数量最多14位整数和4位小数")
    public BigDecimal getAdjustQuantity() { return adjustQuantity; }
    public void setAdjustQuantity(BigDecimal adjustQuantity) { this.adjustQuantity = adjustQuantity; }
    @Size(max = 500, message = "调整原因长度不能超过500个字符")
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
