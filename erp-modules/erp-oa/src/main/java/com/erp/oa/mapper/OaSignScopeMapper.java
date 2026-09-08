package com.erp.oa.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.oa.domain.vo.OaSignScopeOption;

public interface OaSignScopeMapper
{
    int countActiveSignScopeDept(@Param("deptId") Long deptId);

    int countUserSignScope(@Param("userId") Long userId, @Param("deptId") Long deptId);

    List<Long> selectSignScopeDeptIds(@Param("deptId") Long deptId);

    String selectSignScopeDeptName(@Param("deptId") Long deptId);

    List<OaSignScopeOption> selectUserSignScopeOptions(@Param("userId") Long userId);

    List<OaSignScopeOption> selectAllSignScopeOptions();
}
