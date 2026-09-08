package com.erp.system.controller;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;
import com.erp.common.core.constant.UserConstants;
import com.erp.common.core.domain.R;
import com.erp.common.core.text.Convert;
import com.erp.common.core.utils.DateUtils;
import com.erp.common.core.utils.ServletUtils;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.core.utils.file.FileTypeUtils;
import com.erp.common.core.utils.file.MimeTypeUtils;
import com.erp.common.core.web.controller.BaseController;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.RequiresLogin;
import com.erp.common.security.annotation.AllowPasswordChangeRequired;
import com.erp.common.security.annotation.AllowsTemporaryCredential;
import com.erp.common.security.annotation.IdempotentSubmit;
import com.erp.common.security.service.TokenService;
import com.erp.system.service.support.UserSessionInvalidationService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.system.api.RemoteFileService;
import com.erp.system.api.domain.SysFile;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.model.LoginUser;
import com.erp.system.domain.vo.SysProfileCompletionRequest;
import com.erp.system.domain.vo.SysProfileCompletionVo;
import com.erp.system.domain.dto.SysSelfPasswordUpdateRequest;
import com.erp.system.domain.dto.SysSelfProfileUpdateRequest;
import com.erp.system.domain.vo.SysSelfProfileVo;
import com.erp.system.service.ISysConfigService;
import com.erp.system.service.ISysUserProfileCompletionService;
import com.erp.system.service.ISysUserService;

/**
 * 个人信息 业务处理
 * 
 * @author erp
 */
@RestController
@RequestMapping("/user/profile")
public class SysProfileController extends BaseController
{
    private static final long MAX_AVATAR_BYTES = 5L * 1024L * 1024L;

    @Autowired
    private ISysUserService userService;
    
    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserSessionInvalidationService userSessionInvalidationService;
    
    @Autowired
    private RemoteFileService remoteFileService;

    @Autowired
    private ISysConfigService configService;

    @Autowired
    private ISysUserProfileCompletionService profileCompletionService;

    /**
     * 个人信息
     */
    @RequiresLogin
    @GetMapping
    public AjaxResult profile()
    {
        preventProfileResponseCaching();
        String username = SecurityUtils.getUsername();
        SysUser user = userService.selectUserByUserName(username);
        AjaxResult ajax = AjaxResult.success(SysSelfProfileVo.from(user));
        ajax.put("roleGroup", userService.selectUserRoleGroup(username));
        ajax.put("postGroup", userService.selectUserPostGroup(username));
        return ajax;
    }

    /**
     * 当前员工登录资料完整度。
     */
    @RequiresLogin
    @GetMapping("/completion")
    public AjaxResult profileCompletion()
    {
        preventProfileResponseCaching();
        return success(profileCompletionService.evaluate(SecurityUtils.getUserId()));
    }

    /**
     * 补全当前员工可自行维护的登录资料。
     */
    @RequiresLogin
    @IdempotentSubmit(timeout = 30)
    @PutMapping("/completion")
    public AjaxResult updateProfileCompletion(@RequestBody SysProfileCompletionRequest request)
    {
        preventProfileResponseCaching();
        LoginUser loginUser = SecurityUtils.getLoginUser();
        SysProfileCompletionVo result = profileCompletionService.save(
                loginUser.getUserid(), request, loginUser.getUsername());
        loginUser.setSysUser(userService.selectUserByUserName(loginUser.getUsername()));
        tokenService.setLoginUser(loginUser);
        return success(result);
    }

    /**
     * 修改用户
     */
    @RequiresLogin
    @IdempotentSubmit(timeout = 30)
    @Log(title = "个人信息", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping
    public AjaxResult updateProfile(@Validated @RequestBody SysSelfProfileUpdateRequest request)
    {
        preventProfileResponseCaching();
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (StringUtils.isNull(loginUser) || StringUtils.isNull(loginUser.getSysUser()))
        {
            return error("登录状态已过期，请重新登录");
        }
        SysUser currentUser = loginUser.getSysUser();
        currentUser.setNickName(request.getNickName());
        currentUser.setEmail(request.getEmail());
        currentUser.setPhonenumber(request.getPhonenumber());
        currentUser.setSex(request.getSex());
        if (StringUtils.isNotEmpty(request.getPhonenumber()) && !userService.checkPhoneUnique(currentUser))
        {
            return error("修改用户'" + loginUser.getUsername() + "'失败，手机号码已存在");
        }
        if (StringUtils.isNotEmpty(request.getEmail()) && !userService.checkEmailUnique(currentUser))
        {
            return error("修改用户'" + loginUser.getUsername() + "'失败，邮箱账号已存在");
        }
        if (userService.updateSelfProfile(currentUser, request.profileValues(), loginUser.getUsername()))
        {
            // 更新缓存用户信息
            loginUser.setSysUser(userService.selectUserByUserName(loginUser.getUsername()));
            tokenService.setLoginUser(loginUser);
            return success();
        }
        return error("修改个人信息异常，请联系管理员");
    }

    /**
     * 重置密码
     */
    @RequiresLogin
    @AllowPasswordChangeRequired
    @AllowsTemporaryCredential
    @IdempotentSubmit(timeout = 30)
    @Log(title = "个人信息", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping("/updatePwd")
    public AjaxResult updatePwd(@Validated @RequestBody SysSelfPasswordUpdateRequest request)
    {
        preventProfileResponseCaching();
        String oldPassword = request.getOldPassword();
        String newPassword = request.getNewPassword();
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (StringUtils.isNull(loginUser) || StringUtils.isNull(loginUser.getSysUser()))
        {
            return error("登录状态已过期，请重新登录");
        }
        Long userId = loginUser.getUserid();
        String password = loginUser.getSysUser().getPassword();
        if (!SecurityUtils.matchesPassword(oldPassword, password))
        {
            return error("修改密码失败，旧密码错误");
        }
        String passwordError = UserConstants.getPasswordPolicyError(newPassword, getSysAccountChrtype());
        if (StringUtils.isNotEmpty(passwordError))
        {
            return error(passwordError);
        }
        if (SecurityUtils.matchesPassword(newPassword, password))
        {
            return error("新密码不能与旧密码相同");
        }
        newPassword = SecurityUtils.encryptPassword(newPassword);
        if (userService.activateUserPassword(userId, newPassword, loginUser.getUsername()) > 0)
        {
            // 更新缓存用户密码&密码最后更新时间
            loginUser.getSysUser().setPwdUpdateDate(DateUtils.getNowDate());
            loginUser.getSysUser().setPassword(newPassword);
            loginUser.getSysUser().setCredentialState(SysUser.CREDENTIAL_STATE_ACTIVE);
            loginUser.getSysUser().setMustChangePassword("0");
            loginUser.getSysUser().setTemporaryPasswordExpiresAt(null);
            loginUser.setCredentialState(SysUser.CREDENTIAL_STATE_ACTIVE);
            loginUser.setTemporaryPasswordExpiresAt(null);
            userSessionInvalidationService.record(userId,
                    UserSessionInvalidationService.PASSWORD_CHANGED, loginUser.getToken());
            tokenService.setLoginUser(loginUser);
            return success();
        }
        return error("修改密码异常，请联系管理员");
    }
    
    /**
     * 头像上传
     */
    @RequiresLogin
    @IdempotentSubmit(timeout = 30)
    @Log(title = "用户头像", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/avatar")
    public AjaxResult avatar(@RequestParam("avatarfile") MultipartFile file)
    {
        preventProfileResponseCaching();
        if (!file.isEmpty())
        {
            if (file.getSize() > MAX_AVATAR_BYTES)
            {
                return error("头像文件不能超过5 MiB");
            }
            LoginUser loginUser = SecurityUtils.getLoginUser();
            if (StringUtils.isNull(loginUser) || StringUtils.isNull(loginUser.getSysUser()))
            {
                return error("登录状态已过期，请重新登录");
            }
            String extension = FileTypeUtils.getExtension(file);
            if (!StringUtils.equalsAnyIgnoreCase(extension, MimeTypeUtils.IMAGE_EXTENSION))
            {
                return error("文件格式不正确，请上传" + Arrays.toString(MimeTypeUtils.IMAGE_EXTENSION) + "格式");
            }
            R<SysFile> fileResult = remoteFileService.upload(file);
            if (StringUtils.isNull(fileResult) || StringUtils.isNull(fileResult.getData()))
            {
                return error("文件服务异常，请联系管理员");
            }
            String url = fileResult.getData().getUrl();
            if (userService.updateUserAvatar(loginUser.getUserid(), url))
            {
                String oldAvatarUrl = loginUser.getSysUser().getAvatar();
                if (StringUtils.isNotEmpty(oldAvatarUrl))
                {
                    remoteFileService.delete(oldAvatarUrl);
                }
                AjaxResult ajax = AjaxResult.success();
                ajax.put("imgUrl", url);
                // 更新缓存用户头像
                loginUser.getSysUser().setAvatar(url);
                tokenService.setLoginUser(loginUser);
                return ajax;
            }
        }
        return error("上传图片异常，请联系管理员");
    }

    public String getSysAccountChrtype()
    {
        if (configService == null)
        {
            return "0";
        }
        return Convert.toStr(configService.selectConfigByKey("sys.account.chrtype"), "0");
    }

    @ModelAttribute
    public void preventProfileResponseCaching()
    {
        jakarta.servlet.http.HttpServletResponse response = ServletUtils.getResponse();
        if (response != null)
        {
            response.setHeader("Cache-Control", "no-store, max-age=0");
            response.setHeader("Pragma", "no-cache");
        }
    }
}
