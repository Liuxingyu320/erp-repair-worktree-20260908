package com.erp.system.domain;

import java.util.Date;
import com.erp.common.core.web.domain.BaseEntity;

/**
 * HR入职单操作日志 hr_onboarding_operation_log。
 */
public class HrOnboardingOperationLog extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long logId;
    private Long onboardingId;
    private String operationType;
    private String fromStatus;
    private String toStatus;
    private Long operatorUserId;
    private String operatorName;
    private String changedFieldKeys;
    private String decisionSummary;
    private String operationSummary;
    private Date operationTime;

    public Long getLogId() { return logId; }
    public void setLogId(Long logId) { this.logId = logId; }
    public Long getOnboardingId() { return onboardingId; }
    public void setOnboardingId(Long onboardingId) { this.onboardingId = onboardingId; }
    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }
    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }
    public String getToStatus() { return toStatus; }
    public void setToStatus(String toStatus) { this.toStatus = toStatus; }
    public Long getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public String getChangedFieldKeys() { return changedFieldKeys; }
    public void setChangedFieldKeys(String changedFieldKeys) { this.changedFieldKeys = changedFieldKeys; }
    public String getDecisionSummary() { return decisionSummary; }
    public void setDecisionSummary(String decisionSummary) { this.decisionSummary = decisionSummary; }
    public String getOperationSummary() { return operationSummary; }
    public void setOperationSummary(String operationSummary) { this.operationSummary = operationSummary; }
    public Date getOperationTime() { return operationTime; }
    public void setOperationTime(Date operationTime) { this.operationTime = operationTime; }
}
