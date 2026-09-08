package com.erp.auth.service;

import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.erp.common.core.constant.CacheConstants;
import com.erp.common.core.constant.Constants;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.constant.UserConstants;
import com.erp.common.core.domain.R;
import com.erp.common.core.enums.UserStatus;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.text.Convert;
import com.erp.common.core.utils.DateUtils;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.core.utils.ip.IpUtils;
import com.erp.common.redis.service.RedisService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.common.security.exception.CredentialRestrictionException;
import com.erp.system.api.RemoteConfigService;
import com.erp.system.api.RemoteUserService;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.model.LoginUser;

/**
 * 登录校验方法
 * 
 * @author erp
 */
@Component
public class SysLoginService
{
    private static final String PASSWORD_POLICY_CONFIG_KEY = "sys.account.chrtype";

    @Autowired
    private RemoteUserService remoteUserService;

    @Autowired
    private RemoteConfigService remoteConfigService;

    @Autowired
    private SysPasswordService passwordService;

    @Autowired
    private SysRecordLogService recordLogService;

    @Autowired
    private RedisService redisService;

    /**
     * 登录
     */
    public LoginUser login(String username, String password)
    {
        // 用户名或密码为空 错误
        if (StringUtils.isAnyBlank(username, password))
        {
            recordLogService.recordLogininfor(username, Constants.LOGIN_FAIL, "用户/密码必须填写");
            throw new ServiceException("用户/密码必须填写");
        }
        // 登录兼容历史短密码，最小长度策略在注册、改密、重置时执行。
        if (password.length() > UserConstants.PASSWORD_MAX_LENGTH)
        {
            recordLogService.recordLogininfor(username, Constants.LOGIN_FAIL, "用户密码不在指定范围");
            throw new ServiceException("用户密码不在指定范围");
        }
        // 用户名不在指定范围内 错误
        if (username.length() < UserConstants.USERNAME_MIN_LENGTH
                || username.length() > UserConstants.USERNAME_MAX_LENGTH)
        {
            recordLogService.recordLogininfor(username, Constants.LOGIN_FAIL, "用户名不在指定范围");
            throw new ServiceException("用户名不在指定范围");
        }
        // IP黑名单校验
        String blackStr = Convert.toStr(redisService.getCacheObject(CacheConstants.SYS_LOGIN_BLACKIPLIST));
        if (IpUtils.isMatchedIp(blackStr, IpUtils.getIpAddr()))
        {
            recordLogService.recordLogininfor(username, Constants.LOGIN_FAIL, "很遗憾，访问IP已被列入系统黑名单");
            throw new ServiceException("很遗憾，访问IP已被列入系统黑名单");
        }
        // 查询用户信息
        R<LoginUser> userResult = remoteUserService.getUserInfo(username, SecurityConstants.INNER);

        if (R.FAIL == userResult.getCode())
        {
            throw new ServiceException(userResult.getMsg());
        }

        LoginUser userInfo = userResult.getData();
        SysUser user = userResult.getData().getSysUser();
        if (UserStatus.DELETED.getCode().equals(user.getDelFlag()))
        {
            recordLogService.recordLogininfor(username, Constants.LOGIN_FAIL, "对不起，您的账号已被删除");
            throw new ServiceException("对不起，您的账号：" + username + " 已被删除");
        }
        if (UserStatus.DISABLE.getCode().equals(user.getStatus()))
        {
            recordLogService.recordLogininfor(username, Constants.LOGIN_FAIL, "用户已停用，请联系管理员");
            throw new ServiceException("对不起，您的账号：" + username + " 已停用");
        }
        passwordService.validate(user, password);
        applyCredentialState(userInfo, user);
        recordLogService.recordLogininfor(username, Constants.LOGIN_SUCCESS, "登录成功");
        recordLoginInfo(user.getUserId());
        return userInfo;
    }

    private void applyCredentialState(LoginUser loginUser, SysUser user)
    {
        String state = user.resolvedCredentialState();
        if (!SysUser.CREDENTIAL_STATE_ACTIVE.equals(state)
                && !SysUser.CREDENTIAL_STATE_TEMPORARY.equals(state)
                && !SysUser.CREDENTIAL_STATE_CHANGE_REQUIRED.equals(state))
        {
            throw new CredentialRestrictionException(CredentialRestrictionException.INVALID_STATE,
                    state, user.getUserId(), "凭据状态异常，请联系管理员");
        }
        if (SysUser.CREDENTIAL_STATE_TEMPORARY.equals(state))
        {
            Date expiresAt = user.getTemporaryPasswordExpiresAt();
            if (expiresAt == null || !expiresAt.after(new Date()))
            {
                throw new CredentialRestrictionException(CredentialRestrictionException.TEMPORARY_EXPIRED,
                        state, user.getUserId(), "临时密码已失效，请联系管理员重新生成");
            }
        }
        loginUser.setCredentialState(state);
        loginUser.setTemporaryPasswordExpiresAt(user.getTemporaryPasswordExpiresAt());
    }

    /**
     * 记录登录信息
     *
     * @param userId 用户ID
     */
    public void recordLoginInfo(Long userId)
    {
        SysUser sysUser = new SysUser();
        sysUser.setUserId(userId);
        // 更新用户登录IP
        sysUser.setLoginIp(IpUtils.getIpAddr());
        // 更新用户登录时间
        sysUser.setLoginDate(DateUtils.getNowDate());
        remoteUserService.recordUserLogin(sysUser, SecurityConstants.INNER);
    }

    /**
     * 退出
     */
    public void logout(String loginName)
    {
        recordLogService.recordLogininfor(loginName, Constants.LOGOUT, "退出成功");
    }

    /**
     * 解锁
     */
    public void unlock(String password)
    {
        String username = SecurityUtils.getUsername();
        // 或密码为空 错误
        if (StringUtils.isEmpty(password))
        {
            throw new ServiceException("密码不能为空");
        }
        // 查询用户信息
        R<LoginUser> userResult = remoteUserService.getUserInfo(username, SecurityConstants.INNER);

        if (R.FAIL == userResult.getCode())
        {
            throw new ServiceException(userResult.getMsg());
        }

        SysUser user = userResult.getData().getSysUser();
        if (!SecurityUtils.matchesPassword(password, user.getPassword()))
        {
            throw new ServiceException("密码错误，请重新输入");
        }
    }

    /**
     * 注册
     */
    public void register(String username, String password)
    {
        // 用户名或密码为空 错误
        if (StringUtils.isAnyBlank(username, password))
        {
            throw new ServiceException("用户/密码必须填写");
        }
        if (username.length() < UserConstants.USERNAME_MIN_LENGTH
                || username.length() > UserConstants.USERNAME_MAX_LENGTH)
        {
            throw new ServiceException("账户长度必须在2到20个字符之间");
        }
        String passwordError = UserConstants.getPasswordPolicyError(password, getPasswordPolicy());
        if (StringUtils.isNotEmpty(passwordError))
        {
            throw new ServiceException(passwordError);
        }

        // 注册用户信息
        SysUser sysUser = new SysUser();
        sysUser.setUserName(username);
        sysUser.setNickName(username);
        sysUser.setPwdUpdateDate(DateUtils.getNowDate());
        sysUser.setCredentialState(SysUser.CREDENTIAL_STATE_ACTIVE);
        sysUser.setTemporaryPasswordExpiresAt(null);
        sysUser.setPassword(SecurityUtils.encryptPassword(password));
        R<?> registerResult = remoteUserService.registerUserInfo(sysUser, SecurityConstants.INNER);

        if (R.FAIL == registerResult.getCode())
        {
            throw new ServiceException(registerResult.getMsg());
        }
        recordLogService.recordLogininfor(username, Constants.REGISTER, "注册成功");
    }

    public String getPasswordPolicy()
    {
        if (remoteConfigService == null)
        {
            return "0";
        }
        R<String> configResult = remoteConfigService.getConfigKey(PASSWORD_POLICY_CONFIG_KEY, SecurityConstants.INNER);
        if (configResult != null && R.isSuccess(configResult) && StringUtils.isNotEmpty(configResult.getData()))
        {
            return configResult.getData();
        }
        return "0";
    }
}
