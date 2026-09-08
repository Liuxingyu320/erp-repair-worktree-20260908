package com.erp.system.domain.vo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class HrOnboardingConflictVo implements Serializable
{
    private static final long serialVersionUID = 1L;
    private String conflictType;
    private String sourceType;
    private Long candidateUserId;
    private Long candidateOnboardingId;
    private String employeeNo;
    private String name;
    private String maskedPhone;
    private String departmentLabel;
    private Boolean eligibleForBind;
    private Boolean eligibleForRehire;
    private Boolean blocking;
    private List<String> allowedDecisions = new ArrayList<>();

    public String getConflictType() { return conflictType; }
    public void setConflictType(String conflictType) { this.conflictType = conflictType; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public Long getCandidateUserId() { return candidateUserId; }
    public void setCandidateUserId(Long candidateUserId) { this.candidateUserId = candidateUserId; }
    public Long getCandidateOnboardingId() { return candidateOnboardingId; }
    public void setCandidateOnboardingId(Long candidateOnboardingId) { this.candidateOnboardingId = candidateOnboardingId; }
    public String getEmployeeNo() { return employeeNo; }
    public void setEmployeeNo(String employeeNo) { this.employeeNo = employeeNo; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getMaskedPhone() { return maskedPhone; }
    public void setMaskedPhone(String maskedPhone) { this.maskedPhone = maskedPhone; }
    public String getDepartmentLabel() { return departmentLabel; }
    public void setDepartmentLabel(String departmentLabel) { this.departmentLabel = departmentLabel; }
    public Boolean getEligibleForBind() { return eligibleForBind; }
    public void setEligibleForBind(Boolean eligibleForBind) { this.eligibleForBind = eligibleForBind; }
    public Boolean getEligibleForRehire() { return eligibleForRehire; }
    public void setEligibleForRehire(Boolean eligibleForRehire) { this.eligibleForRehire = eligibleForRehire; }
    public Boolean getBlocking() { return blocking; }
    public void setBlocking(Boolean blocking) { this.blocking = blocking; }
    public List<String> getAllowedDecisions() { return allowedDecisions; }
    public void setAllowedDecisions(List<String> allowedDecisions) { this.allowedDecisions = allowedDecisions == null ? new ArrayList<>() : allowedDecisions; }
}
