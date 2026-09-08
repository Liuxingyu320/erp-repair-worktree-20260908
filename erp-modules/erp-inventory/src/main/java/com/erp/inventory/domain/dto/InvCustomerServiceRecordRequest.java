package com.erp.inventory.domain.dto;

import java.math.BigDecimal;
import java.util.Date;

public class InvCustomerServiceRecordRequest
{
    private Date serviceDate;
    private Integer partySize;
    private String teaServed;
    private String preferenceSnapshot;
    private String cautionSnapshot;
    private String serviceNote;
    private BigDecimal consumptionAmount;
    private String requestKey;
    private String sourceClient;

    public Date getServiceDate() { return serviceDate; }
    public void setServiceDate(Date value) { serviceDate = value; }
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
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String value) { requestKey = value; }
    public String getSourceClient() { return sourceClient; }
    public void setSourceClient(String value) { sourceClient = value; }
}
