package com.erp.system.domain.vo;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/** Stable masked employee-master detail DTO. */
public class HrEmployeeProfileVo
{
    /** Keep employee identifiers exact in JavaScript clients. */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    private Boolean profileInitialized;
    private Long deptId;
    private String departmentName;
    private List<Long> postIds = new ArrayList<>();
    private String postNames;
    private String employeeNo;
    private String positionNo;
    private String employeeName;
    private String remark;
    private String nickName;
    private String phonenumber;
    private String email;
    private String sex;
    private String status;
    private String phoneNumberMasked;
    private String idNumberMasked;
    private String bankAccountMasked;
    private String registeredResidenceMasked;
    private String currentAddressMasked;
    private String emergencyContactPhoneMasked;
    private String officePhoneMasked;
    private Integer profileCompletionPercent;
    private Integer profileCompletedFieldCount;
    private Integer profileApplicableFieldCount;
    private Integer profileNotApplicableFieldCount;
    private Integer profileTrackedFieldCount;
    private List<String> missingProfileFields = new ArrayList<>();
    private Integer requiredCompletionPercent;
    private Integer requiredCompletedFieldCount;
    private Integer requiredApplicableFieldCount;
    private Integer coveragePercent;
    private List<String> missingRequiredFields = new ArrayList<>();
    private List<String> missingOptionalFields = new ArrayList<>();
    private Map<String, List<String>> missingByResponsibility = new LinkedHashMap<>();
    private Map<String, Object> fields = new LinkedHashMap<>();
    private Map<String, Object> profile = new LinkedHashMap<>();
    private LocalDate healthCertificateNextValidFrom;
    public LocalDate getHealthCertificateNextValidFrom(){return healthCertificateNextValidFrom;}
    public void setHealthCertificateNextValidFrom(LocalDate value){healthCertificateNextValidFrom=value;}
    private String healthCertificateStatus;
    private LocalDate healthCertificateIssuedDate;
    private LocalDate healthCertificateExpiresOn;
    private Long healthCertificateDaysRemaining;
    private Boolean healthCertificateAttachmentPresent;

    public Long getUserId() { return userId; } public void setUserId(Long v) { userId=v; }
    public Boolean getProfileInitialized(){return profileInitialized;}
    public void setProfileInitialized(Boolean v){profileInitialized=v;}
    public Long getDeptId(){return deptId;} public void setDeptId(Long v){deptId=v;}
    public String getDepartmentName(){return departmentName;} public void setDepartmentName(String v){departmentName=v;}
    public List<Long> getPostIds(){return postIds;} public void setPostIds(List<Long> v){postIds=v==null?new ArrayList<>():v;}
    public String getPostNames(){return postNames;} public void setPostNames(String v){postNames=v;}
    public String getEmployeeNo() { return employeeNo; } public void setEmployeeNo(String v) { employeeNo=v; }
    public String getPositionNo() { return positionNo; } public void setPositionNo(String v) { positionNo=v; }
    public String getEmployeeName() { return employeeName; } public void setEmployeeName(String v) { employeeName=v; }
    public String getRemark() { return remark; } public void setRemark(String v) { remark=v; }
    public String getNickName(){return nickName;} public void setNickName(String v){nickName=v;}
    public String getPhonenumber(){return phonenumber;} public void setPhonenumber(String v){phonenumber=v;}
    public String getEmail(){return email;} public void setEmail(String v){email=v;}
    public String getSex(){return sex;} public void setSex(String v){sex=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public String getPhoneNumberMasked() { return phoneNumberMasked; } public void setPhoneNumberMasked(String v) { phoneNumberMasked=v; }
    public String getIdNumberMasked() { return idNumberMasked; } public void setIdNumberMasked(String v) { idNumberMasked=v; }
    public String getBankAccountMasked() { return bankAccountMasked; } public void setBankAccountMasked(String v) { bankAccountMasked=v; }
    public String getRegisteredResidenceMasked() { return registeredResidenceMasked; } public void setRegisteredResidenceMasked(String v) { registeredResidenceMasked=v; }
    public String getCurrentAddressMasked() { return currentAddressMasked; } public void setCurrentAddressMasked(String v) { currentAddressMasked=v; }
    public String getEmergencyContactPhoneMasked() { return emergencyContactPhoneMasked; } public void setEmergencyContactPhoneMasked(String v) { emergencyContactPhoneMasked=v; }
    public String getOfficePhoneMasked() { return officePhoneMasked; } public void setOfficePhoneMasked(String v) { officePhoneMasked=v; }
    public Integer getProfileCompletionPercent() { return profileCompletionPercent; } public void setProfileCompletionPercent(Integer v) { profileCompletionPercent=v; }
    public Integer getProfileCompletedFieldCount() { return profileCompletedFieldCount; }
    public void setProfileCompletedFieldCount(Integer v) { profileCompletedFieldCount=v; }
    public Integer getProfileApplicableFieldCount() { return profileApplicableFieldCount; }
    public void setProfileApplicableFieldCount(Integer v) { profileApplicableFieldCount=v; }
    public Integer getProfileNotApplicableFieldCount() { return profileNotApplicableFieldCount; }
    public void setProfileNotApplicableFieldCount(Integer v) { profileNotApplicableFieldCount=v; }
    public Integer getProfileTrackedFieldCount() { return profileTrackedFieldCount; }
    public void setProfileTrackedFieldCount(Integer v) { profileTrackedFieldCount=v; }
    public List<String> getMissingProfileFields() { return missingProfileFields; } public void setMissingProfileFields(List<String> v) { missingProfileFields=v; }
    public Integer getRequiredCompletionPercent() { return requiredCompletionPercent; }
    public void setRequiredCompletionPercent(Integer v) { requiredCompletionPercent=v; }
    public Integer getRequiredCompletedFieldCount() { return requiredCompletedFieldCount; }
    public void setRequiredCompletedFieldCount(Integer v) { requiredCompletedFieldCount=v; }
    public Integer getRequiredApplicableFieldCount() { return requiredApplicableFieldCount; }
    public void setRequiredApplicableFieldCount(Integer v) { requiredApplicableFieldCount=v; }
    public Integer getCoveragePercent() { return coveragePercent; }
    public void setCoveragePercent(Integer v) { coveragePercent=v; }
    public List<String> getMissingRequiredFields() { return missingRequiredFields; }
    public void setMissingRequiredFields(List<String> v) { missingRequiredFields=v==null?new ArrayList<>():new ArrayList<>(v); }
    public List<String> getMissingOptionalFields() { return missingOptionalFields; }
    public void setMissingOptionalFields(List<String> v) { missingOptionalFields=v==null?new ArrayList<>():new ArrayList<>(v); }
    public Map<String,List<String>> getMissingByResponsibility(){return missingByResponsibility;}
    public void setMissingByResponsibility(Map<String,List<String>> v)
    {
        missingByResponsibility=new LinkedHashMap<>();
        if(v!=null)v.forEach((key,value)->missingByResponsibility.put(key,
                value==null?new ArrayList<>():new ArrayList<>(value)));
    }
    public Map<String, Object> getFields() { return fields; } public void setFields(Map<String, Object> v) { fields=v; }
    public Map<String,Object> getProfile(){return profile;} public void setProfile(Map<String,Object> v){profile=v;}
    public String getHealthCertificateStatus(){return healthCertificateStatus;} public void setHealthCertificateStatus(String v){healthCertificateStatus=v;}
    public LocalDate getHealthCertificateIssuedDate(){return healthCertificateIssuedDate;} public void setHealthCertificateIssuedDate(LocalDate v){healthCertificateIssuedDate=v;}
    public LocalDate getHealthCertificateExpiresOn(){return healthCertificateExpiresOn;} public void setHealthCertificateExpiresOn(LocalDate v){healthCertificateExpiresOn=v;}
    public Long getHealthCertificateDaysRemaining(){return healthCertificateDaysRemaining;} public void setHealthCertificateDaysRemaining(Long v){healthCertificateDaysRemaining=v;}
    public Boolean getHealthCertificateAttachmentPresent(){return healthCertificateAttachmentPresent;} public void setHealthCertificateAttachmentPresent(Boolean v){healthCertificateAttachmentPresent=v;}
}
