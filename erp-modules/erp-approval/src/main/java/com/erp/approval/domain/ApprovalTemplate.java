package com.erp.approval.domain;

import com.erp.common.core.web.domain.BaseEntity;

/** Registered approval-bearing business. */
public class ApprovalTemplate extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long templateId;
    private String businessCode;
    private String templateName;
    private String businessSource;
    private String engineMode;
    private String definitionMode;
    private String legacyAdapterCode;
    private String callbackService;
    private String templateStatus;
    private Long lockVersion;

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }
    public String getBusinessCode() { return businessCode; }
    public void setBusinessCode(String businessCode) { this.businessCode = businessCode; }
    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }
    public String getBusinessSource() { return businessSource; }
    public void setBusinessSource(String businessSource) { this.businessSource = businessSource; }
    public String getEngineMode() { return engineMode; }
    public void setEngineMode(String engineMode) { this.engineMode = engineMode; }
    public String getDefinitionMode() { return definitionMode; }
    public void setDefinitionMode(String definitionMode) { this.definitionMode = definitionMode; }
    public String getLegacyAdapterCode() { return legacyAdapterCode; }
    public void setLegacyAdapterCode(String legacyAdapterCode) { this.legacyAdapterCode = legacyAdapterCode; }
    public String getCallbackService() { return callbackService; }
    public void setCallbackService(String callbackService) { this.callbackService = callbackService; }
    public String getTemplateStatus() { return templateStatus; }
    public void setTemplateStatus(String templateStatus) { this.templateStatus = templateStatus; }
    public Long getLockVersion() { return lockVersion; }
    public void setLockVersion(Long lockVersion) { this.lockVersion = lockVersion; }
}
