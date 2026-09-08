package com.erp.inventory.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.erp.common.core.web.domain.BaseEntity;

public class InvCustomerServiceProfile extends BaseEntity
{
    private static final long serialVersionUID = 1L;
    private Long customerId;
    private Long photoNodeId;
    private String teaPreferences;
    private String preferenceTags;
    private String brewingServicePreferences;
    private String cautions;
    private BigDecimal budgetMin;
    private BigDecimal budgetMax;
    private Date lastVisitDate;
    private Long version;

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long value) { customerId = value; }
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
}
