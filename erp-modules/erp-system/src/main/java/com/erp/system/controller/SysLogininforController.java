package com.erp.system.controller;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.erp.common.core.constant.CacheConstants;
import com.erp.common.core.constant.HttpStatus;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.core.utils.poi.ExcelUtil;
import com.erp.common.core.web.controller.BaseController;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.core.web.page.TableDataInfo;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.redis.service.RedisService;
import com.erp.common.security.annotation.InnerAuth;
import com.erp.common.security.annotation.IdempotentSubmit;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.system.api.domain.SysLogininfor;
import com.erp.system.api.domain.SysUser;
import com.erp.system.domain.vo.SysLoginLockStateVo;
import com.erp.system.domain.vo.SysLoginUnlockResultVo;
import com.erp.system.service.ISysLogininforService;
import com.erp.system.service.ISysUserService;

/**
 * 系统访问记录
 * 
 * @author erp
 */
@RestController
@RequestMapping("/logininfor")
public class SysLogininforController extends BaseController
{
    private static final Logger log = LoggerFactory.getLogger(SysLogininforController.class);

    @Autowired
    private ISysLogininforService logininforService;

    @Autowired
    private RedisService redisService;

    @Autowired
    private ISysUserService userService;

    @RequiresPermissions("system:logininfor:list")
    @GetMapping("/list")
    public TableDataInfo list(SysLogininfor logininfor)
    {
        startPage();
        List<SysLogininfor> list = logininforService.selectLogininforList(logininfor);
        return getDataTable(list);
    }

    @Log(title = "登录日志", businessType = BusinessType.EXPORT)
    @RequiresPermissions("system:logininfor:export")
    @PostMapping("/export")
    public void export(HttpServletResponse response, SysLogininfor logininfor)
    {
        List<SysLogininfor> list = logininforService.selectLogininforList(logininfor);
        ExcelUtil<SysLogininfor> util = new ExcelUtil<SysLogininfor>(SysLogininfor.class);
        util.exportExcel(response, list, "登录日志");
    }

    @RequiresPermissions("system:logininfor:remove")
    @Log(title = "登录日志", businessType = BusinessType.DELETE)
    @DeleteMapping("/{infoIds}")
    public AjaxResult remove(@PathVariable Long[] infoIds)
    {
        return toAjax(logininforService.deleteLogininforByIds(infoIds));
    }

    @RequiresPermissions("system:logininfor:remove")
    @Log(title = "登录日志", businessType = BusinessType.DELETE)
    @DeleteMapping("/clean")
    public AjaxResult clean()
    {
        return AjaxResult.error(HttpStatus.CONFLICT, "一键清空已停用，请按审计留存制度完成归档与复核");
    }

    @RequiresPermissions("system:logininfor:unlock")
    @GetMapping("/lock-state")
    public AjaxResult lockState(String userName)
    {
        SysUser targetUser = validateUnlockTarget(userName);
        return success(readLockState(targetUser.getUserName()));
    }

    @RequiresPermissions("system:logininfor:unlock")
    @IdempotentSubmit(timeout = 5)
    @Log(title = "账户解锁", businessType = BusinessType.OTHER,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/{userName}/unlock")
    public AjaxResult unlock(@PathVariable("userName") String userName)
    {
        SysLoginUnlockResultVo result = performUnlock(userName);
        return AjaxResult.success(result.isUnlocked() ? "账户已解锁" : "账户当前未锁定", result);
    }

    /**
     * 兼容旧客户端一个版本；新客户端只使用 POST /{userName}/unlock。
     */
    @Deprecated(forRemoval = true)
    @RequiresPermissions("system:logininfor:unlock")
    @Log(title = "账户解锁（兼容接口）", businessType = BusinessType.OTHER,
            isSaveRequestData = false, isSaveResponseData = false)
    @GetMapping("/unlock/{userName}")
    public AjaxResult unlockLegacy(@PathVariable("userName") String userName)
    {
        log.warn("旧版 GET 解锁接口仍被调用，userName={}", userName);
        SysLoginUnlockResultVo result = performUnlock(userName);
        return AjaxResult.success(result.isUnlocked() ? "账户已解锁" : "账户当前未锁定", result);
    }

    @InnerAuth
    @PostMapping
    public AjaxResult add(@RequestBody SysLogininfor logininfor)
    {
        return toAjax(logininforService.insertLogininfor(logininfor));
    }

    private SysLoginUnlockResultVo performUnlock(String userName)
    {
        SysUser targetUser = validateUnlockTarget(userName);
        String normalizedUserName = targetUser.getUserName();
        SysLoginLockStateVo before = readLockState(normalizedUserName);
        boolean unlocked = false;
        if (before.isLocked())
        {
            try
            {
                unlocked = redisService.deleteObject(CacheConstants.PWD_ERR_CNT_KEY + normalizedUserName);
            }
            catch (RuntimeException exception)
            {
                throw lockStateUnavailable(exception);
            }
        }
        SysLoginLockStateVo after = readLockState(normalizedUserName);
        return new SysLoginUnlockResultVo(normalizedUserName, unlocked && !after.isLocked(), before, after);
    }

    private SysUser validateUnlockTarget(String userName)
    {
        String normalizedUserName = StringUtils.trim(userName);
        if (StringUtils.isEmpty(normalizedUserName))
        {
            throw new ServiceException("用户账号不能为空", HttpStatus.BAD_REQUEST);
        }
        SysUser targetUser = userService.selectUserByUserName(normalizedUserName);
        if (targetUser == null)
        {
            throw new ServiceException("用户账号不存在", HttpStatus.NOT_FOUND);
        }
        userService.checkUserAllowed(targetUser);
        userService.checkUserDataScope(targetUser.getUserId());
        return targetUser;
    }

    private SysLoginLockStateVo readLockState(String userName)
    {
        String cacheKey = CacheConstants.PWD_ERR_CNT_KEY + userName;
        try
        {
            Object value = redisService.getCacheObject(cacheKey);
            int retryCount = parseRetryCount(value);
            long remainingSeconds = Math.max(redisService.getExpire(cacheKey), 0L);
            boolean locked = retryCount >= CacheConstants.PASSWORD_MAX_RETRY_COUNT;
            return new SysLoginLockStateVo(userName, locked, retryCount, remainingSeconds, locked);
        }
        catch (RuntimeException exception)
        {
            throw lockStateUnavailable(exception);
        }
    }

    private int parseRetryCount(Object value)
    {
        if (value == null)
        {
            return 0;
        }
        if (value instanceof Number number)
        {
            return Math.max(number.intValue(), 0);
        }
        try
        {
            return Math.max(Integer.parseInt(String.valueOf(value)), 0);
        }
        catch (NumberFormatException exception)
        {
            throw new ServiceException("账户锁定状态数据异常，请联系管理员", HttpStatus.ERROR);
        }
    }

    private ServiceException lockStateUnavailable(RuntimeException exception)
    {
        log.error("读取或更新账户锁定状态失败", exception);
        return new ServiceException("账户锁定状态服务暂不可用，请稍后重试", HttpStatus.ERROR);
    }
}
