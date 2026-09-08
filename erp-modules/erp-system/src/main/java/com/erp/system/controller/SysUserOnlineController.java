package com.erp.system.controller;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.erp.common.core.constant.CacheConstants;
import com.erp.common.core.constant.UserConstants;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.core.web.controller.BaseController;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.core.web.page.TableDataInfo;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.redis.service.RedisService;
import com.erp.common.redis.service.RedisService.KeyScanResult;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.common.security.service.TokenService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.system.api.model.LoginUser;
import com.erp.system.domain.SysUserOnline;
import com.erp.system.domain.SysUserOnlineTableDataInfo;
import com.erp.system.service.ISysUserService;
import com.erp.system.service.ISysUserOnlineService;

/**
 * 在线用户监控
 * 
 * @author erp
 */
@RestController
@RequestMapping("/online")
public class SysUserOnlineController extends BaseController
{
    private static final int REDIS_SCAN_BATCH_SIZE = 500;

    private static final int ONLINE_SESSION_SCAN_LIMIT = 5000;

    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{16,128}$");

    @Autowired
    private ISysUserOnlineService userOnlineService;

    @Autowired
    private RedisService redisService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ISysUserService userService;

    @RequiresPermissions("monitor:online:list")
    @GetMapping("/list")
    public TableDataInfo list(String ipaddr, String userName)
    {
        KeyScanResult scanResult;
        List<SysUserOnline> userOnlineList = new ArrayList<SysUserOnline>();
        List<OnlineSessionCandidate> candidates = new ArrayList<>();
        int invalidSessionCount = 0;
        try
        {
            scanResult = redisService.scanKeys(CacheConstants.LOGIN_TOKEN_KEY + "*", REDIS_SCAN_BATCH_SIZE,
                    ONLINE_SESSION_SCAN_LIMIT);
            Collection<String> keys = scanResult.getKeys();
            if (keys != null)
            {
                for (String key : keys)
                {
                    String tokenId = extractTokenId(key);
                    LoginUser user = tokenId == null ? null : tokenService.getLoginUserByCacheKey(key);
                    if (user == null)
                    {
                        invalidSessionCount++;
                        continue;
                    }
                    Long userId = resolveUserId(user);
                    if (userId == null)
                    {
                        invalidSessionCount++;
                        continue;
                    }
                    candidates.add(new OnlineSessionCandidate(tokenId, userId, user));
                }
            }
            Set<Long> candidateUserIds = new LinkedHashSet<>();
            for (OnlineSessionCandidate candidate : candidates)
            {
                candidateUserIds.add(candidate.userId);
            }
            Set<Long> visibleUserIds = userService.selectVisibleUserIds(candidateUserIds);
            for (OnlineSessionCandidate candidate : candidates)
            {
                if (!visibleUserIds.contains(candidate.userId))
                {
                    continue;
                }
                SysUserOnline online = filterOnlineUser(ipaddr, userName, candidate.loginUser);
                if (online != null)
                {
                    online.setTokenId(candidate.tokenId);
                    online.setCurrentSession(StringUtils.equals(candidate.tokenId, SecurityUtils.getUserKey()));
                    userOnlineList.add(online);
                }
            }
        }
        catch (RuntimeException e)
        {
            logger.error("读取在线会话列表失败，异常类型={}", e.getClass().getSimpleName());
            throw new ServiceException("在线状态暂不可用，请稍后重试");
        }

        userOnlineList.sort(Comparator.comparing(SysUserOnline::getLoginTime,
                Comparator.nullsLast(Comparator.reverseOrder())));
        SysUserOnlineTableDataInfo response = SysUserOnlineTableDataInfo.from(getDataTable(userOnlineList));
        response.setTruncated(scanResult.isTruncated());
        response.setScanLimit(scanResult.getLimit());
        response.setScannedCount(scanResult.getScannedCount());
        response.setInvalidSessionCount(invalidSessionCount);
        return response;
    }

    /**
     * 强退用户
     */
    @RequiresPermissions("monitor:online:forceLogout")
    @Log(title = "在线用户", businessType = BusinessType.FORCE,
            isSaveRequestData = false, isSaveResponseData = false)
    @DeleteMapping("/{tokenId}")
    public AjaxResult forceLogout(@PathVariable String tokenId)
    {
        validateTokenId(tokenId);
        if (StringUtils.equals(tokenId, SecurityUtils.getUserKey()))
        {
            throw new ServiceException("不能强退当前会话，请使用退出登录");
        }

        LoginUser target;
        try
        {
            target = tokenService.getLoginUserByUserKey(tokenId);
        }
        catch (RuntimeException e)
        {
            logger.error("读取待强退会话失败，异常类型={}", e.getClass().getSimpleName());
            throw new ServiceException("在线状态暂不可用，请稍后重试");
        }
        if (target == null)
        {
            deleteSession(tokenId);
            return success("会话已离线");
        }

        Long targetUserId = resolveUserId(target);
        if (targetUserId == null)
        {
            throw new ServiceException("会话用户信息不完整，无法执行强退");
        }
        if (isAdministrator(target, targetUserId) && !SecurityUtils.isAdmin())
        {
            throw new ServiceException("不允许强退管理员会话");
        }
        userService.checkUserDataScope(targetUserId);

        return deleteSession(tokenId) ? success("强退成功") : success("会话已离线");
    }

    private SysUserOnline filterOnlineUser(String ipaddr, String userName, LoginUser user)
    {
        if (StringUtils.isNotEmpty(ipaddr) && StringUtils.isNotEmpty(userName))
        {
            return userOnlineService.selectOnlineByInfo(ipaddr, userName, user);
        }
        if (StringUtils.isNotEmpty(ipaddr))
        {
            return userOnlineService.selectOnlineByIpaddr(ipaddr, user);
        }
        if (StringUtils.isNotEmpty(userName))
        {
            return userOnlineService.selectOnlineByUserName(userName, user);
        }
        return userOnlineService.loginUserToUserOnline(user);
    }

    private String extractTokenId(String cacheKey)
    {
        if (StringUtils.isEmpty(cacheKey) || !cacheKey.startsWith(CacheConstants.LOGIN_TOKEN_KEY))
        {
            return null;
        }
        String tokenId = cacheKey.substring(CacheConstants.LOGIN_TOKEN_KEY.length());
        return SESSION_ID_PATTERN.matcher(tokenId).matches() ? tokenId : null;
    }

    private void validateTokenId(String tokenId)
    {
        if (StringUtils.isEmpty(tokenId) || !SESSION_ID_PATTERN.matcher(tokenId).matches())
        {
            throw new ServiceException("会话标识无效");
        }
    }

    private Long resolveUserId(LoginUser loginUser)
    {
        if (loginUser.getUserid() != null)
        {
            return loginUser.getUserid();
        }
        return loginUser.getSysUser() == null ? null : loginUser.getSysUser().getUserId();
    }

    private boolean isAdministrator(LoginUser loginUser, Long userId)
    {
        Set<String> roles = loginUser.getRoles();
        return UserConstants.isAdmin(userId)
                || (roles != null && roles.contains(UserConstants.SUPER_ADMIN_ROLE_KEY));
    }

    private boolean deleteSession(String tokenId)
    {
        try
        {
            return tokenService.deleteLoginUserByUserKey(tokenId);
        }
        catch (RuntimeException e)
        {
            logger.error("删除在线会话失败，异常类型={}", e.getClass().getSimpleName());
            throw new ServiceException("在线状态暂不可用，请稍后重试");
        }
    }

    private static final class OnlineSessionCandidate
    {
        private final String tokenId;

        private final Long userId;

        private final LoginUser loginUser;

        private OnlineSessionCandidate(String tokenId, Long userId, LoginUser loginUser)
        {
            this.tokenId = tokenId;
            this.userId = userId;
            this.loginUser = loginUser;
        }
    }
}
