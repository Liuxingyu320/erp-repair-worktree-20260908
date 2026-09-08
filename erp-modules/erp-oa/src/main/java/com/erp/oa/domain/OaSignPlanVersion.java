package com.erp.oa.domain;

import java.util.Date;
import java.util.List;
import com.erp.common.core.web.domain.BaseEntity;

/** Immutable snapshot of a published signing plan. */
public class OaSignPlanVersion extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long versionId;
    private Long planId;
    private String planName;
    private Integer versionNo;
    private String scenario;
    private Long shopDeptId;
    private Long legalEntityId;
    private String legalEntityName;
    private String ruleJson;
    private String defaultValuesJson;
    private Integer signDeadlineDays;
    private String reminderPolicyJson;
    private String autoSendConditionJson;
    private String publishStatus;
    private String matchingStatus;
    private Long publishedByUserId;
    private String publishedBy;
    private Date publishedTime;
    private String versionHash;
    private List<OaSignPlanVersionTemplate> templates;

    public Long getVersionId() { return versionId; }
    public void setVersionId(Long versionId) { this.versionId = versionId; }

    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }

    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }

    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }

    public String getScenario() { return scenario; }
    public void setScenario(String scenario) { this.scenario = scenario; }

    public Long getShopDeptId() { return shopDeptId; }
    public void setShopDeptId(Long shopDeptId) { this.shopDeptId = shopDeptId; }

    public Long getLegalEntityId() { return legalEntityId; }
    public void setLegalEntityId(Long legalEntityId) { this.legalEntityId = legalEntityId; }

    public String getLegalEntityName() { return legalEntityName; }
    public void setLegalEntityName(String legalEntityName) { this.legalEntityName = legalEntityName; }

    public String getRuleJson() { return ruleJson; }
    public void setRuleJson(String ruleJson) { this.ruleJson = ruleJson; }

    public String getDefaultValuesJson() { return defaultValuesJson; }
    public void setDefaultValuesJson(String defaultValuesJson) { this.defaultValuesJson = defaultValuesJson; }

    public Integer getSignDeadlineDays() { return signDeadlineDays; }
    public void setSignDeadlineDays(Integer signDeadlineDays) { this.signDeadlineDays = signDeadlineDays; }

    public String getReminderPolicyJson() { return reminderPolicyJson; }
    public void setReminderPolicyJson(String reminderPolicyJson) { this.reminderPolicyJson = reminderPolicyJson; }

    public String getAutoSendConditionJson() { return autoSendConditionJson; }
    public void setAutoSendConditionJson(String autoSendConditionJson) { this.autoSendConditionJson = autoSendConditionJson; }

    public String getPublishStatus() { return publishStatus; }
    public void setPublishStatus(String publishStatus) { this.publishStatus = publishStatus; }

    public String getMatchingStatus() { return matchingStatus; }
    public void setMatchingStatus(String matchingStatus) { this.matchingStatus = matchingStatus; }

    public Long getPublishedByUserId() { return publishedByUserId; }
    public void setPublishedByUserId(Long publishedByUserId) { this.publishedByUserId = publishedByUserId; }

    public String getPublishedBy() { return publishedBy; }
    public void setPublishedBy(String publishedBy) { this.publishedBy = publishedBy; }

    public Date getPublishedTime() { return publishedTime; }
    public void setPublishedTime(Date publishedTime) { this.publishedTime = publishedTime; }

    public String getVersionHash() { return versionHash; }
    public void setVersionHash(String versionHash) { this.versionHash = versionHash; }

    public List<OaSignPlanVersionTemplate> getTemplates() { return templates; }
    public void setTemplates(List<OaSignPlanVersionTemplate> templates) { this.templates = templates; }
}
