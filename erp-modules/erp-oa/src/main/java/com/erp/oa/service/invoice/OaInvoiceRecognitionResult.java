package com.erp.oa.service.invoice;

import java.math.BigDecimal;
import java.util.Date;

public class OaInvoiceRecognitionResult
{
    private String status;
    private String requestedEngine;
    private String engine;
    private String provider;
    private String message;
    private String invoiceType;
    private String invoiceCode;
    private String invoiceNumber;
    private Date invoiceDate;
    private String sellerName;
    private String sellerTaxNo;
    private String purchaserName;
    private String purchaserTaxNo;
    private BigDecimal amountWithoutTax;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private String checkCode;
    private String serviceType;
    private String commoditySummary;
    private BigDecimal confidence;
    private String rawText;
    private String rawPayload;

    public static OaInvoiceRecognitionResult failure(String requestedEngine,
            String engine, String provider, String status, String message)
    {
        OaInvoiceRecognitionResult value = new OaInvoiceRecognitionResult();
        value.setRequestedEngine(requestedEngine);
        value.setEngine(engine);
        value.setProvider(provider);
        value.setStatus(status);
        value.setMessage(message);
        return value;
    }

    public boolean hasRecognizedFields()
    {
        return notBlank(invoiceNumber) || invoiceDate != null
                || notBlank(sellerName) || totalAmount != null;
    }

    public int recognizedFieldCount()
    {
        int count = 0;
        count += notBlank(invoiceNumber) ? 1 : 0;
        count += invoiceDate == null ? 0 : 1;
        count += notBlank(sellerName) ? 1 : 0;
        count += totalAmount == null ? 0 : 1;
        count += notBlank(invoiceCode) ? 1 : 0;
        count += notBlank(sellerTaxNo) ? 1 : 0;
        return count;
    }

    private boolean notBlank(String value)
    {
        return value != null && !value.isBlank();
    }

    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public String getRequestedEngine() { return requestedEngine; }
    public void setRequestedEngine(String value) { requestedEngine = value; }
    public String getEngine() { return engine; }
    public void setEngine(String value) { engine = value; }
    public String getProvider() { return provider; }
    public void setProvider(String value) { provider = value; }
    public String getMessage() { return message; }
    public void setMessage(String value) { message = value; }
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
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal value) { totalAmount = value; }
    public String getCheckCode() { return checkCode; }
    public void setCheckCode(String value) { checkCode = value; }
    public String getServiceType() { return serviceType; }
    public void setServiceType(String value) { serviceType = value; }
    public String getCommoditySummary() { return commoditySummary; }
    public void setCommoditySummary(String value) { commoditySummary = value; }
    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal value) { confidence = value; }
    public String getRawText() { return rawText; }
    public void setRawText(String value) { rawText = value; }
    public String getRawPayload() { return rawPayload; }
    public void setRawPayload(String value) { rawPayload = value; }
}
