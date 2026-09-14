package com.erp.system.controller;

import org.springframework.beans.factory.annotation.Autowired;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.domain.dto.SysUserNotificationPageQuery;
import com.erp.system.domain.dto.SysUserNotificationReadAllRequest;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.erp.common.core.domain.R;
import com.erp.common.core.web.controller.BaseController;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.security.annotation.InnerAuth;
import com.erp.common.security.annotation.RequiresLogin;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.system.api.domain.UserNotificationCommand;
import com.erp.system.api.domain.UserNotificationResult;
import com.erp.system.domain.SysUserDeviceToken;
import com.erp.system.service.ISysUserNotificationService;

/**
 * 指定用户可见的消息与当前设备令牌接口。
 */
@RestController
@RequestMapping("/user-notification")
public class SysUserNotificationController extends BaseController
{
    @Autowired
    private ISysUserNotificationService notificationService;

    @InnerAuth
    @PostMapping("/inner/publish")
    public R<UserNotificationResult> publish(@RequestBody UserNotificationCommand command)
    {
        return R.ok(notificationService.publish(command));
    }

    @RequiresLogin
    @GetMapping("/list")
    public AjaxResult list()
    {
        return success(notificationService.selectUserNotifications(SecurityUtils.getUserId()));
    }

    @RequiresLogin
    @GetMapping("/page")
    public AjaxResult page(SysUserNotificationPageQuery query)
    {
        if (query == null) throw new ServiceException("消息查询参数不能为空");
        query.validate();
        return success(notificationService.page(SecurityUtils.getUserId(), query));
    }

    @RequiresLogin
    @PostMapping("/read-all")
    public AjaxResult readAll(@RequestBody SysUserNotificationReadAllRequest request)
    {
        if (request == null) throw new ServiceException("消息快照上限不能为空");
        request.validate();
        return success(notificationService.markAllRead(SecurityUtils.getUserId(), request));
    }

    @RequiresLogin
    @GetMapping("/unread-count")
    public AjaxResult unreadCount()
    {
        return success(notificationService.countUnread(SecurityUtils.getUserId()));
    }

    @RequiresLogin
    @PostMapping("/{id}/read")
    public AjaxResult read(@PathVariable("id") Long id)
    {
        notificationService.markRead(id, SecurityUtils.getUserId());
        return success();
    }

    @RequiresLogin
    @PostMapping("/device-token")
    public AjaxResult registerDeviceToken(@RequestBody SysUserDeviceToken request)
    {
        return success(notificationService.registerDeviceToken(SecurityUtils.getUserId(), request));
    }

    @RequiresLogin
    @DeleteMapping("/device-token")
    public AjaxResult disableDeviceToken(@RequestBody SysUserDeviceToken request)
    {
        notificationService.disableDeviceToken(SecurityUtils.getUserId(), request);
        return success();
    }
}
