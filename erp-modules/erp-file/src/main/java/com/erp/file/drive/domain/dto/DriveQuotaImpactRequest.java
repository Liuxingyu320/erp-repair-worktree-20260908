package com.erp.file.drive.domain.dto;

import java.util.Date;
import java.util.List;
import jakarta.validation.constraints.NotBlank;

/** 三类额度变更共用的只读影响预览请求。 */
public class DriveQuotaImpactRequest
{
    @NotBlank
    private String changeType;
    private String subjectType;
    private Long subjectId;
    private Boolean deletePolicy;
    private Long quotaBytes;
    private Integer priority;
    private Date expireTime;
    private Boolean enabled;
    private Boolean requireActiveMember;
    private String ruleStatus;
    private Long treeBudgetBytes;
    private String memberWriteMode;
    private String lifecycleStatus;
    private Long physicalCapacityBytes;
    private Integer reservePercent;
    private Long publicPoolBytes;
    private Long personalPoolBytes;
    private Long organizationPoolBytes;
    private String enforcementMode;
    private Integer version;
    private List<DriveOrganizationBatchTarget> targets;

    public String getChangeType() { return changeType; }
    public void setChangeType(String changeType) { this.changeType = changeType; }
    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    public Long getSubjectId() { return subjectId; }
    public void setSubjectId(Long subjectId) { this.subjectId = subjectId; }
    public Boolean getDeletePolicy() { return deletePolicy; }
    public void setDeletePolicy(Boolean deletePolicy) { this.deletePolicy = deletePolicy; }
    public Long getQuotaBytes() { return quotaBytes; }
    public void setQuotaBytes(Long quotaBytes) { this.quotaBytes = quotaBytes; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public Date getExpireTime() { return expireTime; }
    public void setExpireTime(Date expireTime) { this.expireTime = expireTime; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Boolean getRequireActiveMember() { return requireActiveMember; }
    public void setRequireActiveMember(Boolean requireActiveMember) { this.requireActiveMember = requireActiveMember; }
    public String getRuleStatus() { return ruleStatus; }
    public void setRuleStatus(String ruleStatus) { this.ruleStatus = ruleStatus; }
    public Long getTreeBudgetBytes() { return treeBudgetBytes; }
    public void setTreeBudgetBytes(Long treeBudgetBytes) { this.treeBudgetBytes = treeBudgetBytes; }
    public String getMemberWriteMode() { return memberWriteMode; }
    public void setMemberWriteMode(String memberWriteMode) { this.memberWriteMode = memberWriteMode; }
    public String getLifecycleStatus() { return lifecycleStatus; }
    public void setLifecycleStatus(String lifecycleStatus) { this.lifecycleStatus = lifecycleStatus; }
    public Long getPhysicalCapacityBytes() { return physicalCapacityBytes; }
    public void setPhysicalCapacityBytes(Long physicalCapacityBytes) { this.physicalCapacityBytes = physicalCapacityBytes; }
    public Integer getReservePercent() { return reservePercent; }
    public void setReservePercent(Integer reservePercent) { this.reservePercent = reservePercent; }
    public Long getPublicPoolBytes() { return publicPoolBytes; }
    public void setPublicPoolBytes(Long publicPoolBytes) { this.publicPoolBytes = publicPoolBytes; }
    public Long getPersonalPoolBytes() { return personalPoolBytes; }
    public void setPersonalPoolBytes(Long personalPoolBytes) { this.personalPoolBytes = personalPoolBytes; }
    public Long getOrganizationPoolBytes() { return organizationPoolBytes; }
    public void setOrganizationPoolBytes(Long organizationPoolBytes) { this.organizationPoolBytes = organizationPoolBytes; }
    public String getEnforcementMode() { return enforcementMode; }
    public void setEnforcementMode(String enforcementMode) { this.enforcementMode = enforcementMode; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public List<DriveOrganizationBatchTarget> getTargets() { return targets; }
    public void setTargets(List<DriveOrganizationBatchTarget> targets) { this.targets = targets; }
}
