package com.erp.system.mapper;

import com.erp.system.domain.vo.SysSalaryImpactStats;

/** 薪资保存前影响统计，只执行只读聚合查询。 */
public interface SysSalaryImpactMapper
{
    SysSalaryImpactStats selectSchemeImpact(Long schemeId);

    SysSalaryImpactStats selectItemImpact(Long itemId);

    int countUsersByRoleId(Long roleId);

    int countUsersWithDirectSalaryByRoleId(Long roleId);

    int countRoleSalaryBindingsByUserId(Long userId);
}
