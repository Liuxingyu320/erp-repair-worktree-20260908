package com.erp.system.domain.vo;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

/** PATCH-style whitelist of source and user-maintained onboarding fields. */
public class HrOnboardingUpdateRequest
{
    private Integer version;
    private String employeeName;
    private String phoneNumber;
    private Long targetDeptId;
    private Long targetStoreId;
    private Long targetPostId;
    private Long directSupervisorUserId;
    private Long ownerUserId;
    private String jobGrade;
    private String employeeCategory;
    private String sex;
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8") private Date birthDate;
    private String idType;
    private String idNumber;
    private String registeredResidence;
    private String currentAddress;
    private String maritalStatus;
    private String ethnicity;
    private String emergencyContact;
    private String emergencyContactRelation;
    private String emergencyContactPhone;
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8") private Date expectedEntryDate;
    private String workLocation;
    private String workCityLevel;
    private String bankName;
    private String bankAccount;
    private String contractType;
    private String socialType;
    private String probationPeriod;
    private String legalEntity;
    private String remark;
    @JsonIgnore private boolean phoneNumberPresent;
    @JsonIgnore private boolean idNumberPresent;
    @JsonIgnore private boolean registeredResidencePresent;
    @JsonIgnore private boolean currentAddressPresent;
    @JsonIgnore private boolean emergencyContactPhonePresent;
    @JsonIgnore private boolean bankAccountPresent;
    @JsonIgnore private boolean targetStoreIdPresent;
    @JsonIgnore private boolean directSupervisorUserIdPresent;
    @JsonIgnore private boolean birthDatePresent;

    public Integer getVersion() { return version; } public void setVersion(Integer v) { version=v; }
    public String getEmployeeName() { return employeeName; } public void setEmployeeName(String v) { employeeName=v; }
    public String getPhoneNumber() { return phoneNumber; } public void setPhoneNumber(String v) { phoneNumber=v; phoneNumberPresent=true; }
    public Long getTargetDeptId() { return targetDeptId; } public void setTargetDeptId(Long v) { targetDeptId=v; }
    public Long getTargetStoreId() { return targetStoreId; } public void setTargetStoreId(Long v) { targetStoreId=v; targetStoreIdPresent=true; }
    public Long getTargetPostId() { return targetPostId; } public void setTargetPostId(Long v) { targetPostId=v; }
    public Long getDirectSupervisorUserId() { return directSupervisorUserId; } public void setDirectSupervisorUserId(Long v) { directSupervisorUserId=v; directSupervisorUserIdPresent=true; }
    public Long getOwnerUserId() { return ownerUserId; } public void setOwnerUserId(Long v) { ownerUserId=v; }
    public String getJobGrade() { return jobGrade; } public void setJobGrade(String v) { jobGrade=v; }
    public String getEmployeeCategory() { return employeeCategory; } public void setEmployeeCategory(String v) { employeeCategory=v; }
    public String getSex() { return sex; } public void setSex(String v) { sex=v; }
    public Date getBirthDate() { return birthDate; } public void setBirthDate(Date v) { birthDate=v; birthDatePresent=true; }
    public String getIdType() { return idType; } public void setIdType(String v) { idType=v; }
    public String getIdNumber() { return idNumber; } public void setIdNumber(String v) { idNumber=v; idNumberPresent=true; }
    public String getRegisteredResidence() { return registeredResidence; } public void setRegisteredResidence(String v) { registeredResidence=v; registeredResidencePresent=true; }
    public String getCurrentAddress() { return currentAddress; } public void setCurrentAddress(String v) { currentAddress=v; currentAddressPresent=true; }
    public String getMaritalStatus() { return maritalStatus; } public void setMaritalStatus(String v) { maritalStatus=v; }
    public String getEthnicity() { return ethnicity; } public void setEthnicity(String v) { ethnicity=v; }
    public String getEmergencyContact() { return emergencyContact; } public void setEmergencyContact(String v) { emergencyContact=v; }
    public String getEmergencyContactRelation() { return emergencyContactRelation; } public void setEmergencyContactRelation(String v) { emergencyContactRelation=v; }
    public String getEmergencyContactPhone() { return emergencyContactPhone; } public void setEmergencyContactPhone(String v) { emergencyContactPhone=v; emergencyContactPhonePresent=true; }
    public Date getExpectedEntryDate() { return expectedEntryDate; } public void setExpectedEntryDate(Date v) { expectedEntryDate=v; }
    public String getWorkLocation() { return workLocation; } public void setWorkLocation(String v) { workLocation=v; }
    public String getWorkCityLevel() { return workCityLevel; } public void setWorkCityLevel(String v) { workCityLevel=v; }
    public String getBankName() { return bankName; } public void setBankName(String v) { bankName=v; }
    public String getBankAccount() { return bankAccount; } public void setBankAccount(String v) { bankAccount=v; bankAccountPresent=true; }
    public String getContractType() { return contractType; } public void setContractType(String v) { contractType=v; }
    public String getSocialType() { return socialType; } public void setSocialType(String v) { socialType=v; }
    public String getProbationPeriod() { return probationPeriod; } public void setProbationPeriod(String v) { probationPeriod=v; }
    public String getLegalEntity() { return legalEntity; } public void setLegalEntity(String v) { legalEntity=v; }
    public String getRemark() { return remark; } public void setRemark(String v) { remark=v; }
    @JsonIgnore public boolean isPhoneNumberPresent() { return phoneNumberPresent; }
    @JsonIgnore public boolean isIdNumberPresent() { return idNumberPresent; }
    @JsonIgnore public boolean isRegisteredResidencePresent() { return registeredResidencePresent; }
    @JsonIgnore public boolean isCurrentAddressPresent() { return currentAddressPresent; }
    @JsonIgnore public boolean isEmergencyContactPhonePresent() { return emergencyContactPhonePresent; }
    @JsonIgnore public boolean isBankAccountPresent() { return bankAccountPresent; }
    @JsonIgnore public boolean isTargetStoreIdPresent() { return targetStoreIdPresent; }
    @JsonIgnore public boolean isDirectSupervisorUserIdPresent() { return directSupervisorUserIdPresent; }
    @JsonIgnore public boolean isBirthDatePresent() { return birthDatePresent; }
}
