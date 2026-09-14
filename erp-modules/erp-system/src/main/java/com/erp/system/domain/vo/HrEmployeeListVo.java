package com.erp.system.domain.vo;

import com.erp.common.core.annotation.Excel;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/** Safe default employee list/export row; deliberately has no ID, bank or address fields. */
public class HrEmployeeListVo
{
    private LocalDate healthCertificateNextValidFrom;
    public LocalDate getHealthCertificateNextValidFrom(){return healthCertificateNextValidFrom;}
    public void setHealthCertificateNextValidFrom(LocalDate value){healthCertificateNextValidFrom=value;}

    /** Keep employee identifiers exact in JavaScript clients. */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    private Boolean profileInitialized;
    @Excel(name = "工号") private String employeeNo;
    @Excel(name = "岗位工号") private String positionNo;
    @Excel(name = "姓名") private String employeeName;
    @Excel(name = "手机号") private String phoneNumberMasked;
    @Excel(name = "部门") private String departmentName;
    @Excel(name = "职位") private String positionName;
    @Excel(name = "员工状态") private String employeeStatus;
    @Excel(name = "人员类别") private String employeeCategory;
    @Excel(name = "档案完整度") private Integer profileCompletionPercent;
    private Integer profileCompletedFieldCount;
    private Integer profileApplicableFieldCount;
    private Integer profileNotApplicableFieldCount;
    private Integer profileTrackedFieldCount;
    private List<String> missingProfileFields = new ArrayList<>();
    @Excel(name = "业务必填完成率") private Integer requiredCompletionPercent;
    private Integer requiredCompletedFieldCount;
    private Integer requiredApplicableFieldCount;
    private Integer coveragePercent;
    private List<String> missingRequiredFields = new ArrayList<>();
    private List<String> missingOptionalFields = new ArrayList<>();
    private Map<String, List<String>> missingByResponsibility = new LinkedHashMap<>();
    private Boolean accountEnabled;
    @Excel(name = "账号配置状态") private String accountConfigurationStatus;
    private List<String> accountConfigurationRiskCodes = new ArrayList<>();
    @Excel(name = "备注") private String remark;
    private String departmentSupervisor;
    private String directSupervisor;
    private String contractStartDate;
    private String contractEndDate;
    private String contractType;
    private String socialType;
    private String socialSecurityLocation;
    private String housingFundLocation;
    private String legalEntity;
    private String recruitmentChannel;
    private String leaveDate;
    private String onboardingDetailUrl;
    private String employeeDetailUrl;
    private String accountConfigurationUrl;
    @Excel(name = "健康证状态") private String healthCertificateStatus;
    @Excel(name = "健康证办理日期", dateFormat = "yyyy-MM-dd") private LocalDate healthCertificateIssuedDate;
    @Excel(name = "健康证到期日期", dateFormat = "yyyy-MM-dd") private LocalDate healthCertificateExpiresOn;
    private Long healthCertificateDaysRemaining;
    private Boolean healthCertificateAttachmentPresent;

    public Long getUserId() { return userId; } public void setUserId(Long v) { userId=v; }
    public Boolean getProfileInitialized(){return profileInitialized;}
    public void setProfileInitialized(Boolean v){profileInitialized=v;}
    public String getEmployeeNo() { return employeeNo; } public void setEmployeeNo(String v) { employeeNo=v; }
    public String getPositionNo() { return positionNo; } public void setPositionNo(String v) { positionNo=v; }
    public String getEmployeeName() { return employeeName; } public void setEmployeeName(String v) { employeeName=v; }
    public String getPhoneNumberMasked() { return phoneNumberMasked; } public void setPhoneNumberMasked(String v) { phoneNumberMasked=v; }
    public String getDepartmentName() { return departmentName; } public void setDepartmentName(String v) { departmentName=v; }
    public String getPositionName() { return positionName; } public void setPositionName(String v) { positionName=v; }
    public String getEmployeeStatus() { return employeeStatus; } public void setEmployeeStatus(String v) { employeeStatus=v; }
    public String getEmployeeCategory() { return employeeCategory; } public void setEmployeeCategory(String v) { employeeCategory=v; }
    public Integer getProfileCompletionPercent() { return profileCompletionPercent; } public void setProfileCompletionPercent(Integer v) { profileCompletionPercent=v; }
    public Integer getProfileCompletedFieldCount() { return profileCompletedFieldCount; }
    public void setProfileCompletedFieldCount(Integer v) { profileCompletedFieldCount=v; }
    public Integer getProfileApplicableFieldCount() { return profileApplicableFieldCount; }
    public void setProfileApplicableFieldCount(Integer v) { profileApplicableFieldCount=v; }
    public Integer getProfileNotApplicableFieldCount() { return profileNotApplicableFieldCount; }
    public void setProfileNotApplicableFieldCount(Integer v) { profileNotApplicableFieldCount=v; }
    public Integer getProfileTrackedFieldCount() { return profileTrackedFieldCount; }
    public void setProfileTrackedFieldCount(Integer v) { profileTrackedFieldCount=v; }
    public List<String> getMissingProfileFields() { return missingProfileFields; }
    public void setMissingProfileFields(List<String> v)
    { missingProfileFields=v==null?new ArrayList<>():new ArrayList<>(v); }
    public Integer getRequiredCompletionPercent() { return requiredCompletionPercent; }
    public void setRequiredCompletionPercent(Integer v) { requiredCompletionPercent=v; }
    public Integer getRequiredCompletedFieldCount() { return requiredCompletedFieldCount; }
    public void setRequiredCompletedFieldCount(Integer v) { requiredCompletedFieldCount=v; }
    public Integer getRequiredApplicableFieldCount() { return requiredApplicableFieldCount; }
    public void setRequiredApplicableFieldCount(Integer v) { requiredApplicableFieldCount=v; }
    public Integer getCoveragePercent() { return coveragePercent; }
    public void setCoveragePercent(Integer v) { coveragePercent=v; }
    public List<String> getMissingRequiredFields() { return missingRequiredFields; }
    public void setMissingRequiredFields(List<String> v)
    { missingRequiredFields=v==null?new ArrayList<>():new ArrayList<>(v); }
    public List<String> getMissingOptionalFields() { return missingOptionalFields; }
    public void setMissingOptionalFields(List<String> v)
    { missingOptionalFields=v==null?new ArrayList<>():new ArrayList<>(v); }
    public Map<String, List<String>> getMissingByResponsibility() { return missingByResponsibility; }
    public void setMissingByResponsibility(Map<String, List<String>> v)
    {
        missingByResponsibility=new LinkedHashMap<>();
        if(v!=null)v.forEach((key,value)->missingByResponsibility.put(key,
                value==null?new ArrayList<>():new ArrayList<>(value)));
    }
    public Boolean getAccountEnabled() { return accountEnabled; }
    public void setAccountEnabled(Boolean accountEnabled) { this.accountEnabled=accountEnabled; }
    public String getAccountConfigurationStatus() { return accountConfigurationStatus; } public void setAccountConfigurationStatus(String v) { accountConfigurationStatus=v; }
    public List<String> getAccountConfigurationRiskCodes(){return accountConfigurationRiskCodes;}
    public void setAccountConfigurationRiskCodes(List<String> v){accountConfigurationRiskCodes=v==null?new ArrayList<>():v;}
    public String getRemark() { return remark; } public void setRemark(String v) { remark=v; }
    public String getDepartmentSupervisor(){return departmentSupervisor;} public void setDepartmentSupervisor(String v){departmentSupervisor=v;}
    public String getDirectSupervisor(){return directSupervisor;} public void setDirectSupervisor(String v){directSupervisor=v;}
    public String getContractStartDate(){return contractStartDate;} public void setContractStartDate(String v){contractStartDate=v;}
    public String getContractEndDate(){return contractEndDate;} public void setContractEndDate(String v){contractEndDate=v;}
    public String getContractType(){return contractType;} public void setContractType(String v){contractType=v;}
    public String getSocialType(){return socialType;} public void setSocialType(String v){socialType=v;}
    public String getSocialSecurityLocation(){return socialSecurityLocation;} public void setSocialSecurityLocation(String v){socialSecurityLocation=v;}
    public String getHousingFundLocation(){return housingFundLocation;} public void setHousingFundLocation(String v){housingFundLocation=v;}
    public String getLegalEntity(){return legalEntity;} public void setLegalEntity(String v){legalEntity=v;}
    public String getRecruitmentChannel(){return recruitmentChannel;} public void setRecruitmentChannel(String v){recruitmentChannel=v;}
    public String getLeaveDate(){return leaveDate;} public void setLeaveDate(String v){leaveDate=v;}
    public String getOnboardingDetailUrl(){return onboardingDetailUrl;} public void setOnboardingDetailUrl(String v){onboardingDetailUrl=v;}
    public String getEmployeeDetailUrl(){return employeeDetailUrl;} public void setEmployeeDetailUrl(String v){employeeDetailUrl=v;}
    public String getAccountConfigurationUrl(){return accountConfigurationUrl;} public void setAccountConfigurationUrl(String v){accountConfigurationUrl=v;}
    public String getHealthCertificateStatus(){return healthCertificateStatus;} public void setHealthCertificateStatus(String v){healthCertificateStatus=v;}
    public LocalDate getHealthCertificateIssuedDate(){return healthCertificateIssuedDate;} public void setHealthCertificateIssuedDate(LocalDate v){healthCertificateIssuedDate=v;}
    public LocalDate getHealthCertificateExpiresOn(){return healthCertificateExpiresOn;} public void setHealthCertificateExpiresOn(LocalDate v){healthCertificateExpiresOn=v;}
    public Long getHealthCertificateDaysRemaining(){return healthCertificateDaysRemaining;} public void setHealthCertificateDaysRemaining(Long v){healthCertificateDaysRemaining=v;}
    public Boolean getHealthCertificateAttachmentPresent(){return healthCertificateAttachmentPresent;} public void setHealthCertificateAttachmentPresent(Boolean v){healthCertificateAttachmentPresent=v;}
}
