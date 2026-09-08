package com.erp.system.domain.vo;

/** Fail-closed capability state for accepting new health-certificate submissions. */
public class HrHealthCertificateCapabilityVo
{
    private boolean intakeEnabled;
    private String reason;
    private boolean schemaRequired=true;

    public boolean isIntakeEnabled(){return intakeEnabled;}
    public void setIntakeEnabled(boolean intakeEnabled){this.intakeEnabled=intakeEnabled;}
    public String getReason(){return reason;}
    public void setReason(String reason){this.reason=reason;}
    public boolean isSchemaRequired(){return schemaRequired;}
    public void setSchemaRequired(boolean schemaRequired){this.schemaRequired=schemaRequired;}
}
