package com.erp.oa.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.oa.domain.OaSalaryRecord;
import com.erp.oa.mapper.OaSalaryRecordMapper;
import com.erp.oa.service.IOaSalaryService;
import com.erp.oa.service.impl.OaSalaryServiceImpl;

class OaSalaryPersonalExportTest
{
    @AfterEach
    void clearIdentity() { SecurityContextHolder.remove(); }

    @Test
    void personalExportUsesPersonalQueryAndKeepsMonthAndShop() throws Exception
    {
        IOaSalaryService service = mock(IOaSalaryService.class);
        OaSalaryController controller = new OaSalaryController();
        ReflectionTestUtils.setField(controller, "salaryService", service);
        OaSalaryRecord query = new OaSalaryRecord();
        query.setSalaryMonth("2026-08");
        when(service.selectMyRecords(query, 201L)).thenReturn(Collections.emptyList());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Dept-NumId", "201");
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.exportMy(response, query, request);

        verify(service).selectMyRecords(query, 201L);
        verify(service, never()).selectAllRecords(any(), anyLong());
        assertThat(query.getSalaryMonth()).isEqualTo("2026-08");
        assertThat(response.getContentAsByteArray()).isNotEmpty();
    }

    @Test
    void personalQueryOverridesCallerSuppliedUserAndAppliesShopScope()
    {
        SecurityContextHolder.setUserId("42");
        OaSalaryRecordMapper mapper = mock(OaSalaryRecordMapper.class);
        ShopScopeService shopScope = mock(ShopScopeService.class);
        OaSalaryServiceImpl service = new OaSalaryServiceImpl();
        ReflectionTestUtils.setField(service, "salaryMapper", mapper);
        ReflectionTestUtils.setField(service, "shopScopeService", shopScope);
        OaSalaryRecord query = new OaSalaryRecord();
        query.setUserId(999L);
        query.setSalaryMonth("2026-08");
        when(mapper.selectOaSalaryRecordList(query)).thenReturn(Collections.emptyList());
        doAnswer(call -> {
            assertThat(((OaSalaryRecord) call.getArgument(0)).getUserId()).isEqualTo(42L);
            return null;
        }).when(shopScope).appendShopScope(query, 201L);

        service.selectMyRecords(query, 201L);

        assertThat(query.getUserId()).isEqualTo(42L);
        assertThat(query.getSalaryMonth()).isEqualTo("2026-08");
        verify(shopScope).appendShopScope(query, 201L);
        verify(mapper).selectOaSalaryRecordList(query);
    }
}
