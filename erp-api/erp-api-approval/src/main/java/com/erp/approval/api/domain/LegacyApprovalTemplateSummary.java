package com.erp.approval.api.domain;

import java.io.Serializable;

public class LegacyApprovalTemplateSummary implements Serializable
{
    private static final long serialVersionUID = 1L;
    private String businessCode;
    private String templateName;
    private String businessSource;
    private String engineMode = "LEGACY";
    private String adapterCode;
    private String configSummary;
    private String supportedAdminActions;

    public String getBusinessCode() { return businessCode; }
    public void setBusinessCode(String value) { businessCode = value; }
    public String getTemplateName() { return templateName; }
    public void setTemplateName(String value) { templateName = value; }
    public String getBusinessSource() { return businessSource; }
    public void setBusinessSource(String value) { businessSource = value; }
    public String getEngineMode() { return engineMode; }
    public void setEngineMode(String value) { engineMode = value; }
    public String getAdapterCode() { return adapterCode; }
    public void setAdapterCode(String value) { adapterCode = value; }
    public String getConfigSummary() { return configSummary; }
    public void setConfigSummary(String value) { configSummary = value; }
    public String getSupportedAdminActions() { return supportedAdminActions; }
    public void setSupportedAdminActions(String value) { supportedAdminActions = value; }
}
