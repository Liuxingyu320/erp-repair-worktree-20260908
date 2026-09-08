package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.ibatis.io.Resources;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.InvCustomer;
import com.erp.inventory.mapper.InvCustomerMapper;
import com.erp.inventory.mapper.InvDeptScopeMapper;
import com.erp.inventory.mapper.InvSalesOrderMapper;

class InvCustomerServiceImplTest
{
    private InvCustomerMapper customerMapper;
    private InvSalesOrderMapper salesOrderMapper;
    private InvCustomerServiceImpl service;

    @BeforeEach
    void setUp()
    {
        SecurityContextHolder.setUserId("7");
        SecurityContextHolder.setUserName("store-user");
        customerMapper = mock(InvCustomerMapper.class);
        salesOrderMapper = mock(InvSalesOrderMapper.class);
        InvDeptScopeMapper deptScopeMapper = mock(InvDeptScopeMapper.class);
        when(deptScopeMapper.countUserShopScope(7L, 10L)).thenReturn(1);
        when(deptScopeMapper.countDeptInScope(10L, 10L)).thenReturn(1);

        service = new InvCustomerServiceImpl();
        ReflectionTestUtils.setField(service, "customerMapper", customerMapper);
        ReflectionTestUtils.setField(service, "salesOrderMapper", salesOrderMapper);
        ReflectionTestUtils.setField(service, "deptScopeMapper", deptScopeMapper);
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    void legacyDeleteCannotRemoveCustomerWithServiceCardData()
    {
        InvCustomer customer = new InvCustomer();
        customer.setCustomerId(50L);
        customer.setCustomerName("王女士");
        customer.setShopDeptId(10L);
        when(customerMapper.selectInvCustomerById(50L)).thenReturn(customer);
        when(salesOrderMapper.countByCustomerNameAndShop("王女士", 10L))
                .thenReturn(0);
        when(customerMapper.deleteInvCustomerByIds(new Long[] {50L}))
                .thenReturn(0);

        assertThatThrownBy(() -> service.deleteCustomerByIds(
                new Long[] {50L}, 10L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不能物理删除");

        verify(customerMapper).deleteInvCustomerByIds(new Long[] {50L});
    }

    @Test
    void legacyDeleteSqlHasDatabaseLevelServiceCardGuard() throws Exception
    {
        try (InputStream input = Resources.getResourceAsStream(
                "mapper/inventory/InvCustomerMapper.xml"))
        {
            String xml = new String(input.readAllBytes(),
                    StandardCharsets.UTF_8);
            int start = xml.indexOf("<delete id=\"deleteInvCustomerByIds\"");
            int end = xml.indexOf("</delete>", start);
            String deleteSql = xml.substring(start, end);

            org.assertj.core.api.Assertions.assertThat(deleteSql)
                    .contains("not exists",
                            "inv_customer_service_profile",
                            "p.customer_id = inv_customer.customer_id");
        }
    }
}
