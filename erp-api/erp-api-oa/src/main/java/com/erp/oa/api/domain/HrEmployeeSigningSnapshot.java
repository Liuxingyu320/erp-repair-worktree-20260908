package com.erp.oa.api.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 签约规则使用的显式员工快照。
 *
 * <p>代码字段用于稳定匹配，名称字段用于HR阅读和冻结展示。不得用任意JSON替代此契约。</p>
 */
public class HrEmployeeSigningSnapshot
{
    private Long employeeId;
    private String employeeNo;
    private String positionNo;
    private String employeeName;
    private String phone;
    private String idType;
    private String idNumber;
    private String currentAddress;
    private String employeeStatus;
    private String employeeCategory;
    private String accountStatus;
    private String offboardingType;
    private String leaveReason;
    private String salarySettlementStatus;
    private String assetHandoverStatus;
    private String nonCompeteDecision;
    private BigDecimal compensationAmount;
    private String compensationNote;

    private Long shopDeptId;
    private String shopDeptName;
    private Long deptId;
    private String deptName;

    private Long legalEntityId;
    private String legalEntityCode;
    private String legalEntityName;

    private Long postId;
    private String postCode;
    private String postName;
    private String jobGradeCode;
    private String jobGradeName;

    private Long directSupervisorId;
    private String directSupervisorName;
    private Long departmentSupervisorId;
    private String departmentSupervisorName;

    private String workLocation;
    private String workCityLevel;
    private String contractTypeCode;
    private String contractTermCode;
    private String socialTypeCode;
    private Integer renewalCount;

    private LocalDate entryDate;
    private LocalDate contractStartDate;
    private LocalDate contractEndDate;
    private LocalDate probationStartDate;
    private LocalDate probationEndDate;
    private LocalDate actualRegularizationDate;
    private LocalDate transferEffectiveDate;
    private LocalDate leaveDate;

    private BigDecimal baseSalary;
    private BigDecimal postSalary;
    private BigDecimal fieldAllowance;
    private BigDecimal performanceSalary;
    private BigDecimal salaryTotal;
    private String salaryVersion;

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public String getEmployeeNo() { return employeeNo; }
    public void setEmployeeNo(String employeeNo) { this.employeeNo = employeeNo; }
    public String getPositionNo() { return positionNo; }
    public void setPositionNo(String positionNo) { this.positionNo = positionNo; }
    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getIdType() { return idType; }
    public void setIdType(String idType) { this.idType = idType; }
    public String getIdNumber() { return idNumber; }
    public void setIdNumber(String idNumber) { this.idNumber = idNumber; }
    public String getCurrentAddress() { return currentAddress; }
    public void setCurrentAddress(String currentAddress) { this.currentAddress = currentAddress; }
    public String getEmployeeStatus() { return employeeStatus; }
    public void setEmployeeStatus(String employeeStatus) { this.employeeStatus = employeeStatus; }
    public String getEmployeeCategory() { return employeeCategory; }
    public void setEmployeeCategory(String employeeCategory) { this.employeeCategory = employeeCategory; }
    public String getAccountStatus() { return accountStatus; }
    public void setAccountStatus(String accountStatus) { this.accountStatus = accountStatus; }
    public String getOffboardingType() { return offboardingType; }
    public void setOffboardingType(String offboardingType) { this.offboardingType = offboardingType; }
    public String getLeaveReason() { return leaveReason; }
    public void setLeaveReason(String leaveReason) { this.leaveReason = leaveReason; }
    public String getSalarySettlementStatus() { return salarySettlementStatus; }
    public void setSalarySettlementStatus(String salarySettlementStatus)
    {
        this.salarySettlementStatus = salarySettlementStatus;
    }
    public String getAssetHandoverStatus() { return assetHandoverStatus; }
    public void setAssetHandoverStatus(String assetHandoverStatus)
    {
        this.assetHandoverStatus = assetHandoverStatus;
    }
    public String getNonCompeteDecision() { return nonCompeteDecision; }
    public void setNonCompeteDecision(String nonCompeteDecision) { this.nonCompeteDecision = nonCompeteDecision; }
    public BigDecimal getCompensationAmount() { return compensationAmount; }
    public void setCompensationAmount(BigDecimal compensationAmount) { this.compensationAmount = compensationAmount; }
    public String getCompensationNote() { return compensationNote; }
    public void setCompensationNote(String compensationNote) { this.compensationNote = compensationNote; }

    public Long getShopDeptId() { return shopDeptId; }
    public void setShopDeptId(Long shopDeptId) { this.shopDeptId = shopDeptId; }
    public String getShopDeptName() { return shopDeptName; }
    public void setShopDeptName(String shopDeptName) { this.shopDeptName = shopDeptName; }
    public Long getDeptId() { return deptId; }
    public void setDeptId(Long deptId) { this.deptId = deptId; }
    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }

    public Long getLegalEntityId() { return legalEntityId; }
    public void setLegalEntityId(Long legalEntityId) { this.legalEntityId = legalEntityId; }
    public String getLegalEntityCode() { return legalEntityCode; }
    public void setLegalEntityCode(String legalEntityCode) { this.legalEntityCode = legalEntityCode; }
    public String getLegalEntityName() { return legalEntityName; }
    public void setLegalEntityName(String legalEntityName) { this.legalEntityName = legalEntityName; }

    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }
    public String getPostCode() { return postCode; }
    public void setPostCode(String postCode) { this.postCode = postCode; }
    public String getPostName() { return postName; }
    public void setPostName(String postName) { this.postName = postName; }
    public String getJobGradeCode() { return jobGradeCode; }
    public void setJobGradeCode(String jobGradeCode) { this.jobGradeCode = jobGradeCode; }
    public String getJobGradeName() { return jobGradeName; }
    public void setJobGradeName(String jobGradeName) { this.jobGradeName = jobGradeName; }

    public Long getDirectSupervisorId() { return directSupervisorId; }
    public void setDirectSupervisorId(Long directSupervisorId) { this.directSupervisorId = directSupervisorId; }
    public String getDirectSupervisorName() { return directSupervisorName; }
    public void setDirectSupervisorName(String directSupervisorName) { this.directSupervisorName = directSupervisorName; }
    public Long getDepartmentSupervisorId() { return departmentSupervisorId; }
    public void setDepartmentSupervisorId(Long departmentSupervisorId)
    {
        this.departmentSupervisorId = departmentSupervisorId;
    }
    public String getDepartmentSupervisorName() { return departmentSupervisorName; }
    public void setDepartmentSupervisorName(String departmentSupervisorName)
    {
        this.departmentSupervisorName = departmentSupervisorName;
    }

    public String getWorkLocation() { return workLocation; }
    public void setWorkLocation(String workLocation) { this.workLocation = workLocation; }
    public String getWorkCityLevel() { return workCityLevel; }
    public void setWorkCityLevel(String workCityLevel) { this.workCityLevel = workCityLevel; }
    public String getContractTypeCode() { return contractTypeCode; }
    public void setContractTypeCode(String contractTypeCode) { this.contractTypeCode = contractTypeCode; }
    public String getContractTermCode() { return contractTermCode; }
    public void setContractTermCode(String contractTermCode) { this.contractTermCode = contractTermCode; }
    public String getSocialTypeCode() { return socialTypeCode; }
    public void setSocialTypeCode(String socialTypeCode) { this.socialTypeCode = socialTypeCode; }
    public Integer getRenewalCount() { return renewalCount; }
    public void setRenewalCount(Integer renewalCount) { this.renewalCount = renewalCount; }

    public LocalDate getEntryDate() { return entryDate; }
    public void setEntryDate(LocalDate entryDate) { this.entryDate = entryDate; }
    public LocalDate getContractStartDate() { return contractStartDate; }
    public void setContractStartDate(LocalDate contractStartDate) { this.contractStartDate = contractStartDate; }
    public LocalDate getContractEndDate() { return contractEndDate; }
    public void setContractEndDate(LocalDate contractEndDate) { this.contractEndDate = contractEndDate; }
    public LocalDate getProbationStartDate() { return probationStartDate; }
    public void setProbationStartDate(LocalDate probationStartDate) { this.probationStartDate = probationStartDate; }
    public LocalDate getProbationEndDate() { return probationEndDate; }
    public void setProbationEndDate(LocalDate probationEndDate) { this.probationEndDate = probationEndDate; }
    public LocalDate getActualRegularizationDate() { return actualRegularizationDate; }
    public void setActualRegularizationDate(LocalDate actualRegularizationDate)
    {
        this.actualRegularizationDate = actualRegularizationDate;
    }
    public LocalDate getTransferEffectiveDate() { return transferEffectiveDate; }
    public void setTransferEffectiveDate(LocalDate transferEffectiveDate)
    {
        this.transferEffectiveDate = transferEffectiveDate;
    }
    public LocalDate getLeaveDate() { return leaveDate; }
    public void setLeaveDate(LocalDate leaveDate) { this.leaveDate = leaveDate; }

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
}
