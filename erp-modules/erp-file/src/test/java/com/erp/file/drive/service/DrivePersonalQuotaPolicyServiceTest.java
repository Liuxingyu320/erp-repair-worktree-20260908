package com.erp.file.drive.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;
import java.util.Set;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.domain.DrivePersonalQuotaPolicy;
import com.erp.file.drive.domain.DriveUserQuotaContext;
import com.erp.file.drive.exception.DriveException;
import com.erp.file.drive.mapper.DrivePersonalQuotaPolicyMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("云盘个人额度策略管理")
class DrivePersonalQuotaPolicyServiceTest
{
    private DrivePersonalQuotaPolicyMapper mapper;
    private DriveEffectiveQuotaService effectiveQuotaService;
    private DriveCapacityService capacityService;
    private DriveOrganizationScopeService scopeService;
    private DrivePersonalQuotaPolicyService service;

    @BeforeEach
    void setUp()
    {
        mapper = mock(DrivePersonalQuotaPolicyMapper.class);
        effectiveQuotaService = mock(DriveEffectiveQuotaService.class);
        capacityService = mock(DriveCapacityService.class);
        scopeService = mock(DriveOrganizationScopeService.class);
        when(scopeService.canManage(any(), anyLong())).thenReturn(true);
        service = new DrivePersonalQuotaPolicyService(mapper, effectiveQuotaService,
                capacityService, new DriveAuthorizationService(), scopeService);
    }

    @Test
    @DisplayName("新建个人例外后校验容量并立即对账已有个人盘")
    void shouldCreateUserOverrideAndReconcileAffectedSpace()
    {
        DrivePersonalQuotaPolicy saved = policy(9L, DriveConstants.QUOTA_SUBJECT_USER,
                20L, 5_000L, 0);
        when(mapper.countActiveUser(20L)).thenReturn(1);
        when(mapper.selectActiveUserContexts()).thenReturn(
                java.util.List.of(user(20L, 8L)));
        when(mapper.selectBySubject(DriveConstants.QUOTA_SUBJECT_USER, 20L))
                .thenReturn(null, saved);
        when(mapper.insertPolicy(any())).thenReturn(1);
        when(effectiveQuotaService.affectedUserIds(
                DriveConstants.QUOTA_SUBJECT_USER, 20L)).thenReturn(Set.of(20L));

        DrivePersonalQuotaPolicy result = service.savePolicy(
                "user", 20L, 5_000L, 0, null, 0, "项目资料增加", manager());

        assertThat(result.getQuotaBytes()).isEqualTo(5_000L);
        verify(capacityService).requireCurrentAllocationsWithinPools();
        verify(effectiveQuotaService).reconcileExistingUsers(Set.of(20L), "manager");
    }

    @Test
    @DisplayName("过期版本拒绝覆盖他人的策略修改")
    void shouldRejectStalePolicyVersion()
    {
        DrivePersonalQuotaPolicy existing = policy(9L,
                DriveConstants.QUOTA_SUBJECT_POST, 30L, 5_000L, 10);
        existing.setVersion(4);
        when(mapper.countActivePost(30L)).thenReturn(1);
        when(mapper.selectBySubject(DriveConstants.QUOTA_SUBJECT_POST, 30L))
                .thenReturn(existing);

        assertThatThrownBy(() -> service.savePolicy(
                "POST", 30L, 6_000L, 10, null, 3, "调整岗位额度", manager()))
                .isInstanceOf(DriveException.class)
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_CONCURRENT_MODIFICATION);
        verify(mapper, never()).updatePolicy(any());
        verify(capacityService, never()).requireCurrentAllocationsWithinPools();
    }

    @Test
    @DisplayName("全员默认不可删除且非管理者不可修改")
    void shouldProtectGlobalDefaultAndPermissions()
    {
        assertThatThrownBy(() -> service.deletePolicy(
                DriveConstants.QUOTA_SUBJECT_GLOBAL, 0L, 0, manager()))
                .isInstanceOf(DriveException.class)
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_POLICY_INVALID);

        DriveActor ordinary = new DriveActor(20L, 8L, "财务部", "alice",
                Set.of(DriveConstants.PERMISSION_ACCESS), false);
        assertThatThrownBy(() -> service.savePolicy(
                "USER", 20L, 5_000L, 0, null, 0, "尝试越权", ordinary))
                .isInstanceOf(DriveException.class)
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_ACCESS_DENIED);
    }

    @Test
    @DisplayName("非管理范围内的用户 ID 不能被设置个人例外")
    void shouldRejectUserOutsideOrganizationScope()
    {
        when(mapper.selectActiveUserContexts()).thenReturn(
                java.util.List.of(user(99L, 9L)));
        when(scopeService.canManage(any(), org.mockito.ArgumentMatchers.eq(9L)))
                .thenReturn(false);

        assertThatThrownBy(() -> service.savePolicy("USER", 99L, 5_000L,
                0, null, 0, "越权尝试", manager()))
                .isInstanceOf(DriveException.class)
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_ACCESS_DENIED);
        verify(mapper, never()).insertPolicy(any());
    }

    @Test
    @DisplayName("非管理员只看到管理范围内的个人例外策略")
    void shouldHideUserPoliciesOutsideOrganizationScope()
    {
        DrivePersonalQuotaPolicy global = policy(1L,
                DriveConstants.QUOTA_SUBJECT_GLOBAL, 0L, 1_000L, 0);
        DrivePersonalQuotaPolicy post = policy(2L,
                DriveConstants.QUOTA_SUBJECT_POST, 30L, 2_000L, 10);
        DrivePersonalQuotaPolicy allowed = policy(3L,
                DriveConstants.QUOTA_SUBJECT_USER, 20L, 3_000L, 0);
        DrivePersonalQuotaPolicy denied = policy(4L,
                DriveConstants.QUOTA_SUBJECT_USER, 99L, 4_000L, 0);
        when(mapper.selectAllPolicies()).thenReturn(
                List.of(global, post, allowed, denied));
        when(mapper.selectActiveUserContexts()).thenReturn(
                List.of(user(20L, 8L), user(99L, 9L)));
        when(scopeService.manageableDeptIds(any())).thenReturn(Set.of(8L));

        assertThat(service.listPolicies(manager()))
                .extracting(DrivePersonalQuotaPolicy::getPolicyId)
                .containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("个人临时额度不允许设置已经过去的失效时间")
    void shouldRejectExpiredUserOverride()
    {
        assertThatThrownBy(() -> service.savePolicy("USER", 20L, 5_000L,
                0, new Date(1L), 0, "错误失效时间", manager()))
                .isInstanceOf(DriveException.class)
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_POLICY_INVALID);
        verify(mapper, never()).insertPolicy(any());
    }

    private static DrivePersonalQuotaPolicy policy(Long id, String type,
            Long subjectId, long bytes, int priority)
    {
        DrivePersonalQuotaPolicy value = new DrivePersonalQuotaPolicy();
        value.setPolicyId(id);
        value.setSubjectType(type);
        value.setSubjectId(subjectId);
        value.setQuotaBytes(bytes);
        value.setPriority(priority);
        value.setStatus(DriveConstants.STATUS_ACTIVE);
        value.setVersion(0);
        return value;
    }

    private static DriveActor manager()
    {
        return new DriveActor(1L, 8L, "财务部", "manager", Set.of(
                DriveConstants.PERMISSION_ACCESS,
                DriveConstants.PERMISSION_QUOTA_MANAGE), false);
    }

    private static DriveUserQuotaContext user(Long userId, Long deptId)
    {
        DriveUserQuotaContext value = new DriveUserQuotaContext();
        value.setUserId(userId);
        value.setDeptId(deptId);
        return value;
    }
}
