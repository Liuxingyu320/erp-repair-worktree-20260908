package com.erp.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.system.domain.SysRoleSalaryScheme;

/**
 * 角色薪资方案关联 数据层
 *
 * @author erp
 */
public interface SysRoleSalarySchemeMapper
{
    public List<SysRoleSalaryScheme> selectRoleSalarySchemesByRoleId(Long roleId);

    public List<SysRoleSalaryScheme> selectRoleSalarySchemesByRoleIds(@Param("roleIds") Long[] roleIds);

    public List<SysRoleSalaryScheme> selectRoleSalarySchemesBySchemeId(Long schemeId);

    public int deleteRoleSalarySchemeByRoleId(Long roleId);

    public int deleteRoleSalarySchemeBySchemeIds(Long[] schemeIds);

    public int batchRoleSalaryScheme(@Param("list") List<SysRoleSalaryScheme> list);
}
