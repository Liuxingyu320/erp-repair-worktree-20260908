package com.erp.system.api.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.erp.common.core.web.domain.BaseEntity;

/**
 * 用户员工档案对象 sys_user_profile
 *
 * @author erp
 */
public class SysUserProfile extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 档案ID */
    private Long profileId;

    /** 用户ID */
    private Long userId;

    /** 档案所属组织ID（授权与待办的稳定键） */
    private Long deptId;

    /** 员工姓名 */
    private String employeeName;

    /** 手机号码 */
    private String phoneNumber;

    /** 性别 */
    private String sex;

    /** 工号 */
    private String employeeNo;

    /** 当前岗位工号（岗位编码-固定员工号） */
    private String positionNo;

    /** 所属公司 */
    private String companyName;

    /** 1级部门 */
    private String deptLevel1Name;

    /** 2级部门 */
    private String deptLevel2Name;

    /** 3级部门 */
    private String deptLevel3Name;

    /** 4级门店 */
    private String storeName;

    /** 职位 */
    private String positionNames;

    /** 职级 */
    private String jobGrade;

    /** 部门主管 */
    private String departmentSupervisor;

    /** 直属主管 */
    private String directSupervisor;

    /** 直属主管用户ID */
    private Long directSupervisorUserId;

    /** 员工状态 */
    private String employeeStatus;

    /** 人员类别 */
    private String employeeCategory;

    /** 出生日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date birthDate;

    /** 证件类型 */
    private String idType;

    /** 证件号码 */
    private String idNumber;

    /** 血型 */
    private String bloodType;

    /** 户口所在地 */
    private String registeredResidence;

    /** 现居住地址 */
    private String currentAddress;

    /** 当前在校事实：STUDENT/NON_STUDENT */
    private String studentStatus;

    /** 当前在读学校（仅在校时保留） */
    private String schoolName;

    /** 退休客观事实：RETIRED/NOT_RETIRED */
    private String retirementStatus;

    /** 个人劳动收入成为主要生活来源起始月（yyyy-MM） */
    private String incomeStartYearMonth;

    /** 第一学历 */
    private String firstEducation;

    /** 第一学位 */
    private String firstDegree;

    /** 毕业时间 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date firstGraduationDate;

    /** 第一学历毕业学校 */
    private String firstGraduationSchool;

    /** 第一学历所学专业 */
    private String firstMajor;

    /** 最高学历 */
    private String highestEducation;

    /** 最高学位 */
    private String highestDegree;

    /** 最高学历毕业时间 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date highestGraduationDate;

    /** 最高学历毕业学校 */
    private String highestGraduationSchool;

    /** 最高学历所学专业 */
    private String highestMajor;

    /** 政治面貌 */
    private String politicalStatus;

    /** 婚姻状况 */
    private String maritalStatus;

    /** 国籍 */
    private String nationality;

    /** 是否外籍 */
    private String foreignNationalFlag;

    /** 民族 */
    private String ethnicity;

    /** 健康状况 */
    private String healthStatus;

    /** 紧急联系人 */
    private String emergencyContact;

    /** 与紧急联系人关系 */
    private String emergencyContactRelation;

    /** 紧急联系人电话 */
    private String emergencyContactPhone;

    /** 招聘渠道 */
    private String recruitmentChannel;

    /** 办公电话 */
    private String officePhone;

    /** 参加工作时间 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date workStartDate;

    /** 工龄 */
    private String workYears;

    /** 入职时间 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date entryDate;

    /** 试用期 */
    private String probationPeriod;

    /** 试用期开始日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date probationStartDate;

    /** 试用期结束日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date probationEndDate;

    /** 计划转正日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date plannedRegularizationDate;

    /** 实际转正日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date actualRegularizationDate;

    /** 司龄 */
    private String companyYears;

    /** 本岗位任职日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date currentPositionStartDate;

    /** 现合同起始日 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date contractStartDate;

    /** 现合同到期日 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date contractEndDate;

    /** 合同类型 */
    private String contractType;

    /** 合同期限 */
    private String contractTerm;

    /** 续签次数 */
    private Integer renewalCount;

    /** 工作所在地 */
    private String workLocation;

    /** 工作所在城市级别 */
    private String workCityLevel;

    /** 考勤方式 */
    private String attendanceMethod;

    /** 户口性质 */
    private String householdType;

    /** 社保类型 */
    private String socialType;

    /** 社保缴纳地 */
    private String socialSecurityLocation;

    /** 公积金缴纳地 */
    private String housingFundLocation;

    /** 离职时间 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date leaveDate;

    /** 开户银行 */
    private String bankName;

    /** 银行卡号 */
    private String bankAccount;

    /** 法人单位 */
    private String legalEntity;

    /** 稳定法律主体ID */
    private Long legalEntityId;

    /** 稳定法律主体代码 */
    private String legalEntityCode;

    /** 基本工资，仅供服务端签约规则使用 */
    @JsonIgnore
    private BigDecimal baseSalary;

    /** 岗位工资，仅供服务端签约规则使用 */
    @JsonIgnore
    private BigDecimal postSalary;

    /** 外勤补贴，仅供服务端签约规则使用 */
    @JsonIgnore
    private BigDecimal fieldAllowance;

    /** 绩效工资，仅供服务端签约规则使用 */
    @JsonIgnore
    private BigDecimal performanceSalary;

    /** 薪资合计，仅供服务端签约规则使用 */
    @JsonIgnore
    private BigDecimal salaryTotal;

    /** 薪资版本，仅供服务端签约规则使用 */
    @JsonIgnore
    private String salaryVersion;

    /** 社保类型（HR 档案字段） */
    private String socialSecurityType;

    /** 入职流程状态 */
    private String onboardingStatus;

    /** 预计入职日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date expectedEntryDate;

    /** 入职确认人 */
    private String onboardingConfirmedBy;

    /** 入职确认时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date onboardingConfirmedTime;

    /** 入职取消原因 */
    private String onboardingCancelReason;

    /** 档案来源 */
    private String profileSource;

    /** 最近档案更新人 */
    private String lastProfileUpdateBy;

    /** 最近档案更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastProfileUpdateTime;

    /** 身份证人像面状态 */
    private String idCardPortraitStatus;

    /** 身份证国徽面状态 */
    private String idCardEmblemStatus;

    /** 学历证书状态 */
    private String educationCertificateStatus;

    /** 学位证书状态 */
    private String degreeCertificateStatus;

    /** 离职证明状态 */
    private String resignationCertificateStatus;

    /** 员工照片状态 */
    private String employeePhotoStatus;

    /** 实时派生字段警告（不持久化） */
    private List<String> derivedWarnings = new ArrayList<>();

    public Long getProfileId()
    {
        return profileId;
    }

    public void setProfileId(Long profileId)
    {
        this.profileId = profileId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public Long getDeptId()
    {
        return deptId;
    }

    public void setDeptId(Long deptId)
    {
        this.deptId = deptId;
    }

    public String getEmployeeName()
    {
        return employeeName;
    }

    public void setEmployeeName(String employeeName)
    {
        this.employeeName = employeeName;
    }

    public String getPhoneNumber()
    {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber)
    {
        this.phoneNumber = phoneNumber;
    }

    public String getSex()
    {
        return sex;
    }

    public void setSex(String sex)
    {
        this.sex = sex;
    }

    public String getEmployeeNo()
    {
        return employeeNo;
    }

    public void setEmployeeNo(String employeeNo)
    {
        this.employeeNo = employeeNo;
    }

    public String getPositionNo()
    {
        return positionNo;
    }

    public void setPositionNo(String positionNo)
    {
        this.positionNo = positionNo;
    }

    public String getCompanyName()
    {
        return companyName;
    }

    public void setCompanyName(String companyName)
    {
        this.companyName = companyName;
    }

    public String getDeptLevel1Name()
    {
        return deptLevel1Name;
    }

    public void setDeptLevel1Name(String deptLevel1Name)
    {
        this.deptLevel1Name = deptLevel1Name;
    }

    public String getDeptLevel2Name()
    {
        return deptLevel2Name;
    }

    public void setDeptLevel2Name(String deptLevel2Name)
    {
        this.deptLevel2Name = deptLevel2Name;
    }

    public String getDeptLevel3Name()
    {
        return deptLevel3Name;
    }

    public void setDeptLevel3Name(String deptLevel3Name)
    {
        this.deptLevel3Name = deptLevel3Name;
    }

    public String getStoreName()
    {
        return storeName;
    }

    public void setStoreName(String storeName)
    {
        this.storeName = storeName;
    }

    public String getPositionNames()
    {
        return positionNames;
    }

    public void setPositionNames(String positionNames)
    {
        this.positionNames = positionNames;
    }

    public String getJobGrade()
    {
        return jobGrade;
    }

    public void setJobGrade(String jobGrade)
    {
        this.jobGrade = jobGrade;
    }

    public String getDepartmentSupervisor()
    {
        return departmentSupervisor;
    }

    public void setDepartmentSupervisor(String departmentSupervisor)
    {
        this.departmentSupervisor = departmentSupervisor;
    }

    public String getDirectSupervisor()
    {
        return directSupervisor;
    }

    public void setDirectSupervisor(String directSupervisor)
    {
        this.directSupervisor = directSupervisor;
    }

    public Long getDirectSupervisorUserId()
    {
        return directSupervisorUserId;
    }

    public void setDirectSupervisorUserId(Long directSupervisorUserId)
    {
        this.directSupervisorUserId = directSupervisorUserId;
    }

    public String getEmployeeStatus()
    {
        return employeeStatus;
    }

    public void setEmployeeStatus(String employeeStatus)
    {
        this.employeeStatus = employeeStatus;
    }

    public String getEmployeeCategory()
    {
        return employeeCategory;
    }

    public void setEmployeeCategory(String employeeCategory)
    {
        this.employeeCategory = employeeCategory;
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

    public String getBloodType()
    {
        return bloodType;
    }

    public void setBloodType(String bloodType)
    {
        this.bloodType = bloodType;
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

    public String getFirstEducation()
    {
        return firstEducation;
    }

    public void setFirstEducation(String firstEducation)
    {
        this.firstEducation = firstEducation;
    }

    public String getFirstDegree()
    {
        return firstDegree;
    }

    public void setFirstDegree(String firstDegree)
    {
        this.firstDegree = firstDegree;
    }

    public Date getFirstGraduationDate()
    {
        return firstGraduationDate;
    }

    public void setFirstGraduationDate(Date firstGraduationDate)
    {
        this.firstGraduationDate = firstGraduationDate;
    }

    public String getFirstGraduationSchool()
    {
        return firstGraduationSchool;
    }

    public void setFirstGraduationSchool(String firstGraduationSchool)
    {
        this.firstGraduationSchool = firstGraduationSchool;
    }

    public String getFirstMajor()
    {
        return firstMajor;
    }

    public void setFirstMajor(String firstMajor)
    {
        this.firstMajor = firstMajor;
    }

    public String getHighestEducation()
    {
        return highestEducation;
    }

    public void setHighestEducation(String highestEducation)
    {
        this.highestEducation = highestEducation;
    }

    public String getHighestDegree()
    {
        return highestDegree;
    }

    public void setHighestDegree(String highestDegree)
    {
        this.highestDegree = highestDegree;
    }

    public Date getHighestGraduationDate()
    {
        return highestGraduationDate;
    }

    public void setHighestGraduationDate(Date highestGraduationDate)
    {
        this.highestGraduationDate = highestGraduationDate;
    }

    public String getHighestGraduationSchool()
    {
        return highestGraduationSchool;
    }

    public void setHighestGraduationSchool(String highestGraduationSchool)
    {
        this.highestGraduationSchool = highestGraduationSchool;
    }

    public String getHighestMajor()
    {
        return highestMajor;
    }

    public void setHighestMajor(String highestMajor)
    {
        this.highestMajor = highestMajor;
    }

    public String getPoliticalStatus()
    {
        return politicalStatus;
    }

    public void setPoliticalStatus(String politicalStatus)
    {
        this.politicalStatus = politicalStatus;
    }

    public String getMaritalStatus()
    {
        return maritalStatus;
    }

    public void setMaritalStatus(String maritalStatus)
    {
        this.maritalStatus = maritalStatus;
    }

    public String getNationality()
    {
        return nationality;
    }

    public void setNationality(String nationality)
    {
        this.nationality = nationality;
    }

    public String getForeignNationalFlag()
    {
        return foreignNationalFlag;
    }

    public void setForeignNationalFlag(String foreignNationalFlag)
    {
        this.foreignNationalFlag = foreignNationalFlag;
    }

    public String getEthnicity()
    {
        return ethnicity;
    }

    public void setEthnicity(String ethnicity)
    {
        this.ethnicity = ethnicity;
    }

    public String getHealthStatus()
    {
        return healthStatus;
    }

    public void setHealthStatus(String healthStatus)
    {
        this.healthStatus = healthStatus;
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

    public String getRecruitmentChannel()
    {
        return recruitmentChannel;
    }

    public void setRecruitmentChannel(String recruitmentChannel)
    {
        this.recruitmentChannel = recruitmentChannel;
    }

    public String getOfficePhone()
    {
        return officePhone;
    }

    public void setOfficePhone(String officePhone)
    {
        this.officePhone = officePhone;
    }

    public Date getWorkStartDate()
    {
        return workStartDate;
    }

    public void setWorkStartDate(Date workStartDate)
    {
        this.workStartDate = workStartDate;
    }

    public String getWorkYears()
    {
        return workYears;
    }

    public void setWorkYears(String workYears)
    {
        this.workYears = workYears;
    }

    public Date getEntryDate()
    {
        return entryDate;
    }

    public void setEntryDate(Date entryDate)
    {
        this.entryDate = entryDate;
    }

    public String getProbationPeriod()
    {
        return probationPeriod;
    }

    public void setProbationPeriod(String probationPeriod)
    {
        this.probationPeriod = probationPeriod;
    }

    public Date getProbationStartDate() { return probationStartDate; }
    public void setProbationStartDate(Date probationStartDate) { this.probationStartDate = probationStartDate; }
    public Date getProbationEndDate() { return probationEndDate; }
    public void setProbationEndDate(Date probationEndDate) { this.probationEndDate = probationEndDate; }

    public Date getPlannedRegularizationDate()
    {
        return plannedRegularizationDate;
    }

    public void setPlannedRegularizationDate(Date plannedRegularizationDate)
    {
        this.plannedRegularizationDate = plannedRegularizationDate;
    }

    public Date getActualRegularizationDate()
    {
        return actualRegularizationDate;
    }

    public void setActualRegularizationDate(Date actualRegularizationDate)
    {
        this.actualRegularizationDate = actualRegularizationDate;
    }

    public String getCompanyYears()
    {
        return companyYears;
    }

    public void setCompanyYears(String companyYears)
    {
        this.companyYears = companyYears;
    }

    public Date getCurrentPositionStartDate()
    {
        return currentPositionStartDate;
    }

    public void setCurrentPositionStartDate(Date currentPositionStartDate)
    {
        this.currentPositionStartDate = currentPositionStartDate;
    }

    public Date getContractStartDate()
    {
        return contractStartDate;
    }

    public void setContractStartDate(Date contractStartDate)
    {
        this.contractStartDate = contractStartDate;
    }

    public Date getContractEndDate()
    {
        return contractEndDate;
    }

    public void setContractEndDate(Date contractEndDate)
    {
        this.contractEndDate = contractEndDate;
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

    public Integer getRenewalCount()
    {
        return renewalCount;
    }

    public void setRenewalCount(Integer renewalCount)
    {
        this.renewalCount = renewalCount;
    }

    public String getWorkLocation()
    {
        return workLocation;
    }

    public void setWorkLocation(String workLocation)
    {
        this.workLocation = workLocation;
    }

    public String getWorkCityLevel()
    {
        return workCityLevel;
    }

    public void setWorkCityLevel(String workCityLevel)
    {
        this.workCityLevel = workCityLevel;
    }

    public String getAttendanceMethod()
    {
        return attendanceMethod;
    }

    public void setAttendanceMethod(String attendanceMethod)
    {
        this.attendanceMethod = attendanceMethod;
    }

    public String getHouseholdType()
    {
        return householdType;
    }

    public void setHouseholdType(String householdType)
    {
        this.householdType = householdType;
    }

    public String getSocialType()
    {
        return socialType;
    }

    public void setSocialType(String socialType)
    {
        this.socialType = socialType;
    }

    public String getSocialSecurityLocation()
    {
        return socialSecurityLocation;
    }

    public void setSocialSecurityLocation(String socialSecurityLocation)
    {
        this.socialSecurityLocation = socialSecurityLocation;
    }

    public String getHousingFundLocation()
    {
        return housingFundLocation;
    }

    public void setHousingFundLocation(String housingFundLocation)
    {
        this.housingFundLocation = housingFundLocation;
    }

    public Date getLeaveDate()
    {
        return leaveDate;
    }

    public void setLeaveDate(Date leaveDate)
    {
        this.leaveDate = leaveDate;
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

    public String getLegalEntity()
    {
        return legalEntity;
    }

    public void setLegalEntity(String legalEntity)
    {
        this.legalEntity = legalEntity;
    }

    public Long getLegalEntityId() { return legalEntityId; }
    public void setLegalEntityId(Long legalEntityId) { this.legalEntityId = legalEntityId; }
    public String getLegalEntityCode() { return legalEntityCode; }
    public void setLegalEntityCode(String legalEntityCode) { this.legalEntityCode = legalEntityCode; }
    public BigDecimal getBaseSalary() { return baseSalary; }
    public void setBaseSalary(BigDecimal baseSalary) { this.baseSalary = baseSalary; }
    public BigDecimal getPostSalary() { return postSalary; }
    public void setPostSalary(BigDecimal postSalary) { this.postSalary = postSalary; }
    public BigDecimal getFieldAllowance() { return fieldAllowance; }
    public void setFieldAllowance(BigDecimal fieldAllowance) { this.fieldAllowance = fieldAllowance; }
    public BigDecimal getPerformanceSalary() { return performanceSalary; }
    public void setPerformanceSalary(BigDecimal performanceSalary) { this.performanceSalary = performanceSalary; }
    public BigDecimal getSalaryTotal() { return salaryTotal; }
    public void setSalaryTotal(BigDecimal salaryTotal) { this.salaryTotal = salaryTotal; }
    public String getSalaryVersion() { return salaryVersion; }
    public void setSalaryVersion(String salaryVersion) { this.salaryVersion = salaryVersion; }

    public String getSocialSecurityType()
    {
        return socialSecurityType;
    }

    public void setSocialSecurityType(String socialSecurityType)
    {
        this.socialSecurityType = socialSecurityType;
    }

    public String getOnboardingStatus()
    {
        return onboardingStatus;
    }

    public void setOnboardingStatus(String onboardingStatus)
    {
        this.onboardingStatus = onboardingStatus;
    }

    public Date getExpectedEntryDate()
    {
        return expectedEntryDate;
    }

    public void setExpectedEntryDate(Date expectedEntryDate)
    {
        this.expectedEntryDate = expectedEntryDate;
    }

    public String getOnboardingConfirmedBy()
    {
        return onboardingConfirmedBy;
    }

    public void setOnboardingConfirmedBy(String onboardingConfirmedBy)
    {
        this.onboardingConfirmedBy = onboardingConfirmedBy;
    }

    public Date getOnboardingConfirmedTime()
    {
        return onboardingConfirmedTime;
    }

    public void setOnboardingConfirmedTime(Date onboardingConfirmedTime)
    {
        this.onboardingConfirmedTime = onboardingConfirmedTime;
    }

    public String getOnboardingCancelReason()
    {
        return onboardingCancelReason;
    }

    public void setOnboardingCancelReason(String onboardingCancelReason)
    {
        this.onboardingCancelReason = onboardingCancelReason;
    }

    public String getProfileSource()
    {
        return profileSource;
    }

    public void setProfileSource(String profileSource)
    {
        this.profileSource = profileSource;
    }

    public String getLastProfileUpdateBy()
    {
        return lastProfileUpdateBy;
    }

    public void setLastProfileUpdateBy(String lastProfileUpdateBy)
    {
        this.lastProfileUpdateBy = lastProfileUpdateBy;
    }

    public Date getLastProfileUpdateTime()
    {
        return lastProfileUpdateTime;
    }

    public void setLastProfileUpdateTime(Date lastProfileUpdateTime)
    {
        this.lastProfileUpdateTime = lastProfileUpdateTime;
    }

    public String getIdCardPortraitStatus()
    {
        return idCardPortraitStatus;
    }

    public void setIdCardPortraitStatus(String idCardPortraitStatus)
    {
        this.idCardPortraitStatus = idCardPortraitStatus;
    }

    public String getIdCardEmblemStatus()
    {
        return idCardEmblemStatus;
    }

    public void setIdCardEmblemStatus(String idCardEmblemStatus)
    {
        this.idCardEmblemStatus = idCardEmblemStatus;
    }

    public String getEducationCertificateStatus()
    {
        return educationCertificateStatus;
    }

    public void setEducationCertificateStatus(String educationCertificateStatus)
    {
        this.educationCertificateStatus = educationCertificateStatus;
    }

    public String getDegreeCertificateStatus()
    {
        return degreeCertificateStatus;
    }

    public void setDegreeCertificateStatus(String degreeCertificateStatus)
    {
        this.degreeCertificateStatus = degreeCertificateStatus;
    }

    public String getResignationCertificateStatus()
    {
        return resignationCertificateStatus;
    }

    public void setResignationCertificateStatus(String resignationCertificateStatus)
    {
        this.resignationCertificateStatus = resignationCertificateStatus;
    }

    public String getEmployeePhotoStatus()
    {
        return employeePhotoStatus;
    }

    public void setEmployeePhotoStatus(String employeePhotoStatus)
    {
        this.employeePhotoStatus = employeePhotoStatus;
    }

    public List<String> getDerivedWarnings()
    {
        return derivedWarnings;
    }

    public void setDerivedWarnings(List<String> derivedWarnings)
    {
        this.derivedWarnings = derivedWarnings == null ? new ArrayList<>() : derivedWarnings;
    }
}
