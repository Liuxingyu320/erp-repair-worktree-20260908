package com.erp.system.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.erp.common.core.web.domain.BaseEntity;

/**
 * HR入职单 hr_onboarding。
 */
public class HrOnboarding extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private Long onboardingId;
    private String onboardingNo;
    private String status;
    private Integer version;
    private String employeeName;
    private String phoneNumber;
    /** Read-only safe task projection; never persisted. */
    private String phoneNumberMasked;
    /** Read-only safe task decision projection; never persisted. */
    private transient Integer readinessMissingCount;
    /** Read-only safe task decision projection; never persisted. */
    private transient Boolean markReadyAllowed;
    private String employeeNo;
    private Long targetDeptId;
    private Long targetStoreId;
    private Long targetPostId;
    private Long directSupervisorUserId;
    private Long ownerUserId;
    private String ownerName;
    private String companyName;
    private String deptLevel1Name;
    private String deptLevel2Name;
    private String deptLevel3Name;
    private String storeName;
    private String positionName;
    private String departmentSupervisor;
    private String jobGrade;
    private String employeeCategory;
    private String sex;
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date birthDate;
    private String idType;
    private String idNumber;
    private String registeredResidence;
    private String currentAddress;
    private String maritalStatus;
    private String ethnicity;
    private String emergencyContact;
    private String emergencyContactRelation;
    private String emergencyContactPhone;
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date expectedEntryDate;
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date actualEntryDate;
    private String workLocation;
    private String workCityLevel;
    private String bankName;
    private String bankAccount;
    private String contractType;
    private String socialType;
    private String probationPeriod;
    private String legalEntity;
    private String sourceType;
    private Long linkedUserId;
    private String preferredConflictAction;
    private Long preferredBindUserId;
    private String accountConfigurationStatus;
    private String accountRiskCode;
    private String cancelReason;
    private Long cancelledByUserId;
    private String cancelledBy;
    private Date cancelledTime;
    private Long confirmedByUserId;
    private String confirmedBy;
    private Date confirmedTime;
    private String confirmIdempotencyKey;

    public Long getOnboardingId() { return onboardingId; }
    public void setOnboardingId(Long onboardingId) { this.onboardingId = onboardingId; }
    public String getOnboardingNo() { return onboardingNo; }
    public void setOnboardingNo(String onboardingNo) { this.onboardingNo = onboardingNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public String getPhoneNumberMasked() { return phoneNumberMasked; }
    public void setPhoneNumberMasked(String phoneNumberMasked) { this.phoneNumberMasked = phoneNumberMasked; }
    public Integer getReadinessMissingCount() { return readinessMissingCount; }
    public void setReadinessMissingCount(Integer readinessMissingCount) { this.readinessMissingCount = readinessMissingCount; }
    public Boolean getMarkReadyAllowed() { return markReadyAllowed; }
    public void setMarkReadyAllowed(Boolean markReadyAllowed) { this.markReadyAllowed = markReadyAllowed; }
    public String getEmployeeNo() { return employeeNo; }
    public void setEmployeeNo(String employeeNo) { this.employeeNo = employeeNo; }
    public Long getTargetDeptId() { return targetDeptId; }
    public void setTargetDeptId(Long targetDeptId) { this.targetDeptId = targetDeptId; }
    public Long getTargetStoreId() { return targetStoreId; }
    public void setTargetStoreId(Long targetStoreId) { this.targetStoreId = targetStoreId; }
    public Long getTargetPostId() { return targetPostId; }
    public void setTargetPostId(Long targetPostId) { this.targetPostId = targetPostId; }
    public Long getDirectSupervisorUserId() { return directSupervisorUserId; }
    public void setDirectSupervisorUserId(Long directSupervisorUserId) { this.directSupervisorUserId = directSupervisorUserId; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getDeptLevel1Name() { return deptLevel1Name; }
    public void setDeptLevel1Name(String deptLevel1Name) { this.deptLevel1Name = deptLevel1Name; }
    public String getDeptLevel2Name() { return deptLevel2Name; }
    public void setDeptLevel2Name(String deptLevel2Name) { this.deptLevel2Name = deptLevel2Name; }
    public String getDeptLevel3Name() { return deptLevel3Name; }
    public void setDeptLevel3Name(String deptLevel3Name) { this.deptLevel3Name = deptLevel3Name; }
    public String getStoreName() { return storeName; }
    public void setStoreName(String storeName) { this.storeName = storeName; }
    public String getPositionName() { return positionName; }
    public void setPositionName(String positionName) { this.positionName = positionName; }
    public String getDepartmentSupervisor() { return departmentSupervisor; }
    public void setDepartmentSupervisor(String departmentSupervisor) { this.departmentSupervisor = departmentSupervisor; }
    public String getJobGrade() { return jobGrade; }
    public void setJobGrade(String jobGrade) { this.jobGrade = jobGrade; }
    public String getEmployeeCategory() { return employeeCategory; }
    public void setEmployeeCategory(String employeeCategory) { this.employeeCategory = employeeCategory; }
    public String getSex() { return sex; }
    public void setSex(String sex) { this.sex = sex; }
    public Date getBirthDate() { return birthDate; }
    public void setBirthDate(Date birthDate) { this.birthDate = birthDate; }
    public String getIdType() { return idType; }
    public void setIdType(String idType) { this.idType = idType; }
    public String getIdNumber() { return idNumber; }
    public void setIdNumber(String idNumber) { this.idNumber = idNumber; }
    public String getRegisteredResidence() { return registeredResidence; }
    public void setRegisteredResidence(String registeredResidence) { this.registeredResidence = registeredResidence; }
    public String getCurrentAddress() { return currentAddress; }
    public void setCurrentAddress(String currentAddress) { this.currentAddress = currentAddress; }
    public String getMaritalStatus() { return maritalStatus; }
    public void setMaritalStatus(String maritalStatus) { this.maritalStatus = maritalStatus; }
    public String getEthnicity() { return ethnicity; }
    public void setEthnicity(String ethnicity) { this.ethnicity = ethnicity; }
    public String getEmergencyContact() { return emergencyContact; }
    public void setEmergencyContact(String emergencyContact) { this.emergencyContact = emergencyContact; }
    public String getEmergencyContactRelation() { return emergencyContactRelation; }
    public void setEmergencyContactRelation(String emergencyContactRelation) { this.emergencyContactRelation = emergencyContactRelation; }
    public String getEmergencyContactPhone() { return emergencyContactPhone; }
    public void setEmergencyContactPhone(String emergencyContactPhone) { this.emergencyContactPhone = emergencyContactPhone; }
    public Date getExpectedEntryDate() { return expectedEntryDate; }
    public void setExpectedEntryDate(Date expectedEntryDate) { this.expectedEntryDate = expectedEntryDate; }
    public Date getActualEntryDate() { return actualEntryDate; }
    public void setActualEntryDate(Date actualEntryDate) { this.actualEntryDate = actualEntryDate; }
    public String getWorkLocation() { return workLocation; }
    public void setWorkLocation(String workLocation) { this.workLocation = workLocation; }
    public String getWorkCityLevel() { return workCityLevel; }
    public void setWorkCityLevel(String workCityLevel) { this.workCityLevel = workCityLevel; }
    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    public String getBankAccount() { return bankAccount; }
    public void setBankAccount(String bankAccount) { this.bankAccount = bankAccount; }
    public String getContractType() { return contractType; }
    public void setContractType(String contractType) { this.contractType = contractType; }
    public String getSocialType() { return socialType; }
    public void setSocialType(String socialType) { this.socialType = socialType; }
    public String getProbationPeriod() { return probationPeriod; }
    public void setProbationPeriod(String probationPeriod) { this.probationPeriod = probationPeriod; }
    public String getLegalEntity() { return legalEntity; }
    public void setLegalEntity(String legalEntity) { this.legalEntity = legalEntity; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public Long getLinkedUserId() { return linkedUserId; }
    public void setLinkedUserId(Long linkedUserId) { this.linkedUserId = linkedUserId; }
    public String getPreferredConflictAction() { return preferredConflictAction; }
    public void setPreferredConflictAction(String preferredConflictAction) { this.preferredConflictAction = preferredConflictAction; }
    public Long getPreferredBindUserId() { return preferredBindUserId; }
    public void setPreferredBindUserId(Long preferredBindUserId) { this.preferredBindUserId = preferredBindUserId; }
    public String getAccountConfigurationStatus() { return accountConfigurationStatus; }
    public void setAccountConfigurationStatus(String accountConfigurationStatus) { this.accountConfigurationStatus = accountConfigurationStatus; }
    public String getAccountRiskCode() { return accountRiskCode; }
    public void setAccountRiskCode(String accountRiskCode) { this.accountRiskCode = accountRiskCode; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public Long getCancelledByUserId() { return cancelledByUserId; }
    public void setCancelledByUserId(Long cancelledByUserId) { this.cancelledByUserId = cancelledByUserId; }
    public String getCancelledBy() { return cancelledBy; }
    public void setCancelledBy(String cancelledBy) { this.cancelledBy = cancelledBy; }
    public Date getCancelledTime() { return cancelledTime; }
    public void setCancelledTime(Date cancelledTime) { this.cancelledTime = cancelledTime; }
    public Long getConfirmedByUserId() { return confirmedByUserId; }
    public void setConfirmedByUserId(Long confirmedByUserId) { this.confirmedByUserId = confirmedByUserId; }
    public String getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(String confirmedBy) { this.confirmedBy = confirmedBy; }
    public Date getConfirmedTime() { return confirmedTime; }
    public void setConfirmedTime(Date confirmedTime) { this.confirmedTime = confirmedTime; }
    public String getConfirmIdempotencyKey() { return confirmIdempotencyKey; }
    public void setConfirmIdempotencyKey(String confirmIdempotencyKey) { this.confirmIdempotencyKey = confirmIdempotencyKey; }
}
