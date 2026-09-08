package com.erp.inventory.service;

import java.util.List;
import com.erp.inventory.domain.InvCustomer;

public interface IInvCustomerService
{
    List<InvCustomer> selectCustomerList(InvCustomer customer, Long selectedShopDeptId);
    InvCustomer selectCustomerById(Long customerId, Long selectedShopDeptId);
    InvCustomer saveCustomer(InvCustomer customer, Long selectedShopDeptId);
    void deleteCustomerByIds(Long[] customerIds, Long selectedShopDeptId);
}
