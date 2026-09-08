package com.erp.approval.api.domain;

import java.io.Serializable;

/** Readiness details for internal callers. */
public class ApprovalServiceStatus implements Serializable
{
    private static final long serialVersionUID = 1L;

    private String serviceName;

    private Boolean runtimeEnabled;

    private String phase;

    public ApprovalServiceStatus()
    {
    }

    public ApprovalServiceStatus(String serviceName, Boolean runtimeEnabled,
            String phase)
    {
        this.serviceName = serviceName;
        this.runtimeEnabled = runtimeEnabled;
        this.phase = phase;
    }

    public String getServiceName()
    {
        return serviceName;
    }

    public void setServiceName(String serviceName)
    {
        this.serviceName = serviceName;
    }

    public Boolean getRuntimeEnabled()
    {
        return runtimeEnabled;
    }

    public void setRuntimeEnabled(Boolean runtimeEnabled)
    {
        this.runtimeEnabled = runtimeEnabled;
    }

    public String getPhase()
    {
        return phase;
    }

    public void setPhase(String phase)
    {
        this.phase = phase;
    }
}
