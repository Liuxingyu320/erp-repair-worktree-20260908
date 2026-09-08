package com.erp.system.api;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.constant.ServiceNameConstants;
import com.erp.common.core.domain.R;
import com.erp.system.api.factory.RemoteConfigFallbackFactory;

/**
 * 参数配置服务
 *
 * @author erp
 */
@FeignClient(contextId = "remoteConfigService", value = ServiceNameConstants.SYSTEM_SERVICE, fallbackFactory = RemoteConfigFallbackFactory.class)
public interface RemoteConfigService
{
    /**
     * 根据参数键名查询参数值
     *
     * @param configKey 参数键名
     * @param source 请求来源
     * @return 参数值
     */
    @GetMapping("/config/inner/configKey/{configKey}")
    public R<String> getConfigKey(@PathVariable("configKey") String configKey, @RequestHeader(SecurityConstants.FROM_SOURCE) String source);
}
