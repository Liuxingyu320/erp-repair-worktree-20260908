package com.erp.system.api.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 签约候选员工
 */
public class SignCandidateUser
{
    private Long userId;

    private String userName;

    private String nickName;

    private String phonenumber;

    private String accountStatus;

    private Long deptId;

    private String deptName;

    private String postNames;

    private Long postId;

    private String postCode;

    private String postName;

    private String employeeNo;

    private String employeeStatus;

    private String idType;

    private String idNumber;

    private String currentAddress;

    private String studentStatus;

    private String schoolName;

    private String retirementStatus;

    private String incomeStartYearMonth;

    /** Optimistic-lock hash for the five System-owned signing profile facts. */
    private String profileFactsHash;

    private String jobGrade;

    private String contractType;

    private String contractTerm;

    private String socialType;

    private String entryDate;

    private String contractStartDate;

    private String contractEndDate;

    private String legalEntity;

    private Long legalEntityId;

    private String legalEntityCode;

    private String probationStartDate;

    private String probationEndDate;

    private BigDecimal baseSalary;

    private BigDecimal postSalary;

    private BigDecimal fieldAllowance;

    private BigDecimal performanceSalary;

    private BigDecimal salaryTotal;

    private String salaryVersion;

    private List<SignReadinessIssue> readinessIssues = new ArrayList<>();

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getUserName()
    {
        return userName;
    }

    public void setUserName(String userName)
    {
        this.userName = userName;
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

    public String getAccountStatus()
    {
        return accountStatus;
    }

    public void setAccountStatus(String accountStatus)
    {
        this.accountStatus = accountStatus;
    }

    public Long getDeptId()
    {
        return deptId;
    }

    public void setDeptId(Long deptId)
    {
        this.deptId = deptId;
    }

    public String getDeptName()
    {
        return deptName;
    }

    public void setDeptName(String deptName)
    {
        this.deptName = deptName;
    }

    public String getPostNames()
    {
        return postNames;
    }

    public void setPostNames(String postNames)
    {
        this.postNames = postNames;
    }

    public Long getPostId()
    {
        return postId;
    }

    public void setPostId(Long postId)
    {
        this.postId = postId;
    }

    public String getPostCode()
    {
        return postCode;
    }

    public void setPostCode(String postCode)
    {
        this.postCode = postCode;
    }

    public String getPostName()
    {
        return postName;
    }

    public void setPostName(String postName)
    {
        this.postName = postName;
    }

    public String getEmployeeNo()
    {
        return employeeNo;
    }

    public void setEmployeeNo(String employeeNo)
    {
        this.employeeNo = employeeNo;
    }

    public String getEmployeeStatus()
    {
        return employeeStatus;
    }

    public void setEmployeeStatus(String employeeStatus)
    {
        this.employeeStatus = employeeStatus;
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

    public String getCurrentAddress()
    {
        return currentAddress;
    }

    public void setCurrentAddress(String currentAddress)
    {
        this.currentAddress = currentAddress;
    }

    public String getStudentStatus()
    {
        return studentStatus;
    }

    public void setStudentStatus(String studentStatus)
    {
        this.studentStatus = studentStatus;
    }

    public String getSchoolName()
    {
        return schoolName;
    }

    public void setSchoolName(String schoolName)
    {
        this.schoolName = schoolName;
    }

    public String getRetirementStatus()
    {
        return retirementStatus;
    }

    public void setRetirementStatus(String retirementStatus)
    {
        this.retirementStatus = retirementStatus;
    }

    public String getIncomeStartYearMonth()
    {
        return incomeStartYearMonth;
    }

    public void setIncomeStartYearMonth(String incomeStartYearMonth)
    {
        this.incomeStartYearMonth = incomeStartYearMonth;
    }

    public String getProfileFactsHash()
    {
        return profileFactsHash;
    }

    public void setProfileFactsHash(String profileFactsHash)
    {
        this.profileFactsHash = profileFactsHash;
    }

    public String getJobGrade()
    {
        return jobGrade;
    }

    public void setJobGrade(String jobGrade)
    {
        this.jobGrade = jobGrade;
    }

    public String getContractType()
    {
        return contractType;
    }

    public void setContractType(String contractType)
    {
        this.contractType = contractType;
    }

    public String getContractTerm()
    {
        return contractTerm;
    }

    public void setContractTerm(String contractTerm)
    {
        this.contractTerm = contractTerm;
    }

    public String getSocialType()
    {
        return socialType;
    }

    public void setSocialType(String socialType)
    {
        this.socialType = socialType;
    }

    public String getEntryDate()
    {
        return entryDate;
    }

    public void setEntryDate(String entryDate)
    {
        this.entryDate = entryDate;
    }

    public String getContractStartDate()
    {
        return contractStartDate;
    }

    public void setContractStartDate(String contractStartDate)
    {
        this.contractStartDate = contractStartDate;
    }

    public String getContractEndDate()
    {
        return contractEndDate;
    }

    public void setContractEndDate(String contractEndDate)
    {
        this.contractEndDate = contractEndDate;
    }

    public String getLegalEntity()
    {
        return legalEntity;
    }

    public void setLegalEntity(String legalEntity)
    {
        this.legalEntity = legalEntity;
    }

    public Long getLegalEntityId()
    {
        return legalEntityId;
    }

    public void setLegalEntityId(Long legalEntityId)
    {
        this.legalEntityId = legalEntityId;
    }

    public String getLegalEntityCode()
    {
        return legalEntityCode;
    }

    public void setLegalEntityCode(String legalEntityCode)
    {
        this.legalEntityCode = legalEntityCode;
    }

    public String getProbationStartDate()
    {
        return probationStartDate;
    }

    public void setProbationStartDate(String probationStartDate)
    {
        this.probationStartDate = probationStartDate;
    }

    public String getProbationEndDate()
    {
        return probationEndDate;
    }

    public void setProbationEndDate(String probationEndDate)
    {
        this.probationEndDate = probationEndDate;
    }

    public BigDecimal getBaseSalary()
    {
        return baseSalary;
    }

    public void setBaseSalary(BigDecimal baseSalary)
    {
        this.baseSalary = baseSalary;
    }

    public BigDecimal getPostSalary()
    {
        return postSalary;
    }

    public void setPostSalary(BigDecimal postSalary)
    {
        this.postSalary = postSalary;
    }

    public BigDecimal getFieldAllowance()
    {
        return fieldAllowance;
    }

    public void setFieldAllowance(BigDecimal fieldAllowance)
    {
        this.fieldAllowance = fieldAllowance;
    }

    public BigDecimal getPerformanceSalary()
    {
        return performanceSalary;
    }

    public void setPerformanceSalary(BigDecimal performanceSalary)
    {
        this.performanceSalary = performanceSalary;
    }

    public BigDecimal getSalaryTotal()
    {
        return salaryTotal;
    }

    public void setSalaryTotal(BigDecimal salaryTotal)
    {
        this.salaryTotal = salaryTotal;
    }

    public String getSalaryVersion()
    {
        return salaryVersion;
    }

    public void setSalaryVersion(String salaryVersion)
    {
        this.salaryVersion = salaryVersion;
    }

    public List<SignReadinessIssue> getReadinessIssues()
    {
        return readinessIssues;
    }

    public void setReadinessIssues(List<SignReadinessIssue> readinessIssues)
    {
        this.readinessIssues = readinessIssues == null ? new ArrayList<>() : readinessIssues;
    }

}
