package com.erp.approval.api.domain;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Command used by a business service to start one approval round. */
public class ApprovalStartRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    @NotBlank
    @Size(max = 64)
    private String businessCode;

    @NotBlank
    @Size(max = 128)
    private String businessId;

    @NotNull
    @Positive
    private Integer businessRound;

    @NotNull
    @Positive
    private Long applicantId;

    @Positive
    private Long anchorDeptId;

    @Size(max = 64)
    private String applicantName;

    @Positive
    private Long applicantDeptId;

    @Size(max = 100)
    private String applicantDeptName;

    @Size(max = 100)
    private String anchorDeptName;

    @Positive
    private Long previousInstanceId;

    @Size(max = 64)
    private String businessSubtype;

    @NotBlank
    @Size(max = 128)
    private String idempotencyKey;

    private Map<String, Object> variables = new LinkedHashMap<>();

    private Map<String, String> routeSnapshot = new LinkedHashMap<>();

    public String getBusinessCode()
    {
        return businessCode;
    }

    public void setBusinessCode(String businessCode)
    {
        this.businessCode = businessCode;
    }

    public String getBusinessId()
    {
        return businessId;
    }

    public void setBusinessId(String businessId)
    {
        this.businessId = businessId;
    }

    public Integer getBusinessRound()
    {
        return businessRound;
    }

    public void setBusinessRound(Integer businessRound)
    {
        this.businessRound = businessRound;
    }

    public Long getApplicantId()
    {
        return applicantId;
    }

    public void setApplicantId(Long applicantId)
    {
        this.applicantId = applicantId;
    }

    public Long getAnchorDeptId()
    {
        return anchorDeptId;
    }

    public String getApplicantName() { return applicantName; }
    public void setApplicantName(String applicantName) { this.applicantName = applicantName; }
    public Long getApplicantDeptId() { return applicantDeptId; }
    public void setApplicantDeptId(Long applicantDeptId) { this.applicantDeptId = applicantDeptId; }
    public String getApplicantDeptName() { return applicantDeptName; }
    public void setApplicantDeptName(String applicantDeptName) { this.applicantDeptName = applicantDeptName; }
    public String getAnchorDeptName() { return anchorDeptName; }
    public void setAnchorDeptName(String anchorDeptName) { this.anchorDeptName = anchorDeptName; }
    public Long getPreviousInstanceId() { return previousInstanceId; }
    public void setPreviousInstanceId(Long previousInstanceId) { this.previousInstanceId = previousInstanceId; }

    public void setAnchorDeptId(Long anchorDeptId)
    {
        this.anchorDeptId = anchorDeptId;
    }

    public String getBusinessSubtype()
    {
        return businessSubtype;
    }

    public void setBusinessSubtype(String businessSubtype)
    {
        this.businessSubtype = businessSubtype;
    }

    public String getIdempotencyKey()
    {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey)
    {
        this.idempotencyKey = idempotencyKey;
    }

    public Map<String, Object> getVariables()
    {
        return variables;
    }

    public void setVariables(Map<String, Object> variables)
    {
        this.variables = variables == null ? new LinkedHashMap<>() : variables;
    }

    public Map<String, String> getRouteSnapshot()
    {
        return routeSnapshot;
    }

    public void setRouteSnapshot(Map<String, String> routeSnapshot)
    {
        this.routeSnapshot = routeSnapshot == null
                ? new LinkedHashMap<>() : routeSnapshot;
    }
}
