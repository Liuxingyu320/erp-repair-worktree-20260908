package com.erp.approval.domain.dto;

import java.util.LinkedHashMap;
import java.util.Map;

public class ApprovalPreviewRequest
{
    private Long anchorDeptId;
    private Long applicantId;
    private String businessSubtype;
    private Map<String, Object> variables = new LinkedHashMap<>();

    public Long getAnchorDeptId() { return anchorDeptId; }
    public void setAnchorDeptId(Long anchorDeptId) { this.anchorDeptId = anchorDeptId; }
    public Long getApplicantId() { return applicantId; }
    public void setApplicantId(Long applicantId) { this.applicantId = applicantId; }
    public String getBusinessSubtype() { return businessSubtype; }
    public void setBusinessSubtype(String businessSubtype) { this.businessSubtype = businessSubtype; }
    public Map<String, Object> getVariables() { return variables; }
    public void setVariables(Map<String, Object> value) { variables = value == null ? new LinkedHashMap<>() : value; }
}
