package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignTask;
import com.erp.oa.mapper.OaSignPackageMapper;
import com.erp.oa.mapper.OaSignTaskMapper;

@DisplayName("签约包访问边界")
class OaSignPackageAccessPolicyTest
{
    private final OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
    private final OaSignTaskMapper taskMapper = mock(OaSignTaskMapper.class);
    private final ShopScopeService shopScopeService = mock(ShopScopeService.class);
    private final OaSignHrAccessService hrAccessService = mock(OaSignHrAccessService.class);
    private final OaSignPackageAccessPolicy policy = new OaSignPackageAccessPolicy(
            packageMapper, taskMapper, shopScopeService, hrAccessService);

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("普通HR必须同时满足组织范围和有效任务负责人绑定")
    void ordinaryHrRequiresScopeAndTaskOwnerBinding()
    {
        SecurityContextHolder.setUserId("101");
        OaSignPackage signPackage = taskPackage(90L, 9L, 1171L);
        OaSignTask task = task(9L, 90L, 101L);
        when(packageMapper.selectOaSignPackageById(90L)).thenReturn(signPackage);
        when(shopScopeService.resolveRequiredShopDept(1171L)).thenReturn(1171L);
        when(shopScopeService.resolveScopeDeptIds(1171L)).thenReturn(List.of(1171L));
        when(taskMapper.selectOaSignTaskById(9L)).thenReturn(task);

        assertThat(policy.hrScopedPackage(90L, 1171L)).isSameAs(signPackage);

        verify(hrAccessService).requireTaskOwner(task);
    }

    @Test
    @DisplayName("组织越权在读取任务绑定前失败关闭")
    void scopeViolationFailsBeforeTaskLookup()
    {
        SecurityContextHolder.setUserId("101");
        OaSignPackage signPackage = taskPackage(90L, 9L, 1172L);
        when(packageMapper.selectOaSignPackageById(90L)).thenReturn(signPackage);
        when(shopScopeService.resolveRequiredShopDept(1171L)).thenReturn(1171L);
        when(shopScopeService.resolveScopeDeptIds(1171L)).thenReturn(List.of(1171L));

        assertThatThrownBy(() -> policy.hrScopedPackage(90L, 1171L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("无权访问");

        verify(taskMapper, never()).selectOaSignTaskById(9L);
        verify(hrAccessService, never()).requireTaskOwner(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("管理员可跨组织但不能绕过任务与签约包绑定完整性")
    void adminBypassesScopeButNotBrokenBinding()
    {
        SecurityContextHolder.setUserId("1");
        OaSignPackage signPackage = taskPackage(90L, 9L, 1172L);
        OaSignTask mismatched = task(9L, 91L, 202L);
        when(packageMapper.selectOaSignPackageById(90L)).thenReturn(signPackage);
        when(taskMapper.selectOaSignTaskById(9L)).thenReturn(mismatched);
        doThrow(new ServiceException("签约任务不存在或未分配给当前合同经办人"))
                .when(hrAccessService).requireTaskOwner(null);

        assertThatThrownBy(() -> policy.hrScopedPackage(90L, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("签约任务不存在");

        verify(shopScopeService, never()).resolveRequiredShopDept(
                org.mockito.ArgumentMatchers.any());
        verify(hrAccessService).requireTaskOwner(null);
    }

    @Test
    @DisplayName("员工只能访问自己的签约包")
    void employeePackageRequiresCurrentEmployee()
    {
        SecurityContextHolder.setUserId("960");
        OaSignPackage signPackage = taskPackage(90L, null, 1171L);
        signPackage.setEmployeeId(960L);
        when(packageMapper.selectOaSignPackageById(90L)).thenReturn(signPackage);

        assertThat(policy.employeePackage(90L)).isSameAs(signPackage);

        signPackage.setEmployeeId(961L);
        assertThatThrownBy(() -> policy.employeePackage(90L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("本人的签约包");
    }

    private OaSignPackage taskPackage(Long packageId, Long taskId, Long shopDeptId)
    {
        OaSignPackage value = new OaSignPackage();
        value.setPackageId(packageId);
        value.setTaskId(taskId);
        value.setShopDeptId(shopDeptId);
        return value;
    }

    private OaSignTask task(Long taskId, Long packageId, Long assignedHrUserId)
    {
        OaSignTask value = new OaSignTask();
        value.setTaskId(taskId);
        value.setPackageId(packageId);
        value.setAssignedHrUserId(assignedHrUserId);
        return value;
    }
}
