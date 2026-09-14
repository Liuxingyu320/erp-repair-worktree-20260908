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
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public class OaLaborContract extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long contractId;

    @NotNull(message = "合同模板不能为空")
    private Long templateId;

    private String templateName;

    @NotNull(message = "员工账号不能为空")
    private Long employeeId;

    private Long employeeDeptId;

    @Excel(name = "员工部门")
    private String employeeDeptName;

    private Long shopDeptId;

    @Excel(name = "所属店铺")
    private String shopDeptName;

    private Long schemeId;

    private Long salaryItemId;

    @Excel(name = "合同编号")
    private String contractNo;

    @Excel(name = "合同标题")
    private String contractTitle;

    @NotBlank(message = "员工姓名不能为空")
    @Size(max = 64, message = "员工姓名长度不能超过64个字符")
    @Excel(name = "员工姓名")
    private String employeeName;

    @NotBlank(message = "身份证号不能为空")
    @Size(max = 32, message = "身份证号长度不能超过32个字符")
    @Excel(name = "身份证号")
    private String employeeIdCard;

    @NotBlank(message = "手机号不能为空")
    @Size(max = 32, message = "手机号长度不能超过32个字符")
    @Excel(name = "手机号")
    private String employeePhone;

    @NotBlank(message = "岗位不能为空")
    @Size(max = 64, message = "岗位长度不能超过64个字符")
    @Excel(name = "岗位")
    private String postName;

    @NotBlank(message = "社保口径不能为空")
    @Size(max = 20, message = "社保口径长度不能超过20个字符")
    @Excel(name = "社保口径")
    private String socialType;

    @NotBlank(message = "合同开始日期不能为空")
    @Excel(name = "合同开始日期")
    private String contractStartDate;

    @NotBlank(message = "合同结束日期不能为空")
    @Excel(name = "合同结束日期")
    private String contractEndDate;

    @Excel(name = "试用期开始日期")
    private String probationStartDate;

    @Excel(name = "试用期结束日期")
    private String probationEndDate;

    private boolean probationStartDateSpecified;
    private boolean probationEndDateSpecified;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private boolean identityManuallyVerified;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Size(max = 200, message = "身份核对说明不能超过200个字符")
    private String identityVerificationNote;

    @JsonIgnore
    public boolean isProbationStartDateSpecified() { return probationStartDateSpecified; }
    @JsonIgnore
    public boolean isProbationEndDateSpecified() { return probationEndDateSpecified; }
    public boolean isIdentityManuallyVerified() { return identityManuallyVerified; }
    public void setIdentityManuallyVerified(boolean value) { identityManuallyVerified = value; }
    public String getIdentityVerificationNote() { return identityVerificationNote; }
    public void setIdentityVerificationNote(String value) { identityVerificationNote = value; }


    @Excel(name = "基本工资")
    private BigDecimal baseSalary;

    @Excel(name = "管理津贴")
    private BigDecimal managementAllowance;

    @Excel(name = "加班费")
    private BigDecimal overtimePay;

    @Excel(name = "奖励津贴")
    private BigDecimal rewardAllowance;

    @Excel(name = "全勤奖")
    private BigDecimal fullAttendanceBonus;

    @Excel(name = "社保补贴")
    private BigDecimal socialSubsidy;

    @Excel(name = "通勤补贴")
    private BigDecimal commuteSubsidy;

    @Excel(name = "工资合计")
    private BigDecimal totalSalary;

    private String accommodationText;

    private String commuteText;

    @Excel(name = "状态", readConverterExp = "draft=草稿,pending_sign=待员工签,signed=已签,voided=作废,expired=过期")
    private String status;

    private String previewFileUrl;

    private String archiveFileUrl;

    private String pdfFileUrl;

    private String signatureFileUrl;

    private String contractFileHash;

    private String documentVersion;

    private String previewFileHash;

    private String archiveFileHash;

    private String certificateFileUrl;

    private String certificateFileHash;

    private String templateFileHash;

    private String sealImageHash;

    private String signerIp;

    private String signerUserAgent;

    private Date sentTime;

    private Date signedTime;

    private Date voidedTime;

    private String signProvider;

    private String providerContractId;

    private String providerSignUrl;

    private String providerStatus;

    private String providerCallbackPayload;

    private String caCertNo;

    private List<OaLaborContractEvent> events;

    public Long getContractId() { return contractId; }
    public void setContractId(Long contractId) { this.contractId = contractId; }

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }

    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }

    public Long getEmployeeDeptId() { return employeeDeptId; }
    public void setEmployeeDeptId(Long employeeDeptId) { this.employeeDeptId = employeeDeptId; }

    public String getEmployeeDeptName() { return employeeDeptName; }
    public void setEmployeeDeptName(String employeeDeptName) { this.employeeDeptName = employeeDeptName; }

    public Long getShopDeptId() { return shopDeptId; }
    public void setShopDeptId(Long shopDeptId) { this.shopDeptId = shopDeptId; }

    public String getShopDeptName() { return shopDeptName; }
    public void setShopDeptName(String shopDeptName) { this.shopDeptName = shopDeptName; }

    public Long getSchemeId() { return schemeId; }
    public void setSchemeId(Long schemeId) { this.schemeId = schemeId; }

    public Long getSalaryItemId() { return salaryItemId; }
    public void setSalaryItemId(Long salaryItemId) { this.salaryItemId = salaryItemId; }

    public String getContractNo() { return contractNo; }
    public void setContractNo(String contractNo) { this.contractNo = contractNo; }

    public String getContractTitle() { return contractTitle; }
    public void setContractTitle(String contractTitle) { this.contractTitle = contractTitle; }

    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

    public String getEmployeeIdCard() { return employeeIdCard; }
    public void setEmployeeIdCard(String employeeIdCard) { this.employeeIdCard = employeeIdCard; }

    public String getEmployeePhone() { return employeePhone; }
    public void setEmployeePhone(String employeePhone) { this.employeePhone = employeePhone; }

    public String getPostName() { return postName; }
    public void setPostName(String postName) { this.postName = postName; }

    public String getSocialType() { return socialType; }
    public void setSocialType(String socialType) { this.socialType = socialType; }

    public String getContractStartDate() { return contractStartDate; }
    public void setContractStartDate(String contractStartDate) { this.contractStartDate = contractStartDate; }

    public String getContractEndDate() { return contractEndDate; }
    public void setContractEndDate(String contractEndDate) { this.contractEndDate = contractEndDate; }

    public String getProbationStartDate() { return probationStartDate; }
    public void setProbationStartDate(String probationStartDate) { this.probationStartDate = probationStartDate == null || probationStartDate.isBlank() ? null : probationStartDate; this.probationStartDateSpecified = true; }

    public String getProbationEndDate() { return probationEndDate; }
    public void setProbationEndDate(String probationEndDate) { this.probationEndDate = probationEndDate == null || probationEndDate.isBlank() ? null : probationEndDate; this.probationEndDateSpecified = true; }

    public BigDecimal getBaseSalary() { return baseSalary; }
    public void setBaseSalary(BigDecimal baseSalary) { this.baseSalary = baseSalary; }

    public BigDecimal getManagementAllowance() { return managementAllowance; }
    public void setManagementAllowance(BigDecimal managementAllowance) { this.managementAllowance = managementAllowance; }

    public BigDecimal getOvertimePay() { return overtimePay; }
    public void setOvertimePay(BigDecimal overtimePay) { this.overtimePay = overtimePay; }

    public BigDecimal getRewardAllowance() { return rewardAllowance; }
    public void setRewardAllowance(BigDecimal rewardAllowance) { this.rewardAllowance = rewardAllowance; }

    public BigDecimal getFullAttendanceBonus() { return fullAttendanceBonus; }
    public void setFullAttendanceBonus(BigDecimal fullAttendanceBonus) { this.fullAttendanceBonus = fullAttendanceBonus; }

    public BigDecimal getSocialSubsidy() { return socialSubsidy; }
    public void setSocialSubsidy(BigDecimal socialSubsidy) { this.socialSubsidy = socialSubsidy; }

    public BigDecimal getCommuteSubsidy() { return commuteSubsidy; }
    public void setCommuteSubsidy(BigDecimal commuteSubsidy) { this.commuteSubsidy = commuteSubsidy; }

    public BigDecimal getTotalSalary() { return totalSalary; }
    public void setTotalSalary(BigDecimal totalSalary) { this.totalSalary = totalSalary; }

    public String getAccommodationText() { return accommodationText; }
    public void setAccommodationText(String accommodationText) { this.accommodationText = accommodationText; }

    public String getCommuteText() { return commuteText; }
    public void setCommuteText(String commuteText) { this.commuteText = commuteText; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPreviewFileUrl() { return previewFileUrl; }
    public void setPreviewFileUrl(String previewFileUrl) { this.previewFileUrl = previewFileUrl; }

    public String getArchiveFileUrl() { return archiveFileUrl; }
    public void setArchiveFileUrl(String archiveFileUrl) { this.archiveFileUrl = archiveFileUrl; }

    public String getPdfFileUrl() { return pdfFileUrl; }
    public void setPdfFileUrl(String pdfFileUrl) { this.pdfFileUrl = pdfFileUrl; }

    public String getSignatureFileUrl() { return signatureFileUrl; }
    public void setSignatureFileUrl(String signatureFileUrl) { this.signatureFileUrl = signatureFileUrl; }

    public String getContractFileHash() { return contractFileHash; }
    public void setContractFileHash(String contractFileHash) { this.contractFileHash = contractFileHash; }

    public String getDocumentVersion() { return documentVersion; }
    public void setDocumentVersion(String documentVersion) { this.documentVersion = documentVersion; }

    public String getPreviewFileHash() { return previewFileHash; }
    public void setPreviewFileHash(String previewFileHash) { this.previewFileHash = previewFileHash; }

    public String getArchiveFileHash() { return archiveFileHash; }
    public void setArchiveFileHash(String archiveFileHash) { this.archiveFileHash = archiveFileHash; }

    public String getCertificateFileUrl() { return certificateFileUrl; }
    public void setCertificateFileUrl(String certificateFileUrl) { this.certificateFileUrl = certificateFileUrl; }

    public String getCertificateFileHash() { return certificateFileHash; }
    public void setCertificateFileHash(String certificateFileHash) { this.certificateFileHash = certificateFileHash; }

    public String getTemplateFileHash() { return templateFileHash; }
    public void setTemplateFileHash(String templateFileHash) { this.templateFileHash = templateFileHash; }

    public String getSealImageHash() { return sealImageHash; }
    public void setSealImageHash(String sealImageHash) { this.sealImageHash = sealImageHash; }

    public String getSignerIp() { return signerIp; }
    public void setSignerIp(String signerIp) { this.signerIp = signerIp; }

    public String getSignerUserAgent() { return signerUserAgent; }
    public void setSignerUserAgent(String signerUserAgent) { this.signerUserAgent = signerUserAgent; }

    public Date getSentTime() { return sentTime; }
    public void setSentTime(Date sentTime) { this.sentTime = sentTime; }

    public Date getSignedTime() { return signedTime; }
    public void setSignedTime(Date signedTime) { this.signedTime = signedTime; }

    public Date getVoidedTime() { return voidedTime; }
    public void setVoidedTime(Date voidedTime) { this.voidedTime = voidedTime; }

    public String getSignProvider() { return signProvider; }
    public void setSignProvider(String signProvider) { this.signProvider = signProvider; }

    public String getProviderContractId() { return providerContractId; }
    public void setProviderContractId(String providerContractId) { this.providerContractId = providerContractId; }

    public String getProviderSignUrl() { return providerSignUrl; }
    public void setProviderSignUrl(String providerSignUrl) { this.providerSignUrl = providerSignUrl; }

    public String getProviderStatus() { return providerStatus; }
    public void setProviderStatus(String providerStatus) { this.providerStatus = providerStatus; }

    public String getProviderCallbackPayload() { return providerCallbackPayload; }
    public void setProviderCallbackPayload(String providerCallbackPayload) { this.providerCallbackPayload = providerCallbackPayload; }

    public String getCaCertNo() { return caCertNo; }
    public void setCaCertNo(String caCertNo) { this.caCertNo = caCertNo; }

    public List<OaLaborContractEvent> getEvents() { return events; }
    public void setEvents(List<OaLaborContractEvent> events) { this.events = events; }
}
