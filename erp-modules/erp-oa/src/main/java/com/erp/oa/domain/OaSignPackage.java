package com.erp.oa.domain;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.erp.common.core.annotation.Excel;
import com.erp.common.core.web.domain.BaseEntity;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public class OaSignPackage extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long packageId;

    @Excel(name = "签约包编号")
    private String packageNo;

    @NotNull(message = "员工账号不能为空")
    private Long employeeId;

    private Long deptIdSnapshot;
    private String deptNameSnapshot;
    private Long shopDeptId;
    private String shopDeptName;
    private Long legalEntityIdSnapshot;
    private String legalEntityCodeSnapshot;
    private String legalEntityNameSnapshot;
    private Long legalEntitySourceDeptId;
    private String legalEntityResolveMode;
    private String legalEntityCreditCodeSnapshot;
    private String legalEntityAddressSnapshot;
    private String legalRepresentativeSnapshot;
    private String legalEntityPhoneSnapshot;
    private String legalEntityOverrideReason;
    private Long sealIdSnapshot;
    private String sealNameSnapshot;
    private String sealImageUrlSnapshot;
    private String sealImageHashSnapshot;
    private Long sourcePlanId;
    private String sourcePlanName;
    /** Excel recommendation only; never treated as a selected legal entity. */
    private String recommendedCompanySnapshot;
    private String recommendedLegalRepresentativeSnapshot;
    private String recommendedRegisteredAddressSnapshot;

    @NotBlank(message = "员工姓名不能为空")
    @Size(max = 64, message = "员工姓名长度不能超过64个字符")
    @Excel(name = "员工姓名")
    private String employeeNameSnapshot;

    @NotBlank(message = "手机号不能为空")
    @Size(max = 32, message = "手机号长度不能超过32个字符")
    private String employeePhoneSnapshot;

    @NotBlank(message = "身份证号不能为空")
    @Size(max = 32, message = "身份证号长度不能超过32个字符")
    private String employeeIdCardSnapshot;

    @Size(max = 255, message = "住址长度不能超过255个字符")
    private String employeeAddressSnapshot;

    @NotBlank(message = "签约场景不能为空")
    @Excel(name = "签约场景")
    private String scenario;

    @Excel(name = "用工类型")
    private String employmentType;

    @Size(max = 32, message = "合同期限类型长度不能超过32个字符")
    private String contractTermCodeSnapshot;

    @Excel(name = "社保口径")
    private String socialType;

    @Excel(name = "劳务人员类型")
    private String servicePersonType;

    @Excel(name = "保险类型")
    private String insuranceType;

    @Excel(name = "岗位")
    private String postNameSnapshot;

    @Excel(name = "岗位等级")
    private String postLevelSnapshot;

    private String salaryVersion;
    private String entryDate;
    private String contractStartDate;
    private String contractEndDate;
    private String previousContractEndDate;
    private String previousEmploymentType;
    private Long previousLegalEntityIdSnapshot;
    private Integer previousRenewalCount;
    private Integer renewalCount;
    private String probationStartDate;
    private String probationEndDate;
    private String actualRegularizationDate;
    private String transferEffectiveDate;
    private Boolean historicalSupplement;
    private String beforeDeptNameSnapshot;
    private String beforePostNameSnapshot;
    private String workStartDate;
    private String studentStatusSnapshot;
    private String retirementStatusSnapshot;
    private String incomeStartYearMonth;
    private String workEndDate;
    private String leaveDate;
    private String leaveReason;
    private String offboardingType;
    private String salarySettlementStatus;
    private String assetHandoverStatus;
    private String nonCompeteDecision;
    private BigDecimal compensationAmount;
    private String compensationNote;
    private BigDecimal baseSalary;
    private BigDecimal postSalary;
    private BigDecimal fieldAllowance;
    private BigDecimal performanceSalary;
    private BigDecimal salaryTotal;

    @Excel(name = "状态", readConverterExp = "draft=草稿,pending_sign=待签署,part_viewed=已查看,signed=已签署,voided=已撤回,failed=失败")
    private String status;

    private String voidReason;
    private Date sentTime;
    private Date viewedTime;
    private Date signedTime;
    private Date initialSignedTime;
    /** Frozen workflow order: COMPANY_FIRST or SIGNATURE_FIRST. */
    private String signingSequence;
    /** Task-scoped signature sample; never copied into the employee master profile. */
    private String signatureSampleFileUrl;
    private String signatureSampleHash;
    private Date signatureSampleTime;
    private Date companyFrozenTime;
    private String documentVersion;
    private String finalDocumentVersion;
    private String finalDocumentRootHash;
    private Date finalGeneratedTime;
    private Date finalConfirmedTime;
    private String finalConfirmationStatus;
    /** Aggregate hash of immutable post-confirmation archive files. */
    private String finalArchiveRootHash;
    private Date finalEvidenceGeneratedTime;
    private Date signDeadline;
    private String deadlinePolicySource;
    private Integer deadlineDaysSnapshot;
    private Date terminalTime;
    private String terminalReasonCode;
    private String terminalReasonDetail;
    private String resolutionStatus;
    private Long resolvedBy;
    private Date resolvedTime;
    private String resolutionReasonCode;
    private String resolutionReasonDetail;
    private Long reissueOfPackageId;
    private Long reissuedToPackageId;
    private Long version = 0L;
    private Long taskId;
    private String confirmStatus;
    private Long planVersionId;
    private Integer documentCount;
    private List<OaSignPackageDocument> documents;
    private List<OaSignEvent> events;

    public Long getPackageId() { return packageId; }
    public void setPackageId(Long packageId) { this.packageId = packageId; }

    public String getPackageNo() { return packageNo; }
    public void setPackageNo(String packageNo) { this.packageNo = packageNo; }

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }

    public Long getDeptIdSnapshot() { return deptIdSnapshot; }
    public void setDeptIdSnapshot(Long deptIdSnapshot) { this.deptIdSnapshot = deptIdSnapshot; }

    public String getDeptNameSnapshot() { return deptNameSnapshot; }
    public void setDeptNameSnapshot(String deptNameSnapshot) { this.deptNameSnapshot = deptNameSnapshot; }

    public Long getShopDeptId() { return shopDeptId; }
    public void setShopDeptId(Long shopDeptId) { this.shopDeptId = shopDeptId; }

    public String getShopDeptName() { return shopDeptName; }
    public void setShopDeptName(String shopDeptName) { this.shopDeptName = shopDeptName; }

    public Long getLegalEntityIdSnapshot() { return legalEntityIdSnapshot; }
    public void setLegalEntityIdSnapshot(Long legalEntityIdSnapshot) { this.legalEntityIdSnapshot = legalEntityIdSnapshot; }

    public String getLegalEntityCodeSnapshot() { return legalEntityCodeSnapshot; }
    public void setLegalEntityCodeSnapshot(String legalEntityCodeSnapshot) { this.legalEntityCodeSnapshot = legalEntityCodeSnapshot; }

    public String getLegalEntityNameSnapshot() { return legalEntityNameSnapshot; }
    public void setLegalEntityNameSnapshot(String legalEntityNameSnapshot) { this.legalEntityNameSnapshot = legalEntityNameSnapshot; }

    public Long getLegalEntitySourceDeptId() { return legalEntitySourceDeptId; }
    public void setLegalEntitySourceDeptId(Long legalEntitySourceDeptId) { this.legalEntitySourceDeptId = legalEntitySourceDeptId; }
    public String getLegalEntityResolveMode() { return legalEntityResolveMode; }
    public void setLegalEntityResolveMode(String legalEntityResolveMode) { this.legalEntityResolveMode = legalEntityResolveMode; }
    public String getLegalEntityCreditCodeSnapshot() { return legalEntityCreditCodeSnapshot; }
    public void setLegalEntityCreditCodeSnapshot(String legalEntityCreditCodeSnapshot) { this.legalEntityCreditCodeSnapshot = legalEntityCreditCodeSnapshot; }
    public String getLegalEntityAddressSnapshot() { return legalEntityAddressSnapshot; }
    public void setLegalEntityAddressSnapshot(String legalEntityAddressSnapshot) { this.legalEntityAddressSnapshot = legalEntityAddressSnapshot; }
    public String getLegalRepresentativeSnapshot() { return legalRepresentativeSnapshot; }
    public void setLegalRepresentativeSnapshot(String legalRepresentativeSnapshot) { this.legalRepresentativeSnapshot = legalRepresentativeSnapshot; }
    public String getLegalEntityPhoneSnapshot() { return legalEntityPhoneSnapshot; }
    public void setLegalEntityPhoneSnapshot(String legalEntityPhoneSnapshot) { this.legalEntityPhoneSnapshot = legalEntityPhoneSnapshot; }
    public String getLegalEntityOverrideReason() { return legalEntityOverrideReason; }
    public void setLegalEntityOverrideReason(String legalEntityOverrideReason) { this.legalEntityOverrideReason = legalEntityOverrideReason; }
    public Long getSealIdSnapshot() { return sealIdSnapshot; }
    public void setSealIdSnapshot(Long sealIdSnapshot) { this.sealIdSnapshot = sealIdSnapshot; }
    public String getSealNameSnapshot() { return sealNameSnapshot; }
    public void setSealNameSnapshot(String sealNameSnapshot) { this.sealNameSnapshot = sealNameSnapshot; }
    public String getSealImageUrlSnapshot() { return sealImageUrlSnapshot; }
    public void setSealImageUrlSnapshot(String sealImageUrlSnapshot) { this.sealImageUrlSnapshot = sealImageUrlSnapshot; }
    public String getSealImageHashSnapshot() { return sealImageHashSnapshot; }
    public void setSealImageHashSnapshot(String sealImageHashSnapshot) { this.sealImageHashSnapshot = sealImageHashSnapshot; }

    public Long getSourcePlanId() { return sourcePlanId; }
    public void setSourcePlanId(Long sourcePlanId) { this.sourcePlanId = sourcePlanId; }

    public String getSourcePlanName() { return sourcePlanName; }
    public void setSourcePlanName(String sourcePlanName) { this.sourcePlanName = sourcePlanName; }

    public String getRecommendedCompanySnapshot() { return recommendedCompanySnapshot; }
    public void setRecommendedCompanySnapshot(String value) { this.recommendedCompanySnapshot = value; }

    public String getRecommendedLegalRepresentativeSnapshot() { return recommendedLegalRepresentativeSnapshot; }
    public void setRecommendedLegalRepresentativeSnapshot(String value) { this.recommendedLegalRepresentativeSnapshot = value; }

    public String getRecommendedRegisteredAddressSnapshot() { return recommendedRegisteredAddressSnapshot; }
    public void setRecommendedRegisteredAddressSnapshot(String value) { this.recommendedRegisteredAddressSnapshot = value; }

    public String getEmployeeNameSnapshot() { return employeeNameSnapshot; }
    public void setEmployeeNameSnapshot(String employeeNameSnapshot) { this.employeeNameSnapshot = employeeNameSnapshot; }

    public String getEmployeePhoneSnapshot() { return employeePhoneSnapshot; }
    public void setEmployeePhoneSnapshot(String employeePhoneSnapshot) { this.employeePhoneSnapshot = employeePhoneSnapshot; }

    public String getEmployeeIdCardSnapshot() { return employeeIdCardSnapshot; }
    public void setEmployeeIdCardSnapshot(String employeeIdCardSnapshot) { this.employeeIdCardSnapshot = employeeIdCardSnapshot; }

    public String getEmployeeAddressSnapshot() { return employeeAddressSnapshot; }
    public void setEmployeeAddressSnapshot(String employeeAddressSnapshot) { this.employeeAddressSnapshot = employeeAddressSnapshot; }

    public String getScenario() { return scenario; }
    public void setScenario(String scenario) { this.scenario = scenario; }

    public String getEmploymentType() { return employmentType; }
    public void setEmploymentType(String employmentType) { this.employmentType = employmentType; }

    public String getContractTermCodeSnapshot() { return contractTermCodeSnapshot; }
    public void setContractTermCodeSnapshot(String contractTermCodeSnapshot)
    {
        this.contractTermCodeSnapshot = contractTermCodeSnapshot;
    }

    public String getSocialType() { return socialType; }
    public void setSocialType(String socialType) { this.socialType = socialType; }

    public String getServicePersonType() { return servicePersonType; }
    public void setServicePersonType(String servicePersonType) { this.servicePersonType = servicePersonType; }

    public String getInsuranceType() { return insuranceType; }
    public void setInsuranceType(String insuranceType) { this.insuranceType = insuranceType; }

    public String getPostNameSnapshot() { return postNameSnapshot; }
    public void setPostNameSnapshot(String postNameSnapshot) { this.postNameSnapshot = postNameSnapshot; }

    public String getPostLevelSnapshot() { return postLevelSnapshot; }
    public void setPostLevelSnapshot(String postLevelSnapshot) { this.postLevelSnapshot = postLevelSnapshot; }

    public String getSalaryVersion() { return salaryVersion; }
    public void setSalaryVersion(String salaryVersion) { this.salaryVersion = salaryVersion; }

    public String getEntryDate() { return entryDate; }
    public void setEntryDate(String entryDate) { this.entryDate = entryDate; }

    public String getContractStartDate() { return contractStartDate; }
    public void setContractStartDate(String contractStartDate) { this.contractStartDate = contractStartDate; }

    public String getContractEndDate() { return contractEndDate; }
    public void setContractEndDate(String contractEndDate) { this.contractEndDate = contractEndDate; }

    public String getPreviousContractEndDate() { return previousContractEndDate; }
    public void setPreviousContractEndDate(String previousContractEndDate) { this.previousContractEndDate = previousContractEndDate; }

    public String getPreviousEmploymentType() { return previousEmploymentType; }
    public void setPreviousEmploymentType(String previousEmploymentType) { this.previousEmploymentType = previousEmploymentType; }

    public Long getPreviousLegalEntityIdSnapshot() { return previousLegalEntityIdSnapshot; }
    public void setPreviousLegalEntityIdSnapshot(Long previousLegalEntityIdSnapshot) { this.previousLegalEntityIdSnapshot = previousLegalEntityIdSnapshot; }

    public Integer getPreviousRenewalCount() { return previousRenewalCount; }
    public void setPreviousRenewalCount(Integer previousRenewalCount) { this.previousRenewalCount = previousRenewalCount; }

    public Integer getRenewalCount() { return renewalCount; }
    public void setRenewalCount(Integer renewalCount) { this.renewalCount = renewalCount; }

    public String getProbationStartDate() { return probationStartDate; }
    public void setProbationStartDate(String probationStartDate) { this.probationStartDate = probationStartDate; }

    public String getProbationEndDate() { return probationEndDate; }
    public void setProbationEndDate(String probationEndDate) { this.probationEndDate = probationEndDate; }

    public String getActualRegularizationDate() { return actualRegularizationDate; }
    public void setActualRegularizationDate(String actualRegularizationDate) { this.actualRegularizationDate = actualRegularizationDate; }

    public String getTransferEffectiveDate() { return transferEffectiveDate; }
    public void setTransferEffectiveDate(String transferEffectiveDate) { this.transferEffectiveDate = transferEffectiveDate; }

    public Boolean getHistoricalSupplement() { return historicalSupplement; }
    public void setHistoricalSupplement(Boolean historicalSupplement) { this.historicalSupplement = historicalSupplement; }

    public String getBeforeDeptNameSnapshot() { return beforeDeptNameSnapshot; }
    public void setBeforeDeptNameSnapshot(String beforeDeptNameSnapshot) { this.beforeDeptNameSnapshot = beforeDeptNameSnapshot; }

    public String getBeforePostNameSnapshot() { return beforePostNameSnapshot; }
    public void setBeforePostNameSnapshot(String beforePostNameSnapshot) { this.beforePostNameSnapshot = beforePostNameSnapshot; }

    public String getWorkStartDate() { return workStartDate; }
    public void setWorkStartDate(String workStartDate) { this.workStartDate = workStartDate; }

    public String getStudentStatusSnapshot() { return studentStatusSnapshot; }
    public void setStudentStatusSnapshot(String studentStatusSnapshot)
    {
        this.studentStatusSnapshot = studentStatusSnapshot;
    }

    public String getRetirementStatusSnapshot() { return retirementStatusSnapshot; }
    public void setRetirementStatusSnapshot(String retirementStatusSnapshot)
    {
        this.retirementStatusSnapshot = retirementStatusSnapshot;
    }

    public String getIncomeStartYearMonth() { return incomeStartYearMonth; }
    public void setIncomeStartYearMonth(String incomeStartYearMonth)
    {
        this.incomeStartYearMonth = incomeStartYearMonth;
    }

    public String getWorkEndDate() { return workEndDate; }
    public void setWorkEndDate(String workEndDate) { this.workEndDate = workEndDate; }

    public String getLeaveDate() { return leaveDate; }
    public void setLeaveDate(String leaveDate) { this.leaveDate = leaveDate; }

    public String getLeaveReason() { return leaveReason; }
    public void setLeaveReason(String leaveReason) { this.leaveReason = leaveReason; }

    public String getOffboardingType() { return offboardingType; }
    public void setOffboardingType(String offboardingType) { this.offboardingType = offboardingType; }

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
    public void setNonCompeteDecision(String nonCompeteDecision)
    {
        this.nonCompeteDecision = nonCompeteDecision;
    }

    public BigDecimal getCompensationAmount() { return compensationAmount; }
    public void setCompensationAmount(BigDecimal compensationAmount)
    {
        this.compensationAmount = compensationAmount;
    }

    public String getCompensationNote() { return compensationNote; }
    public void setCompensationNote(String compensationNote) { this.compensationNote = compensationNote; }

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

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getVoidReason() { return voidReason; }
    public void setVoidReason(String voidReason) { this.voidReason = voidReason; }

    public Date getSentTime() { return sentTime; }
    public void setSentTime(Date sentTime) { this.sentTime = sentTime; }

    public Date getViewedTime() { return viewedTime; }
    public void setViewedTime(Date viewedTime) { this.viewedTime = viewedTime; }

    public Date getSignedTime() { return signedTime; }
    public void setSignedTime(Date signedTime) { this.signedTime = signedTime; }

    public Date getInitialSignedTime() { return initialSignedTime; }
    public void setInitialSignedTime(Date initialSignedTime) { this.initialSignedTime = initialSignedTime; }

    public String getSigningSequence() { return signingSequence; }
    public void setSigningSequence(String value) { signingSequence = value; }
    public String getSignatureSampleFileUrl() { return signatureSampleFileUrl; }
    public void setSignatureSampleFileUrl(String value) { signatureSampleFileUrl = value; }
    public String getSignatureSampleHash() { return signatureSampleHash; }
    public void setSignatureSampleHash(String value) { signatureSampleHash = value; }
    public Date getSignatureSampleTime() { return signatureSampleTime; }
    public void setSignatureSampleTime(Date value) { signatureSampleTime = value; }
    public Date getCompanyFrozenTime() { return companyFrozenTime; }
    public void setCompanyFrozenTime(Date value) { companyFrozenTime = value; }

    public String getDocumentVersion() { return documentVersion; }
    public void setDocumentVersion(String documentVersion) { this.documentVersion = documentVersion; }

    public String getFinalDocumentVersion() { return finalDocumentVersion; }
    public void setFinalDocumentVersion(String finalDocumentVersion) { this.finalDocumentVersion = finalDocumentVersion; }
    public String getFinalDocumentRootHash() { return finalDocumentRootHash; }
    public void setFinalDocumentRootHash(String finalDocumentRootHash) { this.finalDocumentRootHash = finalDocumentRootHash; }
    public Date getFinalGeneratedTime() { return finalGeneratedTime; }
    public void setFinalGeneratedTime(Date finalGeneratedTime) { this.finalGeneratedTime = finalGeneratedTime; }
    public Date getFinalConfirmedTime() { return finalConfirmedTime; }
    public void setFinalConfirmedTime(Date finalConfirmedTime) { this.finalConfirmedTime = finalConfirmedTime; }
    public String getFinalConfirmationStatus() { return finalConfirmationStatus; }
    public void setFinalConfirmationStatus(String finalConfirmationStatus) { this.finalConfirmationStatus = finalConfirmationStatus; }
    public String getFinalArchiveRootHash() { return finalArchiveRootHash; }
    public void setFinalArchiveRootHash(String value) { finalArchiveRootHash = value; }
    public Date getFinalEvidenceGeneratedTime() { return finalEvidenceGeneratedTime; }
    public void setFinalEvidenceGeneratedTime(Date value) { finalEvidenceGeneratedTime = value; }

    public Date getSignDeadline() { return signDeadline; }
    public void setSignDeadline(Date signDeadline) { this.signDeadline = signDeadline; }

    public String getDeadlinePolicySource() { return deadlinePolicySource; }
    public void setDeadlinePolicySource(String deadlinePolicySource) { this.deadlinePolicySource = deadlinePolicySource; }

    public Integer getDeadlineDaysSnapshot() { return deadlineDaysSnapshot; }
    public void setDeadlineDaysSnapshot(Integer deadlineDaysSnapshot) { this.deadlineDaysSnapshot = deadlineDaysSnapshot; }

    public Date getTerminalTime() { return terminalTime; }
    public void setTerminalTime(Date terminalTime) { this.terminalTime = terminalTime; }

    public String getTerminalReasonCode() { return terminalReasonCode; }
    public void setTerminalReasonCode(String terminalReasonCode) { this.terminalReasonCode = terminalReasonCode; }

    public String getTerminalReasonDetail() { return terminalReasonDetail; }
    public void setTerminalReasonDetail(String terminalReasonDetail) { this.terminalReasonDetail = terminalReasonDetail; }

    public String getResolutionStatus() { return resolutionStatus; }
    public void setResolutionStatus(String resolutionStatus) { this.resolutionStatus = resolutionStatus; }

    public Long getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(Long resolvedBy) { this.resolvedBy = resolvedBy; }

    public Date getResolvedTime() { return resolvedTime; }
    public void setResolvedTime(Date resolvedTime) { this.resolvedTime = resolvedTime; }

    public String getResolutionReasonCode() { return resolutionReasonCode; }
    public void setResolutionReasonCode(String resolutionReasonCode) { this.resolutionReasonCode = resolutionReasonCode; }

    public String getResolutionReasonDetail() { return resolutionReasonDetail; }
    public void setResolutionReasonDetail(String resolutionReasonDetail) { this.resolutionReasonDetail = resolutionReasonDetail; }

    public Long getReissueOfPackageId() { return reissueOfPackageId; }
    public void setReissueOfPackageId(Long reissueOfPackageId) { this.reissueOfPackageId = reissueOfPackageId; }

    public Long getReissuedToPackageId() { return reissuedToPackageId; }
    public void setReissuedToPackageId(Long reissuedToPackageId) { this.reissuedToPackageId = reissuedToPackageId; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }

    public String getConfirmStatus() { return confirmStatus; }
    public void setConfirmStatus(String confirmStatus) { this.confirmStatus = confirmStatus; }

    public Long getPlanVersionId() { return planVersionId; }
    public void setPlanVersionId(Long planVersionId) { this.planVersionId = planVersionId; }

    public Integer getDocumentCount() { return documentCount; }
    public void setDocumentCount(Integer documentCount) { this.documentCount = documentCount; }

    public List<OaSignPackageDocument> getDocuments() { return documents; }
    public void setDocuments(List<OaSignPackageDocument> documents) { this.documents = documents; }

    public List<OaSignEvent> getEvents() { return events; }
    public void setEvents(List<OaSignEvent> events) { this.events = events; }
}
