package com.erp.file.drive.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.domain.DriveIdentity;
import com.erp.file.drive.domain.DriveSpace;
import com.erp.file.drive.exception.DriveException;
import com.erp.file.drive.mapper.DriveIdentityMapper;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.model.LoginUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

@DisplayName("云盘登录身份与空间权限")
class DriveAuthorizationServiceTest
{
    private final DriveAuthorizationService service = new DriveAuthorizationService();

    @Test
    @DisplayName("个人、公司、部门及管理员权限矩阵应严格隔离")
    void shouldEnforceSpacePermissionMatrix()
    {
        DriveActor owner = actor(20L, 8L, Set.of(DriveConstants.PERMISSION_ACCESS), false);
        DriveActor outsider = actor(30L, 99L, Set.of(DriveConstants.PERMISSION_ACCESS), false);
        DriveActor employee = actor(31L, 8L, Set.of(DriveConstants.PERMISSION_ACCESS), false);
        DriveActor deptManager = actor(32L, 8L,
                Set.of(DriveConstants.PERMISSION_ACCESS, DriveConstants.PERMISSION_DEPARTMENT_MANAGE), false);
        DriveActor companyManager = actor(33L, 9L,
                Set.of(DriveConstants.PERMISSION_ACCESS, DriveConstants.PERMISSION_COMPANY_MANAGE), false);
        DriveActor admin = actor(1L, 99L, Set.of(), true);

        assertThat(service.canRead(owner, personalSpace(20L))).isTrue();
        assertThat(service.canWrite(outsider, personalSpace(20L))).isFalse();
        assertThat(service.canRead(employee, companySpace())).isTrue();
        assertThat(service.canWrite(companyManager, companySpace())).isTrue();
        assertThat(service.canWrite(deptManager, departmentSpace(8L))).isTrue();
        assertThat(service.canRead(employee, departmentSpace(99L))).isFalse();
        assertThat(service.canWrite(admin, departmentSpace(99L))).isTrue();
        assertThat(service.canWrite(employee, departmentSpace(8L))).isFalse();
        assertThat(service.canWrite(companyManager, departmentSpace(9L))).isFalse();
    }

    @Test
    @DisplayName("拒绝访问时返回稳定业务错误码")
    void shouldFailClosedWithStableBusinessCode()
    {
        DriveActor outsider = actor(30L, 99L, Set.of(DriveConstants.PERMISSION_ACCESS), false);

        assertThatThrownBy(() -> service.requireWrite(outsider, personalSpace(20L)))
                .isInstanceOf(DriveException.class)
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_ACCESS_DENIED);
    }

    @Test
    @DisplayName("只读组织盘禁止新增但允许原管理人员清理容量")
    void shouldAllowCleanupButNotWriteInReadOnlyOrganizationSpace()
    {
        DriveActor manager = actor(32L, 8L,
                Set.of(DriveConstants.PERMISSION_ACCESS,
                        DriveConstants.PERMISSION_DEPARTMENT_MANAGE), false);
        DriveSpace space = departmentSpace(8L);
        space.setStatus(DriveConstants.STATUS_READ_ONLY);

        assertThat(service.canWrite(manager, space)).isFalse();
        assertThat(service.canCleanup(manager, space)).isTrue();
    }

    @Test
    @DisplayName("解析身份时令牌提供账号权限而部门来自实时数据库")
    void shouldResolveLiveDepartmentInsteadOfTokenDepartment()
    {
        DriveIdentityMapper mapper = mock(DriveIdentityMapper.class);
        DriveActorResolver resolver = new DriveActorResolver(mapper);
        LoginUser loginUser = loginUser(20L, "alice", 8L,
                Set.of(DriveConstants.PERMISSION_ACCESS, DriveConstants.PERMISSION_COMPANY_MANAGE));
        DriveIdentity identity = identity(20L, 9L, "财务部");
        when(mapper.selectCurrentIdentity(20L)).thenReturn(identity);

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class))
        {
            security.when(SecurityUtils::getLoginUser).thenReturn(loginUser);
            security.when(SecurityUtils::isAdmin).thenReturn(false);

            DriveActor actor = resolver.resolve();

            assertThat(actor.userId()).isEqualTo(20L);
            assertThat(actor.username()).isEqualTo("alice");
            assertThat(actor.deptId()).isEqualTo(9L);
            assertThat(actor.deptName()).isEqualTo("财务部");
            assertThat(actor.permissions()).containsExactlyInAnyOrder(
                    DriveConstants.PERMISSION_ACCESS,
                    DriveConstants.PERMISSION_COMPANY_MANAGE);
            assertThat(actor.admin()).isFalse();
        }
        verify(mapper).selectCurrentIdentity(20L);
    }

    @Test
    @DisplayName("无有效部门的启用用户以无部门身份继续")
    void shouldResolveEnabledUserWithoutDepartment()
    {
        DriveIdentityMapper mapper = mock(DriveIdentityMapper.class);
        DriveActorResolver resolver = new DriveActorResolver(mapper);
        LoginUser loginUser = loginUser(20L, "alice", 8L, Set.of(DriveConstants.PERMISSION_ACCESS));
        when(mapper.selectCurrentIdentity(20L)).thenReturn(identity(20L, null, null));

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class))
        {
            security.when(SecurityUtils::getLoginUser).thenReturn(loginUser);
            DriveActor actor = resolver.resolve();
            assertThat(actor.deptId()).isNull();
            assertThat(service.canRead(actor, departmentSpace(8L))).isFalse();
        }
    }

    @Test
    @DisplayName("登录用户不存在或数据库身份失效时关闭访问")
    void shouldRejectMissingOrDisabledIdentity()
    {
        DriveIdentityMapper mapper = mock(DriveIdentityMapper.class);
        DriveActorResolver resolver = new DriveActorResolver(mapper);
        LoginUser loginUser = loginUser(20L, "alice", 8L, Set.of(DriveConstants.PERMISSION_ACCESS));
        when(mapper.selectCurrentIdentity(20L)).thenReturn(null);

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class))
        {
            security.when(SecurityUtils::getLoginUser).thenReturn(loginUser);
            assertThatThrownBy(resolver::resolve)
                    .isInstanceOf(DriveException.class)
                    .extracting("businessCode")
                    .isEqualTo(DriveErrorCodes.DRIVE_ACCESS_DENIED);

            security.when(SecurityUtils::getLoginUser).thenReturn(null);
            assertThatThrownBy(resolver::resolve)
                    .isInstanceOf(DriveException.class)
                    .extracting("businessCode")
                    .isEqualTo(DriveErrorCodes.DRIVE_ACCESS_DENIED);
        }
    }

    private static DriveActor actor(Long userId, Long deptId, Set<String> permissions, boolean admin)
    {
        return new DriveActor(userId, deptId, "部门", "user" + userId, permissions, admin);
    }

    private static DriveSpace personalSpace(Long ownerId)
    {
        DriveSpace space = activeSpace(DriveConstants.SPACE_PERSONAL);
        space.setOwnerUserId(ownerId);
        return space;
    }

    private static DriveSpace companySpace()
    {
        return activeSpace(DriveConstants.SPACE_COMPANY);
    }

    private static DriveSpace departmentSpace(Long deptId)
    {
        DriveSpace space = activeSpace(DriveConstants.SPACE_DEPARTMENT);
        space.setDeptId(deptId);
        return space;
    }

    private static DriveSpace activeSpace(String type)
    {
        DriveSpace space = new DriveSpace();
        space.setSpaceType(type);
        space.setStatus(DriveConstants.STATUS_ACTIVE);
        return space;
    }

    private static LoginUser loginUser(
            Long userId, String username, Long tokenDeptId, Set<String> permissions)
    {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserid(userId);
        loginUser.setUsername(username);
        loginUser.setPermissions(permissions);
        SysUser tokenUser = new SysUser();
        tokenUser.setDeptId(tokenDeptId);
        loginUser.setSysUser(tokenUser);
        return loginUser;
    }

    private static DriveIdentity identity(Long userId, Long deptId, String deptName)
    {
        DriveIdentity identity = new DriveIdentity();
        identity.setUserId(userId);
        identity.setDeptId(deptId);
        identity.setDeptName(deptName);
        return identity;
    }
}
