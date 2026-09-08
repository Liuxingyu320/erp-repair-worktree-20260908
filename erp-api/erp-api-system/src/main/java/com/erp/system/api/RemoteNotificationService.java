package com.erp.system.api;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.constant.ServiceNameConstants;
import com.erp.common.core.domain.R;
import com.erp.system.api.domain.UserNotificationCommand;
import com.erp.system.api.domain.UserNotificationResult;
import com.erp.system.api.factory.RemoteNotificationFallbackFactory;

/**
 * 定向用户消息服务。
 */
@FeignClient(contextId = "remoteNotificationService", value = ServiceNameConstants.SYSTEM_SERVICE,
        fallbackFactory = RemoteNotificationFallbackFactory.class)
public interface RemoteNotificationService
{
    @PostMapping("/user-notification/inner/publish")
    R<UserNotificationResult> publish(@RequestBody UserNotificationCommand command,
            @RequestHeader(SecurityConstants.FROM_SOURCE) String source);
}
