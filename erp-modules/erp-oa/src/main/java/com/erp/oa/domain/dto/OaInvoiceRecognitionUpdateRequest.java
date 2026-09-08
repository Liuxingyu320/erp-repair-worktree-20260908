package com.erp.oa.domain.dto;

import java.math.BigDecimal;
import java.util.Date;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonFormat;

public class OaInvoiceRecognitionUpdateRequest
{
    @Size(max = 80, message = "发票类型不能超过80个字符")
    private String invoiceType;

    @Size(max = 32, message = "发票代码不能超过32个字符")
    private String invoiceCode;

    @Size(max = 40, message = "发票号码不能超过40个字符")
    private String invoiceNumber;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date invoiceDate;

    @Size(max = 200, message = "销售方名称不能超过200个字符")
    private String sellerName;

    @Size(max = 40, message = "销售方税号不能超过40个字符")
    private String sellerTaxNo;

    @Size(max = 200, message = "购买方名称不能超过200个字符")
    private String purchaserName;

    @Size(max = 40, message = "购买方税号不能超过40个字符")
    private String purchaserTaxNo;

    @DecimalMin(value = "0.00", message = "不含税金额不能小于0")
    private BigDecimal amountWithoutTax;

    @DecimalMin(value = "0.00", message = "税额不能小于0")
    private BigDecimal taxAmount;

    @DecimalMin(value = "0.00", message = "价税合计不能小于0")
    private BigDecimal invoiceTotalAmount;

    @Size(max = 40, message = "校验码不能超过40个字符")
    private String checkCode;

    @Size(max = 80, message = "消费类型不能超过80个字符")
    private String serviceType;

    @Size(max = 500, message = "商品摘要不能超过500个字符")
    private String commoditySummary;

    public String getInvoiceType() { return invoiceType; }
    public void setInvoiceType(String value) { invoiceType = value; }
    public String getInvoiceCode() { return invoiceCode; }
    public void setInvoiceCode(String value) { invoiceCode = value; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String value) { invoiceNumber = value; }
    public Date getInvoiceDate() { return invoiceDate; }
    public void setInvoiceDate(Date value) { invoiceDate = value; }
    public String getSellerName() { return sellerName; }
    public void setSellerName(String value) { sellerName = value; }
    public String getSellerTaxNo() { return sellerTaxNo; }
    public void setSellerTaxNo(String value) { sellerTaxNo = value; }
    public String getPurchaserName() { return purchaserName; }
    public void setPurchaserName(String value) { purchaserName = value; }
    public String getPurchaserTaxNo() { return purchaserTaxNo; }
    public void setPurchaserTaxNo(String value) { purchaserTaxNo = value; }
    public BigDecimal getAmountWithoutTax() { return amountWithoutTax; }
    public void setAmountWithoutTax(BigDecimal value) { amountWithoutTax = value; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal value) { taxAmount = value; }
    public BigDecimal getInvoiceTotalAmount() { return invoiceTotalAmount; }
    public void setInvoiceTotalAmount(BigDecimal value) { invoiceTotalAmount = value; }
    public String getCheckCode() { return checkCode; }
    public void setCheckCode(String value) { checkCode = value; }
    public String getServiceType() { return serviceType; }
    public void setServiceType(String value) { serviceType = value; }
    public String getCommoditySummary() { return commoditySummary; }
    public void setCommoditySummary(String value) { commoditySummary = value; }
}
