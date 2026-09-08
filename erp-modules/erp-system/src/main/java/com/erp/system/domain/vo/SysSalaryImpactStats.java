package com.erp.system.domain.vo;

/** 薪资影响聚合查询的内部统计结果。 */
public class SysSalaryImpactStats
{
    private Integer affectedUserCount;
    private Integer directUserCount;
    private Integer roleInheritedUserCount;
    private Integer effectiveNowCount;
    private Integer futureEffectiveCount;
    private Integer conflictUserCount;

    public Integer getAffectedUserCount() { return affectedUserCount; }
    public void setAffectedUserCount(Integer value) { this.affectedUserCount = value; }
    public Integer getDirectUserCount() { return directUserCount; }
    public void setDirectUserCount(Integer value) { this.directUserCount = value; }
    public Integer getRoleInheritedUserCount() { return roleInheritedUserCount; }
    public void setRoleInheritedUserCount(Integer value) { this.roleInheritedUserCount = value; }
    public Integer getEffectiveNowCount() { return effectiveNowCount; }
    public void setEffectiveNowCount(Integer value) { this.effectiveNowCount = value; }
    public Integer getFutureEffectiveCount() { return futureEffectiveCount; }
    public void setFutureEffectiveCount(Integer value) { this.futureEffectiveCount = value; }
    public Integer getConflictUserCount() { return conflictUserCount; }
    public void setConflictUserCount(Integer value) { this.conflictUserCount = value; }
}
