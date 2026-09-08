package com.erp.oa.domain.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * Permission-scoped HR import row. Name and phone are shown in full to the authorized contract
 * operator; identity number, address and the normalized snapshot JSON remain minimized.
 */
public class OaSignOnboardImportRowView
{
    @JsonSerialize(using = ToStringSerializer.class)
    private Long rowId;
    private Integer sourceRowNumber;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long employeeId;
    private String matchType;
    private String employeeName;
    private String phone;
    private String idNumber;
    private String currentAddress;
    private String status;
    private List<String> errorCodes = new ArrayList<>();
    private List<String> warningCodes = new ArrayList<>();
    private List<String> missingFields = new ArrayList<>();
    private Boolean warningConfirmed;
    private String routeCode;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long planVersionId;
    private String planVersionHash;
    private String planName;
    private List<String> templateNames = new ArrayList<>();
    private String salaryVersion;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long matchedLegalEntityId;
    private String matchedLegalEntityCode;
    private String matchedLegalEntityName;
    private String matchedUnifiedSocialCreditCode;
    private String matchedRegisteredAddress;
    private String matchedLegalRepresentative;
    private String companyMatchMode;
    private BigDecimal companyMatchScore;
    private BigDecimal companySecondScore;
    private String companyMatchPolicyVersion;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long companyMasterVersion;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long deptLegalEntityId;
    private Boolean companyDeptConflict;
    private List<CompanyCandidate> companyCandidates = new ArrayList<>();
    @JsonSerialize(using = ToStringSerializer.class)
    private Long recommendedSealId;
    private String sealRecommendationMode;
    private List<SealCandidate> sealCandidates = new ArrayList<>();
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dataRequestId;
    /** Sequence frozen on the row's current employee-data request, if one exists. */
    private String dataRequestSigningSequence;
    /** True only after that request completed with a captured signature hash and time. */
    private Boolean dataRequestSignatureCaptured;
    /** Server preflight for starting a signature-first request on this exact row snapshot. */
    private Boolean signatureRequestable;
    private List<String> signatureRequestBlockers = new ArrayList<>();
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long packageId;
    /** Current open ONBOARD task, if any. This is a live, PII-free blocking fact. */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long existingTaskId;
    private String existingTaskStatus;
    private String existingTaskSourceType;
    /** Latest ONBOARD task, including terminal history, if any. */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long latestTaskId;
    private String latestTaskStatus;
    private String latestTaskSourceType;
    private Boolean historicalSupplement;
    private Boolean noExternalContractConfirmed;
    private String contractTypeCode;
    private String socialTypeCode;
    private String contractTermCode;
    private String employeePost;
    private String workLocation;
    private String cityLevel;
    private LocalDate contractStartDate;
    private LocalDate contractEndDate;
    private LocalDate probationStartDate;
    private LocalDate probationEndDate;
    private BigDecimal salaryTotal;
    private BigDecimal baseSalary;
    private BigDecimal postSalary;
    private BigDecimal fieldAllowance;
    private BigDecimal performanceSalary;
    private String warningReason;
    private String historicalReason;
    private String jobGradeCode;
    private String servicePersonType;
    private String insuranceType;
    private String recommendedCompany;
    private String recommendedLegalRepresentative;
    private String recommendedRegisteredAddress;
    private String studentStatus;
    private String schoolName;
    private String retirementStatus;
    private String incomeStartYearMonth;
    private Long version;

    public Long getRowId() { return rowId; }
    public void setRowId(Long rowId) { this.rowId = rowId; }
    public Integer getSourceRowNumber() { return sourceRowNumber; }
    public void setSourceRowNumber(Integer sourceRowNumber) { this.sourceRowNumber = sourceRowNumber; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public String getMatchType() { return matchType; }
    public void setMatchType(String matchType) { this.matchType = matchType; }
    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getIdNumber() { return idNumber; }
    public void setIdNumber(String idNumber) { this.idNumber = idNumber; }
    public String getCurrentAddress() { return currentAddress; }
    public void setCurrentAddress(String currentAddress) { this.currentAddress = currentAddress; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<String> getErrorCodes() { return new ArrayList<>(errorCodes); }
    public void setErrorCodes(List<String> value) { this.errorCodes = copy(value); }
    public List<String> getWarningCodes() { return new ArrayList<>(warningCodes); }
    public void setWarningCodes(List<String> value) { this.warningCodes = copy(value); }
    public List<String> getMissingFields() { return new ArrayList<>(missingFields); }
    public void setMissingFields(List<String> value) { this.missingFields = copy(value); }
    public Boolean getWarningConfirmed() { return warningConfirmed; }
    public void setWarningConfirmed(Boolean warningConfirmed) { this.warningConfirmed = warningConfirmed; }
    public String getRouteCode() { return routeCode; }
    public void setRouteCode(String routeCode) { this.routeCode = routeCode; }
    public Long getPlanVersionId() { return planVersionId; }
    public void setPlanVersionId(Long planVersionId) { this.planVersionId = planVersionId; }
    public String getPlanVersionHash() { return planVersionHash; }
    public void setPlanVersionHash(String planVersionHash) { this.planVersionHash = planVersionHash; }
    public String getPlanName() { return planName; }
    public void setPlanName(String value) { this.planName = value; }
    public List<String> getTemplateNames() { return new ArrayList<>(templateNames); }
    public void setTemplateNames(List<String> value) { this.templateNames = copy(value); }
    public String getSalaryVersion() { return salaryVersion; }
    public void setSalaryVersion(String value) { this.salaryVersion = value; }
    public Long getMatchedLegalEntityId() { return matchedLegalEntityId; }
    public void setMatchedLegalEntityId(Long value) { this.matchedLegalEntityId = value; }
    public String getMatchedLegalEntityCode() { return matchedLegalEntityCode; }
    public void setMatchedLegalEntityCode(String value) { this.matchedLegalEntityCode = value; }
    public String getMatchedLegalEntityName() { return matchedLegalEntityName; }
    public void setMatchedLegalEntityName(String value) { this.matchedLegalEntityName = value; }
    public String getMatchedUnifiedSocialCreditCode() { return matchedUnifiedSocialCreditCode; }
    public void setMatchedUnifiedSocialCreditCode(String value) { this.matchedUnifiedSocialCreditCode = value; }
    public String getMatchedRegisteredAddress() { return matchedRegisteredAddress; }
    public void setMatchedRegisteredAddress(String value) { this.matchedRegisteredAddress = value; }
    public String getMatchedLegalRepresentative() { return matchedLegalRepresentative; }
    public void setMatchedLegalRepresentative(String value) { this.matchedLegalRepresentative = value; }
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
    public List<CompanyCandidate> getCompanyCandidates() { return new ArrayList<>(companyCandidates); }
    public void setCompanyCandidates(List<CompanyCandidate> value) { this.companyCandidates = value == null ? new ArrayList<>() : new ArrayList<>(value); }
    public Long getRecommendedSealId() { return recommendedSealId; }
    public void setRecommendedSealId(Long value) { this.recommendedSealId = value; }
    public String getSealRecommendationMode() { return sealRecommendationMode; }
    public void setSealRecommendationMode(String value) { this.sealRecommendationMode = value; }
    public List<SealCandidate> getSealCandidates() { return new ArrayList<>(sealCandidates); }
    public void setSealCandidates(List<SealCandidate> value) { this.sealCandidates = value == null ? new ArrayList<>() : new ArrayList<>(value); }
    public Long getDataRequestId() { return dataRequestId; }
    public void setDataRequestId(Long dataRequestId) { this.dataRequestId = dataRequestId; }
    public String getDataRequestSigningSequence() { return dataRequestSigningSequence; }
    public void setDataRequestSigningSequence(String value) { dataRequestSigningSequence = value; }
    public Boolean getDataRequestSignatureCaptured() { return dataRequestSignatureCaptured; }
    public void setDataRequestSignatureCaptured(Boolean value) { dataRequestSignatureCaptured = value; }
    public Boolean getSignatureRequestable() { return signatureRequestable; }
    public void setSignatureRequestable(Boolean value) { signatureRequestable = value; }
    public List<String> getSignatureRequestBlockers() { return new ArrayList<>(signatureRequestBlockers); }
    public void setSignatureRequestBlockers(List<String> value) { signatureRequestBlockers = copy(value); }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getPackageId() { return packageId; }
    public void setPackageId(Long packageId) { this.packageId = packageId; }
    public Long getExistingTaskId() { return existingTaskId; }
    public void setExistingTaskId(Long value) { this.existingTaskId = value; }
    public String getExistingTaskStatus() { return existingTaskStatus; }
    public void setExistingTaskStatus(String value) { this.existingTaskStatus = value; }
    public String getExistingTaskSourceType() { return existingTaskSourceType; }
    public void setExistingTaskSourceType(String value) { this.existingTaskSourceType = value; }
    public Long getLatestTaskId() { return latestTaskId; }
    public void setLatestTaskId(Long value) { this.latestTaskId = value; }
    public String getLatestTaskStatus() { return latestTaskStatus; }
    public void setLatestTaskStatus(String value) { this.latestTaskStatus = value; }
    public String getLatestTaskSourceType() { return latestTaskSourceType; }
    public void setLatestTaskSourceType(String value) { this.latestTaskSourceType = value; }
    public Boolean getHistoricalSupplement() { return historicalSupplement; }
    public void setHistoricalSupplement(Boolean historicalSupplement) { this.historicalSupplement = historicalSupplement; }
    public Boolean getNoExternalContractConfirmed() { return noExternalContractConfirmed; }
    public void setNoExternalContractConfirmed(Boolean value) { this.noExternalContractConfirmed = value; }
    public String getContractTypeCode() { return contractTypeCode; }
    public void setContractTypeCode(String value) { this.contractTypeCode = value; }
    public String getSocialTypeCode() { return socialTypeCode; }
    public void setSocialTypeCode(String value) { this.socialTypeCode = value; }
    public String getContractTermCode() { return contractTermCode; }
    public void setContractTermCode(String value) { this.contractTermCode = value; }
    public String getEmployeePost() { return employeePost; }
    public void setEmployeePost(String value) { this.employeePost = value; }
    public String getWorkLocation() { return workLocation; }
    public void setWorkLocation(String value) { this.workLocation = value; }
    public String getCityLevel() { return cityLevel; }
    public void setCityLevel(String value) { this.cityLevel = value; }
    public LocalDate getContractStartDate() { return contractStartDate; }
    public void setContractStartDate(LocalDate value) { this.contractStartDate = value; }
    public LocalDate getContractEndDate() { return contractEndDate; }
    public void setContractEndDate(LocalDate value) { this.contractEndDate = value; }
    public LocalDate getProbationStartDate() { return probationStartDate; }
    public void setProbationStartDate(LocalDate value) { this.probationStartDate = value; }
    public LocalDate getProbationEndDate() { return probationEndDate; }
    public void setProbationEndDate(LocalDate value) { this.probationEndDate = value; }
    public BigDecimal getSalaryTotal() { return salaryTotal; }
    public void setSalaryTotal(BigDecimal value) { this.salaryTotal = value; }
    public BigDecimal getBaseSalary() { return baseSalary; }
    public void setBaseSalary(BigDecimal value) { this.baseSalary = value; }
    public BigDecimal getPostSalary() { return postSalary; }
    public void setPostSalary(BigDecimal value) { this.postSalary = value; }
    public BigDecimal getFieldAllowance() { return fieldAllowance; }
    public void setFieldAllowance(BigDecimal value) { this.fieldAllowance = value; }
    public BigDecimal getPerformanceSalary() { return performanceSalary; }
    public void setPerformanceSalary(BigDecimal value) { this.performanceSalary = value; }
    public String getWarningReason() { return warningReason; }
    public void setWarningReason(String value) { this.warningReason = value; }
    public String getHistoricalReason() { return historicalReason; }
    public void setHistoricalReason(String value) { this.historicalReason = value; }
    public String getJobGradeCode() { return jobGradeCode; }
    public void setJobGradeCode(String value) { this.jobGradeCode = value; }
    public String getServicePersonType() { return servicePersonType; }
    public void setServicePersonType(String value) { this.servicePersonType = value; }
    public String getInsuranceType() { return insuranceType; }
    public void setInsuranceType(String value) { this.insuranceType = value; }
    public String getRecommendedCompany() { return recommendedCompany; }
    public void setRecommendedCompany(String value) { this.recommendedCompany = value; }
    public String getRecommendedLegalRepresentative() { return recommendedLegalRepresentative; }
    public void setRecommendedLegalRepresentative(String value) { this.recommendedLegalRepresentative = value; }
    public String getRecommendedRegisteredAddress() { return recommendedRegisteredAddress; }
    public void setRecommendedRegisteredAddress(String value) { this.recommendedRegisteredAddress = value; }
    public String getStudentStatus() { return studentStatus; }
    public void setStudentStatus(String value) { this.studentStatus = value; }
    public String getSchoolName() { return schoolName; }
    public void setSchoolName(String value) { this.schoolName = value; }
    public String getRetirementStatus() { return retirementStatus; }
    public void setRetirementStatus(String value) { this.retirementStatus = value; }
    public String getIncomeStartYearMonth() { return incomeStartYearMonth; }
    public void setIncomeStartYearMonth(String value) { this.incomeStartYearMonth = value; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    private List<String> copy(List<String> source)
    {
        return source == null ? new ArrayList<>() : new ArrayList<>(source);
    }

    public static class CompanyCandidate
    {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long legalEntityId;
        private String legalEntityCode;
        private String legalEntityName;
        private String unifiedSocialCreditCode;
        private String registeredAddress;
        private String legalRepresentative;
        private BigDecimal score;
        private List<String> missingMasterFields = new ArrayList<>();
        @JsonSerialize(using = ToStringSerializer.class)
        private Long masterVersion;

        public Long getLegalEntityId() { return legalEntityId; }
        public void setLegalEntityId(Long value) { legalEntityId = value; }
        public String getLegalEntityCode() { return legalEntityCode; }
        public void setLegalEntityCode(String value) { legalEntityCode = value; }
        public String getLegalEntityName() { return legalEntityName; }
        public void setLegalEntityName(String value) { legalEntityName = value; }
        public String getUnifiedSocialCreditCode() { return unifiedSocialCreditCode; }
        public void setUnifiedSocialCreditCode(String value) { unifiedSocialCreditCode = value; }
        public String getRegisteredAddress() { return registeredAddress; }
        public void setRegisteredAddress(String value) { registeredAddress = value; }
        public String getLegalRepresentative() { return legalRepresentative; }
        public void setLegalRepresentative(String value) { legalRepresentative = value; }
        public BigDecimal getScore() { return score; }
        public void setScore(BigDecimal value) { score = value; }
        public List<String> getMissingMasterFields() { return new ArrayList<>(missingMasterFields); }
        public void setMissingMasterFields(List<String> value) { missingMasterFields = value == null ? new ArrayList<>() : new ArrayList<>(value); }
        public Long getMasterVersion() { return masterVersion; }
        public void setMasterVersion(Long value) { masterVersion = value; }
    }

    public static class SealCandidate
    {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long sealId;
        private String sealName;
        private String sealCode;
        private Boolean defaultSeal;

        public Long getSealId() { return sealId; }
        public void setSealId(Long value) { sealId = value; }
        public String getSealName() { return sealName; }
        public void setSealName(String value) { sealName = value; }
        public String getSealCode() { return sealCode; }
        public void setSealCode(String value) { sealCode = value; }
        public Boolean getDefaultSeal() { return defaultSeal; }
        public void setDefaultSeal(Boolean value) { defaultSeal = value; }
    }
}
