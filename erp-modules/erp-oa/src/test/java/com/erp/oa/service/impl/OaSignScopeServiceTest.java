package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.mapper.OaSignScopeMapper;

class OaSignScopeServiceTest
{
    private static final Long HR_USER_ID = 940L;
    private static final Long XI_AN_COMPANY_ID = 1157L;

    private OaSignScopeMapper mapper;
    private OaSignScopeService service;

    @BeforeEach
    void setUp()
    {
        SecurityContextHolder.setUserId(String.valueOf(HR_USER_ID));
        mapper = mock(OaSignScopeMapper.class);
        service = new OaSignScopeService(mapper);
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    void shouldAuthorizeExactActiveCompanyScope1157()
    {
        when(mapper.countActiveSignScopeDept(XI_AN_COMPANY_ID)).thenReturn(1);
        when(mapper.countUserSignScope(HR_USER_ID, XI_AN_COMPANY_ID)).thenReturn(1);

        assertThat(service.resolveRequiredShopDept(XI_AN_COMPANY_ID)).isEqualTo(XI_AN_COMPANY_ID);
        verify(mapper).countUserSignScope(HR_USER_ID, XI_AN_COMPANY_ID);
    }

    @Test
    void shouldNotAllowChildStoreAssignmentToAuthorizeParentCompany()
    {
        when(mapper.countActiveSignScopeDept(XI_AN_COMPANY_ID)).thenReturn(1);
        when(mapper.countUserSignScope(HR_USER_ID, XI_AN_COMPANY_ID)).thenReturn(0);

        assertThatThrownBy(() -> service.resolveRequiredShopDept(XI_AN_COMPANY_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("无权选择该签约组织");
    }

    @Test
    void shouldIncludeExactCompanyAndAuthorizedDescendantsInScope()
    {
        when(mapper.countActiveSignScopeDept(XI_AN_COMPANY_ID)).thenReturn(1);
        when(mapper.countUserSignScope(HR_USER_ID, XI_AN_COMPANY_ID)).thenReturn(1);
        when(mapper.selectSignScopeDeptIds(XI_AN_COMPANY_ID))
                .thenReturn(List.of(1157L, 1185L, 1186L));

        assertThat(service.resolveScopeDeptIds(XI_AN_COMPANY_ID))
                .containsExactly(1157L, 1185L, 1186L);
    }

    @Test
    void shouldRejectWarehouseBecauseSigningOnlySupportsStoreOrCompany()
    {
        when(mapper.countActiveSignScopeDept(1200L)).thenReturn(0);

        assertThatThrownBy(() -> service.resolveRequiredShopDept(1200L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("类型不支持");
    }

    @Test
    void shouldFailClosedIfAuthorizedScopeChangesDuringResolution()
    {
        when(mapper.countActiveSignScopeDept(XI_AN_COMPANY_ID)).thenReturn(1);
        when(mapper.countUserSignScope(HR_USER_ID, XI_AN_COMPANY_ID)).thenReturn(1);
        when(mapper.selectSignScopeDeptIds(XI_AN_COMPANY_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.resolveScopeDeptIds(XI_AN_COMPANY_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("范围已变化");
    }
}
