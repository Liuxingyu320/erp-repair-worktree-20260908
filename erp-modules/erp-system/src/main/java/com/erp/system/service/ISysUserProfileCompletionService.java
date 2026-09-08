package com.erp.system.service;

import com.erp.system.domain.vo.SysProfileCompletionRequest;
import com.erp.system.domain.vo.SysProfileCompletionVo;

/**
 * 登录员工资料完整度服务。
 */
public interface ISysUserProfileCompletionService
{
    public SysProfileCompletionVo evaluate(Long userId);

    public SysProfileCompletionVo save(Long userId, SysProfileCompletionRequest request, String operator);
}
