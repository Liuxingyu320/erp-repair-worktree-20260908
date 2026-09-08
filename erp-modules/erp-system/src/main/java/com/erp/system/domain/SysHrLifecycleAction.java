package com.erp.system.domain;

import java.time.LocalDate;
import java.util.Date;
import com.erp.common.core.web.domain.BaseEntity;

/**
 * 不可变的人事生命周期业务动作 sys_hr_lifecycle_action。
 */
public class SysHrLifecycleAction extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long actionId;
    private String actionType;
    private Long employeeId;
    private String sourceType;
    private String sourceBusinessId;
    private String beforeSnapshotJson;
    private String afterSnapshotJson;
    private LocalDate effectiveDate;
    private Date actualConfirmTime;
    private String businessStatus;
    private String riskLevel;
    private String riskCodesJson;
    private String riskDetail;
    private String riskConfirmationJson;
    private String historicalReason;
    private String requestId;
    private Long version = 1L;
    private String operatorType;
    private Long operatorUserId;
    private String operatorName;
    private String operatorIp;
    private String operatorUserAgent;

    public Long getActionId() { return actionId; }
    public void setActionId(Long actionId) { this.actionId = actionId; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceBusinessId() { return sourceBusinessId; }
    public void setSourceBusinessId(String sourceBusinessId) { this.sourceBusinessId = sourceBusinessId; }
    public String getBeforeSnapshotJson() { return beforeSnapshotJson; }
    public void setBeforeSnapshotJson(String beforeSnapshotJson) { this.beforeSnapshotJson = beforeSnapshotJson; }
    public String getAfterSnapshotJson() { return afterSnapshotJson; }
    public void setAfterSnapshotJson(String afterSnapshotJson) { this.afterSnapshotJson = afterSnapshotJson; }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }
    public Date getActualConfirmTime() { return actualConfirmTime; }
    public void setActualConfirmTime(Date actualConfirmTime) { this.actualConfirmTime = actualConfirmTime; }
    public String getBusinessStatus() { return businessStatus; }
    public void setBusinessStatus(String businessStatus) { this.businessStatus = businessStatus; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public String getRiskCodesJson() { return riskCodesJson; }
    public void setRiskCodesJson(String riskCodesJson) { this.riskCodesJson = riskCodesJson; }
    public String getRiskDetail() { return riskDetail; }
    public void setRiskDetail(String riskDetail) { this.riskDetail = riskDetail; }
    public String getRiskConfirmationJson() { return riskConfirmationJson; }
    public void setRiskConfirmationJson(String riskConfirmationJson) { this.riskConfirmationJson = riskConfirmationJson; }
    public String getHistoricalReason() { return historicalReason; }
    public void setHistoricalReason(String historicalReason) { this.historicalReason = historicalReason; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public String getOperatorType() { return operatorType; }
    public void setOperatorType(String operatorType) { this.operatorType = operatorType; }
    public Long getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public String getOperatorIp() { return operatorIp; }
    public void setOperatorIp(String operatorIp) { this.operatorIp = operatorIp; }
    public String getOperatorUserAgent() { return operatorUserAgent; }
    public void setOperatorUserAgent(String operatorUserAgent) { this.operatorUserAgent = operatorUserAgent; }
}
