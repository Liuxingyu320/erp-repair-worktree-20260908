package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.ibatis.io.Resources;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.constant.TokenConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.exception.auth.NotPermissionException;
import com.erp.inventory.mapper.InvDeptScopeMapper;
import com.erp.inventory.mapper.InvMobileMapper;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.model.LoginUser;

@DisplayName("手机端供应商选项数据范围契约")
class InvMobileSupplierScopeContractTest
{
    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("供应商选项使用当前下级及祖先的 related scope")
    void supplierOptionsShouldUseRelatedScope()
    {
        setLoginUser(Set.of("inv:supplier:list"));
        InvMobileMapper mobileMapper = org.mockito.Mockito.mock(InvMobileMapper.class);
        InvDeptScopeMapper deptScopeMapper = authorizedScopeMapper();
        when(deptScopeMapper.selectRelatedDeptIds(88L)).thenReturn(List.of(88L, 188L, 8L));
        when(mobileMapper.selectSupplierOptions(anyString(), any(), anyInt())).thenReturn(Collections.emptyList());

        service(mobileMapper, deptScopeMapper).selectOptions("supplier", "供应商", 20, 88L);

        ArgumentCaptor<Map<String, Object>> params = paramsCaptor();
        verify(mobileMapper).selectSupplierOptions(eq("供应商"), params.capture(), eq(20));
        assertThat(params.getValue()).containsEntry("scopeDeptIds", List.of(88L, 188L, 8L));
        verify(deptScopeMapper).selectRelatedDeptIds(88L);
        verify(deptScopeMapper, never()).selectSubDeptIds(any());
    }

    @Test
    @DisplayName("客户选项继续使用当前及下级 subtree scope")
    void customerOptionsShouldKeepSubtreeScope()
    {
        setLoginUser(Set.of("inv:customer:list"));
        InvMobileMapper mobileMapper = org.mockito.Mockito.mock(InvMobileMapper.class);
        InvDeptScopeMapper deptScopeMapper = authorizedScopeMapper();
        when(deptScopeMapper.selectSubDeptIds(88L)).thenReturn(List.of(88L, 188L));
        when(mobileMapper.selectCustomerOptions(any(), any(), anyInt())).thenReturn(Collections.emptyList());

        service(mobileMapper, deptScopeMapper).selectOptions("customer", null, null, 88L);

        ArgumentCaptor<Map<String, Object>> params = paramsCaptor();
        verify(mobileMapper).selectCustomerOptions(eq(null), params.capture(), eq(20));
        assertThat(params.getValue()).containsEntry("scopeDeptIds", List.of(88L, 188L));
        verify(deptScopeMapper).selectSubDeptIds(88L);
        verify(deptScopeMapper, never()).selectRelatedDeptIds(any());
    }

    @Test
    @DisplayName("供应商 related scope 为空时不注入范围并由 SQL fail closed")
    void supplierOptionsShouldFailClosedForEmptyRelatedScope()
    {
        setLoginUser(Set.of("inv:supplier:list"));
        InvMobileMapper mobileMapper = org.mockito.Mockito.mock(InvMobileMapper.class);
        InvDeptScopeMapper deptScopeMapper = authorizedScopeMapper();
        when(deptScopeMapper.selectRelatedDeptIds(88L)).thenReturn(Collections.emptyList());
        when(mobileMapper.selectSupplierOptions(any(), any(), anyInt())).thenReturn(Collections.emptyList());

        service(mobileMapper, deptScopeMapper).selectOptions("supplier", null, null, 88L);

        ArgumentCaptor<Map<String, Object>> params = paramsCaptor();
        verify(mobileMapper).selectSupplierOptions(eq(null), params.capture(), eq(20));
        assertThat(params.getValue()).doesNotContainKey("scopeDeptIds");
    }

    @Test
    @DisplayName("缺供应商列表权限时不解析范围也不执行 Mapper")
    void supplierOptionsShouldCheckPermissionBeforeScopeAndQuery()
    {
        setLoginUser(Set.of("inv:product:list"));
        InvMobileMapper mobileMapper = org.mockito.Mockito.mock(InvMobileMapper.class);
        InvDeptScopeMapper deptScopeMapper = org.mockito.Mockito.mock(InvDeptScopeMapper.class);

        assertThatThrownBy(() -> service(mobileMapper, deptScopeMapper)
                .selectOptions("supplier", null, null, 88L))
                .isInstanceOf(NotPermissionException.class)
                .hasMessage("inv:supplier:list");

        verify(deptScopeMapper, never()).countUserShopScope(any(), any());
        verify(deptScopeMapper, never()).selectRelatedDeptIds(any());
        verify(mobileMapper, never()).selectSupplierOptions(any(), any(), anyInt());
    }

    @Test
    @DisplayName("未授权组织在 related scope 解析前拒绝且不执行 Mapper")
    void supplierOptionsShouldRejectUnauthorizedSelectedDept()
    {
        setLoginUser(Set.of("inv:supplier:list"));
        InvMobileMapper mobileMapper = org.mockito.Mockito.mock(InvMobileMapper.class);
        InvDeptScopeMapper deptScopeMapper = org.mockito.Mockito.mock(InvDeptScopeMapper.class);
        when(deptScopeMapper.countUserShopScope(7L, 88L)).thenReturn(0);

        assertThatThrownBy(() -> service(mobileMapper, deptScopeMapper)
                .selectOptions("supplier", null, null, 88L))
                .isInstanceOf(ServiceException.class)
                .hasMessage("当前用户无权选择该店铺");

        verify(deptScopeMapper, never()).selectRelatedDeptIds(any());
        verify(mobileMapper, never()).selectSupplierOptions(any(), any(), anyInt());
    }

    @Test
    @DisplayName("供应商 SQL 保留启用合作过滤与空范围拒绝")
    void supplierSqlShouldKeepStatusFiltersAndFailClosed() throws Exception
    {
        String mapperXml = resourceText("mapper/inventory/InvMobileMapper.xml");
        String supplierSql = statementXml(mapperXml, "selectSupplierOptions");
        String supplierScope = sqlFragmentXml(mapperXml, "ScopeBySupplierDept");

        assertThat(supplierSql)
                .contains("s.status = '0'")
                .contains("s.cooperation_status is null or s.cooperation_status = '0'")
                .contains("<include refid=\"ScopeBySupplierDept\"/>");
        assertThat(supplierScope)
                .contains("params.scopeDeptIds != null and params.scopeDeptIds.size() > 0")
                .contains("<otherwise>and 1 = 0</otherwise>");
    }

    private static InvMobileServiceImpl service(InvMobileMapper mobileMapper, InvDeptScopeMapper deptScopeMapper)
    {
        InvMobileServiceImpl service = new InvMobileServiceImpl();
        ReflectionTestUtils.setField(service, "mobileMapper", mobileMapper);
        ReflectionTestUtils.setField(service, "deptScopeMapper", deptScopeMapper);
        return service;
    }

    private static InvDeptScopeMapper authorizedScopeMapper()
    {
        InvDeptScopeMapper deptScopeMapper = org.mockito.Mockito.mock(InvDeptScopeMapper.class);
        when(deptScopeMapper.countUserShopScope(7L, 88L)).thenReturn(1);
        return deptScopeMapper;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ArgumentCaptor<Map<String, Object>> paramsCaptor()
    {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
    }

    private static void setLoginUser(Set<String> permissions)
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(SecurityConstants.AUTHORIZATION_HEADER, TokenConstants.PREFIX + "unit-test-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.setUserId("7");

        LoginUser loginUser = new LoginUser();
        loginUser.setUserid(7L);
        loginUser.setUsername("supplier-scope-user");
        loginUser.setRoles(Set.of("user"));
        loginUser.setPermissions(permissions);
        SysUser sysUser = new SysUser();
        sysUser.setUserId(7L);
        sysUser.setUserName("supplier-scope-user");
        loginUser.setSysUser(sysUser);
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);
    }

    private static String resourceText(String resource) throws Exception
    {
        try (InputStream inputStream = Resources.getResourceAsStream(resource))
        {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String statementXml(String mapperXml, String statementId)
    {
        return elementXml(mapperXml, "select", statementId);
    }

    private static String sqlFragmentXml(String mapperXml, String fragmentId)
    {
        return elementXml(mapperXml, "sql", fragmentId);
    }

    private static String elementXml(String mapperXml, String element, String id)
    {
        int start = mapperXml.indexOf("id=\"" + id + "\"");
        assertThat(start).as(id + " id").isGreaterThanOrEqualTo(0);
        int elementStart = mapperXml.lastIndexOf("<" + element, start);
        int elementEnd = mapperXml.indexOf("</" + element + ">", start);
        assertThat(elementStart).as(id + " start").isGreaterThanOrEqualTo(0);
        assertThat(elementEnd).as(id + " end").isGreaterThan(elementStart);
        return mapperXml.substring(elementStart, elementEnd);
    }
}
