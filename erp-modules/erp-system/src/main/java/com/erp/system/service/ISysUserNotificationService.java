package com.erp.system.service;

import java.util.List;
import com.erp.system.api.domain.UserNotificationCommand;
import com.erp.system.api.domain.UserNotificationResult;
import com.erp.system.domain.SysUserDeviceToken;
import com.erp.system.domain.SysUserNotification;

/**
 * 定向用户消息与设备令牌服务。
 */
public interface ISysUserNotificationService
{
    UserNotificationResult publish(UserNotificationCommand command);

    List<SysUserNotification> selectUserNotifications(Long userId);

    long countUnread(Long userId);

    int markRead(Long notificationId, Long userId);

    UserNotificationResult registerDeviceToken(Long currentUserId, SysUserDeviceToken request);

    int disableDeviceToken(Long currentUserId, SysUserDeviceToken request);

    List<SysUserDeviceToken> selectEnabledDeviceTokens(Long userId);

    int disableInvalidDeviceToken(String platform, String tokenHash);
}
