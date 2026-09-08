package com.erp.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.system.domain.SysUserSalaryScheme;

/**
 * 用户薪资方案关联 数据层
 *
 * @author erp
 */
public interface SysUserSalarySchemeMapper
{
    public List<SysUserSalaryScheme> selectUserSalarySchemesByUserId(Long userId);

    public List<SysUserSalaryScheme> selectUserSalarySchemesByUserIds(@Param("userIds") Long[] userIds);

    public int deleteUserSalarySchemeByUserId(Long userId);

    public int deleteUserSalarySchemeBySchemeIds(Long[] schemeIds);

    public int batchUserSalaryScheme(@Param("list") List<SysUserSalaryScheme> list);
}
