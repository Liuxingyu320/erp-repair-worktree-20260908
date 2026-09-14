package com.erp.system.service.impl;

import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.api.domain.UserNotificationCommand;
import com.erp.system.config.InAppPushProperties;
import com.erp.system.domain.SysUserNotification;
import com.erp.system.domain.SysUserNotificationPushOutbox;
import com.erp.system.mapper.SysUserNotificationMapper;
import com.erp.system.mapper.SysUserNotificationPushOutboxMapper;

/** Commits the in-app message and optional push intent together, without provider calls. */
@Service
public class InAppNotificationWriter
{
    private static final Set<String> DIRECT_ROUTES = Set.of(
            "OA_SIGN_HR_TASK", "OA_SIGN_PACKAGE_SIGN", "HR_HEALTH_CERT_DUE");

    private final SysUserNotificationMapper notificationMapper;
    private final SysUserNotificationPushOutboxMapper outboxMapper;
    private final InAppPushProperties properties;

    public InAppNotificationWriter(SysUserNotificationMapper notificationMapper,
            SysUserNotificationPushOutboxMapper outboxMapper, InAppPushProperties properties)
    {
        this.notificationMapper = notificationMapper;
        this.outboxMapper = outboxMapper;
        this.properties = properties;
    }

    @Transactional(rollbackFor = Exception.class)
    public int insert(SysUserNotification notification)
    {
        int inserted = notificationMapper.insertNotification(notification);
        // An idempotent replay never backfills a message created before enablement.
        if (inserted == 1 && properties.isEnabled())
        {
            if (notification.getNotificationId() == null || notification.getNotificationId() <= 0)
            {
                throw new ServiceException("消息未返回有效ID，无法保存推送任务");
            }
            SysUserNotificationPushOutbox row = new SysUserNotificationPushOutbox();
            row.setNotificationId(notification.getNotificationId());
            row.setPayloadJson(JSON.toJSONString(pushCommand(notification)));
            if (outboxMapper.insert(row) != 1)
            {
                throw new ServiceException("消息推送任务保存失败");
            }
        }
        return inserted;
    }

    static UserNotificationCommand pushCommand(SysUserNotification notification)
    {
        UserNotificationCommand command = new UserNotificationCommand();
        command.setChannel("MOBILE_PUSH");
        command.setRecipientUserId(notification.getUserId());
        command.setBusinessKey(notification.getBusinessKey());
        command.setTitle(notification.getTitle());
        command.setBody(notification.getBody());
        if (DIRECT_ROUTES.contains(notification.getRouteType()))
        {
            // Preserve the source payload exactly so OA's existing second channel shares its ledger.
            command.setRouteType(notification.getRouteType());
            command.setRouteParams(notification.getRouteParams());
            if ("HR_HEALTH_CERT_DUE".equals(notification.getRouteType())
                    && notification.getBusinessKey().startsWith("HR_HEALTH_CERT:")
                    && notification.getBusinessKey().endsWith(":IN_APP"))
            {
                String key = notification.getBusinessKey();
                command.setBusinessKey(key.substring(0, key.length() - ":IN_APP".length()) + ":MOBILE_PUSH");
            }
        }
        else
        {
            command.setRouteType("USER_NOTIFICATION");
            command.setRouteParams("{\"notificationId\":" + notification.getNotificationId() + "}");
        }
        return command;
    }
}
