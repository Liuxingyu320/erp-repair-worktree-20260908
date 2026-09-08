package com.erp.oa.domain;

import java.time.LocalDate;
import java.util.Date;
import com.erp.common.core.web.domain.BaseEntity;

/**
 * Business orchestration source of truth for one employee signing workflow.
 */
public class OaSignTask extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long taskId;
    private String taskNo;
    private String scenario;
    private Long employeeId;
    /** Employee snapshot name used by the HR task list; not persisted in the task table. */
    private String employeeName;
    /** List-only filter for contracts due within three days. */
    private Boolean dueSoon;
    /** List-only filter for tasks completed in the current calendar month. */
    private Boolean completedMonth;
    private Long shopDeptId;
    private Long legalEntityId;
    private Long assignedHrUserId;
    private String sourceType;
    private String sourceBusinessId;
    private String sourceEventVersion;
    private String dedupeKey;
    private String beforeSnapshotJson;
    private String afterSnapshotJson;
    private LocalDate businessEffectiveDate;
    private Boolean historicalSupplement;
    private String status;
    private String automationLevel = "MANUAL";
    private String riskLevel = "NORMAL";
    private Long planVersionId;
    private Long packageId;
    private Long confirmedBy;
    private Date confirmedTime;
    private String confirmedSnapshotHash;
    private Date signDeadline;
    private String deadlinePolicySource;
    private Integer deadlineDaysSnapshot;
    private Date terminalTime;
    private String terminalReasonCode;
    private String terminalReasonDetail;
    private String resolutionStatus;
    private Long resolvedBy;
    private Date resolvedTime;
    private String resolutionReasonCode;
    private String resolutionReasonDetail;
    private Long reissueOfTaskId;
    private Long reissuedToTaskId;
    private String failureCode;
    private String failureDetail;
    private Integer retryCount = 0;
    private Date nextRetryTime;
    private Long version = 0L;
    private Date createdTime;
    private Date sentTime;
    private Date completedTime;
    private Date cancelledTime;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getTaskNo() { return taskNo; }
    public void setTaskNo(String taskNo) { this.taskNo = taskNo; }
    public String getScenario() { return scenario; }
    public void setScenario(String scenario) { this.scenario = scenario; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
    public Boolean getDueSoon() { return dueSoon; }
    public void setDueSoon(Boolean dueSoon) { this.dueSoon = dueSoon; }
    public Boolean getCompletedMonth() { return completedMonth; }
    public void setCompletedMonth(Boolean completedMonth) { this.completedMonth = completedMonth; }
    public Long getShopDeptId() { return shopDeptId; }
    public void setShopDeptId(Long shopDeptId) { this.shopDeptId = shopDeptId; }
    public Long getLegalEntityId() { return legalEntityId; }
    public void setLegalEntityId(Long legalEntityId) { this.legalEntityId = legalEntityId; }
    public Long getAssignedHrUserId() { return assignedHrUserId; }
    public void setAssignedHrUserId(Long assignedHrUserId) { this.assignedHrUserId = assignedHrUserId; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceBusinessId() { return sourceBusinessId; }
    public void setSourceBusinessId(String sourceBusinessId) { this.sourceBusinessId = sourceBusinessId; }
    public String getSourceEventVersion() { return sourceEventVersion; }
    public void setSourceEventVersion(String sourceEventVersion) { this.sourceEventVersion = sourceEventVersion; }
    public String getDedupeKey() { return dedupeKey; }
    public void setDedupeKey(String dedupeKey) { this.dedupeKey = dedupeKey; }
    public String getBeforeSnapshotJson() { return beforeSnapshotJson; }
    public void setBeforeSnapshotJson(String beforeSnapshotJson) { this.beforeSnapshotJson = beforeSnapshotJson; }
    public String getAfterSnapshotJson() { return afterSnapshotJson; }
    public void setAfterSnapshotJson(String afterSnapshotJson) { this.afterSnapshotJson = afterSnapshotJson; }
    public LocalDate getBusinessEffectiveDate() { return businessEffectiveDate; }
    public void setBusinessEffectiveDate(LocalDate businessEffectiveDate) { this.businessEffectiveDate = businessEffectiveDate; }
    public Boolean getHistoricalSupplement() { return historicalSupplement; }
    public void setHistoricalSupplement(Boolean historicalSupplement) { this.historicalSupplement = historicalSupplement; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAutomationLevel() { return automationLevel; }
    public void setAutomationLevel(String automationLevel) { this.automationLevel = automationLevel; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public Long getPlanVersionId() { return planVersionId; }
    public void setPlanVersionId(Long planVersionId) { this.planVersionId = planVersionId; }
    public Long getPackageId() { return packageId; }
    public void setPackageId(Long packageId) { this.packageId = packageId; }
    public Long getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(Long confirmedBy) { this.confirmedBy = confirmedBy; }
    public Date getConfirmedTime() { return confirmedTime; }
    public void setConfirmedTime(Date confirmedTime) { this.confirmedTime = confirmedTime; }
    public String getConfirmedSnapshotHash() { return confirmedSnapshotHash; }
    public void setConfirmedSnapshotHash(String confirmedSnapshotHash) { this.confirmedSnapshotHash = confirmedSnapshotHash; }
    public Date getSignDeadline() { return signDeadline; }
    public void setSignDeadline(Date signDeadline) { this.signDeadline = signDeadline; }
    public String getDeadlinePolicySource() { return deadlinePolicySource; }
    public void setDeadlinePolicySource(String deadlinePolicySource) { this.deadlinePolicySource = deadlinePolicySource; }
    public Integer getDeadlineDaysSnapshot() { return deadlineDaysSnapshot; }
    public void setDeadlineDaysSnapshot(Integer deadlineDaysSnapshot) { this.deadlineDaysSnapshot = deadlineDaysSnapshot; }
    public Date getTerminalTime() { return terminalTime; }
    public void setTerminalTime(Date terminalTime) { this.terminalTime = terminalTime; }
    public String getTerminalReasonCode() { return terminalReasonCode; }
    public void setTerminalReasonCode(String terminalReasonCode) { this.terminalReasonCode = terminalReasonCode; }
    public String getTerminalReasonDetail() { return terminalReasonDetail; }
    public void setTerminalReasonDetail(String terminalReasonDetail) { this.terminalReasonDetail = terminalReasonDetail; }
    public String getResolutionStatus() { return resolutionStatus; }
    public void setResolutionStatus(String resolutionStatus) { this.resolutionStatus = resolutionStatus; }
    public Long getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(Long resolvedBy) { this.resolvedBy = resolvedBy; }
    public Date getResolvedTime() { return resolvedTime; }
    public void setResolvedTime(Date resolvedTime) { this.resolvedTime = resolvedTime; }
    public String getResolutionReasonCode() { return resolutionReasonCode; }
    public void setResolutionReasonCode(String resolutionReasonCode) { this.resolutionReasonCode = resolutionReasonCode; }
    public String getResolutionReasonDetail() { return resolutionReasonDetail; }
    public void setResolutionReasonDetail(String resolutionReasonDetail) { this.resolutionReasonDetail = resolutionReasonDetail; }
    public Long getReissueOfTaskId() { return reissueOfTaskId; }
    public void setReissueOfTaskId(Long reissueOfTaskId) { this.reissueOfTaskId = reissueOfTaskId; }
    public Long getReissuedToTaskId() { return reissuedToTaskId; }
    public void setReissuedToTaskId(Long reissuedToTaskId) { this.reissuedToTaskId = reissuedToTaskId; }
    public String getFailureCode() { return failureCode; }
    public void setFailureCode(String failureCode) { this.failureCode = failureCode; }
    public String getFailureDetail() { return failureDetail; }
    public void setFailureDetail(String failureDetail) { this.failureDetail = failureDetail; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public Date getNextRetryTime() { return nextRetryTime; }
    public void setNextRetryTime(Date nextRetryTime) { this.nextRetryTime = nextRetryTime; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public Date getCreatedTime() { return createdTime; }
    public void setCreatedTime(Date createdTime) { this.createdTime = createdTime; }
    public Date getSentTime() { return sentTime; }
    public void setSentTime(Date sentTime) { this.sentTime = sentTime; }
    public Date getCompletedTime() { return completedTime; }
    public void setCompletedTime(Date completedTime) { this.completedTime = completedTime; }
    public Date getCancelledTime() { return cancelledTime; }
    public void setCancelledTime(Date cancelledTime) { this.cancelledTime = cancelledTime; }
}
