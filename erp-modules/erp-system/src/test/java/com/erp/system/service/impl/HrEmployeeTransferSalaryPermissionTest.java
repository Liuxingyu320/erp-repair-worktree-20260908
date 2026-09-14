package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.auth.NotPermissionException;
import com.erp.common.security.aspect.PreAuthorizeAspect;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.model.LoginUser;
import com.erp.system.controller.HrEmployeeProfileController;
import com.erp.system.domain.dto.HrEmployeeTransferRequest;
import com.erp.system.mapper.*;
import com.erp.system.service.ISysUserShopService;

class HrEmployeeTransferSalaryPermissionTest
{
    private SysConfigMapper config;
    private SysUserProfileMapper profiles;
    private HrEmployeeProfileController controller;
    private MockHttpServletRequest servletRequest;

    @BeforeEach void setUp()
    {
        config = mock(SysConfigMapper.class);
        profiles = mock(SysUserProfileMapper.class);
        when(config.selectConfiguredSignHrUserId()).thenReturn(88L);
        HrLifecycleServiceImpl service = new HrLifecycleServiceImpl(config, profiles,
                mock(SysHrLifecycleActionMapper.class), mock(SysHrSignEventOutboxMapper.class),
                mock(SysHrRenewalGuardMapper.class), mock(SysPostMapper.class), mock(SysDeptMapper.class),
                mock(SysUserMapper.class), mock(SysUserPostMapper.class), mock(ISysUserShopService.class),
                JsonMapper.builder().findAndAddModules().build(), mock(HrSalarySourceService.class));
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(Instant.parse("2026-09-12T00:00:00Z"), ZoneOffset.UTC));
        HrEmployeeProfileController target = new HrEmployeeProfileController();
        ReflectionTestUtils.setField(target, "hrLifecycleService", service);
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(new PreAuthorizeAspect());
        controller = factory.getProxy();
        servletRequest = new MockHttpServletRequest();
        servletRequest.addHeader(SecurityConstants.AUTHORIZATION_HEADER, "Bearer synthetic-transfer-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));
    }

    @AfterEach void clear()
    {
        SecurityContextHolder.remove();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test void salaryWritePermissionCannotReplaceTransferPermission()
    {
        login("hr:employee:salary:edit");
        assertThatThrownBy(() -> controller.confirmTransfer(9L, request(true), servletRequest))
                .isInstanceOf(NotPermissionException.class);
        verifyNoInteractions(config, profiles);
    }

    @Test void sensitiveViewOldSalaryAndSendPermissionsCannotAuthorizeSalaryWrites()
    {
        for (String permission : new String[] { "hr:employee:sensitive:view", "system:salary:edit", "oa:signTask:send" })
        {
            login("hr:employee:transfer", permission);
            assertThatThrownBy(() -> controller.confirmTransfer(9L, request(true), servletRequest))
                    .isInstanceOf(NotPermissionException.class);
        }
        verifyNoInteractions(profiles);
    }

    @Test void ordinaryTransferDoesNotRequireSalaryPermissionOrAmounts()
    {
        login("hr:employee:transfer");
        assertThatThrownBy(() -> controller.confirmTransfer(9L, request(false), servletRequest))
                .hasMessage("员工档案不存在");
        verify(profiles).lockSigningProfileByUserId(9L);
    }

    @Test void bothWritePermissionsEnterTheExistingLockedProfilePath()
    {
        login("hr:employee:transfer", "hr:employee:salary:edit");
        assertThatThrownBy(() -> controller.confirmTransfer(9L, request(true), servletRequest))
                .hasMessage("员工档案不存在");
        verify(profiles).lockSigningProfileByUserId(9L);
    }

    @Test void salaryPermissionDoesNotBypassConfiguredHrIdentity()
    {
        login("hr:employee:transfer", "hr:employee:salary:edit");
        when(config.selectConfiguredSignHrUserId()).thenReturn(99L);
        assertThatThrownBy(() -> controller.confirmTransfer(9L, request(true), servletRequest))
                .hasMessage("仅当前配置HR本人可以执行此操作");
        verifyNoInteractions(profiles);
    }

    private void login(String... permissions)
    {
        SysUser user = new SysUser(88L);
        user.setMustChangePassword("0");
        LoginUser login = new LoginUser();
        login.setUserid(88L); login.setUsername("synthetic-hr"); login.setSysUser(user);
        login.setPermissions(Set.of(permissions)); login.setRoles(Set.of());
        SecurityContextHolder.setUserId("88"); SecurityContextHolder.setUserName("synthetic-hr");
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, login);
    }

    private HrEmployeeTransferRequest request(boolean adjust)
    {
        HrEmployeeTransferRequest request = new HrEmployeeTransferRequest();
        request.setRequestId("synthetic-transfer"); request.setEffectiveDate(LocalDate.of(2026, 9, 12));
        request.setTargetDeptId(20L); request.setTargetDeptName("Synthetic Dept");
        request.setPostId(30L); request.setPostCode("POST"); request.setPostName("Synthetic Post");
        request.setJobGradeCode("P3"); request.setJobGradeName("P3");
        request.setWorkLocation("Synthetic City"); request.setWorkCityLevel("T1");
        request.setLegalEntityId(40L); request.setLegalEntityCode("COMPANY"); request.setLegalEntityName("Synthetic Company");
        request.setAdjustSalary(adjust);
        if (adjust)
        {
            request.setBaseSalary(new BigDecimal("5000.00")); request.setPostSalary(new BigDecimal("1000.00"));
            request.setFieldAllowance(BigDecimal.ZERO); request.setPerformanceSalary(BigDecimal.ZERO);
            request.setSalaryTotal(new BigDecimal("6000.00"));
        }
        return request;
    }
}
