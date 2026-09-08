package com.erp.oa.domain;

import java.math.BigDecimal;
import java.util.Date;

/** Persisted preview and generation state for one source or missing-selected row. */
public class OaSignOnboardImportRow
{
    private Long rowId;
    private Long batchId;
    private Integer sourceRowNumber;
    private String rowHash;
    private Long employeeId;
    private String matchType;
    private String employeeNameMasked;
    private String phoneMasked;
    private String idNumberMasked;
    private String addressMasked;
    private String snapshotJson;
    private String status;
    private String errorCodesJson;
    private String warningCodesJson;
    private String missingFieldsJson;
    private Boolean warningConfirmed;
    private String warningReason;
    private String routeCode;
    private Long planVersionId;
    private String planVersionHash;
    private Long matchedLegalEntityId;
    private String companyMatchMode;
    private BigDecimal companyMatchScore;
    private BigDecimal companySecondScore;
    private String companyMatchPolicyVersion;
    private Long companyMasterVersion;
    private Long deptLegalEntityId;
    /**
     * Persisted as NOT NULL. Rows that fail employee matching never reach the
     * company matcher, so they must still carry an explicit false value.
     */
    private Boolean companyDeptConflict = false;
    private String companyCandidatesJson;
    private Long recommendedSealId;
    private String sealRecommendationMode;
    private String sealCandidatesJson;
    private Long dataRequestId;
    private Long taskId;
    private Long packageId;
    private Boolean historicalSupplement;
    private String historicalReason;
    private Boolean noExternalContractConfirmed;
    private String generationRequestId;
    private Long sourceEventVersion;
    private Long version;
    private Date generatedTime;
    private Date createTime;
    private Date updateTime;

    public Long getRowId() { return rowId; }
    public void setRowId(Long rowId) { this.rowId = rowId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Integer getSourceRowNumber() { return sourceRowNumber; }
    public void setSourceRowNumber(Integer sourceRowNumber) { this.sourceRowNumber = sourceRowNumber; }
    public String getRowHash() { return rowHash; }
    public void setRowHash(String rowHash) { this.rowHash = rowHash; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public String getMatchType() { return matchType; }
    public void setMatchType(String matchType) { this.matchType = matchType; }
    public String getEmployeeNameMasked() { return employeeNameMasked; }
    public void setEmployeeNameMasked(String employeeNameMasked) { this.employeeNameMasked = employeeNameMasked; }
    public String getPhoneMasked() { return phoneMasked; }
    public void setPhoneMasked(String phoneMasked) { this.phoneMasked = phoneMasked; }
    public String getIdNumberMasked() { return idNumberMasked; }
    public void setIdNumberMasked(String idNumberMasked) { this.idNumberMasked = idNumberMasked; }
    public String getAddressMasked() { return addressMasked; }
    public void setAddressMasked(String addressMasked) { this.addressMasked = addressMasked; }
    public String getSnapshotJson() { return snapshotJson; }
    public void setSnapshotJson(String snapshotJson) { this.snapshotJson = snapshotJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorCodesJson() { return errorCodesJson; }
    public void setErrorCodesJson(String errorCodesJson) { this.errorCodesJson = errorCodesJson; }
    public String getWarningCodesJson() { return warningCodesJson; }
    public void setWarningCodesJson(String warningCodesJson) { this.warningCodesJson = warningCodesJson; }
    public String getMissingFieldsJson() { return missingFieldsJson; }
    public void setMissingFieldsJson(String missingFieldsJson) { this.missingFieldsJson = missingFieldsJson; }
    public Boolean getWarningConfirmed() { return warningConfirmed; }
    public void setWarningConfirmed(Boolean warningConfirmed) { this.warningConfirmed = warningConfirmed; }
    public String getWarningReason() { return warningReason; }
    public void setWarningReason(String warningReason) { this.warningReason = warningReason; }
    public String getRouteCode() { return routeCode; }
    public void setRouteCode(String routeCode) { this.routeCode = routeCode; }
    public Long getPlanVersionId() { return planVersionId; }
    public void setPlanVersionId(Long planVersionId) { this.planVersionId = planVersionId; }
    public String getPlanVersionHash() { return planVersionHash; }
    public void setPlanVersionHash(String planVersionHash) { this.planVersionHash = planVersionHash; }
    public Long getMatchedLegalEntityId() { return matchedLegalEntityId; }
    public void setMatchedLegalEntityId(Long value) { this.matchedLegalEntityId = value; }
    public String getCompanyMatchMode() { return companyMatchMode; }
    public void setCompanyMatchMode(String value) { this.companyMatchMode = value; }
    public BigDecimal getCompanyMatchScore() { return companyMatchScore; }
    public void setCompanyMatchScore(BigDecimal value) { this.companyMatchScore = value; }
    public BigDecimal getCompanySecondScore() { return companySecondScore; }
    public void setCompanySecondScore(BigDecimal value) { this.companySecondScore = value; }
    public String getCompanyMatchPolicyVersion() { return companyMatchPolicyVersion; }
    public void setCompanyMatchPolicyVersion(String value) { this.companyMatchPolicyVersion = value; }
    public Long getCompanyMasterVersion() { return companyMasterVersion; }
    public void setCompanyMasterVersion(Long value) { this.companyMasterVersion = value; }
    public Long getDeptLegalEntityId() { return deptLegalEntityId; }
    public void setDeptLegalEntityId(Long value) { this.deptLegalEntityId = value; }
    public Boolean getCompanyDeptConflict() { return companyDeptConflict; }
    public void setCompanyDeptConflict(Boolean value) { this.companyDeptConflict = value; }
    public String getCompanyCandidatesJson() { return companyCandidatesJson; }
    public void setCompanyCandidatesJson(String value) { this.companyCandidatesJson = value; }
    public Long getRecommendedSealId() { return recommendedSealId; }
    public void setRecommendedSealId(Long value) { this.recommendedSealId = value; }
    public String getSealRecommendationMode() { return sealRecommendationMode; }
    public void setSealRecommendationMode(String value) { this.sealRecommendationMode = value; }
    public String getSealCandidatesJson() { return sealCandidatesJson; }
    public void setSealCandidatesJson(String value) { this.sealCandidatesJson = value; }
    public Long getDataRequestId() { return dataRequestId; }
    public void setDataRequestId(Long dataRequestId) { this.dataRequestId = dataRequestId; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getPackageId() { return packageId; }
    public void setPackageId(Long packageId) { this.packageId = packageId; }
    public Boolean getHistoricalSupplement() { return historicalSupplement; }
    public void setHistoricalSupplement(Boolean historicalSupplement) { this.historicalSupplement = historicalSupplement; }
    public String getHistoricalReason() { return historicalReason; }
    public void setHistoricalReason(String historicalReason) { this.historicalReason = historicalReason; }
    public Boolean getNoExternalContractConfirmed() { return noExternalContractConfirmed; }
    public void setNoExternalContractConfirmed(Boolean value) { this.noExternalContractConfirmed = value; }
    public String getGenerationRequestId() { return generationRequestId; }
    public void setGenerationRequestId(String generationRequestId) { this.generationRequestId = generationRequestId; }
    public Long getSourceEventVersion() { return sourceEventVersion; }
    public void setSourceEventVersion(Long sourceEventVersion) { this.sourceEventVersion = sourceEventVersion; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public Date getGeneratedTime() { return generatedTime; }
    public void setGeneratedTime(Date generatedTime) { this.generatedTime = generatedTime; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
