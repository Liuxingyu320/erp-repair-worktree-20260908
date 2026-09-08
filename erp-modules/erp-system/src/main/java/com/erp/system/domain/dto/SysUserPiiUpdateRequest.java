package com.erp.system.domain.dto;

import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Explicit administrator PII patch contract. Setter tracking preserves the
 * difference between an omitted property and an explicit null used to clear it.
 */
public class SysUserPiiUpdateRequest
{
    @JsonIgnore
    private final Map<String, Object> providedValues = new LinkedHashMap<>();

    private String email;
    private String phonenumber;
    private String sex;
    private Date birthDate;
    private String idType;
    private String idNumber;
    private String bloodType;
    private String registeredResidence;
    private String currentAddress;
    private String firstEducation;
    private String firstDegree;
    private Date firstGraduationDate;
    private String firstGraduationSchool;
    private String firstMajor;
    private String highestEducation;
    private String highestDegree;
    private Date highestGraduationDate;
    private String highestGraduationSchool;
    private String highestMajor;
    private String politicalStatus;
    private String maritalStatus;
    private String nationality;
    private String foreignNationalFlag;
    private String ethnicity;
    private String healthStatus;
    private String emergencyContact;
    private String emergencyContactRelation;
    private String emergencyContactPhone;
    private String officePhone;
    private String workLocation;
    private String householdType;
    private String socialSecurityLocation;
    private String housingFundLocation;
    private String bankName;
    private String bankAccount;

    @JsonIgnore
    public Map<String, Object> getProvidedValues()
    {
        return Collections.unmodifiableMap(providedValues);
    }

    public void clearProvidedValues()
    {
        providedValues.clear();
    }

    @JsonAnySetter
    public void rejectUnknownField(String field, Object ignored)
    {
        throw new IllegalArgumentException("个人信息接口不接受字段: " + field);
    }

    /** All contract fields, used only to compare field names without logging values. */
    @JsonIgnore
    public Map<String, Object> getAllValues()
    {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("email", email);
        values.put("phonenumber", phonenumber);
        values.put("sex", sex);
        values.put("birthDate", birthDate);
        values.put("idType", idType);
        values.put("idNumber", idNumber);
        values.put("bloodType", bloodType);
        values.put("registeredResidence", registeredResidence);
        values.put("currentAddress", currentAddress);
        values.put("firstEducation", firstEducation);
        values.put("firstDegree", firstDegree);
        values.put("firstGraduationDate", firstGraduationDate);
        values.put("firstGraduationSchool", firstGraduationSchool);
        values.put("firstMajor", firstMajor);
        values.put("highestEducation", highestEducation);
        values.put("highestDegree", highestDegree);
        values.put("highestGraduationDate", highestGraduationDate);
        values.put("highestGraduationSchool", highestGraduationSchool);
        values.put("highestMajor", highestMajor);
        values.put("politicalStatus", politicalStatus);
        values.put("maritalStatus", maritalStatus);
        values.put("nationality", nationality);
        values.put("foreignNationalFlag", foreignNationalFlag);
        values.put("ethnicity", ethnicity);
        values.put("healthStatus", healthStatus);
        values.put("emergencyContact", emergencyContact);
        values.put("emergencyContactRelation", emergencyContactRelation);
        values.put("emergencyContactPhone", emergencyContactPhone);
        values.put("officePhone", officePhone);
        values.put("workLocation", workLocation);
        values.put("householdType", householdType);
        values.put("socialSecurityLocation", socialSecurityLocation);
        values.put("housingFundLocation", housingFundLocation);
        values.put("bankName", bankName);
        values.put("bankAccount", bankAccount);
        return values;
    }

    @Email(message = "邮箱格式不正确")
    @Size(max = 50, message = "邮箱长度不能超过50个字符")
    public String getEmail() { return email; }
    public void setEmail(String value) { email = value; providedValues.put("email", value); }
    @Size(max = 11, message = "手机号码长度不能超过11个字符")
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号码格式不正确")
    public String getPhonenumber() { return phonenumber; }
    public void setPhonenumber(String value) { phonenumber = value; providedValues.put("phonenumber", value); }
    @Size(max = 1, message = "性别代码长度不能超过1个字符")
    public String getSex() { return sex; }
    public void setSex(String value) { sex = value; providedValues.put("sex", value); }
    @JsonFormat(pattern = "yyyy-MM-dd")
    public Date getBirthDate() { return birthDate; }
    public void setBirthDate(Date value) { birthDate = value; providedValues.put("birthDate", value); }
    @Size(max = 64, message = "证件类型长度不能超过64个字符")
    public String getIdType() { return idType; }
    public void setIdType(String value) { idType = value; providedValues.put("idType", value); }
    @Size(max = 64, message = "证件号码长度不能超过64个字符")
    public String getIdNumber() { return idNumber; }
    public void setIdNumber(String value) { idNumber = value; providedValues.put("idNumber", value); }
    @Size(max = 16, message = "血型长度不能超过16个字符")
    public String getBloodType() { return bloodType; }
    public void setBloodType(String value) { bloodType = value; providedValues.put("bloodType", value); }
    @Size(max = 255, message = "户籍地址长度不能超过255个字符")
    public String getRegisteredResidence() { return registeredResidence; }
    public void setRegisteredResidence(String value) { registeredResidence = value; providedValues.put("registeredResidence", value); }
    @Size(max = 255, message = "现居住地址长度不能超过255个字符")
    public String getCurrentAddress() { return currentAddress; }
    public void setCurrentAddress(String value) { currentAddress = value; providedValues.put("currentAddress", value); }
    @Size(max = 64, message = "第一学历长度不能超过64个字符")
    public String getFirstEducation() { return firstEducation; }
    public void setFirstEducation(String value) { firstEducation = value; providedValues.put("firstEducation", value); }
    @Size(max = 64, message = "第一学位长度不能超过64个字符")
    public String getFirstDegree() { return firstDegree; }
    public void setFirstDegree(String value) { firstDegree = value; providedValues.put("firstDegree", value); }
    @JsonFormat(pattern = "yyyy-MM-dd")
    public Date getFirstGraduationDate() { return firstGraduationDate; }
    public void setFirstGraduationDate(Date value) { firstGraduationDate = value; providedValues.put("firstGraduationDate", value); }
    @Size(max = 100, message = "第一学历毕业学校长度不能超过100个字符")
    public String getFirstGraduationSchool() { return firstGraduationSchool; }
    public void setFirstGraduationSchool(String value) { firstGraduationSchool = value; providedValues.put("firstGraduationSchool", value); }
    @Size(max = 100, message = "第一学历专业长度不能超过100个字符")
    public String getFirstMajor() { return firstMajor; }
    public void setFirstMajor(String value) { firstMajor = value; providedValues.put("firstMajor", value); }
    @Size(max = 64, message = "最高学历长度不能超过64个字符")
    public String getHighestEducation() { return highestEducation; }
    public void setHighestEducation(String value) { highestEducation = value; providedValues.put("highestEducation", value); }
    @Size(max = 64, message = "最高学位长度不能超过64个字符")
    public String getHighestDegree() { return highestDegree; }
    public void setHighestDegree(String value) { highestDegree = value; providedValues.put("highestDegree", value); }
    @JsonFormat(pattern = "yyyy-MM-dd")
    public Date getHighestGraduationDate() { return highestGraduationDate; }
    public void setHighestGraduationDate(Date value) { highestGraduationDate = value; providedValues.put("highestGraduationDate", value); }
    @Size(max = 100, message = "最高学历毕业学校长度不能超过100个字符")
    public String getHighestGraduationSchool() { return highestGraduationSchool; }
    public void setHighestGraduationSchool(String value) { highestGraduationSchool = value; providedValues.put("highestGraduationSchool", value); }
    @Size(max = 100, message = "最高学历专业长度不能超过100个字符")
    public String getHighestMajor() { return highestMajor; }
    public void setHighestMajor(String value) { highestMajor = value; providedValues.put("highestMajor", value); }
    @Size(max = 64, message = "政治面貌长度不能超过64个字符")
    public String getPoliticalStatus() { return politicalStatus; }
    public void setPoliticalStatus(String value) { politicalStatus = value; providedValues.put("politicalStatus", value); }
    @Size(max = 32, message = "婚姻状况长度不能超过32个字符")
    public String getMaritalStatus() { return maritalStatus; }
    public void setMaritalStatus(String value) { maritalStatus = value; providedValues.put("maritalStatus", value); }
    @Size(max = 64, message = "国籍长度不能超过64个字符")
    public String getNationality() { return nationality; }
    public void setNationality(String value) { nationality = value; providedValues.put("nationality", value); }
    @Size(max = 8, message = "外籍标识长度不能超过8个字符")
    public String getForeignNationalFlag() { return foreignNationalFlag; }
    public void setForeignNationalFlag(String value) { foreignNationalFlag = value; providedValues.put("foreignNationalFlag", value); }
    @Size(max = 64, message = "民族长度不能超过64个字符")
    public String getEthnicity() { return ethnicity; }
    public void setEthnicity(String value) { ethnicity = value; providedValues.put("ethnicity", value); }
    @Size(max = 64, message = "健康状况长度不能超过64个字符")
    public String getHealthStatus() { return healthStatus; }
    public void setHealthStatus(String value) { healthStatus = value; providedValues.put("healthStatus", value); }
    @Size(max = 100, message = "紧急联系人长度不能超过100个字符")
    public String getEmergencyContact() { return emergencyContact; }
    public void setEmergencyContact(String value) { emergencyContact = value; providedValues.put("emergencyContact", value); }
    @Size(max = 64, message = "紧急联系人关系长度不能超过64个字符")
    public String getEmergencyContactRelation() { return emergencyContactRelation; }
    public void setEmergencyContactRelation(String value) { emergencyContactRelation = value; providedValues.put("emergencyContactRelation", value); }
    @Size(max = 32, message = "紧急联系人电话长度不能超过32个字符")
    public String getEmergencyContactPhone() { return emergencyContactPhone; }
    public void setEmergencyContactPhone(String value) { emergencyContactPhone = value; providedValues.put("emergencyContactPhone", value); }
    @Size(max = 32, message = "办公电话长度不能超过32个字符")
    public String getOfficePhone() { return officePhone; }
    public void setOfficePhone(String value) { officePhone = value; providedValues.put("officePhone", value); }
    @Size(max = 100, message = "工作地长度不能超过100个字符")
    public String getWorkLocation() { return workLocation; }
    public void setWorkLocation(String value) { workLocation = value; providedValues.put("workLocation", value); }
    @Size(max = 64, message = "户口性质长度不能超过64个字符")
    public String getHouseholdType() { return householdType; }
    public void setHouseholdType(String value) { householdType = value; providedValues.put("householdType", value); }
    @Size(max = 100, message = "社保缴纳地长度不能超过100个字符")
    public String getSocialSecurityLocation() { return socialSecurityLocation; }
    public void setSocialSecurityLocation(String value) { socialSecurityLocation = value; providedValues.put("socialSecurityLocation", value); }
    @Size(max = 100, message = "公积金缴纳地长度不能超过100个字符")
    public String getHousingFundLocation() { return housingFundLocation; }
    public void setHousingFundLocation(String value) { housingFundLocation = value; providedValues.put("housingFundLocation", value); }
    @Size(max = 100, message = "开户银行长度不能超过100个字符")
    public String getBankName() { return bankName; }
    public void setBankName(String value) { bankName = value; providedValues.put("bankName", value); }
    @Size(max = 64, message = "银行卡号长度不能超过64个字符")
    public String getBankAccount() { return bankAccount; }
    public void setBankAccount(String value) { bankAccount = value; providedValues.put("bankAccount", value); }
}
