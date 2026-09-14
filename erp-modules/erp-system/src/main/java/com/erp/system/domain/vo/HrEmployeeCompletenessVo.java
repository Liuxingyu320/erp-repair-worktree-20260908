package com.erp.system.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import java.util.List;

/** Safe employee completeness queue row enriched from one linked onboarding record. */
public class HrEmployeeCompletenessVo extends HrEmployeeListVo
{
    private Long onboardingId;
    private String onboardingStatus;
    private Integer onboardingCompletionPercent;
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date postEntryDueDate;
    private Boolean postEntryOverdue = false;
    private List<HrOnboardingCompletionVo.MissingField> missingOnboardingFields = new java.util.ArrayList<>();
    private String positionConfigurationUrl;

    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    public Long getOnboardingId() { return onboardingId; }
    public void setOnboardingId(Long value) { onboardingId=value; }
    public String getOnboardingStatus() { return onboardingStatus; }
    public void setOnboardingStatus(String value) { onboardingStatus=value; }
    public Integer getOnboardingCompletionPercent() { return onboardingCompletionPercent; }
    public void setOnboardingCompletionPercent(Integer value) { onboardingCompletionPercent=value; }
    public Date getPostEntryDueDate() { return postEntryDueDate; }
    public void setPostEntryDueDate(Date value) { postEntryDueDate=value; }
    public Boolean getPostEntryOverdue() { return postEntryOverdue; }
    public void setPostEntryOverdue(Boolean value) { postEntryOverdue=value; }
    public List<HrOnboardingCompletionVo.MissingField> getMissingOnboardingFields() { return missingOnboardingFields; }
    public void setMissingOnboardingFields(List<HrOnboardingCompletionVo.MissingField> value)
    { missingOnboardingFields=value==null?new java.util.ArrayList<>():value; }
    public String getPositionConfigurationUrl() { return positionConfigurationUrl; }
    public void setPositionConfigurationUrl(String value) { positionConfigurationUrl=value; }
}
