package com.erp.oa.service.impl;

import com.erp.common.security.shop.AbstractShopScopeService;
import com.erp.oa.mapper.OaDeptScopeMapper;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class OaShopScopeService extends AbstractShopScopeService
{
    @Autowired
    private OaDeptScopeMapper deptScopeMapper;

    @Override
    protected int countUserShopScope(Long userId, Long deptId)
    {
        return deptScopeMapper.countUserShopScope(userId, deptId);
    }

    @Override
    protected List<Long> selectSubDeptIds(Long deptId)
    {
        return deptScopeMapper.selectSubDeptIds(deptId);
    }

    @Override
    public String resolveShopDeptName(Long deptId)
    {
        return deptId == null ? null : deptScopeMapper.selectDeptName(deptId);
    }
}
