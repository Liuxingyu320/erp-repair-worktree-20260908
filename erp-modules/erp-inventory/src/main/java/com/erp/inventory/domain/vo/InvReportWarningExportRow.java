package com.erp.inventory.domain.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import com.erp.common.core.annotation.Excel;
import com.erp.inventory.domain.InvStock;

public class InvReportWarningExportRow implements Serializable
{
    private static final long serialVersionUID = 1L;

    @Excel(name = "商品编码")
    private String productCode;

    @Excel(name = "商品名称")
    private String productName;

    @Excel(name = "商品分类")
    private String categoryName;

    @Excel(name = "规格")
    private String spec;

    @Excel(name = "单位")
    private String unit;

    @Excel(name = "库存组织")
    private String inventoryOrganizationName;

    @Excel(name = "批次号")
    private String batchNo;

    @Excel(name = "效期", dateFormat = "yyyy-MM-dd")
    private Date expiryDate;

    @Excel(name = "序列号")
    private String serialNo;

    @Excel(name = "库位编码")
    private String locationCode;

    @Excel(name = "库位名称")
    private String locationName;

    @Excel(name = "当前库存")
    private BigDecimal currentQuantity;

    @Excel(name = "可用库存")
    private BigDecimal availableQuantity;

    @Excel(name = "安全下限")
    private BigDecimal safetyStockMin;

    @Excel(name = "预警缺口")
    private BigDecimal warningGap;

    @Excel(name = "预警状态")
    private String warningStatus;

    @Excel(name = "最后库存变动", dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date lastMovementTime;

    public static InvReportWarningExportRow from(InvStock stock)
    {
        InvStock source = stock == null ? new InvStock() : stock;
        InvReportWarningExportRow row = new InvReportWarningExportRow();
        row.setProductCode(source.getProductCode());
        row.setProductName(source.getProductName());
        row.setCategoryName(firstText(source.getCategoryFullPath(), source.getCategoryName()));
        row.setSpec(source.getSpec());
        row.setUnit(source.getUnit());
        row.setInventoryOrganizationName(firstText(
                source.getWarehouseName(), source.getShopDeptName()));
        row.setBatchNo(source.getBatchNo());
        row.setExpiryDate(source.getExpiryDate());
        row.setSerialNo(source.getSerialNo());
        row.setLocationCode(source.getLocationCode());
        row.setLocationName(source.getLocationName());
        BigDecimal current = defaultZero(source.getCurrentQuantity());
        BigDecimal available = source.getAvailableQuantity() == null
                ? current : source.getAvailableQuantity();
        BigDecimal safety = defaultZero(source.getSafetyStockMin());
        row.setCurrentQuantity(current);
        row.setAvailableQuantity(available);
        row.setSafetyStockMin(safety);
        if (safety.signum() <= 0)
        {
            row.setWarningGap(null);
            row.setWarningStatus("阈值未配置");
        }
        else
        {
            row.setWarningGap(safety.subtract(available).max(BigDecimal.ZERO));
            row.setWarningStatus(available.signum() <= 0 ? "缺货" : "低库存");
        }
        row.setLastMovementTime(latest(source.getLastInTime(), source.getLastOutTime()));
        return row;
    }

    private static BigDecimal defaultZero(BigDecimal value)
    {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static Date latest(Date first, Date second)
    {
        if (first == null)
        {
            return second;
        }
        if (second == null)
        {
            return first;
        }
        return first.after(second) ? first : second;
    }

    private static String firstText(String first, String second)
    {
        if (first != null && !first.isBlank())
        {
            return first.trim();
        }
        return second == null ? null : second.trim();
    }

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public String getSpec() { return spec; }
    public void setSpec(String spec) { this.spec = spec; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getInventoryOrganizationName() { return inventoryOrganizationName; }
    public void setInventoryOrganizationName(String inventoryOrganizationName) { this.inventoryOrganizationName = inventoryOrganizationName; }
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
    public BigDecimal getCurrentQuantity() { return currentQuantity; }
    public void setCurrentQuantity(BigDecimal currentQuantity) { this.currentQuantity = currentQuantity; }
    public BigDecimal getAvailableQuantity() { return availableQuantity; }
    public void setAvailableQuantity(BigDecimal availableQuantity) { this.availableQuantity = availableQuantity; }
    public BigDecimal getSafetyStockMin() { return safetyStockMin; }
    public void setSafetyStockMin(BigDecimal safetyStockMin) { this.safetyStockMin = safetyStockMin; }
    public BigDecimal getWarningGap() { return warningGap; }
    public void setWarningGap(BigDecimal warningGap) { this.warningGap = warningGap; }
    public String getWarningStatus() { return warningStatus; }
    public void setWarningStatus(String warningStatus) { this.warningStatus = warningStatus; }
    public Date getLastMovementTime() { return lastMovementTime; }
    public void setLastMovementTime(Date lastMovementTime) { this.lastMovementTime = lastMovementTime; }
}
