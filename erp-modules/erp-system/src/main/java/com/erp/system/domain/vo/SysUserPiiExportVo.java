package com.erp.system.domain.vo;

import java.util.Date;
import com.erp.common.core.annotation.Excel;

/** Dedicated sensitive export model. Access is protected by two permissions and a reason code. */
public class SysUserPiiExportVo
{
    @Excel(name = "用户编号") private Long userId;
    @Excel(name = "登录账号") private String userName;
    @Excel(name = "姓名") private String nickName;
    @Excel(name = "部门") private String deptName;
    @Excel(name = "手机号") private String phonenumber;
    @Excel(name = "邮箱") private String email;
    @Excel(name = "性别", readConverterExp = "0=男,1=女,2=未知") private String sex;
    @Excel(name = "出生日期", dateFormat = "yyyy-MM-dd") private Date birthDate;
    @Excel(name = "证件类型") private String idType;
    @Excel(name = "证件号码") private String idNumber;
    @Excel(name = "血型") private String bloodType;
    @Excel(name = "户籍地址") private String registeredResidence;
    @Excel(name = "现居住地址") private String currentAddress;
    @Excel(name = "第一学历") private String firstEducation;
    @Excel(name = "第一学位") private String firstDegree;
    @Excel(name = "第一学历毕业日期", dateFormat = "yyyy-MM-dd") private Date firstGraduationDate;
    @Excel(name = "第一学历毕业学校") private String firstGraduationSchool;
    @Excel(name = "第一学历专业") private String firstMajor;
    @Excel(name = "最高学历") private String highestEducation;
    @Excel(name = "最高学位") private String highestDegree;
    @Excel(name = "最高学历毕业日期", dateFormat = "yyyy-MM-dd") private Date highestGraduationDate;
    @Excel(name = "最高学历毕业学校") private String highestGraduationSchool;
    @Excel(name = "最高学历专业") private String highestMajor;
    @Excel(name = "政治面貌") private String politicalStatus;
    @Excel(name = "婚姻状况") private String maritalStatus;
    @Excel(name = "国籍") private String nationality;
    @Excel(name = "外籍标识") private String foreignNationalFlag;
    @Excel(name = "民族") private String ethnicity;
    @Excel(name = "健康状况") private String healthStatus;
    @Excel(name = "紧急联系人") private String emergencyContact;
    @Excel(name = "紧急联系人关系") private String emergencyContactRelation;
    @Excel(name = "紧急联系人电话") private String emergencyContactPhone;
    @Excel(name = "办公电话") private String officePhone;
    @Excel(name = "工作地") private String workLocation;
    @Excel(name = "户口性质") private String householdType;
    @Excel(name = "社保缴纳地") private String socialSecurityLocation;
    @Excel(name = "公积金缴纳地") private String housingFundLocation;
    @Excel(name = "开户银行") private String bankName;
    @Excel(name = "银行卡号") private String bankAccount;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getNickName() { return nickName; }
    public void setNickName(String nickName) { this.nickName = nickName; }
    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }
    public String getPhonenumber() { return phonenumber; }
    public void setPhonenumber(String phonenumber) { this.phonenumber = phonenumber; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getSex() { return sex; }
    public void setSex(String sex) { this.sex = sex; }
    public Date getBirthDate() { return birthDate; }
    public void setBirthDate(Date birthDate) { this.birthDate = birthDate; }
    public String getIdType() { return idType; }
    public void setIdType(String idType) { this.idType = idType; }
    public String getIdNumber() { return idNumber; }
    public void setIdNumber(String idNumber) { this.idNumber = idNumber; }
    public String getBloodType() { return bloodType; }
    public void setBloodType(String bloodType) { this.bloodType = bloodType; }
    public String getRegisteredResidence() { return registeredResidence; }
    public void setRegisteredResidence(String registeredResidence) { this.registeredResidence = registeredResidence; }
    public String getCurrentAddress() { return currentAddress; }
    public void setCurrentAddress(String currentAddress) { this.currentAddress = currentAddress; }
    public String getFirstEducation() { return firstEducation; }
    public void setFirstEducation(String firstEducation) { this.firstEducation = firstEducation; }
    public String getFirstDegree() { return firstDegree; }
    public void setFirstDegree(String firstDegree) { this.firstDegree = firstDegree; }
    public Date getFirstGraduationDate() { return firstGraduationDate; }
    public void setFirstGraduationDate(Date firstGraduationDate) { this.firstGraduationDate = firstGraduationDate; }
    public String getFirstGraduationSchool() { return firstGraduationSchool; }
    public void setFirstGraduationSchool(String firstGraduationSchool) { this.firstGraduationSchool = firstGraduationSchool; }
    public String getFirstMajor() { return firstMajor; }
    public void setFirstMajor(String firstMajor) { this.firstMajor = firstMajor; }
    public String getHighestEducation() { return highestEducation; }
    public void setHighestEducation(String highestEducation) { this.highestEducation = highestEducation; }
    public String getHighestDegree() { return highestDegree; }
    public void setHighestDegree(String highestDegree) { this.highestDegree = highestDegree; }
    public Date getHighestGraduationDate() { return highestGraduationDate; }
    public void setHighestGraduationDate(Date highestGraduationDate) { this.highestGraduationDate = highestGraduationDate; }
    public String getHighestGraduationSchool() { return highestGraduationSchool; }
    public void setHighestGraduationSchool(String highestGraduationSchool) { this.highestGraduationSchool = highestGraduationSchool; }
    public String getHighestMajor() { return highestMajor; }
    public void setHighestMajor(String highestMajor) { this.highestMajor = highestMajor; }
    public String getPoliticalStatus() { return politicalStatus; }
    public void setPoliticalStatus(String politicalStatus) { this.politicalStatus = politicalStatus; }
    public String getMaritalStatus() { return maritalStatus; }
    public void setMaritalStatus(String maritalStatus) { this.maritalStatus = maritalStatus; }
    public String getNationality() { return nationality; }
    public void setNationality(String nationality) { this.nationality = nationality; }
    public String getForeignNationalFlag() { return foreignNationalFlag; }
    public void setForeignNationalFlag(String foreignNationalFlag) { this.foreignNationalFlag = foreignNationalFlag; }
    public String getEthnicity() { return ethnicity; }
    public void setEthnicity(String ethnicity) { this.ethnicity = ethnicity; }
    public String getHealthStatus() { return healthStatus; }
    public void setHealthStatus(String healthStatus) { this.healthStatus = healthStatus; }
    public String getEmergencyContact() { return emergencyContact; }
    public void setEmergencyContact(String emergencyContact) { this.emergencyContact = emergencyContact; }
    public String getEmergencyContactRelation() { return emergencyContactRelation; }
    public void setEmergencyContactRelation(String emergencyContactRelation) { this.emergencyContactRelation = emergencyContactRelation; }
    public String getEmergencyContactPhone() { return emergencyContactPhone; }
    public void setEmergencyContactPhone(String emergencyContactPhone) { this.emergencyContactPhone = emergencyContactPhone; }
    public String getOfficePhone() { return officePhone; }
    public void setOfficePhone(String officePhone) { this.officePhone = officePhone; }
    public String getWorkLocation() { return workLocation; }
    public void setWorkLocation(String workLocation) { this.workLocation = workLocation; }
    public String getHouseholdType() { return householdType; }
    public void setHouseholdType(String householdType) { this.householdType = householdType; }
    public String getSocialSecurityLocation() { return socialSecurityLocation; }
    public void setSocialSecurityLocation(String socialSecurityLocation) { this.socialSecurityLocation = socialSecurityLocation; }
    public String getHousingFundLocation() { return housingFundLocation; }
    public void setHousingFundLocation(String housingFundLocation) { this.housingFundLocation = housingFundLocation; }
    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    public String getBankAccount() { return bankAccount; }
    public void setBankAccount(String bankAccount) { this.bankAccount = bankAccount; }

    public String auditDigestMaterial()
    {
        return String.join("\u001f",
                value(userId), value(userName), value(nickName), value(deptName),
                value(phonenumber), value(email), value(sex), value(birthDate), value(idType),
                value(idNumber), value(bloodType), value(registeredResidence), value(currentAddress),
                value(firstEducation), value(firstDegree), value(firstGraduationDate),
                value(firstGraduationSchool), value(firstMajor), value(highestEducation),
                value(highestDegree), value(highestGraduationDate), value(highestGraduationSchool),
                value(highestMajor), value(politicalStatus), value(maritalStatus), value(nationality),
                value(foreignNationalFlag), value(ethnicity), value(healthStatus),
                value(emergencyContact), value(emergencyContactRelation), value(emergencyContactPhone),
                value(officePhone), value(workLocation), value(householdType),
                value(socialSecurityLocation), value(housingFundLocation), value(bankName), value(bankAccount));
    }

    private String value(Object value)
    {
        return value == null ? "<null>" : String.valueOf(value);
    }
}
