package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import com.erp.common.core.constant.Constants;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.constant.UserConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.datascope.annotation.DataScope;
import com.erp.common.datascope.aspect.DataScopeAspect;
import com.erp.system.api.domain.SysDept;
import com.erp.system.api.domain.SysRole;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.domain.SysUserProfile;
import com.erp.system.api.model.LoginUser;
import com.erp.system.domain.HrOnboarding;
import com.erp.system.domain.SysPost;
import com.erp.system.domain.vo.HrOnboardingQuery;
import com.erp.system.domain.vo.HrOnboardingOwnerOptionVo;
import com.erp.system.domain.vo.HrOnboardingOwnerQuery;
import com.erp.system.mapper.HrOnboardingMapper;
import com.erp.system.mapper.SysDeptMapper;
import com.erp.system.mapper.SysPostMapper;
import com.erp.system.mapper.SysUserMapper;

@ExtendWith(MockitoExtension.class)
class HrOnboardingAccessServiceTest
{
    @Mock private HrOnboardingMapper onboardingMapper;
    @Mock private SysDeptMapper deptMapper;
    @Mock private SysPostMapper postMapper;
    @Mock private SysUserMapper userMapper;
    @Mock private SysUserProfileDerivationService profileDerivationService;

    private HrOnboardingAccessService service;

    @BeforeEach
    void setUp()
    {
        service = new HrOnboardingAccessService(onboardingMapper, deptMapper, postMapper, userMapper,
                profileDerivationService);
    }

    @Test
    void publicScopeBoundariesAreAnnotatedAndLockUsesScopedForUpdateMapper() throws Exception
    {
        for (String methodName : new String[] { "findScoped", "lockScopedForUpdate", "validateTargets" })
        {
            Method method = methodName.equals("validateTargets")
                    ? HrOnboardingAccessService.class.getMethod(methodName, HrOnboarding.class)
                    : HrOnboardingAccessService.class.getMethod(methodName, HrOnboardingQuery.class);
            assertThat(method.getAnnotation(DataScope.class)).isNotNull();
            assertThat(method.getAnnotation(DataScope.class).deptAlias()).isEqualTo("d");
        }
        for (String methodName : new String[] { "listScopedUserConflicts", "listScopedOnboardingConflicts" })
        {
            Method method = HrOnboardingAccessService.class.getMethod(methodName,
                    HrOnboardingQuery.class, String.class, String.class, String.class);
            assertThat(method.getAnnotation(DataScope.class)).isNotNull();
            assertThat(method.getAnnotation(DataScope.class).deptAlias()).isEqualTo("d");
        }
        for(String methodName:new String[]{"listScopedUserConflictsBatch","listScopedOnboardingConflictsBatch"})
        {
            Method method=HrOnboardingAccessService.class.getMethod(methodName,HrOnboardingQuery.class,List.class);
            assertThat(method.getAnnotation(DataScope.class)).isNotNull();
            assertThat(method.getAnnotation(DataScope.class).deptAlias()).isEqualTo("d");
        }
        Method linkedBatch=HrOnboardingAccessService.class.getMethod("listScopedByLinkedUserIds",
                HrOnboardingQuery.class,List.class);
        assertThat(linkedBatch.getAnnotation(DataScope.class)).isNotNull();
        assertThat(linkedBatch.getAnnotation(DataScope.class).deptAlias()).isEqualTo("d");
        Method candidateLock = HrOnboardingAccessService.class.getMethod("lockScopedBindCandidateForUpdate",
                HrOnboardingQuery.class, Long.class, String.class, String.class, String.class);
        assertThat(candidateLock.getAnnotation(DataScope.class)).isNotNull();
        assertThat(candidateLock.getAnnotation(DataScope.class).deptAlias()).isEqualTo("d");
        Method globalIdentityLock = HrOnboardingAccessService.class.getMethod(
                "lockGlobalOpenIdentitySetForUpdate", String.class, String.class, String.class);
        assertThat(globalIdentityLock.getAnnotation(DataScope.class)).isNull();
        Method ownerOptions = HrOnboardingAccessService.class.getMethod(
                "listScopedOwnerOptions", HrOnboardingOwnerQuery.class);
        assertThat(ownerOptions.getAnnotation(DataScope.class)).isNotNull();
        assertThat(ownerOptions.getAnnotation(DataScope.class).deptAlias()).isEqualTo("d");
        Method ownerDepartment = HrOnboardingAccessService.class.getMethod(
                "validateScopedOwnerDepartment", HrOnboardingOwnerQuery.class);
        assertThat(ownerDepartment.getAnnotation(DataScope.class)).isNotNull();
        assertThat(ownerDepartment.getAnnotation(DataScope.class).deptAlias()).isEqualTo("d");

        HrOnboarding row = onboarding(8L);
        when(onboardingMapper.selectScopedOnboardingByIdForUpdate(any())).thenReturn(row);

        HrOnboardingQuery scopedRequest = new HrOnboardingQuery();
        scopedRequest.setOnboardingId(8L);
        scopedRequest.getParams().put("dataScope", " AND (d.dept_id = 10)");
        assertThat(service.lockScopedForUpdate(scopedRequest)).isSameAs(row);
        ArgumentCaptor<HrOnboardingQuery> query = ArgumentCaptor.forClass(HrOnboardingQuery.class);
        verify(onboardingMapper).selectScopedOnboardingByIdForUpdate(query.capture());
        assertThat(query.getValue().getOnboardingId()).isEqualTo(8L);
        assertThat(query.getValue().getParams().get("dataScope")).isEqualTo(" AND (d.dept_id = 10)");
    }

    @Test
    void ownerOptionsValidatesDepartmentInsideTheSameScopeAndReturnsOnlyMapperProjection()
    {
        HrOnboardingOwnerQuery query = new HrOnboardingOwnerQuery();
        query.setDeptId(20L);
        query.getParams().put("dataScope", " AND d.dept_id = 20");
        SysDept department = new SysDept();
        department.setDeptId(20L);
        department.setStatus("0");
        when(deptMapper.selectDeptList(any())).thenReturn(Collections.singletonList(department));
        HrOnboardingOwnerOptionVo option = new HrOnboardingOwnerOptionVo();
        option.setUserId(9L);
        option.setPhoneMasked("138****8000");
        when(onboardingMapper.selectScopedOwnerOptions(query)).thenReturn(Collections.singletonList(option));

        service.validateScopedOwnerDepartment(query);
        assertThat(service.listScopedOwnerOptions(query)).containsExactly(option);

        ArgumentCaptor<SysDept> scopedDepartment = ArgumentCaptor.forClass(SysDept.class);
        verify(deptMapper).selectDeptList(scopedDepartment.capture());
        assertThat(scopedDepartment.getValue().getDeptId()).isEqualTo(20L);
        assertThat(scopedDepartment.getValue().getStatus()).isEqualTo("0");
        assertThat(scopedDepartment.getValue().getParams().get("dataScope"))
                .isEqualTo(" AND d.dept_id = 20");
        verify(onboardingMapper).selectScopedOwnerOptions(query);
    }

    @Test
    void ownerOptionsFailsClosedWhenRequestedDepartmentIsOutOfScope()
    {
        HrOnboardingOwnerQuery query = new HrOnboardingOwnerQuery();
        query.setDeptId(999L);
        query.getParams().put("dataScope", " AND d.dept_id = 20");
        when(deptMapper.selectDeptList(any())).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> service.validateScopedOwnerDepartment(query))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("无权访问");
        verify(onboardingMapper, never()).selectScopedOwnerOptions(any());
    }

    @Test
    void ownerOptionsReceivesTheCallersDepartmentScopeBeforeTheMapperRuns()
    {
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(service);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAspect(new DataScopeAspect());
        HrOnboardingAccessService proxy = proxyFactory.getProxy();
        when(onboardingMapper.selectScopedOwnerOptions(any())).thenReturn(Collections.emptyList());
        loginAsDepartmentScopedUser();
        try
        {
            proxy.listScopedOwnerOptions(new HrOnboardingOwnerQuery());

            ArgumentCaptor<HrOnboardingOwnerQuery> scoped =
                    ArgumentCaptor.forClass(HrOnboardingOwnerQuery.class);
            verify(onboardingMapper).selectScopedOwnerOptions(scoped.capture());
            assertThat(scoped.getValue().getParams().get("dataScope")).asString()
                    .contains("d.dept_id = 20");
        }
        finally
        {
            SecurityContextHolder.remove();
        }
    }

    @Test
    void scopedLinkedEmployeeBatchReturnsOnlyRowsSelectedInsideTheScopeInNewestFirstOrder()
    {
        HrOnboarding olderInScope=onboarding(40L);olderInScope.setLinkedUserId(7L);olderInScope.setTargetDeptId(10L);
        when(onboardingMapper.selectScopedByLinkedUserIds(any(),org.mockito.ArgumentMatchers.eq(List.of(7L))))
                .thenReturn(List.of(olderInScope));
        HrOnboardingQuery query=new HrOnboardingQuery();
        query.getParams().put("dataScope"," AND d.dept_id = 10");

        List<HrOnboarding> result=service.listScopedByLinkedUserIds(query,List.of(7L));

        assertThat(result).containsExactly(olderInScope);
        verify(onboardingMapper).selectScopedByLinkedUserIds(query,List.of(7L));
    }

    @Test
    void globalIdentityInvariantLockIncludesOpenRowsFromDifferentDepartmentsWithoutReturningPii()
    {
        HrOnboarding local = onboarding(42L);
        local.setStatus(HrOnboarding.STATUS_READY);
        local.setTargetDeptId(10L);
        HrOnboarding otherDepartment = onboarding(43L);
        otherDepartment.setStatus(HrOnboarding.STATUS_DRAFT);
        otherDepartment.setTargetDeptId(999L);
        when(onboardingMapper.selectGlobalOpenIdentitySetForUpdate(
                "13800008000", "110101199001010000", "E88"))
                .thenReturn(Arrays.asList(local, otherDepartment));

        List<HrOnboarding> rows = service.lockGlobalOpenIdentitySetForUpdate(
                "13800008000", "110101199001010000", "E88");

        assertThat(rows).extracting(HrOnboarding::getOnboardingId).containsExactly(42L, 43L);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.getStatus()).isIn(HrOnboarding.STATUS_DRAFT, HrOnboarding.STATUS_READY);
            assertThat(row.getEmployeeName()).isNull();
            assertThat(row.getPhoneNumber()).isNull();
            assertThat(row.getIdNumber()).isNull();
        });
        verify(onboardingMapper).selectGlobalOpenIdentitySetForUpdate(
                "13800008000", "110101199001010000", "E88");
    }

    @Test
    void scopedCandidateLockFailsClosedAndPassesAspectScopeIntoForUpdateMapper()
    {
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(service);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAspect(new DataScopeAspect());
        HrOnboardingAccessService proxy = proxyFactory.getProxy();
        HrOnboardingQuery query = new HrOnboardingQuery();
        query.setOnboardingId(42L);
        SysUser candidate = new SysUser();
        candidate.setUserId(88L);
        when(userMapper.selectScopedOnboardingConflictUserForUpdate(any(),
                org.mockito.ArgumentMatchers.eq(88L), org.mockito.ArgumentMatchers.eq("13800008000"),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(candidate);
        loginAsDepartmentScopedUser();
        try
        {
            assertThat(proxy.lockScopedBindCandidateForUpdate(query, 88L,
                    "13800008000", null, null)).isSameAs(candidate);

            ArgumentCaptor<HrOnboardingQuery> scoped = ArgumentCaptor.forClass(HrOnboardingQuery.class);
            verify(userMapper).selectScopedOnboardingConflictUserForUpdate(scoped.capture(),
                    org.mockito.ArgumentMatchers.eq(88L), org.mockito.ArgumentMatchers.eq("13800008000"),
                    org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull());
            assertThat(scoped.getValue().getParams().get("dataScope")).asString().contains("d.dept_id = 20");
        }
        finally
        {
            SecurityContextHolder.remove();
        }

        when(userMapper.selectScopedOnboardingConflictUserForUpdate(any(),
                org.mockito.ArgumentMatchers.eq(99L), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(null);
        assertThatThrownBy(() -> service.lockScopedBindCandidateForUpdate(new HrOnboardingQuery(),
                99L, "13800008000", null, null))
                .isInstanceOf(ServiceException.class).hasMessageContaining("无权");
    }

    @Test
    void formOptionCatalogsUseDataScopedDepartmentAndUserQueries() throws Exception
    {
        Method departments = HrOnboardingAccessService.class.getMethod("listScopedDepartments", SysDept.class);
        Method users = HrOnboardingAccessService.class.getMethod("listScopedUsers", SysUser.class);
        assertThat(departments.getAnnotation(DataScope.class)).isNotNull();
        assertThat(departments.getAnnotation(DataScope.class).deptAlias()).isEqualTo("d");
        assertThat(users.getAnnotation(DataScope.class)).isNotNull();
        assertThat(users.getAnnotation(DataScope.class).deptAlias()).isEqualTo("d");
        when(deptMapper.selectDeptList(any())).thenReturn(Collections.emptyList());
        when(onboardingMapper.selectScopedUserOptions(any())).thenReturn(Collections.emptyList());

        assertThat(service.listScopedDepartments(new SysDept())).isEmpty();
        assertThat(service.listScopedUsers(new SysUser())).isEmpty();

        ArgumentCaptor<SysDept> deptQuery = ArgumentCaptor.forClass(SysDept.class);
        verify(deptMapper).selectDeptList(deptQuery.capture());
        assertThat(deptQuery.getValue().getStatus()).isEqualTo("0");
        ArgumentCaptor<SysUser> userQuery = ArgumentCaptor.forClass(SysUser.class);
        verify(onboardingMapper).selectScopedUserOptions(userQuery.capture());
        assertThat(userQuery.getValue().getStatus()).isEqualTo("0");
        verify(userMapper, never()).selectUserList(any());
    }

    @Test
    void realDataScopeProxyPopulatesFormOptionQueriesWithoutArgumentErrors()
    {
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(service);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAspect(new DataScopeAspect());
        HrOnboardingAccessService proxy = proxyFactory.getProxy();
        when(deptMapper.selectDeptList(any())).thenReturn(Collections.emptyList());
        when(onboardingMapper.selectScopedUserOptions(any())).thenReturn(Collections.emptyList());
        loginAsDepartmentScopedUser();
        try
        {
            proxy.listScopedDepartments(new SysDept());
            proxy.listScopedUsers(new SysUser());

            ArgumentCaptor<SysDept> departments = ArgumentCaptor.forClass(SysDept.class);
            verify(deptMapper).selectDeptList(departments.capture());
            assertThat(departments.getValue().getParams().get("dataScope")).asString()
                    .contains("d.dept_id = 20");
            ArgumentCaptor<SysUser> users = ArgumentCaptor.forClass(SysUser.class);
            verify(onboardingMapper).selectScopedUserOptions(users.capture());
            assertThat(users.getValue().getParams().get("dataScope")).asString()
                    .contains("d.dept_id = 20");
            verify(userMapper, never()).selectUserList(any());
        }
        finally
        {
            SecurityContextHolder.remove();
        }
    }

    @Test
    void nullFormOptionScopeQueriesFailClosed()
    {
        assertThatThrownBy(() -> service.listScopedDepartments(null))
                .isInstanceOf(ServiceException.class).hasMessageContaining("查询条件");
        assertThatThrownBy(() -> service.listScopedUsers(null))
                .isInstanceOf(ServiceException.class).hasMessageContaining("查询条件");
        verify(deptMapper, never()).selectDeptList(any());
        verify(onboardingMapper, never()).selectScopedUserOptions(any());
        verify(userMapper, never()).selectUserList(any());
    }

    @Test
    void deniedScopedReadAndLockFailClosed()
    {
        when(onboardingMapper.selectOnboardingList(any())).thenReturn(Collections.emptyList());
        when(onboardingMapper.selectScopedOnboardingByIdForUpdate(any())).thenReturn(null);

        HrOnboardingQuery query = new HrOnboardingQuery();
        query.setOnboardingId(99L);
        assertThatThrownBy(() -> service.findScoped(query))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("无权");
        assertThatThrownBy(() -> service.lockScopedForUpdate(query))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("无权");
    }

    @Test
    void validatesEveryTargetWithAspectPopulatedScopeAndGlobalPostCatalog()
    {
        HrOnboarding targets = onboarding(1L);
        targets.setTargetDeptId(10L);
        targets.setTargetStoreId(11L);
        targets.setTargetPostId(12L);
        targets.setDirectSupervisorUserId(13L);
        targets.setOwnerUserId(14L);
        targets.getParams().put("dataScope", " AND (d.dept_id = 10)");

        when(deptMapper.selectDeptList(any())).thenAnswer(invocation -> {
            SysDept query = invocation.getArgument(0);
            SysDept result = new SysDept();
            result.setDeptId(query.getDeptId());
            result.setDeptName(query.getDeptId().equals(10L) ? "人事部" : "厦门店");
            result.setDeptType(query.getDeptId().equals(11L) ? "STORE" : "COMPANY");
            result.setAncestors(query.getDeptId().equals(11L) ? "0,10" : "0");
            result.setStatus("0");
            return Collections.singletonList(result);
        });
        SysPost post = new SysPost();
        post.setPostId(12L);
        post.setPostName("招聘专员");
        post.setStatus("0");
        when(postMapper.selectPostById(12L)).thenReturn(post);
        when(userMapper.selectUserList(any())).thenAnswer(invocation -> {
            SysUser query = invocation.getArgument(0);
            SysUser result = new SysUser();
            result.setUserId(query.getUserId());
            result.setNickName(query.getUserId().equals(13L) ? "主管" : "负责人");
            result.setStatus("0");
            return Collections.singletonList(result);
        });
        SysUserProfile derived = new SysUserProfile();
        derived.setCompanyName("华东公司");
        derived.setDeptLevel1Name("人事部");
        derived.setStoreName("厦门店");
        derived.setPositionNames("招聘专员");
        derived.setDepartmentSupervisor("部门负责人");
        when(profileDerivationService.preview(any())).thenReturn(derived);

        service.validateTargets(targets);

        assertThat(targets.getCompanyName()).isEqualTo("华东公司");
        assertThat(targets.getStoreName()).isEqualTo("厦门店");
        assertThat(targets.getPositionName()).isEqualTo("招聘专员");
        assertThat(targets.getDepartmentSupervisor()).isEqualTo("部门负责人");
        org.mockito.InOrder targetValidationOrder = org.mockito.Mockito.inOrder(
                deptMapper, postMapper, userMapper, profileDerivationService);
        targetValidationOrder.verify(deptMapper, org.mockito.Mockito.times(2)).selectDeptList(any());
        targetValidationOrder.verify(postMapper).selectPostById(12L);
        targetValidationOrder.verify(userMapper, org.mockito.Mockito.times(2)).selectUserList(any());
        targetValidationOrder.verify(profileDerivationService).preview(any());
        ArgumentCaptor<SysDept> deptQueries = ArgumentCaptor.forClass(SysDept.class);
        verify(deptMapper, org.mockito.Mockito.times(2)).selectDeptList(deptQueries.capture());
        assertThat(deptQueries.getAllValues())
                .allSatisfy(q -> assertThat(q.getParams().get("dataScope")).isEqualTo(" AND (d.dept_id = 10)"));
        ArgumentCaptor<SysUser> userQueries = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper, org.mockito.Mockito.times(2)).selectUserList(userQueries.capture());
        assertThat(userQueries.getAllValues())
                .allSatisfy(q -> assertThat(q.getParams().get("dataScope")).isEqualTo(" AND (d.dept_id = 10)"));
    }

    @Test
    void rejectsOutOfScopeTargetsAndMissingOrDisabledGlobalPost()
    {
        HrOnboarding targets = onboarding(1L);
        targets.setTargetDeptId(10L);
        targets.setTargetPostId(12L);

        when(deptMapper.selectDeptList(any())).thenReturn(Collections.emptyList());
        assertThatThrownBy(() -> service.validateTargets(targets))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("目标组织");

        SysDept dept = new SysDept();
        dept.setDeptId(10L);
        dept.setDeptName("人事部");
        dept.setStatus("0");
        when(deptMapper.selectDeptList(any())).thenReturn(Collections.singletonList(dept));
        SysPost disabled = new SysPost();
        disabled.setPostId(12L);
        disabled.setStatus("1");
        when(postMapper.selectPostById(12L)).thenReturn(disabled);
        assertThatThrownBy(() -> service.validateTargets(targets))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("岗位");
        verify(profileDerivationService, never()).preview(any());
    }

    @Test
    void changingOrganizationReplacesEveryDerivedFieldAndClearsStaleValuesAfterScopeValidation()
    {
        HrOnboarding targets = onboarding(1L);
        targets.setTargetDeptId(20L);
        targets.setCompanyName("旧公司");
        targets.setDeptLevel1Name("旧一级");
        targets.setDeptLevel2Name("旧二级");
        targets.setDeptLevel3Name("旧三级");
        targets.setStoreName("旧门店");
        targets.setPositionName("旧岗位");
        targets.setDepartmentSupervisor("旧负责人");
        SysDept authorized = dept(20L, 2L, "0,1,2", "新组织", "DEPARTMENT", null);
        when(deptMapper.selectDeptList(any())).thenReturn(Collections.singletonList(authorized));
        SysUserProfile derived = new SysUserProfile();
        derived.setCompanyName("新公司");
        derived.setDeptLevel1Name("新一级");
        when(profileDerivationService.preview(any())).thenReturn(derived);

        service.validateTargets(targets);

        assertThat(targets.getCompanyName()).isEqualTo("新公司");
        assertThat(targets.getDeptLevel1Name()).isEqualTo("新一级");
        assertThat(targets.getDeptLevel2Name()).isNull();
        assertThat(targets.getDeptLevel3Name()).isNull();
        assertThat(targets.getStoreName()).isNull();
        assertThat(targets.getPositionName()).isNull();
        assertThat(targets.getDepartmentSupervisor()).isNull();
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(deptMapper, profileDerivationService);
        order.verify(deptMapper).selectDeptList(any());
        order.verify(profileDerivationService).preview(any());
    }

    @Test
    void rejectsDepartmentAndStoreFromDifferentOrganizationBranches()
    {
        HrOnboarding targets = onboarding(1L);
        targets.setTargetDeptId(4L);
        targets.setTargetStoreId(8L);
        SysDept branchA = dept(4L, 3L, "0,1,2,3", "A部门", "DEPARTMENT", null);
        SysDept branchB = dept(8L, 7L, "0,1,6,7", "B门店", "STORE", null);
        when(deptMapper.selectDeptList(any())).thenAnswer(invocation -> {
            Long id = ((SysDept) invocation.getArgument(0)).getDeptId();
            return Collections.singletonList(id.equals(4L) ? branchA : branchB);
        });

        assertThatThrownBy(() -> service.validateTargets(targets))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("同一组织链");
        verify(profileDerivationService, never()).preview(any());
    }

    @Test
    void usesTheDeeperAuthorizedNodeForBothLegalDepartmentStoreOrders()
    {
        SysUserProfile derived = new SysUserProfile();
        when(profileDerivationService.preview(any())).thenReturn(derived);
        SysDept department = dept(4L, 3L, "0,1,2,3", "部门", "DEPARTMENT", null);
        SysDept storeBelow = dept(5L, 4L, "0,1,2,3,4", "下级门店", "STORE", null);
        when(deptMapper.selectDeptList(any())).thenAnswer(invocation -> {
            Long id = ((SysDept) invocation.getArgument(0)).getDeptId();
            return Collections.singletonList(id.equals(4L) ? department : storeBelow);
        });
        HrOnboarding storeBelowTargets = onboarding(1L);
        storeBelowTargets.setTargetDeptId(4L);
        storeBelowTargets.setTargetStoreId(5L);

        service.validateTargets(storeBelowTargets);

        SysDept storeAbove = dept(6L, 2L, "0,1,2", "上级门店", "STORE", null);
        SysDept departmentBelow = dept(7L, 6L, "0,1,2,6", "门店下部门", "DEPARTMENT", null);
        org.mockito.Mockito.reset(deptMapper);
        when(deptMapper.selectDeptList(any())).thenAnswer(invocation -> {
            Long id = ((SysDept) invocation.getArgument(0)).getDeptId();
            return Collections.singletonList(id.equals(6L) ? storeAbove : departmentBelow);
        });
        HrOnboarding departmentBelowTargets = onboarding(2L);
        departmentBelowTargets.setTargetDeptId(7L);
        departmentBelowTargets.setTargetStoreId(6L);

        service.validateTargets(departmentBelowTargets);

        ArgumentCaptor<SysUser> projections = ArgumentCaptor.forClass(SysUser.class);
        verify(profileDerivationService, org.mockito.Mockito.times(2)).preview(projections.capture());
        assertThat(projections.getAllValues()).extracting(SysUser::getDeptId).containsExactly(5L, 7L);
    }

    @Test
    void derivesMultiLevelOrganizationFromUnifiedServiceAndNeverUsesDirectSupervisorAsDepartmentLeader()
    {
        List<SysDept> hierarchy = Arrays.asList(
                dept(1L, 0L, "0", "集团", "GROUP", null),
                dept(2L, 1L, "0,1", "华东公司", "COMPANY", null),
                dept(3L, 2L, "0,1,2", "业务中心", "DEPARTMENT", "部门负责人"),
                dept(4L, 3L, "0,1,2,3", "业务二组", "DEPARTMENT", null),
                dept(5L, 4L, "0,1,2,3,4", "厦门店", "STORE", null));
        when(deptMapper.selectDeptList(any())).thenAnswer(invocation -> {
            SysDept query = invocation.getArgument(0);
            if (query.getDeptId() == null) return hierarchy;
            return hierarchy.stream().filter(item -> item.getDeptId().equals(query.getDeptId()))
                    .collect(java.util.stream.Collectors.toList());
        });
        SysPost post = new SysPost();
        post.setPostId(12L); post.setPostName("招商主管"); post.setPostSort(1); post.setStatus("0");
        when(postMapper.selectPostById(12L)).thenReturn(post);
        when(postMapper.selectPostAll()).thenReturn(Collections.singletonList(post));
        when(userMapper.selectUserList(any())).thenAnswer(invocation -> {
            SysUser query = invocation.getArgument(0);
            SysUser result = new SysUser();
            result.setUserId(query.getUserId());
            result.setNickName(query.getUserId().equals(13L) ? "直属主管本人" : "入职负责人");
            result.setStatus("0");
            return Collections.singletonList(result);
        });

        HrOnboardingAccessService unified = new HrOnboardingAccessService(onboardingMapper, deptMapper,
                postMapper, userMapper, new SysUserProfileDerivationService(deptMapper, postMapper,
                        Clock.fixed(Instant.parse("2026-07-11T00:00:00Z"), ZoneOffset.UTC)));
        HrOnboarding targets = onboarding(1L);
        targets.setTargetDeptId(4L);
        targets.setTargetStoreId(5L);
        targets.setTargetPostId(12L);
        targets.setDirectSupervisorUserId(13L);
        targets.setOwnerUserId(14L);
        targets.getParams().put("dataScope", " AND (d.dept_id in (4,5))");

        unified.validateTargets(targets);

        assertThat(targets.getCompanyName()).isEqualTo("华东公司");
        assertThat(targets.getDeptLevel1Name()).isNull();
        assertThat(targets.getDeptLevel2Name()).isEqualTo("业务中心");
        assertThat(targets.getDeptLevel3Name()).isEqualTo("业务二组");
        assertThat(targets.getStoreName()).isEqualTo("厦门店");
        assertThat(targets.getPositionName()).isEqualTo("招商主管");
        assertThat(targets.getDepartmentSupervisor()).isEqualTo("部门负责人");
        assertThat(targets.getDepartmentSupervisor()).isNotEqualTo("直属主管本人");
    }

    private static SysDept dept(Long id, Long parentId, String ancestors, String name, String type, String leader)
    {
        SysDept dept = new SysDept();
        dept.setDeptId(id); dept.setParentId(parentId); dept.setAncestors(ancestors);
        dept.setDeptName(name); dept.setDeptType(type); dept.setLeader(leader); dept.setStatus("0");
        if (leader != null && !leader.isBlank()) dept.setLeaderUserId(id + 1000L);
        return dept;
    }

    private static HrOnboarding onboarding(Long id)
    {
        HrOnboarding row = new HrOnboarding();
        row.setOnboardingId(id);
        return row;
    }

    private static void loginAsDepartmentScopedUser()
    {
        SysRole role = new SysRole();
        role.setRoleId(7L);
        role.setRoleKey("hr_user");
        role.setStatus(UserConstants.ROLE_NORMAL);
        role.setDataScope(Constants.Dept.DATA_SCOPE_DEPT);
        SysUser user = new SysUser();
        user.setUserId(200L);
        user.setDeptId(20L);
        user.setRoles(Collections.singletonList(role));
        LoginUser loginUser = new LoginUser();
        loginUser.setSysUser(user);
        loginUser.setRoles(Collections.singleton("hr_user"));
        SecurityContextHolder.setUserId("200");
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);
    }
}
