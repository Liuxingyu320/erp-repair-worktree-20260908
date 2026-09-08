package com.erp.system.domain.vo;

import java.io.Serializable;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 当前员工可自行维护的入职必填资料。
 */
public class SysProfileCompletionRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    private String nickName;

    private String phonenumber;

    private String sex;

    @JsonFormat(pattern = "yyyy-MM-dd")
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

    private String bankName;

    private String bankAccount;

    @JsonAnySetter
    public void rejectUnknownField(String field, Object ignored)
    {
        throw new IllegalArgumentException("本人资料补全接口不接受字段: " + field);
    }

    public String getNickName()
    {
        return nickName;
    }

    public void setNickName(String nickName)
    {
        this.nickName = nickName;
    }

    public String getPhonenumber()
    {
        return phonenumber;
    }

    public void setPhonenumber(String phonenumber)
    {
        this.phonenumber = phonenumber;
    }

    public String getSex()
    {
        return sex;
    }

    public void setSex(String sex)
    {
        this.sex = sex;
    }

    public Date getBirthDate()
    {
        return birthDate;
    }

    public void setBirthDate(Date birthDate)
    {
        this.birthDate = birthDate;
    }

    public String getIdType()
    {
        return idType;
    }

    public void setIdType(String idType)
    {
        this.idType = idType;
    }

    public String getIdNumber()
    {
        return idNumber;
    }

    public void setIdNumber(String idNumber)
    {
        this.idNumber = idNumber;
    }

    public String getRegisteredResidence()
    {
        return registeredResidence;
    }

    public void setRegisteredResidence(String registeredResidence)
    {
        this.registeredResidence = registeredResidence;
    }

    public String getCurrentAddress()
    {
        return currentAddress;
    }

    public void setCurrentAddress(String currentAddress)
    {
        this.currentAddress = currentAddress;
    }

    public String getMaritalStatus()
    {
        return maritalStatus;
    }

    public void setMaritalStatus(String maritalStatus)
    {
        this.maritalStatus = maritalStatus;
    }

    public String getEthnicity()
    {
        return ethnicity;
    }

    public void setEthnicity(String ethnicity)
    {
        this.ethnicity = ethnicity;
    }

    public String getEmergencyContact()
    {
        return emergencyContact;
    }

    public void setEmergencyContact(String emergencyContact)
    {
        this.emergencyContact = emergencyContact;
    }

    public String getEmergencyContactRelation()
    {
        return emergencyContactRelation;
    }

    public void setEmergencyContactRelation(String emergencyContactRelation)
    {
        this.emergencyContactRelation = emergencyContactRelation;
    }

    public String getEmergencyContactPhone()
    {
        return emergencyContactPhone;
    }

    public void setEmergencyContactPhone(String emergencyContactPhone)
    {
        this.emergencyContactPhone = emergencyContactPhone;
    }

    public String getBankName()
    {
        return bankName;
    }

    public void setBankName(String bankName)
    {
        this.bankName = bankName;
    }

    public String getBankAccount()
    {
        return bankAccount;
    }

    public void setBankAccount(String bankAccount)
    {
        this.bankAccount = bankAccount;
    }
}
