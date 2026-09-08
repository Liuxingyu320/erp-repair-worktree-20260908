package com.erp.system.domain.dto;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonAnySetter;

/** Fields a signed-in user may maintain for their own account. */
public class SysSelfProfileUpdateRequest
{
    @NotBlank(message = "用户昵称不能为空")
    @Size(max = 30, message = "用户昵称长度不能超过30个字符")
    private String nickName;
    @Email(message = "邮箱格式不正确")
    @Size(max = 50, message = "邮箱长度不能超过50个字符")
    private String email;
    @Size(max = 11, message = "手机号码长度不能超过11个字符")
    private String phonenumber;
    private String sex;
    @Pattern(regexp = "(?s).*\\S.*", message = "现住地不能为空")
    @Size(max = 255, message = "现住地长度不能超过255个字符")
    private String currentAddress;
    @Size(max = 64, message = "紧急联系人长度不能超过64个字符")
    private String emergencyContact;
    @Size(max = 32, message = "紧急联系人关系长度不能超过32个字符")
    private String emergencyContactRelation;
    @Size(max = 32, message = "紧急联系人电话长度不能超过32个字符")
    private String emergencyContactPhone;
    @Size(max = 32, message = "婚姻状况长度不能超过32个字符")
    private String maritalStatus;
    @Size(max = 64, message = "民族长度不能超过64个字符")
    private String ethnicity;
    @Size(max = 64, message = "政治面貌长度不能超过64个字符")
    private String politicalStatus;
    @Size(max = 64, message = "第一学历长度不能超过64个字符")
    private String firstEducation;
    @Size(max = 64, message = "第一学位长度不能超过64个字符")
    private String firstDegree;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date firstGraduationDate;
    @Size(max = 128, message = "第一学历毕业学校长度不能超过128个字符")
    private String firstGraduationSchool;
    @Size(max = 128, message = "第一学历专业长度不能超过128个字符")
    private String firstMajor;
    @Size(max = 64, message = "最高学历长度不能超过64个字符")
    private String highestEducation;
    @Size(max = 64, message = "最高学位长度不能超过64个字符")
    private String highestDegree;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date highestGraduationDate;
    @Size(max = 128, message = "最高学历毕业学校长度不能超过128个字符")
    private String highestGraduationSchool;
    @Size(max = 128, message = "最高学历专业长度不能超过128个字符")
    private String highestMajor;
    @Size(max = 128, message = "开户银行长度不能超过128个字符")
    private String bankName;
    @Pattern(regexp = "^[0-9]{8,32}$", message = "银行卡号应为8至32位数字")
    private String bankAccount;

    @JsonAnySetter
    public void rejectUnknownField(String field, Object ignored)
    {
        throw new IllegalArgumentException("本人资料接口不接受字段: " + field);
    }

    public String getNickName() { return nickName; }
    public void setNickName(String nickName) { this.nickName = nickName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhonenumber() { return phonenumber; }
    public void setPhonenumber(String phonenumber) { this.phonenumber = phonenumber; }
    public String getSex() { return sex; }
    public void setSex(String sex) { this.sex = sex; }
    public String getCurrentAddress() { return currentAddress; }
    public void setCurrentAddress(String currentAddress) { this.currentAddress = currentAddress; }
    public String getEmergencyContact() { return emergencyContact; }
    public void setEmergencyContact(String value) { emergencyContact = value; }
    public String getEmergencyContactRelation() { return emergencyContactRelation; }
    public void setEmergencyContactRelation(String value) { emergencyContactRelation = value; }
    public String getEmergencyContactPhone() { return emergencyContactPhone; }
    public void setEmergencyContactPhone(String value) { emergencyContactPhone = value; }
    public String getMaritalStatus() { return maritalStatus; }
    public void setMaritalStatus(String value) { maritalStatus = value; }
    public String getEthnicity() { return ethnicity; }
    public void setEthnicity(String value) { ethnicity = value; }
    public String getPoliticalStatus() { return politicalStatus; }
    public void setPoliticalStatus(String value) { politicalStatus = value; }
    public String getFirstEducation() { return firstEducation; }
    public void setFirstEducation(String value) { firstEducation = value; }
    public String getFirstDegree() { return firstDegree; }
    public void setFirstDegree(String value) { firstDegree = value; }
    public Date getFirstGraduationDate() { return firstGraduationDate; }
    public void setFirstGraduationDate(Date value) { firstGraduationDate = value; }
    public String getFirstGraduationSchool() { return firstGraduationSchool; }
    public void setFirstGraduationSchool(String value) { firstGraduationSchool = value; }
    public String getFirstMajor() { return firstMajor; }
    public void setFirstMajor(String value) { firstMajor = value; }
    public String getHighestEducation() { return highestEducation; }
    public void setHighestEducation(String value) { highestEducation = value; }
    public String getHighestDegree() { return highestDegree; }
    public void setHighestDegree(String value) { highestDegree = value; }
    public Date getHighestGraduationDate() { return highestGraduationDate; }
    public void setHighestGraduationDate(Date value) { highestGraduationDate = value; }
    public String getHighestGraduationSchool() { return highestGraduationSchool; }
    public void setHighestGraduationSchool(String value) { highestGraduationSchool = value; }
    public String getHighestMajor() { return highestMajor; }
    public void setHighestMajor(String value) { highestMajor = value; }
    public String getBankName() { return bankName; }
    public void setBankName(String value) { bankName = value; }
    public String getBankAccount() { return bankAccount; }
    public void setBankAccount(String value) { bankAccount = value; }

    public Map<String, Object> profileValues()
    {
        Map<String, Object> values = new LinkedHashMap<>();
        put(values, "currentAddress", currentAddress);
        put(values, "emergencyContact", emergencyContact);
        put(values, "emergencyContactRelation", emergencyContactRelation);
        put(values, "emergencyContactPhone", emergencyContactPhone);
        put(values, "maritalStatus", maritalStatus);
        put(values, "ethnicity", ethnicity);
        put(values, "politicalStatus", politicalStatus);
        put(values, "firstEducation", firstEducation);
        put(values, "firstDegree", firstDegree);
        put(values, "firstGraduationDate", firstGraduationDate);
        put(values, "firstGraduationSchool", firstGraduationSchool);
        put(values, "firstMajor", firstMajor);
        put(values, "highestEducation", highestEducation);
        put(values, "highestDegree", highestDegree);
        put(values, "highestGraduationDate", highestGraduationDate);
        put(values, "highestGraduationSchool", highestGraduationSchool);
        put(values, "highestMajor", highestMajor);
        put(values, "bankName", bankName);
        if (bankAccount != null && !bankAccount.isBlank()) values.put("bankAccount", bankAccount.trim());
        return values;
    }

    private static void put(Map<String, Object> values, String key, Object value)
    {
        if (value != null) values.put(key, value instanceof String ? ((String) value).trim() : value);
    }
}
