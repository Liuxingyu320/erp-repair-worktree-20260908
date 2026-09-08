package com.erp.oa.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonFormat;

public class OaReimbursementInvoice implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long invoiceId;
    private Long reimbursementId;
    private Long itemId;
    private Boolean idempotentReplay;
    private String originalName;

    @JsonIgnore
    private String storedName;

    @JsonIgnore
    private String storagePath;

    private String contentType;
    private String fileExtension;
    private Long fileSize;

    @JsonIgnore
    private String sha256;
    private String duplicateStatus;

    @JsonIgnore
    private Long duplicateReimbursementId;
    private String recognitionStatus;
    private String requestedEngine;
    private String recognitionEngine;
    private String recognitionProvider;
    private String recognitionMessage;
    private String invoiceType;
    private String invoiceCode;
    private String invoiceNumber;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date invoiceDate;

    private String sellerName;
    private String sellerTaxNo;
    private String purchaserName;
    private String purchaserTaxNo;
    private BigDecimal amountWithoutTax;
    private BigDecimal taxAmount;
    private BigDecimal invoiceTotalAmount;
    private String checkCode;
    private String serviceType;
    private String commoditySummary;
    private BigDecimal recognitionConfidence;

    @JsonIgnore
    private String recognitionRawText;

    @JsonIgnore
    private String recognitionRawPayload;

    private Date recognizedTime;
    private Long correctedBy;
    private Date correctedTime;
    private Integer sortNo;
    private Long uploadedBy;
    private String uploadedByName;
    private Date createTime;

    public Long getInvoiceId() { return invoiceId; }
    public void setInvoiceId(Long value) { invoiceId = value; }
    public Long getReimbursementId() { return reimbursementId; }
    public void setReimbursementId(Long value) { reimbursementId = value; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long value) { itemId = value; }
    public Boolean getIdempotentReplay() { return idempotentReplay; }
    public void setIdempotentReplay(Boolean value) { idempotentReplay = value; }
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String value) { originalName = value; }
    public String getStoredName() { return storedName; }
    public void setStoredName(String value) { storedName = value; }
    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String value) { storagePath = value; }
    public String getContentType() { return contentType; }
    public void setContentType(String value) { contentType = value; }
    public String getFileExtension() { return fileExtension; }
    public void setFileExtension(String value) { fileExtension = value; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long value) { fileSize = value; }
    public String getSha256() { return sha256; }
    public void setSha256(String value) { sha256 = value; }
    public String getDuplicateStatus() { return duplicateStatus; }
    public void setDuplicateStatus(String value) { duplicateStatus = value; }
    public Long getDuplicateReimbursementId() { return duplicateReimbursementId; }
    public void setDuplicateReimbursementId(Long value) { duplicateReimbursementId = value; }
    public String getRecognitionStatus() { return recognitionStatus; }
    public void setRecognitionStatus(String value) { recognitionStatus = value; }
    public String getRequestedEngine() { return requestedEngine; }
    public void setRequestedEngine(String value) { requestedEngine = value; }
    public String getRecognitionEngine() { return recognitionEngine; }
    public void setRecognitionEngine(String value) { recognitionEngine = value; }
    public String getRecognitionProvider() { return recognitionProvider; }
    public void setRecognitionProvider(String value) { recognitionProvider = value; }
    public String getRecognitionMessage() { return recognitionMessage; }
    public void setRecognitionMessage(String value) { recognitionMessage = value; }
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
    public BigDecimal getRecognitionConfidence() { return recognitionConfidence; }
    public void setRecognitionConfidence(BigDecimal value) { recognitionConfidence = value; }
    public String getRecognitionRawText() { return recognitionRawText; }
    public void setRecognitionRawText(String value) { recognitionRawText = value; }
    public String getRecognitionRawPayload() { return recognitionRawPayload; }
    public void setRecognitionRawPayload(String value) { recognitionRawPayload = value; }
    public Date getRecognizedTime() { return recognizedTime; }
    public void setRecognizedTime(Date value) { recognizedTime = value; }
    public Long getCorrectedBy() { return correctedBy; }
    public void setCorrectedBy(Long value) { correctedBy = value; }
    public Date getCorrectedTime() { return correctedTime; }
    public void setCorrectedTime(Date value) { correctedTime = value; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer value) { sortNo = value; }
    public Long getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(Long value) { uploadedBy = value; }
    public String getUploadedByName() { return uploadedByName; }
    public void setUploadedByName(String value) { uploadedByName = value; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date value) { createTime = value; }
}
