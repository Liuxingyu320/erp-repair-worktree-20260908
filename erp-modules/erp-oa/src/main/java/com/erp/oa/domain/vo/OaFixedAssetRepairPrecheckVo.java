package com.erp.oa.domain.vo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 固定资产上报预校验结果和同款购买参考。 */
public class OaFixedAssetRepairPrecheckVo
{
    private Boolean allowed;
    private String errorCode;
    private String message;
    private BigDecimal quotaUsageAmount;
    private BigDecimal availableQuotaAmount;
    private Long oeItemId;
    private String oeItemCode;
    private String oeItemName;
    private String itemDescription;
    private String orderUnit;
    private String imageUrl;
    private String purchaseReferenceUrl;
    private String purchaseReferenceNote;
    private Boolean purchaseReferenceReady;
    private List<String> missingPurchaseReferenceFields = new ArrayList<>();

    public Boolean getAllowed() { return allowed; }
    public void setAllowed(Boolean allowed) { this.allowed = allowed; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public BigDecimal getQuotaUsageAmount() { return quotaUsageAmount; }
    public void setQuotaUsageAmount(BigDecimal quotaUsageAmount) { this.quotaUsageAmount = quotaUsageAmount; }
    public BigDecimal getAvailableQuotaAmount() { return availableQuotaAmount; }
    public void setAvailableQuotaAmount(BigDecimal availableQuotaAmount) { this.availableQuotaAmount = availableQuotaAmount; }
    public Long getOeItemId() { return oeItemId; }
    public void setOeItemId(Long oeItemId) { this.oeItemId = oeItemId; }
    public String getOeItemCode() { return oeItemCode; }
    public void setOeItemCode(String oeItemCode) { this.oeItemCode = oeItemCode; }
    public String getOeItemName() { return oeItemName; }
    public void setOeItemName(String oeItemName) { this.oeItemName = oeItemName; }
    public String getItemDescription() { return itemDescription; }
    public void setItemDescription(String itemDescription) { this.itemDescription = itemDescription; }
    public String getOrderUnit() { return orderUnit; }
    public void setOrderUnit(String orderUnit) { this.orderUnit = orderUnit; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getPurchaseReferenceUrl() { return purchaseReferenceUrl; }
    public void setPurchaseReferenceUrl(String purchaseReferenceUrl) { this.purchaseReferenceUrl = purchaseReferenceUrl; }
    public String getPurchaseReferenceNote() { return purchaseReferenceNote; }
    public void setPurchaseReferenceNote(String purchaseReferenceNote) { this.purchaseReferenceNote = purchaseReferenceNote; }
    public Boolean getPurchaseReferenceReady() { return purchaseReferenceReady; }
    public void setPurchaseReferenceReady(Boolean purchaseReferenceReady) { this.purchaseReferenceReady = purchaseReferenceReady; }
    public List<String> getMissingPurchaseReferenceFields() { return missingPurchaseReferenceFields; }
    public void setMissingPurchaseReferenceFields(List<String> missingPurchaseReferenceFields)
    {
        this.missingPurchaseReferenceFields = missingPurchaseReferenceFields == null
                ? new ArrayList<>() : new ArrayList<>(missingPurchaseReferenceFields);
    }
}
