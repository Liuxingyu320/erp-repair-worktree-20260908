package com.erp.oa.service.impl;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.oa.domain.vo.OaSignScopeOption;
import com.erp.oa.mapper.OaSignScopeMapper;

/**
 * Authorization boundary dedicated to employee signing. It intentionally does
 * not alter the inventory/OA STORE|WAREHOUSE context service.
 */
@Service("oaSignScopeService")
public class OaSignScopeService implements ShopScopeService
{
    private final OaSignScopeMapper signScopeMapper;

    public OaSignScopeService(OaSignScopeMapper signScopeMapper)
    {
        this.signScopeMapper = signScopeMapper;
    }

    @Override
    public Long resolveRequiredShopDept(Long selectedDeptId)
    {
        if (selectedDeptId == null || selectedDeptId <= 0)
        {
            throw new ServiceException("请先选择签约组织");
        }
        if (signScopeMapper.countActiveSignScopeDept(selectedDeptId) != 1)
        {
            throw new ServiceException("签约组织不存在、已停用或类型不支持");
        }
        if (!SecurityUtils.isAdmin()
                && signScopeMapper.countUserSignScope(SecurityUtils.getUserId(), selectedDeptId) <= 0)
        {
            throw new ServiceException("当前用户无权选择该签约组织");
        }
        return selectedDeptId;
    }

    @Override
    public List<Long> resolveScopeDeptIds(Long selectedDeptId)
    {
        if (SecurityUtils.isAdmin() && (selectedDeptId == null || selectedDeptId == 0))
        {
            return Collections.emptyList();
        }
        Long scopeRoot = resolveRequiredShopDept(selectedDeptId);
        List<Long> deptIds = signScopeMapper.selectSignScopeDeptIds(scopeRoot);
        if (deptIds == null || deptIds.isEmpty() || !deptIds.contains(scopeRoot))
        {
            throw new ServiceException("签约组织范围已变化，请刷新后重试");
        }
        return deptIds;
    }

    @Override
    public boolean hasUserShopScope(Long userId, Long deptId)
    {
        return userId != null && deptId != null
                && signScopeMapper.countActiveSignScopeDept(deptId) == 1
                && signScopeMapper.countUserSignScope(userId, deptId) > 0;
    }

    @Override
    public String resolveShopDeptName(Long deptId)
    {
        return deptId == null ? null : signScopeMapper.selectSignScopeDeptName(deptId);
    }

    @Override
    public void appendShopScope(Map<String, Object> params, Long selectedDeptId)
    {
        if (params == null)
        {
            return;
        }
        List<Long> deptIds = resolveScopeDeptIds(selectedDeptId);
        if (!deptIds.isEmpty())
        {
            params.put("scopeDeptIds", deptIds);
        }
    }

    public List<OaSignScopeOption> listAvailableScopes()
    {
        List<OaSignScopeOption> options = SecurityUtils.isAdmin()
                ? signScopeMapper.selectAllSignScopeOptions()
                : signScopeMapper.selectUserSignScopeOptions(SecurityUtils.getUserId());
        return options == null ? Collections.emptyList() : options;
    }
}
