package com.erp.inventory.domain.vo;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import com.erp.inventory.domain.InvCustomerServiceRecord;

/** 门店员工可见的客户服务资料；刻意不声明信用和账期字段。 */
public class InvCustomerServiceCardVo
{
    private Long customerId;
    private String customerName;
    private String customerCode;
    private String contactPerson;
    private String contactPhone;
    private Long shopDeptId;
    private String shopDeptName;
    private String status;
    private Long photoNodeId;
    private String teaPreferences;
    private String preferenceTags;
    private String brewingServicePreferences;
    private String cautions;
    private BigDecimal budgetMin;
    private BigDecimal budgetMax;
    private Date lastVisitDate;
    private Long version;
    private String createBy;
    private Date createTime;
    private String updateBy;
    private Date updateTime;
    private List<InvCustomerServiceRecord> serviceRecords;

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
    public Long getShopDeptId() { return shopDeptId; }
    public void setShopDeptId(Long value) { shopDeptId = value; }
    public String getShopDeptName() { return shopDeptName; }
    public void setShopDeptName(String value) { shopDeptName = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
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
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String value) { createBy = value; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date value) { createTime = value; }
    public String getUpdateBy() { return updateBy; }
    public void setUpdateBy(String value) { updateBy = value; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date value) { updateTime = value; }
    public List<InvCustomerServiceRecord> getServiceRecords() { return serviceRecords; }
    public void setServiceRecords(List<InvCustomerServiceRecord> value) { serviceRecords = value; }
}
