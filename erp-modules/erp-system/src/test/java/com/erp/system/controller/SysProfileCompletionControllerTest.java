package com.erp.system.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.constant.HttpStatus;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.security.service.TokenService;
import com.erp.system.api.domain.SysDept;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.domain.SysUserProfile;
import com.erp.system.api.model.LoginUser;
import com.erp.system.domain.vo.SysProfileCompletionFieldVo;
import com.erp.system.domain.vo.SysProfileCompletionRequest;
import com.erp.system.domain.vo.SysProfileCompletionVo;
import com.erp.system.domain.vo.SysSelfProfileVo;
import com.erp.system.service.ISysConfigService;
import com.erp.system.service.ISysDeptService;
import com.erp.system.service.ISysPermissionService;
import com.erp.system.service.ISysUserProfileCompletionService;
import com.erp.system.service.ISysUserService;
import com.fasterxml.jackson.databind.ObjectMapper;

@DisplayName("登录资料补全控制器")
class SysProfileCompletionControllerTest
{
    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("资料查询使用当前登录用户")
    void profileCompletionShouldEvaluateCurrentUser()
    {
        setLoginUser(20L, "employee20", Collections.emptySet());
        ISysUserProfileCompletionService completionService = mock(ISysUserProfileCompletionService.class);
        SysProfileCompletionVo completion = incompleteCompletion();
        when(completionService.evaluate(20L)).thenReturn(completion);
        SysProfileController controller = new SysProfileController();
        ReflectionTestUtils.setField(controller, "profileCompletionService", completionService);

        AjaxResult result = controller.profileCompletion();

        assertThat(result.get(AjaxResult.DATA_TAG)).isSameAs(completion);
        verify(completionService).evaluate(20L);
    }

    @Test
    @DisplayName("个人资料接口不返回密码哈希或员工敏感档案")
    void profileShouldReturnAccountOnlyData()
    {
        setLoginUser(20L, "employee20", Collections.emptySet());
        ISysUserService userService = mock(ISysUserService.class);
        SysUser queried = new SysUser(20L);
        queried.setUserName("employee20");
        queried.setPassword("encoded-password");
        queried.getProfile().setIdNumber("330102199001011234");
        when(userService.selectUserByUserName("employee20")).thenReturn(queried);
        when(userService.selectUserRoleGroup("employee20")).thenReturn("员工");
        when(userService.selectUserPostGroup("employee20")).thenReturn("店员");
        SysProfileController controller = new SysProfileController();
        ReflectionTestUtils.setField(controller, "userService", userService);

        AjaxResult result = controller.profile();

        SysSelfProfileVo returned = (SysSelfProfileVo) result.get(AjaxResult.DATA_TAG);
        assertThat(returned.getUserName()).isEqualTo("employee20");
        assertThat(returned.getProfile().get("idNumberMasked"))
                .isEqualTo("3301**********1234");
        assertThat(returned.getProfile()).doesNotContainKey("idNumber");
        assertThat(result).containsEntry("roleGroup", "员工").containsEntry("postGroup", "店员");
    }

    @Test
    @DisplayName("资料保存成功后刷新登录缓存中的用户")
    void updateProfileCompletionShouldRefreshLoginCache()
    {
        LoginUser loginUser = setLoginUser(20L, "employee20", Collections.emptySet());
        ISysUserProfileCompletionService completionService = mock(ISysUserProfileCompletionService.class);
        ISysUserService userService = mock(ISysUserService.class);
        TokenService tokenService = mock(TokenService.class);
        SysProfileCompletionRequest request = new SysProfileCompletionRequest();
        SysProfileCompletionVo completion = new SysProfileCompletionVo();
        SysUser refreshedUser = new SysUser(20L);
        refreshedUser.setUserName("employee20");
        when(completionService.save(20L, request, "employee20")).thenReturn(completion);
        when(userService.selectUserByUserName("employee20")).thenReturn(refreshedUser);
        SysProfileController controller = new SysProfileController();
        ReflectionTestUtils.setField(controller, "profileCompletionService", completionService);
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "tokenService", tokenService);

        AjaxResult result = controller.updateProfileCompletion(request);

        assertThat(result.get(AjaxResult.DATA_TAG)).isSameAs(completion);
        assertThat(loginUser.getSysUser()).isSameAs(refreshedUser);
        verify(completionService).save(20L, request, "employee20");
        verify(tokenService).setLoginUser(loginUser);
    }

    @Test
    @DisplayName("登录信息返回资料完整度和缺失字段")
    void getInfoShouldPublishProfileCompletionState() throws Exception
    {
        LoginUser loginUser = setLoginUser(20L, "employee20", Set.of("system:test"));
        SysUserProfile privateProfile = new SysUserProfile();
        privateProfile.setIdNumber("330102199001011234");
        privateProfile.setBankAccount("622202020202025678");
        loginUser.getSysUser().setProfile(privateProfile);
        ISysPermissionService permissionService = mock(ISysPermissionService.class);
        ISysConfigService configService = mock(ISysConfigService.class);
        ISysUserProfileCompletionService completionService = mock(ISysUserProfileCompletionService.class);
        when(permissionService.getRolePermission(org.mockito.ArgumentMatchers.any(SysUser.class)))
                .thenReturn(Set.of("employee"));
        when(permissionService.getMenuPermission(org.mockito.ArgumentMatchers.any(SysUser.class)))
                .thenReturn(Set.of("system:test"));
        when(configService.selectConfigByKey(anyString())).thenReturn("0");
        SysProfileCompletionVo completion = incompleteCompletion();
        when(completionService.evaluate(20L)).thenReturn(completion);
        SysUserController controller = new SysUserController();
        ReflectionTestUtils.setField(controller, "permissionService", permissionService);
        ReflectionTestUtils.setField(controller, "configService", configService);
        ReflectionTestUtils.setField(controller, "tokenService", mock(TokenService.class));
        ReflectionTestUtils.setField(controller, "profileCompletionService", completionService);

        AjaxResult result = controller.getInfo();

        assertThat(result.get("profileCompletionRequired")).isEqualTo(true);
        assertThat(result.get("profileMissingFields")).isEqualTo(completion.getMissingFields());
        assertThat(((SysUser) result.get("user")).hasProfile()).isFalse();
        assertThat(new ObjectMapper().writeValueAsString(result))
                .doesNotContain("330102199001011234")
                .doesNotContain("622202020202025678");
        verify(completionService).evaluate(20L);
    }

    @Test
    @DisplayName("资料不完整时店铺树返回业务冲突且不查询组织")
    void shopTreeShouldRejectIncompleteUser()
    {
        setLoginUser(20L, "employee20", Collections.emptySet());
        ISysDeptService deptService = mock(ISysDeptService.class);
        ISysUserProfileCompletionService completionService = mock(ISysUserProfileCompletionService.class);
        SysProfileCompletionVo completion = incompleteCompletion();
        when(completionService.evaluate(20L)).thenReturn(completion);
        SysDeptController controller = shopController(deptService, completionService);

        AjaxResult result = controller.shopTree();

        assertThat(result.get(AjaxResult.CODE_TAG)).isEqualTo(HttpStatus.CONFLICT);
        assertThat(result.get(AjaxResult.MSG_TAG)).isEqualTo("请先补全入职资料");
        assertThat(result.get("businessCode")).isEqualTo("PROFILE_COMPLETION_REQUIRED");
        assertThat(result.get("profileMissingFields")).isEqualTo(completion.getMissingFields());
        verify(deptService, never()).selectShopTree(org.mockito.ArgumentMatchers.any(SysDept.class));
    }

    @Test
    @DisplayName("资料完整时店铺树正常返回")
    void shopTreeShouldAllowCompleteUser()
    {
        setLoginUser(20L, "employee20", Collections.emptySet());
        ISysDeptService deptService = mock(ISysDeptService.class);
        ISysUserProfileCompletionService completionService = mock(ISysUserProfileCompletionService.class);
        SysProfileCompletionVo completion = new SysProfileCompletionVo();
        List<SysDept> shops = List.of(new SysDept());
        when(completionService.evaluate(20L)).thenReturn(completion);
        when(deptService.selectShopTree(org.mockito.ArgumentMatchers.any(SysDept.class))).thenReturn(shops);
        SysDeptController controller = shopController(deptService, completionService);

        AjaxResult result = controller.shopTree();

        assertThat(result.get(AjaxResult.CODE_TAG)).isEqualTo(HttpStatus.SUCCESS);
        assertThat(result.get(AjaxResult.DATA_TAG)).isSameAs(shops);
    }

    @Test
    @DisplayName("admin超级管理员继续访问店铺树")
    void shopTreeShouldAllowAdmin()
    {
        setLoginUser(1L, "admin", Collections.emptySet());
        ISysDeptService deptService = mock(ISysDeptService.class);
        ISysUserProfileCompletionService completionService = mock(ISysUserProfileCompletionService.class);
        SysProfileCompletionVo completion = new SysProfileCompletionVo();
        when(completionService.evaluate(1L)).thenReturn(completion);
        when(deptService.selectShopTree(org.mockito.ArgumentMatchers.any(SysDept.class)))
                .thenReturn(Collections.emptyList());
        SysDeptController controller = shopController(deptService, completionService);

        AjaxResult result = controller.shopTree();

        assertThat(result.get(AjaxResult.CODE_TAG)).isEqualTo(HttpStatus.SUCCESS);
        verify(completionService).evaluate(1L);
        verify(deptService).selectShopTree(org.mockito.ArgumentMatchers.any(SysDept.class));
    }

    private static SysDeptController shopController(ISysDeptService deptService,
            ISysUserProfileCompletionService completionService)
    {
        SysDeptController controller = new SysDeptController();
        ReflectionTestUtils.setField(controller, "deptService", deptService);
        ReflectionTestUtils.setField(controller, "profileCompletionService", completionService);
        return controller;
    }

    private static SysProfileCompletionVo incompleteCompletion()
    {
        SysProfileCompletionVo completion = new SysProfileCompletionVo();
        completion.setCompletionRequired(true);
        completion.setMissingFields(List.of(new SysProfileCompletionFieldVo("nickName", "姓名")));
        return completion;
    }

    private static LoginUser setLoginUser(Long userId, String username, Set<String> permissions)
    {
        SecurityContextHolder.setUserId(String.valueOf(userId));
        SecurityContextHolder.setUserName(username);
        SysUser user = new SysUser(userId);
        user.setUserName(username);
        LoginUser loginUser = new LoginUser();
        loginUser.setUserid(userId);
        loginUser.setUsername(username);
        loginUser.setSysUser(user);
        loginUser.setPermissions(permissions);
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);
        return loginUser;
    }
}
