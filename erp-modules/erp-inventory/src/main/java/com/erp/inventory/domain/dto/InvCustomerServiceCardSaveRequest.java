package com.erp.inventory.domain.dto;

import java.math.BigDecimal;
import java.util.Date;
import jakarta.validation.constraints.NotBlank;

public class InvCustomerServiceCardSaveRequest
{
    private Long customerId;
    @NotBlank(message = "客户姓名不能为空")
    private String customerName;
    private String customerCode;
    private String contactPerson;
    private String contactPhone;
    private Long photoNodeId;
    private String teaPreferences;
    private String preferenceTags;
    private String brewingServicePreferences;
    private String cautions;
    private BigDecimal budgetMin;
    private BigDecimal budgetMax;
    private Date lastVisitDate;
    private Long version;
    private String requestKey;
    private String sourceClient;

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long value) { customerId = value; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String value) { customerName = value; }
    public String getCustomerCode() { return customerCode; }
    public void setCustomerCode(String value) { customerCode = value; }
    public String getContactPerson() { return contactPerson; }
    public void setContactPerson(String value) { contactPerson = value; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String value) { contactPhone = value; }
    public Long getPhotoNodeId() { return photoNodeId; }
    public void setPhotoNodeId(Long value) { photoNodeId = value; }
    public String getTeaPreferences() { return teaPreferences; }
    public void setTeaPreferences(String value) { teaPreferences = value; }
    public String getPreferenceTags() { return preferenceTags; }
    public void setPreferenceTags(String value) { preferenceTags = value; }
    public String getBrewingServicePreferences() { return brewingServicePreferences; }
    public void setBrewingServicePreferences(String value) { brewingServicePreferences = value; }
    public String getCautions() { return cautions; }
    public void setCautions(String value) { cautions = value; }
    public BigDecimal getBudgetMin() { return budgetMin; }
    public void setBudgetMin(BigDecimal value) { budgetMin = value; }
    public BigDecimal getBudgetMax() { return budgetMax; }
    public void setBudgetMax(BigDecimal value) { budgetMax = value; }
    public Date getLastVisitDate() { return lastVisitDate; }
    public void setLastVisitDate(Date value) { lastVisitDate = value; }
    public Long getVersion() { return version; }
    public void setVersion(Long value) { version = value; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String value) { requestKey = value; }
    public String getSourceClient() { return sourceClient; }
    public void setSourceClient(String value) { sourceClient = value; }
}
