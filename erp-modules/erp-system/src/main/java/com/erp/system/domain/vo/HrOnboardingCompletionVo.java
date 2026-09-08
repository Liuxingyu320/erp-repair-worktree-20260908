package com.erp.system.domain.vo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Server-owned onboarding/profile completeness decision.
 */
public class HrOnboardingCompletionVo implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Integer percentage = 0;
    private Integer completedFieldCount = 0;
    private Integer requiredFieldCount = 0;
    private List<String> missingFields = new ArrayList<>();
    private List<String> missingLabels = new ArrayList<>();
    private Map<String, List<MissingField>> groupedMissingFields = new LinkedHashMap<>();
    private List<String> blockingCodes = new ArrayList<>();
    private List<String> riskCodes = new ArrayList<>();
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date postEntryDueDate;
    private Boolean postEntryOverdue = false;
    private List<String> allowedActions = new ArrayList<>();

    public Integer getPercentage() { return percentage; }
    public void setPercentage(Integer percentage) { this.percentage = percentage; }
    public Integer getCompletedFieldCount() { return completedFieldCount; }
    public void setCompletedFieldCount(Integer completedFieldCount) { this.completedFieldCount = completedFieldCount; }
    public Integer getRequiredFieldCount() { return requiredFieldCount; }
    public void setRequiredFieldCount(Integer requiredFieldCount) { this.requiredFieldCount = requiredFieldCount; }
    public List<String> getMissingFields() { return missingFields; }
    public void setMissingFields(List<String> missingFields) { this.missingFields = missingFields; }
    public List<String> getMissingLabels() { return missingLabels; }
    public void setMissingLabels(List<String> missingLabels) { this.missingLabels = missingLabels; }
    public Map<String, List<MissingField>> getGroupedMissingFields() { return groupedMissingFields; }
    public void setGroupedMissingFields(Map<String, List<MissingField>> groupedMissingFields) { this.groupedMissingFields = groupedMissingFields; }
    public List<String> getBlockingCodes() { return blockingCodes; }
    public void setBlockingCodes(List<String> blockingCodes) { this.blockingCodes = blockingCodes; }
    public List<String> getRiskCodes() { return riskCodes; }
    public void setRiskCodes(List<String> riskCodes) { this.riskCodes = riskCodes; }
    public Date getPostEntryDueDate() { return postEntryDueDate; }
    public void setPostEntryDueDate(Date postEntryDueDate) { this.postEntryDueDate = postEntryDueDate; }
    public Boolean getPostEntryOverdue() { return postEntryOverdue; }
    public void setPostEntryOverdue(Boolean postEntryOverdue) { this.postEntryOverdue = postEntryOverdue; }
    public List<String> getAllowedActions() { return allowedActions; }
    public void setAllowedActions(List<String> allowedActions) { this.allowedActions = allowedActions; }

    public static class MissingField implements Serializable
    {
        private static final long serialVersionUID = 1L;
        private String key;
        private String label;

        public MissingField() { }
        public MissingField(String key, String label) { this.key = key; this.label = label; }
        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
    }
}
