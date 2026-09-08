package com.erp.inventory.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.domain.InvCustomer;
import com.erp.inventory.mapper.InvCustomerMapper;
import com.erp.inventory.mapper.InvSalesOrderMapper;
import com.erp.inventory.service.IInvCustomerService;

@Service
public class InvCustomerServiceImpl extends InvBaseService implements IInvCustomerService
{
    @Autowired
    private InvCustomerMapper customerMapper;

    @Autowired
    private InvSalesOrderMapper salesOrderMapper;

    @Override
    public List<InvCustomer> selectCustomerList(InvCustomer customer, Long selectedShopDeptId)
    {
        appendShopScope(customer, selectedShopDeptId);
        return customerMapper.selectInvCustomerList(customer);
    }

    @Override
    public InvCustomer selectCustomerById(Long customerId, Long selectedShopDeptId)
    {
        return assertAndGetScopedCustomer(customerId, selectedShopDeptId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvCustomer saveCustomer(InvCustomer customer, Long selectedShopDeptId)
    {
        Long shopDeptId = resolveAndValidateShopDept(selectedShopDeptId);
        if (customer.getCustomerId() == null)
        {
            customer.setShopDeptId(shopDeptId);
            customer.setCreateBy(SecurityUtils.getUsername());
            customerMapper.insertInvCustomer(customer);
        }
        else
        {
            assertAndGetScopedCustomer(customer.getCustomerId(), selectedShopDeptId);
            customer.setUpdateBy(SecurityUtils.getUsername());
            customerMapper.updateInvCustomer(customer);
        }
        return customerMapper.selectInvCustomerById(customer.getCustomerId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCustomerByIds(Long[] customerIds, Long selectedShopDeptId)
    {
        for (Long customerId : customerIds)
        {
            InvCustomer customer = assertAndGetScopedCustomer(customerId, selectedShopDeptId);
            if (salesOrderMapper.countByCustomerNameAndShop(customer.getCustomerName(), customer.getShopDeptId()) > 0)
            {
                throw new ServiceException("客户已被销售历史单据引用，不能删除，请停用客户");
            }
        }
        if (customerMapper.deleteInvCustomerByIds(customerIds) != customerIds.length)
        {
            throw new ServiceException("客户服务资料已建立，不能物理删除，请使用客户服务卡归档");
        }
    }

    private InvCustomer assertAndGetScopedCustomer(Long customerId, Long selectedShopDeptId)
    {
        InvCustomer db = customerMapper.selectInvCustomerById(customerId);
        if (db == null)
        {
            throw new ServiceException("客户不存在");
        }
        assertShopVisible(db.getShopDeptId(), selectedShopDeptId, "无权访问该店铺客户");
        return db;
    }
}
