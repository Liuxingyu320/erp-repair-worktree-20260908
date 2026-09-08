package com.erp.system.api.factory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import com.erp.common.core.domain.R;
import com.erp.system.api.RemoteNotificationService;
import com.erp.system.api.domain.UserNotificationCommand;
import com.erp.system.api.domain.UserNotificationResult;
import feign.FeignException;

/**
 * 定向用户消息服务降级处理。日志不得记录消息正文、路由参数或设备令牌。
 */
@Component
public class RemoteNotificationFallbackFactory implements FallbackFactory<RemoteNotificationService>
{
    private static final Logger log = LoggerFactory.getLogger(RemoteNotificationFallbackFactory.class);

    @Override
    public RemoteNotificationService create(Throwable throwable)
    {
        int responseCode = remoteStatus(throwable);
        String errorType = throwable == null ? "Unknown" : throwable.getClass().getSimpleName();
        log.error("定向用户消息服务调用失败，type={}, status={}", errorType, responseCode);
        return new RemoteNotificationService()
        {
            @Override
            public R<UserNotificationResult> publish(UserNotificationCommand command, String source)
            {
                return R.fail(responseCode, "发布定向用户消息失败");
            }
        };
    }

    private static int remoteStatus(Throwable throwable)
    {
        Throwable current = throwable;
        while (current != null)
        {
            if (current instanceof FeignException feignException && feignException.status() > 0)
            {
                return feignException.status();
            }
            current = current.getCause();
        }
        return R.FAIL;
    }
}
