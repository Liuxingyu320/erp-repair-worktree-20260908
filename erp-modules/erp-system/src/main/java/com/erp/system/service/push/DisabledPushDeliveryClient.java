package com.erp.system.service.push;

import org.springframework.stereotype.Component;
import com.erp.system.api.domain.UserNotificationCommand;
import com.erp.system.domain.SysUserDeviceToken;

/**
 * 未支持平台或未配置推送时的安全降级实现。
 */
@Component
public class DisabledPushDeliveryClient implements PushDeliveryClient
{
    @Override
    public String platform()
    {
        return "*";
    }

    @Override
    public DeliveryResult deliver(SysUserDeviceToken deviceToken, UserNotificationCommand command)
    {
        return DeliveryResult.disabled("PUSH_DISABLED");
    }
}
