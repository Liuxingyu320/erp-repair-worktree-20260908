package com.erp.inventory.mapper;

import java.util.List;
import com.erp.inventory.domain.InvCustomer;

public interface InvCustomerMapper
{
    List<InvCustomer> selectInvCustomerList(InvCustomer customer);
    InvCustomer selectInvCustomerById(Long customerId);
    int insertInvCustomer(InvCustomer customer);
    int updateInvCustomer(InvCustomer customer);
    int deleteInvCustomerByIds(Long[] customerIds);
}
