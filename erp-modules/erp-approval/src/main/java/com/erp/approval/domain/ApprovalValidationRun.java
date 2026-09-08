package com.erp.approval.domain;

import java.util.Date;
import com.erp.common.core.web.domain.BaseEntity;

/** Immutable summary of one configuration validation execution. */
public class ApprovalValidationRun extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long runId;
    private Long templateId;
    private Long ruleId;
    private Long ruleVersionId;
    private String validationType;
    private String runStatus;
    private Integer totalCount;
    private Integer errorCount;
    private Integer warningCount;
    private Long startedByUserId;
    private String startedByName;
    private Date startedTime;
    private Date finishedTime;
    private String scopeSnapshot;

    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }
    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }
    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    public Long getRuleVersionId() { return ruleVersionId; }
    public void setRuleVersionId(Long ruleVersionId) { this.ruleVersionId = ruleVersionId; }
    public String getValidationType() { return validationType; }
    public void setValidationType(String validationType) { this.validationType = validationType; }
    public String getRunStatus() { return runStatus; }
    public void setRunStatus(String runStatus) { this.runStatus = runStatus; }
    public Integer getTotalCount() { return totalCount; }
    public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }
    public Integer getErrorCount() { return errorCount; }
    public void setErrorCount(Integer errorCount) { this.errorCount = errorCount; }
    public Integer getWarningCount() { return warningCount; }
    public void setWarningCount(Integer warningCount) { this.warningCount = warningCount; }
    public Long getStartedByUserId() { return startedByUserId; }
    public void setStartedByUserId(Long startedByUserId) { this.startedByUserId = startedByUserId; }
    public String getStartedByName() { return startedByName; }
    public void setStartedByName(String startedByName) { this.startedByName = startedByName; }
    public Date getStartedTime() { return startedTime; }
    public void setStartedTime(Date startedTime) { this.startedTime = startedTime; }
    public Date getFinishedTime() { return finishedTime; }
    public void setFinishedTime(Date finishedTime) { this.finishedTime = finishedTime; }
    public String getScopeSnapshot() { return scopeSnapshot; }
    public void setScopeSnapshot(String scopeSnapshot) { this.scopeSnapshot = scopeSnapshot; }
}
