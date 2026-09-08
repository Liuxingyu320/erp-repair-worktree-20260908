package com.erp.oa.api.factory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import com.erp.common.core.domain.R;
import com.erp.oa.api.RemoteSignTaskService;
import com.erp.oa.api.domain.HrSignBusinessEvent;
import feign.FeignException;

/**
 * OA签约事件调用降级。日志只记录错误类型和状态码，不记录员工快照。
 */
@Component
public class RemoteSignTaskFallbackFactory implements FallbackFactory<RemoteSignTaskService>
{
    private static final Logger log = LoggerFactory.getLogger(RemoteSignTaskFallbackFactory.class);

    @Override
    public RemoteSignTaskService create(Throwable throwable)
    {
        int responseCode = remoteStatus(throwable);
        String errorType = throwable == null ? "Unknown" : throwable.getClass().getSimpleName();
        log.error("OA签约事件服务调用失败，type={}, status={}", errorType, responseCode);
        return new RemoteSignTaskService()
        {
            @Override
            public R<Long> publishEvent(HrSignBusinessEvent event, String source)
            {
                return R.fail(responseCode, "发布人事签约事件失败");
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
