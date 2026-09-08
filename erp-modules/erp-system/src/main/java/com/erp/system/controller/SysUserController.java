package com.erp.system.controller;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.BeanUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.erp.common.core.constant.UserConstants;
import com.erp.common.core.domain.R;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.text.Convert;
import com.erp.common.core.utils.DateUtils;
import com.erp.common.core.utils.ServletUtils;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.core.utils.bean.BeanValidators;
import com.erp.common.core.utils.poi.ExcelUtil;
import com.erp.common.core.web.controller.BaseController;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.core.web.page.TableDataInfo;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.AllowPasswordChangeRequired;
import com.erp.common.security.annotation.InnerAuth;
import com.erp.common.security.annotation.AllowsTemporaryCredential;
import com.erp.common.security.annotation.Logical;
import com.erp.common.security.annotation.RequiresLogin;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.common.security.auth.AuthUtil;
import com.erp.common.security.service.TokenService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.system.api.domain.SignCandidateUser;
import com.erp.system.api.domain.SignCandidateUserQuery;
import com.erp.system.api.domain.SysDept;
import com.erp.system.api.domain.SysRole;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.domain.SysUserProfile;
import com.erp.system.api.model.LoginUser;
import com.erp.system.config.DriveFeatureProperties;
import com.erp.system.domain.dto.SysUserChangeRequest;
import com.erp.system.domain.vo.SysProfileCompletionVo;
import com.erp.system.domain.vo.SysBuildInfoVo;
import com.erp.system.domain.vo.SysTemporaryCredentialVo;
import com.erp.system.domain.vo.SysUserCreateResultVo;
import com.erp.system.domain.vo.SysUserImportResultVo;
import com.erp.system.domain.dto.SysUserManageUpdateRequest;
import com.erp.system.domain.dto.SysUserPiiAccessReason;
import com.erp.system.domain.dto.SysUserPiiUpdateRequest;
import com.erp.system.domain.vo.SysUserAdminExportVo;
import com.erp.system.domain.vo.SysUserListVo;
import com.erp.system.domain.vo.SysUserManageDetailVo;
import com.erp.system.domain.vo.SysUserOptionVo;
import com.erp.system.domain.vo.SysUserPiiExportVo;
import com.erp.system.domain.vo.SysUserSetupSummaryVo;
import com.erp.system.domain.dto.LegacyCredentialRotationExecuteRequest;
import com.erp.system.domain.dto.LegacyCredentialRotationPreviewRequest;
import com.erp.system.service.BusinessFeatureGate;
import com.erp.system.service.FrontendFeatureCatalog;
import com.erp.system.service.ISysConfigService;
import com.erp.system.service.ISysDeptService;
import com.erp.system.service.ISysPermissionService;
import com.erp.system.service.ISysPostService;
import com.erp.system.service.ISysRoleService;
import com.erp.system.service.ISysUserProfileCompletionService;
import com.erp.system.service.ISysUserService;
import com.erp.system.service.support.LegacyCredentialRotationService;
import com.erp.system.service.support.SystemBuildInfoProvider;
import com.erp.system.support.SysShopDeptFilterSupport;
import com.erp.system.support.HrSensitiveFieldMasker;
import com.erp.system.support.HrEmployeeFieldRegistry;
import com.erp.system.support.HrEmployeeStatusCatalog;
import com.erp.system.support.HrEmployeeFieldRegistry.FieldDefinition;
import com.erp.system.support.HrEmployeeFieldRegistry.MaskingClass;
import com.erp.system.support.HrEmployeeFieldRegistry.StorageOwner;
import jakarta.validation.Validator;

/**
 * 用户信息
 * 
 * @author erp
 */
@RestController
@RequestMapping("/user")
public class SysUserController extends BaseController
{
    private static final Set<String> USER_SETUP_STATUSES = Set.of(
            "missingRole", "missingShopScope", "complete");

    @Autowired
    private ISysUserService userService;

    @Autowired
    private ISysRoleService roleService;

    @Autowired
    private ISysDeptService deptService;

    @Autowired
    private ISysPostService postService;

    @Autowired
    private ISysPermissionService permissionService;

    @Autowired
    private ISysConfigService configService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private LegacyCredentialRotationService legacyCredentialRotationService;

    @Autowired
    private SysShopDeptFilterSupport shopDeptFilterSupport;

    @Autowired
    private ISysUserProfileCompletionService profileCompletionService;

    @Autowired
    private DriveFeatureProperties driveFeatureProperties;

    @Autowired
    private BusinessFeatureGate businessFeatureGate;

    @Autowired(required = false)
    private FrontendFeatureCatalog frontendFeatureCatalog;

    @Autowired(required = false)
    private SystemBuildInfoProvider systemBuildInfoProvider;

    @Autowired
    private HrSensitiveFieldMasker sensitiveFieldMasker;

    @Autowired
    private HrEmployeeFieldRegistry employeeFieldRegistry;

    @Autowired
    private Validator validator;

    /**
     * 获取用户列表
     */
    @RequiresPermissions("system:user:list")
    @GetMapping("/list")
    public TableDataInfo list(SysUser user, HttpServletRequest request)
    {
        validateSetupStatus(user);
        shopDeptFilterSupport.applyTo(user, request);
        startPage();
        List<SysUserListVo> list = userService.selectUserManageList(user);
        return getDataTable(list);
    }

    /** Scoped account setup health used by the user-management summary cards. */
    @RequiresPermissions("system:user:list")
    @GetMapping("/setup-summary")
    public AjaxResult setupSummary(SysUser user, HttpServletRequest request)
    {
        businessFeatureGate.requireEnabled(BusinessFeatureGate.SYSTEM_MANAGEMENT_UX_V2);
        user.setStatus(null);
        user.setSetupStatus(null);
        shopDeptFilterSupport.applyTo(user, request);
        SysUserSetupSummaryVo summary = userService.selectUserSetupSummary(user);
        return success(summary);
    }

    @Log(title = "用户管理", businessType = BusinessType.EXPORT,
            isSaveRequestData = false, isSaveResponseData = false)
    @RequiresPermissions("system:user:export")
    @PostMapping("/export")
    public void export(HttpServletResponse response, SysUser user, HttpServletRequest request)
    {
        validateSetupStatus(user);
        shopDeptFilterSupport.applyTo(user, request);
        List<SysUserAdminExportVo> list = userService.selectUserManageList(user).stream()
                .map(SysUserAdminExportVo::from).collect(Collectors.toList());
        ExcelUtil<SysUserAdminExportVo> util = new ExcelUtil<>(SysUserAdminExportVo.class);
        util.exportExcel(response, list, "用户数据");
    }

    @Log(title = "用户个人敏感信息导出", businessType = BusinessType.EXPORT,
            isSaveRequestData = false, isSaveResponseData = false)
    @RequiresPermissions(value = { "system:user:export", "system:user:pii:export" })
    @PostMapping("/export-sensitive")
    public void exportSensitive(HttpServletResponse response, SysUser user,
            @RequestParam SysUserPiiAccessReason reasonCode,
            @RequestParam Boolean confirmed, HttpServletRequest request)
    {
        validateSetupStatus(user);
        if (!Boolean.TRUE.equals(confirmed))
        {
            throw new ServiceException("导出完整个人信息前必须二次确认");
        }
        shopDeptFilterSupport.applyTo(user, request);
        List<SysUserPiiExportVo> list = userService.selectUserPiiExportList(user,
                reasonCode, SecurityUtils.getUserId());
        ExcelUtil<SysUserPiiExportVo> util = new ExcelUtil<>(SysUserPiiExportVo.class);
        util.exportExcel(response, list, "用户个人敏感信息");
    }

    private void validateSetupStatus(SysUser user)
    {
        String setupStatus = user == null ? null : user.getSetupStatus();
        if (StringUtils.isNotEmpty(setupStatus) && !USER_SETUP_STATUSES.contains(setupStatus))
        {
            throw new ServiceException("不支持的用户配置状态");
        }
    }

    @Log(title = "用户管理", businessType = BusinessType.IMPORT,
            isSaveRequestData = false, isSaveResponseData = false)
    @RequiresPermissions("system:user:import")
    @PostMapping("/importData")
    public AjaxResult importData(MultipartFile file, boolean updateSupport,
            HttpServletResponse response) throws Exception
    {
        ExcelUtil<SysUser> util = new ExcelUtil<SysUser>(SysUser.class);
        List<SysUser> userList = util.importExcel(file.getInputStream());
        if (userList != null)
        {
            userList.forEach(this::assertSensitiveProfilePayloadAllowed);
        }
        String operName = SecurityUtils.getUsername();
        SysUserImportResultVo result = userService.importUser(userList, updateSupport, operName);
        preventCredentialResponseCaching(response);
        return success(result);
    }

    @RequiresPermissions("system:user:import")
    @PostMapping("/importTemplate")
    public void importTemplate(HttpServletResponse response) throws IOException
    {
        ExcelUtil<SysUser> util = new ExcelUtil<SysUser>(SysUser.class);
        util.importTemplateExcel(response, "用户导入模板");
    }

    /**
     * 获取当前用户信息
     */
    @InnerAuth
    @GetMapping("/info/{username}")
    public R<LoginUser> info(@PathVariable("username") String username)
    {
        SysUser sysUser = userService.selectUserByUserName(username);
        if (StringUtils.isNull(sysUser))
        {
            return R.fail("用户名或密码错误");
        }
        // 角色集合
        Set<String> roles = permissionService.getRolePermission(sysUser);
        // 权限集合
        Set<String> permissions = permissionService.getMenuPermission(sysUser);
        LoginUser sysUserVo = new LoginUser();
        sysUserVo.setSysUser(sysUser);
        sysUserVo.setRoles(roles);
        sysUserVo.setPermissions(permissions);
        return R.ok(sysUserVo);
    }

    /**
     * 注册用户信息
     */
    @InnerAuth
    @PostMapping("/register")
    public R<Boolean> register(@RequestBody SysUser sysUser)
    {
        String username = sysUser.getUserName();
        if (!("true".equals(configService.selectConfigByKey("sys.account.registerUser"))))
        {
            return R.fail("当前系统没有开启注册功能！");
        }
        if (!userService.checkUserNameUnique(sysUser))
        {
            return R.fail("保存用户'" + username + "'失败，注册账号已存在");
        }
        return R.ok(userService.registerUser(sysUser));
    }

    /**
     *记录用户登录IP地址和登录时间
     */
    @InnerAuth
    @PutMapping("/recordlogin")
    public R<Boolean> recordlogin(@RequestBody SysUser sysUser)
    {
        return R.ok(userService.updateLoginInfo(sysUser));
    }

    @InnerAuth
    @PostMapping("/sign-candidates")
    public R<List<SignCandidateUser>> signCandidates(@RequestBody SignCandidateUserQuery query)
    {
        if (query == null)
        {
            query = new SignCandidateUserQuery();
        }
        int limit = query.getLimit() == null || query.getLimit() <= 0 ? 200 : Math.min(query.getLimit(), 500);
        query.setLimit(limit);
        return R.ok(userService.selectSignCandidateUsers(query));
    }

    /**
     * 获取用户信息
     * 
     * @return 用户信息
     */
    @RequiresLogin
    @AllowPasswordChangeRequired
    @AllowsTemporaryCredential
    @GetMapping("getInfo")
    public AjaxResult getInfo()
    {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        SysUser user = loginUser.getSysUser();
        // 角色集合
        Set<String> roles = permissionService.getRolePermission(user);
        // 权限集合
        Set<String> permissions = permissionService.getMenuPermission(user);
        if (!loginUser.getPermissions().equals(permissions))
        {
            loginUser.setPermissions(permissions);
            tokenService.refreshToken(loginUser);
        }
        AjaxResult ajax = AjaxResult.success();
        SysProfileCompletionVo profileCompletion = profileCompletionService.evaluate(user.getUserId());
        ajax.put("user", sanitizeCurrentUserForResponse(user));
        ajax.put("roles", roles);
        ajax.put("permissions", permissions);
        putFrontendFeatureState(ajax, permissions);
        ajax.put("profileCompletionRequired", profileCompletion.isCompletionRequired());
        ajax.put("profileMissingFields", profileCompletion.getMissingFields());
        putDriveFeatureState(ajax);
        putBusinessFeatureState(ajax);
        ajax.put("systemBuild", systemBuildInfoProvider == null
                ? SysBuildInfoVo.unavailable() : systemBuildInfoProvider.current());
        ajax.put("pwdChrtype", getSysAccountChrtype());
        String credentialState = loginUser.getCredentialState();
        ajax.put("credentialState", credentialState);
        ajax.put("temporaryPasswordExpiresAt", loginUser.getTemporaryPasswordExpiresAt());
        ajax.put("isDefaultModifyPwd", false);
        ajax.put("isPasswordExpired", SysUser.CREDENTIAL_STATE_ACTIVE.equals(credentialState)
                && passwordIsExpiration(user.getPwdUpdateDate()));
        HttpServletResponse response = ServletUtils.getResponse();
        if (response != null)
        {
            preventCredentialResponseCaching(response);
        }
        return ajax;
    }

    void putDriveFeatureState(AjaxResult ajax)
    {
        ajax.put("driveEnabled", driveFeatureProperties != null && driveFeatureProperties.isEnabled());
    }

    void putBusinessFeatureState(AjaxResult ajax)
    {
        ajax.put("businessFeatures", businessFeatureGate == null
                ? BusinessFeatureGate.disabledCapabilities()
                : businessFeatureGate.capabilities());
    }

    void putFrontendFeatureState(AjaxResult ajax, Set<String> permissions)
    {
        if (frontendFeatureCatalog == null)
        {
            ajax.put("frontendPermissions", Set.of());
            ajax.put("frontendFeatures", List.of());
            return;
        }
        ajax.put("frontendPermissions", frontendFeatureCatalog.frontendPermissions(permissions));
        ajax.put("frontendFeatures", frontendFeatureCatalog.enabledFeatures(permissions));
    }

    // 获取用户密码自定义配置规则
    public String getSysAccountChrtype()
    {
        if (configService == null)
        {
            return "0";
        }
        return Convert.toStr(configService.selectConfigByKey("sys.account.chrtype"), "0");
    }

    // 检查密码是否过期
    public boolean passwordIsExpiration(Date pwdUpdateDate)
    {
        Integer passwordValidateDays = Convert.toInt(configService.selectConfigByKey("sys.account.passwordValidateDays"));
        if (passwordValidateDays != null && passwordValidateDays > 0)
        {
            if (StringUtils.isNull(pwdUpdateDate))
            {
                // 如果从未修改过初始密码，直接提醒过期
                return true;
            }
            Date nowDate = DateUtils.getNowDate();
            return DateUtils.differentDaysByMillisecond(nowDate, pwdUpdateDate) > passwordValidateDays;
        }
        return false;
    }

    /**
     * 根据用户编号获取详细信息
     */
    @RequiresPermissions("system:user:query")
    @GetMapping(value = { "/", "/{userId}" })
    public AjaxResult getInfo(@PathVariable(value = "userId", required = false) Long userId)
    {
        AjaxResult ajax = AjaxResult.success();
        SysUser targetUser = null;
        if (StringUtils.isNotNull(userId))
        {
            userService.checkUserDataScope(userId);
            targetUser = userService.selectUserById(userId);
            SysUserManageDetailVo manageDetail = userService.selectUserManageDetail(userId);
            ajax.put(AjaxResult.DATA_TAG, manageDetail);
            ajax.put("postIds", postService.selectPostListByUserId(userId));
            ajax.put("roleIds", targetUser.getRoles().stream().map(SysRole::getRoleId).collect(Collectors.toList()));
        }
        List<SysRole> roles = roleService.selectRoleAll();
        ajax.put("roles", filterVisibleRoles(roles, isCurrentOrTargetAdmin(targetUser, null)));
        ajax.put("posts", postService.selectPostAll());
        // The system-user screen must not call the HR-only form-options API just
        // to render a non-sensitive status selector.
        ajax.put("employeeStatusOptions", HrEmployeeStatusCatalog.values());
        return ajax;
    }

    @RequiresPermissions(value = { "system:user:query", "system:user:pii:read" })
    @GetMapping("/{userId}/pii")
    public AjaxResult getPii(@PathVariable Long userId,
            @RequestParam SysUserPiiAccessReason reasonCode)
    {
        userService.checkUserDataScope(userId);
        return success(userService.selectUserPiiById(userId, reasonCode, SecurityUtils.getUserId()));
    }

    @RequiresPermissions(value = { "system:user:edit", "system:user:pii:edit" })
    @Log(title = "用户个人敏感信息", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping("/{userId}/pii")
    public AjaxResult updatePii(@PathVariable Long userId,
            @RequestParam SysUserPiiAccessReason reasonCode,
            @Validated @RequestBody SysUserPiiUpdateRequest request)
    {
        userService.checkUserDataScope(userId);
        SysUser target = userService.selectUserById(userId);
        if (target == null) return error("用户不存在");
        userService.checkUserAllowed(target);
        int rows = userService.updateUserPii(userId, request, reasonCode,
                SecurityUtils.getUserId(), SecurityUtils.getUsername());
        return rows >= 0 ? success() : error();
    }

    @RequiresPermissions(value = { "system:user:add", "system:user:pii:edit" })
    @Log(title = "新建用户个人敏感信息", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping("/{userId}/pii-after-create")
    public AjaxResult updatePiiAfterCreate(@PathVariable Long userId,
            @RequestParam SysUserPiiAccessReason reasonCode,
            @Validated @RequestBody SysUserPiiUpdateRequest request)
    {
        userService.checkUserDataScope(userId);
        SysUser target = userService.selectUserById(userId);
        Date createdAt = target == null ? null : target.getCreateTime();
        long tenMinutesAgo = System.currentTimeMillis() - 10L * 60L * 1000L;
        if (target == null || createdAt == null || createdAt.getTime() < tenMinutesAgo
                || !StringUtils.equals(SecurityUtils.getUsername(), target.getCreateBy()))
        {
            return AjaxResult.error(403, "新建后个人信息补充窗口已失效，请由具备用户修改权限的管理员处理");
        }
        int rows = userService.updateUserPii(userId, request, reasonCode,
                SecurityUtils.getUserId(), SecurityUtils.getUsername());
        return rows >= 0 ? success() : error();
    }

    /**
     * 预览归属部门、岗位和日期对应的实时派生档案字段。
     */
    @RequiresPermissions(value = { "system:user:add", "system:user:edit" }, logical = Logical.OR)
    @PostMapping("/derived-preview")
    public AjaxResult previewDerivedProfile(@RequestBody SysUser user)
    {
        if (StringUtils.isNotNull(user.getDeptId()))
        {
            deptService.checkDeptDataScope(user.getDeptId());
        }
        return success(userService.previewDerivedProfile(user));
    }

    /**
     * 直属主管等用户选择器使用的最小数据接口。
     */
    @RequiresPermissions(value = { "system:user:add", "system:user:edit" }, logical = Logical.OR)
    @GetMapping("/options")
    public AjaxResult options(String keyword, Long excludeUserId, Integer limit)
    {
        SysUser query = new SysUser();
        query.getParams().put("keyword", StringUtils.trim(keyword));
        query.getParams().put("excludeUserId", excludeUserId);
        query.getParams().put("limit", limit == null ? 50 : Math.max(1, Math.min(limit, 200)));
        List<SysUserOptionVo> options = userService.selectUserOptionList(query).stream()
                .map(SysUserOptionVo::from)
                .collect(Collectors.toList());
        return success(options);
    }

    private SysUser sanitizeUserForResponse(SysUser user)
    {
        SysUser sanitizedUser = copyUserWithoutPassword(user);
        if (sanitizedUser == null || AuthUtil.hasPermi("hr:employee:sensitive:view"))
        {
            return sanitizedUser;
        }
        for (FieldDefinition field : employeeFieldRegistry.getFields())
        {
            if (field.getMaskingClass() == MaskingClass.NONE)
            {
                continue;
            }
            if (field.getStorageOwner() != StorageOwner.SYS_USER && !sanitizedUser.hasProfile())
            {
                continue;
            }
            Object value = employeeFieldRegistry.read(sanitizedUser, field);
            if (field.getMaskingClass() == MaskingClass.SALARY)
            {
                employeeFieldRegistry.write(sanitizedUser, field, null);
            }
            else if (value instanceof String text)
            {
                employeeFieldRegistry.write(sanitizedUser, field,
                        maskSensitiveValue(field.getMaskingClass(), text));
            }
        }
        return sanitizedUser;
    }

    private SysUser copyUserWithoutPassword(SysUser user)
    {
        if (user == null)
        {
            return null;
        }
        SysUser copy = new SysUser();
        BeanUtils.copyProperties(user, copy);
        copy.setPassword(null);
        if (user.hasProfile())
        {
            SysUserProfile profileCopy = new SysUserProfile();
            BeanUtils.copyProperties(user.getProfile(), profileCopy);
            copy.setProfile(profileCopy);
        }
        else
        {
            copy.setProfile(null);
        }
        return copy;
    }

    private String maskSensitiveValue(MaskingClass maskingClass, String value)
    {
        return switch (maskingClass)
        {
            case PHONE -> sensitiveFieldMasker.maskPhone(value);
            case ID_NUMBER -> sensitiveFieldMasker.maskIdNumber(value);
            case BANK_ACCOUNT -> sensitiveFieldMasker.maskBankAccount(value);
            case ADDRESS -> sensitiveFieldMasker.maskAddress(value);
            case SALARY -> null;
            case NONE -> value;
        };
    }

    private void maskPhoneForList(List<SysUser> users, boolean canViewPlaintext)
    {
        if (canViewPlaintext || users == null)
        {
            return;
        }
        users.forEach(user -> {
            if (user != null)
            {
                user.setPhonenumber(sensitiveFieldMasker.maskPhone(user.getPhonenumber()));
            }
        });
    }

    private SysUser sanitizeCurrentUserForResponse(SysUser user)
    {
        SysUser sanitizedUser = copyUserWithoutPassword(user);
        if (sanitizedUser != null)
        {
            sanitizedUser.setProfile(null);
        }
        return sanitizedUser;
    }

    /**
     * 新增用户
     */
    @RequiresPermissions("system:user:add")
    @Log(title = "用户管理", businessType = BusinessType.INSERT,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody SysUserManageUpdateRequest request,
            HttpServletResponse response)
    {
        SysUser user = request.toSysUser();
        deptService.checkDeptDataScope(user.getDeptId());
        roleService.checkRoleDataScope(user.getRoleIds());
        validateSupervisorSelection(user);
        if (!userService.checkUserNameUnique(user))
        {
            return error("新增用户'" + user.getUserName() + "'失败，登录账号已存在");
        }
        user.setCreateBy(SecurityUtils.getUsername());
        user.setPassword(null);
        SysTemporaryCredentialVo credential = userService.insertUserWithTemporaryCredential(user,
                getSysAccountChrtype());
        preventCredentialResponseCaching(response);
        return success(new SysUserCreateResultVo(credential));
    }

    /**
     * 修改用户
     */
    @RequiresPermissions("system:user:edit")
    @Log(title = "用户管理", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody SysUserManageUpdateRequest request)
    {
        SysUser user = request.toSysUser();
        if (user.getUserId() == null) return error("用户编号不能为空");
        userService.checkUserAllowed(user);
        userService.checkUserDataScope(user.getUserId());
        validateRequiredAssignments(user);
        deptService.checkDeptDataScope(user.getDeptId());
        roleService.checkRoleDataScope(user.getRoleIds());
        validateSupervisorSelection(user);
        if (!userService.checkUserNameUnique(user))
        {
            return error("修改用户'" + user.getUserName() + "'失败，登录账号已存在");
        }
        user.setUpdateBy(SecurityUtils.getUsername());
        return toAjax(userService.updateUser(user));
    }

    /**
     * 差量修改用户。请求体只携带实际变化字段，服务端基于最新受控数据合并，
     * 防止页面中的陈旧隐藏字段覆盖角色、岗位或员工档案。
     */
    @RequiresPermissions("system:user:edit")
    @Log(title = "用户管理", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PatchMapping("/{userId}")
    public AjaxResult patch(@PathVariable Long userId, @Validated @RequestBody SysUserChangeRequest request)
    {
        if (!userId.equals(request.getUserId()))
        {
            throw new ServiceException("路径用户ID与请求用户ID不一致");
        }
        SysUser current = userService.selectUserById(userId);
        if (current == null)
        {
            throw new ServiceException("用户不存在");
        }
        userService.checkUserAllowed(current);
        userService.checkUserDataScope(userId);
        current.setRoleIds(current.getRoles() == null ? new Long[0]
                : current.getRoles().stream().map(SysRole::getRoleId).toArray(Long[]::new));
        List<Long> currentPostIds = postService.selectPostListByUserId(userId);
        current.setPostIds(currentPostIds == null ? new Long[0] : currentPostIds.toArray(new Long[0]));
        assertSensitiveProfileChangesAllowed(request.getChangedFields());
        applyUserChanges(current, request.getChanges(), request.getChangedFields());
        current.setPassword(null);
        current.setUpdateBy(SecurityUtils.getUsername());
        userService.previewDerivedProfile(current);
        BeanValidators.validateWithException(validator, current);
        validateRequiredAssignments(current);
        deptService.checkDeptDataScope(current.getDeptId());
        roleService.checkRoleDataScope(current.getRoleIds());
        if (request.getChangedFields().contains("profile.directSupervisorUserId"))
        {
            validateSupervisorSelection(current);
        }
        if (!userService.checkUserNameUnique(current))
        {
            return error("修改用户'" + current.getUserName() + "'失败，登录账号已存在");
        }
        if (StringUtils.isNotEmpty(current.getPhonenumber()) && !userService.checkPhoneUnique(current))
        {
            return error("修改用户'" + current.getUserName() + "'失败，手机号码已存在");
        }
        if (StringUtils.isNotEmpty(current.getEmail()) && !userService.checkEmailUnique(current))
        {
            return error("修改用户'" + current.getUserName() + "'失败，邮箱账号已存在");
        }
        return toAjax(userService.updateUser(current));
    }

    void assertSensitiveProfileChangesAllowed(Set<String> changedFields)
    {
        if (changedFields == null || AuthUtil.hasPermi("hr:employee:edit"))
        {
            return;
        }
        for (String field : changedFields)
        {
            if (field == null || !field.startsWith("profile."))
            {
                continue;
            }
            FieldDefinition definition = employeeFieldRegistry.getProfileFieldByPropertyName(
                    field.substring("profile.".length()));
            if (definition != null && definition.getMaskingClass() != MaskingClass.NONE)
            {
                throw new ServiceException("修改员工敏感档案需要HR编辑权限");
            }
        }
    }

    void assertSensitiveProfilePayloadAllowed(SysUser user)
    {
        if (user == null || !user.hasProfile() || AuthUtil.hasPermi("hr:employee:edit"))
        {
            return;
        }
        for (FieldDefinition field : employeeFieldRegistry.getFields())
        {
            if (field.getStorageOwner() == StorageOwner.SYS_USER
                    || field.getStorageOwner() == StorageOwner.DERIVED
                    || field.getMaskingClass() == MaskingClass.NONE)
            {
                continue;
            }
            Object value = employeeFieldRegistry.read(user, field);
            if (value != null && (!(value instanceof String text) || StringUtils.isNotEmpty(text.trim())))
            {
                throw new ServiceException("修改员工敏感档案需要HR编辑权限");
            }
        }
    }

    void applyUserChanges(SysUser target, SysUser changes, Set<String> changedFields)
    {
        if (target == null || changes == null || changedFields == null)
        {
            throw new ServiceException("变化内容不能为空");
        }
        for (String field : changedFields)
        {
            switch (field)
            {
                case "nickName" -> target.setNickName(changes.getNickName());
                case "deptId" -> {
                    target.setDeptId(changes.getDeptId());
                    if (target.getProfile() != null) target.getProfile().setDeptId(changes.getDeptId());
                }
                case "phonenumber" -> target.setPhonenumber(changes.getPhonenumber());
                case "email" -> target.setEmail(changes.getEmail());
                case "sex" -> target.setSex(changes.getSex());
                case "status" -> target.setStatus(changes.getStatus());
                case "remark" -> target.setRemark(changes.getRemark());
                case "postIds" -> target.setPostIds(changes.getPostIds() == null
                        ? new Long[0] : changes.getPostIds().clone());
                case "roleIds" -> target.setRoleIds(changes.getRoleIds() == null
                        ? new Long[0] : changes.getRoleIds().clone());
                default -> applyProfileChange(target, changes, field);
            }
        }
    }

    private void applyProfileChange(SysUser target, SysUser changes, String field)
    {
        if (field == null || !field.startsWith("profile.") || changes.getProfile() == null)
        {
            throw new ServiceException("不允许修改字段：" + field);
        }
        String propertyName = field.substring("profile.".length());
        FieldDefinition definition = employeeFieldRegistry.getProfileFieldByPropertyName(propertyName);
        if (definition == null)
        {
            throw new ServiceException("不允许修改员工档案字段：" + propertyName);
        }
        employeeFieldRegistry.write(target, definition, employeeFieldRegistry.read(changes, definition));
    }

    /** 直属主管必须是真实、启用且位于当前操作人数据范围内的用户。 */
    void validateSupervisorSelection(SysUser user)
    {
        if (user == null || user.getProfile() == null)
        {
            return;
        }
        Long supervisorUserId = user.getProfile().getDirectSupervisorUserId();
        if (supervisorUserId == null)
        {
            if (user.getUserId() == null && StringUtils.isNotEmpty(user.getProfile().getDirectSupervisor()))
            {
                throw new ServiceException("直属主管必须从用户选择器中选择");
            }
            return;
        }
        if (supervisorUserId.equals(user.getUserId()))
        {
            throw new ServiceException("直属主管不能选择用户本人");
        }
        userService.checkUserDataScope(supervisorUserId);
        SysUser supervisor = userService.selectUserById(supervisorUserId);
        if (supervisor == null || !UserConstants.NORMAL.equals(supervisor.getStatus())
                || !UserConstants.NORMAL.equals(supervisor.getDelFlag()))
        {
            throw new ServiceException("直属主管不存在或账号已停用");
        }
        user.getProfile().setDirectSupervisor(StringUtils.isNotEmpty(supervisor.getNickName())
                ? supervisor.getNickName() : supervisor.getUserName());
    }

    void validateRequiredAssignments(SysUser user)
    {
        if (user == null || user.getDeptId() == null)
        {
            throw new ServiceException("归属部门不能为空");
        }
        if (user.getRoleIds() == null || user.getRoleIds().length == 0)
        {
            throw new ServiceException("至少选择一个角色");
        }
        for (Long roleId : user.getRoleIds())
        {
            if (roleId == null)
            {
                throw new ServiceException("角色ID不能为空");
            }
        }
    }

    /**
     * 删除用户
     */
    @RequiresPermissions("system:user:remove")
    @Log(title = "用户管理", businessType = BusinessType.DELETE,
            isSaveRequestData = false, isSaveResponseData = false)
    @DeleteMapping("/{userIds}")
    public AjaxResult remove(@PathVariable Long[] userIds)
    {
        if (ArrayUtils.contains(userIds, SecurityUtils.getUserId()))
        {
            return error("当前用户不能删除");
        }
        return toAjax(userService.deleteUserByIds(userIds));
    }

    /**
     * 重置密码
     */
    @RequiresPermissions("system:user:resetPwd")
    @Log(title = "用户管理", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping("/resetPwd")
    public AjaxResult resetPwd(@RequestBody SysUser user, HttpServletResponse response)
    {
        userService.checkUserDataScope(user.getUserId());
        SysUser targetUser = userService.selectUserById(user.getUserId());
        if (targetUser == null)
        {
            return error("用户不存在");
        }
        userService.checkUserAllowed(targetUser);
        targetUser.setUpdateBy(SecurityUtils.getUsername());
        SysTemporaryCredentialVo credential = userService.resetTemporaryCredential(targetUser,
                getSysAccountChrtype());
        preventCredentialResponseCaching(response);
        return success(new SysUserCreateResultVo(credential));
    }

    @RequiresPermissions("system:user:resetPwd")
    @Log(title = "旧凭据轮换预览", businessType = BusinessType.OTHER,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/credential-rotation/preview")
    public AjaxResult previewLegacyCredentialRotation(
            @RequestBody LegacyCredentialRotationPreviewRequest request)
    {
        AjaxResult denied = credentialRotationGuard();
        if (denied != null) return denied;
        return success(legacyCredentialRotationService.preview(request, SecurityUtils.getUserId()));
    }

    @RequiresPermissions("system:user:resetPwd")
    @Log(title = "旧凭据批量轮换", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/credential-rotation/execute")
    public AjaxResult executeLegacyCredentialRotation(
            @RequestBody LegacyCredentialRotationExecuteRequest request,
            HttpServletResponse response)
    {
        AjaxResult denied = credentialRotationGuard();
        if (denied != null) return denied;
        preventCredentialResponseCaching(response);
        return success(legacyCredentialRotationService.execute(request, SecurityUtils.getUserId()));
    }

    private AjaxResult credentialRotationGuard()
    {
        if (!SecurityUtils.isAdmin())
        {
            return AjaxResult.error(403, "仅超级管理员可执行旧凭据轮换");
        }
        if (!legacyCredentialRotationService.isOpen())
        {
            return AjaxResult.error(409, "旧凭据迁移已关闭")
                    .put("businessCode", "LEGACY_CREDENTIAL_MIGRATION_CLOSED");
        }
        return null;
    }

    /**
     * 状态修改
     */
    @RequiresPermissions("system:user:edit")
    @Log(title = "用户管理", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping("/changeStatus")
    public AjaxResult changeStatus(@RequestBody SysUser user)
    {
        userService.checkUserAllowed(user);
        userService.checkUserDataScope(user.getUserId());
        user.setUpdateBy(SecurityUtils.getUsername());
        int rows = userService.updateUserStatus(user);
        return toAjax(rows);
    }

    /**
     * 根据用户编号获取授权角色
     */
    @RequiresPermissions("system:user:authRole")
    @GetMapping("/authRole/{userId}")
    public AjaxResult authRole(@PathVariable("userId") Long userId)
    {
        userService.checkUserDataScope(userId);
        AjaxResult ajax = AjaxResult.success();
        SysUser user = userService.selectUserById(userId);
        List<SysRole> roles = roleService.selectRolesByUserId(userId);
        ajax.put("user", SysUserOptionVo.from(user));
        ajax.put("roles", filterVisibleRoles(roles, isCurrentOrTargetAdmin(user, roles)));
        return ajax;
    }

    private List<SysRole> filterVisibleRoles(List<SysRole> roles, boolean includeAdminRole)
    {
        if (includeAdminRole)
        {
            return roles;
        }
        return roles.stream().filter(r -> !r.isAdmin()).collect(Collectors.toList());
    }

    private boolean isCurrentOrTargetAdmin(SysUser user, List<SysRole> roles)
    {
        if (SecurityUtils.isAdmin())
        {
            return true;
        }
        if (user != null && user.isAdmin())
        {
            return true;
        }
        return roles != null && roles.stream().anyMatch(SysRole::isAdmin);
    }

    /**
     * 用户授权角色
     */
    @RequiresPermissions("system:user:edit")
    @Log(title = "用户管理", businessType = BusinessType.GRANT,
            isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping("/authRole")
    public AjaxResult insertAuthRole(Long userId, Long[] roleIds)
    {
        userService.checkUserDataScope(userId);
        roleService.checkRoleDataScope(roleIds);
        userService.insertUserAuth(userId, roleIds);
        return success();
    }

    /**
     * 获取部门树列表
     */
    @RequiresPermissions("system:user:list")
    @GetMapping("/deptTree")
    public AjaxResult deptTree(SysDept dept)
    {
        return success(deptService.selectDeptTreeList(dept));
    }

    private void preventCredentialResponseCaching(HttpServletResponse response)
    {
        response.setHeader("Cache-Control", "no-store, max-age=0");
        response.setHeader("Pragma", "no-cache");
    }
}
