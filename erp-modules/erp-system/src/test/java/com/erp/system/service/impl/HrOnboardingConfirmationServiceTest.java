package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Transactional;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.domain.SysUserProfile;
import com.erp.system.api.domain.SysRole;
import com.erp.system.domain.HrOnboarding;
import com.erp.system.domain.HrOnboardingPositionConfig;
import com.erp.system.domain.HrOnboardingOperationLog;
import com.erp.system.domain.vo.HrOnboardingCompletionVo;
import com.erp.system.domain.vo.HrOnboardingConfirmRequest;
import com.erp.system.domain.vo.HrOnboardingConfirmResult;
import com.erp.system.domain.vo.HrOnboardingConflictVo;
import com.erp.system.exception.HrOnboardingValidationException;
import com.erp.system.mapper.HrOnboardingMapper;
import com.erp.system.mapper.HrOnboardingOperationLogMapper;
import com.erp.system.mapper.SysUserMapper;
import com.erp.system.mapper.SysRoleMapper;
import com.erp.system.mapper.SysUserPostMapper;
import com.erp.system.mapper.SysUserProfileMapper;
import com.erp.system.mapper.SysUserRoleMapper;
import com.erp.system.mapper.SysUserShopMapper;
import com.erp.system.service.IHrOnboardingPositionConfigService;
import com.erp.system.service.ISysUserService;
import com.erp.system.service.ISysRoleService;
import com.erp.system.service.ISysUserShopService;
import com.erp.system.service.support.TemporaryCredentialPolicy;
import com.erp.system.support.HrEmployeeNoGenerator;

class HrOnboardingConfirmationServiceTest
{
    private final HrOnboardingMapper onboardingMapper = mock(HrOnboardingMapper.class);
    private final HrOnboardingOperationLogMapper logMapper = mock(HrOnboardingOperationLogMapper.class);
    private final HrOnboardingAccessService accessService = mock(HrOnboardingAccessService.class);
    private final HrOnboardingRuleService rules = mock(HrOnboardingRuleService.class);
    private final IHrOnboardingPositionConfigService configService = mock(IHrOnboardingPositionConfigService.class);
    private final HrOnboardingConflictService conflictService = mock(HrOnboardingConflictService.class);
    private final HrEmployeeNoGenerator employeeNoGenerator = mock(HrEmployeeNoGenerator.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysRoleMapper systemRoleMapper = mock(SysRoleMapper.class);
    private final SysUserProfileMapper profileMapper = mock(SysUserProfileMapper.class);
    private final SysUserRoleMapper roleMapper = mock(SysUserRoleMapper.class);
    private final SysUserPostMapper postMapper = mock(SysUserPostMapper.class);
    private final SysUserShopMapper shopMapper = mock(SysUserShopMapper.class);
    private final ISysUserShopService userShopService = mock(ISysUserShopService.class);
    private final ISysUserService userService = mock(ISysUserService.class);
    private final ISysRoleService roleService = mock(ISysRoleService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-11T00:00:00Z"), ZoneId.of("UTC"));
    private HrOnboardingConfirmationService service;
    private HrOnboarding stored;

    @BeforeEach
    void setUp()
    {
        service = new HrOnboardingConfirmationService(onboardingMapper, logMapper, accessService, rules,
                configService, conflictService, employeeNoGenerator, userMapper, profileMapper, roleMapper,
                postMapper, shopMapper, userShopService, userService, systemRoleMapper, roleService,
                clock, new TemporaryCredentialPolicy(), () -> 9L, () -> false,
                () -> "A1b2C3d4E5f6G7h8", raw -> "HASHED-CREDENTIAL");
        stored = readyOnboarding();
        when(accessService.findScoped(any())).thenReturn(stored);
        when(accessService.lockScopedForUpdate(any())).thenReturn(stored);
        when(configService.resolveActive(30L, "FORMAL")).thenReturn(activeConfig());
        when(rules.evaluateConfirm(any(), any())).thenReturn(new HrOnboardingCompletionVo());
        when(conflictService.findConflicts(any())).thenReturn(Collections.emptyList());
        when(employeeNoGenerator.generate(42L, entryDate())).thenReturn("E000042");
        when(profileMapper.selectActivePostCodeById(30L)).thenReturn("P100");
        when(userMapper.insertUser(any())).thenAnswer(invocation -> {
            invocation.<SysUser>getArgument(0).setUserId(100L);
            return 1;
        });
        when(profileMapper.insertUserProfile(any())).thenReturn(1);
        when(profileMapper.updateOnboardingProfile(any())).thenReturn(1);
        when(userMapper.updateHrEmployeeProfileUser(any())).thenReturn(1);
        when(userService.updateHrEmployeeProfile(any())).thenReturn(1);
        when(onboardingMapper.confirmOnboardingByVersion(any())).thenReturn(1);
        when(accessService.lockGlobalOpenIdentitySetForUpdate(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(systemRoleMapper.selectRoleByIdForUpdate(7L)).thenReturn(activeRole(7L));
        when(roleMapper.insertUserRoleIfAbsent(anyLong(), anyLong())).thenReturn(1);
    }

    @Test
    void createsAccountProfileAndConfiguredRelationsWithOneTimePassword()
    {
        HrOnboardingConfirmResult result = service.confirm(42L, request("key-new", "CREATE_NEW", null), "hr");

        assertThat(result.getUserId()).isEqualTo(100L);
        assertThat(result.getEmployeeNo()).isEqualTo("E000042");
        assertThat(result.getOneTimePassword()).isEqualTo("A1b2C3d4E5f6G7h8");
        assertThat(result.getOneTimePasswordExpiresAt())
                .isEqualTo(Date.from(Instant.parse("2026-07-12T00:00:00Z")));
        assertThat(result.isReplayed()).isFalse();
        ArgumentCaptor<SysUser> user = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).insertUser(user.capture());
        assertThat(user.getValue().getUserName()).isEqualTo("13800008000");
        assertThat(user.getValue().getPhonenumber()).isEqualTo("13800008000");
        assertThat(user.getValue().getPassword()).isEqualTo("HASHED-CREDENTIAL");
        assertThat(user.getValue().getPassword()).doesNotContain(result.getOneTimePassword());
        assertThat(user.getValue().getPwdUpdateDate()).isNull();
        assertThat(user.getValue().getCredentialState()).isEqualTo(SysUser.CREDENTIAL_STATE_TEMPORARY);
        assertThat(user.getValue().getTemporaryPasswordExpiresAt())
                .isEqualTo(Date.from(Instant.parse("2026-07-12T00:00:00Z")));
        assertThat(user.getValue().getStatus()).isEqualTo("0");
        assertThat(user.getValue().getRemark()).isEqualTo("入职来源备注");
        ArgumentCaptor<SysUserProfile> profile = ArgumentCaptor.forClass(SysUserProfile.class);
        verify(profileMapper).insertUserProfile(profile.capture());
        assertThat(profile.getValue().getEntryDate()).isEqualTo(entryDate());
        assertThat(profile.getValue().getPositionNo()).isEqualTo("P100-E000042");
        assertThat(profile.getValue().getEmployeeStatus()).isEqualTo("试用");
        assertThat(profile.getValue().getPlannedRegularizationDate()).isEqualTo(date("2026-10-11T00:00:00Z"));
        assertThat(profile.getValue().getDirectSupervisorUserId()).isEqualTo(77L);
        verify(roleMapper).insertUserRoleIfAbsent(100L, 7L);
        verify(postMapper).insertUserPostIfAbsent(100L, 30L);
        verify(userShopService).saveUserShops(100L, new Long[] { 20L }, "hr", 9L, false);
        verify(onboardingMapper).confirmOnboardingByVersion(stored);
        assertThat(stored.getConfirmIdempotencyKey()).isEqualTo("key-new");
        assertThat(stored.getAccountConfigurationStatus()).isEqualTo("CONFIGURED");
        ArgumentCaptor<HrOnboardingOperationLog> audit = ArgumentCaptor.forClass(HrOnboardingOperationLog.class);
        verify(logMapper).insertOperationLog(audit.capture());
        assertThat(audit.getValue().getDecisionSummary()).doesNotContain(result.getOneTimePassword());
        assertThat(audit.getValue().getOperationSummary()).doesNotContain(result.getOneTimePassword());
    }

    @Test
    void rejectsInvalidPhoneBeforeGeneratingEmployeeNumberOrWritingAccount()
    {
        stored.setPhoneNumber("1380000800");

        assertThatThrownBy(() -> service.confirm(42L,
                request("invalid-login-phone", "CREATE_NEW", null), "hr"))
                .isInstanceOf(HrOnboardingValidationException.class)
                .extracting("errorCode").isEqualTo("ACCOUNT_LOGIN_PHONE_INVALID");

        verify(employeeNoGenerator, never()).generate(anyLong(), any());
        verify(userMapper, never()).insertUser(any());
    }

    @Test
    void rejectsPhoneAlreadyUsedAsLoginAccount()
    {
        SysUser occupied = new SysUser();
        occupied.setUserId(88L);
        when(userMapper.checkUserNameUnique("13800008000")).thenReturn(occupied);

        assertThatThrownBy(() -> service.confirm(42L,
                request("occupied-login-phone", "CREATE_NEW", null), "hr"))
                .isInstanceOf(HrOnboardingValidationException.class)
                .extracting("errorCode").isEqualTo("ACCOUNT_LOGIN_PHONE_CONFLICT");

        verify(employeeNoGenerator, never()).generate(anyLong(), any());
        verify(userMapper, never()).insertUser(any());
    }

    @Test
    void rejectsPhoneAlreadyStoredOnAnotherAccount()
    {
        SysUser occupied = new SysUser();
        occupied.setUserId(88L);
        when(userMapper.checkPhoneUnique("13800008000")).thenReturn(occupied);

        assertThatThrownBy(() -> service.confirm(42L,
                request("occupied-system-phone", "CREATE_NEW", null), "hr"))
                .isInstanceOf(HrOnboardingValidationException.class)
                .extracting("errorCode").isEqualTo("ACCOUNT_LOGIN_PHONE_CONFLICT");

        verify(employeeNoGenerator, never()).generate(anyLong(), any());
        verify(userMapper, never()).insertUser(any());
    }

    @Test
    void revalidatesLockedTargetsBeforeCreatingFormalRelations()
    {
        service.confirm(42L, request("target-validation", "CREATE_NEW", null), "hr");

        verify(accessService).validateTargets(stored);
    }

    @Test
    void derivesOptionalBirthDateFromAValidMainlandIdCard()
    {
        stored.setIdType("居民身份证");
        stored.setIdNumber("330102199001011234");
        stored.setBirthDate(null);

        service.confirm(42L, request("derive-birth-date", "CREATE_NEW", null), "hr");

        Date expected = date("1990-01-01T00:00:00Z");
        ArgumentCaptor<SysUserProfile> profile = ArgumentCaptor.forClass(SysUserProfile.class);
        verify(profileMapper).insertUserProfile(profile.capture());
        assertThat(profile.getValue().getBirthDate()).isEqualTo(expected);
        assertThat(stored.getBirthDate()).isEqualTo(expected);
        verify(onboardingMapper).confirmOnboardingByVersion(stored);
    }

    @Test
    void locksGlobalOpenIdentitySetAfterAuthorizationSnapshotAndBeforeSelectedRow()
    {
        String sourceEmployeeNo = stored.getEmployeeNo();
        service.confirm(42L, request("lock-order", "CREATE_NEW", null), "hr");

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(accessService);
        order.verify(accessService).findScoped(any());
        order.verify(accessService).lockGlobalOpenIdentitySetForUpdate(
                eq(stored.getPhoneNumber()), eq(stored.getIdNumber()), eq(sourceEmployeeNo));
        order.verify(accessService).lockScopedForUpdate(any());
    }

    @Test
    void bindsExistingAccountWithoutChangingCredentialsAndReplacesCurrentPost()
    {
        SysUser existing = new SysUser();
        existing.setUserId(88L);
        existing.setUserName("legacy-login");
        existing.setPassword("legacy-hash");
        existing.setStatus("0");
        existing.setPhonenumber(stored.getPhoneNumber());
        SysUserProfile existingProfile = new SysUserProfile();
        existingProfile.setUserId(88L);
        existingProfile.setEmployeeNo("LEGACY-88");
        existingProfile.setFirstEducation("本科");
        existingProfile.setNationality("中国");
        existingProfile.setHealthStatus("健康");
        existingProfile.setRecruitmentChannel("内推");
        existingProfile.setContractEndDate(date("2028-07-10T00:00:00Z"));
        existingProfile.setActualRegularizationDate(date("2020-10-01T00:00:00Z"));
        existingProfile.setAttendanceMethod("排班");
        existingProfile.setSocialSecurityLocation("上海");
        existingProfile.setHousingFundLocation("上海");
        existingProfile.setLeaveDate(date("2025-01-01T00:00:00Z"));
        existingProfile.setBirthDate(date("1990-01-01T00:00:00Z"));
        existingProfile.setMaritalStatus("已婚");
        existingProfile.setEthnicity("汉族");
        existingProfile.setEmergencyContact("家属");
        existingProfile.setEmergencyContactRelation("配偶");
        existingProfile.setEmergencyContactPhone("13900139000");
        existingProfile.setWorkCityLevel("一线");
        existingProfile.setLegalEntity("原法人单位");
        existing.setProfile(existingProfile);
        when(accessService.lockScopedBindCandidateForUpdate(any(), eq(88L), eq(stored.getPhoneNumber()),
                eq(stored.getIdNumber()), eq(stored.getEmployeeNo()))).thenReturn(existing);
        when(profileMapper.selectUserProfileByUserId(88L)).thenReturn(existingProfile);
        HrOnboardingConflictVo eligible = accountConflict(88L, true, true);
        when(conflictService.findConflicts(any())).thenReturn(Collections.singletonList(eligible));
        when(roleMapper.selectRoleIdsByUserId(88L)).thenReturn(Arrays.asList(5L, 7L));
        when(postMapper.selectPostIdsByUserId(88L)).thenReturn(Collections.singletonList(99L));
        when(shopMapper.selectAllShopDeptIdsByUserId(88L)).thenReturn(Collections.singletonList(55L));

        HrOnboardingConfirmResult result = service.confirm(42L, request("key-bind", "BIND_EXISTING", 88L), "hr");

        assertThat(result.getUserId()).isEqualTo(88L);
        assertThat(result.getOneTimePassword()).isNull();
        assertThat(result.getOneTimePasswordExpiresAt()).isNull();
        assertThat(result.getEmployeeNo()).isEqualTo("LEGACY-88");
        assertThat(result.getOneTimePassword()).isNull();
        ArgumentCaptor<SysUser> update = ArgumentCaptor.forClass(SysUser.class);
        verify(userService, times(1)).updateHrEmployeeProfile(update.capture());
        assertThat(update.getValue().getUserName()).isNull();
        assertThat(update.getValue().getPassword()).isNull();
        assertThat(update.getValue().getStatus()).isEqualTo("0");
        assertThat(update.getValue().getRemark()).isEqualTo("入职来源备注");
        assertThat(update.getValue().getProfile()).isNotNull();
        assertThat(update.getValue().getProfile().getEmployeeNo()).isEqualTo("LEGACY-88");
        assertThat(update.getValue().getProfile().getDirectSupervisorUserId()).isEqualTo(77L);
        assertThat(update.getValue().getProfile().getEntryDate()).isEqualTo(entryDate());
        assertThat(update.getValue().getProfile().getFirstEducation()).isEqualTo("本科");
        assertThat(update.getValue().getProfile().getNationality()).isEqualTo("中国");
        assertThat(update.getValue().getProfile().getHealthStatus()).isEqualTo("健康");
        assertThat(update.getValue().getProfile().getRecruitmentChannel()).isEqualTo("内推");
        assertThat(update.getValue().getProfile().getContractEndDate()).isEqualTo(date("2028-07-10T00:00:00Z"));
        assertThat(update.getValue().getProfile().getActualRegularizationDate())
                .isEqualTo(date("2020-10-01T00:00:00Z"));
        assertThat(update.getValue().getProfile().getAttendanceMethod()).isEqualTo("排班");
        assertThat(update.getValue().getProfile().getSocialSecurityLocation()).isEqualTo("上海");
        assertThat(update.getValue().getProfile().getHousingFundLocation()).isEqualTo("上海");
        assertThat(update.getValue().getProfile().getLeaveDate()).isEqualTo(date("2025-01-01T00:00:00Z"));
        assertThat(update.getValue().getProfile().getBirthDate()).isEqualTo(date("1990-01-01T00:00:00Z"));
        assertThat(update.getValue().getProfile().getMaritalStatus()).isEqualTo("已婚");
        assertThat(update.getValue().getProfile().getEthnicity()).isEqualTo("汉族");
        assertThat(update.getValue().getProfile().getEmergencyContact()).isEqualTo("家属");
        assertThat(update.getValue().getProfile().getEmergencyContactRelation()).isEqualTo("配偶");
        assertThat(update.getValue().getProfile().getEmergencyContactPhone()).isEqualTo("13900139000");
        assertThat(update.getValue().getProfile().getWorkCityLevel()).isEqualTo("一线");
        assertThat(update.getValue().getProfile().getLegalEntity()).isEqualTo("原法人单位");
        verify(userService).checkUserAllowed(existing);
        verify(userMapper, never()).updateHrEmployeeProfileUser(any());
        verify(profileMapper, never()).updateOnboardingProfile(any());
        verify(profileMapper, never()).updateUserProfile(any());
        verify(profileMapper, never()).insertUserProfile(any());
        verify(userMapper, never()).updateUser(any());
        verify(userMapper, never()).resetUserPwd(anyLong(), any(), any());
        verify(roleMapper, never()).deleteUserRoleByUserId(anyLong());
        verify(postMapper).deleteUserPostByUserId(88L);
        verify(shopMapper, never()).deleteUserShopByUserId(anyLong());
        verify(roleMapper, never()).insertUserRoleIfAbsent(88L, 7L);
        verify(postMapper).insertUserPostIfAbsent(88L, 30L);
        verify(shopMapper).insertUserShopIfAbsent(88L, 20L, "hr");
        assertThat(existing.getUserName()).isEqualTo("legacy-login");
        assertThat(existing.getPassword()).isEqualTo("legacy-hash");
    }

    @Test
    void bindingAccountWithoutProfileUsesAggregateBoundaryToCreateFormalProfile()
    {
        SysUser existing = new SysUser();
        existing.setUserId(88L);
        existing.setUserName("legacy-login");
        existing.setPassword("legacy-hash");
        existing.setStatus("0");
        existing.setPhonenumber(stored.getPhoneNumber());
        when(accessService.lockScopedBindCandidateForUpdate(any(), eq(88L), eq(stored.getPhoneNumber()),
                eq(stored.getIdNumber()), eq(stored.getEmployeeNo()))).thenReturn(existing);
        when(profileMapper.selectUserProfileByUserId(88L)).thenReturn(null);
        when(conflictService.findConflicts(any())).thenReturn(
                Collections.singletonList(accountConflict(88L, true, true)));
        when(roleMapper.selectRoleIdsByUserId(88L)).thenReturn(Collections.emptyList());
        when(postMapper.selectPostIdsByUserId(88L)).thenReturn(Collections.emptyList());
        when(shopMapper.selectAllShopDeptIdsByUserId(88L)).thenReturn(Collections.emptyList());

        service.confirm(42L, request("key-bind-new-profile", "BIND_EXISTING", 88L), "hr");

        ArgumentCaptor<SysUser> update = ArgumentCaptor.forClass(SysUser.class);
        verify(userService, times(1)).updateHrEmployeeProfile(update.capture());
        assertThat(update.getValue().getUserId()).isEqualTo(88L);
        assertThat(update.getValue().getProfile().getEmployeeNo()).isEqualTo("E000042");
        assertThat(update.getValue().getProfile().getDirectSupervisorUserId()).isEqualTo(77L);
        verify(userMapper, never()).updateHrEmployeeProfileUser(any());
        verify(profileMapper, never()).updateOnboardingProfile(any());
        verify(profileMapper, never()).updateUserProfile(any());
        verify(profileMapper, never()).insertUserProfile(any());
    }

    @Test
    void rehireRestoresOriginalAccountAndEmployeeNumberWithFreshAccessRelations()
    {
        SysUser existing = new SysUser();
        existing.setUserId(88L);
        existing.setUserName("legacy-login");
        existing.setPassword("legacy-hash");
        existing.setNickName(stored.getEmployeeName());
        existing.setStatus("1");
        existing.setDelFlag("0");
        existing.setPhonenumber(stored.getPhoneNumber());
        SysUserProfile existingProfile = new SysUserProfile();
        existingProfile.setUserId(88L);
        existingProfile.setEmployeeNo("LEGACY-88");
        existingProfile.setEmployeeStatus("离职");
        existingProfile.setLeaveDate(date("2025-01-01T00:00:00Z"));
        existingProfile.setContractEndDate(date("2025-01-01T00:00:00Z"));
        existingProfile.setActualRegularizationDate(date("2020-10-01T00:00:00Z"));
        existing.setProfile(existingProfile);
        when(accessService.lockScopedBindCandidateForUpdate(any(), eq(88L), eq(stored.getPhoneNumber()),
                eq(stored.getIdNumber()), eq(stored.getEmployeeNo()))).thenReturn(existing);
        HrOnboardingConflictVo eligible = new HrOnboardingConflictVo();
        eligible.setCandidateUserId(88L);
        eligible.setEligibleForRehire(true);
        eligible.setBlocking(true);
        eligible.setAllowedDecisions(Collections.singletonList("REHIRE_EXISTING"));
        when(conflictService.findConflicts(any())).thenReturn(Collections.singletonList(eligible));
        when(postMapper.insertUserPostIfAbsent(88L, 30L)).thenReturn(1);
        when(shopMapper.insertUserShopIfAbsent(88L, 20L, "hr")).thenReturn(1);

        HrOnboardingConfirmResult result = service.confirm(42L,
                request("key-rehire", "REHIRE_EXISTING", 88L), "hr");

        assertThat(result.getUserId()).isEqualTo(88L);
        assertThat(result.getEmployeeNo()).isEqualTo("LEGACY-88");
        assertThat(result.getAccountStatus()).isEqualTo("ENABLED");
        assertThat(result.getOneTimePassword()).isNull();
        ArgumentCaptor<SysUser> update = ArgumentCaptor.forClass(SysUser.class);
        verify(userService).updateHrEmployeeProfile(update.capture());
        assertThat(update.getValue().getUserName()).isNull();
        assertThat(update.getValue().getPassword()).isNull();
        assertThat(update.getValue().getStatus()).isEqualTo("0");
        assertThat(update.getValue().getProfile().getEmployeeNo()).isEqualTo("LEGACY-88");
        assertThat(update.getValue().getProfile().getLeaveDate()).isNull();
        assertThat(update.getValue().getProfile().getContractEndDate()).isNull();
        assertThat(update.getValue().getProfile().getActualRegularizationDate()).isNull();
        verify(roleMapper).deleteUserRoleByUserId(88L);
        verify(postMapper).deleteUserPostByUserId(88L);
        verify(shopMapper).deleteUserShopByUserId(88L);
        verify(roleMapper).insertUserRoleIfAbsent(88L, 7L);
        verify(postMapper).insertUserPostIfAbsent(88L, 30L);
        verify(shopMapper).insertUserShopIfAbsent(88L, 20L, "hr");
        verify(userMapper, never()).insertUser(any());
        assertThat(existing.getUserName()).isEqualTo("legacy-login");
        assertThat(existing.getPassword()).isEqualTo("legacy-hash");
    }

    @Test
    void missingConfigurationBindingKeepsAggregateAccountDisabled()
    {
        when(configService.resolveActive(30L, "FORMAL")).thenReturn(null);
        SysUser existing = new SysUser();
        existing.setUserId(88L);
        existing.setStatus("0");
        existing.setPhonenumber(stored.getPhoneNumber());
        when(accessService.lockScopedBindCandidateForUpdate(any(), eq(88L), eq(stored.getPhoneNumber()),
                eq(stored.getIdNumber()), eq(stored.getEmployeeNo()))).thenReturn(existing);
        SysUserProfile existingProfile = new SysUserProfile();
        existingProfile.setUserId(88L);
        existingProfile.setEmployeeNo("LEGACY-88");
        when(profileMapper.selectUserProfileByUserId(88L)).thenReturn(existingProfile);
        when(conflictService.findConflicts(any())).thenReturn(
                Collections.singletonList(accountConflict(88L, true, true)));

        HrOnboardingConfirmResult result = service.confirm(42L,
                request("key-bind-missing-config", "BIND_EXISTING", 88L), "hr");

        ArgumentCaptor<SysUser> update = ArgumentCaptor.forClass(SysUser.class);
        verify(userService).updateHrEmployeeProfile(update.capture());
        assertThat(update.getValue().getStatus()).isEqualTo("1");
        assertThat(update.getValue().getProfile().getEmployeeStatus()).isEqualTo("正式");
        assertThat(result.getAccountStatus()).isEqualTo("DISABLED");
        assertThat(result.getRiskCodes()).containsExactly("ACCOUNT_CONFIGURATION_MISSING");
        verify(userMapper, never()).updateHrEmployeeProfileUser(any());
        verify(profileMapper, never()).updateOnboardingProfile(any());
    }

    @Test
    void sameKeyReplayReturnsLinkedDataWithoutPasswordAndDoesNotCreateAgain()
    {
        HrOnboarding confirmed = readyOnboarding();
        confirmed.setStatus(HrOnboarding.STATUS_CONFIRMED);
        confirmed.setConfirmIdempotencyKey("same-key");
        confirmed.setLinkedUserId(88L);
        confirmed.setEmployeeNo("E88");
        confirmed.setAccountConfigurationStatus("CONFIGURED");
        when(accessService.lockScopedForUpdate(any())).thenReturn(confirmed);
        SysUser linked = new SysUser();
        linked.setUserId(88L);
        linked.setStatus("0");
        when(userMapper.selectUserById(88L)).thenReturn(linked);

        HrOnboardingConfirmResult result = service.confirm(42L, request("same-key", "CREATE_NEW", null), "hr");

        assertThat(result.isReplayed()).isTrue();
        assertThat(result.getOneTimePassword()).isNull();
        assertThat(result.getUserId()).isEqualTo(88L);
        verify(userMapper, never()).insertUser(any());
        verify(profileMapper, never()).insertUserProfile(any());
    }

    @Test
    void differentKeyAgainstConfirmedReturnsStableConflictCode()
    {
        stored.setStatus(HrOnboarding.STATUS_CONFIRMED);
        stored.setConfirmIdempotencyKey("first-key");

        assertThatThrownBy(() -> service.confirm(42L, request("other-key", "CREATE_NEW", null), "hr"))
                .isInstanceOf(HrOnboardingValidationException.class)
                .extracting("errorCode").isEqualTo("ONBOARDING_ALREADY_CONFIRMED");
        verify(userMapper, never()).insertUser(any());
    }

    @Test
    void concurrentSameKeyCallsCreateExactlyOneUser() throws Exception
    {
        HrOnboarding confirmed = readyOnboarding();
        confirmed.setStatus(HrOnboarding.STATUS_CONFIRMED);
        confirmed.setConfirmIdempotencyKey("concurrent-key");
        confirmed.setLinkedUserId(100L);
        confirmed.setEmployeeNo("E000042");
        AtomicInteger locks = new AtomicInteger();
        CountDownLatch firstConfirmed = new CountDownLatch(1);
        when(accessService.lockScopedForUpdate(any())).thenAnswer(invocation -> {
            if (locks.getAndIncrement() == 0) return stored;
            assertThat(firstConfirmed.await(5, TimeUnit.SECONDS)).isTrue();
            return confirmed;
        });
        when(onboardingMapper.confirmOnboardingByVersion(any())).thenAnswer(invocation -> {
            firstConfirmed.countDown();
            return 1;
        });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try
        {
            Future<HrOnboardingConfirmResult> left = executor.submit(() -> service.confirm(42L,
                    request("concurrent-key", "CREATE_NEW", null), "hr"));
            Future<HrOnboardingConfirmResult> right = executor.submit(() -> service.confirm(42L,
                    request("concurrent-key", "CREATE_NEW", null), "hr"));
            assertThat(Arrays.asList(left.get(5, TimeUnit.SECONDS), right.get(5, TimeUnit.SECONDS)))
                    .extracting(HrOnboardingConfirmResult::isReplayed).containsExactlyInAnyOrder(false, true);
            assertThat(Arrays.asList(left.get(), right.get()))
                    .filteredOn(HrOnboardingConfirmResult::isReplayed)
                    .allSatisfy(result -> assertThat(result.getOneTimePassword()).isNull());
            verify(userMapper, times(1)).insertUser(any());
        }
        finally
        {
            executor.shutdownNow();
        }
    }

    @Test
    void profileFailurePropagatesBeforeOnboardingConfirmationForTransactionRollback()
            throws Exception
    {
        doThrow(new IllegalStateException("profile write failed")).when(profileMapper).insertUserProfile(any());

        assertThatThrownBy(() -> service.confirm(42L, request("rollback-key", "CREATE_NEW", null), "hr"))
                .isInstanceOf(IllegalStateException.class).hasMessage("profile write failed");
        verify(onboardingMapper, never()).confirmOnboardingByVersion(any());
        Transactional tx = HrOnboardingConfirmationService.class
                .getMethod("confirm", Long.class, HrOnboardingConfirmRequest.class, String.class)
                .getAnnotation(Transactional.class);
        assertThat(tx).isNotNull();
        assertThat(tx.rollbackFor()).containsExactly(Exception.class);
    }

    @Test
    void blockingPhoneOrIdConflictRejectsCreateNew()
    {
        when(conflictService.findConflicts(any())).thenReturn(Collections.singletonList(accountConflict(88L, true, true)));

        assertThatThrownBy(() -> service.confirm(42L, request("conflict-key", "CREATE_NEW", null), "hr"))
                .isInstanceOf(HrOnboardingValidationException.class)
                .extracting("errorCode").isEqualTo("ONBOARDING_CONFLICT_BLOCKING");
        verify(userMapper, never()).insertUser(any());
    }

    @Test
    void rejectsBindingCandidateThatWasNotEligibleInScopedPreview()
    {
        when(conflictService.findConflicts(any())).thenReturn(Collections.singletonList(accountConflict(88L, false, true)));

        assertThatThrownBy(() -> service.confirm(42L, request("bind-key", "BIND_EXISTING", 88L), "hr"))
                .isInstanceOf(HrOnboardingValidationException.class)
                .extracting("errorCode").isEqualTo("ONBOARDING_BIND_NOT_ELIGIBLE");
        verify(userMapper, never()).selectUserById(anyLong());
    }

    @Test
    void protectedAdministratorCannotBeBoundOrDisabledWhenConfigurationIsMissing()
    {
        when(configService.resolveActive(30L, "FORMAL")).thenReturn(null);
        SysUser administrator = new SysUser();
        administrator.setUserId(1L);
        administrator.setStatus("0");
        administrator.setPhonenumber(stored.getPhoneNumber());
        when(conflictService.findConflicts(any())).thenReturn(
                Collections.singletonList(accountConflict(1L, true, true)));
        when(accessService.lockScopedBindCandidateForUpdate(any(), eq(1L), eq(stored.getPhoneNumber()),
                eq(stored.getIdNumber()), eq(stored.getEmployeeNo()))).thenReturn(administrator);
        doThrow(new com.erp.common.core.exception.ServiceException("不允许操作超级管理员用户"))
                .when(userService).checkUserAllowed(administrator);

        assertThatThrownBy(() -> service.confirm(42L,
                request("admin-bind", "BIND_EXISTING", 1L), "hr"))
                .isInstanceOf(HrOnboardingValidationException.class)
                .extracting("errorCode").isEqualTo("ONBOARDING_BIND_PROTECTED");

        verify(userService, never()).updateHrEmployeeProfile(any());
    }

    @Test
    void bindingRevalidatesLockedCandidateEligibilityAfterPreview()
    {
        SysUser movedOrDisabled = new SysUser();
        movedOrDisabled.setUserId(88L);
        movedOrDisabled.setStatus("1");
        movedOrDisabled.setPhonenumber(stored.getPhoneNumber());
        when(conflictService.findConflicts(any())).thenReturn(
                Collections.singletonList(accountConflict(88L, true, true)));
        when(accessService.lockScopedBindCandidateForUpdate(any(), eq(88L), eq(stored.getPhoneNumber()),
                eq(stored.getIdNumber()), eq(stored.getEmployeeNo()))).thenReturn(movedOrDisabled);

        assertThatThrownBy(() -> service.confirm(42L,
                request("candidate-drift", "BIND_EXISTING", 88L), "hr"))
                .isInstanceOf(HrOnboardingValidationException.class)
                .extracting("errorCode").isEqualTo("ONBOARDING_BIND_NOT_ELIGIBLE");

        verify(userService, never()).updateHrEmployeeProfile(any());
    }

    @Test
    void anotherOpenOnboardingWithSameIdentityBlocksConfirmationAfterLock()
    {
        HrOnboarding draft = readyOnboarding();
        draft.setOnboardingId(43L);
        draft.setStatus(HrOnboarding.STATUS_DRAFT);
        HrOnboarding ready = readyOnboarding();
        ready.setOnboardingId(44L);
        when(accessService.lockGlobalOpenIdentitySetForUpdate(eq(stored.getPhoneNumber()),
                eq(stored.getIdNumber()), eq(stored.getEmployeeNo()))).thenReturn(Arrays.asList(draft, ready));

        assertThatThrownBy(() -> service.confirm(42L,
                request("open-conflict", "CREATE_NEW", null), "hr"))
                .isInstanceOf(HrOnboardingValidationException.class)
                .hasMessageNotContaining(stored.getPhoneNumber())
                .hasMessageNotContaining(draft.getEmployeeName())
                .hasMessageNotContaining("43")
                .hasMessageNotContaining("44")
                .extracting("errorCode").isEqualTo("ONBOARDING_OPEN_CONFLICT");

        verify(userMapper, never()).insertUser(any());
    }

    @Test
    void roleBecomingProtectedAfterConfigurationSaveBlocksConfirmation()
    {
        SysRole admin = activeRole(7L);
        admin.setRoleKey("admin");
        when(systemRoleMapper.selectRoleByIdForUpdate(7L)).thenReturn(admin);
        doThrow(new com.erp.common.core.exception.ServiceException("不允许操作超级管理员角色"))
                .when(roleService).checkRoleAllowed(admin);

        assertThatThrownBy(() -> service.confirm(42L,
                request("admin-role", "CREATE_NEW", null), "hr"))
                .isInstanceOf(HrOnboardingValidationException.class)
                .extracting("errorCode").isEqualTo("ONBOARDING_ROLE_NOT_ALLOWED");
        verify(userMapper, never()).insertUser(any());
    }

    @Test
    void roleMovingOutOfScopeAfterConfigurationSaveBlocksConfirmation()
    {
        doThrow(new com.erp.common.core.exception.ServiceException("无角色数据权限"))
                .when(roleService).checkRoleDataScope(7L);

        assertThatThrownBy(() -> service.confirm(42L,
                request("role-scope", "CREATE_NEW", null), "hr"))
                .isInstanceOf(HrOnboardingValidationException.class)
                .extracting("errorCode").isEqualTo("ONBOARDING_ROLE_NOT_ALLOWED");
        verify(userMapper, never()).insertUser(any());
    }

    @Test
    void disabledConfiguredRoleBlocksConfirmationBeforeAccountWrite()
    {
        SysRole disabled = activeRole(7L);
        disabled.setStatus("1");
        when(systemRoleMapper.selectRoleByIdForUpdate(7L)).thenReturn(disabled);

        assertThatThrownBy(() -> service.confirm(42L,
                request("disabled-role", "CREATE_NEW", null), "hr"))
                .isInstanceOf(HrOnboardingValidationException.class)
                .extracting("errorCode").isEqualTo("ONBOARDING_ROLE_INACTIVE");
        verify(userMapper, never()).insertUser(any());
    }

    @Test
    void zeroRowRoleAssignmentFailsConfirmationInsteadOfSilentlyUnderProvisioning()
    {
        when(roleMapper.insertUserRoleIfAbsent(100L, 7L)).thenReturn(0);

        assertThatThrownBy(() -> service.confirm(42L,
                request("role-write-zero", "CREATE_NEW", null), "hr"))
                .isInstanceOf(HrOnboardingValidationException.class)
                .extracting("errorCode").isEqualTo("ONBOARDING_ROLE_ASSIGNMENT_FAILED");
        verify(onboardingMapper, never()).confirmOnboardingByVersion(any());
    }

    @Test
    void missingPositionConfigCreatesDisabledFormalProfileWithoutRelationsAndRecordsRisk()
    {
        when(configService.resolveActive(30L, "FORMAL")).thenReturn(null);

        HrOnboardingConfirmResult result = service.confirm(42L, request("risk-key", "CREATE_NEW", null), "hr");

        assertThat(result.getAccountStatus()).isEqualTo("DISABLED");
        assertThat(result.getRiskCodes()).containsExactly("ACCOUNT_CONFIGURATION_MISSING");
        ArgumentCaptor<SysUser> user = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).insertUser(user.capture());
        assertThat(user.getValue().getStatus()).isEqualTo("1");
        verify(profileMapper).insertUserProfile(any());
        verify(roleMapper, never()).insertUserRoleIfAbsent(anyLong(), anyLong());
        verify(postMapper, never()).insertUserPostIfAbsent(anyLong(), anyLong());
        verify(userShopService, never()).saveUserShops(anyLong(), any(), any(), anyLong(), eq(false));
        verify(shopMapper, never()).insertUserShopIfAbsent(anyLong(), anyLong(), any());
        assertThat(stored.getAccountRiskCode()).isEqualTo("ACCOUNT_CONFIGURATION_MISSING");
        assertThat(stored.getAccountConfigurationStatus()).isEqualTo("MISSING");
    }

    @Test
    void staleVersionReturnsVersionConflictBeforeAnyWrite()
    {
        HrOnboardingConfirmRequest request = request("stale-key", "CREATE_NEW", null);
        request.setVersion(2);

        assertThatThrownBy(() -> service.confirm(42L, request, "hr"))
                .isInstanceOf(HrOnboardingValidationException.class)
                .extracting("errorCode").isEqualTo("ONBOARDING_VERSION_CONFLICT");
        verify(userMapper, never()).insertUser(any());
    }

    @Test
    void versionUpdateFailureReturnsVersionConflict()
    {
        when(onboardingMapper.confirmOnboardingByVersion(any())).thenReturn(0);

        assertThatThrownBy(() -> service.confirm(42L, request("race-key", "CREATE_NEW", null), "hr"))
                .isInstanceOf(HrOnboardingValidationException.class)
                .extracting("errorCode").isEqualTo("ONBOARDING_VERSION_CONFLICT");
    }

    private HrOnboarding readyOnboarding()
    {
        HrOnboarding row = new HrOnboarding();
        row.setOnboardingId(42L);
        row.setStatus(HrOnboarding.STATUS_READY);
        row.setVersion(3);
        row.setEmployeeName("张三");
        row.setPhoneNumber("13800008000");
        row.setTargetDeptId(10L);
        row.setTargetStoreId(20L);
        row.setTargetPostId(30L);
        row.setDirectSupervisorUserId(77L);
        row.setEmployeeCategory("FORMAL");
        row.setSex("0");
        row.setRemark("入职来源备注");
        row.setActualEntryDate(entryDate());
        return row;
    }

    private HrOnboardingPositionConfig activeConfig()
    {
        HrOnboardingPositionConfig config = new HrOnboardingPositionConfig();
        config.setStatus("0");
        config.setAccountEnabled(true);
        config.setDataScopeStrategy("TARGET_STORE");
        config.setProbationPeriodMode("REQUIRED");
        config.setDefaultProbationPeriod("3个月");
        config.setRoleIds(Collections.singletonList(7L));
        return config;
    }

    private SysRole activeRole(Long roleId)
    {
        SysRole role = new SysRole(roleId);
        role.setRoleKey("employee");
        role.setStatus("0");
        role.setDelFlag("0");
        return role;
    }

    private HrOnboardingConfirmRequest request(String key, String action, Long bindUserId)
    {
        HrOnboardingConfirmRequest request = new HrOnboardingConfirmRequest();
        request.setVersion(3);
        request.setActualEntryDate(entryDate());
        request.setConflictAction(action);
        request.setBindUserId(bindUserId);
        request.setIdempotencyKey(key);
        return request;
    }

    private HrOnboardingConflictVo accountConflict(Long userId, boolean eligible, boolean blocking)
    {
        HrOnboardingConflictVo conflict = new HrOnboardingConflictVo();
        conflict.setSourceType("EMPLOYEE_ACCOUNT");
        conflict.setConflictType("PHONE");
        conflict.setCandidateUserId(userId);
        conflict.setEligibleForBind(eligible);
        conflict.setBlocking(blocking);
        conflict.setAllowedDecisions(eligible
                ? Collections.singletonList("BIND_EXISTING") : Collections.emptyList());
        return conflict;
    }

    private Date entryDate() { return date("2026-07-11T00:00:00Z"); }
    private Date date(String value) { return Date.from(Instant.parse(value)); }
}
