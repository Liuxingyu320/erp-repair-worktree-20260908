package com.erp.oa.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.erp.common.core.annotation.Excel;
import com.erp.common.core.web.domain.BaseEntity;

public class OaSignTemplate extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long templateId;

    @NotBlank(message = "模板类型不能为空")
    private String templateType;

    @NotBlank(message = "模板名称不能为空")
    @Size(max = 120, message = "模板名称长度不能超过120个字符")
    @Excel(name = "模板名称")
    private String templateName;

    @Excel(name = "模板版本")
    private String templateVersion;

    @Excel(name = "签约场景")
    private String scenario;

    @Excel(name = "用工类型")
    private String employmentType;

    @Excel(name = "社保口径")
    private String socialType;

    @Excel(name = "岗位等级范围")
    private String postLevelScope;

    @Excel(name = "薪酬版本")
    private String salaryVersion;

    @NotBlank(message = "模板文件不能为空")
    private String fileUrl;

    private String fileName;
    private Long fileSize;
    private String fileHash;
    private String requiredPlaceholders;
    private String optionalPlaceholders;
    private String employeeVisible;
    private String readConfirmationRequired;
    private String employeeSignRequired;
    private String signaturePositionJson;
    private String companySealPositionJson;
    private String companySealRequired;
    private String matchConditionJson;
    private Integer sortOrder;

    @Excel(name = "状态", readConverterExp = "0=启用,1=停用")
    private String status;

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }

    public String getTemplateType() { return templateType; }
    public void setTemplateType(String templateType) { this.templateType = templateType; }

    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }

    public String getTemplateVersion() { return templateVersion; }
    public void setTemplateVersion(String templateVersion) { this.templateVersion = templateVersion; }

    public String getScenario() { return scenario; }
    public void setScenario(String scenario) { this.scenario = scenario; }

    public String getEmploymentType() { return employmentType; }
    public void setEmploymentType(String employmentType) { this.employmentType = employmentType; }

    public String getSocialType() { return socialType; }
    public void setSocialType(String socialType) { this.socialType = socialType; }

    public String getPostLevelScope() { return postLevelScope; }
    public void setPostLevelScope(String postLevelScope) { this.postLevelScope = postLevelScope; }

    public String getSalaryVersion() { return salaryVersion; }
    public void setSalaryVersion(String salaryVersion) { this.salaryVersion = salaryVersion; }

    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public String getFileHash() { return fileHash; }
    public void setFileHash(String fileHash) { this.fileHash = fileHash; }

    public String getRequiredPlaceholders() { return requiredPlaceholders; }
    public void setRequiredPlaceholders(String requiredPlaceholders) { this.requiredPlaceholders = requiredPlaceholders; }

    public String getOptionalPlaceholders() { return optionalPlaceholders; }
    public void setOptionalPlaceholders(String optionalPlaceholders) { this.optionalPlaceholders = optionalPlaceholders; }

    public String getEmployeeVisible() { return employeeVisible; }
    public void setEmployeeVisible(String employeeVisible) { this.employeeVisible = employeeVisible; }

    public String getReadConfirmationRequired() { return readConfirmationRequired; }
    public void setReadConfirmationRequired(String readConfirmationRequired) { this.readConfirmationRequired = readConfirmationRequired; }

    public String getEmployeeSignRequired() { return employeeSignRequired; }
    public void setEmployeeSignRequired(String employeeSignRequired) { this.employeeSignRequired = employeeSignRequired; }

    public String getSignaturePositionJson() { return signaturePositionJson; }
    public void setSignaturePositionJson(String signaturePositionJson) { this.signaturePositionJson = signaturePositionJson; }

    public String getCompanySealPositionJson() { return companySealPositionJson; }
    public void setCompanySealPositionJson(String companySealPositionJson) { this.companySealPositionJson = companySealPositionJson; }

    public String getCompanySealRequired() { return companySealRequired; }
    public void setCompanySealRequired(String companySealRequired) { this.companySealRequired = companySealRequired; }

    public String getMatchConditionJson() { return matchConditionJson; }
    public void setMatchConditionJson(String matchConditionJson) { this.matchConditionJson = matchConditionJson; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
