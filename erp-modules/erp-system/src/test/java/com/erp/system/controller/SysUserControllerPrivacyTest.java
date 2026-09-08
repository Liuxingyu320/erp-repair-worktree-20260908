package com.erp.system.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.security.annotation.Logical;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.system.api.domain.SysDept;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.domain.SysUserProfile;
import com.erp.system.domain.vo.SysUserAccountExportVo;
import com.erp.system.domain.vo.SysUserOptionVo;
import com.erp.system.service.ISysRoleService;
import com.erp.system.service.ISysPostService;
import com.erp.system.service.ISysUserService;
import com.erp.system.support.HrSensitiveFieldMasker;
import com.erp.system.support.HrEmployeeFieldRegistry;
import com.erp.system.support.HrEmployeeStatusCatalog;

@DisplayName("用户管理隐私与主管选择")
class SysUserControllerPrivacyTest
{
    @Test
    @DisplayName("系统用户表单选项不依赖HR专用接口")
    void userFormOptionsShouldIncludeSafeEmployeeStatusCatalog()
    {
        ISysRoleService roleService = mock(ISysRoleService.class);
        ISysPostService postService = mock(ISysPostService.class);
        when(roleService.selectRoleAll()).thenReturn(Collections.emptyList());
        when(postService.selectPostAll()).thenReturn(Collections.emptyList());
        SysUserController controller = new SysUserController();
        ReflectionTestUtils.setField(controller, "roleService", roleService);
        ReflectionTestUtils.setField(controller, "postService", postService);

        AjaxResult response = controller.getInfo(null);

        assertThat(response.get("employeeStatusOptions")).isEqualTo(HrEmployeeStatusCatalog.values());
    }

    @Test
    @DisplayName("安全用户选项只返回最小字段并限制查询数量")
    void optionsShouldReturnOnlyMinimalScopedFields() throws Exception
    {
        ISysUserService userService = mock(ISysUserService.class);
        SysUser user = activeUser(12L, "alice", "张三");
        user.setPhonenumber("13800138000");
        user.setEmail("alice@example.com");
        SysDept dept = new SysDept();
        dept.setDeptId(3L);
        dept.setDeptName("华东区");
        user.setDeptId(3L);
        user.setDept(dept);
        when(userService.selectUserOptionList(any(SysUser.class))).thenReturn(Collections.singletonList(user));
        SysUserController controller = new SysUserController();
        ReflectionTestUtils.setField(controller, "userService", userService);

        AjaxResult response = controller.options(" alice ", 99L, 500);

        @SuppressWarnings("unchecked")
        List<SysUserOptionVo> rows = (List<SysUserOptionVo>) response.get(AjaxResult.DATA_TAG);
        assertThat(rows).singleElement().satisfies(option -> {
            assertThat(option.getUserId()).isEqualTo(12L);
            assertThat(option.getDeptName()).isEqualTo("华东区");
        });
        assertThat(Arrays.stream(SysUserOptionVo.class.getDeclaredFields()).map(Field::getName).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder("userId", "userName", "nickName", "deptId", "deptName",
                        "postNames", "status");
        ArgumentCaptor<SysUser> query = ArgumentCaptor.forClass(SysUser.class);
        verify(userService).selectUserOptionList(query.capture());
        assertThat(query.getValue().getParams()).containsEntry("keyword", "alice").containsEntry("limit", 200);

        Method method = SysUserController.class.getMethod("options", String.class, Long.class, Integer.class);
        RequiresPermissions permissions = method.getAnnotation(RequiresPermissions.class);
        assertThat(permissions.value()).containsExactly("system:user:add", "system:user:edit");
        assertThat(permissions.logical()).isEqualTo(Logical.OR);
    }

    @Test
    @DisplayName("角色授权页只返回最小用户字段")
    void authRoleShouldNotReturnPasswordOrEmployeeProfile()
    {
        ISysUserService userService = mock(ISysUserService.class);
        ISysRoleService roleService = mock(ISysRoleService.class);
        SysUser user = activeUser(12L, "alice", "张三");
        user.setPassword("encoded-password");
        user.getProfile().setIdNumber("110101199001011234");
        when(userService.selectUserById(12L)).thenReturn(user);
        when(roleService.selectRolesByUserId(12L)).thenReturn(Collections.emptyList());
        SysUserController controller = new SysUserController();
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "roleService", roleService);

        AjaxResult response = controller.authRole(12L);

        assertThat(response.get("user")).isInstanceOf(SysUserOptionVo.class);
        assertThat(response.get("user")).extracting("userName", "nickName")
                .containsExactly("alice", "张三");
        verify(userService).checkUserDataScope(12L);
    }

    @Test
    @DisplayName("通用用户导出模型不包含HR高敏字段")
    void accountExportShouldUseDedicatedSafeProjection()
    {
        assertThat(Arrays.stream(SysUserAccountExportVo.class.getDeclaredFields())
                .map(Field::getName).collect(Collectors.toSet()))
                .doesNotContain("idNumber", "bankAccount", "currentAddress", "birthDate",
                        "emergencyContact", "emergencyContactPhone", "registeredResidence")
                .contains("userName", "employeeNo", "deptName", "setupStatus");
    }

    @Test
    @DisplayName("用户列表无敏感权限时手机号脱敏")
    void maskPhoneShouldNotExposePlaintext()
    {
        SysUserController controller = new SysUserController();
        ReflectionTestUtils.setField(controller, "sensitiveFieldMasker", new HrSensitiveFieldMasker());
        SysUser user = activeUser(12L, "alice", "张三");
        user.setPhonenumber("13800138000");

        ReflectionTestUtils.invokeMethod(controller, "maskPhoneForList", Collections.singletonList(user), false);

        assertThat(user.getPhonenumber()).isEqualTo("138****8000");
    }

    @Test
    @DisplayName("用户详情无敏感权限时深拷贝并脱敏HR字段")
    void userDetailShouldMaskSensitiveFieldsWithoutMutatingSource()
    {
        SysUserController controller = new SysUserController();
        ReflectionTestUtils.setField(controller, "sensitiveFieldMasker", new HrSensitiveFieldMasker());
        ReflectionTestUtils.setField(controller, "employeeFieldRegistry", new HrEmployeeFieldRegistry());
        SysUser source = activeUser(12L, "alice", "张三");
        source.setPassword("encoded-password");
        source.setPhonenumber("13800138000");
        source.getProfile().setIdNumber("110101199001011234");
        source.getProfile().setBankAccount("622202020202025678");
        source.getProfile().setCurrentAddress("上海市浦东新区世纪大道100号");
        source.getProfile().setEmergencyContactPhone("13900139000");

        SysUser sanitized = ReflectionTestUtils.invokeMethod(controller, "sanitizeUserForResponse", source);

        assertThat(sanitized).isNotSameAs(source);
        assertThat(sanitized.getPassword()).isNull();
        assertThat(sanitized.getPhonenumber()).isEqualTo("138****8000");
        assertThat(sanitized.getProfile()).isNotSameAs(source.getProfile());
        assertThat(sanitized.getProfile().getIdNumber()).isEqualTo("1101**********1234");
        assertThat(sanitized.getProfile().getBankAccount()).isEqualTo("6222**********5678");
        assertThat(sanitized.getProfile().getCurrentAddress()).startsWith("上海").doesNotContain("世纪大道");
        assertThat(sanitized.getProfile().getEmergencyContactPhone()).isEqualTo("139****9000");
        assertThat(source.getPassword()).isEqualTo("encoded-password");
        assertThat(source.getProfile().getIdNumber()).isEqualTo("110101199001011234");
    }

    @Test
    @DisplayName("当前用户基础资料保留本人手机号但移除密码与员工档案")
    void currentUserResponseShouldPreserveOwnPhoneAndRemovePrivateProfile()
    {
        SysUserController controller = new SysUserController();
        SysUser source = activeUser(12L, "alice", "张三");
        source.setPassword("encoded-password");
        source.setPhonenumber("13800138000");
        source.getProfile().setIdNumber("110101199001011234");

        SysUser sanitized = ReflectionTestUtils.invokeMethod(controller, "sanitizeCurrentUserForResponse", source);

        assertThat(sanitized.getPhonenumber()).isEqualTo("13800138000");
        assertThat(sanitized.getPassword()).isNull();
        assertThat(sanitized.hasProfile()).isFalse();
        assertThat(source.hasProfile()).isTrue();
    }

    @Test
    @DisplayName("系统用户编辑权限不能绕过HR敏感档案写入权限")
    void sensitiveProfileWritesShouldRequireHrEditPermission()
    {
        SysUserController controller = new SysUserController();
        ReflectionTestUtils.setField(controller, "employeeFieldRegistry", new HrEmployeeFieldRegistry());
        SysUser payload = activeUser(12L, "alice", "张三");
        payload.getProfile().setIdNumber("110101199001011234");

        assertThatThrownBy(() -> controller.assertSensitiveProfilePayloadAllowed(payload))
                .isInstanceOf(ServiceException.class).hasMessageContaining("HR编辑权限");
        assertThatThrownBy(() -> controller.assertSensitiveProfileChangesAllowed(
                new LinkedHashSet<>(List.of("profile.bankAccount"))))
                .isInstanceOf(ServiceException.class).hasMessageContaining("HR编辑权限");

        payload.getProfile().setIdNumber(null);
        payload.getProfile().setJobGrade("P5");
        controller.assertSensitiveProfilePayloadAllowed(payload);
        controller.assertSensitiveProfileChangesAllowed(new LinkedHashSet<>(List.of("profile.jobGrade")));
    }

    @Test
    @DisplayName("直属主管必须位于数据范围内并使用真实用户姓名")
    void supervisorShouldBeScopedAndCanonicalized()
    {
        ISysUserService userService = mock(ISysUserService.class);
        when(userService.selectUserById(22L)).thenReturn(activeUser(22L, "manager", "李主管"));
        SysUserController controller = new SysUserController();
        ReflectionTestUtils.setField(controller, "userService", userService);
        SysUser target = activeUser(12L, "alice", "张三");
        SysUserProfile profile = new SysUserProfile();
        profile.setDirectSupervisorUserId(22L);
        profile.setDirectSupervisor("伪造姓名");
        target.setProfile(profile);

        controller.validateSupervisorSelection(target);

        verify(userService).checkUserDataScope(22L);
        assertThat(target.getProfile().getDirectSupervisor()).isEqualTo("李主管");

        doThrow(new ServiceException("没有权限访问用户数据")).when(userService).checkUserDataScope(33L);
        target.getProfile().setDirectSupervisorUserId(33L);
        assertThatThrownBy(() -> controller.validateSupervisorSelection(target))
                .isInstanceOf(ServiceException.class).hasMessageContaining("没有权限访问用户数据");
    }

    @Test
    @DisplayName("新增修改用户必须选择部门和至少一个角色")
    void requiredAssignmentsShouldFailClosed()
    {
        SysUserController controller = new SysUserController();
        SysUser user = new SysUser();

        assertThatThrownBy(() -> controller.validateRequiredAssignments(user))
                .isInstanceOf(ServiceException.class).hasMessageContaining("归属部门");
        user.setDeptId(10L);
        assertThatThrownBy(() -> controller.validateRequiredAssignments(user))
                .isInstanceOf(ServiceException.class).hasMessageContaining("至少选择一个角色");
        user.setRoleIds(new Long[] { 3L });
        controller.validateRequiredAssignments(user);
    }

    @Test
    @DisplayName("差量合并只修改声明字段并拒绝未知字段")
    void applyUserChangesShouldPreserveUnchangedFields()
    {
        SysUserController controller = new SysUserController();
        ReflectionTestUtils.setField(controller, "employeeFieldRegistry", new HrEmployeeFieldRegistry());
        SysUser target = activeUser(12L, "alice", "旧姓名");
        target.setEmail("keep@example.com");
        target.setProfile(new SysUserProfile());
        target.getProfile().setIdNumber("OLD-ID");
        SysUser changes = new SysUser();
        changes.setNickName("新姓名");
        changes.setEmail("must-not-overwrite@example.com");
        changes.setProfile(new SysUserProfile());
        changes.getProfile().setIdNumber("NEW-ID");

        controller.applyUserChanges(target, changes,
                new LinkedHashSet<>(List.of("nickName", "profile.idNumber")));

        assertThat(target.getNickName()).isEqualTo("新姓名");
        assertThat(target.getEmail()).isEqualTo("keep@example.com");
        assertThat(target.getProfile().getIdNumber()).isEqualTo("NEW-ID");
        assertThatThrownBy(() -> controller.applyUserChanges(target, changes,
                new LinkedHashSet<>(List.of("password"))))
                .isInstanceOf(ServiceException.class).hasMessageContaining("不允许修改字段");
    }

    private static SysUser activeUser(Long userId, String userName, String nickName)
    {
        SysUser user = new SysUser();
        user.setUserId(userId);
        user.setUserName(userName);
        user.setNickName(nickName);
        user.setStatus("0");
        user.setDelFlag("0");
        return user;
    }
}
