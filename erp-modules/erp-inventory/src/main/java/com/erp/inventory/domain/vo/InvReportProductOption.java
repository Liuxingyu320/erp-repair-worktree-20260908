package com.erp.inventory.domain.vo;

import java.io.Serializable;

public class InvReportProductOption implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long productId;
    private String productCode;
    private String productName;
    private String spec;
    private String unit;

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getSpec() { return spec; }
    public void setSpec(String spec) { this.spec = spec; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
}
