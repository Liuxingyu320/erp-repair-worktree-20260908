package com.erp.system.domain.vo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.erp.system.domain.vo.HrOnboardingCompletionVo.MissingField;

/**
 * HR入职单脱敏详情视图。
 */
public class HrOnboardingDetailVo extends HrOnboardingListVo
{
    private static final long serialVersionUID = 1L;

    private String idNumberMasked;
    private String bankAccountMasked;
    private String registeredResidenceMasked;
    private String currentAddressMasked;
    private String emergencyContactPhoneMasked;
    private Long directSupervisorUserId;
    private String departmentSupervisor;
    private String jobGrade;
    private String sex;
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date birthDate;
    private String idType;
    private String maritalStatus;
    private String ethnicity;
    private String emergencyContact;
    private String emergencyContactRelation;
    private String workLocation;
    private String workCityLevel;
    private String bankName;
    private String contractType;
    private String socialType;
    private String probationPeriod;
    private String legalEntity;
    private String remark;
    private String preferredConflictAction;
    private Long preferredBindUserId;
    private Map<String, List<MissingField>> missingOnboardingFields = new LinkedHashMap<>();
    private Map<String, List<MissingField>> missingProfileFields = new LinkedHashMap<>();
    private Integer onboardingCompletionPercent = 0;
    private Integer onboardingCompletedFieldCount = 0;
    private Integer onboardingRequiredFieldCount = 0;
    private Integer profileCompletionPercent = 0;
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date postEntryDueDate;
    private Boolean postEntryOverdue = false;
    private List<String> readinessBlockingCodes = new ArrayList<>();
    private List<String> accountConfigurationRiskCodes = new ArrayList<>();
    private List<OperationLogVo> operationLogs = new ArrayList<>();

    public String getIdNumberMasked() { return idNumberMasked; }
    public void setIdNumberMasked(String idNumberMasked) { this.idNumberMasked = idNumberMasked; }
    public String getBankAccountMasked() { return bankAccountMasked; }
    public void setBankAccountMasked(String bankAccountMasked) { this.bankAccountMasked = bankAccountMasked; }
    public String getRegisteredResidenceMasked() { return registeredResidenceMasked; }
    public void setRegisteredResidenceMasked(String registeredResidenceMasked) { this.registeredResidenceMasked = registeredResidenceMasked; }
    public String getCurrentAddressMasked() { return currentAddressMasked; }
    public void setCurrentAddressMasked(String currentAddressMasked) { this.currentAddressMasked = currentAddressMasked; }
    public String getEmergencyContactPhoneMasked() { return emergencyContactPhoneMasked; }
    public void setEmergencyContactPhoneMasked(String emergencyContactPhoneMasked) { this.emergencyContactPhoneMasked = emergencyContactPhoneMasked; }
    public Long getDirectSupervisorUserId() { return directSupervisorUserId; }
    public void setDirectSupervisorUserId(Long value) { directSupervisorUserId = value; }
    public String getDepartmentSupervisor() { return departmentSupervisor; }
    public void setDepartmentSupervisor(String value) { departmentSupervisor = value; }
    public String getJobGrade() { return jobGrade; }
    public void setJobGrade(String value) { jobGrade = value; }
    public String getSex() { return sex; }
    public void setSex(String value) { sex = value; }
    public Date getBirthDate() { return birthDate; }
    public void setBirthDate(Date value) { birthDate = value; }
    public String getIdType() { return idType; }
    public void setIdType(String value) { idType = value; }
    public String getMaritalStatus() { return maritalStatus; }
    public void setMaritalStatus(String value) { maritalStatus = value; }
    public String getEthnicity() { return ethnicity; }
    public void setEthnicity(String value) { ethnicity = value; }
    public String getEmergencyContact() { return emergencyContact; }
    public void setEmergencyContact(String value) { emergencyContact = value; }
    public String getEmergencyContactRelation() { return emergencyContactRelation; }
    public void setEmergencyContactRelation(String value) { emergencyContactRelation = value; }
    public String getWorkLocation() { return workLocation; }
    public void setWorkLocation(String value) { workLocation = value; }
    public String getWorkCityLevel() { return workCityLevel; }
    public void setWorkCityLevel(String value) { workCityLevel = value; }
    public String getBankName() { return bankName; }
    public void setBankName(String value) { bankName = value; }
    public String getContractType() { return contractType; }
    public void setContractType(String value) { contractType = value; }
    public String getSocialType() { return socialType; }
    public void setSocialType(String value) { socialType = value; }
    public String getProbationPeriod() { return probationPeriod; }
    public void setProbationPeriod(String value) { probationPeriod = value; }
    public String getLegalEntity() { return legalEntity; }
    public void setLegalEntity(String value) { legalEntity = value; }
    public String getRemark() { return remark; }
    public void setRemark(String value) { remark = value; }
    public String getPreferredConflictAction() { return preferredConflictAction; }
    public void setPreferredConflictAction(String value) { preferredConflictAction = value; }
    public Long getPreferredBindUserId() { return preferredBindUserId; }
    public void setPreferredBindUserId(Long value) { preferredBindUserId = value; }
    public Map<String, List<MissingField>> getMissingOnboardingFields() { return missingOnboardingFields; }
    public void setMissingOnboardingFields(Map<String, List<MissingField>> value) { missingOnboardingFields = value; }
    public Map<String, List<MissingField>> getMissingProfileFields() { return missingProfileFields; }
    public void setMissingProfileFields(Map<String, List<MissingField>> value) { missingProfileFields = value; }
    public Integer getOnboardingCompletionPercent() { return onboardingCompletionPercent; }
    public void setOnboardingCompletionPercent(Integer value) { onboardingCompletionPercent = value; }
    public Integer getOnboardingCompletedFieldCount() { return onboardingCompletedFieldCount; }
    public void setOnboardingCompletedFieldCount(Integer value) { onboardingCompletedFieldCount = value; }
    public Integer getOnboardingRequiredFieldCount() { return onboardingRequiredFieldCount; }
    public void setOnboardingRequiredFieldCount(Integer value) { onboardingRequiredFieldCount = value; }
    public Integer getProfileCompletionPercent() { return profileCompletionPercent; }
    public void setProfileCompletionPercent(Integer value) { profileCompletionPercent = value; }
    public Date getPostEntryDueDate() { return postEntryDueDate; }
    public void setPostEntryDueDate(Date value) { postEntryDueDate = value; }
    public Boolean getPostEntryOverdue() { return postEntryOverdue; }
    public void setPostEntryOverdue(Boolean value) { postEntryOverdue = value; }
    public List<String> getReadinessBlockingCodes() { return readinessBlockingCodes; }
    public void setReadinessBlockingCodes(List<String> value)
    { readinessBlockingCodes = value == null ? new ArrayList<>() : new ArrayList<>(value); }
    public List<String> getAccountConfigurationRiskCodes() { return accountConfigurationRiskCodes; }
    public void setAccountConfigurationRiskCodes(List<String> value) { accountConfigurationRiskCodes = value; }
    public List<OperationLogVo> getOperationLogs() { return operationLogs; }
    public void setOperationLogs(List<OperationLogVo> value) { operationLogs = value; }

    public static class OperationLogVo implements Serializable
    {
        private static final long serialVersionUID = 1L;
        private String operationType;
        private String operatorName;
        private String fromStatus;
        private String toStatus;
        private List<String> changedFieldKeys = new ArrayList<>();
        private Date operationTime;
        private String summary;
        public String getOperationType() { return operationType; } public void setOperationType(String v) { operationType=v; }
        public String getOperatorName() { return operatorName; } public void setOperatorName(String v) { operatorName=v; }
        public String getFromStatus() { return fromStatus; } public void setFromStatus(String v) { fromStatus=v; }
        public String getToStatus() { return toStatus; } public void setToStatus(String v) { toStatus=v; }
        public List<String> getChangedFieldKeys() { return changedFieldKeys; }
        public void setChangedFieldKeys(List<String> v) { changedFieldKeys=v==null?new ArrayList<>():v; }
        public Date getOperationTime() { return operationTime; } public void setOperationTime(Date v) { operationTime=v; }
        public String getSummary() { return summary; } public void setSummary(String v) { summary=v; }
    }
}
