package com.erp.system.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.system.api.domain.SysUser;
import com.erp.system.domain.vo.SysUserSetupSummaryVo;
import com.erp.system.service.BusinessFeatureGate;
import com.erp.system.service.ISysConfigService;
import com.erp.system.service.ISysUserService;
import com.erp.system.support.SysShopDeptFilterSupport;

class SysUserSetupSummaryControllerTest
{
    @Test
    void setupSummaryIsFeatureGatedScopedAndIgnoresClickedStatusCard() throws Exception
    {
        ISysConfigService configService = mock(ISysConfigService.class);
        when(configService.selectConfigByKey(BusinessFeatureGate.SYSTEM_MANAGEMENT_UX_V2))
                .thenReturn("true");
        BusinessFeatureGate featureGate = new BusinessFeatureGate(configService);
        ISysUserService userService = mock(ISysUserService.class);
        SysShopDeptFilterSupport filterSupport = mock(SysShopDeptFilterSupport.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        SysUser query = new SysUser();
        query.setStatus("1");
        query.setSetupStatus("missingRole");
        query.setNickName("张");
        SysUserSetupSummaryVo summary = new SysUserSetupSummaryVo();
        summary.setTotalCount(7L);
        when(userService.selectUserSetupSummary(query)).thenReturn(summary);
        SysUserController controller = new SysUserController();
        ReflectionTestUtils.setField(controller, "businessFeatureGate", featureGate);
        ReflectionTestUtils.setField(controller, "shopDeptFilterSupport", filterSupport);
        ReflectionTestUtils.setField(controller, "userService", userService);

        AjaxResult result = controller.setupSummary(query, request);

        assertThat(query.getStatus()).isNull();
        assertThat(query.getSetupStatus()).isNull();
        assertThat(query.getNickName()).isEqualTo("张");
        assertThat(result.get("data")).isSameAs(summary);
        verify(filterSupport).applyTo(query, request);
        verify(userService).selectUserSetupSummary(query);
        RequiresPermissions permissions = SysUserController.class
                .getMethod("setupSummary", SysUser.class, HttpServletRequest.class)
                .getAnnotation(RequiresPermissions.class);
        assertThat(permissions).isNotNull();
        assertThat(permissions.value()).containsExactly("system:user:list");
    }
}
