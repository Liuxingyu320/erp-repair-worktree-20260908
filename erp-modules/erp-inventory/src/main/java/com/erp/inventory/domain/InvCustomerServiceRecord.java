package com.erp.inventory.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.erp.common.core.web.domain.BaseEntity;

public class InvCustomerServiceRecord extends BaseEntity
{
    private static final long serialVersionUID = 1L;
    private Long recordId;
    private Long customerId;
    private Date serviceDate;
    private Long serviceUserId;
    private String serviceUserName;
    private Integer partySize;
    private String teaServed;
    private String preferenceSnapshot;
    private String cautionSnapshot;
    private String serviceNote;
    private BigDecimal consumptionAmount;
    private Long shopDeptId;
    private String requestKey;
    private String sourceClient;

    public Long getRecordId() { return recordId; }
    public void setRecordId(Long value) { recordId = value; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long value) { customerId = value; }
    public Date getServiceDate() { return serviceDate; }
    public void setServiceDate(Date value) { serviceDate = value; }
    public Long getServiceUserId() { return serviceUserId; }
    public void setServiceUserId(Long value) { serviceUserId = value; }
    public String getServiceUserName() { return serviceUserName; }
    public void setServiceUserName(String value) { serviceUserName = value; }
    public Integer getPartySize() { return partySize; }
    public void setPartySize(Integer value) { partySize = value; }
    public String getTeaServed() { return teaServed; }
    public void setTeaServed(String value) { teaServed = value; }
    public String getPreferenceSnapshot() { return preferenceSnapshot; }
    public void setPreferenceSnapshot(String value) { preferenceSnapshot = value; }
    public String getCautionSnapshot() { return cautionSnapshot; }
    public void setCautionSnapshot(String value) { cautionSnapshot = value; }
    public String getServiceNote() { return serviceNote; }
    public void setServiceNote(String value) { serviceNote = value; }
    public BigDecimal getConsumptionAmount() { return consumptionAmount; }
    public void setConsumptionAmount(BigDecimal value) { consumptionAmount = value; }
    public Long getShopDeptId() { return shopDeptId; }
    public void setShopDeptId(Long value) { shopDeptId = value; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String value) { requestKey = value; }
    public String getSourceClient() { return sourceClient; }
    public void setSourceClient(String value) { sourceClient = value; }
}
