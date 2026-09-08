package com.erp.oa.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Normalized, immutable-at-generation contract values from one Excel row plus reviewed facts. */
public class OaSignOnboardContractSnapshot
{
    private String employeeName;
    private String idNumber;
    private String phone;
    private String currentAddress;
    private String addressSource;
    private String socialTypeCode;
    private String employeePost;
    private String cityLevel;
    private String recommendedCompany;
    private String legalRepresentative;
    private String registeredAddress;
    /** Legal-entity values below always come from company master data, never Excel text. */
    private Long matchedLegalEntityId;
    private String matchedLegalEntityCode;
    private String matchedLegalEntityName;
    private String matchedUnifiedSocialCreditCode;
    private String matchedRegisteredAddress;
    private String matchedLegalRepresentative;
    private String matchedCompanyPhone;
    /** Seal values are frozen only after the selected seal passes ownership/hash validation. */
    private Long matchedSealId;
    private String matchedSealName;
    private String matchedSealImageUrl;
    private String matchedSealImageHash;
    private String contractTypeCode;
    private String jobGradeCode;
    private String workLocation;
    private String contractTermCode;
    private LocalDate contractStartDate;
    private LocalDate contractEndDate;
    private LocalDate probationStartDate;
    private LocalDate probationEndDate;
    private String workSchedule;
    private BigDecimal salaryTotal;
    private BigDecimal baseSalary;
    private BigDecimal postSalary;
    private BigDecimal fieldAllowance;
    private BigDecimal performanceSalary;
    private String salaryVersion;
    private String remarks;
    private String servicePersonType;
    private String insuranceType;
    private String studentStatus;
    private String schoolName;
    private String retirementStatus;
    private String incomeStartYearMonth;
    private BigDecimal referenceSalaryMin;
    private BigDecimal referenceSalaryMax;
    /** System compare-and-set token frozen when the row is first matched. */
    private String profileFactsHash;

    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
    public String getIdNumber() { return idNumber; }
    public void setIdNumber(String idNumber) { this.idNumber = idNumber; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getCurrentAddress() { return currentAddress; }
    public void setCurrentAddress(String currentAddress) { this.currentAddress = currentAddress; }
    public String getAddressSource() { return addressSource; }
    public void setAddressSource(String addressSource) { this.addressSource = addressSource; }
    public String getSocialTypeCode() { return socialTypeCode; }
    public void setSocialTypeCode(String socialTypeCode) { this.socialTypeCode = socialTypeCode; }
    public String getEmployeePost() { return employeePost; }
    public void setEmployeePost(String employeePost) { this.employeePost = employeePost; }
    public String getCityLevel() { return cityLevel; }
    public void setCityLevel(String cityLevel) { this.cityLevel = cityLevel; }
    public String getRecommendedCompany() { return recommendedCompany; }
    public void setRecommendedCompany(String recommendedCompany) { this.recommendedCompany = recommendedCompany; }
    public String getLegalRepresentative() { return legalRepresentative; }
    public void setLegalRepresentative(String legalRepresentative) { this.legalRepresentative = legalRepresentative; }
    public String getRegisteredAddress() { return registeredAddress; }
    public void setRegisteredAddress(String registeredAddress) { this.registeredAddress = registeredAddress; }
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
    public String getMatchedCompanyPhone() { return matchedCompanyPhone; }
    public void setMatchedCompanyPhone(String value) { this.matchedCompanyPhone = value; }
    public Long getMatchedSealId() { return matchedSealId; }
    public void setMatchedSealId(Long value) { this.matchedSealId = value; }
    public String getMatchedSealName() { return matchedSealName; }
    public void setMatchedSealName(String value) { this.matchedSealName = value; }
    public String getMatchedSealImageUrl() { return matchedSealImageUrl; }
    public void setMatchedSealImageUrl(String value) { this.matchedSealImageUrl = value; }
    public String getMatchedSealImageHash() { return matchedSealImageHash; }
    public void setMatchedSealImageHash(String value) { this.matchedSealImageHash = value; }
    public String getContractTypeCode() { return contractTypeCode; }
    public void setContractTypeCode(String contractTypeCode) { this.contractTypeCode = contractTypeCode; }
    public String getJobGradeCode() { return jobGradeCode; }
    public void setJobGradeCode(String jobGradeCode) { this.jobGradeCode = jobGradeCode; }
    public String getWorkLocation() { return workLocation; }
    public void setWorkLocation(String workLocation) { this.workLocation = workLocation; }
    public String getContractTermCode() { return contractTermCode; }
    public void setContractTermCode(String contractTermCode) { this.contractTermCode = contractTermCode; }
    public LocalDate getContractStartDate() { return contractStartDate; }
    public void setContractStartDate(LocalDate contractStartDate) { this.contractStartDate = contractStartDate; }
    public LocalDate getContractEndDate() { return contractEndDate; }
    public void setContractEndDate(LocalDate contractEndDate) { this.contractEndDate = contractEndDate; }
    public LocalDate getProbationStartDate() { return probationStartDate; }
    public void setProbationStartDate(LocalDate probationStartDate) { this.probationStartDate = probationStartDate; }
    public LocalDate getProbationEndDate() { return probationEndDate; }
    public void setProbationEndDate(LocalDate probationEndDate) { this.probationEndDate = probationEndDate; }
    public String getWorkSchedule() { return workSchedule; }
    public void setWorkSchedule(String workSchedule) { this.workSchedule = workSchedule; }
    public BigDecimal getSalaryTotal() { return salaryTotal; }
    public void setSalaryTotal(BigDecimal salaryTotal) { this.salaryTotal = salaryTotal; }
    public BigDecimal getBaseSalary() { return baseSalary; }
    public void setBaseSalary(BigDecimal baseSalary) { this.baseSalary = baseSalary; }
    public BigDecimal getPostSalary() { return postSalary; }
    public void setPostSalary(BigDecimal postSalary) { this.postSalary = postSalary; }
    public BigDecimal getFieldAllowance() { return fieldAllowance; }
    public void setFieldAllowance(BigDecimal fieldAllowance) { this.fieldAllowance = fieldAllowance; }
    public BigDecimal getPerformanceSalary() { return performanceSalary; }
    public void setPerformanceSalary(BigDecimal performanceSalary) { this.performanceSalary = performanceSalary; }
    public String getSalaryVersion() { return salaryVersion; }
    public void setSalaryVersion(String salaryVersion) { this.salaryVersion = salaryVersion; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public String getServicePersonType() { return servicePersonType; }
    public void setServicePersonType(String servicePersonType) { this.servicePersonType = servicePersonType; }
    public String getInsuranceType() { return insuranceType; }
    public void setInsuranceType(String insuranceType) { this.insuranceType = insuranceType; }
    public String getStudentStatus() { return studentStatus; }
    public void setStudentStatus(String studentStatus) { this.studentStatus = studentStatus; }
    public String getSchoolName() { return schoolName; }
    public void setSchoolName(String schoolName) { this.schoolName = schoolName; }
    public String getRetirementStatus() { return retirementStatus; }
    public void setRetirementStatus(String retirementStatus) { this.retirementStatus = retirementStatus; }
    public String getIncomeStartYearMonth() { return incomeStartYearMonth; }
    public void setIncomeStartYearMonth(String incomeStartYearMonth) { this.incomeStartYearMonth = incomeStartYearMonth; }
    public BigDecimal getReferenceSalaryMin() { return referenceSalaryMin; }
    public void setReferenceSalaryMin(BigDecimal referenceSalaryMin) { this.referenceSalaryMin = referenceSalaryMin; }
    public BigDecimal getReferenceSalaryMax() { return referenceSalaryMax; }
    public void setReferenceSalaryMax(BigDecimal referenceSalaryMax) { this.referenceSalaryMax = referenceSalaryMax; }
    public String getProfileFactsHash() { return profileFactsHash; }
    public void setProfileFactsHash(String profileFactsHash) { this.profileFactsHash = profileFactsHash; }
}
