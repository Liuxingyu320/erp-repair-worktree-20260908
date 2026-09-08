package com.erp.system.domain.vo;

import java.util.ArrayList;
import java.util.List;

/** 保存前返回给管理员的薪资影响摘要。 */
public class SysSalaryImpactPreview
{
    private String operation;
    private Long schemeId;
    private Integer currentVersion;
    private Integer affectedUserCount = 0;
    private Integer directUserCount = 0;
    private Integer roleInheritedUserCount = 0;
    private Integer effectiveNowCount = 0;
    private Integer futureEffectiveCount = 0;
    private String effectiveDate;
    private boolean requiresConfirmation;
    private List<String> conflicts = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();

    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public Long getSchemeId() { return schemeId; }
    public void setSchemeId(Long schemeId) { this.schemeId = schemeId; }
    public Integer getCurrentVersion() { return currentVersion; }
    public void setCurrentVersion(Integer currentVersion) { this.currentVersion = currentVersion; }
    public Integer getAffectedUserCount() { return affectedUserCount; }
    public void setAffectedUserCount(Integer value) { this.affectedUserCount = safe(value); }
    public Integer getDirectUserCount() { return directUserCount; }
    public void setDirectUserCount(Integer value) { this.directUserCount = safe(value); }
    public Integer getRoleInheritedUserCount() { return roleInheritedUserCount; }
    public void setRoleInheritedUserCount(Integer value) { this.roleInheritedUserCount = safe(value); }
    public Integer getEffectiveNowCount() { return effectiveNowCount; }
    public void setEffectiveNowCount(Integer value) { this.effectiveNowCount = safe(value); }
    public Integer getFutureEffectiveCount() { return futureEffectiveCount; }
    public void setFutureEffectiveCount(Integer value) { this.futureEffectiveCount = safe(value); }
    public String getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(String effectiveDate) { this.effectiveDate = effectiveDate; }
    public boolean isRequiresConfirmation() { return requiresConfirmation; }
    public void setRequiresConfirmation(boolean value) { this.requiresConfirmation = value; }
    public List<String> getConflicts() { return conflicts; }
    public void setConflicts(List<String> conflicts) { this.conflicts = conflicts == null ? new ArrayList<>() : conflicts; }
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings == null ? new ArrayList<>() : warnings; }

    private int safe(Integer value) { return value == null ? 0 : value; }
}
