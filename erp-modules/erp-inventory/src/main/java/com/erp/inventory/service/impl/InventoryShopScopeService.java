package com.erp.inventory.service.impl;

import com.erp.common.security.shop.AbstractShopScopeService;
import com.erp.inventory.mapper.InvDeptScopeMapper;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class InventoryShopScopeService extends AbstractShopScopeService
{
    @Autowired
    private InvDeptScopeMapper deptScopeMapper;

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
}
