package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.core.io.ClassPathResource;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Arrays;
import java.util.function.BiFunction;
import jakarta.validation.Validation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.system.api.domain.SysRole;
import com.erp.system.api.domain.SysDept;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.domain.SysUserProfile;
import com.erp.system.api.model.LoginUser;
import com.erp.system.domain.SysPost;
import com.erp.system.domain.vo.SysTemporaryCredentialVo;
import com.erp.system.domain.vo.SysUserImportResultVo;
import com.erp.system.mapper.SysDeptMapper;
import com.erp.system.mapper.SysConfigMapper;
import com.erp.system.mapper.SysPostMapper;
import com.erp.system.mapper.SysRoleMapper;
import com.erp.system.mapper.SysUserMapper;
import com.erp.system.mapper.SysUserProfileMapper;
import com.erp.system.mapper.SysUserPostMapper;
import com.erp.system.mapper.SysUserProfileMapper;
import com.erp.system.mapper.SysUserRoleMapper;
import com.erp.system.mapper.SysUserShopMapper;
import com.erp.system.service.ISysConfigService;
import com.erp.system.service.ISysDeptService;
import com.erp.system.service.SigningProfileNormalizer;
import com.erp.system.service.support.TemporaryCredentialPolicy;
import com.erp.system.service.support.TemporaryPasswordGenerator;
import com.erp.system.service.support.UserSessionInvalidationService;
import org.mockito.Mockito;
import java.util.Date;

@DisplayName("用户业务服务")
class SysUserServiceImplTest
{
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-10T00:00:00Z"), ZoneId.of("UTC"));

    private static final String PREDICTABLE_HIGH_ENTROPY_PASSWORD = "Ab9~PredictableTmpPwd!";
    private static final Date EXPECTED_TEMPORARY_EXPIRY = Date.from(Instant.parse("2026-07-11T00:00:00Z"));

    @Test
    @DisplayName("新增用户使用高熵临时密码并保留首次改密门禁")
    void insertUserWithTemporaryCredentialShouldUseHighEntropyPassword()
    {
        AtomicReference<SysUser> inserted = new AtomicReference<>();
        SysUserMapper userMapper = mapper(SysUserMapper.class, (method, args) -> {
            if ("insertUser".equals(method))
            {
                SysUser value = (SysUser) args[0];
                value.setUserId(1107L);
                SysUser snapshot = new SysUser();
                snapshot.setPassword(value.getPassword());
                snapshot.setPwdUpdateDate(value.getPwdUpdateDate());
                snapshot.setCredentialState(value.getCredentialState());
                snapshot.setTemporaryPasswordExpiresAt(value.getTemporaryPasswordExpiresAt());
                inserted.set(snapshot);
                return 1;
            }
            throw unexpected(method);
        });
        TemporaryPasswordGenerator generator = Mockito.mock(TemporaryPasswordGenerator.class);
        Mockito.when(generator.generate("0")).thenReturn(PREDICTABLE_HIGH_ENTROPY_PASSWORD);
        SysUserServiceImpl service = newService(userMapper, deptService(), generator);
        SysUser user = user(null, 20L, "13781702750");

        SysTemporaryCredentialVo credential = service.insertUserWithTemporaryCredential(user, "0");

        assertThat(credential.getTemporaryPassword()).isEqualTo(PREDICTABLE_HIGH_ENTROPY_PASSWORD);
        assertThat(credential.getTemporaryPassword()).isNotEqualTo("123456");
        assertThat(credential.getExpiresAt()).isEqualTo(EXPECTED_TEMPORARY_EXPIRY);
        assertThat(inserted.get().getCredentialState()).isEqualTo(SysUser.CREDENTIAL_STATE_TEMPORARY);
        assertThat(inserted.get().getPwdUpdateDate()).isNull();
        assertThat(inserted.get().getTemporaryPasswordExpiresAt()).isEqualTo(EXPECTED_TEMPORARY_EXPIRY);
        assertThat(inserted.get().getPassword()).isNotEqualTo(PREDICTABLE_HIGH_ENTROPY_PASSWORD);
        assertThat(SecurityUtils.matchesPassword(PREDICTABLE_HIGH_ENTROPY_PASSWORD, inserted.get().getPassword())).isTrue();
        Mockito.verify(generator).generate("0");
    }

    @Test
    @DisplayName("纯数字策略无法满足 80-bit 熵时新增临时凭证 fail-closed")
    void insertUserWithTemporaryCredentialShouldRejectNumericOnlyPolicy()
    {
        TemporaryPasswordGenerator generator = new TemporaryPasswordGenerator();
        SysUserServiceImpl service = newService(mapper(SysUserMapper.class, (method, args) -> {
            throw unexpected(method);
        }), deptService(), generator);
        SysUser user = user(null, 20L, "13781702750");

        assertThatThrownBy(() -> service.insertUserWithTemporaryCredential(user, "1"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("80 bit");
    }

    @Test
    @DisplayName("重置临时密码使用高熵策略并写入 TEMPORARY 过期")
    void resetTemporaryCredentialShouldUseHighEntropyPassword()
    {
        AtomicReference<String> storedHash = new AtomicReference<>();
        AtomicReference<Date> storedExpiry = new AtomicReference<>();
        SysUserMapper userMapper = mapper(SysUserMapper.class, (method, args) -> {
            if ("resetUserTemporaryCredential".equals(method))
            {
                storedHash.set((String) args[1]);
                storedExpiry.set((Date) args[2]);
                return 1;
            }
            throw unexpected(method);
        });
        TemporaryPasswordGenerator generator = Mockito.mock(TemporaryPasswordGenerator.class);
        Mockito.when(generator.generate("4")).thenReturn(PREDICTABLE_HIGH_ENTROPY_PASSWORD);
        SysUserServiceImpl service = newService(userMapper, deptService(), generator);
        SysUser user = user(55L, 20L, "reset-user");
        user.setUpdateBy("admin");

        SysTemporaryCredentialVo credential = service.resetTemporaryCredential(user, "4");

        assertThat(credential.getTemporaryPassword()).isEqualTo(PREDICTABLE_HIGH_ENTROPY_PASSWORD);
        assertThat(credential.getTemporaryPassword()).isNotEqualTo("123456");
        assertThat(credential.getExpiresAt()).isEqualTo(EXPECTED_TEMPORARY_EXPIRY);
        assertThat(storedExpiry.get()).isEqualTo(EXPECTED_TEMPORARY_EXPIRY);
        assertThat(storedHash.get()).isNotEqualTo(PREDICTABLE_HIGH_ENTROPY_PASSWORD);
        assertThat(SecurityUtils.matchesPassword(PREDICTABLE_HIGH_ENTROPY_PASSWORD, storedHash.get())).isTrue();
        Mockito.verify(generator).generate("4");
    }

    @Test
    @DisplayName("导入新建用户使用高熵临时密码并只回传一次明文")
    void importUserCreateShouldUseHighEntropyTemporaryPassword()
    {
        setAdminSecurityContext();
        try
        {
            AtomicReference<SysUser> inserted = new AtomicReference<>();
            SysUserMapper userMapper = mapper(SysUserMapper.class, (method, args) -> {
                if ("selectUserByUserName".equals(method))
                {
                    return null;
                }
                if ("insertUser".equals(method))
                {
                    SysUser value = (SysUser) args[0];
                    value.setUserId(2208L);
                    SysUser snapshot = new SysUser();
                    snapshot.setUserName(value.getUserName());
                    snapshot.setPassword(value.getPassword());
                    snapshot.setPwdUpdateDate(value.getPwdUpdateDate());
                    snapshot.setCredentialState(value.getCredentialState());
                    snapshot.setTemporaryPasswordExpiresAt(value.getTemporaryPasswordExpiresAt());
                    inserted.set(snapshot);
                    return 1;
                }
                throw unexpected(method);
            });
            TemporaryPasswordGenerator generator = Mockito.mock(TemporaryPasswordGenerator.class);
            Mockito.when(generator.generate("0")).thenReturn(PREDICTABLE_HIGH_ENTROPY_PASSWORD);
            SysUserServiceImpl service = newService(userMapper, deptService(), generator);
            ReflectionTestUtils.setField(service, "profileMapper", mapper(SysUserProfileMapper.class, (method, args) -> {
                throw unexpected(method);
            }));
            ReflectionTestUtils.setField(service, "signingProfileNormalizer", new SigningProfileNormalizer());

            SysUser imported = user(null, 20L, "import-user-01");
            SysUserImportResultVo result = service.importUser(Collections.singletonList(imported), false, "tester");

            assertThat(result.isCommitted()).isTrue();
            assertThat(result.getCreatedCount()).isEqualTo(1);
            assertThat(result.getTemporaryCredentials()).hasSize(1);
            SysTemporaryCredentialVo credential = result.getTemporaryCredentials().get(0);
            assertThat(credential.getTemporaryPassword()).isEqualTo(PREDICTABLE_HIGH_ENTROPY_PASSWORD);
            assertThat(credential.getTemporaryPassword()).isNotEqualTo("123456");
            assertThat(credential.getExpiresAt()).isEqualTo(EXPECTED_TEMPORARY_EXPIRY);
            assertThat(inserted.get().getCredentialState()).isEqualTo(SysUser.CREDENTIAL_STATE_TEMPORARY);
            assertThat(inserted.get().getPwdUpdateDate()).isNull();
            assertThat(inserted.get().getTemporaryPasswordExpiresAt()).isEqualTo(EXPECTED_TEMPORARY_EXPIRY);
            assertThat(inserted.get().getPassword()).isNotEqualTo(PREDICTABLE_HIGH_ENTROPY_PASSWORD);
            assertThat(SecurityUtils.matchesPassword(PREDICTABLE_HIGH_ENTROPY_PASSWORD, inserted.get().getPassword())).isTrue();
            Mockito.verify(generator).generate("0");
        }
        finally
        {
            SecurityContextHolder.remove();
        }
    }

    @Test
    @DisplayName("重新授权配置HR角色后触发托管权限同步")
    void insertUserAuthShouldSyncManagedSignHrPermissions()
    {
        assertThat(Arrays.stream(SysUserServiceImpl.class.getDeclaredFields()).map(field -> field.getName()))
                .contains("configMapper");
        List<String> events = new java.util.ArrayList<>();
        SysUserRoleMapper userRoleMapper = mapper(SysUserRoleMapper.class, (method, args) -> {
            if ("batchUserRole".equals(method))
            {
                events.add(method);
                return 1;
            }
            if ("lockUserForRoleAssignment".equals(method)) return args[0];
            if ("selectRoleIdsByUserId".equals(method)) return Collections.emptyList();
            throw unexpected(method);
        });
        SysConfigMapper configMapper = mapper(SysConfigMapper.class, (method, args) -> {
            if ("lockSignHrState".equals(method))
            {
                events.add(method);
                return 1L;
            }
            if ("syncSignHrPermissions".equals(method))
            {
                events.add(method);
                return null;
            }
            throw unexpected(method);
        });
        SysUserServiceImpl service = new SysUserServiceImpl();
        ReflectionTestUtils.setField(service, "userRoleMapper", userRoleMapper);
        ReflectionTestUtils.setField(service, "roleMapper", mapper(SysRoleMapper.class, (method, args) -> {
            if ("selectRolePermissionByUserId".equals(method))
            {
                return Collections.emptyList();
            }
            if ("selectRoleByIdForUpdate".equals(method))
            {
                SysRole role = new SysRole((Long) args[0]);
                role.setStatus("0"); role.setDelFlag("0");
                return role;
            }
            throw unexpected(method);
        }));
        ReflectionTestUtils.setField(service, "configMapper", configMapper);
        ReflectionTestUtils.setField(service, "userSessionInvalidationService",
                Mockito.mock(UserSessionInvalidationService.class));

        setAdminSecurityContext();
        try
        {
            service.insertUserAuth(88L, new Long[] { 2L, 3L });
        }
        finally
        {
            SecurityContextHolder.remove();
        }

        assertThat(events).containsExactly("lockSignHrState", "batchUserRole", "syncSignHrPermissions");
    }

    @Test
    @DisplayName("服务层拒绝替换超级管理员角色")
    void insertUserAuthShouldRejectSuperAdministratorTarget()
    {
        SysUserServiceImpl service = new SysUserServiceImpl();

        assertThatThrownBy(() -> service.insertUserAuth(1L, new Long[] { 2L }))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("超级管理员用户");
    }

    @Test
    @DisplayName("导入更新用户时应使用Excel中的新部门并归一化签约字段")
    void importUserUpdateShouldApplyImportedDeptId()
    {
        SecurityContextHolder.setUserId("1");
        LoginUser loginUser = new LoginUser();
        loginUser.setRoles(new HashSet<>(Collections.singletonList("admin")));
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);
        try
        {
            AtomicReference<SysUser> updatedUser = new AtomicReference<>();
            AtomicReference<SysUserProfile> insertedProfile = new AtomicReference<>();
            SysUser existing = user(9L, 10L, "tea001");
            SysUserMapper userMapper = mapper(SysUserMapper.class, (method, args) -> {
                if ("selectUserByUserName".equals(method))
                {
                    return existing;
                }
                if ("updateUser".equals(method))
                {
                    updatedUser.set((SysUser) args[0]);
                    return 1;
                }
                throw unexpected(method);
            });
            SysUserProfileMapper profileMapper = mapper(SysUserProfileMapper.class, (method, args) -> {
                if ("selectUserProfileByUserId".equals(method))
                {
                    return null;
                }
                if ("insertUserProfile".equals(method))
                {
                    insertedProfile.set((SysUserProfile) args[0]);
                    return 1;
                }
                throw unexpected(method);
            });
            SysUserServiceImpl userService = newService(userMapper, deptService());
            ReflectionTestUtils.setField(userService, "profileMapper", profileMapper);
            ReflectionTestUtils.setField(userService, "signingProfileNormalizer", new SigningProfileNormalizer());

            SysUser imported = user(null, 20L, "tea001");
            SysUserProfile profile = new SysUserProfile();
            profile.setContractType("固定期限劳动合同");
            profile.setSocialType("有社保");
            imported.setProfile(profile);

            SysUserImportResultVo result = userService.importUser(Collections.singletonList(imported), true, "tester");

            assertThat(result.isCommitted()).isTrue();
            assertThat(result.getUpdatedCount()).isEqualTo(1);
            assertThat(updatedUser.get()).isNotNull();
            assertThat(updatedUser.get().getUserId()).isEqualTo(9L);
            assertThat(updatedUser.get().getDeptId()).isEqualTo(20L);
            assertThat(insertedProfile.get()).isNotNull();
            assertThat(insertedProfile.get().getContractType()).isEqualTo("LABOR_CONTRACT");
            assertThat(insertedProfile.get().getContractTerm()).isEqualTo("FIXED_TERM");
            assertThat(insertedProfile.get().getSocialType()).isEqualTo("SOCIAL_INSURED");
        }
        finally
        {
            SecurityContextHolder.remove();
        }
    }

    @Test
    @DisplayName("Excel用户导入声明整批事务回滚边界")
    void importUserShouldDeclareBatchTransaction()
            throws Exception
    {
        Transactional transactional = SysUserServiceImpl.class
                .getMethod("importUser", List.class, Boolean.class, String.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.rollbackFor()).contains(Exception.class);
    }

    @Test
    @DisplayName("已分配员工号后禁止通过用户编辑绕过调岗流程")
    void updateUserShouldRejectAssignedEmployeePostChange()
    {
        SysUserProfile assignedProfile = new SysUserProfile();
        assignedProfile.setUserId(9L);
        assignedProfile.setEmployeeNo("E000009");
        SysUserProfileMapper profileMapper = mapper(SysUserProfileMapper.class, (method, args) -> {
            if ("selectUserProfileByUserId".equals(method))
            {
                return assignedProfile;
            }
            throw unexpected(method);
        });
        SysUserPostMapper userPostMapper = mapper(SysUserPostMapper.class, (method, args) -> {
            if ("selectPostIdsByUserId".equals(method))
            {
                return List.of(11L);
            }
            throw unexpected(method);
        });
        SysUserServiceImpl userService = newService(mapper(SysUserMapper.class,
                (method, args) -> { throw unexpected(method); }), deptService());
        ReflectionTestUtils.setField(userService, "profileMapper", profileMapper);
        ReflectionTestUtils.setField(userService, "userPostMapper", userPostMapper);

        SysUser edited = user(9L, 20L, "tea001");
        edited.setPostIds(new Long[] { 12L });

        assertThatThrownBy(() -> userService.updateUser(edited))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("确认调岗");
    }

    @Test
    @DisplayName("员工档案更新不得修改已分配的员工号")
    void updateHrEmployeeProfileShouldRejectEmployeeNoChange()
    {
        SysUserProfile existing = new SysUserProfile();
        existing.setProfileId(99L);
        existing.setUserId(9L);
        existing.setEmployeeNo("E000009");
        SysUserProfileMapper profileMapper = mapper(SysUserProfileMapper.class, (method, args) -> {
            if ("selectUserProfileByUserId".equals(method))
            {
                return existing;
            }
            throw unexpected(method);
        });
        SysUserMapper userMapper = mapper(SysUserMapper.class, (method, args) -> {
            if ("updateHrEmployeeProfileUser".equals(method))
            {
                return 1;
            }
            throw unexpected(method);
        });
        SysUserServiceImpl userService = newService(userMapper, deptService());
        ReflectionTestUtils.setField(userService, "profileMapper", profileMapper);
        ReflectionTestUtils.setField(userService, "signingProfileNormalizer", new SigningProfileNormalizer());

        SysUser edited = user(9L, 20L, "tea001");
        SysUserProfile submitted = new SysUserProfile();
        submitted.setEmployeeNo("E000010");
        edited.setProfile(submitted);

        assertThatThrownBy(() -> userService.updateHrEmployeeProfile(edited))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("一经分配不可修改");
    }

    @Test
    @DisplayName("导入把当前唯一HR改为离职时拒绝整批且不执行同步")
    void importUserShouldRejectOffboardingConfiguredHrWithoutSync()
    {
        setAdminSecurityContext();
        try
        {
            AtomicInteger updateCount = new AtomicInteger();
            AtomicInteger syncCount = new AtomicInteger();
            SysUser ordinary = user(9L, 20L, "tea001");
            SysUser configuredHr = user(88L, 20L, "hr001");
            SysUserMapper userMapper = mapper(SysUserMapper.class, (method, args) -> {
                if ("selectUserByUserName".equals(method))
                {
                    return "hr001".equals(args[0]) ? configuredHr : ordinary;
                }
                if ("updateUser".equals(method))
                {
                    updateCount.incrementAndGet();
                    return 1;
                }
                throw unexpected(method);
            });
            SysConfigMapper configMapper = mapper(SysConfigMapper.class, (method, args) -> {
                if ("lockSignHrState".equals(method))
                {
                    return 1L;
                }
                if ("selectConfiguredSignHrUserId".equals(method))
                {
                    return 88L;
                }
                if ("syncSignHrPermissions".equals(method))
                {
                    syncCount.incrementAndGet();
                    return null;
                }
                throw unexpected(method);
            });
            SysUserServiceImpl userService = newService(userMapper, deptService());
            ReflectionTestUtils.setField(userService, "configMapper", configMapper);
            ReflectionTestUtils.setField(userService, "signingProfileNormalizer", new SigningProfileNormalizer());
            ReflectionTestUtils.setField(userService, "profileMapper",
                    mapper(SysUserProfileMapper.class, (method, args) -> {
                        if ("selectUserProfileByUserId".equals(method))
                        {
                            return null;
                        }
                        if ("insertUserProfile".equals(method))
                        {
                            return 1;
                        }
                        throw unexpected(method);
                    }));

            SysUser importedOrdinary = user(null, 20L, "tea001");
            SysUser importedHr = user(null, 20L, "hr001");
            SysUserProfile profile = new SysUserProfile();
            profile.setEmployeeStatus("离职");
            importedHr.setProfile(profile);

            SysUserImportResultVo result = userService.importUser(
                    List.of(importedOrdinary, importedHr), true, "tester");
            assertThat(result.isCommitted()).isFalse();
            assertThat(result.getFailureCount()).isEqualTo(1);
            assertThat(result.getFailures().get(0).getMessage()).contains("请先更换签约默认接收人配置");
            assertThat(updateCount).hasValue(1);
            assertThat(syncCount).hasValue(0);
        }
        finally
        {
            SecurityContextHolder.remove();
        }
    }

    @Test
    @DisplayName("普通用户批量导入先锁状态并只同步一次")
    void importUserShouldLockBeforeQueriesAndSyncOnceAfterSuccessfulBatch()
    {
        setAdminSecurityContext();
        try
        {
            List<String> events = new java.util.ArrayList<>();
            SysUser first = user(9L, 20L, "tea001");
            SysUser second = user(10L, 20L, "tea002");
            SysUserMapper userMapper = mapper(SysUserMapper.class, (method, args) -> {
                if ("selectUserByUserName".equals(method))
                {
                    events.add("read:" + args[0]);
                    return "tea002".equals(args[0]) ? second : first;
                }
                if ("updateUser".equals(method))
                {
                    events.add("write:" + ((SysUser) args[0]).getUserName());
                    return 1;
                }
                throw unexpected(method);
            });
            SysConfigMapper configMapper = mapper(SysConfigMapper.class, (method, args) -> {
                if ("lockSignHrState".equals(method))
                {
                    events.add("lock:signHrState");
                    return 1L;
                }
                if ("selectConfiguredSignHrUserId".equals(method))
                {
                    events.add("read:configuredHr");
                    return 88L;
                }
                if ("syncSignHrPermissions".equals(method))
                {
                    events.add("write:syncSignHrPermissions");
                    return null;
                }
                throw unexpected(method);
            });
            SysUserServiceImpl userService = newService(userMapper, deptService());
            ReflectionTestUtils.setField(userService, "configMapper", configMapper);
            ReflectionTestUtils.setField(userService, "signingProfileNormalizer", new SigningProfileNormalizer());

            SysUserImportResultVo result = userService.importUser(
                    List.of(user(null, 20L, "tea001"), user(null, 20L, "tea002")), true, "tester");

            assertThat(result.isCommitted()).isTrue();
            assertThat(result.getUpdatedCount()).isEqualTo(2);
            assertThat(events).containsExactly(
                    "lock:signHrState",
                    "read:tea001", "read:configuredHr", "write:tea001",
                    "read:tea002", "read:configuredHr", "write:tea002",
                    "write:syncSignHrPermissions");
        }
        finally
        {
            SecurityContextHolder.remove();
        }
    }

    @Test
    @DisplayName("禁止操作拥有admin角色的用户")
    void checkUserAllowedShouldRejectUserWithAdminRoleKey()
    {
        SysRole adminRole = new SysRole();
        adminRole.setRoleId(88L);
        adminRole.setRoleKey("admin");
        SysRoleMapper roleMapper = mapper(SysRoleMapper.class, (method, args) -> {
            if ("selectRolePermissionByUserId".equals(method))
            {
                assertThat(args[0]).isEqualTo(9L);
                return List.of(adminRole);
            }
            throw unexpected(method);
        });
        SysUserMapper userMapper = mapper(SysUserMapper.class, (method, args) -> {
            throw unexpected(method);
        });
        SysUserServiceImpl userService = newService(userMapper, roleMapper, deptService());

        assertThatThrownBy(() -> userService.checkUserAllowed(user(9L, 20L, "admin-user")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("超级管理员用户");
    }

    @Test
    @DisplayName("用户列表批量派生组织字段且部门只查询一次")
    void selectUserListShouldDeriveProfilesWithOneDepartmentQuery()
    {
        SysUser first = user(9L, 3L, "tea001");
        first.setPostNames("店长");
        first.setProfile(new SysUserProfile());
        first.getProfile().setEmployeeStatus("在职");
        SysUser second = user(10L, 3L, "tea002");
        second.setPostNames("培训师");
        second.setProfile(new SysUserProfile());
        second.getProfile().setEmployeeStatus("历史未知");
        SysUserMapper userMapper = mapper(SysUserMapper.class, (method, args) -> {
            if ("selectUserList".equals(method))
            {
                return List.of(first, second);
            }
            throw unexpected(method);
        });
        AtomicInteger departmentQueries = new AtomicInteger();
        SysDeptMapper deptMapper = mapper(SysDeptMapper.class, (method, args) -> {
            if ("selectDeptList".equals(method))
            {
                departmentQueries.incrementAndGet();
                return List.of(
                        dept(1L, 0L, "0", "星河集团", "GROUP"),
                        dept(2L, 1L, "0,1", "星河公司", "COMPANY"),
                        dept(3L, 2L, "0,1,2", "人事部", "COMPANY"));
            }
            throw unexpected(method);
        });
        SysPostMapper postMapper = mapper(SysPostMapper.class, (method, args) -> {
            throw unexpected(method);
        });
        SysUserServiceImpl userService = newService(userMapper, deptService());
        ReflectionTestUtils.setField(userService, "profileDerivationService",
                new SysUserProfileDerivationService(deptMapper, postMapper, FIXED_CLOCK));

        List<SysUser> result = userService.selectUserList(new SysUser());

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getProfile().getCompanyName()).isEqualTo("星河公司");
        assertThat(result.get(0).getProfile().getDeptLevel1Name()).isEqualTo("人事部");
        assertThat(result.get(0).getProfile().getPositionNames()).isEqualTo("店长");
        assertThat(result.get(1).getProfile().getPositionNames()).isEqualTo("培训师");
        assertThat(result.get(0).getProfile().getEmployeeStatus()).isEqualTo("正式");
        assertThat(result.get(1).getProfile().getEmployeeStatus()).isEqualTo("历史未知");
        assertThat(departmentQueries).hasValue(1);
    }

    @Test
    @DisplayName("用户详情派生已分配的多岗位名称")
    void selectUserByIdShouldDeriveAssignedPositions()
    {
        SysUser detail = user(9L, 1L, "tea001");
        detail.setProfile(new SysUserProfile());
        detail.getProfile().setEmployeeStatus("在职");
        SysUserMapper userMapper = mapper(SysUserMapper.class, (method, args) -> {
            if ("selectUserById".equals(method))
            {
                return detail;
            }
            throw unexpected(method);
        });
        SysDeptMapper deptMapper = mapper(SysDeptMapper.class, (method, args) -> {
            if ("selectDeptList".equals(method))
            {
                return List.of(dept(1L, 0L, "0", "星河公司", "COMPANY"));
            }
            throw unexpected(method);
        });
        SysPostMapper postMapper = mapper(SysPostMapper.class, (method, args) -> {
            if ("selectPostsByUserName".equals(method))
            {
                return List.of(post(2L, "培训师", 2), post(1L, "店长", 1));
            }
            throw unexpected(method);
        });
        SysUserServiceImpl userService = newService(userMapper, deptService());
        ReflectionTestUtils.setField(userService, "profileDerivationService",
                new SysUserProfileDerivationService(deptMapper, postMapper, FIXED_CLOCK));

        SysUser result = userService.selectUserById(9L);

        assertThat(result.getProfile().getPositionNames()).isEqualTo("店长、培训师");
        assertThat(result.getProfile().getEmployeeStatus()).isEqualTo("正式");
    }

    @Test
    @DisplayName("用户列表SQL返回角色和店铺授权配置状态")
    void userListSqlShouldExposeSetupStatus()
            throws Exception
    {
        String xml = new String(new ClassPathResource("mapper/system/SysUserMapper.xml").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);

        assertThat(xml).contains("role_count");
        assertThat(xml).contains("shop_scope_count");
        assertThat(xml).contains("setup_status");
        assertThat(xml).contains("missingRole");
        assertThat(xml).contains("missingShopScope");
    }

    @Test
    @DisplayName("用户列表SQL应按用户昵称模糊查询")
    void userListSqlShouldFuzzyFilterByNickName()
            throws Exception
    {
        String xml = new String(new ClassPathResource("mapper/system/SysUserMapper.xml").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);

        assertThat(xml).contains("nickName != null and nickName != ''");
        assertThat(xml).contains("u.nick_name like concat('%', #{nickName}, '%')");
    }

    private static SysUser user(Long userId, Long deptId, String userName)
    {
        SysUser user = new SysUser();
        user.setUserId(userId);
        user.setDeptId(deptId);
        user.setUserName(userName);
        user.setNickName("测试用户");
        user.setStatus("0");
        return user;
    }

    private static void setAdminSecurityContext()
    {
        SecurityContextHolder.setUserId("1");
        LoginUser loginUser = new LoginUser();
        loginUser.setRoles(new HashSet<>(Collections.singletonList("admin")));
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);
    }

    private static SysDept dept(Long id, Long parentId, String ancestors, String name, String type)
    {
        SysDept dept = new SysDept();
        dept.setDeptId(id);
        dept.setParentId(parentId);
        dept.setAncestors(ancestors);
        dept.setDeptName(name);
        dept.setDeptType(type);
        dept.setStatus("0");
        dept.setDelFlag("0");
        return dept;
    }

    private static SysPost post(Long id, String name, Integer sort)
    {
        SysPost post = new SysPost();
        post.setPostId(id);
        post.setPostName(name);
        post.setPostSort(sort);
        post.setStatus("0");
        return post;
    }

    private static SysUserServiceImpl newService(SysUserMapper userMapper, ISysDeptService deptService)
    {
        return newService(userMapper, deptService, new TemporaryPasswordGenerator());
    }

    private static SysUserServiceImpl newService(SysUserMapper userMapper, ISysDeptService deptService,
            TemporaryPasswordGenerator temporaryPasswordGenerator)
    {
        return newService(userMapper, mapper(SysRoleMapper.class, (method, args) -> {
            if ("selectRolePermissionByUserId".equals(method))
            {
                return Collections.emptyList();
            }
            throw unexpected(method);
        }), deptService, temporaryPasswordGenerator);
    }

    private static SysUserServiceImpl newService(SysUserMapper userMapper, SysRoleMapper roleMapper, ISysDeptService deptService)
    {
        return newService(userMapper, roleMapper, deptService, new TemporaryPasswordGenerator());
    }

    private static SysUserServiceImpl newService(SysUserMapper userMapper, SysRoleMapper roleMapper, ISysDeptService deptService,
            TemporaryPasswordGenerator temporaryPasswordGenerator)
    {
        SysUserServiceImpl service = new SysUserServiceImpl();
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "profileMapper", mapper(SysUserProfileMapper.class, (method, args) -> {
            throw unexpected(method);
        }));
        ReflectionTestUtils.setField(service, "roleMapper", roleMapper);
        ReflectionTestUtils.setField(service, "postMapper", mapper(SysPostMapper.class, (method, args) -> {
            throw unexpected(method);
        }));
        ReflectionTestUtils.setField(service, "userRoleMapper", mapper(SysUserRoleMapper.class, (method, args) -> {
            if ("deleteUserRoleByUserId".equals(method))
            {
                return 1;
            }
            throw unexpected(method);
        }));
        ReflectionTestUtils.setField(service, "userPostMapper", mapper(SysUserPostMapper.class, (method, args) -> {
            if ("deleteUserPostByUserId".equals(method))
            {
                return 1;
            }
            throw unexpected(method);
        }));
        ReflectionTestUtils.setField(service, "userShopMapper", mapper(SysUserShopMapper.class, (method, args) -> {
            throw unexpected(method);
        }));
        ReflectionTestUtils.setField(service, "configService", mapper(ISysConfigService.class, (method, args) -> {
            if ("selectConfigByKey".equals(method) && "sys.account.chrtype".equals(args[0]))
            {
                return "0";
            }
            throw unexpected(method);
        }));
        ReflectionTestUtils.setField(service, "temporaryPasswordGenerator", temporaryPasswordGenerator);
        ReflectionTestUtils.setField(service, "clock", FIXED_CLOCK);
        ReflectionTestUtils.setField(service, "temporaryCredentialPolicy", new TemporaryCredentialPolicy());
        ReflectionTestUtils.setField(service, "userSessionInvalidationService",
                Mockito.mock(UserSessionInvalidationService.class));
        ReflectionTestUtils.setField(service, "configMapper", mapper(SysConfigMapper.class, (method, args) -> {
            if ("lockSignHrState".equals(method))
            {
                return 1L;
            }
            if ("selectConfiguredSignHrUserId".equals(method))
            {
                return null;
            }
            if ("syncSignHrPermissions".equals(method))
            {
                return null;
            }
            throw unexpected(method);
        }));
        ReflectionTestUtils.setField(service, "deptService", deptService);
        ReflectionTestUtils.setField(service, "validator", Validation.buildDefaultValidatorFactory().getValidator());
        return service;
    }

    private static ISysDeptService deptService()
    {
        return mapper(ISysDeptService.class, (method, args) -> {
            if ("checkDeptDataScope".equals(method))
            {
                assertThat(args[0]).isEqualTo(20L);
                return null;
            }
            throw unexpected(method);
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T mapper(Class<T> type, BiFunction<String, Object[], Object> handler)
    {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class)
            {
                return method.invoke(thisProxy(type), args);
            }
            return handler.apply(method.getName(), args == null ? new Object[0] : args);
        });
    }

    private static Object thisProxy(Class<?> type)
    {
        return new Object()
        {
            @Override
            public String toString()
            {
                return type.getSimpleName() + "TestProxy";
            }
        };
    }

    private static AssertionError unexpected(String method)
    {
        return new AssertionError("Unexpected call: " + method);
    }
}
