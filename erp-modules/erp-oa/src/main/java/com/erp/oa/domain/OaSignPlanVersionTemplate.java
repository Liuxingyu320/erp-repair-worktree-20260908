package com.erp.oa.domain;

import com.erp.common.core.web.domain.BaseEntity;

/** Immutable template evidence bound to a published signing plan version. */
public class OaSignPlanVersionTemplate extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long planVersionId;
    private Long templateId;
    private String templateVersion;
    private String templateType;
    private String templateName;
    private String sourceFileUrl;
    private String sourceFileHash;
    private String requiredPlaceholders;
    private Integer sortOrder;
    private String employeeVisible;
    private String readConfirmationRequired;
    private String employeeSignRequired;
    private String signaturePositionJson;
    private String companySealPositionJson;
    private String companySealRequired;
    private String matchConditionJson;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPlanVersionId() { return planVersionId; }
    public void setPlanVersionId(Long planVersionId) { this.planVersionId = planVersionId; }

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }

    public String getTemplateVersion() { return templateVersion; }
    public void setTemplateVersion(String templateVersion) { this.templateVersion = templateVersion; }

    public String getTemplateType() { return templateType; }
    public void setTemplateType(String templateType) { this.templateType = templateType; }

    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }

    public String getSourceFileUrl() { return sourceFileUrl; }
    public void setSourceFileUrl(String sourceFileUrl) { this.sourceFileUrl = sourceFileUrl; }

    public String getSourceFileHash() { return sourceFileHash; }
    public void setSourceFileHash(String sourceFileHash) { this.sourceFileHash = sourceFileHash; }

    public String getRequiredPlaceholders() { return requiredPlaceholders; }
    public void setRequiredPlaceholders(String requiredPlaceholders) { this.requiredPlaceholders = requiredPlaceholders; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

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
}
