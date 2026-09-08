package com.erp.system.service.credential;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.service.TokenService;
import com.erp.system.api.domain.SysUser;
import com.erp.system.service.ISysUserService;

/**
 * 在同一数据库事务内编排管理员重置密码和旧会话撤销。
 *
 * <p>Redis 撤销失败时抛出异常，使密码更新回滚；避免管理员拿不到临时密码，
 * 但数据库密码已经变化的半完成状态。若数据库提交随后失败，旧会话已撤销但
 * 密码未变化，属于安全侧失败，用户可重新登录。</p>
 */
@Service
public class UserCredentialResetService
{
    private static final Logger log = LoggerFactory.getLogger(UserCredentialResetService.class);

    private final ISysUserService userService;

    private final TokenService tokenService;

    public UserCredentialResetService(ISysUserService userService, TokenService tokenService)
    {
        this.userService = userService;
        this.tokenService = tokenService;
    }

    @Transactional(rollbackFor = Exception.class)
    public int resetPasswordAndRevokeSessions(SysUser user)
    {
        int updated = userService.resetPwd(user);
        if (updated <= 0)
        {
            return updated;
        }
        try
        {
            tokenService.deleteLoginUsersByUserId(user.getUserId());
        }
        catch (RuntimeException failure)
        {
            log.error("密码重置会话撤销失败，userId={}，异常类型={}", user.getUserId(),
                    failure.getClass().getSimpleName());
            throw new ServiceException("密码重置未完成：会话撤销失败，请稍后重试")
                    .setDetailMessage(failure.getClass().getSimpleName());
        }
        return updated;
    }
}
