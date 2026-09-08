package com.erp.system.api.factory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import com.erp.common.core.domain.R;
import com.erp.system.api.RemoteConfigService;

/**
 * 参数配置服务降级处理
 *
 * @author erp
 */
@Component
public class RemoteConfigFallbackFactory implements FallbackFactory<RemoteConfigService>
{
    private static final Logger log = LoggerFactory.getLogger(RemoteConfigFallbackFactory.class);

    @Override
    public RemoteConfigService create(Throwable throwable)
    {
        log.error("参数配置服务调用失败:{}", throwable.getMessage());
        return new RemoteConfigService()
        {
            @Override
            public R<String> getConfigKey(String configKey, String source)
            {
                return R.fail("获取参数配置失败:" + throwable.getMessage());
            }
        };
    }
}
